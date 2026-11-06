package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * <b>Este jugador esta protegido</b>: atacarle o apuntarle no sirve.
 *
 * <p>Discord, 06-11-2026: una mesa entera en fase (Teferi's Protection) y el
 * jugador sin ninguna marca, asi que parecia que solo se habian ido sus
 * permanentes; le ataco y no le hizo nada. El jugador no estaba en fase (en
 * Magic solo los permanentes entran en fase): tenia <i>protection from
 * everything</i>, una palabra clave del JUGADOR que el motor publica
 * ({@code PlayerView.getKeywords}) y que en el escritorio solo salia en el
 * detalle, al pasar el raton.
 *
 * <p>Cuenta las que cambian si sirve atacarle o apuntarle: protecciones,
 * antimaleficio y velo. Lo demas (no robar, mano al descubierto...) sigue en
 * el detalle. Java puro, sobre los textos de las palabras clave: lo usa
 * Android por el jar con las suyas ({@code PlayerSnapshot.palabras}).
 */
public final class PlayerShield {

    private PlayerShield() {
    }

    /** Las palabras clave del jugador que le protegen, limpias ("Protection from everything"); vacia si ninguna. */
    public static List<String> of(final Iterable<String> keywords) {
        final List<String> out = new ArrayList<>();
        if (keywords == null) {
            return out;
        }
        for (final String k : keywords) {
            if (k == null) {
                continue;
            }
            // Sin el recordatorio entre parentesis (habla de "this creature",
            // escrito para cartas) y sin el "from from" que suelta Forge al
            // ponersela a un jugador ("Protection from from everything").
            String t = k.trim();
            final int paren = t.indexOf(" (");
            if (paren > 0) {
                t = t.substring(0, paren).trim();
            }
            t = t.replace("from from", "from");
            final String low = t.toLowerCase(Locale.ROOT);
            if ((low.startsWith("protection") || low.startsWith("hexproof") || low.startsWith("shroud"))
                    && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** Las de un jugador de la partida. */
    public static List<String> of(final forge.game.player.PlayerView p) {
        final List<String> words = new ArrayList<>();
        try {
            if (p != null && p.getKeywords() != null) {
                for (final Object k : p.getKeywords()) {
                    if (k != null) {
                        words.add(k.toString());
                    }
                }
            }
        } catch (final RuntimeException e) {
            // sin palabras clave, sin escudo
        }
        return of(words);
    }

    /** El rotulo de la marca: "Protegido", en el idioma del juego. */
    public static String label() {
        return forge.neo.NeoText.get("bar.protected");
    }

    /** Lo que se lee al pasar el raton o tocarla: que le protege y que significa. */
    public static String tooltip(final List<String> shields) {
        return forge.neo.NeoText.get("bar.protected.tip") + "\n" + String.join("\n", shields);
    }
}
