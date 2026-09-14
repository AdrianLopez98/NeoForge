package forge.neo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traducir el aviso de <b>elegir objetivo</b>, que Forge no traduce en ningun
 * idioma.
 *
 * <p><b>Que es esto y que NO es.</b> No confundir con {@link EnginePhrase}, que
 * <i>reconoce</i> una frase del motor venga en el idioma que venga (para saber
 * si el prompt es el rutinario, si el boton se llama "Auto"...). Esto es lo
 * contrario: coge una frase que el motor ha escrito <b>en ingles a la fuerza</b>
 * y la vuelve a escribir en el idioma del jugador.
 *
 * <p><b>Por que hace falta.</b> El aviso sale de dos sitios y los dos son
 * ingles crudo:
 *
 * <ul>
 *   <li>{@code TargetRestrictions} lo compone a mano:
 *       {@code "Select target " + validTgtsDesc}, y {@code Lang.buildValidDesc}
 *       tampoco traduce nada — solo pasa el tipo a minusculas.</li>
 *   <li>La mayoria de las veces ni eso: viene del {@code TgtPrompt$} del
 *       <b>script de la carta</b>, que es texto suelto en ingles dentro de
 *       {@code cardsfolder}. Son <b>7.205 usos de 2.217 frases distintas</b>.</li>
 * </ul>
 *
 * <p>O sea que no hay ninguna clave de {@code Localizer} que pedir: el ingles
 * esta cosido al motor y a los datos, y le pasa igual a las dos interfaces
 * oficiales de Forge. Por la regla de oro no se toca alli.
 *
 * <p><b>Como se traduce sin una tabla de 2.217 filas.</b> La frase inglesa es
 * muy regular, asi que se <b>compone</b> a partir de piezas que si estan
 * traducidas:
 *
 * <pre>
 *   Select  up to one   target  nonland          permanent  an opponent controls
 *   └verbo┘ └cuantif.─┘         └adjetivo──────┘ └nombre──┘ └calificador───────┘
 *   Elige   hasta un            permanente que no sea tierra que controla un oponente
 * </pre>
 *
 * <p>Con un vocabulario de unas cien piezas se cubre el <b>76%</b> de los usos
 * reales de {@code cardsfolder}. El resto son nombres de subtipo
 * (<i>"Select target Wall"</i>) y frases escritas a medida para una carta.
 *
 * <p><b>Y la regla que lo hace seguro: o entera, o nada.</b> Si sobra un solo
 * token que no este en el vocabulario, se devuelve la frase <b>tal cual vino</b>.
 * Nunca se entrega media frase traducida: un aviso a medio idioma
 * (<i>"Elige una criatura you control"</i>) es peor que el ingles entero, y aqui
 * ademas es texto que decide una jugada. Es el mismo principio que
 * {@code frontLoadInstruction}: <i>no inventamos nada sobre un texto que no
 * reconocemos</i>.
 *
 * <p><b>El vocabulario vive en los ficheros de idioma</b> ({@link NeoText}), no
 * aqui: lo unico que es nuestro es el lado <b>ingles</b>, que no se traduce
 * porque es la forma en que escribe el motor. Y se pide con
 * {@link NeoText#own(String)} y no con {@code get}, a proposito: si a un idioma
 * le falta una pieza hay que <b>rendirse</b>, no caerse al ingles y mezclar.
 */
public final class EngineText {

    private EngineText() {
    }

    // ------------------------------------------------------------------
    // Lo que se llama desde fuera
    // ------------------------------------------------------------------

    /**
     * El aviso entero, renglon a renglon.
     *
     * <p>Se va por renglones porque el aviso de objetivos son varios y solo uno
     * es la instruccion: {@code InputSelectTargets} monta
     * {@code "<carta> - <instruccion>"}, y debajo la lista de lo ya elegido.
     * Traducir el bloque entero de una pieza obligaria a reconocerlo todo; asi,
     * cada renglon se traduce si se puede y se queda si no.
     */
    public static String prompt(final String message) {
        if (message == null || message.isEmpty() || !translating()) {
            return message;
        }
        final String[] lines = message.split("\n", -1);
        boolean changed = false;
        for (int i = 0; i < lines.length; i++) {
            final String t = line(lines[i]);
            if (!t.equals(lines[i])) {
                lines[i] = t;
                changed = true;
            }
        }
        return changed ? String.join("\n", lines) : message;
    }

    /**
     * Un renglon.
     *
     * <p>La instruccion puede venir sola o detras del nombre de la carta
     * ({@code "Cobbled Wings - Select target creature you control"}). Se prueba
     * primero entera y despues solo la cola, porque el nombre de la carta ya
     * viene traducido por el motor y no hay que tocarlo.
     */
    static String line(final String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        final String fixed = fixed(raw.trim());
        if (fixed != null) {
            return raw.replace(raw.trim(), fixed);
        }
        final String whole = phrase(raw.trim());
        if (whole != null) {
            return raw.replace(raw.trim(), whole);
        }
        final int cut = raw.lastIndexOf(SEPARATOR);
        if (cut > 0) {
            final String tail = raw.substring(cut + SEPARATOR.length()).trim();
            final String t = phrase(tail);
            if (t != null) {
                return raw.substring(0, cut + SEPARATOR.length()) + t;
            }
        }
        return raw;
    }

    /** Como {@code InputSelectTargets} pega el nombre de la carta y la instruccion. */
    private static final String SEPARATOR = " - ";

    /**
     * Una frase de elegir objetivo, o {@code null} si no se reconoce entera.
     *
     * <p>Publico porque tambien vale para un aviso que llegue suelto, sin el
     * bloque de {@code InputSelectTargets} alrededor.
     */
    public static String phrase(final String english) {
        if (english == null || english.isBlank() || !translating()) {
            return null;
        }
        try {
            return parse(english.trim());
        } catch (final RuntimeException e) {
            // Un aviso raro no puede tumbar una partida: el precio de fallar
            // aqui es quedarse en ingles, que es justo donde estabamos.
            return null;
        }
    }

    /**
     * ¿Hay que traducir algo?
     *
     * <p>En ingles no: la frase ya esta en ingles y componerla otra vez solo
     * podria empeorarla.
     *
     * <p>⚠️ <b>Y se guarda la respuesta.</b> {@code NeoLanguage.current()} LISTA
     * LA CARPETA {@code res/languages} cada vez que se le pregunta, y aqui se
     * pregunta una vez por renglon de cada aviso — o sea varias veces por turno,
     * desde el hilo del motor. En el escritorio no se notaria; en una tablet eso
     * es E/S sobre el almacenamiento compartido en mitad de la partida.
     *
     * <p>Guardarla es ademas <b>correcto</b>, no un atajo: el idioma no puede
     * cambiar sin reiniciar (los nombres de carta se precargan al arrancar, ver
     * {@link NeoLanguage}), asi que la respuesta no cambia mientras el proceso
     * viva. {@link #forget()} existe solo para las pruebas, que si cambian.
     */
    private static Boolean translating;

    private static boolean translating() {
        Boolean t = translating;
        if (t == null) {
            t = !NeoLanguage.DEFAULT.equals(NeoLanguage.current());
            translating = t;
        }
        return t;
    }

    /** Olvida el idioma guardado. Solo hace falta en {@code TextCheck}. */
    static void forget() {
        translating = null;
    }

    // ------------------------------------------------------------------
    // Los trozos fijos que NO vienen del script de la carta
    // ------------------------------------------------------------------

    /**
     * Los renglones que {@code InputSelectTargets} escribe el mismo.
     *
     * <p>Son literales en su codigo ({@code "\nTargeted: "},
     * {@code "\nParent Targeted:"}, {@code "(N more can be targeted)"}), o sea
     * que no dependen de ninguna carta y se traducen enteros. Son ademas los que
     * mas se repiten, porque salen en <b>cada</b> eleccion de objetivo.
     */
    private static String fixed(final String text) {
        final Matcher m = MORE.matcher(text);
        if (m.matches()) {
            final String more = NeoText.own("tgt.fixed.moreCanBeTargeted");
            return more == null ? null : more.replace("{0}", m.group(1));
        }
        final String key = FIXED.get(text);
        return key == null ? null : NeoText.own(key);
    }

    private static final Pattern MORE = Pattern.compile("\\((\\d+) more can be targeted\\)");

    /** Ingles literal del motor -> clave de {@link NeoText}. */
    private static final Map<String, String> FIXED = new LinkedHashMap<>();

    static {
        FIXED.put("Targeted:", "tgt.fixed.targeted");
        FIXED.put("Parent Targeted:", "tgt.fixed.parentTargeted");
    }

    // ------------------------------------------------------------------
    // El analizador
    // ------------------------------------------------------------------

    private static String parse(final String original) {
        String t = original.replaceAll("\\s+", " ").trim();
        final boolean dot = t.endsWith(".");
        if (dot) {
            t = t.substring(0, t.length() - 1).trim();
        }
        // Se normaliza lo que el vocabulario no deberia tener que escribir: el
        // apostrofo (que ademas se escapa distinto en cada fichero) y la
        // contraccion.
        t = t.replace("don't", "do not").replace("doesn't", "does not")
             .replace("opponent's", "opponent").replace("'s", "");

        final String[] words = t.split(" ");
        final String verb = verbOf(words[0]);
        if (verb == null) {
            return null;
        }
        final String rest = String.join(" ", Arrays.asList(words).subList(1, words.length));

        // "Select any target [para lo que sea]": no lleva nombre detras.
        final String any = anyTarget(verb, rest, dot);
        if (any != null) {
            return any;
        }
        // "Select a player": tampoco lleva la palabra "target".
        if (rest.startsWith("a ") || rest.startsWith("an ")) {
            final String body = rest.substring(rest.indexOf(' ') + 1);
            final String made = nounList(body, Quantifier.NONE);
            return made == null ? null : finish(verb, made, List.of(), dot);
        }

        // <cuantificador> target|targets <cuerpo>
        Quantifier quant = null;
        String body = null;
        for (int n = 4; n >= 0; n--) {
            if (words.length > n + 1 && ("target".equals(words[n + 1]) || "targets".equals(words[n + 1]))) {
                final String q = String.join(" ", Arrays.asList(words).subList(1, n + 1));
                quant = QUANTIFIERS.get(q);
                if (quant != null) {
                    body = String.join(" ", Arrays.asList(words).subList(n + 2, words.length));
                }
                break;
            }
        }
        if (quant == null || body == null || body.isBlank()) {
            return null;
        }
        body = body.replace("don't", "do not").replace("opponent's", "opponent");

        // Los calificadores se despegan por el final, del mas largo al mas
        // corto: "creature you don't control" tiene que ver "you do not
        // control" antes que "control".
        final List<String> quals = new ArrayList<>();
        boolean cut = true;
        while (cut && !body.isBlank()) {
            cut = false;
            for (final String k : QUALIFIER_ORDER) {
                if (body.endsWith(" " + k)) {
                    final String es = NeoText.own(QUALIFIERS.get(k));
                    if (es == null) {
                        return null;
                    }
                    quals.add(0, es);
                    body = body.substring(0, body.length() - k.length() - 1).trim();
                    cut = true;
                    break;
                }
            }
            if (cut) {
                continue;
            }
            final String[] parts = body.split(" ");
            for (int i = parts.length - 1; i > 0 && !cut; i--) {
                final String tail = String.join(" ", Arrays.asList(parts).subList(i, parts.length));
                for (final Numeric num : NUMERICS) {
                    final Matcher m = num.pattern.matcher(tail);
                    if (m.matches()) {
                        final String es = NeoText.own(num.key);
                        if (es == null) {
                            return null;
                        }
                        quals.add(0, es.replace("{0}", m.group(1)));
                        body = String.join(" ", Arrays.asList(parts).subList(0, i)).trim();
                        cut = true;
                        break;
                    }
                }
            }
        }
        if (body.isBlank()) {
            return null;
        }
        final String made = nounList(body, quant);
        return made == null ? null : finish(verb, made, quals, dot);
    }

    private static String finish(final String verb, final String nouns,
            final List<String> quals, final boolean dot) {
        final StringBuilder sb = new StringBuilder(verb).append(' ').append(nouns);
        for (final String q : quals) {
            sb.append(' ').append(q);
        }
        if (dot) {
            sb.append('.');
        }
        return sb.toString();
    }

    private static String verbOf(final String word) {
        final String key = VERBS.get(word);
        return key == null ? null : NeoText.own(key);
    }

    /** "Select any target to distribute damage to" y sus hermanas. */
    private static String anyTarget(final String verb, final String rest, final boolean dot) {
        for (final Map.Entry<String, String> e : ANY.entrySet()) {
            if (!rest.equals(e.getKey()) && !rest.startsWith(e.getKey() + " ")) {
                continue;
            }
            final String head = NeoText.own(e.getValue());
            if (head == null) {
                return null;
            }
            final String tail = rest.substring(e.getKey().length()).trim();
            if (tail.isEmpty()) {
                return finish(verb, head, List.of(), dot);
            }
            final String qual = QUALIFIERS.get(tail);
            if (qual == null) {
                return null;
            }
            final String es = NeoText.own(qual);
            return es == null ? null : finish(verb, head, List.of(es), dot);
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Los nombres
    // ------------------------------------------------------------------

    /**
     * "artifact, creature, or land" -> "un artefacto, una criatura o una tierra".
     *
     * <p>El cuantificador <b>sustituye al articulo del primero</b> y no se
     * repite: "hasta un artefacto o un encantamiento", que es como se dice.
     */
    private static String nounList(final String body, final Quantifier quant) {
        final List<String> items = splitNouns(body);
        final List<Head> heads = new ArrayList<>();
        for (final String it : items) {
            final Head h = head(it);
            if (h == null) {
                return null;
            }
            heads.add(h);
        }
        if (heads.isEmpty()) {
            return null;
        }
        // El plural llega por dos caminos: el cuantificador ("up to two") o el
        // propio nombre ("Select target creatures", que no lleva ninguno).
        boolean plural = quant.plural;
        for (final Head h : heads) {
            plural |= h.many;
        }
        final boolean fem = heads.get(0).fem;
        final String q = quant.word(fem);
        final List<String> pieces = new ArrayList<>();
        for (int i = 0; i < heads.size(); i++) {
            final Head h = heads.get(i);
            String word;
            if (plural) {
                word = h.plur;
            } else {
                final String art = (i == 0 && q != null && !q.isEmpty())
                        ? q : NeoText.own(h.fem ? "tgt.article.f" : "tgt.article.m");
                if (art == null) {
                    return null;
                }
                word = art + " " + h.sing;
            }
            if (!h.adjectives.isEmpty()) {
                word += " " + String.join(" ", h.adjectives);
            }
            pieces.add(word);
        }
        String out;
        if (pieces.size() == 1) {
            out = pieces.get(0);
        } else {
            final String or = NeoText.own("tgt.or");
            if (or == null) {
                return null;
            }
            out = String.join(", ", pieces.subList(0, pieces.size() - 1))
                    + " " + or + " " + pieces.get(pieces.size() - 1);
        }
        if (plural && q != null && !q.isEmpty()) {
            out = q + " " + out;
        }
        return out;
    }

    /**
     * Parte por "or", pero <b>no</b> por el "or" que va dentro de una pieza.
     *
     * <p>"attacking or blocking creature" es UN nombre con UN adjetivo, y
     * "instant or sorcery card" tambien. Partirlos por el "or" da dos nombres
     * que no existen.
     */
    private static List<String> splitNouns(final String body) {
        String t = body;
        for (int i = 0; i < GLUED.size(); i++) {
            t = t.replace(GLUED.get(i), "@@" + i + "@@");
        }
        t = t.replace(", or ", "|").replace(" or ", "|").replace(", ", "|");
        final List<String> out = new ArrayList<>();
        for (String piece : t.split("\\|")) {
            for (int i = 0; i < GLUED.size(); i++) {
                piece = piece.replace("@@" + i + "@@", GLUED.get(i));
            }
            if (!piece.isBlank()) {
                out.add(piece.trim());
            }
        }
        return out;
    }

    private static final List<String> GLUED =
            List.of("attacking or blocking", "instant or sorcery");

    /** Un nombre ya resuelto: como se dice, de que genero es y que adjetivos lleva. */
    private static final class Head {
        private final String sing;
        private final String plur;
        private final boolean fem;
        private final boolean many;
        private final List<String> adjectives;

        Head(final String sing, final String plur, final boolean fem,
                final boolean many, final List<String> adjectives) {
            this.sing = sing;
            this.plur = plur;
            this.fem = fem;
            this.many = many;
            this.adjectives = adjectives;
        }
    }

    private static Head head(final String item) {
        final String[] words = item.split(" ");
        final String last = words[words.length - 1];

        // "<tipo> card" y "<tipo> spell" son productivos: "creature card",
        // "instant or sorcery card", "noncreature spell"... Se resuelven aparte
        // porque en castellano son "carta DE criatura", no un adjetivo.
        if (words.length >= 2 && (last.startsWith("card") || last.startsWith("spell"))) {
            final boolean card = last.startsWith("card");
            final String base = NeoText.own(card ? "tgt.head.card" : "tgt.head.spell");
            final String basePl = NeoText.own(card ? "tgt.head.cards" : "tgt.head.spells");
            final String noBase = NeoText.own(card ? "tgt.head.cardNon" : "tgt.head.spellNon");
            if (base == null || basePl == null || noBase == null) {
                return null;
            }
            String tw = String.join(" ", Arrays.asList(words).subList(0, words.length - 1));
            boolean negated = false;
            if (!TYPEWORDS.containsKey(tw) && tw.startsWith("non") && TYPEWORDS.containsKey(tw.substring(3))) {
                tw = tw.substring(3);
                negated = true;
            }
            final String key = TYPEWORDS.get(tw);
            if (key != null) {
                final String type = NeoText.own(key);
                if (type == null) {
                    return null;
                }
                final boolean many = last.endsWith("s");
                if (negated) {
                    return new Head(noBase + " " + type, noBase + " " + type,
                            card, many, List.of());
                }
                return new Head(base + " " + type, basePl + " " + type, card, many, List.of());
            }
        }

        // El nombre, en singular o en plural.
        String noun = last;
        boolean many = false;
        if (!NOUNS.containsKey(noun)) {
            for (final Map.Entry<String, Noun> e : NOUNS.entrySet()) {
                if (noun.equals(e.getKey() + "s") || noun.equals(e.getValue().english_plural)) {
                    noun = e.getKey();
                    many = true;
                    break;
                }
            }
        }
        final Noun n = NOUNS.get(noun);
        if (n == null) {
            return null;
        }
        final String sing = NeoText.own(n.key);
        final String plur = NeoText.own(n.key + "s");
        final String gender = NeoText.own(n.key + ".g");
        if (sing == null || plur == null || gender == null) {
            return null;
        }
        final boolean fem = "f".equals(gender.trim());

        // Los adjetivos, de izquierda a derecha y probando primero el mas largo.
        final List<String> adjectives = new ArrayList<>();
        int i = 0;
        final int limit = words.length - 1;
        while (i < limit) {
            String hit = null;
            int len = 0;
            for (int span = 3; span >= 1; span--) {
                if (i + span > limit) {
                    continue;
                }
                final String k = String.join(" ", Arrays.asList(words).subList(i, i + span));
                if (ADJECTIVES.containsKey(k)) {
                    hit = k;
                    len = span;
                    break;
                }
            }
            if (hit == null) {
                return null;
            }
            final String es = NeoText.own(ADJECTIVES.get(hit) + (fem ? ".f" : ".m"));
            if (es == null) {
                return null;
            }
            adjectives.add(es);
            i += len;
        }
        return new Head(sing, plur, fem, many, adjectives);
    }

    // ------------------------------------------------------------------
    // EL VOCABULARIO INGLES.  Lo traducido vive en los ficheros de idioma.
    // ------------------------------------------------------------------
    //
    // Esta mitad NO se traduce y por eso esta aqui y no en un .properties: es
    // la forma en que ESCRIBE EL MOTOR, en ingles y siempre igual, venga del
    // script de una carta o de TargetRestrictions. Lo que cambia de un idioma a
    // otro es solo el lado de alla, que se pide por su clave.

    private static final class Noun {
        private final String key;
        private final String english_plural;

        Noun(final String key, final String englishPlural) {
            this.key = key;
            this.english_plural = englishPlural;
        }
    }

    private static final Map<String, String> VERBS = new LinkedHashMap<>();
    private static final Map<String, Noun> NOUNS = new LinkedHashMap<>();
    private static final Map<String, String> TYPEWORDS = new LinkedHashMap<>();
    private static final Map<String, String> ADJECTIVES = new LinkedHashMap<>();
    private static final Map<String, String> QUALIFIERS = new LinkedHashMap<>();
    private static final Map<String, String> ANY = new LinkedHashMap<>();
    private static final Map<String, Quantifier> QUANTIFIERS = new LinkedHashMap<>();
    private static final List<String> QUALIFIER_ORDER;

    private static final class Quantifier {
        static final Quantifier NONE = new Quantifier(null, false);

        private final String key;
        private final boolean plural;

        Quantifier(final String key, final boolean plural) {
            this.key = key;
            this.plural = plural;
        }

        /** Lo que sustituye al articulo, ya con la apocope del idioma puesta. */
        String word(final boolean fem) {
            if (key == null) {
                return "";
            }
            final String es = NeoText.own(key + (fem ? ".f" : ".m"));
            return es == null ? "" : es;
        }
    }

    private static final class Numeric {
        private final Pattern pattern;
        private final String key;

        Numeric(final String regex, final String key) {
            this.pattern = Pattern.compile(regex);
            this.key = key;
        }
    }

    private static final List<Numeric> NUMERICS = List.of(
            new Numeric("^with power (\\S+) or greater$", "tgt.qual.powerOrGreater"),
            new Numeric("^with power (\\S+) or less$", "tgt.qual.powerOrLess"),
            new Numeric("^with toughness (\\S+) or greater$", "tgt.qual.toughnessOrGreater"),
            new Numeric("^with toughness (\\S+) or less$", "tgt.qual.toughnessOrLess"),
            new Numeric("^with mana value (\\S+) or greater$", "tgt.qual.mvOrGreater"),
            new Numeric("^with mana value (\\S+) or less$", "tgt.qual.mvOrLess"));

    static {
        VERBS.put("Select", "tgt.verb.select");
        VERBS.put("Choose", "tgt.verb.select");
        VERBS.put("Counter", "tgt.verb.counter");

        NOUNS.put("creature", new Noun("tgt.noun.creature", "creatures"));
        NOUNS.put("spell", new Noun("tgt.noun.spell", "spells"));
        NOUNS.put("permanent", new Noun("tgt.noun.permanent", "permanents"));
        NOUNS.put("artifact", new Noun("tgt.noun.artifact", "artifacts"));
        NOUNS.put("enchantment", new Noun("tgt.noun.enchantment", "enchantments"));
        NOUNS.put("land", new Noun("tgt.noun.land", "lands"));
        NOUNS.put("planeswalker", new Noun("tgt.noun.planeswalker", "planeswalkers"));
        NOUNS.put("player", new Noun("tgt.noun.player", "players"));
        NOUNS.put("opponent", new Noun("tgt.noun.opponent", "opponents"));
        NOUNS.put("card", new Noun("tgt.noun.card", "cards"));
        NOUNS.put("ability", new Noun("tgt.noun.ability", "abilities"));
        NOUNS.put("token", new Noun("tgt.noun.token", "tokens"));
        NOUNS.put("instant", new Noun("tgt.noun.instant", "instants"));
        NOUNS.put("sorcery", new Noun("tgt.noun.sorcery", "sorceries"));
        NOUNS.put("battle", new Noun("tgt.noun.battle", "battles"));
        NOUNS.put("Vehicle", new Noun("tgt.noun.vehicle", "Vehicles"));
        NOUNS.put("Equipment", new Noun("tgt.noun.equipment", "Equipment"));
        NOUNS.put("equipment", new Noun("tgt.noun.equipment", "equipment"));
        NOUNS.put("Aura", new Noun("tgt.noun.aura", "Auras"));

        TYPEWORDS.put("creature", "tgt.type.creature");
        TYPEWORDS.put("artifact", "tgt.type.artifact");
        TYPEWORDS.put("enchantment", "tgt.type.enchantment");
        TYPEWORDS.put("land", "tgt.type.land");
        TYPEWORDS.put("permanent", "tgt.type.permanent");
        TYPEWORDS.put("planeswalker", "tgt.type.planeswalker");
        TYPEWORDS.put("instant", "tgt.type.instant");
        TYPEWORDS.put("sorcery", "tgt.type.sorcery");
        TYPEWORDS.put("battle", "tgt.type.battle");
        TYPEWORDS.put("instant or sorcery", "tgt.type.instantOrSorcery");

        ADJECTIVES.put("nonland", "tgt.adj.nonland");
        ADJECTIVES.put("noncreature", "tgt.adj.noncreature");
        ADJECTIVES.put("nonartifact", "tgt.adj.nonartifact");
        ADJECTIVES.put("nontoken", "tgt.adj.nontoken");
        ADJECTIVES.put("nonbasic", "tgt.adj.nonbasic");
        ADJECTIVES.put("nonlegendary", "tgt.adj.nonlegendary");
        ADJECTIVES.put("legendary", "tgt.adj.legendary");
        ADJECTIVES.put("attacking", "tgt.adj.attacking");
        ADJECTIVES.put("blocking", "tgt.adj.blocking");
        ADJECTIVES.put("attacking or blocking", "tgt.adj.attackingOrBlocking");
        ADJECTIVES.put("tapped", "tgt.adj.tapped");
        ADJECTIVES.put("untapped", "tgt.adj.untapped");
        ADJECTIVES.put("artifact", "tgt.adj.artifact");
        ADJECTIVES.put("white", "tgt.adj.white");
        ADJECTIVES.put("blue", "tgt.adj.blue");
        ADJECTIVES.put("black", "tgt.adj.black");
        ADJECTIVES.put("red", "tgt.adj.red");
        ADJECTIVES.put("green", "tgt.adj.green");
        ADJECTIVES.put("nonwhite", "tgt.adj.nonwhite");
        ADJECTIVES.put("nonblue", "tgt.adj.nonblue");
        ADJECTIVES.put("nonblack", "tgt.adj.nonblack");
        ADJECTIVES.put("nonred", "tgt.adj.nonred");
        ADJECTIVES.put("nongreen", "tgt.adj.nongreen");

        QUALIFIERS.put("you control", "tgt.qual.youControl");
        QUALIFIERS.put("you do not control", "tgt.qual.youDontControl");
        QUALIFIERS.put("you own", "tgt.qual.youOwn");
        QUALIFIERS.put("an opponent controls", "tgt.qual.opponentControls");
        QUALIFIERS.put("each opponent controls", "tgt.qual.eachOpponentControls");
        QUALIFIERS.put("that player controls", "tgt.qual.thatPlayerControls");
        QUALIFIERS.put("defending player controls", "tgt.qual.defendingPlayerControls");
        QUALIFIERS.put("damaged player controls", "tgt.qual.damagedPlayerControls");
        QUALIFIERS.put("in your graveyard", "tgt.qual.inYourGraveyard");
        QUALIFIERS.put("in a graveyard", "tgt.qual.inAGraveyard");
        QUALIFIERS.put("in an opponent graveyard", "tgt.qual.inOpponentGraveyard");
        QUALIFIERS.put("from your graveyard", "tgt.qual.fromYourGraveyard");
        QUALIFIERS.put("from a graveyard", "tgt.qual.fromAGraveyard");
        QUALIFIERS.put("from an opponent graveyard", "tgt.qual.fromOpponentGraveyard");
        QUALIFIERS.put("attached to a creature", "tgt.qual.attachedToCreature");
        QUALIFIERS.put("with flying", "tgt.qual.withFlying");
        QUALIFIERS.put("without flying", "tgt.qual.withoutFlying");
        QUALIFIERS.put("with defender", "tgt.qual.withDefender");
        QUALIFIERS.put("with a +1/+1 counter", "tgt.qual.withP1P1");
        QUALIFIERS.put("with a single target", "tgt.qual.withSingleTarget");
        QUALIFIERS.put("that was dealt damage this turn", "tgt.qual.dealtDamageThisTurn");
        QUALIFIERS.put("to distribute damage to", "tgt.qual.toDistributeDamage");
        QUALIFIERS.put("to distribute counters to", "tgt.qual.toDistributeCounters");
        QUALIFIERS.put("to redirect the damage to", "tgt.qual.toRedirectDamage");
        QUALIFIERS.put("to prevent damage to", "tgt.qual.toPreventDamage");
        QUALIFIERS.put("to exile", "tgt.qual.toExile");

        ANY.put("any other target", "tgt.any.other");
        ANY.put("any number of targets", "tgt.any.number");
        ANY.put("any target", "tgt.any.one");

        QUANTIFIERS.put("", new Quantifier(null, false));
        QUANTIFIERS.put("another", new Quantifier("tgt.quant.another", false));
        QUANTIFIERS.put("up to one", new Quantifier("tgt.quant.upToOne", false));
        QUANTIFIERS.put("up to one other", new Quantifier("tgt.quant.upToOneOther", false));
        QUANTIFIERS.put("up to two", new Quantifier("tgt.quant.upToTwo", true));
        QUANTIFIERS.put("up to three", new Quantifier("tgt.quant.upToThree", true));
        QUANTIFIERS.put("up to X", new Quantifier("tgt.quant.upToX", true));
        QUANTIFIERS.put("two", new Quantifier("tgt.quant.two", true));
        QUANTIFIERS.put("three", new Quantifier("tgt.quant.three", true));
        QUANTIFIERS.put("X", new Quantifier("tgt.quant.x", true));
        QUANTIFIERS.put("one or two", new Quantifier("tgt.quant.oneOrTwo", true));
        QUANTIFIERS.put("any number of", new Quantifier("tgt.quant.anyNumber", true));

        // Del mas largo al mas corto: "you do not control" tiene que ganarle a
        // "control", y "in an opponent graveyard" a "in a graveyard".
        final List<String> order = new ArrayList<>(QUALIFIERS.keySet());
        order.sort((a, b) -> Integer.compare(b.length(), a.length()));
        QUALIFIER_ORDER = List.copyOf(order);
    }

    /**
     * TODAS las claves que este analizador puede llegar a pedir.
     *
     * <p>Es lo que hace comprobable una traduccion: si a un idioma le falta una
     * sola, las frases que la usen se quedan en ingles <b>sin decir nada</b> -
     * que es el fallo silencioso de siempre. Con esta lista, {@code TextCheck}
     * puede cantar exactamente cual falta.
     */
    public static List<String> allKeys() {
        final List<String> out = new ArrayList<>();
        out.addAll(VERBS.values());
        out.add("tgt.article.f");
        out.add("tgt.article.m");
        out.add("tgt.or");
        out.add("tgt.fixed.targeted");
        out.add("tgt.fixed.parentTargeted");
        out.add("tgt.fixed.moreCanBeTargeted");
        for (final Noun n : NOUNS.values()) {
            out.add(n.key);
            out.add(n.key + "s");
            out.add(n.key + ".g");
        }
        out.addAll(TYPEWORDS.values());
        out.add("tgt.head.card");
        out.add("tgt.head.cards");
        out.add("tgt.head.cardNon");
        out.add("tgt.head.spell");
        out.add("tgt.head.spells");
        out.add("tgt.head.spellNon");
        for (final String k : ADJECTIVES.values()) {
            out.add(k + ".f");
            out.add(k + ".m");
        }
        out.addAll(QUALIFIERS.values());
        for (final Numeric n : NUMERICS) {
            out.add(n.key);
        }
        for (final Quantifier q : QUANTIFIERS.values()) {
            if (q.key != null) {
                out.add(q.key + ".f");
                out.add(q.key + ".m");
            }
        }
        out.addAll(ANY.values());
        return out.stream().distinct().toList();
    }

    /**
     * Cuantas frases distintas sabe componer, para el comprobador.
     *
     * <p>No es el numero de claves: es lo que de verdad se puede armar, que es
     * lo unico que dice si un idioma esta completo o a medias.
     */
    public static boolean ready() {
        return NeoText.own("tgt.verb.select") != null
                && NeoText.own("tgt.noun.creature") != null
                && NeoText.own("tgt.qual.youControl") != null;
    }

    /** Lo que sabe traducir, en el idioma de ahora. Para el comprobador. */
    public static List<String> sample() {
        final List<String> out = new ArrayList<>();
        for (final String s : List.of(
                "Select target creature you control",
                "Select up to one target artifact or enchantment",
                "Choose target creature card in your graveyard",
                "Select another target attacking creature",
                "Select any target to distribute damage to")) {
            final String t = phrase(s);
            out.add(s + "  ->  " + (t == null ? "(sin traducir)" : t));
        }
        return out;
    }
}
