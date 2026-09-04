package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import forge.card.CardRarity;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.quest.DuelFace;

/**
 * El duelo de un nodo: contra quien juegas, con que vida y con que ventajas.
 *
 * <p><b>Aqui vive toda la curva de dificultad</b>, y a proposito en un solo
 * sitio: si estuviera repartida entre la pantalla del mapa, el que lanza la
 * partida y el que reparte premios, ajustar el modo seria ir a buscarla a tres
 * ficheros y ninguno diria la verdad entero.
 *
 * <h2>Las cinco palancas (el plan de Ascenso)</h2>
 *
 * <table>
 *   <tr><th></th><th>Acto 1</th><th>Acto 2</th><th>Acto 3</th></tr>
 *   <tr><th>Mazo del rival</th><td>tercio flojo</td><td>tercio medio</td>
 *       <td>tercio fuerte</td></tr>
 *   <tr><th>Vida del rival</th><td>20</td><td>25</td><td>30</td></tr>
 *   <tr><th>Perfil de IA</th><td>Cautious</td><td>Default</td><td>Reckless</td></tr>
 *   <tr><th>Ventaja de salida</th><td>ninguna</td><td>una tierra en mesa</td>
 *       <td>dos tierras</td></tr>
 *   <tr><th>El plano</th><td colspan="3">fase 3</td></tr>
 * </table>
 *
 * <p>Y encima de eso, el nodo: una <b>elite</b> sube un escalon de mazo, lleva
 * cinco vidas mas y una <b>reliquia suya</b>; el <b>jefe</b> juega de
 * archienemigo con su mazo de esquemas, 40 vidas y dos reliquias.
 *
 * <h2>Ninguna de las cinco es una regla nuestra</h2>
 *
 * <p>Los mazos salen de los 505 preconstruidos que trae Forge (y de los 173 de
 * Commander), la vida es {@code setStartingLife}, el perfil de IA es un fichero
 * de {@code res/ai}, la ventaja es {@code addExtraCardsOnBattlefield} y los
 * esquemas son {@code DeckgenUtil.generateSchemePool}. Lo unico nuestro es
 * <b>que pedir</b>.
 *
 * <h2>El rival no se sortea dos veces</h2>
 *
 * <p>Contra quien juegas en un nodo sale de la semilla de la run mezclada con
 * la <b>clave del nodo</b>, no de un {@code Random} suelto. O sea que es el
 * mismo rival aunque cierres el juego y vuelvas — que es lo que impide mirar
 * contra quien te toca, salir y volver a entrar hasta que salga uno comodo. Es
 * la misma razon por la que el mapa se guarda por semilla ({@link AscentRun}).
 *
 * <p>Cero JavaFX: {@link AscentCheck} recorre una run entera sin ventana.
 */
public final class AscentBattle {

    private AscentBattle() {
    }

    /**
     * La vida del rival, en proporcion a la TUYA, del primer nodo al ultimo.
     *
     * <p><b>Los primeros rivales tienen que ser un paseo</b>, y esto es lo que
     * lo consigue. Con 20 de vida arrastrada, el primer nodo enfrenta a alguien
     * con <b>6</b>: sales con una ventaja enorme, que es exactamente lo que
     * pasa en <i>Slay the Spire</i> cuando la primera pelea es un bicho al
     * azar. Un roguelike que te mata en el nodo 1 con el mazo de salida no es
     * dificil, es injusto — y encima lo que has perdido son cuarenta minutos
     * que todavia no habias jugado.
     *
     * <p>Y por eso es una <b>proporcion</b> y no un numero: en Commander sales
     * con 40, asi que el primer rival tiene 12 y el ultimo 50. La curva se
     * siente igual en los dos modos sin escribirla dos veces.
     */
    private static final double LIFE_START = 0.35;

    /** Y al llegar arriba. Por encima de la tuya: para entonces tu mazo es otro. */
    private static final double LIFE_END = 1.60;

    /** Ninguno baja de aqui, pase lo que pase. Un rival de 2 vidas no es un duelo. */
    private static final int MIN_LIFE = 5;

    /** Lo que multiplica la vida de una elite sobre la del combate de su altura. */
    private static final double ELITE_LIFE = 1.4;

    /**
     * Y la de un jefe.
     *
     * <p>No son 40 fijas. El jefe del acto 1 con 40 mientras los combates de su
     * alrededor tienen 12 no es "un jefe": es un muro contra el que se estrella
     * un mazo que todavia no existe. Lo que hace jefe a un jefe son <b>los
     * esquemas y sus dos reliquias</b>, no el saco de vida — y la vida del
     * archienemigo (CR 904.5) la pisamos igual con {@code setStartingLife},
     * asi que no hay ninguna regla que respetar aqui.
     */
    private static final double BOSS_LIFE = 2.0;

