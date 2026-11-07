package forge.neo.card;

import forge.card.ColorSet;

/**
 * <b>La caja de fuerza/resistencia, como la de una carta impresa</b>
 * (Discord, 07-11-2026, con maqueta: <i>"what if cards had a card-like
 * background for it's power/toughness?"</i>).
 *
 * <p>Medida sobre la maqueta pixel a pixel, de arriba abajo y en fracciones
 * del alto de la caja: filo claro y marco (~10 %), una RANURA oscura (~7 %),
 * el panel liso (~65 %), un labio claro (~9 %) y el canto de abajo oscuro
 * (~8 %). En horizontal, la ranura se ve fuerte a la derecha y apenas a la
 * izquierda. Esquinas en chaflan. El panel, del color de la carta en tono
 * palido; todo lo demas sale de mezclar ese tono con blanco o con negro.
 *
 * <p>Java puro: lo pintan igual el escritorio ({@link PtBox}, JavaFX) y
 * Android (Compose), para que las dos se vean igual.
 */
public final class PtBoxStyle {

    private PtBoxStyle() {
    }

    /**
     * Ajuste: esta caja, como la de las cartas de Magic, en vez de la
     * pastilla negra con cifras blancas. APAGADO de fabrica (Ana,
     * 07-11-2026): la negra se lee mejor, y el que quiera, que la active.
     */
    public static final String CARD_STYLE = "ptCardStyle";

    // --- geometria, en fracciones del ALTO de la caja ---

    /** Alto de la caja por cada punto de letra. */
    public static final float HEIGHT_PER_FONT = 1.42f;
    /** Ancho minimo (un 1/1 no es una caja estrecha: en la carta tampoco). */
    public static final float MIN_WIDTH = 2.15f;
    /** Lo que se deja a los lados de las cifras, sumando los dos. */
    public static final float SIDE_ROOM = 0.95f;
    /** El chaflan de las esquinas de fuera. */
    public static final float CHAMFER = 0.19f;
    /** Lo que se redondea cada vertice del chaflan (en la carta no hay picos). */
    public static final float ROUND = 0.07f;

    /** La ranura: izquierda, arriba, derecha, abajo. */
    public static final float[] GROOVE = {0.118f, 0.10f, 0.09f, 0.185f};
    /** El panel: izquierda, arriba, derecha, abajo. */
    public static final float[] PANEL = {0.125f, 0.175f, 0.155f, 0.165f};
    /** Centro vertical del panel, donde va el centro de las cifras. */
    public static final float TEXT_CENTER_Y = (PANEL[1] + 1f - PANEL[3]) / 2f;

    // --- color ---

    public static final int INK = 0xFF101010;
    public static final int UP = 0xFF0E7A38;
    public static final int DOWN = 0xFFB3221C;
    public static final int LETHAL_PANEL = 0xFFCF443E;
    public static final int LETHAL_INK = 0xFFFFFFFF;

    /** El panel del color de la carta (mascara del motor), tono palido. */
    public static int panel(final int mask) {
        return panel(ColorSet.fromMask(mask));
    }

    /** El panel del color de la carta, tono palido (los de la maqueta). */
    public static int panel(final ColorSet c) {
        if (c == null || c.isColorless()) {
            return 0xFF9A9A9A;
        }
        if (c.isMulticolor()) {
            return 0xFFADA27A;
        }
        if (c.hasWhite()) {
            return 0xFFD0C9B0;
        }
        if (c.hasBlue()) {
            return 0xFF92AEC6;
        }
        if (c.hasBlack()) {
            return 0xFF9C9499;
        }
        if (c.hasRed()) {
            return 0xFFCAAAA9;
        }
        return 0xFF86A188;
    }

    /** Las paradas del marco (de arriba abajo). */
    public static final float[] RIM_AT = {0f, 0.05f, 0.10f, 0.20f, 0.80f, 0.835f, 0.89f, 0.92f, 1f};
    /** Cuanto se mezcla con blanco (positivo) o con negro (negativo) en cada parada. */
    private static final float[] RIM_MIX = {0.50f, 0.38f, 0.22f, 0.12f, 0.12f, 0.34f, 0.36f, -0.30f, -0.62f};

    public static final float[] GROOVE_AT = {0f, 1f};
    private static final float[] GROOVE_MIX = {-0.66f, -0.50f};

    public static final float[] PANEL_AT = {0f, 0.06f, 0.6f, 1f};
    private static final float[] PANEL_MIX = {0.28f, 0f, 0f, -0.05f};

    public static int[] rim(final int panel) {
        return mixAll(panel, RIM_MIX);
    }

    public static int[] groove(final int panel) {
        return mixAll(panel, GROOVE_MIX);
    }

    public static int[] panelFill(final int panel) {
        return mixAll(panel, PANEL_MIX);
    }

    private static int[] mixAll(final int base, final float[] mix) {
        final int[] out = new int[mix.length];
        for (int i = 0; i < mix.length; i++) {
            out[i] = mix[i] >= 0 ? mix(base, 0xFFFFFFFF, mix[i]) : mix(base, 0xFF000000, -mix[i]);
        }
        return out;
    }

    /** Mezcla dos colores ARGB opacos: 0 = el primero, 1 = el segundo. */
    public static int mix(final int a, final int b, final float f) {
        final int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        final int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        final int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /**
     * Las esquinas de un poligono, redondeadas: por cada vertice, seis
     * numeros {@code ax, ay, vx, vy, bx, by}: se va en recta hasta (a) y de
     * ahi en curva, con (v) de control, hasta (b). Asi lo pintan igual una
     * {@code Path} de JavaFX y una de Compose.
     */
    public static double[] rounded(final double[] poly, final double r) {
        final int n = poly.length / 2;
        final double[] out = new double[n * 6];
        for (int i = 0; i < n; i++) {
            final double vx = poly[i * 2];
            final double vy = poly[i * 2 + 1];
            final int pi = (i + n - 1) % n;
            final int ni = (i + 1) % n;
            final double[] a = toward(vx, vy, poly[pi * 2], poly[pi * 2 + 1], r);
            final double[] b = toward(vx, vy, poly[ni * 2], poly[ni * 2 + 1], r);
            out[i * 6] = a[0];
            out[i * 6 + 1] = a[1];
            out[i * 6 + 2] = vx;
            out[i * 6 + 3] = vy;
            out[i * 6 + 4] = b[0];
            out[i * 6 + 5] = b[1];
        }
        return out;
    }

    /** El punto a {@code r} de (x, y) hacia (tx, ty), sin pasar de la mitad del lado. */
    private static double[] toward(final double x, final double y, final double tx, final double ty,
                                   final double r) {
        final double dx = tx - x;
        final double dy = ty - y;
        final double len = Math.hypot(dx, dy);
        if (len < 1e-6) {
            return new double[] {x, y};
        }
        final double d = Math.min(r, len / 2) / len;
        return new double[] {x + dx * d, y + dy * d};
    }

    /**
     * Los vertices de un rectangulo con las esquinas en chaflan, en el orden
     * x0,y0,x1,y1... Lo usan las dos interfaces para las tres capas.
     */
    public static double[] chamfered(final double x0, final double y0, final double x1, final double y1,
                                     final double c) {
        final double k = Math.max(0, Math.min(c, Math.min((x1 - x0) / 2, (y1 - y0) / 2)));
        return new double[] {
            x0 + k, y0, x1 - k, y0, x1, y0 + k, x1, y1 - k,
            x1 - k, y1, x0 + k, y1, x0, y1 - k, x0, y0 + k,
        };
    }
}
