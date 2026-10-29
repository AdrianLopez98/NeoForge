package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * Elegir X hasta lo que puedes pagar ({@link AffordableX}), sin ventana.
 *
 * <p>Cada mesa: tu fase principal, el hechizo en la mano y unas tierras. Con la
 * partida parada esperando al jugador, se pide la pista como la pediria
 * {@code SafeActions.Guarded} y se mira el numero:
 *
 * <ul>
 *   <li>Hurricane ({X}{G}) con cuatro Bosques: X = 3.</li>
 *   <li>Crackle with Power ({X}{X}{X}{R}{R}) con ocho Montanas: X = 2 — la X
 *       va por TRES, no por una.</li>
 *   <li>Hurricane sin tierras: X = 0, y la lista sigue preguntando (con un
 *       corte por debajo del minimo, Forge contestaria sin preguntar).</li>
 *   <li>Grizzly Bears: no tiene X, no hay pista y la lista es la de Forge.</li>
 *   <li>Escavar (Treasure Cruise): pagar pregunta, asi que la prueba de la IA
 *       NO se hace - le preguntaba al jugador una vez por cada X probada
 *       (Discord, 29-10-2026, Teval y Exsanguinate). Y Hurricane si se hace.</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd affordablexcheck}.
 */
public final class AffordableXCheck {

    private AffordableXCheck() {
    }

    private static int passed;
    private static int failed;
    /** Si la ultima mesa se llego a mirar (sin esto, "sin pista" no prueba nada). */
    private static boolean looked;
    /** {@link AffordableX#asksWhilePaying} del hechizo de la ultima mesa. */
    private static boolean asks;

    private static final String HURRICANE = "Hurricane|Set:10E";
    private static final String CRACKLE = "Crackle with Power|Set:WAR";
    private static final String BEARS = "Grizzly Bears|Set:6ED";
    private static final String CRUISE = "Treasure Cruise|Set:KTK";
    private static final String FOREST = "Forest|Set:M21";
    private static final String MOUNTAIN = "Mountain|Set:M21";

    private static final int SECONDS = 20;

    public static void run() {
        passed = 0;
        failed = 0;

        Integer h = probe("Hurricane, 4 Bosques", HURRICANE, (FOREST + ";").repeat(3) + FOREST);
        check(Integer.valueOf(3).equals(h), "Hurricane con 4 Bosques: hasta X = 3 (" + h + ")");
        check(cut(h, 0) == 3, "la lista llega hasta el 3 y sigue el \"Otro...\" (" + cut(h, 0) + ")");

        h = probe("Crackle, 8 Montanas", CRACKLE, (MOUNTAIN + ";").repeat(7) + MOUNTAIN);
        check(Integer.valueOf(2).equals(h), "Crackle with Power (XXXRR) con 8 Montanas: hasta X = 2 (" + h + ")");

        h = probe("Hurricane sin tierras", HURRICANE, "");
        check(Integer.valueOf(0).equals(h), "Hurricane sin tierras: X = 0 (" + h + ")");
        check(cut(h, 1) == 1, "con minimo 1 el corte no baja del minimo (" + cut(h, 1) + ")");

        h = probe("Grizzly Bears", BEARS, (FOREST + ";").repeat(3) + FOREST);
        check(looked && h == null, "sin X no hay pista (" + h + ", mirada: " + looked + ")");
        check(AffordableX.cutoff(0, Integer.MAX_VALUE, 9) == 9, "sin pista, el corte de Forge");

        probe("Treasure Cruise (escavar)", CRUISE, FOREST);
        check(looked && asks, "escavar pregunta al pagar: sin prueba de la IA (mirada: " + looked + ")");
        probe("Hurricane otra vez", HURRICANE, FOREST);
        check(looked && !asks, "Hurricane no pregunta al pagar: con prueba (mirada: " + looked + ")");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la X han fallado");
        }
    }

    /** El corte que saldria con esa pista puesta. */
    private static int cut(final Integer hint, final int min) {
        if (hint == null) {
            return -1;
        }
        AffordableX.setForTest(hint);
        try {
            return AffordableX.cutoff(min, Integer.MAX_VALUE, min + 9);
        } finally {
            AffordableX.clear();
        }
    }

    /**
     * Monta la mesa y, con la partida esperando al jugador, pide la pista
     * para el primer hechizo de la mano. Devuelve la pista, o null.
     */
    private static Integer probe(final String title, final String hand, final String battlefield) {
        System.out.println("  Mesa: " + title);
        final AtomicReference<Integer> out = new AtomicReference<>();
        final AtomicReference<Boolean> done = new AtomicReference<>(false);
        final TutorialLesson lesson = new TutorialLesson("affordablex", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + battlefield,
                "humanhand=" + hand,
                "humanlibrary=" + (FOREST + ";").repeat(6) + FOREST,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + FOREST, "aihand=",
                "ailibrary=" + (FOREST + ";").repeat(6) + FOREST,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.HUMAN, SECONDS, null, false, gui -> {
                    final Thread t = new Thread(() -> {
                        final long until = System.currentTimeMillis() + (SECONDS - 5) * 1000L;
                        while (System.currentTimeMillis() < until && !done.get()) {
                            try {
                                Thread.sleep(300);
                                if (gui.getGameController() == null) {
                                    continue;
                                }
                                final PlayerControllerHuman pc =
                                        (PlayerControllerHuman) NeoMatchUI.seated(gui.getGameController());
                                final Player p = pc.getPlayer();
                                // El turno del MOTOR: el GameView de la interfaz llega por
                                // lotes y puede seguir diciendo "turno 1".
                                if (p.getGame().getPhaseHandler().getTurn() < 3) {
                                    continue;
                                }
                                if (!p.getGame().getPhaseHandler().getPhase().isMain()
                                        || p.getCardsIn(ZoneType.Hand).isEmpty()) {
                                    System.out.println("  (aun no: " + p.getGame().getPhaseHandler().getPhase()
                                            + ", mano " + p.getCardsIn(ZoneType.Hand).size() + ")");
                                    continue;
                                }
                                final Card c = p.getCardsIn(ZoneType.Hand).get(0);
                                final SpellAbility sa = c.getFirstSpellAbility();
                                sa.setActivatingPlayer(p);
                                asks = AffordableX.asksWhilePaying(sa);
                                AffordableX.prepare(sa, p);
                                out.set(AffordableX.hintForTest());
                                AffordableX.clear();
                                done.set(true);
                            } catch (final InterruptedException e) {
                                return;
                            } catch (final RuntimeException e) {
                                // La partida aun no esta lista: otra vuelta.
                                System.out.println("  (aun no: " + e + ")");
                            }
                        }
                    }, "affordablex-probe");
                    t.setDaemon(true);
                    t.start();
                });
        looked = done.get();
        if (!looked) {
            System.out.println("  (no se llego a mirar la mesa)");
        }
        return out.get();
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
