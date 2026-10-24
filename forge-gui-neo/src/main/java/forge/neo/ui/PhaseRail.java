package forge.neo.ui;

import java.util.EnumMap;
import java.util.Map;

import forge.game.phase.PhaseType;
import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * Rail vertical de fases, como el de Arena.
 *
 * <p>Muestra siempre el turno completo con la fase actual encendida. Saber en
 * que fase estas sin tener que leer un texto es la mitad de lo que hace que un
 * cliente de Magic se sienta comodo.
 *
 * <p>En la fase 4 cada parada sera clicable para elegir donde recuperar la
 * prioridad, que es como Arena deja controlar el auto-pass.
 */
public class PhaseRail extends VBox {

    /** Las paradas que se muestran; se agrupan pasos que al jugador le dan igual. */
    private static final PhaseType[] STOPS = {
            PhaseType.UNTAP,
            PhaseType.UPKEEP,
            PhaseType.DRAW,
            PhaseType.MAIN1,
            PhaseType.COMBAT_BEGIN,
            PhaseType.COMBAT_DECLARE_ATTACKERS,
            PhaseType.COMBAT_DECLARE_BLOCKERS,
            PhaseType.COMBAT_DAMAGE,
            PhaseType.COMBAT_END,
            PhaseType.MAIN2,
            PhaseType.END_OF_TURN,
            PhaseType.CLEANUP,
    };

    /**
     * El nombre de una fase, corto.
     *
     * <p>Publico porque el prompt tambien lo necesita: el del motor viene en
     * ingles ("Main phase, precombat") y la mesa ya tiene la traduccion aqui.
     * Tener dos tablas distintas seria pedir que se desincronicen.
     *
     * <p><b>Por que no se usa {@code PhaseType.nameForUi}</b>, que el motor ya
     * trae traducido: porque es la frase larga ("Declare Attackers Step") y el
     * rail es una columna estrecha. La version corta va en nuestra tabla, y
     * para un idioma que no hayamos traducido se cae a la del motor, que sera
     * larga pero esta en su idioma.
     */
    public static String nameOf(final PhaseType phase) {
        if (phase == null) {
            return "";
        }
        final String key = "phase." + phase.name();
        final String mine = NeoText.get(key);
        if (!key.equals(mine)) {
            return mine;
        }
        return phase.nameForUi == null ? phase.name() : phase.nameForUi;
    }

    private final Map<PhaseType, Label> labels = new EnumMap<>(PhaseType.class);
    private static final javafx.css.PseudoClass CURRENT =
            javafx.css.PseudoClass.getPseudoClass("current");
    private static final javafx.css.PseudoClass STOP =
            javafx.css.PseudoClass.getPseudoClass("stop");

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /** Recibe (paradas de MIS turnos?, fase). */
    private java.util.function.BiConsumer<Boolean, PhaseType> onToggle;

    /**
     * De quien son las paradas que se ven y se tocan: de tus turnos o de los
     * del rival.
     *
     * <p>Pedido en r/forgeMTG el 15-09-2026: el Forge de siempre deja marcar las
     * paradas de cada jugador por separado, y aqui habia una sola lista para
     * los dos turnos — no se podia parar en el mantenimiento del rival sin
     * pararte tambien en el tuyo. Dos pestanyas encima del rail y no un segundo
     * rail: la mesa no tiene alto que regalar.
     *
     * <p>Sigue sola al turno ({@link #setTurnOwner}): lo normal es querer ver
     * las paradas que aplican a lo que se esta jugando. Si se elige la otra a
     * mano, se respeta hasta el siguiente cambio de turno.
     */
    private boolean editingMine = true;
    private Boolean lastOwner;
    private java.util.function.Predicate<PhaseType> mineStops;
    private java.util.function.Predicate<PhaseType> theirStops;
    private final Label mineTab = new Label(NeoText.get("phase.stops.mine"));
    private final Label theirTab = new Label(NeoText.get("phase.stops.theirs"));
    private final javafx.scene.layout.HBox header = new javafx.scene.layout.HBox(4);

