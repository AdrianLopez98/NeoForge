package forge.neo.ui;

import forge.neo.card.CardNode;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Las animaciones que cuentan que acaba de pasar.
 *
 * <p>Regla de las notas de diseño seccion 6: <b>una animacion existe para explicar un
 * cambio</b> (esto entro, esto murio, esto se taparon). Si no explica nada, se
 * quita. Y dura 150-250 ms: mas es esperar.
 *
 * <p>Estan aqui y no en {@code CardNode} porque lo que se anima muchas veces no
 * es una carta suelta sino la pila que la contiene, o un numero de la barra.
 *
 * <p>Todas respetan el interruptor de animaciones de los ajustes
 * ({@link CardNode#areAnimationsEnabled()}): apagado, el cambio se aplica de
 * golpe y se ve exactamente lo mismo, solo que sin recorrido.
 */
public final class Anim {

    private Anim() {
    }

    private static final Duration ENTER = Duration.millis(230);
    private static final Duration LEAVE = Duration.millis(230);
    private static final Duration BUMP = Duration.millis(170);
    private static final Duration DEAL = Duration.millis(240);
    private static final Duration PICK = Duration.millis(260);

    /**
     * "Esto acaba de entrar en la mesa."
     *
     * <p>La IA no tiene manos: resuelve su turno en milisegundos y un
     * permanente nuevo simplemente <i>esta</i>, sin que haya forma de saber si
     * estaba antes. Un cuarto de segundo de entrada es lo que lo hace visible.
     *
     * <p>Crece desde un poco mas pequenya, no desde cero: la carta ocupa su
     * hueco desde el primer fotograma y la fila no da un salto.
     */
    public static void enter(final Node node) {
        if (!CardNode.areAnimationsEnabled()) {
            node.setOpacity(1);
            return;
        }
        node.setOpacity(0);
        node.setScaleX(0.86);
        node.setScaleY(0.86);

        final FadeTransition f = new FadeTransition(ENTER, node);
        f.setToValue(1);
        final ScaleTransition sc = new ScaleTransition(ENTER, node);
        sc.setToX(1);
        sc.setToY(1);
        sc.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(f, sc).play();
    }

    /**
     * "Esto se acaba de ir de la mesa."
     *
     * <p>Se llama sobre un nodo que el motor YA no devuelve: se queda en su
     * sitio como un fantasma, encogiendose hasta desaparecer. Sin esto, una
     * criatura que muere en el turno del rival no deja ni rastro.
     *
     * @param onDone quitarlo de la escena; se llama siempre, tambien con las
     *               animaciones apagadas
     */
    public static void leave(final Node node, final Runnable onDone) {
        // Un fantasma no se puede clicar: la carta ya no esta en la partida.
        node.setMouseTransparent(true);
        if (!CardNode.areAnimationsEnabled()) {
            onDone.run();
            return;
        }
        final FadeTransition f = new FadeTransition(LEAVE, node);
        f.setToValue(0);
        final ScaleTransition sc = new ScaleTransition(LEAVE, node);
        sc.setToX(0.82);
        sc.setToY(0.82);
        sc.setInterpolator(Interpolator.EASE_IN);
        final ParallelTransition p = new ParallelTransition(f, sc);
        p.setOnFinished(e -> onDone.run());
        p.play();
    }

    /**
     * Abrir un sobre: las cartas salen del centro y se abren en abanico.
     *
     * <p>Es lo que hace que un draft se sienta a draft y no a un formulario.
     * Cada carta sale del centro de la rejilla hacia su hueco, con un retraso
     * que crece con su posicion: el ojo las ve aparecer una detras de otra, que
     * es exactamente la sensacion de rasgar el sobre.
     *
     * <p>El desplazamiento se calcula por FILA Y COLUMNA, no por coordenadas
     * reales: cuando esto se llama, el {@code TilePane} todavia no ha colocado
     * nada, asi que preguntar posiciones daria cero.
     *
     * @param index   posicion de la carta en el sobre
     * @param columns cuantas caben por fila
     * @param opening true al abrir un sobre nuevo; false para un repintado
     */
    public static void dealIn(final Node node, final int index, final int columns,
                              final boolean opening) {
        if (!CardNode.areAnimationsEnabled()) {
            node.setOpacity(1);
            return;
        }
        final int col = index % Math.max(1, columns);
        final int row = index / Math.max(1, columns);
        final double fromX = opening ? (Math.max(1, columns) / 2.0 - col) * 46 : 0;
        final double fromY = opening ? (1 - row) * 30 : 0;

        node.setOpacity(0);
        node.setScaleX(opening ? 0.62 : 0.9);
        node.setScaleY(opening ? 0.62 : 0.9);
        node.setTranslateX(fromX);
        node.setTranslateY(fromY);

        final Duration d = opening ? DEAL : Duration.millis(150);
        final FadeTransition f = new FadeTransition(d, node);
        f.setToValue(1);
        final ScaleTransition sc = new ScaleTransition(d, node);
        sc.setToX(1);
        sc.setToY(1);
        final TranslateTransition tr = new TranslateTransition(d, node);
        tr.setToX(0);
        tr.setToY(0);

        final ParallelTransition p = new ParallelTransition(f, sc, tr);
        p.setInterpolator(Interpolator.EASE_OUT);
        p.setDelay(Duration.millis(index * (opening ? 42 : 12)));
        p.play();
    }

    /**
     * "Esta me la quedo": la carta elegida vuela a la pila de picks.
     *
     * <p>Sin esto un pick es un parpadeo — el sobre cambia y ya. El vuelo dice
     * las dos cosas que hacen falta: cual has cogido y donde ha ido a parar.
     */
    public static void flyTo(final Node node, final double dx, final double dy,
                             final Runnable onDone) {
        node.setMouseTransparent(true);
        if (!CardNode.areAnimationsEnabled()) {
            onDone.run();
            return;
        }
        node.setViewOrder(-1);   // por delante del resto del sobre mientras vuela
        final TranslateTransition tr = new TranslateTransition(PICK, node);
        tr.setToX(dx);
        tr.setToY(dy);
        final ScaleTransition sc = new ScaleTransition(PICK, node);
        sc.setToX(0.45);
        sc.setToY(0.45);
        final FadeTransition f = new FadeTransition(PICK, node);
        f.setToValue(0.15);

        final ParallelTransition p = new ParallelTransition(tr, sc, f);
        p.setInterpolator(Interpolator.EASE_IN);
        p.setOnFinished(e -> onDone.run());
        p.play();
    }

    /** Lo que no has elegido se va, mas discreto y sin moverse. */
    public static void fadeAway(final Node node) {
        node.setMouseTransparent(true);
        if (!CardNode.areAnimationsEnabled()) {
            node.setOpacity(0);
            return;
        }
        final FadeTransition f = new FadeTransition(PICK, node);
        f.setToValue(0);
        final ScaleTransition sc = new ScaleTransition(PICK, node);
        sc.setToX(0.9);
        sc.setToY(0.9);
        new ParallelTransition(f, sc).play();
    }

    /**
     * "Este numero acaba de cambiar."
     *
     * <p>Un golpe de escala y vuelta. Se usa para la vida, que es el numero que
     * decide la partida y que hoy cambia en silencio mientras juega la IA.
     */
    /**
     * "A esto le acaban de pegar."
     *
     * <p>Un tinte rojo corto SOBRE la carta. Existe por una queja concreta de
     * jugar: <i>"de repente me han matado una criatura y no se ni como"</i>. El
     * motor dice quien pego a quien ({@code GameEventCardDamaged}) y la mesa lo
     * cambiaba en silencio; un destello en la que lo recibe cuenta DONDE ha
     * pasado, que es la mitad de la respuesta.
     *
     * <p><b>Por dentro y no alrededor.</b> Lo natural seria un resplandor
     * ({@code DropShadow}) por fuera, pero {@code CardNode} lleva un
     * {@code setClip} con la forma redondeada de la carta: todo lo que se pinte
     * fuera de esa silueta se recorta, asi que el resplandor no se veia. Se
     * mete un rectangulo dentro, que ademas hereda ese mismo recorte y respeta
     * las esquinas.
     *
     * <p>Corto a proposito (180 ms): esto pasa muchas veces por combate, y una
     * animacion larga aqui haria el turno del rival insoportable.
     */
    public static void hit(final CardNode node) {
        if (!CardNode.areAnimationsEnabled() || node == null) {
            return;
        }
        final javafx.scene.shape.Rectangle flash = new javafx.scene.shape.Rectangle(
                node.getCardWidth(), node.getCardHeight());
        flash.setFill(javafx.scene.paint.Color.web("#FF3B30"));
        flash.setOpacity(0);
        // Que no se coma los clicks: la carta sigue siendo clicable mientras
        // parpadea, y el motor puede estar esperando justo eso.
        flash.setMouseTransparent(true);
        node.getChildren().add(flash);

        final FadeTransition f = new FadeTransition(Duration.millis(70), flash);
        f.setFromValue(0);
        f.setToValue(0.55);
        f.setAutoReverse(true);
        f.setCycleCount(2);
        f.setInterpolator(Interpolator.EASE_OUT);
        f.setOnFinished(e -> node.getChildren().remove(flash));
        f.play();
    }

    public static void bump(final Node node) {
        if (!CardNode.areAnimationsEnabled()) {
            return;
        }
        final ScaleTransition sc = new ScaleTransition(BUMP, node);
        sc.setFromX(1);
        sc.setFromY(1);
        sc.setToX(1.2);
        sc.setToY(1.2);
        sc.setAutoReverse(true);
        sc.setCycleCount(2);
        sc.setInterpolator(Interpolator.EASE_OUT);
        sc.play();
    }
}
