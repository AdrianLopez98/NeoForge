package forge.neo.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import forge.neo.NeoText;
import forge.neo.ascent.AscentBattle;
import forge.neo.ascent.AscentMap;
import forge.neo.ascent.AscentNode;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentPlanes;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRun;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import forge.item.PaperCard;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * El mapa del acto. <b>La pantalla estrella del modo.</b>
 *
 * <h2>Es un mapa, no un grafo</h2>
 *
 * <p>Va sobre {@link Parchment} y no sobre el tapete oscuro de la mesa, y esa
 * es la decision de la que cuelga todo lo demas (el plan de Ascenso 3.3, decidida con
 * tres referencias delante el 02-09-2026). En la mesa manda la regla de
 * <i>fondo oscuro, cartas luminosas</i> porque ahi lo que brilla es el arte de
 * las cartas; <b>aqui no hay cartas, hay iconos</b>, y un icono sobre negro con
 * lineas entre medias no parece un mapa: parece un grafo de depuracion.
 *
 * <p>De ahi salen las tres reglas de dibujo, y ninguna es un adorno:
 *
 * <ol>
 *   <li><b>Caminos punteados</b>, no lineas continuas. Una fila de puntos se
 *       lee como una ruta; una linea, como un cable.
 *   <li><b>Un icono por tipo</b>, reconocible por la silueta ({@link AscentIcon}).
 *   <li><b>Los nodos no estan en una rejilla.</b> Cada uno lleva un desvio
 *       propio, sacado de la semilla de la run. Una cuadricula perfecta delata
 *       que esto es una matriz de 12x7, y entonces vuelve a parecer un grafo.
 * </ol>
 *
 * <h2>Se pinta tumbado</h2>
 *
 * <p>El modelo habla de <b>filas</b> (0 abajo, el jefe arriba) porque es como
 * se genera y como se vuelca en texto. La pantalla lo pinta girado: <b>la
 * progresion va de izquierda a derecha</b> y las columnas son carriles de
 * arriba abajo. El motivo es tonto y manda: una pantalla es <b>ancha</b>. Doce
 * pasos en vertical dejan los nodos a 60 px con las tripas del mapa fuera de
 * cuadro; en horizontal caben los doce enteros y sobra sitio para el cerco y la
 * ficha de informacion.
 *
 * <h2>Todo el mapa se ve desde el principio</h2>
 *
 * <p>Con sus iconos, incluidos los caminos que no has cogido. Es lo que
 * convierte elegir en una <b>decision</b> y no en una moneda al aire: se va por
 * la izquierda <i>porque hay una tienda dos pasos mas alla</i>. Un mapa que se
 * descubre andando no tiene decision ninguna.
 */
public class AscentMapScreen extends StackPane {

    /** Que se puede hacer desde el mapa. */
    public interface Actions {
        /** Entrar en un nodo alcanzable. */
        void enter(AscentNode node);

        /**
         * Mirar tu mazo. <b>Solo mirar</b>: el plan de Ascenso
         */
        void deck();

        /** Abandonar la run. Pregunta antes: no se puede deshacer. */
        void abandon();

        void back();
    }

    private final AscentRun run;
    private final AscentMap map;
    private final Actions actions;

    private final Pane board = new Pane();
    private final Canvas paths = new Canvas();
    private final List<NodeDot> dots = new ArrayList<>();
    private final VBox info = new VBox(4);
    private Set<String> reachable = new HashSet<>();

    /**
     * Cuanto ha crecido el jugador, calculado UNA vez.
     *
     * <p>Sale de {@code AscentBattle.playerEdge}, que lee el mazo del disco.
     * La ficha de informacion se levanta en cada raton que pasa por encima de un
     * nodo: pedirlo ahi seria leer el {@code .dck} decenas de veces por segundo.
     */
    private final double edge;

    /** Margen del papel, en tanto por uno del lado. */
    private static final double MARGIN = 0.055;

