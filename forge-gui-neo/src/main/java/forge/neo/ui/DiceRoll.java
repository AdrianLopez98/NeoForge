package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * <b>Unos dados que se tiran en mitad de la mesa.</b>
 *
 * <p>Discord, 11-10-2026 (Minty, con Mad Science Fair Project delante): <i>"A
 * visual dice roll (toggle?) or toast that shows us the dice roll for dice
 * cards"</i>. El motor publica cada tirada ({@code GameEventRollDice}: cuantas
 * caras y lo que ha salido en cada dado) y hasta ahora solo se podia leer en el
 * registro. Forge lo pinta en su version de movil ({@code DiceOverlay}, en 3D);
 * aqui va con las mismas reglas que {@link CoinFlip}, que es su hermana: <b>no
 * se puede clicar</b>, <b>se va sola</b> y es <b>corta</b> (~1,7 s). Los dados
 * ruedan cambiando de numero cada vez mas despacio y caen en el que ha salido;
 * el maximo en oro y el 1 en rojo, para que se vea sin leer. Hasta
 * {@link #MAX_DICE} a la vez, como el movil; los demas solo cuentan en la suma.
 *
 * <p>El "toggle" que pedia es el de siempre: con las animaciones apagadas no
 * sale, igual que la moneda.
 */
public final class DiceRoll extends VBox {

    /** Cuantos dados se pintan como mucho. */
    public static final int MAX_DICE = 6;

    private final HBox row = new HBox();
    private final Label caption = new Label();
    private SequentialTransition running;
    private Runnable onDone;

    public DiceRoll() {
        getStyleClass().add("dice-roll");
        setAlignment(Pos.CENTER);
        setSpacing(UiScale.px(14));
        setVisible(false);
        setManaged(false);
        // Nunca se come un click: la mesa tiene que seguir siendo clicable.
        setMouseTransparent(true);

        row.setAlignment(Pos.CENTER);
        row.setSpacing(UiScale.px(14));
        caption.getStyleClass().add("coin-caption");
        caption.setWrapText(true);
        caption.setMaxWidth(UiScale.px(460));
        getChildren().addAll(row, caption);
    }

    /**
     * Tira los dados.
     *
     * @param sides   cuantas caras tiene cada dado
     * @param results lo que ha salido en cada uno, en orden
     * @param text    de que va la tirada (la carta), o vacio
     * @param done    al terminar (o si se corta), una sola vez
     */
    public void roll(final int sides, final List<Integer> results, final String text,
                     final Runnable done) {
        finish();
        onDone = done;
        caption.setText(text == null ? "" : text);
        caption.setOpacity(0);
        caption.setVisible(text != null && !text.isBlank());
        caption.setManaged(caption.isVisible());

        final int n = Math.min(MAX_DICE, results.size());
        final double size = UiScale.px(n > 3 ? 64 : 84);
        final List<StackPane> dice = new ArrayList<>();
        final List<Label> faces = new ArrayList<>();
        row.getChildren().clear();
        for (int i = 0; i < n; i++) {
            final StackPane die = new StackPane();
            die.getStyleClass().add("die");
            die.setMinSize(size, size);
            die.setPrefSize(size, size);
            die.setMaxSize(size, size);
            final Label face = new Label();
            face.getStyleClass().add("die-face");
            UiScale.fixedFont(face, n > 3 ? 26 : 34);
            // Que dado es, en la esquina: un 4 en un d20 no es un 4 en un d6.
            final Label kind = new Label("d" + sides);
            kind.getStyleClass().add("die-kind");
            StackPane.setAlignment(kind, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(kind, new Insets(0, UiScale.px(6), UiScale.px(3), 0));
            die.getChildren().addAll(face, kind);
            dice.add(die);
            faces.add(face);
        }
        row.getChildren().addAll(dice);
        setOpacity(0);
        setVisible(true);
        setManaged(true);
        requestLayout();

        // Ruedan: el numero cambia en cada paso, cada vez mas despacio, y el
        // ultimo deja el que ha salido. Mientras, giran y saltan.
        final Random rnd = new Random();
        final Timeline tumble = new Timeline();
        final int steps = 9;
        double t = 0;
        for (int s = 0; s < steps; s++) {
            t += 45 + s * 12;
            final boolean last = s == steps - 1;
            tumble.getKeyFrames().add(new KeyFrame(Duration.millis(t), e -> {
                for (int i = 0; i < faces.size(); i++) {
                    faces.get(i).setText(String.valueOf(
                            last ? results.get(i) : 1 + rnd.nextInt(Math.max(1, sides))));
                }
            }));
        }
        final double lift = UiScale.px(60);
        for (int i = 0; i < dice.size(); i++) {
            final StackPane die = dice.get(i);
            // Cada uno gira hacia un lado, para que no parezcan uno repetido.
            final double turns = (i % 2 == 0 ? -1 : 1) * (360 + rnd.nextInt(180));
            die.setRotate(turns);
            tumble.getKeyFrames().add(new KeyFrame(Duration.ZERO,
                    new KeyValue(die.rotateProperty(), turns),
                    new KeyValue(die.translateYProperty(), 0)));
            tumble.getKeyFrames().add(new KeyFrame(Duration.millis(t * 0.4),
                    new KeyValue(die.translateYProperty(), -lift, Interpolator.EASE_OUT)));
            tumble.getKeyFrames().add(new KeyFrame(Duration.millis(t),
                    new KeyValue(die.rotateProperty(), 0, Interpolator.EASE_OUT),
                    new KeyValue(die.translateYProperty(), 0, Interpolator.EASE_IN)));
        }
        tumble.setOnFinished(e -> {
            for (int i = 0; i < dice.size(); i++) {
                final int r = results.get(i);
                dice.get(i).pseudoClassStateChanged(MAX, sides > 1 && r == sides);
                dice.get(i).pseudoClassStateChanged(MIN, sides > 1 && r == 1);
            }
        });

        final FadeTransition in = new FadeTransition(Duration.millis(150), this);
        in.setFromValue(0);
        in.setToValue(1);
        final FadeTransition says = new FadeTransition(Duration.millis(150), caption);
        says.setFromValue(0);
        says.setToValue(1);
        final PauseTransition hold = new PauseTransition(Duration.millis(700));
        final FadeTransition out = new FadeTransition(Duration.millis(220), this);
        out.setFromValue(1);
        out.setToValue(0);

        running = new SequentialTransition(in, tumble, says, hold, out);
        running.setOnFinished(e -> finish());
        running.play();
    }

    /** Los quita y avisa, si quedaba alguien esperando. */
    public void finish() {
        if (running != null) {
            final SequentialTransition r = running;
            running = null;
            r.stop();
        }
        setVisible(false);
        setManaged(false);
        setOpacity(1);
        row.getChildren().clear();
        final Runnable done = onDone;
        onDone = null;
        if (done != null) {
            done.run();
        }
    }

    private static final javafx.css.PseudoClass MAX = javafx.css.PseudoClass.getPseudoClass("max");
    private static final javafx.css.PseudoClass MIN = javafx.css.PseudoClass.getPseudoClass("min");
}
