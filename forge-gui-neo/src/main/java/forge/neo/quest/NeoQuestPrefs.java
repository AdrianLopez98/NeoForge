package forge.neo.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import forge.gamemodes.quest.data.QuestPreferences;
import forge.gamemodes.quest.data.QuestPreferences.QPref;
import forge.model.FModel;
import forge.neo.NeoText;
import forge.util.Localizer;

/**
 * LAS PREFERENCIAS DE LA QUEST, las de Forge (Discord, 10-10-2026: <i>"Would you
 * be able to implement the full Preferences list as found in Forge? ... A return
 * to default settings would be good as well"</i>).
 *
 * <p>No hay ni una regla nuestra: son las {@code QPref} de
 * {@code QuestPreferences}, con sus valores de fabrica ({@code getDefault}) y su
 * validacion ({@code validatePreference}), y los rotulos son los de Forge
 * ({@code Localizer}, en los once idiomas — el arabe lo trae nuestro
 * {@code ar-MA.properties}). Se guardan donde las guarda Forge
 * ({@code quest.preferences}) y valen para todas las Quests, como alli.
 *
 * <h2>Solo las que hacen algo AQUI</h2>
 *
 * <p>Las listas de Forge (escritorio y movil) traen algunas que en NeoForge no
 * pintan nada, y un ajuste que no hace nada es peor que no tenerlo (principio 1):
 * <ul>
 *   <li>las proporciones del sobre ({@code BOOSTER_*}): solo las usa
 *       {@code QuestUtilCards.generateQuestBooster}, el sobre del motor, que aqui
 *       se apaga — el premio es un sobre de verdad de su expansion
 *       ({@link NeoQuestPrize});</li>
 *   <li>los sobres especiales ({@code SPECIAL_BOOSTERS}): van a la lista de la
 *       tienda del motor, de la que solo se cogen las sueltas;</li>
 *   <li>desbloquear expansiones y los drafts de la Quest: no existen aqui;</li>
 *   <li>los filtros por defecto de la GUI vieja.</li>
 * </ul>
 * Lo demas lo lee el motor por los caminos que usamos: el reparto de
 * {@code QuestWinLoseController}, la tienda y la venta de {@code QuestUtilCards},
 * los duelos, los desafios, las mascotas y el arranque de una Quest nueva.
 */
public final class NeoQuestPrefs {

    private NeoQuestPrefs() {
    }

    /** Como se escribe. */
    public enum Kind {
        /** Un entero. */
        NUMBER,
        /** Con decimales ({@code 0.3}, {@code 2.0}). */
        DECIMAL,
        /** 0 o 1: un interruptor. */
        SWITCH
    }

    /** Una preferencia: su clave de Forge y su rotulo. */
    public static final class Item {
        private final QPref pref;
        private final String labelKey;
        private final String tipKey;

        Item(final QPref pref, final String labelKey, final String tipKey) {
            this.pref = pref;
            this.labelKey = labelKey;
            this.tipKey = tipKey;
        }

        public QPref getPref() {
            return pref;
        }

        /** El rotulo de Forge, en tu idioma. */
        public String getLabel() {
            return forge(labelKey);
        }

        /** La clave del rotulo en el {@code Localizer} (para {@code QuestPrefsCheck}). */
        String getLabelKey() {
            return labelKey;
        }

        /** Su explicacion, si Forge la trae; si no, vacio. */
        public String getTip() {
            return tipKey == null ? "" : forge(tipKey);
        }

        public Kind getKind() {
            if (SWITCHES.contains(pref)) {
                return Kind.SWITCH;
            }
            return pref.getDefault().contains(".") ? Kind.DECIMAL : Kind.NUMBER;
        }

        /** Lo que vale ahora. */
        public String getValue() {
            return FModel.getQuestPreferences().getPref(pref);
        }

        public String getDefault() {
            return pref.getDefault();
        }

        /** Si esta como viene de fabrica (2.0 y 2 cuentan igual). */
        public boolean isDefault() {
            return same(getValue(), getDefault());
        }

