package forge.neo;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Los textos de <b>nuestra</b> interfaz, en el idioma elegido.
 *
 * <p>Forge ya viene traducido a diez idiomas ({@link NeoLanguage}), pero eso
 * traduce <b>sus</b> mensajes: los prompts del motor, las fases, los dialogos
 * que nacen en {@code forge-gui}. Nuestro cromo — "JUGAR", "Ajustes",
 * "Volver" — estaba escrito en castellano a pelo, asi que cambiar a Deutsch
 * dejaba media aplicacion sin traducir. Esta clase es donde vive ese cromo.
 *
 * <p><b>Por que una tabla propia y no el {@code Localizer} de Forge.</b> Sus
 * 3.600 claves son de SU interfaz: no cubren nuestras frases, y depender de
 * ellas ataria nuestros textos a que Forge no las renombre en un rebase. Un
 * fichero nuestro por idioma no puede romperse desde fuera.
 *
 * <p><b>Se puede traducir por pantallas, no de golpe.</b> Si falta una clave se
 * cae al ingles, y si tampoco esta se devuelve la clave: una pantalla a medio
 * traducir sigue siendo usable, y lo que falta se ve.
 */
public final class NeoText {

    private NeoText() {
    }

    /** Donde viven los ficheros, dentro del jar. */
    private static final String DIR = "/forge/neo/lang/";

    /** El de respaldo: si falta una clave en tu idioma, sale en ingles. */
    private static final String FALLBACK = "en-US";

    private static Properties chosen;
    private static Properties fallback;

    private static synchronized void load() {
        if (chosen != null) {
            return;
        }
        fallback = read(FALLBACK);
        final String id = NeoLanguage.current();
        chosen = FALLBACK.equals(id) ? fallback : read(id);
    }

    private static Properties read(final String id) {
        final Properties p = new Properties();
        try (InputStream in = NeoText.class.getResourceAsStream(DIR + "neo-" + id + ".properties")) {
            if (in != null) {
                // Los .properties se leen en Latin-1 salvo que se diga otra
                // cosa, y los nuestros llevan acentos.
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
        } catch (final IOException e) {
            System.err.println("[neo] no se ha podido leer los textos de " + id + ": " + e);
        }
        return p;
    }

    /**
     * Vuelve a leer los ficheros.
     *
     * <p>Solo hace falta en las pruebas, que cambian de idioma sin reiniciar.
     */
    public static synchronized void reload() {
        chosen = null;
        fallback = null;
    }

    /** El texto de esa clave. Si no esta, en ingles; si tampoco, la clave. */
    public static String get(final String key) {
        load();
        final String mine = chosen.getProperty(key);
        if (mine != null && !mine.isEmpty()) {
            return mine;
        }
        final String english = fallback.getProperty(key);
        return english == null || english.isEmpty() ? key : english;
    }

    /**
     * El texto de esa clave con huecos rellenos: {@code {0}}, {@code {1}}...
     *
     * <p>No se usa {@code MessageFormat} a proposito: se come las comillas
     * simples, y los nombres de carta van llenos ({@code Wak'dern}).
     */
    public static String get(final String key, final Object... args) {
        String out = get(key);
        for (int i = 0; i < args.length; i++) {
            out = out.replace("{" + i + "}", args[i] == null ? "" : args[i].toString());
        }
        return out;
    }
}
