package forge.neo.match;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.gamemodes.puzzle.Puzzle;
import forge.gamemodes.puzzle.PuzzleIO;
import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoSettings;
import forge.neo.platform.NeoGuiBase;
import forge.player.GamePlayerUtil;

/**
 * Lanza una partida de Commander conducida por {@link NeoMatchUI}.
 *
 * <p>Es el equivalente minimo de lo que hace la GUI Swing al pulsar "Start
 * Game", pero sin una sola linea de Swing. Cuando llegue la fase 5 esto se
 * llamara desde la pantalla de inicio en vez de desde la linea de comandos.
 */
public final class NeoGame {

    private NeoGame() {
    }

    /** Resultado de una partida, para poder validar desde fuera. */
    public static final class Result {
        public final boolean completed;
        public final int turns;
        public final int decisions;
        public final String winner;
        /** Que pidio el jugador al salir: volver al menu o jugar otra. */
        public final NeoMatchUI.Exit exit;
        /**
         * Con cuanta vida termino el jugador, o -1 si no se pudo leer.
         *
         * <p>Lo pide Ascenso, donde la vida <b>se arrastra al nodo siguiente</b>
         * y por tanto es el resultado que de verdad importa: ganar con 3 vidas
         * y ganar con 30 son dos partidas distintas. Al resto de modos les da
         * igual y por eso nadie lo miraba.
         */
        public final int yourLife;

        Result(final boolean completed, final int turns, final int decisions, final String winner,
               final NeoMatchUI.Exit exit, final int yourLife) {
            this.completed = completed;
            this.turns = turns;
            this.decisions = decisions;
            this.winner = winner;
            this.exit = exit;
            this.yourLife = yourLife;
        }
    }

    /**
     * @param deck        mazo a usar para todos los asientos
     * @param opponents   numero de oponentes IA
     * @param mode        OBSERVE (todo IA) o AUTO_PLAY (humano automatico)
     * @param timeoutSecs tope de tiempo antes de dar la partida por colgada
     * @param verbose     traza detallada de fases y eventos
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose) {
        return play(deck, opponents, mode, timeoutSecs, verbose, null);
    }

    /**
     * Igual, pero pintando la partida en una mesa.
     *
     * @param binder puente con la interfaz, o {@code null} para solo consola
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder) {
        return play(deck, opponents, mode, timeoutSecs, verbose, binder, null);
    }

    /**
     * Igual, eligiendo ademas el caracter de la IA.
     *
     * @param aiProfile uno de los perfiles de {@code forge-gui/res/ai/}
     *                  (Cautious, Default, Reckless, Experimental), o
     *                  {@code null} para el que tenga configurado Forge
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder, final String aiProfile) {
        return play(deck, opponents, mode, timeoutSecs, verbose, binder, aiProfile, true);
    }

    /**
     * @param autoPayMana pagar el mana solo al lanzar, en vez de clicar tierras
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder, final String aiProfile,
                              final boolean autoPayMana) {
        return play(deck, opponents, mode, timeoutSecs, verbose, binder, aiProfile,
                autoPayMana, NeoFormat.COMMANDER);
    }

    /**
     * Igual, en el formato que se pida.
     *
     * <p>Es lo unico que hace falta para jugar Estandar, Brawl u Oathbreaker en
     * vez de Commander: el {@link GameType} que va en las reglas y como se
     * registra el asiento. Todo lo demas — la mesa, la interaccion, el combate —
     * ya vale igual, porque nada de eso sabe a que estas jugando.
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder, final String aiProfile,
                              final boolean autoPayMana, final NeoFormat format) {
        return play(deck, opponents, mode, timeoutSecs, verbose, binder, aiProfile,
                autoPayMana, format, null);
    }

    /**
     * Igual, pero cada rival con SU mazo.
     *
     * <p>Sin esto los cuatro asientos jugaban el mismo mazo, que ademas es el
     * tuyo: una partida a cuatro contra tres copias de ti mismo. Con los
     * preconstruidos de Forge disponibles, darle a cada rival algo distinto es
     * la diferencia entre una prueba y una partida.
     *
     * @param opponentDecks un mazo por rival; los huecos a null — y la lista
     *                      entera a null — se rellenan con el mazo del humano
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder, final String aiProfile,
                              final boolean autoPayMana, final NeoFormat format,
                              final List<Deck> opponentDecks) {
        return play(deck, opponents, mode, timeoutSecs, verbose, binder, aiProfile,
                autoPayMana, format, opponentDecks, 1);
    }

    /**
     * Igual, pero al mejor de N partidas en vez de una sola.
     *
     * <p>Solo tiene sentido en draft y sellado (la auditoría del motor, apartado C5): son los
     * unicos formatos con banquillo de verdad
     * ({@code GameType.isSideboardingAllowed()}) y con un rival fijo al que
     * enfrentarse mas de una vez seguida. En Commander {@code gamesPerMatch}
     * se queda en 1 en TODOS los caminos que no llaman a este overload — nada
     * cambia para quien no juega un evento de limitado.
     *
     * @param gamesPerMatch 1 para una sola partida (lo de siempre), 3 para
     *                      Bo3. El motor decide el ganador del partido el
     *                      solo ({@code Match.isMatchOver}, dos de tres) y
     *                      pide el banquillo el solo entre partida y partida
     *                      ({@code NeoMatchUI.sideboard}).
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder, final String aiProfile,
                              final boolean autoPayMana, final NeoFormat format,
                              final List<Deck> opponentDecks, final int gamesPerMatch) {
        return play(deck, opponents, mode, timeoutSecs, verbose, binder, aiProfile,
                autoPayMana, format, opponentDecks, gamesPerMatch, null);
    }

    /**
     * Quien monta los asientos cuando el formato no basta para decirlo todo.
     *
     * <p>Existe por Ascenso, y no se pudo resolver retocando el asiento despues
     * de montarlo: los <b>esquemas</b> del archienemigo solo entran por
     * {@code RegisteredPlayer.forVariants} — no hay setter — y la <b>vida</b>
     * hay que ponerla justo despues, porque {@code forVariants} la pisa. O sea
     * que el asiento hay que construirlo entero, no ajustarlo.
     *
     * <p>Lo que NO se saca por aqui es todo lo demas que hace este metodo: las
     * preferencias del motor, el blindaje de la IA, la funda del mazo, la mesa
     * y el menu de pausa. Ese fue el motivo de poner una costura en vez de que
     * el modo montara su propio {@code HostedMatch}: un tercer camino que
     * arranca partidas es exactamente lo que produjo el fallo del principio 8
     * (las notas de diseño), con el duelo de la aventura jugandose con otros ajustes.
     */
    public interface Seating {
        /** Las variantes de la partida. Van a las reglas Y a {@code forVariants}. */
        EnumSet<GameType> variants();

