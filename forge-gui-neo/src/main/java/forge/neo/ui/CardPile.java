package forge.neo.ui;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

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
 * <p>Al pasar el raton por una carta, la pila <b>se abre</b>: las de debajo
 * bajan lo justo para que la senyalada se vea entera. Antes la carta se pintaba
 * por delante de las de abajo, y como JavaFX tambien reparte el raton en ese
 * orden, tapaba sus franjas: no habia forma de pasar a la carta de justo debajo
 * (reportado jugando, 24-09-2026). Abriendo no se tapa nada, y la siguiente
 * queda pegada al borde inferior de la que estas leyendo. Es lo que hace Moxfield.
 *
 * <p>Al abrirse, el alto de la pila NO cambia (el desplazamiento va en
 * {@code translateY}, fuera del layout), asi que la columna y el scroll no
 * saltan con el raton. Y no hace falta reservar sitio: las franjas de las cartas
 * empujadas siguen cayendo dentro del alto de la pila cerrada; lo unico que se
 * sale por abajo es el cuerpo de la ultima, que ya estaba tapado de todos modos.
 * Reservarlo costaba cuatro cartas por columna.
 *
 * <p>Aviso de {@code Pane} (ya documentado en el proyecto): al sobrescribir
 * {@code layoutChildren} hay que llamar a {@code resize()} en cada hijo, porque
 * {@code Pane} deja de dimensionarlos por su cuenta.
 */
public class CardPile extends Pane {

    /** Que fraccion de cada carta tapada se sigue viendo. */
    private static final double PEEK = 0.19;

    /** Lo que tarda en abrirse o cerrarse (tokens de diseño: micro). */
    private static final Duration OPEN_TIME = Duration.millis(150);

    private final double cardWidth;
    private final double cardHeight;

    /** La carta senyalada; -1 = ninguna, la pila cerrada. */
    private int opened = -1;

    public CardPile(final double cardWidth, final double cardHeight) {
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        // Entre carta y carta no se cierra (se veria un parpadeo): solo al salir
        // de la pila entera.
        setOnMouseExited(e -> open(-1));
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

    /**
     * Abre la pila por debajo de {@code child}. La llama cada carta al entrarle
     * el raton; {@code null} la cierra.
     */
    public void openAt(final Node child) {
        open(child == null ? -1 : getChildren().indexOf(child));
    }

    private void open(final int index) {
        if (index == opened) {
            return;
        }
        opened = index;
        final double shift = cardHeight - step();
        for (int i = 0; i < getChildren().size(); i++) {
            final Node child = getChildren().get(i);
            final double target = index >= 0 && i > index ? shift : 0;
            if (child.getTranslateY() == target) {
                continue;
            }
            // En translateY y no en el layout: el alto de la pila no cambia y
            // la animacion parte de donde este.
            final Object running = child.getProperties().get(CardPile.class);
            if (running instanceof TranslateTransition) {
                ((TranslateTransition) running).stop();
            }
            final TranslateTransition move = new TranslateTransition(OPEN_TIME, child);
            move.setToY(target);
            move.setInterpolator(Interpolator.EASE_OUT);
            child.getProperties().put(CardPile.class, move);
            move.play();
        }
    }
}
