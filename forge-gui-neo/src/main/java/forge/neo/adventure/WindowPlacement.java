package forge.neo.adventure;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

/**
 * que el Adventure salga EN LA PANTALLA DE NEOFORGE y no en una
 * ventanita suya.
 *
 * <p>El menu de NeoForge pasa donde esta su ventana ({@code neo.adventure.bounds},
 * en pixeles de pantalla) y aqui se coloca la del Adventure exactamente encima,
 * con el mismo tamanyo, el titulo "NeoForge" y nuestro icono. NeoForge se queda
 * detras: al cerrar el Adventure sigue ahi, sin haberse minimizado.
 *
 * <p>Cuando la ventana ya esta en su sitio escribe {@link #READY} en la salida:
 * es lo que espera el menu para dejar de decir "abriendo".
 */
final class WindowPlacement {

    static final String READY = "[aventura] ventana del Adventure en su sitio";

    private WindowPlacement() {
    }

    /** Los argumentos width=/height= que entiende el lanzador del Adventure. */
    static String[] launcherArgs(final String[] args) {
        final int[] b = bounds();
        if (b == null) {
            return args;
        }
        final String[] out = new String[args.length + 2];
        System.arraycopy(args, 0, out, 0, args.length);
        out[args.length] = "width=" + b[2];
        out[args.length + 1] = "height=" + b[3];
        return out;
    }

    /** Se ejecuta en el primer fotograma del bucle de libGDX. */
    static void arm() {
        final Thread t = new Thread(() -> {
            while (Gdx.app == null) {
                try {
                    Thread.sleep(20);
                } catch (final InterruptedException e) {
                    return;
                }
            }
            Gdx.app.postRunnable(WindowPlacement::place);
        }, "neo-adventure-window");
        t.setDaemon(true);
        t.start();
    }

    private static void place() {
        try {
            if (!(Gdx.graphics instanceof Lwjgl3Graphics)) {
                return;
            }
            final Lwjgl3Graphics g = (Lwjgl3Graphics) Gdx.graphics;
            final Lwjgl3Window window = g.getWindow();
            g.setTitle("NeoForge");
            setIcon(window);
            final int[] b = bounds();
            if (b != null) {
                g.setResizable(true);
                g.setWindowedMode(b[2], b[3]);
                window.setPosition(b[0], b[1]);
                if (Boolean.getBoolean("neo.adventure.maximized")) {
                    window.maximizeWindow();
                }
            }
            // Oculta mientras carga: la portada de Forge no se ensenya. Se
            // muestra cuando el Adventure ya esta en su primera pantalla.
            window.setVisible(false);
        } catch (final Throwable e) {
            NeoDuelBridge.log("no se pudo colocar la ventana: " + e);
        }
        final long t0 = System.currentTimeMillis();
        waitForAdventure(t0);
    }

    /** Cada fotograma, hasta que el Adventure tenga escena; entonces se ensenya. */
    private static void waitForAdventure(final long t0) {
        final boolean ready = forge.Forge.getCurrentScene() != null;
        if (!ready && System.currentTimeMillis() - t0 < 120_000) {
            Gdx.app.postRunnable(() -> waitForAdventure(t0));
            return;
        }
        try {
            final Lwjgl3Window window = ((Lwjgl3Graphics) Gdx.graphics).getWindow();
            window.setVisible(true);
            window.focusWindow();
        } catch (final Throwable e) {
            NeoDuelBridge.log("no se pudo ensenyar la ventana: " + e);
        }
        System.out.println(READY);
    }

    private static void setIcon(final Lwjgl3Window window) {
        try {
            final Pixmap[] icons = new Pixmap[3];
            final String[] sizes = {"16", "32", "64"};
            for (int i = 0; i < sizes.length; i++) {
                final FileHandle f = Gdx.files.classpath("forge/neo/logo/logo-" + sizes[i] + ".png");
                icons[i] = new Pixmap(f);
            }
            window.setIcon(icons);
            for (final Pixmap p : icons) {
                p.dispose();
            }
        } catch (final Throwable e) {
            NeoDuelBridge.log("sin icono de NeoForge: " + e);
        }
    }

    /** x, y, ancho, alto en pixeles de pantalla, o null si no se paso. */
    private static int[] bounds() {
        final String s = System.getProperty("neo.adventure.bounds");
        if (s == null) {
            return null;
        }
        try {
            final String[] p = s.split(",");
            return new int[] {Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), Integer.parseInt(p[3])};
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
