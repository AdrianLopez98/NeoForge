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
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.neo.tutorial.TutorialState;

/**
 * <b>"Agresividad de la IA contigo"</b> ({@code run.cmd friendlycheck}). Ver
 * {@link FriendlyAttacks}.
 *
 * <p>Una mesa de Commander a cuatro, preparada: le toca a una IA (p1) con tres
 * criaturas imbloqueables; tu (p0) tienes la mesa mas amenazante (ocho
 * artefactos), otra IA (p2) tiene la mitad, y la ultima (p3) nada. Se mira:
 * <ul>
 *   <li>con "Normal" y con "Baja" eres claramente la mayor amenaza: no se te
 *       aparta (eso es lo que se pidio: "si soy claramente la mayor amenaza,
 *       que vengan a por mi");</li>
 *   <li>con "Muy baja" (tu amenaza al 35 %) si: p2 pasa a ser la mayor;</li>
 *   <li>y en la partida de verdad, con "Muy baja", la IA ataca a p2 y no a ti.</li>
 * </ul>
 */
public final class FriendlyCheck {

    private static int passed;
    private static int failed;

    private FriendlyCheck() {
    }

    public static void run() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=p1",
                "activephase=MAIN1",
                "p0life=40", "p1life=40", "p2life=40", "p3life=40",
                "p0battlefield=Darksteel Ingot;Darksteel Ingot;Darksteel Ingot;Darksteel Ingot;"
                        + "Darksteel Ingot;Darksteel Ingot;Darksteel Ingot;Darksteel Ingot",
                "p0hand=", "p0library=Plains;Plains;Plains;Plains;Plains",
                "p1battlefield=Phantom Warrior;Phantom Warrior;Phantom Warrior",
                "p1hand=", "p1library=Island;Island;Island;Island;Island",
                "p2battlefield=Darksteel Ingot;Darksteel Ingot;Darksteel Ingot;Darksteel Ingot",
                "p2hand=", "p2library=Plains;Plains;Plains;Plains;Plains",
                "p3battlefield=Plains",
                "p3hand=", "p3library=Plains;Plains;Plains;Plains;Plains");
        final TutorialState position = new TutorialState(state);

        final String[] decided = new String[3];
        final String[] names = new String[4];
        final NeoMatchUI gui = new NeoMatchUI(NeoMatchUI.Mode.AUTO_PLAY, false);
        final HostedMatch match = new HostedMatch();
        match.setStartGameHook(SafeAi.hook(match, () -> {
            final Game game = match.getGame();
            position.applyToGame(game);
            final List<Player> ps = game.getPlayers();
            for (int i = 0; i < Math.min(4, ps.size()); i++) {
                names[i] = ps.get(i).getName();
            }
            if (ps.size() >= 4) {
                for (int level = 0; level <= 2; level++) {
                    final List<Player> spared = FriendlyAttacks.playersToSpare(ps.get(1), level);
                    decided[level] = spared.contains(ps.get(0)) ? "spared" : "attackable";
                }
            }
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

        final GameRules rules = new GameRules(GameType.Commander);
        rules.setGamesPerMatch(1);

        // La partida libre de Commander lo arma igual (NeoApp.startLiveGame).
        FriendlyAttacks.arm(FriendlyAttacks.VERY_LOW);
        final int sparedBefore = FriendlyAttacks.sparedCount();
        try {
            match.startMatch(rules, null, players, human, gui);
            // Hasta que la IA de p1 declare su ataque.
            final long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (System.nanoTime() < until
                    && (names[1] == null || !FriendlyAttacks.lastAttacked().startsWith(names[1] + "->"))) {
                Thread.sleep(200);
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            FriendlyAttacks.disarm();
        }

        System.out.println("  Players: " + String.join(", ", Arrays.asList(names)));
        System.out.println("  Decisions: Normal=" + decided[0] + ", Low=" + decided[1] + ", Very low=" + decided[2]);
        System.out.println("  Attack: " + FriendlyAttacks.lastAttacked());

        check("con \"Normal\" eres atacable como siempre", "attackable".equals(decided[0]));
        check("con \"Baja\" sigues siendo claramente la mayor amenaza: atacable", "attackable".equals(decided[1]));
        check("con \"Muy baja\" se te aparta", "spared".equals(decided[2]));
        final String attack = FriendlyAttacks.lastAttacked();
        final String target = attack.contains("->") ? attack.substring(attack.indexOf("->") + 2) : "";
        check("en la partida, la IA ataca a la otra IA (" + target + ")",
                names[2] != null && target.equals(names[2]));
        check("y no a ti", names[0] != null && !target.contains(names[0]));
        check("y lo cuenta como apartado", FriendlyAttacks.sparedCount() > sparedBefore);
        check("desarmado al acabar", FriendlyAttacks.armedLevel() == FriendlyAttacks.NORMAL);

        gui.shutdown();
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la IA amable han fallado");
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
