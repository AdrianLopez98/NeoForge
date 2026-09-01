package forge.neo.ui;

import java.util.List;
import java.util.function.Consumer;

import forge.neo.NeoText;
import forge.neo.tutorial.NeoTutorial;
import forge.neo.tutorial.TutorialLesson;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Las lecciones del tutorial: cual jugar.
 *
 * <p>Las tres se pueden jugar en cualquier orden y cuantas veces se quiera —
 * nadie deberia tener que terminar la primera para poder repasar el combate —
 * pero el boton grande apunta siempre a <b>la siguiente sin hacer</b>, que es lo
 * que quiere quien acaba de llegar y no sabe por donde empezar.
 *
 * <p>Cada casilla dice lo que se aprende en ella. "Leccion 2" a secas no
 * informa de nada, y la decision que se toma aqui es exactamente esa: que quiero
 * aprender ahora.
 */
public class TutorialScreen extends BorderPane {

    public TutorialScreen(final boolean firstRun, final Consumer<TutorialLesson> onPlay,
                          final Runnable onBack) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("tutorial.title"));
        title.getStyleClass().add("home-title");

        // La primera vez el subtitulo es una bienvenida; despues, el progreso.
        // Es el mismo hueco diciendo lo que hace falta en cada momento.
        final Label subtitle = new Label(firstRun
                ? NeoText.get("tutorial.welcome")
                : NeoText.get("tutorial.progress", NeoTutorial.doneCount(),
                        NeoTutorial.lessons().size()));
        subtitle.getStyleClass().add("home-subtitle");
        subtitle.setWrapText(true);
        subtitle.setMaxWidth(760);

        final VBox header = new VBox(4, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(30, 20, 16, 20));
        setTop(header);

        final List<TutorialLesson> lessons = NeoTutorial.lessons();

        final FlowPane tiles = new FlowPane(16, 16);
        tiles.setAlignment(Pos.CENTER);
        tiles.setPadding(new Insets(10, 40, 10, 40));
        tiles.setPrefWrapLength(900);

        int n = 1;
        for (final TutorialLesson lesson : lessons) {
            tiles.getChildren().add(tile(n++, lesson, onPlay));
        }

        final ScrollPane scroll = new ScrollPane(tiles);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setOnAction(e -> onBack.run());

        final Button again = new Button(NeoText.get("tutorial.reset"));
        again.getStyleClass().add("btn-secondary");
        again.setDisable(NeoTutorial.doneCount() == 0);
        again.setOnAction(e -> {
            NeoTutorial.reset();
            // Se repinta la pantalla entera: las marcas de "hecha" viven en las
            // casillas, y dejarlas puestas seria mentir sobre lo que hay.
            onBack.run();
        });

        final TutorialLesson next = NeoTutorial.nextLesson();
        final Button play = new Button(NeoText.get("tutorial.play", next.getTitle()));
        play.getStyleClass().addAll("btn-primary", "btn-play");
        play.setOnAction(e -> onPlay.accept(next));

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, back, again, gap, play);
        footer.getStyleClass().add("home-footer");
        footer.setPadding(new Insets(14, 30, 20, 30));
        footer.setAlignment(Pos.CENTER_LEFT);
        for (final Button b : new Button[] {back, again, play}) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        setBottom(footer);
    }

    private static Region tile(final int number, final TutorialLesson lesson,
                               final Consumer<TutorialLesson> onPlay) {
        final Label name = new Label(number + ". " + lesson.getTitle());
        name.getStyleClass().add("mode-tile-name");
        // Envuelve: "Habilidades, instantaneos y comandante" no cabe en una
        // linea, y recortarlo con puntos suspensivos esconde justo la palabra
        // que dice de que va la leccion.
        name.setWrapText(true);
        name.setMinHeight(Region.USE_PREF_SIZE);

        final Label desc = new Label(lesson.getSummary());
        desc.getStyleClass().add("mode-tile-desc");
        desc.setWrapText(true);

        final Label note = new Label(lesson.isDone()
                ? NeoText.get("tutorial.lessonDone")
                : NeoText.get("tutorial.lessonSteps", lesson.getSteps().size()));
        note.getStyleClass().add("mode-tile-note");

        final VBox box = new VBox(6, name, desc, note);
        box.getStyleClass().add("mode-tile");
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(18, 20, 16, 20));
        box.setPrefWidth(270);
        box.setMinHeight(150);
        box.setOnMouseClicked(e -> onPlay.accept(lesson));
        return box;
    }
}
