package forge.neo.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.item.PaperCard;
import forge.neo.NeoSettings;
import forge.neo.NeoText;

/**
 * <b>Lo que llevas cogido en un draft, repartido en mazo y banquillo</b>
 * mientras drafteas, como en Forge y en Arena (itch.io, 23-09-2026, en el
 * escritorio; Discord, 18-11-2026, en Android: <i>"a way to move cards to
 * sideboard, organize by mana cost, all while drafting"</i>).
 *
 * <p>Sin JavaFX: la usan {@code DraftScreen} y, en Android, la pantalla de
 * picks por sus dos puentes (el draft y el de salida de Ascenso). Guarda el
 * reparto por <b>posicion del pick</b>, no por carta: dos copias pueden ir
 * cada una a un lado. Los picks nuevos entran al mazo; las basicas no, que
 * casi nunca se cogen para jugarlas.
 *
 * <p>Lo que va al mazo llega al draft por {@link PackSource#planMain}, y de
 * ahi al constructor del mazo al acabar. Java sencillo (sin {@code switch ->}
 * ni {@code record}): lo carga Android.
 */
public final class PickedPool {

    /** El orden de la tira. "Pick" pone lo ultimo cogido delante. */
    public enum Sort { PICK, COLOR, COST, TYPE }

    /** Donde se recuerda el orden: el mismo ajuste en el escritorio y en Android. */
    public static final String SORT_KEY = "draftSort";

    private final Set<Integer> sideboarded = new HashSet<>();
    /** Cuantos picks se han mirado ya (los de despues son nuevos). */
    private int placed;

    /**
     * Mira los picks nuevos: las basicas, al banquillo. Se llama cada vez que
     * cambia la lista de lo cogido.
     */
    public void notePicks(final List<PaperCard> picked) {
        for (; placed < picked.size(); placed++) {
            if (isBasic(picked.get(placed))) {
                sideboarded.add(placed);
            }
        }
    }

    /** Si el pick numero {@code i} (desde 0) esta en el banquillo. */
    public boolean inSideboard(final int i) {
        return sideboarded.contains(i);
    }

    /** Lo pasa al otro lado. */
    public void toggle(final int i) {
        if (!sideboarded.remove(i)) {
            sideboarded.add(i);
        }
    }

    /** Las posiciones que estan en el banquillo. */
    public Set<Integer> sideboard() {
        return Collections.unmodifiableSet(new HashSet<>(sideboarded));
    }

    /** Lo que va al mazo, en el orden de los picks. */
    public List<PaperCard> main(final List<PaperCard> picked) {
        final List<PaperCard> out = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            if (!sideboarded.contains(i)) {
                out.add(picked.get(i));
            }
        }
        return out;
    }

    /**
     * Se lo dice al draft: lo que no esta en el banquillo va al mazo. Hay que
     * hacerlo tambien con el ULTIMO pick antes de cerrar el draft, o ese se
     * guardaria en el banquillo (lo cazo LimitedUiTest en el escritorio).
     */
    public void planInto(final PackSource draft, final List<PaperCard> picked) {
        notePicks(picked);
        draft.planMain(main(picked));
    }

    // ------------------------------------------------------------------
    //  Orden
    // ------------------------------------------------------------------

    /** El orden guardado, el mismo en los dos sitios. */
    public static Sort savedSort() {
        try {
            return Sort.valueOf(NeoSettings.get(SORT_KEY, Sort.PICK.name()));
        } catch (final IllegalArgumentException e) {
            return Sort.PICK;
        }
    }

    public static void saveSort(final Sort sort) {
        NeoSettings.set(SORT_KEY, sort.name());
        NeoSettings.save();
    }

    /** Las posiciones de {@code picked} en el orden pedido. */
    public static List<Integer> order(final List<PaperCard> picked, final Sort sort) {
        final List<Integer> out = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            out.add(i);
        }
        out.sort(comparator(picked, sort));
        return out;
    }

    private static Comparator<Integer> comparator(final List<PaperCard> mine, final Sort sort) {
        final Comparator<Integer> byName = Comparator.comparing(
                i -> forge.neo.card.CardText.nameOf(mine.get(i)));
        if (sort == Sort.COLOR) {
            return Comparator.<Integer>comparingInt(i -> colourRank(mine.get(i)))
                    .thenComparingInt(i -> cmc(mine.get(i))).thenComparing(byName);
        }
        if (sort == Sort.COST) {
            return Comparator.<Integer>comparingInt(
                    i -> isLand(mine.get(i)) ? 99 : cmc(mine.get(i))).thenComparing(byName);
        }
        if (sort == Sort.TYPE) {
            return Comparator.<Integer>comparingInt(i -> typeRank(mine.get(i)))
                    .thenComparingInt(i -> cmc(mine.get(i))).thenComparing(byName);
        }
        return Comparator.reverseOrder();
    }

    /** El nombre del boton de ese orden ("Pick", "Color"...). */
    public static String label(final Sort sort) {
        return NeoText.get("draft.sort." + sort.name().toLowerCase(Locale.ROOT));
    }

    /**
     * W, U, B, R, G, varios colores, incolora y tierras: el orden en el que se
     * lee un pool de limitado buscando en que colores estas.
     */
    private static int colourRank(final PaperCard card) {
        if (isLand(card)) {
            return 8;
        }
        final forge.card.ColorSet colours = card.getRules().getColor();
        if (colours.isColorless()) {
            return 7;
        }
        if (colours.isMulticolor()) {
            return 6;
        }
        final byte[] wubrg = {forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE,
                forge.card.MagicColor.BLACK, forge.card.MagicColor.RED, forge.card.MagicColor.GREEN};
        for (int i = 0; i < wubrg.length; i++) {
            if (colours.hasAnyColor(wubrg[i])) {
                return i;
            }
        }
        return 7;
    }

    private static int typeRank(final PaperCard card) {
        final forge.card.CardType type = card.getRules().getType();
        return type.isCreature() ? 0 : type.isLand() ? 2 : 1;
    }

    private static int cmc(final PaperCard card) {
        return card.getRules().getManaCost().getCMC();
    }

    private static boolean isLand(final PaperCard card) {
        return card.getRules().getType().isLand();
    }

    public static boolean isBasic(final PaperCard card) {
        return card.getRules().getType().isBasicLand();
    }

    // ------------------------------------------------------------------
    //  De un vistazo
    // ------------------------------------------------------------------

    /**
     * Lo que dice el mazo de un vistazo: cuantas criaturas, cuantos hechizos
     * mas, cuantas tierras, y la curva. Es la pregunta de "donde estan los
     * huecos" que se hace quien draftea a partir del segundo sobre.
     */
    public static String stats(final List<PaperCard> main) {
        int creatures = 0;
        int others = 0;
        int lands = 0;
        final int[] curve = new int[7];
        for (final PaperCard pc : main) {
            if (isLand(pc)) {
                lands++;
                continue;
            }
            if (pc.getRules().getType().isCreature()) {
                creatures++;
            } else {
                others++;
            }
            curve[Math.min(6, cmc(pc))]++;
        }
        final StringBuilder sb = new StringBuilder();
        for (int c = 0; c < curve.length; c++) {
            if (curve[c] == 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(c == 6 ? "6+" : String.valueOf(c)).append(':').append(curve[c]);
        }
        return NeoText.get("draft.stats", creatures, others, lands,
                sb.length() == 0 ? "-" : sb.toString());
    }
}
