package forge.neo.tutorial;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.combat.CombatView;
import forge.game.event.GameEvent;
import forge.game.event.GameEventLandPlayed;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.event.GameEventSpellResolved;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;

/**
 * Lo que un paso del tutorial puede mirar para saber si ya esta hecho.
 *
 * <p>Un paso del tutorial no se da por bueno porque lo diga un boton: se da por
 * bueno cuando el jugador <b>hace</b> lo que se le pide. Y quien sabe lo que ha
 * pasado de verdad es el motor, asi que esto es una ventana de solo lectura a
 * lo que el motor cuenta ({@code GameEvent}) y al estado de la partida
 * ({@code GameView}).
 *
 * <p><b>Esto TIENE MEMORIA, y ahi estaba el fallo.</b> La primera version
 * miraba solo el evento que acababa de llegar, en el instante en que llegaba, y
 * eso deja tres agujeros por los que un paso se queda colgado <b>para siempre</b>
 * sin dar ni un aviso:
 *
 * <ul>
 *   <li><b>Las condiciones de estado son instantaneas.</b> "Estas en el paso de
 *       atacantes" solo es cierto mientras dura esa fase, y solo se miraba
 *       cuando llegaba un evento. Los eventos llegan <b>en lotes</b>
 *       ({@code GameEventForwarder} junta hasta 50, o medio segundo) y ademas
 *       se cruzan al hilo de interfaz con {@code runOnUi}, asi que cuando por
 *       fin se miraba, la fase ya podia haber pasado.</li>
 *   <li><b>Lo que pasa entre dos pasos se pierde.</b> Entre "hecho" y el paso
 *       siguiente hay 850 ms de acuse de recibo; lo que ocurra ahi no lo ve
 *       nadie.</li>
 *   <li><b>Un evento que no llega deja el paso muerto.</b> Sin una segunda
 *       fuente, no hay red de seguridad.</li>
 * </ul>
 *
 * <p>Asi que ahora un probe <b>vive lo que dura el paso</b> y va apuntando lo
 * que va pasando, de <b>dos</b> fuentes que se tapan los agujeros la una a la
 * otra: los eventos del motor y una lectura del {@code GameView} cada vez que
 * se le da de comer. Un paso pregunta "ya ha pasado esto", no "esta pasando
 * justo ahora".
 *
 * <p><b>Cero logica de reglas aqui.</b> No se calcula si algo es legal ni si un
 * ataque es bueno: se apunta lo que el motor dice que ha pasado.
 */
public final class TutorialProbe {

    private GameView game;
    private PlayerView self;
    private GameEvent event;

    /** Las clases de evento que han llegado desde que empezo el paso. */
    private final Set<Class<?>> seen = new HashSet<>();

    /** Las fases por las que ha pasado la partida en TU turno, desde que empezo el paso. */
    private final EnumSet<PhaseType> myPhases = EnumSet.noneOf(PhaseType.class);

    private boolean land;
    private boolean anySpell;
    private boolean creatureSpell;
    private boolean instantSpell;
    private boolean sorcerySpell;
    private boolean ability;
    private boolean attacked;

    /** Solo para {@link #everything()}: contesta que si a todo lo que se apunta. */
    private boolean all;

    public TutorialProbe() {
    }

    /**
     * Un probe al que ya le ha pasado todo.
     *
     * <p>Lo usa {@code run.cmd tutorialcheck} para cazar el fallo mas caro que
     * puede tener un paso: una condicion que <b>no puede volverse cierta
     * nunca</b>. Eso no revienta ni deja rastro — el paso se queda ahi puesto,
     * el jugador hace lo que se le pide una y otra vez y no pasa nada. Si un
     * paso dice que no con esto delante, es que esta mal escrito.
     *
     * <p>Ojo a lo que <b>no</b> contesta: {@link #phase()} sigue devolviendo
     * {@code null}, a proposito. Un paso no debe preguntar en que fase estamos
     * <i>justo ahora</i> — dura un suspiro — sino {@link #reachedOnMyTurn}.
     */
    public static TutorialProbe everything() {
        final TutorialProbe p = new TutorialProbe();
        p.all = true;
        p.land = true;
        p.anySpell = true;
        p.creatureSpell = true;
        p.instantSpell = true;
        p.sorcerySpell = true;
        p.ability = true;
        p.attacked = true;
        p.myPhases.addAll(EnumSet.allOf(PhaseType.class));
        return p;
    }

