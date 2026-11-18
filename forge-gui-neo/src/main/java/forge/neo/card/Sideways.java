package forge.neo.card;

import forge.card.CardRules;
import forge.card.CardSplitType;
import forge.card.CardTypeView;
import forge.card.ICardFace;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;

/**
 * <b>Que cartas se leen de lado</b> al ampliarlas: planos, fenomenos, batallas,
 * habitaciones y partidas (Fire // Ice). Scryfall las sirve en vertical con el
 * dibujo girado, asi que la carta ampliada se gira 90 grados para leerla.
 *
 * <p>Las habitaciones y las partidas, desde el 30-10-2026 (Discord:
 * <i>"rotate room cards/battle cards/all horizontal cards when viewing
 * them"</i>). Las de Aftermath NO: la mitad de arriba se lee derecha.
 *
 * <p>Sin JavaFX: la usan las dos interfaces ({@code CardNode} y, en Android,
 * {@code CardBrief.apaisada}).
 */
public final class Sideways {

    private Sideways() {
    }

    /**
     * <b>Si esa cara se ensenya del reves</b>: la mitad girada de una carta flip
     * de Kamigawa (Discord, 17-11-2026: <i>"can you fix the og kamigawa flip
     * cards? so they turn 180 degrees?"</i>). Las dos mitades van en la MISMA
     * imagen, una del reves, y el motor no da otra: "the rotated image is
     * handled by graphics renderer" ({@code CardFactory}). Ensenyando la cara
     * girada ({@code CardStateName.Flipped}), el arte se gira 180 grados, como
     * en la GUI de Forge. La usan {@code CardNode} y, en Android, {@code
     * CardBrief.delReves}.
     */
    public static boolean upsideDown(final CardView card, final CardStateView shown) {
        return card != null && shown != null && card.isFlipCard()
                && shown.getState() == forge.card.CardStateName.Flipped;
    }

    /** Una carta de la partida (o de la interfaz, con su {@code CardView}). */
    public static boolean reads(final CardView c, final CardTypeView type) {
        if (type != null && (type.isPlane() || type.isPhenomenon() || type.isBattle())) {
            return true;
        }
        if (c == null) {
            return false;
        }
        if (c.isRoom()) {
            return true;
        }
        if (!c.isSplitCard()) {
            return false;
        }
        final CardStateView right = c.getRightSplitState();
        return right == null || !aftermath(right);
    }

    /**
     * Fuera de una partida (enciclopedia, mazos) las palabras clave del
     * {@code CardStateView} vienen vacias: se mira tambien su texto Oracle.
     */
    private static boolean aftermath(final CardStateView half) {
        if (half.hasAftermath()) {
            return true;
        }
        final String oracle = half.getOracleText();
        return oracle != null && oracle.startsWith("Aftermath");
    }

    /** Una carta en papel (catalogo, mazos, enciclopedia). */
    public static boolean reads(final CardRules rules) {
        if (rules == null) {
            return false;
        }
        final CardTypeView type = rules.getType();
        if (type != null && (type.isPlane() || type.isPhenomenon() || type.isBattle())) {
            return true;
        }
        if (rules.getSplitType() != CardSplitType.Split) {
            return false;
        }
        final ICardFace other = rules.getOtherPart();
        if (other != null && other.getKeywords() != null) {
            for (final String k : other.getKeywords()) {
                if ("Aftermath".equalsIgnoreCase(k)) {
                    return false;
                }
            }
        }
        return true;
    }
}
