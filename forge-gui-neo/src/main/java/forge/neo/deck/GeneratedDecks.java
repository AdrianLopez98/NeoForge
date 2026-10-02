package forge.neo.deck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import forge.deck.CardPool;
import forge.deck.CommanderBracketCalculator;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.deck.DeckgenUtil;
import forge.game.GameType;
import forge.gamemodes.limited.CardThemedCommanderDeckBuilder;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.neo.ascent.AscentSynergy;
import forge.util.MyRandom;

/**
 * Los mazos de Commander que monta el generador de Forge, sin las cartas que
 * el propio formato no deja meter.
 *
 * <p><b>Por que existe.</b> {@code DeckgenUtil.generateRandomCommanderDeck}
 * sin {@code isCardGen} baraja el pozo entero de la identidad del comandante
 * con {@code DeckFormat.isLegalCardPredicate}, que mira la lista de prohibidas
 * pero <b>no</b> el limite de copias que trae la propia carta
 * ({@code K:DeckLimit}). Y hay una con limite cero: <b>Gleemox</b>, la broma
 * de los promos de MTGO — <i>"This card is banned."</i>,
 * {@code K:DeckLimit:0}. Es incolora, asi que cabe con cualquier comandante:
 * medido el 02-10-2026, <b>3 de cada 300</b> mazos al azar la traian (y 2 de
 * 200 con un comandante monoblanco, que tiene menos pozo donde diluirla). El
 * motor la da luego por ilegal ({@code "must not contain more than 0 copies
 * of the card Gleemox"}), pero nadie se lo pregunta a un rival: la partida
 * empezaba igual y la IA jugaba con un Mox gratis que dice que esta prohibido.
 * Es lo que hizo fallar {@code deckcheck} con Brad Boimler.
 *
 * <p>No se toca el generador (regla de oro): se limpia lo que devuelve. Lo que
 * sobra se cambia por <b>la basica que el mazo ya lleva mas</b> — la misma
 * impresion, para no colar otra edicion en una run de Ascenso con expansiones
 * elegidas — y el mazo se queda en su tamaño. Solo si no trae ninguna se usa
 * Wastes, que es lo que pone el Adventure de Forge para rellenar
 * ({@code DuelScene.PLACEHOLDER_MAIN}): incolora y basica, vale con cualquier
 * comandante (si el formato la deja; si no, la carta sale sin sustituta).
 *
 * <p>Solo toca el <b>principal</b>, que es lo que monta el generador, y solo
 * cuenta lo que hay fuera de el si se le pasa:
 * {@link #fixCopyLimits(Deck, DeckFormat, CardPool)}. Es lo que usa "Generar
 * mazo" del constructor, con la zona de mando y el banquillo del jugador, que
 * el generador no conoce. ⚠️ Y en ese mismo camino — Oathbreaker con
 * {@code isCardGen=true} — el generador repite tambien <b>su propia</b> zona
 * de mando: medido el 02-10-2026, 68 de 200 mazos de Arlinn Kord traian en el
 * principal el planeswalker o el hechizo que el mismo habia elegido. En
 * Commander (800 mazos, con y sin {@code isCardGen}) y en el "Generame uno" de
 * Oathbreaker, Brawl y Tiny Leaders (600), ni una vez. Un sitio nuevo que
 * genere Oathbreaker con {@code isCardGen=true} y se quede el mazo entero
 * tiene que pasarle su propia zona de mando.
 *
 * <p>El camino de {@code isCardGen=true} saca las cartas de mazos de verdad y
 * no la trajo ninguna vez (0 de 150 con Krenko), pero pasar por aqui no cuesta
 * nada: si no hay nada de mas, el mazo no se toca.
 *
 * <p>Y un caso que no se arregla limpiando: con un comandante que admite
 * companero de mando el generador monta para <b>otra</b> zona de mando. Eso
 * no se lava despues, se monta de otra forma: {@link #forCommandZone}.
 */
public final class GeneratedDecks {

    private GeneratedDecks() {
    }

    /** La basica de reserva, si el mazo no trae ninguna. */
    private static final String FALLBACK_BASIC = "Wastes";

