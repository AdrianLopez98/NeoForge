package forge.neo.match;

import java.util.List;

import forge.game.card.CardView.CardStateView;
import forge.game.card.CardView;
import forge.game.zone.ZoneType;

/**
 * El orden que el motor pide antes de ADJUNTAR varias Auras o Equipos.
 *
 * <p>Ana, 19-10-2026, con Ardenn, Intrepid Archaeologist: el modal de «Choose
 * order of cards to put onto the battlefield» le obligaba a marcar TODOS sus
 * equipos, incluso los que no queria, y luego le preguntaba uno a uno si los
 * adjuntaba. Es el motor y es igual en la interfaz de Forge: el efecto de
 * adjuntar (`AttachEffect`) ordena primero TODOS los objetos con el orden
 * generico de "cartas que van al campo de batalla" y despues, si el efecto es
 * opcional, confirma uno a uno. El modal no elige cuales: solo el orden, que
 * para adjuntar da igual. Lo unico que se cambia es decirlo.
 *
 * <p>Se reconoce sin el motor: un orden "al campo de batalla" cuyas cartas YA
 * estan todas en el campo de batalla y son todas Auras o Equipos no esta
 * metiendo nada en la mesa: las esta adjuntando.
 */
public final class AttachOrder {

    private AttachOrder() {
    }

    public static boolean applies(final List<?> items) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        try {
            for (final Object o : items) {
                if (!(o instanceof CardView c) || c.getZone() != ZoneType.Battlefield) {
                    return false;
                }
                final CardStateView st = c.getCurrentState();
                if (st == null || st.getType() == null
                        || !(st.getType().hasSubtype("Aura") || st.getType().hasSubtype("Equipment"))) {
                    return false;
                }
            }
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }
}
