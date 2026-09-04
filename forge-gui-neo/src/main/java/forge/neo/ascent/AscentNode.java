package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Un nodo del mapa: donde esta, que es y a donde lleva.
 *
 * <p>Es un dato puro, sin nada de JavaFX: el mapa se genera y se recorre entero
 * desde {@code AscentCheck}, sin ventana. La pantalla solo lo dibuja.
 */
public final class AscentNode {

    /**
     * Que hay en un nodo.
     *
     * <p>El orden importa poco, pero el <b>nombre</b> si: se guarda tal cual en
     * la run, asi que renombrar una constante rompe las partidas guardadas.
     */
    public enum Kind {
        /** Duelo normal. Recompensa: elegir 1 de 3 cartas y creditos. */
        COMBAT,
        /** Rival duro con reliquia propia. Recompensa: reliquia. */
        ELITE,
        /** Curarte o quitar una carta del mazo. Solo una de las dos. */
        REST,
        /** Comprar reliquias, cartas y borrados. */
        SHOP,
        /** Una reliquia, gratis. */
        TREASURE,
        /** Texto con opciones. A veces sale mal. */
        EVENT,
        /** Archenemy: el rival tiene mazo de esquemas. Cierra el acto. */
        BOSS
    }

    private final int row;
    private final int col;
    private Kind kind;
    private final List<AscentNode> next = new ArrayList<>();
    private boolean cleared;

    AscentNode(final int row, final int col, final Kind kind) {
        this.row = row;
        this.col = col;
        this.kind = kind;
    }

    /** Fila, de 0 (abajo, donde se empieza) a {@link AscentMap#ROWS}-1 (el jefe). */
    public int getRow() {
        return row;
    }

    /** Columna, de 0 a {@link AscentMap#COLS}-1. */
    public int getCol() {
        return col;
    }

    public Kind getKind() {
        return kind;
    }

    void setKind(final Kind kind) {
        this.kind = kind;
    }

    /** Los nodos de la fila de arriba a los que se puede ir desde aqui. */
    public List<AscentNode> getNext() {
        return Collections.unmodifiableList(next);
    }

    void link(final AscentNode other) {
        if (!next.contains(other)) {
            next.add(other);
        }
    }

    /** Si ya se resolvio. */
    public boolean isCleared() {
        return cleared;
    }

    public void setCleared(final boolean cleared) {
        this.cleared = cleared;
    }

    /** La clave con la que se guarda en la run: {@code fila,columna}. */
    public String key() {
        return row + "," + col;
    }

    @Override
    public String toString() {
        return key() + ":" + kind;
    }
}
