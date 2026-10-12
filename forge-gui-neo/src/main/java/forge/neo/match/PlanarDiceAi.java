package forge.neo.match;

import forge.ai.AiPlayDecision;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * <b>Que la IA tire el dado planar</b> (Discord, 12-10-2026: <i>"I'm not sure
 * if the AI are rolling Planechase die"</i>). No lo tiraba nunca: 25 turnos de
 * IA contra IA con Quicksilver Sea, que le dice "tira siempre", y ni una tirada.
 *
 * <p>Es un hueco del motor, no de su IA. La IA SABE cuando tirar
 * ({@code RollPlanarDiceAi}: la pista {@code AIRollPlanarDieParams} de cada
 * plano, solo en la segunda fase principal, sus dudas y el coste X), pero
 * nunca llega a preguntarselo: busca lo que puede jugar en
 * {@code ComputerUtilAbility.getAvailableCards}, que de la zona de mando solo
 * coge lo que <b>no</b> es inmutable ({@code !c.isImmutable() || c.isEmblem()}),
 * y el "Planar Dice" que crea {@code Player.createPlanechaseEffects} es un
 * EFECTO, o sea inmutable. Se queda fuera siempre.
 *
 * <p>No se toca {@code forge-ai} (la regla de oro): {@link SafeAi}, que
 * envuelve el controlador de cada IA, cuando la IA no encuentra nada que hacer
 * le pregunta aqui. Y aqui no se decide nada: se le pasa su dado a SU propia
 * logica ({@code AiController.canPlaySa}), que es la que habria usado si lo
 * hubiera visto. Si algun dia lo arreglan rio arriba, la IA lo elegira ella
 * antes y esto no llegara a preguntarse.
 */
final class PlanarDiceAi {

    private PlanarDiceAi() {
    }

    /** El dado planar de esta IA si SU logica quiere tirarlo ahora, o null. */
    static SpellAbility pick(final PlayerControllerAi ctrl) {
        final Player p = ctrl.getPlayer();
        final Game game = p == null ? null : p.getGame();
        if (game == null || game.getActivePlanes() == null || game.getActivePlanes().isEmpty()
                || !game.getPhaseHandler().isPlayerTurn(p) || !game.getStack().isEmpty()) {
            return null;
        }
        for (final Card c : p.getCardsIn(ZoneType.Command)) {
            for (final SpellAbility sa : c.getSpellAbilities()) {
                if (sa.getApi() != ApiType.RollPlanarDice) {
                    continue;
                }
                sa.setActivatingPlayer(p);
                if (sa.canPlay() && ctrl.getAi().canPlaySa(sa) == AiPlayDecision.WillPlay) {
                    return sa;
                }
            }
        }
        return null;
    }
}
