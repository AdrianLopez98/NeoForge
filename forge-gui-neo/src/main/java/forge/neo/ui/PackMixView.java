package forge.neo.ui;

import java.util.Map;

import forge.card.CardEdition;
import forge.model.CardBlock;
import forge.neo.NeoText;
import forge.neo.draft.PackMix;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Lo que comparten las pantallas de Draft y de Sellado para la MEZCLA de
 * expansiones ({@link PackMix}): la barra con cada expansion y su
 * <b>− N +</b>, la suma de sobres, y la ficha de un bloque.
 *
 * <p>Una sola pieza para las dos pantallas porque la pregunta es la misma —
 * "de que expansiones, y cuantos sobres de cada una" — y conviene que se
 * conteste igual en los dos sitios (como ya pasa con la rejilla de
 * expansiones).
 */
final class PackMixView {

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");

    private PackMixView() {
    }

    /**
     * La mezcla como fila de fichas: codigo, − N +, y al final la suma
     * ("3 de 3 sobres", en rojo si no cuadra) y "Vaciar".
     *
     * @param target cuantos sobres tiene que haber para poder empezar
     */
    static Region bar(final PackMix mix, final int target, final Runnable onChange) {
        final FlowPane row = new FlowPane(8, 8);
        row.setAlignment(Pos.CENTER_LEFT);
        if (mix.isEmpty()) {
            final Label help = new Label(NeoText.get("mix.help"));
            help.getStyleClass().add("home-subtitle");
            help.setWrapText(true);
            row.getChildren().add(help);
        }
        for (final Map.Entry<CardEdition, Integer> e : mix.entries()) {
            final CardEdition ed = e.getKey();
            final Label code = new Label(ed.getCode());
            code.getStyleClass().add("set-code");
            final Label n = new Label("×" + e.getValue());
            n.getStyleClass().add("duel-name");
            final Button minus = small("−", () -> {
                mix.remove(ed);
                onChange.run();
            });
            final Button plus = small("+", () -> {
                mix.add(ed);
                onChange.run();
            });
            plus.setDisable(mix.total() >= target);
            final HBox chip = new HBox(6, code, n, minus, plus);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("set-tile");
            chip.setPadding(new Insets(4, 8, 4, 10));
            installTip(chip, ed.getName());
            row.getChildren().add(chip);
        }
        final Label total = new Label(NeoText.get("mix.total", mix.total(), target));
        total.getStyleClass().add(mix.total() == target ? "quest-ok" : "quest-problem");
        row.getChildren().add(total);
        if (!mix.isEmpty()) {
            row.getChildren().add(small(NeoText.get("mix.clear"), () -> {
                mix.clear();
                onChange.run();
            }));
        }
        return row;
    }

    /** La ficha de un bloque: su nombre y sus expansiones. */
    static Region blockTile(final CardBlock block, final boolean picked, final Runnable onPick) {
        final Label codes = new Label(PackMix.blockCodes(block));
        codes.getStyleClass().add("set-code");
        final Label name = new Label(block.getName());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(UiScale.px(190));
        name.setMinHeight(Region.USE_PREF_SIZE);
        final VBox box = new VBox(2, codes, name);
        box.getStyleClass().add("set-tile");
        box.setPadding(new Insets(8, 10, 8, 10));
        box.setPrefWidth(UiScale.px(210));
        box.pseudoClassStateChanged(PICKED, picked);
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                onPick.run();
            }
        });
        return box;
    }

    /** Lo que se ensenya en el pie: "Sobres: FRF + 2×KTK (3 de 3)". */
    static String chosen(final PackMix mix, final int target) {
        return mix.isEmpty() ? "" : NeoText.get("mix.chosen", mix.label(), mix.total(), target);
    }

    private static Button small(final String text, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> action.run());
        return b;
    }

    private static void installTip(final Region node, final String text) {
        final javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip(text);
        tip.setShowDelay(javafx.util.Duration.millis(300));
        javafx.scene.control.Tooltip.install(node, tip);
    }
}
