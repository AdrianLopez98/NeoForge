package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.deck.Deck;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.ChatMessage;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gui.GuiBase;
import forge.interfaces.IPlayerChangeListener;
import forge.neo.NeoText;
import forge.neo.match.NeoFormat;
import forge.neo.net.NeoLobby;
import forge.neo.net.NeoOnline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * La sala de espera de una partida privada.
 *
 * <p>Es la MISMA pantalla para el anfitrion y para el invitado, y eso no es
 * pereza: <b>quien puede tocar que lo decide el motor</b>, no nosotros.
 * {@code GameLobby} publica {@code hasControl()}, {@code mayEdit(i)},
 * {@code mayControl(i)} y {@code mayRemove(i)}, y las dos implementaciones
 * ({@code ServerGameLobby} y {@code ClientGameLobby}) ya contestan lo que toca:
 * el invitado solo puede editar SU asiento, y solo el anfitrion puede empezar.
 * Escribir esas reglas otra vez aqui seria inventarse una segunda version de
 * algo que ya existe, y que ademas el servidor <b>valida igualmente</b> cuando
 * llega el evento.
 *
 * <p><b>Los avisos llegan en cualquier hilo</b> — el de netty, el de fondo que
 * conecto — asi que todo lo que toca nodos pasa por {@code Platform.runLater}.
 */
public class LobbyScreen extends BorderPane implements NeoOnline.View {

    /** Que hacer cuando el jugador se va o cuando arranca la partida. */
    public interface Actions {
        /** Volver al menu. */
        void back();

        /** La partida ha empezado: hay que enseñar la mesa. */
        void gameStarting();
    }

    private final Actions actions;
    private final boolean host;
    private final double tileWidth;

    private volatile GameLobby lobby;
    private volatile IPlayerChangeListener playerChange;

    private final VBox seatBox = new VBox(8);
    private final VBox chatLines = new VBox(2);
    private final ScrollPane chatScroll = new ScrollPane(chatLines);
    private final TextField chatInput = new TextField();
    private final Label status = new Label();
    private final Label addressLabel = new Label();
    private final Button startButton = new Button(NeoText.get("lobby.start"));
    private final Overlay overlay = new Overlay();
    private final StackPane rootStack;

    /** Para no llamar dos veces a gameStarting si llegan dos avisos juntos. */
    private final AtomicBoolean started = new AtomicBoolean();

    private NeoOnline online;

    public LobbyScreen(final boolean host, final double tileWidth, final Actions actions) {
        this.host = host;
        this.tileWidth = tileWidth;
        this.actions = actions;
        getStyleClass().addAll("table-root", "home");

        setTop(header());
        setCenter(centre());
        setBottom(footer());

        rootStack = new StackPane(this, overlay);
    }

    /** Lo que hay que meter en la escena: la pantalla con su capa de dialogos. */
    public Region getRoot() {
        return rootStack;
    }

    public void setOnline(final NeoOnline online) {
        this.online = online;
    }

    public Overlay getOverlay() {
        return overlay;
    }

    // ------------------------------------------------------------------
    // Montaje
    // ------------------------------------------------------------------

    private Region header() {
        final Label title = new Label(NeoText.get(host ? "lobby.title.host" : "lobby.title.guest"));
        title.getStyleClass().add("home-title");

        status.getStyleClass().add("home-subtitle");
        status.setText(NeoText.get("lobby.connecting"));

        final VBox words = new VBox(2, title, status);

        final VBox box = new VBox(10, words);
        if (host) {
            box.getChildren().add(addressRow());
        }
        box.setPadding(new Insets(20, 30, 12, 30));
        return box;
    }

