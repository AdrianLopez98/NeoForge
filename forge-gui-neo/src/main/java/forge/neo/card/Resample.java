package forge.neo.card;

/**
 * Reducir una imagen con un filtro Lanczos de 3 lobulos.
 *
 * <p>Reportado en r/forgeMTG el 14-09-2026: <i>"the cards are unreadable... I'm
 * not sure which algorithm are you using to shrink the image... I recommend
 * using Bicubic or Lanczos"</i>. Y tenia razon. La carta de Scryfall mide
 * 488x680 y en la mesa se pinta a 100-250 pixeles de ancho; hasta aqui eso lo
 * hacia el {@code ImageView} de JavaFX <b>al pintar</b>, y su filtro (bilineal)
 * solo mira los cuatro pixeles de alrededor: reduciendo a la mitad o menos se
 * salta pixeles del original, el texto se come y los bordes salen en dientes de
 * sierra — peor aun con la mano girada.
 *
 * <p><b>Por que Lanczos y no un promedio de area.</b> Se probo primero el
 * promedio (un filtro de caja): quitaba los dientes, pero medido en la pantalla
 * real (4K, cartas de ~240 px) dejaba el texto mas blando que antes, porque la
 * caja emborrona justo en las reducciones moderadas. Lanczos-3 es el estandar
 * para reducir conservando nitidez; con el soporte ensanchado por la proporcion
 * de reduccion, tampoco deja pixeles sin mirar.
 *
 * <p>Se hace UNA vez, en segundo plano, y al tamano en pixeles de la pantalla:
 * girar despues una imagen que ya esta a su tamano es un remuestreo casi 1:1,
 * que es donde el bilineal de JavaFX si va bien ("rotate first and then shrink"
 * pedia el jugador; esto es encoger bien primero y girar sin encoger).
 *
 * <p>Java puro, sin JavaFX: asi se puede comprobar sin ventana
 * ({@code run.cmd imagecheck}).
 */
public final class Resample {

    private Resample() {
    }

    /** Lobulos del filtro. 3 es el Lanczos de siempre (el de las herramientas de imagen). */
    private static final int LOBES = 3;

    /**
     * Reduce {@code src} (ARGB, fila a fila) de {@code sw x sh} a {@code dw x dh}.
     *
     * <p>Se filtra en <b>premultiplicado</b>: si no, un pixel transparente (que en
     * ARGB puede traer cualquier color guardado) destine a sus vecinos, y las
     * esquinas redondeadas de una carta salen con cerco. Y se recorta cada canal
     * a 0-255: Lanczos tiene pesos negativos, y en un borde muy marcado la suma
     * se puede pasar un poco por arriba o por abajo.
     */
    public static int[] downscale(final int[] src, final int sw, final int sh,
                                  final int dw, final int dh) {
        if (sw <= 0 || sh <= 0 || dw <= 0 || dh <= 0 || src == null || src.length < sw * sh) {
            throw new IllegalArgumentException("tamanos no validos: " + sw + "x" + sh + " -> " + dw + "x" + dh);
        }
        final Axis ax = axis(sw, dw);
        final Axis ay = axis(sh, dh);

        // --- pasada horizontal: sh filas, dw columnas, premultiplicado ---
        final double[] rows = new double[dw * sh * 4];
        for (int y = 0; y < sh; y++) {
            final int base = y * sw;
            for (int dx = 0; dx < dw; dx++) {
                double a = 0;
                double r = 0;
                double g = 0;
                double b = 0;
                for (int k = ax.start[dx]; k < ax.start[dx + 1]; k++) {
                    final int p = src[base + ax.index[k]];
                    final double wt = ax.weight[k];
                    final int pa = (p >>> 24) & 0xFF;
                    final double pm = wt * pa / 255.0;
                    a += wt * pa;
                    r += pm * ((p >> 16) & 0xFF);
                    g += pm * ((p >> 8) & 0xFF);
                    b += pm * (p & 0xFF);
                }
                final int o = (y * dw + dx) * 4;
                rows[o] = a;
                rows[o + 1] = r;
                rows[o + 2] = g;
                rows[o + 3] = b;
            }
        }

        // --- pasada vertical: dh filas, dw columnas ---
        final int[] out = new int[dw * dh];
        for (int dy = 0; dy < dh; dy++) {
            final int from = ay.start[dy];
            final int to = ay.start[dy + 1];
            for (int dx = 0; dx < dw; dx++) {
                double a = 0;
                double r = 0;
                double g = 0;
                double b = 0;
                for (int k = from; k < to; k++) {
                    final int o = (ay.index[k] * dw + dx) * 4;
                    final double wt = ay.weight[k];
                    a += wt * rows[o];
                    r += wt * rows[o + 1];
                    g += wt * rows[o + 2];
                    b += wt * rows[o + 3];
                }
                final int ca = clamp(a);
                int cr = 0;
                int cg = 0;
                int cb = 0;
                if (a > 1e-9) {
                    // Deshacer el premultiplicado.
                    final double k255 = 255.0 / a;
                    cr = clamp(r * k255);
                    cg = clamp(g * k255);
                    cb = clamp(b * k255);
                }
                out[dy * dw + dx] = (ca << 24) | (cr << 16) | (cg << 8) | cb;
            }
        }
        return out;
    }

    private static int clamp(final double v) {
        final long n = Math.round(v);
        return n < 0 ? 0 : n > 255 ? 255 : (int) n;
    }

    private static double lanczos(final double x) {
        final double ax = Math.abs(x);
        if (ax < 1e-9) {
            return 1;
        }
        if (ax >= LOBES) {
            return 0;
        }
        final double px = Math.PI * x;
        return LOBES * Math.sin(px) * Math.sin(px / LOBES) / (px * px);
    }

    /**
     * Que pixeles del original entran en cada pixel nuevo de un eje, y con que
     * peso. Los pesos de cada pixel nuevo suman 1.
     */
    private static final class Axis {
        /** Donde empiezan los aportes del pixel {@code d}; {@code start[d+1]} es el final. */
        final int[] start;
        final int[] index;
        final double[] weight;

        Axis(final int[] start, final int[] index, final double[] weight) {
            this.start = start;
            this.index = index;
            this.weight = weight;
        }
    }

    private static Axis axis(final int sn, final int dn) {
        final double scale = (double) sn / dn;
        // Al reducir, el filtro se ensancha en la misma proporcion: si no, se
        // saltaria pixeles del original igual que el bilineal.
        final double filter = Math.max(1.0, scale);
        final double support = LOBES * filter;
        final int per = (int) Math.ceil(support * 2) + 2;
        final int[] start = new int[dn + 1];
        final int[] index = new int[dn * per];
        final double[] weight = new double[dn * per];
        int n = 0;
        for (int d = 0; d < dn; d++) {
            start[d] = n;
            final double center = (d + 0.5) * scale - 0.5;
            final int s0 = (int) Math.floor(center - support);
            final int s1 = (int) Math.ceil(center + support);
            double sum = 0;
            final int first = n;
            for (int s = s0; s <= s1; s++) {
                final double wt = lanczos((s - center) / filter);
                if (wt == 0) {
                    continue;
                }
                // Borde: se repite el ultimo pixel en vez de mirar fuera.
                index[n] = Math.max(0, Math.min(sn - 1, s));
                weight[n] = wt;
                sum += wt;
                n++;
            }
            if (Math.abs(sum) > 1e-12) {
                for (int k = first; k < n; k++) {
                    weight[k] /= sum;
                }
            }
        }
        start[dn] = n;
        return new Axis(start, index, weight);
    }
}
