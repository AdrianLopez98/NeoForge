package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.HLineTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.QuadCurveTo;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.VLineTo;

/**
 * <b>El dibujo de cada modo en el menu</b> (Discord, 09-10-2026: <i>"what about
 * mixing the layout we have in android to PC as well? The android layout seems
 * vivid while the PC one feels like an old version of the same program"</i>).
 *
 * <p>Es el {@code ModoIlustraciones.kt} de Android pasado a un {@code Canvas} de
 * JavaFX, trazo por trazo: una corona para Commander, una montanya con su camino
 * para Ascenso, un castillo para la Quest... Mismo criterio: <b>se reconocen por
 * la silueta</b>, con relleno translucido del color del modo y contorno a plena
 * tinta, para que el dibujo viva detras del texto sin competir con el. Y tres
 * que Android no tiene porque alli no hay casilla: el birrete del tutorial, el
 * cuadro del torneo y la paleta de Personalizar.
 *
 * <p>Todo se dibuja en un cuadrado de {@code side} x {@code side} centrado en
 * ({@code cx}, {@code cy}). Los angulos de los arcos van al reves que en Compose
 * (alli crecen en el sentido del reloj; aqui en el contrario): por eso los
 * arcos llevan el signo cambiado respecto al original.
 */
public final class ModeArt {

    private ModeArt() {
    }

    /** Los colores de Magic tal como los pinta Android ({@code Mana}). */
    static final Color W = Color.web("#F3E4BE");
    static final Color U = Color.web("#6FA9DE");
    static final Color B = Color.web("#9A88A8");
    static final Color R = Color.web("#E08A72");
    static final Color G = Color.web("#74BC8E");
    static final Color C = Color.web("#98A5B6");
    static final Color GOLD = Color.web("#D7B057");

    /**
     * Cada casilla del menu: sus colores (los halos y los puntos), el color del
     * dibujo y el dibujo. Los colores son los de Android para los mismos modos.
     */
    public enum Mode {
        TUTORIAL(U, new Color[] {W, U}),
        COMMANDER(GOLD, new Color[] {B, G}),
        STANDARD(U, new Color[] {U}),
        QUEST(W, new Color[] {W, G}),
        TOURNAMENT(GOLD, new Color[] {W, R}),
        ASCENT(R, new Color[] {B, R}),
        ADVENTURE(G, new Color[] {R, G}),
        OTHER_FORMATS(C, new Color[] {W, B}),
        DRAFT(G, new Color[] {R, G}),
        SEALED(R, new Color[] {R}),
        BRAWL(W, new Color[] {W, U}),
        OATHBREAKER(R, new Color[] {U, R}),
        ONLINE(U, new Color[] {U, R}),
        PUZZLES(U, new Color[] {U, B}),
        LIBRARY(U, new Color[] {W, U}),
        LOOK(GOLD, new Color[] {U, G}),
        ACHIEVEMENTS(GOLD, new Color[] {W, B});

        private final Color ink;
        private final Color[] colors;

        Mode(final Color ink, final Color[] colors) {
            this.ink = ink;
            this.colors = colors;
        }

        /** El color del dibujo. */
        public Color ink() {
            return ink;
        }

        /** Sus colores, en orden WUBRG: un halo y un punto por cada uno. */
        public List<Color> colors() {
            return List.of(colors);
        }

        /** El del texto y el borde: el suyo si es de un color, oro si de varios. */
        public Color tint() {
            return colors.length == 1 ? colors[0] : GOLD;
        }
    }

