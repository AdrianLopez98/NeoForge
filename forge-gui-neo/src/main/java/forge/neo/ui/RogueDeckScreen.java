package forge.neo.ui;

import forge.game.card.CardView;
import forge.gamemodes.rogue.RogueRun;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.rogue.RogueDeckRules;
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
        // Ordenado como el mazo de Ascenso (Discord, 25-11-2026: "I see no logic
        // in how cards are"): por coste o por tipo, con su rotulo en cada grupo
        // y la cuenta por tipo. Es la MISMA preferencia (AscentDecks.byType),
        // porque AscentDeckView la lee por dentro: una propia dejaria los
        // botones diciendo una cosa y la rejilla haciendo otra.
        final List<PaperCard> main = run.getCurrentDeck().getMain().toFlatList();
        final Label types = new Label(forge.neo.ascent.AscentDecks.typeLine(main));
        types.getStyleClass().add("rogue-hint");
        types.setWrapText(true);
        final HBox order = new HBox(8, sortButton(false), sortButton(true));
        order.setAlignment(Pos.CENTER_LEFT);
        final HBox typesRow = new HBox(14, types, order);
        typesRow.setAlignment(Pos.CENTER_LEFT);
        final VBox head = new VBox(4, title, hint, typesRow);
        head.setPadding(new Insets(14, 22, 6, 22));
        layout.setTop(head);

        // El comandante, aparte y a la izquierda (como en Ascenso): metido en la
        // lista caia en "Coste 4" entre las demas, y no se puede quitar.
        final AscentDeckView view = new AscentDeckView(main, cardWidth * 0.8, this::askRemove);
        final BorderPane deckPane = new BorderPane(view);
        final VBox commanders = new VBox(8);
        commanders.setAlignment(Pos.TOP_CENTER);
        commanders.setPadding(new Insets(4, 14, 0, 22));
        for (final PaperCard c : run.getCurrentDeck().getCommanders()) {
            final CardNode node = new CardNode(cardWidth * 0.9);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(c));
            commanders.getChildren().add(node);
        }
        if (!commanders.getChildren().isEmpty()) {
            final Label cmdLabel = new Label(NeoText.get("commander.yours").toUpperCase());
            cmdLabel.getStyleClass().add("rogue-section");
            commanders.getChildren().add(0, cmdLabel);
            deckPane.setLeft(commanders);
        }

        // Lo que se puede anyadir (las basicas) A LA IZQUIERDA y el mazo a la
        // derecha, como un constructor (Discord, 25-11-2026: "splitting a
        // widescreen top to bottom doesn't make much sense"). Abajo, la tira
        // le quitaba al mazo una fila entera de alto.
        final double landW = cardWidth * 0.55;
        final javafx.scene.layout.FlowPane lands = new javafx.scene.layout.FlowPane(10, 10);
        lands.setPrefWrapLength(landW * 2 + 10);
        lands.setAlignment(Pos.TOP_CENTER);
        final Label addLabel = new Label(NeoText.get("rogue.deck.addBasic"));
        addLabel.getStyleClass().add("rogue-section");
        addLabel.setWrapText(true);
        for (final PaperCard land : rules.basicLands()) {
            final CardNode node = new CardNode(landW);
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
        landStrip.setFitToWidth(true);
        landStrip.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        landStrip.getStyleClass().add("rogue-scroll");
        // Su ancho, entero: dos columnas de basicas y la barra de desplazar.
        final double landColW = landW * 2 + 10 + UiScale.px(24);
        landStrip.setMinWidth(landColW);
        landStrip.setPrefWidth(landColW);
        landStrip.setMaxWidth(landColW);
        final VBox landColumn = new VBox(8, addLabel, landStrip);
        landColumn.setPadding(new Insets(6, 8, 6, 22));
        landColumn.setMaxWidth(landColW + UiScale.px(30));
        addLabel.setMaxWidth(landColW);
        VBox.setVgrow(landStrip, Priority.ALWAYS);
        layout.setLeft(landColumn);
        layout.setCenter(deckPane);

        credits.setText(NeoText.get("rogue.credits", run.getRemovalCredits()));
        undo.setDisable(!rules.canUndo());
    }

    /** Por coste / Por tipo: el elegido, marcado. Cambiarlo guarda y rehace la pantalla. */
    private Button sortButton(final boolean byType) {
        final Button b = new Button(NeoText.get(byType ? "ascent.deck.sortType" : "ascent.deck.sortCost"));
        b.setId(byType ? "rogue-sort-type" : "rogue-sort-cost");
        // Su ancho, entero: la linea de tipos de al lado lo estrujaba ("Por coste" cortado a 1440).
        b.setMinWidth(Region.USE_PREF_SIZE);
        final boolean on = forge.neo.ascent.AscentDecks.byType() == byType;
        b.getStyleClass().add(on ? "btn-primary" : "btn-secondary");
        b.setOnAction(e -> {
            if (!on) {
                forge.neo.ascent.AscentDecks.setByType(byType);
                refresh();
            }
        });
        return b;
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
