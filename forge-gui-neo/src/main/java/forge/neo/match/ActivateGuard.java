package forge.neo.match;

import java.util.List;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * <b>"¿Activar Merfolk Looter?"</b> antes de que un toque en un permanente
 * active su habilidad.
 *
 * <p>Discord, 11-10-2026: <i>"the ability to back out of a tap effect without
 * triggering it in case you tried to either click a different card or, say,
 * you were trying to block and the creature wasn't properly selected"</i>. Con
 * una sola habilidad que se pueda activar, el motor la activa directamente al
 * clicar la carta ({@code InputPassPriority.onCardSelected} →
 * {@code getAbilityToPlay}, que con una no pregunta), y el pase automatico
 * suelta la prioridad con ella en la pila: se resuelve sin dar tiempo a nada.
 * El deshacer nuevo de Forge (el boton "Deshacer") no llega, porque la
 * prioridad ya se ha ido.
 *
 * <p>Aqui vive SOLO la pregunta "¿este toque activaria una habilidad?", sin
 * JavaFX: la usan las dos interfaces (Android por el jar), igual que
 * {@link PickGuard}. Se pregunta si:
 *
 * <ul>
 *   <li>el motor espera la prioridad ({@code InputPassPriority}): ni atacar, ni
 *       bloquear, ni elegir objetivos, ni pagar — ahi el toque significa otra
 *       cosa y ya tiene su vuelta atras;</li>
 *   <li>la carta es un permanente tuyo en la mesa;</li>
 *   <li>y tiene UNA habilidad que se puede activar ahora y que no es de mana.
 *       Con varias el motor ya pregunta cual, y ese menu se puede cerrar; las de
 *       mana (tierras, elfos) se usan sin parar y se deshacen con "Deshacer".</li>
 * </ul>
 *
 * <p>Apagado de fabrica, como los otros dos: quien juega rapido no lo quiere.
 * Ajustes → "Preguntar antes de activar una habilidad"
 * ({@link forge.neo.NeoSettings#CONFIRM_ACTIVATE}). En una partida en red, como
 * invitado, el controlador no es local y no se pregunta.
 */
public final class ActivateGuard {

    private ActivateGuard() {
    }

    /**
     * Si este toque activaria una habilidad y su ajuste esta puesto: la de un
     * permanente ({@code CONFIRM_ACTIVATE}, apagado de fabrica) o una desde la
     * MANO en vez de lanzar la carta ({@code CONFIRM_HAND_ABILITY}, encendido).
     */
    public static boolean shouldAsk(final IGameController controller, final CardView card) {
        return (forge.neo.NeoSettings.confirmActivate() && abilityFor(controller, card) != null)
                || (forge.neo.NeoSettings.confirmHandAbility() && handAbilityFor(controller, card) != null);
    }

    /** Si la pregunta es por una carta de la mano (para el titulo: "no se puede lanzar ahora"). */
    public static boolean fromHand(final IGameController controller, final CardView card) {
        return handAbilityFor(controller, card) != null;
    }

    /**
     * <b>Ciclar sin querer</b> (Discord, 17-10-2026: <i>"Can we get a confirmation
     * before Cycling cards? I do it too many times accidentally when things are
     * on the stack and I mean to play the actual card"</i>). Con algo en la
     * pila una criatura no se puede lanzar, y lo UNICO que puede hacer la carta
     * es ciclarse; con una sola opcion el motor no pregunta, y la carta se va.
     *
     * <p>La habilidad que haria este toque sobre una carta de TU MANO, si es lo
     * unico que puede hacer y no es lanzarla: ciclo, canalizar, ninjutsu,
     * conspirar... Null si se puede lanzar (entonces es lo que se quiere, o hay
     * varias y el motor ya pregunta cual), si es jugar una tierra o si es de
     * mana (se usa al pagar).
     */
    public static SpellAbility handAbilityFor(final IGameController controller, final CardView card) {
        if (card == null || !(controller instanceof PlayerControllerHuman human)) {
            return null;
        }
        try {
            if (!(human.getInputQueue().getInput() instanceof InputPassPriority)) {
                return null;
            }
            final Player me = human.getPlayer();
            final Game game = me == null ? null : me.getGame();
            final Card c = game == null ? null : game.findById(card.getId());
            if (c == null || !c.isInZone(ZoneType.Hand) || c.getOwner() != me) {
                return null;
            }
            final List<SpellAbility> abilities = c.getAllPossibleAbilities(me, true);
            if (abilities.size() != 1) {
                return null;
            }
            final SpellAbility sa = abilities.get(0);
            return !sa.isSpell() && !sa.isLandAbility() && !sa.isManaAbility() ? sa : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * La habilidad que activaria este toque, o null si no activaria ninguna
     * (o si activaria una de mana, o si el motor va a preguntar cual).
     */
    public static SpellAbility abilityFor(final IGameController controller, final CardView card) {
        if (card == null || !(controller instanceof PlayerControllerHuman human)) {
            return null;
        }
        try {
            if (!(human.getInputQueue().getInput() instanceof InputPassPriority)) {
                return null;
            }
            final Player me = human.getPlayer();
            final Game game = me == null ? null : me.getGame();
            final Card c = game == null ? null : game.findById(card.getId());
            if (c == null || !c.isInZone(ZoneType.Battlefield) || c.getController() != me) {
                return null;
            }
            final List<SpellAbility> abilities = c.getAllPossibleAbilities(me, true);
            if (abilities.size() != 1) {
                return null;
            }
            final SpellAbility sa = abilities.get(0);
            return sa.isActivatedAbility() && !sa.isManaAbility() ? sa : null;
        } catch (final RuntimeException e) {
            // Leer la partida desde fuera puede tropezar: entonces no se pregunta.
            return null;
        }
    }

    /** Lo que hace esa habilidad, ya traducido por el motor, para la pregunta. */
    public static String describe(final IGameController controller, final CardView card) {
        SpellAbility sa = abilityFor(controller, card);
        if (sa == null) {
            sa = handAbilityFor(controller, card);
        }
        if (sa == null) {
            return "";
        }
        try {
            final String text = sa.getView().getDescription();
            return text == null || text.isBlank() ? String.valueOf(sa) : text.trim();
        } catch (final RuntimeException e) {
            return String.valueOf(sa);
        }
    }
}
