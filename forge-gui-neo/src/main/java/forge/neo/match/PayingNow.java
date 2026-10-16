package forge.neo.match;

import forge.game.card.CardView;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.InputPayMana;
import forge.gamemodes.match.input.NeoPaymentPeek;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * <b>Que estas pagando ahora mismo</b>: lo que falta del coste y de que carta
 * (Discord, 16-10-2026: <i>"When paying for a card cost, and the large land
 * screen is open, could we get a target mana value added somewhere?"</i>). Con
 * el panel de tierras abierto el aviso del motor queda tapado, y no se veia que
 * habia que pagar sin cerrarlo.
 *
 * <p>Lo lee de la entrada de pago del motor con {@link NeoPaymentPeek}, que es
 * el que sabe leer sus campos protegidos sin tocar Forge. Java puro: lo usa
 * Android por el jar.
 */
public final class PayingNow {

    private PayingNow() {
    }

    /**
     * @param left lo que falta, como lo escribe Forge ("{3}{G}{B}"), o vacio
     * @param card la carta cuyo coste se paga, o null
     */
    public record Info(String left, CardView card) {
    }

    /** Lo que se esta pagando, o null si no hay un pago de mana en curso aqui. */
    public static Info of(final IGameController controller) {
        try {
            if (!(controller instanceof PlayerControllerHuman human)) {
                return null;
            }
            final Input input = human.getInputQueue().getInput();
            if (!(input instanceof InputPayMana)) {
                return null;
            }
            final ManaCostBeingPaid cost = NeoPaymentPeek.costOf(input);
            final SpellAbility sa = NeoPaymentPeek.paidFor(input);
            final String left = cost == null ? "" : cost.toManaCost().toString();
            final CardView card = sa == null || sa.getHostCard() == null ? null : CardView.get(sa.getHostCard());
            return left.isBlank() && card == null ? null : new Info(left, card);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
