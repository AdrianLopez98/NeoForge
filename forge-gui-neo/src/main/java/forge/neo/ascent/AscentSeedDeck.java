package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import forge.card.CardRarity;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.deck.DeckgenUtil;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.util.MyRandom;

/**
 * El mazo con el que empieza una run. <b>Generado, y flojo a proposito.</b>
 *
 * <h2>Las dos cosas que tiene que ser</h2>
 *
 * <p><b>1. Distinto cada vez.</b> Un roguelike vive de que dos runs no se
 * parezcan. Si el mazo de salida fuera fijo, las primeras cuatro o cinco
 * decisiones serian identicas siempre — y son las que marcan el resto.
 *
 * <p><b>2. Malo.</b> Y esto es igual de importante, o mas. Todo el modo consiste
 * en <i>mejorar</i> un mazo eligiendo cartas nodo a nodo. Si el mazo de salida
 * ya fuera bueno, las recompensas no se notarian y el bucle entero se queda sin
 * su recompensa. <b>Un mazo semilla fuerte se carga el modo</b> mas que uno
 * aburrido.
 *
 * <h2>Como se consigue cada modo</h2>
 *
 * <table>
 *   <tr><th>Estandar</th><td>Uno o <b>dos colores como mucho</b>, y solo cartas
 *       <b>comunes e infrecuentes</b>. Es el mazo de principiante: hace lo que
 *       dice, sin cartas que ganen la partida solas.</td></tr>
 *   <tr><th>Commander</th><td>El comandante lo <b>eliges tu</b> (o sale uno al
 *       azar si no eliges), y el mazo se monta a su alrededor con su mecanica —
 *       eso lo hace el motor. Pero con <b>bracket 1 o 2</b>, que es la escala
 *       oficial de potencia de Commander: bracket 1-2 es mazo casual, sin
 *       combos ni cartas que rompen la partida.</td></tr>
 * </table>
 *
 * <p>Ninguna de las dos cosas es logica nuestra: los colores los limita
 * {@code buildColorDeck} con la seleccion que se le pasa, la rareza un
 * {@code Predicate} que el propio generador acepta, y el bracket es el
 * parametro {@code maxBracket} de {@code generateRandomCommanderDeck}. Nosotros
 * solo decidimos que pedir.
 *
 * <h2>El recorte</h2>
 *
 * <p>El motor genera mazos de 60 y de 100; una run empieza con 20-30. Recortar
 * si es nuestro, y no es tonto: se guarda la proporcion de tierras y se
 * prefieren los costes bajos. Un recorte al azar deja mazos de nueve tierras y
 * cuatro bombas de coste siete, que no es "variado": es roto.
 *
 * <p><b>Lo que tiene que variar son las cartas, no si el mazo se puede jugar.</b>
 */
public final class AscentSeedDeck {

    private AscentSeedDeck() {
    }

    /** Cuantas cartas tiene el mazo de salida en Estandar. */
    public static final int STANDARD_SIZE = 30;

    /** Y en Commander, sin contar al comandante. */
    public static final int COMMANDER_SIZE = 20;

    /** Que parte del mazo son tierras. Doce de treinta es la proporcion de siempre. */
    private static final double LAND_RATIO = 0.40;

    /**
     * El techo de potencia del mazo de Commander de salida.
     *
     * <p>Es la escala oficial de brackets de Commander, que el motor calcula con
     * {@code CommanderBracketCalculator}: 1 es "exhibicion", 2 "nucleo". A
     * partir de 3 empiezan los mazos afilados, y eso aqui sobra.
     */
    private static final int MAX_BRACKET = 2;

    /**
     * De que rarezas se monta el mazo de principiante.
     *
     * <p>Sin raras ni miticas. Es la forma mas limpia de que el mazo sea flojo
     * sin tener que juzgar carta por carta: las cartas que ganan solas casi
     * siempre son raras.
     */
    private static final Predicate<PaperCard> BEGINNER =
            c -> c.getRarity() == CardRarity.Common
                    || c.getRarity() == CardRarity.Uncommon
                    || c.getRarity() == CardRarity.BasicLand;

