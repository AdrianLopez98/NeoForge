package forge.neo.ascent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import forge.item.PaperCard;
import forge.neo.NeoSettings;

/**
 * <b>Los comandantes favoritos de Ascenso</b> (Discord, 06-10-2026: <i>"Ability
 * to add favorite commanders to Ascent"</i>).
 *
 * <p>Son diez mil comandantes en paginas de veinticuatro: el que siempre quieres
 * jugar habia que buscarlo cada vez. Se marcan con una estrella en el selector,
 * salen <b>los primeros</b> y hay un filtro para ver solo esos.
 *
 * <p>Se guardan <b>por nombre</b> en una sola linea de los ajustes, separados
 * por {@code |} (ningun nombre de carta lo lleva: es el separador de Forge en
 * los {@code .dck}). Por nombre y no por impresion porque lo que gusta es el
 * comandante, y el selector ya ensenya la impresion del pozo de la run.
 *
 * <p>Pura y compartida con Android por el jar.
 */
public final class AscentFavorites {

    static final String KEY = "ascent.favoriteCommanders";

    private AscentFavorites() {
    }

    /** Los favoritos, en el orden en que se marcaron. */
    public static synchronized Set<String> names() {
        final Set<String> out = new LinkedHashSet<>();
        for (final String n : NeoSettings.get(KEY, "").split("\\|")) {
            if (!n.isBlank()) {
                out.add(n.trim());
            }
        }
        return out;
    }

    public static boolean isFavorite(final String name) {
        return name != null && names().contains(name);
    }

    /**
     * Lo marca o lo desmarca, y lo guarda.
     *
     * @return si queda como favorito
     */
    public static synchronized boolean toggle(final String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        final Set<String> all = names();
        final boolean now = !all.remove(name);
        if (now) {
            all.add(name);
        }
        NeoSettings.set(KEY, all.isEmpty() ? null : String.join("|", all));
        NeoSettings.save();
        return now;
    }

    /** Los mismos comandantes con los favoritos delante (cada grupo en su orden). */
    public static List<PaperCard> favoritesFirst(final List<PaperCard> pool) {
        final Set<String> fav = names();
        if (fav.isEmpty()) {
            return pool;
        }
        final List<PaperCard> first = new ArrayList<>();
        final List<PaperCard> rest = new ArrayList<>();
        for (final PaperCard c : pool) {
            (fav.contains(c.getName()) ? first : rest).add(c);
        }
        first.addAll(rest);
        return first;
    }

    /** Solo los favoritos que deja el pozo. */
    public static List<PaperCard> only(final List<PaperCard> pool) {
        final Set<String> fav = names();
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard c : pool) {
            if (fav.contains(c.getName())) {
                out.add(c);
            }
        }
        return out;
    }
}
