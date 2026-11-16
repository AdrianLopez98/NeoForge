package forge.neo.deck;

import java.util.List;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * <b>IMPORTAR UN MAZO NUEVO DESDE EL SELECTOR, SIN PASAR POR EL EDITOR</b>
 * (itch.io, 16-11-2026: en PC eran siete pasos — Nuevo mazo, Importar lista,
 * pegar, Importar, Guardar, Volver, confirmar la salida — y en Android tres).
 *
 * <p>Hace con el mazo leido lo mismo que el editor al importar
 * ({@code DeckBuilderScreen.applyImportedDeck}): comandante primero, el
 * principal y el banquillo con {@link DeckEditor#addAnyway}, el companero
 * despues. Y lo guarda <b>solo si el editor lo dejaria guardar</b>
 * ({@link DeckEditor#blockingProblem}): si lleva algo prohibido o fuera de la
 * identidad, no se guarda nada y se devuelve el editor para abrirlo con la
 * lista ya dentro y arreglarlo ahi — la misma regla que en el editor, sin
 * atajo que se la salte.
 *
 * <p>Sin JavaFX: la usa la pantalla de inicio.
 */
public final class DeckImportNew {

    private DeckImportNew() {
    }

    /**
     * @param saved   el mazo guardado (el del almacen), o null si no se pudo guardar
     * @param editor  el editor con la lista dentro, SIN guardar, si {@code saved} es null
     * @param added   cuantas cartas entraron
     */
    public record Result(Deck saved, DeckEditor editor, int added) {
    }

    /** Si lo pegado es un enlace de mazo y no una lista (la misma regla que el constructor). */
    public static boolean looksLikeUrl(final String text) {
        final String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.contains("\n") || t.contains(" ")) {
            return false;
        }
        final String lower = t.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.contains("moxfield.com") || lower.contains("archidekt.com")
                || lower.contains("tappedout.net") || lower.contains("mtggoldfish.com");
    }

    /**
     * @param fallbackName el nombre si el mazo no trae uno (una lista pegada)
     * @param url          de donde salio, para "Actualizar desde el enlace"; null si se pego
     */
    public static Result apply(final DeckContext where, final Deck loaded, final String fallbackName,
                               final String url) {
        final String base = loaded.getName() != null && !loaded.getName().isBlank()
                ? loaded.getName() : fallbackName;
        final DeckEditor editor = DeckEditor.createNew(where, base);
        editor.setName(editor.freeName(base));

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
        if (loaded.has(DeckSection.Sideboard)) {
            for (final Map.Entry<PaperCard, Integer> e : loaded.get(DeckSection.Sideboard)) {
                if (!companions.contains(e.getKey())) {
                    added += editor.addSideboardAnyway(e.getKey(), e.getValue());
                }
            }
        }
        // Despues del principal, como el editor: setCompanion la saca de ahi.
        for (final PaperCard c : companions) {
            if (editor.setCompanion(c) == null) {
                break;
            }
        }
        if (url != null) {
            editor.setSource(url);
        }
        if (added == 0 && editor.commanders().isEmpty()) {
            return new Result(null, null, 0);
        }
        if (editor.blockingProblem() != null) {
            return new Result(null, editor, added);
        }
        editor.save();
        return new Result(where.storage().get(editor.getDeck().getName()), null, added);
    }
}
