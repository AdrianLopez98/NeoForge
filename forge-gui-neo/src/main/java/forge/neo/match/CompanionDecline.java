package forge.neo.match;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.ability.effects.DetachedCardEffect;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.PlayerZone;
import forge.game.zone.ZoneType;

/**
 * <b>Decir que no al compañero</b> (Discord, 14-10-2026: <i>"If I start a
 * constructed match where my deck has a companion in the sideboard, and I do
 * not select that companion, choosing to start the game will immediately end
 * the game"</i>).
 *
 * <p>El fallo es del motor: {@code Player.assignCompanion} pregunta el
 * compañero como OPCIONAL, pero con la respuesta "ninguno" hace
 * {@code moveTo(Command, null)} y revienta, y la partida muere antes de
 * empezar. Por la regla de oro no se toca: se envuelve.
 *
 * <p>Si el jugador dice que no, al motor se le contesta con el primero
 * ({@link #stand}) — es lo unico que no le hace reventar — y antes de la
 * primera decision de la partida ({@link #undo}: lo llaman
 * {@code complainCardsCantPlayWell} y {@code mulliganKeepHand}, que llegan
 * despues de {@code assignCompanion} y antes de jugar nada) se deshace: la
 * carta vuelve al banquillo y se quita el efecto de "paga 3 y ponla en tu
 * mano". O sea, como si no hubiera elegido ninguno. Si algun dia lo arreglan
 * rio arriba, no llegara un "ninguno" que tapar y esto no salta.
 */
public final class CompanionDecline {

    private CompanionDecline() {
    }

    /** La carta que se le dio al motor en lugar de "ninguno", o null. */
    static Card stand(final Card first) {
        System.out.println("[neo] companero rechazado: se da " + first.getName()
                + " al motor y se devuelve al banquillo antes de jugar");
        return first;
    }

    /** Devuelve al banquillo el companero que no se queria, y quita su efecto. */
    static void undo(final Player player, final Card companion) {
        if (companion != null) {
            undo(player, companion.getName());
        }
    }

    /**
     * Lo mismo, por nombre. Android lo llama desde su interfaz (no tiene
     * nuestro controlador humano), con el motor esperando una respuesta.
     */
    public static void undo(final Player player, final String companionName) {
        if (player == null || companionName == null) {
            return;
        }
        try {
            final Game game = player.getGame();
            final PlayerZone command = player.getZone(ZoneType.Command);
            Card inCommand = null;
            DetachedCardEffect effect = null;
            for (final Card c : command.getCards()) {
                if (c instanceof DetachedCardEffect eff) {
                    final Card source = eff.getEffectSource();
                    if (source != null && source.getName().equals(companionName)
                            && eff.getName().endsWith("Companion Effect")) {
                        effect = eff;
                    }
                } else if (c.getName().equals(companionName) && c.getOwner() == player) {
                    inCommand = c;
                }
            }
            if (effect != null) {
                command.remove(effect);
            }
            if (inCommand != null) {
                game.getAction().moveTo(ZoneType.Sideboard, inCommand, null, AbilityKey.newMap());
            }
            player.updateZoneForView(command);
            System.out.println("[neo] companero devuelto al banquillo: " + companionName
                    + (inCommand == null ? " (no estaba en el mando)" : ""));
        } catch (final RuntimeException e) {
            // Peor que dejarlo puesto seria reventar la partida que estamos salvando.
            System.err.println("[neo] no se ha podido devolver el companero: " + e);
        }
    }
}
