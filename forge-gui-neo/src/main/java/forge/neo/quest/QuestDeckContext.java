package forge.neo.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.game.GameType;
import forge.item.PaperCard;
import forge.neo.deck.DeckContext;
import forge.util.storage.IStorage;

/**
 * Montar mazos <b>con lo que tienes</b>, dentro de la aventura.
 *
 * <p>Es el mismo editor que el general — mismo buscador, misma curva, mismas
 * columnas — cambiando solo tres cosas:
 *
 * <ol>
 *   <li><b>El catalogo es tu coleccion</b>, no las 33.000 cartas de Magic. Es
 *       lo que cierra el bucle del modo: abres un sobre, ves que te ha salido,
 *       y decides si lo metes.</li>
 *   <li><b>El techo de copias es lo que tienes.</b> En una aventura de Estandar
 *       podrias llevar cuatro Rayos, pero si solo has abierto uno, llevas uno.
 *       Eso no es una regla de Magic, es el limite de tu coleccion, y por eso
 *       vive aqui y no en el {@code DeckFormat}.</li>
 *   <li><b>Se guarda en los mazos de la aventura</b>
 *       ({@code QuestController.getMyDecks()}), que son los que salen al elegir
 *       con que juegas el duelo.</li>
 * </ol>
 *
 * <p>La legalidad la sigue contestando el motor entero, y ahi se juntan DOS
 * reglamentos: el de Quest (minimo de cartas, banda) y ademas el de Commander
 * si la aventura es de Commander. De eso ya sabe
 * {@code QuestUtil.getDeckConformanceProblemsBeforeGame}, que es justo lo que
 * usa {@link NeoQuest#problemWith(Deck)}.
 *
 * <p><b>Se cuenta por NOMBRE, no por impresion.</b> Si tienes el Sol Ring de
 * Commander 2016 y el de Modern Horizons, tienes dos Sol Rings — que es lo que
 * diria cualquiera mirando su coleccion. Es la misma regla que usa la tienda
 * para decidir que carta es "NUEVA".
 *
 * <p><b>Pero el ARTE si va por impresion.</b> Cuantas llevas se cuenta por
 * nombre; cual de ellas puedes poner, no. Aqui el arte no es un adorno: es
 * parte del boton. Si te ha salido el Rayo normal llevas el normal, y para
 * llevar el borderless hay que abrirlo. Ver {@link #printingsOf(PaperCard)}.
 */
public final class QuestDeckContext implements DeckContext {

    private final List<PaperCard> pool;
    private final Map<String, Integer> owned;

    /**
     * Las impresiones concretas que tienes de cada carta.
     *
     * <p>La coleccion del motor guarda {@code PaperCard}s exactos — con su
     * edicion y su numero de coleccionista — asi que el dato ya estaba; lo que
     * haciamos era tirarlo al agrupar por nombre.
     */
    private final Map<String, List<PaperCard>> printings;

    /** Cuantas tienes de cada impresion exacta ({@code ArtWishes.printKey}). */
    private final Map<String, Integer> exact = new HashMap<>();

    /**
     * El interruptor "Todas las cartas" (Discord, 15-10-2026, como en Arena).
     * Apagado de fabrica: el constructor de la Quest se abre con tu coleccion,
     * como siempre.
     */
    private boolean showAll;

    /** Tu coleccion mas el resto de Magic que deja la Quest; se monta la primera vez. */
    private List<PaperCard> allPool;

    public QuestDeckContext() {
        this.pool = new ArrayList<>();
        this.owned = new HashMap<>();
        this.printings = new HashMap<>();

        // Una sola pasada por la coleccion: la lista para el catalogo y, de
        // paso, cuantas tienes de cada una. La coleccion se recorre muchas
        // veces al pintar y al validar.
        //
        // Del catalogo se queda UNA impresion por carta (la primera que salga):
        // para montar un mazo da igual de que edicion es el Sol Ring, y con
        // todas las impresiones el buscador sale lleno de duplicados.
        final Map<String, PaperCard> unique = new HashMap<>();
        if (NeoQuest.isActive() && NeoQuest.collection() != null) {
            for (final Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
                final String key = key(e.getKey());
                owned.merge(key, e.getValue(), Integer::sum);
                exact.merge(forge.neo.deck.ArtWishes.printKey(e.getKey()), e.getValue(), Integer::sum);
                unique.putIfAbsent(key, e.getKey());
                final List<PaperCard> arts =
                        printings.computeIfAbsent(key, k -> new ArrayList<>());
                if (!arts.contains(e.getKey())) {
                    arts.add(e.getKey());
                }
            }
        }
        pool.addAll(unique.values());
        pool.sort(java.util.Comparator.comparing(PaperCard::getName));
        // Lo que haya entrado por un camino que guarda por su cuenta, con hora
        // antes de abrir el editor ("lo ultimo primero").
        QuestAcquired.sync();
    }

