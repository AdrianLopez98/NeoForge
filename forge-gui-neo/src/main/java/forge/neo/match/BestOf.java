package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.deck.CardPool;
import forge.game.GameType;
import forge.game.GameView;
import forge.item.PaperCard;
import forge.neo.NeoText;

/**
 * Lo que hace falta entre partida y partida de un partido al mejor de 3, en
 * Java puro: lo usan el escritorio ({@code NeoMatchUI}) y Android por el jar.
 *
 * <p>Nacio en el draft (la auditoría del motor, apartado C5) y salio aqui el 19-10-2026, cuando el
 * mejor de 3 llego a todos los formatos y a Android (Discord: <i>"there is no
 * bo3 option, correct?"</i>).
 */
public final class BestOf {

    private BestOf() {
    }

    /**
     * "Marcador: Ana 1 · IA-1 0" en un partido de varias partidas, o null en
     * una suelta. Cada jugador con las partidas que lleva ganadas, en el orden
     * del motor: como el "Won / Lost" de Forge.
     */
    public static String score(final GameView gv) {
        try {
            if (gv == null || gv.getMatch() == null || gv.getNumGamesInMatch() < 2) {
                return null;
            }
            final StringBuilder sb = new StringBuilder();
            for (final forge.game.player.RegisteredPlayer rp : gv.getMatch().getPlayers()) {
                final forge.LobbyPlayer lp = rp.getPlayer();
                if (lp == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append("  ·  ");
                }
                sb.append(lp.getName()).append(' ').append(gv.getGamesWonBy(lp));
            }
            return sb.length() == 0 ? null : NeoText.get("over.score", sb.toString());
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * El pool entero para rehacer el mazo: lo que estaba en el mazo mas lo que
     * se quedo fuera. En el draft la banda YA es el pool entero (el principal
     * es una copia de una parte) y sumar los dos duplicaria cada carta del
     * mazo; en el resto son montones aparte.
     */
    public static List<PaperCard> sideboardPool(final CardPool sideboard, final CardPool main) {
        final List<PaperCard> pool = new ArrayList<>();
        if (isSuperset(sideboard, main)) {
            pool.addAll(sideboard.toFlatList());
        } else {
            pool.addAll(main.toFlatList());
            pool.addAll(sideboard.toFlatList());
        }
        return pool;
    }

    /**
     * Cuantas cartas puede llevar el mazo al volver del banquillo: lo que el
     * formato da por legal ({@code DeckFormat.getMainRange}), sin pasar del
     * pool. Si el mazo de ahora ya estaba fuera de ese margen, se admite
     * tambien su tamanyo, para no dejar al jugador sin forma de seguir.
     *
     * @return {minimo, maximo}
     */
    public static int[] legalMainRange(final GameType type, final int current, final int pool) {
        int lo = current;
        int hi = current;
        try {
            final forge.deck.DeckFormat format = type == null ? null : type.getDeckFormat();
            final org.apache.commons.lang3.Range<Integer> r = format == null ? null : format.getMainRange();
            if (r != null) {
                lo = Math.min(current, r.getMinimum());
                hi = Math.max(current, Math.min(pool, r.getMaximum()));
            }
        } catch (final RuntimeException e) {
            // Sin formato que consultar: el tamanyo de antes, como siempre.
        }
        return new int[] {Math.max(0, lo), Math.max(lo, Math.min(pool, hi))};
    }

    /** Si la banda cubre, carta a carta, lo que hay en el mazo (draft). */
    private static boolean isSuperset(final CardPool sideboard, final CardPool main) {
        if (main.isEmpty()) {
            return false;
        }
        for (final Map.Entry<PaperCard, Integer> e : main) {
            if (sideboard.countAll(c -> c.equals(e.getKey())) < e.getValue()) {
                return false;
            }
        }
        return true;
    }
}
