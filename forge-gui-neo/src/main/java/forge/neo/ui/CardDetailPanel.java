package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Multiset;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.card.CounterType;
import forge.neo.card.CardImages;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * Panel de detalle de la carta bajo el cursor.
 *
 * <p>Arena hace esto y es la razon de que se pueda jugar con cartas pequenas en
 * la mesa: no hace falta que el texto de reglas sea legible ahi, porque tienes
 * la carta grande a un lado en cuanto pasas el raton.
 *
 * <p>Decision de diseno: <b>la carta se ve SIEMPRE a tamano completo</b>, aunque
 * no quepa. Lo que se hace scrollable es el panel entero, con el texto de
 * reglas debajo. Encoger la imagen para que cupiera todo la dejaba demasiado
 * pequena, que es justo lo contrario de para lo que sirve este panel.
 *
 * <p>El texto de reglas se muestra ademas como TEXTO, no solo dentro de la
 * imagen: es mas legible, se puede leer con cualquier resolucion y funciona
 * aunque la imagen no haya llegado a descargarse.
 */
public class CardDetailPanel extends ScrollPane {

    private final double width;
    private final ImageView big = new ImageView();
    private final Rectangle bigClip = new Rectangle();
    private final Label name = new Label();
    private final Label type = new Label();
    private final Label rules = new Label();
    /**
     * Los contadores, escritos.
     *
     * <p>Sobre la carta caben tres pastillas; aqui caben todos y con su nombre
     * completo. Es la salida para lo que la mesa recorta.
     */
    private final Label countersText = new Label();
    private final Label hint = new Label(NeoText.get("detail.hint"));
    private final VBox content = new VBox(10);

    private CardView current;

