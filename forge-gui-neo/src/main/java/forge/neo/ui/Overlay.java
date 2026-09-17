package forge.neo.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Capa oscurecida por encima de la mesa, para los dialogos.
 *
 * <p>Cuando el motor te pide algo que no cabe en la mesa (elige 2 de estas 7
 * cartas, ordena estos triggers, reparte dano) se levanta esta capa. Oscurece
 * el fondo para que quede claro que el juego esta esperando por ti, que es lo
 * que hace Arena.
 *
 * <p>Mientras esta visible se traga los clicks: el motor esta bloqueado
 * esperando una respuesta concreta y clicar la mesa por detras no haria nada
 * util.
 */
public class Overlay extends StackPane {

    public Overlay() {
        getStyleClass().add("overlay");
        setAlignment(Pos.CENTER);
        setPadding(new Insets(40));
        setVisible(false);
        setManaged(false);
        // Traga los clicks que no vayan al dialogo.
        setPickOnBounds(true);
        setOnMouseClicked(e -> {
            e.consume();
            // SOLO cuenta como "click fuera" si de verdad se ha clicado el
            // fondo. Un click en un boton del dialogo BURBUJEA hasta aqui, y
            // sin esta comprobacion se trataba tambien como click de fondo: si
            // ese boton abria otro dialogo, el mismo click lo cerraba acto
            // seguido. Sintoma real: "cambiar edicion no hace nada".
            if (e.getTarget() == this && onBackgroundClick != null) {
                onBackgroundClick.run();
            }
        });
    }

    private Runnable onBackgroundClick;

    /**
     * Que hacer al clicar fuera del contenido.
     *
     * <p>Por defecto no hace nada: un dialogo que el motor esta esperando NO se
     * puede cerrar clicando al lado. Solo lo usa la carta ampliada, que es pura
     * consulta y se cierra a la minima.
     */
    public void setOnBackgroundClick(final Runnable handler) {
        this.onBackgroundClick = handler;
    }

    /** Muestra un dialogo centrado. */
    public void show(final Region content) {
        setPeeking(false);
        getChildren().setAll(content);
        if (peekable) {
            getChildren().add(peekPill);
        }
        setVisible(true);
        setManaged(true);
        toFront();
    }

    public void hide() {
        setPeeking(false);
        getChildren().clear();
        setVisible(false);
        setManaged(false);
    }

    // ---------- mirar la mesa sin contestar ----------

    /**
     * Si esta capa ofrece "Ver la mesa".
     *
     * <p>Pedido jugando: con un tutor, lo que buscas depende de lo que hay en la
     * mesa, y el dialogo la tapa entera. Solo la capa de los dialogos de la
     * partida lo activa; la carta ampliada y los menus no.
     */
    private boolean peekable;
    private boolean peeking;
    private final javafx.scene.control.Label peekPill = new javafx.scene.control.Label();
    private String peekText = "";
    private String backText = "";

    public void setPeekable(final String peek, final String back) {
        this.peekable = true;
        this.peekText = peek;
        this.backText = back;
        // Una etiqueta y no un boton: un boton se queda el foco y el siguiente
        // Espacio lo "pulsaria" en vez de ir al dialogo.
        peekPill.getStyleClass().add("peek-pill");
        peekPill.setText(peek);
        StackPane.setAlignment(peekPill, Pos.TOP_CENTER);
        // En la franja del margen, por encima del dialogo y sin taparlo.
        peekPill.setTranslateY(-30);
        peekPill.setOnMouseClicked(e -> {
            e.consume();
            setPeeking(!peeking);
        });
    }

    /**
     * El dialogo se esconde pero SIGUE ahi, con lo que llevaras marcado: la
     * capa deja de oscurecer y de tragarse el raton (menos la pastilla de
     * volver), y la mesa se puede mirar, ampliar y abrir sus zonas. Nada de
     * eso contesta al motor, que sigue esperando este dialogo.
     *
     * <p>{@link #isShowing()} sigue diciendo que si a proposito: los atajos de
     * partida (Espacio pasa la prioridad) no pueden despertarse mientras hay
     * una pregunta sin contestar.
     */
    public void setPeeking(final boolean on) {
        if (!peekable || peeking == on) {
            return;
        }
        peeking = on;
        for (final javafx.scene.Node n : getChildren()) {
            if (n != peekPill) {
                n.setVisible(!on);
            }
        }
        // Sin fondo NI recogida por limites: el raton atraviesa la capa.
        setStyle(on ? "-fx-background-color: null;" : "");
        setPickOnBounds(!on);
        peekPill.setText(on ? backText : peekText);
        peekPill.pseudoClassStateChanged(PEEKING, on);
    }

    public boolean isPeeking() {
        return peeking;
    }

    private static final javafx.css.PseudoClass PEEKING =
            javafx.css.PseudoClass.getPseudoClass("peeking");

    public boolean isShowing() {
        return isVisible();
    }
}
