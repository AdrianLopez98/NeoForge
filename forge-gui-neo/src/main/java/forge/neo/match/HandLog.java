package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.GameLogEntryType;
import forge.game.card.Card;
import forge.game.event.GameEvent;
import forge.game.event.GameEventTurnPhase;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * <b>Tu mano, en el registro de la partida, cada turno</b> (Discord,
 * 26-10-2026: <i>"I give the logs from my games to ChatGPT so it can analyze
 * my deck construction and play, but ... it doesn't know what cards I had in
 * my hand at the time"</i>).
 *
 * <p>En la primera fase principal de cada turno (el tuyo, ya robado; el del
 * rival, la que tienes mientras juega) anyade al registro de Forge una linea
 * {@code INFORMATION} con tus cartas. Va al registro DE VERDAD ({@code
 * GameLog.add}, publico), asi que sale en el panel del registro, en lo que se
 * copia y en el registro detallado, que copia esas lineas.
 *
 * <p>Apagado de fabrica ({@link #SETTING}), y solo con <b>una</b> persona en
 * la partida: en hot seat o en red el registro lo ve mas de uno, y seria
 * ensenyar tu mano. Los nombres de carta van en ingles, que es lo que entiende
 * quien lo analice. Se engancha en {@code openView} (PC y Android), como
 * {@link GameStats}.
 */
public final class HandLog {

    /** El ajuste, en {@code neo.properties}; apagado de fabrica. */
    public static final String SETTING = "handLog";

    private static final Map<Game, HandLog> BY_GAME = new WeakHashMap<>();

    private final Player me;
    private int lastTurn = -1;

    private HandLog(final Player me) {
        this.me = me;
    }

    public static boolean enabled() {
        try {
            return forge.neo.NeoSettings.getBool(SETTING, false);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** Lo engancha a esa partida si el ajuste esta puesto y juega una sola persona. */
    public static void attach(final Game game) {
        if (game == null || !enabled()) {
            return;
        }
        synchronized (BY_GAME) {
            if (BY_GAME.containsKey(game)) {
                return;
            }
            Player human = null;
            int humans = 0;
            for (final Player p : game.getPlayers()) {
                if (p.getController() != null && !p.getController().isAI()) {
                    human = p;
                    humans++;
                }
            }
            if (humans != 1) {
                return;
            }
            final HandLog log = new HandLog(human);
            BY_GAME.put(game, log);
            game.subscribeToEvents(log);
        }
    }

    @Subscribe
    public void receive(final GameEvent ev) {
        try {
            if (ev instanceof GameEventTurnPhase tp && tp.phase() == PhaseType.MAIN1) {
                final Game game = me.getGame();
                final int turn = game.getPhaseHandler().getTurn();
                if (turn == lastTurn) {
                    return;
                }
                lastTurn = turn;
                game.getGameLog().add(GameLogEntryType.INFORMATION, line(me));
            }
        } catch (final RuntimeException e) {
            // Un registro que no se puede escribir no puede tirar la partida.
        }
    }

    /** "Tu mano (3): Island, Counterspell, Brainstorm". */
    static String line(final Player p) {
        final List<String> names = new ArrayList<>();
        for (final Card c : p.getCardsIn(ZoneType.Hand)) {
            names.add(c.getName());
        }
        return forge.neo.NeoText.get("log.hand", p.getName(), names.size(),
                names.isEmpty() ? "-" : String.join(", ", names));
    }
}
