package forge.neo.deck;

import forge.card.ColorSet;
import forge.item.PaperCard;

/**
 * <b>El filtro de color del constructor</b>, el mismo en el escritorio y en
 * Android.
 *
 * <p>Hasta el 17-10-2026 un color era "lo que tenga ese color en su
 * identidad": con solo el negro salian las doradas negro-rojas y hasta una
 * criatura verde con una habilidad de {B} (Discord: <i>"when filtering by
 * single colour, the multicolors also show up ... isn't that what the M is
 * for?"</i>). Ahora es como el filtro del Forge de siempre:
 *
 * <ul>
 *   <li><b>Un hechizo pasa si TODOS sus colores estan marcados.</b> Negro solo:
 *       las negras. Negro y rojo: las negras, las rojas y las negro-rojas.</li>
 *   <li><b>"M" anyade las multicolor</b> que tengan alguno de los colores
 *       marcados (o todas, si no hay ninguno marcado).</li>
 *   <li><b>Una tierra pasa si da alguno de los colores marcados</b>: con el
 *       negro sale la tierra doble negro-roja. Su identidad es justo el mana
 *       que da.</li>
 *   <li>Las incoloras, solo con "C".</li>
 * </ul>
 *
 * <p>Es solo lo que ensenya el catalogo: la legalidad del mazo y la identidad
 * del comandante ("Solo lo que cabe") no pasan por aqui.
 */
public final class ColourFilter {

    private ColourFilter() {
    }

    /**
     * @param colours     los colores marcados, en mascara de {@code MagicColor}
     * @param colourless  "C" marcado
     * @param multicolour "M" marcado
     */
    public static boolean matches(final PaperCard card, final byte colours,
                                  final boolean colourless, final boolean multicolour) {
        if (card == null || card.getRules() == null) {
            return false;
        }
        final ColorSet id = card.getRules().getColorIdentity();
        if (id.isColorless()) {
            return colourless;
        }
        final byte mine = id.getColor();
        final boolean land = card.getRules().getType() != null && card.getRules().getType().isLand();
        if (land && (mine & colours) != 0) {
            return true;
        }
        if (colours != 0 && (mine & ~colours) == 0) {
            return true;
        }
        return multicolour && id.countColors() > 1 && (colours == 0 || (mine & colours) != 0);
    }
}
