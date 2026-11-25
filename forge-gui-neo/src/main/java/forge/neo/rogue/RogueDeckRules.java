package forge.neo.rogue;

import forge.gamemodes.rogue.RogueIO;
import forge.gamemodes.rogue.RogueRun;
import forge.item.PaperCard;
import forge.localinstance.achievements.RogueCommanderAchievements;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Las reglas del mazo DURANTE una run, como las aplica Forge en
 * {@code CEditorRogue} (forge-gui-desktop) y, desde Forge #13023,
 * {@code RogueDeckEdits} y {@code RogueRun.basicLands} (forge-gui):
 * <ul>
 *   <li>tierras basicas: las que quieras, de la edicion del comandante y de
 *       sus colores (y Wastes si es incoloro o el mazo las lleva);</li>
 *   <li>quitar una carta que no es tierra basica gasta un credito de
 *       eliminacion, y sin creditos no se puede;</li>
 *   <li>deshacer devuelve la carta y el credito;</li>
 *   <li>cada anyadir, quitar y deshacer se guarda en el acto; al salir del editor, guardar y
 *       evaluar logros.</li>
 * </ul>
 * Sin JavaFX: la pantalla solo llama. Ver {@link RogueSources}.
 */
public final class RogueDeckRules {

    private record Undo(boolean add, PaperCard card, int credits) {
    }

    private final RogueRun run;
    private final Deque<Undo> undo = new ArrayDeque<>();

    public RogueDeckRules(final RogueRun run) {
        this.run = run;
    }

    /**
     * Las basicas que se pueden anyadir (el pool infinito). Desde Forge #13023
     * lo calcula el propio {@code RogueRun}, para las dos interfaces de Forge.
     */
    public List<PaperCard> basicLands() {
        if (run.getSelectedRogueDeck() == null || run.getSelectedRogueDeck().getStartDeck() == null
                || run.getSelectedRogueDeck().getStartDeck().getCommanders().isEmpty()) {
            return new ArrayList<>();
        }
        return run.basicLands();
    }

    /**
     * Anyadir una basica ({@code onAddItems}: solo basicas) y guardar, como
     * Forge desde #13023 ({@code RogueDeckEdits.added}).
     */
    public boolean add(final PaperCard card) {
        if (card == null || !card.getRules().getType().isBasicLand()) {
            return false;
        }
        run.getCurrentDeck().getMain().add(card);
        undo.push(new Undo(true, card, 0));
        RogueIO.saveRun(run);
        return true;
    }

    /** Si se puede quitar: una basica siempre; lo demas, con creditos. */
    public boolean canRemove(final PaperCard card) {
        if (card == null || !run.getCurrentDeck().getMain().contains(card)) {
            return false;
        }
        return card.getRules().getType().isBasicLand() || run.getRemovalCredits() > 0;
    }

    /** Quitar una ({@code onRemoveItems}): gasta un credito si no es basica, y guarda. */
    public boolean remove(final PaperCard card) {
        if (!canRemove(card)) {
            return false;
        }
        final int credits = card.getRules().getType().isBasicLand() ? 0 : 1;
        run.getCurrentDeck().getMain().remove(card);
        run.setRemovalCredits(run.getRemovalCredits() - credits);
        undo.push(new Undo(false, card, credits));
        RogueIO.saveRun(run);
        return true;
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    /** {@code undoLastRemoval}: deshace lo ultimo y guarda. */
    public void undo() {
        if (undo.isEmpty()) {
            return;
        }
        final Undo last = undo.pop();
        if (last.add()) {
            run.getCurrentDeck().getMain().remove(last.card());
        } else {
            run.getCurrentDeck().getMain().add(last.card());
            run.setRemovalCredits(run.getRemovalCredits() + last.credits());
        }
        RogueIO.saveRun(run);
    }

    /**
     * Al salir del editor ({@code canSwitchAway}): guardar y evaluar logros.
     * Se llama desde el hilo de la run, no el de JavaFX (los logros avisan).
     */
    public void leave() {
        RogueIO.saveRun(run);
        RogueCommanderAchievements.getInstance().evaluateRunAchievements(run);
    }
}
