package forge.gamemodes.match.input;

import forge.game.mana.ManaCostBeingPaid;
import forge.game.spellability.SpellAbility;

/**
 * Que coste se esta pagando ahora mismo, y de que hechizo.
 *
 * <p><b>Por que vive en un paquete de Forge.</b> {@code InputPayMana} lo sabe
 * —{@code manaCost} y {@code saPaidFor}— pero los tiene {@code protected} y no
 * los publica. Una clase NUESTRA en el mismo paquete los puede leer sin tocar
 * un solo fichero del motor (regla de oro) y sin reflexion: todo va en el
 * classpath, asi que es el mismo paquete en tiempo de ejecucion. Es el mismo
 * truco que las copias sombra de {@code forge.adventure.scene}.
 *
 * <p>Si algun dia Card-Forge renombra esos campos, esto <b>no compila</b>:
 * falla en voz alta en el primer {@code build.cmd} despues del rebase, que es
 * justo lo que se quiere.
 *
 * <p>Lo usa {@code forge.neo.match.ManaCombo} para prerrellenar el reparto de
 * mana de "X manas en cualquier combinacion de colores" con lo que pide el
 * coste.
 */
public final class NeoPaymentPeek {

    private NeoPaymentPeek() {
    }

    /** Lo que falta por pagar, o {@code null} si el input no es un pago de mana. */
    public static ManaCostBeingPaid costOf(final Input input) {
        return input instanceof InputPayMana pay ? pay.manaCost : null;
    }

    /** Lo que se esta pagando, o {@code null} si el input no es un pago de mana. */
    public static SpellAbility paidFor(final Input input) {
        return input instanceof InputPayMana pay ? pay.saPaidFor : null;
    }

    /**
     * Si es un pago que pide un EFECTO mientras se resuelve — "puede pagar {4};
     * si lo hace..." (Paralyze), un coste de "a menos que", eco, mantenimiento
     * acumulativo — y no el coste de algo que el jugador ha decidido lanzar o
     * activar. En esos pagos <b>no pagar es una decision</b>: Cancelar es "no
     * pago". Es el {@code effect} que {@code PlaySpellAbility} pasa al pedir el
     * pago ({@code hcd.isEffect()}), protegido y sin getter en Forge.
     */
    public static boolean isEffectPayment(final Input input) {
        return input instanceof InputPayMana pay && pay.effect;
    }

    /**
     * Las fuentes que usaria "Auto" ahora mismo, o {@code null} si no puede
     * pagar. Es EXACTAMENTE lo que calcula {@code InputPayMana} para su vista
     * previa ({@code ensureAutoPayManaSources}: la IA en el asiento del
     * jugador), sobre una copia del coste, asi que no paga nada. Lo usa
     * {@code forge.neo.match.AutoPaySacrifice}.
     */
    public static forge.game.card.CardCollection autoPlan(final Input input) {
        if (!(input instanceof InputPayMana pay) || pay.manaCost == null || pay.manaCost.isPaid()) {
            return null;
        }
        final ManaCostBeingPaid copy = new ManaCostBeingPaid(pay.manaCost);
        final forge.game.card.CardCollection[] out = {null};
        pay.runAsAi(() -> out[0] = forge.ai.ComputerUtilMana.getManaSourcesToPayCost(
                copy, pay.saPaidFor, pay.player, pay.effect));
        return out[0];
    }
}
