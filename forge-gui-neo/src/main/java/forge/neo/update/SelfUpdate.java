package forge.neo.update;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * <b>Actualizar desde dentro del juego</b> (09-11-2026; idea de un jugador en
 * Discord, que se hizo un {@code .bat} que baja el zip de GitHub y lo descomprime
 * encima). <b>Solo Windows y solo el paquete con {@code .exe}</b>
 * ({@link #installRoot}): ni la copia portable de {@code D:}, ni Mac (el
 * {@code .app} no se puede escribir), ni Linux, ni lanzado desde el proyecto.
 *
 * <h2>La regla de todo esto</h2>
 *
 * <p>Ana, al encargar la revision: <i>"si da problemas, que salga un mensaje
 * de algo ha salido mal, tu carpeta de datos esta intacta, descargalo de
 * itch.io"</i>. O sea: <b>cualquier fallo acaba en "no se ha cambiado nada" o
 * en "se ha vuelto a la version de antes", y siempre con un mensaje</b>. Nunca
 * un juego a medias, nunca un juego que se cierra y no vuelve, y {@code datos\}
 * no se toca en ningun camino. Cada fallo previsible lleva su {@link Problem},
 * que el menu convierte en una frase que se entiende.
 *
 * <h2>Los pasos</h2>
 *
 * <ol>
 *   <li>Con el juego abierto y de fondo ({@link #prepare}): se comprueba
 *       ANTES de bajar nada que la carpeta se puede escribir y renombrar
 *       ({@link #preflight}; en {@code Archivos de programa} no) y que hay
 *       sitio; se baja el zip, se comprueba su <b>SHA-256</b> con el que publica
 *       GitHub, se descomprime en {@code <instalacion>\.neo-update\new} (mismo
 *       disco: renombrar es instantaneo) y se monta el cambiador
 *       ({@link #buildSwapper}).</li>
 *   <li>Al pulsar "Reiniciar" ({@link #launchSwapper}): el cambiador arranca y
 *       <b>el juego no se cierra hasta que el cambiador dice que esta vivo</b>
 *       ({@link #ALIVE}). Si no llega a arrancar, el juego sigue abierto y lo
 *       dice.</li>
 *   <li>{@link Swapper}, con el juego ya cerrado, cambia de version renombrando
 *       carpetas, abre la nueva y <b>espera a que diga que ha arrancado</b>
 *       ({@link #STARTED}); si muere sin decirlo, vuelve a poner la de antes.</li>
 * </ol>
 *
 * <h2>Rutas raras (un usuario polaco, 2026)</h2>
 *
 * <p>Nada de lo que hace esto pasa una ruta por un sitio por donde no pase ya el
 * propio juego. El cambiador no recibe ninguna ruta por la linea de ordenes (el
 * lanzador de jpackage la pasaria por la pagina de codigos ANSI): la saca de
 * donde esta su propio jar, igual que el juego encuentra el suyo. Si el juego
 * arranca desde esa carpeta, el cambiador tambien. Todo lo demas es Java, que
 * en Windows usa las llamadas Unicode del sistema.
 *
 * <p>GitHub solo se consulta al pulsar "Actualizar" (memoria "sin bucles ni
 * sondeos"): el aviso de version nueva sigue siendo el de itch.io
 * ({@link NeoUpdate}).
 */
public final class SelfUpdate {

    private SelfUpdate() {
    }

    // ------------------------------------------------------------------
    // CONTRATO entre versiones: estos nombres los lee una version y los
    // escribe otra (la que actualiza y la actualizada). NO CAMBIARLOS.

    /** Carpeta de trabajo, dentro de la instalacion: mismo disco, renombrar es instantaneo. */
    public static final String WORK_DIR = ".neo-update";
    /** Lo que deja escrito el cambiador para el arranque siguiente: {@code ok} o {@code failed\t...}. */
    public static final String RESULT = "result.txt";
    /** Lo escribe la version nueva al arrancar: el cambiador ya no tiene que deshacer nada. */
    public static final String STARTED = "started";
    // ------------------------------------------------------------------

    /** La version que hay preparada en {@code new}, esperando a reiniciar. */
    static final String READY = "ready.txt";
    /** El cambiador dice que ha arrancado: hasta entonces el juego no se cierra. */
    static final String ALIVE = "swapper.alive";
    /** El pid del juego, para el cambiador (por fichero, no por la linea de ordenes). */
    static final String JOB = "job.txt";
    /** Donde va el cambiador, dentro de {@link #WORK_DIR}. */
    static final String SWAPPER = "swapper";

    static final String API = "https://api.github.com/repos/AdrianLopez98/NeoForge/releases/latest";
    static final String ASSET = "NeoForge-windows-x64.zip";

    private static final int CONNECT_MS = 15_000;
    private static final int READ_MS = 60_000;

    /** Lo que dice GitHub de la ultima release. */
    public record Release(String version, String url, long size, String sha256) {
    }

    /** El avance: que se esta haciendo y cuanto (0..1, o -1 si no se sabe). */
    public interface Progress {
        void at(Step step, double fraction);
    }

    public enum Step { FETCH, DOWNLOAD, VERIFY, UNPACK, PREPARE }

    /** Por que no se ha podido: cada uno tiene su frase en el menu. */
    public enum Problem { NETWORK, GITHUB_BUSY, NOT_ON_GITHUB, SPACE, NO_WRITE, CORRUPT, BAD_PACKAGE, START, OTHER }

    /** Un fallo con su {@link Problem}. El mensaje es para el registro, no para el jugador. */
    public static final class UpdateException extends IOException {
        private static final long serialVersionUID = 1L;
        public final Problem problem;
        /** Para {@link Problem#SPACE}: cuanto hace falta, en MB. */
        public final long neededMb;

        UpdateException(final Problem problem, final String detail) {
            this(problem, detail, 0, null);
        }

        UpdateException(final Problem problem, final String detail, final long neededMb, final Throwable cause) {
            super(problem + ": " + detail, cause);
            this.problem = problem;
            this.neededMb = neededMb;
        }
    }

    // ------------------------------------------------------------------
    // Donde esta instalado

    /**
     * La carpeta del {@code NeoForge.exe} si este juego se puede actualizar
     * solo, o {@code null}. {@code -Dneo.selfupdate.root=...} la fuerza (pruebas).
     * Nunca lanza.
     */
    public static File installRoot() {
        try {
            final String forced = System.getProperty("neo.selfupdate.root");
            if (forced != null) {
                return new File(forced);
            }
            if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows")) {
                return null;
            }
            final String exe = ProcessHandle.current().info().command().orElse(null);
            if (exe == null || !new File(exe).getName().equalsIgnoreCase("NeoForge.exe")) {
                return null;
            }
            final File root = new File(exe).getAbsoluteFile().getParentFile();
            return looksInstalled(root) && !managedByItchApp(root) ? root : null;
        } catch (final Throwable e) {
            return null;
        }
    }

    /**
     * Instalado con la app de itch.io: esa app ya actualiza sola (con butler) y
     * lleva la cuenta de cada fichero. Si los cambiaramos nosotros, los veria
     * tocados y lo volveria a bajar entero. Se reconoce por su recibo,
     * {@code .itch\receipt.json.gz}, que deja en la carpeta del juego.
     */
    static boolean managedByItchApp(final File root) {
        return new File(root, ".itch").isDirectory();
    }

    /**
     * Tiene la forma del paquete de jpackage: {@code .exe}, {@code app\neo.jar},
     * {@code runtime} (sin {@code java.exe}: jpackage no lo pone, solo su
     * {@code jvm.dll}) y {@code res}.
     */
    static boolean looksInstalled(final File root) {
        return root != null
                && new File(root, "NeoForge.exe").isFile()
                && new File(root, "app" + File.separator + "neo.jar").isFile()
                && new File(root, "runtime" + File.separator + "bin" + File.separator + "server"
                        + File.separator + "jvm.dll").isFile()
                && new File(root, "res").isDirectory();
    }

    /**
     * La version bajada esta entera: lo de {@link #looksInstalled} y ademas lo
     * que necesita el lanzador ({@code app\NeoForge.cfg}) y las cartas.
     */
    static boolean looksComplete(final File game) {
        return looksInstalled(game)
                && new File(game, "app" + File.separator + "NeoForge.cfg").isFile()
                && new File(game, "res" + File.separator + "cardsfolder").isDirectory();
    }

    public static boolean supported() {
        return installRoot() != null;
    }

    // ------------------------------------------------------------------
    // GitHub

    /** La API; {@code -Dneo.selfupdate.api} la cambia (pruebas con un servidor local). */
    static String api() {
        return System.getProperty("neo.selfupdate.api", API);
    }

    /** La ultima release de GitHub. Lanza {@link UpdateException}. */
    public static Release fetchRelease() throws UpdateException {
        try {
            final HttpURLConnection c = open(api());
            c.setRequestProperty("Accept", "application/vnd.github+json");
            try {
                checkStatus(c);
                try (InputStream in = c.getInputStream()) {
                    final Release r = parseRelease(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    if (r == null) {
                        throw new UpdateException(Problem.NOT_ON_GITHUB, "no zip with a sha256 digest");
                    }
                    return r;
                }
            } finally {
                c.disconnect();
            }
        } catch (final IOException | RuntimeException e) {
            throw classify(e);
        }
    }

    /** Lo que importa del JSON de GitHub; {@code null} si no trae el zip con su huella. */
    static Release parseRelease(final String json) {
        try {
            final JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (o.has("draft") && o.get("draft").getAsBoolean()) {
                return null;
            }
            final String tag = o.get("tag_name").getAsString();
            final String version = ItchVersion.clean(tag.startsWith("v") ? tag.substring(1) : tag);
            final JsonArray assets = o.getAsJsonArray("assets");
            for (final JsonElement e : assets) {
                final JsonObject a = e.getAsJsonObject();
                if (!ASSET.equals(a.get("name").getAsString())) {
                    continue;
                }
                final String digest = a.has("digest") && !a.get("digest").isJsonNull()
                        ? a.get("digest").getAsString() : "";
                final String sha = digest.startsWith("sha256:") ? digest.substring(7).toLowerCase(Locale.ROOT) : "";
                final long size = a.get("size").getAsLong();
                final String url = a.get("browser_download_url").getAsString();
                if (version == null || !sha.matches("[0-9a-f]{64}") || size <= 0 || !url.startsWith("http")) {
                    return null;
                }
                return new Release(version, url, size, sha);
            }
        } catch (final RuntimeException e) {
            // JSON raro: "no se sabe".
        }
        return null;
    }

    private static HttpURLConnection open(final String url) throws IOException {
        final HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_MS);
        c.setReadTimeout(READ_MS);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "NeoForge-updater");
        return c;
    }

    /** GitHub limita a 60 consultas por hora y direccion: eso no es "no hay red". */
    private static void checkStatus(final HttpURLConnection c) throws IOException {
        final int code = c.getResponseCode();
        if (code == 403 || code == 429) {
            throw new UpdateException(Problem.GITHUB_BUSY, "HTTP " + code);
        }
        if (code == 404) {
            throw new UpdateException(Problem.NOT_ON_GITHUB, "HTTP 404");
        }
        if (code < 200 || code >= 300) {
            throw new UpdateException(Problem.NETWORK, "HTTP " + code);
        }
    }

    /** De una excepcion cualquiera, que le ha pasado de verdad. */
    static UpdateException classify(final Throwable e) {
        if (e instanceof UpdateException u) {
            return u;
        }
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException
                || e instanceof java.net.SocketTimeoutException || e instanceof java.net.NoRouteToHostException
                || e instanceof javax.net.ssl.SSLException || e instanceof java.net.SocketException) {
            return new UpdateException(Problem.NETWORK, String.valueOf(e), 0, e);
        }
        if (e instanceof AccessDeniedException) {
            return new UpdateException(Problem.NO_WRITE, String.valueOf(e), 0, e);
        }
        final String m = String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT);
        if (m.contains("not enough space") || m.contains("no space left") || m.contains("disk full")) {
            return new UpdateException(Problem.SPACE, String.valueOf(e), 0, e);
        }
        if (e instanceof FileSystemException && (m.contains("denied") || m.contains("acceso"))) {
            return new UpdateException(Problem.NO_WRITE, String.valueOf(e), 0, e);
        }
        if (e instanceof InvalidPathException || e instanceof java.util.zip.ZipException) {
            return new UpdateException(Problem.BAD_PACKAGE, String.valueOf(e), 0, e);
        }
        return new UpdateException(Problem.OTHER, String.valueOf(e), 0, e);
    }

    // ------------------------------------------------------------------
    // Preparar

    /**
     * Baja, comprueba, descomprime y deja el cambiador listo. Bloquea: llamarlo
     * en un hilo de fondo. Si {@code cancel} se pone a true, para (lanza
     * {@link java.util.concurrent.CancellationException}). Cualquier otro fallo
     * es una {@link UpdateException}, y en los dos casos la instalacion no se
     * ha tocado: todo lo de aqui va dentro de {@link #WORK_DIR}.
     */
    public static void prepare(final File root, final Release r, final Progress p, final AtomicBoolean cancel)
            throws UpdateException {
        try {
            final Path work = root.toPath().resolve(WORK_DIR);
            deleteTree(work);
            preflight(root.toPath());
            // Sitio: el zip, lo descomprimido (~2x) y la copia del Java.
            final long need = r.size() * 3 + 300L * 1024 * 1024;
            final long free = root.getUsableSpace();
            if (free > 0 && free < need) {
                throw new UpdateException(Problem.SPACE, free + " < " + need, need >> 20, null);
            }
            final Path zip = work.resolve(ASSET);
            download(r, zip, p, cancel);
            p.at(Step.VERIFY, -1);
            final String got = sha256(zip);
            if (!got.equalsIgnoreCase(r.sha256())) {
                throw new UpdateException(Problem.CORRUPT, "sha256 " + got + " != " + r.sha256());
            }
            check(cancel);
            final Path fresh = work.resolve("new");
            final java.util.List<String> names = unzip(zip, fresh, p, cancel);
            Files.deleteIfExists(zip);
            if (!looksComplete(fresh.resolve("NeoForge").toFile())) {
                throw new UpdateException(Problem.BAD_PACKAGE, "no complete NeoForge folder in the zip");
            }
            writeManifest(fresh.resolve("NeoForge").resolve("res"), names);
            p.at(Step.PREPARE, -1);
            buildSwapper(root.toPath(), work.resolve(SWAPPER), cancel);
            Files.writeString(work.resolve(READY), r.version(), StandardCharsets.UTF_8);
        } catch (final java.util.concurrent.CancellationException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            throw classify(e);
        }
    }

    /**
     * ¿Se puede escribir y renombrar aqui? Antes de bajar 300 MB: en
     * {@code C:\Archivos de programa} un usuario normal no puede, y con el
     * "acceso controlado a carpetas" de Windows tampoco. Se prueba de verdad
     * (crear, escribir, renombrar y borrar una carpeta al lado del juego), que
     * es lo unico que no miente.
     */
    static void preflight(final Path root) throws UpdateException {
        final Path work = root.resolve(WORK_DIR);
        final Path probe = root.resolve(".neo-update-probe");
        try {
            Files.createDirectories(work);
            deleteTree(probe);
            Files.createDirectories(probe);
            Files.writeString(probe.resolve("x.txt"), "x");
            final Path moved = work.resolve("probe");
            Files.move(probe, moved, StandardCopyOption.ATOMIC_MOVE);
            deleteTree(moved);
        } catch (final IOException | RuntimeException e) {
            try {
                deleteTree(probe);
            } catch (final IOException ignored) {
                // si no se puede escribir, tampoco habra quedado nada
            }
            throw new UpdateException(Problem.NO_WRITE, String.valueOf(e), 0, e);
        }
    }

    /**
     * El cambiador: una copia de ESTE lanzador, de ESTE Java y de ESTE jar,
     * fuera de lo que se va a cambiar.
     *
     * <p>⚠️ El Java de jpackage <b>no trae {@code java.exe} ni {@code javaw.exe}</b>
     * (solo {@code jvm.dll}, que carga {@code NeoForge.exe}): lo descubrio la
     * prueba de verdad sobre el paquete, el 09-11-2026. Asi que el cambiador se
     * arranca con el mismo lanzador, copiado, y con su propio
     * {@code app\NeoForge.cfg}: clase principal {@link Swapper}, y en el
     * classpath solo {@code neo.jar} (no necesita nada mas que {@code java.*}).
     */
    static void buildSwapper(final Path root, final Path to, final AtomicBoolean cancel) throws IOException {
        final Path app = to.resolve("app");
        Files.createDirectories(app);
        copyTree(root.resolve("runtime"), to.resolve("runtime"), cancel);
        Files.copy(root.resolve("NeoForge.exe"), to.resolve("NeoForge.exe"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(root.resolve("app").resolve("neo.jar"), app.resolve("neo.jar"), StandardCopyOption.REPLACE_EXISTING);
        final Path state = root.resolve("app").resolve(".jpackage.xml");
        if (Files.isRegularFile(state)) {
            Files.copy(state, app.resolve(".jpackage.xml"), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(app.resolve("NeoForge.cfg"), "[Application]\r\n"
                + "app.classpath=$APPDIR\\neo.jar\r\n"
                + "app.mainclass=" + Swapper.class.getName() + "\r\n\r\n"
                + "[JavaOptions]\r\n"
                + "java-options=-Dfile.encoding=UTF-8\r\n", StandardCharsets.US_ASCII);
    }

    private static void download(final Release r, final Path to, final Progress p, final AtomicBoolean cancel)
            throws IOException {
        final HttpURLConnection c = open(r.url());
        try {
            checkStatus(c);
            try (InputStream in = new BufferedInputStream(c.getInputStream(), 1 << 16);
                 OutputStream out = Files.newOutputStream(to)) {
                final long total = r.size();
                final byte[] buf = new byte[1 << 16];
                long done = 0;
                long lastShown = 0;
                int n;
                while ((n = in.read(buf)) >= 0) {
                    check(cancel);
                    out.write(buf, 0, n);
                    done += n;
                    if (done > total + (1 << 20)) {
                        throw new UpdateException(Problem.CORRUPT, "more bytes than announced");
                    }
                    if (done - lastShown > (1 << 20)) {
                        lastShown = done;
                        p.at(Step.DOWNLOAD, (double) done / total);
                    }
                }
                if (done != total) {
                    throw new UpdateException(Problem.NETWORK, "cut off: " + done + " of " + total + " bytes");
                }
            }
        } finally {
            c.disconnect();
        }
    }

    static String sha256(final Path file) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 1 << 16)) {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            final byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (final java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /**
     * La lista de lo que trae el paquete en {@code res} ({@link Swapper#MANIFEST}),
     * dentro de la propia {@code res} nueva: la siguiente actualizacion sabra
     * con ella que es del jugador y que no ({@link Swapper#carryUserFiles}).
     */
    static void writeManifest(final Path res, final java.util.List<String> names) throws IOException {
        final String prefix = "NeoForge/res/";
        final StringBuilder b = new StringBuilder();
        for (final String n : names) {
            if (n.regionMatches(true, 0, prefix, 0, prefix.length()) && n.length() > prefix.length()
                    && !n.endsWith("/")) {
                b.append(n.substring(prefix.length())).append('\n');
            }
        }
        Files.writeString(res.resolve(Swapper.MANIFEST), b.toString(), StandardCharsets.UTF_8);
    }

    /**
     * Descomprime sin dejar salir nada de {@code to} (zip-slip) y sin
     * {@code datos} ni perfil, vengan o no. Devuelve los ficheros que ha
     * puesto (rutas con {@code /}, relativas a {@code to}).
     */
    static java.util.List<String> unzip(final Path zip, final Path to, final Progress p, final AtomicBoolean cancel)
            throws IOException {
        final java.util.List<String> names = new java.util.ArrayList<>();
        final Path base = to.toAbsolutePath().normalize();
        Files.createDirectories(base);
        final long total = Math.max(1, Files.size(zip));
        try (var counted = new java.io.FilterInputStream(new BufferedInputStream(Files.newInputStream(zip), 1 << 16)) {
            long read;
            long shown;

            @Override
            public int read(final byte[] b, final int off, final int len) throws IOException {
                final int n = super.read(b, off, len);
                if (n > 0) {
                    read += n;
                    if (read - shown > (4 << 20)) {
                        shown = read;
                        p.at(Step.UNPACK, (double) read / total);
                    }
                }
                return n;
            }
        }; ZipInputStream in = new ZipInputStream(counted, legacyNames())) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) {
                check(cancel);
                final String name = e.getName().replace('\\', '/');
                final Path out = base.resolve(name).normalize();
                if (!out.startsWith(base) || out.equals(base)) {
                    throw new UpdateException(Problem.BAD_PACKAGE, "unsafe path in zip: " + name);
                }
                if (isProtected(base, out)) {
                    continue;
                }
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    names.add(base.relativize(out).toString().replace('\\', '/'));
                }
            }
        }
        return names;
    }

    /**
     * Como leer los nombres que NO vienen marcados como UTF-8: los zips de
     * Windows los escriben en la pagina de codigos de DOS (CP437). Lo cazo la
     * prueba de verdad con el zip de la 1.0.49: {@code clavileño.dck}, de la
     * Aventura, venia con la "ñ" en CP437 y Java, que por defecto lee UTF-8,
     * reventaba. Los que si vienen marcados siguen leyendose en UTF-8.
     */
    static java.nio.charset.Charset legacyNames() {
        try {
            return java.nio.charset.Charset.forName("IBM437");
        } catch (final RuntimeException e) {
            return StandardCharsets.ISO_8859_1;
        }
    }

    /** {@code NeoForge/datos/...} o {@code NeoForge/forge.profile.properties}: no se descomprime. */
    static boolean isProtected(final Path base, final Path out) {
        final Path rel = base.relativize(out);
        if (rel.getNameCount() < 2) {
            return false;
        }
        final String top = rel.getName(1).toString();
        return Swapper.PROTECTED.stream().anyMatch(top::equalsIgnoreCase);
    }

    // ------------------------------------------------------------------
    // Reiniciar y despues

    /** La version preparada en {@code new} y aun sin poner, o {@code null}. Nunca lanza. */
    public static String readyVersion(final File root) {
        if (root == null) {
            return null;
        }
        try {
            final Path work = root.toPath().resolve(WORK_DIR);
            if (Files.isRegularFile(work.resolve(READY)) && !Files.exists(work.resolve(RESULT))
                    && looksComplete(work.resolve("new").resolve("NeoForge").toFile())
                    && Files.isRegularFile(work.resolve(SWAPPER).resolve("app").resolve("NeoForge.cfg"))) {
                return ItchVersion.clean(Files.readString(work.resolve(READY), StandardCharsets.UTF_8).trim());
            }
        } catch (final IOException | RuntimeException e) {
            // nada preparado
        }
        return null;
    }

    /**
     * Lanza el cambiador y devuelve su proceso. Ninguna ruta por la linea de
     * ordenes: el cambiador las saca de donde esta su jar, y el pid del juego
     * va en {@link #JOB}.
     */
    public static Process launchSwapper(final File root) throws IOException {
        final Path work = root.toPath().resolve(WORK_DIR);
        Files.deleteIfExists(work.resolve(ALIVE));
        Files.writeString(work.resolve(JOB), "pid=" + ProcessHandle.current().pid() + "\n", StandardCharsets.US_ASCII);
        final Path exe = work.resolve(SWAPPER).resolve("NeoForge.exe");
        final ProcessBuilder pb = new ProcessBuilder(exe.toString())
                .directory(work.toFile())
                .redirectErrorStream(true)
                .redirectOutput(work.resolve("swapper.out").toFile());
        pb.environment().remove("_JPACKAGE_LAUNCHER");
        return pb.start();
    }

    /**
     * Espera a que el cambiador diga que esta vivo. Si no lo dice a tiempo, o se
     * muere antes, se mata y devuelve false: el juego sigue abierto.
     */
    public static boolean awaitSwapper(final File root, final Process p, final long maxMs) {
        final Path alive = root.toPath().resolve(WORK_DIR).resolve(ALIVE);
        final long end = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < end) {
            if (Files.isRegularFile(alive)) {
                return true;
            }
            if (!p.isAlive()) {
                return Files.isRegularFile(alive);
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (Files.isRegularFile(alive)) {
            return true;
        }
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
        return false;
    }

    /**
     * ¿Hay otro Neo Forge corriendo desde esta instalacion (la Aventura, otra
     * ventana)? El juego que pregunta y el lanzador que lo arranco no cuentan.
     */
    public static boolean othersRunning(final File root) {
        try {
            final java.util.Set<Long> mine = new java.util.HashSet<>();
            ProcessHandle h = ProcessHandle.current();
            while (h != null) {
                mine.add(h.pid());
                h = h.parent().orElse(null);
            }
            final String prefix = lower(root.toPath()) + File.separator;
            final String skip = lower(root.toPath().resolve(WORK_DIR)) + File.separator;
            return ProcessHandle.allProcesses().anyMatch(ph -> {
                if (mine.contains(ph.pid())) {
                    return false;
                }
                final String c = ph.info().command().map(s -> s.toLowerCase(Locale.ROOT)).orElse("");
                return c.startsWith(prefix) && !c.startsWith(skip);
            });
        } catch (final RuntimeException e) {
            return false;
        }
    }

    static String lower(final Path p) {
        return p.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    /** Como fue el ultimo cambio: la linea del cambiador y su registro (para el informe). */
    public record Outcome(String result, String log) {
        public boolean ok() {
            return result != null && result.startsWith("ok");
        }
    }

    /**
     * Al arrancar. Si el ultimo arranque fue un cambio de version, dice como
     * fue, <b>avisa al cambiador de que esta version ha arrancado</b>
     * ({@link #STARTED}) y limpia {@code .neo-update} de fondo — leyendo antes
     * su registro, que es lo que hace falta si hay que contar un fallo. Si lo
     * que hay es una descarga a medias (se cerro el juego bajando), la limpia
     * tambien. Nunca lanza.
     */
    public static Outcome afterStart(final File root) {
        if (root == null) {
            return null;
        }
        final Path work = root.toPath().resolve(WORK_DIR);
        try {
            if (!Files.isDirectory(work)) {
                return null;
            }
            final Path result = work.resolve(RESULT);
            Outcome out = null;
            if (Files.isRegularFile(result)) {
                final String text = Files.readString(result, StandardCharsets.UTF_8).trim();
                Files.writeString(work.resolve(STARTED), "ok", StandardCharsets.US_ASCII);
                out = new Outcome(text, text.startsWith("ok") ? null : UpdateReport.swapperLog(root));
            } else if (readyVersion(root) != null) {
                return null; // bajada y lista: se ofrece reiniciar
            }
            cleanLater(work);
            return out;
        } catch (final IOException | RuntimeException e) {
            System.err.println("[neo] actualizar: al arrancar: " + e);
            return null;
        }
    }

    /** Borra {@code .neo-update} de fondo, con paciencia: el cambiador puede tardar en soltar su Java. */
    static void cleanLater(final Path work) {
        final Thread t = new Thread(() -> {
            for (int i = 0; i < 40 && Files.exists(work, LinkOption.NOFOLLOW_LINKS); i++) {
                try {
                    deleteTree(work);
                } catch (final IOException | RuntimeException e) {
                    try {
                        Thread.sleep(1500);
                    } catch (final InterruptedException ie) {
                        return;
                    }
                }
            }
        }, "neo-update-cleanup");
        t.setDaemon(true);
        t.start();
    }

    /** Borra lo preparado (al cancelar, o si falla a medias). Nunca lanza. */
    public static void discard(final File root) {
        if (root == null) {
            return;
        }
        try {
            deleteTree(root.toPath().resolve(WORK_DIR));
        } catch (final IOException | RuntimeException e) {
            System.err.println("[neo] actualizar: no se ha podido limpiar ahora, se reintenta: " + e);
            cleanLater(root.toPath().resolve(WORK_DIR));
        }
    }

    // ------------------------------------------------------------------

    private static void check(final AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) {
            throw new java.util.concurrent.CancellationException();
        }
    }

    static void copyTree(final Path from, final Path to, final AtomicBoolean cancel) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes a) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes a) throws IOException {
                check(cancel);
                final Path dest = to.resolve(from.relativize(file).toString());
                Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                writable(dest);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Borra una carpeta entera. Sin seguir enlaces, y quitando el "solo
     * lectura" cuando hace falta: en Windows un fichero de solo lectura no se
     * deja borrar, y segun con que se descomprimio el juego los puede haber.
     */
    static void deleteTree(final Path dir) throws IOException {
        if (!Files.exists(dir, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!Files.isDirectory(dir, LinkOption.NOFOLLOW_LINKS)) {
            deleteOne(dir);
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes a) throws IOException {
                deleteOne(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path d, final IOException e) throws IOException {
                if (e != null) {
                    throw e;
                }
                deleteOne(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteOne(final Path p) throws IOException {
        try {
            Files.delete(p);
        } catch (final AccessDeniedException e) {
            writable(p);
            Files.delete(p);
        }
    }

    /** Quita el "solo lectura" de Windows, si lo tiene. */
    static void writable(final Path p) {
        try {
            final DosFileAttributeView dos = Files.getFileAttributeView(p, DosFileAttributeView.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (dos != null && dos.readAttributes().isReadOnly()) {
                dos.setReadOnly(false);
            }
        } catch (final IOException | RuntimeException e) {
            // si no se puede, ya lo dira quien intente borrarlo
        }
    }
}