        /** El asiento del humano, con su mazo. */
        RegisteredPlayer human(Deck deck, int seats);

        /** El asiento del rival numero {@code i}, con el mazo que le toque. */
        RegisteredPlayer opponent(int i, Deck deck, int seats);
    }

    /**
     * <b>Solo para medir</b>: {@code -Dneo.ai.relics=id1,id2} le pone esas
     * reliquias de Ascenso a cada IA.
     *
     * <h2>Para que existe</h2>
     *
     * <p>Reportado jugando (05-09-2026): <i>"la pelea contra el boss se
     * lagueaba"</i>, y la sospecha del jugador era que fuera por las reliquias
     * del jefe — una que le daba mana cada turno y otra que le hacia robar. Es
     * una hipotesis <b>medible</b>, pero para medirla hay que poder reproducir
     * las condiciones del jefe sin llegar a un jefe: una run entera por
     * medicion no es una medicion, es una tarde.
     *
     * <p>Con esto, {@code run.cmd ui --live --watch -Dneo.ai.relics=...} juega
     * lo mismo que un nodo de jefe en lo que importa. Sin la bandera no hace
     * absolutamente nada, que es lo correcto fuera de una medicion.
     */
    private static void testRelics(final RegisteredPlayer ai) {
        final String ids = System.getProperty("neo.ai.relics");
        if (ids == null || ids.isBlank()) {
            return;
        }
        forge.neo.ascent.AscentRelics.install();
        final List<forge.item.IPaperCard> cards = new ArrayList<>();
        for (final String id : ids.split(",")) {
            final forge.neo.ascent.AscentRelic relic =
                    forge.neo.ascent.AscentRelics.byId(id.trim());
            final forge.item.PaperCard card = relic == null ? null
                    : forge.neo.ascent.AscentRelics.cardOf(relic);
            if (card != null) {
                cards.add(card);
            } else {
                System.out.println("[medicion] no hay reliquia con id '" + id.trim() + "'");
            }
        }
        if (!cards.isEmpty()) {
            System.out.println("[medicion] la IA juega con " + cards.size()
                    + " reliquia(s): " + cards);
            ai.addExtraCardsInCommandZone(cards);
        }
    }

