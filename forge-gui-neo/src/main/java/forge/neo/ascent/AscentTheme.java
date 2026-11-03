package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import forge.card.CardRarity;
import forge.card.CardType;
import forge.deck.CommanderBracketCalculator;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.deck.GeneratedDecks;
import forge.util.MyRandom;

/**
 * <b>El mazo de salida de un comandante que Forge no conoce, con su tribu.</b>
 *
 * <p>Discord, 31-10-2026: con Omnath o Athreos el mazo pegaba con el
 * comandante, pero con Vona, Munda, Verrak o The Valeyard salia <i>"a random
 * mishmash"</i>. Es que la matriz de mazos reales ({@link AscentSynergy}) solo
 * conoce ~1.000 de los ~3.800 comandantes, y a los demas el generador les da
 * noventa y nueve cartas de sus colores y nada mas.
 *
 * <p>Esto le da un tema, y <b>poco</b>, a proposito: el mazo de salida es flojo
 * por diseno (todo el modo es mejorarlo) y lo que se busca es coherencia, no
 * potencia. Por eso:
 * <ul>
 *   <li>El tema son <b>tipos de criatura</b>: los que el comandante nombra en
 *       su texto (Munda dice "Ally") y los suyos propios que son tribus (Vona es
 *       Vampire). Las clases genericas (Human, Warrior, Wizard...) no cuentan
 *       si no las nombra. Sin tema, no se toca nada.</li>
 *   <li>Solo se cambian <b>criaturas por criaturas</b> del mismo coste (±1),
 *       como mucho {@link #MAX_SWAPS}: tierras, quitar y rampa siguen igual, y
 *       la curva tambien.</li>
 *   <li>Solo <b>comunes e infrecuentes</b>, legales, de su identidad, y cada
 *       una pasa por la calculadora de bracket del motor
 *       ({@code CommanderBracketCalculator}, la misma que usa el generador):
 *       la que subiria el mazo por encima del techo de la run, no entra.</li>
 * </ul>
 *
 * <p>Sortea con {@code MyRandom}, recorriendo las cartas en orden fijo: con la
 * semilla de un reto, el mismo mazo.
 */
public final class AscentTheme {

    /** Cuantas criaturas se cambian como mucho, de las ~60 cartas. */
    static final int MAX_SWAPS = 12;

    /** Y de esos, cuantos hechizos que no son criatura como mucho. */
    static final int MAX_SPELL_SWAPS = 6;

    /** Lo mas caro que entra: un mazo de salida no vive de bombas. */
    private static final int MAX_CMC = 6;

    /**
     * Tipos que son <b>clase</b> y no tribu: los lleva media base de cartas y
     * por si solos no dicen de que va un mazo. Cuentan solo si el comandante
     * los nombra en su texto ("los Soldier que controlas").
     */
    private static final Set<String> GENERIC = Set.of(
            "Human", "Warrior", "Wizard", "Soldier", "Cleric", "Rogue", "Shaman", "Knight",
            "Advisor", "Noble", "Artificer", "Druid", "Monk", "Scout", "Peasant", "Citizen",
            "Berserker", "Assassin", "Archer", "Mercenary", "Warlock", "Bard", "Ranger",
            "Detective", "Performer", "Coward", "Spellshaper", "Mystic", "Nomad", "Rebel",
            "Survivor", "Employee", "Pilot", "Avatar", "Child", "Hero", "Villain", "Time Lord");

    private AscentTheme() {
    }

    /**
     * Los tipos de criatura que son el tema de este comandante (y su
     * companero). Vacio si no tiene.
     */
    public static Set<String> themeOf(final PaperCard commander, final PaperCard partner) {
        return themeOf(commander, partner, true);
    }

