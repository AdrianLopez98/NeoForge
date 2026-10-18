package forge.neo.ui;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * <b>La carta que espera a su golpe.</b> El motor mata la criatura al resolver
 * el hechizo, y el hechizo de los destellos ({@link ImpactLayer}) aun tiene que
 * volar hasta ella: sin esto, el efecto llegaba a un hueco vacio (Ana,
 * 18-10-2026: <i>"la criatura muere y desaparece antes de que llegue el
 * efecto"</i>). Mientras dura la espera, el fantasma de la carta que se va
 * ({@code BattlefieldPane.startGhost}) se queda entero, y luego se va como
 * siempre.
 *
 * <p>Solo en el hilo de interfaz. Unas pocas entradas que caducan solas.
 */
final class GhostHold {

    private GhostHold() {
    }

    /** Lo que se espera: el viaje mas largo (0,36 s) y que se vea el impacto. */
    static final long WAIT_MS = 800;

    private static final Map<Integer, Long> UNTIL = new HashMap<>();

    /** Esa carta acaba de ser objetivo de algo que va hacia ella. */
    static void hold(final int cardId) {
        final long now = System.nanoTime();
        for (final Iterator<Long> it = UNTIL.values().iterator(); it.hasNext();) {
            if (it.next() < now) {
                it.remove();
            }
        }
        UNTIL.put(cardId, now + WAIT_MS * 1_000_000L);
    }

    /** Cuanto le queda de espera a esa carta, en ms; 0 si ninguna. */
    static long remainingMs(final int cardId) {
        final Long until = UNTIL.get(cardId);
        return until == null ? 0 : Math.max(0, (until - System.nanoTime()) / 1_000_000L);
    }
}
