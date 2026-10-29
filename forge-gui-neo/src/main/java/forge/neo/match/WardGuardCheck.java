package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Que {@link WardGuard} vea el Ward de una carta de la mesa ({@code run.cmd wardcheck}).
 *
 * <p>El Kappa Cannoneer del informe (Ward {4}) tiene que dar "Ward {4}", y unos
 * Grizzly Bears nada. Se lee del {@code CardView} de una partida de verdad, que
 * es lo que tiene la mesa al clicar.
 */
public final class WardGuardCheck {

    private WardGuardCheck() {
    }

    public static void run() {
        final AtomicReference<String> kappa = new AtomicReference<>("(sin mirar)");
        final AtomicReference<String> bears = new AtomicReference<>("(sin mirar)");
        final TutorialLesson lesson = new TutorialLesson("ward", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=Mountain", "humanhand=Shock",
                "humanlibrary=Island;Island;Island;Island;Island",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Kappa Cannoneer;Grizzly Bears", "aihand=",
                "ailibrary=Island;Island;Island",
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 20,
                null, false, ui -> {
                    final Thread t = new Thread(() -> {
                        for (int i = 0; i < 200; i++) {
                            try {
                                Thread.sleep(50L);
                            } catch (final InterruptedException e) {
                                return;
                            }
                            if (ui.getGameView() == null || ui.getGameView().getGame() == null) {
                                continue;
                            }
                            final Game g = ui.getGameView().getGame();
                            for (final Player p : g.getPlayers()) {
                                for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
                                    if (c.getName().equals("Kappa Cannoneer")) {
                                        kappa.set(WardGuard.wardOf(c.getView()));
                                    } else if (c.getName().equals("Grizzly Bears")) {
                                        bears.set(WardGuard.wardOf(c.getView()));
                                    }
                                }
                            }
                            // Las DOS: el puzzle pone las cartas una a una, y cortar
                            // al ver la primera dejaba la otra "sin mirar".
                            if (!"(sin mirar)".equals(kappa.get()) && !"(sin mirar)".equals(bears.get())) {
                                return;
                            }
                        }
                    }, "wardcheck");
                    t.setDaemon(true);
                    t.start();
                });
        final boolean ok = "Ward {4}".equals(kappa.get()) && bears.get() == null;
        System.out.printf(Locale.ROOT, "  Kappa Cannoneer: %s   Grizzly Bears: %s%n", kappa.get(), bears.get());
        System.out.println(ok ? "\n  1 bien, 0 mal" : "\n  0 bien, 1 mal");
        if (!ok) {
            throw new IllegalStateException("WardGuard no ve el Ward");
        }
    }
}
