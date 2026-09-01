package forge.neo.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.card.CardEdition;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.gamemodes.limited.SealedDeckBuilder;
import forge.item.BoosterPack;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * El sellado.
 *
 * <p>Es el mismo evento que el draft — tu pool, siete rivales con el suyo, dos
 * derrotas y se acaba — con una diferencia: <b>los sobres no se pasan</b>. Te
 * los dan, los abres todos de golpe y montas con lo que salga. No hay
 * decisiones de pick, hay una sola decision grande: que dos colores juegas.
 *
 * <p><b>Casi todo esto ya estaba, y en dos sitios distintos.</b> El motor tiene
 * {@code SealedCardPoolGenerator.generateSealedDeck}, que hace el pool entero
 * y los siete rivales... pero preguntando por {@code SGuiChoose} y
 * {@code SOptionPane}, o sea levantando cuatro dialogos del motor, incluido un
 * {@code getInteger} que <b>repite la pregunta en bucle</b> (ver la trampa de
 * {@code showInputDialog} en las notas de diseño). Y nosotros ya tenemos, del
 * draft, la parte que de verdad importa: montar un mazo jugable con un pool
 * ({@code SealedDeckBuilder}).
 *
 * <p>Asi que aqui no se pregunta nada: se recibe la expansion y cuantos sobres,
 * se abren, y se monta. La eleccion se hace en la pantalla, que es donde tiene
 * que hacerse.
 *
 * <p>El resultado es un {@link DeckGroup} en el almacen de sellados de Forge,
 * exactamente con la misma forma que uno de draft — por eso
 * {@link DraftRun.Kind} lo lleva sin cambiar nada mas.
 */
public final class NeoSealed {

    private NeoSealed() {
    }

    /** Cuantos sobres trae un sellado de verdad. */
    public static final int DEFAULT_BOOSTERS = 6;

    /** Cuantos rivales, como en el draft. */
    public static final int OPPONENTS = 7;

    /** Las expansiones de las que se puede montar un sellado. */
    public static List<CardEdition> editions() {
        final List<CardEdition> out = new ArrayList<>();
        for (final CardEdition e : FModel.getMagicDb().getEditions()) {
            if (e != null && e.hasBoosterTemplate()) {
                out.add(e);
            }
        }
        out.sort(java.util.Comparator.comparing(CardEdition::getDate).reversed());
        return out;
    }

    /**
     * Abre N sobres de esa expansion.
     *
     * <p>Uno nuevo cada vez, y no es un detalle: {@code SealedProduct.getCards()}
     * <b>memoriza</b> — genera el contenido la primera vez y luego devuelve
     * siempre el mismo. Reutilizar el objeto daria seis sobres identicos.
     */
    public static List<PaperCard> openBoosters(final CardEdition edition, final int howMany) {
        final List<PaperCard> out = new ArrayList<>();
        if (edition == null) {
            return out;
        }
        for (int i = 0; i < Math.max(1, howMany); i++) {
            try {
                final BoosterPack pack = BoosterPack.fromSet(edition);
                if (pack != null) {
                    out.addAll(pack.getCards());
                }
            } catch (final RuntimeException e) {
                System.err.println("[sellado] no se ha podido abrir un sobre de "
                        + edition.getCode() + ": " + e);
            }
        }
        return out;
    }

