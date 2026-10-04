package forge.neo.update;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Que version hay en itch.io, y si es mas nueva que la que corre. Java puro:
 * sin JavaFX, sin ajustes, sin hilos — lo usan el menu del PC
 * ({@link NeoUpdate}) y el de Android, que la recibe por el jar (su regla 2.2:
 * el codigo compartido no se copia).
 *
 * <p>El numero sale de itch.io mismo: las subidas van con butler y llevan la
 * version puesta, y {@code itch.io/api/1/x/wharf/latest?target=...&channel_name=...}
 * la devuelve sin clave ni cuenta: {@code {"latest":"4.1"}}. No anyade ningun
 * paso al publicar, y <b>no puede avisar de una version que aun no se puede
 * bajar</b>: solo aparece ahi cuando ya esta subida.
 *
 * <p>La regla de todo lo de aqui: <b>lo que no se entiende es "no se sabe", y
 * "no se sabe" nunca es "hay una nueva"</b>. Sin linea, un error de itch.io o
 * un numero raro acaban igual que estar al dia: sin aviso.
 *
 * <p>⚠️ El {@code /x/} de la ruta es "experimental" en itch.io. Si un dia la
 * quitan, esto deja de avisar sin romper nada ({@code run.cmd updatecheck} lo
 * dice en su parte opcional).
 */
public final class ItchVersion {

    /** La pagina del juego: a donde llevan el boton y el aviso. */
    public static final String PAGE_URL = "https://dokkodolabs.itch.io/neo-forge";

    /** Usuario y juego en itch.io, como los escribe butler. */
    static final String TARGET = "dokkodolabs/neo-forge";

    static final String API = "https://itch.io/api/1/x/wharf/latest";

    /** El canal del APK: va por su propia numeracion (1.0.x). */
    public static final String ANDROID_CHANNEL = "android";

    private static final int TIMEOUT_MS = 5000;

    private static final Pattern LATEST =
            Pattern.compile("\"latest\"\\s*:\\s*\"([^\"\\\\]{1,32})\"");
    private static final Pattern VERSION = Pattern.compile("\\d{1,6}(\\.\\d{1,6}){0,4}");

    private ItchVersion() {
    }

    /**
     * De que avisar: {@code latest} si es mas nueva que la que corre y que la
     * descartada ("Ahora no"). Cualquier cosa que no se entienda, {@code null}.
     */
    public static String offerFor(final String current, final String latest, final String dismissed) {
        if (current == null || latest == null) {
            return null;
        }
        if (compare(latest, current) <= 0) {
            return null;
        }
        // Una descartada que no se entiende no tapa nada: si no, un
        // neo.properties tocado a mano callaria el aviso para siempre.
        if (clean(dismissed) != null && compare(latest, dismissed) <= 0) {
            return null;
        }
        return clean(latest);
    }

