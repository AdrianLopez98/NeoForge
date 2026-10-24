package forge.neo.net;

import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.atomic.AtomicInteger;

import forge.game.player.PlayerView;
import forge.gamemodes.match.input.InputQueue;
import forge.gui.FThreads;
import forge.gui.interfaces.IGuiGame;
import forge.player.PlayerControllerHuman;
import forge.util.Localizer;
import forge.util.ThreadUtil;

/**
 * "Esperando acciones...", para el invitado en red, cuando ya ha contestado.
 *
 * <p><b>El fallo</b> (Discord, 24-10-2026, Geier Reach Sanitarium en red: "Player
 * 2's prompt stays in affect asking for a discard ... until Player 1 continue
 * with their turn"). Cuando una peticion se acaba y no viene otra, Forge pone
 * {@code InputLockUI}, que a los 500 ms deberia apagar los botones y escribir
 * "Esperando acciones". Pero su {@code isActive()} compara
 * {@code inputQueue.getInput() == this}, y con la cola vacia
 * {@code getInput()} es {@code null}: no lo hace NUNCA. En local no se nota (la
 * siguiente pregunta llega enseguida); al invitado se le quedaba la pregunta
 * vieja en pantalla —descartar otra vez, con OK— hasta que el motor le volvia a
 * preguntar algo, y cada clic le decia "objetivo no valido".
 *
 * <p><b>El arreglo</b>, desde fuera y sin tocar Forge: la cola es un
 * {@code Observable} publico. Se la observa y, si medio segundo despues de
 * cambiar sigue vacia, se hace lo que queria hacer {@code InputLockUI}. Si en
 * ese medio segundo llega otra peticion, la cola vuelve a cambiar y este aviso
 * se descarta (el numero de vuelta). Si Forge lo arregla rio arriba, esto
 * repite su mismo mensaje y ya.
 *
 * <p>Solo para invitados ({@link SafeRemoteGuiGame}): en local nuestra mesa se
 * apanya, y no se toca lo que funciona.
 */
final class WaitingPrompt implements Observer {

    private final PlayerControllerHuman controller;
    private final IGuiGame gui;
    private final PlayerView player;
    private final AtomicInteger turn = new AtomicInteger();

    private WaitingPrompt(final PlayerControllerHuman controller, final IGuiGame gui, final PlayerView player) {
        this.controller = controller;
        this.gui = gui;
        this.player = player;
    }

    static void watch(final PlayerControllerHuman controller, final IGuiGame gui, final PlayerView player) {
        final InputQueue queue = controller.getInputQueue();
        if (queue != null) {
            queue.addObserver(new WaitingPrompt(controller, gui, player));
        }
    }

    @Override
    public void update(final Observable o, final Object arg) {
        final int mine = turn.incrementAndGet();
        if (!idle()) {
            return;
        }
        ThreadUtil.delay(500, () -> {
            if (mine != turn.get() || !idle()) {
                return;
            }
            FThreads.invokeInEdtLater(() -> {
                if (mine != turn.get() || !idle()) {
                    return;
                }
                try {
                    if (controller.mayAutoPass()) {
                        gui.updateAutoPassPrompt();
                    } else {
                        gui.updateButtons(player, "", "", false, false, false);
                        gui.showPromptMessage(player, Localizer.getInstance().getMessage("lblWaitingforActions"));
                    }
                } catch (final RuntimeException e) {
                    // Un invitado que se ha ido: no es nuestro problema aqui.
                }
            });
        });
    }

    /** Sin peticion pendiente, y con la partida aun en marcha. */
    private boolean idle() {
        try {
            return controller.getInputQueue().getInput() == null
                    && !controller.getPlayer().getGame().isGameOver();
        } catch (final RuntimeException e) {
            return false;
        }
    }
}
