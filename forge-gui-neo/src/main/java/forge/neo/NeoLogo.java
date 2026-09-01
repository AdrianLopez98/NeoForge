package forge.neo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

/**
 * El logo de la aplicacion.
 *
 * <p>Vive en {@code resources/forge/neo/logo/} en ocho tamanyos, de 16 a 512.
 * <b>Y hacen falta los ocho</b>: Windows no coge uno y lo escala — pide el
 * tamanyo que necesita para cada sitio (16 en la barra de titulo, 32 en la
 * barra de tareas, 256 en Alt+Tab), y si solo damos el grande lo encoge con un
 * algoritmo malo y el icono sale sucio. Un PNG por tamanyo, redimensionado
 * bien, es la diferencia entre parecer una aplicacion y parecer un atajo.
 *
 * <p>Las imagenes se cargan <b>una sola vez</b> y se reparten: {@code Image} de
 * JavaFX es inmutable y se puede compartir entre nodos, asi que no hay motivo
 * para decodificar el mismo PNG en cada pantalla.
 *
 * <p>⚠️ Como todo lo que sea {@code javafx.scene.image.Image}, esto
 * <b>necesita ventana</b> (ver {@code Sprites}): no se puede llamar desde los
 * comprobadores sin interfaz. Por eso {@link #icons()} devuelve una lista vacia
 * si algo falla en vez de reventar — un icono que no sale es un detalle, y no
 * puede llevarse por delante un arranque.
 */
public final class NeoLogo {

    private NeoLogo() {
    }

    private static final String DIR = "/forge/neo/logo/logo-";

    /** Los tamanyos que hay en disco, de menor a mayor. */
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256, 512};

    private static List<Image> icons;
    private static Image large;

    /**
     * Todos los tamanyos del icono, para {@code Stage.getIcons()}.
     *
     * <p>JavaFX se los pasa al sistema tal cual y es Windows quien elige cual
     * usar en cada sitio.
     */
    public static synchronized List<Image> icons() {
        if (icons != null) {
            return icons;
        }
        final List<Image> out = new ArrayList<>();
        for (final int size : SIZES) {
            final Image image = load(size);
            if (image != null) {
                out.add(image);
            }
        }
        icons = out;
        return out;
    }

    /** El logo grande, para ensenyarlo en una pantalla. */
    public static synchronized Image large() {
        if (large == null) {
            large = load(512);
        }
        return large;
    }

    /**
     * El logo como nodo, a la altura pedida.
     *
     * <p>Es cuadrado, asi que ancho = alto y no hace falta decir los dos.
     * Devuelve {@code null} si no hay logo, para que quien lo pida pueda
     * seguir sin el.
     */
    public static ImageView view(final double size) {
        final Image image = large();
        if (image == null) {
            return null;
        }
        final ImageView view = new ImageView(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setMouseTransparent(true);
        return view;
    }

    /** Le pone el icono a una ventana. Vale para la principal y para cualquier dialogo. */
    public static void applyTo(final Stage stage) {
        if (stage == null) {
            return;
        }
        final List<Image> all = icons();
        if (all.isEmpty()) {
            // Que se vea. Un icono que no carga no rompe nada, pero si se calla
            // se queda asi para siempre: lo unico que se nota es que la barra de
            // tareas ensenya el cafe de Java.
            System.err.println("[logo] no se ha podido cargar ningun tamanyo de "
                    + DIR + "*.png");
            return;
        }
        stage.getIcons().setAll(all);
    }

    private static Image load(final int size) {
        try (InputStream in = NeoLogo.class.getResourceAsStream(DIR + size + ".png")) {
            if (in == null) {
                return null;
            }
            final Image image = new Image(in);
            return image.isError() ? null : image;
        } catch (final Exception e) {
            // Sin ventana (los comprobadores) o sin fichero: se sigue sin logo.
            return null;
        }
    }
}
