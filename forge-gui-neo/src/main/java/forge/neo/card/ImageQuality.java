package forge.neo.card;

import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoSettings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A que tamanyo se bajan las imagenes de carta de Scryfall (Ajustes ->
 * Imagenes, solo en el escritorio).
 *
 * <p>Discord, 13-10-2026: <i>"the resolution of the images seems a bit low, even
 * with the HD option selected and the cards look/feel blurry"</i>. Forge pide
 * siempre la version {@code normal} (488x680, fijo en
 * {@code ImageUtil.getScryfallDownloadUrl}), y la carta ampliada en una
 * pantalla de 1080p la estira al doble. Aqui se cambia la URL al pedirla:
 * {@code large} (672x936) o {@code png} (745x1040). La carta en pantalla mide
 * lo mismo — su tamanyo lo pone la mesa, y las tres versiones tienen la misma
 * proporcion —; lo que cambia es el detalle.
 *
 * <p>Lo usan los tres caminos por los que se baja una imagen:
 * {@code NeoImageFetcher.doFetch} (todas las de la partida y los menus),
 * {@link ArtDownload} (la descarga de todas) y {@link ArtHdScan} ("buscar
 * imagenes mejores", que si no, volveria a dejar la normal encima). Las ya
 * bajadas se quedan como estan.
 *
 * <p><b>Cada calidad en su carpeta</b> (Discord, 13-10-2026: bajarse las tres y
 * elegir en cada partida cual se ENSENYA — la normal para un Commander lleno de
 * fichas, la maxima para un uno contra uno). La normal se queda donde siempre
 * ({@code pics/cards} y {@code pics/arte}, las de Forge: no se pierde nada de
 * lo ya bajado); las otras, al lado: {@code cards-large}, {@code arte-png}...
 * El selector decide que se busca primero ({@link #lookupOrder}): la elegida, y
 * si falta, la mejor que haya. Las fichas no cambian: van siempre a la normal.
 *
 * <p>Lo que gasta MEMORIA son los pixeles, no lo que pesa el fichero: una carta
 * en pantalla ocupa ~1,3 MB en normal, ~2,5 MB en alta y ~3,1 MB en maxima.
 *
 * <p>Android no tiene el ajuste (decision del autor: solo PC): sin nada escrito
 * en {@link NeoSettings#IMAGE_QUALITY}, todo sigue en {@link #NORMAL} y en las
 * carpetas de siempre.
 */
public enum ImageQuality {
    /** 488x680, unos 80 KB por carta. Lo de siempre. */
    NORMAL("normal", 1),
    /** 672x936, unos 150 KB (medido: 147 KB). */
    LARGE("large", 2),
    /** 745x1040 en PNG, alrededor de 1,5 MB por carta (medido: 1,46 MB). */
    PNG("png", 18);

    /** El nombre de la version en Scryfall (y la clave en {@code image_uris}). */
    public final String scryfall;

    /** Cuanto pesa respecto a la normal, para avisar de lo que ocupa una descarga. */
    public final int weight;

    ImageQuality(final String scryfall, final int weight) {
        this.scryfall = scryfall;
        this.weight = weight;
    }

    /** La elegida en Ajustes; {@link #NORMAL} si no hay nada o no se entiende. */
    public static ImageQuality current() {
        final String v = NeoSettings.get(NeoSettings.IMAGE_QUALITY, NORMAL.scryfall);
        for (final ImageQuality q : values()) {
            if (q.scryfall.equalsIgnoreCase(v == null ? "" : v.trim())) {
                return q;
            }
        }
        return NORMAL;
    }

    /** Esta URL de Scryfall, pidiendo la calidad elegida. Lo que no sea una imagen normal se queda igual. */
    public static String apply(final String url) {
        return current().rewrite(url);
    }

    /** Las carpetas donde se busca, en orden: la elegida y luego de mejor a peor. */
    public static List<ImageQuality> lookupOrder() {
        final ImageQuality cur = current();
        final List<ImageQuality> out = new ArrayList<>(3);
        out.add(cur);
        for (final ImageQuality q : new ImageQuality[] {PNG, LARGE, NORMAL}) {
            if (q != cur) {
                out.add(q);
            }
        }
        return out;
    }

    /** La carpeta de las impresiones exactas ({@code pics/cards} en la normal). */
    public File cardsDir() {
        final File base = new File(ForgeConstants.CACHE_CARD_PICS_DIR);
        return this == NORMAL ? base : new File(base.getParentFile(), base.getName() + "-" + scryfall);
    }

    /** La carpeta del arte por nombre de la descarga de Ajustes ({@code pics/arte} en la normal). */
    public File artDir() {
        final File base = OfflineArt.dir();
        return this == NORMAL ? base : new File(base.getParentFile(), base.getName() + "-" + scryfall);
    }

    /**
     * Esta impresion en esta calidad, si esta en disco. La normal la busca el
     * motor ({@code ImageKeys}); las otras se buscan por el mismo nombre con el
     * que las guarda el descargador: {@code SET/Nombre.fullborder.jpg} (o
     * {@code .full.jpg}).
     *
     * @param relative la ruta relativa sin extension ({@code PaperCard.getCardImageKey})
     */
    public File exactFile(final String relative) {
        if (this == NORMAL || relative == null || relative.isEmpty()) {
            return null;
        }
        final File dir = cardsDir();
        if (!dir.isDirectory()) {
            return null;
        }
        final String border = relative.endsWith(".full")
                ? relative.substring(0, relative.length() - 5) + ".fullborder" : null;
        if (border != null) {
            final File f = new File(dir, border + ".jpg");
            if (f.exists()) {
                return f;
            }
        }
        final File f = new File(dir, relative + ".jpg");
        return f.exists() ? f : null;
    }

    /**
     * Donde se guarda de verdad una imagen que iba a la carpeta normal: la misma
     * ruta en la carpeta de esta calidad. Lo que no va a {@code pics/cards} ni a
     * {@code pics/arte} (fichas, sobres, fundas) se queda donde iba.
     */
    public String redirect(final String destPath) {
        if (this == NORMAL || destPath == null) {
            return destPath;
        }
        final String moved = swapRoot(destPath, NORMAL.cardsDir(), cardsDir());
        return moved != null ? moved : nonNull(swapRoot(destPath, NORMAL.artDir(), artDir()), destPath);
    }

    /** Si la ruta va a la carpeta normal de las cartas o del arte, para redirigirla. */
    public static boolean isCardImage(final String destPath) {
        return destPath != null && (swapRoot(destPath, NORMAL.cardsDir(), NORMAL.cardsDir()) != null
                || swapRoot(destPath, NORMAL.artDir(), NORMAL.artDir()) != null);
    }

    private static String nonNull(final String a, final String b) {
        return a != null ? a : b;
    }

    private static String swapRoot(final String path, final File from, final File to) {
        final String p = new File(path).getAbsolutePath();
        final String root = from.getAbsolutePath();
        if (p.length() > root.length() && p.regionMatches(true, 0, root, 0, root.length())
                && (p.charAt(root.length()) == File.separatorChar || p.charAt(root.length()) == '/')) {
            return new File(to, p.substring(root.length() + 1)).getPath();
        }
        return null;
    }

    /**
     * La URL con esta calidad. Solo toca las de la carta entera en
     * {@code normal}: un recorte de arte ({@code art_crop}), una ficha o
     * cualquier otra cosa se devuelven tal cual.
     */
    public String rewrite(final String url) {
        if (this == NORMAL || url == null) {
            return url;
        }
        // La API: ...?format=image&version=normal
        if (url.contains("version=normal")) {
            return url.replace("version=normal", "version=" + scryfall);
        }
        // La CDN: https://cards.scryfall.io/normal/front/a/b/<uuid>.jpg — y en
        // PNG el fichero tambien cambia de extension.
        final String cdn = "cards.scryfall.io/normal/";
        final int at = url.indexOf(cdn);
        if (at >= 0) {
            String out = url.substring(0, at) + "cards.scryfall.io/" + scryfall + "/"
                    + url.substring(at + cdn.length());
            if (this == PNG) {
                final int q = out.indexOf('?');
                final String path = q < 0 ? out : out.substring(0, q);
                final String rest = q < 0 ? "" : out.substring(q);
                if (path.endsWith(".jpg")) {
                    out = path.substring(0, path.length() - 4) + ".png" + rest;
                }
            }
            return out;
        }
        return url;
    }
}
