package forge.neo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * El registro de la aplicacion, en un fichero.
 *
 * <p>Existe por una razon muy concreta: <b>arrancar el juego no puede abrir una
 * ventana negra</b>. Y sin embargo todo lo que escupe Forge al cargar — los
 * 33.666 scripts, las cartas sin edicion, los avisos de sets futuros — mas
 * nuestras propias trazas es <b>lo primero que se mira cuando algo va mal</b>.
 * Las dos cosas se arreglan a la vez volcandolo a un fichero.
 *
 * <p>Asi que {@code jugar.cmd} usa {@code javaw} (el lanzador SIN consola) y el
 * registro se lee aqui despues. {@code run.cmd} sigue con {@code java}, porque
 * ahi la salida por pantalla es justo lo que se va a buscar.
 *
 * <p>No sustituye a la salida: la <b>duplica</b>. Con consola se sigue viendo
 * todo en vivo Y ademas queda escrito.
 *
 * <p>Se guarda la ejecucion anterior. Un cuelgue se investiga <b>despues</b> de
 * volver a arrancar, y para entonces el fichero ya se habria sobrescrito.
 *
 * <p>⚠️ <b>La carpeta se calcula a mano y no con {@code ForgeConstants}</b>, que
 * es lo primero que uno intenta. Esa clase resuelve el directorio de assets en
 * su <b>inicializador estatico</b>, y para eso llama a
 * {@code GuiBase.getInterface().getAssetsDir()} — o sea que <b>tocarla antes de
 * registrar el {@code IGuiBase} revienta con
 * {@code ExceptionInInitializerError}</b>. Y el registro tiene que empezar
 * antes que nada, justamente para cazar los fallos del arranque. Una clase de
 * traza que impide arrancar es lo peor que puede pasar aqui.
 */
public final class NeoLog {

    private NeoLog() {
    }

    private static final String NAME = "neo.log";
    private static final String PREVIOUS = "neo-anterior.log";

    private static File file;

    /** Donde esta el registro. */
    public static File file() {
        return file;
    }

    /**
     * La carpeta donde va el registro.
     *
     * <p>La comparte {@link NeoLock}, que necesita un sitio donde dejar su
     * cerrojo <b>antes</b> de que exista {@code ForgeConstants} y por el mismo
     * motivo que explica el javadoc de esta clase. Mejor que las dos usen la
     * misma regla a que haya una tercera copia de ella por ahi.
     *
     * <p>No crea nada: solo dice donde es.
     */
    public static File dir() {
        return new File(userDir(), "neo");
    }

    /**
     * Empieza a escribir el registro. Se llama lo PRIMERO de todo.
     *
     * <p>Si algo falla aqui no pasa nada: se sigue sin registro. Un fichero de
     * traza que impide arrancar seria el peor cambio posible.
     */
    public static synchronized void start() {
        if (file != null) {
            return;
        }
        try {
            final File dir = dir();
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            final File target = new File(dir, NAME);
            if (target.isFile()) {
                final File previous = new File(dir, PREVIOUS);
                if (previous.isFile() && !previous.delete()) {
                    System.err.println("[log] no se ha podido borrar " + previous);
                }
                if (!target.renameTo(previous)) {
                    System.err.println("[log] no se ha podido rotar " + target);
                }
            }

            final OutputStream out = new FileOutputStream(target, true);
            System.setOut(tee(System.out, out));
            System.setErr(tee(System.err, out));
            file = target;
        } catch (final IOException | RuntimeException e) {
            System.err.println("[log] no se ha podido abrir el registro: " + e);
        }
    }

    /**
     * {@code %APPDATA%\Forge}, igual que el resto de datos de usuario.
     *
     * <p>Se calcula a mano por lo que dice el javadoc de arriba. Si no hubiera
     * {@code APPDATA} — no deberia pasar en Windows — se usa el directorio del
     * usuario, que siempre existe.
     */
    private static File userDir() {
        // En modo portable, dentro de la carpeta del juego. Se pregunta aqui y
        // no se deriva de ForgeConstants por lo que dice el javadoc de arriba:
        // tocar esa clase antes de registrar el IGuiBase revienta.
        final File portable = NeoPortable.dataDir();
        if (portable != null) {
            return portable;
        }
        final String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return new File(appData, "Forge");
        }
        return new File(System.getProperty("user.home", "."), ".forge");
    }

    /**
     * Un flujo que escribe en los dos sitios.
     *
     * <p>{@code autoFlush} a true y a proposito: lo que se busca en este
     * fichero es <b>la ultima linea antes del cuelgue</b>, y con el buffer
     * puesto esa linea es justo la que no llega a escribirse.
     */
    private static PrintStream tee(final PrintStream console, final OutputStream fileOut) {
        return new PrintStream(new OutputStream() {
            @Override
            public void write(final int b) throws IOException {
                console.write(b);
                fileOut.write(b);
            }

            @Override
            public void write(final byte[] b, final int off, final int len) throws IOException {
                console.write(b, off, len);
                fileOut.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                console.flush();
                fileOut.flush();
            }
        }, true, StandardCharsets.UTF_8);
    }
}
