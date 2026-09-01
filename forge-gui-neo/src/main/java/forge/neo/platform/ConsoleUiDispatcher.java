package forge.neo.platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link UiDispatcher} de la fase 1: un unico hilo dedicado que hace de "EDT".
 *
 * <p>No pinta nada, pero se comporta igual que el hilo de JavaFX de cara al
 * motor. Asi la fase 1 valida el modelo de hilos de verdad, y la fase 2 solo
 * tiene que cambiar esta clase por una que use {@code Platform.runLater}.
 */
public final class ConsoleUiDispatcher implements UiDispatcher {

    private static final String THREAD_NAME = "neo-ui";

    private final ExecutorService ui = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, THREAD_NAME);
        t.setDaemon(true);
        return t;
    });

    public ConsoleUiDispatcher() {
        // Arrancar el hilo ya, para que exista antes de la primera consulta.
        ui.execute(() -> { });
    }

    @Override
    public boolean isUiThread() {
        return THREAD_NAME.equals(Thread.currentThread().getName());
    }

    @Override
    public void runLater(final Runnable task) {
        if (task == null) {
            return;
        }
        try {
            ui.execute(wrap(task));
        } catch (final Exception ignored) {
            // dispatcher cerrado
        }
    }

    @Override
    public void runAndWait(final Runnable task) {
        if (task == null) {
            return;
        }
        if (isUiThread()) {
            task.run();
            return;
        }
        final CountDownLatch done = new CountDownLatch(1);
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        try {
            ui.execute(() -> {
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
        } catch (final Exception e) {
            return; // dispatcher cerrado
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

    private static Runnable wrap(final Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (final Exception e) {
                System.err.println("[neo-ui] excepcion: " + e);
                e.printStackTrace();
            }
        };
    }

    @Override
    public void shutdown() {
        ui.shutdownNow();
    }
}
