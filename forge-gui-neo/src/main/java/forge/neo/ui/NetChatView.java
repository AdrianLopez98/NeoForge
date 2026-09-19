package forge.neo.ui;

import java.util.List;
import java.util.function.Consumer;

import forge.neo.NeoText;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * El chat de una partida en red, DENTRO de la mesa.
 *
 * <p>Hasta ahora el chat solo existia en la sala de espera, y la sala no se ve
 * mientras se juega. Lo que se perdia no era la charla: era lo unico que el
 * servidor tiene para contar que alguien se ha caido y que la partida le
 * espera, y los comandos del anfitrion ({@code /skipreconnect},
 * {@code /skiptimeout}) que se escriben aqui.
 *
 * <p>Se abre con su boton, encima de "Registro", y se cierra igual que el
 * registro. Mientras escribes, las teclas no llegan a la partida: los atajos
 * no se disparan con el foco en un campo de texto ({@code NeoApp}).
 */
public class NetChatView extends VBox {

    private final VBox lines = new VBox(3);
    private final ScrollPane scroll = new ScrollPane(lines);
    private final TextField input = new TextField();

    public NetChatView(final List<String> history, final Consumer<String> send,
                       final Runnable onClose) {
        getStyleClass().addAll("dialog", "game-log");
        setSpacing(12);
        setPadding(new Insets(18, 20, 16, 20));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(NeoText.get("lobby.chat"));
        heading.getStyleClass().add("dialog-title");

        lines.setPadding(new Insets(4, 6, 4, 6));
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(520);
        scroll.setPrefViewportHeight(340);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // Pegado abajo: lo ultimo que se ha dicho es lo que importa.
        lines.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));
        for (final String h : history) {
            addLine(h);
        }

        input.setPromptText(NeoText.get("lobby.chatHint"));
        input.setOnAction(e -> {
            final String t = input.getText();
            if (t != null && !t.isBlank()) {
                send.accept(t);
            }
            input.clear();
        });
        HBox.setHgrow(input, Priority.ALWAYS);

        final Button close = new Button(NeoText.get("common.close"));
        close.getStyleClass().add("btn-secondary");
        close.setOnAction(e -> onClose.run());

        final HBox footer = new HBox(10, input, close);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, scroll, footer);
        Platform.runLater(input::requestFocus);
    }

    /** Una linea mas. Hilo de JavaFX. */
    public void addLine(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("dialog-text");
        l.setWrapText(true);
        l.setMaxWidth(500);
        l.setMinHeight(Region.USE_PREF_SIZE);
        lines.getChildren().add(l);
        while (lines.getChildren().size() > 200) {
            lines.getChildren().remove(0);
        }
    }
}
