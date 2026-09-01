package forge.neo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

/**
 * Que solo haya UN NeoForge abierto.
 *
 * <p><b>De donde sale.</b> El juego tarda entre 12 y 40 segundos en ensenyar
 * la primera pantalla, asi que quien lo abre por primera vez no sabe si ha
 * dado bien al doble clic y vuelve a darle. Cuatro doble clics son cuatro
 * procesos, cada uno pidiendo {@code -Xmx2g} y — esto es lo grave — <b>los
 * cuatro escribiendo en la misma carpeta de datos</b>. Las preferencias, los
 * mazos y la aventura los acaba escribiendo el que se cierre el ultimo, y los
 * otros tres se pierden sin que nadie se entere.
 *
 * <p>O sea que esto no es una comodidad: es lo que impide que la impaciencia
 * del primer arranque se lleve por delante los datos del jugador.
 *
 * <p><b>Por que un {@code FileLock} y no un fichero con el PID dentro.</b>
 * Porque el sistema operativo suelta el cerrojo cuando el proceso muere, pase
 * lo que pase — incluso si revienta o lo matan desde el administrador de
 * tareas. Un fichero centinela habria que borrarlo al salir, y el dia que el
 * juego se cerrara mal se quedaria ahi para siempre: el juego no volveria a
 * abrir nunca y no habria forma de adivinar por que.
 *
 * <p><b>Y que pasa al segundo doble clic.</b> No se descarta en silencio: el
 * segundo proceso deja una senyal, y el primero <b>trae su ventana al
 * frente</b> (ver {@link #watchForRaise}). Asi el gesto tiene respuesta —
 * abrirlo cuando ya estaba abierto ensenya el juego, que es lo que se queria—
 * en vez de no hacer nada, que es indistinguible de estar roto.
 *
 * <p><b>El caso que casi se cuela.</b> Al elegir un idioma distinto en la
 * primera pantalla, el juego <b>se relanza a si mismo</b>
 * ({@code NeoApp.reopenIn}): arranca el hijo y luego se cierra el padre. Con
 * un cerrojo ingenuo el hijo lo encontraria cogido y se cerraria — el juego
 * no volveria a abrirse jamas, y justo en el primer arranque. Por eso hay dos
 * defensas: {@link #release()} se llama antes de lanzar al hijo, y ademas
 * {@link #acquire()} <b>reintenta unos segundos</b> antes de rendirse.
 */
public final class NeoLock {

    private NeoLock() {
    }

    /** Cuanto se insiste antes de dar por hecho que ya hay otro abierto. */
    private static final long ESPERA_MS = 4000L;

    /** Cada cuanto se mira si el otro ya ha soltado. */
    private static final long REINTENTO_MS = 150L;

    /** Cada cuanto mira el primero si le estan llamando. */
    private static final long VIGILANCIA_MS = 800L;

    private static final String FICHERO_CERROJO = "neo.lock";
    private static final String FICHERO_LLAMADA = "neo.raise";

    /**
     * El canal y el cerrojo se guardan en estaticos <b>a proposito</b>: el
     * cerrojo vive mientras viva el canal, y si el recolector se llevara el
     * {@code FileChannel} el cerrojo se soltaria solo a mitad de partida.
     */
    private static FileChannel canal;
    private static FileLock cerrojo;

    /** Si esta ejecucion llego a coger el cerrojo. */
    private static boolean mio;

    /**
     * Coge el cerrojo. {@code true} si somos el unico NeoForge.
     *
     * <p>Si devuelve {@code false} ya hay otro abierto (o abriendose) y este
     * proceso <b>tiene que salir</b>. Antes de rendirse deja la senyal para
     * que el que esta vivo se ponga delante.
     *
     * <p>Si algo falla — no se puede crear el fichero, el disco es de solo
     * lectura, un sistema de ficheros raro sin cerrojos — devuelve
     * {@code true} y se sigue. Un juego que no arranca por el fichero con el
     * que se cuentan las instancias seria muchisimo peor que dos ventanas.
     */
    public static synchronized boolean acquire() {
        final File dir = NeoLog.dir();
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return true;
            }
            final File objetivo = new File(dir, FICHERO_CERROJO);
            canal = new RandomAccessFile(objetivo, "rw").getChannel();

