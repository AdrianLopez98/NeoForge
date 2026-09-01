package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.neo.card.CardNode;
import javafx.scene.layout.Pane;

/**
 * La mano, en abanico curvo como en Arena.
 *
 * <p>Las cartas se solapan y se inclinan siguiendo un arco: cuanto mas lejos
 * del centro, mas rotacion y mas caida. Es lo que hace que una mano de 7 cartas
 * se lea de un vistazo en vez de parecer una fila de fichas.
 *
 * <p>El solapamiento se ajusta solo: con pocas cartas se separan, con muchas se
 * comprimen, y cuando ni comprimiendolas caben <b>encogen</b>, igual que la
 * mesa. La mano nunca se sale de la pantalla ni se mueve de sitio: es donde el
 * jugador tiene que poder clicar.
 */
public class HandFan extends Pane {

    /** Rotacion maxima de las cartas de los extremos, en grados. */
    private static final double MAX_TILT = 5.0;

    /** Cuanto cae la carta de los extremos respecto a la del centro. */
    private static final double ARC_DROP = 0.09;

    /**
     * Cuanto tiene que asomar de una carta tapada para seguir siendo una carta.
     *
     * <p>Un tercio: es lo que ocupa el nombre y el coste, que es por lo que se
     * reconoce una carta de la mano sin ampliarla.
     */
    private static final double MIN_STEP_RATIO = 0.32;

    /** Suelo al encoger. Mas pequena que esto ya no se distingue nada. */
    private static final double MIN_CARD_WIDTH = 34;

    /**
     * Margen a los lados.
     *
     * <p>Las de los extremos van inclinadas, y una carta girada asoma un poco
     * mas ancha que lo que mide. Sin este margen, las dos puntas del abanico
     * rozan el borde.
     */
    private static final double EDGE_PAD = 6;

    private final double cardWidth;
    private final List<CardNode> cards = new ArrayList<>();

    /**
     * El ancho con el que estan pintadas ahora mismo las cartas.
     *
     * <p>Encoger es cambiar diez propiedades por carta, y el reparto corre en
     * CADA aviso del motor. Se aplica solo cuando de verdad cambia.
     */
    private double appliedWidth;

    public HandFan(final double cardWidth) {
        this.cardWidth = cardWidth;
        setPickOnBounds(false);
        // Altura fija: la carta entera mas holgura para el arco y el hover.
        final double h = cardWidth * CardNode.ASPECT + cardWidth * 0.22;
        setPrefHeight(h);
        setMinHeight(h);
        setMaxHeight(h);
    }

    public void add(final CardNode node) {
        node.setCardWidth(cardWidth);
        cards.add(node);
        getChildren().add(node);
        // La carta nueva entra a tamano entero: hay que recalcular el encogido.
        appliedWidth = 0;
        requestLayout();
    }

    public void clearCards() {
        cards.clear();
        getChildren().clear();
        appliedWidth = 0;
    }

    public List<CardNode> getCards() {
        return cards;
    }

    @Override
    protected void layoutChildren() {
        final int n = cards.size();
        if (n == 0) {
            return;
        }
        final double available = Math.max(MIN_CARD_WIDTH, getWidth() - EDGE_PAD * 2);

        // Tres escalones, y hacen falta los tres. Con una mano sin limite
        // (Reliquary Tower y compania) se llega facil a 30 cartas, y el reparto
        // de antes se plantaba en el primero: el suelo de solape ganaba, el
        // abanico salia MAS ancho que el hueco y x0 se iba a negativo, o sea
        // que las cartas de los dos extremos se salian de la pantalla.
        //
        // Mover la mano no es una opcion: es donde el jugador tiene que poder
        // clicar. Apelotonadas si, fuera nunca.
        double w = cardWidth;
        double step = w * 1.04;

        if (step * (n - 1) + w > available) {
            // 1) Apretarlas hasta el solape minimo legible.
            step = Math.max(w * MIN_STEP_RATIO, (available - w) / (n - 1));

            // 2) Y si ni asi caben, ENCOGER la carta. Es lo que hace la mesa
            //    cuando se llena, y es lo correcto: lo que no cabe, no cabe.
            if (step * (n - 1) + w > available) {
                w = Math.max(MIN_CARD_WIDTH, available / (MIN_STEP_RATIO * (n - 1) + 1));
                step = w * MIN_STEP_RATIO;
            }

            // 3) Ultimo recurso, ya con el suelo de tamano tocado: solaparse
            //    mas todavia. Feo, pero dentro de la pantalla.
            if (step * (n - 1) + w > available) {
                step = Math.max(1, (available - w) / (n - 1));
            }
        }

        if (Math.abs(w - appliedWidth) > 0.5) {
            appliedWidth = w;
            for (final CardNode c : cards) {
                c.setCardWidth(w);
            }
        }

        final double cardH = w * CardNode.ASPECT;
        final double totalWidth = step * (n - 1) + w;
        final double x0 = EDGE_PAD + Math.max(0, (available - totalWidth) / 2.0);
        final double centre = (n - 1) / 2.0;
        final double baseY = getHeight() - cardH - 4;

        for (int i = 0; i < n; i++) {
            final CardNode c = cards.get(i);
            // -1 en el extremo izquierdo, 0 en el centro, +1 en el derecho.
            final double t = n == 1 ? 0 : (i - centre) / Math.max(1e-6, centre);

            // OJO: al sobrescribir layoutChildren, Pane ya no dimensiona a los
            // hijos por nosotros. Sin este resize el nodo se queda a 0x0, el
            // StackPane coloca la imagen a su tamano natural y solo se ve una
            // esquina del arte. Hay que darle el tamano explicitamente.
            c.resize(w, cardH);
            c.setLayoutX(x0 + i * step);
            c.setLayoutY(baseY + Math.abs(t) * cardH * ARC_DROP);
            c.setRotate(c.isTapped() ? 90 : t * MAX_TILT);
            // Las de la derecha por encima: se lee como un abanico de verdad.
            c.setViewOrder(-i * 0.001);
        }
    }
}
