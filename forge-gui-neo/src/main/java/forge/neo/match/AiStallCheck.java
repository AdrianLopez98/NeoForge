package forge.neo.match;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code run.cmd aistallcheck}: el vigia de la IA atascada ({@link AiStallWatch}).
 *
 * <p>Un atasco de verdad de la IA no se puede provocar a voluntad, asi que se
 * finge: hilos con nombre de partida ({@code Game-...}) dando vueltas dentro de
 * ESTA clase, y el vigia contando esta clase como "la IA". Lo que se mira es
 * la regla, no la IA: que avise una sola vez por atasco, con la traza de donde
 * se queda, y que no avise de un hilo que espera (el humano pensando) ni de uno
 * que no es de partida.
 */
public final class AiStallCheck {

    private AiStallCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() throws InterruptedException {
        passed = 0;
        failed = 0;
        final String oldAi = AiStallWatch.aiPrefix;
        final long oldPoll = AiStallWatch.pollMs;
        AiStallWatch.aiPrefix = AiStallCheck.class.getName();
        AiStallWatch.pollMs = 200;
        System.setProperty("neo.aistall.seconds", "1");
        final List<String> reports = new CopyOnWriteArrayList<>();
        try {
            // 1. Un hilo de partida dando vueltas: avisa, una vez, con la traza.
            AiStallWatch.watch(() -> "turn 7, MAIN1, priority: Teval", reports::add);
            run("Game-stall", AiStallCheck::spin, 3000);
            check(reports.size() == 1, "un atasco de 3 s avisa una vez (" + reports.size() + ")");
            check(!reports.isEmpty() && reports.get(0).contains("spin"),
                    "con la traza de donde da vueltas");
            check(!reports.isEmpty() && reports.get(0).contains("priority: Teval"),
                    "y como iba la partida");

            // 2. Un hilo de partida esperando (el humano decidiendo): nada.
            reports.clear();
            run("Game-wait", AiStallCheck::waitAround, 2500);
            check(reports.isEmpty(), "esperar no es atascarse (" + reports.size() + ")");

            // 3. Un hilo que no es de partida: nada.
            reports.clear();
            run("Other-stall", AiStallCheck::spin, 2500);
            check(reports.isEmpty(), "un hilo que no es de partida no cuenta (" + reports.size() + ")");

            // 4. Con la partida acabada (stop), nada.
            reports.clear();
            AiStallWatch.stop();
            run("Game-after", AiStallCheck::spin, 2500);
            check(reports.isEmpty(), "tras acabar la partida no avisa (" + reports.size() + ")");
        } finally {
            AiStallWatch.stop();
            AiStallWatch.aiPrefix = oldAi;
            AiStallWatch.pollMs = oldPoll;
            System.clearProperty("neo.aistall.seconds");
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del vigia de la IA han fallado");
        }
    }

    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    private static void run(final String name, final Runnable body, final long ms) throws InterruptedException {
        RUNNING.set(true);
        final Thread t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
        Thread.sleep(ms);
        RUNNING.set(false);
        t.join(2000);
        // Una vuelta mas del vigia, para que vea que el hilo ya no esta.
        Thread.sleep(AiStallWatch.pollMs * 2);
    }

    /** "La IA" dando vueltas. */
    private static void spin() {
        long x = 0;
        while (RUNNING.get()) {
            x += System.nanoTime() % 7;
        }
        if (x == 42) {
            System.out.print("");
        }
    }

    /** "El humano" pensando: el hilo espera. */
    private static void waitAround() {
        while (RUNNING.get()) {
            try {
                Thread.sleep(50);
            } catch (final InterruptedException e) {
                return;
            }
        }
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
