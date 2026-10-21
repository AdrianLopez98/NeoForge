package forge.neo.match;

import forge.game.Game;
import forge.game.cost.Cost;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.game.spellability.StackItemView;

/**
 * <b>Cuanto vale la X</b> de un objeto de la pila (Discord, 21-10-2026:
 * <i>"display what X equals when an ai casts a potential board wipe"</i>). Un
 * Cataclysmic Prospecting en la pila decia "deals X damage to each creature" y
 * no habia forma de saber si iba a por tus 2/2 o por todo.
 *
 * <p>El motor no lo publica en la vista: {@code StackItemView} no lleva la X.
 * Esta en la habilidad ({@code SpellAbility.getXManaCostPaid}, lo mismo que lee
 * {@code Count$xPaid} al resolverse), asi que se busca su
 * {@code SpellAbilityStackInstance} por id (es el de la vista). Solo si el
 * coste de mana lleva X: las X que se definen de otra forma ("X es el numero
 * de...") no son una cantidad que se haya elegido.
 *
 * <p>Se lee desde fuera del hilo del motor, como el resto del retrato de la
 * mesa: si la pila cambia justo entonces, null y el siguiente refresco lo dice.
 * Pura y sin JavaFX: la usan las dos interfaces.
 */
public final class StackX {

    private StackX() {
    }

    /** La X pagada de ese objeto de la pila, o null si no lleva X o no se sabe. */
    public static Integer of(final Game game, final StackItemView item) {
        if (game == null || item == null) {
            return null;
        }
        try {
            for (final SpellAbilityStackInstance si : game.getStack()) {
                if (si.getId() != item.getId()) {
                    continue;
                }
                final SpellAbility sa = si.getSpellAbility();
                if (sa == null) {
                    return null;
                }
                final Cost cost = sa.getPayCosts();
                if (cost == null || cost.getCostMana() == null || cost.getCostMana().getAmountOfX() <= 0) {
                    return null;
                }
                return sa.getXManaCostPaid();
            }
        } catch (final RuntimeException e) {
            // La pila cambiando mientras se lee: lo dira el siguiente refresco.
        }
        return null;
    }
}
