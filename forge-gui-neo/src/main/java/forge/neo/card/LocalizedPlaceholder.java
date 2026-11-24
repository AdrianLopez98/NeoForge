package forge.neo.card;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * La imagen de relleno de Scryfall: <i>"Localized Image Not Available"</i>.
 *
 * <p>Reportado el 24-11-2026 con el plano de Pantlaza en Rogue Commander (Naya,
 * OHOP 27): jugando en castellano salia la carta INGLESA con una banda oscura
 * encima que dice eso y una pastilla "ES". Scryfall tiene la ficha de muchas
 * impresiones traducidas <b>sin el escaneo</b>, y a
 * {@code /cards/ohop/27/es?format=image} no le contesta con un 404 sino con un
 * 302 a una imagen de verdad: la inglesa con la banda pintada. Las cabeceras son
 * las de una imagen buena y el tamanyo tambien (488x680), asi que {@link CardArt}
 * la guardaba en {@code pics/cards-es} como si fuera la carta traducida, y ya
 * nunca volvia a preguntar.
 *
 * <p><b>No era cosa de un plano.</b> En la cache de este PC, de 3.899 cartas en
 * castellano, <b>676</b> eran esto: casi toda la Cuarta Edicion, Mirage,
 * Onslaught, Kamigawa, las tierras basicas viejas...
 *
 * <p>Dos defensas, y hacen falta las dos:
 * <ul>
 * <li><b>Al bajarla</b> manda la ficha: se pide el JSON de la carta (la misma url
 * con {@code format=json}) y si su {@code image_status} es {@code placeholder} o
 * {@code missing} ni se baja: se apunta que no esta en ese idioma y se ve la
 * inglesa. Si esta bien, la imagen sale de su {@code image_uris} (la CDN, sin
 * limite), asi que sigue costando UNA peticion a la API, como antes.</li>
 * <li><b>Lo que ya esta en el disco</b> no se puede preguntar carta a carta (una
 * peticion por carta, y en el pueblo no hay red): se reconoce <b>por los
 * pixeles</b>. La banda es la misma en todas (mismo sitio, mismas letras; entre
 * dos idiomas de la misma carta solo cambia la pastilla), asi que en el
 * tamanyo en que se bajan (488x680, el "normal") se compara
 * con una mascara ({@code localized-placeholder-mask.png}, 245x72: blanco =
 * letra, negro = fondo de la banda, gris = no se mira). En las 676 las letras
 * salen claras en el 99,9 % de sus pixeles y el fondo oscuro en el 100 %; en las
 * 3.223 cartas buenas, como mucho el 54 % de las dos cosas a la vez.</li>
 * </ul>
 *
 * <p>Sin JavaFX ni motor: lo prueba {@code run.cmd imagecheck}. Android puede
 * usar desde el jar la mitad del JSON; la de los pixeles no (tira de
 * {@code javax.imageio} y {@code java.awt.image}, que alli no hay).
 */
public final class LocalizedPlaceholder {

    private LocalizedPlaceholder() {
    }

    /** El tamanyo "normal" de Scryfall, en el que esta medida la mascara. */
    private static final int REF_W = 488;
    private static final int REF_H = 680;
    /** Donde empieza la mascara dentro de esa carta. */
    private static final int MASK_X = 150;
    private static final int MASK_Y = 233;

    /** Que parte de las letras tiene que salir clara, y del fondo oscura. */
    private static final double MIN_MATCH = 0.97;
    private static final int TEXT_MIN_LUMA = 170;
    private static final int BACK_MAX_LUMA = 100;

    /**
     * La marca de "carpeta ya repasada". Lleva version: si algun dia cambia la
     * deteccion, se sube y se vuelve a repasar todo una vez.
     */
    static final String SWEPT_STAMP = ".neo-placeholders-v1";

    // ------------------------------------------------------------------ JSON

    /**
     * Si la ficha de Scryfall dice que NO hay escaneo en ese idioma.
     *
     * <p>{@code image_status} vale {@code missing}, {@code placeholder},
     * {@code lowres} o {@code highres_scan}. Las dos primeras no son la carta
     * traducida; {@code lowres} si lo es (un escaneo pequenyo, pero suyo).
     * Un JSON que no se entiende NO cuenta como "no hay": no se sabe.
     */
    public static boolean isNotAvailable(final String json) {
        final JsonObject card = parse(json);
        if (card == null) {
            return false;
        }
        final String status = string(card, "image_status");
        return "placeholder".equals(status) || "missing".equals(status);
    }

    /**
     * La imagen {@code normal} que da la ficha (la CDN de Scryfall), o
     * {@code null} si no trae ninguna.
     *
     * <p>Las cartas de dos caras no tienen {@code image_uris} arriba sino una
     * por cara en {@code card_faces}: se coge la segunda si se pedia la de
     * atras. Partidas, giratorias y meld tienen una sola imagen arriba.
     */
    public static String imageUrl(final String json, final boolean back) {
        final JsonObject card = parse(json);
        if (card == null) {
            return null;
        }
        final String top = normalOf(card);
        if (top != null) {
            return top;
        }
        final JsonElement faces = card.get("card_faces");
        if (faces == null || !faces.isJsonArray()) {
            return null;
        }
        final JsonArray list = faces.getAsJsonArray();
        final int i = back ? 1 : 0;
        if (list.size() <= i || !list.get(i).isJsonObject()) {
            return null;
        }
        return normalOf(list.get(i).getAsJsonObject());
    }

