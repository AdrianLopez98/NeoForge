package forge.neo.ui;

import forge.gamemodes.rogue.RogueDeck;
import forge.gamemodes.rogue.effect.CustomRunModification;
import forge.neo.NeoText;
import forge.neo.rogue.RogueSession;
import forge.neo.rogue.RogueStartRules;
import forge.neo.rogue.RogueText;
import forge.util.Localizer;
import java.util.EnumMap;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Empezar una run de Rogue Commander ({@code VSubmenuRogueStart} +
 * {@code CSubmenuRogueStart} de Forge): elegir comandante, y si estan
 * desbloqueadas, la run personalizada y la Descension.
 *
 * <p>Las reglas de que se puede elegir son las de Forge, copiadas de
 * {@code updateStartButtonState}, {@code updateDescensionVisibility} y
 * {@code onCustomModificationToggled} (desbloqueos, conflictos entre
 * modificaciones, el comandante generado que fuerza las dos aleatorias).
 */
public final class RogueStartScreen extends RogueScreen {

    public interface Actions {
        void start(RogueSession.StartOptions options);

        void continueRun();

        void aether();

        void history();

        void back();
    }

    private final Actions actions;
    /** Las reglas de Forge de que se puede elegir: esta pantalla solo las pinta. */
    private final RogueStartRules rules = new RogueStartRules();
    /** El carrusel de Forge (CommanderCarouselPanel): ver RogueCarousel. */
    private final RogueCarousel carousel = new RogueCarousel(this::pick);
    private final Label name = new Label();
    private final Label description = new Label();
    private final Label themes = new Label();
    private final CheckBox custom = new CheckBox(Localizer.getInstance().getMessage("lblRogueCustomRun"));
    private final VBox modsBox = new VBox(6);
    private final Map<CustomRunModification, CheckBox> mods = new EnumMap<>(CustomRunModification.class);
    private final CheckBox descension = new CheckBox(Localizer.getInstance().getMessage("lblRogueDescensionMode"));
    private final Label descensionLevel = new Label();
    private final Label descensionDesc = new Label();
    private final Button down = new Button("<");
    private final Button up = new Button(">");
    private final VBox descensionBox = new VBox(6);
    private final Button start = new Button(NeoText.get("rogue.start.start"));

