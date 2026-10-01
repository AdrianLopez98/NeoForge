package forge.neo.data;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * <b>Tus datos en un zip: exportar, e importar en otro sitio.</b>
 *
 * <p>Pedido en Discord el 30-09-2026: <i>"an import/export system in game ...
 * where you can copy old data for updates or between platforms? Rather than
 * manually going into files ... And it would also keep track of correct art
 * style / foil"</i>. El arte y el foil ya van dentro de cada {@code .dck}
 * (edicion, numero de arte y la marca de foil por carta), asi que basta con
 * llevarse los ficheros enteros.
 *
 * <p><b>Pura</b> (sin JavaFX, sin nada que Android 8 no tenga): la usan el
 * escritorio y Android por el jar, y es lo que hace posible pasar los datos
 * de uno a otro. Las dos plataformas tienen la misma carpeta de datos
 * ({@code CarpetaPublica.kt}: "datos\ del PC y Documents/NeoForge del movil
 * son la misma carpeta"), menos la Aventura, que cada una guarda en un sitio:
 * por eso en el zip va aparte ({@code adventure/}) y cada una la escribe donde
 * la lee ({@link Places}).
 *
 * <p><b>Importar no se hace con el juego abierto.</b> Con los mazos, la Quest
 * y los ajustes cargados, al cerrarse el juego los volveria a guardar ENCIMA
 * de lo importado. Asi que {@link #stage} solo deja el zip preparado, y
 * {@link #applyPending} lo aplica en el siguiente arranque, antes de que el
 * motor lea nada — como la mudanza de carpeta de Android. Y antes de tocar
 * nada, una copia de lo que habia ({@code neo/backups}): importar se deshace.
 */
public final class NeoBackup {

    private NeoBackup() {
    }

    /** Version del formato del zip. */
    public static final int FORMAT = 1;
    static final String MANIFEST = "neoforge-backup.properties";
    static final String DATA = "data/";
    static final String ADVENTURE = "adventure/";

    /** Que se lleva. La musica solo si se pide: pesa mucho. */
    public enum Group {
        DECKS("decks/"),
        QUEST("quest/"),
        ADVENTURE_SAVES(),
        ACHIEVEMENTS("achievements/"),
        PUZZLES("puzzle/"),
        TOURNAMENT("tournament/"),
        LOOK("neo/avatars/", "neo/playmats/", "neo/sleeves/"),
        SETTINGS("preferences/neo.properties"),
        MUSIC("neo/music/", "custom/");

        final List<String> paths;

        Group(final String... paths) {
            this.paths = Arrays.asList(paths);
        }
    }

    /**
     * Donde vive cada cosa en ESTE dispositivo.
     *
     * @param root      la carpeta de datos (la de Forge: decks, quest, preferences...)
     * @param adventure la de las partidas de la Aventura en este dispositivo
     */
    public static final class Places {
        public final File root;
        public final File adventure;

        public Places(final File root, final File adventure) {
            this.root = root;
            this.adventure = adventure;
        }
    }

    public enum Mode {
        /** Lo que ya tienes no se toca: solo entra lo que falta. */
        ADD,
        /** Queda como en el zip (lo tuyo va antes a una copia). */
        REPLACE
    }

    /** Lo que trae un zip (o lo que se ha exportado): cuantos ficheros de cada grupo. */
    public static final class Summary {
        public final Map<Group, Integer> counts = new EnumMap<>(Group.class);
        public int decks;
        public String version = "";
        public String platform = "";
        public String date = "";
        public boolean valid;

        public int total() {
            int n = 0;
            for (final int c : counts.values()) {
                n += c;
            }
            return n;
        }

        public int count(final Group g) {
            final Integer c = counts.get(g);
            return c == null ? 0 : c;
        }
    }

    // ==================================================================
    // Exportar

    /**
     * Mete en {@code zip} los datos de {@code places}. La musica solo si
     * {@code music}. {@code version} y {@code platform} van al manifest para
     * ensenyarlos al importar.
     */
    public static Summary export(final Places places, final File zip, final boolean music,
                                 final String version, final String platform) throws IOException {
        final Set<Group> groups = new HashSet<>(Arrays.asList(Group.values()));
        if (!music) {
            groups.remove(Group.MUSIC);
        }
        return export(places, zip, groups, version, platform);
    }

    static Summary export(final Places places, final File zip, final Set<Group> groups,
                          final String version, final String platform) throws IOException {
        final Summary s = new Summary();
        s.version = version == null ? "" : version;
        s.platform = platform == null ? "" : platform;
        s.date = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date());
        final File parent = zip.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        final File tmp = new File(zip.getPath() + ".tmp");
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            for (final Group g : Group.values()) {
                if (!groups.contains(g)) {
                    continue;
                }
                int n = 0;
                if (g == Group.ADVENTURE_SAVES) {
                    n += addTree(out, places.adventure, ADVENTURE, s);
                } else {
                    for (final String p : g.paths) {
                        final File f = new File(places.root, p);
                        if (p.endsWith("/")) {
                            n += addTree(out, f, DATA + p, s);
                        } else if (f.isFile()) {
                            addFile(out, f, DATA + p);
                            n++;
                        }
                    }
                }
                s.counts.put(g, n);
            }
            // El manifest, al final: ya se sabe cuanto hay.
            final Properties m = new Properties();
            m.setProperty("format", String.valueOf(FORMAT));
            m.setProperty("version", s.version);
            m.setProperty("platform", s.platform);
            m.setProperty("date", s.date);
            m.setProperty("decks", String.valueOf(s.decks));
            for (final Map.Entry<Group, Integer> e : s.counts.entrySet()) {
                m.setProperty("group." + e.getKey().name(), String.valueOf(e.getValue()));
            }
            out.putNextEntry(new ZipEntry(MANIFEST));
            final Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            m.store(w, "Neo Forge");
            w.flush();
            out.closeEntry();
        }
        if (zip.exists() && !zip.delete()) {
            throw new IOException("no se puede sustituir " + zip);
        }
        if (!tmp.renameTo(zip)) {
            throw new IOException("no se puede escribir " + zip);
        }
        s.valid = true;
        return s;
    }

    private static int addTree(final ZipOutputStream out, final File dir, final String prefix,
                               final Summary s) throws IOException {
        if (dir == null || !dir.isDirectory()) {
            return 0;
        }
        int n = 0;
        final File[] kids = dir.listFiles();
        if (kids == null) {
            return 0;
        }
        Arrays.sort(kids);
        for (final File k : kids) {
            if (k.isDirectory()) {
                n += addTree(out, k, prefix + k.getName() + "/", s);
            } else if (wanted(k.getName()) && !deviceOnly(prefix + k.getName())) {
                addFile(out, k, prefix + k.getName());
                n++;
                if (k.getName().toLowerCase(Locale.ROOT).endsWith(".dck")) {
                    s.decks++;
                }
            }
        }
        return n;
    }

    /**
     * Lo de ESTE dispositivo que no se lleva: la configuracion de la Aventura
     * (ventana, pantalla completa, escala), como las claves de DEVICE_KEYS.
     */
    static boolean deviceOnly(final String zipName) {
        return (ADVENTURE + "settings.json").equals(zipName);
    }

    /** Lo que nunca va: registros, cerrojos y basura del sistema. */
    static boolean wanted(final String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        return !n.endsWith(".log") && !n.endsWith(".lock") && !n.equals("thumbs.db")
                && !n.equals(".ds_store") && !n.endsWith(".tmp");
    }

    private static void addFile(final ZipOutputStream out, final File f, final String name) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
            copy(in, out);
        }
        out.closeEntry();
    }

    // ==================================================================
    // Mirar un zip antes de importarlo

    /** Lo que trae; {@code valid} a false si no es una copia de Neo Forge. */
    public static Summary inspect(final File zip) {
        final Summary s = new Summary();
        try (ZipFile z = new ZipFile(zip)) {
            final ZipEntry e = z.getEntry(MANIFEST);
            if (e == null) {
                return s;
            }
            final Properties m = new Properties();
            try (Reader r = new InputStreamReader(z.getInputStream(e), StandardCharsets.UTF_8)) {
                m.load(r);
            }
            s.version = m.getProperty("version", "");
            s.platform = m.getProperty("platform", "");
            s.date = m.getProperty("date", "");
            s.decks = parse(m.getProperty("decks"));
            for (final Group g : Group.values()) {
                final String v = m.getProperty("group." + g.name());
                if (v != null) {
                    s.counts.put(g, parse(v));
                }
            }
            s.valid = parse(m.getProperty("format")) >= 1;
        } catch (final IOException | RuntimeException e) {
            s.valid = false;
        }
        return s;
    }

    private static int parse(final String v) {
        try {
            return v == null ? 0 : Integer.parseInt(v.trim());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    // ==================================================================
    // Importar: se prepara ahora, se aplica al arrancar

    static final String PENDING = "neo/import-pendiente.zip";
    static final String PENDING_MODE = "neo/import-pendiente.modo";
    static final String RESULT = "neo/import-resultado.properties";
    static final String BACKUPS = "neo/backups";

    /** Deja {@code zip} listo para aplicarse en el siguiente arranque. */
    public static void stage(final File root, final File zip, final Mode mode) throws IOException {
        final File dest = new File(root, PENDING);
        dest.getParentFile().mkdirs();
        try (InputStream in = new BufferedInputStream(new FileInputStream(zip));
             OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
            copy(in, out);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(new File(root, PENDING_MODE)),
                StandardCharsets.UTF_8)) {
            w.write(mode.name());
        }
    }

    public static boolean hasPending(final File root) {
        return new File(root, PENDING).isFile();
    }

    /**
     * Lo que hay que hacer en el arranque, ANTES de que el motor o los ajustes
     * lean nada. No lanza nunca: un fallo aqui no puede dejar el juego sin
     * abrir. Lo que haya pasado queda en {@link #takeResult}.
     */
    public static void applyPending(final Places places) {
        final File pending = new File(places.root, PENDING);
        if (!pending.isFile()) {
            return;
        }
        final File modeFile = new File(places.root, PENDING_MODE);
        final Properties result = new Properties();
        try {
            Mode mode = Mode.ADD;
            if (modeFile.isFile()) {
                try (Reader r = new InputStreamReader(new FileInputStream(modeFile), StandardCharsets.UTF_8)) {
                    final char[] buf = new char[32];
                    final int n = r.read(buf);
                    if (n > 0 && "REPLACE".equals(new String(buf, 0, n).trim())) {
                        mode = Mode.REPLACE;
                    }
                }
            }
            final Summary incoming = inspect(pending);
            if (!incoming.valid) {
                result.setProperty("error", "not-a-backup");
            } else {
                // 1. La copia de lo que se va a tocar: de los grupos que trae.
                final Set<Group> touched = new HashSet<>(incoming.counts.keySet());
                final File backups = new File(places.root, BACKUPS);
                final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
                final File safety = new File(backups, "antes-de-importar-" + stamp + ".zip");
                export(places, safety, touched, "", "backup");
                pruneBackups(backups, 5);
                result.setProperty("backup", safety.getAbsolutePath());
                // 2. Y el zip.
                final int[] n = apply(places, pending, mode, touched);
                result.setProperty("mode", mode.name());
                result.setProperty("written", String.valueOf(n[0]));
                result.setProperty("kept", String.valueOf(n[1]));
                result.setProperty("decks", String.valueOf(incoming.decks));
                result.setProperty("from", incoming.platform);
                result.setProperty("version", incoming.version);
            }
        } catch (final IOException | RuntimeException e) {
            result.setProperty("error", String.valueOf(e));
        } finally {
            // Se borre como se borre: un zip pendiente que falla no puede
            // volver a intentarse en cada arranque.
            pending.delete();
            modeFile.delete();
            final File out = new File(places.root, RESULT);
            try (Writer w = new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8)) {
                result.store(w, "Neo Forge");
            } catch (final IOException ignored) {
                // sin resultado: el juego abre igual
            }
        }
    }

    /**
     * Lo que paso en la ultima importacion, una vez (se borra al leerlo), o
     * {@code null} si no hubo ninguna.
     */
    public static Properties takeResult(final File root) {
        final File f = new File(root, RESULT);
        if (!f.isFile()) {
            return null;
        }
        final Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (final IOException e) {
            p.setProperty("error", String.valueOf(e));
        }
        f.delete();
        return p;
    }

    /**
     * Escribe el zip. Devuelve {ficheros escritos, ficheros que ya habia y se
     * dejaron (modo ADD)}.
     */
    static int[] apply(final Places places, final File zip, final Mode mode, final Set<Group> touched)
            throws IOException {
        final int[] n = {0, 0};
        if (mode == Mode.REPLACE) {
            // Queda como en el zip: fuera lo que habia en esos grupos (ya esta
            // en la copia). Los ajustes no se borran: se funden abajo.
            for (final Group g : touched) {
                if (g == Group.SETTINGS) {
                    continue;
                }
                if (g == Group.ADVENTURE_SAVES) {
                    clear(places.adventure);
                } else {
                    for (final String p : g.paths) {
                        if (p.endsWith("/")) {
                            clear(new File(places.root, p));
                        }
                    }
                }
            }
        }
        try (ZipFile z = new ZipFile(zip)) {
            final java.util.Enumeration<? extends ZipEntry> all = z.entries();
            while (all.hasMoreElements()) {
                final ZipEntry e = all.nextElement();
                if (e.isDirectory() || MANIFEST.equals(e.getName())) {
                    continue;
                }
                final File dest = destination(places, e.getName());
                if (dest == null) {
                    continue;
                }
                if (e.getName().equals(DATA + Group.SETTINGS.paths.get(0))) {
                    mergeSettings(z, e, dest, mode);
                    n[0]++;
                    continue;
                }
                if (mode == Mode.ADD && dest.exists()) {
                    n[1]++;
                    continue;
                }
                dest.getParentFile().mkdirs();
                try (InputStream in = z.getInputStream(e);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(dest))) {
                    copy(in, out);
                }
                n[0]++;
            }
        }
        return n;
    }

    /**
     * Donde va una entrada del zip, o {@code null} si no es de un grupo
     * conocido o se sale de su carpeta ("zip slip": {@code ../../algo}).
     */
    static File destination(final Places places, final String name) throws IOException {
        final String n = name.replace('\\', '/');
        final File base;
        final String rel;
        if (n.startsWith(ADVENTURE)) {
            base = places.adventure;
            rel = n.substring(ADVENTURE.length());
        } else if (n.startsWith(DATA)) {
            rel = n.substring(DATA.length());
            if (!inAnyGroup(rel)) {
                return null;
            }
            base = places.root;
        } else {
            return null;
        }
        if (rel.isEmpty() || !wanted(rel.substring(rel.lastIndexOf('/') + 1)) || deviceOnly(n)) {
            return null;
        }
        final File dest = new File(base, rel).getCanonicalFile();
        final String root = base.getCanonicalPath() + File.separator;
        return dest.getPath().startsWith(root) ? dest : null;
    }

    private static boolean inAnyGroup(final String rel) {
        for (final Group g : Group.values()) {
            for (final String p : g.paths) {
                if (p.endsWith("/") ? rel.startsWith(p) : rel.equals(p)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Las claves de {@code neo.properties} que son de ESTE dispositivo y no
     * se traen de otro: la escala de un PC no le sirve a un movil. Todo lo
     * demas (la run de Ascenso, los desbloqueos, tus ajustes de partida) si.
     * La zona de ampliar de la mano de Android y su aviso tambien son del
     * aparato: en un plegable se pone en el centro de un lado y en un movil en
     * una esquina (decision 192 de Android, 01-10-2026).
     */
    static final Set<String> DEVICE_KEYS = new HashSet<>(Arrays.asList(
            "uiScale", "fullscreen", "hoverZoom", "textZoom", "soundVolume", "musicVolume",
            "touchHand", "neo.adventure.process", "language", "handZoomCorner", "handZoomHint"));

    /**
     * Los ajustes se funden clave a clave: con ADD entran solo las que no
     * tienes; con REPLACE, todas. Las del dispositivo ({@link #DEVICE_KEYS})
     * no entran NUNCA: con ADD tambien, porque un movil que no tenia "language"
     * (usaba el del sistema) se quedaba con el idioma del PC (probado en el
     * emulador, 30-09-2026).
     */
    static void mergeSettings(final ZipFile z, final ZipEntry e, final File dest, final Mode mode)
            throws IOException {
        final Properties incoming = new Properties();
        try (Reader r = new InputStreamReader(z.getInputStream(e), StandardCharsets.UTF_8)) {
            incoming.load(r);
        }
        final Properties mine = new Properties();
        if (dest.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(dest), StandardCharsets.UTF_8)) {
                mine.load(r);
            }
        }
        final Map<String, String> merged = new LinkedHashMap<>();
        for (final String k : mine.stringPropertyNames()) {
            merged.put(k, mine.getProperty(k));
        }
        for (final String k : incoming.stringPropertyNames()) {
            final boolean have = merged.containsKey(k);
            if (DEVICE_KEYS.contains(k)) {
                continue;
            }
            if (mode == Mode.REPLACE || !have) {
                merged.put(k, incoming.getProperty(k));
            }
        }
        final Properties out = new Properties();
        for (final Map.Entry<String, String> x : merged.entrySet()) {
            out.setProperty(x.getKey(), x.getValue());
        }
        dest.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(dest), StandardCharsets.UTF_8)) {
            out.store(w, "Neo Forge");
        }
    }

    private static void clear(final File dir) {
        final File[] kids = dir == null ? null : dir.listFiles();
        if (kids == null) {
            return;
        }
        for (final File k : kids) {
            if (k.isDirectory()) {
                clear(k);
            }
            k.delete();
        }
    }

    private static void pruneBackups(final File dir, final int keep) {
        final File[] all = dir.listFiles((d, n) -> n.startsWith("antes-de-importar-") && n.endsWith(".zip"));
        if (all == null || all.length <= keep) {
            return;
        }
        Arrays.sort(all);
        for (int i = 0; i < all.length - keep; i++) {
            all[i].delete();
        }
    }

    private static void copy(final InputStream in, final OutputStream out) throws IOException {
        final byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
    }

    /** Los grupos que se ensenyan al importar, en orden. */
    public static List<Group> shownGroups() {
        return new ArrayList<>(Arrays.asList(Group.DECKS, Group.QUEST, Group.ADVENTURE_SAVES,
                Group.ACHIEVEMENTS, Group.PUZZLES, Group.TOURNAMENT, Group.LOOK, Group.SETTINGS, Group.MUSIC));
    }
}
