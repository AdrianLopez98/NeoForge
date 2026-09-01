package forge.neo.quest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.card.CardEdition;
import forge.gamemodes.quest.data.QuestPreferences.DifficultyPrefs;
import forge.gamemodes.quest.data.QuestPreferences.QPref;
import forge.gui.GuiBase;
import forge.item.BoosterPack;
import forge.model.FModel;
import forge.util.MyRandom;

/**
 * El sobre de premio por ganar un duelo.
 *
 * <p><b>Por que no lo da el motor.</b> Forge tambien regala un sobre cada N
 * victorias, pero para darlo te pregunta <i>"Choose bonus booster format"</i>:
 * Standard, Pauper, Modern, Legacy, Vintage. Eso, en mitad de la pantalla y
 * justo despues de ganar, no dice nada — un formato no es una cosa que se pueda
 * imaginar, y encima el sobre que sale es un revoltijo de cartas de ese formato,
 * no un sobre de ninguna expansion concreta.
 *
 * <p>Lo que se ensenya aqui en su lugar es una <b>mano de expansiones</b>: diez,
 * con su nombre y lo que costaria comprarlas en la tienda. Eso si es una
 * decision, y ademas engancha con el resto del modo — reconoces las expansiones
 * de la tienda, y ves cuando te ha tocado poder elegir una cara.
 *
 * <p>Las reglas del sorteo, tal y como se pidieron:
 *
 * <ul>
 *   <li><b>Diez</b> para elegir.</li>
 *   <li><b>Dos</b> salen siempre de las cinco expansiones mas recientes: que
 *       aparezca lo nuevo no puede depender de la suerte.</li>
 *   <li>Las otras ocho, al azar <b>pesadas por el precio</b>: cuanto mas caro es
 *       el sobre, menos veces aparece. Un sobre de 35.000 creditos sale unas
 *       cien veces menos que uno de 300, asi que verlo en la lista ya es el
 *       premio.</li>
 * </ul>
 *
 * <p><b>Nada de esto son reglas de juego</b>: es la economia del modo, que es
 * justo lo que la interfaz si decide. El sobre lo sigue generando el motor con
 * la plantilla real de la expansion, igual que en la tienda.
 */
public final class NeoQuestPrize {

    private NeoQuestPrize() {
    }

    /** Cuantas expansiones se ofrecen. */
    private static final int CHOICES = 10;

    /** De cuantas de las mas nuevas se garantiza alguna. */
    private static final int RECENT_WINDOW = 5;

    /** Cuantas de esa ventana entran seguro. */
    private static final int RECENT_GUARANTEED = 2;

    // ---------------------------------------------------------------
    // Apagar el sobre del motor
    // ---------------------------------------------------------------

    /**
     * La preferencia que decide cada cuantas victorias toca sobre.
     *
     * <p>Va por dificultad de la aventura: {@code WINS_BOOSTER_EASY},
     * {@code _MEDIUM}, {@code _HARD}, {@code _EXPERT}.
     */
    public static QPref boosterPref() {
        final int difficulty = FModel.getQuest().getAchievements().getDifficulty();
        final String suffix;
        switch (difficulty) {
            case 1: suffix = "_MEDIUM"; break;
            case 2: suffix = "_HARD"; break;
            case 3: suffix = "_EXPERT"; break;
            default: suffix = "_EASY"; break;
        }
        return QPref.valueOf(DifficultyPrefs.WINS_BOOSTER.toString() + suffix);
    }

    /** Cada cuantas victorias toca sobre, segun la dificultad de la aventura. */
    public static int winsPerPrize() {
        try {
            return FModel.getQuestPreferences().getPrefInt(
                    DifficultyPrefs.WINS_BOOSTER,
                    FModel.getQuest().getAchievements().getDifficulty());
        } catch (final RuntimeException e) {
            return 1;
        }
    }