    /**
     * Dibuja el modo.
     *
     * @param ink      el color del trazo
     * @param bg       el del fondo de la tarjeta: para los huecos (puerta, ventanas)
     * @param strength 1 encendido, menos para un modo que no se puede jugar
     */
    public static void draw(final GraphicsContext g, final Mode mode, final double cx, final double cy,
                            final double side, final Color ink, final Color bg, final double strength) {
        final Pen t = new Pen(g, side, ink, bg, strength);
        g.save();
        g.translate(cx, cy);
        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);
        switch (mode) {
            case TUTORIAL -> cap(t);
            case COMMANDER -> crown(t);
            case STANDARD -> swords(t);
            case QUEST -> castle(t);
            case TOURNAMENT -> bracket(t);
            case ASCENT -> mountain(t);
            case ADVENTURE -> compass(t);
            case OTHER_FORMATS -> decks(t);
            case DRAFT -> fan(t);
            case SEALED -> pack(t);
            case BRAWL -> shield(t);
            case OATHBREAKER -> staff(t);
            case ONLINE -> network(t);
            case PUZZLES -> piece(t);
            case LIBRARY -> book(t);
            case LOOK -> palette(t);
            case ACHIEVEMENTS -> trophy(t);
            default -> {
            }
        }
        g.restore();
    }

    /** Lo que comparten todos los dibujos: tamanyo, colores y grosor. */
    private static final class Pen {
        final GraphicsContext g;
        final double s;
        final Color ink;
        final Color bg;
        final double f;
        final double line;
        final double thin;
        final Color outline;

        Pen(final GraphicsContext g, final double s, final Color ink, final Color bg, final double f) {
            this.g = g;
            this.s = s;
            this.ink = ink;
            this.bg = bg;
            this.f = f;
            this.line = s * 0.028;
            this.thin = s * 0.016;
            this.outline = alpha(ink, 0.95 * f);
        }

        /** De arriba abajo, mas denso arriba: da volumen sin sombrear nada. */
        Paint fill() {
            return new LinearGradient(0, -s * 0.5, 0, s * 0.5, false, CycleMethod.NO_CYCLE,
                    new Stop(0, alpha(ink, 0.42 * f)), new Stop(1, alpha(ink, 0.10 * f)));
        }

        Color outline(final double a) {
            return alpha(ink, a * f);
        }

        Color hole(final double a) {
            return alpha(bg, a);
        }
    }

    static Color alpha(final Color c, final double a) {
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(1, a)));
    }

    // ------------------------------------------------------------------
    //  Ayudas
    // ------------------------------------------------------------------

    /** Un poligono cerrado con las coordenadas en fracciones del lado. */
    private static void poly(final Pen t, final double... xy) {
        t.g.beginPath();
        t.g.moveTo(xy[0] * t.s, xy[1] * t.s);
        for (int i = 2; i < xy.length; i += 2) {
            t.g.lineTo(xy[i] * t.s, xy[i + 1] * t.s);
        }
        t.g.closePath();
    }

    /** Rellena con el degradado y perfila el camino actual. */
    private static void shape(final Pen t) {
        t.g.setFill(t.fill());
        t.g.fill();
        t.g.setStroke(t.outline);
        t.g.setLineWidth(t.line);
        t.g.stroke();
    }

    private static void fillPath(final Pen t, final Paint p) {
        t.g.setFill(p);
        t.g.fill();
    }

    private static void strokePath(final Pen t, final Paint p, final double width) {
        t.g.setStroke(p);
        t.g.setLineWidth(width);
        t.g.stroke();
    }

    private static void dot(final Pen t, final Paint p, final double r, final double x, final double y) {
        t.g.setFill(p);
        t.g.fillOval(x * t.s - r, y * t.s - r, r * 2, r * 2);
    }

    private static void ring(final Pen t, final Paint p, final double r, final double x, final double y,
                             final double width) {
        t.g.setStroke(p);
        t.g.setLineWidth(width);
        t.g.strokeOval(x * t.s - r, y * t.s - r, r * 2, r * 2);
    }

    private static void line(final Pen t, final Paint p, final double x0, final double y0, final double x1,
                             final double y1, final double width) {
        t.g.setStroke(p);
        t.g.setLineWidth(width);
        t.g.strokeLine(x0 * t.s, y0 * t.s, x1 * t.s, y1 * t.s);
    }

    /** Un rectangulo de esquinas redondas, relleno y perfilado. */
    private static void roundRect(final Pen t, final double x0, final double y0, final double x1, final double y1,
                                  final double radius) {
        final double arc = radius * 2 * t.s;
        t.g.setFill(t.fill());
        t.g.fillRoundRect(x0 * t.s, y0 * t.s, (x1 - x0) * t.s, (y1 - y0) * t.s, arc, arc);
        t.g.setStroke(t.outline);
        t.g.setLineWidth(t.line);
        t.g.strokeRoundRect(x0 * t.s, y0 * t.s, (x1 - x0) * t.s, (y1 - y0) * t.s, arc, arc);
    }

    /** Gira lo que pinte {@code body} alrededor de ({@code px}, {@code py}), en fracciones. */
    private static void rotated(final Pen t, final double degrees, final double px, final double py,
                                final Runnable body) {
        t.g.save();
        t.g.translate(px * t.s, py * t.s);
        t.g.rotate(degrees);
        t.g.translate(-px * t.s, -py * t.s);
        body.run();
        t.g.restore();
    }

    /** La chispa de cuatro puntas. */
    private static void sparkle(final Pen t, final double cx, final double cy, final double r) {
        final double k = r * 0.22;
        poly(t, cx, cy - r, cx + k, cy - k, cx + r, cy, cx + k, cy + k,
                cx, cy + r, cx - k, cy + k, cx - r, cy, cx - k, cy - k);
        fillPath(t, t.outline);
    }

    /** Una carta de pie, para el abanico, los mazos y la red. */
    private static void card(final Pen t, final double cx, final double cy, final double w, final double h) {
        final double arc = 0.035 * 2 * t.s;
        t.g.setFill(t.hole(0.92));
        t.g.fillRoundRect((cx - w / 2) * t.s, (cy - h / 2) * t.s, w * t.s, h * t.s, arc, arc);
        roundRect(t, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 0.035);
        // El recuadro del dibujo: sin el es un rectangulo, con el es una carta.
        t.g.setStroke(t.outline(0.45));
        t.g.setLineWidth(t.thin);
        t.g.strokeRect((cx - w / 2 + 0.04) * t.s, (cy - h / 2 + 0.07) * t.s, (w - 0.08) * t.s, t.s * h * 0.42);
    }

    /**
     * Un arco "como en Compose": desde {@code start} grados barriendo {@code sweep}
     * en el sentido del reloj. Aqui los angulos crecen al reves, de ahi el signo.
     */
    private static void arc(final Pen t, final double cx, final double cy, final double r,
                            final double start, final double sweep, final boolean moveFirst) {
        if (moveFirst) {
            final double a = Math.toRadians(start);
            t.g.moveTo((cx + r * Math.cos(a)) * t.s, (cy + r * Math.sin(a)) * t.s);
        }
        t.g.arc(cx * t.s, cy * t.s, r * t.s, r * t.s, -start, -sweep);
    }

    // ------------------------------------------------------------------
    //  Los dibujos
    // ------------------------------------------------------------------

    /** Commander: la corona. El comandante es el rey de su mazo. */
    private static void crown(final Pen t) {
        poly(t, -0.38, 0.16, -0.44, -0.22, -0.20, 0.00, 0, -0.34, 0.20, 0.00, 0.44, -0.22, 0.38, 0.16);
        shape(t);
        roundRect(t, -0.40, 0.18, 0.40, 0.34, 0.03);
        for (final double[] p : new double[][] {{-0.44, -0.27}, {0, -0.40}, {0.44, -0.27}}) {
            dot(t, t.outline, t.s * 0.05, p[0], p[1]);
        }
        // Las joyas de la banda: el hueco es lo que hace que se lean como piedras.
        for (final double x : new double[] {-0.22, 0, 0.22}) {
            dot(t, t.hole(0.9), t.s * 0.045, x, 0.26);
            ring(t, t.outline, t.s * 0.045, x, 0.26, t.thin);
        }
    }

    /** Ascenso: la montanya, con el camino punteado hasta la bandera. */
    private static void mountain(final Pen t) {
        poly(t, 0.02, 0.38, 0.28, -0.02, 0.52, 0.38);
        shape(t);
        poly(t, -0.48, 0.38, -0.04, -0.30, 0.40, 0.38);
        shape(t);
        // La nieve de la cima.
        poly(t, -0.04, -0.30, -0.15, -0.13, -0.07, -0.17, 0.0, -0.11, 0.07, -0.16);
        fillPath(t, t.outline(0.55));
        // El camino: una fila de puntos, como en el mapa.
        final double[][] trail = {{-0.32, 0.34}, {-0.14, 0.24}, {-0.26, 0.12}, {-0.08, 0.02},
                {-0.16, -0.08}, {-0.04, -0.20}};
        for (int i = 0; i < trail.length - 1; i++) {
            for (int k = 0; k < 3; k++) {
                final double fr = k / 3.0;
                dot(t, t.hole(0.85), t.s * 0.018, trail[i][0] + (trail[i + 1][0] - trail[i][0]) * fr,
                        trail[i][1] + (trail[i + 1][1] - trail[i][1]) * fr);
            }
        }
        // La bandera en la cima.
        line(t, t.outline, -0.04, -0.30, -0.04, -0.50, t.s * 0.022);
        poly(t, -0.04, -0.50, 0.14, -0.45, -0.04, -0.39);
        fillPath(t, t.outline);
    }

    /** Quest: el castillo, con su almena y su puerta. */
    private static void castle(final Pen t) {
        poly(t, -0.40, 0.38, -0.40, -0.06, -0.32, -0.06, -0.32, 0.00, -0.24, 0.00,
                -0.24, -0.06, -0.16, -0.06, -0.16, 0.00, 0.16, 0.00, 0.16, -0.06,
                0.24, -0.06, 0.24, 0.00, 0.32, 0.00, 0.32, -0.06, 0.40, -0.06, 0.40, 0.38);
        shape(t);
        poly(t, -0.14, 0.00, -0.14, -0.30, -0.08, -0.30, -0.08, -0.24, -0.03, -0.24,
                -0.03, -0.30, 0.03, -0.30, 0.03, -0.24, 0.08, -0.24, 0.08, -0.30, 0.14, -0.30, 0.14, 0.00);
        shape(t);
        // El estandarte.
        line(t, t.outline, 0, -0.30, 0, -0.48, t.s * 0.02);
        poly(t, 0, -0.48, 0.17, -0.44, 0, -0.39);
        fillPath(t, t.outline);
        // La puerta en arco: el hueco del color de la tarjeta.
        t.g.beginPath();
        t.g.moveTo(-0.10 * t.s, 0.38 * t.s);
        t.g.lineTo(-0.10 * t.s, 0.20 * t.s);
        arc(t, 0, 0.20, 0.10, 180, 180, false);
        t.g.lineTo(0.10 * t.s, 0.38 * t.s);
        t.g.closePath();
        fillPath(t, t.hole(0.9));
        strokePath(t, t.outline, t.thin);
        // Dos ventanas.
        t.g.setFill(t.hole(0.9));
        for (final double x : new double[] {-0.27, 0.27}) {
            t.g.fillRect((x - 0.03) * t.s, 0.10 * t.s, t.s * 0.06, t.s * 0.10);
        }
    }

    /** Aventura: la brujula. Es el modo del mapa del mundo. */
    private static void compass(final Pen t) {
        final double r = t.s * 0.42;
        t.g.setFill(t.fill());
        t.g.fillOval(-r, -r, r * 2, r * 2);
        ring(t, t.outline, r, 0, 0, t.line);
        ring(t, t.outline(0.5), t.s * 0.34, 0, 0, t.thin);
        // Las marcas de los rumbos.
        for (int i = 0; i < 16; i++) {
            final double a = i / 16.0 * 2 * Math.PI;
            final double len = i % 4 == 0 ? 0.07 : i % 2 == 0 ? 0.045 : 0.025;
            line(t, t.outline, Math.cos(a) * 0.42, Math.sin(a) * 0.42,
                    Math.cos(a) * (0.42 - len), Math.sin(a) * (0.42 - len), t.s * 0.012);
        }
        // La rosa: el norte relleno, el resto en contorno.
        poly(t, 0, -0.30, 0.06, 0, 0, 0.30, -0.06, 0);
        shape(t);
        poly(t, -0.22, 0, 0, -0.045, 0.22, 0, 0, 0.045);
        shape(t);
        poly(t, 0, -0.30, 0.06, 0, -0.06, 0);
        fillPath(t, t.outline);
        dot(t, t.bg, t.s * 0.03, 0, 0);
    }

    /** Draft: tres cartas en abanico, que es como se pasan los sobres. */
    private static void fan(final Pen t) {
        for (final double deg : new double[] {-22, 0, 22}) {
            rotated(t, deg, 0, 0.45, () -> card(t, 0, -0.05, 0.34, 0.50));
        }
        // La flecha de pasar, por encima.
        t.g.beginPath();
        t.g.moveTo(-0.30 * t.s, -0.40 * t.s);
        t.g.quadraticCurveTo(0, -0.56 * t.s, 0.30 * t.s, -0.40 * t.s);
        strokePath(t, t.outline, t.thin);
        poly(t, 0.33, -0.38, 0.22, -0.37, 0.28, -0.46);
        fillPath(t, t.outline);
    }

    /** Sellado: el sobre cerrado, con el dentado arriba y abajo. */
    private static void pack(final Pen t) {
        final int teeth = 6;
        t.g.beginPath();
        t.g.moveTo(-0.26 * t.s, -0.38 * t.s);
        for (int i = 0; i <= teeth; i++) {
            final double x = -0.26 + 0.52 * i / teeth;
            t.g.lineTo(x * t.s, (i % 2 == 0 ? -0.38 : -0.43) * t.s);
        }
        t.g.lineTo(0.26 * t.s, 0.38 * t.s);
        for (int i = teeth; i >= 0; i--) {
            final double x = -0.26 + 0.52 * i / teeth;
            t.g.lineTo(x * t.s, (i % 2 == 0 ? 0.38 : 0.43) * t.s);
        }
        t.g.closePath();
        shape(t);
        // Las dos costuras del precinto.
        for (final double y : new double[] {-0.30, 0.30}) {
            line(t, t.outline(0.55), -0.26, y, 0.26, y, t.s * 0.012);
        }
        // El destello: lo que haya dentro, todavia sin abrir.
        sparkle(t, 0, 0, 0.16);
        sparkle(t, 0.13, -0.16, 0.06);
        sparkle(t, -0.12, 0.15, 0.05);
    }

    /**
     * Puzzles: la pieza, con dos salientes y un entrante. El {@code Canvas} no
     * sabe unir ni restar figuras, asi que se hace con las de JavaFX y se copia
     * su contorno.
     */
    private static void piece(final Pen t) {
        final double s = t.s;
        final javafx.scene.shape.Rectangle body =
                new javafx.scene.shape.Rectangle(-0.30 * s, -0.22 * s, 0.56 * s, 0.56 * s);
        body.setArcWidth(0.06 * s);
        body.setArcHeight(0.06 * s);
        Shape out = Shape.union(body, new javafx.scene.shape.Circle(-0.02 * s, -0.30 * s, 0.11 * s));
        out = Shape.union(out, new javafx.scene.shape.Circle(0.34 * s, 0.06 * s, 0.11 * s));
        out = Shape.subtract(out, new javafx.scene.shape.Circle(-0.30 * s, 0.06 * s, 0.10 * s));
        final Shape result = out;
        rotated(t, -8, 0, 0, () -> {
            replay(t, result);
            shape(t);
        });
    }

    /** Copia al lienzo el contorno de una figura de JavaFX. */
    private static void replay(final Pen t, final Shape shape) {
        t.g.beginPath();
        if (!(shape instanceof javafx.scene.shape.Path path)) {
            return;
        }
        t.g.setFillRule(path.getFillRule());
        double x = 0;
        double y = 0;
        final List<PathElement> elements = new ArrayList<>(path.getElements());
        for (final PathElement e : elements) {
            if (e instanceof MoveTo m) {
                x = m.getX();
                y = m.getY();
                t.g.moveTo(x, y);
            } else if (e instanceof LineTo l) {
                x = l.getX();
                y = l.getY();
                t.g.lineTo(x, y);
            } else if (e instanceof HLineTo h) {
                x = h.getX();
                t.g.lineTo(x, y);
            } else if (e instanceof VLineTo v) {
                y = v.getY();
                t.g.lineTo(x, y);
            } else if (e instanceof CubicCurveTo c) {
                x = c.getX();
                y = c.getY();
                t.g.bezierCurveTo(c.getControlX1(), c.getControlY1(), c.getControlX2(), c.getControlY2(), x, y);
            } else if (e instanceof QuadCurveTo q) {
                x = q.getX();
                y = q.getY();
                t.g.quadraticCurveTo(q.getControlX(), q.getControlY(), x, y);
            } else if (e instanceof ClosePath) {
                t.g.closePath();
            }
        }
    }

    /** Estandar: las dos espadas cruzadas del duelo. */
    private static void swords(final Pen t) {
        for (final double dir : new double[] {1, -1}) {
            rotated(t, 40 * dir, 0, 0, () -> {
                // La hoja, con su punta.
                poly(t, -0.035, 0.18, -0.035, -0.38, 0, -0.46, 0.035, -0.38, 0.035, 0.18);
                shape(t);
                // La guarda y la empunyadura.
                poly(t, -0.14, 0.18, 0.14, 0.18, 0.14, 0.23, -0.14, 0.23);
                shape(t);
                line(t, t.outline, 0, 0.23, 0, 0.38, t.s * 0.04);
                dot(t, t.outline, t.s * 0.035, 0, 0.41);
            });
        }
    }

    /** Brawl: el escudo con el rayo, que es Commander deprisa. */
    private static void shield(final Pen t) {
        final double s = t.s;
        t.g.beginPath();
        t.g.moveTo(-0.34 * s, -0.34 * s);
        t.g.quadraticCurveTo(0, -0.44 * s, 0.34 * s, -0.34 * s);
        t.g.lineTo(0.34 * s, 0.02 * s);
        t.g.quadraticCurveTo(0.30 * s, 0.30 * s, 0, 0.44 * s);
        t.g.quadraticCurveTo(-0.30 * s, 0.30 * s, -0.34 * s, 0.02 * s);
        t.g.closePath();
        shape(t);
        poly(t, 0.05, -0.28, -0.14, 0.04, -0.01, 0.04, -0.07, 0.30, 0.14, -0.04, 0.01, -0.04);
        fillPath(t, t.outline);
    }

    /** Oathbreaker: el baculo con la chispa del planeswalker. */
    private static void staff(final Pen t) {
        final double s = t.s;
        rotated(t, 18, 0, 0, () -> {
            line(t, t.outline, 0, -0.20, 0, 0.46, s * 0.045);
            // El remate, abrazando la chispa.
            t.g.beginPath();
            t.g.moveTo(-0.02 * s, -0.18 * s);
            t.g.bezierCurveTo(-0.24 * s, -0.22 * s, -0.20 * s, -0.46 * s, 0, -0.46 * s);
            t.g.bezierCurveTo(0.20 * s, -0.46 * s, 0.24 * s, -0.22 * s, 0.02 * s, -0.18 * s);
            strokePath(t, t.outline, t.line);
            dot(t, t.fill(), s * 0.14, 0, -0.32);
            sparkle(t, 0, -0.32, 0.12);
        });
        // Y la chispa suelta, que ya no es del baculo sino de quien lo lleva.
        sparkle(t, 0.28, 0.10, 0.07);
        sparkle(t, -0.30, -0.05, 0.05);
    }

    /** Partida en red: dos cartas enfrentadas y las ondas que van de una a otra. */
    private static void network(final Pen t) {
        rotated(t, -10, -0.28, 0.10, () -> card(t, -0.28, 0.10, 0.26, 0.38));
        rotated(t, 10, 0.28, 0.10, () -> card(t, 0.28, 0.10, 0.26, 0.38));
        // Las ondas, abiertas hacia arriba desde el punto del medio.
        final double[] radii = {0.10, 0.19, 0.28};
        for (int k = 0; k < radii.length; k++) {
            t.g.beginPath();
            arc(t, 0, -0.16, radii[k], 215, 110, true);
            strokePath(t, t.outline(0.95 - k * 0.2), t.line);
        }
        dot(t, t.outline, t.s * 0.035, 0, -0.16);
    }

    /** Enciclopedia: el libro abierto, con una carta en cada pagina. */
    private static void book(final Pen t) {
        final double s = t.s;
        for (final double side : new double[] {-1, 1}) {
            t.g.beginPath();
            t.g.moveTo(0, -0.26 * s);
            t.g.quadraticCurveTo(0.20 * side * s, -0.34 * s, 0.44 * side * s, -0.28 * s);
            t.g.lineTo(0.44 * side * s, 0.30 * s);
            t.g.quadraticCurveTo(0.20 * side * s, 0.24 * s, 0, 0.32 * s);
            t.g.closePath();
            shape(t);
            // Una carta pequenya dibujada en la pagina.
            t.g.setStroke(t.outline(0.55));
            t.g.setLineWidth(t.thin);
            t.g.strokeRect((0.13 * side - 0.08) * s, -0.18 * s, s * 0.16, s * 0.22);
            // Y sus renglones.
            for (int k = 0; k < 3; k++) {
                final double y = 0.10 + k * 0.06;
                line(t, t.outline(0.45), 0.07 * side, y, 0.36 * side, y, s * 0.012);
            }
        }
        // El lomo.
        line(t, t.outline, 0, -0.26, 0, 0.32, s * 0.02);
    }

    /** Logros: el trofeo, con sus asas y la chispa del grado mitico. */
    private static void trophy(final Pen t) {
        final double s = t.s;
        t.g.beginPath();
        t.g.moveTo(-0.26 * s, -0.36 * s);
        t.g.lineTo(0.26 * s, -0.36 * s);
        t.g.bezierCurveTo(0.26 * s, -0.02 * s, 0.14 * s, 0.08 * s, 0.05 * s, 0.10 * s);
        t.g.lineTo(-0.05 * s, 0.10 * s);
        t.g.bezierCurveTo(-0.14 * s, 0.08 * s, -0.26 * s, -0.02 * s, -0.26 * s, -0.36 * s);
        t.g.closePath();
        shape(t);
        // Las asas.
        for (final double side : new double[] {-1, 1}) {
            t.g.beginPath();
            t.g.moveTo(0.25 * side * s, -0.30 * s);
            t.g.bezierCurveTo(0.44 * side * s, -0.30 * s, 0.42 * side * s, -0.06 * s, 0.18 * side * s, -0.04 * s);
            strokePath(t, t.outline, t.line);
        }
        // El pie y la peana.
        poly(t, -0.05, 0.10, 0.05, 0.10, 0.07, 0.24, -0.07, 0.24);
        shape(t);
        poly(t, -0.20, 0.24, 0.20, 0.24, 0.20, 0.36, -0.20, 0.36);
        shape(t);
        sparkle(t, 0, -0.16, 0.10);
    }

    /** Otros formatos: varios mazos en fila, cada uno con su pozo. */
    private static void decks(final Pen t) {
        final double[] xs = {-0.20, 0, 0.20};
        for (int i = 0; i < xs.length; i++) {
            final double dy = i == 1 ? -0.06 : 0.04;
            // Dos cartas por mazo, la de atras asomando: se lee como un monton.
            card(t, xs[i] + 0.025, dy + 0.025, 0.28, 0.42);
            card(t, xs[i], dy, 0.28, 0.42);
        }
    }

    // ------------------------------------------------------------------
    //  Los tres que solo tiene el escritorio
    // ------------------------------------------------------------------

    /** Tutorial: el birrete. Es donde se aprende a manejar la mesa. */
    private static void cap(final Pen t) {
        final double s = t.s;
        // La copa, debajo del tablero.
        t.g.beginPath();
        t.g.moveTo(-0.26 * s, -0.06 * s);
        t.g.lineTo(-0.26 * s, 0.16 * s);
        t.g.quadraticCurveTo(0, 0.32 * s, 0.26 * s, 0.16 * s);
        t.g.lineTo(0.26 * s, -0.06 * s);
        t.g.quadraticCurveTo(0, 0.06 * s, -0.26 * s, -0.06 * s);
        t.g.closePath();
        shape(t);
        // El tablero, en rombo: es lo que lo hace birrete y no gorro.
        poly(t, 0, -0.38, 0.48, -0.16, 0, 0.06, -0.48, -0.16);
        shape(t);
        // El boton y la borla, que cuelga por un lado.
        dot(t, t.outline, s * 0.035, 0, -0.16);
        line(t, t.outline, 0, -0.16, 0.38, -0.04, t.thin * 1.4);
        line(t, t.outline, 0.38, -0.04, 0.38, 0.20, t.thin * 1.4);
        poly(t, 0.38, 0.17, 0.44, 0.33, 0.32, 0.33);
        fillPath(t, t.outline);
    }

    /** Torneo: el cuadro de eliminatorias, de cuatro a uno, con la chispa del campeon. */
    private static void bracket(final Pen t) {
        final double s = t.s;
        final double h = 0.09;
        // Los cuatro de la primera ronda, los dos de la semifinal y el campeon.
        final double[] first = {-0.36, -0.14, 0.14, 0.36};
        final double[] semi = {-0.25, 0.25};
        // Las lineas que los unen, primero: las cajas van por encima.
        for (int p = 0; p < 2; p++) {
            final double a = first[p * 2];
            final double b = first[p * 2 + 1];
            line(t, t.outline(0.8), -0.20, a, -0.13, a, t.thin * 1.3);
            line(t, t.outline(0.8), -0.20, b, -0.13, b, t.thin * 1.3);
            line(t, t.outline(0.8), -0.13, a, -0.13, b, t.thin * 1.3);
            line(t, t.outline(0.8), -0.13, semi[p], -0.07, semi[p], t.thin * 1.3);
        }
        line(t, t.outline(0.8), 0.16, semi[0], 0.22, semi[0], t.thin * 1.3);
        line(t, t.outline(0.8), 0.16, semi[1], 0.22, semi[1], t.thin * 1.3);
        line(t, t.outline(0.8), 0.22, semi[0], 0.22, semi[1], t.thin * 1.3);
        line(t, t.outline(0.8), 0.22, 0, 0.27, 0, t.thin * 1.3);
        for (final double y : first) {
            roundRect(t, -0.46, y - h / 2, -0.20, y + h / 2, 0.02);
        }
        for (final double y : semi) {
            roundRect(t, -0.07, y - h / 2, 0.16, y + h / 2, 0.02);
        }
        roundRect(t, 0.27, -0.065, 0.48, 0.065, 0.025);
        sparkle(t, 0.375, -0.17, 0.08);
        sparkle(t, 0.47, -0.27, 0.04);
        dot(t, t.outline, s * 0.02, 0.375, 0);
    }

    /** Personalizar: la paleta con una gota de cada color de Magic. */
    private static void palette(final Pen t) {
        final double s = t.s;
        t.g.beginPath();
        t.g.moveTo(0, -0.36 * s);
        t.g.bezierCurveTo(0.30 * s, -0.38 * s, 0.48 * s, -0.12 * s, 0.40 * s, 0.12 * s);
        t.g.bezierCurveTo(0.34 * s, 0.30 * s, 0.12 * s, 0.40 * s, -0.10 * s, 0.36 * s);
        t.g.bezierCurveTo(-0.36 * s, 0.32 * s, -0.46 * s, 0.06 * s, -0.38 * s, -0.14 * s);
        t.g.bezierCurveTo(-0.30 * s, -0.32 * s, -0.14 * s, -0.35 * s, 0, -0.36 * s);
        t.g.closePath();
        shape(t);
        // El agujero del pulgar.
        dot(t, t.hole(0.9), s * 0.07, 0.10, 0.16);
        ring(t, t.outline, s * 0.07, 0.10, 0.16, t.thin);
        // Las cinco gotas, en el orden de siempre.
        final double[][] at = {{-0.24, -0.10}, {-0.08, -0.22}, {0.12, -0.21}, {0.27, -0.06}, {-0.24, 0.12}};
        final Color[] paint = {W, U, B, R, G};
        for (int i = 0; i < at.length; i++) {
            dot(t, alpha(paint[i], 0.95 * t.f), s * 0.06, at[i][0], at[i][1]);
            ring(t, t.outline(0.8), s * 0.06, at[i][0], at[i][1], t.thin);
        }
    }
}
