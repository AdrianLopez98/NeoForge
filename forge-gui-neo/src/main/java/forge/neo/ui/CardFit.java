package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.neo.card.CardNode;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;

/**
 * Una rejilla de cartas que llena su visor en vez de quedarse en un tamanyo fijo.
 *
 * <p>Las vistas de mazo de Ascenso (el descanso, el resumen, el visor del mapa,
 * los eventos) pintaban sus treinta cartas a una fraccion fija del ancho de la
 * mesa: tres filas arriba y mas de media pantalla vacia debajo, en 1080p y peor
 * en 2K (24-09-2026). Aqui se busca el ancho MAS GRANDE con el que caben todas
 * sin desplazarse, entre un suelo — el tamanyo de siempre, asi que nunca sale
 * mas pequenya que antes — y un techo, para que un mazo de cinco cartas no las
 * pinte como carteles. Si ni con el suelo caben, se queda en el suelo y se
 * desplaza, que es lo que ya pasaba.
 */
final class CardFit {

    private CardFit() {
    }

    /** El ancho mas grande entre {@code min} y {@code max} con el que caben {@code n} cartas. */
    static double width(final int n, final double w, final double h, final double gap,
                        final double min, final double max) {
        if (n <= 0 || w <= 0 || h <= 0 || max <= min) {
            return min;
        }
        for (double c = max; c > min; c -= 2) {
            final int cols = Math.max(1, (int) ((w + gap) / (c + gap)));
            final int rows = (n + cols - 1) / cols;
            if (rows * (c * CardNode.ASPECT + gap) - gap <= h) {
                return c;
            }
        }
        return min;
    }

    /** Engancha la rejilla al visor: se recalcula cada vez que el visor cambia de tamanyo. */
    static void install(final ScrollPane scroll, final FlowPane grid,
                        final double min, final double max) {
        final Runnable fit = () -> {
            final Bounds b = scroll.getViewportBounds();
            final List<CardNode> cards = new ArrayList<>();
            for (final Node n : grid.getChildren()) {
                if (n instanceof CardNode c) {
                    cards.add(c);
                }
            }
            if (cards.isEmpty() || b.getWidth() <= 0 || b.getHeight() <= 0) {
                return;
            }
            // Unos px de margen: el borde y la sombra de la carta, y que no
            // asome la barra de desplazamiento por medio pixel.
            final double c = width(cards.size(), b.getWidth() - 4, b.getHeight() - 6,
                    grid.getHgap(), min, max);
            for (final CardNode n : cards) {
                if (Math.abs(n.getCardWidth() - c) > 1) {
                    n.setCardWidth(c);
                }
            }
        };
        scroll.viewportBoundsProperty().addListener((o, was, is) -> fit.run());
    }
}
