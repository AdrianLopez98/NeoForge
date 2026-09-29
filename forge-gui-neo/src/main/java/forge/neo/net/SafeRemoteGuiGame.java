package forge.neo.net;

import forge.gamemodes.net.server.RemoteClient;
import forge.gamemodes.net.server.RemoteClientGuiGame;
import forge.gui.control.GameEventForwarder;

/**
 * La GUI de un invitado, del lado del anfitrion, con el {@code null} que le
 * falta comprobar a Forge. Mismo patron que {@code forge.neo.match.SafeAi}: no
 * se toca el motor, se le envuelve desde aqui.
 *
 * <p><b>El fallo</b> (Forge 2.0.16, #12065, visto el 29-09-2026 con
 * {@code lobbycheck}): {@code RemoteClientGuiGame.syncAndSend} ahora pregunta
 * {@code getForwarder().hasPendingZoneChange(...)} antes de mandar cada
 * mensaje. Pero {@code HostedMatch.endCurrentGame} hace
 * {@code shutdownForwarder()} — que lo deja a {@code null} — y JUSTO DESPUES
 * llama a {@code afterGameEnd()}, que pasa por ahi: {@code NullPointerException},
 * el invitado no recibe el fin de partida (se queda mirando la mesa, el fallo
 * que se arreglo en la 4.4) y el bucle de fin de partida se corta.
 *
 * <p><b>El arreglo:</b> sin forwarder se devuelve uno VACIO, que dice "no hay
 * cambios de zona pendientes" — lo cierto, porque ya se ha vaciado al cerrar.
 * Todos los demas que lo leen ({@code HostedMatch.startGame}, el
 * {@code flush} del final) lo hacen con el de verdad puesto. Si Forge lo
 * arregla rio arriba, esto simplemente deja de hacer falta.
 *
 * <p>Se pone en {@link NetHostedMatch#startMatch}: el constructor de
 * {@code RemoteClientGuiGame} se registra solo en su cliente
 * ({@code client.setGui(this)}), asi que desde ahi TODO lo de Forge —
 * reconexion incluida — usa esta.
 */
public class SafeRemoteGuiGame extends RemoteClientGuiGame {

    private final GameEventForwarder empty = new GameEventForwarder(this);

    public SafeRemoteGuiGame(final RemoteClient client) {
        super(client);
    }

    @Override
    public GameEventForwarder getForwarder() {
        final GameEventForwarder real = super.getForwarder();
        return real != null ? real : empty;
    }
}
