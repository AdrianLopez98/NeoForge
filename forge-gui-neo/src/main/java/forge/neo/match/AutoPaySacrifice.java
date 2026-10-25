package forge.neo.match;

import forge.game.card.Card;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostSacrifice;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.NeoPaymentPeek;

/**
 * <b>"Auto" no sacrifica tus criaturas sin preguntar</b> (Discord, 25-10-2026:
 * Kokusho y Enduring Tenacity al cementerio sin una linea en el registro).
 *
 * <p>El "Auto" de Forge paga con la IA sentada en tu sitio
 * ({@code InputPayMana.runAsAi}), y esa IA usa sin problema habilidades de mana
 * que piden sacrificar OTRA cosa — Phyrexian Tower ({T}, sacrifica una
 * criatura: {B}{B}), Ashnod's Altar... — eligiendo ella que sacrificar. Las
 * habilidades de mana no pasan por la pila, asi que el registro solo dice que
 * la carta fue al cementerio. El propio Forge lo tiene apuntado ("TODO cheating
 * via autopay can still happen", {@code ComputerUtilCost}).
 *
 * <p>Esto mira el plan de "Auto" ({@link NeoPaymentPeek#autoPlan}) y dice si
 * alguna de esas fuentes tiene una habilidad de mana que sacrifica otra carta.
 * Lo que se sacrifica a SI MISMO (un Tesoro, Lotus Petal) no cuenta: eso es lo
 * que se espera de el. Es prudente a proposito: una fuente con dos habilidades
 * (la torre tambien da {C} sin sacrificar) cuenta igual, porque el plan dice
 * QUE fuentes, no con que habilidad — y un aviso de mas se aparta con un clic,
 * una criatura sacrificada no vuelve (principio 6).
 *
 * <p>Sin JavaFX: lo usa Android igual.
 */
public final class AutoPaySacrifice {

    private AutoPaySacrifice() {
    }

    /**
     * La fuente del plan de "Auto" que podria sacrificar otra carta, o
     * {@code null} si no hay ninguna (o el input no es un pago).
     */
    public static Card culprit(final Object input) {
        final forge.game.card.CardCollection plan;
        try {
            plan = input instanceof Input in ? NeoPaymentPeek.autoPlan(in) : null;
        } catch (final RuntimeException e) {
            return null;
        }
        if (plan == null) {
            return null;
        }
        for (final Card c : plan) {
            if (sacrificesOther(c)) {
                return c;
            }
        }
        return null;
    }

    /** Si alguna habilidad de mana de esta carta pide sacrificar otra cosa. */
    public static boolean sacrificesOther(final Card card) {
        if (card == null) {
            return false;
        }
        for (final SpellAbility sa : card.getManaAbilities()) {
            final Cost cost = sa.getPayCosts();
            if (cost == null) {
                continue;
            }
            for (final CostPart part : cost.getCostParts()) {
                if (part instanceof CostSacrifice && !part.payCostFromSource()) {
                    return true;
                }
            }
        }
        return false;
    }
}
