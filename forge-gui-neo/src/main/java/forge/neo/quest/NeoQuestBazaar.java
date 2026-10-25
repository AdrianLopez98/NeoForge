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
        private final List<LockedPet> locked;

        Stall(final String name, final String title, final String fluff,
              final List<IQuestBazaarItem> items, final List<LockedPet> locked) {
            this.name = name;
            this.title = title;
            this.fluff = fluff;
            this.items = items;
            this.locked = locked;
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

        /**
         * Las mascotas de este puesto cuyo nivel siguiente <b>aun no se vende</b>
         * por rango. Solo para ensenyarlas: no se compran.
         */
        public List<LockedPet> getLocked() {
            return locked;
        }
    }

    /**
     * Una mascota cuyo nivel siguiente espera a tu rango.
     *
     * <p>Discord, 15-10-2026: <i>"it got sold out so I was not sure when the
     * store gives better ones"</i>. Forge solo pone a la venta el nivel N+1 de
     * una mascota cuando tu rango de Quest llega a N
     * ({@code QuestPetController.isAvailableForPurchase}), y el rango sube con
     * las victorias ({@code QuestAchievements.getLevel}: una cada 3, 4, 5 o 6
     * segun la dificultad). Mientras tanto la mascota <b>desaparecia</b> del
     * mostrador, y si era lo unico del puesto salia "ya has comprado todo",
     * que es falso. Ahora se queda, apagada, diciendo cuantas victorias faltan.
     *
     * @param pet       la mascota
     * @param nextLevel el nivel que se compraria
     * @param rank      el rango que hace falta para comprarlo
     * @param winsLeft  las victorias que faltan para ese rango
     */
    public record LockedPet(QuestPetController pet, int nextLevel, int rank, int winsLeft) {
    }

    /**
     * La mascota de ese nombre si esta bloqueada por rango, o null. La regla es
     * la del motor; aqui solo se repite para poder decir cuanto falta, y por eso
     * se le pregunta primero a {@code isAvailableForPurchase}: si el motor
     * cambia la regla, lo peor que pasa es que no salga el aviso.
     */
    private static LockedPet lockedPet(final QuestController q, final String itemName) {
        try {
            final QuestPetController pet = q.getPetsStorage().getPet(itemName);
            if (pet == null) {
                return null;
            }
            final int level = q.getAssets().getPetLevel(pet.getSaveFileKey());
            if (level >= pet.getMaxLevel() || pet.isAvailableForPurchase(q.getAssets(), q)) {
                return null;
            }
            final forge.gamemodes.quest.data.QuestAchievements a = q.getAchievements();
            final int perRank = forge.model.FModel.getQuestPreferences().getPrefInt(
                    forge.gamemodes.quest.data.QuestPreferences.DifficultyPrefs.WINS_RANKUP,
                    a.getDifficulty());
            if (perRank <= 0 || q.getLevel() >= level) {
                return null;
            }
            return new LockedPet(pet, level + 1, level, Math.max(1, level * perRank - a.getWin()));
        } catch (final RuntimeException e) {
            return null;
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
                final List<LockedPet> locked = new ArrayList<>();
                if (def != null && def.getItems() != null) {
                    for (final String itemName : def.getItems()) {
                        final LockedPet l = lockedPet(q, itemName);
                        if (l != null) {
                            locked.add(l);
                        }
                    }
                }
                out.add(new Stall(name,
                        def == null || def.getDisplayName() == null ? name : def.getDisplayName(),
                        def == null ? "" : def.getFluff(),
                        items, locked));
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

    /**
     * Lo que te PAGA el bazar por comprarlo, ademas de cobrarte el precio.
     *
     * <p>{@code QuestUtil.buyQuestItem} resta {@code getBuyingPrice} y suma
     * {@code getSellingPrice}. Hoy solo paga <b>Pound of Flesh</b> (vendes una
     * vida de partida por creditos), y su precio es 0: ensenyar solo el precio
     * decia "0 cr.", como si fuera gratis y no hiciera nada. Discord, 10-10-2026:
     * un jugador lo pulso seis veces buscando que hacia y se quedo en 14 vidas.
     */
    public static int gainOf(final IQuestBazaarItem item) {
        if (item == null || !NeoQuest.isActive()) {
            return 0;
        }
        return Math.max(0, item.getSellingPrice(NeoQuest.engine().getAssets()));
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

    // ===============================================================
    // Lo que ya has comprado (Discord, 25-10-2026: "I bought a zeppelin, I'd
    // like to show it off"). Las mejoras desaparecen del puesto al comprarlas
    // y no se veian en ningun sitio; las mascotas si tenian su apartado.
    // ===============================================================

    /**
     * Una mejora comprada: su nombre, a que nivel la tienes (de cuantos), lo
     * que hace, y su icono de Forge: el fichero de la lamina y el recorte
     * {@code {x, y, ancho, alto}}, o null si no se sabe.
     */
    public record OwnedUpgrade(IQuestBazaarItem item, String name, int level, int maxLevel,
                               String description, String iconFile, int[] iconRect) {
    }

    /**
     * Las mejoras que tienes (nivel mayor que 0), en el orden del bazar. Sin
     * las mascotas, que ya tienen su apartado.
     *
     * <p>Los objetos son los del motor: la lista entera es privada en
     * {@code QuestBazaarManager} ({@code items}) y su icono tambien
     * ({@code QuestItemBasic.icon}, un {@code FSkinProp} que pone XStream al
     * leer {@code index.xml}), asi que se leen por reflexion. Si un dia
     * cambian, no se rompe nada: la lista sale vacia o sin iconos.
     */
    public static List<OwnedUpgrade> ownedUpgrades() {
        final List<OwnedUpgrade> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        final QuestController q = NeoQuest.engine();
        try {
            // La lista se carga con los puestos (QuestBazaarManager.load), y
            // solo cuando alguien los pide: sin esto, la primera vez salia vacia.
            final java.util.Set<String> stallNames = q.getBazaar().getStallNames();
            final java.lang.reflect.Field f = q.getBazaar().getClass().getDeclaredField("items");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            final java.util.Map<String, IQuestBazaarItem> all =
                    (java.util.Map<String, IQuestBazaarItem>) f.get(q.getBazaar());
            // En el orden de los puestos, como se ven en el bazar.
            final java.util.LinkedHashSet<IQuestBazaarItem> ordered = new java.util.LinkedHashSet<>();
            for (final String stall : stallNames) {
                final QuestStallDefinition def = q.getBazaar().getStall(stall);
                if (def != null && def.getItems() != null) {
                    for (final String name : def.getItems()) {
                        final IQuestBazaarItem item = all.get(name);
                        if (item != null) {
                            ordered.add(item);
                        }
                    }
                }
            }
            ordered.addAll(all.values());
            for (final IQuestBazaarItem item : ordered) {
                if (!(item instanceof forge.gamemodes.quest.bazaar.QuestItemBasic basic)) {
                    continue;
                }
                final int level = q.getAssets().getItemLevel(basic.getItemType());
                if (level <= 0) {
                    continue;
                }
                out.add(new OwnedUpgrade(item, item.getPurchaseName(), level, basic.getMaxLevel(),
                        descriptionOf(item), iconFile(basic), iconRect(basic)));
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[bazar] no se han podido leer las mejoras compradas: " + e);
        }
        return out;
    }

    /**
     * Solo pruebas ({@code -Dneo.bazaar.demoUpgrades=true}): zepelin, Estates
     * 2 y Elixir 3, EN MEMORIA y sin guardar, para capturar las insignias.
     */
    public static void demoUpgradesForTest() {
        if (!NeoQuest.isActive()) {
            return;
        }
        final forge.gamemodes.quest.data.QuestAssets a = NeoQuest.engine().getAssets();
        a.setItemLevel(QuestItemType.ZEPPELIN, Math.max(1, a.getItemLevel(QuestItemType.ZEPPELIN)));
        a.setItemLevel(QuestItemType.ESTATES, Math.max(2, a.getItemLevel(QuestItemType.ESTATES)));
        a.setItemLevel(QuestItemType.ELIXIR_OF_LIFE, Math.max(3, a.getItemLevel(QuestItemType.ELIXIR_OF_LIFE)));
    }

    /** La lamina de iconos de Forge, la del skin de serie (el zepelin, el nuestro). */
    private static String iconFile(final forge.gamemodes.quest.bazaar.QuestItemBasic item) {
        if (item.getItemType() == QuestItemType.ZEPPELIN) {
            return zeppelinIcon();
        }
        return forge.localinstance.properties.ForgeConstants.DEFAULT_SKINS_DIR
                + forge.localinstance.properties.ForgeConstants.SPRITE_ICONS_FILE;
    }

    /**
     * El zepelin de Forge (ICO_QUEST_ZEP, y el BIG tambien) es una CALAVERA en
     * la lamina de serie: a quien lo compra para presumir de zepelin no se le
     * ensenya eso. El nuestro (25-10-2026) va en el jar, pintado con la IA
     * local al estilo de los demas iconos del bazar (ver su PROCEDENCIA.md), y
     * se saca a un fichero una vez: {@link OwnedUpgrade} habla de ficheros,
     * porque Android recorta con {@code BitmapFactory.decodeFile}.
     */
    private static String zeppelinIcon() {
        final java.io.File f = new java.io.File(System.getProperty("java.io.tmpdir"), "neoforge-zeppelin-1.png");
        if (!f.isFile()) {
            try (java.io.InputStream in = NeoQuestBazaar.class.getResourceAsStream("zeppelin.png")) {
                if (in == null) {
                    return null;
                }
                java.nio.file.Files.copy(in, f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (final java.io.IOException | RuntimeException e) {
                return null;
            }
        }
        return f.getAbsolutePath();
    }

    private static int[] iconRect(final forge.gamemodes.quest.bazaar.QuestItemBasic item) {
        if (item.getItemType() == QuestItemType.ZEPPELIN) {
            return new int[] {0, 0, 80, 80};
        }
        try {
            final java.lang.reflect.Field f = forge.gamemodes.quest.bazaar.QuestItemBasic.class.getDeclaredField("icon");
            f.setAccessible(true);
            final Object prop = f.get(item);
            if (prop instanceof forge.localinstance.skin.FSkinProp p && p.getCoords() != null
                    && p.getCoords().length >= 4) {
                return p.getCoords().clone();
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            // Sin icono: la insignia sale solo con el nombre.
        }
        return null;
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
