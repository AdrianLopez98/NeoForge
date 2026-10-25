package forge.neo.ui;

import java.util.function.Consumer;

import forge.gamemodes.limited.CustomLimited;
import forge.neo.NeoText;
import forge.neo.draft.CubeCobra;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Los cubos en las pantallas de draft y de sellado: la casilla de un cubo y
 * la barra de <b>importar de CubeCobra</b> (Discord, 25-10-2026). Las dos
 * pantallas hacen la misma pregunta, asi que se contesta igual.
 *
 * <p>La descarga va en un hilo propio — {@link CubeCobra#download} hace red y
 * puede tardar hasta su tope — y la respuesta vuelve con {@code runLater}.
 */
final class CubeView {

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");

    private CubeView() {
    }

    /**
     * El enlace o ID, el boton y lo que ha pasado. {@code onImported} recibe
     * el nombre con el que sale el cubo en la lista, ya en el hilo de la
     * interfaz.
     */
    static Region importBar(final Consumer<String> onImported) {
        final TextField link = new TextField();
        link.setPromptText(NeoText.get("cube.import.prompt"));
        link.getStyleClass().add("text-input");
        link.setPrefColumnCount(28);
        link.setId("cube-link");

        final Button go = new Button(NeoText.get("cube.import.go"));
        go.getStyleClass().add("btn-secondary");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.setId("cube-import");

        final Label status = new Label(NeoText.get("cube.import.about"));
        status.getStyleClass().add("home-subtitle");
        status.setWrapText(true);

        final Runnable run = () -> {
            final String text = link.getText();
            if (CubeCobra.parseId(text) == null) {
                status.setText(NeoText.get("cube.import.badId"));
                return;
            }
            go.setDisable(true);
            link.setDisable(true);
            status.setText(NeoText.get("cube.import.busy"));
            final Thread t = new Thread(() -> {
                String name = null;
                String error = null;
                try {
                    name = CubeCobra.download(text);
                } catch (final CubeCobra.ImportException e) {
                    System.err.println("[neo] CubeCobra: " + e.getMessage());
                    error = switch (e.reason) {
                        case BAD_ID -> NeoText.get("cube.import.badId");
                        case NOT_FOUND, EMPTY -> NeoText.get("cube.import.notFound");
                        case NETWORK -> NeoText.get("cube.import.network");
                    };
                }
                final String done = name;
                final String failed = error;
                Platform.runLater(() -> {
                    go.setDisable(false);
                    link.setDisable(false);
                    if (done != null) {
                        link.clear();
                        status.setText(NeoText.get("cube.import.done", done));
                        onImported.accept(done);
                    } else {
                        status.setText(failed);
                    }
                });
            }, "neo-cubecobra");
            t.setDaemon(true);
            t.start();
        };
        go.setOnAction(e -> run.run());
        link.setOnAction(e -> run.run());

        // Solo pruebas: -Dneo.cube.import=<id> lo escribe y pulsa Importar (la guía de pruebas).
        final String test = System.getProperty("neo.cube.import");
        if (test != null) {
            Platform.runLater(() -> {
                link.setText(test);
                run.run();
            });
        }

        final HBox row = new HBox(8, link, go);
        row.setAlignment(Pos.CENTER_LEFT);
        return new VBox(4, row, status);
    }

    /**
     * La casilla de un cubo: cuantas cartas y su nombre. Los importados llevan
     * la etiqueta "CubeCobra" y un "Quitar" que pide un segundo clic.
     */
    static Region tile(final CustomLimited cube, final boolean picked,
                       final Runnable onPick, final Runnable onDeleted) {
        final Label name = new Label(cube.getName());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(UiScale.px(190));
        name.setMinHeight(Region.USE_PREF_SIZE);

        final Label count = new Label(NeoText.get("draftNew.cubeSize", cube.getCardPool().countAll()));
        count.getStyleClass().add("set-code");

        final VBox box = new VBox(2);
        if (CubeCobra.isImported(cube)) {
            final Label tag = new Label(NeoText.get("cube.tag"));
            tag.getStyleClass().add("home-subtitle");
            final Button delete = new Button(NeoText.get("cube.delete"));
            delete.getStyleClass().add("btn-secondary");
            delete.setMinWidth(Region.USE_PREF_SIZE);
            // Un segundo clic para quitarlo: volver a tenerlo pide internet.
            delete.setOnAction(e -> {
                if (!"sure".equals(delete.getUserData())) {
                    delete.setUserData("sure");
                    delete.setText(NeoText.get("cube.delete.sure"));
                    return;
                }
                CubeCobra.delete(cube);
                onDeleted.run();
            });
            final Region gap = new Region();
            HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
            final HBox top = new HBox(6, count, tag, gap, delete);
            top.setAlignment(Pos.CENTER_LEFT);
            box.getChildren().addAll(top, name);
        } else {
            box.getChildren().addAll(count, name);
        }
        box.getStyleClass().add("set-tile");
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setPrefWidth(UiScale.px(210));
        box.pseudoClassStateChanged(PICKED, picked);
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                onPick.run();
            }
        });
        return box;
    }
}
