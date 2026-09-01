package forge.neo.net;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.game.GameType;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.ChatMessage;
import forge.gamemodes.net.client.ClientGameLobby;
import forge.gamemodes.net.client.FGameClient;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.gui.interfaces.IGuiGame;
import forge.interfaces.ILobbyListener;
import forge.localinstance.properties.ForgeNetPreferences;
import forge.model.FModel;
import forge.neo.platform.NeoGuiBase;

/**
 * La partida privada, envuelta.
 *
 * <p><b>Aqui no hay ni una linea de red.</b> Forge trae el cliente y el
 * servidor enteros ({@code forge.gamemodes.net}, ~9.000 lineas mantenidas):
 * netty, UPnP, sincronizacion por deltas con checksums, latidos, ventana de
 * reconexion de 300 s y relevo por la IA si alguien no vuelve. Esto es la
 * fachada que necesita nuestra interfaz para pedirselo, y nada mas.
 *
 * <p><b>El hallazgo que lo hace barato:</b> el protocolo <i>es</i>
 * {@code IGuiGame}. {@code ProtocolMethod} enumera 46 metodos servidor→cliente
 * — exactamente los que {@code NeoMatchUI} ya implementa — y 16
 * cliente→servidor, que son {@code IGameController}, el que ya llamamos. No hay
 * ninguna "API de red" que aprender: por el cable viaja la misma llamada
 * bloqueante que ya atendemos en local.
 *
 * <p><b>Como se reparten los papeles.</b> El anfitrion tiene un
 * {@link ServerGameLobby} y su propia {@code NeoMatchUI}; cada invitado tiene un
 * {@link ClientGameLobby} y la suya. Del lado del anfitrion, cada asiento remoto
 * recibe un {@code RemoteClientGuiGame} que convierte cada llamada de
 * {@code IGuiGame} en un paquete. Eso lo monta Forge solo.
 *
 * <p><b>Lo que NO hace, y conviene saberlo:</b> no hay contrasenya, ni expulsar,
 * ni banear — el lobby de Forge no los tiene. "Privada" aqui significa que solo
 * entra quien conozca la direccion.
 */
public final class NeoLobby {

    /** El tope que se ha decidido para esta aplicacion. El motor admite 8. */
    public static final int MAX_SEATS = 6;

    /** Minimo para que {@code GameLobby.startGame()} deje empezar. */
    public static final int MIN_SEATS = 2;

    private NeoLobby() {
    }

    /** El puerto configurado, que es el que hay que compartir. */
    public static int port() {
        return FModel.getNetPreferences().getPrefInt(ForgeNetPreferences.FNetPref.NET_PORT);
    }

    /**
     * La direccion de la maquina en la red local.
     *
     * <p>Para jugar por internet hace falta la externa
     * ({@code FServerManager.getExternalAddress()}), que sale de consultar a un
     * servicio y puede tardar o no estar.
     */
    public static String localAddress() {
        return FServerManager.getLocalAddress();
    }

    // ------------------------------------------------------------------
    // Anfitrion
    // ------------------------------------------------------------------

    /**
     * Levanta el servidor y devuelve el lobby que hay que pintar.
     *
     * <p>Se hace a mano y no con {@code NetConnectUtil.host(...)} por una razon
     * concreta: aquel exige un {@code IOnlineLobby} y un
     * {@code IOnlineChatInterface} montados de antemano, y ademas fija el
     * oyente del lobby a un chat. Lo que hace de verdad son cuatro llamadas, y
     * asi la pantalla decide cuando se engancha a que.
     *
     * <p><b>La fabrica de interfaces es lo delicado.</b> El asiento LOCAL del
     * anfitrion no lo crea la pantalla: lo pide el motor con
     * {@code IGuiBase.getNewGuiGame()} en mitad de {@code startGame()}. Y esa
     * fabrica es un estatico que nadie limpia, asi que hay que dejarla puesta
     * <b>aqui</b>, al hospedar — si se confiara en la que dejo la ultima
     * partida suelta, el anfitrion jugaria con la {@code NeoMatchUI} de aquella
     * partida, ya terminada.
     *
     * @param guiFactory crea una interfaz NUEVA cada vez que se la llama
     */
    public static ServerGameLobby host(final java.util.function.Supplier<IGuiGame> guiFactory) {
        NeoGuiBase.setGuiGameFactory(guiFactory);

        final FServerManager server = FServerManager.getInstance();
        final ServerGameLobby lobby = new ServerGameLobby();
        server.startServer(port());
        server.setLobby(lobby);
        System.out.printf(Locale.ROOT, "[lobby] hospedando en %s:%d%n", localAddress(), port());
        return lobby;
    }

