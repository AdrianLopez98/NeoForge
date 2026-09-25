package forge.neo.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.deck.CardPool;
import forge.deck.CommanderBracketCalculator;
import forge.deck.CommanderDeckGenerator;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.deck.DeckgenUtil;
import forge.gamemodes.limited.CardThemedCommanderDeckBuilder;
import forge.gamemodes.quest.QuestEventCommanderDuel;
import forge.gamemodes.quest.QuestEventCommanderDuelManager;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.gamemodes.quest.QuestEventDuel;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.ascent.AscentSynergy;
import forge.util.MyRandom;

/**
 * Los rivales de una Quest de Commander, <b>montados por nosotros</b> en vez de
 * por {@code QuestEventCommanderDuelManager.generateDuels()}.
 *
 * <h2>El fallo de Forge que envuelve (24-09-2026)</h2>
 *
 * <p>Reportado en itch.io: <i>"even at the last level they were still using
 * cards only useful as bookmarks"</i>. Y tenia razon, por dos motivos:
 *
 * <ol>
 *   <li><b>El mazo "facil" son 400 cartas al azar del color.</b> Forge lo pide
 *       con {@code isCardGen = false}: baraja todo lo legal de la identidad del
 *       comandante y el montador elige por curva. De ahi los artefactos de
 *       "paga 7, gira: roba una carta".</li>
 *   <li><b>El mazo "experto" no se usa nunca.</b> Forge monta la lista buena
 *       ({@code expertCommanderDecks}, con la matriz de mazos reales), pero
 *       {@code getExpertGenDeck()} la busca en {@code commanderDuels}, que es la
 *       lista FACIL. O sea que las cartas "mejores" de medio y dificil, y el
 *       mazo entero de experto, salen del mismo generador al azar: subir de
 *       nivel solo cambia la etiqueta.</li>
 * </ol>
 *
 * <p>Por la regla de oro no se toca: {@link NeoQuest#duels()} llama aqui en vez
 * de al motor cuando la Quest es de Commander. Si algun dia lo arreglan rio
 * arriba, esto sigue valiendo igual.
 *
 * <h2>Como se monta cada nivel</h2>
 *
 * <p>Todos salen de la matriz de sinergias de Forge ({@link AscentSynergy}):
 * <b>cartas que la gente juega de verdad con ese comandante</b>, y cuando no
 * llegan, las que se juegan en cualquier mazo de su color (los <i>staples</i>).
 * Lo que cambia de un nivel a otro es el techo:
 *
 * <table>
 *   <tr><th>Facil</th><td>solo comunes e infrecuentes, bracket 2</td></tr>
 *   <tr><th>Medio</th><td>hasta raras (sin miticas), bracket 2</td></tr>
 *   <tr><th>Dificil</th><td>cualquier rareza, bracket 3</td></tr>
 *   <tr><th>Experto</th><td>el mazo experto de Forge, el que tenia que salir,
 *       con el bracket de las preferencias (de fabrica, sin techo)</td></tr>
 * </table>
 *
 * <p>La rareza de una carta es la <b>mas baja de todas sus impresiones</b>: Sol
 * Ring es infrecuente aunque tenga ediciones miticas, y eso es lo que el
 * jugador espera de "cartas de menos rareza". El bracket lo calcula el motor
 * ({@code CommanderBracketCalculator}), igual que hace Forge con su
 * preferencia de bracket maximo: nosotros no decidimos que carta es fuerte.
 *
 * <p><b>Solo salen comandantes que la matriz conoce.</b> De los ~10.800
 * legales la mayoria no esta, y a esos solo se les puede montar el mazo al
 * azar — justo lo que se queria quitar. Si la matriz no carga, se devuelve lo
 * de Forge tal cual: peor un mazo flojo que una Quest sin rivales.
 */
public final class NeoCommanderDuels {

    private NeoCommanderDuels() {
    }

    /** Cuantos duelos se ofrecen: los mismos cuatro que Forge. */
    private static final int DUELS = 4;

    /** Cuantas cartas del pozo del comandante se consideran como mucho. */
    private static final int OWN_POOL = 220;

    /** Y cuantas en total, contando los staples de relleno. */
    private static final int CANDIDATES = 320;

