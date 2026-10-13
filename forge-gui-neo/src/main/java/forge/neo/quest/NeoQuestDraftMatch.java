package forge.neo.quest;

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

/**
 * Una partida del torneo de draft de la Quest: tu mazo contra el del rival que
 * te toca en el cuadro ({@link NeoQuestDrafts#nextMatch()}).
 *
 * <p>Por {@link NeoGame} y no por {@code QuestUtil}: un torneo no es un duelo
 * de la Quest (no da el botin de un duelo ni cuenta para el rango), es una
 * partida de limitado como las del draft de siempre. Lo unico que cambia es
 * <b>quien se sienta enfrente</b> — el rival con el nombre y la cara que tiene
 * en el cuadro ({@link NeoGame.Seating#player}) —, que es al mejor de tres como
 * en Forge, y que al acabar se vuelve a la Quest ({@code Ending.QUEST}).
 *
 * <p>Sin JavaFX: el hilo lo pone quien llama, y se queda esperando hasta que
 * termina la partida.
 */
public final class NeoQuestDraftMatch {

    private NeoQuestDraftMatch() {
    }

    /** Al mejor de tres, como el torneo de Forge ({@code QuestDraftUtils}). */
    public static final int GAMES = 3;

    /** Lo que ha pasado. */
    public static final class Outcome {
        /** Si has ganado la partida. */
        public final boolean won;
        /** Si se ha jugado de verdad y hay que apuntarla. */
        public final boolean counts;
        public final NeoMatchUI.Exit exit;

        Outcome(final boolean won, final boolean counts, final NeoMatchUI.Exit exit) {
            this.won = won;
            this.counts = counts;
            this.exit = exit;
        }
    }

    /**
     * Juega la partida que toca. Llamar fuera del hilo de interfaz.
     *
     * <p>Se mira quien gano el PARTIDO ({@code matchWinner}), no la ultima
     * partida: rendirse a mitad del al mejor de tres es perderlo, igual que en
     * Forge, aunque la ultima partida jugada la ganaras tu. "Reiniciar" no se
     * ofrece en esta partida ({@code NeoApp.noRestartFor}): seria un
     * reintento gratis con premios en juego.
     *
     * @return null si no hay partida que jugar
     */
    public static Outcome play(final TableBinder binder, final NeoMatchUI.Mode mode,
                               final boolean autoPayMana, final String aiProfile) {
        final NeoQuestDrafts.Pairing p = NeoQuestDrafts.nextMatch();
        final Deck mine = NeoQuestDrafts.myDeck();
        final String rival = p == null ? null : p.rivalOfHuman();
        final Deck theirs = rival == null ? null : NeoQuestDrafts.deckOf(rival);
        if (p == null || mine == null || theirs == null) {
            return null;
        }
        final String rivalName = NeoQuestDrafts.nameOf(rival, "");
        final int rivalFace = NeoQuestDrafts.iconOf(rival);
        final NeoGame.Seating seating = new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return EnumSet.of(GameType.Draft);
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                return NeoFormat.DRAFT.register(deck, seats);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                return NeoFormat.DRAFT.register(deck, seats);
            }

            @Override
            public forge.LobbyPlayer player(final int i, final String profile) {
                return GamePlayerUtil.createAiPlayer(rivalName, rivalFace);
            }
        };
        System.out.printf(java.util.Locale.ROOT, "[torneo] ronda %d contra %s%n", p.round, rivalName);
        // Un dia de tope: al mejor de tres, una partida larga no puede darse
        // por colgada (con el tope corto, se volveria al cuadro a media partida).
        final NeoGame.Result r = NeoGame.play(mine, 1, mode, 86_400, false, binder, aiProfile,
                autoPayMana, NeoFormat.DRAFT, List.of(theirs), GAMES, seating, NeoMatchUI.Ending.QUEST);
        final boolean won = r.matchWinner != null
                && r.matchWinner.equals(GamePlayerUtil.getGuiPlayer().getName());
        return new Outcome(won, true, r.exit);
    }
}
