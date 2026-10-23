package forge.neo.quest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import forge.deck.Deck;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.player.GamePlayerUtil;
import forge.util.MyRandom;

/**
 * Tu partida del torneo de la Quest ({@link NeoQuestCup}): en Commander, tu
 * mesa de cuatro (como {@link NeoColosseumMatch}); en Estandar, uno contra uno
 * con el mazo del duelo de Forge que te toque.
 *
 * <p>Por {@link NeoGame}, como el Coliseo y el torneo de draft. Cada rival se
 * sienta con su nombre (el comandante, o el del duelo). Sin JavaFX: el hilo lo
 * pone quien llama, y se queda esperando hasta que termina la partida.
 */
public final class NeoQuestCupMatch {

    private NeoQuestCupMatch() {
    }

    /** Lo que ha pasado. */
    public static final class Outcome {
        public final boolean won;
        public final NeoMatchUI.Exit exit;
        public final int turns;

        Outcome(final boolean won, final NeoMatchUI.Exit exit, final int turns) {
            this.won = won;
            this.exit = exit;
            this.turns = turns;
        }
    }

    /**
     * Monta los mazos y juega. Se gana si el partido lo gana TU nombre; lo
     * demas (rendirse, salir, que gane una IA) es perder.
     *
     * @return null si no se ha podido montar la mesa o se ha dejado antes de
     *         empezar (no se ha jugado nada: el torneo sigue igual)
     */
    public static Outcome play(final TableBinder binder, final NeoMatchUI.Mode mode,
                               final boolean autoPayMana, final String aiProfile) {
        return play(binder, mode, autoPayMana, aiProfile, 86_400);
    }

    /** Con tope de tiempo: el comprobador no espera a que acabe. */
    static Outcome play(final TableBinder binder, final NeoMatchUI.Mode mode,
                        final boolean autoPayMana, final String aiProfile, final int timeoutSecs) {
        final Deck mine = NeoQuest.currentDeck();
        if (mine == null || NeoQuestCup.deckProblem() != null
                || NeoQuestCup.stage() != NeoQuestCup.Stage.PLAYING) {
            return null;
        }
        final boolean commander = NeoQuestCup.terms().commander;
        final List<String> names = NeoQuestCup.rivalNames();
        final List<Deck> decks = NeoQuestCup.rivalDecks();
        if (decks.isEmpty() || decks.size() < names.size()) {
            System.out.println("[torneo] solo " + decks.size() + " mazos rivales: no se juega");
            return null;
        }
        if (binder != null && binder.isAbandoned()) {
            System.out.println("[torneo] se ha dejado la mesa antes de empezar: no se juega");
            return null;
        }
        final NeoFormat format = commander ? NeoFormat.COMMANDER : NeoFormat.ESTANDAR;
        final int[] faces = faces(decks.size());
        final NeoGame.Seating seating = new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return EnumSet.of(commander ? GameType.Commander : GameType.Constructed);
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                return format.register(deck, seats);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                return format.register(deck, seats);
            }

            @Override
            public forge.LobbyPlayer player(final int i, final String profile) {
                return GamePlayerUtil.createAiPlayer(names.get(i), faces[i],
                        forge.neo.look.RivalSetup.resolve(profile, i, MyRandom.getRandom()));
            }
        };
        System.out.println("[torneo] ronda " + (NeoQuestCup.round() + 1) + " contra " + names);
        final NeoGame.Result r = NeoGame.play(mine, decks.size(), mode, timeoutSecs, false, binder,
                aiProfile, autoPayMana, format, decks, 1, seating, NeoMatchUI.Ending.QUEST);
        final boolean won = r.matchWinner != null
                && r.matchWinner.equals(GamePlayerUtil.getGuiPlayer().getName());
        return new Outcome(won, r.exit, r.turns);
    }

    private static int[] faces(final int n) {
        final int total = Math.max(n, forge.neo.look.NeoLook.builtInAvatarCount());
        final List<Integer> all = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            all.add(i);
        }
        java.util.Collections.shuffle(all, MyRandom.getRandom());
        final int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = all.get(i);
        }
        return out;
    }
}
