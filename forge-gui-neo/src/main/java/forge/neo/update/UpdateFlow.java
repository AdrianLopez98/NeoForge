package forge.neo.update;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.neo.NeoSettings;
import forge.neo.NeoText;
import forge.neo.NeoVersion;
import javafx.application.Platform;

/**
 * <b>El estado de "Actualizar ahora"</b> para el menu ({@link SelfUpdate} hace
 * el trabajo). Vive mas que el menu, que se monta de nuevo cada vez que se
 * vuelve a el: la descarga sigue mientras juegas y el menu que se ensenye
 * despues la recoge donde vaya. Solo hilo de JavaFX salvo los hilos que lanza.
 *
 * <p><b>Todos los fallos acaban en una frase que se entiende</b> (Ana: "algo ha
 * salido mal, tu carpeta de datos esta intacta, descargalo de itch.io"): la causa
 * en palabras ({@link #why}) y detras siempre lo mismo, que no se ha cambiado
 * nada y que esta itch.io. El detalle tecnico va al registro, no a la pantalla.
 *
 * <p>Escucha UNO, el ultimo menu ({@link #listen}), igual que
 * {@link NeoUpdate#whenKnown}: el anterior ya no se ve, y no puede quedarse
 * agarrado (principio 11).
 */
public final class UpdateFlow {

    private UpdateFlow() {
    }

    public enum State { IDLE, WORKING, READY, FAILED, DONE }

    /** Cuanto se espera a que el cambiador diga que esta vivo antes de rendirse. */
    static final long SWAPPER_START_MS = 30_000;
    /** Si el juego no ha terminado de cerrarse en esto, se corta (el cambiador espera). */
    static final long HALT_AFTER_MS = 20_000;

    private static State state = State.IDLE;
    private static String text = "";
    private static double fraction = -1;
    private static String version;
    private static Runnable listener;
    private static AtomicBoolean cancel;
    private static boolean resultRead;
    private static boolean restarting;
    /** El informe del ultimo fallo, para "Copiar detalles" (ver {@link UpdateReport}). */
    private static String details;
    /** El paso en que va, para el informe. */
    private static volatile String step = "check";

    /** Si se puede ofrecer: Windows, el paquete del {@code .exe} y encendido en Ajustes. */
    public static boolean available() {
        try {
            return NeoSettings.selfUpdate() && (SelfUpdate.supported() || Boolean.getBoolean("neo.selfupdate.fake"));
        } catch (final Throwable e) {
            return false;
        }
    }

    public static State state() {
        return state;
    }

    public static String text() {
        return text;
    }

    public static double fraction() {
        return fraction;
    }

    public static String version() {
        return version;
    }

    /** El informe del ultimo fallo, o null. */
    public static String details() {
        return details;
    }

    /** Se esta lanzando el cambiador: el boton de reiniciar no se ensenya. */
    public static boolean isRestarting() {
        return restarting;
    }

    public static void listen(final Runnable r) {
        listener = r;
    }

    /**
     * Al arrancar, en el menu: si se acaba de cambiar de version, se dice como
     * fue (y el cambiador se entera de que esta version ha arrancado); si hay
     * una version ya bajada y sin poner, se ofrece reiniciar. Nunca lanza.
     */
    public static void atStartup() {
        if (resultRead) {
            return;
        }
        resultRead = true;
        // -Dneo.selfupdate.fakeState=WORKING|READY|DONE|FAILED: para capturar
        // cada estado del aviso sin red ni instalacion.
        final String fake = System.getProperty("neo.selfupdate.fakeState");
        if (fake != null) {
            state = State.valueOf(fake);
            version = "1.0.99";
            fraction = .42;
            text = switch (state) {
                case WORKING -> NeoText.get("update.downloading", "1.0.99", 42);
                case READY -> NeoText.get("update.ready", "1.0.99");
                case DONE -> NeoText.get("update.done", NeoVersion.neoVersion());
                default -> NeoText.get("update.failed", why(SelfUpdate.Problem.NO_WRITE, 0));
            };
            if (state == State.FAILED) {
                details = UpdateReport.build("prueba", "1.0.99", null, "failed\tfake", null);
            }
            return;
        }
        final File root = SelfUpdate.installRoot();
        if (root == null) {
            return;
        }
        final SelfUpdate.Outcome last = SelfUpdate.afterStart(root);
        if (last != null) {
            System.err.println("[neo] actualizar: el ultimo cambio de version: " + last.result().replace('\t', ' '));
            if (last.ok()) {
                state = State.DONE;
                text = NeoText.get("update.done", NeoVersion.neoVersion());
            } else {
                state = State.FAILED;
                text = NeoText.get("update.rolledBack");
                report("swap", null, null, last.result(), last.log());
            }
            return;
        }
        final String ready = SelfUpdate.readyVersion(root);
        if (ready != null && ItchVersion.compare(ready, NeoVersion.neoVersion()) > 0 && available()) {
            state = State.READY;
            version = ready;
            text = NeoText.get("update.ready", ready);
        } else if (ready != null) {
            SelfUpdate.discard(root);
        } else if (Boolean.getBoolean("neo.selfupdate.autotest") && available()) {
            // Prueba de punta a punta desde el .exe (ver SelfUpdateCheck): pulsa solo.
            start("autotest");
        }
    }

