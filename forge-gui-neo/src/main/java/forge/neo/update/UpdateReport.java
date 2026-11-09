package forge.neo.update;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * <b>El informe de un fallo al actualizar</b>, para que el jugador lo pegue en
 * Discord con un clic (Ana: <i>"que si les falla se sepa por que y nos lo
 * puedan pasar para que tu lo veas facil"</i>).
 *
 * <p>Lleva lo que hace falta para entender el fallo sin preguntar nada: las dos
 * versiones, Windows y Java, en que paso fallo, el problema y la cadena de
 * excepciones con las primeras lineas de cada una, si se podia escribir y
 * cuanto sitio habia, y el registro del cambiador si llego a correr. Se queda
 * ademas en {@code datos/neo/update-report.txt}.
 *
 * <p><b>Sin el nombre de usuario</b>: la carpeta de usuario sale como
 * {@code %USERPROFILE%} y el nombre suelto como {@code <user>}. Va a un canal
 * publico.
 */
public final class UpdateReport {

    private UpdateReport() {
    }

    static final String FILE = "update-report.txt";

    /**
     * El informe.
     *
     * @param step     el paso en que fallo (descargar, descomprimir, cambiar...)
     * @param target   la version a la que se iba, si se sabe
     * @param error    lo que paso, o null
     * @param result   la linea del cambiador ({@code failed\t...}), o null
     * @param log      el registro del cambiador, o null
     */
    public static String build(final String step, final String target, final Throwable error,
                               final String result, final String log) {
        final StringBuilder b = new StringBuilder();
        b.append("Neo Forge update report\n");
        b.append("date: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        b.append("from: ").append(safe(forge.neo.NeoVersion::neoVersion))
                .append("  to: ").append(target == null ? "?" : target).append('\n');
        b.append("windows: ").append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(" (").append(System.getProperty("os.arch")).append(")  java ")
                .append(System.getProperty("java.version")).append('\n');
        final File root = SelfUpdate.installRoot();
        if (root != null) {
            b.append("install: ").append(root.getAbsolutePath())
                    .append("  free: ").append(root.getUsableSpace() >> 20).append(" MB")
                    .append("  writable: ").append(root.canWrite()).append('\n');
        } else {
            b.append("install: (not the Windows package)\n");
        }
        b.append("step: ").append(step).append('\n');
        if (result != null) {
            b.append("result: ").append(result.replace('\t', ' ')).append('\n');
        }
        if (error != null) {
            final SelfUpdate.UpdateException u = SelfUpdate.classify(error);
            b.append("problem: ").append(u.problem).append('\n');
            Throwable t = error;
            for (int depth = 0; t != null && depth < 4; depth++, t = t.getCause()) {
                b.append(depth == 0 ? "error: " : "caused by: ").append(t).append('\n');
                final StackTraceElement[] st = t.getStackTrace();
                for (int i = 0; i < Math.min(6, st.length); i++) {
                    b.append("    at ").append(st[i]).append('\n');
                }
            }
        }
        if (log != null && !log.isBlank()) {
            b.append("--- updater log ---\n").append(tail(log, 60)).append('\n');
        }
        return redact(b.toString());
    }

    /** Lo deja en {@code datos/neo/update-report.txt}. Nunca lanza. */
    public static File save(final String report) {
        try {
            final File dir = forge.neo.NeoLog.dir();
            Files.createDirectories(dir.toPath());
            final File f = new File(dir, FILE);
            Files.writeString(f.toPath(), report, StandardCharsets.UTF_8);
            return f;
        } catch (final Throwable e) {
            return null;
        }
    }

    /** El registro del cambiador y lo que escribio su lanzador, si estan. Nunca lanza. */
    public static String swapperLog(final File root) {
        if (root == null) {
            return null;
        }
        final Path work = root.toPath().resolve(SelfUpdate.WORK_DIR);
        final StringBuilder b = new StringBuilder();
        for (final String name : List.of("swapper.log", "swapper.out")) {
            try {
                final Path p = work.resolve(name);
                if (Files.isRegularFile(p) && Files.size(p) > 0) {
                    b.append("[").append(name).append("]\n")
                            .append(new String(Files.readAllBytes(p), StandardCharsets.UTF_8)).append('\n');
                }
            } catch (final IOException | RuntimeException e) {
                b.append("[").append(name).append("] unreadable: ").append(e).append('\n');
            }
        }
        return b.length() == 0 ? null : b.toString();
    }

    /** Sin el usuario de Windows: va a un canal publico. */
    static String redact(final String s) {
        String out = s;
        final String home = System.getProperty("user.home");
        if (home != null && home.length() > 3) {
            out = replaceIgnoreCase(out, home, "%USERPROFILE%");
        }
        final String user = System.getProperty("user.name");
        if (user != null && user.length() >= 3) {
            out = replaceIgnoreCase(out, user, "<user>");
        }
        return out;
    }

    private static String replaceIgnoreCase(final String s, final String what, final String with) {
        final String lower = s.toLowerCase(Locale.ROOT);
        final String w = what.toLowerCase(Locale.ROOT);
        final StringBuilder b = new StringBuilder();
        int from = 0;
        int i;
        while ((i = lower.indexOf(w, from)) >= 0) {
            b.append(s, from, i).append(with);
            from = i + what.length();
        }
        return b.append(s.substring(from)).toString();
    }

    private static String tail(final String s, final int lines) {
        final String[] all = s.split("\r?\n");
        if (all.length <= lines) {
            return s.strip();
        }
        return "(...)\n" + String.join("\n", java.util.Arrays.copyOfRange(all, all.length - lines, all.length));
    }

    private static String safe(final java.util.function.Supplier<String> s) {
        try {
            final String v = s.get();
            return v == null ? "?" : v;
        } catch (final Throwable e) {
            return "?";
        }
    }
}
