package forge.neo.ui;

import forge.neo.NeoText;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import forge.neo.card.CardNode;

/**
 * "Te toca."
 *
 * <p>Sale de jugar: <i>"no me entero muy bien de cuando es mi turno, solo me
 * guio por el texto pequenyo de la derecha"</i>. Y era verdad: quien juega solo
 * se podia <b>leer</b>, en un renglon de cuatro lineas, en ingles, en la esquina
 * — justo lo contrario del principio de la mesa, que dice que el estado se ve y
 * no se lee.
 *
 * <p>Esto es lo que hace Arena: al cambiar el turno, un cartel corto cruza el
 * centro de la pantalla. Dura poco y se va solo; no hay que cerrarlo.
 *
 * <p>Tres decisiones que importan:
 *
 * <ul>
 *   <li><b>No se puede clicar.</b> Es {@code mouseTransparent}: aunque este
 *       encima de la mesa, los clicks pasan a traves. Muchas peticiones del
 *       motor se contestan clicando cartas, y algo nuestro en medio las haria
 *       imposibles de responder (seccion 10b, principio 3).</li>
 *   <li><b>Tu turno y el suyo no se parecen.</b> El tuyo entra en acento y mas
 *       grande; el del rival, apagado y mas breve. Si los dos carteles fueran
 *       iguales habria que leerlos, que es lo que se venia a evitar.</li>
 *   <li><b>Corto.</b> 250 ms de entrada, se queda un momento y se va. Un cartel
 *       que dura mas que el primer click estorba.</li>
 * </ul>
 */
public class TurnBanner extends VBox {

    private final Label title = new Label();
    private final Label detail = new Label();
    private SequentialTransition running;

    public TurnBanner() {
        getStyleClass().add("turn-banner");
        setAlignment(Pos.CENTER);
        setSpacing(-2);
        setPadding(new Insets(10, 40, 12, 40));
        setVisible(false);
        setManaged(false);
        // Nunca se come un click: la mesa tiene que seguir siendo clicable.
        setMouseTransparent(true);

        title.getStyleClass().add("turn-banner-title");
        detail.getStyleClass().add("turn-banner-detail");
        final HBox row = new HBox(title);
        row.setAlignment(Pos.CENTER);
        getChildren().addAll(row, detail);
    }

    /**
     * Anuncia de quien es el turno.
     *
     * @param yours    si el turno es tuyo
     * @param who      nombre del jugador al que le toca
     * @param turn     numero de turno, o 0 si no se sabe
     */
    public void announce(final boolean yours, final String who, final int turn) {
        title.setText(yours ? NeoText.get("turn.yours")
                : NeoText.get("turn.theirs", who == null ? "?" : who.toUpperCase()));
        detail.setText(turn > 0 ? NeoText.get("turn.number", turn) : "");
        detail.setVisible(turn > 0);
        detail.setManaged(turn > 0);
        pseudoClassStateChanged(YOURS, yours);

        setVisible(true);
        setManaged(true);
        requestLayout();

        if (!CardNode.areAnimationsEnabled()) {
            // Sin animaciones sigue haciendo falta que se vaya solo, o se queda
            // el cartel del turno 1 puesto toda la partida.
            holdThenHide(yours);
            return;
        }

        if (running != null) {
            running.stop();
        }
        setOpacity(0);
        setTranslateY(-14);

        final FadeTransition in = new FadeTransition(Duration.millis(160), this);
        in.setFromValue(0);
        in.setToValue(1);

        final TranslateTransition drop = new TranslateTransition(Duration.millis(250), this);
        drop.setFromY(-14);
        drop.setToY(0);
        drop.setInterpolator(Interpolator.EASE_OUT);

        final javafx.animation.ParallelTransition enter =
                new javafx.animation.ParallelTransition(in, drop);

        // El tuyo se queda mas: es el que hay que notar. El del rival pasa.
        final PauseTransition hold = new PauseTransition(Duration.millis(yours ? 900 : 550));

        final FadeTransition out = new FadeTransition(Duration.millis(240), this);
        out.setFromValue(1);
        out.setToValue(0);

        running = new SequentialTransition(enter, hold, out);
        running.setOnFinished(e -> hide());
        running.play();
    }

    private void holdThenHide(final boolean yours) {
        final PauseTransition t = new PauseTransition(Duration.millis(yours ? 1100 : 700));
        t.setOnFinished(e -> hide());
        t.play();
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
        setOpacity(1);
        setTranslateY(0);
    }

    /** El alto que pide, para que la mesa lo coloque. */
    public double bannerHeight(final double width) {
        return prefHeight(width);
    }

    public double bannerWidth() {
        return Math.max(prefWidth(-1), 260);
    }

    private static final javafx.css.PseudoClass YOURS =
            javafx.css.PseudoClass.getPseudoClass("yours");

    /** Ancho maximo razonable: un cartel de lado a lado tapa la mesa. */
    public static final double MAX_WIDTH = 560;
}
