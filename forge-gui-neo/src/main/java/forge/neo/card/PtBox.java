package forge.neo.card;

import javafx.geometry.VPos;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.StrokeType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * <b>La caja de fuerza/resistencia de la mesa</b>, dibujada como la de una
 * carta impresa: marco en relieve con las esquinas en chaflan, una ranura
 * oscura y el panel del color de la carta. La forma y los colores son de
 * {@link PtBoxStyle}, que comparte Android.
 *
 * <p>Las cifras se centran por la altura de sus MAYUSCULAS, no por la caja de
 * la letra: cada letra deja un hueco distinto encima y debajo, y centrando la
 * caja las cifras quedaban caidas hacia abajo.
 *
 * <p>Letra: Times New Roman en Windows (la mas parecida a la de las cartas);
 * donde no la hay (Linux), la serif del sistema.
 */
public final class PtBox extends Region {

    private static final String FAMILY;
    /** Altura de las mayusculas, en fracciones del tamanyo de letra. */
    private static final double CAP;

    static {
        boolean times = false;
        try {
            times = Font.getFamilies().contains("Times New Roman");
        } catch (final RuntimeException e) {
            // sin lista de letras: la serif del sistema
        }
        FAMILY = times ? "Times New Roman" : "Serif";
        CAP = times ? 0.662 : 0.72;
    }

    private final Path rim = new Path();
    private final Path groove = new Path();
    private final Path panel = new Path();
    private final Text pow = new Text();
    private final Text slash = new Text("/");
    private final Text tou = new Text();

    private double fontSize = 12;
    private int panelArgb = 0;

    public PtBox() {
        getStyleClass().add("card-pt-box");
        rim.setStroke(Color.rgb(0, 0, 0, 0.6));
        rim.setStrokeType(StrokeType.OUTSIDE);
        rim.setStrokeLineJoin(StrokeLineJoin.ROUND);
        rim.setEffect(new DropShadow(BlurType.GAUSSIAN, Color.rgb(0, 0, 0, 0.55), 3, 0, 0, 1));
        // Un Path trae un trazo negro de fabrica (un Polygon no): fuera.
        groove.setStroke(null);
        panel.setStroke(null);
        for (final Text t : new Text[] {pow, slash, tou}) {
            t.setTextOrigin(VPos.BASELINE);
            t.setManaged(false);
        }
        rim.setManaged(false);
        groove.setManaged(false);
        panel.setManaged(false);
        getChildren().addAll(rim, groove, panel, pow, slash, tou);
        setMouseTransparent(true);
        applyFont();
        setColours(PtBoxStyle.panel(0), false);
    }

    /** El tamanyo de la letra de las cifras. */
    public void setFontSize(final double size) {
        if (Math.abs(size - fontSize) < 0.01) {
            return;
        }
        fontSize = size;
        applyFont();
        requestLayout();
    }

    /**
     * Las cifras y su color: negro, o verde/rojo si estan por encima/debajo de
     * lo impreso. Con {@code lethal} la caja entera va en rojo y las cifras en
     * blanco: el danyo ya la mata.
     */
    public void setValues(final int power, final int toughness, final int powerArgb, final int toughnessArgb,
                          final int cardPanelArgb, final boolean lethal) {
        final String p = Integer.toString(power);
        final String t = Integer.toString(toughness);
        final boolean resize = !p.equals(pow.getText()) || !t.equals(tou.getText());
        pow.setText(p);
        tou.setText(t);
        pow.setFill(fx(lethal ? PtBoxStyle.LETHAL_INK : powerArgb));
        tou.setFill(fx(lethal ? PtBoxStyle.LETHAL_INK : toughnessArgb));
        slash.setFill(fx(lethal ? PtBoxStyle.LETHAL_INK : PtBoxStyle.INK));
        setColours(lethal ? PtBoxStyle.LETHAL_PANEL : cardPanelArgb, lethal);
        if (resize) {
            requestLayout();
        }
    }

    private void applyFont() {
        final Font f = Font.font(FAMILY, FontWeight.BOLD, fontSize);
        pow.setFont(f);
        slash.setFont(f);
        tou.setFont(f);
    }

