package forge.neo.card;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import forge.ImageKeys;
import forge.localinstance.properties.ForgeConstants;
import forge.util.FileUtil;

/**
 * <b>El arte de las mascotas de la Quest</b> (Discord, 13-10-2026: <i>"add art
 * that changes with upgrades"</i>).
 *
 * <p>Forge lo tiene hecho: 26 cartas, una por mascota y nivel ("Wolf Pet ·
 * Level 2 of 4"), con el arte cambiando al subir (al simio le salen cuernos, la
 * planta se abre). Viven en su repositorio {@code forge-extras} y la lista de
 * direcciones viene con Forge ({@code res/lists/quest-pet-token-images.txt}).
 * Pero solo las baja el menu "descargar imagenes" de la GUI vieja: al pedirlas
 * en partida, {@code ImageFetcher} busca los tokens en OTRA lista
 * ({@code token-images.txt}), y la clave de una mascota ({@code t:petwolf_lvl1},
 * sin edicion) la da por mal formada. O sea que nunca llegaban.
 *
 * <p>Aqui se contesta la direccion de cada una; {@code CardImages} la baja
 * directa a la carpeta de tokens de Forge, como las rebalanceadas de Arena, y
 * a partir de ahi la encuentra el camino de siempre. Se bajan bajo demanda,
 * igual que las cartas: no van en el paquete.
 *
 * <p>Java puro: lo usa tambien Android por el jar.
 */
public final class PetArt {

    private PetArt() {
    }

    private static volatile Map<String, String> urls;

    /**
     * La direccion de la imagen de esta mascota, o null si la clave no es de
     * una mascota.
     *
     * @param imageKey la clave de la carta ({@code t:petwolf_lvl1})
     */
    public static String urlFor(final String imageKey) {
        final String file = fileFor(imageKey);
        return file == null ? null : table().get(file);
    }

    /** El nombre del fichero en la carpeta de tokens ({@code petwolf_lvl1.jpg}), o null. */
    public static String fileFor(final String imageKey) {
        if (imageKey == null || !imageKey.startsWith(ImageKeys.TOKEN_PREFIX)) {
            return null;
        }
        final String name = imageKey.substring(ImageKeys.TOKEN_PREFIX.length());
        if (name.isEmpty() || name.indexOf('|') >= 0) {
            return null;
        }
        final String file = name.toLowerCase(Locale.ROOT) + ".jpg";
        return table().containsKey(file) ? file : null;
    }

    /** Si la clave es de una mascota. */
    public static boolean isPet(final String imageKey) {
        return fileFor(imageKey) != null;
    }

    /** La clave de imagen de una mascota a partir de su dibujo del bazar ({@code petwolf_lvl1}). */
    public static String keyFor(final String picture) {
        return picture == null || picture.isEmpty() ? null : ImageKeys.getTokenKey(picture);
    }

    /**
     * La carta de una mascota en un nivel, como la monta Forge
     * ({@code QuestPetStats.getCard}): su fichero del bazar y su dibujo. Sirve
     * para ensenyar en la tienda el nivel que vas a COMPRAR, que Forge no
     * publica (solo da la carta del nivel que ya tienes).
     *
     * @param saveFileKey la clave de la mascota ("Wolf", "Plant"...)
     * @return null si ese nivel no existe o no tiene carta
     */
    public static forge.item.PaperToken levelCard(final String saveFileKey, final int level) {
        final String[] entry = levels().get(saveFileKey + "#" + level);
        if (entry == null) {
            return null;
        }
        final String key = saveFileKey + "#" + level;
        forge.item.PaperToken card = CARDS.get(key);
        if (card == null) {
            try {
                final java.util.List<String> lines = FileUtil.readFile(
                        new java.io.File(ForgeConstants.BAZAAR_DIR, entry[1]));
                final forge.card.CardRules rules = forge.card.CardRules.fromScript(lines);
                card = new forge.item.PaperToken(rules, forge.card.CardEdition.UNKNOWN, entry[0], "",
                        forge.item.IPaperCard.NO_ARTIST_NAME);
                CARDS.put(key, card);
            } catch (final RuntimeException e) {
                System.out.println("[neo] no se ha podido montar la mascota " + key + ": " + e);
                return null;
            }
        }
        return card;
    }

    private static final Map<String, forge.item.PaperToken> CARDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile Map<String, String[]> levels;

    /**
     * "Wolf#2" -> {dibujo, fichero}, leido de {@code res/quest/bazaar/index.xml}
     * (el mismo que lee Forge). Con expresiones y no con un lector de XML: son
     * dos atributos por linea y tiene que valer igual en Android.
     */
    private static Map<String, String[]> levels() {
        Map<String, String[]> m = levels;
        if (m == null) {
            m = new HashMap<>();
            try {
                final java.util.regex.Pattern pet = java.util.regex.Pattern.compile("<pet\\b[^>]*saveFileKey=\"([^\"]+)\"");
                final java.util.regex.Pattern lvl = java.util.regex.Pattern.compile(
                        "<level\\b[^>]*value=\"(\\d+)\"[^>]*pic=\"([^\"]+)\"[^>]*cardFile=\"([^\"]+)\"");
                String current = null;
                for (final String line : FileUtil.readFile(new java.io.File(ForgeConstants.BAZAAR_DIR, "index.xml"))) {
                    final java.util.regex.Matcher p = pet.matcher(line);
                    if (p.find()) {
                        current = p.group(1);
                    }
                    final java.util.regex.Matcher l = lvl.matcher(line);
                    if (current != null && l.find()) {
                        m.put(current + "#" + l.group(1), new String[] {l.group(2), l.group(3)});
                    }
                }
            } catch (final RuntimeException e) {
                System.out.println("[neo] no se ha podido leer el bazar: " + e);
            }
            levels = m;
        }
        return m;
    }

    /** Cuantas mascotas-nivel tienen imagen: para el comprobador. */
    public static int count() {
        return table().size();
    }

    /** La carpeta donde se guardan (la de tokens de Forge). */
    public static String folder() {
        return ForgeConstants.CACHE_TOKEN_PICS_DIR;
    }

    private static Map<String, String> table() {
        Map<String, String> t = urls;
        if (t == null) {
            t = new HashMap<>();
            try {
                for (final org.apache.commons.lang3.tuple.Pair<String, String> p
                        : FileUtil.readNameUrlFile(ForgeConstants.IMAGE_LIST_QUEST_TOKENS_FILE)) {
                    if (p.getLeft() != null && p.getRight() != null) {
                        t.put(p.getLeft().trim().toLowerCase(Locale.ROOT), p.getRight().trim());
                    }
                }
            } catch (final RuntimeException e) {
                System.out.println("[neo] no se ha podido leer la lista de mascotas: " + e);
            }
            urls = t;
        }
        return t;
    }
}
