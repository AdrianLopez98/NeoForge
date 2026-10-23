package forge.neo.quest;

import java.util.List;
import java.util.Locale;

import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.data.QuestPreferences;
import forge.model.FModel;

/**
 * Desbloquear expansiones en una Quest con expansiones elegidas
 * ({@link NeoQuestUnlock}), sin ventana: el caso del que lo pidio (9th Edition y
 * Ravnica, y luego Guildpact). {@code run.cmd questunlockcheck}.
 */
public final class QuestUnlockCheck {

    private QuestUnlockCheck() {
    }

    private static final String NAME = "neo-check-desbloqueo";

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
        System.out.println(ok ? "  OK - desbloquear expansiones en la Quest"
                : "  FALLO - revisa el desbloqueo de expansiones");
    }

    private static boolean check(final boolean good, final String what) {
        System.out.println("  " + (good ? "[ok]  " : "[MAL] ") + what);
        return good;
    }

    private static boolean runAll() {
        boolean ok = true;
        NeoQuest.delete(NAME);
        final forge.neo.ascent.AscentPool pool = forge.neo.ascent.AscentPool.sets(java.util.Arrays.asList("9ED", "RAV"));
        NeoQuest.start(NAME, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                NeoQuest.colourStarter("neo-check-desbloqueo-mazo", forge.neo.ascent.AscentSeedDeck.NO_COLOURS, null),
                null, pool);
        final QuestController q = FModel.getQuest();
        ok &= check(NeoQuestUnlock.available(), "una Quest con expansiones elegidas desbloquea");
        ok &= check(NeoQuestUnlock.tokens() == 0, "sin victorias, ningun desbloqueo");
        final int per = NeoQuestUnlock.winsPerUnlock();
        ok &= check(NeoQuestUnlock.winsToNext() == per, "el siguiente, en " + per + " victorias");
        for (int i = 0; i < per; i++) {
            q.getAchievements().addWin();
        }
        ok &= check(NeoQuestUnlock.tokens() == 1, "con " + per + " victorias, un desbloqueo");
        final List<NeoQuestUnlock.Offer> offers = NeoQuestUnlock.offers();
        ok &= check(!offers.isEmpty(), "hay expansiones que ofrecer (" + offers.size() + ")");
        boolean hasGuildpact = false;
        for (final NeoQuestUnlock.Offer o : offers.subList(0, Math.min(8, offers.size()))) {
            System.out.printf(Locale.ROOT, "     %s %s: %d%n", o.edition.getCode(), o.edition.getName(), o.price);
            hasGuildpact |= "GPT".equals(o.edition.getCode());
        }
        ok &= check(hasGuildpact, "Guildpact sale entre las mas cercanas");
        ok &= check(offers.stream().noneMatch(o -> pool.codes().contains(o.edition.getCode())),
                "no ofrece las que ya tienes");
        NeoQuestUnlock.Offer gpt = null;
        for (final NeoQuestUnlock.Offer o : offers) {
            if ("GPT".equals(o.edition.getCode())) {
                gpt = o;
            }
        }
        if (gpt == null) {
            return false;
        }
        q.getAssets().setCredits(gpt.price - 1);
        ok &= check(NeoQuestUnlock.unlock(gpt) == null, "sin creditos no se desbloquea");
        q.getAssets().setCredits(gpt.price + 500);
        final NeoQuestUnlock.Result r = NeoQuestUnlock.unlock(gpt);
        ok &= check(r != null, "con creditos, se desbloquea");
        ok &= check(NeoQuest.credits() == 500, "y se cobra (" + NeoQuest.credits() + ")");
        ok &= check(NeoQuest.chosenSets().contains("GPT"), "Guildpact entra en las expansiones de la Quest");
        ok &= check(NeoQuestShop.editions().stream().anyMatch(e -> "GPT".equals(e.getCode())),
                "y la tienda ya vende sus sobres");
        ok &= check(r != null && r.packs.size() == 3, "tres sobres de regalo ("
                + (r == null ? 0 : r.packs.size()) + ")");
        ok &= check(NeoQuestUnlock.tokens() == 0 && NeoQuestUnlock.used() == 1, "gastado el desbloqueo");
        ok &= check(NeoQuestUnlock.unlock(gpt) == null, "no se desbloquea dos veces");
        NeoQuest.save();
        ok &= check(NeoQuest.load(NAME) && NeoQuest.chosenSets().contains("GPT"), "se guarda con la Quest");

        // Una Quest con todas las expansiones: nada que desbloquear.
        NeoQuest.delete(NAME);
        NeoQuest.start(NAME, NeoQuest.Modalidad.ESTANDAR, NeoQuest.Dificultad.NORMAL,
                NeoQuest.colourStarter("neo-check-desbloqueo-mazo", forge.neo.ascent.AscentSeedDeck.NO_COLOURS, null),
                null, null);
        ok &= check(!NeoQuestUnlock.available(), "con todas las expansiones, no sale");
        return ok;
    }
}
