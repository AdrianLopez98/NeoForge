package forge.neo.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import forge.neo.NeoShortcuts;
import forge.neo.NeoShortcuts.Action;
import forge.neo.NeoShortcuts.Chord;
import forge.neo.NeoShortcuts.Preset;
import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Los atajos de teclado: consultarlos y cambiarlos.
 *
 * <p>Es una pantalla aparte y no mas filas en Ajustes, por lo que pidio Ana al
 * encargarlo: trece acciones con dos teclas cada una alargarian un scroll que
 * ya es largo, y esto se viene a <b>consultar</b> tanto como a cambiar. Se llega
 * desde Ajustes (en el menu y en la pausa, con su "Volver") y, en partida, con
 * la tecla de "ver los atajos" (H de fabrica), que abre esta misma pantalla.
 *
 * <p>Cambiar una tecla: click en el hueco, se pulsa la nueva. Retroceso la
 * quita y Escape cancela. Mientras se esta escuchando, esta pantalla <b>se
 * queda con todas las pulsaciones</b> (filtro, no manejador): si no, pulsar
 * Espacio para asignarlo pasaria la prioridad, y Escape cerraria los ajustes en
 * vez de cancelar.
 */
public class ShortcutsPanel extends VBox {

    private final Label title = new Label(NeoText.get("shortcuts.title"));
    private final VBox content = new VBox(4);
    private final ScrollPane scroll = new ScrollPane(content);
    private final HBox footer = new HBox();
    private final Label note = new Label();

    private final Map<Action, Button[]> keyButtons = new EnumMap<>(Action.class);
    private final Map<Preset, Button> presetButtons = new EnumMap<>(Preset.class);

    /** El hueco que esta escuchando, si hay alguno. */
    private Button capturing;
    private Action capturingAction;
    private int capturingSlot;
    /** Modificadores pulsados sin tecla todavia: "Ctrl+Shift" se asigna al soltar. */
    private Chord pendingModifiers;

    /**
     * @param standalone true si va suelta encima de la mesa (con su marco de
     *     dialogo); false si va dentro del panel de Ajustes, que ya lo tiene
     * @param backLabel el texto del boton de salir
     * @param onBack lo que hace ese boton
     */
    public ShortcutsPanel(final boolean standalone, final String backLabel, final Runnable onBack) {
        setSpacing(6);
        if (standalone) {
            getStyleClass().addAll("dialog", "settings");
            setPadding(new Insets(22, 26, 18, 26));
        }
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        title.getStyleClass().add("dialog-title");

        final Label hint = new Label(NeoText.get("shortcuts.hint"));
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(UiScale.px(560));
        hint.setMinHeight(Region.USE_PREF_SIZE);

        // --- estilo: NeoForge / Forge / Arena ---
        final javafx.scene.layout.FlowPane presets = new javafx.scene.layout.FlowPane(4, 4);
        for (final Preset p : Preset.values()) {
            final Button b = new Button(p.label());
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> {
                cancelCapture();
                NeoShortcuts.applyPreset(p);
                note.setText(NeoText.get("shortcuts.preset.applied", p.label()));
                refresh();
            });
            presetButtons.put(p, b);
            presets.getChildren().add(b);
        }
        content.getChildren().add(row(NeoText.get("shortcuts.preset"), presets));

        note.getStyleClass().add("settings-note");
        note.setWrapText(true);
        note.setMaxWidth(UiScale.px(560));
        note.setMinHeight(Region.USE_PREF_SIZE);
        // Vacia no ocupa: si no, deja un hueco entre el estilo y la primera
        // accion que no se explica (se vio en la primera captura).
        note.managedProperty().bind(note.textProperty().isNotEmpty());
        note.visibleProperty().bind(note.textProperty().isNotEmpty());
        content.getChildren().add(note);

        // --- una fila por accion, con sus dos huecos ---
        for (final Action a : Action.values()) {
            final Button[] slots = new Button[NeoShortcuts.SLOTS];
            final HBox keys = new HBox(6);
            keys.setAlignment(Pos.CENTER_LEFT);
            for (int i = 0; i < slots.length; i++) {
                final int slot = i;
                final Button b = new Button();
                b.getStyleClass().addAll("segment", "shortcut-key");
                b.setMinWidth(UiScale.px(118));
                b.setOnAction(e -> startCapture(b, a, slot));
                b.focusedProperty().addListener((o, was, is) -> {
                    if (!is && capturing == b) {
                        cancelCapture();
                    }
                });
                slots[i] = b;
                keys.getChildren().add(b);
            }
            keyButtons.put(a, slots);
            content.getChildren().add(row(NeoText.get(a.textKey()), keys));
        }

        final Label escape = new Label(NeoText.get("shortcuts.escape"));
        escape.getStyleClass().add("home-subtitle");
        escape.setWrapText(true);
        escape.setMaxWidth(UiScale.px(560));
        escape.setMinHeight(Region.USE_PREF_SIZE);
        content.getChildren().add(escape);

        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        final Button back = new Button(backLabel);
        back.getStyleClass().add("btn-primary");
        back.setOnAction(e -> {
            cancelCapture();
            onBack.run();
        });
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        footer.getChildren().addAll(gap, back);
        footer.setPadding(new Insets(12, 0, 0, 0));

