package forge.neo.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextBoundsType;

/**
 * Los iconos que Forge dibuja a mano en su mapa de Rogue Commander, con sus
 * mismas coordenadas y colores, en JavaFX: la llama (Wrathful) y el pentagrama
 * (Cursed) de {@code NodePlaneboundPanel}, y el corazon, la moneda, el cofre y
 * el pergamino de los nodos redondos ({@code NodeSanctumPanel},
 * {@code NodeBazaarPanel}, {@code NodeChestPanel}, {@code NodeEventPanel}).
 *
 * <p>Todo se mide como en Forge (pixeles de 1080p) y se multiplica por
 * {@code s}, lo que crece la interfaz. Si Card-Forge cambia un dibujo,
 * {@code roguecheck} lo dice ({@code RogueSources}).
 */
final class RogueNodeIcons {

    private RogueNodeIcons() {
    }

    /** {@code NodePlaneboundPanel.createFlameIcon(14, 18)}. */
    static Node flame(final double s) {
        final double w = 14 * s;
        final double h = 18 * s;
        final double cx = w / 2.0;
        final Path outer = new Path(new MoveTo(cx, 0),
            new CubicCurveTo(cx + w * 0.1, h * 0.2, cx + w * 0.55, h * 0.4, cx + w * 0.45, h * 0.7),
            new CubicCurveTo(cx + w * 0.35, h * 0.9, cx + w * 0.1, h, cx, h),
            new CubicCurveTo(cx - w * 0.1, h, cx - w * 0.35, h * 0.9, cx - w * 0.45, h * 0.7),
            new CubicCurveTo(cx - w * 0.55, h * 0.4, cx - w * 0.1, h * 0.2, cx, 0));
        outer.setFill(Color.rgb(255, 80, 0));
        outer.setStroke(null);
        final Path inner = new Path(new MoveTo(cx, h * 0.3),
            new CubicCurveTo(cx + w * 0.05, h * 0.45, cx + w * 0.25, h * 0.55, cx + w * 0.2, h * 0.75),
            new CubicCurveTo(cx + w * 0.1, h * 0.9, cx, h * 0.95, cx, h * 0.95),
            new CubicCurveTo(cx, h * 0.95, cx - w * 0.1, h * 0.9, cx - w * 0.2, h * 0.75),
            new CubicCurveTo(cx - w * 0.25, h * 0.55, cx - w * 0.05, h * 0.45, cx, h * 0.3));
        inner.setFill(Color.rgb(255, 200, 50));
        inner.setStroke(null);
        return box(w, h, outer, inner);
    }

    /** {@code NodePlaneboundPanel.createPentagramIcon(14, 18)}. */
    static Node pentagram(final double s) {
        final double w = 14 * s;
        final double h = 18 * s;
        final double cx = w / 2.0;
        final double cy = h / 2.0;
        final double r = h / 2.0 - 1 * s;
        final double[] px = new double[5];
        final double[] py = new double[5];
        for (int i = 0; i < 5; i++) {
            final double angle = Math.PI / 2 + i * 2 * Math.PI / 5;
            px[i] = cx + r * Math.cos(angle);
            py[i] = cy - r * Math.sin(angle);
        }
        final int[] order = {0, 2, 4, 1, 3};
        final Path star = new Path(new MoveTo(px[order[0]], py[order[0]]));
        for (int i = 1; i < 5; i++) {
            star.getElements().add(new LineTo(px[order[i]], py[order[i]]));
        }
        star.getElements().add(new ClosePath());
        // WIND_NON_ZERO, como en Forge: el centro tambien relleno.
        star.setFillRule(FillRule.NON_ZERO);
        star.setFill(Color.rgb(80, 10, 120));
        star.setStroke(null);
        final Group lines = new Group();
        for (int i = 0; i < 5; i++) {
            final int j = (i + 2) % 5;
            final Line l = new Line(px[i], py[i], px[j], py[j]);
            l.setStroke(Color.rgb(200, 130, 255));
            l.setStrokeWidth(1.5 * s);
            lines.getChildren().add(l);
        }
        return box(w, h, star, lines);
    }

    /** {@code NodeSanctumPanel}: un corazon rojo, Serif de 35, un poco por debajo del centro. */
    static Node heart(final double s) {
        final Text t = new Text("♥");
        t.setFont(Font.font("Serif", 35 * s));
        t.setFill(Color.rgb(220, 53, 69));
        t.setBoundsType(TextBoundsType.VISUAL);
        t.setTranslateY(3 * s);
        return t;
    }

