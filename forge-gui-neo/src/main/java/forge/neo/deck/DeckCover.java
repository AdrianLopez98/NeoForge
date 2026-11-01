package forge.neo.deck;

import java.util.List;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * <b>La carta que representa un mazo</b>: la que se ve al elegirlo.
 *
 * <p>Con comandante es el comandante y no se discute. Sin el (Estandar,
 * Moderno, la Quest...) era la PRIMERA carta de la lista, que es tanto como
 * decir una cualquiera — muchas veces una tierra basica (Discord, 23-10-2026:
 * <i>"in constructed it just seems to pick a random card to display"</i>).
 *
 * <p>Ahora se puede elegir, y se guarda en un campo que el {@code .dck} YA
 * tiene: las <b>cartas clave</b> de Forge ({@code KeyCards=}, que sus dos
 * editores marcan con una estrella). La portada es la primera carta clave que
 * siga en el mazo. Asi no hay dato nuevo nuestro, el mazo se lleva su portada
 * a donde vaya, y abierto en Forge la carta sale marcada como clave.
 *
 * <p>En el jar a proposito: la usan tambien NeoForge Android (DeckLoader).
 */
public final class DeckCover {

    private DeckCover() {
    }

    /**
     * La carta de la portada: el comandante, la elegida, o la de <b>mayor coste</b>
     * del mazo sin contar tierras (Discord, 01-11-2026: con los mazos traidos de
     * Forge sin carta clave, todas las portadas eran tierras basicas; <i>"at
     * least the biggest potential threat in the deck would be shown"</i>). A
     * igual coste, la primera de la lista; sin hechizos, la primera carta.
     */
    public static PaperCard of(final Deck deck) {
        if (deck == null) {
            return null;
        }
        if (deck.has(DeckSection.Commander)) {
            for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                return e.getKey();
            }
        }
        final PaperCard chosen = chosen(deck);
        if (chosen != null) {
            return chosen;
        }
        PaperCard best = null;
        PaperCard first = null;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final PaperCard c = e.getKey();
            if (first == null) {
                first = c;
            }
            if (c.getRules().getType().isLand()) {
                continue;
            }
            if (best == null || c.getRules().getManaCost().getCMC() > best.getRules().getManaCost().getCMC()) {
                best = c;
            }
        }
        return best != null ? best : first;
    }

    /** La portada elegida, si sigue en el mazo; si no, {@code null}. */
    public static PaperCard chosen(final Deck deck) {
        if (deck == null) {
            return null;
        }
        for (final String name : deck.getKeyCards()) {
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                if (e.getKey().getName().equalsIgnoreCase(name)) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** Si esta carta es la portada elegida. */
    public static boolean isCover(final Deck deck, final PaperCard card) {
        final PaperCard c = chosen(deck);
        return c != null && card != null && c.getName().equalsIgnoreCase(card.getName());
    }

    /**
     * La pone de portada: primera de las cartas clave, sin perder las demas
     * (las que alguien haya marcado en Forge siguen ahi, detras).
     */
    public static void set(final Deck deck, final PaperCard card) {
        if (deck == null || card == null) {
            return;
        }
        final List<String> rest = deck.getKeyCards();
        for (final String name : rest) {
            deck.removeKeyCard(name);
        }
        deck.addKeyCard(card.getName());
        for (final String name : rest) {
            if (!name.equalsIgnoreCase(card.getName())) {
                deck.addKeyCard(name);
            }
        }
    }

    /** Quita esa carta de portada: vuelve a salir la siguiente clave, o la primera del mazo. */
    public static void clear(final Deck deck, final PaperCard card) {
        if (deck != null && card != null) {
            for (final String name : deck.getKeyCards()) {
                if (name.equalsIgnoreCase(card.getName())) {
                    deck.removeKeyCard(name);
                }
            }
        }
    }
}
