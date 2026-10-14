package forge.neo.quest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import forge.card.CardEdition;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.gamemodes.limited.BoosterDraft;
import forge.gamemodes.limited.LimitedDeckEvaluator;
import forge.gamemodes.limited.LimitedPoolType;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestDraftUtils;
import forge.gamemodes.quest.QuestEventDraft;
import forge.gamemodes.quest.data.QuestAchievements;
import forge.item.BoosterPack;
import forge.item.PaperCard;
import forge.model.CardBlock;
import forge.model.FModel;
import forge.neo.draft.NeoDraft;
import forge.util.MyRandom;

/**
 * <b>Los torneos de draft de la Quest</b>, envueltos (Discord, 13-10-2026: dar
 * algo que hacer a la Quest cuando ya tienes coleccion).
 *
 * <p>Forge los trae enteros en el MODELO: {@link QuestEventDraft} genera el
 * evento (sobres, entrada, siete rivales con nombre y cara), lleva el cuadro en
 * {@code getStandings()}, sabe en que puesto has quedado y reparte los premios.
 * Lo que <b>no</b> usamos es su controlador ({@code QuestTournamentController}):
 * pregunta con dialogos del motor que bloquean, guarda la Quest en cada
 * refresco, y apuntar el resultado de cada partida lo deja a la pantalla de fin
 * de partida de cada GUI ({@code QuestDraftWinLose}). Aqui se hace lo mismo
 * con llamadas directas, como la tienda y el bazar.
 *
 * <p><b>El cuadro</b> ({@code getStandings()}, 15 huecos): 0-7 los asientos de
 * la primera ronda (0 contra 1, 2 contra 3...), 8-11 los que pasan a
 * semifinales, 12-13 a la final y 14 el campeon. Cada hueco vale
 * {@link QuestEventDraft#HUMAN}, el numero de un rival ("1".."7": su nombre es
 * {@code getAINames()[n-1]} y su mazo el {@code n-1} del grupo) o
 * {@link QuestEventDraft#UNDETERMINED}.
 *
 * <p><b>Las partidas entre dos IA se simulan</b>, como en el Forge de movil: el
 * mazo mejor valorado ({@code LimitedDeckEvaluator}) gana tres de cada cuatro.
 * Es la misma regla que {@code QuestDraftUtils.injectRandomMatchOutcome}, pero
 * para cualquier emparejamiento que este listo y no solo el primero libre del
 * cuadro: asi la ronda entera de los demas esta resuelta cuando te toca a ti.
 *
 * <p><b>Que se guarda y cuando.</b> Al entrar se cobra la entrada y se apunta
 * el evento como el actual, y se guarda la Quest: las dos cosas juntas en el
 * disco. Los picks NO se guardan (tampoco en Forge): si el juego se cierra a
 * mitad del draft, al volver se encuentra un evento actual sin mazo y se
 * devuelve la entrada ({@link #current()}).
 *
 * <p>Java puro y sin JavaFX: lo usa tambien Android por el jar (nada de API que
 * no tenga Android 8).
 */
public final class NeoQuestDrafts {

    private NeoQuestDrafts() {
    }

    /** En que punto esta el torneo actual. */
    public enum Stage {
        /** No hay ninguno: se ensenyan los que se ofrecen. */
        NONE,
        /** Draftado: falta montar el mazo y empezar. */
        DECK,
        /** Empezado, y te queda alguna partida. */
        PLAYING,
        /** Empezado y sin partidas para ti: toca recoger el premio. */
        FINISHED
    }

    /** Un emparejamiento del cuadro, para pintarlo. */
    public static final class Pairing {
        /** El hueco donde va el ganador (8..14). */
        public final int slot;
        /** 1 = primera ronda, 2 = semifinal, 3 = final. */
        public final int round;
        /** Los dos que se enfrentan (HUMAN, "1".."7" o UNDETERMINED). */
        public final String a;
        public final String b;
        /** Quien ha ganado, o null si aun no se ha jugado. */
        public final String winner;

        Pairing(final int slot, final int round, final String a, final String b, final String winner) {
            this.slot = slot;
            this.round = round;
            this.a = a;
            this.b = b;
            this.winner = winner;
        }

        public boolean hasHuman() {
            return QuestEventDraft.HUMAN.equals(a) || QuestEventDraft.HUMAN.equals(b);
        }

