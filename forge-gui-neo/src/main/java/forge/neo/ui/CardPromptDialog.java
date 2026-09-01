package forge.neo.ui;

import java.util.List;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * "Esta carta te pregunta esto": el dialogo de los triggers, como en Arena.
 *
 * <p>Cuando salta un disparo que te deja decidir algo ("puedes sacrificar un
 * permanente..."), Arena te pone <b>la carta grande en medio de la pantalla</b>
 * con la pregunta y los botones al lado. Aqui se hace igual, y por la misma
 * razon: el motor te para en mitad de tu paso final y, si la pregunta sale
 * pequenya en un panel lateral, ni te enteras de que ha pasado ni de que carta
 * viene.
 *
 * <p>La carta no es decorado. El texto de la pregunta suele ser una linea
 * ("Do you want to sacrifice?") que no dice de que habilidad viene ni que gana
 * el rival si dices que si; eso esta escrito en la carta, y por eso la carta
 * tiene que verse y poder leerse.
 *
 * <p>El motor SIEMPRE nos pasa esa carta en {@code confirm(CardView, ...)}.
 * Antes se tiraba.
 */
public class CardPromptDialog extends VBox {

    /**
     * @param card     la carta de la que viene la pregunta; puede ser null
     * @param question lo que pregunta el motor
     * @param options  las respuestas, tal y como las manda el motor
     * @param onPick   recibe el indice elegido
     */
    public CardPromptDialog(final CardView card, final String title, final String question,
                            final List<String> options, final int defaultOption,
                            final double cardWidth, final Consumer<Integer> onPick) {
        getStyleClass().addAll("dialog", "card-prompt");
        setSpacing(12);
        setPadding(new Insets(22, 26, 20, 26));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final VBox right = new VBox(12);
        right.setAlignment(Pos.CENTER_LEFT);

        final String heading = title != null && !title.isBlank() ? title : nameOf(card);
        if (heading != null && !heading.isBlank()) {
            final Label name = new Label(heading);
            name.getStyleClass().add("dialog-title");
            name.setWrapText(true);
            name.setMaxWidth(420);
            right.getChildren().add(name);
        }

        final Label ask = new Label(question == null ? "" : question);
        ask.getStyleClass().add("card-prompt-question");
        ask.setWrapText(true);
        ask.setMaxWidth(420);
        ask.setMinHeight(Region.USE_PREF_SIZE);
        right.getChildren().add(ask);

        // Botones grandes y en columna: es una decision de partida, no un
        // "aceptar" de tramite, y a veces son cuatro modos con texto largo.
        final VBox buttons = new VBox(8);
        for (int i = 0; i < options.size(); i++) {
            final int index = i;
            final Button b = new Button(options.get(i));
            b.getStyleClass().addAll(i == defaultOption ? "btn-primary" : "btn-secondary",
                    "card-prompt-option");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setWrapText(true);
            b.setMinHeight(Region.USE_PREF_SIZE);
            b.setOnAction(e -> onPick.accept(index));
            buttons.getChildren().add(b);
        }
        buttons.setPrefWidth(300);
        right.getChildren().add(buttons);

        if (card != null) {
            final CardNode node = new CardNode(cardWidth);
            // Derecha y sin pastillas: aqui la carta esta para leerla.
            node.setRotationEnabled(false);
            node.setCard(card);
            node.setHoverEnabled(false);
            node.setBadgesVisible(false);
            final HBox row = new HBox(26, node, right);
            row.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(row);
        } else {
            getChildren().add(right);
        }
    }

    private static String nameOf(final CardView card) {
        return card == null || card.getCurrentState() == null
                ? null : forge.neo.card.CardText.nameOf(card.getCurrentState());
    }
}
