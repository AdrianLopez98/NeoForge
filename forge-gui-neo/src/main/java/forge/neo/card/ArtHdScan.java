package forge.neo.card;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import forge.StaticData;
import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.util.BuildInfo;
import forge.util.ScryfallRateLimiter;

/**
 * <b>Buscar imagenes mejores (HD)</b> de todo lo que ya esta bajado, de un
 * golpe.
 *
 * <p>Pedido en itch.io el 29-09-2026 despues del boton de una carta: Scryfall
 * pone una imagen provisional mientras no tiene el escaneo, y la cache no
 * vuelve a mirar. Aqui no se pregunta carta a carta: se pide a Scryfall cada
 * EXPANSION entera ({@code /cards/search}, 175 cartas por peticion), y de cada
 * impresion salen dos datos:
 * <ul>
 *   <li>{@code highres_image}: si ya esta el escaneo bueno;</li>
 *   <li>la FECHA de su imagen actual, que va en el propio enlace
 *       ({@code ...jpg?1788003039}).</li>
 * </ul>
 * Si es HD y es mas nueva que nuestro fichero, el nuestro se bajo antes (o sea,
 * es la provisional) y se vuelve a bajar. Si no, no se toca.
 *
 * <p>Solo las expansiones de los ULTIMOS {@link #MONTHS} meses (las
 * provisionales solo existen en las recientes) y solo aquellas de las que hay
 * alguna imagen en disco: sin nada bajado no se pregunta. La descarga va por
 * el {@link Store} (en el escritorio, {@code CardImages.replaceFromCdn}), que escribe a un {@code .tmp} y solo
 * sustituye si llega entera. Sin red, o con Scryfall limitando, se para.
 *
 * <p>Lo traducido ({@code cards-es}...) no entra en esta primera version.
 * Clase pura, sin JavaFX: donde esta cada imagen y como se sustituye lo pone
 * quien la llama ({@link Store}), porque el escritorio ({@code CardImages.HD_STORE})
 * y Android no guardan igual. La pantalla la llama en su hilo.
 */
public final class ArtHdScan {

    private ArtHdScan() {
    }

    /** Cuantos meses hacia atras se mira. */
    static final int MONTHS = 18;

    /** Como va: expansion {@code done} de {@code sets}, y cuantas imagenes cambiadas. */
    public interface Progress {
        void update(int done, int sets, String setName, int updated);

        /**
         * Scryfall ha pedido esperar (429) y se esta esperando: quedan
         * {@code seconds}. Sin esto la pantalla parece colgada medio minuto.
         */
        default void waiting(long seconds) {
        }
    }

    /**
     * Una busqueda por segundo como mucho: la MITAD de lo que permite Scryfall
     * en {@code /cards/search} (2 por segundo, que es a lo que espacia
     * {@code ScryfallRateLimiter}). Ir justo al limite, con la partida bajando
     * imagenes a la vez y la latencia de la red, acababa en 429 hacia la
     * expansion 12 de 51 (Discord, 29-09-2026, con el registro).
     */
    static final long SEARCH_EVERY_MS = 1000;

    /** Cuantas veces se espera y se reintenta una misma pagina tras un 429. */
    static final int RETRIES = 3;

    private static long lastSearchAt;

    /**
     * Donde vive cada imagen y como se cambia: lo unico que depende de la
     * interfaz. En Android una carta puede tener DOS ficheros (entera y
     * recorte de arte), por eso es una lista.
     */
    public interface Store {
        /** Los ficheros en disco de esa cara (entera y/o {@code .artcrop}); vacia si no hay. */
        List<File> files(String imageKey);

        /** Baja {@code url} encima de {@code file}, sin romperlo si falla. */
        boolean replace(String imageKey, String url, File file);
    }

    /** Como ha acabado. */
    public record Result(int sets, int checked, int updated, boolean stoppedByNetwork) {
    }