    /**
     * Cuanto sube el jefe en la escalera de mazos, por encima de su altura.
     *
     * <p>0,25 y no 0,15 (lo de una elite): en el acto 1 eso pone al jefe en el
     * escalon 1 mientras los combates de su piso siguen en el 0, que es
     * exactamente lo que se pidio — <i>"un mazo un pelin mejor que el del
     * jugador y el de los enemigos de ese piso"</i>. Ver {@code tierFor}, que
     * corta en 0,34 y 0,70.
     */
    private static final double BOSS_DECK_EDGE = 0.25;

    /**
     * Los caracteres de IA, del mas blando al mas agresivo.
     *
     * <p>Son ficheros de {@code forge-gui/res/ai}. Se reparten por <b>altura de
     * la run</b>, no por acto: un rival del final del acto 1 ya juega distinto
     * que el del primer nodo.
     */
    private static final String[] AI_LADDER = {"Cautious", "Default", "Reckless"};

    // ------------------------------------------------------------------
    //  El plan
    // ------------------------------------------------------------------

    /**
     * Todo lo que hace falta para montar el duelo de un nodo.
     *
     * <p>Es un dato, no una accion: se puede calcular, imprimir y comprobar sin
     * abrir una partida. Eso es lo que permite <b>medir</b> que la curva de
     * dificultad sube de verdad, en vez de suponerlo.
     */
    public static final class Plan {
        public final AscentNode.Kind kind;
        public final int act;
        /** El mazo del rival, tal cual lo trae Forge. */
        public final Deck opponentDeck;
        /** Con que carta se presenta. {@code null} si el mazo no da ninguna. */
        public final PaperCard opponentFace;
        /** Con cuanta vida sales tu: la que arrastra la run. */
        public final int yourLife;
        /** Y el rival. */
        public final int opponentLife;
        /** El caracter de la IA, un fichero de {@code res/ai}. */
        public final String aiProfile;
        /** Las tierras que el rival tiene ya en la mesa al empezar. */
        public final List<PaperCard> opponentHeadStart;
        /** Las reliquias del rival: solo elites y jefes tienen. */
        public final List<PaperCard> opponentRelics;
        /** Las tuyas, las que llevas ganadas en la run. */
        public final List<PaperCard> yourRelics;
        /** El mazo de esquemas del jefe, o {@code null} si el nodo no es un jefe. */
        public final CardPool schemes;
        /** Las variantes que se aplican: la del modo y, en el jefe, Archenemy. */
        public final EnumSet<GameType> variants;

        Plan(final AscentNode.Kind kind, final int act, final Deck opponentDeck,
             final PaperCard opponentFace, final int yourLife, final int opponentLife,
             final String aiProfile, final List<PaperCard> opponentHeadStart,
             final List<PaperCard> opponentRelics, final List<PaperCard> yourRelics,
             final CardPool schemes, final EnumSet<GameType> variants) {
            this.kind = kind;
            this.act = act;
            this.opponentDeck = opponentDeck;
            this.opponentFace = opponentFace;
            this.yourLife = yourLife;
            this.opponentLife = opponentLife;
            this.aiProfile = aiProfile;
            this.opponentHeadStart = Collections.unmodifiableList(opponentHeadStart);
            this.opponentRelics = Collections.unmodifiableList(opponentRelics);
            this.yourRelics = Collections.unmodifiableList(yourRelics);
            this.schemes = schemes;
            this.variants = variants;
        }

        /** Si el rival juega de archienemigo. */
        public boolean isBoss() {
            return kind == AscentNode.Kind.BOSS;
        }

        @Override
        public String toString() {
            return "acto " + act + " " + kind + " | " + name(opponentDeck)
                    + " | vidas " + yourLife + " vs " + opponentLife
                    + " | IA " + aiProfile
                    + (opponentHeadStart.isEmpty() ? "" : " | ventaja " + opponentHeadStart.size())
                    + (opponentRelics.isEmpty() ? "" : " | reliquias rival " + opponentRelics.size())
                    + (schemes == null ? "" : " | esquemas " + schemes.countAll());
        }
    }