    /** Cierra el servidor. Idempotente: se puede llamar sin estar hospedando. */
    public static void stopHosting() {
        final FServerManager server = FServerManager.getInstance();
        if (server.isHosting()) {
            server.stopServer();
            System.out.println("[lobby] servidor cerrado");
        }
    }

    public static boolean isHosting() {
        return FServerManager.getInstance().isHosting();
    }

    // ------------------------------------------------------------------
    // Invitado
    // ------------------------------------------------------------------

    /** Lo que le hace falta a la pantalla del invitado para seguir viva. */
    public static final class Guest {
        private final FGameClient client;
        private final ClientGameLobby lobby;

        Guest(final FGameClient client, final ClientGameLobby lobby) {
            this.client = client;
            this.lobby = lobby;
        }

        public FGameClient getClient() {
            return client;
        }

        public ClientGameLobby getLobby() {
            return lobby;
        }

        /** Cambiar mazo, nombre, "listo"... todo va por aqui. */
        public void send(final UpdateLobbyPlayerEvent event) {
            client.send(event);
        }

        public void close() {
            client.close();
        }
    }

    /**
     * Se conecta a la partida de otro.
     *
     * <p>{@code gui} es la {@code NeoMatchUI} con la que jugara este invitado.
     * No hay que engancharle el mando: cuando el anfitrion mande {@code openView},
     * {@code GameClientHandler} llama solo a {@code setGameControllers(...)} y a
     * {@code setNetGame()}.
     *
     * @param onLobby  se llama con cada actualizacion del lobby que manda el anfitrion
     * @param onChat   mensajes del chat
     * @param onClose  la conexion se ha ido
     */
    public static Guest join(final String host, final int port, final String username,
                             final IGuiGame gui,
                             final java.util.function.Consumer<ClientGameLobby> onLobby,
                             final java.util.function.Consumer<ChatMessage> onChat,
                             final Runnable onClose) {
        final FGameClient client = new FGameClient(username, gui, host, port);
        final ClientGameLobby lobby = new ClientGameLobby();

        client.addLobbyListener(new ILobbyListener() {
            @Override
            public void update(final GameLobby.GameLobbyData state, final int slot) {
                // El orden importa: primero quien soy yo, luego el estado. Al
                // reves, la pantalla se pinta sin saber cual es su asiento y
                // sale todo en gris (mayEdit compara con localPlayer).
                lobby.setLocalPlayer(slot);
                lobby.setData(state);
                if (onLobby != null) {
                    onLobby.accept(lobby);
                }
            }

            @Override
            public void message(final String source, final String message,
                                final ChatMessage.MessageType type) {
                if (onChat != null) {
                    onChat.accept(new ChatMessage(source, message, type));
                }
            }

            @Override
            public void close() {
                if (onClose != null) {
                    onClose.run();
                }
            }

            @Override
            public ClientGameLobby getLobby() {
                return lobby;
            }
        });

        client.connect();
        System.out.printf(Locale.ROOT, "[lobby] conectado a %s:%d como %s%n", host, port, username);
        return new Guest(client, lobby);
    }

    // ------------------------------------------------------------------
    // Cosas que valen para los dos lados
    // ------------------------------------------------------------------

    /**
     * Deja el lobby en Commander.
     *
     * <p><b>Esto es lo que hace que Commander sea Commander</b>, y el lobby lo
     * hace bien por construccion: {@code applyVariant} escribe en los DOS sitios
     * de {@code GameRules} — el tipo actual y el conjunto de variantes — que es
     * justo la trampa que ya nos costo una tarde en {@code NeoGame}. Sin el
     * conjunto no se pregunta por devolver el comandante a la zona de mando
     * (CR 903.9a) ni se pierde por 21 de danyo de comandante.
     */
    public static void setCommander(final GameLobby lobby) {
        lobby.clearVariants();
        lobby.applyVariant(GameType.Commander);
    }

