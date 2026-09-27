package forge.neo.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * La barra de abajo de las pantallas que no tienen una propia: lo que se
 * pase a la izquierda y <b>Volver a la derecha</b>.
 *
 * <p>Informe de itch.io (27-09-2026): el boton de volver estaba en cinco
 * sitios distintos segun la pantalla — abajo a la izquierda, abajo a la
 * derecha, centrado, arriba a la derecha... Ahora vive siempre en el mismo:
 * abajo a la derecha, y si hay accion principal, justo a su izquierda
 * (las notas de diseño, principio 12). Las pantallas que ya tienen pie lo colocan
 * ahi a mano; esta es para las que tenian el boton en la cabecera.
 */
public final class BackBar {

    private BackBar() {
    }

    /**
     * @param back la accion de volver (el boton se hace aqui, con su texto)
     * @param left lo que va a la izquierda, si hay algo
     */
    public static HBox of(final Runnable back, final Node... left) {
        final Button b = new Button(forge.neo.NeoText.get("common.back"));
        b.getStyleClass().add("btn-secondary");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> back.run());
        return of(b, left);
    }

    /** Lo mismo con un boton ya hecho (por si la pantalla lo necesita a mano). */
    public static HBox of(final Button back, final Node... left) {
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox bar = new HBox(10);
        bar.getChildren().addAll(left);
        bar.getChildren().addAll(gap, back);
        bar.getStyleClass().add("home-footer");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 30, 14, 30));
        return bar;
    }
}
