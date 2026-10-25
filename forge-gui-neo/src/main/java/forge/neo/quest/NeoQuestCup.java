package forge.neo.quest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.gamemodes.quest.QuestEventDuel;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.MyRandom;

/**
 * <b>El torneo de la Quest</b> (itch.io, 23-10-2026: <i>"when playing commander
 * can we have commander tournaments instead of draft?"</i>; Ana: los de draft
 * se quedan, esto va al lado).
 *
 * <ul>
 *   <li><b>Commander</b>: 16 jugadores. Primera ronda, cuatro mesas de cuatro
 *       (la tuya se juega, como el Coliseo); final, una mesa con los cuatro
 *       ganadores.</li>
 *   <li><b>Estandar</b>: cuadro de 8, eliminacion directa, uno contra uno con
 *       tu mazo de la Quest contra los de los duelos de Forge.</li>
 * </ul>
 *
 * <p><b>Crece con la campana</b> (Ana: <i>"no quiero que empieces una quest
 * nueva ... y te den ya un game changer"</i>): entrada, rivales y premio salen
 * del nivel de la Quest ({@link NeoQuest#tier}, que sube con las victorias y
 * la dificultad elegida). Los rivales van un nivel por delante, como en el
 * Coliseo. Lo que se ofrece se fija al pagar y ya no cambia.
 *
 * <p><b>Uno por Quest, guardado</b> en {@code <nombre>.torneo} junto a la
 * partida (Forge solo lista los {@code .dat}): cerrar entre la primera ronda y
 * la final deja el torneo donde estaba. Las mesas que no son la tuya se
 * resuelven al azar al cerrarse la tuya (los rivales son todos del mismo
 * nivel), y el cuadro las ensenya.
 *
 * <p>Sin JavaFX: lo usa tambien NeoForge Android.
 */
public final class NeoQuestCup {

    private NeoQuestCup() {
    }

    /** En que punto esta. */
    public enum Stage {
        /** No hay torneo: se ofrece uno. */
        OFFER,
        /** Pagado, y te toca jugar. */
        PLAYING,
        /** Eliminado antes de la final. */
        ELIMINATED,
        /** Has perdido la final: se devuelve la entrada. */
        RUNNER_UP,
        /** Has ganado. */
        CHAMPION
    }

    /** Lo que se gana, por nivel. Ver {@link #prizeFor}. */
    public enum PrizeCard {
        NONE, RARE, MYTHIC, GAME_CHANGER
    }

    /** Las condiciones de un torneo: entrada, nivel y premio. */
    public static final class Terms {
        public final boolean commander;
        /** Tu nivel al entrar. */
        public final QuestEventDifficulty tier;
        public final int fee;
        public final int prizeCredits;
        /** Sobres normales de premio. */
        public final int boosters;
        /** Sobres de colector de premio. */
        public final int collectors;
        /** Que carta se elige de entre tres, o NONE. */
        public final PrizeCard pick;

        Terms(final boolean commander, final QuestEventDifficulty tier) {
            this.commander = commander;
            this.tier = tier;
            switch (tier) {
                case EXPERT:
                    fee = 3600;
                    prizeCredits = 12_000;
                    boosters = 0;
                    collectors = commander ? 1 : 2;
                    pick = commander ? PrizeCard.GAME_CHANGER : PrizeCard.MYTHIC;
                    break;
                case HARD:
                    fee = 2400;
                    prizeCredits = 8000;
                    boosters = 0;
                    collectors = 1;
                    pick = PrizeCard.MYTHIC;
                    break;
                case MEDIUM:
                    fee = 1500;
                    prizeCredits = 5000;
                    boosters = 0;
                    collectors = 1;
                    pick = PrizeCard.RARE;
                    break;
                default:
                    fee = 900;
                    prizeCredits = 2500;
                    boosters = 2;
                    collectors = 0;
                    pick = PrizeCard.NONE;
                    break;
            }
        }

