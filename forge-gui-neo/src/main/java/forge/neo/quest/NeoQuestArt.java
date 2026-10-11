package forge.neo.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * <b>Cambiar el arte</b> de una carta de tu coleccion de la aventura, pagando.
 *
 * <p>Pedido en Discord (11-10-2026): <i>"a card art changer. Select the owned
 * card to view it's alt arts then select the art you want to change it to.
 * Change price could be based on it's rarity."</i>
 *
 * <p>En la aventura el arte <b>es contenido</b> ({@code DeckContext.printingsOf}):
 * el borderless se gana abriendo sobres de colector, y por eso el constructor
 * solo deja elegir artes que has abierto. Esto es la otra puerta, y por eso
 * <b>cuesta</b>: por rareza de la impresion que quieres, y el triple si es un
 * arte especial (borderless, showcase, arte extendido, Secret Lair, promo). Si
 * costara lo mismo que uno normal, el sobre de colector dejaria de tener gracia.
 *
 * <p>Lo que no puede pasar es <b>romper un mazo</b>. Se cambian N copias de una
 * impresion por otra; cada mazo que la usa se queda con tantas de la vieja como
 * te queden, y el resto pasan a la nueva — la misma cuenta que hace Forge al
 * vender ({@code QuestUtilCards.removeCard}), pero cambiando en vez de quitar.
 *
 * <p>Clase pura: la usan el escritorio y Android (nada de JavaFX ni de API de
 * Java que Android 26 no tenga).
 */
public final class NeoQuestArt {

    /** Lo que cuesta una copia, por rareza de la impresion nueva. */
    static final int COMMON = 20;
    static final int UNCOMMON = 40;
    static final int RARE = 80;
    static final int MYTHIC = 160;

    /** Cuanto mas cuesta un arte especial que uno normal de su rareza. */
    static final int SPECIAL_MULTIPLIER = 3;

    /** Las de Secret Lair, que son arte especial aunque esten en su seccion principal. */
    private static final Set<String> SECRET_LAIR = new HashSet<>(java.util.Arrays.asList("SLD", "SLC", "SLU", "SLX"));

    private NeoQuestArt() {
    }

    /**
     * Si a esta carta de tu coleccion se le puede cambiar el arte: la tienes, no
     * es una basica (esas son gratis en la aventura y sus mazos llevan mas de las
     * que hay en la coleccion) y hay alguna otra impresion que ofrecer.
     */
    public static boolean canChange(final PaperCard owned) {
        return owned != null && NeoQuest.isActive() && !owned.isVeryBasicLand()
                && NeoQuestSell.owned(owned) > 0 && !printingsOf(owned).isEmpty();
    }

