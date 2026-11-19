package forge.neo.deck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import forge.deck.CommanderBracketCalculator;
import forge.deck.Deck;
import forge.neo.NeoSettings;
import forge.neo.NeoText;
import forge.neo.match.NeoFormat;

/**
 * <b>El bracket de Commander de un mazo</b> (Discord, 19-11-2026: <i>"support
 * for commander brackets (1-5 + uncategorized)? Being able to mark/categorize/
 * filter decks ... and also an option to limit which decks AI opponents can
 * pick"</i>). Sin JavaFX: lo usan el PC y Android.
 *
 * <p><b>Dos fuentes, y manda la tuya.</b> Forge ya CALCULA un bracket minimo
 * del 1 al 4 ({@code CommanderBracketCalculator}: Game Changers, destruccion
 * masiva de tierras, turnos extra y combos de dos cartas; lo usa su GUI Swing
 * como columna). Lo que no puede calcular es el 5 ni la intencion del mazo, asi
 * que cada mazo se puede MARCAR a mano (1-5); sin marca, vale el calculado.
 * "Sin categoria" en el filtro son los que no has marcado.
 *
 * <p><b>La marca va en {@code neo.properties}, no en el {@code .dck}</b>
 * ({@code bracket.<FORMATO>.<mazo>}): los de Forge y los de internet no se
 * pueden guardar desde el selector, y asi tambien se pueden marcar. Lo
 * comparten el PC y Android (decision 129). Si renombras un mazo, pierde la
 * marca (vuelve al calculado).
 *
 * <p><b>Los rivales al azar</b> ({@link #rivalsFirst}) van ordenados con los
 * que encajan delante, no filtrados: si ninguno encaja, sale uno igual, como
 * cuando no hay mazos legales ({@code RivalDecks}). Nunca un rival sin mazo.
 */
public final class DeckBracket {

    private DeckBracket() {
    }

    /** El mas alto que existe (cEDH). */
    public static final int MAX = 5;

    private static final String KEY = "bracket.";

    /** Lo que se le pide a un rival al azar, comparado con tu mazo. */
    public enum Limit {
        ANY, AT_MOST, SAME, AT_LEAST;

