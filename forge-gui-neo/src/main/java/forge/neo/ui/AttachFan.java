package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.neo.card.CardNode;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.beans.InvalidationListener;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.layout.Pane;
import javafx.util.Duration;

/**
 * Lo enganchado a una carta, abierto en abanico al pasar el raton por ella
 * (Discord, 22-11-2026). Solo con el ajuste de llevarlo APILADO detras
 * ({@code NeoSettings.ATTACHMENTS_STACKED}): ahi no se ve nada mas que el
 * contador "+N", y el jugador no sabia que el Firebreathing de su criatura se
 * activa clicando el aura — <i>"the stack of target card and its enchantments
 * separate slightly on mouse over and the enchantment becomes clickable"</i>.
 *
 * <p>Vive en la mesa entera ({@code TableScreen}) y no dentro de la fila por
 * el recorte: la fila corta lo que se sale de ella, y en modo apilado no deja
 * sitio por debajo de la carta (para eso es el modo). Aqui encima no lo corta
 * nadie, y como solo dura lo que el raton esta encima, puede tapar un momento
 * la fila de al lado.
 *
 * <p>Las cartas son <b>las mismas</b> que la pila tiene escondidas, prestadas
 * mientras dura ({@link CardStackNode#lendAttachments}): asi llevan los
 * resaltados de "se puede usar", el click y el detalle sin copiar nada. Se
 * devuelven al cerrar, y se cierra en cuanto la pila deja la escena — lo que
 * vive mas que una pantalla no puede agarrarla (principio 11).
 */
public final class AttachFan extends Pane {

    /** Lo que tarda en cerrarse al salir: el raton cruza el hueco de la carta al abanico. */
    private static final Duration GRACE = Duration.millis(280);

    /** Cuanto mide cada carta del abanico respecto a su anfitriona. */
    private static final double SCALE = 0.5;

    private CardStackNode shown;
    private List<CardNode> cards = List.of();
    private final PauseTransition closeLater = new PauseTransition(GRACE);
    private final InvalidationListener hoverWatch = o -> hoverChanged();
    private final InvalidationListener sceneWatch = o -> {
        if (shown != null && shown.getScene() == null) {
            close();
        }
    };

    public AttachFan() {
        setManaged(false);
        setPickOnBounds(false);
        closeLater.setOnFinished(e -> closeIfIdle());
    }

    /** El raton ha entrado en esa carta: se abre lo que lleva encima. */
    public void open(final CardStackNode stack) {
        closeLater.stop();
        if (stack == shown) {
            return;
        }
        close();
        final List<CardNode> lent = stack.lendAttachments();
        if (lent.isEmpty()) {
            return;
        }
        shown = stack;
        cards = lent;
        stack.sceneProperty().addListener(sceneWatch);
        stack.getFront().hoverProperty().addListener(hoverWatch);
        for (final CardNode c : cards) {
            c.hoverProperty().addListener(hoverWatch);
            c.setVisible(true);
        }
        getChildren().setAll(cards);
        place();
        setOpacity(0);
        final FadeTransition in = new FadeTransition(Duration.millis(140), this);
        in.setToValue(1);
        in.play();
    }

    /** El raton ha salido de esa carta: se cierra si no va camino del abanico. */
    public void leaving(final CardStackNode stack) {
        if (stack == shown) {
            closeLater.playFromStart();
        }
    }

    /** Se cierra ya, devolviendo las cartas a su pila. */
    public void close() {
        closeLater.stop();
        if (shown == null) {
            return;
        }
        final CardStackNode stack = shown;
        final List<CardNode> back = cards;
        shown = null;
        cards = List.of();
        stack.sceneProperty().removeListener(sceneWatch);
        stack.getFront().hoverProperty().removeListener(hoverWatch);
        for (final CardNode c : back) {
            c.hoverProperty().removeListener(hoverWatch);
        }
        getChildren().clear();
        stack.takeBackAttachments(back);
    }

    /** Lo que se esta ensenyando ahora (para las pruebas). */
    public CardStackNode shown() {
        return shown;
    }

    public List<CardNode> cards() {
        return new ArrayList<>(cards);
    }

    private void hoverChanged() {
        if (shown == null) {
            return;
        }
        if (anyHover()) {
            closeLater.stop();
        } else {
            closeLater.playFromStart();
        }
    }

    private boolean anyHover() {
        if (shown != null && shown.getFront().isHover()) {
            return true;
        }
        for (final CardNode c : cards) {
            if (c.isHover()) {
                return true;
            }
        }
        return false;
    }

    private void closeIfIdle() {
        if (!anyHover()) {
            close();
        }
    }

    /**
     * Debajo de la carta, centrado en ella y algo mas grande que en el abanico
     * fijo (se abre para CLICARLO). Si no cabe por debajo, por encima. Se mide
     * sin el crecimiento del hover, que se anima y moveria el abanico.
     */
    private void place() {
        final CardNode front = shown.getFront();
        final Bounds inScene = shown.localToScene(new BoundingBox(front.getLayoutX(),
                front.getLayoutY(), front.getWidth(), front.getHeight()));
        final Bounds host = sceneToLocal(inScene);
        if (host == null) {
            return;
        }
        final double w = Math.max(48, host.getWidth() * SCALE);
        final double h = w * CardNode.ASPECT;
        final int n = cards.size();
        final double step = w * 0.8;
        final double total = w + (n - 1) * step;
        final double areaW = getParent() == null ? Double.MAX_VALUE
                : getParent().getLayoutBounds().getWidth();
        final double areaH = getParent() == null ? Double.MAX_VALUE
                : getParent().getLayoutBounds().getHeight();
        final double x0 = Math.max(4, Math.min(areaW - total - 4,
                host.getMinX() + host.getWidth() / 2 - total / 2));
        final double gap = host.getHeight() * 0.05;
        double y = host.getMaxY() + gap;
        if (y + h > areaH - 4) {
            y = host.getMinY() - gap - h;
        }
        for (int i = 0; i < n; i++) {
            final CardNode c = cards.get(i);
            c.setCardWidth(w);
            c.resize(w, h);
            c.relocate(x0 + i * step, y);
        }
    }
}
