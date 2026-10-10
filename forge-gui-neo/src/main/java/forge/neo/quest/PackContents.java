package forge.neo.quest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;

import org.apache.commons.lang3.tuple.Pair;

import forge.StaticData;
import forge.card.CardEdition;
import forge.card.PrintSheet;
import forge.item.BoosterSlot;
import forge.item.PaperCard;
import forge.item.SealedTemplate;
import forge.item.SealedTemplateWithSlots;
import forge.item.generation.BoosterGenerator;
import forge.util.TextUtil;

/**
 * <b>Que puede salir en un sobre de la tienda, y cada cuanto.</b>
 *
 * <p>Pedido en Discord (09-10-2026): <i>"a feature in quest to look up which
 * pack a specific card would be in from the shop, or even a way to preview the
 * possible pulls from a pack, with a search function. I'm new to mtg so knowing
 * where to pull for the cards I want would be super helpful"</i>.
 *
 * <h2>De donde sale: del propio generador</h2>
 *
 * <p>Un sobre de Forge es una plantilla de huecos ({@code "6 Common, 1
 * RareMythic, ..."}) y cada hueco tira de una <b>hoja</b>: la de comunes de la
 * expansion, una {@code fromSheet("BLB special guests")}, la de tierras full-art.
 * Los huecos con nombre (Bloomburrow y casi todo lo moderno) llevan ademas una
 * base y unos reemplazos con su porcentaje ({@code Replace=.015625F ...}). Aqui
 * se recorre la plantilla <b>igual que {@code BoosterGenerator}</b> y cada hoja
 * se monta con su propio {@code BoosterGenerator.makeSheet}, que es publico: la
 * lista es la del generador, no "las cartas de la expansion" (que no es lo
 * mismo: hay cartas del set que no salen en sobre, y cartas de otras hojas que
 * si). Lo vigila {@code PackContentsCheck}, abriendo sobres de verdad.
 *
 * <h2>La probabilidad</h2>
 *
 * <p>Las hojas llevan peso (en {@code RareMythic} cada rara va dos veces y cada
 * mitica una: la mitica sale una de cada ocho), asi que de cada carta se sabe
 * cuantas salen de media por sobre: huecos x probabilidad de esa hoja x su peso
 * en ella. Con varios tipos de sobre (la tienda elige uno al azar,
 * {@code CardEdition.getRandomBoosterKind}) se hace la media. Es aproximada en
 * las expansiones viejas con foils y reemplazos raros — por eso la pantalla
 * dice "≈" — pero el orden de magnitud es el bueno, que es lo que se necesita
 * para saber donde buscar.
 *
 * <h2>Lo unico por reflexion</h2>
 *
 * <p>La base y los reemplazos de un hueco con nombre ({@code BoosterSlot}) son
 * privados y sin getter. Se leen por reflexion; si un dia Card-Forge los
 * renombra, el hueco se queda solo con lo que se pueda saber y
 * {@code PackContentsCheck} se pone en rojo (le faltarian cartas).
 *
 * <p>Sin JavaFX: lo usa tambien Android.
 */
public final class PackContents {

    private PackContents() {
    }

    /** Una carta que puede salir, y cuantas salen de media por sobre. */
    public static final class Pull {
        private final PaperCard card;
        private final double perPack;

        Pull(final PaperCard card, final double perPack) {
            this.card = card;
            this.perPack = perPack;
        }

        /** La impresion que sale (la de la hoja, sin foil). */
        public PaperCard getCard() {
            return card;
        }

        /** Cuantas salen de media por sobre (casi siempre, la probabilidad de que salga). */
        public double getPerPack() {
            return perPack;
        }

        /** "Una de cada N sobres": 1 si sale en todos (o casi). */
        public int getOneIn() {
            return perPack >= 0.95 ? 1 : (int) Math.max(1, Math.round(1.0 / Math.max(perPack, 1e-9)));
        }
    }

    /** Donde sale una carta: un sobre de la tienda y su probabilidad (todas sus impresiones). */
    public static final class Source {
        private final CardEdition edition;
        private final boolean collector;
        private final Pull pull;

        Source(final CardEdition edition, final boolean collector, final Pull pull) {
            this.edition = edition;
            this.collector = collector;
            this.pull = pull;
        }