        public boolean isReady() {
            return !QuestEventDraft.UNDETERMINED.equals(a) && !QuestEventDraft.UNDETERMINED.equals(b);
        }

        /** El rival del jugador en este emparejamiento, o null. */
        public String rivalOfHuman() {
            if (QuestEventDraft.HUMAN.equals(a)) {
                return b;
            }
            return QuestEventDraft.HUMAN.equals(b) ? a : null;
        }
    }

    /** Lo que se ha llevado el jugador al cerrar el torneo. */
    public static final class Prizes {
        public final int place;
        public final int credits;
        /** Las cartas de los sobres y las sueltas, ya en la coleccion. */
        public final List<PaperCard> cards;
        /** Las raras entre las que elegir una (1º y 2º), o vacio. */
        public final List<PaperCard> rareChoices;
        /** Si gano el torneo: una ficha de draft. */
        public final boolean token;

        Prizes(final int place, final int credits, final List<PaperCard> cards,
               final List<PaperCard> rareChoices, final boolean token) {
            this.place = place;
            this.credits = credits;
            this.cards = cards;
            this.rareChoices = rareChoices;
            this.token = token;
        }
    }

    /** Con que Quest y cuantas victorias se generaron los torneos la ultima vez. */
    private static volatile String lastGenerated;

    /**
     * Mientras se draftea en ESTA sesion. Sin esto, {@link #current()} veria
     * un evento actual sin mazo (el draft aun no ha terminado) y lo daria por
     * interrumpido.
     */
    private static volatile boolean drafting;

    private static QuestController quest() {
        return FModel.getQuest();
    }

    private static QuestAchievements achievements() {
        final QuestController q = quest();
        return q == null ? null : q.getAchievements();
    }

    // ---------------------------------------------------------------
    // Que hay

    /**
     * Los torneos que se ofrecen ahora, sin el que estes jugando.
     *
     * <p>{@code generateDrafts} es lo que hace Forge al abrir la pantalla: rota
     * los viejos y anyade los que te hayas ganado (uno cada
     * {@code WINS_NEW_DRAFT} victorias). Guarda la Quest, asi que se llama una
     * vez por pantalla y no en cada repintado.
     */
    public static List<QuestEventDraft> offered() {
        final QuestAchievements a = achievements();
        if (a == null) {
            return java.util.Collections.emptyList();
        }
        try {
            // Solo si han cambiado las victorias (o es otra Quest): los torneos
            // nuevos y la rotacion de los viejos salen de ahi, y generar guarda
            // la Quest entera — en cada vuelta al cuartel seria escribirla en
            // el disco para nada.
            final String stamp = quest().getName() + "#" + a.getWin() + "#" + a.getDraftTokens();
            if (!stamp.equals(lastGenerated)) {
                a.generateDrafts();
                lastGenerated = stamp;
            }
        } catch (final RuntimeException e) {
            // Sin formatos de draft disponibles (una Quest limitada a unas
            // expansiones sin sobres) el motor puede tropezar: sin torneos.
            System.out.println("[torneo] no se han podido generar torneos: " + e);
        }
        final List<QuestEventDraft> out = new ArrayList<>();
        if (a.getDraftEvents() != null) {
            final QuestEventDraft mine = current();
            for (final QuestEventDraft e : a.getDraftEvents()) {
                if (e != mine) {
                    out.add(e);
                }
            }
        }
        return out;
    }

    /**
     * El torneo que estas jugando, o null.
     *
     * <p>Si hay uno apuntado pero sin mazo, y no se esta drafteando ahora, es un
     * draft que se quedo a medias al cerrar el juego: se devuelve la entrada y
     * se quita, igual que si se hubiera abandonado.
     */
    public static QuestEventDraft current() {
        final QuestAchievements a = achievements();
        if (a == null) {
            return null;
        }
        final QuestEventDraft e = a.getCurrentDraft();
        if (e == null) {
            return null;
        }
        if (!drafting && group() == null) {
            System.out.println("[torneo] draft a medias de una sesion anterior: se devuelve la entrada");
            refundAndDrop(e);
            return null;
        }
        return e;
    }

    public static Stage stage() {
        final QuestEventDraft e = current();
        if (e == null || group() == null) {
            return Stage.NONE;
        }
        if (!e.isStarted()) {
            return Stage.DECK;
        }
        return e.playerHasMatchesLeft() ? Stage.PLAYING : Stage.FINISHED;
    }

