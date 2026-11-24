package forge.neo.ui;

import forge.game.card.CardView;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RoguePlaneboundType;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.effect.DescensionLevel;
import forge.gamemodes.rogue.effect.EndlessMode;
import forge.gamemodes.rogue.effect.RogueEffect;
import forge.gamemodes.rogue.effect.RogueEffectComposite;
import forge.gamemodes.rogue.path.NodeBazaar;
import forge.gamemodes.rogue.path.NodeChest;
import forge.gamemodes.rogue.path.NodeEvent;
import forge.gamemodes.rogue.path.NodePlanebound;
import forge.gamemodes.rogue.path.NodeSanctum;
import forge.gamemodes.rogue.path.RoguePath;
import forge.gamemodes.rogue.path.RoguePathNode;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.card.KeywordIcon;
import forge.neo.look.NeoLook;
import forge.neo.rogue.RogueMapModel;
import forge.neo.rogue.RoguePathLayout;
import forge.neo.rogue.RogueSession;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.animation.Transition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.util.Duration;

/**
 * El mapa de una run de Rogue Commander: {@code VSubmenuRogueMap} con el
 * camino de {@code PathVisualizerPanel} y sus nodos ({@code NodePanel} y
 * familia) de forge-gui-desktop, <b>copiados lo mas fielmente posible</b>
 * (Ana, 24-11-2026: "en Ascenso hicimos lo que nos dio la gana porque es
 * nuestro, pero aqui quiero ser fiel a lo suyo").
 *
 * <p>Lo que se pinta lo calcula {@link RogueMapModel} (boca abajo, revelado) y
 * donde va cada nodo {@link RoguePathLayout}, los dos como Forge. Esta pantalla
 * dibuja lo que Forge dibuja: cada Planebound como un panel de 270x260 con su
 * carta de plano en horizontal (250x180), el nombre y la vida debajo; el resto
 * como un circulo de 75 con su icono; marcos gris / verde con su ✔ / oro, y
 * flechas de 4 px. Los planos que se ven por primera vez se dan la vuelta uno
 * tras otro con el sonido de girar carta.
 *
 * <p>Lo NUESTRO, y por que ({@code NEOFORGE}):
 * <ul>
 *   <li>Las medidas de Forge son pixeles fijos; aqui son de 1080p y crecen con
 *       la pantalla ({@code UiScale}). Y como en Forge, si una fila no cabe a
 *       lo ancho, el mapa se desplaza de lado (antes se cortaba: principio 5).</li>
 *   <li>Forge pone el marco dorado y la mano al pasar el raton por CUALQUIER
 *       nodo, aunque el clic no haga nada. Aqui solo donde se puede elegir
 *       (principio 1: un control que no hace lo que parece).</li>
 *   <li>Forge amplia el plano con la rueda hacia arriba; aqui con el clic
 *       derecho, que es el gesto de leer una carta en todo el juego (la rueda
 *       ya mueve el mapa).</li>
 * </ul>
 */
public final class RogueMapScreen extends RogueScreen {

    /** Lo que hace cada boton (lo pone {@code NeoAppRogue}). */
    public interface Actions {
        void enter();

        void deck();

        void reroll();

        void saveAndQuit();

        void abandon();
    }

    // NodePanel / NodeCircularPanel / NodePlaneboundPanel, en pixeles de 1080p.
    private static final double PANEL_WIDTH = 270;
    private static final double PANEL_HEIGHT = 260;
    private static final double CIRCULAR_SIZE = 75;
    private static final double CARD_WIDTH = 250;
    private static final double CARD_HEIGHT = 180;
    private static final int FACE_DOWN_PLANE_SLEEVE_INDEX = 18;
    private static final int PATH_LINE_WIDTH = 4;

    private static final Color GOLD = Color.rgb(255, 215, 0);
    private static final Color GREEN = Color.rgb(0, 200, 0);
    private static final Color GREY = Color.rgb(100, 100, 100);

