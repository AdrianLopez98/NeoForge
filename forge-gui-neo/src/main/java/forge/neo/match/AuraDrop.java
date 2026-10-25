package forge.neo.match;

import java.util.List;

import forge.game.card.CardView;
import forge.game.zone.ZoneType;

/**
 * <b>Soltar un aura de la mano sobre una criatura</b> (Discord, 24-10-2026:
 * <i>"Dragging auras from your hand onto a creature prompts the mana cost and
 * then attaches to that chosen creature"</i>).
 *
 * <p>Sin una sola regla nuestra: el aura se lanza con el mismo {@code selectCard}
 * de siempre, y lo unico que se anyade es contestar la eleccion de objetivo que
 * pide el motor con la criatura sobre la que se solto — si esa criatura esta
 * entre las que el motor deja elegir. Lo decidio Ana, y por eso es tan corto:
 * solo auras (los equipos llevan su pago de equipar), solo sobre una criatura,
 * y si sale el menu de habilidades (un aura con otra forma de jugarla) se
 * olvida y elige el jugador, "mejor eso que cagarla". Soltada en otro sitio, o
 * sobre una criatura que no vale de objetivo, todo es como antes.
 *
 * <p>Logica pura sobre las vistas, para que Android la use igual.
 */
public final class AuraDrop {

    private AuraDrop() {
    }

    /**
     * La criatura a la que apuntar si se ha soltado un aura sobre ella, o
     * {@code null} si este arrastre no es eso.
     */
    public static CardView creatureTarget(final CardView source, final Object target) {
        if (source == null || source.getCurrentState() == null
                || !source.getCurrentState().getType().hasSubtype("Aura")) {
            return null;
        }
        if (!(target instanceof CardView card) || card.getId() == source.getId()
                || card.getZone() != ZoneType.Battlefield || card.getCurrentState() == null
                || !card.getCurrentState().isCreature()) {
            return null;
        }
        return card;
    }

    /** Si la criatura esta entre lo que el motor deja elegir ahora (por id). */
    public static boolean contains(final List<CardView> selectable, final CardView card) {
        if (selectable == null || card == null) {
            return false;
        }
        for (final CardView c : selectable) {
            if (c != null && c.getId() == card.getId()) {
                return true;
            }
        }
        return false;
    }
}
