package forge.neo.platform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <b>Abrir los .frp con Neo Forge</b> en Windows (Discord, 03-11-2026:
 * <i>"imagine if .frp files could directly open on Neo Forge?"</i>).
 *
 * <p>jpackage solo registra extensiones con <b>instalador</b>, y Neo Forge es un
 * zip portable: la asociacion la escribe el propio juego, cuando el jugador lo
 * pide (Ajustes → Repeticiones), en el registro DEL USUARIO
 * ({@code HKCU\Software\Classes}, sin permisos de administrador) con
 * {@code reg.exe}. Nunca por su cuenta: tocar el registro sin que lo pidas no.
 *
 * <p>Apunta al {@code .exe} de AHORA ({@code jpackage.app-path}, que pone el
 * propio lanzador de jpackage): si se mueve la carpeta del juego hay que
 * volver a pulsarlo, y por eso {@link #isAssociated} compara la ruta. Sin
 * {@code .exe} (run.cmd, la portable con su .cmd) no se ofrece.
 *
 * <p>El {@code .exe} recibe la ruta como unico argumento: lo atiende
 * {@code NeoMain} (que arranca la ventana con ella) y, si ya hay un juego
 * abierto, {@code NeoLock.handOver}.
 */
public final class ReplayFileAssoc {

    private ReplayFileAssoc() {
    }

    private static final String CLASSES = "HKCU\\Software\\Classes\\";
    private static final String PROG_ID = "NeoForge.Replay";

    /** El .exe del juego, o null si no hay (o no es Windows). */
    public static File exe() {
        if (NeoOs.MAC || NeoOs.LINUX) {
            return null;
        }
        final String path = System.getProperty("jpackage.app-path");
        if (path == null || path.isBlank()) {
            return null;
        }
        final File f = new File(path);
        return f.isFile() ? f : null;
    }

    /** Si se puede ofrecer: Windows y lanzado desde el .exe. */
    public static boolean isAvailable() {
        return exe() != null;
    }

    /** Si los .frp ya se abren con ESTE .exe. */
    public static boolean isAssociated() {
        final File exe = exe();
        if (exe == null) {
            return false;
        }
        final String out = run(List.of("reg", "query", CLASSES + PROG_ID + "\\shell\\open\\command", "/ve"));
        final String dot = run(List.of("reg", "query", CLASSES + ".frp", "/ve"));
        return out != null && out.toLowerCase().contains(exe.getAbsolutePath().toLowerCase())
                && dot != null && dot.contains(PROG_ID);
    }

    /** Escribe la asociacion. @return si ha ido bien */
    public static boolean associate(final String description) {
        final File exe = exe();
        if (exe == null) {
            return false;
        }
        final String path = exe.getAbsolutePath();
        final List<List<String>> cmds = new ArrayList<>();
        cmds.add(List.of("reg", "add", CLASSES + ".frp", "/ve", "/d", PROG_ID, "/f"));
        cmds.add(List.of("reg", "add", CLASSES + PROG_ID, "/ve", "/d", description, "/f"));
        cmds.add(List.of("reg", "add", CLASSES + PROG_ID + "\\DefaultIcon", "/ve", "/d", q(path) + ",0", "/f"));
        cmds.add(List.of("reg", "add", CLASSES + PROG_ID + "\\shell\\open\\command", "/ve",
                "/d", q(path) + " " + q("%1"), "/f"));
        for (final List<String> c : cmds) {
            if (run(c) == null) {
                return false;
            }
        }
        return isAssociated();
    }

    /**
     * Un valor entre comillas para reg.exe: Java pone el argumento entre
     * comillas si lleva espacios pero NO escapa las de dentro, y reg las quiere
     * escapadas con barra. Asi el registro guarda "C:\...\NeoForge.exe" "%1".
     */
    private static String q(final String value) {
        return "\\\"" + value + "\\\"";
    }

    /** La quita (solo lo nuestro: .frp solo si apunta a nosotros). @return si ha ido bien */
    public static boolean remove() {
        run(List.of("reg", "delete", CLASSES + PROG_ID, "/f"));
        final String dot = run(List.of("reg", "query", CLASSES + ".frp", "/ve"));
        if (dot != null && dot.contains(PROG_ID)) {
            run(List.of("reg", "delete", CLASSES + ".frp", "/f"));
        }
        return !isAssociated();
    }

    /** Ejecuta reg.exe y devuelve su salida, o null si falla. */
    private static String run(final List<String> cmd) {
        try {
            final Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            final String out = new String(p.getInputStream().readAllBytes());
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? out : null;
        } catch (final java.io.IOException | RuntimeException e) {
            System.err.println("[neo] reg.exe ha fallado: " + e);
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