    /** Atajo para una comprobacion suelta (lo usa quien no lleva memoria). */
    public TutorialProbe(final GameView game, final PlayerView self, final GameEvent event) {
        feed(game, self, event);
    }

    // ---------------------------------------------------------------
    // Darle de comer
    // ---------------------------------------------------------------

    /**
     * Apunta lo que se sepa ahora mismo.
     *
     * <p>Se llama con un evento cuando el motor cuenta algo, y con
     * {@code null} en un repaso: al ensenyar el paso, y cada vez que la mesa se
     * refresca. Las dos cosas hacen falta, porque hay pasos que se cierran por
     * un evento y pasos que se cierran porque la partida <b>esta</b> de cierta
     * manera, y ninguna de las dos fuentes llega siempre.
     */
    public void feed(final GameView game0, final PlayerView self0, final GameEvent event0) {
        if (game0 != null) {
            this.game = game0;
        }
        if (self0 != null) {
            this.self = self0;
        }
        this.event = event0;

        if (event0 != null) {
            seen.add(event0.getClass());
            noteEvent(event0);
        }
        noteState();
    }

    /** Lo que se deduce del evento que acaba de llegar. */
    private void noteEvent(final GameEvent e) {
        if (e instanceof GameEventLandPlayed lp && isMe(lp.player())) {
            land = true;
        }
        // La RESOLUCION de un hechizo vale igual que su lanzamiento, y hace
        // falta: si el aviso de que entra al stack se pierde, este llega
        // despues y salva el paso.
        //
        // Solo para HECHIZOS, a proposito. Este aviso no dice quien lo activo
        // ni si era un disparo, asi que para una habilidad no se puede
        // distinguir "la has activado tu" de "ha saltado un disparo" — y
        // apuntarlo de mas cerraria el paso solo, sin que el jugador toque
        // nada. De un hechizo si se sabe de quien es: por su carta.
        if (e instanceof GameEventSpellResolved done && done.spell() != null
                && done.spell().isSpell() && isMine(done.spell().getHostCard())) {
            anySpell = true;
            noteSpellType(done.spell().getHostCard());
        }
        if (!(e instanceof GameEventSpellAbilityCast cast) || cast.sa() == null) {
            return;
        }
        // Quien lo activo lo dice el objeto del stack. Si no viene, se acepta:
        // en el tutorial solo hay un rival, y sale mas caro perder el paso que
        // contar de mas.
        if (cast.si() != null && cast.si().getActivatingPlayer() != null
                && !isMe(cast.si().getActivatingPlayer())) {
            return;
        }
        final boolean trigger = cast.si() != null && cast.si().isTrigger();
        if (!cast.sa().isSpell()) {
            // Un disparo pasa por el stack igual, pero no lo has activado tu.
            if (!trigger) {
                ability = true;
            }
            return;
        }
        anySpell = true;
        noteSpellType(cast.sa().getHostCard());
    }

    /** Lo que se deduce de como esta la partida ahora mismo. */
    private void noteState() {
        if (game == null) {
            return;
        }
        final PhaseType phase = game.getPhase();
        final boolean mine = myTurn();
        if (phase != null && mine) {
            myPhases.add(phase);
        }

        final CombatView combat = game.getCombat();
        if (mine && combat != null && combat.getNumAttackers() > 0) {
            attacked = true;
        }

        // La tierra, por estado. Y hace falta de verdad: el aviso del motor
        // (GameEventLandPlayed) es la UNICA fuente que tenia este gesto, y
        // resulta que en una partida local ese aviso no llega — Forge suscribe
        // su propio FControlGameEventHandler al bus y ese llama a updateCards,
        // handleLandPlayed y compania, pero NUNCA a handleGameEvent. O sea que
        // el paso "juega una tierra" no se cerraba jamas: no fallaba, se
        // quedaba ahi. Reportado jugando: "la juego y no pasa al siguiente".
        //
        // El contador lo lleva el propio motor y lo publica en la vista
        // (Player.addLandPlayedThisTurn -> view.updateNumLandThisTurn), asi
        // que no hay que contar nada: se pregunta.
        if (mine && self != null && self.getNumLandThisTurn() > 0) {
            land = true;
        }

        // El stack es la segunda fuente: si el aviso del lanzamiento se perdio
        // por el camino, el objeto sigue ahi hasta que resuelva.
        if (game.getStack() == null) {
            return;
        }
        for (final StackItemView item : game.getStack()) {
            if (item == null || item.isTrigger()) {
                continue;
            }
            if (item.getActivatingPlayer() != null && !isMe(item.getActivatingPlayer())) {
                continue;
            }
            if (item.isAbility()) {
                ability = true;
            } else {
                anySpell = true;
                noteSpellType(item.getSourceCard());
            }
        }
    }