    /** Los sobres del evento, con su nombre: "Bloomburrow · Bloomburrow · ...". */
    public static List<String> boosterNames(final QuestEventDraft e) {
        final List<String> out = new ArrayList<>();
        if (e == null) {
            return out;
        }
        for (final String code : e.getBoosterConfiguration()) {
            final CardEdition ed = FModel.getMagicDb().getEditions().get(code);
            out.add(ed == null ? code : ed.getName());
        }
        return out;
    }

    /**
     * Los sobres agrupados por expansion, en el orden en que se abren:
     * "3 × Bloomburrow", o "2 × Ixalan" y "1 × Rivals of Ixalan".
     */
    public static List<String> boosterSummary(final QuestEventDraft e) {
        final java.util.LinkedHashMap<String, Integer> counts = new java.util.LinkedHashMap<>();
        for (final String name : boosterNames(e)) {
            counts.merge(name, 1, Integer::sum);
        }
        final List<String> out = new ArrayList<>();
        for (final java.util.Map.Entry<String, Integer> c : counts.entrySet()) {
            out.add(c.getValue() + " \u00d7 " + c.getKey());
        }
        return out;
    }

    public static boolean canAfford(final QuestEventDraft e) {
        return e != null && quest() != null && quest().getAssets().getCredits() >= e.getEntryFee();
    }

    public static int tokens() {
        final QuestAchievements a = achievements();
        return a == null ? 0 : a.getDraftTokens();
    }

    /** Los formatos para los que se puede crear un torneo (con una ficha). */
    public static List<QuestEventDraft.QuestDraftFormat> formats() {
        final QuestController q = quest();
        if (q == null) {
            return java.util.Collections.emptyList();
        }
        try {
            final List<QuestEventDraft.QuestDraftFormat> out =
                    new ArrayList<>(QuestEventDraft.getAvailableFormats(q));
            java.util.Collections.sort(out);
            return out;
        } catch (final RuntimeException e) {
            return java.util.Collections.emptyList();
        }
    }

    /**
     * Gasta una ficha (se gana quedando 1º): un torneo nuevo del formato que
     * elijas. Es {@code spendDraftToken} de Forge, que lo genera y guarda.
     */
    public static boolean spendToken(final QuestEventDraft.QuestDraftFormat format) {
        final QuestAchievements a = achievements();
        if (a == null || format == null || a.getDraftTokens() <= 0) {
            return false;
        }
        a.spendDraftToken(format);
        lastGenerated = null;
        return true;
    }

    // ---------------------------------------------------------------
    // El draft

    /**
     * Entra en el torneo: cobra la entrada, lo apunta como el actual, guarda la
     * Quest y abre los sobres.
     *
     * <p>No es {@code QuestEventDraft.enter()} a proposito: ese busca el bloque
     * por el nombre del evento, y el de una expansion suelta que no forma
     * bloque ("Bloomburrow") no existe — el draft no se abriria. Aqui, si el
     * bloque no esta, se monta como en el draft de siempre
     * ({@link NeoDraft#blockFor}).
     *
     * @return null si no llega el dinero o los sobres no se han podido abrir
     */
    public static NeoDraft enter(final QuestEventDraft e) {
        final QuestController q = quest();
        if (e == null || q == null || !canAfford(e) || current() != null) {
            return null;
        }
        final String[] packs = e.getBoosterConfiguration();
        if (packs.length == 0) {
            return null;
        }
        CardBlock block = FModel.getBlocks().get(e.getBlock());
        if (block == null) {
            final CardEdition first = FModel.getMagicDb().getEditions().get(packs[0]);
            if (first == null) {
                return null;
            }
            block = NeoDraft.blockFor(first);
        }
        final BoosterDraft d;
        try {
            d = BoosterDraft.createDraft(LimitedPoolType.Block, block, packs, 8);
        } catch (final RuntimeException ex) {
            System.out.println("[torneo] no se han podido abrir los sobres de " + e.getTitle() + ": " + ex);
            return null;
        }
        if (d == null) {
            return null;
        }
        e.select();
        q.getAchievements().setCurrentDraft(e);
        q.getAssets().subtractCredits(e.getEntryFee());
        drafting = true;
        q.save();
        return NeoDraft.of(d, e.getTitle());
    }

