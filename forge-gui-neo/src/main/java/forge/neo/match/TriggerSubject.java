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

    /** Lo apuntado para la pregunta de modo que se esta haciendo en este hilo. */
    private static final ThreadLocal<List<Subject>> ASKING = new ThreadLocal<>();

    /**
     * Las cartas de la pregunta que llega, si es una eleccion de modo.
     *
     * <p>Solo cuando TODAS las opciones son modos ({@code SpellAbilityView}):
     * cualquier otra cosa que el motor preguntara por el camino no habla de
     * estas cartas y no debe ensenyarlas.
     */
    public static List<Subject> forChoices(final List<?> choices) {
        final List<Subject> subjects = ASKING.get();
        if (subjects == null || subjects.isEmpty() || choices == null || choices.isEmpty()) {
            return List.of();
        }
        for (final Object o : choices) {
            if (!(o instanceof SpellAbilityView)) {
                return List.of();
            }
        }
        return subjects;
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
            final List<Subject> outer = ASKING.get();
            ASKING.set(subjectsOrNothing(sa));
            try {
                return super.chooseModeForAbility(sa, possible, min, num, allowRepeat);
            } finally {
                if (outer == null) {
                    ASKING.remove();
                } else {
                    ASKING.set(outer);
                }
            }
        }
    }
}