    private void noteSpellType(final CardView host) {
        if (host == null || host.getCurrentState() == null
                || host.getCurrentState().getType() == null) {
            return;
        }
        final var type = host.getCurrentState().getType();
        if (type.isCreature()) {
            creatureSpell = true;
        }
        if (type.isInstant()) {
            instantSpell = true;
        }
        if (type.isSorcery()) {
            sorcerySpell = true;
        }
    }

    // ---------------------------------------------------------------
    // Lo que puede preguntar un paso
    // ---------------------------------------------------------------

    public GameView game() {
        return game;
    }

    public PlayerView self() {
        return self;
    }

    /** El ultimo evento que llego, o {@code null} si esto es un repaso. */
    public GameEvent raw() {
        return event;
    }

    /**
     * true si el motor ha contado algo de este tipo <b>desde que empezo el
     * paso</b>, no solo justo ahora.
     */
    public boolean is(final Class<? extends GameEvent> type) {
        if (all) {
            return true;
        }
        if (type.isInstance(event)) {
            return true;
        }
        for (final Class<?> c : seen) {
            if (type.isAssignableFrom(c)) {
                return true;
            }
        }
        return false;
    }

    /** En que fase esta la partida ahora mismo. */
    public PhaseType phase() {
        return game == null ? null : game.getPhase();
    }

    /**
     * true si la partida ha llegado a esa fase (o mas alla) <b>en tu turno</b>
     * desde que empezo el paso.
     *
     * <p>Es lo que hay que preguntar, y no {@code phase() == X}: una fase dura
     * un suspiro y para cuando el aviso cruza al hilo de interfaz puede haber
     * pasado ya. Ese fue el fallo que dejaba "Ve al combate" colgado despues de
     * haber atacado y ganado el combate entero.
     */
    public boolean reachedOnMyTurn(final PhaseType target) {
        if (target == null) {
            return false;
        }
        for (final PhaseType p : myPhases) {
            if (p.ordinal() >= target.ordinal()) {
                return true;
            }
        }
        return false;
    }

    /** true si el turno es tuyo. */
    public boolean myTurn() {
        return all || (game != null && self != null && isMe(game.getPlayerTurn()));
    }

    /** true si ese jugador soy yo. Compara por id, como el resto del proyecto. */
    public boolean isMe(final PlayerView who) {
        return who != null && self != null && who.getId() == self.getId();
    }

    /** true si esa carta la controlas tu. */
    private boolean isMine(final CardView card) {
        return card != null && isMe(card.getController());
    }

    // ---------------------------------------------------------------
    // Los gestos de partida que pide el tutorial
    // ---------------------------------------------------------------

    /** Has jugado una tierra desde que empezo el paso. */
    public boolean playedLand() {
        return land;
    }

    /**
     * Has lanzado un hechizo (no una habilidad) desde que empezo el paso.
     *
     * @param kind {@code "creature"}, {@code "instant"}, {@code "sorcery"} o
     *             {@code null} para cualquiera
     */
    public boolean castSpell(final String kind) {
        if (kind == null) {
            return anySpell;
        }
        return switch (kind) {
            case "creature" -> creatureSpell;
            case "instant" -> instantSpell;
            case "sorcery" -> sorcerySpell;
            default -> anySpell;
        };
    }

    /**
     * Has activado una habilidad de una carta tuya desde que empezo el paso.
     *
     * <p>Las habilidades de mana <b>no</b> entran por aqui, y eso es justo lo
     * que hace falta: no pasan por el stack, asi que el motor no las anuncia y
     * girar una tierra no cuenta como "activar una habilidad". Los disparos
     * tampoco: pasan por el stack, pero no los has activado tu.
     */
    public boolean activatedAbility() {
        return ability;
    }

    /** Has declarado atacantes desde que empezo el paso. */
    public boolean declaredAttackers() {
        return attacked;
    }
}
