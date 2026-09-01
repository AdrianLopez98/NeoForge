package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Las fuentes de mana que el planificador del motor <b>no sabe ver</b>.
 *
 * <p><b>El fallo, medido.</b> Con un Bosque y una Pradera Hierbasol
 * ({@code {1}, {T}: Agrega {G}{W}}) en la mesa, las dos enderezadas, y un
 * hechizo de {@code {G}{W}} en la mano:
 *
 * <pre>
 *   getManaSourcesToPayCost = null      <- el boton "Auto" no se enciende
 *   canPayManaCost          = false     <- el motor cree que no se puede pagar
 * </pre>
 *
 * <p>Y con un Bosque y una Llanura, la misma mesa por lo demas, devuelve las
 * dos tierras y {@code true}. O sea que no es el coste ni la mesa: es que
 * {@code ComputerUtilMana} <b>no contempla una habilidad de mana que cuesta
 * mana</b>. Forge lo sabe: el script de la carta lleva {@code AI:RemoveDeck:All},
 * que es decirle a su propia IA "no metas esto en un mazo".
 *
 * <p><b>Por que esto no viola la regla de oro.</b> No reescribimos el
 * planificador ni tocamos {@code forge-ai}: solo <i>reconocemos</i> las fuentes
 * que caen en ese hueco, para poder nombrarlas en el aviso y — si el jugador lo
 * enciende — activarlas por el mismo camino que un click. El motor <b>si</b> las
 * acepta por ahi; lo unico ciego es su planificador.
 *
 * <p><b>El criterio es estrecho a proposito</b>: solo una habilidad de mana
 * cuyo COSTE incluya mana. Ese es exactamente el hueco medido, y no queremos
 * ponernos a adivinar donde el motor si sabe lo que hace.
 */
public final class FilterSources {

    private FilterSources() {
    }

    /**
     * Las cartas del jugador cuya habilidad de mana cuesta mana y se puede
     * activar ahora mismo. Lista vacia si no hay ninguna o si no hay partida
     * local — en una partida en red el {@code Game} vive en el anfitrion.
     */
    public static List<Card> of(final Game game, final Player p) {
        final List<Card> out = new ArrayList<>();
        if (game == null || p == null) {
            return out;
        }
        for (final Card c : p.getCardsIn(ZoneType.Battlefield).threadSafeIterable()) {
            if (costsManaToMakeMana(c, p)) {
                out.add(c);
            }
        }
        return out;
    }

    /** Si esta carta tiene una habilidad de mana que cuesta mana y es jugable. */
    private static boolean costsManaToMakeMana(final Card c, final Player p) {
        if (c == null || c.isTapped()) {
            return false;
        }
        for (final SpellAbility ma : c.getManaAbilities()) {
            if (ma.getPayCosts() == null || ma.getPayCosts().getCostMana() == null) {
                continue;
            }
            ma.setActivatingPlayer(p);
            if (ma.canPlay()) {
                return true;
            }
        }
        return false;
    }
}
