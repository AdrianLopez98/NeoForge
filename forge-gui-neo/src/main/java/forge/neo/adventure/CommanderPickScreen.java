package forge.neo.adventure;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.card.CardText;
import forge.neo.ui.CardZoom;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Elegir el comandante de una Aventura nueva: tres cartas, una se elige.
 *
 * <p>La carta en grande y nada mas, porque se elige mirandola; el clic derecho
 * la amplia para leerla (y solo el izquierdo elige: elegir no se deshace). Y
 * una salida que no haya que adivinar (principio 7): quedarse con el mazo de
 * siempre.
 */
final class CommanderPickScreen extends BorderPane {

    private final HBox cards = new HBox(36);
    private final Label status = new Label();
    private final Button fixed = new Button(NeoText.get("adventure.pick.fixed"));
    /** Ya se ha elegido: sin apagar las cartas (apagadas se ven grises justo al elegir). */
    private boolean locked;
    private final List<Runnable> picks = new java.util.ArrayList<>();

    /**
     * @param options  los tres comandantes
     * @param onPick   con el elegido
     * @param onFixed  si prefiere el mazo de siempre
     */
    CommanderPickScreen(final List<PaperCard> options, final double cardWidth,
                        final Consumer<PaperCard> onPick, final Runnable onFixed) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("adventure.pick.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("adventure.pick.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        final VBox top = new VBox(6, title, sub);
        top.setAlignment(Pos.CENTER);
        top.setPadding(new Insets(36, 40, 10, 40));
        setTop(top);

        cards.setAlignment(Pos.CENTER);
        for (final PaperCard pc : options) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setBadgesVisible(false);
            node.setCard(CardView.getCardForUi(pc));
            node.setCursor(javafx.scene.Cursor.HAND);
            final Label name = new Label(CardText.nameOf(pc));
            name.getStyleClass().add("home-subtitle");
            final VBox box = new VBox(12, node, name);
            box.setAlignment(Pos.CENTER);
            final Runnable pick = () -> {
                if (!locked) {
                    building(pc);
                    onPick.accept(pc);
                }
            };
            picks.add(pick);
            node.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    pick.run();
                }
            });
            cards.getChildren().add(box);
        }
        status.getStyleClass().add("home-subtitle");
        final VBox center = new VBox(24, cards, status);
        center.setAlignment(Pos.CENTER);
        setCenter(center);

        fixed.getStyleClass().add("btn-secondary");
        fixed.setOnAction(e -> {
            locked = true;
            fixed.setDisable(true);
            onFixed.run();
        });
        final HBox bottom = new HBox(fixed);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPadding(new Insets(10, 40, 36, 40));
        setBottom(bottom);

        CardZoom.install(this);
    }

    /** Elegido: ya no se puede tocar nada mientras se monta el mazo. */
    private void building(final PaperCard pc) {
        locked = true;
        fixed.setDisable(true);
        status.setText(NeoText.get("adventure.pick.building", CardText.nameOf(pc)));
    }

    /** Solo pruebas: elige la opcion {@code i} como un clic. */
    void pickForTest(final int i) {
        picks.get(i).run();
    }

    void failed() {
        status.setText(NeoText.get("adventure.pick.failed"));
    }
}
