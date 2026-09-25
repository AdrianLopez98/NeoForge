package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.util.Localizer;

/**
 * Lo que un jugador <b>tiene</b> de la mesa, ya traducido: ser el monarca, la
 * iniciativa.
 *
 * <p>El motor lo publica como una carta de efecto en la zona de mando DEL QUE
 * LO ES ({@code Player.createMonarchEffect}, que la llama "The Monarch" en
 * ingles siempre), y del rival la zona de mando es solo un contador: si el
 * monarca era el rival, no habia forma de saberlo. Reportado jugando el
 * 25-09-2026. El texto traducido lo trae Forge ({@code lblTheMonarch},
 * {@code lblTheInitiative}), asi que no hay nada que traducir aqui.
 *
 * <p>Es publica porque la usan las dos interfaces: la barra del escritorio
 * ({@code TableBinder} → {@code PlayerBar.setTitles}) y la chapa del jugador de
 * NeoForge Android, que lee este jar.
 */
public final class PlayerTitles {

    private PlayerTitles() {
    }

    /** Los titulos del jugador, en el orden en que estan en su zona de mando. Nunca null. */
    public static List<String> of(final PlayerView p) {
        final List<String> out = new ArrayList<>();
        if (p == null) {
            return out;
        }
        try {
            final var command = p.getCards(ZoneType.Command);
            if (command == null) {
                return out;
            }
            final Localizer loc = Localizer.getInstance();
            for (final CardView cv : command) {
                final String name = cv == null || cv.getCurrentState() == null
                        ? null : cv.getCurrentState().getName();
                if ("The Monarch".equals(name)) {
                    out.add(loc.getMessage("lblTheMonarch"));
                } else if ("The Initiative".equals(name)) {
                    out.add(loc.getMessage("lblTheInitiative"));
                }
            }
        } catch (final RuntimeException e) {
            return out;
        }
        return out;
    }
}
