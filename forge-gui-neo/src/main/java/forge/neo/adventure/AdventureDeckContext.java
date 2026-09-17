package forge.neo.adventure;

import com.badlogic.gdx.Gdx;
import forge.adventure.player.AdventurePlayer;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.item.PaperCard;
import forge.neo.deck.DeckContext;
import forge.util.storage.IStorage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * nuestro deck builder montando mazos del Adventure.
 *
 * <p>Lo mismo que {@code QuestDeckContext} pero leyendo del jugador del
 * Adventure: el catalogo es su coleccion (sin lo marcado para autovender, como
 * en su editor), el techo de copias lo que tiene, los artes los que tiene, y
 * las <b>basicas son ilimitadas</b> (la franja de basicas la pone el propio editor) — como en su editor, que fabrica copias "no
 * vendibles" cuando pides mas de las que tienes. Eso se hace al guardar.
 *
 * <p>Se guarda en el <b>mazo elegido</b> del Adventure, en su sitio: se
 * sustituye el contenido del mismo objeto {@code Deck}, asi que todo lo que el
 * Adventure tenga apuntando a el (la ranura, el equipo atado al mazo) sigue
 * valiendo.
 */
final class AdventureDeckContext implements DeckContext {

    private final AdventurePlayer player;
    private final List<PaperCard> pool = new ArrayList<>();
    private final Map<String, Integer> owned = new HashMap<>();
    private final Map<String, List<PaperCard>> printings = new HashMap<>();
    private final SlotStorage storage = new SlotStorage();

    AdventureDeckContext(final AdventurePlayer player) {
        this.player = player;
        final Map<String, PaperCard> unique = new HashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : player.getCollectionCards(false)) {
            final String key = key(e.getKey());
            owned.merge(key, e.getValue(), Integer::sum);
            unique.putIfAbsent(key, e.getKey());
            final List<PaperCard> arts = printings.computeIfAbsent(key, k -> new ArrayList<>());
            if (!arts.contains(e.getKey())) {
                arts.add(e.getKey());
            }
        }
        pool.addAll(unique.values());
        pool.sort(Comparator.comparing(PaperCard::getName));
    }

    private static String key(final PaperCard card) {
        return card == null ? "" : card.getName().toLowerCase(Locale.ROOT);
    }

    private static boolean isBasic(final PaperCard card) {
        return card != null && card.isVeryBasicLand();
    }

    @Override
    public String getLabel() {
        return "Adventure";
    }

    @Override
    public DeckFormat deckFormat() {
        return player.isCommanderMode() ? GameType.Commander.getDeckFormat()
                : GameType.Adventure.getDeckFormat();
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
    public int owned(final PaperCard card) {
        if (isBasic(card)) {
            return Integer.MAX_VALUE;
        }
        return card == null ? 0 : owned.getOrDefault(key(card), 0);
    }

    @Override
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        if (isBasic(card)) {
            return null; // las basicas, con cualquier arte: salen gratis
        }
        final List<PaperCard> mine = printings.get(key(card));
        return mine == null ? List.of() : new ArrayList<>(mine);
    }

    Deck currentDeck() {
        return player.getSelectedDeck();
    }

    /** Escribe {@code edited} dentro del mazo elegido, en el hilo de libGDX. */
    private void writeBack(final Deck edited) {
        final CountDownLatch done = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> {
            try {
                final Deck target = player.getSelectedDeck();
                for (final DeckSection section : DeckSection.values()) {
                    final CardPool from = edited.get(section);
                    if (from == null || from.isEmpty()) {
                        if (target.get(section) != null) {
                            target.get(section).clear();
                        }
                        continue;
                    }
                    final CardPool to = target.getOrCreate(section);
                    to.clear();
                    to.addAll(from);
                }
                payForBasics(target);
                if (edited.getName() != null && !edited.getName().equals(target.getName())) {
                    player.renameDeck(edited.getName());
                }
                NeoDuelBridge.log("mazo guardado en el Adventure: " + target.getName()
                        + " (" + target.getMain().countAll() + " cartas)");
            } catch (final Throwable e) {
                NeoDuelBridge.log("no se pudo guardar el mazo: " + e);
                e.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        try {
            done.await(10, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Las basicas que el mazo lleva por encima de las que tienes pasan a ser
     * copias "no vendibles" nuevas, que es exactamente lo que hace el editor
     * del Adventure ({@code AdventureDeckEditor.addChosenBasicLands}).
     */
    private void payForBasics(final Deck deck) {
        final CardPool main = deck.getMain();
        final CardPool newCopies = new CardPool();
        for (final Map.Entry<PaperCard, Integer> e : new ArrayList<>(main.toFlatList().stream()
                .filter(AdventureDeckContext::isBasic).distinct()
                .map(c -> Map.entry(c, main.count(c))).toList())) {
            final PaperCard card = e.getKey();
            final int have = player.getCards().count(card);
            final int excess = e.getValue() - have;
            if (excess <= 0) {
                continue;
            }
            final PaperCard noSell = card.getNoSellVersion();
            main.remove(card, excess);
            main.add(noSell, excess);
            final int haveNoSell = player.getCards().count(noSell);
            final int missing = main.count(noSell) - haveNoSell;
            if (missing > 0) {
                newCopies.add(noSell, missing);
            }
        }
        if (!newCopies.isEmpty()) {
            player.addCards(newCopies);
        }
    }

    /** Un "almacen" de un solo mazo: el elegido en el Adventure. */
    private final class SlotStorage implements IStorage<Deck> {

        private Deck current() {
            return currentDeck();
        }

        @Override
        public String getName() {
            return "Adventure";
        }

        @Override
        public String getFullPath() {
            return "adventure";
        }

        @Override
        public Deck get(final String name) {
            final Deck d = current();
            return d != null && d.getName().equals(name) ? d : null;
        }

        @Override
        public Deck find(final Predicate<Deck> condition) {
            final Deck d = current();
            return d != null && condition.test(d) ? d : null;
        }

        @Override
        public Collection<String> getItemNames() {
            final Deck d = current();
            return d == null ? List.of() : List.of(d.getName());
        }

        @Override
        public boolean contains(final String name) {
            return get(name) != null;
        }

        @Override
        public int size() {
            return current() == null ? 0 : 1;
        }

        @Override
        public void add(final Deck item) {
            writeBack(item);
        }

        @Override
        public void add(final String name, final Deck item) {
            writeBack(item);
        }

        @Override
        public void delete(final String deckName) {
            // Borrar ranuras se hace desde el Adventure.
        }

        @Override
        public IStorage<IStorage<Deck>> getFolders() {
            return null;
        }

        @Override
        public IStorage<Deck> tryGetFolder(final String path) {
            return null;
        }

        @Override
        public IStorage<Deck> getFolderOrCreate(final String path) {
            return null;
        }

        @Override
        public Stream<Deck> stream() {
            final Deck d = current();
            return d == null ? Stream.empty() : Stream.of(d);
        }

        @Override
        public Iterator<Deck> iterator() {
            return stream().iterator();
        }
    }
}
