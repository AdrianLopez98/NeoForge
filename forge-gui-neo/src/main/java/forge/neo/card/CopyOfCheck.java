package forge.neo.card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * {@code run.cmd copycheck}: una copia en la mesa dice que carta hay debajo.
 * Ver {@link CopyOf}.
 *
 * <p>Grizzly Bears y Llanowar Elves en la mesa, y Mirrorweave "ya lanzado"
 * sobre los Bears: los Elves pasan a ser copia de los Bears hasta el final del
 * turno. Tiene que haber dos Grizzly Bears, y de una {@code CopyOf} tiene que
 * decir que debajo es Llanowar Elves; de la otra, nada. (Sculpting Steel no
 * vale para la prueba: {@code humanprecast} lo pone en la mesa sin su "entra
 * como copia", y el piloto automatico no lo lanza.)
 */
public final class CopyOfCheck {

    private CopyOfCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final List<String> found = new ArrayList<>();
        final int[] solRings = {0};
        final java.util.function.Consumer<NeoMatchUI> spyOn = ui -> {
            final Thread spy = new Thread(() -> {
                for (int i = 0; i < 300; i++) {
                    try {
                        Thread.sleep(50);
                        final forge.game.GameView gv = ui.getGameView();
                        if (gv == null || gv.getPlayers() == null) {
                            continue;
                        }
                        int rings = 0;
                        final List<String> now = new ArrayList<>();
                        for (final PlayerView p : gv.getPlayers()) {
                            if (p.getBattlefield() == null) {
                                continue;
                            }
                            for (final CardView c : p.getBattlefield()) {
                                if (c.getCurrentState() != null
                                        && "Grizzly Bears".equals(c.getCurrentState().getName())) {
                                    rings++;
                                    final CardView real = CopyOf.original(c);
                                    if (real != null) {
                                        now.add(real.getCurrentState().getName());
                                    }
                                }
                            }
                        }
                        if (i % 40 == 0) {
                            final List<String> seen = new ArrayList<>();
                            for (final PlayerView p : gv.getPlayers()) {
                                if (p.getBattlefield() != null) {
                                    for (final CardView c : p.getBattlefield()) {
                                        seen.add(c.getName() + (c.isCloned() ? "*" : ""));
                                    }
                                }
                            }
                            System.out.println("  (mesa: " + seen + ")");
                        }
                        if (rings >= 2) {
                            solRings[0] = rings;
                            found.clear();
                            found.addAll(now);
                            return;
                        }
                    } catch (final InterruptedException e) {
                        return;
                    } catch (final RuntimeException e) {
                        // la mesa aun se esta montando
                        if (i % 40 == 0) {
                            System.out.println("  (espiando: " + e + ")");
                        }
                    }
                }
            }, "neo-copycheck-spy");
            spy.setDaemon(true);
            spy.start();
        };
        final List<String> state = new ArrayList<>(Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=Grizzly Bears|Id:501;Llanowar Elves|Id:502",
                "humanhand=", "humanlibrary=Island;Island;Island;Island;Island",
                "humanprecast=Mirrorweave->501",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=", "ailibrary=Island;Island;Island;Island;Island",
                "aigraveyard=", "aiexile=", "aicommand="));
        final TutorialLesson lesson = new TutorialLesson("copyof", state, List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 15, null, false, spyOn);

        check(solRings[0] >= 2, "Mirrorweave convierte a Llanowar Elves en copia de Grizzly Bears (" + solRings[0] + " Grizzly Bears)");
        check(found.size() == 1 && found.get(0).equals("Llanowar Elves"),
                "de una, y solo de una, se sabe que debajo es Llanowar Elves " + found);

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las copias han fallado");
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
