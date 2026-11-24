package forge.neo.ui;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * Base de las pantallas de Rogue Commander: el contenido y DOS capas encima.
 *
 * <ul>
 *   <li>{@link #dialogs()}: lo que CONTESTA a la logica del modo (premios,
 *       eventos, bazar, cofre...). Solo una cosa a la vez: la logica espera.</li>
 *   <li>{@link #consult()}: lo que solo se MIRA (el mazo) mientras hay una
 *       pregunta abierta. Va por encima y al cerrarla vuelve la pregunta.</li>
 * </ul>
 *
 * <p>Son dos por el principio 10 de las notas de diseño: abrir algo de consulta en la
 * capa de las preguntas borra la pregunta de la pantalla, y la logica se queda
 * esperando una respuesta que ya no se puede dar.
 */
public class RogueScreen extends StackPane {

    private final Overlay dialogs = new Overlay();
    private final Overlay consult = new Overlay();

    public RogueScreen() {
        getStyleClass().addAll("rogue-screen");
        dialogs.setVisible(false);
        consult.setVisible(false);
        // Clic derecho en cualquier carta = la carta grande, como en todo el
        // juego. Faltaba (24-11-2026, Ana: "no puedo hacerlas grandes para
        // leerlas" en los premios). Ver NeoAppRogue.shown().
        CardZoom.install(this);
    }

    /** Pone el contenido de la pantalla, por debajo de las dos capas. */
    protected void setContent(final Region content) {
        getChildren().setAll(content, dialogs, consult);
    }

    public Overlay dialogs() {
        return dialogs;
    }

    public Overlay consult() {
        return consult;
    }
}