    /**
     * Los cuatro duelos de hoy, como los devolveria {@code generateDuels()}: el
     * ultimo es el "rival sorpresa", con la dificultad escondida.
     */
    public static List<QuestEventDuel> generate(final QuestEventCommanderDuelManager forge) {
        final List<PaperCard> known = new ArrayList<>();
        for (final QuestEventDuel d : forge.getAllDuels()) {
            if (d instanceof QuestEventCommanderDuel c
                    && c.getDeckProxy() instanceof CommanderDeckGenerator g
                    && AscentSynergy.knows(g.getPaperCard())) {
                known.add(g.getPaperCard());
            }
        }
        if (known.size() < DUELS) {
            System.out.println("[quest] la matriz no conoce comandantes; rivales de Forge");
            return forge.generateDuels();
        }

        final QuestEventDifficulty tier = NeoQuest.tier();
        Collections.shuffle(known, MyRandom.getRandom());
        final List<QuestEventDuel> out = new ArrayList<>();
        for (final PaperCard cmd : known) {
            if (out.size() == DUELS) {
                break;
            }
            final Deck deck = deckFor(cmd, tier);
            if (deck == null) {
                continue;
            }
            final QuestEventCommanderDuel duel = new QuestEventCommanderDuel();
            duel.setDescription("Generated " + cmd.getName() + " commander deck.");
            duel.setName(cmd.getName());
            duel.setTitle(cmd.getName());
            duel.setOpponentName(cmd.getName());
            duel.setDifficulty(tier);
            duel.setEventDeck(deck);
            out.add(duel);
        }
        if (out.size() < DUELS) {
            return forge.generateDuels();
        }

        // El cuarto es el "rival sorpresa", como en Forge: mismo mazo, sin
        // decir quien es ni su dificultad.
        final QuestEventCommanderDuel last = (QuestEventCommanderDuel) out.get(DUELS - 1);
        last.setTitle("Random Opponent");
        last.setShowDifficulty(false);
        last.setDescription("Fight a random generated commander opponent.");
        last.setIsRandomMatch(true);
        return out;
    }

    /**
     * El mazo de un comandante para ese nivel, o {@code null} si no ha salido
     * (el que llama prueba con el siguiente comandante).
     */
    public static Deck deckFor(final PaperCard cmd, final QuestEventDifficulty tier) {
        try {
            if (tier == QuestEventDifficulty.EXPERT) {
                // Lo que Forge queria dar y no daba: su generador bueno, con
                // el bracket maximo de las preferencias.
                try {
                    final Deck d = DeckgenUtil.generateRandomCommanderDeck(
                            cmd, DeckFormat.Commander, true, true);
                    if (d != null && d.getMain().countAll() >= 90) {
                        return d;
                    }
                } catch (final RuntimeException e) {
                    System.out.println("[quest] el generador experto ha fallado con "
                            + cmd.getName() + ": " + e);
                }
                return build(cmd, CardRarity.MythicRare,
                        FModel.getPreferences().getPrefInt(FPref.DECKGEN_MAXIMUM_COMMANDER_BRACKET));
            }
            switch (tier) {
                case HARD:
                    return build(cmd, CardRarity.MythicRare, 3);
                case MEDIUM:
                    return build(cmd, CardRarity.Rare, 2);
                default:
                    return build(cmd, CardRarity.Uncommon, 2);
            }
        } catch (final RuntimeException e) {
            System.out.println("[quest] no se ha podido montar el mazo de "
                    + cmd.getName() + ": " + e);
            return null;
        }
    }

