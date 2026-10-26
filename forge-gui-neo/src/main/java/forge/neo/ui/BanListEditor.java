package forge.neo.ui;

import java.util.List;

import forge.neo.NeoText;
import forge.neo.deck.BanList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * <b>Las cartas prohibidas de cada formato, editables</b> (Discord, 26-10-2026),
 * en Ajustes → Modos. Un formato, sus prohibidas con una ✕ para quitarlas, un
 * buscador para anyadir, y volver a la oficial. Todo lo hace {@link BanList};
 * aqui solo se pinta, y se repinta tras cada cambio.
 */
final class BanListEditor extends VBox {

    private static final int MAX_SUGGESTIONS = 12;

    private String format;
    private final FlowPane formats = new FlowPane(4, 4);
    private final Label status = new Label();
    private final Button reset = new Button(NeoText.get("banlist.reset"));
    private final FlowPane cards = new FlowPane(6, 6);
    private final TextField search = new TextField();
    private final FlowPane suggestions = new FlowPane(6, 6);

    BanListEditor() {
        super(8);
        final List<String> all = BanList.formats();
        format = all.isEmpty() ? null : all.get(0);
        for (final String f : all) {
            final Button b = new Button(f);
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> {
                format = f;
                refresh();
            });
            formats.getChildren().add(b);
        }
        status.getStyleClass().add("settings-note");
        reset.getStyleClass().add("segment");
        reset.setOnAction(e -> {
            if (format != null) {
                BanList.reset(format);
                refresh();
            }
        });
        final HBox head = new HBox(10, status, reset);
        head.setAlignment(Pos.CENTER_LEFT);
        search.setPromptText(NeoText.get("banlist.search"));
        search.setMaxWidth(UiScale.px(360));
        search.textProperty().addListener((o, a, b) -> refreshSuggestions());
        cards.setPrefWrapLength(UiScale.px(640));
        suggestions.setPrefWrapLength(UiScale.px(640));
        final Label note = new Label(NeoText.get("banlist.note"));
        note.getStyleClass().add("settings-note");
        note.setWrapText(true);
        note.setMaxWidth(UiScale.px(640));
        note.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().addAll(formats, head, search, suggestions, cards, note);
        refresh();
    }

    private void refresh() {
        for (final javafx.scene.Node n : formats.getChildren()) {
            if (n instanceof Button b) {
                b.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"),
                        b.getText().equals(format));
            }
        }
        cards.getChildren().clear();
        if (format == null) {
            return;
        }
        final List<String> banned = BanList.banned(format);
        status.setText(NeoText.get(BanList.isEdited(format) ? "banlist.edited" : "banlist.official", banned.size()));
        reset.setDisable(!BanList.isEdited(format));
        for (final String card : banned) {
            final Label name = new Label(local(card));
            final Button x = new Button("✕");
            x.getStyleClass().add("segment");
            x.setOnAction(e -> {
                BanList.unban(format, card);
                refresh();
            });
            final HBox chip = new HBox(6, name, x);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().add("banlist-chip");
            cards.getChildren().add(chip);
        }
        refreshSuggestions();
    }

    private void refreshSuggestions() {
        suggestions.getChildren().clear();
        if (format == null) {
            return;
        }
        final List<String> banned = BanList.banned(format);
        for (final String card : BanList.search(search.getText(), MAX_SUGGESTIONS + banned.size())) {
            if (banned.contains(card)) {
                continue;
            }
            final Button add = new Button("+ " + local(card));
            add.getStyleClass().add("segment");
            add.setOnAction(e -> {
                BanList.ban(format, card);
                refresh();
            });
            suggestions.getChildren().add(add);
            if (suggestions.getChildren().size() >= MAX_SUGGESTIONS) {
                break;
            }
        }
    }

    private static String local(final String card) {
        try {
            final String t = forge.util.CardTranslation.getTranslatedName(card);
            return t == null || t.isBlank() || t.equals(card) ? card : t + " (" + card + ")";
        } catch (final RuntimeException e) {
            return card;
        }
    }
}
