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
 *
 * <p>Y dos cosas mas que pidio Discord el 09-10-2026, con captura de un evento
 * de Ascenso (<i>"put the cards to pick in the middle of the screen ... it zooms
 * but you end up losing information"</i>):
 * <ul>
 *   <li><b>Sitio para la carta con el raton encima</b>
 *       ({@code CardNode.hoverRoomFor}), que crece y sube: sin el, el visor le
 *       cortaba la cabeza a la primera fila — justo el nombre. Es el mismo
 *       arreglo que ya tenian el visor de zonas y los dialogos.</li>
 *   <li><b>Centrada en vertical si cabe</b>: con tres cartas, la rejilla se
 *       quedaba pegada arriba con media pantalla vacia debajo.</li>
 * </ul>
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

    /**
     * Engancha la rejilla al visor: se recalcula cada vez que el visor cambia de
     * tamanyo. Si la rejilla es el contenido directo del visor, la mete en una
     * caja del alto del visor para que quede centrada cuando cabe.
     */
    static void install(final ScrollPane scroll, final FlowPane grid,
                        final double min, final double max) {
        if (scroll.getContent() == grid) {
            final javafx.scene.layout.StackPane holder = new javafx.scene.layout.StackPane(grid);
            // Un pixel menos que el visor: igualito, el redondeo saca a veces
            // la barra de desplazamiento por medio pixel.
            holder.minHeightProperty().bind(javafx.beans.binding.Bindings.createDoubleBinding(
                    () -> Math.max(0, scroll.getViewportBounds().getHeight() - 1),
                    scroll.viewportBoundsProperty()));
            scroll.setContent(holder);
        }
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
            // asome la barra de desplazamiento por medio pixel. Y el sitio de
            // la carta ampliada, que depende del ancho: se mide sin el, se
            // calcula ese sitio y se vuelve a medir quitandolo.
            double c = width(cards.size(), b.getWidth() - 4, b.getHeight() - 6,
                    grid.getHgap(), min, max);
            javafx.geometry.Insets room = CardNode.hoverRoomFor(c);
            c = width(cards.size(), b.getWidth() - 4 - room.getLeft() - room.getRight(),
                    b.getHeight() - 6 - room.getTop() - room.getBottom(), grid.getHgap(), min, max);
            room = CardNode.hoverRoomFor(c);
            if (!room.equals(grid.getPadding())) {
                grid.setPadding(room);
            }
            for (final CardNode n : cards) {
                if (Math.abs(n.getCardWidth() - c) > 1) {
                    n.setCardWidth(c);
                }
            }
        };
        scroll.viewportBoundsProperty().addListener((o, was, is) -> fit.run());
    }
}