    public CardDetailPanel(final double width) {
        this.width = width;

        getStyleClass().addAll("detail-panel", "dialog-scroll");
        setFitToWidth(true);
        setPannable(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setMinWidth(width + 28);
        setPrefWidth(width + 28);

        content.setPadding(new Insets(CONTENT_PADDING));
        content.setAlignment(Pos.TOP_CENTER);
        content.setFillWidth(true);

        big.setPreserveRatio(false);
        big.setSmooth(true);
        big.setClip(bigClip);
        setImageWidth(width);

        // El ancho de la imagen NO puede ser una constante.
        //
        // Con fitToWidth(true) el ancho real del contenido lo decide el visor, y
        // el visor encoge cuando aparece la barra de scroll vertical. Fijando la
        // imagen al ancho nominal, esos pocos pixeles de barra hacian que la
        // carta se saliera y se viera CORTADA por la derecha, justo por donde
        // van los costes de mana. Se mide el visor y se ajusta.
        viewportBoundsProperty().addListener((o, was, is) -> {
            if (is != null) {
                setImageWidth(is.getWidth() - CONTENT_PADDING * 2);
            }
        });
        big.setVisible(false);
        big.setManaged(false);

        // Hueco reservado para la imagen: asi el texto no salta cuando la
        // imagen aparece o desaparece.
        final Region imageSlot = new Region();
        imageSlot.setMinHeight(0);
        imageSlot.setPrefHeight(0);

        name.getStyleClass().add("detail-name");
        type.getStyleClass().add("detail-type");
        rules.getStyleClass().add("detail-text");
        countersText.getStyleClass().add("detail-counters");
        hint.getStyleClass().add("detail-type");

        // Con fitToWidth(true) el ancho lo pone el visor. NO fijar aqui
        // prefWidth: si la etiqueta pide mas ancho que el visor y la barra
        // horizontal esta desactivada, el texto se recorta por la derecha sin
        // forma de llegar a el.
        for (final Label l : new Label[] {name, type, rules, countersText}) {
            l.setWrapText(true);
            l.setMinHeight(Region.USE_PREF_SIZE);
            l.setMaxWidth(Double.MAX_VALUE);
        }

        final VBox imageBox = new VBox(big);
        imageBox.setAlignment(Pos.CENTER);
        imageBox.setMinHeight(Region.USE_PREF_SIZE);

        content.getChildren().addAll(imageBox, name, type, countersText, rules, hint);
        setContent(content);

        // Ctrl + rueda = tamano del texto. Un planeswalker tiene parrafos y
        // parrafos; poder agrandarlos sin tocar el resto de la interfaz es mas
        // comodo que forzar la vista.
        addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                setTextZoom(textZoom + (e.getDeltaY() > 0 ? 0.1 : -0.1));
                e.consume();
            }
        });
        applyTextZoom();
    }

    /** Factor de tamano del texto de reglas. */
    private double textZoom = 1.15;

    public void setTextZoom(final double z) {
        this.textZoom = Math.max(0.8, Math.min(2.4, z));
        applyTextZoom();
    }

    public double getTextZoom() {
        return textZoom;
    }

    private void applyTextZoom() {
        rules.setStyle("-fx-font-size: " + String.format(java.util.Locale.ROOT, "%.2f", textZoom) + "em;");
        name.setStyle("-fx-font-size: " + String.format(java.util.Locale.ROOT, "%.2f", 1.15 * textZoom) + "em;");
        type.setStyle("-fx-font-size: " + String.format(java.util.Locale.ROOT, "%.2f", 0.92 * textZoom) + "em;");
    }

    public void show(final CardView card) {
        this.current = card;
        refresh();
        // Al cambiar de carta, volver arriba: si no, te quedas mirando el
        // final del texto de la carta anterior.
        setVvalue(0);
    }

    public void refresh() {
        if (current == null) {
            return;
        }
        final CardStateView st = current.getCurrentState();
        if (st == null) {
            return;
        }
        hint.setVisible(false);
        hint.setManaged(false);

        final String cost = st.getManaCost() == null ? "" : st.getManaCost().toString();
        // En el idioma elegido: Forge trae los nombres y el texto de las cartas
        // traducidos y hay que PEDIRLOS. Ver CardText. Este panel es donde mas
        // se nota, porque es donde se lee de verdad.
        name.setText(forge.neo.card.CardText.nameOf(st)
                + (cost.isEmpty() ? "" : "   " + cost));
        type.setText(forge.neo.card.CardText.typeOf(st));

        String text = forge.neo.card.CardText.rulesOf(st);
        if (st.isCreature()) {
            final String pt = st.getPower() + " / " + st.getToughness();
            text = (text == null ? "" : text + "\n\n") + pt;
        }
        rules.setText(text == null ? "" : text.replace("\\n", "\n"));

        final Image img = CardImages.get(st.getImageKey());
        big.setImage(img);
        big.setVisible(img != null);
        big.setManaged(img != null);
    }

    /** "3 +1/+1, 1 STUN". Vacio si la carta no lleva ninguno. */
    private static String countersOf(final CardView card) {
        final Multiset<CounterType> all = card == null ? null : card.getCounters();
        if (all == null || all.isEmpty()) {
            return "";
        }
        final List<String> parts = new ArrayList<>();
        for (final CounterType t : all.elementSet()) {
            parts.add(all.count(t) + " " + t.getCounterOnCardDisplayName());
        }
        java.util.Collections.sort(parts);
        return String.join(" · ", parts);
    }

    /** La carta que se esta ensenyando, para poder ampliarla desde fuera. */
    public CardView getCurrent() {
        return current;
    }

    public double getCardWidth() {
        return width;
    }

    /** Margen interior del contenido, a cada lado. */
    private static final double CONTENT_PADDING = 14;

    /** Ajusta la imagen al ancho que de verdad hay disponible. */
    private void setImageWidth(final double w) {
        final double clamped = Math.max(60, w);
        final double h = clamped * CardNode.ASPECT;
        big.setFitWidth(clamped);
        big.setFitHeight(h);
        bigClip.setWidth(clamped);
        bigClip.setHeight(h);
        bigClip.setArcWidth(clamped * 0.1);
        bigClip.setArcHeight(clamped * 0.1);
    }
}
