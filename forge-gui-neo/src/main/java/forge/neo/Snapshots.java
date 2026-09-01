package forge.neo;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

/**
 * Guarda una captura de la escena a PNG.
 *
 * <p>Sirve para verificar la interfaz sin tener que mirar la pantalla: util al
 * iterar la estetica, y la unica forma de comprobar el resultado sin abrir
 * la ventana a mano.
 *
 * <p>La conversion se hace a mano en vez de con {@code SwingFXUtils} para no
 * arrastrar el modulo {@code javafx-swing} solo por esto.
 */
public final class Snapshots {

    private Snapshots() {
    }

    public static void writePng(final Image fxImage, final File dest) throws IOException {
        final int w = (int) Math.round(fxImage.getWidth());
        final int h = (int) Math.round(fxImage.getHeight());
        if (w <= 0 || h <= 0) {
            throw new IOException("captura vacia");
        }

        final BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        final PixelReader reader = fxImage.getPixelReader();
        if (reader == null) {
            throw new IOException("la imagen no tiene PixelReader");
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }

        final File parent = dest.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        if (!ImageIO.write(out, "png", dest)) {
            throw new IOException("no hay codificador PNG");
        }
    }
}
