package forge.neo.match;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.LobbyPlayer;
import forge.game.GameEntity;
import forge.game.GameEntityView;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;
import forge.game.trigger.Trigger;
import forge.player.PlayerControllerHuman;

/**
 * De QUE carta habla un disparo cuando te pide elegir modo.
 *
 * <p><b>De donde sale esto.</b> Reportado jugando con <i>Jin Sakai, Ghost of
 * Tsushima</i> en un Commander a cuatro: cada vez que una criatura tuya ataca
 * sola a un jugador, eliges para ella dobleataque o que no se pueda bloquear.
 * Atacas con tres criaturas, una a cada rival, y saltan tres disparos. Los tres
 * preguntan con el mismo titulo ("Ana activo Jin Sakai - Elige un modo") y los
 * mismos dos modos ("<i>Gana</i> dobleataque", "<i>No puede</i> ser
 * bloqueada")... y ninguno dice de QUE criatura habla. Si a una le quieres dar
 * una cosa y a otra la otra, es a ciegas.
 *
 * <p><b>El motor lo sabe, pero no nos lo manda.</b> El modo lo pregunta
 * {@code PlayerControllerHuman.chooseModeForAbility}, que recibe la habilidad
 * entera — con sus <i>objetos disparadores</i>: aqui {@code Attackers}, con la
 * criatura que ataco — y a la interfaz solo le pasa un titulo y los modos
 * ({@code getGui().one(titulo, modos)}). Ese controlador es el mismo para las
 * dos GUIs oficiales, asi que tienen el mismo hueco.
 *
 * <p><b>Como se recupera sin tocar el motor.</b> Igual que {@link ManaColor} y
 * {@link SafeActions}: sentandose en el controlador. {@code chooseModeForAbility}
 * es publico y sobrescribible; aqui se miran los objetos disparadores, se apunta
 * de que cartas habla el disparo mientras dura la pregunta, y la pregunta la
 * sigue haciendo el motor, con su titulo y sus modos de siempre.
 * {@link NeoMatchUI#getChoices} lo recoge y el dialogo ensenya esas cartas
 * debajo del titulo.
 *
 * <p><b>Por que un {@code ThreadLocal}.</b> Entre el controlador y el dialogo
 * no hay donde dejar el dato: {@code one(...)} no lleva mas parametros. Pero las
 * dos llamadas van por el <b>mismo hilo</b> — el motor pregunta y se queda
 * esperando — asi que lo que se apunta en el hilo al preguntar es exactamente lo
 * que ve {@code getChoices} un instante despues. Y se repone al contestar: una
 * pregunta no puede heredar las cartas de otra.
 *
 * <p><b>Que cartas.</b> Las de los objetos disparadores, sin la fuente del
 * disparo — esa ya la nombra el titulo — y solo si son pocas: un "una o mas
 * criaturas mueren" con veinte cartas no habla de ninguna en concreto, y veinte
 * miniaturas taparian los modos. Si la carta esta atacando, se dice ademas a
 * QUIEN: con dos fichas iguales atacando a dos rivales, la imagen sola no las
 * distingue y el rival si.
 *
 * <p><b>Y de QUIEN habla</b> (Discord, 09-10-2026, <i>Parapet Thrasher</i> en
 * una partida a cinco: <i>"the burn damage does not target 1 of my
 * friends"</i>). "Cuando tus Dragones hacen dano de combate a un rival, elige
 * uno que no hayas elegido este turno: ... o 4 de dano a cada OTRO rival".
 * Tres rivales danados son tres disparos por cada Dragon, y cada uno pregunta
 * el modo con el mismo titulo y sin decir de que rival es. El jugador escogia
 * a ciegas, el de 4 de dano acababa siempre en el disparo de Lisian... y a
 * Lisian, que es "ese" rival, no le llegaba nunca. El motor lo hizo bien; lo
 * que faltaba era el dato. Ahora va una linea bajo el titulo con lo mismo que
 * el disparo ensenya en el stack ({@code Trigger.getImportantStackObjects}:
 * "Damaged: Lisian, Amount: 4"), ya traducida por el motor y valida para
 * cualquier tipo de disparo. La lee {@link #detailFor}, en el PC y en Android.
 *
 * <p><b>Lo que no hace.</b> No toca la regla ni el orden de los disparos, ni
 * cambia el "ella" del texto por el nombre (seria reescribir el texto del motor
 * en diez idiomas). En red solo sale en el asiento del anfitrion: al invitado la
 * pregunta le llega por el protocolo, que no lleva este dato.
 */
public final class TriggerSubject {

    private TriggerSubject() {
    }

    /**
     * Mas de estas ya no es "esta criatura", es un grupo — y el texto del
     * disparo en el stack ya lo cuenta. Es tambien lo que cabe en una fila del
     * dialogo sin empujar los modos fuera de la ventana.
     */
    static final int MAX_SUBJECTS = 4;

    /** Una carta de la que habla el disparo, y a quien ataca si esta atacando. */
    public record Subject(CardView card, GameEntityView attacking) {
    }

