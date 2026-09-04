package forge.neo.ascent;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
        // add() reescribe si ya existe, que es justo lo que hace falta: el
        // mazo de la run cambia en cada nodo.
        storage().add(deck);
    }

    /** El mazo de esa run, o {@code null} si no esta. */
    public static Deck load(final String name) {
        return name == null ? null : storage().get(name);
    }

    /** El mazo de esa run. */
    public static Deck load(final AscentRun run) {
        return run == null ? null : load(run.getDeckName());
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
        out.sort(Comparator
                .comparingInt((PaperCard c) -> c.getRules() == null ? 0
                        : c.getRules().getManaCost().getCMC())
                .reversed()
                .thenComparing(PaperCard::getName));
        return out;
    }

    /** Borra el mazo de una run terminada. */
    public static void remove(final String name) {
        if (name != null && storage().contains(name)) {
            storage().delete(name);
        }
    }
}