    /**
     * Con {@code own} a false, solo los tipos que NOMBRA: un comandante con otro
     * tema (Codie copia hechizos preparados) no es un mazo de su propio tipo
     * (Construct). Lo decide {@link CommanderTheme#hasMechanic}.
     */
    public static Set<String> themeOf(final PaperCard commander, final PaperCard partner, final boolean own) {
        final Set<String> out = new LinkedHashSet<>();
        for (final PaperCard c : new PaperCard[] {commander, partner}) {
            if (c == null) {
                continue;
            }
            // Los que nombra: "Ally", "Vampires", "Elves"...
            final String text = c.getRules().getOracleText();
            if (text != null && !text.isEmpty()) {
                for (final String t : CardType.getAllCreatureTypes()) {
                    if (mentions(text, t)) {
                        out.add(t);
                    }
                }
            }
            // Los suyos, si son tribu. Un cambiaformas los tiene todos: esos no.
            final CardType type = c.getRules().getType();
            if (own && !type.hasAllCreatureTypes()) {
                for (final String t : type.getCreatureTypes()) {
                    if (!GENERIC.contains(t)) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    /** Si el texto nombra ese tipo, en singular o en plural, como palabra entera. */
    static boolean mentions(final String text, final String type) {
        final String plural = CardType.getPluralType(type);
        final String regex = "\\b(" + Pattern.quote(type) + "|" + Pattern.quote(type) + "s"
                + (plural.equals(type) ? "" : "|" + Pattern.quote(plural)) + ")\\b";
        return Pattern.compile(regex).matcher(text).find();
    }

    /**
     * Cambia hasta {@link #MAX_SWAPS} cartas fuera de tema por otras del tema:
     * criatura por criatura y, como mucho {@link #MAX_SPELL_SWAPS}, hechizo por
     * hechizo. Toca {@code deck} en su sitio y devuelve cuantas ha cambiado.
     */
    public static int apply(final Deck deck, final PaperCard commander, final PaperCard partner,
                            final int maxBracket) {
        final Set<String> theme = themeOf(commander, partner,
                !CommanderTheme.hasMechanic(commander) && !CommanderTheme.hasMechanic(partner));
        // Y lo que no es tribu (Discord, 02-11-2026: Codie y las de preparar):
        // lo lee CommanderTheme de la carta: criaturas y tambien hechizos.
        final Predicate<PaperCard> more = c -> CommanderTheme.matches(commander, c)
                || CommanderTheme.matches(partner, c);
        if (theme.isEmpty() && !CommanderTheme.hasTheme(commander) && !CommanderTheme.hasTheme(partner)) {
            return 0;
        }
        final List<PaperCard> commanders = new ArrayList<>();
        commanders.add(commander);
        if (partner != null) {
            commanders.add(partner);
        }
        final Predicate<PaperCard> identity =
                DeckFormat.Commander.isLegalCardForCommanderPredicate(commanders);

        // Lo que ya lleva, por nombre: ni repetir (es singleton) ni quitar lo del tema.
        final Set<String> inDeck = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        final List<PaperCard> offTheme = new ArrayList<>();
        // Y los hechizos que no son criatura, para lo que pide la carta y no es
        // criatura (Discord, 03-11-2026: Tam, the Possibility salia en Ascenso
        // sin planeswalkers; en la Quest si). Se cambian hechizo por hechizo.
        final List<PaperCard> offThemeSpells = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final PaperCard c = e.getKey();
            inDeck.add(c.getName());
            final CardType t = c.getRules().getType();
            if (t.isLand() || onTheme(c, theme, more)) {
                continue;
            }
            for (int i = 0; i < e.getValue(); i++) {
                (t.isCreature() ? offTheme : offThemeSpells).add(c);
            }
        }
        if (offTheme.isEmpty() && offThemeSpells.isEmpty()) {
            return 0;
        }

        // Candidatas, en orden fijo y luego barajadas con el azar de la run.
        final List<PaperCard> candidates = new ArrayList<>();
        for (final PaperCard c : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (fits(c, theme, more, inDeck, identity) || fitsSpell(c, more, inDeck, identity)) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) {
            return 0;
        }
        candidates.sort(Comparator.comparing(PaperCard::getName));
        Collections.shuffle(candidates, MyRandom.getRandom());

        // El bracket, como lo lleva el generador: los nombres del mazo y, por
        // cada candidata, si con ella sigue dentro del techo.
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(CommanderBracketCalculator.getCardNames(commander));
        names.addAll(CommanderBracketCalculator.getCardNames(partner));
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            names.addAll(CommanderBracketCalculator.getCardNames(e.getKey()));
        }

        int swaps = 0;
        int spellSwaps = 0;
        for (final PaperCard in : candidates) {
            if (swaps >= MAX_SWAPS) {
                break;
            }
            final boolean creature = in.getRules().getType().isCreature();
            if (!creature && spellSwaps >= MAX_SPELL_SWAPS) {
                continue;
            }
            final List<PaperCard> pool = creature ? offTheme : offThemeSpells;
            final PaperCard out = sameCost(pool, in);
            if (out == null) {
                continue;
            }
            final Set<String> next = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            next.addAll(names);
            next.addAll(CommanderBracketCalculator.getCardNames(in));
            if (CommanderBracketCalculator.calculate(next).getBracket() > maxBracket) {
                continue;
            }
            names = next;
            pool.remove(out);
            if (!creature) {
                spellSwaps++;
            }
            deck.getMain().remove(out, 1);
            deck.getMain().add(in);
            inDeck.add(in.getName());
            swaps++;
        }
        return swaps;
    }

    private static boolean onTheme(final PaperCard c, final Set<String> theme, final Predicate<PaperCard> more) {
        if (more.test(c)) {
            return true;
        }
        for (final String t : c.getRules().getType().getCreatureTypes()) {
            if (theme.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean fits(final PaperCard c, final Set<String> theme, final Predicate<PaperCard> more,
                                final Set<String> inDeck, final Predicate<PaperCard> identity) {
        final CardType type = c.getRules().getType();
        if (!type.isCreature() || type.isLand()) {
            return false;
        }
        // Una cambiaformas es de todas las tribus: valdria para cualquier
        // tema, y llenaria todos los mazos de las mismas.
        if (type.hasAllCreatureTypes() || !onTheme(c, theme, more)) {
            return false;
        }
        final CardRarity r = c.getRarity();
        if (r != CardRarity.Common && r != CardRarity.Uncommon) {
            return false;
        }
        if (c.getRules().getManaCost().getCMC() > MAX_CMC || inDeck.contains(c.getName())) {
            return false;
        }
        if (c.getName().startsWith("A-") || c.getRules().getAiHints().getRemAIDecks()
                || GeneratedDecks.notForDecks(c)) {
            return false;
        }
        return DeckFormat.Commander.isLegalCard(c) && identity.test(c);
    }

    /**
     * Un hechizo que no es criatura y es del tema que lee {@link CommanderTheme}
     * (no las tribus: un "Elf" no es un instantaneo). Comun o infrecuente en
     * ALGUNA impresion (los planeswalkers infrecuentes de War of the Spark se
     * reimprimen como raros), y lo demas igual que {@link #fits}.
     */
    private static boolean fitsSpell(final PaperCard c, final Predicate<PaperCard> more,
                                     final Set<String> inDeck, final Predicate<PaperCard> identity) {
        final CardType type = c.getRules().getType();
        if (type.isCreature() || type.isLand() || !more.test(c)) {
            return false;
        }
        if (c.getRules().getManaCost().getCMC() > MAX_CMC || inDeck.contains(c.getName())) {
            return false;
        }
        if (c.getName().startsWith("A-") || c.getRules().getAiHints().getRemAIDecks()
                || GeneratedDecks.notForDecks(c)) {
            return false;
        }
        boolean cheap = false;
        for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards(c.getName())) {
            if (p.getRarity() == CardRarity.Common || p.getRarity() == CardRarity.Uncommon) {
                cheap = true;
                break;
            }
        }
        return cheap && DeckFormat.Commander.isLegalCard(c) && identity.test(c);
    }

    /** La de fuera de tema que cuesta lo mismo; si no hay, una que cueste uno de diferencia. */
    private static PaperCard sameCost(final List<PaperCard> offTheme, final PaperCard in) {
        final int cmc = in.getRules().getManaCost().getCMC();
        PaperCard near = null;
        for (final PaperCard c : offTheme) {
            final int d = Math.abs(c.getRules().getManaCost().getCMC() - cmc);
            if (d == 0) {
                return c;
            }
            if (d == 1 && near == null) {
                near = c;
            }
        }
        return near;
    }

    /** Para el registro: "Vampire, Ally". */
    static String describe(final Set<String> theme) {
        return theme.isEmpty() ? "-" : String.join(", ", theme);
    }
}
