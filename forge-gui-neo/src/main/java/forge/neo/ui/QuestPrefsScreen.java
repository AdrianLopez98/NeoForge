package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.neo.NeoText;
import forge.neo.quest.NeoQuestPrefs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Las preferencias de la Quest: las de Forge, con su boton de volver a como
 * venian (Discord, 10-10-2026). Que hay y por que, en {@link NeoQuestPrefs}.
 *
 * <p>Cada fila dice lo que vale <b>de fabrica</b>, y la que has cambiado se
 * marca y trae su ↺: sin eso no hay forma de saber que has tocado ni de volver
 * a una sola. "Volver a como venian" (todas) va a la izquierda de Volver y
 * pregunta, con Cancelar marcado: no se deshace (principios 6 y 12).
 *
 * <p>Un numero se guarda al pulsar Intro o al salir del campo. Si Forge no lo
 * admite, se dice debajo y el campo se queda como estaba.
 */
public class QuestPrefsScreen extends StackPane {

    private static final javafx.css.PseudoClass CHANGED =
            javafx.css.PseudoClass.getPseudoClass("changed");
    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    private final Runnable back;
    /** Los grupos, en columnas: son nueve y casi sesenta filas, en una sola eran tres pantallas. */
    private final javafx.scene.layout.FlowPane content = new javafx.scene.layout.FlowPane(16, 16);
    private final Label changed = new Label();
    private final Overlay overlay = new Overlay();

    public QuestPrefsScreen(final Runnable back) {
        this.back = back;
        getStyleClass().addAll("table-root", "quest");

        final BorderPane frame = new BorderPane();
        frame.setTop(header());
        content.setPadding(new Insets(8, 30, 22, 30));
        content.setAlignment(Pos.TOP_LEFT);
        content.setRowValignment(javafx.geometry.VPos.TOP);
        final ScrollPane sp = new ScrollPane(content);
        content.prefWrapLengthProperty().bind(sp.widthProperty().subtract(UiScale.px(70)));
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        frame.setCenter(sp);

        final Button resetAll = new Button(NeoText.get("questPrefs.resetAll"));
        resetAll.getStyleClass().add("btn-secondary");
        resetAll.setOnAction(e -> askResetAll());
        frame.setBottom(BackBar.of(back, resetAll));

        getChildren().addAll(frame, overlay);
        fill();
    }

    private Region header() {
        final Label title = new Label(NeoText.get("questPrefs.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("questPrefs.subtitle"));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        changed.getStyleClass().add("home-subtitle");
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final VBox texts = new VBox(2, title, sub);
        HBox.setHgrow(texts, Priority.SOMETIMES);
        final HBox row = new HBox(14, texts, gap, changed);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(18, 28, 8, 30));
        return row;
    }

    /** Se repinta entero tras volver a como venian: es una vez, y es lo que ha cambiado todo. */
    private void fill() {
        content.getChildren().clear();
        final int current = NeoQuestPrefs.currentDifficulty();
        final List<NeoQuestPrefs.Group> groups = NeoQuestPrefs.groups();
        for (int g = 0; g < groups.size(); g++) {
            // Los cuatro de dificultad son los ultimos; el de la Quest en curso
            // se dice, que es el que se nota al jugar.
            final int difficulty = g - (groups.size() - 4);
            content.getChildren().add(groupBox(groups.get(g), difficulty >= 0 && difficulty == current));
        }
        refreshCount();
    }

    private void refreshCount() {
        final int n = NeoQuestPrefs.changedCount();
        changed.setText(n == 0 ? NeoText.get("questPrefs.allDefault") : NeoText.get("questPrefs.changed", n));
    }

    private Region groupBox(final NeoQuestPrefs.Group group, final boolean yours) {
        final Label name = new Label(group.getTitle()
                + (yours ? "   ·   " + NeoText.get("questPrefs.yours") : ""));
        name.getStyleClass().add("quest-deck-name");

        final GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        int r = 0;
        for (final NeoQuestPrefs.Item item : group.getItems()) {
            r = addRow(grid, r, item);
        }
        final VBox box = new VBox(10, name, grid);
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(14, 18, 14, 18));
        box.setPrefWidth(UiScale.px(580));
        return box;
    }