        /** Para los interruptores. */
        public boolean isOn() {
            return "1".equals(getValue().trim());
        }
    }

    /** Un grupo, con el titulo de Forge. */
    public static final class Group {
        private final String titleKey;
        private final List<Item> items;

        Group(final String titleKey, final List<Item> items) {
            this.titleKey = titleKey;
            this.items = Collections.unmodifiableList(items);
        }

        public String getTitle() {
            return forge(titleKey);
        }

        /** La clave del titulo (para {@code QuestPrefsCheck}). */
        String getTitleKey() {
            return titleKey;
        }

        public List<Item> getItems() {
            return items;
        }
    }

    /** Las de 0 o 1 (validatePreference las limita a eso). */
    private static final java.util.Set<QPref> SWITCHES = java.util.EnumSet.of(
            QPref.EXCLUDE_PROMOS_FROM_POOL, QPref.MORE_DUEL_CHOICES, QPref.ITEM_LEVEL_RESTRICTION);

    private static final String[] DIFFICULTIES = {"EASY", "MEDIUM", "HARD", "EXPERT"};
    private static final String[] DIFFICULTY_TITLES = {
            "lblDifficultyAdjustmentsEasy", "lblDifficultyAdjustmentsMedium",
            "lblDifficultyAdjustmentsHard", "lblDifficultyAdjustmentsExpert"};

