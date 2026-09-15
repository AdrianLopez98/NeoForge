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

        dayNight.getStyleClass().add("day-night");
        dayNight.setMaxWidth(Double.MAX_VALUE);
        dayNight.setVisible(false);
        dayNight.setManaged(false);
        getChildren().add(dayNight);
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
        phases.setPrefTileWidth(Math.max(1, Math.floor((available - 3 * (columns - 1) - 4) / columns)));
        phases.setPrefTileHeight(tallest);
        return 14 + header.prefHeight(width) + (dayNight.isManaged() ? dayNight.prefHeight(width) + 2 : 0)
                + Math.ceil(STOPS.length / (double) columns) * (tallest + 3);
    }}


