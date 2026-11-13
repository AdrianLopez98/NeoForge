package forge.neo.card;

import forge.game.card.CardView;
import forge.game.zone.ZoneType;

/**
 * <b>Que carta hay debajo de una copia</b> (Discord, 13-11-2026: «Sculpting
 * Steel and True Polymorph ... it doesn't show which one is the copy or the
 * real one»). Una copia (Clone, Sculpting Steel, Spark Double, True
 * Polymorph...) se ve EXACTAMENTE como la carta copiada; con dos iguales en la
 * mesa no habia forma de saber cual era la de verdad, y en un mazo de
 * recursion eso importa (lo que vuelve del cementerio es la de debajo).
 *
 * <p>El motor lo dice todo, sin tocar reglas: {@code CardView.isCloned()} y
 * {@code getBackup()}, la carta en papel que hay debajo (la que el propio
 * Forge usa para ensenyar el arte de verdad). Java puro: lo usan el PC
 * (marcador en la carta y bloque en la ampliacion) y Android.
 */
public final class CopyOf {

    private CopyOf() {
    }

    /** La carta de verdad de una copia en la mesa, o null si no es una copia. */
    public static CardView original(final CardView card) {
        if (card == null) {
            return null;
        }
        try {
            if (!card.isCloned() || card.getZone() != ZoneType.Battlefield) {
                return null;
            }
            final CardView backup = card.getBackup();
            if (backup == null || backup.getCurrentState() == null) {
                return null;
            }
            // Si la de debajo se llama igual (una copia de si misma), no hay
            // nada que distinguir.
            if (card.getCurrentState() != null
                    && backup.getCurrentState().getName().equals(card.getCurrentState().getName())) {
                return null;
            }
            return backup;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** El nombre de la carta de verdad, traducido; null si no es una copia. */
    public static String originalName(final CardView card) {
        final CardView o = original(card);
        return o == null ? null : CardText.nameOf(o.getCurrentState());
    }
}
