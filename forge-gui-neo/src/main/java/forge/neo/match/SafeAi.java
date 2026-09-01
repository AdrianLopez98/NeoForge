package forge.neo.match;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import forge.LobbyPlayer;
import forge.ai.PlayerControllerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.match.HostedMatch;

/**
 * Que un fallo de la IA no se lleve la partida por delante.
 *
 * <p><b>De donde sale esto.</b> Jugando una partida en red a cuatro, la IA de
 * Forge revento con un NPE dentro de {@code AiBlockController} mientras decidia
 * bloqueos. La traza acaba asi:
 *
 * <pre>
 *   AiController.declareBlockersFor
 *   PhaseHandler.declareBlockersTurnBasedAction
 *   PhaseHandler.onPhaseBegin
 *   PhaseHandler.mainLoopStep
 *   PhaseHandler.mainGameLoop        &lt;-- EL BUCLE DE LA PARTIDA
 *   GameAction.startGame
 * </pre>
 *
 * <p>O sea que la excepcion se lleva <b>el bucle entero</b>. A partir de ahi no
 * hay partida: ni prioridad, ni fases, ni forma de conceder — el hilo <i>es</i>
 * la partida, y su maquina de estados vive en esa pila de llamadas. Los dos
 * jugadores se quedaron mirando una mesa muerta, con Salir sin efecto.
 *
 * <p><b>Por que no vale con "que la IA conceda".</b> Conceder es una accion de
 * la partida y hay que pedirsela al motor; con el bucle ya muerto no queda
 * nadie a quien pedirsela. Hay que cogerlo <b>antes</b> de que suba.
 *
 * <p><b>Y se puede, sin tocar el motor.</b> {@code PlayerControllerAi} es
 * publica, no es final y sus decisiones son sobrescribibles, y
 * {@code Player.dangerouslySetController} es publica — la usa el propio Forge
 * para sentar una IA en el sitio de quien se desconecta. Asi que al empezar
 * cada partida se le cambia el controlador a cada IA por uno igual pero
 * envuelto: si una decision revienta, se anota y esa IA <b>se salta esa
 * decision</b> (no ataca, no bloquea, o pasa) en vez de tirar la partida.
 *
 * <p>Es peor IA durante un instante, y una partida entera de diferencia.
 *
 * <p><b>Lo que NO hace:</b> no arregla el fallo de Forge — eso es un PR al
 * upstream, no un parche local (regla de oro) — ni protege contra un fallo del
 * motor de reglas. Solo contra la IA, que es el sitio donde un fallo no tiene
 * por que ser mortal.
 */
public final class SafeAi {

    private SafeAi() {
    }

    /**
     * Rompe la IA a proposito, para poder comprobar todo esto sin esperar a que
     * vuelva a salir la carta rara. {@code -Dneo.ai.crashTest=true}
     */
    private static final String CRASH_TEST = System.getProperty("neo.ai.crashTest", "");

    /** {@code -Dneo.ai.crashTest=true} falla una vez; {@code =always}, siempre. */
    private static final boolean CRASH_ALWAYS = "always".equalsIgnoreCase(CRASH_TEST);

    private static final boolean CRASH_ONCE = CRASH_ALWAYS
            || "true".equalsIgnoreCase(CRASH_TEST);

    /**
     * Cuantos fallos se le aguantan a una IA antes de retirarla.
     *
     * <p>Uno puede ser mala suerte. Tres seguidos no: significa que hay algo en
     * la mesa con lo que esa IA no sabe lidiar, y entonces <b>no vuelve a
     * bloquear ni a atacar en toda la partida</b>. Un rival que no hace nada es
     * peor que un rival menos: no es una partida, es un cadaver ocupando sitio.
     * A partir de ahi se retira y los demas siguen jugando.
     */
    private static final int MAX_FAILS = Integer.getInteger("neo.ai.maxFails", 3);

    /**
     * Se puede apagar con {@code -Dneo.ai.guard=false}.
     *
     * <p>Apagarlo no quita el envoltorio: lo que hace es <b>volver a lanzar</b>
     * la excepcion en vez de tragarsela. Y es a proposito — la primera version
     * se saltaba el envoltorio entero, y como el inyector de fallos vive dentro,
     * la contraprueba no rompia nada y "pasaba" sin demostrar nada. Una prueba
     * que no puede fallar no prueba nada.
     *
     * <p>Sirve para dos cosas: demostrar que sin el la partida SI se muere, y
     * que una sesion futura pueda descartarlo si alguna vez tapa un fallo que
     * convenga ver.
     */
    private static final boolean GUARD =
            !"false".equalsIgnoreCase(System.getProperty("neo.ai.guard", "true"));

    /** Cuantas decisiones se han rescatado. Lo mira el comprobador. */
    private static final AtomicInteger RESCUES = new AtomicInteger();

    public static int rescues() {
        return RESCUES.get();
    }

    /** Cuantas IA se han retirado por fallar demasiado. */
    private static final AtomicInteger CONCEDES = new AtomicInteger();

    public static int concedes() {
        return CONCEDES.get();
    }

    public static void resetRescues() {
        RESCUES.set(0);
    }

