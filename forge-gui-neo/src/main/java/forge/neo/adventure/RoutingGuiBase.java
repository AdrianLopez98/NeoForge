package forge.neo.adventure;

import com.badlogic.gdx.Gdx;
import forge.gui.interfaces.IGuiBase;
import forge.neo.platform.FxUiDispatcher;
import forge.neo.platform.NeoGuiBase;

import java.security.CodeSource;
import java.util.concurrent.CountDownLatch;

/**
 * La plataforma del motor MIENTRAS esta abierta una pantalla de NeoForge dentro
 * del Adventure: la de NeoForge, pero con los encargos de hilo repartidos.
 *
 * <p>El motor solo admite UNA {@code IGuiBase}, y el Adventure sigue vivo
 * detras: su ventana sigue encargando tareas por {@code FThreads} (cargar una
 * fuente, una textura). Esas tareas tocan OpenGL y solo pueden correr en el
 * hilo de libGDX; mandadas al de JavaFX la JVM aborta ("No context is
 * current"), que es lo que paso en la primera autoprueba.
 *
 * <p>Se decide por <b>de donde viene la tarea</b>: si su clase se cargo del jar
 * del Adventure (libGDX, forge-gui-mobile) va a su hilo; lo demas —el motor y
 * NeoForge— al nuestro. Y {@code isGuiThread()} dice que SI en el hilo de
 * libGDX: su codigo lo comprueba antes de cargar recursos, y con un no la
 * excepcion tumbaba su bucle (segunda autoprueba).
 *
 * <p><b>Es una {@code NeoGuiBase} de verdad, no un proxy.</b> La primera version
 * lo era, y la mesa pregunta {@code instanceof NeoGuiBase} antes de pintar los
 * botones de cada pregunta del motor: con el proxy no los pintaba nunca y la
 * partida se quedaba parada en "play or draw" (probado jugando, 17-09-2026).
 * La autoprueba no lo vio porque ahi contesta la IA, sin botones.
 */
final class RoutingGuiBase extends NeoGuiBase {

    private final CodeSource adventureJar;
    private final Thread gdxThread;

    private RoutingGuiBase(final IGuiBase adventure, final Thread gdxThread) {
        super(new FxUiDispatcher());
        this.adventureJar = adventure.getClass().getProtectionDomain().getCodeSource();
        this.gdxThread = gdxThread;
    }

    static IGuiBase create(final IGuiBase adventure, final Thread gdxThread) {
        return new RoutingGuiBase(adventure, gdxThread);
    }

    @Override
    public boolean isGuiThread() {
        return Thread.currentThread() == gdxThread || super.isGuiThread();
    }

    @Override
    public void invokeInEdtNow(final Runnable r) {
        if (!toAdventure(r, false)) {
            super.invokeInEdtNow(r);
        }
    }

    @Override
    public void invokeInEdtLater(final Runnable r) {
        if (!toAdventure(r, false)) {
            super.invokeInEdtLater(r);
        }
    }

    @Override
    public void invokeInEdtAndWait(final Runnable r) {
        if (!toAdventure(r, true)) {
            super.invokeInEdtAndWait(r);
        }
    }

    /** Si la tarea es del Adventure, la manda a su hilo y devuelve true. */
    private boolean toAdventure(final Runnable r, final boolean wait) {
        if (r == null || !fromAdventure(r)) {
            return false;
        }
        if (Thread.currentThread() == gdxThread) {
            r.run();
        } else if (wait) {
            final CountDownLatch done = new CountDownLatch(1);
            Gdx.app.postRunnable(() -> {
                try {
                    r.run();
                } finally {
                    done.countDown();
                }
            });
            try {
                done.await();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            Gdx.app.postRunnable(r);
        }
        return true;
    }

    private boolean fromAdventure(final Runnable r) {
        final CodeSource cs = r.getClass().getProtectionDomain().getCodeSource();
        return cs != null && adventureJar != null && cs.getLocation().equals(adventureJar.getLocation());
    }
}
