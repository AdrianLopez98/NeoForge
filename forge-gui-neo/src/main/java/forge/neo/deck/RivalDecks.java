package forge.neo.deck;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.game.GameType;

/**
 * <b>Un rival al azar no puede salir con un mazo a medio hacer.</b>
 *
 * <p>Discord, 07-10-2026: <i>"when playing while having a deck in commander
 * still in the process of being built ... the ai rolled the unfinished deck, and
 * therefore couldn't/could barely play, since most of the time I hadn't added
 * lands yet"</i>. El sorteo de rivales cogia de TODOS los mazos del formato,
 * los tuyos a medias incluidos. Ahora solo de los que el motor da por buenos
 * ({@code getDeckConformanceProblem == null}: tamanyo, comandante, identidad,
 * copias), que es la misma regla con la que ya se sorteaba TU mazo al azar
 * ({@code HomeScreen.drawRandom}).
 *
 * <p>Si no queda ninguno valido se sortea como antes, de todos: mejor un rival
 * flojo que una partida que no empieza. Un rival elegido A MANO no se toca.
 *
 * <p>Pura y compartida con Android por el jar.
 */
public final class RivalDecks {

    private RivalDecks() {
    }

    /** Si ese mazo vale para que lo juegue la IA en este formato. */
    public static boolean ready(final Deck deck, final GameType type) {
        if (deck == null) {
            return false;
        }
        if (type == null) {
            return true;
        }
        try {
            return type.getDeckFormat().getDeckConformanceProblem(deck) == null;
        } catch (final RuntimeException e) {
            // Si el motor no sabe juzgarlo, no se le quita: es lo de antes.
            return true;
        }
    }

    /**
     * Los mismos mazos en el mismo orden, con los que no valen detras. Asi el
     * sorteo que ya habia (y su orden, que favorece los modernos) sigue igual,
     * y solo si no hay validos suficientes sale uno a medias.
     */
    /**
     * El primero de {@code decks} que no este en {@code used} y que valga; si
     * no vale ninguno de los libres, el primero libre. Lo apunta en
     * {@code used}. Da lo mismo que {@link #readyFirst} y luego el primero
     * libre, pero <b>mirando la legalidad solo de los que van saliendo</b>:
     * comprobarla de la bolsa entera (los ~180 de Commander, uno por uno con
     * el motor) para quedarse con uno es lo que en Android, que lo hace en el
     * hilo principal, dejaba la aplicacion "sin responder" (08-10-2026).
     *
     * @param cache lo ya comprobado en este mismo sorteo (por objeto)
     * @return null si estan todos usados
     */
    public static Deck firstFree(final List<Deck> decks, final GameType type,
                                 final java.util.Set<String> used, final java.util.Map<Deck, Boolean> cache) {
        if (decks == null) {
            return null;
        }
        Deck fallback = null;
        for (final Deck d : decks) {
            if (d == null || used.contains(d.getName())) {
                continue;
            }
            Boolean ok = cache.get(d);
            if (ok == null) {
                ok = ready(d, type);
                cache.put(d, ok);
            }
            if (ok) {
                used.add(d.getName());
                return d;
            }
            if (fallback == null) {
                fallback = d;
            }
        }
        if (fallback != null) {
            used.add(fallback.getName());
        }
        return fallback;
    }

    public static List<Deck> readyFirst(final List<Deck> decks, final GameType type) {
        final List<Deck> good = new ArrayList<>();
        final List<Deck> bad = new ArrayList<>();
        if (decks != null) {
            for (final Deck d : decks) {
                (ready(d, type) ? good : bad).add(d);
            }
        }
        good.addAll(bad);
        return good;
    }
}
