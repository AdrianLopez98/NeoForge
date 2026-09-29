package forge.neo.adventure;

import com.badlogic.gdx.Gdx;

/**
 * <b>Solo diagnostico</b> ({@code -Dneo.adventure.trace=true}): apunta en el
 * registro del Adventure cada cambio de escena, pantalla o transicion, con la
 * pila de escenas guardadas ({@code Forge.lastScene}).
 *
 * <p>Sale de la pantalla negra al acabar un match de un evento de la Aventura
 * (draft y Jumpstart, 29-09-2026): el registro del jugador ensenya que, al
 * volver, se vuelve a ENTRAR en el duelo en vez de volver al evento, y el
 * resultado no se apunta. Esto dice que escena hay debajo cuando pasa.
 *
 * <p>Lee campos de Forge que no son publicos por reflexion, y SOLO los lee:
 * no cambia nada del juego. Si Forge los renombra, se apunta y se sigue.
 */
final class SceneTrace {

    private SceneTrace() {
    }

    private static String last = "";

    static void arm() {
        if (!Boolean.getBoolean("neo.adventure.trace")) {
            return;
        }
        final Thread t = new Thread(() -> {
            NeoDuelBridge.log("traza: vigilando escenas cada 300 ms");
            while (true) {
                try {
                    Thread.sleep(300);
                } catch (final InterruptedException e) {
                    return;
                }
                if (Gdx.app == null) {
                    continue;
                }
                Gdx.app.postRunnable(SceneTrace::sample);
            }
        }, "neo-adventure-trace");
        t.setDaemon(true);
        t.start();
    }

    private static void sample() {
        try {
            final StringBuilder s = new StringBuilder();
            s.append("escena=").append(name(forge.Forge.getCurrentScene()));
            s.append(" pantalla=").append(name(forge.Forge.getCurrentScreen()));
            s.append(" transicion=").append(name(read("transitionScreen")));
            final Object stack = read("lastScene");
            s.append(" pila=[");
            if (stack instanceof com.badlogic.gdx.utils.Array<?> arr) {
                for (int i = 0; i < arr.size; i++) {
                    s.append(i == 0 ? "" : ", ").append(name(arr.get(i)));
                }
            } else {
                s.append("?");
            }
            s.append("] encima=[");
            for (final forge.toolbox.FOverlay ov : forge.toolbox.FOverlay.getOverlays()) {
                s.append(ov.getClass().getSimpleName()).append(ov.isVisible() ? " " : "(oculta) ");
            }
            s.append("]");
            final String now = s.toString();
            if (!now.equals(last)) {
                last = now;
                NeoDuelBridge.log("traza: " + now);
            }
        } catch (final Throwable e) {
            NeoDuelBridge.log("traza: no se pudo leer: " + e);
        }
    }

    private static Object read(final String field) {
        try {
            final java.lang.reflect.Field f = forge.Forge.class.getDeclaredField(field);
            f.setAccessible(true);
            return f.get(null);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return "?" + field;
        }
    }

    private static String name(final Object o) {
        return o == null ? "null" : o instanceof String ? (String) o : o.getClass().getSimpleName();
    }

    /** Desde donde se ha llamado: las lineas de pila que importan. */
    static String caller() {
        final StringBuilder s = new StringBuilder();
        int n = 0;
        for (final StackTraceElement e : new Throwable().getStackTrace()) {
            final String c = e.getClassName();
            if (c.startsWith("forge.neo.adventure.SceneTrace") || c.startsWith("java.")) {
                continue;
            }
            s.append("\n      at ").append(e);
            if (++n >= 14) {
                break;
            }
        }
        return s.toString();
    }
}
