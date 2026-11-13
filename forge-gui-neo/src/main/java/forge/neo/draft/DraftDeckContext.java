package forge.neo.draft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import forge.card.CardEdition;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.deck.DeckContext;
import forge.util.storage.IStorage;

/**
 * Montar el mazo <b>con lo que has draftado</b>.
 *
 * <p>Es el tercer contexto del mismo editor — el general monta con todo Magic,
 * el de la aventura con tu coleccion, y este con las 45 cartas que has pasado
 * por delante. Todo lo demas (buscador, curva, columnas por tipo, click
 * derecho, validacion) es el mismo codigo. Ver {@link DeckContext}.
 *
 * <p>Existe porque un draft sin editor no es un draft: el mazo lo monta el
 * motor solo, y el motor elige DOS colores. Reportado jugando: <i>"me he hecho
 * un mono negro, pero al final del sobre ya no quedaba negro y he cogido
 * cartas rojas y verdes; ahora el mazo me las mete, y con ellas tierras rojas
 * que no quiero"</i>. Un mazo que no se puede tocar convierte 45 decisiones en
 * ninguna.
 *
 * <p><b>Donde vive el pool</b>: mazo y banda son <b>disjuntos</b>, y el pool
 * es la suma de los dos. Hasta el 23-09-2026 el draft era distinto (banda =
 * pool entero, mazo = copia) y eso hacia crecer el pool al quitar cartas;
 * los drafts de antes se pasan al formato nuevo al abrirlos
 * ({@code DraftRun.splitIfNeeded}).
 *
 * <p>Las <b>tierras basicas</b> no salen de ningun sobre: en limitado se ponen
 * las que hagan falta. Van al catalogo aparte y sin techo.
 */
public final class DraftDeckContext implements DeckContext {

    /** Como se llama el evento: el grupo de mazos y el mazo humano. */
    private final String eventName;
    /** Donde vive el grupo (los drafts, los sellados, o los de la Quest). */
    private final IStorage<DeckGroup> groups;
    private final boolean sealedPool;
    /** Lo que hay que hacer despues de guardar, o null. La Quest se guarda entera. */
    private final Runnable afterSave;

    /** Cuantas copias tienes de cada impresion. Las basicas no estan aqui. */
    private final Map<PaperCard, Integer> owned = new HashMap<>();

    /**
     * Lo mismo, pero sumado por NOMBRE.
     *
     * <p>Es lo que de verdad pregunta {@link #owned(PaperCard)}: el techo de
     * copias del draft es "cuantas tienes de esta carta", no "cuantas tienes
     * de esta impresion exacta". Sin esto, {@code SealedDeckBuilder} devolviendo
     * OTRA impresion de la misma carta al montar el mazo automatico dejaba el
     * pool sin reconocerla — la misma trampa que ya evita
     * {@code QuestDeckContext}, y aqui no se habia aplicado.
     */
    private final Map<String, Integer> ownedByName = new HashMap<>();

    /** El catalogo: una entrada por impresion del pool, mas las cinco basicas. */
    private final List<PaperCard> pool = new ArrayList<>();

    /** Las impresiones que tienes de cada carta, por nombre. */
    private final Map<String, List<PaperCard>> printings = new HashMap<>();

    public DraftDeckContext(final DraftRun run) {
        this(run == null ? null : run.getName(), run == null ? null : run.getKind().storage(),
                run != null && run.getKind() == DraftRun.Kind.SEALED, null);
    }