    /** Lo que se apunta de un disparo: sus cartas y su linea del stack. */
    private record Asking(List<Subject> subjects, String detail) {
    }

    /** Lo apuntado para la pregunta de modo que se esta haciendo en este hilo. */
    private static final ThreadLocal<Asking> ASKING = new ThreadLocal<>();

    /**
     * Las cartas de la pregunta que llega, si es una eleccion de modo.
     *
     * <p>Solo cuando TODAS las opciones son modos ({@code SpellAbilityView}):
     * cualquier otra cosa que el motor preguntara por el camino no habla de
     * estas cartas y no debe ensenyarlas.
     */
    public static List<Subject> forChoices(final List<?> choices) {
        final Asking asking = ASKING.get();
        if (asking == null || !allModes(choices)) {
            return List.of();
        }
        return asking.subjects();
    }

    /**
     * La linea del disparo cuyo modo se pregunta ("Damaged: Lisian, Amount:
     * 4"), o {@code null}. Con el mismo cuidado que {@link #forChoices}: solo
     * si todas las opciones son modos.
     */
    public static String detailFor(final List<?> choices) {
        final Asking asking = ASKING.get();
        return asking == null || !allModes(choices) ? null : asking.detail();
    }

    /**
     * Lo mismo, pero mirando tambien el titulo: a un INVITADO de una partida
     * en red la linea le llega ahi (ver {@link #wire}), porque este hilo, y
     * con el lo apuntado, se queda en el ordenador del anfitrion.
     */
    public static String detailFor(final List<?> choices, final String message) {
        final String local = detailFor(choices);
        if (local != null || !allModes(choices)) {
            return local;
        }
        return wiredDetail(message);
    }

    /**
     * Marca invisible (U+2063, "separador invisible") entre el titulo de la
     * pregunta y la linea del disparo, cuando la pregunta viaja a un invitado.
     * Un invitado con una version anterior la ve como una segunda linea del
     * titulo, que tambien sirve; uno al dia la quita y la pinta como en casa.
     */
    private static final String WIRE_MARK = "\n⁣";

    /** La linea que trae el titulo, o null. */
    static String wiredDetail(final String message) {
        if (message == null) {
            return null;
        }
        final int i = message.indexOf(WIRE_MARK);
        if (i < 0) {
            return null;
        }
        final String d = message.substring(i + WIRE_MARK.length()).trim();
        return d.isEmpty() ? null : d;
    }

    /** El titulo sin la linea que trae pegada, si la trae. */
    public static String titleOf(final String message) {
        if (message == null) {
            return null;
        }
        final int i = message.indexOf(WIRE_MARK);
        return i < 0 ? message : message.substring(0, i);
    }

    /** Para el comprobador: hacer como si el asiento fuera el de un invitado. */
    static volatile boolean wireForTests;

