package forge.neo.match;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.LobbyPlayer;
import forge.deck.CardPool;
import forge.game.Game;
import forge.game.GameEntityView;
import forge.game.GameState;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.spellability.StackItemView;
import forge.interfaces.IGameController;
import forge.player.AutoYieldStore;
import forge.gamemodes.match.YieldController;
import forge.game.event.GameEvent;
import forge.game.event.GameEventLandPlayed;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.event.GameEventTurnPhase;
import forge.game.event.GameEventPlayerDamaged;
import forge.game.event.GameEventCardChangeZone;
import forge.game.phase.PhaseType;
import forge.game.player.DelayedReveal;
import forge.game.player.IHasIcon;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import forge.gamemodes.net.NetworkGuiGame;
import forge.gamemodes.match.NextGameDecision;
import forge.gui.GuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;
import forge.neo.platform.NeoGuiBase;
import forge.neo.ui.AbilityMenu;
import forge.neo.ui.AmountDialog;
import forge.neo.ui.CardPromptDialog;
import forge.neo.ui.ChoiceDialog;
import forge.neo.ui.ConfirmDialog;
import forge.neo.ui.DamageDialog;
import forge.neo.ui.GameOverScreen;
import forge.neo.ui.TableScreen;
import forge.neo.platform.UiDispatcher;
import forge.neo.ui.PromptBanner;
import forge.player.PlayerZoneUpdate;
import forge.player.PlayerZoneUpdates;
import forge.trackable.TrackableCollection;
import forge.util.FSerializableFunction;
import forge.util.ITriggerEvent;

/**
 * FASE 1 — implementacion de {@link IGuiGame} sin interfaz grafica.
 *
 * <p>Objetivo: validar que una partida real de Commander arranca, avanza y
 * termina estando conducida por NUESTRO codigo, no por la GUI Swing. Todo se
 * imprime por consola.
 *
 * <p>Dos modos:
 * <ul>
 *   <li>{@code OBSERVE} — todos los jugadores son IA y nosotros solo
 *       observamos. Valida el camino de LECTURA: GameView, eventos, zonas.
 *       Es la base de las fases 2 y 3 (pintar).</li>
 *   <li>{@code AUTO_PLAY} — hay un jugador humano, pero contestamos
 *       automaticamente. Valida el camino de ESCRITURA: el modelo bloqueante
 *       de peticion/respuesta, que es la parte de verdad arriesgada.</li>
 * </ul>
 *
 * <p>En la fase 4 las decisiones dejaran de ser automaticas y se le pediran al
 * usuario. La estructura no cambia: cambia quien contesta.
 */
/*
 * Por que NetworkGuiGame y no AbstractGuiGame, que es lo que era:
 *
 * NetworkGuiGame ES un AbstractGuiGame — solo anyade la implementacion de
 * applyDelta(DeltaPacket), que en AbstractGuiGame es un no-op. Fuera de red no
 * cambia absolutamente nada, porque ese metodo solo lo llama el servidor por el
 * cable. Y dentro de red es imprescindible: el servidor no manda el GameView
 * entero en cada cambio, manda SOLO las propiedades que se han movido
 * (DeltaSyncManager). Heredando de AbstractGuiGame esos paquetes se recibirian
 * y se tirarian en silencio, y la mesa del invitado no se moveria nunca.
 *
 * No anyade ni un metodo abstracto, asi que el cambio es esta linea y ya.
 */
public class NeoMatchUI extends NetworkGuiGame {

    public enum Mode {
        /** Todo IA; solo miramos. */
        OBSERVE,
        /** Hay humano pero contestamos solos (validacion). */
        AUTO_PLAY,
        /** Juega el usuario. */
        HUMAN
    }

    private final Mode mode;
    private final boolean verbose;

    /** Un solo hilo para contestar al motor. NUNCA responder en el hilo del motor. */
    private final ExecutorService responder =
            Executors.newSingleThreadExecutor(r -> {
                final Thread t = new Thread(r, "neo-responder");
                t.setDaemon(true);
                return t;
            });

    /** Que hacer cuando el match se cierre. Lo lee {@link NeoGame}. */
    public enum Exit {
        /** Volver a la pantalla de inicio. */
        MENU,
        /** Empezar otra partida con los mismos mazos. */
        RESTART
    }

    private volatile Exit exitAction;

    /**
     * De donde viene esta partida, o sea <b>a donde se vuelve al acabarla</b>.
     *
     * <p>Es lo unico que decide que botones ensena {@link GameOverScreen}, y no
     * es cosmetico: en un modo con estado propio (la aventura, una run de
     * Ascenso) "otra partida" es un boton que <b>miente</b> — el modo esta
     * esperando a repartir su premio y a seguir su bucle, no a montar un duelo
     * suelto. Un control que no hace lo que parece es peor que no tenerlo
     * (principio 1 de las notas de diseño).
     */
    public enum Ending {
        /**
         * Partida suelta: "volver al menu" y "otra partida" dicen la verdad.
         */
        NORMAL,
        /**
         * Duelo de la aventura. Sin "otra partida": el motor esta esperando a
         * repartir la recompensa (el sobre de premio), y RESTART se saltaba esa
         * pantalla entera para meterte directo en un duelo nuevo. Y sin "volver
         * al menu": de ahi se vuelve al cuartel general.
         */
        QUEST,
        /**
         * Nodo de una run de Ascenso. Un solo boton, "continuar Ascenso", que
         * lleva al premio y al mapa.
         *
         * <p>Los dos de siempre mentian los dos a la vez: "otra partida" se
         * trataba como abandonar el duelo a medias y <b>se llevaba la run por
         * delante</b> (reportado jugando), y "volver al menu" no volvia a
         * ningun menu — el bucle del modo seguia igual hacia el premio. Aqui
         * solo hay una continuacion posible, asi que solo hay un boton.
         */
        ASCENT
    }

    private volatile Ending ending = Ending.NORMAL;

    public void setEnding(final Ending ending) {
        this.ending = ending == null ? Ending.NORMAL : ending;
    }

    /**
     * Salir de la partida en curso.
     *
     * <p>No se corta por lo sano: se <b>concede</b>. Es lo que hace la GUI
     * Swing y es lo unico correcto, porque el motor puede estar bloqueado
     * esperando una decision nuestra. Conceder termina la partida por las
     * buenas, libera ese hilo y deja que Forge cierre el match como toca.
     *
     * <p>{@code concede()} de {@code AbstractGuiGame} ya distingue los casos
     * raros (uno contra uno, multijugador donde quedan IAs peleando,
     * espectador), asi que se reutiliza tal cual en vez de reimplementarlo.
     */
    /**
     * Salida de emergencia: el motor no ha contestado y hay que irse igual.
     *
     * <p>Ver {@link #leaveMatch(Exit)}.
     */
    private volatile Runnable onAbandon;

    public void setOnAbandon(final Runnable action) {
        this.onAbandon = action;
    }

    /**
     * Cuanto se espera a que el motor cierre la partida antes de irse por las
     * bravas. Una concesion sana es instantanea; seis segundos son de sobra.
     */
    private static final long LEAVE_TIMEOUT_MS = 6_000;