        public CardEdition getEdition() {
            return edition;
        }

        /** El sobre de colector de NeoForge, no el normal. */
        public boolean isCollector() {
            return collector;
        }

        /** La impresion mas probable de la carta en ese sobre, con la suma de todas. */
        public Pull getPull() {
            return pull;
        }
    }

    /** Lo ya calculado, por expansion ("BLB" o "BLB+collector"). */
    private static final Map<String, List<Pull>> CACHE = new HashMap<>();
    /** Las hojas ya montadas, por su clave: muchas se repiten entre tipos de sobre. */
    private static final Map<String, PrintSheet> SHEETS = new HashMap<>();
    /** Las cartas de cada expansion, para montar una hoja sin recorrer las 95.000 impresiones. */
    private static Map<String, List<PaperCard>> bySet;

    // ------------------------------------------------------------------
    //  Lo que sale en un sobre
    // ------------------------------------------------------------------

    /**
     * Lo que puede salir en un sobre de esa expansion, de la mas probable a la
     * menos (y por nombre a igualdad). Vacio si no tiene sobre.
     */
    public static List<Pull> of(final CardEdition edition) {
        if (edition == null) {
            return Collections.emptyList();
        }
        final String key = edition.getCode();
        // Todo el calculo bajo el mismo cerrojo: la tienda lo pide desde el
        // hilo de interfaz y el buscador desde otro, y asi no se pisan entre
        // ellos (las caches de aqui y la de hojas fijas de BoosterGenerator).
        // Una compra en el hilo de interfaz usa el generador sin este cerrojo;
        // solo coincidiria en los ~0,2 s de la primera carga del buscador, y lo
        // peor seria perder una entrada de esa cache del motor.
        synchronized (PackContents.class) {
            final List<Pull> known = CACHE.get(key);
            if (known != null) {
                return known;
            }
            final List<SealedTemplate> kinds = new ArrayList<>();
            for (final String kind : edition.getAvailableBoosterTypes()) {
                final SealedTemplate t = edition.getBoosterTemplate(kind);
                if (t != null) {
                    kinds.add(t);
                }
            }
            // Los sets de Commander no tienen sobre en Forge: el de la tienda.
            if (kinds.isEmpty() && NeoQuestShop.commanderTemplate(edition) != null) {
                kinds.add(NeoQuestShop.commanderTemplate(edition));
            }
            final List<Pull> out = average(edition, kinds);
            CACHE.put(key, out);
            return out;
        }
    }

    /** Lo que puede salir en el sobre de colector de NeoForge de esa expansion. */
    public static List<Pull> ofCollector(final CardEdition edition) {
        if (edition == null) {
            return Collections.emptyList();
        }
        final String key = edition.getCode() + "+collector";
        synchronized (PackContents.class) {
            final List<Pull> known = CACHE.get(key);
            if (known != null) {
                return known;
            }
            final SealedTemplate t = NeoQuestShop.collectorTemplate(edition);
            final List<Pull> out = t == null ? Collections.<Pull>emptyList()
                    : average(edition, Collections.singletonList(t));
            CACHE.put(key, out);
            return out;
        }
    }

