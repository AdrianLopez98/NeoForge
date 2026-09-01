package forge.neo.quest;

import java.util.ArrayList;
import java.util.List;

import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestUtil;
import forge.gamemodes.quest.bazaar.IQuestBazaarItem;
import forge.gamemodes.quest.bazaar.QuestItemType;
import forge.gamemodes.quest.bazaar.QuestPetController;
import forge.gamemodes.quest.bazaar.QuestStallDefinition;

/**
 * El bazar de la aventura, envuelto.
 *
 * <p>Y otra vez la misma historia: <b>estaba entero y no se le habia pedido</b>.
 * Forge trae {@code res/quest/bazaar/index.xml} con seis puestos, once mejoras
 * permanentes y cinco mascotas de cuatro niveles cada una, y
 * {@code QuestBazaarManager} las carga, sabe cuales puedes comprar hoy y cuanto
 * valen. Comprar es <b>una llamada</b>: {@code QuestUtil.buyQuestItem}, que
 * cobra, aplica el efecto y guarda.
 *
 * <p><b>Que hace el bazar, en concreto</b> — y es mas de lo que parece un
 * "puesto de adornos":
 *
 * <ul>
 *   <li><b>Elixir of Life</b> sube tus vidas de partida para siempre.</li>
 *   <li><b>Map</b> y <b>Zeppelin</b> acortan lo que tardan en llegar los
 *       desafios ({@code QuestController.getTurnsToUnlockChallenge}: -1 y -2
 *       victorias).</li>
 *   <li><b>Estates</b> mejora lo que te pagan al vender
 *       ({@code QuestUtilCards.getSellMultiplier}).</li>
 *   <li><b>Sleight</b> te regala la primera cartada del mulligan, y eso lo
 *       aplica el motor <b>dentro de la partida</b>
 *       ({@code QuestController.receiveGameEvent}).</li>
 *   <li><b>Las mascotas</b> son una criatura que empieza contigo en la mesa. Se
 *       suben de nivel comprando el nivel siguiente, y llevas <b>dos</b> a la
 *       vez ({@code MAX_PET_SLOTS}).</li>
 * </ul>
 *
 * <p>O sea que el bazar no es cosmetico: toca las vidas, el ritmo de los
 * desafios, el precio de venta y la propia partida. Por eso merece pantalla.
 *
 * <p>Aqui no hay ni una regla nuestra. Lo unico que se anyade es no pedirle
 * <b>iconos</b>: {@code IQuestBazaarItem.getIcon} devuelve un
 * {@code ISkinImage}, o sea el sistema de skins de la GUI vieja, y eso es
 * justo lo que no queremos arrastrar.
 */
public final class NeoQuestBazaar {

    private NeoQuestBazaar() {
    }

    /** Un puesto del bazar. */
    public static final class Stall {
        private final String name;
        private final String title;
        private final String fluff;
        private final List<IQuestBazaarItem> items;

        Stall(final String name, final String title, final String fluff,
              final List<IQuestBazaarItem> items) {
            this.name = name;
            this.title = title;
            this.fluff = fluff;
            this.items = items;
        }

        /** El nombre interno, que es el que usa el motor. */
        public String getName() {
            return name;
        }

        /** Como se llama de cara al jugador ("Bank of Sarpadia"). */
        public String getTitle() {
            return title;
        }

        /** Su parrafo de ambientacion. Lo escribe Forge y esta bien escrito. */
        public String getFluff() {
            return fluff;
        }

        /** Lo que se puede comprar HOY en el. Puede estar vacio. */
        public List<IQuestBazaarItem> getItems() {
            return items;
        }
    }

    /**
     * Los puestos, con lo que venden hoy.
     *
     * <p>Ojo con una cosa: la lista de un puesto <b>cambia sola</b>. Cada
     * objeto decide si esta a la venta ({@code isAvailableForPurchase}), asi
     * que las mejoras que ya has comprado desaparecen y las mascotas van
     * ensenyando el nivel siguiente. Hay que volver a pedirla despues de cada
     * compra o el mostrador se queda con lo de antes.
     */
    public static List<Stall> stalls() {
        final List<Stall> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        final QuestController q = NeoQuest.engine();
        try {
            for (final String name : q.getBazaar().getStallNames()) {
                final QuestStallDefinition def = q.getBazaar().getStall(name);
                final List<IQuestBazaarItem> items =
                        new ArrayList<>(q.getBazaar().getItems(q, name));
                out.add(new Stall(name,
                        def == null || def.getDisplayName() == null ? name : def.getDisplayName(),
                        def == null ? "" : def.getFluff(),
                        items));
            }
        } catch (final RuntimeException e) {
            System.err.println("[bazar] no se ha podido leer el bazar: " + e);
        }
        return out;
    }

