package forge.neo.ui;

import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
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
    private final HBox buttons;

    /**
     * <b>Los jugadores entre los que hay que elegir</b>, un boton por cada uno
     * (Discord, 08-10-2026, <i>Curator of Destinies</i>). Cuando el motor
     * espera un JUGADOR — "un rival elige una de las pilas", y hay tres — solo
     * se contestaba clicando un retrato, y nada lo decia: el prompt era
     * "Chooser:" y los botones estaban apagados. Van aqui, entre la pregunta y
     * OK, porque es donde se mira cuando no se sabe que hacer (principio 1:
     * lo que contesta al motor vive en esta barra). El retrato sigue valiendo.
     * Ver {@code forge.neo.match.PlayerPick}.
     */
    private final FlowPane players = new FlowPane(6, 6);

    /**
     * <b>"Resolverlo todo (6)"</b> (Discord, 08-10-2026, Munkster: seis disparos
     * iguales, seis OK). Sale solo cuando tienes la prioridad con dos o mas
     * cosas en el stack, y pasa la prioridad hasta que se vacie. Va encima de
     * OK y no a su lado: tres botones en la columna no caben, y este no es
     * otra forma de decir OK, es otra pregunta. Ver
     * {@code NeoMatchUI.resolveStack}.
     */
    private final Button resolveAll = new Button();
    private Runnable onResolveAll;

    /** Un jugador elegible: su nombre, si ya esta elegido y que hacer al clicarlo. */
    public record PlayerChoice(String name, boolean picked, Runnable onPick) {
    }

    /**
     * En una fila (la columna plegada, itch.io 04-10-2026): el texto a la
     * izquierda y los botones a la derecha, para caber en la barra del
     * jugador. En columna, lo de siempre: texto arriba y botones debajo.
     */
    private boolean inline;
    private final VBox textColumn = new VBox(4);
    private final HBox inlineRow = new HBox(12);

    private static final javafx.css.PseudoClass INLINE =
            javafx.css.PseudoClass.getPseudoClass("inline");

    public void setInline(final boolean on) {
        if (inline == on) {
            return;
        }
        inline = on;
        pseudoClassStateChanged(INLINE, on);
        if (on) {
            textColumn.getChildren().setAll(prompt, warning, players, resolveAll);
            textColumn.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(textColumn, Priority.ALWAYS);
            textColumn.setMinWidth(0);
            // Los botones no se encogen: un "Fin de tu..." cortado no se lee.
            buttons.setMinWidth(Region.USE_PREF_SIZE);
            HBox.setHgrow(buttons, Priority.NEVER);
            inlineRow.setAlignment(Pos.CENTER_LEFT);
            inlineRow.getChildren().setAll(textColumn, buttons);
            getChildren().setAll(inlineRow);
        } else {
            inlineRow.getChildren().clear();
            textColumn.getChildren().clear();
            buttons.setMinWidth(Region.USE_COMPUTED_SIZE);
            getChildren().setAll(prompt, warning, players, resolveAll, buttons);
        }
        requestLayout();
    }

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
        buttons = new HBox(8, cancel, ok);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        players.getStyleClass().add("player-choices");
        players.setVisible(false);
        players.setManaged(false);

        resolveAll.getStyleClass().addAll("btn-secondary", "resolve-all");
        resolveAll.setMaxWidth(Double.MAX_VALUE);
        resolveAll.setVisible(false);
        resolveAll.setManaged(false);
        resolveAll.setOnAction(e -> {
            // Fuera en el acto, como los botones de jugador: la pregunta ya
            // esta contestada, y un segundo clic iria contra la siguiente.
            // La accion se coge ANTES: quitar el boton la borra.
            final Runnable action = onResolveAll;
            setResolveAll(null, null, null);
            fire(action);
        });

        getChildren().addAll(prompt, warning, players, resolveAll, buttons);
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

    /**
     * Pone (o quita, con una lista vacia) los botones de jugador. Al clicar uno
     * se quitan todos en el acto: la pregunta se ha contestado, y un boton
     * que se queda puesto hasta el siguiente aviso del motor se puede pulsar
     * contra la pregunta siguiente.
     */
    public void setPlayerChoices(final java.util.List<PlayerChoice> choices) {
        players.getChildren().clear();
        final boolean show = choices != null && !choices.isEmpty();
        if (show) {
            for (final PlayerChoice c : choices) {
                final Button b = new Button(c.name());
                b.getStyleClass().addAll("btn-secondary", "player-choice");
                b.pseudoClassStateChanged(SELECTED, c.picked());
                b.setOnAction(e -> {
                    setPlayerChoices(null);
                    fire(c.onPick());
                });
                players.getChildren().add(b);
            }
        }
        players.setVisible(show);
        players.setManaged(show);
    }

    /**
     * Pone el boton de "Resolverlo todo", o lo quita con {@code label} null.
     */
    public void setResolveAll(final String label, final String tip, final Runnable onClick) {
        final boolean show = label != null && !label.isEmpty();
        resolveAll.setText(show ? label : "");
        if (!show || tip == null) {
            resolveAll.setTooltip(null);
        } else if (resolveAll.getTooltip() == null || !tip.equals(resolveAll.getTooltip().getText())) {
            resolveAll.setTooltip(new javafx.scene.control.Tooltip(tip));
        }
        onResolveAll = show ? onClick : null;
        resolveAll.setVisible(show);
        resolveAll.setManaged(show);
    }

    /** Solo pruebas: el texto del boton de "Resolverlo todo", o null si no esta. */
    public String resolveAllText() {
        return resolveAll.isVisible() ? resolveAll.getText() : null;
    }

    /** Solo pruebas: pulsarlo, como el raton. */
    public boolean clickResolveAll() {
        if (!resolveAll.isVisible()) {
            return false;
        }
        resolveAll.fire();
        return true;
    }

    /** Solo pruebas: los nombres de los botones de jugador puestos ahora. */
    public java.util.List<String> playerChoiceNames() {
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (final javafx.scene.Node n : players.getChildren()) {
            if (n instanceof Button b) {
                out.add(b.getText());
            }
        }
        return out;
    }

    /** Solo pruebas: clica el boton de ese jugador, como el raton. */
    public boolean clickPlayerChoice(final String name) {
        for (final javafx.scene.Node n : players.getChildren()) {
            if (n instanceof Button b && b.getText().equals(name)) {
                b.fire();
                return true;
            }
        }
        return false;
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

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

    /** Solo pruebas: el boton de la izquierda (Cancelar), si esta activo. */
    public boolean pressSecondary() {
        if (!cancel.isDisabled()) {
            fire(onCancel);
            return true;
        }
        return false;
    }

    public boolean isOkEnabled() {
        return !ok.isDisabled();
    }
}
