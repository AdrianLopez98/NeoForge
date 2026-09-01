package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.neo.card.CardNode;
import forge.neo.tutorial.Gesture;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;

/**
 * La mesa de juego, con la disposicion de Arena.
 *
 * <pre>
 *   +-----------------------------------------------+---------+
 *   | barra del oponente (vida, zonas, mana)        |         |
 *   |   apoyo del oponente (tierras, artefactos...) |  fases  |
 *   |   criaturas del oponente                      |         |
 *   |  =========== linea de combate ==============  |  stack  |
 *   |   tus criaturas                               |         |
 *   |   tu apoyo                                    | prompt  |
 *   | tu barra                                      |         |
 *   |   tu mano en abanico                          | detalle |
 *   +-----------------------------------------------+---------+
 * </pre>
 *
 * <p>El reparto vertical se hace a mano en {@link #layoutChildren()} en vez de
 * con un VBox. Con contenedores automaticos, el alto que piden las cartas hace
 * que las zonas de abajo (barra y mano) acaben fuera de la ventana. Aqui se
 * reparte un presupuesto de alto fijo: barras y mano cogen lo suyo y el resto
 * se parte entre los dos campos. Asi es imposible que se desborde, sea cual sea
 * la resolucion o el numero de permanentes.
 */
public class TableScreen extends Pane {

    private static final double LINE_H = 14;
    private static final double PAD = 10;

    private final double cardWidth;
    private final double sideWidth;

    private final OpponentTabs opponentTabs = new OpponentTabs();
    private final PlayerBar opponentBar = new PlayerBar(true);
    private final PlayerBar selfBar = new PlayerBar(false);
    private final PlayerField opponentField;
    private final PlayerField selfField;
    private final HandFan hand;
    private final CommandZone commandZone;
    private final PhaseRail phaseRail = new PhaseRail();
    private final CardDetailPanel detail;
    private final VBox stackBox = new VBox(6);
    private final ActionBar actionBar = new ActionBar();
    private final CombatOverlay combatOverlay = new CombatOverlay();
    private final Overlay overlay = new Overlay();
    private final PromptBanner promptBanner;
    private final TurnBanner turnBanner;

    /**
     * El boton del registro.
     *
     * <p>Discreto y en una esquina a proposito: se consulta de vez en cuando y
     * no puede robarle sitio a la mesa. Lleva un punto cuando ha pasado algo que
     * te afecta y todavia no lo has abierto.
     */
    private final Button logButton = new Button(NeoText.get("table.log"));

    /** "Scryfall nos ha limitado, vuelve en Ns." Ver {@link ImageCooldownBadge}. */
    private final ImageCooldownBadge cooldownBadge = new ImageCooldownBadge();

    /**
     * Capa aparte para el menu de pausa.
     *
     * <p>El motor puede estar bloqueado esperando una respuesta justo cuando se
     * pulsa Escape. Con una sola capa, abrir el menu se llevaria por delante ese
     * dialogo y la partida se quedaria colgada para siempre.
     */
    private final Overlay menuOverlay = new Overlay();

    /**
     * La carta ampliada del click derecho.
     *
     * <p>Capa propia y por encima de todo: se abre y se cierra constantemente
     * mientras lees la mesa, y no puede interferir con lo que el motor este
     * esperando ni con el menu de pausa.
     */
    private final Overlay zoomOverlay = new Overlay();

    /**
     * El tapete, detras de todo.
     *
     * <p>Y encima, un velo oscuro. No es decoracion: la mesa tiene que ser
     * oscura para que el arte de las cartas destaque (seccion 6 de las notas de diseño),
     * y una foto a pleno color compite con las cartas justo en lo unico que no
     * puede competir. Con el velo, el tapete se nota y las cartas siguen
     * mandando.
     *
     * <p>Los dos son transparentes al raton: la mesa se clica constantemente
     * para responder al motor y un fondo que se coma los clicks colgaria la
     * partida.
     */
    /**
     * El estado del jugador, al pasar el raton por su barra.
     *
     * <p>Lo compone el motor entero y traducido ({@code PlayerView.getDetails}
     * mas {@code getPlayerCommanderInfo}): vidas, veneno, cartas en mano y tu
     * maximo, tierras jugadas, robadas este turno, turnos extra, el impuesto de
     * cada comandante y el desglose del dano de comandante.
     *
     * <p>Al pasar el raton y NO al clicar: el click de una barra ya significa
     * "elijo a este jugador" y el motor lo necesita — quien empieza, a quien
     * atacas, a quien apunta un hechizo. Y transparente al raton, para no
     * robarle el hover a la propia barra.
     */
    private final Label playerDetails = new Label();

    private final javafx.scene.image.ImageView playmat = new javafx.scene.image.ImageView();
    private final javafx.scene.shape.Rectangle playmatVeil = new javafx.scene.shape.Rectangle();

    private final VBox side;
    private final Line combatLine = new Line();

    private final List<CardNode> extraNodes = new ArrayList<>();
    private Consumer<CardNode> onCardHover;

    // ---------------------------------------------------------------
    // Tutorial: la banda de arriba y el cerco que senyala donde mirar
    // ---------------------------------------------------------------

    /**
     * La banda del tutorial. Null salvo mientras se juega una leccion.
     *
     * <p>Ocupa sitio de verdad en el reparto de alto (no flota sobre la mesa)
     * porque casi todo lo que el tutorial pide se contesta clicando cartas.
     * Ver {@link CoachPanel}.
     */
    private CoachPanel coach;

    /**
     * El cerco que rodea la parte de la que se esta hablando.
     *
     * <p>Transparente al raton, siempre: el paso suele consistir en clicar
     * justo lo que hay dentro del cerco.
     */
    private final javafx.scene.shape.Rectangle spotlight =
            new javafx.scene.shape.Rectangle();

    private String spotName;

    /** Quien se entera de los gestos de la interfaz. Solo lo usa el tutorial. */
    private Consumer<String> gestureSpy;

