package forge.neo.quest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import forge.card.CardEdition;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.MyRandom;

/**
 * <b>El Coliseo</b> de la Quest de Commander (13-10-2026, Ana): una partida a
 * CUATRO — tu mazo de la Quest contra tres rivales con mazos buenos —, cara de
 * entrar y con premio gordo: creditos y un sobre de colector.
 *
 * <p>Las reglas son pocas y todas nuestras (Forge no tiene nada parecido), asi
 * que viven aqui, sin JavaFX y aptas para Android:
 *
 * <ul>
 *   <li><b>Solo en una Quest de Commander</b>: se juega con tu mazo de la
 *       Quest, y en una de Estandar es de 60 y sin comandante.</li>
 *   <li><b>La dificultad sigue a la Quest</b>: los rivales son del nivel
 *       <b>siguiente</b> al tuyo ({@link #rivalTier}) — un reto, no un muro —,
 *       con sus mazos montados como los de los duelos
 *       ({@link NeoCommanderDuels#deckFor}: la matriz de mazos reales con el
 *       techo de rareza y de bracket del nivel). La entrada sube con el nivel
 *       ({@link #fee}) y el premio en creditos es el doble.</li>
 *   <li><b>Asequible</b> (Discord, 28-10-2026: es la forma de jugar Commander a
 *       cuatro dentro de la Quest, y costaba demasiado): la entrada bajo a un
 *       40 % y el premio con ella, y el sobre es de colector solo en dificil y
 *       experto ({@link #collectorPrize}); en facil y medio, uno normal de la
 *       misma expansion. Ganando una de cada cuatro sigue sin salir a cuenta,
 *       asi que no es una forma de sacar sobres.</li>
 *   <li><b>Ganar cuenta como una victoria de la Quest</b> (sube el rango y
 *       acerca desafios y torneos). Perder solo cuesta la entrada.</li>
 *   <li><b>Los rivales se quedan</b> hasta que juegues ({@link #lineup}), y
 *       guardados junto a la Quest: si cambiaran al salir, al cambiar de Quest
 *       o al reiniciar el juego, se podria elegir rival (y sobre) gratis.</li>
 *   <li><b>La entrada se liquida UNA vez</b>: {@link #pay} la deja pendiente,
 *       apuntada a la Quest que pago, y la cierra lo primero que llegue de
 *       {@link #win}, {@link #lose} o {@link #refund}. Hay varios caminos que
 *       pueden llegar a la vez (la partida que vuelve, la mesa que se abandona
 *       a la fuerza) y ninguno puede cobrar o devolver dos veces.</li>
 * </ul>
 */
public final class NeoColosseum {

    private NeoColosseum() {
    }

    /** Cuantos rivales: Commander a cuatro. */
    public static final int RIVALS = 3;

    /** Los rivales de ahora, de que Quest y nivel son, y el sobre del premio. */
    private static List<PaperCard> lineup;
    private static String lineupKey;
    private static CardEdition prizeEdition;

    /** La entrada pagada y aun sin liquidar, o null. */
    private static Entry pending;

    private static final class Entry {
        final QuestController quest;
        final String questName;
        final int fee;

        Entry(final QuestController quest, final String questName, final int fee) {
            this.quest = quest;
            this.questName = questName;
            this.fee = fee;
        }
    }

    /** Si hay Coliseo en esta Quest: solo en las de Commander. */
    public static boolean available() {
        return NeoQuest.isActive() && NeoQuest.modalidad() == NeoQuest.Modalidad.COMMANDER;
    }

    /**
     * La entrada: sube con el nivel de la Quest. Era 600/1000/1600/2400 hasta
     * el 28-10-2026; ver la cabecera.
     */
    public static int fee() {
        switch (NeoQuest.tier()) {
            case EXPERT:
                return 1000;
            case HARD:
                return 650;
            case MEDIUM:
                return 400;
            default:
                return 250;
        }
    }

    /** Si el sobre del premio es de colector (dificil y experto) o uno normal. */
    public static boolean collectorPrize() {
        final QuestEventDifficulty t = NeoQuest.tier();
        return t == QuestEventDifficulty.HARD || t == QuestEventDifficulty.EXPERT;
    }

