package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * El auto-pass con Nyx Lotus, sin ventana (ver {@link HiddenMana}).
 *
 * <p>Tres mesas, las tres con el Lotus enderezado y sin tierras:
 *
 * <ul>
 *   <li><b>La del informe</b>: Gisa en la mesa (devoción a negro 2) y Feed the
 *       Swarm en la mano, con una criatura rival a la que apuntar. El motor
 *       tiene que decir "no hay acciones" — si no lo dice, Card-Forge ha
 *       arreglado su estimación y esto sobra — y nosotros tenemos que parar.</li>
 *   <li><b>Devoción cero</b> (un Ornithopter en lugar de Gisa): el Lotus no da
 *       nada, así que no hay nada oculto y no se para. Es lo que evita que un
 *       Lotus en la mesa te pare en cada prioridad del rival.</li>
 *   <li><b>Demasiado caro</b> (Griselbrand en la mano): el Lotus se ve, pero
 *       con dos de maná no llega, así que tampoco se para.</li>
 * </ul>
 *
 * <p>Y dos con <b>Lion's Eye Diamond</b>, que el motor no cuenta como fuente
 * (itch.io, 15-10-2026), sin tierras:
 * <ul>
 *   <li><b>La del informe</b>: LED y Underworld Breach en la mesa, Brain Freeze
 *       y cartas de sobra en el cementerio, la mano vacia. Con el LED se escapa
 *       el Brain Freeze; el motor dice que no hay nada y nosotros paramos.</li>
 *   <li><b>LED sin nada</b>: el LED solo, sin nada que lanzar en ninguna parte.
 *       No se para: un LED en la mesa no puede frenarte en cada prioridad.</li>
 * </ul>
 * <p>Se mira el <b>primer</b> veredicto de cada mesa, que es la primera
 * prioridad del jugador: lo que pase después depende de lo que robe y juegue
 * el piloto automático, y eso no es lo que se prueba.
 *
 * <p>Se ejecuta con {@code run.cmd devotioncheck}.
 */
public final class DevotionCheck {

    private DevotionCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String LOTUS = "Nyx Lotus|Set:THB";
    private static final String GISA = "Gisa, Glorious Resurrector|Set:MID";
    private static final String ORNITHOPTER = "Ornithopter|Set:AER";
    private static final String FEED = "Feed the Swarm|Set:C21";
    private static final String GRISELBRAND = "Griselbrand|Set:AVR";
    private static final String BEARS = "Grizzly Bears|Set:6ED";
    private static final String SWAMP = "Swamp|Set:M21";
    private static final String LED = "Lion's Eye Diamond|Set:MIR";
    private static final String BREACH = "Underworld Breach|Set:THB";
    private static final String BRAIN_FREEZE = "Brain Freeze|Set:SCG";
    private static final String BLACK_LOTUS = "Black Lotus|Set:LEA";
    private static final String ANCESTRAL = "Ancestral Recall|Set:LEA";
    private static final String TUTOR = "Demonic Tutor|Set:LEA";

    /** Lo que dura cada mesa: basta con la primera prioridad. */
    private static final int SECONDS = 15;

