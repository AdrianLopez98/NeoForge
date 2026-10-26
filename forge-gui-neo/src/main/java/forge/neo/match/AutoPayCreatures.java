package forge.neo.match;

import forge.game.card.Card;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.NeoPaymentPeek;
import forge.neo.NeoSettings;

/**
 * <b>"El pago automatico no gira criaturas"</b>, un ajuste apagado de fabrica
 * (Discord, 26-10-2026: <i>"if you want to disable creature mana used through
 * auto"</i>).
 *
 * <p>Quien elige las fuentes es el planificador de la IA de Forge
 * ({@code ComputerUtilMana}), y no se le puede decir "estas no". Lo que si se
 * puede es mirar su plan ({@link NeoPaymentPeek#autoPlan}) y, si usa una
 * criatura (un Llanowar Elves, un Birds of Paradise), <b>no pagar solo</b>: el
 * pago se queda en tus manos, como con Phyrexian Tower
 * ({@link AutoPaySacrifice}) y el mana flotante ({@link FloatingManaChoice}),
 * y "Auto" sigue ahi por si quieres que la use.
 *
 * <p>Sin JavaFX: lo usa Android igual.
 */
public final class AutoPayCreatures {

    private AutoPayCreatures() {
    }

    /** La criatura que giraria el pago automatico, o {@code null} (o el ajuste apagado). */
    public static Card culprit(final Object input) {
        if (!NeoSettings.autoManaNoCreatures()) {
            return null;
        }
        try {
            final forge.game.card.CardCollection plan =
                    input instanceof Input in ? NeoPaymentPeek.autoPlan(in) : null;
            if (plan == null) {
                return null;
            }
            for (final Card c : plan) {
                if (c.isCreature()) {
                    return c;
                }
            }
        } catch (final RuntimeException e) {
            // Ante la duda, como antes: se paga solo.
        }
        return null;
    }
}