    /**
     * Vigilante de "Salir".
     *
     * <p><b>Por que hace falta.</b> Salir de una partida se le PIDE al motor
     * (se concede), y eso es lo correcto — el motor puede estar bloqueado
     * esperando una decision nuestra y hay que liberarlo por las buenas. Pero
     * da por hecho que el motor sigue vivo.
     *
     * <p>Y puede no estarlo. Caso real, jugando en red: la IA de Forge reventó
     * con un NPE dentro de {@code AiBlockController} y <b>se llevo el hilo de
     * la partida</b>. A partir de ahi no habia OK, no habia prioridad y "Salir"
     * ponia "Cerrando la partida..." y se quedaba asi para siempre: los dos
     * jugadores encerrados en una mesa muerta, sin mas salida que matar la
     * ventana.
     *
     * <p>Un boton que no hace lo que dice es peor que no tenerlo (principio 1),
     * y el jugador necesita una valvula de escape (principio 7). Asi que si el
     * motor no ha cerrado la partida en {@value #LEAVE_TIMEOUT_MS} ms, la
     * interfaz se va sola. La partida se pierde igual — ya estaba perdida —
     * pero se sale.
     */
    private void watchdogForLeaving() {
        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(LEAVE_TIMEOUT_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (closeNotified.get()) {
                return;
            }
            System.out.println("[neo] el motor no ha cerrado la partida en "
                    + (LEAVE_TIMEOUT_MS / 1000) + " s: se sale igual");
            final Runnable out = onAbandon;
            if (out != null && closeNotified.compareAndSet(false, true)) {
                out.run();
            }
        }, "neo-leave-watchdog");
        t.setDaemon(true);
        t.start();
    }

    public void leaveMatch(final Exit action) {
        this.exitAction = action;
        watchdogForLeaving();
        respondLater(() -> {
            // Sin esto, concede() levantaria SU dialogo de confirmacion en
            // ingles encima del nuestro, que ya ha preguntado.
            autoConfirm = true;
            try {
                concede();
            } catch (final RuntimeException e) {
                // No se deja pasar en silencio, pero TAMPOCO se aborta la
                // salida: ver endTheGameForReal(), que es lo que la remata.
                System.out.println("[neo] el motor ha fallado al conceder: " + e);
            } finally {
                autoConfirm = false;
            }
            endTheGameForReal();
        });
    }

    /**
     * Rematar la salida: que la partida se acabe DE VERDAD.
     *
     * <p><b>Por que hace falta.</b> Conceder no termina la partida cuando
     * quedan rivales vivos: en una mesa a cuatro te sales tu y las tres IAs
     * siguen jugando entre ellas. Forge lo sabe y lo remata solo... pero por
     * un camino que se rompe de dos maneras, y las dos salieron jugando:
     *
     * <ul>
     *   <li>{@code AbstractGuiGame.concede()} manda el {@code QUIT} <b>al
     *       final</b>, despues de tocar el motor. Y lo toca desde el hilo de
     *       interfaz: si por el camino salta una {@code
     *       ConcurrentModificationException} — que en este registro salen a
     *       pares — el {@code QUIT} no se manda nunca. El jugador ya ha
     *       concedido, o sea que su parte es irreversible, y la pantalla se
     *       queda ahi mirando como juegan las IAs.</li>
     *   <li>Y al segundo intento entra por la otra rama (ya tiene resultado,
     *       "no hace falta conceder") que si termina la partida. De ahi el
     *       sintoma exacto que se reporto: <i>"le di a salir y no me salio;
     *       tuve que darle otra vez"</i>.</li>
     * </ul>
     *
     * <p>Asi que se hace en una sola pulsacion lo que antes costaba dos:
     * conceder — que es lo que lo convierte en una derrota, y lo que la
     * aventura anota — y acto seguido terminar la partida por
     * {@code AllHumansLost}, que es literalmente lo que hace el
     * {@code forceEndGameForRemainingAIs} de Forge. Desde ahi ya vuelve por el
     * camino normal: {@code finishGame} ve el {@code exitAction} puesto y
     * cierra el match.
     *
     * <p>Se toca el motor <b>desde un hilo de partida</b>
     * ({@code GameAction.invoke}), no desde el de interfaz. Es de donde salian
     * las {@code ConcurrentModificationException} de arriba.
     */
    private void endTheGameForReal() {
        if (isNetGame()) {
            // En red la partida no vive aqui: el motor esta en el ordenador del
            // anfitrion y ya se le ha mandado la concesion. Rematarla desde
            // este lado seria terminar una copia. Al invitado lo saca
            // afterGameEnd (y si no llega, el vigilante).
            return;
        }
        final GameView gv = getGameView();
        if (gv == null || gv.isGameOver()) {
            // Ya se acabo: finishGame llega (o ha llegado) por su cuenta.
            return;
        }
        final Game game = gv.getGame();
        if (game == null) {
            return;
        }
        game.getAction().invoke(() -> {
            try {
                game.setGameOver(forge.game.GameEndReason.AllHumansLost);
            } catch (final RuntimeException e) {
                System.out.println("[neo] no se ha podido cerrar la partida: " + e);
            }
        });
    }

    public Exit getExitAction() {
        return exitAction;
    }

    /** Mientras esta puesto, las confirmaciones del motor se dan por aceptadas. */
    private volatile boolean autoConfirm;

    private final AtomicBoolean finished = new AtomicBoolean(false);
    private final AtomicInteger decisions = new AtomicInteger();
    private PhaseType lastPhase;
    private int lastTurn = -1;

    public NeoMatchUI(final Mode mode, final boolean verbose) {
        this.mode = mode;
        this.verbose = verbose;
    }

    public int getDecisionCount() {
        return decisions.get();
    }

    public boolean isFinished() {
        return finished.get();
    }

    public void shutdown() {
        responder.shutdownNow();
    }

    // ===============================================================
    // Puente con la mesa (fase 3)
    // ===============================================================

    private volatile TableBinder binder;
    private volatile TableScreen table;

    /** Engancha la mesa. Si no hay, seguimos funcionando solo por consola. */
    public void setBinder(final TableBinder binder) {
        this.binder = binder;
        pushToTable();
    }

    /** La pantalla con la que hablar en modo humano. */
    public void setTable(final TableScreen table) {
        this.table = table;
        if (table == null || mode != Mode.HUMAN) {
            return;
        }
        // Esto se llama desde el hilo que lanza la partida, no desde el de
        // JavaFX, y toca nodos de la escena. Al hilo de interfaz con ello.
        final UiDispatcher ui = uiDispatcher();
        final Runnable wire = () -> {
            wireGameLogButton();
            table.setOnCardClicked(this::onCardClicked);
            table.setOnPlayerClicked(this::onPlayerClicked);
            // Lanzar desde el cementerio o el exilio: el visor de la zona deja
            // clicar lo que el motor dice que se puede lanzar desde ahi.
            table.setPlayableOutside(this::isPlayableOutside);
            // El visor de zonas ensenya cada carta solo si el motor lo permite:
            // la biblioteca es informacion oculta.
            table.setCardVisibility(this::mayView);
            table.setOnCardDropped(this::onCardDropped);

            // Gastar mana flotante clicando su pip.
            table.setOnManaClicked(color ->
                    respondLater(() -> getGameController().useMana(color)));

            // "No me vuelvas a preguntar por esto": el menu del stack.
            table.setOnStackMenu(this::showStackMenu);

            // Paradas de fase: el control del auto-pass, como los stops de Arena.
            table.setPhaseStops(this::hasStop);
            table.setOnPhaseToggled(phase -> {
                toggleStop(phase);
                table.setPhaseStops(this::hasStop);
            });
        };
        if (ui != null) {
            ui.runLater(wire);
        } else {
            wire.run();
        }
    }

    private boolean interactive() {
        return mode == Mode.HUMAN && table != null;
    }

    /**
     * Click en cualquier carta de la mesa.
     *
     * <p>Es el verbo universal: {@code selectCard} sirve para lanzar un
     * hechizo, activar una habilidad, elegir objetivo, sacrificar o declarar
     * atacante. Quien decide que significa es el input activo del motor, no
     * nosotros. Si la carta tiene varias habilidades jugables, el motor nos
     * devolvera la llamada por {@code getAbilityToPlay}.
     */
    /**
     * Click en el retrato de un jugador.
     *
     * <p>Lo pide el motor en varios sitios ("Who would you like to start this
     * game? (Click on the portrait)", objetivos que son jugadores, a quien
     * atacas). Sin esto la partida se queda esperando para siempre.
     */
    private void onPlayerClicked(final PlayerView player) {
        if (!interactive() || player == null || finished.get()) {
            return;
        }
        respondLater(() -> getGameController().selectPlayer(player, null));
    }

    /**
     * El jugador ha cambiado de pestanya de rival.
     *
     * <p><b>La pestanya elige tambien a quien atacas.</b> Mientras declaras
     * atacantes, {@code InputAttack} guarda un "defensor actual" y TODO lo que
     * cliques ataca a ese, mires a quien mires. Cambiar de pestanya no se lo
     * decia: mirabas a IA-3, clicabas una criatura, y atacaba a IA-1. Fallo
     * real jugando, y de los peores — el ataque se declara perfectamente, solo
     * que contra quien no era, y no hay forma de darse cuenta hasta que pega.
     *
     * <p>Aqui no hay ninguna regla nuestra: es el MISMO {@code selectPlayer}
     * que manda clicar su retrato. Solo se traduce el gesto.
     */
    public void onOpponentFocused(final PlayerView player) {
        if (!interactive() || player == null || finished.get()) {
            return;
        }
        if (!isDeclaringAttackers() || isHighlighted(player)) {
            return;
        }
        if (CLICK_DEBUG) {
            System.out.printf("[click] pestanya %s -> nuevo defensor%n",
                    PlayerName.of(player));
        }
        respondLater(() -> getGameController().selectPlayer(player, null));
    }

    /**
     * Si el motor esta esperando ahora mismo a que declares atacantes.
     *
     * <p>Se comprueba porque {@code selectPlayer} NO es inofensivo en
     * cualquier momento: durante un lanzamiento significa "te elijo a ti de
     * objetivo", y mirar a un rival no puede convertirse en apuntarle.
     *
     * <p>La clave es {@code isSelecting()}: los DOS inputs que dan sentido a
     * elegir un jugador ({@code InputSelectTargets} y
     * {@code InputSelectEntitiesFromList}) publican sus candidatos con
     * {@code setSelectables}, e {@code InputAttack} no. Con eso, mas la fase y
     * el turno, no queda ningun caso ambiguo.
     */
    private boolean isDeclaringAttackers() {
        final GameView gv = getGameView();
        if (gv == null || gv.getPhase() != PhaseType.COMBAT_DECLARE_ATTACKERS) {
            return false;
        }
        return !payingMana && !isSelecting() && isLocalPlayer(gv.getPlayerTurn());
    }

    private void onCardClicked(final CardView card) {
        if (!interactive() || card == null || finished.get()) {
            return;
        }

        // Volver a clicar la fuente que acabas de tapear = deshacerlo: la carta
        // se endereza y el mana vuelve. Es lo que espera cualquiera que haya
        // jugado en una mesa de verdad, y es un error facil de cometer.
        //
        // El motor solo sabe deshacer LA ULTIMA accion, asi que esto se ofrece
        // unicamente sobre la ultima fuente que produjo mana. Clicar otra
        // tierra tapada no deshace nada ajeno: simplemente no hace nada.
        //
        // Las dos condiciones de estado son IMPRESCINDIBLES, no adorno: una
        // carta ENDEREZADA no puede tener nada que deshacer, y sin mana
        // flotante tampoco hay nada que devolver. Sin ellas, si la marca se
        // quedaba pegada a una carta (por ejemplo porque el mana se vacio al
        // cambiar de fase sin avisar), esa carta dejaba de responder al click
        // para siempre: cada click se iba a un deshacer que no hacia nada.
        if (canUndoClick(card) && !isSelecting()) {
            clickTrace(card, "deshacer");
            lastManaSource = null;
            respondLater(() -> getGameController().undoLastAction());
            return;
        }

        // Quitar de combate un bloqueador que para a OTRO atacante.
        //
        // InputBlock guarda un "atacante actual" y todo lo que clicas se
        // entiende referido a EL: si la ficha bloquea a A y el actual es B, el
        // click no hace nada y la ficha se queda pegada al combate para
        // siempre. Fallo real jugando: "me deja poner mas bloqueadores y
        // quitarlos, pero a este no hay forma de quitarlo".
        //
        // El arreglo es el mismo truco de orden que ya usa el arrastre: dos
        // clicks, primero el atacante y luego el bloqueador. Ni una regla
        // nuestra.
        final CardView blocked = attackerToSelectFirst(card);
        if (blocked != null) {
            clickTrace(card, "quitar bloqueo");
            respondLater(() -> getGameController().selectCard(blocked, null, null));
            lastClicked = card;
            respondLater(() -> getGameController().selectCard(card, null, null));
            return;
        }

        // Lanzar el hechizo de una criatura PREPARADA clicandola a ella.
        //
        // Sin esto no se puede: el motor no pone la habilidad en la criatura.
        // Lo que hace (AlterAttributeEffect, caso "Prepared") es dejar una
        // COPIA de la carta en el exilio y darle MayPlay desde un efecto
        // invisible del mando. O sea que el click que el motor espera es sobre
        // esa copia, dentro del visor del exilio — un sitio al que nadie va a
        // llegar solo cuando lo que la carta dice es "esta criatura se
        // prepara".
        //
        // Aqui no se inventa nada: se manda el MISMO selectCard, solo que a la
        // carta que el motor espera. Es el mismo truco de redireccion que ya
        // usa el quitar un bloqueador.
        final CardView prepared = preparedSpellToCast(card);
        if (prepared != null) {
            clickTrace(card, "lanzar su hechizo preparado");
            lastClicked = card;
            respondLater(() -> getGameController().selectCard(prepared, null, null));
            return;
        }

        clickTrace(card, "activar");
        lastClicked = card;
        respondLater(() -> getGameController().selectCard(card, null, null));
    }

    /**
     * El hechizo preparado que hay que lanzar al clicar esta criatura, o null.
     *
     * <p>Tres condiciones, y las tres hacen falta:
     *
     * <ol>
     *   <li><b>Que este preparada de verdad</b>: {@code getPreparedSpell()} da
     *       la copia del exilio, y es null si no lo esta. (Ojo con
     *       {@code hasPreparedSpell()}, que es estructural y vale para las 54
     *       cartas de la mecanica aunque no esten preparadas.)</li>
     *   <li><b>Que el motor la de por jugable AHORA</b>, con lo mismo que mira
     *       el visor de zonas: {@code PlayerView.getFlashback()}. Es lo que
     *       tiene en cuenta el mana, el turno, la fase y el stack — y no
     *       nosotros.</li>
     *   <li><b>Que clicar la criatura no signifique ya otra cosa</b>:
     *       {@code getActivateDescription} devuelve null cuando ese click no
     *       haria nada. Es el mismo guardia del bloqueador, y es lo que deja
     *       intactos declarar atacante, bloquear y elegirla de objetivo.</li>
     * </ol>
     *
     * <p>Medido sobre las 54 cartas: <b>ninguna</b> tiene ademas una habilidad
     * activada propia, asi que en la practica el guardia no le quita el click
     * a nada.
     */
    private CardView preparedSpellToCast(final CardView card) {
        final CardView spell = castablePreparedSpell(card);
        if (spell == null) {
            return null;
        }
        return getGameController().getActivateDescription(card) == null ? spell : null;
    }

    /**
     * Lo mismo pero <b>sin preguntarle nada al motor</b>: solo mirando la vista.
     *
     * <p>Existe por el resaltado. {@code setActionable} se recalcula sobre
     * TODOS los nodos de la mesa en cada refresco y desde el hilo de interfaz;
     * meter ahi un {@code getActivateDescription} por carta serian decenas de
     * llamadas al motor por refresco, desde el hilo que no es. Estas dos
     * preguntas son lecturas de la vista y no cuestan nada.
     */
    private CardView castablePreparedSpell(final CardView card) {
        if (card == null || isSelecting() || payingMana) {
            return null;
        }
        final CardView spell = card.getPreparedSpell();
        return spell != null && isPlayableOutside(spell) ? spell : null;
    }

    /**
     * El atacante que hay que elegir ANTES de clicar este bloqueador, o null si
     * el click ya vale tal cual.
     *
     * <p>Se contesta con dos datos que ya nos da el motor y ninguna deduccion:
     *
     * <ol>
     *   <li><b>Que atacante esta activo:</b> {@code InputBlock} lo marca con
     *       {@code setHighlighted}. Si no hay ninguno marcado, no estamos
     *       declarando bloqueos y aqui no hay nada que hacer.</li>
     *   <li><b>Que haria el click:</b> {@code getActivateDescription} pregunta
     *       al input activo. Si devuelve null, clicar esa carta NO hace nada —
     *       y solo entonces nos metemos. Asi se respeta el caso legitimo de la
     *       criatura que puede bloquear a varias a la vez, donde el click SI
     *       significa "bloquea tambien a este".</li>
     * </ol>
     */
    private CardView attackerToSelectFirst(final CardView card) {
        final GameView gv = getGameView();
        if (gv == null || card == null || !card.isBlocking() || isSelecting() || payingMana) {
            return null;
        }
        final forge.game.combat.CombatView combat = gv.getCombat();
        if (combat == null || combat.getAttackers() == null) {
            return null;
        }
        CardView blocked = null;
        boolean anyHighlighted = false;
        for (final CardView attacker : combat.getAttackers()) {
            if (attacker == null) {
                continue;
            }
            if (isHighlighted(attacker)) {
                anyHighlighted = true;
                if (blocks(combat, attacker, card)) {
                    return null;   // ya es el atacante activo: el click vale
                }
            } else if (blocked == null && blocks(combat, attacker, card)) {
                blocked = attacker;
            }
        }
        if (!anyHighlighted || blocked == null) {
            return null;
        }
        return getGameController().getActivateDescription(card) == null ? blocked : null;
    }

    private static boolean blocks(final forge.game.combat.CombatView combat,
                                  final CardView attacker, final CardView blocker) {
        return contains(combat.getBlockers(attacker), blocker)
                || contains(combat.getPlannedBlockers(attacker), blocker);
    }

    private static boolean contains(final Iterable<CardView> cards, final CardView card) {
        if (cards == null) {
            return false;
        }
        for (final CardView cv : cards) {
            if (cv != null && cv.getId() == card.getId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Deshacer lo ultimo. Es lo mismo que hace el click sobre la fuente recien
     * tapeada, pero con el teclado y sin tener que acertarle a la carta.
     */
    public void undoLast() {
        if (!interactive() || finished.get()) {
            return;
        }
        lastManaSource = null;
        respondLater(() -> getGameController().undoLastAction());
    }

    /** Ultima carta que ha clicado el jugador. */
    private volatile CardView lastClicked;

    /**
     * Ultima carta que produjo mana al clicarla.
     *
     * <p>Se deduce comparando el tamano de la reserva antes y despues: si al
     * clicar una carta la reserva crece, esa carta es la fuente. Es la unica
     * forma de saberlo sin duplicar estado del motor.
     */
    private volatile CardView lastManaSource;
    private volatile int lastPoolSize;

    /**
     * Traza que ha entendido la interfaz de cada click.
     *
     * <p>Se enciende con {@code -Dneo.click.debug=true}. Existe porque el fallo
     * tipico de esta parte no es que el motor rechace algo, sino que el click
     * se lo quede la interfaz por el camino y no llegue a salir: sin ver cual
     * de los dos pasa, se depura a ciegas.
     */
    private void clickTrace(final CardView card, final String action) {
        if (!CLICK_DEBUG) {
            return;
        }
        System.out.printf("[click] %s -> %s | girada=%s elegible=%s reserva=%d fuente=%s%n",
                card, action, card.isTapped(), isSelectable(card), localPoolSize(),
                lastManaSource);
    }

    private static final boolean CLICK_DEBUG = Boolean.getBoolean("neo.click.debug");

    /** Si clicar esta carta significa "deshacer" en vez de "activar". */
    private boolean canUndoClick(final CardView card) {
        return card.equals(lastManaSource) && card.isTapped() && localPoolSize() > 0;
    }

    /**
     * El mismo jugador, pero el objeto VIVO de la partida.
     *
     * <p>Hace falta por lo mismo que en {@code TableBinder}: en el invitado de
     * una partida en red, los {@code PlayerView} que llegan en
     * {@code openView} son copias que el cliente deja tal cual y que la
     * sincronizacion por deltas <b>no vuelve a tocar</b>. Preguntarle a esa
     * copia cuanto mana tienes devuelve siempre 0, y si has perdido, siempre
     * que no.
     *
     * <p>Como {@code equals} compara por id, lo de fuera sigue funcionando
     * igual (los mapas de {@code AbstractGuiGame} aciertan con las dos); lo
     * unico que hay que cuidar es de donde se LEEN los datos.
     */
    private PlayerView live(final PlayerView p) {
        final GameView gv = getGameView();
        if (p == null || gv == null || gv.getPlayers() == null) {
            return p;
        }
        for (final PlayerView candidate : gv.getPlayers()) {
            if (candidate != null && candidate.getId() == p.getId()) {
                return candidate;
            }
        }
        return p;
    }

    private int localPoolSize() {
        if (!hasLocalPlayers()) {
            return 0;
        }
        int total = 0;
        for (final PlayerView seat : getLocalPlayers()) {
            final PlayerView p = live(seat);
            // ManaAtom, no MagicColor: ver el comentario de TableBinder. Con la
            // codificacion equivocada el mana incoloro no se contaba, y eso
            // ademas impedia deshacer el tapeo de una fuente incolora.
            for (final byte type : forge.card.mana.ManaAtom.MANATYPES) {
                total += p.getMana(type);
            }
        }
        return total;
    }

    /**
     * Soltar una carta encima de algo. El verbo principal de Arena.
     *
     * <p>No hay ninguna logica de reglas aqui: el arrastre se traduce a la
     * MISMA pareja de ordenes que daria un jugador clicando, y es el input
     * activo del motor quien decide que significan. Lo unico que aporta este
     * metodo es el ORDEN de esos dos clicks, que es lo que la gente se
     * equivoca al hacerlo a mano:
     *
     * <ul>
     *   <li><b>Atacar:</b> primero se elige a quien atacas (jugador o
     *       planeswalker) y despues la criatura. {@code InputAttack} guarda un
     *       "defensor actual" y declara contra el.</li>
     *   <li><b>Bloquear:</b> primero el atacante y luego el bloqueador, porque
     *       {@code InputBlock} guarda un "atacante actual".</li>
     *   <li><b>Jugar desde la mano:</b> soltar en cualquier sitio lanza la
     *       carta. Los objetivos los pide el motor despues, como en Arena.</li>
     * </ul>
     */
    private void onCardDropped(final CardView source, final boolean fromHand,
                               final Object target) {
        if (!interactive() || source == null || finished.get()) {
            return;
        }
        if (fromHand || target == null) {
            respondLater(() -> getGameController().selectCard(source, null, null));
            return;
        }
        if (target instanceof PlayerView player) {
            respondLater(() -> getGameController().selectPlayer(player, null));
        } else if (target instanceof CardView card && card.getId() != source.getId()) {
            respondLater(() -> getGameController().selectCard(card, null, null));
        }
        respondLater(() -> getGameController().selectCard(source, null, null));
    }

    /**
     * Empuja el estado a la mesa. El binder agrupa los avisos, asi que se puede
     * llamar tantas veces como haga falta sin coste.
     */
    private void pushToTable() {
        final TableBinder b = binder;
        if (b != null) {
            b.setGameView(getGameView());
            b.requestRefresh();
        }
    }

    // ===============================================================
    // Log
    // ===============================================================

    private void log(final String fmt, final Object... args) {
        System.out.println("  " + String.format(fmt, args));
    }

    private void trace(final String fmt, final Object... args) {
        if (verbose) {
            System.out.println("    . " + String.format(fmt, args));
        }
    }

    private static String nameOf(final PlayerView p) {
        return PlayerName.of(p);
    }

    // ===============================================================
    // Observacion: lo que el motor nos empuja
    // ===============================================================

    /**
     * El motor avisa de cambio de turno por aqui, no por setGameView.
     * En la fase 3 esto refrescara la cabecera de la mesa.
     */
    @Override
    public void updateTurn(final PlayerView player) {
        super.updateTurn(player);
        dumpTable();
        pushToTable();
        announceTurn(player);
    }

    /**
     * "Te toca", en grande y en el centro.
     *
     * <p>Salio de jugar: <i>"no me entero de cuando es mi turno, solo me guio
     * por el texto pequenyo de la derecha"</i>. El motor avisa del cambio de
     * turno por aqui — no por {@code setGameView} — asi que este es el sitio.
     *
     * <p>Corre en el hilo del MOTOR: todo lo que toque la mesa va por el hilo de
     * interfaz.
     */
    private void announceTurn(final PlayerView player) {
        final forge.neo.ui.TableScreen t = table;
        if (t == null || player == null) {
            return;
        }
        // isLocalPlayer lo trae AbstractGuiGame: sabe que asientos maneja esta
        // interfaz. Comparar con el jugador de la barra de abajo funcionaria casi
        // siempre y fallaria justo donde importa (espectador, varios asientos).
        final boolean yours = isLocalPlayer(player);
        final String who = nameOf(player);
        final int turn = getGameView() == null ? 0 : getGameView().getTurn();
        runOnUi(() -> {
            t.announceTurn(yours, who, turn);
            t.getPhaseRail().setTurnOwner(yours, who);
        });
    }


    /**
     * Cambio de fase. En la fase 3 esto movera el indicador del rail de fases.
     */
    @Override
    public void updatePhase(final boolean afterPhase) {
        super.updatePhase(afterPhase);
        final GameView gv = getGameView();
        if (gv == null) {
            return;
        }
        final PhaseType phase = gv.getPhase();
        if (phase != lastPhase) {
            lastPhase = phase;
            trace("fase: %s", phase);
            // El mana flotante se vacia al acabar cada paso, y de eso no
            // siempre llega aviso. Olvidar aqui la fuente evita que la marca
            // se quede pegada a una carta que ya no tiene nada que deshacer.
            lastManaSource = null;
            lastClicked = null;
            lastPoolSize = localPoolSize();
        }
        pushToTable();
    }

    /** Estado de la mesa: exactamente los datos que pintara la fase 3. */
    private void dumpTable() {
        final GameView gv = getGameView();
        if (gv == null) {
            return;
        }
        final int turn = gv.getTurn();
        if (turn == lastTurn) {
            return;
        }
        lastTurn = turn;

        final StringBuilder sb = new StringBuilder();
        sb.append(String.format("%n-- Turno %d . juega %s", turn, nameOf(gv.getPlayerTurn())));
        for (final PlayerView p : gv.getPlayers()) {
            sb.append(String.format("%n     %-14s %3d vida | mano %2d | mesa %2d | cementerio %2d | mazo %2d",
                    nameOf(p), p.getLife(),
                    p.getZoneSize(ZoneType.Hand),
                    p.getZoneSize(ZoneType.Battlefield),
                    p.getZoneSize(ZoneType.Graveyard),
                    p.getZoneSize(ZoneType.Library)));
        }
        System.out.println(sb);
    }

    @Override
    public void handleGameEvent(final GameEvent event) {
        if (event == null) {
            return;
        }
        // EL PRIMERO, y a proposito. Lo que viene detras puede tardar mucho
        // (watchForThingsThatAffectMe llega a PARAR el hilo del motor hasta que
        // pulsas "Entendido") y puede reventar, y en los dos casos el aviso se
        // perderia. Quien escucha aqui — hoy el tutorial — da un paso por bueno
        // cuando el motor cuenta algo, asi que un aviso perdido es un paso que
        // no se cierra NUNCA: no falla, se queda ahi. Avisar cuesta un
        // Platform.runLater; ser el ultimo de la cola costaba una leccion.
        tellTheSpy(event);
        // Cada evento del motor (carta jugada, criatura muerta, dano...) puede
        // cambiar lo que se ve. El binder agrupa, asi que esto es barato.
        pushToTable();
        refreshCentralPrompt();
        flushStrandedViewChanges();
        final boolean stopped = watchForThingsThatAffectMe(event);
        // Si la partida acaba de pararse a contarte algo, ya has tenido tu
        // tiempo: frenar ademas por el reloj seria esperar dos veces.
        if (!stopped) {
            slowTheAiDown(event);
        }
        animateEvent(event);
        trace("evento: %s", event.getClass().getSimpleName());
    }

    /**
     * Pausa base entre carta y carta de la IA, en milisegundos.
     *
     * <p>Tres segundos es lo que se pidio jugando y es lo que dura leer el
     * nombre de una carta y ver donde cae. El ajuste la multiplica:
     * x2 la deja en 1,5 s, x0,5 en 6 s.
     */
    private static final long AI_STEP_MS = 3_000;

    /** El multiplicador del ajuste, en centesimas. 0 = sin pausa. */
    private volatile int aiSpeed =
            forge.neo.NeoSettings.getInt(forge.neo.NeoSettings.AI_SPEED, 100);

    public void setAiSpeed(final int hundredths) {
        this.aiSpeed = hundredths;
    }

    /**
     * <b>Frena a la IA para que se pueda ver lo que hace.</b>
     *
     * <p>Sale de jugar, y es el principio 9 de las notas de diseño llevado al reloj: la
     * IA no tiene manos. Resuelve un turno entero en uno o dos segundos, asi
     * que te bajan tres criaturas, te matan una y te enteras al mirar el
     * marcador. La pausa por aviso ("Pararse en el turno del rival") lo cuenta,
     * pero solo lo que te afecta y a cambio de un OK; esto es lo otro que hacia
     * falta: <b>ritmo</b>.
     *
     * <p>Se frena en lo que un jugador de verdad tarda en hacer: poner una
     * tierra y lanzar algo. No en cada evento — un solo hechizo dispara diez —
     * ni en lo que hace el jugador local, que ya sabe lo que esta haciendo.
     *
     * <p><b>Duerme el hilo del MOTOR</b>, que es justo lo que se quiere: la
     * partida se frena de verdad, no solo la pantalla. Este metodo se llama
     * desde {@code handleGameEvent}, que corre ahi. Nunca desde el de interfaz:
     * eso seria congelar la ventana.
     */
    private void slowTheAiDown(final GameEvent event) {
        final int speed = aiSpeed;
        if (speed <= 0 || !interactive()) {
            return;
        }
        if (isNetGame()) {
            // En red al otro lado hay una persona, no una IA: no hay nada que
            // frenar. Y ademas los eventos llegan por el cable, asi que dormir
            // aqui seria dormir la CONEXION y acumular retraso.
            return;
        }
        // Seguro de vida: dormir el hilo de interfaz congela la ventana.
        final UiDispatcher ui = uiDispatcher();
        if (ui != null && ui.isUiThread()) {
            return;
        }
        if (!isSomebodyElsePlayingACard(event)) {
            return;
        }
        try {
            Thread.sleep(AI_STEP_MS * 100 / speed);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** ¿Este evento es "un rival acaba de jugar una carta"? */
    private boolean isSomebodyElsePlayingACard(final GameEvent event) {
        final PlayerView me = localPlayer();
        if (me == null) {
            return false;
        }
        if (event instanceof GameEventSpellAbilityCast e) {
            final StackItemView si = e.si();
            return si != null && si.getActivatingPlayer() != null
                    && !me.equals(si.getActivatingPlayer());
        }
        if (event instanceof GameEventLandPlayed e) {
            return e.player() != null && !me.equals(e.player());
        }
        return false;
    }

    /**
     * Quien mas quiere enterarse de lo que cuenta el motor.
     *
     * <p>Hoy solo el tutorial, que da un paso por bueno cuando el jugador HACE
     * lo que se le pedia — jugar una tierra, declarar atacantes, activar una
     * habilidad — y quien sabe si ha pasado de verdad es el motor. Va aqui, que
     * es por donde pasan todos los eventos de cualquier partida (principio 8),
     * y no en quien monta el tutorial.
     */
    private volatile java.util.function.Consumer<GameEvent> eventSpy;

    public void setEventSpy(final java.util.function.Consumer<GameEvent> spy) {
        this.eventSpy = spy;
    }

    private void tellTheSpy(final GameEvent event) {
        final java.util.function.Consumer<GameEvent> spy = eventSpy;
        if (spy == null) {
            return;
        }
        // Corre en el hilo del MOTOR: quien escucha pinta, asi que se cruza al
        // hilo de interfaz aqui y no en cada oyente.
        runOnUi(() -> {
            try {
                spy.accept(event);
            } catch (final RuntimeException e) {
                // Un fallo del tutorial no puede llevarse la partida por
                // delante: aqui estamos en el hilo de interfaz, y una excepcion
                // que se lleve ese hilo se ve como un cuelgue.
                System.out.println("[neo] el tutorial ha fallado al mirar un evento: " + e);
            }
        });
    }

    /**
     * Las animaciones que explican un cambio concreto.
     *
     * <p>La regla de la seccion 10b vale aqui igual: <b>si no explica nada, no
     * esta</b>. Lo que entra y lo que se va de la mesa ya lo anima
     * {@code BattlefieldPane} al aparecer y desaparecer los nodos; lo que
     * faltaba es lo que cambia <i>dentro</i> de una carta que sigue donde
     * estaba, porque eso la mesa lo repinta en silencio:
     *
     * <ul>
     *   <li><b>Dano</b> — un destello rojo en la que lo recibe. Sale de una
     *       queja concreta jugando: <i>"me han matado una criatura y no se ni
     *       como"</i>.</li>
     *   <li><b>Contadores</b> — un golpe de escala cuando suben o bajan: los
     *       +1/+1, la lealtad de un planeswalker, las cargas.</li>
     * </ul>
     *
     * <p>Corre en el hilo del MOTOR, asi que todo va por el hilo de interfaz.
     */
    private void animateEvent(final GameEvent event) {
        final forge.neo.ui.TableScreen t = table;
        if (t == null || !forge.neo.card.CardNode.areAnimationsEnabled()) {
            return;
        }
        if (event instanceof forge.game.event.GameEventCardDamaged e && e.amount() > 0) {
            final CardView hit = e.card();
            runOnUi(() -> t.flashHit(hit));
        } else if (event instanceof forge.game.event.GameEventCardCounters e
                && e.oldValue() != e.newValue()) {
            final CardView changed = e.card();
            runOnUi(() -> t.bumpCard(changed));
        }
    }

    /**
     * Manda algo al hilo de interfaz, venga de donde venga.
     *
     * <p>Se pasa por el {@link UiDispatcher} y no por {@code Platform.runLater}
     * a secas. La diferencia sale en cuanto no hay ventana: los comprobadores
     * ({@code tutorialcheck}, {@code questcheck}…) corren sin toolkit de
     * JavaFX, y ahi {@code Platform.runLater} lanza
     * {@code IllegalStateException: Toolkit not initialized}. Antes no se
     * notaba porque en local no llegaba un solo evento; en cuanto empezaron a
     * llegar, el comprobador del tutorial se llenaba de fallos. El dispatcher
     * ya sabe donde vive el hilo de interfaz en cada montaje — y si no hay
     * ninguno, esto se hace aqui mismo, que es lo correcto sin pantalla.
     */
    private void runOnUi(final Runnable task) {
        if (javafx.application.Platform.isFxApplicationThread()) {
            task.run();
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui != null) {
            ui.runLater(task);
        } else {
            task.run();
        }
    }

    // ---------------------------------------------------------------
    // Enterarse de lo que hace el rival
    // ---------------------------------------------------------------

    /**
     * Lo que le ha pasado a lo tuyo desde la ultima vez que miraste.
     *
     * <p>Feedback real jugando: <i>"los turnos de la IA van a toda hostia y no
     * me entero; de repente me han matado una criatura y no se ni como"</i>.
     * Cuando el rival hace algo a lo que no puedes responder, el motor ni te da
     * prioridad: la mesa cambia sola.
     */
    private final List<String> pendingNews = new ArrayList<>();

    /**
     * Cuanto hay que frenar el turno del rival.
     *
     * <p>Salio jugando: <i>"la IA juega muy rapido, sacan algo y dura un
     * segundo, no me da tiempo a leerlo"</i>. La IA no tiene manos, asi que
     * resuelve su turno entero en milisegundos y te lo pierdes.
     *
     * <ul>
     *   <li>{@code 0} — <b>nunca</b>. El ritmo seguido.</li>
     *   <li>{@code 1} — <b>si me afecta</b>: pierdes un permanente, recibes
     *       dano. Se acumula y se suelta al cambiar de paso.</li>
     *   <li>{@code 2} — <b>lo que va al stack</b>: cada hechizo, habilidad
     *       activada o disparo de un rival. Es el punto medio, y el que casi
     *       siempre se quiere: <b>todo lo que puede cambiar la partida pasa por
     *       el stack</b>, y las habilidades de mana — que son la mayor parte del
     *       ruido de un turno — no pasan por ahi.</li>
     *   <li>{@code 3} — <b>en todo</b>: ademas las tierras que juegan y las
     *       fichas que crean. El mas lento.</li>
     * </ul>
     *
     * <p>El {@code 2} se apoya en un detalle del motor que conviene saber:
     * {@code GameEventSpellAbilityCast} se dispara desde {@code MagicStack.add},
     * o sea <b>solo cuando algo entra de verdad al stack</b>. No hay que filtrar
     * nada a mano.
     */
    private volatile int pauseMode =
            forge.neo.NeoSettings.getInt(forge.neo.NeoSettings.PAUSE_MODE, 2);

    public void setPauseMode(final int mode) {
        this.pauseMode = mode;
    }

    public int getPauseMode() {
        return pauseMode;
    }

    /**
     * Anota lo que te afecta y, en un punto natural, para y te lo cuenta.
     *
     * <p>La pausa <b>no salta en cada evento</b>: eso daria cinco "OK" seguidos
     * por un solo Wrath of God. Se acumula lo que va pasando y se suelta todo
     * junto al cambiar de paso, que es el primer momento tranquilo. Asi sale un
     * aviso por tanda, con el resumen completo.
     *
     * <p>Solo se mira cuando <b>no es tu turno</b>: en el tuyo eres tu quien
     * actua y ya estas viendo lo que haces.
     */
    private boolean watchForThingsThatAffectMe(final GameEvent event) {
        if (!interactive() || pauseMode <= 0 || localPlayer() == null) {
            return false;
        }

        final String note = describeIfItAffectsMe(event);
        if (note != null) {
            synchronized (pendingNews) {
                if (!pendingNews.contains(note)) {
                    pendingNews.add(note);
                }
            }
            uiRunLater(() -> table.markLogUnread());

            // Del nivel 2 en adelante se suelta en el acto: lo que se quiere es
            // seguir la jugada del rival segun la hace, no un resumen al final.
            if (pauseMode >= 2) {
                return flushNews();
            }
        }

        // Punto natural para soltar el resumen: el motor cambia de paso. Asi un
        // Wrath of God que se lleva cinco criaturas es UN aviso con las cinco, y
        // no cinco "OK" seguidos.
        if (event instanceof GameEventTurnPhase) {
            return flushNews();
        }
        return false;
    }

    /**
     * Para la partida y ensenya lo acumulado, si hay algo.
     *
     * @return si de verdad ha parado (lo mira el ritmo de la IA)
     */
    private boolean flushNews() {
        final List<String> news;
        synchronized (pendingNews) {
            if (pendingNews.isEmpty()) {
                return false;
            }
            news = new ArrayList<>(pendingNews);
            pendingNews.clear();
        }
        if (table == null) {
            return false;
        }

        final String text = String.join(System.lineSeparator(), news);
        // askUser bloquea el hilo del MOTOR hasta que se pulsa OK, que es
        // justamente lo que se quiere: la partida se detiene de verdad.
        askUser(done -> {
            table.getPromptBanner().showAlert(text, NeoText.get("banner.understood"), () -> {
                table.getPromptBanner().hide();
                done.accept(Boolean.TRUE);
            });
            table.requestLayout();
        }, Boolean.TRUE);
        return true;
    }

    /**
     * Traduce un evento a una linea, o null si no te afecta.
     *
     * <p>Se cuentan solo las cosas que cambian TU posicion: pierdes un
     * permanente, recibes dano, pierdes vidas o descartas. Que el rival juegue
     * una tierra o se busque una carta no para la partida — pararia siempre.
     */
    private String describeIfItAffectsMe(final GameEvent event) {
        final PlayerView me = localPlayer();

        // Desde el nivel 2: todo lo que un rival mete en el stack, aunque no te
        // apunte. Es lo que te deja seguir su turno.
        if (pauseMode >= 2 && event instanceof GameEventSpellAbilityCast e) {
            final StackItemView si = e.si();
            if (si != null && si.getActivatingPlayer() != null
                    && !me.equals(si.getActivatingPlayer())) {
                return NeoText.get("alert.casts",
                        PlayerName.of(si.getActivatingPlayer()),
                        si.getText() == null || si.getText().isBlank()
                            ? NeoText.get("alert.aSpell") : si.getText());
            }
            return null;
        }

        // Nivel 3: lo que NO pasa por el stack. Jugar una tierra no se puede
        // responder y casi nunca cambia nada, por eso va aparte.
        if (pauseMode >= 3 && event instanceof GameEventLandPlayed e) {
            if (e.player() != null && !me.equals(e.player())) {
                return NeoText.get("alert.playsLand",
                        PlayerName.of(e.player()), nameOfCard(e.land()));
            }
            return null;
        }

        if (event instanceof GameEventCardChangeZone e) {
            final CardView card = e.card();
            if (card == null || e.from() == null || e.to() == null || !isMine(card)) {
                return null;
            }
            if (e.from().zoneType() != ZoneType.Battlefield) {
                return null;
            }
            switch (e.to().zoneType()) {
                case Graveyard:
                    return NeoText.get("alert.died", nameOfCard(card));
                case Exile:
                    return NeoText.get("alert.exiled", nameOfCard(card));
                case Hand:
                    return NeoText.get("alert.toHand", nameOfCard(card));
                case Library:
                    return NeoText.get("alert.toLibrary", nameOfCard(card));
                default:
                    return null;
            }
        }

        if (event instanceof GameEventPlayerDamaged e) {
            if (!me.equals(e.target()) || e.amount() <= 0) {
                return null;
            }
            return e.source() == null
                    ? NeoText.get("alert.damaged", e.amount())
                    : NeoText.get("alert.damagedBy", e.amount(), nameOfCard(e.source()));
        }

        return null;
    }

    private boolean isMine(final CardView card) {
        final PlayerView me = localPlayer();
        return me != null
                && (me.equals(card.getController()) || me.equals(card.getOwner()));
    }

    private static String nameOfCard(final CardView card) {
        if (card == null) {
            return NeoText.get("alert.aCard");
        }
        final String n = forge.neo.card.CardText.nameOf(card);
        return n == null || n.isBlank() ? NeoText.get("alert.aCard") : n;
    }

    /**
     * Decide que ensenyar en el centro de la mesa, al estilo de Arena.
     *
     * <p>Tres casos, por orden:
     *
     * <ol>
     *   <li><b>Hay algo en el stack.</b> Es lo que Arena pone en mitad de la
     *       pantalla: la carta del hechizo o del disparo que esta esperando, a
     *       tamanyo de lectura, con su texto. Cuando el juego te para, lo
     *       primero que necesitas saber es de que carta viene.</li>
     *   <li><b>El motor pregunta algo concreto</b> ("elige una criatura para
     *       sacrificar"). Se ensenya la pregunta, con la carta que el motor haya
     *       marcado con {@code setCard} si la hay.</li>
     *   <li><b>Prioridad rutinaria y stack vacio.</b> No pasa nada: fuera el
     *       cartel. Antes salia igualmente y se quedaba plantado en mitad de la
     *       mesa todo tu turno.</li>
     * </ol>
     */
    private void showCentralPrompt(final String okLabel, final String cancelLabel,
                                   final boolean okEnabled, final boolean cancelEnabled) {
        final PromptBanner banner = table.getPromptBanner();

        // El aviso de "esto te acaba de pasar" MANDA sobre el espejo del stack.
        // Mientras esta puesto, el hilo del motor esta parado esperando su
        // boton: repintar aqui se lo lleva por delante y la partida se queda
        // muerta para siempre. Se vio en cuanto los eventos empezaron a llegar
        // en local — con la pausa en nivel 2 el turno 2 no pasaba de ahi.
        if (banner.isAlerting()) {
            return;
        }

        // ---- EL CARTEL CENTRAL ES UN ESPEJO DEL STACK. NADA MAS. ----
        //
        // Antes tambien repetia aqui el prompt del motor, y de ahi salieron
        // tres fallos seguidos jugando: se quedaba puesto un "Waiting for
        // IA-2..." que no se iba, tapaba las criaturas justo cuando habia que
        // declarar atacantes, y tapaba lo que el motor te estaba ensenyando.
        //
        // El problema de fondo era que dependia de cuando llegaba cada aviso y
        // de acertar con un monton de condiciones. Un espejo del stack no tiene
        // ese problema: hay algo en el stack -> se ve; se vacia -> desaparece.
        // Es ademas lo que se pidio ("la carta en grande para saber que pasa") y
        // lo que hace Arena.
        //
        // El prompt del motor vive en la barra de la derecha, que es su sitio y
        // donde siempre ha funcionado.
        final StackItemView top = topOfStack();

        // Con una seleccion en curso hay que ver la mesa para poder clicarla,
        // asi que ni siquiera el stack se pinta encima.
        if (top == null || isSelecting() || table.isModalShowing()) {
            banner.hide();
            return;
        }

        // Si el jugador ya lo ha apartado para esto que hay en el stack, no se
        // le vuelve a poner delante. Con lo siguiente que entre, si.
        final String key = String.valueOf(top.getKey());
        if (key.equals(dismissedStackKey)) {
            banner.hide();
            return;
        }

        banner.setOnDismiss(() -> dismissedStackKey = key);
        banner.showInfo(top.getSourceCard(), stackText(top));
        table.requestLayout();
    }

    /** Lo que el jugador ha apartado del centro, para no repetirselo. */
    private volatile String dismissedStackKey;

    /**
     * Repinta el cartel central con lo que haya ahora.
     *
     * <p>Hace falta porque el stack cambia con los eventos de partida, no solo
     * cuando el motor te pide algo: sin esto, un hechizo que se resuelve solo
     * dejaba su carta puesta en mitad de la mesa hasta la siguiente pregunta.
     */
    private final java.util.concurrent.atomic.AtomicBoolean promptPending =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    private void refreshCentralPrompt() {
        final UiDispatcher ui = uiDispatcher();
        if (ui == null || table == null || !interactive()) {
            return;
        }
        // Se agrupa, como hace el binder con la mesa. Un turno de la IA suelta
        // cientos de eventos en unos milisegundos: uno por evento serian
        // cientos de repintados del cartel, todos leyendo el mismo stack. El
        // que quede pendiente lee el estado de cuando le toque correr, asi que
        // agrupar no pierde nada.
        if (!promptPending.compareAndSet(false, true)) {
            return;
        }
        ui.runLater(() -> {
            promptPending.set(false);
            showCentralPrompt(null, null, false, false);
        });
    }

    /** Lo que hay encima del stack, o null si esta vacio. */
    private StackItemView topOfStack() {
        final GameView gv = getGameView();
        if (gv == null || gv.getStack() == null) {
            return null;
        }
        for (final StackItemView item : gv.getStack()) {
            return item;
        }
        return null;
    }

    /**
     * El texto de lo que hay en el stack.
     *
     * <p>Sale del script de la carta, asi que ya viene redactado y legible. Se
     * dice ademas de quien es: en una partida a cuatro, saber si el disparo lo
     * ha puesto el de enfrente o tu mismo cambia todo.
     */
    private static String stackText(final StackItemView item) {
        final StringBuilder sb = new StringBuilder();
        if (item.getActivatingPlayer() != null) {
            sb.append(PlayerName.of(item.getActivatingPlayer())).append(": ");
        }
        final String body = item.getText();
        sb.append(body == null || body.isBlank() ? NeoText.get("alert.aSpell") : body);
        return sb.toString();
    }

    /**
     * Si esto es el aviso de "la IA no puede jugar bien estas cartas".
     *
     * <p>Forge lo suelta al empezar cada partida
     * ({@code GameAction.revealUnplayableByAI}) y llega como un <i>reveal</i>,
     * asi que se come una pantalla antes de cada partida. Es util la primera
     * vez y molesto a partir de la segunda, por eso se puede silenciar en
     * Ajustes.
     *
     * <p>Se reconoce por su etiqueta, pedida al mismo {@code Localizer} que la
     * genera para que siga valiendo en cualquier idioma.
     */
    private static boolean isSilencedAiDeckWarning(final String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (!forge.neo.NeoSettings.getBool(forge.neo.NeoSettings.HIDE_AI_WARNING, false)) {
            return false;
        }
        return forge.neo.EnginePhrase.contains(message, "lblAICantPlayCards");
    }

    /**
     * Si el prompt es el rutinario de "tienes prioridad".
     *
     * <p>Forge lo compone en {@code InputBase.getTurnPhasePriorityMessage}: es
     * el volcado de estado ("Priority: X / Turn: 6 / Phase: ... / Stack: Empty")
     * que sale continuamente mientras juegas tu turno. <b>No es una pregunta</b>,
     * asi que no puede levantar el cartel central: se quedaba plantado en mitad
     * de la mesa todo el rato.
     *
     * <p>Se reconoce por como empieza, y la etiqueta se pide al mismo
     * {@code Localizer} que la genera para que siga valiendo en cualquier
     * idioma.
     */
    private static boolean isRoutinePriorityPrompt(final String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return true;
        }
        return forge.neo.EnginePhrase.startsWith(prompt, "lblPriority", ": ");
    }

    /**
     * Deja el boton del registro conectado.
     *
     * <p>El registro sale del {@code GameView}, que solo tiene la interfaz de
     * partida, asi que el cable se pone aqui y no en la mesa.
     */
    public void wireGameLogButton() {
        if (table == null) {
            return;
        }
        table.getLogButton().setOnAction(e -> {
            final GameView gv = getGameView();
            if (gv != null) {
                table.showGameLog(gv.getGameLog(), localPlayer());
            }
        });
    }

    /**
     * El jugador de abajo, el que maneja la persona.
     *
     * <p>{@code getLocalPlayers()} puede traer varios en modos raros; para esto
     * el primero es el correcto.
     */
    private PlayerView localPlayer() {
        for (final PlayerView p : getLocalPlayers()) {
            return live(p);
        }
        return null;
    }

    /** El mismo, para quien lo necesite desde fuera ({@code TableBinder}). */
    public PlayerView localPlayerView() {
        return localPlayer();
    }

    /**
     * Se puede LANZAR esta carta desde donde esta (cementerio, exilio, mando,
     * biblioteca o banda)?
     *
     * <p>Lo contesta el motor entero, sin una sola deduccion nuestra:
     * {@code ZoneType.Flashback} es una zona de mentira que Forge calcula en
     * {@code Player.getCardsActivatableInExternalZones} y publica en
     * {@code PlayerView.getFlashback()}. Ahi caen la aventura exiliada
     * ({@code isOnAdventure}), lo presagiado ({@code isForetold}), el
     * flashback, la evasion, alterar, perturbar, empezar de nuevo... y todo lo
     * que venga despues. Enumerar aqui esa lista seria firmar que se queda
     * corta la proxima vez que Card-Forge anyada un mecanismo.
     *
     * <p>Sin esto, la carta se ve en el visor de la zona y <b>no hay forma de
     * jugarla</b>: el motor no pide nada ni marca nada, simplemente espera un
     * click sobre ella igual que sobre una de la mano
     * ({@code InputPassPriority.onCardSelected} llama a
     * {@code getAllPossibleAbilities}, que ya sabe de esas zonas).
     */
    public boolean isPlayableOutside(final CardView card) {
        if (card == null || !interactive() || finished.get()) {
            return false;
        }
        final PlayerView me = localPlayer();
        final var flash = me == null ? null : me.getFlashback();
        if (flash == null) {
            return false;
        }
        for (final CardView cv : flash) {
            // Por id: los CardView que llegan por la red son copias, y la
            // identidad de objeto no vale (ver las trampas conocidas).
            if (cv != null && cv.getId() == card.getId()) {
                return true;
            }
        }
        return false;
    }

    /** Atajo: correr algo en el hilo de interfaz si lo hay. */
    private void uiRunLater(final Runnable r) {
        final UiDispatcher ui = uiDispatcher();
        if (ui != null && table != null) {
            ui.runLater(r);
        }
    }


    /**
     * La reserva de mana ha cambiado.
     *
     * <p>Es un aviso APARTE de los eventos de partida: sin escucharlo, los pips
     * de mana de la barra solo se refrescaban de rebote, cuando pasaba
     * cualquier otra cosa. Tapabas una tierra y no se veia el mana.
     */
    /** Traza la reserva con las DOS codificaciones. -Dneo.mana.debug=true */
    private static final boolean MANA_DEBUG = Boolean.getBoolean("neo.mana.debug");

    @Override
    public void updateManaPool(final Iterable<PlayerView> players) {
        if (MANA_DEBUG && players != null) {
            for (final PlayerView p : players) {
                System.out.printf(
                        "[mana] %s  W=%d U=%d B=%d R=%d G=%d | incoloro: ManaAtom(32)=%d "
                                + "MagicColor(0)=%d%n",
                        PlayerName.of(p),
                        p.getMana((byte) forge.card.mana.ManaAtom.WHITE),
                        p.getMana((byte) forge.card.mana.ManaAtom.BLUE),
                        p.getMana((byte) forge.card.mana.ManaAtom.BLACK),
                        p.getMana((byte) forge.card.mana.ManaAtom.RED),
                        p.getMana((byte) forge.card.mana.ManaAtom.GREEN),
                        p.getMana((byte) forge.card.mana.ManaAtom.COLORLESS),
                        p.getMana(forge.card.MagicColor.COLORLESS));
            }
        }
        final int now = localPoolSize();
        if (now > lastPoolSize) {
            lastManaSource = lastClicked;
        } else if (now == 0) {
            lastManaSource = null;
        }
        lastPoolSize = now;
        pushToTable();
    }

    @Override
    public void updateCurrentPlayer(final PlayerView player) {
        trace("jugador actual: %s", nameOf(player));
        pushToTable();
    }

    @Override
    public void openView(final TrackableCollection<PlayerView> myPlayers) {
        // Los ajustes que el motor necesita para que esta interfaz funcione,
        // AQUI, que es por donde pasan TODAS las partidas.
        //
        // Antes se aplicaban en NeoGame.play y en NeoGame.playPuzzle, y el
        // duelo de la aventura no pasa por ninguno de los dos: lo monta
        // QuestUtil.finishStartingGame(). Consecuencia real, reportada jugando:
        // en un duelo de la aventura, buscar una tierra en la biblioteca dejaba
        // la partida clavada — con UI_SELECT_FROM_CARD_DISPLAYS en true (que es
        // como viene) el motor espera que la GUI tenga la biblioteca PINTADA
        // como un panel, y nuestra mesa no la pinta: ni se abria nada, ni se
        // podia clicar nada, ni se activaba OK.
        //
        // openView lo llama el motor al empezar cada partida, en cualquier
        // modo, asi que un camino nuevo no puede olvidarse de esto. Y las
        // preferencias se leen en vivo (YieldController.getStringPref cae a
        // FModel.getPreferences()), asi que llegar aqui no es tarde.
        if (mode == Mode.HUMAN) {
            NeoGame.applyEnginePrefs();
            // applyEnginePrefs acaba de encender los resaltados: la marca de
            // setActionableScans tiene que decir lo mismo, o la proxima vez que
            // haya que apagarlos se creera que ya estan apagados.
            actionableScans.set(true);
        }
        // Con "Otra partida" el mismo NeoMatchUI conduce una partida nueva. Si
        // no se limpiara la marca de terminada, los clicks se seguirian
        // descartando y la segunda partida naceria muerta.
        finished.set(false);
        closeNotified.set(false);
        // El aviso de "el motor se ha muerto" es de un solo uso: si no se
        // rearma aqui, la segunda partida se rompe en silencio.
        forge.neo.platform.NeoGuiBase.clearEngineCrash();
        lastPhase = null;
        lastTurn = -1;
        log("Partida abierta. Asientos locales: %s",
                myPlayers == null ? "(ninguno, modo observador)" : myPlayers.toString());
        final TableBinder b = binder;
        if (b != null && myPlayers != null && !myPlayers.isEmpty()) {
            // Nuestro asiento va abajo, como en cualquier juego de cartas.
            b.setSelf(myPlayers.iterator().next());
        }
        // Quien somos, para que el click derecho no ensenye el morfo del
        // rival. Ver el porque en forge.neo.ui.CardZoom.localViewers.
        forge.neo.ui.CardZoom.setLocalViewers(getLocalPlayers());
        pushToTable();

        // Que una tierra de dos colores pregunte cual da, en vez de decidirlo
        // ella. Va AQUI por lo mismo que los ajustes de arriba: el duelo de la
        // aventura no pasa por NeoGame. Ver ManaColor.
        if (myPlayers != null) {
            for (final PlayerView seat : myPlayers) {
                if (ManaColor.install(getGameController(seat))) {
                    log("Tierras de dos colores: preguntaran el color (%s)",
                            PlayerName.of(seat));
                }
            }
        }

        // Y a partir de aqui, enterarnos de lo que pasa. Ver listenToTheEngine:
        // en una partida local nadie nos cuenta nada si no lo pedimos.
        listenToTheEngine();

        // ---- decirle al anfitrion como juego YO ----
        //
        // Solo tiene efecto en el invitado, y es facil de pasar por alto: el
        // motor corre en el ANFITRION, asi que quien decide si a este jugador
        // se le pasa turno solo o se le resaltan las cartas jugables es el
        // YieldController que el anfitrion tiene de nosotros. Ese controlador
        // lee sus propias preferencias... salvo que el cliente le mande las
        // suyas, que es justo lo que hace esto (SYNCED_PREFS incluye
        // YIELD_AUTO_PASS_NO_ACTIONS y UI_SHOW_ACTIONABLE_HIGHLIGHTS).
        //
        // Sin esta llamada funcionaba igual, pero POR CASUALIDAD: el anfitrion
        // caia a sus propias preferencias, que son las que aplica
        // applyEnginePrefs mas arriba. En cuanto el anfitrion fuera otra cosa,
        // o el invitado cambiara sus ajustes, se acababa la casualidad. Y las
        // paradas de fase del invitado no llegaban nunca.
        //
        // Las dos GUIs oficiales lo llaman en el mismo momento (CMatchUI y
        // MatchController, al acabar de montar la partida). En el anfitrion es
        // inofensivo: solo hace algo si hay NetGameController, que solo existe
        // en el lado del cliente.
        if (isNetGame() && getGameView() != null) {
            try {
                seedYieldStateOnHost();
            } catch (final Exception e) {
                // Que esto falle no puede llevarse la partida por delante:
                // como mucho se juega con los ajustes del anfitrion.
                System.out.println("[neo] no se han podido enviar mis ajustes al anfitrion: " + e);
            }
        }

        // "Ya empieza: enseña la mesa."
        //
        // Va AQUI y no en quien monta la partida porque este es el punto por el
        // que pasan todas (principio 8). En una partida en red hace ademas
        // falta de verdad: el lobby no avisa — onGameStarted() esta vacio en
        // las dos implementaciones de Forge — y en el invitado no hay ningun
        // codigo nuestro arrancando nada, la partida simplemente LLEGA por el
        // cable.
        final Runnable opened = onOpened;
        if (opened != null) {
            opened.run();
        }
    }

    // ---------------------------------------------------------------
    // Oir al motor
    // ---------------------------------------------------------------

    /**
     * Engancha esta interfaz al bus de eventos de la partida.
     *
     * <p><b>Sin esto, en una partida local {@code handleGameEvent} no se llama
     * NUNCA.</b> Forge suscribe al bus su propio {@code FControlGameEventHandler}
     * ({@code HostedMatch}, la rama del {@code else}), y ese llama a
     * {@code updateCards}, {@code updateLives}, {@code handleLandPlayed}… pero
     * jamas a {@code handleGameEvent}. La unica ruta que lo llamaba era la de
     * RED. O sea que todo lo que cuelga de ahi — las animaciones por evento, el
     * aviso de "esto te acaba de pasar", "pararse en el turno del rival" y el
     * espia del tutorial — estaba muerto jugando en local, y sin dar un aviso:
     * {@code run.cmd play -v} jugaba 21 turnos sin imprimir una sola linea
     * {@code evento:}.
     *
     * <p>Va en {@code openView} por el principio 8: es el punto por el que
     * pasan TODAS las partidas, las monte quien las monte — nosotros, el lobby
     * de red o {@code QuestUtil} en un duelo de la aventura.
     *
     * <p>Y no hay doble entrega: al <b>invitado</b> de una partida en red los
     * eventos ya le llegan por el cable ({@code handleGameEvents}), y ahi
     * {@code GameView.getGame()} es {@code null} — el campo es {@code transient}
     * y esa vista viaja serializada. La condicion no es un apanyo: es
     * exactamente "¿tengo la partida delante o me la estan contando?".
     */
    private void listenToTheEngine() {
        final GameView view = getGameView();
        final Game game = view == null ? null : view.getGame();
        // Null: invitado de red. Igual: segunda llamada a openView sobre la
        // misma partida, y suscribirse dos veces al mismo bus duplica TODO.
        if (game == null || game == subscribedTo) {
            return;
        }
        subscribedTo = game;
        game.subscribeToEvents(new EngineEars(this));
        trace("enganchado al bus de la partida");
    }

    /** La partida a cuyo bus ya estamos enganchados. */
    private volatile Game subscribedTo;

    /**
     * El oyente. Guava reparte por el tipo del parametro, asi que pedir
     * {@code GameEvent} los trae todos — es lo mismo que hacen el
     * {@code GameEventForwarder} y el {@code FControlGameEventHandler} de Forge.
     *
     * <p>Corre en el hilo del MOTOR y sin red debajo: Guava se traga las
     * excepciones de un oyente, pero se las traga <b>en silencio</b>, y un
     * evento perdido aqui es una animacion que no sale o un paso del tutorial
     * que no se cierra. Mejor que se vea.
     */
    private static final class EngineEars {
        private final NeoMatchUI ui;

        EngineEars(final NeoMatchUI ui) {
            this.ui = ui;
        }

        @com.google.common.eventbus.Subscribe
        public void receive(final GameEvent event) {
            try {
                ui.handleGameEvent(event);
            } catch (final RuntimeException e) {
                System.out.println("[neo] fallo atendiendo un evento del motor: " + e);
                e.printStackTrace();
            }
        }
    }

    /** Aviso de "la partida ya esta abierta". Lo usa la pantalla para enseñar la mesa. */
    private volatile Runnable onOpened;

    public void setOnOpened(final Runnable action) {
        this.onOpened = action;
    }

    /** Aviso de "la partida se ha acabado del todo". Ver {@link #afterGameEnd()}. */
    private volatile Runnable onClosed;

    public void setOnClosed(final Runnable action) {
        this.onClosed = action;
    }

    /**
     * La partida se ha terminado y hay que quitar la mesa de en medio.
     *
     * <p><b>Este es el aviso que cierra la pantalla, y es el par de
     * {@code openView}</b>: uno la abre y el otro la cierra. Las dos GUIs
     * oficiales hacen exactamente eso aqui — la Swing cierra la pestanya de la
     * partida y la de movil llama a {@code Forge.back(true)} — y nosotros no lo
     * teniamos implementado.
     *
     * <p>En una partida local no se notaba, porque ahi el que cierra es
     * {@code HostedMatch.setOnMatchOver}, que {@code NeoGame} si escucha. Pero
     * <b>el invitado de una partida en red no tiene HostedMatch</b>: el motor
     * corre en el ordenador del anfitrion. Su unico aviso viaja por el cable, y
     * es este.
     *
     * <p>Sintoma real, reportado jugando: le das a Escape → Salir, la interfaz
     * concede de verdad ({@code AbstractGuiGame.concede} manda el paquete y se
     * aparta a proposito — "que el servidor lleve el final de partida"), el
     * anfitrion termina la partida y vuelve a la sala... y el invitado se queda
     * mirando una mesa muerta, sin forma de salir. En el registro se ve
     * clavado: "Client sent concede" y cinco segundos despues
     * "Received: afterGameEnd", que no escuchaba nadie.
     *
     * <p>Se avisa una sola vez: {@code finishGame} y esto pueden llegar los
     * dos, y volver al menu dos veces se ve como un parpadeo.
     */
    @Override
    public void afterGameEnd() {
        super.afterGameEnd();
        finished.set(true);
        // La marca se pone SIEMPRE, aunque nadie escuche: es tambien lo que
        // mira el vigilante de "salir" para saber si la partida se cerro de
        // verdad.
        final boolean first = closeNotified.compareAndSet(false, true);
        final Runnable closed = onClosed;
        if (closed != null && first) {
            closed.run();
        }
    }

    /** Para no avisar dos veces de que la partida se acabo. */
    private final AtomicBoolean closeNotified = new AtomicBoolean(false);

    @Override
    public void finishGame() {
        finished.set(true);
        final GameView gv = getGameView();
        System.out.println();
        if (gv != null && gv.isGameOver()) {
            log("PARTIDA TERMINADA - gana: %s", gv.getWinningPlayerName());
        } else {
            log("PARTIDA TERMINADA");
        }

        // Al acabar la partida, Forge espera a que el jugador decida que hacer
        // (revancha o salir). Mientras no conteste, el match NO se cierra y
        // setOnMatchOver no dispara nunca.
        // Si el jugador ha pedido salir o reiniciar desde el menu de pausa, no
        // tiene sentido ensenyarle una pantalla de derrota: ya sabe lo que ha
        // hecho. Se cierra el match y NeoApp decide a donde ir.
        //
        // Con UNA excepcion, y es la aventura: ahi salirse de un duelo no es
        // "cerrar una ventana", es RENDIRSE. Cuenta como derrota — la anota el
        // motor al conceder, igual que si te hubieran matado — y el marcador
        // del cuartel general se mueve. Callarselo seria esconder la unica
        // consecuencia que tiene el boton. Asi que se dice, con la misma
        // pantalla de siempre, y de ahi al cuartel.
        if (exitAction != null) {
            if (ending == Ending.QUEST && exitAction == Exit.MENU && showGameOverScreen(false)) {
                return;
            }
            respondLater(() -> getGameController().nextGameDecision(NextGameDecision.QUIT));
            return;
        }

        if (showGameOverScreen(localPlayerWon())) {
            return;
        }
        // Aqui NUNCA hay un Bo3 de verdad esperando "siguiente partida": el
        // unico camino que arranca uno (NeoApp.playDraftMatch) usa
        // Mode.HUMAN con mesa, y eso ya se ha ido por showGameOverScreen de
        // arriba. Esto es AUTO_PLAY/OBSERVE — comprobadores sin ventana, y
        // el INVITADO de una partida en red (LobbyGuest, siempre AUTO_PLAY).
        // Justo ese ultimo caso es por lo que NO se puede mirar aqui
        // gv.isMatchOver(): en el invitado es una copia sincronizada por
        // deltas, y el aviso de "partida terminada" puede llegarle antes que
        // el delta con MatchOver=true — una carrera de red, no de partida.
        // Seguir cerrando siempre en QUIT es lo unico seguro en los dos casos
        // hasta que exista un AUTO_PLAY de Bo3 de verdad que lo necesite.
        if (mode != Mode.OBSERVE) {
            respondLater(() -> getGameController().nextGameDecision(NextGameDecision.QUIT));
        }
    }

    /**
     * Ensenya la pantalla de victoria/derrota, si hay donde ensenyarla.
     *
     * <p>Y es ademas lo que cierra el match: hasta que no se conteste,
     * {@code setOnMatchOver} no dispara.
     *
     * <p>En un Bo3 (la auditoría del motor, apartado C5) esto puede llamarse una vez por cada
     * partida del partido, no solo al final: {@code GameView.isMatchOver()}
     * dice si esta era la ultima. Si no lo era, la pantalla no ofrece "otra
     * partida" ni "volver al menu" — esos botones mienten a mitad de un Bo3
     * — sino "siguiente partida" (sigue el MISMO partido, el motor pide el
     * banquillo el solo antes de empezarla) o "rendir el partido".
     *
     * @return si se ha ensenyado (si no, quien llama tiene que cerrar el match)
     */
    private boolean showGameOverScreen(final boolean won) {
        if (mode != Mode.HUMAN || table == null) {
            return false;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui == null) {
            return false;
        }
        final GameView gv = getGameView();
        final String winner = gv == null ? null : gv.getWinningPlayerName();
        final int turns = gv == null ? 0 : gv.getTurn();
        final boolean matchOver = gv == null || gv.isMatchOver();
        final int totalGames = gv == null ? 0 : gv.getNumGamesInMatch();
        // NumPlayedGamesInMatch se congela al construir esta GameView (antes
        // de que ESTA partida empezara), asi que +1 es la que se acaba de
        // jugar — nunca la que viene.
        final int gameNumber = gv == null ? 0 : gv.getNumPlayedGamesInMatch() + 1;
        ui.runLater(() -> table.getOverlay().show(new GameOverScreen(
                won, winner, turns, ending, matchOver, gameNumber, totalGames,
                decision -> {
                    table.getOverlay().hide();
                    if (decision == NextGameDecision.CONTINUE) {
                        // Sigue el MISMO partido: NO se toca exitAction (eso
                        // es solo para "salir de verdad"), y el motor llama a
                        // sideboard() el solo antes de la proxima partida.
                        respondLater(() ->
                                getGameController().nextGameDecision(NextGameDecision.CONTINUE));
                        return;
                    }
                    // Una partida nueva se monta desde cero (NeoApp) en
                    // vez de con NextGameDecision.NEW: asi no se
                    // reaprovecha nada de la anterior y no hay estado
                    // viejo que se cuele en la siguiente.
                    exitAction = decision == NextGameDecision.NEW
                            ? Exit.RESTART : Exit.MENU;
                    respondLater(() ->
                            getGameController().nextGameDecision(NextGameDecision.QUIT));
                })));
        return true;
    }

    /**
     * Si ha ganado el jugador de esta interfaz.
     *
     * <p>Se mira por jugador y no por el nombre del ganador: en una partida a
     * cuatro puede ganar un tercero, y comparar nombres se rompe en cuanto dos
     * asientos se llaman igual.
     */
    private boolean localPlayerWon() {
        if (!hasLocalPlayers()) {
            return false;
        }
        for (final PlayerView p : getLocalPlayers()) {
            if (!live(p).getHasLost()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void showPromptMessage(final PlayerView playerView, final String message, final CardView card) {
        trace("prompt[%s]: %s", nameOf(playerView), message);
        // El aviso con "null" dentro que suelta el motor al cerrar el ataque.
        // Se tira: ver isStaleAttackPrompt.
        if (isStaleAttackPrompt(message)) {
            trace("prompt descartado (el 'null' de declarar atacantes)");
            return;
        }
        lastPrompt = message == null ? "" : message;
        // CR 903.9a: el comandante que se ha ido al cementerio o al exilio. El
        // motor lo pregunta como un si/no cualquiera y asi se pasa de largo.
        // Ver offerCommanderZone: es una decision que NO se puede deshacer.
        if (interactive() && offerCommanderZone(message)) {
            final TableBinder cb = binder;
            if (cb != null) {
                cb.setPrompt(NeoText.get("commander.leftGame"));
            }
            return;
        }
        // Adivinar y vigilar de UNA carta. Tambien llegan como un si/no
        // cualquiera y tambien hay que sacarlas fuera. Ver offerTopOrBottom.
        if (interactive() && offerTopOrBottom(message, card)) {
            return;
        }
        final TableBinder b = binder;
        if (b != null) {
            // El volcado rutinario de prioridad se cambia por uno en castellano
            // que EMPIEZA por lo que se pregunta el jugador. El del motor
            // ("Priority: Wak'dern / Turn: 9 (Wak'dern) / Phase: Main phase,
            // precombat / Stack: Empty") tiene la respuesta escondida entre
            // cuatro renglones en ingles, y era la unica forma de saber de quien
            // era el turno.
            final String shown = (isRoutinePriorityPrompt(message)
                    ? routinePrompt() : frontLoadInstruction(message))
                    + pickedSuffix();
            if (!shown.equals(message)) {
                trace("prompt reordenado: %s", shown.replace(System.lineSeparator(), " | "));
            }
            b.setPrompt(shown);
        }
        refreshCentralPrompt();
    }

    /**
     * "Te toca" y por donde va el turno, en dos renglones.
     *
     * <p>Se compone del {@code GameView}, que ya tiene todo: de quien es el
     * turno, en que fase va y que hay en la pila. La fase se traduce con la
     * misma tabla que el rail ({@code PhaseRail.nameOf}), para que las dos
     * digan siempre lo mismo.
     */
    private String routinePrompt() {
        final forge.game.GameView gv = getGameView();
        if (gv == null) {
            return "";
        }
        final PlayerView active = gv.getPlayerTurn();
        final boolean mine = active != null && isLocalPlayer(active);

        final StringBuilder sb = new StringBuilder();
        sb.append(mine ? NeoText.get("turn.yours")
                : NeoText.get("prompt.plays", nameOf(active)));
        sb.append(System.lineSeparator());
        sb.append(NeoText.get("turn.number", gv.getTurn()));
        final String phase = forge.neo.ui.PhaseRail.nameOf(gv.getPhase());
        if (!phase.isEmpty()) {
            sb.append("  ·  ").append(phase);
        }
        final int onStack = gv.getStack() == null ? 0 : gv.getStack().size();
        if (onStack > 0) {
            sb.append(System.lineSeparator())
              .append(NeoText.get("prompt.onStack", onStack));
        }
        return sb.toString();
    }

    /**
     * La marca de la pregunta de CR 903.9a, tal y como la escribe
     * {@code GameAction.stateBasedAction_Commander}.
     *
     * <p>En el motor es una cadena literal sin traducir, asi que reconocerla es
     * estable. Y si algun dia cambiara, lo unico que pasaria es que la pregunta
     * volveria a salir como el si/no generico de la barra: no se rompe nada.
     */
    private static final String COMMANDER_ZONE_MARK = ": If a commander is in a graveyard or in exile";

    /**
     * "Tu comandante ha muerto (o lo han exiliado): a donde va?"
     *
     * <p>Regla 903.9a: cuando un comandante va a parar al cementerio o al
     * exilio, su duenyo <b>puede</b> ponerlo en la zona de mando. El motor la
     * tiene implementada y nos la pregunta — pero llega como un si/no mas, con
     * el texto de reglas en ingles metido en el prompt de la barra lateral y los
     * botones puestos en "Yes"/"No". Jugando eso es invisible: se pasa de largo
     * y el comandante se queda donde esta.
     *
     * <p>Y es una decision que <b>no se puede deshacer</b>: el motor pone
     * {@code setMoveToCommandZone(false)} ANTES de preguntar, o sea que solo
     * pregunta una vez. Contestar que no por descuido deja el comandante fuera
     * de la partida para siempre. Es el principio 6 de las notas de diseño: lo que no
     * tiene vuelta atras hay que evitar que pase, no remediarlo despues.
     *
     * <p>Por eso se saca a un dialogo con la carta delante y las dos respuestas
     * escritas en cristiano, impuesto de comandante incluido (+2 genericos por
     * cada vez que ya se haya lanzado desde la zona de mando).
     *
     * @return true si nos hemos hecho cargo de la pregunta
     */
    private boolean offerCommanderZone(final String message) {
        if (message == null || table == null) {
            return false;
        }
        final int at = message.indexOf(COMMANDER_ZONE_MARK);
        if (at < 0) {
            return false;
        }
        final String name = message.substring(0, at).trim();
        final CardView commander = findCommanderAway(name);
        trace("comandante fuera de la partida: %s -> %s", name,
                commander == null ? "(NO ENCONTRADO)"
                        : commander.getName() + " en " + commander.getZone());
        final ZoneType zone = commander == null ? null : commander.getZone();
        final String where = NeoText.get(zone == ZoneType.Graveyard
                ? "commander.inGraveyard" : "commander.inExile");

        final UiDispatcher ui = uiDispatcher();
        if (ui == null) {
            return false;
        }
        final AtomicBoolean answered = new AtomicBoolean();
        ui.runLater(() -> {
            final CardPromptDialog dialog = new CardPromptDialog(
                    commander,
                    name.isEmpty() ? NeoText.get("commander.yours") : name,
                    NeoText.get("commander.ask", where),
                    List.of(NeoText.get("commander.toZone"),
                            NeoText.get("commander.leaveIt", where)),
                    0,
                    table.zoomCardWidth() * 0.62,
                    index -> {
                        // Contestar dos veces liberaria dos veces al motor.
                        if (!answered.compareAndSet(false, true)) {
                            return;
                        }
                        table.getOverlay().hide();
                        final boolean toCommand = index != null && index == 0;
                        respondLater(() -> {
                            if (toCommand) {
                                getGameController().selectButtonOk();
                            } else {
                                getGameController().selectButtonCancel();
                            }
                        });
                    });
            // El cartel del stack no puede quedarse por encima de una pregunta.
            table.getPromptBanner().hide();
            table.getOverlay().show(dialog);
            table.requestLayout();
        });
        return true;
    }

    /** El ultimo mensaje que mando el motor, tal cual. */
    private volatile String lastPrompt = "";

    /** Ya hemos avisado en esta eleccion. */
    private final AtomicBoolean noPickWarned = new AtomicBoolean();

    /**
     * "Le he dado a OK y no ha pasado nada."
     *
     * <p>Hay hechizos cuyos objetivos son <b>opcionales de verdad</b>:
     * <i>Will of the Abzan</i> dice "cualquier cantidad de oponentes objetivo"
     * y su script lleva {@code TargetMin$ 0}. El motor, entonces, deja OK
     * encendido desde el primer momento — cero objetivos es una respuesta
     * legal — y si le das sin haber clicado a nadie, el hechizo se resuelve
     * <b>sin objetivos</b>: nadie sacrifica, nadie pierde vidas, y la carta se
     * va al cementerio. Reportado jugando tal cual: <i>"la he lanzado con mi
     * comandante en mesa y no ha pasado nada"</i>.
     *
     * <p>No es un fallo del motor: es que nada avisaba de que estaba esperando
     * clicks. Y es el principio 6 de las notas de diseño — <b>lo que no se puede deshacer
     * hay que evitar que pase</b>, no remediarlo despues: un hechizo resuelto
     * es un hechizo gastado.
     *
     * <p>Como se sabe que hay una eleccion en curso y que esta vacia, sin
     * inventarse nada:
     * <ul>
     *   <li><b>Que hay eleccion:</b> {@code getSelectionMax() > 0}. Lo pone
     *       {@code InputSelectTargets} en su constructor, y lo limpia
     *       {@code clearSelectables()} al parar. Ojo: {@code isSelecting()} NO
     *       vale, porque mira si hay <i>cartas</i> elegibles y aqui lo que se
     *       elige son <b>jugadores</b>.</li>
     *   <li><b>Que es opcional:</b> {@code getSelectionMin() == 0}. Si fuera
     *       obligatoria, el motor tendria OK apagado y no se llegaria aqui.</li>
     *   <li><b>Que no has elegido nada:</b> ninguna carta marcada
     *       ({@code countPickedSelectables()}) y el prompt del motor sin su
     *       renglon {@code "Targeted:"}, que {@code InputSelectTargets} anyade
     *       <b>solo</b> cuando ya hay algo apuntado — jugadores incluidos. Es
     *       texto fijo sin traducir, asi que la comprobacion vale en cualquier
     *       idioma.</li>
     * </ul>
     *
     * @return true si nos hemos quedado la pulsacion para preguntar
     */
    private boolean warnNothingPicked() {
        if (getSelectionMax() <= 0 || getSelectionMin() != 0) {
            return false;
        }
        if (countPickedSelectables() > 0 || lastPrompt.contains(TARGETED_MARK)) {
            return false;
        }
        if (table == null || !noPickWarned.compareAndSet(false, true)) {
            return false;
        }
        final AtomicBoolean answered = new AtomicBoolean();
        table.getOverlay().show(new ConfirmDialog(
                NeoText.get("pick.noneTitle"),
                NeoText.get("pick.noneAsk"),
                List.of(NeoText.get("pick.noneBack"), NeoText.get("pick.noneAnyway")),
                0,
                index -> {
                    if (!answered.compareAndSet(false, true)) {
                        return;
                    }
                    table.getOverlay().hide();
                    if (index != null && index == 1) {
                        respondLater(() -> getGameController().selectButtonOk());
                    }
                }));
        table.requestLayout();
        return true;
    }

    /**
     * "Le he dado a OK y me he plantado en el combate sin querer."
     *
     * <p>Una fase principal con disparos se contesta a base de OK, uno por
     * disparo, y cuando se acaban <b>el boton no cambia</b>: el siguiente OK
     * — el que ya ibas a dar por inercia — pasa la prioridad con el stack
     * vacio, o sea que deja tu fase principal y te planta en el combate
     * declarando atacantes. No hay vuelta atras: el motor solo deshace lo que
     * pasa por el stack, y un cambio de fase no pasa por ahi. Es el principio
     * 6 de las notas de diseño — lo que no se puede deshacer hay que evitar que pase.
     *
     * <p>Se pregunta <b>solo</b> cuando ese OK significa de verdad "salgo de mi
     * fase principal", y eso se comprueba con lo que dice el motor, sin deducir
     * nada:
     *
     * <ul>
     *   <li><b>Que es tu fase principal:</b> {@code getPhase()} es MAIN1 o
     *       MAIN2 y el turno es tuyo. En el turno del rival pasar prioridad no
     *       te cuesta ninguna fase.</li>
     *   <li><b>Que la fase puede acabarse ahora:</b> el stack esta vacio. Con
     *       algo encima, pasar prioridad lo unico que hace es resolverlo — que
     *       es justo el OK de los disparos, el que NO hay que interrumpir.</li>
     *   <li><b>Que este OK es el de pasar prioridad</b> y no la respuesta a una
     *       pregunta: el prompt es el rutinario que compone
     *       {@code InputPassPriority.showNormalPrompt}
     *       ({@link #isRoutinePriorityPrompt}), no hay una eleccion en curso y
     *       no hay un pago de mana. Sin esto se preguntaria "quieres pasar al
     *       combate?" encima de un "quieres sacrificar?", que cae en tu propia
     *       fase principal y con el stack vacio.</li>
     * </ul>
     *
     * <p>El dialogo va <b>en medio y en grande a proposito</b>: la inercia va
     * dirigida al boton de la columna de la derecha, y una pregunta que
     * apareciera ahi mismo se contestaria con el mismo click que la provoco.
     * Por lo mismo, la respuesta marcada es la segura ("me quedo").
     *
     * @return true si nos hemos quedado la pulsacion para preguntar
     */
    private boolean confirmLeavingMain() {
        if (table == null || !interactive() || finished.get()) {
            return false;
        }
        final int mode = forge.neo.NeoSettings.confirmPhaseMode();
        if (mode <= 0) {
            return false;
        }
        final GameView gv = getGameView();
        if (gv == null) {
            return false;
        }
        final PhaseType phase = gv.getPhase();
        final boolean toCombat = phase == PhaseType.MAIN1;
        if (!toCombat && (phase != PhaseType.MAIN2 || mode < 2)) {
            return false;
        }
        if (!isLocalPlayer(gv.getPlayerTurn())) {
            return false;
        }
        if (payingMana || isSelecting() || getSelectionMax() > 0 || topOfStack() != null) {
            return false;
        }
        final String prompt = lastPrompt;
        if (prompt == null || prompt.isBlank() || !isRoutinePriorityPrompt(prompt)) {
            return false;
        }
        // Si ya hay un dialogo delante, esta pulsacion no puede venir del
        // jugador (la capa se traga los clicks y la barra espaciadora se
        // desactiva con ella). Se mira la capa en vez de guardar una marca
        // nuestra: una marca que se quedara puesta apagaria la pregunta para el
        // resto de la partida, y en silencio.
        if (table.getOverlay().isShowing()) {
            return false;
        }
        final AtomicBoolean answered = new AtomicBoolean();
        final ConfirmDialog dialog = new ConfirmDialog(
                NeoText.get(toCombat ? "phase.ask.combatTitle" : "phase.ask.endTitle"),
                NeoText.get(toCombat ? "phase.ask.combatDetail" : "phase.ask.endDetail"),
                List.of(NeoText.get("phase.ask.stay"),
                        NeoText.get(toCombat ? "phase.ask.combatGo" : "phase.ask.endGo")),
                0,
                index -> {
                    if (!answered.compareAndSet(false, true)) {
                        return;
                    }
                    table.getOverlay().hide();
                    if (index != null && index == 1) {
                        respondLater(() -> getGameController().selectButtonOk());
                    }
                });
        dialog.getStyleClass().add("phase-ask");
        // Para el piloto de pruebas: cual de los dos botones es el de seguir.
        // El piloto pulsa el primer .btn-primary que ve, y aqui el marcado es
        // "me quedo" — se quedaria dando OK y diciendo que no para siempre, sin
        // llegar nunca al combate. Marcando el otro contesta el que toca, y los
        // comprobadores pasan por esta pregunta de verdad en vez de esquivarla.
        // No vale cambiar cual esta marcado: para un humano la marcada tiene
        // que ser la segura.
        if (dialog.option(1) != null) {
            dialog.option(1).getStyleClass().add("phase-ask-go");
        }
        table.getOverlay().show(dialog);
        table.requestLayout();
        return true;
    }

    /** Lo que anyade {@code InputSelectTargets} en cuanto hay algo apuntado. */
    private static final String TARGETED_MARK = "Targeted:";

    /**
     * "Adivinas 1 (o vigilas 1): la dejas arriba o se va?"
     *
     * <p>Es la misma trampa que la del comandante, y por eso se resuelve
     * igual. El motor <b>no levanta ningun dialogo</b> para esto: cuando hay
     * una sola carta que mirar, {@code arrangeForScry} y
     * {@code arrangeForSurveil} preguntan con un {@code InputConfirm} normal y
     * corriente — texto de reglas en el prompt de la barra lateral y los dos
     * botones renombrados a "Superior"/"Fondo" o "Biblioteca"/"Cementerio".
     *
     * <p>Jugando eso es invisible: se reporto como <i>"juego una tierra que
     * adivina 1, el disparo va al stack, le doy a OK y no pasa nada, no se me
     * ensenya la carta ni nada"</i>. Y tiene toda la razon en lo de la carta —
     * con adivinar 1 no se mueve <b>nada</b> en pantalla, asi que la unica
     * forma de saber que ha ocurrido algo es ver la carta de la que va.
     *
     * <p>El motor si nos la pasa ({@code setCard} justo antes, y de nuevo en
     * este mismo aviso), asi que se saca a un dialogo con la carta grande y las
     * dos respuestas escritas en cristiano. Es la misma pieza que usan los
     * disparos, {@link CardPromptDialog}.
     *
     * <p>La deteccion va por el <b>mensaje que compone el propio motor</b>,
     * reconocido con {@link forge.neo.EnginePhrase}: se comparan los trozos
     * literales de la plantilla <b>en los diez idiomas</b> que trae Forge, no
     * la frase reconstruida con nuestro {@code Localizer}. En una partida en
     * red eso es imprescindible — la frase la escribe el ANFITRION, en SU
     * idioma — y de paso el nombre de la carta, que cada lado traduce por su
     * cuenta, cae en el hueco {@code {0}} y deja de importar. Si algun dia
     * cambiara la clave, lo unico que pasaria es que la pregunta volveria a
     * salir como el si/no generico de la barra: no se rompe nada.
     *
     * @return true si nos hemos hecho cargo de la pregunta
     */
    private boolean offerTopOrBottom(final String message, final CardView card) {
        if (message == null || card == null || table == null) {
            return false;
        }
        final boolean scry =
                forge.neo.EnginePhrase.contains(message, "lblPutCardOnTopOrBottomLibrary");
        final boolean surveil = !scry
                && forge.neo.EnginePhrase.contains(message, "lblPutCardsOnTheTopLibraryOrGraveyard");
        if (!scry && !surveil) {
            return false;
        }
        // El motor repite el aviso mientras el input sigue vivo. Contestar dos
        // veces liberaria dos veces al motor, y volver a montar el dialogo
        // encima del que ya esta puesto pierde la respuesta a medio dar.
        if (!topOrBottomUp.compareAndSet(false, true)) {
            return true;
        }
        final String ask = NeoText.get(scry ? "scry.ask" : "surveil.ask");
        final TableBinder b = binder;
        if (b != null) {
            b.setPrompt(ask);
        }

        final UiDispatcher ui = uiDispatcher();
        if (ui == null) {
            topOrBottomUp.set(false);
            return false;
        }
        final AtomicBoolean answered = new AtomicBoolean();
        ui.runLater(() -> {
            final CardPromptDialog dialog = new CardPromptDialog(
                    card,
                    null,
                    ask,
                    List.of(NeoText.get("scry.top"),
                            NeoText.get(scry ? "scry.bottom" : "surveil.grave")),
                    0,
                    table.zoomCardWidth() * 0.62,
                    index -> {
                        if (!answered.compareAndSet(false, true)) {
                            return;
                        }
                        table.getOverlay().hide();
                        topOrBottomUp.set(false);
                        final boolean top = index != null && index == 0;
                        respondLater(() -> {
                            if (top) {
                                getGameController().selectButtonOk();
                            } else {
                                getGameController().selectButtonCancel();
                            }
                        });
                    });
            // El cartel del stack no puede quedarse por encima de una pregunta.
            table.getPromptBanner().hide();
            table.getOverlay().show(dialog);
            table.requestLayout();
        });
        return true;
    }

    /** Hay un "arriba o abajo" puesto ahora mismo. */
    private final AtomicBoolean topOrBottomUp = new AtomicBoolean();

    /**
     * El comandante del que va la pregunta.
     *
     * <p>No hay que buscarlo por las zonas a mano: el motor ya publica la lista
     * en {@code PlayerView.getCommanders()}. Nos quedamos con el que este fuera
     * de la partida; el nombre solo hace falta para desempatar cuando hay dos
     * (partner, background).
     */
    private CardView findCommanderAway(final String name) {
        final GameView gv = getGameView();
        if (gv == null || gv.getPlayers() == null) {
            return null;
        }

        // 1. Por NOMBRE y mirando la zona de verdad.
        //
        // El nombre es lo unico que el motor nos ha dicho con certeza — viene
        // en su propio mensaje — y getCards(zone) es la lista real de esa zona.
        // Buscar por getCommanders() + getZone() fallaba: la vista del
        // comandante que hay en esa lista no siempre se ha enterado todavia de
        // que la carta acaba de cambiar de zona.
        for (final PlayerView p : gv.getPlayers()) {
            for (final ZoneType zone : AWAY_ZONES) {
                final CardView hit = byName(p.getCards(zone), name);
                if (hit != null) {
                    return hit;
                }
            }
        }

        // 2. Entre los comandantes declarados, ya sin mirar la zona.
        for (final PlayerView p : gv.getPlayers()) {
            final CardView hit = byName(p.getCommanders(), name);
            if (hit != null) {
                return hit;
            }
        }

        // 3. Sin carta, y NUNCA la ultima resaltada.
        //
        // Aqui habia un ultimo recurso a focusCard — la carta que el motor
        // habia marcado por lo que fuera — y eso es lo que produjo el fallo que
        // se reporto jugando: te exilian a Felothar y el dialogo te ensenya la
        // ultima criatura que lanzaste, con su arte y su texto, como si fuera
        // tu comandante. Ensenyar la carta EQUIVOCADA con esa seguridad es
        // mucho peor que no ensenyar ninguna: el titulo y el texto ya dicen de
        // que comandante se trata, y el dialogo sabe salir sin carta.
        System.err.println("[neo] no encuentro el comandante \"" + name
                + "\" fuera de la partida; el dialogo saldra sin carta");
        return null;
    }

    /** Donde puede estar un comandante que ha salido de la partida. */
    private static final ZoneType[] AWAY_ZONES = {
        ZoneType.Exile, ZoneType.Graveyard, ZoneType.Command,
    };

    /** La primera carta de la lista que se llame asi. */
    private static CardView byName(final Iterable<CardView> cards, final String name) {
        if (cards == null || name == null || name.isEmpty()) {
            return null;
        }
        for (final CardView c : cards) {
            if (c == null) {
                continue;
            }
            // El motor manda el nombre en INGLES, asi que se compara con
            // getName() y no con el traducido.
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    @Override
    public void updateButtons(final PlayerView owner, final String okLabel, final String cancelLabel,
                              final boolean okEnabled, final boolean cancelEnabled, final boolean focusOk) {
        // Lo PRIMERO: saber si hay un pago de mana en curso, y saberlo por lo
        // que dice el motor. Ver setPayingMana.
        setPayingMana(isAutoPayLabel(okLabel));
        if (interactive()) {
            // Modo humano: el motor esta esperando. Le enseñamos al jugador
            // que puede hacer y esperamos a que pulse. Las etiquetas vienen
            // del motor ("Pasar", "Terminar", "No bloquear"...), asi que leer
            // el boton basta para saber que va a pasar.
            // ---- pago automatico de mana, estilo Arena ----
            //
            // Forge ya sabe pagar solo: durante un pago, el boton OK pasa a
            // llamarse "Auto" y al pulsarlo el motor usa la logica de mana de la
            // IA para tapar lo que haga falta. Lo unico que faltaba era pulsarlo.
            //
            // Sin esto hay que ir clicando tierras a mano, que no es lo que hace
            // ni Arena, ni la mesa de verdad, ni la propia GUI vieja de Forge.
            if (payingMana && okEnabled && autoPayMana
                    && autoPayPresses.incrementAndGet() <= MAX_AUTO_PAY_PRESSES) {
                // Antes de soltarle el "Auto" al motor: apagar el rastreo de
                // cartas accionables. Ver setActionableScans().
                setActionableScans(false);
                pressAutoInAMoment();
                return;
            }

            // El "Auto" viene apagado. Antes de rendirse: si el hueco es el
            // conocido — una fuente que COBRA mana para dar mas del que cobra,
            // que el planificador del motor no contempla — se activa una, que
            // es literalmente lo que haria el jugador clicandola. Ver
            // FilterSources y tryBlindSource().
            if (payingMana && !okEnabled && autoPayMana && tryBlindSource()) {
                return;
            }

            // Aqui no vamos a pulsar nosotros: si el jugador tiene que pagar a
            // mano, necesita ver las fuentes resaltadas.
            setActionableScans(true);

            // Y si el "Auto" viene APAGADO, decir por que.
            noteAutoPayState(okEnabled);

            final UiDispatcher ui = uiDispatcher();
            if (ui != null) {
                ui.runLater(() -> {
                    table.getActionBar().setButtons(okLabel, cancelLabel, okEnabled, cancelEnabled);
                    table.getActionBar().setOnOk(() -> {
                        // Antes de dar por buena una eleccion VACIA, preguntar.
                        if (warnNothingPicked()) {
                            return;
                        }
                        // Y antes de dejar tu fase principal, tambien.
                        if (confirmLeavingMain()) {
                            return;
                        }
                        respondLater(() -> getGameController().selectButtonOk());
                    });
                    table.getActionBar().setOnCancel(() ->
                            respondLater(() -> getGameController().selectButtonCancel()));

                    // Y en el centro de la mesa, GRANDE y con la carta, lo que
                    // esta pasando. Sin botones: los de verdad son estos de
                    // arriba, en la barra de la derecha.
                    showCentralPrompt(okLabel, cancelLabel, okEnabled, cancelEnabled);
                    table.requestLayout();
                    table.setSelectable(this::isSelectable);
                    // Con una seleccion en curso, cada ficha tiene que poder
                    // clicarse por separado: apiladas, siempre se elige la
                    // primera y no hay forma de senyalar la segunda.
                    table.setGroupingEnabled(!isSelecting());
                    // Si no hay cartas elegibles pero si hay que contestar algo,
                    // puede que lo que se pida sea un jugador. Dejarlos clicables
                    // siempre es inofensivo: el motor ignora lo que no toca.
                    table.setPlayersSelectable(true);
                });
            }
            return;
        }

        if (mode != Mode.AUTO_PLAY || finished.get()) {
            return;
        }
        // AQUI ESTA EL MODELO BLOQUEANTE.
        // El motor nos acaba de decir "hay botones activos" y se ha quedado
        // esperando. Hay que contestar desde OTRO hilo: si llamamos a
        // selectButtonOk() aqui mismo, reentramos en el motor por el mismo
        // hilo que esta bloqueado. En la fase 4 este disparo lo hara el click
        // del usuario en vez de este executor.
        if (okEnabled) {
            respondLater(() -> getGameController().selectButtonOk());
            return;
        }

        // Un pago atascado tambien se intenta aqui, y no solo en el modo
        // humano: es lo unico que permite comprobar el bucle entero sin
        // ventana (FilterCheck). No toca a los otros comprobadores porque el
        // interruptor viene apagado y ninguno lo enciende.
        if (payingMana && autoPayMana && tryBlindSource()) {
            return;
        }

        // OK deshabilitado puede significar dos cosas muy distintas:
        // (a) hay una seleccion de cartas en curso y faltan cartas por elegir
        // (b) no hay nada que hacer salvo cancelar
        if (isSelecting()) {
            final CardView next = nextUnpickedSelectable();
            if (next != null) {
                trace("eligiendo carta: %s", next);
                respondLater(() -> getGameController().selectCard(next, null, null));
                return;
            }
        }

        // (c) el motor quiere que elijas algo, pero NO es una carta.
        //
        // El caso de verdad: con tres o mas jugadores, quien gana el sorteo
        // inicial tiene que decir quien empieza, y el motor lo pide con
        // "(Haz clic en el retrato)" -- sin marcar ni una carta como elegible.
        // Jugando esta resuelto porque los retratos se clican; en automatico no
        // habia respuesta posible y la partida se quedaba clavada en el turno 0
        // <b>la mitad de las veces</b>, segun cayera la moneda. Un comprobador
        // que falla a cara o cruz es peor que no tenerlo: se acaba dando por
        // bueno el fallo.
        //
        // Se mira getSelectionMax() y no isSelecting(), que es la trampa de
        // siempre: isSelecting() pregunta si hay CARTAS elegibles, y aqui lo
        // que se elige es un jugador.
        if (getSelectionMax() > 0 && countPickedSelectables() < getSelectionMin()
                && selectables.isEmpty()) {
            final PlayerView who = localPlayer();
            if (who != null) {
                trace("eligiendo jugador: %s", nameOf(who));
                respondLater(() -> getGameController().selectPlayer(who, null));
                return;
            }
        }

        if (cancelEnabled) {
            respondLater(() -> getGameController().selectButtonCancel());
        }
    }

    /**
     * Cartas que el motor ha marcado como elegibles ahora mismo.
     *
     * <p>Se guardan aqui porque {@code setSelectables} se invoca desde el
     * CONSTRUCTOR del input, antes de que ese input este activo en la cola: si
     * contestamos en ese momento, el click se descarta y la partida se queda
     * colgada. Un humano nunca lo nota porque tarda 100 ms en hacer click.
     * Por eso la eleccion se dispara desde {@code updateButtons}, que si ocurre
     * con el input ya vivo.
     */
    /**
     * Tope de pulsaciones automaticas por pago.
     *
     * <p>Red de seguridad: {@code ComputerUtilMana} paga el coste entero de una
     * vez, asi que con una basta. Si por lo que sea no avanzara, el tope evita
     * que la interfaz se quede dandole al boton para siempre y le devuelve el
     * control al jugador.
     */
    private static final int MAX_AUTO_PAY_PRESSES = 4;

    /**
     * Hay un pago de mana en curso.
     *
     * <p><b>Esto se decide por la ETIQUETA DEL BOTON, y es importante que sea
     * asi.</b> La primera version lo deducia de {@code showManaPool} /
     * {@code hideManaPool}, que parecia lo natural — son los avisos que el
     * motor manda al empezar y al acabar un pago — y estaba mal por los dos
     * lados:
     *
     * <ul>
     *   <li>{@code showManaPool} solo se llama <b>si ya tenias mana flotante</b>
     *       cuando empezo el pago ({@code InputPayMana}: {@code wasFloatingMana}).
     *       En un pago normal no llega nunca.</li>
     *   <li>{@code hideManaPool} solo se llama cuando el pago se <b>aborta</b>
     *       ({@code onStop} con {@code !isFinished()}). Cuando el pago termina
     *       bien, no llega — o sea que la marca <b>se quedaba encendida para
     *       siempre</b>.</li>
     * </ul>
     *
     * <p>Y con la marca encendida, este metodo pulsaba OK <b>solo</b> en los
     * cuatro avisos siguientes, fueran de lo que fueran. El sintoma que lo
     * delato, jugando: <i>"jugue una tierra que adivina 1, el disparo fue al
     * stack, le di a OK y el adivinar no se hizo"</i>. Si se hizo — el motor
     * pregunta "arriba o abajo" con un {@code InputConfirm} normal y corriente,
     * y la interfaz contesto "arriba" ella sola antes de ensenyar nada. Con
     * adivina 1 no se mueve nada en pantalla, asi que parece que no paso.
     *
     * <p>La senyal buena la publica el propio {@code InputPayMana}: mientras
     * dura el pago, y <b>solo</b> mientras dura, el boton principal se llama
     * "Auto" ({@code lblAuto}). Se reevalua en cada {@code updateButtons}, asi
     * que no se puede quedar pegada.
     */
    private volatile boolean payingMana;

    /** Cuantas veces seguidas hemos pulsado Auto en este pago. */
    private final AtomicInteger autoPayPresses = new AtomicInteger();

    /** El "Auto" del motor, en el idioma que sea. */
    private static boolean isAutoPayLabel(final String okLabel) {
        return forge.neo.EnginePhrase.equalsAny(okLabel, "lblAuto");
    }

    private void setPayingMana(final boolean on) {
        // Cualquier aviso nuevo del motor invalida la pulsacion de "Auto" que
        // estuviera esperando: el estado sobre el que se decidio ya cambio.
        autoPayGen.incrementAndGet();
        if (on && !payingMana) {
            // Empieza un pago: se reinicia el tope de pulsaciones.
            autoPayPresses.set(0);
            blindTries.set(0);
            blindTried.clear();
        }
        if (!on) {
            // Se acabo el pago: lo que estuviera pendiente de avisar, ya no.
            autoPayHint.incrementAndGet();
            // Y vuelven los resaltados. Ver setActionableScans().
            setActionableScans(true);
            // Y si algun cambio se quedo sin llegar a la pantalla durante el
            // pago, que se vuelque. Ver flushStrandedViewChanges().
            needTrackerFlush.set(true);
        }
        payingMana = on;
    }

    /**
     * Estan encendidos los resaltados de "que puedes usar ahora mismo".
     *
     * <p>Se apaga a proposito mientras el "Auto" esta pagando. Ver
     * {@link #setActionableScans(boolean)}.
     */
    private final AtomicBoolean actionableScans = new AtomicBoolean(true);

    /**
     * <b>Silencio en el hilo de interfaz mientras el "Auto" paga.</b>
     *
     * <p>Este es el arreglo de un fallo que se llevaba partidas enteras por
     * delante, y que en pantalla no se parecia en nada a lo que era. Los dos
     * sintomas que se reportaron jugando:
     *
     * <ul>
     *   <li><i>"lance una carta de 4 con {2} de offspring, pague 6, y solo se
     *       me giraron 2 tierras"</i> — las seis estaban giradas <b>en el
     *       motor</b>; lo que no llego fue el aviso a la pantalla.</li>
     *   <li><i>"al turno siguiente se peto la IA"</i> — la partida muerta, con
     *       el cartel de "la partida se ha roto".</li>
     * </ul>
     *
     * <p><b>Lo que pasa de verdad.</b> El {@code Tracker} del motor —el que
     * lleva la cuenta de que propiedades han cambiado para volcarlas a las
     * vistas— lo dice el en su propio javadoc: <i>"Not thread-safe — game
     * thread only"</i>. Y durante un pago de mana hay <b>dos</b> hilos dentro:
     *
     * <ol>
     *   <li>El del pago. {@code InputPayMana.onOk} lanza
     *       {@code ComputerUtilMana.payManaCost} con
     *       {@code GameAction.invoke}, o sea en un hilo de partida aparte.</li>
     *   <li>El de la interfaz. Mientras aquello paga, el motor va refrescando
     *       el mensaje del pago, y ese refresco se hace <b>en el hilo de la
     *       interfaz</b> ({@code InputPayMana.updateMessage} llega por
     *       {@code invokeInEdtLater}). Y no es solo pintar: por el camino llama
     *       a {@code pushActionableCards}, que para saber que cartas puedes
     *       usar recorre tus zonas preguntandole a cada carta por sus
     *       habilidades — y eso acaba en {@code checkStaticAbilities}, que
     *       congela y descongela el Tracker.</li>
     * </ol>
     *
     * <p>Los dos a la vez sobre la misma lista, y salta la
     * {@code ConcurrentModificationException} que hay a pares en {@code
     * neo.log}. Y lo peor no es la excepcion: es que revienta <b>a mitad</b> de
     * {@code Tracker.unfreeze}, que es justo el bucle que aplica los cambios
     * pendientes a las vistas. Los que quedaban detras no se aplican y ademas
     * no se limpian, asi que se quedan ahi para el siguiente descongelado. De
     * ahi las tierras que se ven sin girar.
     *
     * <p><b>Por que nos toca a nosotros y no a la GUI vieja.</b> Porque
     * nosotros pulsamos "Auto" <b>al instante</b>, en cuanto el motor enciende
     * el boton. Un humano tarda medio segundo, y en medio segundo el refresco
     * de la interfaz ya ha terminado. Es otra vez la regla de siempre: el motor
     * da por hecho que al otro lado hay alguien con reflejos humanos.
     *
     * <p><b>El arreglo.</b> No se puede tocar el motor (regla de oro), pero ese
     * recorrido si tiene interruptor: el propio Forge lo consulta en
     * {@code FPref.UI_SHOW_ACTIONABLE_HIGHLIGHTS} antes de mirar una sola
     * carta, y sin el se va de {@code pushActionableCards} sin entrar. Asi que
     * se apaga justo mientras el "Auto" esta pagando y se vuelve a encender en
     * cuanto el pago acaba —o en cuanto queda claro que va a pagar el jugador a
     * mano, que es cuando los resaltados hacen falta de verdad—. Durante un
     * pago automatico no se pierde nada: resaltar las tierras que puedes tocar
     * mientras las esta tocando otro no le sirve a nadie.
     *
     * <p>Se cambia <b>solo en memoria</b>, como todo lo que tocamos de las
     * preferencias de Forge (ver {@code NeoGame.applyEnginePrefs}).
     */
    /**
     * <b>Rescatar los cambios que se quedaron sin llegar a la pantalla.</b>
     *
     * <p>Es la red de seguridad del fallo de las tierras que se ven sin girar
     * (ver {@link #setActionableScans(boolean)}). Si el {@code Tracker} revienta
     * a mitad de {@code unfreeze}, los cambios que quedaban detras <b>ni se
     * aplican ni se borran</b>: se quedan en la lista, y la carta se sigue
     * viendo como estaba.
     *
     * <p>Sacarlos de ahi es un {@code freeze()} + {@code unfreeze()}: el
     * segundo baja el contador a cero, vuelca la lista entera y la limpia. Los
     * dos metodos son publicos, o sea que no hay que tocar el motor.
     *
     * <p>Cuesta nada cuando no hay nada atascado — {@code unfreeze} se va en la
     * primera linea si la lista esta vacia — y si otro sitio esta dentro de su
     * propio freeze, subir y bajar el contador no le afecta.
     *
     * <p>Va aqui, en {@code handleGameEvent}, porque este es el <b>hilo del
     * motor</b>, que es el unico desde el que el Tracker se puede tocar. Y solo
     * despues de un pago de mana, que es el unico sitio donde se ha visto
     * pasar: hacerlo en cada evento seria trabajo en balde en el camino
     * caliente de la partida.
     */
    private void flushStrandedViewChanges() {
        if (!needTrackerFlush.compareAndSet(true, false)) {
            return;
        }
        final GameView gv = getGameView();
        final Game game = gv == null ? null : gv.getGame();
        if (game == null || game.getTracker() == null) {
            return;
        }
        try {
            game.getTracker().freeze();
            game.getTracker().unfreeze();
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se han podido volcar los cambios pendientes: " + e);
        }
    }

    /** Hay que volcar lo que se quedara atascado. Ver {@link #flushStrandedViewChanges()}. */
    private final AtomicBoolean needTrackerFlush = new AtomicBoolean();

    private void setActionableScans(final boolean on) {
        if (actionableScans.get() == on) {
            return;
        }
        actionableScans.set(on);
        try {
            forge.model.FModel.getPreferences().setPref(
                    forge.localinstance.properties.ForgePreferences.FPref
                            .UI_SHOW_ACTIONABLE_HIGHLIGHTS, on);
        } catch (final RuntimeException e) {
            // Sin preferencias cargadas (pruebas sin motor) no hay nada que hacer.
            actionableScans.set(true);
        }
        if (!on) {
            // Lo que hubiera resaltado deja de tener sentido: durante el pago
            // automatico no se toca nada a mano.
            clearWeaklySelectable();
        }
    }

    /**
     * El "Auto" apagado <b>quiere decir algo</b>, y hay que decirlo.
     *
     * <p>Durante un pago de mana el motor solo enciende el boton si su
     * planificador — el de la IA, {@code ComputerUtilMana.getManaSourcesToPayCost}
     * — encuentra un plan completo. Ese planificador <b>no es exhaustivo</b>:
     * hay combinaciones que un jugador ve y el no, sobre todo con tierras que
     * cobran mana para dar mas del que cobran (las de filtro: {1},{T}: añade
     * {W}{U}). Reportado jugando: <i>"el auto me decia que no y luego yo a mano
     * haciendo la logica podia"</i>.
     *
     * <p>El fallo de interfaz no era ese — la logica del motor no la vamos a
     * reescribir, y menos por nuestra cuenta (regla de oro) — sino que <b>el
     * boton se quedaba gris sin explicar nada</b>, y un boton gris se lee como
     * "esto no se puede hacer" cuando en realidad significa "esto no lo se
     * hacer yo; hazlo tu". Con las fuentes resaltadas (ver
     * {@link #setWeaklySelectable}) hacerlo a mano es cuestion de clicar.
     *
     * <p><b>Por que con retardo.</b> La secuencia del motor pasa siempre por un
     * "Auto" apagado antes de encenderlo: {@code InputPayMana.showMessage} llama
     * a {@code updateButtons} (apagado) y solo despues {@code updateMessage} lo
     * enciende, si hay plan. Avisar en el primero saldria en TODOS los pagos.
     * Se espera medio segundo y, si mientras tanto llega el encendido, el aviso
     * se cancela solo por el numero de generacion.
     */
    private void noteAutoPayState(final boolean okEnabled) {
        if (!payingMana || !autoPayMana) {
            return;
        }
        final int mine = autoPayHint.incrementAndGet();
        if (okEnabled) {
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui == null || table == null) {
            return;
        }
        // El reloj es el de Forge y no un PauseTransition: los comprobadores
        // sin ventana llegan hasta aqui, y JavaFX sin toolkit levantado
        // revienta con "Toolkit not initialized" (ver las trampas conocidas).
        // Si el hueco es el conocido — una fuente que cobra mana para dar mas
        // del que cobra — se NOMBRA. "No encuentra la combinacion" no dice que
        // hacer; "no sabe usar Pradera Hierbasol" dice exactamente que clicar.
        final String culprit = blindSourceName();
        forge.util.ThreadUtil.delay(500, () -> ui.runLater(() -> {
            if (autoPayHint.get() == mine && payingMana) {
                table.getActionBar().setWarning(culprit == null
                        ? NeoText.get("mana.autoStuck")
                        : NeoText.get("mana.autoStuckSource", culprit), false);
            }
        }));
    }

    /** Numero de generacion del aviso de "el Auto no puede". */
    private final AtomicInteger autoPayHint = new AtomicInteger();

    /**
     * Si el pago automatico intenta por su cuenta las fuentes que el motor no
     * ve. Apagado de fabrica (la auditoría del motor 1.1).
     */
    private static volatile boolean blindSourceFallback;

    public static void setBlindSourceFallback(final boolean on) {
        blindSourceFallback = on;
    }

    public static boolean isBlindSourceFallback() {
        return blindSourceFallback;
    }

    /** Cuantas fuentes ciegas se han probado en ESTE pago. */
    private final AtomicInteger blindTries = new AtomicInteger();

    /**
     * Tope de intentos por pago. Cada intento gira una tierra, y girar no se
     * deshace desde aqui: si con cuatro no ha salido, el plan era malo y el
     * jugador se apanya mejor a mano.
     */
    private static final int MAX_BLIND_TRIES = 4;

    /** Las cartas que ya se han probado en este pago, por id. */
    private final java.util.Set<Integer> blindTried =
            java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /** El nombre de la primera fuente que el motor no sabe usar, o null. */
    private String blindSourceName() {
        final forge.game.card.Card c = firstBlindSource();
        return c == null ? null : forge.neo.card.CardText.nameOf(c.getView());
    }

    /**
     * La primera fuente de mana que cuesta mana y que aun no hemos probado en
     * este pago.
     *
     * <p>Devuelve {@code null} si no hay partida local: en una partida en red
     * el {@code Game} vive en el anfitrion y aqui solo llegan vistas. Es el
     * caso en el que todo esto se apaga solo y la pantalla se queda como
     * estaba (la auditoría del motor 1.1).
     */
    private forge.game.card.Card firstBlindSource() {
        final GameView gv = getGameView();
        final Game game = gv == null ? null : gv.getGame();
        if (game == null) {
            return null;
        }
        forge.game.player.Player me = null;
        for (final PlayerView seat : getLocalPlayers()) {
            for (final forge.game.player.Player p : game.getPlayers()) {
                if (p.getView() == seat) {
                    me = p;
                }
            }
        }
        for (final forge.game.card.Card c : FilterSources.of(game, me)) {
            if (!blindTried.contains(c.getId())) {
                return c;
            }
        }
        return null;
    }

    /**
     * Activa una fuente de las que el motor no ve, como si la clicara el
     * jugador.
     *
     * <p><b>Por que esto funciona.</b> El planificador se atraganta con el
     * coste ENTERO ({@code {G}{W}} con una tierra de filtro), pero el sub-pago
     * que abre la propia tierra — su {@code {1}} — es un coste generico
     * corriente que su "Auto" si sabe pagar. O sea que basta con dar el primer
     * paso, que es justo el que un humano da a mano, y el motor se encarga del
     * resto por el camino de siempre.
     *
     * @return si se ha activado algo (y por tanto hay que esperar al motor)
     */
    private boolean tryBlindSource() {
        if (!blindSourceFallback || blindTries.get() >= MAX_BLIND_TRIES) {
            return false;
        }
        final forge.game.card.Card c = firstBlindSource();
        if (c == null) {
            return false;
        }
        blindTries.incrementAndGet();
        blindTried.add(c.getId());
        trace("pago: probando una fuente que el motor no ve: %s", c);
        final CardView view = c.getView();
        respondLater(() -> getGameController().selectCard(view, null, null));
        return true;
    }

    /**
     * Cuanto se espera antes de pulsar "Auto", en milisegundos.
     *
     * <p><b>Este numero es un arreglo, no una preferencia estetica.</b> Pulsar
     * en el mismo instante en que el motor enciende el boton mete nuestro pago
     * dentro del refresco que el propio motor esta haciendo en el hilo de
     * interfaz, y de ahi salen las {@code ConcurrentModificationException} que
     * dejaban tierras sin girar y llegaban a matar la partida (ver
     * {@link #setActionableScans(boolean)}). Un jugador de carne tarda medio
     * segundo largo y por eso a la GUI vieja no le pasa.
     *
     * <p>Un cuarto de segundo es de sobra para que el refresco termine y no se
     * nota jugando. Se puede tocar con {@code -Dneo.autoPay.delayMs} para
     * probar.
     */
    private static final long AUTO_PAY_DELAY_MS =
            Long.getLong("neo.autoPay.delayMs", 250);

    /**
     * Pulsa "Auto", pero <b>dejando respirar al motor primero</b>.
     *
     * <p>El numero de generacion es lo que evita pulsar de mas: si mientras
     * esperamos llega otro {@code updateButtons} — porque el pago avanzo, o
     * porque ya no hay pago — la pulsacion que estaba en el aire se descarta
     * sola en vez de caer sobre un estado que ya no existe.
     */
    private void pressAutoInAMoment() {
        final int mine = autoPayGen.get();
        if (AUTO_PAY_DELAY_MS <= 0) {
            respondLater(() -> getGameController().selectButtonOk());
            return;
        }
        // El reloj es el de Forge (un hilo "Delayed-N") y no un
        // PauseTransition: los comprobadores sin ventana llegan hasta aqui y
        // JavaFX sin toolkit levantado revienta con "Toolkit not initialized".
        // De la vuelta al hilo de interfaz ya se encarga respondLater.
        forge.util.ThreadUtil.delay((int) AUTO_PAY_DELAY_MS, () -> {
            if (autoPayGen.get() != mine || !payingMana || !autoPayMana) {
                return;
            }
            respondLater(() -> getGameController().selectButtonOk());
        });
    }

    /** Generacion de la pulsacion de "Auto" que esta en el aire. */
    private final AtomicInteger autoPayGen = new AtomicInteger();

    /** Ajuste: pagar el mana solo. Encendido por defecto, como en Arena. */
    private volatile boolean autoPayMana = true;

    public void setAutoPayMana(final boolean on) {
        this.autoPayMana = on;
        if (!on) {
            // Si el jugador apaga el pago automatico a mitad de un pago, los
            // resaltados le hacen falta YA: es el quien va a tocar las tierras.
            setActionableScans(true);
        }
    }

    private volatile List<CardView> selectables = List.of();

    @Override
    public void setSelectables(final Iterable<CardView> cards, final int min, final int max) {
        super.setSelectables(cards, min, max);
        final List<CardView> list = new ArrayList<>();
        if (cards != null) {
            cards.forEach(list::add);
        }
        selectables = list;
        noPickWarned.set(false);
        trace("setSelectables: %d elegibles (min %d, max %d)", list.size(), min, max);
    }

    @Override
    public void clearSelectables() {
        super.clearSelectables();
        selectables = List.of();
        noPickWarned.set(false);
    }

    /**
     * El motor marca y desmarca entidades: el defensor elegido, el atacante
     * sobre el que estas asignando bloqueos, las cartas ya escogidas de una
     * seleccion. Sin pintarlo, el combate se juega a ciegas.
     */
    @Override
    public void setHighlighted(final Iterable<GameEntityView> entities, final boolean on) {
        super.setHighlighted(entities, on);
        refreshHighlights();
    }

    private void refreshHighlights() {
        if (!interactive()) {
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui != null) {
            ui.runLater(() -> {
                table.setHighlighted(this::isHighlightedEntity);
                // Y el "llevas N de M", que cambia con cada click. Sin
                // repintarlo aqui solo se actualizaria cuando el motor mandara
                // un prompt nuevo, y durante una eleccion no manda ninguno.
                final TableBinder b = binder;
                if (b != null) {
                    b.setPrompt((isRoutinePriorityPrompt(lastPrompt)
                            ? routinePrompt() : lastPrompt) + pickedSuffix());
                }
            });
        }
    }

    /**
     * El aviso roto que el motor manda al terminar de declarar atacantes.
     *
     * <p>Es un fallo del motor, no de una carta ni nuestro.
     * {@code InputAttack.onOk()} — el boton con el que cierras el ataque —
     * llama a {@code setCurrentDefender(null)} "para quitar los resaltados", y
     * esa funcion termina SIEMPRE llamando a {@code updateMessage()}, que
     * compone la frase asi:
     *
     * <pre>
     *   lblSelectAttackCreatures + " " + currentDefender + " " + lblSelectAttackTarget
     * </pre>
     *
     * <p>Con el defensor ya a {@code null}, o sea que sale <i>"Selecciona las
     * criaturas para atacar a <b>null</b> o selecciona al jugador/planeswalker
     * que quieres atacar"</i> — justo cuando acabas de terminar de declararlos
     * y ya no hay nada que elegir. Pasa en los diez idiomas y le pasa igual a
     * las dos GUIs oficiales.
     *
     * <p>Por la regla de oro no se toca alli: se reconoce y se tira, y el
     * siguiente aviso de verdad llega solo. Se reconoce con
     * {@link forge.neo.EnginePhrase} para que valga venga en el idioma que venga — en
     * una partida en red ese texto lo escribe el anfitrion, en SU idioma — y
     * ademas se exige el {@code " null "} literal, que es lo que de verdad
     * distingue el aviso roto del bueno: el bueno lleva ahi el nombre del
     * defensor.
     *
     * <p>(Si algun dia un jugador se llama exactamente "null", su aviso bueno
     * se tirara tambien. Se asume.)
     */
    private boolean isStaleAttackPrompt(final String message) {
        return message != null
                && message.contains(" null ")
                && forge.neo.EnginePhrase.contains(message, "lblSelectAttackCreatures");
    }

    /**
     * Pone <b>delante</b> lo que el motor te esta pidiendo AHORA.
     *
     * <p>Cuando hay que elegir objetivo, {@code InputSelectTargets} compone el
     * aviso siempre igual: primero la carta y el texto de la habilidad,
     * despues una linea en blanco, y al final la instruccion concreta
     * ({@code TgtPrompt$} del script). Con una carta de varios modos eso sale
     * al reves de como hace falta.
     *
     * <p>El caso que lo destapo es Shadrix Silverquill: eliges DOS modos y
     * cada uno tiene que apuntar a un jugador distinto. El aviso quedaba asi:
     *
     * <pre>
     *   Shadrix (203) - - El jugador objetivo crea una ficha...  - El jugador
     *                     objetivo roba una carta y pierde 1 vida.
     *
     *   Select target player to create a 2/1 ... inkling ...
     * </pre>
     *
     * O sea: arriba y en castellano, <b>los dos</b> modos que ya elegiste — que
     * no distinguen nada — y abajo del todo, la unica linea que dice cual te
     * esta pidiendo. Encima esa linea va en <b>ingles</b>, porque
     * {@code TgtPrompt$} es texto suelto del script de la carta y Forge no lo
     * traduce en ningun idioma. Reportado jugando: <i>"no me queda claro que
     * habilidad va a ir a quien"</i>.
     *
     * <p>Aqui se le da la vuelta: la instruccion primero, el recordatorio
     * detras. Lo que cambia entre el primer objetivo y el segundo es
     * justamente esa linea, asi que ponerla arriba es lo que hace que se
     * distingan. Si el aviso no tiene esa forma se devuelve tal cual: no
     * inventamos nada sobre un texto que no reconocemos.
     */
    static String frontLoadInstruction(final String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        final java.util.regex.Matcher m =
                BLANK_LINE.matcher(message);
        if (!m.find()) {
            return message;
        }
        final String recap = message.substring(0, m.start()).trim();
        final String instruction = message.substring(m.end()).trim();
        if (recap.isEmpty() || instruction.isEmpty()) {
            return message;
        }
        return instruction + System.lineSeparator() + recap;
    }

    private static final java.util.regex.Pattern BLANK_LINE =
            java.util.regex.Pattern.compile("\\R[ \\t]*\\R");

    /**
     * "Elegidas 2 de 3", cuando hay una eleccion de varias cartas en curso.
     *
     * <p>El motor lleva la cuenta y la publica ({@code countPickedSelectables},
     * {@code getSelectionMin/Max}); lo que faltaba era ensenyarla. Con un
     * "sacrifica tres criaturas" no habia forma de saber por cual ibas, y el
     * marco dorado de la carta dice <i>cual</i> pero no <i>cuantas te quedan</i>.
     *
     * <p>Solo con mas de una: para elegir UNA sola, la eleccion se cierra en
     * cuanto clicas y el contador seria un parpadeo.
     */
    private String pickedSuffix() {
        if (!isSelecting()) {
            return "";
        }
        final int need = getSelectionMin() > 0 ? getSelectionMin() : getSelectionMax();
        if (need <= 1) {
            return "";
        }
        return System.lineSeparator()
                + NeoText.get("prompt.picked", countPickedSelectables(), need);
    }

    private boolean isHighlightedEntity(final Object entity) {
        return entity instanceof GameEntityView view && isHighlighted(view);
    }

    /**
     * El motor esta esperando que cliques un JUGADOR, no una carta.
     *
     * <p>El caso claro: con tres o mas jugadores, quien gana el sorteo inicial
     * dice quien empieza, y el motor lo pide con "(Haz clic en el retrato)"
     * sin marcar ni una carta como elegible.
     *
     * <p>Se mira {@code getSelectionMax()} y no {@code isSelecting()}, que es
     * la trampa de siempre: {@code isSelecting()} pregunta si hay CARTAS
     * elegibles, y aqui lo que se elige es un jugador.
     *
     * <p>Lo usa el piloto de pruebas ({@code --autopilot}): sin esto se queda
     * dandole al OK, que ahi no hace nada, y la partida no arranca <b>la mitad
     * de las veces</b> — segun caiga la moneda.
     */
    public boolean isWaitingForPlayerPick() {
        return getSelectionMax() > 0
                && countPickedSelectables() < getSelectionMin()
                && selectables.isEmpty();
    }

    /** Primera carta elegible que aun no se ha elegido. */
    private CardView nextUnpickedSelectable() {
        if (countPickedSelectables() >= getSelectionMin()) {
            return null;
        }
        for (final CardView cv : selectables) {
            if (!isHighlighted(cv)) {
                return cv;
            }
        }
        return null;
    }

    /**
     * Manda una respuesta al motor desde el hilo de interfaz.
     *
     * <p>Es exactamente lo que hace Swing: el click del boton se procesa en el
     * EDT, y desde ahi se libera el latch que tiene bloqueado al motor. Hacerlo
     * desde un hilo cualquiera funciona, pero va mucho mas lento por contencion.
     * En la fase 4 quien llame a esto sera el manejador del click del usuario.
     */
    private void respondLater(final Runnable action) {
        decisions.incrementAndGet();
        final Runnable safe = () -> {
            try {
                action.run();
            } catch (final Exception e) {
                // El motor puede haber avanzado ya; no es fatal.
                trace("respuesta descartada: %s", e.getClass().getSimpleName());
            }
        };
        final UiDispatcher ui = uiDispatcher();
        if (ui != null) {
            ui.runLater(safe);
        } else {
            try {
                responder.execute(safe);
            } catch (final Exception ignored) {
                // executor cerrado
            }
        }
    }

    private static UiDispatcher uiDispatcher() {
        final Object base = GuiBase.getInterface();
        return base instanceof NeoGuiBase ? ((NeoGuiBase) base).getDispatcher() : null;
    }

    // ===============================================================
    // Modo humano (fase 4)
    // ===============================================================

    /**
     * Pide algo al usuario y BLOQUEA el hilo del motor hasta que conteste.
     *
     * <p>Este es el primitivo sobre el que se apoya toda la interaccion. El
     * motor llama a {@code getChoices(...)} desde su hilo y espera un valor de
     * vuelta; nosotros levantamos el dialogo en el hilo de JavaFX y dejamos al
     * motor parado en el {@code get()}.
     *
     * <p>NUNCA llamarlo desde el hilo de JavaFX: se quedaria esperandose a si
     * mismo. Por eso la comprobacion del principio.
     *
     * @param shower recibe el "callback de respuesta"; se ejecuta en el hilo de UI
     */
    private <T> T askUser(final Consumer<Consumer<T>> shower, final T fallback) {
        final UiDispatcher ui = uiDispatcher();
        if (ui == null || table == null) {
            return fallback;
        }
        if (ui.isUiThread()) {
            return askOnUiThread(shower, fallback);
        }

        final CompletableFuture<T> answer = new CompletableFuture<>();
        ui.runLater(() -> {
            try {
                shower.accept(value -> {
                    table.getOverlay().hide();
                    answer.complete(value);
                });
            } catch (final Exception e) {
                e.printStackTrace();
                answer.complete(fallback);
            }
        });

        try {
            return answer.get();
        } catch (final Exception e) {
            Thread.currentThread().interrupt();
            return fallback;
        }
    }

    /**
     * La misma pregunta, pero hecha DESDE el hilo de interfaz.
     *
     * <p>Pasa mas de lo que parece. Un click del jugador se atiende en el hilo
     * de JavaFX y desde ahi se llama al motor ({@code selectCard}); si esa carta
     * tiene varias habilidades, el motor devuelve la llamada a
     * {@code getAbilityToPlay} <b>sin cambiar de hilo</b>. O sea que acabamos
     * teniendo que ensenyar un dialogo y esperar respuesta estando ya en el hilo
     * que tiene que pintarlo.
     *
     * <p>Bloquearlo con un {@code Future} seria un cuelgue seguro. Antes se
     * devolvia el valor por defecto, que es peor de lo que parece: la decision
     * del jugador se perdia EN SILENCIO. Sintoma real: clicar una tierra con dos
     * habilidades de mana, o un equipamiento con su "Equipar", y que no pasara
     * absolutamente nada.
     *
     * <p>La solucion es la misma que usa Swing con sus dialogos modales: un
     * <b>bucle de eventos anidado</b>. {@code Platform.enterNestedEventLoop}
     * detiene esta pila de llamadas pero SIGUE atendiendo eventos, asi que el
     * dialogo se pinta y responde; cuando el jugador contesta,
     * {@code exitNestedEventLoop} devuelve el valor justo aqui y el motor
     * continua como si nada.
     */
    private <T> T askOnUiThread(final Consumer<Consumer<T>> shower, final T fallback) {
        final Object key = new Object();
        final java.util.concurrent.atomic.AtomicBoolean answered =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final List<T> box = new ArrayList<>(1);
        box.add(fallback);

        try {
            shower.accept(value -> {
                // Contestar dos veces reventaria el bucle anidado.
                if (!answered.compareAndSet(false, true)) {
                    return;
                }
                table.getOverlay().hide();
                box.set(0, value);
                javafx.application.Platform.exitNestedEventLoop(key, null);
            });
        } catch (final Exception e) {
            e.printStackTrace();
            return fallback;
        }

        if (answered.get()) {
            // Ha contestado sin llegar a pintarse (un dialogo que se resuelve
            // solo): no hay bucle que arrancar.
            return box.get(0);
        }

        try {
            javafx.application.Platform.enterNestedEventLoop(key);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido abrir el dialogo anidado: " + e);
            return fallback;
        }
        return box.get(0);
    }

    /**
     * "Elige una de las dos pilas", con su dialogo de verdad.
     *
     * <p>El motor manda esta pregunta como una lista de cartas normal, con las
     * dos pilas metidas dentro como {@code CardView} falsos. Ver
     * {@link forge.neo.ui.PileDialog}, que explica por que eso no se puede
     * ensenyar tal cual.
     *
     * <p>Devuelve la <b>etiqueta</b> elegida, que es lo que
     * {@code chooseCardsPile} compara. Si algo va mal devuelve null y se cae al
     * dialogo de siempre: mas vale feo que colgado.
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> askPile(final String message, final List<T> choices) {
        final List<CardView> cards = new ArrayList<>();
        for (final T o : choices) {
            if (!(o instanceof CardView)) {
                return null;
            }
            cards.add((CardView) o);
        }
        final CardView picked = askUser(reply -> {
            final forge.neo.ui.PileDialog dialog = new forge.neo.ui.PileDialog(
                    message, cards, handCardWidth(), reply::accept);
            table.getOverlay().show(dialog);
        }, null);
        if (picked == null) {
            return null;
        }
        final List<T> out = new ArrayList<>();
        out.add((T) picked);
        return out;
    }

    /** Dialogo de "elige N de esta lista". Cubre casi todo el long tail. */
    private <T> List<T> askChoice(final String title, final List<T> options,
                                  final int min, final int max,
                                  final FSerializableFunction<T, String> display) {
        if (options == null || options.isEmpty()) {
            return new ArrayList<>();
        }
        // reveal() llega como getChoices(msg, -1, -1, items): no hay nada que
        // elegir, solo enseñar una lista. Se pasa tal cual al dialogo, que sabe
        // ponerse en modo solo-lectura.
        final boolean reveal = min < 0 || max <= 0;
        final int lo = reveal ? -1 : Math.max(0, Math.min(min, options.size()));
        final int hi = reveal ? -1 : Math.max(lo, Math.min(max, options.size()));

        final List<T> picked = askUser(reply -> {
            final ChoiceDialog<T> dialog = new ChoiceDialog<>(
                    title, options, lo, hi,
                    display == null ? String::valueOf : display::apply,
                    handCardWidth(), reply::accept);
            table.getOverlay().show(dialog);
        }, null);

        if (picked != null) {
            return picked;
        }
        // Si algo fue mal, contestar lo minimo legal en vez de colgar la partida.
        final List<T> safe = new ArrayList<>();
        for (int i = 0; i < Math.max(0, lo); i++) {
            safe.add(options.get(i));
        }
        return safe;
    }

    /**
     * Herramienta de prueba: lanza un dialogo de opciones y devuelve lo elegido.
     *
     * <p>Existe para poder verificar el caso dificil — pedirle algo al jugador
     * ESTANDO ya en el hilo de interfaz — sin tener que montar una partida y
     * encontrar una carta con varias habilidades. La usa {@code --nested-test}.
     */
    public int selfTestAskOption() {
        return askOption("Prueba", NeoText.get("choice.title"),
                java.util.List.of("Primera", "Segunda"), 0);
    }

    /**
     * Pregunta ensenyando la carta de la que viene, como hace Arena.
     *
     * <p>Es el camino por defecto de todas las preguntas de si/no y de modos:
     * la carta a tamano de lectura y los botones grandes al lado, en el centro
     * de la pantalla. Sin la carta delante, el jugador no sabe que le estan
     * preguntando ni por que.
     */
    private int askCard(final CardView card, final String title, final String question,
                        final List<String> options, final int defaultOption) {
        if (options == null || options.isEmpty()) {
            return defaultOption;
        }
        final Integer index = askUser(reply -> {
            final CardPromptDialog dialog = new CardPromptDialog(
                    card, title, question, options, defaultOption,
                    table.zoomCardWidth() * 0.62, reply::accept);
            table.getOverlay().show(dialog);
        }, null);
        return index == null ? defaultOption : index;
    }

    /** Dialogo de botones. Devuelve el indice elegido. */
    private int askOption(final String title, final String message,
                          final List<String> options, final int defaultOption) {
        if (options == null || options.isEmpty()) {
            return defaultOption;
        }
        final Integer index = askUser(reply -> {
            final ConfirmDialog dialog = new ConfirmDialog(
                    title, message, options, defaultOption, reply::accept);
            table.getOverlay().show(dialog);
        }, null);
        return index == null ? defaultOption : index;
    }

    private double handCardWidth() {
        return 132;
    }

    // ===============================================================
    // Decisiones automaticas (fase 1)
    // En la fase 4 cada una de estas se convierte en una interaccion real.
    // ===============================================================

    @Override
    public boolean confirm(final CardView c, final String question, final boolean defaultYes,
                           final List<String> options) {
        if (interactive()) {
            final List<String> opts = options == null || options.size() < 2
                    ? List.of("Si", "No") : options;
            // El motor SIEMPRE manda aqui la carta de la que viene la pregunta.
            // Antes se tiraba, y por eso un disparo en tu paso final salia como
            // una linea de texto sin decir siquiera de que carta era.
            return askCard(c, null, question, opts, defaultYes ? 0 : 1) == 0;
        }
        trace("confirm: %s -> %s", question, defaultYes);
        return defaultYes;
    }

    @Override
    public boolean showConfirmDialog(final String message, final String title, final String yes,
                                     final String no, final boolean defaultYes) {
        if (autoConfirm) {
            // Ya se le ha preguntado al jugador en nuestro propio dialogo.
            return true;
        }
        if (interactive()) {
            final List<String> opts = List.of(
                    yes == null || yes.isBlank() ? "Si" : yes,
                    no == null || no.isBlank() ? "No" : no);
            // SIN carta, y es lo correcto: ver setCard.
            return askCard(null, title, message, opts, defaultYes ? 0 : 1) == 0;
        }
        trace("confirmDialog: %s -> %s", message, defaultYes);
        return defaultYes;
    }

    @Override
    public int showOptionDialog(final String message, final String title, final FSkinProp icon,
                                final List<String> options, final int defaultOption) {
        if (interactive()) {
            // SIN carta, y es lo correcto: ver setCard.
            return askCard(null, title, message, options, defaultOption);
        }
        trace("optionDialog: %s -> %d", message, defaultOption);
        return defaultOption;
    }

    /**
     * Escribir algo: casi siempre <b>un numero</b>.
     *
     * <p><b>Devolver {@code initialInput} sin preguntar colgaba la partida
     * entera</b>, y no de forma sutil. `AbstractGuiGame.getInteger` pregunta
     * primero con una lista corta (0..9 y "Otro numero"), y si eliges "Otro
     * numero" cae a un {@code while (true)} que llama <b>aqui</b> y solo sale
     * con un numero o con una cancelacion ({@code null}). Nuestro
     * {@code initialInput} era {@code ""}: ni numero ni cancelacion, asi que el
     * hilo del motor se ponia a girar para siempre — sin excepcion, sin aviso y
     * al 100% de CPU.
     *
     * <p>Se llega ahi con cualquier <b>X mayor que 9</b>, con un multikicker
     * repetido muchas veces o con "cuantos contadores" pasado de diez. O sea
     * que no era un caso raro: era cuestion de tiempo.
     *
     * <p>Ojo al orden de los parametros, que enganya: {@code message} es el
     * texto de ayuda ("escribe un numero entre 0 y 15") y {@code title} el
     * titulo. Forge llama {@code showInputDialog(prompt, message, ...)}.
     */
    @Override
    public String showInputDialog(final String message, final String title, final FSkinProp icon,
                                  final String initialInput, final List<String> inputOptions,
                                  final boolean isNumeric) {
        if (!interactive()) {
            trace("inputDialog (sin jugador): %s", message);
            return initialInput;
        }
        // Con opciones cerradas no hay nada que escribir: es una eleccion.
        if (inputOptions != null && !inputOptions.isEmpty()) {
            final List<String> picked =
                    askChoice(headingOf(title, message), new ArrayList<>(inputOptions), 1, 1, null);
            return picked.isEmpty() ? null : picked.get(0);
        }
        return askUser(reply -> {
            final javafx.scene.layout.Region dialog = isNumeric
                    ? forge.neo.ui.TextDialog.number(headingOf(title, null), message,
                            initialInput, reply::accept, () -> reply.accept(null))
                    : forge.neo.ui.TextDialog.line(headingOf(title, null), message,
                            initialInput, reply::accept, () -> reply.accept(null));
            table.getOverlay().show(dialog);
        }, null);
    }

    /**
     * De dia o de noche.
     *
     * <p>El motor lo lleva desde Innistrad y nos lo manda aqui; hasta ahora
     * {@code AbstractGuiGame} se lo guardaba y no se veia en ninguna parte. Hay
     * cartas que se transforman al cambiar el ciclo, y sin el indicador la
     * transformacion parece que pasa sola.
     */
    @Override
    public void updateDayTime(final String daytime) {
        super.updateDayTime(daytime);
        if (table == null) {
            return;
        }
        runOnUi(() -> table.getPhaseRail().setDayTime(daytime));
    }

    // ===============================================================
    // El resaltado DEBIL: "esto lo puedes usar ahora mismo"
    // ===============================================================

    /**
     * El motor nos dice que cartas puedes usar, y hasta cual taparia el "Auto".
     *
     * <p><b>Esto llegaba y se tiraba.</b> {@code PlayerControllerHuman.
     * pushActionableCards} recorre tus zonas y manda por aqui la lista de lo
     * que puedes jugar o activar ahora mismo; durante un pago de mana esa lista
     * es <b>exactamente tus fuentes de mana disponibles</b>. Y viene
     * <b>pesada</b>: las cartas que el boton "Auto" iba a tapar van dos veces,
     * asi que {@code getWeakSelectableStrength} vale 2 para ellas. O sea que el
     * plan del motor se puede ENSENYAR antes de pulsar nada.
     *
     * <p>Nosotros ya poniamos {@code UI_SHOW_ACTIONABLE_HIGHLIGHTS} en true —
     * o sea que el motor estaba haciendo el trabajo — y luego no lo pintabamos.
     *
     * <p>Es distinto de {@code setSelectables}: aquello es "el motor espera que
     * elijas una de estas y no sigue hasta que lo hagas"; esto es "podrias".
     */
    @Override
    public void setWeaklySelectable(final Iterable<CardView> cards) {
        super.setWeaklySelectable(cards);
        pushActionable();
    }

    @Override
    public void clearWeaklySelectable() {
        super.clearWeaklySelectable();
        pushActionable();
    }

    private void pushActionable() {
        if (verbose) {
            // El recuento recorre todas las zonas: solo con traza encendida.
            trace("resaltado debil: %d cartas", weaklySelectableCount());
        }
        if (table == null) {
            return;
        }
        runOnUi(() -> table.setActionable(this::actionableStrength));
    }

    /** Cuantas cartas trae el ultimo resaltado debil. Solo para la traza. */
    private int weaklySelectableCount() {
        int n = 0;
        final forge.game.GameView gv = getGameView();
        if (gv == null || gv.getPlayers() == null) {
            return 0;
        }
        for (final PlayerView p : gv.getPlayers()) {
            for (final forge.game.zone.ZoneType z : new forge.game.zone.ZoneType[] {
                    forge.game.zone.ZoneType.Battlefield, forge.game.zone.ZoneType.Hand}) {
                final var cards = p.getCards(z);
                if (cards == null) {
                    continue;
                }
                for (final CardView cv : cards) {
                    if (actionableStrength(cv) > 0) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /** 0 nada, 1 la puedes usar, 2 ademas es del plan del boton "Auto". */
    public int actionableStrength(final CardView card) {
        if (card == null) {
            return 0;
        }
        try {
            final int engine = getWeakSelectableStrength(card);
            if (engine > 0) {
                return engine;
            }
            // Una criatura preparada cuyo hechizo se puede lanzar YA.
            //
            // El motor no la resalta, y con razon desde su punto de vista: lo
            // jugable es la copia del exilio, no ella. Pero el click que lo
            // lanza es el suyo (ver preparedSpellToCast), asi que el resaltado
            // tiene que estar donde esta el click — o seria un control
            // invisible, que es peor que no tenerlo.
            return castablePreparedSpell(card) != null ? 1 : 0;
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /** Titulo y ayuda vienen sueltos y a veces uno de los dos esta vacio. */
    private static String headingOf(final String title, final String extra) {
        final String t = title == null || title.isBlank()
                ? NeoText.get("input.title") : title;
        return extra == null || extra.isBlank() ? t : t + " — " + extra;
    }

    @Override
    public <T> List<T> getChoices(final String message, final int min, final int max,
                                  final List<T> choices, final List<T> selected,
                                  final FSerializableFunction<T, String> display) {
        if (isSilencedAiDeckWarning(message)) {
            // Es el aviso de arranque de "la IA no juega bien estas cartas".
            // Llega como un reveal, o sea que sale un panel que hay que cerrar
            // antes de empezar CADA partida. Si el jugador lo ha silenciado, se
            // contesta solo con la lista vacia, que es lo que espera un reveal.
            return new ArrayList<>();
        }
        if (interactive()) {
            // "Elige una pila" no es una eleccion de cartas, aunque el motor la
            // mande como tal. Ver PileDialog.
            if (max == 1 && forge.neo.ui.PileDialog.looksLikePiles(choices)) {
                final List<T> one = askPile(message, choices);
                if (one != null) {
                    return one;
                }
            }
            return askChoice(message, choices, min, max, display);
        }
        // Coger los primeros 'min'.
        final List<T> out = new ArrayList<>();
        if (choices != null) {
            final int want = Math.min(Math.max(min, 0), choices.size());
            for (int i = 0; i < want; i++) {
                out.add(choices.get(i));
            }
        }
        trace("getChoices(%d..%d de %d) -> %d", min, max,
                choices == null ? 0 : choices.size(), out.size());
        return out;
    }

    @Override
    public GameEntityView chooseSingleEntityForEffect(final String title,
                                                      final List<? extends GameEntityView> optionList,
                                                      final DelayedReveal delayedReveal,
                                                      final boolean isOptional) {
        if (interactive() && optionList != null && !optionList.isEmpty()) {
            final List<GameEntityView> opts = new ArrayList<>(optionList);
            final List<GameEntityView> picked =
                    askChoice(title, opts, isOptional ? 0 : 1, 1, null);
            return picked.isEmpty() ? null : picked.get(0);
        }
        final GameEntityView pick =
                (optionList == null || optionList.isEmpty()) ? null : optionList.get(0);
        trace("chooseSingleEntity: %s", title);
        return pick;
    }

    @Override
    public List<GameEntityView> chooseEntitiesForEffect(final String title,
                                                        final List<? extends GameEntityView> optionList,
                                                        final int min, final int max,
                                                        final DelayedReveal delayedReveal) {
        if (interactive() && optionList != null && !optionList.isEmpty()) {
            return askChoice(title, new ArrayList<GameEntityView>(optionList), min, max, null);
        }
        final List<GameEntityView> out = new ArrayList<>();
        if (optionList != null) {
            final int want = Math.min(Math.max(min, 0), optionList.size());
            for (int i = 0; i < want; i++) {
                out.add(optionList.get(i));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------
    // "No me vuelvas a preguntar por esto"
    // ---------------------------------------------------------------

    /**
     * El menu de click derecho sobre algo del stack.
     *
     * <p><b>Esto tampoco lo programamos nosotros.</b> Forge guarda las
     * respuestas en {@code AutoYieldStore} y en {@code PersistentAutoDecisionStore},
     * y las <b>recuerda entre partidas</b>. Las dos GUIs oficiales lo enseñan
     * desde el click derecho del stack ({@code VStack} + {@code
     * VAutoYieldsAndTriggers}); nosotros no lo enseñabamos en ningun sitio, asi
     * que el mismo disparo te preguntaba una y otra vez. En Commander, donde un
     * disparo puede saltar ocho veces por turno, eso es de lo que mas cansa.
     *
     * <p>La llave de cada cosa la da el motor: {@code StackItemView.getKey()}.
     * El alcance — si lo recordado vale para esta habilidad o para la carta
     * entera — lo decide tambien el motor
     * ({@code YieldController.isAbilityScope()}), no nosotros.
     *
     * <p><b>El zoom sigue el primero.</b> Antes de esto, el click derecho en el
     * stack ampliaba la carta y nada mas; si el menu lo sustituyera sin mas, se
     * habria perdido algo que ya funcionaba. Va como primera opcion.
     *
     * <p>Y "olvidar lo recordado" no es un extra: una respuesta que se guarda
     * sola y para siempre <b>tiene</b> que poder deshacerse desde donde se puso
     * (principio 6 de las notas de diseño). Solo sale si hay algo que olvidar.
     */
    private void showStackMenu(final StackItemView item) {
        if (!interactive() || item == null || table == null) {
            return;
        }
        final CardView source = item.getSourceCard();
        final IGameController gc = getGameController();
        if (gc == null) {
            table.showZoom(source);
            return;
        }

        final String key = item.getKey();
        final boolean hasKey = key != null && !key.isEmpty();
        // Un disparo opcional es el unico al que tiene sentido contestarle
        // "siempre" — y solo si es TUYO: el motor no te pregunta por los del
        // rival. isOptionalTrigger() es justo el dato que hacia falta.
        final boolean mineTrigger = hasKey && item.isOptionalTrigger()
                && isLocalPlayer(item.getActivatingPlayer());
        final boolean canYield = hasKey && item.isAbility();

        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();

        labels.add(NeoText.get("stack.menu.zoom"));
        actions.add(() -> table.showZoom(source));

        if (canYield) {
            final boolean yielding = gc.shouldAutoYield(key);
            labels.add(NeoText.get(yielding ? "stack.menu.yield.on" : "stack.menu.yield.off"));
            actions.add(() -> {
                gc.setShouldAutoYield(key, !yielding, abilityScope(gc));
                refreshYieldUi(getCurrentPlayer());
            });
        }

        if (mineTrigger) {
            final AutoYieldStore.TriggerDecision now = gc.getTriggerDecision(key);
            labels.add(NeoText.get(now == AutoYieldStore.TriggerDecision.ACCEPT
                    ? "stack.menu.alwaysYes.on" : "stack.menu.alwaysYes.off"));
            actions.add(() -> {
                gc.setTriggerDecision(key,
                        now == AutoYieldStore.TriggerDecision.ACCEPT
                                ? AutoYieldStore.TriggerDecision.ASK
                                : AutoYieldStore.TriggerDecision.ACCEPT,
                        abilityScope(gc));
                refreshYieldUi(getCurrentPlayer());
            });
            labels.add(NeoText.get(now == AutoYieldStore.TriggerDecision.DECLINE
                    ? "stack.menu.alwaysNo.on" : "stack.menu.alwaysNo.off"));
            actions.add(() -> {
                gc.setTriggerDecision(key,
                        now == AutoYieldStore.TriggerDecision.DECLINE
                                ? AutoYieldStore.TriggerDecision.ASK
                                : AutoYieldStore.TriggerDecision.DECLINE,
                        abilityScope(gc));
                refreshYieldUi(getCurrentPlayer());
            });
        }

        if (hasRemembered(gc)) {
            labels.add(NeoText.get("stack.menu.forget"));
            actions.add(() -> {
                gc.getYieldController().clearAutoYields();
                gc.getYieldController().setDisableAutoTriggers(false);
                forgetTriggerDecisions(gc);
                refreshYieldUi(getCurrentPlayer());
            });
        }

        final boolean[] all = new boolean[labels.size()];
        java.util.Arrays.fill(all, true);
        final AbilityMenu menu = new AbilityMenu(source, labels, all,
                table.zoomCardWidth() * 0.62, picked -> {
                    table.getOverlay().hide();
                    if (picked != null && picked >= 0 && picked < actions.size()) {
                        actions.get(picked).run();
                    }
                });
        table.getOverlay().show(menu);
    }

    /** Si lo recordado vale para la habilidad o para la carta. Lo dice el motor. */
    private static boolean abilityScope(final IGameController gc) {
        return gc.getYieldController() != null && gc.getYieldController().isAbilityScope();
    }

    /** ¿Hay algo recordado que se pueda olvidar? */
    private static boolean hasRemembered(final IGameController gc) {
        final YieldController yc = gc.getYieldController();
        if (yc == null) {
            return false;
        }
        return yc.getAutoYields().iterator().hasNext()
                || yc.getAutoTriggers().iterator().hasNext();
    }

    /** Devuelve a "preguntar" cada disparo que tuviera respuesta fija. */
    private static void forgetTriggerDecisions(final IGameController gc) {
        final YieldController yc = gc.getYieldController();
        if (yc == null) {
            return;
        }
        // Se copian las llaves antes de tocarlas: se esta recorriendo lo mismo
        // que se modifica.
        final List<String> keys = new ArrayList<>();
        for (final Map.Entry<String, AutoYieldStore.TriggerDecision> e : yc.getAutoTriggers()) {
            keys.add(e.getKey());
        }
        for (final String k : keys) {
            gc.setTriggerDecision(k, AutoYieldStore.TriggerDecision.ASK, abilityScope(gc));
        }
    }

    /**
     * El motor avisa de que lo recordado ha cambiado.
     *
     * <p>Lo llama Forge cuando una respuesta se guarda o se borra — tambien
     * desde el otro lado en una partida en red. Se repinta el stack porque el
     * menu se construye leyendo el estado en el momento de abrirlo: sin esto,
     * la marca de "ya lo dejas pasar" se quedaria como estaba hasta el
     * siguiente cambio de mesa.
     */
    @Override
    public void refreshYieldUi(final PlayerView player) {
        // Solo si es de los nuestros: en una partida en red esto llega tambien
        // por lo que recuerde el otro, y repintar nuestra mesa por eso seria
        // trabajo para nada.
        if (player == null || isLocalPlayer(player)) {
            pushToTable();
        }
    }

    @Override
    public SpellAbilityView getAbilityToPlay(final CardView hostCard,
                                             final List<SpellAbilityView> abilities,
                                             final ITriggerEvent triggerEvent) {
        // El motor nos devuelve la llamada cuando una carta tiene varias cosas
        // jugables: es el menu contextual de Arena.
        if (interactive() && abilities != null && abilities.size() > 1) {
            final Integer picked = askUser(reply -> {
                final AbilityMenu menu = new AbilityMenu(
                        hostCard, abilities, table.zoomCardWidth() * 0.62, reply::accept);
                table.getOverlay().show(menu);
            }, null);
            // -1 (o nada) significa "me he equivocado de carta": devolver null
            // es la forma de decirle al motor que no se hace nada. Sin esto,
            // clicar una carta por error te obligaba a activar algo.
            if (picked == null || picked < 0 || picked >= abilities.size()) {
                return null;
            }
            return abilities.get(picked);
        }
        return (abilities == null || abilities.isEmpty()) ? null : abilities.get(0);
    }

    @Override
    public Map<CardView, Integer> assignCombatDamage(final CardView attacker,
                                                     final List<CardView> blockers, final int damage,
                                                     final GameEntityView defender,
                                                     final boolean overrideOrder,
                                                     final boolean maySkip) {
        if (interactive() && blockers != null && !blockers.isEmpty()) {
            final boolean trample = attacker != null && attacker.getCurrentState() != null
                    && attacker.getCurrentState().hasTrample();
            final GameView gv = getGameView();
            final int poison = gv == null ? 10 : gv.getPoisonCountersToLose();
            final Map<CardView, Integer> picked = askUser(reply -> {
                final DamageDialog dialog = new DamageDialog(
                        attacker, blockers, damage, defender, overrideOrder, maySkip,
                        trample, poison, handCardWidth(), reply::accept);
                table.getOverlay().show(dialog);
            }, null);
            if (picked != null) {
                return picked;
            }
            // askUser solo devuelve null si algo fue mal (o si el jugador eligio
            // "no asignar", que el motor entiende como null): en ambos casos hay
            // que devolver algo legal, y el reparto automatico lo es.
            if (maySkip) {
                return null;
            }
        }
        // Reparto automatico: letal en orden, el resto al ultimo.
        final Map<CardView, Integer> map = new HashMap<>();
        if (blockers != null && !blockers.isEmpty()) {
            map.put(blockers.get(0), damage);
        }
        return map;
    }

    /**
     * "Reparte N entre estos".
     *
     * <p><b>Esto contestaba solo y no hacia ruido</b>: le daba el total al
     * primer objetivo del mapa. Por aqui pasa, entre otras cosas, <b>que mana
     * produce una tierra de filtro</b> — {@code
     * PlayerControllerHuman.specifyManaCombo} acaba llamando a este metodo — y
     * el orden de {@code ColorSet} pone el <b>rojo antes que el blanco</b>
     * ({@code RW(Color.RED, Color.WHITE)}), asi que una <i>Rugged Prairie</i>
     * producia siempre {R}{R} sin preguntar nada. Reportado jugando: <i>"quiero
     * generar 2 blancos, no me pregunta y genera 2 rojos"</i>.
     *
     * <p>Con un solo objetivo no hay nada que repartir y preguntar seria un
     * dialogo con una sola respuesta posible, asi que se contesta directamente.
     */
    @Override
    public Map<Object, Integer> assignGenericAmount(final CardView effectSource,
                                                    final Map<Object, Integer> targets,
                                                    final int amount, final boolean atLeastOne,
                                                    final String amountLabel) {
        if (targets == null || targets.isEmpty() || amount <= 0) {
            return new HashMap<>();
        }
        if (interactive() && targets.size() > 1) {
            final Map<Object, Integer> picked = askUser(reply -> {
                final AmountDialog dialog = new AmountDialog(
                        effectSource, targets, amount, atLeastOne, amountLabel,
                        handCardWidth(), reply::accept);
                table.getOverlay().show(dialog);
            }, null);
            if (picked != null && !picked.isEmpty()) {
                return picked;
            }
        }
        // Sin jugador delante (o si algo fue mal) hay que devolver algo legal:
        // que sume el total y que nadie pase de su tope.
        return AmountDialog.autoSplit(targets, amount, atLeastOne);
    }

    /**
     * "Elige cuales, y en que orden."
     *
     * <p><b>Esto devolvia la lista entera sin preguntar, y era un fallo caro
     * porque no hacia ruido.</b> Por aqui pasan, entre otras cosas,
     * <b>adivinar</b> y <b>vigilar</b> de dos cartas o mas: {@code
     * PlayerControllerHuman.arrangeForScry} / {@code arrangeForSurveil} llaman
     * a {@code getGui().many(...)}, y {@code many} con mas de una carta acaba
     * aqui. Devolver "todas" significaba <b>mandar al cementerio todo lo que
     * vigilas</b> y <b>al fondo todo lo que adivinas</b>, en silencio y sin que
     * el jugador viera nada. Con una sola carta el motor pregunta por otro
     * camino ({@code InputConfirm}), asi que el fallo solo salia a partir de
     * dos.
     *
     * <p><b>Como se traducen los limites.</b> El motor no cuenta lo que hay que
     * ELEGIR, cuenta lo que puede QUEDARSE en el origen: {@code min} y {@code
     * max} son "cuantas pueden quedar sin elegir". Un {@code -1} es "las que
     * quieras". Se le da la vuelta para el dialogo, que piensa en lo elegido.
     *
     * <p><b>Y el orden sale gratis.</b> {@code ChoiceDialog} guarda lo elegido
     * en un conjunto por orden de insercion, o sea en el orden en el que se
     * clica. Para los usos que son de verdad ordenar — colocar cartas encima de
     * tu biblioteca, el orden de dano entre bloqueadores — eso es exactamente
     * la respuesta: se clican en el orden que quieras.
     */
    @Override
    public <T> IGuiGame.OrderResult<T> order(final String title, final String top, final int min,
                                             final int max, final List<T> sourceChoices,
                                             final List<T> destChoices, final CardView referenceCard,
                                             final boolean referenceCardIsSource,
                                             final boolean rememberOption) {
        final List<T> source = sourceChoices == null ? List.of() : sourceChoices;
        final List<T> already = destChoices == null ? new ArrayList<>() : new ArrayList<>(destChoices);
        if (source.isEmpty()) {
            return new IGuiGame.OrderResult<>(already, false);
        }
        if (!interactive()) {
            final List<T> all = new ArrayList<>(already);
            all.addAll(source);
            trace("order(%s) sin jugador -> %d", title, all.size());
            return new IGuiGame.OrderResult<>(all, false);
        }

        // De "cuantas pueden QUEDAR" a "cuantas hay que ELEGIR".
        final int n = source.size();
        final int pickMin = max < 0 ? 0 : Math.max(0, n - max);
        final int pickMax = min < 0 ? n : Math.min(n, n - min);

        final String heading = top == null || top.isBlank() ? title
                : (title == null || title.isBlank() ? top : title + " — " + top);

        final List<T> picked = askChoice(heading, source, pickMin, Math.max(pickMin, pickMax), null);
        final List<T> ordered = new ArrayList<>(already);
        if (picked != null) {
            ordered.addAll(picked);
        }
        return new IGuiGame.OrderResult<>(ordered, false);
    }

    @Override
    public List<CardView> manipulateCardList(final String title, final Iterable<CardView> cards,
                                             final Iterable<CardView> manipulable, final boolean toTop,
                                             final boolean toBottom, final boolean toAnywhere) {
        final List<CardView> out = new ArrayList<>();
        if (cards != null) {
            cards.forEach(out::add);
        }
        return out;
    }

    /**
     * Banquillo entre partida y partida de un Bo3 (draft y sellado con
     * banquillo — {@code la auditoría del motor} C5).
     *
     * <p>Solo lo llama el motor cuando el formato admite banquillo
     * ({@code GameType.isSideboardingAllowed()} — Commander no, draft y
     * sellado si) y va a empezar la SEGUNDA partida en adelante de un
     * partido ({@code Match.prepareAllZones}). Fuera de eso esto no se llama
     * nunca, asi que no hace falta comprobar el formato aqui.
     *
     * <p>Se ofrece el pool ENTERO — lo que estaba en el mazo mas lo que se
     * quedo fuera — y se elige de ahi el nuevo mazo, con el mismo tamano de
     * antes: es mas simple que un "mueve de aqui para alla" con dos listas, y
     * el mismo lenguaje visual que ya usa {@code ChoiceDialog} en todo lo
     * demas (la carta es la protagonista, no una fila de texto). El motor
     * valida el resultado el solo y vuelve a preguntar si algo no cuadra
     * (tamano minimo, banquillo maximo), asi que aqui no hace falta calcular
     * ningun limite de verdad — basta con pedir el mismo numero de cartas
     * que ya tenia el mazo, que es ademas lo que de verdad significa
     * "sideboard": cambias cartas, no encoges ni agrandas el mazo.
     */
    @Override
    public List<PaperCard> sideboard(final CardPool sideboard, final CardPool main,
                                     final String message) {
        if (!interactive()) {
            return null; // sin jugador: se queda el mazo de antes
        }
        final List<PaperCard> current = main.toFlatList();
        final List<PaperCard> pool = new ArrayList<>();
        if (sideboardIsSuperset(sideboard, main)) {
            // Draft: la banda YA es el pool entero (el principal es una copia
            // de una parte). Sumar los dos duplicaria cada carta del mazo.
            pool.addAll(sideboard.toFlatList());
        } else {
            // Sellado: montones disjuntos. El pool de verdad es la suma.
            pool.addAll(current);
            pool.addAll(sideboard.toFlatList());
        }
        final int size = current.size();
        final List<PaperCard> picked = askUser(reply -> {
            final ChoiceDialog<PaperCard> dialog = new ChoiceDialog<>(
                    NeoText.get("sideboard.title"), pool, size, size,
                    PaperCard::getName, handCardWidth(), current, reply::accept);
            table.getOverlay().show(dialog);
        }, null);
        return picked;
    }

    /**
     * Si la banda ya contiene lo que hay en el mazo, o son montones aparte.
     *
     * <p>El draft deja el pool ENTERO en la banda (el principal es una copia
     * de una parte de ese pool), asi que ahi el principal es siempre
     * subconjunto de la banda. El sellado los deja disjuntos: nada del mazo
     * esta tambien en la banda ({@code NeoSealed.buildMainDeck} lo retira a
     * proposito). No hace falta que nadie nos diga cual es cual: se ve
     * mirando si la banda cubre, carta a carta, lo que hay en el mazo.
     */
    private static boolean sideboardIsSuperset(final CardPool sideboard, final CardPool main) {
        if (main.isEmpty()) {
            return false; // nada que comprobar: tratarlos como aparte no pierde ninguna carta
        }
        for (final Map.Entry<PaperCard, Integer> e : main) {
            if (sideboard.countAll(c -> c.equals(e.getKey())) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    // ===============================================================
    // Presentacion: en fase 1 no aplica
    // ===============================================================

    /**
     * El motor avisa de que el combate ha cambiado.
     *
     * <p>Se dispara cada vez que se declara o se retira un atacante o un
     * bloqueador, asi que es lo que mantiene las flechas al dia mientras el
     * jugador declara.
     */
    @Override
    public void showCombat() {
        pushToTable();
    }

    /**
     * Empieza un pago de mana.
     *
     * <p>Aqui no hay que montar nada: los pips de la barra ya muestran
     * {@code PlayerView.getMana(color)}, que ES la reserva. Lo unico que falta
     * es destacarla mientras dura el pago, y que clicarla la gaste (eso lo
     * cablea {@link #setTable}).
     */
    @Override
    public void showManaPool(final PlayerView player) {
        manaPool(player, true);
    }

    @Override
    public void hideManaPool(final PlayerView player) {
        manaPool(player, false);
    }

    /**
     * Destaca (o apaga) la reserva de mana en la barra del jugador.
     *
     * <p>Solo es la luz de los pips. <b>No</b> decide si hay un pago en curso:
     * eso lo dice la etiqueta del boton, ver {@link #payingMana}.
     */
    private void manaPool(final PlayerView player, final boolean on) {
        if (!interactive()) {
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui != null) {
            ui.runLater(() -> table.setManaPoolActive(player, on));
        }
    }

    @Override public void updateShards(final Iterable<PlayerView> players) { }
    @Override public void enableOverlay() { }
    @Override public void disableOverlay() { }
    /**
     * "Marca esta carta." Se recibe y <b>no se guarda</b>, a proposito.
     *
     * <p>Aqui habia un {@code focusCard} con la ultima carta que el motor
     * hubiera marcado, y las preguntas genericas ({@code showConfirmDialog},
     * {@code showOptionDialog}) salian con ella al lado. El problema es que esa
     * carta <b>no tiene por que tener nada que ver</b>: no se limpia nunca, asi
     * que lo que se ensenya es lo ultimo que pasara por delante. Reportado
     * jugando: el aviso de <i>"tienes mana flotando que podrias perder"</i>
     * salio con la criatura que se acababa de lanzar al lado, grande y con su
     * texto, como si preguntara por ella.
     *
     * <p>Y no es un caso raro que haya que tapar: <b>ninguna</b> de esas dos
     * preguntas es de carta. Las que Forge manda por ahi son conceder, cerrar
     * como espectador, sobrescribir un fichero y el mana flotante
     * ({@code InputPassPriority.passPriority}). Cuando la pregunta SI viene de
     * una carta, el motor la pasa en la firma — {@code confirm(CardView, ...)}
     * — y ahi si se ensenya.
     *
     * <p>Es la misma leccion que la del comandante que sale de la partida (ver
     * {@code commanderByName}): <b>ensenyar la carta equivocada con esa
     * seguridad es mucho peor que no ensenyar ninguna.</b>
     */
    @Override
    public void setCard(final CardView card) {
    }
    @Override public void setPanelSelection(final CardView card) { }
    @Override public void setPlayerAvatar(final LobbyPlayer player, final IHasIcon icon) { }
    /**
     * El motor dice que esa accion no vale ahi.
     *
     * <p>La suelta, por ejemplo, al clicar a un jugador al que no puedes
     * atacar. Antes solo se anotaba en la consola: el jugador clicaba y no
     * pasaba nada, sin explicacion.
     */
    @Override
    public void flashIncorrectAction() {
        trace("accion incorrecta");
        showWhy(NeoText.get("why.incorrect"));
    }

    @Override public void alertUser() { }

    /**
     * Un aviso del motor. <b>Y hay que ensenyarlo.</b>
     *
     * <p>Esto es por donde llega la explicacion de por que el motor no te deja
     * terminar el combate, y se estaba tirando a la consola. Sintoma real, del
     * usuario jugando:
     *
     * <blockquote>me pusieron goad a todas mis criaturas, se me olvido, y en mi
     * fase de combate le estaba dando a OK todo el rato sin que pasara nada
     * porque estoy obligado a atacar</blockquote>
     *
     * <p>Dos caminos llegan aqui, los dos del motor:
     * <ul>
     *   <li><b>Bloqueos</b>: {@code InputBlock.onOk} llama a
     *       {@code CombatUtil.validateBlocks}, que devuelve una frase que
     *       <i>nombra la carta</i> ("... cannot be blocked with 1 creatures you
     *       have assigned"). Se ensenya tal cual: esta en ingles, pero dice
     *       exactamente cual y por que.</li>
     *   <li><b>Ataques</b>: el {@code PhaseHandler} manda un
     *       "Attack declaration invalid" que no explica nada ni en ingles. Ese
     *       se sustituye por algo que si sirve.</li>
     * </ul>
     */
    @Override
    public void message(final String message, final String title) {
        log("[%s] %s", title, message);
        if (message == null || message.isBlank()) {
            return;
        }
        final forge.game.GameView gv = getGameView();

        // --- El ataque: el motor no explica nada, asi que se CITA ---
        //
        // "Attack declaration invalid" y punto. Como no se puede afirmar cual
        // criatura tiene la culpa — el goad ni siquiera llega al CardView —, se
        // ensenya lo que el registro tenga escrito de hace poco. Es una cita, y
        // se presenta como tal.
        if (ATTACK_INVALID.equals(message.trim())) {
            final java.util.List<String> quoted = WhyNot.recentCompulsion(gv, 3);
            trace("por que no se puede: ataque | registro=%d lineas", quoted.size());
            showWhy(quoted.isEmpty()
                    ? NeoText.get("why.attack")
                    : NeoText.get("why.attack") + "\n\n"
                            + NeoText.get("why.log", String.join("\n", quoted)));
            return;
        }

        // --- El bloqueo: el motor SI explica, y nombra la carta ---
        //
        // Tres escalones, del que mas dice al que menos:
        //   1. Una amenaza con un solo bloqueador: se puede decir la palabra.
        //   2. Cualquiera de los siete motivos de validateBlocks, traducido.
        //   3. Lo que diga el motor, en ingles pero nombrando la carta.
        final String menaced = menacedWithOneBlocker(gv == null ? null : gv.getCombat());
        final String translated = WhyNot.translate(message);
        trace("por que no se puede: amenaza=%s | traducido=%s | motor=%s",
                menaced == null ? "(ninguna)" : menaced,
                translated == null ? "(no)" : "si", message.trim());

        if (menaced != null) {
            showWhy(NeoText.get("why.menace", menaced));
        } else if (translated != null) {
            showWhy(translated);
        } else {
            // Por AQUI llega tambien lo que NO es un error.
            //
            // `PlayerControllerHuman.notifyOfValue` usa este mismo metodo para
            // contar lo que acaba de pasar: el resultado de una moneda ("Ganas"
            // / "Cara"), el de un dado, el de una votacion, lo que revela un
            // rival. Presentar eso como "no se puede declarar asi" es peor que
            // no ensenyarlo: el jugador cree que ha hecho algo mal.
            //
            // No se intenta adivinar cual es cual — no hay forma de saberlo
            // desde aqui, y cada intento acaba en una condicion mas. Se aplica
            // la misma regla que WhyNot: afirmar SOLO lo que el motor afirma.
            // Lo que reconocemos se explica; lo que no, se cita sin decir que
            // sea un fallo.
            showInfo(NeoText.get("engine.says", message.trim()));
        }
    }

    /** Lo que dice el motor, sin llamarlo error. Ver el comentario de arriba. */
    private void showInfo(final String text) {
        if (!interactive() || table == null) {
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui == null) {
            return;
        }
        ui.runLater(() -> table.getActionBar().setWarning(text, false));
    }

    /**
     * Atacantes con amenaza a los que les has puesto UN solo bloqueador.
     *
     * <p>Se saca de las vistas, sin adivinar nada: amenaza es una palabra clave
     * de verdad ({@code Keyword.MENACE}, "no puede ser bloqueada excepto por
     * dos o mas criaturas") y el {@code CombatView} dice cuantos bloqueadores
     * llevas puestos en cada atacante.
     *
     * <p><b>Y no puede equivocarse.</b> Un atacante con amenaza y exactamente
     * un bloqueador es ilegal SIEMPRE, asi que si aparece es de verdad un
     * motivo por el que el motor te esta diciendo que no — aunque hubiera
     * ademas otros. Cuando no aparece ninguno no se inventa nada: se ensenya lo
     * que diga el motor.
     *
     * @return los nombres, o null si no hay ninguno en ese estado
     */
    public static String menacedWithOneBlocker(final forge.game.combat.CombatView combat) {
        try {
            if (combat == null || combat.getAttackers() == null) {
                return null;
            }
            final StringBuilder names = new StringBuilder();
            for (final CardView attacker : combat.getAttackers()) {
                if (attacker == null || attacker.getCurrentState() == null) {
                    continue;
                }
                if (!attacker.getCurrentState().hasKeyword(forge.game.keyword.Keyword.MENACE)) {
                    continue;
                }
                if (blockerCount(combat, attacker) != 1) {
                    continue;
                }
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(forge.neo.card.CardText.nameOf(attacker.getCurrentState()));
            }
            return names.length() == 0 ? null : names.toString();
        } catch (final RuntimeException e) {
            // Explicar mejor un fallo no vale una excepcion.
            return null;
        }
    }

    /** Cuantos bloqueadores lleva ese atacante, contando los que estas poniendo. */
    private static int blockerCount(final forge.game.combat.CombatView combat,
                                    final CardView attacker) {
        // Mientras declaras, los tuyos van en "planned"; ya confirmados, en
        // "blockers". Se mira el que tenga algo.
        final var planned = combat.getPlannedBlockers(attacker);
        if (planned != null && !planned.isEmpty()) {
            return planned.size();
        }
        final var blockers = combat.getBlockers(attacker);
        return blockers == null ? 0 : blockers.size();
    }

    /**
     * Lo que manda el motor cuando la declaracion de ataque no vale.
     *
     * <p>Es una cadena literal de {@code PhaseHandler}, sin traducir, asi que
     * reconocerla es estable. Y si algun dia cambiara, lo unico que pasaria es
     * que se ensenyaria el texto del motor en vez del nuestro.
     */
    private static final String ATTACK_INVALID = "Attack declaration invalid";

    /** Deja el aviso al lado del boton que acabas de pulsar. */
    private void showWhy(final String text) {
        if (!interactive() || table == null) {
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui == null) {
            return;
        }
        ui.runLater(() -> table.getActionBar().setWarning(text));
    }

    /**
     * Un error del motor durante la partida, que hasta ahora sólo se veía en
     * la traza — y {@code jugar.cmd} arranca sin consola (principio 6 de
     * las notas de diseño: nunca dejar al jugador sin saber qué se espera de él).
     *
     * <p>Sin devolución de valor a propósito: el motor no espera respuesta, así
     * que esto es {@code showWhy} y no {@code askOption} — se agenda en el hilo
     * de UI y se vuelve enseguida, nunca bloquea el hilo del motor. Y por lo
     * mismo, apagado sin ventana ({@code interactive()} en falso): no puede
     * colgar {@code questcheck} ni {@code lobbycheck} esperando una pantalla
     * que no existe.
     */
    @Override
    public void showErrorDialog(final String message, final String title) {
        System.err.println("  [ERROR] " + title + ": " + message);
        if (!interactive() || table == null) {
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui == null) {
            return;
        }
        ui.runLater(() -> {
            final ConfirmDialog dialog = new ConfirmDialog(
                    title, message, List.of(NeoText.get("common.accept")), 0,
                    index -> table.getOverlay().hide());
            table.getOverlay().show(dialog);
        });
    }

    /**
     * Fases en las que SI queremos que el juego se pare.
     *
     * <p>En todas las demas se pasa solo. Es la clave de que jugar no sea
     * pesado: sin esto hay que dar OK en cada paso del turno del rival.
     */
    private final java.util.Set<PhaseType> stops = java.util.Collections.synchronizedSet(
            java.util.EnumSet.of(
                    PhaseType.MAIN1,
                    PhaseType.MAIN2,
                    PhaseType.COMBAT_DECLARE_ATTACKERS,
                    PhaseType.COMBAT_DECLARE_BLOCKERS));

    /** Marca o desmarca una parada, como los "stops" de Arena. */
    public void toggleStop(final PhaseType phase) {
        if (!stops.remove(phase)) {
            stops.add(phase);
        }
    }

    public boolean hasStop(final PhaseType phase) {
        return stops.contains(phase);
    }

    /**
     * El motor pregunta: "esta fase la salto sin preguntarte?".
     *
     * <p>Cuidado con la logica de Forge ({@code YieldController}):
     * <ul>
     *   <li>Si el stack esta VACIO y decimos que si, se salta sin mas.</li>
     *   <li>Si el stack tiene algo, esto se ignora y manda
     *       {@code hasAvailableActions()}: si puedes responder, te para.</li>
     * </ul>
     * O sea que decir "salta" nunca te impide reaccionar a un hechizo del
     * rival. Es justo el comportamiento de Arena.
     */
    @Override
    public boolean isUiSetToSkipPhase(final PlayerView player, final PhaseType phase) {
        return phase != null && !stops.contains(phase);
    }

    @Override
    public GameState getGamestate() {
        // Puerta trasera al estado vivo. Solo para depurar; la GUI lee *View.
        return null;
    }

    // ---------------------------------------------------------------
    // Zonas que hay que ABRIR para poder contestar
    // ---------------------------------------------------------------

    /**
     * "Abre estas zonas, que el jugador tiene que clicar dentro."
     *
     * <p><b>Esto no es decoracion: es el unico camino para contestar a media
     * Magic.</b> Cuando un hechizo tiene por objetivo una carta que no esta en
     * la mesa — <i>"devuelve como objetivo una carta de criatura de tu
     * cementerio"</i>, y con ella Regrowth, Eternal Witness, Regenerar y
     * cientos mas — el motor <b>no</b> levanta ningun dialogo. Lo que hace es
     * {@code TargetSelection.chooseTargets} → {@code openZones(...)} y despues
     * un {@code InputSelectTargets}, que marca las cartas con
     * {@code setSelectables} y <b>se queda esperando un click sobre ellas</b>,
     * alli donde esten.
     *
     * <p>Devolviendo null aqui — que es lo que se hacia — esas cartas no se
     * pintaban en ningun sitio, no habia forma de clicarlas, OK no se activaba
     * nunca y el jugador veia esto: <i>"lance la carta que devuelve algo del
     * cementerio, le di a OK y no paso nada, y tenia criaturas en el
     * cementerio"</i>. No era un fallo del motor ni del jugador: era una
     * peticion que la interfaz no ensenyaba.
     *
     * <p>Se ignoran la mesa y la mano porque esas ya estan pintadas y ya se
     * clican. Lo demas se abre en el visor de zona, que desde ahora sabe marcar
     * y elegir. No bloquea: solo publica el panel y vuelve — quien espera es el
     * {@code showAndWait} del input, despues, y en el hilo del motor.
     *
     * @return lo que se ha abierto, para que {@link #restoreOldZones} lo cierre
     */
    @Override
    public PlayerZoneUpdates openZones(final PlayerView controller, final Collection<ZoneType> zones,
                                       final Map<PlayerView, Object> players,
                                       final boolean backupLastZones) {
        if (!interactive() || zones == null || players == null) {
            return null;
        }
        final PlayerZoneUpdates opened = new PlayerZoneUpdates();
        for (final ZoneType zone : zones) {
            if (zone == null || zone == ZoneType.Battlefield || zone == ZoneType.Hand
                    || zone == ZoneType.Stack) {
                continue;
            }
            for (final PlayerView owner : players.keySet()) {
                if (owner != null && owner.getZoneSize(zone) > 0) {
                    opened.add(new PlayerZoneUpdate(owner, zone));
                }
            }
        }
        if (opened.isEmpty()) {
            return null;
        }
        // Se abre UNA. Con objetivos repartidos entre varias zonas, el visor
        // se cierra con un click y la pila de cada zona lo vuelve a abrir, que
        // desde ahora tambien deja elegir: no queda nada inalcanzable.
        final PlayerZoneUpdate first = opened.iterator().next();
        final ZoneType firstZone = first.getZones().isEmpty()
                ? null : first.getZones().iterator().next();
        if (firstZone == null) {
            return null;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui != null) {
            ui.runLater(() -> table.showZone(first.getPlayer(), firstZone));
        }
        return opened;
    }

    /**
     * Se acabo la eleccion: se cierra lo que abrimos.
     *
     * <p>Solo si lo que hay puesto es nuestro visor. Si el jugador ya lo cerro
     * y abrio otra cosa, cerrarlo a ciegas le quitaria de delante algo que si
     * habia pedido.
     */
    @Override
    public void restoreOldZones(final PlayerView playerView, final PlayerZoneUpdates zonesToRestore) {
        if (zonesToRestore == null || !interactive()) {
            return;
        }
        final UiDispatcher ui = uiDispatcher();
        if (ui != null) {
            ui.runLater(() -> table.hideZoneViewer());
        }
    }

    @Override
    public Iterable<PlayerZoneUpdate> tempShowZones(final PlayerView controller,
                                                    final Iterable<PlayerZoneUpdate> zones) {
        return zones;
    }

    @Override
    public void hideZones(final PlayerView playerView, final Iterable<PlayerZoneUpdate> zones) { }
}
