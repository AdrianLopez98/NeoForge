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
import forge.deck.DeckFormat;
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
 * <h2>Y por eso suben de calidad nodo a nodo</h2>
 *
 * <p>El suelo son las <b>infrecuentes</b>: las comunes no se ofrecen. Por
 * encima, la rareza se <b>tira carta a carta</b> contra la altura de la run
 * ({@link AscentBattle#progress}), no contra el acto:
 *
 * <table>
 *   <tr><th></th><th>infrecuente</th><th>rara</th><th>mitica</th>
 *       <th>gamechanger</th></tr>
 *   <tr><th>primer nodo</th><td>65%</td><td>30%</td><td>5%</td><td>—</td></tr>
 *   <tr><th>mitad de la run</th><td>~33%</td><td>~43%</td><td>~20%</td>
 *       <td>~2%</td></tr>
 *   <tr><th>el jefe final</th><td>10%</td><td>55%</td><td>35%</td>
 *       <td>20%</td></tr>
 * </table>
 *
 * <p><b>Contra la altura y no contra el acto</b>, y eso es lo que se reporto
 * jugando (05-09-2026): <i>"practicamente en el piso 1 hasta el boss no he
 * mejorado apenas"</i>. Con la calidad atada al acto, los doce nodos de un acto
 * ofrecen lo mismo — mientras el rival, que si va por altura, sube en cada uno.
 *
 * <p><b>Y el 5% de mitica ya en el primer nodo no es un descuido</b>: es la
 * tirada de <i>Binding of Isaac</i>, donde la sala 1 puede darte un objeto que
 * cambia la partida. Sin ella, los primeros premios no tienen nada en juego.
 *
 * <p>Arriba del todo entran los <b>gamechangers</b>, que no son una lista
 * nuestra: es {@code res/lists/gamechangers.txt}, la que el motor usa para
 * calcular el bracket de un mazo de Commander — el mismo sistema con el que
 * {@link AscentSeedDeck} limita el mazo de salida.
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

    /**
     * Cuantas cartas se ofrecen <b>por carta que te llevas</b>.
     *
     * <p>Tres, y no es un numero al azar: elegir 1 de 3 es el bucle del genero
     * entero. Lo que cambia entre modos no es esta proporcion sino cuantas
     * cartas te llevas — ver {@link #choices} y {@link AscentRun#cardBatch()}.
     */
    public static final int CHOICES = 3;

    /**
     * Cuantas cartas ensenya un nodo en ese modo: <b>3 en Estandar, 6 en
     * Commander</b>.
     *
     * <p>Es {@link #CHOICES} por lo que se lleva uno ({@link
     * AscentRun#cardBatch()}), asi que la <b>proporcion se mantiene</b>: sigue
     * siendo 1 de cada 3, solo que en Commander se hace dos veces. Doblar solo
     * las que te llevas sin doblar las ofrecidas habria hecho el premio mas
     * generoso Y menos interesante a la vez — con 2 de 3, elegir es casi
     * descartar.
     */
    public static int choices(final AscentRun.Mode mode) {
        return CHOICES * AscentRun.cardBatch(mode);
    }

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
        /**
         * Cuantas de las cartas ofrecidas te puedes llevar.
         *
         * <p>1 en Estandar y <b>2 en Commander</b> ({@link
         * AscentRun#cardBatch()}): ahi el mazo es de 60 cartas y una sola no se
         * notaria. Cambiar por tierras gasta <b>una</b> de estas, asi que en
         * Commander se puede coger una carta y dos tierras, o cuatro tierras.
         */
        public final int picks;
        /**
         * Las tierras que se pueden coger <b>en vez de</b> una carta.
         *
         * <p>Van en el premio y no se piden aparte por la misma razon que todo
         * lo demas: sembradas con la clave del nodo, o sea que <b>no se pueden
         * rerodar</b> saliendo del juego y volviendo. Ver {@link #landsFor}.
         */
        public final List<PaperCard> lands;

        Reward(final List<PaperCard> cards, final int credits,
               final List<AscentRelic> relics, final boolean chooseOne, final int picks,
               final List<PaperCard> lands) {
            this.cards = Collections.unmodifiableList(cards);
            this.credits = credits;
            this.relics = Collections.unmodifiableList(relics);
            this.chooseOne = chooseOne;
            this.picks = picks;
            this.lands = Collections.unmodifiableList(lands);
        }

        /** La unica reliquia de un nodo que da una sola, o {@code null}. */
        public AscentRelic singleRelic() {
            return chooseOne || relics.isEmpty() ? null : relics.get(0);
        }

        @Override
        public String toString() {
            return cards.size() + " cartas (eliges " + picks + ") | " + credits + " creditos"
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
        // La MISMA altura con la que AscentBattle calcula al rival: si el
        // premio subiera solo al cambiar de acto, los doce nodos de un acto
        // ofrecerian lo mismo mientras enfrente la cosa sube nodo a nodo — que
        // es justo lo que se reporto ("del piso 1 al jefe no he mejorado apenas").
        final double climb = AscentBattle.progress(act, node.getRow());
        // Cuantas te llevas. Las RELIQUIAS no escalan: una reliquia es una
        // pasiva permanente, no una carta del mazo — dos por jefe convertiria
        // el acto siguiente en un paseo, que es justo lo que playerEdge existe
        // para evitar.
        final int picks = run.cardBatch();
        switch (node.getKind()) {
            case COMBAT:
                return new Reward(pickCards(run, climb, rnd),
                        credits(run, CREDITS_BY_ACT[act - 1], rnd), List.of(), false, picks,
                        landsFor(run, act, rnd));
            case ELITE:
                // Una elite da carta, mas creditos Y reliquia: es el nodo que
                // se elige a proposito sabiendo que puede costarte la run.
                return new Reward(pickCards(run, climb, rnd),
                        credits(run, CREDITS_BY_ACT[act - 1] * 2, rnd),
                        one(relic(run, AscentRelic.Rarity.RARE, rnd)), false, picks,
                        landsFor(run, act, rnd));
            case BOSS:
                // TRES reliquias de jefe, y eliges. Es el momento en el que se
                // decide si el acto siguiente se aguanta.
                return new Reward(pickCards(run, climb, rnd),
                        credits(run, CREDITS_BY_ACT[act - 1] * 3, rnd),
                        relics(run, AscentRelic.Rarity.BOSS, RELIC_CHOICES, rnd), true, picks,
                        landsFor(run, act, rnd));
            case TREASURE:
                return new Reward(new ArrayList<>(), 0,
                        one(relic(run, AscentRelic.Rarity.COMMON, rnd)), false, picks, List.of());
            default:
                // Descanso, tienda y evento no dan premio: lo suyo lo decide su
                // propia pantalla.
                return new Reward(new ArrayList<>(), 0, List.of(), false, picks, List.of());
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

    /**
     * Cuantas copias entran al coger la tierra del premio. <b>Una.</b>
     *
     * <p>Eran dos cuando la tierra <b>costaba</b> tu carta: una sola a cambio
     * de una rara de acto 3 no la habria cogido nadie, y una opcion que nadie
     * coge no es una opcion. Desde el 05-09-2026 la tierra es <b>gratis</b>
     * (§24.9), asi que esa razon desaparece — y la de enfrente aparece:
     * <i>"que puedas llevartela o no, no que te la den siempre, porque entonces
     * al final de la run acabas con tierras de mas"</i>.
     *
     * <p>Una por nodo y opcional: si la coges siempre, el mazo mantiene su
     * proporcion de tierras mientras crece; si no, no.
     */
    public static final int LANDS_INSTEAD = 1;

    /**
     * Cambia la carta del premio por <b>tierras basicas</b>.
     *
     * <h2>Por que existe</h2>
     *
     * <p>Reportado jugando (05-09-2026): <i>"tu vas anyadiendo cartas al mazo
     * pero no tenemos la opcion de anyadir tierras"</i>. Y la cuenta le daba la
     * razon — empiezas con 30 cartas y 12 tierras, terminas con 40 y <b>las
     * mismas 12</b>, porque lo que el premio ofrece son siempre hechizos (y
     * debe serlo: una tierra entre las tres opciones seria una opcion vacia).
     * O sea que la run entera empeoraba tu base de mana sin que hubiera ni un
     * sitio donde arreglarla.
     *
     * <h2>Por que aqui y no en el descanso</h2>
     *
     * <p>Porque es <b>al mismo ritmo al que crece el mazo</b>: se ofrece
     * exactamente en el momento en el que el mazo gana cartas. En el descanso
     * habria competido con curarse, que es otra pregunta.
     *
     * <h2>Y no cuesta tu carta</h2>
     *
     * <p>Al principio si: cambiabas la carta por tierras. Reportado jugando
     * (05-09-2026): <i>"que no te haga elegir entre tierra o criatura, porque
     * todo escala muy rapido"</i> — y tenia razon, sobre todo en Estandar,
     * donde con <b>una</b> sola eleccion arreglar el mana significaba renunciar
     * al premio entero mientras enfrente la curva seguia subiendo.
     *
     * <p>Ahora es un <b>extra opcional</b>: te llevas tus cartas <i>y</i>, si
     * quieres, una tierra. Una, y hay que cogerla — no se regala — porque
     * <i>"si te la dan siempre, al final de la run acabas con tierras de
     * mas"</i>.
     *
     * @return el mazo ya con las tierras dentro, o {@code null} si no habia mazo
     */
    public static Deck takeLands(final AscentRun run, final PaperCard land) {
        final Deck deck = AscentDecks.load(run);
        if (deck == null || land == null) {
            return deck;
        }
        // ⚠️ NO son siempre dos: en Commander una tierra que no sea basica es
        // de UNA copia. Lo decide el motor, no nosotros. Ver copiesOf().
        final int copies = copiesOf(run, land);
        if (copies <= 0) {
            return deck;
        }
        deck.getMain().add(land, copies);
        AscentDecks.save(deck);
        return deck;
    }

    /**
     * <b>Cuantas copias entran de verdad</b> al cambiar la carta por esa tierra.
     *
     * <h2>La trampa del singleton</h2>
     *
     * <p>{@link #LANDS_INSTEAD} son <b>dos</b>, y en Commander eso es ilegal
     * para todo lo que no sea una basica: el formato es de <b>una copia</b>. Dos
     * Tumbas Cenagosas no son un mazo arriesgado, son un mazo que el motor
     * rechaza — y aqui eso significaria una run muerta por una opcion que el
     * propio juego te ofrecio.
     *
     * <p>No se codifica la excepcion a mano ("las basicas no cuentan"): la
     * contesta <b>el motor</b>, con {@code DeckFormat.getMaxCardCopies(card)},
     * que ya sabe de basicas, de <i>Wastes</i> y de las cartas que dicen "puedes
     * tener cualquier numero". Es la misma regla que aplica el deck builder.
     *
     * @return cuantas caben, entre 0 y {@link #LANDS_INSTEAD}
     */
    public static int copiesOf(final AscentRun run, final PaperCard land) {
        if (land == null) {
            return 0;
        }
        final DeckFormat format = formatOf(run.getMode());
        final int max = format.getMaxCardCopies(land);
        int have = 0;
        final Deck deck = AscentDecks.load(run);
        if (deck != null) {
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                // Por NOMBRE, que es como cuenta el motor: dos impresiones
                // distintas de la misma tierra siguen siendo la misma carta.
                if (e.getKey().getName().equals(land.getName())) {
                    have += e.getValue();
                }
            }
        }
        return Math.max(0, Math.min(LANDS_INSTEAD, max - have));
    }

    /** Con que reglas se construye el mazo de este modo. Lo dice el motor. */
    private static DeckFormat formatOf(final AscentRun.Mode mode) {
        return (mode == AscentRun.Mode.COMMANDER
                ? forge.game.GameType.Commander
                : forge.game.GameType.Constructed).getDeckFormat();
    }

    /**
     * Cuantas tierras <b>no basicas</b> puede ensenyar un nodo, y con que
     * probabilidad cada una.
     *
     * <p>Sube con el acto a proposito. Pedido asi (05-09-2026): <i>"que no solo
     * sean basicas, que haya posibilidad de duales o triples, obviamente con
     * menos chance que las basicas, pero que a raiz que vas avanzando sea mas
     * comun"</i>. Es la probabilidad de que <b>cada hueco</b> de tierra saque
     * una buena en vez de una basica.
     */
    private static final double[] FANCY_CHANCE = {0.35, 0.60, 0.85};


    /**
     * Las tierras que ofrece un nodo: las basicas <b>siempre</b>, y con suerte
     * alguna buena.
     *
     * <h2>Que cuenta como "buena", sin inventarse un criterio</h2>
     *
     * <p>Una tierra no basica entra si esta dentro de tus colores (lo mismo que
     * exigen las cartas), si su rareza es la del acto —{@link #rarityFits}, la
     * misma escalera que todo lo demas del modo— <b>y</b> si ademas cumple una
     * de estas dos:
     *
     * <ul>
     *   <li>produce <b>dos colores o mas</b> de los tuyos: una dual o una
     *       triple, que es lo que de verdad arregla una base de mana;
     *   <li>o es <b>rara o mitica</b>: ahi caben las monocolor y las incoloras
     *       que valen por si solas (una <i>Nykthos</i>, una <i>Ancient Tomb</i>).
     * </ul>
     *
     * <p>Eso es literalmente lo que se pidio — <i>"duales o triples o monocolor
     * pero muy buenas"</i> — y de regalo hace la progresion sola: en el acto 1
     * solo hay comunes e infrecuentes, o sea que <b>solo</b> pueden salir
     * duales; a partir del 2 entran las raras.
     *
     * <p>⚠️ En Commander se descarta la que <b>ya lleves</b>: el formato es de
     * una copia, asi que ofrecerla seria ofrecer un boton que no hace nada.
     */
    public static List<PaperCard> landsFor(final AscentRun run, final int act, final Random rnd) {
        final int slots = run.cardBatch();
        final double chance = FANCY_CHANCE[Math.max(0, Math.min(2, act - 1))];
        final List<PaperCard> fancy = fancyLandPool(run, act);
        final List<PaperCard> basics = basicsByNeed(run);
        final List<PaperCard> out = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        int next = 0;
        for (int i = 0; i < slots; i++) {
            PaperCard pick = null;
            if (!fancy.isEmpty() && rnd.nextDouble() < chance) {
                for (int t = 0; t < 60 && pick == null; t++) {
                    final PaperCard c = fancy.get(rnd.nextInt(fancy.size()));
                    if (!seen.contains(c.getName())) {
                        pick = c;
                    }
                }
            }
            while (pick == null && next < basics.size()) {
                final PaperCard c = basics.get(next++);
                if (!seen.contains(c.getName())) {
                    pick = c;
                }
            }
            if (pick == null) {
                // Se acabaron las opciones DISTINTAS. Pasa de verdad: un mazo
                // monocolor en el acto 1 solo tiene una tierra que exista — su
                // basica, porque una dual necesita dos colores y las raras no
                // entran hasta el acto 2. Ofrecer una es lo correcto; repetir
                // la misma Montanya en los dos huecos seria ensenyar dos veces
                // lo mismo y llamarlo eleccion.
                break;
            }
            seen.add(pick.getName());
            out.add(pick);
        }
        return out;
    }

    /**
     * Las basicas <b>ordenadas por la que te falta</b>.
     *
     * <h2>Por que no salen todas, y por que en este orden</h2>
     *
     * <p>El premio ensenya {@link AscentRun#cardBatch()} tierras, no la lista
     * entera de tus colores: eliges {@code picks} cosas de las
     * {@link #choices} cartas <b>mas</b> estas, todo del mismo monton — <i>"seis
     * conjuros y dos tierras, y eliges dos"</i>, que pueden ser una dual y una
     * criatura. Ensenyar las cinco basicas de un comandante de cinco colores no
     * anyadia una sola decision: solo te puedes llevar dos igual.
     *
     * <p>Pero con dos huecos, cual sale <b>si</b> importa, y dejarlo al azar
     * convertiria arreglar la base de mana en una tombola. Sale la del color
     * del que <b>menos tienes</b>, que es exactamente la que cogerias tu. No es
     * una ayuda de mas: la decision sigue siendo <i>tierra o carta</i>, que es
     * la que importa.
     */
    private static List<PaperCard> basicsByNeed(final AscentRun run) {
        final List<PaperCard> basics = new ArrayList<>(basicLandsFor(run));
        final Deck deck = AscentDecks.load(run);
        final Map<String, Integer> have = new java.util.HashMap<>();
        if (deck != null) {
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                have.merge(e.getKey().getName(), e.getValue(), Integer::sum);
            }
        }
        // Por nombre a igualdad de cuenta: sin desempate, dos runs con el mismo
        // mazo darian ordenes distintos segun como salga el recorrido del pool.
        basics.sort(java.util.Comparator
                .comparingInt((PaperCard c) -> have.getOrDefault(c.getName(), 0))
                .thenComparing(PaperCard::getName));
        return basics;
    }

    /** El pozo de tierras buenas de ese acto. Ver {@link #landsFor}. */
    private static List<PaperCard> fancyLandPool(final AscentRun run, final int act) {
        final ColorSet allowed = colorsOf(run);
        final boolean singleton = run.getMode() == AscentRun.Mode.COMMANDER;
        final Set<String> owned = new HashSet<>();
        if (singleton) {
            final Deck deck = AscentDecks.load(run);
            if (deck != null) {
                for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                    owned.add(e.getKey().getName());
                }
            }
        }
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard c : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (c.getRules() == null || !c.getRules().getType().isLand()
                    || c.getRules().getType().isBasicLand()) {
                continue;
            }
            if (!rarityFits(c.getRarity(), act)) {
                continue;
            }
            final ColorSet id = c.getRules().getColorIdentity();
            if (!id.hasNoColorsExcept(allowed)) {
                continue;
            }
            // Dual o triple, o buena de verdad. Una tierra infrecuente que solo
            // produce un color y entra girada no es un premio, es un hueco.
            if (id.countColors() < 2
                    && c.getRarity() != CardRarity.Rare
                    && c.getRarity() != CardRarity.MythicRare) {
                continue;
            }
            if (singleton && owned.contains(c.getName())) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /**
     * Que basicas se pueden coger: las de los colores en los que se premia.
     *
     * <p>En Commander eso es la identidad del comandante, o sea que no se puede
     * ensanchar — meter una Isla en un mazo verde-rojo dejaria el mazo
     * <b>ilegal</b> y el motor lo rechazaria al empezar la partida siguiente.
     * En Estandar son los colores del mazo, y ahi <b>si</b> se ensancha: ver
     * {@link #colorsOf}.
     *
     * <p>Un mazo sin un solo simbolo de color (todo artefactos) recibe
     * {@code Wastes}, que es lo que hace {@code AscentSeedDeck.ensureManaBase}
     * por lo mismo: las basicas de color no le sirven de nada.
     */
    public static List<PaperCard> basicLandsFor(final AscentRun run) {
        final ColorSet allowed = colorsOf(run);
        final List<PaperCard> out = new ArrayList<>();
        for (int i = 0; i < forge.card.MagicColor.WUBRG.length; i++) {
            if (!allowed.hasAnyColor(forge.card.MagicColor.WUBRG[i])) {
                continue;
            }
            final PaperCard basic = FModel.getMagicDb().getCommonCards()
                    .getCard(forge.card.MagicColor.Constant.BASIC_LANDS.get(i));
            if (basic != null) {
                out.add(basic);
            }
        }
        if (out.isEmpty()) {
            final PaperCard wastes = FModel.getMagicDb().getCommonCards().getCard("Wastes");
            if (wastes != null) {
                out.add(wastes);
            }
        }
        return out;
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
    private static List<PaperCard> pickCards(final AscentRun run, final double climb,
                                             final Random rnd) {
        return offer(run, climb, rnd, choices(run.getMode()));
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
        // Sin nodo no hay altura exacta: se toma la mitad del acto, que es el
        // valor medio de lo que ese acto ofrece. Lo usan el evento del maestro
        // y cualquier sitio que pida cartas sin estar en un nodo.
        return offer(run, midClimb(act), rnd, count);
    }

    /**
     * El mismo sorteo, pero sabiendo <b>lo alto que estas</b>.
     *
     * <p>La altura (0 en el primer nodo del acto 1, 1 en el jefe del acto 3) es
     * la misma que usa {@link AscentBattle#progress} para la dificultad, y por
     * eso se pide en vez del acto: el rival crece nodo a nodo, y si el premio
     * solo crecia al cambiar de mapa, los doce nodos de un acto ofrecian lo
     * mismo mientras enfrente subia la cosa.
     */
    public static List<PaperCard> offer(final AscentRun run, final double climb,
                                        final Random rnd, final int count) {
        final Pools pools = poolsFor(run);
        final List<PaperCard> out = new ArrayList<>();
        if (pools.isEmpty()) {
            return out;
        }
        final Set<String> seen = new HashSet<>();
        // Se tira un numero acotado de veces en vez de barajar el pozo entero:
        // son miles de cartas y esto se llama al acabar cada combate.
        for (int tries = 0; tries < 400 && out.size() < count; tries++) {
            final PaperCard c = pools.roll(climb, rnd);
            if (c != null && seen.add(c.getName())) {
                out.add(c);
            }
        }
        return out;
    }

    /** La altura del punto medio de un acto, para quien no tiene nodo. */
    private static double midClimb(final int act) {
        return AscentBattle.progress(act, AscentMap.ROWS / 2);
    }

    // ------------------------------------------------------------------
    //  De que calidad es una carta de premio
    // ------------------------------------------------------------------

    /**
     * La probabilidad de que una carta ofrecida sea <b>mitica</b>, abajo del
     * todo y arriba del todo.
     *
     * <p>Reportado jugando (05-09-2026): <i>"siento que practicamente en el
     * piso 1 hasta el boss no he mejorado apenas... en Binding of Isaac existe
     * la chance de que en la sala 1 te den un super item, aqui estamos dando
     * cartas de mierda"</i>.
     *
     * <p>Por eso el suelo <b>no</b> es cero: ya en el primer nodo hay un 5% de
     * que salga una mitica. Es la tirada de Isaac — lo que hace que abrir el
     * premio tenga algo en juego aunque estes empezando.
     */
    private static final double MYTHIC_LOW = 0.05;
    private static final double MYTHIC_HIGH = 0.35;

    /** Y de que sea rara. El resto, infrecuente. */
    private static final double RARE_LOW = 0.30;
    private static final double RARE_HIGH = 0.55;

    /**
     * Cuando empiezan a salir <b>gamechangers</b>, y hasta cuanto.
     *
     * <p>Nada hasta pasada casi la mitad de la run, y de ahi hacia arriba hasta
     * una de cada cinco. Es lo que se pidio — <i>"que a medida que avanzas
     * mejoren aun mas, llegando a que sean staples y gamechangers"</i> — y lo
     * que hace que el acto 3 no se sienta como el 1 con numeros mas grandes.
     */
    private static final double GC_START = 0.45;
    private static final double GC_TOP = 0.20;

    /** Que parte de las cartas ofrecidas a esa altura son gamechangers. */
    public static double gameChangerChance(final double climb) {
        final double c = Math.max(0.0, Math.min(1.0, climb));
        return c <= GC_START ? 0.0 : GC_TOP * (c - GC_START) / (1.0 - GC_START);
    }

    /** Y que parte son miticas. */
    public static double mythicChance(final double climb) {
        final double c = Math.max(0.0, Math.min(1.0, climb));
        return MYTHIC_LOW + (MYTHIC_HIGH - MYTHIC_LOW) * c;
    }

    /**
     * Los pozos de un premio, <b>en una sola pasada</b>.
     *
     * <p>Antes se recorrian las 33.000 cartas una vez por premio para quedarse
     * con las de la rareza del acto. Ahora la rareza se tira <b>carta a
     * carta</b>, asi que harian falta tres pasadas — o una, repartiendo en
     * cubos, que es lo que hace esto.
     */
    private static final class Pools {
        private final List<PaperCard> uncommon = new ArrayList<>();
        private final List<PaperCard> rare = new ArrayList<>();
        private final List<PaperCard> mythic = new ArrayList<>();
        private final List<PaperCard> gameChangers = new ArrayList<>();

        boolean isEmpty() {
            return uncommon.isEmpty() && rare.isEmpty() && mythic.isEmpty();
        }

        /**
         * Una carta de la calidad que toque a esa altura.
         *
         * <p>Si el cubo que sale esta vacio se baja al siguiente: un mazo de un
         * color raro puede no tener ni una mitica jugable, y devolver
         * {@code null} ahi seria ofrecer dos cartas de tres.
         */
        PaperCard roll(final double climb, final Random rnd) {
            if (!gameChangers.isEmpty() && rnd.nextDouble() < gameChangerChance(climb)) {
                return gameChangers.get(rnd.nextInt(gameChangers.size()));
            }
            final double r = rnd.nextDouble();
            final double mythicP = mythicChance(climb);
            final double rareP = RARE_LOW + (RARE_HIGH - RARE_LOW) * Math.max(0, Math.min(1, climb));
            final List<List<PaperCard>> order = r < mythicP
                    ? List.of(mythic, rare, uncommon)
                    : r < mythicP + rareP
                    ? List.of(rare, mythic, uncommon)
                    : List.of(uncommon, rare, mythic);
            for (final List<PaperCard> bucket : order) {
                if (!bucket.isEmpty()) {
                    return bucket.get(rnd.nextInt(bucket.size()));
                }
            }
            return null;
        }
    }

    /**
     * Los pozos de un premio: colores del mazo, y repartidos por calidad.
     *
     * <p>Se recalcula en cada nodo a proposito: los colores del mazo cambian
     * dentro de la run (una carta incolora no, pero el comandante puede no ser
     * el unico que aporte identidad), y cachear dejaria de casar en cuanto eso
     * pasara. Recorrer 33.000 cartas una vez por combate no se nota al lado de
     * la propia partida.
     *
     * <p><b>Las comunes ya no entran.</b> Reportado jugando: en el acto 1 se
     * ofrecian comunes e infrecuentes, o sea que llegar al jefe con el mazo
     * casi igual que al empezar era lo normal. El suelo es <b>infrecuente</b>,
     * y por encima manda la altura de la run.
     */
    private static Pools poolsFor(final AscentRun run) {
        final ColorSet allowed = colorsOf(run);
        // ⚠️ En Commander no se ofrece lo que ya llevas. El formato es de UNA
        // copia, asi que la segunda dejaria el mazo ilegal — y el premio ni
        // siquiera pregunta: mete la carta y guarda. Salio al doblar el premio
        // a 2 de 6 (§24.6), que multiplica por dos las ocasiones de que pase,
        // pero el agujero estaba desde el principio.
        final Set<String> owned = new HashSet<>();
        if (run.getMode() == AscentRun.Mode.COMMANDER) {
            final Deck mine = AscentDecks.load(run);
            if (mine != null) {
                for (final Map.Entry<PaperCard, Integer> e : mine.getMain()) {
                    owned.add(e.getKey().getName());
                }
            }
        }
        final Set<String> changers = gameChangerNames();
        final Pools out = new Pools();
        for (final PaperCard c : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (owned.contains(c.getName())) {
                continue;
            }
            if (c.getRules() == null || c.getRules().getType().isLand()) {
                // Las tierras no se ofrecen ENTRE LOS CONJUROS: una tierra al
                // lado de dos hechizos es una opcion vacia, o sea ofrecer dos
                // opciones de tres. Van en su propio hueco del monton — ver
                // landsFor() y takeLands().
                continue;
            }
            final ColorSet id = c.getRules().getColorIdentity();
            if (!id.hasNoColorsExcept(allowed)) {
                continue;
            }
            // Un gamechanger va a SU cubo y solo a ese: si estuviera ademas en
            // el de su rareza, saldria por los dos lados y su probabilidad no
            // seria la que dice gameChangerChance().
            if (changers.contains(c.getName())) {
                out.gameChangers.add(c);
                continue;
            }
            switch (c.getRarity()) {
                case Uncommon:
                    out.uncommon.add(c);
                    break;
                case Rare:
                    out.rare.add(c);
                    break;
                case MythicRare:
                    out.mythic.add(c);
                    break;
                default:
                    // Comunes, fichas, especiales: fuera. El suelo es
                    // infrecuente (ver el javadoc de arriba).
                    break;
            }
        }
        return out;
    }

    /**
     * Las cartas que <b>Forge</b> marca como gamechanger.
     *
     * <p>No es una lista nuestra: es {@code res/lists/gamechangers.txt}, la que
     * el propio motor usa para calcular el <i>bracket</i> de un mazo de
     * Commander ({@code CommanderBracketCalculator}) — el mismo sistema con el
     * que {@link AscentSeedDeck} limita el mazo de salida. Inventarse una lista
     * de "cartas buenas" a mano seria mantenerla para siempre y equivocarse; y
     * al leer el fichero, las que Forge anyada manyana entran solas.
     *
     * <p>Se lee una vez: son 53 nombres y no cambian durante la partida.
     */
    private static Set<String> gameChangerNames() {
        Set<String> names = gameChangers;
        if (names != null) {
            return names;
        }
        names = new HashSet<>();
        for (final String line : forge.util.FileUtil.readFile(
                forge.localinstance.properties.ForgeConstants
                        .COMMANDER_BRACKET_GAMECHANGERS_FILE)) {
            // El fichero admite comentarios con '#', igual que lo lee Forge.
            final int hash = line.indexOf('#');
            final String name = (hash < 0 ? line : line.substring(0, hash)).trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        gameChangers = names;
        return names;
    }

    private static volatile Set<String> gameChangers;

    /** Que rarezas de TIERRA se ofrecen en cada acto. Ver {@link #fancyLandPool}. */
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
            final PaperCard c = e.getKey();
            if (!c.getRules().getType().isLand()) {
                // Por el coste, no por la identidad: lo que decide de que
                // colores es un mazo es lo que hay que PAGAR. Contar la
                // identidad mete el tercer color de una tierra que solo produce
                // mana (el plan de Ascenso 2b).
                mask |= c.getRules().getManaCost().getColorProfile();
            } else if (c.getRules().getType().isBasicLand()) {
                // ⚠️ Y las BASICAS si, desde el 05-09-2026. Es lo que hace que
                // {@link #basicLandsFor} sea una decision de verdad y no solo
                // un parche a la base de mana: reportado jugando — "si te daba
                // un mono rojo era todo rojo; ahora, si te metes una Isla, ya
                // tu mazo es azul y rojo y te puede mostrar cartas azules".
                //
                // Solo las basicas, no toda tierra: el motivo por el que las
                // tierras estaban fuera sigue en pie para las demas — una
                // tri-tierra que solo produce mana no convierte tu mazo en
                // tricolor. Una basica, en cambio, solo esta en el mazo porque
                // alguien la puso ahi a proposito.
                mask |= c.getRules().getColorIdentity().getColor();
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
