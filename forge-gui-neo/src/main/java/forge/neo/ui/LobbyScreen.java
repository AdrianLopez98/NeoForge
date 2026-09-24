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
public class LobbyScreen extends BorderPane
        implements NeoOnline.View, forge.gui.interfaces.IDraftEventHandler {

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

        /**
         * Ha llegado el primer sobre del draft: hay que abrir la pantalla de
         * picks. Llega en un hilo cualquiera.
         *
         * @param seat  mi asiento en el pod, que no se sabe hasta este momento
         * @param event el evento, para el nombre del producto y los nombres del
         *              pod; puede ser null en el invitado antes del primer
         *              reparto
         */
        default void draftStarting(int seat, forge.gamemodes.net.NetworkEventView event) {
        }

        /**
         * Se acabo el draft (o el sellado): aqui esta tu pool. Hilo cualquiera.
         *
         * <p>Lo que toca es abrir el constructor para que montes el mazo con
         * el. El pool ya esta guardado cuando se llama.
         */
        default void poolArrived(forge.deck.Deck pool, boolean sealed) {
        }
    }

    private final Actions actions;
    private final boolean host;
    private final double tileWidth;

    private volatile GameLobby lobby;
    private volatile IPlayerChangeListener playerChange;

    /**
     * A que se juega. Lo manda el anfitrion y llega al invitado por el cable.
     *
     * <p>Es {@code volatile} porque lo escribe {@code refresh()} (hilo de
     * JavaFX) y lo leen los botones de mazo, que corren ahi mismo — pero
     * tambien {@code autoDriveForTest}, y sobre todo sirve de aviso de que este
     * valor NO es una decision de esta pantalla: es un espejo de lo que dicen
     * las variantes del lobby.
     */
    private volatile NeoFormat format = NeoFormat.COMMANDER;

    /** Los botones del modo, en el anfitrion. Vacio en el invitado. */
    private final java.util.Map<NeoFormat, Button> formatButtons =
            new java.util.EnumMap<>(NeoFormat.class);

    /** Lo que se lee del modo: el nombre (invitado) y la linea que lo explica. */
    private final Label formatName = new Label();
    private final Label formatNote = new Label();

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

        final VBox box = new VBox(10, words, formatRow(), eventRow());
        if (host) {
            box.getChildren().add(addressRow());
        }
        box.setPadding(new Insets(20, 30, 12, 30));
        return box;
    }

    /**
     * A que se juega.
     *
     * <p><b>Se ve en los dos lados y es lo primero que hay que ver</b>, porque
     * decide el mazo: el invitado elegia siempre entre sus mazos de Commander,
     * asi que la sala solo sabia jugar a una cosa. Aqui manda el anfitrion —
     * es su partida — y el invitado lo lee.
     *
     * <p>Los botones se crean <b>una vez</b> y luego solo se les cambia la
     * marca: reconstruir la fila en cada aviso del lobby (que llegan a
     * puñados) le quitaria el boton de debajo del raton a quien esta a punto de
     * pulsarlo.
     */
    private Region formatRow() {
        final Label caption = new Label(NeoText.get("lobby.format"));
        caption.getStyleClass().add("caption");

        formatName.getStyleClass().add("mode-tile-name");
        formatNote.getStyleClass().add("mode-tile-note");
        formatNote.setWrapText(true);
        formatNote.setMaxWidth(UiScale.px(560));
        formatNote.setMinHeight(Region.USE_PREF_SIZE);

        if (!host) {
            // El invitado no elige: lo lee. Un botón apagado diría lo mismo
            // peor — parecería que se puede pulsar y no se puede.
            return new VBox(2, caption, formatName, formatNote);
        }

        final HBox picks = new HBox(6);
        picks.setAlignment(Pos.CENTER_LEFT);
        for (final NeoFormat f : NeoLobby.FORMATS) {
            final Button b = new Button(f.getLabel());
            b.getStyleClass().add("segment");
            // Sin esto JavaFX los encoge por debajo de su texto en cuanto la
            // fila va justa, y salen botones que ponen "..." y nada mas.
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> changeFormat(f));
            formatButtons.put(f, b);
            picks.getChildren().add(b);
        }
        // formatName sale tambien en el anfitrion, pero SOLO en limitado: ahi
        // los botones se esconden y sin el la fila se quedaria con un titulo y
        // una explicacion de algo que no se nombra en ninguna parte.
        formatName.setVisible(false);
        formatName.setManaged(false);
        return new VBox(6, caption, formatName, picks, formatNote);
    }

    /**
     * El anfitrion cambia el modo.
     *
     * <p>Lo que hace de verdad vive en {@link NeoLobby#setFormat} — incluido
     * quitarle el mazo a todo el mundo, que es la parte que no se ve venir — y
     * aqui solo queda volver a sentar a las IAs, que son las unicas que no
     * pueden elegirse mazo solas.
     *
     * <p>No se pregunta antes aunque se pierdan las elecciones de los demas: es
     * la sala del anfitrion, el cambio se deshace pulsando el modo de antes y
     * lo unico que se pierde son dos clicks. Preguntar en un control que se usa
     * al principio y una vez seria estorbar en el caso normal para cubrir el
     * raro.
     */
    private void changeFormat(final NeoFormat wanted) {
        final GameLobby l = lobby;
        if (l == null || !host || wanted == format || l.isMatchActive()) {
            return;
        }
        NeoLobby.setFormat(l, wanted);
        // Las IAs vuelven a quedarse listas solas, con un mazo del modo nuevo:
        // son asientos que no tienen a nadie detras y dejarlos a medias
        // obligaria a repasarlos uno a uno cada vez que se prueba otro modo.
        for (int i = 0; i < l.getNumberOfSlots(); i++) {
            final LobbySlot s = l.getSlot(i);
            if (s == null || s.getType() != LobbySlotType.AI) {
                continue;
            }
            final Deck deck = NeoLobby.randomAiDeck(wanted);
            if (deck != null) {
                sendDeck(i, deck);
            }
            if (deck != null || !NeoLobby.needsDeck(wanted)) {
                send(i, UpdateLobbyPlayerEvent.isReadyUpdate(true));
            }
        }
        refresh();
    }

    /**
     * Con que modo se abre la sala.
     *
     * <p>Commander, que es a lo que se juega aqui. {@code -Dneo.lobby.format=X}
     * lo cambia y existe <b>solo para las capturas</b>: la fila de Momir no
     * tiene boton de mazo y no hay forma de llegar a ella desde una sesion sin
     * manos. Un nombre que no sea de {@link NeoLobby#FORMATS} se ignora.
     */
    private static NeoFormat startFormat() {
        final String wanted = System.getProperty("neo.lobby.format");
        if (wanted != null && !wanted.isBlank()) {
            for (final NeoFormat f : NeoLobby.FORMATS) {
                if (f.name().equalsIgnoreCase(wanted.trim())) {
                    return f;
                }
            }
            System.out.println("[lobby] modo desconocido: " + wanted);
        }
        return NeoFormat.COMMANDER;
    }

    // ------------------------------------------------------------------
    // El draft y el sellado en red
    // ------------------------------------------------------------------

    /**
     * La fila del evento: montar un draft o un sellado, y ver como va.
     *
     * <p><b>Un evento no es un modo, y por eso tiene fila propia.</b> Commander
     * o Estandar es una variante que se aplica a la partida; un draft es una
     * <b>fase previa</b> entera — sobres, picks por el cable, un pool, cada uno
     * monta su mazo — y solo despues se juega. Meterlo en la fila de modos
     * seria prometer con un boton pequenyo algo que dura media hora.
     *
     * <p>Mientras hay evento, la fila de modos se apaga: en limitado no se
     * elige formato, lo pone el motor ({@code GameType.Draft} y
     * {@code DeckFormat.Limited}, que salen de {@code data.isLimitedMode()}).
     */
    private Region eventRow() {
        final Label caption = new Label(NeoText.get("net.event"));
        caption.getStyleClass().add("caption");

        eventName.getStyleClass().add("mode-tile-name");
        eventNote.getStyleClass().add("mode-tile-note");
        eventNote.setWrapText(true);
        eventNote.setMaxWidth(UiScale.px(560));
        eventNote.setMinHeight(Region.USE_PREF_SIZE);

        eventButtons.setAlignment(Pos.CENTER_LEFT);
        if (host) {
            setUpEvent.getStyleClass().add("btn-secondary");
            setUpEvent.setMinWidth(Region.USE_PREF_SIZE);
            setUpEvent.setOnAction(e -> openEventWizard());

            startEvent.getStyleClass().add("btn-primary");
            startEvent.setMinWidth(Region.USE_PREF_SIZE);
            startEvent.setOnAction(e -> launchEvent());

            dropEvent.getStyleClass().add("btn-secondary");
            dropEvent.setMinWidth(Region.USE_PREF_SIZE);
            dropEvent.setOnAction(e -> discardEvent());

            eventButtons.getChildren().addAll(setUpEvent, startEvent, dropEvent);
        }
        // "Montar mi mazo" vale para los DOS: el pool le llega a cada uno.
        buildPool.getStyleClass().add("btn-secondary");
        buildPool.setMinWidth(Region.USE_PREF_SIZE);
        buildPool.setOnAction(e -> openMyPool());
        eventButtons.getChildren().add(buildPool);

        // Esto es lo ultimo que se ha anyadido y lo unico de la aplicacion que
        // NO se ha jugado con dos ordenadores de verdad: el comprobador lo
        // recorre entero, pero en un solo proceso y con IA. Decirlo antes de
        // que alguien monte un draft de media hora con sus amigos es mas
        // barato que el rato que se pierde si se corta a mitad.
        eventWarn.getStyleClass().add("lobby-warn");
        eventWarn.setWrapText(true);
        eventWarn.setMaxWidth(UiScale.px(560));
        eventWarn.setMinHeight(Region.USE_PREF_SIZE);

        return new VBox(6, caption, eventName, eventNote, eventWarn, eventButtons);
    }

    private final Label eventWarn = new Label(NeoText.get("net.event.beta"));

    private final Label eventName = new Label();
    private final Label eventNote = new Label();
    private final HBox eventButtons = new HBox(8);
    private final Button setUpEvent = new Button(NeoText.get("net.event.setUp"));
    private final Button startEvent = new Button(NeoText.get("net.event.start"));
    private final Button dropEvent = new Button(NeoText.get("net.event.drop"));
    private final Button buildPool = new Button(NeoText.get("net.event.build"));

    /** El pool que me ha tocado, cuando llegue. */
    private volatile forge.deck.Deck myPool;

    /** Si el evento era de sellado, para abrir el constructor con el rotulo bueno. */
    private volatile boolean poolSealed;

    /** Mi fuente del draft mientras dura. La crea el primer sobre. */
    private volatile forge.neo.net.NetDraftSource draftSource;

    /** Pone al dia la fila del evento. Hilo de JavaFX. */
    private void showEvent(final GameLobby l) {
        final forge.gamemodes.net.NetworkEventView view = forge.neo.net.NeoNetEvent.viewOf(l);
        final boolean limited = forge.neo.net.NeoNetEvent.isLimited(l);
        final boolean havePool = myPool != null;

        eventName.setText(view == null
                ? NeoText.get(limited ? "net.event.done" : "net.event.none")
                : NeoText.get(view.getFormat() == forge.gamemodes.net.EventFormat.SEALED
                        ? "net.event.sealed" : "net.event.draft"));
        eventNote.setText(eventNote(l, view, havePool));

        buildPool.setVisible(havePool);
        buildPool.setManaged(havePool);
        if (!host) {
            return;
        }
        final boolean running = view != null
                && view.getPhase() != forge.gamemodes.net.EventPhase.LOBBY_GATHER;
        // Montar otro con uno a medias seria tirar el que esta corriendo sin
        // decirlo; y con la partida en marcha no hay nada que montar.
        setUpEvent.setDisable(failed || running || l.isMatchActive());
        startEvent.setVisible(view != null && !running);
        startEvent.setManaged(view != null && !running);
        startEvent.setText(NeoText.get(
                view != null && view.getFormat() == forge.gamemodes.net.EventFormat.SEALED
                        ? "net.event.startSealed" : "net.event.startDraft"));
        startEvent.setDisable(failed || l.findFirstUnreadySlot() != null);
        dropEvent.setVisible(view != null || limited);
        dropEvent.setManaged(view != null || limited);
    }

    /** La linea que explica en que punto esta el evento. */
    private String eventNote(final GameLobby l,
                             final forge.gamemodes.net.NetworkEventView view,
                             final boolean havePool) {
        if (havePool) {
            return NeoText.get("net.event.note.pool", myPool.getName());
        }
        if (view == null) {
            return NeoText.get(forge.neo.net.NeoNetEvent.isLimited(l)
                    ? "net.event.note.limited"
                    : host ? "net.event.note.hostIdle" : "net.event.note.guestIdle");
        }
        if (view.getPhase() != forge.gamemodes.net.EventPhase.LOBBY_GATHER) {
            return NeoText.get("net.event.note.running", view.getProductDescription());
        }
        final String product = view.getProductDescription() == null
                ? "" : view.getProductDescription();
        if (view.getFormat() == forge.gamemodes.net.EventFormat.SEALED) {
            return NeoText.get("net.event.note.readySealed", product);
        }
        return NeoText.get("net.event.note.readyDraft", product,
                view.getPodSize(), view.getPickTimerSeconds());
    }

    /**
     * El asistente para montar el evento. <b>Hilo de fondo</b>, y no por gusto.
     *
     * <p>Por dentro pregunta seis cosas seguidas con {@code SGuiChoose} y
     * {@code SOptionPane} — tipo de evento, formato del pozo, bloque o
     * expansion (eso lo pregunta el propio {@code BoosterDraft}), tamanyo del
     * pod, regla de picks y los dos relojes — y <b>cada una bloquea hasta que
     * contestas</b>. En el hilo de JavaFX eso es un cuelgue instantaneo.
     *
     * <p>Lo bueno es que esos dialogos ya salen en NUESTRA interfaz sin tocar
     * nada: {@code SGuiChoose} entra por {@code NeoGuiBase.getChoices}, que es
     * el mismo camino por el que el draft de cubo pregunta que cubo quieres.
     * Por eso montar un evento es una secuencia de llamadas y no una pantalla
     * nueva.
     */
    private void openEventWizard() {
        final GameLobby l = lobby;
        if (!(l instanceof forge.gamemodes.net.server.ServerGameLobby server)) {
            return;
        }
        setUpEvent.setDisable(true);
        final Thread t = new Thread(() -> {
            try {
                runEventWizard(server, l);
            } catch (final Exception | LinkageError e) {
                System.out.println("[lobby] no se ha podido montar el evento: " + e);
                e.printStackTrace();
                setNotice(NeoText.get("net.event.failed", String.valueOf(e)));
            } finally {
                Platform.runLater(() -> refresh());
            }
        }, "neo-lobby-event");
        t.setDaemon(true);
        t.start();
    }

    private void runEventWizard(final forge.gamemodes.net.server.ServerGameLobby server,
                                final GameLobby l) {
        // Un pool ya jugado se puede volver a usar sin draftear otra vez: es lo
        // que hace falta cuando la sesion se corto a mitad, o cuando quedais
        // otro dia con los mismos mazos.
        final List<forge.gamemodes.net.NetworkEvent.EventChoice> past =
                forge.neo.net.NeoNetEvent.pastEvents();
        if (!past.isEmpty()) {
            final String create = NeoText.get("net.event.wizard.new");
            final String load = NeoText.get("net.event.wizard.past");
            final String what = forge.gui.util.SGuiChoose.oneOrNone(
                    NeoText.get("net.event.wizard.whatNow"), List.of(create, load));
            if (what == null) {
                return;
            }
            if (load.equals(what)) {
                final forge.gamemodes.net.NetworkEvent.EventChoice chosen =
                        forge.gui.util.SGuiChoose.oneOrNone(
                                NeoText.get("net.event.wizard.pickPast"), past);
                if (chosen != null) {
                    toLimited(l, server);
                    server.selectEventForMatch(chosen.id(), true);
                }
                return;
            }
        }

        final String draftLabel = NeoText.get("net.event.draft");
        final String sealedLabel = NeoText.get("net.event.sealed");
        final String kind = forge.gui.util.SGuiChoose.oneOrNone(
                NeoText.get("net.event.wizard.kind"), List.of(draftLabel, sealedLabel));
        if (kind == null) {
            return;
        }
        final boolean isDraft = draftLabel.equals(kind);
        if (isDraft && !forge.neo.net.NeoNetEvent.podFits(l)) {
            setNotice(NeoText.get("net.event.tooMany",
                    forge.gamemodes.limited.BoosterDraft.N_PLAYERS));
            return;
        }

        final forge.gamemodes.limited.LimitedPoolType poolType =
                forge.gui.util.SGuiChoose.oneOrNone(NeoText.get("net.event.wizard.pool"),
                        List.of(forge.gamemodes.limited.LimitedPoolType.values(isDraft)));
        if (poolType == null) {
            return;
        }

        // Montar el draft dispara los dialogos de bloque/expansion/cubo del
        // propio motor. Null = el jugador ha cancelado uno de ellos.
        forge.gamemodes.limited.BoosterDraft draft = null;
        if (isDraft) {
            draft = forge.gamemodes.limited.BoosterDraft.createDraftForNetwork(poolType);
            if (draft == null) {
                return;
            }
            final List<Integer> pods = forge.neo.net.NeoNetEvent.podSizes(l);
            final int recommended = draft.getPodSize();
            final Integer pod = forge.gui.util.SGuiChoose.oneOrNone(
                    NeoText.get("net.event.wizard.pod"), pods,
                    pods.contains(recommended) ? recommended : pods.get(0),
                    n -> forge.gamemodes.net.NetworkEvent.markSetDefault(
                            forge.gamemodes.net.NetworkEvent.podSizeLabel(n), n == recommended));
            if (pod == null) {
                return;
            }
            draft.setPodSize(pod);

            final forge.card.DraftOptions.DoublePick byDefault =
                    forge.gamemodes.net.NetworkEvent.defaultPicksFor(draft, pod);
            final forge.card.DraftOptions.DoublePick picks = forge.gui.util.SGuiChoose.oneOrNone(
                    NeoText.get("net.event.wizard.picks"),
                    forge.neo.net.NeoNetEvent.pickRules(), byDefault,
                    rule -> forge.gamemodes.net.NetworkEvent.markSetDefault(
                            forge.gamemodes.net.NetworkEvent.picksLabel(rule), rule == byDefault));
            if (picks == null) {
                return;
            }
            draft.setDoublePick(picks);
        }

        final int pick = isDraft
                ? askSeconds("net.event.wizard.timer", forge.neo.net.NeoNetEvent.DEFAULT_PICK_SECONDS)
                : 0;
        final int grace = isDraft
                ? askSeconds("net.event.wizard.grace", forge.neo.net.NeoNetEvent.DEFAULT_GRACE_SECONDS)
                : 0;

        final boolean ok = forge.neo.net.NeoNetEvent.configure(server,
                isDraft ? forge.gamemodes.net.EventFormat.BOOSTER_DRAFT
                        : forge.gamemodes.net.EventFormat.SEALED,
                poolType, draft, pick, grace);
        if (ok) {
            toLimited(l, server);
        }
    }

    /**
     * La sala pasa a limitado.
     *
     * <p><b>Hay que limpiar las variantes, y no es cosmetico.</b> Si la sala
     * venia de Momir Basic, el conjunto sigue teniendo {@code MomirBasic} — y
     * {@code GameLobby.startGame()} mira eso ANTES que nada: encuentra una
     * variante autogenerada, monta sesenta tierras basicas para cada uno y
     * <b>tira el pool que acabais de draftear</b>. Media hora de picks a la
     * basura sin un solo aviso.
     *
     * <p>Se hace con {@link NeoLobby#setFormat}, que ademas quita los mazos y
     * los "listo": el de Commander que tuviera cada uno ya no vale aqui.
     */
    private void toLimited(final GameLobby l,
                           final forge.gamemodes.net.server.ServerGameLobby server) {
        NeoLobby.setFormat(l, NeoFormat.ESTANDAR);
        server.setLimitedMode(true);
    }

    /** Un plazo en segundos, con el de fabrica escrito. Cancelar deja el de fabrica. */
    private static int askSeconds(final String key, final int fallback) {
        final String answer = forge.gui.util.SOptionPane.showInputDialog(
                NeoText.get(key), NeoText.get("net.event.wizard.timers"),
                null, String.valueOf(fallback), null, true);
        if (answer == null || answer.isBlank()) {
            return fallback;
        }
        try {
            final int n = Integer.parseInt(answer.trim());
            return n >= 0 ? n : fallback;
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * Repartir sobres (o pools). <b>Hilo de fondo</b>: abre los sobres de todo
     * el pod, que con ocho asientos son 24 sobres.
     */
    private void launchEvent() {
        final GameLobby l = lobby;
        if (!(l instanceof forge.gamemodes.net.server.ServerGameLobby server)) {
            return;
        }
        startEvent.setDisable(true);
        final Thread t = new Thread(() -> {
            final String why = forge.neo.net.NeoNetEvent.start(server);
            if (why != null) {
                setNotice(NeoText.get("net.event.cannot." + why));
            }
            Platform.runLater(this::refresh);
        }, "neo-lobby-event-start");
        t.setDaemon(true);
        t.start();
    }

    /** El anfitrion se echa atras: fuera el evento y la sala vuelve a ser normal. */
    private void discardEvent() {
        final GameLobby l = lobby;
        if (!(l instanceof forge.gamemodes.net.server.ServerGameLobby server)) {
            return;
        }
        server.clearCurrentEvent();
        server.selectEventForMatch(null, true);
        server.setLimitedMode(false);
        myPool = null;
        // Al salir de limitado hay que volver a poner un modo de verdad, y con
        // el, quitarle a todos el mazo del pool: ya no vale para nada.
        NeoLobby.setFormat(l, NeoFormat.COMMANDER);
        refresh();
    }

    /** Abre el constructor con mi pool. Lo hace la app, que es quien tiene pantallas. */
    private void openMyPool() {
        final forge.deck.Deck pool = myPool;
        if (pool != null) {
            actions.poolArrived(pool, poolSealed);
        }
    }

    // ------------------------------------------------------------------
    // IDraftEventHandler — TODO esto llega en el hilo de netty
    // ------------------------------------------------------------------

    /**
     * Forge pregunta esto <b>una vez</b>, al conectar
     * ({@code NetConnectUtil.host/join}), y se queda con lo que devolvamos. Si
     * devuelves null — que es lo que hacia el {@code default} del interfaz —
     * los paquetes del draft se reciben y se tiran en silencio: el sobre no
     * llega nunca y la pantalla no sale.
     */
    @Override
    public forge.gui.interfaces.IDraftEventHandler getDraftHandler() {
        return this;
    }

    @Override
    public void draftPackArrived(final int seatIndex, final List<forge.item.PaperCard> pack,
                                 final int packNumber, final int pickNumber,
                                 final int timerDurationSeconds) {
        forge.neo.net.NetDraftSource source = draftSource;
        if (source == null) {
            final GameLobby l = lobby;
            final forge.gamemodes.net.NetworkEventView view =
                    forge.neo.net.NeoNetEvent.viewOf(l);
            source = new forge.neo.net.NetDraftSource(seatIndex,
                    view == null ? List.of() : view.getParticipants(),
                    view == null ? null : view.getProductDescription(),
                    pick -> {
                        final NeoOnline o = online;
                        if (o != null) {
                            o.sendDraftPick(lobby, pick);
                        }
                    });
            draftSource = source;
            source.packArrived(pack, packNumber, pickNumber, timerDurationSeconds);
            actions.draftStarting(seatIndex, view);
            return;
        }
        source.packArrived(pack, packNumber, pickNumber, timerDurationSeconds);
        repaintDraft();
    }

    @Override
    public void draftSeatPicked(final int seatIndex, final int[] seatQueueDepths) {
        final forge.neo.net.NetDraftSource source = draftSource;
        if (source != null) {
            source.seatPicked(seatQueueDepths);
            repaintDraft();
        }
    }

    @Override
    public void draftAutoPicked(final int seatIndex, final forge.item.PaperCard card,
                                final int packNumber, final int pickInPack) {
        final forge.neo.net.NetDraftSource source = draftSource;
        if (source != null) {
            source.autoPicked(seatIndex, card);
            repaintDraft();
        }
    }

    /**
     * Se acabo: aqui esta tu pool.
     *
     * <p>Llega igual en las dos puntas y en los dos formatos — al terminar el
     * draft y al repartir los pools de un sellado — y por eso es el unico sitio
     * donde hay que cerrar la pantalla de picks: hacerlo contando picks seria
     * adivinar.
     */
    @Override
    public void receiveEventPool(final String eventId, final forge.deck.Deck pool) {
        final GameLobby l = lobby;
        final forge.gamemodes.net.NetworkEventView view = forge.neo.net.NeoNetEvent.viewOf(l);
        poolSealed = view != null
                && view.getFormat() == forge.gamemodes.net.EventFormat.SEALED;
        forge.neo.net.NeoNetEvent.savePool(pool,
                l instanceof forge.gamemodes.net.server.ServerGameLobby server
                        ? server.getCurrentEvent() : null);
        myPool = pool;

        final forge.neo.net.NetDraftSource source = draftSource;
        if (source != null) {
            source.finish();
            repaintDraft();
        }
        draftSource = null;

        if (l instanceof forge.gamemodes.net.server.ServerGameLobby server) {
            // El anfitrion decide con que pool se juega, y lo reparte: sin esto
            // el invitado no sabe que mazos le valen.
            server.selectEventForMatch(eventId, true);
        }
        actions.poolArrived(pool, poolSealed);
        refresh();
    }

    /** Quien esta pintando el draft, para avisarle. Lo pone la app. */
    private volatile Runnable draftRepaint;

    public void setDraftRepaint(final Runnable repaint) {
        this.draftRepaint = repaint;
    }

    /** La fuente del draft, para que la pantalla de picks tire de ella. */
    public forge.neo.net.NetDraftSource getDraftSource() {
        return draftSource;
    }

    private void repaintDraft() {
        final Runnable r = draftRepaint;
        if (r != null) {
            Platform.runLater(r);
        }
    }

    /** Pone al dia la fila del modo. Hilo de JavaFX. */
    private void showFormat(final GameLobby l) {
        // En limitado no hay modo que elegir: lo pone el motor a partir del
        // evento (GameType.Draft y DeckFormat.Limited, que salen de
        // isLimitedMode en GameLobby.startGame). Dejar los botones encendidos
        // seria ofrecer una decision que no se aplica — principio 1.
        final boolean limited = forge.neo.net.NeoNetEvent.isLimited(l);
        formatName.setText(limited
                ? NeoText.get("net.event.limited") : format.getLabel());
        if (host) {
            formatName.setVisible(limited);
            formatName.setManaged(limited);
        }
        formatNote.setText(limited
                ? NeoText.get("net.event.limited.note")
                : NeoLobby.needsDeck(format)
                        ? format.getDescription()
                        : NeoText.get("lobby.format.noDeck", format.getDescription()));
        for (final java.util.Map.Entry<NeoFormat, Button> e : formatButtons.entrySet()) {
            e.getValue().pseudoClassStateChanged(SELECTED, !limited && e.getKey() == format);
            e.getValue().setVisible(!limited);
            e.getValue().setManaged(!limited);
            // Con la partida en marcha el modo ya esta decidido: cambiarlo no
            // tocaria esa partida y solo serviria para que al volver a la sala
            // nadie supiera por que se ha quedado sin mazo.
            e.getValue().setDisable(failed || l.isMatchActive());
        }
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

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
            note.setMaxWidth(UiScale.px(560));
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
            note.setMaxWidth(UiScale.px(560));
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
            warn.setMaxWidth(UiScale.px(560));
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
            fix.setMaxWidth(UiScale.px(560));
            fix.setMinHeight(Region.USE_PREF_SIZE);

            final Label why = new Label(NeoText.get("lobby.address.cgnat.what"));
            why.getStyleClass().add("lobby-warn-why");
            why.setWrapText(true);
            why.setMaxWidth(UiScale.px(560));
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
        hint.setMaxWidth(UiScale.px(560));
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
            dunno.setMaxWidth(UiScale.px(560));
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
        box.setPrefWidth(UiScale.px(320));
        box.setMinWidth(UiScale.px(260));
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

        final HBox row = new HBox(10, back, NetHelp.button(), gap);
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
        who.setMinWidth(UiScale.px(140));

        final Label kind = new Label(kindLabel(l, index, slot));
        kind.getStyleClass().add("mode-tile-note");
        kind.setMinWidth(UiScale.px(90));

        final HBox row = new HBox(12, who, kind);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("mode-tile");
        row.setPadding(new Insets(10, 14, 10, 14));

        if (!open) {
            row.getChildren().add(deckCell(l, index, slot));

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

    /**
     * Con que juega este asiento.
     *
     * <p>En Momir Basic y MoJhoSto <b>no es un boton</b>: ahi el mazo lo monta
     * el motor y no hay nada que elegir. Un boton apagado en su sitio seria un
     * control que no hace nada y, peor, dejaria pensando que falta algo por
     * hacer.
     */
    private Region deckCell(final GameLobby l, final int index, final LobbySlot slot) {
        if (!NeoLobby.needsDeck(format)) {
            final Label auto = new Label(NeoText.get("lobby.autoDeck"));
            auto.getStyleClass().add("mode-tile-note");
            auto.setMinWidth(Region.USE_PREF_SIZE);
            return auto;
        }
        final Deck deck = slot.getDeck();
        // En limitado el mazo sale del pool, y hasta que el evento no reparte
        // no hay ninguno: abrir ahi el selector ensenyaria una rejilla vacia
        // sin decir por que. El boton dice que se esta esperando.
        final boolean limited = forge.neo.net.NeoNetEvent.isLimited(l);
        final forge.gamemodes.net.NetworkEventView pending = forge.neo.net.NeoNetEvent.viewOf(l);
        // Dos formas de estar esperando, y hacen falta las dos. La obvia: no
        // hay ningun pool guardado. La que no se ve venir: SI los hay, pero de
        // drafts de otros dias — con el evento montado y sin repartir, el
        // selector te ofrecia el pool de la semana pasada para jugar el draft
        // de hoy. Mientras haya un evento sin repartir, no hay mazo que elegir.
        //
        // ⚠️ "Sin repartir" es la FASE, no la existencia del evento: la vista
        // sigue publicada despues de repartir (la quita clearCurrentEvent, que
        // solo llama el anfitrion al descartarlo). Mirando solo si hay evento,
        // el boton se quedaba apagado para siempre justo cuando ya tenias pool.
        final boolean notDealtYet = pending != null
                && pending.getPhase() == forge.gamemodes.net.EventPhase.LOBBY_GATHER;
        final boolean noPoolYet = limited
                && (notDealtYet || forge.neo.net.NeoNetEvent.poolsFor(
                        forge.neo.net.NeoNetEvent.activeEventId(l),
                        forge.neo.net.NeoNetEvent.conformance(l)).isEmpty());
        final Button b = new Button(deck != null ? shorten(deck.getName())
                : NeoText.get(noPoolYet ? "lobby.waitPool" : "lobby.pickDeck"));
        b.getStyleClass().add("btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE);
        // mayEdit lo contesta el motor: en el invitado solo es true para SU
        // asiento, y en el anfitrion para todos menos los remotos.
        b.setDisable(!l.mayEdit(index) || noPoolYet);
        b.setOnAction(e -> pickDeck(index));
        return b;
    }

    private Button readyButton(final GameLobby l, final int index, final LobbySlot slot) {
        final boolean ready = slot.isReady();
        final Button b = new Button(NeoText.get(ready ? "lobby.ready" : "lobby.notReady"));
        b.getStyleClass().add(ready ? "btn-primary" : "btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE);
        // Sin mazo no se puede estar listo: el motor lo rechazaria al empezar y
        // el aviso saldria mucho despues, cuando ya nadie sabe de que va. Pero
        // solo donde hace falta mazo: en Momir esperar uno que no va a llegar
        // deja la sala muerta con el boton apagado y sin explicacion.
        b.setDisable(!l.mayEdit(index)
                || (NeoLobby.needsDeck(format) && slot.getDeck() == null));
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
     * <p><b>Los mazos salen del modo de la sala</b>, no de una carpeta fija: en
     * una sala de Brawl se ofrecen los de Brawl. Antes se ofrecian siempre los
     * de Commander, que es lo que hacia que la sala solo supiera jugar a una
     * cosa.
     *
     * <p>Se reparten igual que en la pantalla de inicio — tuyos en una pestanya
     * y los de Forge en otra, con {@code format.isMine} decidiendo — y se
     * añaden los bajados de internet, que son mazos hechos y ya estan en el
     * disco. En los formatos con comandante va ademas "Generame uno": Brawl,
     * Oathbreaker y Tiny Leaders <b>no traen preconstruidos</b>, asi que sin
     * eso quien no se haya montado uno se encuentra la lista vacia y la sala no
     * sirve de nada.
     */
    private void pickDeck(final int index) {
        final NeoFormat f = format;
        final GameLobby l = lobby;
        final List<Deck> mine = new ArrayList<>();
        final List<Deck> stock = new ArrayList<>();
        if (forge.neo.net.NeoNetEvent.isLimited(l)) {
            // En un evento el mazo sale del POOL y de ningun otro sitio: es lo
            // que impide sentarse en un draft con un mazo de Commander de tu
            // carpeta. El filtro lo decide el anfitrion (conformance) y viaja
            // en el estado de la sala, asi que el invitado aplica el mismo.
            mine.addAll(forge.neo.net.NeoNetEvent.poolsFor(
                    forge.neo.net.NeoNetEvent.activeEventId(l),
                    forge.neo.net.NeoNetEvent.conformance(l)));
        } else {
            for (final Deck d : f.decks()) {
                (f.isMine(d) ? mine : stock).add(d);
            }
            if (forge.neo.deck.NetDecks.isSupported(f.getGameType())) {
                stock.addAll(forge.neo.deck.NetDecks.cached(f.getGameType()));
            }
        }

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
                overlay::hide,
                // En limitado no se genera nada: el pool es el que es.
                !forge.neo.net.NeoNetEvent.isLimited(l) && f.isCommanderStyle()
                        ? () -> NeoLobby.generateDeck(f) : null);
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
            // Y se sienta ya con un mazo DEL MODO y lista: sentar una IA y
            // tener que elegirle mazo Y marcarla lista era lo que mas pasos
            // costaba de la sala. El mazo se le cambia igual con su boton.
            final Deck deck = NeoLobby.randomAiDeck(format);
            if (deck != null) {
                sendDeck(index, deck);
            }
            // En Momir no hay mazo que darle y aun asi esta lista: esperar uno
            // que no existe la dejaria sin marcar para siempre.
            if (deck != null || !NeoLobby.needsDeck(format)) {
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
        // El modo lo pone el anfitrion: el invitado lo recibe por el cable.
        // Commander de salida porque es a lo que se juega aqui; se cambia con
        // la fila de arriba.
        if (host) {
            NeoLobby.setFormat(lobby, startFormat());
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
            // El modo primero: los asientos se pintan a partir de el (que mazos
            // se ofrecen, si hace falta mazo para estar listo).
            //
            // El aviso se compone AQUI y no se manda por el chat a proposito:
            // por el cable viaja texto tal cual, asi que la frase del anfitrion
            // llegaria en su idioma. Cada lado lee las variantes que le han
            // llegado y escribe la suya.
            final NeoFormat wasFormat = format;
            format = NeoLobby.formatOf(l);
            // En limitado NO se anuncia: el modo lo fija el evento, y decir
            // "ahora se juega a Estandar" mientras la fila de al lado pone
            // "Draft" es contarse una cosa por dos sitios y mal.
            if (format != wasFormat && !first
                    && !forge.neo.net.NeoNetEvent.isLimited(l)) {
                addChatLine(NeoText.get("lobby.format.changed", format.getLabel()));
            }
            // Hasta que no hay asientos, lo que se esta leyendo no es el modo
            // de la sala: es el lobby vacio del invitado, que nace sin
            // variantes (o sea "Estandar") y se rellena cuando llega el primer
            // reparto del anfitrion. Anunciarlo ahi le diria "el anfitrion ha
            // cambiado el modo" nada mas entrar, y no ha cambiado nada.
            first = l.getNumberOfSlots() == 0;
            showFormat(l);
            showEvent(l);
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

    /**
     * Todavia no se ha pintado nada.
     *
     * <p>El primer repaso siempre "cambia" el modo — se pasa del valor inicial
     * al que diga el lobby — y anunciar "el anfitrion ha cambiado el modo" nada
     * mas entrar seria decir que ha pasado algo que no ha pasado.
     */
    private boolean first = true;

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
            l.setMaxWidth(UiScale.px(290));
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
                        if (s.getDeck() == null && NeoLobby.needsDeck(format)) {
                            final List<Deck> mine = new ArrayList<>(format.decks());
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
     * Monta un evento sin pasar por el asistente. <b>Solo para capturas.</b>
     *
     * <p>El asistente son seis preguntas encadenadas y desde una sesion sin
     * manos no hay forma de contestarlas, asi que ni la fila del evento ni la
     * pantalla de picks en red se podrian mirar nunca. Se usa
     * {@code LimitedPoolType.Full}, que es el unico que no abre dialogos de
     * bloque ni de expansion.
     *
     * @param kind {@code draft}, {@code sealed} o {@code draft-run} (que ademas
     *             reparte los sobres y abre la pantalla de picks)
     */
    public void autoEventForTest(final String kind) {
        final Thread t = new Thread(() -> {
            for (int i = 0; i < 200 && lobby == null; i++) {
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!(lobby instanceof forge.gamemodes.net.server.ServerGameLobby server)) {
                return;
            }
            final boolean sealed = kind.startsWith("sealed");
            forge.gamemodes.limited.BoosterDraft draft = null;
            if (!sealed) {
                draft = forge.gamemodes.limited.BoosterDraft.createDraftForNetwork(
                        forge.gamemodes.limited.LimitedPoolType.Full);
                if (draft == null) {
                    return;
                }
            }
            final boolean ok = forge.neo.net.NeoNetEvent.configure(server,
                    sealed ? forge.gamemodes.net.EventFormat.SEALED
                            : forge.gamemodes.net.EventFormat.BOOSTER_DRAFT,
                    forge.gamemodes.limited.LimitedPoolType.Full, draft, 45, 120);
            if (!ok) {
                System.out.println("[lobby-auto] no se ha podido montar el evento");
                return;
            }
            toLimited(lobby, server);
            Platform.runLater(this::refresh);
            if (kind.endsWith("-run")) {
                // Directo al asiento, NO por send(): el IPlayerChangeListener
                // lo pone NetConnectUtil DESPUES de atar el lobby a la
                // pantalla, asi que aqui todavia puede ser null y el "listo"
                // se perderia sin decirlo (salia "notReady" al arrancar).
                final GameLobby l = lobby;
                for (int i = 0; i < l.getNumberOfSlots(); i++) {
                    if (l.mayEdit(i)) {
                        l.applyToSlot(i, UpdateLobbyPlayerEvent.isReadyUpdate(true));
                    }
                }
                final String why = forge.neo.net.NeoNetEvent.start(server);
                System.out.println("[lobby-auto] evento arrancado: "
                        + (why == null ? "ok" : why));
            }
        }, "neo-lobby-auto-event");
        t.setDaemon(true);
        t.start();
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

    /**
     * A que se juega en esta sala.
     *
     * <p>Lo decide el anfitrion y llega al invitado dentro del estado del
     * lobby, asi que aqui no se guarda ninguna decision: es un espejo de
     * {@link NeoLobby#formatOf}.
     */
    public NeoFormat format() {
        return format;
    }
}
