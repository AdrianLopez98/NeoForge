package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.GameView;
import forge.game.player.PlayerView;

/**
 * <b>La ronda</b>, al lado del turno: <i>"Turno 23 (Ronda 12)"</i> (Discord,
 * 13-10-2026: <i>"could we get a round tick too, following the turn tick? ...
 * Could have an option to toggle it on or off in the settings"</i>).
 *
 * <p>El motor no la lleva: solo cuenta turnos. Y la cuenta facil
 * (turno / jugadores) se rompe en cuanto pasa algo normal en Commander: un
 * turno extra, o un rival eliminado a mitad de partida. Asi que se cuenta
 * mirando <b>de quien es cada turno</b>: el orden de juego se apunta en el
 * primer turno que se ve (por id, que no cambia aunque alguien pierda), y una
 * ronda nueva empieza cuando el turno <b>vuelve atras</b> en ese orden. Un
 * turno extra (el mismo jugador otra vez) no la cambia.
 *
 * <p>Si se empieza a mirar con la partida ya empezada (un invitado que entra
 * tarde), se arranca de turno / jugadores y desde ahi se cuenta.
 *
 * <p>Pura y sin estado del motor: una por partida, alimentada con el
 * {@code GameView} cada vez que se pinta el turno. Compartida con Android por
 * el jar (nada de API de Java que no tenga en la 26).
 */
public final class RoundCounter {

    /** El ajuste: ensenyar la ronda. Apagado de fabrica. */
    public static final String SETTING = "showRound";

    private final List<Integer> order = new ArrayList<>();
    private int lastTurn = -1;
    private int lastSeat = -1;
    private int round;

    /** Si esta encendido en el PC (Android lo mira en sus Ajustes). */
    public static boolean enabled() {
        return forge.neo.NeoSettings.getBool(SETTING, false);
    }

    /**
     * La ronda del turno que dice el {@code GameView}, o 0 si no se sabe.
     * Llamarlo con cada turno nuevo (o mas a menudo: el mismo turno no cuenta
     * dos veces).
     */
    public synchronized int round(final GameView gv) {
        if (gv == null || gv.getTurn() <= 0) {
            return 0;
        }
        final int turn = gv.getTurn();
        if (turn == lastTurn) {
            return round;
        }
        final PlayerView active = gv.getPlayerTurn();
        if (order.isEmpty()) {
            start(gv, active);
        }
        final int seat = active == null ? -1 : order.indexOf(active.getId());
        if (lastTurn < 0) {
            // Primera vez que se mira: con la partida empezada, turno / jugadores.
            final int players = Math.max(1, order.size());
            round = (turn + players - 1) / players;
        } else if (turn < lastTurn) {
            // Deshacer no vuelve atras de turno; esto es otra partida.
            order.clear();
            start(gv, active);
            round = 1;
        } else if (seat >= 0 && lastSeat >= 0 && seat < lastSeat) {
            round++;
        } else if (seat < 0 || lastSeat < 0) {
            final int players = Math.max(1, order.size());
            round = Math.max(round, (turn + players - 1) / players);
        }
        lastTurn = turn;
        if (seat >= 0) {
            lastSeat = seat;
        }
        return round;
    }

    /**
     * El orden de juego, empezando por quien tiene el turno: los jugadores van
     * en {@code getPlayers()} en orden de asiento, y se juega en ese orden a
     * partir de quien empieza.
     */
    private void start(final GameView gv, final PlayerView active) {
        final List<PlayerView> seats = new ArrayList<>();
        if (gv.getPlayers() != null) {
            for (final PlayerView p : gv.getPlayers()) {
                seats.add(p);
            }
        }
        int first = active == null ? 0 : Math.max(0, seats.indexOf(active));
        for (int i = 0; i < seats.size(); i++) {
            order.add(seats.get((first + i) % seats.size()).getId());
        }
    }
}
