package forge.neo.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.StaticData;
import forge.card.CardEdition;
import forge.card.CardRules;
import forge.item.PaperToken;

/**
 * <b>Las fichas en la enciclopedia</b> (Discord, 24-10-2026: <i>"would it be
 * possible to show the Tokens in the Encyclopaedia too? Perhaps we could have a
 * separate tab where we can view all the Token images?"</i>).
 *
 * <p>Una por ficha (por su guion, {@code w_1_1_soldier}), en su impresion mas
 * reciente: la misma que baja {@link TokenArtDownload}, asi que con el arte de
 * las fichas bajado se ven todas sin red. Sin JavaFX: la usa tambien NeoForge
 * Android ({@code LibraryBridge}).
 */
public final class TokenLibrary {

    private static volatile List<PaperToken> all;

    private TokenLibrary() {
    }

    /** Todas, por nombre. Se calcula una vez (unas decimas): pedirlo fuera del hilo de interfaz. */
    public static List<PaperToken> all() {
        List<PaperToken> out = all;
        if (out == null) {
            out = build();
            all = out;
        }
        return out;
    }

    /** Las que casan con el texto: nombre, tipo, o texto de reglas. Vacio = todas. */
    public static List<PaperToken> find(final String text) {
        final String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return all();
        }
        final String[] words = q.split("\\s+");
        final List<PaperToken> out = new ArrayList<>();
        for (final PaperToken t : all()) {
            final String hay = haystack(t);
            boolean ok = true;
            for (final String w : words) {
                if (!hay.contains(w)) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(t);
            }
        }
        return out;
    }

    private static String haystack(final PaperToken t) {
        final CardRules r = t.getRules();
        final StringBuilder sb = new StringBuilder(t.getName());
        if (r != null) {
            sb.append(' ').append(r.getType()).append(' ').append(r.getOracleText());
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static List<PaperToken> build() {
        final StaticData data = StaticData.instance();
        if (data == null) {
            return Collections.emptyList();
        }
        // El guion de cada ficha y la expansion mas nueva que la trae con numero.
        final Map<String, CardEdition> newest = new LinkedHashMap<>();
        final Map<String, Date> when = new HashMap<>();
        for (final CardEdition ed : data.getEditions()) {
            if (ed.getType() == CardEdition.Type.CUSTOM_SET) {
                continue;
            }
            for (final Map.Entry<String, CardEdition.EditionEntry> t : ed.getTokens().entries()) {
                final String name = t.getKey();
                final String number = t.getValue() == null ? null : t.getValue().collectorNumber();
                if (name == null || name.isEmpty() || number == null || number.isEmpty()) {
                    continue;
                }
                final Date seen = when.get(name);
                if (seen == null || (ed.getDate() != null && ed.getDate().after(seen))) {
                    when.put(name, ed.getDate());
                    newest.put(name, ed);
                }
            }
        }
        final List<PaperToken> out = new ArrayList<>(newest.size());
        for (final Map.Entry<String, CardEdition> e : newest.entrySet()) {
            try {
                final PaperToken t = data.getAllTokens().getToken(e.getKey(), e.getValue().getCode(), 1);
                if (t != null) {
                    out.add(t);
                }
            } catch (final RuntimeException ignored) {
                // un guion que la expansion nombra y no existe: se queda fuera
            }
        }
        out.sort(Comparator.comparing((PaperToken t) -> t.getName().toLowerCase(Locale.ROOT))
                .thenComparing(PaperToken::getEdition));
        System.out.println("[fichas] enciclopedia: " + out.size());
        return Collections.unmodifiableList(out);
    }
}
