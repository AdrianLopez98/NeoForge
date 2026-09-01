package forge.neo.ui;

import forge.game.card.CardView;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Lo que esta pasando ahora mismo, en el centro y con la carta grande.
 *
 * <p>Copia a Arena: cuando un hechizo o un disparo esta esperando en el stack,
 * se ensenya <b>en mitad de la pantalla, con su arte a tamanyo de lectura</b>.
 * Cuando el juego te para, lo primero que necesitas saber es de que carta viene,
 * y un nombre suelto en una esquina no basta.
 *
 * <h3>Por que casi nunca lleva botones</h3>
 *
 * <p>La primera version repetia aqui los botones del motor (OK / Cancelar), y
 * salio mal jugando: <i>"doy a OK para cerrarlo y lo que hago es pasar turno"</i>.
 * Tenia razon — ese OK no cerraba nada, era el OK de verdad del motor.
 *
 * <p>Asi que se separan las dos cosas, que es ademas lo que hace Arena:
 *
 * <ul>
 *   <li><b>El centro informa</b> ({@link #showInfo}): la carta y el texto, sin
 *       un solo boton. Nada que pulsar, nada que confundir.</li>
 *   <li><b>La barra de la derecha actua</b>: ahi siguen OK y Cancelar, en el
 *       sitio fijo de siempre.</li>
 * </ul>
 *
 * <p>La unica excepcion es {@link #showAlert}: el aviso de "esto te acaba de
 * pasar", donde el motor esta parado esperando solo eso y su boton no hace nada
 * mas que seguir.
 *
 * <p>No es un dialogo: deja pasar el raton, porque debajo hay cartas que hay que
 * poder clicar para contestar ("elige una criatura para sacrificar").
 */
public class PromptBanner extends VBox {

    private final Label text = new Label();
    /**
     * La carta grande del cartel.
     *
     * <p>Es el unico nodo de toda la interfaz que va cambiando de carta, asi
     * que es el unico que borra el arte viejo mientras llega el nuevo: aqui lo
     * que se quedaria puesto seria el arte del hechizo ANTERIOR, al lado del
     * texto del nuevo. Ver {@code CardNode.setBlankWhileLoading}.
     */
    private final CardNode card = new CardNode(120);
    private final HBox body = new HBox(16);
    private final Button ok = new Button(NeoText.get("banner.understood"));
    private final HBox buttons = new HBox(10, ok);
    private final VBox column = new VBox(10);

    private Runnable onOk;

    public PromptBanner(final double cardWidth) {
        card.setBlankWhileLoading(true);
        getStyleClass().add("prompt-banner");
        setSpacing(10);
        setPadding(new Insets(16, 22, 16, 22));
        setAlignment(Pos.CENTER);
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        // Vista de LECTURA: la carta nunca sale girada aunque este tapada, que
        // es justo cuando quieres leerla.
        card.setCardWidth(cardWidth);
        card.setRotationEnabled(false);
        card.setBadgesVisible(false);
        card.setHoverEnabled(false);

        text.getStyleClass().add("prompt-banner-text");
        text.setWrapText(true);
        text.setMaxWidth(420);
        text.setMinHeight(Region.USE_PREF_SIZE);

        ok.getStyleClass().addAll("btn-primary", "prompt-banner-btn");
        ok.setOnAction(e -> {
            if (onOk != null) {
                onOk.run();
            }
        });
        buttons.setAlignment(Pos.CENTER);

        column.setAlignment(Pos.CENTER_LEFT);
        body.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(body);

        // Un click en el cartel lo quita de en medio.
        //
        // Es solo informativo — el mismo texto esta en la barra de la derecha —
        // asi que tiene que haber forma de apartarlo sin depender de que
        // nosotros acertemos con cuando estorba. Es la valvula de escape.
        setOnMouseClicked(e -> {
            dismiss();
            e.consume();
        });

        setVisible(false);
        setManaged(false);
    }

    /**
     * Ensenya lo que esta pasando. <b>Sin botones.</b>
     *
     * <p>Es puro cartel: informa de que hay en el stack o de que te estan
     * preguntando. Para contestar se usa la barra de la derecha o se clica una
     * carta de la mesa.
     */
    public void showInfo(final CardView source, final String message) {
        if (message == null || message.isBlank()) {
            hide();
            return;
        }
        onOk = null;
        layoutContent(source, message, false);
    }

    /**
     * Aviso con un solo boton para seguir.
     *
     * <p>Solo para "esto te acaba de pasar": ahi el motor esta detenido
     * esperando exactamente esto, asi que el boton no puede confundirse con una
     * jugada.
     */
    public void showAlert(final String message, final String okLabel, final Runnable onContinue) {
        if (message == null || message.isBlank()) {
            hide();
            return;
        }
        onOk = onContinue;
        ok.setText(okLabel == null || okLabel.isBlank()
                ? NeoText.get("banner.understood") : okLabel);
        layoutContent(null, message, true);
    }

    /**
     * true mientras hay un aviso <b>con boton</b> delante.
     *
     * <p>Hay que preguntarlo antes de repintar el cartel desde ningun sitio.
     * Cuando esto es cierto, el hilo del MOTOR esta detenido esperando ese
     * boton y nada mas: pisar el cartel se lleva el boton por delante y la
     * partida se queda muerta <b>para siempre</b>, sin excepcion, sin aviso y
     * con una mesa que se ve perfectamente normal.
     *
     * <p>Salio en cuanto los eventos del motor empezaron a llegar de verdad en
     * una partida local: el espejo del stack se repinta con cada evento, y el
     * evento que levanta el aviso trae otro detras.
     */
    public boolean isAlerting() {
        return onOk != null;
    }

    private void layoutContent(final CardView source, final String message,
                               final boolean withButton) {
        text.setText(message);

        column.getChildren().setAll(text);
        if (withButton) {
            column.getChildren().add(buttons);
        }

        body.getChildren().clear();
        if (source != null) {
            card.setCard(source);
            body.getChildren().add(card);
            text.setAlignment(Pos.CENTER_LEFT);
            text.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
            column.setAlignment(Pos.CENTER_LEFT);
        } else {
            text.setAlignment(Pos.CENTER);
            text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            column.setAlignment(Pos.CENTER);
        }
        body.getChildren().add(column);

        setVisible(true);
        setManaged(true);
        // Nada de toFront(): su sitio en el orden de hijos ya es el correcto,
        // justo DEBAJO de las capas de dialogo. Con toFront() se ponia por
        // delante de todo y tapaba lo que el motor estaba ensenyando — salio
        // jugando: un rival busco en su biblioteca y el cartel del hechizo
        // tapaba el panel que decia que carta se habia llevado.
    }

    public void hide() {
        onOk = null;
        setVisible(false);
        setManaged(false);
    }

    /**
     * Lo cierra el jugador.
     *
     * <p>Distinto de {@link #hide()}: aqui se avisa a quien lo mostro para que
     * NO lo vuelva a levantar con lo mismo. Sin eso, el siguiente aviso del
     * motor lo pintaria otra vez y cerrarlo no serviria de nada.
     *
     * <p>El aviso de "esto te acaba de pasar" no se puede descartar asi: ahi el
     * motor esta detenido esperando su boton.
     */
    private void dismiss() {
        if (onOk != null) {
            return;
        }
        hide();
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    private Runnable onDismiss;

    public void setOnDismiss(final Runnable r) {
        this.onDismiss = r;
    }

    /** Lo aparta un click dado en otro sitio de la mesa. */
    public void fireDismiss() {
        dismiss();
    }

    /** Vuelve a leer la imagen si ha llegado de Scryfall. */
    public void refreshArt() {
        card.refresh();
    }
}
