package forge.neo.ui;

import forge.neo.NeoText;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Pedir texto: el nombre de un mazo, o una decklist entera pegada.
 *
 * <p>Son el mismo dialogo con dos tamanyos porque son la misma pregunta. La
 * version grande es la que hace util el importador: se copia el mazo de
 * Moxfield, se pega aqui y ya esta — que es como la gente monta mazos hoy, no
 * carta a carta.
 */
public final class TextDialog {

    private TextDialog() {
    }

    /** Una linea: nombres de mazo y poco mas. */
    public static Region line(final String title, final String prompt, final String initial,
                              final Consumer<String> onAccept, final Runnable onCancel) {
        final TextField field = new TextField(initial == null ? "" : initial);
        field.getStyleClass().add("text-input");
        field.setPrefColumnCount(28);
        // Enter acepta: escribir un nombre y tener que ir al raton molesta.
        field.setOnAction(e -> accept(field.getText(), onAccept));

        final Region box = wrap(title, prompt, field,
                () -> accept(field.getText(), onAccept), onCancel, NeoText.get("common.accept"));
        javafx.application.Platform.runLater(field::requestFocus);
        return box;
    }

    /**
     * Un numero, y solo un numero.
     *
     * <p>Existe aparte de {@link #line} porque el motor <b>no valida, vuelve a
     * preguntar</b>: {@code AbstractGuiGame.getInteger} llama a
     * {@code showInputDialog} dentro de un {@code while (true)} y solo sale con
     * un numero o con una cancelacion. Devolver cualquier otra cosa — una
     * cadena vacia, un "12 " con espacio — deja la partida <b>girando para
     * siempre</b>, sin excepcion y sin aviso. Por eso aqui el campo no deja
     * escribir nada que no sea un digito y Aceptar esta apagado mientras este
     * vacio.
     */
    public static Region number(final String title, final String prompt, final String initial,
                                final Consumer<String> onAccept, final Runnable onCancel) {
        final TextField field = new TextField(initial == null ? "" : initial);
        field.getStyleClass().add("text-input");
        field.setPrefColumnCount(8);
        field.setTextFormatter(new javafx.scene.control.TextFormatter<>(
                c -> c.getControlNewText().matches("\\d*") ? c : null));
        field.setOnAction(e -> {
            if (!field.getText().isEmpty()) {
                accept(field.getText(), onAccept);
            }
        });

        final Region box = wrap(title, prompt, field,
                () -> accept(field.getText(), onAccept), onCancel, NeoText.get("common.accept"),
                field.textProperty().isEmpty());
        javafx.application.Platform.runLater(field::requestFocus);
        return box;
    }

    /** Un bloque: para pegar o copiar una decklist entera. */
    public static Region block(final String title, final String prompt, final String initial,
                               final String acceptLabel,
                               final Consumer<String> onAccept, final Runnable onCancel) {
        final TextArea area = new TextArea(initial == null ? "" : initial);
        area.getStyleClass().add("text-input");
        area.setPrefRowCount(16);
        area.setPrefColumnCount(46);
        area.setWrapText(false);

        final Region box = wrap(title, prompt, area,
                () -> accept(area.getText(), onAccept), onCancel, acceptLabel);
        javafx.application.Platform.runLater(() -> {
            area.requestFocus();
            area.selectAll();
        });
        return box;
    }

    private static void accept(final String text, final Consumer<String> onAccept) {
        if (onAccept != null) {
            onAccept.accept(text);
        }
    }

    private static Region wrap(final String title, final String prompt, final Region input,
                               final Runnable onAccept, final Runnable onCancel,
                               final String acceptLabel) {
        return wrap(title, prompt, input, onAccept, onCancel, acceptLabel, null);
    }

    private static Region wrap(final String title, final String prompt, final Region input,
                               final Runnable onAccept, final Runnable onCancel,
                               final String acceptLabel,
                               final javafx.beans.value.ObservableValue<Boolean> disableAccept) {
        final Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");

        final Label hint = new Label(prompt);
        hint.getStyleClass().add("dialog-text");
        hint.setWrapText(true);
        hint.setMaxWidth(460);

        final Button ok = new Button(acceptLabel);
        ok.getStyleClass().add("btn-primary");
        ok.setOnAction(e -> onAccept.run());
        if (disableAccept != null) {
            ok.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                    disableAccept::getValue, disableAccept));
        }

        final Button cancel = new Button(NeoText.get("common.cancel"));
        cancel.getStyleClass().add("btn-secondary");
        cancel.setOnAction(e -> onCancel.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, gap, cancel, ok);
        footer.setAlignment(Pos.CENTER_RIGHT);

        final VBox box = new VBox(12, heading, hint, input, footer);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(22, 26, 20, 26));
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }
}
