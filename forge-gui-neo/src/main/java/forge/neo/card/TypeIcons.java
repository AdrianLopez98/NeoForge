package forge.neo.card;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import forge.card.CardTypeView;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.item.PaperCard;
import forge.util.ImageUtil;

/**
 * <b>El icono de la carta SIN IMAGEN</b> (Discord, 07-11-2026: <i>"something
 * simple ... may be grayscale or card's color"</i>, con una lamina de iconos
 * por tipo de criatura, tierra y artefacto).
 *
 * <p>Los iconos son de <a href="https://game-icons.net">game-icons.net</a>
 * (CC BY 3.0, autores en AVISOS.txt), guardados como TRAZADO SVG sobre una
 * caja de 512x512 en {@code type-icons.txt}: sin imagenes, sin red, y los
 * pintan igual JavaFX ({@code SVGPath}) y Compose ({@code PathParser}).
 *
 * <p>Cual: el PRIMER subtipo de la carta que tenga icono, en el orden en que
 * viene impreso ("Human Cleric" es Human; "Artifact - Equipment", la
 * espada; "Plains", el sol). Si ninguno, el de su tipo principal, con la
 * prioridad de lo que mas se mira en la mesa: criatura, planeswalker,
 * batalla, tierra, instantaneo, conjuro, artefacto, encantamiento.
 *
 * <p>Java puro: lo usa Android igual, que conoce la carta por su
 * {@code imageKey} ({@link #pathForImageKey}).
 */
public final class TypeIcons {

    private TypeIcons() {
    }

    /** Lado de la caja de los trazados. */
    public static final int BOX = 512;

    /** Ajuste: el icono del color de la carta (true, de fabrica) o en gris. */
    public static final String COLOURED = "typeIconColoured";

    private static volatile Map<String, String> paths;

    private static Map<String, String> paths() {
        Map<String, String> p = paths;
        if (p != null) {
            return p;
        }
        synchronized (TypeIcons.class) {
            if (paths != null) {
                return paths;
            }
            final Map<String, String> read = new HashMap<>();
            try (InputStream in = TypeIcons.class.getResourceAsStream("type-icons.txt")) {
                if (in != null) {
                    final BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        final int eq = line.indexOf('=');
                        final int bar = line.indexOf('|', eq + 1);
                        if (eq > 0 && bar > eq) {
                            read.put(line.substring(0, eq), line.substring(bar + 1));
                        }
                    }
                }
            } catch (final Exception e) {
                System.err.println("[iconos] no se han podido leer los iconos de tipo: " + e);
            }
            paths = read;
            return read;
        }
    }

    /** El trazado (512x512) para esa carta, o null si no hay ninguno. */
    public static String pathFor(final CardTypeView type) {
        if (type == null) {
            return null;
        }
        final Map<String, String> p = paths();
        try {
            for (final String sub : type.getSubtypes()) {
                final String d = p.get(sub);
                if (d != null) {
                    return d;
                }
            }
        } catch (final RuntimeException e) {
            // sin subtipos legibles: el del tipo
        }
        final String core = coreKey(type);
        return core == null ? null : p.get(core);
    }

    /** El del tipo principal ("core.creature"...), o null. */
    static String coreKey(final CardTypeView type) {
        if (type.isCreature()) {
            return "core.creature";
        }
        if (type.isPlaneswalker()) {
            return "core.planeswalker";
        }
        if (type.isBattle()) {
            return "core.battle";
        }
        if (type.isLand()) {
            return "core.land";
        }
        if (type.isInstant()) {
            return "core.instant";
        }
        if (type.isSorcery()) {
            return "core.sorcery";
        }
        if (type.isArtifact()) {
            return "core.artifact";
        }
        if (type.isEnchantment()) {
            return "core.enchantment";
        }
        return null;
    }

    private static final Map<String, String> BY_KEY = new ConcurrentHashMap<>();
    private static final String NONE = "";

    /**
     * El trazado de la carta de esa {@code imageKey}, o null. Una ficha no
     * esta en la base de cartas: si {@code creature}, el de criatura.
     */
    public static String pathForImageKey(final String imageKey, final boolean creature) {
        String d = null;
        if (imageKey != null && !imageKey.isBlank()) {
            final String cached = BY_KEY.get(imageKey);
            if (cached != null) {
                d = cached.isEmpty() ? null : cached;
            } else {
                try {
                    final PaperCard pc = ImageUtil.getPaperCardFromImageKey(imageKey);
                    if (pc != null && pc.getRules() != null) {
                        d = pathFor(pc.getRules().getType());
                    }
                } catch (final RuntimeException e) {
                    // una clave rara: sin icono
                }
                BY_KEY.put(imageKey, d == null ? NONE : d);
            }
        }
        return d == null && creature ? paths().get("core.creature") : d;
    }

    /**
     * El color del icono, 0xAARRGGBB: el de la carta (un color, dorado si es
     * multicolor, gris si es incolora) o gris si {@code coloured} es false.
     * Claros: van sobre el fondo oscuro de la carta.
     */
    public static int colourOf(final int mask, final boolean coloured) {
        final int grey = 0xFFC8CDD4;
        if (!coloured) {
            return grey;
        }
        final ColorSet c = ColorSet.fromMask(mask);
        if (c.isColorless()) {
            return grey;
        }
        if (c.isMulticolor()) {
            return 0xFFE6C766;
        }
        if (c.hasWhite()) {
            return 0xFFF1E7C9;
        }
        if (c.hasBlue()) {
            return 0xFF7DB3E8;
        }
        if (c.hasBlack()) {
            return 0xFFB7A6C9;
        }
        if (c.hasRed()) {
            return 0xFFEF8466;
        }
        return c.hasGreen() ? 0xFF79CC93 : grey;
    }

    /** Lo mismo con un {@link ColorSet}. */
    public static int colourOf(final ColorSet colours, final boolean coloured) {
        return colourOf(colours == null ? MagicColor.COLORLESS : colours.getColor(), coloured);
    }
}
