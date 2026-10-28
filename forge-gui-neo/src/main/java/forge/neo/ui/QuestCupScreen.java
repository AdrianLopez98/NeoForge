package forge.neo.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.card.CardView;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.quest.NeoQuest;
import forge.neo.quest.NeoQuestCup;
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
 * <b>El torneo de la Quest</b> ({@link NeoQuestCup}): lo que es, lo que cuesta
 * y lo que se gana con tu nivel de ahora, antes de pagar; luego el cuadro, tu
 * partida y el premio. Los de draft siguen en su pantalla.
 */
public class QuestCupScreen extends StackPane {

    /** Lo que esta pantalla pide fuera. */
    public interface Actions {
        /** Jugar la partida que toca. */
        void play();

        /** Volver a pintarla (tras cerrar o retirarse). */
        void refresh();

        /** Al cuartel general. */
        void back();

        /**
         * Mirar otra mesa de la ronda entre IAs (28-10-2026): su ganador pasa
         * de verdad. Ver {@link NeoQuestCup#watchableTables}.
         */
        default void watch(final int table) {
        }
    }

    private final double cardWidth;
    private final Actions actions;
    private final Overlay overlay = new Overlay();
    private final BorderPane frame = new BorderPane();

    public QuestCupScreen(final double cardWidth, final Actions actions) {
        this.cardWidth = cardWidth;
        this.actions = actions;
        getStyleClass().addAll("table-root", "quest");
        setId("quest-cup");
        // Si el juego se cerro a mitad de una ronda, aqui cuenta como perdida.
        NeoQuestCup.settleInterrupted();
        build();
        getChildren().addAll(frame, overlay);
        overlay.hide();
        CardZoom.install(this);
    }

