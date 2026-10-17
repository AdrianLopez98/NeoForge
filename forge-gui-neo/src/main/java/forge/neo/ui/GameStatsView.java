package forge.neo.ui;

import java.util.List;

import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * <b>Las estadisticas de la partida</b>, desde el boton del final (Discord,
 * 17-10-2026). Con la misma pinta que el registro ({@link GameLogView}) y en la
 * misma capa, la de consultar: no contesta nada al motor (principio 10). Las
 * frases las hace {@link forge.neo.match.GameStats}, que comparte Android.
 */
public class GameStatsView extends VBox {

    public GameStatsView(final List<String> lines, final Runnable onClose) {
        getStyleClass().addAll("dialog", "game-log");
        setSpacing(12);
        setPadding(new Insets(18, 20, 16, 20));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(NeoText.get("stats.title"));
        heading.getStyleClass().add("dialog-title");
        getChildren().add(heading);

        final VBox body = new VBox(8);
        if (lines == null || lines.isEmpty()) {
            final Label none = new Label(NeoText.get("stats.none"));
            none.getStyleClass().add("dialog-text");
            body.getChildren().add(none);
        } else {
            for (final String line : lines) {
                final Label l = new Label("•  " + line);
                l.getStyleClass().add("dialog-text");
                l.setWrapText(true);
                l.setMaxWidth(UiScale.px(620));
                l.setMinHeight(Region.USE_PREF_SIZE);
                body.getChildren().add(l);
            }
        }
        getChildren().add(body);

        final Button close = new Button(NeoText.get("common.close"));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> onClose.run());
        final HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        getChildren().add(footer);
    }
}
