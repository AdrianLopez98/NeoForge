package forge.neo.deck;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>Cuando conseguiste cada carta</b>: el registro que ordena una coleccion por
 * "lo ultimo primero" ({@link DeckEditor.Sort#NEWEST}).
 *
 * <p>Nacio dentro de {@code forge.neo.adventure.AcquiredLedger} y se saco aqui
 * cuando la Quest lo pidio tambien (Discord, 16-10-2026: <i>"sort cards in the
 * deck editor in quest mode by new, so the most recent cards from booster packs
 * are at the top"</i>). Ninguno de los dos modos guarda cuando entro una carta,
 * asi que se apunta aparte: por nombre, las copias que habia la ultima vez y la
 * hora a la que subieron por ultima vez. Quien lo usa le pasa los totales de vez
 * en cuando ({@link #update}) y lo que haya subido se lleva la hora.
 *
 * <p>Java puro y sin JavaFX: lo usa tambien Android por el jar.
 */
public class AcquiredBook {

    private final Map<String, long[]> entries = new ConcurrentHashMap<>();
    private volatile boolean dirty;
    /** De quien es: el registro y su fichero cambian juntos, bajo el mismo cerrojo. */
    private File file;

    /**
     * @param counts copias de cada carta, por nombre en minusculas
     * @param rebase tras una carga: los totales se toman como estan, sin poner
     *               hora a nada (cargar una partida no es conseguir cartas)
     * @param now    la hora que se pone a lo que haya subido
     */
    public synchronized void update(final Map<String, Integer> counts, final boolean rebase,
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

    /** La hora de esa carta, o 0 si no se sabe (lo de antes del registro). */
    public long stampOf(final String key) {
        final long[] e = entries.get(key);
        return e == null ? 0 : e[1];
    }

    public synchronized void clear() {
        entries.clear();
        dirty = true;
    }

    /** De que fichero es ahora mismo, o null. */
    public synchronized File file() {
        return file;
    }

    /** Una linea por carta: hora, copias y nombre. */
    public synchronized void read(final File f) {
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
            System.out.println("[neo] no se ha podido leer " + f + ": " + e);
        }
    }

    public synchronized void write() {
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
            System.out.println("[neo] no se ha podido guardar " + f + ": " + e);
        }
    }
}
