package forge.neo;

import java.lang.management.ManagementFactory;

import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.util.Duration;

/**
 * <b>Medir lo que cuesta la interfaz</b>, con numeros y no a ojo:
 * {@code -Dneo.fx.bench=true}. Cada 5 s escribe una linea
 * {@code [bench] fps=.. heap=.. cpu=.. nodos=.. imagenes=..}:
 * <ul>
 *   <li><b>fps</b>: pulsos de JavaFX en esos 5 s. Con la pantalla a 60 Hz,
 *       60 es que pinta sin esfuerzo; si baja, algo le cuesta.</li>
 *   <li><b>heap</b>: la memoria de Java usada ahora (sin forzar GC), y la
 *       maxima de la sesion.</li>
 *   <li><b>cpu</b>: el proceso entero, en % de la maquina entera (todos los
 *       nucleos), como lo da {@code getProcessCpuLoad}.</li>
 *   <li><b>nodos</b>: cuantos elementos tiene la escena.</li>
 *   <li><b>imagenes</b>: cuantas cartas hay decodificadas en la cache y lo
 *       que ocupan ({@code CardImages.cachedBytes}).</li>
 * </ul>
 * Con {@code -Dneo.fx.bench.gcAt=N} (s) hace UN GC completo a esa altura y
 * escribe lo que queda vivo: es la memoria que de verdad retiene la partida.
 *
 * <p>Salio de una pregunta del autor (18-10-2026): <i>"quiero saber con datos
 * reales como pueden perjudicar a un crasheo y al rendimiento los efectos
 * nuevos"</i>. Sin la bandera no hace nada.
 */
final class FxBench {

    private FxBench() {
    }

    private static long frames;
    private static long maxUsed;

    static void start(final Scene scene) {
        if (!Boolean.getBoolean("neo.fx.bench")) {
            return;
        }
        final AnimationTimer counter = new AnimationTimer() {
            @Override
            public void handle(final long now) {
                frames++;
            }
        };
        counter.start();
        final long t0 = System.nanoTime();
        final Timeline every = new Timeline(new KeyFrame(Duration.seconds(5), e -> {
            final Runtime rt = Runtime.getRuntime();
            final long used = rt.totalMemory() - rt.freeMemory();
            maxUsed = Math.max(maxUsed, used);
            double cpu = -1;
            final java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                cpu = sun.getProcessCpuLoad() * 100;
            }
            System.out.printf(java.util.Locale.ROOT,
                    "[bench] t=%3.0fs fps=%5.1f heap=%4d MB (max %4d) cpu=%5.1f%% nodos=%5d imagenes=%d (%d MB)%n",
                    (System.nanoTime() - t0) / 1e9, frames / 5.0, used >> 20, maxUsed >> 20, cpu,
                    count(scene.getRoot()), forge.neo.card.CardImages.cacheSize(),
                    forge.neo.card.CardImages.cachedBytes() >> 20);
            frames = 0;
        }));
        every.setCycleCount(Timeline.INDEFINITE);
        every.play();
        final long gcAt = Long.getLong("neo.fx.bench.gcAt", -1L);
        if (gcAt > 0) {
            final Timeline gc = new Timeline(new KeyFrame(Duration.seconds(gcAt), e -> {
                System.gc();
                final Runtime rt = Runtime.getRuntime();
                System.out.printf(java.util.Locale.ROOT, "[bench] tras GC: heap vivo=%d MB  imagenes=%d (%d MB)%n",
                        (rt.totalMemory() - rt.freeMemory()) >> 20, forge.neo.card.CardImages.cacheSize(),
                        forge.neo.card.CardImages.cachedBytes() >> 20);
            }));
            gc.play();
        }
    }

    private static int count(final Node n) {
        int c = 1;
        if (n instanceof Parent p) {
            for (final Node k : p.getChildrenUnmodifiable()) {
                c += count(k);
            }
        }
        return c;
    }
}
