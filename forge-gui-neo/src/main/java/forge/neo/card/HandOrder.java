package forge.neo.card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import forge.game.card.CardView;

/**
 * La mano ordenada por coste, luego color y luego nombre.
 *
 * <p>Es el orden de "Order hand by CMC and color" de Forge ({@code CHand}),
 * pedido en itch.io el 28-09-2026: <i>"an option for hand ordering like the
 * vanilla forge option"</i>. Se ordena en la interfaz, como alli, porque el
 * invitado de una partida en red solo tiene {@code CardView} y porque el ajuste
 * tiene que notarse en cuanto se cambia.
 *
 * <p>Clase pura, sin JavaFX: la usan la mesa del escritorio
 * ({@code TableScreen.setHand}) y la de Android por el jar, con el mismo
 * ajuste ({@code NeoSettings.ORDER_HAND}).
 */
public final class HandOrder {

    private HandOrder() {
    }

    private static final Comparator<CardView> ORDER = Comparator
            .comparingInt((CardView cv) -> cv.getCurrentState() == null ? 0
                    : cv.getCurrentState().getManaCost().getCMC())
            .thenComparingDouble(cv -> cv.getCurrentState() == null ? 0
                    : cv.getCurrentState().getColors().getOrderWeight())
            .thenComparing(cv -> cv.getCurrentState() == null ? ""
                    : String.valueOf(cv.getCurrentState().getName()));

    /** Una copia ordenada; la lista que llega no se toca. */
    public static List<CardView> sorted(final Iterable<CardView> cards) {
        final List<CardView> out = new ArrayList<>();
        if (cards != null) {
            for (final CardView cv : cards) {
                out.add(cv);
            }
        }
        out.sort(ORDER);
        return out;
    }
}
