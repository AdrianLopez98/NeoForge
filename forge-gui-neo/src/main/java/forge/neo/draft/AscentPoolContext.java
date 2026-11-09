package forge.neo.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.item.PaperCard;
import forge.neo.ascent.AscentDraft;
import forge.neo.ascent.AscentSeedDeck;
import forge.neo.deck.DeckContext;
import forge.util.storage.IStorage;
import forge.util.storage.StorageBase;

/**
 * <b>Montar el mazo de salida de Ascenso con el pool del draft</b> (09-11-2026,
 * Discord: <i>"Basic Lands can be added afterwards, as well as trimming the deck
 * to a total size of 30. I guess its kinda more like sealed"</i>).
 *
 * <p>El cuarto {@code DeckContext} de limitado, hermano de
 * {@link DraftDeckContext} y {@link NetPoolContext}: el catalogo son tus
 * {@value AscentDraft#PICKS} picks mas las cinco basicas, el mazo empieza vacio
 * con todo el pool en la banda ({@code poolInSideboard}) y "Montar solo" es
 * {@link AscentDraft#autoBuild} (18 hechizos y 12 basicas). La unica regla
 * propia: <b>exactamente {@link AscentSeedDeck#STANDARD_SIZE} cartas</b>, la
 * invariante de todo el modo.
 *
 * <p><b>Donde se guarda: en ningun sitio.</b> El almacen es de memoria, y
 * guardar un mazo valido es lo que empieza la run ({@code onSaved}): la run no
 * existe hasta entonces, asi que salir del constructor sin guardar no cuesta
 * nada mas que el draft. Lo usan el escritorio ({@code NeoAppAscent}) y
 * Android ({@code AscentBridge}), por el jar.
 */
public final class AscentPoolContext implements DeckContext {

    private final Map<PaperCard, Integer> owned = new HashMap<>();
    private final Map<String, Integer> ownedByName = new HashMap<>();
    private final List<PaperCard> pool = new ArrayList<>();
    private final Map<String, List<PaperCard>> printings = new HashMap<>();
    private final Map<String, PaperCard> basicCards = new HashMap<>();
    private final Memory storage;
    private final Deck start;

    /**
     * @param picks   lo cogido en los sobres
     * @param name    como se llama el mazo
     * @param onSaved lo que pasa al guardar un mazo de 30 (empezar la run); puede ser null
     */
    public AscentPoolContext(final List<PaperCard> picks, final String name, final Consumer<Deck> onSaved) {
        this.storage = new Memory(onSaved, this);
        this.start = new Deck(name);
        final CardPool side = start.getOrCreate(DeckSection.Sideboard);
        for (final PaperCard c : picks) {
            if (c != null) {
                side.add(c);
            }
        }
        for (final Map.Entry<PaperCard, Integer> e : side) {
            final PaperCard card = e.getKey();
            owned.merge(card, e.getValue(), Integer::sum);
            ownedByName.merge(DraftDeckContext.normalizedName(card.getName()), e.getValue(), Integer::sum);
            final List<PaperCard> arts = printings.computeIfAbsent(card.getName(), k -> new ArrayList<>());
            if (!arts.contains(card)) {
                arts.add(card);
            }
        }
        pool.addAll(owned.keySet());
        pool.sort(java.util.Comparator.comparing(PaperCard::getName).thenComparing(PaperCard::getEdition));
        final List<PaperCard> basics = DraftDeckContext.basics(start);
        for (final PaperCard b : basics) {
            basicCards.put(b.getName(), b);
        }
        pool.addAll(basics);
    }

    /** El mazo con el que se abre el constructor: vacio, con el pool en la banda. */
    public Deck startingDeck() {
        return start;
    }

    // ------------------------------------------------------------------

    @Override
    public String getLabel() {
        return forge.neo.NeoText.get("ascent.draft.build");
    }

    /** Limitado: sin techo de copias mas alla del pool. El tamano lo dice {@link #conformanceProblem}. */
    @Override
    public DeckFormat deckFormat() {
        return GameType.Sealed.getDeckFormat();
    }

    @Override
    public String conformanceProblem(final Deck deck) {
        final int size = deck == null ? 0 : deck.getMain().countAll();
        return size == AscentSeedDeck.STANDARD_SIZE ? null
                : forge.neo.NeoText.get("ascent.draft.size", AscentSeedDeck.STANDARD_SIZE, size);
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
    public boolean canAutoBuild() {
        return true;
    }

    @Override
    public CardPool autoBuild(final List<PaperCard> cards) {
        return AscentDraft.autoBuild(cards, basicCards);
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

    @Override
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return new ArrayList<>();
        }
        if (DraftDeckContext.isBasic(card)) {
            return null;
        }
        final List<PaperCard> mine = printings.get(card.getName());
        return mine == null ? new ArrayList<>() : new ArrayList<>(mine);
    }

    @Override
    public String catalogueLabel() {
        return forge.neo.NeoText.get("ascent.draft.pool");
    }

    @Override
    public boolean canRename() {
        return false;
    }

    @Override
    public boolean canCopy() {
        return false;
    }

    /** El almacen de memoria: guardar un mazo de 30 empieza la run. */
    private static final class Memory extends StorageBase<Deck> {
        private final Map<String, Deck> decks;
        private final Consumer<Deck> onSaved;
        private final AscentPoolContext owner;

        Memory(final Consumer<Deck> onSaved, final AscentPoolContext owner) {
            this(new HashMap<>(), onSaved, owner);
        }

        private Memory(final Map<String, Deck> decks, final Consumer<Deck> onSaved, final AscentPoolContext owner) {
            super("ascent-draft", null, decks);
            this.decks = decks;
            this.onSaved = onSaved;
            this.owner = owner;
        }

        @Override
        public void add(final Deck deck) {
            add(deck.getName(), deck);
        }

        @Override
        public void add(final String name, final Deck deck) {
            decks.put(name, deck);
            if (onSaved != null && owner.conformanceProblem(deck) == null) {
                onSaved.accept(deck);
            }
        }

        @Override
        public void delete(final String name) {
            decks.remove(name);
        }
    }
}
