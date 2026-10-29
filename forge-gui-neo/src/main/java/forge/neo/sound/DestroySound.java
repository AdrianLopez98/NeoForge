package forge.neo.sound;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * <b>Destruir suena segun lo que se destruye</b> (Discord, 26-10-2026: <i>"my
 * Sol Ring screams when it is destroyed"</i>).
 *
 * <p>El aviso de Forge ({@code GameEventCardDestroyed}) va vacio: no dice que
 * carta. Hasta Forge #12500 (29-10-2026) la carta llevaba una marca
 * ({@code wasDestroyed}) mientras sonaba; ya no. Lo que si se sabe es que
 * {@code GameAction.destroy} la mueve al cementerio justo despues del aviso, y
 * que cada cementerio guarda EN ORDEN lo que le ha llegado desde la mesa este
 * turno ({@code Zone.getCardsAddedThisTurn}). Asi que al sonar el aviso se
 * apunta cuanto habia ({@link #snapshot}) y un momento despues la primera
 * carta nueva es la destruida ({@link #poolSince}). Lo de esperar lo hace
 * {@link NeoSounds#play}. Si no llega al cementerio (Rest in Peace...), el de
 * siempre.
 *
 * <p>De lo concreto a lo general: artefacto criatura, criatura o planeswalker
 * (el grito de siempre: devuelve null), artefacto, tierra, encantamiento y lo
 * demas.
 */
public final class DestroySound {

    private DestroySound() {
    }

    /** Cuantas cartas habia llegado ya a cada cementerio desde la mesa (al sonar el aviso). */
    public static Map<Player, Integer> snapshot() {
        final Map<Player, Integer> out = new HashMap<>();
        final Game g = forge.neo.card.AffectedBy.current();
        if (g == null) {
            return out;
        }
        try {
            for (final Player p : g.getPlayers()) {
                out.put(p, p.getZone(ZoneType.Graveyard).getCardsAddedThisTurn(ZoneType.Battlefield).size());
            }
        } catch (final RuntimeException e) {
            out.clear();
        }
        return out;
    }

    /** El monton de la primera carta llegada desde {@code before}, o null para el de siempre. */
    public static String poolSince(final Map<Player, Integer> before) {
        if (before == null || before.isEmpty()) {
            return null;
        }
        try {
            for (final Map.Entry<Player, Integer> e : before.entrySet()) {
                final List<Card> added = e.getKey().getZone(ZoneType.Graveyard)
                        .getCardsAddedThisTurn(ZoneType.Battlefield);
                if (added.size() > e.getValue()) {
                    return of(added.get(e.getValue()));
                }
            }
        } catch (final RuntimeException e) {
            // Mejor el sonido de siempre que ninguno.
        }
        return null;
    }

    /** El monton para esa carta, o null para el de siempre (criaturas y planeswalkers). */
    public static String of(final Card c) {
        if (c.isArtifact() && c.isCreature()) {
            return "destroy_artifact_creature";
        }
        if (c.isCreature() || c.isPlaneswalker()) {
            return null;
        }
        if (c.isArtifact()) {
            return "destroy_artifact";
        }
        if (c.isLand()) {
            return "destroy_land";
        }
        if (c.isEnchantment()) {
            return "destroy_enchantment";
        }
        return "destroy_other";
    }
}
