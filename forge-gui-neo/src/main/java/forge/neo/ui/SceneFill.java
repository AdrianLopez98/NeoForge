package forge.neo.ui;

import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Shape;

/**
 * Un relleno que solo esta puesto mientras la figura esta en pantalla.
 *
 * <p>Para las {@code ImagePattern} de imagenes COMPARTIDAS (los avatares y las
 * fundas salen de {@code Sprites}, que las guarda para siempre). Si la imagen es
 * una {@code WritableImage}, JavaFX la da por "cambiante" y el {@code Shape} se
 * apunta como oyente EN LA IMAGEN — con un oyente fuerte que no se quita hasta
 * que se cambia el relleno. Una imagen que vive para siempre retenia asi el
 * circulo, su barra y la mesa entera de cada partida.
 *
 * <p>Encontrado con un volcado del heap el 25-09-2026, persiguiendo la pantalla
 * en blanco de la Aventura (VRAM subiendo duelo a duelo): la cadena era
 * {@code Sprites.avatarCache -> WritableImage -> Circle -> PlayerBar -> TableScreen}.
 */
final class SceneFill {

    private SceneFill() {
    }

    private static final String PAINT = "neo.sceneFill.paint";
    private static final String HOOKED = "neo.sceneFill.hooked";

    /** Pone {@code paint} (o lo quita, con null) atado a que la figura este en escena. */
    static void set(final Shape shape, final Paint paint) {
        shape.getProperties().put(PAINT, paint);
        if (!shape.getProperties().containsKey(HOOKED)) {
            shape.getProperties().put(HOOKED, Boolean.TRUE);
            final ChangeListener<Scene> onScene = (o, was, is) -> apply(shape);
            shape.sceneProperty().addListener(onScene);
        }
        apply(shape);
    }

    private static void apply(final Shape shape) {
        final Object paint = shape.getProperties().get(PAINT);
        shape.setFill(shape.getScene() == null ? null : (Paint) paint);
    }
}