    public RogueStartScreen(final boolean hasRun, final Actions actions) {
        this.actions = actions;
        getStyleClass().add("rogue-start");

        final Label title = new Label(NeoText.get("rogue.start.title"));
        title.getStyleClass().add("rogue-title");
        final Label beta = new Label(NeoText.get("rogue.start.beta"));
        beta.getStyleClass().addAll("rogue-pill", "rogue-pill-beta");
        beta.setWrapText(true);
        // La "i" de arriba a la derecha (Ana, 24-11-2026): que es el modo, como
        // se juega y a quien se reportan los fallos (RogueText.info()).
        final Button info = new Button("i");
        info.getStyleClass().addAll("btn-secondary", "rogue-info-button");
        info.setTooltip(new Tooltip(NeoText.get("rogue.info.title")));
        info.setOnAction(e -> showInfo());
        final Region headGap = new Region();
        HBox.setHgrow(headGap, Priority.ALWAYS);
        final HBox head = new HBox(14, title, beta, headGap, info);
        head.setAlignment(Pos.CENTER_LEFT);


        name.getStyleClass().add("rogue-commander-name");
        description.getStyleClass().add("dialog-text");
        description.setWrapText(true);
        themes.getStyleClass().add("rogue-themes");
        themes.setWrapText(true);
        final VBox details = new VBox(6, name, description, themes);

        custom.getStyleClass().add("rogue-check");
        custom.setOnAction(e -> onCustomToggled());
        for (final CustomRunModification m : CustomRunModification.values()) {
            final CheckBox box = new CheckBox(RogueText.modification(m));
            box.getStyleClass().add("rogue-check");
            Tooltip.install(box, new Tooltip(RogueText.clean(m.getRawDescription())));
            box.setOnAction(e -> onModificationToggled(m));
            mods.put(m, box);
        }
        final FlowPane modGrid = new FlowPane(14, 6);
        modGrid.getChildren().addAll(mods.values());
        final Label limits = new Label(Localizer.getInstance().getMessage("lblRogueCustomRunLimitations"));
        limits.getStyleClass().add("rogue-hint");
        limits.setWrapText(true);
        modsBox.getChildren().addAll(limits, modGrid);
        modsBox.setPadding(new Insets(0, 0, 0, 24));

        descension.getStyleClass().add("rogue-check");
        descension.setOnAction(e -> {
            rules.setDescension(descension.isSelected());
            refreshDescension();
        });
        down.getStyleClass().add("btn-secondary");
        up.getStyleClass().add("btn-secondary");
        down.setOnAction(e -> {
            rules.descensionDown();
            refreshDescension();
        });
        up.setOnAction(e -> {
            rules.descensionUp();
            refreshDescension();
        });
        descensionLevel.getStyleClass().add("rogue-level");
        descensionDesc.getStyleClass().add("rogue-hint");
        descensionDesc.setWrapText(true);
        final HBox stepper = new HBox(8, down, descensionLevel, up);
        stepper.setAlignment(Pos.CENTER_LEFT);
        descensionBox.getChildren().addAll(stepper, descensionDesc);
        descensionBox.setPadding(new Insets(0, 0, 0, 24));

        final VBox options = new VBox(10);
        options.getChildren().addAll(customRow(), modsBox, descensionRow(), descensionBox);

        final VBox middle = new VBox(16, details, options);
        middle.setPadding(new Insets(0, 30, 0, 30));
        final ScrollPane middleScroll = new ScrollPane(middle);
        middleScroll.setFitToWidth(true);
        middleScroll.getStyleClass().add("rogue-scroll");

        final VBox center = new VBox(10, carousel, middleScroll);
        VBox.setVgrow(middleScroll, Priority.ALWAYS);

        // Pie (principio 12): lo secundario a la izquierda; volver junto a la principal.
        final boolean anyCompleted = rules.anyRunCompleted();
        final Button aether = button(Localizer.getInstance().getMessage("lblRogueAether"), actions::aether);
        aether.setDisable(!anyCompleted);
        if (!anyCompleted) {
            Tooltip.install(aether, new Tooltip(Localizer.getInstance().getMessage("lblRogueUnlockAetherHint")));
        }
        final Button history = button(Localizer.getInstance().getMessage("lblRogueHistory"), actions::history);
        history.setDisable(!anyCompleted);
        if (!anyCompleted) {
            Tooltip.install(history, new Tooltip(Localizer.getInstance().getMessage("lblRogueUnlockHistoryHint")));
        }
        final Button back = button(NeoText.get("rogue.back"), actions::back);
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, aether, history, gap, back);
        if (hasRun) {
            final Button cont = button(NeoText.get("rogue.start.continue"), actions::continueRun);
            // La principal es la grande (btn-play): con una run a medias es
            // continuarla, y "Empezar la run" baja a secundario sin su tamanyo
            // (salia mas grande que la principal, 24-11-2026).
            cont.getStyleClass().setAll("button", "btn-primary", "btn-play");
            footer.getChildren().add(cont);
        }
        start.getStyleClass().addAll(hasRun ? java.util.List.of("btn-secondary") : java.util.List.of("btn-primary", "btn-play"));
        start.setOnAction(e -> actions.start(rules.options()));
        footer.getChildren().add(start);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 22, 14, 22));
        footer.getStyleClass().add("rogue-bottom");

        final BorderPane layout = new BorderPane();
        head.setPadding(new Insets(14, 22, 4, 22));
        layout.setTop(head);
        layout.setCenter(center);
        layout.setBottom(footer);
        setContent(layout);

        loadCommanders();
    }

    /** Solo pruebas (-Dneo.rogue.mock=info): abre la "i". */
    public void pressInfo() {
        showInfo();
    }

    /** La ventana de la "i", en la capa de CONSULTA: no tapa ninguna pregunta del modo. */
    private void showInfo() {
        final VBox body = new VBox(10);
        for (final RogueText.InfoSection section : RogueText.info()) {
            final Label h = new Label(section.title());
            h.getStyleClass().add("rogue-section");
            final Label t = new Label(section.text());
            t.getStyleClass().add("dialog-text");
            t.setWrapText(true);
            t.setMinHeight(Region.USE_PREF_SIZE);
            body.getChildren().addAll(h, t);
        }
        body.setPadding(new Insets(4, 8, 4, 0));
        final ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("rogue-scroll");
        // Ancho fijo para que el texto parta igual siempre; alto, el del texto
        // con un tope (y la barra si no cabe): un alto fijo dejaba un hueco vacio.
        body.setPrefWidth(UiScale.px(820));
        scroll.setPrefViewportWidth(UiScale.px(820));
        scroll.setPrefViewportHeight(UiScale.px(600));
        body.heightProperty().addListener((o, a, b) ->
            scroll.setPrefViewportHeight(Math.min(b.doubleValue(), UiScale.px(600))));
        final Label title = new Label(NeoText.get("rogue.info.title"));
        title.getStyleClass().add("dialog-title");
        final Button report = button(NeoText.get("rogue.info.report"), () -> {
            try {
                forge.gui.GuiBase.getInterface().browseToUrl(RogueText.REPORT_URL);
            } catch (final Exception ex) {
                System.out.println("[rogue] no se ha podido abrir " + RogueText.REPORT_URL + ": " + ex);
            }
        });
        final Button close = new Button(NeoText.get("rogue.close"));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> consult().hide());
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, report, gap, close);
        footer.setAlignment(Pos.CENTER_LEFT);
        final VBox box = new VBox(12, title, scroll, footer);
        box.getStyleClass().addAll("dialog", "rogue-dialog");
        box.setPadding(new Insets(18));
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        consult().setOnBackgroundClick(() -> consult().hide());
        consult().show(box);
    }

    private static Button button(final String text, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().add("btn-secondary");
        b.setOnAction(e -> action.run());
        return b;
    }

    private Region customRow() {
        if (rules.customUnlocked()) {
            return custom;
        }
        custom.setSelected(false);
        return lockLine(Localizer.getInstance().getMessage("lblRogueCustomRunWin2RunsWithAnyCommanderToUnlock"));
    }

    private Region descensionRow() {
        if (rules.descensionUnlocked()) {
            return descension;
        }
        return lockLine(Localizer.getInstance().getMessage("lblRogueUnlockDescensionHint"));
    }

    private static Label lockLine(final String text) {
        final Label l = new Label("🔒 " + RogueText.clean(text));
        l.getStyleClass().add("rogue-hint");
        l.setWrapText(true);
        return l;
    }

    // ------------------------------------------------------------------

    /** El carrusel, con los comandantes que diga {@link RogueStartRules}. */
    private void loadCommanders() {
        carousel.setDecks(rules.commanders());
        showSelected();
        modsBox.setVisible(custom.isSelected());
        modsBox.setManaged(custom.isSelected());
    }

    /** {@code onCommanderSelected}: un desbloqueado se elige; uno bloqueado solo dice como desbloquearlo. */
    private void pick(final RogueDeck deck) {
        if (deck.isUnlocked()) {
            select(deck);
        } else {
            showLocked(deck);
        }
    }

    private void select(final RogueDeck deck) {
        rules.select(deck);
        showSelected();
    }

    /** El comandante elegido, su descripcion y la Descension. */
    private void showSelected() {
        final RogueDeck deck = rules.selected();
        carousel.center(deck);
        if (deck == null) {
            name.setText("");
            description.setText(NeoText.get("rogue.start.none"));
            themes.setText("");
        } else {
            name.setText(deck.getCommanderCardName());
            description.setText(RogueText.clean(deck.getDescription()));
            final String t = deck.getThemeDescription();
            themes.setText(t == null || t.isBlank() ? "" : Localizer.getInstance().getMessage("lblRogueThemesLabel")
                + " " + RogueText.clean(t));
        }
        refreshDescension();
    }

    private void showLocked(final RogueDeck deck) {
        rules.select(deck);
        carousel.center(deck);
        name.setText("???");
        final String how = deck.getUnlockDescription();
        description.setText(Localizer.getInstance().getMessage("lblRogueUnlockLabel") + " "
            + RogueText.clean(how == null || how.isBlank() ? Localizer.getInstance().getMessage("lblRogueLocked") : how));
        themes.setText("");
        refreshDescension();
    }

    // ------------------------------------------------------------------

    private void onCustomToggled() {
        final boolean reload = rules.setCustom(custom.isSelected());
        modsBox.setVisible(custom.isSelected());
        modsBox.setManaged(custom.isSelected());
        if (reload) {
            loadCommanders();
        } else {
            refreshDescension();
        }
    }

    /** {@code onCustomModificationToggled}: las reglas, en {@link RogueStartRules}. */
    private void onModificationToggled(final CustomRunModification m) {
        if (rules.setModification(m, mods.get(m).isSelected())) {
            loadCommanders();
        } else {
            refreshDescension();
        }
    }

    /** {@code updateDescensionVisibility} + {@code updateDescensionDisplay} + {@code updateStartButtonState}. */
    private void refreshDescension() {
        for (final CustomRunModification m : CustomRunModification.values()) {
            mods.get(m).setSelected(rules.isChecked(m));
            mods.get(m).setDisable(rules.isDisabled(m));
        }
        final RogueStartRules.Descension d = rules.descension();
        descension.setDisable(!d.checkEnabled());
        descension.setSelected(d.checked());
        descensionBox.setVisible(d.boxVisible());
        descensionBox.setManaged(d.boxVisible());
        down.setVisible(d.downVisible());
        up.setVisible(d.upVisible());
        descensionLevel.setText(d.levelText());
        descensionDesc.setText(d.description());
        start.setDisable(!rules.canStart());
        carousel.setDimmed(rules.isCustom() && rules.generatedCommander());
    }
}
