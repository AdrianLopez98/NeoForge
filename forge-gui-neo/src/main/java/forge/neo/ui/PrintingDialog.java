package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.List;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Elegir con que edicion — o sea, con que arte — llevas una carta.
 *
 * <p>Es el <i>switch printing</i> de Moxfield. Da igual para las reglas y no da
 * igual para nada mas: la carta que eliges es la que vas a ver en la mesa toda
 * la partida.
 *
 * <p>Se ensenyan las cartas, no una lista de codigos de edicion, porque la
 * pregunta que se esta haciendo el jugador es <i>cual me gusta mas</i>. El
 * codigo y el numero van debajo para quien busque una concreta.
 */
public class PrintingDialog extends VBox {

    public PrintingDialog(final PaperCard current, final List<PaperCard> printings,
                          final double cardWidth, final Consumer<PaperCard> onPick,
                          final Runnable onCancel) {
        getStyleClass().add("dialog");
        setSpacing(12);
        setPadding(new Insets(20, 24, 18, 24));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(NeoText.get("printing.title", CardText.nameOf(current)));
        heading.getStyleClass().add("dialog-title");

        final Label hint = new Label(printings.size() == 1
                ? NeoText.get("printing.only")
                : NeoText.get("printing.hint"));
        hint.getStyleClass().add("dialog-text");
        hint.setWrapText(true);
        hint.setMaxWidth(720);

        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.CENTER);
        grid.setPrefWrapLength(Math.max(640, cardWidth * 5));

        for (final PaperCard printing : printings) {
            grid.getChildren().add(tile(printing, printing.equals(current), cardWidth, onPick));
        }

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(Math.min(520, cardWidth * CardNode.ASPECT * 2 + 60));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        final Label count = new Label(printings.size() == 1
                ? NeoText.get("printing.count.one")
                : NeoText.get("printing.count", printings.size()));
        count.getStyleClass().add("dialog-counter");

        final Button cancel = new Button(NeoText.get("common.cancel"));
        cancel.getStyleClass().add("btn-secondary");
        cancel.setOnAction(e -> onCancel.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, count, gap, cancel);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, hint, scroll, footer);
    }

    /** Una edicion: la carta, y debajo el codigo del set y su numero. */
    private static Region tile(final PaperCard printing, final boolean isCurrent,
                               final double cardWidth, final Consumer<PaperCard> onPick) {
        final CardNode node = new CardNode(cardWidth);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(printing));
        node.setSelectable(isCurrent);

        final Label label = new Label(editionName(printing));
        label.getStyleClass().add("printing-label");
        label.setWrapText(true);
        label.setMaxWidth(cardWidth);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        final VBox box = new VBox(4, node, label);
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().add("catalogue-tile");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setOnMouseClicked(e -> onPick.accept(printing));

        if (isCurrent) {
            final Label mark = new Label(NeoText.get("printing.current"));
            mark.getStyleClass().add("catalogue-count");
            final StackPane stack = new StackPane(box, mark);
            StackPane.setAlignment(mark, Pos.TOP_RIGHT);
            StackPane.setMargin(mark, new Insets(4));
            stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            return stack;
        }
        return box;
    }

    /**
     * "MH2 · Modern Horizons 2", o solo el codigo si no se conoce la edicion.
     *
     * <p>El codigo es lo que la gente sabe de memoria y el nombre lo que
     * reconoce; caben los dos.
     */
    private static String editionName(final PaperCard card) {
        final String code = card.getEdition();
        try {
            final forge.card.CardEdition ed = FModel.getMagicDb().getEditions().get(code);
            if (ed != null && ed.getName() != null && !ed.getName().equals(code)) {
                return code + " · " + ed.getName();
            }
        } catch (final RuntimeException e) {
            // Una edicion desconocida no vale una excepcion: con el codigo basta.
        }
        return code;
    }
}