    /**
     * "Genérame uno": comandante al azar y su mazo, ya limpio.
     *
     * <p>Es {@code DeckgenUtil.generateCommanderDeck} y nada mas: el que usan
     * el selector de rival, el torneo y la sala en red.
     */
    public static Deck commanderDeck(final boolean forAi, final GameType type) {
        final Deck deck = DeckgenUtil.generateCommanderDeck(forAi, type);
        if (deck != null) {
            fixCopyLimits(deck, type.getDeckFormat());
        }
        return deck;
    }

    /**
     * Si {@code generateRandomCommanderDeck} le buscaria a este comandante un
     * companero de mando <b>suyo</b>, y montaria el mazo para los dos.
     *
     * <p>Es la misma pregunta que se hace el generador con {@code isCardGen}:
     * en Oathbreaker el segundo hueco es el hechizo insignia (y eso ya lo
     * limpia {@link #fixCopyLimits}); en el resto, cualquier comandante que
     * admita companero — Partner, Background, Doctor... —, tenga el jugador
     * uno puesto o no.
     */
    public static boolean picksItsOwnPartner(final PaperCard commander, final DeckFormat format) {
        return commander != null && !format.equals(DeckFormat.Oathbreaker)
                && commander.getRules().canBePartnerCommander();
    }

    /**
     * "Generar mazo" del constructor para un comandante que admite companero:
     * el principal para la zona de mando <b>del jugador</b>.
     *
     * <p><b>Por que no vale el generador.</b> Solo recibe un comandante, y si
     * admite companero le busca <b>siempre</b> uno suyo, al azar
     * ({@code getRandomPartnerCommander}), y monta 98 cartas para la identidad
     * de los dos. El constructor tiraba ese companero y se quedaba el
     * principal: medido el 02-10-2026, Thrasios solo, <b>20 de 20</b> mazos
     * con 7-30 cartas fuera de su identidad y una carta de menos (98 + 1);
     * Thrasios con Tymna, <b>14 de 30</b> (cuando le tocaba Rograkh o Kraum).
     * Cambiar lo de fuera por basicas lo dejaba legal, pero con ~20 tierras de
     * mas.
     *
     * <p>Asi que se hace lo que hace el generador con {@code isCardGen=true},
     * con el companero que tiene puesto el jugador o con ninguno: las cartas
     * que la matriz de Commander junta con <b>los dos</b> (sumando lo que se
     * juega con cada uno), ordenadas al azar con peso
     * ({@code prepareWeightedRandomizedCardPool}), sin lo que el formato no
     * deja ni lo que ya esta en la zona de mando o el banquillo, con el tope de
     * bracket de las preferencias, y <b>el mismo montador</b>
     * ({@code CardThemedCommanderDeckBuilder}, que usa el companero para los
     * colores y para quitarle una carta al tamanyo). Lo unico del generador que
     * no se copia es el recorte al azar de hasta cuatro cartas: el orden con
     * peso ya da la variedad.
     *
     * <p>La matriz es la de Commander tambien en Tiny Leaders, como en el
     * generador. Brawl sale de otra (la de Estandar), pero hoy <b>ningun</b>
     * comandante de Brawl admite companero (medido el 02-10-2026: 0, por 224
     * de Commander y 95 de Tiny Leaders); si llega uno, aqui se monta con las
     * cartas de la de Commander que Brawl deja, que es legal aunque no sea la
     * suya.
     *
     * <p>No limpia el limite de copias: eso lo hace quien lo llama, con
     * {@link #fixCopyLimits(Deck, DeckFormat, CardPool)}, igual que con lo que
     * devuelve el generador.
     *
     * @param partner     el segundo comandante del jugador, o null
     * @param outsideMain lo que el mazo ya lleva fuera del principal (banquillo
     *                    y zona de mando), para no repetirlo
     * @return el mazo con su zona de mando, o null si la matriz no conoce a
     *         ninguno de los dos (como el generador, que ahi revienta)
     */
    public static Deck forCommandZone(final PaperCard commander, final PaperCard partner,
                                      final DeckFormat format, final CardPool outsideMain) {
        final Map<String, Map.Entry<PaperCard, Integer>> together = new LinkedHashMap<>();
        for (final PaperCard cmd : partner == null ? List.of(commander) : List.of(commander, partner)) {
            for (final Map.Entry<PaperCard, Integer> e : AscentSynergy.poolOf(cmd)) {
                together.merge(e.getKey().getName(), e,
                        (a, b) -> Map.entry(a.getKey(), a.getValue() + b.getValue()));
            }
        }
        if (together.isEmpty()) {
            return null;
        }

        final Set<String> taken = new HashSet<>(countByNormalizedName(outsideMain).keySet());
        taken.add(normalized(commander.getName()));
        if (partner != null) {
            taken.add(normalized(partner.getName()));
        }
        final List<PaperCard> candidates = new ArrayList<>();
        for (final PaperCard c : weightedOrder(new ArrayList<>(together.values()))) {
            if (format.isLegalCard(c) && !taken.contains(normalized(c.getName()))) {
                candidates.add(c);
            }
        }
        final List<PaperCard> pool = limitToBracket(candidates, commander, partner,
                FModel.getPreferences().getPrefInt(FPref.DECKGEN_MAXIMUM_COMMANDER_BRACKET));

        final CardThemedCommanderDeckBuilder gen =
                new CardThemedCommanderDeckBuilder(commander, partner, pool, false, format);
        gen.setSingleton(true);
        gen.setUseArtifacts(!FModel.getPreferences().getPrefBoolean(FPref.DECKGEN_ARTIFACTS));
        final CardPool cards = gen.getDeck(format.getMainRange().getMaximum(), false);

        final Deck deck = new Deck("Generated " + format + " deck (" + commander.getName()
                + (partner == null ? "" : "--" + partner.getName()) + ")");
        deck.setDirectory("generated/commander");
        deck.getMain().addAll(cards);
        deck.getOrCreate(DeckSection.Commander).add(commander);
        if (partner != null) {
            deck.get(DeckSection.Commander).add(partner);
        }
        return deck;
    }

