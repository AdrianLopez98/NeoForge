package forge.neo.match;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>Los textos del motor que salen rotos, arreglados al pintarlos.</b>
 *
 * <p>Discord, 05-11-2026, con captura: Edifice of Authority sobre una criatura
 * ponia en el stack <i>"Archfiend of Ifnir gains hidden cardname can't attack.
 * until end of turn."</i>. La carta da una palabra clave OCULTA
 * ({@code KW$ HIDDEN CARDNAME can't attack.}) y {@code PumpEffect} la escribe
 * tal cual en minusculas detras de "gains", con su punto, y luego el "until end
 * of turn." Las reglas van bien: la criatura no puede atacar. Solo es el texto.
 *
 * <p>No se toca el motor (regla de oro): se limpia aqui, donde lo pintamos.
 * Sin JavaFX: Android lo usa por el jar.
 */
public final class EngineText {

    private EngineText() {
    }

    /** "gains hidden cardname " (y la mayuscula que traiga), que se quita entero. */
    private static final Pattern HIDDEN = Pattern.compile("(?i)\\bgains hidden cardname\\s+");

    /** El punto de la palabra clave antes de la duracion: "attack. until end of turn." */
    private static final Pattern DOT_BEFORE_DURATION =
            Pattern.compile("\\.\\s+(until end of turn|for as long as)");

    /** El texto de una entrada del stack, sin lo que el motor deja a medias. */
    public static String stackText(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        final Matcher m = HIDDEN.matcher(text);
        if (!m.find()) {
            return text;
        }
        String out = m.replaceAll("");
        out = DOT_BEFORE_DURATION.matcher(out).replaceAll(" $1");
        return out;
    }
}