        public String label() {
            return NeoText.get("bracket.limit." + name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /** El ajuste de los rivales al azar, por formato. */
    public static Limit limit(final NeoFormat format) {
        final String v = NeoSettings.get(KEY + "limit." + (format == null ? "" : format.name()), "");
        for (final Limit l : Limit.values()) {
            if (l.name().equals(v)) {
                return l;
            }
        }
        return Limit.ANY;
    }

    public static void setLimit(final NeoFormat format, final Limit limit) {
        NeoSettings.set(KEY + "limit." + (format == null ? "" : format.name()),
                limit == null || limit == Limit.ANY ? null : limit.name());
        NeoSettings.save();
    }

    /** Si el bracket tiene sentido en este formato (los de comandante). */
    public static boolean appliesTo(final NeoFormat format) {
        return format != null && format.isCommanderStyle();
    }

    // ------------------------------------------------------------------
    //  La marca
    // ------------------------------------------------------------------

    private static String markKey(final NeoFormat format, final String deckName) {
        return KEY + (format == null ? "" : format.name()) + "." + deckName;
    }

    /** Lo mismo por NOMBRE de mazo (Android trabaja con resumenes, no con el Deck). */
    public static Integer marked(final NeoFormat format, final String deckName) {
        if (deckName == null || deckName.isEmpty()) {
            return null;
        }
        final int v = NeoSettings.getInt(markKey(format, deckName), 0);
        return v >= 1 && v <= MAX ? v : null;
    }

    public static void mark(final NeoFormat format, final String deckName, final Integer bracket) {
        if (deckName == null || deckName.isEmpty()) {
            return;
        }
        NeoSettings.set(markKey(format, deckName),
                bracket == null || bracket < 1 || bracket > MAX ? null : String.valueOf(bracket));
        NeoSettings.save();
    }

    /** El que le has puesto tu, 1-5, o null si no lo has marcado. */
    public static Integer marked(final NeoFormat format, final Deck deck) {
        return deck == null ? null : marked(format, deck.getName());
    }

    /** Marcarlo (1-5) o quitar la marca (null). Se guarda en el acto. */
    public static void mark(final NeoFormat format, final Deck deck, final Integer bracket) {
        if (deck != null) {
            mark(format, deck.getName(), bracket);
        }
    }

    // ------------------------------------------------------------------
    //  El calculado
    // ------------------------------------------------------------------

    /**
     * Por OBJETO: {@code Deck.equals} compara el contenido entero, y un mazo
     * guardado de nuevo es otro objeto (se recalcula, que es lo correcto). Se
     * vacia si crece demasiado, por no guardar los de antes para siempre.
     */
    private static final Map<Deck, Integer> CALCULATED =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** El minimo que calcula Forge, 1-4. */
    public static int calculated(final Deck deck) {
        if (deck == null) {
            return 1;
        }
        final Integer hit = CALCULATED.get(deck);
        if (hit != null) {
            return hit;
        }
        int b;
        try {
            b = CommanderBracketCalculator.getBracket(deck);
        } catch (final RuntimeException e) {
            // Sin las listas de Forge (res/lists) o con un mazo raro: el mas bajo.
            b = 1;
        }
        if (CALCULATED.size() > 4000) {
            CALCULATED.clear();
        }
        CALCULATED.put(deck, b);
        return b;
    }

    /** El porque del calculado, en el idioma del juego (lo escribe Forge). */
    public static String explanation(final Deck deck) {
        try {
            return CommanderBracketCalculator.getExplanation(deck);
        } catch (final RuntimeException e) {
            return "";
        }
    }

    /** El que vale: el tuyo si lo has marcado, si no el calculado. */
    public static int of(final NeoFormat format, final Deck deck) {
        final Integer m = marked(format, deck);
        return m != null ? m : calculated(deck);
    }

    // ------------------------------------------------------------------
    //  Filtro y rivales
    // ------------------------------------------------------------------

    /** Lo que se elige en el filtro: 0 = todos, 1-5 = ese bracket, -1 = sin marcar. */
    public static final int FILTER_ANY = 0;
    public static final int FILTER_UNMARKED = -1;

    public static boolean matches(final NeoFormat format, final Deck deck, final int filter) {
        if (filter == FILTER_ANY) {
            return true;
        }
        if (filter == FILTER_UNMARKED) {
            return marked(format, deck) == null;
        }
        return of(format, deck) == filter;
    }

    /** Las opciones del filtro, en orden: todos, 1..5, sin marcar. */
    public static int[] filterValues() {
        return new int[] {FILTER_ANY, 1, 2, 3, 4, 5, FILTER_UNMARKED};
    }

    public static String filterLabel(final int filter) {
        return filter == FILTER_ANY ? NeoText.get("bracket.filter.any")
                : filter == FILTER_UNMARKED ? NeoText.get("bracket.filter.unmarked")
                : NeoText.get("bracket.filter.n", filter, name(filter));
    }

    /** Lo mismo, corto, para el filtro cerrado: "Bracket", "B4", "Sin poner". */
    public static String shortFilterLabel(final int filter) {
        return filter == FILTER_ANY ? NeoText.get("bracket.title")
                : filter == FILTER_UNMARKED ? NeoText.get("bracket.filter.unmarkedShort")
                : NeoText.get("bracket.badge", filter);
    }

    /** El nombre del bracket: Exhibition, Core, Upgraded, Optimized, cEDH. */
    public static String name(final int bracket) {
        return bracket >= 1 && bracket <= MAX ? NeoText.get("bracket.name." + bracket) : "";
    }

    /** Si un rival con ese mazo encaja con el limite, contra tu bracket. */
    public static boolean fits(final NeoFormat format, final Deck rival, final int mine, final Limit limit) {
        if (limit == null || limit == Limit.ANY || rival == null) {
            return true;
        }
        final int b = of(format, rival);
        switch (limit) {
            case AT_MOST:
                return b <= mine;
            case SAME:
                return b == mine;
            case AT_LEAST:
                return b >= mine;
            default:
                return true;
        }
    }

    /**
     * La bolsa de los rivales al azar con los que encajan DELANTE, cada grupo
     * en el orden que traia (el sorteo, y los modernos antes si toca). Con
     * {@link Limit#ANY}, o fuera de Commander, la misma lista.
     */
    public static List<Deck> rivalsFirst(final NeoFormat format, final List<Deck> pool, final Deck mine) {
        final Limit limit = limit(format);
        if (pool == null || mine == null || limit == Limit.ANY || !appliesTo(format)) {
            return pool;
        }
        final int me = of(format, mine);
        final List<Deck> good = new ArrayList<>();
        final List<Deck> rest = new ArrayList<>();
        for (final Deck d : pool) {
            (fits(format, d, me, limit) ? good : rest).add(d);
        }
        good.addAll(rest);
        return good;
    }
}
