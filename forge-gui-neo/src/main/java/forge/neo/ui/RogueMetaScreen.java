package forge.neo.ui;

import forge.gamemodes.rogue.AetherUpgrade;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RogueRunHistoryEntry;
import forge.gamemodes.rogue.effect.AetherEffect;
import forge.neo.rogue.RogueMeta;
import forge.neo.rogue.RogueText;
import forge.neo.NeoText;
import forge.util.Localizer;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
 * Lo que queda entre runs de Rogue Commander: el <b>Aether</b>
 * ({@code VSubmenuRogueAether}: mejoras permanentes que se compran con ecos y
 * se activan hasta llenar la energia) y el <b>historial</b>
 * ({@code VSubmenuRogueHistory}). Todo lo de juego lo hace
 * {@link RogueMetaProgress} de Forge; aqui solo se pinta y se llama.
 */
public final class RogueMetaScreen extends RogueScreen {

    private final Runnable back;
    private final boolean aether;

    private RogueMetaScreen(final boolean aether, final Runnable back) {
        this.aether = aether;
        this.back = back;
        getStyleClass().add(aether ? "rogue-aether" : "rogue-history");
        refresh();
    }

    public static RogueMetaScreen aether(final Runnable back) {
        return new RogueMetaScreen(true, back);
    }

    public static RogueMetaScreen history(final Runnable back) {
        return new RogueMetaScreen(false, back);
    }

    private void refresh() {
        final BorderPane layout = new BorderPane();
        layout.setTop(head());
        final ScrollPane sp = new ScrollPane(aether ? aetherBody() : historyBody());
        sp.setFitToWidth(true);
        sp.getStyleClass().add("rogue-scroll");
        layout.setCenter(sp);
        layout.setBottom(footer());
        setContent(layout);
    }

    private Region head() {
        final RogueMetaProgress p = RogueMetaProgress.getInstance();
        final Label title = new Label(Localizer.getInstance().getMessage(aether ? "lblRogueTheAether" : "lblRogueRunHistory"));
        title.getStyleClass().add("rogue-title");
        final HBox head = new HBox(18, title);
        head.setAlignment(Pos.CENTER_LEFT);
        head.setPadding(new Insets(14, 22, 8, 22));
        if (aether) {
            head.getChildren().addAll(
                stat("rogue-echo", NeoText.get("rogue.echoes", p.getTotalEchoes())),
                stat("rogue-spark", NeoText.get("rogue.sparks", p.getTotalSparks())),
                stat("rogue-energy", NeoText.get("rogue.energy", p.getActiveAetherEffectCount(),
                    p.getActiveAetherEffectSlots())));
        }
        return head;
    }

    private static Label stat(final String style, final String text) {
        final Label l = new Label(text);
        l.getStyleClass().addAll("rogue-stat", style);
        return l;
    }

