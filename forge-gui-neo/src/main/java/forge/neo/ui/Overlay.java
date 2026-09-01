package forge.neo.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Capa oscurecida por encima de la mesa, para los dialogos.
 *
 * <p>Cuando el motor te pide algo que no cabe en la mesa (elige 2 de estas 7
 * cartas, ordena estos triggers, reparte dano) se levanta esta capa. Oscurece
 * el fondo para que quede claro que el juego esta esperando por ti, que es lo
 * que hace Arena.
 *
 * <p>Mientras esta visible se traga los clicks: el motor esta bloqueado
 * esperando una respuesta concreta y clicar la mesa por detras no haria nada
 * util.
 */
public class Overlay extends StackPane {

    public Overlay() {
        getStyleClass().add("overlay");
        setAlignment(Pos.CENTER);
        setPadding(new Insets(40));
        setVisible(false);
        setManaged(false);
        // Traga los clicks que no vayan al dialogo.
        setPickOnBounds(true);
        setOnMouseClicked(e -> {
            e.consume();
            // SOLO cuenta como "click fuera" si de verdad se ha clicado el
            // fondo. Un click en un boton del dialogo BURBUJEA hasta aqui, y
            // sin esta comprobacion se trataba tambien como click de fondo: si
            // ese boton abria otro dialogo, el mismo click lo cerraba acto
            // seguido. Sintoma real: "cambiar edicion no hace nada".
            if (e.getTarget() == this && onBackgroundClick != null) {
                onBackgroundClick.run();
            }
        });
    }

    private Runnable onBackgroundClick;

    /**
     * Que hacer al clicar fuera del contenido.
     *
     * <p>Por defecto no hace nada: un dialogo que el motor esta esperando NO se
     * puede cerrar clicando al lado. Solo lo usa la carta ampliada, que es pura
     * consulta y se cierra a la minima.
     */
    public void setOnBackgroundClick(final Runnable handler) {
        this.onBackgroundClick = handler;
    }

    /** Muestra un dialogo centrado. */
    public void show(final Region content) {
        getChildren().setAll(content);
        setVisible(true);
        setManaged(true);
        toFront();
    }

    public void hide() {
        getChildren().clear();
        setVisible(false);
        setManaged(false);
    }

    public boolean isShowing() {
        return isVisible();
    }
}
