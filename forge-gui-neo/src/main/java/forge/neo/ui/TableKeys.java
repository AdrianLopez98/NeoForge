package forge.neo.ui;

import java.util.function.Predicate;
import java.util.function.Supplier;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

/**
 * El teclado de la mesa que <b>no</b> es una lista de atajos.
 *
 * <p>Hay una sola cosa aqui, y esta aqui porque tiene que valer para TODAS las
 * partidas: la mesa se monta desde {@code NeoApp} (menu, Ascenso, torneo,
 * red...) y tambien desde {@code NeoDuelBridge} (el duelo de la Aventura), que
 * vive en <b>otra ventana y otra escena</b>. Lo que se escriba en un solo sitio
 * no lo tiene el otro, y eso ya ha costado un fallo — este mismo, dos veces.
 * Ver el principio 8 de las notas de diseño.
 */
public final class TableKeys {

    private TableKeys() {
    }

    /**
     * <b>Espacio y Enter sobre un boton de la mesa que se ha quedado con el
     * foco.</b>
     *
     * <p>Un boton de JavaFX se "pulsa" con Espacio, y se queda la tecla antes
     * de que llegue a los atajos. Consecuencia: basta con haber clicado UNA vez
     * el boton del registro para que cada Espacio vuelva a abrirlo, en vez de
     * pasar la prioridad. Reportado dos veces por dos caminos distintos: en la
     * mesa normal el 15-09-2026 (<i>"every time I press space to close the pop
     * ups it opens the Log"</i>) y en el duelo de la <b>Aventura</b> el
     * 20-09-2026, que se habia quedado sin esta guardia porque monta su escena
     * aparte.
     *
     * <p>En la mesa, y sin nada modal encima, Espacio y Enter son
     * <b>atajos de partida</b> — nunca "pulsar lo que tenga el foco". Con algo
     * modal delante se deja pasar: ahi Espacio sobre el boton del dialogo es
     * justo lo que se quiere.
     *
     * <p>Va como <b>filtro</b> de la escena y no como manejador: un filtro baja
     * desde la raiz, asi que llega antes que el boton. Un manejador llegaria
     * despues de que el boton ya se hubiera disparado, que es el fallo entero.
     *
     * @param table    la mesa de ahora mismo; se pregunta cada vez porque en
     *                 {@code NeoApp} la pantalla cambia bajo los pies
     * @param shortcut que hagan las teclas cuando el foco no manda
     * @param onRelease rearmar los atajos que no se repiten al soltar
     */
    public static void guardFocusedButtons(final Scene scene,
                                           final Supplier<TableScreen> table,
                                           final Predicate<KeyEvent> shortcut,
                                           final Runnable onRelease) {
        if (scene == null || table == null) {
            return;
        }
        final javafx.event.EventHandler<KeyEvent> guard = ev -> {
            final KeyCode code = ev.getCode();
            if (code != KeyCode.SPACE && code != KeyCode.ENTER) {
                return;
            }
            final TableScreen now = table.get();
            if (now == null || now.getScene() == null || now.isModalShowing()) {
                return;
            }
            final Node owner = scene.getFocusOwner();
            if (!(owner instanceof ButtonBase) || !isInside(owner, now)) {
                return;
            }
            ev.consume();
            if (ev.getEventType() == KeyEvent.KEY_PRESSED) {
                if (shortcut != null) {
                    shortcut.test(ev);
                }
            } else if (onRelease != null) {
                onRelease.run();
            }
        };
        scene.addEventFilter(KeyEvent.KEY_PRESSED, guard);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, guard);
    }

    /** ¿Este nodo cuelga de la mesa? (el foco puede estar en otra pantalla). */
    private static boolean isInside(final Node node, final Node root) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == root) {
                return true;
            }
        }
        return false;
    }
}
