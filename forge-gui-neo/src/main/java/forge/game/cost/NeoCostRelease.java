package forge.game.cost;

import java.util.function.Consumer;

import forge.game.card.CardCollectionView;
import forge.game.spellability.SpellAbility;

/**
 * Soltar la foto de la mesa que se queda en un coste ya pagado.
 *
 * <p>Al pagar un coste con lista (sacrificar, exiliar, descartar, girar
 * criaturas...) {@code CostPartWithList.executePayment} hace
 * {@code game.copyLastStateBattlefield()} y {@code copyLastStateGraveyard()}:
 * copias NUEVAS de todas las cartas de la mesa y de los cementerios, en su
 * {@code table}. Despues {@code CostPayment} llama a {@code resetLists()}, que
 * vacia las listas y la tabla... pero no esas dos fotos, que se quedan colgando
 * del coste hasta que se vuelva a pagar. Con un Tesoro es peor: el hechizo que
 * pago guarda el mana ({@code payingMana}), el mana guarda su carta, y la carta
 * su coste con la mesa entera dentro. Ver {@code forge.neo.match.LkiPruner}.
 *
 * <p><b>Por que vive en un paquete de Forge.</b> {@code table} es
 * {@code protected} y no tiene getter. Una clase NUESTRA en el mismo paquete la
 * alcanza sin tocar un fichero del motor (regla de oro) y sin reflexion, como
 * {@code forge.gamemodes.match.input.NeoPaymentPeek}. Si Card-Forge la renombra,
 * esto <b>no compila</b>, que es justo lo que se quiere.
 *
 * <p>Se deja la tabla como la de un coste recien creado (fotos vacias, que es lo
 * que pone su constructor): {@code executePayment} pone las suyas antes de cada
 * pago, y fuera del pago no la lee nadie.
 */
public final class NeoCostRelease {

    private NeoCostRelease() {
    }

    /**
     * @param before recibe cada foto antes de soltarla (el podador sigue por sus
     *               copias), o null
     * @return cuantos costes tenian foto y se han soltado
     */
    public static int release(final SpellAbility sa, final Consumer<CardCollectionView> before) {
        final Cost cost = sa == null ? null : sa.getPayCosts();
        if (cost == null) {
            return 0;
        }
        int n = 0;
        for (final CostPart part : cost.getCostParts()) {
            if (part instanceof CostPartWithList list && list.table != null
                    && (notEmpty(list.table.getLastStateBattlefield())
                        || notEmpty(list.table.getLastStateGraveyard()))) {
                if (before != null) {
                    before.accept(list.table.getLastStateBattlefield());
                    before.accept(list.table.getLastStateGraveyard());
                }
                list.table.setLastStateBattlefield(null);
                list.table.setLastStateGraveyard(null);
                n++;
            }
        }
        return n;
    }

    private static boolean notEmpty(final CardCollectionView cards) {
        return cards != null && !cards.isEmpty();
    }
}
