package forge.neo.ui;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

/**
 * Cartas apiladas dejando ver solo la franja de arriba de cada una.
 *
 * <p>Es la pila de la vista visual de Moxfield: la franja que queda a la vista
 * lleva el nombre y el coste, que es lo que hace falta para repasar un mazo, y
 * asi caben treinta criaturas en una columna sin perder el arte.
 *
 * <p>Se coloca a mano en vez de con un {@code VBox} de separacion negativa.
 * Un VBox con separacion negativa <i>parece</i> que funciona, pero calcula un
 * alto que no corresponde con lo que pinta, y las columnas acaban descuadradas
 * unas respecto a otras. Aqui el alto es exactamente el que ocupa la pila.
 *
 * <p>Aviso de {@code Pane} (ya documentado en el proyecto): al sobrescribir
 * {@code layoutChildren} hay que llamar a {@code resize()} en cada hijo, porque
 * {@code Pane} deja de dimensionarlos por su cuenta.
 */
public class CardPile extends Pane {

    /** Que fraccion de cada carta tapada se sigue viendo. */
    private static final double PEEK = 0.19;

    private final double cardWidth;
    private final double cardHeight;

    public CardPile(final double cardWidth, final double cardHeight) {
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
    }

    /** La fraccion visible de una carta tapada, para quien calcule cuantas caben. */
    public static double peek() {
        return PEEK;
    }

    /** Cuanto baja cada carta respecto a la anterior. */
    private double step() {
        return cardHeight * PEEK;
    }

    @Override
    protected double computePrefWidth(final double height) {
        return cardWidth;
    }

    @Override
    protected double computePrefHeight(final double width) {
        final int n = getChildren().size();
        return n == 0 ? 0 : (n - 1) * step() + cardHeight;
    }

    @Override
    protected double computeMinHeight(final double width) {
        return computePrefHeight(width);
    }

    @Override
    protected double computeMinWidth(final double height) {
        return cardWidth;
    }

    @Override
    protected void layoutChildren() {
        double y = 0;
        for (final Node child : getChildren()) {
            if (child instanceof Region) {
                ((Region) child).resize(cardWidth, cardHeight);
            }
            child.relocate(0, y);
            y += step();
        }
    }
}
