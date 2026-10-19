package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.gamemodes.match.AbstractGuiGame;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * Cuantos hechizos se han lanzado este turno: los de un jugador y los de todos.
 *
 * <p>Pedido en Discord el 18-10-2026: <i>"Spells played this turn. Helpful for
 * Storm cards in hand"</i>. La tormenta copia el hechizo una vez por cada
 * hechizo lanzado ANTES este turno, de cualquier jugador, y llevar la cuenta de
 * cabeza con la IA lanzando cosas en tu turno no hay quien lo haga.
 *
 * <p><b>Por que se lee del motor y no de los eventos.</b> El motor no publica
 * este numero en el {@code PlayerView} (de lo que pasa en el turno solo publica
 * las robadas), y contar los {@code GameEventSpellAbilityCast} sale mal: el
 * evento salta tambien con las COPIAS (las de la propia tormenta) y desde la
 * vista no se puede saber si un hechizo es copia. El motor si lo sabe: su lista
 * {@code MagicStack.getSpellsCastThisTurn()} es exactamente la que mira la
 * tormenta ({@code isSpell() && !isCopied()}), y se vacia sola al cambiar de turno.
 *
 * <p>Esa lista la escribe el hilo del motor y aqui se lee desde el de interfaz:
 * se copia de golpe y, si justo cambia mientras, se dice "no se sabe" y el
 * refresco siguiente la vuelve a leer. Solo se ve en una partida de este
 * ordenador; un invitado en red no tiene el {@code Game} y la pastilla no sale.
 */
public final class SpellsThisTurn {

    /** Lo que se ha lanzado este turno: {@link #UNKNOWN} si no se puede leer. */
    public record Count(int mine, int all) { }

    public static final Count UNKNOWN = new Count(-1, -1);

    private SpellsThisTurn() {
    }

    public static Count of(final AbstractGuiGame ui, final PlayerView p) {
        if (ui == null || p == null) {
            return UNKNOWN;
        }
        try {
            final Game game = gameOf(ui);
            if (game == null) {
                return UNKNOWN;
            }
            final Player who = game.getPlayer(p);
            final List<SpellAbility> cast = new ArrayList<>(game.getStack().getSpellsCastThisTurn());
            int mine = 0;
            for (final SpellAbility sa : cast) {
                if (sa != null && who != null && who.equals(sa.getActivatingPlayer())) {
                    mine++;
                }
            }
            return new Count(mine, cast.size());
        } catch (final RuntimeException e) {
            // ConcurrentModification si el motor la toca justo ahora, o una
            // partida a medio montar: el siguiente refresco lo arregla.
            return UNKNOWN;
        }
    }

    static Game gameOf(final AbstractGuiGame ui) {
        for (final IGameController c : ui.getOriginalGameControllers()) {
            if (c instanceof PlayerControllerHuman human && human.getPlayer() != null) {
                return human.getPlayer().getGame();
            }
        }
        return null;
    }
}
