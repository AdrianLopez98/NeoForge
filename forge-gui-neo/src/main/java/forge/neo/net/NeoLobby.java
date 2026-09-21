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
import forge.neo.match.NeoFormat;
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
 * <p><b>A que se juega lo decide el anfitrion</b> y viaja solo: las variantes
 * van dentro del {@code GameLobbyData} que se reparte en cada cambio. Ver
 * {@link #FORMATS}, {@link #setFormat} y {@link #formatOf} — esa ultima es la
 * que hay que usar SIEMPRE para preguntar el modo, porque
 * {@code lobby.getGameType()} miente en el invitado.
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
        markHosting();

        final FServerManager server = FServerManager.getInstance();
        final ServerGameLobby lobby = new ServerGameLobby();
        server.startServer(port());
        server.setLobby(lobby);
        System.out.printf(Locale.ROOT, "[lobby] hospedando en %s:%d%n", localAddress(), port());
        return lobby;
    }

    /**
     * Estamos hospedando una sala: la partida que monte el lobby es nuestra.
     *
     * <p>Una bandera propia y no {@code FServerManager.getInstance().isHosting()}
     * porque quien la lee es {@code NeoGuiBase.hostMatch}, por el que pasan
     * TODAS las partidas, y {@code getInstance()} crea el singleton con sus dos
     * grupos de hilos de netty: pagarlo en cada partida suelta seria arrancar
     * un servidor a medias para nada.
     */
    private static volatile boolean hostingLobby;

    /** Lo llaman los dos caminos que hospedan: este y {@code NeoOnline.host}. */
    public static void markHosting() {
        hostingLobby = true;
    }

    /** Si la partida que se va a montar sale de NUESTRA sala. Ver {@link NetHostedMatch}. */
    public static boolean isHostingLobby() {
        return hostingLobby;
    }

    /** Cierra el servidor. Idempotente: se puede llamar sin estar hospedando. */
    public static void stopHosting() {
        hostingLobby = false;
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
     * Deja el lobby en Commander. Ver {@link #setFormat}.
     *
     * <p><b>Esto es lo que hace que Commander sea Commander</b>, y el lobby lo
     * hace bien por construccion: {@code applyVariant} escribe en los DOS sitios
     * de {@code GameRules} — el tipo actual y el conjunto de variantes — que es
     * justo la trampa que ya nos costo una tarde en {@code NeoGame}. Sin el
     * conjunto no se pregunta por devolver el comandante a la zona de mando
     * (CR 903.9a) ni se pierde por 21 de danyo de comandante.
     */
    public static void setCommander(final GameLobby lobby) {
        setFormat(lobby, NeoFormat.COMMANDER);
    }

    // ------------------------------------------------------------------
    // Que se juega
    // ------------------------------------------------------------------

    /**
     * Los modos que se pueden elegir en una partida privada.
     *
     * <p>Son los cinco del menu principal ({@code isInMainMenu}) mas los dos
     * que <b>no necesitan mazo</b>: Momir Basic y MoJhoSto montan el suyo solos
     * ({@code GameType.autoGenerateDeck}), asi que dos amigos que no se hayan
     * construido nada pueden jugar igualmente.
     *
     * <p>No estan ni el draft ni el sellado: en red esos no son un modo de la
     * sala, son un <b>evento</b> entero de Forge (repartir sobres, pasar picks)
     * con su propia maquinaria. Y los "otros formatos" (Modern, Pauper...)
     * tampoco: no son un modo distinto — son el mismo Construido con menos
     * cartas — y el lobby de Forge no sabe recortar el pozo, solo mira las
     * reglas de construccion.
     */
    public static final List<NeoFormat> FORMATS = List.of(
            NeoFormat.COMMANDER, NeoFormat.ESTANDAR, NeoFormat.BRAWL,
            NeoFormat.OATHBREAKER, NeoFormat.TINY_LEADERS,
            NeoFormat.MOMIR, NeoFormat.MOJHOSTO);

    /**
     * Que se esta jugando en esta sala, visto desde <b>cualquiera de los dos
     * lados</b>.
     *
     * <p><b>Se lee de las variantes y no de {@code lobby.getGameType()}</b>, y
     * esa es la trampa: {@code currentGameType} es un campo suelto del
     * {@code GameLobby} y <b>no viaja por el cable</b> — lo que se reparte es el
     * {@code GameLobbyData}, y ahi dentro solo esta el conjunto de variantes.
     * Preguntandole el tipo al lobby del invitado contesta siempre
     * {@code Constructed}, o sea que su pantalla diria "Estandar" en una partida
     * de Commander y le dejaria elegir el mazo equivocado.
     *
     * <p>El orden de las preguntas es el MISMO que usa
     * {@code GameLobby.startGame()} para decidir a que se juega, porque la
     * respuesta tiene que ser la que va a aplicar el motor y no la nuestra.
     */
    public static NeoFormat formatOf(final GameLobby lobby) {
        final EnumSet<GameType> applied = variants(lobby);
        if (applied.contains(GameType.Oathbreaker)) {
            return NeoFormat.OATHBREAKER;
        }
        if (applied.contains(GameType.TinyLeaders)) {
            return NeoFormat.TINY_LEADERS;
        }
        if (applied.contains(GameType.Brawl)) {
            return NeoFormat.BRAWL;
        }
        if (applied.contains(GameType.Commander)) {
            return NeoFormat.COMMANDER;
        }
        if (applied.contains(GameType.MoJhoSto)) {
            return NeoFormat.MOJHOSTO;
        }
        if (applied.contains(GameType.MomirBasic)) {
            return NeoFormat.MOMIR;
        }
        // Sin variantes es Construido, que es como lo entiende el motor:
        // startGame() hace new RegisteredPlayer(deck) cuando el conjunto esta
        // vacio. "Estandar" no es una variante, es la ausencia de todas.
        return NeoFormat.ESTANDAR;
    }

    /**
     * Cambia el modo de la sala. <b>Solo el anfitrion.</b>
     *
     * <p>Lo primero que hace es <b>quitarle el mazo y el "listo" a todo el
     * mundo</b>, y no es celo: un mazo de Commander en una sala de Estandar no
     * es un mazo peor, es uno que el motor rechaza al empezar — y el aviso
     * saldria minutos despues, al darle a EMPEZAR, cuando ya nadie recuerda que
     * se cambio el modo. Ademas el invitado tenia el "Listo" puesto sobre una
     * decision que ya no vale.
     *
     * <p><b>Estandar se pone poniendolo y quitandolo, y tiene su motivo.</b> Lo
     * que hay que dejar es el conjunto de variantes <i>vacio</i>, pero
     * {@code clearVariants()} no avisa a nadie — no llama a {@code updateView}
     * — asi que el invitado se quedaria con el modo de antes hasta que alguien
     * tocara otra cosa en la sala. {@code applyVariant} + {@code removeVariant}
     * son los dos metodos publicos que si reparten el estado, y terminan justo
     * donde hay que terminar: sin variantes y con el tipo en
     * {@code Constructed}.
     */
    public static void setFormat(final GameLobby lobby, final NeoFormat format) {
        clearDecksAndReady(lobby);
        lobby.clearVariants();
        if (format.getGameType() == GameType.Constructed) {
            lobby.applyVariant(GameType.Constructed);
            lobby.removeVariant(GameType.Constructed);
        } else {
            lobby.applyVariant(format.getGameType());
        }
    }

    /**
     * Deja a todos sin mazo y sin marcar "listo".
     *
     * <p>Se escribe <b>directamente en el asiento</b>, como
     * {@link #clearOpenSeats}, y no con un {@code UpdateLobbyPlayerEvent}: el
     * anfitrion no puede editar un asiento remoto
     * ({@code ServerGameLobby.mayEdit} dice que no) y aqui hace falta tocar
     * precisamente esos. Vale porque lo que se reparte es el
     * {@code GameLobbyData} entero, asientos incluidos, en el
     * {@code updateView(true)} que viene detras.
     *
     * @return si ha cambiado algo
     */
    public static boolean clearDecksAndReady(final GameLobby lobby) {
        boolean changed = false;
        for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot s = lobby.getSlot(i);
            if (s == null || s.getType() == LobbySlotType.OPEN) {
                continue;
            }
            if (s.getDeck() != null) {
                s.setDeck(null);
                changed = true;
            }
            if (s.isReady()) {
                s.setIsReady(false);
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Si en este modo hay que elegir mazo.
     *
     * <p>En Momir Basic y en MoJhoSto no: el mazo lo monta el motor
     * ({@code GameType.autoGenerateDeck}) y {@code startGame()} ni lo pide. Un
     * boton de "Elegir mazo" ahi seria un control que no hace nada (principio
     * 1), y un "Listo" apagado esperando un mazo que no va a llegar nunca deja
     * la sala muerta sin decir por que.
     */
    public static boolean needsDeck(final NeoFormat format) {
        return !format.isAutoGenerated();
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

    /**
     * Si el anfitrion puede quitar este asiento con la cruz.
     *
     * <p>{@code ServerGameLobby.mayRemove} solo dice "a partir del tercero", y
     * eso no basta: el servidor apunta a cada invitado <b>por su posicion</b>
     * en la lista ({@code RemoteClient.getIndex()}) y {@code removeSlot}
     * desplaza la lista sin avisarle. Quitar el asiento de un amigo lo deja
     * conectado y sin sitio; quitar uno que tenga un amigo DETRAS le cambia la
     * posicion a ese amigo, y a partir de ahi lo que el cambie en su asiento
     * revienta en el servidor y al empezar se queda sin interfaz. Asi que solo
     * se quitan IAs y huecos libres, y solo si ningun invitado viene detras.
     */
    public static boolean mayRemoveSeat(final GameLobby lobby, final int index) {
        if (!lobby.mayRemove(index)) {
            return false;
        }
        final LobbySlot slot = lobby.getSlot(index);
        if (slot == null || slot.getType() == LobbySlotType.REMOTE
                || slot.getType() == LobbySlotType.LOCAL) {
            return false;
        }
        for (int i = index + 1; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot after = lobby.getSlot(i);
            if (after != null && after.getType() == LobbySlotType.REMOTE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lo que hay que mandar para cambiar el mazo de un asiento: DOS eventos.
     *
     * <p><b>Con el mazo solo no basta, y era el fallo mas gordo de la sala.</b>
     * {@code LobbySlot.apply} guarda el mazo pero no lo cuenta como cambio (su
     * {@code changed} solo mira los campos de arriba), asi que
     * {@code applyToSlot} no avisa y el servidor <b>no reparte el estado</b>. El
     * invitado elegia mazo y no le constaba a nadie: ni a el — su "Listo" pide
     * mazo y se quedaba apagado — ni al anfitrion, hasta que alguien tocara
     * otra cosa en la sala. La GUI Swing no lo nota porque no ata "Listo" al
     * mazo; la de movil lo resuelve igual que aqui: detras del mazo manda su
     * NOMBRE ({@code setDeckSchemePlaneVanguard}), que si cuenta como cambio.
     * El orden importa y lo garantiza el canal: cuando llega el nombre, el
     * mazo ya esta puesto, y el reparto que provoca ya lo lleva.
     */
    public static List<UpdateLobbyPlayerEvent> deckEvents(final Deck deck) {
        return List.of(UpdateLobbyPlayerEvent.deckUpdate(deck),
                UpdateLobbyPlayerEvent.setDeckSchemePlaneVanguard(
                        deck == null ? null : deck.getName(), null, null, null));
    }

    /**
     * Un mazo al azar de este modo, para la IA que se acaba de sentar.
     *
     * <p>Se prefieren los que <b>no son tuyos</b> (los preconstruidos que trae
     * Forge): sentar a la IA con tu propio mazo no es un rival, es un espejo.
     * Pero si el modo no trae ninguno — Brawl, Oathbreaker y Tiny Leaders no
     * tienen preconstruidos — vale cualquiera antes que dejar el asiento a
     * medias, y si tampoco hay, se le genera uno de verdad. El ultimo recurso
     * es null: la IA se queda sin mazo y se le elige a mano.
     */
    public static Deck randomAiDeck(final NeoFormat format) {
        if (!needsDeck(format)) {
            return null;   // el motor lo monta solo
        }
        final List<Deck> stock = new ArrayList<>();
        final List<Deck> any = new ArrayList<>();
        for (final Deck d : format.decks()) {
            any.add(d);
            if (!format.isMine(d)) {
                stock.add(d);
            }
        }
        final List<Deck> pool = stock.isEmpty() ? any : stock;
        if (!pool.isEmpty()) {
            return pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
        }
        return generateDeck(format);
    }

    /**
     * Un mazo montado por el generador de Forge, para cuando no hay ninguno.
     *
     * <p>Es la misma fachada que usa la pantalla de inicio para "Generame uno"
     * ({@code HomeScreen.generateOpponentDeck}). Solo sabe de formatos con
     * comandante; en Estandar hay 505 preconstruidos y nunca se llega aqui.
     */
    public static Deck generateDeck(final NeoFormat format) {
        if (!format.isCommanderStyle()) {
            return null;
        }
        try {
            return forge.deck.DeckgenUtil.generateCommanderDeck(true, format.getGameType());
        } catch (final RuntimeException | LinkageError e) {
            System.out.println("[lobby] no se ha podido generar un mazo: " + e);
            return null;
        }
    }

    /**
     * Quita el mazo de los huecos libres.
     *
     * <p>Cuando un amigo se va, el servidor deja su asiento en OPEN
     * ({@code ServerGameLobby.disconnectPlayer}) pero no le quita el mazo, y el
     * siguiente en entrar se lo encontraba puesto: se sentaba con el mazo de
     * otro, y podia darle a "Listo" sin haber elegido nada.
     *
     * @return si ha cambiado algo
     */
    public static boolean clearOpenSeats(final GameLobby lobby) {
        boolean changed = false;
        for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot s = lobby.getSlot(i);
            if (s != null && s.getType() == LobbySlotType.OPEN && s.getDeck() != null) {
                s.setDeck(null);
                changed = true;
            }
        }
        return changed;
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
        // El modo se saca de las variantes, no de getGameType(): en el invitado
        // ese campo dice siempre Constructed. Ver formatOf.
        sb.append(formatOf(lobby)).append(" [");
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