    /**
     * Las cartas en orden al azar <b>con peso</b>: lo que mas se juega sale
     * antes, sin ser siempre lo mismo. Es
     * {@code DeckgenUtil.prepareWeightedRandomizedCardPool}, que es privado.
     */
    private static List<PaperCard> weightedOrder(final List<Map.Entry<PaperCard, Integer>> pool) {
        final Map<Map.Entry<PaperCard, Integer>, Double> key = new IdentityHashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            key.put(e, Math.log(MyRandom.getRandom().nextDouble()) / Math.max(1, e.getValue()));
        }
        pool.sort((a, b) -> Double.compare(key.get(b), key.get(a)));
        final List<PaperCard> out = new ArrayList<>(pool.size());
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            out.add(e.getKey());
        }
        return out;
    }

    /**
     * Lo mismo que {@code DeckgenUtil.limitCardsToCommanderBracket}, que es
     * privado: se van metiendo cartas mientras el mazo que resultaria —
     * comandante y companero incluidos — no pase del bracket. Solo hace algo
     * entre 1 y 3, igual que alli. El bracket lo calcula el motor
     * ({@code CommanderBracketCalculator}).
     *
     * @param partner el segundo comandante, o null
     */
    public static List<PaperCard> limitToBracket(final List<PaperCard> cards, final PaperCard commander,
                                                 final PaperCard partner, final int maxBracket) {
        if (maxBracket < 1 || maxBracket >= 4) {
            return cards;
        }
        final List<PaperCard> out = new ArrayList<>();
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(CommanderBracketCalculator.getCardNames(commander));
        names.addAll(CommanderBracketCalculator.getCardNames(partner));
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
     * Cambia por una basica cada carta que el formato no deja tener tantas
     * veces en el principal.
     *
     * <p>El limite lo contesta el motor ({@code DeckFormat.getMaxCardCopies}):
     * el de la carta si trae {@code DeckLimit}, ninguno si es basica o "any
     * number of", y el del formato si no. Se cuenta por nombre, que es como
     * cuenta {@code getDeckConformanceProblem}.
     *
     * @return cuantas cartas ha cambiado (0 si el mazo ya estaba bien)
     */
    public static int fixCopyLimits(final Deck deck, final DeckFormat format) {
        return fixCopyLimits(deck, format, new CardPool());
    }

    /**
     * Lo mismo, contando tambien lo que el mazo ya lleva <b>fuera</b> del
     * principal.
     *
     * <p>Es "Generar mazo" del constructor: el principal sale del generador y
     * la zona de mando y el banquillo son los del jugador. El generador solo
     * recibe el comandante, asi que el hechizo insignia (Oathbreaker) y el
     * companero del banquillo no los conoce y los puede meter en el principal:
     * medido el 02-10-2026, <b>18 de 30</b> mazos de Arlinn Kord traian su
     * Moonmist y 18 de 30 de Runo Stromkirk su Gyruda — y el mazo recien
     * generado salia ya sin poderse guardar (<i>"must not contain more than 1
     * copies of the card Moonmist"</i>).
     *
     * <p>Se cuenta como {@code DeckFormat.getDeckConformanceProblem}: por
     * nombre normalizado, y las cartas con un numero fijo
     * ({@code DeckLimit}, los Siete Enanos) solo en el principal. Solo se
     * quita del principal: lo de fuera es del jugador.
     *
     * @param outsideMain lo que el mazo lleva fuera del principal y el motor
     *        cuenta con el (banquillo y zona de mando)
     */
    public static int fixCopyLimits(final Deck deck, final DeckFormat format, final CardPool outsideMain) {
        final CardPool main = deck.getMain();
        final Map<String, Integer> outside = countByNormalizedName(outsideMain);
        final Set<String> seen = new HashSet<>();
        final List<String> removed = new ArrayList<>();
        int total = 0;
        for (final Map.Entry<PaperCard, Integer> e : entries(main)) {
            final String name = e.getKey().getName();
            if (!seen.add(name)) {
                continue;
            }
            final int inMain = main.countByName(name);
            final int alsoOutside = DeckFormat.canHaveSpecificNumberInDeck(e.getKey()) == null
                    ? outside.getOrDefault(normalized(name), 0) : 0;
            final int excess = Math.min(inMain,
                    inMain + alsoOutside - format.getMaxCardCopies(e.getKey()));
            if (excess <= 0) {
                continue;
            }
            removeByName(main, name, excess);
            total += excess;
            removed.add(excess > 1 ? name + " x" + excess : name);
        }
        if (total == 0) {
            return 0;
        }
        final PaperCard basic = mostCommonBasic(main, format);
        if (basic != null) {
            main.add(basic, total);
        }
        System.out.println("[neo] mazo generado (" + deck.getName() + "): fuera " + removed
                + (basic != null ? ", en su lugar " + total + " " + basic.getName()
                        : ", y sin basica con que rellenar"));
        return total;
    }

    /** Cuantas copias hay de cada nombre, agrupadas como las agrupa el motor. */
    private static Map<String, Integer> countByNormalizedName(final CardPool pool) {
        final Map<String, Integer> out = new HashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            out.merge(normalized(e.getKey().getName()), e.getValue(), Integer::sum);
        }
        return out;
    }

    private static String normalized(final String name) {
        return FModel.getMagicDb().getCommonCards().getNormalizedName(name);
    }

    /** Una copia de las entradas, para poder tocar el pozo mientras se recorre. */
    private static List<Map.Entry<PaperCard, Integer>> entries(final CardPool pool) {
        final List<Map.Entry<PaperCard, Integer>> out = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            out.add(Map.entry(e.getKey(), e.getValue()));
        }
        return out;
    }

    /** Quita {@code amount} copias de ese nombre, sea cual sea la impresion. */
    private static void removeByName(final CardPool pool, final String name, final int amount) {
        int left = amount;
        for (final Map.Entry<PaperCard, Integer> e : entries(pool)) {
            if (left == 0) {
                break;
            }
            if (e.getKey().getName().equals(name)) {
                final int n = Math.min(left, e.getValue());
                pool.remove(e.getKey(), n);
                left -= n;
            }
        }
    }

    /**
     * La basica de la que el mazo lleva mas copias; si no lleva ninguna,
     * Wastes, pero solo si el formato la deja (Brawl tira de unas pocas
     * expansiones). Si tampoco, nada: mejor una carta de menos, que el motor
     * dice con todas las letras, que una ilegal.
     */
    private static PaperCard mostCommonBasic(final CardPool pool, final DeckFormat format) {
        PaperCard best = null;
        int most = 0;
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            if (e.getKey().getRules().getType().isBasicLand() && e.getValue() > most) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        if (best != null) {
            return best;
        }
        final PaperCard wastes = FModel.getMagicDb().getCommonCards().getCard(FALLBACK_BASIC);
        return wastes != null && format.isLegalCard(wastes) ? wastes : null;
    }
}