    private void setColours(final int panelColour, final boolean lethal) {
        if (panelColour == panelArgb) {
            return;
        }
        panelArgb = panelColour;
        rim.setFill(gradient(PtBoxStyle.RIM_AT, PtBoxStyle.rim(panelColour)));
        groove.setFill(gradient(PtBoxStyle.GROOVE_AT, PtBoxStyle.groove(panelColour)));
        panel.setFill(gradient(PtBoxStyle.PANEL_AT, PtBoxStyle.panelFill(panelColour)));
    }

    private static LinearGradient gradient(final float[] at, final int[] colours) {
        final Stop[] stops = new Stop[at.length];
        for (int i = 0; i < at.length; i++) {
            stops[i] = new Stop(at[i], fx(colours[i]));
        }
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, stops);
    }

    private static Color fx(final int argb) {
        return Color.rgb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, ((argb >>> 24) & 0xFF) / 255.0);
    }

    private double boxHeight() {
        return Math.round(fontSize * PtBoxStyle.HEIGHT_PER_FONT);
    }

    private double textWidth() {
        return pow.getLayoutBounds().getWidth() + slash.getLayoutBounds().getWidth()
                + tou.getLayoutBounds().getWidth();
    }

    @Override
    protected double computePrefHeight(final double width) {
        return boxHeight();
    }

    @Override
    protected double computePrefWidth(final double height) {
        final double h = boxHeight();
        return Math.ceil(Math.max(h * PtBoxStyle.MIN_WIDTH, textWidth() + h * PtBoxStyle.SIDE_ROOM));
    }

    @Override
    protected double computeMinWidth(final double height) {
        return computePrefWidth(height);
    }

    @Override
    protected double computeMinHeight(final double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computeMaxWidth(final double height) {
        return computePrefWidth(height);
    }

    @Override
    protected double computeMaxHeight(final double width) {
        return computePrefHeight(width);
    }

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        rim.setStrokeWidth(Math.max(0.8, h * 0.025));
        set(rim, 0, 0, w, h, h * PtBoxStyle.CHAMFER);
        final float[] g = PtBoxStyle.GROOVE;
        set(groove, h * g[0], h * g[1], w - h * g[2], h - h * g[3], h * (PtBoxStyle.CHAMFER - 0.06));
        final float[] p = PtBoxStyle.PANEL;
        final double pl = h * p[0];
        final double pr = w - h * p[2];
        set(panel, pl, h * p[1], pr, h - h * p[3], h * (PtBoxStyle.CHAMFER - 0.08));

        // Las cifras: centradas en el panel, por la altura de las mayusculas.
        final double baseline = h * PtBoxStyle.TEXT_CENTER_Y + fontSize * CAP / 2;
        double x = (pl + pr) / 2 - textWidth() / 2;
        for (final Text t : new Text[] {pow, slash, tou}) {
            t.setLayoutX(x - t.getLayoutBounds().getMinX());
            t.setLayoutY(baseline);
            x += t.getLayoutBounds().getWidth();
        }
    }

    private void set(final Path path, final double x0, final double y0, final double x1, final double y1,
                     final double chamfer) {
        final double[] c = PtBoxStyle.rounded(PtBoxStyle.chamfered(x0, y0, x1, y1, chamfer),
                getHeight() * PtBoxStyle.ROUND);
        final int n = c.length / 6;
        final PathElement[] els = new PathElement[n * 2 + 2];
        els[0] = new MoveTo(c[0], c[1]);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                els[i * 2] = new LineTo(c[i * 6], c[i * 6 + 1]);
            } else {
                els[0] = new MoveTo(c[0], c[1]);
            }
            els[i * 2 + 1] = new QuadCurveTo(c[i * 6 + 2], c[i * 6 + 3], c[i * 6 + 4], c[i * 6 + 5]);
        }
        els[n * 2] = new LineTo(c[0], c[1]);
        els[n * 2 + 1] = new ClosePath();
        path.getElements().setAll(els);
    }
}
