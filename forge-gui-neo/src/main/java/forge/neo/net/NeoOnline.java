package forge.neo.net;

import java.util.Locale;
import java.util.function.Consumer;

import forge.gamemodes.match.GameLobby;
import forge.gamemodes.net.ChatMessage;
import forge.gamemodes.net.IOnlineChatInterface;
import forge.gamemodes.net.IOnlineLobby;
import forge.gamemodes.net.IRemote;
import forge.gamemodes.net.NetConnectUtil;
import forge.gamemodes.net.client.FGameClient;
import forge.gamemodes.net.event.MessageEvent;
import forge.gamemodes.net.server.FServerManager;
import forge.gui.interfaces.ILobbyView;
import forge.localinstance.properties.ForgeNetPreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.look.NeoLook;

/**
 * El pegamento entre nuestra pantalla y la red de Forge.
 *
 * <p>Existe para poder entrar por {@link NetConnectUtil}, que es el camino
 * oficial y hace bastantes mas cosas que levantar el socket: engancha el chat
 * con sus <b>comandos de anfitrion</b> ({@code /skipreconnect},
 * {@code /skiptimeout}), anuncia el plazo de AFK, conecta el manejador de draft
 * en red y, en el invitado, deja el lobby atado al {@code AbstractGuiGame} para
 * que la partida sepa de que mazo juega cada uno. Montarlo a mano era escribir
 * eso otra vez peor.
 *
 * <p>Para usarlo hay que darle a Forge dos interfaces, y son pequenyas:
 * {@link IOnlineLobby} (tres metodos) y {@link IOnlineChatInterface} (dos).
 * Las dos las implementa esta clase.
 *
 * <p><b>Dos cosas de hilos que no se pueden olvidar:</b>
 * <ol>
 *   <li>{@code host}/{@code join} se llaman desde un hilo <b>de fondo</b>, nunca
 *       desde el de JavaFX: abren sockets y pueden tardar.</li>
 *   <li>y por eso {@link #setLobby} llega en ese hilo de fondo. La pantalla
 *       tiene que existir <b>antes</b>, creada en el hilo de JavaFX; aqui solo
 *       se le ata el lobby. Crear nodos aqui seria crearlos fuera de su hilo.</li>
 * </ol>
 */
public final class NeoOnline implements IOnlineLobby, IOnlineChatInterface {

    /** Lo que la pantalla tiene que saber hacer para que esto funcione. */
    public interface View extends ILobbyView {
        /** Ata el lobby (del servidor o del cliente) a la pantalla. */
        void bindLobby(GameLobby lobby);

        /** Un mensaje de chat, del sistema o de alguien. */
        void addChat(ChatMessage message);

        /** Se ha caido la conexion. Llega en un hilo cualquiera. */
        void connectionLost(String message);
    }

    private final View view;
    private volatile FGameClient client;
    private volatile IRemote remote;

    public NeoOnline(final View view) {
        this.view = view;
    }

    // ------------------------------------------------------------------
    // Arrancar
    // ------------------------------------------------------------------

