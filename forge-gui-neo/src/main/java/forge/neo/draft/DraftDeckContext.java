package forge.neo.draft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import forge.card.CardEdition;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.deck.DeckContext;
import forge.util.storage.IStorage;

/**
 * Montar el mazo <b>con lo que has draftado</b>.
 *
 * <p>Es el tercer contexto del mismo editor — el general monta con todo Magic,
 * el de la aventura con tu coleccion, y este con las 45 cartas que has pasado
 * por delante. Todo lo demas (buscador, curva, columnas por tipo, click
 * derecho, validacion) es el mismo codigo. Ver {@link DeckContext}.
 *
 * <p>Existe porque un draft sin editor no es un draft: el mazo lo monta el
 * motor solo, y el motor elige DOS colores. Reportado jugando: <i>"me he hecho
 * un mono negro, pero al final del sobre ya no quedaba negro y he cogido
 * cartas rojas y verdes; ahora el mazo me las mete, y con ellas tierras rojas
 * que no quiero"</i>. Un mazo que no se puede tocar convierte 45 decisiones en
 * ninguna.
 *
 * <p><b>Donde vive el pool</b>, que es lo unico raro de aqui:
 *
 * <ul>
 *   <li>En un <b>draft</b>, la banda ({@code Sideboard}) es el pool ENTERO —
 *       las 45 cartas — y el mazo principal es una copia de lo que se juega.
 *       Lo deja asi {@link NeoDraft#save}.</li>
 *   <li>En un <b>sellado</b>, mazo y banda son <b>disjuntos</b>: lo que entro
 *       en el mazo salio de la banda ({@link NeoSealed}). El pool es la suma
 *       de los dos.</li>
 * </ul>
 *
 * <p>No es bonito que sean distintos, pero es un dato <b>seguro</b>: lo
 * garantiza quien escribe cada mazo, y asi los eventos ya guardados se abren
 * bien sin adivinar nada ni migrar ficheros. Deducirlo del contenido — "si la
 * banda contiene todo el mazo, es un draft" — fallaria justo con un pool que
 * tenga copias repetidas.
 *
 * <p>Las <b>tierras basicas</b> no salen de ningun sobre: en limitado se ponen
 * las que hagan falta. Van al catalogo aparte y sin techo.
 */
public final class DraftDeckContext implements DeckContext {

    private final DraftRun run;

    /** Cuantas copias tienes de cada impresion. Las basicas no estan aqui. */
    private final Map<PaperCard, Integer> owned = new HashMap<>();

    /**
     * Lo mismo, pero sumado por NOMBRE.
     *
     * <p>Es lo que de verdad pregunta {@link #owned(PaperCard)}: el techo de
     * copias del draft es "cuantas tienes de esta carta", no "cuantas tienes
     * de esta impresion exacta". Sin esto, {@code SealedDeckBuilder} devolviendo
     * OTRA impresion de la misma carta al montar el mazo automatico dejaba el
     * pool sin reconocerla — la misma trampa que ya evita
     * {@code QuestDeckContext}, y aqui no se habia aplicado.
     */
    private final Map<String, Integer> ownedByName = new HashMap<>();

    /** El catalogo: una entrada por impresion del pool, mas las cinco basicas. */
    private final List<PaperCard> pool = new ArrayList<>();

    /** Las impresiones que tienes de cada carta, por nombre. */
    private final Map<String, List<PaperCard>> printings = new HashMap<>();

    public DraftDeckContext(final DraftRun run) {
        this.run = run;
        final Deck deck = run == null ? null : run.getDeck();
        if (deck == null) {
            return;
        }

        count(deck.get(DeckSection.Sideboard));
        if (run.getKind() == DraftRun.Kind.SEALED) {
            count(deck.getMain());
        }

        pool.addAll(owned.keySet());
        pool.sort(java.util.Comparator.comparing(PaperCard::getName)
                .thenComparing(PaperCard::getEdition));
        pool.addAll(basics(deck));
    }

