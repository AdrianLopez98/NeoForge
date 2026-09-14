package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.game.card.CardView;
import forge.item.PaperCard;
import javafx.animation.*;
import javafx.util.Duration;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import java.util.*;
import java.util.function.Predicate;

/** Presentation of an already committed purchase. Never generates or awards cards. */
public final class PackOpening extends StackPane {
    private final List<PaperCard> cards;
    private final Predicate<PaperCard> isNew;
    private final boolean[] revealed;
    private final GridPane grid = new GridPane();
    private final StackPane stage = new StackPane();
    private final Pane sparks = new Pane();
    private final Label progress = new Label();
    private final Button reveal = new Button(NeoText.get("opening.revealAll"));
    private final Pager pager;
    private final List<Animation> animations = new ArrayList<>();
    private boolean opened, closed;
    private final Runnable onDone;
    private final double cardWidth;
    private final int columns;

    public PackOpening(String title, String subtitle, List<PaperCard> contents,
                       Predicate<PaperCard> isNew, double availW, double availH, Runnable onDone) {
        this.cards = new ArrayList<>(contents);
        // A box is a haul, not a fabricated sequence of packs. Keep every physical copy.
        if (cards.size() > 24) cards.sort(Comparator.comparingInt(PackOpening::weight).reversed());
        this.isNew = isNew;
        this.onDone = onDone;
        revealed = new boolean[cards.size()];
        double width = Math.max(400, availW - 96), height = Math.max(320, availH - 96);
        setPrefSize(width, height); setMaxSize(width, height); setMinSize(0, 0);
        // Ordinary packs fit on one page, in two rows (14 cards = 7 by 2).
        // Larger hauls keep bounded pages so boxes remain readable and cheap to render.
        final int pageSize = cards.size() <= 20 ? Math.max(1, cards.size()) : 8;
        columns = Math.max(1, (int) Math.ceil(pageSize / 2.0));
        final int rows = (int) Math.ceil(pageSize / (double) columns);
        cardWidth = Math.max(40, Math.min(220, Math.min(
                (width - 48 - (columns - 1) * 16) / columns,
                (height - 175) / (rows * CardNode.ASPECT))));
        getStyleClass().add("pack-opening");
        setId("pack-opening");
        Label heading = new Label(title); heading.getStyleClass().add("opening-title");
        heading.setWrapText(true);
        Label detail = new Label(subtitle); detail.getStyleClass().add("opening-detail"); detail.setWrapText(true);
        VBox head = new VBox(5, heading, detail); head.setAlignment(Pos.CENTER);
        head.setPadding(new Insets(18, 20, 8, 20));
        pager = new Pager(pageSize, this::showPage); pager.setTotal(cards.size());
        pager.setVisible(false); pager.setManaged(false);
        reveal.setId("opening-reveal-all"); reveal.getStyleClass().add("btn-primary");
        reveal.setOnAction(e -> revealAll());
        Button done = new Button(NeoText.get("haul.done")); done.setId("opening-done");
        done.getStyleClass().add("btn-secondary"); done.setOnAction(e -> close());
        progress.getStyleClass().add("opening-detail");
        HBox buttons = new HBox(12, pager, reveal, done); buttons.setAlignment(Pos.CENTER);
        VBox footer = new VBox(8, progress, buttons); footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(8, 16, 16, 16));
        BorderPane content = new BorderPane(stage, head, null, footer, null);
        sparks.setMouseTransparent(true);
        getChildren().addAll(content, sparks);
        grid.setAlignment(Pos.CENTER); grid.setHgap(16); grid.setVgap(10);
        showSealed(); updateProgress();
        parentProperty().addListener((o, before, after) -> { if (after == null) stopAnimations(); });
    }

    private void showSealed() {
        Label crest = new Label("✦"); crest.getStyleClass().add("opening-crest");
        Label name = new Label(NeoText.get("opening.sealed")); name.getStyleClass().add("opening-pack-label");
        Button seal = new Button(); seal.setId("opening-seal");
        VBox face = new VBox(14, crest, name); face.setAlignment(Pos.CENTER);
        seal.setGraphic(face); seal.getStyleClass().add("opening-seal");
        seal.setPrefSize(180, 240); seal.setMaxSize(180, 240);
        seal.setOnAction(e -> open());
        Label hint = new Label(NeoText.get("opening.hint")); hint.getStyleClass().add("opening-detail");
        Circle orbit = new Circle(150, Color.TRANSPARENT);
        orbit.setStroke(Color.web("#d4aa5e", .32)); orbit.setStrokeWidth(1);
        orbit.getStrokeDashArray().setAll(36.0, 18.0, 3.0, 18.0);
        orbit.setMouseTransparent(true);
        StackPane altar = new StackPane(orbit, seal); altar.setMaxHeight(300);
        VBox pack = new VBox(16, altar, hint); pack.setAlignment(Pos.CENTER);
        if (CardNode.areAnimationsEnabled()) {
            RotateTransition rotate = new RotateTransition(Duration.seconds(24), orbit);
            rotate.setByAngle(360); rotate.setCycleCount(Animation.INDEFINITE); rotate.setInterpolator(Interpolator.LINEAR); play(rotate);
            ScaleTransition pulse = new ScaleTransition(Duration.seconds(1.8), seal);
            pulse.setToX(1.035); pulse.setToY(1.035); pulse.setAutoReverse(true); pulse.setCycleCount(Animation.INDEFINITE); play(pulse);
        }
        stage.getChildren().setAll(pack);
    }

    private void open() {
        if (opened || closed) return;
        opened = true;
        sound(forge.sound.SoundEffectType.Shuffle);
        showPage();
        Circle wave = new Circle(getWidth() / 2, getHeight() / 2, 28, Color.TRANSPARENT);
        wave.setStroke(Color.web("#f5ce88", .7)); wave.setStrokeWidth(2); sparks.getChildren().add(wave);
        if (CardNode.areAnimationsEnabled()) {
            ScaleTransition expand = new ScaleTransition(Duration.millis(650), wave); expand.setToX(10); expand.setToY(10);
            FadeTransition fade = new FadeTransition(Duration.millis(650), wave); fade.setToValue(0);
            ParallelTransition ripple = new ParallelTransition(expand, fade); ripple.setOnFinished(e -> sparks.getChildren().remove(wave)); play(ripple);
        } else sparks.getChildren().remove(wave);
        burst(getWidth() / 2, getHeight() / 2, Color.web("#e8b95e"), 32);
    }

    private void showPage() {
        if (!opened) return;
        stopAnimations(); grid.getChildren().clear();
        pager.setTotal(cards.size());
        for (int i = pager.from(); i < pager.to(); i++) {
            final int index = i;
            PaperCard card = cards.get(i);
            StackPane tile = new StackPane(); tile.setId("opening-card-" + i);
            tile.setPrefSize(cardWidth, cardWidth * CardNode.ASPECT);
            tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            paint(tile, index);
            tile.setOnMouseClicked(e -> {
                if (!revealed[index]) revealOne(tile, index, 0);
                else CardZoom.show(tile, CardView.getCardForUi(card));
                e.consume();
            });
            tile.setFocusTraversable(true);
            tile.setOnKeyPressed(e -> {
                if (e.getCode() == javafx.scene.input.KeyCode.ENTER || e.getCode() == javafx.scene.input.KeyCode.SPACE) {
                    if (!revealed[index]) revealOne(tile, index, 0);
                    else CardZoom.show(tile, CardView.getCardForUi(card));
                    e.consume();
                }
            });
            grid.add(tile, (i - pager.from()) % columns, (i - pager.from()) / columns);
        }
        stage.getChildren().setAll(grid);
        updateProgress();
    }

    private void paint(StackPane tile, int index) {
        tile.getChildren().clear(); tile.getStyleClass().setAll("opening-card");
        if (!revealed[index]) {
            Label rune = new Label("✧"); rune.getStyleClass().add("opening-rune");
            tile.getStyleClass().add("opening-card-back"); tile.getChildren().add(rune);
            tile.setAccessibleText(NeoText.get("opening.hint"));
            return;
        }
        PaperCard card = cards.get(index);
        CardNode face = new CardNode(cardWidth); face.setRotationEnabled(false);
        face.setHoverEnabled(false); face.setBadgesVisible(false);
        face.setCard(CardView.getCardForUi(card)); face.setMouseTransparent(true);
        tile.getChildren().add(face);
        if (weight(card) >= 3) tile.getStyleClass().add(weight(card) >= 5 ? "opening-mythic" : "opening-rare");
        if (card.isFoil()) tile.getStyleClass().add("opening-foil");
        if (isNew != null && isNew.test(card)) {
            Label badge = new Label(NeoText.get("haul.new")); badge.getStyleClass().add("new-badge");
            badge.setMouseTransparent(true); StackPane.setAlignment(badge, Pos.TOP_CENTER);
            tile.getChildren().add(badge);
        }
        tile.setAccessibleText(forge.neo.card.CardText.nameOf(card));
    }

    private void revealOne(StackPane tile, int index, double delay) {
        if (revealed[index] || closed) return;
        revealed[index] = true; updateProgress();
        if (delay == 0) sound(weight(cards.get(index)) >= 5 ? forge.sound.SoundEffectType.Planeswalker : forge.sound.SoundEffectType.Draw);
        if (!CardNode.areAnimationsEnabled()) { paint(tile, index); return; }
        ScaleTransition out = new ScaleTransition(Duration.millis(130), tile);
        out.setToX(0.03); out.setDelay(Duration.millis(delay));
        ScaleTransition in = new ScaleTransition(Duration.millis(240), tile);
        in.setToX(1); in.setInterpolator(Interpolator.EASE_OUT);
        out.setOnFinished(e -> {
            paint(tile, index);
            if (weight(cards.get(index)) >= 3 || cards.get(index).isFoil()) {
                Bounds b = sceneToLocal(tile.localToScene(tile.getBoundsInLocal()));
                burst(b.getCenterX(), b.getCenterY(), weight(cards.get(index)) >= 5 ? Color.CORAL : Color.GOLD, 18);
            }
        });
        play(new SequentialTransition(out, in));
    }

    private void revealAll() {
        if (closed) return;
        if (!opened) open();
        // Only animate visible cards; boxes never allocate hundreds of image nodes.
        int n = 0;
        for (Node node : grid.getChildren()) revealOne((StackPane) node, pager.from() + n, n++ * 55);
        Arrays.fill(revealed, true); updateProgress();
    }

    private void updateProgress() {
        int count = 0; for (boolean value : revealed) if (value) count++;
        progress.setText(NeoText.get("opening.progress", count, cards.size()));
        reveal.setDisable(count == cards.size());
    }

    private void burst(double x, double y, Color color, int count) {
        if (!CardNode.areAnimationsEnabled() || closed) return;
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count, distance = 70 + (i % 4) * 30;
            Circle particle = new Circle(x, y, i % 3 + 1, color); sparks.getChildren().add(particle);
            TranslateTransition move = new TranslateTransition(Duration.millis(700), particle);
            move.setToX(Math.cos(angle) * distance); move.setToY(Math.sin(angle) * distance);
            FadeTransition fade = new FadeTransition(Duration.millis(700), particle); fade.setToValue(0);
            ParallelTransition effect = new ParallelTransition(move, fade);
            effect.setOnFinished(e -> sparks.getChildren().remove(particle)); play(effect);
        }
    }
    private static void sound(forge.sound.SoundEffectType effect) {
        try { forge.sound.SoundSystem.instance.play(effect, true); }
        catch (RuntimeException ex) { System.err.println("[opening] Audio unavailable: " + ex.getMessage()); }
    }
    private void play(Animation animation) {
        animations.removeIf(a -> a.getStatus() == Animation.Status.STOPPED);
        animations.add(animation); animation.play();
    }
    private void stopAnimations() { for (Animation a : animations) a.stop(); animations.clear(); sparks.getChildren().clear(); }
    private void close() { if (closed) return; closed = true; stopAnimations(); onDone.run(); }
    private static int weight(PaperCard c) {
        if (c.getRarity() == null) return 0;
        switch (c.getRarity()) { case MythicRare: return 5; case Special: return 4; case Rare: return 3; case Uncommon: return 2; default: return 1; }
    }
}
