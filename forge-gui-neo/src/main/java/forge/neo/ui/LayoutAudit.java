package forge.neo.ui;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.Region;

/**
 * Lo que no cabe: con {@code -Dneo.layout.overflow=true}, al poco de salir la
 * pantalla se escriben en el registro los nodos mas anchos que la ventana o que
 * su contenedor, con su ancho minimo.
 *
 * <p>Nacio con la Steam Deck (Discord, 19-10-2026): a 1280x800 y escala 130%
 * la pantalla de Commander y los Ajustes se salian por los dos lados. JavaFX
 * no avisa de eso: un hijo con un minimo mayor que su padre simplemente se
 * centra y se corta. Esto dice QUIEN es.
 */
public final class LayoutAudit {

    private LayoutAudit() {
    }

    /** Si esta la bandera, mira {@code root} cuando entre en escena. */
    public static void installIfAsked(final Region root, final String what) {
        if (!Boolean.getBoolean("neo.layout.overflow")) {
            return;
        }
        root.sceneProperty().addListener((obs, was, now) -> {
            if (now == null) {
                return;
            }
            final javafx.animation.PauseTransition later =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            later.setOnFinished(e -> report(root, what));
            later.play();
        });
    }

    private static void report(final Region root, final String what) {
        final double sceneW = root.getScene() == null ? Double.NaN : root.getScene().getWidth();
        System.out.printf(java.util.Locale.ROOT, "[desborde] %s: ventana %.0f, raiz %.0f (min %.0f)%n",
                what, sceneW, root.getWidth(), root.minWidth(-1));
        walk(root, 0, sceneW);
    }

    private static void walk(final Node node, final int depth, final double sceneW) {
        if (!(node instanceof Parent p) || depth > 14) {
            return;
        }
        for (final Node child : p.getChildrenUnmodifiable()) {
            if (!child.isVisible() || !child.isManaged() || !(child instanceof Region r)) {
                continue;
            }
            final double min = r.minWidth(-1);
            final double parentW = node instanceof Region pr ? pr.getWidth() : Double.NaN;
            if (min > parentW + 1 || r.getWidth() > sceneW + 1) {
                System.out.printf(java.util.Locale.ROOT, "[desborde] %s%s  ancho %.0f  min %.0f  padre %.0f%n",
                        "  ".repeat(depth), describe(r), r.getWidth(), min, parentW);
                // Y de que esta hecho ese minimo: sus hijos, uno por uno.
                for (final Node part : r.getChildrenUnmodifiable()) {
                    if (part instanceof Region pr && pr.isManaged()) {
                        System.out.printf(java.util.Locale.ROOT, "[desborde] %s  - %s  min %.0f  pref %.0f%n",
                                "  ".repeat(depth), describe(pr), pr.minWidth(-1), pr.prefWidth(-1));
                    }
                }
            }
            walk(child, depth + 1, sceneW);
        }
    }

    private static String describe(final Region r) {
        final StringBuilder sb = new StringBuilder(r.getClass().getSimpleName());
        if (r.getId() != null) {
            sb.append('#').append(r.getId());
        }
        if (!r.getStyleClass().isEmpty()) {
            sb.append(' ').append(r.getStyleClass());
        }
        if (r instanceof javafx.scene.control.Labeled l && l.getText() != null) {
            sb.append(" \"").append(l.getText().length() > 40 ? l.getText().substring(0, 40) : l.getText()).append('"');
        }
        return sb.toString();
    }
}
