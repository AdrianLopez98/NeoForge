package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * Lo que el motor sabe <b>lanzar</b> desde fuera de la mano pero se olvida de
 * <b>publicar</b>.
 *
 * <p>Las interfaces saben que se puede lanzar desde el cementerio o el exilio
 * por {@code PlayerView.getFlashback()}, que sale de
 * {@code PlayerZone.OwnCardsActivationFilter}. Ese filtro conoce flashback,
 * retrace, jump-start, escape, disturb, el presagio y la aventura... y se deja
 * dos mecanismos que {@code GameActionUtil} si sabe lanzar:
 *
 * <ul>
 *   <li><b>Lo planeado</b> (Plot, OTJ), en el exilio. Reportado en itch.io el
 *       27-09-2026 (Sunbird's Invocation con Make Your Own Luck).</li>
 *   <li><b>El caos</b> (Mayhem, SPM), en el cementerio. Reportado en itch.io el
 *       28-09-2026 (Spider-Islanders).</li>
 *   <li><b>Armonizar</b> (Harmonize, TDM: 12 cartas) y <b>"Beam me up"</b>
 *       (Open Communications), en el cementerio. Reportado el 30-09-2026
 *       (Nature's Rhythm): el visor decia "25 cartas se pueden lanzar desde
 *       aqui" y justo esa no.</li>
 * </ul>
 *
 * <p>En los dos casos la carta se veia y no habia forma de jugarla, porque el
 * visor solo deja tocar lo de esa lista. Regla de oro: no se toca el filtro;
 * se pregunta a la carta del motor con <b>las mismas condiciones</b> que
 * {@code GameActionUtil}, copiadas aqui y en ningun otro sitio. El dia que
 * Forge los meta en su lista, esto sobra y no estorba.
 *
 * <p>La usan las dos interfaces: {@code NeoMatchUI} en el escritorio y
 * {@code AndroidMatchUI} en Android, que la recibe por el jar (su regla 2.2:
 * el codigo compartido no se copia). Por eso nada de API de Java que Android
 * no tenga en la 26. Y la vigilan {@code PlotCheck} y {@code MayhemCheck}.
 *
 * <p>Lee el {@code Card} vivo del motor: solo en partida local (el invitado en
 * red no tiene {@code Game}), y mejor desde el hilo del motor.
 */
public final class OutsideCasts {

    private OutsideCasts() {
    }

    /**
     * {@code GameActionUtil}, rama "Mayhem": en el cementerio, con Mayhem,
     * descartada y llegada este turno. El momento ("timing rules still
     * apply") no entra: eso lo decide el motor al lanzarla.
     */
    public static boolean mayhem(final Card c) {
        return c != null && c.isInZone(ZoneType.Graveyard)
                && c.hasKeyword(Keyword.MAYHEM)
                && c.wasDiscarded() && c.enteredThisTurn();
    }

    /**
     * {@code GameActionUtil}, ramas "Harmonize" y "Beam me up": en el
     * cementerio y con la palabra clave. Nada mas: ni que haya llegado este
     * turno ni que se descartara. Lo que cueste (girar una criatura para
     * rebajar, devolver una a la mano) y el momento lo decide el motor al
     * lanzarla. La unica rama que el motor descarta ("Harmonize" sin coste
     * propio en una carta sin coste de mana) no la tiene ninguna carta.
     */
    public static boolean graveyardKeyword(final Card c) {
        return c != null && c.isInZone(ZoneType.Graveyard)
                && (c.hasKeyword(Keyword.HARMONIZE) || c.hasKeyword(Keyword.BEAM_ME_UP));
    }

    /** Lo que se puede lanzar desde el cementerio y el motor no publica. */
    public static boolean fromGraveyard(final Card c) {
        return mayhem(c) || graveyardKeyword(c);
    }

    /**
     * {@code GameActionUtil}, rama "Plotted": planeada, en el exilio, tuya,
     * no llegada este turno, y tu puedes lanzar a velocidad de conjuro.
     */
    public static boolean plotted(final Card c, final Player activator) {
        return c != null && activator != null && c.isPlotted()
                && c.isInZone(ZoneType.Exile)
                && activator.equals(c.getOwner())
                && !c.enteredThisTurn()
                && activator.canCastSorcery();
    }

    /**
     * Las cartas de {@code player} que se pueden lanzar ahora por uno de esos
     * caminos: lo que {@code getFlashback()} se deja.
     */
    public static List<Card> missedBy(final Player player) {
        final List<Card> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        for (final Card c : player.getCardsIn(ZoneType.Graveyard)) {
            if (fromGraveyard(c)) {
                out.add(c);
            }
        }
        for (final Card c : player.getCardsIn(ZoneType.Exile)) {
            if (plotted(c, player)) {
                out.add(c);
            }
        }
        return out;
    }
}
