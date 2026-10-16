package forge.neo.match;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.neo.NeoText;

/**
 * Lo que dice {@code ReplayPlayer} (Forge #12255) en el idioma de la interfaz.
 *
 * <p>La reproduccion pone sus propios textos en los dos botones y en el aviso,
 * en ingles y escritos en el codigo ("Pause", "Speed 2x", "Replaying Speed
 * 1x."). Se traducen al llegar; lo que no se reconozca se ensenya tal cual, que
 * es mejor que un boton en blanco si Forge los cambia.
 *
 * <p>Clase pura: la vigila {@code replaycheck}.
 */
public final class ReplayText {

    private static final Pattern SPEED = Pattern.compile("Speed (\\d+)x");
    private static final Pattern PLAYING = Pattern.compile("Replaying Speed (\\d+)x\\.?");
    private static final Pattern PAUSED = Pattern.compile("Replay paused\\. Speed (\\d+)x\\.?");

    private ReplayText() {
    }

    /** Un boton: Pause, Resume, Close o "Speed Nx". */
    public static String label(final String label) {
        if (label == null) {
            return null;
        }
        final String l = label.trim();
        switch (l) {
            case "Pause":
                return NeoText.get("replay.pause");
            case "Resume":
                return NeoText.get("replay.resume");
            case "Close":
                return NeoText.get("replay.close");
            default:
                final Matcher m = SPEED.matcher(l);
                return m.matches() ? NeoText.get("replay.speed", m.group(1)) : label;
        }
    }

    /** El aviso: reproduciendo, en pausa o terminada. */
    public static String prompt(final String message) {
        if (message == null) {
            return "";
        }
        final String m = message.trim();
        if (m.equals("Replay finished.")) {
            return NeoText.get("replay.finished");
        }
        Matcher x = PLAYING.matcher(m);
        if (x.matches()) {
            return NeoText.get("replay.playing", x.group(1));
        }
        x = PAUSED.matcher(m);
        if (x.matches()) {
            return NeoText.get("replay.paused", x.group(1));
        }
        return message;
    }
}