    public AscentMapScreen(final AscentRun run, final Actions actions) {
        this.run = run;
        this.map = run.map();
        this.actions = actions;
        this.edge = AscentBattle.playerEdge(run);
        getStyleClass().add("ascent-map-root");

        for (final AscentNode n : run.available()) {
            reachable.add(n.key());
        }

        // El papel, sembrado con la semilla de la run y tenido por acto: los
        // tres actos tienen que verse distintos, y la run guardada tiene que
        // volver con exactamente el mismo papel.
        final Parchment paper = new Parchment(run.getSeed() + run.getAct(), tintOfAct(run.getAct()));
        StackPane.setMargin(paper, new Insets(10));

        board.getChildren().add(paths);
        board.setPickOnBounds(false);
        buildNodes();

        final BorderPane chrome = new BorderPane();
        chrome.setPickOnBounds(false);
        chrome.setTop(statusBar());
        chrome.setBottom(bottomBar());
        chrome.setCenter(board);
        BorderPane.setMargin(board, new Insets(4, 18, 4, 18));

        info.getStyleClass().add("ascent-info");
        // ⚠️ Sin el tope, un hijo de StackPane se ESTIRA hasta llenar el
        // contenedor y la alineacion no sirve de nada: la primera captura salio
        // con la ficha ocupando la pantalla entera y el mapa detras. USE_PREF_SIZE
        // es lo que la deja del tamanyo de su texto.
        info.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        info.setVisible(false);
        info.setMouseTransparent(true);
        StackPane.setAlignment(info, Pos.BOTTOM_RIGHT);
        // 100 y no 78: la barra de abajo crecio 22 px al apartarse del borde
        // roto del papel (ver bottomBar), y esta ficha se apoya en el mismo
        // canto. Sin subirla, la ficha del nodo se meteria por debajo de los
        // botones.
        StackPane.setMargin(info, new Insets(0, 40, 100, 0));

        getChildren().addAll(paper, chrome, info);

        board.widthProperty().addListener((o, a, b) -> relayoutBoard());
        board.heightProperty().addListener((o, a, b) -> relayoutBoard());

        // Para poder CAPTURAR la ficha de informacion. El raton no se puede
        // sintetizar en una captura sin ventana, y esa ficha es justo donde se
        // lee con cuanta vida sale el rival — el dato por el que se elige ruta.
        final int hoverAt = Integer.getInteger("neo.ascent.hoverAt", 0);
        if (hoverAt > 0) {
            final javafx.animation.PauseTransition wait =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(hoverAt));
            wait.setOnFinished(e -> {
                for (final NodeDot d : dots) {
                    if (reachable.contains(d.node.key())) {
                        hover(d, true);
                        return;
                    }
                }
            });
            wait.play();
        }

