package forge.neo.match;

import forge.game.Game;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.gamemodes.match.input.InputPassPriority;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * <b>"¿Ir al combate?" / "¿Terminar tu turno?"</b>, leyendo el motor y sin
 * JavaFX, para Android (Discord, 26-10-2026: <i>"having the 'Ask before leaving
 * your main phase' setting from the PC version would be huge on mobile"</i>).
 *
 * <p>El escritorio lo hace en {@code NeoMatchUI.confirmLeavingMain} y
 * {@code confirmEndingTurn} mirando el texto del prompt; aqui se mira el motor
 * (que es tu turno, la fase, la pila vacia y que lo que espera es pasar la
 * prioridad), como {@link BlockGuard}. Misma regla y mismo ajuste
 * ({@link forge.neo.NeoSettings#CONFIRM_PHASE}): {@code 1} pregunta al ir al
 * combate, {@code 2} tambien al dejar la segunda fase principal.
 *
 * <p>Se llama desde el hilo de interfaz con el motor parado esperando la
 * prioridad. Como invitado en red no hay motor en este lado: no se pregunta.
 */
public final class PhaseGuard {

    private PhaseGuard() {
    }

    /** Lo que se pregunta. */
    public enum Ask { NONE, TO_COMBAT, END_MAIN }

    /** Si el OK de pasar la prioridad te sacaria de tu fase principal, con ese modo. */
    public static Ask leavingMain(final IGameController controller, final int mode) {
        final PhaseHandler ph = myPriority(controller, mode);
        if (ph == null) {
            return Ask.NONE;
        }
        // Si ya has jugado algo en esta fase principal, pasar es a proposito
        // (Discord, 28-10-2026). Ver MainPhaseActivity.
        if (MainPhaseActivity.playedSomething(((PlayerControllerHuman) controller).getPlayer())) {
            return Ask.NONE;
        }
        if (ph.getPhase() == PhaseType.MAIN1) {
            return Ask.TO_COMBAT;
        }
        return ph.getPhase() == PhaseType.MAIN2 && mode >= 2 ? Ask.END_MAIN : Ask.NONE;
    }

    /**
     * Si "Fin de turno" se saltaria parte de TU turno: antes de la segunda fase
     * principal con el modo 1 o mas, y en ella solo con el 2. Como
     * {@code NeoMatchUI.confirmEndingTurn}.
     */
    public static boolean endingTurn(final IGameController controller, final int mode) {
        final PhaseHandler ph = myPriority(controller, mode);
        if (ph == null || !ph.getPhase().isBefore(PhaseType.END_OF_TURN)) {
            return false;
        }
        return ph.getPhase() != PhaseType.MAIN2 || mode >= 2;
    }

    /** El siguiente en jugar, o null si no se sabe. */
    public static Player nextTurn(final IGameController controller) {
        try {
            final PlayerControllerHuman human = (PlayerControllerHuman) controller;
            return human.getGame().getPhaseHandler().getNextTurn();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Si el que juega soy yo y el motor me espera para pasar la prioridad con la pila vacia. */
    private static PhaseHandler myPriority(final IGameController controller, final int mode) {
        if (mode <= 0 || !(controller instanceof PlayerControllerHuman human)) {
            return null;
        }
        try {
            if (!(human.getInputQueue().getInput() instanceof InputPassPriority)) {
                return null;
            }
            final Game game = human.getGame();
            if (game == null || !game.getStack().isEmpty()) {
                return null;
            }
            final PhaseHandler ph = game.getPhaseHandler();
            if (ph.getPhase() == null || !ph.isPlayerTurn(human.getPlayer())) {
                return null;
            }
            return ph;
        } catch (final RuntimeException e) {
            // Una pregunta de mas o de menos no puede costar la partida.
            return null;
        }
    }
}
