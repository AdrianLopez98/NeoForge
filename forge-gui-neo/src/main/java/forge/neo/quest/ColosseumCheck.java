package forge.neo.quest;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.gamemodes.quest.data.QuestPreferences;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.match.NeoMatchUI;

/**
 * El Coliseo de la Quest de Commander, sin ventana ({@link NeoColosseum}).
 *
 * <ol>
 *   <li>Solo existe en una Quest de Commander.</li>
 *   <li>En cada nivel de la Quest: la entrada sube, el premio es el doble, los
 *       rivales van un nivel por delante (como mucho experto), son tres
 *       comandantes distintos que no son el tuyo, NO cambian al volver a
 *       mirar, y sus mazos salen legales para Commander.</li>
 *   <li>Los rivales y el sobre sobreviven a reiniciar el juego (guardados
 *       junto a la Quest, y borrados con ella).</li>
 *   <li>Pagar cobra; sin dinero no se paga; devolver devuelve.</li>
 *   <li>Ganar: el doble de la entrada, un sobre de colector en la coleccion,
 *       una victoria de la Quest y rivales nuevos. Perder: ni victoria ni
 *       derrota apuntadas, y rivales nuevos. Y una entrada se liquida UNA
 *       vez: despues no se cobra, pierde ni devuelve otra.</li>
 *   <li>Una partida DE VERDAD a cuatro con el piloto automatico (con tope de
 *       {@code -Dneo.colosseum.matchSecs}, 90 s): se sienta y se juega.</li>
 * </ol>
 *
 * <p>{@code run.cmd colosseumcheck}. {@code -Dneo.colosseum.realMatch=false}
 * se salta la partida (para iterar rapido).
 */
public final class ColosseumCheck {

    private ColosseumCheck() {
    }

