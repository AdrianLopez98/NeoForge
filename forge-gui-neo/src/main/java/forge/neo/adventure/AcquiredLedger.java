package forge.neo.adventure;

import com.badlogic.gdx.Gdx;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.world.WorldSave;
import forge.item.PaperCard;
import forge.model.FModel;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <b>Cuando conseguiste cada carta</b>, para poder ordenar la coleccion por lo
 * ultimo que ha entrado.
 *
 * <p>Pedido en itch.io el 27-09-2026: <i>"sort the cards by acquisition time in
 * the overview or deck building screens in adventure mode"</i>. El Adventure no
 * lo guarda en ningun sitio: la coleccion es un {@code CardPool} sobre un
 * {@code ConcurrentHashMap} y al guardar se escribe <b>ordenada por nombre</b>
 * ({@code CardPool.toCardList}). Lo unico parecido es {@code newCards}, que es
 * un "desde que cerraste el editor" sin orden dentro, y que ademas solo vacia
 * el editor de Forge — con el nuestro crece para siempre. Y su fichero de
 * partida no se toca (regla de oro), asi que el dato lo apuntamos nosotros, al
 * lado.
 *
 * <p>Como: cada pocos segundos, en el hilo de libGDX (donde vive el jugador),
 * se cuentan las copias de cada carta <b>por nombre</b> — el catalogo del
 * editor es una tesela por nombre — y si alguna ha subido, se le pone la hora.
 * Sube por un premio, una tienda, un sobre o un cofre: da igual por donde, que
 * es justo por lo que se mira el total y no cada camino. El editor tambien lo
 * pide al abrirse ({@link #syncNow}), asi que lo que acabas de ganar ya cuenta.
 *
 * <p>Tres decisiones que no se ven leyendo:
 * <ul>
 *   <li><b>Cargar una partida no es conseguir cartas.</b> La coleccion salta de
 *       cero a cientos de golpe; tras cada carga ({@code onLoad}) se toman los
 *       totales como estan, sin poner horas. Y una partida nueva empieza el
 *       registro de cero: el mazo de salida no es "lo ultimo".</li>
 *   <li><b>Lo vendido no se borra</b>, se queda con cero copias. Si luego
 *       vuelve a entrar, sube de cero y cuenta como nueva — que es lo que es.</li>
 *   <li><b>Lo de antes no tiene hora</b> (0): el registro existe desde la 3.9.
 *       Esas cartas van detras de las fechadas, por nombre, y el boton lo dice.</li>
 * </ul>
 *
 * <p>Un fichero por personaje, junto a sus partidas:
 * {@code <adventure>/<plano>/neo-adquiridas-<nombre>.txt}. No toca nada del
 * Adventure: si se borra, solo se pierde el orden.
 */
public final class AcquiredLedger {

    private AcquiredLedger() {
    }

    /** Cada cuanto se miran los totales. Contar 2.000 entradas es nada. */
    private static final long PERIOD_MS = 3000;

    private static final Book book = new Book();
    private static final AtomicBoolean queued = new AtomicBoolean();
    private static volatile boolean listening;
    /** Hubo una carga: los proximos totales se toman como estan. */
    private static volatile boolean rebase = true;
    /** La carga era una partida NUEVA: el registro empieza de cero. */
    private static volatile boolean fresh;
    private static volatile String owner;

    /** Arranca el vigilante. Lo llama {@link AdventureNeoMain}. */
    static void arm() {
        if ("false".equalsIgnoreCase(System.getProperty("neo.adventure.acquired"))) {
            return;
        }
        final Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(PERIOD_MS);
                } catch (final InterruptedException e) {
                    return;
                }
                try {
                    if (Gdx.app != null && FModel.getMagicDb() != null
                            && queued.compareAndSet(false, true)) {
                        // Uno en cola como mucho: si libGDX esta parado (un
                        // duelo nuestro delante), no se amontonan.
                        Gdx.app.postRunnable(() -> {
                            queued.set(false);
                            syncNow();
                        });
                    }
                    book.write();
                } catch (final Throwable ignored) {
                    // todavia cargando, o un fallo al escribir: el orden es un
                    // adorno y no puede tumbar la Aventura
                }
            }
        }, "neo-adventure-acquired");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Mira los totales ya. <b>En el hilo de libGDX</b>, que es donde se cargan
     * y se tocan las cartas del jugador.
     */
    static void syncNow() {
        try {
            final WorldSave save = WorldSave.getCurrentSave();
            if (!listening) {
                listening = true;
                save.onLoad(() -> {
                    rebase = true;
                    if (StarterDeck.fromNewWorld()) {
                        fresh = true;
                    }
                });
            }
            if (save.getWorld().getData() == null) {
                return; // en la portada: no hay partida
            }
            final AdventurePlayer player = save.getPlayer();
            final String name = player.getName();
            if (name == null || name.isBlank()) {
                return;
            }
            if (!name.equals(owner)) {
                book.write();
                owner = name;
                book.read(new File(WorldSave.getSaveDir(), "neo-adquiridas-" + safe(name) + ".txt"));
                rebase = true;
            }
            if (fresh) {
                fresh = false;
                book.clear();
            }
            final Map<String, Integer> counts = new HashMap<>();
            for (final Map.Entry<PaperCard, Integer> e : player.getCards()) {
                counts.merge(key(e.getKey()), e.getValue(), Integer::sum);
            }
            book.update(counts, rebase, System.currentTimeMillis());
            rebase = false;
        } catch (final Throwable e) {
            NeoDuelBridge.log("registro de cartas conseguidas: " + e);
        }
    }

    /** Cuando entro esa carta (milisegundos), o 0 si no se sabe. */
    static long acquiredAt(final PaperCard card) {
        return book.stampOf(key(card));
    }

    static String key(final PaperCard card) {
        return card == null ? "" : card.getName().toLowerCase(Locale.ROOT);
    }

    private static String safe(final String name) {
        final String s = name.replaceAll("[^\\p{L}\\p{N}_ -]", "_").trim();
        return s.isEmpty() ? "_" : s;
    }

    /**
     * El registro en si, sin libGDX: lo que prueba {@code AdventureCheck}.
     *
     * <p>Por nombre: las copias que habia la ultima vez y la hora a la que
     * subieron por ultima vez.
     */
    static final class Book {
        private final Map<String, long[]> entries = new ConcurrentHashMap<>();
        private volatile boolean dirty;
        /** De quien es: el registro y su fichero cambian juntos, bajo el mismo cerrojo. */
        private File file;

        /**
         * @param rebase tras una carga: los totales se toman como estan, sin
         *               poner hora a nada
         */
        synchronized void update(final Map<String, Integer> counts, final boolean rebase,
                                 final long now) {
            for (final Map.Entry<String, Integer> c : counts.entrySet()) {
                final long[] e = entries.get(c.getKey());
                final int have = c.getValue();
                if (e == null) {
                    entries.put(c.getKey(), new long[] {have, rebase ? 0 : now});
                    dirty = true;
                } else if (have != e[0]) {
                    if (have > e[0] && !rebase) {
                        e[1] = now;
                    }
                    e[0] = have;
                    dirty = true;
                }
            }
            // Lo que ya no tienes se queda con cero: si vuelve, es nuevo.
            for (final Map.Entry<String, long[]> e : entries.entrySet()) {
                if (e.getValue()[0] != 0 && !counts.containsKey(e.getKey())) {
                    e.getValue()[0] = 0;
                    dirty = true;
                }
            }
        }

        long stampOf(final String key) {
            final long[] e = entries.get(key);
            return e == null ? 0 : e[1];
        }

        synchronized void clear() {
            entries.clear();
            dirty = true;
        }

        /** Una linea por carta: hora, copias y nombre. */
        synchronized void read(final File f) {
            file = f;
            entries.clear();
            dirty = false;
            if (f == null || !f.isFile()) {
                return;
            }
            try {
                for (final String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    final String[] p = line.split("\t", 3);
                    if (p.length == 3) {
                        try {
                            entries.put(p[2], new long[] {Long.parseLong(p[1]), Long.parseLong(p[0])});
                        } catch (final NumberFormatException ignored) {
                            // linea rota: se salta
                        }
                    }
                }
            } catch (final IOException e) {
                NeoDuelBridge.log("no se ha podido leer " + f + ": " + e);
            }
        }

        synchronized void write() {
            final File f = file;
            if (!dirty || f == null) {
                return;
            }
            final List<String> lines = new ArrayList<>(entries.size());
            for (final Map.Entry<String, long[]> e : entries.entrySet()) {
                lines.add(e.getValue()[1] + "\t" + e.getValue()[0] + "\t" + e.getKey());
            }
            try {
                f.getParentFile().mkdirs();
                final File tmp = new File(f.getPath() + ".tmp");
                Files.write(tmp.toPath(), lines, StandardCharsets.UTF_8);
                Files.move(tmp.toPath(), f.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                dirty = false;
            } catch (final IOException e) {
                NeoDuelBridge.log("no se ha podido guardar " + f + ": " + e);
            }
        }
    }
}
