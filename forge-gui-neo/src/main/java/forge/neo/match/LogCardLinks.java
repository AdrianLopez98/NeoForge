package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.game.GameView;
import forge.game.card.CardView;
import forge.trackable.TrackableTypes;

/**
 * <b>Las cartas de una linea del registro</b>, para poder verlas (Discord,
 * 17-10-2026: <i>"More information in the log tab, perhaps clicking or
 * hovering a card name can display a picture of it, would make it easy at a
 * glance to see what happened"</i>).
 *
 * <p>Parte el texto de una entrada en trozos: texto suelto o una carta. Las
 * cartas salen de dos sitios, los dos del motor:
 * <ul>
 *   <li>Las que el texto nombra con su numero de partida, "Oni of Wild Places
 *       (175)": el numero es el id de la carta, y el {@code Tracker} de la
 *       partida da su {@code CardView}. Se enlaza el nombre entero con el
 *       numero si el texto lo lleva justo delante; si no, solo el numero.</li>
 *   <li>La carta que el motor adjunta a la entrada ({@code GameLogEntry.sourceCard}):
 *       "Remingten cast Tinybones, Pocket Nuisance" no lleva numero, pero la
 *       entrada sabe que es Tinybones. Se enlaza donde aparezca su nombre.</li>
 * </ul>
 * Si no se reconoce nada, la linea es un solo trozo de texto: nunca se inventa.
 *
 * <p>Clase pura: la pintan el escritorio ({@code GameLogView}) y Android, por el jar.
 */
public final class LogCardLinks {

    private static final Pattern ID = Pattern.compile("\\((\\d{1,7})\\)");

    private LogCardLinks() {
    }

    /** Un trozo: texto, y la carta si es un enlace (null si es texto suelto). */
    public record Piece(String text, CardView card) {
    }

    /** El buscador de cartas por id de una partida (null si no la hay). */
    public static IntFunction<CardView> lookupIn(final GameView gv) {
        if (gv == null || gv.getTracker() == null) {
            return null;
        }
        return id -> {
            try {
                return gv.getTracker().getObj(TrackableTypes.CardViewType, id);
            } catch (final RuntimeException e) {
                return null;
            }
        };
    }

    /**
     * Los trozos de {@code message}.
     *
     * @param source la carta de la entrada, o null
     * @param lookup carta por id, o null (entonces solo se enlaza {@code source})
     */
    public static List<Piece> split(final String message, final CardView source,
                                    final IntFunction<CardView> lookup) {
        final List<Piece> out = new ArrayList<>();
        if (message == null || message.isEmpty()) {
            return out;
        }
        // 1. Lo que lleva numero.
        final List<int[]> spans = new ArrayList<>();
        final List<CardView> cards = new ArrayList<>();
        if (lookup != null) {
            final Matcher m = ID.matcher(message);
            while (m.find()) {
                CardView card;
                try {
                    card = lookup.apply(Integer.parseInt(m.group(1)));
                } catch (final RuntimeException e) {
                    card = null;
                }
                if (card == null) {
                    continue;
                }
                int start = m.start();
                final int nameStart = nameBefore(message, m.start(), card);
                if (nameStart >= 0) {
                    start = nameStart;
                }
                if (!overlaps(spans, start, m.end())) {
                    spans.add(new int[] {start, m.end()});
                    cards.add(card);
                }
            }
        }
        // 2. La carta de la entrada, donde salga su nombre sin numero.
        if (source != null) {
            for (final String name : namesOf(source)) {
                final int at = indexOfIgnoreCase(message, name, 0);
                if (at >= 0 && !overlaps(spans, at, at + name.length())) {
                    spans.add(new int[] {at, at + name.length()});
                    cards.add(source);
                    break;
                }
            }
        }
        if (spans.isEmpty()) {
            out.add(new Piece(message, null));
            return out;
        }
        // En orden, con el texto de entre medias.
        final Integer[] order = new Integer[spans.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(spans.get(a)[0], spans.get(b)[0]));
        int pos = 0;
        for (final int i : order) {
            final int[] s = spans.get(i);
            if (s[0] > pos) {
                out.add(new Piece(message.substring(pos, s[0]), null));
            }
            out.add(new Piece(message.substring(s[0], s[1]), cards.get(i)));
            pos = s[1];
        }
        if (pos < message.length()) {
            out.add(new Piece(message.substring(pos), null));
        }
        return out;
    }

    /** Donde empieza el nombre de la carta justo antes de "(id)", o -1. */
    private static int nameBefore(final String message, final int parenAt, final CardView card) {
        int end = parenAt;
        while (end > 0 && message.charAt(end - 1) == ' ') {
            end--;
        }
        for (final String name : namesOf(card)) {
            final int start = end - name.length();
            if (start >= 0 && message.regionMatches(true, start, name, 0, name.length())) {
                return start;
            }
        }
        return -1;
    }

    /** El nombre que puede llevar el texto: el de la cara de ahora, el de la carta y el traducido. */
    private static List<String> namesOf(final CardView card) {
        final List<String> names = new ArrayList<>();
        try {
            if (card.getCurrentState() != null) {
                add(names, card.getCurrentState().getName());
                add(names, card.getCurrentState().getTranslatedName());
            }
            add(names, card.getName());
        } catch (final RuntimeException ignored) {
            // sin nombre: solo el numero
        }
        // El mas largo primero: "Tinybones, Pocket Nuisance" antes que "Tinybones".
        names.sort((a, b) -> Integer.compare(b.length(), a.length()));
        return names;
    }

    private static void add(final List<String> names, final String name) {
        if (name != null && name.length() >= 2 && !names.contains(name)) {
            names.add(name);
        }
    }

    private static int indexOfIgnoreCase(final String text, final String part, final int from) {
        return text.toLowerCase(Locale.ROOT).indexOf(part.toLowerCase(Locale.ROOT), from);
    }

    private static boolean overlaps(final List<int[]> spans, final int start, final int end) {
        for (final int[] s : spans) {
            if (start < s[1] && end > s[0]) {
                return true;
            }
        }
        return false;
    }
}
