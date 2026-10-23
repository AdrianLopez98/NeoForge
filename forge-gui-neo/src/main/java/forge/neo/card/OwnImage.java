package forge.neo.card;

import java.io.File;
import java.util.Locale;

import forge.ImageKeys;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.util.ImageUtil;

/**
 * <b>Donde va la imagen de una carta o ficha que no la tiene</b>, para poner
 * una propia (Discord, 23-10-2026: <i>"some kind of indicator added telling me
 * where to put the missing image and what it's filename should be OR ... a way
 * to import a missing image in real time"</i>).
 *
 * <p>Es el MISMO fichero en el que la dejaria el descargador de Forge
 * ({@code ImageFetcher}): las cartas, {@code <cache>/pics/cards/<clave de la
 * impresion>.jpg}; las fichas, {@code <cache>/pics/tokens/<SET>/[<numero>_]<nombre>.jpg}
 * — con la expansion completada por {@link TokenKeys}, que si no la ficha se
 * buscaria donde no la busca nadie. Asi la imagen propia la encuentra
 * {@code ImageKeys.getImageFile} sin nada nuestro por medio, y la ve tambien
 * el Forge de siempre.
 *
 * <p>Pura y en el jar: la usan {@code CardImages} y NeoForge Android.
 */
public final class OwnImage {

    private OwnImage() {
    }

    /**
     * El fichero donde va la imagen de esa clave, con esa extension
     * ({@code ".jpg"} o {@code ".png"}, las dos que lee Forge), o
     * {@code null} si la clave no es de una carta ni de una ficha.
     */
    public static File target(final String imageKey, final String extension) {
        final String rel = relative(imageKey);
        if (rel == null) {
            return null;
        }
        final String ext = ".png".equalsIgnoreCase(extension) ? ".png" : ".jpg";
        return imageKey.startsWith(ImageKeys.TOKEN_PREFIX)
                ? new File(ForgeConstants.CACHE_TOKEN_PICS_DIR, rel + ext)
                : new File(ForgeConstants.CACHE_CARD_PICS_DIR, rel + ext);
    }

    /** Lo que se le ensenya al jugador: la ruta desde la carpeta de imagenes ("tokens/FRC/gingerbrute.jpg"). */
    public static String shownPath(final String imageKey) {
        final String rel = relative(imageKey);
        if (rel == null) {
            return null;
        }
        return (imageKey.startsWith(ImageKeys.TOKEN_PREFIX) ? "tokens/" : "cards/") + rel + ".jpg";
    }

    /** La extension de un fichero elegido, si es una de las que sirven; si no, {@code null}. */
    public static String extensionOf(final String fileName) {
        if (fileName == null) {
            return null;
        }
        final String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return ".jpg";
        }
        return lower.endsWith(".png") ? ".png" : null;
    }

    /** La ruta sin la carpeta base ni la extension, como la calcula el descargador. */
    private static String relative(final String rawKey) {
        if (rawKey == null || rawKey.isEmpty()) {
            return null;
        }
        if (rawKey.startsWith(ImageKeys.TOKEN_PREFIX)) {
            String key = TokenKeys.complete(rawKey);
            String face = "";
            if (key.endsWith(ImageKeys.BACKFACE_POSTFIX)) {
                face = "_back";
                key = key.substring(0, key.length() - ImageKeys.BACKFACE_POSTFIX.length());
            }
            final String[] parts = key.substring(ImageKeys.TOKEN_PREFIX.length()).split("\\|");
            final StringBuilder sb = new StringBuilder();
            if (parts.length > 1) {
                sb.append(parts[1]).append('/');
            }
            if (parts.length > 2) {
                sb.append(parts[2]).append('_');
            }
            sb.append(parts[0]);
            if (parts.length <= 2) {
                sb.append(face);
            }
            return sb.toString();
        }
        if (rawKey.startsWith(ImageKeys.CARD_PREFIX)) {
            try {
                final PaperCard pc = ImageUtil.getPaperCardFromImageKey(rawKey);
                if (pc == null) {
                    return null;
                }
                return rawKey.endsWith(ImageKeys.BACKFACE_POSTFIX) ? pc.getCardAltImageKey() : pc.getCardImageKey();
            } catch (final RuntimeException e) {
                return null;
            }
        }
        return null;
    }
}