    /** "Lo ultimo primero" (Discord, 16-10-2026). Ver {@link QuestAcquired}. */
    @Override
    public boolean tracksAcquisition() {
        return true;
    }

    @Override
    public long acquiredAt(final PaperCard card) {
        return QuestAcquired.acquiredAt(card);
    }

    @Override
    public String acquisitionTip() {
        return "deck.newest.tip.quest";
    }

    private static String key(final PaperCard card) {
        return card == null ? "" : card.getName().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getLabel() {
        return forge.neo.NeoText.get("menu.quest");
    }

    /**
     * Las reglas de construccion del editor.
     *
     * <p>En una aventura de Commander manda el reglamento de Commander: una
     * copia por carta, identidad de color, y zona de mando. En una de Estandar,
     * el de Quest, que es un construido normal con minimo de cartas mas bajo.
     *
     * <p>Ojo: esto es lo que usa el editor para "cuantas caben" y "puede ser
     * comandante". Lo de si el mazo ENTERO vale para jugar es
     * {@link #conformanceProblem(Deck)}, que junta los dos reglamentos.
     */
    @Override
    public DeckFormat deckFormat() {
        return NeoQuest.isActive() && NeoQuest.modalidad() == NeoQuest.Modalidad.COMMANDER
                ? GameType.Commander.getDeckFormat()
                : GameType.Quest.getDeckFormat();
    }

    @Override
    public String conformanceProblem(final Deck deck) {
        return NeoQuest.problemWith(deck);
    }

    /**
     * Los mazos de la aventura, y <b>guardar uno escribe la aventura</b>.
     *
     * <p>{@code getMyDecks()} vive dentro de los datos de la Quest, en memoria:
     * meter ahi un mazo no lo escribe en el disco, eso lo hace
     * {@code QuestController.save()}. El escritorio lo llamaba al salir del
     * editor ({@code NeoAppQuest.showQuestDeckBuilder}); Android no, y la
     * aventura solo se escribia al acabar un duelo. Discord, 11-10-2026, con dos
     * videos: <i>"if you changed anything in your deck in quest mode, saved
     * then quit to other mode or just quit out from quest itself the deck won't
     * saved but if you saved then enter the next match right away then the deck
     * will saved as normal"</i> — al volver a la Quest se releia del disco.
     *
     * <p>Aqui y no en cada pantalla, por el principio 8: guardar, borrar y
     * renombrar pasan TODOS por este almacen, el editor del PC, el de Android y
     * el importador.
     */
    @Override
    public IStorage<Deck> storage() {
        return new SavingStorage(forge.model.FModel.getQuest().getMyDecks());
    }

    /** El almacen de la aventura que, al cambiar, la escribe. Ver {@link #storage()}. */
    private static final class SavingStorage implements IStorage<Deck> {
        private final IStorage<Deck> inner;

        SavingStorage(final IStorage<Deck> inner) {
            this.inner = inner;
        }

        private static void persist() {
            try {
                NeoQuest.save();
            } catch (final RuntimeException e) {
                System.out.println("[quest] no se ha podido guardar la aventura tras cambiar un mazo: " + e);
            }
        }

        @Override
        public void add(final Deck item) {
            inner.add(item);
            persist();
        }

        @Override
        public void add(final String name, final Deck item) {
            inner.add(name, item);
            persist();
        }

        @Override
        public void delete(final String deckName) {
            inner.delete(deckName);
            persist();
        }

        @Override
        public String getName() {
            return inner.getName();
        }

        @Override
        public String getFullPath() {
            return inner.getFullPath();
        }

        @Override
        public Deck get(final String name) {
            return inner.get(name);
        }

        @Override
        public Deck find(final java.util.function.Predicate<Deck> condition) {
            return inner.find(condition);
        }

        @Override
        public java.util.Collection<String> getItemNames() {
            return inner.getItemNames();
        }

        @Override
        public boolean contains(final String name) {
            return inner.contains(name);
        }

        @Override
        public int size() {
            return inner.size();
        }

        @Override
        public IStorage<IStorage<Deck>> getFolders() {
            return inner.getFolders();
        }

        @Override
        public IStorage<Deck> tryGetFolder(final String path) {
            return inner.tryGetFolder(path);
        }

        @Override
        public IStorage<Deck> getFolderOrCreate(final String path) {
            return inner.getFolderOrCreate(path);
        }

        @Override
        public java.util.stream.Stream<Deck> stream() {
            return inner.stream();
        }

        @Override
        public java.util.Iterator<Deck> iterator() {
            return inner.iterator();
        }
    }

    @Override
    public List<PaperCard> pool() {
        return showAll ? allPool() : pool;
    }

    /**
     * Lo que se pueden meter cartas que no tienes: siempre en la Quest. El
     * interruptor solo decide QUE ensenya el catalogo; un mazo guardado con
     * cartas de mas no se toca nunca al volver a abrirlo. Ver
     * {@link DeckContext#allowsMissing()}.
     */
    @Override
    public boolean allowsMissing() {
        return true;
    }

    @Override
    public boolean canShowAllCards() {
        return true;
    }

    /** Sin banquillo libre: cargar la Quest lo vacia. Ver DeckContext.allowsSideboard. */
    @Override
    public boolean allowsSideboard() {
        return false;
    }

    /** Con "Todas las cartas" ya no es tu coleccion: es el catalogo. */
    @Override
    public String catalogueLabel() {
        return forge.neo.NeoText.get(showAll ? "deck.catalogue" : "deck.collection");
    }

    @Override
    public boolean showsAllCards() {
        return showAll;
    }

    @Override
    public void setShowAllCards(final boolean on) {
        showAll = on;
    }

    /**
     * Tu coleccion y, ademas, una impresion de cada carta de Magic que puede
     * salir en ESTA Quest: si se creo con unas expansiones, solo esas
     * ({@code getMainFormat}); si no, todas. De lo tuyo se queda TU impresion,
     * para que el arte que veas sea el que tienes.
     */
    /**
     * Si una carta puede llegar a tus manos en la Quest. Fuera las rebalanceadas
     * de Alchemy ("A-..."): no salen en ningun sobre ni en la tienda, y la
     * mitad las mete el motor en el set desconocido ("???", donde
     * {@code isRebalanced()} ya no las reconoce). Llenaban la primera pagina,
     * sin foto, con cartas que nunca vas a tener.
     */
    private static boolean obtainable(final PaperCard c) {
        return !c.getName().startsWith("A-") && !c.isRebalanced()
                && !forge.card.CardEdition.UNKNOWN_CODE.equals(c.getEdition());
    }

    private List<PaperCard> allPool() {
        if (allPool != null) {
            return allPool;
        }
        final Map<String, PaperCard> byName = new HashMap<>();
        for (final PaperCard c : pool) {
            byName.put(key(c), c);
        }
        java.util.function.Predicate<PaperCard> allowed = c -> true;
        try {
            final forge.game.GameFormat f = NeoQuest.isActive() ? NeoQuest.engine().getMainFormat() : null;
            if (f != null && f.getAllowedSetCodes() != null && !f.getAllowedSetCodes().isEmpty()) {
                allowed = f.getFilterRules();
            }
        } catch (final RuntimeException e) {
            // Sin formato que leer: todo Magic, que es lo de una Quest normal.
        }
        // Las reliquias de Ascenso tampoco: son cartas nuestras registradas en
        // caliente y pueden acabar en getUniqueCards (ver CardIndex).
        final java.util.Set<String> ours = forge.neo.ascent.AscentRelics.allCardNames();
        for (final PaperCard c : forge.model.FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (c != null && c.getRules() != null && allowed.test(c) && obtainable(c)
                    && !ours.contains(c.getName())) {
                byName.putIfAbsent(key(c), c);
            }
        }
        final List<PaperCard> out = new ArrayList<>(byName.values());
        out.sort(java.util.Comparator.comparing(PaperCard::getName));
        allPool = out;
        return out;
    }

    @Override
    public int owned(final PaperCard card) {
        return card == null ? 0 : owned.getOrDefault(key(card), 0);
    }

    @Override
    public int ownedExact(final PaperCard card) {
        return card == null ? 0 : exact.getOrDefault(forge.neo.deck.ArtWishes.printKey(card), 0);
    }

    /**
     * Solo los artes que has abierto.
     *
     * <p>Se ordenan por edicion, igual que en el editor general, para que
     * "la de Modern Horizons" se encuentre mirando.
     */
    @Override
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        final List<PaperCard> mine = printings.get(key(card));
        if (mine == null || mine.isEmpty()) {
            // No la tienes: no hay arte que ofrecer, ni siquiera el suyo. Tampoco
            // con "Todas las cartas": la que aun no tienes se apunta con su arte
            // de siempre, y el resto se elige al conseguirla, como cualquier
            // otra. Si no, se apuntaba la de Secret Lair y en cuanto abrieras
            // una normal se jugaba con ella: arte gratis, sin abrirlo ni
            // pagarlo (NeoQuestArt).
            return List.of();
        }
        final List<PaperCard> out = new ArrayList<>(mine);
        out.sort(java.util.Comparator.comparing(PaperCard::getEdition)
                .thenComparing(PaperCard::getCollectorNumber));
        return out;
    }

    /** Cuantas cartas distintas tienes. Para ensenyarlo en la pantalla. */
    public int uniqueCount() {
        return pool.size();
    }

    /** Cuantas cartas tienes en total, contando copias. */
    public int totalCount() {
        int total = 0;
        for (final int n : owned.values()) {
            total += n;
        }
        return total;
    }
}
