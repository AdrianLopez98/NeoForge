package forge.neo.ui;

import forge.neo.ascent.AscentNode;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;

/**
 * El icono de cada tipo de nodo, dibujado con figuras.
 *
 * <h2>Por que dibujados y no emoji ni PNG</h2>
 *
 * <p>Los <b>emoji no valen</b>: en Windows muchos se pintan en blanco y negro o
 * directamente como dos letras — es el mismo motivo por el que existe
 * {@link FlagIcon}. Y siete PNG habria que mantenerlos, reescalarlos para cada
 * densidad de pantalla y meterlos en el zip. Cada icono de aqui son cuatro o
 * cinco figuras, se tinen con un parametro y escalan solos.
 *
 * <h2>Se reconocen por la SILUETA</h2>
 *
 * <p>Principio 3 del las notas de diseño: <i>el estado se ve, no se lee</i>. Un nodo del
 * mapa se tiene que poder identificar de un vistazo desde el otro lado de la
 * pantalla, asi que lo que separa a una calavera de un cofre es su contorno, no
 * un detalle interior ni un color: las siete siluetas son distintas <b>en
 * negro</b>.
 *
 * <p>Todos se dibujan dentro de un cuadrado de {@code size} x {@code size} con
 * el origen en su centro, para que colocarlos sea poner el centro y ya.
 */
public final class AscentIcon {

    private AscentIcon() {
    }

    /**
     * El icono de ese tipo de nodo.
     *
     * @param kind  que hay en el nodo
     * @param size  el lado del cuadrado en el que cabe
     * @param color la tinta con la que se dibuja
     * @param hole  el color de los HUECOS (ojos de la calavera, cerradura del
     *              cofre, joyas de la corona). Tiene que ser <b>el color de la
     *              chapa sobre la que se pinta</b>, no el del papel: con el del
     *              papel, un hueco claro sobre una chapa gris no es un hueco —
     *              es una mancha, y la calavera se ve como un borron blanco.
     *              Fue el primer fallo que se vio en la primera captura
     */
    public static Group of(final AscentNode.Kind kind, final double size,
                           final Color color, final Color hole) {
        final Group g = new Group();
        switch (kind) {
            case COMBAT:
                swords(g, size, color);
                break;
            case ELITE:
                skull(g, size, color, hole);
                break;
            case REST:
                campfire(g, size, color);
                break;
            case SHOP:
                pouch(g, size, color, hole);
                break;
            case TREASURE:
                chest(g, size, color, hole);
                break;
            case EVENT:
                question(g, size, color);
                break;
            case BOSS:
            default:
                crown(g, size, color, hole);
                break;
        }
        for (final javafx.scene.Node n : g.getChildren()) {
            if (n instanceof Shape) {
                ((Shape) n).setStrokeLineCap(StrokeLineCap.ROUND);
            }
        }
        g.setMouseTransparent(true);
        return g;
    }

    // ------------------------------------------------------------------

    /** Combate: dos espadas cruzadas. */
    private static void swords(final Group g, final double s, final Color c) {
        for (final int dir : new int[]{1, -1}) {
            final Line blade = new Line(-s * 0.34 * dir, s * 0.36, s * 0.34 * dir, -s * 0.38);
            blade.setStroke(c);
            blade.setStrokeWidth(s * 0.11);
            // La punta: lo que hace que se lea como espada y no como aspa.
            final Polygon tip = new Polygon(
                    s * 0.34 * dir, -s * 0.44,
                    s * 0.20 * dir, -s * 0.26,
                    s * 0.44 * dir, -s * 0.28);
            tip.setFill(c);
            // La guarda, atravesada.
            final Line guard = new Line(-s * 0.32 * dir, s * 0.10, -s * 0.08 * dir, s * 0.30);
            guard.setStroke(c);
            guard.setStrokeWidth(s * 0.09);
            g.getChildren().addAll(blade, tip, guard);
        }
    }

    /** Elite: una calavera. */
    private static void skull(final Group g, final double s, final Color c, final Color hole) {
        final Ellipse dome = new Ellipse(0, -s * 0.08, s * 0.38, s * 0.36);
        dome.setFill(c);
        final Rectangle jaw = new Rectangle(-s * 0.24, s * 0.16, s * 0.48, s * 0.24);
        jaw.setArcWidth(s * 0.16);
        jaw.setArcHeight(s * 0.16);
        jaw.setFill(c);
        final Ellipse le = new Ellipse(-s * 0.16, -s * 0.10, s * 0.11, s * 0.13);
        final Ellipse re = new Ellipse(s * 0.16, -s * 0.10, s * 0.11, s * 0.13);
        le.setFill(hole);
        re.setFill(hole);
        final Polygon nose = new Polygon(0, s * 0.02, -s * 0.06, s * 0.14, s * 0.06, s * 0.14);
        nose.setFill(hole);
        final Line teeth = new Line(-s * 0.16, s * 0.16, -s * 0.16, s * 0.40);
        final Line teeth2 = new Line(s * 0.16, s * 0.16, s * 0.16, s * 0.40);
        teeth.setStroke(hole);
        teeth2.setStroke(hole);
        teeth.setStrokeWidth(s * 0.06);
        teeth2.setStrokeWidth(s * 0.06);
        g.getChildren().addAll(dome, jaw, le, re, nose, teeth, teeth2);
    }