    /**
     * El evento que convierte un asiento libre en una IA, <b>con nombre</b>.
     *
     * <p><b>Sin el nombre la partida no empieza, y no dice por que.</b>
     * {@code GameLobby.addSlot()} crea el asiento con el nombre a {@code null}
     * — es lo normal, la GUI vieja lo rellena en cuanto marcas "IA" — y un
     * {@code null} dentro de {@code UpdateLobbyPlayerEvent} significa "este
     * campo no viene", asi que {@code LobbySlot.apply} lo ignora y el asiento
     * se queda sin nombre para siempre.
     *
     * <p>Lo que pasa entonces al darle a EMPEZAR: el motor construye
     * {@code new Player(null, ...)} y el constructor llama a
     * {@code Player.chooseName}, que recorre a los ya sentados haciendo
     * {@code p.getName().equals(...)}. El primero sin nombre no falla —
     * todavia no hay nadie con quien compararlo — pero deja en la mesa un
     * jugador con {@code getName() == null}, y <b>el siguiente</b> revienta con
     * un NPE. Una sola IA sin nombre se lleva la partida entera. Y como salta
     * en un hilo de fondo, lo que se ve es que el boton se apaga y no pasa
     * nada.
     *
     * <p>La regla vive aqui y no en la pantalla para que el comprobador sin
     * ventana pruebe <b>la misma</b>, que es la unica forma de que no vuelva a
     * pasar.
     *
     * <p>El nombre sale del mismo sitio que en una partida normal
     * ({@code NeoPlayers}), asi que tus rivales de siempre se llaman igual
     * jugando con amigos. Se evita repetir uno que ya este en la sala: el motor
     * sabe desempatar — pondria "2o Kaldar" — pero eso se lee peor que dos
     * nombres distintos.
     */
    public static UpdateLobbyPlayerEvent aiSeatEvent(final GameLobby lobby) {
        return UpdateLobbyPlayerEvent.create(LobbySlotType.AI, freeAiName(lobby),
                -1, -1, -1, false, false, java.util.Collections.emptySet(), null);
    }

    /** Un nombre de IA que no este ya cogido en esta sala. Nunca null. */
    public static String freeAiName(final GameLobby lobby) {
        final List<String> taken = new ArrayList<>();
        for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot s = lobby.getSlot(i);
            if (s != null && s.getName() != null) {
                taken.add(s.getName());
            }
        }
        for (int i = 0; i < MAX_SEATS; i++) {
            final String candidate = forge.neo.look.NeoPlayers.aiName(i);
            if (candidate != null && !candidate.isBlank() && !taken.contains(candidate)) {
                return candidate;
            }
        }
        // Con todos cogidos da igual cual salga, pero NUNCA null.
        return "IA-" + lobby.getNumberOfSlots();
    }

    /** Los asientos ocupados, o sea los que van a jugar. */
    public static List<LobbySlot> activeSlots(final GameLobby lobby) {
        final List<LobbySlot> out = new ArrayList<>();
        for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot s = lobby.getSlot(i);
            if (s != null && s.getType() != LobbySlotType.OPEN) {
                out.add(s);
            }
        }
        return out;
    }

    /**
     * Un resumen del lobby en una linea, para el registro.
     *
     * <p>Sale en {@code neo.log} cada vez que cambia algo. Es lo unico que
     * queda cuando un amigo dice "no me deja empezar" y no estas delante.
     */
    public static String describe(final GameLobby lobby) {
        final StringBuilder sb = new StringBuilder();
        sb.append(lobby.getGameType()).append(" [");
        for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot s = lobby.getSlot(i);
            if (i > 0) {
                sb.append(", ");
            }
            if (s == null || s.getType() == LobbySlotType.OPEN) {
                sb.append("libre");
                continue;
            }
            final Deck d = s.getDeck();
            sb.append(s.getType()).append(':').append(s.getName())
              .append(s.isReady() ? " listo" : " NO listo")
              .append(d == null ? " sin mazo" : " (" + d.getName() + ")");
        }
        return sb.append(']').toString();
    }

    /** Las variantes puestas, para comprobar que Commander llego a las dos partes. */
    public static EnumSet<GameType> variants(final GameLobby lobby) {
        final EnumSet<GameType> out = EnumSet.noneOf(GameType.class);
        for (final GameType t : lobby.getAppliedVariants()) {
            out.add(t);
        }
        return out;
    }
}
