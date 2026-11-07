package forge.neo.match;

import forge.game.card.Card;
import forge.game.mana.Mana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.mana.ManaPool;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.NeoPaymentPeek;

/**
 * <b>Con mana flotante, el pago automatico deja elegir</b> (Discord,
 * 25-10-2026: <i>"I had 2 colorless mana floating ... I wanted to use one
 * colorless and one blue and keep the other colorless floating"</i>).
 *
 * <p>El pago automatico pulsa el "Auto" de Forge, que paga con la IA sentada
 * en tu sitio, y esa IA gasta <b>primero lo flotante</b>. Elegir ya se podia
 * — durante un pago, los pips de la reserva de tu barra se clican
 * ({@code IGameController.useMana}) — pero el pago automatico no daba tiempo
 * a verlo.
 *
 * <p>Hay eleccion cuando las dos cosas son verdad: algo de lo flotante sirve
 * para este coste, y tienes ademas alguna fuente de mana sin girar en la mesa.
 * Entonces el pago automatico se para (como con Phyrexian Tower, ver
 * {@link AutoPaySacrifice}) y pagas tu: los pips, tus tierras, o "Auto". Sin
 * mana flotante, o sin otra fuente con la que pagar, no hay nada que decidir y
 * se paga solo como siempre. Y tampoco si no sobra nada: con lo justo
 * (flotante mas fuentes igual a lo que falta) se gasta todo igual. Es prudente a proposito: una fuente sin girar
 * que no sirva para este coste cuenta igual, y el precio de un aviso de mas es
 * un clic en "Auto".
 *
 * <p>Sin JavaFX: lo usa Android igual.
 */
public final class FloatingManaChoice {

    private FloatingManaChoice() {
    }

    /** Si en este pago hay que elegir entre lo flotante y lo demas. */
    public static boolean applies(final Object input) {
        try {
            if (!(input instanceof Input in)) {
                return false;
            }
            return applies(NeoPaymentPeek.costOf(in), NeoPaymentPeek.payer(in));
        } catch (final RuntimeException e) {
            // Ante la duda, como antes: se paga solo.
            return false;
        }
    }

    /** Lo mismo, con el coste que falta y quien paga (lo usa {@code FloatingManaCheck}). */
    static boolean applies(final ManaCostBeingPaid cost, final Player player) {
        try {
            if (cost == null || cost.isPaid() || player == null) {
                return false;
            }
            final ManaPool pool = player.getManaPool();
            if (pool.isEmpty() || !usable(pool, cost)) {
                return false;
            }
            // SOLO SI SOBRA (itch.io, 07-11-2026, con video: Hypnotic Specter
            // {1}{B}{B}, uno incoloro flotando y dos Pantanos sin girar; el pago
            // automatico se paraba y habia que girarlos a mano). Si lo
            // flotante y todo lo que pueden dar tus fuentes no pasa de lo que
            // falta, se va a gastar todo de todas formas: no hay nada que
            // elegir. Cada fuente cuenta lo que mas da (Sol Ring, dos): mejor
            // un aviso de mas que quedarse sin poder guardar lo flotante.
            int sources = 0;
            for (final Card c : player.getCardsIn(ZoneType.Battlefield)) {
                if (!c.isTapped() && !c.getManaAbilities().isEmpty()) {
                    int most = 1;
                    for (final forge.game.spellability.SpellAbility ma : c.getManaAbilities()) {
                        try {
                            most = Math.max(most, ma.amountOfManaGenerated(true));
                        } catch (final RuntimeException e) {
                            // una cantidad que no se calcula cuenta como una
                        }
                    }
                    sources += most;
                }
            }
            return sources > 0 && pool.totalMana() + sources > cost.getConvertedManaCost();
        } catch (final RuntimeException e) {
            // Ante la duda, como antes: se paga solo.
            return false;
        }
    }

    /** Si algo de lo que flota paga alguna parte de lo que falta. */
    private static boolean usable(final ManaPool pool, final ManaCostBeingPaid cost) {
        for (final Mana m : pool) {
            if (cost.isAnyPartPayableWith(m.getColor(), pool)) {
                return true;
            }
        }
        return false;
    }
}