    /**
     * La clave del texto del premio con sobre, "{0} creditos y un sobre (de
     * colector) de {1}": la usan las dos interfaces.
     */
    public static String prizeTextKey() {
        return collectorPrize() ? "colosseum.prize" : "colosseum.prizeBooster";
    }

    /** Los creditos del premio (ademas del sobre de colector). */
    public static int prizeCredits() {
        return fee() * 2;
    }

    /** El nivel de los rivales: uno por encima del tuyo, como mucho experto. */
    public static QuestEventDifficulty rivalTier() {
        switch (NeoQuest.tier()) {
            case EASY:
                return QuestEventDifficulty.MEDIUM;
            case MEDIUM:
                return QuestEventDifficulty.HARD;
            default:
                return QuestEventDifficulty.EXPERT;
        }
    }

    public static boolean canAfford() {
        return NeoQuest.isActive() && NeoQuest.credits() >= fee();
    }

    /** Por que no se puede entrar con tu mazo de ahora, o null. */
    public static String deckProblem() {
        return NeoQuest.problemWith(NeoQuest.currentDeck());
    }

    // ---------------------------------------------------------------
    // Los rivales

    /**
     * Los tres comandantes rivales de ahora. Salen de los que la matriz de
     * mazos reales conoce (si no, el mazo saldria al azar) y ninguno es el tuyo.
     * Se quedan hasta la siguiente partida, o hasta que cambies de nivel.
     */
    public static synchronized List<PaperCard> lineup() {
        final String key = NeoQuest.name() + "#" + rivalTier();
        if (lineup == null || !key.equals(lineupKey)) {
            if (!load(key)) {
                lineup = draw();
                prizeEdition = drawEdition();
                lineupKey = key;
                save();
            }
        }
        return lineup;
    }

    /**
     * De que expansion es el sobre de colector del premio, o null si ninguna
     * de las de tu Quest lo tiene (entonces el premio son solo los creditos).
     * Se sortea con los rivales y se queda con ellos: se ve ANTES de pagar.
     */
    public static synchronized CardEdition prizeEdition() {
        lineup();
        return prizeEdition;
    }

    private static List<PaperCard> draw() {
        final List<PaperCard> pool = new ArrayList<>();
        final String mine = myCommanderName();
        for (final PaperCard c : forge.neo.ascent.AscentSeedDeck.commanderPool()) {
            if (forge.neo.ascent.AscentSynergy.knows(c) && !c.getName().equals(mine)
                    && NeoCommanderDuels.fitsTable(c)) {
                pool.add(c);
            }
        }
        Collections.shuffle(pool, MyRandom.getRandom());
        return Collections.unmodifiableList(new ArrayList<>(pool.subList(0, Math.min(RIVALS, pool.size()))));
    }

    private static CardEdition drawEdition() {
        final List<CardEdition> eds = NeoQuestShop.collectorEditions();
        return eds.isEmpty() ? null : eds.get(MyRandom.getRandom().nextInt(eds.size()));
    }

    private static String myCommanderName() {
        final Deck d = NeoQuest.currentDeck();
        if (d == null || !d.has(forge.deck.DeckSection.Commander)) {
            return "";
        }
        for (final PaperCard c : d.get(forge.deck.DeckSection.Commander).toFlatList()) {
            return c.getName();
        }
        return "";
    }

