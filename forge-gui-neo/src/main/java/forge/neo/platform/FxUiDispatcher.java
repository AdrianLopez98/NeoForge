package forge.neo.platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javafx.application.Platform;

/**
 * {@link UiDispatcher} sobre el hilo de aplicacion de JavaFX.
 *
 * <p>Sustituye a {@link ConsoleUiDispatcher} en cuanto hay ventana. Es la unica
 * clase que cambia entre la fase 1 y la fase 2: el resto del codigo habla con
 * la interfaz {@code UiDispatcher} y no se entera.
 *
 * <p>Recordatorio de las reglas (las notas de diseño seccion 5):
 * <ul>
 *   <li>El motor corre en su propio hilo, nunca en el de JavaFX.</li>
 *   <li>{@link #runAndWait} lo llama el motor para pintar y esperar. Nunca debe
 *       llamarlo el propio hilo de JavaFX esperando al motor: eso es un cuelgue.</li>
 * </ul>
 */
public final class FxUiDispatcher implements UiDispatcher {

    @Override
    public boolean isUiThread() {
        return Platform.isFxApplicationThread();
    }

    @Override
    public void runLater(final Runnable task) {
        if (task == null) {
            return;
        }
        if (isUiThread()) {
            safe(task);
        } else {
            try {
                Platform.runLater(() -> safe(task));
            } catch (final IllegalStateException ignored) {
                // El toolkit aun no ha arrancado o ya se cerro.
            }
        }
    }

    @Override
    public void runAndWait(final Runnable task) {
        if (task == null) {
            return;
        }
        if (isUiThread()) {
            safe(task);
            return;
        }
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        try {
            Platform.runLater(() -> {
                try {
                    task.run();
                } catch (final RuntimeException e) {
                    failure.set(e);
                } catch (final Exception e) {
                    failure.set(new RuntimeException(e));
                } finally {
                    done.countDown();
                }
            });
        } catch (final IllegalStateException e) {
            return; // toolkit no disponible
        }
        try {
            done.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (failure.get() != null) {
            throw failure.get();
        }
    }

    private static void safe(final Runnable task) {
        try {
            task.run();
        } catch (final Exception e) {
            System.err.println("[neo-fx] excepcion en el hilo de UI: " + e);
            e.printStackTrace();
        }
    }
}
