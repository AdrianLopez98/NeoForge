package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import forge.card.CardEdition;
import forge.neo.NeoText;
import forge.neo.ascent.AscentPool;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * <b>Con que expansiones</b>: todas, desde una hasta otra, o las que se elijan
 * una a una. El selector de la Quest nueva (Discord, 03-10-2026: <i>"something
 * similar for Quest (choosing sets by year)"</i>).
 *
 * <p>Es el mismo pozo que Ascenso ({@link AscentPool}) con el aspecto de las
 * pantallas oscuras; el de Ascenso vive en su pergamino
 * ({@code AscentSetupScreen.poolBox}) y no se ha tocado. Debajo dice cuantas
 * cartas deja o por que no se puede jugar, que es lo que hay que saber ANTES
 * de empezar: el texto lo pone quien lo usa ({@code describe}), porque cada
 * modo explica cosas distintas.
 */
public final class SetPoolPicker extends VBox {

    private AscentPool.Kind kind = AscentPool.Kind.ALL;
    private String from;
    private String to;
    private final List<String> sets = new ArrayList<>();

    private final Consumer<AscentPool> onChange;
    private final Function<AscentPool, String[]> describe;

    /**
     * @param onChange cada vez que cambia el pozo
     * @param describe el texto de debajo para un pozo que no es "todas":
     *                 {texto, "problem"} si no se puede jugar, {texto, null} si si
     */
    public SetPoolPicker(final Consumer<AscentPool> onChange,
                         final Function<AscentPool, String[]> describe) {
        super(8);
        this.onChange = onChange;
        this.describe = describe;
        setAlignment(Pos.TOP_LEFT);
        rebuild();
    }

    /** El pozo puesto ahora. */
    public AscentPool pool() {
        switch (kind) {
            case RANGE:
                return AscentPool.range(from, to);
            case SET:
                return AscentPool.sets(sets);
            default:
                return AscentPool.ALL;
        }
    }

    /** Lo deja puesto sin avisar (para abrir la pantalla con un pozo, y capturarla). */
    public void preset(final AscentPool p) {
        kind = p.kind;
        if (p.kind == AscentPool.Kind.RANGE) {
            from = p.from;
            to = p.to;
        } else if (p.kind == AscentPool.Kind.SET) {
            sets.clear();
            sets.addAll(p.sets);
        }
        rebuild();
    }

    /** Vuelve a "todas" sin avisar (lo pide quien elige un mundo). */
    public void reset() {
        if (kind != AscentPool.Kind.ALL) {
            kind = AscentPool.Kind.ALL;
            rebuild();
        }
    }

    /** Vuelve a pintar el texto de debajo (cambiaron las reglas, p. ej.). */
    public void refresh() {
        rebuild();
    }

    private void changed() {
        rebuild();
        onChange.accept(pool());
    }

    private void rebuild() {
        getChildren().clear();
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        for (final AscentPool.Kind k : AscentPool.Kind.values()) {
            final Button b = new Button(NeoText.get("ascent.pool." + k.name().toLowerCase(java.util.Locale.ROOT)));
            b.getStyleClass().add(k == kind ? "btn-primary" : "btn-secondary");
            b.setMinWidth(Button.USE_PREF_SIZE);
            b.setOnAction(e -> {
                if (k == kind) {
                    return;
                }
                kind = k;
                final List<CardEdition> eds = AscentPool.editions();
                if (k == AscentPool.Kind.RANGE && from == null && !eds.isEmpty()) {
                    from = eds.get(0).getCode();
                    to = eds.get(eds.size() - 1).getCode();
                }
                changed();
            });
            row.getChildren().add(b);
        }
        getChildren().add(row);

        if (kind == AscentPool.Kind.RANGE) {
            final Label a = new Label(NeoText.get("ascent.pool.from"));
            final Label b = new Label(NeoText.get("ascent.pool.to"));
            a.getStyleClass().add("caption");
            b.getStyleClass().add("caption");
            final HBox pick = new HBox(10, a, editionBox(from, c -> {
                from = c;
                changed();
            }), b, editionBox(to, c -> {
                to = c;
                changed();
            }));
            pick.setAlignment(Pos.CENTER_LEFT);
            getChildren().add(pick);
        } else if (kind == AscentPool.Kind.SET) {
            final ComboBox<CardEdition> adder = editionBox(null, c -> {
                if (!sets.contains(c)) {
                    sets.add(c);
                    changed();
                }
            });
            adder.setPromptText(NeoText.get("ascent.pool.add"));
            getChildren().add(adder);
            final List<String> chosen = pool().sets;
            if (!chosen.isEmpty()) {
                final FlowPane chips = new FlowPane(6, 6);
                for (final String code : chosen) {
                    final Button chip = new Button(nameOf(code) + "  \u00d7");
                    chip.getStyleClass().add("keyword-chip");
                    chip.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("ascent.pool.remove")));
                    chip.setOnAction(e -> {
                        sets.remove(code);
                        changed();
                    });
                    chips.getChildren().add(chip);
                }
                getChildren().add(chips);
            }
        }

        final AscentPool p = pool();
        if (!p.isAll()) {
            final String[] text = describe.apply(p);
            final Label info = new Label(text[0]);
            info.getStyleClass().add(text[1] != null ? "quest-problem" : "home-subtitle");
            info.setWrapText(true);
            info.setMaxWidth(UiScale.px(760));
            getChildren().add(info);
        }
    }

    /** "Zendikar (ZEN)", o el codigo si el motor no la conoce. */
    private static String nameOf(final String code) {
        try {
            final CardEdition ed = forge.model.FModel.getMagicDb().getEditions().get(code);
            return ed == null ? code : ed.getName() + " (" + code + ")";
        } catch (final RuntimeException e) {
            return code;
        }
    }

    /** Un desplegable de expansiones, de la mas vieja a la mas nueva, con su anyo. */
    private static ComboBox<CardEdition> editionBox(final String selected, final Consumer<String> onPick) {
        final ComboBox<CardEdition> box = new ComboBox<>();
        box.getStyleClass().add("team-combo");
        box.getItems().addAll(AscentPool.editions());
        box.setVisibleRowCount(16);
        box.setPrefWidth(UiScale.px(320));
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final CardEdition ed) {
                if (ed == null) {
                    return "";
                }
                final String year = ed.getDate() == null ? ""
                        : "  \u00b7  " + new java.text.SimpleDateFormat("yyyy", java.util.Locale.ROOT)
                                .format(ed.getDate());
                return ed.getName() + " (" + ed.getCode() + ")" + year;
            }

            @Override
            public CardEdition fromString(final String s) {
                return null;
            }
        });
        for (final CardEdition ed : box.getItems()) {
            if (ed.getCode().equals(selected)) {
                box.getSelectionModel().select(ed);
                break;
            }
        }
        box.setOnAction(e -> {
            if (box.getValue() != null) {
                onPick.accept(box.getValue().getCode());
            }
        });
        return box;
    }
}
