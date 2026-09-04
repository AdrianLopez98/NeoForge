package forge.neo.ui;

import java.util.List;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;

/**
 * El mazo de una run, en grande y de mas caro a mas barato.
 *
 * <p>Es <b>la misma vista</b> en los cuatro sitios donde aparece: el descanso
 * (para elegir que sobra), la tienda (para elegir que se borra), el visor de
 * solo lectura del mapa y el resumen del final. Y tiene que serlo: si cada una
 * ordenara a su manera, la carta que el jugador ya sabe donde esta cambiaria de
 * sitio segun por que puerta haya entrado.
 *
 * <h2>Por que ordenado por coste, y de mayor a menor</h2>
 *
 * <p>Porque es la pregunta que se viene a contestar. Lo que se quita de un mazo
 * de treinta casi siempre es <b>la carta de coste siete que nunca puedes
 * pagar</b>, y ponerla arriba es ensenyar la respuesta antes de que haya que
 * buscarla. Para lo demas (contar tierras, ver la curva) sirve igual leido al
 * reves.
 *
 * <h2>Elegir es opcional</h2>
 *
 * <p>Sin {@code onPick} las cartas no se pueden clicar: es un visor, y en el
 * mapa el mazo de una run <b>se mira pero no se toca</b> (el plan de Ascenso). El
 * click derecho amplia siempre, que es como se lee una carta en todo el juego.
 */
public class AscentDeckView extends ScrollPane {

    /**
     * @param cards    las cartas, tal cual (las repetidas salen repetidas: son
     *                 copias del mazo y hay que poder contarlas)
     * @param cardWidth ancho de carta
     * @param onPick   que hacer al clicar una, o {@code null} para solo mirar
     */
    public AscentDeckView(final List<PaperCard> cards, final double cardWidth,
                          final Consumer<PaperCard> onPick) {
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.CENTER);
        for (final PaperCard card : cards) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(card));
            if (onPick != null) {
                node.setCursor(javafx.scene.Cursor.HAND);
                node.setOnMouseClicked(e -> {
                    // Solo el izquierdo. El derecho es para LEER la carta, y lo
                    // que hay detras de este click (quitarla del mazo) no se
                    // deshace: ver las trampas conocidas.
                    if (e.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    onPick.accept(card);
                });
            }
            grid.getChildren().add(node);
        }
        setContent(grid);
        setFitToWidth(true);
        getStyleClass().add("ascent-scroll");
        setHbarPolicy(ScrollBarPolicy.NEVER);
    }

}
