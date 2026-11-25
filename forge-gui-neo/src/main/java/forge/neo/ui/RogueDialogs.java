package forge.neo.ui;

import forge.deck.Deck;
import forge.game.card.CardView;
import forge.gamemodes.rogue.BazaarPricing;
import forge.gamemodes.rogue.RogueEvent;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.effect.BazaarItem;
import forge.gamemodes.rogue.effect.ChestEffect;
import forge.gamemodes.rogue.effect.RogueEffect;
import forge.gamemodes.rogue.effect.SanctumContext;
import forge.gamemodes.rogue.npc.NPC;
import forge.gamemodes.rogue.npc.NPCContext;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.match.TableBinder;
import forge.neo.rogue.RoguePrompts;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Las preguntas de Rogue Commander ({@link RoguePrompts}) en pantalla.
 *
 * <p>Cada metodo lo llama el HILO DE LA RUN y bloquea: pinta el dialogo en la
 * capa de preguntas de la pantalla que haya ({@link RogueScreen#dialogs()}) y
 * espera a que el jugador conteste. Nunca se llama desde el hilo de JavaFX
 * (ver {@code NeoAppRogue}, que lanza todo lo del modo en su propio hilo).
 *
 * <p>Equivalen a los dialogos Swing de Forge ({@code CardRewardDialog},
 * {@code BazaarDialog}, {@code EventDialog}, {@code ChestDialog},
 * {@code SanctumDialog}, {@code NPCDialog}...), con nuestra estetica: las
 * cartas son {@link CardNode} de verdad (se amplian con el clic derecho), y
 * "Ver mazo" va en la capa de CONSULTA, que no borra la pregunta.
 */
public final class RogueDialogs implements RoguePrompts {

    /** Lo que los dialogos necesitan de la aplicacion. */
    public interface Host {
        /** La pantalla del modo que esta puesta ahora (donde se pintan las preguntas). */
        RogueScreen screen();

        /** Pone la mesa para un duelo y devuelve su puente. Bloquea hasta que esta. */
        TableBinder openTable();

        /** Vuelve del duelo a la pantalla del modo. */
        void closeTable();

        double cardWidth();
    }

    private final Host host;

    public RogueDialogs(final Host host) {
        this.host = host;
    }

    // ------------------------------------------------------------------
    // La espera
    // ------------------------------------------------------------------

    /**
     * Pinta lo que devuelve {@code build} en la capa de preguntas y espera la
     * respuesta. {@code build} recibe con que contestar.
     */
    private <T> T ask(final Function<Consumer<T>, Region> build) {
        if (Platform.isFxApplicationThread()) {
            throw new IllegalStateException("RogueDialogs desde el hilo de JavaFX: se colgaria la ventana");
        }
        final CompletableFuture<T> answer = new CompletableFuture<>();
        Platform.runLater(() -> {
            final RogueScreen screen = host.screen();
            if (screen == null) {
                answer.complete(null);
                return;
            }
            final Overlay layer = screen.dialogs();
            final Region content = build.apply(value -> {
                layer.hide();
                answer.complete(value);
            });
            layer.setPeekable(NeoText.get("rogue.peekMap"), NeoText.get("rogue.backToChoice"));
            layer.show(content);
        });
        try {
            return answer.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (final java.util.concurrent.ExecutionException e) {
            return null;
        }
    }

    private double cw() {
        return host.cardWidth();
    }

    // ------------------------------------------------------------------
    // Piezas
    // ------------------------------------------------------------------

    private static VBox dialogBox(final String title) {
        final VBox box = new VBox(12);
        box.getStyleClass().addAll("dialog", "rogue-dialog");
        box.setPadding(new Insets(18));
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        if (title != null && !title.isBlank()) {
            final Label t = new Label(title);
            t.getStyleClass().add("dialog-title");
            t.setWrapText(true);
            t.setMaxWidth(UiScale.px(820));
            box.getChildren().add(t);
        }
        return box;
    }

    private static Label text(final String s) {
        final Label l = new Label(s == null ? "" : clean(s));
        l.getStyleClass().add("dialog-text");
        l.setWrapText(true);
        l.setMaxWidth(UiScale.px(820));
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    /** Los textos de Forge llevan marcas de resaltado (**carta**, {{palabra}}) y a veces HTML. */
    static String clean(final String s) {
        return forge.neo.rogue.RogueText.clean(s);
    }

    /** Un boton de dos lineas: lo que es, y que hace (como {@code RogueButtonHelper.createChoiceButton}). */
    private static Choice choice(final String label, final String description) {
        return choice(label, description, 640);
    }

    private static Choice choice(final String label, final String description, final double width) {
        return new Choice(label, description, UiScale.px(width));
    }

    /**
     * Una opcion de dos lineas, lo que es y que hace (como
     * {@code RogueButtonHelper.createChoiceButton}). No es un {@code Button}:
     * un boton con un grafico de varios textos con salto de linea calcula mal su
     * alto (salia altisimo); un VBox reparte el ancho a sus textos como toca.
     */
    static final class Choice extends VBox {
        private Runnable pick;

        Choice(final String label, final String description, final double width) {
            super(2);
            getStyleClass().add("rogue-choice");
            setMaxWidth(Double.MAX_VALUE);
            setPrefWidth(width);
            final Label top = new Label(clean(label));
            top.getStyleClass().add("rogue-choice-label");
            top.setWrapText(true);
            getChildren().add(top);
            if (description != null && !description.isBlank()) {
                addLine(description);
            }
            setCursor(javafx.scene.Cursor.HAND);
            setOnMouseClicked(e -> {
                if (!isDisabled() && pick != null && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    pick.run();
                }
            });
        }

        void addLine(final String text) {
            final Label sub = new Label(clean(text));
            sub.getStyleClass().add("rogue-choice-desc");
            sub.setWrapText(true);
            getChildren().add(sub);
        }

        void onPick(final Runnable action) {
            this.pick = action;
        }
    }
    private static Button button(final String text, final String style, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setOnAction(e -> action.run());
        return b;
    }

    private static HBox row(final javafx.scene.Node... nodes) {
        final HBox r = new HBox(10, nodes);
        r.setAlignment(Pos.CENTER_RIGHT);
        return r;
    }

    private static Region grow() {
        final Region g = new Region();
        HBox.setHgrow(g, Priority.ALWAYS);
        return g;
    }

    private CardNode card(final PaperCard pc, final double width) {
        final CardNode node = new CardNode(width);
        node.setRotationEnabled(false);
        node.setCard(CardView.getCardForUi(pc));
        return node;
    }

    private FlowPane cards(final List<PaperCard> list, final double width) {
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.CENTER);
        grid.setPrefWrapLength(UiScale.px(860));
        for (final PaperCard pc : list) {
            if (pc != null) {
                grid.getChildren().add(card(pc, width));
            }
        }
        return grid;
    }

    private static ScrollPane scroll(final Region content, final double maxHeight) {
        final ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("rogue-scroll");
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setMaxHeight(maxHeight);
        sp.setPrefViewportWidth(UiScale.px(880));
        return sp;
    }

    /** "Ver mazo": el mazo de la run, en la capa de CONSULTA (no borra la pregunta). */
    private Button viewDeck(final Supplier<Deck> deck) {
        return button(NeoText.get("rogue.viewDeck"), "btn-secondary", () -> showDeck(deck.get()));
    }

    private void showDeck(final Deck deck) {
        final RogueScreen screen = host.screen();
        if (screen == null || deck == null) {
            return;
        }
        final VBox box = dialogBox(NeoText.get("rogue.deckTitle", deck.getMain().countAll()));
        final List<PaperCard> all = new ArrayList<>(deck.getCommanders());
        all.addAll(deck.getMain().toFlatList());
        final AscentDeckView view = new AscentDeckView(all, cw() * 0.8, null);
        view.setPrefViewportHeight(UiScale.px(560));
        view.setPrefViewportWidth(UiScale.px(900));
        box.getChildren().addAll(view, row(button(NeoText.get("rogue.close"), "btn-primary",
            () -> screen.consult().hide())));
        screen.consult().setOnBackgroundClick(() -> screen.consult().hide());
        screen.consult().show(box);
    }

    private static Deck deckOf(final RogueRun run) {
        return run == null ? null : run.getCurrentDeck();
    }

    // ------------------------------------------------------------------
    // RoguePrompts
    // ------------------------------------------------------------------

    @Override
    public void message(final String title, final String text) {
        ask(done -> {
            final VBox box = dialogBox(title);
            box.getChildren().addAll(text(text), row(button(NeoText.get("rogue.ok"), "btn-primary",
                () -> done.accept(Boolean.TRUE))));
            return box;
        });
    }

    @Override
    public boolean confirm(final String title, final String text, final String yes, final String no) {
        final Boolean got = ask(done -> {
            final VBox box = dialogBox(title);
            box.getChildren().addAll(text(text), row(
                button(no, "btn-secondary", () -> done.accept(Boolean.FALSE)),
                button(yes, "btn-primary", () -> done.accept(Boolean.TRUE))));
            return box;
        });
        return Boolean.TRUE.equals(got);
    }

    @Override
    public void result(final String title, final String text, final List<Section> sections) {
        ask(done -> {
            final VBox box = dialogBox(title);
            final VBox body = new VBox(12);
            if (text != null && !text.isBlank()) {
                body.getChildren().add(text(text));
            }
            for (final Section s : sections == null ? List.<Section>of() : sections) {
                if (s.label() != null && !s.label().isBlank()) {
                    final Label l = new Label(clean(s.label()));
                    l.getStyleClass().add("rogue-section");
                    body.getChildren().add(l);
                }
                if (s.text() != null && !s.text().isBlank()) {
                    body.getChildren().add(text(s.text()));
                }
                if (s.cards() != null && !s.cards().isEmpty()) {
                    body.getChildren().add(cards(s.cards(), cw()));
                }
            }
            box.getChildren().addAll(scroll(body, UiScale.px(620)),
                row(button(NeoText.get("rogue.ok"), "btn-primary", () -> done.accept(Boolean.TRUE))));
            return box;
        });
    }

    @Override
    public List<PaperCard> cardReward(final String title, final List<PaperCard> cards, final int maxPicks,
                                      final String rerollLabel, final int rerollCost,
                                      final boolean rerollEnabled, final int gold) {
        final RogueRun run = forge.neo.rogue.RogueSession.inMemory();
        final Object got = ask(done -> {
            final ChoiceDialog<PaperCard> dialog = new ChoiceDialog<>(
                NeoText.get("rogue.reward.title", clean(title), maxPicks), cards, 0, maxPicks,
                PaperCard::getName, cw(), picked -> done.accept(picked));
            final HBox top = new HBox(10);
            top.setAlignment(Pos.CENTER_LEFT);
            top.getChildren().addAll(goldLabel(gold), grow());
            if (rerollLabel != null) {
                final Button reroll = button(rerollText(rerollLabel), "btn-secondary", () -> done.accept(REROLL));
                reroll.setDisable(!rerollEnabled);
                top.getChildren().add(reroll);
            }
            top.getChildren().add(viewDeck(() -> deckOf(run)));
            dialog.setContext(top);
            return dialog;
        });
        if (got == REROLL) {
            return null;
        }
        @SuppressWarnings("unchecked")
        final List<PaperCard> list = got instanceof List<?> ? (List<PaperCard>) got : new ArrayList<>();
        return new ArrayList<>(list);
    }

    /** Marca de "volver a tirar" (las respuestas de cartas son listas; esto no). */
    private static final Object REROLL = new Object();

    /** "Reroll: N" (texto fijo en ingles de CardRewardHelper) en nuestro idioma. */
    private static String rerollText(final String forgeLabel) {
        final String n = forgeLabel == null ? "" : forgeLabel.replaceAll("[^0-9]", "");
        return n.isEmpty() || "0".equals(n) ? NeoText.get("rogue.rerollFree") : NeoText.get("rogue.reroll", n);
    }

    private static Label goldLabel(final int gold) {
        final Label g = new Label(NeoText.get("rogue.gold", gold));
        g.getStyleClass().add("rogue-gold");
        return g;
    }

    @Override
    public List<PaperCard> chooseCards(final String title, final String subtitle, final List<PaperCard> cards,
                                       final int min, final int max, final RogueRun run,
                                       final Function<List<PaperCard>, Deck> preview) {
        final List<PaperCard> got = ask(done -> {
            final String heading = subtitle == null || subtitle.isBlank() ? clean(title)
                : clean(title) + " · " + clean(subtitle);
            final ChoiceDialog<PaperCard> dialog = new ChoiceDialog<>(heading, cards, min, max,
                PaperCard::getName, cw(), done);
            final RogueRun deckRun = run != null ? run : forge.neo.rogue.RogueSession.inMemory();
            if (deckRun != null && deckRun.getCurrentDeck() != null) {
                final HBox top = new HBox(10, grow(), viewDeck(() -> deckOf(deckRun)));
                top.setAlignment(Pos.CENTER_LEFT);
                dialog.setContext(top);
            }
            return dialog;
        });
        return got == null ? new ArrayList<>() : new ArrayList<>(got);
    }

    @Override
    public NpcAnswer npc(final NPCContext ctx, final int rerolls) {
        final NpcAnswer got = ask(done -> talk(ctx.displayName(), ctx.avatarIndex(), ctx.flavorTextChunks(),
            ctx.choices(), rerolls, false, done));
        return got == null ? new NpcAnswer(false, null) : got;
    }

    @Override
    public boolean tutorial(final RogueTutorial tutorial) {
        final NpcAnswer got = ask(done -> talk(NPC.TEFERI.name + " · " + tutorial.getTitle(), NPC.TEFERI.avatarIndex,
            tutorial.getMessageChunks(), List.of(), 0, true, done));
        return got != null && got.reroll();
    }

    /**
     * Un NPC (o Teferi en un tutorial): el texto por trozos con "Continuar" y,
     * al final, sus opciones. En el tutorial, "saltar todos" se devuelve como
     * {@code reroll = true} (no hay tirada en un tutorial).
     */
    private Region talk(final String who, final int avatarIndex, final List<String> chunks,
                        final List<NPCContext.NPCChoice> choices,
                        final int rerolls, final boolean tutorial, final Consumer<NpcAnswer> done) {
        final VBox box = dialogBox(who);
        box.getStyleClass().add("rogue-talk");
        final Label body = text(chunks.isEmpty() ? "" : chunks.get(0));
        body.getStyleClass().add("rogue-talk-text");
        final VBox options = new VBox(8);
        final HBox footer = row();
        // La cara de quien habla, como NPCDialog de Forge: su avatar de la hoja
        // de Forge (el mismo numero que FSkin.getAvatars), 100 px a la izquierda.
        final javafx.scene.image.Image face = forge.neo.look.NeoLook.builtInAvatar(avatarIndex).image();
        if (face != null) {
            final javafx.scene.image.ImageView portrait = new javafx.scene.image.ImageView(face);
            portrait.setFitWidth(UiScale.px(100));
            portrait.setFitHeight(UiScale.px(100));
            portrait.setPreserveRatio(true);
            portrait.setSmooth(true);
            final StackPane frame = new StackPane(portrait);
            frame.getStyleClass().add("rogue-talk-face");
            frame.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            final HBox speech = new HBox(16, frame, body);
            speech.setAlignment(Pos.TOP_LEFT);
            HBox.setHgrow(body, Priority.ALWAYS);
            box.getChildren().addAll(speech, options, footer);
        } else {
            box.getChildren().addAll(body, options, footer);
        }
        final int[] page = {0};
        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            body.setText(clean(chunks.isEmpty() ? "" : chunks.get(page[0])));
            options.getChildren().clear();
            footer.getChildren().clear();
            final boolean last = page[0] >= chunks.size() - 1;
            if (tutorial) {
                footer.getChildren().add(button(NeoText.get("rogue.skipTutorials"), "btn-secondary",
                    () -> done.accept(new NpcAnswer(true, null))));
                footer.getChildren().add(grow());
            }
            if (!last) {
                footer.getChildren().add(button(NeoText.get("rogue.continue"), "btn-primary", () -> {
                    page[0]++;
                    render[0].run();
                }));
                return;
            }
            if (choices.isEmpty()) {
                footer.getChildren().add(button(NeoText.get("rogue.continue"), "btn-primary",
                    () -> done.accept(new NpcAnswer(false, null))));
                return;
            }
            for (final NPCContext.NPCChoice c : choices) {
                final Choice b = choice(c.label(), c.npcEffect() == null ? null : c.npcEffect().getDescription());
                b.onPick(() -> done.accept(new NpcAnswer(false, c.npcEffect())));
                options.getChildren().add(b);
            }
            if (rerolls > 0) {
                footer.getChildren().add(button(NeoText.get("rogue.rerollsLeft", rerolls), "btn-secondary",
                    () -> done.accept(new NpcAnswer(true, null))));
            }
        };
        render[0].run();
        return box;
    }

    @Override
    public EventAnswer event(final RogueEvent event, final RogueRun run, final int rerolls) {
        final EventAnswer got = ask(done -> {
            final VBox box = dialogBox(event.getDisplayName());
            box.getChildren().add(text(event.getDescription()));
            final VBox options = new VBox(8);
            for (final RogueEvent.EventChoice c : event.getChoices()) {
                final Choice b = choice(c.label(), c.effect().getDescription());
                final boolean available = c.effect().isChoiceAvailable(run);
                b.setDisable(!available);
                if (!available) {
                    final String why = c.effect().getUnavailableReason(run);
                    if (why != null && !why.isBlank()) {
                        b.addLine(why);
                    }
                }
                b.onPick(() -> done.accept(new EventAnswer(false, c)));
                options.getChildren().add(b);
            }
            final HBox footer = row(viewDeck(() -> deckOf(run)));
            if (rerolls > 0) {
                footer.getChildren().add(0, button(NeoText.get("rogue.rerollsLeft", rerolls), "btn-secondary",
                    () -> done.accept(new EventAnswer(true, null))));
            }
            box.getChildren().addAll(options, footer);
            return box;
        });
        return got == null ? new EventAnswer(false, null) : got;
    }

    @Override
    public ChestAnswer chest(final List<ChestEffect> choices, final RogueRun run, final int rerolls) {
        final ChestAnswer got = ask(done -> {
            final VBox box = dialogBox(Localizer.getInstance().getMessage("lblRogueChooseYourLoot"));
            final HBox loot = new HBox(16);
            loot.setAlignment(Pos.CENTER);
            for (final ChestEffect effect : choices) {
                loot.getChildren().add(effectOffer(effect, () -> done.accept(new ChestAnswer(false, effect))));
            }
            final HBox footer = row(
                button(Localizer.getInstance().getMessage("lblRogueSkipLoot"), "btn-secondary",
                    () -> done.accept(new ChestAnswer(false, null))),
                grow(), viewDeck(() -> deckOf(run)));
            if (rerolls > 0) {
                footer.getChildren().add(1, button(NeoText.get("rogue.rerollsLeft", rerolls), "btn-secondary",
                    () -> done.accept(new ChestAnswer(true, null))));
            }
            box.getChildren().addAll(loot, footer);
            return box;
        });
        return got == null ? new ChestAnswer(false, null) : got;
    }

    /** Un botin o un rasgo: su carta (si tiene), su nombre y que hace, todo clicable. */
    private Region effectOffer(final RogueEffect effect, final Runnable pick) {
        final VBox v = new VBox(8);
        v.setAlignment(Pos.TOP_CENTER);
        v.getStyleClass().add("rogue-offer");
        final PaperCard c = effect.getEffectCard();
        if (c != null) {
            v.getChildren().add(card(c, cw() * 0.9));
        }
        final Choice b = choice(effect.getDisplayName(), effect.getDescription(), 270);
        b.setMaxWidth(UiScale.px(300));
        b.onPick(pick);
        v.getChildren().add(b);
        v.setMaxWidth(UiScale.px(320));
        return v;
    }

    @Override
    public BazaarAnswer bazaar(final List<BazaarItem> items, final RogueRun run, final String title,
                               final String rerollLabel, final boolean rerollEnabled, final int tab) {
        final BazaarAnswer got = ask(done -> new BazaarView(items, run, title, rerollLabel, rerollEnabled, tab, done));
        return got == null ? new BazaarAnswer(false, Set.of(), tab) : got;
    }

    /** {@code BazaarDialog}: cartas (y rasgos y objetos), cada una con su precio; se elige dentro del oro. */
    private final class BazaarView extends VBox {
        private final Set<BazaarItem> picked = new LinkedHashSet<>();
        private final Label cost = new Label();
        private final Button buy;
        private final int gold;

        BazaarView(final List<BazaarItem> items, final RogueRun run, final String title, final String rerollLabel,
                   final boolean rerollEnabled, final int tab, final Consumer<BazaarAnswer> done) {
            super(12);
            getStyleClass().addAll("dialog", "rogue-dialog");
            setPadding(new Insets(18));
            setMaxWidth(Region.USE_PREF_SIZE);
            setMaxHeight(Region.USE_PREF_SIZE);
            gold = run == null ? 0 : run.getCurrentGold();
            final Label t = new Label(clean(title));
            t.getStyleClass().add("dialog-title");

            final List<BazaarItem> cardsOnly = new ArrayList<>();
            final List<BazaarItem> special = new ArrayList<>();
            for (final BazaarItem item : items) {
                if (item.type() == BazaarItem.Type.CARD || item.type() == BazaarItem.Type.CURIO) {
                    cardsOnly.add(item);
                } else {
                    special.add(item);
                }
            }
            final FlowPane cardsGrid = grid(cardsOnly);
            final FlowPane specialGrid = grid(special);
            final StackPane pages = new StackPane(cardsGrid, specialGrid);
            final int[] current = {special.isEmpty() ? 0 : tab};
            final HBox tabs = new HBox(6);
            if (!special.isEmpty()) {
                final ToggleButton a = new ToggleButton(NeoText.get("rogue.bazaar.cards"));
                final ToggleButton b = new ToggleButton(NeoText.get("rogue.bazaar.special"));
                final javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
                a.setToggleGroup(group);
                b.setToggleGroup(group);
                a.getStyleClass().add("rogue-tab");
                b.getStyleClass().add("rogue-tab");
                a.setOnAction(e -> {
                    current[0] = 0;
                    cardsGrid.setVisible(true);
                    specialGrid.setVisible(false);
                    a.setSelected(true);
                });
                b.setOnAction(e -> {
                    current[0] = 1;
                    cardsGrid.setVisible(false);
                    specialGrid.setVisible(true);
                    b.setSelected(true);
                });
                (current[0] == 1 ? b : a).setSelected(true);
                tabs.getChildren().addAll(a, b);
            }
            cardsGrid.setVisible(current[0] == 0);
            specialGrid.setVisible(current[0] == 1);

            buy = button(NeoText.get("rogue.bazaar.buy"), "btn-primary",
                () -> done.accept(new BazaarAnswer(false, new LinkedHashSet<>(picked), current[0])));
            final HBox head = new HBox(12, t, grow(), goldLabel(gold), cost);
            head.setAlignment(Pos.CENTER_LEFT);
            cost.getStyleClass().add("rogue-cost");
            final HBox footer = row(
                button(NeoText.get("rogue.bazaar.leave"), "btn-secondary",
                    () -> done.accept(new BazaarAnswer(false, Set.of(), current[0]))),
                grow(), viewDeck(() -> deckOf(run)));
            if (rerollLabel != null) {
                final Button reroll = button(rerollText(rerollLabel), "btn-secondary",
                    () -> done.accept(new BazaarAnswer(true, Set.of(), current[0])));
                reroll.setDisable(!rerollEnabled);
                footer.getChildren().add(1, reroll);
            }
            footer.getChildren().add(buy);
            getChildren().addAll(head, tabs, scroll(pages, UiScale.px(620)), footer);
            refresh();
        }

        private FlowPane grid(final List<BazaarItem> list) {
            final FlowPane grid = new FlowPane(12, 12);
            grid.setAlignment(Pos.CENTER);
            grid.setPrefWrapLength(UiScale.px(900));
            for (final BazaarItem item : list) {
                grid.getChildren().add(offer(item));
            }
            return grid;
        }

        private Region offer(final BazaarItem item) {
            final VBox v = new VBox(6);
            v.setAlignment(Pos.TOP_CENTER);
            v.getStyleClass().add("rogue-offer");
            if (item.card() != null) {
                final CardNode node = card(item.card(), cw() * 0.85);
                v.getChildren().add(node);
            }
            if (item.type() == BazaarItem.Type.TRAIT && item.traitEffect() != null) {
                final Label name = new Label(clean(item.traitEffect().getDisplayName()));
                name.getStyleClass().add("rogue-choice-label");
                v.getChildren().add(name);
                Tooltip.install(v, new Tooltip(clean(item.traitEffect().getDescription())));
            } else if (item.type() == BazaarItem.Type.CARRY_CARD && item.carryCardType() != null) {
                final Label kind = new Label(NeoText.get("rogue.carry." + item.carryCardType().name().toLowerCase()));
                kind.getStyleClass().add("rogue-choice-desc");
                v.getChildren().add(kind);
            }
            final Label price = new Label(item.isDiscounted()
                ? NeoText.get("rogue.priceDiscount", item.getBasePrice(), item.getPrice())
                : NeoText.get("rogue.price", item.getPrice()));
            price.getStyleClass().addAll("rogue-price", item.isDiscounted() ? "rogue-price-discount" : "rogue-price-normal");
            v.getChildren().add(price);
            v.setCursor(javafx.scene.Cursor.HAND);
            v.setOnMouseClicked(e -> {
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                if (picked.contains(item)) {
                    picked.remove(item);
                } else if (BazaarPricing.calculateItemsTotalCost(picked) + item.getPrice() <= gold) {
                    picked.add(item);
                }
                v.getStyleClass().remove("rogue-offer-picked");
                if (picked.contains(item)) {
                    v.getStyleClass().add("rogue-offer-picked");
                }
                refresh();
            });
            return v;
        }

        private void refresh() {
            final int total = BazaarPricing.calculateItemsTotalCost(picked);
            cost.setText(NeoText.get("rogue.bazaar.cost", total, gold - total));
            buy.setDisable(picked.isEmpty());
        }
    }

    @Override
    public SanctumAnswer sanctum(final SanctumOffer offer, final RogueRun run) {
        final SanctumAnswer got = ask(done -> {
            final Localizer l = Localizer.getInstance();
            final VBox box = dialogBox(l.getMessage("lblRogueSanctum"));
            box.getChildren().add(text(l.getMessage("lblRogueChooseAction")));
            final Choice rest = choice(l.getMessage("lblRogueRest"), offer.restEnabled()
                ? offer.restDescription() : offer.restDescription() + "\n" + clean(offer.restDisabledReason()));
            rest.setDisable(!offer.restEnabled());
            rest.onPick(() -> done.accept(new SanctumAnswer(SanctumPick.HEAL, null)));
            final Choice cook = choice(l.getMessage("lblRogueCook"), l.getMessage("lblRogueCookDescription", "{{Item}}"));
            cook.onPick(() -> done.accept(new SanctumAnswer(SanctumPick.COOK, null)));
            final Choice reflect = choice(l.getMessage("lblRogueReflect"),
                l.getMessage("lblRogueReflectDescription", "{{Removal Credits}}"));
            reflect.onPick(() -> done.accept(new SanctumAnswer(SanctumPick.REFLECT, null)));
            final VBox options = new VBox(8, rest, cook, reflect);
            for (final SanctumContext.SanctumChoice c : offer.extraChoices()) {
                final Choice b = choice(c.label(), c.description());
                b.onPick(() -> done.accept(new SanctumAnswer(SanctumPick.CUSTOM, c)));
                options.getChildren().add(b);
            }
            box.getChildren().addAll(options, row(
                button(NeoText.get("rogue.skip"), "btn-secondary", () -> done.accept(new SanctumAnswer(SanctumPick.SKIP, null))),
                grow(), viewDeck(() -> deckOf(run))));
            return box;
        });
        return got == null ? new SanctumAnswer(SanctumPick.SKIP, null) : got;
    }

    @Override
    public List<PaperCard> chooseCommander(final List<PaperCard> eligible,
                                           final Function<PaperCard, List<PaperCard>> partners) {
        final PaperCard commander = pickByName(Localizer.getInstance().getMessage("lblRogueChooseAnyCommander"),
            eligible, 1);
        if (commander == null) {
            return List.of();
        }
        final List<PaperCard> partnerOptions = partners.apply(commander);
        if (partnerOptions == null || partnerOptions.isEmpty()) {
            return List.of(commander);
        }
        final PaperCard partner = pickByName(Localizer.getInstance().getMessage("lblChoosePartnerFor",
            commander.getName()), partnerOptions, 0);
        return partner == null ? List.of(commander) : List.of(commander, partner);
    }

    /** Elegir una carta de una lista enorme, por nombre (con buscador). {@code min} 0 = se puede saltar. */
    private PaperCard pickByName(final String title, final List<PaperCard> options, final int min) {
        final List<String> names = new ArrayList<>();
        for (final PaperCard c : options) {
            names.add(c.getName());
        }
        final List<String> got = ask(done -> new ChoiceDialog<>(title, names, min, 1, s -> s, cw(), done));
        if (got == null || got.isEmpty()) {
            return null;
        }
        final int i = names.indexOf(got.get(0));
        return i < 0 ? null : options.get(i);
    }

    /** Pone la mesa (lo pide {@code RogueNeoTable} antes de cada duelo). */
    public TableBinder openTable() {
        return host.openTable();
    }

    /** Quita la mesa al acabar el duelo. */
    public void closeTable() {
        host.closeTable();
    }
}