    private static final String NAME = "neo-check-coliseo";

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
                ? "  OK - Coliseo: precio, rivales, mazos, premio y partida a cuatro"
                : "  FALLO - revisa el Coliseo de la Quest");
    }

    private static boolean check(final boolean good, final String what) {
        System.out.println("  " + (good ? "[ok]  " : "[MAL] ") + what);
        return good;
    }

    private static boolean runAll() {
        boolean ok = true;

        // 1. Solo en Commander
        NeoQuest.delete(NAME);
        NeoQuest.start(NAME, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                NeoQuest.colourStarter("neo-check-coliseo-mazo", forge.neo.ascent.AscentSeedDeck.NO_COLOURS, null));
        ok &= check(!NeoColosseum.available(), "en una Quest de Estandar no hay Coliseo");
        NeoQuest.delete(NAME);
        final List<Deck> starters = NeoQuest.starterDecks(NeoQuest.Modalidad.COMMANDER);
        if (!check(!starters.isEmpty(), "hay mazos de inicio de Commander")) {
            return false;
        }
        NeoQuest.start(NAME, NeoQuest.Modalidad.COMMANDER, NeoQuest.Dificultad.NORMAL, starters.get(0));
        final QuestController q = FModel.getQuest();
        ok &= check(NeoColosseum.available(), "en una de Commander si");
        ok &= check(NeoColosseum.deckProblem() == null, "el mazo de inicio vale para entrar");
        final String mine = commanderOf(NeoQuest.currentDeck());

        // 2. Cada nivel de la Quest
        int lastFee = 0;
        for (int level = 0; level < 4; level++) {
            final QuestEventDifficulty tier = NeoQuest.tier();
            final QuestEventDifficulty rivals = NeoColosseum.rivalTier();
            final int fee = NeoColosseum.fee();
            System.out.printf(Locale.ROOT, "  -- Quest %s (%d victorias): entrada %d, rivales %s%n",
                    tier, NeoQuest.wins(), fee, rivals);
            ok &= check(fee > lastFee, "la entrada sube con el nivel: " + fee);
            ok &= check(NeoColosseum.prizeCredits() == fee * 2, "el premio es el doble");
            // Mas barato desde el 28-10-2026: el sobre de colector, solo en dificil y experto.
            ok &= check(NeoColosseum.collectorPrize() == (tier == QuestEventDifficulty.HARD
                    || tier == QuestEventDifficulty.EXPERT), "sobre de colector solo en dificil y experto");
            ok &= check(fee <= 1000, "la entrada asequible (como mucho 1000): " + fee);
            ok &= check(rivals == next(tier), "los rivales van un nivel por delante: " + rivals);
            lastFee = fee;

            final List<PaperCard> lineup = NeoColosseum.lineup();
            final Set<String> names = new HashSet<>();
            boolean known = true;
            for (final PaperCard c : lineup) {
                names.add(c.getName());
                known &= forge.neo.ascent.AscentSynergy.knows(c);
            }
            ok &= check(lineup.size() == NeoColosseum.RIVALS && names.size() == NeoColosseum.RIVALS,
                    "tres comandantes distintos: " + names);
            ok &= check(!names.contains(mine), "ninguno es el tuyo (" + mine + ")");
            ok &= check(known, "todos los conoce la matriz de mazos reales");
            ok &= check(NeoColosseum.lineup() == lineup, "volver a mirar no los cambia");
            ok &= check(NeoColosseum.prizeEdition() != null
                    && NeoQuestShop.collectorEditions().contains(NeoColosseum.prizeEdition()),
                    "el sobre del premio es de una expansion con colector: "
                            + (NeoColosseum.prizeEdition() == null ? "-" : NeoColosseum.prizeEdition().getCode()));

            if (tier != QuestEventDifficulty.EXPERT) {
                final long t0 = System.currentTimeMillis();
                final List<Deck> decks = NeoColosseum.buildDecks(lineup);
                boolean legal = decks.size() == NeoColosseum.RIVALS;
                final StringBuilder sb = new StringBuilder();
                for (final Deck d : decks) {
                    legal &= DeckFormat.Commander.getDeckConformanceProblem(d) == null;
                    sb.append(commanderOf(d)).append(" (").append(d.getMain().countAll()).append(") ");
                }
                ok &= check(legal, String.format(Locale.ROOT, "tres mazos legales de nivel %s en %d ms: %s",
                        rivals, System.currentTimeMillis() - t0, sb.toString().trim()));
            }

            // Al siguiente nivel, a base de victorias
            int guard = 0;
            while (NeoQuest.tier() == tier && tier != QuestEventDifficulty.EXPERT && guard++ < 500) {
                q.getAchievements().addWin();
            }
            if (tier == QuestEventDifficulty.EXPERT) {
                break;
            }
        }

        // 3. Los rivales sobreviven a reiniciar el juego (se guardan con la Quest)
        final List<PaperCard> saved = NeoColosseum.lineup();
        final forge.card.CardEdition savedEd = NeoColosseum.prizeEdition();
        NeoColosseum.forgetInMemoryForTest();
        ok &= check(names(NeoColosseum.lineup()).equals(names(saved))
                && NeoColosseum.prizeEdition() == savedEd, "reiniciar no cambia los rivales ni el sobre");

        // 4. Pagar, no poder pagar y devolver (una vez)
        q.getAssets().addCredits(100_000);
        long before = q.getAssets().getCredits();
        final int fee = NeoColosseum.fee();
        ok &= check(NeoColosseum.pay() && q.getAssets().getCredits() == before - fee
                && NeoColosseum.hasPending(), "pagar cobra la entrada y la deja pendiente");
        ok &= check(NeoColosseum.refund() && q.getAssets().getCredits() == before, "devolver la devuelve");
        ok &= check(!NeoColosseum.refund() && q.getAssets().getCredits() == before,
                "y no se devuelve dos veces");
        ok &= check(names(NeoColosseum.lineup()).equals(names(saved)), "devolver no cambia los rivales");
        q.getAssets().subtractCredits(q.getAssets().getCredits() - (fee - 1));
        ok &= check(!NeoColosseum.canAfford() && !NeoColosseum.pay()
                && q.getAssets().getCredits() == fee - 1, "sin dinero no se entra (ni se cobra)");
        q.getAssets().addCredits(100_000);

        // 5. Ganar
        before = q.getAssets().getCredits();
        int wins = NeoQuest.wins();
        final int lost = q.getAchievements().getLost();
        List<PaperCard> old = NeoColosseum.lineup();
        NeoColosseum.pay();
        final NeoColosseum.Prize prize = NeoColosseum.win();
        ok &= check(prize != null && q.getAssets().getCredits() == before - fee + 2L * fee,
                "ganar paga el doble de la entrada: " + (prize == null ? "-" : prize.credits));
        ok &= check(NeoQuest.wins() == wins + 1, "y cuenta como una victoria de la Quest");
        boolean inPool = prize != null && prize.pack != null && !prize.pack.getCards().isEmpty();
        if (inPool) {
            for (final PaperCard c : prize.pack.getCards()) {
                inPool &= q.getCards().getCardpool().count(c) > 0;
            }
        }
        ok &= check(inPool, "el sobre de colector entra en la coleccion: "
                + (prize == null || prize.pack == null ? "nada" : prize.pack.getCards().size() + " cartas"));
        ok &= check(NeoColosseum.lineup() != old, "y hay rivales nuevos");
        final long afterWin = q.getAssets().getCredits();
        ok &= check(NeoColosseum.win() == null && !NeoColosseum.lose() && !NeoColosseum.refund()
                && q.getAssets().getCredits() == afterWin && NeoQuest.wins() == wins + 1,
                "una partida liquidada no se vuelve a cobrar, perder ni devolver");

        // 6. Perder
        wins = NeoQuest.wins();
        old = NeoColosseum.lineup();
        before = q.getAssets().getCredits();
        NeoColosseum.pay();
        ok &= check(NeoColosseum.lose() && NeoQuest.wins() == wins && q.getAchievements().getLost() == lost
                && q.getAssets().getCredits() == before - fee, "perder cuesta la entrada, sin apuntar derrota");
        ok &= check(NeoColosseum.lineup() != old, "y hay rivales nuevos");
        ok &= check(!NeoColosseum.refund() && q.getAssets().getCredits() == before - fee,
                "y despues no se puede devolver");

        // 7. Borrar la Quest se lleva sus rivales guardados
        final java.io.File side = new java.io.File(forge.localinstance.properties.ForgeConstants.QUEST_SAVE_DIR,
                NAME + ".coliseo");
        ok &= check(side.isFile(), "los rivales estan guardados junto a la Quest");
        final List<PaperCard> beforeMatch = NeoColosseum.lineup();

        // 8. Una partida de verdad a cuatro
        if (!"false".equals(System.getProperty("neo.colosseum.realMatch"))) {
            final int secs = Integer.getInteger("neo.colosseum.matchSecs", 90);
            final NeoColosseumMatch.Outcome out = NeoColosseumMatch.play(null, NeoMatchUI.Mode.AUTO_PLAY,
                    true, null, secs);
            ok &= check(out != null && out.turns >= 1, "se juega la partida a cuatro"
                    + (out == null ? "" : " (" + out.turns + " turnos" + (out.won ? ", ganada)" : ")")));
        }
        ok &= check(names(NeoColosseum.lineup()).equals(names(beforeMatch)),
                "jugar sin liquidar no cambia los rivales");
        NeoQuest.delete(NAME);
        ok &= check(!side.isFile(), "y borrar la Quest los borra");
        return ok;
    }

    private static List<String> names(final List<PaperCard> cards) {
        final List<String> out = new java.util.ArrayList<>();
        for (final PaperCard c : cards) {
            out.add(c.getName());
        }
        return out;
    }

    private static QuestEventDifficulty next(final QuestEventDifficulty t) {
        switch (t) {
            case EASY:
                return QuestEventDifficulty.MEDIUM;
            case MEDIUM:
                return QuestEventDifficulty.HARD;
            default:
                return QuestEventDifficulty.EXPERT;
        }
    }

    private static String commanderOf(final Deck d) {
        if (d != null && d.has(DeckSection.Commander)) {
            for (final PaperCard c : d.get(DeckSection.Commander).toFlatList()) {
                return c.getName();
            }
        }
        return "";
    }
}