    public TableScreen(final double cardWidth, final double sideWidth) {
        this.cardWidth = cardWidth;
        this.sideWidth = sideWidth;
        this.hand = new HandFan(cardWidth * 1.12);
        this.commandZone = new CommandZone(cardWidth * 1.12);
        this.detail = new CardDetailPanel(sideWidth - 52);
        this.opponentField = new PlayerField(cardWidth * 0.92, true);
        this.selfField = new PlayerField(cardWidth, false);

        getStyleClass().add("table-root");

        opponentField.setOnCardHover(this::hovered);
        selfField.setOnCardHover(this::hovered);
        opponentField.setOnCardClick(this::cardClicked);
        selfField.setOnCardClick(this::cardClicked);

        combatLine.getStyleClass().add("combat-line");
        hand.getStyleClass().add("hand-area");

        final Label stackTitle = new Label(NeoText.get("table.stack"));
        stackTitle.getStyleClass().add("caption");
        stackBox.getStyleClass().add("stack-box");
        stackBox.setPadding(new Insets(8, 10, 8, 10));
        final Label stackEmpty = new Label(NeoText.get("table.stack.empty"));
        stackEmpty.getStyleClass().add("hud-sub");
        stackBox.getChildren().addAll(stackTitle, stackEmpty);

        // La columna: fases, stack y botones. El panel de detalle NO esta.
        //
        // Ocupaba un tercio de la columna para ensenyar una carta que ya se ve
        // en la mesa, y se la robaba al stack — que es de lo mas importante de
        // una partida de Magic. Para leer una carta esta el click derecho, que
        // funciona en cualquier sitio y no le quita sitio a nada.
        //
        // El objeto sigue existiendo (lo usan showDetail y las maquetas), pero
        // no esta en la escena: no ocupa, no pinta y no decodifica imagenes.
        side = new VBox(10, phaseRail, stackBox, actionBar);
        side.setPadding(new Insets(PAD));

        commandZone.setOnCardHover(this::hovered);

        promptBanner = new PromptBanner(cardWidth * 1.55);
        turnBanner = new TurnBanner();
        logButton.getStyleClass().add("log-button");

        playerDetails.getStyleClass().add("player-details");
        playerDetails.setWrapText(true);
        playerDetails.setMouseTransparent(true);
        playerDetails.setVisible(false);
        playerDetails.setManaged(false);
        playerDetails.setMaxWidth(420);
        playerDetails.setMinHeight(Region.USE_PREF_SIZE);
        hoverDetails(opponentBar, true);
        hoverDetails(selfBar, false);

        playmat.setPreserveRatio(false);
        playmat.setMouseTransparent(true);
        playmatVeil.setMouseTransparent(true);
        playmatVeil.setFill(javafx.scene.paint.Color.web("#0B0E13", 0.62));
        getChildren().addAll(playmat, playmatVeil);
        applyPlaymat();

        // El area de juego va dentro de un visor que se puede acercar y
        // arrastrar. Son DOS nodos y hacen falta los dos: el recorte tiene que
        // vivir en un nodo SIN transformar (en JavaFX el clip se transforma con
        // el nodo, asi que un recorte en el mismo sitio que la escala crece con
        // ella y deja de recortar) y la escala en el de dentro.
        board.getChildren().addAll(opponentField, combatLine, selfField);
        viewport.getChildren().add(board);
        viewport.setClip(viewportClip);
        viewport.setPickOnBounds(false);
        board.setPickOnBounds(false);

        spotlight.getStyleClass().add("coach-spot");
        spotlight.setMouseTransparent(true);
        spotlight.setVisible(false);
        spotlight.setManaged(false);
        spotlight.setArcWidth(14);
        spotlight.setArcHeight(14);
        spotlight.setFill(javafx.scene.paint.Color.TRANSPARENT);

        getChildren().addAll(opponentTabs, opponentBar, viewport,
                selfBar, hand, commandZone, side, combatOverlay, logButton, cooldownBadge,
                promptBanner, turnBanner, playerDetails, zoomBadge, spotlight,
                overlay, menuOverlay, zoomOverlay);

        // Se cierra con un click en cualquier sitio, como en Arena.
        //
        // Y con un FILTRO, no solo con el click de fondo. El hueco que rodea a
        // la carta dentro de la capa lo ocupa un contenedor sin fondo, y un
        // Region sin fondo no se puede clicar: ahi el click no llegaba ni a la
        // carta ni al fondo de la capa, asi que parecia que solo cerraba
        // clicando la carta. Un filtro en la capa entera lo caza pase lo que
        // pase, y no hace falta acertar con la geometria.
        zoomOverlay.setOnBackgroundClick(this::hideZoom);
        // EXCEPTO sobre un control de verdad: el "Ver el texto actual" de
        // Deadpool (y el resto del panel de estado que compone CardZoom) vive
        // DENTRO de esta capa, y un filtro que se traga todo se adelanta a su
        // propio boton. Ver CardZoom.isInteractiveTarget.
        zoomOverlay.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (CardZoom.isInteractiveTarget(e.getTarget())) {
                return;
            }
            e.consume();
            hideZoom();
        });

        combatOverlay.setLocator(this::boundsOf);
        opponentBar.setOnZoneClicked(this::showZone);
        selfBar.setOnZoneClicked(this::showZone);
        // Las pilas grandes de la mesa abren el mismo visor que el contador
        // pequenyo de la barra. Son lo que de verdad se mira — estan en el campo
        // de batalla y son grandes — asi que tienen que responder igual.
        opponentField.setOnZoneClicked(this::showZone);
        selfField.setOnZoneClicked(this::showZone);
        // Antes que los gestos de carta: un arrastre de mesa se traga el
        // evento, y asi no empieza ademas a arrastrar la carta de debajo.
        installBoardZoom();
        installDragGestures();
    }

    // ---------------------------------------------------------------
    // Layout: presupuesto de alto explicito
    // ---------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        final double contentW = Math.max(200, w - sideWidth);

        side.resizeRelocate(contentW, 0, sideWidth, h);

        // El estado del jugador, pegado a su barra y hacia el centro de la
        // mesa: fuera de la pantalla no sirve, y sobre la mano taparia cartas.
        if (playerDetails.isVisible()) {
            final double pw = Math.min(420, contentW - 2 * PAD);
            final double ph = playerDetails.prefHeight(pw);
            final double py = detailsBelow
                    ? opponentBar.getBoundsInParent().getMaxY() + 6
                    : selfBar.getBoundsInParent().getMinY() - ph - 6;
            playerDetails.resizeRelocate(PAD, Math.max(PAD, py), pw, ph);
        }

        // El tapete cubre la mesa entera, incluida la columna: un fondo que se
        // corta a mitad de pantalla se ve peor que no tenerlo.
        playmat.setFitWidth(w);
        playmat.setFitHeight(h);
        playmatVeil.setWidth(w);
        playmatVeil.setHeight(h);

        // El boton del registro, en la esquina de abajo a la izquierda: es la
        // zona mas muerta de la mesa (la mano empieza mas a la derecha).
        final double lbW = logButton.prefWidth(-1);
        final double lbH = logButton.prefHeight(lbW);
        logButton.resizeRelocate(PAD, h - lbH - PAD, lbW, lbH);
        combatOverlay.resizeRelocate(0, 0, w, h);

        // Encima del boton del registro: la misma esquina muerta, y solo
        // aparece cuando hace falta.
        if (cooldownBadge.isVisible()) {
            final double cbW = cooldownBadge.prefWidth(-1);
            final double cbH = cooldownBadge.prefHeight(cbW);
            cooldownBadge.resizeRelocate(PAD, h - lbH - PAD - cbH - 6, cbW, cbH);
        }

        // El cartel de turno, centrado y ARRIBA del todo del area de juego: en
        // el centro exacto taparia las criaturas justo cuando hay que mirarlas.
        if (turnBanner.isVisible()) {
            final double bw = Math.min(TurnBanner.MAX_WIDTH,
                    Math.max(turnBanner.bannerWidth(), contentW * 0.42));
            final double bh = turnBanner.bannerHeight(bw);
            turnBanner.resizeRelocate((contentW - bw) / 2, h * 0.13, bw, bh);
        }
        overlay.resizeRelocate(0, 0, w, h);
        menuOverlay.resizeRelocate(0, 0, w, h);
        zoomOverlay.resizeRelocate(0, 0, w, h);

        // La banda del tutorial, si la hay, se cobra su alto ANTES de repartir:
        // asi la mesa se encoge un poco y no queda nada tapado. Ver CoachPanel.
        double coachH = 0;
        if (coach != null && coach.isVisible()) {
            coachH = coach.prefHeight(contentW);
            coach.resizeRelocate(0, 0, contentW, coachH);
        }

        final double tabsH = opponentTabs.barHeight();
        final double oppBarH = opponentBar.prefHeight(contentW);
        final double selfBarH = selfBar.prefHeight(contentW);
        double handH = hand.prefHeight(contentW);

        // Si la ventana es baja, la mano cede altura antes que los campos:
        // es mejor ver la mesa entera con la mano un poco recortada que al
        // reves.
        final double fixed = coachH + tabsH + oppBarH + selfBarH + LINE_H;
        final double minFields = cardWidth * 1.2;
        if (fixed + handH + minFields > h) {
            handH = Math.max(cardWidth * 0.7, h - fixed - minFields);
        }

        final double fieldsH = Math.max(0, h - fixed - handH);
        final double eachField = fieldsH / 2.0;

        double y = coachH;
        if (tabsH > 0) {
            opponentTabs.resizeRelocate(0, y, contentW, tabsH);
            y += tabsH;
        }
        opponentBar.resizeRelocate(0, y, contentW, oppBarH);
        y += oppBarH;

        // Desde aqui y hasta la barra de abajo, todo va dentro del visor, o
        // sea en SUS coordenadas: se le resta el origen.
        final double boardTop = y;
        final double boardH = eachField * 2 + LINE_H;
        viewport.resizeRelocate(0, boardTop, contentW, boardH);
        viewportClip.setWidth(contentW);
        viewportClip.setHeight(boardH);
        board.resizeRelocate(0, 0, contentW, boardH);
        applyBoardTransform();

        opponentField.resizeRelocate(0, 0, contentW, eachField);
        y += eachField;

        combatLine.setStartX(PAD * 3);
        combatLine.setEndX(contentW - PAD * 3);
        combatLine.setStartY(y - boardTop + LINE_H / 2);
        combatLine.setEndY(y - boardTop + LINE_H / 2);

        // El aviso central va justo sobre la linea de combate: es la franja mas
        // vacia de la mesa, y esta a la altura donde ya estas mirando.
        if (promptBanner.isVisible()) {
            final double bw = Math.min(contentW - PAD * 4, promptBanner.prefWidth(-1));
            final double bh = promptBanner.prefHeight(bw);
            promptBanner.resizeRelocate((contentW - bw) / 2, y + LINE_H / 2 - bh / 2, bw, bh);
        }

        y += LINE_H;

        selfField.resizeRelocate(0, y - boardTop, contentW, eachField);
        y += eachField;

        // El aviso de "estas mirando de cerca", en la esquina del visor. Es la
        // unica forma de volver sin adivinar: se clica y se deshace.
        if (zoomBadge.isVisible()) {
            final double bw = zoomBadge.prefWidth(-1);
            final double bh = zoomBadge.prefHeight(bw);
            zoomBadge.resizeRelocate(PAD, boardTop + PAD, bw, bh);
        }

        selfBar.resizeRelocate(0, y, contentW, selfBarH);
        y += selfBarH;

        // La zona de mando va a la derecha de la mano, a su misma altura:
        // el comandante no esta en tu mano, pero lo tienes siempre a la vista.
        final double handArea = Math.max(0, h - y);
        // Con techo: este ancho se le quita a la mano, asi que por muchos
        // emblemas o efectos que haya en la zona de mando no puede llevarse
        // media mesa. Reportado jugando: once efectos identicos y la mano
        // fuera de la pantalla.
        final double cmdW = Math.min(contentW * 0.34, commandZone.preferredWidth() + 16);
        hand.resizeRelocate(0, y, Math.max(120, contentW - cmdW), handArea);
        commandZone.resizeRelocate(contentW - cmdW, y, cmdW, handArea);

        // El cerco va el ULTIMO: se calcula sobre las posiciones que se acaban
        // de repartir, no sobre las del layout anterior.
        layoutSpotlight();
    }

    // ---------------------------------------------------------------
    // Tutorial
    // ---------------------------------------------------------------

    /**
     * Pone (o quita, con null) la banda del tutorial.
     *
     * <p>Se anyade delante del resto para que quede por debajo de las capas de
     * dialogo: si el motor levanta una pregunta, manda la pregunta.
     */
    public void setCoach(final CoachPanel panel) {
        if (coach != null) {
            getChildren().remove(coach);
        }
        coach = panel;
        if (coach != null) {
            getChildren().add(getChildren().indexOf(spotlight), coach);
        }
        requestLayout();
    }

    public CoachPanel getCoach() {
        return coach;
    }

    /**
     * Rodea con un cerco la parte de la mesa de la que se esta hablando.
     *
     * <p>Los nombres son los de {@code TutorialStep.Spot}. Un nombre que no se
     * reconozca — o {@code null} — simplemente quita el cerco: senyalar el sitio
     * equivocado seria peor que no senyalar nada.
     */
    public void spotlight(final String name) {
        this.spotName = name;
        requestLayout();
    }

    private void layoutSpotlight() {
        final Region target = spotTarget(spotName);
        if (target == null || target.getWidth() <= 1 || target.getHeight() <= 1) {
            spotlight.setVisible(false);
            return;
        }
        // De coordenadas del nodo a las nuestras. Se pasa por la escena a
        // proposito: el campo de batalla vive dentro del visor, que puede estar
        // escalado y desplazado, y localToScene aplica esas transformaciones.
        final Bounds b = sceneToLocal(target.localToScene(target.getBoundsInLocal()));
        final double pad = 5;
        // Recortado a la ventana: la mano llega hasta el borde de abajo, y un
        // cerco que se sale deja el lado inferior fuera de la pantalla — o sea
        // que justo lo que hay que mirar parece no tener marco.
        final double x0 = Math.max(1, b.getMinX() - pad);
        final double y0 = Math.max(1, b.getMinY() - pad);
        final double x1 = Math.min(getWidth() - 1, b.getMaxX() + pad);
        final double y1 = Math.min(getHeight() - 1, b.getMaxY() + pad);
        if (x1 <= x0 || y1 <= y0) {
            spotlight.setVisible(false);
            return;
        }
        spotlight.setX(x0);
        spotlight.setY(y0);
        spotlight.setWidth(x1 - x0);
        spotlight.setHeight(y1 - y0);
        spotlight.setVisible(true);
    }

    private Region spotTarget(final String name) {
        if (name == null) {
            return null;
        }
        return switch (name) {
            case "HAND" -> hand;
            case "BOARD_SELF" -> selfField;
            case "BOARD_OPP" -> opponentField;
            case "SIDE" -> actionBar;
            case "PHASES" -> phaseRail;
            case "STACK" -> stackBox;
            case "SELF_BAR" -> selfBar;
            case "OPP_BAR" -> opponentBar;
            case "COMMAND" -> commandZone;
            case "LOG" -> logButton;
            default -> null;
        };
    }

    /**
     * Quien se entera de los gestos que el motor NO ve.
     *
     * <p>Ampliar una carta, acercar la mesa, abrir una zona, poner una parada
     * de fase: nada de eso llega al motor, asi que el tutorial no puede
     * enterarse preguntandole a el. Se cuenta desde aqui, que es donde pasan.
     */
    public void setGestureSpy(final Consumer<String> spy) {
        this.gestureSpy = spy;
    }

    /**
     * Cuenta un gesto. Publico porque no todos pasan por la mesa: el menu de
     * pausa y los Ajustes viven en la capa de encima y avisan desde alli.
     */
    public void gesture(final String what) {
        final Consumer<String> spy = gestureSpy;
        if (spy != null) {
            spy.accept(what);
        }
    }

    // ---------------------------------------------------------------
    // Acercar y arrastrar la mesa
    // ---------------------------------------------------------------

    /**
     * El visor: el area de juego, que se puede acercar y mover.
     *
     * <p>Existe porque con la mesa llena las cartas se encogen hasta que no se
     * leen — y encoger es lo correcto, porque lo que no cabe no cabe. Lo que
     * faltaba era poder <b>acercarse</b> a mirar sin que la mesa deje de estar
     * donde estaba: Ctrl + rueda para acercar, arrastrar para moverse.
     *
     * <p>Solo se transforma el area de juego. Las barras, la mano, la columna y
     * los dialogos se quedan como estan: son los controles, y un control que se
     * va de la pantalla al acercarte no sirve para nada.
     */
    private final Pane viewport = new Pane() {
        @Override
        protected void layoutChildren() {
            // Lo coloca TableScreen.layoutChildren, en coordenadas explicitas.
        }
    };

    private final Pane board = new Pane() {
        @Override
        protected void layoutChildren() {
            // Idem: aqui no manda un contenedor automatico.
        }
    };

    private final javafx.scene.shape.Rectangle viewportClip =
            new javafx.scene.shape.Rectangle();

    /** Cuanto se acerca la mesa: 1 es el tamano normal. */
    private double zoom = 1;
    private double panX;
    private double panY;

    private static final double ZOOM_MIN = 1;
    private static final double ZOOM_MAX = 3;
    private static final double ZOOM_STEP = 1.12;

    private boolean panning;
    private double panPressX;
    private double panPressY;
    private double panFromX;
    private double panFromY;

    private final javafx.scene.control.Label zoomBadge = new javafx.scene.control.Label();

    private void installBoardZoom() {
        zoomBadge.getStyleClass().add("zoom-badge");
        zoomBadge.setVisible(false);
        zoomBadge.setManaged(false);
        zoomBadge.setOnMouseClicked(e -> {
            e.consume();
            resetBoardZoom();
        });

        addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (!e.isControlDown() || isModalShowing()) {
                return;
            }
            final Point2D m = viewport.sceneToLocal(e.getSceneX(), e.getSceneY());
            zoomAt(zoom * (e.getDeltaY() >= 0 ? ZOOM_STEP : 1 / ZOOM_STEP),
                    m.getX(), m.getY());
            e.consume();
        });

        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (isModalShowing() || !isPanGesture(e)) {
                return;
            }
            panning = true;
            panClick = true;
            panPressX = e.getSceneX();
            panPressY = e.getSceneY();
            panFromX = panX;
            panFromY = panY;
            setCursor(javafx.scene.Cursor.CLOSED_HAND);
            e.consume();
        });

        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!panning) {
                return;
            }
            panX = panFromX + (e.getSceneX() - panPressX);
            panY = panFromY + (e.getSceneY() - panPressY);
            applyBoardTransform();
            gesture(Gesture.BOARD_PAN);
            e.consume();
        });

        addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!panning) {
                return;
            }
            panning = false;
            setCursor(null);
            e.consume();
        });

        // Y el click que viene detras del arrastre no vale como click.
        //
        // JavaFX manda MOUSE_CLICKED al soltar aunque el PRESSED se haya
        // consumido, asi que sin esto un Ctrl+click sobre una carta movia la
        // mesa Y ADEMAS activaba la carta.
        addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (panClick) {
                panClick = false;
                e.consume();
            }
        });
    }

    private boolean panClick;

    /**
     * Que gesto mueve la mesa.
     *
     * <p>El boton izquierdo a secas ya significa otra cosa — arrastrar una
     * carta para jugarla o para atacar — asi que mover la mesa pide el boton
     * central, o Ctrl con el izquierdo, que es el mismo Ctrl con el que se
     * acerca. Y solo cuando hay algo que mover: sin acercar, no.
     */
    private boolean isPanGesture(final MouseEvent e) {
        return zoom > 1.001
                && (e.getButton() == javafx.scene.input.MouseButton.MIDDLE
                    || (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                        && e.isControlDown()));
    }

    /**
     * Acerca o aleja dejando quieto el punto que hay bajo el raton.
     *
     * <p>Es lo que hace que acercarse se sienta natural: te acercas a donde
     * estas mirando, no al centro de la pantalla.
     */
    private void zoomAt(final double wanted, final double mx, final double my) {
        final double next = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, wanted));
        if (Math.abs(next - zoom) < 0.0001) {
            return;
        }
        // La escala pivota en el centro del propio nodo, asi que el punto fijo
        // se mantiene corrigiendo el desplazamiento.
        final double cx = board.getWidth() / 2;
        final double cy = board.getHeight() / 2;
        panX = mx - cx - (next / zoom) * (mx - cx - panX);
        panY = my - cy - (next / zoom) * (my - cy - panY);
        zoom = next;
        applyBoardTransform();
        gesture(Gesture.BOARD_ZOOM);
    }

    /** Vuelve a la mesa entera. */
    public void resetBoardZoom() {
        zoom = 1;
        panX = 0;
        panY = 0;
        applyBoardTransform();
    }

    private void applyBoardTransform() {
        // El desplazamiento se limita a lo que de verdad sobra: sin esto se
        // puede arrastrar la mesa fuera de la pantalla y quedarse mirando el
        // tapete sin saber como volver.
        final double maxX = Math.max(0, (zoom - 1) * board.getWidth() / 2);
        final double maxY = Math.max(0, (zoom - 1) * board.getHeight() / 2);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));

        board.setScaleX(zoom);
        board.setScaleY(zoom);
        board.setTranslateX(panX);
        board.setTranslateY(panY);

        final boolean on = zoom > 1.001;
        zoomBadge.setText(NeoText.get("table.zoom", String.format("%.1f", zoom)));
        if (zoomBadge.isVisible() != on) {
            zoomBadge.setVisible(on);
            requestLayout();
        }
        if (!on) {
            setCursor(null);
        }
    }

    /** Cuanto esta acercada la mesa. Solo para comprobar con capturas. */
    public double boardZoom() {
        return zoom;
    }

    // ---------------------------------------------------------------
    // Alimentar la mesa
    // ---------------------------------------------------------------

    /**
     * Los permanentes de cada lado.
     *
     * <p>Se guardan los dos y se repintan los dos, aunque solo cambie uno.
     * Hace falta porque <b>lo enganchado cruza de lado</b>: un aura del rival
     * sobre una criatura tuya se pinta debajo de TU criatura, y para saber eso
     * hay que tener las dos listas a la vez. Ver {@code PlayerField.setCards}.
     */
    public void setOpponentBattlefield(final List<CardView> cards) {
        opponentCards = cards == null ? List.of() : cards;
        repaintFields();
    }

    public void setSelfBattlefield(final List<CardView> cards) {
        selfCards = cards == null ? List.of() : cards;
        repaintFields();
    }

    private List<CardView> opponentCards = List.of();
    private List<CardView> selfCards = List.of();

    private void repaintFields() {
        final List<CardView> everywhere = new ArrayList<>(selfCards);
        everywhere.addAll(opponentCards);
        opponentField.setCards(opponentCards, everywhere);
        selfField.setCards(selfCards, everywhere);
        reindex();
    }

    /** Contadores de cementerio y exilio que se ven en la propia mesa. */
    public void setOpponentZonePiles(final int graveyard, final int exile) {
        setOpponentZonePiles(graveyard, exile, false, false);
    }

    public void setOpponentZonePiles(final int graveyard, final int exile,
                                     final boolean gravePlayable, final boolean exilePlayable) {
        opponentField.setZoneOwner(opponentBar.getPlayer());
        opponentField.setZoneCounts(graveyard, exile, gravePlayable, exilePlayable);
    }

    public void setSelfZonePiles(final int graveyard, final int exile) {
        setSelfZonePiles(graveyard, exile, false, false);
    }

    public void setSelfZonePiles(final int graveyard, final int exile,
                                 final boolean gravePlayable, final boolean exilePlayable) {
        selfField.setZoneOwner(selfBar.getPlayer());
        selfField.setZoneCounts(graveyard, exile, gravePlayable, exilePlayable);
    }

    public void setHand(final List<CardView> cards) {
        hand.clearCards();
        extraNodes.clear();
        for (final CardView cv : cards) {
            final CardNode n = new CardNode(cardWidth * 1.12);
            n.setCard(cv);
            n.hoverProperty().addListener((o, was, is) -> {
                if (is) {
                    hovered(n);
                }
            });
            n.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    cardClicked(n);
                }
            });
            extraNodes.add(n);
            hand.add(n);
        }
        reindex();
    }

    /** Pestanyas de rival, para partidas de mas de dos. */
    public void setOpponents(final List<PlayerView> opponents,
                             final PlayerView selected,
                             final PlayerView activeTurn) {
        opponentTabs.setOpponents(opponents, selected, activeTurn);
        requestLayout();
    }

    /**
     * Vamos a cambiar de rival: lo de arriba se sustituye entero, no se muere.
     * Ver {@link BattlefieldPane#skipRemovalAnimation()}.
     */
    public void skipOpponentRemovalAnimation() {
        opponentField.skipRemovalAnimation();
    }

    public void setOnOpponentSelected(final Consumer<PlayerView> h) {
        opponentTabs.setOnSelect(h);
    }

    /** Cartas en la zona de mando (el comandante). */
    public void setCommandZone(final List<CardView> command,
                               final PlayerView owner) {
        commandZone.setCards(command, owner);
        reindex();
    }

    /** Muestra una carta en el panel de detalle sin necesidad de hover. */
    public void showDetail(final CardView card) {
        detail.show(card);
    }

    private void hovered(final CardNode n) {
        detail.show(n.getCard());
        if (onCardHover != null) {
            onCardHover.accept(n);
        }
    }

    public PlayerBar getOpponentBar() {
        return opponentBar;
    }

    /** El campo del rival que se este mirando ahora (uno solo, aunque haya varios). */
    public PlayerField getOpponentField() {
        return opponentField;
    }

    /** Tu propio campo. */
    public PlayerField getSelfField() {
        return selfField;
    }

    /** Tu pila de cementerio. Para las pruebas: la del rival esta antes. */
    public ZonePile getSelfGraveyardPile() {
        return selfField.getGraveyardPile();
    }

    public PlayerBar getSelfBar() {
        return selfBar;
    }

    /**
     * Pinta el stack: lo que esta esperando a resolverse, de arriba abajo.
     *
     * <p><b>Sin cartas, a proposito.</b> Antes cada entrada llevaba su carta al
     * lado y el resultado era ilegible en cuanto se encadenaban varias: la
     * columna es estrecha, las cartas salian a 50 px — demasiado pequenyas para
     * reconocerlas — y con la carta grande no cabian las de debajo. El stack es
     * una LISTA ORDENADA, y lo que hace falta leer de ella es cuantos hay, de
     * quien son, en que orden se resuelven y que hacen. Todo eso es texto.
     *
     * <p>La carta no se pierde: clicar una fila la ensenya en el panel de
     * detalle, que esta justo debajo y no tapa la mesa — que es importante,
     * porque muchas respuestas se dan clicando cartas del campo.
     */
    public void setStack(final Iterable<StackItemView> items, final PlayerView me) {
        stackBox.getChildren().clear();

        final java.util.List<StackItemView> list = new java.util.ArrayList<>();
        if (items != null) {
            for (final StackItemView item : items) {
                list.add(item);
            }
        }
        stackSize = list.size();

        // La caja se estira SOLO cuando hay mas filas de las que caben, y
        // entonces se queda con el hueco que dejo el panel de detalle: son unos
        // 76 px, o sea tres filas mas a la vista sin tener que rodar.
        //
        // Con una o dos cosas en el stack no se estira: una caja de 285 px con
        // una linea dentro llama la atencion hacia donde no hay nada.
        VBox.setVgrow(stackBox,
                list.size() > VISIBLE_ROWS ? Priority.ALWAYS : Priority.NEVER);

        final Label title = new Label(list.isEmpty()
                ? NeoText.get("table.stack")
                : NeoText.get("table.stack.count", list.size()));
        title.getStyleClass().add("caption");
        stackBox.getChildren().add(title);

        if (list.isEmpty()) {
            final Label empty = new Label(NeoText.get("table.stack.empty"));
            empty.getStyleClass().add("hud-sub");
            stackBox.getChildren().add(empty);
            return;
        }

        final VBox rows = new VBox(2);
        rows.getChildren().add(topRow(list.get(0), me));
        for (int i = 1; i < list.size(); i++) {
            rows.getChildren().add(waitingRow(list.get(i), i + 1, me));
        }

        // Se ensenyan TODOS, y con quince encima puedes querer mirar el
        // septimo: lo que no cabe se alcanza rodando. Cortar en cinco sin
        // decirlo, como antes, era mentir sobre lo que hay esperando.
        final ScrollPane scroll = new ScrollPane(rows);
        scroll.getStyleClass().add("stack-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(
                Math.min(list.size(), VISIBLE_ROWS) * ROW_H + TOP_ROW_EXTRA);
        stackBox.getChildren().add(scroll);
    }

    /** Cuantas filas se ven de una vez; el resto, rodando. */
    private static final int VISIBLE_ROWS = 6;

    /** Lo que mide una fila normal, con su separacion. */
    private static final double ROW_H = 23;

    /** Lo que la primera fila abulta de mas por llevar el texto de la accion. */
    private static final double TOP_ROW_EXTRA = 38;

    /**
     * El que se resuelve AHORA.
     *
     * <p>Es el unico que ademas dice QUE hace. De los que esperan debajo, lo
     * que necesitas saber es que estan y de quien son; el detalle se pide
     * clicando, y para entonces ya te has fijado en ellos.
     */
    private Region topRow(final StackItemView item, final PlayerView me) {
        final Label header = new Label(headerFor(item, me));
        header.getStyleClass().add("stack-now");
        header.pseudoClassStateChanged(MINE, isMine(item, me));

        final Label name = new Label(headlineOf(item, me));
        name.getStyleClass().add("stack-top-name");
        name.setWrapText(true);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setMinHeight(Region.USE_PREF_SIZE);

        final VBox box = new VBox(2, header, name);
        box.getStyleClass().add("stack-top");

        final String what = effectText(item);
        if (!what.isEmpty()) {
            final Label text = new Label(what);
            text.getStyleClass().add(item.isTrigger() ? "stack-trigger" : "stack-spell");
            text.setWrapText(true);
            text.setMaxWidth(Double.MAX_VALUE);
            text.setMinHeight(Region.USE_PREF_SIZE);
            box.getChildren().add(text);
        }
        wireDetail(box, item);
        return box;
    }

    /** Uno de los que esperan: numero, quien y nombre. Una linea y ya. */
    private Region waitingRow(final StackItemView item, final int order, final PlayerView me) {
        final Label num = new Label(String.valueOf(order));
        num.getStyleClass().add("stack-order");
        num.setMinWidth(15);

        final Label who = new Label(ownerName(item, me));
        who.getStyleClass().add("stack-who");
        who.pseudoClassStateChanged(MINE, isMine(item, me));
        who.setMinWidth(Region.USE_PREF_SIZE);

        final Label name = new Label(headlineOf(item, me));
        name.getStyleClass().add(item.isTrigger() ? "stack-trigger" : "stack-spell");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        final HBox row = new HBox(6, num, who, name);
        row.getStyleClass().add("stack-strip");
        row.setAlignment(Pos.CENTER_LEFT);
        wireDetail(row, item);
        return row;
    }

    /**
     * Clicar una entrada ensenya su carta en el panel de detalle.
     *
     * <p>En el panel y no en la carta ampliada: la ampliada tapa la mesa, y
     * mientras hay stack la mesa es justo lo que hay que poder clicar para
     * responder. El detalle esta al lado y no estorba.
     */
    private void wireDetail(final Region row, final StackItemView item) {
        final CardView source = item.getSourceCard();
        if (source == null) {
            return;
        }
        row.setCursor(javafx.scene.Cursor.HAND);
        row.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                // El menu del stack si hay partida detras; si no, lo de
                // siempre. La maqueta (run.cmd ui) no tiene controlador al que
                // preguntarle nada, y ahi el click derecho tiene que seguir
                // ampliando la carta como hasta ahora.
                final Consumer<StackItemView> menu = onStackMenu;
                if (menu != null) {
                    menu.accept(item);
                } else {
                    showZoom(source);
                }
            } else {
                showDetail(source);
            }
        });
    }

    /**
     * Que hacer al clicar con el derecho en algo del stack.
     *
     * <p>Lo engancha {@code NeoMatchUI}, que es quien tiene el controlador del
     * jugador y por tanto lo unico que puede contestar "¿esto ya lo dejas
     * pasar?". La mesa no sabe de eso ni tiene por que.
     */
    public void setOnStackMenu(final Consumer<StackItemView> handler) {
        onStackMenu = handler;
    }

    private Consumer<StackItemView> onStackMenu;

    private static final javafx.css.PseudoClass MINE =
            javafx.css.PseudoClass.getPseudoClass("mine");

    /** "SE RESUELVE AHORA \u00b7 IA-1", o sin el quien si el motor no lo dice. */
    private static String headerFor(final StackItemView item, final PlayerView me) {
        final String owner = ownerName(item, me);
        return owner.isEmpty()
                ? NeoText.get("stack.now.plain")
                : NeoText.get("stack.now", owner);
    }

    private static boolean isMine(final StackItemView item, final PlayerView me) {
        return me != null && me.equals(item.getActivatingPlayer());
    }

    /** Quien lo ha puesto ahi. Lo tuyo se dice "TU", que se lee antes. */
    private static String ownerName(final StackItemView item, final PlayerView me) {
        final PlayerView who = item.getActivatingPlayer();
        if (who == null) {
            return "";
        }
        return isMine(item, me) ? NeoText.get("stack.you")
                : forge.neo.match.PlayerName.of(who);
    }

    /** El titulo de una entrada: el nombre de la carta, y si es disparo, dicho. */
    private static String headlineOf(final StackItemView item, final PlayerView me) {
        final CardView src = item.getSourceCard();
        final String name = src == null || src.getCurrentState() == null
                ? "" : CardText.nameOf(src.getCurrentState());
        String head = item.isTrigger() ? NeoText.get("stack.triggerOf", name) : name;
        // Un disparo OPCIONAL se puede declinar; uno normal, no — y sin esto
        // no habia forma de distinguirlos salvo leyendo el texto entero cada
        // vez. Solo se dice de los TUYOS: es tu decision, no la del rival, y
        // el motor solo te pregunta a ti si aceptas los tuyos.
        if (item.isOptionalTrigger() && isMine(item, me)) {
            head = NeoText.get("stack.optional") + " " + head;
        }
        // Los costes opcionales que HAN pagado: kicker, escalada, exceso...
        // Cambian lo que va a hacer el hechizo y decidian si merece la pena
        // responder; el motor los publica aparte (`getOptionalCostString`) y no
        // estan en el texto, asi que sin esto no habia forma de saberlo.
        final String extra = optionalCostOf(item);
        return extra.isEmpty() ? head : head + "  " + extra;
    }

    private static String optionalCostOf(final StackItemView item) {
        try {
            final String s = item.getOptionalCostString();
            return s == null || s.isBlank() ? "" : "(" + s.trim() + ")";
        } catch (final RuntimeException e) {
            return "";
        }
    }

    /**
     * Que HACE el objeto, sin repetir el nombre.
     *
     * <p>Era el fallo mas visible del panel: el nombre salia hasta tres veces
     * — en la carta, en nuestra primera linea y otra vez dentro del texto del
     * motor, que para un permanente es literalmente el nombre. Se veia
     * "Espada de la animista / Espada de la animista", y con varios objetos
     * encadenados eso es lo que hacia el panel ilegible.
     */
    private static String effectText(final StackItemView item) {
        String text = item.getText();
        if (text == null) {
            return "";
        }
        text = text.trim();
        final CardView src = item.getSourceCard();
        if (src != null) {
            final String translated = src.getCurrentState() == null
                    ? null : CardText.nameOf(src.getCurrentState());
            for (final String name : new String[] {translated, src.getName()}) {
                if (name != null && !name.isBlank() && text.startsWith(name)) {
                    text = text.substring(name.length()).trim();
                    // Y el separador que deje detras ("- ", ": ", "\u2014 ").
                    while (!text.isEmpty() && SEPARATORS.indexOf(text.charAt(0)) >= 0) {
                        text = text.substring(1).trim();
                    }
                    break;
                }
            }
        }
        return text;
    }

    private static final String SEPARATORS = "-\u2013\u2014:\u00b7";

    /** Ensenya el estado de un jugador mientras el raton este sobre su barra. */
    private void hoverDetails(final PlayerBar bar, final boolean opponent) {
        bar.setOnMouseEntered(e -> {
            final String text = bar.getDetailsText();
            if (text == null || text.isBlank()) {
                return;
            }
            playerDetails.setText(text);
            playerDetails.setVisible(true);
            detailsBelow = opponent;
            requestLayout();
        });
        bar.setOnMouseExited(e -> {
            playerDetails.setVisible(false);
            requestLayout();
        });
    }

    /** Debajo de la barra del rival, encima de la tuya: siempre hacia la mesa. */
    private boolean detailsBelow;

    /**
     * Relee el tapete elegido.
     *
     * <p>Publico porque la pantalla de personalizacion lo cambia con la mesa ya
     * montada: sin esto habria que reiniciar para verlo.
     */
    public final void applyPlaymat() {
        javafx.scene.image.Image image = null;
        try {
            image = forge.neo.look.NeoLook.currentPlaymat().image();
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido poner el tapete: " + e);
        }
        playmat.setImage(image);
        final boolean on = image != null;
        playmat.setVisible(on);
        playmatVeil.setVisible(on);
    }

    public void setPhase(final PhaseType phase) {
        phaseRail.setCurrent(phase);
    }

    public void setPrompt(final String text) {
        actionBar.setPrompt(text);
        lastPrompt = text;
    }

    /** El boton que abre el registro. */
    public Button getLogButton() {
        return logButton;
    }

    /** Levanta el registro de partida. */
    public void showGameLog(final forge.game.GameLog log, final PlayerView me) {
        final GameLogView view = new GameLogView(menuOverlay::hide);
        view.update(log, me);
        logButton.pseudoClassStateChanged(UNREAD, false);
        menuOverlay.setOnBackgroundClick(menuOverlay::hide);
        menuOverlay.show(view);
        gesture(Gesture.LOG_OPEN);
    }

    /** Marca que hay novedades sin leer en el registro. */
    public void markLogUnread() {
        logButton.pseudoClassStateChanged(UNREAD, true);
    }

    private static final javafx.css.PseudoClass UNREAD =
            javafx.css.PseudoClass.getPseudoClass("unread");

    /** El aviso grande del centro de la mesa. */
    public PromptBanner getPromptBanner() {
        return promptBanner;
    }

    /** El ultimo texto que mando el motor, para poder repetirlo en el banner. */
    public String getLastPrompt() {
        return lastPrompt;
    }

    private String lastPrompt = "";

    public ActionBar getActionBar() {
        return actionBar;
    }

    public Overlay getOverlay() {
        return overlay;
    }

    /**
     * Un aviso que solo hay que leer y cerrar.
     *
     * <p>Va en la capa del menu, no en la de los dialogos del motor: esto no es
     * una pregunta con la que el motor este bloqueado, y cerrarlo no debe
     * llevarse por delante nada que este esperando.
     */
    public void showInfo(final String title, final String text) {
        final javafx.scene.control.Label heading = new javafx.scene.control.Label(
                title == null ? "" : title);
        heading.getStyleClass().add("dialog-title");

        // Los textos del motor traen los saltos de linea escapados.
        final Label body = new Label(text == null ? "" : text.replace("\\n", "\n"));
        body.getStyleClass().add("dialog-text");
        body.setWrapText(true);
        body.setMaxWidth(560);
        body.setMinHeight(Region.USE_PREF_SIZE);

        final javafx.scene.control.Button ok = new javafx.scene.control.Button(NeoText.get("banner.understood"));
        ok.getStyleClass().add("btn-primary");
        ok.setOnAction(e -> menuOverlay.hide());

        final javafx.scene.layout.HBox footer = new javafx.scene.layout.HBox(ok);
        footer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        final VBox box = new VBox(12, heading, body, footer);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(22, 26, 20, 26));
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        menuOverlay.show(box);
    }

    /**
     * Abre el visor de una zona.
     *
     * <p>Va en la capa del menu: normalmente es consultar, no contestar, y asi
     * si el motor estaba esperando algo su dialogo sigue debajo y no se pierde.
     *
     * <p>Pero las cartas de dentro <b>se pueden elegir</b> si el motor las ha
     * marcado como elegibles ({@code setSelectables}). Eso es lo que permite
     * contestar a "elige como objetivo una criatura de tu cementerio", que no
     * llega por ningun dialogo: el motor espera un click en la carta, alli donde
     * este. Se aplica igual cuando abres el cementerio tu mismo desde la pila:
     * si hay algo que elegir ahi, se puede elegir.
     *
     * <p>Y ademas se pueden <b>lanzar</b> las cartas que el motor dice que se
     * pueden lanzar desde aqui ({@code PlayerView.getFlashback()}): la aventura
     * que se fue al exilio, el flashback del cementerio. Eso no es contestar
     * una pregunta, es jugar — y por eso va con el resaltado de "accionable" y
     * no con el azul de "elige esto". Ver {@link ZoneViewer}.
     */
    public void showZone(final PlayerView owner, final forge.game.zone.ZoneType zone) {
        if (owner == null) {
            return;
        }
        final List<CardView> cards = new ArrayList<>();
        final var view = owner.getCards(zone);
        if (view != null) {
            for (final CardView cv : view) {
                cards.add(cv);
            }
        }
        menuOverlay.show(new ZoneViewer(forge.neo.match.PlayerName.of(owner), zone, cards,
                mayView, cardWidth * 1.15, selectable, playableOutside, this::pickedInZone,
                menuOverlay::hide));
        gesture(Gesture.ZONE_OPEN);
    }

    /**
     * Se ha clicado una carta dentro del visor de una zona.
     *
     * <p>Se cierra el visor antes de contestar: el motor puede pedir lo
     * siguiente inmediatamente (otro objetivo, un dialogo), y dejar el visor
     * encima taparia esa peticion. Es el mismo principio que ya costo un
     * {@code toFront()}: lo que el motor te esta ensenyando manda.
     */
    private void pickedInZone(final CardView card) {
        menuOverlay.hide();
        cardClickedDirect(card);
    }

    /** Si el motor ha marcado esta carta como elegible ahora mismo. */
    private java.util.function.Predicate<CardView> selectable = c -> false;

    /** Si esta carta se puede LANZAR desde la zona en la que esta. */
    private java.util.function.Predicate<CardView> playableOutside = c -> false;

    /**
     * Que cartas se pueden lanzar desde fuera de la mano y del campo.
     *
     * <p>Lo contesta el motor entero: {@code PlayerView.getFlashback()}. Aqui
     * no se deduce nada — ni por tipo de carta, ni por palabra clave, ni por
     * zona — porque la lista de formas de lanzar algo desde el cementerio o el
     * exilio no para de crecer y cualquier copia nuestra nacería incompleta.
     */
    public void setPlayableOutside(final Predicate<CardView> test) {
        this.playableOutside = test == null ? c -> false : test;
    }

    /** Si esta carta se puede lanzar desde donde esta. Para las pilas. */
    public boolean isPlayableOutside(final CardView card) {
        return card != null && playableOutside.test(card);
    }

    /** Cierra el visor de zona, si es lo que hay puesto. */
    public void hideZoneViewer() {
        if (menuOverlay.isShowing() && !menuOverlay.getChildren().isEmpty()
                && menuOverlay.getChildren().get(0) instanceof ZoneViewer) {
            menuOverlay.hide();
        }
    }

    /**
     * Que cartas puede ver el jugador.
     *
     * <p>Lo decide el motor ({@code IGuiGame.mayView}), no nosotros: la
     * biblioteca es informacion oculta y el visor no puede destaparla. Por
     * defecto se ve todo, que es lo correcto fuera de una partida (maquetas).
     */
    public void setCardVisibility(final Predicate<CardView> test) {
        this.mayView = test == null ? c -> true : test;
    }

    private Predicate<CardView> mayView = c -> true;

    /** La capa del menu de pausa, por encima de los dialogos del motor. */
    public Overlay getMenuOverlay() {
        return menuOverlay;
    }

    /** true si hay algo modal delante de la mesa. */
    public boolean isModalShowing() {
        return overlay.isShowing() || menuOverlay.isShowing() || zoomOverlay.isShowing();
    }

    /**
     * Ampliar una carta a pantalla casi completa, como el click derecho de Arena.
     *
     * <p>El tamano sale del alto de la mesa, no de un numero fijo: en 4K la
     * carta tiene que crecer igual que crece todo lo demas.
     */
    /** Ancho de carta para las vistas "grandes": la ampliada y el menu. */
    public double zoomCardWidth() {
        return Math.max(320, getHeight() * 0.86) / CardNode.ASPECT;
    }

    public void showZoom(final CardView card) {
        if (card == null) {
            return;
        }
        // La misma composicion que el click derecho del resto de pantallas:
        // la carta grande y, al lado, lo que la imagen NO cuenta (P/T de ahora
        // contra la impresa, contadores, danyo y lo que lleva encima). El click
        // sobre la propia carta tambien cierra: nadie quiere buscar una X.
        zoomOverlay.show(CardZoom.compose(card, zoomCardWidth(),
                Math.max(getWidth(), 640), this::hideZoom));
        gesture(Gesture.ZOOM_CARD);
    }

    public void hideZoom() {
        zoomOverlay.hide();
    }

    public boolean isZoomShowing() {
        return zoomOverlay.isShowing();
    }

    /**
     * Anuncia de quien es el turno, en grande y en el centro.
     *
     * <p>Lo llama {@code NeoMatchUI} cuando el motor avisa de cambio de turno.
     */
    public void announceTurn(final boolean yours, final String who, final int turn) {
        turnBanner.announce(yours, who, turn);
        requestLayout();
    }

    public TurnBanner getTurnBanner() {
        return turnBanner;
    }

    /** El rail de fases, que ademas dice de quien es el turno. */
    public PhaseRail getPhaseRail() {
        return phaseRail;
    }

    /** El panel de detalle, para poder ajustar el zoom de su texto. */
    public CardDetailPanel getDetailPanel() {
        return detail;
    }

    /**
     * Apila o desapila las fichas identicas.
     *
     * <p>Se desapilan mientras el motor espera que el jugador senyale cartas
     * concretas: una pila es un solo nodo y siempre elegiria la misma ficha.
     */
    public void setGroupingEnabled(final boolean on) {
        opponentField.setGroupingEnabled(on);
        selfField.setGroupingEnabled(on);
        commandZone.setGroupingEnabled(on);
    }

    /**
     * Marca que cartas se pueden elegir ahora mismo.
     *
     * <p>Es el resaltado azul: el motor dice cuales valen y el jugador solo
     * tiene que clicar una. Sin esto habria que adivinar.
     */
    public void setSelectable(final Predicate<CardView> test) {
        // Se guarda ademas del reparto por nodos: las cartas que no estan
        // pintadas en la mesa (cementerio, exilio, mando) tambien tienen que
        // poder marcarse cuando se abre su visor.
        this.selectable = test == null ? c -> false : test;
        for (final CardNode n : everyNode()) {
            final CardView cv = n.getCard();
            n.setSelectable(cv != null && test != null && test.test(cv));
        }
    }

    /**
     * El resaltado <b>debil</b> que manda el motor: "esto lo puedes usar".
     *
     * <p>La funcion devuelve la <b>fuerza</b> de cada carta: 1 la puedes usar,
     * 2 ademas es una de las que taparia el boton "Auto". Ver
     * {@code CardNode.setActionable}.
     */
    public void setActionable(final java.util.function.ToIntFunction<CardView> strength) {
        this.actionable = strength;
        for (final CardNode n : everyNode()) {
            final CardView cv = n.getCard();
            n.setActionable(cv == null || strength == null ? 0 : strength.applyAsInt(cv));
        }
    }

    private java.util.function.ToIntFunction<CardView> actionable;

    /** Que hacer cuando se clica el retrato de un jugador. */
    public void setOnPlayerClicked(final Consumer<PlayerView> handler) {
        opponentBar.setOnPlayerClicked(handler);
        selfBar.setOnPlayerClicked(handler);
    }

    /** Resalta los retratos cuando hay que elegir jugador. */
    public void setPlayersSelectable(final boolean on) {
        opponentBar.setSelectable(on);
        selfBar.setSelectable(on);
    }

    /** Que hacer cuando el jugador clica una carta. */
    public void setOnCardClicked(final Consumer<CardView> handler) {
        this.onCardClicked = handler;
    }

    private Consumer<CardView> onCardClicked;

    private void cardClicked(final CardNode n) {
        if (onCardClicked != null && n.getCard() != null) {
            onCardClicked.accept(n.getCard());
        }
    }

    /** Como un click en la mesa, pero sobre una carta que no esta pintada ahi. */
    private void cardClickedDirect(final CardView card) {
        if (onCardClicked != null && card != null) {
            onCardClicked.accept(card);
        }
    }

    public void setOnCardHover(final Consumer<CardNode> handler) {
        this.onCardHover = handler;
    }

    /** Refresca todo lo pintado. Se llama cuando llegan imagenes nuevas. */
    public void refreshAll() {
        // Tambien lo que haya en los dialogos: una carta ampliada o el reparto
        // de dano se quedaban con el marcador hasta cerrarlos y volver a abrir.
        CardNode.refreshAllIn(overlay);
        CardNode.refreshAllIn(menuOverlay);
        CardNode.refreshAllIn(zoomOverlay);
        // Y el cartel central, que se quedaba fuera del barrido: su carta es un
        // CardNode reutilizado, asi que cuando por fin llegaba su imagen no
        // habia nadie que se lo dijera. El arte solo aparecia al cambiar de
        // carta — o sea que el cartel iba SIEMPRE una carta por detras.
        CardNode.refreshAllIn(promptBanner);
        opponentField.refreshAll();
        selfField.refreshAll();
        commandZone.refreshAll();
        for (final CardNode n : extraNodes) {
            n.refresh();
        }
        detail.refresh();
    }

    private List<CardNode> everyNode() {
        final List<CardNode> all = new ArrayList<>(extraNodes);
        all.addAll(opponentField.nodes());
        all.addAll(selfField.nodes());
        all.addAll(commandZone.nodes());
        return all;
    }

    /**
     * Si hay en la mesa alguna criatura <b>preparada</b>. Solo para capturas.
     *
     * <p>Existe porque ese estado no se puede pedir: aparece cuando la partida
     * llega a la fase que lo dispara, y con el piloto automatico eso cae en un
     * momento cualquiera. Sin poder esperarlo, la captura sale con lo que
     * hubiera — un dialogo, otro turno — y no prueba nada.
     */
    public boolean hasPreparedCard() {
        return preparedCardNode() != null;
    }

    /** La primera criatura preparada de la mesa, para clicarla en una prueba. */
    public CardNode preparedCardNode() {
        for (final CardNode n : everyNode()) {
            if (n.getCard() != null && n.getCard().getPreparedSpell() != null) {
                return n;
            }
        }
        return null;
    }

    public int nodeCount() {
        return everyNode().size();
    }

    public long nodesWithArt() {
        return everyNode().stream().filter(CardNode::hasArt).count();
    }


    // ---------------------------------------------------------------
    // Indice de cartas: CardView -> nodo en pantalla
    // ---------------------------------------------------------------

    /**
     * Donde esta pintada cada carta.
     *
     * <p>Lo necesitan las flechas de combate y el arrastre: los dos trabajan
     * con {@code CardView} y tienen que llegar a pixeles. Se reconstruye cuando
     * cambian las zonas, no en cada consulta: se consulta 60 veces por segundo
     * mientras hay flechas en pantalla.
     */
    private final Map<Integer, CardNode> byCardId = new HashMap<>();

    /**
     * Respaldo por identidad para las vistas SIN id valido.
     *
     * <p>{@code CardView.getCardForUi()} (maquetas, editor de mazos) devuelve
     * vistas que comparten un id negativo: indexarlas por id las colapsaria
     * todas en una. Ahi la unica clave fiable es el propio objeto.
     */
    private final java.util.IdentityHashMap<CardView, CardNode> byCardRef =
            new java.util.IdentityHashMap<>();

    private void reindex() {
        byCardId.clear();
        byCardRef.clear();
        for (final CardNode n : everyNode()) {
            final CardView cv = n.getCard();
            if (cv == null) {
                continue;
            }
            byCardRef.put(cv, n);
            if (cv.getId() >= 0) {
                byCardId.put(cv.getId(), n);
            }
        }
    }

    /**
     * Destella la carta a la que acaban de pegar.
     *
     * <p>Lo pide {@code NeoMatchUI} al recibir {@code GameEventCardDamaged}. Si
     * esa carta no esta en la mesa — dano a algo del cementerio, a una carta que
     * ya se ha ido — no pasa nada: no hay nada que ensenyar.
     */
    public void flashHit(final CardView card) {
        final CardNode node = nodeOf(card);
        if (node != null) {
            Anim.hit(node);
        }
    }

    /** Da un golpe de escala a una carta: "este numero acaba de cambiar". */
    public void bumpCard(final CardView card) {
        final CardNode node = nodeOf(card);
        if (node != null) {
            Anim.bump(node);
        }
    }

    private CardNode nodeOf(final CardView card) {
        if (card == null) {
            return null;
        }
        final CardNode byId = card.getId() >= 0 ? byCardId.get(card.getId()) : null;
        return byId != null ? byId : byCardRef.get(card);
    }

    /**
     * Rectangulo de una entidad del motor en coordenadas de la mesa.
     *
     * <p>Es lo unico que necesita {@link CombatOverlay} para dibujar. Un
     * jugador se representa con su barra: es lo que hace Arena, donde la flecha
     * del atacante apunta al retrato del rival.
     */
    private Bounds boundsOf(final Object entity) {
        Node node = null;
        if (entity instanceof CardView cv) {
            node = nodeOf(cv);
        } else if (entity instanceof PlayerView pv) {
            if (pv.equals(opponentBar.getPlayer())) {
                node = opponentBar.getPortrait();
            } else if (pv.equals(selfBar.getPlayer())) {
                node = selfBar.getPortrait();
            }
        }
        if (node == null || node.getScene() == null) {
            return null;
        }
        return sceneToLocal(node.localToScene(node.getBoundsInLocal()));
    }

    // ---------------------------------------------------------------
    // Flechas
    // ---------------------------------------------------------------

    /**
     * Pinta las flechas de combate y de objetivo.
     *
     * @param links tripletas {origen, destino, CombatOverlay.Kind}
     */
    public void setCombatLinks(final List<Object[]> links) {
        combatOverlay.setLinks(links);
    }

    /** Cuantas flechas hay ahora mismo. Solo para verificar con capturas. */
    public int combatLinkCount() {
        return combatOverlay.linkCount();
    }

    /** Los nodos de la mano, en orden. */
    public List<CardNode> handNodes() {
        return new ArrayList<>(extraNodes);
    }

    /** Los nodos del campo del rival. */
    public List<CardNode> opponentFieldNodes() {
        return opponentField.nodes();
    }

    /** Los nodos del campo propio. */
    public List<CardNode> selfFieldNodes() {
        return selfField.nodes();
    }

    /** Cuantas entradas hay en el stack. Solo para verificar con capturas. */
    public int stackSize() {
        return stackSize;
    }

    private int stackSize;

    /** Cuantas flechas de combate (ataque/bloqueo) hay ahora mismo. */
    public int fightLinkCount() {
        return combatOverlay.fightLinkCount();
    }

    /** Repinta las flechas ya. Solo para verificar con capturas. */
    public void redrawCombat() {
        combatOverlay.redrawNow();
    }

    // ---------------------------------------------------------------
    // Arrastrar y soltar
    // ---------------------------------------------------------------

    /** Que hacer cuando se suelta una carta encima de algo. */
    public interface DropHandler {
        /**
         * @param source   la carta que se arrastro
         * @param fromHand true si salio de la mano (soltar = jugarla)
         * @param target   CardView o PlayerView debajo del raton, o null
         */
        void onDrop(CardView source, boolean fromHand, Object target);
    }

    private DropHandler onDrop;
    private CardNode dragNode;
    private boolean dragFromHand;
    private boolean dragActive;

    private double pressX;
    private double pressY;
    private CardNode lastDropNode;
    private boolean swallowNextClick;

    /** Distancia a partir de la cual un click se considera arrastre. */
    private static final double DRAG_SLOP = 9;

    /** Traza cada soltada. Se enciende con -Dneo.drag.debug=true */
    private static final boolean DRAG_DEBUG = Boolean.getBoolean("neo.drag.debug");

    public void setOnCardDropped(final DropHandler handler) {
        this.onDrop = handler;
    }

    /** Si ya hay quien atienda las soltadas. Lo usa la prueba de arrastre. */
    public boolean hasDropHandler() {
        return onDrop != null;
    }

    /**
     * Arrastrar como verbo principal, igual que en Arena.
     *
     * <p>Se resuelve con filtros de raton sobre la mesa entera en vez de con la
     * API de drag-and-drop de JavaFX: esa API arranca un gesto del sistema
     * operativo, obliga a serializar el contenido y no deja pintar la flecha
     * curva mientras se arrastra, que es justo lo que cuenta la accion.
     *
     * <p>Mientras no se supere {@link #DRAG_SLOP} no se consume nada, asi que
     * un click sigue siendo un click. Es importante: el click tiene que seguir
     * funcionando siempre como alternativa al arrastre.
     */
    private void installDragGestures() {
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            // Clicar en cualquier sitio aparta el cartel central. NO se consume
            // el evento: el click sigue su camino y hace lo que fuera a hacer.
            if (promptBanner.isVisible() && !isInside(e.getTarget(), promptBanner)) {
                promptBanner.fireDismiss();
            }

            // Click derecho: ampliar la carta para poder leerla. No entra en
            // conflicto con el motor porque nunca le mandamos el boton pulsado
            // (siempre pasamos triggerEvent = null).
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                final CardNode zoomed = cardNodeAt(e.getTarget());
                CardView card = zoomed == null ? null : zoomed.getCard();
                if (card == null && isInside(e.getTarget(), detail)) {
                    // El panel de detalle no pinta un CardNode sino la imagen
                    // suelta, asi que hay que preguntarle a el que carta lleva.
                    card = detail.getCurrent();
                }
                if (card != null) {
                    showZoom(card);
                } else if (zoomOverlay.isShowing()) {
                    hideZoom();
                }
                e.consume();
                return;
            }
            dragNode = draggableAt(e.getTarget());
            dragFromHand = dragNode != null && extraNodes.contains(dragNode);
            dragActive = false;
            // Si el arrastre anterior acabo en el vacio, JavaFX no manda el
            // click que ibamos a tragarnos y la marca se queda puesta: sin
            // limpiarla aqui, el siguiente click legitimo se pierde.
            swallowNextClick = false;
            pressX = e.getSceneX();
            pressY = e.getSceneY();
        });

        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (dragNode == null || isModalShowing()) {
                return;
            }
            if (!dragActive) {
                if (Math.hypot(e.getSceneX() - pressX, e.getSceneY() - pressY) < DRAG_SLOP) {
                    return;
                }
                dragActive = true;
                dragNode.setDragging(true);
                combatOverlay.startDrag(dragNode.getCard());
            }
            final Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
            final boolean cancelling = dragFromHand && !isOverBoard(e.getSceneX(), e.getSceneY());
            final Object target = cancelling ? null : entityAt(e.getSceneX(), e.getSceneY());
            markDropTarget(target);
            // Fuera de la mesa la flecha se apaga: es la senyal de que soltar
            // ahi no va a jugar nada.
            combatOverlay.moveDrag(p.getX(), p.getY(), target != null);
            e.consume();
        });

        addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!dragActive) {
                dragNode = null;
                return;
            }
            final Object target = entityAt(e.getSceneX(), e.getSceneY());
            final CardView source = dragNode.getCard();
            final boolean fromHand = dragFromHand;
            dragNode.setDragging(false);
            markDropTarget(null);
            combatOverlay.endDrag();
            dragNode = null;
            dragActive = false;
            swallowNextClick = true;
            // Soltar una carta de la mano fuera de la mesa es arrepentirse.
            //
            // Antes se llamaba a onDrop pasara lo que pasara, y para una carta
            // de la mano soltar significa jugarla: cogias una tierra, te lo
            // pensabas mejor, la devolvias hacia la mano... y se jugaba igual
            // en cuanto el soltar no caia EXACTAMENTE sobre el rectangulo de
            // la mano. Y jugar una tierra NO se puede deshacer (Forge solo
            // deshace lo que pasa por el stack, y una tierra no pasa), asi que
            // el error era definitivo. Ahora hace falta lo contrario: que el
            // drop caiga de verdad sobre la mesa (ver isOverBoard) para que
            // cuente como jugarla. Cualquier otro sitio — la mano, o el hueco
            // de en medio, o mas alla — cancela.
            final boolean cancelled = fromHand && !isOverBoard(e.getSceneX(), e.getSceneY());

            if (DRAG_DEBUG) {
                System.out.printf("[drag] %s (mano=%s) -> %s%s%n",
                        source, fromHand, target, cancelled ? " [CANCELADO]" : "");
            }
            if (!cancelled && onDrop != null && source != null) {
                onDrop.onDrop(source, fromHand, target);
            }
            e.consume();
        });

        // Tras un arrastre, JavaFX manda ademas un click en el nodo de origen.
        // Sin tragarselo, soltar una carta la jugaria dos veces.
        addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (swallowNextClick) {
                swallowNextClick = false;
                e.consume();
            }
        });
    }

    /**
     * La carta pulsada, si es una que se puede arrastrar.
     *
     * <p>Se descartan las que estan en la columna lateral (la carta grande del
     * panel de detalle es una vista previa, no una carta de la partida) y las
     * de un dialogo modal, donde el gesto que vale es el click.
     */
    /**
     * Si ese punto de la pantalla cae sobre la mesa (los dos campos y la
     * linea de combate — lo que ya delimita {@link #board} para el zoom).
     *
     * <p>Es la zona donde soltar una carta de la mano SI la juega. Antes solo
     * se cancelaba soltando EXACTAMENTE sobre la mano; un arrastre hacia
     * atras que se quedara corto — sin llegar a caer dentro del rectangulo de
     * la mano, pero sin llegar a la mesa tampoco — jugaba la carta igual.
     * Reportado jugando: <i>"si la arrastro para atras que no se juegue,
     * ahora se juega apuntes donde apuntes y da a missclick"</i>. Con jugar
     * una tierra irreversible (principio 6 de las notas de diseño), lo seguro es
     * exigir que el gesto sea inequivoco: solo cuenta un drop que caiga DE
     * VERDAD sobre la mesa, y cualquier otro sitio cancela.
     */
    private boolean isOverBoard(final double sceneX, final double sceneY) {
        final Point2D p = board.sceneToLocal(sceneX, sceneY);
        return board.getBoundsInLocal().contains(p);
    }

    /** true si el objetivo del evento cuelga de ese nodo. */
    private static boolean isInside(final Object target, final Node ancestor) {
        Node n = target instanceof Node ? (Node) target : null;
        while (n != null) {
            if (n == ancestor) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    /** Sube por el arbol hasta encontrar la carta que hay bajo el raton. */
    private static CardNode cardNodeAt(final Object target) {
        Node n = target instanceof Node ? (Node) target : null;
        while (n != null) {
            if (n instanceof CardNode cn) {
                return cn;
            }
            n = n.getParent();
        }
        return null;
    }

    private CardNode draggableAt(final Object target) {
        Node n = target instanceof Node ? (Node) target : null;
        CardNode found = null;
        while (n != null) {
            if (found == null && n instanceof CardNode cn) {
                found = cn;
            }
            if (n == side || n == overlay || n == menuOverlay) {
                return null;
            }
            n = n.getParent();
        }
        return found;
    }

    /**
     * Que hay debajo del raton: una carta o un jugador.
     *
     * <p>Se busca por geometria y no con {@code pick} porque la carta que se
     * arrastra va por delante de todo y taparia siempre al destino.
     */
    private Object entityAt(final double sceneX, final double sceneY) {
        CardNode best = null;
        for (final CardNode n : everyNode()) {
            if (n == dragNode || n.getCard() == null || n.getScene() == null) {
                continue;
            }
            if (n.localToScene(n.getBoundsInLocal()).contains(sceneX, sceneY)) {
                // La ultima gana: es la que esta pintada mas arriba.
                best = n;
            }
        }
        if (best != null) {
            return best.getCard();
        }
        if (opponentBar.getPlayer() != null
                && opponentBar.localToScene(opponentBar.getBoundsInLocal()).contains(sceneX, sceneY)) {
            return opponentBar.getPlayer();
        }
        if (selfBar.getPlayer() != null
                && selfBar.localToScene(selfBar.getBoundsInLocal()).contains(sceneX, sceneY)) {
            return selfBar.getPlayer();
        }
        return null;
    }

    /** Marca la carta bajo el raton para que se vea donde va a caer. */
    private void markDropTarget(final Object target) {
        final CardNode node = target instanceof CardView cv ? nodeOf(cv) : null;
        if (node == lastDropNode) {
            return;
        }
        if (lastDropNode != null) {
            lastDropNode.setDropTarget(false);
        }
        lastDropNode = node;
        if (node != null) {
            node.setDropTarget(true);
        }
    }

    // ---------------------------------------------------------------
    // Resaltados del motor
    // ---------------------------------------------------------------

    /**
     * Resaltado que pide el motor con {@code setHighlighted}.
     *
     * <p>Durante el combate es lo que dice sobre que estas trabajando: el
     * defensor elegido mientras declaras atacantes, el atacante elegido
     * mientras declaras bloqueadores.
     */
    public void setHighlighted(final Predicate<Object> test) {
        for (final CardNode n : everyNode()) {
            final CardView cv = n.getCard();
            n.setHighlighted(cv != null && test != null && test.test(cv));
        }
        opponentBar.setHighlighted(test != null && opponentBar.getPlayer() != null
                && test.test(opponentBar.getPlayer()));
        selfBar.setHighlighted(test != null && selfBar.getPlayer() != null
                && test.test(selfBar.getPlayer()));
    }

    // ---------------------------------------------------------------
    // Mana y paradas de fase
    // ---------------------------------------------------------------

    /**
     * Enciende o apaga la reserva de mana del jugador.
     *
     * <p>El motor la abre al empezar un pago y la cierra al acabar. Verla
     * destacada justo cuando importa es lo que hace que se entienda que esos
     * pips son gastables, no un adorno.
     */
    public void setManaPoolActive(final PlayerView player, final boolean on) {
        if (player == null) {
            return;
        }
        if (player.equals(selfBar.getPlayer())) {
            selfBar.setManaPoolActive(on);
        } else if (player.equals(opponentBar.getPlayer())) {
            opponentBar.setManaPoolActive(on);
        }
    }

    /** Click en un pip de mana flotante: gastarlo. */
    public void setOnManaClicked(final Consumer<Byte> handler) {
        selfBar.setOnManaClicked(handler);
    }

    /** Click en una fase del rail: poner o quitar la parada. */
    public void setOnPhaseToggled(final Consumer<PhaseType> handler) {
        // Se envuelve en vez de enganchar el rail por su cuenta: asi el aviso
        // sale exactamente cuando la parada se pone de verdad, y quien monta el
        // tutorial no tiene que acordarse de nada.
        phaseRail.setOnToggle(phase -> {
            gesture(Gesture.PHASE_STOP);
            if (handler != null) {
                handler.accept(phase);
            }
        });
    }

    public void setPhaseStops(final Predicate<PhaseType> isStop) {
        phaseRail.setStops(isStop);
    }

    /** Criaturas pintadas en el campo propio. Solo lo usa el modo maqueta. */
    public List<CardView> selfCreatureViews() {
        return creatureViews(selfField);
    }

    /** Criaturas pintadas en el campo del rival. Solo lo usa el modo maqueta. */
    public List<CardView> opponentCreatureViews() {
        return creatureViews(opponentField);
    }

    private static List<CardView> creatureViews(final PlayerField field) {
        final List<CardView> out = new ArrayList<>();
        for (final CardNode n : field.nodes()) {
            final CardView cv = n.getCard();
            if (cv != null && cv.getCurrentState() != null && cv.getCurrentState().isCreature()) {
                out.add(cv);
            }
        }
        return out;
    }

    /** Vuelca las alturas reales de cada zona. Solo para depurar el layout. */
    public void dumpLayout() {
        System.out.printf("[layout] table=%.0fx%.0f%n", getWidth(), getHeight());
        dump("oppBar", opponentBar);
        dump("oppField", opponentField);
        dump("selfField", selfField);
        dump("selfBar", selfBar);
        dump("hand", hand);
        // La columna de la derecha. Util cuando algo de ahi sale apretado: un
        // VBox al que se le pide mas de lo que hay encoge a TODOS, y el alto
        // que acaba dando no se adivina leyendo el codigo.
        dump("side", side);
        dump("  phaseRail", phaseRail);
        dump("  stackBox", stackBox);
        dump("  actionBar", actionBar);
    }

    private static void dump(final String label, final Region r) {
        System.out.printf("[layout] %-10s y=%.0f h=%.0f%n",
                label, r.getLayoutY(), r.getHeight());
    }
}
