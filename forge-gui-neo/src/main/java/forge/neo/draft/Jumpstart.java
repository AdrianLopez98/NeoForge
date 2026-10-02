package forge.neo.draft;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.item.PaperCard;
import forge.item.SealedTemplate;
import forge.item.generation.UnOpenedProduct;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.FileUtil;

/**
 * <b>Los sobres de Jumpstart, para el sellado.</b>
 *
 * <p>Pedido en Discord el 02-10-2026: <i>"Would it be possible to add the
 * Jumpstart sets to the Sealed game mode (as their packs) like the main Forge
 * does?"</i>. Un sobre de Jumpstart no es un sobre normal: son ~20 cartas de un
 * TEMA ("Angels 1", "Goblins 3"), tierras incluidas, y dos se barajan juntos y
 * ya es un mazo de 40.
 *
 * <p><b>Nada de esto es nuestro.</b> Forge los trae en
 * {@code res/blockdata/blocks.txt}: cada Jumpstart es una linea
 * {@code Nombre, -/2/CODIGO, Meta-Choose(S(JMP Angels 1)Angels 1;...)Themes}
 * — dos sobres, y cada uno se ELIGE de esa lista (en el Forge de escritorio,
 * con un dialogo por sobre; los rivales, al azar). Cada {@code S(...)} es un
 * "special booster" de {@code boosters-special.txt} que el motor ya sabe
 * abrir ({@code UnOpenedProduct}). Aqui solo se lee esa linea, porque
 * {@code MetaSet} no publica su lista.
 *
 * <p>Java de la 8: lo usa tambien Android, por el jar.
 */
public final class Jumpstart {

    private Jumpstart() {
    }

    /** Un tema: el sobre que abre ({@code template}) y como se llama. */
    public static final class Theme {
        public final String template;
        public final String name;

        Theme(final String template, final String name) {
            this.template = template;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Un producto de Jumpstart: su nombre, su codigo y sus temas. */
    public static final class Product {
        public final String name;
        public final String code;
        public final List<Theme> themes;

        Product(final String name, final String code, final List<Theme> themes) {
            this.name = name;
            this.code = code;
            this.themes = Collections.unmodifiableList(themes);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** "Al azar", para los selectores: {@link #pick} lo trata como null. */
    public static final Theme RANDOM = new Theme("", "");

    private static volatile List<Product> products;

    private static final Pattern THEME = Pattern.compile("S\\(([^)]+)\\)([^;)]*)");

    /**
     * Los Jumpstart que trae Forge, del mas nuevo al mas viejo (el orden del
     * fichero, al reves). Solo los temas que el motor sabe abrir: el fichero
     * tiene alguna errata (un "S(DMU Wolves 4)" dentro de Jumpstart 2022) y un
     * tema que no existe no puede llegar a un sobre.
     */
    public static List<Product> products() {
        List<Product> out = products;
        if (out != null) {
            return out;
        }
        out = new ArrayList<>();
        final File file = new File(ForgeConstants.BLOCK_DATA_DIR, "blocks.txt");
        for (final String line : FileUtil.readFile(file)) {
            final int meta = line.indexOf("Meta-Choose(");
            if (meta < 0 || !line.contains("S(")) {
                continue;
            }
            final String[] head = line.substring(0, meta).split(",");
            if (head.length < 2) {
                continue;
            }
            final String name = head[0].trim();
            // La misma formula la usan los prerelease de Magic Origins y los
            // "Guild Sealed" de Ravnica, que no son Jumpstart.
            if (!name.toLowerCase(java.util.Locale.ROOT).contains("jumpstart")) {
                continue;
            }
            final String[] packs = head[1].trim().split("/");
            final String code = packs.length >= 3 ? packs[2].trim() : "";
            final List<Theme> themes = new ArrayList<>();
            final Matcher m = THEME.matcher(line.substring(meta));
            while (m.find()) {
                final String template = m.group(1).trim();
                final String shown = m.group(2).trim().replaceAll("\\s+", " ");
                if (exists(template)) {
                    themes.add(new Theme(template, shown.isEmpty() ? template : shown));
                }
            }
            if (themes.size() >= 2) {
                out.add(new Product(name, code, themes));
            }
        }
        Collections.reverse(out);
        products = out;
        return out;
    }

    private static boolean exists(final String template) {
        try {
            return FModel.getMagicDb().getSpecialBoosters().get(template) != null;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** El producto con ese codigo, o null. */
    public static Product byCode(final String code) {
        for (final Product p : products()) {
            if (p.code.equalsIgnoreCase(code)) {
                return p;
            }
        }
        return null;
    }

    /** Abre un sobre de ese tema. Uno nuevo cada vez: el contenido se sortea al abrirlo. */
    public static List<PaperCard> open(final Theme theme) {
        final SealedTemplate t = FModel.getMagicDb().getSpecialBoosters().get(theme.template);
        if (t == null) {
            return new ArrayList<>();
        }
        return new UnOpenedProduct(t).get();
    }

    /**
     * Los dos temas de un jugador: los elegidos, y al azar los que no (null).
     * Nunca el mismo dos veces, salvo que el producto solo tenga uno.
     */
    public static List<Theme> pick(final Product product, final Theme first, final Theme second,
                                   final Random rnd) {
        final List<Theme> out = new ArrayList<>();
        final Theme a = first == RANDOM ? null : first;
        final Theme b = second == RANDOM ? null : second;
        out.add(a != null ? a : random(product, b, rnd));
        out.add(b != null ? b : random(product, out.get(0), rnd));
        return out;
    }

    private static Theme random(final Product product, final Theme not, final Random rnd) {
        final List<Theme> from = new ArrayList<>(product.themes);
        if (not != null && from.size() > 1) {
            from.remove(not);
        }
        return from.get(rnd.nextInt(from.size()));
    }
}
