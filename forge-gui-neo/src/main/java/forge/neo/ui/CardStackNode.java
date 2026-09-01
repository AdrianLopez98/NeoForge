package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.neo.card.CardNode;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * Un hueco del campo de batalla: una carta suelta, o una pila de fichas
 * identicas con su contador.
 *
 * <p>Como en Arena: la pila se dibuja como la carta de delante con dos bordes
 * asomando por detras, y una insignia "x11" en la esquina. Ocupa el sitio de
 * una sola carta pase lo que pase, que es justo lo que evita que un mazo de
 * fichas inunde la mesa.
 */
public class CardStackNode extends Pane {

    /** Cuantas cartas "de atras" se dibujan como mucho. */
    private static final int MAX_SHADOWS = 2;

    /** Tamano de lo enganchado respecto a la carta anfitriona. */
    private static final double ATTACH_SCALE = 0.42;

    /**
     * Que parte de lo enganchado se mete DEBAJO de la anfitriona.
     *
     * <p>Poca, y a proposito: lo justo para que se lea como algo pegado a esa
     * criatura y no como una carta suelta de la fila. Lo demas asoma por abajo.
     *
     * <p>Y siempre por DETRAS. Un equipo no puede taparle nada a la criatura
     * que equipa — ni el arte, ni el nombre, ni el numero de la esquina, que es
     * el que mas se mira de toda la mesa. Va debajo, como en Arena.
     */
    private static final double ATTACH_TUCK = 0.18;

    private final CardNode front;
    private final Rectangle[] shadows = new Rectangle[MAX_SHADOWS];
    private final Label count = new Label();
    private final int size;

    private double cardWidth;

    public CardStackNode(final double cardWidth, final CardView card, final int size) {
        this.size = size;
        this.front = new CardNode(cardWidth);
        this.front.setCard(card);

        setPickOnBounds(false);

        for (int i = 0; i < MAX_SHADOWS; i++) {
            final Rectangle r = new Rectangle();
            r.setFill(Color.rgb(20, 25, 32));
            r.setStroke(Color.rgb(0, 0, 0, 0.8));
            r.setStrokeWidth(1);
            r.setVisible(size > i + 1);
            shadows[i] = r;
            getChildren().add(r);
        }
        getChildren().add(front);

        count.getStyleClass().add("stack-count");
        count.setAlignment(Pos.CENTER);
        count.setText("x" + size);
        count.setVisible(size > 1);
        count.setMouseTransparent(true);
        getChildren().add(count);

        setCardWidth(cardWidth);
    }

    /**
     * Equipamientos, auras y todo lo enganchado a esta carta.
     *
     * <p>Como en Arena: viajan con la criatura en vez de ocupar su propio hueco
     * en la mesa, y van <b>por debajo y por detras</b> de ella, sin taparle
     * nada.
     *
     * <p>Antes asomaban un dedo y el resultado era que un aura encima de una
     * criatura no se veia: casi entera detras de su anfitriona, fuera del
     * recorte de la fila y sin responder al raton. O sea que la mitad de las
     * auras que importan — las que te cambian la criatura — no habia forma de
     * leerlas, y un equipo no habia forma de clicarlo para moverlo.
     */
    private final List<CardNode> attachments = new ArrayList<>();

    /** Que hacer cuando se clica o se pasa el raton por lo enganchado. */
    private java.util.function.Consumer<CardNode> onAttachClick;
    private java.util.function.Consumer<CardNode> onAttachHover;

    public void setAttachmentHandlers(final java.util.function.Consumer<CardNode> click,
                                      final java.util.function.Consumer<CardNode> hover) {
        this.onAttachClick = click;
        this.onAttachHover = hover;
    }

    public void setAttachments(final List<CardView> attached) {
        final List<CardView> now = attached == null ? List.of() : attached;

        // Reutilizar los nodos mientras siga enganchado lo mismo. Esto se llama
        // en CADA aviso del motor, o sea decenas de veces por turno: recrear la
        // carta cada vez pierde la imagen ya descargada y reinicia sus
        // animaciones. Es la regla de rendimiento de las notas de diseño.
        if (sameCards(now)) {
            for (int i = 0; i < attachments.size(); i++) {
                attachments.get(i).setCard(now.get(i));
            }
            layoutAttachments();
            return;
        }

        for (final CardNode n : attachments) {
            getChildren().remove(n);
        }
        attachments.clear();
        for (final CardView cv : now) {
            final CardNode n = new CardNode(cardWidth * ATTACH_SCALE);
            n.setCard(cv);
            n.setAttached(true);
            n.getStyleClass().add("attachment");
            wireAttachment(n);
            attachments.add(n);
            getChildren().add(n);
        }
        layoutAttachments();
    }

