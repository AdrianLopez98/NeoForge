package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * {@code run.cmd sacoutletcheck}: con algo del rival en el stack, un altar de
 * sacrificio (Ashnod's Altar) te deja la prioridad aunque el pase automatico
 * este puesto. Ver {@link SacOutlets}.
 *
 * <p>Turno de la IA, con un Divination suyo ya en el stack
 * ({@code aiputonstack}): la IA pasa y la prioridad llega al humano, que es
 * cuando se mira.
 */
public final class SacOutletCheck {

    private SacOutletCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String ALTAR = "Ashnod's Altar|Set:EMA";
    private static final String BEAR = "Grizzly Bears|Set:M10";
    private static final String PETAL = "Lotus Petal|Set:TMP";
    private static final String SWAMP = "Swamp|Set:M21";
    private static final String DIVINATION = "Divination"; // putonstack busca por nombre a secas

    private static final int SECONDS = 15;

    public static void run() {
        passed = 0;
        failed = 0;
        SafeActions.forceScanOnForTest();

        // 1. El del informe: altar y una criatura que sacrificar.
        play("altar y criatura", ALTAR + ";" + BEAR, true);
        check(SacOutlets.seen().contains("Ashnod's Altar"),
                "Ashnod's Altar cuenta como respuesta (" + SacOutlets.seen() + ")");

        // 2. Altar sin nada que sacrificar: no hay respuesta.
        play("altar sin criatura", ALTAR + ";" + SWAMP, true);
        check(SacOutlets.seen().isEmpty(),
                "sin criatura el altar no cuenta (" + SacOutlets.seen() + ")");

        // 3. Lotus Petal se sacrifica a si misma: eso no es responder.
        play("lotus petal", PETAL + ";" + BEAR, true);
        check(SacOutlets.seen().isEmpty(),
                "lo que se sacrifica a si mismo no cuenta (" + SacOutlets.seen() + ")");

        // 4. Sin nada en el stack: el pase sigue como siempre.
        play("stack vacio", ALTAR + ";" + BEAR, false);
        check(SacOutlets.seen().isEmpty(),
                "con el stack vacio no para (" + SacOutlets.seen() + ")");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del altar de sacrificio han fallado");
        }
    }

    private static void play(final String title, final String battlefield, final boolean stack) {
        System.out.println("  Mesa: " + title);
        SacOutlets.resetForTest();
        final List<String> state = new ArrayList<>(Arrays.asList(
                "turn=4", "activeplayer=ai", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + battlefield,
                "humanhand=",
                "humanlibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + SWAMP, "aihand=",
                "ailibrary=" + (SWAMP + ";").repeat(9) + SWAMP,
                "aigraveyard=", "aiexile=", "aicommand="));
        if (stack) {
            state.add("aiputonstack=" + DIVINATION);
        }
        final TutorialLesson lesson = new TutorialLesson("sacoutlet", state, List.of());
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
