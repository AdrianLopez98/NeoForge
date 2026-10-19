package forge.neo.match;

import forge.game.Game;
import forge.game.GameEntity;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.combat.Combat;
import forge.game.combat.CombatUtil;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputBlock;
import forge.gamemodes.match.input.InputPassPriority;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * "¿Bloquear con esta criatura o usar sus habilidades?": cuando tocarla AHORA
 * no bloquea, aunque lo parezca.
 *
 * <p>Discord, 19-10-2026: <i>"attempting to block with Devoted Druid in the
 * blockers phase, and I can't seem to use it as a blocker without just
 * activating one of the card abilities"</i>, y lo mismo con Auntie Ool y con
 * Cankerbloom. No es el motor ni el bloqueo: es <b>cuando</b>. El rival declara
 * sus atacantes y tu recibes prioridad en el paso de ATACANTES, antes de la
 * declaracion de bloqueadores. El pase automatico solo para ahi si tienes algo
 * que usar — o sea justo con criaturas con habilidades —, la flecha del ataque
 * ya se ve, y tocar la criatura activa su habilidad (es lo que debe hacer: hay
 * prioridad). Con criaturas sin habilidades esa parada no existe y se bloquea
 * como se espera. Le pasaba igual en la interfaz de Forge.
 *
 * <p>Las dos interfaces preguntan entonces, y "Bloquear" pasa la prioridad y,
 * en cuanto el motor abre la declaracion, toca esa criatura por el jugador
 * ({@link #isDeclaringBlocks}): el mismo {@code selectCard} que un toque. Nada
 * de reglas nuestras: si no puede bloquear a ese atacante, el motor dice que no.
 */
public final class BlockIntent {

    private BlockIntent() {
    }

    /**
     * Si tocar {@code card} ahora mismo seria, casi seguro, querer bloquear con
     * ella: paso de atacantes del rival, prioridad tuya con la pila vacia, te
     * atacan (a ti o a algo tuyo), y es una criatura tuya enderezada que puede
     * bloquear.
     */
    public static boolean wouldMeanBlock(final IGameController controller, final CardView card) {
        if (!(controller instanceof PlayerControllerHuman human) || card == null) {
            return false;
        }
        try {
            if (!(human.getInputQueue().getInput() instanceof InputPassPriority)) {
                return false;
            }
            final Player me = human.getPlayer();
            final Game game = me == null ? null : me.getGame();
            if (game == null || game.getPhaseHandler().getPhase() != PhaseType.COMBAT_DECLARE_ATTACKERS
                    || me.equals(game.getPhaseHandler().getPlayerTurn()) || !game.getStack().isEmpty()) {
                return false;
            }
            final Combat combat = game.getCombat();
            if (combat == null || !attacksMe(combat, me)) {
                return false;
            }
            final Card c = game.findById(card.getId());
            return c != null && c.isCreature() && !c.isTapped() && me.equals(c.getController())
                    && c.isInZone(ZoneType.Battlefield) && CombatUtil.canBlock(c);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** Si el motor esta ahora en la declaracion de bloqueadores de este jugador. */
    public static boolean isDeclaringBlocks(final IGameController controller) {
        return controller instanceof PlayerControllerHuman human
                && human.getInputQueue().getInput() instanceof InputBlock;
    }

    private static boolean attacksMe(final Combat combat, final Player me) {
        for (final GameEntity d : combat.getDefenders()) {
            final boolean mine = d.equals(me)
                    || (d instanceof Card dc && me.equals(dc.getController()));
            if (mine && !combat.getAttackersOf(d).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
