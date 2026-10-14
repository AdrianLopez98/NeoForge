package forge.neo.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.deck.Deck;
import forge.gamemodes.quest.QuestEventDraft;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.quest.NeoQuest;
import forge.neo.quest.NeoQuestDrafts;
import forge.player.GamePlayerUtil;
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
 * <b>Los torneos de draft de la Quest</b>: los que se ofrecen, y el que estes
 * jugando — el mazo por montar, el cuadro de tres rondas y el premio.
 *
 * <p>Toda la logica vive en {@link NeoQuestDrafts}; aqui solo se ensenya y se
 * pregunta. Tres cosas que no se deducen leyendo el codigo:
 *
 * <ul>
 *   <li><b>El cuadro se ve entero</b> desde la primera ronda: los ocho
 *       nombres, quien gano cada partida de las IA (que se simulan) y donde
 *       estas tu. Es lo que convierte "juega la siguiente" en un torneo.</li>
 *   <li><b>Retirarse va a la izquierda</b> y pregunta (principio 12 de
 *       las notas de diseño): no se deshace. Jugar, la accion principal, va junto a
 *       Volver.</li>
 *   <li><b>Cobrar cierra el torneo en el acto</b> ({@link NeoQuestDrafts#collect}):
 *       la rara se elige despues, y si el juego se cierra entonces se pierde
 *       la rara y nada mas — nunca se cobra dos veces.</li>
 * </ul>
 */
public class QuestTournamentScreen extends StackPane {

    /** Lo que esta pantalla pide fuera. */
    public interface Actions {
        /** Entrar en ese torneo (ya confirmado): abrir los sobres. */
        void enter(QuestEventDraft event);

        /** Montar o retocar el mazo del torneo. */
        void editDeck();

        /** Jugar la partida que toca. */
        void play();

        /** Volver a pintar esta pantalla (tras empezar). */
        void refresh();

        /** Al cuartel general. */
        void back();
    }

    private final double cardWidth;
    private final Actions actions;
    private final Overlay overlay = new Overlay();
    private final BorderPane frame = new BorderPane();

    public QuestTournamentScreen(final double cardWidth, final Actions actions) {
        this.cardWidth = cardWidth;
        this.actions = actions;
        getStyleClass().addAll("table-root", "quest");
        build();
        getChildren().addAll(frame, overlay);
        overlay.hide();
        CardZoom.install(this);
    }

    private void build() {
        final NeoQuestDrafts.Stage stage = NeoQuestDrafts.stage();
        final QuestEventDraft current = NeoQuestDrafts.current();

        final Label title = new Label(current == null ? NeoText.get("questDraft.title")
                : current.getTitle());
        title.getStyleClass().add("home-title");
        final Label sub = new Label(current == null
                ? NeoText.get("questDraft.subtitle", NeoQuest.credits())
                : String.join("  ·  ", NeoQuestDrafts.boosterSummary(current)));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        final VBox head = new VBox(2, title, sub);
        head.setPadding(new Insets(20, 30, 8, 30));
        frame.setTop(head);

        final VBox body = new VBox(16);
        body.setPadding(new Insets(8, 30, 8, 30));
        final List<Node> left = new ArrayList<>();
        Button primary = null;

        switch (stage) {
            case NONE:
                if (NeoQuestDrafts.tokens() > 0) {
                    body.getChildren().add(tokens());
                }
                body.getChildren().addAll(offered(), explain());
                break;
            case DECK:
                body.getChildren().addAll(deckStep(), poolStrip());
                // Lo que toca hacer va en el boton grande: con el mazo sin
                // montar es montarlo (no un "Empezar" apagado y la accion de
                // verdad escondida a la izquierda).
                primary = NeoQuestDrafts.deckProblem() == null
                        ? button(NeoText.get("questDraft.start"), "btn-primary", this::start)
                        : button(NeoText.get("questDraft.buildDeck"), "btn-primary", actions::editDeck);
                left.add(button(NeoText.get("questDraft.leave"), "btn-danger", this::confirmLeave));
                break;
            case PLAYING:
                body.getChildren().addAll(status(), bracket());
                final NeoQuestDrafts.Pairing next = NeoQuestDrafts.nextMatch();
                primary = button(next == null ? NeoText.get("questDraft.play")
                        : NeoText.get("questDraft.playAgainst", rivalName(next)), "btn-primary", actions::play);
                primary.setDisable(next == null || NeoQuestDrafts.deckProblem() != null);
                left.add(button(NeoText.get("questDraft.withdraw"), "btn-danger", this::confirmWithdraw));
                break;
            case FINISHED:
            default:
                body.getChildren().addAll(status(), bracket());
                primary = button(NeoText.get("questDraft.collect"), "btn-primary", null);
                final Button collect = primary;
                collect.setOnAction(e -> {
                    // Un solo clic: cobrar no se repite.
                    collect.setDisable(true);
                    collectPrizes();
                });
                break;
        }
        if (stage == NeoQuestDrafts.Stage.PLAYING
                || stage == NeoQuestDrafts.Stage.DECK && NeoQuestDrafts.deckProblem() == null) {
            left.add(button(NeoText.get("questDraft.editDeck"), "btn-secondary", actions::editDeck));
        }

        final ScrollPane sp = new ScrollPane(body);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        frame.setCenter(sp);
        frame.setBottom(footer(left, primary));
    }

    /** Volver a la derecha y, justo a su izquierda, la accion principal. */
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

    // ---------------------------------------------------------------
    // Los que se ofrecen

    private Region offered() {
        final Label caption = new Label(NeoText.get("questDraft.open"));
        caption.getStyleClass().add("caption");
        final FlowPane row = new FlowPane(14, 14);
        final List<QuestEventDraft> events = NeoQuestDrafts.offered();
        for (final QuestEventDraft e : events) {
            row.getChildren().add(eventTile(e));
        }
        if (events.isEmpty()) {
            final Label none = new Label(NeoText.get("questDraft.none", winsPerDraft()));
            none.getStyleClass().add("home-subtitle");
            none.setWrapText(true);
            row.getChildren().add(none);
        }
        return new VBox(6, caption, row);
    }

    private Region eventTile(final QuestEventDraft e) {
        final Label name = new Label(e.getTitle());
        name.getStyleClass().add("quest-deck-name");
        name.setWrapText(true);
        final Label packs = new Label(String.join("\n", NeoQuestDrafts.boosterSummary(e)));
        packs.getStyleClass().add("home-subtitle");
        packs.setWrapText(true);
        final boolean afford = NeoQuestDrafts.canAfford(e);
        final Button enter = button(NeoText.get("questDraft.enter", e.getEntryFee()), "btn-primary",
                () -> confirmEnter(e));
        enter.setDisable(!afford);
        final VBox box = new VBox(8, name, packs, enter);
        if (!afford) {
            final Label poor = new Label(NeoText.get("questDraft.cantAfford",
                    e.getEntryFee() - NeoQuest.credits()));
            poor.getStyleClass().add("quest-problem");
            poor.setWrapText(true);
            box.getChildren().add(poor);
        }
        box.getStyleClass().add("duel-tile");
        box.setPadding(new Insets(14));
        box.setPrefWidth(UiScale.px(330));
        return box;
    }

    /**
     * Las fichas de draft (se gana una quedando 1º): crear un torneo del
     * formato que elijas. Es lo que en Forge hace "Spend Token".
     */
    private Region tokens() {
        final Label what = new Label(NeoText.get("questDraft.tokens", NeoQuestDrafts.tokens()));
        what.getStyleClass().add("quest-ok");
        what.setWrapText(true);
        final Button use = button(NeoText.get("questDraft.useToken"), "btn-secondary", this::pickFormat);
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(14, what, gap, use);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stat-tile");
        row.setPadding(new Insets(12, 18, 12, 18));
        return row;
    }

    private void pickFormat() {
        final Label t = new Label(NeoText.get("questDraft.tokenTitle"));
        t.getStyleClass().add("dialog-title");
        final FlowPane list = new FlowPane(8, 8);
        for (final QuestEventDraft.QuestDraftFormat f : NeoQuestDrafts.formats()) {
            list.getChildren().add(button(f.toString(), "btn-secondary", () -> {
                overlay.hide();
                if (NeoQuestDrafts.spendToken(f)) {
                    actions.refresh();
                }
            }));
        }
        final ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("dialog-scroll");
        sp.setPrefViewportHeight(Math.max(200, getHeight() * 0.6));
        final Button cancel = button(NeoText.get("common.cancel"), "btn-secondary", overlay::hide);
        final VBox box = new VBox(10, t, sp, cancel);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(18));
        box.setMaxWidth(Math.max(400, getWidth() * 0.8));
        box.setMaxHeight(Region.USE_PREF_SIZE);
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(box);
    }

    private Region explain() {
        final Label how = new Label(NeoText.get("questDraft.how", winsPerDraft()));
        how.getStyleClass().add("home-subtitle");
        how.setWrapText(true);
        how.setMaxWidth(UiScale.px(820));
        return how;
    }

    private static int winsPerDraft() {
        return FModel.getQuestPreferences().getPrefInt(
                forge.gamemodes.quest.data.QuestPreferences.QPref.WINS_NEW_DRAFT);
    }

    private void confirmEnter(final QuestEventDraft e) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("questDraft.enterTitle", e.getTitle()),
                NeoText.get("questDraft.enterBody", e.getEntryFee(), NeoQuest.credits()),
                List.of(NeoText.get("questDraft.enterYes"), NeoText.get("common.cancel")), 1, i -> {
                    overlay.hide();
                    if (i == 0) {
                        actions.enter(e);
                    }
                }));
    }

    // ---------------------------------------------------------------
    // El mazo

    private Region deckStep() {
        final Deck d = NeoQuestDrafts.myDeck();
        final int main = d == null ? 0 : d.getMain().countAll();
        final int pool = NeoQuestDrafts.poolSize();
        final String problem = NeoQuestDrafts.deckProblem();
        final Label what = new Label(NeoText.get("questDraft.deckStep", pool));
        what.getStyleClass().add("home-subtitle");
        what.setWrapText(true);
        final Label state = new Label(problem == null ? NeoText.get("quest.deckReady", main)
                : NeoText.get("questDraft.deckProblem", main,
                        forge.neo.deck.DeckProblem.translate(problem)));
        state.getStyleClass().add(problem == null ? "quest-ok" : "quest-problem");
        state.setWrapText(true);
        final VBox box = new VBox(6, what, state);
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(14));
        return box;
    }

    /** Lo que has drafteado, para verlo (clic derecho para leer). */
    private Region poolStrip() {
        final Deck d = NeoQuestDrafts.myDeck();
        final FlowPane grid = new FlowPane(8, 8);
        if (d != null) {
            final double w = cardWidth * 0.62;
            for (final PaperCard c : d.getAllCardsInASinglePool().toFlatList()) {
                if (c.getRules() != null && c.getRules().getType().isBasicLand()) {
                    continue;
                }
                final CardNode node = new CardNode(w);
                node.setCard(CardView.getCardForUi(c));
                node.setRotationEnabled(false);
                grid.getChildren().add(node);
            }
        }
        final Label caption = new Label(NeoText.get("questDraft.poolCaption"));
        caption.getStyleClass().add("caption");
        return new VBox(6, caption, grid);
    }

    private void start() {
        if (NeoQuestDrafts.start()) {
            actions.refresh();
        }
    }

    // ---------------------------------------------------------------
    // El cuadro

    private static String human() {
        return GamePlayerUtil.getGuiPlayer().getName();
    }

    private static String rivalName(final NeoQuestDrafts.Pairing p) {
        return NeoQuestDrafts.nameOf(p.rivalOfHuman(), human());
    }

    /** "segundo", "tercero"...: el puesto dicho con palabras. */
    private static String placeWord(final int place) {
        return NeoText.get("questDraft.place." + Math.max(1, Math.min(4, place)));
    }

    private Region status() {
        final QuestEventDraft e = NeoQuestDrafts.current();
        final NeoQuestDrafts.Pairing next = NeoQuestDrafts.nextMatch();
        final String text;
        if (next != null) {
            text = NeoText.get("questDraft.next", next.round, rivalName(next));
        } else if (e != null && e.getPlayerPlacement() == 1) {
            text = NeoText.get("questDraft.champion");
        } else {
            text = NeoText.get("questDraft.placed", placeWord(e == null ? 4 : e.getPlayerPlacement()));
        }
        final Label l = new Label(text);
        l.getStyleClass().add("quest-deck-name");
        l.setWrapText(true);
        return l;
    }

    private Region bracket() {
        final List<NeoQuestDrafts.Pairing> all = NeoQuestDrafts.bracket();
        final HBox cols = new HBox(22);
        cols.setAlignment(Pos.CENTER_LEFT);
        for (int round = 1; round <= 3; round++) {
            final VBox col = new VBox(12);
            col.setAlignment(Pos.CENTER);
            final Label head = new Label(NeoText.get("questDraft.round" + round));
            head.getStyleClass().add("caption");
            col.getChildren().add(head);
            for (final NeoQuestDrafts.Pairing p : all) {
                if (p.round == round) {
                    col.getChildren().add(pairingBox(p));
                }
            }
            cols.getChildren().add(col);
        }
        // El campeon, al final.
        final NeoQuestDrafts.Pairing fin = all.isEmpty() ? null : all.get(all.size() - 1);
        final VBox champ = new VBox(12);
        champ.setAlignment(Pos.CENTER);
        final Label head = new Label(NeoText.get("questDraft.championHead"));
        head.getStyleClass().add("caption");
        final Label who = new Label(fin == null || fin.winner == null ? "?"
                : NeoQuestDrafts.nameOf(fin.winner, human()));
        who.getStyleClass().add("quest-deck-name");
        champ.getChildren().addAll(head, who);
        cols.getChildren().add(champ);
        return cols;
    }

    private Region pairingBox(final NeoQuestDrafts.Pairing p) {
        final VBox box = new VBox(4, seat(p, p.a), seat(p, p.b));
        box.getStyleClass().add("duel-tile");
        if (p.hasHuman()) {
            box.getStyleClass().add("tier-medium");
        }
        box.setPadding(new Insets(8, 12, 8, 12));
        box.setPrefWidth(UiScale.px(200));
        return box;
    }

    /**
     * Un nombre del cuadro: en claro si aun no se ha jugado, en verde quien
     * gano, apagado quien perdio, y tu en negrita (clases {@code tour-seat*}
     * de neo.css).
     */
    private static Label seat(final NeoQuestDrafts.Pairing p, final String sid) {
        final String name = QuestEventDraft.UNDETERMINED.equals(sid) ? "…" : NeoQuestDrafts.nameOf(sid, human());
        final Label l = new Label(name);
        l.setWrapText(false);
        l.getStyleClass().add("tour-seat");
        if (p.winner != null) {
            l.getStyleClass().add(sid.equals(p.winner) ? "tour-seat-won" : "tour-seat-lost");
        }
        if (QuestEventDraft.HUMAN.equals(sid)) {
            l.getStyleClass().add("tour-seat-me");
        }
        return l;
    }

    // ---------------------------------------------------------------
    // Irse y cobrar

    private void confirmLeave() {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("questDraft.leaveTitle"),
                NeoText.get("questDraft.leaveBody"),
                List.of(NeoText.get("questDraft.leaveYes"), NeoText.get("common.cancel")), 1, i -> {
                    overlay.hide();
                    if (i == 0) {
                        collectPrizes();
                    }
                }));
    }

    private void confirmWithdraw() {
        final QuestEventDraft e = NeoQuestDrafts.current();
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("questDraft.withdrawTitle"),
                NeoText.get("questDraft.withdrawBody", placeWord(e == null ? 4 : e.getPlayerPlacement())),
                List.of(NeoText.get("questDraft.withdrawYes"), NeoText.get("common.cancel")), 1, i -> {
                    overlay.hide();
                    if (i == 0) {
                        collectPrizes();
                    }
                }));
    }

    /** Cobra (y cierra) y ensenya lo que te llevas; en 1º y 2º, antes la rara. */
    private void collectPrizes() {
        final QuestEventDraft e = NeoQuestDrafts.current();
        final String eventTitle = e == null ? "" : e.getTitle();
        final boolean started = e != null && e.isStarted();
        final Set<String> had = new HashSet<>();
        for (final java.util.Map.Entry<PaperCard, Integer> c : FModel.getQuest().getCards().getCardpool()) {
            had.add(c.getKey().getName());
        }
        final NeoQuestDrafts.Prizes prizes = NeoQuestDrafts.collect();
        if (prizes == null) {
            actions.back();
            return;
        }
        if (!prizes.rareChoices.isEmpty()) {
            pickRare(prizes, eventTitle, had, started);
        } else {
            showHaul(prizes, null, eventTitle, had, started);
        }
    }

    /** Elige una rara: las del set del torneo, una carta cada una. */
    private void pickRare(final NeoQuestDrafts.Prizes prizes, final String eventTitle,
                          final Set<String> had, final boolean started) {
        final Label t = new Label(NeoText.get("questDraft.rareTitle"));
        t.getStyleClass().add("dialog-title");
        final Label s = new Label(NeoText.get("questDraft.rareBody"));
        s.getStyleClass().add("home-subtitle");
        s.setWrapText(true);
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.CENTER);
        final double w = cardWidth * 0.95;
        for (final PaperCard card : prizes.rareChoices) {
            final CardNode node = new CardNode(w);
            node.setCard(CardView.getCardForUi(card));
            node.setRotationEnabled(false);
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(ev -> {
                if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    NeoQuestDrafts.takeRare(card);
                    showHaul(prizes, card, eventTitle, had, started);
                }
            });
            grid.getChildren().add(node);
        }
        final ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("dialog-scroll");
        sp.setPrefViewportHeight(Math.max(200, getHeight() * 0.62));
        final VBox box = new VBox(10, t, s, sp);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(18));
        box.setMaxWidth(Math.max(400, getWidth() * 0.86));
        box.setMaxHeight(Region.USE_PREF_SIZE);
        // Sin cerrar tocando fuera: hay que elegir una.
        overlay.setOnBackgroundClick(null);
        overlay.show(box);
    }

    private void showHaul(final NeoQuestDrafts.Prizes prizes, final PaperCard rare, final String eventTitle,
                          final Set<String> had, final boolean started) {
        final List<PaperCard> cards = new ArrayList<>();
        if (rare != null) {
            cards.add(rare);
        }
        cards.addAll(prizes.cards);
        final List<String> notes = new ArrayList<>();
        if (started && prizes.credits > 0) {
            notes.add(NeoText.get("questDraft.noteCredits", prizes.credits));
        }
        if (prizes.token) {
            notes.add(NeoText.get("questDraft.noteToken"));
        }
        notes.add(NeoText.get("questDraft.notePool"));
        if (started && NeoQuest.modalidad() != NeoQuest.Modalidad.COMMANDER) {
            notes.add(NeoText.get("questDraft.noteDeck"));
        }
        final String title = !started ? NeoText.get("questDraft.leftTitle")
                : prizes.place == 1 ? NeoText.get("questDraft.champion")
                : NeoText.get("questDraft.placed", placeWord(prizes.place));
        overlay.setOnBackgroundClick(null);
        overlay.show(CardHaul.panel(title, eventTitle, cards, c -> !had.contains(c.getName()), notes,
                cardWidth, getWidth(), getHeight(), actions::back));
    }

    /**
     * Solo pruebas ({@code --quest-tournament}): cobra como el boton y, con
     * {@code pickFirst}, elige la primera rara.
     */
    public void debugCollect(final boolean pickFirst) {
        final QuestEventDraft e = NeoQuestDrafts.current();
        final String eventTitle = e == null ? "" : e.getTitle();
        final boolean started = e != null && e.isStarted();
        final NeoQuestDrafts.Prizes prizes = NeoQuestDrafts.collect();
        if (prizes == null) {
            return;
        }
        if (pickFirst || prizes.rareChoices.isEmpty()) {
            final PaperCard rare = prizes.rareChoices.isEmpty() ? null : prizes.rareChoices.get(0);
            NeoQuestDrafts.takeRare(rare);
            showHaul(prizes, rare, eventTitle, new HashSet<>(), started);
        } else {
            pickRare(prizes, eventTitle, new HashSet<>(), started);
        }
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