    /** "Actualizar ahora": a GitHub, y si esta, bajar y preparar. */
    public static void start(final String offered) {
        if (state == State.WORKING || state == State.READY || restarting) {
            return;
        }
        final File root = SelfUpdate.installRoot();
        state = State.WORKING;
        fraction = -1;
        text = NeoText.get("update.checking");
        cancel = new AtomicBoolean();
        final AtomicBoolean myCancel = cancel;
        notifyMenu();
        if (Boolean.getBoolean("neo.selfupdate.fake")) {
            // Para capturar la barra sin red ni instalacion.
            set(State.WORKING, NeoText.get("update.downloading", offered, 42), .42, null);
            return;
        }
        if (root == null) {
            fail(SelfUpdate.Problem.OTHER, 0);
            return;
        }
        details = null;
        step = "check";
        final Thread t = new Thread(() -> {
            String target = offered;
            try {
                final SelfUpdate.Release r = SelfUpdate.fetchRelease();
                target = r.version();
                if (ItchVersion.compare(r.version(), NeoVersion.neoVersion()) <= 0) {
                    report(step, target, null, "github has " + r.version(), null);
                    fail(SelfUpdate.Problem.NOT_ON_GITHUB, 0);
                    return;
                }
                SelfUpdate.prepare(root, r, (step, f) -> progress(step, f, r.version()), myCancel);
                Platform.runLater(() -> {
                    set(State.READY, NeoText.get("update.ready", r.version()), 1, r.version());
                    if (Boolean.getBoolean("neo.selfupdate.autotest")) {
                        restart();
                    }
                });
            } catch (final java.util.concurrent.CancellationException e) {
                SelfUpdate.discard(root);
                Platform.runLater(() -> set(State.IDLE, "", -1, null));
            } catch (final Throwable e) {
                System.err.println("[neo] actualizar: " + e);
                report(step, target, e, null, null);
                SelfUpdate.discard(root);
                final SelfUpdate.UpdateException u = SelfUpdate.classify(e);
                fail(u.problem, u.neededMb);
            }
        }, "neo-self-update");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /** Cancelar la descarga en marcha. */
    public static void cancel() {
        if (cancel != null) {
            cancel.set(true);
        }
        if (Boolean.getBoolean("neo.selfupdate.fake")) {
            set(State.IDLE, "", -1, null);
        }
    }

    /** Volver al aviso normal tras un fallo o tras decir que se actualizo. */
    public static void reset() {
        if (state == State.FAILED || state == State.DONE) {
            set(State.IDLE, "", -1, null);
        }
    }

    /**
     * "Reiniciar para actualizar". Si hay otra ventana de Neo Forge abierta (la
     * Aventura), se pide cerrarla. Si no, se lanza el cambiador y <b>el juego
     * solo se cierra cuando el cambiador dice que esta vivo</b>: si no llega a
     * arrancar, el juego sigue abierto y lo dice.
     */
    public static void restart() {
        final File root = SelfUpdate.installRoot();
        if (root == null || state != State.READY || restarting) {
            return;
        }
        if (SelfUpdate.othersRunning(root)) {
            text = NeoText.get("update.closeOthers");
            notifyMenu();
            return;
        }
        restarting = true;
        text = NeoText.get("update.restarting");
        notifyMenu();
        final Thread t = new Thread(() -> {
            boolean ok;
            Throwable error = null;
            try {
                final Process p = SelfUpdate.launchSwapper(root);
                ok = SelfUpdate.awaitSwapper(root, p, SWAPPER_START_MS);
            } catch (final Throwable e) {
                System.err.println("[neo] actualizar: el cambiador no arranca: " + e);
                error = e;
                ok = false;
            }
            if (!ok) {
                // Antes de limpiar: lo que dijo el lanzador es justo lo que hace falta.
                report("start updater", version, error, "the updater did not report alive",
                        UpdateReport.swapperLog(root));
            }
            final boolean started = ok;
            Platform.runLater(() -> {
                restarting = false;
                if (!started) {
                    System.err.println("[neo] actualizar: el cambiador no ha dicho que esta vivo");
                    SelfUpdate.discard(root);
                    fail(SelfUpdate.Problem.START, 0);
                    return;
                }
                closeForUpdate();
            });
        }, "neo-self-update-restart");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Cerrar el juego para que el cambiador haga lo suyo. Por si algo no deja
     * terminar de cerrarse, un corte a los {@link #HALT_AFTER_MS}: el cambiador
     * espera a que el proceso desaparezca, y un juego medio cerrado le dejaria
     * esperando.
     */
    private static void closeForUpdate() {
        final Thread halt = new Thread(() -> {
            try {
                Thread.sleep(HALT_AFTER_MS);
            } catch (final InterruptedException e) {
                return;
            }
            System.err.println("[neo] actualizar: el juego no terminaba de cerrarse; se corta");
            Runtime.getRuntime().halt(0);
        }, "neo-self-update-halt");
        halt.setDaemon(true);
        halt.start();
        try {
            forge.neo.NeoLock.release();
        } catch (final RuntimeException e) {
            // el cambiador espera igual a que se cierre el proceso
        }
        Platform.exit();
    }

    /** Monta el informe del fallo, lo guarda y lo deja para "Copiar detalles". Nunca lanza. */
    private static void report(final String where, final String target, final Throwable error,
                               final String result, final String log) {
        try {
            final String r = UpdateReport.build(where, target, error, result, log);
            details = r;
            final File f = UpdateReport.save(r);
            System.err.println("[neo] actualizar: informe del fallo en " + f);
        } catch (final Throwable e) {
            System.err.println("[neo] actualizar: sin informe: " + e);
        }
    }

    private static void progress(final SelfUpdate.Step step, final double f, final String v) {
        UpdateFlow.step = step.name().toLowerCase(java.util.Locale.ROOT);
        final int pct = f < 0 ? -1 : (int) Math.round(Math.min(1, f) * 100);
        final String t = switch (step) {
            case FETCH -> NeoText.get("update.checking");
            case DOWNLOAD -> NeoText.get("update.downloading", v, Math.max(0, pct));
            case VERIFY -> NeoText.get("update.verifying");
            case UNPACK -> NeoText.get("update.unpacking", Math.max(0, pct));
            case PREPARE -> NeoText.get("update.preparing");
        };
        Platform.runLater(() -> {
            if (state == State.WORKING) {
                set(State.WORKING, t, f, null);
            }
        });
    }

    /** La causa, en palabras que se entienden. */
    static String why(final SelfUpdate.Problem problem, final long neededMb) {
        return switch (problem) {
            case NETWORK -> NeoText.get("update.why.network");
            case GITHUB_BUSY -> NeoText.get("update.why.githubBusy");
            case NOT_ON_GITHUB -> NeoText.get("update.why.notOnGithub");
            case SPACE -> NeoText.get("update.why.space",
                    neededMb > 0 ? String.format(java.util.Locale.ROOT, "%.1f", neededMb / 1024.0) : "1.2");
            case NO_WRITE -> NeoText.get("update.why.noWrite");
            case CORRUPT -> NeoText.get("update.why.corrupt");
            case START -> NeoText.get("update.why.start");
            case BAD_PACKAGE, OTHER -> NeoText.get("update.why.other");
        };
    }

    private static void fail(final SelfUpdate.Problem problem, final long neededMb) {
        final String msg = NeoText.get("update.failed", why(problem, neededMb));
        if (Platform.isFxApplicationThread()) {
            set(State.FAILED, msg, -1, null);
        } else {
            Platform.runLater(() -> set(State.FAILED, msg, -1, null));
        }
    }

    private static void set(final State s, final String t, final double f, final String v) {
        state = s;
        text = t;
        fraction = f;
        if (v != null) {
            version = v;
        }
        notifyMenu();
    }

    private static void notifyMenu() {
        final Runnable l = listener;
        if (l != null) {
            try {
                l.run();
            } catch (final RuntimeException e) {
                System.err.println("[neo] actualizar: " + e);
            }
        }
    }
}