        /** El nivel de los rivales: uno por encima del tuyo, como mucho experto. */
        public QuestEventDifficulty rivalTier() {
            switch (tier) {
                case EASY:
                    return QuestEventDifficulty.MEDIUM;
                case MEDIUM:
                    return QuestEventDifficulty.HARD;
                default:
                    return QuestEventDifficulty.EXPERT;
            }
        }

        /** Cuantos jugadores: 16 en Commander (mesas de 4), 8 en Estandar. */
        public int seats() {
            return commander ? 16 : 8;
        }

        /** Cuantos por mesa. */
        public int groupSize() {
            return commander ? 4 : 2;
        }

        /** Cuantas rondas: 2 en Commander, 3 en Estandar. */
        public int rounds() {
            return commander ? 2 : 3;
        }
    }

    /** El torneo en curso (o terminado y sin cerrar). */
    private static Cup cup;
    private static String cupQuest;

    private static final class Cup {
        Terms terms;
        /** Asiento 0 = tu. Los demas: el comandante (Commander) o el duelo (Estandar). */
        final List<String> seats = new ArrayList<>();
        /** Por ronda, los que pasan (en el orden de las mesas). */
        final List<List<Integer>> winners = new ArrayList<>();
        Stage stage = Stage.PLAYING;
        /** Las tres cartas a elegir del premio (nombre|edicion), o vacio. */
        final List<String> choices = new ArrayList<>();
        /** Si la entrada esta ya liquidada (cobrada, perdida o devuelta). */
        boolean settled;
    }

    /** Si hay torneo en esta Quest (en todas: Commander y Estandar). */
    public static boolean available() {
        return NeoQuest.isActive();
    }

    /** Lo que se ofrece ahora (con tu nivel de ahora), o las del torneo en curso. */
    public static synchronized Terms terms() {
        final Cup c = current();
        if (c != null) {
            return c.terms;
        }
        return new Terms(NeoQuest.modalidad() == NeoQuest.Modalidad.COMMANDER, NeoQuest.tier());
    }

    public static synchronized Stage stage() {
        final Cup c = current();
        return c == null ? Stage.OFFER : c.stage;
    }

    public static boolean canAfford() {
        return NeoQuest.isActive() && NeoQuest.credits() >= terms().fee;
    }

    /** Por que no se puede entrar con tu mazo de ahora, o null. */
    public static String deckProblem() {
        return NeoQuest.problemWith(NeoQuest.currentDeck());
    }

    // ---------------------------------------------------------------
    // El cuadro

    /** Los nombres de los asientos, ya para ensenyar ("" = tu). */
    public static synchronized List<String> seatNames() {
        final Cup c = current();
        final List<String> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        for (int i = 0; i < c.seats.size(); i++) {
            out.add(i == 0 ? "" : displayName(c, i));
        }
        return out;
    }

    /** El comandante del asiento (Commander), o null. Para pintar su carta. */
    public static synchronized PaperCard commanderOf(final int seat) {
        final Cup c = current();
        if (c == null || !c.terms.commander || seat <= 0 || seat >= c.seats.size()) {
            return null;
        }
        return commanderByName(c.seats.get(seat));
    }

    /**
     * Las mesas de la ronda {@code round} (0 = la primera): cada una, sus
     * asientos. Una ronda a la que aun no se ha llegado sale vacia.
     */
    public static synchronized List<List<Integer>> groups(final int round) {
        final Cup c = current();
        final List<List<Integer>> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        final List<Integer> entrants = entrants(c, round);
        if (entrants == null) {
            return out;
        }
        final int size = c.terms.groupSize();
        for (int i = 0; i + size <= entrants.size(); i += size) {
            out.add(new ArrayList<>(entrants.subList(i, i + size)));
        }
        return out;
    }

