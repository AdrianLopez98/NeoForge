package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.HostedMatch;
import forge.neo.tutorial.TutorialState;

/**
 * <b>"Uno por jugador" tambien cuando elige la IA</b> ({@code run.cmd
 * foreachcheck}). Ver {@link ForEachPlayerTargets}.
 *
 * <p>Le toca a la IA (p1) en su segunda fase principal, con Nils, Discipline
 * Enforcer y dos Grizzly Bears; tu (p0) tienes otro. Al final de su turno Nils
 * pone un contador "para cada jugador, en hasta una criatura que ese jugador
 * controle": entre las de la IA tiene que haber UNO como mucho. Sin el arreglo
 * ({@code -Dneo.ai.forEachPlayerFix=false}) se ponia dos (Discord, 03-11-2026).
 */
public final class ForEachCheck {

    private static int passed;
    private static int failed;

    private ForEachCheck() {
    }

    public static void run() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=p1",
                "activephase=MAIN2",
                "p0life=20", "p1life=20",
                "p0battlefield=Grizzly Bears",
                "p0hand=", "p0library=Plains;Plains;Plains;Plains;Plains",
                "p1battlefield=Nils, Discipline Enforcer;Grizzly Bears;Grizzly Bears",
                "p1hand=", "p1library=Plains;Plains;Plains;Plains;Plains");
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
        final RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                .setPlayer(forge.neo.look.NeoPlayers.ai(0, null));
        ai.setStartingHand(0);
        players.add(ai);

        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setGamesPerMatch(1);

        int mine = -1;
        int yours = -1;
        try {
            match.startMatch(rules, null, players, human, gui);
            // Hasta que empiece el turno siguiente (el tuyo): el disparo ya se ha resuelto.
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (System.nanoTime() < until
                    && (game[0] == null || game[0].getPhaseHandler().getTurn() < 4)) {
                Thread.sleep(200);
            }
            if (game[0] != null && game[0].getPhaseHandler().getTurn() >= 4) {
                final List<Player> ps = game[0].getPlayers();
                mine = withCounter(ps.get(1));
                yours = withCounter(ps.get(0));
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("  Criaturas de la IA con contador: " + mine + "; tuyas: " + yours);
        check("la partida llega al turno siguiente (el disparo se ha resuelto)", mine >= 0);
        check("Nils de la IA pone UN contador como mucho en sus criaturas (" + mine + ")", mine >= 0 && mine <= 1);
        check("y lo usa (al menos uno)", mine >= 1);

        gui.shutdown();
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de uno por jugador han fallado");
        }
    }

    private static int withCounter(final Player p) {
        int n = 0;
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isCreature() && c.getCounters(CounterEnumType.P1P1) > 0) {
                n++;
            }
        }
        return n;
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
