package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.neo.tutorial.TutorialState;

/**
 * <b>Has caido: terminar la partida</b> ({@code run.cmd outcheck}). Ver
 * {@code NeoMatchUI.offerEndWhenOut}.
 *
 * <p>Una mesa de Commander a cuatro en la que empiezas con 0 vidas: caes en la
 * primera comprobacion y las tres IA seguirian jugando entre ellas. Con
 * {@code -Dneo.out.autoEnd=true} (lo que contesta "Terminar la partida") la
 * partida tiene que cerrarse enseguida, como {@code AllHumansLost}, y no al
 * cabo de minutos de IA contra IA.
 */
public final class OutCheck {

    private static int passed;
    private static int failed;

    private OutCheck() {
    }

    public static void run() {
        final List<String> state = Arrays.asList(
                "turn=1",
                "activeplayer=p1",
                "activephase=MAIN1",
                "p0life=0", "p1life=40", "p2life=40", "p3life=40",
                "p0battlefield=Plains", "p0hand=", "p0library=Plains;Plains;Plains",
                "p1battlefield=Grizzly Bears", "p1hand=", "p1library=Plains;Plains;Plains;Plains;Plains;Plains",
                "p2battlefield=Grizzly Bears", "p2hand=", "p2library=Plains;Plains;Plains;Plains;Plains;Plains",
                "p3battlefield=Grizzly Bears", "p3hand=", "p3library=Plains;Plains;Plains;Plains;Plains;Plains");
        final TutorialState position = new TutorialState(state);

        final Game[] game = new Game[1];
        final NeoMatchUI gui = new NeoMatchUI(NeoMatchUI.Mode.AUTO_PLAY, false);
        final HostedMatch match = new HostedMatch();
        match.setStartGameHook(SafeAi.hook(match, () -> {
            game[0] = match.getGame();
            position.applyToGame(game[0]);
        }));

        final List<RegisteredPlayer> players = new ArrayList<>();
        final RegisteredPlayer human = new RegisteredPlayer(new Deck())
                .setPlayer(forge.neo.look.NeoPlayers.human());
        human.setStartingHand(0);
        players.add(human);
        for (int i = 0; i < 3; i++) {
            final RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                    .setPlayer(forge.neo.look.NeoPlayers.ai(i, null));
            ai.setStartingHand(0);
            players.add(ai);
        }
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setGamesPerMatch(1);

        System.setProperty("neo.out.autoEnd", "true");
        long took = -1;
        try {
            final long start = System.nanoTime();
            match.startMatch(rules, null, players, human, gui);
            final long until = start + TimeUnit.SECONDS.toNanos(60);
            while (System.nanoTime() < until && (game[0] == null || !game[0].isGameOver())) {
                Thread.sleep(200);
            }
            if (game[0] != null && game[0].isGameOver()) {
                took = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            System.clearProperty("neo.out.autoEnd");
        }

        final GameEndReason reason = game[0] == null || game[0].getOutcome() == null
                ? null : game[0].getOutcome().getWinCondition();
        System.out.println("  La partida se cierra en " + took + " s, por " + reason
                + (game[0] == null ? "" : ", en el turno " + game[0].getPhaseHandler().getTurn()));
        check("la partida se cierra (no sigue IA contra IA)", took >= 0);
        check("enseguida: en el turno 1 o 2", game[0] != null && game[0].getPhaseHandler().getTurn() <= 2);
        check("como AllHumansLost (lo que hace Forge al rendirse con IA en pie)",
                reason == GameEndReason.AllHumansLost);

        gui.shutdown();
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de terminar al caer han fallado");
        }
    }

    private static void check(final String what, final boolean ok) {
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
