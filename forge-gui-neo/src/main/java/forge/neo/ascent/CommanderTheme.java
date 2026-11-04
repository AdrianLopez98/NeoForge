package forge.neo.ascent;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import forge.card.CardRules;
import forge.card.CardSplitType;
import forge.card.CardType;
import forge.card.DeckHints;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * <b>De que va un comandante que la matriz no conoce</b>, leido de su propia
 * carta.
 *
 * <p>Discord, 02-11-2026: <i>"picking the new Codie as your commander will just
 * throw a random series of cards in ... none of them have Prepared spells"</i>,
 * y lo mismo con Tam, the Possibility y los planeswalkers. {@link AscentSynergy}
 * sale de mazos reales, y de un comandante recien salido no hay ninguno: el mazo
 * salia de los staples de su color, sin tema, y el comandante se quedaba de
 * adorno.
 *
 * <p>Tres pistas, todas de la carta y ninguna inventada:
 * <ul>
 * <li>sus {@code DeckHints} / {@code DeckNeeds} — lo que el propio script dice
 *     que quiere al lado (lo usa el generador de limitado de Forge);</li>
 * <li>los <b>tipos que nombra como suyos</b> en el oracle: "planeswalker spells
 *     you cast", "Elves you control", "instant or sorcery card"... — no basta
 *     con nombrarlos: "destroy target artifact" no es un mazo de artefactos;</li>
 * <li>"prepared" / "prepare": las cartas con hechizo de preparar
 *     ({@link CardSplitType#Prepare}).</li>
 * </ul>
 * El peso de cada carta es lo que se juega en Commander
 * ({@link AscentSynergy#popularity()}), y 1 si no sale ahi: el mismo orden al
 * azar con peso que el resto del pozo. Vacio si la carta no da ninguna pista.
 */
public final class CommanderTheme {

    private CommanderTheme() {
    }

    /** Tipos de carta que un comandante puede pedir por su nombre. */
    private static final String[] CARD_TYPES = {
            "Planeswalker", "Artifact", "Enchantment", "Equipment", "Aura", "Vehicle",
            "Saga", "Instant", "Sorcery", "Battle", "Legendary"};

    private static final Map<String, List<Map.Entry<PaperCard, Integer>>> CACHE = new HashMap<>();

    /** Las cartas del tema de este comandante, con su peso. Vacio si no hay pistas. */
    public static synchronized List<Map.Entry<PaperCard, Integer>> poolOf(final PaperCard commander) {
        if (commander == null) {
            return List.of();
        }
        // CON SU PROPIO AZAR (seedcheck, 04-11-2026: la misma semilla de Ascenso
        // con Krenko daba otro mazo la segunda vez). Leer el tema gasta tiradas
        // del azar compartido (MyRandom: la base de cartas elige impresiones),
        // y solo la PRIMERA vez, que es la que lo calcula: con una semilla
        // puesta, la run de despues ya no era la misma. El tema no depende del
        // azar; la run, si. Asi la primera y las demas gastan lo mismo: nada.
        final List<Map.Entry<PaperCard, Integer>> known = CACHE.get(commander.getName());
        if (known != null) {
            return known;
        }
        final List<Map.Entry<PaperCard, Integer>> out = ownRandom(commander, () -> compute(commander));
        CACHE.put(commander.getName(), out);
        return out;
    }

    /**
     * Lo que solo se calcula la primera vez, con un azar suyo (ver
     * {@link #poolOf}): el compartido se deja como estaba.
     */
    private static <T> T ownRandom(final PaperCard commander, final java.util.function.Supplier<T> work) {
        final java.util.Random before = forge.util.MyRandom.getRandom();
        forge.util.MyRandom.setRandom(new java.util.Random(commander.getName().hashCode()));
        try {
            return work.get();
        } finally {
            forge.util.MyRandom.setRandom(before);
        }
    }

    /** Lo que se ha leido de la carta, para el registro y el comprobador. */
    public static List<String> clues(final PaperCard commander) {
        final List<String> out = new ArrayList<>();
        if (commander != null) {
            build(commander, out);
        }
        return out;
    }

    private static final Map<String, Predicate<CardRules>> FILTERS = new HashMap<>();

    /**
     * Si esta carta es del tema de este comandante. Lo usa {@link AscentTheme}
     * para que Ascenso cuente tambien lo que no es tribu (las de preparar).
     */
    public static synchronized boolean matches(final PaperCard commander, final PaperCard card) {
        if (commander == null || card == null) {
            return false;
        }
        final Predicate<CardRules> p = FILTERS.computeIfAbsent(commander.getName(),
                n -> {
                    final Predicate<CardRules> b = ownRandom(commander,
                            () -> build(commander, new ArrayList<>()));
                    return b == null ? r -> false : b;
                });
        return p.test(card.getRules());
    }

    /** Si la carta da alguna pista. */
    public static boolean hasTheme(final PaperCard commander) {
        return commander != null && !clues(commander).isEmpty();
    }

    /**
     * Si pide algo que no es una tribu (sus pistas, preparar o un tipo de
     * carta). Entonces sus propios tipos de criatura no son el tema: Codie es
     * un Construct y va de hechizos preparados.
     */
    public static boolean hasMechanic(final PaperCard commander) {
        if (commander == null) {
            return false;
        }
        final CardRules rules = commander.getRules();
        final String lower = rules.getOracleText() == null ? ""
                : rules.getOracleText().toLowerCase(Locale.ROOT);
        final DeckHints hints = rules.getAiHints().getDeckHints();
        final DeckHints needs = rules.getAiHints().getDeckNeeds();
        if (hints != null && hints.isValid() || needs != null && needs.isValid() || prepares(lower)) {
            return true;
        }
        for (final String type : CARD_TYPES) {
            if (asOwn(lower, type.toLowerCase(Locale.ROOT), plural(type).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean prepares(final String lower) {
        return lower.contains("prepared") || Pattern.compile("\\bprepare\\b").matcher(lower).find();
    }

    private static List<Map.Entry<PaperCard, Integer>> compute(final PaperCard commander) {
        final List<String> clues = new ArrayList<>();
        final Predicate<CardRules> wanted = build(commander, clues);
        if (wanted == null) {
            return List.of();
        }
        final Map<String, Integer> weight = new HashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : AscentSynergy.popularity()) {
            weight.merge(e.getKey().getName(), e.getValue(), Math::max);
        }
        final List<Map.Entry<PaperCard, Integer>> out = new ArrayList<>();
        for (final PaperCard c : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (c.getName().equals(commander.getName()) || !wanted.test(c.getRules())) {
                continue;
            }
            out.add(new AbstractMap.SimpleImmutableEntry<>(c, weight.getOrDefault(c.getName(), 1)));
        }
        System.out.println("[tema] " + commander.getName() + ": " + clues + " -> " + out.size() + " cartas");
        return out;
    }

    /** El filtro de las cartas que pegan, o null si la carta no da pistas. */
    private static Predicate<CardRules> build(final PaperCard commander, final List<String> clues) {
        final CardRules rules = commander.getRules();
        final List<Predicate<CardRules>> any = new ArrayList<>();
        final String oracle = rules.getOracleText() == null ? "" : rules.getOracleText();
        final String lower = oracle.toLowerCase(Locale.ROOT);

        final DeckHints hints = rules.getAiHints().getDeckHints();
        final DeckHints needs = rules.getAiHints().getDeckNeeds();
        for (final DeckHints h : new DeckHints[] {hints, needs}) {
            if (h != null && h.isValid()) {
                clues.add("hints");
                // Se filtra UNA vez sobre todas: DeckHints trabaja con listas.
                final Set<String> names = new java.util.HashSet<>();
                for (final PaperCard p : h.filter(FModel.getMagicDb().getCommonCards().getUniqueCards())) {
                    names.add(p.getName());
                }
                any.add(r -> names.contains(r.getName()));
            }
        }

        if (prepares(lower)) {
            clues.add("prepare");
            any.add(r -> r.getSplitType() == CardSplitType.Prepare);
        }

        for (final String type : CARD_TYPES) {
            if (asOwn(lower, type.toLowerCase(Locale.ROOT), plural(type).toLowerCase(Locale.ROOT))) {
                clues.add(type);
                if ("Instant".equals(type) || "Sorcery".equals(type)) {
                    any.add(r -> r.getType().isInstant() || r.getType().isSorcery());
                } else {
                    any.add(r -> r.getType().hasStringType(type));
                }
            }
        }

        // Las tribus, con la regla de Ascenso: las que nombra y las suyas que
        // no son una clase generica (estas, solo si no pide otra cosa). Una
        // cambiaformas no cuenta como de todas.
        final Set<String> tribes = new LinkedHashSet<>(AscentTheme.themeOf(commander, null, !hasMechanic(commander)));
        for (final String t : tribes) {
            clues.add(t);
            any.add(r -> !r.getType().hasAllCreatureTypes() && r.getType().hasCreatureType(t));
        }

        if (any.isEmpty()) {
            return null;
        }
        return r -> {
            for (final Predicate<CardRules> p : any) {
                if (p.test(r)) {
                    return true;
                }
            }
            return false;
        };
    }

    /** El plural de Forge, y si no lo sabe (Planeswalker, Artifact...), con una s. */
    private static String plural(final String type) {
        final String p = CardType.getPluralType(type);
        return p.equals(type) ? type + "s" : p;
    }

    /**
     * Si el texto pide ese tipo <b>como suyo</b>: "X spell(s)", "X(s) you
     * control", "X card(s)", "X permanent(s)", "another X", "each X". Con la
     * palabra entera, para que "Elf" no salga de "Elfhame".
     */
    private static boolean asOwn(final String text, final String singular, final String plural) {
        final String w = "(?:" + Pattern.quote(singular) + "|" + Pattern.quote(plural) + ")";
        return Pattern.compile("(?:\\b" + w + "\\b(?: or [a-z]+)? (?:spells?|cards?|permanents?|you control|you cast)"
                + "|\\b(?:another|each|other) " + w + "\\b"
                + "|\\b" + w + " types\\b)").matcher(text).find();
    }
}