    private Region footer() {
        final Button reset = new Button(Localizer.getInstance().getMessage(
            aether ? "lblRogueResetAetherworks" : "lblRogueResetHistory"));
        reset.getStyleClass().addAll("btn-secondary", "btn-danger");
        reset.setOnAction(e -> confirmReset());
        final Button done = new Button(NeoText.get("rogue.back"));
        done.getStyleClass().add("btn-primary");
        done.setOnAction(e -> back.run());
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, reset, gap, done);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 22, 14, 22));
        footer.getStyleClass().add("rogue-bottom");
        return footer;
    }

    private void confirmReset() {
        final Localizer l = Localizer.getInstance();
        final String text = l.getMessage(aether ? "lblRogueResetAetherworksConfirm" : "lblRogueResetHistoryConfirm");
        final String title = l.getMessage(aether ? "lblRogueResetAetherworks" : "lblRogueResetRunHistory");
        dialogs().show(new ConfirmDialog(title, RogueText.clean(text),
            List.of(NeoText.get("rogue.cancel"), l.getMessage("lblReset")), 0, i -> {
                dialogs().hide();
                if (i != 1) {
                    return;
                }
                final RogueMetaProgress p = RogueMetaProgress.getInstance();
                if (aether) {
                    p.resetAetherEffects();
                } else {
                    p.clearRunHistory();
                }
                refresh();
            }));
    }

    // ------------------------------------------------------------------
    // El Aether
    // ------------------------------------------------------------------

    private Region aetherBody() {
        final RogueMetaProgress p = RogueMetaProgress.getInstance();
        final int level = p.getAetherUpgradeLevel();
        final FlowPane grid = new FlowPane(14, 14);
        grid.setPadding(new Insets(10, 22, 22, 22));
        grid.setPrefWrapLength(UiScale.px(1200));

        if (!p.isDescensionModeUnlocked()) {
            grid.getChildren().add(info("🔒", Localizer.getInstance().getMessage("lblRogueAetherEnergyHint")));
        } else {
            final AetherUpgrade next = AetherUpgrade.forLevel(level + 1);
            if (next != null) {
                final VBox card = panel(next.name, next.description);
                card.getStyleClass().add("rogue-aether-upgrade");
                final Button buy = new Button(NeoText.get("rogue.aether.upgrade", next.sparkCost, next.echoCost));
                buy.getStyleClass().add("btn-primary");
                buy.setDisable(p.getTotalSparks() < next.sparkCost || p.getTotalEchoes() < next.echoCost);
                buy.setOnAction(e -> {
                    if (p.purchaseAetherUpgrade(level + 1)) {
                        refresh();
                        dialogs().show(new ConfirmDialog(
                            Localizer.getInstance().getMessage("lblRogueAetherUpgradeUnlocked"),
                            RogueText.clean(Localizer.getInstance().getMessage("lblRogueUnlockedAetherUpgrade",
                                next.name, next.description)),
                            List.of(NeoText.get("rogue.ok")), 0, i -> dialogs().hide()));
                    }
                });
                card.getChildren().add(buy);
                grid.getChildren().add(card);
            }
        }

        for (final AetherEffect effect : AetherEffect.values()) {
            if (!effect.isAccessibleAt(level)) {
                continue;
            }
            final int rank = p.getAetherEffectRank(effect);
            final int maxRank = effect.getEffectiveMaxRank(level);
            final boolean active = p.getActiveAetherEffects().contains(effect);
            final VBox card = panel(effect.getDisplayName(),
                effect.getDescriptionWithAllRanks(rank, level));
            if (active) {
                card.getStyleClass().add("rogue-aether-active");
            }
            final Label r = new Label(Localizer.getInstance().getMessage("lblRogueRank", rank, maxRank));
            r.getStyleClass().add("rogue-hint");
            final Button build = new Button();
            build.getStyleClass().add("btn-secondary");
            if (rank >= maxRank) {
                build.setText(Localizer.getInstance().getMessage("lblRogueMaxRank"));
                build.setDisable(true);
            } else {
                final int cost = effect.getEchoCostForRank(rank + 1);
                build.setText(NeoText.get(rank == 0 ? "rogue.aether.build" : "rogue.aether.improve", cost));
                build.setDisable(p.getTotalEchoes() < cost);
                build.setOnAction(e -> {
                    if (RogueMeta.build(effect)) {
                        refresh();
                    }
                });
            }
            final Button toggle = new Button(NeoText.get(active ? "rogue.aether.off" : "rogue.aether.on"));
            toggle.getStyleClass().add(active ? "btn-secondary" : "btn-primary");
            toggle.setDisable(!RogueMeta.canToggle(effect));
            toggle.setOnAction(e -> {
                RogueMeta.toggle(effect);
                refresh();
            });
            final HBox buttons = new HBox(8, r, new Region(), build, toggle);
            HBox.setHgrow(buttons.getChildren().get(1), Priority.ALWAYS);
            buttons.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(buttons);
            grid.getChildren().add(card);
        }
        return grid;
    }

    private static VBox panel(final String title, final String text) {
        final VBox v = new VBox(8);
        v.getStyleClass().add("rogue-panel");
        v.setPadding(new Insets(14));
        v.setPrefWidth(UiScale.px(360));
        v.setMaxWidth(UiScale.px(360));
        final Label t = new Label(RogueText.clean(title));
        t.getStyleClass().add("rogue-choice-label");
        t.setWrapText(true);
        final Label d = new Label(RogueText.clean(text));
        d.getStyleClass().add("rogue-choice-desc");
        d.setWrapText(true);
        d.setMinHeight(Region.USE_PREF_SIZE);
        v.getChildren().addAll(t, d);
        return v;
    }

    private static Region info(final String icon, final String text) {
        final VBox v = panel(icon, text);
        v.getStyleClass().add("rogue-panel-info");
        return v;
    }

    // ------------------------------------------------------------------
    // El historial
    // ------------------------------------------------------------------

    private Region historyBody() {
        final VBox list = new VBox(10);
        list.setPadding(new Insets(10, 22, 22, 22));
        final List<RogueRunHistoryEntry> entries = RogueMetaProgress.getInstance().getRunHistory();
        if (entries.isEmpty()) {
            final Label none = new Label(Localizer.getInstance().getMessage("lblRogueNoCompletedRunsYet"));
            none.getStyleClass().add("rogue-hint");
            list.getChildren().add(none);
            return list;
        }
        // La mas reciente arriba, como en Forge.
        for (int i = entries.size() - 1; i >= 0; i--) {
            final RogueRunHistoryEntry e = entries.get(i);
            final VBox row = new VBox(4);
            row.getStyleClass().add("rogue-panel");
            row.setPadding(new Insets(10, 14, 10, 14));
            // Morir en modo infinito cuenta como victoria (RogueMeta.outcome).
            final String outcome = RogueMeta.outcome(e);
            final Label head = new Label(RogueMeta.headline(e));
            head.getStyleClass().addAll("rogue-choice-label",
                "VICTORY".equals(outcome) ? "rogue-won" : "DEFEAT".equals(outcome) ? "rogue-lost" : "rogue-abandoned");
            row.getChildren().add(head);
            for (final String detail : RogueMeta.details(e)) {
                row.getChildren().add(line(detail));
            }
            final FlowPane effects = new FlowPane(6, 6);
            for (final RogueRunHistoryEntry.EffectSnapshot s : e.getActiveEffects()) {
                final Label p = new Label(RogueText.clean(s.getDisplayText()));
                p.getStyleClass().addAll("rogue-pill", "rogue-pill-effect");
                if (s.getTooltipText() != null) {
                    Tooltip.install(p, new Tooltip(RogueText.clean(s.getTooltipText())));
                }
                effects.getChildren().add(p);
            }
            if (!effects.getChildren().isEmpty()) {
                row.getChildren().add(effects);
            }
            list.getChildren().add(row);
        }
        return list;
    }

    private static Label line(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("rogue-choice-desc");
        l.setWrapText(true);
        return l;
    }
}