    private void build() {
        final NeoQuestCup.Terms t = NeoQuestCup.terms();
        final NeoQuestCup.Stage stage = NeoQuestCup.stage();
        final Label title = new Label(NeoText.get(t.commander ? "cup.title.commander" : "cup.title.standard"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("cup.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        final VBox head = new VBox(2, title, sub);
        head.setPadding(new Insets(20, 30, 8, 30));
        frame.setTop(head);

        final VBox body = new VBox(16);
        body.setPadding(new Insets(8, 30, 8, 30));
        if (NeoQuestCup.takeInterruptedNotice()) {
            final Label closed = new Label(NeoText.get("cup.interrupted"));
            closed.getStyleClass().add("home-subtitle");
            closed.setWrapText(true);
            body.getChildren().add(closed);
        }
        final List<Node> left = new ArrayList<>();
        Button primary = null;
        switch (stage) {
            case OFFER:
                body.getChildren().addAll(explain(t), terms(t), myDeck());
                primary = button(NeoText.get("cup.enter", t.fee), "btn-primary", this::confirmEnter);
                primary.setId("cup-enter");
                primary.setDisable(!NeoQuestCup.canAfford() || NeoQuestCup.deckProblem() != null);
                break;
            case PLAYING:
                body.getChildren().add(explain(t));
                if (!NeoQuestCup.watchableTables().isEmpty()) {
                    // Mirar es opcional: lo que no mires, al azar como siempre.
                    final Label hint = new Label(NeoText.get("cup.watchHint"));
                    hint.getStyleClass().add("home-subtitle");
                    hint.setWrapText(true);
                    hint.setMaxWidth(UiScale.px(900));
                    body.getChildren().add(hint);
                }
                body.getChildren().addAll(bracket(t), myDeck());
                primary = button(NeoQuestCup.isFinal() ? NeoText.get("cup.playFinal")
                        : NeoText.get("cup.play", NeoQuestCup.round() + 1), "btn-primary", actions::play);
                primary.setId("cup-play");
                primary.setDisable(NeoQuestCup.deckProblem() != null);
                left.add(button(NeoText.get("cup.withdraw"), "btn-danger", this::confirmWithdraw));
                break;
            default:
                body.getChildren().addAll(resultLine(stage, t), bracket(t));
                if (!NeoQuestCup.pendingChoices().isEmpty()) {
                    primary = button(NeoText.get("cup.pickTitle"), "btn-primary",
                            () -> pickCard(NeoQuestCup.pendingChoices(), null));
                } else {
                    primary = button(NeoText.get("cup.close"), "btn-primary", () -> {
                        NeoQuestCup.dismiss();
                        actions.refresh();
                    });
                }
                primary.setId("cup-close");
                break;
        }
        final ScrollPane sp = new ScrollPane(body);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        frame.setCenter(sp);
        frame.setBottom(footer(left, primary));
    }

    /** Volver a la derecha y, justo a su izquierda, la accion principal (principio 12). */
    private Region footer(final List<Node> left, final Button primary) {
        final Button back = button(NeoText.get("common.back"), "btn-secondary", actions::back);
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox bar = new HBox(10);
        bar.getChildren().addAll(left);
        bar.getChildren().add(gap);
        if (primary != null) {
            bar.getChildren().add(primary);
        }
        bar.getChildren().add(back);
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

    private static Region explain(final NeoQuestCup.Terms t) {
        final Label how = new Label(NeoText.get(t.commander ? "cup.how.commander" : "cup.how.standard",
                tierName(t.rivalTier())));
        how.getStyleClass().add("home-subtitle");
        how.setWrapText(true);
        how.setMaxWidth(UiScale.px(900));
        return how;
    }

    /** El premio en una frase: lo que dan los {@link NeoQuestCup.Terms}. */
    public static String prizeText(final NeoQuestCup.Terms t) {
        final List<String> parts = new ArrayList<>();
        parts.add(NeoText.get("cup.prize.credits", t.prizeCredits));
        if (t.boosters > 0) {
            parts.add(NeoText.get("cup.prize.boosters", t.boosters));
        }
        if (t.collectors == 1) {
            parts.add(NeoText.get("cup.prize.collector"));
        } else if (t.collectors > 1) {
            parts.add(NeoText.get("cup.prize.collectors", t.collectors));
        }
        switch (t.pick) {
            case RARE:
                parts.add(NeoText.get("cup.prize.rare"));
                break;
            case MYTHIC:
                parts.add(NeoText.get("cup.prize.mythic"));
                break;
            case GAME_CHANGER:
                parts.add(NeoText.get("cup.prize.gameChanger"));
                break;
            default:
                break;
        }
        return String.join(" · ", parts);
    }

    private static Region terms(final NeoQuestCup.Terms t) {
        final HBox row = new HBox(14);
        row.setMaxWidth(UiScale.px(1200));
        row.getChildren().addAll(
                tile(NeoText.get("cup.levelHead"), tierName(t.tier), NeoText.get("cup.levelNote")),
                tile(NeoText.get("cup.feeHead"), NeoText.get("colosseum.credits", t.fee), NeoText.get("cup.feeNote")),
                tile(NeoText.get("cup.prizeHead"), prizeText(t), ""),
                tile(NeoText.get("colosseum.walletHead"), NeoText.get("colosseum.credits", NeoQuest.credits()),
                        NeoQuestCup.canAfford() ? "" : NeoText.get("colosseum.short", t.fee - NeoQuest.credits())));
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

    private static Region myDeck() {
        final Label caption = new Label(NeoText.get("colosseum.yourDeck"));
        caption.getStyleClass().add("caption");
        final Deck d = NeoQuest.currentDeck();
        final Label name = new Label(d == null ? NeoText.get("colosseum.noDeck") : deckLine(d));
        name.getStyleClass().add("quest-deck-name");
        name.setWrapText(true);
        final VBox box = new VBox(4, name);
        final String problem = NeoQuestCup.deckProblem();
        if (d != null && problem != null) {
            final Label p = new Label(NeoText.get("colosseum.deckProblem", problem));
            p.getStyleClass().add("quest-problem");
            p.setWrapText(true);
            box.getChildren().add(p);
        }
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

    static String tierName(final QuestEventDifficulty tier) {
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

    private static Region resultLine(final NeoQuestCup.Stage stage, final NeoQuestCup.Terms t) {
        final String text;
        switch (stage) {
            case CHAMPION:
                text = NeoText.get("cup.wonTitle");
                break;
            case RUNNER_UP:
                text = NeoText.get("cup.runnerUp", t.fee);
                break;
            default:
                text = NeoText.get("cup.eliminated");
                break;
        }
        final Label l = new Label(text);
        l.getStyleClass().add("quest-deck-name");
        l.setWrapText(true);
        return l;
    }

    // ---------------------------------------------------------------
    // El cuadro

    private Region bracket(final NeoQuestCup.Terms t) {
        final List<String> names = NeoQuestCup.seatNames();
        final HBox cols = new HBox(22);
        cols.setAlignment(Pos.CENTER_LEFT);
        Integer champion = null;
        for (int r = 0; r < t.rounds(); r++) {
            final VBox col = new VBox(12);
            col.setAlignment(Pos.CENTER);
            final Label head = new Label(r == t.rounds() - 1 ? NeoText.get("cup.final")
                    : NeoText.get("cup.round", r + 1));
            head.getStyleClass().add("caption");
            col.getChildren().add(head);
            final List<List<Integer>> groups = NeoQuestCup.groups(r);
            final List<Integer> won = NeoQuestCup.winnersOf(r);
            final List<Integer> watchable = r == NeoQuestCup.round() ? NeoQuestCup.watchableTables() : List.of();
            if (groups.size() > 4) {
                // 64 jugadores (28-10-2026): las 16 mesas de la primera ronda en
                // una rejilla de cuatro por fila, y cada fila al lado de la mesa
                // de la segunda ronda a la que van sus ganadores. En columna
                // eran dos mil pixeles de alto.
                final javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
                grid.setHgap(10);
                grid.setVgap(12);
                for (int g = 0; g < groups.size(); g++) {
                    grid.add(table(names, groups.get(g), NeoQuestCup.tableWinner(r, g), g, watchable),
                            g % 4, g / 4);
                }
                col.getChildren().add(grid);
            } else if (groups.isEmpty()) {
                // Aun no se sabe quien llega: huecos.
                final int count = t.seats() / (int) Math.pow(t.groupSize(), r + 1);
                for (int g = 0; g < Math.max(1, count); g++) {
                    final VBox box = new VBox(4);
                    for (int s = 0; s < t.groupSize(); s++) {
                        final Label l = new Label("…");
                        l.getStyleClass().add("tour-seat");
                        box.getChildren().add(l);
                    }
                    col.getChildren().add(groupBox(box, false));
                }
            } else {
                for (int g = 0; g < groups.size(); g++) {
                    col.getChildren().add(table(names, groups.get(g), NeoQuestCup.tableWinner(r, g), g, watchable));
                }
                if (r == t.rounds() - 1 && !won.isEmpty()) {
                    champion = won.get(0);
                }
            }
            cols.getChildren().add(col);
        }
        final VBox champ = new VBox(12);
        champ.setAlignment(Pos.CENTER);
        final Label head = new Label(NeoText.get("cup.champion"));
        head.getStyleClass().add("caption");
        final Label who = new Label(champion == null ? "?" : nameOf(names, champion));
        who.getStyleClass().add("quest-deck-name");
        champ.getChildren().addAll(head, who);
        cols.getChildren().add(champ);
        final ScrollPane sp = new ScrollPane(cols);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToHeight(true);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    /** Una mesa: sus asientos, y "Ver" si se puede mirar. */
    private Region table(final List<String> names, final List<Integer> group, final Integer winner,
                         final int index, final List<Integer> watchable) {
        final VBox box = new VBox(4);
        for (final int s : group) {
            box.getChildren().add(seat(names, s, winner));
        }
        if (watchable.contains(index)) {
            final Button see = button(NeoText.get("cup.watch"), "btn-secondary", () -> actions.watch(index));
            see.setId("cup-watch-" + index);
            box.getChildren().add(see);
        }
        return groupBox(box, group.contains(0));
    }

    private static Region groupBox(final VBox box, final boolean mine) {
        box.getStyleClass().add("duel-tile");
        if (mine) {
            box.getStyleClass().add("tier-medium");
        }
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setPrefWidth(UiScale.px(230));
        return box;
    }

    private static String nameOf(final List<String> names, final int seat) {
        return seat == 0 ? NeoText.get("cup.you") : seat < names.size() ? names.get(seat) : "?";
    }

    /** Un asiento: verde quien gano la mesa, apagado quien perdio, tu en negrita. */
    private static Label seat(final List<String> names, final int seat, final Integer winner) {
        final Label l = new Label(nameOf(names, seat));
        l.getStyleClass().add("tour-seat");
        if (winner != null) {
            l.getStyleClass().add(seat == winner ? "tour-seat-won" : "tour-seat-lost");
        }
        if (seat == 0) {
            l.getStyleClass().add("tour-seat-me");
        }
        return l;
    }

    // ---------------------------------------------------------------
    // Entrar, retirarse y el resultado

    private void confirmEnter() {
        final NeoQuestCup.Terms t = NeoQuestCup.terms();
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("cup.confirmTitle"),
                NeoText.get("cup.confirmBody", t.fee, NeoQuest.credits() - t.fee, prizeText(t)),
                List.of(NeoText.get("cup.confirmYes"), NeoText.get("common.cancel")), 1, i -> {
                    overlay.hide();
                    if (i == 0 && NeoQuestCup.pay()) {
                        actions.refresh();
                    }
                }));
    }

    private void confirmWithdraw() {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("cup.withdrawTitle"), NeoText.get("cup.withdrawBody"),
                List.of(NeoText.get("cup.withdrawYes"), NeoText.get("common.cancel")), 1, i -> {
                    overlay.hide();
                    if (i == 0) {
                        NeoQuestCup.withdraw();
                        actions.refresh();
                    }
                }));
    }

    /** Tras tu partida: pasar de ronda, quedar fuera, la final perdida o el premio. */
    public void showResult(final NeoQuestCup.Result r) {
        if (getWidth() <= 0 || getHeight() <= 0) {
            layoutBoundsProperty().addListener(new javafx.beans.value.ChangeListener<javafx.geometry.Bounds>() {
                @Override
                public void changed(final javafx.beans.value.ObservableValue<? extends javafx.geometry.Bounds> o,
                                    final javafx.geometry.Bounds was, final javafx.geometry.Bounds now) {
                    if (now.getWidth() > 0 && now.getHeight() > 0) {
                        layoutBoundsProperty().removeListener(this);
                        showResult(r);
                    }
                }
            });
            return;
        }
        if (r == null) {
            return;
        }
        switch (r.stage) {
            case CHAMPION:
                if (r.prize != null && !r.prize.choices.isEmpty()) {
                    pickCard(r.prize.choices, r.prize);
                } else {
                    showHaul(r.prize, null);
                }
                break;
            case RUNNER_UP:
                message(NeoText.get("cup.runnerUpTitle"), NeoText.get("cup.runnerUp", r.refunded));
                break;
            case ELIMINATED:
                message(NeoText.get("cup.lostTitle"), NeoText.get("cup.eliminated"));
                break;
            default:
                message(NeoText.get("cup.advancedTitle"), NeoQuestCup.isFinal()
                        ? NeoText.get("cup.advancedFinal") : NeoText.get("cup.advanced", NeoQuestCup.round() + 1));
                break;
        }
    }

    private void message(final String title, final String body) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(title, body, List.of(NeoText.get("common.accept")), 0, i -> overlay.hide()));
    }

    /** Elige una de tres: hay que elegir, no se cierra tocando fuera. */
    private void pickCard(final List<PaperCard> choices, final NeoQuestCup.Prize prize) {
        final Label t = new Label(NeoText.get("cup.pickTitle"));
        t.getStyleClass().add("dialog-title");
        final Label s = new Label(NeoText.get("cup.pickBody"));
        s.getStyleClass().add("home-subtitle");
        s.setWrapText(true);
        final FlowPane grid = new FlowPane(14, 14);
        grid.setAlignment(Pos.CENTER);
        final double w = cardWidth * 1.15;
        for (final PaperCard card : choices) {
            final CardNode node = new CardNode(w);
            node.setCard(CardView.getCardForUi(card));
            node.setRotationEnabled(false);
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(ev -> {
                if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY && NeoQuestCup.take(card)) {
                    showHaul(prize, card);
                }
            });
            grid.getChildren().add(node);
        }
        final VBox box = new VBox(12, t, s, grid);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(18));
        box.setMaxWidth(Math.max(400, getWidth() * 0.86));
        box.setMaxHeight(Region.USE_PREF_SIZE);
        overlay.setOnBackgroundClick(null);
        overlay.show(box);
    }

    /** Lo ganado: la carta elegida y los sobres, con lo nuevo marcado. */
    private void showHaul(final NeoQuestCup.Prize prize, final PaperCard picked) {
        final List<PaperCard> cards = new ArrayList<>();
        final Set<PaperCard> fresh = new HashSet<>();
        if (picked != null) {
            cards.add(picked);
            fresh.add(picked);
        }
        final List<String> notes = new ArrayList<>();
        if (prize != null) {
            for (final NeoQuestShop.Opened o : prize.packs) {
                for (final PaperCard c : o.getCards()) {
                    cards.add(c);
                    if (o.isNew(c)) {
                        fresh.add(c);
                    }
                }
            }
            notes.add(NeoText.get("colosseum.noteCredits", prize.credits));
        }
        notes.add(NeoText.get("colosseum.noteWin"));
        overlay.setOnBackgroundClick(null);
        overlay.show(CardHaul.panel(NeoText.get("cup.wonTitle"),
                NeoText.get("cup.wonSub", prize == null ? 0 : prize.credits), cards, fresh::contains, notes,
                cardWidth, getWidth(), getHeight(), () -> {
                    overlay.hide();
                    actions.refresh();
                }));
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
