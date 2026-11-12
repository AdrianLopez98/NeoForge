package forge.neo.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.CommanderSearch;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * <b>El filtro de color de los selectores de comandante</b> (Ascenso y Quest;
 * Discord, 12-11-2026). Dos preguntas en una fila:
 *
 * <ul>
 * <li><b>Que colores</b>: pastillas W U B R G C, como el filtro de Arena.
 * Marcar verde da todos los que llevan verde, los monoverdes primero
 * ({@link CommanderSearch#identityMatches}, {@link CommanderSearch#identityExtra}).
 * <li><b>Cuantos colores</b>, de tanto a tanto, 0 a 5
 * ({@link CommanderSearch#colourCountIn}).
 * </ul>
 *
 * <p>El azar de cada pantalla ("Que elija el juego") sale de {@link #pool}: solo
 * los filtros de color, no el buscador ni los favoritos (Ana, 08-10-2026: el
 * azar es entre todos). La regla es del jar y Android usa la misma.
 *
 * <p>Banderas de captura: {@code -Dneo.ascent.setupIdentity=G} (o WU, C) y
 * {@code -Dneo.ascent.setupColourRange=2-3}; valen para las dos pantallas.
 */
final class CommanderColourFilter {

    private int identity = parseIdentity(System.getProperty("neo.ascent.setupIdentity", ""));
    private int minColours = rangeEnd(0);
    private int maxColours = rangeEnd(1);
    private final Runnable onChange;
    private final String labelStyle;

    /**
     * {@code onChange}: volver a pintar la rejilla (desde la primera pagina).
     * {@code labelStyle}: la clase de las etiquetas, la del texto de cada
     * pantalla (Ascenso es pergamino; la Quest, mesa oscura).
     */
    CommanderColourFilter(final String labelStyle, final Runnable onChange) {
        this.labelStyle = labelStyle;
        this.onChange = onChange;
    }

    /** Si hay algun filtro de color puesto. */
    boolean active() {
        return identity != CommanderSearch.ANY_IDENTITY || minColours > 0 || maxColours < 5;
    }

    /** Lo que deja pasar de {@code cards}, en orden Arena (los de justo esos colores primero; estable). */
    List<PaperCard> apply(final List<PaperCard> cards) {
        if (!active()) {
            return cards;
        }
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard c : cards) {
            if (CommanderSearch.identityMatches(c, identity)
                    && CommanderSearch.colourCountIn(c, minColours, maxColours)) {
                out.add(c);
            }
        }
        if (identity != CommanderSearch.ANY_IDENTITY) {
            final int wanted = identity;
            out.sort(Comparator.comparingInt(c -> CommanderSearch.identityExtra(c, wanted)));
        }
        return out;
    }

    /** Uno al azar de los que deja el filtro; null si no hay filtro o no queda ninguno. */
    PaperCard pickRandom(final List<PaperCard> cards) {
        if (!active()) {
            return null;
        }
        final List<PaperCard> pool = apply(cards);
        return pool.isEmpty() ? null : pool.get(forge.util.MyRandom.getRandom().nextInt(pool.size()));
    }

    /** Las pastillas y el rango, en una fila. */
    Region build() {
        final HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER);
        final String tip = NeoText.get("ascent.setup.identity.tip");
        for (int i = 0; i <= AscentSetupScreen.COLOUR_ORDER.length; i++) {
            final int c = i < AscentSetupScreen.COLOUR_ORDER.length ? AscentSetupScreen.COLOUR_ORDER[i] : 0;
            final String letter = i < AscentSetupScreen.COLOUR_ORDER.length ? AscentSetupScreen.COLOUR_LETTERS[i] : "C";
            final Button b = new Button(letter);
            b.getStyleClass().addAll("segment", "colour-filter", "mana-" + letter.toLowerCase(Locale.ROOT));
            final boolean on = identity != CommanderSearch.ANY_IDENTITY
                    && (c == 0 ? identity == 0 : (identity & c) != 0);
            if (on) {
                b.getStyleClass().add("colour-filter-on");
            }
            b.setTooltip(new Tooltip(tip));
            b.setOnAction(e -> {
                identity = CommanderSearch.toggleIdentity(identity, c);
                onChange.run();
            });
            row.getChildren().add(b);
        }
        final Label count = new Label(NeoText.get("ascent.setup.colourCount"));
        count.getStyleClass().add(labelStyle);
        count.setPadding(new Insets(0, 0, 0, UiScale.px(8)));
        final Label dash = new Label("–");
        dash.getStyleClass().add(labelStyle);
        row.getChildren().addAll(count, countBox(minColours, v -> {
            minColours = v;
            maxColours = Math.max(maxColours, v);
            onChange.run();
        }), dash, countBox(maxColours, v -> {
            maxColours = v;
            minColours = Math.min(minColours, v);
            onChange.run();
        }));
        return row;
    }

    private static ComboBox<Integer> countBox(final int value, final java.util.function.IntConsumer onPick) {
        final ComboBox<Integer> box = new ComboBox<>();
        box.getStyleClass().add("team-combo");
        box.getItems().addAll(0, 1, 2, 3, 4, 5);
        box.setValue(value);
        box.setTooltip(new Tooltip(NeoText.get("ascent.setup.colourCount.tip")));
        box.setOnAction(e -> {
            if (box.getValue() != null && box.getValue() != value) {
                onPick.accept(box.getValue());
            }
        });
        return box;
    }

    private static int parseIdentity(final String letters) {
        if (letters == null || letters.isBlank()) {
            return CommanderSearch.ANY_IDENTITY;
        }
        if (letters.equalsIgnoreCase("C")) {
            return 0;
        }
        int out = 0;
        for (int i = 0; i < AscentSetupScreen.COLOUR_LETTERS.length; i++) {
            if (letters.toUpperCase(Locale.ROOT).contains(AscentSetupScreen.COLOUR_LETTERS[i])) {
                out |= AscentSetupScreen.COLOUR_ORDER[i];
            }
        }
        return out == 0 ? CommanderSearch.ANY_IDENTITY : out;
    }

    private static int rangeEnd(final int which) {
        final String[] p = System.getProperty("neo.ascent.setupColourRange", "0-5").split("-");
        try {
            return Math.max(0, Math.min(5, Integer.parseInt(p[Math.min(which, p.length - 1)].trim())));
        } catch (final NumberFormatException e) {
            return which == 0 ? 0 : 5;
        }
    }
}
