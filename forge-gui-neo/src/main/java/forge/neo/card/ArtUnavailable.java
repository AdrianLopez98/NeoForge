package forge.neo.card;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.localinstance.properties.ForgeConstants;

/**
 * <b>Las imagenes que no se pueden bajar, apuntadas para no volver a contarlas.</b>
 *
 * <p>Reportado en itch.io el 24-09-2026, con el registro entero: tras bajar
 * "todas las cartas y todos los artes" quedaban ~740 que fallaban siempre, y
 * cada vez que se entraba a la pantalla decia "744 cards missing" — que parece
 * un fallo y no lo es. Son huecos entre lo que Forge cree que existe y lo que
 * hay: las Alchemy "A-" (solo digitales), las caras de fusion y de las cartas
 * giratorias de Kamigawa (van dentro de la imagen de otra), variantes
 * numeradas que Scryfall no tiene ("Forest3" de NEO) y alguna que Forge pide
 * a {@code downloads.cardforge.org}, que ya no resuelve.
 *
 * <p>El descargador de Forge no dice POR QUE fallo cada una (404, 429, sin
 * red...): todas cuentan igual como hechas. Asi que la regla es otra, y se
 * mira al terminar mirando el disco:
 *
 * <blockquote>Una que falta cuenta como no disponible solo si <b>despues de
 * ella</b> se bajo alguna otra en la misma tanda.</blockquote>
 *
 * <p>Eso prueba que habia red cuando le toco, y deja fuera los dos casos que
 * no hay que apuntar: una tanda sin conexion (no baja ninguna) y una conexion
 * que se cae a mitad (las de despues de la caida no tienen ninguna bajada
 * detras). Un 429 suelto si se colaria, pero el descargador espera el
 * enfriamiento de Scryfall antes de seguir, asi que es una o dos.
 *
 * <p>Las que fallan <b>despues</b> de la ultima bajada no las cubre esa
 * regla, y se le pregunta al servidor por cada una: ver {@link #record}.
 *
 * <p>Y por si acaso, <b>caduca a los {@value #DAYS} dias</b>: Scryfall anyade
 * imagenes (las de un set recien salido, justo como SOA en ese registro) y lo
 * que hoy no esta puede estar el mes que viene.
 *
 * <p>Se guarda al lado de la cache de imagenes, porque es un dato de ella: se
 * va con la carpeta {@code datos} al actualizar, y si se borra la cache se
 * borra con ella. La clave son las dos ultimas piezas de la ruta
 * ({@code SOA/Zombify3.fullborder.jpg}), no la ruta entera: en la copia
 * portable la letra de la unidad cambia de un ordenador a otro.
 */
public final class ArtUnavailable {

    /** Cuanto vale un "no esta". */
    static final int DAYS = 30;

    private ArtUnavailable() {
    }

    private static File file() {
        return new File(ForgeConstants.CACHE_DIR, "pics" + File.separator + "no-disponibles.txt");
    }

