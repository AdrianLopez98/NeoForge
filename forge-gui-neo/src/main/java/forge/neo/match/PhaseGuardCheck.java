package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.phase.PhaseType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * La pregunta de dejar tu fase principal leida del motor ({@link PhaseGuard},
 * la de Android), sin ventana. Tu fase principal 1 con algo que lanzar, y con
 * la partida esperando tu prioridad:
 *
 * <ul>
 *   <li>modo 0: no pregunta nada;</li>
 *   <li>modo 1: el OK pregunta "¿ir al combate?" y "Fin de turno" pregunta;</li>
 *   <li>modo 2: lo mismo.</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd phaseguardcheck}.
 */
public final class PhaseGuardCheck {

    private PhaseGuardCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String ISLAND = "Island|Set:M21";
    private static final int SECONDS = 20;

    public static void run() {
        passed = 0;
        failed = 0;
        System.out.println("  Mesa: tu fase principal 1, con un Ornithopter en la mano");
        final AtomicReference<String> out = new AtomicReference<>();
        final TutorialLesson lesson = new TutorialLesson("phaseguard", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + ISLAND,
                // Algo que lanzar, o el pase automatico se lleva la fase.
                "humanhand=Ornithopter|Set:M15",
                "humanlibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + ISLAND, "aihand=",
                "ailibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.HUMAN, SECONDS, null, false, gui -> {
                    final Thread t = new Thread(() -> {
                        final long until = System.currentTimeMillis() + (SECONDS - 5) * 1000L;
                        while (System.currentTimeMillis() < until && out.get() == null) {
                            try {
                                Thread.sleep(300);
                                final forge.interfaces.IGameController gc = gui.getGameController();
                                if (gc == null) {
                                    continue;
                                }
                                final PlayerControllerHuman human = (PlayerControllerHuman) NeoMatchUI.seated(gc);
                                if (human.getGame().getPhaseHandler().getTurn() < 3
                                        || human.getGame().getPhaseHandler().getPhase() != PhaseType.MAIN1
                                        || !(human.getInputQueue().getInput()
                                                instanceof forge.gamemodes.match.input.InputPassPriority)) {
                                    continue;
                                }
                                out.set(PhaseGuard.leavingMain(gc, 0) + " " + PhaseGuard.leavingMain(gc, 1) + " "
                                        + PhaseGuard.leavingMain(gc, 2) + " " + PhaseGuard.endingTurn(gc, 0) + " "
                                        + PhaseGuard.endingTurn(gc, 1));
                            } catch (final InterruptedException e) {
                                return;
                            } catch (final RuntimeException e) {
                                // La partida aun no esta lista: otra vuelta.
                            }
                        }
                    }, "phaseguard-probe");
                    t.setDaemon(true);
                    t.start();
                });
        final String r = out.get();
        check(r != null, "se llego a mirar la mesa (" + r + ")");
        check("NONE TO_COMBAT TO_COMBAT false true".equals(r),
                "modo 0 no pregunta; 1 y 2 preguntan al ir al combate; Fin de turno pregunta con 1");
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la fase principal han fallado");
        }
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
