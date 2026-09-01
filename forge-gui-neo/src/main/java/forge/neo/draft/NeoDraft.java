package forge.neo.draft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.card.CardEdition;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.gamemodes.limited.BoosterDraft;
import forge.gamemodes.limited.IBoosterDraft;
import forge.gamemodes.limited.LimitedPlayer;
import forge.gamemodes.limited.LimitedPoolType;
import forge.item.PaperCard;
import forge.model.CardBlock;
import forge.model.FModel;

/**
 * Un draft en curso.
 *
 * <p>Es el equivalente de {@link forge.neo.match.NeoFormat} para el draft: la
 * tabla que enchufa lo que ya trae Forge, <b>sin una sola regla propia</b>. El
 * motor tiene el draft entero hecho — 31 clases en
 * {@code forge.gamemodes.limited} — detras de una interfaz de cinco metodos:
 *
 * <pre>
 *   pool = draft.nextChoice()   el sobre que tienes delante
 *   draft.setChoice(carta)      eliges una; los 7 rivales eligen a la vez
 *   draft.hasNextChoice()       queda algo por elegir
 *   draft.postDraftActions()    cierre
 *   draft.getComputerDecks()    con que van a jugar los rivales
 * </pre>
 *
 * <p>Lo que faltaba no era el draft: era la <b>pantalla de picks</b>. Esta clase
 * existe para que esa pantalla no tenga que saber nada de {@code IBoosterDraft},
 * y para que el camino se pueda probar sin abrir una ventana
 * ({@code run.cmd draftcheck}).
 *
 * <p>Cero JavaFX aqui dentro, a proposito.
 */
public final class NeoDraft {

    private final IBoosterDraft draft;
    private CardPool pack;
    private int packSize;

    private NeoDraft(final IBoosterDraft draft) {
        this.draft = draft;
        advance();
    }

    /**
     * Empieza un draft.
     *
     * @param type de donde salen los sobres: {@code Full} son todas las cartas
     *             de Forge, {@code Block} un bloque concreto, {@code Chaos}
     *             sobres de ediciones al azar
     * @return null si el motor no ha podido generar el producto
     */
    public static NeoDraft start(final LimitedPoolType type) {
        final BoosterDraft d = BoosterDraft.createDraft(type);
        return d == null ? null : new NeoDraft(d);
    }

    /**
     * Empieza un draft de UNA expansion: tres sobres de la misma.
     *
     * <p>Con {@code LimitedPoolType.Full} — lo que habia — cada sobre sale de
     * la nada: son cartas de todo Magic metidas en una plantilla generica, asi
     * que en el mismo sobre conviven una carta de 1994 y una de este anyo. Eso
     * no es un draft de verdad: lo que se draftea es un <b>set</b>, con su
     * curva, sus arquetipos y sus comunes buenas.
     *
     * <p>El motor lo tiene hecho y publicado —
     * {@code BoosterDraft.createDraft(tipo, bloque, sobres)} — pero por el
     * camino de siempre ({@code generateProduct} con {@code Block}) lo pregunta
     * con dos {@code SGuiChoose} seguidos, o sea dos dialogos del motor
     * levantados desde donde no toca. Esta sobrecarga es justo la que existe
     * para saltarse eso: se le da el bloque y los tres sobres ya elegidos, y no
     * pregunta nada. Es la misma decision que en {@link NeoSealed}.
     *
     * @param edition de que expansion son los tres sobres
     * @return null si esa expansion no tiene sobre
     */
    public static NeoDraft start(final CardEdition edition) {
        if (edition == null || !edition.hasBoosterTemplate()) {
            return null;
        }
        final String code = edition.getCode();
        final BoosterDraft d = BoosterDraft.createDraft(LimitedPoolType.Block,
                blockFor(edition), new String[] {code, code, code});
        if (d == null) {
            return null;
        }
        final NeoDraft neo = new NeoDraft(d);
        neo.productName = edition.getName() + " (" + code + ")";
        return neo;
    }

