package forge.neo.ui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.ascent.AscentDecks;
import forge.neo.card.CardNode;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
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
 * <h2>O por tipo, y siempre en grupos</h2>
 *
 * <p>Desde el 09-10-2026 (Discord: <i>"add the ability to sort by type ... see
 * how many lands I have as well as other card types"</i>) el jugador puede
 * pedirlo por tipo en el visor del mapa ({@link AscentDeckScreen}), y entonces
 * sale asi en los cuatro sitios: los grupos del editor de mazos, cada uno con
 * su rotulo y su cuenta, y dentro de cada uno de cara a barata. Y por coste
 * tambien en grupos, uno por coste y las tierras aparte
 * ({@link AscentDecks#headerOf}): sin rotulos, los dos botones parecian hacer
 * lo mismo. Ordena aqui dentro ({@link AscentDecks#sort}) para que ninguna
 * pantalla se quede con el orden de antes.
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
        fill(grid, cards, card -> {
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
            return node;
        });
        setContent(grid);
        setFitToWidth(true);
        // Que el mazo llene el visor: ver CardFit. El ancho que llega es el
        // suelo, y el techo evita carteles con un mazo corto.
        CardFit.install(this, grid, cardWidth, Math.max(cardWidth, UiScale.px(230)));
        getStyleClass().add("ascent-scroll");
        setHbarPolicy(ScrollBarPolicy.NEVER);
    }

    /**
     * Mete en la rejilla las cartas, en el orden que haya elegido el jugador
     * ({@link AscentDecks#sort}), con un rotulo a lo ancho al empezar cada
     * grupo ("CRIATURAS · 23", "COSTE 5 · 4"). Lo usa tambien el descanso, que
     * monta su propia rejilla.
     *
     * <p>Los rotulos no son {@link CardNode}, asi que {@link CardFit} no los
     * cuenta como cartas; solo anyaden una fila.
     */
    static void fill(final FlowPane grid, final List<PaperCard> cards,
                     final Function<PaperCard, Node> nodeFor) {
        final List<PaperCard> ordered = AscentDecks.sort(cards);
        final java.util.List<String> heads = new java.util.ArrayList<>();
        for (final PaperCard card : ordered) {
            heads.add(AscentDecks.headerOf(card));
        }
        for (int i = 0; i < ordered.size(); i++) {
            final String head = heads.get(i);
            if (i == 0 || !head.equals(heads.get(i - 1))) {
                int count = 0;
                for (int j = i; j < heads.size() && heads.get(j).equals(head); j++) {
                    count++;
                }
                grid.getChildren().add(header(grid, head, count));
            }
            grid.getChildren().add(nodeFor.apply(ordered.get(i)));
        }
    }

    /** El rotulo de un grupo, del ancho de la rejilla para que vaya en su fila. */
    private static Label header(final FlowPane grid, final String head, final int count) {
        final Label label = new Label(head.toUpperCase() + "  ·  " + count);
        label.getStyleClass().add("ascent-group-head");
        label.prefWidthProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                () -> Math.max(0, grid.getWidth() - grid.getPadding().getLeft()
                        - grid.getPadding().getRight() - 2),
                grid.widthProperty(), grid.paddingProperty()));
        return label;
    }

}
