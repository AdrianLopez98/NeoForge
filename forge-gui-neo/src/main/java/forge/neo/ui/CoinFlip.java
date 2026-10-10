package forge.neo.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * <b>Una moneda que se lanza en mitad de la mesa.</b>
 *
 * <p>Forge la trajo el 10-10-2026 para su version de movil
 * ({@code IGuiGame.showCoinFlip}, que su {@code FControlGameEventHandler} llama
 * con cada {@code GameEventFlipCoin}): el sorteo de quien empieza y las cartas
 * que lanzan monedas (Mana Crypt, Krark's Thumb, Mana Clash...). Hasta entonces
 * eso solo se podia leer en el registro; ahora se ve caer, que es lo que pide la
 * mesa (las notas de diseño, seccion 6: el estado se ve, no se lee). El texto lo compone
 * el motor ya traducido: "Has ganado el sorteo", "IA-1 ha sacado cara".
 *
 * <p>Las mismas reglas que {@link TurnBanner}: <b>no se puede clicar</b> (la
 * mesa sigue respondiendo debajo), <b>se va sola</b> y es <b>corta</b>: sube y
 * gira ~700 ms, se queda 600 ms con la cara que ha salido y se apaga. Cara en
 * oro, cruz en plata, para que el resultado se vea sin leer la palabra.
 */
public final class CoinFlip extends VBox {

    private final StackPane coin = new StackPane();
    private final Label face = new Label();
    private final Label caption = new Label();
    private SequentialTransition running;
    private Runnable onDone;

    public CoinFlip() {
        getStyleClass().add("coin-flip");
        setAlignment(Pos.CENTER);
        setSpacing(UiScale.px(14));
        setVisible(false);
        setManaged(false);
        // Nunca se come un click: la mesa tiene que seguir siendo clicable.
        setMouseTransparent(true);

        coin.getStyleClass().add("coin");
        final double d = UiScale.px(96);
        coin.setMinSize(d, d);
        coin.setPrefSize(d, d);
        coin.setMaxSize(d, d);
        face.getStyleClass().add("coin-face");
        coin.getChildren().add(face);

        caption.getStyleClass().add("coin-caption");
        caption.setWrapText(true);
        caption.setMaxWidth(UiScale.px(460));
        getChildren().addAll(coin, caption);
    }

    /**
     * Lanza la moneda.
     *
     * @param heads   si sale cara (en el sorteo inicial: si lo has ganado tu)
     * @param text    lo que ha pasado, ya escrito por el motor
     * @param headsWord {@code "cara"} en el idioma de la partida
     * @param tailsWord {@code "cruz"}
     * @param done    al terminar (o si se corta), una sola vez
     */
    public void flip(final boolean heads, final String text, final String headsWord,
                     final String tailsWord, final Runnable done) {
        finish();
        onDone = done;
        caption.setText(text == null ? "" : text);
        caption.setOpacity(0);
        showFace(!heads, headsWord, tailsWord);
        setOpacity(0);
        setVisible(true);
        setManaged(true);
        requestLayout();

        // La moneda da media vuelta cada vez que su ancho pasa por cero: al
        // pasar, se cambia la cara. Siete medias vueltas (impar: se empieza por
        // la otra cara), cada una mas lenta, y la ultima deja la que ha salido.
        final int halves = 7;
        final Timeline spin = new Timeline();
        double t = 0;
        boolean showing = !heads;
        for (int i = 0; i < halves; i++) {
            final double half = 50 + i * 14;
            final boolean next = !showing;
            spin.getKeyFrames().add(new KeyFrame(Duration.millis(t + half / 2),
                    e -> showFace(next, headsWord, tailsWord),
                    new KeyValue(coin.scaleXProperty(), 0.05, Interpolator.EASE_IN)));
            spin.getKeyFrames().add(new KeyFrame(Duration.millis(t + half),
                    new KeyValue(coin.scaleXProperty(), 1, Interpolator.EASE_OUT)));
            t += half;
            showing = next;
        }
        // Y sube y baja mientras gira, como lanzada con el pulgar.
        final double lift = UiScale.px(70);
        spin.getKeyFrames().add(new KeyFrame(Duration.ZERO, new KeyValue(coin.translateYProperty(), 0)));
        spin.getKeyFrames().add(new KeyFrame(Duration.millis(t * 0.45),
                new KeyValue(coin.translateYProperty(), -lift, Interpolator.EASE_OUT)));
        spin.getKeyFrames().add(new KeyFrame(Duration.millis(t),
                new KeyValue(coin.translateYProperty(), 0, Interpolator.EASE_IN)));

        final FadeTransition in = new FadeTransition(Duration.millis(150), this);
        in.setFromValue(0);
        in.setToValue(1);
        final FadeTransition says = new FadeTransition(Duration.millis(150), caption);
        says.setFromValue(0);
        says.setToValue(1);
        final PauseTransition hold = new PauseTransition(Duration.millis(600));
        final FadeTransition out = new FadeTransition(Duration.millis(220), this);
        out.setFromValue(1);
        out.setToValue(0);

        running = new SequentialTransition(in, spin, says, hold, out);
        running.setOnFinished(e -> finish());
        running.play();
    }

    private void showFace(final boolean heads, final String headsWord, final String tailsWord) {
        face.setText((heads ? headsWord : tailsWord).toUpperCase(java.util.Locale.ROOT));
        coin.pseudoClassStateChanged(HEADS, heads);
    }

    /** La quita y avisa, si quedaba alguien esperando. */
    public void finish() {
        if (running != null) {
            final SequentialTransition r = running;
            running = null;
            r.stop();
        }
        setVisible(false);
        setManaged(false);
        setOpacity(1);
        coin.setScaleX(1);
        coin.setTranslateY(0);
        final Runnable done = onDone;
        onDone = null;
        if (done != null) {
            done.run();
        }
    }

    private static final javafx.css.PseudoClass HEADS = javafx.css.PseudoClass.getPseudoClass("heads");
}