    /**
     * Monta el mazo de salida de una run nueva, sin Ascension.
     *
     * @param mode      con que reglas se juega
     * @param commander en Commander, el que eligio el jugador; {@code null} para
     *                  que salga uno al azar. En Estandar se ignora
     * @param name      como se va a llamar el {@code .dck}
     */
    public static Deck generate(final AscentRun.Mode mode, final PaperCard commander,
                                final String name) {
        return generate(mode, commander, name, 0);
    }

    /**
     * Igual, con Ascension: a partir del nivel 3 el mazo sale con una carta
     * maldita (§curse).
     */
    public static Deck generate(final AscentRun.Mode mode, final PaperCard commander,
                                final String name, final int ascension) {
        final Deck deck = mode == AscentRun.Mode.COMMANDER
                ? commander(commander, name)
                : standard(name);
        if (ascension >= 3) {
            curse(deck);
        }
        return deck;
    }

    /** Igual, dejando que el comandante salga al azar. */
    public static Deck generate(final AscentRun.Mode mode, final String name) {
        return generate(mode, null, name);
    }

    /**
     * Ascension 3: una carta maldita en el mazo de salida.
     *
     * <p>No se AÑADE: el tamano del mazo es una invariante que se da por
     * sentada en el resto del modo (30 en Estandar, 20 + comandante en
     * Commander), y el suelo de la tienda ({@code AscentShop.MIN_DECK}) cuenta
     * sobre ese numero. Se <b>sustituye</b> un hechizo cualquiera por
     * {@link #CURSED_CARD}.
     *
     * <p>Y es una carta <b>real</b> del motor, no un invento nuestro:
     * {@code Millstone}, un artefacto sin color que no hace nada por ti — la
     * unica forma de que dane a alguien es activarla sobre OTRO jugador, asi
     * que lo unico que te cuesta es el hueco del mazo. Sin color a proposito:
     * vale igual en un Estandar mono-color que en cualquier identidad de
     * Commander, sin tener que elegir una version por color.
     */
    private static void curse(final Deck deck) {
        final PaperCard cursed = FModel.getMagicDb().getCommonCards().getCard(CURSED_CARD);
        if (cursed == null) {
            return; // no deberia faltar nunca; mejor un mazo sin maldicion que uno roto
        }
        PaperCard toDrop = null;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (!e.getKey().getRules().getType().isLand()) {
                toDrop = e.getKey();
                break;
            }
        }
        if (toDrop == null) {
            return; // mazo sin hechizos (no deberia pasar): mejor dejarlo tal cual
        }
        deck.getMain().remove(toDrop);
        deck.getMain().add(cursed);
    }

    /** La carta maldita de la Ascension 3. */
    private static final String CURSED_CARD = "Millstone";

    // ------------------------------------------------------------------
    //  Estandar: mono o dos colores, y de principiante
    // ------------------------------------------------------------------

    private static Deck standard(final String name) {
        // Uno o dos colores. El motor ofrece {1,2,3} y {1,2,3,5}; ninguno vale,
        // asi que se le pasa la seleccion a mano. "Random" es su forma de decir
        // "elige tu el color".
        final int colors = 1 + MyRandom.getRandom().nextInt(2);
        final List<String> selection = new ArrayList<>();
        for (int i = 0; i < colors; i++) {
            selection.add("Random");
        }
        final Deck full = DeckgenUtil.buildColorDeck(selection, BEGINNER, false);
        return trim(full, null, STANDARD_SIZE, name);
    }

    // ------------------------------------------------------------------
    //  Commander: el que elijas, y con techo de potencia
    // ------------------------------------------------------------------

    private static Deck commander(final PaperCard chosen, final String name) {
        final PaperCard cmd = chosen != null ? chosen : randomCommander();
        if (cmd == null) {
            // Sin comandante no hay mazo de Commander. Falla ruidoso: montar
            // uno sin comandante daria una run rarisima sin decir por que.
            throw new IllegalStateException("no se ha podido elegir comandante para la run");
        }
        // maxBracket es lo que limita la potencia, y lo aplica el motor
        // (limitCardsToCommanderBracket). Solo hace caso entre 1 y 3.
        final Deck full = DeckgenUtil.generateRandomCommanderDeck(
                cmd, DeckFormat.Commander, false, false, MAX_BRACKET);
        return trim(full, cmd, COMMANDER_SIZE, name);
    }

    /**
     * Un comandante al azar de los que se pueden jugar.
     *
     * <p>Es la opcion "que elija el juego": no todo el mundo quiere leerse
     * ochocientos legendarios antes de empezar una run de cuarenta minutos.
     */
    public static PaperCard randomCommander() {
        final List<PaperCard> pool = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : FModel.getCommanderPool()) {
            pool.add(e.getKey());
        }
        if (pool.isEmpty()) {
            return null;
        }
        return pool.get(MyRandom.getRandom().nextInt(pool.size()));
    }

    /**
     * Todos los comandantes que se pueden elegir, para la pantalla de montar la run.
     *
     * <p><b>Sin las rebalanceadas de Arena</b> (las que se llaman {@code A-algo}).
     * No es un capricho: ordenadas por nombre se van todas al principio, asi que
     * la primera pantalla del selector salia entera de cartas <i>A-</i> — que no
     * son las que nadie busca al elegir comandante, y encima parecen un fallo.
     * <p>Se filtran por <b>dos</b> condiciones y hacen falta las dos:
     * {@code isRebalanced()} sola solo quitaba 33 de las 10.824 — pregunta
     * {@code StaticData.isRebalanced(name)} con el nombre que <i>ya</i> lleva el
     * prefijo, asi que casi ninguna casa. El prefijo {@code A-} lo pone Forge
     * literal y no hay ninguna carta de Magic de verdad que empiece asi.
     */
    public static List<PaperCard> commanderPool() {
        final List<PaperCard> pool = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : FModel.getCommanderPool()) {
            if (!e.getKey().isRebalanced() && !e.getKey().getName().startsWith("A-")) {
                pool.add(e.getKey());
            }
        }
        pool.sort(Comparator.comparing(PaperCard::getName));
        return pool;
    }

    // ------------------------------------------------------------------

    /**
     * Garantiza que el mazo tenga base de mana, pase lo que pase.
     *
     * <p><b>Esto no es paranoia: es un fallo medido.</b> Los generadores del
     * motor devuelven de vez en cuando un mazo con <b>cero tierras</b> — una de
     * cada cuarenta aproximadamente, en los dos modos, comprobado con el
     * contador en bruto de {@link #lastRawLandCount()}. Un mazo sin tierras no
     * es un mazo flojo: es una run que se pierde en el primer nodo sin que el
     * jugador pueda hacer nada, y encima parecerria culpa del modo.
     *
     * <p>No se arregla en el motor (regla de oro) ni se reintenta la generacion
     * — reintentar tapa el sintoma y deja el mazo a merced de la siguiente
     * tirada. Se completa con <b>basicas de los colores que el mazo ya juega</b>,
     * que es lo que haria cualquiera montando el mazo a mano.
     *
     * <p>Si hace falta sitio, lo que se quita son los hechizos <b>mas caros</b>:
     * en un mazo de treinta cartas, la carta de coste siete es lo primero que
     * sobra.
     *
     * <p>⚠️ {@code wantLands} son las tierras que el mazo <b>necesita</b>, no
     * las que trajo el generador. Parece una obviedad y no lo es: la primera
     * version recibia {@code min(las que hay, las que hacen falta)}, asi que en
     * el unico caso que importa — el mazo que llega con <b>cero</b> tierras —
     * se le pedia "garantizame cero" y devolvia el mazo injugable tal cual. El
     * fallo no revienta y solo sale 1 de cada 40 mazos: lo caza la sonda 7 de
     * {@code ascentcheck}, y aun asi tardo dos corridas en aparecer.
     */
    private static void ensureManaBase(final Deck deck, final int wantLands, final int size) {
        int have = 0;
        // WUBRG y BASIC_LANDS estan en el MISMO orden (blanco, azul, negro,
        // rojo, verde), asi que el indice sirve para los dos.
        final java.util.Set<Integer> colors = new java.util.LinkedHashSet<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                have += e.getValue();
            } else {
                for (int i = 0; i < forge.card.MagicColor.WUBRG.length; i++) {
                    if (e.getKey().getRules().getColorIdentity()
                            .hasAnyColor(forge.card.MagicColor.WUBRG[i])) {
                        colors.add(i);
                    }
                }
            }
        }
        if (have >= wantLands) {
            return;
        }

        final List<PaperCard> basics = new ArrayList<>();
        for (final int idx : colors) {
            final PaperCard b = FModel.getMagicDb().getCommonCards()
                    .getCard(forge.card.MagicColor.Constant.BASIC_LANDS.get(idx));
            if (b != null) {
                basics.add(b);
            }
        }
        if (basics.isEmpty()) {
            // Mazo sin un solo hechizo de color (todo artefactos): las basicas
            // de color no le sirven de nada, pero un Yermo si.
            final PaperCard wastes = FModel.getMagicDb().getCommonCards().getCard("Wastes");
            if (wastes != null) {
                basics.add(wastes);
            } else {
                return; // sin nada que anyadir, mejor dejarlo como esta que reventar
            }
        }

        int i = 0;
        while (have < wantLands) {
            if (deck.getMain().countAll() >= size && !dropCostliestSpell(deck)) {
                break; // no queda hechizo que quitar: el mazo se queda como esta
            }
            deck.getMain().add(basics.get(i++ % basics.size()));
            have++;
        }
    }

    /** Quita el hechizo mas caro del mazo. Devuelve si quito alguno. */
    private static boolean dropCostliestSpell(final Deck deck) {
        PaperCard worst = null;
        int worstCmc = -1;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                continue;
            }
            final int cmc = e.getKey().getRules().getManaCost().getCMC();
            if (cmc > worstCmc) {
                worstCmc = cmc;
                worst = e.getKey();
            }
        }
        if (worst == null) {
            return false;
        }
        deck.getMain().remove(worst);
        return true;
    }

    /**
     * Recorta un mazo del motor a {@code size} cartas, sin romperlo.
     *
     * <p>Tierras primero, hasta la proporcion; luego hechizos por coste
     * ascendente. Lo que sobra se tira. Y al final,
     * {@link #ensureManaBase} se asegura de que haya con que jugar.
     */
    /** Cuantas tierras trajo el ultimo mazo del generador, ANTES de recortar. */
    private static volatile int lastRawLands = -1;

    /** Solo para el comprobador: distinguir un fallo del generador de uno del recorte. */
    public static int lastRawLandCount() {
        return lastRawLands;
    }

    private static Deck trim(final Deck full, final PaperCard commander,
                             final int size, final String name) {
        final List<PaperCard> lands = new ArrayList<>();
        final List<PaperCard> spells = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : full.getMain()) {
            for (int i = 0; i < e.getValue(); i++) {
                if (e.getKey().getRules().getType().isLand()) {
                    lands.add(e.getKey());
                } else {
                    spells.add(e.getKey());
                }
            }
        }
        // Los hechizos baratos primero: una run empieza con poco mana, y un
        // mazo de bombas es un mazo que pierde el primer nodo.
        lastRawLands = lands.size();
        spells.sort(Comparator.comparingInt(c -> c.getRules().getManaCost().getCMC()));

        // Dos numeros distintos, y confundirlos fue un fallo de verdad:
        //   needLands  cuantas tierras hacen falta para que el mazo se pueda jugar
        //   takeLands  cuantas se pueden coger de las que trajo el generador
        // Pasandole a ensureManaBase el segundo, un mazo que llegaba con CERO
        // tierras le pedia "garantizame cero" y se iba de vacio: justo el caso
        // que esa funcion existe para tapar. Ver el comentario de ensureManaBase.
        final int needLands = (int) Math.round(size * LAND_RATIO);
        final int takeLands = Math.min(lands.size(), needLands);
        final Deck out = new Deck(name);
        for (int i = 0; i < takeLands; i++) {
            out.getMain().add(lands.get(i));
        }
        for (int i = 0; i < spells.size() && out.getMain().countAll() < size; i++) {
            out.getMain().add(spells.get(i));
        }
        // Si el generador dio pocos hechizos, se completa con tierras: mejor un
        // mazo con tierras de mas que uno corto, que barajaria distinto.
        for (int i = takeLands; i < lands.size() && out.getMain().countAll() < size; i++) {
            out.getMain().add(lands.get(i));
        }

        ensureManaBase(out, needLands, size);

        if (commander != null) {
            out.getOrCreate(DeckSection.Commander).add(commander);
        }
        return out;
    }
}
