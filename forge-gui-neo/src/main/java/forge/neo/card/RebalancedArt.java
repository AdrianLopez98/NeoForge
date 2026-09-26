package forge.neo.card;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import forge.ImageKeys;
import forge.StaticData;
import forge.card.CardEdition;
import forge.gui.download.CdnUuidCache;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.util.ImageUtil;

/**
 * <b>El arte de las cartas rebalanceadas de Arena (las {@code A-...}).</b>
 *
 * <p>Reportado el 26-09-2026: en el Forge normal salen con foto y aqui no. La
 * historia entera, porque no se deduce de ningun sitio:
 *
 * <ul>
 *   <li>Wizards <b>quito las rebalanceadas de Arena</b> hacia el 22-09-2026
 *       (segun contaron en Discord), y Scryfall las quito con ellas de su API ({@code /cards/afr/A-9} da 404,
 *       {@code is:rebalanced} sale vacio) <b>y de su indice masivo</b>: el del
 *       23-09 traia 221, el del 26-09 ninguna.</li>
 *   <li>Pero las <b>fotos siguen en su CDN</b>
 *       ({@code cards.scryfall.io/normal/front/.../<uuid>.jpg}): comprobadas las
 *       221 el 26-09-2026. Solo hace falta saber el identificador.</li>
 *   <li>Forge las pide por la CDN con el identificador que tenga guardado en
 *       {@code CdnUuidCache}. A quien bajo su indice antes del 22-09 le salen;
 *       a quien lo baje ahora, ya no. Y como ese indice <b>nunca borra</b> una
 *       entrada al fusionar, a los primeros les seguira saliendo.</li>
 * </ul>
 *
 * <p>Asi que los identificadores <b>los llevamos nosotros</b>:
 * {@code forge/neo/rebalanced-art.tsv}, sacado del indice de este PC del
 * 23-09-2026. Cubre 221 de las 222 que trae Forge (la otra, A-Town, no tiene
 * juego). Si Scryfall algun dia borra tambien las fotos, la descarga falla como
 * cualquier otra y la carta se queda como estaba: no hay nada peor que perder.
 */
public final class RebalancedArt {

    private RebalancedArt() {
    }

    /** "juego|numero" -> {cara, trasera o null}. */
    private static volatile Map<String, String[]> table;

    private static Map<String, String[]> table() {
        Map<String, String[]> t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return t;
    }

    private static Map<String, String[]> load() {
        final Map<String, String[]> out = new HashMap<>();
        try (InputStream in = RebalancedArt.class.getResourceAsStream("/forge/neo/rebalanced-art.tsv")) {
            if (in == null) {
                System.err.println("[arte] falta forge/neo/rebalanced-art.tsv");
                return Collections.emptyMap();
            }
            final BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                final String[] f = line.split("\t", -1);
                if (f.length < 3) {
                    continue;
                }
                final String back = f.length > 3 && !f[3].isEmpty() ? f[3] : null;
                out.put(key(f[0], f[1]), new String[] {f[2], back});
            }
        } catch (final IOException e) {
            System.err.println("[arte] no se pudo leer la tabla de rebalanceadas: " + e);
        }
        return out;
    }

    private static String key(final String scryfallCode, final String cn) {
        return scryfallCode.toLowerCase(Locale.ROOT) + "|" + cn;
    }

    /** Si la carta es una rebalanceada de Arena. */
    public static boolean isRebalanced(final PaperCard c) {
        return c != null && (c.getName().startsWith("A-") || c.isRebalanced());
    }

    /**
     * La foto de esta rebalanceada en la CDN, o {@code null} si no es
     * rebalanceada o no la tenemos. Si esa impresion no esta, vale otra de la
     * misma carta: es la misma foto.
     */
    public static String cdnUrl(final PaperCard c, final boolean back) {
        if (!isRebalanced(c)) {
            return null;
        }
        final String url = cdnUrlOf(c, back);
        if (url != null) {
            return url;
        }
        for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards(c.getName())) {
            final String other = cdnUrlOf(p, back);
            if (other != null) {
                return other;
            }
        }
        return null;
    }

    private static String cdnUrlOf(final PaperCard p, final boolean back) {
        final CardEdition edition = StaticData.instance().getEditions().get(p.getEdition());
        final String code = edition == null ? null : edition.getScryfallCode();
        if (code == null || code.isEmpty() || p.getCollectorNumber() == null) {
            return null;
        }
        final String[] uuids = table().get(key(code, p.getCollectorNumber()));
        if (uuids == null) {
            return null;
        }
        if (back) {
            return uuids[1] == null ? null : CdnUuidCache.cdnUrl(uuids[1], "back", "normal");
        }
        return CdnUuidCache.cdnUrl(uuids[0], "front", "normal");
    }

    /** Lo mismo, a partir de la clave de imagen que maneja {@link CardImages}. */
    static String cdnUrl(final String imageKey) {
        if (imageKey == null || !imageKey.startsWith(ImageKeys.CARD_PREFIX)) {
            return null;
        }
        try {
            return cdnUrl(ImageUtil.getPaperCardFromImageKey(imageKey),
                    imageKey.endsWith(ImageKeys.BACKFACE_POSTFIX));
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Cuantas trae la tabla. Para el comprobador. */
    public static int size() {
        return table().size();
    }
}