    /**
     * Hospedar.
     *
     * <p>La pregunta del UPnP se contesta <b>antes</b> y a proposito. Forge la
     * hace dentro de {@code startServer} con un {@code SOptionPane}, y ese
     * dialogo tendria que salir y bloquear en mitad de una llamada que ya viene
     * de un hilo de fondo: es justo la clase de dialogo modal cruzado que cuelga
     * esta interfaz. Preguntandolo nosotros en la pantalla, con una casilla, el
     * motor no llega a preguntar nada.
     *
     * <p>Se escribe <b>solo en memoria</b> (sin {@code save()}): es una decision
     * de esta partida, no un ajuste que haya que heredar.
     *
     * @return el mensaje que Forge quiere que se enseñe (puerto, o el fallo)
     */
    public ChatMessage host(final boolean useUpnp) {
        FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.UPnP,
                useUpnp ? "ALWAYS" : "NEVER");
        ensureName();
        return NetConnectUtil.host(this, this);
    }

    /**
     * Unirse a la partida de otro.
     *
     * @param url {@code ip} o {@code ip:puerto}; Forge lo valida y pone el
     *            puerto por defecto si falta
     */
    public ChatMessage join(final String url) {
        ensureName();
        return NetConnectUtil.join(url, this, this);
    }

    /**
     * El nombre con el que te sientas.
     *
     * <p>Forge lo pediria con un dialogo si estuviera vacio
     * ({@code NetConnectUtil.ensurePlayerName}), y ese dialogo sale en mitad de
     * la conexion. Aqui ya tenemos uno elegido en Personalizacion, asi que se
     * usa ese y la pregunta no llega a hacerse.
     */
    private static void ensureName() {
        final String current = FModel.getPreferences().getPref(FPref.PLAYER_NAME);
        if (current == null || current.isBlank()) {
            FModel.getPreferences().setPref(FPref.PLAYER_NAME, NeoLook.playerName());
        }
    }

    // ------------------------------------------------------------------
    // Chat
    // ------------------------------------------------------------------

    /**
     * Manda una linea de chat.
     *
     * <p>El anfitrion y el invitado escriben por sitios distintos y el
     * {@code IRemote} que nos da Forge ya lo resuelve: en el anfitrion es un
     * envoltorio que primero mira si la linea es un <b>comando</b>
     * ({@code /skipreconnect}) y solo si no lo es la reparte.
     */
    public void say(final String text) {
        final IRemote r = remote;
        if (r == null || text == null || text.isBlank()) {
            return;
        }
        r.send(new MessageEvent(text.trim()));
    }

    @Override
    public void setGameClient(final IRemote remote) {
        this.remote = remote;
    }

    @Override
    public void addMessage(final ChatMessage message) {
        view.addChat(message);
    }

    // ------------------------------------------------------------------
    // IOnlineLobby
    // ------------------------------------------------------------------

    @Override
    public ILobbyView setLobby(final GameLobby lobby) {
        // Llega en el hilo de fondo. La pantalla ya existe: aqui solo se ata.
        view.bindLobby(lobby);
        return view;
    }

    @Override
    public void setClient(final FGameClient client) {
        this.client = client;
    }

    @Override
    public void closeConn(final String msg) {
        view.connectionLost(msg);
    }

    // ------------------------------------------------------------------
    // Cerrar
    // ------------------------------------------------------------------

    /**
     * Deja de hospedar o de estar conectado, segun el papel.
     *
     * <p>Se puede llamar siempre: si no habia nada abierto no hace nada.
     */
    public void leave() {
        final FGameClient c = client;
        if (c != null) {
            c.close();
            client = null;
        }
        NeoLobby.stopHosting();
        remote = null;
    }

    public boolean isHost() {
        return client == null && FServerManager.getInstance().isHosting();
    }

    public boolean isGuest() {
        return client != null;
    }

    /** La direccion que hay que pasarle a los amigos, ya formateada. */
    public static String shareAddress() {
        final String local = FServerManager.getLocalAddress();
        return String.format(Locale.ROOT, "%s:%d", local, NeoLobby.port());
    }

    /**
     * La direccion desde fuera de casa, o null.
     *
     * <p><b>Consulta a internet</b> ({@code checkip.amazonaws.com}), asi que
     * NUNCA desde el hilo de JavaFX: puede tardar segundos o no contestar.
     */
    public static String externalAddress(final Consumer<String> whenKnown) {
        final Thread t = new Thread(() -> {
            String out = null;
            try {
                final String ip = FServerManager.getExternalAddress();
                if (ip != null && !ip.isBlank()) {
                    out = String.format(Locale.ROOT, "%s:%d", ip.trim(), NeoLobby.port());
                }
            } catch (final Exception e) {
                System.out.println("[lobby] no se ha podido averiguar la IP externa: " + e);
            }
            whenKnown.accept(out);
        }, "neo-external-ip");
        t.setDaemon(true);
        t.start();
        return null;
    }
}
