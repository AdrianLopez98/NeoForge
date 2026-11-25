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
 *   <li><b>Color por significado.</b> Rojo = ataque, dorado = bloqueo (como
 *       Arena, y Ajustes deja elegir los dos), ambar = objetivo de un
 *       hechizo. Se distingue sin leer nada.</li>
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

    /**
     * La carta bajo el raton (25-11-2026, Discord: <i>"I find blocking very
     * confusing"</i>). Si esta en el combate, su pareja se pinta entera y el
     * resto de flechas se apaga: con seis atacantes y cuatro bloqueos se ve de
     * un vistazo quien para a quien.
     */
    private Object focus;

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
        // Fuera de la pantalla no se pinta: ver sync().
        sceneProperty().addListener((o, was, is) -> sync());
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

    /**
     * La carta bajo el raton, o null. Solo cambia el dibujo: la capa sigue
     * siendo transparente al raton.
     */
    public void setFocus(final Object entity) {
        if (DEBUG) {
            System.out.println("[flecha] foco " + entity);
        }
        this.focus = entity;
        if (running) {
            draw();
        }
    }

    public void endDrag() {
        this.dragSource = null;
        sync();
    }

    public boolean isDragging() {
        return dragSource != null;
    }

    /**
     * Arranca o para el repintado segun haya algo que dibujar Y la mesa este
     * en pantalla.
     *
     * <p>Lo de la pantalla no es un detalle: un {@code AnimationTimer} en marcha
     * vive en la lista global de JavaFX, y con el a esta capa y a TODA su mesa
     * — la partida, las cartas, las imagenes y sus texturas. Una partida que
     * acababa con alguna flecha puesta (el ataque letal, lo normal al ganar)
     * dejaba su mesa viva y repintandose para siempre. En la Aventura, una por
     * duelo, hasta llenar la memoria de video y dejar la pantalla en blanco
     * (itch.io, 23 y 25-09-2026; encontrado con un volcado del heap).
     */
    private void sync() {
        final boolean want = (!links.isEmpty() || dragSource != null) && getScene() != null;
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

        // Los atacantes que alguien bloquea: su flecha al jugador se apaga (ya
        // no llega) y deja que se lea la del bloqueo, que es la que importa.
        final List<Object> blocked = new ArrayList<>();
        for (final Link link : links) {
            if (link.kind == Kind.BLOCK) {
                blocked.add(link.to);
            }
        }
        final List<Link> lit = litBy(focus);
        // El latido de lo que entra sin bloquear, como Android (decision 646).
        final double pulse = 0.5 + 0.5 * Math.sin(System.nanoTime() / 1.0e9 * Math.PI / 1.1);
        final Color attack = colorOf(Kind.ATTACK);
        final Color block = colorOf(Kind.BLOCK);

        // Primero objetivos y ataques; los bloqueos los ultimos, por encima.
        for (int pass = 0; pass < 2; pass++) {
            for (final Link link : links) {
                if ((link.kind == Kind.BLOCK) != (pass == 1)) {
                    continue;
                }
                final Bounds a = locator.boundsOf(link.from);
                final Bounds b = locator.boundsOf(link.to);
                if (a == null || b == null) {
                    continue;
                }
                if (DEBUG) {
                    System.out.printf("[flecha] %s  %s -> %s   %s => %s%n", link.kind,
                            link.from, link.to, fmt(a), fmt(b));
                }
                final double dim = lit == null || lit.contains(link) ? 1 : 0.18;
                switch (link.kind) {
                    case ATTACK -> {
                        if (containsSame(blocked, link.from)) {
                            arrow(g, a, b, attack.deriveColor(0, 1, 1, 0.40 * dim), 2.2, true);
                        } else {
                            arrow(g, a, b, attack.deriveColor(0, 1, 1, (0.72 + 0.28 * pulse) * dim), 4.5, true);
                        }
                    }
                    case BLOCK -> blockArrow(g, a, b, block.deriveColor(0, 1, 1, dim));
                    default -> arrow(g, a, b, colorOf(link.kind).deriveColor(0, 1, 1, dim), widthOf(link.kind), true);
                }
            }
        }

        if (dragSource != null) {
            final Color c = dragValid ? Color.web("#4FB477") : Color.web("#4A9BE0");
            final Bounds a = locator.boundsOf(dragSource);
            if (a != null) {
                arrowTo(g, centerX(a), centerY(a), dragX, dragY, c, 4.5, true);
            }
        }
    }

    /**
     * Las flechas que se ven enteras con {@code focus} bajo el raton, o null si
     * no toca a ninguna (entonces se ven todas). Un atacante enciende su ataque
     * y sus bloqueos; un bloqueador, los de cada atacante que bloquea (asi se
     * ven tambien los demas que paran a ese mismo); y un objetivo de hechizo,
     * su flecha.
     */
    private List<Link> litBy(final Object focus) {
        if (focus == null) {
            return null;
        }
        final List<Object> attackers = new ArrayList<>();
        for (final Link l : links) {
            if (l.kind == Kind.ATTACK && same(l.from, focus)) {
                attackers.add(l.from);
            } else if (l.kind == Kind.BLOCK && (same(l.from, focus) || same(l.to, focus))) {
                attackers.add(l.to);
            }
        }
        final List<Link> out = new ArrayList<>();
        for (final Link l : links) {
            final boolean mine = switch (l.kind) {
                case ATTACK -> containsSame(attackers, l.from);
                case BLOCK -> containsSame(attackers, l.to);
                default -> same(l.from, focus) || same(l.to, focus);
            };
            if (mine) {
                out.add(l);
            }
        }
        return out.isEmpty() ? null : out;
    }

    /**
     * La misma entidad aunque sean dos objetos: el motor rehace sus
     * {@code CardView} y la carta bajo el raton puede no ser la instancia de la
     * flecha.
     */
    private static boolean same(final Object a, final Object b) {
        if (a == b) {
            return true;
        }
        if (a instanceof forge.game.card.CardView ca && b instanceof forge.game.card.CardView cb) {
            return ca.getId() == cb.getId();
        }
        return a != null && a.equals(b);
    }

    private static boolean containsSame(final List<Object> list, final Object o) {
        for (final Object x : list) {
            if (same(x, o)) {
                return true;
            }
        }
        return false;
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
                return safeColor(forge.neo.NeoSettings.arrowColor(true), "#D9534F");
            case BLOCK:
                return safeColor(forge.neo.NeoSettings.arrowColor(false), "#F2C14E");
            case TARGET:
                return Color.web("#E0A63C");
            default:
                return Color.web("#4FB477");
        }
    }

    private static Color safeColor(final String hex, final String fallback) {
        try {
            return Color.web(hex);
        } catch (final RuntimeException e) {
            return Color.web(fallback);
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
                       final Color color, final double width, final boolean fade) {
        final double x1 = centerX(from);
        final double y1 = centerY(from);
        final double x2 = centerX(to);
        final double y2 = centerY(to);
        final double[] start = edgePoint(from, x2, y2);
        final double[] end = edgePoint(to, x1, y1);
        arrowTo(g, start[0], start[1], end[0], end[1], color, width, fade);
    }

    /**
     * LA FLECHA DEL BLOQUEO, como la de Android (sus decisiones 214 y 646):
     * solida de punta a punta, mas gruesa, con un halo de su color y un
     * contorno oscuro, y en S por el hueco entre las dos filas, entrando de
     * frente en su atacante. Antes era azul y nacia casi transparente: con el
     * bloqueador justo debajo de su atacante quedaba un trazo de un dedo que
     * no se veia (Discord, 25-11-2026, con capturas).
     */
    private void blockArrow(final GraphicsContext g, final Bounds from, final Bounds to, final Color color) {
        final double sy;
        final double ey;
        if (from.getMinY() >= to.getMaxY() + 4) {
            sy = from.getMinY();
            ey = to.getMaxY();
        } else if (from.getMaxY() + 4 <= to.getMinY()) {
            sy = from.getMaxY();
            ey = to.getMinY();
        } else {
            // Sin hueco entre las dos (misma fila): la curva de siempre.
            final double[] start = edgePoint(from, centerX(to), centerY(to));
            strokeArrow(g, start[0], start[1], centerX(to), centerY(to), null, color, 5.5, false, true);
            return;
        }
        // La punta, en el CENTRO del atacante y no en su borde (Ana,
        // 25-11-2026): con el bloqueador justo debajo de su atacante la flecha
        // hasta el borde medía un dedo; hasta el centro recorre media carta y
        // se ve sin duda a cual para. El codo sigue en el hueco entre las filas.
        final double sx = centerX(from);
        final double ex = centerX(to);
        final double mid = (sy + ey) / 2;
        strokeArrow(g, sx, sy, ex, centerY(to), new double[] {sx, mid, ex, mid},
                color, 5.5, false, true);
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
                         final Color color, final double width, final boolean fade) {
        strokeArrow(g, x1, y1, x2, y2, null, color, width, fade, false);
    }

    /**
     * Pinta la flecha. Sin {@code elbow}, una cuadratica con la panza hacia
     * fuera; con el, una cubica por esos dos puntos de control. {@code fade}:
     * casi transparente en el origen y solida en la punta (dice el sentido sin
     * buscar la cabeza). {@code halo}: resplandor ancho de su color y contorno
     * oscuro debajo, para que se lea encima del arte.
     */
    private void strokeArrow(final GraphicsContext g, final double x1, final double y1,
                             final double x2, final double y2, final double[] elbow,
                             final Color color, final double width, final boolean fade,
                             final boolean halo) {
        final double dx = x2 - x1;
        final double dy = y2 - y1;
        final double len = Math.hypot(dx, dy);
        if (len < 4) {
            return;
        }
        // El punto desde el que llega la curva a la punta (orienta la cabeza).
        final double hx;
        final double hy;
        g.beginPath();
        g.moveTo(x1, y1);
        if (elbow != null) {
            g.bezierCurveTo(elbow[0], elbow[1], elbow[2], elbow[3], x2, y2);
            hx = elbow[2];
            hy = elbow[3];
        } else {
            // Control desplazado en perpendicular: curva suave y constante.
            final double bulge = Math.min(70, len * 0.22);
            final double cx = (x1 + x2) / 2 + -dy / len * bulge;
            final double cy = (y1 + y2) / 2 + dx / len * bulge;
            g.quadraticCurveTo(cx, cy, x2, y2);
            hx = cx;
            hy = cy;
        }
        g.setLineCap(StrokeLineCap.ROUND);
        if (halo) {
            g.setStroke(color.deriveColor(0, 1, 1, 0.20));
            g.setLineWidth(width * 3.6);
            g.stroke();
            g.setStroke(Color.rgb(12, 15, 20, 0.80 * color.getOpacity()));
            g.setLineWidth(width + 3.5);
            g.stroke();
        }
        if (fade) {
            g.setStroke(new LinearGradient(x1, y1, x2, y2, false, CycleMethod.NO_CYCLE,
                    new Stop(0, color.deriveColor(0, 1, 1, 0.25)),
                    new Stop(1, color)));
        } else {
            g.setStroke(color);
        }
        g.setLineWidth(width);
        g.stroke();

        // La punta se orienta con la tangente de la curva en el final, que es
        // la direccion desde el ultimo punto de control.
        double tx = x2 - hx;
        double ty = y2 - hy;
        double tl = Math.hypot(tx, ty);
        if (tl < 0.01) {
            tx = dx;
            ty = dy;
            tl = len;
        }
        final double ux = tx / tl;
        final double uy = ty / tl;
        final double head = width * (halo ? 2.6 : 3.2);
        final double px = -uy;
        final double py = ux;

        g.beginPath();
        g.moveTo(x2, y2);
        g.lineTo(x2 - ux * head + px * head * 0.55, y2 - uy * head + py * head * 0.55);
        g.lineTo(x2 - ux * head * 0.62, y2 - uy * head * 0.62);
        g.lineTo(x2 - ux * head - px * head * 0.55, y2 - uy * head - py * head * 0.55);
        g.closePath();
        if (halo) {
            g.setStroke(Color.rgb(12, 15, 20, 0.80 * color.getOpacity()));
            g.setLineWidth(3);
            g.stroke();
        }
        g.setFill(color);
        g.fill();
    }
}