    /**
     * Compara dos versiones por numeros, no por letras: la 4.10 es mas nueva
     * que la 4.9, y la 4.1 es la misma que la 4.1.0. Si alguna no se entiende,
     * 0.
     */
    public static int compare(final String a, final String b) {
        final String ca = clean(a);
        final String cb = clean(b);
        if (ca == null || cb == null) {
            return 0;
        }
        final String[] pa = ca.split("\\.");
        final String[] pb = cb.split("\\.");
        // UN SOLO NUMERO PARA PC Y ANDROID (30-09-2026, decision del autor): el
        // PC paso de su 4.7 al 1.0.8 de Android. Las del esquema viejo del PC
        // -dos cifras con la primera 2 o mas: "4.6", "4.7"- son ANTERIORES a
        // cualquiera del nuevo, que lleva tres ("1.0.8"). Sin esto un PC con la
        // 1.0.8 veria en itch.io la 4.6 como "mas nueva". Una "2.0.0" futura
        // lleva tres cifras, asi que sigue siendo nueva.
        final boolean oldA = legacyPc(pa);
        final boolean oldB = legacyPc(pb);
        if (oldA != oldB) {
            return oldA ? -1 : 1;
        }
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            final int x = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            final int y = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    /** Del esquema viejo del PC (hasta la 4.7): dos cifras, la primera 2 o mas. */
    private static boolean legacyPc(final String[] parts) {
        return parts.length == 2 && Integer.parseInt(parts[0]) >= 2;
    }

    /** {@code "v4.2 "} → {@code "4.2"}; lo que no sea una version, {@code null}. */
    public static String clean(final String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        if (t.startsWith("v") || t.startsWith("V")) {
            t = t.substring(1);
        }
        return VERSION.matcher(t).matches() ? t : null;
    }

    /** El numero de la respuesta de itch.io, o {@code null} si es un error u otra cosa. */
    public static String parse(final String body) {
        if (body == null) {
            return null;
        }
        final Matcher m = LATEST.matcher(body);
        return m.find() ? clean(m.group(1)) : null;
    }

    /**
     * El canal del PC en itch.io: los nombres, tal cual se subieron (con
     * espacios: con guiones itch.io contesta {@code invalid channel}).
     *
     * <p>Mac por arquitectura; todo lo demas, el zip de Windows. Un Mac con
     * Apple Silicon que corre la version Intel (Rosetta) dice {@code x86_64}, y
     * es justo la que tiene instalada.
     */
    public static String channelFor(final boolean mac, final String arch) {
        return channelFor(mac, false, arch);
    }

    /**
     * Lo mismo, sabiendo si es Linux. Desde la 1.0.14 Linux tiene su propio
     * paquete (un {@code .tar.gz} con Java dentro, compilado en GitHub como el
     * de Mac): canal {@code linux}, y {@code linux arm64} en ARM. Antes en
     * Linux y en la Steam Deck se bajaba el zip de Windows, y se le avisaba por
     * {@code win64}.
     */
    public static String channelFor(final boolean mac, final boolean linux, final String arch) {
        final String a = arch == null ? "" : arch.toLowerCase(Locale.ROOT);
        final boolean arm = a.contains("aarch64") || a.contains("arm");
        if (mac) {
            return arm ? "mac apple silicon" : "mac apple intel";
        }
        if (linux) {
            return arm ? "linux arm64" : "linux";
        }
        return "win64";
    }

    /**
     * ⚠️ Con {@code encode(String, String)} y no con la de {@code Charset}:
     * esa es de Java 10 y en Android no existe hasta la API 33 (el APK arranca
     * en la 26). Lo mismo con {@code readNBytes} en {@link #fetch}.
     */
    public static String url(final String channel) {
        String enc;
        try {
            enc = URLEncoder.encode(channel, "UTF-8").replace("+", "%20");
        } catch (final java.io.UnsupportedEncodingException e) {
            enc = channel.replace(" ", "%20");
        }
        return API + "?target=" + TARGET + "&channel_name=" + enc;
    }

    /**
     * Una peticion, con tope de tiempo (cinco segundos para conectar y cinco
     * para leer), y el numero o {@code null}. <b>No lanza</b>: sin linea
     * devuelve {@code null} y ya. Bloquea: nunca desde un hilo de interfaz.
     *
     * @param agent quien pregunta ({@code NeoForge/4.1}), por cortesia con itch.io
     */
    public static String fetch(final String address, final String agent) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(address).openConnection();
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", agent == null ? "NeoForge" : agent);
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return null;
            }
            try (InputStream in = c.getInputStream()) {
                // Lo que se espera son veinte bytes; mas de 4 KB ya no es eso.
                // A mano y no con readNBytes: ver url().
                final byte[] buf = new byte[4096];
                int n = 0;
                int r;
                while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) {
                    n += r;
                }
                return parse(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
        } catch (final Exception | LinkageError e) {
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }
}