    /** Abandonar el draft antes de acabarlo: se devuelve la entrada, como en Forge. */
    public static void cancelDraft() {
        drafting = false;
        final QuestAchievements a = achievements();
        final QuestEventDraft e = a == null ? null : a.getCurrentDraft();
        if (e != null) {
            refundAndDrop(e);
        }
    }

    private static void refundAndDrop(final QuestEventDraft e) {
        final QuestController q = quest();
        q.getAssets().addCredits(e.getEntryFee());
        q.getAchievements().deleteDraft(e);
        q.getDraftDecks().delete(QuestEventDraft.DECK_NAME);
        q.save();
    }

    /**
     * Se acabaron los picks: el pool (y lo que fuiste poniendo en el mazo) y
     * los siete mazos de los rivales se guardan en la Quest. Es
     * {@code QuestDraftUtils.completeDraft}, que ademas les pone a los rivales
     * el nombre que espera el resto del torneo ("AI Deck 0".."6").
     */
    public static void finishDraft(final NeoDraft draft) {
        final DeckGroup group = draft.toGroup(QuestEventDraft.DECK_NAME);
        QuestDraftUtils.completeDraft(group);
        drafting = false;
    }

    // ---------------------------------------------------------------
    // El mazo

    /** El grupo del torneo: tu mazo y los siete rivales. */
    public static DeckGroup group() {
        final QuestController q = quest();
        return q == null ? null : q.getDraftDecks().get(QuestEventDraft.DECK_NAME);
    }

    public static Deck myDeck() {
        final DeckGroup g = group();
        return g == null ? null : g.getHumanDeck();
    }

    /**
     * Cuantas cartas has drafteado: mazo y banda juntos, SIN las basicas. Las
     * basicas no salieron de ningun sobre y las pone el editor (o "Montar
     * solo"); contarlas decia "tienes 60 cartas" con 42 drafteadas.
     */
    public static int poolSize() {
        final Deck d = myDeck();
        if (d == null) {
            return 0;
        }
        int n = 0;
        for (final java.util.Map.Entry<PaperCard, Integer> e : d.getAllCardsInASinglePool()) {
            if (!e.getKey().getRules().getType().isBasicLand()) {
                n += e.getValue();
            }
        }
        return n;
    }

    /** Por que no se puede jugar el mazo (menos de 40 cartas...), o null. */
    public static String deckProblem() {
        final Deck d = myDeck();
        if (d == null) {
            return "";
        }
        return forge.game.GameType.QuestDraft.getDeckFormat().getDeckConformanceProblem(d);
    }

    // ---------------------------------------------------------------
    // El torneo

    /** Empieza el torneo con el mazo como este. */
    public static boolean start() {
        final QuestEventDraft e = current();
        if (e == null || e.isStarted() || deckProblem() != null) {
            return false;
        }
        e.start();
        simulateReady();
        quest().save();
        return true;
    }

    /** Los siete emparejamientos del cuadro, ronda a ronda. */
    public static List<Pairing> bracket() {
        final List<Pairing> out = new ArrayList<>();
        final QuestEventDraft e = current();
        if (e == null) {
            return out;
        }
        final String[] s = e.getStandings();
        for (int slot = 8; slot < 15; slot++) {
            final int feeder = (slot - 8) * 2;
            final String winner = QuestEventDraft.UNDETERMINED.equals(s[slot]) ? null : s[slot];
            out.add(new Pairing(slot, roundOf(slot), s[feeder], s[feeder + 1], winner));
        }
        return out;
    }

    private static int roundOf(final int slot) {
        return slot <= 11 ? 1 : slot <= 13 ? 2 : 3;
    }

    /** La partida que te toca ahora, o null si no hay (o aun no estan los dos). */
    public static Pairing nextMatch() {
        for (final Pairing p : bracket()) {
            if (p.winner == null && p.hasHuman() && p.isReady()) {
                return p;
            }
        }
        return null;
    }

    /** El nombre de un participante: el tuyo o el del rival. */
    public static String nameOf(final String sid, final String humanName) {
        if (sid == null || QuestEventDraft.UNDETERMINED.equals(sid)) {
            return "";
        }
        if (QuestEventDraft.HUMAN.equals(sid)) {
            return humanName;
        }
        final QuestEventDraft e = current();
        final int i = indexOf(sid);
        return e == null || i < 0 || i >= e.getAINames().length ? sid : e.getAINames()[i];
    }

