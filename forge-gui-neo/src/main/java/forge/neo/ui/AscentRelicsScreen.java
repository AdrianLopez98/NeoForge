package forge.neo.ui;

import java.util.List;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRun;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Las reliquias que llevas, a mitad de run. <b>Se miran, no se tocan</b>, igual
 * que el mazo ({@link AscentDeckScreen}).
 *
 * <h2>Por que hace falta, si ya salen en la barra del mapa</h2>
 *
 * <p>Pedido en Discord (29-09-2026) por mas de uno. Las pastillas de arriba
 * dicen <b>como se llaman</b>, y para saber <b>que hacen</b> habia que
 * adivinar que se les puede dar click derecho. Y con una run larga ni eso: la
 * barra es una fila y no parte linea, asi que las ultimas se salian por la
 * derecha sin forma de llegar a ellas (principio 5).
 *
 * <p>Aqui van <b>todas</b>, como carta y con su texto debajo — el mismo dibujo
 * que en el premio donde se eligieron, para que se reconozcan.
 */
public class AscentRelicsScreen extends StackPane {

    public AscentRelicsScreen(final AscentRun run, final double cardWidth, final Runnable back) {
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 113L, Color.web("#E4D3AC"));
        StackPane.setMargin(paper, new Insets(10));

        final List<AscentRelic> relics = run.relics();

        final Label title = new Label(
                NeoText.get("ascent.over.relics", relics.size()).toUpperCase());
        title.getStyleClass().add("ascent-act");

        final double width = cardWidth * 1.3;
        final FlowPane grid = new FlowPane(22, 18);
        grid.setAlignment(Pos.TOP_CENTER);
        // El sitio de la carta con el raton encima, que crece y sube: sin el,
        // el visor le cortaba la cabeza (el nombre) a la primera fila y el
        // borde a la de la izquierda (Discord, 10-10-2026, con 16 reliquias).
        // Lo mismo que el visor de zonas y CardFit.
        final Insets room = CardNode.hoverRoomFor(width);
        grid.setPadding(new Insets(Math.max(6, room.getTop()), room.getRight(),
                Math.max(12, room.getBottom()), room.getLeft()));
        for (final AscentRelic relic : relics) {
            grid.getChildren().add(cell(relic, width));
        }

        // Scroll y no una fila: con muchas reliquias el boton de volver no
        // puede acabar fuera de la pantalla (ver AscentDeckScreen).
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("ascent-scroll");

        final Button close = new Button(NeoText.get("common.back"));
        close.getStyleClass().addAll("ascent-button", "btn-primary");
        close.setOnAction(e -> back.run());
        final HBox buttons = new HBox(close);
        buttons.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        buttons.setPadding(new Insets(10, 0, Parchment.SAFE_EDGE, 0));

        final VBox head = new VBox(8, title);
        head.setAlignment(Pos.TOP_CENTER);
        head.setPadding(new Insets(22, 28, 10, 28));

        final BorderPane chrome = new BorderPane();
        chrome.setTop(head);
        chrome.setCenter(scroll);
        chrome.setBottom(buttons);
        BorderPane.setMargin(scroll, new Insets(0, 28, 0, 28));

        getChildren().addAll(paper, chrome);
        // Click derecho = la carta grande, como en el visor del mazo.
        CardZoom.install(this);
    }

    /** Una reliquia: la carta, su nombre y QUE HACE, siempre a la vista. */
    private static VBox cell(final AscentRelic relic, final double width) {
        final VBox cell = new VBox(6);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.setMaxWidth(width);
        final PaperCard card = AscentRelics.cardOf(relic);
        if (card != null) {
            final CardNode node = new CardNode(width);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(card));
            cell.getChildren().add(node);
            // La carta ampliada se pinta por encima de las de al lado: dentro
            // de su celda ya va delante, pero la celda de la derecha y las de
            // la fila de abajo se pintan despues y le tapaban el borde.
            node.hoverProperty().addListener((o, was, is) -> cell.setViewOrder(is ? -1 : 0));
        }
        final Label name = new Label(relic.getCardName());
        name.getStyleClass().add("ascent-info-title");
        name.setWrapText(true);
        name.setMaxWidth(width);
        cell.getChildren().add(name);

        // El texto se escribe aparte aunque vaya en la carta: una reliquia no
        // tiene arte y a este tamanyo el de la carta no se lee (ver el premio,
        // AscentRewardScreen.relicRow, que lo aprendio en la primera captura).
        final String text = relic.getText();
        if (text != null && !text.isBlank()) {
            final Label what = new Label(text);
            what.getStyleClass().add("ascent-info-text");
            what.setWrapText(true);
            what.setMaxWidth(width);
            cell.getChildren().add(what);
        }
        return cell;
    }
}
