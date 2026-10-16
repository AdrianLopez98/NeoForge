package forge.neo.quest;

import java.util.Locale;
import java.util.Map;

import forge.item.PaperCard;

/**
 * <b>Lo que te llevas de un duelo, mirado desde tu coleccion</b> (Discord,
 * 16-10-2026: <i>"check if you already own these card when you open the prize
 * ... (it would be good to have the xN there if already owned) ... being able
 * to sell the cards right away in this same page"</i>).
 *
 * <p>Cuantas tienes de cada carta (por NOMBRE, como "Tienes 3" en la tienda:
 * es lo que importa para jugarla) y venderla ahi mismo, con las mismas reglas
 * que la tienda ({@link NeoQuestSell#sellOne}: cobra, la quita y la devuelve al
 * mostrador, que es la unica marcha atras). Lo que tus mazos usan NO se vende
 * desde aqui ({@link NeoQuestSell#usedByDecks}, la misma guarda que la venta
 * por lotes): la carta recien ganada puede ser justo la copia que le faltaba a
 * un mazo.
 *
 * <p>Sin JavaFX: la usan el botin del escritorio ({@code QuestRewardScreen}) y
 * el de Android, por el jar.
 */
public final class NeoQuestHaul {

    /** Lo que tus mazos guardan de cada impresion; se mira una vez, al abrir el botin. */
    private final Map<PaperCard, Integer> keep;
    private int sold;
    private long earned;

    public NeoQuestHaul() {
        keep = NeoQuestSell.usedByDecks();
    }

    /** Cuantas tienes de esta carta, de cualquier impresion (ya con las de este botin). */
    public int ownedByName(final PaperCard card) {
        if (card == null || !NeoQuest.isActive() || NeoQuest.collection() == null) {
            return 0;
        }
        final String name = card.getName().toLowerCase(Locale.ROOT);
        int n = 0;
        for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            if (e.getKey() != null && e.getKey().getName().toLowerCase(Locale.ROOT).equals(name)) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** Las copias de ESTA impresion que no le hacen falta a ningun mazo. */
    public int spare(final PaperCard card) {
        if (card == null || !NeoQuest.isActive()) {
            return 0;
        }
        return Math.max(0, NeoQuestSell.owned(card) - keep.getOrDefault(card, 0));
    }

    /** Si se puede vender una desde el botin. */
    public boolean canSell(final PaperCard card) {
        return spare(card) > 0;
    }

    /** Si no se vende porque la usa un mazo (para decirlo, y no solo apagar el boton). */
    public boolean keptForDecks(final PaperCard card) {
        return card != null && NeoQuestSell.owned(card) > 0 && spare(card) == 0;
    }

    public int price(final PaperCard card) {
        return NeoQuestSell.priceOf(card);
    }

    /** Vende UNA copia. @return lo cobrado, 0 si no se podia */
    public int sell(final PaperCard card) {
        if (!canSell(card)) {
            return 0;
        }
        final int got = NeoQuestSell.sellOne(card, 1);
        if (got > 0) {
            sold++;
            earned += got;
        }
        return got;
    }

    /** Cuantas has vendido desde este botin. */
    public int sold() {
        return sold;
    }

    /** Lo cobrado desde este botin. */
    public long earned() {
        return earned;
    }

    public long credits() {
        return NeoQuest.credits();
    }
}
