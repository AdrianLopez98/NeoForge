package forge.neo.match;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * La pausa mientras lees una carta, jugada de verdad ({@code run.cmd readingcheck}).
 *
 * <p>Pedida el 20-09-2026 en itch.io: ampliar una carta (o abrir el menu de
 * pausa) detiene la partida hasta que la cierras, para que un jugador nuevo
 * pueda leersela sin perderse lo que pasa detras. Lo hace
 * {@code NeoMatchUI.holdWhileReading}, durmiendo el hilo del MOTOR.
 *
 * <p>Lo que se comprueba es justo lo que no se ve jugando, y las tres partes
 * importan lo mismo:
 *
 * <ol>
 *   <li>que el motor <b>se pare de verdad</b> — que no sea la pantalla quieta
 *       mientras la partida sigue corriendo por debajo, que es exactamente el
 *       fallo que este ajuste viene a arreglar;</li>
 *   <li>que <b>vuelva a andar</b> al cerrar la carta;</li>
 *   <li>y que el <b>tope</b> suelte la partida aunque la bandera se quedara
 *       encendida. Eso ultimo es lo unico que puede convertir un ajuste comodo
 *       en una partida congelada para siempre, y de ahi no se sale mas que
 *       matando la ventana.</li>
 * </ol>
 *
 * <p>Sin ventana: la mesa se sustituye por una sonda
 * ({@code NeoMatchUI.setReadingProbe}), que es lo unico que la mesa le cuenta
 * al motor. Montar un JavaFX entero para comprobar una espera seria pagar una
 * ventana por nada. Los avisos se cuentan con el mismo espia que usa el
 * tutorial ({@code setEventSpy}): sin interfaz corre <b>en el hilo del motor</b>
 * y en orden, asi que "no ha llegado ni uno" significa lo que parece.
 */
public final class ReadingCheck {

    private ReadingCheck() {
    }

    private static final String FOREST = "Forest|Set:M21";

    /** Cuanto se espera a que el motor de senyales de vida. */
    private static final long FLOW_MS = 8_000;

    /** Cuanto se mira que NO pase nada con la carta ampliada. */
    private static final long QUIET_MS = 2_000;

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());

        // Lo que la mesa le diria al motor: "estoy leyendo una carta".
        final AtomicBoolean reading = new AtomicBoolean(false);
        // Avisos del motor recibidos. Sube mientras la partida anda.
        final AtomicInteger events = new AtomicInteger();

        final AtomicBoolean flowedFirst = new AtomicBoolean(false);
        final AtomicBoolean stopped = new AtomicBoolean(false);
        final AtomicBoolean resumed = new AtomicBoolean(false);
        final AtomicBoolean capped = new AtomicBoolean(false);
        final AtomicInteger leaked = new AtomicInteger(-1);
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread probe = new Thread(() -> {
            try {
                // 1. Control: sin nadie leyendo, la partida anda.
                flowedFirst.set(waitForFlow(events, alive));

                // 2. Con la carta ampliada, el motor se calla.
                reading.set(true);
                settle(events, alive);
                final int at = events.get();
                sleep(QUIET_MS, alive);
                leaked.set(events.get() - at);
                stopped.set(events.get() == at);

                // 3. Al cerrarla, sigue donde estaba.
                reading.set(false);
                resumed.set(waitForFlow(events, alive));

                // 4. El tope: con la bandera encendida a la fuerza, la partida
                //    se suelta sola. Cinco minutos no se pueden esperar aqui,
                //    asi que se acortan con la bandera de la guía de pruebas.
                System.setProperty("neo.readingCapMs", "500");
                reading.set(true);
                capped.set(waitForFlow(events, alive));
            } finally {
                // Pase lo que pase, la partida se suelta: una prueba que deja
                // el motor parado se come el tiempo de espera entero y acaba
                // acusando a otra cosa.
                reading.set(false);
                System.clearProperty("neo.readingCapMs");
            }
        }, "readingcheck-probe");
        probe.setDaemon(true);

        System.out.println("  Mesa: un Bosque por bando. Se amplia una carta a mitad de"
                + " partida y se mira si el motor se calla.");
        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, 120, null, false, ui -> {
                    ui.setReadingProbe(reading::get);
                    ui.setEventSpy(e -> events.incrementAndGet());
                    probe.start();
                });
        alive.set(false);

        System.out.printf(Locale.ROOT, "  Turnos jugados: %d | avisos del motor: %d%n",
                result.turns, events.get());

        check(flowedFirst.get(),
                "sin nadie leyendo, el motor va contando lo que pasa",
                "el motor no conto nada antes de parar: la prueba no prueba nada");
        check(stopped.get(),
                "con la carta ampliada, el motor se calla del todo (" + QUIET_MS + " ms)",
                "la partida siguio corriendo detras de la carta ampliada ("
                        + leaked.get() + " avisos en " + QUIET_MS + " ms)");
        check(resumed.get(),
                "al cerrar la carta, la partida sigue donde estaba",
                "la partida no volvio a andar al cerrar la carta: se quedo colgada");
        check(capped.get(),
                "el tope suelta la partida aunque la bandera se quede encendida",
                "con la bandera encendida la partida no se solto NUNCA: eso es un cuelgue");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed
                    + " comprobacion(es) de la pausa por lectura han fallado");
        }
    }

    /** Espera a que llegue un aviso mas. false si no llega ninguno a tiempo. */
    private static boolean waitForFlow(final AtomicInteger events, final AtomicBoolean alive) {
        final int from = events.get();
        final long until = System.currentTimeMillis() + FLOW_MS;
        while (alive.get() && System.currentTimeMillis() < until) {
            if (events.get() > from) {
                return true;
            }
            sleep(25, alive);
        }
        return false;
    }

    /**
     * Espera a que el motor se quede quieto.
     *
     * <p>Hace falta porque parar no es instantaneo y no puede serlo: el aviso
     * que ya venia de camino se entrega y el motor se para justo despues (la
     * espera vive DENTRO de {@code handleGameEvent}). Sin este compas, la
     * prueba contaria ese aviso y diria que no se ha parado nada.
     */
    private static void settle(final AtomicInteger events, final AtomicBoolean alive) {
        final long until = System.currentTimeMillis() + FLOW_MS;
        int last = -1;
        long stableSince = System.currentTimeMillis();
        while (alive.get() && System.currentTimeMillis() < until) {
            final int now = events.get();
            if (now != last) {
                last = now;
                stableSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - stableSince > 500) {
                return;
            }
            sleep(25, alive);
        }
    }

    private static void sleep(final long ms, final AtomicBoolean alive) {
        if (!alive.get()) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Una mesa cualquiera que se juegue sola.
     *
     * <p>Da igual lo que haya encima: lo que se mide es el pulso del motor, no
     * una jugada. Bosques, porque no hacen nada raro.
     */
    private static TutorialLesson position() {
        // Veinte por bando: lo justo para que la partida dure mas que la
        // prueba (unos 12 s entre las cuatro fases) y se acabe sola por
        // biblioteca poco despues. Con cuarenta eran 84 turnos y 50 s de
        // comprobador para mirar cuatro cosas.
        final String library = String.join(";", Collections.nCopies(20, FOREST));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + FOREST,
                "humanhand=",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=" + FOREST,
                "aihand=",
                "ailibrary=" + library,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("reading", state, List.of());
    }

    private static void check(final boolean ok, final String good, final String bad) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK" : "MAL", ok ? good : bad);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
