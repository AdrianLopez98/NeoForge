package forge.neo.quest;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.deck.Deck;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.gamemodes.quest.data.QuestPreferences;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.neo.match.NeoMatchUI;

/**
 * El torneo de la Quest ({@link NeoQuestCup}) sin ventana: que entrada y premio
 * suban con el nivel (y el Game Changer solo arriba), el cuadro, que se guarde a
 * medias y se recupere, el premio y la final perdida, el de Estandar con los
 * duelos de Forge, y una partida de verdad de cada uno (con tope).
 * {@code run.cmd questcupcheck}.
 */
public final class QuestCupCheck {

    private QuestCupCheck() {
    }

    private static final String NAME = "neo-check-torneo";

    public static void run() {
        final QuestPreferences prefs = FModel.getQuestPreferences();
        final String current = prefs.getPref(QuestPreferences.QPref.CURRENT_QUEST);
        boolean ok = false;
        try {
            ok = runAll();
        } catch (final RuntimeException e) {
            System.out.println("  FALLO: " + e);
            e.printStackTrace(System.out);
        } finally {
            NeoQuest.delete(NAME);
            prefs.setPref(QuestPreferences.QPref.CURRENT_QUEST, current);
            prefs.save();
        }
        System.out.println();
        System.out.println(ok
                ? "  OK - torneo de la Quest: niveles, cuadro, guardado, premio y partidas"
                : "  FALLO - revisa el torneo de la Quest");
    }

    private static boolean check(final boolean good, final String what) {
        System.out.println("  " + (good ? "[ok]  " : "[MAL] ") + what);
        return good;
    }

