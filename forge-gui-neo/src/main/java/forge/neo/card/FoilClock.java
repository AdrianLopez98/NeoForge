package forge.neo.card;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javafx.animation.AnimationTimer;

/**
 * El reloj de las foil: UNO para todas las cartas de la pantalla.
 *
 * <p>Existe por rendimiento, no por elegancia. Un {@code Timeline} por carta
 * significa cuarenta relojes despertando cuarenta veces por segundo en una
 * mesa llena de foil; aqui hay un solo {@link AnimationTimer} que reparte la
 * misma fase a todas y <b>se apaga solo</b> en cuanto no queda ninguna
 * registrada. Sin foil en pantalla, esto no consume absolutamente nada.
 *
 * <p>Y el ritmo es <b>adaptativo</b>: el brillo tarda siete segundos en cruzar
 * la carta, asi que no hace falta refrescarlo a 60 Hz. Con pocas foil va a
 * ~30 Hz y con muchas baja a ~15 Hz, que en una banda difusa que se mueve
 * despacio no se distingue. Se degrada <b>a la vez para todas</b> a proposito:
 * dejar quietas la mitad de las cartas se ve como un fallo, ir todas un poco
 * mas lentas no se ve.
 */
public final class FoilClock {

    private FoilClock() {
    }

    /** Lo que tarda el brillo en dar una vuelta entera, en nanosegundos. */
    private static final long PERIOD_NS = 7_000_000_000L;

    /** Refresco normal (~30 Hz) y refresco con la mesa llena de foil (~15 Hz). */
    private static final long TICK_FAST_NS = 33_000_000L;
    private static final long TICK_SLOW_NS = 66_000_000L;

    /** A partir de cuantas foil en pantalla se baja el ritmo. */
    private static final int CROWD = 16;

    private static final Set<CardNode> LIVE = new LinkedHashSet<>();

    /**
     * Copia sobre la que se itera. Repartir la fase puede acabar tocando el
     * grafo de escena, y no hay que estar recorriendo el conjunto mientras
     * algo se da de baja.
     */
    private static final List<CardNode> SNAPSHOT = new ArrayList<>();

    private static long lastTick;

    private static final AnimationTimer CLOCK = new AnimationTimer() {
        @Override
        public void handle(final long now) {
            final long tick = LIVE.size() > CROWD ? TICK_SLOW_NS : TICK_FAST_NS;
            if (now - lastTick < tick) {
                return;
            }
            lastTick = now;

            double phase = (now % PERIOD_NS) / (double) PERIOD_NS;
            SNAPSHOT.clear();
            SNAPSHOT.addAll(LIVE);
            for (final CardNode node : SNAPSHOT) {
                node.tickFoil(phase);
            }
        }
    };

    private static boolean running;

    /** Da de alta una carta foil. Arranca el reloj si estaba parado. */
    static void register(final CardNode node) {
        if (!LIVE.add(node)) {
            return;
        }
        if (!running) {
            running = true;
            lastTick = 0;
            CLOCK.start();
        }
    }

    /** Da de baja una carta. Para el reloj si era la ultima. */
    static void unregister(final CardNode node) {
        if (!LIVE.remove(node)) {
            return;
        }
        if (LIVE.isEmpty() && running) {
            running = false;
            CLOCK.stop();
        }
    }

    /** Cuantas foil se estan animando ahora mismo (para medir). */
    public static int animatedCount() {
        return LIVE.size();
    }
}
