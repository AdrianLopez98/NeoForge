package forge.neo.adventure;

import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoText;
import forge.neo.ui.LoadingScreen;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Abre la Aventura (el Adventure de Forge) desde el menu de NeoForge.
 *
 * <h2>En su propio proceso</h2>
 *
 * <p>El Adventure es otra aplicacion entera: su motor de mapa en libGDX, su
 * propia plataforma ({@code GuiMobile}) y su propia carga del motor. Meterlo en
 * este proceso obligaria a compartir un solo {@code GuiBase} entre dos
 * aplicaciones, asi que se lanza aparte con el MISMO Java y el MISMO classpath
 * que este (sirve igual en desarrollo, en el {@code .exe} y en la portable). Sus
 * combates y su editor de mazos son los nuestros: ver {@link AdventureNeoMain}.
 *
 * <h2>Una sola ventana a la vista</h2>
 *
 * <p>Mientras carga se ve nuestra pantalla de carga (nada de la portada de
 * Forge ni su selector de modo). Cuando el Adventure avisa de que su ventana ya
 * esta en su sitio -encima de esta, mismo tamanyo- esta se <b>oculta</b>, no se
 * minimiza: una sola entrada en la barra de tareas. Al cerrar el Adventure
 * vuelve a aparecer, en el menu.
 *
 * <h2>Donde guarda sus datos</h2>
 *
 * <p>El Adventure reescribe {@code forge.preferences} al arrancar (idioma,
 * selector de modo, musica). Sin {@code forge.profile.properties} -desarrollo y
 * la portable de D:- la carpeta de Forge se decide por {@code APPDATA}, y se le
 * da una propia: {@code <datos de Forge>/neo/adventure}. En desarrollo eso
 * importa de verdad, porque {@code %APPDATA%\Forge} se comparte con el Forge
 * antiguo y le apagaria la musica. Con perfil -el {@code .exe}- el perfil manda
 * sobre {@code APPDATA} y comparte {@code datos\} con NeoForge, lo que no rompe
 * nada: NeoForge vuelve a poner su idioma y su musica en cada arranque.
 * {@code LOCALAPPDATA} no se toca: ahi solo hay caches, y compartir la de
 * imagenes evita volver a bajarlas (y el parpadeo del panel de detalle que
 * causaban las descargas mientras se jugaba).
 */
public final class AdventureLauncher {

    private AdventureLauncher() {
    }

    private static volatile Process running;

    /** Si hay una Aventura abierta ahora mismo. */
    public static boolean isRunning() {
        final Process p = running;
        return p != null && p.isAlive();
    }

    /**
     * Desde el hilo de JavaFX, al pulsar la casilla.
     *
     * @param stage    la ventana de NeoForge
     * @param showMenu vuelve a poner el menu (tras la carga, o si algo falla)
     */
    public static void open(final Stage stage, final Runnable showMenu) {
        if (isRunning() || stage == null || stage.getScene() == null) {
            return;
        }
        final Scene scene = stage.getScene();
        try {
            final Launch launch = command(stage);
            final ProcessBuilder pb = new ProcessBuilder(launch.cmd);
            if (!launch.props.isEmpty()) {
                pb.environment().put(AdventureNeoMain.PROPS_ENV, String.join("\n", launch.props));
            }
            pb.directory(new File(ForgeConstants.ASSETS_DIR).getCanonicalFile());
            pb.redirectErrorStream(true);

            final boolean profile = new File(ForgeConstants.PROFILE_FILE).isFile();
            final File prefsDir;
            if (profile) {
                prefsDir = new File(ForgeConstants.USER_PREFS_DIR);
            } else {
                final File root = new File(ForgeConstants.USER_DIR, "neo" + File.separator + "adventure");
                root.mkdirs();
                pb.environment().put("APPDATA", root.getPath());
                prefsDir = new File(root, "Forge" + File.separator + "preferences");
            }
            AdventureSettings.prepare(prefsDir, !profile);

            final LoadingScreen loading = new LoadingScreen();
            loading.setDescription(NeoText.get("adventure.opening"));
            scene.setRoot(loading);

            final Process proc = pb.start();
            running = proc;
            final File log = new File(ForgeConstants.USER_DIR, "neo" + File.separator + "adventure.log");
            log.getParentFile().mkdirs();
            final Thread reader = new Thread(() -> readOutput(proc, log, stage, showMenu), "neo-adventure-log");
            reader.setDaemon(true);
            reader.start();

            proc.onExit().thenRun(() -> Platform.runLater(() -> {
                if (scene.getRoot() == loading) {
                    // Se cerro (o fallo) antes de ensenyarse.
                    showMenu.run();
                }
                if (!stage.isShowing()) {
                    stage.show();
                }
                stage.toFront();
                stage.requestFocus();
                Platform.setImplicitExit(true);
                // El menu cambia la nota de la casilla segun si esta abierta.
                showMenu.run();
            }));
        } catch (final Exception e) {
            System.err.println("[aventura] no se pudo abrir: " + e);
            e.printStackTrace();
            showMenu.run();
        }
    }

