package forge.neo.quest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.player.GamePlayerUtil;
import forge.util.MyRandom;

/**
 * La partida del Coliseo ({@link NeoColosseum}): Commander a cuatro, tu mazo de
 * la Quest contra los tres rivales de {@link NeoColosseum#lineup()}.
 *
 * <p>Por {@link NeoGame} y no por {@code QuestUtil}, como el torneo de draft:
 * el duelo de la Quest de Forge es uno contra uno y su botin es otro. Cada
 * rival se sienta con el nombre de su comandante ({@link NeoGame.Seating#player})
 * para que en la mesa se sepa quien es quien, y al acabar se vuelve a la Quest
 * ({@code Ending.QUEST}).
 *
 * <p>Sin JavaFX: el hilo lo pone quien llama, y se queda esperando hasta que
 * termina la partida.
 */
public final class NeoColosseumMatch {

    private NeoColosseumMatch() {
    }

    /** Lo que ha pasado. */
    public static final class Outcome {
        /** Si has ganado (el ultimo en pie). */
        public final boolean won;
        public final NeoMatchUI.Exit exit;
        /** Cuantos turnos duro (para el comprobador). */
        public final int turns;

        Outcome(final boolean won, final NeoMatchUI.Exit exit, final int turns) {
            this.won = won;
            this.exit = exit;
            this.turns = turns;
        }
    }

    /**
     * Monta los mazos de los rivales y juega. Llamar fuera del hilo de
     * interfaz: el mazo de nivel experto lo hace el generador de Forge y tarda.
     *
     * <p>Se gana si el partido lo gana TU nombre ({@code matchWinner}); cualquier
     * otra cosa — rendirse, salir al menu, que gane una IA — es perder. No hay
     * "Reiniciar" ({@code NeoApp.noRestartFor}): seria volver a tirar gratis
     * con la entrada ya pagada.
     *
     * <p>Si la mesa se ha dejado mientras se montaban los mazos (el experto
     * tarda), no se empieza: seria una partida que nadie ve, esperando el
     * mulligan de un humano que ya esta en el menu.
     *
     * @return null si no se ha podido montar la mesa, o se ha dejado antes de
     *         empezar (no se ha jugado nada)
     */
    public static Outcome play(final TableBinder binder, final NeoMatchUI.Mode mode,
                               final boolean autoPayMana, final String aiProfile) {
        // Un dia de tope: una partida a cuatro es larga, y con el tope corto se
        // volveria a la Quest a media partida.
        return play(binder, mode, autoPayMana, aiProfile, 86_400);
    }

    /** Con tope de tiempo: {@code ColosseumCheck} no espera a que acabe. */
    static Outcome play(final TableBinder binder, final NeoMatchUI.Mode mode,
                        final boolean autoPayMana, final String aiProfile, final int timeoutSecs) {
        final Deck mine = NeoQuest.currentDeck();
        if (mine == null || NeoColosseum.deckProblem() != null) {
            return null;
        }
        final List<Deck> decks = NeoColosseum.buildDecks(NeoColosseum.lineup());
        if (decks.size() < NeoColosseum.RIVALS) {
            System.out.println("[coliseo] solo " + decks.size() + " mazos rivales: no se juega");
            return null;
        }
        if (binder != null && binder.isAbandoned()) {
            System.out.println("[coliseo] se ha dejado la mesa antes de empezar: no se juega");
            return null;
        }
        final List<String> names = new ArrayList<>();
        for (final Deck d : decks) {
            names.add(commanderName(d));
        }
        final int[] faces = faces(decks.size());
        final NeoGame.Seating seating = new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return EnumSet.of(GameType.Commander);
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                return NeoFormat.COMMANDER.register(deck, seats);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                return NeoFormat.COMMANDER.register(deck, seats);
            }

            @Override
            public forge.LobbyPlayer player(final int i, final String profile) {
                // El caracter de IA que tengas puesto (o el de cada rival).
                return GamePlayerUtil.createAiPlayer(names.get(i), faces[i],
                        forge.neo.look.RivalSetup.resolve(profile, i, MyRandom.getRandom()));
            }
        };
        System.out.println("[coliseo] contra " + names + " (nivel " + NeoColosseum.rivalTier() + ")");
        final NeoGame.Result r = NeoGame.play(mine, NeoColosseum.RIVALS, mode, timeoutSecs, false, binder,
                aiProfile, autoPayMana, NeoFormat.COMMANDER, decks, 1, seating, NeoMatchUI.Ending.QUEST);
        final boolean won = r.matchWinner != null
                && r.matchWinner.equals(GamePlayerUtil.getGuiPlayer().getName());
        return new Outcome(won, r.exit, r.turns);
    }

    /** El nombre del comandante, en tu idioma: el mismo que se ve en la pantalla del Coliseo. */
    private static String commanderName(final Deck d) {
        if (d.has(DeckSection.Commander)) {
            for (final PaperCard c : d.get(DeckSection.Commander).toFlatList()) {
                return forge.neo.card.CardText.nameOf(c);
            }
        }
        return d.getName();
    }

    /** Tres caras distintas al azar de las que trae el juego. */
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
