package forge.neo.match;

import java.util.concurrent.atomic.AtomicInteger;

import forge.util.ThreadUtil;

/**
 * <b>Preguntarle al motor por una carta, solo desde el hilo de la partida.</b>
 *
 * <p>El motor no es seguro entre hilos (el javadoc de {@code Tracker} lo dice
 * tal cual: <i>"game thread only"</i>). Cualquier consulta que acabe en
 * {@code checkStaticAbilities} — {@code getReplacementList}, {@code canPlay},
 * {@code canPay}, {@code getAllPossibleAbilities}... — reescribe las estaticas
 * y congela el Tracker; hecha desde la interfaz mientras la partida sigue,
 * <b>mata la partida</b> con una {@code ConcurrentModificationException}. Paso
 * en la 1.0.43 y otra vez en la 1.0.61 (21-11-2026: partidas rotas al minuto
 * de publicarla, en PC y Android).
 *
 * <p>Las sondas que preguntan al motor llaman a {@link #check} al empezar:
 * fuera del hilo de la partida <b>no preguntan</b> (devuelven lo de "no se
 * sabe", que es no pintar la marca) y se apunta. {@code GamesCheck} juega
 * partidas enteras con la mesa de verdad y sale en rojo si se apunta una sola:
 * asi el fallo salta SIEMPRE en la bateria, no solo cuando los dos hilos
 * tienen la mala suerte de cruzarse.
 */
public final class EngineThread {

    private EngineThread() {
    }

    private static final AtomicInteger VIOLATIONS = new AtomicInteger();

    /**
     * Si se puede preguntar al motor desde aqui. Si no, se apunta (con la
     * traza la primera vez de cada sonda) y la sonda no debe preguntar.
     */
    public static boolean check(final String probe) {
        if (ThreadUtil.isGameThread()) {
            return true;
        }
        if (VIOLATIONS.getAndIncrement() < 5) {
            System.err.println("[hilo] " + probe + " pregunto al motor desde el hilo '"
                    + Thread.currentThread().getName() + "': no se pregunta (ver EngineThread)");
            new Throwable("[hilo] " + probe).printStackTrace();
        }
        return false;
    }

    /** Cuantas veces alguna sonda intento preguntar fuera del hilo de la partida. */
    public static int violations() {
        return VIOLATIONS.get();
    }
}