    /** La salida del Adventure va al registro; y se espera a que este listo. */
    private static void readOutput(final Process proc, final File log, final Stage stage,
                                   final Runnable showMenu) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(proc.getInputStream(),
                StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(log),
                     StandardCharsets.UTF_8), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                out.println(line);
                if (line.contains(WindowPlacement.READY)) {
                    Platform.runLater(() -> {
                        if (!proc.isAlive()) {
                            return;
                        }
                        // Ocultar la unica ventana cerraria JavaFX: por eso el
                        // implicitExit, que se repone al volver.
                        Platform.setImplicitExit(false);
                        stage.hide();
                        showMenu.run();
                    });
                }
            }
        } catch (final java.io.IOException ignored) {
            // el proceso se cerro
        }
    }

    /** Como se lanza: con un java de verdad, o con el propio .exe. */
    private static final class Launch {
        final List<String> cmd = new ArrayList<>();
        final List<String> props = new ArrayList<>();
    }

    private static Launch command(final Stage stage) {
        final Launch l = new Launch();
        try {
            l.props.add("forge.assetsDir=" + new File(ForgeConstants.ASSETS_DIR).getCanonicalPath() + File.separator);
        } catch (final java.io.IOException e) {
            l.props.add("forge.assetsDir=" + ForgeConstants.ASSETS_DIR);
        }
        // Donde esta la ventana de NeoForge: el Adventure sale ahi encima, con
        // el mismo tamanyo (ver WindowPlacement).
        final Scene sc = stage.getScene();
        final double sx = stage.getOutputScaleX();
        final double sy = stage.getOutputScaleY();
        l.props.add("neo.adventure.bounds=" + Math.round((stage.getX() + sc.getX()) * sx) + ","
                + Math.round((stage.getY() + sc.getY()) * sy) + ","
                + Math.round(sc.getWidth() * sx) + "," + Math.round(sc.getHeight() * sy));
        l.props.add("neo.adventure.maximized=" + stage.isMaximized());
        // Lo que hace portable a la copia de D: (user.home, temporales y cache
        // de JavaFX dentro del disco) tiene que valer tambien aqui: si no,
        // libGDX y JavaFX escribirian en C: del ordenador donde se enchufe.
        // Y solo pruebas: las banderas de autoprueba.
        for (final String k : new String[] {"user.home", "java.io.tmpdir", "javafx.cachedir",
                "neo.adventure.selftest", "neo.adventure.auto", "neo.adventure.snapshot",
                "neo.adventure.snapshotMs", "neo.adventure.pressEsc"}) {
            if (System.getProperty(k) != null) {
                l.props.add(k + "=" + System.getProperty(k));
            }
        }

        final File bin = new File(System.getProperty("java.home"), "bin");
        final File javaw = new File(bin, "javaw.exe");
        final File java = new File(bin, "java");
        final File javaExe = new File(bin, "java.exe");
        final String app = System.getProperty("jpackage.app-path");
        if (!javaw.isFile() && !java.isFile() && !javaExe.isFile() && app != null) {
            // El .exe de jpackage: su Java no trae ejecutable. Se relanza el
            // propio NeoForge.exe con "adventure" (lo atiende NeoMain), con las
            // opciones del .cfg, y lo nuestro va por entorno.
            l.cmd.add(app);
            l.cmd.add("adventure");
            return l;
        }
        l.cmd.add(javaw.isFile() ? javaw.getPath() : javaExe.isFile() ? javaExe.getPath() : java.getPath());
        l.cmd.add("-Xmx4g");
        for (final String open : new String[] {"java.desktop/java.beans", "java.desktop/javax.swing.border",
                "java.desktop/javax.swing.event", "java.desktop/sun.swing", "java.desktop/java.awt.image",
                "java.desktop/java.awt.color", "java.desktop/sun.awt.image", "java.desktop/javax.swing",
                "java.desktop/java.awt", "java.base/java.util", "java.base/java.lang",
                "java.base/java.lang.reflect", "java.base/java.text", "java.desktop/java.awt.font",
                "java.base/jdk.internal.misc", "java.base/sun.nio.ch", "java.base/java.nio",
                "java.base/java.math", "java.base/java.util.concurrent", "java.base/java.net"}) {
            l.cmd.add("--add-opens");
            l.cmd.add(open + "=ALL-UNNAMED");
        }
        l.cmd.add("-Dio.netty.tryReflectionSetAccessible=true");
        l.cmd.add("-Dfile.encoding=UTF-8");
        for (final String p : l.props) {
            l.cmd.add("-D" + p);
        }
        // El idioma forzado para grabar (-Dneo.language) tambien vale dentro:
        // la Aventura lee NeoLanguage en su propio proceso.
        final String forcedLanguage = System.getProperty("neo.language");
        if (forcedLanguage != null) {
            l.cmd.add("-Dneo.language=" + forcedLanguage);
        }
        l.cmd.add("-cp");
        l.cmd.add(absoluteClasspath());
        l.cmd.add(AdventureNeoMain.class.getName());
        l.props.clear();
        return l;
    }

    /**
     * El classpath de este proceso, con rutas ABSOLUTAS.
     *
     * <p>{@code jugar.cmd} arranca con {@code target\lib\*} relativo a
     * {@code forge-gui-neo}, y el proceso del Adventure arranca en la carpeta de
     * los recursos: con las rutas tal cual no encontraria ni una clase.
     */
    private static String absoluteClasspath() {
        final StringBuilder sb = new StringBuilder();
        for (final String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(File.pathSeparatorChar);
            }
            sb.append(new File(entry).getAbsolutePath());
        }
        return sb.toString();
    }

    private static boolean autoLaunched;

    /** Solo pruebas: {@code -Dneo.adventure.autoLaunch=true} la abre sola al ver el menu, una vez. */
    public static boolean takeAutoLaunch() {
        if (autoLaunched || !Boolean.getBoolean("neo.adventure.autoLaunch")) {
            return false;
        }
        autoLaunched = true;
        return true;
    }
}
