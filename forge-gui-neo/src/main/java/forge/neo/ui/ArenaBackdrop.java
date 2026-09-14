package forge.neo.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/** Original vector tabletop. Decorative only: never receives input. */
final class ArenaBackdrop extends Region {
    private final Canvas canvas = new Canvas();

    ArenaBackdrop() {
        setMouseTransparent(true);
        getChildren().add(canvas);
        getStyleClass().add("arena-backdrop");
    }

    @Override protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.setWidth(w);
        canvas.setHeight(h);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        double cx = w * .5, cy = h * .46;
        // Quiet engraved rings and stone seams; no animation or image downloads.
        g.setLineWidth(1);
        for (int i = 0; i < 3; i++) {
            double rw = w * (.46 + i * .032), rh = h * (.53 + i * .036);
            g.setStroke(Color.web("#ba9863", .07 + i * .018));
            g.strokeOval(cx - rw / 2, cy - rh / 2, rw, rh);
        }
        g.setStroke(Color.web("#ba9863", .13));
        g.strokePolygon(new double[]{cx, cx + 28, cx, cx - 28},
                new double[]{cy - 36, cy, cy + 36, cy}, 4);
        g.setStroke(Color.web("#090d12", .45));
        for (int i = 1; i < 9; i++) {
            double x = w * i / 9;
            g.strokeLine(x, 0, cx + (x - cx) * .88, h * .17);
            g.strokeLine(x, h, cx + (x - cx) * .88, h * .83);
        }
        g.setStroke(Color.web("#bd9a62", .22));
        g.strokeRoundRect(12, 12, Math.max(0, w - 24), Math.max(0, h - 24), 36, 36);
        g.setLineWidth(2);
        for (double x : new double[]{30, w - 30}) {
            double sign = x < cx ? 1 : -1;
            g.strokeLine(x, 32, x + sign * 65, 32);
            g.strokeLine(x, h - 32, x + sign * 65, h - 32);
        }
    }
}
