package forge.neo.card;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.localinstance.properties.ForgeConstants;

/**
 * <b>Las imagenes que no se pueden bajar, apuntadas para no volver a contarlas.</b>
 *
 * <p>Reportado en itch.io el 24-09-2026, con el registro entero: tras bajar
 * "todas las cartas y todos los artes" quedaban ~740 que fallaban siempre, y
 * cada vez que se entraba a la pantalla decia "744 cards missing" — que parece
 * un fallo y no lo es. Son huecos entre lo que Forge cree que existe y lo que
 * hay: las Alchemy "A-" (solo digitales), las caras de fusion y de las cartas
 * giratorias de Kamigawa (van dentro de la imagen de otra), variantes
 * numeradas que Scryfall no tiene ("Forest3" de NEO) y alguna que Forge pide
 * a {@code downloads.cardforge.org}, que ya no resuelve.
 *
 * <p>El descargador de Forge no dice POR QUE fallo cada una (404, 429, sin
 * red...): todas cuentan igual como hechas. Asi que la regla es otra, y se
 * mira al terminar mirando el disco:
 *
 * <blockquote>Una que falta cuenta como no disponible solo si <b>despues de
 * ella</b> se bajo alguna otra en la misma tanda.</blockquote>
 *
 * <p>Eso prueba que habia red cuando le toco, y deja fuera los dos casos que
 * no hay que apuntar: una tanda sin conexion (no baja ninguna) y una conexion
 * que se cae a mitad (las de despues de la caida no tienen ninguna bajada
 * detras). Un 429 suelto si se colaria, pero el descargador espera el
 * enfriamiento de Scryfall antes de seguir, asi que es una o dos.
 *
 * <p>Y por si acaso, <b>caduca a los {@value #DAYS} dias</b>: Scryfall anyade
 * imagenes (las de un set recien salido, justo como SOA en ese registro) y lo
 * que hoy no esta puede estar el mes que viene.
 *
 * <p>Se guarda al lado de la cache de imagenes, porque es un dato de ella: se
 * va con la carpeta {@code datos} al actualizar, y si se borra la cache se
 * borra con ella. La clave son las dos ultimas piezas de la ruta
 * ({@code SOA/Zombify3.fullborder.jpg}), no la ruta entera: en la copia
 * portable la letra de la unidad cambia de un ordenador a otro.
 */
public final class ArtUnavailable {

    /** Cuanto vale un "no esta". */
    static final int DAYS = 30;

    private ArtUnavailable() {
    }

    private static File file() {
        return new File(ForgeConstants.CACHE_DIR, "pics" + File.separator + "no-disponibles.txt");
    }

    private static long today() {
        return System.currentTimeMillis() / 86_400_000L;
    }

    /** {@code .../pics/cards/SOA/Zombify3.fullborder.jpg} -> {@code soa/zombify3.fullborder.jpg} */
    static String key(final String localPath) {
        final File f = new File(localPath);
        final File parent = f.getParentFile();
        final String name = (parent == null ? "" : parent.getName() + "/") + f.getName();
        return name.toLowerCase(java.util.Locale.ROOT);
    }

    /** Las apuntadas que siguen vigentes, con el dia en que se apunto cada una. */
    private static synchronized Map<String, Long> read() {
        final Map<String, Long> out = new HashMap<>();
        final File f = file();
        if (!f.isFile()) {
            return out;
        }
        final long oldest = today() - DAYS;
        try {
            for (final String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                final int tab = line.indexOf('\t');
                if (tab <= 0) {
                    continue;
                }
                try {
                    final long day = Long.parseLong(line.substring(0, tab));
                    if (day >= oldest) {
                        out.put(line.substring(tab + 1), day);
                    }
                } catch (final NumberFormatException ignored) {
                    // linea rota: se ignora, y al reescribir desaparece
                }
            }
        } catch (final IOException e) {
            System.err.println("[arte] no se pudo leer " + f + ": " + e);
        }
        return out;
    }

    private static synchronized void write(final Map<String, Long> all) {
        final File f = file();
        final List<String> lines = new ArrayList<>(all.size());
        all.forEach((k, day) -> lines.add(day + "\t" + k));
        java.util.Collections.sort(lines);
        try {
            Files.createDirectories(f.getParentFile().toPath());
            Files.write(f.toPath(), lines, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            System.err.println("[arte] no se pudo guardar " + f + ": " + e);
        }
    }

    /**
     * Quita de la lista del descargador las que ya se sabe que no estan.
     *
     * @return cuantas quito
     */
    public static int filter(final Map<String, String> downloads) {
        if (downloads == null || downloads.isEmpty()) {
            return 0;
        }
        final Map<String, Long> known = read();
        if (known.isEmpty()) {
            return 0;
        }
        final int before = downloads.size();
        downloads.keySet().removeIf(path -> known.containsKey(key(path)));
        final int removed = before - downloads.size();
        if (removed > 0) {
            System.out.println("[arte] " + removed + " no disponibles apuntadas: no se piden");
        }
        return removed;
    }

    /** El descargador guarda con otro nombre si Scryfall solo tiene borde completo. */
    private static boolean onDisk(final String localPath) {
        if (new File(localPath).exists()) {
            return true;
        }
        return localPath.contains(".full.")
                && new File(localPath.replace(".full.", ".fullborder.")).exists();
    }

    /**
     * Mira como quedo una tanda TERMINADA y apunta las que no estan.
     *
     * <p>{@code downloads} es la lista que se recorrio, en su orden. Solo se
     * llama cuando la tanda acaba entera: si se cancela no se sabe hasta donde
     * llego.
     *
     * @return cuantas se apuntaron
     */
    public static int record(final Map<String, String> downloads) {
        if (downloads == null || downloads.isEmpty()) {
            return 0;
        }
        final List<String> paths = new ArrayList<>(downloads.keySet());
        int lastOk = -1;
        for (int i = paths.size() - 1; i >= 0; i--) {
            if (onDisk(paths.get(i))) {
                lastOk = i;
                break;
            }
        }
        if (lastOk < 0) {
            // Ninguna bajo: sin red, o Scryfall caido. No se apunta nada.
            return 0;
        }
        final Map<String, Long> all = read();
        final long today = today();
        int added = 0;
        for (int i = 0; i < lastOk; i++) {
            final String p = paths.get(i);
            if (!onDisk(p) && all.putIfAbsent(key(p), today) == null) {
                added++;
            }
        }
        if (added > 0) {
            write(all);
            System.out.println("[arte] " + added + " imagenes no disponibles apuntadas ("
                    + DAYS + " dias)");
        }
        return added;
    }
}
