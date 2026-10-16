package forge.neo.deck;

import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * LOS ARTES DESEADOS de un mazo de la Quest (Ana, 16-10-2026).
 *
 * <p>En la Quest lo que falta se cuenta por CARTA: con cualquier arte de los
 * Llanowar Elves vale el que pusiste. Pero si en "Todos los artes" eliges uno
 * que no tienes (el showcase de March from the Black Gate), ese arte es un
 * deseo mas: esas copias no se juegan hasta tener ESA impresion, abriendola o
 * pagando el cambio de arte de la tienda ({@code NeoQuestArt}).
 *
 * <p>Que una copia sea "por arte" no se puede deducir de la impresion: el arte
 * de siempre tambien es una impresion. Asi que se APUNTA en el mazo, como
 * etiqueta ({@code Deck.getTags}, que Forge guarda con el mazo y copia con
 * el): {@code neo-art:LTR#545}. Edicion y numero de coleccionista, sin el
 * nombre: las etiquetas se escriben separadas por comas, y hay nombres con
 * coma.
 *
 * <p>Clase pura: la usan el editor (escritorio y Android, por el jar) y
 * {@code NeoQuest}.
 */
public final class ArtWishes {

    private static final String TAG = "neo-art:";

    private ArtWishes() {
    }

    /** La impresion, sin el foil: el brillo va aparte ({@code DeckEditor.canFoil}). */
    public static String printKey(final PaperCard c) {
        return c == null ? "" : (c.getEdition() + "#" + c.getCollectorNumber()).toUpperCase(Locale.ROOT);
    }

    public static boolean isWished(final Deck deck, final PaperCard c) {
        return deck != null && c != null && deck.getTags().contains(TAG + printKey(c));
    }

    public static void setWished(final Deck deck, final PaperCard c, final boolean on) {
        if (deck == null || c == null) {
            return;
        }
        if (on) {
            deck.getTags().add(TAG + printKey(c));
        } else {
            deck.getTags().remove(TAG + printKey(c));
        }
    }

    /** Quita las marcas de impresiones que ya no estan en el mazo. */
    public static void prune(final Deck deck) {
        if (deck == null) {
            return;
        }
        final java.util.Set<String> present = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (final CardPool pool : pools(deck)) {
            for (final Map.Entry<PaperCard, Integer> e : pool) {
                present.add(TAG + printKey(e.getKey()));
            }
        }
        deck.getTags().removeIf(t -> t.regionMatches(true, 0, TAG, 0, TAG.length()) && !present.contains(t));
    }

    /**
     * Cuantas copias de esa carta (por nombre) faltan en el mazo: las de arte
     * deseado necesitan su impresion exacta, y las demas valen con cualquiera.
     *
     * @param ownedByName cuantas tienes de esa carta, con cualquier arte
     * @param ownedExact  cuantas tienes de esa impresion exacta
     * @return {general, porArte}: las que faltan de cada clase
     */
    public static int[] missing(final Deck deck, final String name, final int ownedByName,
                                final ToIntFunction<PaperCard> ownedExact) {
        final String key = name.toLowerCase(Locale.ROOT);
        final Map<PaperCard, Integer> copies = new java.util.LinkedHashMap<>();
        for (final CardPool pool : pools(deck)) {
            for (final Map.Entry<PaperCard, Integer> e : pool) {
                if (e.getKey().getName().toLowerCase(Locale.ROOT).equals(key)) {
                    copies.merge(e.getKey(), e.getValue(), Integer::sum);
                }
            }
        }
        int general = 0;
        int byArt = 0;
        int usedOwned = 0;
        for (final Map.Entry<PaperCard, Integer> e : copies.entrySet()) {
            if (isWished(deck, e.getKey())) {
                final int have = Math.max(0, ownedExact.applyAsInt(e.getKey()));
                byArt += Math.max(0, e.getValue() - have);
                usedOwned += Math.min(e.getValue(), have);
            } else {
                general += e.getValue();
            }
        }
        final int left = Math.max(0, ownedByName - usedOwned);
        return new int[] {Math.max(0, general - left), byArt};
    }

    private static java.util.List<CardPool> pools(final Deck deck) {
        final java.util.List<CardPool> out = new java.util.ArrayList<>();
        for (final DeckSection s : new DeckSection[] {DeckSection.Commander, DeckSection.Main, DeckSection.Sideboard}) {
            if (deck.get(s) != null) {
                out.add(deck.get(s));
            }
        }
        return out;
    }
}
