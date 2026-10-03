package forge.neo.match;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.gamemodes.match.input.InputBlock;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * <b>"No has puesto a nadie a bloquear: ¿seguro?"</b>
 *
 * <p>Pedido en Discord el 03-10-2026: <i>"with decks that generate a lot of
 * life-gain, +1/+1 counter, token, or other triggers, I may have 10–15
 * consecutive OK prompts to work through. After clicking through that many
 * prompts, it becomes very easy to continue clicking automatically and
 * accidentally advance past the point where blockers can be declared"</i>.
 * Es el principio 6b otra vez: el mismo boton, en el mismo sitio, que de
 * repente significa algo que no se deshace. Lo de la fase principal lo hace
 * {@code NeoMatchUI.confirmLeavingMain}; esto es lo mismo para los bloqueos.
 *
 * <p>Aqui vive SOLO la pregunta "¿hay que avisar?", leyendo el motor, sin
 * JavaFX: la usan las dos interfaces (el escritorio y Android, por el jar), y
 * cada una pinta su dialogo. Se avisa cuando las tres cosas son ciertas:
 *
 * <ul>
 *   <li>el OK es el de <b>declarar bloqueadores</b> ({@code InputBlock} activo);</li>
 *   <li>no has puesto <b>a nadie</b> a bloquear;</li>
 *   <li>y tienes alguna criatura que <b>podria</b> bloquear a algun atacante.
 *       Sin eso la pregunta solo seria un toque de mas.</li>
 * </ul>
 *
 * <p>Apagado de fabrica: es lo que pidio quien lo propuso, y quien juega
 * rapido no lo quiere. Ajustes → "Preguntar si no bloqueas con nada"
 * ({@link forge.neo.NeoSettings#CONFIRM_NO_BLOCK}).
 *
 * <p>Se llama desde el hilo de interfaz con el motor parado esperando la
 * respuesta a {@code InputBlock}, igual que los clicks que ponen bloqueadores:
 * nadie esta cambiando el combate mientras se mira. En una partida en red
 * como invitado el controlador no es el del motor y no se avisa (no hay
 * combate que leer en este lado).
 */
public final class BlockGuard {

    private BlockGuard() {
    }

    /** Si el ajuste esta puesto y este OK dejaria el combate sin un solo bloqueo pudiendo bloquear. */
    public static boolean shouldAsk(final IGameController controller) {
        if (!forge.neo.NeoSettings.confirmNoBlock()) {
            return false;
        }
        return wouldSkipBlocking(controller);
    }

    /** Lo mismo sin mirar el ajuste (para las pruebas). */
    public static boolean wouldSkipBlocking(final IGameController controller) {
        if (!(controller instanceof PlayerControllerHuman)) {
            return false;
        }
        final PlayerControllerHuman human = (PlayerControllerHuman) controller;
        try {
            if (!(human.getInputQueue().getInput() instanceof InputBlock)) {
                return false;
            }
            final Game game = human.getGame();
            final Combat combat = game == null ? null : game.getCombat();
            if (combat == null || combat.getAttackers().isEmpty()) {
                return false;
            }
            final Player defender = defender(human.getPlayer(), combat);
            if (defender == null) {
                return false;
            }
            for (final Card blocker : combat.getAllBlockers()) {
                if (blocker.getController() == defender) {
                    return false; // ya hay alguno bloqueando: has decidido
                }
            }
            for (final Card creature : defender.getCreaturesInPlay()) {
                if (!CombatUtil.canBlock(creature, combat)) {
                    continue;
                }
                for (final Card attacker : combat.getAttackers()) {
                    if (CombatUtil.canBlock(attacker, creature, combat)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (final RuntimeException e) {
            // Una pregunta de mas o de menos no puede costar la partida.
            return false;
        }
    }

    /**
     * A quien le estamos declarando bloqueadores: a nosotros, o a quien nos
     * haya cedido la declaracion (Odric, Lunarch Marshal y compania). Como
     * {@code PhaseHandler.declareBlockersTurnBasedAction}.
     */
    private static Player defender(final Player me, final Combat combat) {
        Player other = null;
        for (final Player p : combat.getDefendingPlayers()) {
            final Player declares = p.getDeclaresBlockers() != null ? p.getDeclaresBlockers() : p;
            if (declares != me || !combat.isPlayerAttacked(p)) {
                continue;
            }
            if (p == me) {
                return p;
            }
            if (other == null) {
                other = p;
            }
        }
        return other;
    }
}
