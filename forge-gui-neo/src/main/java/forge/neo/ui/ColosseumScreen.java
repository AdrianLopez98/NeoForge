package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.card.CardEdition;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.quest.NeoColosseum;
import forge.neo.quest.NeoQuest;
import forge.neo.quest.NeoQuestShop;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * <b>El Coliseo</b> de la Quest de Commander: lo que es, lo que cuesta, lo que
 * se gana y contra quien, <b>antes</b> de pagar (Ana, 13-10-2026: <i>"que
 * antes de entrar obviamente te diga en que consiste, precio y rewards"</i>).
 *
 * <p>La logica vive en {@link NeoColosseum}; aqui solo se ensenya y se
 * pregunta. Entrar pide confirmacion: la entrada es cara y no se devuelve.
 */
public class ColosseumScreen extends StackPane {

    /** Lo que esta pantalla pide fuera. */
    public interface Actions {
        /** Ya pagado: jugar la partida. */
        void play();

        /** Al cuartel general. */
        void back();
    }

    private final double cardWidth;
    private final Actions actions;
    private final Overlay overlay = new Overlay();
    private final BorderPane frame = new BorderPane();

    public ColosseumScreen(final double cardWidth, final Actions actions) {
        this.cardWidth = cardWidth;
        this.actions = actions;
        getStyleClass().addAll("table-root", "quest");
        build();
        getChildren().addAll(frame, overlay);
        overlay.hide();
        CardZoom.install(this);
    }

    private void build() {
        final Label title = new Label(NeoText.get("colosseum.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("colosseum.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        final VBox head = new VBox(2, title, sub);
        head.setPadding(new Insets(20, 30, 8, 30));
        frame.setTop(head);

        final VBox body = new VBox(16);
        body.setPadding(new Insets(8, 30, 8, 30));
        body.getChildren().addAll(explain(), terms(), rivals(), myDeck());

        final ScrollPane sp = new ScrollPane(body);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        frame.setCenter(sp);

        final Button enter = button(NeoText.get("colosseum.enter", NeoColosseum.fee()), "btn-primary",
                this::confirmEnter);
        enter.setDisable(!NeoColosseum.canAfford() || NeoColosseum.deckProblem() != null
                || NeoColosseum.lineup().size() < NeoColosseum.RIVALS);
        frame.setBottom(footer(enter));
    }

    /** Volver a la derecha y, justo a su izquierda, entrar (principio 12). */
    private Region footer(final Button primary) {
        final Button back = button(NeoText.get("common.back"), "btn-secondary", actions::back);
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox bar = new HBox(10, gap, primary, back);
        bar.getStyleClass().add("home-footer");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 30, 14, 30));
        return bar;
    }

    private static Button button(final String text, final String style, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setMinWidth(Region.USE_PREF_SIZE);
        if (action != null) {
            b.setOnAction(e -> action.run());
        }
        return b;
    }

    /** En que consiste: a cuatro, todos contra todos, gana el ultimo en pie. */
    private Region explain() {
        final Label how = new Label(NeoText.get("colosseum.how", tierName(NeoColosseum.rivalTier())));
        how.getStyleClass().add("home-subtitle");
        how.setWrapText(true);
        how.setMaxWidth(UiScale.px(860));
        return how;
    }

    /** Precio y premios, en grande: es lo que se decide aqui. */
    private Region terms() {
        final CardEdition ed = NeoColosseum.prizeEdition();
        final String prize = ed == null
                ? NeoText.get("colosseum.prizeCreditsOnly", NeoColosseum.prizeCredits())
                : NeoText.get(NeoColosseum.prizeTextKey(), NeoColosseum.prizeCredits(), ed.getName());
        // En una fila que se reparte el ancho: con FlowPane, en 2K la tercera
        // bajaba a otra linea y empujaba tu mazo fuera de la pantalla.
        final HBox row = new HBox(14);
        row.setMaxWidth(UiScale.px(1100));
        row.getChildren().addAll(
                tile(NeoText.get("colosseum.feeHead"), NeoText.get("colosseum.credits", NeoColosseum.fee()),
                        NeoText.get("colosseum.feeNote")),
                tile(NeoText.get("colosseum.prizeHead"), prize, NeoText.get("colosseum.prizeNote")),
                tile(NeoText.get("colosseum.walletHead"), NeoText.get("colosseum.credits", NeoQuest.credits()),
                        NeoColosseum.canAfford() ? "" : NeoText.get("colosseum.short",
                                NeoColosseum.fee() - NeoQuest.credits())));
        return row;
    }

    private static Region tile(final String head, final String value, final String note) {
        final Label h = new Label(head);
        h.getStyleClass().add("caption");
        final Label v = new Label(value);
        v.getStyleClass().add("quest-deck-name");
        v.setWrapText(true);
        final VBox box = new VBox(4, h, v);
        if (note != null && !note.isEmpty()) {
            final Label n = new Label(note);
            n.getStyleClass().add("home-subtitle");
            n.setWrapText(true);
            box.getChildren().add(n);
        }
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(12, 18, 12, 18));
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Los tres rivales, con su comandante a la vista. */
    private Region rivals() {
        final Label caption = new Label(NeoText.get("colosseum.rivals"));
        caption.getStyleClass().add("caption");
        final FlowPane row = new FlowPane(18, 14);
        final List<PaperCard> lineup = NeoColosseum.lineup();
        final double w = cardWidth;
        for (final PaperCard cmd : lineup) {
            final CardNode node = new CardNode(w);
            node.setCard(CardView.getCardForUi(cmd));
            node.setRotationEnabled(false);
            final Label name = new Label(forge.neo.card.CardText.nameOf(cmd));
            name.getStyleClass().add("tour-seat");
            // Hay comandantes de nombre largo ("Joshua, Phoenix's Dominant"):
            // en dos lineas antes que cortado.
            name.setWrapText(true);
            name.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            name.setMaxWidth(w);
            final VBox seat = new VBox(6, node, name);
            seat.setAlignment(Pos.TOP_CENTER);
            row.getChildren().add(seat);
        }
        final Label note = new Label(lineup.size() < NeoColosseum.RIVALS
                ? NeoText.get("colosseum.noRivals") : NeoText.get("colosseum.rivalsNote"));
        note.getStyleClass().add("home-subtitle");
        note.setWrapText(true);
        return new VBox(6, caption, row, note);
    }

    /** Con que mazo entras, y si no vale, por que. */
    private Region myDeck() {
        final Label caption = new Label(NeoText.get("colosseum.yourDeck"));
        caption.getStyleClass().add("caption");
        final Deck d = NeoQuest.currentDeck();
        final List<Node> parts = new ArrayList<>();
        final Label name = new Label(d == null ? NeoText.get("colosseum.noDeck") : deckLine(d));
        name.getStyleClass().add("quest-deck-name");
        name.setWrapText(true);
        parts.add(name);
        final String problem = NeoColosseum.deckProblem();
        if (d != null && problem != null) {
            final Label p = new Label(NeoText.get("colosseum.deckProblem", problem));
            p.getStyleClass().add("quest-problem");
            p.setWrapText(true);
            parts.add(p);
        }
        final VBox box = new VBox(4);
        box.getChildren().addAll(parts);
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(12, 18, 12, 18));
        return new VBox(6, caption, box);
    }

    private static String deckLine(final Deck d) {
        if (d.has(DeckSection.Commander)) {
            for (final PaperCard c : d.get(DeckSection.Commander).toFlatList()) {
                return d.getName() + "  ·  " + forge.neo.card.CardText.nameOf(c);
            }
        }
        return d.getName();
    }

    private static String tierName(final forge.gamemodes.quest.QuestEventDifficulty tier) {
        switch (tier) {
            case EASY:
                return NeoText.get("colosseum.tier.easy");
            case MEDIUM:
                return NeoText.get("colosseum.tier.medium");
            case HARD:
                return NeoText.get("colosseum.tier.hard");
            default:
                return NeoText.get("colosseum.tier.expert");
        }
    }

    private void confirmEnter() {
        final int fee = NeoColosseum.fee();
        final CardEdition ed = NeoColosseum.prizeEdition();
        final String prize = ed == null
                ? NeoText.get("colosseum.prizeCreditsOnly", NeoColosseum.prizeCredits())
                : NeoText.get(NeoColosseum.prizeTextKey(), NeoColosseum.prizeCredits(), ed.getName());
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("colosseum.confirmTitle"),
                NeoText.get("colosseum.confirmBody", fee, NeoQuest.credits() - fee, prize),
                List.of(NeoText.get("colosseum.confirmYes"), NeoText.get("common.cancel")), 1, i -> {
                    overlay.hide();
                    if (i == 0 && NeoColosseum.pay()) {
                        actions.play();
                    }
                }));
    }

    /** Has ganado: el sobre abierto, con lo nuevo marcado, y los creditos. */
    public void showWin(final NeoColosseum.Prize prize) {
        // Se llama nada mas poner la pantalla: el panel del botin se mide con
        // el ancho Y el alto de la pantalla, asi que si aun no los tiene,
        // espera (el ancho llega antes que el alto: hay que mirar los dos).
        if (getWidth() <= 0 || getHeight() <= 0) {
            layoutBoundsProperty().addListener(new javafx.beans.value.ChangeListener<javafx.geometry.Bounds>() {
                @Override
                public void changed(final javafx.beans.value.ObservableValue<? extends javafx.geometry.Bounds> o,
                                    final javafx.geometry.Bounds was, final javafx.geometry.Bounds now) {
                    if (now.getWidth() > 0 && now.getHeight() > 0) {
                        layoutBoundsProperty().removeListener(this);
                        showWin(prize);
                    }
                }
            });
            return;
        }
        final List<PaperCard> cards = prize.pack == null ? List.of() : prize.pack.getCards();
        final NeoQuestShop.Opened pack = prize.pack;
        final List<String> notes = new ArrayList<>();
        notes.add(NeoText.get("colosseum.noteCredits", prize.credits));
        notes.add(NeoText.get("colosseum.noteWin"));
        if (pack == null) {
            notes.add(NeoText.get(NeoColosseum.collectorPrize() ? "colosseum.noteNoPack" : "colosseum.noteNoBooster"));
        }
        overlay.setOnBackgroundClick(null);
        overlay.show(CardHaul.panel(NeoText.get("colosseum.wonTitle"), NeoText.get("colosseum.wonSub"),
                cards, c -> pack != null && pack.isNew(c), notes, cardWidth, getWidth(), getHeight(),
                overlay::hide));
    }

    /** Has perdido: la entrada se queda, y hay rivales nuevos. */
    public void showLoss() {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("colosseum.lostTitle"), NeoText.get("colosseum.lostBody"),
                List.of(NeoText.get("common.accept")), 0, i -> overlay.hide()));
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
