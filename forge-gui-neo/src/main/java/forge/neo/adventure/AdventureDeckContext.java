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
 * Adventure: el catalogo es su coleccion, el techo de copias lo que tiene
 * <b>menos lo marcado para autovender</b>, los artes los que tiene, y
 * las <b>basicas son ilimitadas</b> (la franja de basicas la pone el propio editor) — como en su editor, que fabrica copias "no
 * vendibles" cuando pides mas de las que tienes. Eso se hace al guardar.
 *
 * <p>Se guarda en el <b>mazo elegido</b> del Adventure, en su sitio: se
 * sustituye el contenido del mismo objeto {@code Deck}, asi que todo lo que el
 * Adventure tenga apuntando a el (la ranura, el equipo atado al mazo) sigue
 * valiendo.
 *
 * <p><b>Autovender</b> (19-09-2026): su editor tiene una pagina aparte para lo
 * que se vende solo en la siguiente tienda; el nuestro no tenia nada. Va en el
 * menu de click derecho del catalogo ({@link #catalogueActions}), y por eso lo
 * marcado para vender <b>sigue en el catalogo</b> — con cero disponibles — en
 * vez de desaparecer como en el suyo: sin su pagina, desaparecer seria no poder
 * sacarlo nunca. Nunca se marca una copia que este en un mazo, ni en el que se
 * esta editando aunque no este guardado.
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
        for (final Map.Entry<PaperCard, Integer> e : player.getCollectionCards(true)) {
            final String key = key(e.getKey());
            unique.putIfAbsent(key, e.getKey());
            final List<PaperCard> arts = printings.computeIfAbsent(key, k -> new ArrayList<>());
            if (!arts.contains(e.getKey())) {
                arts.add(e.getKey());
            }
        }
        pool.addAll(unique.values());
        pool.sort(Comparator.comparing(PaperCard::getName));
        countOwned();
    }

    /** Las que puedes meter en un mazo: todas menos las marcadas para autovender. */
    private void countOwned() {
        owned.clear();
        for (final Map.Entry<PaperCard, Integer> e : player.getCollectionCards(false)) {
            owned.merge(key(e.getKey()), e.getValue(), Integer::sum);
        }
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

    /**
     * En la Aventura, no.
     *
     * <p>El formato pone el tamanyo del mazo, el limite de copias y la
     * identidad de color — eso son reglas de construccion y valen aqui igual
     * que en cualquier sitio. Lo que no vale es su <b>lista de prohibidas</b>:
     * dentro de la Aventura el pozo de cartas es tu coleccion, y lo que hay en
     * ella te lo ha dado el propio modo como premio. Ver
     * {@link DeckContext#enforcesCardPool()} para el por que entero y para lo
     * que costaba (reportado en Reddit el 22-09-2026).
     */
    @Override
    public boolean enforcesCardPool() {
        return false;
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

    /** Sus mazos son ranuras fijas: una copia pisaria la tuya. Ver DeckContext.canCopy. */
    @Override
    public boolean canCopy() {
        return false;
    }

    /** Lo apunta {@link AcquiredLedger}: el Adventure no lo guarda. */
    @Override
    public boolean tracksAcquisition() {
        return true;
    }

    /**
     * <b>Las basicas de tus Landscape Sketchbook</b> (Discord, 08-10-2026:
     * <i>"there is a thing called landscape sketchbooks, it used to add basic
     * land to our deck ... the current neoforge adventure mode deck building
     * tools doesn't support it"</i>).
     *
     * <p>En su editor, "Anyadir basicas" ofrece las cinco de cada edicion cuyo
     * <i>Landscape Sketchbook - &lt;edicion&gt;</i> tengas, mas la de fabrica del
     * mundo; el nuestro solo ensenyaba las basicas que ya tenias en la
     * coleccion, asi que sin ninguna Island no habia forma de meter una, y el
     * cuaderno comprado no servia de nada. Es su
     * {@code AdventureDeckEditor.getBasicLandSets} tal cual, incluidos los
     * nombres de objeto con dos puntos o apostrofes de menos. Se lee al abrir
     * el editor: los objetos no cambian mientras esta abierto.
     */
    @Override
    public List<forge.card.CardEdition> basicLandEditions() {
        if (landSets == null) {
            landSets = readLandSets();
        }
        return landSets;
    }

    private List<forge.card.CardEdition> landSets;

    private List<forge.card.CardEdition> readLandSets() {
        final List<forge.card.CardEdition> out = new ArrayList<>();
        // Solo pruebas: -Dneo.mock.landSets=DOM,JMP hace como si tuvieras esos
        // cuadernos, para capturar el desplegable sin una partida que los tenga.
        final String mock = System.getProperty("neo.mock.landSets");
        if (mock != null) {
            for (final String code : mock.split(",")) {
                final forge.card.CardEdition e = forge.model.FModel.getMagicDb().getEditions().get(code.trim());
                if (e != null && !out.contains(e)) {
                    out.add(e);
                }
            }
            return out;
        }
        try {
            final Map<String, forge.card.CardEdition> byName = new HashMap<>();
            for (final forge.card.CardEdition e : forge.model.FModel.getMagicDb().getEditions()) {
                final String n = e.getName().toLowerCase(Locale.ROOT);
                byName.put(n, e);
                byName.put(n.replace(":", ""), e);
                byName.put(n.replace("'", ""), e);
            }
            final String prefix = "landscape sketchbook - ";
            for (final forge.adventure.data.ItemData item : player.getItems()) {
                if (item == null || item.name == null
                        || !item.name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    continue;
                }
                final forge.card.CardEdition e = byName.get(
                        item.name.substring(prefix.length()).trim().toLowerCase(Locale.ROOT));
                if (e != null && e.hasBasicLands() && !out.contains(e)) {
                    out.add(e);
                }
            }
            final String code = forge.adventure.util.Config.instance().getConfigData().defaultBasicLandSet;
            final forge.card.CardEdition def = code == null ? null
                    : forge.model.FModel.getMagicDb().getEditions().get(code);
            if (def != null && !out.contains(def)) {
                out.add(def);
            }
        } catch (final RuntimeException ex) {
            // Sin objetos legibles: las basicas que tengas en la coleccion, como antes.
        }
        return out;
    }

    /**
     * Como {@code FDeckViewer.copyCollectionToClipboard}: cuantas tienes de
     * cada NOMBRE (todas las impresiones juntas) y el nombre en ingles, que es
     * lo que entienden Moxfield y los importadores. Ordenado por nombre, que el
     * suyo sale en el orden del mapa.
     */
    @Override
    public boolean hasPrices() {
        return true;
    }

    /** Lo que te dan al venderla, lo mismo que dice su editor ("Sell for N"). */
    @Override
    public int price(final PaperCard card) {
        if (card == null || isBasic(card) || card.hasNoSellValue()) {
            return 0;
        }
        try {
            return player.cardSellPrice(card);
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /**
     * "Autovender lo filtrado" ("Auto-sell current filters" de su editor): lo
     * que SOBRA de cada carta — nunca lo que esta en un mazo, ni en el que se
     * edita sin guardar, ni lo ya marcado —, en UNA pasada por el hilo de
     * libGDX y no una por carta.
     */
    @Override
    public BulkSell bulkAutoSell(final List<PaperCard> cards,
                                 final java.util.function.ToIntFunction<PaperCard> inThisDeck) {
        final Map<String, PaperCard> byKey = new java.util.LinkedHashMap<>();
        for (final PaperCard c : cards) {
            if (c != null && !isBasic(c) && !c.hasNoSellValue()) {
                byKey.putIfAbsent(key(c), c);
            }
        }
        final Map<PaperCard, Integer> plan = new java.util.LinkedHashMap<>();
        int copies = 0;
        int value = 0;
        for (final PaperCard c : byKey.values()) {
            final String k = key(c);
            int total = 0;
            int used = 0;
            for (final Map.Entry<PaperCard, Integer> e : player.getCards()) {
                if (key(e.getKey()).equals(k)) {
                    total += e.getValue();
                    used += player.getCopiesUsedInDecks(e.getKey());
                }
            }
            final int spare = Math.max(0, total - Math.max(used, inThisDeck.applyAsInt(c))
                    - countIn(player.getAutoSellCards(), k));
            if (spare > 0) {
                plan.put(c, spare);
                copies += spare;
                value += spare * price(c);
            }
        }
        final int cardCount = plan.size();
        return new BulkSell(cardCount, copies, value, () -> {
            onGdx(() -> {
                for (final Map.Entry<PaperCard, Integer> e : plan.entrySet()) {
                    markSpare(e.getKey(), e.getValue());
                }
                NeoDuelBridge.log("autovender lo filtrado: " + cardCount + " cartas");
            });
            countOwned();
        });
    }

    @Override
    public String collectionText() {
        final java.util.TreeMap<String, Integer> byName = new java.util.TreeMap<>();
        for (final Map.Entry<PaperCard, Integer> e : player.getCards()) {
            byName.merge(e.getKey().getCardName(), e.getValue(), Integer::sum);
        }
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Integer> e : byName.entrySet()) {
            sb.append(e.getValue()).append(' ').append(e.getKey()).append(System.lineSeparator());
        }
        return sb.toString();
    }

    @Override
    public long acquiredAt(final PaperCard card) {
        return AcquiredLedger.acquiredAt(card);
    }

    @Override
    public boolean onlyFitsByDefault() {
        return false;
    }

    @Override
    public List<Action> catalogueActions(final PaperCard card, final int copiesInThisDeck) {
        // Las basicas salen gratis y lo "no vendible" no tiene precio: como en su editor.
        if (card == null || isBasic(card) || card.hasNoSellValue()) {
            return List.of();
        }
        final String k = key(card);
        int total = 0;
        int used = 0;
        for (final Map.Entry<PaperCard, Integer> e : player.getCards()) {
            if (key(e.getKey()).equals(k)) {
                total += e.getValue();
                used += player.getCopiesUsedInDecks(e.getKey());
            }
        }
        if (total <= 0) {
            return List.of();
        }
        final int inAutoSell = countIn(player.getAutoSellCards(), k);
        // Lo que se puede vender sin dejar cojo ningun mazo: los guardados y
        // el que se esta editando ahora.
        final int spare = Math.max(0, total - Math.max(used, copiesInThisDeck) - inAutoSell);
        final List<Action> out = new ArrayList<>();
        final String none = spare > 0 ? null : forge.neo.NeoText.get("adventure.autosell.noSpare");
        out.add(new Action(forge.neo.NeoText.get("adventure.autosell.one"), none, spare > 0,
                () -> moveToAutoSell(card, 1)));
        if (spare > 1) {
            out.add(new Action(forge.neo.NeoText.get("adventure.autosell.many", spare), null, true,
                    () -> moveToAutoSell(card, spare)));
        }
        if (inAutoSell > 0) {
            out.add(new Action(forge.neo.NeoText.get("adventure.autosell.back"), null, true,
                    () -> takeBack(card, 1)));
            if (inAutoSell > 1) {
                out.add(new Action(forge.neo.NeoText.get("adventure.autosell.backAll", inAutoSell), null,
                        true, () -> takeBack(card, inAutoSell)));
            }
        }
        return out;
    }

    private static int countIn(final Iterable<Map.Entry<PaperCard, Integer>> pool, final String k) {
        int n = 0;
        for (final Map.Entry<PaperCard, Integer> e : pool) {
            if (key(e.getKey()).equals(k)) {
                n += e.getValue();
            }
        }
        return n;
    }

    /**
     * Marca {@code amount} copias para autovender, repartidas entre las
     * impresiones que tienes de esa carta: primero las que mas sobran. De una
     * impresion que esta en un mazo solo se marca lo que sobra.
     */
    private void moveToAutoSell(final PaperCard card, final int amount) {
        onGdx(() -> {
            final int done = markSpare(card, amount);
            NeoDuelBridge.log("autovender: +" + done + " " + card.getName());
        });
        countOwned();
    }

    /**
     * El reparto de {@link #moveToAutoSell}, sin cambiar de hilo: ya se esta en
     * el de libGDX. Devuelve cuantas se han marcado.
     */
    private int markSpare(final PaperCard card, final int amount) {
        final String k = key(card);
        int left = amount;
        final List<PaperCard> mine = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : player.getCards()) {
            if (key(e.getKey()).equals(k)) {
                mine.add(e.getKey());
            }
        }
        mine.sort(Comparator.comparingInt((PaperCard c) -> -spareOf(c)));
        for (final PaperCard c : mine) {
            if (left <= 0) {
                break;
            }
            final int take = Math.min(left, spareOf(c));
            if (take > 0) {
                player.getAutoSellCards().add(c, take);
                left -= take;
            }
        }
        return amount - left;
    }

    /** Copias de esa impresion que no estan en ningun mazo ni ya en autovender. */
    private int spareOf(final PaperCard c) {
        return player.getCards().count(c) - player.getCopiesUsedInDecks(c)
                - player.getAutoSellCards().count(c);
    }

    /** Devuelve {@code amount} copias de autovender a la coleccion. */
    private void takeBack(final PaperCard card, final int amount) {
        final String k = key(card);
        onGdx(() -> {
            int left = amount;
            final List<Map.Entry<PaperCard, Integer>> marked = new ArrayList<>();
            for (final Map.Entry<PaperCard, Integer> e : player.getAutoSellCards()) {
                if (key(e.getKey()).equals(k)) {
                    marked.add(Map.entry(e.getKey(), e.getValue()));
                }
            }
            for (final Map.Entry<PaperCard, Integer> e : marked) {
                if (left <= 0) {
                    break;
                }
                final int take = Math.min(left, e.getValue());
                player.getAutoSellCards().remove(e.getKey(), take);
                left -= take;
            }
            NeoDuelBridge.log("autovender: -" + (amount - left) + " " + card.getName());
        });
        countOwned();
    }

    /** Lo que toca al jugador del Adventure, en su hilo (como el guardado). */
    private static void onGdx(final Runnable task) {
        final CountDownLatch done = new CountDownLatch(1);
        Gdx.app.postRunnable(() -> {
            try {
                task.run();
            } catch (final Throwable e) {
                NeoDuelBridge.log("autovender ha fallado: " + e);
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
