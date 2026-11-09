package forge.neo.update;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * {@code run.cmd selfupdatecheck}: actualizar desde dentro ({@link SelfUpdate},
 * {@link Swapper}) sin tocar nada de verdad. Todo sobre instalaciones de
 * mentira en una carpeta temporal <b>cuyo nombre lleva espacios, una ż polaca,
 * una ñ, acentos, kanji, comillas y parentesis</b> (un usuario polaco ya nos
 * rompio el arranque una vez). Se mira:
 *
 * <ul>
 *   <li>el JSON de GitHub y que cualquier rareza sea "no hay";</li>
 *   <li>descomprimir: zip-slip, nombres CP437, {@code datos} dentro del zip;</li>
 *   <li>el cambio con {@code datos} y perfil <b>identicos byte a byte</b>;</li>
 *   <li>un fallo a medias que deja la instalacion <b>exactamente</b> como estaba;</li>
 *   <li>el cambiador entero ({@link Swapper#run}): la version nueva que arranca,
 *       la que <b>no arranca</b> (se vuelve a la de antes), y el juego que
 *       <b>no se cierra</b> (no se toca ni se abre nada);</li>
 *   <li>el cambiador como proceso aparte, como lo lanza el juego;</li>
 *   <li>una carpeta sin permiso de escritura (como Archivos de programa): se
 *       sabe ANTES de bajar nada;</li>
 *   <li>ficheros de solo lectura al limpiar;</li>
 *   <li>que cada fallo tenga su frase.</li>
 * </ul>
 *
 * Lo que NO puede probar (el lanzador de jpackage, el {@code .exe} de verdad) se
 * prueba de punta a punta con {@code -Dneo.selfupdate.autotest} sobre una copia
 * del paquete: ver {@code tools/probar-actualizador.py}.
 */
public final class SelfUpdateCheck {

    private SelfUpdateCheck() {
    }

    /** Lo peor que puede llevar una ruta de Windows y que el juego aguanta. */
    static final String WEIRD = "Gra Użytkownika łżń ñ é 漢字 (copia) 'x' & y";

    private static int good;
    private static int bad;

    public static void run() throws Exception {
        good = 0;
        bad = 0;
        final Path tmp = Files.createTempDirectory("neo-selfupdate").resolve(WEIRD);
        Files.createDirectories(tmp);
        try {
            json();
            unzip(tmp);
            swap(tmp);
            rollBack(tmp);
            carry(tmp);
            swapperRuns(tmp);
            process(tmp);
            awaitSwapper(tmp);
            noWrite(tmp);
            readOnly(tmp);
            texts();
            privacy();
            here();
            online();
        } finally {
            SelfUpdate.deleteTree(tmp.getParent());
        }
        System.out.printf("%n  %d bien, %d mal%n", good, bad);
        if (bad > 0) {
            throw new IllegalStateException("actualizar desde dentro: no cuadra");
        }
    }

    // ------------------------------------------------------------------

    private static final String SHA = "2c005f831818249339efdd53bd03e9daa409e5324abbd6d5cb9a9d071098444e";

    private static void json() {
        final String ok = "{\"tag_name\":\"v1.0.51\",\"draft\":false,\"assets\":["
                + "{\"name\":\"NeoForge-android.apk\",\"size\":1,\"digest\":\"sha256:aa\",\"browser_download_url\":\"x\"},"
                + "{\"name\":\"NeoForge-windows-x64.zip\",\"size\":316257429,\"digest\":\"sha256:" + SHA.toUpperCase() + "\","
                + "\"browser_download_url\":\"https://github.com/z.zip\"}]}";
        final SelfUpdate.Release r = SelfUpdate.parseRelease(ok);
        check(r != null && "1.0.51".equals(r.version()) && SHA.equals(r.sha256())
                && r.size() == 316257429L && r.url().endsWith("z.zip"), "GitHub: version, huella y enlace del zip");
        check(SelfUpdate.parseRelease(ok.replace("\"digest\":\"sha256:" + SHA.toUpperCase() + "\",", "")) == null,
                "sin huella, no se ofrece (no se podria comprobar)");
        check(SelfUpdate.parseRelease(ok.replace(SHA.toUpperCase(), "abc")) == null, "una huella que no es SHA-256, tampoco");
        check(SelfUpdate.parseRelease(ok.replace("windows-x64", "windows-arm")) == null, "sin el zip de Windows, nada");
        check(SelfUpdate.parseRelease(ok.replace("\"draft\":false", "\"draft\":true")) == null, "un borrador, nada");
        check(SelfUpdate.parseRelease("{\"message\":\"API rate limit exceeded\"}") == null
                && SelfUpdate.parseRelease("<html>") == null && SelfUpdate.parseRelease("") == null,
                "una respuesta rara es 'no hay'");
    }

    private static void unzip(final Path tmp) throws IOException {
        final Path zip = tmp.resolve("bueno.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(zip))) {
            put(z, "NeoForge/NeoForge.exe", "exe");
            put(z, "NeoForge/app/neo.jar", "jar");
            put(z, "NeoForge/datos/decks/mio.dck", "NO");
            put(z, "NeoForge/forge.profile.properties", "NO");
            put(z, "NeoForge/res/a.txt", "a");
            put(z, "NeoForge\\res\\barras.txt", "b");
        }
        final Path out = tmp.resolve("unzip");
        SelfUpdate.unzip(zip, out, (s, f) -> { }, null);
        check(Files.isRegularFile(out.resolve("NeoForge/app/neo.jar"))
                && Files.isRegularFile(out.resolve("NeoForge/res/a.txt"))
                && Files.isRegularFile(out.resolve("NeoForge/res/barras.txt")),
                "descomprime lo del juego (tambien con barras de Windows en el nombre)");
        check(!Files.exists(out.resolve("NeoForge/datos")) && !Files.exists(out.resolve("NeoForge/forge.profile.properties")),
                "un datos o un perfil dentro del zip no se descomprimen");

        final Path evil = tmp.resolve("malo.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(evil))) {
            put(z, "NeoForge/../../fuera.txt", "x");
        }
        boolean refused;
        try {
            SelfUpdate.unzip(evil, tmp.resolve("unzip2"), (s, f) -> { }, null);
            refused = false;
        } catch (final IOException e) {
            refused = true;
        }
        check(refused && !Files.exists(tmp.resolve("fuera.txt")), "un zip que intenta escribir fuera se rechaza");

        final Path dos = tmp.resolve("dos.zip");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(dos), java.nio.charset.Charset.forName("IBM437"))) {
            put(z, "NeoForge/res/clavileño.dck", "x");
        }
        SelfUpdate.unzip(dos, tmp.resolve("unzip-dos"), (s, f) -> { }, null);
        check(Files.isRegularFile(tmp.resolve("unzip-dos/NeoForge/res/clavileño.dck")),
                "un nombre en CP437 (zip de Windows) sale con su ñ");

        final Path abc = tmp.resolve("abc");
        Files.writeString(abc, "abc");
        check("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad".equals(SelfUpdate.sha256(abc)),
                "SHA-256 correcto");
    }

    private static void swap(final Path tmp) throws IOException {
        final Path root = fakeInstall(tmp.resolve("swap"), "vieja");
        final Path fresh = fakeGame(tmp.resolve("swap-new").resolve("NeoForge"), "nueva");
        final Map<String, String> datosBefore = snapshot(root.resolve("datos"));
        final String profileBefore = Files.readString(root.resolve("forge.profile.properties"));
        final String r = Swapper.swapOrRollBack(root, fresh, root.resolve(SelfUpdate.WORK_DIR).resolve("old"),
                new ArrayList<>(), s -> { });
        check(r.startsWith("ok"), "el cambio sale bien en una ruta con ż, ñ, kanji y comillas");
        check("nueva".equals(read(root.resolve("app/neo.jar"))) && "nueva".equals(read(root.resolve("NeoForge.exe")))
                && Files.exists(root.resolve("res/nueva.txt")) && !Files.exists(root.resolve("res/vieja.txt")),
                "queda la version nueva (y res es la nueva entera, sin restos de la vieja)");
        check(datosBefore.equals(snapshot(root.resolve("datos"))) && !datosBefore.isEmpty(),
                "datos identico byte a byte (" + datosBefore.size() + " ficheros, uno con ż en el nombre)");
        check(profileBefore.equals(Files.readString(root.resolve("forge.profile.properties"))), "el perfil, intacto");
        check("vieja".equals(read(root.resolve(SelfUpdate.WORK_DIR).resolve("old/app/neo.jar"))),
                "la vieja queda apartada en .neo-update\\old");
        check(Files.exists(root.resolve("suelto-del-jugador.txt")), "lo que no viene en el zip no se toca");
    }

    private static void rollBack(final Path tmp) throws IOException {
        final Path root = fakeInstall(tmp.resolve("rb"), "vieja");
        final Path fresh = fakeGame(tmp.resolve("rb-new").resolve("NeoForge"), "nueva");
        final Map<String, String> before = withoutWork(snapshot(root));
        // El fallo: en old ya hay un "res", asi que apartar res revienta a medias.
        final Path old = root.resolve(SelfUpdate.WORK_DIR).resolve("old");
        Files.createDirectories(old);
        Files.writeString(old.resolve("res"), "estorbo");
        final String r = Swapper.swapOrRollBack(root, fresh, old, new ArrayList<>(), s -> { });
        check(r.startsWith("failed"), "un fallo a medias se dice");
        check(before.equals(withoutWork(snapshot(root))),
                "y la instalacion queda EXACTAMENTE como estaba (" + before.size() + " ficheros)");
        check(Files.exists(fresh.resolve("app/neo.jar")), "la version nueva vuelve a su sitio de preparada");
    }

    /**
     * Lo que el jugador puso en res pasa a la version nueva; lo que Forge quito
     * entre versiones, no. Con la lista del paquete y, sin ella, por la fecha.
     */
    private static void carry(final Path tmp) throws IOException {
        for (final boolean withList : new boolean[] {false, true}) {
            final String tag = withList ? "con lista" : "por fecha";
            final Path root = fakeInstall(tmp.resolve("carry-" + withList), "vieja");
            final Path res = root.resolve("res");
            Files.createDirectories(res.resolve("editions"));
            Files.writeString(res.resolve("editions/A.txt"), "A vieja");
            Files.writeString(res.resolve("editions/QUITADA.txt"), "Forge la quito");
            Files.writeString(res.resolve("editions/B.txt"), "B del jugador");
            Files.createDirectories(res.resolve("adventure/Mi Mundo ż"));
            Files.writeString(res.resolve("adventure/Mi Mundo ż/mapa.json"), "mod");
            Files.createDirectories(res.resolve("cartas"));
            for (int i = 0; i < 200; i++) {
                Files.writeString(res.resolve("cartas/" + i + ".txt"), "c" + i);
            }
            try (Stream<Path> st = Files.walk(res)) {
                for (final Path f : st.filter(Files::isRegularFile).toList()) {
                    created(f, "2026-01-01T10:00:00Z");
                }
            }
            if (withList) {
                // Con lista manda la lista, aunque las fechas digan otra cosa.
                // Con BOM, como la escribiria PowerShell 5.1: tiene que dar igual.
                final StringBuilder list = new StringBuilder("\uFEFFvieja.txt\ncomun.txt\neditions/A.txt\neditions/QUITADA.txt\n");
                for (int i = 0; i < 200; i++) {
                    list.append("cartas/").append(i).append(".txt\n");
                }
                Files.writeString(res.resolve(Swapper.MANIFEST), list.toString());
            } else {
                created(res.resolve("editions/B.txt"), "2026-02-01T10:00:00Z");
                created(res.resolve("adventure/Mi Mundo ż/mapa.json"), "2026-02-01T10:00:00Z");
            }
            final Path fresh = fakeGame(tmp.resolve("carry-new-" + withList).resolve("NeoForge"), "nueva");
            Files.createDirectories(fresh.resolve("res/cartas"));
            for (int i = 0; i < 200; i++) {
                Files.writeString(fresh.resolve("res/cartas/" + i + ".txt"), "c" + i);
            }
            Files.createDirectories(fresh.resolve("res/editions"));
            Files.writeString(fresh.resolve("res/editions/A.txt"), "A nueva");
            Files.writeString(fresh.resolve("res/editions/B.txt"), "B oficial");
            final String r = Swapper.swapOrRollBack(root, fresh, root.resolve(SelfUpdate.WORK_DIR).resolve("old"),
                    new ArrayList<>(), s -> { });
            check(r.startsWith("ok") && "mod".equals(read(res.resolve("adventure/Mi Mundo ż/mapa.json"))),
                    tag + ": el mod del jugador (en una carpeta con ż) pasa a la version nueva"
                            + (withList ? " (lista con BOM)" : " (aunque otro suyo se llame como uno oficial)"));
            check(!Files.exists(res.resolve("editions/QUITADA.txt")) && !Files.exists(res.resolve("vieja.txt")),
                    tag + ": lo que Forge quito entre versiones NO vuelve");
            check("A nueva".equals(read(res.resolve("editions/A.txt"))) && "B oficial".equals(read(res.resolve("editions/B.txt"))),
                    tag + ": lo que trae la nueva manda (no se pisa con lo viejo)");
        }

        // La lista que deja cada actualizacion: lo que trae el zip en res, nada mas.
        final Path res = tmp.resolve("manifest/NeoForge/res");
        Files.createDirectories(res);
        SelfUpdate.writeManifest(res, List.of("NeoForge/NeoForge.exe", "NeoForge/res/editions/A.txt",
                "NeoForge/res/adventure/Mi Mundo ż/mapa.json", "NeoForge/app/neo.jar"));
        final java.util.Set<String> m = Swapper.readManifest(res.resolve(Swapper.MANIFEST));
        check(m != null && m.equals(java.util.Set.of("editions/a.txt", "adventure/mi mundo ż/mapa.json")),
                "la lista del paquete: solo lo de res, y se lee igual (con ż)");
    }

    /** El cambiador entero, en este proceso: arranca / no arranca / no se cierra el juego. */
    private static void swapperRuns(final Path tmp) throws Exception {
        // 1. La nueva arranca (alguien escribe "started" en cuanto ve el ok).
        Path root = staged(tmp.resolve("run-ok"));
        Path work = root.resolve(SelfUpdate.WORK_DIR);
        final Map<String, String> datos = snapshot(root.resolve("datos"));
        final Path w = work;
        final Thread fakeNew = new Thread(() -> {
            for (int i = 0; i < 400; i++) {
                try {
                    final Path res = w.resolve(SelfUpdate.RESULT);
                    if (Files.isRegularFile(res) && Files.readString(res).startsWith("ok")) {
                        Files.writeString(w.resolve(SelfUpdate.STARTED), "ok");
                        return;
                    }
                    Thread.sleep(25);
                } catch (final Exception e) {
                    // el fichero se esta escribiendo: otra vuelta
                }
            }
        });
        fakeNew.start();
        String r = Swapper.run(work, 5_000, 30_000);
        fakeNew.join();
        check(r.startsWith("ok") && "nueva".equals(read(root.resolve("app/neo.jar")))
                && Files.isRegularFile(work.resolve(SelfUpdate.ALIVE)),
                "cambiador: dice que esta vivo, cambia, y la nueva arranca -> se queda la nueva");
        check(datos.equals(snapshot(root.resolve("datos"))), "  y datos, intacto");
        check("mi skin".equals(read(root.resolve("res/skins/mia/skin.txt"))),
                "  y la skin que el jugador puso en res sigue ahi, en la version nueva");

        // 2. La nueva NO arranca (nadie escribe "started"): se vuelve a la de antes.
        root = staged(tmp.resolve("run-dead"));
        work = root.resolve(SelfUpdate.WORK_DIR);
        final Map<String, String> before = withoutWork(snapshot(root));
        r = Swapper.run(work, 5_000, 30_000);
        check(r.startsWith("failed\tstart") && before.equals(withoutWork(snapshot(root))),
                "cambiador: la nueva no arranca -> vuelve EXACTAMENTE la de antes");
        check(Files.readString(work.resolve(SelfUpdate.RESULT)).startsWith("failed\tstart"),
                "  y lo deja escrito para que la de antes lo diga al abrirse");
        check("mi skin".equals(read(root.resolve("res/skins/mia/skin.txt"))),
                "  y la skin del jugador vuelve con la de antes");

        // 3. El juego no se cierra: ni se toca ni se abre nada.
        root = staged(tmp.resolve("run-busy"));
        work = root.resolve(SelfUpdate.WORK_DIR);
        final Map<String, String> busyBefore = withoutWork(snapshot(root));
        final Process alive = new ProcessBuilder("cmd", "/c", "ping -n 30 127.0.0.1 > nul").start();
        try {
            Files.writeString(work.resolve(SelfUpdate.JOB), "pid=" + alive.pid() + "\n");
            r = Swapper.run(work, 2_000, 30_000);
        } finally {
            alive.descendants().forEach(ProcessHandle::destroyForcibly);
            alive.destroyForcibly();
        }
        check("failed\tbusy".equals(r) && busyBefore.equals(withoutWork(snapshot(root))),
                "cambiador: el juego no se cierra -> no se toca nada");
    }

    /** El cambiador como lo lanza el juego: otro proceso. */
    private static void process(final Path tmp) throws Exception {
        final Path root = staged(tmp.resolve("proc"));
        final Path work = root.resolve(SelfUpdate.WORK_DIR);
        check("1.0.99".equals(SelfUpdate.readyVersion(root.toFile())), "lo preparado se reconoce al arrancar");
        final Map<String, String> before = withoutWork(snapshot(root));
        final String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // Sin "started" (la nueva es de mentira y no arranca): tiene que volver a la vieja.
        final ProcessBuilder pb = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                Swapper.class.getName()).redirectErrorStream(true);
        // La ruta rara por el entorno: por argumento llegaria rota (pagina ANSI).
        pb.environment().put("NEO_SWAPPER_ROOT", root.toString());
        final Process p = pb.start();
        p.getInputStream().readAllBytes();
        p.waitFor();
        final String result = Files.isRegularFile(work.resolve(SelfUpdate.RESULT))
                ? Files.readString(work.resolve(SelfUpdate.RESULT)) : "(nada)";
        check(result.startsWith("failed\tstart") && before.equals(withoutWork(snapshot(root))),
                "el cambiador en su proceso, en la ruta rara: cambia, ve que no arranca y deshace");
        check(SelfUpdate.readyVersion(root.toFile()) == null, "con el resultado escrito, ya no hay nada 'preparado'");
        final SelfUpdate.Outcome told = SelfUpdate.afterStart(root.toFile());
        check(told != null && !told.ok() && Files.exists(work.resolve(SelfUpdate.STARTED)),
                "al arrancar se lee como fue (y se avisa de que se ha arrancado)");
        final String report = UpdateReport.build("swap", "1.0.99", null, told == null ? null : told.result(),
                told == null ? null : told.log());
        check(report.contains("result: failed start") && report.contains("updater log")
                && report.contains("deshecho"),
                "el informe lleva el resultado y el registro del cambiador (lo que se pega en Discord)");
        for (int i = 0; i < 60 && Files.exists(work); i++) {
            Thread.sleep(100);
        }
        check(!Files.exists(work), "y .neo-update se limpia");
    }

    private static void awaitSwapper(final Path tmp) throws Exception {
        final Path root = tmp.resolve("await");
        Files.createDirectories(root.resolve(SelfUpdate.WORK_DIR));
        final Process quick = new ProcessBuilder("cmd", "/c", "exit").start();
        long t0 = System.currentTimeMillis();
        check(!SelfUpdate.awaitSwapper(root.toFile(), quick, 10_000) && System.currentTimeMillis() - t0 < 5_000,
                "un cambiador que se muere sin decir nada: el juego no se cierra (y no espera de mas)");
        final Process slow = new ProcessBuilder("cmd", "/c", "ping -n 30 127.0.0.1 > nul").start();
        t0 = System.currentTimeMillis();
        final boolean ok = SelfUpdate.awaitSwapper(root.toFile(), slow, 1_500);
        Thread.sleep(300);
        check(!ok && !slow.isAlive(), "uno que no dice nada a tiempo: se mata y el juego sigue abierto");
    }

    /** Como Archivos de programa: sin permiso para crear carpetas al lado del juego. */
    private static void noWrite(final Path tmp) throws Exception {
        final Path root = fakeInstall(tmp.resolve("ro"), "vieja");
        final String user = System.getProperty("user.name");
        final Process deny = new ProcessBuilder("icacls", root.toString(), "/deny", user + ":(AD)")
                .redirectErrorStream(true).start();
        deny.getInputStream().readAllBytes();
        if (deny.waitFor() != 0) {
            System.out.println("  [info] icacls no ha podido quitar el permiso: no se prueba la carpeta sin escritura");
            return;
        }
        try {
            SelfUpdate.Problem problem = null;
            try {
                SelfUpdate.preflight(root);
            } catch (final SelfUpdate.UpdateException e) {
                problem = e.problem;
            }
            check(problem == SelfUpdate.Problem.NO_WRITE,
                    "carpeta sin permiso de escritura: se sabe ANTES de bajar nada (" + problem + ")");
        } finally {
            final Process undo = new ProcessBuilder("icacls", root.toString(), "/remove:d", user)
                    .redirectErrorStream(true).start();
            undo.getInputStream().readAllBytes();
            undo.waitFor();
        }
        SelfUpdate.preflight(root);
        check(!Files.exists(root.resolve(".neo-update-probe")), "la prueba de escritura no deja nada");
    }

    private static void readOnly(final Path tmp) throws IOException {
        final Path dir = tmp.resolve("solo-lectura");
        Files.createDirectories(dir.resolve("legal"));
        final Path f = dir.resolve("legal/LICENSE");
        Files.writeString(f, "x");
        Files.getFileAttributeView(f, DosFileAttributeView.class).setReadOnly(true);
        SelfUpdate.deleteTree(dir);
        check(!Files.exists(dir), "un fichero de solo lectura no impide limpiar");
    }

    private static void texts() {
        boolean all = true;
        final StringBuilder missing = new StringBuilder();
        for (final SelfUpdate.Problem p : SelfUpdate.Problem.values()) {
            final String t = UpdateFlow.why(p, 1200);
            if (t == null || t.isBlank() || t.startsWith("update.")) {
                all = false;
                missing.append(' ').append(p);
            }
        }
        final String failed = forge.neo.NeoText.get("update.failed", "X.");
        check(all && failed.contains("X.") && !failed.startsWith("update."),
                "cada fallo tiene su frase, y el mensaje dice que no se ha cambiado nada" + missing);
        check(SelfUpdate.classify(new java.net.UnknownHostException("api.github.com")).problem == SelfUpdate.Problem.NETWORK
                && SelfUpdate.classify(new java.nio.file.AccessDeniedException("C:\\Program Files\\x")).problem == SelfUpdate.Problem.NO_WRITE
                && SelfUpdate.classify(new IOException("There is not enough space on the disk")).problem == SelfUpdate.Problem.SPACE
                && SelfUpdate.classify(new java.util.zip.ZipException("bad")).problem == SelfUpdate.Problem.BAD_PACKAGE
                && SelfUpdate.classify(new IllegalStateException("?")).problem == SelfUpdate.Problem.OTHER,
                "cada excepcion cae en su problema (sin red, sin permiso, sin sitio, zip roto, otro)");
    }

    private static void privacy() {
        final String home = System.getProperty("user.home");
        final String user = System.getProperty("user.name");
        final String r = UpdateReport.build("download", "1.0.99",
                new java.nio.file.AccessDeniedException(home + "\\NeoForge\\.neo-update"), null,
                "moviendo " + home + "\\NeoForge\\app de " + user);
        check(!r.toLowerCase().contains(home.toLowerCase())
                && (user.length() < 3 || !r.toLowerCase().contains(user.toLowerCase()))
                && r.contains("%USERPROFILE%") && r.contains("problem: NO_WRITE") && r.contains("windows: "),
                "el informe no lleva el usuario de Windows (va a un canal publico) y si el problema y Windows");
    }

    private static void here() throws IOException {
        check(SelfUpdate.installRoot() == null, "desde el proyecto (java.exe) no se ofrece");
        check(!SelfUpdate.looksInstalled(new File(System.getProperty("user.dir"))), "una carpeta cualquiera no parece la instalacion");
        final Path itch = Files.createTempDirectory("neo-itch");
        try {
            fakeInstall(itch, "vieja");
            final boolean before = SelfUpdate.managedByItchApp(itch.toFile());
            Files.createDirectories(itch.resolve(".itch"));
            check(!before && SelfUpdate.managedByItchApp(itch.toFile()),
                    "instalado con la app de itch.io (.itch): no se ofrece, esa app ya actualiza");
        } finally {
            SelfUpdate.deleteTree(itch);
        }
    }

    private static void online() {
        try {
            final SelfUpdate.Release r = SelfUpdate.fetchRelease();
            System.out.println("  [info] GitHub ahora: " + r.version() + ", " + (r.size() >> 20) + " MB, sha256 "
                    + r.sha256().substring(0, 12) + "…");
        } catch (final SelfUpdate.UpdateException e) {
            System.out.println("  [info] GitHub: " + e.problem + " (" + e.getMessage() + ")");
        }
    }

    // ------------------------------------------------------------------

    /** Una instalacion con una version nueva ya preparada, como la deja {@code prepare}. */
    private static Path staged(final Path root) throws IOException {
        fakeInstall(root, "vieja");
        final Path work = root.resolve(SelfUpdate.WORK_DIR);
        fakeGame(work.resolve("new").resolve("NeoForge"), "nueva");
        Files.writeString(work.resolve(SelfUpdate.READY), "1.0.99");
        Files.createDirectories(work.resolve("swapper/app"));
        Files.writeString(work.resolve("swapper/app/NeoForge.cfg"), "cfg");
        // Un juego ya cerrado.
        Files.writeString(work.resolve(SelfUpdate.JOB), "pid=999999999\n");
        // Lo del paquete viejo, descomprimido en enero; una skin del jugador, de marzo.
        try (Stream<Path> st = Files.walk(root.resolve("res"))) {
            for (final Path f : st.filter(Files::isRegularFile).toList()) {
                created(f, "2026-01-01T10:00:00Z");
            }
        }
        Files.createDirectories(root.resolve("res/skins/mia"));
        Files.writeString(root.resolve("res/skins/mia/skin.txt"), "mi skin");
        created(root.resolve("res/skins/mia/skin.txt"), "2026-03-01T10:00:00Z");
        return root;
    }

    private static void created(final Path f, final String when) throws IOException {
        Files.getFileAttributeView(f, java.nio.file.attribute.BasicFileAttributeView.class)
                .setTimes(null, null, java.nio.file.attribute.FileTime.from(java.time.Instant.parse(when)));
    }

    private static Path fakeInstall(final Path root, final String tag) throws IOException {
        fakeGame(root, tag);
        Files.createDirectories(root.resolve("datos/decks/commander"));
        Files.writeString(root.resolve("datos/decks/commander/mój talia ż.dck"), "[metadata]\nName=mio\n");
        Files.writeString(root.resolve("datos/neo.properties"), "language=pl-PL\n");
        Files.writeString(root.resolve("forge.profile.properties"), "userDir=" + root.resolve("datos") + "\n");
        Files.writeString(root.resolve("suelto-del-jugador.txt"), "mio");
        return root;
    }

    private static Path fakeGame(final Path game, final String tag) throws IOException {
        Files.createDirectories(game.resolve("app"));
        Files.createDirectories(game.resolve("runtime/bin/server"));
        Files.createDirectories(game.resolve("res/cardsfolder"));
        Files.writeString(game.resolve("NeoForge.exe"), tag);
        Files.writeString(game.resolve("app/neo.jar"), tag);
        Files.writeString(game.resolve("app/NeoForge.cfg"), tag);
        Files.writeString(game.resolve("runtime/bin/server/jvm.dll"), tag);
        Files.writeString(game.resolve("res/" + tag + ".txt"), tag);
        Files.writeString(game.resolve("res/comun.txt"), tag);
        Files.writeString(game.resolve("README.txt"), tag);
        return game;
    }

    private static Map<String, String> withoutWork(final Map<String, String> m) {
        m.keySet().removeIf(k -> k.startsWith(SelfUpdate.WORK_DIR));
        return m;
    }

    private static Map<String, String> snapshot(final Path dir) throws IOException {
        final Map<String, String> out = new TreeMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            final List<Path> files = new ArrayList<>();
            s.filter(Files::isRegularFile).forEach(files::add);
            for (final Path f : files) {
                out.put(dir.relativize(f).toString(), SelfUpdate.sha256(f));
            }
        }
        return out;
    }

    private static String read(final Path p) throws IOException {
        return Files.isRegularFile(p) ? Files.readString(p) : null;
    }

    private static void put(final ZipOutputStream z, final String name, final String content) throws IOException {
        z.putNextEntry(new ZipEntry(name));
        z.write(content.getBytes(StandardCharsets.UTF_8));
        z.closeEntry();
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            good++;
        } else {
            bad++;
        }
        System.out.println((ok ? "  [ok]   " : "  [MAL]  ") + what);
    }
}