    private static String normalOf(final JsonObject o) {
        final JsonElement uris = o.get("image_uris");
        if (uris == null || !uris.isJsonObject()) {
            return null;
        }
        final String url = string(uris.getAsJsonObject(), "normal");
        return url != null && url.startsWith("https://") ? url : null;
    }

    private static JsonObject parse(final String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            final JsonElement e = JsonParser.parseString(json);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static String string(final JsonObject o, final String key) {
        final JsonElement e = o.get(key);
        return e == null || !e.isJsonPrimitive() ? null : e.getAsString();
    }

    // --------------------------------------------------------------- pixeles

    /** La mascara: 255 = letra, 0 = fondo de la banda, lo demas no se mira. */
    private static volatile int[] mask;
    private static volatile int maskW;
    private static volatile int maskH;

    private static int[] mask() {
        final int[] m = mask;
        if (m != null) {
            return m;
        }
        synchronized (LocalizedPlaceholder.class) {
            if (mask != null) {
                return mask;
            }
            int[] loaded = new int[0];
            try (InputStream in = LocalizedPlaceholder.class
                    .getResourceAsStream("/forge/neo/card/localized-placeholder-mask.png")) {
                final BufferedImage img = in == null ? null : ImageIO.read(in);
                if (img != null) {
                    final int w = img.getWidth();
                    final int h = img.getHeight();
                    final int[] px = new int[w * h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            px[y * w + x] = img.getRaster().getSample(x, y, 0);
                        }
                    }
                    maskW = w;
                    maskH = h;
                    loaded = px;
                }
            } catch (final IOException e) {
                // Sin mascara no se detecta nada: mejor eso que borrar cartas.
            }
            mask = loaded;
            return loaded;
        }
    }