    /**
     * Monta el mazo con el montador de Forge, pasandole solo cartas de la
     * matriz: primero las de su comandante, despues los staples de su color.
     * Las dos tandas se ordenan al azar <b>con peso</b> (lo que mas se juega sale
     * antes, sin ser siempre lo mismo), que es como lo hace el propio Forge
     * ({@code prepareWeightedRandomizedCardPool}).
     */
    static Deck build(final PaperCard cmd, final CardRarity maxRarity, final int maxBracket) {
        final DeckFormat format = DeckFormat.Commander;
        final ColorSet identity = cmd.getRules().getColorIdentity();
        final Set<String> seen = new HashSet<>();
        seen.add(cmd.getName());

        final List<PaperCard> candidates = new ArrayList<>();
        addWeighted(candidates, seen, AscentSynergy.poolOf(cmd), OWN_POOL,
                format, identity, maxRarity);
        addWeighted(candidates, seen, AscentSynergy.popularity(), CANDIDATES,
                format, identity, maxRarity);

        final List<PaperCard> pool = limitToBracket(candidates, cmd, maxBracket);
        final CardThemedCommanderDeckBuilder gen =
                new CardThemedCommanderDeckBuilder(cmd, null, pool, true, format);
        gen.setSingleton(true);
        gen.setUseArtifacts(!FModel.getPreferences().getPrefBoolean(FPref.DECKGEN_ARTIFACTS));
        final CardPool cards = gen.getDeck(format.getMainRange().getMaximum(), true);

        final Deck deck = new Deck("Generated Commander deck (" + cmd.getName() + ")");
        deck.setDirectory("generated/commander");
        deck.getMain().addAll(cards);
        deck.getOrCreate(DeckSection.Commander).add(cmd);
        return deck;
    }

    /** Anyade del pozo lo que pasa los filtros, en orden al azar con peso, hasta {@code upTo}. */
    private static void addWeighted(final List<PaperCard> out, final Set<String> seen,
            final List<Map.Entry<PaperCard, Integer>> source, final int upTo,
            final DeckFormat format, final ColorSet identity, final CardRarity maxRarity) {
        final List<Map.Entry<PaperCard, Integer>> shuffled = new ArrayList<>(source);
        final Map<Map.Entry<PaperCard, Integer>, Double> key = new IdentityHashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : shuffled) {
            key.put(e, Math.log(MyRandom.getRandom().nextDouble()) / Math.max(1, e.getValue()));
        }
        shuffled.sort((a, b) -> Double.compare(key.get(b), key.get(a)));
        for (final Map.Entry<PaperCard, Integer> e : shuffled) {
            if (out.size() >= upTo) {
                return;
            }
            final PaperCard c = e.getKey();
            if (seen.contains(c.getName())
                    || !format.isLegalCard(c)
                    || !c.getRules().getColorIdentity().hasNoColorsExcept(identity)
                    || c.getRules().getAiHints().getRemAIDecks()
                    || lowestRarity(c).ordinal() > maxRarity.ordinal()) {
                continue;
            }
            seen.add(c.getName());
            out.add(c);
        }
    }

    /**
     * Lo mismo que {@code DeckgenUtil.limitCardsToCommanderBracket}, que es
     * privado: se van metiendo cartas mientras el mazo que resultaria no pase
     * del bracket. Solo hace algo entre 1 y 3, igual que alli.
     */
    private static List<PaperCard> limitToBracket(final List<PaperCard> cards,
            final PaperCard cmd, final int maxBracket) {
        if (maxBracket < 1 || maxBracket >= 4) {
            return cards;
        }
        final List<PaperCard> out = new ArrayList<>();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(CommanderBracketCalculator.getCardNames(cmd));
        for (final PaperCard c : cards) {
            final Set<String> next = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            next.addAll(names);
            next.addAll(CommanderBracketCalculator.getCardNames(c));
            if (CommanderBracketCalculator.calculate(next).getBracket() <= maxBracket) {
                out.add(c);
                names = next;
            }
        }
        return out;
    }

    /**
     * La rareza mas baja con la que se ha impreso la carta. Las impresiones
     * "especiales" (Secret Lair, promos) no cuentan; si solo tiene de esas,
     * cuenta como rara. Una basica cuenta como comun.
     */
    static CardRarity lowestRarity(final PaperCard card) {
        return RARITY.computeIfAbsent(card.getName(), name -> {
            CardRarity best = null;
            for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards(name)) {
                CardRarity r = p.getRarity();
                if (r == CardRarity.BasicLand) {
                    r = CardRarity.Common;
                }
                if (r == null || r.ordinal() > CardRarity.MythicRare.ordinal()) {
                    continue;
                }
                if (best == null || r.ordinal() < best.ordinal()) {
                    best = r;
                }
            }
            return best == null ? CardRarity.Rare : best;
        });
    }

    private static final Map<String, CardRarity> RARITY = new ConcurrentHashMap<>();
}
