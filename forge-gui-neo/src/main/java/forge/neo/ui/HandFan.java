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
     * Si la mano se pinta en abanico o con las cartas rectas.
     *
     * <p>Reportado probando el juego (21-09-2026): una carta girada cinco
     * grados la pinta JavaFX con antialiasing, y en un monitor de 1080p el
     * borde y el texto del nombre salen sucios. Con las cartas rectas cada
     * pixel de la imagen cae sobre un pixel de la pantalla y se lee mejor.
     *
     * <p>Es un ajuste, no un cambio: el abanico sigue siendo el de fabrica
     * porque es lo que hace que una mano de siete cartas se lea de un vistazo.
     * Quien prefiera nitidez lo apaga.
     */
    private static volatile boolean fanned = true;

    /** @see #fanned */
    public static void setFanned(final boolean on) {
        fanned = on;
    }

    /** @see #fanned */
    public static boolean isFanned() {
        return fanned;
    }

    /**
     * Vuelve a repartir todas las manos que cuelguen de este nodo.
     *
     * <p>El ajuste se cambia con la partida abierta detras, y el reparto solo
     * se rehace cuando algo lo pide: sin esto, el cambio no se veria hasta que
     * el motor mandara el siguiente aviso.
     */
    public static void relayoutAllIn(final javafx.scene.Node root) {
        if (root instanceof HandFan) {
            ((HandFan) root).requestLayout();
            return;
        }
        if (root instanceof javafx.scene.Parent) {
            for (final javafx.scene.Node child
                    : ((javafx.scene.Parent) root).getChildrenUnmodifiable()) {
                relayoutAllIn(child);
            }
        }
    }

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

    private double scrollX, scrollMax;
    private final javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
    private final javafx.scene.control.Button previous = scrollButton("‹", -1);
    private final javafx.scene.control.Button next = scrollButton("›", 1);

    private javafx.scene.control.Button scrollButton(String text, int direction) {
        var button = new javafx.scene.control.Button(text);
        button.getStyleClass().add("arena-scroll");
        UiScale.fixedFont(button, 18);
        button.setAccessibleText(direction < 0 ? "Scroll left" : "Scroll right");
        button.setManaged(false);
        button.setVisible(false);
        button.setViewOrder(-100);
        button.setOnAction(e -> {
            scrollX = Math.max(0, Math.min(scrollMax, scrollX + direction * getWidth() * .55));
            requestLayout();
        });
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
        return button;
    }

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
        setClip(clip);
        getChildren().addAll(previous, next);
        setOnScroll(e -> {
            if (!forge.neo.platform.NeoOs.ctrl(e) && scrollMax > .5) {
                double delta = Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY()) ? e.getDeltaX() : e.getDeltaY();
                scrollX = Math.max(0, Math.min(scrollMax, scrollX - delta));
                requestLayout();
                e.consume();
            }
        });
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
        getChildren().setAll(previous, next);
        scrollX = scrollMax = 0; previous.setVisible(false); next.setVisible(false);
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

        // Height determines card size; an unusually large hand scrolls instead of shrinking.
        double w = Math.min(cardWidth, Math.max(34, (getHeight() - 10) / (CardNode.ASPECT * 1.16)));
        w = Math.min(w, available);
        double step = n == 1 ? 0 : Math.max(w * MIN_STEP_RATIO,
                Math.min(w * 1.04, (available - w) / (n - 1)));
        scrollMax = Math.max(0, step * (n - 1) + w - available);
        scrollX = Math.max(0, Math.min(scrollX, scrollMax));

        // El recorte tiene que dejar sitio para el hover, y el hueco fijo de
        // antes (Math.max(80, w * .5)) estaba medido para el 8 % de siempre:
        // con el ajuste nuevo de NeoSettings.hoverZoom() (hasta 150 %) una
        // carta se quedaba cortada por los lados y por arriba, un filo recto
        // justo en el borde del recorte. Se calcula lo que la carta crece de
        // verdad — CardNode.hoverLiftFor es la MISMA cuenta que usa el propio
        // hover, asi que el margen y el gesto nunca se desincronizan — y se
        // anyade un colchon pequenyo por el redondeo del easing.
        final double zoom = forge.neo.NeoSettings.hoverZoom();
        final double hoverCardH = w * CardNode.ASPECT;
        final double growSide = (zoom - 1) * w / 2 + 6;
        final double marginTop = CardNode.hoverLiftFor(zoom, w) + (zoom - 1) * hoverCardH / 2 + 6;
        clip.setX(-growSide); clip.setY(-marginTop);
        clip.setWidth(getWidth() + growSide * 2); clip.setHeight(getHeight() + marginTop);
        previous.setVisible(scrollMax > .5); next.setVisible(scrollMax > .5);
        previous.setDisable(scrollX <= .5); next.setDisable(scrollX >= scrollMax - .5);
        final double bw = UiScale.px(28), bh = UiScale.px(26);
        previous.resizeRelocate(2, 4, bw, bh);
        next.resizeRelocate(Math.max(bw + 2, getWidth() - bw - 2), 4, bw, bh);
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
        // Con las cartas rectas no hay arco, pero el hueco de abajo se deja
        // igual: si la mano cambiase de altura al tocar el ajuste, se moveria
        // toda la mesa con ella.
        final double drop = fanned ? ARC_DROP : 0;
        final double baseY = Math.max(0, getHeight() - cardH * (1 + ARC_DROP) - w * .07 - 4);

        for (int i = 0; i < n; i++) {
            final CardNode c = cards.get(i);
            // -1 en el extremo izquierdo, 0 en el centro, +1 en el derecho.
            final double t = n == 1 ? 0 : (i - centre) / Math.max(1e-6, centre);

            // OJO: al sobrescribir layoutChildren, Pane ya no dimensiona a los
            // hijos por nosotros. Sin este resize el nodo se queda a 0x0, el
            // StackPane coloca la imagen a su tamano natural y solo se ve una
            // esquina del arte. Hay que darle el tamano explicitamente.
            c.resize(w, cardH);
            c.setLayoutX(x0 + i * step - scrollX);
            c.setLayoutY(baseY + Math.abs(t) * cardH * drop);
            c.setRotate(c.isTapped() ? 90 : fanned ? t * MAX_TILT : 0);
            // Las de la derecha por encima: se lee como un abanico de verdad.
            c.setViewOrder(-i * 0.001);
        }
    }
}