    /** Quien gano cada mesa de esa ronda, o una lista vacia si aun no se ha jugado. */
    public static synchronized List<Integer> winnersOf(final int round) {
        final Cup c = current();
        if (c == null || round < 0 || round >= c.winners.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(c.winners.get(round));
    }

    /** La ronda que toca (0..), la de la final si ya ha terminado. */
    public static synchronized int round() {
        final Cup c = current();
        return c == null ? 0 : Math.min(c.winners.size(), c.terms.rounds() - 1);
    }

    /** Si la ronda que toca es la final. */
    public static boolean isFinal() {
        return round() == terms().rounds() - 1;
    }

    /** Tus rivales de la partida que toca (asientos), o vacio si no te toca. */
    public static synchronized List<Integer> myRivals() {
        final Cup c = current();
        final List<Integer> out = new ArrayList<>();
        if (c == null || c.stage != Stage.PLAYING) {
            return out;
        }
        for (final List<Integer> g : groups(c.winners.size())) {
            if (g.contains(0)) {
                for (final int s : g) {
                    if (s != 0) {
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    private static List<Integer> entrants(final Cup c, final int round) {
        if (round == 0) {
            final List<Integer> all = new ArrayList<>();
            for (int i = 0; i < c.seats.size(); i++) {
                all.add(i);
            }
            return all;
        }
        return round - 1 < c.winners.size() ? c.winners.get(round - 1) : null;
    }

    // ---------------------------------------------------------------
    // Entrar, jugar y cerrar

    /** Cobra la entrada y monta el cuadro. */
    public static synchronized boolean pay() {
        if (!available() || current() != null || !canAfford() || deckProblem() != null) {
            return false;
        }
        final Terms t = terms();
        final Cup c = new Cup();
        c.terms = t;
        c.seats.add("");
        c.seats.addAll(t.commander ? drawCommanders(t.seats() - 1) : drawDuels(t.seats() - 1, t.rivalTier()));
        if (c.seats.size() < t.seats()) {
            System.out.println("[torneo] solo " + (c.seats.size() - 1) + " rivales: no se monta");
            return false;
        }
        // Tu sitio en el cuadro, al azar (tu siempre eres el asiento 0).
        final List<String> rivals = new ArrayList<>(c.seats.subList(1, c.seats.size()));
        Collections.shuffle(rivals, MyRandom.getRandom());
        c.seats.subList(1, c.seats.size()).clear();
        c.seats.addAll(rivals);
        final QuestController q = FModel.getQuest();
        q.getAssets().subtractCredits(t.fee);
        q.save();
        cup = c;
        cupQuest = NeoQuest.name();
        save();
        System.out.printf(Locale.ROOT, "[torneo] %s, nivel %s, entrada %d%n",
                t.commander ? "Commander" : "Estandar", t.tier, t.fee);
        return true;
    }

    /** Los mazos de tus rivales de ahora. Tarda (el experto): fuera del hilo de interfaz. */
    public static List<Deck> rivalDecks() {
        final Cup c;
        final List<Integer> rivals;
        synchronized (NeoQuestCup.class) {
            c = current();
            rivals = myRivals();
        }
        final List<Deck> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        for (final int s : rivals) {
            final Deck d = c.terms.commander
                    ? commanderDeck(c.seats.get(s), c.terms.rivalTier())
                    : duelDeck(c.seats.get(s));
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    /** Los nombres con que se sientan tus rivales de ahora, en el mismo orden. */
    public static synchronized List<String> rivalNames() {
        final Cup c = current();
        final List<String> out = new ArrayList<>();
        if (c != null) {
            for (final int s : myRivals()) {
                out.add(displayName(c, s));
            }
        }
        return out;
    }

    /** Lo que ha pasado tras tu partida. */
    public static final class Result {
        public final Stage stage;
        /** El premio, si has ganado el torneo. */
        public final Prize prize;
        /** Lo devuelto, si has perdido la final. */
        public final int refunded;

        Result(final Stage stage, final Prize prize, final int refunded) {
            this.stage = stage;
            this.prize = prize;
            this.refunded = refunded;
        }
    }

    /** Lo que te llevas al ganarlo (creditos y sobres ya dados; la carta, a elegir). */
    public static final class Prize {
        public final int credits;
        public final List<NeoQuestShop.Opened> packs;
        public final List<PaperCard> choices;

        Prize(final int credits, final List<NeoQuestShop.Opened> packs, final List<PaperCard> choices) {
            this.credits = credits;
            this.packs = packs;
            this.choices = choices;
        }
    }

    /**
     * Apunta tu partida. Ganar te pasa de ronda (las demas mesas se resuelven
     * al azar) y, si era la final, cobra el premio. Perder te elimina; en la
     * final, devuelve la entrada.
     *
     * @return null si no habia partida pendiente
     */
    public static synchronized Result record(final boolean won) {
        final Cup c = current();
        if (c == null || c.stage != Stage.PLAYING) {
            return null;
        }
        final int round = c.winners.size();
        final boolean last = round == c.terms.rounds() - 1;
        final List<Integer> pass = new ArrayList<>();
        for (final List<Integer> g : groups(round)) {
            if (g.contains(0)) {
                pass.add(won ? 0 : pickOther(g));
            } else {
                pass.add(g.get(MyRandom.getRandom().nextInt(g.size())));
            }
        }
        c.winners.add(pass);
        Prize prize = null;
        int refunded = 0;
        final QuestController q = FModel.getQuest();
        if (!won) {
            if (last) {
                c.stage = Stage.RUNNER_UP;
                NeoQuestTrophies.noteCup(NeoQuest.name(), false);
                refunded = c.terms.fee;
                q.getAssets().addCredits(refunded);
            } else {
                // El resto del cuadro, para que se vea quien lo gana.
                while (c.winners.size() < c.terms.rounds()) {
                    final List<Integer> next = new ArrayList<>();
                    for (final List<Integer> g : groups(c.winners.size())) {
                        next.add(g.get(MyRandom.getRandom().nextInt(g.size())));
                    }
                    c.winners.add(next);
                }
                c.stage = Stage.ELIMINATED;
            }
            c.settled = true;
        } else if (last) {
            c.stage = Stage.CHAMPION;
            NeoQuestTrophies.noteCup(NeoQuest.name(), true);
            prize = grantPrize(c);
            q.getAchievements().addWin();
            c.settled = true;
        } else {
            // Pasar de ronda tambien es ganar una partida de la Quest.
            q.getAchievements().addWin();
        }
        q.save();
        save();
        System.out.printf(Locale.ROOT, "[torneo] ronda %d: %s -> %s%n", round + 1, won ? "ganada" : "perdida", c.stage);
        return new Result(c.stage, prize, refunded);
    }

    private static int pickOther(final List<Integer> g) {
        final List<Integer> others = new ArrayList<>(g);
        others.remove(Integer.valueOf(0));
        return others.get(MyRandom.getRandom().nextInt(others.size()));
    }

    /** Retirarse: cuenta como perder tu partida de ahora. */
    public static Result withdraw() {
        return record(false);
    }

    /** Las cartas a elegir del premio que siguen pendientes (vacio si ninguna). */
    public static synchronized List<PaperCard> pendingChoices() {
        final Cup c = current();
        final List<PaperCard> out = new ArrayList<>();
        if (c != null) {
            for (final String key : c.choices) {
                final PaperCard pc = cardByKey(key);
                if (pc != null) {
                    out.add(pc);
                }
            }
        }
        return out;
    }

    /** Te quedas con esa carta del premio (una de {@link #pendingChoices}). */
    public static synchronized boolean take(final PaperCard card) {
        final Cup c = current();
        if (c == null || card == null || !c.choices.contains(keyOf(card))) {
            return false;
        }
        FModel.getQuest().getCards().addSingleCard(card, 1);
        c.choices.clear();
        FModel.getQuest().save();
        save();
        return true;
    }

    /**
     * Cierra el torneo terminado (visto el resultado) para que se ofrezca el
     * siguiente. Con una carta del premio sin elegir no se cierra: se elige
     * antes.
     */
    public static synchronized boolean dismiss() {
        final Cup c = current();
        if (c == null || c.stage == Stage.PLAYING || !c.choices.isEmpty()) {
            return false;
        }
        cup = null;
        cupQuest = null;
        deleteSaved(NeoQuest.name());
        return true;
    }

    // ---------------------------------------------------------------
    // El premio

    private static Prize grantPrize(final Cup c) {
        final Terms t = c.terms;
        final QuestController q = FModel.getQuest();
        q.getAssets().addCredits(t.prizeCredits);
        final List<NeoQuestShop.Opened> packs = new ArrayList<>();
        final List<CardEdition> eds = new ArrayList<>(NeoQuestShop.editions());
        Collections.shuffle(eds, MyRandom.getRandom());
        for (int i = 0; i < t.boosters && !eds.isEmpty(); i++) {
            for (final CardEdition ed : eds.subList(0, Math.min(5, eds.size()))) {
                final NeoQuestShop.Opened got = safeGrant(NeoQuestShop.boosterOf(ed));
                if (got != null) {
                    packs.add(got);
                    break;
                }
            }
        }
        final List<CardEdition> col = new ArrayList<>(NeoQuestShop.collectorEditions());
        Collections.shuffle(col, MyRandom.getRandom());
        for (int i = 0; i < t.collectors && !col.isEmpty(); i++) {
            for (final CardEdition ed : col.subList(0, Math.min(5, col.size()))) {
                final NeoQuestShop.Opened got = safeGrant(NeoQuestShop.collectorBooster(ed));
                if (got != null) {
                    packs.add(got);
                    break;
                }
            }
        }
        c.choices.clear();
        for (final PaperCard pc : drawChoices(t.pick)) {
            c.choices.add(keyOf(pc));
        }
        System.out.printf(Locale.ROOT, "[torneo] premio: %d creditos, %d sobres, %d cartas a elegir%n",
                t.prizeCredits, packs.size(), c.choices.size());
        return new Prize(t.prizeCredits, packs, pendingChoicesOf(c));
    }

    private static List<PaperCard> pendingChoicesOf(final Cup c) {
        final List<PaperCard> out = new ArrayList<>();
        for (final String key : c.choices) {
            final PaperCard pc = cardByKey(key);
            if (pc != null) {
                out.add(pc);
            }
        }
        return out;
    }

    private static NeoQuestShop.Opened safeGrant(final forge.item.BoosterPack pack) {
        if (pack == null) {
            return null;
        }
        try {
            return NeoQuestShop.grantFree(pack);
        } catch (final RuntimeException e) {
            System.out.println("[torneo] un sobre del premio no sale: " + e);
            return null;
        }
    }

    /** Tres cartas distintas para elegir una. */
    public static List<PaperCard> drawChoices(final PrizeCard kind) {
        final List<PaperCard> pool = new ArrayList<>();
        if (kind == PrizeCard.NONE) {
            return pool;
        }
        if (kind == PrizeCard.GAME_CHANGER) {
            for (final String name : gameChangers()) {
                final PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(name);
                if (pc != null) {
                    pool.add(pc);
                }
            }
        } else {
            final CardRarity want = kind == PrizeCard.MYTHIC ? CardRarity.MythicRare : CardRarity.Rare;
            final Set<String> seen = new LinkedHashSet<>();
            for (final CardEdition ed : NeoQuestShop.editions()) {
                for (final PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards(ed)) {
                    if (pc.getRarity() == want && seen.add(pc.getName())) {
                        pool.add(pc);
                    }
                }
            }
        }
        Collections.shuffle(pool, MyRandom.getRandom());
        return new ArrayList<>(pool.subList(0, Math.min(3, pool.size())));
    }

    /** La lista de Game Changers que trae Forge (la del bracket de Commander). */
    static List<String> gameChangers() {
        final List<String> out = new ArrayList<>();
        try {
            for (final String line : Files.readAllLines(
                    new File(ForgeConstants.COMMANDER_BRACKET_GAMECHANGERS_FILE).toPath(), StandardCharsets.UTF_8)) {
                final String n = line.trim();
                if (!n.isEmpty() && !n.startsWith("#")) {
                    out.add(n);
                }
            }
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[torneo] no se lee la lista de Game Changers: " + e);
        }
        return out;
    }

    // ---------------------------------------------------------------
    // Los rivales

    private static List<String> drawCommanders(final int n) {
        final List<PaperCard> pool = new ArrayList<>();
        final String mine = myCommanderName();
        for (final PaperCard c : forge.neo.ascent.AscentSeedDeck.commanderPool()) {
            if (forge.neo.ascent.AscentSynergy.knows(c) && !c.getName().equals(mine)) {
                pool.add(c);
            }
        }
        Collections.shuffle(pool, MyRandom.getRandom());
        final List<String> out = new ArrayList<>();
        for (final PaperCard c : pool.subList(0, Math.min(n, pool.size()))) {
            out.add(c.getName());
        }
        return out;
    }

    private static List<String> drawDuels(final int n, final QuestEventDifficulty tier) {
        final List<String> out = new ArrayList<>();
        final List<QuestEventDuel> pool = new ArrayList<>();
        try {
            for (final QuestEventDuel d : FModel.getQuest().getDuelsManager().getDuels(tier)) {
                if (d != null && d.getEventDeck() != null) {
                    pool.add(d);
                }
            }
            // Si ese nivel no da para siete, se completa con los de al lado.
            if (pool.size() < n) {
                for (final QuestEventDuel d : FModel.getQuest().getDuelsManager().getAllDuels()) {
                    if (d != null && d.getEventDeck() != null && !pool.contains(d)) {
                        pool.add(d);
                    }
                }
            }
        } catch (final RuntimeException e) {
            System.out.println("[torneo] no salen los duelos: " + e);
        }
        Collections.shuffle(pool, MyRandom.getRandom());
        for (final QuestEventDuel d : pool) {
            if (out.size() >= n) {
                break;
            }
            if (!out.contains(d.getName())) {
                out.add(d.getName());
            }
        }
        return out;
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

    private static PaperCard commanderByName(final String name) {
        for (final PaperCard c : forge.neo.ascent.AscentSeedDeck.commanderPool()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return FModel.getMagicDb().getCommonCards().getCard(name);
    }

    private static QuestEventDuel duelByName(final String name) {
        try {
            for (final QuestEventDuel d : FModel.getQuest().getDuelsManager().getAllDuels()) {
                if (d != null && name.equals(d.getName())) {
                    return d;
                }
            }
        } catch (final RuntimeException e) {
            // sin duelos: el asiento sale con su nombre tal cual
        }
        return null;
    }

    private static String displayName(final Cup c, final int seat) {
        final String raw = c.seats.get(seat);
        if (c.terms.commander) {
            final PaperCard pc = commanderByName(raw);
            return pc == null ? raw : forge.neo.card.CardText.nameOf(pc);
        }
        final QuestEventDuel d = duelByName(raw);
        return d == null || d.getTitle() == null ? raw : d.getTitle();
    }

    /** El mazo de ese comandante a ese nivel; si no sale legal, el de otro. */
    private static Deck commanderDeck(final String name, final QuestEventDifficulty tier) {
        final PaperCard cmd = commanderByName(name);
        Deck d = cmd == null ? null : NeoCommanderDuels.deckFor(cmd, tier);
        if (d != null && DeckFormat.Commander.getDeckConformanceProblem(d) == null) {
            return d;
        }
        for (final PaperCard other : forge.neo.ascent.AscentSeedDeck.commanderPool()) {
            if (!forge.neo.ascent.AscentSynergy.knows(other) || other.getName().equals(name)) {
                continue;
            }
            d = NeoCommanderDuels.deckFor(other, tier);
            if (d != null && DeckFormat.Commander.getDeckConformanceProblem(d) == null) {
                return d;
            }
        }
        return null;
    }

    private static Deck duelDeck(final String name) {
        final QuestEventDuel d = duelByName(name);
        return d == null ? null : d.getEventDeck();
    }

    // ---------------------------------------------------------------
    // Guardar

    private static String keyOf(final PaperCard pc) {
        return pc.getName() + "|" + pc.getEdition();
    }

    private static PaperCard cardByKey(final String key) {
        final int bar = key.lastIndexOf('|');
        if (bar < 0) {
            return FModel.getMagicDb().getCommonCards().getCard(key);
        }
        final PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(key.substring(0, bar), key.substring(bar + 1));
        return pc != null ? pc : FModel.getMagicDb().getCommonCards().getCard(key.substring(0, bar));
    }

    /** El de la Quest abierta, cargado del disco si hace falta. */
    private static Cup current() {
        if (!NeoQuest.isActive()) {
            return null;
        }
        final String name = NeoQuest.name();
        if (cup != null && name.equals(cupQuest)) {
            return cup;
        }
        cup = load(name);
        cupQuest = cup == null ? null : name;
        return cup;
    }

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".torneo");
    }

    /**
     * Una linea por cosa: version, formato, nivel, etapa, asientos, las
     * cartas del premio y una linea por ronda con los que pasan.
     */
    private static void save() {
        if (cup == null || cupQuest == null) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("1\n");
        sb.append(cup.terms.commander ? "commander" : "standard").append('\n');
        sb.append(cup.terms.tier.name()).append('\n');
        sb.append(cup.stage.name()).append('\n');
        sb.append(String.join("\t", cup.seats)).append('\n');
        sb.append(String.join("\t", cup.choices)).append('\n');
        for (final List<Integer> r : cup.winners) {
            final List<String> parts = new ArrayList<>();
            for (final int s : r) {
                parts.add(Integer.toString(s));
            }
            sb.append(String.join(",", parts)).append('\n');
        }
        try {
            Files.write(fileOf(cupQuest).toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[torneo] no se ha podido guardar: " + e);
        }
    }

    private static Cup load(final String questName) {
        final File f = fileOf(questName);
        if (!f.isFile()) {
            return null;
        }
        try {
            final List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            if (lines.size() < 6 || !"1".equals(lines.get(0).trim())) {
                return null;
            }
            final Cup c = new Cup();
            c.terms = new Terms("commander".equals(lines.get(1).trim()),
                    QuestEventDifficulty.valueOf(lines.get(2).trim()));
            c.stage = Stage.valueOf(lines.get(3).trim());
            for (final String s : lines.get(4).split("\t", -1)) {
                c.seats.add(s);
            }
            for (final String s : lines.get(5).split("\t")) {
                if (!s.isEmpty()) {
                    c.choices.add(s);
                }
            }
            for (int i = 6; i < lines.size(); i++) {
                final String l = lines.get(i).trim();
                if (l.isEmpty()) {
                    continue;
                }
                final List<Integer> r = new ArrayList<>();
                for (final String p : l.split(",")) {
                    r.add(Integer.parseInt(p.trim()));
                }
                c.winners.add(r);
            }
            c.settled = c.stage != Stage.PLAYING;
            if (c.seats.size() != c.terms.seats()) {
                return null;
            }
            return c;
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[torneo] no se ha podido leer: " + e);
            return null;
        }
    }

    /** Al borrar una Quest: su torneo con ella. */
    static void deleteSaved(final String questName) {
        final File f = fileOf(questName);
        if (f.isFile()) {
            f.delete();
        }
        if (questName != null && questName.equals(cupQuest)) {
            cup = null;
            cupQuest = null;
        }
    }

    /** Solo el comprobador: olvida lo que hay en memoria (como al reiniciar). */
    static synchronized void forgetInMemoryForTest() {
        cup = null;
        cupQuest = null;
    }
}
