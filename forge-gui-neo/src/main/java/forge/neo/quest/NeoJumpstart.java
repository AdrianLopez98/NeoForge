package forge.neo.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import forge.card.CardRarity;
import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.item.SealedTemplate;
import forge.item.generation.UnOpenedProduct;
import forge.model.CardBlock;
import forge.model.FModel;
import forge.model.MetaSet;

/**
 * LOS SOBRES JUMPSTART, para la tienda de la Quest.
 *
 * <p>Discord, 20-10-2026: <i>"where could I pull Hei Bai, Forest Guardian on
 * quest shop? checked on TLA booster but not in there"</i>. No estaba en
 * ningun sobre: es de la parte Jumpstart de TLE, y Forge tiene los Jumpstart
 * montados (los temas, "Hei Bai 1", "Shrines"...) pero ni su tienda ni la
 * nuestra los vendian. Ana: en la tienda, de todas las expansiones que los
 * tengan, en su pestanya.
 *
 * <p>De donde sale: {@code res/blockdata/blocks.txt} define cada Jumpstart como
 * un bloque con un {@code Meta-Choose} de temas ({@code S(TLA Hei Bai 1)Hei Bai
 * 1;...}), y cada tema es un sobre especial de {@code boosters-special.txt}
 * ({@code 1 wholeSheet("TLA Hei Bai 1")}): una lista FIJA de cartas, como el
 * sobre de verdad. El texto del Meta-Choose es privado en {@code MetaSet}: se
 * lee por reflexion, y si no se puede, no hay Jumpstart (la tienda sigue).
 *
 * <p>Java puro: lo usa Android por el jar (nada de API que no tenga Android 8).
 */
public final class NeoJumpstart {

    private NeoJumpstart() {
    }

    /** Un tema de un Jumpstart: lo que se compra. */
    public static final class Theme {
        /** El bloque: "Avatar: The Last Airbender Jumpstart". */
        public final String product;
        /** "Hei Bai 1". */
        public final String label;
        /** La expansion a la que va (la de las tierras del bloque): TLA. */
        public final String setCode;
        final SealedTemplate template;
        private List<PaperCard> contents;

        Theme(final String product, final String label, final String setCode, final SealedTemplate template) {
            this.product = product;
            this.label = label;
            this.setCode = setCode;
            this.template = template;
        }

        /** "Avatar: The Last Airbender Jumpstart · Hei Bai 1". */
        public String name() {
            return product + " · " + label;
        }

        /**
         * Lo que trae. Un tema es una lista fija ({@code wholeSheet}), asi que
         * sale siempre lo mismo y se puede ensenyar ANTES de comprar.
         */
        public synchronized List<PaperCard> contents() {
            if (contents == null) {
                List<PaperCard> got;
                try {
                    got = new UnOpenedProduct(template).get();
                } catch (final RuntimeException e) {
                    got = Collections.emptyList();
                }
                contents = Collections.unmodifiableList(new ArrayList<>(got));
            }
            return contents;
        }

        /** La carta de portada: la de mas rareza que no sea tierra basica. */
        public PaperCard face() {
            PaperCard best = null;
            for (final PaperCard c : contents()) {
                if (c.getRules().getType().isBasicLand()) {
                    continue;
                }
                if (best == null || rank(c.getRarity()) > rank(best.getRarity())) {
                    best = c;
                }
            }
            return best;
        }

        /** Sus raras y miticas, por nombre: lo que se busca y lo que se lee de un vistazo. */
        public String highlights() {
            final StringBuilder sb = new StringBuilder();
            for (final PaperCard c : contents()) {
                final CardRarity r = c.getRarity();
                if ((r == CardRarity.Rare || r == CardRarity.MythicRare) && sb.indexOf(c.getName()) < 0) {
                    if (sb.length() > 0) {
                        sb.append(" · ");
                    }
                    sb.append(c.getName());
                }
            }
            return sb.toString();
        }

        /** Todas, por nombre y con cuantas: "7 Forest, Hei Bai, Forest Guardian, ...". */
        public String cardList() {
            final java.util.LinkedHashMap<String, Integer> n = new java.util.LinkedHashMap<>();
            for (final PaperCard c : contents()) {
                final Integer was = n.get(c.getName());
                n.put(c.getName(), was == null ? 1 : was + 1);
            }
            final StringBuilder sb = new StringBuilder();
            for (final java.util.Map.Entry<String, Integer> e : n.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                if (e.getValue() > 1) {
                    sb.append(e.getValue()).append(' ');
                }
                sb.append(e.getKey());
            }
            return sb.toString();
        }
    }

