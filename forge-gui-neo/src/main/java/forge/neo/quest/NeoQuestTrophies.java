package forge.neo.quest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEventChallenge;
import forge.gamemodes.quest.data.QuestAchievements;
import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.skin.FSkinProp;

/**
 * <b>La vitrina de una Quest</b> (Discord, 25-10-2026: <i>"a list of challenges
 * won and other trophies (championship, tournaments)"</i>, despues de pedir ver
 * las mejoras del bazar).
 *
 * <p>Casi todo lo guarda ya Forge en {@code QuestAchievements}: los desafios de
 * una sola vez superados ({@code getLockedChallenges}, por id), los puestos en
 * los Campeonatos de draft ({@code getWinsForPlace}) y la mejor racha. Lo que
 * es NUESTRO no lo guardaba nadie, asi que se cuenta desde el 25-10-2026 en
 * {@code <quest>.vitrina}, junto a la partida (como el sobre del dia): las
 * victorias del Coliseo y las Copas ganadas y las finales perdidas.
 *
 * <p>Los dibujos son los de la vitrina de los logros de Forge
 * ({@code sprite_trophies.png}): un trofeo por desafio y oro, plata y bronce
 * para los podios. Las imagenes propias de cada desafio venian de
 * {@code downloads.cardforge.org}, que ya no existe.
 *
 * <p>Pura y en el jar: la usa tambien NeoForge Android.
 */
public final class NeoQuestTrophies {

    private NeoQuestTrophies() {
    }

    /**
     * Solo pruebas: {@code -Dneo.quest.demoTrophies=true} rellena la vitrina
     * en memoria (cuatro desafios, podios, Copas y Coliseo) para capturarla.
     */
    private static final boolean DEMO = Boolean.getBoolean("neo.quest.demoTrophies");

    /** Un desafio superado: su id, su titulo y lo que decia. */
    public record Challenge(String id, String title, String description) {
    }

    /** Lo de una sola vez que has superado, en el orden en que lo superaste. */
    public static List<Challenge> challenges() {
        final List<Challenge> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        final QuestController q = NeoQuest.engine();
        final List<String> ids = new ArrayList<>(q.getAchievements().getLockedChallenges());
        if (DEMO && ids.isEmpty()) {
            for (final QuestEventChallenge c : q.getChallenges()) {
                if (ids.size() < 4) {
                    ids.add(c.getId());
                }
            }
        }
        for (final String id : ids) {
            QuestEventChallenge c = null;
            try {
                c = q.getChallenges().get(id);
            } catch (final RuntimeException e) {
                // De otro mundo: se ensenya igual, con su numero.
            }
            final String title = c == null || c.getTitle() == null ? "#" + id : c.getTitle();
            out.add(new Challenge(id, title, c == null || c.getDescription() == null ? "" : c.getDescription()));
        }
        return out;
    }

    /** Las veces que quedaste 1.º, 2.º, 3.º y 4.º en un Campeonato (los drafts de la Quest). */
    public static int[] championshipPlaces() {
        final int[] out = new int[4];
        if (DEMO) {
            return new int[] {2, 1, 0, 3};
        }
        if (NeoQuest.isActive()) {
            final QuestAchievements a = NeoQuest.engine().getAchievements();
            for (int i = 0; i < 4; i++) {
                out[i] = a.getWinsForPlace(i + 1);
            }
        }
        return out;
    }

    public static int bestStreak() {
        return NeoQuest.isActive() ? NeoQuest.engine().getAchievements().getWinStreakBest() : 0;
    }

    public static int wins() {
        return NeoQuest.isActive() ? NeoQuest.engine().getAchievements().getWin() : 0;
    }

    public static int losses() {
        return NeoQuest.isActive() ? NeoQuest.engine().getAchievements().getLost() : 0;
    }

    public static int colosseumWins() {
        return read(NeoQuest.isActive() ? NeoQuest.name() : null, "colosseum");
    }

