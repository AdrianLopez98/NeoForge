package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>La prevision del pago, con la carta en la mano</b> ({@code run.cmd tapcheck}).
 * Ver {@link TapPreview}.
 *
 * <p>Una mesa con Plains, Island y Forest, y en la mano:
 *
 * <ul>
 *   <li><b>Grizzly Bears</b> ({1}{G}): dos fuentes, y una es el Forest.</li>
 *   <li><b>Counterspell</b> ({U}{U}) con una sola Island: no se puede pagar,
 *       sin prevision.</li>
 *   <li><b>Blaze</b> (X): la X no se sabe, sin prevision.</li>
 *   <li><b>Treasure Cruise</b> (escavar): pagar pregunta, sin prevision — y
 *       sobre todo <b>sin preguntar nada</b>, que es el bucle de Teval.</li>
 * </ul>
 */
public final class TapPreviewCheck {

    private TapPreviewCheck() {
    }

    public static void run() {
        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean finished = new AtomicBoolean();
        final List<String> bad = new ArrayList<>();
        final List<String> good = new ArrayList<>();

        final Thread driver = new Thread(() -> {
            try {
                Game game = null;
                Player me = null;
                for (int i = 0; i < 600 && me == null; i++) {
                    Thread.sleep(50L);
                    final NeoMatchUI ui = gui[0];
                    if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null
                            || !(ui.getGameController() instanceof PlayerControllerHuman c)) {
                        continue;
                    }
                    final Game g = ui.getGameView().getGame();
                    final Player p = human(g);
                    if (p != null && g.getPhaseHandler().isPlayerTurn(p)
                            && g.getPhaseHandler().getPhase() == PhaseType.MAIN1
                            && c.getInputQueue().getInput() instanceof InputPassPriority) {
                        game = g;
                        me = p;
                    }
                }
                if (me == null) {
                    bad.add("no se llego a la fase principal");
                    return;
                }
                Thread.sleep(500L);
                final Player self = me;
                final Game g = game;
                expect(bad, good, "Grizzly Bears", preview(gui[0], self, "Grizzly Bears"), 2, "Forest", g);
                expect(bad, good, "Counterspell", preview(gui[0], self, "Counterspell"), 0, null, g);
                expect(bad, good, "Blaze", preview(gui[0], self, "Blaze"), 0, null, g);
                expect(bad, good, "Treasure Cruise", preview(gui[0], self, "Treasure Cruise"), 0, null, g);
                // Y que nada de lo anterior haya dejado una pregunta abierta.
                final NeoMatchUI ui = gui[0];
                if (!(ui.getGameController() instanceof PlayerControllerHuman c
                        && c.getInputQueue().getInput() instanceof InputPassPriority)) {
                    bad.add("la prevision ha dejado una pregunta abierta");
                } else {
                    good.add("sin preguntas abiertas");
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RuntimeException e) {
                bad.add("excepcion en la prueba: " + e);
                e.printStackTrace();
            } finally {
                finished.set(true);
                final NeoMatchUI ui = gui[0];
                if (ui != null) {
                    ui.nudgeAutoPlay();
                }
            }
        }, "tapcheck");
        driver.setDaemon(true);

        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 300, null, false, ui -> {
            gui[0] = ui;
            ui.setAutoPlayHold(() -> !finished.get());
            driver.start();
        });

        good.forEach(n -> System.out.println("  bien: " + n));
        bad.forEach(n -> System.out.println("  MAL:  " + n));
        System.out.println("\n  " + good.size() + " bien, " + bad.size() + " mal");
        if (!bad.isEmpty()) {
            throw new IllegalStateException("la prevision del pago ha fallado");
        }
    }

    /** Lo que marcaria la mesa, calculado en el hilo de interfaz, como al pasar el raton. */
    private static Set<Integer> preview(final NeoMatchUI ui, final Player me, final String name) {
        final Card card = named(me, ZoneType.Hand, name);
        final AtomicReference<Set<Integer>> out = new AtomicReference<>(Set.of());
        GuiBase.getInterface().invokeInEdtAndWait(
                () -> out.set(TapPreview.sourcesFor(ui.getGameView(), card.getView())));
        return out.get();
    }

    private static void expect(final List<String> bad, final List<String> good, final String what,
                               final Set<Integer> ids, final int size, final String mustHave, final Game game) {
        final Set<String> names = new TreeSet<>();
        for (final int id : ids) {
            final Card c = game.findById(id);
            names.add(c == null ? "#" + id : c.getName());
        }
        final String line = what + " -> " + names;
        if (ids.size() != size || (mustHave != null && !names.contains(mustHave))) {
            bad.add(line + " (esperaba " + size + (mustHave == null ? "" : " con " + mustHave) + ")");
        } else {
            good.add(line);
        }
    }

    private static Card named(final Player me, final ZoneType zone, final String name) {
        for (final Card c : me.getCardsIn(zone)) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        throw new IllegalStateException("no esta " + name);
    }

    private static Player human(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=Plains;Island;Forest",
                "humanhand=Grizzly Bears;Counterspell;Blaze;Treasure Cruise",
                "humanlibrary=Island;Island;Island;Island;Island",
                "humangraveyard=Island;Island;Island;Island;Island;Island;Island;Island",
                "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=Island;Island;Island",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("tapcheck", state, List.of());
    }
}