    /**
     * Monta el duelo de ese nodo.
     *
     * @throws IllegalArgumentException si el nodo no es de pelear
     */
    public static Plan plan(final AscentRun run, final AscentNode node) {
        if (!isBattle(node.getKind())) {
            throw new IllegalArgumentException("el nodo " + node + " no es un combate");
        }
        final int act = clampAct(run.getAct());
        final Random rnd = rng(run, node);
        final AscentNode.Kind kind = node.getKind();
        final boolean tough = kind != AscentNode.Kind.COMBAT;

        // Cuanto has subido en la run entera, de 0 (primer nodo del acto 1) a 1
        // (el jefe del acto 3). TODA la curva cuelga de aqui, y por eso sube
        // dentro de cada acto y no solo al cambiar de acto: entre el nodo 1 y
        // el 12 tu mazo ya ha crecido en once cartas.
        double climb = progress(act, node.getRow());
        if (run.getAscension() >= 4 && kind == AscentNode.Kind.COMBAT) {
            // Ascension 4: los rivales normales pelean como los de un acto mas arriba.
            climb = Math.min(1.0, climb + 1.0 / AscentRun.ACTS);
        }
        // Una elite o un jefe se pelean como algo de mas arriba que el sitio
        // donde estan: es lo que hace que elegirlos sea una apuesta.
        // El jefe sube MAS que una elite. Lo pidio el jugador al quitarle los
        // esquemas — "un mazo un pelin mejor que el del jugador y el de los
        // enemigos de ese piso" — y es lo que le queda para diferenciarse:
        // sin Archienemigo, un jefe con el mazo de un combate normal solo se
        // distinguiria por tener el doble de vida, que es aburrido.
        final double effective = kind == AscentNode.Kind.BOSS
                ? Math.min(1.0, climb + BOSS_DECK_EDGE)
                : tough ? Math.min(1.0, climb + 0.15) : climb;

        final Deck deck = pickDeck(run.getMode(), tierFor(effective), rnd);

        int life = lifeAt(run.getMaxLife(), climb, playerEdge(run));
        if (kind == AscentNode.Kind.ELITE) {
            life = (int) Math.round(life * ELITE_LIFE);
        } else if (kind == AscentNode.Kind.BOSS) {
            life = (int) Math.round(life * BOSS_LIFE);
        }

        final String ai = AI_LADDER[Math.min(AI_LADDER.length - 1,
                (int) (effective * AI_LADDER.length))];

        final EnumSet<GameType> variants = EnumSet.of(
                run.getMode() == AscentRun.Mode.COMMANDER
                        ? GameType.Commander : GameType.Constructed);
        // ⚠️ EL JEFE YA NO ES ARCHIENEMIGO. Ver bossIsNotArchenemy().
        final CardPool schemes = null;

        int headStartCount = headStartAt(effective);
        if (kind == AscentNode.Kind.BOSS && run.getAscension() >= 5) {
            // Ascension 5: no se le puede dar un esquema DE MAS — el robo de
            // esquema del archienemigo esta escrito a fuego dentro del motor
            // (PhaseHandler: "if (playerTurn.isArchenemy()) setSchemeInMotion"),
            // sin gancho de carta ni habilidad — tocarlo seria tocar
            // forge-game, la regla que no se salta. Mismo espiritu (el jefe
            // pega mas fuerte nada mas empezar) por un camino que SI es
            // nuestro: entra con una carta MAS ya en mesa.
            headStartCount++;
        }

        final List<PaperCard> opponentRelics = relicsFor(kind, rnd);
        if (kind == AscentNode.Kind.BOSS && act == AscentRun.ACTS && run.getAscension() >= 10) {
            // Ascension 10: el jefe del acto 3 tiene una "segunda fase". No es
            // una regla de Magic — hay que inventar que significa — y se hace
            // con el MISMO mecanismo que las otras 35 reliquias: una carta de
            // verdad en su mando, con un disparo condicionado a su propia vida
            // (Count$YourLifeTotal). El umbral es literal y por MODO (18 en
            // Estandar, 36 en Commander: la vida de salida se dobla entre los
            // dos) y no "la mitad exacta de la suya" — el motor no publica la
            // vida INICIAL en ningun Count$, solo la actual, asi que una
            // fraccion exacta pediria registrar un script distinto por
            // partida. Es una aproximacion a proposito, documentada aqui para
            // que no parezca un descuido.
            final PaperCard segundoAliento = AscentRelics.bossPhaseCard(run.getMode());
            if (segundoAliento != null) {
                opponentRelics.add(segundoAliento);
            }
        }

        return new Plan(kind, act, deck, DuelFace.bestCardOf(deck),
                run.getLife(), life, ai,
                headStart(deck, headStartCount),
                opponentRelics, relicCards(run), schemes, variants);
    }

