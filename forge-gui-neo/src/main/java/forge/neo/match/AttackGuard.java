package forge.neo.match;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.player.Player;
import forge.gamemodes.match.input.InputAttack;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * <b>"No has declarado ningun atacante: ¿seguro?"</b>, el gemelo de
 * {@link BlockGuard} para el ataque.
 *
 * <p>Pedido en Discord el 04-11-2026: <i>"Can you do the same thing for
 * attacking as you did for blocking? ... I get click happy sometimes and miss
 * an attack opportunity"</i>. Principio 6b: el OK de declarar atacantes es el
 * mismo boton que el de pasar, y un ataque que no se declara no se deshace.
 *
 * <p>Se avisa cuando el OK es el de <b>declarar atacantes</b>
 * ({@code InputAttack} activo), no hay <b>ningun</b> atacante declarado y tienes
 * alguna criatura que <b>podria</b> atacar. Apagado de fabrica, como el de los
 * bloqueos: Ajustes → "Preguntar si no atacas con nada"
 * ({@link forge.neo.NeoSettings#CONFIRM_NO_ATTACK}). Sin JavaFX: lo usan las
 * dos interfaces (Android, por el jar).
 *
 * <p>Se llama desde el hilo de interfaz con el motor parado esperando la
 * respuesta a {@code InputAttack}, igual que los clicks que declaran atacantes.
 */
public final class AttackGuard {

    private AttackGuard() {
    }

    /** Si el ajuste esta puesto y este OK dejaria pasar el ataque sin nadie pudiendo atacar. */
    public static boolean shouldAsk(final IGameController controller) {
        if (!forge.neo.NeoSettings.confirmNoAttack()) {
            return false;
        }
        return wouldSkipAttack(controller);
    }

    /** Lo mismo sin mirar el ajuste (para las pruebas). */
    public static boolean wouldSkipAttack(final IGameController controller) {
        if (!(controller instanceof PlayerControllerHuman)) {
            return false;
        }
        final PlayerControllerHuman human = (PlayerControllerHuman) controller;
        try {
            if (!(human.getInputQueue().getInput() instanceof InputAttack)) {
                return false;
            }
            final Game game = human.getGame();
            final Combat combat = game == null ? null : game.getCombat();
            if (combat == null || !combat.getAttackers().isEmpty()) {
                return false; // ya hay alguno atacando: has decidido
            }
            final Player attacker = combat.getAttackingPlayer() != null
                    ? combat.getAttackingPlayer() : human.getPlayer();
            for (final Card creature : attacker.getCreaturesInPlay()) {
                if (CombatUtil.canAttack(creature)) {
                    return true;
                }
            }
            return false;
        } catch (final RuntimeException e) {
            // Una pregunta de mas o de menos no puede costar la partida.
            return false;
        }
    }
}