    private static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /** {@code .../pics/cards/SOA/Zombify3.fullborder.jpg} -> {@code soa/zombify3.fullborder.jpg} */
    static String key(final String localPath) {
        final File f = new File(localPath);
        final File parent = f.getParentFile();
        final String name = (parent == null ? "" : parent.getName() + "/") + f.getName();
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    /** Las apuntadas que siguen vigentes, con el dia en que se apunto cada una. */
    private static synchronized Map<String, Long> read() {
        final Map<String, Long> out = new HashMap<>();
        final File f = file();
        if (!f.isFile()) {
            return out;
        }
        final long oldest = today() - DAYS;
        try {
            for (final String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                final int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                try {
                    final long day = Long.parseLong(line.substring(0, tab));
                    if (day >= oldest) {
                        out.put(line.substring(tab + 1), day);
                    }
                } catch (final NumberFormatException ignored) {
                    // linea rota: se ignora, y al reescribir desaparece
                }
            }
        } catch (final IOException e) {
            System.err.println("[arte] no se pudo leer " + f + ": " + e);
        }
        return out;
    }

    private static synchronized void write(final Map<String, Long> all) {
        final File f = file();
        final List<String> lines = new ArrayList<>(all.size());
        all.forEach((k, day) -> lines.add(day + "\t" + k));
        java.util.Collections.sort(lines);
        try {
            Files.createDirectories(f.getParentFile().toPath());
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            System.err.println("[arte] no se pudo guardar " + f + ": " + e);
        }
    }

    /**
     * La tanda que se esta apuntando ahora mismo, o null. La esperan
     * {@link #filter} (contar justo despues de acabar daria la cifra de antes)
     * y la pantalla, que no dice "Listo" hasta saber cuantas se apuntaron.
     */
    private static volatile java.util.concurrent.CompletableFuture<Result> pending;

    /** Como quedo una tanda: cuantas se apuntaron y cuantas no se pudieron comprobar. */
    public static final class Result {
        public final int noted;
        public final int unchecked;

        Result(final int noted, final int unchecked) {
            this.noted = noted;
            this.unchecked = unchecked;
        }
    }

    /**
     * Apunta la tanda en un hilo aparte ({@code finish} llega en el de
     * interfaz, y la cola se pregunta al servidor una a una).
     */
    public static java.util.concurrent.CompletableFuture<Result> recordLater(final Map<String, String> downloads) {
        final java.util.concurrent.CompletableFuture<Result> f = new java.util.concurrent.CompletableFuture<>();
        pending = f;
        final Thread t = new Thread(() -> {
            try {
                f.complete(record(downloads));
            } catch (final RuntimeException e) {
                System.err.println("[arte] no se pudieron apuntar las no disponibles: " + e);
                f.complete(new Result(0, 0));
            }
        }, "neo-arte-no-disponibles");
        t.setDaemon(true);
        t.start();
        return f;
    }

    /** La ultima tanda apuntada o por apuntar; null si no hubo ninguna. */
    public static java.util.concurrent.CompletableFuture<Result> last() {
        return pending;
    }

    private static void awaitPending() {
        final java.util.concurrent.CompletableFuture<Result> f = pending;
        if (f != null) {
            try {
                f.get(10, java.util.concurrent.TimeUnit.MINUTES);
            } catch (final Exception ignored) {
                // si no acaba, se cuenta con lo que haya
            }
        }
    }

    /**
     * Quita de la lista del descargador las que ya se sabe que no estan.
     *
     * @return cuantas quito
     */
    public static int filter(final Map<String, String> downloads) {
        if (downloads == null || downloads.isEmpty()) {
            return 0;
        }
        awaitPending();
        final Map<String, Long> known = read();
        if (known.isEmpty()) {
            return 0;
        }
        final int before = downloads.size();
        downloads.keySet().removeIf(path -> known.containsKey(key(path)));
        final int removed = before - downloads.size();
        if (removed > 0) {
            System.out.println("[arte] " + removed + " no disponibles apuntadas: no se piden");
        }
        return removed;
    }

    /**
     * Si el fichero esta donde el CONTADOR lo busca: la clave tal cual, o su
     * version de borde completo. Es lo que decide si la proxima vez cuenta
     * como pendiente, y por eso manda a la hora de apuntar.
     */
    private static boolean counted(final String key) {
        return new File(key).exists()
                || key.contains(".full.") && new File(key.replace(".full.", ".fullborder.")).exists();
    }

    /**
     * Si el descargador lo ESCRIBIO: el decodifica la clave (un "+" pasa a
     * ser un espacio) antes de guardar. Es la prueba de que habia red.
     *
     * <p>Las dos cosas no coinciden cuando el nombre lleva "+" o "%": el
     * descargador de Forge lo guarda donde nadie lo busca, y esa imagen
     * contaria como pendiente para siempre aunque se baje bien. Reintentar no
     * sirve, asi que se apunta como las demas.
     */
    private static boolean written(final String key) {
        String decoded;
        try {
            decoded = java.net.URLDecoder.decode(key, StandardCharsets.UTF_8.name());
        } catch (final IllegalArgumentException | java.io.UnsupportedEncodingException e) {
            decoded = key;
        }
        return counted(decoded);
    }

    /**
     * Mira como quedo una tanda TERMINADA y apunta las que no estan.
     *
     * <p>{@code downloads} es la lista que se recorrio, en su orden (ruta ->
     * URL). Solo se llama cuando la tanda acaba entera: si se cancela, el
     * servicio no llama a {@code finish} y no se sabe hasta donde llego.
     *
     * <p><b>La cola se pregunta al servidor</b> (24-09-2026, segundo informe de
     * itch.io). Con la regla de arriba sola, las que fallaban DESPUES de la
     * ultima bajada no se apuntaban nunca — y en la pasada siguiente fallaban
     * todas, no bajaba ninguna y no se apuntaba nada: "310 cards missing" para
     * siempre, mientras la pantalla decia que estaban apuntadas. Asi que esas
     * se vuelven a pedir, una a una, y solo cuenta la respuesta que dice "no
     * existe" ({@link #verdict}). La primera que no responda eso con claridad
     * (429, 5xx, sin red) corta la comprobacion: lo que quede se vera la
     * proxima vez, y la pantalla lo dice.
     *
     * <p>Se guarda cada {@value #SAVE_EVERY}: si el jugador cierra el juego a
     * mitad de la comprobacion, lo ya comprobado no se pierde.
     */
    static Result record(final Map<String, String> downloads) {
        if (downloads == null || downloads.isEmpty()) {
            return new Result(0, 0);
        }
        final List<Map.Entry<String, String>> items = new ArrayList<>(downloads.entrySet());
        int lastOk = -1;
        for (int i = items.size() - 1; i >= 0; i--) {
            if (written(items.get(i).getKey())) {
                lastOk = i;
                break;
            }
        }
        final Map<String, Long> all = read();
        final long today = today();
        int added = 0;
        final List<Map.Entry<String, String>> tail = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            final String p = items.get(i).getKey();
            if (counted(p) || all.containsKey(key(p))) {
                continue;
            }
            if (i < lastOk || written(p)) {
                all.put(key(p), today);
                added++;
            } else {
                tail.add(items.get(i));
            }
        }
        if (added > 0) {
            write(all);
        }
        int unchecked = 0;
        Boolean online = null;
        int sinceSave = 0;
        for (int i = 0; i < tail.size(); i++) {
            final String p = tail.get(i).getKey();
            final String url = tail.get(i).getValue();
            Verdict v = verdict(url);
            // Un 429 no es "no se sabe": es "espera". Scryfall lo da aunque se
            // vaya a su ritmo (probado: 14 de 60 seguidas), y rendirse ahi
            // dejaba justo el hueco de antes. Se espera como el descargador.
            for (int retry = 0; v == Verdict.UNKNOWN && retry < 5
                    && forge.util.ScryfallRateLimiter.isCoolingDown(); retry++) {
                forge.util.ScryfallRateLimiter.awaitCooldownCleared(() -> false);
                v = verdict(url);
            }
            if (v == null) {
                // El servidor no resuelve. Solo es "no esta" si hay red: se
                // mira UNA vez, contra Scryfall.
                if (online == null) {
                    final Verdict probe = verdict("https://api.scryfall.com/");
                    online = probe != null && probe != Verdict.UNKNOWN;
                }
                v = online ? Verdict.GONE : Verdict.UNKNOWN;
            }
            if (v == Verdict.UNKNOWN) {
                unchecked += tail.size() - i;
                System.out.println("[arte] se deja de comprobar: el servidor no contesta claro");
                break;
            }
            if (v == Verdict.THERE && writable(p)) {
                // Esta: la proxima tanda la baja.
                unchecked++;
                continue;
            }
            // (y si esta pero su nombre no puede ser un fichero en este
            // sistema, no se bajara nunca: cuenta como no disponible)
            all.put(key(p), today);
            added++;
            if (++sinceSave >= SAVE_EVERY) {
                write(all);
                sinceSave = 0;
            }
        }
        if (sinceSave > 0) {
            write(all);
        }
        System.out.println("[arte] " + added + " imagenes no disponibles apuntadas (" + DAYS + " dias), "
                + unchecked + " sin comprobar");
        return new Result(added, unchecked);
    }