    public static void run() {
        passed = 0;
        failed = 0;
        SafeActions.forceScanOnForTest();

        // 1. La del informe.
        play("informe", GISA, FEED, true);
        final HiddenMana.Verdict report = HiddenMana.first();
        final Boolean engine = HiddenMana.engineSawActions();
        check(report != null && report.hidden().stream().anyMatch(s -> s.startsWith("Nyx Lotus")),
                "se ve el Nyx Lotus como fuente oculta (" + report + ")");
        check(report != null && report.holds() && "Feed the Swarm".equals(report.reason()),
                "y se para por Feed the Swarm");
        if (Boolean.TRUE.equals(engine)) {
            System.out.println("  NOTA el motor YA ve la accion: Card-Forge ha arreglado la "
                    + "estimacion del Lotus y HiddenMana sobra. Mirar antes de quitarlo");
        } else {
            check(Boolean.FALSE.equals(engine),
                    "el motor, en cambio, dice que no hay acciones (el hueco existe: " + engine + ")");
        }

        // 2. Devocion cero: nada que ocultar.
        play("devocion cero", ORNITHOPTER, FEED, false);
        check(HiddenMana.first() == null && HiddenMana.holds() == 0,
                "con devocion cero no hay fuente oculta ni parada (" + HiddenMana.first() + ")");

        // 3. Demasiado caro.
        play("caro", GISA, GRISELBRAND, false);
        final HiddenMana.Verdict dear = HiddenMana.first();
        check(dear != null && !dear.holds(),
                "con un hechizo de 8 el Lotus se ve pero no se para (" + dear + ")");

        // 4. Lion's Eye Diamond con Underworld Breach: el informe.
        table("LED y Underworld Breach", LED + ";" + BREACH, "",
                BRAIN_FREEZE + ";" + (SWAMP + ";").repeat(4) + SWAMP, true);
        final HiddenMana.Verdict led = HiddenMana.first();
        final Boolean ledEngine = HiddenMana.engineSawActions();
        check(led != null && led.hidden().stream().anyMatch(s -> s.startsWith("Lion's Eye Diamond")),
                "se ve el LED como fuente oculta (" + led + ")");
        check(led != null && led.holds() && "Brain Freeze".equals(led.reason()),
                "y se para por el Brain Freeze del cementerio");
        if (Boolean.TRUE.equals(ledEngine)) {
            System.out.println("  NOTA el motor YA cuenta el LED: la IA lo ha aprendido y esta parte de "
                    + "HiddenMana sobra. Mirar antes de quitarla");
        } else {
            check(Boolean.FALSE.equals(ledEngine),
                    "el motor, en cambio, dice que no hay acciones (" + ledEngine + ")");
        }

        // 5. El LED solo, sin nada que lanzar: no se para.
        table("LED sin nada que lanzar", LED, "", "", false);
        final HiddenMana.Verdict alone = HiddenMana.first();
        check(alone != null && !alone.holds(), "un LED sin nada que lanzar no te para (" + alone + ")");

        // 6. Black Lotus sin tierras, con hechizos baratos (Discord,
        // 07-11-2026): la IA "no gasta un Lotus en algo de menos de 4"
        // (AILogic$ BlackLotus) y esa estimacion decidia que no podias pagar
        // Ancestral Recall ni Demonic Tutor: pasaba sola y perdias el turno.
        table("Black Lotus con hechizos baratos", BLACK_LOTUS, ANCESTRAL + ";" + TUTOR, "", true);
        final HiddenMana.Verdict lotus = HiddenMana.first();
        final Boolean lotusEngine = HiddenMana.engineSawActions();
        check(lotus != null && lotus.hidden().stream().anyMatch(s -> s.startsWith("Black Lotus")),
                "se ve el Black Lotus como fuente que el motor infravalora (" + lotus + ")");
        check(lotus != null && lotus.holds(), "y se para: hay algo que lanzar con su mana");
        if (Boolean.TRUE.equals(lotusEngine)) {
            System.out.println("  NOTA el motor YA ve la accion con el Black Lotus: esta parte de HiddenMana sobra");
        } else {
            check(Boolean.FALSE.equals(lotusEngine),
                    "el motor, en cambio, dice que no hay acciones (" + lotusEngine + ")");
        }

        // 7. Not of This World (Discord, 08-11-2026, repeticion de Ascenso):
        // gratis si apunta a algo que apunta a tu criatura de fuerza 7+, pero
        // el motor la cuenta SIN objetivo, a {7}, y pasaba solo mientras el
        // rival le quitaba el comandante al jugador (TargetedRebate).
        stack("Unlicensed Disintegration contra Zhulodok", "Not of This World|Set:ROE");
        final TargetedRebate.Verdict rebate = TargetedRebate.first();
        check(rebate != null && rebate.holds() && TargetedRebate.holds() > 0,
                "con Not of This World en la mano, el removal del rival te para (" + rebate + ")");
        stack("el mismo removal, sin nada que responder", "");
        check(TargetedRebate.first() == null && TargetedRebate.holds() == 0,
                "sin la carta no hay nada que parar (" + TargetedRebate.first() + ")");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del mana oculto han fallado");
        }
    }

    private static void play(final String title, final String permanent, final String inHand,
                             final boolean probeEngine) {
        System.out.println("  Mesa: " + title);
        HiddenMana.resetForTest(probeEngine);
        final TutorialLesson lesson = new TutorialLesson("devotion", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + LOTUS + ";" + permanent,
                "humanhand=" + inHand,
                "humanlibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + BEARS, "aihand=",
                "ailibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, SECONDS, null, false, null);
    }

    /** El rival con un removal en el stack contra la criatura de fuerza 7 del jugador. */
    private static void stack(final String title, final String hand) {
        System.out.println("  Mesa: " + title);
        HiddenMana.resetForTest(false);
        TargetedRebate.resetForTest();
        final TutorialLesson lesson = new TutorialLesson("devotion", Arrays.asList(
                "turn=3", "activeplayer=ai", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=Zhulodok, Void Gorger|Id:64",
                "humanhand=" + hand,
                "humanlibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + BEARS, "aihand=",
                "ailibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "aigraveyard=", "aiexile=", "aicommand=",
                "aiputonstack=Unlicensed Disintegration->64"), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, SECONDS, null, false, null);
    }

    /** Una mesa sin el Lotus: lo que haya en la mesa, la mano y el cementerio. */
    private static void table(final String title, final String battlefield, final String hand,
                              final String graveyard, final boolean probeEngine) {
        System.out.println("  Mesa: " + title);
        HiddenMana.resetForTest(probeEngine);
        final TutorialLesson lesson = new TutorialLesson("devotion", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + battlefield,
                "humanhand=" + hand,
                "humanlibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "humangraveyard=" + graveyard, "humanexile=", "humancommand=",
                "aibattlefield=" + BEARS, "aihand=",
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
