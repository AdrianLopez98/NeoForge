package forge.neo.card;

import java.io.File;
import java.text.Normalizer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import forge.ImageKeys;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.util.ImageUtil;

/**
 * EL ARTE SIN CONEXION: una imagen por NOMBRE de carta, bajada de antemano.
 *
 * <p>Sale de {@code tools/bajar-arte.py} del proyecto Android (decision 93 de
 * alli): ~36.000 cartas con su recorte de arte, ~3 GB, en
 * {@code <cache>/pics/arte/<letra>/<Nombre>.jpg} y {@code .artcrop.jpg}. Sirve
 * para jugar donde no hay internet - el pueblo con la portable, un vuelo con el
 * movil - con foto en vez de la carta dibujada.
 *
 * <p><b>Va DETRAS de la cache normal.</b> Si la impresion exacta (o la
 * traducida) esta en disco, gana ella: esto solo contesta cuando no hay otra
 * cosa. Y no se apoya en el "sin edicion" que ya trae
 * {@code ImageKeys.getImageFile}: ese camino lanza una busqueda asincrona por
 * {@code setLookup} que puede apuntar la clave en {@code missingCards} DESPUES
 * de haberla encontrado, y desde ahi la carta se queda sin imagen.
 *
 * <p>Clase pura, sin JavaFX, porque la usan las DOS interfaces: el
 * {@code CardImages} de aqui y el de Android. Y el nombre del fichero lo
 * calcula igual el script que lo baja: <b>si se toca {@link #fileName} o
 * {@link #folder}, se toca {@code nombre_fichero} y {@code carpeta} en
 * bajar-arte.py</b>.
 */
public final class OfflineArt {

    private OfflineArt() {
    }

    /**
     * Lo ya mirado, con "" para "no esta". La mesa pide la misma carta muchas
     * veces, y 70.000 ficheros en un disco USB - o en el almacenamiento
     * compartido de Android - no se miran gratis. El arte se copia con el
     * programa cerrado, asi que recordar un "no esta" toda la sesion no pierde
     * nada.
     */
    private static final Map<String, String> SEEN = new ConcurrentHashMap<>();

    /** La carpeta del arte: hermana de {@code pics/cards}. */
    public static File dir() {
        return new File(ForgeConstants.CACHE_DIR, "pics" + File.separator + "arte");
    }

    /**
     * El fichero de esa carta, si esta.
     *
     * @param imageKey clave de busqueda del motor, {@code c:Nombre|SET|1}, con
     *                 {@code $alt} si es la cara de atras
     * @param crop     el recorte de arte en vez de la carta entera
     */
    public static File find(final String imageKey, final boolean crop) {
        if (imageKey == null || !imageKey.startsWith(ImageKeys.CARD_PREFIX)) {
            return null;
        }
        final String seen = SEEN.computeIfAbsent(crop ? imageKey + "#crop" : imageKey, k -> {
            final File f = lookup(imageKey, crop);
            return f == null ? "" : f.getPath();
        });
        return seen.isEmpty() ? null : new File(seen);
    }

    private static File lookup(final String imageKey, final boolean crop) {
        final File dir = dir();
        if (!dir.isDirectory()) {
            return null;
        }
        final String name = cardName(imageKey);
        if (name == null) {
            return null;
        }
        final String clean = fileName(name);
        if (clean.isEmpty()) {
            return null;
        }
        final File f = new File(new File(dir, folder(clean)), clean + (crop ? ".artcrop.jpg" : ".jpg"));
        return f.exists() ? f : null;
    }

    /**
     * El nombre con el que se guardo la imagen.
     *
     * <p>La trasera de una doble cara, flip o meld tiene su propia foto con su
     * propio nombre; lo demas - partidas, aventuras - es una sola foto con el
     * nombre de arriba, que es el que Scryfall da a la primera cara.
     */
    static String cardName(final String imageKey) {
        try {
            final PaperCard pc = ImageUtil.getPaperCardFromImageKey(imageKey);
            if (pc == null) {
                return null;
            }
            return imageKey.endsWith(ImageKeys.BACKFACE_POSTFIX)
                    ? ImageUtil.getNameToUse(pc, "back")
                    : pc.getRules().getMainPart().getName();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Sin tildes y sin lo que Windows o Android no admiten en un nombre. */
    public static String fileName(final String name) {
        String n = Normalizer.normalize(name, Normalizer.Form.NFKD).replaceAll("\\p{Mn}+", "");
        n = n.replaceAll("[\"*/:<>?\\\\|]", "").trim();
        while (n.endsWith(".")) {
            n = n.substring(0, n.length() - 1);
        }
        return n;
    }

    /** Una carpeta por inicial: 70.000 ficheros en una sola van lentos. */
    public static String folder(final String clean) {
        final char c = Character.toLowerCase(clean.charAt(0));
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? String.valueOf(c) : "_";
    }
}
