package forge.neo.match;

import forge.game.Game;
import forge.game.GameView;
import forge.gamemodes.match.AbstractGuiGame;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * Rendir SOLO la partida en curso de un partido al mejor de N, y seguir con la
 * siguiente (Discord, 03-11-2026: <i>"in a BO5 match, if I get mana-screwed and
 * don't want to continue playing the current game, I still have to play it
 * out"</i>). Lo usan el PC ({@code NeoMatchUI.concedeGame}, en la pausa) y
 * Android por el jar.
 *
 * <p><b>No pasa por {@code AbstractGuiGame.concede()}.</b> Ese, si la partida no
 * se ve terminada justo despues de conceder (una mesa a tres o mas, o el aviso
 * que aun no ha llegado), manda {@code NextGameDecision.QUIT} y cierra el
 * PARTIDO entero. Aqui se hace a mano y desde un hilo de partida
 * ({@code GameAction.invoke}, como cuando se sale): los jugadores de esta
 * pantalla conceden, el motor mira si se acabo (a dos, si: gana el rival) y si
 * no, se cierra como {@code AllHumansLost} — lo que hace Forge con las IAs que
 * quedan. Luego llega el final de partida de siempre, con "Siguiente partida".
 */
public final class GameConcession {

    private GameConcession() {
    }

    /**
     * Si tiene sentido aqui: partido al mejor de N, en local, con la partida
     * empezada (turno 1: antes estan "jugar o robar" y el mulligan, preguntas
     * del motor a medio contestar; Forge tampoco deja conceder en el mulligan)
     * y sin terminar.
     */
    public static boolean possible(final AbstractGuiGame gui) {
        if (gui == null || gui.isNetGame() || gui.isReplay()) {
            return false;
        }
        try {
            final GameView gv = gui.getGameView();
            return gv != null && gv.getGame() != null && !gv.isGameOver() && !gv.isMulligan()
                    && gv.getTurn() > 0 && gv.getNumGamesInMatch() > 1;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** Rinde esta partida. No hace nada si {@link #possible} dice que no. */
    public static void concede(final AbstractGuiGame gui) {
        if (!possible(gui)) {
            return;
        }
        final Game game = gui.getGameView().getGame();
        game.getAction().invoke(() -> {
            try {
                for (final IGameController c : gui.getOriginalGameControllers()) {
                    if (c instanceof PlayerControllerHuman h && h.getPlayer() != null
                            && h.getPlayer().getOutcome() == null) {
                        h.getPlayer().concede();
                    }
                }
                game.getAction().checkGameOverCondition();
                if (!game.isGameOver()) {
                    game.setGameOver(forge.game.GameEndReason.AllHumansLost);
                }
            } catch (final RuntimeException e) {
                System.out.println("[neo] no se ha podido rendir la partida: " + e);
            }
        });
    }
}
