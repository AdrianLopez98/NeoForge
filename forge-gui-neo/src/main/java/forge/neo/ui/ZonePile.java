package forge.neo.ui;

import forge.neo.card.CardNode;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Una pila de zona en la mesa: cementerio o exilio.
 *
 * <p>Se dibuja como un monton de cartas boca abajo con el numero encima, en el
 * borde del campo de batalla. Estan siempre a la vista pero no estorban, que es
 * lo que hace falta: en Commander consultas el cementerio constantemente y
 * tener que abrir un menu para saber cuantas cartas hay es un incordio.
 *
 * <p><b>Se clican</b> para abrir el visor de la zona. Salio de jugar: el
 * contador pequenyo de la barra del jugador ya se clicaba, pero estas pilas —
 * que son lo que de verdad se mira, porque estan en la mesa y son grandes — no
 * hacian nada. Un monton de cartas dibujado en el campo de batalla pide que lo
 * cliquen; si no responde, parece roto.
 */
public class ZonePile extends VBox {

    private final Rectangle back = new Rectangle();
    private final Rectangle shadow = new Rectangle();
    private final Label count = new Label("0");
    private final Label caption;

    private double cardWidth;

    public ZonePile(final String captionText, final double cardWidth) {
        getStyleClass().add("zone-pile");
        setAlignment(Pos.CENTER);
        setSpacing(2);
        setOnMouseClicked(e -> {
            // Solo el boton izquierdo: el derecho es "ampliar la carta" en toda
            // la mesa y aqui no hay ninguna que ampliar.
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && onClick != null) {
                e.consume();
                onClick.run();
            }
        });

        shadow.setFill(Color.rgb(14, 18, 24));
        shadow.setStroke(Color.rgb(0, 0, 0, 0.7));
        shadow.setStrokeWidth(1);

        back.getStyleClass().add("zone-pile-face");

        count.getStyleClass().add("zone-pile-count");
        count.setAlignment(Pos.CENTER);

        caption = new Label(captionText);
        caption.getStyleClass().add("caption");

        final StackPane pile = new StackPane(shadow, back, count);
        pile.setAlignment(Pos.CENTER);
        getChildren().addAll(pile, caption);

        setCardWidth(cardWidth);
    }

    /**
     * Que hacer al clicarla.
     *
     * <p>Con {@code null} deja de responder y de senyalarse: una pila vacia no
     * tiene nada que ensenyar, y un cursor de mano sobre algo que no hace nada
     * es peor que no tener cursor.
     */
    public void setOnClick(final Runnable handler) {
        this.onClick = handler;
        final boolean live = handler != null;
        setCursor(live ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
        pseudoClassStateChanged(CLICKABLE, live);
    }

    private Runnable onClick;

    /**
     * Aqui dentro hay algo que puedes lanzar AHORA.
     *
     * <p>La pila dice cuantas cartas hay, no si sirven para algo. Y una carta
     * que se juega desde el cementerio o el exilio — una aventura exiliada, un
     * flashback — no se descubre sola: hay que abrir la zona para verla. El
     * borde de acento es lo que hace que se abra.
     */
    public void setHasPlayable(final boolean on) {
        pseudoClassStateChanged(PLAYABLE, on);
    }

    private static final javafx.css.PseudoClass CLICKABLE =
            javafx.css.PseudoClass.getPseudoClass("clickable");

    private static final javafx.css.PseudoClass PLAYABLE =
            javafx.css.PseudoClass.getPseudoClass("playable");

    public final void setCardWidth(final double w) {
        this.cardWidth = w;
        final double h = w * CardNode.ASPECT;
        final double radius = w * 0.06;

        back.setWidth(w);
        back.setHeight(h);
        back.setArcWidth(radius * 2);
        back.setArcHeight(radius * 2);

        shadow.setWidth(w);
        shadow.setHeight(h);
        shadow.setArcWidth(radius * 2);
        shadow.setArcHeight(radius * 2);
        shadow.setTranslateX(Math.max(2, w * 0.05));
        shadow.setTranslateY(-Math.max(2, w * 0.05));

        count.setStyle("-fx-font-size:" + Math.max(10, w * 0.34) + "px;");
    }

    public double getCardWidth() {
        return cardWidth;
    }

    /** Cuantas cartas hay. Es lo que decide si la pila responde al click. */
    public int getCount() {
        return size;
    }

    private int size;

    public void setCount(final int n) {
        this.size = n;
        count.setText(String.valueOf(n));
        // Una zona vacia se apaga en vez de desaparecer: asi el sitio no baila.
        setOpacity(n == 0 ? 0.32 : 1.0);
    }

    /**
     * La funda elegida, encima del rectangulo liso de siempre.
     *
     * <p>Mismo truco que {@code PlayerBar.setAvatarImage}: {@code setFill(null)}
     * devuelve el control a la hoja de estilos (el color plano de
     * {@code .zone-pile-face}), y una {@code ImagePattern} lo sustituye cuando
     * hay funda de verdad. Sin funda elegida — o si la imagen no ha cargado
     * todavia — se ve exactamente como se veia siempre: no hay pantalla en
     * blanco esperando a Scryfall, porque las fundas no dependen de la red.
     */
    public void setSleeveImage(final javafx.scene.image.Image image) {
        if (image == null) {
            back.setFill(null);
            back.getStyleClass().remove("zone-pile-sleeve");
            return;
        }
        back.setFill(new javafx.scene.paint.ImagePattern(image, 0, 0, 1, 1, true));
        if (!back.getStyleClass().contains("zone-pile-sleeve")) {
            back.getStyleClass().add("zone-pile-sleeve");
        }
    }
}
