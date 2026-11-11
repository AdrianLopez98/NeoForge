package forge.neo.match;

import java.util.HashMap;
import java.util.Map;

/**
 * EL VIGIA DE LA IA ATASCADA (Discord, 11-11-2026: <i>"AI refused to play
 * ... I waited like almost 5 minutes"</i>, con Teval, Arbiter of Virtue y el
 * aro de "pensando" quieto).
 *
 * <p>La IA de Forge tiene un tiempo limite ({@code AITimeoutTracker}), pero es
 * <b>cooperativo</b>: solo se mira en ciertos puntos, asi que un bucle dentro
 * de una evaluacion no lo corta nunca. La partida no muere — se puede tocar
 * cartas —, simplemente no avanza. Y pasado su limite el propio contador da la
 * sesion por perdida y deja de contarla, asi que tampoco sirve para vigilar.
 *
 * <p>Esto mira el hilo de la partida ({@code Game-N}) cada pocos segundos: si
 * lleva {@link #stallSeconds()} seguidos <b>ejecutando codigo de la IA</b>
 * ({@code forge.ai.*}), se apunta su traza UNA vez por atasco y se avisa. No
 * corta nada ni cambia la partida: es para saber, la proxima vez, en que carta
 * y en que funcion se queda. Las decisiones del humano no cuentan: entonces el
 * hilo espera en la interfaz, no en la IA.
 *
 * <p>Java puro, sin nada que Android no tenga en la 26: lo usan
 * {@code NeoMatchUI} (al registro y un aviso en la mesa) y {@code AndroidMatchUI}
 * (a «Últimos fallos», con su boton de copiar). {@code -Dneo.aistall.seconds=N}
 * cambia el umbral; {@code run.cmd aistallcheck} lo prueba.
 */
public final class AiStallWatch {

    private AiStallWatch() {
    }

    /** A quien se avisa. Se llama desde el hilo del vigia: hay que saltar al de interfaz. */
    public interface Listener {
        void stalled(String report);
    }

    private static final long POLL_MS = 5_000L;

    /** Que marco cuenta como "la IA". Lo cambia la prueba. */
    static volatile String aiPrefix = "forge.ai.";
    /** Que hilos se miran. Los de partida de Forge se llaman Game-N. */
    static volatile String threadPrefix = "Game-";
    static volatile long pollMs = POLL_MS;

    private static volatile Listener listener;
    private static volatile java.util.function.Supplier<String> context = () -> "";
    private static Thread watcher;

    /** Hilo -> desde cuando (ms) esta en la IA sin parar. */
    private static final Map<Thread, Long> since = new HashMap<>();
    /** Los que ya se han contado en este atasco. */
    private static final java.util.Set<Thread> reported = new java.util.HashSet<>();

    public static long stallSeconds() {
        try {
            return Math.max(1L, Long.parseLong(System.getProperty("neo.aistall.seconds", "90")));
        } catch (final NumberFormatException e) {
            return 90L;
        }
    }

    /**
     * Empieza a vigilar (o cambia a quien avisa). Se llama al abrir cada partida;
     * una llamada nueva sustituye a la anterior.
     *
     * @param what una linea que diga como va la partida (turno, fase, quien
     *             tiene la prioridad), leida EN EL MOMENTO del atasco
     */
    public static synchronized void watch(final java.util.function.Supplier<String> what, final Listener l) {
        listener = l;
        context = what == null ? () -> "" : what;
        since.clear();
        reported.clear();
        if (watcher == null || !watcher.isAlive()) {
            final Thread t = new Thread(AiStallWatch::loop, "neo-ai-stall-watch");
            t.setDaemon(true);
            watcher = t;
            t.start();
        }
    }

    /** Deja de avisar (la partida se ha acabado). El hilo se queda dormido, no cuesta nada. */
    public static synchronized void stop() {
        listener = null;
        since.clear();
        reported.clear();
    }

    private static void loop() {
        while (true) {
            try {
                Thread.sleep(pollMs);
            } catch (final InterruptedException e) {
                return;
            }
            try {
                sample();
            } catch (final RuntimeException e) {
                // El vigia no puede tumbar nada.
            }
        }
    }

    static void sample() {
        final Listener l = listener;
        if (l == null) {
            return;
        }
        final long now = System.currentTimeMillis();
        final long limit = stallSeconds() * 1000L;
        String report = null;
        synchronized (AiStallWatch.class) {
            final Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
            since.keySet().retainAll(all.keySet());
            reported.retainAll(all.keySet());
            for (final Map.Entry<Thread, StackTraceElement[]> e : all.entrySet()) {
                final Thread t = e.getKey();
                if (t.getName() == null || !t.getName().startsWith(threadPrefix)) {
                    continue;
                }
                if (t.getState() != Thread.State.RUNNABLE || !inAi(e.getValue())) {
                    // Fuera de la IA (o esperando): el atasco, si lo hubo, se acabo.
                    since.remove(t);
                    reported.remove(t);
                    continue;
                }
                final Long from = since.get(t);
                if (from == null) {
                    since.put(t, now);
                } else if (now - from >= limit && reported.add(t)) {
                    report = describe(t, e.getValue(), (now - from) / 1000L);
                }
            }
        }
        if (report != null) {
            System.out.println("[neo] " + report);
            try {
                l.stalled(report);
            } catch (final RuntimeException ignored) {
                // Quien avisa no puede tumbar al vigia.
            }
        }
    }

    /**
     * Como va la partida, para el informe: turno, fase, de quien es el turno y
     * quien tiene la prioridad. Se lee sin bloquear nada: si algo falla, vacio.
     */
    public static String gameLine(final forge.game.Game g) {
        if (g == null) {
            return "";
        }
        try {
            final forge.game.phase.PhaseHandler ph = g.getPhaseHandler();
            final forge.game.player.Player turn = ph.getPlayerTurn();
            final forge.game.player.Player prio = ph.getPriorityPlayer();
            return "turn " + ph.getTurn() + ", " + ph.getPhase()
                    + (turn == null ? "" : ", " + turn.getName() + "'s turn")
                    + (prio == null ? "" : ", priority: " + prio.getName()
                    + " (hand " + prio.getCardsIn(forge.game.zone.ZoneType.Hand).size()
                    + ", graveyard " + prio.getCardsIn(forge.game.zone.ZoneType.Graveyard).size() + ")");
        } catch (final RuntimeException e) {
            return "";
        }
    }

    private static boolean inAi(final StackTraceElement[] stack) {
        if (stack == null) {
            return false;
        }
        for (final StackTraceElement f : stack) {
            if (f.getClassName().startsWith(aiPrefix)) {
                return true;
            }
        }
        return false;
    }

    private static String describe(final Thread t, final StackTraceElement[] stack, final long seconds) {
        final StringBuilder sb = new StringBuilder();
        sb.append("AI stuck thinking for ").append(seconds).append(" s");
        String what;
        try {
            what = context.get();
        } catch (final RuntimeException e) {
            what = null;
        }
        if (what != null && !what.isEmpty()) {
            sb.append(" (").append(what).append(')');
        }
        sb.append(" on ").append(t.getName()).append('\n');
        // Lo de arriba es donde da vueltas; lo de abajo, que decision empezo.
        final int max = 60;
        for (int i = 0; i < stack.length && i < max; i++) {
            sb.append("  at ").append(stack[i]).append('\n');
        }
        if (stack.length > max) {
            sb.append("  ... ").append(stack.length - max).append(" more\n");
        }
        return sb.toString().trim();
    }
}