    /**
     * Apaga el sobre del motor y devuelve lo que habia, para poder reponerlo.
     *
     * <p>{@code QuestWinLoseController} solo llama a {@code awardBooster()} si
     * esta preferencia es mayor que cero, asi que ponerla a cero es la forma de
     * quitarle el sobre <b>sin tocar un solo fichero de Forge</b>.
     *
     * <p>⚠️ Hay que reponerla ANTES de {@code actionOnQuit()}, porque esa
     * llamada hace {@code QuestPreferences.save()} — y ese fichero se comparte
     * con la instalacion normal de Forge del usuario.
     */
    public static String muteEngineBooster() {
        try {
            final QPref pref = boosterPref();
            final String was = FModel.getQuestPreferences().getPref(pref);
            FModel.getQuestPreferences().setPref(pref, "0");
            return was;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Repone lo que habia. Llamar SIEMPRE, y antes de que se guarde. */
    public static void unmuteEngineBooster(final String was) {
        if (was == null) {
            return;
        }
        try {
            FModel.getQuestPreferences().setPref(boosterPref(), was);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido reponer WINS_BOOSTER: " + e);
        }
    }

    /**
     * Toca sobre?
     *
     * <p>Misma cadencia que el motor, para no cambiar el ritmo del modo: se
     * cuenta con las victorias de ANTES de anotar esta, que es como lo hace
     * {@code QuestWinLoseController}.
     */
    public static boolean isDue(final boolean won) {
        if (!NeoQuest.isActive()) {
            return false;
        }
        final int difficulty = FModel.getQuest().getAchievements().getDifficulty();
        if (!won && difficulty != 0) {
            return false;
        }
        final int outcome = won ? NeoQuest.wins() : NeoQuest.losses();
        final int every = winsPerPrize();
        return every > 0 && (outcome + 1) % every == 0;
    }

    // ---------------------------------------------------------------
    // El sorteo
    // ---------------------------------------------------------------

    /** Las expansiones entre las que se elige, ya sorteadas. */
    public static List<CardEdition> choices() {
        final List<CardEdition> all = NeoQuestShop.editions();
        if (all.size() <= CHOICES) {
            return all;
        }

        final List<CardEdition> picked = new ArrayList<>();
        final Set<String> taken = new HashSet<>();

        // 1. Las nuevas, garantizadas. NeoQuestShop.editions() viene de la mas
        //    nueva a la mas vieja, asi que la ventana es el principio.
        final List<CardEdition> recent = new ArrayList<>(
                all.subList(0, Math.min(RECENT_WINDOW, all.size())));
        for (int i = 0; i < RECENT_GUARANTEED && !recent.isEmpty(); i++) {
            final CardEdition e = recent.remove(MyRandom.getRandom().nextInt(recent.size()));
            picked.add(e);
            taken.add(e.getCode());
        }

        // 2. El resto, pesadas por el precio: peso = 1/precio. Un sobre de
        //    35.000 sale ~100 veces menos que uno de 300.
        final List<CardEdition> pool = new ArrayList<>();
        final List<Double> weights = new ArrayList<>();
        double total = 0;
        for (final CardEdition e : all) {
            if (taken.contains(e.getCode())) {
                continue;
            }
            final double w = 1.0 / Math.max(1, NeoQuestShop.priceOf(NeoQuestShop.boosterOf(e)));
            pool.add(e);
            weights.add(w);
            total += w;
        }

        while (picked.size() < CHOICES && !pool.isEmpty()) {
            double roll = MyRandom.getRandom().nextDouble() * total;
            int idx = pool.size() - 1;
            for (int i = 0; i < pool.size(); i++) {
                roll -= weights.get(i);
                if (roll <= 0) {
                    idx = i;
                    break;
                }
            }
            picked.add(pool.remove(idx));
            total -= weights.remove(idx);
        }

        // 3. Barajar: si no, las dos nuevas salen siempre las primeras y el
        //    sorteo se lee como una lista ordenada en vez de como una mano.
        java.util.Collections.shuffle(picked, MyRandom.getRandom());
        return picked;
    }

    /**
     * Pregunta cual quieres y te lo da.
     *
     * <p>Sin ventana (los comprobadores) contesta {@code NeoGuiBase} con la
     * primera, asi que esto tambien funciona sin pantalla.
     *
     * @return lo que ha salido, o null si no se ha podido dar
     */
    public static NeoQuestShop.Opened award() {
        final List<CardEdition> options = choices();
        if (options.isEmpty()) {
            return null;
        }
        final List<CardEdition> answer = GuiBase.getInterface().getChoices(
                forge.neo.NeoText.get("quest.prize.ask"), 1, 1,
                options, null, NeoQuestPrize::label);
        final CardEdition chosen = answer == null || answer.isEmpty()
                ? options.get(0) : answer.get(0);

        final BoosterPack pack = NeoQuestShop.boosterOf(chosen);
        return pack == null ? null : NeoQuestShop.grantFree(pack);
    }

    /** "The Hobbit  ·  395 cr." — el precio dice lo bueno que es el premio. */
    private static String label(final CardEdition e) {
        return e.getName() + "  ·  " + NeoQuestShop.priceOf(NeoQuestShop.boosterOf(e)) + " cr.";
    }
}
