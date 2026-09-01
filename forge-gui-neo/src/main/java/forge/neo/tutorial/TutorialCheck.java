package forge.neo.tutorial;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.StaticData;
import forge.game.event.GameEvent;
import forge.game.phase.PhaseType;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;

/**
 * El tutorial entero, sin ventana. {@code run.cmd tutorialcheck}.
 *
 * <p>De un tutorial, lo que se rompe en silencio no se ve en una captura:
 *
 * <ul>
 *   <li><b>Un nombre de carta mal escrito.</b> El motor lo tira por
 *       {@code System.err} y sigue: la leccion arranca con la mano vacia y el
 *       paso dice "lanza la criatura" sobre una mano en la que no hay nada.</li>
 *   <li><b>Un texto que falta.</b> {@link forge.neo.NeoText} cae al ingles y,
 *       si tampoco esta, ensenya la clave. En pantalla se lee
 *       "tutorial.mesa.land.body", que es exactamente lo que hay que cazar
 *       aqui, y en <b>los dos</b> idiomas.</li>
 *   <li><b>Un gesto inventado.</b> Un paso que espera {@code "zoom-card"}
 *       cuando la mesa dice {@code "zoom.card"} <b>no se puede terminar
 *       nunca</b>: no falla, se queda ahi. Ver {@link Gesture}.</li>
 *   <li><b>Una posicion que no arranca.</b> Por eso al final se juega cada
 *       leccion de verdad, con la interfaz contestando sola, y se ensenya
 *       <b>como quedo la mesa</b>. Con "la partida ha empezado" no basta: una
 *       zona vacia empieza igual de bien.</li>
 * </ul>
 */
public final class TutorialCheck {

    private TutorialCheck() {
    }

    /** Cuanto se le deja jugar sola a cada leccion. */
    private static final int PLAY_SECONDS = 45;

    private static int problems;

    public static void run() {
        problems = 0;

        final Properties es = read("es-ES");
        final Properties en = read("en-US");
        System.out.printf(Locale.ROOT, "  Textos: %d en castellano, %d en ingles%n",
                es.size(), en.size());

        for (final TutorialLesson lesson : NeoTutorial.lessons()) {
            System.out.println();
            System.out.println("  == " + lesson.getId() + " ==");
            checkState(lesson);
            checkSteps(lesson, es, en);
            checkLessonTexts(lesson, es, en);
            checkReachable(lesson);
        }

        System.out.println();
        System.out.println("  -- Jugando cada leccion (la interfaz contesta sola) --");
        for (final TutorialLesson lesson : NeoTutorial.lessons()) {
            play(lesson);
        }

        System.out.println();
        if (problems == 0) {
            System.out.println("  OK: el tutorial esta entero.");
        } else {
            System.out.printf(Locale.ROOT, "  %d PROBLEMA(S). Ver arriba.%n", problems);
        }
    }

    // ---------------------------------------------------------------

    /** Las cartas existen, la fase existe y estan los dos jugadores. */
    private static void checkState(final TutorialLesson lesson) {
        boolean human = false;
        boolean ai = false;
        int cards = 0;

        for (final String line : lesson.getState()) {
            final int eq = line.indexOf('=');
            if (eq < 0) {
                fail(lesson, "linea sin '=': " + line);
                continue;
            }
            final String key = line.substring(0, eq).toLowerCase(Locale.ROOT);
            final String value = line.substring(eq + 1);

            if (key.startsWith("human")) {
                human = true;
            } else if (key.startsWith("ai")) {
                ai = true;
            }

            if ("activephase".equals(key) && PhaseType.smartValueOf(value) == null) {
                fail(lesson, "fase desconocida: " + value);
            }
            if ("activeplayer".equals(key) && !"human".equals(value) && !"ai".equals(value)) {
                fail(lesson, "activeplayer tiene que ser human o ai: " + value);
            }
            if (!isZone(key) || value.isBlank()) {
                continue;
            }
            for (final String entry : value.split(";")) {
                // El nombre va antes del primer '|': lo que sigue son marcas
                // (Set:, IsCommander, Tapped, Counters:...).
                final String[] parts = entry.split("\\|");
                final String name = parts[0].trim();
                if (name.isEmpty()) {
                    continue;
                }
                String set = null;
                for (int i = 1; i < parts.length; i++) {
                    if (parts[i].startsWith("Set:")) {
                        set = parts[i].substring(4).trim();
                    }
                }
                cards++;
                // Con la EDICION, no solo con el nombre. Ahi se esconde el
                // fallo: una carta que existe pero no en esa edicion se queda
                // en null y el motor se la salta en silencio, asi que la
                // leccion arranca con la zona a medias y el paso pide algo que
                // no esta en ninguna parte.
                if (StaticData.instance().getCommonCards().getCard(name, set, -1) == null) {
                    fail(lesson, "carta que no existe: " + name
                            + (set == null ? "" : " en " + set) + "  (en " + key + ")");
                } else if (set == null) {
                    // Y sin edicion fijada el motor coge la impresion mas
                    // vieja: marco antiguo y arte que quien empieza no lee.
                    fail(lesson, "carta sin edicion fijada: " + name + "  (en " + key + ")");
                }
            }
        }

        if (!human || !ai) {
            fail(lesson, "faltan lineas de un jugador: el motor exige DOS estados");
        }
        System.out.printf(Locale.ROOT, "     posicion: %d lineas, %d cartas%n",
                lesson.getState().size(), cards);
    }

