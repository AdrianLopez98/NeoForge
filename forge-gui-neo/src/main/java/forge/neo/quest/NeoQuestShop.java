package forge.neo.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.card.CardEdition;
import forge.gamemodes.quest.QuestSpellShop;
import forge.item.BoosterPack;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * La tienda de sobres.
 *
 * <p>Es el pilar del modo: eliges expansion, pagas, abres y ves que te ha
 * salido. Todo lo de verdad lo hace el motor —
 * {@code BoosterPack.fromSet(edicion)} genera el sobre con la plantilla real de
 * esa expansion (comunes, infrecuentes, la rara o mitica, la tierra) y
 * {@code QuestUtilCards.buyPack} cobra y mete las cartas en tu coleccion.
 *
 * <p>Lo unico que anyadimos es saber <b>cuales son nuevas</b>, que es la mitad
 * de la gracia de abrir un sobre: se mira la coleccion ANTES de comprar.
 */
public final class NeoQuestShop {

    private NeoQuestShop() {
    }

    /** Precio por defecto cuando la expansion no esta en la lista del motor. */
    private static final int DEFAULT_PRICE = 395;

    /**
     * Las expansiones de las que se puede abrir sobre.
     *
     * <p>No todas tienen: las hay que nunca salieron en sobres (mazos de
     * duelo, promocionales...). Lo dice el motor con
     * {@code CardEdition.Predicates.CAN_MAKE_BOOSTER}.
     *
     * <p>De la mas nueva a la mas vieja: es el orden en que se busca un sobre.
     */
    public static List<CardEdition> editions() {
        final List<CardEdition> out = new ArrayList<>();
        for (final CardEdition e : FModel.getMagicDb().getEditions()) {
            if (e != null && e.hasBoosterTemplate()) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparing(CardEdition::getDate).reversed());
        return out;
    }