    /**
     * Las OTRAS impresiones de esa carta, en las expansiones de esta Quest (si
     * tiene formato), y foil si la tuya lo es: cambiar el arte no es cambiar el
     * acabado.
     */
    public static List<PaperCard> printingsOf(final PaperCard owned) {
        final List<PaperCard> out = new ArrayList<>();
        if (owned == null || !NeoQuest.isActive()) {
            return out;
        }
        final java.util.function.Predicate<PaperCard> inQuest =
                NeoQuest.engine().getCards().applyFormatFilter(c -> true);
        final Set<String> seen = new HashSet<>();
        seen.add(key(owned));
        for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards(owned.getName())) {
            if (p == null || !inQuest.test(p)) {
                continue;
            }
            final PaperCard target = owned.isFoil() ? p.getFoiled() : p;
            if (seen.add(key(target))) {
                out.add(target);
            }
        }
        return out;
    }

    /** Lo que cuesta pasar UNA copia a esa impresion. */
    public static int priceOf(final PaperCard target) {
        if (target == null) {
            return 0;
        }
        final CardRarity r = target.getRarity();
        final int base = r == CardRarity.Common ? COMMON
                : r == CardRarity.Uncommon ? UNCOMMON
                : r == CardRarity.Rare || r == CardRarity.Special ? RARE
                : r == CardRarity.MythicRare ? MYTHIC
                : UNCOMMON;
        return isSpecial(target) ? base * SPECIAL_MULTIPLIER : base;
    }

    /**
     * Si es un arte especial: de Secret Lair, de una expansion de promos o de
     * coleccionista, o una impresion que su expansion no trae en la seccion
     * principal ({@code [cards]}) sino en {@code [borderless]}, {@code [showcase]},
     * {@code [extended art]}... que es justo lo que sale en el sobre de colector.
     */
    public static boolean isSpecial(final PaperCard card) {
        if (card == null) {
            return false;
        }
        final String code = card.getEdition();
        if (SECRET_LAIR.contains(code)) {
            return true;
        }
        final CardEdition edition = FModel.getMagicDb().getEditions().get(code);
        if (edition == null) {
            return false;
        }
        if (edition.getType() == CardEdition.Type.PROMO
                || edition.getType() == CardEdition.Type.COLLECTOR_EDITION) {
            return true;
        }
        final List<CardEdition.EditionEntry> main = edition.getCards();
        if (main == null || main.isEmpty()) {
            return false;
        }
        final String number = card.getCollectorNumber();
        for (final CardEdition.EditionEntry e : main) {
            if (card.getName().equals(e.name())
                    && (number == null || e.collectorNumber() == null
                        || number.equalsIgnoreCase(e.collectorNumber()))) {
                return false;
            }
        }
        // Esta en la expansion, pero no en su seccion principal.
        return !edition.getCardInSet(card.getName()).isEmpty();
    }

    /**
     * Cambia {@code copies} copias de {@code from} por {@code to}, cobrando.
     *
     * @return lo cobrado, o -1 si no se ha hecho (no te llega, no la tienes, no
     *         es la misma carta...)
     */
    public static int change(final PaperCard from, final PaperCard to, final int copies) {
        if (from == null || to == null || !NeoQuest.isActive() || from.isVeryBasicLand()
                || !from.getName().equals(to.getName()) || key(from).equals(key(to))) {
            return -1;
        }
        final int have = NeoQuestSell.owned(from);
        final int n = Math.min(copies, have);
        if (n <= 0) {
            return -1;
        }
        final int price = priceOf(to) * n;
        if (NeoQuest.credits() < price) {
            return -1;
        }
        final forge.gamemodes.quest.data.QuestAssets assets = NeoQuest.engine().getAssets();
        assets.subtractCredits(price);
        assets.getCardPool().remove(from, n);
        assets.getCardPool().add(to, n);

        // Los mazos: cada uno se queda con tantas de la vieja como te quedan, y
        // las demas pasan a la nueva. Con todas cambiadas, todas.
        final int left = have - n;
        int decks = 0;
        for (final Deck deck : NeoQuest.decks()) {
            boolean touched = false;
            for (final Map.Entry<DeckSection, CardPool> section : deck) {
                final CardPool pool = section.getValue();
                final int inSection = pool.count(from);
                if (inSection <= 0) {
                    continue;
                }
                // Lo que la vieja puede seguir cubriendo se reparte por secciones
                // en el orden en que salen; lo que no, se cambia.
                final int keep = Math.min(inSection, Math.max(0, left - countElsewhere(deck, from, section.getKey())));
                final int swap = inSection - keep;
                if (swap > 0) {
                    pool.remove(from, swap);
                    pool.add(to, swap);
                    touched = true;
                }
            }
            if (touched) {
                decks++;
            }
        }
        NeoQuest.save();
        System.out.printf(java.util.Locale.ROOT,
                "[arte] %s (%s %s) -> (%s %s) x%d por %d cr., %d mazos cambiados, quedan %d cr.%n",
                from.getName(), from.getEdition(), from.getCollectorNumber(),
                to.getEdition(), to.getCollectorNumber(), n, price, decks, NeoQuest.credits());
        return price;
    }

    /**
     * Cuantas de la vieja usa ese mazo en las secciones de ANTES de esta (las
     * que ya se han mirado), para no contar dos veces lo que la vieja cubre.
     */
    private static int countElsewhere(final Deck deck, final PaperCard card, final DeckSection upTo) {
        int n = 0;
        for (final Map.Entry<DeckSection, CardPool> section : deck) {
            if (section.getKey() == upTo) {
                break;
            }
            n += section.getValue().count(card);
        }
        return n;
    }

    /** Una impresion: expansion, numero, arte y acabado. */
    private static String key(final PaperCard c) {
        return c.getName() + "|" + c.getEdition() + "|" + c.getCollectorNumber() + "|" + c.getArtIndex()
                + (c.isFoil() ? "|foil" : "");
    }
}