    // ------------------------------------------------------------------
    //  La curva, en un sitio y medible
    // ------------------------------------------------------------------

    /**
     * Cuanto has subido en la run entera: 0 en el primer nodo, 1 en el ultimo.
     *
     * <p>Es <b>la altura de la run</b>, no el acto. Esa es la diferencia entre
     * una curva y tres escalones: con la dificultad atada al acto, los doce
     * nodos de un acto son identicos entre si y el salto llega de golpe al
     * cambiar de mapa — cuando lo que ha cambiado durante esos doce nodos es
     * <b>tu mazo</b>, que ha ganado una carta por combate.
     */
    public static double progress(final int act, final int row) {
        final int total = AscentRun.ACTS * AscentMap.ROWS - 1;
        final int done = (clampAct(act) - 1) * AscentMap.ROWS
                + Math.max(0, Math.min(AscentMap.ROWS - 1, row));
        return Math.max(0.0, Math.min(1.0, (double) done / total));
    }

    /**
     * La vida de un rival normal a esa altura, contra la tuya.
     *
     * @param yourMaxLife tu vida maxima: 20 en Estandar, 40 en Commander
     * @param climb       la altura de la run, de 0 a 1
     * @param edge        lo que has crecido TU, de {@link #playerEdge}
     */
    public static int lifeAt(final int yourMaxLife, final double climb, final double edge) {
        final double ratio = LIFE_START + (LIFE_END - LIFE_START) * climb + edge;
        return Math.max(MIN_LIFE, (int) Math.round(yourMaxLife * ratio));
    }

    /** Techo de lo que puede sumar {@link #playerEdge}. */
    private static final double MAX_EDGE = 0.55;

    /** Lo que suma cada reliquia que llevas puesta. */
    private static final double RELIC_EDGE = 0.055;

    /** Y lo que suma tener el mazo lleno de cartas buenas. */
    private static final double DECK_EDGE = 0.90;

    /**
     * Cuanto has crecido TU, y por tanto cuanto se estira el rival.
     *
     * <h2>Por que existe</h2>
     *
     * <p>Decision del autor (02-09-2026): <b>que la curva escale con las pasivas y
     * las cartas buenas que vas consiguiendo</b>, no solo con el numero de nodo.
     * Sin esto, una run que engancha dos reliquias de jefe y llena el mazo de
     * raras se convierte en un paseo por el acto 3, porque la dificultad iba
     * contando pasos mientras el jugador multiplicaba su potencia. Y al reves:
     * una run que ha ido mal se estrella contra una curva calculada para la run
     * que si fue bien.
     *
     * <h2>Se mide con lo que ya sabemos medir</h2>
     *
     * <ul>
     *   <li><b>Las reliquias</b>, que son pasivas permanentes: cada una suma.
     *   <li><b>La calidad del mazo</b>, con la <b>misma</b> puntuacion con la que
     *       se ordenan los rivales ({@link #power}) y con la que
     *       {@link AscentSeedDeck} hace flojo el mazo de salida. El mazo semilla
     *       de Estandar no lleva <b>ni una rara</b>, asi que esto empieza en cero
     *       y sube solo segun coges premios de los actos altos.
     * </ul>
     *
     * <h2>Y suma MENOS de lo que te ha dado</h2>
     *
     * <p>A proposito, y es lo que separa esto de un ajuste que anula el progreso:
     * el rival recupera parte de tu ventaja, nunca toda. Una reliquia que le da
     * +2/+2 a todas tus criaturas vale muchisimo mas que las tres vidas de mas
     * que le pone al rival. Mejorar sigue mereciendo mucho la pena; lo que ya no
     * pasa es que mejorar te <b>regale</b> el resto de la run.
     */
    public static double playerEdge(final AscentRun run) {
        double edge = run.relics().size() * RELIC_EDGE;
        final Deck yours = AscentDecks.load(run);
        if (yours != null) {
            edge += power(yours) * DECK_EDGE;
        }
        return Math.min(MAX_EDGE, edge);
    }

    /** El escalon de mazo que toca a esa altura. */
    private static int tierFor(final double climb) {
        return climb < 0.34 ? 0 : climb < 0.70 ? 1 : 2;
    }

    /**
     * Cuantas tierras tiene ya puestas el rival a esa altura.
     *
     * <p>Cero durante todo el primer tramo, a proposito: la ventaja de salida
     * es la palanca que mas se nota de las cinco, y ponersela a un rival de los
     * primeros nodos seria justo lo contrario de lo que se busca.
     */
    private static int headStartAt(final double climb) {
        return climb < 0.40 ? 0 : climb < 0.75 ? 1 : 2;
    }

