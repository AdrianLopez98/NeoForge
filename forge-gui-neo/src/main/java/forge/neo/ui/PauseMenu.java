package forge.neo.ui;

import java.util.function.Consumer;

import forge.neo.NeoText;
import forge.neo.tutorial.Gesture;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * El menu de Escape: continuar, ajustes, reiniciar, salir.
 *
 * <p>Vive en una capa PROPIA, por encima de la de los dialogos del motor. Es
 * importante: el motor puede estar bloqueado esperando una respuesta ("elige
 * una carta") justo cuando pulsas Escape, y su dialogo no se puede perder. Con
 * dos capas, el menu se pone encima y al cerrarlo el dialogo sigue ahi.
 *
 * <p>Reiniciar y salir piden confirmacion porque las dos tiran la partida en
 * curso, y estas a un tecleo de distancia de ellas.
 */
public class PauseMenu extends StackPane {

    /** Que ha pedido el jugador. */
    public interface Actions {
        void resume();

        void restart();

        void quitToMenu();
    }

    private final VBox root = new VBox(10);
    private final Actions actions;
    private final SettingsPanel.Host settingsHost;

    /**
     * Quien se entera de que se ha entrado en Ajustes, si hay alguien.
     *
     * <p>Lo pone el tutorial, que tiene un paso dedicado a que el jugador vea
     * lo que hay ahi dentro. Entrar en Ajustes no llega al motor de ninguna
     * forma, asi que hay que contarlo desde aqui. Ver {@code Gesture}.
     */
    private Consumer<String> gestureSpy;

    public PauseMenu(final Actions actions, final SettingsPanel.Host settingsHost) {
        this.actions = actions;
        this.settingsHost = settingsHost;
        setAlignment(Pos.CENTER);
        showMain();
    }

    private void showMain() {
        root.getStyleClass().addAll("dialog", "pause-menu");
        root.setSpacing(10);
        root.setPadding(new Insets(26, 30, 22, 30));
        root.setAlignment(Pos.CENTER);
        root.setMaxWidth(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);
        root.getChildren().clear();

        final Label title = new Label(NeoText.get("pause.title"));
        title.getStyleClass().add("dialog-title");

        root.getChildren().addAll(title,
                item(NeoText.get("pause.resume"), "btn-primary", actions::resume),
                item(NeoText.get("common.settings"), "btn-secondary", this::showSettings),
                item(NeoText.get("pause.restart"), "btn-secondary",
                        () -> confirm(NeoText.get("pause.restart.ask"),
                                NeoText.get("pause.restart.detail"),
                                NeoText.get("pause.restart.yes"), actions::restart)),
                item(NeoText.get("pause.quit"), "btn-secondary",
                        () -> confirm(NeoText.get("pause.quit.ask"),
                                NeoText.get("pause.quit.detail"),
                                NeoText.get("pause.quit.yes"), actions::quitToMenu)));

        getChildren().setAll(root);
    }

    /** Quien se entera de los gestos de este menu. Lo usa el tutorial. */
    public void setGestureSpy(final Consumer<String> spy) {
        this.gestureSpy = spy;
    }

    private void showSettings() {
        getChildren().setAll(new SettingsPanel(settingsHost, this::showMain));
        if (gestureSpy != null) {
            gestureSpy.accept(Gesture.SETTINGS_OPEN);
        }
    }

    /** Confirmacion para lo que no tiene vuelta atras. */
    private void confirm(final String question, final String detail,
                         final String yesLabel, final Runnable onYes) {
        final VBox box = new VBox(10);
        box.getStyleClass().addAll("dialog", "pause-menu");
        box.setPadding(new Insets(26, 30, 22, 30));
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);

        final Label title = new Label(question);
        title.getStyleClass().add("dialog-title");
        final Label sub = new Label(detail);
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        sub.setMaxWidth(340);

        box.getChildren().addAll(title, sub,
                item(yesLabel, "btn-primary", onYes),
                item(NeoText.get("common.cancel"), "btn-secondary", this::showMain));
        getChildren().setAll(box);
    }

    private static Button item(final String text, final String style, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().addAll(style, "pause-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return b;
    }
}
