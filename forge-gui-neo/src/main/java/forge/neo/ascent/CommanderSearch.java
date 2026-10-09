package forge.neo.ascent;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import forge.card.CardEdition;
import forge.card.CardRules;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.util.CardTranslation;

/**
 * <b>Buscar comandante por algo mas que el nombre</b> (Discord, 08-10-2026:
 * <i>"When choosing a commander in Ascent, add the ability to filter cards by
 * more than name ... abilities (Lifelink, Trample, Deathtouch), sets, types"</i>).
 *
 * <p>Cada palabra de lo buscado tiene que salir en ALGUN sitio de la carta: el
 * nombre (tambien el traducido), la linea de tipos, el texto de reglas (ahi
 * estan las palabras clave: <i>lifelink</i>, <i>trample</i>...) o una de las
 * expansiones en las que se imprimio, por codigo o por nombre. Asi
 * "lifelink vampire" da los vampiros con vinculo vital, y "dmu legendary" los
 * de Dominaria United. Sin tildes ni mayusculas: "relampago" encuentra
 * "Relampago" con tilde.
 *
 * <p>Lo de siempre, buscar por nombre, sale por el camino corto sin construir
 * nada. Lo demas se prepara la primera vez que hace falta para cada carta y se
 * guarda por nombre: son unos 11.000 comandantes y la impresion de cada uno
 * no cambia en toda la sesion.
 *
 * <p>Lo usan el selector de comandante de Ascenso del escritorio y, por el
 * jar, el de Android (Ascenso y Quest de Commander): nada de API de Java que
 * Android no tenga en la 26 (ni {@code isBlank}, ni {@code CardText.typeOf},
 * que la usa).
 */
public final class CommanderSearch {

    private CommanderSearch() {
    }

    /** Lo que se busca de cada carta, ya en minusculas y sin tildes, por nombre. */
    private static final Map<String, String> HAYSTACK = new ConcurrentHashMap<>();

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** Si la carta casa con lo buscado. Sin nada escrito, todas. */
    public static boolean matches(final PaperCard card, final String query) {
        if (card == null) {
            return false;
        }
        final List<String> words = words(query);
        if (words.isEmpty()) {
            return true;
        }
        // El camino corto: buscar por nombre, que es lo que se hace casi siempre.
        final String name = fold(card.getName());
        boolean allInName = true;
        for (final String w : words) {
            if (!name.contains(w)) {
                allInName = false;
                break;
            }
        }
        if (allInName) {
            return true;
        }
        final String hay = haystack(card);
        for (final String w : words) {
            if (!hay.contains(w)) {
                return false;
            }
        }
        return true;
    }

    /** Las palabras de lo buscado, en minusculas y sin tildes. */
    static List<String> words(final String query) {
        final List<String> out = new ArrayList<>();
        if (query == null) {
            return out;
        }
        for (final String w : SPACES.split(fold(query).trim())) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /** Todo lo que se mira de una carta. Solo pruebas y este paquete. */
    static String haystack(final PaperCard card) {
        final String key = card.getName();
        final String cached = HAYSTACK.get(key);
        if (cached != null) {
            return cached;
        }
        final String built = build(card);
        HAYSTACK.put(key, built);
        return built;
    }

    private static String build(final PaperCard card) {
        final StringBuilder sb = new StringBuilder(512);
        add(sb, card.getName());
        final CardRules rules = card.getRules();
        String type = null;
        if (rules != null) {
            type = rules.getType() == null ? null : rules.getType().toString();
            add(sb, type);
            add(sb, rules.getOracleText());
        }
        // Lo traducido, para quien juega en otro idioma: "vinculo vital",
        // "vampiro". En ingles devuelve lo mismo y no estorba.
        try {
            add(sb, CardTranslation.getTranslatedName(card.getName()));
            if (type != null) {
                add(sb, CardTranslation.getTranslatedType(card.getName(), type));
            }
            add(sb, CardTranslation.getTranslatedOracle(card.getName()));
        } catch (final RuntimeException ignored) {
            // sin traduccion: se busca en ingles
        }
        // Las expansiones de TODAS sus impresiones, por codigo y por nombre:
        // un comandante se reimprime, y quien busca "dominaria" lo quiere aunque
        // el pozo lo haya cogido de otra.
        try {
            final CardEdition.Collection editions = FModel.getMagicDb().getEditions();
            for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards(card.getName())) {
                add(sb, p.getEdition());
                final CardEdition ed = editions.get(p.getEdition());
                if (ed != null) {
                    add(sb, ed.getName());
                }
            }
        } catch (final RuntimeException ignored) {
            add(sb, card.getEdition());
        }
        return fold(sb.toString());
    }

    private static void add(final StringBuilder sb, final String s) {
        if (s != null && !s.isEmpty()) {
            sb.append(s).append('\n');
        }
    }

    /** Minusculas y sin tildes. */
    static String fold(final String s) {
        if (s == null) {
            return "";
        }
        final String lower = s.toLowerCase(Locale.ROOT);
        return MARKS.matcher(Normalizer.normalize(lower, Normalizer.Form.NFD)).replaceAll("");
    }
}
