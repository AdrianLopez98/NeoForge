package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * {@code run.cmd anytypecheck}: lo robado con "mana de cualquier tipo" brilla
 * cuando llega el mana, aunque no sea de su color. Ver {@link AnyTypeMana}.
 *
 * <p>El permiso lo crea la propia carta: Black Cat, Cunning Thief se "lanza" al
 * montar la mesa ({@code humanprecast}, su disparo {@code TrigDig} contra la IA)
 * y exilia dos de los nueve de arriba de la IA, que son todos Tragic Arrogance
 * ({3}{W}{W}). Con Gonti, Canny Acquisitor en la mesa cuesta 4. Sin una sola
 * fuente blanca.
 */
public final class AnyTypeCheck {

    private AnyTypeCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String ARROGANCE = "Tragic Arrogance|Set:KLD";
    private static final String GONTI = "Gonti, Canny Acquisitor|Set:OTC";
    private static final String SWAMP = "Swamp|Set:M21";
    private static final String CAT = "Black Cat, Cunning Thief:TrigDig->ai";

    private static final int SECONDS = 15;

    public static void run() {
        passed = 0;
        failed = 0;
        SafeActions.forceScanOnForTest();

        // 1. La del informe: cuatro Pantanos y Gonti (cuesta 4). Llega.
        play("informe", GONTI + ";" + (SWAMP + ";").repeat(3) + SWAMP, true);
        check(AnyTypeMana.seen().contains("Tragic Arrogance"),
                "Tragic Arrogance brilla con cuatro Pantanos (" + AnyTypeMana.seen() + ")");

        // 2. Tres Pantanos: cuesta 4, no llega. No tiene que brillar.
        play("no llega", GONTI + ";" + (SWAMP + ";").repeat(2) + SWAMP, true);
        check(AnyTypeMana.seen().isEmpty(),
                "con tres Pantanos no brilla: cuesta 4 (" + AnyTypeMana.seen() + ")");

        // 3. Sin Black Cat no hay permiso: nada que mirar.
        play("sin permiso", GONTI + ";" + (SWAMP + ";").repeat(3) + SWAMP, false);
        check(AnyTypeMana.seen().isEmpty(),
                "sin nada robado no ilumina nada (" + AnyTypeMana.seen() + ")");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del mana de cualquier tipo han fallado");
        }
    }

    private static void play(final String title, final String battlefield, final boolean steal) {
        System.out.println("  Mesa: " + title);
        AnyTypeMana.resetForTest();
        final java.util.List<String> state = new java.util.ArrayList<>(Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + battlefield,
                "humanhand=",
                "humanlibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + SWAMP, "aihand=",
                "ailibrary=" + (ARROGANCE + ";").repeat(9) + ARROGANCE,
                "aigraveyard=", "aiexile=", "aicommand="));
        if (steal) {
            state.add("humanprecast=" + CAT);
        }
        final TutorialLesson lesson = new TutorialLesson("anytype", state, List.of());
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