    /**
     * Si {@code img} tiene la banda. {@code img} es la carta entera, o solo un
     * trozo que empieza en ({@code offX},{@code offY}) de una carta de
     * {@code fullW}x{@code fullH}.
     */
    private static boolean matches(final BufferedImage img, final int offX, final int offY,
                                   final int fullW, final int fullH) {
        final int[] m = mask();
        if (m.length == 0 || img == null || !cardShaped(fullW, fullH)) {
            return false;
        }
        int text = 0;
        int textLit = 0;
        int back = 0;
        int backDark = 0;
        for (int my = 0; my < maskH; my++) {
            final int y = MASK_Y + my - offY;
            for (int mx = 0; mx < maskW; mx++) {
                final int kind = m[my * maskW + mx];
                if (kind != 0 && kind != 255) {
                    continue;
                }
                final int x = MASK_X + mx - offX;
                if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) {
                    return false;
                }
                final int luma = luma(img.getRGB(x, y));
                if (kind == 255) {
                    text++;
                    if (luma > TEXT_MIN_LUMA) {
                        textLit++;
                    }
                } else {
                    back++;
                    if (luma < BACK_MAX_LUMA) {
                        backDark++;
                    }
                }
            }
        }
        return text > 0 && back > 0
                && textLit >= MIN_MATCH * text && backDark >= MIN_MATCH * back;
    }

    /** Si esta imagen (la carta entera) es la de relleno. */
    public static boolean looksLikePlaceholder(final BufferedImage card) {
        return card != null && matches(card, 0, 0, card.getWidth(), card.getHeight());
    }

    /**
     * Si el fichero es la imagen de relleno. Solo se decodifica la franja de la
     * banda: repasar una carpeta de miles de cartas tiene que ser barato. Lo que
     * no se puede leer NO es de relleno (no se borra nada por dudar).
     */
    public static boolean looksLikePlaceholder(final File file) {
        if (file == null || !file.isFile() || mask().length == 0) {
            return false;
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(file)) {
            if (in == null) {
                return false;
            }
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return false;
            }
            final ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                final int w = reader.getWidth(0);
                final int h = reader.getHeight(0);
                if (!cardShaped(w, h)) {
                    return false;
                }
                final ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceRegion(new Rectangle(MASK_X, MASK_Y, maskW, maskH));
                return matches(reader.read(0, param), MASK_X, MASK_Y, w, h);
            } finally {
                reader.dispose();
            }
        } catch (final IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Solo el tamanyo "normal" exacto, que es en el que se baja SIEMPRE el arte
     * traducido (la calidad de Ajustes no le cambia la url) y en el que estaban
     * las 3.899 de la cache del autor. Una carta reescalada a otro tamanyo ya no
     * da la banda nitida pixel a pixel, y no se mira: mejor dejar una de relleno
     * que arriesgarse a quitar una buena.
     */
    private static boolean cardShaped(final int w, final int h) {
        return w == REF_W && h == REF_H;
    }

    private static int luma(final int rgb) {
        final int r = (rgb >> 16) & 0xff;
        final int g = (rgb >> 8) & 0xff;
        final int b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    // ---------------------------------------------------------- lo ya bajado

    /**
     * Aparta la imagen y deja la marca de "no esta en este idioma"
     * ({@link CardArt#missMarkerFor}), para que se vea la inglesa y no se
     * vuelva a pedir.
     *
     * <p><b>Apartar, no borrar.</b> La de relleno ES la carta inglesa con una
     * banda encima: se lee. Quien siempre ha jugado en castellano puede no tener
     * la inglesa en el disco, y sin red (la copia del pueblo) borrarla le dejaba
     * la carta en blanco. Asi que se queda al lado como
     * {@code <carta>.fullborder.jpg.placeholder} ({@link CardArt#placeholderFor})
     * y {@code CardImages} la ensenya solo si no hay NADA mejor, mientras pide la
     * inglesa.
     *
     * @return si la imagen ya no esta donde la buscan (apartada o borrada)
     */
    public static boolean discard(final File image) {
        try {
            final File marker = CardArt.missMarkerFor(image);
            if (!marker.exists()) {
                marker.createNewFile();
            }
        } catch (final IOException | SecurityException e) {
            System.err.println("[neo] no se ha podido marcar " + image + ": " + e);
        }
        final File aside = CardArt.placeholderFor(image);
        if (aside.exists()) {
            aside.delete();
        }
        if (image.renameTo(aside) || image.delete() || !image.exists()) {
            return true;
        }
        System.err.println("[neo] no se ha podido apartar la imagen de relleno " + image);
        return false;
    }

    /**
     * Si el fichero es de relleno, lo aparta ({@link #discard}). Devuelve si lo
     * era (aunque no se haya podido apartar: quien pregunta no debe ensenyarlo).
     */
    public static boolean discardIfPlaceholder(final File image) {
        if (!looksLikePlaceholder(image)) {
            return false;
        }
        discard(image);
        return true;
    }

    /** Si la mascara esta cargada: sin ella no se detecta nada. */
    static boolean ready() {
        return mask().length > 0;
    }

    /** Las carpetas a las que ya se les ha lanzado el repaso en esta sesion. */
    private static final Set<String> SWEEPING = ConcurrentHashMap.newKeySet();

    /** Si esa carpeta ya se repaso entera (en esta sesion o en otra). */
    public static boolean isSwept(final File dir) {
        return dir == null || new File(dir, SWEPT_STAMP).exists();
    }

    /**
     * Repasa la carpeta del arte traducido UNA vez, en segundo plano: quita las
     * de relleno que se guardaron antes del 24-11-2026. La marca solo se deja si
     * termina; si se cierra el juego a medias, se repasa otra vez la proxima.
     * Mientras tanto quien pinta mira cada carta antes de ensenyarla
     * ({@code CardImages.findLocalized}), asi que no hay carrera.
     */
    public static void sweepInBackground(final File dir) {
        if (dir == null || !dir.isDirectory() || isSwept(dir)
                || !SWEEPING.add(dir.getAbsolutePath())) {
            return;
        }
        final Thread t = new Thread(() -> {
            try {
                final int[] counts = sweep(dir, false);
                System.out.println("[neo] arte traducido: " + counts[1]
                        + " imagenes de relleno de Scryfall quitadas de " + counts[0] + " en " + dir);
            } catch (final RuntimeException e) {
                System.err.println("[neo] repasando " + dir + ": " + e);
            }
        }, "neo-placeholder-sweep");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    /**
     * Repasa una carpeta. Con {@code dryRun} solo cuenta, sin quitar ni marcar
     * nada (lo usa {@code imagecheck -Dneo.placeholder.scan=<carpeta>}).
     *
     * @return {cartas miradas, de relleno}
     */
    public static int[] sweep(final File dir, final boolean dryRun) {
        // {miradas, de relleno, las que no se han podido apartar}
        final int[] counts = new int[3];
        if (!ready()) {
            // Sin mascara todo saldria "buena": NO se marca la carpeta como
            // repasada, o no se volveria a mirar ni con la mascara de vuelta.
            System.err.println("[neo] sin la mascara de la imagen de relleno: no se repasa " + dir);
            return new int[] {0, 0};
        }
        walk(dir, dryRun, counts);
        if (!dryRun && counts[2] == 0) {
            // Si alguna no se ha podido apartar (un antivirus con el fichero
            // abierto), sin marca: se repasa otra vez el proximo arranque.
            try {
                new File(dir, SWEPT_STAMP).createNewFile();
            } catch (final IOException | SecurityException e) {
                // Sin marca se repasa otra vez el proximo arranque. No pasa nada.
            }
        }
        return new int[] {counts[0], counts[1]};
    }

    private static void walk(final File dir, final boolean dryRun, final int[] counts) {
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (final File f : files) {
            if (f.isDirectory()) {
                walk(f, dryRun, counts);
            } else if (f.getName().endsWith(".jpg")) {
                counts[0]++;
                if (looksLikePlaceholder(f)) {
                    counts[1]++;
                    if (!dryRun && !discard(f)) {
                        counts[2]++;
                    }
                }
            }
        }
    }
}
