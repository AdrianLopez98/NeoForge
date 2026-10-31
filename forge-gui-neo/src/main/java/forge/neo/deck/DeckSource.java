package forge.neo.deck;

import forge.deck.Deck;

/**
 * <b>De que enlace salio un mazo</b> (Moxfield, Archidekt, TappedOut,
 * MTGGoldfish), para poder volver a traerlo con un boton (Discord, 30-10-2026:
 * <i>"'Update Deck' button that is a one-click solution for any decks that were
 * uploaded using a Moxfield link"</i>).
 *
 * <p>Va en el propio {@code .dck}, en las etiquetas de Forge ({@code Tags=}),
 * como {@link HouseRules}: el mazo lleva su enlace a donde vaya, PC o Android.
 * Solo lo tienen los mazos importados por enlace desde que existe esto: los de
 * antes no saben de donde vinieron.
 *
 * <p>Un enlace con comas no se apunta: la coma es el separador de
 * {@code Tags=} y lo partiria en dos etiquetas.
 *
 * <p>En el jar: la usa NeoForge Android.
 */
public final class DeckSource {

    private DeckSource() {
    }

    /** El principio de la etiqueta; detras va el enlace tal cual. */
    public static final String TAG = "neo-source:";

    /** El enlace del que salio, o null. */
    public static String of(final Deck deck) {
        if (deck == null) {
            return null;
        }
        for (final String t : deck.getTags()) {
            if (t != null && t.startsWith(TAG) && t.length() > TAG.length()) {
                return t.substring(TAG.length());
            }
        }
        return null;
    }

    /**
     * Apunta el enlace (o lo quita con null).
     *
     * @return si ha cambiado algo
     */
    public static boolean set(final Deck deck, final String url) {
        if (deck == null) {
            return false;
        }
        final String clean = url == null ? null : url.trim();
        final boolean storable = clean != null && !clean.isEmpty()
                && clean.indexOf(',') < 0 && clean.indexOf('\n') < 0 && clean.indexOf('\r') < 0;
        final String before = of(deck);
        final String after = storable ? clean : null;
        if (before == null ? after == null : before.equals(after)) {
            return false;
        }
        deck.getTags().removeIf(t -> t != null && t.startsWith(TAG));
        if (after != null) {
            deck.getTags().add(TAG + after);
        }
        return true;
    }
}
