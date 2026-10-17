package forge.neo.deck;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.card.CardEdition;
import forge.deck.Deck;
import forge.game.GameFormat;
import forge.neo.match.NeoFormat;

/**
 * <b>De que epoca es un mazo, y en que formatos vale.</b> Para filtrar la
 * lista de mazos de construido.
 *
 * <p>Discord, 12-10-2026: <i>"more granular filtering for the deck selection
 * page. Like how Forge has: Formats, but also by set ... Helps selecting decks
 * from the same era"</i>. Las dos preguntas ya las contesta Forge, y aqui no se
 * inventa ninguna regla:
 *
 * <ul>
 *   <li><b>La epoca</b> es la de su lista de mazos ({@code DeckProxy.getEdition}):
 *       la expansion MAS NUEVA entre las <b>originales</b> de sus cartas
 *       ({@code getTheLatestOfAllTheOriginalEditionsOfCardsIn}). Una reimpresion
 *       no la adelanta: un mazo de M19 con un Mentor of the Meek sigue siendo
 *       de M19, no del set donde se reimprimio despues.</li>
 *   <li><b>El formato</b> es el pozo de cartas de {@link NeoFormat#poolFormat()}
 *       ({@code GameFormat.isDeckLegal}): solo cartas, no tamanyo.</li>
 * </ul>
 *
 * <p>Sin JavaFX: la usan las dos interfaces (Android por el jar). Las cuentas
 * se guardan por MAZO (identidad, no nombre: hay preconstruidos y mazos tuyos
 * que se llaman igual), porque la lista se vuelve a filtrar en cada tecla.
 */
public final class DeckEra {

    private DeckEra() {
    }

    /**
     * Los formatos por los que se puede filtrar una lista de construido: los
     * de 60 cartas de "Otros formatos", del mas nuevo al mas viejo.
     */
    public static final List<NeoFormat> FORMATS = Collections.unmodifiableList(Arrays.asList(
            NeoFormat.STANDARD, NeoFormat.PIONEER, NeoFormat.HISTORIC, NeoFormat.MODERN,
            NeoFormat.EXTENDED, NeoFormat.PREMODERN, NeoFormat.LEGACY, NeoFormat.VINTAGE,
            NeoFormat.PAUPER));

    /** Tope de la cache: unas cuantas listas enteras de mazos. */
    private static final int MAX_CACHED = 4000;

    private static final Map<Deck, CardEdition> ERAS = new IdentityHashMap<>();
    private static final Map<NeoFormat, Map<Deck, Boolean>> LEGAL = new LinkedHashMap<>();

    /**
     * Si el filtro de formato tiene sentido para la lista de ese formato: en
     * construido de 60 cartas (Estandar y sus "otros formatos") y, desde el
     * 17-10-2026, en los de comandante (Discord: <i>"Commander mode, could we
     * add a filter for Formats and sets please. Same as we have in
     * Standard"</i>) — los preconstruidos de Commander son de muchas epocas, y
     * "solo cartas de Premodern" o "de Pauper" tambien es una pregunta que se
     * le hace a un mazo de 100. Lo que no: draft, sellado y los demas
     * limitados, cuyas listas no son de construido.
     */
    public static boolean appliesTo(final NeoFormat listFormat) {
        return listFormat != null
                && (listFormat.getGameType() == forge.game.GameType.Constructed
                        || listFormat.isCommanderStyle());
    }

    /**
     * La epoca de un mazo, o null si no se sabe (vacio, o solo cartas de sets
     * especiales).
     */
    public static CardEdition eraOf(final Deck deck) {
        if (deck == null) {
            return null;
        }
        synchronized (ERAS) {
            if (ERAS.containsKey(deck)) {
                return ERAS.get(deck);
            }
        }
        CardEdition era = preconEdition(deck);
        if (era == null) {
            era = nameEdition(deck);
        }
        if (era == null) {
            try {
                final CardEdition found = StaticData.instance().getEditions()
                        .getTheLatestOfAllTheOriginalEditionsOfCardsIn(deck.getAllCardsInASinglePool());
                era = found == null || found == CardEdition.UNKNOWN ? null : found;
            } catch (final RuntimeException e) {
                era = null;
            }
        }
        synchronized (ERAS) {
            if (ERAS.size() > MAX_CACHED) {
                ERAS.clear();
            }
            ERAS.put(deck, era);
        }
        return era;
    }

    /** Los preconstruidos de Forge y la expansion que declaran. Se lee una vez. */
    private static Map<Deck, CardEdition> precons;

