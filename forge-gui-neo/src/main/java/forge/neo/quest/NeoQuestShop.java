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

    /**
     * Cuantas cartas DISTINTAS de las que te faltan en tus mazos estan hoy en
     * las sueltas. Es el aviso del boton de la tienda en el cuartel.
     */
    public static int wantedInShop() {
        final java.util.Map<String, Integer> wanted = NeoQuest.wantedByName();
        final java.util.Set<String> arts = NeoQuest.wantedArts();
        if (wanted.isEmpty() && arts.isEmpty()) {
            return 0;
        }
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (final forge.item.PaperCard c : singles()) {
            if (isWanted(c, wanted, arts)) {
                seen.add(c.getName().toLowerCase(java.util.Locale.ROOT));
            }
        }
        return seen.size();
    }

    /**
     * Si esta suelta te sirve para un mazo: la carta te falta (con cualquier
     * arte), o es justo el arte deseado que te falta ({@code ArtWishes}).
     */
    public static boolean isWanted(final forge.item.PaperCard c, final java.util.Map<String, Integer> wanted,
                                   final java.util.Set<String> arts) {
        return c != null && (wanted.containsKey(c.getName().toLowerCase(java.util.Locale.ROOT))
                || arts.contains(forge.neo.deck.ArtWishes.printKey(c)));
    }

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
     *
     * <p>En una Quest limitada a unas expansiones ({@link NeoQuest#allowsSet}),
     * solo esas. De aqui salen tambien el sobre de premio y los de colector.
     */
    /**
     * Ajustes -> Modos: los sobres de Alchemy en la Quest (Discord, 22-10-2026:
     * <i>"Can we have a way to disable the alchemy packs?"</i>). Encendido de
     * fabrica, que es como estaba; apagado, no salen en la tienda, ni en el
     * sobre de premio, ni en el del dia (todos leen de {@link #editions}).
     */
    public static final String ALCHEMY = "quest.alchemyPacks";

    /**
     * Si es una expansion de Alchemy: las de Arena, con cartas rebalanceadas y
     * mecanicas digitales. Forge las marca como {@code Online}, pero ahi tambien
     * van las de MTGO de siempre (Vintage Masters...), asi que se mira el nombre:
     * todas se llaman "Alchemy: ..." (YTDM, YSOS, HBG...).
     */
    public static boolean isAlchemy(final CardEdition e) {
        return e != null && e.getType() == CardEdition.Type.ONLINE
                && e.getName() != null && e.getName().startsWith("Alchemy");
    }

    /**
     * Si es de las que Forge solo carga con "cargar cartas no legales": las
     * "Funny" y las de borde plateado (Unglued, Unstable...). Sin esa opcion,
     * {@code StaticData} descarta todas sus cartas menos las de {@code [eternal]}.
     */
    static boolean isFunny(final CardEdition e) {
        return e != null && (e.getType() == CardEdition.Type.FUNNY
                || e.getBorderColor() == CardEdition.BorderColor.SILVER);
    }

    /** Si al arrancar se cargaron las cartas no legales (lo lee {@code FModel} una vez). */
    private static boolean funnyCardsLoaded() {
        try {
            return FModel.getPreferences().getPrefBoolean(
                    forge.localinstance.properties.ForgePreferences.FPref.UI_LOAD_NONLEGAL_CARDS);
        } catch (final RuntimeException e) {
            return true;
        }
    }

    public static List<CardEdition> editions() {
        final List<CardEdition> out = new ArrayList<>();
        final boolean alchemy = forge.neo.NeoSettings.getBool(ALCHEMY, true);
        final boolean funny = funnyCardsLoaded();
        for (final CardEdition e : FModel.getMagicDb().getEditions()) {
            if (!alchemy && isAlchemy(e)) {
                continue;
            }
            // Sin sus cartas cargadas, un sobre "Funny" esta vacio (Discord,
            // 26-10-2026: el de Unstable solo podia dar Steamflogger Boss).
            if (!funny && isFunny(e)) {
                continue;
            }
            if (e != null && (e.hasBoosterTemplate() || hasBuiltPack(e)) && NeoQuest.allowsSet(e.getCode())) {
                out.add(e);
            }
        }
        // Con el ajuste del mundo (NeoQuest.WORLD_SHOP), solo las de ese mundo.
        // Si ninguna de ellas tiene sobre, se queda como estaba: una tienda sin
        // sobres y un premio que no sale serian peores que no limitar.
        final java.util.Set<String> world = NeoQuest.worldSets();
        if (world != null) {
            final List<CardEdition> local = new ArrayList<>();
            for (final CardEdition e : out) {
                if (world.contains(e.getCode())) {
                    local.add(e);
                }
            }
            if (!local.isEmpty()) {
                out.clear();
                out.addAll(local);
            }
        }
        out.sort(Comparator.comparing(CardEdition::getDate).reversed());
        return out;
    }

    /** Un sobre de esa expansion, o null si no se puede montar. */
    public static BoosterPack boosterOf(final CardEdition edition) {
        try {
            if (hasBuiltPack(edition)) {
                final forge.item.SealedTemplate t = commanderTemplate(edition);
                // Sin " Booster": getName() ya le anyade " Booster Pack" (salia
                // "Star Trek Commander Booster Booster Pack").
                return t == null ? null : new BoosterPack(edition.getName(), t);
            }
            return BoosterPack.fromSet(edition);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    // ===============================================================
    // Los mazos de Commander (09-10-2026)
    // ===============================================================

    /** Lo que cuesta un mazo de Commander, como mucho y como poco. */
    static final int COMMANDER_DECK_MIN = 2500;
    /** @see #COMMANDER_DECK_MIN */
    static final int COMMANDER_DECK_MAX = 9000;

    private static List<forge.item.PreconDeck> commanderDecks;
    private static final java.util.Map<String, Integer> COMMANDER_DECK_PRICES = new java.util.HashMap<>();

    /**
     * Los mazos de Commander preconstruidos de Forge (173), a la venta enteros.
     * Es como se consiguen de verdad las cartas de los sets de Commander
     * (Discord, 09-10-2026: <i>"where are the commander sets in quest?"</i>):
     * el mazo de 100 cartas, que entra en tus mazos y en tu coleccion. Van
     * aparte de los preconstruidos de la tienda, que son los de 60 que el motor
     * repone, porque estos estan siempre.
     *
     * <p>Con la Quest limitada a unas expansiones, o en un mundo que limita la
     * tienda, solo los de esas expansiones (la del corchete del nombre:
     * "Ahoy Mateys [LCC] [2023]").
     */
    public static List<forge.item.PreconDeck> commanderDecks() {
        synchronized (NeoQuestShop.class) {
            if (commanderDecks == null) {
                final List<forge.item.PreconDeck> all = new ArrayList<>();
                for (final forge.deck.Deck d : FModel.getDecks().getCommanderPrecons()) {
                    if (d == null || d.getCommanders().isEmpty()) {
                        continue;
                    }
                    all.add(new forge.item.PreconDeck(d, setOfDeck(d), null));
                }
                // De lo mas nuevo a lo mas viejo, como los sobres.
                all.sort(Comparator.comparing((forge.item.PreconDeck p) -> {
                    final CardEdition e = FModel.getMagicDb().getEditions().get(p.getEdition());
                    return e == null ? new java.util.Date(0) : e.getDate();
                }).reversed().thenComparing(forge.item.PreconDeck::getName));
                commanderDecks = all;
            }
        }
        final java.util.Set<String> world = NeoQuest.worldSets();
        final List<forge.item.PreconDeck> out = new ArrayList<>();
        for (final forge.item.PreconDeck p : commanderDecks) {
            if (NeoQuest.allowsSet(p.getEdition()) && (world == null || world.contains(p.getEdition()))) {
                out.add(p);
            }
        }
        return out;
    }

    /** La expansion de un mazo de Commander: la del corchete del nombre, o la de su comandante. */
    private static String setOfDeck(final forge.deck.Deck d) {
        final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[([A-Z0-9]{2,6})\\]").matcher(d.getName());
        while (m.find()) {
            if (FModel.getMagicDb().getEditions().get(m.group(1)) != null) {
                return m.group(1);
            }
        }
        return d.getCommanders().get(0).getEdition();
    }

    /**
     * Lo que cuesta un mazo de Commander: lo que valen sus cartas sueltas en
     * la tienda, con el descuento de comprarlas juntas, entre {@value
     * #COMMANDER_DECK_MIN} y {@value #COMMANDER_DECK_MAX}. Un mazo con las
     * miticas buenas cuesta mas, como en la tienda de verdad.
     */
    public static int commanderDeckPrice(final forge.item.PreconDeck precon) {
        if (precon == null || precon.getDeck() == null) {
            return COMMANDER_DECK_MAX;
        }
        synchronized (COMMANDER_DECK_PRICES) {
            final Integer known = COMMANDER_DECK_PRICES.get(precon.getName());
            if (known != null) {
                return known;
            }
        }
        long sum = 0;
        for (final java.util.Map.Entry<PaperCard, Integer> e : precon.getDeck().getAllCardsInASinglePool()) {
            if (e.getKey().getRules() != null && e.getKey().getRules().getType().isBasicLand()) {
                continue;
            }
            sum += (long) priceOfCard(e.getKey()) * e.getValue();
        }
        final long raw = Math.round(sum * COMMANDER_DECK_DISCOUNT / 50.0) * 50L;
        final int price = (int) Math.max(COMMANDER_DECK_MIN, Math.min(COMMANDER_DECK_MAX, raw));
        synchronized (COMMANDER_DECK_PRICES) {
            COMMANDER_DECK_PRICES.put(precon.getName(), price);
        }
        return price;
    }

    /** Lo que se paga de cada carta al comprar el mazo entero. */
    static final double COMMANDER_DECK_DISCOUNT = 0.35;

    /**
     * Compra un mazo de Commander: entra en tus mazos (con su comandante) y
     * sus cartas en tu coleccion. Lo hace el motor, como un preconstruido.
     */
    public static Opened buyCommanderDeck(final forge.item.PreconDeck precon) {
        if (precon == null || precon.getDeck() == null || !NeoQuest.isActive()) {
            return null;
        }
        final int price = commanderDeckPrice(precon);
        if (NeoQuest.credits() < price) {
            return null;
        }
        final Before before = Before.now();
        // Una copia: el motor guarda el mazo tal cual, y el de la lista tiene
        // que seguir intacto para el siguiente que lo compre. Con el nombre
        // limpio ("Ahoy Mateys", no "Ahoy Mateys [LCC] [2023]") y sin pisar
        // uno que ya tengas: tus mazos se guardan por nombre, y comprar el
        // mismo dos veces machacaria el primero con lo que le hubieras tocado.
        final forge.deck.Deck copy = new forge.deck.Deck(precon.getDeck(), freeDeckName(cleanDeckName(precon.getName())));
        if (NeoQuest.modalidad() != NeoQuest.Modalidad.COMMANDER) {
            // En una Quest de Estandar no hay zona de mando: el comandante
            // se quedaria en una seccion que la partida no lee y el editor no
            // ensenya. Pasa al mazo, y se juegan las 100.
            for (final java.util.Map.Entry<PaperCard, Integer> e : copy.get(forge.deck.DeckSection.Commander)) {
                copy.getMain().add(e.getKey(), e.getValue());
            }
            copy.getOrCreate(forge.deck.DeckSection.Commander).clear();
        }
        final List<PaperCard> cards = copy.getAllCardsInASinglePool().toFlatList();
        NeoQuest.engine().getCards().buyPreconDeck(new forge.item.PreconDeck(copy, precon.getEdition(), null), price);
        NeoQuest.dropSideboards();
        return finish(cleanDeckName(precon.getName()), new ArrayList<>(cards), before, price);
    }

    /** "Ahoy Mateys [LCC] [2023]" -> "Ahoy Mateys": el codigo y el anyo ya se ven en la tienda. */
    public static String cleanDeckName(final String name) {
        if (name == null) {
            return "";
        }
        final String clean = name.replaceAll("\\s*\\[[^\\]]*\\]", "").trim();
        return clean.isEmpty() ? name : clean;
    }

    /** Un nombre que no tengas ya entre tus mazos: "X", "X (2)", "X (3)"... */
    private static String freeDeckName(final String base) {
        final forge.util.storage.IStorage<forge.deck.Deck> mine = NeoQuest.engine().getMyDecks();
        if (mine == null || !mine.contains(base)) {
            return base;
        }
        int n = 2;
        while (mine.contains(base + " (" + n + ")")) {
            n++;
        }
        return base + " (" + n + ")";
    }

    // ===============================================================
    // Los sets de Commander (09-10-2026)
    // ===============================================================

    /**
     * Lo que cuesta un sobre de un set de Commander: trae dos raras o miticas
     * (el de una expansion normal, una), y las cartas de esos sets son de las
     * que mas se buscan para un mazo de Commander.
     */
    public static final int COMMANDER_PACK_PRICE = 650;

    /** Cuantas cartas trae un sobre de un set de Commander. */
    static final int COMMANDER_PACK_SIZE = 12;

    private static final java.util.Map<String, forge.item.SealedTemplate> COMMANDER_TEMPLATES =
            new java.util.HashMap<>();

    /**
     * Un set de Commander que en Forge no tiene sobre: los mazos de cada anyo
     * (C13...C21), los de cada expansion (BLC, DSC, TDC...), Commander Masters...
     * 48 en total. Discord, 09-10-2026: <i>"where are the commander sets in
     * quest? A majority of cards I want in quest are usually in commander
     * sets"</i>. En la vida real son mazos, no sobres, asi que la tienda no
     * tenia de donde sacarlos: ahora les monta un sobre propio (ver
     * {@link #commanderTemplate}) y vende los mazos aparte.
     */
    public static boolean isCommanderSet(final CardEdition e) {
        return e != null && e.getType() == CardEdition.Type.COMMANDER && !e.hasBoosterTemplate();
    }

    /**
     * Otras expansiones que Forge trae SIN sobre y a las que se les monta uno,
     * como a las de Commander, con su tamanyo (Discord, 22-10-2026: <i>"Add the
     * Assassin's Creed ... and Mystery box cards to Quest"</i>). Assassin's Creed
     * se vendia en "Beyond Boosters" de 7 cartas; Mystery Booster 2 y sus cartas
     * de prueba no tienen plantilla en Forge, ni el Mystery Booster Commander.
     * Una lista y no una regla: "sin sobre" tambien son los avatares de MTGO o
     * sets de tres cartas.
     *
     * <p><b>Si Forge les pone sobre propio, el nuestro se apaga solo</b>
     * ({@link #hasBuiltPack} exige que no tenga plantilla): no salen dos. Y
     * {@code packcheck} se pone en rojo, que es el aviso para borrar su linea.
     */
    private static final java.util.Map<String, Integer> BUILT_PACKS = java.util.Map.of(
            "ACR", 7, "MB2", 12, "CMB1", 12,
            // Mystery Booster Commander Edition (Forge, 19-10-2026): sus comandantes
            // nuevos (Worzel, Thomil...).  Discord, 24-10-2026: "Are the last
            // mystery box commanders on quest?"
            "MBC", 20);

    /** Si la tienda le monta un sobre propio: un set de Commander o uno de {@link #BUILT_PACKS}. */
    public static boolean hasBuiltPack(final CardEdition e) {
        return isCommanderSet(e)
                || e != null && !e.hasBoosterTemplate() && BUILT_PACKS.containsKey(e.getCode());
    }

    /**
     * El sobre de un set de Commander: {@value #COMMANDER_PACK_SIZE} cartas,
     * dos de ellas raras o miticas, y el resto comunes e infrecuentes si el set
     * tiene bastantes (los hay casi sin comunes: entonces cualquiera del set).
     * En el mismo dialecto que el colector, y la lee el generador de Forge.
     */
    public static synchronized forge.item.SealedTemplate commanderTemplate(final CardEdition edition) {
        if (!hasBuiltPack(edition)) {
            return null;
        }
        final int size = isCommanderSet(edition) ? COMMANDER_PACK_SIZE
                : BUILT_PACKS.getOrDefault(edition.getCode(), COMMANDER_PACK_SIZE);
        final String code = edition.getCode();
        if (COMMANDER_TEMPLATES.containsKey(code)) {
            return COMMANDER_TEMPLATES.get(code);
        }
        if ("MBC".equals(code)) {
            forge.item.SealedTemplate mbc = null;
            try {
                mbc = mysteryCommanderTemplate(edition);
            } catch (final RuntimeException e) {
                System.err.println("[tienda] no se ha podido montar el sobre de MBC: " + e);
            }
            COMMANDER_TEMPLATES.put(code, mbc);
            return mbc;
        }
        int commons = 0;
        int uncommons = 0;
        int rares = 0;
        for (final forge.card.CardEdition.EditionEntry c : edition.getAllCardsInSet()) {
            if (c.rarity() == forge.card.CardRarity.Common) {
                commons++;
            } else if (c.rarity() == forge.card.CardRarity.Uncommon) {
                uncommons++;
            } else if (c.rarity() == forge.card.CardRarity.Rare || c.rarity() == forge.card.CardRarity.MythicRare) {
                rares++;
            }
        }
        // Con 12 cartas: 2 raras, 5 comunes, 4 infrecuentes (o 7 si no hay
        // comunes) y el resto de cualquiera; con 7, la mitad de cada cosa.
        final boolean big = size >= 10;
        final int rm = rares >= 3 ? (big ? 2 : 1) : 0;
        final int c = commons >= 10 ? (big ? 5 : 3) : 0;
        final int u = uncommons >= 6 ? Math.min(size - rm - c, c > 0 ? size / 3 : size * 7 / 12) : 0;
        final int any = size - rm - c - u;
        final List<String> parts = new ArrayList<>();
        if (c > 0) {
            parts.add(c + " Common");
        }
        if (u > 0) {
            parts.add(u + " Uncommon");
        }
        if (rm > 0) {
            parts.add(rm + " RareMythic");
        }
        if (any > 0) {
            parts.add(any + " Any");
        }
        // Con huecos con nombre (ver mysteryCommanderTemplate): con la plantilla
        // de siempre, un set con el foil "al final del sobre" y sin hueco de
        // comunes - CMB1 - sacaba a veces una carta de mas (13 de 12).
        forge.item.SealedTemplate t = null;
        try {
            final List<org.apache.commons.lang3.tuple.Pair<String, Integer>> slots = new ArrayList<>();
            final List<forge.item.BoosterSlot> defs = new ArrayList<>();
            for (final String part : parts) {
                final String[] kv = part.split(" ", 2);
                slot(slots, defs, kv[1], Integer.parseInt(kv[0]), kv[1]);
            }
            t = new forge.item.SealedTemplateWithSlots(code, slots, defs);
        } catch (final RuntimeException e) {
            System.err.println("[tienda] no se ha podido montar el sobre de " + code + ": " + e);
        }
        COMMANDER_TEMPLATES.put(code, t);
        return t;
    }

    /**
     * EL SOBRE DEL MYSTERY BOOSTER COMMANDER, COMO EL DE VERDAD (Ana,
     * 24-10-2026: "que traiga lo mismo que trae los de verdad respecto a
     * reparto de rareza").  20 cartas, hueco a hueco como lo cuenta Wizards
     * ("What's Inside Mystery Booster Commander Edition"):
     * <ul>
     * <li>15 comunes o infrecuentes, 3 de cada color;</li>
     * <li>1 comodin de reimpresion, de cualquier rareza;</li>
     * <li>1 comandante reimpreso, raro o mitico;</li>
     * <li>1 comandante nuevo (los 60);</li>
     * <li>1 carta con marco de Future Sight;</li>
     * <li>1 carta de prueba (las 121).</li>
     * </ul>
     * Forge solo trae de MBC sus 60 comandantes y 20 cartas mas, no las listas
     * de reimpresiones ni las de marco de Future Sight, asi que donde falta la
     * de verdad va la mas parecida que si esta: las listas por color del
     * Mystery Booster original (MB1, tambien reimpresiones con el sello del
     * planeswalker), sus cartas de marco antiguo para el hueco de Future Sight,
     * los comandantes raros y miticos de los mazos de Commander, y las 121 de
     * prueba de CMB1, que son justo las mismas.  Cuando Forge traiga el sobre
     * de verdad, este se apaga solo ({@link #hasBuiltPack}).
     *
     * <p>⚠️ <b>Con huecos con nombre</b> ({@code SealedTemplateWithSlots}), no
     * con la plantilla de siempre: esa aplica las reglas de foil del set, y con
     * MBC el foil "al final del sobre" se sumaba una vez por cada hueco que
     * empezara igual - salian sobres de 26.  Esta ignora el set y cada hueco
     * lleva su probabilidad: el comandante nuevo sale foil un 4,75 % de las
     * veces, como en el de verdad.
     */
    private static forge.item.SealedTemplateWithSlots mysteryCommanderTemplate(final CardEdition mbc) {
        final List<String> news = new ArrayList<>();
        final List<String> extras = new ArrayList<>();
        for (final CardEdition.EditionEntry e : mbc.getAllCardsInSet()) {
            int n;
            try {
                n = Integer.parseInt(e.collectorNumber());
            } catch (final RuntimeException ex) {
                n = Integer.MAX_VALUE;
            }
            (n <= 60 ? news : extras).add(e.name());
        }
        final java.util.Set<String> reprinted = new java.util.TreeSet<>();
        for (final CardEdition ed : FModel.getMagicDb().getEditions()) {
            if (ed.getType() != CardEdition.Type.COMMANDER) {
                continue;
            }
            for (final CardEdition.EditionEntry e : ed.getAllCardsInSet()) {
                if (e.rarity() != forge.card.CardRarity.Rare && e.rarity() != forge.card.CardRarity.MythicRare) {
                    continue;
                }
                final forge.card.CardRules rules = FModel.getMagicDb().getCommonCards().getRules(e.name(), false);
                if (rules != null && rules.canBeCommander() && !news.contains(e.name())) {
                    reprinted.add(e.name());
                }
            }
        }
        final List<String> playtest = new ArrayList<>();
        final CardEdition cmb1 = FModel.getMagicDb().getEditions().get("CMB1");
        if (cmb1 != null) {
            for (final CardEdition.EditionEntry e : cmb1.getAllCardsInSet()) {
                playtest.add(e.name());
            }
        }
        final List<org.apache.commons.lang3.tuple.Pair<String, Integer>> slots = new ArrayList<>();
        final List<forge.item.BoosterSlot> defs = new ArrayList<>();
        for (final String color : new String[] {"White", "Blue", "Black", "Red", "Green"}) {
            slot(slots, defs, color, 3, "fromSheet(\"MB1 " + color + " CommonUncommon\")");
        }
        slot(slots, defs, "Wildcard", 1, names("name", extras));
        slot(slots, defs, "ReprintedCommander", 1, names("promo", new ArrayList<>(reprinted)));
        final String newOnes = names("name", news);
        slot(slots, defs, "NewCommander", 1, newOnes, "Replace=.0475 " + newOnes + "+");
        slot(slots, defs, "FutureSight", 1, "fromSheet(\"MB1 Pre M15\")");
        slot(slots, defs, "Playtest", 1, names("promo", playtest));
        return new forge.item.SealedTemplateWithSlots(mbc.getCode(), slots, defs);
    }

    /** Un hueco con nombre: cuantas cartas, de donde, y los reemplazos con su probabilidad. */
    private static void slot(final List<org.apache.commons.lang3.tuple.Pair<String, Integer>> slots,
                             final List<forge.item.BoosterSlot> defs, final String name, final int count,
                             final String base, final String... replace) {
        slots.add(org.apache.commons.lang3.tuple.Pair.of(name, count));
        final List<String> contents = new ArrayList<>();
        contents.add("Base=" + base);
        contents.addAll(java.util.Arrays.asList(replace));
        defs.add(new forge.item.BoosterSlot(name, contents));
    }

    /**
     * Una lista de cartas en el dialecto de los sobres de Forge:
     * {@code name("A, la primera","B")}.  OJO, no hacen lo mismo: {@code name}
     * FILTRA las impresiones del set del sobre (MBC) y {@code promo} es la
     * lista tal cual, de cualquier set - con {@code name} los comandantes
     * reimpresos y las cartas de prueba salian vacios.  Fuera lo que ese dialecto no sabe
     * leer dentro de la lista (comillas, parentesis, punto y coma) y lo que la
     * base de datos no tiene, que dejaria un hueco nulo en el sobre.
     */
    private static String names(final String op, final List<String> list) {
        final List<String> ok = new ArrayList<>();
        for (final String n : list) {
            if (n == null || n.indexOf('"') >= 0 || n.indexOf('(') >= 0 || n.indexOf(')') >= 0
                    || n.indexOf(';') >= 0 || FModel.getMagicDb().getCommonCards().getCard(n) == null) {
                continue;
            }
            ok.add("\"" + n + "\"");
        }
        return op + "(" + String.join(",", ok) + ")";
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
        if (isCommanderSet(FModel.getMagicDb().getEditions().get(pack.getEdition()))) {
            return COMMANDER_PACK_PRICE;
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
        /** Las que te faltaban en algun mazo: nombre en minusculas -> esos mazos. */
        private final java.util.Map<String, List<String>> wanted;

        Opened(final List<PaperCard> cards, final Set<String> fresh, final int paid,
               final java.util.Map<String, List<String>> wanted) {
            this.cards = cards;
            this.fresh = fresh;
            this.paid = paid;
            this.wanted = wanted;
        }

        /**
         * Los mazos a los que les faltaba esta carta ANTES de abrir, o una
         * lista vacia. Ver {@link NeoQuest#wantedDecksByName}.
         */
        public List<String> wantedIn(final PaperCard card) {
            if (card == null) {
                return List.of();
            }
            return wanted.getOrDefault(card.getName().toLowerCase(java.util.Locale.ROOT), List.of());
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
     * <b>Varios sobres de una vez</b>, de la misma expansion (Discord,
     * 09-10-2026: <i>"add an option to buy multiple packs at once ... it is
     * really annoying to moving the mouse around the complete monitor all the
     * time"</i>).
     *
     * <p>Cada sobre se monta NUEVO ({@link #boosterOf}): un {@code SealedProduct}
     * memoriza su contenido, y comprar el mismo diez veces serian diez veces las
     * mismas cartas. Se cobran uno a uno por {@code buyPack} — el mismo camino que
     * uno solo — pero se guarda <b>una</b> vez al final, y "nuevas" cuenta contra
     * lo que tenias ANTES del primero.
     *
     * @return lo que ha salido de todos juntos, o null si no te llega para
     *         todos (entonces no se compra ninguno)
     */
    public static Opened buyAndOpen(final CardEdition edition, final int count) {
        if (edition == null || count < 1 || !NeoQuest.isActive()) {
            return null;
        }
        if (count == 1) {
            return buyAndOpen(boosterOf(edition));
        }
        return buyMany(edition, count, NeoQuestShop::boosterOf, NeoQuestShop::priceOf);
    }

    /**
     * Lo comun a comprar varios sobres de una vez, normales o de colector: cada
     * uno montado NUEVO, todos abiertos antes de cobrar el primero, y una sola
     * escritura al final.
     */
    private static Opened buyMany(final CardEdition edition, final int count,
                                  final java.util.function.Function<CardEdition, BoosterPack> make,
                                  final java.util.function.ToIntFunction<BoosterPack> pricing) {
        final List<BoosterPack> packs = new ArrayList<>(count);
        int total = 0;
        for (int i = 0; i < count; i++) {
            final BoosterPack pack = make.apply(edition);
            if (pack == null) {
                return null;
            }
            packs.add(pack);
            total += pricing.applyAsInt(pack);
        }
        if (NeoQuest.credits() < total) {
            return null;
        }
        // Abiertos ANTES de cobrar, como en open(): si uno revienta, no se
        // cobra ninguno.
        try {
            for (final BoosterPack pack : packs) {
                pack.getCards();
            }
        } catch (final RuntimeException e) {
            System.err.println("[tienda] no se han podido abrir los sobres de " + edition.getCode()
                    + ", no se cobra: " + e);
            return null;
        }
        final Before before = Before.now();
        final List<PaperCard> cards = new ArrayList<>();
        for (final BoosterPack pack : packs) {
            NeoQuest.engine().getCards().buyPack(pack, pricing.applyAsInt(pack));
            cards.addAll(pack.getCards());
        }
        return finish(count + " x " + packs.get(0).getName(), cards, before, total);
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
        final Before before = Before.now();

        // El sobre se abre ANTES de cobrar. Forge cobra y despues abre
        // (QuestUtilCards.buyPack), asi que un sobre que revienta al montarse
        // se llevaba los creditos sin dar nada: le pasaba al de colector de
        // DMR, DMU, DOM y WAR (ver collectorTemplate, 09-10-2026). getCards()
        // memoriza, asi que buyPack mete exactamente estas.
        try {
            pack.getCards();
        } catch (final RuntimeException e) {
            System.err.println("[tienda] no se ha podido abrir " + pack.getName() + ", no se cobra: " + e);
            return null;
        }

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
    /**
     * Lo de ANTES de comprar: que tenias (para "nueva") y que te faltaba en tus
     * mazos (para "te faltaba en X"). Despues de comprar ya no se puede saber.
     */
    private static final class Before {
        final Set<String> owned;
        final java.util.Map<String, List<String>> wanted;

        private Before(final Set<String> owned, final java.util.Map<String, List<String>> wanted) {
            this.owned = owned;
            this.wanted = wanted;
        }

        static Before now() {
            return new Before(ownedNames(), NeoQuest.wantedDecksByName());
        }
    }

    static Set<String> ownedNames() {
        final Set<String> before = new HashSet<>();
        for (final java.util.Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            before.add(key(e.getKey()));
        }
        return before;
    }

    /** Guarda, traza y dice cuales eran nuevas. Comun a todo lo que se compra. */
    private static Opened finish(final String what, final List<PaperCard> cards,
                                 final Before before, final int price) {
        final Set<String> fresh = new HashSet<>();
        final java.util.Map<String, List<String>> wanted = new java.util.HashMap<>();
        for (final PaperCard card : cards) {
            if (!before.owned.contains(key(card))) {
                fresh.add(key(card));
            }
            final String k = card.getName().toLowerCase(java.util.Locale.ROOT);
            final List<String> decks = before.wanted.get(k);
            if (decks != null) {
                wanted.put(k, decks);
            }
        }
        NeoQuest.save();
        System.out.printf(java.util.Locale.ROOT,
                "[tienda] %s por %s -> %d cartas (%d nuevas, %d que te faltaban en un mazo: %s), quedan %d cr.%n",
                what, price > 0 ? price + " cr." : "PREMIO",
                cards.size(), fresh.size(), wanted.size(), wanted, NeoQuest.credits());
        return new Opened(cards, fresh, price, wanted);
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
        final Before before = Before.now();
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

    /**
     * Las cajas y lotes: caja de sobres, fat pack y tournament pack. Y delante,
     * los cinco sobres de tierras basicas ({@link LandPack}), que no los repone
     * el motor: estan siempre, en cualquier mundo.
     */
    public static List<forge.item.SealedProduct> boxes() {
        final List<forge.item.SealedProduct> out = new ArrayList<>();
        out.addAll(LandPack.onSale());
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
        if (item instanceof LandPack) {
            return LandPack.PRICE;
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
        final Before before = Before.now();
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
        final Before before = Before.now();
        NeoQuest.engine().getCards().buyPack(product, price);
        // Los de tierras no estan en el mostrador del motor: no hay que retirarlos.
        if (!(product instanceof LandPack)) {
            NeoQuest.engine().getAssets().getShopList().remove(product);
        }
        return finish(productName(product), new ArrayList<>(product.getCards()), before, price);
    }

    /**
     * Como se llama un producto en la tienda. El de un {@link SealedProduct} es
     * {@code final} y le pega el tipo detras ("X Booster Box"); el de tierras
     * tiene el suyo.
     */
    public static String productName(final forge.item.SealedProduct product) {
        if (product instanceof LandPack) {
            return ((LandPack) product).displayName();
        }
        return product == null ? "" : product.getName();
    }

    /** La carta con la que se ensenya un producto, o null (solo los de tierras la tienen). */
    public static PaperCard faceOfProduct(final forge.item.SealedProduct product) {
        return product instanceof LandPack ? ((LandPack) product).getLand() : null;
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
            // Los de Commander no: su sobre ya es nuestro, y un colector montado
            // encima con comunes que el set casi no tiene saldria cojo. Ni los
            // demas que se montan aqui (hasBuiltPack), por lo mismo.
            if (hasBuiltPack(e) || artSheetsOf(e).isEmpty()) {
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
        final forge.item.SealedTemplate template = collectorTemplate(edition);
        return template == null ? null : new BoosterPack(edition.getName() + " Collector", template);
    }

    /**
     * La plantilla del sobre de colector (ver {@link #collectorBooster}), o
     * {@code null}. Aparte porque {@link PackContents} la recorre para decir
     * que puede salir en el.
     */
    static forge.item.SealedTemplate collectorTemplate(final CardEdition edition) {
        final List<String> sheets = artSheetsOf(edition);
        if (edition == null || sheets.isEmpty()) {
            return null;
        }
        final String code = edition.getCode();
        // ⚠️ Sin "+" (foil) en las expansiones que garantizan una carta por
        // sobre (BoosterMustContain: DMR, DMU y DOM una criatura legendaria, WAR
        // un planeswalker). Forge, para garantizarla, monta la hoja de cada
        // hueco con el "+" pegado al nombre y revienta ("operator could not be
        // parsed - RareMythic+") cada vez que el sobre no la trae por si solo:
        // en WAR casi siempre. Lo encontro PackContentsCheck (09-10-2026).
        final String must = edition.getBoosterMustContain();
        final String foil = must == null || must.trim().isEmpty() ? "+" : "";
        final StringBuilder desc = new StringBuilder("5 Common, 4 Uncommon, 1 RareMythic" + foil);
        for (final String sheet : sheets) {
            desc.append(", 1 fromSheet(\"").append(code).append(' ').append(sheet).append("\")");
        }
        if (hasSheet(edition, "fullart")) {
            desc.append(", 1 Land:fromSheet(\"").append(code).append(" fullart\")").append(foil);
        }
        try {
            return new forge.item.SealedTemplate(code, desc.toString());
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

    /**
     * <b>Varios de colector de una vez</b>, como los normales (Discord,
     * 11-10-2026: <i>"I was happy that buying boosters back to back was added
     * but was there a reason that same system couldn't be added to the
     * collector variants?"</i>). Lo mismo que {@link #buyAndOpen(CardEdition, int)}:
     * cada sobre se monta nuevo y, si no llega para todos, no se compra ninguno.
     */
    public static Opened buyCollector(final CardEdition edition, final int count) {
        if (edition == null || count < 1 || !NeoQuest.isActive()) {
            return null;
        }
        if (count == 1) {
            return buyCollector(edition, collectorBooster(edition));
        }
        final int price = collectorPrice(edition);
        return buyMany(edition, count, NeoQuestShop::collectorBooster, pack -> price);
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
        if (!secretLairAllowed()) {
            return List.of();
        }
        final List<PaperCard> pool = new ArrayList<>(secretLairPool());
        if (pool.isEmpty()) {
            return List.of();
        }
        java.util.Collections.shuffle(pool, forge.util.MyRandom.getRandom());
        return new ArrayList<>(pool.subList(0, Math.min(SECRET_LAIR_SIZE, pool.size())));
    }

    public static boolean canAffordSecretLair() {
        return NeoQuest.isActive() && secretLairAllowed() && NeoQuest.credits() >= SECRET_LAIR_PRICE;
    }

    /**
     * Si Secret Lair se vende en esta Quest: siempre, salvo en una limitada a
     * unas expansiones que no lo incluyan (un bloque de Zendikar no trae
     * exclusivas de 2024).
     */
    public static boolean secretLairAllowed() {
        final java.util.Set<String> sets = NeoQuest.chosenSets();
        if (sets == null) {
            return true;
        }
        for (final String code : SECRET_LAIR_SETS) {
            if (sets.contains(code)) {
                return true;
            }
        }
        return false;
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
        final Before before = Before.now();
        NeoQuest.engine().getAssets().subtractCredits(SECRET_LAIR_PRICE);
        NeoQuest.engine().getCards().addAllCards(cards);
        return finish("Secret Lair", new ArrayList<>(cards), before, SECRET_LAIR_PRICE);
    }

    // ===============================================================
    // Jumpstart
    // ===============================================================

    /**
     * Lo que cuesta un sobre Jumpstart: vez y media el normal de su expansion
     * (veinte cartas, con tierras, una rara o mitica asegurada). Las que no
     * tienen sobre normal (JMP, J22, J25) van con el precio de siempre.
     */
    public static int jumpstartPrice(final NeoJumpstart.Product product) {
        return jumpstartPrice(product.setCode);
    }

    public static int jumpstartPrice(final NeoJumpstart.Theme theme) {
        return jumpstartPrice(theme.setCode);
    }

    private static int jumpstartPrice(final String setCode) {
        int base = DEFAULT_PRICE;
        try {
            final CardEdition e = FModel.getMagicDb().getEditions().get(setCode);
            final BoosterPack normal = e == null ? null : boosterOf(e);
            if (normal != null) {
                base = priceOf(normal);
            }
        } catch (final RuntimeException ignored) {
            // Sin sobre normal que mirar: el de siempre.
        }
        return base * 3 / 2;
    }

    public static boolean canAffordJumpstart(final NeoJumpstart.Product product) {
        return NeoQuest.isActive() && NeoQuest.credits() >= jumpstartPrice(product);
    }

    /** Compra un sobre Jumpstart: trae UN tema al azar de los suyos, como el de verdad. */
    public static Opened buyJumpstart(final NeoJumpstart.Product product) {
        return product == null || product.themes.isEmpty() ? null : buyJumpstart(product.draw());
    }

    /**
     * Compra un tema Jumpstart: sus veinte cartas a la coleccion. No pasa por
     * {@code buyPack} (no es un sobre de expansion), como Secret Lair: se cobra
     * y se meten las cartas, y lo que trae se saca ANTES de cobrar.
     */
    public static Opened buyJumpstart(final NeoJumpstart.Theme theme) {
        if (theme == null || !NeoQuest.isActive()) {
            return null;
        }
        final int price = jumpstartPrice(theme);
        if (NeoQuest.credits() < price) {
            return null;
        }
        final List<PaperCard> cards = theme.contents();
        if (cards.isEmpty()) {
            return null;
        }
        final Before before = Before.now();
        NeoQuest.engine().getAssets().subtractCredits(price);
        NeoQuest.engine().getCards().addAllCards(cards);
        // Y se apunta el sobre, para montar mazos Jumpstart con el (Discord, 26-10-2026).
        NeoQuestJumpstart.record(theme.name(), cards);
        return finish(theme.name(), new ArrayList<>(cards), before, price);
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
        return secretLairAllowed() ? SecretLairDrops.all() : List.of();
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
        final Before before = Before.now();
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