        // Y entrar en un nodo por el camino real, para poder comprobar de
        // punta a punta lo unico que no se puede capturar: que elegir un nodo
        // JUEGA la partida y vuelve con su premio.
        final int enterAt = Integer.getInteger("neo.ascent.enterAt", 0);
        if (enterAt > 0) {
            final javafx.animation.PauseTransition go =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(enterAt));
            go.setOnFinished(e -> {
                for (final NodeDot d : dots) {
                    if (reachable.contains(d.node.key())) {
                        actions.enter(d.node);
                        return;
                    }
                }
            });
            go.play();
        }
    }

    // ------------------------------------------------------------------
    //  El cromo
    // ------------------------------------------------------------------

    /*
     * NO hay franja con el arte del plano, y es una decision, no un olvido.
     *
     * Se probo (03-09-2026): una banda fina bajo la barra con el paisaje del
     * plano del acto. Dos cosas la tumbaron, y la segunda sola habria bastado:
     *
     *   1. El arte NO llega. La imagen de un plano no se descarga por el camino
     *      de CardImages ni esperando 30 segundos, asi que la franja se quedaba
     *      VACIA: un rectangulo mas claro que rompia el pergamino, robaba 74px
     *      de mapa y encima llevaba un nombre flotando sobre nada.
     *   2. Aunque llegara, el sitio es del mapa. "Me gusta como se ve ahora, si
     *      pones imagenes como fondo a lo mejor perdemos legibilidad" (Ana, en
     *      cuanto lo vio). El pergamino es lo que hace que los iconos y los
     *      caminos punteados se lean; competir con un paisaje detras es
     *      justamente lo que la §3.3 decidio no hacer.
     *
     * Lo que SI queda del plano es su nombre, en la barra: da el sitio del acto
     * sin tocar el fondo ni quitar un pixel de mapa. Ver AscentPlanes.
     */

    private Region statusBar() {
        final Label title = new Label(NeoText.get("ascent.map.act", run.getAct()));
        title.getStyleClass().add("ascent-act");

        // De que SITIO es este acto. Es lo unico que queda del plano, y va
        // pegado al titulo y no como pastilla suelta: "ACTO 1" y al lado
        // "Akoum" se lee como un lugar; una pastilla mas entre la vida y los
        // creditos se lee como otro numero que no se entiende.
        final PaperCard plane = AscentPlanes.current(run);
        final Label where = new Label(plane == null ? "" : plane.getName());
        where.getStyleClass().add("ascent-hint");

        final HBox stats = new HBox(18,
                pill(NeoText.get("ascent.life") + "  " + run.getLife() + " / " + run.getMaxLife(),
                        "ascent-pill-life"),
                pill(NeoText.get("ascent.credits") + "  " + run.getCredits(), "ascent-pill"));

        stats.setAlignment(Pos.CENTER_LEFT);

        // Las reliquias, como fila de pastillas. Click derecho -> la carta
        // grande, o sea QUE HACE cada una: es el gesto de leer una carta en
        // todo el juego, y aqui son las tuyas.
        //
        // ⚠️ No vale con CardZoom.install: ese busca un CardNode bajo el raton
        // y una pastilla no lo es. Asi que la pastilla lleva el suyo, que
        // acaba en la MISMA capa (CardZoom.show).
        final HBox relics = new HBox(6);
        relics.setAlignment(Pos.CENTER_RIGHT);
        for (final AscentRelic r : run.relics()) {
            final Label chip = pill(r.getCardName(), "ascent-pill-relic");
            final forge.item.PaperCard card = AscentRelics.cardOf(r);
            if (card != null) {
                chip.setOnMouseClicked(e -> {
                    if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                        CardZoom.show(chip, forge.game.card.CardView.getCardForUi(card));
                    }
                });
            }
            relics.getChildren().add(chip);
        }

        final Region gap = new Region();
        HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
        final HBox bar = new HBox(16, title, where, stats, gap, relics);
        bar.getStyleClass().add("ascent-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        // ⚠️ 38 arriba y no 14: las pastillas son mas altas que el titulo y
        // salian CORTADAS por el borde de la ventana — reportado jugando, con
        // "Creditos 780" partido por la mitad. El titulo aguantaba porque es
        // texto suelto sin fondo, asi que el fallo solo se ve en las pastillas.
        //
        // Y no basta con que quepan: el pergamino tiene el borde ROTO, o sea
        // que arriba del todo el papel todavia no ha empezado. La pastilla
        // entera tiene que quedar sobre el claro, no a caballo del borde
        // (Ana, viendolo en la ventana de verdad).
        bar.setPadding(new Insets(Parchment.SAFE_EDGE, 34, 10, 34));
        return bar;
    }

    private Region bottomBar() {
        final Label hint = new Label(NeoText.get("ascent.map.hint"));
        hint.getStyleClass().add("ascent-hint");

        // "Ver tu mazo" llego cuando existio el visor de SOLO LECTURA
        // (AscentDeckScreen). Antes no estaba a proposito: el unico visor que
        // habia era el EDITOR, y el mazo de una run no se toca (el plan de Ascenso
        // §14) — un boton que abriera el editor haria justo lo que no debe.
        final Button deck = new Button(NeoText.get("ascent.map.deck"));
        deck.getStyleClass().add("ascent-button");
        deck.setOnAction(e -> actions.deck());

        final Button abandon = new Button(NeoText.get("ascent.map.abandon"));
        abandon.getStyleClass().addAll("ascent-button", "ascent-button-danger");
        abandon.setOnAction(e -> confirmAbandon());

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("ascent-button");
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
        final HBox bar = new HBox(10, back, deck, gap, hint, abandon);
        bar.getStyleClass().add("ascent-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        // ⚠️ 38 abajo y no 16, exactamente por lo mismo que la barra de arriba
        // lleva 38 en vez de 14: el pergamino tiene el borde ROTO, asi que
        // abajo del todo el papel YA SE HA ACABADO. Con 16, "Volver", "Tu
        // mazo", la pista y "Abandonar la run" quedaban a caballo del mordisco
        // — medio texto sobre el papel y medio sobre el fondo oscuro, o sea
        // ilegible. Reportado jugando el 03-09-2026.
        //
        // El numero sale del dibujo: Parchment muerde el borde `bite =
        // min(ancho,alto) * 0,018` y ademas lo hace temblar +-bite, asi que el
        // papel puede acabar hasta 2*bite antes del canto. En una ventana de
        // 1080 de alto eso son ~38 px, que es de donde salio el de arriba.
        bar.setPadding(new Insets(10, 34, Parchment.SAFE_EDGE, 34));
        return bar;
    }

    private Label pill(final String text, final String style) {
        final Label l = new Label(text);
        l.getStyleClass().addAll("ascent-pill-base", style);
        return l;
    }

    /**
     * Pregunta antes de tirar la run. No se puede deshacer (principio 6): el
     * boton solo decia que preguntaba, pero llamaba a {@code actions.abandon()}
     * directo. Mismo patron que {@code AscentSetupScreen.confirmOverwrite}.
     */
    private void confirmAbandon() {
        final VBox box = new VBox(12);
        box.getStyleClass().add("ascent-info");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setAlignment(Pos.CENTER);

        final Label q = new Label(NeoText.get("ascent.abandon.confirm.ask"));
        q.getStyleClass().add("ascent-info-title");
        final Label d = new Label(NeoText.get("ascent.abandon.confirm.detail"));
        d.getStyleClass().add("ascent-info-text");
        d.setWrapText(true);
        d.setMaxWidth(420);

        final Button no = new Button(NeoText.get("common.cancel"));
        no.getStyleClass().addAll("ascent-button", "btn-primary");
        final Button yes = new Button(NeoText.get("ascent.abandon.confirm.yes"));
        yes.getStyleClass().addAll("ascent-button", "ascent-button-danger");
        yes.setOnAction(e -> actions.abandon());

        final HBox buttons = new HBox(12, no, yes);
        buttons.setAlignment(Pos.CENTER);
        box.getChildren().addAll(q, d, buttons);

        final StackPane layer = new StackPane(box);
        layer.getStyleClass().add("overlay-dim");
        // La respuesta segura es la de la izquierda Y la que cierra al clicar
        // fuera: si se pulsa sin querer, no pasa nada (principio 6b).
        no.setOnAction(e -> getChildren().remove(layer));
        layer.setOnMouseClicked(e -> {
            if (e.getTarget() == layer) {
                getChildren().remove(layer);
            }
        });
        getChildren().add(layer);
    }

    // ------------------------------------------------------------------
    //  Los nodos
    // ------------------------------------------------------------------

    /** Un nodo dibujado: su chapa, su icono y su estado. */
    private final class NodeDot extends Group {
        final AscentNode node;
        final Circle plate = new Circle();
        Group icon;
        double jitterX;
        double jitterY;

        NodeDot(final AscentNode node, final Random rnd) {
            this.node = node;
            // El desvio propio de cada nodo. Sin esto el mapa es una
            // cuadricula perfecta y se le ve la matriz de 12x7.
            this.jitterX = (rnd.nextDouble() - 0.5) * 0.55;
            this.jitterY = (rnd.nextDouble() - 0.5) * 0.55;
            getChildren().add(plate);
            setOnMouseEntered(e -> hover(this, true));
            setOnMouseExited(e -> hover(this, false));
            setOnMouseClicked(e -> {
                // ⚠️ Con el derecho NO se entra. Entrar en un nodo arranca la
                // partida, y de ahi solo se sale ganandola o perdiendo la run
                // (el plan de Ascenso): es lo ultimo que puede dispararse con el
                // boton que en todo el juego significa "quiero mirar esto".
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                if (reachable.contains(node.key())) {
                    actions.enter(node);
                }
            });
        }

        void restyle(final double base) {
            final boolean can = reachable.contains(node.key());
            final boolean done = node.isCleared();
            // El jefe y la elite son mas grandes. No es un adorno: son los dos
            // nodos por los que se decide una ruta, y tienen que verse venir
            // desde el otro lado del mapa (principio 3, el estado se ve).
            final double r = base * (node.getKind() == AscentNode.Kind.BOSS ? 1.55
                    : node.getKind() == AscentNode.Kind.ELITE ? 1.22 : 1.0);
            final Color plateFill = done ? Color.web("#B9A884")
                    : can ? Color.web("#33404F")
                    : node.getKind() == AscentNode.Kind.BOSS
                            || node.getKind() == AscentNode.Kind.ELITE
                    ? Color.web("#5E4038") : Color.web("#6E6553");
            plate.setRadius(r);
            plate.setFill(plateFill);
            plate.setStroke(can ? Color.web("#4A9BE0")
                    : done ? Color.web("#8A7C63") : Color.web("#4C4436"));
            plate.setStrokeWidth(can ? r * 0.16 : r * 0.09);
            plate.setOpacity(can || done ? 1 : 0.86);
            if (can) {
                plate.setEffect(new DropShadow(r * 0.9, Color.web("#4A9BE0")));
            } else {
                plate.setEffect(null);
            }
            if (icon != null) {
                getChildren().remove(icon);
            }
            // Los huecos del icono van del color de LA CHAPA. Con el del papel,
            // los ojos de la calavera eran dos manchas claras sobre gris y el
            // icono entero se veia como un borron. Se vio en la primera captura.
            icon = AscentIcon.of(node.getKind(), r * 1.15,
                    can ? Color.web("#EAF2FB") : done ? Color.web("#6B6047")
                            : Color.web("#E4DAC4"),
                    plateFill);
            icon.setOpacity(done ? 0.55 : 1);
            getChildren().add(icon);
        }
    }

    private void buildNodes() {
        final Random rnd = new Random(run.getSeed() * 7919L + run.getAct());
        for (int r = 0; r < AscentMap.ROWS; r++) {
            for (final AscentNode n : map.row(r)) {
                final NodeDot dot = new NodeDot(n, rnd);
                dots.add(dot);
                board.getChildren().add(dot);
            }
        }
    }

    private void hover(final NodeDot dot, final boolean on) {
        dot.setScaleX(on ? 1.18 : 1);
        dot.setScaleY(on ? 1.18 : 1);
        info.setVisible(on);
        if (!on) {
            return;
        }
        info.getChildren().clear();
        final Label name = new Label(NeoText.get("ascent.node." + key(dot.node.getKind())));
        name.getStyleClass().add("ascent-info-title");
        final Label what = new Label(NeoText.get("ascent.node." + key(dot.node.getKind()) + ".desc"));
        what.getStyleClass().add("ascent-info-text");
        what.setWrapText(true);
        what.setMaxWidth(300);
        info.getChildren().addAll(name, what);

        // Con cuanta vida sale el rival de ese nodo. Es LA informacion para
        // decidir por donde ir, y el motor no la publica en ningun sitio: sale
        // de la misma curva que va a usar la partida (AscentBattle).
        if (AscentBattle.isBattle(dot.node.getKind())) {
            final int life = AscentBattle.lifeAt(run.getMaxLife(),
                    AscentBattle.progress(run.getAct(), dot.node.getRow()), edge);
            final Label duel = new Label(NeoText.get("ascent.node.duel", run.getLife(), life));
            duel.getStyleClass().add("ascent-info-duel");
            info.getChildren().add(duel);
        }
    }

    private static String key(final AscentNode.Kind kind) {
        return kind.name().toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------
    //  Colocar y pintar
    // ------------------------------------------------------------------

    private void relayoutBoard() {
        final double w = board.getWidth();
        final double h = board.getHeight();
        if (w <= 20 || h <= 20) {
            return;
        }
        final double mx = w * MARGIN;
        final double my = h * MARGIN;
        final double stepX = (w - mx * 2) / AscentMap.ROWS;
        final double stepY = (h - my * 2) / AscentMap.COLS;
        // La chapa, contra el hueco mas apretado de los dos. Si se dimensionara
        // solo contra el ancho, en una ventana baja los nodos se solapan.
        final double r = Math.max(11, Math.min(stepX, stepY) * 0.34);

        for (final NodeDot dot : dots) {
            dot.setLayoutX(pos(dot, true, mx, stepX));
            dot.setLayoutY(pos(dot, false, my, stepY));
            dot.restyle(r);
        }

        paths.setWidth(w);
        paths.setHeight(h);
        paintPaths(paths.getGraphicsContext2D(), r);
    }

    /** Donde cae un nodo. La FILA va al eje X: la progresion se pinta tumbada. */
    private double pos(final NodeDot dot, final boolean horizontal,
                       final double margin, final double step) {
        final int index = horizontal ? dot.node.getRow() : dot.node.getCol();
        final double jitter = horizontal ? dot.jitterX : dot.jitterY;
        return margin + (index + 0.5 + jitter * 0.55) * step;
    }

    /**
     * Los caminos, en puntos.
     *
     * <p>Una curva de Bezier recorrida a pasos, dejando un punto en cada uno —
     * el mismo algoritmo de {@code CombatOverlay}, solo que en vez de trazar la
     * curva se van poniendo circulos sobre ella. Es lo que hace que se lea como
     * una ruta de mapa y no como un cable.
     *
     * <p>El punto de control se separa en horizontal, que es la direccion en la
     * que avanza el mapa: asi los caminos salen y entran de los nodos por los
     * lados y no se pisan con el icono.
     */
    private void paintPaths(final GraphicsContext g, final double r) {
        g.clearRect(0, 0, paths.getWidth(), paths.getHeight());
        for (final NodeDot from : dots) {
            for (final AscentNode next : from.node.getNext()) {
                final NodeDot to = find(next);
                if (to == null) {
                    continue;
                }
                final boolean live = reachable.contains(next.key()) && !from.node.isCleared()
                        || from.node.isCleared() && next.isCleared();
                g.setFill(live ? Color.web("#4A9BE0") : Color.web("#7A6D55", 0.75));

                final double x1 = from.getLayoutX();
                final double y1 = from.getLayoutY();
                final double x2 = to.getLayoutX();
                final double y2 = to.getLayoutY();
                final double cx = (x1 + x2) / 2;
                final double steps = Math.max(6, Math.hypot(x2 - x1, y2 - y1) / (r * 0.85));
                for (int i = 1; i < steps; i++) {
                    final double t = i / steps;
                    final double u = 1 - t;
                    // Bezier cuadratica con DOS controles a media altura, uno
                    // por lado: da la panza suave de las rutas dibujadas a mano.
                    final double x = u * u * x1 + 2 * u * t * cx + t * t * x2;
                    final double y = u * u * y1 + 2 * u * t * ((y1 + y2) / 2) + t * t * y2;
                    final double size = r * (live ? 0.19 : 0.15);
                    g.fillOval(x - size, y - size, size * 2, size * 2);
                }
            }
        }
    }

    private NodeDot find(final AscentNode node) {
        for (final NodeDot d : dots) {
            if (d.node.key().equals(node.key())) {
                return d;
            }
        }
        return null;
    }

    /**
     * El tono del papel de cada acto.
     *
     * <p>Los tres actos tienen que verse distintos de un vistazo, y tenirle el
     * papel es la forma mas barata: no hace falta ni una imagen. El acto 1 es
     * pergamino limpio, el 2 se oscurece y el 3 tira a ceniza.
     */
    private static Color tintOfAct(final int act) {
        switch (act) {
            case 1:
                return Color.web("#E8D7B0");
            case 2:
                return Color.web("#D9C398");
            default:
                return Color.web("#C7AE8B");
        }
    }
}
