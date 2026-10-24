package forge.neo.card;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        final Map<String, String[]> missing = new LinkedHashMap<>();
        for (final Map.Entry<String, String[]> e : newestPrintings().entrySet()) {
            if (limit > 0 && missing.size() >= limit) {
                break;
            }
            final File dest = new File(ForgeConstants.CACHE_TOKEN_PICS_DIR, e.getKey() + ".jpg");
            if (dest.exists() || new File(ForgeConstants.CACHE_TOKEN_PICS_DIR, e.getKey() + ".png").exists()) {
                continue;
            }
            missing.put(dest.getAbsolutePath(), e.getValue());
        }
        // LAS QUE SCRYFALL NO TIENE NO SE CUENTAN (Discord, 24-10-2026: "On the
        // second run, it's showing this" - 32 que fallaban y volvian a salir
        // pendientes en cada pasada).  Se prueba cada impresion de la ficha, de
        // la mas nueva a la mas vieja; si Scryfall ha contestado por todas y no
        // esta en ninguna, se da por perdida.  Si alguna busqueda falla por la
        // red, se intenta como antes por la API: puede que si este.
        final Map<String, List<String[]>> printings = allPrintings();
        int cdn = 0;
        int absent = 0;
        for (final Map.Entry<String, String[]> e : missing.entrySet()) {
            final String[] newest = e.getValue();
            final String name = new File(e.getKey()).getName().replaceFirst("\\.jpg$", "");
            String url = null;
            boolean unsure = false;
            for (final String[] where : printings.getOrDefault(name, List.<String[]>of(newest))) {
                final String found = cdnUrl(where[0], where[1]);
                if (found == UNKNOWN) {
                    unsure = true;
                } else if (found != ABSENT) {
                    url = found;
                    break;
                }
            }
            if (url != null) {
                cdn++;
            } else if (unsure) {
                url = ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD
                        + ImageUtil.getScryfallTokenDownloadUrl(newest[1], newest[0], "en", "");
            } else {
                absent++;
                continue;
            }
            out.put(e.getKey(), url);
        }
        System.out.println("[fichas] " + out.size() + " por bajar, " + cdn + " de la CDN; "
                + absent + " que Scryfall no tiene");
        return out;
    }

    /**
     * La foto de la ficha en la CDN de Scryfall ({@code cards.scryfall.io}), o
     * null si no se sabe.
     *
     * <p><b>Por que no la API</b> (Discord, 24-10-2026: en Android, descargadas
     * las fichas, volvian a salir las 766 como pendientes). La direccion de la
     * API ({@code api.scryfall.com/cards/<juego>/<numero>?format=image}) exige
     * la cabecera {@code Accept}, y el descargador de Forge no la pone: en el
     * escritorio la añade Java por su cuenta, pero en Android no, y Scryfall
     * contesta 400 a todas. La descarga se las saltaba en silencio. La CDN no
     * pide nada (ni tiene limite), y su direccion sale de buscar el juego de
     * fichas con cabeceras puestas por nosotros: una pagina por juego.
     *
     * <p>Primero el indice de Forge ({@code CdnUuidCache}), por si "todas las
     * cartas" ya lo bajo; si no, la busqueda, guardada para toda la sesion.
     */
    /** {@link #cdnUrl}: Scryfall ha contestado y esa ficha no la tiene. */
    private static final String ABSENT = new String("absent");
    /** {@link #cdnUrl}: no se ha podido preguntar (red, limite de Scryfall). */
    private static final String UNKNOWN = new String("unknown");

    private static String cdnUrl(final String tokensCode, final String number) {
        final String code = tokensCode.toLowerCase(java.util.Locale.ROOT);
        final boolean back = number.endsWith("☇");
        final String cn = back ? number.substring(0, number.length() - 1) : number;
        try {
            final String cached = forge.gui.download.CdnUuidCache.getCdnUrlIfCached(code, cn, "en",
                    back ? "back" : "", "normal");
            if (cached != null) {
                return cached;
            }
        } catch (final RuntimeException ignored) {
            // sin indice: a buscar
        }
        // computeIfAbsent no guarda un null: lo que falla por la red se vuelve a
        // preguntar en la pasada siguiente.
        final Map<String, String[]> set = SETS.computeIfAbsent(code, TokenArtDownload::searchSet);
        if (set == null) {
            return UNKNOWN;
        }
        final String[] faces = set.get(cn);
        final String url = faces == null ? null : back ? faces[1] : faces[0];
        return url == null ? ABSENT : url;
    }

    /** Lo ya buscado en esta sesion: juego de fichas -> numero -> {cara, trasera}. */
    private static final Map<String, Map<String, String[]>> SETS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Todas las fichas de un juego, con su foto normal. Vacio si Scryfall dice
     * que no tiene ese juego (404); null si no se ha podido preguntar.
     */
    private static Map<String, String[]> searchSet(final String code) {
        final Map<String, String[]> out = new HashMap<>();
        try {
            String url = "https://api.scryfall.com/cards/search?unique=prints&include_extras=true&q="
                    + java.net.URLEncoder.encode("set:" + code, "UTF-8");
            while (url != null) {
                if (forge.util.ScryfallRateLimiter.isCoolingDown()) {
                    return null;
                }
                forge.util.ScryfallRateLimiter.acquire(url);
                final java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
                c.setConnectTimeout(10_000);
                c.setReadTimeout(15_000);
                c.setRequestProperty("Accept", "application/json");
                c.setRequestProperty("User-Agent", forge.gui.GuiBase.getInterface().getUserAgent());
                final int status = c.getResponseCode();
                if (status == 404) {
                    c.disconnect();
                    break;
                }
                if (status != 200) {
                    forge.util.ScryfallRateLimiter.noteIfRateLimited(status, url, c.getHeaderField("Retry-After"));
                    c.disconnect();
                    return null;
                }
                final com.google.gson.JsonObject page;
                try (java.io.Reader r = new java.io.InputStreamReader(c.getInputStream(),
                        java.nio.charset.StandardCharsets.UTF_8)) {
                    page = com.google.gson.JsonParser.parseReader(r).getAsJsonObject();
                }
                for (final com.google.gson.JsonElement el : page.getAsJsonArray("data")) {
                    final com.google.gson.JsonObject card = el.getAsJsonObject();
                    final String cn = card.has("collector_number") ? card.get("collector_number").getAsString() : null;
                    if (cn == null) {
                        continue;
                    }
                    String front = normal(card);
                    String back = null;
                    if (card.has("card_faces")) {
                        final com.google.gson.JsonArray faces = card.getAsJsonArray("card_faces");
                        if (front == null && faces.size() > 0) {
                            front = normal(faces.get(0).getAsJsonObject());
                        }
                        if (faces.size() > 1) {
                            back = normal(faces.get(1).getAsJsonObject());
                        }
                    }
                    if (front != null) {
                        out.put(cn, new String[] {front, back});
                    }
                }
                url = page.has("has_more") && page.get("has_more").getAsBoolean() && page.has("next_page")
                        ? page.get("next_page").getAsString() : null;
            }
        } catch (final Exception e) {
            System.out.println("[fichas] no se pudo buscar " + code + ": " + e);
            return null;
        }
        return out;
    }

    private static String normal(final com.google.gson.JsonObject o) {
        if (!o.has("image_uris")) {
            return null;
        }
        final com.google.gson.JsonObject uris = o.getAsJsonObject("image_uris");
        return uris.has("normal") ? uris.get("normal").getAsString() : null;
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

    /**
     * Cada ficha con TODAS sus impresiones con numero, de la mas nueva a la mas
     * vieja: si la mas nueva no esta en Scryfall, puede estar otra.
     */
    static Map<String, List<String[]>> allPrintings() {
        final Map<String, List<Object[]>> found = new HashMap<>();
        final StaticData data = StaticData.instance();
        if (data != null) {
            for (final CardEdition ed : data.getEditions()) {
                if (ed.getType() == CardEdition.Type.CUSTOM_SET || ed.getTokensCode() == null
                        || ed.getTokensCode().isEmpty()) {
                    continue;
                }
                for (final Map.Entry<String, CardEdition.EditionEntry> t : ed.getTokens().entries()) {
                    final String number = t.getValue() == null ? null : t.getValue().collectorNumber();
                    if (t.getKey() == null || t.getKey().isEmpty() || number == null || number.isEmpty()) {
                        continue;
                    }
                    found.computeIfAbsent(t.getKey(), k -> new ArrayList<>())
                            .add(new Object[] {ed.getDate(), new String[] {ed.getTokensCode(), number}});
                }
            }
        }
        final Map<String, List<String[]>> out = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (final Map.Entry<String, List<Object[]>> e : found.entrySet()) {
            final List<Object[]> list = e.getValue();
            list.sort((a, b) -> {
                final Date da = (Date) a[0];
                final Date db = (Date) b[0];
                return da == null ? 1 : db == null ? -1 : db.compareTo(da);
            });
            final List<String[]> where = new ArrayList<>(list.size());
            for (final Object[] o : list) {
                where.add((String[]) o[1]);
            }
            out.put(e.getKey(), where);
        }
        return out;
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