    public PhaseRail() {
        getStyleClass().add("phase-rail");
        setSpacing(2);
        setPadding(new Insets(5, 10, 5, 10));
        setAlignment(Pos.TOP_LEFT);

        title.getStyleClass().add("caption");
        final javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        javafx.scene.layout.HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        for (final Label tab : new Label[] {mineTab, theirTab}) {
            final boolean mine = tab == mineTab;
            tab.getStyleClass().add("phase-owner");
            tab.setCursor(javafx.scene.Cursor.HAND);
            tab.setMinWidth(Label.USE_PREF_SIZE);
            tab.setTooltip(new javafx.scene.control.Tooltip(
                    NeoText.get(mine ? "phase.stops.mine.tip" : "phase.stops.theirs.tip")));
            tab.setOnMouseClicked(e -> {
                setEditingMine(mine);
                e.consume();
            });
        }
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(title, spacer, mineTab, theirTab);
        getChildren().add(header);
        setEditingMine(true);

        // El orden de turnos (ver setTurnOrder): solo con tres o mas.
        turnOrder.getStyleClass().add("turn-order");
        turnOrder.setHgap(4);
        turnOrder.setVgap(1);
        turnOrder.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.control.Tooltip.install(turnOrder,
                new javafx.scene.control.Tooltip(NeoText.get("phase.order.tip")));
        turnOrder.setVisible(false);
        turnOrder.setManaged(false);
        getChildren().add(turnOrder);

        dayNight.getStyleClass().add("day-night");
        dayNight.setMaxWidth(Double.MAX_VALUE);
        dayNight.setVisible(false);
        dayNight.setManaged(false);
        getChildren().add(dayNight);
        // El plano activo (Planechase): como el dia y la noche, solo si lo hay.
        plane.getStyleClass().addAll("day-night", "active-plane");
        plane.setMaxWidth(Double.MAX_VALUE);
        plane.setCursor(javafx.scene.Cursor.HAND);
        plane.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("phase.plane.tip")));
        plane.setOnMouseClicked(e -> {
            if (!planes.isEmpty()) {
                CardZoom.show(plane, planes.size(), 0, planes::get);
            }
            e.consume();
        });
        plane.setVisible(false);
        plane.setManaged(false);
        getChildren().add(plane);
        phases.setHgap(3); phases.setVgap(3);
        getChildren().add(phases);

        for (final PhaseType p : STOPS) {
            final Label l = new Label(nameOf(p));
            l.getStyleClass().add("phase-stop");
            l.setMaxWidth(Double.MAX_VALUE);
            l.setCursor(javafx.scene.Cursor.HAND);
            l.setOnMouseClicked(e -> {
                if (onToggle != null) {
                    onToggle.accept(editingMine, p);
                }
            });
            labels.put(p, l);
            l.setMinWidth(0);
            l.setAlignment(Pos.CENTER);
            l.setTooltip(new javafx.scene.control.Tooltip(nameOf(p)));
            phases.getChildren().add(l);
        }
    }

    /**
     * De quien es el turno, encima del rail.
     *
     * <p>El cartel del centro dura un segundo; esto se queda. Va aqui y no en
     * otro sitio porque el rail es lo que ya se mira para saber por donde va el
     * turno — la pregunta de al lado.
     */
    public void setTurnOwner(final boolean yours, final String who) {
        title.setText(yours ? NeoText.get("turn.yours")
                : NeoText.get("turn.theirs",
                        who == null ? "?" : who.toUpperCase(java.util.Locale.ROOT)));
        title.pseudoClassStateChanged(YOURS, yours);
        // Solo al CAMBIAR de turno: esto se llama en cada repintado, y si no
        // se miraba, elegir la otra pestanya a mano duraba un instante.
        if (lastOwner == null || lastOwner != yours) {
            lastOwner = yours;
            setEditingMine(yours);
        }
    }

    /**
     * <b>El orden de turnos</b>, debajo de "TURNO DE ...": el que juega ahora
     * el primero, encendido, y detras los que vienen (Discord, 24-10-2026:
     * <i>"somewhere on the UI that shows the turn order"</i>). En una mesa de
     * cuatro, saber cuando te toca no se deduce de donde se sienta cada uno.
     *
     * <p>Y <b>quien tiene la prioridad</b>, con un punto delante (Discord,
     * 24-10-2026: <i>"maybe just highlight the name of whoever has priority in
     * the turn order?"</i>; el "Waiting for X" de Forge sale solo a ratos). Por
     * eso sale tambien a dos, que el orden no dice nada pero la prioridad si.
     *
     * <p>Lo que el motor no publica es si el sentido se ha invertido (Aeon
     * Engine y compania), asi que en esas partidas sale el orden de los
     * asientos.
     *
     * @param names    los que siguen en juego, empezando por el del turno
     * @param you      cual de ellos eres tu, o -1
     * @param priority cual de ellos tiene la prioridad, o -1
     */
    public void setTurnOrder(final java.util.List<String> names, final int you, final int priority) {
        final boolean on = names != null && names.size() >= 2;
        turnOrder.setVisible(on);
        turnOrder.setManaged(on);
        if (!on) {
            return;
        }
        if (!names.equals(orderShown) || you != orderYou) {
            orderShown = java.util.List.copyOf(names);
            orderYou = you;
            orderPlayers.clear();
            final Label caption = new Label(NeoText.get("phase.order"));
            caption.getStyleClass().add("turn-order-caption");
            turnOrder.getChildren().setAll(caption);
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) {
                    final Label arrow = new Label("›");
                    arrow.getStyleClass().add("turn-order-arrow");
                    turnOrder.getChildren().add(arrow);
                }
                final Label who = new Label(i == you ? NeoText.get("phase.stops.mine") : names.get(i));
                who.getStyleClass().add("turn-order-player");
                who.pseudoClassStateChanged(CURRENT, i == 0);
                who.pseudoClassStateChanged(YOURS, i == you);
                orderPlayers.add(who);
                turnOrder.getChildren().add(who);
            }
            priorityShown = -1;
            paintPriority(priority);
            return;
        }
        // LA IA PASA LA PRIORIDAD EN MILISEGUNDOS: el punto saltaria de nombre en
        // nombre sin que se pudiera leer. Solo se mueve si se queda quieto un
        // momento, que es justo cuando alguien esta pensando de verdad.
        priorityWanted = priority;
        prioritySettle.playFromStart();
    }

    private void paintPriority(final int priority) {
        if (priority == priorityShown) {
            return;
        }
        priorityShown = priority;
        for (int i = 0; i < orderPlayers.size(); i++) {
            final Label who = orderPlayers.get(i);
            final boolean has = i == priority;
            who.pseudoClassStateChanged(PRIORITY, has);
            if (has) {
                final Label dot = new Label("●");
                dot.getStyleClass().add("turn-order-priority");
                who.setGraphic(dot);
            } else {
                who.setGraphic(null);
            }
        }
    }

    private final javafx.scene.layout.FlowPane turnOrder = new javafx.scene.layout.FlowPane();
    private final java.util.List<Label> orderPlayers = new java.util.ArrayList<>();
    private java.util.List<String> orderShown = java.util.List.of();
    private int orderYou = -2;
    private int priorityShown = -1;
    private int priorityWanted = -1;
    private final javafx.animation.PauseTransition prioritySettle =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(300));
    {
        prioritySettle.setOnFinished(e -> paintPriority(priorityWanted));
    }
    private static final javafx.css.PseudoClass PRIORITY =
            javafx.css.PseudoClass.getPseudoClass("priority");

    /** Elige de quien son las paradas que se ven y se conmutan. */
    public void setEditingMine(final boolean mine) {
        editingMine = mine;
        mineTab.pseudoClassStateChanged(SELECTED, mine);
        theirTab.pseudoClassStateChanged(SELECTED, !mine);
        paintStops();
    }

    public boolean isEditingMine() {
        return editingMine;
    }

    private final javafx.scene.layout.TilePane phases = new javafx.scene.layout.TilePane();

    /** Cuantos huecos hay ahora mismo delante de la ultima fila (ver alignLastRowRight). */
    private int spacersNow = -1;

    /**
     * Si las fases no caben en una fila, la ULTIMA fila va pegada a la
     * derecha. Con sitio para 11 de las 12, Cleanup bajaba sola a la primera
     * columna, JUSTO DEBAJO de Untap, y al poner una parada en Untap se clicaba
     * Cleanup sin querer (reportado el 29-09-2026, en Commander cambiando de
     * mesa). Pegada a la derecha queda debajo de "Fin del turno", que es la
     * fase de antes. Se rellena con huecos invisibles, y solo se toca la lista
     * cuando cambia el numero de huecos: esto corre dentro del layout.
     */
    private void alignLastRowRight(final int columns) {
        final int n = STOPS.length;
        final int rest = n % columns;
        final int spacers = n > columns && rest > 0 ? columns - rest : 0;
        if (spacers == spacersNow) {
            return;
        }
        spacersNow = spacers;
        final java.util.List<javafx.scene.Node> nodes = new java.util.ArrayList<>();
        int i = 0;
        for (final PhaseType p : STOPS) {
            if (i++ == n - rest && spacers > 0) {
                for (int k = 0; k < spacers; k++) {
                    final javafx.scene.layout.Region gap = new javafx.scene.layout.Region();
                    gap.setMouseTransparent(true);
                    nodes.add(gap);
                }
            }
            nodes.add(labels.get(p));
        }
        phases.getChildren().setAll(nodes);
    }
    private final Label title = new Label(NeoText.get("phase.caption"));

    /**
     * Si es de dia o de noche.
     *
     * <p>El motor lleva la cuenta desde Innistrad y nos la manda por
     * {@code IGuiGame.updateDayTime}; nosotros la tirabamos. Importa porque hay
     * cartas que se transforman al cambiar el ciclo (daybound / nightbound) y
     * sin verlo la transformacion parece que pasa sola.
     *
     * <p>Aparece <b>solo cuando el ciclo esta en marcha</b>: en la inmensa
     * mayoria de partidas nunca lo esta, y una etiqueta permanente de "de dia"
     * seria ruido en todas ellas.
     */
    private final Label dayNight = new Label();

    /** {@code "Day"}, {@code "Night"} o {@code null} si no hay ciclo. */
    public void setDayTime(final String daytime) {
        final boolean day = "Day".equals(daytime);
        final boolean night = "Night".equals(daytime);
        dayNight.setText(day ? NeoText.get("phase.day")
                : night ? NeoText.get("phase.night") : "");
        dayNight.pseudoClassStateChanged(NIGHT, night);
        dayNight.setVisible(day || night);
        dayNight.setManaged(day || night);
    }

    /**
     * <b>El plano activo</b> (Planechase, 08-10-2026). Esta en la zona de mando
     * de quien lo controla — el jugador del turno — y la de un rival en la mesa
     * es solo un contador: en su turno no se veia el plano, que es justo la carta
     * que afecta a todos. Aqui se ve siempre su nombre, y un clic la amplia
     * (girada para leerla: ver {@code CardNode.sidewaysArtProperty}).
     */
    private final Label plane = new Label();
    private java.util.List<forge.game.card.CardView> planes = java.util.List.of();

    /** Los planos (o fenomenos) activos; vacio sin Planechase. */
    public void setPlanes(final java.util.List<forge.game.card.CardView> now) {
        planes = now == null ? java.util.List.of() : java.util.List.copyOf(now);
        final boolean on = !planes.isEmpty();
        if (on) {
            final StringBuilder names = new StringBuilder();
            for (final forge.game.card.CardView c : planes) {
                if (names.length() > 0) {
                    names.append(" · ");
                }
                names.append(forge.neo.card.CardText.nameOf(c));
            }
            plane.setText("⬡ " + NeoText.get("phase.plane", names));
        }
        plane.setVisible(on);
        plane.setManaged(on);
        // Solo pruebas: -Dneo.zoom.plane=true amplia el plano la primera vez que
        // sale, para capturar la carta girada para leerla.
        if (on && !zoomedForTest && Boolean.getBoolean("neo.zoom.plane")) {
            zoomedForTest = true;
            javafx.application.Platform.runLater(() -> CardZoom.show(plane, planes.size(), 0, planes::get));
        }
    }

    private boolean zoomedForTest;

    private static final javafx.css.PseudoClass NIGHT =
            javafx.css.PseudoClass.getPseudoClass("night");

    private static final javafx.css.PseudoClass YOURS =
            javafx.css.PseudoClass.getPseudoClass("yours");

    public void setCurrent(final PhaseType phase) {
        for (final Map.Entry<PhaseType, Label> e : labels.entrySet()) {
            e.getValue().pseudoClassStateChanged(CURRENT, e.getKey() == phase);
        }
    }

    /**
     * Que hacer al clicar una fase: poner o quitar la parada.
     *
     * <p>Es el control del auto-pass de Arena. Con la parada puesta el juego se
     * detiene en esa fase aunque no tengas nada urgente; sin ella pasa sola.
     * Poder cambiarlo en mitad de la partida es lo que hace usable el auto-pass:
     * en un turno quieres parar en tu Principal 2 y en el siguiente no.
     */
    public void setOnToggle(final java.util.function.BiConsumer<Boolean, PhaseType> handler) {
        this.onToggle = handler;
    }

    /** Marca las fases en las que el juego te va a dar la prioridad, en tus turnos y en los del rival. */
    public void setStops(final java.util.function.Predicate<PhaseType> mine,
                         final java.util.function.Predicate<PhaseType> theirs) {
        this.mineStops = mine;
        this.theirStops = theirs;
        paintStops();
    }

    private void paintStops() {
        final java.util.function.Predicate<PhaseType> isStop = editingMine ? mineStops : theirStops;
        for (final Map.Entry<PhaseType, Label> e : labels.entrySet()) {
            e.getValue().pseudoClassStateChanged(STOP,
                    isStop != null && isStop.test(e.getKey()));
        }
    }
    /** Same clickable phase labels, arranged horizontally with a two-row fallback. */
    public double arenaHeight(double width) {
        double available = Math.max(1, width - 20);
        double widest = 70;
        double tallest = 18;
        for (Label label : labels.values()) {
            widest = Math.max(widest, label.prefWidth(-1));
            tallest = Math.max(tallest, label.prefHeight(-1));
        }
        int columns = Math.max(1, Math.min(STOPS.length, (int)((available + 3) / (widest + 3))));
        phases.setPrefColumns(columns);
        // Un pixel de holgura por fase (Discord, 14-10-2026, 4K a pantalla
        // completa): con el escalado de Windows (150%) JavaFX redondea cada
        // fase y cada hueco a pixeles de pantalla, y con 12 fases eso pasaba de
        // los 4 px de margen: Cleanup bajaba sola a la segunda fila, a la
        // izquierda y encima de la barra del rival.
        phases.setPrefTileWidth(Math.max(1, Math.floor((available - 3 * (columns - 1) - 4) / columns) - 1));
        phases.setPrefTileHeight(tallest);
        // Los huecos de la ultima fila los pone layoutChildren, con las
        // columnas que de verdad salen: si los pusieran los dos, se pelearian.
        // El dia/noche y el plano activo (Planechase) son filas enteras: si no
        // se cuentan, las fases se salen del hueco y pisan la barra del rival
        // (Discord, 08-10-2026, en 4K con Planechase).
        // Y el orden de turnos, que es otra fila (y puede partirse en dos).
        final double orderRow = turnOrder.isManaged() ? turnOrder.prefHeight(available) + 2 : 0;
        return 14 + header.prefHeight(width) + orderRow + rowHeight(dayNight, width) + rowHeight(plane, width)
                + Math.ceil(STOPS.length / (double) columns) * (tallest + 3);
    }

    /**
     * Y si aun asi el TilePane parte la fila distinto de lo calculado, la
     * ultima fila se alinea con las columnas que HA PUESTO, no con las que se
     * esperaban: mejor Cleanup a la derecha que debajo de Untap.
     */
    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        final double tile = phases.getTileWidth();
        final double inner = phases.getWidth() - phases.snappedLeftInset() - phases.snappedRightInset();
        if (tile > 0 && inner > 0) {
            final int real = Math.max(1, (int) Math.floor((inner + phases.getHgap()) / (tile + phases.getHgap())));
            final int before = spacersNow;
            alignLastRowRight(Math.min(real, STOPS.length));
            if (before != spacersNow) {
                phases.requestLayout();
            }
        }
    }

    private static double rowHeight(final Label row, final double width) {
        return row.isManaged() ? row.prefHeight(width) + 2 : 0;
    }}


