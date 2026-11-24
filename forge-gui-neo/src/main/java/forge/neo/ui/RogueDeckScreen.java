package forge.neo.ui;

import forge.game.card.CardView;
import forge.gamemodes.rogue.RogueRun;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.rogue.RogueDeckRules;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * El mazo durante una run de Rogue Commander ({@code CEditorRogue} de Forge,
 * con las reglas en {@link RogueDeckRules}): clic en una carta para quitarla
 * (gasta un credito de eliminacion, salvo las basicas), clic en una basica de
 * abajo para anyadirla, y deshacer. Lo que no se deshace (quitar una carta)
 * pregunta antes (principio 6).
 */
public final class RogueDeckScreen extends RogueScreen {

    private final RogueRun run;
    private final RogueDeckRules rules;
    private final double cardWidth;
    private final BorderPane layout = new BorderPane();
    private final Label credits = new Label();
    private final Button undo = new Button(NeoText.get("rogue.deck.undo"));

    public RogueDeckScreen(final RogueRun run, final double cardWidth, final Runnable back) {
        this.run = run;
        this.rules = new RogueDeckRules(run);
        this.cardWidth = cardWidth;
        getStyleClass().add("rogue-deck");

        undo.getStyleClass().add("btn-secondary");
        undo.setOnAction(e -> {
            rules.undo();
            refresh();
        });
        final Button done = new Button(NeoText.get("rogue.deck.back"));
        done.getStyleClass().add("btn-primary");
        done.setOnAction(e -> back.run());
        credits.getStyleClass().addAll("rogue-stat", "rogue-credits");
        Tooltip.install(credits, new Tooltip(NeoText.get("rogue.deck.creditsHint")));
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, undo, credits, gap, done);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 22, 14, 22));
        footer.getStyleClass().add("rogue-bottom");
        layout.setBottom(footer);
        setContent(layout);
        refresh();
    }

    /** Las reglas, para que quien cierre la pantalla guarde y evalue logros (fuera del hilo de JavaFX). */
    public RogueDeckRules rules() {
        return rules;
    }

    private void refresh() {
        final Label title = new Label(NeoText.get("rogue.deckTitle", run.getCurrentDeck().getMain().countAll()));
        title.getStyleClass().add("rogue-title");
        final Label hint = new Label(NeoText.get("rogue.deck.hint"));
        hint.getStyleClass().add("rogue-hint");
        hint.setWrapText(true);
        final VBox head = new VBox(4, title, hint);
        head.setPadding(new Insets(14, 22, 6, 22));
        layout.setTop(head);

        final List<PaperCard> cards = new ArrayList<>(run.getCurrentDeck().getCommanders());
        cards.addAll(run.getCurrentDeck().getMain().toFlatList());
        final AscentDeckView view = new AscentDeckView(cards, cardWidth * 0.8, this::askRemove);

        final HBox lands = new HBox(10);
        lands.setAlignment(Pos.CENTER_LEFT);
        final Label addLabel = new Label(NeoText.get("rogue.deck.addBasic"));
        addLabel.getStyleClass().add("rogue-section");
        lands.getChildren().add(addLabel);
        for (final PaperCard land : rules.basicLands()) {
            final CardNode node = new CardNode(cardWidth * 0.55);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(land));
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(e -> {
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY && rules.add(land)) {
                    refresh();
                }
            });
            lands.getChildren().add(node);
        }
        final javafx.scene.control.ScrollPane landStrip = new javafx.scene.control.ScrollPane(lands);
        landStrip.setFitToHeight(true);
        landStrip.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        landStrip.getStyleClass().add("rogue-scroll");
        landStrip.setPadding(new Insets(6, 22, 6, 22));
        // Su alto, entero: si no, la lista del mazo (que crece) la aplasta y las
        // basicas salian diminutas y cortadas.
        final double landH = cardWidth * 0.55 * CardNode.ASPECT + UiScale.px(40);
        landStrip.setMinHeight(landH);
        landStrip.setPrefHeight(landH);

        final VBox center = new VBox(8, view, landStrip);
        VBox.setVgrow(view, Priority.ALWAYS);
        layout.setCenter(center);

        credits.setText(NeoText.get("rogue.credits", run.getRemovalCredits()));
        undo.setDisable(!rules.canUndo());
    }

    private void askRemove(final PaperCard card) {
        if (card == null || run.getCurrentDeck().getCommanders().contains(card)) {
            return;
        }
        final boolean basic = card.getRules().getType().isBasicLand();
        if (!rules.canRemove(card)) {
            dialogs().show(confirm(NeoText.get("rogue.deck.noCredits.title"), NeoText.get("rogue.deck.noCredits"),
                List.of(NeoText.get("rogue.ok")), i -> dialogs().hide()));
            return;
        }
        final String text = basic ? NeoText.get("rogue.deck.removeBasic", card.getName())
            : NeoText.get("rogue.deck.remove", card.getName(), run.getRemovalCredits());
        dialogs().show(confirm(NeoText.get("rogue.deck.remove.title"), text,
            List.of(NeoText.get("rogue.cancel"), NeoText.get("rogue.deck.remove.yes")), i -> {
                dialogs().hide();
                if (i == 1 && rules.remove(card)) {
                    refresh();
                }
            }));
    }

    private static Region confirm(final String title, final String text, final List<String> options,
                                  final java.util.function.Consumer<Integer> onChoice) {
        return new ConfirmDialog(title, text, options, 0, onChoice);
    }
}