    private static boolean isZone(final String key) {
        return key.endsWith("battlefield") || key.endsWith("hand") || key.endsWith("library")
                || key.endsWith("graveyard") || key.endsWith("exile") || key.endsWith("command");
    }

    /** Cada paso tiene sus dos textos en los dos idiomas, y su gesto existe. */
    private static void checkSteps(final TutorialLesson lesson, final Properties es,
                                   final Properties en) {
        final List<String> missing = new ArrayList<>();
        int reads = 0;
        int actions = 0;

        for (final TutorialStep step : lesson.getSteps()) {
            for (final String suffix : new String[] {".title", ".body"}) {
                final String key = step.getKey() + suffix;
                if (blank(es, key)) {
                    missing.add("es " + key);
                }
                if (blank(en, key)) {
                    missing.add("en " + key);
                }
            }
            if (step.isRead()) {
                reads++;
            } else {
                actions++;
            }
            final String gesture = step.getGesture();
            if (gesture != null && !Gesture.all().contains(gesture)) {
                fail(lesson, "gesto que la mesa no dispara nunca: " + gesture
                        + "  (en " + step.getKey() + ")");
            }
        }

        System.out.printf(Locale.ROOT, "     pasos: %d (%d de leer, %d de hacer)%n",
                lesson.getSteps().size(), reads, actions);
        for (final String m : missing) {
            fail(lesson, "falta el texto: " + m);
        }
    }

    private static void checkLessonTexts(final TutorialLesson lesson, final Properties es,
                                         final Properties en) {
        for (final String suffix : new String[] {".title", ".summary"}) {
            final String key = "tutorial." + lesson.getId() + suffix;
            if (blank(es, key)) {
                fail(lesson, "falta el texto: es " + key);
            }
            if (blank(en, key)) {
                fail(lesson, "falta el texto: en " + key);
            }
        }
    }

    /**
     * Un paso que NO se puede cerrar nunca es el fallo mas caro del tutorial.
     *
     * <p>No revienta, no deja rastro y no se ve en una captura: el paso se
     * queda ahi puesto, el jugador hace lo que se le pide una y otra vez y no
     * pasa nada. Asi que a cada paso de partida se le ensenya un probe al que
     * <b>ya le ha pasado todo</b> y otro al que no le ha pasado nada:
     *
     * <ul>
     *   <li>si dice que no con el primero, la condicion esta mal escrita y el
     *       paso es una trampa: no hay forma humana de terminarlo;</li>
     *   <li>si dice que si con el segundo, se cierra solo nada mas salir y el
     *       jugador no llega ni a leerlo.</li>
     * </ul>
     */
    private static void checkReachable(final TutorialLesson lesson) {
        int watches = 0;
        for (final TutorialStep step : lesson.getSteps()) {
            if (step.isRead() || step.getGesture() != null) {
                continue;
            }
            watches++;
            if (!step.closedBy(TutorialProbe.everything())) {
                fail(lesson, "paso IMPOSIBLE de terminar: " + step.getKey()
                        + "  (dice que no aunque haya pasado todo)");
            }
            if (step.closedBy(new TutorialProbe())) {
                fail(lesson, "paso que se cierra solo: " + step.getKey()
                        + "  (ya es cierto sin haber hecho nada)");
            }
        }
        System.out.printf(Locale.ROOT, "     condiciones: %d pasos de partida, comprobados%n",
                watches);
    }