    public static int cupWins() {
        return read(NeoQuest.isActive() ? NeoQuest.name() : null, "cup");
    }

    public static int cupFinals() {
        return read(NeoQuest.isActive() ? NeoQuest.name() : null, "cupFinal");
    }

    /** Si hay algo que ensenyar (si no, la vitrina dice que esta vacia). */
    public static boolean isEmpty() {
        int places = 0;
        for (final int p : championshipPlaces()) {
            places += p;
        }
        return challenges().isEmpty() && places == 0 && colosseumWins() == 0 && cupWins() == 0
                && cupFinals() == 0 && NeoQuestBazaar.ownedUpgrades().isEmpty();
    }

    // ---------------------------------------------------------------
    // Lo que se cuenta desde ahora
    // ---------------------------------------------------------------

    /** Una victoria en el Coliseo de esa Quest. */
    static void noteColosseumWin(final String questName) {
        add(questName, "colosseum");
    }

    /** Una Copa ganada (o una final perdida) en esa Quest. */
    static void noteCup(final String questName, final boolean champion) {
        add(questName, champion ? "cup" : "cupFinal");
    }

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".vitrina");
    }

    private static Properties load(final String questName) {
        final Properties p = new Properties();
        if (questName == null) {
            return p;
        }
        final File f = fileOf(questName);
        if (f.isFile()) {
            try (java.io.Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (final java.io.IOException | RuntimeException e) {
                System.out.println("[vitrina] no se ha podido leer " + f + ": " + e);
            }
        }
        return p;
    }

    private static int read(final String questName, final String key) {
        if (DEMO) {
            return "cupFinal".equals(key) ? 0 : "cup".equals(key) ? 1 : 3;
        }
        try {
            return Integer.parseInt(load(questName).getProperty(key, "0").trim());
        } catch (final NumberFormatException e) {
            return 0;
        }
    }

    private static synchronized void add(final String questName, final String key) {
        if (questName == null) {
            return;
        }
        final Properties p = load(questName);
        p.setProperty(key, String.valueOf(read(questName, key) + 1));
        try (java.io.Writer w = Files.newBufferedWriter(fileOf(questName).toPath(), StandardCharsets.UTF_8)) {
            p.store(w, "Neo Forge: trophy case");
        } catch (final java.io.IOException | RuntimeException e) {
            System.out.println("[vitrina] no se ha podido apuntar " + key + ": " + e);
        }
    }

    /** Al borrar una Quest: su vitrina con ella. */
    static void deleteSaved(final String questName) {
        final File f = fileOf(questName);
        if (f.isFile()) {
            f.delete();
        }
    }

    // ---------------------------------------------------------------
    // Los dibujos: la lamina de trofeos de Forge
    // ---------------------------------------------------------------

    /** Que trofeo: el de cada desafio, y oro, plata, bronce y cuarto para los podios. */
    public enum Cup {
        CHALLENGE(FSkinProp.IMG_SPECIAL_TROPHY),
        GOLD(FSkinProp.IMG_RARE_TROPHY),
        SILVER(FSkinProp.IMG_UNCOMMON_TROPHY),
        BRONZE(FSkinProp.IMG_MYTHIC_TROPHY),
        FOURTH(FSkinProp.IMG_COMMON_TROPHY);

        private final FSkinProp prop;

        Cup(final FSkinProp prop) {
            this.prop = prop;
        }

        /** El recorte {@code {x, y, ancho, alto}} en {@link #spriteFile}. */
        public int[] rect() {
            return prop.getCoords() == null ? null : prop.getCoords().clone();
        }
    }

    /** La lamina de trofeos de Forge, la del skin de serie. */
    public static String spriteFile() {
        return ForgeConstants.DEFAULT_SKINS_DIR + ForgeConstants.SPRITE_TROPHIES_FILE;
    }
}
