package forge.neo.deck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import forge.card.CardEdition;
import forge.game.GameFormat;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * La enciclopedia: todas las cartas, buscables y filtrables, sin mazo detras.
 *
 * <p>Nace de un informe de itch.io (27-09-2026): <i>"browse and filter cards
 * without having to enter the deck-building screen"</i>. El buscador del
 * constructor contesta otra pregunta — "que cabe en ESTE mazo" — y por eso
 * arrastra identidad de comandante, pozo del formato y techo de copias. Aqui
 * la pregunta es "que existe", y lo que manda es poder mirarlo por expansion,
 * por formato o por lo nuevo.
 *
 * <p>Cero JavaFX y cero reglas propias: la legalidad la contesta el
 * {@link GameFormat} del motor ({@code getFilterRules}, la misma pregunta que
 * hace el constructor: "esta CARTA vale aqui", no "esta impresion"), y el
 * texto lo busca {@link CardIndex}, que ya sabe apartar las reliquias de
 * Ascenso. Asi {@code librarycheck} lo prueba entero sin ventana.
 *
 * <p>Lo unico que se construye aqui es el mapa de <b>impresiones</b>: en que
 * expansiones salio cada carta y cuando salio por primera vez. Recorre las
 * ~95.000 impresiones una vez y se guarda; lo hace la pantalla en segundo
 * plano ({@link #get()} es lo caro).
 */
public final class CardLibrary {

    /** Que se hace con la coleccion. */
    public enum Ownership { ALL, OWNED, MISSING }

    /**
     * Como se ordena.
     *
     * <p>{@code NEWEST} es la primera IMPRESION de la carta (la fecha de su
     * expansion); {@code ADDED}, cuando llego a Forge. No es lo mismo: los
     * adelantos de una expansion que aun no ha salido llegan a Forge semanas
     * antes, y son justo lo que se viene a buscar con "Lo ultimo".
     */
    public enum Sort { NAME, COST, NEWEST, TYPE, ADDED }

    /** Lo que se pide. Todo es opcional: una consulta vacia devuelve todo. */
    public static final class Query {
        public String text = "";
        /** Buscar tambien en el texto de reglas, no solo en nombre y tipo. */
        public boolean rulesText;
        /** Color, tipo, rareza, coste: los botones de siempre. */
        public Predicate<PaperCard> extra;
        /** Solo las cartas de esta expansion, con SU impresion. */
        public String setCode;
        /** Solo las que valen en este formato. */
        public GameFormat format;
        public Ownership ownership = Ownership.ALL;
        /** Los nombres que tienes, en minusculas. Sin esto no se filtra por propiedad. */
        public Set<String> owned;
        public Sort sort = Sort.NAME;
    }

    private static volatile CardLibrary instance;

    private final CardIndex index;
    /** Nombre en minusculas -> la primera vez que se imprimio. */
    private final Map<String, Date> firstPrinted = new HashMap<>();
    /** Codigo de expansion -> sus cartas, una impresion por nombre. */
    private final Map<String, Map<String, PaperCard>> bySet = new HashMap<>();
    /** Las expansiones con alguna carta, de la mas nueva a la mas vieja. */
    private final List<CardEdition> editions = new ArrayList<>();
    /**
     * Nombre en minusculas -> el que se ve, ya en minusculas. Ordenar 33.000
     * cartas pidiendo la traduccion en cada comparacion son medio millon de
     * consultas; asi es una por carta, una vez. El idioma no cambia sin
     * reiniciar, asi que no se queda viejo.
     */
    private final Map<String, String> shown = new HashMap<>();
    /**
     * Nombre en minusculas -> cuando llego a Forge ("AAAA-MM-DD", que se
     * ordena bien como texto). Sale de {@code cartas-en-forge.txt}, que genera
     * {@code tools/fechas-cartas.py} con el historial de git: las copias no
     * tienen git, asi que viaja dentro del jar.
     */
    private final Map<String, String> added = readAdded();

    private CardLibrary(final CardIndex index, final Collection<PaperCard> printings) {
        this.index = index;
        final Set<String> known = new HashSet<>();
        for (int i = 0; i < index.size(); i++) {
            final PaperCard c = index.cardAt(i);
            known.add(key(c));
            shown.put(key(c), forge.neo.card.CardText.nameOf(c).toLowerCase(Locale.ROOT));
        }
        final CardEdition.Collection all = FModel.getMagicDb().getEditions();
        final Map<String, CardEdition> seen = new LinkedHashMap<>();
        for (final PaperCard p : printings) {
            final String name = key(p);
            // Lo que el indice no tiene (las reliquias de Ascenso) tampoco
            // existe aqui: por la puerta de las expansiones se colarian.
            if (!known.contains(name)) {
                continue;
            }
            final CardEdition ed = all.get(p.getEdition());
            if (ed == null) {
                continue;
            }
            seen.putIfAbsent(ed.getCode(), ed);
            final Map<String, PaperCard> cards =
                    bySet.computeIfAbsent(ed.getCode(), c -> new LinkedHashMap<>());
            final PaperCard was = cards.get(name);
            // La primera ilustracion de la expansion, no la que llegue antes.
            if (was == null || p.getArtIndex() < was.getArtIndex()) {
                cards.put(name, p);
            }
            final Date date = ed.getDate();
            if (date != null) {
                firstPrinted.merge(name, date, (a, b) -> a.before(b) ? a : b);
            }
        }
        editions.addAll(seen.values());
        editions.sort(Comparator.comparing(CardLibrary::dateOf).reversed()
                .thenComparing(CardEdition::getName));
    }

    /**
     * La enciclopedia, construida la primera vez que se pide.
     *
     * <p>Tarda lo que tarde recorrer las impresiones (unas decimas): quien la
     * pida desde la interfaz lo hace fuera del hilo de JavaFX.
     */
    public static CardLibrary get() {
        CardLibrary lib = instance;
        if (lib == null) {
            synchronized (CardLibrary.class) {
                lib = instance;
                if (lib == null) {
                    lib = new CardLibrary(CardIndex.get(),
                            FModel.getMagicDb().getCommonCards().getAllCards());
                    instance = lib;
                }
            }
        }
        return lib;
    }

    /** Si ya esta construida: la pantalla la pinta sin esperar. */
    public static boolean isReady() {
        return instance != null;
    }

    /** Si esa carta tiene alguna impresion en la expansion {@code setCode}. */
    public boolean hasPrintingIn(final String setCode, final PaperCard card) {
        final Map<String, PaperCard> cards = bySet.get(setCode);
        return cards != null && cards.containsKey(key(card));
    }

    /** Cuantas cartas distintas hay. */
    public int size() {
        return index.size();
    }

    /** Las expansiones con cartas, de la mas nueva a la mas vieja. */
    public List<CardEdition> editions() {
        return Collections.unmodifiableList(editions);
    }

    /**
     * La ultima expansion YA publicada.
     *
     * <p>Forge trae las siguientes antes de que salgan (con su fecha futura),
     * asi que "lo ultimo" no es la primera de la lista. Y se busca una de
     * verdad — base o expansion —, no el sobre de promos de esa semana, que
     * tambien es mas nuevo y trae cuatro cartas.
     */
    public CardEdition latestRelease() {
        final Date now = new Date();
        CardEdition fallback = null;
        for (final CardEdition ed : editions) {
            if (ed.getDate() == null || ed.getDate().after(now)) {
                continue;
            }
            if (ed.getType() == CardEdition.Type.EXPANSION || ed.getType() == CardEdition.Type.CORE) {
                return ed;
            }
            if (fallback == null) {
                fallback = ed;
            }
        }
        return fallback;
    }

    /**
     * Todas las impresiones de una carta — o sea, todos sus artes —, de la
     * expansion mas nueva a la mas vieja. Es lo que ensenya "Ver sus artes".
     */
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        final CardEdition.Collection all = FModel.getMagicDb().getEditions();
        final List<PaperCard> out =
                new ArrayList<>(FModel.getMagicDb().getCommonCards().getAllCards(card.getName()));
        out.sort(Comparator.comparing((PaperCard p) -> {
            final CardEdition ed = all.get(p.getEdition());
            return ed == null ? new Date(0) : dateOf(ed);
        }).reversed().thenComparing(PaperCard::getEdition)
                .thenComparing(PaperCard::getCollectorNumber)
                .thenComparingInt(PaperCard::getArtIndex));
        return out;
    }

    /** Cuantas cartas distintas trae una expansion. */
    public int countIn(final String setCode) {
        final Map<String, PaperCard> cards = bySet.get(setCode);
        return cards == null ? 0 : cards.size();
    }

    /** Cuando se imprimio la carta por primera vez, o null si no se sabe. */
    public Date firstPrinted(final PaperCard card) {
        return card == null ? null : firstPrinted.get(key(card));
    }

    /**
     * Cuando llego la carta a Forge ("AAAA-MM-DD"), o null si la tabla no la
     * trae — o sea, si es mas nueva que la ultima vez que se genero.
     */
    public String addedToForge(final PaperCard card) {
        if (card == null) {
            return null;
        }
        final String name = key(card);
        String date = added.get(name);
        // Las partidas ("Fire // Ice"): su script se llama como su mitad izquierda.
        final int split = name.indexOf(" // ");
        if (date == null && split > 0) {
            date = added.get(name.substring(0, split));
        }
        return date;
    }

    private static Map<String, String> readAdded() {
        final Map<String, String> out = new HashMap<>();
        try (java.io.InputStream in = CardLibrary.class.getResourceAsStream("cartas-en-forge.txt")) {
            if (in == null) {
                return out;
            }
            final java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                final int tab = line.indexOf('\t');
                if (tab > 0) {
                    out.putIfAbsent(line.substring(tab + 1).toLowerCase(Locale.ROOT),
                            line.substring(0, tab));
                }
            }
        } catch (final java.io.IOException e) {
            // Sin tabla, "Lo ultimo" ordena por nombre: feo, pero no rompe nada.
        }
        return out;
    }

    /** Busca, filtra y ordena. */
    public List<PaperCard> find(final Query q) {
        final String text = q.text == null ? "" : q.text.trim().toLowerCase(Locale.ROOT);
        final Predicate<PaperCard> legal = q.format == null ? null : q.format.getFilterRules();
        final boolean byOwnership = q.ownership != Ownership.ALL && q.owned != null;
        final Map<String, PaperCard> inSet = q.setCode == null ? null
                : bySet.getOrDefault(q.setCode, Collections.emptyMap());

        final List<PaperCard> out = new ArrayList<>();
        for (int i = 0; i < index.size(); i++) {
            if (q.rulesText ? !index.matchesText(i, text) : !index.matches(i, text)) {
                continue;
            }
            PaperCard card = index.cardAt(i);
            final String name = key(card);
            if (inSet != null) {
                // Con expansion elegida se ensenya SU impresion: su arte y su
                // rareza, que es lo que se viene a mirar.
                card = inSet.get(name);
                if (card == null) {
                    continue;
                }
            }
            if (byOwnership && q.owned.contains(name) != (q.ownership == Ownership.OWNED)) {
                continue;
            }
            if (legal != null && !legal.test(card)) {
                continue;
            }
            if (q.extra != null && !q.extra.test(card)) {
                continue;
            }
            out.add(card);
        }
        out.sort(comparator(q.sort));
        return out;
    }

    private Comparator<PaperCard> comparator(final Sort sort) {
        final Comparator<PaperCard> byName = Comparator.comparing(this::shownName);
        switch (sort == null ? Sort.NAME : sort) {
            case COST:
                return Comparator.comparingInt((PaperCard c) -> c.getRules().getManaCost().getCMC())
                        .thenComparing(byName);
            case NEWEST:
                // Lo nuevo primero; lo que no tiene fecha, al final.
                return Comparator.comparing((PaperCard c) -> firstPrinted.get(key(c)),
                        Comparator.nullsLast(Comparator.<Date>reverseOrder())).thenComparing(byName);
            case TYPE:
                return Comparator.comparing(DeckEditor::groupOf).thenComparing(byName);
            case ADDED:
                // Lo que la tabla no trae va PRIMERO: es lo que llego despues
                // de generarla (actualizar.bat la rehace, pero si algun dia no,
                // lo nuevo no puede irse al fondo, que es lo que se busca).
                return Comparator.comparing(this::addedToForge,
                        Comparator.nullsFirst(Comparator.<String>reverseOrder())).thenComparing(byName);
            default:
                return byName;
        }
    }

    /**
     * Los nombres de una coleccion, en minusculas: lo que pide {@link Query#owned}.
     *
     * <p>Por NOMBRE: tener el Sol Ring de otra edicion es tener el Sol Ring,
     * la misma regla que usan la coleccion de la Quest y su constructor.
     */
    public static Set<String> namesOf(final Iterable<Map.Entry<PaperCard, Integer>> pool) {
        final Set<String> out = new HashSet<>();
        if (pool != null) {
            for (final Map.Entry<PaperCard, Integer> e : pool) {
                out.add(key(e.getKey()));
            }
        }
        return out;
    }

    private static String key(final PaperCard card) {
        return card.getName().toLowerCase(Locale.ROOT);
    }

    private String shownName(final PaperCard card) {
        final String s = shown.get(key(card));
        return s != null ? s : forge.neo.card.CardText.nameOf(card).toLowerCase(Locale.ROOT);
    }

    private static Date dateOf(final CardEdition ed) {
        return ed.getDate() == null ? new Date(0) : ed.getDate();
    }
}
