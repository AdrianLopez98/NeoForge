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
}
