package forge.neo.match;

import java.util.function.Predicate;

import forge.game.card.CardView;
import forge.game.player.PlayerView;

/**
 * <b>La mano de un rival que puedes ver</b> (Discord, 07-10-2026, con Sen
 * Triplets: <i>"ver la mano del rival si la ensenya o si ha revelado algo como
 * se hace en magic arena"</i>).
 *
 * <p>El motor ya lo sabe todo: una carta de su mano que te deja mirar
 * ({@code MayLookAt}: Sen Triplets, Telepathy, Glasses of Urza...) pasa el
 * {@code mayView} del interfaz, y una que te deja LANZAR ({@code MayPlay}) esta
 * en tu {@code getFlashback()}, que recorre tambien las manos de los demas
 * ({@code Player.getCardsActivatableInExternalZones}). Lo que faltaba era
 * DECIRLO: el contador de su mano no cambiaba, asi que no habia forma de saber
 * que se podia abrir.
 *
 * <p>Pura y compartida con Android por el jar: aqui solo se cuenta; quien
 * decide que se ve y que se lanza es el motor.
 */
public final class RevealedHand {

    private RevealedHand() {
    }

    /** Cuantas cartas de su mano te deja ver el motor. */
    public static int visible(final PlayerView owner, final Predicate<CardView> mayView) {
        return count(owner, mayView);
    }

    /** Cuantas de su mano puedes lanzar o jugar tu ahora mismo. */
    public static int castable(final PlayerView owner, final Predicate<CardView> playable) {
        return count(owner, playable);
    }

    private static int count(final PlayerView owner, final Predicate<CardView> test) {
        if (owner == null || test == null) {
            return 0;
        }
        int n = 0;
        try {
            final Iterable<CardView> hand = owner.getHand();
            if (hand == null) {
                return 0;
            }
            for (final CardView c : hand) {
                if (c != null && test.test(c)) {
                    n++;
                }
            }
        } catch (final RuntimeException e) {
            return n;
        }
        return n;
    }
}
