package forge.neo;

import java.io.InputStream;
import java.util.Properties;

/**
 * Que version del motor de Forge lleva esta copia, para poder decirlo.
 *
 * <h2>Por que hace falta</h2>
 *
 * <p>Pedido por un jugador el 22-09-2026: <i>"how do we know the Forge version
 * it is running on? The official one has a 09.21 (date), for neo it only shows
 * 2.0.15"</i>. Y tiene razon en lo que le importa: cuando algo falla por una
 * carta o una regla, el fallo es <b>del motor</b>, y para reportarlo en
 * Card-Forge hace falta decir contra que version pasa.
 *
 * <p>El instalador oficial lo resuelve con el nombre del fichero
 * ({@code forge-installer-2.0.15-SNAPSHOT-09.21.jar}): version <b>y fecha</b>.
 * Aqui no habia ninguna de las dos.
 *
 * <h2>Por que no se le pregunta a {@code BuildInfo}</h2>
 *
 * <p>{@code BuildInfo.getVersionString()} lee {@code Implementation-Version}
 * del manifiesto, y <b>los jars del motor que compilamos no lo llevan</b>
 * (comprobado: el manifiesto de {@code forge-core} solo trae
 * {@code Created-By} y {@code Build-Jdk-Spec}), asi que devuelve
 * {@code "GIT"}. Y {@code BuildInfo.getTimestamp()} lee un {@code /build.txt}
 * que generan los modulos de Forge con filtrado de recursos — <b>ninguno de
 * los que consumimos</b>. O sea que las dos preguntas obvias contestan nada.
 *
 * <p>Asi que se sella al compilar, en nuestro modulo y con nuestro pom
 * ({@code build.properties} filtrado), que ademas es lo unico que la regla de
 * oro permite: ni un fichero de Forge tocado.
 *
 * <h2>Lo que dice, y lo que NO dice</h2>
 *
 * <p>Dice la version del motor y <b>cuando se compilo esta copia</b>. No dice
 * el commit exacto de Card-Forge: para saberlo habria que leer git al compilar,
 * y {@code build.cmd} no siempre corre sobre un arbol con git a mano (el zip
 * que se reparte no lleva repositorio). La fecha de compilacion es la misma
 * informacion que da el instalador oficial y se actualiza sola en cada
 * {@code actualizar.bat}, que es justo lo que se pedia: <i>que no sea un texto
 * estatico</i>.
 *
 * <p>⚠️ En el arbol de desarrollo, si se arranca con las clases sueltas sin
 * recompilar, la fecha es la de la ultima compilacion. Es la verdad: el motor
 * que corre es ese.
 */
public final class NeoVersion {

    private static final String FILE = "/forge/neo/build.properties";

    private static String forgeVersion;
    private static String builtAt;
    private static boolean read;

    private NeoVersion() {
    }

    private static synchronized void load() {
        if (read) {
            return;
        }
        read = true;
        try (InputStream in = NeoVersion.class.getResourceAsStream(FILE)) {
            if (in == null) {
                return;
            }
            final Properties p = new Properties();
            p.load(in);
            forgeVersion = clean(p.getProperty("forgeVersion"));
            builtAt = clean(p.getProperty("builtAt"));
        } catch (final Exception e) {
            // Un adorno del menu no puede impedir abrir el juego.
            System.err.println("[neo] no se ha podido leer la version: " + e);
        }
    }

    /**
     * Un valor sellado de verdad, o {@code null}.
     *
     * <p>Si alguien arranca sin pasar por Maven, el fichero llega con el
     * {@code ${...}} sin sustituir. Ensenyar eso seria peor que no ensenyar
     * nada: parece un fallo y ademas no informa.
     */
    private static String clean(final String v) {
        if (v == null) {
            return null;
        }
        final String t = v.trim();
        return t.isEmpty() || t.startsWith("${") ? null : t;
    }

    /** La version del motor ({@code 2.0.15-SNAPSHOT}), o {@code null}. */
    public static String forgeVersion() {
        load();
        return forgeVersion;
    }

    /** Cuando se compilo esta copia ({@code 2026-09-22}), o {@code null}. */
    public static String builtAt() {
        load();
        return builtAt;
    }

    /**
     * Lo que se ensenya en el menu, o {@code null} si no hay nada que decir.
     *
     * <p>Mismo formato que el instalador oficial, que es con lo que la gente va
     * a comparar: {@code Forge 2.0.15-SNAPSHOT · 2026-09-22}.
     */
    public static String engineLabel() {
        load();
        if (forgeVersion == null && builtAt == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder("Forge ");
        sb.append(forgeVersion == null ? "?" : forgeVersion);
        if (builtAt != null) {
            sb.append(" \u00b7 ").append(builtAt);
        }
        return sb.toString();
    }
}