    /** La cara de un rival (indice de la hoja de avatares de Forge). */
    public static int iconOf(final String sid) {
        final QuestEventDraft e = current();
        final int i = indexOf(sid);
        return e == null || i < 0 || i >= e.getAIIcons().length ? 0 : e.getAIIcons()[i];
    }

    /** El mazo de un rival ("1".."7"). */
    public static Deck deckOf(final String sid) {
        final DeckGroup g = group();
        final int i = indexOf(sid);
        return g == null || i < 0 || i >= g.getAiDecks().size() ? null : g.getAiDecks().get(i);
    }

    private static int indexOf(final String sid) {
        try {
            return Integer.parseInt(sid) - 1;
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Apunta tu partida: ganas tu o gana el rival. Luego se simula lo que
     * quede listo entre las IA, y se guarda.
     */
    public static void recordMatch(final boolean won) {
        final QuestEventDraft e = current();
        final Pairing p = nextMatch();
        if (e == null || p == null) {
            return;
        }
        final String rival = p.rivalOfHuman();
        // setWinner busca por NOMBRE: cualquier nombre que no sea de un rival
        // es el jugador. HUMAN no lo es nunca.
        e.setWinner(won ? QuestEventDraft.HUMAN : e.getAINames()[indexOf(rival)]);
        simulateReady();
        quest().save();
        System.out.printf(java.util.Locale.ROOT, "[torneo] ronda %d: %s; puesto %d%n",
                p.round, won ? "ganada" : "perdida", e.getPlayerPlacement());
    }

    /**
     * Resuelve las partidas entre IA que esten listas.
     *
     * <p>Mientras sigas vivo, solo hasta tu ronda: el cuadro no avanza por
     * delante de ti. Si ya no te queda ninguna, hasta el final, para que se vea
     * quien gano.
     */
    public static boolean simulateReady() {
        final QuestEventDraft e = current();
        if (e == null || !e.isStarted()) {
            return false;
        }
        boolean any = false;
        final String[] s = e.getStandings();
        boolean changed = true;
        while (changed) {
            changed = false;
            final boolean alive = e.playerHasMatchesLeft();
            final Pairing mine = nextMatch();
            final int limit = !alive ? 3 : mine != null ? mine.round : humanRound(s);
            for (int slot = 8; slot < 15; slot++) {
                final int feeder = (slot - 8) * 2;
                final String a = s[feeder];
                final String b = s[feeder + 1];
                if (!QuestEventDraft.UNDETERMINED.equals(s[slot]) || roundOf(slot) > limit
                        || QuestEventDraft.UNDETERMINED.equals(a) || QuestEventDraft.UNDETERMINED.equals(b)
                        || QuestEventDraft.HUMAN.equals(a) || QuestEventDraft.HUMAN.equals(b)) {
                    continue;
                }
                s[slot] = simulate(a, b);
                changed = true;
                any = true;
            }
        }
        return any;
    }

    /**
     * Pone al dia el cuadro al abrir la pantalla, y guarda si ha cambiado algo.
     *
     * <p>{@code %APPDATA%\\Forge} se comparte con el Forge de siempre, que juega
     * las partidas entre IA cuando se le da al boton: un torneo que alli quedo
     * con tu ronda ganada y las demas sin jugar se quedaria aqui sin partida
     * que jugar y con "Jugar" apagado para siempre.
     */
    public static void catchUp() {
        if (simulateReady()) {
            quest().save();
        }
    }

    /** La ronda que juega el jugador ahora (la de su hueco mas alto, +1). */
    private static int humanRound(final String[] s) {
        int at = -1;
        for (int i = s.length - 1; i >= 0; i--) {
            if (QuestEventDraft.HUMAN.equals(s[i])) {
                at = i;
                break;
            }
        }
        return at <= 7 ? 1 : at <= 11 ? 2 : 3;
    }

    /** El mazo mejor valorado gana tres de cada cuatro (la regla de Forge). */
    private static String simulate(final String a, final String b) {
        final double va = value(a);
        final double vb = value(b);
        final boolean strongerWins = MyRandom.getRandom().nextInt(100) < 75;
        final String stronger = va > vb ? a : b;
        final String weaker = va > vb ? b : a;
        return strongerWins ? stronger : weaker;
    }

    private static double value(final String sid) {
        final Deck d = deckOf(sid);
        return d == null ? 0 : LimitedDeckEvaluator.getDeckDraftValue(d);
    }

    // ---------------------------------------------------------------
    // Cerrar

    /**
     * Cobra los premios del puesto en que estes (los de Forge: creditos y
     * sobres segun el bote, una promo, y en 1º y 2º una rara a elegir), si has
     * ganado una ficha de draft, y <b>cierra el torneo en el acto</b>
     * ({@link #close}).
     *
     * <p>Cerrar aqui y no despues de elegir la rara es a proposito: si el juego
     * se cerrara mientras eliges, el torneo seguiria "por cobrar" y se podria
     * cobrar dos veces. La rara se mete despues con {@link #takeRare}; si se
     * cierra el juego antes, se pierde la rara y nada mas. Un torneo sin
     * empezar no da premio (tu pool si pasa a la coleccion).
     */
    public static Prizes collect() {
        final QuestEventDraft e = current();
        if (e == null) {
            return null;
        }
        final int place = e.getPlayerPlacement();
        if (!e.isStarted()) {
            close();
            return new Prizes(place, 0, java.util.Collections.emptyList(), java.util.Collections.emptyList(), false);
        }
        final QuestEventDraft.QuestDraftPrizes p = e.collectPrizes();
        final List<PaperCard> cards = new ArrayList<>();
        int credits = 0;
        List<PaperCard> rares = java.util.Collections.emptyList();
        if (p != null) {
            credits = p.credits;
            if (p.hasBoosterPacks()) {
                for (final BoosterPack pack : p.boosterPacks) {
                    cards.addAll(pack.getCards());
                }
            }
            if (p.hasIndividualCards()) {
                cards.addAll(p.individualCards);
            }
            if (p.selectRareFromSets()) {
                rares = new ArrayList<>(p.selectRareCards);
            }
        }
        final boolean token = place == 1;
        if (token) {
            achievements().addDraftToken();
        }
        close();
        System.out.printf(java.util.Locale.ROOT,
                "[torneo] premios: puesto %d, %d creditos, %d cartas, %d raras a elegir%s%n",
                place, credits, cards.size(), rares.size(), token ? ", ficha" : "");
        return new Prizes(place, credits, cards, rares, token);
    }

    /**
     * Cierra el torneo: tu pool entero a la coleccion (menos las basicas que
     * pusiste tu), y el evento se quita
     * apuntando el puesto. Es {@code QuestEventDraft.addToQuestDecks} salvo una
     * cosa: el mazo del torneo solo se guarda entre tus mazos en una Quest de
     * Estandar — en una de Commander seria un mazo de 40 que no vale para nada.
     */
    private static void close() {
        final QuestController q = quest();
        final QuestEventDraft e = current();
        if (q == null || e == null) {
            return;
        }
        final Deck mine = myDeck();
        if (mine != null) {
            for (final PaperCard c : mine.getAllCardsInASinglePool().toFlatList()) {
                if (!c.isVeryBasicLand() || c.isFoil()) {
                    q.getCards().addSingleCard(c, 1);
                }
            }
            if (mine.get(DeckSection.Main).countAll() > 0
                    && NeoQuest.modalidad() != NeoQuest.Modalidad.COMMANDER) {
                final Deck kept = new Deck(e.getTitle() + " "
                        + new java.text.SimpleDateFormat("yyyy-MM-dd HH-mm", java.util.Locale.ROOT)
                                .format(new java.util.Date()));
                kept.getOrCreate(DeckSection.Main).addAll(mine.get(DeckSection.Main));
                q.getMyDecks().add(kept);
            }
        }
        q.getDraftDecks().delete(QuestEventDraft.DECK_NAME);
        q.getAchievements().endCurrentTournament(e.getPlayerPlacement());
        q.save();
    }

    /** La rara que has elegido de premio (1º y 2º), a la coleccion. */
    public static void takeRare(final PaperCard rare) {
        final QuestController q = quest();
        if (q == null || rare == null) {
            return;
        }
        q.getCards().addSingleCard(rare, 1);
        q.save();
    }

    /** Solo el comprobador: como si se hubiera cerrado el juego a mitad del draft. */
    static void forgetDraftingForTest() {
        drafting = false;
    }

    /** Para el comprobador: el cuadro como texto. */
    static String describe() {
        final QuestEventDraft e = current();
        return e == null ? "(sin torneo)" : Arrays.toString(e.getStandings());
    }
}
