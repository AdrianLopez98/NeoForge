package forge.neo.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.gamemodes.limited.SealedDeckBuilder;
import forge.item.PaperCard;

/**
 * Montar un mazo de 40 con un pool de limitado, <b>solo si el jugador lo pide</b>.
 *
 * <p>Hasta el 23-09-2026 el draft y el sellado montaban el mazo solos al
 * terminar, sin preguntar y sin forma de apagarlo. Reportado en itch.io por
 * alguien que juega limitado a diario: <i>"no one ever asked for this"</i> — y
 * como no habia boton de vaciar, antes de construir habia que sacar las 23
 * cartas una a una. Tenia razon: en limitado montar el mazo ES el juego. Ahora
 * el pool llega entero a la banda y esto solo corre desde el boton "Montar
 * solo" del editor.
 *
 * <p>Se usa {@code SealedDeckBuilder} y no {@code LimitedDeckBuilder}: el
 * segundo necesita que le digas los dos colores, y su clase de colores no es
 * publica fuera de su paquete. El de sellado los elige solo.
 */
public final class LimitedAutoBuild {

    private LimitedAutoBuild() {
    }

    /**
     * El mazo principal que montaria el motor con ese pool, o null si no puede.
     *
     * <p>Las cartas que no son basicas se devuelven <b>con la impresion que
     * hay en el pool</b>. El motor a veces contesta con otra impresion de la
     * misma carta, y entonces al sacarla de la banda no se encontraba: la
     * carta quedaba a la vez en el mazo y en el pool, y el pool crecia solo.
     */
    public static CardPool build(final List<PaperCard> pool, final String landSetCode) {
        if (pool == null || pool.isEmpty()) {
            return null;
        }
        final Deck built;
        try {
            built = landSetCode == null
                    ? new SealedDeckBuilder(new ArrayList<>(pool)).buildDeck()
                    : new SealedDeckBuilder(new ArrayList<>(pool)).buildDeck(landSetCode);
        } catch (final RuntimeException e) {
            System.err.println("[limitado] no se ha podido montar el mazo: " + e);
            return null;
        }
        if (built == null || built.getMain().isEmpty()) {
            return null;
        }
        final List<PaperCard> left = new ArrayList<>(pool);
        final CardPool out = new CardPool();
        for (final Map.Entry<PaperCard, Integer> e : built.getMain()) {
            final PaperCard card = e.getKey();
            for (int i = 0; i < e.getValue(); i++) {
                if (DraftDeckContext.isBasic(card)) {
                    out.add(card);
                    continue;
                }
                final PaperCard mine = takeSame(left, card);
                if (mine != null) {
                    out.add(mine);
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    /** Saca del pool esa impresion, o si no esta, otra de la misma carta. */
    private static PaperCard takeSame(final List<PaperCard> left, final PaperCard card) {
        final int exact = left.indexOf(card);
        if (exact >= 0) {
            return left.remove(exact);
        }
        final String name = DraftDeckContext.normalizedName(card.getName());
        for (int i = 0; i < left.size(); i++) {
            if (DraftDeckContext.normalizedName(left.get(i).getName()).equals(name)) {
                return left.remove(i);
            }
        }
        return null;
    }

    /** Las cartas del pool que no son basicas, mazo y banda juntos. */
    public static List<PaperCard> poolOf(final Deck deck) {
        final List<PaperCard> out = new ArrayList<>();
        if (deck == null) {
            return out;
        }
        for (final CardPool section : new CardPool[] {
                deck.get(forge.deck.DeckSection.Sideboard), deck.getMain()}) {
            if (section == null) {
                continue;
            }
            for (final Map.Entry<PaperCard, Integer> e : section) {
                if (DraftDeckContext.isBasic(e.getKey())) {
                    continue;
                }
                for (int i = 0; i < e.getValue(); i++) {
                    out.add(e.getKey());
                }
            }
        }
        return out;
    }
}