    /**
     * El mismo editor para un grupo que no es un {@link DraftRun}: el del
     * torneo de draft de la Quest, que vive en {@code QuestController.getDraftDecks()}
     * y se guarda con la Quest ({@code afterSave}).
     */
    public DraftDeckContext(final String eventName, final IStorage<DeckGroup> groups,
                            final boolean sealed, final Runnable afterSave) {
        this.eventName = eventName;
        this.groups = groups;
        this.sealedPool = sealed;
        this.afterSave = afterSave;
        final Deck deck = humanDeck();
        if (deck == null) {
            return;
        }

        count(deck.get(DeckSection.Sideboard));
        count(deck.getMain());
        commanderDraft = CommanderDraft.is(deck);
        if (commanderDraft) {
            // El comandante elegido sigue contando como tuyo, y los gratis
            // (el de la expansion, The Prismatic Piper, Faceless One) se pueden
            // poner de comandante sin haberlos draftado, como en Forge.
            count(deck.get(DeckSection.Commander));
            for (final PaperCard free : CommanderDraft.freeCommanders(deck)) {
                if (ownedByName.getOrDefault(normalizedName(free.getName()), 0) == 0) {
                    freeOnly.add(normalizedName(free.getName()));
                    owned.put(free, 1);
                    ownedByName.put(normalizedName(free.getName()), 1);
                    printings.computeIfAbsent(free.getName(), k -> new ArrayList<>()).add(free);
                }
            }
        }

        pool.addAll(owned.keySet());
        pool.sort(java.util.Comparator.comparing(PaperCard::getName)
                .thenComparing(PaperCard::getEdition));
        pool.addAll(basics(deck));
    }

    /** Si es un Commander Draft (ver {@link CommanderDraft}). */
    private boolean commanderDraft;

    /** Los comandantes gratis que NO has draftado: no estan en el pool. */
    private final java.util.Set<String> freeOnly = new java.util.HashSet<>();

    @Override
    public boolean isFreeCommander(final PaperCard card) {
        return card != null && freeOnly.contains(normalizedName(card.getName()));
    }

    public boolean isCommanderDraft() {
        return commanderDraft;
    }

    /**
     * En un Commander Draft el pool sin usar sigue en la banda y no se juega:
     * la legalidad se mira sin el ({@link CommanderDraft#problem}).
     */
    @Override
    public String conformanceProblem(final Deck deck) {
        if (commanderDraft && deck != null) {
            return CommanderDraft.problem(deck);
        }
        return DeckContext.super.conformanceProblem(deck);
    }

    private void count(final CardPool section) {
        if (section == null) {
            return;
        }
        for (final Map.Entry<PaperCard, Integer> e : section) {
            final PaperCard card = e.getKey();
            if (isBasic(card)) {
                continue;
            }
            owned.merge(card, e.getValue(), Integer::sum);
            ownedByName.merge(normalizedName(card.getName()), e.getValue(), Integer::sum);
            final List<PaperCard> arts =
                    printings.computeIfAbsent(card.getName(), k -> new ArrayList<>());
            if (!arts.contains(card)) {
                arts.add(card);
            }
        }
    }

    /** Visible en el paquete: lo usa tambien {@link NetPoolContext}. */
    static boolean isBasic(final PaperCard card) {
        return card != null && card.getRules() != null
                && card.getRules().getType().isBasicLand();
    }

    /** Igual que {@code DeckEditor.normalized}: junta aventura y hechizo, o carta de ambientacion. */
    static String normalizedName(final String name) {
        return name == null ? "" : forge.StaticData.instance().getCommonCards().getNormalizedName(name);
    }

    /**
     * Las cinco basicas, de una edicion que las tenga todas.
     *
     * <p>Se prefiere una edicion que ya este en el pool: si los sobres eran de
     * Bloomburrow, las llanuras del mazo tambien lo son y la mesa no queda
     * hecha un mosaico. Si ninguna las trae, la que Forge prefiera por arte.
     */
    static List<PaperCard> basics(final Deck deck) {
        final List<PaperCard> out = new ArrayList<>();
        final Map<String, PaperCard> already = basicsInDeck(deck);
        final String code = landSet(deck);
        for (final String name : forge.card.MagicColor.Constant.BASIC_LANDS) {
            // La que YA esta en el mazo manda sobre cualquier heuristica: si no,
            // el catalogo ofreceria otra impresion del mismo pantano y anyadir
            // uno partiria la lista en dos entradas con artes distintos.
            PaperCard card = already.get(name);
            if (card == null && code != null) {
                card = FModel.getMagicDb().getCommonCards().getCard(name, code);
            }
            if (card == null) {
                card = FModel.getMagicDb().getCommonCards().getCard(name);
            }
            if (card != null) {
                out.add(card);
            }
        }
        return out;
    }

