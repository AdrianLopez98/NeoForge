package forge.neo.match;

import java.util.EnumSet;

import forge.deck.Deck;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;

/**
 * <b>Two-Headed Giant</b> (itch.io, 16-11-2026: <i>"would 2hg be something
 * possible in the near future? Since it seems Forge's engine has implemented
 * it"</i>), como lo trae Forge (#12587 y #12786, noviembre de 2026).
 *
 * <p><b>No es lo mismo que los equipos de siempre</b> ({@link NeoTeams}). Con
 * equipos cada uno juega su turno y tiene su vida; en 2HG los dos de un equipo
 * <b>comparten turno</b> (atacan y bloquean juntos), <b>comparten la vida</b>
 * (30, o 60 en Commander), el veneno (se pierde con 15), el dano de comandante
 * (por equipo) y las victorias, y pueden ver la mano del otro. Todo eso lo hace
 * el motor en cuanto la variante {@code GameType.TwoHeadedGiant} esta en las
 * reglas ({@code GameRules.hasSharedTeamTurns}); aqui no hay ni una regla.
 *
 * <p>Lo que hace su lobby ({@code GameLobby.applyVariant} y
 * {@code startGame}), y por tanto esto:
 * <ul>
 *   <li><b>Cuatro jugadores exactos</b>: tu y tres rivales.</li>
 *   <li><b>Los equipos van por asiento, de dos en dos</b>
 *       ({@code applyVariantTeams}: equipo {@code i / 2}), y los companeros
 *       sentados juntos (CR 805.1). O sea: tu y el rival 1 contra los rivales 2
 *       y 3. Con 2HG no se mira el desplegable de equipos de cada asiento.</li>
 *   <li><b>Con Commander o con un construido</b>. Las vidas las pone
 *       {@code RegisteredPlayer.forVariants}: 30, y 40 + 20 = 60 si va con
 *       Commander. Brawl, Oathbreaker, Tiny Leaders y Vanguard ponen sus propias
 *       vidas y Forge los quita; Planechase y Archenemy tambien (Planechase
 *       tiene sus reglas de equipos, CR 901.12). Aqui no se juntan: con 2HG
 *       puesto esos dos se ignoran.</li>
 * </ul>
 *
 * <p>El rival 1 puede ser una persona en hot seat ({@code RivalSetup}): tu y un
 * amigo contra dos IA en el mismo aparato.
 *
 * <p>Se monta por la costura de siempre ({@link NeoGame.Seating}). Compartida
 * con Android, que sienta a mano (su {@code MatchLauncher}): {@link #seat}.
 */
public final class TwoHeadedGiant {

    private TwoHeadedGiant() {
    }

    /** Con cuantos rivales: siempre tres (cuatro jugadores, dos equipos de dos). */
    public static final int OPPONENTS = 3;

    /**
     * En que formatos: Commander (y PreDH, que es Commander) y los construidos.
     * Los que no, porque Forge los quita al poner 2HG (sus propias vidas) o
     * porque no son una partida suelta.
     */
    public static boolean appliesTo(final GameType type) {
        return type == GameType.Commander || type == GameType.Constructed;
    }

    /** Si se juega: encendido, el formato vale y hay justo tres rivales. */
    public static boolean applies(final boolean on, final GameType type, final int opponents) {
        return on && appliesTo(type) && opponents == OPPONENTS;
    }

    /** Las variantes de la partida: las del formato y 2HG. */
    public static EnumSet<GameType> variants(final GameType type) {
        final EnumSet<GameType> out = EnumSet.of(type);
        out.add(GameType.TwoHeadedGiant);
        return out;
    }

    /**
     * El equipo del asiento {@code index} (0 el tuyo, 1.. los rivales): de dos
     * en dos, como {@code applyVariantTeams}.
     */
    public static int teamOf(final int index) {
        return index / 2;
    }

    /**
     * Los asientos con 2HG, o {@code base} tal cual si no se juega.
     *
     * @param base la costura que habria sin 2HG; se ignora entera (los equipos
     *             y las variantes los decide 2HG)
     */
    public static NeoGame.Seating wrap(final NeoFormat format, final NeoGame.Seating base,
                                       final boolean on, final int opponents) {
        if (format == null || !applies(on, format.getGameType(), opponents)) {
            return base;
        }
        final EnumSet<GameType> variants = variants(format.getGameType());
        // Jugando tu vas el primero y el rival i es el asiento i + 1. Observando
        // no hay asiento humano: NeoGame sienta una IA mas, la ULTIMA, que juega
        // tu mazo y ocupa TU asiento (como en NeoTeams), asi que se lleva tu
        // equipo: el rival i es el asiento (i + 1) % 4 y la ultima el 0. Es lo
        // que dice la pantalla ("tu y el rival 1 contra 2 y 3"), y los
        // companyeros siguen juntos en el circulo (el ultimo y el primero).
        final int seats = opponents + 1;
        return new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return EnumSet.copyOf(variants);
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int n) {
                return seat(deck, n, variants, 0);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int n) {
                return seat(deck, n, variants, (i + 1) % seats);
            }

            @Override
            public forge.LobbyPlayer player(final int i, final String aiProfile) {
                return base == null ? null : base.player(i, aiProfile);
            }
        };
    }

    /**
     * Un asiento con 2HG: {@code forVariants} con la variante puesta, que es la
     * que da las 30 vidas (60 con Commander) y el comandante, y su equipo.
     *
     * @param index el asiento: 0 el tuyo, 1.. los rivales
     */
    public static RegisteredPlayer seat(final Deck deck, final int seats,
                                        final EnumSet<GameType> variants, final int index) {
        final RegisteredPlayer rp = RegisteredPlayer.forVariants(seats, variants, deck, null, false, null, null);
        rp.setTeamNumber(teamOf(index));
        return rp;
    }
}