    /** Un sobre de esa expansion, o null si no se puede montar. */
    public static BoosterPack boosterOf(final CardEdition edition) {
        try {
            return BoosterPack.fromSet(edition);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Lo que cuesta ese sobre.
     *
     * <p>Sale de {@code res/quest/booster-prices.txt}, que es la lista que usa
     * el motor. Las expansiones que no estan valen lo mismo que en el draft de
     * la aventura.
     */
    public static int priceOf(final BoosterPack pack) {
        if (pack == null) {
            return DEFAULT_PRICE;
        }
        final Integer value = QuestSpellShop.getCardValue(pack);
        return value == null || value <= 0 ? DEFAULT_PRICE : value;
    }

    public static boolean canAfford(final BoosterPack pack) {
        return NeoQuest.isActive() && NeoQuest.credits() >= priceOf(pack);
    }

    /** Lo que ha salido de un sobre. */
    public static final class Opened {
        private final List<PaperCard> cards;
        private final Set<String> fresh;
        private final int paid;

        Opened(final List<PaperCard> cards, final Set<String> fresh, final int paid) {
            this.cards = cards;
            this.fresh = fresh;
            this.paid = paid;
        }

        public List<PaperCard> getCards() {
            return cards;
        }

        /** true si esa carta no la tenias antes de abrir. */
        public boolean isNew(final PaperCard card) {
            return card != null && fresh.contains(key(card));
        }

        public int getNewCount() {
            return fresh.size();
        }

        public int getPaid() {
            return paid;
        }
    }

    /**
     * Compra el sobre, lo abre y mete las cartas en tu coleccion.
     *
     * <p>El orden importa: se mira que tienes ANTES de comprar, porque despues
     * ya estan todas dentro y no habria forma de saber cuales eran nuevas.
     *
     * @return lo que ha salido, o null si no te llega el dinero
     */
    public static Opened buyAndOpen(final BoosterPack pack) {
        if (pack == null || !NeoQuest.isActive()) {
            return null;
        }
        final int price = priceOf(pack);
        if (NeoQuest.credits() < price) {
            return null;
        }
        return open(pack, price);
    }

    /**
     * Un sobre <b>de regalo</b>: entra igual en la coleccion pero no se cobra.
     *
     * <p>Es el premio por ganar un duelo. Ver {@link NeoQuestPrize}.
     */
    public static Opened grantFree(final BoosterPack pack) {
        if (pack == null || !NeoQuest.isActive()) {
            return null;
        }
        return open(pack, 0);
    }

    private static Opened open(final BoosterPack pack, final int price) {
        final Set<String> before = ownedNames();

        if (price > 0) {
            NeoQuest.engine().getCards().buyPack(pack, price);
        } else {
            NeoQuest.engine().getCards().addAllCards(pack.getCards());
        }

        // getCards() memoriza: son EXACTAMENTE las que acaba de meter buyPack.
        return finish(pack.getName(), new ArrayList<>(pack.getCards()), before, price);
    }

    /**
     * Lo que tienes AHORA, por nombre. Se mira antes de comprar.
     *
     * <p>Visible en el paquete porque el botin de un duelo necesita lo mismo:
     * quien reparte alli es el motor, no la tienda, pero "nueva" significa
     * exactamente igual. Ver {@code NeoQuestRewards}.
     */
    static Set<String> ownedNames() {
        final Set<String> before = new HashSet<>();
        for (final java.util.Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            before.add(key(e.getKey()));
        }
        return before;
    }

    /** Guarda, traza y dice cuales eran nuevas. Comun a todo lo que se compra. */
    private static Opened finish(final String what, final List<PaperCard> cards,
                                 final Set<String> before, final int price) {
        final Set<String> fresh = new HashSet<>();
        for (final PaperCard card : cards) {
            if (!before.contains(key(card))) {
                fresh.add(key(card));
            }
        }
        NeoQuest.save();
        System.out.printf(java.util.Locale.ROOT,
                "[tienda] %s por %s -> %d cartas (%d nuevas), quedan %d cr.%n",
                what, price > 0 ? price + " cr." : "PREMIO",
                cards.size(), fresh.size(), NeoQuest.credits());
        return new Opened(cards, fresh, price);
    }

    // ===============================================================
    // Cartas sueltas
    // ===============================================================

    /**
     * Las cartas sueltas que hay hoy en el mostrador.
     *
     * <p><b>Esto no lo generamos nosotros: ya estaba.</b> La tienda del modo
     * aventura de Forge nunca fue solo de sobres —
     * {@code QuestUtilCards.generateCardsInShop()} repone en cada visita cartas
     * sueltas, sobres, preconstruidos, tournament packs, fat packs y cajas — y
     * {@code getShopList()} <b>lo genera la primera vez que se le pide</b>. No
     * se le habia pedido nunca.
     *
     * <p>Y lo que importa para el long tail: las sueltas se sacan por rareza de
     * <b>toda</b> la base de datos, sin filtrar por expansion
     * ({@code BoosterGenerator.getPrintSheet} tira de
     * {@code getCommonCards().getAllCards()}). O sea que por aqui puede
     * aparecer cualquier carta del juego, incluidas las que nunca salieron en
     * sobres.
     *
     * <p>El mostrador tambien trae sobres y mazos preconstruidos; aqui se
     * filtran las cartas y ya. Los sobres se compran por expansion en su
     * pestanya, que es mas claro que encontrartelos sueltos entre el catalogo.
     *
     * <p>Cuantas hay depende de tu nivel y de tus victorias
     * ({@code SHOP_STARTING_PACKS}, {@code SHOP_WINS_FOR_ADDITIONAL_PACK}), asi
     * que el mostrador crece contigo.
     */
    public static List<PaperCard> singles() {
        final List<PaperCard> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        for (final java.util.Map.Entry<forge.item.InventoryItem, Integer> e
                : NeoQuest.engine().getCards().getShopList()) {
            if (e.getKey() instanceof PaperCard pc) {
                for (int i = 0; i < Math.max(1, e.getValue()); i++) {
                    out.add(pc);
                }
            }
        }
        return out;
    }

    /** Lo que cuesta una carta suelta. La tasa el motor, no nosotros. */
    public static int priceOfCard(final PaperCard card) {
        if (card == null) {
            return DEFAULT_PRICE;
        }
        final Integer value = QuestSpellShop.getCardValue(card);
        return value == null || value <= 0 ? 1 : value;
    }

    public static boolean canAffordCard(final PaperCard card) {
        return NeoQuest.isActive() && NeoQuest.credits() >= priceOfCard(card);
    }

    /**
     * Compra una carta suelta.
     *
     * <p>Se quita del mostrador a mano: {@code QuestUtilCards.buyCard} cobra y
     * la mete en tu coleccion, pero <b>no la retira de la lista de la tienda</b>
     * — eso lo hace aparte la pantalla de la GUI vieja. Sin quitarla, la misma
     * copia se podria comprar infinitas veces.
     */
    public static Opened buySingle(final PaperCard card) {
        if (card == null || !NeoQuest.isActive()) {
            return null;
        }
        final int price = priceOfCard(card);
        if (NeoQuest.credits() < price) {
            return null;
        }
        final Set<String> before = ownedNames();
        NeoQuest.engine().getCards().buyCard(card, 1, price);
        NeoQuest.engine().getAssets().getShopList().remove(card);
        return finish(card.getName(), List.of(card), before, price);
    }

    // ===============================================================
    // Preconstruidos, cajas y lotes
    // ===============================================================

    /**
     * Lo que hay en el mostrador que no es una carta suelta.
     *
     * <p>Sale de la misma lista que las sueltas: el motor la repone con
     * preconstruidos, cajas de sobres, fat packs y tournament packs, y solo
     * habia que mirarla. Se piden por tipo porque cada uno tiene su pestanya —
     * un mazo entero y una caja de 36 sobres no se comparan entre si.
     */
    public static <T> List<T> onSale(final Class<T> type) {
        final List<T> out = new ArrayList<>();
        if (!NeoQuest.isActive()) {
            return out;
        }
        for (final java.util.Map.Entry<forge.item.InventoryItem, Integer> e
                : NeoQuest.engine().getCards().getShopList()) {
            if (type.isInstance(e.getKey())) {
                for (int i = 0; i < Math.max(1, e.getValue()); i++) {
                    out.add(type.cast(e.getKey()));
                }
            }
        }
        return out;
    }

    /** Las cajas y lotes: caja de sobres, fat pack y tournament pack. */
    public static List<forge.item.SealedProduct> boxes() {
        final List<forge.item.SealedProduct> out = new ArrayList<>();
        out.addAll(onSale(forge.item.BoosterBox.class));
        out.addAll(onSale(forge.item.FatPack.class));
        out.addAll(onSale(forge.item.TournamentPack.class));
        return out;
    }

    /**
     * Lo que cuesta cualquier cosa del mostrador.
     *
     * <p>Lo tasa el motor y sabe de todo: los preconstruidos por su ficha
     * ({@code QuestController.getPreconDeals}), las cajas a 8.750, los fat
     * packs a 2.365 y los tournament packs a 995.
     */
    public static int priceOfItem(final forge.item.InventoryItem item) {
        if (item == null) {
            return DEFAULT_PRICE;
        }
        final Integer value = QuestSpellShop.getCardValue(item);
        return value == null || value <= 0 ? 1 : value;
    }

    /**
     * Compra un preconstruido.
     *
     * <p>El motor hace las dos cosas que hacen falta: te mete <b>las cartas</b>
     * en la coleccion y ademas <b>el mazo montado</b> en tus mazos, listo para
     * jugarlo tal cual. Es el atajo del modo para quien no quiere construir.
     */
    public static Opened buyPrecon(final forge.item.PreconDeck precon) {
        if (precon == null || !NeoQuest.isActive()) {
            return null;
        }
        final int price = priceOfItem(precon);
        if (NeoQuest.credits() < price) {
            return null;
        }
        final Set<String> before = ownedNames();
        final List<PaperCard> cards = precon.getDeck() == null ? List.<PaperCard>of()
                : precon.getDeck().getAllCardsInASinglePool().toFlatList();
        NeoQuest.engine().getCards().buyPreconDeck(precon, price);
        // El preconstruido entra en tus mazos con su banquillo puesto, y en la
        // aventura eso deja el mazo injugable sin ensenyar por que: ver
        // NeoQuest.dropSideboards(). Las cartas ya estan en tu coleccion.
        NeoQuest.dropSideboards();
        NeoQuest.engine().getAssets().getShopList().remove(precon);
        return finish(precon.getName(), new ArrayList<>(cards), before, price);
    }

    /**
     * Compra una caja o un lote.
     *
     * <p>Van todos por {@code buyPack}, que acepta cualquier
     * {@code SealedProduct} — una caja de 36 sobres no es mas que un producto
     * sellado grande. Y como {@code getCards()} memoriza, lo que se ensenya
     * despues son <b>exactamente</b> las cartas que acaba de meter.
     */
    public static Opened buyBox(final forge.item.SealedProduct product) {
        if (product == null || !NeoQuest.isActive()) {
            return null;
        }
        final int price = priceOfItem(product);
        if (NeoQuest.credits() < price) {
            return null;
        }
        final Set<String> before = ownedNames();
        NeoQuest.engine().getCards().buyPack(product, price);
        NeoQuest.engine().getAssets().getShopList().remove(product);
        return finish(product.getName(), new ArrayList<>(product.getCards()), before, price);
    }

    // ===============================================================
    // Sobres de colector
    // ===============================================================

    /**
     * Las hojas de arte especial que puede traer una expansion.
     *
     * <p>Los sets modernos las traen ya troceadas en su fichero de edicion:
     * {@code [borderless]}, {@code [showcase]}, {@code [extended art]} y
     * {@code [fullart]} son listas de numeros de coleccionista con su artista.
     * Forge las carga como hojas de impresion con el nombre
     * {@code "<CODIGO> <seccion>"}, y {@code BoosterGenerator} sabe tirar de
     * ellas con {@code fromSheet(...)}.
     *
     * <p>O sea: <b>las impresiones bonitas ya existen y ya se pueden repartir.
     * Lo que no existe es el producto que las reparte</b> — Forge no define
     * ningun sobre de colector, ni hay clase que lo represente. Eso es lo que
     * se monta aqui.
     */
    private static final String[] ART_SHEETS = {"borderless", "showcase", "extended art"};

    /** Lo que cuesta un sobre de colector respecto al normal. */
    private static final int COLLECTOR_MULTIPLIER = 3;

    /** Si esa hoja existe para esa expansion. */
    private static boolean hasSheet(final CardEdition edition, final String sheet) {
        return edition != null
                && FModel.getMagicDb().getPrintSheets().contains(edition.getCode() + " " + sheet);
    }

    /**
     * Las expansiones de las que se puede montar un sobre de colector.
     *
     * <p>Hacen falta dos cosas: que la expansion tenga sobre normal (de ahi
     * salen los comunes y la rara) y que tenga <b>al menos una</b> hoja de arte
     * especial. Sin eso, un "sobre de colector" seria un sobre normal mas caro,
     * que es exactamente el timo que no queremos.
     */
    public static List<CardEdition> collectorEditions() {
        final List<CardEdition> out = new ArrayList<>();
        for (final CardEdition e : editions()) {
            if (artSheetsOf(e).isEmpty()) {
                continue;
            }
            out.add(e);
        }
        return out;
    }

    /** Que hojas de arte tiene esta expansion, en orden. */
    public static List<String> artSheetsOf(final CardEdition edition) {
        final List<String> out = new ArrayList<>();
        for (final String sheet : ART_SHEETS) {
            if (hasSheet(edition, sheet)) {
                out.add(sheet);
            }
        }
        return out;
    }

    /**
     * Un sobre de colector de esa expansion.
     *
     * <p>Se monta con la forma del de verdad, adaptada a lo que Forge tiene
     * troceado: el grueso en comunes e infrecuentes, la rara o mitica <b>en
     * foil</b>, <b>una carta de cada hoja de arte especial</b> que tenga el set,
     * y la tierra full-art foil si la hay. Un sobre de colector de verdad son
     * quince cartas y practicamente todo lo bueno viene de esas hojas: aqui es
     * lo mismo, con las que existan.
     *
     * <p>La plantilla se escribe en el mismo dialecto que usan los ficheros de
     * edicion ({@code "5 Common, 1 RareMythic+"}) y la parsea el motor. El
     * {@code +} es foil, y el nombre de la plantilla es el codigo de la
     * expansion porque {@code BoosterGenerator} lo usa para las tiradas de foil.
     */
    public static BoosterPack collectorBooster(final CardEdition edition) {
        final List<String> sheets = artSheetsOf(edition);
        if (edition == null || sheets.isEmpty()) {
            return null;
        }
        final String code = edition.getCode();
        final StringBuilder desc = new StringBuilder("5 Common, 4 Uncommon, 1 RareMythic+");
        for (final String sheet : sheets) {
            desc.append(", 1 fromSheet(\"").append(code).append(' ').append(sheet).append("\")");
        }
        if (hasSheet(edition, "fullart")) {
            desc.append(", 1 Land:fromSheet(\"").append(code).append(" fullart\")+");
        }
        try {
            return new BoosterPack(edition.getName() + " Collector",
                    new forge.item.SealedTemplate(code, desc.toString()));
        } catch (final RuntimeException e) {
            System.err.println("[tienda] no se ha podido montar el colector de "
                    + code + ": " + e);
            return null;
        }
    }

    /**
     * Lo que cuesta un sobre de colector: el triple que el normal.
     *
     * <p>Es lo que valen en la vida real, mas o menos, y sobre todo es lo que
     * hace que abrirlo sea una decision. El normal lo tasa el motor por su
     * expansion, asi que esto sube y baja con el.
     */
    public static int collectorPrice(final CardEdition edition) {
        return priceOf(boosterOf(edition)) * COLLECTOR_MULTIPLIER;
    }

    public static boolean canAffordCollector(final CardEdition edition) {
        return NeoQuest.isActive() && NeoQuest.credits() >= collectorPrice(edition);
    }

    /** Compra y abre un sobre de colector. */
    public static Opened buyCollector(final CardEdition edition, final BoosterPack pack) {
        if (pack == null || edition == null || !NeoQuest.isActive()) {
            return null;
        }
        final int price = collectorPrice(edition);
        if (NeoQuest.credits() < price) {
            return null;
        }
        return open(pack, price);
    }

    // ===============================================================
    // El sobre de Secret Lair
    // ===============================================================

    /** Lo que cuesta un Secret Lair. Fijo y caro: es un capricho. */
    public static final int SECRET_LAIR_PRICE = 1500;

    /** Cuantas cartas trae. */
    public static final int SECRET_LAIR_SIZE = 2;

    /**
     * Las expansiones que cuentan como Secret Lair.
     *
     * <p>Se dejan fuera SLP (promocionales) y PSSC (planos), que no son drops.
     *
     * <p><b>SLX (<i>Universes Within</i>) tiene que estar</b>, y no tenerlo
     * costo cuatro drops. SLX es la version "dentro del multiverso" de las
     * mismas cartas, y Forge las guarda con el <b>mismo nombre</b> que la de
     * Secret Lair: sin SLX aqui, Rick, Ryu o Xenk parecian tener una impresion
     * fuera de Secret Lair y quedaban descartados como no exclusivos — cuando
     * SLX tampoco sale en ningun sobre, porque no tiene plantilla. El sintoma
     * era que The Walking Dead, Street Fighter y Honor Among Thieves aparecian
     * con "0 exclusivas", que es justo lo contrario de lo que son.
     */
    private static final Set<String> SECRET_LAIR_SETS = Set.of("SLD", "SLC", "SLU", "SLX");

    private static List<PaperCard> secretLairPool;

    /**
     * Las cartas que <b>solo existen</b> en Secret Lair.
     *
     * <p>Es la parte con criterio de todo esto. Secret Lair es, en su mayoria,
     * <b>arte nuevo de cartas que ya tienes</b>: Sol Ring, Bloodghast, tierras
     * basicas nevadas. Un sobre lleno de eso no daria nada — ya tienes esas
     * cartas, y aqui las ediciones alternativas no se coleccionan.
     *
     * <p>Lo que si es exclusivo son los cruces con otras marcas: <i>Jin Sakai,
     * Ghost of Tsushima</i> y companyia. Cartas de verdad, con sus reglas, que
     * no aparecen en ninguna otra expansion y por tanto <b>en ningun sobre</b>.
     * Ese es exactamente el hueco que este producto tapa.
     *
     * <p>El criterio, entonces: una carta entra si <b>todas</b> sus impresiones
     * estan en Secret Lair. Se calcula una vez recorriendo la base entera y se
     * guarda, porque son 95.000 impresiones.
     */
    public static synchronized List<PaperCard> secretLairPool() {
        if (secretLairPool != null) {
            return secretLairPool;
        }
        final Set<String> alsoElsewhere = new HashSet<>();
        final java.util.Map<String, PaperCard> inSecretLair = new java.util.LinkedHashMap<>();
        for (final PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards()) {
            if (pc == null) {
                continue;
            }
            if (SECRET_LAIR_SETS.contains(pc.getEdition())) {
                inSecretLair.putIfAbsent(pc.getName(), pc);
            } else {
                alsoElsewhere.add(pc.getName());
            }
        }
        final List<PaperCard> out = new ArrayList<>();
        for (final java.util.Map.Entry<String, PaperCard> e : inSecretLair.entrySet()) {
            if (!alsoElsewhere.contains(e.getKey())) {
                out.add(e.getValue());
            }
        }
        secretLairPool = out;
        System.out.println("[tienda] Secret Lair: " + out.size()
                + " cartas que no salen en ningun otro sitio");
        return out;
    }

    /**
     * Un Secret Lair recien montado: siete cartas al azar, sin repetir dentro
     * del mismo sobre.
     *
     * <p>Hay que pedir uno nuevo despues de cada compra, igual que con los
     * sobres: si se reutiliza el mismo, salen las mismas cartas.
     */
    public static List<PaperCard> secretLairPack() {
        final List<PaperCard> pool = new ArrayList<>(secretLairPool());
        if (pool.isEmpty()) {
            return List.of();
        }
        java.util.Collections.shuffle(pool, forge.util.MyRandom.getRandom());
        return new ArrayList<>(pool.subList(0, Math.min(SECRET_LAIR_SIZE, pool.size())));
    }

    public static boolean canAffordSecretLair() {
        return NeoQuest.isActive() && NeoQuest.credits() >= SECRET_LAIR_PRICE;
    }

    /**
     * Compra y abre un Secret Lair.
     *
     * <p>No pasa por {@code buyPack}: eso quiere un {@code SealedProduct} de una
     * expansion con plantilla de sobre, y Secret Lair no la tiene — que es
     * justamente por lo que no se podia comprar. Se cobra y se meten las cartas
     * con las mismas dos llamadas que usa el motor por dentro.
     */
    public static Opened buySecretLair(final List<PaperCard> cards) {
        if (cards == null || cards.isEmpty() || !NeoQuest.isActive()) {
            return null;
        }
        if (NeoQuest.credits() < SECRET_LAIR_PRICE) {
            return null;
        }
        final Set<String> before = ownedNames();
        NeoQuest.engine().getAssets().subtractCredits(SECRET_LAIR_PRICE);
        NeoQuest.engine().getCards().addAllCards(cards);
        return finish("Secret Lair", new ArrayList<>(cards), before, SECRET_LAIR_PRICE);
    }

    // ===============================================================
    // Los drops con nombre
    // ===============================================================

    /**
     * Los Secret Lair que se pueden pedir POR SU NOMBRE.
     *
     * <p>La tabla la escribimos nosotros porque Forge no la tiene. Ver
     * {@link SecretLairDrops}, que explica por que no se puede deducir.
     */
    public static List<SecretLairDrops.Drop> drops() {
        return SecretLairDrops.all();
    }

    public static boolean canAffordDrop(final SecretLairDrops.Drop drop) {
        return drop != null && NeoQuest.isActive() && NeoQuest.credits() >= drop.getPrice();
    }

    /**
     * Compra un drop entero.
     *
     * <p>Como el sobre sorpresa, no pasa por {@code buyPack}: eso quiere un
     * {@code SealedProduct} de una expansion con plantilla de sobre, y Secret
     * Lair no la tiene. Se cobra y se meten las cartas con las mismas dos
     * llamadas que usa el motor por dentro.
     *
     * <p>Aqui no hay sorpresa que preservar — las cartas se han ensenyado antes
     * de pagar — pero se sigue mirando la coleccion ANTES, porque cuales eran
     * nuevas <b>para ti</b> si es informacion, y despues de comprar ya no se
     * puede saber.
     */
    public static Opened buyDrop(final SecretLairDrops.Drop drop) {
        if (drop == null || drop.getCards().isEmpty() || !NeoQuest.isActive()) {
            return null;
        }
        final int price = drop.getPrice();
        if (NeoQuest.credits() < price) {
            return null;
        }
        final Set<String> before = ownedNames();
        NeoQuest.engine().getAssets().subtractCredits(price);
        NeoQuest.engine().getCards().addAllCards(drop.getCards());
        return finish("Secret Lair: " + drop.getName(),
                new ArrayList<>(drop.getCards()), before, price);
    }

    // ===============================================================
    // La cara de un preconstruido
    // ===============================================================

    /**
     * Con que carta se ensenya un mazo preconstruido.
     *
     * <p>Un preconstruido en una lista es solo un nombre — <i>Abzan Siege</i>,
     * <i>Adaptive Enchantment</i> — y de ahi no se saca ni de que color es, ni
     * de que va, ni quien lo lidera. Lo que si se sabe de un vistazo es la
     * <b>carta</b>: en Commander, el comandante; en un mazo de 60, la carta
     * gorda alrededor de la que esta construido.
     *
     * <p>El comandante lo publica el motor ({@code Deck.getCommanders()}), y
     * eso resuelve los de Commander. Para el resto no hay ningun campo que diga
     * "la carta importante" — el {@code +} de los ficheros de Forge es la marca
     * de <b>foil</b>, no de carta destacada — asi que se elige la de mayor
     * rareza y, a igual rareza, la de coste mas alto. No es exacto, pero acierta
     * casi siempre: un preconstruido se monta alrededor de su mitica o su rara
     * mas cara, y aunque fallara, lo que se ensenya sigue siendo una carta de
     * ese mazo y de sus colores.
     *
     * <p>Se cachea porque esto se pregunta en cada repintado de la rejilla.
     */
    public static PaperCard faceOf(final forge.item.PreconDeck precon) {
        if (precon == null || precon.getDeck() == null) {
            return null;
        }
        return FACES.computeIfAbsent(precon.getName(), name -> pickFace(precon.getDeck()));
    }

    private static final java.util.Map<String, PaperCard> FACES = new java.util.HashMap<>();

    private static PaperCard pickFace(final forge.deck.Deck deck) {
        final List<PaperCard> commanders = deck.getCommanders();
        if (commanders != null && !commanders.isEmpty()) {
            return commanders.get(0);
        }
        PaperCard best = null;
        for (final PaperCard c : deck.getMain().toFlatList()) {
            if (c == null || c.getRules() == null || c.getRules().getType().isLand()) {
                continue;
            }
            if (best == null || faceScore(c) > faceScore(best)) {
                best = c;
            }
        }
        return best;
    }

    /** Rareza primero, coste despues. */
    private static int faceScore(final PaperCard c) {
        final int rarity;
        switch (c.getRarity()) {
            case MythicRare: rarity = 5; break;
            case Special:    rarity = 4; break;
            case Rare:       rarity = 3; break;
            case Uncommon:   rarity = 2; break;
            default:         rarity = 1; break;
        }
        return rarity * 100 + c.getRules().getManaCost().getCMC();
    }

    /** Por nombre, no por impresion: tener la carta es tenerla. */
    static String key(final PaperCard card) {
        return card == null ? "" : card.getName();
    }
}
