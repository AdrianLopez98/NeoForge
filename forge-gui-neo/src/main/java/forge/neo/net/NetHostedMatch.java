package forge.neo.net;

import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.interfaces.IGuiGame;
import forge.neo.match.NeoGame;

/**
 * La partida que monta el lobby de Forge, con NUESTRAS reglas.
 *
 * <p><b>Por que existe.</b> Una partida suelta la monta {@code NeoGame}, que
 * construye sus {@code GameRules} a mano: una sola partida, y la apuesta y el
 * barajado con trampa segun nuestros Ajustes. La de red no pasa por ahi — la
 * arranca {@code GameLobby.startGame()} con
 * {@code startMatch(GameType, variantes, jugadores, guis)}, que saca las
 * reglas de {@code HostedMatch.getDefaultRules}, o sea de las preferencias de
 * <b>Forge</b>. Y ahi {@code UI_MATCHES_PER_GAME} vale 3 de fabrica: en red se
 * jugaba al mejor de tres sin que nadie lo hubiera pedido, con su pantalla de
 * "siguiente partida" en medio de una de Commander.
 *
 * <p>No se toca {@code GameLobby} ni {@code HostedMatch}: la sobrecarga que
 * usa el lobby es publica y no final, asi que se sobrescribe y se reenvia a la
 * otra, la que recibe las reglas ya hechas. Es la misma que usa {@code NeoGame}.
 *
 * <p>Solo la crea {@code NeoGuiBase.hostMatch} mientras hospedamos una sala
 * ({@link NeoLobby#isHostingLobby()}).
 */
public class NetHostedMatch extends HostedMatch {

    @Override
    public void startMatch(final GameType gameType, final Set<GameType> appliedVariants,
                           final List<RegisteredPlayer> players,
                           final Map<RegisteredPlayer, IGuiGame> guis) {
        final GameRules rules = new GameRules(gameType);
        // Una sola partida, como en local. En Commander no hay banquillo con
        // el que jugar un Bo3, y a cuatro nadie espera una segunda.
        rules.setGamesPerMatch(1);
        NeoGame.applyNeoRules(rules);
        startMatch(rules, appliedVariants, players, guis == null ? null : safeGuis(guis), null);
    }

    /**
     * La GUI de cada invitado, cambiada por {@link SafeRemoteGuiGame} (el
     * {@code null} que Forge 2.0.16 no comprueba al acabar la partida). La que
     * viene la acaba de crear el lobby para esta partida y no tiene nada dentro;
     * una en pausa (invitado desconectado) se deja como esta.
     */
    static Map<RegisteredPlayer, IGuiGame> safeGuis(final Map<RegisteredPlayer, IGuiGame> guis) {
        final Map<RegisteredPlayer, IGuiGame> out = new java.util.HashMap<>(guis);
        for (final Map.Entry<RegisteredPlayer, IGuiGame> e : out.entrySet()) {
            if (e.getValue() instanceof forge.gamemodes.net.server.RemoteClientGuiGame remote
                    && !(remote instanceof SafeRemoteGuiGame) && !remote.isPaused()) {
                e.setValue(new SafeRemoteGuiGame(remote.getClient()));
            }
        }
        return out;
    }
}
