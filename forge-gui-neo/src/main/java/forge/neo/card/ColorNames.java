package forge.neo.card;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.item.PaperCard;

/**
 * <b>Los nombres de las combinaciones de colores</b>, para los buscadores:
 * "boros" son las cartas de identidad blanca y roja, y solo esas.
 *
 * <p>Discord, 11-10-2026 (Minty), con la tabla de nombres delante: <i>"Add
 * the names of color combos to the search logic. For example, if I search
 * Boros, W/R creatures will show up ... And WUBRG for 5 color"</i>; y sobre
 * todo para elegir comandante en Ascenso: <i>"I just tried to search white
 * red, and it gave me results for such ... And you can see here it also
 * includes commanders with green"</i>. Buscar "white red" casa con cualquier
 * carta que diga esas palabras en su texto; "boros" es la identidad
 * <b>exacta</b>.
 *
 * <p>Los gremios de Ravnica, los fragmentos de Alara, los clanes de Tarkir, los
 * cuatro colores por sus nefilim (Glint-Eye, Dune-Brood, Ink-Treader,
 * Witch-Maw, Yore-Tiller, tambien la primera palabra sola) y "wubrg" para los
 * cinco. Son nombres propios de Magic: no se traducen.
 *
 * <p>⚠️ Un nombre <b>anyade</b>, no quita: la carta casa si la palabra esta en
 * su texto <i>o</i> si su identidad es esa combinacion. Asi "witch" sigue
 * encontrando a las brujas y "ink" a Inkmoth Nexus, como antes, y ademas los
 * comandantes de esos cuatro colores. Lo aplican quienes buscan
 * ({@code CommanderSearch}, {@code DeckEditor.find}, {@code CardLibrary.find}).
 *
 * <p>Clase pura: la usa tambien NeoForge Android por el jar.
 */
public final class ColorNames {

    private ColorNames() {
    }

    private static final Map<String, Integer> NAMES = new HashMap<>();

    static {
        final int w = MagicColor.WHITE;
        final int u = MagicColor.BLUE;
        final int b = MagicColor.BLACK;
        final int r = MagicColor.RED;
        final int g = MagicColor.GREEN;
        // Dos colores: los gremios de Ravnica.
        put(w | u, "azorius");
        put(r | w, "boros");
        put(u | b, "dimir");
        put(b | g, "golgari");
        put(r | g, "gruul");
        put(u | r, "izzet");
        put(w | b, "orzhov");
        put(b | r, "rakdos");
        put(g | w, "selesnya");
        put(g | u, "simic");
        // Tres: los fragmentos de Alara y los clanes de Tarkir.
        put(w | b | g, "abzan");
        put(g | w | u, "bant");
        put(w | u | b, "esper");
        put(u | b | r, "grixis");
        put(u | r | w, "jeskai");
        put(b | r | g, "jund");
        put(r | w | b, "mardu");
        put(r | g | w, "naya");
        put(b | g | u, "sultai");
        put(g | u | r, "temur");
        // Cuatro: los nefilim de Guildpact, por el color que les falta.
        put(u | b | r | g, "glint", "glint-eye");
        put(w | b | r | g, "dune", "dune-brood");
        put(w | u | r | g, "ink", "ink-treader");
        put(w | u | b | g, "witch", "witch-maw");
        put(w | u | b | r, "yore", "yore-tiller");
        // Cinco.
        put(w | u | b | r | g, "wubrg", "5c", "five-color");
    }

    private static void put(final int mask, final String... names) {
        for (final String n : names) {
            NAMES.put(n, mask);
        }
    }

    /**
     * La combinacion que nombra esa palabra, o {@code -1} si no es ninguna.
     * Sin mayusculas ni espacios alrededor.
     */
    public static int maskOf(final String word) {
        if (word == null) {
            return -1;
        }
        final Integer mask = NAMES.get(word.trim().toLowerCase(Locale.ROOT));
        return mask == null ? -1 : mask;
    }

    /** Si la identidad de color de la carta es EXACTAMENTE esa combinacion. */
    public static boolean identityIs(final PaperCard card, final int mask) {
        if (mask < 0 || card == null || card.getRules() == null) {
            return false;
        }
        final ColorSet identity = card.getRules().getColorIdentity();
        return identity != null && identity.getColor() == mask;
    }
}
