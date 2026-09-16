package forge.neo.platform;

import java.util.Locale;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.input.GestureEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

/**
 * En que sistema corre el juego, y lo poco que cambia por eso.
 *
 * <p><b>Existe por el Mac.</b> La interfaz se hizo en Windows y da por hecho
 * sus gestos: Ctrl es el modificador de todo (deshacer, acercar la mesa,
 * moverla) y el clic derecho es un boton. En un Mac las dos cosas son
 * distintas:
 *
 * <ul>
 *   <li><b>Cmd hace de Ctrl.</b> Nadie en un Mac deshace con Ctrl+Z. Aqui Cmd
 *       vale lo mismo que Ctrl, asi que los atajos guardados ("Ctrl+Z") y los
 *       textos siguen valiendo tal cual.</li>
 *   <li><b>Ctrl+clic ES el clic derecho.</b> Con un trackpad sin el clic
 *       secundario puesto, o un raton de un boton, es la unica forma de
 *       hacerlo. JavaFX lo entrega como clic IZQUIERDO con Ctrl, y en esta mesa
 *       un clic izquierdo sobre una carta la juega — y jugar una tierra no se
 *       deshace. Por eso se convierte en clic derecho antes de que llegue a
 *       nadie ({@link #installMacMouse}). Mover la mesa acercada pasa a ser
 *       Cmd+arrastrar.</li>
 * </ul>
 *
 * <p><b>Todo va detras de {@link #MAC}.</b> En Windows cada metodo de aqui
 * devuelve exactamente lo que devolvia el codigo de antes: el modo nuevo no
 * puede romper el viejo porque no cambia su camino.
 */
public final class NeoOs {

    public static final boolean MAC = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT).contains("mac");

    private NeoOs() {
    }

    /** Ctrl, o en Mac tambien Cmd. */
    public static boolean ctrl(final MouseEvent e) {
        return e.isControlDown() || (MAC && e.isMetaDown());
    }

    /** Ctrl, o en Mac tambien Cmd. Vale para la rueda y los gestos del trackpad. */
    public static boolean ctrl(final GestureEvent e) {
        return e.isControlDown() || (MAC && e.isMetaDown());
    }

    /** Ctrl, o en Mac tambien Cmd. */
    public static boolean ctrl(final KeyEvent e) {
        return e.isControlDown() || (MAC && e.isMetaDown());
    }

    /**
     * El gesto de mover la mesa acercada con el boton izquierdo.
     *
     * <p>En Windows, Ctrl. En Mac, Cmd: Ctrl+clic ya es el clic derecho y no
     * puede significar ademas "arrastrar la mesa".
     */
    public static boolean panModifier(final MouseEvent e) {
        return MAC ? e.isMetaDown() : e.isControlDown();
    }

    /** Como se llama la tecla de Ctrl para ensenyarla: "Cmd" en un Mac. */
    public static String ctrlLabel() {
        return MAC ? "Cmd" : "Ctrl";
    }

    /**
     * Ctrl+clic izquierdo pasa a ser clic derecho, en toda la ventana.
     *
     * <p>Va en la escena, que es por donde pasa cualquier clic antes que por
     * ningun nodo: asi valen igual el zoom de carta, los menus de click
     * derecho del deck builder y el mapa de Ascenso, sin tocar ninguno. El
     * evento de antes se consume y se lanza otro identico salvo el boton, por
     * el mismo camino — para quien lo recibe es un clic derecho de verdad.
     *
     * <p>No hace nada si JavaFX ya lo entrega como clic derecho: entonces no
     * llega nunca un izquierdo con Ctrl.
     */
    public static void installMacMouse(final Scene scene) {
        if (!MAC) {
            return;
        }
        scene.addEventFilter(MouseEvent.ANY, e -> {
            if (e.getButton() != MouseButton.PRIMARY || !e.isControlDown() || e.isMetaDown()) {
                return;
            }
            if (e.getEventType() != MouseEvent.MOUSE_PRESSED
                    && e.getEventType() != MouseEvent.MOUSE_RELEASED
                    && e.getEventType() != MouseEvent.MOUSE_CLICKED) {
                return;
            }
            e.consume();
            final boolean down = e.getEventType() == MouseEvent.MOUSE_PRESSED;
            final MouseEvent secondary = new MouseEvent(scene, e.getTarget(), e.getEventType(),
                    e.getSceneX(), e.getSceneY(), e.getScreenX(), e.getScreenY(),
                    MouseButton.SECONDARY, e.getClickCount(),
                    e.isShiftDown(), false, e.isAltDown(), false,
                    false, e.isMiddleButtonDown(), down,
                    e.isBackButtonDown(), e.isForwardButtonDown(),
                    e.isSynthesized(), e.isPopupTrigger(), e.isStillSincePress(),
                    e.getPickResult());
            Event.fireEvent(e.getTarget(), secondary);
        });
    }

    /**
     * Abrir una direccion en el navegador, en un Mac.
     *
     * <p>{@code java.awt.Desktop} arranca AWT, y en un Mac AWT y JavaFX se
     * disputan el hilo principal de la aplicacion. {@code open} es lo que usa
     * el propio sistema y no despierta nada.
     */
    public static boolean openOnMac(final String url) {
        if (!MAC) {
            return false;
        }
        try {
            new ProcessBuilder("open", url).start();
            return true;
        } catch (final java.io.IOException e) {
            System.err.println("[neo] no se ha podido abrir " + url + ": " + e);
            return true;
        }
    }
}
