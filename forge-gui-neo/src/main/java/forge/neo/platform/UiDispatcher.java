package forge.neo.platform;

/**
 * Abstraccion del "hilo de interfaz".
 *
 * <p>ESTA ES LA PIEZA MAS DELICADA DEL PROYECTO. El motor de Forge asume el
 * modelo de Swing: existe un hilo de UI, la partida corre en otro, y el motor
 * comprueba activamente en que hilo esta ({@code FThreads.assertExecutedByEdt}).
 * Si {@link #isUiThread()} miente, el motor se niega a bloquearse esperando al
 * jugador y la partida no arranca.
 *
 * <p>Reglas:
 * <ul>
 *   <li>El hilo del motor NUNCA debe ser el hilo de UI.</li>
 *   <li>El hilo de UI NUNCA debe bloquearse esperando al motor.</li>
 *   <li>Las respuestas al motor se mandan desde un tercer hilo (o desde el de
 *       UI de forma no bloqueante).</li>
 * </ul>
 *
 * <p>Fase 1: {@link ConsoleUiDispatcher}, un hilo dedicado.
 * <br>Fase 2: una implementacion sobre {@code Platform.runLater} de JavaFX.
 * El resto del codigo no se entera del cambio.
 */
public interface UiDispatcher {

    /** true si el hilo actual es el de interfaz. */
    boolean isUiThread();

    /** Encola en el hilo de interfaz y vuelve inmediatamente. */
    void runLater(Runnable task);

    /**
     * Ejecuta en el hilo de interfaz y espera a que termine.
     * Si ya estamos en el hilo de interfaz, ejecuta en linea.
     */
    void runAndWait(Runnable task);

    /** Cierra el dispatcher. */
    default void shutdown() { }
}
