package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.event.GameEvent;
import forge.game.event.GameEventCardChangeZone;
import forge.game.event.GameEventTurnBegan;
import forge.game.player.Player;

/**
 * El color que se elige ANTES DE LA PARTIDA para un comandante (Clara Oswald,
 * The Prismatic Piper) no se pierde al cambiar de zona.
 *
 * <p>Discord, 11-10-2026: <i>"Clara Oswald incorrectly survives All Is Dust after
 * choosing black"</i>. Es un fallo del motor: {@code Player.initCommanderColor}
 * guarda el color en la carta del mando ({@code setChosenColors}), pero al
 * cambiar de zona {@code GameAction.changeZone} hace una copia nueva con
 * {@code CardCopyService.copyCard}, y esa copia lleva el "es comandante" y NO el
 * color elegido. O sea que en cuanto se lanza desde el mando llega incolora, y
 * todo lo que mira el color (All Is Dust, "destruye las criaturas negras") se la
 * salta.
 *
 * <p>Por la regla de oro no se arregla alli: se envuelve, como {@link SafeAi}.
 * Escucha los eventos de la partida, que van en el hilo del motor y en el mismo
 * momento (el {@code EventBus} de {@code Game} es sincrono): apunta el color de
 * los comandantes que lo llevan y, cuando uno cambia de zona y llega sin el, se
 * lo devuelve antes de que se recalculen los estaticos. La carta conserva su id
 * en la copia ({@code copyCard(false)}). Si algun dia lo arreglan en Forge, la
 * carta ya llega con su color y esto no hace nada.
 *
 * <p>Lo instala {@link SafeAi#hook}, que pasa por TODAS las partidas (PC,
 * Android y el anfitrion de una partida en red).
 */
public final class CommanderColorKeeper {

    private final Game game;

    /** El color de cada comandante "comodin", por id de carta. */
    private final Map<Integer, List<String>> chosen = new ConcurrentHashMap<>();

    private CommanderColorKeeper(final Game game) {
        this.game = game;
    }

    /**
     * Lo engancha a esta partida. Va despues de que se elija el color: el
     * gancho de inicio corre tras {@code Match.prepareAllZones}.
     */
    public static void install(final Game game) {
        // -Dneo.commanderColor.off=true: sin el envoltorio, para ver que
        // commandercolorcheck SI caza el fallo del motor.
        if (game == null || Boolean.getBoolean("neo.commanderColor.off")) {
            return;
        }
        final CommanderColorKeeper keeper = new CommanderColorKeeper(game);
        keeper.remember();
        game.subscribeToEvents(keeper);
    }

    @Subscribe
    public void receiveGameEvent(final GameEvent event) {
        try {
            if (event instanceof GameEventCardChangeZone move && move.card() != null) {
                // La vista del evento es la de la carta ANTES de moverse, que
                // aun lleva su color: sirve para apuntarlo aunque no se viera
                // al empezar (una mesa preparada, un puzzle).
                final List<String> before = move.card().getChosenColors();
                if (before != null && !before.isEmpty()) {
                    final Card now = game.findById(move.card().getId());
                    if (now != null && wildCard(now)) {
                        chosen.put(now.getId(), new ArrayList<>(before));
                    }
                }
                restore(move.card().getId());
            } else if (event instanceof GameEventTurnBegan) {
                remember();
            }
        } catch (final RuntimeException e) {
            // Es un arreglo, no puede tumbar la partida.
            System.out.println("[comandante] no se ha podido conservar el color: " + e);
        }
    }

    /** Apunta el color de los comandantes que lo eligieron. */
    private void remember() {
        for (final Player p : game.getPlayers()) {
            for (final Card c : p.getCommanders()) {
                if (c != null && wildCard(c) && c.hasChosenColor()) {
                    final List<String> colors = new ArrayList<>();
                    c.getChosenColors().forEach(colors::add);
                    chosen.put(c.getId(), colors);
                }
            }
        }
    }

    /** Si esta carta es uno de ellos y ha llegado sin su color, se le devuelve. */
    private void restore(final int id) {
        final List<String> colors = chosen.get(id);
        if (colors == null) {
            return;
        }
        final Card c = game.findById(id);
        if (c != null && !c.hasChosenColor()) {
            c.setChosenColors(new ArrayList<>(colors));
            System.out.println("[comandante] " + c.getName() + " conserva su color: " + colors);
        }
    }

    private static boolean wildCard(final Card c) {
        return c.getRules() != null && c.getRules().getAddsWildCardColor();
    }
}
