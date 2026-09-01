package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.StrokeLineCap;

/**
 * Las flechas de combate y de objetivo, como en Arena.
 *
 * <p>Es una capa transparente por encima de toda la mesa. Nunca recibe clicks:
 * lo que pinta es informacion, no controles.
 *
 * <p>Tres cosas que hacen que se lea bien, copiadas del lenguaje visual de
 * Arena:
 *
 * <ol>
 *   <li><b>Curva, no recta.</b> Una Bezier con la panza hacia fuera separa
 *       visualmente flechas que salen del mismo sitio. Con lineas rectas, seis
 *       atacantes contra el mismo jugador son un borron.</li>
 *   <li><b>Color por significado.</b> Rojo = ataque, azul = bloqueo, ambar =
 *       objetivo de un hechizo. Se distingue sin leer nada.</li>
 *   <li><b>La punta toca el borde de la carta</b>, no su centro: asi se ve a
 *       quien apunta aunque las cartas esten solapadas.</li>
 * </ol>
 *
 * <p>Se repinta en cada pulso mientras haya algo que dibujar. Las cartas se
 * mueven con animaciones (avance del atacante, hover, tapado) y una flecha que
 * no sigue a su carta se ve peor que no tener flecha.
 */
public class CombatOverlay extends Pane {

    /** Que significa la flecha. El color sale de aqui. */
    public enum Kind {
        /** Atacante -> a quien ataca. */
        ATTACK,
        /** Bloqueador -> atacante al que bloquea. */
        BLOCK,
        /** Hechizo o habilidad -> lo que tiene por objetivo. */
        TARGET,
        /** La que sigue al raton mientras arrastras. */
        DRAG
    }

    /** Traduce una entidad del motor (CardView / PlayerView) a un rectangulo. */
    public interface Locator {
        /** Rectangulo de la entidad en coordenadas de esta capa, o null si no se ve. */
        Bounds boundsOf(Object entity);
    }

    private static final class Link {
        private final Object from;
        private final Object to;
        private final Kind kind;

        Link(final Object from, final Object to, final Kind kind) {
            this.from = from;
            this.to = to;
            this.kind = kind;
        }
    }

    private final Canvas canvas = new Canvas();
    private final List<Link> links = new ArrayList<>();
    private Locator locator;

    /** Arrastre en curso: origen real y punta pegada al raton. */
    private Object dragSource;
    private double dragX;
    private double dragY;
    private boolean dragValid;

    private final AnimationTimer pulse = new AnimationTimer() {
        @Override
        public void handle(final long now) {
            draw();
        }
    };
    private boolean running;

