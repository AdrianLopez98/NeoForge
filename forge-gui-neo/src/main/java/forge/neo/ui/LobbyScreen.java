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
import forge.neo.net.NetReach;
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

    /**
     * Quien mas quiere enterarse de lo que pasa en la red: la mesa.
     *
     * <p>Mientras se juega, esta pantalla no se ve — y hasta ahora el chat y
     * los avisos del servidor SOLO vivian aqui. Asi que "Pepe se ha
     * desconectado, se le espera 5 minutos" llegaba a una pantalla escondida y
     * la partida se quedaba congelada sin explicacion. Todo lo que llega se
     * sigue apuntando aqui y ademas se le pasa a quien escuche.
     */
    public interface NetListener {
        /** Un mensaje del chat, ya leido y traducido. Llega en un hilo cualquiera. */
        void chat(forge.neo.net.NetPhrases.Phrase phrase);

        /** Se ha perdido la conexion. Llega en un hilo cualquiera. */
        void lost(String message);
    }

    private volatile NetListener netListener;

    public void setNetListener(final NetListener listener) {
        this.netListener = listener;
    }

    /** Lo dicho hasta ahora, para abrir el chat en la mesa con la conversacion entera. */
    private final List<String> history = java.util.Collections.synchronizedList(new ArrayList<>());

    public List<String> history() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

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
     *
     * <p><b>Y si hay una red virtual puesta, esa va la primera y marcada.</b>
     * Antes salian cuatro renglones iguales y el jugador tenia que adivinar
     * cual: la de casa no le vale a nadie de fuera, la de internet puede no
     * existir (CGNAT) y la de Radmin/ZeroTier/Tailscale funciona siempre. Son
     * tres cosas distintas presentadas como una lista, o sea una pregunta
     * disfrazada de informacion.
     */
    private Region addressRow() {
        final VBox rows = new VBox(4);
        final List<String[]> local = NeoOnline.localAddresses();
        if (local.isEmpty()) {
            rows.getChildren().add(addressLine(
                    NeoText.get("lobby.address", NeoOnline.shareAddress()), NeoOnline.shareAddress()));
        }

        // La IPv6 antes que nada: con IPv6 no hay NAT de ningun tipo, asi que
        // el CGNAT deja de existir y no hay que instalar ni pagar nada. Va sin
        // marcar en verde a proposito — tener la direccion no demuestra que se
        // pueda entrar por ella: hacen falta DOS cosas mas que no podemos
        // comprobar desde aqui (que el amigo tambien tenga IPv6 y que el
        // cortafuegos del router la deje entrar, que muchos la cierran de
        // fabrica). Se enseña con lo que hay que saber y se deja decidir.
        final String[] v6 = NeoOnline.globalIpv6();
        if (v6 != null) {
            final Label note = new Label(NeoText.get("lobby.address.ipv6.note"));
            note.getStyleClass().add("lobby-warn-why");
            note.setWrapText(true);
            note.setMaxWidth(560);
            note.setMinHeight(Region.USE_PREF_SIZE);
            rows.getChildren().addAll(
                    addressLine(NeoText.get("lobby.address.ipv6", v6[1]), v6[1]), note);
        }

        // La red virtual primero y con su nombre: es la unica que funciona
        // pase lo que pase con el router, asi que es LA respuesta a "y que le
        // paso a mi amigo".
        final String[] virtual = NeoOnline.virtualLan();
        if (virtual != null) {
            final Label note = new Label(NeoText.get("lobby.address.virtual.note"));
            note.getStyleClass().add("lobby-warn-why");
            note.setWrapText(true);
            note.setMaxWidth(560);
            note.setMinHeight(Region.USE_PREF_SIZE);
            rows.getChildren().addAll(
                    addressLine(NeoText.get("lobby.address.virtual", virtual[0], virtual[1]),
                            virtual[1], true),
                    note);
        }

        // Todas, no solo una: con una VPN de jugar (Radmin, Hamachi...) la
        // buena es la de la VPN, y el sistema no la elegiria. Como mucho
        // cuatro: un portatil con redes virtuales tiene para dar y tomar.
        int shown = 0;
        for (int i = 0; i < local.size() && shown < 4; i++) {
            final String[] a = local.get(i);
            if (virtual != null && virtual[1].equals(a[1])) {
                continue;   // ya esta arriba, y dos veces se lee como dos redes
            }
            rows.getChildren().add(addressLine(NeoText.get("lobby.address.lan", a[0], a[1]), a[1]));
            shown++;
        }

        // La de internet, cuando llegue: hay que preguntar a un servicio de
        // fuera Y al router, y puede tardar o no contestar (sin conexion,
        // nunca). El hueco que se rellena solo sigue siendo mejor que una
        // pantalla esperando — pero se rellena UNA vez y ya sabiendo si la
        // direccion sirve: enseñarla antes y corregirla despues seria darle
        // unos segundos a alguien para copiar una direccion que no lleva a
        // ninguna parte.
        final VBox internet = new VBox(2);
        rows.getChildren().add(internet);
        NetReach.check(reach -> Platform.runLater(() -> fillInternetRow(internet, reach)));
        return rows;
    }

    /**
     * El renglon de "por internet", ya sabiendo si alguien puede llegar.
     *
     * <p><b>Bajo CGNAT no sale la direccion, y no es un olvido.</b> Existe, se
     * puede copiar y no lleva a ninguna parte: el operador reparte esa IP entre
     * muchos clientes y el router de casa no tiene direccion propia. Un boton
     * de "Copiar" al lado seria exactamente el principio 1 — un control que no
     * hace lo que parece es peor que no tenerlo —, y aqui ademas es el control
     * que manda a un amigo a esperar delante de una puerta que no existe.
     *
     * <p>Se dice tambien <b>por el chat</b>, que es donde el motor suelta su
     * {@code lblUPnPSuccess} diciendo lo contrario ("deberian poder conectarse
     * usando tu IP externa") cuando el router acepta el mapeo. Las dos frases
     * tienen que caer en el mismo sitio o gana la optimista.
     *
     * <p>Si no se sabe ({@code UNKNOWN}), la sala se queda <b>igual que
     * siempre</b>: el aviso solo sale cuando el router ha dicho el mismo que
     * por fuera tiene una direccion que no se ve desde internet.
     */
    private void fillInternetRow(final VBox internet, final NetReach.Result reach) {
        if (reach.isCgnat()) {
            // El CGNAT es un problema de IPv4 y de nadie mas. Con una IPv6
            // global en la lista, decir "por internet no va a entrar nadie"
            // seria MENTIR justo encima de la direccion por la que si podrian
            // entrar — y ese es el fallo que esta pantalla existe para no
            // repetir, solo que al reves.
            final boolean hasIpv6 = NeoOnline.globalIpv6() != null;
            final Label warn = new Label(NeoText.get(
                    hasIpv6 ? "lobby.address.cgnat.v4only" : "lobby.address.cgnat"));
            warn.getStyleClass().add("lobby-warn");
            warn.setWrapText(true);
            warn.setMaxWidth(560);
            warn.setMinHeight(Region.USE_PREF_SIZE);

            // Y ahora lo unico que le importa a quien esta leyendo esto: que
            // hago para jugar. Tres situaciones distintas y tres respuestas
            // distintas — la del medio es la que faltaba y la que mas rabia
            // daba: TIENES Radmin, solo que cerrado, y la sala te mandaba a
            // descargar lo que ya tenias instalado.
            final boolean hasVirtual = NeoOnline.virtualLan() != null;
            final String dormant = hasVirtual ? null : NeoOnline.dormantVirtualLan();
            final String fixKey;
            if (hasVirtual) {
                fixKey = "lobby.address.cgnat.useVirtual";
            } else if (dormant != null) {
                fixKey = "lobby.address.cgnat.wakeUp";
            } else if (hasIpv6) {
                // Con IPv6 delante, mandar a instalar Radmin es el consejo
                // equivocado: primero se prueba lo que ya tienes y es gratis.
                fixKey = "lobby.address.cgnat.tryIpv6";
            } else {
                fixKey = "lobby.address.cgnat.fix";
            }
            final Label fix = new Label(dormant != null
                    ? NeoText.get(fixKey, dormant) : NeoText.get(fixKey));
            fix.getStyleClass().add(dormant != null ? "lobby-warn" : "lobby-warn-why");
            fix.setWrapText(true);
            fix.setMaxWidth(560);
            fix.setMinHeight(Region.USE_PREF_SIZE);

            final Label why = new Label(NeoText.get("lobby.address.cgnat.what"));
            why.getStyleClass().add("lobby-warn-why");
            why.setWrapText(true);
            why.setMaxWidth(560);
            why.setMinHeight(Region.USE_PREF_SIZE);

            internet.getChildren().setAll(warn, fix, why);
            // Sin nada instalado, el siguiente paso es una descarga: ponerla a
            // un click. Con Radmin ya puesto NO sale — mandar a descargar lo
            // que ya tienes es el consejo equivocado.
            if (!hasVirtual && dormant == null && !hasIpv6) {
                final Button get = new Button(NeoText.get("lobby.address.cgnat.getIt"));
                get.getStyleClass().add("btn-secondary");
                // La interfaz lo declara con excepciones comprobadas aunque la
                // nuestra ya se las coma por dentro. Que no se abra el
                // navegador no puede tirar la sala: el jugador tiene la sala
                // abierta y una partida esperando.
                get.setOnAction(e -> {
                    try {
                        GuiBase.getInterface().browseToUrl("https://www.radmin-vpn.com/");
                    } catch (final Exception ex) {
                        System.out.println("[lobby] no se ha podido abrir la descarga: " + ex);
                    }
                });
                final HBox line = new HBox(10, get);
                line.setAlignment(Pos.CENTER_LEFT);
                internet.getChildren().add(line);
            }
            addChatLine(NeoText.get("lobby.address.cgnat"));
            return;
        }
        if (reach.publicIp() == null) {
            // Sin linea. El hueco se queda vacio, como ha hecho siempre.
            return;
        }
        final String ext = String.format(java.util.Locale.ROOT, "%s:%d",
                reach.publicIp(), NeoLobby.port());
        final Label hint = new Label(NeoText.get("lobby.address.internetHint",
                String.valueOf(NeoLobby.port())));
        hint.getStyleClass().add("mode-tile-note");
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        internet.getChildren().setAll(
                addressLine(NeoText.get("lobby.address.internet", ext), ext), hint);

        // Y si el router no ha contestado, decirlo. Callarse era lo correcto
        // para NO acusar en falso de CGNAT, pero deja al jugador con una
        // direccion que puede valer o no y sin forma de saberlo — y si ademas
        // el UPnP ha fallado (mismo motivo: no aparece ningun router), se queda
        // mirando un "ha fallado" sin nada que hacer con el. Se dice lo que se
        // sabe, que es nada, y se da la comprobacion a mano: comparar la IP que
        // dice el router con la de aqui. Es lo MISMO que hace NetReach cuando
        // puede, y cualquiera puede hacerlo sin UPnP.
        if (reach.verdict() == NetReach.Verdict.UNKNOWN && reach.routerWan() == null) {
            final Label dunno = new Label(NeoText.get("lobby.address.noRouter"));
            dunno.getStyleClass().add("lobby-warn-why");
            dunno.setWrapText(true);
            dunno.setMaxWidth(560);
            dunno.setMinHeight(Region.USE_PREF_SIZE);
            internet.getChildren().add(dunno);
        }
    }

    private Region addressLine(final String text, final String address) {
        return addressLine(text, address, false);
    }

    /**
     * Un renglon de direccion con su boton de copiar.
     *
     * <p>{@code best} es la que hay que pasar. Se marca con <b>color y
     * negrita</b>, no con un icono ni un orden: el orden ya se usa (la primera
     * es la de casa) y un icono hay que aprenderselo.
     */
    private Region addressLine(final String text, final String address, final boolean best) {
        final Label label = new Label(text);
        label.getStyleClass().add(best ? "lobby-best" : "mode-tile-note");

        final Button copy = new Button(NeoText.get("lobby.copy"));
        copy.getStyleClass().add("btn-secondary");
        copy.setMinWidth(Region.USE_PREF_SIZE);
        copy.setOnAction(e -> {
            GuiBase.getInterface().copyToClipboard(address);
            addChatLine(NeoText.get("lobby.copied"));
        });

        final HBox row = new HBox(10, label, copy);
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

        // La regla NO es mayRemove a secas: ver NeoLobby.mayRemoveSeat.
        if (host && NeoLobby.mayRemoveSeat(l, index)) {
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
                        sendDeck(index, chosen);
                        // Una IA no tiene nada mas que decidir: con mazo, lista.
                        final LobbySlot s = lobby == null ? null : lobby.getSlot(index);
                        if (s != null && s.getType() == LobbySlotType.AI && !s.isReady()) {
                            send(index, UpdateLobbyPlayerEvent.isReadyUpdate(true));
                        }
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

    /** Cambiar el mazo de un asiento, de forma que se entere todo el mundo. Ver NeoLobby.deckEvents. */
    private void sendDeck(final int index, final Deck deck) {
        for (final UpdateLobbyPlayerEvent e : NeoLobby.deckEvents(deck)) {
            send(index, e);
        }
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
            // Y se sienta ya con un preconstruido y lista: sentar una IA y
            // tener que elegirle mazo Y marcarla lista era lo que mas pasos
            // costaba de la sala. El mazo se le cambia igual con su boton.
            final Deck deck = NeoLobby.randomAiDeck();
            if (deck != null) {
                sendDeck(index, deck);
                send(index, UpdateLobbyPlayerEvent.isReadyUpdate(true));
            }
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
        final GameLobby l = lobby;
        if (l != null && host) {
            // En el anfitrion esto corre ANTES de que el servidor reparta el
            // estado (NetConnectUtil avisa primero a la pantalla), asi que lo
            // que se limpie aqui ya viaja limpio.
            NeoLobby.clearOpenSeats(l);
        }
        if (l != null && !host && l.getNumberOfSlots() > 0) {
            // Ya estamos sentados: se dice que version tenemos. Ver NetBuild.
            final NeoOnline o = online;
            if (o != null) {
                o.announceBuild();
            }
        }
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
            // Al registro cada vez que cambia algo: es lo unico que queda
            // cuando un amigo dice "no me deja empezar" y no estas delante.
            // (NeoLobby.describe existia para esto, y no lo llamaba nadie.)
            final String now = NeoLobby.describe(l);
            if (!now.equals(lastDescribed)) {
                lastDescribed = now;
                System.out.println("[lobby] " + now);
            }
            rebuildSeats();
            status.setText(statusText(l));
            if (host) {
                // Con la partida anterior todavia en marcha, NO: se vuelve a la
                // sala antes de que acabe cuando te rindes en una partida a
                // tres (los otros dos siguen), y empezar otra encima seria
                // montar dos partidas sobre el mismo lobby.
                startButton.setDisable(failed || l.isMatchActive()
                        || l.findFirstUnreadySlot() != null
                        || NeoLobby.activeSlots(l).size() < NeoLobby.MIN_SEATS);
            }
            if (l.isMatchActive() && started.compareAndSet(false, true)) {
                actions.gameStarting();
            }
        });
    }

    /** Lo ultimo que se apunto en el registro, para no repetirlo. */
    private String lastDescribed = "";

    private String statusText(final GameLobby l) {
        if (failed) {
            return failedText;
        }
        if (l.isMatchActive()) {
            return NeoText.get("lobby.matchRunning");
        }
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
        // Los avisos del servidor llegan en ingles: se leen aqui, una vez,
        // para la sala y para la mesa. Ver NetPhrases.
        final forge.neo.net.NetPhrases.Phrase phrase =
                forge.neo.net.NetPhrases.read(message.getSource(), message.getMessage());
        addChatLine(phrase.text());
        final NetListener nl = netListener;
        if (nl != null) {
            nl.chat(phrase);
        }
    }

    private void addChatLine(final String text) {
        synchronized (history) {
            history.add(text);
            while (history.size() > 200) {
                history.remove(0);
            }
        }
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
        failed(NeoText.get("lobby.lost"));
        final NetListener nl = netListener;
        if (nl != null) {
            nl.lost(message);
        }
    }

    /** No se pudo hospedar o conectar, o se cayo: la sala ya no sirve. */
    private volatile boolean failed;
    private volatile String failedText = "";

    /**
     * La sala deja de ser una sala: se dice por que en la cabecera y se apaga
     * EMPEZAR.
     *
     * <p>Antes, si el puerto estaba cogido o el invitado escribia mal la
     * direccion, el motivo salia en el chat y la cabecera se quedaba en
     * "Conectando..." para siempre, con los asientos pintados como si nada.
     */
    public void failed(final String why) {
        failed = true;
        failedText = why == null || why.isBlank() ? NeoText.get("lobby.lost") : why;
        Platform.runLater(() -> {
            status.setText(failedText);
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
                                sendDeck(i, mine.get(0));
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
        // EMPEZAR lo decide refresh(), que mira si la partida sigue viva: se
        // puede volver aqui antes de que acabe (rendirse en una a tres).
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