    private final RogueRun run;
    private final Actions actions;
    private final Pane board = new Pane();
    private final Group edges = new Group();
    private final ScrollPane scroll = new ScrollPane(board);
    private final Button enter = new Button();
    private final Button deck = new Button(NeoText.get("rogue.map.deck"));
    private final Button reroll = new Button();
    private final Button quit = new Button(NeoText.get("rogue.map.saveQuit"));
    private final Button abandon = new Button(NeoText.get("rogue.map.abandon"));
    private boolean busy;
    private final BorderPane layout = new BorderPane();
    /** Las vueltas de carta pendientes: se paran al salir de la escena (principio 11). */
    private final List<Animation> reveals = new ArrayList<>();

    public RogueMapScreen(final RogueRun run, final Actions actions) {
        this.run = run;
        this.actions = actions;
        getStyleClass().add("rogue-map");

        board.getStyleClass().add("rogue-board");
        edges.setMouseTransparent(true);
        scroll.setFitToWidth(false);
        scroll.getStyleClass().add("rogue-map-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        enter.getStyleClass().addAll("btn-primary", "btn-play");
        enter.setOnAction(e -> actions.enter());
        deck.getStyleClass().add("btn-secondary");
        deck.setOnAction(e -> actions.deck());
        reroll.getStyleClass().add("btn-secondary");
        reroll.setOnAction(e -> actions.reroll());
        quit.getStyleClass().add("btn-secondary");
        quit.setOnAction(e -> actions.saveAndQuit());
        abandon.getStyleClass().addAll("btn-secondary", "btn-danger");
        abandon.setOnAction(e -> actions.abandon());

        layout.setTop(statusBar());
        layout.setCenter(scroll);
        layout.setBottom(bottomBar());
        setContent(layout);
        // Una sola vez: al cambiar el ancho se vuelve a colocar lo que haya.
        scroll.viewportBoundsProperty().addListener((o, a, b) -> {
            if (a == null || Math.abs(a.getWidth() - b.getWidth()) > 0.5) {
                place();
            }
        });
        sceneProperty().addListener((o, was, is) -> {
            if (is == null) {
                stopReveals();
            }
        });
        refresh();
    }

    /** Solo pruebas: abre el mazo (ver -Dneo.rogue.mock=deck). */
    public void pressDeck() {
        deck.fire();
    }

    /** Solo pruebas: pulsa Entrar (ver -Dneo.rogue.mock=enter). */
    public void pressEnter() {
        enter.fire();
    }

    /** Mientras la logica trabaja (un nodo, un duelo) no se puede pulsar nada. */
    public void setBusy(final boolean on) {
        busy = on;
        enter.setDisable(on || run.getCurrentNode() == null);
        deck.setDisable(on);
        reroll.setDisable(on || reroll.isDisable());
        quit.setDisable(on);
        abandon.setDisable(on);
        if (nodes != null) {
            for (final NodeView v : nodes.values()) {
                v.paintFrame();
            }
        }
    }

    // ------------------------------------------------------------------

    private Region statusBar() {
        final VBox bar = new VBox(6);
        bar.getStyleClass().add("rogue-status");
        bar.setPadding(new Insets(12, 22, 10, 22));
        final Label name = new Label(run.getCurrentCommanderName());
        name.getStyleClass().add("rogue-commander-name");
        final HBox stats = new HBox(18,
            stat("rogue-life", NeoText.get("rogue.life", run.getCurrentLife(), run.getMaxLife())),
            stat("rogue-gold", NeoText.get("rogue.gold", run.getCurrentGold())),
            stat("rogue-echo", NeoText.get("rogue.echoes", RogueMetaProgress.getInstance().getTotalEchoes())),
            stat("rogue-credits", NeoText.get("rogue.credits", run.getRemovalCredits())));
        stats.setAlignment(Pos.CENTER_LEFT);
        if (run.getDescensionLevel() > 0) {
            final Label d = pill(NeoText.get("rogue.descension", run.getDescensionLevel()), "rogue-pill-descension");
            final StringBuilder tip = new StringBuilder();
            for (int l = 1; l <= run.getDescensionLevel(); l++) {
                final DescensionLevel dl = DescensionLevel.forLevel(l);
                if (dl != null) {
                    tip.append(l).append(". ").append(dl.name).append(": ").append(dl.description).append('\n');
                }
            }
            Tooltip.install(d, new Tooltip(RogueDialogs.clean(tip.toString().trim())));
            stats.getChildren().add(d);
        }
        if (run.getEndlessLevel() > 0) {
            final Label en = pill(NeoText.get("rogue.endless", run.getEndlessLevel()), "rogue-pill-endless");
            Tooltip.install(en, new Tooltip(RogueDialogs.clean(EndlessMode.getModifiersDescription(run.getEndlessLevel()))));
            stats.getChildren().add(en);
        }
        final HBox top = new HBox(16, name, stats);
        top.setAlignment(Pos.CENTER_LEFT);

        // Lo que llevas puesto: rasgos, heridas, bendiciones... (menos la
        // Descension y el modo infinito, que ya tienen su pastilla).
        final FlowPane effects = new FlowPane(6, 6);
        for (final RogueEffect effect : RogueEffectComposite.getAllEffects(run)) {
            if (effect instanceof DescensionLevel || effect instanceof EndlessMode) {
                continue;
            }
            final Label p = pill(RogueDialogs.clean(effect.getUIDisplayText()), "rogue-pill-effect");
            Tooltip.install(p, new Tooltip(RogueDialogs.clean(effect.getActiveDescription(run))));
            effects.getChildren().add(p);
        }
        if (!run.getCarryCards().isEmpty()) {
            final StringBuilder names = new StringBuilder();
            for (final RogueRun.CarryCard c : run.getCarryCards()) {
                names.append(c.cardName()).append('\n');
            }
            final Label carry = pill(NeoText.get("rogue.carry", run.getCarryCards().size()), "rogue-pill-carry");
            Tooltip.install(carry, new Tooltip(names.toString().trim()));
            effects.getChildren().add(carry);
        }
        bar.getChildren().add(top);
        if (!effects.getChildren().isEmpty()) {
            bar.getChildren().add(effects);
        }
        return bar;
    }

    private static Label stat(final String style, final String text) {
        final Label l = new Label(text);
        l.getStyleClass().addAll("rogue-stat", style);
        return l;
    }

    private static Label pill(final String text, final String style) {
        final Label l = new Label(text);
        l.getStyleClass().addAll("rogue-pill", style);
        return l;
    }

    private Region bottomBar() {
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        // Principio 12: lo destructivo a la izquierda; volver justo a la
        // izquierda de la accion principal.
        final HBox bar = new HBox(10, abandon, deck, gap, reroll, quit, enter);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("rogue-bottom");
        bar.setPadding(new Insets(10, 22, 14, 22));
        return bar;
    }

    // ------------------------------------------------------------------
    // El camino (PathVisualizerPanel)
    // ------------------------------------------------------------------

    private boolean placed;
    private RogueMapModel model;
    private Map<Integer, NodeView> nodes;

    /**
     * {@code updatePath}: vuelve a montar el mapa con lo que hay ahora en la
     * run. Elegir otro nodo de la fila NO pasa por aqui (ver {@link #select}),
     * igual que en Forge: se rehacian todas las cartas por un clic y se
     * cortaba la vuelta de las que se estaban revelando.
     */
    public void refresh() {
        // La barra de arriba tambien: vida, oro, ecos y rasgos cambian con cada nodo.
        layout.setTop(statusBar());
        stopReveals();
        final RogueMapModel model = RogueMapModel.build(run);
        if (this.model != null && this.model.currentRow != model.currentRow) {
            placed = false;
        }
        board.getChildren().setAll(edges);
        final Map<Integer, NodeView> nodes = new HashMap<>();
        final List<NodeView> toReveal = new ArrayList<>();
        this.model = model;
        this.nodes = nodes;
        for (final List<RogueMapModel.Node> row : model.rows) {
            for (final RogueMapModel.Node n : row) {
                final NodeView view = new NodeView(n);
                nodes.put(n.index(), view);
                board.getChildren().add(view);
                if (view.flipPending) {
                    toReveal.add(view);
                }
            }
        }
        // Las de la fila de mas arriba primero, como en Forge (por indice).
        toReveal.sort(java.util.Comparator.comparingInt(v -> v.n.index()));
        animateReveals(toReveal);
        place();
        javafx.application.Platform.runLater(this::place);
        updateSelection();
    }

    /**
     * {@code setSelectedNode} + {@code updateViewWithSelection}: el marco del
     * elegido, el texto de Entrar y el boton de volver a tirar el plano.
     */
    private void updateSelection() {
        if (nodes != null) {
            for (final NodeView v : nodes.values()) {
                v.selected = v.n.index() == run.getCurrentNodeIndex();
                v.paintFrame();
            }
        }
        final RoguePathNode current = run.getCurrentNode();
        enter.setText(current == null ? NeoText.get("rogue.map.noNode")
            : RogueMapModel.enterText(current, model.pathCtx));
        enter.setDisable(busy || current == null);
        final boolean planebound = current instanceof NodePlanebound;
        final int rerolls = model.pathCtx.remainingPlaneboundRerolls;
        reroll.setVisible(planebound && rerolls > 0);
        reroll.setManaged(reroll.isVisible());
        if (reroll.isVisible()) {
            reroll.setText(NeoText.get("rogue.map.rerollPlane", rerolls));
            reroll.setDisable(busy || RogueSession.planeboundRerollCandidates(run, (NodePlanebound) current).isEmpty());
        }
    }

    /** {@code CSubmenuRogueMap.handleNodeClick}: las reglas las pone {@link RogueSession#select}. */
    private void select(final NodeView view) {
        if (busy || !view.n.selectable()) {
            return;
        }
        if (RogueSession.select(run, view.n.index())) {
            updateSelection();
        }
    }

    /** {@code doLayout} + {@code paintComponent}: coloca los nodos y dibuja las flechas. */
    private void place() {
        final RogueMapModel model = this.model;
        final Map<Integer, NodeView> nodes = this.nodes;
        final RoguePath path = run.getPath();
        if (model == null || nodes == null || path == null) {
            return;
        }
        final double s = UiScale.growth();
        final double viewport = scroll.getViewportBounds().getWidth();
        final RoguePathLayout.Result where = RoguePathLayout.layout(path, i -> {
            final NodeView v = nodes.get(i);
            return v == null ? new double[] {0, 0} : new double[] {v.w, v.h};
        }, viewport, s);
        double currentY = -1;
        for (final Map.Entry<Integer, RoguePathLayout.Rect> e : where.bounds().entrySet()) {
            final NodeView v = nodes.get(e.getKey());
            if (v == null) {
                continue;
            }
            final RoguePathLayout.Rect r = e.getValue();
            v.resizeRelocate(r.x(), r.y(), r.width(), r.height());
            if (v.n.row() == model.currentRow && (currentY < 0 || r.y() < currentY)) {
                currentY = r.y();
            }
        }
        final double width = Math.max(viewport, where.width());
        board.setMinSize(width, where.height());
        board.setPrefSize(width, where.height());

        // Las flechas: del centro de abajo de un nodo al centro de arriba del
        // siguiente, por debajo de los nodos.
        edges.getChildren().clear();
        for (int i = 0; i < path.getNodeCount(); i++) {
            final RoguePathLayout.Rect from = where.bounds().get(i);
            if (from == null) {
                continue;
            }
            for (final int to : path.getReachableNodeIndices(i)) {
                final RoguePathLayout.Rect target = where.bounds().get(to);
                if (target != null) {
                    edge(from.centerX(), from.y() + from.height(), target.centerX(), target.y(), s);
                }
            }
        }

        if (!placed && currentY >= 0) {
            // NEOFORGE: Forge no baja hasta la fila actual porque su panel vive
            // toda la run y se queda donde lo dejaste; esta pantalla se monta
            // de nuevo al volver de cada nodo.
            placed = true;
            final double total = Math.max(1, where.height() - scroll.getViewportBounds().getHeight());
            final double target = Math.max(0, currentY - UiScale.px(40));
            scroll.setVvalue(Math.min(1, target / total));
        }
    }

    /** Una linea (curva si va en diagonal) y su punta abierta, como {@code drawCurvedLine} + {@code drawArrow}. */
    private void edge(final double x1, final double y1, final double x2, final double y2, final double s) {
        final Shape line;
        if (Math.abs(x2 - x1) > 10 * s) {
            // Keep both control points halfway down so the curve leaves and enters the nodes vertically.
            final double midY = (y1 + y2) / 2;
            line = new CubicCurve(x1, y1, x1, midY, x2, midY, x2, y2);
        } else {
            line = new Line(x1, y1, x2, y2);
        }
        edges.getChildren().add(stroke(line, s));
        final double arrowSize = 10 * s;
        final double angle = Math.atan2(y2 - y1, x2 - x1);
        edges.getChildren().add(stroke(new Line(x2, y2,
            x2 - arrowSize * Math.cos(angle - Math.PI / 6), y2 - arrowSize * Math.sin(angle - Math.PI / 6)), s));
        edges.getChildren().add(stroke(new Line(x2, y2,
            x2 - arrowSize * Math.cos(angle + Math.PI / 6), y2 - arrowSize * Math.sin(angle + Math.PI / 6)), s));
    }

    private static Shape stroke(final Shape shape, final double s) {
        shape.getStyleClass().add("rogue-edge-line");
        shape.setFill(null);
        shape.setStrokeWidth(PATH_LINE_WIDTH * s);
        return shape;
    }

    // ------------------------------------------------------------------
    // La vuelta de los planos nuevos (animateReveals + CardUtil.FlipAnimation)
    // ------------------------------------------------------------------

    private void animateReveals(final List<NodeView> toReveal) {
        for (int i = 0; i < toReveal.size(); i++) {
            final NodeView view = toReveal.get(i);
            // Brief pause before first flip, then one every 150 ms.
            final PauseTransition wait = new PauseTransition(Duration.millis(300 + 150.0 * i));
            wait.setOnFinished(e -> view.flipToReveal());
            reveals.add(wait);
            wait.play();
        }
    }

    private void stopReveals() {
        for (final Animation a : reveals) {
            a.stop();
        }
        reveals.clear();
        if (nodes != null) {
            for (final NodeView v : nodes.values()) {
                v.finishFlip();
            }
        }
    }

    // ------------------------------------------------------------------
    // Un nodo (NodePanel y sus hijos)
    // ------------------------------------------------------------------

    private final class NodeView extends Pane {
        final RogueMapModel.Node n;
        final boolean plane;
        final double w;
        final double h;
        final Shape frame;
        boolean selected;
        boolean hovered;
        /** Va boca abajo hasta que le toque darse la vuelta. */
        boolean flipPending;
        private StackPane art;
        private Label name;

        NodeView(final RogueMapModel.Node n) {
            this.n = n;
            this.plane = n.node() instanceof NodePlanebound;
            final double s = UiScale.growth();
            this.w = (plane ? PANEL_WIDTH : CIRCULAR_SIZE) * s;
            this.h = (plane ? PANEL_HEIGHT : CIRCULAR_SIZE) * s;
            setMinSize(w, h);
            setPrefSize(w, h);
            setMaxSize(w, h);
            getStyleClass().add("rogue-node");
            if (plane) {
                buildPlanebound((NodePlanebound) n.node(), s);
                final Rectangle r = new Rectangle(5 * s, 5 * s, w - 10 * s, h - 10 * s);
                r.setArcWidth(10 * s);
                r.setArcHeight(10 * s);
                frame = r;
            } else {
                buildCircular(s);
                frame = new Circle(w / 2, h / 2, (w - 10 * s) / 2);
            }
            // El marco y el ✔ van ENCIMA de todo, como en NodePanel.paint.
            frame.setFill(null);
            frame.setMouseTransparent(true);
            getChildren().add(frame);
            if (n.completed()) {
                getChildren().add(checkmark(s));
            }
            setOnMouseEntered(e -> {
                hovered = true;
                paintFrame();
            });
            setOnMouseExited(e -> {
                hovered = false;
                paintFrame();
            });
            setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    select(this);
                }
            });
            paintFrame();
        }

        /** {@code paintNodeBorder}. */
        void paintFrame() {
            final double s = UiScale.growth();
            final boolean canPick = n.selectable() && !busy;
            setCursor(canPick ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
            final Color color;
            final double width;
            if (hovered && canPick && !selected) {
                // Hovered node (not selected): yellow/gold border to show interactivity
                color = GOLD;
                width = 3;
            } else if (selected) {
                // Selected node: thick bright gold border (used for all node types)
                color = GOLD;
                width = 4;
            } else if (n.completed()) {
                // Completed node: thin green border
                color = GREEN;
                width = 2;
            } else {
                // Uncompleted node: thin gray border
                color = GREY;
                width = 2;
            }
            frame.setStroke(color);
            frame.setStrokeWidth(width * s);
        }

        /** {@code paintCompletionCheckmark}. */
        private Node checkmark(final double s) {
            final Circle disc = new Circle(w - 35 * s + 12.5 * s, 10 * s + 12.5 * s, 12.5 * s,
                Color.rgb(0, 200, 0, 230 / 255.0));
            final double checkX = w - 32 * s;
            final double checkY = 13 * s;
            final Polyline check = new Polyline(
                checkX + 5 * s, checkY + 11 * s,
                checkX + 9 * s, checkY + 15 * s,
                checkX + 18 * s, checkY + 7 * s);
            check.setStroke(Color.WHITE);
            check.setStrokeWidth(2 * s);
            check.setFill(null);
            final Group g = new Group(disc, check);
            g.setMouseTransparent(true);
            return g;
        }

        // --- NodeCircularPanel y sus cuatro hijos ---

        private void buildCircular(final double s) {
            final RoguePathNode node = n.node();
            final Node icon;
            final String tip;
            final Localizer l = Localizer.getInstance();
            if (node instanceof NodeSanctum) {
                icon = RogueNodeIcons.heart(s);
                tip = l.getMessage("lblRogueSanctumTooltip");
            } else if (node instanceof NodeBazaar) {
                icon = RogueNodeIcons.coin(s);
                tip = l.getMessage("lblRogueBazaarTooltip");
            } else if (node instanceof NodeChest) {
                icon = RogueNodeIcons.chest(s);
                tip = l.getMessage("lblRogueChestTooltip");
            } else if (node instanceof NodeEvent) {
                icon = RogueNodeIcons.scroll(s);
                tip = l.getMessage("lblRogueEventTooltip");
            } else {
                // NodeGenericPanel
                icon = null;
                tip = String.valueOf(node);
            }
            if (icon != null) {
                final StackPane center = new StackPane(icon);
                center.setMouseTransparent(true);
                center.resizeRelocate(0, 0, w, h);
                center.setPrefSize(w, h);
                center.setManaged(false);
                getChildren().add(center);
            }
            Tooltip.install(this, new Tooltip(tip));
        }

        // --- NodePlaneboundPanel ---

        private void buildPlanebound(final NodePlanebound pb, final double s) {
            final double x = 10 * s;
            final boolean showFaceDown = n.faceDown() || n.justRevealed();
            flipPending = !n.faceDown() && n.justRevealed() && n.plane() != null;

            // Card image (plane card) - rotated 90 degrees clockwise for horizontal display
            art = new StackPane();
            art.setMouseTransparent(true);
            art.setManaged(false);
            art.resizeRelocate(x, 10 * s, CARD_WIDTH * s, CARD_HEIGHT * s);
            showArt(showFaceDown, s);
            getChildren().add(art);

            // Name row: elite/boss marker + planebound name in a centered flow layout
            final HBox nameRow = new HBox(2 * s);
            nameRow.setAlignment(Pos.CENTER);
            nameRow.setManaged(false);
            nameRow.resizeRelocate(x, (10 + CARD_HEIGHT + 5) * s, CARD_WIDTH * s, 20 * s);
            name = new Label(showFaceDown ? "???" : pb.getRoguePlanebound().planeboundName());
            name.getStyleClass().add("rogue-node-name");
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            name.setMinWidth(0);
            final RoguePlaneboundType type = pb.getPlaneboundType();
            final Localizer l = Localizer.getInstance();
            if (type == RoguePlaneboundType.ELITE) {
                nameRow.getChildren().add(marker(FSkinProp.IMG_STAR_FILLED, 16 * s, "★",
                    l.getMessage("lblRogueElitePlanebound"), s));
            } else if (type == RoguePlaneboundType.BOSS) {
                nameRow.getChildren().add(marker(FSkinProp.IMG_POISON, 18 * s, "☠",
                    l.getMessage("lblRogueBossPlanebound"), s));
            }
            nameRow.getChildren().add(name);
            getChildren().add(nameRow);

            // Lower stat row: wrathful/cursed markers + life total
            final HBox lifeRow = new HBox(2 * s);
            lifeRow.setAlignment(Pos.CENTER);
            lifeRow.setManaged(false);
            lifeRow.resizeRelocate(x, (10 + CARD_HEIGHT + 5 + 25) * s, CARD_WIDTH * s, 20 * s);
            for (int i = 0; i < pb.getWrathfulCount(); i++) {
                lifeRow.getChildren().add(withTip(RogueNodeIcons.flame(s), l.getMessage("lblRogueWrathfulTooltip")));
            }
            for (int i = 0; i < pb.getCursedCount(); i++) {
                lifeRow.getChildren().add(withTip(RogueNodeIcons.pentagram(s), l.getMessage("lblRogueCursedTooltip")));
            }
            final Label life = new Label(NeoText.get("rogue.node.life", n.planeboundLife()));
            life.getStyleClass().add("rogue-node-life");
            life.setPadding(new Insets(0, 0, 0, 6 * s));
            Tooltip.install(life, new Tooltip(l.getMessage("lblRoguePlaneboundStartingLife", n.planeboundLife())));
            lifeRow.getChildren().add(life);
            getChildren().add(lifeRow);

            // Ampliar el plano (solo boca arriba).
            setOnMousePressed(e -> {
                if (e.getButton() == MouseButton.SECONDARY && !n.faceDown() && n.plane() != null && !flipPending) {
                    showZoom(n.plane());
                    e.consume();
                }
            });
        }

        /** La carta de plano, o la funda boca abajo. */
        private void showArt(final boolean faceDown, final double s) {
            final Node turned;
            if (faceDown || n.plane() == null) {
                final Image back = NeoLook.builtInSleeve(FACE_DOWN_PLANE_SLEEVE_INDEX).image();
                if (back == null) {
                    art.getChildren().clear();
                    return;
                }
                final ImageView iv = new ImageView(back);
                iv.setFitWidth(CARD_HEIGHT * s);
                iv.setFitHeight(CARD_WIDTH * s);
                iv.setPreserveRatio(true);
                iv.setSmooth(true);
                turned = new Group(iv);
            } else {
                turned = new Group(planeCard(n.plane(), CARD_HEIGHT * s));
            }
            turned.setRotate(90);
            art.getChildren().setAll(turned);
        }

        /** {@code flipToReveal}: se encoge de lado, cambia de cara en la mitad y vuelve. */
        void flipToReveal() {
            if (!flipPending) {
                return;
            }
            try {
                forge.sound.SoundSystem.instance.play(forge.sound.SoundEffectType.FlipCard,
                    forge.sound.SoundEffectType.FlipCard.isSynced());
            } catch (final RuntimeException e) {
                System.err.println("[rogue] sonido de girar carta: " + e);
            }
            final Transition flip = new Transition() {
                private boolean swapped;

                {
                    setCycleDuration(Duration.millis(300));
                    setInterpolator(javafx.animation.Interpolator.LINEAR);
                }

                @Override
                protected void interpolate(final double progress) {
                    // Cosine easing: smooth ease-in-out (1.0 -> 0.0 -> 1.0)
                    double scaleX = Math.abs(Math.cos(progress * Math.PI));
                    if (!swapped && progress >= 0.5) {
                        scaleX = 0.0;
                        swapped = true;
                        faceUp();
                    }
                    setScaleX(Math.max(0.01, scaleX));
                }
            };
            flip.setOnFinished(e -> setScaleX(1));
            reveals.add(flip);
            flip.play();
        }

        /** Si se corta la animacion, el nodo se queda como tiene que acabar. */
        void finishFlip() {
            setScaleX(1);
            if (flipPending) {
                faceUp();
            }
        }

        private void faceUp() {
            if (!flipPending) {
                return;
            }
            flipPending = false;
            showArt(false, UiScale.growth());
            name.setText(((NodePlanebound) n.node()).getRoguePlanebound().planeboundName());
        }
    }

    /** El icono de elite o de jefe (el de la skin de Forge; si no esta, el caracter). */
    private static Node marker(final FSkinProp prop, final double size, final String fallback,
                               final String tip, final double s) {
        final Node icon = KeywordIcon.view(prop, size);
        final Node shown;
        if (icon != null) {
            shown = icon;
        } else {
            final Label l = new Label(fallback);
            l.getStyleClass().add("rogue-node-name");
            shown = l;
        }
        final StackPane holder = new StackPane(shown);
        // Forge le pone 5 px por debajo: el icono queda un poco mas alto que el nombre.
        holder.setPadding(new Insets(0, 0, 5 * s, 0));
        Tooltip.install(holder, new Tooltip(tip));
        return holder;
    }

    private static Node withTip(final Node icon, final String tip) {
        final StackPane holder = new StackPane(icon);
        Tooltip.install(holder, new Tooltip(tip));
        return holder;
    }

    private static CardNode planeCard(final PaperCard plane, final double cardWidth) {
        final CardNode card = new CardNode(cardWidth);
        card.setRotationEnabled(false);
        card.setMouseTransparent(true);
        card.setCard(CardView.getCardForUi(plane));
        return card;
    }

    /** {@code showZoom}: el plano grande y en horizontal, en la capa de mirar (principio 10). */
    private void showZoom(final PaperCard plane) {
        final double sceneW = getScene() == null ? 1280 : getScene().getWidth();
        final double sceneH = getScene() == null ? 720 : getScene().getHeight();
        // Girada, la carta mide (ancho x ASPECT) de ancho y su ancho de alto.
        final double cardW = Math.min(sceneH * 0.8, sceneW * 0.9 / CardNode.ASPECT);
        final Group turned = new Group(planeCard(plane, cardW));
        turned.setRotate(90);
        final StackPane box = new StackPane(new Group(turned));
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setOnMouseClicked(e -> consult().hide());
        consult().setOnBackgroundClick(() -> consult().hide());
        consult().show(box);
    }
}
