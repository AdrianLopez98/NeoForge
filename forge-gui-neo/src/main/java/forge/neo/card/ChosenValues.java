package forge.neo.card;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.util.CardTranslation;
import forge.util.Localizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lo que se ELIGIO para una carta: el tipo de criatura (Herald's Horn, Roaming
 * Throne, Cavern of Souls), el color, la carta nombrada (Pithing Needle), el
 * jugador, el numero, la direccion o par/impar.
 *
 * <p>Discord, 13-10-2026: <i>"Any way to indicate what someone chooses when
 * someone needs to choose a creature type?"</i>. El motor lo publica todo en el
 * {@code CardView}; no lo ensenyabamos en ningun sitio, asi que habia que
 * acordarse. Ahora sale en la mesa (una etiqueta en la carta) y en la carta
 * ampliada, en el escritorio y en Android.
 *
 * <p>Java puro y sin JavaFX: Android lo usa por el jar (nada de API que no
 * tenga Android 8). Los VALORES salen ya en el idioma del juego cuando el motor
 * los traduce (colores, direccion, nombre de carta); los tipos de criatura van
 * como los escribe Forge. Los ROTULOS ("Tipo de criatura") los pone cada
 * interfaz con su sistema de textos, por la {@link Entry#kind}.
 */
public final class ChosenValues {

    private ChosenValues() {
    }

    /** Que clase de eleccion: el rotulo lo pone la interfaz. */
    public enum Kind { TYPE, COLOR, CARD, PLAYER, NUMBER, DIRECTION, PARITY }

    /** Una eleccion y su valor, ya escrito para ensenyarlo. */
    public static final class Entry {
        public final Kind kind;
        public final String value;

        Entry(final Kind kind, final String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    /** Todo lo elegido para esta carta, en orden; vacio si nada. */
    public static List<Entry> of(final CardView card) {
        if (card == null) {
            return Collections.emptyList();
        }
        final List<Entry> out = new ArrayList<>();
        try {
            add(out, Kind.TYPE, card.getChosenType());
            add(out, Kind.TYPE, card.getChosenType2());
            final List<String> colors = card.getChosenColors();
            if (colors != null && !colors.isEmpty()) {
                final StringBuilder sb = new StringBuilder();
                for (final String c : colors) {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(colorName(c));
                }
                add(out, Kind.COLOR, sb.toString());
            }
            final List<String> named = card.getNamedCard();
            if (named != null) {
                for (final String n : named) {
                    add(out, Kind.CARD, n == null || n.isEmpty() ? null : CardTranslation.getTranslatedName(n));
                }
            }
            final PlayerView player = card.getChosenPlayer();
            if (player != null) {
                add(out, Kind.PLAYER, players(card, player));
            }
            add(out, Kind.NUMBER, card.getChosenNumber());
            if (card.getChosenDirection() != null) {
                final String d = card.getChosenDirection().toString();
                add(out, Kind.DIRECTION, "Left".equalsIgnoreCase(d) ? msg("lblLeft", d)
                        : "Right".equalsIgnoreCase(d) ? msg("lblRight", d) : d);
            }
            if (card.getChosenEvenOdd() != null) {
                add(out, Kind.PARITY, card.getChosenEvenOdd().toString());
            }
        } catch (final RuntimeException e) {
            // Un CardView a medias (un invitado en red, una carta que se va) no
            // vale una excepcion en la mesa: lo que se haya leido, se ensenya.
        }
        return out;
    }

    /**
     * La etiqueta corta para la carta en la mesa: los valores, sin rotulos
     * ("Elf", "Green", "Sol Ring"), o null si no hay nada elegido.
     */
    public static String badge(final CardView card) {
        final List<Entry> all = of(card);
        if (all.isEmpty()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        for (final Entry e : all) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(e.value);
        }
        return sb.toString();
    }

    /**
     * El jugador elegido y, si la carta eligio MAS de uno, los demas.
     *
     * <p>Bitter Feud elige dos (Discord, 19-10-2026: <i>"I'm not seeing a way
     * to see both players effected by Bitter Feud ... it only showed my name"</i>).
     * Su guion elige el primero y lo RECUERDA ({@code RememberChosen}), y luego
     * elige el segundo, que pisa al primero como "elegido": la vista solo
     * publica ese. Los recordados no tienen propiedad publica en el
     * {@code CardView}, pero el motor los escribe al final de su texto
     * ({@code getText}: "Remembered:" y uno por linea, un jugador por su
     * nombre). Se cogen de ahi los que son el nombre de un jugador de la
     * partida, y solo si la carta ya tiene un jugador elegido: los recordados
     * sirven para muchas otras cosas.
     */
    private static String players(final CardView card, final PlayerView chosen) {
        final java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        try {
            final java.util.Set<String> inGame = new java.util.HashSet<>();
            for (final PlayerView p : new PlayerView[] {card.getController(), card.getOwner()}) {
                if (p != null) {
                    inGame.add(p.getName());
                    if (p.getOpponents() != null) {
                        for (final PlayerView o : p.getOpponents()) {
                            inGame.add(o.getName());
                        }
                    }
                }
            }
            final String text = card.getText();
            final int at = text == null ? -1 : text.indexOf("Remembered:");
            if (at >= 0) {
                for (final String line : text.substring(at + "Remembered:".length()).split("[\r\n]+")) {
                    final String n = line.trim();
                    if (!n.isEmpty() && inGame.contains(n)) {
                        names.add(n);
                    }
                }
            }
        } catch (final RuntimeException e) {
            // Sin los recordados, al menos el elegido.
        }
        names.add(chosen.getName());
        return String.join(", ", names);
    }

    private static void add(final List<Entry> out, final Kind kind, final String value) {
        if (value != null && !value.trim().isEmpty()) {
            out.add(new Entry(kind, value.trim()));
        }
    }

    /** "green" -> "Verde": la palabra del motor en el idioma del juego. */
    private static String colorName(final String c) {
        if (c == null) {
            return "";
        }
        switch (c.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "white": return msg("lblWhite", "White");
            case "blue": return msg("lblBlue", "Blue");
            case "black": return msg("lblBlack", "Black");
            case "red": return msg("lblRed", "Red");
            case "green": return msg("lblGreen", "Green");
            case "colorless": return msg("lblColorless", "Colorless");
            default: return c;
        }
    }

    private static String msg(final String key, final String fallback) {
        try {
            final String s = Localizer.getInstance().getMessage(key);
            return s == null || s.isEmpty() ? fallback : s;
        } catch (final RuntimeException e) {
            return fallback;
        }
    }
}