    /**
     * La expansion de un preconstruido, como hace Forge ({@code DeckProxy}:
     * {@code PreconDeck.getEdition}), o null si no lo es. Sin esto la regla de
     * las cartas se equivoca justo con ellos: el de Ajani de M19 salia de
     * Double Masters 2022, porque una de sus cartas tiene alli su primera
     * impresion "de expansion".
     */
    private static CardEdition preconEdition(final Deck deck) {
        Map<Deck, CardEdition> known;
        synchronized (DeckEra.class) {
            known = precons;
        }
        if (known == null) {
            known = new IdentityHashMap<>();
            try {
                for (final forge.item.PreconDeck p : forge.gamemodes.quest.QuestController.getPrecons()) {
                    final CardEdition ed = p.getEdition() == null ? null
                            : StaticData.instance().getEditions().get(p.getEdition());
                    if (p.getDeck() != null && ed != null) {
                        known.put(p.getDeck(), ed);
                    }
                }
            } catch (final RuntimeException e) {
                // Sin la lista de Forge, la regla de las cartas sigue valiendo.
            }
            synchronized (DeckEra.class) {
                precons = known;
            }
        }
        return known.get(deck);
    }

    /** "Adaptive Enchantment [C18] [2018]": la expansion y el anyo al final del nombre. */
    private static final java.util.regex.Pattern NAMED_SET =
            java.util.regex.Pattern.compile("\\[([A-Za-z0-9]{2,6})\\]\\s*\\[\\d{4}\\]\\s*$");

    /**
     * La expansion que dice el NOMBRE del mazo, como los preconstruidos de
     * Commander de Forge ({@code res/quest/commanderprecons}): "Adaptive
     * Enchantment [C18] [2018]". Su fichero declara {@code Set=C18}, pero el
     * {@code Deck} no lo guarda, y la regla de las cartas con ellos no vale:
     * 136 de los 180 salian de "TMC" (Discord, 17-10-2026, el filtro de
     * expansion en Commander). Solo si el codigo es una expansion que existe.
     */
    private static CardEdition nameEdition(final Deck deck) {
        final String name = deck.getName();
        if (name == null) {
            return null;
        }
        final java.util.regex.Matcher m = NAMED_SET.matcher(name);
        if (!m.find()) {
            return null;
        }
        try {
            final CardEdition ed = StaticData.instance().getEditions().get(m.group(1));
            return ed == null || ed == CardEdition.UNKNOWN ? null : ed;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Si todas las cartas del mazo son legales en ese formato. */
    public static boolean legalIn(final Deck deck, final NeoFormat format) {
        if (deck == null || format == null) {
            return false;
        }
        final Map<Deck, Boolean> cache;
        synchronized (LEGAL) {
            cache = LEGAL.computeIfAbsent(format, f -> new IdentityHashMap<>());
            final Boolean known = cache.get(deck);
            if (known != null) {
                return known;
            }
        }
        boolean legal;
        try {
            final GameFormat pool = format.poolFormat();
            legal = pool != null && pool.isDeckLegal(deck);
        } catch (final RuntimeException e) {
            legal = false;
        }
        synchronized (LEGAL) {
            if (cache.size() > MAX_CACHED) {
                cache.clear();
            }
            cache.put(deck, legal);
        }
        return legal;
    }

    /**
     * Las epocas que salen en una lista, de la mas nueva a la mas vieja, con
     * cuantos mazos hay de cada una. Para el selector: solo lo que hay.
     */
    public static List<Map.Entry<CardEdition, Integer>> erasIn(final Iterable<Deck> decks) {
        final Map<CardEdition, Integer> count = new IdentityHashMap<>();
        for (final Deck d : decks) {
            final CardEdition era = eraOf(d);
            if (era != null) {
                count.merge(era, 1, Integer::sum);
            }
        }
        final List<Map.Entry<CardEdition, Integer>> out = new ArrayList<>(count.entrySet());
        out.sort((a, b) -> {
            final int byDate = compareDates(b.getKey(), a.getKey());
            return byDate != 0 ? byDate : a.getKey().getName().compareTo(b.getKey().getName());
        });
        return out;
    }

    private static int compareDates(final CardEdition a, final CardEdition b) {
        final java.util.Date da = a.getDate();
        final java.util.Date db = b.getDate();
        if (da == null || db == null) {
            return da == null ? (db == null ? 0 : -1) : 1;
        }
        return da.compareTo(db);
    }

    /** "Core Set 2019 (M19)": como se ensenya una epoca. */
    public static String label(final CardEdition era) {
        return era == null ? "" : era.getName() + " (" + era.getCode() + ")";
    }
}
