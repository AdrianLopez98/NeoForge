package forge.neo.deck;

import forge.deck.Deck;

/**
 * Las notas de un mazo, en texto libre (Discord, 22-11-2026: <i>"a note
 * attached to each deck ... where the user can note specific things about his
 * deck as free text"</i>).
 *
 * <p>Viven en el <b>comentario</b> que Forge ya guarda dentro del {@code .dck}
 * ({@code [metadata] Comment=}), asi que viajan con el mazo, se copian al
 * duplicarlo y el Forge de siempre las ensenya en su lista. La pega es que
 * {@code DeckSerializer} lo escribe en UNA linea y le quita los saltos: aqui se
 * guardan como la secuencia {@code \n} (barra y ene) y se deshace al leer. Una
 * barra que escriba el jugador se dobla, para que no se confunda.
 *
 * <p>⚠️ El sellado usa ese mismo comentario para los temas del Jumpstart
 * ({@code NeoSealed}): las notas solo se ofrecen en los formatos con
 * colecciones, que no son ni draft ni sellado.
 *
 * <p>Clase pura: la usa Android por el jar.
 */
public final class DeckNotes {

    private DeckNotes() {
    }

    /** Cuanto se deja escribir. Es una nota, no un diario. */
    public static final int MAX = 4000;

    /** Las notas del mazo, con sus saltos de linea, o "" si no tiene. */
    public static String of(final Deck deck) {
        return deck == null ? "" : decode(deck.getComment());
    }

    /** Si el mazo lleva alguna nota. */
    public static boolean has(final Deck deck) {
        return !of(deck).isBlank();
    }

    /** Le pone las notas al mazo (sin guardarlo: eso es {@code DeckCollections.saveNotes}). */
    public static void set(final Deck deck, final String notes) {
        if (deck == null) {
            return;
        }
        final String clean = notes == null ? "" : notes.strip();
        deck.setComment(clean.isEmpty() ? null : encode(clean.length() > MAX ? clean.substring(0, MAX) : clean));
    }

    /** Texto con saltos -> una linea que sobrevive al {@code .dck}. */
    public static String encode(final String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n')
                .replace("\\", "\\\\").replace("\n", "\\n");
    }

    /** Lo contrario de {@link #encode}. Un comentario de Forge sin escapar sale tal cual. */
    public static String decode(final String line) {
        if (line == null || line.isEmpty()) {
            return "";
        }
        final StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                final char n = line.charAt(i + 1);
                if (n == 'n') {
                    out.append('\n');
                    i++;
                    continue;
                }
                if (n == '\\') {
                    out.append('\\');
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }
}