    /**
     * UN JUMPSTART, como se vende: un sobre que trae UN tema al azar de los
     * suyos (Ana, 20-10-2026: "que jumpstart sean sobres, no todas las cartas
     * sueltas"). Lo que hay dentro no se elige: se descubre al abrirlo.
     */
    public static final class Product {
        public final String name;
        public final String setCode;
        public final List<Theme> themes;

        Product(final String name, final String setCode, final List<Theme> themes) {
            this.name = name;
            this.setCode = setCode;
            this.themes = Collections.unmodifiableList(themes);
        }

        /** Un tema al azar: lo que trae el sobre que se compra. */
        public Theme draw() {
            return themes.get(forge.util.MyRandom.getRandom().nextInt(themes.size()));
        }

        /** La portada: la carta gorda de su primer tema. */
        public PaperCard face() {
            for (final Theme t : themes) {
                final PaperCard f = t.face();
                if (f != null) {
                    return f;
                }
            }
            return null;
        }

        /** Los temas que puede traer, por nombre. */
        public String themeList() {
            final StringBuilder sb = new StringBuilder();
            for (final Theme t : themes) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(t.label);
            }
            return sb.toString();
        }
    }

    /** Los Jumpstart que admite la Quest activa, con sus temas. */
    public static List<Product> products() {
        final java.util.LinkedHashMap<String, List<Theme>> by = new java.util.LinkedHashMap<>();
        for (final Theme t : available()) {
            List<Theme> l = by.get(t.product);
            if (l == null) {
                l = new ArrayList<>();
                by.put(t.product, l);
            }
            l.add(t);
        }
        final List<Product> out = new ArrayList<>();
        for (final java.util.Map.Entry<String, List<Theme>> e : by.entrySet()) {
            out.add(new Product(e.getKey(), e.getValue().get(0).setCode, e.getValue()));
        }
        return out;
    }

    private static List<Theme> all;

    /** Todos los temas de todos los Jumpstart que tiene Forge, en su orden. */
    public static synchronized List<Theme> all() {
        if (all == null) {
            all = Collections.unmodifiableList(read());
        }
        return all;
    }

    /** Los que admite la Quest activa (una Quest limitada, solo los de sus expansiones). */
    public static List<Theme> available() {
        final List<Theme> out = new ArrayList<>();
        for (final Theme t : all()) {
            if (NeoQuest.allowsSet(t.setCode)) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<Theme> read() {
        final List<Theme> out = new ArrayList<>();
        final java.lang.reflect.Field dataField;
        try {
            dataField = MetaSet.class.getDeclaredField("data");
            dataField.setAccessible(true);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            System.err.println("[jumpstart] no se pueden leer los temas: " + e);
            return out;
        }
        for (final CardBlock block : FModel.getBlocks()) {
            final String product = block.getName();
            if (product == null || !product.toLowerCase(java.util.Locale.ROOT).contains("jumpstart")) {
                continue;
            }
            final CardEdition land = block.getLandSet();
            final String setCode = land == null ? null : land.getCode();
            if (setCode == null) {
                continue;
            }
            for (final String metaName : block.getMetaSetNames()) {
                final MetaSet meta = block.getMetaSet(metaName);
                final String data;
                try {
                    data = (String) dataField.get(meta);
                } catch (final ReflectiveOperationException | RuntimeException e) {
                    continue;
                }
                if (data == null) {
                    continue;
                }
                for (final String entry : data.split(";")) {
                    final Theme t = theme(product, setCode, entry.trim());
                    if (t != null) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    /** "S(TLA Hei Bai 1)Hei Bai 1" -> el tema, o null si su sobre no existe. */
    private static Theme theme(final String product, final String setCode, final String entry) {
        if (!entry.startsWith("S(")) {
            return null;
        }
        final int close = entry.indexOf(')');
        if (close < 0) {
            return null;
        }
        final String key = entry.substring(2, close).trim();
        String label = entry.substring(close + 1).trim();
        if (label.isEmpty()) {
            label = key;
        }
        final SealedTemplate template;
        try {
            template = FModel.getMagicDb().getSpecialBoosters().get(key);
        } catch (final RuntimeException e) {
            return null;
        }
        return template == null ? null : new Theme(product, label, setCode, template);
    }

    private static int rank(final CardRarity r) {
        if (r == CardRarity.MythicRare) {
            return 4;
        }
        if (r == CardRarity.Rare) {
            return 3;
        }
        if (r == CardRarity.Uncommon) {
            return 2;
        }
        return 1;
    }
}
