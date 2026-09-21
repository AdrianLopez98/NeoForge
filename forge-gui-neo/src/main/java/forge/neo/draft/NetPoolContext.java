package forge.neo.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.deck.DeckContext;
import forge.util.storage.IStorage;

/**
 * Montar el mazo con el pool de un <b>evento en red</b> (draft o sellado).
 *
 * <p>Es el hermano de {@link DraftDeckContext} para el otro origen de pool. Se
 * parecen tanto que da pereza tenerlos separados, y aun asi lo estan por dos
 * diferencias que no son cosmeticas:
 *
 * <ul>
 *   <li><b>Donde se guarda.</b> Un evento nuestro es un {@code DeckGroup} con
 *       tu mazo y los siete rivales dentro, y {@code DraftDeckContext} tiene
 *       una fachada entera de {@code IStorage} para reescribirlo. Un pool de
 *       red es un <b>mazo suelto</b> en su propia carpeta
 *       ({@code getNetworkEventDecks}), porque los rivales son personas y cada
 *       una guarda el suyo en su ordenador.</li>
 *   <li><b>El nombre SI se toca.</b> Alli no se puede — es la clave del
 *       marcador del evento. Aqui el mazo no tiene marcador ninguno, y poder
 *       llamarlo "el rojo agresivo" importa: en la sala se elige por nombre y
 *       en un draft acabas con varios pools del mismo dia.</li>
 * </ul>
 *
 * <p><b>Lo que NO cambia es la forma del pool</b>, y eso es lo que permite
 * reutilizar la aritmetica de al lado: mazo y banda son <b>disjuntos</b> y el
 * pool es la suma de los dos. Lo deja asi el servidor — el draft manda
 * {@code new Deck(player.getDeck(), ...)} con los picks en
 * {@code DeckSection.Sideboard}, y el sellado abre los seis sobres ahi mismo —
 * y por eso {@link #poolInSideboard()} es cierto: sin eso, sacar una carta del
 * mazo la <b>destruye</b> en vez de devolverla al pool (el fallo del sellado en
 * Android, 12-09-2026).
 *
 * <p>Las <b>tierras basicas</b> no salen de ningun sobre: en limitado se ponen
 * las que hagan falta, sin techo.
 */
public final class NetPoolContext implements DeckContext {

    private final boolean sealed;
    private final IStorage<Deck> storage;

    /** Cuantas copias hay de cada impresion. Las basicas no estan aqui. */
    private final Map<PaperCard, Integer> owned = new HashMap<>();

    /** Lo mismo sumado por NOMBRE, que es el techo de verdad. Ver DraftDeckContext. */
    private final Map<String, Integer> ownedByName = new HashMap<>();

    /** El catalogo: una entrada por impresion del pool, mas las cinco basicas. */
    private final List<PaperCard> pool = new ArrayList<>();

    /** Las impresiones que tienes de cada carta, por nombre. */
    private final Map<String, List<PaperCard>> printings = new HashMap<>();

    /**
     * @param deck   el pool tal y como llego por el cable (o como se guardo)
     * @param sealed si el evento era de sellado; solo cambia el rotulo y el
     *               {@code DeckFormat}, que el motor usa para las estadisticas
     */
    public NetPoolContext(final Deck deck, final boolean sealed) {
        this.sealed = sealed;
        this.storage = FModel.getDecks().getNetworkEventDecks();
        if (deck == null) {
            return;
        }
        // Las DOS mitades: el pool es la suma. Al recibirlo esta entero en la
        // banda, pero en cuanto el jugador monta algo, parte se ha mudado al
        // mazo — y volver a abrirlo tiene que seguir viendo el pool completo.
        count(deck.get(DeckSection.Sideboard));
        count(deck.getMain());

        pool.addAll(owned.keySet());
        pool.sort(java.util.Comparator.comparing(PaperCard::getName)
                .thenComparing(PaperCard::getEdition));
        pool.addAll(DraftDeckContext.basics(deck));
    }

    private void count(final CardPool section) {
        if (section == null) {
            return;
        }
        for (final Map.Entry<PaperCard, Integer> e : section) {
            final PaperCard card = e.getKey();
            if (DraftDeckContext.isBasic(card)) {
                continue;
            }
            owned.merge(card, e.getValue(), Integer::sum);
            ownedByName.merge(DraftDeckContext.normalizedName(card.getName()),
                    e.getValue(), Integer::sum);
            final List<PaperCard> arts =
                    printings.computeIfAbsent(card.getName(), k -> new ArrayList<>());
            if (!arts.contains(card)) {
                arts.add(card);
            }
        }
    }

    // ------------------------------------------------------------------

    @Override
    public String getLabel() {
        return forge.neo.NeoText.get(sealed ? "net.event.sealed" : "net.event.draft");
    }

    /** Limitado: minimo 40 cartas y sin techo de copias mas alla del pool. */
    @Override
    public DeckFormat deckFormat() {
        return (sealed ? GameType.Sealed : GameType.Draft).getDeckFormat();
    }

    @Override
    public IStorage<Deck> storage() {
        return storage;
    }

    @Override
    public List<PaperCard> pool() {
        return pool;
    }

    @Override
    public boolean poolInSideboard() {
        return true;
    }

    @Override
    public int owned(final PaperCard card) {
        if (card == null) {
            return 0;
        }
        if (DraftDeckContext.isBasic(card)) {
            return Integer.MAX_VALUE;
        }
        return ownedByName.getOrDefault(DraftDeckContext.normalizedName(card.getName()), 0);
    }

    /** Solo los artes que han salido en tus sobres. Las basicas, cualquiera. */
    @Override
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        if (DraftDeckContext.isBasic(card)) {
            return null;
        }
        final List<PaperCard> mine = printings.get(card.getName());
        return mine == null || mine.isEmpty() ? List.of() : new ArrayList<>(mine);
    }

    @Override
    public String catalogueLabel() {
        return forge.neo.NeoText.get(sealed ? "deck.poolSealed" : "deck.poolDraft");
    }
}