    /**
     * Monta el evento entero y lo guarda.
     *
     * <p>Tu pool va a la <b>banda</b> ({@code DeckSection.Sideboard}), que es
     * como Forge representa "las cartas de las que aun tienes que sacar el
     * mazo", y el mazo principal se monta solo con
     * {@code SealedDeckBuilder} — igual que en el draft. Un pool guardado con
     * el mazo vacio no se puede jugar, y mandar al jugador al constructor antes
     * de dejarle probar nada es el camino largo.
     *
     * <p>Los siete rivales abren <b>sus propios</b> sobres de la misma
     * expansion. Es lo que hace que un sellado tenga sentido: te enfrentas a
     * pools del mismo tamanyo y de las mismas cartas, no a preconstruidos.
     *
     * @return el evento guardado, o null si no se ha podido
     */
    public static DeckGroup create(final String name, final CardEdition edition,
                                   final int boosters) {
        if (name == null || name.isBlank() || edition == null) {
            return null;
        }
        final List<PaperCard> mine = openBoosters(edition, boosters);
        if (mine.isEmpty()) {
            return null;
        }

        final Deck deck = new Deck(name);
        final CardPool pool = deck.getOrCreate(DeckSection.Sideboard);
        for (final PaperCard c : mine) {
            pool.add(c);
        }
        buildMainDeck(deck, edition.getCode());

        final DeckGroup group = new DeckGroup(name);
        group.setHumanDeck(deck);
        for (int i = 0; i < OPPONENTS; i++) {
            final List<PaperCard> theirs = openBoosters(edition, boosters);
            if (theirs.isEmpty()) {
                break;
            }
            try {
                final Deck ai = new SealedDeckBuilder(theirs).buildDeck(edition.getCode());
                // SealedDeckBuilder bautiza el mazo con el toString() de su
                // objeto de colores, o sea "DeckColors@3c1f2651". Eso acaba en
                // la pantalla, delante del jugador, como nombre del rival.
                ai.setName("Rival " + (i + 1));
                group.addAiDeck(ai);
            } catch (final RuntimeException e) {
                System.err.println("[sellado] el rival " + (i + 1) + " no se ha podido montar: " + e);
            }
        }

        // Los rivales, del peor al mejor: la primera ronda tiene que ser la
        // mas facil o el evento se acaba en la primera partida. Es lo que hace
        // el motor con los suyos.
        try {
            group.rankAiDecks(new forge.gamemodes.limited.LimitedDeckEvaluator.LimitedDeckComparer());
        } catch (final RuntimeException e) {
            System.err.println("[sellado] no se han podido ordenar los rivales: " + e);
        }

        DraftRun.Kind.SEALED.storage().add(group);
        System.out.printf(Locale.ROOT,
                "[sellado] %s: %d cartas de %s en %d sobres, %d rivales%n",
                name, mine.size(), edition.getCode(), boosters, group.getAiDecks().size());
        return group;
    }

    /**
     * Monta un mazo jugable de 40 cartas con el pool.
     *
     * <p>Se usa {@code SealedDeckBuilder} y no {@code LimitedDeckBuilder} por
     * el mismo motivo que en el draft: el segundo necesita que le <b>digas</b>
     * los dos colores, y su clase de colores no es publica fuera de su paquete.
     * El de sellado los elige el solo a partir del mejor tercio del pool.
     */
    private static void buildMainDeck(final Deck deck, final String landSetCode) {
        try {
            final List<PaperCard> pool = deck.get(DeckSection.Sideboard).toFlatList();
            final Deck built = new SealedDeckBuilder(pool).buildDeck(landSetCode);
            if (built == null || built.getMain().isEmpty()) {
                return;
            }
            deck.getMain().clear();
            deck.getMain().addAll(built.getMain());
            // Lo que ha entrado en el mazo sale de la banda: si no, el pool
            // ensenyaria dos veces las mismas cartas y el constructor dejaria
            // meter copias que no tienes.
            for (final java.util.Map.Entry<PaperCard, Integer> e : built.getMain()) {
                deck.get(DeckSection.Sideboard).remove(e.getKey(), e.getValue());
            }
        } catch (final RuntimeException e) {
            System.err.println("[sellado] no se ha podido montar el mazo: " + e);
        }
    }

    /** Los sellados guardados. */
    public static Iterable<DeckGroup> saved() {
        return DraftRun.Kind.SEALED.storage();
    }

    /** Un nombre libre para el siguiente. */
    public static String nextName() {
        for (int i = 1; i < 500; i++) {
            final String candidate = "Sellado " + i;
            boolean taken = false;
            for (final DeckGroup g : saved()) {
                if (g.getName().equals(candidate)) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return candidate;
            }
        }
        return "Sellado";
    }
}
