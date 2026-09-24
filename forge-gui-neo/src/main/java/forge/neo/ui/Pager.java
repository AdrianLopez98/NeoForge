package forge.neo.ui;

import forge.neo.NeoText;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * Pasar paginas.
 *
 * <p>Las rejillas de esta interfaz cortan en unas decenas de elementos porque
 * cada uno es una carta con su imagen y pintar cientos de golpe deja la pantalla
 * pegada. Pero cortar sin mas es mentir: la lista decia "40 de 173" y no habia
 * ninguna forma de ver los otros 133.
 *
 * <p>Se esconde solo cuando todo cabe en una pagina, para no ensuciar el caso
 * normal.
 *
 * <p>Regla que sale de aqui, y que vale para el resto del proyecto: <b>si algo
 * se recorta, tiene que haber forma de llegar a lo recortado.</b>
 */
public class Pager extends HBox {

    private final Button prev = new Button("‹");
    private final Button next = new Button("›");
    private final Label label = new Label();
    private final Runnable onChange;

    private int page;
    private int pageSize;
    private int total;

    public Pager(final int pageSize, final Runnable onChange) {
        this.pageSize = pageSize;
        this.onChange = onChange;

        setSpacing(6);
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("pager");

        label.getStyleClass().add("dialog-counter");

        prev.getStyleClass().add("segment");
        prev.setMinWidth(Region.USE_PREF_SIZE);
        prev.setOnAction(e -> go(page - 1));

        next.getStyleClass().add("segment");
        next.setMinWidth(Region.USE_PREF_SIZE);
        next.setOnAction(e -> go(page + 1));

        getChildren().addAll(prev, label, next);
    }

    /**
     * Dice cuantos elementos hay en total y coloca la pagina.
     *
     * <p>Se llama cada vez que cambia el filtro o la busqueda. La pagina se
     * recorta al ultimo valor valido: si estabas en la 5 y ahora solo hay dos,
     * quedarte en la 5 dejaria la rejilla vacia sin explicacion.
     */
    public void setTotal(final int total) {
        this.total = total;
        final int last = Math.max(0, pageCount() - 1);
        if (page > last) {
            page = last;
        }
        refresh();
    }

    /** Vuelve a la primera pagina (al cambiar de pestanya o de busqueda). */
    public void reset() {
        page = 0;
    }

    private void go(final int target) {
        final int last = Math.max(0, pageCount() - 1);
        final int clamped = Math.max(0, Math.min(last, target));
        if (clamped == page) {
            return;
        }
        page = clamped;
        refresh();
        onChange.run();
    }

    private void refresh() {
        final int pages = pageCount();
        final boolean needed = pages > 1;
        setVisible(needed);
        setManaged(needed);
        if (!needed) {
            return;
        }
        label.setText(NeoText.get("pager.page", page + 1, pages));
        prev.setDisable(page == 0);
        next.setDisable(page >= pages - 1);
    }

    private int pageCount() {
        return total <= 0 ? 1 : (total + pageSize - 1) / pageSize;
    }

    /** El primer indice de la pagina actual. */
    public int from() {
        return page * pageSize;
    }

    /** El indice siguiente al ultimo de la pagina actual. */
    public int to() {
        return Math.min(total, from() + pageSize);
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(final int size) {
        this.pageSize = Math.max(1, size);
        refresh();
    }

    /** El alto de casilla mas grande visto hasta ahora (ver {@link #fitTo}). Solo crece. */
    private double fitTileH;
    /** Filas como mucho, mientras el visor no cambie (ver {@link #fitTo}). Solo baja. */
    private int fitRowCap = Integer.MAX_VALUE;
    private double fitViewW;
    private double fitViewH;

    /**
     * Hace que la pagina sea <b>lo que cabe</b> en el visor, y no un numero fijo.
     *
     * <p>Con 24 fijas, las casillas de texto (las expansiones del sellado y del
     * draft) llenaban un tercio de la pantalla en 1080p y aun menos en 2K, y el
     * resto se quedaba vacio con "Pagina 1 de 9" debajo (24-09-2026). Cuenta
     * columnas con el ancho de casilla y filas con el alto de la mas alta que
     * se haya pintado, y conserva el primer elemento a la vista al recalcular.
     *
     * <p>El alto medido solo crece: si pudiera bajar, una pagina de casillas de
     * una linea daria mas filas, la siguiente traeria casillas de dos, bajaria
     * de nuevo... y la rejilla no pararia de repaginarse. Y como las casillas
     * no miden todas lo mismo (hay nombres de tres lineas), si aun asi la
     * pagina pintada no cabe se quita una fila, y esa rebaja dura mientras el
     * visor no cambie de tamanyo: por lo mismo, solo baja.
     *
     * @param tileW ancho de una casilla, en px (con su padding)
     * @param tileH alto estimado hasta que se pinte la primera
     * @param min   nunca menos que esto por pagina
     */
    public void fitTo(final javafx.scene.control.ScrollPane scroll,
                      final javafx.scene.layout.FlowPane grid,
                      final double tileW, final double tileH, final int min) {
        fitTileH = tileH;
        final Runnable fit = () -> {
            final javafx.geometry.Bounds b = scroll.getViewportBounds();
            if (b.getWidth() <= 0 || b.getHeight() <= 0) {
                return;
            }
            for (final javafx.scene.Node n : grid.getChildren()) {
                if (n instanceof Region r && r.getHeight() > fitTileH) {
                    fitTileH = r.getHeight();
                }
            }
            // Solo un cambio de tamanyo DE VERDAD reinicia el tope de filas. La
            // barra de desplazamiento que aparece al desbordar estrecha el
            // visor unos px; si eso contara, desbordar quitaria la barra,
            // quitarla devolveria la fila, y la rejilla no pararia nunca.
            if (Math.abs(b.getHeight() - fitViewH) > 1 || Math.abs(b.getWidth() - fitViewW) > 40) {
                fitViewW = b.getWidth();
                fitViewH = b.getHeight();
                fitRowCap = Integer.MAX_VALUE;
            }
            final javafx.geometry.Insets pad = grid.getPadding();
            // Las columnas, con el ancho del ULTIMO cambio de verdad y sitio para
            // la barra: con el ancho de cada momento, la barra que aparece y
            // desaparece haria bailar el numero de columnas.
            final int cols = Math.max(1, (int) ((fitViewW - 16 - pad.getLeft() - pad.getRight()
                    + grid.getHgap()) / (tileW + grid.getHgap())));
            final int rows = Math.max(1, (int) ((b.getHeight() - pad.getTop() - pad.getBottom()
                    + grid.getVgap()) / (fitTileH + grid.getVgap())));
            int shownRows = Math.min(rows, fitRowCap);
            // La pagina pintada no cabe (una fila de casillas mas altas): una
            // fila menos, y ya no se vuelve a subir con este visor.
            if (grid.getChildren().size() >= pageSize && grid.getHeight() > b.getHeight() + 1
                    && pageSize / cols > 1) {
                fitRowCap = Math.min(shownRows, pageSize / cols) - 1;
                shownRows = fitRowCap;
            }
            final int size = Math.max(min, cols * Math.max(1, shownRows));
            if (size == pageSize) {
                return;
            }
            final int first = from();
            pageSize = size;
            page = first / size;
            setTotal(total);
            onChange.run();
        };
        scroll.viewportBoundsProperty().addListener((o, was, is) -> fit.run());
        // Despues de pintar ya se sabe cuanto mide de verdad una casilla.
        grid.heightProperty().addListener((o, was, is) -> fit.run());
    }
}
