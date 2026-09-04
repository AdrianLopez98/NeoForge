package forge.neo.ui;

import java.util.Random;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Un pergamino, dibujado.
 *
 * <h2>Por que el mapa NO va sobre el tapete oscuro</h2>
 *
 * <p>Toda la interfaz de partida es oscura, y con razon (las notas de diseño seccion 6:
 * <i>mesa oscura, cartas luminosas</i>): ahi lo que tiene que brillar es el
 * arte de las cartas. <b>En el mapa no hay cartas, hay iconos</b> — y un icono
 * oscuro sobre negro no se ve. Un fondo negro con puntos y lineas parece un
 * grafo de depuracion, que es exactamente lo que esta pantalla no puede
 * parecer: es la que mas va a mirar el jugador.
 *
 * <h2>Dibujado, no descargado</h2>
 *
 * <p>Ni un PNG. Es la misma decision que {@link FlagIcon}: un degradado calido,
 * unas manchas de tinta suaves y un <b>borde roto</b> dan el pergamino entero
 * en codigo, escalan a cualquier resolucion, se tinen por acto con un
 * parametro y no anyaden un byte al zip que se publica.
 *
 * <h2>La textura es determinista</h2>
 *
 * <p>Las manchas salen de un {@link Random} sembrado con la semilla que se le
 * pase, no de {@code Math.random()}. Si no, <b>cada redibujado daria otro
 * pergamino</b>: al mover la ventana, al pasar el raton por un nodo o al
 * cambiar de acto, el fondo entero parpadearia. Con la semilla de la run, el
 * mapa de una partida guardada tiene siempre exactamente el mismo papel.
 */
public final class Parchment extends Region {

    /**
     * Cuantas manchas por cada 100.000 pixeles de superficie.
     *
     * <p>Muchas y muy pequenyas. La primera version ponia 22 manchas grandes y
     * en la captura <b>parecian burbujas</b>: circulos palidos del tamanyo de un
     * nodo, compitiendo con los iconos. Una textura de papel se tiene que notar
     * sin llegar a verse — si al mirar la pantalla se distingue una mancha
     * concreta, sobra.
     */
    private static final double STAINS_PER_AREA = 140.0 / 100_000.0;

    private final Canvas canvas = new Canvas();
    private final long seed;
    private final Color base;
    /**
     * Cuanto hay que apartarse del canto para caer sobre el papel SEGURO.
     *
     * <h2>Por que hace falta un numero para esto</h2>
     *
     * <p>El pergamino tiene el <b>borde roto</b> ({@code tornPath}): se muerde
     * {@code bite = min(ancho,alto) * 0,018} y ademas tiembla {@code +-bite},
     * asi que el papel puede acabar hasta {@code 2*bite} antes del canto del
     * lienzo — y no siempre en el mismo sitio, porque el mordisco es
     * irregular. Cualquier cosa pegada al borde queda <b>a caballo</b>: medio
     * texto sobre el papel claro y medio sobre el fondo oscuro. No se lee.
     *
     * <p>Pasa en las dos direcciones y ha habido que arreglarlo tres veces por
     * separado antes de escribirlo aqui: las pastillas de arriba del mapa
     * (<i>"Creditos 780" partido por la mitad</i>), la botonera de abajo del
     * mapa y los botones del resumen final (<i>"estos tambien hay que
     * subirlos"</i>, 03-09-2026). Por eso el numero es de esta clase y no de
     * cada pantalla: la que se olvide de mirarlo es la siguiente que falla.
     *
     * <p>38 sale de la cuenta a 1080 de alto ({@code 2*bite ≈ 38}). Es fijo y
     * no proporcional a proposito: en una ventana mas pequenya sobra margen, y
     * sobrar es gratis — faltar es lo que se ve.
     */
    public static final double SAFE_EDGE = 38;

    private final Color edge;

