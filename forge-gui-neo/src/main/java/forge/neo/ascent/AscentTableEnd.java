package forge.neo.ascent;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.event.GameEvent;
import forge.game.player.Player;

/**
 * <b>En una mesa de cuatro de Ascenso, si caes se acaba la partida</b> (Ana,
 * 27-10-2026).
 *
 * <p>Forge sigue la partida entre las IA cuando el humano pierde a cuatro
 * ({@code GameAction.checkGameOverCondition} solo la cierra con un jugador en
 * pie), y en Ascenso eso es quedarse mirando como se reparten una run que ya
 * has perdido. Se cierra igual que al conceder: {@code AllHumansLost}, lo mismo
 * que el {@code forceEndGameForRemainingAIs} de Forge.
 *
 * <p>El motor no avisa de que un jugador ha perdido (no hay evento), asi que
 * se mira con cada aviso del bus, que es barato: cuatro asientos. El bus es
 * sincrono, en el hilo de la partida, que es desde donde hay que tocarla.
 *
 * <p>Pura, sin JavaFX: la engancha {@code NeoMatchUI.openView} en el PC y
 * {@code AndroidMatchUI.openView} en Android, solo en partidas de Ascenso.
 */
public final class AscentTableEnd {

    private static final Map<Game, Boolean> WATCHED = Collections.synchronizedMap(new WeakHashMap<>());

    private final Game game;
    private boolean closed;

    /**
     * Cuantos rivales seguian en pie la ultima vez que se cerro una mesa. Solo
     * para la prueba ({@code AscentTableProbe.fall}): despues de cerrar, el
     * motor reparte el resultado y da por perdidos a los que quedaban, asi que
     * contarlos al final no dice nada.
     */
    static volatile int lastStanding = -1;

    private AscentTableEnd(final Game game) {
        this.game = game;
    }

    /** Vigila esta partida, si no se vigilaba ya. Solo hace algo si es a mas de dos. */
    public static void watch(final Game game) {
        if (game == null || WATCHED.putIfAbsent(game, Boolean.TRUE) != null) {
            return;
        }
        game.subscribeToEvents(new AscentTableEnd(game));
    }

    @Subscribe
    public void receive(final GameEvent ev) {
        if (closed || game.isGameOver() || game.getRegisteredPlayers().size() <= 2) {
            return;
        }
        boolean anyHuman = false;
        for (final Player p : game.getRegisteredPlayers()) {
            if (p.isAI()) {
                continue;
            }
            anyHuman = true;
            if (!p.hasLost()) {
                return;
            }
        }
        if (!anyHuman) {
            return;
        }
        closed = true;
        int standing = 0;
        for (final Player p : game.getRegisteredPlayers()) {
            if (!p.hasLost()) {
                standing++;
            }
        }
        lastStanding = standing;
        System.out.println("[ascenso] has caido en una mesa de cuatro, con " + standing
                + " rivales en pie: se cierra la partida");
        game.getAction().invoke(() -> {
            try {
                if (!game.isGameOver()) {
                    game.setGameOver(GameEndReason.AllHumansLost);
                }
            } catch (final RuntimeException e) {
                System.out.println("[ascenso] no se ha podido cerrar la mesa: " + e);
            }
        });
    }
}
