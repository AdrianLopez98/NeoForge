package forge.neo.quest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import forge.card.CardEdition;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.data.GameFormatQuest;
import forge.gamemodes.quest.data.QuestPreferences.QPref;
import forge.model.FModel;

/**
 * <b>Desbloquear expansiones en una Quest con expansiones elegidas</b>
 * (Discord, 23-10-2026: <i>"start with 9th Edition and Ravnica: City of Guilds,
 * and afterwards unlock Guildpact"</i>), como el Forge de siempre.
 *
 * <p>Las reglas son del motor y se leen, no se copian: cuantos desbloqueos
 * tienes ({@code QuestController.getUnlocksTokens}: uno cada
 * {@code WINS_UNLOCK_SET} victorias, 20 de fabrica), que expansiones siguen
 * cerradas ({@code GameFormatQuest.getLockedSets}) y el desbloqueo en si
 * ({@code unlockSet}, que ya cuenta los usados). Lo unico que se rehace aqui es
 * lo que en Forge vive en {@code QuestUtilUnlockSets} junto a sus dialogos de
 * Swing (no se pueden llamar desde esta interfaz): el ORDEN (las mas cercanas
 * en fecha a las que ya tienes, primero), el PRECIO (4.000 creditos o mas
 * segun lo que cuesta su sobre, y mas caro cuanto mas lejos) y los sobres de
 * regalo.
 *
 * <p>Solo vale en las Quest creadas con "desbloquear" encendido: el formato
 * guarda si se permite al crearse y no se puede cambiar despues.
 *
 * <p>Sin JavaFX: lo usa tambien NeoForge Android.
 */
public final class NeoQuestUnlock {

    private NeoQuestUnlock() {
    }

    /** Lo de Forge: precio minimo. */
    private static final int UNLOCK_COST = 4000;
    /** Lo de Forge: solo expansiones "de verdad". */
    private static final EnumSet<CardEdition.Type> UNLOCKABLE = EnumSet.of(
            CardEdition.Type.CORE, CardEdition.Type.EXPANSION, CardEdition.Type.REPRINT, CardEdition.Type.STARTER);
    /** Cuantas se ofrecen como mucho en la lista (las mas cercanas). */
    private static final int MAX_OFFERS = 40;

    /** Una expansion que se puede desbloquear, con su precio. */
    public static final class Offer {
        public final CardEdition edition;
        public final int price;

        Offer(final CardEdition edition, final int price) {
            this.edition = edition;
            this.price = price;
        }
    }

    private static GameFormatQuest format() {
        final QuestController q = FModel.getQuest();
        return q == null ? null : q.getMainFormat();
    }

    /** Si esta Quest desbloquea expansiones (se eligieron al crearla, con desbloqueo). */
    public static boolean available() {
        if (!NeoQuest.isActive()) {
            return false;
        }
        final GameFormatQuest f = format();
        return f != null && f.canUnlockSets() && !f.getAllowedSetCodes().isEmpty();
    }

