package forge.neo.ui;

import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Dialogo de pregunta con botones.
 *
 * <p>Sirve para el si/no y tambien para elegir entre varias opciones de texto
 * (los modos de un hechizo modal, por ejemplo). Las opciones son botones de
 * verdad, no una lista con un "Aceptar" aparte: un click menos en algo que
 * aparece constantemente.
 */
public class ConfirmDialog extends VBox {

    public ConfirmDialog(final String title, final String message, final List<String> options,
                         final int defaultOption, final Consumer<Integer> onChoice) {
        getStyleClass().add("dialog");
        setSpacing(14);
        setPadding(new Insets(18));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);
        setAlignment(Pos.CENTER_LEFT);

        if (title != null && !title.isBlank()) {
            final Label t = new Label(title);
            t.getStyleClass().add("dialog-title");
            t.setWrapText(true);
            t.setMaxWidth(560);
            getChildren().add(t);
        }

        final Label msg = new Label(message == null ? "" : message);
        msg.getStyleClass().add("dialog-text");
        msg.setWrapText(true);
        msg.setMaxWidth(560);
        msg.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().add(wrapIfLong(msg));

        final FlowPane buttons = new FlowPane(8, 8);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.setPrefWrapLength(560);
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            final Button b = new Button(options.get(i));
            b.getStyleClass().add(i == defaultOption ? "btn-primary" : "btn-secondary");
            b.setOnAction(e -> onChoice.accept(index));
            buttons.getChildren().add(b);
            this.options.add(b);
        }
        getChildren().add(buttons);
    }

    /** Los botones, en el orden en que se pidieron. */
    private final List<Button> options = new java.util.ArrayList<>();

    /**
     * Uno de los botones, para poder marcarlo con una clase propia.
     *
     * <p>Lo necesita el piloto de pruebas: {@code --autopilot} pulsa el primer
     * {@code .btn-primary} que encuentra, y para contestar una pregunta
     * concreta hay que poder decir <b>cual</b> es su boton. Sin esto habria que
     * ir a buscarlo por el texto, que cambia con el idioma.
     */
    public Button option(final int index) {
        return index >= 0 && index < options.size() ? options.get(index) : null;
    }

    /**
     * Un mensaje largo va dentro de un visor con tope de alto.
     *
     * <p>Sin esto, el dialogo <b>crece</b> con el texto y los botones se salen
     * de la pantalla por abajo: no hay forma de cerrarlo. Salio de un mazo
     * importado con cuarenta y seis cartas que no encajaban — el motor las lista
     * todas — y el usuario se quedo encerrado: <i>"no puedo salir, ese aviso
     * ocupa todo"</i>.
     *
     * <p>El tope es la mitad de la pantalla, no una cifra en pixeles: lo que
     * tiene que caber por debajo son los botones, y eso depende de la pantalla
     * de cada uno. Y es un tope, no un tamanyo: un mensaje de dos lineas sigue
     * ocupando dos lineas.
     */
    private static Region wrapIfLong(final Label msg) {
        final javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(msg);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        sp.setMaxHeight(javafx.stage.Screen.getPrimary().getVisualBounds().getHeight() * 0.5);
        // Sin esto el visor pide su alto por defecto (400 px) aunque el texto
        // sea una linea, y todos los dialogos cortos saldrian gigantes.
        sp.setPrefHeight(Region.USE_COMPUTED_SIZE);
        sp.prefViewportHeightProperty().bind(msg.heightProperty());
        return sp;
    }
}
