package forge.neo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * El orden de las casillas del menu y cuales se esconden, a gusto del jugador
 * (pedido en Discord el 24-10-2026: <i>"I will also never use the Tutorial so
 * it'd be great to be able to customize the main screen"</i>).
 *
 * <p>Logica pura y sin pantalla, para que el escritorio y Android ordenen igual:
 * cada uno le pasa SU lista de fabrica (no tienen las mismas casillas ni en el
 * mismo orden) y esto la devuelve como la dejo el jugador. Se guarda en
 * {@code neo.properties} como dos listas de identificadores separados por comas.
 *
 * <p>Tres cosas que no se ven leyendolo por encima:
 * <ul>
 * <li><b>Una casilla nueva no se pierde.</b> Si una version trae un modo que el
 * orden guardado no conoce, entra detras de la casilla que la precede de
 * fabrica — no al final, donde nadie la buscaria, ni escondida.</li>
 * <li><b>Lo guardado que ya no existe se ignora</b>, sin borrarlo: la Aventura
 * no esta en Mac, y un {@code neo.properties} copiado de Windows no tiene por
 * que perder su sitio.</li>
 * <li><b>No se puede esconder la ultima casilla visible.</b> Un menu vacio no
 * tiene por donde entrar a jugar (principio 7).</li>
 * </ul>
 *
 * <p>Sin {@code List.of} ni {@code isBlank}: lo usa Android desde la 26.
 */
public final class MenuLayout {

    /** El orden elegido, todas las casillas (tambien las ocultas). */
    public static final String ORDER = "menuOrder";

    /** Las casillas que no salen en el menu. */
    public static final String HIDDEN = "menuHidden";

    private MenuLayout() {
    }

    /** Todas las casillas, en el orden del jugador, ocultas incluidas. */
    public static List<String> order(final List<String> defaults) {
        final List<String> out = new ArrayList<>();
        for (final String id : read(ORDER)) {
            if (defaults.contains(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        for (int i = 0; i < defaults.size(); i++) {
            final String id = defaults.get(i);
            if (out.contains(id)) {
                continue;
            }
            // Detras de la que la precede de fabrica (la mas cercana que ya este).
            int at = 0;
            for (int j = i - 1; j >= 0; j--) {
                final int k = out.indexOf(defaults.get(j));
                if (k >= 0) {
                    at = k + 1;
                    break;
                }
            }
            out.add(at, id);
        }
        return out;
    }

    /** Las que salen en el menu, en su orden. */
    public static List<String> visible(final List<String> defaults) {
        final Set<String> hidden = hiddenSet();
        final List<String> out = new ArrayList<>();
        for (final String id : order(defaults)) {
            if (!hidden.contains(id)) {
                out.add(id);
            }
        }
        // Si todo estuviera oculto (a mano en el fichero), mejor el menu entero
        // que ninguno.
        return out.isEmpty() ? new ArrayList<>(defaults) : out;
    }

    public static boolean isHidden(final String id) {
        return hiddenSet().contains(id);
    }

    /**
     * Esconde o ensenya una casilla. Devuelve false, sin tocar nada, si es la
     * ultima que quedaba a la vista.
     */
    public static boolean setHidden(final List<String> defaults, final String id, final boolean hide) {
        final Set<String> hidden = hiddenSet();
        if (hide) {
            if (hidden.contains(id)) {
                return true;
            }
            int shown = 0;
            for (final String d : defaults) {
                if (!hidden.contains(d) && !d.equals(id)) {
                    shown++;
                }
            }
            if (shown == 0) {
                return false;
            }
            hidden.add(id);
        } else {
            hidden.remove(id);
        }
        write(HIDDEN, new ArrayList<>(hidden));
        return true;
    }

    /** ¿Se puede esconder esta? (no, si es la ultima a la vista). */
    public static boolean canHide(final List<String> defaults, final String id) {
        if (isHidden(id)) {
            return true;
        }
        for (final String d : visible(defaults)) {
            if (!d.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mueve una casilla {@code delta} puestos (negativo, hacia delante). Las
     * ocultas cuentan como puestos: en el modo de ordenar se ven todas.
     */
    public static void move(final List<String> defaults, final String id, final int delta) {
        final List<String> all = order(defaults);
        final int from = all.indexOf(id);
        if (from < 0) {
            return;
        }
        final int to = Math.max(0, Math.min(all.size() - 1, from + delta));
        if (to == from) {
            return;
        }
        all.remove(from);
        all.add(to, id);
        write(ORDER, all);
    }

    /** La pone en el sitio de {@code target} (arrastrar y soltar). */
    public static void moveTo(final List<String> defaults, final String id, final String target) {
        final List<String> all = order(defaults);
        final int from = all.indexOf(id);
        final int to = all.indexOf(target);
        if (from < 0 || to < 0 || from == to) {
            return;
        }
        all.remove(from);
        all.add(to, id);
        write(ORDER, all);
    }

    /** ¿Ha tocado algo el jugador? (para encender "Restaurar"). */
    public static boolean customized(final List<String> defaults) {
        return !order(defaults).equals(defaults) || !hiddenSet().isEmpty();
    }

    /** El menu de fabrica otra vez. */
    public static void reset() {
        NeoSettings.set(ORDER, null);
        NeoSettings.set(HIDDEN, null);
        NeoSettings.save();
    }

    private static Set<String> hiddenSet() {
        return new LinkedHashSet<>(read(HIDDEN));
    }

    private static List<String> read(final String key) {
        final String raw = NeoSettings.get(key, "");
        final List<String> out = new ArrayList<>();
        for (final String s : Arrays.asList(raw.split(","))) {
            final String id = s.trim();
            if (!id.isEmpty()) {
                out.add(id);
            }
        }
        return out;
    }

    private static void write(final String key, final List<String> ids) {
        NeoSettings.set(key, ids.isEmpty() ? null : String.join(",", ids));
        NeoSettings.save();
    }
}