    /** Si en ese nodo se juega una partida. */
    public static boolean isBattle(final AscentNode.Kind kind) {
        return kind == AscentNode.Kind.COMBAT
                || kind == AscentNode.Kind.ELITE
                || kind == AscentNode.Kind.BOSS;
    }

    // ------------------------------------------------------------------
    //  Los asientos
    // ------------------------------------------------------------------

    /**
     * El asiento del jugador, con su vida arrastrada y sus reliquias puestas.
     *
     * <p>⚠️ <b>El orden importa y equivocarse no da ningun error.</b>
     * {@code forVariants} PISA la vida (pone 40 en Commander y otras 40 en el
     * archienemigo), asi que {@code setStartingLife} va <b>despues</b>. Es la
     * trampa que haria que la vida no se arrastrara nunca — el modo entero
     * colgando de una linea que no se queja. Lo mide la sonda 2 de
     * {@code ascentcheck}.
     */
    public static RegisteredPlayer humanSeat(final Plan plan, final Deck deck, final int seats) {
        final RegisteredPlayer seat = RegisteredPlayer.forVariants(
                seats, plan.variants, deck, null, false, null, null);
        seat.setStartingLife(plan.yourLife);
        if (!plan.yourRelics.isEmpty()) {
            seat.addExtraCardsInCommandZone(new ArrayList<>(plan.yourRelics));
        }
        return seat;
    }

    /** El asiento del rival: su mazo, su vida, sus esquemas y su ventaja. */
    public static RegisteredPlayer opponentSeat(final Plan plan, final int seats) {
        // Los esquemas SOLO entran por aqui: RegisteredPlayer no tiene setter
        // para ellos, asi que un jefe registrado sin esta llamada se queda en
        // un rival normal con 40 vidas, y no lo dice.
        final Deck deck = plan.schemes == null
                ? plan.opponentDeck : withSchemes(plan.opponentDeck, plan.schemes);
        final RegisteredPlayer seat = RegisteredPlayer.forVariants(
                seats, plan.variants, deck,
                plan.schemes == null ? null : plan.schemes.toFlatList(),
                plan.schemes != null, null, null);
        seat.setStartingLife(plan.opponentLife);
        if (!plan.opponentHeadStart.isEmpty()) {
            seat.addExtraCardsOnBattlefield(new ArrayList<>(plan.opponentHeadStart));
        }
        if (!plan.opponentRelics.isEmpty()) {
            // ⚠️ Sobre el asiento de la IA, no sobre el tuyo: puestas en el
            // humano por error, la elite te REGALA su reliquia y el nodo duro
            // pasa a ser el facil.
            seat.addExtraCardsInCommandZone(new ArrayList<>(plan.opponentRelics));
        }
        return seat;
    }

    // ------------------------------------------------------------------
    //  Jugar el nodo
    // ------------------------------------------------------------------

    /**
     * Juega el duelo de ese nodo y <b>apunta la vida con la que sales</b>.
     *
     * <p>La partida la lanza {@code NeoGame.play} — no un {@code HostedMatch}
     * propio — porque ahi viven las preferencias del motor, el blindaje de la
     * IA ({@code SafeAi}), la funda del mazo, la mesa y el menu de pausa. Un
     * cuarto camino que arranca partidas por su cuenta es justo el fallo del
     * principio 8 (las notas de diseño): el duelo de la aventura se jugaba con otros
     * ajustes y se clavaba al buscar en la biblioteca.
     *
     * <p>Lo unico que Ascenso pone de su parte son los <b>asientos</b>, que
     * entran por {@code NeoGame.Seating}: los esquemas del jefe no tienen
     * setter y la vida hay que ponerla justo despues de {@code forVariants}.
     *
     * @param binder la mesa, o {@code null} para jugar sin ventana
     */
    public static Outcome fight(final AscentRun run, final AscentNode node,
                                final NeoMatchUI.Mode mode, final int timeoutSecs,
                                final TableBinder binder) {
        final Plan plan = plan(run, node);
        final Deck yours = AscentDecks.load(run);
        if (yours == null) {
            throw new IllegalStateException("la run no tiene mazo guardado: " + run.getDeckName());
        }
        // El plan, al registro. Es lo unico que deja saber despues por que un
        // nodo salio como salio: la vida del rival, su caracter y su ventaja no
        // se pueden deducir mirando la mesa.
        System.out.println("[ascenso] " + plan);
        // Ending.ASCENT: la pantalla de fin de partida de un nodo ofrece UN
        // boton, "continuar Ascenso". Los dos de una partida suelta mentian los
        // dos — "otra partida" se leia como dejar el duelo a medias y borraba
        // la run, y "volver al menu" no volvia a ningun menu.
        final NeoGame.Result result = NeoGame.play(yours, 1, mode, timeoutSecs, false,
                binder, plan.aiProfile, true,
                run.getMode() == AscentRun.Mode.COMMANDER ? NeoFormat.COMMANDER : NeoFormat.ESTANDAR,
                List.of(plan.opponentDeck), 1, seating(plan), NeoMatchUI.Ending.ASCENT);

        // ⚠️ GANAR y SOBREVIVIR no son lo mismo, y confundirlos rompe el modo
        // en las dos direcciones. Se puede perder una partida de Magic con
        // vidas de sobra (te quedas sin biblioteca, o el rival te saca de otra
        // forma), y entonces mirar solo la vida daria la run por viva despues
        // de haberla perdido. Asi que manda quien gano; la vida solo decide
        // con cuanta llegas al nodo siguiente.
        final boolean won = won(result);
        // Si no se pudo leer la vida (partida colgada, o cerrada a lo bruto) se
        // deja la que habia: dar una run por perdida porque no se leyo un
        // numero seria el peor fallo posible en un modo donde perder es final.
        final boolean alive = run.recordLife(result.yourLife < 0 ? run.getLife() : result.yourLife);
        return new Outcome(won, won && alive, result.exit);
    }