    /** {@code NodeBazaarPanel.drawCoin}, de 30. */
    static Node coin(final double s) {
        final double size = 30 * s;
        final double c = size / 2;
        final Circle gold = new Circle(c, c, size / 2, Color.rgb(255, 215, 0));
        final Circle rim = new Circle(c, c, (size - 10 * s) / 2);
        rim.setFill(null);
        rim.setStroke(Color.rgb(218, 165, 32));
        rim.setStrokeWidth(3 * s);
        final Text dollar = new Text("$");
        dollar.setFont(Font.font("Arial", FontWeight.BOLD, 15 * s));
        dollar.setFill(Color.rgb(139, 90, 0));
        dollar.setBoundsType(TextBoundsType.VISUAL);
        final double tw = dollar.getLayoutBounds().getWidth();
        final double th = dollar.getLayoutBounds().getHeight();
        dollar.setX(c - tw / 2 - dollar.getLayoutBounds().getMinX());
        dollar.setY(c - th / 2 - dollar.getLayoutBounds().getMinY());
        return box(size, size, gold, rim, dollar);
    }

    /** {@code NodeChestPanel.drawChest}, de 32 de ancho. */
    static Node chest(final double s) {
        final double bodyWidth = 32 * s;
        final double bodyHeight = bodyWidth * 0.7;
        final double cx = bodyWidth / 2;
        final double cy = bodyHeight / 2 + 2 * s;
        final Rectangle body = new Rectangle(cx - bodyWidth / 2, cy - bodyHeight / 2, bodyWidth, bodyHeight);
        body.setArcWidth(8 * s);
        body.setArcHeight(8 * s);
        body.setFill(Color.rgb(139, 90, 43));
        final double latchWidth = 12 * s;
        final double latchHeight = 18 * s;
        final Rectangle latch = new Rectangle(cx - latchWidth / 2, cy - latchHeight / 2, latchWidth, latchHeight);
        latch.setFill(Color.rgb(255, 215, 0));
        final Circle keyhole = new Circle(cx, cy + 1 * s, 3 * s, Color.BLACK);
        final Line band = new Line(cx - bodyWidth / 2, cy, cx + bodyWidth / 2, cy);
        band.setStroke(Color.rgb(80, 50, 20));
        band.setStrokeWidth(2 * s);
        return box(bodyWidth, bodyHeight + 4 * s, body, latch, keyhole, band);
    }

    /** {@code NodeEventPanel.drawScroll}, de 32 de ancho. */
    static Node scroll(final double s) {
        final double scrollWidth = 32 * s;
        final double scrollHeight = scrollWidth * 0.8;
        final double cx = scrollWidth / 2 + 2 * s;
        final double cy = scrollHeight / 2 + 5 * s;
        final Rectangle parchment = new Rectangle(cx - scrollWidth / 2, cy - scrollHeight / 2, scrollWidth, scrollHeight);
        parchment.setArcWidth(5 * s);
        parchment.setArcHeight(5 * s);
        parchment.setFill(Color.rgb(245, 222, 179));
        final Rectangle top = new Rectangle(cx - scrollWidth / 2 - 2 * s, cy - scrollHeight / 2 - 5 * s,
            scrollWidth + 4 * s, 8 * s);
        top.setFill(Color.rgb(210, 180, 140));
        final Rectangle bottom = new Rectangle(cx - scrollWidth / 2 - 2 * s, cy + scrollHeight / 2 - 3 * s,
            scrollWidth + 4 * s, 8 * s);
        bottom.setFill(Color.rgb(210, 180, 140));
        final Text mark = new Text("?");
        mark.setFont(Font.font("Arial", FontWeight.BOLD, scrollWidth * 0.7));
        mark.setFill(Color.rgb(139, 69, 19));
        mark.setBoundsType(TextBoundsType.VISUAL);
        mark.setX(cx - mark.getLayoutBounds().getWidth() / 2 - mark.getLayoutBounds().getMinX());
        mark.setY(cy - mark.getLayoutBounds().getHeight() / 2 - mark.getLayoutBounds().getMinY());
        final Rectangle edge = new Rectangle(cx - scrollWidth / 2, cy - scrollHeight / 2, scrollWidth, scrollHeight);
        edge.setArcWidth(5 * s);
        edge.setArcHeight(5 * s);
        edge.setFill(null);
        edge.setStroke(Color.rgb(210, 180, 140));
        edge.setStrokeWidth(2 * s);
        return box(scrollWidth + 4 * s, scrollHeight + 10 * s, parchment, top, bottom, mark, edge);
    }

    /** Un lienzo de tamanyo fijo con las formas dentro, que no se mide por lo que sobresale. */
    private static Pane box(final double w, final double h, final Node... parts) {
        final Pane p = new Pane(parts);
        p.setMinSize(w, h);
        p.setPrefSize(w, h);
        p.setMaxSize(w, h);
        p.setMouseTransparent(true);
        return p;
    }
}
