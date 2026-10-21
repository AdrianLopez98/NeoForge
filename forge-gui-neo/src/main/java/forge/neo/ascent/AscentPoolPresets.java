package forge.neo.ascent;

import java.util.ArrayList;
import java.util.List;

import forge.neo.NeoSettings;

/**
 * <b>Las combinaciones de expansiones guardadas</b> de Ascenso (Discord,
 * 21-10-2026: <i>"a list of personalized pack combos to choose from to generate
 * a run instead of having to build the same 7 pack gothic run each time ...
 * being able to save multiple set combinations"</i>).
 *
 * <p>Montar un bloque a medida son siete clics en el desplegable cada run. Se
 * guarda con un boton y sale como una pastilla que lo deja puesto de un clic.
 * Vale tambien para un desde/hasta; "todas" no, que ya es un clic.
 *
 * <p>En una linea de los ajustes ({@value #KEY}), cada una con
 * {@link AscentPool#serialize} y separadas por {@code ;}, que ese formato no
 * usa ({@code set:ZEN,WWK} / {@code range:LEA:4ED}). La ultima guardada va
 * delante; como mucho {@value #MAX}, y la mas vieja se cae.
 *
 * <p>Pura, Java de la 8 y compartida con Android por el jar.
 */
public final class AscentPoolPresets {

    static final String KEY = "ascent.poolPresets";
    public static final int MAX = 12;

    private AscentPoolPresets() {
    }

    /** Las guardadas, la ultima primero. Las que ya no se entienden se saltan. */
    public static synchronized List<AscentPool> all() {
        final List<AscentPool> out = new ArrayList<>();
        for (final String raw : NeoSettings.get(KEY, "").split(";")) {
            if (raw.trim().isEmpty()) {
                continue;
            }
            final AscentPool p = AscentPool.parse(raw.trim());
            if (!p.isAll() && !out.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Si se puede guardar: un desde/hasta o unas expansiones, y no repetida. */
    public static boolean canSave(final AscentPool pool) {
        if (pool == null || pool.isAll()) {
            return false;
        }
        if (pool.kind == AscentPool.Kind.SET && (pool.sets == null || pool.sets.isEmpty())) {
            return false;
        }
        return !all().contains(pool);
    }

    /** La guarda delante de todas. @return si se ha guardado */
    public static synchronized boolean save(final AscentPool pool) {
        if (!canSave(pool)) {
            return false;
        }
        final List<AscentPool> list = all();
        list.add(0, pool);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        write(list);
        return true;
    }

    public static synchronized void remove(final AscentPool pool) {
        final List<AscentPool> list = all();
        if (list.remove(pool)) {
            write(list);
        }
    }

    /**
     * Como se ensenya en su pastilla: los codigos ("ZEN · WWK · ROE"), que son
     * cortos y es como se conocen, o "LEA – 4ED" para un desde/hasta.
     */
    public static String label(final AscentPool pool) {
        if (pool == null) {
            return "";
        }
        if (pool.kind == AscentPool.Kind.RANGE) {
            return pool.from + " – " + pool.to;
        }
        return pool.sets == null ? "" : String.join(" · ", pool.sets);
    }

    private static void write(final List<AscentPool> list) {
        final List<String> raw = new ArrayList<>();
        for (final AscentPool p : list) {
            raw.add(p.serialize());
        }
        NeoSettings.set(KEY, raw.isEmpty() ? null : String.join(";", raw));
        NeoSettings.save();
    }
}
