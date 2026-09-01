package forge.neo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Que TODOS los datos del jugador vivan dentro de la carpeta del juego.
 *
 * <p>Se activa con {@code -Dneo.dataDir=<carpeta>}. Sin esa bandera esto no
 * hace absolutamente nada, que es lo que tiene que pasar en el arbol de
 * desarrollo: ahi los datos siguen en {@code %APPDATA%\Forge}, compartidos con
 * la instalacion normal de Forge, que es justo lo que se quiere para probar con
 * los mazos de verdad.
 *
 * <p><b>Para que hace falta.</b> Para poder darle el juego a alguien: se
 * descomprime, se juega, y todo lo suyo — mazos, aventuras, preferencias,
 * imagenes descargadas — se queda dentro de esa carpeta. Se borra la carpeta y
 * no queda rastro. Y al reves, igual de importante: <b>no toca los datos del
 * Forge que ya tenga</b>. Ni los lee ni los pisa.
 *
 * <p><b>Como.</b> Forge ya sabe hacerlo: {@code ForgeProfileProperties} lee un
 * {@code forge.profile.properties} del directorio de assets y de ahi saca donde
 * van los datos, la cache y los mazos. Lo unico que faltaba era escribirlo.
 *
 * <p><b>Y por que se escribe cada vez que se arranca</b>, en vez de meterlo en
 * el zip ya hecho: porque ese fichero guarda <b>rutas absolutas</b>, y quien lo
 * descomprima lo pondra donde le parezca. Un fichero fijo apuntaria a la
 * carpeta de otro. Escribirlo en el arranque, a partir de donde esta el
 * programa <i>ahora</i>, hace que la carpeta se pueda mover, renombrar o pasar
 * a un USB sin que nada se entere.
 *
 * <p>⚠️ <b>Esto se llama lo PRIMERO de todo, antes incluso del registro.</b> En
 * cuanto alguien toca {@code ForgeConstants}, su inicializador estatico lee el
 * fichero de perfil y ya no hay vuelta atras: escribirlo despues no cambiaria
 * nada y los datos irian al sitio de siempre. Por eso aqui no se usa
 * {@code ForgeConstants} ni de lejos — la ruta de assets se calcula a mano, con
 * la misma regla que {@code NeoGuiBase}.
 */
public final class NeoPortable {

    private NeoPortable() {
    }

    /** La carpeta de datos, o {@code null} si no estamos en modo portable. */
    private static File dataDir;

    /**
     * Donde van los datos del jugador, o {@code null} si van donde siempre.
     *
     * <p>Lo necesita {@link NeoLog}, que escribe antes de que el motor exista y
     * por eso calcula su carpeta a mano.
     */
    public static File dataDir() {
        return dataDir;
    }

    public static boolean isPortable() {
        return dataDir != null;
    }

    /**
     * Deja el fichero de perfil apuntando dentro de la carpeta del juego.
     *
     * <p>Si algo falla, se sigue sin modo portable: los datos iran a
     * {@code %APPDATA%\Forge}. Es peor que lo que se pedia, pero se juega — y
     * un juego que no arranca por donde guarda las preferencias seria mucho
     * peor.
     */
    public static void apply() {
        final String requested = System.getProperty("neo.dataDir");
        if (requested == null || requested.isBlank()) {
            return;
        }
        try {
            final File dir = new File(requested).getCanonicalFile();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                System.err.println("[portable] no se ha podido crear " + dir);
                return;
            }
            write(dir);
            dataDir = dir;
            System.out.println("[portable] los datos van a " + dir);
        } catch (final IOException | RuntimeException e) {
            System.err.println("[portable] no se ha podido activar: " + e);
        }
    }

    /**
     * Escribe el {@code forge.profile.properties}.
     *
     * <p>Las cuatro rutas se dan explicitas y no se dejan a que Forge las
     * derive: {@code cardPicsDir} cuelga de {@code cacheDir} pero
     * {@code decksDir} cuelga de {@code userDir}, y una sola que se escapara
     * dejaria <b>parte</b> de los datos fuera de la carpeta. Medio portable es
     * lo peor de los dos mundos: parece que funciona hasta que aparecen los
     * mazos de otro.
     *
     * <p>{@code Properties.store} escapa las barras invertidas de Windows por
     * su cuenta, que es la trampa clasica de escribir esto a mano.
     */
    private static void write(final File dir) throws IOException {
        final Properties props = new Properties();
        props.setProperty("userDir", dir.getPath());
        props.setProperty("cacheDir", new File(dir, "cache").getPath());
        props.setProperty("cardPicsDir",
                new File(dir, "cache" + File.separator + "pics" + File.separator + "cards").getPath());
        props.setProperty("decksDir", new File(dir, "decks").getPath());

        final File target = new File(assetsDir(), "forge.profile.properties");
        final File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("no existe " + parent);
        }
        try (OutputStream out = new FileOutputStream(target)) {
            props.store(new java.io.OutputStreamWriter(out, StandardCharsets.UTF_8),
                    "Lo escribe NeoForge en cada arranque. No hace falta tocarlo:"
                    + " si mueves la carpeta, se vuelve a calcular solo.");
        }
    }

    /**
     * El directorio de assets, calculado igual que {@code NeoGuiBase}.
     *
     * <p>Se repite la regla en vez de preguntarsela porque preguntarsela obliga
     * a tener ya registrado el {@code IGuiBase}, y para entonces
     * {@code ForgeConstants} puede haberse inicializado — que es exactamente lo
     * que hay que evitar.
     */
    private static File assetsDir() {
        return new File(System.getProperty("forge.assetsDir", "../forge-gui/"));
    }
}
