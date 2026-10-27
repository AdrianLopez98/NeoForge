package forge.neo.card;

import java.util.function.Predicate;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.spellability.SpellAbilityView;

/**
 * Que es de verdad una carta tuya boca abajo, dicho en el TEXTO.
 *
 * <p>Itch.io, 27-10-2026, con <i>Magar of the Magic Strings</i>: cada carta
 * que pone boca abajo es un 3/3 que, al danar, copia "la carta con el nombre
 * anotado". Con cuatro pegando a la vez salen cuatro disparos para ordenar
 * que dicen todos <i>"Face-down card (H4): Whenever this creature deals
 * combat damage..."</i>, y no hay forma de saber cual es Bonus Round y cual
 * Goblin Game — y ahi el orden es justo lo que importa.
 *
 * <p>El motor ya sabe que TU puedes mirarla (es tuya y esta en la mesa o en el
 * stack: {@code CardView.canFaceDownBeShownTo}) y la carta ampliada ya la
 * gira. Lo que faltaba es el texto: aqui se le pone el nombre real al lado,
 * <b>solo</b> si quien mira puede verla. Al rival no se le dice nada: para el
 * sigue siendo informacion oculta, como cualquier carta boca abajo.
 *
 * <p>Pura y sin JavaFX: la usa tambien Android, cada uno con su forma de
 * saber quien mira ({@code mayFlip}).
 */
public final class FaceDownName {

    private FaceDownName() {
    }

    /**
     * El nombre real (traducido) de una carta boca abajo que puedes mirar, o
     * null si no esta boca abajo, no puedes mirarla o no se sabe.
     */
    public static String of(final CardView card, final Predicate<CardView> mayFlip) {
        if (card == null || mayFlip == null || !card.isFaceDown()) {
            return null;
        }
        try {
            if (!mayFlip.test(card)) {
                return null;
            }
            final CardStateView real = card.getAlternateState();
            if (real == null || real == card.getCurrentState()) {
                return null;
            }
            final String translated = real.getTranslatedName();
            final String name = translated == null || translated.trim().isEmpty()
                    ? real.getName() : translated;
            return name == null || name.trim().isEmpty() ? null : name.trim();
        } catch (final RuntimeException e) {
            // Sin poder preguntarlo, nada: nunca ensenyar de mas.
            return null;
        }
    }

    /**
     * El texto de una habilidad con el nombre real de su carta, si es tuya y
     * esta boca abajo: <i>"Face-down card (H4 · Bonus Round): Whenever..."</i>.
     * Si no, el texto del motor tal cual.
     */
    public static String label(final SpellAbilityView sa, final Predicate<CardView> mayFlip) {
        if (sa == null) {
            return "";
        }
        final String text = sa.getDescription() == null ? "" : sa.getDescription();
        final CardView host = sa.getHostCard();
        final String real = of(host, mayFlip);
        if (real == null) {
            return text;
        }
        return withName(text, host.getCurrentState() == null ? null : host.getCurrentState().getName(), real);
    }

    /**
     * Mete el nombre real detras del de la carta boca abajo, la primera vez
     * que sale. El motor la escribe "Face-down card (H4)": el numero se queda,
     * que con dos iguales es lo que las distingue.
     */
    static String withName(final String text, final String shown, final String real) {
        if (shown != null && !shown.isEmpty()) {
            final int at = text.indexOf(shown);
            if (at >= 0) {
                int end = at + shown.length();
                // "(H4)" detras: el nombre va dentro del parentesis.
                if (text.startsWith(" (", end)) {
                    final int close = text.indexOf(')', end);
                    if (close > end) {
                        return text.substring(0, close) + " · " + real + text.substring(close);
                    }
                }
                return text.substring(0, end) + " (" + real + ")" + text.substring(end);
            }
        }
        return real + ": " + text;
    }
}