    private static final int SAVE_EVERY = 25;

    /** Si esa ruta puede ser un fichero aqui (en Windows no valen {@code " : ? *}...). */
    private static boolean writable(final String key) {
        try {
            java.nio.file.Paths.get(java.net.URLDecoder.decode(key, StandardCharsets.UTF_8.name()));
            return true;
        } catch (final IllegalArgumentException
                | java.io.UnsupportedEncodingException e) {
            return false;
        }
    }

    /** Lo que dice el servidor de una imagen. */
    enum Verdict {
        /** 404 y compania, o un servidor que ya no existe: se apunta. */
        GONE,
        /** Esta: la proxima tanda la baja. */
        THERE,
        /** No se sabe (429, 5xx, sin red): se para de preguntar. */
        UNKNOWN
    }

    /**
     * Pregunta por una URL sin bajarla. {@code null} si el servidor no
     * resuelve (un dominio muerto, o no hay red: lo decide quien llama).
     */
    static Verdict verdict(final String url) {
        if (url == null || url.isEmpty()) {
            return Verdict.GONE;
        }
        java.net.HttpURLConnection conn = null;
        try {
            final boolean api = forge.util.ScryfallRateLimiter.isApiUrl(url);
            if (api) {
                forge.util.ScryfallRateLimiter.acquire(url);
                // A 2/s (lo del limitador) Scryfall corta cada ~25 con un
                // castigo de 60 s: medido, 120 comprobaciones = 3 min. A 1/s
                // no corta y salen 2.
                Thread.sleep(500);
            }
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestProperty("User-Agent", forge.util.BuildInfo.getUserAgent());
            // Como el descargador: fuera de la API, una redireccion es "no esta".
            conn.setInstanceFollowRedirects(api);
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            final int code = conn.getResponseCode();
            if (code == 429) {
                forge.util.ScryfallRateLimiter.noteIfRateLimited(code, url, conn.getHeaderField("Retry-After"));
                return Verdict.UNKNOWN;
            }
            if (code >= 200 && code < 300) {
                return Verdict.THERE;
            }
            if ((code >= 300 && code < 400) || (code >= 400 && code < 500)) {
                return Verdict.GONE;
            }
            return Verdict.UNKNOWN;
        } catch (final java.net.UnknownHostException e) {
            return null;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return Verdict.UNKNOWN;
        } catch (final IOException | RuntimeException e) {
            return Verdict.UNKNOWN;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