    private void count(final CardPool section) {
        if (section == null) {
            return;
        }
        for (final Map.Entry<PaperCard, Integer> e : section) {
            final PaperCard card = e.getKey();
            if (isBasic(card)) {
                continue;
            }
            owned.merge(card, e.getValue(), Integer::sum);
            ownedByName.merge(normalizedName(card.getName()), e.getValue(), Integer::sum);
            final List<PaperCard> arts =
                    printings.computeIfAbsent(card.getName(), k -> new ArrayList<>());
            if (!arts.contains(card)) {
                arts.add(card);
            }
        }
    }

    private static boolean isBasic(final PaperCard card) {
        return card != null && card.getRules() != null
                && card.getRules().getType().isBasicLand();
    }

    /** Igual que {@code DeckEditor.normalized}: junta aventura y hechizo, o carta de ambientacion. */
    private static String normalizedName(final String name) {
        return name == null ? "" : forge.StaticData.instance().getCommonCards().getNormalizedName(name);
    }

    /**
     * Las cinco basicas, de una edicion que las tenga todas.
     *
     * <p>Se prefiere una edicion que ya este en el pool: si los sobres eran de
     * Bloomburrow, las llanuras del mazo tambien lo son y la mesa no queda
     * hecha un mosaico. Si ninguna las trae, la que Forge prefiera por arte.
     */
    private static List<PaperCard> basics(final Deck deck) {
        final List<PaperCard> out = new ArrayList<>();
        final Map<String, PaperCard> already = basicsInDeck(deck);
        final String code = landSet(deck);
        for (final String name : forge.card.MagicColor.Constant.BASIC_LANDS) {
            // La que YA esta en el mazo manda sobre cualquier heuristica: si no,
            // el catalogo ofreceria otra impresion del mismo pantano y anyadir
            // uno partiria la lista en dos entradas con artes distintos.
            PaperCard card = already.get(name);
            if (card == null && code != null) {
                card = FModel.getMagicDb().getCommonCards().getCard(name, code);
            }
            if (card == null) {
                card = FModel.getMagicDb().getCommonCards().getCard(name);
            }
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    /** Que impresion de cada basica lleva ya el mazo. */
    private static Map<String, PaperCard> basicsInDeck(final Deck deck) {
        final Map<String, PaperCard> out = new LinkedHashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (isBasic(e.getKey())) {
                out.putIfAbsent(e.getKey().getName(), e.getKey());
            }
        }
        return out;
    }

    /** La edicion con basicas mas repetida en el pool, o null. */
    private static String landSet(final Deck deck) {
        final Map<String, Integer> seen = new LinkedHashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getAllCardsInASinglePool()) {
            seen.merge(e.getKey().getEdition(), e.getValue(), Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (final Map.Entry<String, Integer> e : seen.entrySet()) {
            final CardEdition ed = FModel.getMagicDb().getEditions().get(e.getKey());
            if (ed != null && CardEdition.Predicates.hasBasicLands.test(ed)
                    && e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        if (best != null) {
            return best;
        }
        final CardEdition fallback =
                CardEdition.Predicates.getPreferredArtEditionWithAllBasicLands();
        return fallback == null ? null : fallback.getCode();
    }

    // ---------------------------------------------------------------
    // Lo que el editor pregunta

    private boolean sealed() {
        return run != null && run.getKind() == DraftRun.Kind.SEALED;
    }

    @Override
    public String getLabel() {
        return (sealed() ? forge.neo.match.NeoFormat.SELLADO
                : forge.neo.match.NeoFormat.DRAFT).getLabel();
    }

    /**
     * Limitado: minimo 40 cartas y sin limite de copias.
     *
     * <p>Las copias no las limitan las reglas sino el pool — puedes llevar las
     * nueve islas que hayas abierto. Eso lo contesta {@link #owned(PaperCard)}.
     */
    @Override
    public DeckFormat deckFormat() {
        return (sealed() ? forge.game.GameType.Sealed
                : forge.game.GameType.Draft).getDeckFormat();
    }

    @Override
    public List<PaperCard> pool() {
        return pool;
    }

    @Override
    public int owned(final PaperCard card) {
        if (card == null) {
            return 0;
        }
        if (isBasic(card)) {
            return Integer.MAX_VALUE;
        }
        return ownedByName.getOrDefault(normalizedName(card.getName()), 0);
    }

    /**
     * Solo los artes que han salido en tus sobres.
     *
     * <p>Igual que en la aventura: cambiar una carta del pool por otra
     * impresion seria cambiarla por una que no tienes, y el editor la marcaria
     * ilegal acto seguido. Las basicas si admiten cualquier arte, porque no
     * salen del pool.
     */
    @Override
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        if (isBasic(card)) {
            return null;
        }
        final List<PaperCard> mine = printings.get(card.getName());
        return mine == null || mine.isEmpty() ? List.of() : new ArrayList<>(mine);
    }

    /** Esa columna no es un catalogo ni una coleccion: es lo que draftaste. */
    @Override
    public String catalogueLabel() {
        return forge.neo.NeoText.get(sealed() ? "deck.poolSealed" : "deck.poolDraft");
    }

    /**
     * El nombre no se toca.
     *
     * <p>El mazo de un evento no es un mazo suelto: es la mitad humana de un
     * {@link DeckGroup} que lleva ademas los siete rivales, y Forge da por
     * hecho que se llama igual que el grupo. Y el marcador — las victorias y
     * las derrotas — vive en {@code neo.properties} colgando de ese mismo
     * nombre. Renombrar aqui perderia la cuenta sin decirlo.
     */
    @Override
    public boolean canRename() {
        return false;
    }

    @Override
    public IStorage<Deck> storage() {
        return storage;
    }

    private final IStorage<Deck> storage = new GroupStorage();

    /**
     * Guardar es escribir dentro del evento.
     *
     * <p>El editor guarda con {@code storage().add(deck)} y no tiene por que
     * saber que aqui no hay ninguna carpeta de mazos: lo que hay es un
     * {@link DeckGroup} con tu mazo y los siete de los rivales, y se reescribe
     * entero. Por eso esto es una fachada de {@link IStorage} y no un almacen
     * de verdad: dentro solo hay un mazo, el tuyo.
     */
    private final class GroupStorage implements IStorage<Deck> {

        private DeckGroup group() {
            if (run == null) {
                return null;
            }
            for (final DeckGroup g : run.getKind().storage()) {
                if (g.getName().equals(run.getName())) {
                    return g;
                }
            }
            return null;
        }

        @Override
        public void add(final Deck item) {
            final DeckGroup g = group();
            if (g == null || item == null) {
                return;
            }
            // El mazo humano tiene que llamarse como el grupo: es lo que da por
            // hecho DeckGroupSerializer al releerlo del disco.
            g.setHumanDeck(item.getName().equals(g.getName())
                    ? item : new Deck(item, g.getName()));
            run.getKind().storage().add(g);
        }

        @Override
        public void add(final String name, final Deck item) {
            add(item);
        }

        @Override
        public Deck get(final String name) {
            final DeckGroup g = group();
            return g == null ? null : g.getHumanDeck();
        }

        @Override
        public boolean contains(final String name) {
            return run != null && run.getName().equals(name) && group() != null;
        }

        @Override
        public Collection<String> getItemNames() {
            return group() == null ? List.of() : List.of(run.getName());
        }

        @Override
        public int size() {
            return getItemNames().size();
        }

        @Override
        public Deck find(final Predicate<Deck> condition) {
            final Deck mine = get(null);
            return mine != null && condition.test(mine) ? mine : null;
        }

        @Override
        public java.util.Iterator<Deck> iterator() {
            final Deck mine = get(null);
            return (mine == null ? List.<Deck>of() : List.of(mine)).iterator();
        }

        @Override
        public Stream<Deck> stream() {
            final Deck mine = get(null);
            return mine == null ? Stream.empty() : Stream.of(mine);
        }

        /**
         * Borrar el mazo seria borrar el evento entero, y eso no se decide
         * desde el editor: se hace desde la papelera de la pantalla de mazos,
         * que avisa de que se van tambien los siete rivales.
         */
        @Override
        public void delete(final String deckName) {
        }

        @Override
        public String getName() {
            return run == null ? "" : run.getName();
        }

        @Override
        public String getFullPath() {
            return run == null ? "" : run.getKind().storage().getFullPath();
        }

        @Override
        public IStorage<IStorage<Deck>> getFolders() {
            return null;
        }

        @Override
        public IStorage<Deck> tryGetFolder(final String path) {
            return null;
        }

        @Override
        public IStorage<Deck> getFolderOrCreate(final String path) {
            return this;
        }
    }
}
