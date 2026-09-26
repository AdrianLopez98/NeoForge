package forge.neo.match;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import forge.deck.Deck;
import forge.game.GameType;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;

/**
 * Partidas por equipos, <b>como las monta el Forge de siempre</b>.
 *
 * <p>En su lobby cada jugador —el humano tambien— tiene un desplegable
 * "Equipo" del 1 al 8 ({@code PlayerPanel.teamComboBox}), y de fabrica cada
 * asiento va en uno distinto, o sea todos contra todos. Si todos acaban en el
 * mismo, no deja empezar: "no hay suficientes equipos"
 * ({@code GameLobby.isEnoughTeams}). Aqui es lo mismo, con los mismos textos
 * de Forge ({@code lblTeam}, {@code lblNotEnoughTeams}, {@code lblTeamWon}),
 * que ya vienen traducidos a los diez idiomas.
 *
 * <p><b>No hay ni una regla nuestra.</b> Todo sale de
 * {@code RegisteredPlayer.setTeamNumber} al montar el asiento — la misma
 * llamada que hace {@code GameLobby.startGame} — y a partir de ahi el motor
 * lo aplica solo: {@code Player.isOpponentOf} deja de contar al del mismo
 * equipo (no se le puede atacar, "cada oponente" no le incluye, la IA no le
 * apunta) y la partida se acaba cuando queda <b>un equipo</b>
 * ({@code GameEndReason.AllOpposingTeamsLost}).
 *
 * <p>Va como {@link NeoGame.Seating} y no como un parametro mas de
 * {@code NeoGame.play}: los asientos los sigue montando el formato
 * ({@code NeoFormat.register}), con las mismas variantes, y solo se les pone
 * el numero encima. Con cada uno en su equipo {@link #seating} devuelve
 * {@code null} y la partida se monta exactamente como antes de que esto
 * existiera.
 *
 * <p>Los equipos van en un {@code int[]}: el hueco 0 eres tu y el
 * {@code i + 1} el rival {@code i}. Numeros desde 0, como en el motor; en
 * pantalla se ensenyan desde 1, como en el desplegable de Forge.
 */
public final class NeoTeams {

    /** Cuantos equipos ofrece el desplegable. Forge: {@code VLobby.MAX_PLAYERS}. */
    public static final int MAX_TEAMS = 8;

    private NeoTeams() {
    }

    /** Todos contra todos: cada asiento en su equipo. Lo de fabrica en Forge. */
    public static int[] freeForAll(final int opponents) {
        final int[] out = new int[Math.max(0, opponents) + 1];
        for (int i = 0; i < out.length; i++) {
            out[i] = i;
        }
        return out;
    }

    /**
     * Los asientos con su equipo puesto, o {@code null} si es todos contra
     * todos (lo de siempre, sin tocar nada) o si el reparto no vale.
     *
     * <p>Observando no se sienta nadie en tu sitio: {@code NeoGame} anyade una
     * IA mas, la ULTIMA ({@code i == opponents}), que juega tu mazo. Esa ocupa
     * tu asiento y se lleva tu equipo.
     *
     * @param teams hueco 0 = tu, {@code i + 1} = rival {@code i}
     */
    public static NeoGame.Seating seating(final NeoFormat format, final int[] teams) {
        if (teams == null || isFreeForAll(teams) || !isEnoughTeams(teams)) {
            return null;
        }
        final int[] team = teams.clone();
        return new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                // Las mismas que pone NeoGame sin Seating: solo cambia el equipo.
                return EnumSet.of(format.getGameType());
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                final RegisteredPlayer rp = format.register(deck, seats);
                rp.setTeamNumber(team[0]);
                return rp;
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                final RegisteredPlayer rp = format.register(deck, seats);
                rp.setTeamNumber(i + 1 < team.length ? team[i + 1] : team[0]);
                return rp;
            }
        };
    }

    /** Si cada asiento va en un equipo distinto. */
    public static boolean isFreeForAll(final int[] teams) {
        if (teams == null) {
            return true;
        }
        final Set<Integer> seen = new HashSet<>();
        for (final int t : teams) {
            if (!seen.add(t)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Si hay al menos dos equipos: {@code GameLobby.isEnoughTeams}. Con uno
     * solo el motor daria la partida por ganada antes del primer turno.
     */
    public static boolean isEnoughTeams(final int[] teams) {
        if (teams == null || teams.length < 2) {
            return true;
        }
        for (final int t : teams) {
            if (t != teams[0]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Si {@code other} juega del lado de {@code me}.
     *
     * <p>Se le pregunta al motor, no a lo que se eligio en el menu: la lista
     * de rivales de cada jugador la fija {@code HostedMatch} al empezar
     * ({@code updateOpponentsForView}) y no cambia aunque alguien pierda, asi
     * que vale toda la partida — tambien en red y en la aventura, que ponen
     * sus equipos por su cuenta. En todos contra todos siempre es
     * {@code false}.
     */
    public static boolean isAlly(final PlayerView me, final PlayerView other) {
        return me != null && other != null && !me.equals(other) && !me.isOpponentOf(other);
    }

    /**
     * El equipo que ha ganado, o -1 si la partida no iba por equipos (o no
     * ha acabado).
     *
     * <p>No vale leer {@code GameView.getWinningTeam()} a pelo: es un entero
     * que <b>vale 0 de fabrica</b> hasta que el motor lo pone, y en red el
     * invitado recibe la vista por deltas — podria leer ese 0 antes de que
     * llegue el -1 de una partida de todos contra todos y anunciar que ha
     * ganado "el equipo 1". Asi que solo cuenta si de verdad hay alguien que
     * juega con alguien.
     */
    public static int winningTeam(final forge.game.GameView gv) {
        if (gv == null || !gv.isGameOver() || gv.getPlayers() == null) {
            return -1;
        }
        boolean teams = false;
        for (final PlayerView a : gv.getPlayers()) {
            for (final PlayerView b : gv.getPlayers()) {
                if (isAlly(a, b)) {
                    teams = true;
                }
            }
        }
        return teams ? gv.getWinningTeam() : -1;
    }

    /** Para {@code NeoSettings.TEAMS}: "0,1,0,1". */
    public static String toSetting(final int[] teams) {
        final StringBuilder sb = new StringBuilder();
        if (teams != null) {
            for (int i = 0; i < teams.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(teams[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Lo contrario, para {@code opponents} rivales. Un asiento que falte, o un
     * numero que no se entienda, va a su equipo de fabrica (el suyo propio).
     * Y un reparto que no se puede jugar vuelve a todos contra todos: un
     * ajuste raro nunca deja una partida que no se puede empezar.
     */
    public static int[] fromSetting(final String value, final int opponents) {
        final int[] out = freeForAll(opponents);
        if (value == null || value.isBlank()) {
            return out;
        }
        final String[] parts = value.split(",");
        for (int i = 0; i < out.length && i < parts.length; i++) {
            try {
                final int t = Integer.parseInt(parts[i].trim());
                if (t >= 0 && t < MAX_TEAMS) {
                    out[i] = t;
                }
            } catch (final NumberFormatException e) {
                // se queda el de fabrica
            }
        }
        return isEnoughTeams(out) ? out : freeForAll(opponents);
    }
}
