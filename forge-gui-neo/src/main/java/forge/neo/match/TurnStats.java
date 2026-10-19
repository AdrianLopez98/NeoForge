package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.AbstractGuiGame;

/**
 * Lo que ha pasado ESTE turno, jugador por jugador: el panel «Turno» de la mesa.
 *
 * <p>Discord, 18-10-2026, despues de las pastillas de robadas y de hechizos:
 * <i>"Life gained/lost and cards put in the graveyard would be handy too. Though
 * with all of these perhaps it might be better to have a button to open up a
 * turn stats window? Rather than clutter the field"</i>. Las dos pastillas se
 * quedan (salen solo cuando dicen algo); el resto vive aqui, a un clic, y de
 * todos los jugadores: es lo que se mira para contar los disparos de un Orcish
 * Bowmasters o si ya se ha ganado vida este turno.
 *
 * <p>Todo son contadores del propio motor, los mismos que leen las cartas que
 * preguntan por "este turno" — el motor no los publica en el {@code PlayerView},
 * asi que se leen del {@code Game} como en {@link SpellsThisTurn} (y por lo
 * mismo, solo en una partida de este ordenador). Las listas las escribe el hilo
 * del motor: se copian de golpe y, si justo cambian, ese dato sale como
 * desconocido y la lectura siguiente lo arregla.
 */
public final class TurnStats {

    /** Un jugador. {@code -1} en un dato es "no se ha podido leer". */
    public record Row(PlayerView player, boolean active, int lifeGained, int lifeLost, int drawn,
                      int spells, int toGraveyard, List<String> graveyardNames, int discarded,
                      int sacrificed, int tokens, int lands) { }

    /** El turno entero: su numero, de quien es y los jugadores, tu el primero. */
    public record Snapshot(int turn, String activeName, int spellsAll, List<Row> rows) { }

    private TurnStats() {
    }

    /** {@code null} si no hay partida local que leer (un invitado en red). */
    public static Snapshot read(final AbstractGuiGame ui, final PlayerView me) {
        if (ui == null) {
            return null;
        }
        try {
            final Game game = SpellsThisTurn.gameOf(ui);
            if (game == null) {
                return null;
            }
            final Player active = game.getPhaseHandler().getPlayerTurn();
            final List<SpellAbility> cast = copy(game.getStack().getSpellsCastThisTurn());
            final List<Player> order = new ArrayList<>(game.getRegisteredPlayers());
            // Tu primero; los demas en el orden de la mesa.
            final Player mine = me == null ? null : game.getPlayer(me);
            if (mine != null && order.remove(mine)) {
                order.add(0, mine);
            }
            final List<Row> rows = new ArrayList<>();
            for (final Player p : order) {
                rows.add(row(p, p.equals(active), cast));
            }
            return new Snapshot(game.getPhaseHandler().getTurn(),
                    active == null ? "" : active.getName(), cast == null ? -1 : cast.size(), rows);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static Row row(final Player p, final boolean active, final List<SpellAbility> cast) {
        int spells = -1;
        if (cast != null) {
            spells = 0;
            for (final SpellAbility sa : cast) {
                if (sa != null && p.equals(sa.getActivatingPlayer())) {
                    spells++;
                }
            }
        }
        // Las cartas que han ido a SU cementerio, vengan de donde vengan. Las
        // fichas no: "cartas", y ademas una ficha deja de existir al llegar.
        int toGraveyard = -1;
        final List<String> names = new ArrayList<>();
        final List<Card> grave = copy(safeGraveyard(p));
        if (grave != null) {
            toGraveyard = 0;
            for (final Card c : grave) {
                if (c != null && !c.isToken()) {
                    toGraveyard++;
                    names.add(c.getName());
                }
            }
        }
        final List<Card> discarded = copy(p.getDiscardedThisTurn());
        final List<Card> sacrificed = copy(p.getSacrificedThisTurn());
        return new Row(p.getView(), active,
                p.getLifeGainedThisTurn(), p.getLifeLostThisTurn(), p.getNumDrawnThisTurn(),
                spells, toGraveyard, names,
                discarded == null ? -1 : discarded.size(),
                sacrificed == null ? -1 : sacrificed.size(),
                p.getNumTokenCreatedThisTurn(), p.getLandsPlayedThisTurn());
    }

    private static List<Card> safeGraveyard(final Player p) {
        try {
            return p.getZone(ZoneType.Graveyard).getCardsAddedThisTurn(null);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static <T> List<T> copy(final List<T> list) {
        if (list == null) {
            return null;
        }
        try {
            return new ArrayList<>(list);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
