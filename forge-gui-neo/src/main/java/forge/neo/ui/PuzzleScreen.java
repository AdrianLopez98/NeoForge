package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
import java.util.function.Consumer;

import forge.gamemodes.puzzle.Puzzle;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * La lista de puzzles.
 *
 * <p>Un puzzle es una partida ya empezada con un objetivo concreto ("gana este
 * turno"). Forge trae cientos en {@code res/puzzle} y sabe montarlos solo; aqui
 * solo hay que elegir uno.
 *
 * <p>Se ensenya el objetivo junto al nombre porque es lo unico que necesitas
 * para decidir si te apetece ese: el nombre por si solo no dice nada.
 */
public class PuzzleScreen extends BorderPane {

    private Puzzle selected;

    public PuzzleScreen(final List<Puzzle> puzzles, final Consumer<Puzzle> onPlay,
                        final Runnable onBack) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("puzzles.title"));
        title.getStyleClass().add("home-title");
        final Label subtitle = new Label(puzzles.isEmpty()
                ? NeoText.get("puzzles.none")
                : NeoText.get("puzzles.count", puzzles.size()));
        subtitle.getStyleClass().add("home-subtitle");
        final VBox header = new VBox(2, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(30, 20, 16, 20));
        setTop(header);

        final VBox list = new VBox(6);
        list.setPadding(new Insets(6, 40, 6, 40));

        final Button play = new Button(NeoText.get("puzzles.play"));
        play.getStyleClass().addAll("btn-primary", "btn-play");
        play.setDisable(true);
        play.setOnAction(e -> {
            if (selected != null) {
                onPlay.accept(selected);
            }
        });

        for (final Puzzle puzzle : puzzles) {
            final Label name = new Label(puzzle.getName());
            name.getStyleClass().add("mode-tile-name");
            final Label goal = new Label(shortGoal(puzzle));
            goal.getStyleClass().add("mode-tile-desc");
            goal.setWrapText(true);

            final VBox row = new VBox(2, name, goal);
            row.getStyleClass().add("puzzle-row");
            row.setPadding(new Insets(10, 16, 10, 16));
            row.setMaxWidth(Double.MAX_VALUE);
            row.setOnMouseClicked(e -> {
                selected = puzzle;
                for (final javafx.scene.Node n : list.getChildren()) {
                    n.pseudoClassStateChanged(SELECTED, n == row);
                }
                play.setDisable(false);
                if (e.getClickCount() >= 2) {
                    onPlay.accept(puzzle);
                }
            });
            list.getChildren().add(row);
        }

        final ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setOnAction(e -> onBack.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, back, gap, play);
        footer.getStyleClass().add("home-footer");
        footer.setPadding(new Insets(14, 30, 20, 30));
        footer.setAlignment(Pos.CENTER_LEFT);
        setBottom(footer);
    }

    /** El objetivo, recortado: algunos traen parrafos enteros. */
    private static String shortGoal(final Puzzle puzzle) {
        String goal = puzzle.getGoalDescription();
        if (goal == null) {
            return "";
        }
        goal = goal.replace("\n", " ").trim();
        return goal.length() > 160 ? goal.substring(0, 157) + "..." : goal;
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
}
