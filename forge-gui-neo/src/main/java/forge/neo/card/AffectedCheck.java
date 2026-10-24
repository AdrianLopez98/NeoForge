package forge.neo.card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.SafeAi;
import forge.neo.tutorial.TutorialState;

/**
 * "Lo que otras cartas le hacen a esta" ({@code run.cmd affectedcheck}). Ver
 * {@link AffectedBy}.
 *
 * <p>Una mesa preparada: el rival tiene un Collector Ouphe ("las habilidades
 * activadas de los artefactos no se pueden activar") y un Arrest sobre tu oso;
 * tu, un Sol Ring y una criatura provocada por el rival. Se mira que cada carta
 * diga lo suyo, y que lo que no le toca no salga.
 */
public final class AffectedCheck {

    private static int passed;
    private static int failed;

    private AffectedCheck() {
    }

    public static void run() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=p0",
                "activephase=MAIN1",
                "p0life=20", "p1life=20",
                "p0battlefield=Sol Ring;Grizzly Bears|Id:500;Hill Giant",
                "p0hand=", "p0library=Plains;Plains;Plains;Plains;Plains",
                "p1battlefield=Collector Ouphe;Arrest|AttachedTo:500",
                "p1hand=", "p1library=Island;Island;Island;Island;Island");
        final TutorialState position = new TutorialState(state);

        final List<String> results = new ArrayList<>();
        final CountDownLatch done = new CountDownLatch(1);
        final NeoMatchUI gui = new NeoMatchUI(NeoMatchUI.Mode.AUTO_PLAY, false);
        final HostedMatch match = new HostedMatch();
        match.setStartGameHook(SafeAi.hook(match, () -> {
            try {
                final Game game = match.getGame();
                position.applyToGame(game);
                final Player me = game.getPlayers().get(0);
                final Player rival = game.getPlayers().get(1);
                Card ring = null;
                Card bears = null;
                Card giant = null;
                Card ouphe = null;
                for (final Card c : game.getCardsInGame()) {
                    switch (c.getName()) {
                        case "Sol Ring" -> ring = c;
                        case "Grizzly Bears" -> bears = c;
                        case "Hill Giant" -> giant = c;
                        case "Collector Ouphe" -> ouphe = c;
                        default -> { }
                    }
                }
                if (giant != null) {
                    giant.addGoad(game.getNextTimestamp(), rival);
                }
                AffectedBy.setGame(game);
                results.add(text(ring));
                results.add(text(bears));
                results.add(text(giant));
                results.add(text(ouphe));
                results.add(me.getName() + "|" + rival.getName());
            } catch (final RuntimeException e) {
                e.printStackTrace(System.out);
            } finally {
                done.countDown();
            }
        }));

        final List<RegisteredPlayer> players = new ArrayList<>();
        final RegisteredPlayer human = new RegisteredPlayer(new Deck()).setPlayer(forge.neo.look.NeoPlayers.human());
        human.setStartingHand(0);
        players.add(human);
        final RegisteredPlayer ai = new RegisteredPlayer(new Deck()).setPlayer(forge.neo.look.NeoPlayers.ai(0, null));
        ai.setStartingHand(0);
        players.add(ai);
        final GameRules rules = new GameRules(GameType.Constructed);
        rules.setGamesPerMatch(1);

        final Thread t = new Thread(() -> match.startMatch(rules, null, players, human, gui), "Game-affected");
        t.setDaemon(true);
        t.start();
        try {
            done.await(90, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (results.size() < 5) {
            check("la mesa se ha montado", false);
        } else {
            final String ring = results.get(0);
            final String bears = results.get(1);
            final String giant = results.get(2);
            final String ouphe = results.get(3);
            final String rival = results.get(4).split("\\|")[1];
            System.out.println("  Sol Ring:        " + ring);
            System.out.println("  Grizzly Bears:   " + bears);
            System.out.println("  Hill Giant:      " + giant);
            System.out.println("  Collector Ouphe: " + ouphe);
            check("Sol Ring: no se puede activar, por Collector Ouphe", ring.contains("Collector Ouphe"));
            check("Grizzly Bears: Arrest (no ataca ni bloquea)", bears.contains("Arrest"));
            check("Grizzly Bears no sale afectada por Collector Ouphe (no es un artefacto)",
                    !bears.contains("Collector Ouphe"));
            check("Hill Giant: provocada por " + rival, giant.contains(rival));
            check("Hill Giant no sale con Arrest ni Collector Ouphe",
                    !giant.contains("Arrest") && !giant.contains("Collector Ouphe"));
            check("Collector Ouphe no se cita a si misma", ouphe.isEmpty());
        }
        AffectedBy.setGame(null);
        check("sin partida, nada que decir", AffectedBy.of(null).isEmpty());

        gui.shutdown();
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de lo que afecta a una carta han fallado");
        }
    }

    private static String text(final Card c) {
        if (c == null) {
            return "(no esta)";
        }
        final StringBuilder sb = new StringBuilder();
        for (final AffectedBy.Line l : AffectedBy.of(c.getView())) {
            sb.append('[').append(l.source()).append(": ").append(l.text()).append(']');
        }
        return sb.toString();
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
