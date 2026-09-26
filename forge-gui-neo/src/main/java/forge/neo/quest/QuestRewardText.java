package forge.neo.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.neo.NeoText;

/**
 * Las lineas del botin de la Quest, en el idioma del jugador.
 *
 * <p>Las escribe {@code QuestWinLoseController} de Forge con frases fijas EN
 * INGLES pegadas a los numeros ("Easy opponent: 25 credits.", "You've won a
 * random rare."), sin pasar por su {@code Localizer}; el titulo de al lado
 * ("Resultados del juego") si va traducido, asi que la pantalla salia mitad en
 * castellano y mitad en ingles (visto en el movil el 25-09-2026).
 *
 * <p>Esto <b>reconoce</b> cada linea y la reescribe con los mismos numeros. Lo
 * que no reconoce se deja tal cual: media frase traducida es peor que una
 * entera en ingles, y una frase nueva de Forge no puede romper el botin. Es el
 * mismo criterio que {@code EnginePhrase} para los avisos de la mesa.
 */
public final class QuestRewardText {

    private QuestRewardText() {
    }

    private static final String N = "(\\d+)";
    private static final String CR = " credits?\\.?";

    private record Rule(Pattern pattern, Function<Matcher, String> out) {
    }

    private static final List<Rule> RULES = new ArrayList<>();

    private static void rule(final String regex, final Function<Matcher, String> out) {
        RULES.add(new Rule(Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE), out));
    }

    static {
        rule("(easy|medium|hard|very hard|expert|wild) opponent: " + N + CR,
                m -> NeoText.get("quest.rw.opponent", difficulty(m.group(1)), m.group(2)));
        rule("Random Opponent Bonus: " + N + CR,
                m -> NeoText.get("quest.rw.random", m.group(1)));
        rule("Bonus for previous wins: " + N + CR,
                m -> NeoText.get("quest.rw.previous", m.group(1)));
        rule("Alternate win condition: (?:<u>)?(.+?)(?:</u>)?! Bonus: " + N + CR,
                m -> NeoText.get("quest.rw.altwin", m.group(1), m.group(2)));
        rule("Mulliganed to zero and still won! Bonus: " + N + CR,
                m -> NeoText.get("quest.rw.mulligan", m.group(1)));
        rule("Won on turn zero! ?Bonus: " + N + CR,
                m -> NeoText.get("quest.rw.turnZero", m.group(1)));
        rule("Won in one turn! ?Bonus: " + N + CR,
                m -> NeoText.get("quest.rw.turnOne", m.group(1)));
        rule("Won by turn " + N + "! ?Bonus: " + N + CR,
                m -> NeoText.get("quest.rw.turnBy", m.group(1), m.group(2)));
        rule("Life total difference: " + N + CR,
                m -> NeoText.get("quest.rw.life", m.group(1)));
        rule("You have not lost once! Bonus: " + N + CR,
                m -> NeoText.get("quest.rw.undefeated", m.group(1)));
        rule("Estates bonus \\(" + N + "%\\): " + N + CR,
                m -> NeoText.get("quest.rw.estates", m.group(1), m.group(2)));
        rule("(?:You've earned|Could be worse:|A respectable|An impressive|Spectacular match!) "
                        + N + " credits in total\\.?",
                m -> NeoText.get("quest.rw.total", m.group(1)));
        rule("You've won a random rare for winning against a very hard deck\\.?",
                m -> NeoText.get("quest.rw.rareHard"));
        rule("You've won a random rare\\.?",
                m -> NeoText.get("quest.rw.rare"));
    }

    /** Si alguna regla reconoce esa linea (para el comprobador). */
    public static boolean recognizes(final String line) {
        final String t = line == null ? "" : line.trim();
        for (final Rule r : RULES) {
            if (r.pattern().matcher(t).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Una linea, o varias separadas por saltos: cada una por su lado. */
    public static String translate(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        final String[] lines = text.split("\\r?\\n", -1);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(line(lines[i]));
        }
        return out.toString();
    }

    private static String line(final String raw) {
        final String t = raw.trim();
        if (t.isEmpty()) {
            return raw;
        }
        for (final Rule r : RULES) {
            final Matcher m = r.pattern().matcher(t);
            if (m.matches()) {
                try {
                    final String s = r.out().apply(m);
                    // Sin traduccion (la clave vuelve tal cual): el ingles.
                    return s.startsWith("quest.rw.") ? raw : s;
                } catch (final RuntimeException e) {
                    return raw;
                }
            }
        }
        return raw;
    }

    /** La dificultad del rival con las palabras de la pantalla (quest.diff.*), en minusculas. */
    private static String difficulty(final String english) {
        final String key;
        switch (english.toLowerCase(Locale.ROOT)) {
            case "medium": key = "quest.diff.medium"; break;
            case "hard": key = "quest.diff.hard"; break;
            case "very hard":
            case "expert": key = "quest.diff.expert"; break;
            case "wild": key = "quest.diff.wild"; break;
            default: key = "quest.diff.easy"; break;
        }
        return NeoText.get(key).toLowerCase(Locale.ROOT);
    }
}