    /** Que impresion de cada basica lleva ya el mazo. */
    private static Map<String, PaperCard> basicsInDeck(final Deck deck) {
        final Map<String, PaperCard> out = new LinkedHashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (isBasic(e.getKey())) {
                out.putIfAbsent(e.getKey().getName(), e.getKey());
            }
        }
        return out;
    }

    /** La edicion con basicas mas repetida en el pool, o null. */
    private static String landSet(final Deck deck) {
        final Map<String, Integer> seen = new LinkedHashMap<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getAllCardsInASinglePool()) {
            seen.merge(e.getKey().getEdition(), e.getValue(), Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (final Map.Entry<String, Integer> e : seen.entrySet()) {
            final CardEdition ed = FModel.getMagicDb().getEditions().get(e.getKey());
            if (ed != null && CardEdition.Predicates.hasBasicLands.test(ed)
                    && e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        if (best != null) {
            return best;
        }
        final CardEdition fallback =
                CardEdition.Predicates.getPreferredArtEditionWithAllBasicLands();
        return fallback == null ? null : fallback.getCode();
    }

    // ---------------------------------------------------------------
    // Lo que el editor pregunta

    private boolean sealed() {
        return sealedPool;
    }

    /** El grupo del evento, o null. */
    private DeckGroup findGroup() {
        if (eventName == null || groups == null) {
            return null;
        }
        for (final DeckGroup g : groups) {
            if (g.getName().equals(eventName)) {
                return g;
            }
        }
        return null;
    }

    private Deck humanDeck() {
        final DeckGroup g = findGroup();
        return g == null ? null : g.getHumanDeck();
    }

    @Override
    public String getLabel() {
        if (commanderDraft) {
            return forge.neo.NeoText.get("draft.commanderDraft");
        }
        return (sealed() ? forge.neo.match.NeoFormat.SELLADO
                : forge.neo.match.NeoFormat.DRAFT).getLabel();
    }

    /**
     * Limitado: minimo 40 cartas y sin limite de copias.
     *
     * <p>Las copias no las limitan las reglas sino el pool — puedes llevar las
     * nueve islas que hayas abierto. Eso lo contesta {@link #owned(PaperCard)}.
     */
    @Override
    public DeckFormat deckFormat() {
        if (commanderDraft) {
            // 60 cartas contando el comandante, zona de mando e identidad.
            return forge.game.GameType.CommanderDraft.getDeckFormat();
        }
        return (sealed() ? forge.game.GameType.Sealed
                : forge.game.GameType.Draft).getDeckFormat();
    }

    @Override
    public List<PaperCard> pool() {
        return pool;
    }

    @Override
    public int owned(final PaperCard card) {
        if (card == null) {
            return 0;
        }
        if (isBasic(card)) {
            return Integer.MAX_VALUE;
        }
        return ownedByName.getOrDefault(normalizedName(card.getName()), 0);
    }

    /**
     * Solo los artes que han salido en tus sobres.
     *
     * <p>Igual que en la aventura: cambiar una carta del pool por otra
     * impresion seria cambiarla por una que no tienes, y el editor la marcaria
     * ilegal acto seguido. Las basicas si admiten cualquier arte, porque no
     * salen del pool.
     */
    @Override
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        if (isBasic(card)) {
            return null;
        }
        final List<PaperCard> mine = printings.get(card.getName());
        return mine == null || mine.isEmpty() ? List.of() : new ArrayList<>(mine);
    }

    /** Esa columna no es un catalogo ni una coleccion: es lo que draftaste. */
    /**
     * Sí: en limitado el pool ES el mazo más la banda.
     *
     * <p>Lo dice el javadoc de arriba y lo garantiza quien escribe cada mazo.
     * Con esto, el editor mueve las cartas entre las dos mitades en vez de
     * crearlas y destruirlas.
     */
    @Override
    public boolean poolInSideboard() {
        return true;
    }

    @Override
    public boolean canAutoBuild() {
        // En un Commander Draft lo monta autoBuildDeck (el constructor de
        // Commander Draft de Forge); en el resto, el SealedDeckBuilder de 40.
        return true;
    }

    /**
     * "Montar solo" en un COMMANDER DRAFT (Ana, 13-11-2026: «lo normal es que
     * se monten el mazo, pero si no saben, que lo haga el build, un mazo que
     * puedan jugar»). Forge no lo ofrece al humano, pero es el mismo
     * constructor que monta los mazos de sus siete rivales
     * ({@code CommanderLimitedPlayerAI} → {@code CommanderDraftDeckBuilder}):
     * con el comandante puesto, 59 cartas de su identidad (24 tierras); sin el,
     * lo elige de tu pool, o el gratis de la expansion.
     */
    /**
     * El comandante que mejor encaja con lo draftado (o uno gratis), o null.
     * La cuenta es la de Forge ({@code CommanderDraftDeckBuilder.scoreCommanderFit}):
     * la mitad de la nota de cada carta del pool que cabe en su identidad, mas el
     * doble de la suya. Pero esa cuenta premia siempre la identidad mas ancha (en
     * cinco colores cabe todo) y un pool de draft no da para la mana de cinco:
     * se miran primero los de hasta tres colores, y los de cuatro o cinco solo si
     * no hay otros.
     */
    private PaperCard bestCommander(final List<PaperCard> drafted, final Deck deck) {
        final List<PaperCard> candidates = new ArrayList<>();
        for (final PaperCard c : drafted) {
            if (c.getRules() != null && c.getRules().canBeCommander() && !c.getRules().canBeBackground()
                    && !candidates.contains(c)) {
                candidates.add(c);
            }
        }
        candidates.addAll(CommanderDraft.freeCommanders(deck));
        final List<PaperCard> narrow = new ArrayList<>();
        for (final PaperCard c : candidates) {
            if (c.getRules().getColorIdentity().countColors() <= 3) {
                narrow.add(c);
            }
        }
        final List<PaperCard> look = narrow.isEmpty() ? candidates : narrow;
        PaperCard best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (final PaperCard c : look) {
            final forge.card.ColorSet identity = c.getRules().getColorIdentity();
            double score = 0;
            for (final PaperCard other : drafted) {
                if (other != c && other.getRules() != null
                        && other.getRules().getColorIdentity().hasNoColorsExcept(identity)) {
                    score += Math.max(0, forge.gamemodes.limited.CardRanker.getRawScore(other)) / 2.0;
                }
            }
            score += Math.max(0, forge.gamemodes.limited.CardRanker.getRawScore(c)) * 2.0;
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    @Override
    public Deck autoBuildDeck(final List<PaperCard> cards, final List<PaperCard> commanders) {
        if (!commanderDraft) {
            return null;
        }
        final Deck deck = humanDeck();
        final String lands = deck == null ? null : landSet(deck);
        final List<PaperCard> drafted = new ArrayList<>(cards);
        try {
            final forge.gamemodes.limited.CommanderDraftDeckBuilder builder;
            if (commanders != null && !commanders.isEmpty()) {
                for (final PaperCard c : commanders) {
                    drafted.remove(c);
                }
                builder = new forge.gamemodes.limited.CommanderDraftDeckBuilder(drafted,
                        new forge.gamemodes.limited.CommanderDeckColors(), commanders.get(0),
                        commanders.size() > 1 ? commanders.get(1) : null);
            } else {
                // Sin comandante: se elige aqui y se le pasa ya elegido. El
                // constructor sabe elegirlo solo, pero ese camino (el que los
                // rivales no usan nunca: a ellos se les da elegido) revienta con
                // "Add Lands to empty deck list!". La idea es la de Forge
                // (scoreCommanderFit): cuantas cartas del pool caben en su
                // identidad, y su nota de draft para desempatar.
                final PaperCard pick = bestCommander(drafted, deck);
                if (pick == null) {
                    return null;
                }
                drafted.remove(pick);
                builder = new forge.gamemodes.limited.CommanderDraftDeckBuilder(drafted,
                        new forge.gamemodes.limited.CommanderDeckColors(), pick, null);
            }
            Deck built = builder.buildDeck(lands);
            if (built == null || !built.has(DeckSection.Commander) || built.get(DeckSection.Commander).isEmpty()) {
                return null;
            }
            // El constructor empareja dos comandantes de un color o menos (la
            // regla de Commander Draft de su editor), pero la legalidad del
            // mazo (DeckFormat) solo acepta companeros de verdad: con una
            // pareja asi el mazo no se podria jugar. Se rehace con uno solo.
            final List<PaperCard> cmds = built.get(DeckSection.Commander).toFlatList();
            if (cmds.size() > 1 && !cmds.get(0).getRules().canBePartnerCommanders(cmds.get(1).getRules())) {
                final List<PaperCard> again = new ArrayList<>(cards);
                again.remove(cmds.get(0));
                // Sin companero de verdad: el constructor, si no le das uno,
                // lo busca solo (selectPartner); aqui se le dice que no.
                built = new forge.gamemodes.limited.CommanderDraftDeckBuilder(again,
                        new forge.gamemodes.limited.CommanderDeckColors(), cmds.get(0), null) {
                    @Override
                    public void selectPartner() {
                        // uno solo
                    }
                }.buildDeck(lands);
            }
            return built;
        } catch (final RuntimeException e) {
            System.err.println("[neo] montar el Commander Draft ha fallado: " + e);
            return null;
        }
    }

    @Override
    public CardPool autoBuild(final List<PaperCard> cards) {
        final Deck deck = humanDeck();
        return LimitedAutoBuild.build(cards, deck == null ? null : landSet(deck));
    }

    @Override
    public String catalogueLabel() {
        return forge.neo.NeoText.get(sealed() ? "deck.poolSealed" : "deck.poolDraft");
    }

    /**
     * El nombre no se toca.
     *
     * <p>El mazo de un evento no es un mazo suelto: es la mitad humana de un
     * {@link DeckGroup} que lleva ademas los siete rivales, y Forge da por
     * hecho que se llama igual que el grupo. Y el marcador — las victorias y
     * las derrotas — vive en {@code neo.properties} colgando de ese mismo
     * nombre. Renombrar aqui perderia la cuenta sin decirlo.
     */
    @Override
    public boolean canRename() {
        return false;
    }

    @Override
    public IStorage<Deck> storage() {
        return storage;
    }

    private final IStorage<Deck> storage = new GroupStorage();

    /**
     * Guardar es escribir dentro del evento.
     *
     * <p>El editor guarda con {@code storage().add(deck)} y no tiene por que
     * saber que aqui no hay ninguna carpeta de mazos: lo que hay es un
     * {@link DeckGroup} con tu mazo y los siete de los rivales, y se reescribe
     * entero. Por eso esto es una fachada de {@link IStorage} y no un almacen
     * de verdad: dentro solo hay un mazo, el tuyo.
     */
    private final class GroupStorage implements IStorage<Deck> {

        private DeckGroup group() {
            return findGroup();
        }

        @Override
        public void add(final Deck item) {
            final DeckGroup g = group();
            if (g == null || item == null) {
                return;
            }
            // El mazo humano tiene que llamarse como el grupo: es lo que da por
            // hecho DeckGroupSerializer al releerlo del disco.
            g.setHumanDeck(item.getName().equals(g.getName())
                    ? item : new Deck(item, g.getName()));
            groups.add(g);
            if (afterSave != null) {
                afterSave.run();
            }
        }

        @Override
        public void add(final String name, final Deck item) {
            add(item);
        }

        @Override
        public Deck get(final String name) {
            final DeckGroup g = group();
            return g == null ? null : g.getHumanDeck();
        }

        @Override
        public boolean contains(final String name) {
            return eventName != null && eventName.equals(name) && group() != null;
        }

        @Override
        public Collection<String> getItemNames() {
            return group() == null ? List.of() : List.of(eventName);
        }

        @Override
        public int size() {
            return getItemNames().size();
        }

        @Override
        public Deck find(final Predicate<Deck> condition) {
            final Deck mine = get(null);
            return mine != null && condition.test(mine) ? mine : null;
        }

        @Override
        public java.util.Iterator<Deck> iterator() {
            final Deck mine = get(null);
            return (mine == null ? List.<Deck>of() : List.of(mine)).iterator();
        }

        @Override
        public Stream<Deck> stream() {
            final Deck mine = get(null);
            return mine == null ? Stream.empty() : Stream.of(mine);
        }

        /**
         * Borrar el mazo seria borrar el evento entero, y eso no se decide
         * desde el editor: se hace desde la papelera de la pantalla de mazos,
         * que avisa de que se van tambien los siete rivales.
         */
        @Override
        public void delete(final String deckName) {
        }

        @Override
        public String getName() {
            return eventName == null ? "" : eventName;
        }

        @Override
        public String getFullPath() {
            return groups == null ? "" : groups.getFullPath();
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
            return this;
        }
    }
}
