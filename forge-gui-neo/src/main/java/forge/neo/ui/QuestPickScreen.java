package forge.neo.ui;

import java.util.List;
import java.util.function.Consumer;

import forge.neo.NeoText;
import forge.neo.quest.NeoQuest;
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
 * Con que aventura quieres seguir.
 *
 * <p>Salio de jugar: el menu decia <i>"3 aventuras guardadas"</i> y al entrar
 * se abria <b>una</b>, la ultima, sin preguntar. Y la pregunta importa porque
 * se puede tener una aventura de Commander y otra de Estandar a la vez: sin
 * elegir, no hay forma de llegar a la otra.
 *
 * <p>Cada fila dice <b>de que modalidad es</b> — que es el dato por el que se
 * elige — ademas del nivel, el record y los creditos. Todo eso se lee del
 * fichero sin abrir la partida ({@link NeoQuest#saveInfos()}).
 *
 * <p>Y cada una lleva su papelera. Borrar una aventura <b>no tiene vuelta
 * atras</b>, asi que se pregunta antes, con el nombre delante.
 */
public class QuestPickScreen extends BorderPane {

    /** Que ha elegido el jugador. */
    public interface Actions {
        /** Seguir con esta. */
        void load(String name);

        /** Empezar una nueva. */
        void create();

        void back();
    }

    private final Actions actions;
    private final Overlay overlay = new Overlay();
    private final VBox list = new VBox(8);

    public QuestPickScreen(final Actions actions) {
        this.actions = actions;
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("questPick.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("questPick.subtitle"));
        sub.getStyleClass().add("home-subtitle");

        final VBox titles = new VBox(2, title, sub);
        titles.setAlignment(Pos.CENTER);
        titles.setPadding(new Insets(26, 20, 12, 20));
        setTop(titles);

        list.setAlignment(Pos.TOP_CENTER);
        list.setPadding(new Insets(4, 30, 10, 30));

        final ScrollPane scroll = new ScrollPane(list);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        final javafx.scene.layout.StackPane centre =
                new javafx.scene.layout.StackPane(scroll, overlay);
        setCenter(centre);

        final Button create = new Button(NeoText.get("quest.new"));
        create.getStyleClass().addAll("btn-primary", "btn-play");
        create.setMinWidth(Region.USE_PREF_SIZE);
        create.setOnAction(e -> actions.create());

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(12, back, gap, create);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("home-footer");
        footer.setPadding(new Insets(14, 30, 22, 30));
        setBottom(footer);

        refresh();
    }

    /** Vuelve a leer las partidas guardadas. */
    private void refresh() {
        list.getChildren().clear();
        final List<NeoQuest.SaveInfo> saves = NeoQuest.saveInfos();
        if (saves.isEmpty()) {
            final Label empty = new Label(NeoText.get("questPick.empty"));
            empty.getStyleClass().add("home-subtitle");
            list.getChildren().add(empty);
            return;
        }
        for (final NeoQuest.SaveInfo save : saves) {
            list.getChildren().add(row(save));
        }
    }

    /** Una aventura guardada. */
    private Region row(final NeoQuest.SaveInfo save) {
        final Label name = new Label(save.getName());
        name.getStyleClass().add("quest-deck-name");

        final Label detail = new Label(save.isReadable()
                ? NeoText.get("questPick.detail",
                        NeoText.get(save.isCommander()
                                ? "quest.mode.COMMANDER.label" : "quest.mode.ESTANDAR.label"),
                        save.getLevel(), save.getWins(), save.getLosses(), save.getCredits())
                : NeoText.get("questPick.broken"));
        detail.getStyleClass().add(save.isReadable() ? "home-subtitle" : "quest-problem");

        final VBox text = new VBox(2, name, detail);
        text.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        text.setMaxWidth(Double.MAX_VALUE);

        final Button play = new Button(NeoText.get("questPick.play"));
        play.getStyleClass().add(save.isReadable() ? "btn-primary" : "btn-secondary");
        play.setMinWidth(Region.USE_PREF_SIZE);
        play.setDisable(!save.isReadable());
        play.setOnAction(e -> actions.load(save.getName()));

        // La papelera. Va apagada y solo se enciende al pasar por encima de la
        // fila: es lo unico de esta pantalla que no se puede deshacer.
        final Button bin = new Button(NeoText.get("questPick.delete"));
        bin.getStyleClass().add("btn-danger");
        bin.setMinWidth(Region.USE_PREF_SIZE);
        bin.setOnAction(e -> confirmDelete(save.getName()));

        final HBox row = new HBox(14, text, play, bin);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("quest-save-row");
        row.setMaxWidth(720);

        // Toda la fila vale para entrar, que es el gesto obvio; la papelera
        // consume su click para que no se cuele.
        if (save.isReadable()) {
            row.setCursor(javafx.scene.Cursor.HAND);
            row.setOnMouseClicked(e -> actions.load(save.getName()));
        }
        bin.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                javafx.event.Event::consume);
        return row;
    }

    /**
     * Pregunta antes de borrar, con el nombre delante.
     *
     * <p>Aqui se pierden las cartas, los creditos y el progreso de esa
     * aventura. Lo que no se puede deshacer hay que evitar que pase por un
     * click de mas.
     */
    private void confirmDelete(final String name) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(
                NeoText.get("questPick.delete.ask"),
                NeoText.get("questPick.delete.detail", name),
                List.of(NeoText.get("questPick.delete.yes"), NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice != null && choice == 0) {
                        NeoQuest.delete(name);
                        refresh();
                    }
                }));
    }

    /** Para que la pantalla se pueda montar desde fuera sin partida. */
    public static QuestPickScreen of(final Consumer<String> onLoad, final Runnable onCreate,
                                     final Runnable onBack) {
        return new QuestPickScreen(new Actions() {
            @Override
            public void load(final String name) {
                onLoad.accept(name);
            }

            @Override
            public void create() {
                onCreate.run();
            }

            @Override
            public void back() {
                onBack.run();
            }
        });
    }
}
