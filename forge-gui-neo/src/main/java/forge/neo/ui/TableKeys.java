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
        numberedChoices(scene, table);
    }

    /**
     * <b>Las teclas 1 a 9 contestan la pregunta del motor</b> que haya en la
     * capa de dialogos ({@link NumberedChoice}): la opcion N, y mantenida, la
     * de la pregunta siguiente (Discord, 16-10-2026, para las macros).
     *
     * <p>Va aqui y no en los atajos porque tiene que valer en las dos mesas
     * (ver arriba). Solo con la capa de los dialogos del MOTOR delante: con el
     * menu de pausa o una carta ampliada encima, el numero no puede contestar
     * una pregunta que no se esta viendo (principio 10). Ni escribiendo en un
     * buscador, ni con Mayus/Ctrl/Alt: Mayus+2 y Mayus+3 son las macros.
     */
    private static void numberedChoices(final Scene scene, final Supplier<TableScreen> table) {
        final java.util.Set<KeyCode> held = new java.util.HashSet<>();
        scene.addEventFilter(KeyEvent.KEY_PRESSED, ev -> {
            final int n = digit(ev);
            if (n <= 0) {
                return;
            }
            final boolean repeat = !held.add(ev.getCode());
            final TableScreen now = table.get();
            if (now == null || now.getScene() != scene || now.isConsultShowing()) {
                return;
            }
            if (scene.getFocusOwner() instanceof javafx.scene.control.TextInputControl) {
                return;
            }
            final Overlay overlay = now.getOverlay();
            if (!overlay.isShowing() || overlay.isPeeking()) {
                return;
            }
            final NumberedChoice choice = find(overlay.getContent(), 4);
            if (choice != null && choice.pickNumber(n, repeat)) {
                ev.consume();
            }
        });
        scene.addEventFilter(KeyEvent.KEY_RELEASED, ev -> held.remove(ev.getCode()));
    }

    /** 1 a 9 (fila de arriba o teclado numerico) sin modificadores; si no, 0. */
    private static int digit(final KeyEvent ev) {
        if (ev.isShiftDown() || ev.isControlDown() || ev.isAltDown() || ev.isMetaDown()
                || ev.isShortcutDown()) {
            return 0;
        }
        final KeyCode c = ev.getCode();
        if (c == null) {
            return 0;
        }
        if (c.ordinal() >= KeyCode.DIGIT1.ordinal() && c.ordinal() <= KeyCode.DIGIT9.ordinal()) {
            return c.ordinal() - KeyCode.DIGIT1.ordinal() + 1;
        }
        if (c.ordinal() >= KeyCode.NUMPAD1.ordinal() && c.ordinal() <= KeyCode.NUMPAD9.ordinal()) {
            return c.ordinal() - KeyCode.NUMPAD1.ordinal() + 1;
        }
        return 0;
    }

    /** El dialogo numerado, aunque venga envuelto (pocos niveles: es un dialogo, no la mesa). */
    private static NumberedChoice find(final Node node, final int depth) {
        if (node instanceof NumberedChoice c) {
            return c;
        }
        if (depth > 0 && node instanceof javafx.scene.Parent p) {
            for (final Node child : p.getChildrenUnmodifiable()) {
                final NumberedChoice c = find(child, depth - 1);
                if (c != null) {
                    return c;
                }
            }
        }
        return null;
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
