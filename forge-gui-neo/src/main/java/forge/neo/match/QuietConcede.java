package forge.neo.match;

import forge.game.Game;
import forge.game.GameView;
import forge.gamemodes.match.AbstractGuiGame;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * <b>Rendirse en una mesa de tres o mas sin tocar el motor desde fuera.</b>
 *
 * <p>Discord (Ana, 12-10-2026): "Salir al menu" en una partida de Commander
 * con Archenemy y Planechase dejo la pantalla en "Cerrando la partida..." para
 * siempre. {@code PlayerControllerHuman.concede()} hace dos cosas, y la segunda
 * es la que rompe:
 *
 * <ol>
 *   <li>{@code Player.concede()}: apunta tu resultado (rendido). Inofensivo.</li>
 *   <li>{@code GameAction.checkGameOverCondition()} <b>en el hilo que llama</b>,
 *       que es el nuestro (el de interfaz en el escritorio, el de entrada en
 *       Android). Con rivales vivos eso ejecuta TODO lo de "un jugador deja la
 *       partida" ({@code Game.onPlayerLost}): disparos, tus esquemas, y si
 *       controlabas el mazo planar, el siguiente <b>se desplaza</b> a otro plano
 *       — mientras el hilo de la partida sigue a lo suyo. Visto en neo.log:
 *       {@code ConcurrentModificationException} en
 *       {@code TriggerHandler.runWaitingTrigger}, y reproducido en una prueba
 *       con un {@code StackOverflowError} en {@code TargetSelection}: el motor
 *       pidiendole objetivos al jugador que ya se habia ido.</li>
 * </ol>
 *
 * <p>Aqui solo se hace lo primero, y quien llama remata con
 * {@code Game.setGameOver(AllHumansLost)} desde un hilo de partida, que ya hacia:
 * ese no ejecuta nada de reglas, apunta el resultado (los demas ganan,
 * {@code Player.onGameOver}; tu ya tienes "rendido") y suelta las esperas del
 * motor ({@code FControlGameEventHandler} -> {@code onGameOver}). La derrota se
 * anota igual que antes.
 *
 * <p>Solo en partidas LOCALES de mas de dos jugadores, fuera del mulligan. En un
 * duelo, conceder ya termina la partida en el acto y es el camino probado de
 * siempre (Aventura, Quest, Ascenso); en red la partida vive en el anfitrion.
 * Sin JavaFX: lo usan las dos interfaces.
 */
public final class QuietConcede {

    private QuietConcede() {
    }

    /**
     * Rinde a los jugadores locales sin pasar por el motor.
     *
     * @return true si se ha hecho, y entonces quien llama tiene que terminar la
     *         partida ({@code setGameOver}); false si hay que conceder como
     *         siempre ({@code AbstractGuiGame.concede()})
     */
    public static boolean tryConcede(final AbstractGuiGame gui) {
        if (gui == null || Boolean.getBoolean("neo.quietConcede.off")) {
            return false;
        }
        try {
            final GameView view = gui.getGameView();
            if (view == null || view.isGameOver() || view.isMulligan()) {
                return false;
            }
            final Game game = view.getGame();
            if (game == null || game.getRegisteredPlayers().size() <= 2) {
                return false;
            }
            boolean conceded = false;
            for (final IGameController c : gui.getOriginalGameControllers()) {
                if (!(c instanceof PlayerControllerHuman human)) {
                    // Un controlador remoto: esto no es una partida local.
                    return false;
                }
                if (human.getPlayer() != null && human.getPlayer().getOutcome() == null) {
                    human.getPlayer().concede();
                    conceded = true;
                }
            }
            return conceded;
        } catch (final RuntimeException e) {
            return false;
        }
    }
}