    /**
     * La direccion que hay que pasarle a los amigos.
     *
     * <p>Se enseña la local desde el primer momento (que es la que sirve en
     * casa) y la externa <b>cuando llegue</b>, porque averiguarla es consultar a
     * internet y puede tardar o fallar. Un hueco que se rellena solo es mejor
     * que una pantalla que se queda esperando.
     */
    private Region addressRow() {
        addressLabel.getStyleClass().add("mode-tile-note");
        addressLabel.setText(NeoText.get("lobby.address", NeoOnline.shareAddress()));

        final Button copy = new Button(NeoText.get("lobby.copy"));
        copy.getStyleClass().add("btn-secondary");
        copy.setMinWidth(Region.USE_PREF_SIZE);
        copy.setOnAction(e -> {
            GuiBase.getInterface().copyToClipboard(NeoOnline.shareAddress());
            addChatLine(NeoText.get("lobby.copied"));
        });

        final HBox row = new HBox(10, addressLabel, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Region centre() {
        seatBox.setPadding(new Insets(4, 0, 4, 0));

        final ScrollPane seats = new ScrollPane(seatBox);
        seats.getStyleClass().add("dialog-scroll");
        seats.setFitToWidth(true);
        seats.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(seats, Priority.ALWAYS);

        final Label seatsTitle = new Label(NeoText.get("lobby.seats"));
        seatsTitle.getStyleClass().add("caption");

        final VBox left = new VBox(8, seatsTitle, seats);
        left.setPadding(new Insets(0, 16, 0, 30));
        HBox.setHgrow(left, Priority.ALWAYS);

        final HBox row = new HBox(16, left, chatPane());
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    /**
     * El chat.
     *
     * <p>No es un adorno: es lo unico que tienen los que estan esperando para
     * decirse "ya estoy" o "dame un minuto". Y en el anfitrion ademas acepta los
     * comandos de Forge ({@code /skipreconnect}, {@code /skiptimeout}), que se
     * mandan tal cual: el envoltorio de {@code NetConnectUtil} los reconoce
     * antes de repartir la linea.
     */
    private Region chatPane() {
        final Label title = new Label(NeoText.get("lobby.chat"));
        title.getStyleClass().add("caption");

        chatLines.setPadding(new Insets(6, 8, 6, 8));
        chatScroll.getStyleClass().add("dialog-scroll");
        chatScroll.setFitToWidth(true);
        chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(chatScroll, Priority.ALWAYS);
        // Se queda pegado abajo: lo ultimo que se ha dicho es lo que importa.
        chatLines.heightProperty().addListener((o, a, b) -> chatScroll.setVvalue(1.0));

        chatInput.setPromptText(NeoText.get("lobby.chatHint"));
        chatInput.setOnAction(e -> {
            final NeoOnline o = online;
            if (o != null) {
                o.say(chatInput.getText());
            }
            chatInput.clear();
        });

        final VBox box = new VBox(8, title, chatScroll, chatInput);
        box.setPrefWidth(320);
        box.setMinWidth(260);
        box.setPadding(new Insets(0, 30, 0, 0));
        return box;
    }

    private Region footer() {
        final Button back = new Button(NeoText.get("lobby.leave"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        final HBox row = new HBox(10, back, gap);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setPadding(new Insets(14, 30, 20, 30));

        if (host) {
            final Button addAi = new Button(NeoText.get("lobby.addAi"));
            addAi.getStyleClass().add("btn-secondary");
            addAi.setMinWidth(Region.USE_PREF_SIZE);
            addAi.setOnAction(e -> addSeat(LobbySlotType.AI));

            final Button addOpen = new Button(NeoText.get("lobby.addOpen"));
            addOpen.getStyleClass().add("btn-secondary");
            addOpen.setMinWidth(Region.USE_PREF_SIZE);
            addOpen.setOnAction(e -> addSeat(LobbySlotType.OPEN));

            startButton.getStyleClass().add("btn-primary");
            startButton.setMinWidth(Region.USE_PREF_SIZE);
            startButton.setOnAction(e -> start());

            row.getChildren().addAll(addAi, addOpen, startButton);
        }
        return row;
    }

    // ------------------------------------------------------------------
    // Los asientos
    // ------------------------------------------------------------------

    /**
     * Una fila por asiento.
     *
     * <p>Se reconstruye entera en cada aviso. Con seis filas eso no cuesta nada
     * y evita el error clasico de que una fila se quede con el estado de otra
     * cuando el anfitrion quita un asiento del medio.
     */
    private void rebuildSeats() {
        final GameLobby l = lobby;
        seatBox.getChildren().clear();
        if (l == null) {
            return;
        }
        for (int i = 0; i < l.getNumberOfSlots(); i++) {
            seatBox.getChildren().add(seatRow(l, i));
        }
    }

    private Region seatRow(final GameLobby l, final int index) {
        final LobbySlot slot = l.getSlot(index);
        final boolean open = slot == null || slot.getType() == LobbySlotType.OPEN;

        final Label who = new Label(open
                ? NeoText.get("lobby.slot.open")
                : slot.getName() == null ? "?" : slot.getName());
        who.getStyleClass().add("mode-tile-name");
        who.setMinWidth(140);

        final Label kind = new Label(kindLabel(l, index, slot));
        kind.getStyleClass().add("mode-tile-note");
        kind.setMinWidth(90);

        final HBox row = new HBox(12, who, kind);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mode-tile");
        row.setPadding(new Insets(10, 14, 10, 14));

        if (!open) {
            row.getChildren().add(deckButton(l, index, slot));

            final Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            row.getChildren().add(gap);

            row.getChildren().add(readyButton(l, index, slot));
        } else {
            final Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            row.getChildren().add(gap);
        }

        if (l.mayRemove(index)) {
            final Button remove = new Button("✕");
            remove.getStyleClass().add("btn-secondary");
            remove.setMinWidth(Region.USE_PREF_SIZE);
            remove.setOnAction(e -> {
                l.removeSlot(index);
                refresh();
            });
            row.getChildren().add(remove);
        }
        return row;
    }

    /**
     * Quien es cada uno, visto desde ESTA pantalla.
     *
     * <p><b>El tipo de asiento no sirve para esto</b>, y es el fallo que costo
     * una captura: {@code LobbySlotType} esta escrito desde el punto de vista
     * del <b>servidor</b> y viaja por el cable tal cual. Para el invitado, el
     * asiento marcado {@code LOCAL} es el del ANFITRION, y el suyo propio
     * llega marcado {@code REMOTE}. Rotularlo por el tipo le decia "tu" a la
     * fila del otro.
     *
     * <p>Quien soy yo se pregunta distinto en cada lado, y en los dos lo
     * contesta el motor:
     * <ul>
     *   <li>invitado: {@code ClientGameLobby.mayEdit(i)} es exactamente
     *       {@code i == localPlayer}, o sea "este es mi asiento";</li>
     *   <li>anfitrion: el suyo es el {@code LOCAL} — ahi {@code mayEdit}
     *       tambien vale para las IA, asi que no distinguiria.</li>
     * </ul>
     */
    private String kindLabel(final GameLobby l, final int index, final LobbySlot slot) {
        if (slot == null || slot.getType() == LobbySlotType.OPEN) {
            return NeoText.get("lobby.kind.waiting");
        }
        if (isMe(l, index, slot)) {
            return NeoText.get("lobby.kind.you");
        }
        return slot.getType() == LobbySlotType.AI
                ? NeoText.get("lobby.kind.ai")
                : NeoText.get("lobby.kind.friend");
    }

    private boolean isMe(final GameLobby l, final int index, final LobbySlot slot) {
        return host ? slot.getType() == LobbySlotType.LOCAL : l.mayEdit(index);
    }

    private Button deckButton(final GameLobby l, final int index, final LobbySlot slot) {
        final Deck deck = slot.getDeck();
        final Button b = new Button(deck == null
                ? NeoText.get("lobby.pickDeck")
                : shorten(deck.getName()));
        b.getStyleClass().add("btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE);
        // mayEdit lo contesta el motor: en el invitado solo es true para SU
        // asiento, y en el anfitrion para todos menos los remotos.
        b.setDisable(!l.mayEdit(index));
        b.setOnAction(e -> pickDeck(index));
        return b;
    }

    private Button readyButton(final GameLobby l, final int index, final LobbySlot slot) {
        final boolean ready = slot.isReady();
        final Button b = new Button(NeoText.get(ready ? "lobby.ready" : "lobby.notReady"));
        b.getStyleClass().add(ready ? "btn-primary" : "btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE);
        // Sin mazo no se puede estar listo: el motor lo rechazaria al empezar y
        // el aviso saldria mucho despues, cuando ya nadie sabe de que va.
        b.setDisable(!l.mayEdit(index) || slot.getDeck() == null);
        b.setOnAction(e -> send(index, UpdateLobbyPlayerEvent.isReadyUpdate(!ready)));
        return b;
    }

    private static String shorten(final String name) {
        return name == null ? "" : name.length() <= 20 ? name : name.substring(0, 19) + "…";
    }

    // ------------------------------------------------------------------
    // Acciones
    // ------------------------------------------------------------------

    /**
     * Elegir mazo, con la misma rejilla visual que en el resto del juego.
     *
     * <p>Los preconstruidos van en su pestanya: quien no se haya montado un
     * mazo de Commander tiene 173 con los que sentarse igualmente.
     */
    private void pickDeck(final int index) {
        final List<Deck> mine = new ArrayList<>();
        final List<Deck> stock = new ArrayList<>();
        forge.model.FModel.getDecks().getCommander().forEach(mine::add);
        forge.model.FModel.getDecks().getCommanderPrecons().forEach(stock::add);

        final DeckPickerDialog picker = new DeckPickerDialog(
                NeoText.get("lobby.pickDeckTitle"), mine, stock, tileWidth * 0.72,
                chosen -> {
                    overlay.hide();
                    if (chosen != null) {
                        send(index, UpdateLobbyPlayerEvent.deckUpdate(chosen));
                    }
                },
                overlay::hide);
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(picker);
    }

    /**
     * Manda un cambio de asiento.
     *
     * <p>Los dos lados usan el MISMO camino: el {@code IPlayerChangeListener}
     * que nos dio Forge al conectar. En el anfitrion apunta a
     * {@code FServerManager.updateSlot} (que aplica y reparte); en el invitado,
     * a {@code client.send}. La pantalla no tiene que saber cual de los dos es.
     */
    private void send(final int index, final UpdateLobbyPlayerEvent event) {
        final IPlayerChangeListener l = playerChange;
        if (l != null) {
            l.update(index, event);
        }
        refresh();
    }

    private void addSeat(final LobbySlotType type) {
        final GameLobby l = lobby;
        if (l == null || l.getNumberOfSlots() >= NeoLobby.MAX_SEATS) {
            return;
        }
        l.addSlot();
        final int index = l.getNumberOfSlots() - 1;
        if (type == LobbySlotType.AI) {
            // El evento lo compone NeoLobby: ahi vive la regla del nombre, y
            // ahi la puede probar el comprobador sin ventana.
            send(index, NeoLobby.aiSeatEvent(l));
        }
        refresh();
    }


    /**
     * Empezar.
     *
     * <p>{@code startGame()} devuelve null y <b>ya ha dicho por que</b> con su
     * propio aviso (falta un mazo, alguien no esta listo, un mazo no es legal en
     * Commander). No hay que repetir esas comprobaciones aqui: se harian peor y
     * se quedarian desfasadas.
     *
     * <p>El {@code Runnable} se ejecuta en un hilo de fondo porque monta la
     * partida y arranca el motor: en el hilo de JavaFX congelaria la ventana.
     */
    private void start() {
        final GameLobby l = lobby;
        if (l == null) {
            return;
        }
        startButton.setDisable(true);
        final Thread t = new Thread(() -> {
            // Las DOS mitades van dentro del try, y eso no es celo: el fallo
            // real que hubo jugando (una IA sin nombre) no revienta en
            // startGame() sino DENTRO del Runnable que devuelve, que es donde
            // se monta la partida. Con el go.run() fuera, la excepcion se la
            // llevaba el hilo, el boton se quedaba apagado y lo que veia el
            // anfitrion era que EMPEZAR no hacia nada. Un boton que no hace lo
            // que dice es peor que no tenerlo.
            try {
                final Runnable go = l.startGame();
                if (go == null) {
                    // startGame() ya ha dicho por que con su propio aviso.
                    Platform.runLater(() -> startButton.setDisable(false));
                    return;
                }
                go.run();
            } catch (final Exception | LinkageError e) {
                System.out.println("[lobby] no se ha podido empezar: " + e);
                e.printStackTrace();
                setNotice(NeoText.get("lobby.startFailed", String.valueOf(e)));
                Platform.runLater(() -> startButton.setDisable(false));
            }
        }, "neo-lobby-start");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------
    // NeoOnline.View / ILobbyView — todo esto llega en hilos cualesquiera
    // ------------------------------------------------------------------

    @Override
    public void bindLobby(final GameLobby lobby) {
        this.lobby = lobby;
        // Commander, y lo pone el anfitrion: el invitado lo recibe por el cable.
        if (host) {
            NeoLobby.setCommander(lobby);
        }
        refresh();
    }

    @Override
    public void setPlayerChangeListener(final IPlayerChangeListener listener) {
        this.playerChange = listener;
    }

    @Override
    public void update(final boolean fullUpdate) {
        refresh();
    }

    @Override
    public void update(final int slot, final LobbySlotType type) {
        refresh();
    }

    /**
     * Repinta.
     *
     * <p>Ademas mira si la partida ha empezado. El aviso no llega por el lobby
     * — {@code onGameStarted()} esta VACIO en las dos implementaciones de Forge
     * — asi que quien de verdad avisa es {@code NeoMatchUI.openView}. Esto es
     * solo el respaldo para el anfitrion, que si tiene {@code isMatchActive()}.
     */
    private void refresh() {
        Platform.runLater(() -> {
            final GameLobby l = lobby;
            if (l == null) {
                return;
            }
            rebuildSeats();
            status.setText(statusText(l));
            if (host) {
                startButton.setDisable(l.findFirstUnreadySlot() != null
                        || NeoLobby.activeSlots(l).size() < NeoLobby.MIN_SEATS);
            }
            if (l.isMatchActive() && started.compareAndSet(false, true)) {
                actions.gameStarting();
            }
        });
    }

    private String statusText(final GameLobby l) {
        final int seated = NeoLobby.activeSlots(l).size();
        final LobbySlot unready = l.findFirstUnreadySlot();
        if (seated < NeoLobby.MIN_SEATS) {
            return NeoText.get("lobby.waitingPlayers");
        }
        if (unready != null) {
            return NeoText.get("lobby.waitingReady", unready.getName());
        }
        return host ? NeoText.get("lobby.readyToStart", seated)
                : NeoText.get("lobby.waitingHost");
    }

    @Override
    public void addChat(final ChatMessage message) {
        if (message == null) {
            return;
        }
        final String src = message.getSource();
        addChatLine(src == null || src.isBlank()
                ? message.getMessage()
                : src + ": " + message.getMessage());
    }

    private void addChatLine(final String text) {
        Platform.runLater(() -> {
            final Label l = new Label(text);
            l.getStyleClass().add("mode-tile-note");
            l.setWrapText(true);
            l.setMaxWidth(290);
            l.setMinHeight(Region.USE_PREF_SIZE);
            chatLines.getChildren().add(l);
            // Un chat que crece sin fin se come la memoria de una partida larga.
            while (chatLines.getChildren().size() > 200) {
                chatLines.getChildren().remove(0);
            }
        });
    }

    @Override
    public void connectionLost(final String message) {
        addChatLine(message == null ? NeoText.get("lobby.lost") : message);
        Platform.runLater(() -> {
            status.setText(NeoText.get("lobby.lost"));
            startButton.setDisable(true);
        });
    }

    /**
     * Piloto de la sala, SOLO para probar ({@code --lobby-auto}).
     *
     * <p>Hace lo que haria una persona y por los mismos botones: elegir mazo,
     * ponerse listo y, si eres el anfitrion, darle a EMPEZAR cuando ya se
     * puede. Existe porque el camino que hay que comprobar — jugar en red y
     * <b>volver a la sala al acabar</b> — necesita dos ordenadores, y desde una
     * sesion sin manos no hay otra forma de recorrerlo.
     *
     * <p>No se activa jugando: solo con la bandera.
     */
    public void autoDriveForTest() {
        final javafx.animation.Timeline t = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> {
                    final GameLobby l = lobby;
                    if (l == null) {
                        return;
                    }
                    for (int i = 0; i < l.getNumberOfSlots(); i++) {
                        final LobbySlot s = l.getSlot(i);
                        if (s == null || s.getType() == LobbySlotType.OPEN || !isMe(l, i, s)) {
                            continue;
                        }
                        if (s.getDeck() == null) {
                            final List<Deck> mine = new ArrayList<>();
                            forge.model.FModel.getDecks().getCommander().forEach(mine::add);
                            if (!mine.isEmpty()) {
                                System.out.println("[lobby-auto] mi mazo: " + mine.get(0).getName());
                                send(i, UpdateLobbyPlayerEvent.deckUpdate(mine.get(0)));
                            }
                            return;
                        }
                        if (!s.isReady()) {
                            System.out.println("[lobby-auto] listo");
                            send(i, UpdateLobbyPlayerEvent.isReadyUpdate(true));
                            return;
                        }
                    }
                    if (host && !startButton.isDisabled()) {
                        System.out.println("[lobby-auto] EMPEZAR");
                        startButton.fire();
                    }
                }));
        t.setCycleCount(javafx.animation.Animation.INDEFINITE);
        t.play();
    }

    /**
     * La partida ha terminado y se vuelve a la sala.
     *
     * <p>Hay que rearmar dos cosas o la sala queda inservible: la marca de "ya
     * ha empezado" — que es de un solo uso — y el boton de EMPEZAR, que se
     * apago al lanzar la partida anterior. Sin esto se vuelve a una sala que se
     * ve bien pero en la que no se puede jugar otra.
     */
    public void matchEnded() {
        started.set(false);
        Platform.runLater(() -> startButton.setDisable(false));
        refresh();
    }

    /** Lo dice el anfitrion al arrancar, con el puerto o con el fallo. */
    public void setNotice(final String text) {
        if (text != null && !text.isBlank()) {
            addChatLine(text);
        }
    }

    /** Que formato se juega. Hoy siempre Commander; queda dicho, no adivinado. */
    public static NeoFormat format() {
        return NeoFormat.COMMANDER;
    }
}
