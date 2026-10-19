package forge.neo.ui;

import java.util.List;
import java.util.function.Supplier;

import forge.neo.NeoText;
import forge.neo.match.TurnStats;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * El panel «Turno»: lo que lleva hecho cada jugador este turno (ver
 * {@link TurnStats}). Una columna por jugador, tu el primero y el del turno
 * marcado; una fila por dato.
 *
 * <p>Se CONSULTA, asi que va en la capa de consulta de la mesa y no en la de los
 * dialogos del motor (principio 10): abrirlo con una pregunta pendiente no la
 * borra. Y se refresca solo cada medio segundo mientras esta abierto — los
 * disparos de un Bowmasters se van contando delante de ti —, con un temporizador
 * que se para al salir de la escena (principio 11).
 */
public class TurnStatsView extends VBox {

    private final Supplier<TurnStats.Snapshot> source;
    private final Label subtitle = new Label();
    private final Label storm = new Label();
    private final GridPane grid = new GridPane();
    private final Timeline ticker;
    private TurnStats.Snapshot last;

    public TurnStatsView(final Supplier<TurnStats.Snapshot> source, final Runnable onClose) {
        this.source = source;
        getStyleClass().addAll("dialog", "turn-stats");
        setSpacing(10);
        setPadding(new Insets(18, 20, 16, 20));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(NeoText.get("turnStats.title"));
        heading.getStyleClass().add("dialog-title");
        subtitle.getStyleClass().add("dialog-text");
        storm.getStyleClass().add("dialog-text");

        grid.getStyleClass().add("turn-stats-grid");
        grid.setHgap(UiScale.px(18));
        grid.setVgap(UiScale.px(5));
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToHeight(true);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // Hasta seis jugadores caben; con mas, se desplaza a lo ancho en vez de
        // salirse de la ventana (principio 5).
        scroll.setPrefViewportWidth(UiScale.px(640));
        scroll.setMaxWidth(UiScale.px(820));

        final Label hint = new Label(NeoText.get("turnStats.hint"));
        hint.getStyleClass().add("turn-stats-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(UiScale.px(640));

        final Button close = new Button(NeoText.get("common.close"));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> onClose.run());
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, hint, gap, close);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, subtitle, scroll, storm, footer);

        ticker = new Timeline(new KeyFrame(Duration.millis(500), e -> refresh()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((o, was, is) -> {
            if (is == null) {
                ticker.stop();
            } else {
                ticker.play();
            }
        });
        refresh();
    }

    /** Lee y vuelve a pintar. Lo que no cambia no cuesta nada: son veinte etiquetas. */
    private void refresh() {
        final TurnStats.Snapshot s = source.get();
        // Solo si ha cambiado algo: rehacer la tabla cada medio segundo se
        // llevaria por delante el tooltip que estas leyendo.
        if (s == null || s.equals(last)) {
            return;
        }
        last = s;
        subtitle.setText(NeoText.get("turnStats.subtitle", s.turn(), s.activeName()));
        storm.setText(s.spellsAll() < 0 ? "" : NeoText.get("turnStats.storm", s.spellsAll()));

        grid.getChildren().clear();
        grid.getColumnConstraints().clear();
        final ColumnConstraints first = new ColumnConstraints();
        first.setHalignment(HPos.LEFT);
        grid.getColumnConstraints().add(first);

        final List<TurnStats.Row> rows = s.rows();
        for (int c = 0; c < rows.size(); c++) {
            final TurnStats.Row r = rows.get(c);
            final ColumnConstraints cc = new ColumnConstraints();
            cc.setHalignment(HPos.CENTER);
            grid.getColumnConstraints().add(cc);
            final Label name = new Label(r.player() == null ? "?" : r.player().getName());
            name.getStyleClass().add("turn-stats-player");
            if (r.active()) {
                name.getStyleClass().add("turn-stats-active");
            }
            grid.add(name, c + 1, 0);
        }

        final String[] keys = {"lifeGained", "lifeLost", "drawn", "spells", "graveyard",
                "discarded", "sacrificed", "tokens", "lands"};
        for (int k = 0; k < keys.length; k++) {
            final Label label = new Label(NeoText.get("turnStats." + keys[k]));
            label.getStyleClass().add("turn-stats-label");
            grid.add(label, 0, k + 1);
            for (int c = 0; c < rows.size(); c++) {
                final TurnStats.Row r = rows.get(c);
                final int v = value(r, k);
                final Label cell = new Label(v < 0 ? "?" : String.valueOf(v));
                cell.getStyleClass().add("turn-stats-value");
                if (v == 0) {
                    cell.getStyleClass().add("turn-stats-zero");
                } else if (k == 0) {
                    cell.getStyleClass().add("turn-stats-gain");
                } else if (k == 1) {
                    cell.getStyleClass().add("turn-stats-loss");
                }
                // Cuales han ido al cementerio, al pasar el raton.
                if (k == 4 && v > 0) {
                    cell.setTooltip(new Tooltip(String.join("\n", r.graveyardNames())));
                }
                grid.add(cell, c + 1, k + 1);
            }
        }
    }

    private static int value(final TurnStats.Row r, final int k) {
        return switch (k) {
            case 0 -> r.lifeGained();
            case 1 -> r.lifeLost();
            case 2 -> r.drawn();
            case 3 -> r.spells();
            case 4 -> r.toGraveyard();
            case 5 -> r.discarded();
            case 6 -> r.sacrificed();
            case 7 -> r.tokens();
            default -> r.lands();
        };
    }
}
