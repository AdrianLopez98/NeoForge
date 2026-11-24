package forge.neo.rogue;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.game.GameType;
import forge.game.GameView;
import forge.game.player.RegisteredPlayer;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;

/**
 * La mesa de Rogue Commander en el ESCRITORIO: el duelo por {@link NeoGame#play}
 * con un {@link NeoGame.Seating}, que es el camino por el que pasan todas las
 * partidas (principio 8 de las notas de diseño): el blindaje de la IA, los ajustes del
 * motor y la mesa de siempre.
 *
 * <p>JavaFX vive aqui y en ningun otro sitio de {@code forge.neo.rogue}:
 * Android NO usa esta clase (tiene su {@code RogueMatch.kt}).
 */
public final class RogueNeoTable implements RogueTable {

    /** Cuanto se espera a un duelo antes de darlo por colgado (como Ascenso). */
    private static final int TIMEOUT_SECS = 60 * 60;

    private final Supplier<TableBinder> open;
    private final Runnable close;
    private final NeoMatchUI.Mode mode;

    /**
     * @param open  pone la mesa y devuelve su puente, o null para jugar sin
     *              mesa (el comprobador: entonces juega la IA por ti)
     * @param close quita la mesa al acabar
     * @param mode  como se juega cuando HAY mesa
     */
    public RogueNeoTable(Supplier<TableBinder> open, Runnable close, NeoMatchUI.Mode mode) {
        this.open = open;
        this.close = close;
        this.mode = mode;
    }

    @Override
    public GameView play(RogueSeats seats) {
        NeoGame.Result result = null;
        try {
            TableBinder binder = open.get();
            NeoMatchUI.Mode playMode = binder == null ? NeoMatchUI.Mode.AUTO_PLAY : mode;
            // Sin nadie delante (el comprobador) no se espera una hora a un motor muerto.
            int timeout = playMode == NeoMatchUI.Mode.HUMAN ? TIMEOUT_SECS : 180;
            result = NeoGame.play(seats.humanDeck(), 1, playMode, timeout, false, binder,
                null, true, NeoFormat.COMMANDER, List.of(seats.planeboundDeck()), 1, new Seating(seats),
                NeoMatchUI.Ending.ROGUE);
        } catch (RuntimeException e) {
            System.err.println("[rogue] el duelo no ha podido empezar: " + e);
        } finally {
            // Si se corto la espera por un cuelgue del motor (NeoAppRogue interrumpe
            // este hilo), se limpia: si no, las preguntas de despues fallarian.
            Thread.interrupted();
            close.run();
        }
        if (result == null || !result.completed || result.lastGame == null
                || result.lastGame.getOutcome() == null) {
            return null;
        }
        return result.lastGame;
    }

    /** {@link RogueSeats} con la forma que pide {@link NeoGame}. */
    private static final class Seating implements NeoGame.Seating {
        private final RogueSeats seats;

        Seating(RogueSeats seats) {
            this.seats = seats;
        }

        @Override
        public EnumSet<GameType> variants() {
            return seats.variants();
        }

        @Override
        public GameType gameType() {
            return seats.gameType();
        }

        @Override
        public RegisteredPlayer human(Deck deck, int count) {
            return seats.human();
        }

        @Override
        public RegisteredPlayer opponent(int i, Deck deck, int count) {
            return seats.opponent();
        }

        @Override
        public LobbyPlayer player(int i, String aiProfile) {
            return seats.aiPlayer();
        }
    }
}
