package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Que te llevas de cada nodo: las tres cartas, los creditos y la reliquia.
 *
 * <h2>Las tres cartas son el modo</h2>
 *
 * <p>Elegir <b>una de tres</b> despues de cada combate es el bucle entero: es
 * lo unico que hace que el mazo del final de la run no se parezca al del
 * principio, y por eso el mazo de salida es flojo a proposito
 * ({@link AscentSeedDeck}). Si las cartas ofrecidas no se notaran, no habria
 * modo.
 *
 * <h2>Y por eso suben de rareza acto a acto</h2>
 *
 * <table>
 *   <tr><th>Acto 1</th><td>comunes e infrecuentes</td></tr>
 *   <tr><th>Acto 2</th><td>infrecuentes y raras</td></tr>
 *   <tr><th>Acto 3</th><td>raras y miticas</td></tr>
 * </table>
 *
 * <p>Es la misma escala con la que {@link AscentBattle} ordena a los rivales y
 * con la que {@link AscentSeedDeck} hace flojo el mazo de salida. Que las tres
 * cosas usen el mismo criterio es lo que hace que la run <b>se sienta</b> subir.
 *
 * <h2>Lo que se ofrece se puede jugar</h2>
 *
 * <p>Las cartas se filtran por los colores que el mazo ya juega. En Commander
 * eso es una <b>regla</b> (la identidad del comandante), pero en Estandar es
 * una <b>decision nuestra</b> y conviene saber por que: un mazo de run son
 * treinta cartas con doce tierras de uno o dos colores, asi que una carta de un
 * tercer color no es "una opcion arriesgada", es una carta que no se puede
 * lanzar. Ofrecerla seria ofrecer dos opciones de tres.
 *
 * <p>Los incoloros entran siempre: valen en cualquier mazo, y son la unica
 * forma de que un artefacto bueno pueda salir en una run monocolor.
 *
 * <h2>Ni las reliquias ni las cartas raras del motor</h2>
 *
 * <p>El pozo sale de {@code getUniqueCards()}, que es el mismo del buscador del
 * deck builder: una impresion por carta. Las reliquias no estan ahi (son
 * {@link CardRarity#Special} y ademas lo comprueba {@code ascentcheck}), y las
 * cartas sin coste de mana — esquemas, planos, emblemas, avatares — se caen
 * solas al pedir rareza.
 */
public final class AscentRewards {

    private AscentRewards() {
    }

    /** Cuantas cartas se ofrecen para elegir una. */
    public static final int CHOICES = 3;

    /** Creditos de un combate normal, por acto. */
    private static final int[] CREDITS_BY_ACT = {25, 40, 60};

    // ------------------------------------------------------------------

    /** Cuantas reliquias ofrece un jefe para que elijas una. */
    public static final int RELIC_CHOICES = 3;

    /** Lo que da un nodo. */
    public static final class Reward {
        /** Las cartas entre las que elegir una. Vacia si el nodo no da cartas. */
        public final List<PaperCard> cards;
        /** Creditos, que se gastan en la tienda. */
        public final int credits;
        /**
         * Las reliquias del nodo. Vacia si no da ninguna.
         *
         * <p>Un <b>jefe ofrece {@link #RELIC_CHOICES} y eliges una</b> (decision
         * del autor, 02-09-2026): la reliquia de jefe es el salto con el que se
         * aguanta el acto siguiente, y darla al azar convierte el momento mas
         * importante de la run en una tirada. Los demas nodos dan una y ya.
         */
        public final List<AscentRelic> relics;
        /** Si hay que elegir una de las de arriba, o se llevan todas. */
        public final boolean chooseOne;

        Reward(final List<PaperCard> cards, final int credits,
               final List<AscentRelic> relics, final boolean chooseOne) {
            this.cards = Collections.unmodifiableList(cards);
            this.credits = credits;
            this.relics = Collections.unmodifiableList(relics);
            this.chooseOne = chooseOne;
        }

        /** La unica reliquia de un nodo que da una sola, o {@code null}. */
        public AscentRelic singleRelic() {
            return chooseOne || relics.isEmpty() ? null : relics.get(0);
        }

        @Override
        public String toString() {
            return cards.size() + " cartas | " + credits + " creditos"
                    + (relics.isEmpty() ? ""
                    : " | " + relics.size() + (chooseOne ? " reliquias a elegir" : " reliquia"));
        }
    }

    /**
     * El premio de ese nodo.
     *
     * <p>Sembrado con la clave del nodo, igual que el rival
     * ({@link AscentBattle}): si se sorteara suelto, salir del juego antes de
     * elegir y volver daria otras tres cartas — o sea, tirar hasta que salga la
     * que quieres.
     */
    public static Reward of(final AscentRun run, final AscentNode node) {
        final Random rnd = rng(run, node);
        final int act = Math.max(1, Math.min(AscentRun.ACTS, run.getAct()));
        switch (node.getKind()) {
            case COMBAT:
                return new Reward(pickCards(run, act, rnd),
                        credits(run, CREDITS_BY_ACT[act - 1], rnd), List.of(), false);
            case ELITE:
                // Una elite da carta, mas creditos Y reliquia: es el nodo que
                // se elige a proposito sabiendo que puede costarte la run.
                return new Reward(pickCards(run, act, rnd),
                        credits(run, CREDITS_BY_ACT[act - 1] * 2, rnd),
                        one(relic(run, AscentRelic.Rarity.RARE, rnd)), false);
            case BOSS:
                // TRES reliquias de jefe, y eliges. Es el momento en el que se
                // decide si el acto siguiente se aguanta.
                return new Reward(pickCards(run, act, rnd),
                        credits(run, CREDITS_BY_ACT[act - 1] * 3, rnd),
                        relics(run, AscentRelic.Rarity.BOSS, RELIC_CHOICES, rnd), true);
            case TREASURE:
                return new Reward(new ArrayList<>(), 0,
                        one(relic(run, AscentRelic.Rarity.COMMON, rnd)), false);
            default:
                // Descanso, tienda y evento no dan premio: lo suyo lo decide su
                // propia pantalla.
                return new Reward(new ArrayList<>(), 0, List.of(), false);
        }
    }

    /**
     * Mete la carta elegida en el mazo de la run y lo guarda.
     *
     * @return el mazo ya con la carta dentro, o {@code null} si no habia mazo
     */
    public static Deck take(final AscentRun run, final PaperCard card) {
        final Deck deck = AscentDecks.load(run);
        if (deck == null || card == null) {
            return deck;
        }
        deck.getMain().add(card);
        AscentDecks.save(deck);
        return deck;
    }

    // ------------------------------------------------------------------
    //  Las cartas
    // ------------------------------------------------------------------

    /**
     * Tres cartas distintas, del pozo del acto y de los colores del mazo.
     *
     * <p>Si el pozo se queda corto (un mazo de un color muy raro en el acto 3)
     * se devuelven las que haya: <b>menos de tres es peor que tres, pero
     * infinitamente mejor que colgarse buscando</b>.
     */
    private static List<PaperCard> pickCards(final AscentRun run, final int act, final Random rnd) {
        return offer(run, act, rnd, CHOICES);
    }

    /**
     * El mismo sorteo, pidiendo cuantas cartas se quieran.
     *
     * <p>Lo usa la <b>tienda</b> ({@link AscentShop}): su mostrador sale del
     * mismo pozo que los premios a proposito — la rareza del acto y los colores
     * del mazo son las dos reglas que hacen que una carta ofrecida sea una
     * carta jugable, y valen igual se pague por ella o no.
     */
    public static List<PaperCard> offer(final AscentRun run, final int act,
                                        final Random rnd, final int count) {
        final List<PaperCard> pool = poolFor(run, act);
        final List<PaperCard> out = new ArrayList<>();
        if (pool.isEmpty()) {
            return out;
        }
        final Set<String> seen = new HashSet<>();
        // Se tira un numero acotado de veces en vez de barajar el pozo entero:
        // son miles de cartas y esto se llama al acabar cada combate.
        for (int tries = 0; tries < 200 && out.size() < count; tries++) {
            final PaperCard c = pool.get(rnd.nextInt(pool.size()));
            if (seen.add(c.getName())) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * El pozo de un acto: rareza del acto y colores del mazo.
     *
     * <p>Se recalcula en cada nodo a proposito: los colores del mazo cambian
     * dentro de la run (una carta incolora no, pero el comandante puede no ser
     * el unico que aporte identidad), y cachear un pozo por acto dejaria de
     * casar en cuanto eso pasara. Recorrer 33.000 cartas una vez por combate no
     * se nota al lado de la propia partida.
     */
    private static List<PaperCard> poolFor(final AscentRun run, final int act) {
        final ColorSet allowed = colorsOf(run);
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard c : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (!rarityFits(c.getRarity(), act)) {
                continue;
            }
            if (c.getRules() == null || c.getRules().getType().isLand()) {
                // Las tierras no se ofrecen: la base de mana de una run la fija
                // el mazo de salida, y una tierra mas en el premio es un premio
                // que no se nota.
                continue;
            }
            final ColorSet id = c.getRules().getColorIdentity();
            if (!id.hasNoColorsExcept(allowed)) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /** Que rarezas se ofrecen en cada acto. */
    private static boolean rarityFits(final CardRarity rarity, final int act) {
        switch (act) {
            case 1:
                return rarity == CardRarity.Common || rarity == CardRarity.Uncommon;
            case 2:
                return rarity == CardRarity.Uncommon || rarity == CardRarity.Rare;
            default:
                return rarity == CardRarity.Rare || rarity == CardRarity.MythicRare;
        }
    }

    /**
     * Los colores en los que se puede premiar.
     *
     * <p>En Commander los manda el <b>comandante</b>, y eso no es una eleccion
     * nuestra: una carta fuera de su identidad es ilegal en el mazo y el motor
     * la rechazaria. En Estandar son los del mazo.
     */
    public static ColorSet colorsOf(final AscentRun run) {
        final Deck deck = AscentDecks.load(run);
        if (deck == null) {
            return ColorSet.fromMask(forge.card.MagicColor.ALL_COLORS);
        }
        if (run.getMode() == AscentRun.Mode.COMMANDER && deck.has(DeckSection.Commander)) {
            byte mask = 0;
            for (final PaperCard cmd : deck.getCommanders()) {
                mask |= cmd.getRules().getColorIdentity().getColor();
            }
            return ColorSet.fromMask(mask);
        }
        byte mask = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            // Por el coste, no por la identidad: lo que decide de que colores
            // es un mazo es lo que hay que PAGAR. Contar la identidad mete el
            // tercer color de una tierra que solo produce mana (el plan de Ascenso 2b).
            if (!e.getKey().getRules().getType().isLand()) {
                mask |= e.getKey().getRules().getManaCost().getColorProfile();
            }
        }
        return mask == 0 ? ColorSet.fromMask(forge.card.MagicColor.ALL_COLORS) : ColorSet.fromMask(mask);
    }

    // ------------------------------------------------------------------
    //  Creditos y reliquias
    // ------------------------------------------------------------------

    /** Los creditos del nodo, con su pellizco de azar y la Ascension aplicada. */
    private static int credits(final AscentRun run, final int base, final Random rnd) {
        int amount = base + rnd.nextInt(Math.max(1, base / 4));
        if (run.getAscension() >= 6) {
            // Ascension 6: menos creditos en todo.
            amount = amount * 3 / 4;
        }
        return amount;
    }

    /**
     * Una reliquia de esa rareza que <b>no lleves ya</b>.
     *
     * <p>Repetirla no haria nada (las estaticas no se apilan y
     * {@link AscentRun#addRelic} las rechaza), asi que una reliquia repetida es
     * un premio vacio disfrazado de premio. Si no queda ninguna de su rareza se
     * baja a las comunes; si tampoco, se devuelve {@code null} y quien llame
     * dara creditos en su lugar.
     */
    public static AscentRelic relic(final AscentRun run, final AscentRelic.Rarity rarity,
                                    final Random rnd) {
        return pickRelic(owned(run), rollRarity(rarity, rnd), rnd);
    }

    /**
     * De que rareza acaba saliendo una reliquia que "deberia" ser de esta.
     *
     * <h2>Aqui viven las legendarias</h2>
     *
     * <p>Las rotas <b>no tienen nodo propio</b>: se cuelan en el sorteo de las
     * demas con muy poca probabilidad. Es como funciona Isaac, y es lo que hace
     * que salga una sea una historia y no un tramite — si tuvieran su propio
     * nodo, saldrian cuando toca y dejarian de sorprender.
     *
     * <table>
     *   <tr><th>El nodo paga</th><th>y sale</th></tr>
     *   <tr><td>comun (tesoro, tienda)</td><td>78% comun · 18% rara · <b>4% legendaria</b></td></tr>
     *   <tr><td>rara (elite)</td><td>85% rara · <b>15% legendaria</b></td></tr>
     *   <tr><td>de jefe</td><td>92% de jefe · <b>8% legendaria</b></td></tr>
     * </table>
     *
     * <p>Un 4% por tesoro suena a poco y no lo es: una run pasa por varios
     * tesoros, varias tiendas y hasta tres jefes, asi que <b>ver una legendaria
     * es raro pero no excepcional</b> — que es exactamente el punto. Lo que
     * tiene que ser raro es verla <i>pronto</i>.
     */
    public static AscentRelic.Rarity rollRarity(final AscentRelic.Rarity base, final Random rnd) {
        final int roll = rnd.nextInt(100);
        switch (base) {
            case COMMON:
                return roll < 4 ? AscentRelic.Rarity.LEGENDARY
                        : roll < 22 ? AscentRelic.Rarity.RARE : AscentRelic.Rarity.COMMON;
            case RARE:
                return roll < 15 ? AscentRelic.Rarity.LEGENDARY : AscentRelic.Rarity.RARE;
            case BOSS:
                return roll < 8 ? AscentRelic.Rarity.LEGENDARY : AscentRelic.Rarity.BOSS;
            default:
                return base;
        }
    }

    /**
     * El orden en el que se baja de rareza cuando no queda ninguna libre.
     *
     * <p>Hace falta porque las reliquias no se repiten: al final de una run
     * larga puede no quedar ninguna de la rareza pedida. Se baja, nunca se
     * sube: rellenar un hueco con una legendaria seria regalar la mejor carta
     * del juego <b>justo por haber tenido suerte antes</b>.
     */
    private static final AscentRelic.Rarity[] FALLBACK = {
        AscentRelic.Rarity.LEGENDARY, AscentRelic.Rarity.BOSS,
        AscentRelic.Rarity.RARE, AscentRelic.Rarity.COMMON};

    /** Las rarezas a probar, empezando por la pedida y bajando. */
    private static List<AscentRelic.Rarity> tiersFrom(final AscentRelic.Rarity from) {
        final List<AscentRelic.Rarity> out = new ArrayList<>();
        boolean started = false;
        for (final AscentRelic.Rarity r : FALLBACK) {
            if (r == from) {
                started = true;
            }
            if (started) {
                out.add(r);
            }
        }
        if (out.isEmpty()) {
            out.add(from);
        }
        return out;
    }

    /**
     * Varias reliquias distintas de esa rareza, ninguna que ya lleves.
     *
     * <p>Si no quedan suficientes de su rareza se completa con las de abajo:
     * <b>menos de tres es peor que tres, pero un hueco vacio en la pantalla del
     * jefe es mucho peor que las dos cosas</b>. Con seis reliquias de jefe en el
     * catalogo esto solo hace falta en el tercer jefe de una run redonda.
     */
    public static List<AscentRelic> relics(final AscentRun run, final AscentRelic.Rarity rarity,
                                           final int howMany, final Random rnd) {
        final List<AscentRelic> out = new ArrayList<>();
        final Set<String> taken = owned(run);
        // La rareza se tira POR HUECO, no una vez para los tres: asi las tres
        // que ofrece un jefe pueden no ser del mismo escalon, y de vez en
        // cuando una de ellas es legendaria. Tirarla una sola vez daria tres
        // legendarias juntas o ninguna, que son los dos extremos malos.
        while (out.size() < howMany) {
            final AscentRelic pick = pickRelic(taken, rollRarity(rarity, rnd), rnd);
            if (pick == null) {
                break;
            }
            out.add(pick);
            taken.add(pick.getId());
        }
        return out;
    }

    /** Una lista con esa reliquia, o vacia si es {@code null}. */
    private static List<AscentRelic> one(final AscentRelic relic) {
        return relic == null ? List.of() : List.of(relic);
    }

    /** Una reliquia libre de esa rareza, bajando de escalon si no queda ninguna. */
    private static AscentRelic pickRelic(final Set<String> taken,
                                         final AscentRelic.Rarity rarity, final Random rnd) {
        for (final AscentRelic.Rarity tier : tiersFrom(rarity)) {
            final List<AscentRelic> pool = new ArrayList<>();
            for (final AscentRelic r : AscentRelics.all()) {
                if (r.getRarity() == tier && !taken.contains(r.getId())) {
                    pool.add(r);
                }
            }
            if (!pool.isEmpty()) {
                return pool.get(rnd.nextInt(pool.size()));
            }
        }
        return null;
    }

    /** Las que ya llevas puestas. */
    private static Set<String> owned(final AscentRun run) {
        final Set<String> mine = new HashSet<>();
        for (final AscentRelic r : run.relics()) {
            mine.add(r.getId());
        }
        return mine;
    }

    // ------------------------------------------------------------------

    /** El mismo sorteo sembrado que {@link AscentBattle}, y por lo mismo. */
    private static Random rng(final AscentRun run, final AscentNode node) {
        long h = run.getSeed() * 131L + run.getAct() * 17L;
        for (final char c : node.key().toCharArray()) {
            h = h * 31L + c;
        }
        return new Random(h);
    }
}