    /**
     * Recorre y actualiza. Bloquea: llamarlo fuera del hilo de interfaz.
     *
     * @param cancel si se pone a true, se para en la siguiente expansion
     */
    public static Result run(final Store store, final Progress progress, final AtomicBoolean cancel) {
        final List<CardEdition> sets = recentCachedSets();
        final Map<String, Map<String, PaperCard>> byEdition = printingsBy(sets);
        int checked = 0;
        int updated = 0;
        for (int i = 0; i < sets.size(); i++) {
            if (cancel != null && cancel.get()) {
                break;
            }
            final CardEdition ed = sets.get(i);
            if (progress != null) {
                progress.update(i, sets.size(), ed.getName(), updated);
            }
            final List<JsonObject> cards = searchSet(ed.getScryfallCode(), progress, cancel);
            if (cards == null) {
                // Sin red o limitados: seguir no sirve de nada.
                return new Result(sets.size(), checked, updated, true);
            }
            final Map<String, PaperCard> mine = byEdition.getOrDefault(ed.getCode(), java.util.Collections.emptyMap());
            for (final JsonObject c : cards) {
                if (cancel != null && cancel.get()) {
                    break;
                }
                if (!c.has("highres_image") || !c.get("highres_image").getAsBoolean()) {
                    continue;
                }
                final PaperCard pc = mine.get(collector(c));
                if (pc == null) {
                    continue;
                }
                checked++;
                updated += maybeUpdate(store, pc.getImageKey(false), frontUris(c));
                if (pc.getRules() != null && pc.getRules().getOtherPart() != null) {
                    updated += maybeUpdate(store, pc.getImageKey(true), backUris(c));
                }
            }
        }
        if (progress != null) {
            progress.update(sets.size(), sets.size(), "", updated);
        }
        return new Result(sets.size(), checked, updated, false);
    }

    /**
     * Vuelve a bajar esa cara si lo que hay en disco es MAS VIEJO que la imagen
     * HD de Scryfall. Devuelve 1 si la ha cambiado.
     */
    private static int maybeUpdate(final Store store, final String imageKey, final JsonObject uris) {
        if (imageKey == null || uris == null) {
            return 0;
        }
        final List<File> files = store.files(imageKey);
        if (files == null) {
            return 0;
        }
        int changed = 0;
        for (final File file : files) {
            if (file == null || !file.exists()) {
                continue;
            }
            final boolean crop = file.getName().contains(".artcrop");
            final String url = uris.has(crop ? "art_crop" : "normal")
                    ? uris.get(crop ? "art_crop" : "normal").getAsString() : null;
            final long stamp = stampOf(url);
            if (url == null || stamp <= 0 || file.lastModified() / 1000L >= stamp) {
                continue;
            }
            if (store.replace(imageKey, url, file)) {
                changed = 1;
            }
        }
        return changed;
    }

    /** La fecha de la imagen, en segundos: lo que va detras del "?". */
    static long stampOf(final String url) {
        if (url == null) {
            return 0;
        }
        final int q = url.lastIndexOf('?');
        if (q < 0 || q == url.length() - 1) {
            return 0;
        }
        try {
            return Long.parseLong(url.substring(q + 1).trim());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private static JsonObject frontUris(final JsonObject c) {
        if (c.has("image_uris")) {
            return c.getAsJsonObject("image_uris");
        }
        return faceUris(c, 0);
    }

    private static JsonObject backUris(final JsonObject c) {
        return c.has("image_uris") ? null : faceUris(c, 1);
    }

    private static JsonObject faceUris(final JsonObject c, final int face) {
        if (!c.has("card_faces")) {
            return null;
        }
        final JsonArray faces = c.getAsJsonArray("card_faces");
        if (faces.size() <= face) {
            return null;
        }
        final JsonObject f = faces.get(face).getAsJsonObject();
        return f.has("image_uris") ? f.getAsJsonObject("image_uris") : null;
    }

    private static String collector(final JsonObject c) {
        return c.has("collector_number") ? c.get("collector_number").getAsString() : "";
    }

    /** Las expansiones recientes de las que hay alguna imagen en disco. */
    static List<CardEdition> recentCachedSets() {
        final Calendar from = Calendar.getInstance();
        from.add(Calendar.MONTH, -MONTHS);
        final Date since = from.getTime();
        final File root = new File(ForgeConstants.CACHE_CARD_PICS_DIR);
        final List<CardEdition> out = new ArrayList<>();
        for (final CardEdition ed : StaticData.instance().getEditions()) {
            final Date d = ed.getDate();
            if (d == null || d.before(since) || ed.getScryfallCode() == null
                    || ed.getScryfallCode().isEmpty()) {
                continue;
            }
            final File dir = new File(root, ed.getCode());
            final String[] files = dir.list();
            if (files != null && files.length > 0) {
                out.add(ed);
            }
        }
        out.sort((a, b) -> b.getDate().compareTo(a.getDate()));
        return out;
    }

    /** Numero de coleccionista -> impresion, por edicion. */
    private static Map<String, Map<String, PaperCard>> printingsBy(final List<CardEdition> sets) {
        final Map<String, Map<String, PaperCard>> out = new HashMap<>();
        for (final CardEdition ed : sets) {
            out.put(ed.getCode(), new HashMap<>());
        }
        for (final PaperCard pc : forge.model.FModel.getMagicDb().getCommonCards().getAllCards()) {
            final Map<String, PaperCard> m = out.get(pc.getEdition());
            if (m != null) {
                String cn = pc.getCollectorNumber();
                if (cn != null && cn.endsWith("☇")) {
                    cn = cn.substring(0, cn.length() - 1);
                }
                m.putIfAbsent(cn, pc);
            }
        }
        return out;
    }

    /** Espera a que pase la busqueda anterior: ver {@link #SEARCH_EVERY_MS}. */
    private static synchronized void pace() {
        final long wait = lastSearchAt + SEARCH_EVERY_MS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastSearchAt = System.currentTimeMillis();
    }

    /**
     * Espera a que acabe el enfriamiento de Scryfall, si lo hay, diciendolo.
     * false si se ha cancelado mientras.
     */
    private static boolean waitCooldown(final Progress progress, final AtomicBoolean cancel) {
        if (!ScryfallRateLimiter.isCoolingDown()) {
            return true;
        }
        ScryfallRateLimiter.awaitCooldownCleared(() -> cancel != null && cancel.get(), message -> {
            if (progress != null) {
                progress.waiting(secondsIn(message));
            }
        });
        return cancel == null || !cancel.get();
    }

    /** Los segundos del aviso de Forge ("... waiting 27s before continuing..."). */
    static long secondsIn(final String message) {
        final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)s\\b").matcher(
                message == null ? "" : message);
        return m.find() ? Long.parseLong(m.group(1)) : 0;
    }

