package forge.neo.ui;

import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Lo que el motor te pide y como contestarle.
 *
 * <p>Es el equivalente del boton grande de Arena. Dos ideas de diseno:
 *
 * <ul>
 *   <li><b>El boton dice lo que hace.</b> El motor nos manda la etiqueta en
 *       {@code updateButtons} ("OK", "Pasar", "Terminar", "No bloquear"...).
 *       Se muestra tal cual en vez de un "OK" generico: leer el boton debe
 *       bastar para saber que va a pasar.</li>
 *   <li><b>Si no puedes hacer nada, no hay boton.</b> Deshabilitado se ve
 *       apagado, no desaparece, para que la interfaz no baile.</li>
 * </ul>
 *
 * <p>La barra espaciadora activa el boton principal, que es lo que acaba usando
 * todo el mundo para pasar prioridad.
 */
public class ActionBar extends VBox {

    private final Label prompt = new Label();

    /**
     * Por que el motor no te ha dejado hacer lo que acabas de intentar.
     *
     * <p>Va <b>aqui debajo del boton</b> y no en un cartel en medio: es la
     * respuesta a un OK que acabas de pulsar, y estas mirando justo aqui.
     * Ademas, cuando esto sale casi siempre hay que clicar cartas de la mesa
     * para arreglarlo — declarar otro atacante, poner otro bloqueador — y
     * cualquier cosa nuestra por encima de la mesa lo impediria.
     */
    private final Label warning = new Label();
    private final Button ok = new Button(NeoText.get("action.ok"));
    private final Button cancel = new Button(NeoText.get("common.cancel"));

    private Runnable onOk;
    private Runnable onCancel;

    public ActionBar() {
        getStyleClass().add("action-bar");
        setSpacing(8);
        setPadding(new Insets(10, 12, 10, 12));

        warning.getStyleClass().add("action-warning");
        warning.setWrapText(true);
        warning.setMaxWidth(Double.MAX_VALUE);
        warning.setMinHeight(Region.USE_PREF_SIZE);
        warning.setVisible(false);
        warning.setManaged(false);

        prompt.getStyleClass().add("prompt-text");
        prompt.setWrapText(true);
        prompt.setMaxWidth(Double.MAX_VALUE);
        prompt.setMinHeight(Region.USE_PREF_SIZE);

        ok.getStyleClass().add("btn-primary");
        ok.setMaxWidth(Double.MAX_VALUE);
        ok.setOnAction(e -> fire(onOk));

        cancel.getStyleClass().add("btn-secondary");
        cancel.setMaxWidth(Double.MAX_VALUE);
        cancel.setOnAction(e -> fire(onCancel));

        HBox.setHgrow(ok, Priority.ALWAYS);
        HBox.setHgrow(cancel, Priority.ALWAYS);
        final HBox buttons = new HBox(8, cancel, ok);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(prompt, warning, buttons);
        setButtons(NeoText.get("action.ok"), NeoText.get("common.cancel"), false, false);
    }

    private static void fire(final Runnable r) {
        if (r != null) {
            r.run();
        }
    }

    public void setPrompt(final String text) {
        final String next = text == null ? "" : text;
        // El aviso se va cuando cambia la PREGUNTA, no en cada refresco.
        //
        // Estaba borrandose en setButtons y no se llegaba a ver: el motor
        // llama a updateButtons constantemente — en cada cambio de prioridad —
        // y justo despues de un ataque invalido vuelve a pedir atacantes, o
        // sea que el aviso duraba milisegundos. Con la pregunta como
        // referencia, se queda puesto mientras siga siendo el mismo momento.
        if (!next.equals(lastPrompt)) {
            lastPrompt = next;
            setWarning(null);
        }
        prompt.setText(next);
    }

    private String lastPrompt = "";

    /**
     * Ensenya por que no se ha podido hacer algo. Null lo quita.
     *
     * <p>No se va solo con el tiempo: se quita cuando el motor cambia de
     * pregunta ({@code setButtons}). Un aviso que desaparece a los tres
     * segundos es un aviso que te pierdes mientras miras la mesa buscando que
     * arreglar.
     */
    public void setWarning(final String text) {
        setWarning(text, true);
    }

    /**
     * @param blocking true si es "no se puede hacer eso", false si solo es
     *     "esto acaba de pasar" — el resultado de una moneda, de un dado o de
     *     una votacion. Por el mismo metodo del motor ({@code IGuiGame.message})
     *     llegan las dos cosas, y presentar un "Ganas el lanzamiento" en rojo y
     *     con cara de error es peor que no ensenyarlo.
     */
    public void setWarning(final String text, final boolean blocking) {
        final boolean show = text != null && !text.isBlank();
        warning.setText(show ? text : "");
        warning.setVisible(show);
        warning.setManaged(show);
        warning.pseudoClassStateChanged(INFO, show && !blocking);
    }

    private static final javafx.css.PseudoClass INFO =
            javafx.css.PseudoClass.getPseudoClass("info");

    /**
     * Estado de los botones tal y como lo pide el motor.
     * Las etiquetas vienen de {@code IGuiGame.updateButtons}.
     */
    public void setButtons(final String okLabel, final String cancelLabel,
                           final boolean okEnabled, final boolean cancelEnabled) {
        ok.setText(okLabel == null || okLabel.isBlank() ? NeoText.get("action.ok") : okLabel);
        cancel.setText(cancelLabel == null || cancelLabel.isBlank()
                ? NeoText.get("common.cancel") : cancelLabel);
        ok.setDisable(!okEnabled);
        cancel.setDisable(!cancelEnabled);
    }

    public void setOnOk(final Runnable r) {
        this.onOk = r;
    }

    public void setOnCancel(final Runnable r) {
        this.onCancel = r;
    }

    /** Dispara el boton principal si esta activo (lo usa la barra espaciadora). */
    public boolean pressPrimary() {
        if (!ok.isDisabled()) {
            fire(onOk);
            return true;
        }
        return false;
    }

    public boolean isOkEnabled() {
        return !ok.isDisabled();
    }
}