    public CombatOverlay() {
        getStyleClass().add("combat-overlay");
        // Informacion, no controles: los clicks pasan de largo hasta la mesa.
        setMouseTransparent(true);
        getChildren().add(canvas);
        canvas.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.85)));
    }

    public void setLocator(final Locator locator) {
        this.locator = locator;
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    // ---------------------------------------------------------------
    // Contenido
    // ---------------------------------------------------------------

    /**
     * Sustituye todas las flechas de una vez.
     *
     * @param triples cada elemento es {origen, destino, Kind}
     */
    public void setLinks(final List<Object[]> triples) {
        links.clear();
        if (triples != null) {
            for (final Object[] t : triples) {
                if (t != null && t.length == 3 && t[0] != null && t[1] != null) {
                    links.add(new Link(t[0], t[1], (Kind) t[2]));
                }
            }
        }
        sync();
    }

    /** Fuerza un repintado inmediato. Lo usa la captura disparada por combate. */
    public void redrawNow() {
        draw();
    }

    /** Cuantas flechas hay pintadas. Lo usa la captura disparada por combate. */
    public int linkCount() {
        return links.size();
    }

    /** Solo las de combate (ataque y bloqueo), sin contar objetivos de hechizo. */
    public int fightLinkCount() {
        int n = 0;
        for (final Link l : links) {
            if (l.kind == Kind.ATTACK || l.kind == Kind.BLOCK) {
                n++;
            }
        }
        return n;
    }

    public void clearLinks() {
        links.clear();
        sync();
    }

    /** Empieza a pintar la flecha que sigue al raton. */
    public void startDrag(final Object source) {
        this.dragSource = source;
        this.dragValid = false;
        sync();
    }

    /**
     * Mueve la punta de la flecha de arrastre.
     *
     * @param valid true si debajo del raton hay un destino aceptable; la flecha
     *              se pinta en verde para decirlo sin texto, como Arena
     */
    public void moveDrag(final double x, final double y, final boolean valid) {
        this.dragX = x;
        this.dragY = y;
        this.dragValid = valid;
    }

    public void endDrag() {
        this.dragSource = null;
        sync();
    }

    public boolean isDragging() {
        return dragSource != null;
    }

    /** Arranca o para el repintado segun haya algo que dibujar. */
    private void sync() {
        final boolean want = !links.isEmpty() || dragSource != null;
        if (want && !running) {
            running = true;
            pulse.start();
        } else if (!want && running) {
            running = false;
            pulse.stop();
            clearCanvas();
        }
    }

    private void clearCanvas() {
        canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    // ---------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------

    private void draw() {
        final GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        if (locator == null) {
            return;
        }

        for (final Link link : links) {
            final Bounds a = locator.boundsOf(link.from);
            final Bounds b = locator.boundsOf(link.to);
            if (a == null || b == null) {
                continue;
            }
            if (DEBUG) {
                System.out.printf("[flecha] %s  %s -> %s   %s => %s%n", link.kind,
                        link.from, link.to, fmt(a), fmt(b));
            }
            arrow(g, a, b, colorOf(link.kind), widthOf(link.kind));
        }

        if (dragSource != null) {
            final Color c = dragValid ? Color.web("#4FB477") : Color.web("#4A9BE0");
            final Bounds a = locator.boundsOf(dragSource);
            if (a != null) {
                arrowTo(g, centerX(a), centerY(a), dragX, dragY, c, 4.5);
            }
        }
    }

    /** Traza las coordenadas de cada flecha. Se enciende con -Dneo.arrows.debug=true */
    private static final boolean DEBUG = Boolean.getBoolean("neo.arrows.debug");

    private static String fmt(final Bounds b) {
        return String.format("(%.0f,%.0f %.0fx%.0f)",
                b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }

    private static Color colorOf(final Kind kind) {
        switch (kind) {
            case ATTACK:
                return Color.web("#D9534F");
            case BLOCK:
                return Color.web("#4A9BE0");
            case TARGET:
                return Color.web("#E0A63C");
            default:
                return Color.web("#4FB477");
        }
    }

    private static double widthOf(final Kind kind) {
        return kind == Kind.TARGET ? 3.0 : 4.5;
    }

    private static double centerX(final Bounds b) {
        return b.getMinX() + b.getWidth() / 2;
    }

    private static double centerY(final Bounds b) {
        return b.getMinY() + b.getHeight() / 2;
    }

    /**
     * Flecha entre dos rectangulos. Se recorta contra los bordes para que ni
     * salga del centro de la carta ni la punta se meta dentro del destino.
     */
    private void arrow(final GraphicsContext g, final Bounds from, final Bounds to,
                       final Color color, final double width) {
        final double x1 = centerX(from);
        final double y1 = centerY(from);
        final double x2 = centerX(to);
        final double y2 = centerY(to);
        final double[] start = edgePoint(from, x2, y2);
        final double[] end = edgePoint(to, x1, y1);
        arrowTo(g, start[0], start[1], end[0], end[1], color, width);
    }

    /**
     * Punto donde la recta hacia (tx,ty) sale del rectangulo.
     *
     * <p>Sin esto la flecha nace en el centro de la carta y tapa el arte;
     * naciendo en el borde parece que sale de la criatura.
     */
    private static double[] edgePoint(final Bounds box, final double tx, final double ty) {
        final double cx = centerX(box);
        final double cy = centerY(box);
        final double dx = tx - cx;
        final double dy = ty - cy;
        if (dx == 0 && dy == 0) {
            return new double[] {cx, cy};
        }
        final double hw = box.getWidth() / 2 * 0.92;
        final double hh = box.getHeight() / 2 * 0.92;
        // Escala minima que lleva el punto hasta uno de los dos bordes.
        final double sx = dx == 0 ? Double.MAX_VALUE : hw / Math.abs(dx);
        final double sy = dy == 0 ? Double.MAX_VALUE : hh / Math.abs(dy);
        final double s = Math.min(sx, sy);
        return new double[] {cx + dx * s, cy + dy * s};
    }

    /** Curva Bezier con punta. La panza hace legibles varias flechas juntas. */
    private void arrowTo(final GraphicsContext g, final double x1, final double y1,
                         final double x2, final double y2,
                         final Color color, final double width) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        final double len = Math.hypot(dx, dy);
        if (len < 4) {
            return;
        }

        // Control desplazado en perpendicular: curva suave y constante.
        final double bulge = Math.min(70, len * 0.22);
        final double nx = -dy / len;
        final double ny = dx / len;
        final double cx = (x1 + x2) / 2 + nx * bulge;
        final double cy = (y1 + y2) / 2 + ny * bulge;

        // Degradado: casi transparente en el origen, solido en la punta. Deja
        // claro el sentido de la flecha sin tener que buscar la cabeza.
        g.setStroke(new LinearGradient(x1, y1, x2, y2, false, CycleMethod.NO_CYCLE,
                new Stop(0, color.deriveColor(0, 1, 1, 0.25)),
                new Stop(1, color)));
        g.setLineWidth(width);
        g.setLineCap(StrokeLineCap.ROUND);
        g.beginPath();
        g.moveTo(x1, y1);
        g.quadraticCurveTo(cx, cy, x2, y2);
        g.stroke();

        // La punta se orienta con la tangente de la curva en el final, que en
        // una cuadratica es la direccion desde el punto de control.
        final double tx = x2 - cx;
        final double ty = y2 - cy;
        final double tl = Math.hypot(tx, ty);
        if (tl < 0.01) {
            return;
        }
        final double ux = tx / tl;
        final double uy = ty / tl;
        final double head = width * 3.2;
        final double px = -uy;
        final double py = ux;

        g.setFill(color);
        g.beginPath();
        g.moveTo(x2, y2);
        g.lineTo(x2 - ux * head + px * head * 0.55, y2 - uy * head + py * head * 0.55);
        g.lineTo(x2 - ux * head * 0.62, y2 - uy * head * 0.62);
        g.lineTo(x2 - ux * head - px * head * 0.55, y2 - uy * head - py * head * 0.55);
        g.closePath();
        g.fill();
    }
}
