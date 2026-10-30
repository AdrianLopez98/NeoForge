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
 * {@code AndroidMatchUI.openView} en Android, en partidas de Ascenso y, desde el
 * 30-10-2026, en las de la Quest a cuatro (el Coliseo y el campeonato de
 * Commander): ahi caer ya es perder, y quedarse mirando no da ningun puesto.
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

    /**
     * Tu puesto en la ultima mesa de cuatro vigilada, apuntado AL CAER: uno mas
     * que los que seguian en pie (caes con dos en pie: 3.º). 0 si no has caido
     * (o has ganado). Lo leen la vitrina del Coliseo y del campeonato
     * ({@code NeoColosseum.lose}, {@code NeoQuestCup.record}): despues de
     * cerrar, el motor da por perdidos a los que quedaban y contar al final no
     * sirve. Una partida a la vez, asi que basta un valor; {@link #watch} lo
     * pone a 0 en cada partida.
     */
    private static volatile int lastFallPlace;

    /** Ver {@link #lastFallPlace}. */
    public static int lastFallPlace() {
        return lastFallPlace;
    }

    private AscentTableEnd(final Game game) {
        this.game = game;
    }

    /** Vigila esta partida, si no se vigilaba ya. Solo hace algo si es a mas de dos. */
    public static void watch(final Game game) {
        if (game == null || WATCHED.putIfAbsent(game, Boolean.TRUE) != null) {
            return;
        }
        lastFallPlace = 0;
        game.subscribeToEvents(new AscentTableEnd(game));
    }

    @Subscribe
    public void receive(final GameEvent ev) {
        if (closed || game.getRegisteredPlayers().size() <= 2) {
            return;
        }
        // El puesto, antes de mirar si se ha acabado: si caes con UNO en pie, el
        // motor cierra la partida el solo y no se llegaria a contar (2.º).
        notePlace();
        if (game.isGameOver()) {
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
        notePlace();
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

    /** Tu puesto, la primera vez que se te ve caido (ver {@link #lastFallPlace}). */
    private void notePlace() {
        if (lastFallPlace != 0) {
            return;
        }
        boolean fallen = false;
        int standing = 0;
        for (final Player p : game.getRegisteredPlayers()) {
            if (p.isAI()) {
                if (!p.hasLost()) {
                    standing++;
                }
            } else if (p.hasLost()) {
                fallen = true;
            } else {
                return; // queda un humano en pie: aun no hay puesto
            }
        }
        if (fallen && standing > 0) {
            lastFallPlace = standing + 1;
        }
    }
}
