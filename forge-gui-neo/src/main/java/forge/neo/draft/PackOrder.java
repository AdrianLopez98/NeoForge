package forge.neo.draft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import forge.card.CardRarity;
import forge.item.PaperCard;

/**
 * El orden de un sobre en la pantalla de picks, como en Arena (Discord,
 * 15-10-2026: <i>"like in mtg arena when you are picking, all the cards are
 * sorted by rarity with the land at the end"</i>).
 *
 * <p>Primero la rareza, de mitica a comun: lo que mas pesa en un pick y lo que
 * se busca con la vista al abrir el sobre. Dentro de cada rareza, por color
 * (blanco, azul, negro, rojo, verde, oro, incoloro) y coste, que es como lo
 * ordenaba ya Android: lo siguiente que decide un pick es de que color va a
 * ir el mazo. Las tierras que no son basicas, detras de los hechizos de su
 * rareza; y la tierra basica del hueco de la tierra, al final del todo.
 *
 * <p>Solo cambia como se ENSENYA el sobre: el pick va por carta, no por
 * posicion. Lo comparten el escritorio ({@code DraftScreen}) y Android
 * ({@code LimitedBridge}, por {@link #order}).
 */
public final class PackOrder {

    private PackOrder() {
    }

    /** El sobre ordenado, en una lista nueva: la de entrada no se toca. */
    public static List<PaperCard> sort(final List<PaperCard> pack) {
        final List<PaperCard> out = new ArrayList<>(pack);
        out.sort(COMPARATOR);
        return out;
    }

    /**
     * Los indices del sobre en el orden en que se ensenya: el de pantalla i es
     * {@code pack.get(order.get(i))}. Para quien tiene que volver de la
     * posicion en pantalla a la carta del motor.
     */
    public static List<Integer> order(final List<PaperCard> pack) {
        final List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < pack.size(); i++) {
            idx.add(i);
        }
        idx.sort((a, b) -> COMPARATOR.compare(pack.get(a), pack.get(b)));
        return idx;
    }

    private static final Comparator<PaperCard> COMPARATOR = Comparator
            .comparingInt(PackOrder::basicLast)
            .thenComparingInt(PackOrder::rarityRank)
            .thenComparingInt(PackOrder::landLast)
            .thenComparingInt(PackOrder::colourRank)
            .thenComparingInt(PackOrder::cmc)
            .thenComparing(c -> c.getName() == null ? "" : c.getName());

    /** 1 para la tierra basica: al final del todo. */
    static int basicLast(final PaperCard c) {
        return c.isVeryBasicLand() || (c.getRules() != null && c.getRules().getType().isBasicLand()) ? 1 : 0;
    }

    /** Mitica, rara, especial, infrecuente, comun y el resto. */
    static int rarityRank(final PaperCard c) {
        final CardRarity r = c.getRarity();
        if (r == null) {
            return 5;
        }
        switch (r) {
            case MythicRare:
                return 0;
            case Rare:
                return 1;
            case Special:
                return 2;
            case Uncommon:
                return 3;
            case Common:
                return 4;
            default:
                return 5;
        }
    }

    static int landLast(final PaperCard c) {
        return c.getRules() != null && c.getRules().getType().isLand() ? 1 : 0;
    }

    /**
     * Blanco, azul, negro, rojo, verde, oro, incoloro. Por el COLOR de la carta,
     * no por su identidad: en un sobre se mira de que color es, y con la
     * identidad una roja con una habilidad que cuesta {B} (Flamecache Gecko) se
     * iba con las doradas.
     */
    static int colourRank(final PaperCard c) {
        if (c.getRules() == null) {
            return 6;
        }
        final forge.card.ColorSet color = c.getRules().getColor();
        if (color.countColors() > 1) {
            return 5;
        }
        if (color.hasWhite()) {
            return 0;
        }
        if (color.hasBlue()) {
            return 1;
        }
        if (color.hasBlack()) {
            return 2;
        }
        if (color.hasRed()) {
            return 3;
        }
        if (color.hasGreen()) {
            return 4;
        }
        return 6;
    }

    private static int cmc(final PaperCard c) {
        return c.getRules() == null ? 0 : c.getRules().getManaCost().getCMC();
    }
}
