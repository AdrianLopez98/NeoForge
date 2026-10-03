package forge.neo.ui;

import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.Scene;

import forge.neo.NeoLanguage;

/**
 * <b>De derecha a izquierda</b>, para el arabe (1.0.11).
 *
 * <p>La regla, decidida con Ana: los MENUS se dan la vuelta, como cualquier
 * aplicacion en arabe; <b>la mesa y las cartas no</b>. La mesa es geometria de
 * juego (tu mano abajo, el rival arriba, la columna de la derecha) y una carta
 * es un objeto impreso: espejarlas no traduce nada, solo desorienta.
 *
 * <p><b>Que hace JavaFX al espejar</b> (medido con un programa de prueba antes
 * de escribir esto): da la vuelta a la COLOCACION — el orden de una fila, las
 * alineaciones, las posiciones puestas a mano en un {@code Pane} — pero no al
 * contenido: una imagen, un lienzo o un texto se ven igual. Por eso lo que hay
 * que proteger son los dibujos montados con piezas colocadas (una carta con su
 * pastilla de contadores arriba a la derecha, una bandera), no las imagenes.
 *
 * <p><b>Con cualquier otro idioma esto no hace nada</b>: {@link #on()} es
 * falso, ninguna llamada toca nada, y la escena queda como siempre.
 */
public final class Rtl {

    private Rtl() {
    }

    private static volatile Boolean on;

    /**
     * Si la interfaz va de derecha a izquierda. Se mira una vez: el idioma no
     * cambia sin reiniciar. Ante cualquier fallo, no: es como estaba todo.
     */
    public static boolean on() {
        Boolean b = on;
        if (b == null) {
            // Antes de que el motor tenga su interfaz puesta no se pregunta el
            // idioma: hacerlo tocaria ForgeConstants antes de tiempo, y ese
            // fallo no tiene vuelta atras. Se responde "no" y se vuelve a mirar
            // la proxima vez, ya con todo en su sitio.
            if (forge.gui.GuiBase.getInterface() == null) {
                return false;
            }
            try {
                b = NeoLanguage.isRightToLeft();
            } catch (final Throwable e) {
                b = Boolean.FALSE;
            }
            on = b;
        }
        return b;
    }

    /** La escena entera de derecha a izquierda, si toca. */
    public static void applyTo(final Scene scene) {
        if (on() && scene != null) {
            scene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        }
    }

    /**
     * Esto se dibuja como siempre aunque el resto vaya de derecha a izquierda:
     * la mesa, una carta, una bandera. Devuelve el mismo nodo.
     */
    public static <T extends Node> T keepLtr(final T node) {
        if (on() && node != null) {
            node.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        }
        return node;
    }

    /** Este texto o panel, de derecha a izquierda aunque este dentro de la mesa. */
    public static <T extends Node> T rtl(final T node) {
        if (on() && node != null) {
            node.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);
        }
        return node;
    }
}
