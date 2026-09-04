package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentRelics;
import forge.neo.ascent.AscentRewards;
import forge.neo.ascent.AscentRun;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Lo que te llevas de un nodo, y lo que hay que <b>elegir</b>.
 *
 * <h2>Elegir 1 de 3 es el modo entero</h2>
 *
 * <p>El mazo de salida es flojo a proposito ({@code AscentSeedDeck}) para que
 * esta pantalla importe: es lo unico que hace que el mazo del final de la run no
 * se parezca al del principio. Por eso la carta se ve <b>grande</b> y no en una
 * lista — la decision se toma mirando la carta, no leyendo su nombre.
 *
 * <h2>Y se puede no coger nada</h2>
 *
 * <p>Hay boton de <b>saltar</b>, y no es un descuido: en un mazo de treinta
 * cartas, meter una mala es peor que no meter ninguna. Que quitar valga tanto
 * como anyadir es de lo que vive el modo (por eso el descanso ofrece borrar una
 * carta), asi que negarse tiene que ser una opcion de verdad.
 *
 * <h2>Las reliquias del jefe</h2>
 *
 * <p>Un jefe ofrece <b>tres a elegir</b>. Es el momento en el que se decide si
 * el acto siguiente se aguanta, y darla al azar convertiria lo mas importante de
 * la run en una tirada. Los demas nodos dan una y se lleva sola: ahi no hay
 * decision que tomar.
 */