    /** Cuantos desbloqueos tienes ahora (los cuenta el motor). */
    public static int tokens() {
        if (!available()) {
            return 0;
        }
        try {
            return FModel.getQuest().getUnlocksTokens();
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /** Victorias por desbloqueo (preferencia de la Quest). */
    public static int winsPerUnlock() {
        return Math.max(1, FModel.getQuestPreferences().getPrefInt(QPref.WINS_UNLOCK_SET));
    }

    /** Cuantas victorias faltan para el siguiente, o 0 si ya tienes alguno. */
    public static int winsToNext() {
        if (!available() || tokens() > 0) {
            return 0;
        }
        final int per = winsPerUnlock();
        final int wins = NeoQuest.wins();
        final int used = format().getUnlocksUsed();
        return Math.max(1, (used + 1) * per - wins);
    }

    /** Cuantas has desbloqueado ya. */
    public static int used() {
        return available() ? format().getUnlocksUsed() : 0;
    }

    /**
     * Las que se pueden desbloquear, las mas cercanas en fecha primero, con su
     * precio: como {@code QuestUtilUnlockSets.chooseSetToUnlock}.
     */
    public static List<Offer> offers() {
        final List<Offer> out = new ArrayList<>();
        if (!available()) {
            return out;
        }
        final GameFormatQuest f = format();
        final CardEdition.Collection eds = FModel.getMagicDb().getEditions();
        final List<CardEdition> allowed = new ArrayList<>();
        for (final String code : f.getAllowedSetCodes()) {
            final CardEdition e = eds.get(code);
            if (e != null && e.getDate() != null) {
                allowed.add(e);
            }
        }
        final List<long[]> byDistance = new ArrayList<>();
        final List<CardEdition> locked = new ArrayList<>();
        for (final String code : f.getLockedSets()) {
            final CardEdition ex = eds.get(code);
            if (ex == null || ex.getDate() == null || !UNLOCKABLE.contains(ex.getType())) {
                continue;
            }
            long distance = Long.MAX_VALUE;
            for (final CardEdition in : allowed) {
                distance = Math.min(distance, Math.abs(ex.getDate().getTime() - in.getDate().getTime()));
            }
            byDistance.add(new long[] {distance, locked.size()});
            locked.add(ex);
        }
        byDistance.sort((a, b) -> Long.compare(a[0], b[0]));
        final Map<String, Integer> prices = new forge.gamemodes.quest.io.ReadPriceList().getPriceList();
        final double step = parse(FModel.getQuestPreferences().getPref(QPref.UNLOCK_DISTANCE_MULTIPLIER), 1.25);
        double multiplier = 1d;
        int j = 0;
        for (final long[] d : byDistance) {
            if (out.size() >= MAX_OFFERS) {
                break;
            }
            final CardEdition ed = locked.get((int) d[1]);
            j++;
            if (j > 5) {
                multiplier = Math.min(500d, multiplier * step);
            }
            int price = UNLOCK_COST;
            final Integer booster = prices.get(ed.getName() + " Booster Pack");
            if (booster != null) {
                price = Math.max((int) (30 * Math.pow(Math.sqrt(booster), 1.70)), UNLOCK_COST);
            }
            out.add(new Offer(ed, (int) (price * multiplier)));
        }
        return out;
    }

    private static double parse(final String s, final double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (final RuntimeException e) {
            return fallback;
        }
    }

    /** Lo que ha pasado al desbloquear: los sobres de regalo, ya en tu coleccion. */
    public static final class Result {
        public final CardEdition edition;
        public final int paid;
        public final List<NeoQuestShop.Opened> packs;

        Result(final CardEdition edition, final int paid, final List<NeoQuestShop.Opened> packs) {
            this.edition = edition;
            this.paid = paid;
            this.packs = packs;
        }
    }

    /**
     * Desbloquea esa expansion: cobra, la abre (y la que vaya con ella, como
     * Forge) y regala tres sobres suyos si los hay.
     *
     * @return null si no se puede (sin desbloqueos, sin creditos, o ya abierta)
     */
    public static synchronized Result unlock(final Offer offer) {
        if (offer == null || tokens() < 1) {
            return null;
        }
        boolean listed = false;
        for (final Offer o : offers()) {
            if (o.edition.getCode().equals(offer.edition.getCode())) {
                listed = true;
                break;
            }
        }
        if (!listed || NeoQuest.credits() < offer.price) {
            return null;
        }
        final QuestController q = FModel.getQuest();
        q.getAssets().subtractCredits(offer.price);
        final GameFormatQuest f = format();
        f.unlockSet(offer.edition.getCode());
        final String extra = offer.edition.getAdditionalUnlockSet();
        if (extra != null && !extra.isEmpty()) {
            f.unlockSet(extra);
        }
        final List<NeoQuestShop.Opened> packs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            try {
                final forge.item.BoosterPack pack = NeoQuestShop.boosterOf(offer.edition);
                if (pack == null) {
                    break;
                }
                final NeoQuestShop.Opened got = NeoQuestShop.grantFree(pack);
                if (got != null) {
                    packs.add(got);
                }
            } catch (final RuntimeException e) {
                System.out.println("[desbloqueo] el sobre de " + offer.edition.getCode() + " no sale: " + e);
                break;
            }
        }
        q.save();
        System.out.printf(java.util.Locale.ROOT, "[desbloqueo] %s por %d creditos, %d sobres%n",
                offer.edition.getCode(), offer.price, packs.size());
        return new Result(offer.edition, offer.price, packs);
    }
}
