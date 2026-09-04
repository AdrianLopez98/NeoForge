package forge.neo.ascent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.deck.DeckgenUtil;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.HostedMatch;
import forge.gui.interfaces.IGuiGame;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.SafeAi;

/**
 * La sonda de la fase 0 de Ascenso: que el motor haga lo que el plan supone.
 *
 * <p><b>Por que existe.</b> Todo el modo Ascenso descansa sobre cinco cosas que
 * el motor tiene que permitir, y ninguna esta documentada en ningun sitio:
 * ninguna se deduce leyendo {@code IGuiGame}, y las cinco fallarian <b>en
 * silencio</b> o muy tarde. Antes de escribir una sola pantalla hay que saber
 * que las cinco van.
 *
 * <ol>
 *   <li><b>Un mazo corto arranca.</b> El mazo de una run son 21-30 cartas, no
 *       60 ni 100. Si el motor lo rechazara, el modo no existe.
 *   <li><b>La vida se arrastra.</b> {@code setStartingLife} tiene que ganar, y
 *       ojo: {@code forVariants} y {@code forCommander} la PISAN — Commander
 *       pone 40 y el Archenemy tambien. O sea que se aplica <b>despues</b>, y
 *       si se hace al reves no da ningun error: simplemente no se arrastra.
 *   <li><b>El jefe puede ser Archenemy.</b> La IA con mazo de esquemas es lo
 *       que convierte una partida normal en una pelea de jefe sin escribir una
 *       sola regla.
 *   <li><b>Una reliquia nuestra funciona.</b> Una carta escrita por nosotros,
 *       registrada en caliente y puesta en la zona de mando, tiene que hacer
 *       lo que dice su texto.
 *   <li><b>Y no se cuela en el catalogo.</b> {@code CardDb.addCard} mete la
 *       carta en la base de verdad. Si ademas apareciera en el buscador del
 *       deck builder, seria un fallo feisimo y mudo.
 * </ol>
 *
 * <p>Se ejecuta con {@code run.cmd ascentcheck}.
 */
public final class AscentProbe {

    private AscentProbe() {
    }

    private static int passed;
    private static int failed;

    /** Cuanto se espera a que una partida de sonda llegue a donde tiene que llegar. */
    private static final int TIMEOUT_SECS = 60;