            final long limite = System.currentTimeMillis() + ESPERA_MS;
            boolean avisado = false;
            while (true) {
                try {
                    cerrojo = canal.tryLock();
                } catch (final OverlappingFileLockException e) {
                    // Otro hilo de ESTE proceso ya lo tiene: somos nosotros.
                    cerrojo = null;
                }
                if (cerrojo != null) {
                    mio = true;
                    return true;
                }
                if (!avisado) {
                    // Se llama al que esta vivo cuanto antes, no al final de
                    // los reintentos: quien acaba de hacer doble clic quiere
                    // ver la ventana AHORA, no dentro de cuatro segundos.
                    llamar(dir);
                    avisado = true;
                }
                if (System.currentTimeMillis() >= limite) {
                    System.out.println("[cerrojo] ya hay otro NeoForge abierto:"
                            + " se le pide que se ponga delante y salimos");
                    cerrar();
                    return false;
                }
                Thread.sleep(REINTENTO_MS);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return true;
        } catch (final IOException | RuntimeException e) {
            System.err.println("[cerrojo] no se ha podido usar " + dir + ": " + e);
            return true;
        }
    }

    /**
     * Suelta el cerrojo antes de tiempo.
     *
     * <p>Lo unico que lo necesita es el relanzado por cambio de idioma: el
     * proceso hijo arranca mientras el padre sigue vivo, asi que el padre
     * tiene que soltar ANTES de lanzarlo. Sin esto el hijo se encontraria el
     * cerrojo cogido durante sus cuatro segundos de reintentos, que es tiempo
     * de arranque tirado a la basura en el peor momento posible.
     */
    public static synchronized void release() {
        if (!mio) {
            return;
        }
        cerrar();
        mio = false;
    }

    private static void cerrar() {
        try {
            if (cerrojo != null) {
                cerrojo.release();
            }
        } catch (final IOException | RuntimeException e) {
            System.err.println("[cerrojo] no se ha podido soltar: " + e);
        }
        try {
            if (canal != null) {
                canal.close();
            }
        } catch (final IOException | RuntimeException e) {
            System.err.println("[cerrojo] no se ha podido cerrar el canal: " + e);
        }
        cerrojo = null;
        canal = null;
    }

    /** Deja la senyal de "ponte delante" para el que este vivo. */
    private static void llamar(final File dir) {
        try {
            final File senyal = new File(dir, FICHERO_LLAMADA);
            if (!senyal.isFile() && !senyal.createNewFile()) {
                return;
            }
            // Si ya existia — el otro todavia no la ha visto — se le cambia la
            // fecha, que es lo que mira el vigilante.
            if (!senyal.setLastModified(System.currentTimeMillis())) {
                System.err.println("[cerrojo] no se ha podido marcar " + senyal);
            }
        } catch (final IOException | RuntimeException e) {
            System.err.println("[cerrojo] no se ha podido avisar al otro: " + e);
        }
    }

    /**
     * Empieza a vigilar si otra copia pide que nos pongamos delante.
     *
     * <p>Se mira un fichero una vez por segundo. Es a proposito mas tonto que
     * un {@code WatchService}: la carpeta donde vive tambien recibe el
     * registro y las preferencias, o sea que un vigilante de eventos se
     * despertaria constantemente por cosas que no le importan. Un
     * {@code isFile()} al segundo no se nota ni con lupa.
     *
     * <p>La accion se ejecuta en el hilo del vigilante: quien la pase tiene
     * que saltar al hilo de interfaz por su cuenta.
     */
    public static void watchForRaise(final Runnable alFrente) {
        if (!mio || alFrente == null) {
            return;
        }
        final File senyal = new File(NeoLog.dir(), FICHERO_LLAMADA);
        // Una llamada anterior que se quedara sin atender (el juego se cerro
        // mal) haria que la ventana saltara al frente nada mas abrir, sin que
        // nadie lo hubiera pedido. Se limpia antes de empezar a mirar.
        borrar(senyal);

        final Thread vigilante = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(VIGILANCIA_MS);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (senyal.isFile()) {
                    borrar(senyal);
                    System.out.println("[cerrojo] han vuelto a abrir el juego:"
                            + " me pongo delante");
                    alFrente.run();
                }
            }
        }, "neo-cerrojo");
        // Demonio: no puede ser lo que mantenga viva la aplicacion al cerrarla.
        vigilante.setDaemon(true);
        vigilante.start();
    }

    private static void borrar(final File senyal) {
        if (senyal.isFile() && !senyal.delete()) {
            System.err.println("[cerrojo] no se ha podido borrar " + senyal);
        }
    }
}
