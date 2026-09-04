package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Un evento: un texto, dos o tres opciones y lo que pasa con cada una.
 *
 * <h2>Por que hacia falta</h2>
 *
 * <p>Un nodo de evento cobraba su premio —que era ninguno— y devolvia al mapa.
 * Con la tienda hecha, era <b>lo ultimo que quedaba en tramite</b>: 115 nodos
 * de 720 en las 20 runs que recorre {@link AscentCheck}, o sea el 16% del mapa.
 * Y son justo los nodos que en un roguelike dan la variedad: el combate y el
 * descanso siempre hacen lo mismo, el evento no.
 *
 * <h2>La regla que separa esto de una tragaperras</h2>
 *
 * <p><b>Una opcion dice lo que hace, o dice que es una apuesta.</b> Nunca las
 * dos cosas a medias. En <i>Slay the Spire</i> parte de la gracia es no saber
 * que va a pasar, pero aqui eso choca de frente con el principio 1 de
 * las notas de diseño —un control que no hace lo que parece es peor que no tenerlo— y con
 * que nada de esto se deshace. Asi que:
 *
 * <ul>
 *   <li>las opciones normales llevan su efecto escrito, con el numero;</li>
 *   <li>las de azar dicen <b>que son de azar</b> y con que probabilidad.</li>
 * </ul>
 *
 * <h2>Y tres reglas mas, todas por lo mismo</h2>
 *
 * <ol>
 *   <li><b>Un evento nunca mata.</b> El dano pasa por {@link AscentRun#hurt},
 *       que deja en 1 como minimo: acabar una run de cuarenta minutos en un
 *       menu, sin jugar una carta, es el peor final posible.</li>
 *   <li><b>Siempre se puede uno marchar.</b> Todo evento tiene una salida sin
 *       coste (principio 7). Si todas las opciones costaran algo, el nodo seria
 *       una encerrona.</li>
 *   <li><b>Lo que no se puede pagar no se puede elegir</b>, y se ve apagado en
 *       vez de dejarte clicarlo y contestarte que no.</li>
 * </ol>
 *
 * <p>Cual sale en cada nodo va <b>sembrado con la clave del nodo</b>, igual que
 * el rival, los premios y la tienda: si no, salir del juego antes de elegir y
 * volver daria otro evento.
 *
 * <h2>Cuantos, y por que veinte</h2>
 *
 * <p>En una run salen unos <b>cinco</b> nodos de evento. Con siete, a la
 * segunda run ya se repetian casi todos — y un evento repetido deja de ser un
 * encuentro para ser un menu que ya sabes contestar. Con veinte hacen falta
 * cuatro runs para verlos todos, que es justo lo que un roguelike necesita:
 * que la variedad dure mas que la curiosidad.
 *
 * <p>Y desde los <b>hitos</b> (el plan de Ascenso) hay cuatro mas que no
 * salen hasta que te los ganas: el faro, el buhonero, la cripta y la fragua.
 * <b>Se anyaden, no sustituyen</b>: un jugador nuevo ve los mismos veinte de
 * siempre, y lo que cambia es que ahora hay algo detras de ganar. Cada uno
 * hace algo que ninguno de los veinte hace — cambiar techo de vida por vida de
 * ahora, dar carta y reliquia a la vez, dar una reliquia de jefe sin jefe, y
 * mejorar una reliquia que ya llevas.
 *
 * <p>Cero JavaFX: {@link AscentCheck} los recorre todos sin ventana.
 */
public final class AscentEvent {

    /** Que hay que ensenyar DESPUES de elegir, si es que hay algo. */
    public enum Extra {
        /** Nada: se aplica y se vuelve al mapa. */
        NONE,
        /** Elegir 1 de 3 cartas (las monta la pantalla con el pozo del acto). */
        PICK_CARD,
        /**
         * Elegir que carta del mazo se va, y <b>ya no hay vuelta atras</b>:
         * se usa cuando el pago ya se ha hecho (el coleccionista ya te dio los
         * creditos). Dejar salir ahi seria dinero gratis.
         */
        REMOVE_CARD,
        /**
         * Igual, pero <b>se puede volver</b>. Es el caso de la hoguera: elegir
         * "quemar una carta" todavia no ha hecho nada, asi que atrapar ahi al
         * jugador —sin poder volver a la otra opcion— seria una encerrona por
         * haber pulsado un boton para mirar (principio 7).
         */
        REMOVE_CARD_FREE
    }

    /** Lo que hace una opcion. */
    public interface Effect {
        /**
         * Aplicalo.
         *
         * @param rnd el azar del nodo, ya sembrado
         * @return el texto de que ha pasado, ya resuelto
         */
        Outcome apply(AscentRun run, Random rnd);
    }

    /** Si una opcion se puede elegir ahora mismo. */
    public interface Guard {
        boolean ok(AscentRun run);
    }

    /** Lo que ha pasado al elegir. */
    public static final class Outcome {
        private final String messageKey;
        private final Object[] args;
        private final Extra extra;

        public Outcome(final String messageKey, final Extra extra, final Object... args) {
            this.messageKey = messageKey;
            this.extra = extra;
            this.args = args;
        }

        /** La clave de texto de "que ha pasado". */
        public String getMessageKey() {
            return messageKey;
        }

        public Object[] getArgs() {
            return args;
        }

        /** Que pantalla hace falta despues, si hace falta alguna. */
        public Extra getExtra() {
            return extra;
        }

        @Override
        public String toString() {
            return messageKey + Arrays.toString(args)
                    + (extra == Extra.NONE ? "" : " +" + extra);
        }
    }

    /** Una opcion del evento. */
    public static final class Choice {
        private final String labelKey;
        private final Object[] labelArgs;
        private final Guard guard;
        private final Effect effect;
        private final String blockedKey;

        Choice(final String labelKey, final Object[] labelArgs, final Guard guard,
               final String blockedKey, final Effect effect) {
            this.labelKey = labelKey;
            this.labelArgs = labelArgs;
            this.guard = guard;
            this.blockedKey = blockedKey;
            this.effect = effect;
        }

        public String getLabelKey() {
            return labelKey;
        }

        public Object[] getLabelArgs() {
            return labelArgs;
        }

        /** Si se puede elegir con la run tal y como esta. */
        public boolean isAvailable(final AscentRun run) {
            return guard == null || guard.ok(run);
        }

        /**
         * Por que no se puede, para decirlo en vez de dejar un boton muerto.
         *
         * @return la clave de texto, o {@code null} si no aplica
         */
        public String getBlockedKey() {
            return blockedKey;
        }

        public Outcome apply(final AscentRun run, final Random rnd) {
            return effect.apply(run, rnd);
        }
    }

    private final String id;
    private final List<Choice> choices;

    /**
     * El hito que hay que tener para que este evento salga, o {@code null} si
     * sale desde la primera run.
     *
     * <p>Los veinte de siempre no llevan ninguno <b>a proposito</b>: los
     * desbloqueos tienen que <i>anyadir</i>, nunca quitarle contenido a quien
     * empieza. Un jugador nuevo ve hoy exactamente lo mismo que veia antes de
     * que este sistema existiera; lo que cambia es que ahora hay mas cosas
     * detras.
     */
    private final AscentFeat gate;

    private AscentEvent(final String id, final List<Choice> choices) {
        this(id, null, choices);
    }

    private AscentEvent(final String id, final AscentFeat gate, final List<Choice> choices) {
        this.id = id;
        this.gate = gate;
        this.choices = choices;
    }

    public String getId() {
        return id;
    }

    /** El hito que lo abre, o {@code null} si sale desde el principio. */
    public AscentFeat getGate() {
        return gate;
    }

    /** La clave del titulo. Los textos viven en {@code neo-<idioma>.properties}. */
    public String getTitleKey() {
        return "ascent.event." + id + ".title";
    }

    /** La clave del texto que cuenta que te encuentras. */
    public String getTextKey() {
        return "ascent.event." + id + ".text";
    }

    public List<Choice> getChoices() {
        return choices;
    }

    @Override
    public String toString() {
        return id + " (" + choices.size() + " opciones)";
    }

    // ------------------------------------------------------------------
    //  El catalogo
    // ------------------------------------------------------------------

    /** Cuanta vida cuesta el altar. */
    private static final int SHRINE_LIFE = 6;

    /** La apuesta del tahur, y lo que paga. */
    private static final int GAMBLE_BET = 50;
    private static final int GAMBLE_WIN = 150;

    /** El cofre: lo que da y lo que cuesta. */
    private static final int CHEST_CREDITS = 120;
    private static final int CHEST_LIFE = 5;

    /** Lo que paga el coleccionista por una carta. */
    private static final int COLLECTOR_PAYS = 90;

    /** Lo que cura la hoguera. */
    private static final int CAMPFIRE_HEAL = 8;

    /** Lo que cuesta el eco, en vida. */
    private static final int ECHO_LIFE = 4;

    /** Cuanto sube la fuente el techo de vida. */
    private static final int FOUNTAIN_MAX = 3;

    /** Lo que cuesta beber en la fuente. */
    private static final int FOUNTAIN_PRICE = 70;

    /** El peaje del puente, y lo que cuesta forzarlo. */
    private static final int TOLL_PRICE = 60;
    private static final int TOLL_LIFE = 5;

    /** Lo que cobra el herrero por una reliquia rara. */
    private static final int SMITH_PRICE = 80;

    /** El nido: mucho dinero y mucha sangre. */
    private static final int NEST_CREDITS = 150;
    private static final int NEST_LIFE = 8;

    /** La encrucijada: descansar o rebuscar. */
    private static final int CROSS_HEAL = 10;
    private static final int CROSS_CREDITS = 70;

    /** El espejismo: la apuesta que se paga en vida. */
    private static final int MIRAGE_CREDITS = 120;
    private static final int MIRAGE_LIFE = 7;

    /** El pacto: una reliquia rara por TECHO de vida. */
    private static final int PACT_MAX = 4;

    /** El prestamista: dinero ahora, sangre ahora tambien. */
    private static final int LOAN_CREDITS = 200;
    private static final int LOAN_LIFE = 10;

    /** La estatua: profanarla. */
    private static final int STATUE_CREDITS = 180;
    private static final int STATUE_LIFE = 6;

    /** Lo que cuesta compartir la comida con el vagabundo. */
    private static final int WANDERER_PRICE = 40;

    /** El pozo de los deseos. */
    private static final int WELL_BET = 100;
    private static final int WELL_WIN = 260;

    /** El ermitanyo: quemar carta Y curarte. */
    private static final int HERMIT_HEAL = 5;

    /** El maestro: vida ahora por techo para siempre. */
    private static final int TEACHER_LIFE = 5;
    private static final int TEACHER_MAX = 3;

    /** El faro: techo de vida que se quema, y vida de ahora que da a cambio. */
    private static final int BEACON_MAX = 3;
    private static final int BEACON_HEAL = 12;

    /** El buhonero: el fardo entero, carta y reliquia. */
    private static final int PEDDLER_PRICE = 140;

    /** La cripta: el ajuar, y lo que cuesta la corona. */
    private static final int CRYPT_CREDITS = 200;
    private static final int CRYPT_LIFE = 12;

    /** La fragua: lo que cuesta refundir una comun en una rara. */
    private static final int FORGE_PRICE = 90;

    private static final List<AscentEvent> ALL = build();

    /**
     * Todos los eventos que existen, desbloqueados o no.
     *
     * <p>Es la lista para <b>mirar</b> (comprobadores, textos, catalogo). Lo
     * que sale en una run es {@link #pool()}: mezclar las dos es exactamente el
     * fallo del que avisa {@code AscentUnlocks}.
     */
    public static List<AscentEvent> all() {
        return ALL;
    }

    /**
     * Los que pueden salir hoy: los de siempre mas los que se hayan
     * desbloqueado.
     *
     * <p><b>El unico sitio donde se filtra.</b> {@link #of} sortea de aqui y de
     * ningun otro lado, que es lo que impide que un evento bloqueado aparezca
     * por un camino y no por otro — un fallo que no da error y que el jugador
     * no puede notar, porque no sabe que no deberia estar viendolo.
     */
    public static List<AscentEvent> pool() {
        final java.util.Set<AscentFeat> mine = AscentUnlocks.feats();
        final List<AscentEvent> out = new ArrayList<>(ALL.size());
        for (final AscentEvent e : ALL) {
            if (e.gate == null || mine.contains(e.gate)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Uno por su id, o {@code null}. */
    public static AscentEvent byId(final String id) {
        for (final AscentEvent e : ALL) {
            if (e.id.equals(id)) {
                return e;
            }
        }
        return null;
    }

    /**
     * Que evento sale en ese nodo.
     *
     * <p>Sembrado con la clave del nodo, y por lo mismo que todo lo demas del
     * modo: si se sorteara suelto, salir del juego antes de elegir y volver
     * daria otro evento.
     */
    public static AscentEvent of(final AscentRun run, final AscentNode node) {
        final List<AscentEvent> pool = pool();
        return pool.get(rng(run, node).nextInt(pool.size()));
    }

    /**
     * El azar de ese nodo, que es tambien el de las apuestas de dentro.
     *
     * <p>La semilla se <b>mezcla</b> (splitmix64) en vez de irse tal cual a
     * {@code new Random}. Las semillas de este modo salen de sumas y
     * multiplicaciones de la semilla de la run con la clave del nodo, o sea que
     * dos nodos vecinos dan semillas casi iguales — y {@code java.util.Random}
     * arranca con los bits altos muy parecidos cuando las semillas lo son. Eso
     * no se nota al elegir de una lista, pero en una apuesta a cara o cruz si:
     * ver el comentario del tahur.
     */
    public static Random rng(final AscentRun run, final AscentNode node) {
        long h = run.getSeed() * 131L + run.getAct() * 17L + 104729L;
        for (final char c : node.key().toCharArray()) {
            h = h * 31L + c;
        }
        return new Random(mix(h));
    }

    /** Mezcla de bits (splitmix64), para que semillas parecidas no lo sean. */
    private static long mix(final long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static List<AscentEvent> build() {
        final List<AscentEvent> out = new ArrayList<>();

        // 1. Vida por una reliquia. El intercambio mas puro del modo: lo que
        //    te sobra ahora por lo que te hara falta luego.
        out.add(new AscentEvent("shrine", List.of(
                new Choice("ascent.event.opt.payLife", new Object[]{SHRINE_LIFE},
                        run -> run.getLife() > SHRINE_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            final int lost = run.hurt(SHRINE_LIFE);
                            final AscentRelic relic = AscentRewards.relic(run,
                                    AscentRelic.Rarity.COMMON, rnd);
                            if (relic == null || !run.addRelic(relic)) {
                                // Sin reliquia que dar, se devuelve la vida: un
                                // pago sin nada a cambio seria un fallo, no un
                                // riesgo.
                                run.heal(lost);
                                return new Outcome("ascent.event.res.nothing", Extra.NONE);
                            }
                            return new Outcome("ascent.event.res.gotRelic", Extra.NONE,
                                    relic.getCardName(), lost);
                        }),
                leave())));

        // 2. La apuesta, que dice QUE es una apuesta y con que probabilidad.
        out.add(new AscentEvent("gambler", List.of(
                new Choice("ascent.event.opt.gamble", new Object[]{GAMBLE_BET, GAMBLE_WIN},
                        run -> run.getCredits() >= GAMBLE_BET, "ascent.event.needCredits",
                        (run, rnd) -> {
                            run.spend(GAMBLE_BET);
                            // ⚠️ nextInt(100) y NO nextBoolean(): nextBoolean
                            // devuelve el BIT ALTO de la primera tirada, y con
                            // semillas derivadas de forma lineal (que es lo que
                            // sale de mezclar la semilla de la run con la clave
                            // del nodo) ese bit esta sesgadisimo. Medido por
                            // ascentcheck: salia cara 60 de 60. Una apuesta que
                            // gana siempre no es una apuesta, y el jugador no
                            // tiene forma de notarlo.
                            if (rnd.nextInt(100) < 50) {
                                run.addCredits(GAMBLE_WIN);
                                return new Outcome("ascent.event.res.wonBet", Extra.NONE,
                                        GAMBLE_WIN);
                            }
                            return new Outcome("ascent.event.res.lostBet", Extra.NONE,
                                    GAMBLE_BET);
                        }),
                leave())));

        // 3. El cofre: los dos efectos escritos, uno bueno y otro malo.
        out.add(new AscentEvent("chest", List.of(
                new Choice("ascent.event.opt.forceChest",
                        new Object[]{CHEST_CREDITS, CHEST_LIFE}, null, null,
                        (run, rnd) -> {
                            run.addCredits(CHEST_CREDITS);
                            final int lost = run.hurt(CHEST_LIFE);
                            return new Outcome("ascent.event.res.chest", Extra.NONE,
                                    CHEST_CREDITS, lost);
                        }),
                leave())));

        // 4. Vender una carta: quitar del mazo Y cobrar. Es el evento que mas
        //    se parece a una decision de construccion.
        out.add(new AscentEvent("collector", List.of(
                new Choice("ascent.event.opt.sellCard", new Object[]{COLLECTOR_PAYS},
                        AscentShop::canRemove, "ascent.event.needDeck",
                        (run, rnd) -> {
                            run.addCredits(COLLECTOR_PAYS);
                            return new Outcome("ascent.event.res.sold", Extra.REMOVE_CARD,
                                    COLLECTOR_PAYS);
                        }),
                leave())));

        // 5. Una hoguera pequenya: curarte o quemar una carta. Es el descanso
        //    en barato, y por eso cura un numero fijo y no un porcentaje.
        out.add(new AscentEvent("campfire", List.of(
                new Choice("ascent.event.opt.heal", new Object[]{CAMPFIRE_HEAL},
                        run -> run.getLife() < run.getMaxLife(), "ascent.event.fullLife",
                        (run, rnd) -> {
                            final int before = run.getLife();
                            run.heal(CAMPFIRE_HEAL);
                            return new Outcome("ascent.event.res.healed", Extra.NONE,
                                    run.getLife() - before);
                        }),
                new Choice("ascent.event.opt.burnCard", new Object[0],
                        AscentShop::canRemove, "ascent.event.needDeck",
                        (run, rnd) -> new Outcome("ascent.event.res.burn",
                                Extra.REMOVE_CARD_FREE)),
                leave())));

        // 6. Una carta a cambio de vida. El unico sitio del modo donde se puede
        //    comprar carta sin dinero.
        out.add(new AscentEvent("echo", List.of(
                new Choice("ascent.event.opt.cardForLife", new Object[]{ECHO_LIFE},
                        run -> run.getLife() > ECHO_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            final int lost = run.hurt(ECHO_LIFE);
                            return new Outcome("ascent.event.res.cardForLife",
                                    Extra.PICK_CARD, lost);
                        }),
                leave())));

        // 7. Subir el TECHO de vida. Es el unico premio de la run que no se
        //    gasta: todo lo demas (curarse, creditos) se consume.
        out.add(new AscentEvent("fountain", List.of(
                new Choice("ascent.event.opt.drink",
                        new Object[]{FOUNTAIN_PRICE, FOUNTAIN_MAX},
                        run -> run.getCredits() >= FOUNTAIN_PRICE, "ascent.event.needCredits",
                        (run, rnd) -> {
                            run.spend(FOUNTAIN_PRICE);
                            run.raiseMaxLife(FOUNTAIN_MAX);
                            return new Outcome("ascent.event.res.maxLife", Extra.NONE,
                                    FOUNTAIN_MAX, run.getMaxLife());
                        }),
                leave())));

        // 8. Un peaje con dos formas de pasar: con dinero o con sangre. Es el
        //    intercambio del modo puesto en una sola pantalla.
        out.add(new AscentEvent("toll", List.of(
                new Choice("ascent.event.opt.payToll", new Object[]{TOLL_PRICE},
                        run -> run.getCredits() >= TOLL_PRICE, "ascent.event.needCredits",
                        (run, rnd) -> {
                            run.spend(TOLL_PRICE);
                            return new Outcome("ascent.event.res.passed", Extra.PICK_CARD);
                        }),
                new Choice("ascent.event.opt.forcePass", new Object[]{TOLL_LIFE},
                        run -> run.getLife() > TOLL_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            run.hurt(TOLL_LIFE);
                            return new Outcome("ascent.event.res.passed", Extra.PICK_CARD);
                        }),
                leave())));

        // 9. Uno que solo da. Hacen falta: si todos los eventos cobran algo,
        //    entrar en uno deja de ser una alegria y pasa a ser un peaje.
        out.add(new AscentEvent("armory", List.of(
                new Choice("ascent.event.opt.takeCard", new Object[0], null, null,
                        (run, rnd) -> new Outcome("ascent.event.res.freeCard", Extra.PICK_CARD)),
                leave())));

        // 10. La unica reliquia RARA que se puede comprar con dinero.
        out.add(new AscentEvent("smith", List.of(
                new Choice("ascent.event.opt.buyRelic", new Object[]{SMITH_PRICE},
                        run -> run.getCredits() >= SMITH_PRICE, "ascent.event.needCredits",
                        (run, rnd) -> {
                            run.spend(SMITH_PRICE);
                            final AscentRelic relic = AscentRewards.relic(run,
                                    AscentRelic.Rarity.RARE, rnd);
                            if (relic == null || !run.addRelic(relic)) {
                                run.addCredits(SMITH_PRICE);
                                return new Outcome("ascent.event.res.nothing", Extra.NONE);
                            }
                            return new Outcome("ascent.event.res.boughtRelic", Extra.NONE,
                                    relic.getCardName());
                        }),
                leave())));

        // 11. Mucho dinero y mucha sangre. Es el cofre subido de tono, y a
        //     proposito: en el acto 3, cinco vidas ya no asustan.
        out.add(new AscentEvent("nest", List.of(
                new Choice("ascent.event.opt.robNest", new Object[]{NEST_CREDITS, NEST_LIFE},
                        run -> run.getLife() > NEST_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            run.addCredits(NEST_CREDITS);
                            final int lost = run.hurt(NEST_LIFE);
                            return new Outcome("ascent.event.res.creditsForLife", Extra.NONE,
                                    NEST_CREDITS, lost);
                        }),
                leave())));

        // 12. Dos cosas buenas y solo una. La decision no es "si o no", que es
        //     la mas facil: es "cual", que es la que se piensa.
        out.add(new AscentEvent("crossroads", List.of(
                new Choice("ascent.event.opt.heal", new Object[]{CROSS_HEAL},
                        run -> run.getLife() < run.getMaxLife(), "ascent.event.fullLife",
                        (run, rnd) -> {
                            final int before = run.getLife();
                            run.heal(CROSS_HEAL);
                            return new Outcome("ascent.event.res.healed", Extra.NONE,
                                    run.getLife() - before);
                        }),
                new Choice("ascent.event.opt.searchCredits", new Object[]{CROSS_CREDITS},
                        null, null,
                        (run, rnd) -> {
                            run.addCredits(CROSS_CREDITS);
                            return new Outcome("ascent.event.res.creditsFound", Extra.NONE,
                                    CROSS_CREDITS);
                        }),
                leave())));

        // 13. La apuesta que se paga en VIDA, no en dinero. Distinta de la del
        //     tahur en lo unico que importa: lo que arriesgas.
        out.add(new AscentEvent("mirage", List.of(
                new Choice("ascent.event.opt.crossMirage",
                        new Object[]{MIRAGE_CREDITS, MIRAGE_LIFE},
                        run -> run.getLife() > MIRAGE_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            if (rnd.nextInt(100) < 50) {
                                run.addCredits(MIRAGE_CREDITS);
                                return new Outcome("ascent.event.res.creditsFound", Extra.NONE,
                                        MIRAGE_CREDITS);
                            }
                            return new Outcome("ascent.event.res.hurt", Extra.NONE,
                                    run.hurt(MIRAGE_LIFE));
                        }),
                leave())));

        // 14. El unico coste del modo que NO se recupera descansando: el techo.
        out.add(new AscentEvent("pact", List.of(
                new Choice("ascent.event.opt.signPact", new Object[]{PACT_MAX},
                        run -> run.getMaxLife() - PACT_MAX >= AscentRun.MIN_MAX_LIFE,
                        "ascent.event.needMaxLife",
                        (run, rnd) -> {
                            final AscentRelic relic = AscentRewards.relic(run,
                                    AscentRelic.Rarity.RARE, rnd);
                            if (relic == null || !run.addRelic(relic)) {
                                return new Outcome("ascent.event.res.nothing", Extra.NONE);
                            }
                            run.lowerMaxLife(PACT_MAX);
                            return new Outcome("ascent.event.res.pact", Extra.NONE,
                                    relic.getCardName(), run.getMaxLife());
                        }),
                leave())));

        // 15. Dinero a espuertas por sangre a espuertas.
        out.add(new AscentEvent("lender", List.of(
                new Choice("ascent.event.opt.borrow", new Object[]{LOAN_CREDITS, LOAN_LIFE},
                        run -> run.getLife() > LOAN_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            run.addCredits(LOAN_CREDITS);
                            final int lost = run.hurt(LOAN_LIFE);
                            return new Outcome("ascent.event.res.creditsForLife", Extra.NONE,
                                    LOAN_CREDITS, lost);
                        }),
                leave())));

        // 16. Curarte del todo, o profanarla por dinero. La primera vez que
        //     curarse entero es una opcion, y por eso vale la pena que sea una
        //     decision y no un regalo.
        out.add(new AscentEvent("statue", List.of(
                new Choice("ascent.event.opt.pray", new Object[0],
                        run -> run.getLife() < run.getMaxLife(), "ascent.event.fullLife",
                        (run, rnd) -> {
                            final int before = run.getLife();
                            run.heal(run.getMaxLife());
                            return new Outcome("ascent.event.res.healed", Extra.NONE,
                                    run.getLife() - before);
                        }),
                new Choice("ascent.event.opt.desecrate",
                        new Object[]{STATUE_CREDITS, STATUE_LIFE},
                        run -> run.getLife() > STATUE_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            run.addCredits(STATUE_CREDITS);
                            final int lost = run.hurt(STATUE_LIFE);
                            return new Outcome("ascent.event.res.creditsForLife", Extra.NONE,
                                    STATUE_CREDITS, lost);
                        }),
                leave())));

        // 17. Una reliquia comun por poco dinero: el evento "barato" del lote.
        out.add(new AscentEvent("wanderer", List.of(
                new Choice("ascent.event.opt.shareFood", new Object[]{WANDERER_PRICE},
                        run -> run.getCredits() >= WANDERER_PRICE, "ascent.event.needCredits",
                        (run, rnd) -> {
                            run.spend(WANDERER_PRICE);
                            final AscentRelic relic = AscentRewards.relic(run,
                                    AscentRelic.Rarity.COMMON, rnd);
                            if (relic == null || !run.addRelic(relic)) {
                                run.addCredits(WANDERER_PRICE);
                                return new Outcome("ascent.event.res.nothing", Extra.NONE);
                            }
                            return new Outcome("ascent.event.res.boughtRelic", Extra.NONE,
                                    relic.getCardName());
                        }),
                leave())));

        // 18. La apuesta gorda. Paga menos del doble a proposito: una apuesta
        //     que sale a cuenta no es una apuesta, es un boton de "ganar".
        out.add(new AscentEvent("well", List.of(
                new Choice("ascent.event.opt.wish", new Object[]{WELL_BET, WELL_WIN},
                        run -> run.getCredits() >= WELL_BET, "ascent.event.needCredits",
                        (run, rnd) -> {
                            run.spend(WELL_BET);
                            if (rnd.nextInt(100) < 50) {
                                run.addCredits(WELL_WIN);
                                return new Outcome("ascent.event.res.wonBet", Extra.NONE,
                                        WELL_WIN);
                            }
                            return new Outcome("ascent.event.res.lostBet", Extra.NONE, WELL_BET);
                        }),
                leave())));

        // 19. Quitar carta Y curarte, las dos. Es el unico sitio del modo donde
        //     eso pasa, y por eso el evento sale poco: el descanso vive de que
        //     haya que elegir entre las dos.
        out.add(new AscentEvent("hermit", List.of(
                new Choice("ascent.event.opt.hermitBurn", new Object[]{HERMIT_HEAL},
                        AscentShop::canRemove, "ascent.event.needDeck",
                        (run, rnd) -> {
                            run.heal(HERMIT_HEAL);
                            return new Outcome("ascent.event.res.burn", Extra.REMOVE_CARD_FREE);
                        }),
                leave())));

        // 20. Vida de ahora por techo para siempre. Al reves que la fuente, que
        //     lo compra con dinero.
        out.add(new AscentEvent("teacher", List.of(
                new Choice("ascent.event.opt.train", new Object[]{TEACHER_LIFE, TEACHER_MAX},
                        run -> run.getLife() > TEACHER_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            run.hurt(TEACHER_LIFE);
                            run.raiseMaxLife(TEACHER_MAX);
                            return new Outcome("ascent.event.res.maxLife", Extra.NONE,
                                    TEACHER_MAX, run.getMaxLife());
                        }),
                leave())));

        // ------------------------------------------------------------------
        //  Los que hay que desbloquear (el plan de Ascenso)
        //
        //  Van DESPUES y no mezclados con los veinte de arriba, para que se vea
        //  de un vistazo que el pozo de salida no ha menguado: un jugador nuevo
        //  ve hoy exactamente los mismos veinte que veia ayer.
        // ------------------------------------------------------------------

        // 21. El unico sitio que cambia TECHO por vida de ahora, o sea al reves
        //     que el maestro y la fuente. Curarse ya se puede en cuatro sitios
        //     (hoguera, encrucijada, ermitanyo, estatua) — lo que no habia era
        //     un trato para el momento en el que llegas con tres vidas y el
        //     techo te da exactamente igual porque no vas a llegar a usarlo.
        out.add(new AscentEvent("beacon", AscentFeat.REACH_ACT_2, List.of(
                new Choice("ascent.event.opt.burnStores",
                        new Object[]{BEACON_MAX, BEACON_HEAL},
                        // Las dos mitades hacen falta: si el techo ya esta en
                        // el suelo, lowerMaxLife devuelve 0 y esto seria una
                        // cura de 12 gratis; y a vida llena se pagaria el
                        // techo por nada. Un solo motivo para las dos, porque
                        // decir "no te llega de vida maxima" estando a vida
                        // llena seria un motivo falso.
                        run -> run.getMaxLife() - BEACON_MAX >= AscentRun.MIN_MAX_LIFE
                                && run.getLife() < run.getMaxLife(),
                        "ascent.event.beacon.blocked",
                        (run, rnd) -> {
                            final int lowered = run.lowerMaxLife(BEACON_MAX);
                            final int before = run.getLife();
                            run.heal(BEACON_HEAL);
                            return new Outcome("ascent.event.res.burnStores", Extra.NONE,
                                    run.getLife() - before, lowered, run.getMaxLife());
                        }),
                leave())));

        // 22. El unico nodo que da carta Y reliquia de una vez. La reliquia se
        //     entrega en el efecto y la carta se elige despues (Extra.PICK_CARD).
        //     ⚠️ El texto del Outcome se ensenya AL FINAL, ya con la carta
        //     elegida — asi lo hace AscentEventScreen.pickCard — asi que dice
        //     lo que salio del fardo y nada de "y ahora, la carta": anunciar un
        //     paso que ya ha pasado es el principio 1 en pequenyo.
        out.add(new AscentEvent("peddler", AscentFeat.FIRST_WIN, List.of(
                new Choice("ascent.event.opt.buyBundle", new Object[]{PEDDLER_PRICE},
                        run -> run.getCredits() >= PEDDLER_PRICE, "ascent.event.needCredits",
                        (run, rnd) -> {
                            run.spend(PEDDLER_PRICE);
                            final AscentRelic relic = AscentRewards.relic(run,
                                    AscentRelic.Rarity.COMMON, rnd);
                            if (relic == null || !run.addRelic(relic)) {
                                // Sin reliquia que dar se cobra menos, no se
                                // cobra igual: pagar el fardo entero y recibir
                                // media cosa es un fallo, no un riesgo.
                                run.addCredits(PEDDLER_PRICE / 2);
                                return new Outcome("ascent.event.res.bundleHalf",
                                        Extra.PICK_CARD, PEDDLER_PRICE / 2);
                            }
                            return new Outcome("ascent.event.res.bundle", Extra.PICK_CARD,
                                    relic.getCardName());
                        }),
                leave())));

        // 23. La unica reliquia de JEFE que se consigue sin ganarle a un jefe, y
        //     lo que cuesta se nota: doce de vida son medio combate. La otra
        //     opcion es dinero sin riesgo, para que la decision sea de verdad y
        //     no un "si puedes, hazlo".
        out.add(new AscentEvent("crypt", AscentFeat.HOARDER, List.of(
                new Choice("ascent.event.opt.takeGoods", new Object[]{CRYPT_CREDITS},
                        null, null,
                        (run, rnd) -> {
                            run.addCredits(CRYPT_CREDITS);
                            return new Outcome("ascent.event.res.creditsFound", Extra.NONE,
                                    CRYPT_CREDITS);
                        }),
                new Choice("ascent.event.opt.takeCrown", new Object[]{CRYPT_LIFE},
                        run -> run.getLife() > CRYPT_LIFE, "ascent.event.needLife",
                        (run, rnd) -> {
                            final int lost = run.hurt(CRYPT_LIFE);
                            final AscentRelic relic = AscentRewards.relic(run,
                                    AscentRelic.Rarity.BOSS, rnd);
                            if (relic == null || !run.addRelic(relic)) {
                                run.heal(lost);
                                return new Outcome("ascent.event.res.nothing", Extra.NONE);
                            }
                            return new Outcome("ascent.event.res.gotRelic", Extra.NONE,
                                    relic.getCardName(), lost);
                        }),
                leave())));

        // 24. El unico sitio donde una reliquia MEJORA. Se lleva la comun mas
        //     antigua, y lo dice el rotulo: elegir cual pediria una pantalla
        //     nueva, y callarselo seria el principio 1 (un control que no hace
        //     lo que parece). "La mas antigua" es algo que el jugador puede
        //     mirar — las reliquias salen siempre en el orden en que las
        //     conseguiste.
        out.add(new AscentEvent("forge", AscentFeat.CHAMPION, List.of(
                new Choice("ascent.event.opt.reforge", new Object[]{FORGE_PRICE},
                        run -> run.getCredits() >= FORGE_PRICE && oldestCommon(run) != null,
                        "ascent.event.forge.blocked",
                        (run, rnd) -> {
                            final AscentRelic old = oldestCommon(run);
                            if (old == null) {
                                return new Outcome("ascent.event.res.noReforge", Extra.NONE);
                            }
                            // Se quita ANTES de sortear: si no, el sorteo la
                            // veria como "ya la llevas" y podria devolver una
                            // rara menos de las que hay disponibles. Y si el
                            // sorteo falla se devuelve tal cual, sin cobrar.
                            run.removeRelic(old);
                            final AscentRelic better = AscentRewards.relic(run,
                                    AscentRelic.Rarity.RARE, rnd);
                            if (better == null || !run.addRelic(better)) {
                                run.addRelic(old);
                                return new Outcome("ascent.event.res.noReforge", Extra.NONE);
                            }
                            run.spend(FORGE_PRICE);
                            return new Outcome("ascent.event.res.reforged", Extra.NONE,
                                    old.getCardName(), better.getCardName());
                        }),
                leave())));

        return List.copyOf(out);
    }

    /**
     * La reliquia comun mas antigua que llevas, o {@code null}.
     *
     * <p>«La mas antigua» y no «una cualquiera» porque es lo unico que el
     * jugador puede saber de antemano sin una pantalla que se lo pregunte: las
     * reliquias se guardan y se ensenyan en el orden en que se consiguieron.
     */
    private static AscentRelic oldestCommon(final AscentRun run) {
        for (final AscentRelic r : run.relics()) {
            if (r.getRarity() == AscentRelic.Rarity.COMMON) {
                return r;
            }
        }
        return null;
    }

    /**
     * La salida sin coste, que <b>todo evento tiene que tener</b>.
     *
     * <p>Principio 7: si todas las opciones costaran algo, el nodo dejaria de
     * ser una decision y seria una encerrona. Y ademas nunca esta bloqueada,
     * que es lo que garantiza que ningun evento se pueda quedar sin ninguna
     * opcion elegible — un nodo del que no se puede salir seria una run
     * atascada para siempre.
     */
    private static Choice leave() {
        return new Choice("ascent.event.opt.leave", new Object[0], null, null,
                (run, rnd) -> new Outcome("ascent.event.res.left", Extra.NONE));
    }
}