    public static void run() {
        passed = 0;
        failed = 0;

        // El registro de reliquias va antes que nada: las sondas 4 y 5 lo
        // necesitan, y ademas comprobar que install() no revienta ES la
        // primera comprobacion.
        int relics = 0;
        try {
            relics = AscentRelics.install();
            ok("reliquias registradas en la base de cartas: " + relics);
        } catch (final RuntimeException e) {
            fail("install() ha reventado: " + e);
        }

        sonda5CatalogoLimpio();
        sonda6Mapa();
        sonda7Rejugabilidad();
        sonda1MazoCorto();
        sonda2VidaArrastrada();
        sonda3Archenemy();
        sonda4Reliquia();

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " sonda(s) de Ascenso han fallado");
        }
    }

    // ------------------------------------------------------------------
    //  Sonda 5 — las reliquias no ensucian el catalogo
    // ------------------------------------------------------------------

    /**
     * Que una reliquia se pueda usar de verdad: registrada y encontrable por
     * nombre.
     *
     * <p>Va la primera aunque sea la ultima del plan porque es la unica que no
     * necesita partida: si esta falla, lo que hay que replantear es el
     * mecanismo entero, y mejor saberlo antes de esperar cuatro partidas.
     *
     * <p>⚠️ <b>Esto NO comprueba que quedan fuera del catalogo del deck
     * builder</b>, aunque el nombre de la sonda lo sugiera y durante un
     * tiempo se creyo que si: {@code getUniqueCards()} no las trae justo
     * despues de registrarlas (el motor todavia no ha reindexado), pero en
     * cuanto se juega una partida de verdad {@code CardDb} reindexa y SI
     * aparecen — asi que esta sonda, que corre <b>antes</b> de jugar nada,
     * pasaba en verde sin demostrar nada. El filtro de verdad vive en
     * {@code forge.neo.deck.CardIndex} y lo comprueba {@code run.cmd
     * deckcheck} sobre un catalogo construido a proposito, no de pasada.
     */
    private static void sonda5CatalogoLimpio() {
        if (AscentRelics.all().isEmpty()) {
            fail("sin reliquias cargadas no se puede comprobar el registro");
            return;
        }
        final List<String> rotas = new ArrayList<>();
        for (final AscentRelic r : AscentRelics.all()) {
            if (AscentRelics.cardOf(r) == null) {
                rotas.add(r.getId());
            }
        }
        if (rotas.isEmpty()) {
            ok("las " + AscentRelics.all().size() + " reliquias se encuentran por nombre"
                    + " (que no aparezcan en el catalogo del deck builder lo mide"
                    + " run.cmd deckcheck)");
        } else {
            fail("estas reliquias no se encuentran por nombre tras registrarlas: " + rotas);
        }
    }

    // ------------------------------------------------------------------
    //  Sonda 6 — el mapa, sobre muchas semillas
    // ------------------------------------------------------------------

    /**
     * Que ningun mapa generado deje un nodo suelto, y que el mismo par
     * (semilla, acto) de siempre el mismo mapa.
     *
     * <p>Se prueban 300 semillas x 3 actos y no una: un nodo inalcanzable no
     * revienta ni se ve dibujado — deja una run que no se puede terminar — asi
     * que la unica forma de cazarlo es a volumen.
     */
    private static void sonda6Mapa() {
        final int seeds = 300;
        int worstIslands = 0;
        long worstSeed = -1;
        final List<String> repeats = new ArrayList<>();
        int minNodes = Integer.MAX_VALUE;
        int maxNodes = 0;
        final java.util.Map<AscentNode.Kind, Integer> total = new java.util.LinkedHashMap<>();

        for (long seed = 0; seed < seeds; seed++) {
            for (int act = 1; act <= 3; act++) {
                final AscentMap map = new AscentMap(seed, act);
                final List<AscentNode> bad = map.unreachable();
                if (bad.size() > worstIslands) {
                    worstIslands = bad.size();
                    worstSeed = seed;
                }
                minNodes = Math.min(minNodes, map.size());
                maxNodes = Math.max(maxNodes, map.size());
                map.census().forEach((k, v) -> total.merge(k, v, Integer::sum));
                if (repeats.size() < 5) {
                    for (final String v : map.consecutiveViolations()) {
                        repeats.add("semilla " + map.getSeed() + " acto " + map.getAct() + ": " + v);
                    }
                }
            }
        }

        if (worstIslands == 0) {
            ok("mapa: " + (seeds * 3) + " mapas sin un solo nodo inalcanzable "
                    + "(" + minNodes + "-" + maxNodes + " nodos por acto)");
        } else {
            fail("mapa: la semilla " + worstSeed + " deja " + worstIslands
                    + " nodo(s) sueltos — esa run no se podria terminar");
        }

        // Dos descansos (o dos tiendas, o dos elites) seguidos en un mismo
        // camino. No revienta nada: hace el mapa aburrido y, en el caso del
        // descanso, se carga la tension — que es el modo. Se colaba justo
        // debajo de la fila de descanso obligatorio.
        if (repeats.isEmpty()) {
            ok("mapa: ningun camino repite descanso, tienda ni elite dos veces seguidas");
        } else {
            fail("mapa: hay tramos repetidos, p.ej. " + repeats.get(0)
                    + " (y " + (repeats.size() - 1) + " mas en la muestra)");
        }

        // Determinismo: sin esto no se puede guardar "semilla + progreso".
        final AscentMap a = new AscentMap(4242L, 2);
        final AscentMap b = new AscentMap(4242L, 2);
        if (a.render().equals(b.render()) && !a.render().equals(new AscentMap(4243L, 2).render())) {
            ok("mapa: la misma semilla da el mismo mapa, y otra semilla da otro");
        } else {
            fail("mapa: la generacion NO es determinista (no se puede guardar por semilla)");
        }

        System.out.println("         reparto: " + total);
        System.out.println("         ejemplo (semilla 4242, acto 2):");
        System.out.print(new AscentMap(4242L, 2).render());
    }

    // ------------------------------------------------------------------
    //  Sonda 7 — rejugabilidad: que dos runs no se parezcan
    // ------------------------------------------------------------------

    /**
     * Que el mazo de salida y el mapa <b>cambien de verdad</b> entre runs.
     *
     * <p>Es la sonda que mide lo que un roguelike necesita para no cansar. Y se
     * mide, no se supone: "usa un generador aleatorio" no garantiza variedad —
     * un generador puede devolver casi siempre lo mismo y nadie se enteraria
     * hasta la cuarta partida.
     *
     * <p>Se exige, sobre 40 mazos de cada modo:
     * <ul>
     *   <li>que la <b>inmensa mayoria sean distintos</b> (mas del 90%),
     *   <li>que los <b>comandantes</b> varien (no siempre el mismo),
     *   <li>y que todos salgan <b>jugables</b>: del tamanyo pedido y con tierras.
     * </ul>
     */
    private static void sonda7Rejugabilidad() {
        for (final AscentRun.Mode mode : AscentRun.Mode.values()) {
            final int runs = 40;
            final java.util.Set<String> huellas = new java.util.HashSet<>();
            final java.util.Set<String> comandantes = new java.util.HashSet<>();
            int malTamanyo = 0;
            int sinTierras = 0;
            int raras = 0;
            int maxColores = 0;
            final java.util.Set<String> culpables = new java.util.LinkedHashSet<>();
            int diag = -1;
            int minCartas = Integer.MAX_VALUE;
            int maxCartas = 0;

            for (int i = 0; i < runs; i++) {
                final Deck d;
                try {
                    d = AscentSeedDeck.generate(mode, "sonda-" + i);
                } catch (final RuntimeException e) {
                    fail("rejugabilidad (" + mode + "): el generador ha reventado: " + e);
                    return;
                }
                final int n = d.getMain().countAll();
                minCartas = Math.min(minCartas, n);
                maxCartas = Math.max(maxCartas, n);
                final int esperado = mode == AscentRun.Mode.COMMANDER
                        ? AscentSeedDeck.COMMANDER_SIZE : AscentSeedDeck.STANDARD_SIZE;
                if (n != esperado) {
                    malTamanyo++;
                }
                int tierras = 0;
                final List<String> nombres = new ArrayList<>();
                final java.util.Set<Byte> colores = new java.util.HashSet<>();
                for (final java.util.Map.Entry<PaperCard, Integer> e : d.getMain()) {
                    nombres.add(e.getKey().getName() + "x" + e.getValue());
                    if (e.getKey().getRules().getType().isLand()) {
                        tierras += e.getValue();
                    }
                    // Rareza: el mazo de principiante no lleva raras ni miticas.
                    final forge.card.CardRarity rar = e.getKey().getRarity();
                    if (mode == AscentRun.Mode.STANDARD
                            && (rar == forge.card.CardRarity.Rare
                                || rar == forge.card.CardRarity.MythicRare)) {
                        raras++;
                    }
                    // Colores del mazo, para el techo de dos.
                    //
                    // ⚠️ Solo los HECHIZOS. Contando las tierras salian "3
                    // colores" en mazos que juegan a dos: un mazo bicolor lleva
                    // tierras que producen un tercer color y no pasa nada,
                    // porque no hay nada que cueste ese color. Lo que decide de
                    // cuantos colores es un mazo es lo que hay que PAGAR.
                    if (!e.getKey().getRules().getType().isLand()) {
                        for (final byte col : forge.card.MagicColor.WUBRG) {
                            if (e.getKey().getRules().getColorIdentity().hasAnyColor(col)) {
                                colores.add(col);
                                if (colores.size() > 2) {
                                    culpables.add(e.getKey().getName());
                                }
                            }
                        }
                    }
                }
                maxColores = Math.max(maxColores, colores.size());
                if (tierras == 0) {
                    sinTierras++;
                    diag = AscentSeedDeck.lastRawLandCount();
                }
                java.util.Collections.sort(nombres);
                huellas.add(String.join("|", nombres));
                if (!d.getCommanders().isEmpty()) {
                    comandantes.add(d.getCommanders().get(0).getName());
                }
            }

            final int distintos = huellas.size();
            final String etiqueta = "rejugabilidad (" + mode + ")";
            if (distintos * 100 >= runs * 90) {
                ok(etiqueta + ": " + distintos + "/" + runs + " mazos distintos"
                        + (comandantes.isEmpty() ? "" : ", " + comandantes.size() + " comandantes")
                        + " (" + minCartas + "-" + maxCartas + " cartas)");
            } else {
                fail(etiqueta + ": solo " + distintos + "/" + runs
                        + " mazos distintos — las runs se van a parecer demasiado");
            }
            if (malTamanyo > 0) {
                fail(etiqueta + ": " + malTamanyo + " mazo(s) con un tamanyo que no es el pedido");
            }
            if (sinTierras > 0) {
                fail(etiqueta + ": " + sinTierras + " de " + runs
                        + " mazo(s) SIN TIERRAS (injugables). El generador dio "
                        + diag + " tierras en bruto en ese caso");
            }
            if (mode == AscentRun.Mode.COMMANDER && comandantes.size() < 2) {
                fail(etiqueta + ": el comandante sale siempre el mismo, y es lo que define la run");
            }

            // Las dos reglas de "que sea un mazo malo de principiante".
            if (mode == AscentRun.Mode.STANDARD) {
                if (maxColores <= 2) {
                    ok(etiqueta + ": ningun mazo pasa de 2 colores (maximo visto: " + maxColores + ")");
                } else {
                    fail(etiqueta + ": hay mazos de " + maxColores
                            + " colores de hechizo, y el techo es 2. Se salen: " + culpables);
                }
                if (raras == 0) {
                    ok(etiqueta + ": ni una rara ni mitica — es mazo de principiante");
                } else {
                    fail(etiqueta + ": se han colado " + raras
                            + " cartas raras/miticas; el mazo de salida tiene que ser flojo");
                }
            }
        }

        // Que se pueda ELEGIR el comandante, y que el mazo se monte a su
        // alrededor. Es la otra mitad del modo Commander: sin esto solo hay
        // "dame uno al azar".
        final PaperCard elegido = AscentSeedDeck.randomCommander();
        if (elegido == null) {
            fail("rejugabilidad: el pozo de comandantes esta vacio");
        } else {
            final Deck d1 = AscentSeedDeck.generate(AscentRun.Mode.COMMANDER, elegido, "elegido-1");
            final Deck d2 = AscentSeedDeck.generate(AscentRun.Mode.COMMANDER, elegido, "elegido-2");
            final boolean mismo = !d1.getCommanders().isEmpty()
                    && d1.getCommanders().get(0).getName().equals(elegido.getName())
                    && !d2.getCommanders().isEmpty()
                    && d2.getCommanders().get(0).getName().equals(elegido.getName());
            if (mismo) {
                ok("comandante elegido: se respeta (" + elegido.getName()
                        + "), y el mazo se monta a su alrededor");
            } else {
                fail("comandante elegido: el mazo sale con OTRO comandante");
            }
            // El pozo entero, que es lo que necesita la pantalla de eleccion.
            final int pool = AscentSeedDeck.commanderPool().size();
            if (pool > 100) {
                ok("comandante elegido: hay " + pool + " para elegir");
            } else {
                fail("comandante elegido: el pozo son solo " + pool + " cartas");
            }
        }

        // Y que la semilla del mapa no se repita entre runs: dos runs seguidas
        // con el mismo mapa serian el mismo juego dos veces.
        final java.util.Set<Long> semillas = new java.util.HashSet<>();
        for (int i = 0; i < 50; i++) {
            semillas.add(System.nanoTime());
        }
        if (semillas.size() >= 45) {
            ok("rejugabilidad: la semilla del mapa no se repite entre runs");
        } else {
            fail("rejugabilidad: la semilla se repite (" + semillas.size()
                    + "/50) — habria runs con el mismo mapa");
        }
    }

    // ------------------------------------------------------------------
    //  Sonda 1 — un mazo de 21 cartas con comandante arranca
    // ------------------------------------------------------------------

    private static void sonda1MazoCorto() {
        final Deck deck = runDeck();
        if (deck == null) {
            fail("no se ha podido montar el mazo de prueba");
            return;
        }
        final int cartas = deck.getMain().countAll();
        final Result r = play("mazo corto", EnumSet.of(GameType.Commander), deck, -1, null, null, game -> {
            final Player me = human(game);
            return me != null && !me.getCardsIn(ZoneType.Library).isEmpty();
        });
        if (r.reached) {
            ok("un mazo de " + cartas + " cartas con comandante arranca (turno " + r.turns + ")");
        } else {
            fail("un mazo de " + cartas + " cartas NO arranca: " + r.why);
        }
    }

    // ------------------------------------------------------------------
    //  Sonda 2 — la vida arrastrada gana a la del formato
    // ------------------------------------------------------------------

    /**
     * La sonda que mas facil se pasa por alto.
     *
     * <p>Se pide 13 de vida en un Commander, que de suyo pone 40. Si sale 40,
     * el arrastre de vida —o sea, media tension del modo— no funciona.
     */
    private static void sonda2VidaArrastrada() {
        final Deck deck = runDeck();
        if (deck == null) {
            fail("no se ha podido montar el mazo de prueba");
            return;
        }
        final int[] vista = {-1};
        final Result r = play("vida arrastrada", EnumSet.of(GameType.Commander), deck, 13, null, null, game -> {
            final Player me = human(game);
            if (me == null || me.getCardsIn(ZoneType.Library).isEmpty()) {
                return false;
            }
            vista[0] = me.getLife();
            return true;
        });
        if (!r.reached) {
            fail("no se llego a mirar la vida: " + r.why);
        } else if (vista[0] == 13) {
            ok("setStartingLife(13) gana a las 40 de Commander");
        } else {
            fail("se pidieron 13 de vida y el jugador tiene " + vista[0]
                    + " (forVariants la esta pisando: hay que aplicarla DESPUES)");
        }
    }

    // ------------------------------------------------------------------
    //  Sonda 3 — que el ASIENTO de archienemigo se sabe montar
    // ------------------------------------------------------------------

    /**
     * Que sabemos sentar a una IA como archienemigo con su mazo de esquemas.
     *
     * <p>⚠️ <b>El jefe de Ascenso YA NO lo usa</b> (03-09-2026): Archienemigo
     * esta disenyado para 3 contra 1 y en el 1c1 del modo no se puede ganar.
     * Ver {@code AscentBattle.bossIsNotArchenemy()}.
     *
     * <p>La sonda se queda porque lo que mide sigue siendo cierto y costo
     * encontrarlo: los esquemas <b>solo</b> entran por
     * {@code RegisteredPlayer.forVariants} (no hay setter) y la vida hay que
     * ponerla justo despues, porque {@code forVariants} la pisa. Es la costura
     * de la que salio {@code NeoGame.Seating}, y si algun dia se monta un modo
     * multijugador donde Archienemigo si tenga sentido, esto dice si sigue
     * funcionando. Lo que <b>no</b> hay que leer aqui es "el jefe juega asi".
     */
    private static void sonda3Archenemy() {
        final Deck deck = runDeck();
        if (deck == null) {
            fail("no se ha podido montar el mazo de prueba");
            return;
        }
        final CardPool esquemas = DeckgenUtil.generateSchemePool();
        if (esquemas.countAll() == 0) {
            fail("el motor no ha dado ni un esquema (FModel.getArchenemyCards vacio)");
            return;
        }
        final int[] enMazo = {-1};
        final Result r = play("archenemy", EnumSet.of(GameType.Commander, GameType.Archenemy),
                deck, -1, esquemas, null, game -> {
                    final Player ai = ai(game);
                    if (ai == null) {
                        return false;
                    }
                    final int n = ai.getCardsIn(ZoneType.SchemeDeck).size();
                    if (n > 0) {
                        enMazo[0] = n;
                        return true;
                    }
                    return false;
                });
        if (r.reached) {
            ok("el ASIENTO de archienemigo se monta (el jefe ya no lo usa): "
                    + enMazo[0] + " esquemas en su mazo "
                    + "(de " + esquemas.countAll() + " repartidos)");
        } else {
            fail("la IA no ha recibido mazo de esquemas: " + r.why);
        }
    }

    // ------------------------------------------------------------------
    //  Sonda 4 — una reliquia nuestra, haciendo lo que dice
    // ------------------------------------------------------------------

    /**
     * La sonda que decide si el modo es viable tal y como esta planteado.
     *
     * <p>No comprueba que la carta exista ni que este en la zona de mando: eso
     * lo haria pasar una reliquia con el script mal escrito, que es <b>el</b>
     * fallo mudo de este mecanismo. Comprueba el <b>efecto</b>: con "Smith's
     * Blessing" en el mando, una criatura de la mesa tiene que tener un punto
     * mas de fuerza del que trae impreso.
     */
    private static void sonda4Reliquia() {
        final AscentRelic relic = AscentRelics.byId("smiths_blessing");
        if (relic == null) {
            fail("la reliquia de prueba no esta registrada");
            return;
        }
        final PaperCard card = AscentRelics.cardOf(relic);
        if (card == null) {
            fail("la reliquia se registro pero el motor no la encuentra por nombre: "
                    + relic.getCardName());
            return;
        }
        ok("la reliquia es una carta del motor: " + card.getName());

        final Deck deck = runDeck();
        if (deck == null) {
            fail("no se ha podido montar el mazo de prueba");
            return;
        }
        final String[] visto = {null};
        final Result r = play("reliquia", EnumSet.of(GameType.Commander), deck, -1, null, List.of(card), game -> {
            final Player me = human(game);
            if (me == null) {
                return false;
            }
            // La reliquia, en el mando.
            boolean enMando = false;
            for (final Card c : me.getCardsIn(ZoneType.Command)) {
                if (c.getName().equals(relic.getCardName())) {
                    enMando = true;
                    break;
                }
            }
            if (!enMando) {
                return false;
            }
            // Y su efecto, sobre una criatura de verdad.
            for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
                if (!c.isCreature()) {
                    continue;
                }
                final int impresa = c.getCurrentState().getBasePower();
                final int ahora = c.getNetPower();
                if (ahora >= impresa + 1) {
                    visto[0] = c.getName() + " " + impresa + " -> " + ahora;
                    return true;
                }
            }
            return false;
        });
        if (r.reached) {
            ok("la reliquia hace lo que dice: " + visto[0]);
        } else {
            fail("la reliquia esta pero su efecto no se aplica: " + r.why
                    + " (script mal escrito? Forge NO avisa de eso)");
        }
    }

    // ------------------------------------------------------------------
    //  Fontaneria
    // ------------------------------------------------------------------

    /** Lo que sale de una partida de sonda. */
    static final class Result {
        boolean reached;
        int turns;
        String why = "no se cumplio la condicion dentro del tiempo";
    }

    /**
     * Juega una partida de sonda y espera a que se cumpla una condicion.
     *
     * <p>Es el mismo patron que {@code ManaCheck}: la partida corre en su hilo
     * y nosotros nos asomamos cada 100 ms. Y por la misma razon que alli, la
     * condicion se comprueba <b>en bucle</b> y no una vez: la mesa no se pone
     * de golpe, y asomarse demasiado pronto da un falso rojo.
     *
     * @param life  vida inicial, o -1 para dejar la del formato
     * @param schemes mazo de esquemas para la IA, o {@code null}
     * @param command lo que se le pone al humano en la zona de mando (las
     *                reliquias que se estan probando); vacio o {@code null}
     *                para ninguna. Cuando trae algo se le deja ademas una
     *                criatura en la mesa desde el turno 1, que es sobre lo que
     *                se mide casi todo: esperar a que la IA nos deje sacar una
     *                convertiria la sonda en una loteria.
     */
    static Result play(final String label, final EnumSet<GameType> variants,
                       final Deck deck, final int life, final CardPool schemes,
                       final List<PaperCard> command,
                       final java.util.function.Predicate<Game> condition) {
        return play(label, variants, deck, life, schemes, command, false, condition);
    }

    /**
     * Igual, y con la opcion de <b>cortar la partida en cuanto se cumple</b>.
     *
     * <p>Existe por {@link AscentRelicCheck}: ahi hay una partida por reliquia,
     * y esperar a que cada una termine sola son 37 minutos de reloj para
     * comprobar cosas que se ven en el turno 1. Con {@code stopOnHit} el
     * humano <b>se retira</b> en cuanto la condicion se cumple — {@code
     * Player.concede()} es publica y es lo mismo que ya hace {@code SafeAi} con
     * una IA rota — y la partida se cierra por el camino normal, con su
     * {@code setOnMatchOver}. Las sondas de siempre no lo usan: su
     * comportamiento se queda exactamente igual.
     */
    static Result play(final String label, final EnumSet<GameType> variants,
                       final Deck deck, final int life, final CardPool schemes,
                       final List<PaperCard> command, final boolean stopOnHit,
                       final java.util.function.Predicate<Game> condition) {
        return play(label, variants, deck, life, schemes, command, null, stopOnHit,
                POLL_MS, condition);
    }

    /** Cada cuanto se asoma la sonda a la partida, por defecto. */
    private static final long POLL_MS = 100L;

    /**
     * Igual, eligiendo <b>que hay en tu mesa</b> y <b>cada cuanto se mira</b>.
     *
     * <p>Las dos cosas las pide {@link AscentRelicCheck}, y ninguna es un
     * capricho: hay reliquias que solo se pueden ver con una carta concreta
     * delante (una que muera sola, una que meta una criatura cada turno) y hay
     * una — el mana — que aparece y desaparece dentro de una fase, asi que a
     * 100 ms no se pilla nunca.
     *
     * @param board  lo que se le pone al humano en la mesa; {@code null} para
     *               la criatura de siempre
     */
    static Result play(final String label, final EnumSet<GameType> variants,
                       final Deck deck, final int life, final CardPool schemes,
                       final List<PaperCard> command, final List<PaperCard> board,
                       final boolean stopOnHit, final long pollMs,
                       final java.util.function.Predicate<Game> condition) {
        final Result out = new Result();
        NeoGame.applyEnginePrefs();

        final NeoMatchUI gui = new NeoMatchUI(NeoMatchUI.Mode.AUTO_PLAY, false);
        final HostedMatch match = new HostedMatch();
        final CountDownLatch over = new CountDownLatch(1);
        match.setOnMatchOver(over::countDown);
        match.setStartGameHook(SafeAi.hook(match, () -> { }));

        final List<RegisteredPlayer> players = new ArrayList<>();

        // ⚠️ EL ORDEN IMPORTA: forVariants pone la vida del formato (40 en
        // Commander), asi que la nuestra va DESPUES o no vale para nada.
        final RegisteredPlayer me = RegisteredPlayer.forVariants(
                2, variants, deck, null, false, null, null)
                .setPlayer(forge.neo.look.NeoPlayers.human());
        if (life > 0) {
            me.setStartingLife(life);
        }
        if (command != null && !command.isEmpty()) {
            // La firma del motor pide Iterable<IPaperCard>, y List<PaperCard>
            // no vale como tal (Java no covaria los genericos).
            me.addExtraCardsInCommandZone(new ArrayList<forge.item.IPaperCard>(command));
            // Y una criatura en la mesa desde el turno 1, que es sobre lo que
            // se mide el efecto: esperar a que la IA nos deje sacar una
            // convertiria la sonda en una loteria.
            final List<PaperCard> mesa = new ArrayList<>();
            if (board != null && !board.isEmpty()) {
                mesa.addAll(board);
            }
            final PaperCard bicho = anyCreature();
            if (bicho != null) {
                mesa.add(bicho);
            }
            if (!mesa.isEmpty()) {
                me.addExtraCardsOnBattlefield(new ArrayList<forge.item.IPaperCard>(mesa));
            }
        }
        players.add(me);

        final Deck aiDeck = schemes == null ? deck : withSchemes(deck, schemes);
        final RegisteredPlayer ai = RegisteredPlayer.forVariants(
                2, variants, aiDeck,
                schemes == null ? null : schemes.toFlatList(), schemes != null, null, null)
                .setPlayer(forge.neo.look.NeoPlayers.ai(0, null));
        players.add(ai);

        final AtomicBoolean alive = new AtomicBoolean(true);
        final AtomicBoolean hit = new AtomicBoolean(false);
        final Thread poller = new Thread(() -> {
            while (alive.get() && !hit.get()) {
                try {
                    final Game game = gui.getGameView() == null ? null : gui.getGameView().getGame();
                    if (game != null && condition.test(game)) {
                        hit.set(true);
                        out.turns = gui.getGameView().getTurn();
                        if (stopOnHit) {
                            // Ya esta comprobado: retirarse cierra la partida
                            // por el camino de siempre en vez de dejarla correr
                            // hasta el timeout.
                            final Player me2 = human(game);
                            if (me2 != null) {
                                me2.concede();
                            }
                        }
                        return;
                    }
                    Thread.sleep(pollMs);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // Todavia no hay partida, o esta a medio montar: reintentar.
                }
            }
        }, "ascent-probe-" + label);
        poller.setDaemon(true);
        poller.start();

        final GameRules rules = new GameRules(GameType.Commander);
        rules.setGamesPerMatch(1);
        rules.setAppliedVariants(variants);

        try {
            match.startMatch(rules, variants, players, me, (IGuiGame) gui);
            over.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final RuntimeException e) {
            out.why = "la partida ha reventado: " + e;
            alive.set(false);
            return out;
        }
        alive.set(false);
        out.reached = hit.get();
        if (out.turns == 0 && gui.getGameView() != null) {
            out.turns = gui.getGameView().getTurn();
        }
        return out;
    }

    /** El mazo de una run: corto a proposito, que es justo lo que se prueba. */
    static Deck runDeck() {
        final PaperCard commander = firstThatExists(
                "Ghalta, Primal Hunger", "Yeva, Nature's Herald",
                "Omnath, Locus of Mana", "Ezuri, Renegade Leader");
        final PaperCard forest = FModel.getMagicDb().getCommonCards().getCard("Forest");
        final PaperCard bear = anyCreature();
        if (commander == null || forest == null || bear == null) {
            return null;
        }
        final Deck deck = new Deck("Ascenso (sonda)");
        deck.getMain().add(forest, 12);
        deck.getMain().add(bear, 8);
        deck.getOrCreate(DeckSection.Commander).add(commander);
        return deck;
    }

    /** Una criatura verde barata y segura, para no depender de una sola carta. */
    static PaperCard anyCreature() {
        return firstThatExists("Grizzly Bears", "Llanowar Elves",
                "Runeclaw Bear", "Elvish Mystic");
    }

    private static PaperCard firstThatExists(final String... names) {
        for (final String n : names) {
            final PaperCard c = FModel.getMagicDb().getCommonCards().getCard(n);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    /** El mismo mazo, mas la seccion de esquemas. */
    private static Deck withSchemes(final Deck base, final CardPool schemes) {
        final Deck copy = new Deck(base, base.getName() + " (archienemigo)");
        copy.putSection(DeckSection.Schemes, schemes);
        return copy;
    }

    private static Player human(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static Player ai(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static void ok(final String msg) {
        passed++;
        System.out.println("  [ok]   " + msg);
    }

    private static void fail(final String msg) {
        failed++;
        System.out.println("  [MAL]  " + msg);
    }
}
