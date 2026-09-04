package forge.neo.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import forge.deck.Deck;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentDecks;
import forge.neo.ascent.AscentRun;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * El descanso: curarte <b>o</b> quitar una carta del mazo. Una de las dos.
 *
 * <h2>Por que solo una</h2>
 *
 * <p>Es la decision que mas se piensa de toda la run, y lo es <b>porque no se
 * pueden hacer las dos</b>. Curarte es sobrevivir al acto siguiente; quitar una
 * carta mala es que el mazo entero funcione mejor durante el resto de la
 * partida. Dar las dos convierte el nodo en un tramite y de paso deja el mazo
 * hinchandose de basura sin freno, que es justo lo que la run tiene que
 * castigar.
 *
 * <h2>Quitar vale tanto como anyadir</h2>
 *
 * <p>De ahi que la pantalla de quitar ensenye el mazo <b>entero y en grande</b>,
 * ordenado por coste: lo que hay que ver de un vistazo es cual es la carta que
 * sobra, y eso casi siempre es la mas cara que nunca puedes pagar o la tierra de
 * mas.
 */
public class AscentRestScreen extends StackPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        /** Terminado: al mapa. */
        void done();
    }

    private final AscentRun run;
    private final Actions actions;
    private final double cardWidth;
    private final VBox body = new VBox(18);

    public AscentRestScreen(final AscentRun run, final double cardWidth, final Actions actions) {
        this.run = run;
        this.actions = actions;
        this.cardWidth = cardWidth;
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(run.getSeed() + 33L, Color.web("#E4D3AC"));
        StackPane.setMargin(paper, new Insets(10));

        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(24, 24, Parchment.SAFE_EDGE, 24));
        // La vista de quitar carta es una rejilla de treinta cartas dentro de
        // un ScrollPane: es donde puede romperse el reparto, asi que tiene que
        // poder capturarse sin llegar a ella clicando.
        if (Boolean.getBoolean("neo.ascent.restRemove")) {
            showDeck();
        } else {
            showChoice();
        }

        getChildren().addAll(paper, body);
        // Click derecho = la carta grande. Quitar una carta se decide leyendo
        // el mazo, y a este tamanyo no se lee.
        CardZoom.install(this);
    }

    // ------------------------------------------------------------------

    private void showChoice() {
        body.getChildren().clear();

        final Label title = new Label(NeoText.get("ascent.rest.title"));
        title.getStyleClass().add("ascent-act");

        final Label life = new Label(NeoText.get("ascent.life") + "  "
                + run.getLife() + " / " + run.getMaxLife());
        life.getStyleClass().addAll("ascent-pill-base", "ascent-pill-life");

        final Button heal = new Button(NeoText.get("ascent.rest.heal", run.restHeal()));
        heal.getStyleClass().addAll("ascent-button", "btn-primary");
        heal.setDisable(run.getLife() >= run.getMaxLife());
        heal.setOnAction(e -> {
            run.heal(run.restHeal());
            actions.done();
        });

        final Button remove = new Button(NeoText.get("ascent.rest.remove"));
        remove.getStyleClass().add("ascent-button");
        remove.setOnAction(e -> showDeck());

        final Label hint = new Label(NeoText.get("ascent.rest.hint"));
        hint.getStyleClass().add("ascent-hint");

        final HBox buttons = new HBox(18, heal, remove);
        buttons.setAlignment(Pos.CENTER);
        body.getChildren().addAll(title, life, buttons, hint);
    }

    /** El mazo entero, para elegir que sobra. */
    private void showDeck() {
        body.getChildren().clear();

        final Label title = new Label(NeoText.get("ascent.rest.removeTitle"));
        title.getStyleClass().add("ascent-act");

        final Deck deck = AscentDecks.load(run);
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.CENTER);
        if (deck != null) {
            for (final PaperCard card : sortedByCost(deck)) {
                final CardNode node = new CardNode(cardWidth * 0.85);
                node.setRotationEnabled(false);
                node.setCard(CardView.getCardForUi(card));
                node.setCursor(javafx.scene.Cursor.HAND);
                node.setOnMouseClicked(e -> {
                    // Quitar una carta del mazo NO se deshace, asi que el
                    // click derecho (leerla) no puede hacerlo.
                    if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                        return;
                    }
                    deck.getMain().remove(card);
                    AscentDecks.save(deck);
                    actions.done();
                });
                grid.getChildren().add(node);
            }
        }
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("ascent-scroll");
        scroll.setPrefHeight(520);

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("ascent-button");
        back.setOnAction(e -> showChoice());

        body.getChildren().addAll(title, scroll, back);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
    }

    /**
     * El mazo, con lo mas caro primero.
     *
     * <p>Lo que se viene a quitar casi siempre es la carta de coste siete que
     * nunca se puede pagar, asi que ponerla la primera es ahorrarle al jugador
     * buscar entre treinta cartas la que ya sabe que sobra.
     */
    private List<PaperCard> sortedByCost(final Deck deck) {
        final List<PaperCard> out = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            for (int i = 0; i < e.getValue(); i++) {
                out.add(e.getKey());
            }
        }
        out.sort(Comparator.comparingInt(
                (PaperCard c) -> c.getRules().getManaCost().getCMC()).reversed()
                .thenComparing(PaperCard::getName));
        return out;
    }

    /** Por si alguna pantalla quiere el ancho util. */
    public Region root() {
        return this;
    }
}
