package forge.neo.ascent;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.deck.Deck;
import forge.item.PaperCard;
import forge.deck.io.DeckStorage;
import forge.localinstance.properties.ForgeConstants;
import forge.util.storage.IStorage;
import forge.util.storage.StorageImmediatelySerialized;

/**
 * Donde vive el mazo de una run: un {@code .dck} normal, en su propia carpeta.
 *
 * <h2>Por que un .dck y no dentro de la run</h2>
 *
 * <p>{@link AscentRun} se guarda a mano en {@code neo.properties} — cuatro
 * numeros y dos listas de texto — y meter ahi treinta cartas con su edicion
 * seria inventar un formato de mazo teniendo uno que el motor ya lee, escribe
 * y sabe reparar. Ademas asi el mazo de una run se puede <b>mirar</b> con las
 * mismas piezas que cualquier otro mazo (el editor, {@code DeckStackView},
 * {@code CardPile}), que es lo que pide la fase 3.
 *
 * <h2>Carpeta propia</h2>
 *
 * <p>{@code decks/ascenso/}, no la de Estandar ni la de Commander. Un mazo de
 * run es de veinte o treinta cartas y <b>no es legal en ningun formato</b>:
 * suelto en la carpeta de Commander saldria en la pantalla de inicio como un
 * mazo mas, se elegiria sin querer y el motor lo rechazaria al empezar la
 * partida. Y al reves: el jugador no tiene por que ver sus mazos de verdad
 * mezclados con los cadaveres de sus runs.
 *
 * <p>Se limpia sola: al abandonar o perder una run, {@link #remove} borra el
 * fichero. Sin eso, en un mes ahi dentro habria cuarenta mazos muertos.
 */
public final class AscentDecks {

    private AscentDecks() {
    }

    /** El nombre de la carpeta dentro de {@code decks/}. */
    private static final String FOLDER = "ascenso";

    private static IStorage<Deck> storage;

    /**
     * En las maquetas ({@link AscentRun#demo}), los mazos viven aqui y no en
     * el disco. {@code null} fuera de ellas.
     */
    private static Map<String, Deck> inMemory;

    /**
     * Los mazos que <b>este proceso</b> ha escrito en {@code decks/ascenso/}.
     *
     * <p>Solo lo leen los comprobadores ({@link AscentCheckGuard}), para borrar
     * al terminar lo que escribieron ellos y nada mas. "Lo que no estaba al
     * empezar" no vale: el juego abierto puede estar empezando una run de
     * verdad mientras tanto, y su mazo tambien seria nuevo.
     */
    private static final Set<String> written = new LinkedHashSet<>();

    /**
     * Desde aqui, nada se escribe ni se borra en {@code decks/ascenso/}.
     *
     * <p>Leer si se lee — una maqueta sobre la run guardada tiene que ensenyar
     * su mazo —, pero se lee una <b>copia</b>: las pantallas cambian el mazo
     * que cargan antes de guardarlo, y el original es el que tiene en cache el
     * almacen del motor.
     */
    static synchronized void keepInMemory() {
        if (inMemory == null) {
            inMemory = new java.util.HashMap<>();
        }
    }

    /** La carpeta de mazos de Ascenso, creada la primera vez que se pide. */
    public static synchronized IStorage<Deck> storage() {
        if (storage == null) {
            storage = new StorageImmediatelySerialized<>("Ascenso",
                    new DeckStorage(new File(ForgeConstants.DECK_BASE_DIR, FOLDER),
                            ForgeConstants.DECK_BASE_DIR, true),
                    true);
        }
        return storage;
    }

    /** Guarda (o reescribe) el mazo de la run. */
    public static void save(final Deck deck) {
        if (deck == null) {
            return;
        }
        synchronized (AscentDecks.class) {
            if (inMemory != null) {
                inMemory.put(deck.getName(), deck);
                return;
            }
        }
        // add() reescribe si ya existe, que es justo lo que hace falta: el
        // mazo de la run cambia en cada nodo.
        storage().add(deck);
        synchronized (AscentDecks.class) {
            written.add(deck.getName());
        }
    }

    /** El mazo de esa run, o {@code null} si no esta. */
    public static Deck load(final String name) {
        if (name == null) {
            return null;
        }
        synchronized (AscentDecks.class) {
            if (inMemory != null) {
                Deck deck = inMemory.get(name);
                if (deck == null) {
                    final Deck saved = storage().get(name);
                    if (saved != null) {
                        deck = new Deck(saved, name);
                        inMemory.put(name, deck);
                    }
                }
                return deck;
            }
        }
        return storage().get(name);
    }

    /** El mazo de esa run. */
    public static Deck load(final AscentRun run) {
        return run == null ? null : load(run.getDeckName());
    }

    // ------------------------------------------------------------------
    //  El arte, a mitad de run
    // ------------------------------------------------------------------