    /**
     * Todas las impresiones de una expansion en Scryfall, por paginas. null si
     * no se ha podido (sin red, limitados); lista vacia si Scryfall no la
     * tiene (404).
     */
    private static List<JsonObject> searchSet(final String scryfallCode, final Progress progress,
                                              final AtomicBoolean cancel) {
        final List<JsonObject> out = new ArrayList<>();
        String next = "https://api.scryfall.com/cards/search?q=e%3A" + scryfallCode.toLowerCase()
                + "&unique=prints&include_extras=true&include_variations=true";
        int pages = 0;
        int retries = 0;
        while (next != null && pages++ < 20) {
            // Si Scryfall ha pedido esperar (a nosotros o a una descarga de la
            // partida), se espera lo que diga y se sigue, en vez de rendirse.
            if (!waitCooldown(progress, cancel)) {
                return null;
            }
            pace();
            try {
                ScryfallRateLimiter.acquire(next);
                final HttpURLConnection http = (HttpURLConnection) new URL(next).openConnection();
                http.setRequestProperty("Accept", "application/json");
                http.setRequestProperty("User-Agent", BuildInfo.getUserAgent());
                http.setConnectTimeout(8_000);
                http.setReadTimeout(20_000);
                final int code = http.getResponseCode();
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    return out;
                }
                if (code == 429 && retries < RETRIES) {
                    // Se apunta el enfriamiento (lo respeta todo el programa)
                    // y se repite ESTA pagina cuando pase.
                    ScryfallRateLimiter.noteIfRateLimited(code, next, http.getHeaderField("Retry-After"));
                    http.disconnect();
                    retries++;
                    pages--;
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    ScryfallRateLimiter.noteIfRateLimited(code, next, http.getHeaderField("Retry-After"));
                    http.disconnect();
                    return null;
                }
                final String body;
                // A mano y no readAllBytes: esta clase la usa tambien Android,
                // y readAllBytes no llega hasta su API 33 (el minimo es la 26).
                try (InputStream in = http.getInputStream()) {
                    final java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
                    final byte[] buf = new byte[32 * 1024];
                    for (int n; (n = in.read(buf)) > 0; ) {
                        all.write(buf, 0, n);
                    }
                    body = new String(all.toByteArray(), StandardCharsets.UTF_8);
                }
                final JsonObject page = JsonParser.parseString(body).getAsJsonObject();
                for (final JsonElement e : page.getAsJsonArray("data")) {
                    out.add(e.getAsJsonObject());
                }
                next = page.has("has_more") && page.get("has_more").getAsBoolean()
                        && page.has("next_page") ? page.get("next_page").getAsString() : null;
            } catch (final Exception e) {
                System.err.println("[neo-img] no se pudo mirar la expansion " + scryfallCode + ": " + e);
                return null;
            }
        }
        return out;
    }
}
