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

        this.pile = new StackPane(shadow, back, count);
        pile.setAlignment(Pos.CENTER);
        getChildren().addAll(pile, caption);

        setCardWidth(cardWidth);
    }

    private final StackPane pile;

    /**
     * <b>La primera carta, boca arriba</b> (o {@code null} para la funda de
     * siempre).
     *
     * <p>Para el mazo. Hay una familia de cartas que te deja mirar la de
     * arriba — Bolas's Citadel, Oraculo de Mul Daya, Vision del futuro, Melek,
     * Materia de invocacion — y varias ademas te dejan <b>jugarla desde ahi</b>.
     * Todo eso funcionaba ya... si adivinabas que el contador "MAZO" de la
     * barra se clicaba. Reportado en Reddit el 20-09-2026 tal cual:
     * <i>"How to view topdeck? I tried and have Bolas Citadel however I can't
     * find the deck UI"</i>.
     *
     * <p>Quien decide si se puede ver NO es esta clase: es el motor, y lo
     * pregunta la mesa ({@code TableScreen.setSelfLibraryPile} con
     * {@code mayView}). Aqui solo se pinta lo que llegue.
     *
     * <p>La carta se pinta <b>encima de la funda</b> y no en su lugar: asi la
     * pila sigue pareciendo una pila — con su sombra detras y su numero — y lo
     * que cambia es que la de arriba esta destapada, que es justo lo que pasa
     * en la mesa de verdad.
     */
    public void setTopCard(final forge.game.card.CardView card) {
        if (card == null) {
            if (top != null) {
                top.setVisible(false);
                top.setManaged(false);
            }
            back.setVisible(true);
            placeCount(false);
            return;
        }
        if (top == null) {
            top = new CardNode(cardWidth);
            // De lectura: ni se gira ni lleva pastillas. Y el raton lo atraviesa
            // para que el click siga siendo el de la PILA (abrir el visor), que
            // es donde la carta se ve grande y se puede lanzar. Clicarla aqui
            // para lanzarla pagando vidas seria muy facil de hacer sin querer.
            top.setRotationEnabled(false);
            top.setBadgesVisible(false);
            top.setMouseTransparent(true);
            pile.getChildren().add(pile.getChildren().indexOf(count), top);
        }
        top.setVisible(true);
        top.setManaged(true);
        top.setCardWidth(cardWidth);
        top.setCard(card);
        // La funda se esconde: con la carta encima no se ve, y dejarla pintando
        // debajo es trabajo de balde en cada refresco.
        back.setVisible(false);
        placeCount(true);
    }

    /**
     * Donde va el numero: en el centro de la funda, o en una esquina cuando hay
     * carta destapada.
     *
     * <p>Centrado sobre una carta boca arriba tapa el arte y el nombre — o sea
     * justo lo que se ha destapado para poder mirarlo. Se vio en la primera
     * captura y por eso esta escrito aqui.
     */
    private void placeCount(final boolean overCard) {
        StackPane.setAlignment(count, overCard ? Pos.BOTTOM_RIGHT : Pos.CENTER);
        final double w = cardWidth;
        count.setStyle("-fx-font-size:"
                + (overCard ? Math.max(9, w * 0.2) : Math.max(10, w * 0.34)) + "px;");
        if (overCard) {
            if (!count.getStyleClass().contains("zone-pile-count-corner")) {
                count.getStyleClass().add("zone-pile-count-corner");
            }
        } else {
            count.getStyleClass().remove("zone-pile-count-corner");
        }
        StackPane.setMargin(count, overCard
                ? new javafx.geometry.Insets(0, Math.max(2, w * 0.04), Math.max(2, w * 0.04), 0)
                : javafx.geometry.Insets.EMPTY);
    }

    private CardNode top;

    /** La carta que se esta ensenyando boca arriba, si hay alguna. */
    public forge.game.card.CardView getTopCard() {
        return top == null || !top.isVisible() ? null : top.getCard();
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

        if (top != null) {
            top.setCardWidth(w);
        }
        // El tamanyo del numero depende de si hay carta debajo: lo decide
        // placeCount, que ademas sabe donde ponerlo.
        placeCount(top != null && top.isVisible());
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
