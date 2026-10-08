package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * <b>De que expansiones salen las cartas de una run.</b>
 *
 * <p>Pedido en Discord el 02-10-2026 por un jugador de los de antes: <i>"Is
 * there a way to restrict the adventure and/or ascent modes to a certain
 * edition or card sets (e.g. 4th ed and earlier)?"</i>. Tres formas, elegidas
 * al montar la run y guardadas con ella:
 *
 * <ul>
 *   <li><b>Todas</b>, la de fabrica: la run de siempre, sin filtrar nada.</li>
 *   <li><b>Desde una expansion hasta otra</b>, por fecha: "de Alpha a Fourth
 *       Edition". Vale una carta con <b>alguna</b> impresion en una expansion
 *       de esas fechas.</li>
 *   <li><b>Las expansiones que se elijan</b>, una a una: un bloque a medida
 *       ("Zendikar, Worldwake y Rise of the Eldrazi", o todo lo de Marvel).
 *       Pedido en Discord el 03-10-2026: <i>"being able to manually choose the
 *       sets instead of selecting a time range"</i>. Hasta entonces era "solo
 *       una"; una run guardada con una sola se sigue leyendo igual.</li>
 * </ul>
 *
 * <p><b>Las promociones no cuentan</b> ({@link #COUNTED}): Forge fecha algunos
 * sobres de promos por su PRIMERA carta, y "Media Inserts" (1995) trae a Aang
 * y al Capitan America. Contandolos, "hasta Fourth Edition" se llenaba de
 * cartas de 2025. Solo cuentan las expansiones de verdad y sus reediciones.
 *
 * <p>Lo filtra TODO lo que reparte cartas en la run, que es lo que pedia el
 * jugador — no quiere solo SU mazo antiguo, quiere no encontrarse las reglas
 * nuevas en la mesa:
 *
 * <ul>
 *   <li>los comandantes que se pueden elegir y el mazo de salida
 *       ({@link AscentSeedDeck});</li>
 *   <li>los premios, la tienda y las tierras de los eventos
 *       ({@link AscentRewards});</li>
 *   <li>y los <b>rivales</b>, que son los preconstruidos de Forge y ninguno es
 *       de antes de 1995: cada uno se convierte con {@link #restrict} — sus
 *       cartas modernas por otras del pozo de su color, coste y rareza, y sus
 *       tierras raras por basicas. Lo de la rareza es lo que conserva la
 *       escalera de dificultad ({@code AscentBattle.power}).</li>
 * </ul>
 *
 * <p>Las cartas se ensenyan con <b>su impresion de esas expansiones</b>
 * ({@link #printing}): quien elige "hasta Fourth Edition" quiere ver el arte
 * de Fourth Edition, no la reimpresion de 2024.
 *
 * <p>Java de la 8 a proposito: Android lo usa por el jar.
 */
public final class AscentPool {

    /** Que clase de restriccion. */
    public enum Kind {
        ALL, RANGE, SET
    }

    /** Las expansiones que cuentan: las de verdad y sus reediciones, sin promos. */
    public static final EnumSet<CardEdition.Type> COUNTED = EnumSet.of(
            CardEdition.Type.CORE, CardEdition.Type.EXPANSION, CardEdition.Type.STARTER,
            CardEdition.Type.REPRINT, CardEdition.Type.COLLECTOR_EDITION, CardEdition.Type.DRAFT,
            CardEdition.Type.COMMANDER, CardEdition.Type.MULTIPLAYER, CardEdition.Type.BOXED_SET,
            CardEdition.Type.DUEL_DECK);

    /**
     * Cuantas cartas (sin contar tierras) hacen falta para que una run tenga
     * sentido: el mazo de salida, tres premios por nodo durante tres actos y
     * los rivales convertidos. Por debajo, la pantalla no deja empezar.
     */
    public static final int MIN_CARDS = 120;

    public static final AscentPool ALL = new AscentPool(Kind.ALL, null, null);

    public final Kind kind;
    /** Codigo de la primera expansion (en {@link Kind#SET}, la primera de {@link #sets}). */
    public final String from;
    /** Codigo de la ultima expansion del rango; null en {@link Kind#SET}. */
    public final String to;
    /**
     * Las expansiones elegidas en {@link Kind#SET}, de la mas vieja a la mas
     * nueva (asi "A,B" y "B,A" son el mismo pozo y la misma cache). Vacia en
     * los otros dos.
     */
    public final List<String> sets;

    private AscentPool(final Kind kind, final String from, final String to) {
        this(kind, from, to, Collections.<String>emptyList());
    }

    private AscentPool(final Kind kind, final String from, final String to, final List<String> sets) {
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.sets = Collections.unmodifiableList(sets);
    }

    /** De la expansion {@code from} a la {@code to}, las dos incluidas (en cualquier orden). */
    public static AscentPool range(final String from, final String to) {
        if (from == null || to == null) {
            return ALL;
        }
        final CardEdition a = edition(from);
        final CardEdition b = edition(to);
        if (a != null && b != null && a.getDate() != null && b.getDate() != null
                && a.getDate().after(b.getDate())) {
            return new AscentPool(Kind.RANGE, to, from);
        }
        return new AscentPool(Kind.RANGE, from, to);
    }

    public static AscentPool set(final String code) {
        return code == null ? ALL : sets(Collections.singletonList(code));
    }

    /**
     * Las expansiones que se elijan, sin repetir y ordenadas por fecha. Sin
     * ninguna, un pozo {@link Kind#SET} vacio: no es "todas", es "aun no has
     * elegido", y {@link #problem} lo dice ("Elige las expansiones").
     */
    public static AscentPool sets(final java.util.Collection<String> codes) {
        final List<String> list = new ArrayList<>();
        if (codes != null) {
            for (final String c : codes) {
                if (c != null && !c.trim().isEmpty() && !list.contains(c.trim())) {
                    list.add(c.trim());
                }
            }
        }
        list.sort(Comparator.comparing((String c) -> {
            final CardEdition ed = edition(c);
            return ed == null || ed.getDate() == null ? new Date(Long.MAX_VALUE) : ed.getDate();
        }).thenComparing(c -> c));
        return new AscentPool(Kind.SET, list.isEmpty() ? null : list.get(0), null, list);
    }

    public boolean isAll() {
        return kind == Kind.ALL;
    }

    /** Como se guarda en la run: "", "range:LEA:4ED", "set:LEG" o "set:ZEN,WWK,ROE". */
    public String serialize() {
        switch (kind) {
            case RANGE:
                return "range:" + from + ":" + to;
            case SET:
                return "set:" + String.join(",", sets);
            default:
                return "";
        }
    }

    /** Lo contrario de {@link #serialize}. Cualquier cosa rara es "todas". */
    public static AscentPool parse(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return ALL;
        }
        // "set:" a secas es "aun no has elegido ninguna", no "todas": la
        // pantalla de Android lo manda asi mientras se eligen, y tomarlo por
        // "todas" encenderia Empezar con un pozo que el jugador no ha pedido.
        if ("set:".equals(raw.trim())) {
            return sets(Collections.<String>emptyList());
        }
        final String[] parts = raw.trim().split(":");
        if ("range".equals(parts[0]) && parts.length == 3) {
            return range(parts[1], parts[2]);
        }
        if ("set".equals(parts[0]) && parts.length == 2) {
            return sets(java.util.Arrays.asList(parts[1].split(",")));
        }
        return ALL;
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof AscentPool && serialize().equals(((AscentPool) o).serialize());
    }

    @Override
    public int hashCode() {
        return serialize().hashCode();
    }

    @Override
    public String toString() {
        return isAll() ? "todas" : serialize();
    }

    // ------------------------------------------------------------------
    //  Que expansiones se pueden elegir
    // ------------------------------------------------------------------

    /**
     * Las expansiones que se ofrecen, de la mas vieja a la mas nueva (que es
     * como se lee un "desde / hasta"). Solo las que cuentan y traen cartas.
     */
    public static List<CardEdition> editions() {
        final List<CardEdition> out = new ArrayList<>();
        for (final CardEdition ed : FModel.getMagicDb().getEditions()) {
            if (COUNTED.contains(ed.getType()) && ed.getDate() != null && !ed.getAllCardsInSet().isEmpty()) {
                out.add(ed);
            }
        }
        out.sort(Comparator.comparing(CardEdition::getDate).thenComparing(CardEdition::getCode));
        return out;
    }

    /**
     * <b>Una expansion al azar que de para una run</b> (Discord, 08-10-2026:
     * <i>"what about a random set selection button? It could be chaotic, but
     * it would definitely be fun"</i>). Ana: una sola expansion.
     *
     * <p>Solo de las de verdad —basicas y expansiones— ya publicadas. Mirar si
     * una da para una run ({@link #problem}) recorre todas las cartas, asi que
     * antes se descartan las que no llegan ni de lejos con su lista (lo
     * barato), y de las que quedan se prueban por orden al azar hasta dar con
     * una. La que se elige se guarda con la run como cualquier otro pozo: el
     * codigo de la run la lleva.
     *
     * <p>Compartida con Android: nada de API que no tenga en la 26.
     *
     * @param avoid la que ya estaba puesta, para que otro toque cambie; o null
     * @return su codigo, o null si no hay ninguna que valga (no deberia pasar)
     */
    public static String randomSet(final AscentRun.Mode mode, final java.util.Random rnd, final String avoid) {
        final List<CardEdition> candidates = new ArrayList<>();
        final Date now = new Date();
        for (final CardEdition ed : editions()) {
            if ((ed.getType() == CardEdition.Type.CORE || ed.getType() == CardEdition.Type.EXPANSION)
                    && !ed.getDate().after(now) && !ed.getCode().equals(avoid)
                    && ed.getAllCardsInSet().size() >= MIN_CARDS) {
                candidates.add(ed);
            }
        }
        Collections.shuffle(candidates, rnd);
        for (int i = 0; i < candidates.size() && i < RANDOM_TRIES; i++) {
            final String code = candidates.get(i).getCode();
            if (set(code).problem(mode) == null) {
                return code;
            }
        }
        return null;
    }

    /** Cuantas se prueban como mucho: cada una recorre las cartas. */
    private static final int RANDOM_TRIES = 30;

    private static CardEdition edition(final String code) {
        try {
            return FModel.getMagicDb().getEditions().get(code);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Los codigos de las expansiones que entran. Vacio en {@link Kind#ALL}. */
    public Set<String> codes() {
        final Set<String> out = new HashSet<>();
        if (kind == Kind.SET) {
            out.addAll(sets);
        } else if (kind == Kind.RANGE) {
            final CardEdition a = edition(from);
            final CardEdition b = edition(to);
            if (a == null || b == null || a.getDate() == null || b.getDate() == null) {
                return out;
            }
            final Date start = a.getDate();
            final Date end = b.getDate();
            for (final CardEdition ed : FModel.getMagicDb().getEditions()) {
                final Date d = ed.getDate();
                if (d != null && COUNTED.contains(ed.getType()) && !d.before(start) && !d.after(end)) {
                    out.add(ed.getCode());
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Que cartas entran
    // ------------------------------------------------------------------

    /** Lo calculado para un pozo: se recorren las 95.000 impresiones una vez. */
    private static final class Index {
        /** Nombre -> la impresion que se ensenya: la mas reciente DENTRO del pozo. */
        final Map<String, PaperCard> byName = new HashMap<>();
        /** Hechizos que se pueden lanzar (con coste), por nombre, para premios y cambios. */
        final List<PaperCard> spells = new ArrayList<>();
        int commanders;
    }

    private static final Map<String, Index> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<String, Index>(4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, Index> eldest) {
                    return size() > 3;
                }
            });

    private Index index() {
        final String key = serialize();
        Index idx = CACHE.get(key);
        if (idx != null) {
            return idx;
        }
        idx = new Index();
        final Set<String> codes = codes();
        final Map<String, Date> dates = new HashMap<>();
        for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards()) {
            if (!codes.contains(p.getEdition()) || p.getName().startsWith("A-") || isAnte(p)) {
                continue;
            }
            final CardEdition ed = edition(p.getEdition());
            final Date d = ed == null ? null : ed.getDate();
            final Date had = dates.get(p.getName());
            if (!idx.byName.containsKey(p.getName()) || (d != null && (had == null || d.after(had)))) {
                idx.byName.put(p.getName(), p);
                dates.put(p.getName(), d);
            }
        }
        final List<String> names = new ArrayList<>(idx.byName.keySet());
        Collections.sort(names);
        for (final String n : names) {
            final PaperCard p = idx.byName.get(n);
            if (p.getRules() == null || p.getRules().getType().isLand()) {
                continue;
            }
            final forge.card.mana.ManaCost cost = p.getRules().getManaCost();
            if (cost == null || cost.isNoCost()) {
                continue;
            }
            idx.spells.add(p);
            if (p.getRules().canBeCommander()) {
                idx.commanders++;
            }
        }
        CACHE.put(key, idx);
        return idx;
    }

    /**
     * Las cartas de apuesta (ante). El motor las QUITA del mazo al empezar si
     * no se juega por apuesta, con un aviso (Match.getRemovedAnteCards) — y en
     * las expansiones viejas hay unas cuantas. Sin esto, el rival de una run
     * "hasta Fourth Edition" empezaba con un Demonic Attorney de menos y un
     * cartel en medio. Es la misma pregunta que hace el motor.
     */
    public static boolean isAnte(final PaperCard p) {
        try {
            for (final String k : p.getRules().getMainPart().getKeywords()) {
                if (ANTE_KEYWORD.equals(k)) {
                    return true;
                }
            }
        } catch (final RuntimeException e) {
            return false;
        }
        return false;
    }

    private static final String ANTE_KEYWORD =
            "Remove CARDNAME from your deck before playing if you're not playing for ante.";

    /** Si la carta entra. Las basicas entran siempre: sin ellas no hay mazo. */
    public boolean allows(final PaperCard card) {
        if (isAll() || card == null) {
            return true;
        }
        if (card.getRules() != null && card.getRules().getType().isBasicLand()) {
            return true;
        }
        return index().byName.containsKey(card.getName());
    }

    /**
     * La impresion de esa carta que se ensenya en esta run: la de las
     * expansiones elegidas. Con "todas", la misma que llega. Y antes que
     * nada, <b>tu arte favorito</b> si lo tienes, el interruptor de
     * {@link AscentArt} esta puesto y es de estas expansiones.
     */
    public PaperCard printing(final PaperCard card) {
        final PaperCard favourite = AscentArt.favourite(card, this);
        if (favourite != card) {
            return favourite;
        }
        if (isAll() || card == null) {
            return card;
        }
        final PaperCard p = index().byName.get(card.getName());
        return p == null ? card : p;
    }

    /** Cuantos hechizos (cartas con coste, sin tierras) entran. */
    public int spellCount() {
        return isAll() ? Integer.MAX_VALUE : index().spells.size();
    }

    /** Cuantas criaturas legendarias (posibles comandantes) entran. */
    public int commanderCount() {
        return isAll() ? Integer.MAX_VALUE : index().commanders;
    }

    /**
     * Por que no se puede jugar una run con este pozo, o {@code null} si se
     * puede: la clave de texto para la pantalla.
     */
    public String problem(final AscentRun.Mode mode) {
        if (isAll()) {
            return null;
        }
        if (codes().isEmpty()) {
            return "ascent.pool.problem.none";
        }
        if (spellCount() < MIN_CARDS) {
            return "ascent.pool.problem.few";
        }
        if (mode == AscentRun.Mode.COMMANDER && commanderCount() == 0) {
            return "ascent.pool.problem.noCommander";
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Convertir un mazo al pozo
    // ------------------------------------------------------------------

    /**
     * Una copia del mazo con SOLO cartas del pozo.
     *
     * <p>Cada carta que no entra se cambia por otra que si, de los colores del
     * mazo (en Commander, de la identidad del comandante), de la misma rareza
     * si la hay y del coste mas parecido. Las tierras que no son basicas se
     * cambian por basicas de sus colores. En Commander, si el comandante no
     * entra, se elige otro del pozo con la misma identidad (o la mas parecida)
     * y el mazo se ajusta a la suya.
     *
     * <p>Determinista con el mismo {@code rnd}: un rival no puede cambiar si
     * cierras el juego y vuelves ({@code AscentBattle}, "el rival no se sortea
     * dos veces").
     */
    public Deck restrict(final Deck source, final AscentRun.Mode mode, final Random rnd) {
        if (isAll() || source == null) {
            return source;
        }
        final Deck deck = new Deck(source, source.getName());
        final boolean singleton = mode == AscentRun.Mode.COMMANDER;
        ColorSet colours = singleton ? fixCommander(deck, rnd) : null;
        if (colours == null) {
            colours = coloursOf(deck.getMain());
        }

        final CardPool main = deck.getMain();
        final Set<String> used = new HashSet<>();
        // El comandante cuenta como usado: si no, el cambio de una carta de
        // fuera podia ser EL MISMO (un incoloro como Kozilek cabe en cualquier
        // identidad) y el mazo salia con dos copias, ilegal. Visto en
        // questcheck el 03-10-2026 con el bloque de Zendikar.
        if (deck.has(DeckSection.Commander)) {
            for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                used.add(e.getKey().getName());
            }
        }
        final List<PaperCard> gone = new ArrayList<>();
        final List<Integer> goneCopies = new ArrayList<>();
        int lands = 0;
        final CardPool keep = new CardPool();
        for (final Map.Entry<PaperCard, Integer> e : main) {
            final PaperCard c = e.getKey();
            final boolean fits = !singleton
                    || c.getRules().getColorIdentity().hasNoColorsExcept(colours);
            if (allows(c) && fits) {
                keep.add(printing(c), e.getValue());
                used.add(c.getName());
            } else if (c.getRules().getType().isLand()) {
                lands += e.getValue();
            } else {
                gone.add(c);
                goneCopies.add(e.getValue());
            }
        }
        main.clear();
        main.addAll(keep);

        final List<PaperCard> candidates = new ArrayList<>();
        for (final PaperCard p : index().spells) {
            final ColorSet c = singleton ? p.getRules().getColorIdentity() : p.getRules().getColor();
            // Ni las que Forge marca como "la IA no sabe jugarla": casi siempre
            // es un rival quien recibe el cambio, y es la IA.
            if (c.hasNoColorsExcept(colours) && !p.getRules().getAiHints().getRemAIDecks()) {
                candidates.add(p);
            }
        }
        for (int i = 0; i < gone.size(); i++) {
            final PaperCard old = gone.get(i);
            final int copies = singleton ? 1 : Math.min(4, goneCopies.get(i));
            final PaperCard swap = closest(old, candidates, used, rnd);
            if (swap == null) {
                lands += goneCopies.get(i);
                continue;
            }
            used.add(swap.getName());
            main.add(swap, copies);
            // Si en Estandar habia mas de cuatro (no deberia), lo que sobra son basicas.
            lands += goneCopies.get(i) - copies;
        }
        addBasics(main, colours, lands);
        return deck;
    }

    /**
     * Si el comandante no entra, otro del pozo. Devuelve la identidad con la
     * que hay que ajustar el mazo, o null si no hay seccion de comandante.
     */
    private ColorSet fixCommander(final Deck deck, final Random rnd) {
        final CardPool section = deck.get(DeckSection.Commander);
        if (section == null || section.isEmpty()) {
            return null;
        }
        final PaperCard current = section.toFlatList().get(0);
        if (allows(current)) {
            final PaperCard shown = printing(current);
            section.clear();
            section.add(shown);
            return shown.getRules().getColorIdentity();
        }
        final ColorSet want = current.getRules().getColorIdentity();
        final List<PaperCard> same = new ArrayList<>();
        final List<PaperCard> inside = new ArrayList<>();
        final List<PaperCard> any = new ArrayList<>();
        for (final PaperCard p : index().spells) {
            if (!p.getRules().canBeCommander()) {
                continue;
            }
            final ColorSet id = p.getRules().getColorIdentity();
            any.add(p);
            if (id.equals(want)) {
                same.add(p);
            } else if (id.hasNoColorsExcept(want)) {
                inside.add(p);
            }
        }
        List<PaperCard> from = !same.isEmpty() ? same : !inside.isEmpty() ? inside : any;
        if (from == inside) {
            // De las que caben, las de mas colores: se tira lo menos posible del mazo.
            int best = 0;
            for (final PaperCard p : inside) {
                best = Math.max(best, p.getRules().getColorIdentity().countColors());
            }
            final List<PaperCard> widest = new ArrayList<>();
            for (final PaperCard p : inside) {
                if (p.getRules().getColorIdentity().countColors() == best) {
                    widest.add(p);
                }
            }
            from = widest;
        }
        if (from.isEmpty()) {
            return want;
        }
        final PaperCard chosen = from.get(rnd.nextInt(from.size()));
        section.clear();
        section.add(chosen);
        return chosen.getRules().getColorIdentity();
    }

    /** La del pozo mas parecida: misma rareza si la hay, el coste mas cercano, y un poco de azar. */
    private static PaperCard closest(final PaperCard old, final List<PaperCard> candidates,
                                     final Set<String> used, final Random rnd) {
        final int cmc = old.getRules().getManaCost().getCMC();
        final CardRarity rarity = bucket(old.getRarity());
        final boolean creature = old.getRules().getType().isCreature();
        List<PaperCard> best = new ArrayList<>();
        int bestScore = Integer.MAX_VALUE;
        for (final PaperCard p : candidates) {
            if (used.contains(p.getName())) {
                continue;
            }
            int score = Math.abs(p.getRules().getManaCost().getCMC() - cmc) * 2;
            if (bucket(p.getRarity()) != rarity) {
                score += 3;
            }
            if (p.getRules().getType().isCreature() != creature) {
                score += 1;
            }
            if (score < bestScore) {
                bestScore = score;
                best = new ArrayList<>();
                best.add(p);
            } else if (score == bestScore) {
                best.add(p);
            }
        }
        return best.isEmpty() ? null : best.get(rnd.nextInt(best.size()));
    }

    /** Las miticas cuentan como raras: en las expansiones viejas no hay miticas. */
    private static CardRarity bucket(final CardRarity r) {
        if (r == CardRarity.MythicRare || r == CardRarity.Special) {
            return CardRarity.Rare;
        }
        if (r == CardRarity.BasicLand) {
            return CardRarity.Common;
        }
        return r;
    }

    /** Los colores de los hechizos de un mazo de Estandar. */
    private static ColorSet coloursOf(final CardPool main) {
        byte mask = 0;
        for (final Map.Entry<PaperCard, Integer> e : main) {
            if (!e.getKey().getRules().getType().isLand()) {
                mask |= e.getKey().getRules().getColor().getColor();
            }
        }
        return ColorSet.fromMask(mask);
    }

    /** {@code howMany} basicas, repartidas entre los colores. Sin color, Wastes si entra. */
    private void addBasics(final CardPool main, final ColorSet colours, final int howMany) {
        if (howMany <= 0) {
            return;
        }
        final List<PaperCard> basics = new ArrayList<>();
        for (int i = 0; i < forge.card.MagicColor.WUBRG.length; i++) {
            if (colours.hasAnyColor(forge.card.MagicColor.WUBRG[i])) {
                final PaperCard b = printing(FModel.getMagicDb().getCommonCards()
                        .getCard(forge.card.MagicColor.Constant.BASIC_LANDS.get(i)));
                if (b != null) {
                    basics.add(b);
                }
            }
        }
        if (basics.isEmpty()) {
            final PaperCard wastes = FModel.getMagicDb().getCommonCards().getCard("Wastes");
            if (wastes != null) {
                basics.add(wastes);
            }
        }
        if (basics.isEmpty()) {
            return;
        }
        for (int i = 0; i < howMany; i++) {
            main.add(basics.get(i % basics.size()));
        }
    }
}
