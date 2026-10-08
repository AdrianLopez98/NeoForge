package forge.neo.deck;

import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.game.GameFormat;
import forge.item.PaperCard;
import forge.neo.match.NeoFormat;
import forge.util.storage.IStorage;

/**
 * Montar un mazo que vive en una coleccion ({@link DeckCollections}).
 *
 * <p>Es su formato tal cual — mismas reglas, mismo catalogo, mismo todo —
 * salvo el cajon: se guarda en la carpeta de la coleccion. Sin esto el
 * constructor guardaba en la carpeta del formato, y editar un mazo de "Sets
 * de 2010" lo <b>duplicaba</b> en "Mis mazos" (y el renombrado no encontraba
 * el viejo, porque lo buscaba en el cajon equivocado).
 *
 * <p>Delega TODO lo demas, incluidos los metodos con valor por defecto: si un
 * dia {@code NeoFormat} sobrescribe alguno, aqui tiene que seguir valiendo lo
 * mismo que alli.
 */
public final class CollectionContext implements DeckContext {

    private final NeoFormat format;
    private final String collection;

    public CollectionContext(final NeoFormat format, final String collection) {
        this.format = format;
        this.collection = collection;
    }

    public NeoFormat getNeoFormat() {
        return format;
    }

    public String getCollection() {
        return collection;
    }

    @Override
    public IStorage<Deck> storage() {
        final IStorage<Deck> s = DeckCollections.storage(format, collection);
        // La carpeta ha desaparecido por debajo (borrada a mano con el
        // constructor abierto): mejor guardar en "Mis mazos" que perder el mazo.
        return s != null ? s : format.storage();
    }

    @Override public String getLabel() { return format.getLabel(); }
    @Override public DeckFormat deckFormat() { return format.deckFormat(); }
    @Override public boolean poolInSideboard() { return format.poolInSideboard(); }
    @Override public forge.deck.CardPool autoBuild(final List<PaperCard> pool) { return format.autoBuild(pool); }
    @Override public boolean canAutoBuild() { return format.canAutoBuild(); }
    @Override public String conformanceProblem(final Deck deck) { return format.conformanceProblem(deck); }
    @Override public boolean enforcesCardPool() { return format.enforcesCardPool(); }
    @Override public GameFormat poolFormat() { return format.poolFormat(); }
    @Override public List<PaperCard> pool() { return format.pool(); }
    @Override public boolean isLimited() { return format.isLimited(); }
    @Override public String catalogueLabel() { return format.catalogueLabel(); }
    @Override public boolean canRename() { return format.canRename(); }
    @Override public boolean canCopy() { return format.canCopy(); }
    @Override public int owned(final PaperCard card) { return format.owned(card); }
    @Override public List<PaperCard> printingsOf(final PaperCard card) { return format.printingsOf(card); }
    @Override public boolean onlyFitsByDefault() { return format.onlyFitsByDefault(); }
    @Override public boolean tracksAcquisition() { return format.tracksAcquisition(); }
    @Override public long acquiredAt(final PaperCard card) { return format.acquiredAt(card); }

    @Override
    public List<Action> catalogueActions(final PaperCard card, final int copiesInThisDeck) {
        return format.catalogueActions(card, copiesInThisDeck);
    }
}
