package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Las habilidades que solo valen en una fase, sin ventana (ver
 * {@link PhaseOnlyAbilities}).
 *
 * <p>Las tres en el turno de la IA, que ataca con unos Grizzly Bears:
 *
 * <ul>
 *   <li><b>La del informe</b>: tienes un Desert sin girar. Hay que pararse en
 *       el final del combate, que no es parada de fabrica.</li>
 *   <li><b>Desert girado</b>: no se puede activar, no se para.</li>
 *   <li><b>Sin Desert</b>: una Llanura. No se para.</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd phasestopcheck}.
 */
public final class PhaseStopCheck {

    private PhaseStopCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String DESERT = "Desert";
    private static final String PLAINS = "Plains|Set:M21";
    private static final String BEARS = "Grizzly Bears|Set:6ED";
    private static final String ISLAND = "Island|Set:M21";
    private static final String SWAMP = "Swamp|Set:M21";
    private static final String BALL = "Ball Lightning|Set:M10";

    /** El combate de la mesa preparada: el turno 3, de la IA. */
    private static final String FIRST_COMBAT = "T3:COMBAT_END:Desert";

    /** El turno de la IA entero, hasta pasado su combate. */
    private static final int SECONDS = 25;

    public static void run() {
        passed = 0;
        failed = 0;

        play("informe", DESERT);
        List<String> seen = PhaseOnlyAbilities.seenForTest();
        check(seen.contains(FIRST_COMBAT),
                "con Desert sin girar se para en el final del combate del rival " + seen);

        play("desert girado", DESERT + "|Tapped");
        seen = PhaseOnlyAbilities.seenForTest();
        // Solo el primer combate: en el turno siguiente el Desert se endereza.
        check(!seen.contains(FIRST_COMBAT), "con el Desert girado no se para " + seen);

        play("sin desert", PLAINS);
        seen = PhaseOnlyAbilities.seenForTest();
        check(seen.isEmpty(), "sin Desert no se para " + seen);

        // Ebondeath (Discord, 28-10-2026): lanzable desde el cementerio cuando
        // muere algo. Una criatura del rival muere en SU turno (Ball Lightning se
        // sacrifica en el paso final): se para una vez. Tiene que morir A MITAD
        // de turno: muerta al montar la mesa, ya era lanzable desde el principio.
        final String swamps = (SWAMP + ";").repeat(3) + SWAMP;
        ebondeath("ebondeath, muere una criatura", swamps, BALL + ";" + ISLAND);
        java.util.List<String> stops = new java.util.ArrayList<>(NewlyCastable.STOPS);
        check(stops.stream().anyMatch(x -> x.startsWith("T3:") && x.endsWith(":Ebondeath, Dracolich")),
                "Ebondeath en el cementerio y muere una criatura en el turno del rival: se para " + stops);
        ebondeath("ebondeath, no muere nada", swamps, BEARS + ";" + ISLAND);
        stops = new java.util.ArrayList<>(NewlyCastable.STOPS);
        check(stops.stream().noneMatch(x -> x.startsWith("T3:")), "si no muere nada, no se para " + stops);
        ebondeath("ebondeath, sin mana", PLAINS, BALL + ";" + ISLAND);
        stops = new java.util.ArrayList<>(NewlyCastable.STOPS);
        check(stops.stream().noneMatch(x -> x.startsWith("T3:")), "sin con que pagarlo, no se para " + stops);

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las paradas por fase han fallado");
        }
    }

    private static void play(final String title, final String humanLand) {
        System.out.println("  Mesa: " + title);
        PhaseOnlyAbilities.resetForTest();
        final TutorialLesson lesson = new TutorialLesson("phasestop", Arrays.asList(
                "turn=3", "activeplayer=ai", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + humanLand,
                "humanhand=",
                "humanlibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + BEARS + ";" + ISLAND, "aihand=",
                "ailibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, SECONDS, null, false, null);
    }

    private static void ebondeath(final String title, final String humanLands, final String aiField) {
        System.out.println("  Mesa: " + title);
        NewlyCastable.STOPS.clear();
        // Las paradas de fabrica en los turnos del rival (sin el paso final):
        // si otra prueba dejo guardado alli una parada, ya tendrias prioridad.
        final String before = forge.neo.NeoSettings.get(forge.neo.NeoSettings.PHASE_STOPS_THEIRS, null);
        forge.neo.NeoSettings.set(forge.neo.NeoSettings.PHASE_STOPS_THEIRS,
                "MAIN1,MAIN2,COMBAT_DECLARE_ATTACKERS,COMBAT_DECLARE_BLOCKERS");
        try {
            playEbondeath(humanLands, aiField);
        } finally {
            forge.neo.NeoSettings.set(forge.neo.NeoSettings.PHASE_STOPS_THEIRS, before);
        }
    }

    private static void playEbondeath(final String humanLands, final String aiField) {
        final TutorialLesson lesson = new TutorialLesson("phasestop", Arrays.asList(
                "turn=3", "activeplayer=ai", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + humanLands,
                "humanhand=",
                "humanlibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "humangraveyard=Ebondeath, Dracolich|Set:AFR", "humanexile=", "humancommand=",
                "aibattlefield=" + aiField, "aihand=",
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