    /**
     * El bloque al que pertenece una expansion.
     *
     * <p>Se busca primero entre los que Forge trae escritos
     * ({@code res/blockdata/blocks.txt}): ahi esta la expansion de tierras
     * basicas que le corresponde y cualquier particularidad de sus sobres. Solo
     * si no aparece en ninguno se monta uno de una sola expansion, que es lo
     * que pasa con los sets que no forman bloque.
     *
     * <p>El bloque <b>no puede tener la expansion de tierras a null</b>:
     * {@code CardBlock.equals} la desreferencia sin comprobarla.
     */
    private static CardBlock blockFor(final CardEdition edition) {
        for (final CardBlock b : FModel.getBlocks()) {
            if (b.getCntBoostersDraft() > 0 && b.getLandSet() != null
                    && b.getSets().contains(edition)) {
                return b;
            }
        }
        CardEdition lands = CardEdition.Predicates.hasBasicLands.test(edition)
                ? edition : CardEdition.Predicates.getPreferredArtEditionWithAllBasicLands();
        if (lands == null) {
            lands = edition;
        }
        return new CardBlock(0, edition.getName(), List.of(edition),
                new ArrayList<>(), lands, 3, 6);
    }

    /**
     * Los cubos que trae Forge — {@code res/cube/*.dck} con su ficha en
     * {@code res/draft/*.draft} — ordenados por nombre.
     *
     * <p>Es nuestra propia lectura de esos ficheros: {@code BoosterDraft}
     * tiene el equivalente ({@code loadCustomDrafts}) pero es {@code private},
     * y el camino publico que lo usa ({@code createDraft(Custom)}) pregunta
     * por dialogo cual quieres — ver {@link #startCube}. Aqui solo se listan,
     * asi que basta con {@code CustomLimited.parse}, que si es publico.
     */
    public static List<forge.gamemodes.limited.CustomLimited> cubes() {
        final List<forge.gamemodes.limited.CustomLimited> out = new ArrayList<>();
        final java.io.File folder = new java.io.File(forge.localinstance.properties.ForgeConstants.DRAFT_DIR);
        final String[] files = folder.list();
        if (files == null) {
            return out;
        }
        for (final String name : files) {
            if (!name.endsWith(BoosterDraft.FILE_EXT)) {
                continue;
            }
            try {
                final List<String> lines = forge.util.FileUtil.readFile(
                        forge.localinstance.properties.ForgeConstants.DRAFT_DIR + name);
                out.add(forge.gamemodes.limited.CustomLimited.parse(lines, FModel.getDecks().getCubes()));
            } catch (final RuntimeException e) {
                System.err.println("[neo] no se ha podido leer el cubo " + name + ": " + e);
            }
        }
        out.sort(java.util.Comparator.comparing(forge.gamemodes.limited.CustomLimited::getName,
                String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /**
     * Empieza un draft de UN CUBO concreto, sin preguntar nada.
     *
     * <p>El camino de siempre —{@code BoosterDraft.createDraft(Custom)}— hace
     * exactamente lo que hace falta (lee el cubo, monta los sobres, reparte
     * entre ocho jugadores), pero por dentro pregunta "qué cubo" con un
     * {@code SGuiChoose.oneOrNone}, o sea un diálogo del motor levantado
     * desde donde no toca — la elección ya se hizo en nuestra pantalla. Se
     * <b>contesta esa pregunta antes de que se haga</b>: un elegidor de
     * usar-y-tirar que sólo sabe devolver ESTE cubo, instalado justo para
     * esta llamada y quitado en el {@code finally} pase lo que pase — si se
     * quedara puesto, la siguiente pregunta de fuera de partida (la aventura,
     * por ejemplo) se contestaría sola sin que nadie lo supiera.
     *
     * @return null si el cubo no existe o el motor no ha podido generar el
     *         producto
     */
    public static NeoDraft startCube(final String cubeName) {
        forge.gamemodes.limited.CustomLimited match = null;
        for (final forge.gamemodes.limited.CustomLimited c : cubes()) {
            if (c.getName().equals(cubeName)) {
                match = c;
                break;
            }
        }
        if (match == null) {
            return null;
        }
        final forge.gamemodes.limited.CustomLimited chosen = match;
        final forge.neo.platform.NeoGuiBase.Chooser previous = forge.neo.platform.NeoGuiBase.getChooser();
        forge.neo.platform.NeoGuiBase.setChooser(new forge.neo.platform.NeoGuiBase.Chooser() {
            @Override
            public <T> List<T> choose(final String message, final int min, final int max,
                                      final List<T> options, final List<T> preselected,
                                      final java.util.function.Function<T, String> display) {
                return List.of((T) chosen);
            }
        });
        try {
            final BoosterDraft d = BoosterDraft.createDraft(LimitedPoolType.Custom);
            if (d == null) {
                return null;
            }
            final NeoDraft neo = new NeoDraft(d);
            neo.productName = chosen.getName();
            return neo;
        } finally {
            forge.neo.platform.NeoGuiBase.setChooser(previous);
        }
    }

    /**
     * De que se estan abriendo los sobres, para poder decirlo en pantalla.
     *
     * <p>Se apunta aqui y no se le pregunta al motor: la sobrecarga de
     * {@code createDraft} que recibe el bloque ya hecho <b>no rellena</b> su
     * {@code productName}, asi que preguntarselo devolveria null.
     */
    public String productName() {
        return productName;
    }

    private String productName = "";

    /** El sobre que hay delante ahora mismo, o null si el draft ha terminado. */
    public CardPool currentPack() {
        return pack;
    }

    /** Las cartas del sobre, una por copia y en orden estable. */
    public List<PaperCard> currentCards() {
        final List<PaperCard> out = new ArrayList<>();
        if (pack != null) {
            for (final Map.Entry<PaperCard, Integer> e : pack) {
                for (int i = 0; i < e.getValue(); i++) {
                    out.add(e.getKey());
                }
            }
        }
        return out;
    }

    /** Ronda de sobres: 1, 2 o 3 en un draft normal. */
    public int round() {
        return draft.getRound();
    }

    /** Que numero de pick es este dentro del sobre, empezando en 1. */
    public int pickNumber() {
        return packSize <= 0 ? 1 : packSize - (pack == null ? 0 : pack.countAll()) + 1;
    }

    /** Cuantas cartas quedan en el sobre. */
    public int cardsLeft() {
        return pack == null ? 0 : pack.countAll();
    }

    public boolean isDone() {
        return pack == null;
    }

    /** Lo que llevas cogido, en el orden en que lo cogiste. */
    public List<PaperCard> picked() {
        return new ArrayList<>(picked);
    }

    private final List<PaperCard> picked = new ArrayList<>();

    /**
     * Coge una carta del sobre.
     *
     * <p>Las dos comprobaciones raras del principio no son nuestras: son cartas
     * que cambian el propio draft ("Lore Seeker" y compania te hacen saltarte un
     * pick; el Archdemon of Paliano te obliga a coger la primera). El motor las
     * resuelve; aqui solo hay que preguntarle, igual que hace la GUI vieja.
     *
     * @return la carta que de verdad se ha cogido, que puede no ser la pedida
     */
    public PaperCard pick(final PaperCard wanted) {
        if (pack == null || wanted == null) {
            return null;
        }
        final LimitedPlayer me = draft.getHumanPlayer();
        if (me.shouldSkipThisPick()) {
            advance();
            return null;
        }
        PaperCard chosen = wanted;
        if (me.hasArchdemonCurse()) {
            chosen = me.pickFromArchdemonCurse(me.nextChoice());
        }
        draft.setChoice(chosen);
        picked.add(chosen);
        advance();
        return chosen;
    }

    /** Pasar sin coger nada. Alguna carta del propio draft lo provoca. */
    public void skip() {
        if (pack != null) {
            draft.skipChoice();
            advance();
        }
    }

    /** Pide el siguiente sobre. Si no queda ninguno, cierra el draft. */
    private void advance() {
        final CardPool next = draft.hasNextChoice() ? draft.nextChoice() : null;
        if (next == null || next.isEmpty()) {
            pack = null;
            return;
        }
        // Sobre NUEVO, que no es lo mismo que "otro objeto": el motor devuelve
        // un CardPool distinto en cada llamada, asi que comparar referencias
        // reiniciaba la cuenta en cada pick y el contador se quedaba en "pick 1"
        // los quince picks. Dentro de un sobre el numero de cartas solo puede
        // BAJAR (te lo pasa el de al lado con una menos); si sube, es otro sobre.
        final int size = next.countAll();
        if (pack == null || size > pack.countAll()) {
            packSize = size;
        }
        pack = next;
    }

    /**
     * Cierra el draft y lo guarda con las cartas de los ocho jugadores.
     *
     * <p>El resultado NO es un mazo, es un {@link DeckGroup}: tu pool y los
     * siete mazos que los rivales han draftado a la vez que tu. Eso es lo que
     * hace que jugar un draft tenga sentido — te enfrentas a lo que se llevaron
     * ellos, no a un preconstruido cualquiera.
     *
     * <p>Tus cartas llegan a la <b>banda</b> ({@code DeckSection.Sideboard}),
     * que es como Forge representa "el pool del que aun tienes que montar el
     * mazo". El deck builder es el que decide despues cuales juegas.
     */
    public DeckGroup save(final String name) {
        draft.postDraftActions();

        final Deck[] ai = draft.getComputerDecks();
        final LimitedPlayer[] rivals = draft.getOpposingPlayers();
        for (int i = 0; i < ai.length && i < rivals.length; i++) {
            ai[i].setDraftNotes(rivals[i].getSerializedDraftNotes());
        }

        final LimitedPlayer me = draft.getHumanPlayer();
        final Deck mine = (Deck) me.getDeck().copyTo(name);
        for (final PaperCard removed : me.getRemovedFromCardPool()) {
            // Cartas que otra carta del draft te ha quitado del pool.
            mine.get(DeckSection.Sideboard).remove(removed);
        }
        mine.setDraftNotes(me.getSerializedDraftNotes());
        buildMainDeck(mine);

        final DeckGroup group = new DeckGroup(name);
        group.setHumanDeck(mine);
        group.addAiDecks(ai);

        FModel.getDecks().getDraft().add(group);
        return group;
    }

    /**
     * Monta un mazo jugable de 40 cartas con el pool draftado.
     *
     * <p>Un draft guardado deja el pool entero en la banda y el mazo VACIO: tal
     * cual, no se puede jugar. Montarlo es exactamente el trabajo que hace el
     * motor para los siete rivales, asi que se le pide a el en vez de inventar
     * nada.
     *
     * <p>Se usa {@code SealedDeckBuilder} y no {@code LimitedDeckBuilder} por
     * un detalle que cuesta encontrar: el segundo necesita que le DIGAS los dos
     * colores (los rivales los van acumulando pick a pick), y su clase de
     * colores no es publica fuera del paquete. El de sellado los <b>elige el
     * solo</b> a partir del mejor tercio del pool, que es justo lo que hace
     * falta aqui. Sin eso revienta con "Add Lands to empty deck list!".
     *
     * <p>El pool se queda intacto en la banda: cambiar el mazo a mano es cosa
     * del deck builder, y para eso tiene que seguir estando entero.
     */
    private static void buildMainDeck(final Deck deck) {
        final List<PaperCard> pool = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Sideboard)) {
            for (int i = 0; i < e.getValue(); i++) {
                pool.add(e.getKey());
            }
        }
        if (pool.isEmpty()) {
            return;
        }
        try {
            final Deck built = new forge.gamemodes.limited.SealedDeckBuilder(pool).buildDeck();
            deck.getMain().clear();
            deck.getMain().addAll(built.getMain());
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido montar el mazo del draft: " + e);
        }
    }

    /**
     * Lo que se llevo cada rival del draft.
     *
     * <p>Existe para poder <b>comprobarlo</b>: la pregunta razonable de quien
     * juega un draft es si los siete rivales llevan de verdad lo que draftearon
     * a su lado o un mazo inventado. Sus mazos los monta el motor
     * ({@code LimitedPlayerAI.buildDeck} → {@code BoosterDeckBuilder}) a partir
     * de este mismo pool y de los colores que fueron acumulando pick a pick, y
     * {@code DraftCheck} lo verifica carta por carta.
     *
     * @return una lista por rival, en el mismo orden que
     *         {@code getComputerDecks()}
     */
    public List<CardPool> rivalPools() {
        final List<CardPool> out = new ArrayList<>();
        for (final LimitedPlayer p : draft.getOpposingPlayers()) {
            final Deck theirs = p.getDeck();
            out.add(theirs == null ? new CardPool() : theirs.get(DeckSection.Sideboard));
        }
        return out;
    }

    /** Los drafts ya guardados. */
    public static Iterable<DeckGroup> saved() {
        return FModel.getDecks().getDraft();
    }
}
