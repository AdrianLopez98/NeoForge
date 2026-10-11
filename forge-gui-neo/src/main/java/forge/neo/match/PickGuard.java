package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.gamemodes.match.AbstractGuiGame;
import forge.gamemodes.match.input.InputSelectEntitiesFromList;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * <b>"Vas a descartar estas dos: ¿seguro?"</b>
 *
 * <p>Discord, 11-10-2026 (Minty): <i>"Can we get some sort of confirmation
 * button toggle for discarding cards? Many times now I've been trying to look
 * at cards, but as soon as you select the number needed (such as 2) it
 * immediately discards them. I need there to be a pause."</i> Es el motor:
 * cuando una eleccion de "elige N cartas" llega a N, su
 * {@code InputSelectManyBase.refresh} pulsa OK el solo. Eso no se toca; lo que
 * se puede es <b>no mandar todavia</b> el toque que la completaria y preguntar
 * antes.
 *
 * <p>Aqui vive SOLO la pregunta "¿este toque cerraria la eleccion?", sin
 * JavaFX: la usan las dos interfaces (Android por el jar), igual que
 * {@link BlockGuard}. Cierra la eleccion si:
 *
 * <ul>
 *   <li>lo que espera el motor es un {@code InputSelectEntitiesFromList}
 *       (descartar, sacrificar, exiliar, devolver... "elige N"). Ni apuntar con
 *       un hechizo ({@code InputSelectTargets}) ni pagar (convocar,
 *       improvisar): preguntar en cada objetivo seria un toque de mas en cada
 *       hechizo;</li>
 *   <li>la carta se puede elegir y aun no esta elegida (quitarla no cierra
 *       nada);</li>
 *   <li>y con ella se llega al maximo.</li>
 * </ul>
 *
 * <p>Apagado de fabrica, como el del bloqueo: quien juega rapido no lo quiere.
 * Ajustes → "Preguntar antes de cerrar una eleccion de cartas"
 * ({@link forge.neo.NeoSettings#CONFIRM_LAST_PICK}).
 */
public final class PickGuard {

    private PickGuard() {
    }

    /** Si el ajuste esta puesto y este toque cerraria la eleccion. */
    public static boolean shouldAsk(final IGameController controller, final AbstractGuiGame gui,
                                    final CardView card) {
        return forge.neo.NeoSettings.confirmLastPick() && wouldFinish(controller, gui, card);
    }

    /** Lo mismo sin mirar el ajuste (para las pruebas). */
    public static boolean wouldFinish(final IGameController controller, final AbstractGuiGame gui,
                                      final CardView card) {
        if (card == null || gui == null || !(controller instanceof PlayerControllerHuman)) {
            return false;
        }
        try {
            if (!(((PlayerControllerHuman) controller).getInputQueue().getInput()
                    instanceof InputSelectEntitiesFromList)) {
                return false;
            }
        } catch (final RuntimeException e) {
            return false;
        }
        final int max = gui.getSelectionMax();
        return gui.isSelecting() && max > 0 && gui.isSelectable(card) && !gui.isHighlighted(card)
                && gui.countPickedSelectables() + 1 >= max;
    }

    /**
     * Las que quedarian elegidas: las que ya lo estan y {@code last}. Para
     * decirlas en la pregunta. Se buscan en las zonas de todos los jugadores:
     * el motor no publica la lista de lo elegible.
     */
    public static List<CardView> picked(final AbstractGuiGame gui, final CardView last) {
        final List<CardView> out = new ArrayList<>();
        final GameView gv = gui == null ? null : gui.getGameView();
        if (gv != null && gv.getPlayers() != null) {
            for (final PlayerView p : gv.getPlayers()) {
                addPicked(gui, p.getHand(), out);
                addPicked(gui, p.getBattlefield(), out);
                addPicked(gui, p.getGraveyard(), out);
                addPicked(gui, p.getExile(), out);
                addPicked(gui, p.getLibrary(), out);
                addPicked(gui, p.getCommand(), out);
            }
        }
        if (last != null && !out.contains(last)) {
            out.add(last);
        }
        return out;
    }

    private static void addPicked(final AbstractGuiGame gui, final Iterable<CardView> zone,
                                  final List<CardView> out) {
        if (zone == null) {
            return;
        }
        for (final CardView c : zone) {
            if (c != null && gui.isSelectable(c) && gui.isHighlighted(c) && !out.contains(c)) {
                out.add(c);
            }
        }
    }
}
