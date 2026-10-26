package forge.neo.deck;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import forge.game.GameFormat;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.NeoSettings;

/**
 * <b>Tu propia lista de cartas prohibidas, por formato</b> (Discord,
 * 26-10-2026). Quitar una carta de la lista de Commander, o anyadir una a la de
 * Estandar.
 *
 * <p>Sin tocar Forge: la lista de un {@link GameFormat} es una {@code
 * ArrayList} interna ({@code bannedCardNames}), y su filtro ({@code
 * PaperCardPredicates.names}) guarda <b>la referencia</b>, no una copia. Asi
 * que cambiando esa lista se entera todo lo que usa el filtro: la legalidad del
 * mazo, el catalogo del constructor, y Commander, Oathbreaker y Brawl, cuyo
 * {@code StaticData.setCommanderPredicate} es ese mismo filtro. El campo es
 * protegido y sin setter: se cambia por reflexion.
 *
 * <p>Se guarda lo que <b>cambias</b> ({@code banlist.<formato>.add} y {@code
 * .remove}), no la lista entera: si Forge actualiza la oficial, lo nuevo
 * entra y tus cambios siguen. Lo aplica {@link #applyAll} al arrancar (PC y
 * Android) y cada cambio al momento.
 *
 * <p>Tiny Leaders no esta: su lista va escrita dentro de {@code DeckFormat}.
 * Las Quest tampoco: copian el formato al crearse.
 */
public final class BanList {

    private BanList() {
    }

    /** Los formatos que se pueden editar, si Forge los trae. */
    private static final String[] CANDIDATES = {"Commander", "Brawl", "Oathbreaker", "PreDH", "Standard",
        "Pioneer", "Historic", "Modern", "Legacy", "Vintage", "Extended", "Pauper", "Premodern"};

    /** La lista oficial de cada formato, tal como la leyo Forge. */
    private static final Map<String, List<String>> OFFICIAL = new HashMap<>();

    private static final String SEP = "|";

    /** Los formatos que se pueden editar en esta instalacion. */
    public static List<String> formats() {
        final List<String> out = new ArrayList<>();
        for (final String name : CANDIDATES) {
            if (format(name) != null) {
                out.add(name);
            }
        }
        return out;
    }

    /** Las prohibidas ahora en ese formato, por orden alfabetico. */
    public static synchronized List<String> banned(final String formatName) {
        final GameFormat f = format(formatName);
        if (f == null) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<>(f.getBannedCardNames());
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Si tu lista de ese formato no es la oficial. */
    public static boolean isEdited(final String formatName) {
        return !read(formatName, "add").isEmpty() || !read(formatName, "remove").isEmpty();
    }

    /** Quitarla de las prohibidas (si es de la oficial) o deshacer el haberla anyadido. */
    public static synchronized void unban(final String formatName, final String card) {
        final Set<String> add = read(formatName, "add");
        final Set<String> remove = read(formatName, "remove");
        if (!add.remove(card) && official(formatName).contains(card)) {
            remove.add(card);
        }
        write(formatName, add, remove);
    }

    /** Prohibirla (o deshacer el haberla quitado de la oficial). */
    public static synchronized void ban(final String formatName, final String card) {
        final Set<String> add = read(formatName, "add");
        final Set<String> remove = read(formatName, "remove");
        if (!remove.remove(card) && !official(formatName).contains(card)) {
            add.add(card);
        }
        write(formatName, add, remove);
    }

    /** Vuelve a la lista oficial. */
    public static synchronized void reset(final String formatName) {
        write(formatName, new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    /** Aplica lo guardado a todos los formatos. Al arrancar, despues de {@code FModel.initialize}. */
    public static synchronized void applyAll() {
        for (final String name : formats()) {
            apply(name);
        }
    }

    /**
     * Cartas cuyo nombre (en ingles o en tu idioma) contiene {@code query},
     * para anyadirlas. Hasta {@code max}, sin repetir nombre.
     */
    public static List<String> search(final String query, final int max) {
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<>();
        for (final PaperCard pc : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            final String name = pc.getName();
            String local = name;
            try {
                local = forge.util.CardTranslation.getTranslatedName(name);
            } catch (final RuntimeException e) {
                // sin traduccion: el ingles
            }
            if (name.toLowerCase(Locale.ROOT).contains(q) || (local != null && local.toLowerCase(Locale.ROOT).contains(q))) {
                out.add(name);
            }
        }
        out.sort(Comparator.comparingInt((String n) -> n.toLowerCase(Locale.ROOT).startsWith(q) ? 0 : 1)
                .thenComparing(String.CASE_INSENSITIVE_ORDER));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    // --- por dentro ---

    private static GameFormat format(final String name) {
        try {
            return FModel.getFormats().get(name);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static List<String> official(final String formatName) {
        final List<String> known = OFFICIAL.get(formatName);
        if (known != null) {
            return known;
        }
        final GameFormat f = format(formatName);
        final List<String> copy = f == null ? Collections.<String>emptyList() : new ArrayList<>(f.getBannedCardNames());
        OFFICIAL.put(formatName, copy);
        return copy;
    }

    private static void apply(final String formatName) {
        final GameFormat f = format(formatName);
        if (f == null) {
            return;
        }
        final List<String> official = official(formatName);
        final Set<String> remove = read(formatName, "remove");
        final List<String> effective = new ArrayList<>();
        for (final String c : official) {
            if (!remove.contains(c)) {
                effective.add(c);
            }
        }
        for (final String c : read(formatName, "add")) {
            if (!effective.contains(c)) {
                effective.add(c);
            }
        }
        try {
            final Field field = GameFormat.class.getDeclaredField("bannedCardNames");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            final List<String> live = (List<String>) field.get(f);
            live.clear();
            live.addAll(effective);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[neo] no se ha podido aplicar tu lista de prohibidas de " + formatName + ": " + e);
        }
    }

    private static Set<String> read(final String formatName, final String which) {
        final String raw = NeoSettings.get(key(formatName, which), "");
        final Set<String> out = new LinkedHashSet<>();
        if (!raw.isEmpty()) {
            out.addAll(Arrays.asList(raw.split("\\" + SEP)));
        }
        out.remove("");
        return out;
    }

    private static void write(final String formatName, final Set<String> add, final Set<String> remove) {
        NeoSettings.set(key(formatName, "add"), add.isEmpty() ? null : String.join(SEP, add));
        NeoSettings.set(key(formatName, "remove"), remove.isEmpty() ? null : String.join(SEP, remove));
        NeoSettings.save();
        apply(formatName);
    }

    private static String key(final String formatName, final String which) {
        return "banlist." + formatName.toLowerCase(Locale.ROOT) + "." + which;
    }
}