    /** La media de varios tipos de sobre: la tienda elige uno al azar, cada uno con la misma probabilidad. */
    private static List<Pull> average(final CardEdition edition, final List<SealedTemplate> kinds) {
        final Map<PaperCard, Double> sum = new LinkedHashMap<>();
        for (final SealedTemplate t : kinds) {
            final Map<PaperCard, Double> one = new LinkedHashMap<>();
            try {
                walk(edition, t, one);
            } catch (final RuntimeException e) {
                System.err.println("[sobre] no se ha podido leer un sobre de " + edition.getCode() + ": " + e);
            }
            for (final Map.Entry<PaperCard, Double> e : one.entrySet()) {
                sum.merge(e.getKey(), e.getValue() / kinds.size(), Double::sum);
            }
        }
        final List<Pull> out = new ArrayList<>();
        for (final Map.Entry<PaperCard, Double> e : sum.entrySet()) {
            out.add(new Pull(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparingDouble((Pull p) -> -p.getPerPack())
                .thenComparing(p -> p.getCard().getName()));
        return Collections.unmodifiableList(out);
    }

    /**
     * Recorre una plantilla como {@code BoosterGenerator.getBoosterPack} y
     * apunta en {@code out} cuantas salen de media de cada carta.
     */
    private static void walk(final CardEdition edition, final SealedTemplate t, final Map<PaperCard, Double> out) {
        final String setCode = t.getEdition();
        if (t instanceof SealedTemplateWithSlots slots) {
            // Cada hueco: su base y sus reemplazos, cada uno con su probabilidad.
            for (final Pair<String, Integer> slot : slots.getSlots()) {
                final String name = stripFoil(slot.getLeft().trim());
                final BoosterSlot bs = slots.getNamedSlots().get(name);
                if (bs == null) {
                    continue;
                }
                for (final Map.Entry<String, Double> e : sheetsOf(bs).entrySet()) {
                    final PrintSheet ps = namedSheet(stripFoil(e.getKey()), setCode);
                    add(out, ps, slot.getRight() * e.getValue());
                }
            }
            return;
        }
        // La plantilla de siempre ("10 Common, 3 Uncommon, 1 RareMythic").
        for (final Pair<String, Integer> slot : t.getSlots()) {
            final String slotType = stripFoil(slot.getLeft());
            final String[] parts = TextUtil.splitWithParenthesis(slotType, ' ');
            final String code = parts.length == 1 && setCode != null ? setCode : null;
            final String key = code != null && StaticData.instance().getEditions().contains(code)
                    ? slotType.trim() + " " + code : slotType.trim();
            final PrintSheet ps = sheet(key);
            if (ps == null) {
                continue;
            }
            if (key.startsWith("wholeSheet")) {
                // La hoja entera, en cada sobre.
                for (final PaperCard c : ps.toFlatList()) {
                    out.merge(unfoil(c), 1.0, Double::sum);
                }
                continue;
            }
            if (slot.getRight() > 0) {
                add(out, ps, slot.getRight());
            } else if (edition != null && edition.getFoilType() != CardEdition.FoilType.NOT_SUPPORTED) {
                // Un hueco de 0 cartas ("0 Special" en Vintage Masters) da carta
                // por el FOIL: el generador lo elige de vez en cuando como hueco
                // del foil (las Power 9 de VMA, los Time Spiral especiales, las
                // de dos caras). Pocas veces: aproximado, pero que esten.
                add(out, ps, edition.getFoilChanceInBooster() * 0.02);
            }
        }
        if (edition == null || setCode == null || !setCode.equals(edition.getCode())) {
            return;
        }
        // Lo que la expansion anyade por su cuenta. Las probabilidades son
        // aproximadas (el generador decide en mitad del sobre); lo que importa
        // es que esas cartas ESTEN.
        final double foil = edition.getFoilType() == CardEdition.FoilType.NOT_SUPPORTED
                ? 0 : edition.getFoilChanceInBooster();
        addExtra(out, edition.getAdditionalSheetForFoils(), setCode, foil);
        addExtra(out, edition.getSlotReplaceCommonWith(), setCode, edition.getChanceReplaceCommonWith());
        addExtra(out, edition.getBoosterReplaceSlotFromPrintSheet(), null, 1.0);
        addReplace(out, edition.getSheetReplaceCardFromSheet());
        addReplace(out, edition.getSheetReplaceCardFromSheet2());
    }

    /** Una hoja extra de la expansion, con esa probabilidad de que salga una carta suya. */
    private static void addExtra(final Map<PaperCard, Double> out, final String sheetKey,
                                 final String setCode, final double chance) {
        if (sheetKey == null || sheetKey.trim().isEmpty() || chance <= 0) {
            return;
        }
        PrintSheet ps = null;
        if (setCode != null) {
            ps = sheet(sheetKey.trim() + " " + setCode);
        }
        if (ps == null) {
            ps = staticSheet(sheetKey.trim());
        }
        if (ps == null) {
            ps = sheet(sheetKey.trim());
        }
        add(out, ps, Math.min(1.0, chance));
    }

    /** {@code "A_B"}: una carta de la hoja A se cambia por una de la B. */
    private static void addReplace(final Map<PaperCard, Double> out, final String spec) {
        if (spec == null || spec.trim().isEmpty() || !spec.contains("_")) {
            return;
        }
        final String[] split = spec.split("_");
        if (split.length < 2) {
            return;
        }
        PrintSheet ps = staticSheet(split[1]);
        if (ps == null) {
            ps = sheet(split[1]);
        }
        add(out, ps, 1.0);
    }

    /** Las cartas de una hoja, repartiendo {@code draws} tiradas segun su peso. */
    private static void add(final Map<PaperCard, Double> out, final PrintSheet ps, final double draws) {
        if (ps == null || draws <= 0) {
            return;
        }
        final List<PaperCard> flat = ps.toFlatList();
        if (flat.isEmpty()) {
            return;
        }
        final double each = draws / flat.size();
        for (final PaperCard c : flat) {
            out.merge(unfoil(c), each, Double::sum);
        }
    }

    // ------------------------------------------------------------------
    //  Donde sale una carta
    // ------------------------------------------------------------------

    /**
     * En que sobres de esas expansiones sale una carta (por nombre, en
     * cualquiera de sus impresiones), del mas probable al menos. Mira el sobre
     * normal y, si se pide, el de colector.
     */
    public static List<Source> whereIs(final String cardName, final List<CardEdition> editions,
                                       final List<CardEdition> collectorEditions) {
        final List<Source> out = new ArrayList<>();
        if (cardName == null) {
            return out;
        }
        for (final CardEdition e : editions) {
            final Pull p = find(of(e), cardName);
            if (p != null) {
                out.add(new Source(e, false, p));
            }
        }
        if (collectorEditions != null) {
            for (final CardEdition e : collectorEditions) {
                final Pull p = find(ofCollector(e), cardName);
                if (p != null) {
                    out.add(new Source(e, true, p));
                }
            }
        }
        out.sort(Comparator.comparingDouble((Source s) -> -s.getPull().getPerPack())
                .thenComparing(s -> s.getEdition().getName()));
        return out;
    }

    /** Las impresiones de esa carta en la lista, sumadas; la mas probable da la cara. */
    private static Pull find(final List<Pull> pulls, final String name) {
        PaperCard best = null;
        double bestOdds = -1;
        double total = 0;
        for (final Pull p : pulls) {
            if (p.getCard().getName().equals(name)) {
                total += p.getPerPack();
                if (p.getPerPack() > bestOdds) {
                    bestOdds = p.getPerPack();
                    best = p.getCard();
                }
            }
        }
        return best == null ? null : new Pull(best, total);
    }

    /**
     * Todas las cartas (por nombre) que salen en algun sobre de esas
     * expansiones: lo que puede sugerir el buscador. Calcularlo la primera vez
     * monta todos los sobres de la tienda — llamarlo fuera del hilo de interfaz.
     */
    public static List<String> namesIn(final List<CardEdition> editions, final List<CardEdition> collectorEditions) {
        final java.util.TreeSet<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (final CardEdition e : editions) {
            for (final Pull p : of(e)) {
                names.add(p.getCard().getName());
            }
        }
        if (collectorEditions != null) {
            for (final CardEdition e : collectorEditions) {
                for (final Pull p : ofCollector(e)) {
                    names.add(p.getCard().getName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    // ------------------------------------------------------------------
    //  Las hojas
    // ------------------------------------------------------------------

    /**
     * La hoja de un hueco con nombre, como la busca el generador: primero con
     * el codigo de la expansion detras y, si eso no es una hoja, sin el.
     */
    private static PrintSheet namedSheet(final String key, final String setCode) {
        PrintSheet ps = setCode == null ? null : sheet(key + " " + setCode);
        if (ps == null) {
            ps = sheet(key);
        }
        return ps;
    }

    /**
     * Una hoja por su clave: {@code BoosterGenerator.makeSheet}, el mismo que
     * usa el generador, pero dandole solo las cartas de las expansiones de la
     * clave (el resultado es el mismo: la clave filtra por expansion) para no
     * recorrer las 95.000 impresiones en cada una. {@code null} si no existe.
     */
    private static PrintSheet sheet(final String key) {
        synchronized (PackContents.class) {
            if (SHEETS.containsKey(key)) {
                return SHEETS.get(key);
            }
        }
        PrintSheet ps;
        try {
            ps = BoosterGenerator.makeSheet(key, sourceFor(key));
        } catch (final RuntimeException e) {
            ps = null;
        }
        synchronized (PackContents.class) {
            SHEETS.put(key, ps);
        }
        return ps;
    }

    /** Una hoja fija de las del motor ({@code res/blockdata/printsheets.txt} o una seccion de edicion). */
    private static PrintSheet staticSheet(final String name) {
        try {
            return BoosterGenerator.tryGetStaticSheet(name);
        } catch (final RuntimeException e) {
            return StaticData.instance().getPrintSheets().get(name);
        }
    }

    /** Las cartas que necesita {@code makeSheet} para esa clave. */
    private static Iterable<PaperCard> sourceFor(final String key) {
        final String[] parts = TextUtil.splitWithParenthesis(key, ' ', 2);
        if (parts.length < 2) {
            return StaticData.instance().getCommonCards().getAllCards();
        }
        final Map<String, List<PaperCard>> index = bySet();
        final List<PaperCard> src = new ArrayList<>();
        for (final String code : parts[1].split(" ")) {
            final List<PaperCard> cards = index.get(code.toUpperCase(Locale.ROOT));
            if (cards != null) {
                src.addAll(cards);
            }
        }
        return src;
    }

    private static synchronized Map<String, List<PaperCard>> bySet() {
        if (bySet == null) {
            final Map<String, List<PaperCard>> m = new HashMap<>();
            for (final PaperCard c : StaticData.instance().getCommonCards().getAllCards()) {
                m.computeIfAbsent(c.getEdition().toUpperCase(Locale.ROOT), k -> new ArrayList<>()).add(c);
            }
            bySet = m;
        }
        return bySet;
    }

    /**
     * La base y los reemplazos de un hueco con nombre, con su probabilidad.
     * Son privados en {@code BoosterSlot}: ver el javadoc de la clase.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Double> sheetsOf(final BoosterSlot slot) {
        final Map<String, Double> out = new LinkedHashMap<>();
        try {
            final Field base = BoosterSlot.class.getDeclaredField("baseRarity");
            final Field pcts = BoosterSlot.class.getDeclaredField("slotPercentages");
            base.setAccessible(true);
            pcts.setAccessible(true);
            final NavigableMap<Float, String> ranges = (NavigableMap<Float, String>) pcts.get(slot);
            float last = 0f;
            for (final Map.Entry<Float, String> e : ranges.entrySet()) {
                out.merge(e.getValue(), (double) (e.getKey() - last), Double::sum);
                last = e.getKey();
            }
            final String b = (String) base.get(slot);
            if (b != null && last < 1f) {
                out.merge(b, (double) (1f - last), Double::sum);
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[sobre] no se han podido leer los huecos de " + slot.getSlotName() + ": " + e);
        }
        return out;
    }

    /** Las rarezas, en el orden en que se leen: de la que mas se busca a la que menos. */
    private static final forge.card.CardRarity[] RARITY_ORDER = {
        forge.card.CardRarity.MythicRare, forge.card.CardRarity.Rare, forge.card.CardRarity.Uncommon,
        forge.card.CardRarity.Common, forge.card.CardRarity.Special, forge.card.CardRarity.BasicLand,
    };

    /**
     * El grupo de una rareza para ensenyar lo que sale: 0 miticas, 1 raras, 2
     * infrecuentes, 3 comunes, 4 especiales, 5 basicas y 6 lo demas. Es la
     * clave {@code shop.rarity.N} de los textos. Aqui y no en la pantalla
     * porque Android agrupa igual.
     */
    public static int rarityRank(final forge.card.CardRarity rarity) {
        for (int i = 0; i < RARITY_ORDER.length; i++) {
            if (RARITY_ORDER[i] == rarity) {
                return i;
            }
        }
        return RARITY_ORDER.length;
    }

    private static String stripFoil(final String s) {
        return s.endsWith("+") ? s.substring(0, s.length() - 1) : s;
    }

    private static PaperCard unfoil(final PaperCard c) {
        return c.isFoil() ? c.getUnFoiled() : c;
    }
}
