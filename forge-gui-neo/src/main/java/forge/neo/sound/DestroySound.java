package forge.neo.sound;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * <b>Destruir suena segun lo que se destruye</b> (Discord, 26-10-2026: <i>"my
 * Sol Ring screams when it is destroyed"</i>).
 *
 * <p>El aviso de Forge ({@code GameEventCardDestroyed}) va vacio: no dice que
 * carta. Pero {@code GameAction.destroy} hace, en este orden y por cada carta,
 * {@code updateWasDestroyed(true)} -> el aviso (que es cuando suena) -> los
 * disparos -> moverla al cementerio. O sea que mientras suena, la carta sigue
 * en la mesa y es la que lleva la marca. Se lee en el hilo de la partida: el
 * bus de eventos es sincrono y el sonido se pide dentro del aviso.
 *
 * <p>De lo concreto a lo general: artefacto criatura, criatura o planeswalker
 * (el grito de siempre: devuelve null), artefacto, tierra, encantamiento y lo
 * demas.
 */
public final class DestroySound {

    private DestroySound() {
    }

    /** El monton de la carta que se esta destruyendo ahora, o null para el de siempre. */
    static String pool() {
        final Game g = forge.neo.card.AffectedBy.current();
        if (g == null) {
            return null;
        }
        try {
            for (final Player p : g.getPlayers()) {
                for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
                    if (c.getView().wasDestroyed()) {
                        return of(c);
                    }
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
