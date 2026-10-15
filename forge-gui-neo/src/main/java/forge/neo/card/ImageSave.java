package forge.neo.card;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import javax.imageio.ImageIO;

/**
 * Guardar una imagen recien bajada: <b>tal cual llega</b>, despues de mirar que
 * es una imagen.
 *
 * <p>Discord, 14-10-2026, con la calidad "Maxima": <i>"The card art is not
 * downloading on demand, it only shows if I first download the whole set. The
 * Re-download Image button ... returns Couldn't reach Scryfall"</i>. La descarga
 * de una carta suelta ({@code NeoImageFetcher.doFetch}) hacia lo mismo que el
 * Swing de Forge: {@code ImageIO.read} y {@code ImageIO.write(img, "jpg", ...)}.
 * El PNG de Scryfall trae <b>transparencia</b> (las esquinas redondeadas), y el
 * escritor JPG de Java no sabe escribir una imagen con alfa: devuelve
 * {@code false} sin escribir nada. O sea que en "Maxima" no bajaba ni una carta
 * suelta, y el boton de volver a bajarla lo contaba como "sin red". Lo de la
 * expansion entera si salia porque eso lo baja el descargador de Forge, que
 * copia los bytes tal cual.
 *
 * <p>Y ademas volver a escribir en JPG <b>perdia calidad</b> en las otras dos:
 * recomprimir un JPG lo vuelve a comprimir (calidad 0,75 de fabrica). Asi que
 * ahora se hace como el descargador de Forge: los bytes de Scryfall, sin tocar.
 * Lo que si se conserva es mirar ANTES que se puede decodificar, para no dejar
 * en la cache una pagina de error servida con un 200 o una descarga cortada.
 *
 * <p>El fichero se sigue llamando {@code .jpg} aunque dentro haya un PNG: es el
 * nombre con el que lo buscan Forge y {@link ImageQuality#exactFile}, y quien lo
 * abre (JavaFX, {@code ImageIO}, cualquier visor) mira el contenido, no la
 * extension. Es lo mismo que ya hacia la descarga por expansion.
 *
 * <p>Sin motor ni JavaFX: lo prueba {@code run.cmd imagecheck}.
 */
public final class ImageSave {

    private ImageSave() {
    }

    /**
     * Escribe {@code bytes} en {@code destination} si son una imagen que se
     * puede decodificar. Pasa por un {@code .tmp} para que nadie lea una
     * descarga a medias; si algo falla, lo que hubiera en {@code destination}
     * se queda como estaba.
     *
     * @return si ha quedado guardada
     */
    public static boolean save(final byte[] bytes, final File destination) throws IOException {
        if (bytes == null || bytes.length == 0
                || ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
            return false;
        }
        final File tmp = new File(destination.getPath() + ".tmp");
        final File parent = tmp.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(tmp.toPath(), bytes);
        if (destination.exists()) {
            destination.delete();
        }
        if (!tmp.renameTo(destination)) {
            tmp.delete();
            return false;
        }
        return true;
    }
}
