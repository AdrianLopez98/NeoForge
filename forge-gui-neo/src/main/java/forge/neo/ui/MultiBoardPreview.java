package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;

/**
 * MAQUETA: las cuatro mesas de un Commander a la vez, sin pestanyas.
 *
 * <p>No es la mesa de juego ni pretende serlo: no habla con el motor, no tiene
 * stack, ni prompt, ni flechas, ni zoom. Existe para contestar UNA pregunta que
 * no se puede contestar con una cuenta en un papel — <b>a que tamanyo acaban
 * las cartas si los tres rivales se reparten el ancho</b> — y para poder mirar
 * la respuesta en una captura en vez de discutirla.
 *
 * <pre>
 *   +----------------+----------------+----------------+---------+
 *   |  barra IA-1    |  barra IA-2    |  barra IA-3    |         |
 *   |  campo IA-1    |  campo IA-2    |  campo IA-3    | columna |
 *   |                |                |                | lateral |
 *   | =============== linea de combate =============== | (hueco) |
 *   |            tu campo, ancho entero                |         |
 *   |            tu barra                              |         |
 *   |            tu mano                               |         |
 *   +--------------------------------------------------+---------+
 * </pre>
 *
 * <p>Reparte el alto a mano, con el mismo criterio que {@link TableScreen}: un
 * presupuesto explicito del que barras y mano cobran lo suyo primero, y lo que
 * queda se parte entre la fila de rivales y tu campo. Asi la maqueta no puede
 * desbordarse y lo que se ve es lo que se veria de verdad.
 *
 * <p>La columna lateral se reserva VACIA a proposito. Ahi van las fases, el
 * stack y los botones, que no desaparecerian por cambiar la disposicion de la
 * mesa; sin reservarla, la maqueta se daria 365 px de mas y las cartas saldrian
 * mas grandes de lo que van a salir. Una maqueta optimista no sirve para
 * decidir nada.
 */
public class MultiBoardPreview extends Pane {

    private static final double LINE_H = 14;
    private static final double COL_GAP = 10;

    /** Cuanto del alto libre se lleva la fila de rivales. */
    private static final double OPP_SHARE =
            Double.parseDouble(System.getProperty("neo.multiboard.oppShare", "0.52"));

    private final double sideWidth;

    private final List<PlayerBar> oppBars = new ArrayList<>();
    private final List<PlayerField> oppFields = new ArrayList<>();
    private final List<Line> dividers = new ArrayList<>();

    private final PlayerBar selfBar = new PlayerBar(false);
    private final PlayerField selfField;
    private final HandFan hand;
    private final Line combatLine = new Line();
    private final Region side = new Region();
    private final Label hud = new Label();

    /**
     * @param cardWidth    el mismo que usa {@link TableScreen}
     * @param sideWidth    ancho de la columna lateral, que se reserva vacia
     * @param opponents    cuantos rivales (3 es el caso que se esta evaluando)
     * @param pilesOnBoard cementerio y exilio en la mesa del rival, o solo en
     *                     su barra. Es la palanca que decide si esto cabe
     */
    public MultiBoardPreview(final double cardWidth, final double sideWidth,
                             final int opponents, final boolean pilesOnBoard) {
        this.sideWidth = sideWidth;
        this.selfField = new PlayerField(cardWidth, false);
        this.hand = new HandFan(cardWidth * 1.12);

        getStyleClass().add("table-root");

        for (int i = 0; i < opponents; i++) {
            // El campo del rival usa el mismo 0.92 que TableScreen: sus cartas
            // son un pelin mas pequenyas que las tuyas.
            final PlayerField f = new PlayerField(cardWidth * 0.92, true);
            f.setPilesOnBoard(pilesOnBoard);
            oppFields.add(f);
            oppBars.add(new PlayerBar(true));
            if (i > 0) {
                final Line d = new Line();
                d.getStyleClass().add("seat-divider");
                dividers.add(d);
            }
        }

        combatLine.getStyleClass().add("combat-line");
        hand.getStyleClass().add("hand-area");
        side.getStyleClass().add("side-panel");

        hud.getStyleClass().add("hud-sub");
        hud.setPadding(new Insets(6, 10, 6, 10));
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setMouseTransparent(true);
        hud.setWrapText(true);

        getChildren().add(side);
        getChildren().addAll(oppFields);
        getChildren().addAll(oppBars);
        getChildren().addAll(dividers);
        getChildren().addAll(combatLine, selfField, selfBar, hand, hud);
    }

    // ---------------------------------------------------------------
    // Alimentarla
    // ---------------------------------------------------------------

    /** El campo de un rival. {@code everywhere} es toda la mesa, como en Arena. */
    public void setOpponentBoard(final int i, final List<CardView> cards,
                                 final List<CardView> everywhere) {
        oppFields.get(i).setCards(cards, everywhere);
    }

