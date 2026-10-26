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

    // ---------------------------------------------------------------
    // Poner y quitar la propia (Discord, 25-10-2026: "Use my own image" en
    // el constructor, para CUALQUIER carta, tenga o no imagen bajada)
    // ---------------------------------------------------------------

    private static final String[] EXTENSIONS = {".jpg", ".png"};

    /** La copia de la imagen que habia, apartada mientras se usa la propia. */
    private static File backupOf(final File image) {
        return new File(image.getPath() + ".original");
    }

    /** La marca de "esta imagen la ha puesto el jugador". */
    private static File markerOf(final File image) {
        return new File(image.getPath() + ".neo-own");
    }

    /** Si la imagen de esa clave la ha puesto el jugador. */
    public static boolean isOwn(final String imageKey) {
        for (final String ext : EXTENSIONS) {
            final File t = target(imageKey, ext);
            if (t != null && markerOf(t).isFile()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pone la imagen propia. Lo que habia (la bajada, en .jpg o .png) se
     * APARTA, no se borra, para poder volver con {@link #restore} sin internet;
     * y se aparta la de las dos extensiones, porque si quedara un .jpg al lado
     * del .png propio Forge seguiria leyendo el .jpg. Devuelve el fichero
     * escrito, o {@code null} si la clave no tiene sitio.
     */
    public static File install(final String imageKey, final java.io.InputStream source,
                               final String extension) throws java.io.IOException {
        final File dest = target(imageKey, extension);
        if (dest == null || source == null) {
            return null;
        }
        for (final String ext : EXTENSIONS) {
            final File t = target(imageKey, ext);
            if (t == null || !t.isFile()) {
                continue;
            }
            if (markerOf(t).isFile()) {
                // Una propia de antes: se sustituye.
                t.delete();
                markerOf(t).delete();
            } else if (!backupOf(t).exists()) {
                t.renameTo(backupOf(t));
            } else {
                t.delete();
            }
        }
        dest.getParentFile().mkdirs();
        java.nio.file.Files.copy(source, dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        markerOf(dest).createNewFile();
        forgetLookup(imageKey);
        return dest;
    }

    /**
     * Quita la imagen propia y devuelve la que habia. Sin copia apartada (la
     * carta no tenia imagen bajada), se queda sin imagen y el juego la vuelve a
     * pedir cuando haga falta. true si habia una propia.
     */
    public static boolean restore(final String imageKey) {
        boolean had = false;
        for (final String ext : EXTENSIONS) {
            final File t = target(imageKey, ext);
            if (t == null) {
                continue;
            }
            if (markerOf(t).isFile()) {
                had = true;
                t.delete();
                markerOf(t).delete();
            }
        }
        if (!had) {
            return false;
        }
        for (final String ext : EXTENSIONS) {
            final File t = target(imageKey, ext);
            if (t != null && backupOf(t).isFile() && !t.exists()) {
                backupOf(t).renameTo(t);
            }
        }
        forgetLookup(imageKey);
        return true;
    }

    /**
     * Que Forge vuelva a buscar el fichero de esa clave (Discord, 26-10-2026:
     * <i>"I don't think the image replacement works on mobile"</i>).
     * {@code ImageKeys.getImageFile} recuerda que fichero encontro para cada
     * nombre ({@code cachedCards}) y no mira si sigue existiendo: con una
     * propia en .png sobre una bajada en .jpg (apartada como .original), o al
     * volver a la original, seguia devolviendo el fichero que ya no esta, y la
     * carta salia sin imagen o se volvia a bajar encima. El mapa es privado:
     * reflexion, y si algun dia cambia, solo se pierde esto.
     */
    @SuppressWarnings("unchecked")
    private static void forgetLookup(final String imageKey) {
        try {
            final File base = target(imageKey, "");
            if (base == null) {
                return;
            }
            // Sin la extension: "SET/Nombre.full" cubre tambien ".fullborder".
            final String prefix = base.getPath().substring(0, base.getPath().length() - ".jpg".length());
            final java.lang.reflect.Field f = ImageKeys.class.getDeclaredField("cachedCards");
            f.setAccessible(true);
            final java.util.Map<String, File> cached = (java.util.Map<String, File>) f.get(null);
            synchronized (cached) {
                cached.values().removeIf(file -> file != null && file.getPath().startsWith(prefix));
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.out.println("[neo] no se ha podido olvidar la imagen de " + imageKey + ": " + e);
        }
    }

    // ---------------------------------------------------------------
    // La forma y el tamanyo (Ana, 25-10-2026: "que pasaria si ponen una foto
    // del tamanyo de un salvapantallas de windows?"). Se pintaria aplastada en
    // el hueco de una carta y ocuparia en memoria lo que diez. Asi que la
    // imagen se deja como una de Scryfall ANTES de guardarla: aqui solo las
    // cuentas, que el escritorio (ImageIO) y Android (Bitmap) dibujan cada uno
    // con lo suyo.
    // ---------------------------------------------------------------

    /** El tamanyo "large" de Scryfall: lo mas grande que se guarda. */
    public static final int MAX_WIDTH = 672;
    public static final int MAX_HEIGHT = 936;

    /** Cuanto se tolera de diferencia con la forma de una carta (5:7) antes de recortar. */
    private static final double TOLERANCE = 0.04;

    /**
     * El trozo de la imagen que se queda: entera si ya tiene forma de carta;
     * si no, el centro con forma de carta. {@code {x, y, ancho, alto}}.
     */
    public static int[] cardCrop(final int width, final int height) {
        final double card = 5.0 / 7.0;
        final double ratio = (double) width / height;
        if (Math.abs(ratio - card) / card <= TOLERANCE) {
            return new int[] {0, 0, width, height};
        }
        if (ratio > card) {
            // Mas ancha que una carta (un fondo de pantalla): sobra por los lados.
            final int w = (int) Math.round(height * card);
            return new int[] {(width - w) / 2, 0, w, height};
        }
        final int h = (int) Math.round(width / card);
        return new int[] {0, (height - h) / 2, width, h};
    }

    /** El tamanyo final de un trozo: como mucho {@link #MAX_WIDTH}x{@link #MAX_HEIGHT}, nunca agrandado. */
    public static int[] fittedSize(final int width, final int height) {
        final double scale = Math.min(1.0, Math.min((double) MAX_WIDTH / width, (double) MAX_HEIGHT / height));
        return new int[] {Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))};
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