    /**
     * El gancho de arranque de un match, con el blindaje puesto.
     *
     * <p>Se COMPONE en vez de sustituir: ese gancho ya lo usan los puzzles y la
     * prueba del comandante exiliado, y quedarse con el seria cambiar un fallo
     * por otro.
     *
     * @param extra lo que ya hubiera que hacer al empezar; puede ser null
     */
    public static Runnable hook(final HostedMatch match, final Runnable extra) {
        return () -> {
            try {
                protect(match.getGame());
            } catch (final RuntimeException e) {
                // Blindar es un extra: si fallara, la partida sigue como antes.
                System.out.println("[ia] no se ha podido blindar la IA: " + e);
            }
            if (extra != null) {
                extra.run();
            }
        };
    }

    /**
     * Envuelve el controlador de cada IA de esta partida.
     *
     * @return cuantas IA se han blindado
     */
    public static int protect(final Game game) {
        if (game == null) {
            return 0;
        }
        int n = 0;
        for (final Player p : game.getPlayers()) {
            if (!(p.getController() instanceof PlayerControllerAi) || p.getController() instanceof Guarded) {
                continue;
            }
            final Guarded safe = new Guarded(game, p, p.getLobbyPlayer());
            // El perfil que el motor deduce del mazo se recalcula, no se
            // hereda: el campo es privado y su fuente — el mazo — sigue a mano.
            final Deck deck = p.getRegisteredPlayer() == null
                    ? null : p.getRegisteredPlayer().getDeck();
            if (deck != null) {
                safe.setupAutoProfile(deck);
            }
            p.dangerouslySetController(safe);
            n++;
        }
        if (n > 0) {
            System.out.printf(Locale.ROOT, "  [ia] %d rival(es) blindado(s) contra fallos%n", n);
        }
        return n;
    }

    /**
     * Una IA normal, pero que no se lleva la partida si se cae.
     *
     * <p>Solo se envuelven las decisiones que el <b>bucle de la partida</b> le
     * pide directamente, que son las que al reventar lo matan: declarar
     * atacantes, declarar bloqueadoras y elegir que jugar. Envolver los ciento
     * y pico metodos restantes seria mucha superficie para tapar fallos que no
     * cuelgan nada.
     */
    private static final class Guarded extends PlayerControllerAi {

        private boolean crashedOnce;

        /** Fallos de ESTA IA en esta partida. */
        private int fails;

        Guarded(final Game game, final Player p, final LobbyPlayer lp) {
            super(game, p, lp);
        }

        @Override
        public void declareAttackers(final Player attacker, final Combat combat) {
            try {
                // El inyector pincha tambien aqui, y no solo en los bloqueos:
                // declarar atacantes pasa TODOS los turnos, mientras que
                // bloquear solo si alguien te ataca. Con una prueba que
                // depende de que te ataquen, unas veces se ejecuta y otras no.
                boom();
                super.declareAttackers(attacker, combat);
            } catch (final RuntimeException | StackOverflowError e) {
                // Sin atacantes: esta IA se queda en casa este turno.
                if (!GUARD) {
                    throw e;
                }
                report(attacker, "declarar atacantes", e);
            }
        }

        @Override
        public void declareBlockers(final Player defender, final Combat combat) {
            try {
                boom();
                super.declareBlockers(defender, combat);
            } catch (final RuntimeException | StackOverflowError e) {
                // Los bloqueos que ya hubiera puesto valen: cada uno se valido
                // al ponerse. Lo que no llegara a decidir, no bloquea.
                if (!GUARD) {
                    throw e;
                }
                report(defender, "declarar bloqueadoras", e);
            }
        }

        @Override
        public List<SpellAbility> chooseSpellAbilityToPlay() {
            try {
                return super.chooseSpellAbilityToPlay();
            } catch (final RuntimeException | StackOverflowError e) {
                // null es "no juego nada", que es justo lo que devuelve la IA
                // cuando no encuentra jugada. O sea: pasa.
                if (!GUARD) {
                    throw e;
                }
                report(getPlayer(), "elegir que jugar", e);
                return null;
            }
        }

        /** Solo con -Dneo.ai.crashTest=true, y una sola vez por IA. */
        private void boom() {
            if (CRASH_ALWAYS || (CRASH_ONCE && !crashedOnce)) {
                crashedOnce = true;
                throw new IllegalStateException("fallo de IA de mentira (prueba)");
            }
        }

        /**
         * Anota el fallo y, si esa IA ya no levanta cabeza, la retira.
         *
         * <p>Retirarla es <b>concederla</b> ({@code Player.concede()}, que es
         * publica y solo apunta el resultado: el motor la saca en la siguiente
         * comprobacion de estado). En una partida a cuatro eso deja a los otros
         * tres jugando, que es de lo que se trataba. En una a dos termina la
         * partida — pero es que a dos ya se habia terminado.
         */
        private void report(final Player who, final String what, final Throwable e) {
            RESCUES.incrementAndGet();
            fails++;
            final String name = who == null ? "una IA" : who.getName();
            System.out.printf(Locale.ROOT,
                    "  [ia] %s ha fallado al %s (%s). Se salta esa decision (%d de %d).%n",
                    name, what, e, fails, MAX_FAILS);
            if (fails < MAX_FAILS || who == null || who.getOutcome() != null) {
                return;
            }
            System.out.printf(Locale.ROOT,
                    "  [ia] %s ha fallado %d veces: se retira de la partida"
                            + " para que los demas puedan seguir.%n", name, fails);
            try {
                who.concede();
                CONCEDES.incrementAndGet();
            } catch (final RuntimeException ex) {
                System.out.printf(Locale.ROOT, "  [ia] y encima no se ha podido retirar: %s%n", ex);
            }
        }
    }
}