    /**
     * Juega la leccion de verdad, con la leccion <b>enganchada</b>, y ensenya
     * hasta donde llego.
     *
     * <p><b>Por que hay que llevar la cuenta de los pasos y no solo jugar.</b>
     * Antes esto arrancaba la partida y miraba como quedaba la mesa; los pasos
     * no los tocaba nadie. Por eso paso desapercibido que "Ve al combate" no se
     * cerraba nunca: la posicion estaba perfecta, las cartas estaban, la
     * partida arrancaba de maravilla — y el tutorial se quedaba clavado.
     *
     * <p>Sin ventana no hay quien lea un paso ni quien haga un gesto, asi que
     * esos se dan por pasados. Los de partida los cierra el motor o no los
     * cierra nadie, que es justo lo que interesa mirar.
     */
    private static void play(final TutorialLesson lesson) {
        final TutorialState position = new TutorialState(lesson.getState());
        final TutorialProgress progress = new TutorialProgress(lesson);
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread poller = new Thread(() -> {
            while (alive.get()) {
                pump(progress, gui[0], null);
                try {
                    Thread.sleep(100L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "tutorialcheck-poll");
        poller.setDaemon(true);

        final NeoGame.Result r = NeoGame.playTutorial(lesson, position,
                NeoMatchUI.Mode.AUTO_PLAY, PLAY_SECONDS, null, true, ui -> {
                    gui[0] = ui;
                    progress.goTo(0, null, null);
                    ui.setEventSpy(ev -> pump(progress, ui, ev));
                    poller.start();
                });
        alive.set(false);

        final String board = position.getSummary();
        if (board == null) {
            fail(lesson, "la posicion no llego a aplicarse");
        } else {
            System.out.println("     " + lesson.getId() + " -> " + board);
        }
        System.out.printf(Locale.ROOT, "        turnos jugados: %d | decisiones: %d%n",
                r.turns, r.decisions);
        if (r.turns <= 0) {
            fail(lesson, "la partida no llego a empezar");
        }

        final TutorialStep stuck = progress.current();
        System.out.printf(Locale.ROOT, "        pasos pasados: %d de %d%s%n",
                Math.max(0, progress.getIndex()), lesson.getSteps().size(),
                stuck == null ? "" : "  (parado en " + stuck.getKey() + ")");
        checkStalled(lesson, progress);
    }

    /**
     * Los pasos que tenian que haberse cerrado solos, y no se cerraron.
     *
     * <p>El automatico solo sabe pulsar el boton grande: no juega tierras, no
     * lanza nada y no ataca. Asi que aqui solo se puede exigir lo que se
     * consigue pulsando el boton — y da la casualidad de que eso es
     * exactamente el paso que se rompio. "Ve al combate" se cierra por LLEGAR
     * al paso de atacantes, y llegar es lo unico que hace el automatico.
     *
     * <p>Lo demas (jugar una tierra, atacar, activar una habilidad) sigue
     * necesitando manos: se ensenya hasta donde llego y ya.
     */
    private static void checkStalled(final TutorialLesson lesson,
                                     final TutorialProgress progress) {
        for (int i = 0; i < lesson.getSteps().size(); i++) {
            final TutorialStep step = lesson.getSteps().get(i);
            if (!PASSABLE_ALONE.contains(step.getKey())) {
                continue;
            }
            if (progress.getIndex() <= i) {
                fail(lesson, "paso que no avanzo jugando: " + step.getKey()
                        + "  (se cierra con solo llegar, y no se cerro)");
            }
        }
    }

    /**
     * Los pasos que el modo automatico SI puede cerrar, y por tanto los unicos
     * que se pueden exigir aqui.
     *
     * <p>Son <b>toda la leccion del combate</b>, y eso es nuevo: el automatico
     * pulsa el boton grande y, cuando el boton esta apagado, clica las cartas
     * que el motor ha marcado — o sea que llega al combate, declara atacantes y
     * reparte. Antes solo se exigia el primer paso porque los demas no se
     * cerraban... y no se cerraban por un fallo, no por una limitacion:
     * {@code handleGameEvent} no se llamaba nunca en una partida local. Con eso
     * arreglado la leccion se pasa entera y sola, tres veces de tres, asi que
     * ahora se exige. Es la red que caza ese fallo si vuelve.
     *
     * <p>Lo que sigue necesitando manos — jugar una tierra, activar una
     * habilidad — no esta aqui: se ensenya hasta donde llego y ya.
     */
    private static final List<String> PASSABLE_ALONE = List.of(
            "tutorial.combate.go",
            "tutorial.combate.declare",
            "tutorial.combate.blocks");

    /** Cierra todos los pasos que se puedan cerrar ya, de uno en uno. */
    private static void pump(final TutorialProgress progress, final NeoMatchUI ui,
                             final GameEvent first) {
        if (ui == null) {
            return;
        }
        synchronized (progress) {
            GameEvent ev = first;
            while (!progress.isFinished()) {
                final TutorialStep step = progress.current();
                if (step == null) {
                    return;
                }
                // Sin ventana no hay quien lea ni quien clique: esos se pasan.
                final boolean closed = step.isRead() || step.getGesture() != null
                        || progress.sample(ui.getGameView(), ui.getCurrentPlayer(), ev);
                ev = null;
                if (!closed) {
                    return;
                }
                progress.next(ui.getGameView(), ui.getCurrentPlayer());
            }
        }
    }

    // ---------------------------------------------------------------

    private static boolean blank(final Properties p, final String key) {
        final String v = p.getProperty(key);
        return v == null || v.isBlank();
    }

    private static Properties read(final String id) {
        final Properties p = new Properties();
        try (InputStream in = TutorialCheck.class.getResourceAsStream(
                "/forge/neo/lang/neo-" + id + ".properties")) {
            if (in == null) {
                System.out.println("  No se encuentra el fichero de idioma " + id);
                problems++;
                return p;
            }
            try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                p.load(r);
            }
        } catch (final IOException e) {
            System.out.println("  No se ha podido leer " + id + ": " + e);
            problems++;
        }
        return p;
    }

    private static void fail(final TutorialLesson lesson, final String what) {
        problems++;
        System.out.println("     FALLO [" + lesson.getId() + "] " + what);
    }
}
