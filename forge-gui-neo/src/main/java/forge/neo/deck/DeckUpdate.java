package forge.neo.deck;

import java.util.List;
import java.util.Map;

import forge.deck.Deck;
import forge.item.PaperCard;

/**
 * <b>"Actualizar desde el enlace" sin abrir el editor</b> (Discord, 01-11-2026:
 * <i>"is there a way to put it on the main Commander screen without having to go
 * to Edit?"</i>).
 *
 * <p>Hace con la lista bajada lo mismo que el editor al actualizar
 * ({@code DeckBuilderScreen.applyImportUrl} con {@code replacing}): vacia el
 * principal y la zona de mando, pone el comandante, mete el principal con
 * {@link DeckEditor#addAnyway} y el companero. Lo demas del mazo — su nombre, su
 * coleccion, su enlace, la funda, el banquillo — se queda. Y solo guarda si el
 * mazo se puede guardar asi ({@link DeckEditor#blockingProblem}): si no, no
 * toca nada y dice por que, para arreglarlo en el editor.
 *
 * <p>Sin JavaFX: la usa la pantalla de inicio.
 */
public final class DeckUpdate {

    private DeckUpdate() {
    }

    /**
     * @param saved  el mazo guardado de nuevo (el del almacen), o null si no se guardo
     * @param added  cuantas cartas entraron en el principal
     * @param blocking por que no se guardo (lo dice el editor, ya traducido); null si se guardo
     */
    public record Result(Deck saved, int added, String blocking) {
    }

    public static Result apply(final DeckContext where, final Deck current, final Deck loaded) {
        final DeckEditor editor = DeckEditor.copyOf(where, current);
        editor.clearForUpdate();
        final List<PaperCard> companions = DeckImporter.companionsOf(loaded, editor.usesCommander());
        for (final PaperCard c : loaded.getCommanders()) {
            if (!companions.contains(c)) {
                editor.setCommander(c);
            }
        }
        int added = 0;
        for (final Map.Entry<PaperCard, Integer> e : loaded.getMain()) {
            added += editor.addAnyway(e.getKey(), e.getValue());
        }
        // Despues del principal, como el editor: setCompanion la saca de ahi.
        for (final PaperCard c : companions) {
            if (editor.setCompanion(c) == null) {
                break;
            }
        }
        final String blocking = editor.blockingProblem();
        if (blocking != null) {
            return new Result(null, added, blocking);
        }
        editor.save();
        return new Result(where.storage().get(editor.getDeck().getName()), added, null);
    }
}
