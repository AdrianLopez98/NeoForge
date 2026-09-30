package forge.neo.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Exportar e importar tus datos ({@link NeoBackup}) sin ventana y sin tocar
 * los datos de verdad: todo en carpetas temporales. {@code run.cmd backupcheck}.
 *
 * <p>Lo que se comprueba es lo que haria dano si fallara: que al exportar no
 * falte nada ni sobre (registros, musica sin pedirla), que un zip no pueda
 * escribir fuera de la carpeta de datos, que "Anadir" no pise lo tuyo, que
 * "Reemplazar" deje el dispositivo como el zip pero con SU escala y SU volumen,
 * que antes de tocar nada quede la copia, y que un zip pendiente se borre
 * aunque falle (si no, se reintentaria en cada arranque).
 */
public final class BackupCheck {

    private BackupCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        // -Dneo.backup.real=<zip>: exporta TUS datos de verdad (solo lee) y
        // dice que ha metido. Para ver tamanyos y cuentas reales.
        final String real = System.getProperty("neo.backup.real");
        if (real != null && !real.trim().isEmpty()) {
            try {
                final long t0 = System.nanoTime();
                final NeoBackup.Summary s = NeoBackup.export(DataPlaces.desktop(), new File(real), false,
                        forge.neo.NeoVersion.neoVersion(), DataPlaces.platform());
                System.out.printf(Locale.ROOT, "  datos reales: %s -> %s (%d KB, %.1f s)%n",
                        DataPlaces.desktop().root, real, new File(real).length() >> 10,
                        (System.nanoTime() - t0) / 1e9);
                System.out.println("  mazos " + s.decks + ", por grupo " + s.counts);
            } catch (final IOException e) {
                System.out.println("  [MAL]  exportar los datos reales: " + e);
            }
        }
        try {
            runChecks();
        } catch (final IOException | RuntimeException e) {
            check(false, "", "ha reventado: " + e);
            e.printStackTrace();
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de exportar/importar han fallado");
        }
    }

    private static void runChecks() throws IOException {
        final File tmp = Files.createTempDirectory("neo-backupcheck").toFile();
        try {
            // --- El PC de origen ------------------------------------------
            final File a = new File(tmp, "pc");
            final NeoBackup.Places pa = new NeoBackup.Places(a, new File(a, "neo/adventure/Forge/adventure"));
            write(a, "decks/commander/Atraxa.dck", "[metadata]\nName=Atraxa\n[Commander]\n1 Atraxa, Grand Unifier|ONE|1 (F)\n");
            write(a, "decks/commander/Coleccion X/Zinnia.dck", "[metadata]\nName=Zinnia\n");
            write(a, "decks/constructed/Elfos.dck", "[metadata]\nName=Elfos\n");
            write(a, "quest/saves/Mi quest.dat", "quest");
            write(pa.adventure, "Shandalar/slot1.sav", "aventura");
            write(pa.adventure, "settings.json", "{\"fullscreen\":true}");
            write(a, "achievements/constructed.xml", "<a/>");
            write(a, "neo/sleeves/funda.png", "png");
            write(a, "neo/music/cancion.mp3", "musica");
            write(a, "neo/neo.log", "registro");
            write(a, "preferences/forge.preferences", "no-va");
            writeProps(a, "preferences/neo.properties",
                    "uiScale", "1.5", "ascent.active", "true", "aiProfile", "Reckless", "language", "en-US");

            final File zip = new File(tmp, "NeoForge-datos.zip");
            final NeoBackup.Summary out = NeoBackup.export(pa, zip, false, "1.0.8", "windows");
            final NeoBackup.Summary in = NeoBackup.inspect(zip);
            check(in.valid, "el zip se reconoce como copia de Neo Forge", "el zip no se reconoce");
            check(in.decks == 3, "3 mazos, tambien el de la coleccion (" + in.decks + ")", "mazos: " + in.decks);
            check(in.count(NeoBackup.Group.ADVENTURE_SAVES) == 1 && in.count(NeoBackup.Group.QUEST) == 1,
                    "la partida de la Aventura y la de la Quest van dentro", "falta Quest o Aventura");
            check("1.0.8".equals(in.version) && "windows".equals(in.platform),
                    "el manifest dice version y plataforma", "manifest: " + in.version + "/" + in.platform);
            check(!names(zip).contains("data/neo/neo.log"), "los registros no van", "se ha colado un registro");
            check(!names(zip).contains("adventure/settings.json"),
                    "la configuracion de la Aventura de este dispositivo no va", "se ha colado adventure/settings.json");
            check(!names(zip).contains("data/preferences/forge.preferences"),
                    "las preferencias de Forge no van (llevan rutas de este equipo)", "se ha colado forge.preferences");
            check(!names(zip).contains("data/neo/music/cancion.mp3") && in.count(NeoBackup.Group.MUSIC) == 0,
                    "la musica no va si no se pide", "la musica se ha colado");
            check(names(zip).contains("data/decks/commander/Atraxa.dck")
                            && content(zip, "data/decks/commander/Atraxa.dck").contains("|ONE|1 (F)"),
                    "el mazo va tal cual: edicion, arte y foil dentro", "el mazo no va entero");
            final File zipMusic = new File(tmp, "con-musica.zip");
            NeoBackup.export(pa, zipMusic, true, "1.0.8", "windows");
            check(names(zipMusic).contains("data/neo/music/cancion.mp3"), "y va si se pide", "con musica no va");
            check(out.total() == in.total(), "lo exportado y lo leido cuadran", "no cuadran");

            // --- Un zip malicioso no escribe fuera ------------------------
            final NeoBackup.Places anyP = new NeoBackup.Places(new File(tmp, "x"), new File(tmp, "x/adv"));
            check(NeoBackup.destination(anyP, "data/../../evil.txt") == null
                            && NeoBackup.destination(anyP, "adventure/../../evil.txt") == null,
                    "un zip con ../ no puede escribir fuera de la carpeta", "ZIP SLIP: se escribe fuera");
            check(NeoBackup.destination(anyP, "data/secreto/x.txt") == null
                            && NeoBackup.destination(anyP, "otra/cosa.txt") == null,
                    "ni en carpetas que no son de ningun grupo", "acepta rutas de fuera de los grupos");

            // --- Android de destino: ANADIR --------------------------------
            final File b = new File(tmp, "android");
            final NeoBackup.Places pb = new NeoBackup.Places(b, new File(b, "adventure"));
            write(b, "decks/commander/Atraxa.dck", "[metadata]\nName=Atraxa\n# la mia\n");
            write(b, "decks/commander/Solo mio.dck", "[metadata]\nName=Solo mio\n");
            writeProps(b, "preferences/neo.properties", "uiScale", "0.8", "aiProfile", "Cautious");
            NeoBackup.stage(b, zip, NeoBackup.Mode.ADD);
            check(NeoBackup.hasPending(b), "importar deja el zip preparado para el arranque", "no queda pendiente");
            NeoBackup.applyPending(pb);
            check(!NeoBackup.hasPending(b), "y al arrancar se aplica y se quita", "el pendiente sigue ahi");
            check(read(b, "decks/commander/Atraxa.dck").contains("# la mia"),
                    "Anadir no pisa tu mazo del mismo nombre", "Anadir ha pisado un mazo");
            check(new File(b, "decks/commander/Coleccion X/Zinnia.dck").isFile()
                            && new File(b, "decks/constructed/Elfos.dck").isFile(),
                    "y trae los que no tenias, con su coleccion", "no ha traido los mazos nuevos");
            check(new File(pb.adventure, "Shandalar/slot1.sav").isFile(),
                    "la Aventura del PC va a donde la lee Android", "la Aventura no ha llegado a su sitio");
            final Properties pAdd = readProps(b, "preferences/neo.properties");
            check(pAdd.getProperty("language") == null && pAdd.getProperty("uiScale").equals("0.8"),
                    "Anadir no mete el idioma ni la escala del otro dispositivo (el movil usaba el del sistema)",
                    "se ha colado el idioma o la escala: " + pAdd);
            check("Cautious".equals(pAdd.getProperty("aiProfile")) && "true".equals(pAdd.getProperty("ascent.active")),
                    "ajustes: se quedan los tuyos y entran los que no tenias (la run de Ascenso)",
                    "ajustes mal fundidos: " + pAdd);
            final Properties r1 = NeoBackup.takeResult(b);
            check(r1 != null && r1.getProperty("error") == null && new File(r1.getProperty("backup", "-")).isFile(),
                    "antes de tocar nada queda la copia de lo que habia (" + (r1 == null ? "?" : new File(r1.getProperty("backup", "-")).getName()) + ")",
                    "no hay copia previa o hubo error: " + r1);
            check(NeoBackup.takeResult(b) == null, "el resultado se ensenya una sola vez", "el resultado se repite");

            // --- REEMPLAZAR ------------------------------------------------
            NeoBackup.stage(b, zip, NeoBackup.Mode.REPLACE);
            NeoBackup.applyPending(pb);
            check(read(b, "decks/commander/Atraxa.dck").contains("(F)"),
                    "Reemplazar deja el mazo como en el zip", "Reemplazar no ha sustituido el mazo");
            check(!new File(b, "decks/commander/Solo mio.dck").exists(),
                    "y quita lo que no estaba en el zip", "Reemplazar ha dejado mazos de antes");
            final Properties pRep = readProps(b, "preferences/neo.properties");
            check("0.8".equals(pRep.getProperty("uiScale")) && "Reckless".equals(pRep.getProperty("aiProfile")),
                    "ajustes: los del zip, menos la escala de este dispositivo", "ajustes mal: " + pRep);
            final Properties r2 = NeoBackup.takeResult(b);
            check(r2 != null && "REPLACE".equals(r2.getProperty("mode")),
                    "y el resultado dice que fue Reemplazar", "resultado: " + r2);

            // --- Un zip que no es de Neo Forge ------------------------------
            final File bad = new File(tmp, "cualquiera.zip");
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(bad))) {
                z.putNextEntry(new ZipEntry("data/decks/commander/x.dck"));
                z.write("x".getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            check(!NeoBackup.inspect(bad).valid, "un zip cualquiera no se acepta", "acepta un zip sin manifest");
            NeoBackup.stage(b, bad, NeoBackup.Mode.REPLACE);
            NeoBackup.applyPending(pb);
            final Properties r3 = NeoBackup.takeResult(b);
            check(!NeoBackup.hasPending(b) && r3 != null && r3.getProperty("error") != null
                            && new File(b, "decks/commander/Atraxa.dck").isFile(),
                    "y si llega al arranque, no toca nada, lo dice y no se reintenta",
                    "un zip malo ha hecho algo: " + r3);
        } finally {
            delete(tmp);
        }
    }

    // ------------------------------------------------------------------

    private static java.util.Set<String> names(final File zip) throws IOException {
        final java.util.Set<String> out = new java.util.HashSet<>();
        try (ZipFile z = new ZipFile(zip)) {
            z.stream().forEach(e -> out.add(e.getName()));
        }
        return out;
    }

    private static String content(final File zip, final String name) throws IOException {
        try (ZipFile z = new ZipFile(zip)) {
            return new String(z.getInputStream(z.getEntry(name)).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void write(final File root, final String rel, final String text) throws IOException {
        final File f = new File(root, rel);
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(text);
        }
    }

    private static void writeProps(final File root, final String rel, final String... kv) throws IOException {
        final Properties p = new Properties();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        final File f = new File(root, rel);
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            p.store(w, null);
        }
    }

    private static String read(final File root, final String rel) throws IOException {
        return new String(Files.readAllBytes(new File(root, rel).toPath()), StandardCharsets.UTF_8);
    }

    private static Properties readProps(final File root, final String rel) throws IOException {
        final Properties p = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(new File(root, rel)), StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }

    private static void delete(final File f) {
        final File[] kids = f.listFiles();
        if (kids != null) {
            for (final File k : kids) {
                delete(k);
            }
        }
        f.delete();
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [ok]   " + good);
        } else {
            failed++;
            System.out.println("  [MAL]  " + bad);
        }
    }
}
