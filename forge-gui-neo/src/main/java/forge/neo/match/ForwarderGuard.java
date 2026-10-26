package forge.neo.match;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import forge.gamemodes.match.input.InputQueue;
import forge.gui.GuiBase;
import forge.gui.control.GameEventForwarder;

/**
 * Que un {@link GameEventForwarder} solo se vacie en el hilo de la partida.
 *
 * <p><b>De donde sale esto.</b> 26-10-2026: {@code questcheck} se quedaba
 * colgado (300 s y matado) unas veces si y otras no, en paralelo y solo. Al
 * acabar una partida local salia, en el hilo de interfaz:
 *
 * <pre>
 *   ConcurrentModificationException
 *   HashMap$HashIterator.remove
 *   DeltaSyncManager.collectDeltas:151           &lt;-- poda de registeredByKey
 *   ProtocolGuiGame.handleGameEvents
 *   GameEventForwarder.flush / update
 *   InputQueue.updateObservers
 *   RecordActionsMacroSystem.stateChanged / cancelCurrentMacro
 *   FControlGameEventHandler$1.run                &lt;-- el bloque gameFinished
 * </pre>
 *
 * <p><b>Por que una partida local pasa por codigo de red.</b> Son las
 * <b>repeticiones</b> (Forge #12266): {@code HostedMatch} abre en cada partida
 * local un {@code ReplaySession}, que es un espectador invisible
 * ({@code RecordingGuiGame extends ProtocolGuiGame}) alimentado como un cliente
 * remoto — delta sync incluido — y cuyo {@code GameEventForwarder} se cuelga
 * como observador de la {@link InputQueue} del humano. Nada nuestro lo engancha.
 *
 * <p><b>La carrera.</b> El propio Forge escribe que ese forwarder se vacia
 * <i>solo en el hilo de la partida</i> ("all delta collection runs on the game
 * thread"). Pero desde #12288 (macros en red) {@code cancelCurrentMacro} avisa a
 * los observadores de la cola, y {@code FControlGameEventHandler} lo llama al
 * acabar la partida <b>desde el hilo de interfaz</b>. Justo a la vez, el hilo de
 * la partida hace {@code replay.finish()}, que vacia el mismo forwarder. Dos
 * hilos dentro del mismo {@code DeltaSyncManager}: la poda de
 * {@code registeredByKey} revienta (el reintento de Forge solo cubre el
 * recorrido, no la poda).
 *
 * <p><b>Por que eso cuelga.</b> La excepcion corta el runnable del hilo de
 * interfaz <i>antes</i> de {@code matchController.finishGame()} (o, si cae en el
 * bloque gameOver, antes de {@code inputQueue.onGameOver(true)}). Sin
 * {@code finishGame} no sale el final, nadie llama a {@code nextGameDecision} y
 * {@code setOnMatchOver} no dispara nunca. El volcado de hilos lo dice tal cual:
 * el hilo de la partida ya ha terminado, {@code neo-ui} esta ocioso y
 * {@code main} espera para siempre en el latch de
 * {@code NeoQuestMatch.playFully}. Jugando seria lo mismo: la mesa sin pantalla
 * de victoria ni derrota.
 *
 * <p><b>El blindaje.</b> No se toca Forge (regla de oro). La lista de
 * observadores de la cola es un {@code Vector} privado de {@link Observable}, y
 * {@code notifyObservers} solo la lee con {@code toArray()}. Se cambia por una
 * igual cuyo {@code toArray()}, <b>llamado desde el hilo de interfaz</b>, deja
 * fuera a los {@code GameEventForwarder}. El resto de observadores (el
 * {@code InputProxy} de la mesa) se avisa como siempre, y altas y bajas
 * ({@code deleteObserver} en {@code ReplaySession.detach}) van sobre los mismos
 * objetos de siempre. Saltarse ese vaciado no pierde nada: los eventos se
 * quedan en el forwarder y salen en el siguiente punto del hilo de la partida
 * (prioridad, fin de turno, cambio de input o el {@code finish()} del final),
 * que es donde Forge dice que tienen que salir. Vale igual para los clientes
 * remotos cuando hospedamos una partida en red, que cuelgan su forwarder de la
 * misma cola y tienen la misma carrera.
 *
 * <p>Si algun dia lo arreglan rio arriba, esto simplemente deja de saltar.
 */
public final class ForwarderGuard {

    private static final Field OBSERVERS = findObservers();
    private static final AtomicInteger SKIPPED = new AtomicInteger();

    private ForwarderGuard() {
    }

    /** Cuantas veces se ha evitado un vaciado desde el hilo de interfaz. */
    public static int skipped() {
        return SKIPPED.get();
    }

    /** Blinda la cola de un humano. Se puede llamar varias veces. */
    public static void guard(final InputQueue queue) {
        if (OBSERVERS == null || queue == null) {
            return;
        }
        // addObserver y deleteObserver se sincronizan sobre la propia cola.
        synchronized (queue) {
            try {
                final Object current = OBSERVERS.get(queue);
                if (current instanceof GameThreadOnly || !(current instanceof Vector)) {
                    return;
                }
                @SuppressWarnings("unchecked")
                final Vector<Observer> old = (Vector<Observer>) current;
                OBSERVERS.set(queue, new GameThreadOnly(old));
            } catch (final ReflectiveOperationException | RuntimeException e) {
                System.out.println("[forwarder] no se ha podido blindar la cola: " + e);
            }
        }
    }

    private static Field findObservers() {
        try {
            final Field f = Observable.class.getDeclaredField("obs");
            f.setAccessible(true);
            return f;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.out.println("[forwarder] sin blindaje de la cola: " + e);
            return null;
        }
    }

    private static boolean onUiThread() {
        try {
            return GuiBase.getInterface() != null && GuiBase.getInterface().isGuiThread();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** La lista de observadores, que en el hilo de interfaz no avisa a los forwarders. */
    private static final class GameThreadOnly extends Vector<Observer> {
        private static final long serialVersionUID = 1L;

        GameThreadOnly(final Vector<Observer> old) {
            super(old);
        }

        @Override
        public synchronized Object[] toArray() {
            final Object[] all = super.toArray();
            if (!onUiThread()) {
                return all;
            }
            final ArrayList<Object> kept = new ArrayList<>(all.length);
            for (final Object o : all) {
                if (o instanceof GameEventForwarder fwd) {
                    SKIPPED.incrementAndGet();
                    // Lo normal es que este vacio; con eventos es la carrera de verdad.
                    if (fwd.hasPendingEvents()) {
                        System.out.println("[forwarder] vaciado desde el hilo de interfaz evitado"
                                + " (se queda para el hilo de la partida)");
                    }
                } else {
                    kept.add(o);
                }
            }
            return kept.size() == all.length ? all : kept.toArray();
        }
    }
}
