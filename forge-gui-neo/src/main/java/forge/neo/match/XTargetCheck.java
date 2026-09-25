package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * El auto-pass con Chthonian Nightmare, sin ventana (ver {@link XTargets}).
 *
 * <p>Tres mesas, las tres con el Nightmare en la mesa y un Grizzly Bears para
 * sacrificar, en tu fase principal y sin nada en la mano:
 *
 * <ul>
 *   <li><b>La del informe</b>: 3 de energia y un Dark Confidant (valor de mana
 *       2) en el cementerio. El motor tiene que decir "no hay acciones" — si no
 *       lo dice, Card-Forge ha arreglado su estimacion y esto sobra — y
 *       nosotros tenemos que parar, con X = 2.</li>
 *   <li><b>Poca energia</b>: 1 de energia y el mismo Confidant. Con X = 1 no
 *       hay objetivo, y a 2 no llegas: no se para.</li>
 *   <li><b>Cementerio vacio</b>: nada que devolver, no se para.</li>
 * </ul>
 *
 * <p>Se mira el <b>primer</b> veredicto de cada mesa. Se ejecuta con
 * {@code run.cmd xtargetcheck}.
 */
public final class XTargetCheck {

    private XTargetCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String NIGHTMARE = "Chthonian Nightmare|Set:MH3";
    private static final String BEARS = "Grizzly Bears|Set:6ED";
    private static final String CONFIDANT = "Dark Confidant|Set:RAV";
    private static final String SWAMP = "Swamp|Set:M21";

    /** Lo que dura cada mesa: basta con la primera prioridad. */
    private static final int SECONDS = 15;

    public static void run() {
        passed = 0;
        failed = 0;
        SafeActions.forceScanOnForTest();

        // 1. La del informe.
        play("informe", 3, CONFIDANT, true);
        final XTargets.Verdict report = XTargets.first();
        final Boolean engine = XTargets.engineSawActions();
        check(report != null && "Chthonian Nightmare".equals(report.reason()) && report.x() == 2,
                "se para por Chthonian Nightmare, con X = 2 (" + report + ")");
        if (Boolean.TRUE.equals(engine)) {
            System.out.println("  NOTA el motor YA ve la accion: Card-Forge ha arreglado los "
                    + "objetivos en X y XTargets sobra. Mirar antes de quitarlo");
        } else {
            check(Boolean.FALSE.equals(engine),
                    "el motor, en cambio, dice que no hay acciones (el hueco existe: " + engine + ")");
        }

        // 2. Poca energia: a X = 2 no se llega.
        play("poca energia", 1, CONFIDANT, false);
        check(XTargets.first() == null && XTargets.holds() == 0,
                "con 1 de energia y un objetivo de 2 no se para (" + XTargets.first() + ")");

        // 3. Cementerio vacio.
        play("cementerio vacio", 3, "", false);
        check(XTargets.first() == null && XTargets.holds() == 0,
                "sin nada en el cementerio no se para (" + XTargets.first() + ")");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de los objetivos en X han fallado");
        }
    }

    private static void play(final String title, final int energy, final String graveyard,
                             final boolean probeEngine) {
        System.out.println("  Mesa: " + title);
        XTargets.resetForTest(probeEngine);
        final TutorialLesson lesson = new TutorialLesson("xtarget", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humancounters=ENERGY=" + energy,
                "humanbattlefield=" + NIGHTMARE + ";" + BEARS + ";" + SWAMP,
                "humanhand=",
                "humanlibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "humangraveyard=" + graveyard,
                "humanexile=", "humancommand=",
                "aibattlefield=" + SWAMP, "aihand=",
                "ailibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
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