    private boolean sameCards(final List<CardView> now) {
        if (now.size() != attachments.size()) {
            return false;
        }
        for (int i = 0; i < now.size(); i++) {
            final CardView was = attachments.get(i).getCard();
            final CardView is = now.get(i);
            if (was == null || is == null || was.getId() != is.getId()) {
                return false;
            }
        }
        return true;
    }

    private void wireAttachment(final CardNode n) {
        n.hoverProperty().addListener((o, was, is) -> {
            if (is && onAttachHover != null) {
                onAttachHover.accept(n);
            }
        });
        n.setOnMouseClicked(e -> {
            // El derecho lo usa la mesa para ampliar la carta.
            if (onAttachClick != null && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                onAttachClick.accept(n);
            }
        });
    }

    public List<CardNode> getAttachments() {
        return attachments;
    }

    /** Cuanto asoma lo enganchado por debajo del anfitrion, en pixeles. */
    public double attachOverhang() {
        if (attachments.isEmpty()) {
            return 0;
        }
        return cardWidth * ATTACH_SCALE * CardNode.ASPECT * (1 - ATTACH_TUCK);
    }

    private void layoutAttachments() {
        final int n = attachments.size();
        if (n == 0) {
            front.setBottomInset(0);
            return;
        }
        final double w = cardWidth * ATTACH_SCALE;
        final double h = w * CardNode.ASPECT;
        final double hostH = cardWidth * CardNode.ASPECT;

        // Se reparten a lo ancho del borde de abajo. Si no caben sin salirse,
        // se solapan entre ellas: lo que no puede pasar es que la ultima
        // cuelgue fuera del hueco de la carta y se meta en la de al lado.
        final double step = n == 1 ? 0
                : Math.min(w * 0.85, Math.max(w * 0.18, (cardWidth - w) / (n - 1.0)));
        final double used = (n - 1) * step + w;
        final double x0 = Math.max(0, (cardWidth - used) / 2);

        for (int i = 0; i < n; i++) {
            final CardNode a = attachments.get(i);
            a.setCardWidth(w);
            a.resize(w, h);
            a.setLayoutX(x0 + i * step);
            a.setLayoutY(hostH - h * ATTACH_TUCK);
            // Por DETRAS de la anfitriona (los ordenes positivos se pintan
            // despues), y cada una por delante de la de su izquierda. Al pasar
            // el raton por encima se adelanta ella sola; ver CardNode.hoverIn.
            a.setBaseViewOrder(1 - i * 0.001);
        }

        // La P/T se queda donde va impresa: ahora no hay nada tapandola.
        front.setBottomInset(0);
    }

    public CardNode getFront() {
        return front;
    }

    public int getStackSize() {
        return size;
    }

    public final void setCardWidth(final double w) {
        if (Math.abs(w - cardWidth) < 0.5) {
            return;
        }
        this.cardWidth = w;
        front.setCardWidth(w);

        final double h = w * CardNode.ASPECT;
        final double off = Math.max(2, w * 0.035);
        final double radius = w * 0.05;

        for (int i = 0; i < MAX_SHADOWS; i++) {
            final Rectangle r = shadows[i];
            final double d = off * (i + 1);
            r.setX(d);
            r.setY(-d);
            r.setWidth(w);
            r.setHeight(h);
            r.setArcWidth(radius * 2);
            r.setArcHeight(radius * 2);
        }

        final double badge = Math.max(16, w * 0.26);
        // Y se ensancha con las cifras. Una insignia cuadrada le vale a "x2",
        // pero "x11" no cabe y la etiqueta lo recorta a "x...", que es
        // justamente perder el numero — lo unico que la insignia dice.
        final double badgeW = badge * Math.max(1, 0.42 * count.getText().length());
        count.setStyle("-fx-font-size:" + (badge * 0.58) + "px;");
        count.setMinSize(badgeW, badge * 0.72);
        count.setPrefSize(badgeW, badge * 0.72);
        count.setLayoutX(w - badgeW * 0.9);
        count.setLayoutY(-badge * 0.18);

        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);
        front.resize(w, h);
        resize(w, h);
        layoutAttachments();
    }
}
