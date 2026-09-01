package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.deck.DeckEditor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * La curva de mana del mazo: cuantos hechizos cuestan cada cantidad.
 *
 * <p>Es el unico grafico del deck builder, y esta porque responde de un vistazo
 * a la pregunta que todo el mundo se hace al montar un mazo: <i>¿voy a tener
 * algo que hacer los primeros turnos?</i> Una lista de numeros no la responde;
 * la silueta de las barras, si.
 *
 * <p>Las tierras no aparecen — no se lanzan — y todo lo de coste 7 o mas se
 * junta en la ultima barra, que es como se lee siempre una curva.
 */
public class ManaCurvePane extends VBox {

    private final HBox bars = new HBox(4);
    private final Label caption = new Label(NeoText.get("curve.caption"));

    private static final double MAX_HEIGHT = 64;

    public ManaCurvePane() {
        getStyleClass().add("mana-curve");
        setSpacing(6);
        setPadding(new Insets(10, 12, 8, 12));

        caption.getStyleClass().add("caption");
        bars.setAlignment(Pos.BOTTOM_LEFT);
        bars.setMinHeight(MAX_HEIGHT + 18);
        bars.setPrefHeight(MAX_HEIGHT + 18);

        getChildren().addAll(caption, bars);
    }

    /** Vuelve a dibujar con la curva que le pase el editor. */
    public void update(final int[] curve) {
        bars.getChildren().clear();

        int peak = 1;
        for (final int n : curve) {
            peak = Math.max(peak, n);
        }

        int total = 0;
        for (int cmc = 0; cmc < curve.length; cmc++) {
            total += curve[cmc];
            bars.getChildren().add(bar(cmc, curve[cmc], peak, curve.length));
        }

        caption.setText(total == 0
                ? NeoText.get("curve.caption")
                : NeoText.get("curve.captionWith", total));
    }

    /**
     * Una barra con su cuenta encima y su coste debajo.
     *
     * <p>La altura es proporcional al pico, no absoluta: lo que interesa es la
     * forma de la curva, no cuantas cartas hay en total.
     */
    private Region bar(final int cmc, final int count, final int peak, final int slots) {
        final Label value = new Label(count == 0 ? "" : String.valueOf(count));
        value.getStyleClass().add("curve-value");

        final Region fill = new Region();
        fill.getStyleClass().add("curve-bar");
        // Una barra de altura 0 no se ve; se le deja un hilo para que la casilla
        // vacia siga leyendose como parte de la curva.
        fill.setPrefHeight(count == 0 ? 2 : Math.max(4, MAX_HEIGHT * count / (double) peak));
        fill.setMinHeight(Region.USE_PREF_SIZE);
        fill.setMaxWidth(Double.MAX_VALUE);
        fill.pseudoClassStateChanged(EMPTY, count == 0);

        final Label label = new Label(cmc == slots - 1 ? (cmc + "+") : String.valueOf(cmc));
        label.getStyleClass().add("curve-label");

        final VBox column = new VBox(2, value, fill, label);
        column.setAlignment(Pos.BOTTOM_CENTER);
        column.setMinWidth(22);
        HBox.setHgrow(column, Priority.ALWAYS);
        return column;
    }

    private static final javafx.css.PseudoClass EMPTY =
            javafx.css.PseudoClass.getPseudoClass("empty");

    /** Atajo: leer la curva directamente del editor. */
    public void update(final DeckEditor editor) {
        update(editor.manaCurve());
    }
}
