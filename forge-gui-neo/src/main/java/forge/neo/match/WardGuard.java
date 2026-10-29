package forge.neo.match;

import forge.game.card.CardView;
import forge.game.keyword.KeywordView;
import forge.neo.card.CardKeywords;

/**
 * <b>Apuntar a algo con Ward se pregunta antes</b> (Discord, 29-10-2026: <i>"i
 * just lost the spell because i chose not to pay the ward cost (which i didnt
 * see in the first place)"</i>, con Cosmic Hunger sobre un Kappa Cannoneer).
 *
 * <p>Por las reglas no hay vuelta atras: el objetivo se elige al lanzar, el
 * ward se dispara al apuntar, y al resolverse o pagas o te contrarrestan. No se
 * puede ofrecer "elegir otro objetivo" despues sin inventarse una regla. Lo
 * que si se puede es lo del principio 6: lo que no se deshace, se evita antes.
 * Asi que el toque que fijaria de objetivo un permanente del rival con Ward
 * pregunta primero, y dice cuanto cuesta.
 *
 * <p>Solo decide si hay que preguntar; el dialogo lo pone
 * {@code NeoMatchUI.askBeforeWardTarget}.
 */
public final class WardGuard {

    private WardGuard() {
    }

    /**
     * El titulo de su Ward tal cual lo da el motor ("Ward {4}", "Ward—Pay 3
     * life"), o null si no tiene. Si tiene varios, el primero.
     */
    public static String wardOf(final CardView card) {
        if (card == null) {
            return null;
        }
        for (final KeywordView k : CardKeywords.of(card)) {
            final String t = k.title();
            if (t != null && t.regionMatches(true, 0, "Ward", 0, 4)) {
                return t;
            }
        }
        return null;
    }
}