    /** Una fila (y su linea de error, debajo). Devuelve la siguiente fila libre. */
    private int addRow(final GridPane grid, final int r, final NeoQuestPrefs.Item item) {
        final Label label = new Label(item.getLabel());
        label.getStyleClass().add("settings-label");
        label.setWrapText(true);
        label.setMaxWidth(UiScale.px(250));
        label.setMinWidth(UiScale.px(250));
        if (!item.getTip().isEmpty()) {
            final Tooltip tip = new Tooltip(item.getTip());
            tip.setWrapText(true);
            tip.setMaxWidth(UiScale.px(420));
            label.setTooltip(tip);
        }

        final Label factory = new Label(NeoText.get("questPrefs.default", shown(item, item.getDefault())));
        factory.getStyleClass().add("caption");

        final Label error = new Label();
        error.getStyleClass().add("set-price-no");
        error.setVisible(false);
        error.setManaged(false);

        final Button undo = new Button("↺");
        undo.getStyleClass().add("btn-secondary");
        undo.setTooltip(new Tooltip(NeoText.get("questPrefs.resetOne")));

        final Region control;
        final Runnable[] sync = new Runnable[1];
        if (item.getKind() == NeoQuestPrefs.Kind.SWITCH) {
            final Button yes = new Button(NeoText.get("common.yes"));
            final Button no = new Button(NeoText.get("common.no"));
            for (final Button b : new Button[] {yes, no}) {
                b.getStyleClass().add("segment");
                b.setMinWidth(Region.USE_PREF_SIZE);
            }
            yes.setOnAction(e -> {
                NeoQuestPrefs.setOn(item, true);
                sync[0].run();
            });
            no.setOnAction(e -> {
                NeoQuestPrefs.setOn(item, false);
                sync[0].run();
            });
            sync[0] = () -> {
                yes.pseudoClassStateChanged(SELECTED, item.isOn());
                no.pseudoClassStateChanged(SELECTED, !item.isOn());
                label.pseudoClassStateChanged(CHANGED, !item.isDefault());
                undo.setVisible(!item.isDefault());
                refreshCount();
            };
            control = new HBox(4, yes, no);
        } else {
            final TextField field = new TextField(item.getValue());
            field.getStyleClass().add("text-input");
            field.setPrefColumnCount(6);
            field.setMaxWidth(UiScale.px(110));
            final Runnable commit = () -> {
                if (field.getText().trim().equals(item.getValue().trim())) {
                    return;
                }
                final String why = NeoQuestPrefs.set(item, field.getText());
                error.setText(why == null ? "" : why);
                error.setVisible(why != null);
                error.setManaged(why != null);
                if (why != null) {
                    field.setText(item.getValue());
                }
                sync[0].run();
            };
            field.setOnAction(e -> commit.run());
            field.focusedProperty().addListener((o, was, is) -> {
                if (!is) {
                    commit.run();
                }
            });
            sync[0] = () -> {
                if (!field.isFocused()) {
                    field.setText(item.getValue());
                }
                label.pseudoClassStateChanged(CHANGED, !item.isDefault());
                undo.setVisible(!item.isDefault());
                refreshCount();
            };
            control = field;
        }
        undo.setOnAction(e -> {
            NeoQuestPrefs.reset(item);
            error.setVisible(false);
            error.setManaged(false);
            if (control instanceof TextField tf) {
                tf.setText(item.getValue());
            }
            sync[0].run();
        });
        sync[0].run();

        grid.add(label, 0, r);
        grid.add(control, 1, r);
        grid.add(factory, 2, r);
        grid.add(undo, 3, r);
        grid.add(error, 1, r + 1, 3, 1);
        return r + 2;
    }

    /** Un interruptor de fabrica se dice Si/No, no 0/1. */
    private static String shown(final NeoQuestPrefs.Item item, final String value) {
        if (item.getKind() == NeoQuestPrefs.Kind.SWITCH) {
            return NeoText.get("1".equals(value.trim()) ? "common.yes" : "common.no");
        }
        return value;
    }

    /** La marcada es CANCELAR: es lo que sale de un Intro por inercia (principio 6). */
    private void askResetAll() {
        final int n = NeoQuestPrefs.changedCount();
        if (n == 0) {
            return;
        }
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("questPrefs.resetAsk.title"),
                NeoText.get("questPrefs.resetAsk.body", n),
                new ArrayList<>(List.of(NeoText.get("questPrefs.resetAll"), NeoText.get("common.cancel"))), 1,
                choice -> {
                    overlay.hide();
                    if (choice == 0) {
                        NeoQuestPrefs.resetAll();
                        fill();
                    }
                }));
    }

    /** Solo pruebas: el aviso de volver a como venian, sin pulsar nada. */
    public void showResetAskForTest() {
        askResetAll();
    }
}
