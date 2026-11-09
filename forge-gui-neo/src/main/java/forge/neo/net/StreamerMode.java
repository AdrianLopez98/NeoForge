package forge.neo.net;

import java.util.regex.Pattern;

import forge.neo.NeoSettings;

/**
 * <b>Modo streamer: las direcciones IP no salen en pantalla</b> (Discord,
 * 09-11-2026: <i>"A streamer mode to hide the IP in the private games,
 * please"</i>). Quien hospeda una partida privada ensenya su IP para que entren;
 * en directo, eso es ensenyarsela a todo el chat.
 *
 * <p>Puesto, toda IP que pinta la interfaz pasa por {@link #hide} y sale como
 * {@link #MASK}: la tarjeta de crear partida, las direcciones de la sala (cada
 * una con su "Mostrar", por si quieres mirarla fuera de camara), los errores de
 * conexion (su detalle suele llevar IP y puerto) y el campo de unirse, que pasa
 * a ser de contrasenya. <b>"Copiar" copia la de verdad</b>: es la forma de
 * pasarsela al amigo por privado sin que se vea.
 *
 * <p>Apagado de fabrica; se pone desde la propia pantalla de partida en red y
 * desde la sala, donde hace falta, sin ir a Ajustes. Java puro: Android lo usa
 * por el jar.
 */
public final class StreamerMode {

    private StreamerMode() {
    }

    /** Lo que sale en vez de la IP. */
    public static final String MASK = "•••.•••.•••.•••";

    /** IPv4, con su puerto si lo lleva (y la barra que pone Java delante: "/1.2.3.4:5"). */
    private static final Pattern IPV4 = Pattern.compile(
            "/?\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b(?::\\d{1,5})?");
    /** IPv6, con corchetes y puerto si los lleva. Al menos dos ":" y un grupo hexadecimal. */
    private static final Pattern IPV6 = Pattern.compile(
            "\\[?[0-9A-Fa-f]{0,4}(?::[0-9A-Fa-f]{0,4}){2,7}(?:%[\\w.]+)?\\]?(?::\\d{1,5})?");

    public static boolean on() {
        // -Dneo.streamer=true|false: para capturar sin tocar los ajustes del jugador.
        final String forced = System.getProperty("neo.streamer");
        if (forced != null) {
            return Boolean.parseBoolean(forced);
        }
        try {
            return NeoSettings.streamerMode();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    public static void set(final boolean on) {
        NeoSettings.setBool(NeoSettings.STREAMER_MODE, on);
        NeoSettings.save();
    }

    /** El texto con las IP tapadas si el modo esta puesto; si no, tal cual. */
    public static String hide(final String text) {
        return on() ? mask(text) : text;
    }

    /** Tapa las IP (v4 y v6) de un texto, este puesto el modo o no. */
    public static String mask(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = IPV4.matcher(text).replaceAll(MASK);
        out = IPV6.matcher(out).replaceAll(m -> looksLikeIpv6(m.group()) ? MASK : m.group());
        return out;
    }

    /** Para no tapar una hora ("12:30:45"): una IPv6 lleva "::" o algun grupo con letra, o 3+ ":" con hexadecimal. */
    private static boolean looksLikeIpv6(final String s) {
        final String core = s.replaceAll("[\\[\\]]", "").replaceAll("%\\w+", "");
        if (core.contains("::")) {
            return true;
        }
        final long colons = core.chars().filter(c -> c == ':').count();
        return colons >= 3 && core.matches("(?i).*[a-f].*") || colons >= 5;
    }
}