    public static int priceOf(final IQuestBazaarItem item) {
        if (item == null || !NeoQuest.isActive()) {
            return 0;
        }
        return item.getBuyingPrice(NeoQuest.engine().getAssets());
    }

    public static String descriptionOf(final IQuestBazaarItem item) {
        if (item == null || !NeoQuest.isActive()) {
            return "";
        }
        final String text = item.getPurchaseDescription(NeoQuest.engine().getAssets());
        return text == null ? "" : text;
    }

    public static boolean canAfford(final IQuestBazaarItem item) {
        final int price = priceOf(item);
        return NeoQuest.isActive() && price >= 0 && NeoQuest.credits() >= price;
    }

    /**
     * Compra una mejora o una mascota.
     *
     * <p>Todo lo hace {@code QuestUtil.buyQuestItem}: cobra, aplica el efecto
     * y guarda. Comprueba el dinero por su cuenta, pero se comprueba tambien
     * aqui para poder <b>decirlo</b> — el metodo del motor, si no te llega, no
     * hace nada y no devuelve nada, o sea que el boton pareceria roto.
     *
     * @return true si se ha comprado
     */
    public static boolean buy(final IQuestBazaarItem item) {
        if (!canAfford(item)) {
            return false;
        }
        final long before = NeoQuest.credits();
        QuestUtil.buyQuestItem(item);
        final boolean done = NeoQuest.credits() != before || priceOf(item) == 0;
        if (done) {
            System.out.printf(java.util.Locale.ROOT,
                    "[bazar] %s por %d cr., quedan %d%n",
                    item.getPurchaseName(), before - NeoQuest.credits(), NeoQuest.credits());
        }
        return done;
    }

    // ===============================================================
    // Las mascotas
    // ===============================================================

    /** Cuantas mascotas puedes llevar a la vez. Lo dice el motor: dos. */
    public static int petSlots() {
        return QuestController.MAX_PET_SLOTS;
    }

    /**
     * Las mascotas que tienes compradas para ese hueco, al nivel que tengas.
     *
     * <p>El almacen guarda TODAS las mascotas de todos los niveles; las tuyas
     * son las que tienen nivel comprado ({@code getLevel > 0}). Ensenyar las
     * demas seria ofrecerte elegir algo que no tienes.
     */
    public static List<QuestPetController> ownedPets(final int slot) {
        final List<QuestPetController> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        final QuestController q = NeoQuest.engine();
        for (final QuestPetController pet : q.getPetsStorage().getAllPets(slot)) {
            if (pet != null && q.getAssets().getPetLevel(pet.getSaveFileKey()) > 0) {
                out.add(pet);
            }
        }
        return out;
    }

    /** Cual llevas puesta en ese hueco, o null. */
    public static String selectedPet(final int slot) {
        return NeoQuest.isActive() ? NeoQuest.engine().getSelectedPet(slot) : null;
    }

    /**
     * Elige (o quita, con null) la mascota de un hueco.
     *
     * <p>Se guarda al momento a proposito: es una decision que cambia la
     * proxima partida, y perderla por salir de la pantalla seria de las cosas
     * que no se entienden.
     */
    public static void selectPet(final int slot, final String name) {
        if (!NeoQuest.isActive()) {
            return;
        }
        NeoQuest.engine().selectPet(slot, name);
        NeoQuest.save();
    }

    /** Si tienes esa mejora. */
    public static boolean has(final QuestItemType type) {
        return NeoQuest.isActive() && NeoQuest.engine().getAssets().hasItem(type);
    }

    /** A que nivel la tienes (las mascotas y el zepelin van por niveles). */
    public static int levelOf(final QuestItemType type) {
        return NeoQuest.isActive() ? NeoQuest.engine().getAssets().getItemLevel(type) : 0;
    }
}