        title.setMinHeight(Region.USE_PREF_SIZE);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        footer.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().setAll(title, hint, scroll, footer);

        addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        addEventFilter(KeyEvent.KEY_RELEASED, this::onKeyReleased);
        addEventFilter(KeyEvent.KEY_TYPED, e -> {
            if (capturing != null) {
                e.consume();
            }
        });

        refresh();
    }

    /** Vuelve a escribir las teclas de cada hueco y marca el estilo que coincida. */
    private void refresh() {
        for (final Map.Entry<Action, Button[]> e : keyButtons.entrySet()) {
            final List<Chord> chords = NeoShortcuts.bindings(e.getKey());
            final Button[] slots = e.getValue();
            for (int i = 0; i < slots.length; i++) {
                slots[i].setText(i < chords.size() ? chords.get(i).label() : NeoText.get("shortcuts.none"));
                slots[i].pseudoClassStateChanged(SELECTED, false);
            }
        }
        final Preset now = NeoShortcuts.currentPreset();
        for (final Map.Entry<Preset, Button> e : presetButtons.entrySet()) {
            e.getValue().pseudoClassStateChanged(SELECTED, e.getKey() == now);
        }
    }

    private void startCapture(final Button b, final Action a, final int slot) {
        cancelCapture();
        capturing = b;
        capturingAction = a;
        capturingSlot = slot;
        pendingModifiers = null;
        b.setText(NeoText.get("shortcuts.press"));
        b.pseudoClassStateChanged(SELECTED, true);
        b.requestFocus();
    }

    private void cancelCapture() {
        if (capturing == null) {
            return;
        }
        capturing = null;
        capturingAction = null;
        pendingModifiers = null;
        refresh();
    }

    private void onKeyPressed(final KeyEvent e) {
        if (capturing == null) {
            return;
        }
        e.consume();
        final KeyCode code = e.getCode();
        if (code == KeyCode.ESCAPE) {
            cancelCapture();
            return;
        }
        final Chord pressed = Chord.fromEvent(e);
        if ((code == KeyCode.BACK_SPACE || code == KeyCode.DELETE) && !pressed.hasModifiers()) {
            assign(null);
            return;
        }
        if (pressed.key() != null) {
            if (pressed.isValid()) {
                assign(pressed);
            }
            return;
        }
        // Solo modificadores: se ensenya lo que va y se espera a la tecla, o a
        // que se suelten (ahi se asigna "Ctrl+Shift" si son al menos dos).
        pendingModifiers = pressed;
        capturing.setText(pressed.label() + "+...");
    }

    private void onKeyReleased(final KeyEvent e) {
        if (capturing == null) {
            return;
        }
        e.consume();
        final Chord pending = pendingModifiers;
        pendingModifiers = null;
        if (pending != null && pending.isValid()) {
            assign(pending);
        } else if (capturing != null) {
            capturing.setText(NeoText.get("shortcuts.press"));
        }
    }

    private void assign(final Chord chord) {
        final Action action = capturingAction;
        final int slot = capturingSlot;
        capturing = null;
        capturingAction = null;
        pendingModifiers = null;
        if (action == null) {
            return;
        }
        final Action displaced = NeoShortcuts.set(action, slot, chord);
        note.setText(displaced == null || chord == null ? ""
                : NeoText.get("shortcuts.moved", chord.label(), NeoText.get(displaced.textKey())));
        refresh();
    }

    /**
     * Cuanto puede medir el visor sin salirse de la ventana. Lo mismo que hace
     * {@code SettingsPanel}, por lo mismo: medido en {@code layoutChildren}
     * porque en el constructor la escena todavia mide cero.
     */
    @Override
    protected void layoutChildren() {
        final javafx.scene.Scene sc = getScene();
        if (sc != null && sc.getHeight() > 0) {
            double chrome = getPadding().getTop() + getPadding().getBottom() + getSpacing() * 3 + 8;
            for (final javafx.scene.Node n : getChildren()) {
                if (n != scroll) {
                    chrome += n.prefHeight(-1);
                }
            }
            final double room = sc.getHeight() * 0.90 - chrome;
            final double wanted = content.prefHeight(content.getWidth() > 0
                    ? content.getWidth() : content.prefWidth(-1));
            final double h = Math.max(160, Math.min(wanted + 2, room));
            if (Math.abs(h - scroll.getPrefViewportHeight()) > 1) {
                scroll.setPrefViewportHeight(h);
            }
        }
        super.layoutChildren();
    }

    /** Etiqueta a la izquierda y control a la derecha, como las filas de Ajustes. */
    private static HBox row(final String caption, final Region control) {
        final Label label = new Label(caption);
        label.getStyleClass().add("settings-label");
        label.setMinWidth(UiScale.px(260));
        label.setMaxWidth(UiScale.px(260));
        label.setWrapText(true);
        final HBox box = new HBox(14, label, control);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(3, 0, 3, 0));
        return box;
    }

    /** Para las pruebas: cuantas filas de accion hay. */
    public int actionRows() {
        return new ArrayList<>(keyButtons.keySet()).size();
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
}
