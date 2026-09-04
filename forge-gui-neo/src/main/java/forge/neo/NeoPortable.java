package forge.neo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
            dropStrayProfile();
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
     * La marca con la que se reconoce un perfil escrito por nosotros.
     *
     * <p>{@code Properties.store} deja el comentario como primera linea, asi
     * que basta con mirar las de arriba. Se compara contra una palabra que
     * <b>nadie escribiria a mano</b>: ver {@link #dropStrayProfile()}.
     */
    private static final String OURS = "NeoForge";

    /**
     * Tira el perfil que dejo una ejecucion portable ANTERIOR en este arbol.
     *
     * <h2>El fallo que arregla, y es de los caros</h2>
     *
     * <p>{@link #write} deja el {@code forge.profile.properties} en el
     * directorio de <b>assets</b>, que en el arbol de desarrollo es
     * {@code forge/forge-gui/} — o sea una carpeta compartida por TODAS las
     * ejecuciones, no una del modo portable. Cualquier prueba con
     * {@code -Dneo.dataDir=...} (el {@code portablecheck}, probar el zip a
     * mano) lo escribe ahi apuntando a su carpeta temporal... <b>y no lo quita
     * al acabar</b>. A partir de ese momento, y para siempre, el juego normal
     * arranca leyendo los datos de esa carpeta.
     *
     * <p>Y el sintoma no se parece a la causa: no falla nada, no hay
     * excepcion, el juego abre perfectamente. Simplemente <b>tus mazos, tus
     * fundas, tu tapete, tu musica y tus ajustes ya no estan</b> — porque se
     * estan buscando en otro sitio. Reportado jugando el 03-09-2026:
     * <i>"parece no estar cargando mis preferencias, en personalizacion no
     * salen mis canciones ni mi tapete ni mis fundas"</i>. La carpeta a la que
     * apuntaba era el temporal de una sesion que ya no existia, asi que
     * ademas iba a desaparecer con la limpieza del sistema.
     *
     * <h2>Por que este es el sitio y esta es la regla</h2>
     *
     * <p>Las dos situaciones se distinguen solas, sin heuristicas: <b>un
     * paquete de verdad SIEMPRE trae {@code -Dneo.dataDir}</b> (lo pone
     * {@code jpackage}, ver las notas de diseño), y el arbol de desarrollo <b>nunca
     * lo trae</b>. Asi que estar aqui —sin bandera— y encontrarse un perfil
     * nuestro solo puede significar una cosa: lo dejo tirado una prueba.
     *
     * <p>⚠️ Se borra <b>solo si lo escribimos nosotros</b>, y por eso existe
     * {@link #OURS}. Forge admite que el usuario ponga su propio
     * {@code forge.profile.properties} a mano para llevarse los datos a otro
     * disco (trae hasta un {@code .example} al lado): borrarle ese seria
     * cambiarle de sitio los datos sin avisar, o sea el mismo fallo al reves.
     *
     * <p>Y se dice por consola en voz alta. Un arreglo silencioso aqui deja al
     * jugador sin saber por que sus cosas volvieron: lo que hay que poder es
     * atar el sintoma a la causa.
     */
    private static void dropStrayProfile() {
        try {
            final File target = new File(assetsDir(), "forge.profile.properties");
            if (!target.isFile()) {
                return;
            }
            boolean ours = false;
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new FileInputStream(target),
                            java.nio.charset.StandardCharsets.ISO_8859_1))) {
                String line;
                // Solo las lineas de cabecera: una ruta que casualmente
                // contuviera la palabra no puede contar como firma.
                while ((line = r.readLine()) != null && line.startsWith("#")) {
                    if (line.contains(OURS)) {
                        ours = true;
                        break;
                    }
                }
            }
            if (!ours) {
                return;
            }
            System.out.println("[portable] habia un perfil de una prueba portable en "
                    + target + " y estaba mandando TUS datos a otra carpeta."
                    + " Se quita: los datos vuelven a la carpeta de siempre.");
            if (!target.delete()) {
                System.err.println("[portable] no se ha podido borrar " + target
                        + " — borralo a mano o los datos seguiran yendo a otro sitio.");
            }
        } catch (final IOException | RuntimeException e) {
            System.err.println("[portable] no se ha podido revisar el perfil: " + e);
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
     *
     * <p>⚠️ <b>Y se escribe con {@code store(OutputStream)}, NUNCA con un
     * {@code Writer}.</b> Forge lo lee con {@code Properties.load(InputStream)}
     * ({@code ForgeProfileProperties:64}), y esa forma decodifica <b>siempre en
     * ISO-8859-1</b>, diga lo que diga {@code file.encoding}. Su pareja
     * simetrica es {@code store(OutputStream)}, que escribe lo que no sea
     * Latin-1 escapado a su codigo Unicode, y por tanto vuelve intacto. Con un
     * {@code OutputStreamWriter} en UTF-8 se escriben los bytes crudos y Forge
     * los lee como Latin-1: a quien tenga <b>un solo caracter no ASCII en la
     * ruta de instalacion</b> —un acento, una enye, la z con punto de un
     * usuario polaco llamado {@code Uzytkownik}— le sale una carpeta que no
     * existe y que ademas cuelga de {@code C:\Users\}, donde un usuario
     * normal no puede crear nada. O sea que <b>el juego no arranca</b>:
     * {@code cannot create profile directory}. Reportado el 01-09-2026 por
     * alguien que se bajo el zip.
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
            props.store(out,
                    "Lo escribe NeoForge en cada arranque. No hace falta tocarlo:"
                    + " si mueves la carpeta, se vuelve a calcular solo.");
        }
        verify(target, dir);
    }

    /**
     * Relee el fichero <b>por el mismo camino que Forge</b> y comprueba que
     * dice lo mismo que acabamos de escribir.
     *
     * <p>No sobra por ser obvio el arreglo de arriba: esto es un fichero que le
     * dejamos a OTRO programa, y el sintoma de que la codificacion no cuadre no
     * es una excepcion nuestra sino <b>que el juego no arranca</b>, en el
     * ordenador de otro, con un mensaje del motor que no menciona esta clase
     * para nada. Si no vuelve igual se borra —un perfil envenenado es peor que
     * ninguno— y nos quedamos sin modo portable: los datos iran a
     * {@code %APPDATA%\Forge}, que es peor que lo que se pedia pero se juega.
     */
    private static void verify(final File target, final File dir) throws IOException {
        final Properties back = new Properties();
        try (InputStream in = new FileInputStream(target)) {
            back.load(in);
        }
        final String read = back.getProperty("userDir");
        if (!dir.getPath().equals(read)) {
            target.delete();
            throw new IOException("el perfil no vuelve igual de lo escrito: "
                    + dir.getPath() + " -> " + read);
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
