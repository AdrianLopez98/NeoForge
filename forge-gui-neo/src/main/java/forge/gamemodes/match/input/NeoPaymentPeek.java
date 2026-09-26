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
}
