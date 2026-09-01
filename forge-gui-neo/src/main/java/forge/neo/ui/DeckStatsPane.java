package forge.neo.ui;

import forge.deck.CommanderBracketCalculator;
import forge.neo.NeoText;
import forge.util.Localizer;
import java.util.Locale;
import java.util.Map;

import forge.neo.deck.DeckEditor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Los cuatro numeros que se miran al montar un mazo.
 *
 * <p>La curva ya contesta <i>"voy a tener algo que hacer los primeros
 * turnos"</i>. Esto contesta las otras tres preguntas de siempre, y ninguna se
 * puede sacar de la lista de cartas de un vistazo:
 *
 * <ul>
 *   <li><b>Cuanto de cada color pide el mazo.</b> Se cuentan los <b>simbolos
 *       del coste</b>, no las cartas: diez cartas que piden un verde y una que
 *       pide tres no es "once cartas verdes". Es lo que decide cuantas tierras
 *       de cada color hacen falta, y contar cartas se queda corto justo en los
 *       mazos donde importa.</li>
 *   <li><b>Tierras frente a hechizos.</b> El numero que mas se mira y el que
 *       mas se olvida al ir metiendo cartas buenas.</li>
 *   <li><b>El coste medio.</b> Dice si el mazo es rapido o pesado, y hay que
 *       leerlo junto a las tierras: subir la media sin subir las tierras es el
 *       error clasico.</li>
 * </ul>
 *
 * <p>Las barras de color usan los colores canonicos de Magic, los mismos que
 * los pips de la mesa: un mazo se reconoce por su franja de color antes de leer
 * ningun numero.
 */
public class DeckStatsPane extends VBox {

    private final HBox pips = new HBox(6);
    private final Label mix = new Label();
    private final Label average = new Label();
    private final Label bracket = new Label();

    /** Las letras de los cinco colores, en el orden de Magic. */
    private static final String[] LETTERS = {"w", "u", "b", "r", "g"};

    public DeckStatsPane() {
        getStyleClass().add("deck-stats");
        setSpacing(6);
        setPadding(new Insets(10, 12, 10, 12));

        final Label caption = new Label(NeoText.get("stats.caption"));
        caption.getStyleClass().add("caption");

        pips.setAlignment(Pos.CENTER_LEFT);
        for (final Label l : new Label[] {mix, average, bracket}) {
            l.getStyleClass().add("home-subtitle");
            // Envuelve en vez de cortar con puntos suspensivos: la ultima linea
            // enumera los tipos y cortarla se come justo el final ("9 encanta...").
            l.setWrapText(true);
            l.setMaxWidth(Double.MAX_VALUE);
            l.setMinHeight(Region.USE_PREF_SIZE);
        }
        bracket.setVisible(false);
        bracket.setManaged(false);

        getChildren().addAll(caption, pips, mix, average, bracket);
    }

    /** Vuelve a calcularlo todo con lo que hay ahora en el mazo. */
    public void update(final DeckEditor editor) {
        pips.getChildren().clear();

        final int[] counts = editor.colourPips();
        int total = 0;
        for (final int n : counts) {
            total += n;
        }

        if (total == 0) {
            final Label none = new Label(NeoText.get("stats.noPips"));
            none.getStyleClass().add("home-subtitle");
            pips.getChildren().add(none);
        } else {
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] == 0) {
                    // Un color que no se usa no ocupa sitio: cinco casillas
                    // fijas con tres a cero solo hacen ruido.
                    continue;
                }
                pips.getChildren().add(pip(LETTERS[i], counts[i], counts[i] * 100 / total));
            }
        }

        final int lands = editor.landCount();
        final int spells = editor.mainCount() - lands;
        mix.setText(NeoText.get("stats.mix", lands, spells)
                + (editor.mainCount() == 0 ? ""
                        : NeoText.get("stats.landPct",
                                lands * 100 / Math.max(1, editor.mainCount()))));

        average.setText(NeoText.get("stats.average",
                String.format(Locale.ROOT, "%.2f", editor.averageCmc()),
                typeSummary(editor)));

        final boolean showBracket = editor.usesCommander();
        bracket.setVisible(showBracket);
        bracket.setManaged(showBracket);
        if (showBracket) {
            final int level = CommanderBracketCalculator.getBracket(editor.getDeck());
            bracket.setText(Localizer.getInstance().getMessage("lblCommanderBracketMinimum", level));
        }
    }

    /** Una pastilla de color con cuantos simbolos pide y su porcentaje. */
    private static Region pip(final String letter, final int count, final int percent) {
        final Label value = new Label(String.valueOf(count));
        value.getStyleClass().add("pip-count");

        final Label share = new Label(percent + "%");
        share.getStyleClass().add("pip-share");

        final VBox box = new VBox(-1, value, share);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().addAll("colour-pip", "mana-" + letter);
        box.setPadding(new Insets(3, 9, 3, 9));
        box.setMinWidth(Region.USE_PREF_SIZE);
        return box;
    }

    /** "24 criaturas · 12 hechizos · 3 artefactos", lo que haya. */
    private static String typeSummary(final DeckEditor editor) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Integer> e : editor.typeCounts().entrySet()) {
            if (DeckEditor.LANDS.equals(e.getKey())) {
                // Las tierras ya salen en su propia linea: repetirlas aqui
                // gasta la mitad del renglon en lo mismo.
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(e.getValue()).append(' ')
              .append(DeckEditor.groupLabel(e.getKey()).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** Un hueco elastico, para cuando esto va en una fila. */
    public static Region grow(final Region node) {
        HBox.setHgrow(node, Priority.ALWAYS);
        return node;
    }
}