    /**
     * @param seed  de donde salen las manchas. La misma semilla, el mismo papel
     * @param tint  el tono del acto, o {@code null} para el pergamino de siempre
     */
    public Parchment(final long seed, final Color tint) {
        this.seed = seed;
        this.base = tint == null ? Color.web("#E8D7B0") : tint;
        this.edge = base.deriveColor(0, 1.05, 0.62, 1);
        getChildren().add(canvas);
        setMinSize(0, 0);
        // El papel proyecta sombra sobre lo que haya detras: es lo que lo separa
        // del fondo y lo que hace que se lea como una hoja y no como un relleno.
        setEffect(new DropShadow(24, 0, 6, Color.rgb(0, 0, 0, 0.55)));
    }

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (canvas.getWidth() != w || canvas.getHeight() != h) {
            canvas.setWidth(w);
            canvas.setHeight(h);
            paint(canvas.getGraphicsContext2D(), w, h);
        }
        canvas.relocate(0, 0);
    }

    /** Vuelve a dibujar el papel (al cambiar de acto, que cambia el tono). */
    public void repaint() {
        if (canvas.getWidth() > 0) {
            paint(canvas.getGraphicsContext2D(), canvas.getWidth(), canvas.getHeight());
        }
    }

    private void paint(final GraphicsContext g, final double w, final double h) {
        g.clearRect(0, 0, w, h);
        final Random rnd = new Random(seed);

        // 1. El papel: mas claro en el centro y tostado hacia los bordes. Es lo
        //    que hace que parezca una hoja iluminada y no un rectangulo de color.
        g.setFill(new RadialGradient(0, 0, 0.5, 0.45, 0.75, true, CycleMethod.NO_CYCLE,
                new Stop(0, base.deriveColor(0, 0.85, 1.06, 1)),
                new Stop(0.65, base),
                new Stop(1, edge)));
        fillTornShape(g, w, h, rnd);

        // 2. El grano del papel: muchas manchas pequenyas e irregulares. Cada
        //    una son tres ovalos superpuestos y no un circulo — un circulo
        //    perfecto se ve como un circulo, y entonces es una burbuja.
        final int stains = (int) Math.max(40, w * h * STAINS_PER_AREA);
        for (int i = 0; i < stains; i++) {
            final double r = Math.min(w, h) * (0.004 + rnd.nextDouble() * 0.016);
            final double x = rnd.nextDouble() * w;
            final double y = rnd.nextDouble() * h;
            g.setFill(edge.deriveColor(0, 1, 0.94, 0.014 + rnd.nextDouble() * 0.022));
            for (int blob = 0; blob < 3; blob++) {
                final double ox = (rnd.nextDouble() - 0.5) * r;
                final double oy = (rnd.nextDouble() - 0.5) * r;
                final double rx = r * (0.6 + rnd.nextDouble() * 0.8);
                final double ry = r * (0.6 + rnd.nextDouble() * 0.8);
                g.fillOval(x + ox - rx, y + oy - ry, rx * 2, ry * 2);
            }
        }

        // 3. Y un tostado mas fuerte pegado al borde roto, como el papel viejo.
        g.setStroke(edge.deriveColor(0, 1.1, 0.72, 0.55));
        g.setLineWidth(Math.max(3, Math.min(w, h) * 0.012));
        strokeTornShape(g, w, h, new Random(seed));
    }

    // ------------------------------------------------------------------

    /**
     * El borde roto.
     *
     * <p>Es la mitad del efecto y cuesta veinte lineas: un rectangulo con las
     * aristas mordidas. Sin esto el pergamino es un rectangulo de color beige,
     * que no evoca nada.
     */
    private void fillTornShape(final GraphicsContext g, final double w, final double h,
                               final Random rnd) {
        g.beginPath();
        tornPath(g, w, h, rnd);
        g.fill();
    }

    private void strokeTornShape(final GraphicsContext g, final double w, final double h,
                                 final Random rnd) {
        g.beginPath();
        tornPath(g, w, h, rnd);
        g.stroke();
    }

    private void tornPath(final GraphicsContext g, final double w, final double h,
                          final Random rnd) {
        // Cuanto se muerde el borde. Proporcional al lado corto: en una ventana
        // pequenya un mordisco fijo se comeria media pantalla.
        final double bite = Math.min(w, h) * 0.018;
        final int perSide = 14;
        g.moveTo(bite, bite);
        for (int i = 1; i <= perSide; i++) {
            g.lineTo(bite + w * i / (double) perSide - bite * 2 * i / perSide,
                    bite + (rnd.nextDouble() - 0.5) * bite * 2);
        }
        for (int i = 1; i <= perSide; i++) {
            g.lineTo(w - bite + (rnd.nextDouble() - 0.5) * bite * 2,
                    bite + h * i / (double) perSide - bite * 2 * i / perSide);
        }
        for (int i = perSide; i >= 1; i--) {
            g.lineTo(bite + w * i / (double) perSide - bite * 2 * i / perSide,
                    h - bite + (rnd.nextDouble() - 0.5) * bite * 2);
        }
        for (int i = perSide; i >= 1; i--) {
            g.lineTo(bite + (rnd.nextDouble() - 0.5) * bite * 2,
                    bite + h * i / (double) perSide - bite * 2 * i / perSide);
        }
        g.closePath();
    }
}