    /** Los grupos, en el orden de Forge. */
    public static List<Group> groups() {
        final List<Group> out = new ArrayList<>();
        out.add(new Group("lblRewards", java.util.Arrays.asList(
                item(QPref.REWARDS_BASE, "lblBaseWinnings"),
                item(QPref.REWARDS_UNDEFEATED, "lblNoLosses"),
                item(QPref.REWARDS_POISON, "lblPoisonWin"),
                item(QPref.REWARDS_MILLED, "lblMillingWin"),
                item(QPref.REWARDS_MULLIGAN0, "lblMulligan0Win"),
                item(QPref.REWARDS_ALTERNATIVE, "lblAlternativeWin"),
                item(QPref.REWARDS_WINS_MULTIPLIER, "lblBonusMultiplierperWin", "ttBonusMultiplierperWin"),
                item(QPref.REWARDS_WINS_MULTIPLIER_MAX, "lblMaxWinsforMultiplier", "ttMaxWinsforMultiplier"),
                item(QPref.REWARDS_TURN15, "lblWinbyTurn15"),
                item(QPref.REWARDS_TURN10, "lblWinbyTurn10"),
                item(QPref.REWARDS_TURN5, "lblWinbyTurn5"),
                item(QPref.REWARDS_TURN1, "lblFirstTurnWin"),
                item(QPref.REWARDS_HEALTH_DIFF_MAX, "lblMaxLifeDiffBonus"),
                item(QPref.EXCLUDE_PROMOS_FROM_POOL, "lblExcludePromosFromRewardPool"))));
        out.add(new Group("lblShopPreferences", java.util.Arrays.asList(
                item(QPref.SHOP_MAX_PACKS, "lblMaximumPacks"),
                item(QPref.SHOP_MIN_PACKS, "lblMinimumPacks"),
                item(QPref.SHOP_STARTING_PACKS, "lblStartingPacks"),
                item(QPref.SHOP_WINS_FOR_ADDITIONAL_PACK, "lblWinsforPack"),
                item(QPref.SHOP_SINGLES_COMMON, "lblCommonSingles"),
                item(QPref.SHOP_SINGLES_UNCOMMON, "lblUncommonSingles"),
                item(QPref.SHOP_SINGLES_RARE, "lblRareSingles"),
                item(QPref.SHOP_SELLING_PERCENTAGE_BASE, "lblCardSalePercentageBase"),
                item(QPref.SHOP_SELLING_PERCENTAGE_MAX, "lblCardSalePercentageCap"),
                item(QPref.SHOP_MAX_SELLING_PRICE, "lblCardSalePriceCap"),
                item(QPref.SHOP_WINS_FOR_NO_SELL_LIMIT, "lblWinstoUncapSalePrice"),
                item(QPref.PLAYSET_SIZE, "lblPlaysetSize", "ttPlaysetSize"),
                item(QPref.PLAYSET_BASIC_LAND_SIZE, "lblPlaysetSizeBasicLand", "ttPlaysetSizeBasicLand"),
                item(QPref.PLAYSET_ANY_NUMBER_SIZE, "lblPlaysetSizeAnyNumber", "ttPlaysetSizeAnyNumber"),
                item(QPref.ITEM_LEVEL_RESTRICTION, "lblItemLevelRestriction"))));
        out.add(new Group("lblDifficultyAdjustmentsAll", java.util.Arrays.asList(
                item(QPref.WINS_NEW_CHALLENGE, "lblWinsforNewChallenge"),
                item(QPref.STARTING_SNOW_LANDS, "lblStartingSnowLands"),
                item(QPref.STARTING_POOL_COLOR_BIAS, "lblColorBias", "ttColorBias"),
                item(QPref.PENALTY_LOSS, "lblPenaltyforLoss"),
                item(QPref.MORE_DUEL_CHOICES, "lblMoreDuelChoices"),
                item(QPref.WILD_OPPONENTS_MULTIPLIER, "lblWildOpponentMultiplier"),
                item(QPref.WILD_OPPONENTS_NUMBER, "lblWildOpponentNumber"))));
        for (int d = 0; d < DIFFICULTIES.length; d++) {
            final String s = "_" + DIFFICULTIES[d];
            out.add(new Group(DIFFICULTY_TITLES[d], java.util.Arrays.asList(
                    item(QPref.valueOf("WINS_BOOSTER" + s), "lblWinsForBooster"),
                    item(QPref.valueOf("WINS_RANKUP" + s), "lblWinsForRankIncrease"),
                    item(QPref.valueOf("WINS_MEDIUMAI" + s), "lblWinsForMediumAI"),
                    item(QPref.valueOf("WINS_HARDAI" + s), "lblWinsForHardAI"),
                    item(QPref.valueOf("WINS_EXPERTAI" + s), "lblWinsForExpertAI"),
                    item(QPref.valueOf("STARTING_COMMONS" + s), "lblStartingCommons"),
                    item(QPref.valueOf("STARTING_UNCOMMONS" + s), "lblStartingUncommons"),
                    item(QPref.valueOf("STARTING_RARES" + s), "lblStartingRares"),
                    item(QPref.valueOf("STARTING_CREDITS" + s), "lblStartingCredits"))));
        }
        return out;
    }

    /** Todas, en una lista. */
    public static List<Item> all() {
        final List<Item> out = new ArrayList<>();
        for (final Group g : groups()) {
            out.addAll(g.getItems());
        }
        return out;
    }

    /** El grupo de la dificultad de la Quest en curso (0-3), o -1 sin Quest. */
    public static int currentDifficulty() {
        try {
            return NeoQuest.isActive() ? FModel.getQuest().getAchievements().getDifficulty() : -1;
        } catch (final RuntimeException e) {
            return -1;
        }
    }

