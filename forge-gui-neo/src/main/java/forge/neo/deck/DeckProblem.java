package forge.neo.deck;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.neo.NeoText;

/**
 * Qué le falta al mazo, en el idioma en que se juega.
 *
 * <p>{@code DeckFormat.getDeckConformanceProblem} es quien decide si un mazo
 * vale, y eso no se toca: las reglas de construcción las contesta el motor. El
 * problema es que <b>compone la frase a mano</b> — {@code TextUtil.concatWithSpace(
 * "should have at least", min, "cards")} — <b>sin pasar por {@code Localizer}</b>,
 * así que sale en inglés en los once idiomas, incluidos los diez que Forge sí
 * traduce. Y es la línea que se lee cada vez que se empieza un mazo.
 *
 * <p>Arreglarlo río arriba sería tocar {@code forge-core}, o sea saltarse la
 * regla de oro. Así que se reconoce desde fuera, que es lo mismo que hace
 * {@link forge.neo.EnginePhrase} con los prompts de la partida — sólo que aquí
 * es más fácil: como el motor <b>nunca</b> traduce estos textos, siempre llegan
 * en inglés y basta con una expresión regular.
 *
 * <p><b>Lo que no se reconoce se deja pasar tal cual.</b> Es la misma regla que
 * {@link forge.neo.match.WhyNot}: se afirma sólo lo que se sabe, y lo demás se
 * enseña como viene. Aquí están las seis formas que un jugador ve montando
 * mazos — cuatro de {@code DeckFormat} (mínimo, máximo, copias, identidad) y
 * dos de {@code GameFormat} (fuera del pozo, restringida) —; las otras —
 * atracciones, cachivaches, cartas personalizadas, cartas que no existen —
 * pasan de largo, y es lo correcto: traducir a ciegas una frase que no se ha
 * visto nunca es la forma de acabar diciendo otra cosa.
 */
public final class DeckProblem {

    private DeckProblem() {
    }

    /** "should have at least 60 cards" */
    private static final Pattern AT_LEAST =
            Pattern.compile("^should have at least (\\d+) cards$");

    /** "should have no more than 100 cards" */
    private static final Pattern AT_MOST =
            Pattern.compile("^should have no more than (\\d+) cards$");

    /** "must not contain more than 4 copies of the card Lightning Bolt" */
    private static final Pattern COPIES =
            Pattern.compile("^must not contain more than (\\d+) copies of the card (.+)$",
                    Pattern.DOTALL);

    /**
     * La de Commander, que además arrastra la lista de cartas detrás.
     *
     * <p>Sólo se traduce el encabezado: lo que sigue son <b>nombres de carta</b>,
     * uno por línea, y ésos ya vienen en el idioma que toca (o no se traducen).
     */
    private static final Pattern IDENTITY = Pattern.compile(
            "^contains one or more cards that do not match the commanders color identity:(.*)$",
            Pattern.DOTALL);

    /**
     * Las dos formas de {@code GameFormat.getDeckConformanceProblem} — el pozo
     * de un formato construido (Modern, Pioneer, Pauper...), no la construcción
     * del mazo. También arrastran la lista de cartas detrás, y por lo mismo.
     */
    private static final Pattern POOL_ILLEGAL = Pattern.compile(
            "^contains the following illegal cards:(.*)$", Pattern.DOTALL);

    private static final Pattern POOL_RESTRICTED = Pattern.compile(
            "^contains more than one copy of the following restricted cards:(.*)$", Pattern.DOTALL);

    /**
     * El texto del motor, traducido si lo reconocemos.
     *
     * @param engine lo que devolvió {@code getDeckConformanceProblem}, o null
     * @return el mismo texto en el idioma elegido, o tal cual si no es una de
     *         las formas conocidas
     */
    public static String translate(final String engine) {
        if (engine == null || engine.isBlank()) {
            return engine;
        }
        final String s = engine.trim();

        Matcher m = AT_LEAST.matcher(s);
        if (m.matches()) {
            return NeoText.get("deck.problem.atLeast", m.group(1));
        }

        m = AT_MOST.matcher(s);
        if (m.matches()) {
            return NeoText.get("deck.problem.atMost", m.group(1));
        }

        m = COPIES.matcher(s);
        if (m.matches()) {
            return NeoText.get("deck.problem.copies", m.group(1), m.group(2));
        }

        m = IDENTITY.matcher(s);
        if (m.matches()) {
            return NeoText.get("deck.problem.identity") + m.group(1);
        }

        m = POOL_ILLEGAL.matcher(s);
        if (m.matches()) {
            return NeoText.get("deck.problem.poolIllegal") + m.group(1);
        }

        m = POOL_RESTRICTED.matcher(s);
        if (m.matches()) {
            return NeoText.get("deck.problem.poolRestricted") + m.group(1);
        }

        return engine;
    }
}
