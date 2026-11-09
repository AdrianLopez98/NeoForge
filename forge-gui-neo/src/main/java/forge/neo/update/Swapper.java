package forge.neo.update;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * <b>El cambio de version, con el juego cerrado</b> (la mitad de
 * {@link SelfUpdate} que no puede hacer el juego: no se puede reemplazar el
 * programa que esta corriendo).
 *
 * <p>Corre en su propio proceso, con una COPIA del lanzador, del Java y del jar
 * de la version que lo lanza ({@code .neo-update\swapper}, ver
 * {@link SelfUpdate#buildSwapper}): no tiene nada abierto dentro de lo que va a
 * cambiar, y no depende de nada que haga la version nueva salvo escribir
 * {@link SelfUpdate#STARTED}. <b>Solo usa {@code java.*}</b>.
 *
 * <p><b>No recibe rutas por la linea de ordenes</b>: el lanzador de jpackage las
 * pasaria por la pagina de codigos ANSI y una carpeta con una "ż" llegaria rota.
 * Saca la instalacion de donde esta su propio jar ({@link #workFromJar}), que es
 * como el propio juego encuentra el suyo.
 *
 * <p>Lo que hace, en orden:
 * <ol>
 *   <li>Dice que esta vivo ({@link SelfUpdate#ALIVE}): hasta entonces el juego
 *       no se cierra.</li>
 *   <li>Espera a que se cierre el juego y cualquier proceso que corra desde la
 *       instalacion (la Aventura va en el suyo). Si en 90 s no se han cerrado,
 *       <b>no toca nada y no abre nada</b> (el juego sigue abierto).</li>
 *   <li><b>Renombra, no copia</b>: cada cosa de arriba de la version nueva
 *       aparta la vieja a {@code .neo-update\old} y pone la nueva (el
 *       {@code .exe} el ultimo). Si algo falla, <b>deshace en orden inverso</b>.</li>
 *   <li>Abre la nueva y <b>espera a que diga que ha arrancado</b>. Si muere sin
 *       decirlo (un antivirus que se ha comido una DLL, lo que sea), deshace el
 *       cambio y abre la de antes.</li>
 * </ol>
 *
 * <p>{@code datos\} y {@code forge.profile.properties} no se tocan en ningun
 * camino ({@link #PROTECTED}).
 */
public final class Swapper {

    private Swapper() {
    }

    /** Lo que nunca se aparta ni se pone, venga en el zip o no. */
    static final List<String> PROTECTED = List.of("datos", "forge.profile.properties", SelfUpdate.WORK_DIR,
            ".neo-update-probe", ".itch");

    /** Cuanto se espera a que se cierre el juego. */
    static final long WAIT_CLOSE_MS = 90_000;
    /**
     * Cuanto se espera, como mucho, a que la version nueva diga que ha
     * arrancado MIENTRAS siga viva. Largo a proposito: si su lanzador no puede
     * arrancar Java, jpackage ensenya una ventana de error y el proceso sigue
     * vivo hasta que el jugador pulsa Aceptar. Con un tope corto se daria por
     * buena una version rota; asi, en cuanto se cierra esa ventana, se vuelve a
     * la de antes.
     */
    static final long WAIT_START_MS = 30 * 60_000L;
    /** Cuanto tiene que llevar muerta la version nueva, sin haber dicho nada, para deshacer. */
    static final long DEAD_MS = 8_000;

    /** Reintentos de cada renombrado: el antivirus a veces sujeta un fichero recien cerrado. */
    static final int RETRIES = 40;
    static final long RETRY_MS = 250;

    /** Lo hecho, para poder deshacerlo. */
    record Move(Path from, Path to) {
    }

    interface Log {
        void say(String s);
    }

    /**
     * El de verdad no recibe nada: la instalacion sale de donde esta el jar.
     * Las pruebas la pasan en {@code NEO_SWAPPER_ROOT} (el entorno llega en
     * Unicode; un argumento no: lo demostro la propia prueba, con una ż).
     */
    public static void main(final String[] args) {
        final String testRoot = System.getenv("NEO_SWAPPER_ROOT");
        final Path work = testRoot != null ? Path.of(testRoot).resolve(SelfUpdate.WORK_DIR) : workFromJar();
        if (work == null) {
            return;
        }
        run(work, WAIT_CLOSE_MS, WAIT_START_MS);
    }

    /**
     * {@code <instalacion>\.neo-update\swapper\app\neo.jar} → {@code .neo-update}.
     * Si no esta donde tiene que estar, {@code null}: no se toca nada.
     */
    static Path workFromJar() {
        try {
            final Path jar = Path.of(Swapper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            final Path work = jar.getParent().getParent().getParent();
            final Path root = work.getParent();
            if (work.getFileName().toString().equals(SelfUpdate.WORK_DIR)
                    && Files.isRegularFile(root.resolve("NeoForge.exe"))) {
                return work;
            }
        } catch (final Exception e) {
            // nada
        }
        return null;
    }

    /** El cambio entero. Devuelve la linea de resultado (tambien la deja escrita). */
    static String run(final Path work, final long waitCloseMs, final long waitStartMs) {
        final Path root = work.getParent();
        final Path fresh = work.resolve("new").resolve("NeoForge");
        String result;
        // Que abrir al final depende de hasta donde se llego:
        //  - el juego aun no se ha cerrado (o ni se sabe): NADA, sigue abierto;
        //  - ya se abrio la version nueva y arranco: nada mas;
        //  - en cualquier otro caso, ya cerrado: el que haya en la carpeta.
        boolean closed = false;
        boolean newOpened = false;
        try (PrintWriter log = new PrintWriter(Files.newBufferedWriter(work.resolve("swapper.log"),
                StandardCharsets.UTF_8))) {
            final Log l = s -> {
                log.println(LocalDateTime.now() + "  " + s);
                log.flush();
            };
            try {
                Files.writeString(work.resolve(SelfUpdate.ALIVE), "ok", StandardCharsets.US_ASCII);
                l.say("instalacion: " + root);
                if (!waitClosed(readPid(work), root, work, waitCloseMs, l)) {
                    result = "failed\tbusy";
                } else {
                    closed = true;
                    final List<Move> done = new ArrayList<>();
                    result = swapOrRollBack(root, fresh, work.resolve("old"), done, l);
                    if (result.startsWith("ok")) {
                        write(work, result);
                        final Process p = relaunch(root, l);
                        newOpened = true;
                        if (!waitStarted(work, root, p, waitStartMs, l)) {
                            l.say("la version nueva no ha arrancado: se vuelve a la de antes");
                            final String undo = rollBack(done, l);
                            result = "failed\tstart" + (undo == null ? "" : " (rollback: " + undo + ")");
                            newOpened = false;
                        }
                    }
                }
            } catch (final Throwable e) {
                l.say("FALLO inesperado: " + e);
                result = "failed\t" + e;
            }
            l.say("resultado: " + result);
        } catch (final Throwable e) {
            // Ni el registro se ha podido abrir: no se ha dicho "vivo", asi que el
            // juego sigue abierto y lo dira el. Aqui no se abre nada.
            result = "failed\t" + e;
        }
        write(work, result);
        if (closed && !newOpened) {
            relaunch(root, s -> { });
        }
        return result;
    }

    private static void write(final Path work, final String result) {
        try {
            Files.writeString(work.resolve(SelfUpdate.RESULT), result, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            // Sin donde apuntarlo: el juego se abrira igual y no dira nada.
        }
    }

    private static long readPid(final Path work) {
        try {
            for (final String line : Files.readAllLines(work.resolve(SelfUpdate.JOB), StandardCharsets.US_ASCII)) {
                if (line.startsWith("pid=")) {
                    return Long.parseLong(line.substring(4).trim());
                }
            }
        } catch (final IOException | RuntimeException e) {
            // sin pid: se mira solo por la carpeta
        }
        return -1;
    }

    /** ¿Corre algo desde la instalacion (fuera de {@code skip})? */
    static boolean anyRunning(final Path root, final Path skip) {
        final String prefix = SelfUpdate.lower(root) + File.separator;
        final String mine = SelfUpdate.lower(skip) + File.separator;
        final long me = ProcessHandle.current().pid();
        return ProcessHandle.allProcesses().anyMatch(p -> {
            if (p.pid() == me) {
                return false;
            }
            final Optional<String> cmd = p.info().command();
            if (cmd.isEmpty()) {
                return false;
            }
            final String c = cmd.get().toLowerCase(Locale.ROOT);
            return c.startsWith(prefix) && !c.startsWith(mine);
        });
    }

    /**
     * Espera a que no quede el juego ni nada que corra desde la instalacion. Lo
     * de {@code skip} (el propio cambiador) no cuenta. Devuelve si se cerraron.
     */
    static boolean waitClosed(final long pid, final Path root, final Path skip, final long maxMs, final Log l) {
        final long end = System.currentTimeMillis() + maxMs;
        while (System.currentTimeMillis() < end) {
            final boolean game = pid > 0 && ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
            if (!game && !anyRunning(root, skip)) {
                l.say("el juego esta cerrado");
                return true;
            }
            sleep(300);
        }
        l.say("el juego no se ha cerrado en " + maxMs + " ms");
        return false;
    }

    /**
     * Espera a que la version nueva diga que ha arrancado. Devuelve false solo
     * si se ha muerto sin decirlo (lleva {@link #DEAD_MS} sin nada corriendo
     * desde la instalacion: tambien cuando el jugador cierra la ventana de
     * error del lanzador). Si sigue viva y callada al acabar el tiempo, se da
     * por buena: esta abierta, y quitarsela de debajo seria peor.
     */
    static boolean waitStarted(final Path work, final Path root, final Process p, final long maxMs, final Log l) {
        final Path started = work.resolve(SelfUpdate.STARTED);
        final long t0 = System.currentTimeMillis();
        long deadSince = -1;
        while (System.currentTimeMillis() - t0 < maxMs) {
            if (Files.exists(started)) {
                l.say("la version nueva ha arrancado");
                return true;
            }
            final boolean alive = (p != null && p.isAlive()) || anyRunning(root, work);
            if (alive) {
                deadSince = -1;
            } else if (deadSince < 0) {
                deadSince = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - deadSince >= DEAD_MS) {
                return Files.exists(started);
            }
            sleep(500);
        }
        l.say("la version nueva sigue abierta sin avisar: se da por buena");
        return true;
    }

    /**
     * Cambia la version o la deja como estaba. Devuelve la linea de resultado:
     * {@code ok\t...} o {@code failed\t<por que>}. En {@code done} queda lo hecho
     * (para deshacerlo despues si la nueva no arranca).
     */
    static String swapOrRollBack(final Path root, final Path fresh, final Path old, final List<Move> done,
                                 final Log l) {
        try {
            Files.createDirectories(old);
            final List<String> names = new ArrayList<>();
            try (var s = Files.list(fresh)) {
                s.forEach(p -> names.add(p.getFileName().toString()));
            }
            names.removeIf(n -> PROTECTED.stream().anyMatch(n::equalsIgnoreCase));
            if (names.stream().noneMatch(n -> n.equalsIgnoreCase("NeoForge.exe"))) {
                return "failed\tswap: the new version has no NeoForge.exe";
            }
            // El .exe el ultimo: si algo falla a medias, que lo ultimo en cambiar
            // sea lo que se abre.
            names.sort((a, b) -> Boolean.compare(a.equalsIgnoreCase("NeoForge.exe"), b.equalsIgnoreCase("NeoForge.exe")));
            for (final String n : names) {
                final Path current = root.resolve(n);
                if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                    move(current, old.resolve(n), done, l);
                }
                move(fresh.resolve(n), current, done, l);
            }
            // Lo que el jugador puso en res (una skin, un mod de la Aventura,
            // cartas o ediciones propias) se va con el a la version nueva. Si
            // no se puede llevar, se deshace TODO: nunca se pierde.
            final int carried = carryUserFiles(old.resolve("res"), root.resolve("res"), done, l);
            return "ok\t" + names.size() + " items, " + carried + " own files kept";
        } catch (final Exception e) {
            l.say("FALLO: " + e + " — deshaciendo " + done.size() + " pasos");
            final String undo = rollBack(done, l);
            done.clear();
            return "failed\tswap: " + e + (undo == null ? "" : " (rollback: " + undo + ")");
        }
    }

    /**
     * La lista de lo que trajo el paquete en {@code res}, que deja cada
     * actualizacion en la version nueva (rutas relativas a {@code res}, con
     * {@code /}). CONTRATO entre versiones: no renombrar.
     */
    public static final String MANIFEST = ".neo-shipped.txt";

    /**
     * Sin lista: cuanto despues de la descompresion del paquete tiene que
     * haberse creado un fichero para ser "del jugador". Holgado: lo que Forge
     * quito entre versiones se descomprimio en esa misma tanda.
     */
    static final long CARRY_MARGIN_MS = 10 * 60_000L;

    /**
     * <b>Lo tuyo de {@code res}, a la version nueva</b> (Ana, 09-11-2026: <i>"que
     * no se les pise lo suyo custom de res"</i>). Lo dificil es distinguir lo
     * que anyadio el jugador de lo que Forge quito entre versiones — devolver un
     * fichero viejo de Forge podria dejar datos rancios en la nueva:
     *
     * <ul>
     *   <li><b>Con lista</b> ({@link #MANIFEST}): es del jugador todo lo que no
     *       esta en ella. La lista va DENTRO del zip que se publica desde la
     *       1.0.50 ({@code tools/replicar-recursos.ps1}) y cada actualizacion
     *       deja la de la version nueva, asi que es el caso normal.</li>
     *   <li><b>Sin lista</b> (red de seguridad: una copia sin ella): por la fecha
     *       de CREACION. Lo del zip se creo al descomprimirlo; lo del jugador,
     *       despues. La referencia es el percentil 99 de las fechas de lo que
     *       tambien trae la version nueva, no el maximo: un fichero del jugador
     *       que por casualidad se llame como uno oficial nuevo no puede moverla.
     *       Es suyo lo creado mas de {@link #CARRY_MARGIN_MS} despues.</li>
     * </ul>
     *
     * Solo lo que la version nueva NO trae: si trae un fichero en esa misma
     * ruta, manda el oficial. Se MUEVE (mismo disco: instantaneo) y queda en
     * {@code done}, asi que si la nueva no arranca vuelve con la vieja. Si algo
     * no se puede mover, lanza: el cambio entero se deshace.
     *
     * @return cuantos ficheros se han llevado
     */
    static int carryUserFiles(final Path oldRes, final Path newRes, final List<Move> done, final Log l)
            throws IOException {
        if (!Files.isDirectory(oldRes, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(newRes)) {
            return 0;
        }
        final java.util.Set<String> shipped = readManifest(oldRes.resolve(MANIFEST));
        final List<Path> extras = new ArrayList<>();
        final java.util.Map<Path, Long> created = new java.util.HashMap<>();
        final List<Long> shippedTimes = new ArrayList<>();
        Files.walkFileTree(oldRes, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(final Path file,
                                                           final java.nio.file.attribute.BasicFileAttributes a) {
                final String rel = oldRes.relativize(file).toString().replace('\\', '/');
                if (rel.equalsIgnoreCase(MANIFEST)) {
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
                final long c = a.creationTime().toMillis();
                if (Files.exists(newRes.resolve(rel), LinkOption.NOFOLLOW_LINKS)) {
                    // La nueva lo trae: no se lleva, y dice cuando se descomprimio el paquete.
                    shippedTimes.add(c);
                } else if (shipped != null) {
                    if (!shipped.contains(rel.toLowerCase(Locale.ROOT))) {
                        extras.add(file);
                    }
                } else {
                    extras.add(file);
                    created.put(file, c);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        if (shipped == null) {
            // Sin nada en comun no hay referencia: no se adivina, no se lleva nada.
            final long cut;
            if (shippedTimes.isEmpty()) {
                cut = Long.MAX_VALUE;
            } else {
                java.util.Collections.sort(shippedTimes);
                final int i = Math.max(0, (int) Math.ceil(shippedTimes.size() * 0.99) - 1);
                cut = shippedTimes.get(i) + CARRY_MARGIN_MS;
            }
            extras.removeIf(f -> created.get(f) <= cut);
        }
        int n = 0;
        for (final Path f : extras) {
            final Path target = newRes.resolve(oldRes.relativize(f).toString());
            Files.createDirectories(target.getParent());
            retryMove(f, target);
            done.add(new Move(f, target));
            if (n < 20) {
                l.say("tuyo, a la nueva: res/" + oldRes.relativize(f));
            }
            n++;
        }
        l.say("ficheros tuyos de res llevados a la nueva: " + n + (shipped != null ? " (por la lista)" : " (por la fecha)"));
        return n;
    }

    /** La lista de lo que trajo el paquete, en minusculas; null si no hay o no se lee. */
    static java.util.Set<String> readManifest(final Path manifest) {
        try {
            if (!Files.isRegularFile(manifest)) {
                return null;
            }
            final java.util.Set<String> out = new java.util.HashSet<>();
            for (final String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                // Por si alguien la escribe con BOM (PowerShell 5.1 lo pone de serie).
                final String s = line.replace("\uFEFF", "").trim();
                if (!s.isEmpty()) {
                    out.add(s.replace('\\', '/').toLowerCase(Locale.ROOT));
                }
            }
            return out.isEmpty() ? null : out;
        } catch (final IOException | RuntimeException e) {
            return null;
        }
    }

    /** Deshace en orden inverso. Devuelve null si todo volvio a su sitio. */
    static String rollBack(final List<Move> done, final Log l) {
        String problem = null;
        for (int i = done.size() - 1; i >= 0; i--) {
            final Move m = done.get(i);
            try {
                retryMove(m.to(), m.from());
                l.say("deshecho: " + m.to() + " -> " + m.from());
            } catch (final IOException e) {
                problem = String.valueOf(e);
                l.say("NO SE PUDO DESHACER: " + m + ": " + e);
            }
        }
        return problem;
    }

    private static void move(final Path from, final Path to, final List<Move> done, final Log l) throws IOException {
        retryMove(from, to);
        done.add(new Move(from, to));
        l.say(from + " -> " + to);
    }

    /** Un renombrado, con reintentos si algo lo sujeta un momento. */
    static void retryMove(final Path from, final Path to) throws IOException {
        // ATOMIC_MOVE en Windows PISA un destino que ya exista (MoveFileEx con
        // REPLACE_EXISTING): nunca se mueve encima de nada. Lo cazo la prueba
        // de la marcha atras (selfupdatecheck).
        if (Files.exists(to, LinkOption.NOFOLLOW_LINKS)) {
            throw new java.nio.file.FileAlreadyExistsException(to.toString());
        }
        IOException last = null;
        for (int i = 0; i < RETRIES; i++) {
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (final java.nio.file.NoSuchFileException e) {
                throw e; // no hay nada que mover: reintentar no lo arregla
            } catch (final IOException e) {
                last = e;
                sleep(RETRY_MS);
            }
        }
        throw last;
    }

    private static Process relaunch(final Path root, final Log l) {
        final File exe = root.resolve("NeoForge.exe").toFile();
        if (!exe.isFile()) {
            l.say("no hay NeoForge.exe que abrir");
            return null;
        }
        try {
            // ⚠️ La salida del juego, A NINGUN SITIO. Por defecto ProcessBuilder la
            // conecta a una tuberia que tendria que leer este proceso; nadie la lee,
            // el juego escribe mucho al arrancar (los avisos de traducciones de
            // Forge), la tuberia se llena y el juego SE QUEDA BLOQUEADO escribiendo
            // — mientras aqui se espera a que diga que ha arrancado. Lo cazo
            // tools/probar-actualizador.py con el .exe de verdad (09-11-2026).
            final ProcessBuilder pb = new ProcessBuilder(exe.getAbsolutePath()).directory(root.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.environment().remove("_JPACKAGE_LAUNCHER");
            final Process p = pb.start();
            try {
                p.getOutputStream().close();
            } catch (final IOException ignored) {
                // su entrada no hace falta
            }
            l.say("abierto " + exe);
            return p;
        } catch (final IOException e) {
            l.say("no se ha podido abrir " + exe + ": " + e);
            return null;
        }
    }

    private static void sleep(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
