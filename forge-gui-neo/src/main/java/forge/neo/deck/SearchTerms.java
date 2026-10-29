package forge.neo.deck;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>Como se busca una carta</b>: cada palabra tiene que estar, en cualquier
 * orden y en cualquier sitio de la carta.
 *
 * <p>Reportado jugando el 16-10-2026: en la coleccion de la Quest, "flying,
 * dou" sacaba a Zetalpa y "flying, vigi" no. Todos los buscadores comparaban la
 * frase ENTERA como un trozo de texto, asi que solo encontraban lo que la carta
 * dijera literalmente en ese orden ("Flying, double strike, vigilance..."). Lo
 * que se pide es lo de Scryfall: <i>"con poner flying y vigilancia tendra que
 * salir todas las cartas que tengan flying y vigilancia, da igual el orden"</i>.
 *
 * <p>Las palabras se separan por espacios, comas o punto y coma, y cada una
 * vale si esta en CUALQUIERA de los textos que se le den (nombre, tipo, texto
 * de reglas): "elf flying" encuentra un elfo que vuela. Entre comillas se busca
 * la frase exacta ("double strike"). Un nombre de varias palabras sigue
 * saliendo, porque todas sus palabras estan en el nombre.
 *
 * <p><b>{@code t:} busca solo en la linea de tipo</b>, como en Scryfall
 * (Discord, 29-10-2026: <i>"i am building a shrine deck ... typing shrine in
 * the search bar gives you cards that have shrine in the name but not
 * necesarily in the type"</i>). {@code t:shrine} deja fuera a <i>Shrine of
 * Burning Rage</i>, que es un artefacto, y a lo que solo habla de Shrines en su
 * texto. Se combina con lo demas ({@code t:elf flying}) y admite comillas
 * ({@code t:"elf warrior"}). Lo entiende quien le pasa la linea de tipo
 * aparte ({@link #allInTyped}); a los buscadores que no la separan, un
 * {@code t:} les llega como una palabra normal, sin el prefijo.
 *
 * <p>Clase pura y sin API que Android no tenga en la 26: la usan los buscadores
 * del escritorio y, por el jar, los de Android.
 */
public final class SearchTerms {

    private static final String[] NONE = new String[0];

    /** El prefijo de "solo en la linea de tipo". */
    private static final String TYPE = "t:";

    /** La ultima consulta troceada: se pregunta una vez por carta con la misma. */
    private static volatile Object[] last = {"", NONE};

    private SearchTerms() {
    }

    /** Las palabras de una consulta, en minusculas; vacio si no hay nada que buscar. */
    public static String[] parse(final String query) {
        if (query == null) {
            return NONE;
        }
        final Object[] memo = last;
        if (query.equals(memo[0])) {
            return (String[]) memo[1];
        }
        final String q = query.toLowerCase(Locale.ROOT);
        final List<String> out = new ArrayList<>();
        final StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < q.length(); i++) {
            final char c = q.charAt(i);
            if (c == '"' || c == '“' || c == '”') {
                // t:"elf warrior" — el prefijo se queda pegado a la frase
                if (!(!quoted && TYPE.contentEquals(cur))) {
                    flush(cur, out);
                }
                quoted = !quoted;
            } else if (!quoted && (Character.isWhitespace(c) || c == ',' || c == ';')) {
                flush(cur, out);
            } else {
                cur.append(c);
            }
        }
        flush(cur, out);
        final String[] terms = out.toArray(NONE);
        last = new Object[] {query, terms};
        return terms;
    }

    private static void flush(final StringBuilder cur, final List<String> out) {
        final String t = cur.toString().trim();
        if (!t.isEmpty()) {
            out.add(t);
        }
        cur.setLength(0);
    }

    /**
     * Si cada palabra de {@code query} esta en alguno de los textos (ya en
     * minusculas, o null). Sin palabras, todo vale.
     */
    public static boolean matches(final String query, final String... lowerTexts) {
        return allIn(parse(query), lowerTexts);
    }

    /** Lo mismo, con la consulta ya troceada. Un {@code t:} vale como palabra normal. */
    public static boolean allIn(final String[] terms, final String... lowerTexts) {
        return allInTyped(terms, null, lowerTexts);
    }

    /**
     * Como {@link #matches}, pero las palabras con {@code t:} solo valen si
     * estan en {@code lowerTypes} (la linea de tipo, en ingles y traducida).
     * Las demas se buscan en {@code lowerTexts}, que debe incluir tambien el tipo.
     */
    public static boolean matchesTyped(final String query, final String[] lowerTypes,
            final String... lowerTexts) {
        return allInTyped(parse(query), lowerTypes, lowerTexts);
    }

    /** Lo mismo, con la consulta ya troceada; sin {@code lowerTypes}, {@code t:} mira todo. */
    public static boolean allInTyped(final String[] terms, final String[] lowerTypes,
            final String... lowerTexts) {
        for (final String t : terms) {
            final boolean typeOnly = t.startsWith(TYPE);
            final String word = typeOnly ? t.substring(TYPE.length()).trim() : t;
            if (word.isEmpty()) {
                continue; // "t:" a medio escribir: aun no pide nada
            }
            if (!in(word, typeOnly && lowerTypes != null ? lowerTypes : lowerTexts)) {
                return false;
            }
        }
        return true;
    }

    private static boolean in(final String word, final String[] lowerTexts) {
        for (final String h : lowerTexts) {
            if (h != null && h.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
