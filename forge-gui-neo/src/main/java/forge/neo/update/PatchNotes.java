package forge.neo.update;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * <b>Las notas del parche, al abrir una version nueva</b> (Ana, 11-11-2026,
 * pedido por quien ya actualiza desde dentro del juego: «que al entrar salgan
 * las notas del parche»). Y con ellas, un boton de apoyar el proyecto: el
 * popup de donar de itch.io era lo unico que veia quien actualizaba, y con el
 * actualizador ya no pasa por ahi.
 *
 * <p><b>De donde salen, sin escribirlas a mano.</b> Las notas de cada version
 * ya existen: son las de la release {@code v<version>} de GitHub, las que se
 * escriben al cerrar version (para Obtainium y para el actualizador, que baja
 * el zip de esa misma release). Se piden por
 * {@code api.github.com/repos/AdrianLopez98/NeoForge/releases/tags/v<version>}
 * y se lee su {@code body}, en Markdown, que aqui se deja en texto plano.
 *
 * <p><b>Cuando.</b> Solo si la version que arranca es MAS NUEVA que la ultima
 * cuyas notas se ensenyaron (o la ultima que arranco): una peticion, con tope,
 * en su hilo, y sin red no pasa nada (se vuelve a probar en el siguiente
 * arranque). Una instalacion nueva no las ensenya: apunta su version y ya.
 * Ver {@code red-sin-bucles-ni-sondeos}: UNA vez por arranque y solo con motivo.
 *
 * <p>Java puro y sin API que Android no tenga en la 26.
 */
public final class PatchNotes {

    private PatchNotes() {
    }

    static final String API = "https://api.github.com/repos/AdrianLopez98/NeoForge/releases/tags/v";
    private static final int CONNECT_MS = 5_000;
    private static final int READ_MS = 5_000;

    /** Lo que se guarda en los ajustes: la version cuyas notas ya se vieron. */
    public static final String SEEN_KEY = "patchNotesSeen";

    /**
     * Si toca ensenyar las notas de {@code current} habiendo visto las de
     * {@code seen}. Sin nada visto (instalacion nueva), no.
     */
    public static boolean shouldShow(final String current, final String seen) {
        if (current == null || current.isBlank() || seen == null || seen.isBlank()) {
            return false;
        }
        return ItchVersion.compare(current, seen) > 0;
    }

    /**
     * Pide las notas de {@code version} en un hilo aparte y avisa con el texto
     * ya en limpio, o con null si no se han podido leer (sin red, sin release,
     * GitHub ocupado, respuesta rara). Nunca lanza.
     */
    public static void fetch(final String version, final Consumer<String> done) {
        final Thread t = new Thread(() -> {
            String out = null;
            try {
                out = plain(body(download(API + version)));
            } catch (final Exception e) {
                System.out.println("[neo] notas del parche: no se han podido leer (" + e + ")");
            }
            try {
                done.accept(out == null || out.isBlank() ? null : out);
            } catch (final RuntimeException ignored) {
                // Quien avisa no puede tumbar nada.
            }
        }, "neo-patch-notes");
        t.setDaemon(true);
        t.start();
    }

    private static String download(final String url) throws Exception {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setRequestProperty("User-Agent", "NeoForge-updater");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            final int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                final byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                    if (buf.size() > 512 * 1024) {
                        throw new IllegalStateException("respuesta demasiado grande");
                    }
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    /** El {@code body} de una release; null si no lo hay o es un borrador. */
    static String body(final String json) {
        try {
            final JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            final JsonElement draft = o.get("draft");
            if (draft != null && draft.getAsBoolean()) {
                return null;
            }
            final JsonElement b = o.get("body");
            return b == null || b.isJsonNull() ? null : b.getAsString();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * El Markdown de la release, en texto que se lee en una etiqueta: sin
     * negritas ni codigo, los enlaces con su texto, las vinetas como "•", y
     * fuera las lineas que son para Obtainium o los scripts de descarga (no le
     * dicen nada a quien ya esta jugando).
     */
    public static String plain(final String md) {
        if (md == null) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        for (String line : md.replace("\r\n", "\n").split("\n")) {
            final String low = line.toLowerCase(java.util.Locale.ROOT);
            if (low.contains("obtainium") || low.contains("releases/latest/download")) {
                continue;
            }
            line = line.replaceAll("\\[([^\\]]*)]\\([^)]*\\)", "$1")
                    .replace("**", "").replace("__", "").replace("`", "");
            line = line.replaceAll("^\\s*#+\\s*", "");
            line = line.replaceAll("^(\\s*)[-*]\\s+", "$1• ");
            out.append(line.stripTrailing()).append('\n');
        }
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }
}