    public PlayerBar opponentBar(final int i) {
        return oppBars.get(i);
    }

    public PlayerField opponentField(final int i) {
        return oppFields.get(i);
    }

    public PlayerBar getSelfBar() {
        return selfBar;
    }

    public void setSelfBoard(final List<CardView> cards, final List<CardView> everywhere) {
        selfField.setCards(cards, everywhere);
    }

    public void setHand(final List<CardView> cards, final double cardWidth) {
        hand.clearCards();
        for (final CardView cv : cards) {
            final CardNode n = new CardNode(cardWidth * 1.12);
            n.setCard(cv);
            hand.add(n);
        }
    }

    /**
     * El ancho de carta al que ha acabado un campo, ya repartido.
     *
     * <p>Es EL numero de esta maqueta. Se lee del nodo y no se recalcula: lo
     * decide {@link BattlefieldPane}, y cualquier cuenta paralela nuestra
     * podria estar mintiendo justo en lo que se quiere medir.
     *
     * @return 0 si ese campo no tiene ninguna carta
     */
    public double measuredCardWidth(final PlayerField field) {
        double min = Double.MAX_VALUE;
        for (final CardNode n : field.nodes()) {
            min = Math.min(min, n.getCardWidth());
        }
        return min == Double.MAX_VALUE ? 0 : min;
    }

    /** Vuelca los anchos medidos, para poder leerlos sin mirar la captura. */
    public String report() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < oppFields.size(); i++) {
            sb.append(String.format("rival %d: %.0f px  ", i + 1,
                    measuredCardWidth(oppFields.get(i))));
        }
        sb.append(String.format("| tu campo: %.0f px", measuredCardWidth(selfField)));
        return sb.toString();
    }

    /** El rotulo de arriba, con lo que se esta midiendo. */
    public void setHudText(final String text) {
        hud.setText(text);
    }

    // ---------------------------------------------------------------
    // Layout: el mismo presupuesto explicito que TableScreen
    // ---------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        final double w = getWidth();
        final double h = getHeight();
        final double contentW = Math.max(200, w - sideWidth);

        side.resizeRelocate(contentW, 0, sideWidth, h);

        final int n = oppFields.size();
        final double colW = (contentW - COL_GAP * (n - 1)) / n;

        // Las barras de rival van en fila, asi que la fila mide lo que la mas
        // alta: con 1/3 del ancho, PlayerBar envuelve y crece.
        double oppBarH = 0;
        for (final PlayerBar b : oppBars) {
            oppBarH = Math.max(oppBarH, b.prefHeight(colW));
        }
        final double selfBarH = selfBar.prefHeight(contentW);
        double handH = hand.prefHeight(contentW);

        final double fixed = oppBarH + selfBarH + LINE_H;
        final double minFields = 160;
        if (fixed + handH + minFields > h) {
            handH = Math.max(60, h - fixed - minFields);
        }

        final double fieldsH = Math.max(0, h - fixed - handH);
        final double oppH = fieldsH * OPP_SHARE;
        final double selfH = fieldsH - oppH;

        double y = 0;
        for (int i = 0; i < n; i++) {
            final double x = i * (colW + COL_GAP);
            oppBars.get(i).resizeRelocate(x, y, colW, oppBarH);
        }
        y += oppBarH;

        for (int i = 0; i < n; i++) {
            final double x = i * (colW + COL_GAP);
            oppFields.get(i).resizeRelocate(x, y, colW, oppH);
        }
        // Los separadores entre rivales. Sin ellos las tres mesas se leen como
        // una sola muy ancha, que es justo el fallo que hay que evitar.
        for (int i = 0; i < dividers.size(); i++) {
            final double x = (i + 1) * (colW + COL_GAP) - COL_GAP / 2;
            final Line d = dividers.get(i);
            d.setStartX(x);
            d.setEndX(x);
            d.setStartY(0);
            d.setEndY(y + oppH);
        }
        y += oppH;

        combatLine.setStartX(30);
        combatLine.setEndX(contentW - 30);
        combatLine.setStartY(y + LINE_H / 2);
        combatLine.setEndY(y + LINE_H / 2);
        y += LINE_H;

        selfField.resizeRelocate(0, y, contentW, selfH);
        y += selfH;

        selfBar.resizeRelocate(0, y, contentW, selfBarH);
        y += selfBarH;

        hand.resizeRelocate(0, y, contentW, Math.max(0, h - y));

        // El rotulo va en la columna lateral, que esta reservada y vacia. Antes
        // iba arriba a la izquierda y se montaba encima de la barra de IA-1:
        // una maqueta que tapa justo lo que se viene a mirar no sirve.
        final double hudW = sideWidth - 24;
        hud.resizeRelocate(contentW + 12, 12, hudW, hud.prefHeight(hudW));
    }
}
