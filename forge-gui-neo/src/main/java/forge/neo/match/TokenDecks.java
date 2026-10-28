package forge.neo.match;

import java.util.Map;

import forge.card.CardRules;
import forge.card.ICardFace;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * <b>Los mazos de fichas, para no sentarlos en una mesa de cuatro.</b>
 *
 * <p>Discord, 28-10-2026: en el Coliseo un rival de fichas llenó la mesa y la
 * partida se hundió (la entrada, perdida). En una mesa de Commander a cuatro
 * hay tres IA, y cuando una genera fichas sin parar el motor tiene que
 * pensar cada prioridad con cientos de permanentes: la partida se arrastra o
 * se queda sin memoria. Y en Ascenso, si eso pasa en la mesa del final, es
 * la run entera; con un reto del día cuyo jefe sea ese mazo, siempre.
 *
 * <p>No se arregla el motor (regla de oro): se elige otro rival. Cuenta las
 * cartas que crean fichas <b>una y otra vez</b> — un disparo o una habilidad
 * con {@code DB$ Token} / {@code AB$ Token} en su script —, no las que hacen
 * una ficha al lanzarse. Medido sobre los 180 preconstruidos de Commander: la
 * mitad lleva 8 o menos; 24 llevan 15 o más (Squirreled Away, Mardu Surge,
 * Token Triumph...), y esos son los que quedan fuera de las mesas.
 *
 * <p><b>Pura</b>: la usan el escritorio y Android por el jar.
 */
public final class TokenDecks {

    private TokenDecks() {
    }

    /** Cuantas cartas que crean fichas una y otra vez hacen que un mazo sea "de fichas". */
    public static final int HEAVY = 15;

    /** Si la carta crea fichas de forma repetida (disparo o habilidad, no el hechizo de una vez). */
    public static boolean makesTokens(final PaperCard card) {
        final CardRules rules = card == null ? null : card.getRules();
        if (rules == null) {
            return false;
        }
        for (final ICardFace face : rules.getAllFaces()) {
            if (face == null) {
                continue;
            }
            for (final String a : face.getAbilities()) {
                if (a.contains("AB$ Token") || a.contains("DB$ Token")) {
                    return true;
                }
            }
            for (final Map.Entry<String, String> v : face.getVariables()) {
                if (v.getValue().contains("DB$ Token") || v.getValue().contains("AB$ Token")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Cuantas cartas de fichas lleva el mazo (mazo y zona de mando, con sus copias). */
    public static int tokenMakers(final Deck deck) {
        if (deck == null) {
            return 0;
        }
        int n = count(deck.getMain());
        if (deck.has(DeckSection.Commander)) {
            n += count(deck.get(DeckSection.Commander));
        }
        return n;
    }

    /** Si es un mazo de fichas, de los que no se sientan en una mesa de cuatro. */
    public static boolean isHeavy(final Deck deck) {
        return tokenMakers(deck) >= HEAVY;
    }

    private static int count(final CardPool pool) {
        int n = 0;
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            if (makesTokens(e.getKey())) {
                n += e.getValue();
            }
        }
        return n;
    }
}