    /** Como acabo el nodo. */
    public static final class Outcome {
        /** Si ganaste el duelo. */
        public final boolean won;
        /** Si la run sigue: hay que <b>ganar</b> Y salir con vidas. */
        public final boolean alive;
        /** Que pidio el jugador al salir (volver al menu, reiniciar...). */
        public final NeoMatchUI.Exit exit;

        Outcome(final boolean won, final boolean alive, final NeoMatchUI.Exit exit) {
            this.won = won;
            this.alive = alive;
            this.exit = exit;
        }
    }

    /**
     * Si el ganador de la partida eres tu.
     *
     * <p>Se compara por nombre porque es lo que publica el motor
     * ({@code GameView.getWinningPlayerName}), y el nombre del humano sale del
     * mismo sitio del que salio al sentarlo. Sin ganador —partida colgada o
     * cerrada— se da por <b>no ganada</b>: en un modo donde perder es final,
     * lo unico que no se puede hacer es regalar una victoria que no paso.
     */
    private static boolean won(final NeoGame.Result result) {
        if (result.winner == null || result.winner.isBlank()) {
            return false;
        }
        return result.winner.equals(forge.neo.look.NeoPlayers.human().getName());
    }

    /** Los asientos de ese duelo, para {@code NeoGame}. */
    public static NeoGame.Seating seating(final Plan plan) {
        return new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return plan.variants;
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                return humanSeat(plan, deck, seats);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                return opponentSeat(plan, seats);
            }
        };
    }

    // ------------------------------------------------------------------
    //  De donde salen los mazos de los rivales
    // ------------------------------------------------------------------

    /**
     * El pozo de rivales de un modo, ordenado de flojo a fuerte.
     *
     * <p>En Estandar son los preconstruidos de Forge; en Commander, los 173 de
     * {@code commanderprecons}. Se calcula una vez y se guarda: son cientos de
     * mazos, y puntuarlos en cada nodo seria recorrerlos todos para elegir uno.
     */
    public static synchronized List<Deck> pool(final AscentRun.Mode mode) {
        final List<Deck> cached = mode == AscentRun.Mode.COMMANDER ? commanderPool : standardPool;
        if (cached != null) {
            return cached;
        }
        final List<Deck> decks = new ArrayList<>();
        if (mode == AscentRun.Mode.COMMANDER) {
            FModel.getDecks().getCommanderPrecons().forEach(decks::add);
        } else {
            decks.addAll(NeoFormat.precons());
        }
        // Un "mazo" de veinte cartas en la carpeta de preconstruidos no es un
        // rival: es un mazo de muestra. Y con la vida del acto puesta seria un
        // nodo que se gana solo.
        decks.removeIf(d -> d == null || d.getMain().countAll() < 40);
        decks.sort(Comparator.comparingDouble(AscentBattle::power).thenComparing(Deck::getName));
        if (mode == AscentRun.Mode.COMMANDER) {
            commanderPool = decks;
        } else {
            standardPool = decks;
        }
        return decks;
    }

    private static volatile List<Deck> standardPool;
    private static volatile List<Deck> commanderPool;

    /**
     * Cuanto pega un mazo, en una escala que sirve <b>solo para ordenarlos</b>.
     *
     * <p>No es "el poder de un mazo" —eso no lo sabe nadie sin jugarlo— sino la
     * proporcion de cartas de rareza alta que lleva, que es exactamente el
     * criterio con el que {@link AscentSeedDeck} hace <i>flojo</i> el mazo de
     * salida. Usar el mismo en los dos sitios es lo que da sentido a la
     * escalera: el jugador empieza sin una sola rara y el acto 3 le pone
     * enfrente mazos llenos de ellas.
     *
     * <p>Las tierras no cuentan: casi todas son comunes, y contarlas solo
     * premiaria a los mazos que llevan pocas.
     */
    public static double power(final Deck deck) {
        int spells = 0;
        int score = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                continue;
            }
            spells += e.getValue();
            if (e.getKey().getRarity() == CardRarity.MythicRare) {
                score += 2 * e.getValue();
            } else if (e.getKey().getRarity() == CardRarity.Rare) {
                score += e.getValue();
            }
        }
        return spells == 0 ? 0 : (double) score / spells;
    }

    /**
     * Donde empieza y acaba cada escalon, en tanto por ciento del pozo.
     *
     * <p><b>No son tres tercios, y eso se midio.</b> Con el pozo partido en
     * tres partes iguales, la potencia media de los rivales de Estandar salia
     * <b>0,05 - 0,06 - 0,18</b>: entre el acto 1 y el 2 el mazo del rival no
     * cambiaba nada, porque dos tercios de los 440 preconstruidos de Forge son
     * mazos de iniciacion casi sin raras y el reparto esta muy cargado abajo.
     *
     * <p>Partiendo por 55% / 85% cada escalon coge un trozo de verdad distinto.
     * Lo comprueba {@code ascentcheck}, que ademas <b>exige un salto minimo</b>
     * entre escalones: sin eso, la comprobacion pasaba con una diferencia de
     * una centesima y el acto 2 se sentia igual que el 1 sin que nada fallara.
     */
    private static final double[] TIER_EDGES = {0.0, 0.55, 0.85, 1.0};

    /** Un mazo del escalon que toque: 0 el flojo, 1 el medio, 2 el fuerte. */
    private static Deck pickDeck(final AscentRun.Mode mode, final int tier, final Random rnd) {
        final List<Deck> all = pool(mode);
        if (all.isEmpty()) {
            return null;
        }
        final int[] range = tierRange(all.size(), tier);
        return all.get(range[0] + rnd.nextInt(range[1] - range[0]));
    }

    /** Que trozo del pozo es ese escalon: {@code [desde, hasta)}. */
    public static int[] tierRange(final int size, final int tier) {
        final int from = (int) (size * TIER_EDGES[tier]);
        final int to = Math.min(size, Math.max(from + 1, (int) (size * TIER_EDGES[tier + 1])));
        return new int[]{from, to};
    }

    /** El nombre de un mazo, aguantando el {@code null}. */
    public static String name(final Deck deck) {
        return deck == null ? "(sin mazo)" : deck.getName();
    }

    // ------------------------------------------------------------------
    //  Ventajas y reliquias
    // ------------------------------------------------------------------

    /**
     * Las tierras con las que el rival empieza ya puestas.
     *
     * <p>Salen <b>de su propio mazo</b>, no de una lista nuestra: darle un
     * Bosque a un mazo azul-negro seria darle una tierra que no sabe usar, o
     * sea ninguna ventaja. Y salen por copia, asi que su mazo se queda como
     * estaba: {@code addExtraCardsOnBattlefield} anyade cartas, no mueve las
     * suyas.
     */
    private static List<PaperCard> headStart(final Deck deck, final int howMany) {
        final List<PaperCard> out = new ArrayList<>();
        if (deck == null || howMany <= 0) {
            return out;
        }
        PaperCard land = null;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final PaperCard c = e.getKey();
            if (!c.getRules().getType().isLand()) {
                continue;
            }
            // Una basica antes que una tierra rara con condiciones: lo que se
            // le esta dando es mana, no una carta buena.
            if (land == null || (!land.getRules().getType().isBasicLand()
                    && c.getRules().getType().isBasicLand())) {
                land = c;
            }
        }
        for (int i = 0; land != null && i < howMany; i++) {
            out.add(land);
        }
        return out;
    }

    /** Las reliquias que le tocan al rival de ese nodo. */
    private static List<PaperCard> relicsFor(final AscentNode.Kind kind, final Random rnd) {
        final int howMany = kind == AscentNode.Kind.ELITE ? 1
                : kind == AscentNode.Kind.BOSS ? 2 : 0;
        final List<PaperCard> out = new ArrayList<>();
        if (howMany == 0) {
            return out;
        }
        final List<AscentRelic> pool = new ArrayList<>(AscentRelics.all());
        Collections.shuffle(pool, rnd);
        for (final AscentRelic relic : pool) {
            if (out.size() >= howMany) {
                break;
            }
            final PaperCard card = AscentRelics.cardOf(relic);
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    /** Las reliquias que lleva el jugador, ya como cartas. */
    private static List<PaperCard> relicCards(final AscentRun run) {
        final List<PaperCard> out = new ArrayList<>();
        for (final AscentRelic relic : run.relics()) {
            final PaperCard card = AscentRelics.cardOf(relic);
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    /** El mismo mazo, mas su seccion de esquemas. */
    private static Deck withSchemes(final Deck base, final CardPool schemes) {
        final Deck copy = new Deck(base, base.getName());
        copy.putSection(DeckSection.Schemes, schemes);
        return copy;
    }

    // ------------------------------------------------------------------

    private static int clampAct(final int act) {
        return Math.max(1, Math.min(AscentRun.ACTS, act));
    }

    /**
     * El sorteo de un nodo: la semilla de la run mezclada con la clave del nodo.
     *
     * <p>Sembrado a proposito. Con un {@code Random} suelto, salir del juego
     * antes de entrar en el nodo y volver daria <b>otro</b> rival — o sea,
     * tirar los dados hasta que salga uno comodo, que es justo lo que un
     * roguelike no puede permitir.
     */
    private static Random rng(final AscentRun run, final AscentNode node) {
        long h = run.getSeed() * 31L + run.getAct();
        for (final char c : node.key().toCharArray()) {
            h = h * 31L + c;
        }
        return new Random(h);
    }

    /**
     * Por que el jefe NO lleva esquemas de archienemigo.
     *
     * <h2>Los llevo tres dias y se quitaron el 03-09-2026</h2>
     *
     * <p>Reportado jugando, y sin medias tintas: <i>"el boss me ha reventado
     * en turno 3, 0 chances... es imposible ganar a un boss"</i>. Y la razon
     * de fondo la dio el propio jugador, que es la que hace que esto no sea
     * una cuestion de afinar un numero: <b>Archienemigo esta disenyado para
     * 3 contra 1</b>. Sus esquemas estan calibrados para partirse entre tres
     * rivales que ademas tienen tres turnos por cada uno del archienemigo. En
     * un 1c1 esa misma carta cae entera sobre una sola persona, y encima cada
     * turno. No es un jefe dificil: es una partida que no se juega.
     *
     * <p>Bajarles la potencia no era una opcion — los esquemas son cartas del
     * motor, no un parametro nuestro — y elegir "los suaves" habria sido
     * inventar una lista a mano y mantenerla.
     *
     * <h2>Que hace jefe a un jefe ahora</h2>
     *
     * <p>Lo que ya tenia y no dependia de Archienemigo: el <b>doble de vida</b>
     * ({@link #BOSS_LIFE}), <b>dos reliquias</b> en vez de una o ninguna
     * ({@code relicsFor}), la <b>ventaja de salida</b> y —a partir de la
     * Ascension 10— el <b>segundo aliento</b>. Y a eso se le anyade lo que
     * pidio el jugador: un mazo <b>un escalon por encima</b> del que juegan los
     * combates normales de su mismo piso ({@link #BOSS_DECK_EDGE}).
     *
     * <h2>Y de regalo, se fue un cuelgue</h2>
     *
     * <p>Sin esquemas no hay {@code SetInMotionEffect}, y por tanto no hay el
     * fallo del motor que estaba tirando la partida a mitad del combate contra
     * el jefe: {@code Player.setSchemeInMotion} pide el primer esquema del mazo
     * <b>sin comprobar que quede alguno</b> y {@code GameAction.moveTo} revienta
     * con el {@code null}. Se veia como <i>"en medio de la batalla con el boss
     * me saca de la partida y me deja en el mapa"</i>. La red por si algo
     * parecido vuelve por otro lado sigue puesta, y es {@code SafeAi}.
     *
     * <p>Se deja escrito y no se borra sin mas para que no se le ocurra a nadie
     * volver a ponerlos: <b>el problema no era el ajuste, era el formato</b>.
     */
    private static void bossIsNotArchenemy() {
        // Solo documentacion. Ver el javadoc.
    }
}
