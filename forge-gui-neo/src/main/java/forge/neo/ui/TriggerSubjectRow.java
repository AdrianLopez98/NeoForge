package forge.neo.ui;

import java.util.List;

import forge.game.card.CardView;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * "Este disparo lo ha provocado ESTA carta": la fila que va debajo del titulo
 * cuando se elige el modo de un disparo.
 *
 * <p>Quien sabe de que cartas habla el disparo es
 * {@code forge.neo.match.TriggerSubject}; esto solo las pinta. La carta en
 * pequenyo, porque en la mesa la reconoces por la imagen y no por el nombre; el
 * nombre, para no tener que ampliarla; y a quien ataca si esta atacando, que es
 * lo unico que distingue dos fichas iguales.
 *
 * <p>Con sus pastillas puestas, al reves que en {@code CardPromptDialog}: aqui
 * la fuerza de AHORA decide (a cual le das el dobleataque), y la imagen solo
 * trae la impresa. El click derecho la sigue ampliando.
 */
public final class TriggerSubjectRow extends VBox {

    /** Lo que se ensenya de cada carta, ya en el idioma de la partida. */
    public record Entry(CardView card, String name, String attacking) {
    }

    public TriggerSubjectRow(final List<Entry> entries, final double cardWidth) {
        getStyleClass().add("trigger-subject");
        setSpacing(6);

        final Label label = new Label(NeoText.get("choice.triggeredBy"));
        label.getStyleClass().add("dialog-counter");

        final FlowPane cards = new FlowPane(22, 10);
        cards.setAlignment(Pos.CENTER_LEFT);
        for (final Entry e : entries) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setCard(e.card());
            node.setHoverEnabled(false);

            final VBox text = new VBox(3);
            text.setAlignment(Pos.CENTER_LEFT);
            final Label name = new Label(e.name());
            // La clase de siempre y la propia: si la propia faltara en la hoja,
            // se sigue leyendo (un Label sin clase sale gris oscuro sobre el
            // dialogo oscuro).
            name.getStyleClass().addAll("dialog-text", "trigger-subject-name");
            name.setWrapText(true);
            name.setMaxWidth(240);
            text.getChildren().add(name);
            if (e.attacking() != null && !e.attacking().isBlank()) {
                // La flecha es la de la mesa: de atacante a lo que ataca. Y el
                // color, el de "a quien apunta" del stack.
                final Label target = new Label("→ " + e.attacking());
                target.getStyleClass().addAll("stack-targets", "trigger-subject-attacking");
                target.setWrapText(true);
                target.setMaxWidth(240);
                text.getChildren().add(target);
            }

            final HBox one = new HBox(10, node, text);
            one.setAlignment(Pos.CENTER_LEFT);
            cards.getChildren().add(one);
        }
        getChildren().addAll(label, cards);
    }
}