    private static boolean runAll() {
        boolean ok = true;
        final int secs = Integer.getInteger("neo.cup.matchSecs", 90);

        // 1. Los niveles: entrada y premio suben; el Game Changer solo arriba.
        int lastFee = 0;
        for (final QuestEventDifficulty t : QuestEventDifficulty.values()) {
            if (t == QuestEventDifficulty.WILD) {
                continue;
            }
            final NeoQuestCup.Terms c = new NeoQuestCup.Terms(true, t);
            final NeoQuestCup.Terms s = new NeoQuestCup.Terms(false, t);
            System.out.printf(Locale.ROOT, "  -- nivel %s: entrada %d, premio %d, carta %s / %s, rivales %s%n",
                    t, c.fee, c.prizeCredits, c.pick, s.pick, c.rivalTier());
            ok &= check(c.fee > lastFee, "la entrada sube con el nivel");
            ok &= check(c.prizeCredits > c.fee, "el premio en creditos pasa de la entrada");
            ok &= check(s.pick != NeoQuestCup.PrizeCard.GAME_CHANGER, "en Estandar nunca un Game Changer");
            ok &= check((c.pick == NeoQuestCup.PrizeCard.GAME_CHANGER) == (t == QuestEventDifficulty.EXPERT),
                    "en Commander, el Game Changer solo en experto");
            lastFee = c.fee;
        }
        ok &= check(NeoQuestCup.gameChangers().size() >= 20, "la lista de Game Changers de Forge se lee ("
                + NeoQuestCup.gameChangers().size() + ")");
        for (final NeoQuestCup.PrizeCard k : NeoQuestCup.PrizeCard.values()) {
            final List<PaperCard> picks = NeoQuestCup.drawChoices(k);
            ok &= check(k == NeoQuestCup.PrizeCard.NONE ? picks.isEmpty() : picks.size() == 3,
                    "premio " + k + ": " + picks);
        }

        // 2. Commander: cuadro, guardado y premio.
        NeoQuest.delete(NAME);
        final List<Deck> starters = NeoQuest.starterDecks(NeoQuest.Modalidad.COMMANDER);
        if (!check(!starters.isEmpty(), "hay mazos de inicio de Commander")) {
            return false;
        }
        NeoQuest.start(NAME, NeoQuest.Modalidad.COMMANDER, NeoQuest.Dificultad.NORMAL, starters.get(0));
        final QuestController q = FModel.getQuest();
        q.getAssets().addCredits(50_000);
        ok &= check(NeoQuestCup.stage() == NeoQuestCup.Stage.OFFER, "sin pagar, se ofrece");
        ok &= check(NeoQuestCup.terms().tier == NeoQuest.tier(), "con tu nivel de ahora: " + NeoQuest.tier());
        final long before = NeoQuest.credits();
        ok &= check(NeoQuestCup.pay(), "se paga la entrada");
        ok &= check(NeoQuest.credits() == before - NeoQuestCup.terms().fee, "y se cobra una vez");
        ok &= check(!NeoQuestCup.pay(), "no se puede entrar dos veces");
        final List<String> seats = NeoQuestCup.seatNames();
        ok &= check(seats.size() == 64 && new HashSet<>(seats).size() == 64, "64 asientos distintos");
        ok &= check(NeoQuestCup.terms().rounds() == 3, "tres rondas: 16 mesas, 4 y la final");
        ok &= check(NeoQuestCup.groups(0).size() == 16, "dieciseis mesas en la primera ronda");
        ok &= check(NeoQuestCup.myRivals().size() == 3, "tres rivales en tu mesa: " + NeoQuestCup.rivalNames());
        final List<Deck> decks = NeoQuestCup.rivalDecks();
        ok &= check(decks.size() == 3, "sus mazos salen (" + decks.size() + ")");
        final File file = new File(ForgeConstants.QUEST_SAVE_DIR, NAME + ".torneo");
        ok &= check(file.isFile(), "se guarda junto a la Quest");

        // Una partida de verdad (con tope): la mesa arranca.
        final NeoQuestCupMatch.Outcome out = NeoQuestCupMatch.play(null, NeoMatchUI.Mode.AUTO_PLAY,
                true, null, secs);
        ok &= check(out != null && out.turns >= 1, "se juega la mesa a cuatro"
                + (out == null ? "" : " (" + out.turns + " turnos" + (out.won ? ", ganada)" : ")")));

        // Mirar otra mesa (28-10-2026): opcional, y quien la gana pasa de verdad.
        final List<Integer> watchable = NeoQuestCup.watchableTables();
        ok &= check(watchable.size() == 15, "las otras quince mesas se pueden mirar: " + watchable.size());
        final int table = watchable.get(0);
        ok &= check(NeoQuestCup.tableNames(table).size() == 4 && NeoQuestCup.tableDecks(table).size() == 4,
                "sus cuatro mazos salen: " + NeoQuestCup.tableNames(table));
        final Integer watched = NeoQuestCupMatch.watch(null, table, secs);
        System.out.println("  -- mesa mirada " + (table + 1) + ": " + (watched == null ? "sin acabar" : "gana el asiento " + watched));
        final int chosen = watched != null ? watched : NeoQuestCup.tableSeats(table).get(2);
        if (watched == null) {
            ok &= check(NeoQuestCup.recordWatched(table, chosen), "se apunta quien gana la mesa mirada");
        }
        ok &= check(!NeoQuestCup.recordWatched(table, NeoQuestCup.tableSeats(table).get(1)),
                "una mesa mirada no se vuelve a decidir");
        ok &= check(!NeoQuestCup.watchableTables().contains(table) && NeoQuestCup.watchableTables().size() == 14,
                "y ya no sale para mirar");
        ok &= check(NeoQuestCup.myRivals().size() == 3 && !NeoQuestCup.watchableTables().contains(
                NeoQuestCup.groups(0).indexOf(NeoQuestCup.groups(0).stream().filter(g -> g.contains(0)).findFirst().get())),
                "tu mesa no se mira, se juega");
        NeoQuestCup.forgetInMemoryForTest();
        ok &= check(Integer.valueOf(chosen).equals(NeoQuestCup.tableWinner(0, table)),
                "lo mirado se guarda en el disco");

        NeoQuestCup.Result r = NeoQuestCup.record(true);
        ok &= check(r != null && r.stage == NeoQuestCup.Stage.PLAYING && !NeoQuestCup.isFinal(),
                "ganar tu mesa te pasa a la segunda ronda");
        ok &= check(NeoQuestCup.winnersOf(0).size() == 16, "las otras mesas tambien tienen ganador");
        ok &= check(NeoQuestCup.winnersOf(0).get(table) == chosen, "y la mirada, el que la gano");
        ok &= check(NeoQuestCup.watchableTables().size() == 3, "en la segunda, otras tres mesas para mirar");
        ok &= check(NeoQuestCup.groups(1).size() == 4, "cuatro mesas en la segunda ronda");
        r = NeoQuestCup.record(true);
        ok &= check(r != null && r.stage == NeoQuestCup.Stage.PLAYING && NeoQuestCup.isFinal(),
                "ganar la segunda te lleva a la final");
        ok &= check(NeoQuestCup.groups(2).size() == 1 && NeoQuestCup.groups(2).get(0).contains(0),
                "la final es una mesa con los cuatro, tu dentro");
        ok &= check(NeoQuestCup.watchableTables().isEmpty(), "en la final no hay otra mesa que mirar");

        // Cerrar el juego a medias: se recupera tal cual.
        final List<Integer> finalists = NeoQuestCup.groups(2).get(0);
        NeoQuestCup.forgetInMemoryForTest();
        ok &= check(NeoQuestCup.stage() == NeoQuestCup.Stage.PLAYING && NeoQuestCup.isFinal()
                && NeoQuestCup.groups(2).get(0).equals(finalists) && NeoQuestCup.seatNames().equals(seats),
                "se recupera del disco donde estaba");

        final long beforePrize = NeoQuest.credits();
        r = NeoQuestCup.record(true);
        ok &= check(r != null && r.stage == NeoQuestCup.Stage.CHAMPION && r.prize != null, "ganar la final: campeon");
        ok &= check(r != null && r.prize != null && NeoQuest.credits() == beforePrize + r.prize.credits,
                "se cobran los creditos del premio");
        final NeoQuestCup.Terms terms = NeoQuestCup.terms();
        final int packs = r == null || r.prize == null ? 0 : r.prize.packs.size();
        ok &= check(packs == terms.boosters + terms.collectors, "y los sobres (" + packs + ")");
        if (terms.pick != NeoQuestCup.PrizeCard.NONE) {
            final List<PaperCard> choices = NeoQuestCup.pendingChoices();
            ok &= check(choices.size() == 3, "tres cartas a elegir");
            ok &= check(!NeoQuestCup.dismiss(), "sin elegir no se cierra");
            NeoQuestCup.forgetInMemoryForTest();
            ok &= check(NeoQuestCup.pendingChoices().size() == 3, "y la eleccion pendiente se guarda");
            ok &= check(NeoQuestCup.take(NeoQuestCup.pendingChoices().get(0)), "se elige una");
        }
        ok &= check(NeoQuestCup.dismiss() && NeoQuestCup.stage() == NeoQuestCup.Stage.OFFER && !file.isFile(),
                "cerrado, se ofrece el siguiente y el fichero se va");

        // 3. La final perdida devuelve la entrada; perder antes, no.
        NeoQuestCup.pay();
        NeoQuestCup.record(true);
        NeoQuestCup.record(true);
        final long beforeLoss = NeoQuest.credits();
        r = NeoQuestCup.record(false);
        ok &= check(r != null && r.stage == NeoQuestCup.Stage.RUNNER_UP
                && NeoQuest.credits() == beforeLoss + NeoQuestCup.terms().fee, "perder la final devuelve la entrada");
        NeoQuestCup.dismiss();
        NeoQuestCup.pay();
        final long beforeOut = NeoQuest.credits();
        r = NeoQuestCup.record(false);
        ok &= check(r != null && r.stage == NeoQuestCup.Stage.ELIMINATED && NeoQuest.credits() == beforeOut,
                "perder en la primera ronda: fuera, sin devolver");
        ok &= check(NeoQuestCup.winnersOf(2).size() == 1, "y el cuadro se completa hasta el campeon");
        NeoQuestCup.dismiss();
        // Un torneo de 16 guardado antes del 28-10-2026 sigue a 16 (y dos rondas).
        NeoQuestCup.pay();
        try {
            final List<String> lines = new java.util.ArrayList<>(
                    java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8));
            final String[] all = lines.get(4).split("\t", -1);
            lines.set(4, String.join("\t", java.util.Arrays.copyOf(all, 16)));
            java.nio.file.Files.write(file.toPath(), (String.join("\n", lines) + "\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (final java.io.IOException e) {
            ok &= check(false, "no se ha podido preparar el torneo de 16: " + e);
        }
        NeoQuestCup.forgetInMemoryForTest();
        ok &= check(NeoQuestCup.seatNames().size() == 16 && NeoQuestCup.terms().rounds() == 2
                && NeoQuestCup.groups(0).size() == 4 && NeoQuestCup.stage() == NeoQuestCup.Stage.PLAYING,
                "un torneo de 16 de antes se carga y sigue con sus dos rondas");
        NeoQuestCup.record(true);
        ok &= check(NeoQuestCup.isFinal() && NeoQuestCup.groups(1).size() == 1, "y su final es la segunda");
        NeoQuestCup.record(false);
        NeoQuestCup.dismiss();
        NeoQuestCup.pay();
        // Cerrar el juego a mitad de una ronda (Ana, 28-10-2026): al volver
        // cuenta como perdida; acabarla o que el motor reviente, no.
        NeoQuestCup.startPlaying();
        NeoQuestCup.crashed();
        ok &= check(!NeoQuestCup.settleInterrupted() && NeoQuestCup.stage() == NeoQuestCup.Stage.PLAYING,
                "si el motor revienta, la ronda se puede repetir");
        NeoQuestCup.startPlaying();
        ok &= check(NeoQuestCup.settleInterrupted() && NeoQuestCup.stage() == NeoQuestCup.Stage.ELIMINATED
                && NeoQuestCup.takeInterruptedNotice() && !NeoQuestCup.takeInterruptedNotice()
                && !NeoQuestCup.settleInterrupted(),
                "cerrar el juego a mitad de una ronda la da por perdida, y se avisa una vez");
        NeoQuestCup.dismiss();
        NeoQuestCup.pay();
        NeoQuest.delete(NAME);
        ok &= check(!file.isFile(), "borrar la Quest borra su torneo");

        // 4. Estandar: cuadro de 8 con los duelos de Forge, y una partida.
        NeoQuest.start(NAME, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                NeoQuest.colourStarter("neo-check-torneo-mazo", forge.neo.ascent.AscentSeedDeck.NO_COLOURS, null));
        FModel.getQuest().getAssets().addCredits(50_000);
        ok &= check(!NeoQuestCup.terms().commander, "en una Quest de Estandar, el torneo es construido");
        ok &= check(NeoQuestCup.deckProblem() == null, "el mazo de inicio vale");
        ok &= check(NeoQuestCup.pay(), "se entra");
        final Set<String> std = new HashSet<>(NeoQuestCup.seatNames());
        ok &= check(NeoQuestCup.seatNames().size() == 8 && std.size() == 8, "8 asientos distintos: " + std);
        ok &= check(NeoQuestCup.myRivals().size() == 1 && NeoQuestCup.rivalDecks().size() == 1,
                "un rival con su mazo: " + NeoQuestCup.rivalNames());
        final NeoQuestCupMatch.Outcome out2 = NeoQuestCupMatch.play(null, NeoMatchUI.Mode.AUTO_PLAY,
                true, null, secs);
        ok &= check(out2 != null && out2.turns >= 1, "se juega el uno contra uno"
                + (out2 == null ? "" : " (" + out2.turns + " turnos)"));
        NeoQuestCup.record(true);
        NeoQuestCup.record(true);
        ok &= check(NeoQuestCup.isFinal() && NeoQuestCup.stage() == NeoQuestCup.Stage.PLAYING,
                "tres rondas: cuartos, semifinal y final");
        r = NeoQuestCup.record(true);
        ok &= check(r != null && r.stage == NeoQuestCup.Stage.CHAMPION, "y se gana");
        return ok;
    }
}