public class AscentRewardScreen extends StackPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        /** Terminado: al mapa. */
        void done();
    }

    private final AscentRun run;
    private final AscentRewards.Reward reward;
    private final Actions actions;
    private final double cardWidth;

    private final VBox body = new VBox(16);
    private boolean cardTaken;
    private boolean relicTaken;

    public AscentRewardScreen(final AscentRun run, final AscentRewards.Reward reward,
                              final double cardWidth, final Actions actions) {
        this.run = run;
        this.reward = reward;
        this.actions = actions;
        this.cardWidth = cardWidth;
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 77L, Color.web("#E8D7B0"));
        StackPane.setMargin(paper, new Insets(10));

        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(24, 24, Parchment.SAFE_EDGE, 24));
        rebuild();

        getChildren().addAll(paper, body);
        // Click derecho = la carta grande, como en el resto del juego. Aqui
        // hace mas falta que en ninguna parte: se esta eligiendo 1 de 3 y la
        // decision se toma LEYENDO la carta.
        CardZoom.install(this);
    }

    // ------------------------------------------------------------------

    private void rebuild() {
        body.getChildren().clear();

        final Label title = new Label(NeoText.get("ascent.reward.title"));
        title.getStyleClass().add("ascent-act");
        body.getChildren().add(title);

        if (reward.credits > 0) {
            final Label credits = new Label(
                    NeoText.get("ascent.reward.credits", reward.credits));
            credits.getStyleClass().addAll("ascent-pill-base", "ascent-pill");
            body.getChildren().add(credits);
        }

        // ---- la reliquia, primero: es lo que mas pesa ----
        if (!reward.relics.isEmpty() && !relicTaken) {
            final Label what = new Label(NeoText.get(reward.chooseOne
                    ? "ascent.reward.pickRelic" : "ascent.reward.gotRelic"));
            what.getStyleClass().add("ascent-info-title");
            body.getChildren().addAll(what, relicRow());
        }

        // ---- y las tres cartas ----
        if (!reward.cards.isEmpty() && !cardTaken) {
            final Label what = new Label(NeoText.get("ascent.reward.pickCard"));
            what.getStyleClass().add("ascent-info-title");
            body.getChildren().addAll(what, cardRow());

            final Button skip = new Button(NeoText.get("ascent.reward.skip"));
            skip.getStyleClass().add("ascent-button");
            // Saltar es una opcion de verdad: en un mazo de treinta cartas,
            // meter una mala es peor que no meter ninguna.
            skip.setOnAction(e -> {
                cardTaken = true;
                rebuild();
            });
            body.getChildren().add(skip);
        }

        if (nothingLeft()) {
            final Button done = new Button(NeoText.get("ascent.reward.toMap"));
            done.getStyleClass().addAll("ascent-button", "btn-primary");
            done.setOnAction(e -> actions.done());
            body.getChildren().add(done);
        }
    }

    private boolean nothingLeft() {
        final boolean cards = reward.cards.isEmpty() || cardTaken;
        final boolean relics = reward.relics.isEmpty() || relicTaken;
        return cards && relics;
    }

    /** Las cartas, grandes y clicables. */
    private Region cardRow() {
        final HBox row = new HBox(18);
        row.setAlignment(Pos.CENTER);
        int i = 0;
        for (final PaperCard card : reward.cards) {
            final CardNode node = new CardNode(cardWidth * 1.35);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(card));
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(e -> {
                // Solo el boton izquierdo elige. El derecho es para leerla
                // (CardZoom), y elegir una carta AQUI no se deshace: es el
                // principio 6, y por eso el gesto de mirar no puede escoger.
                if (e.getButton() != MouseButton.PRIMARY) {
                    return;
                }
                AscentRewards.take(run, card);
                cardTaken = true;
                rebuild();
            });
            row.getChildren().add(node);
            Anim.dealIn(node, i, reward.cards.size(), true);
            i++;
        }
        return row;
    }

    /** Las reliquias. Se pintan como cartas porque <b>son</b> cartas. */
    private Region relicRow() {
        final HBox row = new HBox(18);
        row.setAlignment(Pos.CENTER);
        final List<AscentRelic> shown = new ArrayList<>(reward.relics);
        int i = 0;
        for (final AscentRelic relic : shown) {
            final PaperCard card = AscentRelics.cardOf(relic);
            final VBox cell = new VBox(6);
            cell.setAlignment(Pos.TOP_CENTER);
            if (card != null) {
                final CardNode node = new CardNode(cardWidth * 1.2);
                node.setRotationEnabled(false);
                node.setCard(CardView.getCardForUi(card));
                cell.getChildren().add(node);
                Anim.dealIn(node, i, shown.size(), true);
            }
            final Label name = new Label(relic.getCardName());
            name.getStyleClass().add("ascent-info-title");
            cell.getChildren().add(name);

            // ⚠️ QUE HACE la reliquia, debajo y siempre. Sin esto se elige a
            // ciegas: una reliquia nuestra no tiene arte, asi que la carta sale
            // como un rectangulo oscuro con el nombre — y entre "+2/+2 a tus
            // criaturas" y "robas tres cartas" no hay decision posible si no se
            // puede leer cual es cual. Se vio en la primera captura.
            final String text = card == null || card.getRules() == null
                    ? null : card.getRules().getOracleText();
            if (text != null && !text.isBlank()) {
                final Label what = new Label(text);
                what.getStyleClass().add("ascent-info-text");
                what.setWrapText(true);
                what.setMaxWidth(cardWidth * 1.2);
                cell.getChildren().add(what);
            }

            if (reward.chooseOne) {
                cell.setCursor(javafx.scene.Cursor.HAND);
                cell.setOnMouseClicked(e -> {
                    if (e.getButton() != MouseButton.PRIMARY) {
                        return;
                    }
                    run.addRelic(relic);
                    relicTaken = true;
                    rebuild();
                });
            }
            row.getChildren().add(cell);
            i++;
        }
        if (!reward.chooseOne) {
            // Se lleva sola: no hay nada que elegir, solo que verla.
            for (final AscentRelic relic : shown) {
                run.addRelic(relic);
            }
            final Button ok = new Button(NeoText.get("ascent.reward.toMap"));
            ok.getStyleClass().addAll("ascent-button", "btn-primary");
            ok.setOnAction(e -> {
                relicTaken = true;
                rebuild();
            });
            final VBox wrap = new VBox(12, row, ok);
            wrap.setAlignment(Pos.CENTER);
            return wrap;
        }
        return row;
    }
}