    /**
     * Igual, pero con los asientos puestos por el modo.
     *
     * @param seating quien monta los asientos, o {@code null} para que los
     *                monte el formato (lo de siempre)
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder, final String aiProfile,
                              final boolean autoPayMana, final NeoFormat format,
                              final List<Deck> opponentDecks, final int gamesPerMatch,
                              final Seating seating) {
        return play(deck, opponents, mode, timeoutSecs, verbose, binder, aiProfile,
                autoPayMana, format, opponentDecks, gamesPerMatch, seating,
                NeoMatchUI.Ending.NORMAL);
    }

    /**
     * Igual, diciendo ademas <b>a donde se vuelve al acabar</b>.
     *
     * <p>Lo pide Ascenso, y no es un adorno: la pantalla de fin de partida es
     * lo que cierra el match, y sus botones tienen que decir la verdad sobre el
     * modo que la lanzo. Con los de una partida suelta, "otra partida" en un
     * nodo de Ascenso se leia como abandonar el duelo a medias y <b>se llevaba
     * la run por delante</b>.
     *
     * <p>Va por aqui —y no en cada sitio que arranca una partida— por el
     * principio 8 (las notas de diseño): la {@code NeoMatchUI} se construye <b>dentro
     * de este metodo</b>, asi que este es el unico punto por el que pasan todos
     * los modos que juegan por esta puerta.
     *
     * @param ending a donde vuelve el jugador al acabar
     */
    public static Result play(final Deck deck, final int opponents, final NeoMatchUI.Mode mode,
                              final int timeoutSecs, final boolean verbose,
                              final TableBinder binder, final String aiProfile,
                              final boolean autoPayMana, final NeoFormat format,
                              final List<Deck> opponentDecks, final int gamesPerMatch,
                              final Seating seating, final NeoMatchUI.Ending ending) {

        if (mode == NeoMatchUI.Mode.HUMAN) {
            applyEnginePrefs();
        } else if (mode == NeoMatchUI.Mode.OBSERVE && binder == null) {
            // Simulacion de fondo sin mesa (el torneo, la auditoría del motor, apartado C6,
            // resolviendo emparejamientos en los que nadie mira). Aqui hay
            // DOS IA pensando de verdad -- a diferencia de AUTO_PLAY, que
            // solo tiene una y responde la otra con un guion casi
            // instantaneo -- y sin esto heredaban el MATCH_AI_TIMEOUT que
            // hubiera dejado puesto la ULTIMA partida jugada por un humano
            // (hasta 20s por decision, ver SettingsPanel): una partida
            // entera se iba a varios minutos reales de pantalla en negro.
            // Se acota aqui, solo en memoria (nunca se guarda) -- la
            // siguiente partida HUMANA lo restaura sola, porque
            // applyEnginePrefs() se llama en cada una de esas.
            FModel.getPreferences().setPref(FPref.MATCH_AI_TIMEOUT, "2");
        }

        final NeoMatchUI gui = new NeoMatchUI(mode, verbose);
        gui.setAutoPayMana(autoPayMana);
        gui.setEnding(ending);
        if (binder != null) {
            gui.setBinder(binder);
            gui.setTable(binder.getTable());
            // El menu de pausa necesita poder decirle "sal de aqui".
            binder.setMatchUi(gui);
        }
        final HostedMatch match = new HostedMatch();
        final CountDownLatch over = new CountDownLatch(1);

        final List<RegisteredPlayer> players = new ArrayList<>();
        RegisteredPlayer humanSeat = null;

        final int seats = opponents + 1;
        // Con que mazo se juega, para que la mesa pinte SU funda. Va antes de
        // montar el asiento: NeoPlayers.human() ya lo pregunta, porque el
        // indice de funda que recibe el motor tiene que decir lo mismo.
        // Observando no juega nadie: ahi no hay mazo tuyo que valga.
        forge.neo.look.NeoLook.setDeckInPlay(
                mode == NeoMatchUI.Mode.OBSERVE ? null : deck);
        if (mode != NeoMatchUI.Mode.OBSERVE) {
            humanSeat = seating == null ? format.register(deck, seats)
                    : seating.human(deck, seats);
            humanSeat.setPlayer(forge.neo.look.NeoPlayers.human());
            players.add(humanSeat);
        }

        final int aiCount = mode == NeoMatchUI.Mode.OBSERVE ? opponents + 1 : opponents;
        for (int i = 0; i < aiCount; i++) {
            final Deck aiDeck = deckForOpponent(opponentDecks, i, deck);
            final RegisteredPlayer ai = seating == null ? format.register(aiDeck, seats)
                    : seating.opponent(i, aiDeck, seats);
            // El perfil vacio significa "el que tenga puesto Forge": es lo que
            // hace la sobrecarga corta, asi que no hay que tratar el null aparte.
            ai.setPlayer(forge.neo.look.NeoPlayers.ai(i, aiProfile));
            testRelics(ai);
            players.add(ai);
        }

        System.out.printf(Locale.ROOT, "  Formato: %s | modo: %s | asientos: %d | mazo: %s | IA: %s%n",
                format, mode, players.size(), deck.getName(),
                aiProfile == null || aiProfile.isBlank() ? "(por defecto)" : aiProfile);
        for (int i = 0; i < aiCount; i++) {
            System.out.printf(Locale.ROOT, "    %s juega: %s%n",
                    forge.neo.look.NeoPlayers.aiName(i),
                    deckForOpponent(opponentDecks, i, deck).getName());
        }

        match.setOnMatchOver(over::countDown);

        // El blindaje de la IA va SIEMPRE, no solo cuando hay algo mas que
        // hacer al empezar. Aqui el match se construye a mano (no sale de
        // IGuiBase.hostMatch), asi que el gancho hay que ponerlo tambien aqui:
        // ese fue el fallo de la primera version, que blindaba las partidas del
        // lobby y de la aventura y dejaba fuera las normales. Ver SafeAi.
        final Runnable rig = devExileCommander ? () -> exileCommander(match.getGame())
                : devPlayFromGraveyard != null ? () -> putInGraveyard(match.getGame())
                : devSpeed > 0 ? () -> riggedSpeed(match.getGame())
                : devFilterLand ? () -> riggedFilterLand(match.getGame())
                : devOppRelics > 0 ? () -> riggedOppRelics(match.getGame())
                : devRig != null ? () -> riggedCards(match.getGame()) : null;
        match.setStartGameHook(SafeAi.hook(match, rig));

        // Normalmente una sola partida (gamesPerMatch=1): para validar nos
        // sobra y tarda un tercio, y en Commander no hay banquillo con el que
        // jugar un Bo3. Solo el evento de draft/sellado pide 3 (la auditoría del motor
        // C5). (getDefaultRules de HostedMatch es privado, asi que
        // construimos las reglas aqui.)
        final GameRules rules = new GameRules(format.getGameType());
        rules.setGamesPerMatch(gamesPerMatch);

        // ---- ESTO ES LO QUE HACE QUE COMMANDER SEA COMMANDER ----
        //
        // El GameType del constructor NO basta: media docena de reglas del
        // motor preguntan por game.getRules().hasAppliedVariant(...), que lee
        // un conjunto APARTE y que empieza vacio. Sin rellenarlo se pierden,
        // entre otras:
        //
        //   - CR 903.9a: "tu comandante esta en el cementerio o en el exilio,
        //     lo devuelves a la zona de mando?" (GameAction, SBA). Sin esto no
        //     te lo pregunta NUNCA y el comandante se queda fuera para siempre.
        //   - Perder por 21 de dano de comandante (Player.checkLoseCondition).
        //   - Las excepciones de Brawl, Oathbreaker y Tiny Leaders al dano de
        //     comandante y a que zonas cubre el reemplazo del mando.
        //
        // Lo pone el lobby de Forge al montar la partida (HostedMatch lo recibe
        // como parametro) y tambien su runner sin interfaz, SimulateMatch, con
        // exactamente esta linea. Nosotros pasabamos null.
        // Con Seating puesta, las variantes las decide el modo: Ascenso mete
        // ademas Archenemy en el nodo de jefe, y sin eso en las reglas el motor
        // no reparte esquemas por mucho que el asiento los lleve.
        rules.setAppliedVariants(seating == null
                ? EnumSet.of(format.getGameType()) : seating.variants());

        // ---- preferencias que SOLO se aplican por GameRules (la auditoría del motor, apartado B4) ----
        //
        // HostedMatch.getDefaultRules(GameType) las lee de FModel.getPreferences()
        // — pero es privado y solo lo usa el overload de startMatch(GameType,...),
        // que nosotros no llamamos: construimos nuestras propias GameRules y
        // llamamos a startMatch(GameRules,...), que NO pasa por ahi. O sea que
        // sin estas dos lineas, encender el ante o el "cheat shuffle" de la IA
        // en Ajustes no habria hecho NADA — la preferencia se habria escrito en
        // FModel.getPreferences() y nadie la habria vuelto a mirar.
        rules.setAllowCheatShuffle(NeoSettings.getBool(NeoSettings.AI_CHEAT_SHUFFLE, false));
        rules.setPlayForAnte(NeoSettings.getBool(NeoSettings.ANTE, false));
        rules.setMatchAnteRarity(NeoSettings.getBool(NeoSettings.ANTE_MATCH_RARITY, false));
        rules.setAnteIncludeBasicLands(NeoSettings.getBool(NeoSettings.ANTE_INCLUDE_BASIC_LANDS, false));

        if (mode == NeoMatchUI.Mode.OBSERVE) {
            // Sin jugadores humanos, HostedMatch.startGame() registra el
            // espectador el solo pidiendo una GUI a IGuiBase. Solo hay que
            // decirle cual devolver.
            NeoGuiBase.setGuiGameFactory(() -> gui);
            // Casts necesarios: hay dos sobrecargas de startMatch que encajan con null.
            match.startMatch(rules, null, players, (RegisteredPlayer) null, (IGuiGame) null);
        } else {
            match.startMatch(rules, null, players, humanSeat, gui);
        }

        boolean completed = false;
        try {
            completed = over.await(timeoutSecs, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final int turns = gui.getGameView() == null ? 0 : gui.getGameView().getTurn();
        final String winner = gui.getGameView() == null ? null : gui.getGameView().getWinningPlayerName();

        if (!completed) {
            System.out.printf(Locale.ROOT,
                    "%n  TIMEOUT: la partida no ha terminado en %ds (turno %d).%n",
                    timeoutSecs, turns);
        }

        final NeoMatchUI.Exit exit = gui.getExitAction();
        // La vida ANTES de shutdown(): despues la vista ya no tiene por que
        // seguir en pie, y es el dato del que vive una run de Ascenso.
        final forge.game.player.PlayerView me = gui.localPlayerView();
        final int yourLife = me == null ? -1 : me.getLife();
        gui.shutdown();
        // Se acabo el mazo en juego: si se dejara puesto, el siguiente puzzle o
        // el siguiente duelo de la aventura heredarian su funda.
        forge.neo.look.NeoLook.setDeckInPlay(null);
        return new Result(completed, turns, gui.getDecisionCount(), winner, exit, yourLife);
    }

    /**
     * Juega un puzzle.
     *
     * <p>Un puzzle no tiene mazos: son dos asientos vacios y una posicion que
     * el propio {@code Puzzle} aplica al empezar. Forge lo trae hecho — nosotros
     * solo enganchamos el {@code startGameHook} donde toca y le pasamos la mesa.
     */
    /** El mazo del rival numero {@code i}, o el del humano si no se eligio. */
    private static Deck deckForOpponent(final List<Deck> decks, final int i, final Deck fallback) {
        if (decks == null || i >= decks.size() || decks.get(i) == null) {
            return fallback;
        }
        return decks.get(i);
    }

    public static Result playPuzzle(final Puzzle puzzle, final int timeoutSecs,
                                    final boolean verbose, final TableBinder binder,
                                    final boolean autoPayMana) {
        applyEnginePrefs();

        final NeoMatchUI gui = new NeoMatchUI(NeoMatchUI.Mode.HUMAN, verbose);
        gui.setAutoPayMana(autoPayMana);
        if (binder != null) {
            gui.setBinder(binder);
            gui.setTable(binder.getTable());
            binder.setMatchUi(gui);
        }

        final HostedMatch match = new HostedMatch();
        final CountDownLatch over = new CountDownLatch(1);
        match.setOnMatchOver(over::countDown);

        // La posicion del puzzle se aplica cuando la partida ya existe.
        match.setStartGameHook(SafeAi.hook(match, () -> puzzle.applyToGame(match.getGame())));
        match.setEndGameHook(() -> puzzle.savePuzzleSolve(
                match.getGame() != null && match.getGame().getOutcome() != null
                        && match.getGame().getOutcome().isWinner(GamePlayerUtil.getGuiPlayer())));

        final List<RegisteredPlayer> players = new ArrayList<>();
        final RegisteredPlayer human = new RegisteredPlayer(new Deck())
                .setPlayer(GamePlayerUtil.getGuiPlayer());
        human.setStartingHand(0);
        players.add(human);

        final RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                .setPlayer(GamePlayerUtil.createAiPlayer());
        ai.setStartingHand(0);
        players.add(ai);

        System.out.printf(Locale.ROOT, "  Puzzle: %s%n", puzzle.getName());

        final GameRules rules = new GameRules(GameType.Puzzle);
        rules.setGamesPerMatch(1);
        match.startMatch(rules, null, players, human, gui);

        boolean completed = false;
        try {
            completed = over.await(timeoutSecs, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final int turns = gui.getGameView() == null ? 0 : gui.getGameView().getTurn();
        final NeoMatchUI.Exit exit = gui.getExitAction();
        gui.shutdown();
        return new Result(completed, turns, gui.getDecisionCount(), null, exit, -1);
    }

    /**
     * Juega una leccion del tutorial.
     *
     * <p>Es un puzzle sin objetivo: dos asientos vacios, la posicion la aplica
     * {@link forge.neo.tutorial.TutorialState} y el {@code GameType.Puzzle} es
     * lo que hace que no haya mulligan y que empieces tu — las dos cosas que
     * un tutorial necesita y que no se pueden pedir de otra forma
     * ({@code GameAction.startGame}: {@code if (gameType != Puzzle) new
     * MulliganService(...)} y {@code determineFirstTurnPlayer}, que devuelve el
     * asiento 0 en Puzzle).
     *
     * <p>Lo que NO se hereda del modo puzzle es la carta de objetivo: aqui no
     * hay nada que cumplir, y una carta en blanco en la zona de mando es justo
     * lo que el tutorial esta intentando explicar que no es normal.
     *
     * @return el resultado, con {@code exit} diciendo por donde se salio
     */
    public static Result playTutorial(final forge.neo.tutorial.TutorialLesson lesson,
                                      final int timeoutSecs, final TableBinder binder,
                                      final boolean autoPayMana,
                                      final java.util.function.Consumer<NeoMatchUI> onReady) {
        return playTutorial(lesson, new forge.neo.tutorial.TutorialState(lesson.getState()),
                NeoMatchUI.Mode.HUMAN, timeoutSecs, binder, autoPayMana, onReady);
    }

    /**
     * Igual, pero eligiendo la posicion y quien juega.
     *
     * <p>Existe por el comprobador sin ventana, que necesita las dos cosas: la
     * posicion, para poder preguntarle despues como quedo la mesa de verdad
     * ({@code TutorialState.getSummary()}), y el modo automatico, porque sin
     * ventana no hay nadie que conteste.
     */
    public static Result playTutorial(final forge.neo.tutorial.TutorialLesson lesson,
                                      final forge.neo.tutorial.TutorialState position,
                                      final NeoMatchUI.Mode mode,
                                      final int timeoutSecs, final TableBinder binder,
                                      final boolean autoPayMana,
                                      final java.util.function.Consumer<NeoMatchUI> onReady) {
        applyEnginePrefs();

        final NeoMatchUI gui = new NeoMatchUI(mode, false);
        gui.setAutoPayMana(autoPayMana);
        if (binder != null) {
            gui.setBinder(binder);
            gui.setTable(binder.getTable());
            binder.setMatchUi(gui);
        }
        // La leccion se engancha a la partida AQUI y no esperando a que
        // aparezca: es el unico momento en que ya existe el NeoMatchUI y
        // todavia no ha empezado a llegar nada del motor.
        if (onReady != null) {
            onReady.accept(gui);
        }

        final HostedMatch match = new HostedMatch();
        final CountDownLatch over = new CountDownLatch(1);
        match.setOnMatchOver(over::countDown);

        match.setStartGameHook(SafeAi.hook(match, () -> position.applyToGame(match.getGame())));

        final List<RegisteredPlayer> players = new ArrayList<>();
        final RegisteredPlayer human = new RegisteredPlayer(new Deck())
                .setPlayer(forge.neo.look.NeoPlayers.human());
        human.setStartingHand(0);
        players.add(human);

        final RegisteredPlayer ai = new RegisteredPlayer(new Deck())
                .setPlayer(forge.neo.look.NeoPlayers.ai(0, null));
        ai.setStartingHand(0);
        players.add(ai);

        System.out.printf(Locale.ROOT, "  Tutorial: %s (%d pasos)%n",
                lesson.getId(), lesson.getSteps().size());

        final GameRules rules = new GameRules(GameType.Puzzle);
        rules.setGamesPerMatch(1);
        match.startMatch(rules, null, players, human, gui);

        boolean completed = false;
        try {
            completed = over.await(timeoutSecs, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        final int turns = gui.getGameView() == null ? 0 : gui.getGameView().getTurn();
        final NeoMatchUI.Exit exit = gui.getExitAction();
        gui.shutdown();
        return new Result(completed, turns, gui.getDecisionCount(), null, exit, -1);
    }

    /** Los puzzles que trae Forge, ordenados. */
    public static List<Puzzle> puzzles() {
        final List<Puzzle> out = new ArrayList<>(
                PuzzleIO.loadPuzzles(ForgeConstants.PUZZLE_DIR));
        java.util.Collections.sort(out);
        return out;
    }

    /**
     * Solo para PROBAR: manda tu comandante al exilio nada mas empezar.
     *
     * <p>La pregunta de la regla 903.9a ("tu comandante esta en el exilio, lo
     * devuelves a la zona de mando?") solo aparece cuando el comandante sale de
     * la partida, o sea en el turno 20 y con suerte. Sin esto no hay forma de
     * comprobar ese camino: con esto sale en el turno 1.
     *
     * <p>No lo enciende ningun camino normal, solo {@code --exile-commander}.
     */
    private static volatile boolean devExileCommander;

    public static void setDevExileCommander(final boolean on) {
        devExileCommander = on;
    }

    /**
     * Arranca la partida con velocidad puesta. Solo para pruebas
     * ({@code --speed=N}).
     *
     * <p>La velocidad ({@code Start your engines!}) no se puede provocar a
     * voluntad: hace falta una carta que la conceda y luego que el rival
     * pierda vidas en tu turno, o sea varios turnos de partida real. Y lo que
     * hay que comprobar es que {@link forge.neo.match.PlayerSpeed} la
     * <b>encuentra</b>, que es lo unico nuestro. {@code Player.increaseSpeed()}
     * es publico y crea el efecto de la zona de mando por el camino de
     * siempre, asi que la mesa queda exactamente igual que en una partida de
     * verdad.
     *
     * <p>A ti te pone N y a los rivales la maxima, que son las dos ramas
     * distintas (numero y "MAXIMA") y ademas es el caso reportado: jugar
     * contra un mazo de velocidad maxima.
     */
    private static volatile int devSpeed;

    public static void setDevSpeed(final int n) {
        devSpeed = n;
    }

    /**
     * Monta la mesa de la tierra de filtro. Solo para pruebas
     * ({@code --filter-land}).
     *
     * <p>Es la unica forma de probar el <b>bucle entero</b> del apanyo de
     * {@code FilterSources}: sin ventana no se puede, porque en modo
     * automatico cada fase dura milisegundos y no hay hueco en el que lanzar
     * sea legal. Aqui la mesa se deja lista y {@code --autopilot} clica la
     * carta como lo haria el jugador.
     */
    private static volatile boolean devFilterLand;

    public static void setDevFilterLand(final boolean on) {
        devFilterLand = on;
    }

    /**
     * Deja cartas sueltas en TU campo de batalla al empezar. Solo para pruebas
     * ({@code --rig=Nombre;Otro}).
     *
     * <p>Es la forma de provocar el disparo de una carta concreta sin
     * depender de que salga jugando: la de Shadrix Silverquill, por ejemplo,
     * salta al inicio de TU combate y pide dos modos con objetivo.
     */
    private static volatile String devRig;

    public static void setDevRig(final String names) {
        devRig = names;
    }

    /**
     * Cuantas reliquias de Ascenso se le ponen al RIVAL en su zona de mando.
     *
     * <p>Solo para pruebas ({@code --rig-opp-relics=N}), y hace falta porque la
     * unica forma de ver esto jugando es llegar a un jefe de Ascenso: una run a
     * medias y diez minutos. Lo que hay que mirar es que sus reliquias se VEAN
     * — del rival no se ensenya la zona de mando, solo su contador, asi que sin
     * las pastillas de {@code PlayerBar} son invisibles.
     *
     * <p>Va por el camino de verdad (la carta entra en la zona de mando del
     * motor) y no pintando la pastilla a mano: pintarla a mano no probaria
     * nada, porque quien la tiene que descubrir es {@code TableBinder} leyendo
     * el {@code PlayerView} — y ademas el binder la borraria en el siguiente
     * refresco.
     */
    private static volatile int devOppRelics;

    public static void setDevOppRelics(final int howMany) {
        devOppRelics = howMany;
    }

    private static void riggedOppRelics(final forge.game.Game game) {
        if (game == null || devOppRelics <= 0) {
            return;
        }
        forge.neo.ascent.AscentRelics.install();
        for (final forge.game.player.Player p : game.getPlayers()) {
            if (!p.isAI()) {
                continue;
            }
            int left = devOppRelics;
            for (final forge.neo.ascent.AscentRelic relic : forge.neo.ascent.AscentRelics.all()) {
                if (left-- <= 0) {
                    break;
                }
                put(game, p, relic.getCardName(), forge.game.zone.ZoneType.Command);
                System.out.println("  [dev] reliquia del rival: " + relic.getCardName());
            }
            return;
        }
    }

    private static void riggedCards(final forge.game.Game game) {
        if (game == null || devRig == null) {
            return;
        }
        for (final forge.game.player.Player p : game.getPlayers()) {
            if (p.isAI()) {
                continue;
            }
            for (final String name : devRig.split(";")) {
                put(game, p, name.trim(), forge.game.zone.ZoneType.Battlefield);
                System.out.println("  [dev] en tu campo: " + name.trim());
            }
            return;
        }
    }

    private static void riggedFilterLand(final forge.game.Game game) {
        if (game == null) {
            return;
        }
        for (final forge.game.player.Player p : game.getPlayers()) {
            if (p.isAI()) {
                continue;
            }
            // Un Bosque y la Pradera: el Bosque paga el {1} de la Pradera y la
            // Pradera devuelve {G}{W}. Es exactamente la jugada que el
            // planificador del motor no ve.
            put(game, p, "Forest", forge.game.zone.ZoneType.Battlefield);
            put(game, p, "Sungrass Prairie", forge.game.zone.ZoneType.Battlefield);
            put(game, p, "Watchwolf", forge.game.zone.ZoneType.Hand);
            System.out.println("  [dev] mesa de tierra de filtro puesta");
            return;
        }
    }

    private static void put(final forge.game.Game game, final forge.game.player.Player p,
                            final String name, final forge.game.zone.ZoneType zone) {
        final forge.item.PaperCard paper = FModel.getMagicDb().getCommonCards().getCard(name);
        if (paper == null) {
            System.out.println("  [dev] no existe la carta: " + name);
            return;
        }
        final forge.game.card.Card c = forge.game.card.Card.fromPaperCard(paper, p);
        game.getAction().moveTo(zone, c, null, forge.game.ability.AbilityKey.newMap());
        c.setSickness(false);
    }

    private static void riggedSpeed(final forge.game.Game game) {
        if (game == null) {
            return;
        }
        for (final forge.game.player.Player p : game.getPlayers()) {
            final int target = p.isAI() ? 4 : Math.min(devSpeed, 4);
            for (int i = 0; i < target; i++) {
                p.increaseSpeed();
            }
            System.out.println("  [dev] velocidad de " + p.getName() + ": " + p.getSpeed());
        }
    }

    /**
     * Deja una carta jugable DESDE EL CEMENTERIO al empezar. Solo para pruebas.
     *
     * <p>Lo que hay que poder comprobar es lanzar desde una zona que no es la
     * mano: la aventura exiliada que reporto el jugador y el flashback del
     * cementerio son el mismo camino del motor
     * ({@code Player.getCardsActivatableInExternalZones}), pero la aventura no
     * se puede fabricar — hay que lanzar antes la mitad de aventura — y una
     * carta con flashback puesta en el cementerio si. Misma zona de mentira,
     * misma lista, misma respuesta.
     */
    private static volatile String devPlayFromGraveyard;

    public static void setDevPlayFromGraveyard(final String cardName) {
        devPlayFromGraveyard = cardName == null || cardName.isBlank() ? null : cardName.trim();
    }

    private static volatile forge.game.card.Card devGraveCard;

    /**
     * En que zona esta ahora la carta que dejamos en el cementerio.
     *
     * <p>Es la unica forma honesta de comprobar que el click la LANZO: mirar
     * si sigue en el visor no vale — puede haberse abierto otra zona — y el
     * flashback la manda al exilio al resolverse, asi que la zona lo dice sin
     * lugar a dudas.
     */
    public static String devGraveCardZone() {
        final forge.game.card.Card c = devGraveCard;
        // El TIPO de zona, no la zona: Zone.toString() escribe "Graveyard de
        // Ana" y comparar contra eso es comparar contra el nombre del jugador.
        final forge.game.zone.Zone z = c == null ? null : c.getZone();
        return z == null ? null : z.getZoneType().name();
    }

    /**
     * Pone una carta concreta en el cementerio del humano. Solo para pruebas.
     *
     * <p>Va al cementerio DESDE la biblioteca si esta ahi, y si no se crea. Lo
     * que interesa comprobar no es de donde sale, es que el motor la publique
     * en {@code PlayerView.getFlashback()} y que el visor de la zona la deje
     * clicar.
     */
    private static void putInGraveyard(final forge.game.Game game) {
        final String name = devPlayFromGraveyard;
        if (game == null || name == null) {
            return;
        }
        for (final forge.game.player.Player p : game.getPlayers()) {
            if (p.isAI()) {
                continue;
            }
            final forge.item.PaperCard paper = FModel.getMagicDb().getCommonCards().getCard(name);
            if (paper == null) {
                System.out.println("  [dev] no existe la carta: " + name);
                return;
            }
            final forge.game.card.Card c = forge.game.card.Card.fromPaperCard(paper, p);
            game.getAction().moveTo(forge.game.zone.ZoneType.Graveyard, c, null,
                    forge.game.ability.AbilityKey.newMap());
            System.out.println("  [dev] al cementerio: " + c);
            devGraveCard = c;

            // Y tres tierras que dan CUALQUIER color, enderezadas. Sin esto la
            // prueba depende del mazo: el coste de flashback lleva color, y un
            // mazo sin ese color no puede pagarlo nunca — la carta se veria
            // marcada, el click llegaria al motor y no pasaria nada, que es
            // indistinguible de que el arreglo no funcione.
            final forge.item.PaperCard land =
                    FModel.getMagicDb().getCommonCards().getCard("City of Brass");
            for (int i = 0; land != null && i < 3; i++) {
                final forge.game.card.Card l = forge.game.card.Card.fromPaperCard(land, p);
                game.getAction().moveTo(forge.game.zone.ZoneType.Battlefield, l, null,
                        forge.game.ability.AbilityKey.newMap());
                l.setSickness(false);
            }
            System.out.println("  [dev] tres City of Brass en tu campo");

            p.updateFlashbackForView();
            return;
        }
    }

    /** Manda al exilio el comandante del jugador humano. Solo para pruebas. */
    private static void exileCommander(final forge.game.Game game) {
        if (game == null) {
            return;
        }
        for (final forge.game.player.Player p : game.getPlayers()) {
            if (p.isAI()) {
                continue;
            }
            for (final forge.game.card.Card c : new ArrayList<>(p.getCommanders())) {
                System.out.println("  [dev] exiliando tu comandante: " + c);
                game.getAction().moveTo(forge.game.zone.ZoneType.Exile, c, null,
                        forge.game.ability.AbilityKey.newMap());
            }
        }
    }

    /**
     * Enciende el auto-pass del motor, que viene apagado de serie.
     *
     * <p>Con {@code YIELD_AUTO_PASS_NO_ACTIONS} el motor calcula por su cuenta
     * si puedes hacer algo ({@code AvailableActions}) y solo te da la prioridad
     * cuando la respuesta es que si. Es lo que hace que el turno del rival no
     * sea una sucesion de "OK".
     *
     * <p>Con {@code UI_SHOW_ACTIONABLE_HIGHLIGHTS} ese calculo devuelve ademas
     * QUE cartas son accionables, que es lo que permite resaltarlas.
     *
     * <p>IMPORTANTE: se cambian SOLO EN MEMORIA, sin guardar. El fichero de
     * preferencias se comparte con la instalacion normal de Forge y no vamos a
     * cambiarle los ajustes al usuario por detras.
     */
    public static void applyEnginePrefs() {
        final ForgePreferences prefs = FModel.getPreferences();
        prefs.setPref(FPref.YIELD_AUTO_PASS_NO_ACTIONS, true);
        prefs.setPref(FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS, true);
        // Y que ademas nos diga CUALES taparia el boton "Auto": el motor las
        // manda por el mismo camino, con el peso a 2. Sin esta preferencia
        // manda la lista sin pesar y el plan no se puede ensenyar.
        prefs.setPref(FPref.UI_SHOW_AUTOTAP_PREVIEW, true);
        // Aviso antes de perder mana flotante al pasar prioridad. Sin esto,
        // clicar una fuente de mana por error (un Sol Ring, por ejemplo) se
        // come el mana en silencio al cambiar de fase.
        prefs.setPref(FPref.UI_MANA_LOST_PROMPT, true);

        // ---- elegir en zonas ocultas: SIEMPRE por dialogo ----
        //
        // Con esta preferencia en true (que es como viene), al buscar en tu
        // biblioteca el motor espera que la GUI tenga la biblioteca PINTADA como
        // un panel y que el jugador clique una carta ahi. La GUI Swing tiene
        // esos paneles; nuestra mesa no, asi que la eleccion era imposible: el
        // boton OK no se activaba nunca y solo quedaba cancelar.
        //
        // En false, todo lo que no sea mano o campo de batalla se pide con
        // chooseSingleEntityForEffect, o sea con nuestro dialogo de cartas, que
        // es ademas como lo ensenya Arena: te abre las cartas y eliges.
        // Ver PlayerControllerHuman.useSelectCardsInput.
        prefs.setPref(FPref.UI_SELECT_FROM_CARD_DISPLAYS, false);

        // ---- regla de mulligan (la auditoría del motor, apartado B4) ----
        //
        // No es una preferencia de Forge: MulliganService la lee de
        // StaticData.instance(), no de FModel.getPreferences(). Aplicarla aqui
        // (y no solo al arrancar) es lo que hace que cambiarla en Ajustes se
        // note en la SIGUIENTE partida sin reiniciar el programa.
        forge.StaticData.instance().setMulliganRule(forge.MulliganDefs.GetRuleByName(
                NeoSettings.get(NeoSettings.MULLIGAN_RULE, NeoSettings.MULLIGAN_RULE_DEFAULT)));

        // ---- tope de tiempo de la IA para el combate (la auditoría del motor, apartado B4) ----
        //
        // Esta SI es una preferencia de Forge de verdad: HostedMatch.startGame()
        // la lee sola de FModel.getPreferences() en cada partida
        // (game.AI_TIMEOUT = ...), asi que solo hace falta escribirla aqui.
        prefs.setPref(FPref.MATCH_AI_TIMEOUT,
                String.valueOf(NeoSettings.getInt(NeoSettings.AI_TIMEOUT, NeoSettings.AI_TIMEOUT_DEFAULT)));

        applySmartPass(prefs);
        // Nada de prefs.save(): el cambio muere con el proceso.
    }

    /**
     * Que el pase automatico se pare cuando pasa algo que te importa.
     *
     * <p><b>Esto no lo programamos nosotros.</b> Forge trae el sistema entero
     * en {@code forge.gamemodes.match.YieldController}, y quien dispara las
     * interrupciones es su {@code FControlGameEventHandler}, que
     * {@code HostedMatch} ya suscribe al bus de cada partida. O sea que aqui
     * solo se encienden preferencias: cero logica nuestra.
     *
     * <p>Lo que faltaba era el <b>interruptor maestro</b>. Nosotros ya
     * poniamos {@code YIELD_AUTO_PASS_NO_ACTIONS} ("pasa sola si no tengo nada
     * que hacer") pero {@code YIELD_AUTO_PASS_RESPECTS_INTERRUPTS} se quedaba
     * en false, y sin el las seis {@code YIELD_INTERRUPT_ON_*} no hacen
     * absolutamente nada — {@code YieldController.applyInterrupt} comprueba
     * las dos antes de tocar el pase. Con las dos puestas, el rival te ataca o
     * te lanza algo y la partida SE PARA para que lo veas.
     *
     * <p>⚠️ Se escriben <b>siempre las seis</b>, tambien al apagar, y no solo
     * las que toca encender: si solo escribieramos las del nivel elegido,
     * bajar de "todo" a "poco" dejaria encendidas las de antes y el ajuste
     * mentiria. Un control que no hace lo que parece es peor que no tenerlo
     * (principio 1 de las notas de diseño).
     *
     * <p>⚠️ Y <b>apagado NO es "las seis a false"</b>: es dejarlas <b>como
     * vienen de fabrica</b>, que en Forge son {@code ATTACKERS} y
     * {@code OPPONENT_SPELL} en <b>true</b> y las otras cuatro en false.
     * Parece lo mismo y no lo es — {@code YieldController.applyInterrupt}
     * limpia un pase activo <i>aunque el interruptor maestro este apagado</i>,
     * asi que forzarlas a false cambiaba el comportamiento de quien no ha
     * tocado nada. Apagado tiene que ser exactamente lo de antes de que esto
     * existiera, o no es un interruptor: es otra cosa que hay que probar.
     */
    private static void applySmartPass(final ForgePreferences prefs) {
        final boolean on = NeoSettings.getBool(NeoSettings.SMART_PASS, false);
        final int level = NeoSettings.smartPassLevel();

        prefs.setPref(FPref.YIELD_AUTO_PASS_RESPECTS_INTERRUPTS, on);

        // Lo de siempre: te atacan, o el rival lanza algo. Son las dos que
        // Forge trae encendidas, asi que apagado se quedan como estaban.
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_ATTACKERS, true);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_OPPONENT_SPELL, true);
        // Nivel 1: y ademas lo que te apunta a ti o a lo tuyo, y los barridos.
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_TARGETING, on && level >= 1);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_MASS_REMOVAL, on && level >= 1);
        // Nivel 2: y ademas cada disparo y cada cosa que se revela. Es mucho
        // parar, por eso no es el de fabrica.
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_TRIGGERS, on && level >= 2);
        prefs.setPref(FPref.YIELD_INTERRUPT_ON_REVEAL, on && level >= 2);
    }

    /**
     * Vuelca el pase inteligente al motor SIN esperar a la proxima partida.
     *
     * <p>Hace falta porque los ajustes se abren desde el menu de pausa, o sea
     * con una partida delante: sin esto habria que salir y volver a entrar
     * para notar el cambio, y un ajuste que no se nota al tocarlo parece roto.
     * Es barato porque {@code YieldController} lee las preferencias EN VIVO.
     */
    public static void refreshSmartPass() {
        applySmartPass(FModel.getPreferences());
    }

    /** Devuelve el primer mazo de Commander del usuario, o null si no hay. */
    public static Deck firstCommanderDeck() {
        for (final Deck d : FModel.getDecks().getCommander()) {
            return d;
        }
        return null;
    }

    /** Busca un mazo de Commander por nombre (sin distinguir mayusculas). */
    public static Deck commanderDeck(final String name) {
        for (final Deck d : FModel.getDecks().getCommander()) {
            if (d.getName().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }
}