    /**
     * Si esta interfaz es la de un invitado de una partida en red
     * ({@code RemoteClientGuiGame}, la que manda las preguntas por el cable).
     * Por NOMBRE y no con {@code instanceof}: esta clase la carga tambien
     * Android, y asi no depende de que esa clase este.
     */
    private static boolean isRemote(final forge.gui.interfaces.IGuiGame gui) {
        for (Class<?> c = gui == null ? null : gui.getClass(); c != null; c = c.getSuperclass()) {
            if ("forge.gamemodes.net.server.RemoteClientGuiGame".equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * La interfaz del invitado, con la linea pegada al titulo de las preguntas
     * de modo ({@code one} / {@code oneOrNone}, las dos que hace
     * {@code chooseModeForAbility}). Todo lo demas pasa tal cual. Solo vive lo
     * que dura la pregunta.
     */
    private static forge.gui.interfaces.IGuiGame wire(final forge.gui.interfaces.IGuiGame gui, final String detail) {
        return (forge.gui.interfaces.IGuiGame) java.lang.reflect.Proxy.newProxyInstance(
                forge.gui.interfaces.IGuiGame.class.getClassLoader(),
                new Class<?>[] {forge.gui.interfaces.IGuiGame.class},
                (proxy, method, args) -> {
                    final String name = method.getName();
                    if (("one".equals(name) || "oneOrNone".equals(name))
                            && args != null && args.length >= 2 && args[0] instanceof String title) {
                        args[0] = title + WIRE_MARK + detail;
                    }
                    try {
                        return method.invoke(gui, args);
                    } catch (final java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static boolean allModes(final List<?> choices) {
        if (choices == null || choices.isEmpty()) {
            return false;
        }
        for (final Object o : choices) {
            if (!(o instanceof SpellAbilityView)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lo que el disparo ensenya entre corchetes en el stack: a quien ha danado,
     * quien ataca, cuanto... Lo escribe el propio motor para cada tipo de
     * disparo, ya en el idioma de la partida. {@code null} si no es un disparo
     * o no tiene nada que decir.
     */
    static String detail(final SpellAbility sa) {
        if (sa == null) {
            return null;
        }
        final Trigger trigger = sa.getTrigger();
        if (trigger == null) {
            return null;
        }
        final String text = trigger.getImportantStackObjects(sa);
        // trim().isEmpty() y no isBlank(): esto lo carga tambien Android.
        return text == null || text.trim().isEmpty() ? null : text.trim();
    }

    /**
     * De que cartas habla este disparo.
     *
     * <p>Vacio si no es un disparo (un hechizo modal no tiene "ella" de quien
     * hablar), si no trae cartas o si trae demasiadas.
     */
    static List<Subject> of(final SpellAbility sa) {
        if (sa == null || !sa.isTrigger()) {
            return List.of();
        }
        final Map<AbilityKey, Object> objects = sa.getTriggeringObjects();
        if (objects == null || objects.isEmpty()) {
            return List.of();
        }
        final Card host = sa.getHostCard();
        final Combat combat = host == null || host.getGame() == null
                ? null : host.getGame().getCombat();
        // Por id: la misma carta llega a menudo dos veces — la de ahora y su
        // copia "tal y como era" (LKI) — y para el jugador es una sola.
        final Map<Integer, Subject> found = new LinkedHashMap<>();
        for (final Object value : objects.values()) {
            if (value instanceof Card one) {
                add(found, one, host, combat);
            } else if (value instanceof Iterable<?> many) {
                for (final Object o : many) {
                    if (o instanceof Card each) {
                        add(found, each, host, combat);
                    }
                    if (found.size() > MAX_SUBJECTS) {
                        return List.of();
                    }
                }
            }
            if (found.size() > MAX_SUBJECTS) {
                return List.of();
            }
        }
        return List.copyOf(found.values());
    }

    private static void add(final Map<Integer, Subject> found, final Card card,
                            final Card host, final Combat combat) {
        if (host != null && card.getId() == host.getId()) {
            return;
        }
        if (found.containsKey(card.getId())) {
            return;
        }
        GameEntityView attacking = null;
        if (combat != null) {
            final GameEntity defender = combat.getDefenderByAttacker(card);
            if (defender != null) {
                attacking = defender.getView();
            }
        }
        found.put(card.getId(), new Subject(card.getView(), attacking));
    }

    /**
     * {@link #of}, sin que un fallo al mirar se lleve la pregunta por delante.
     *
     * <p>Esto es un anyadido de interfaz: si algo revienta al leer los objetos
     * disparadores, la pregunta se hace igual que antes. Se dice por la salida,
     * eso si — un fallo tapado en silencio no lo arregla nadie.
     */
    private static List<Subject> subjectsOrNothing(final SpellAbility sa) {
        try {
            return of(sa);
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se ha podido saber de que carta habla el disparo: " + e);
            return List.of();
        }
    }

    /** {@link #detail}, con la misma red que {@link #subjectsOrNothing}. */
    private static String detailOrNothing(final SpellAbility sa) {
        try {
            return detail(sa);
        } catch (final RuntimeException e) {
            System.out.println("[neo] no se ha podido leer la linea del disparo: " + e);
            return null;
        }
    }

    /**
     * El controlador humano de siempre, diciendo de que carta habla el disparo.
     *
     * <p>Va en la misma silla que los otros arreglos con asiento:
     * {@link AttackCosts.Confirming} hereda de este (y {@link ManaColor} de
     * aquel), y este de {@link SafeActions.Guarded} (ver por que en
     * {@code ManaColor}). Se sobrescribe un metodo; lo demas es Forge sin tocar.
     */
    public static class Telling extends SafeActions.Guarded {

        public Telling(final Player player, final LobbyPlayer lobby,
                       final PlayerControllerHuman owner) {
            super(player, lobby, owner);
        }

        @Override
        public List<AbilitySub> chooseModeForAbility(final SpellAbility sa,
                                                     final List<AbilitySub> possible,
                                                     final int min, final int num,
                                                     final boolean allowRepeat) {
            // Se apunta SIEMPRE, aunque sea vacio, y se repone lo de antes: si
            // esta pregunta fuera por dentro de otra, no puede quedarse con las
            // cartas de la de fuera.
            final Asking outer = ASKING.get();
            final Asking now = new Asking(subjectsOrNothing(sa), detailOrNothing(sa));
            ASKING.set(now);
            // Al INVITADO de una partida en red este hilo no le llega: la linea
            // del disparo viaja pegada al titulo de la pregunta (Discord,
            // 10-10-2026: el del Parapet Thrasher a cinco jugaba en red).
            final forge.gui.interfaces.IGuiGame gui = getGui();
            final boolean wired = now.detail() != null && (wireForTests || isRemote(gui));
            if (wired) {
                setGui(wire(gui, now.detail()));
            }
            try {
                return super.chooseModeForAbility(sa, possible, min, num, allowRepeat);
            } finally {
                if (wired) {
                    setGui(gui);
                }
                if (outer == null) {
                    ASKING.remove();
                } else {
                    ASKING.set(outer);
                }
            }
        }
    }
}