    /**
     * Con que arte se puede llevar esa carta: <b>todas sus impresiones</b>,
     * tambien en una run de unas expansiones. Discord, 06-10-2026: <i>"change
     * the printing of some of these because, 1) final fantasy sets duh, 2) some
     * of them are the Japanese cards lol 3) I hate marvel cards"</i>.
     *
     * <p>Es solo el dibujo y no hace trampa: el pozo de la run deja pasar
     * <b>por nombre</b> ({@link AscentPool#allows}), no por edicion, y la semilla
     * no mira el mazo despues de generarlo.
     */
    public static List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        final List<PaperCard> all = new ArrayList<>(
                forge.model.FModel.getMagicDb().getCommonCards().getAllCards(card.getName()));
        all.sort(Comparator.comparing(PaperCard::getEdition)
                .thenComparing(PaperCard::getCollectorNumber));
        return all;
    }

    /** Cuantas copias de ESTA impresion lleva el mazo (principal y mando). */
    public static int copiesOf(final AscentRun run, final PaperCard card) {
        final Deck deck = load(run);
        if (deck == null || card == null) {
            return 0;
        }
        int n = deck.getMain().count(card);
        if (deck.has(forge.deck.DeckSection.Commander)) {
            n += deck.get(forge.deck.DeckSection.Commander).count(card);
        }
        return n;
    }

    /**
     * Cambia el arte de {@code copies} copias de {@code from} y guarda el mazo
     * de la run. Mira el principal y el comandante; una foil sigue foil. No
     * toca las preferencias de Forge: es el mazo de esta run, no la carta.
     *
     * @return cuantas se han cambiado
     */
    public static int switchPrinting(final AscentRun run, final PaperCard from, PaperCard to,
                                     final int copies) {
        final Deck deck = load(run);
        if (deck == null || from == null || to == null || copies <= 0
                || !from.getName().equals(to.getName())) {
            return 0;
        }
        if (from.isFoil() && !to.isFoil()) {
            to = to.getFoiled();
        }
        if (from.equals(to)) {
            return 0;
        }
        int left = copies;
        int changed = 0;
        final List<forge.deck.CardPool> pools = new ArrayList<>();
        pools.add(deck.getMain());
        if (deck.has(forge.deck.DeckSection.Commander)) {
            pools.add(deck.get(forge.deck.DeckSection.Commander));
        }
        for (final forge.deck.CardPool pool : pools) {
            final int n = Math.min(left, pool.count(from));
            if (n > 0) {
                pool.remove(from, n);
                pool.add(to, n);
                left -= n;
                changed += n;
            }
        }
        if (changed > 0) {
            save(deck);
        }
        return changed;
    }

    /**
     * Las cartas del mazo, <b>de mas caro a mas barato</b>.
     *
     * <p>Es el orden en el que se ensenya un mazo de run en los cuatro sitios
     * donde aparece: el descanso, la tienda, el visor del mapa y el resumen del
     * final. Vive aqui —sin JavaFX— porque el resumen lo necesita cuando la
     * pantalla puede no llegar a existir, y porque si cada pantalla ordenara a
     * su manera, la carta que el jugador ya sabe donde esta cambiaria de sitio
     * segun por que puerta haya entrado.
     *
     * <p>De mayor a menor porque es la pregunta que se viene a contestar: lo
     * que sobra de un mazo de treinta casi siempre es la carta de coste siete
     * que nunca puedes pagar.
     *
     * <p>Las copias salen repetidas: son cartas del mazo y hay que poder
     * contarlas.
     */
    public static List<PaperCard> sortedByCost(final Deck deck) {
        final List<PaperCard> out = new ArrayList<>();
        if (deck == null) {
            return out;
        }
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            for (int i = 0; i < e.getValue(); i++) {
                out.add(e.getKey());
            }
        }
        out.sort(BY_COST);
        return out;
    }

    /**
     * Como se ensenya el mazo de una run: de mas caro a mas barato (de fabrica)
     * o <b>por tipo</b> (Discord, 09-10-2026: <i>"add the ability to sort by
     * type. I'd also love the ability to see how many lands I have as well as
     * other card types"</i>).
     *
     * <p>Es <b>una</b> preferencia para los cuatro sitios donde sale el mazo
     * (descanso, tienda, visor y resumen), por lo de siempre: si cada uno
     * ordenara a su manera, la carta que ya sabes donde esta cambiaria de sitio
     * segun por que puerta entres. No va con la run: {@code discard} borra sus
     * claves una a una y esta no esta entre ellas.
     */
    public static final String SORT_SETTING = "ascent.deckByType";

    public static boolean byType() {
        return forge.neo.NeoSettings.getBool(SORT_SETTING, false);
    }

    public static void setByType(final boolean on) {
        forge.neo.NeoSettings.setBool(SORT_SETTING, on);
        forge.neo.NeoSettings.save();
    }

    /** El mazo en el orden que haya elegido el jugador. Ver {@link #sort}. */
    public static List<PaperCard> sorted(final Deck deck) {
        return sort(sortedByCost(deck));
    }

    /**
     * Esas cartas en el orden elegido. Por tipo van en los grupos del editor de
     * mazos ({@code DeckEditor.GROUPS}: criaturas, hechizos, artefactos...
     * tierras), y <b>dentro de cada uno de mas cara a mas barata</b>: lo que se
     * viene a buscar sigue arriba, solo que ahora dentro de su tipo.
     */
    public static List<PaperCard> sort(final List<PaperCard> cards) {
        final List<PaperCard> out = new ArrayList<>(cards);
        out.sort(byType() ? Comparator.comparingInt(AscentDecks::groupIndex).thenComparing(BY_COST) : BY_COST);
        return out;
    }

    /**
     * De mas cara a mas barata, y <b>las tierras al final</b>, despues de los
     * hechizos de coste 0: cuestan 0 como ellos, pero por coste el mazo va en
     * grupos ({@link #headerOf}) y las tierras son el suyo.
     */
    private static final Comparator<PaperCard> BY_COST = Comparator
            .comparing((PaperCard c) -> c.getRules() != null && c.getRules().getType().isLand())
            .thenComparing(Comparator.comparingInt((PaperCard c) -> c.getRules() == null ? 0
                    : c.getRules().getManaCost().getCMC()).reversed())
            .thenComparing(PaperCard::getName);

    /**
     * El rotulo del grupo en el que va esa carta, en el orden elegido: por tipo,
     * su tipo ("Criaturas"); por coste, su coste ("Coste 5"), y las tierras
     * aparte. Ana, 09-10-2026: <i>"by cost no funciona, no noto que agrupe
     * nada"</i> — por coste salia la lista de siempre, sin nada que dijera
     * donde acaba un coste y empieza otro. Ahora las dos maneras se leen igual.
     */
    public static String headerOf(final PaperCard card) {
        if (byType()) {
            return forge.neo.deck.DeckEditor.groupLabel(groupOf(card));
        }
        if (card == null || card.getRules() == null) {
            return forge.neo.deck.DeckEditor.groupLabel(forge.neo.deck.DeckEditor.OTHER);
        }
        if (card.getRules().getType().isLand()) {
            return forge.neo.deck.DeckEditor.groupLabel(forge.neo.deck.DeckEditor.LANDS);
        }
        return forge.neo.NeoText.get("ascent.deck.costGroup", card.getRules().getManaCost().getCMC());
    }

    /** El grupo del editor de mazos en el que cae: {@code DeckEditor.groupOf}. */
    public static String groupOf(final PaperCard card) {
        return card == null || card.getRules() == null
                ? forge.neo.deck.DeckEditor.OTHER : forge.neo.deck.DeckEditor.groupOf(card);
    }

    private static int groupIndex(final PaperCard card) {
        final String group = groupOf(card);
        final String[] groups = forge.neo.deck.DeckEditor.GROUPS;
        for (int i = 0; i < groups.length; i++) {
            if (groups[i].equals(group)) {
                return i;
            }
        }
        return groups.length;
    }

    /**
     * Cuantas hay de cada tipo, en el orden de lectura del editor y sin los
     * tipos de los que no hay ninguna: "Creatures 23 · Spells 10 · Lands 28".
     * Es el numero con el que se decide que sobra (Discord, 09-10-2026).
     */
    public static String typeLine(final List<PaperCard> cards) {
        final Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (final String g : forge.neo.deck.DeckEditor.GROUPS) {
            counts.put(g, 0);
        }
        for (final PaperCard c : cards) {
            counts.merge(groupOf(c), 1, Integer::sum);
        }
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(forge.neo.deck.DeckEditor.groupLabel(e.getKey())).append(' ').append(e.getValue());
        }
        return sb.toString();
    }

    /** Borra el mazo de una run terminada. */
    public static void remove(final String name) {
        synchronized (AscentDecks.class) {
            if (inMemory != null) {
                if (name != null) {
                    inMemory.remove(name);
                }
                return;
            }
        }
        if (name != null && storage().contains(name)) {
            storage().delete(name);
        }
    }

    /** Los mazos que hay en la carpeta, por nombre. */
    static synchronized Set<String> names() {
        return new HashSet<>(storage().getItemNames());
    }

    /**
     * Borra los mazos que <b>este proceso</b> ha escrito y que no estaban en
     * {@code before}. Es la red de {@link AscentCheckGuard}.
     *
     * @param before lo que habia en la carpeta al empezar ({@link #names()})
     * @param keep   el mazo de la run en curso, que no se toca nunca
     * @return los que ha borrado
     */
    static List<String> removeWrittenSince(final Set<String> before, final String keep) {
        final List<String> mine;
        synchronized (AscentDecks.class) {
            if (inMemory != null) {
                return List.of();
            }
            mine = new ArrayList<>(written);
        }
        final List<String> gone = new ArrayList<>();
        for (final String name : mine) {
            if (before.contains(name) || name.equals(keep) || !storage().contains(name)) {
                continue;
            }
            remove(name);
            gone.add(name);
        }
        return gone;
    }
}
