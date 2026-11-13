package forge.neo.draft;

import forge.gamemodes.limited.CardRanker;
import forge.item.PaperCard;

/**
 * <b>La nota de draft de una carta</b>, 0-99, de las valoraciones de Forge
 * ({@code CardRanker.getRawScore}, sacadas de {@code res/draft/rankings} por
 * expansion). La usan la pantalla de picks, los premios de Ascenso y Android.
 *
 * <p>{@code null} cuando Forge no tiene dato de esa carta: {@code getRawScore}
 * devuelve entonces {@code SCORE_UNPICKABLE} (-100), y recortado a 0 se leia
 * como "la peor carta del sobre" cuando lo que pasa es que no se sabe (las
 * tierras basicas y las expansiones sin valoracion). Mejor sin pastilla.
 *
 * <p>Java puro y sin API que Android no tenga en la 26.
 */
public final class DraftRank {

    private DraftRank() {
    }

    public static Integer of(final PaperCard card) {
        if (card == null) {
            return null;
        }
        try {
            final double raw = CardRanker.getRawScore(card);
            if (raw < 0) {
                return null;
            }
            return (int) Math.round(Math.min(99, raw));
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
