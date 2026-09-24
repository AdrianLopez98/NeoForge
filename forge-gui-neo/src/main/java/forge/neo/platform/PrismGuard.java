package forge.neo.platform;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * La red contra la PANTALLA EN BLANCO de JavaFX: la ventana sigue viva (musica,
 * el cursor cambia al pasar por encima) pero no se pinta nada, para siempre.
 *
 * <p>Reportado en itch.io el 23-09-2026, en la Aventura, "cada 3-5 duelos". El
 * registro traia la cadena entera, y es un fallo de JavaFX (21.0.5), no nuestro:
 *
 * <ol>
 * <li>JavaFX se queda sin textura para una sombra ({@code DropShadow}):
 *     {@code Texture must be non-null}. Es su presupuesto de memoria de video
 *     ({@code prism.maxvram}, 512 MB de fabrica), no la tarjeta.</li>
 * <li>Al fotograma siguiente la sombra revienta A MEDIAS
 *     ({@code Operation requires resource lock}): {@code PPSOneSamplerPeer.filterImpl}
 *     ya habia puesto su shader con {@code setExternalShader} y el que lo quita
 *     esta al final, sin {@code finally}.</li>
 * <li>Ese shader se queda enganchado en el contexto para siempre, y todo lo que
 *     se pinta despues lo usa: {@code Register not found for: idim} en cada
 *     rectangulo, en cada fotograma. De ahi no sale solo.</li>
 * </ol>
 *
 * <p>Dos cosas, y las dos hacen falta:
 *
 * <ul>
 * <li>{@link #beforeJavaFx}: mas presupuesto (1 GB), para que el paso 1 no llegue.
 *     Si alguien ya lo ha puesto con {@code -Dprism.maxvram}, manda el suyo.</li>
 * <li>{@link #install}: si aun asi llega, dos veces por segundo se mira EN EL HILO
 *     DE RENDER, entre fotograma y fotograma, si queda un shader de efecto puesto.
 *     Ahi no puede haber ninguno (cada efecto lo quita al terminar), asi que si lo
 *     hay es el enganchado: se quita y la ventana vuelve a pintarse.</li>
 * </ul>
 *
 * <p>Todo por reflexion y todo opcional: JavaFX va por el classpath (modulo sin
 * nombre), asi que sus clases internas se pueden tocar. Si en otra version o en
 * otro sistema no estan donde se esperan, el vigilante lo dice una vez y se
 * aparta; nunca puede impedir jugar.
 */
public final class PrismGuard {

    private PrismGuard() {
    }

    /** Presupuesto de video de JavaFX si nadie ha dicho otro. De fabrica: 512 MB. */
    static final String MAX_VRAM = "1g";

    private static ScheduledExecutorService timer;
    private static volatile boolean broken;
    private static volatile int repairs;

    /** Antes de arrancar JavaFX: lo lee una vez, al cargar {@code PrismSettings}. */
    public static void beforeJavaFx() {
        if (System.getProperty("prism.maxvram") == null) {
            System.setProperty("prism.maxvram", MAX_VRAM);
        }
    }

    /**
     * Con JavaFX ya arrancado: pone el vigilante. {@code log} recibe una linea
     * cada vez que tiene que arreglar algo (y una si no puede vigilar).
     */
    public static synchronized void install(final Consumer<String> log) {
        if (timer != null || "false".equals(System.getProperty("neo.prism.guard"))) {
            return;
        }
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            final Thread t = new Thread(r, "neo-prism-guard");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleWithFixedDelay(() -> {
            if (broken) {
                return;
            }
            try {
                renderThread().execute(() -> check(log));
            } catch (final Throwable e) {
                giveUp(log, e);
            }
        }, 2, 500, TimeUnit.MILLISECONDS);
        // Solo pruebas: -Dneo.prism.breakTest=SEGUNDOS engancha el shader cada
        // tantos segundos, como la sombra rota. La ventana tiene que volver sola.
        final long every = Long.getLong("neo.prism.breakTest", 0);
        if (every > 0) {
            timer.scheduleWithFixedDelay(PrismGuard::breakForTest, every, every, TimeUnit.SECONDS);
        }
    }

    /** Cuantas veces ha tenido que despegar el shader. Para las pruebas. */
    public static int repairs() {
        return repairs;
    }

    /**
     * "VRAM de JavaFX: 312 de 1024 MB", o null si no se puede saber. Desde
     * cualquier hilo: solo lee dos contadores.
     */
    public static String vram() {
        try {
            final Object pool = call(resourceFactory(), "getTextureResourcePool");
            final long used = (Long) call(pool, "used");
            final long max = (Long) call(pool, "max");
            return "VRAM de JavaFX: " + (used >> 20) + " de " + (max >> 20) + " MB";
        } catch (final Throwable e) {
            return null;
        }
    }

    /**
     * SOLO PRUEBAS: engancha un shader cualquiera, como lo deja la sombra rota.
     * La ventana se queda en blanco hasta que el vigilante lo quite.
     */
    public static void breakForTest() {
        try {
            renderThread().execute(() -> {
                try {
                    final Object ctx = context();
                    final Field f = field(ctx.getClass(), "externalShader");
                    final Field stock = field(ctx.getClass(), "stockShaders");
                    Object any = null;
                    for (final Object s : (Object[]) stock.get(ctx)) {
                        if (s != null) {
                            any = s;
                            break;
                        }
                    }
                    f.set(ctx, any);
                    System.out.println("[neo] prueba: shader enganchado a proposito (" + any + ")");
                } catch (final Throwable e) {
                    System.out.println("[neo] prueba: no se pudo enganchar el shader: " + e);
                }
            });
        } catch (final Throwable e) {
            System.out.println("[neo] prueba: sin hilo de render: " + e);
        }
    }

    /** En el hilo de render, entre fotogramas. */
    private static void check(final Consumer<String> log) {
        try {
            final Object ctx = context();
            final Field f = field(ctx.getClass(), "externalShader");
            if (f.get(ctx) == null) {
                return;
            }
            // Lo quita el mismo metodo que usa el efecto al terminar: vacia lo
            // que quedara en cola con su shader y deja el campo a null.
            final Method set = method(ctx.getClass(), "setExternalShader", 2);
            try {
                set.invoke(ctx, null, null);
            } catch (final Throwable e) {
                f.set(ctx, null);
            }
            repairs++;
            final String v = vram();
            log.accept("JavaFX se habia quedado pintando en blanco (una sombra rota a medias); "
                    + "repuesto" + (v == null ? "" : ". " + v) + " (vez " + repairs + ")");
        } catch (final Throwable e) {
            giveUp(log, e);
        }
    }

    private static void giveUp(final Consumer<String> log, final Throwable e) {
        if (!broken) {
            broken = true;
            log.accept("el vigilante de la pantalla en blanco no puede funcionar aqui: " + e);
            if (timer != null) {
                timer.shutdown();
            }
        }
    }

    private static Executor renderThread() throws Exception {
        final Method m = Class.forName("com.sun.javafx.tk.quantum.QuantumRenderer")
                .getDeclaredMethod("getInstance");
        m.setAccessible(true);
        return (Executor) m.invoke(null);
    }

    private static Object resourceFactory() throws Exception {
        return Class.forName("com.sun.prism.GraphicsPipeline")
                .getMethod("getDefaultResourceFactory").invoke(null);
    }

    private static Object context() throws Exception {
        final Object ctx = call(resourceFactory(), "getContext");
        if (ctx == null) {
            throw new IllegalStateException("sin contexto de render");
        }
        return ctx;
    }

    private static Object call(final Object target, final String name) throws Exception {
        final Method m = method(target.getClass(), name, 0);
        return m.invoke(target);
    }

    private static Method method(final Class<?> type, final String name, final int args) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (final Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        throw new IllegalStateException("no hay " + name + " en " + type.getName());
    }

    private static Field field(final Class<?> type, final String name) throws NoSuchFieldException {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (final NoSuchFieldException e) {
                // sigue subiendo
            }
        }
        throw new NoSuchFieldException(name + " en " + type.getName());
    }
}
