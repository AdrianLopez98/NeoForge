package forge.neo.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * "Esto te acaba de pasar", en la esquina de arriba a la derecha y SIN parar
 * la partida.
 *
 * <p>Reportado en r/forgeMTG el 15-09-2026: <i>"the 'got it' is also annoying,
 * just put it on top right like the base game and it's perfect"</i>. Hasta
 * aqui el resumen del turno del rival salia en el centro y el motor se quedaba
 * esperando su boton ({@code PromptBanner.showAlert}): se leia bien, pero
 * obligaba a un click por cada cosa que hiciera el rival.
 *
 * <p>Aqui no se espera a nadie. Cada aviso es una tarjeta que se va sola
 * (mas tiempo cuanto mas texto), se queda mientras tengas el raton encima y
 * se quita con un click. Como mucho {@link #MAX} a la vez: si la IA encadena
 * cosas, la mas vieja cede el sitio — lo que se pierde sigue en el registro,
 * que se marca como no leido.
 *
 * <p>El ritmo lo sigue poniendo la pausa entre carta y carta de la IA
 * ({@code NeoMatchUI.slowTheAiDown}), que es lo que da tiempo a leerlo.
 * El aviso central de siempre sigue disponible en Ajustes.
 *
 * <p>Deja pasar el raton fuera de las tarjetas ({@code pickOnBounds} a false):
 * debajo esta la mesa de los rivales, que se clica.
 */
public class NoticeStack extends VBox {

    /** Tarjetas a la vez como mucho. */
    static final int MAX = 4;

    public NoticeStack() {
        getStyleClass().add("notice-stack");
        setSpacing(8);
        setAlignment(Pos.TOP_RIGHT);
        setPickOnBounds(false);
        setVisible(false);
        setManaged(false);
    }

    /** Pone un aviso arriba del todo. */
    public void push(final String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        final Label body = new Label(text);
        body.getStyleClass().add("notice-text");
        body.setWrapText(true);
        body.setMinHeight(Region.USE_PREF_SIZE);
        body.setMaxWidth(Double.MAX_VALUE);
        final Label close = new Label("×");
        close.getStyleClass().add("notice-close");

        final HBox card = new HBox(8, body, close);
        HBox.setHgrow(body, Priority.ALWAYS);
        card.getStyleClass().add("notice-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.setCursor(javafx.scene.Cursor.HAND);

        final PauseTransition life = new PauseTransition(Duration.millis(lifeFor(text)));
        life.setOnFinished(e -> remove(card));
        card.setOnMouseEntered(e -> life.pause());
        card.setOnMouseExited(e -> life.play());
        card.setOnMouseClicked(e -> {
            e.consume();
            life.stop();
            remove(card);
        });

        getChildren().add(0, card);
        while (getChildren().size() > MAX) {
            getChildren().remove(getChildren().size() - 1);
        }
        card.setOpacity(0);
        final FadeTransition in = new FadeTransition(Duration.millis(150), card);
        in.setToValue(1);
        in.play();
        life.play();
        setVisible(true);
        relayout();
    }

    /** Cuanto dura: 4 s mas un poco por letra, entre 5 y 14 s. */
    static long lifeFor(final String text) {
        final long ms = 4_000L + 45L * text.length();
        return Math.max(5_000L, Math.min(14_000L, ms));
    }

    public boolean hasNotices() {
        return !getChildren().isEmpty();
    }

    public void clear() {
        getChildren().clear();
        setVisible(false);
        relayout();
    }

    private void remove(final Node card) {
        if (!getChildren().contains(card)) {
            return;
        }
        final FadeTransition out = new FadeTransition(Duration.millis(150), card);
        out.setToValue(0);
        out.setOnFinished(e -> {
            getChildren().remove(card);
            if (getChildren().isEmpty()) {
                setVisible(false);
            }
            relayout();
        });
        out.play();
    }

    private void relayout() {
        if (getParent() != null) {
            getParent().requestLayout();
        }
    }
}
