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
 * <p>Clase pura y sin API que Android no tenga en la 26: la usan los buscadores
 * del escritorio y, por el jar, los de Android.
 */
public final class SearchTerms {

    private static final String[] NONE = new String[0];

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
                flush(cur, out);
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

    /** Lo mismo, con la consulta ya troceada. */
    public static boolean allIn(final String[] terms, final String... lowerTexts) {
        for (final String t : terms) {
            boolean found = false;
            for (final String h : lowerTexts) {
                if (h != null && h.contains(t)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