    /** Descanso: una hoguera. */
    private static void campfire(final Group g, final double s, final Color c) {
        for (final int dir : new int[]{1, -1}) {
            final Line log = new Line(-s * 0.36 * dir, s * 0.24, s * 0.30 * dir, s * 0.40);
            log.setStroke(c);
            log.setStrokeWidth(s * 0.11);
            g.getChildren().add(log);
        }
        // La llama: dos lobulos y una punta. Asimetrica a proposito — una llama
        // simetrica parece una gota.
        final Polygon flame = new Polygon(
                0, -s * 0.46,
                s * 0.26, -s * 0.06,
                s * 0.20, s * 0.18,
                -s * 0.20, s * 0.18,
                -s * 0.26, -s * 0.10,
                -s * 0.06, -s * 0.20);
        flame.setFill(c);
        g.getChildren().add(flame);
    }

    /**
     * Tienda: un puesto de mercado con su toldo.
     *
     * <p>La primera version era una bolsa de monedas y <b>en la captura se veia
     * como una taza</b>: al lado del cofre, dos bultos redondeados con algo
     * encima. La silueta de un toldo — triangulo con el borde ondulado y dos
     * postes — no se parece a nada mas del mapa, que es lo unico que se le pide
     * a un icono de estos.
     */
    private static void pouch(final Group g, final double s, final Color c, final Color hole) {
        // Los dos postes.
        for (final int dir : new int[]{1, -1}) {
            final Line post = new Line(s * 0.30 * dir, -s * 0.02, s * 0.30 * dir, s * 0.42);
            post.setStroke(c);
            post.setStrokeWidth(s * 0.09);
            g.getChildren().add(post);
        }
        // El toldo.
        final Polygon roof = new Polygon(
                0, -s * 0.44,
                s * 0.46, -s * 0.02,
                -s * 0.46, -s * 0.02);
        roof.setFill(c);
        g.getChildren().add(roof);
        // Y el borde ondulado, que es lo que lo hace un toldo y no un tejado.
        for (int i = -1; i <= 1; i++) {
            final Circle scallop = new Circle(i * s * 0.30, -s * 0.02, s * 0.15);
            scallop.setFill(c);
            g.getChildren().add(scallop);
        }
        // El mostrador, en hueco.
        final Rectangle counter = new Rectangle(-s * 0.24, s * 0.14, s * 0.48, s * 0.10);
        counter.setFill(hole);
        g.getChildren().add(counter);
    }

    /** Tesoro: un cofre. */
    private static void chest(final Group g, final double s, final Color c, final Color hole) {
        final Rectangle body = new Rectangle(-s * 0.38, -s * 0.02, s * 0.76, s * 0.38);
        body.setArcWidth(s * 0.10);
        body.setArcHeight(s * 0.10);
        body.setFill(c);
        // La tapa abombada: sin ella es una caja.
        final Ellipse lid = new Ellipse(0, -s * 0.02, s * 0.38, s * 0.24);
        lid.setFill(c);
        final Rectangle cut = new Rectangle(-s * 0.38, -s * 0.02, s * 0.76, s * 0.26);
        cut.setFill(c);
        final Rectangle lock = new Rectangle(-s * 0.08, -s * 0.10, s * 0.16, s * 0.22);
        lock.setArcWidth(s * 0.06);
        lock.setArcHeight(s * 0.06);
        lock.setFill(hole);
        // La juntura entre tapa y cuerpo. Sin ella el cofre es un bulto: es
        // la linea la que dice que eso se abre.
        final Line band = new Line(-s * 0.40, s * 0.02, s * 0.40, s * 0.02);
        band.setStroke(hole);
        band.setStrokeWidth(s * 0.07);
        g.getChildren().addAll(lid, cut, body, band, lock);
    }

    /** Evento: una interrogacion. Es el unico signo que se lee igual en los diez idiomas. */
    private static void question(final Group g, final double s, final Color c) {
        final javafx.scene.text.Text q = new javafx.scene.text.Text("?");
        q.setFill(c);
        q.setStyle("-fx-font-weight: 900;");
        q.setFont(javafx.scene.text.Font.font(s * 1.05));
        // Centrado a mano: el origen de un Text es su linea base, no su centro.
        q.setLayoutX(-q.getLayoutBounds().getWidth() / 2);
        q.setLayoutY(q.getLayoutBounds().getHeight() * 0.36);
        g.getChildren().add(q);
    }

    /** Jefe: una corona. */
    private static void crown(final Group g, final double s, final Color c, final Color hole) {
        final Polygon crown = new Polygon(
                -s * 0.42, s * 0.16,
                -s * 0.34, -s * 0.34,
                -s * 0.16, -s * 0.04,
                0, -s * 0.44,
                s * 0.16, -s * 0.04,
                s * 0.34, -s * 0.34,
                s * 0.42, s * 0.16);
        crown.setFill(c);
        final Rectangle band = new Rectangle(-s * 0.42, s * 0.16, s * 0.84, s * 0.18);
        band.setArcWidth(s * 0.08);
        band.setArcHeight(s * 0.08);
        band.setFill(c);
        // Las tres joyas, en hueco: es lo que la separa de una montanya.
        for (final double x : new double[]{-s * 0.22, 0, s * 0.22}) {
            final Circle gem = new Circle(x, s * 0.25, s * 0.05);
            gem.setFill(hole);
            g.getChildren().add(gem);
        }
        g.getChildren().addAll(0, java.util.List.of(crown, band));
    }
}