    /**
     * Donde se guardan, junto a la partida de la Quest: {@code <nombre>.coliseo}
     * en la carpeta de las Quests (Forge solo lista los {@code .dat}). Tres
     * lineas: la clave (Quest y nivel), los comandantes y el codigo del sobre.
     */
    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".coliseo");
    }

    private static void save() {
        final StringBuilder names = new StringBuilder();
        for (final PaperCard c : lineup) {
            names.append(names.length() == 0 ? "" : "|").append(c.getName());
        }
        final String text = lineupKey + "\n" + names + "\n"
                + (prizeEdition == null ? "" : prizeEdition.getCode()) + "\n";
        try {
            Files.write(fileOf(NeoQuest.name()).toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (final java.io.IOException | RuntimeException e) {
            // Sin guardar sigue funcionando: solo se volverian a sortear al reiniciar.
            System.out.println("[coliseo] no se han podido guardar los rivales: " + e);
        }
    }

    /** Los rivales guardados de esta Quest, si son de este nivel y siguen existiendo. */
    private static boolean load(final String key) {
        final File f = fileOf(NeoQuest.name());
        if (!f.isFile()) {
            return false;
        }
        try {
            final List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.size() < 2 || !key.equals(lines.get(0).trim())) {
                return false;
            }
            final List<String> names = Arrays.asList(lines.get(1).split("\\|"));
            final List<PaperCard> found = new ArrayList<>();
            for (final String n : names) {
                for (final PaperCard c : forge.neo.ascent.AscentSeedDeck.commanderPool()) {
                    if (c.getName().equals(n)) {
                        found.add(c);
                        break;
                    }
                }
            }
            if (found.size() != RIVALS) {
                return false;
            }
            final String code = lines.size() > 2 ? lines.get(2).trim() : "";
            CardEdition ed = code.isEmpty() ? null : FModel.getMagicDb().getEditions().get(code);
            if (ed != null && !NeoQuestShop.collectorEditions().contains(ed)) {
                ed = drawEdition();
            }
            lineup = Collections.unmodifiableList(found);
            prizeEdition = ed;
            lineupKey = key;
            return true;
        } catch (final java.io.IOException | RuntimeException e) {
            return false;
        }
    }

    /** Rivales nuevos para la proxima vez (tras jugar). */
    private static void forgetLineup(final String questName) {
        lineup = null;
        lineupKey = null;
        prizeEdition = null;
        deleteSaved(questName);
    }

    /** Solo {@code ColosseumCheck}: olvida lo que hay en memoria (como al reiniciar). */
    static synchronized void forgetInMemoryForTest() {
        lineup = null;
        lineupKey = null;
        prizeEdition = null;
    }

    /** Al borrar una Quest: sus rivales del Coliseo con ella. */
    static void deleteSaved(final String questName) {
        final File f = fileOf(questName);
        if (f.isFile()) {
            f.delete();
        }
    }

    /**
     * Los mazos de los rivales, del nivel de {@link #rivalTier}. Tarda (el
     * experto usa el generador bueno de Forge): llamar fuera del hilo de
     * interfaz. Si el de un comandante no sale, se prueba con otro.
     */
    public static List<Deck> buildDecks(final List<PaperCard> commanders) {
        final QuestEventDifficulty tier = rivalTier();
        final List<Deck> out = new ArrayList<>();
        final List<PaperCard> tried = new ArrayList<>(commanders);
        for (final PaperCard cmd : commanders) {
            // Sin mazos de fichas: a cuatro hunden la partida, y con ella la
            // entrada (forge.neo.match.TokenDecks, Discord 28-10-2026).
            Deck d = NeoCommanderDuels.deckForTable(cmd, tier);
            if (!legal(d)) {
                d = null;
                for (final PaperCard other : forge.neo.ascent.AscentSeedDeck.commanderPool()) {
                    if (tried.contains(other) || !forge.neo.ascent.AscentSynergy.knows(other)
                            || !NeoCommanderDuels.fitsTable(other)) {
                        continue;
                    }
                    tried.add(other);
                    final Deck alt = NeoCommanderDuels.deckForTable(other, tier);
                    if (legal(alt)) {
                        d = alt;
                        break;
                    }
                }
            }
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    private static boolean legal(final Deck d) {
        return d != null && DeckFormat.Commander.getDeckConformanceProblem(d) == null;
    }

    // ---------------------------------------------------------------
    // La entrada y el premio

    /**
     * Cobra la entrada, guarda, y la deja pendiente de liquidar. Si quedara
     * una de antes sin liquidar (una partida cuyo resultado no llego nunca),
     * se devuelve primero: no se ha jugado.
     */
    public static synchronized boolean pay() {
        if (!available() || !canAfford()) {
            return false;
        }
        refund();
        final QuestController q = FModel.getQuest();
        final int fee = fee();
        q.getAssets().subtractCredits(fee);
        q.save();
        pending = new Entry(q, NeoQuest.name(), fee);
        return true;
    }

    /** Si hay una entrada pagada sin liquidar. */
    public static synchronized boolean hasPending() {
        return pending != null;
    }

    /**
     * Devuelve la entrada pendiente (la partida no ha llegado a jugarse, o el
     * motor se ha roto) a la Quest que la pago. Los rivales se quedan.
     *
     * @return false si no habia nada pendiente
     */
    public static synchronized boolean refund() {
        final Entry e = pending;
        if (e == null) {
            return false;
        }
        pending = null;
        e.quest.getAssets().addCredits(e.fee);
        e.quest.save();
        System.out.println("[coliseo] entrada devuelta: " + e.fee);
        return true;
    }

    /** Lo que te llevas al ganar. */
    public static final class Prize {
        public final int credits;
        /** El sobre de colector ya abierto (en la coleccion), o null. */
        public final NeoQuestShop.Opened pack;

        Prize(final int credits, final NeoQuestShop.Opened pack) {
            this.credits = credits;
            this.pack = pack;
        }
    }

    /**
     * Has ganado: el doble de la entrada, el sobre de colector (de la expansion
     * anunciada), una victoria de la Quest, y rivales nuevos.
     *
     * @return null si no habia entrada pendiente (ya se liquido)
     */
    public static synchronized Prize win() {
        final Entry e = pending;
        if (e == null) {
            return null;
        }
        pending = null;
        final int credits = e.fee * 2;
        e.quest.getAssets().addCredits(credits);
        // El sobre va a la coleccion de la Quest ABIERTA: si ya no es la que
        // pago, solo los creditos (no hay forma de abrirlo en otra).
        final NeoQuestShop.Opened pack = FModel.getQuest() == e.quest ? giftPack() : null;
        e.quest.getAchievements().addWin();
        NeoQuestTrophies.noteColosseumWin(e.questName);
        forgetLineup(e.questName);
        e.quest.save();
        System.out.printf(java.util.Locale.ROOT, "[coliseo] ganado: %d creditos y %s%n", credits,
                pack == null ? "sin sobre" : pack.getCards().size() + " cartas");
        return new Prize(credits, pack);
    }

    /**
     * Has perdido (o te has rendido): la entrada se queda, y rivales nuevos.
     * No se apunta derrota en la Quest.
     *
     * @return false si no habia entrada pendiente
     */
    public static synchronized boolean lose() {
        final Entry e = pending;
        if (e == null) {
            return false;
        }
        pending = null;
        forgetLineup(e.questName);
        // Tu puesto en la mesa, a la vitrina (2.º a 4.º): el que tenias al caer.
        final int place = forge.neo.ascent.AscentTableEnd.lastFallPlace();
        NeoQuestTrophies.noteColosseumPlace(e.questName, place);
        System.out.println("[coliseo] perdido" + (place > 0 ? ", " + place + ".º" : ""));
        return true;
    }

    /**
     * El sobre del premio, ya en tu coleccion: de colector o normal segun el
     * nivel ({@link #collectorPrize}). Si el de la expansion anunciada no se
     * deja montar (alguna revienta en el motor, ver
     * {@code NeoQuestShop.collectorTemplate}), se prueba con otras: un premio
     * anunciado no puede quedarse en nada.
     */
    private static NeoQuestShop.Opened giftPack() {
        final boolean collector = collectorPrize();
        final List<CardEdition> tries = new ArrayList<>();
        if (prizeEdition != null) {
            tries.add(prizeEdition);
        }
        final List<CardEdition> others = new ArrayList<>(NeoQuestShop.collectorEditions());
        Collections.shuffle(others, MyRandom.getRandom());
        tries.addAll(others.subList(0, Math.min(5, others.size())));
        for (final CardEdition ed : tries) {
            try {
                final NeoQuestShop.Opened got = NeoQuestShop.grantFree(
                        collector ? NeoQuestShop.collectorBooster(ed) : NeoQuestShop.boosterOf(ed));
                if (got != null) {
                    return got;
                }
            } catch (final RuntimeException ex) {
                System.out.println("[coliseo] el sobre de " + ed.getCode() + " no sale: " + ex);
            }
        }
        return null;
    }
}
