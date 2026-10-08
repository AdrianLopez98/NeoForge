package forge.neo.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * <b>Un aviso que se va solo</b>, abajo y en medio de la ventana que se esta
 * usando, sobre cualquier pantalla.
 *
 * <p>Nacio con los enlaces (Discord, 08-10-2026: <i>"maybe also give a popup
 * that it opened a browser tab ... I thought it wasn't functioning until I went
 * to my browser and saw like 50 extra tabs open"</i>): el navegador se abre
 * detras o en otro monitor, y sin decirlo el boton parecia no hacer nada. Por
 * eso es un {@link Popup} y no un nodo de la pantalla: vale en el menu, en la
 * sala en red o en cualquier otra sin que ninguna tenga que dejarle sitio.
 *
 * <p>No se puede clicar ni tapa nada que haya que clicar: dice algo y se va.
 */
public final class Toast {

    private Toast() {
    }

    /** El que hay puesto, para quitarlo si llega otro. */
    private static Popup current;

    /** Lo pone (desde cualquier hilo). */
    public static void show(final String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            try {
                Platform.runLater(() -> show(text));
            } catch (final IllegalStateException e) {
                // sin ventana
            }
            return;
        }
        final Window owner = activeWindow();
        if (owner == null || owner.getScene() == null) {
            return;
        }
        if (current != null) {
            current.hide();
        }
        final Label label = new Label(text);
        label.getStyleClass().add("neo-toast");
        label.setMouseTransparent(true);
        final Popup popup = new Popup();
        popup.getContent().add(label);
        popup.setAutoFix(true);
        // El aspecto sale de la hoja de la ventana: el popup tiene su propia
        // escena y sin esto saldria con el estilo de fabrica de JavaFX.
        popup.getScene().getStylesheets().setAll(owner.getScene().getStylesheets());
        current = popup;
        popup.show(owner);
        label.applyCss();
        label.layout();
        final double w = label.prefWidth(-1);
        final double h = label.prefHeight(w);
        popup.setX(owner.getX() + (owner.getWidth() - w) / 2);
        popup.setY(owner.getY() + owner.getHeight() * 0.86 - h);

        final PauseTransition stay = new PauseTransition(Duration.seconds(4));
        final FadeTransition fade = new FadeTransition(Duration.millis(400), label);
        fade.setToValue(0);
        final SequentialTransition life = new SequentialTransition(stay, fade);
        life.setOnFinished(e -> {
            popup.hide();
            if (current == popup) {
                current = null;
            }
        });
        life.play();
    }

    /** Solo pruebas: el texto del aviso puesto ahora, o null. */
    public static String currentText() {
        if (current == null || !current.isShowing() || current.getContent().isEmpty()) {
            return null;
        }
        return current.getContent().get(0) instanceof Label l ? l.getText() : null;
    }

    /** La ventana con el foco, o la primera que se vea. */
    private static Window activeWindow() {
        Window shown = null;
        for (final Window w : Window.getWindows()) {
            if (!w.isShowing() || w instanceof Popup) {
                continue;
            }
            if (w.isFocused()) {
                return w;
            }
            if (shown == null) {
                shown = w;
            }
        }
        return shown;
    }
}