    /**
     * Pone un valor y lo guarda, o dice por que no.
     *
     * <p>Lo decide {@code QuestPreferences.validatePreference}, el juez de Forge.
     * Su mensaje viene en ingles, asi que se da el NUESTRO, con el limite que el
     * propio juez acepta (se le pregunta: ver {@link #minOf} y {@link #maxOf}).
     *
     * @return null si se ha guardado; si no, el motivo en tu idioma
     */
    public static String set(final Item item, final String text) {
        final String raw = text == null ? "" : text.trim().replace(',', '.');
        final QuestPreferences prefs = FModel.getQuestPreferences();
        final String value;
        if (item.getKind() == Kind.DECIMAL) {
            final double d;
            try {
                d = Double.parseDouble(raw);
            } catch (final NumberFormatException e) {
                return forge("lblEnteraNumber");
            }
            if (Double.isNaN(d) || Double.isInfinite(d) || d < 0) {
                return NeoText.get("questPrefs.min", 0);
            }
            value = trimDecimal(d);
        } else {
            final int n;
            try {
                n = Integer.parseInt(raw);
            } catch (final NumberFormatException e) {
                return forge("lblEnteraNumber");
            }
            if (prefs.validatePreference(item.getPref(), n) != null) {
                final Integer max = maxOf(item.getPref());
                return max == null ? NeoText.get("questPrefs.min", minOf(item.getPref()))
                        : NeoText.get("questPrefs.range", minOf(item.getPref()), max);
            }
            value = String.valueOf(n);
        }
        prefs.setPref(item.getPref(), value);
        prefs.save();
        return null;
    }

    /** Los interruptores. */
    public static void setOn(final Item item, final boolean on) {
        final QuestPreferences prefs = FModel.getQuestPreferences();
        prefs.setPref(item.getPref(), on ? "1" : "0");
        prefs.save();
    }

    /** Una, a como viene de fabrica. */
    public static void reset(final Item item) {
        final QuestPreferences prefs = FModel.getQuestPreferences();
        prefs.setPref(item.getPref(), item.getDefault());
        prefs.save();
    }

    /**
     * Todas las de la lista, a como vienen de fabrica. Las demas de
     * {@code quest.preferences} (la Quest en curso, el mazo...) no se tocan: no
     * son preferencias, son donde se quedo cada uno.
     *
     * @return cuantas estaban cambiadas
     */
    public static int resetAll() {
        final QuestPreferences prefs = FModel.getQuestPreferences();
        int changed = 0;
        for (final Item i : all()) {
            if (!i.isDefault()) {
                changed++;
            }
            prefs.setPref(i.getPref(), i.getDefault());
        }
        prefs.save();
        return changed;
    }

    /** Cuantas no estan como vienen de fabrica. */
    public static int changedCount() {
        int n = 0;
        for (final Item i : all()) {
            if (!i.isDefault()) {
                n++;
            }
        }
        return n;
    }

    /** El minimo que acepta el juez de Forge (0 o 1; el sesgo de color, 1). */
    static int minOf(final QPref pref) {
        final QuestPreferences prefs = FModel.getQuestPreferences();
        for (int v = 0; v <= 1; v++) {
            if (prefs.validatePreference(pref, v) == null) {
                return v;
            }
        }
        return 1;
    }

    /** El maximo que acepta, o null si no tiene (casi todas). */
    static Integer maxOf(final QPref pref) {
        final QuestPreferences prefs = FModel.getQuestPreferences();
        final int top = 1_000_000;
        if (prefs.validatePreference(pref, top) == null) {
            return null;
        }
        int lo = minOf(pref);
        int hi = top;
        while (lo < hi) {
            final int mid = (lo + hi + 1) >>> 1;
            if (prefs.validatePreference(pref, mid) == null) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private static Item item(final QPref pref, final String label) {
        return new Item(pref, label, null);
    }

    private static Item item(final QPref pref, final String label, final String tip) {
        return new Item(pref, label, tip);
    }

    private static String forge(final String key) {
        try {
            return Localizer.getInstance().getMessage(key).trim();
        } catch (final RuntimeException e) {
            return key;
        }
    }

    private static boolean same(final String a, final String b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.trim().equals(b.trim())) {
            return true;
        }
        try {
            return Double.parseDouble(a.trim()) == Double.parseDouble(b.trim());
        } catch (final NumberFormatException e) {
            return false;
        }
    }

    /** 2.0 → "2.0" (como lo escribe Forge), 0.35 → "0.35". */
    private static String trimDecimal(final double d) {
        final String s = String.format(Locale.ROOT, "%.4f", d);
        String t = s.replaceAll("0+$", "");
        if (t.endsWith(".")) {
            t = t + "0";
        }
        return t;
    }
}
