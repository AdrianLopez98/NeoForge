package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * El auto-pass con improvisar y convocar, sin ventana (ver {@link TapToPay}).
 *
 * <p>Todas en tu fase principal, con el hechizo en la mano:
 *
 * <ul>
 *   <li><b>La del informe</b>: Reverse Engineer (3UU, improvisar), dos Islas y
 *       tres Ornitopteros. El motor tiene que decir "no hay acciones" — si no lo
 *       dice, Card-Forge ha cambiado su estimacion y esto sobra — y nosotros
 *       tenemos que parar.</li>
 *   <li><b>Falta mana de color</b>: una Isla y los mismos Ornitopteros. Los
 *       artefactos solo pagan genérico y faltan {U}: no se para.</li>
 *   <li><b>Sin artefactos</b>: dos Islas solas. No se para.</li>
 *   <li><b>Convocar</b>: Stoke the Flames (2RR), dos Montanas y dos Grizzly
 *       Bears. Se para.</li>
 * </ul>
 *
 * <p>Se mira el <b>primer</b> veredicto de cada mesa. Se ejecuta con
 * {@code run.cmd improvisecheck}.
 */
public final class ImproviseCheck {

    private ImproviseCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String ENGINEER = "Reverse Engineer|Set:KLD";
    private static final String THOPTER = "Ornithopter|Set:M15";
    private static final String ISLAND = "Island|Set:M21";
    private static final String STOKE = "Stoke the Flames|Set:M15";
    private static final String MOUNTAIN = "Mountain|Set:M21";
    private static final String BEARS = "Grizzly Bears|Set:6ED";

    /** Lo que dura cada mesa: basta con la primera prioridad. */
    private static final int SECONDS = 15;

    public static void run() {
        passed = 0;
        failed = 0;
        SafeActions.forceScanOnForTest();

        // 1. La del informe.
        play("informe", ENGINEER,
                ISLAND + ";" + ISLAND + ";" + THOPTER + ";" + THOPTER + ";" + THOPTER, true);
        final TapToPay.Verdict report = TapToPay.first();
        final Boolean engine = TapToPay.engineSawActions();
        check(report != null && "Reverse Engineer".equals(report.reason()),
                "se para por Reverse Engineer (" + report + ")");
        if (Boolean.TRUE.equals(engine)) {
            System.out.println("  NOTA el motor YA ve la accion: Card-Forge ha cambiado la "
                    + "estimacion de improvisar y TapToPay sobra. Mirar antes de quitarlo");
        } else {
            check(Boolean.FALSE.equals(engine),
                    "el motor, en cambio, dice que no hay acciones (el hueco existe: " + engine + ")");
        }

        // 2. Falta mana de color: los artefactos no pagan {U}.
        play("falta azul", ENGINEER, ISLAND + ";" + THOPTER + ";" + THOPTER + ";" + THOPTER, false);
        check(TapToPay.first() == null && TapToPay.holds() == 0,
                "con una sola Isla no se para: falta {U} (" + TapToPay.first() + ")");

        // 3. Sin artefactos.
        play("sin artefactos", ENGINEER, ISLAND + ";" + ISLAND, false);
        check(TapToPay.first() == null && TapToPay.holds() == 0,
                "sin artefactos no se para (" + TapToPay.first() + ")");

        // 4. Convocar.
        play("convocar", STOKE, MOUNTAIN + ";" + MOUNTAIN + ";" + BEARS + ";" + BEARS, false);
        check(TapToPay.first() != null && "Stoke the Flames".equals(TapToPay.first().reason()),
                "convocar tambien se para (" + TapToPay.first() + ")");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de improvisar han fallado");
        }
    }

    private static void play(final String title, final String hand, final String battlefield,
                             final boolean probeEngine) {
        System.out.println("  Mesa: " + title);
        TapToPay.resetForTest(probeEngine);
        final TutorialLesson lesson = new TutorialLesson("improvise", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + battlefield,
                "humanhand=" + hand,
                "humanlibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + ISLAND, "aihand=",
                "ailibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, SECONDS, null, false, null);
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
