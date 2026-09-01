package forge.neo.match;

import forge.game.player.PlayerView;

/**
 * Como se llama un jugador EN ESTA PARTIDA.
 *
 * <p>Parece una tonteria y no lo es: {@code PlayerView} publica <b>dos</b>
 * nombres y solo uno de ellos es unico.
 *
 * <ul>
 *   <li>{@code getLobbyPlayerName()} es el nombre del perfil, tal cual lo
 *       escribio esa persona. <b>Se puede repetir.</b></li>
 *   <li>{@code getName()} es el nombre dentro de la partida, y el motor lo
 *       <b>desempata el solo</b>: {@code Player.chooseName} recorre los que ya
 *       estan sentados y, si el tuyo esta cogido, te llama "2º Ana".</li>
 * </ul>
 *
 * <p><b>El fallo que lo destapo</b> fue una partida en red en la que los dos
 * jugadores tenian el mismo nombre de perfil, porque ninguno lo habia cambiado.
 * La mesa pedia {@code getLobbyPlayerName()} en todas partes, asi que las dos
 * barras ponian "Ana", el prompt decia "Esperando a Ana..." y no habia forma
 * de saber de quien hablaba nadie. El motor ya lo tenia resuelto — el registro
 * de esa misma partida decia "Active=2º Ana" — y lo estabamos tirando.
 *
 * <p>Es el patron de siempre en este proyecto: <b>informacion que el motor ya
 * da y no le estabamos pidiendo</b>. Y no vale con arreglarlo en la barra de
 * jugador: el nombre sale tambien en el stack, en el registro, en las pestanyas
 * de rival, en el reparto de dano y en el visor de zonas. Por eso hay una sola
 * funcion y no doce parches.
 */
public final class PlayerName {

    private PlayerName() {
    }

    /**
     * El nombre que hay que ENSENYAR.
     *
     * <p>Se cae al del perfil si el de partida no estuviera puesto — pasa antes
     * de que el motor haya montado la mesa, y en las maquetas — y a "?" si no
     * hay jugador, que es lo que se enseñaba ya en esos huecos.
     */
    public static String of(final PlayerView p) {
        if (p == null) {
            return "?";
        }
        try {
            final String inGame = p.getName();
            if (inGame != null && !inGame.isBlank()) {
                return inGame;
            }
            final String lobby = p.getLobbyPlayerName();
            return lobby == null || lobby.isBlank() ? "?" : lobby;
        } catch (final RuntimeException e) {
            // Un nombre no puede llevarse la mesa por delante.
            return "?";
        }
    }
}
