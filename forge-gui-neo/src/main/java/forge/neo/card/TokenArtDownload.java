package forge.neo.card;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import forge.StaticData;
import forge.card.CardEdition;
import forge.gui.download.GuiDownloadService;
import forge.localinstance.properties.ForgeConstants;
import forge.util.ImageUtil;

/**
 * <b>El arte de las fichas, para jugar sin internet</b> (Discord, 23-10-2026:
 * <i>"will be good that the download all includes the tokens and everything
 * else, so that neo can be truly offline"</i>).
 *
 * <p>Va APARTE de "todas las cartas" ({@link ArtDownload}) a proposito (Ana):
 * son dos necesidades distintas, y una ficha apenas tiene texto que leer.
 *
 * <p><b>Una imagen por nombre de ficha</b>, como las cartas: la de la impresion
 * mas reciente que tenga numero (la lista {@code [tokens]} de cada expansion),
 * guardada en {@code tokens/<nombre>.jpg}. Ese es el ultimo sitio donde mira
 * {@code ImageKeys.getImageFile} para CUALQUIER impresion de esa ficha
 * ({@code findFile(dir, setlessFilename)}), asi que con ella delante toda
 * ficha sale con arte aunque no haya red. Salen tambien las fichas que se crean
 * copiando una carta por su nombre (el Gingerbrute), porque estan en esas
 * mismas listas.
 *
 * <p>Lo baja el descargador de Forge ({@code GuiDownloadService}: limitador de
 * Scryfall, reintentos, cancelar), con la misma direccion que usa el motor para
 * una ficha ({@code ImageUtil.getScryfallTokenDownloadUrl}). En el jar: la usa
 * tambien NeoForge Android ({@code DescargaDeArte}).
 */
public final class TokenArtDownload extends GuiDownloadService {

    /** Lo que mide una ficha en disco, en KB (como una carta en calidad normal). */
    public static final int KB_PER_TOKEN = 79;

    @Override
    public String getTitle() {
        return "NeoForge - arte de las fichas";
    }

    @Override
    protected Map<String, String> getNeededFiles() {
        final Map<String, String> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        // Solo pruebas: -Dneo.tokens.testLimit=3 baja tres y para (artecheck).
        final int limit = Integer.getInteger("neo.tokens.testLimit", 0);
        for (final Map.Entry<String, String[]> e : newestPrintings().entrySet()) {
            if (limit > 0 && out.size() >= limit) {
                break;
            }
            final File dest = new File(ForgeConstants.CACHE_TOKEN_PICS_DIR, e.getKey() + ".jpg");
            if (dest.exists() || new File(ForgeConstants.CACHE_TOKEN_PICS_DIR, e.getKey() + ".png").exists()) {
                continue;
            }
            final String[] where = e.getValue();
            out.put(dest.getAbsolutePath(), ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD
                    + ImageUtil.getScryfallTokenDownloadUrl(where[1], where[0], "en", ""));
        }
        System.out.println("[fichas] " + out.size() + " por bajar");
        return out;
    }

    /** Despues de bajar: que se vean sin reiniciar. */
    public static void afterDownload() {
        forge.ImageKeys.clearMissingCards();
        CardImages.clear();
        CardImages.repaint();
    }

    /**
     * Cada nombre de ficha con su impresion mas reciente que tenga numero:
     * {@code {codigo de fichas de la expansion, numero}}.
     */
    static Map<String, String[]> newestPrintings() {
        final Map<String, String[]> best = new LinkedHashMap<>();
        final Map<String, Date> when = new HashMap<>();
        final StaticData data = StaticData.instance();
        if (data == null) {
            return best;
        }
        for (final CardEdition ed : data.getEditions()) {
            if (ed.getType() == CardEdition.Type.CUSTOM_SET || ed.getTokensCode() == null
                    || ed.getTokensCode().isEmpty()) {
                continue;
            }
            for (final Map.Entry<String, CardEdition.EditionEntry> t : ed.getTokens().entries()) {
                final String name = t.getKey();
                final String number = t.getValue() == null ? null : t.getValue().collectorNumber();
                if (name == null || name.isEmpty() || number == null || number.isEmpty()) {
                    continue;
                }
                final Date seen = when.get(name);
                if (seen == null || ed.getDate().after(seen)) {
                    when.put(name, ed.getDate());
                    best.put(name, new String[] {ed.getTokensCode(), number});
                }
            }
        }
        return best;
    }

    /** Para el comprobador. */
    public static Map<String, String[]> newestPrintingsForTest() {
        return newestPrintings();
    }

    /** Cuantas fichas distintas hay (para el comprobador). */
    public static int known() {
        return newestPrintings().size();
    }
}
