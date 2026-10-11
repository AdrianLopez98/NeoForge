package forge.neo.card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.card.CardRules;
import forge.card.CardTypeView;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;

/**
 * Las tierras de la mesa, juntas por color y luego por nombre.
 *
 * <p>Pedido en Discord (11-10-2026), con capturas de un movil plegable: con
 * «Apilar las cartas iguales» apagado, las tierras salian en el orden en que se
 * jugaron — bosque, pantano, pantano, bosque, pantano, yermo — y para saber
 * cuanto mana de cada color tienes habia que contarlas una a una. Apiladas casi
 * no se notaba (las iguales se juntan solas), pero las pilas tambien iban en
 * orden de juego.
 *
 * <p>El color de una tierra no es el suyo (las tierras son incoloras) sino <b>el
 * que da</b>: sus tipos basicos (un Bosque nevado o un Watery Grave los
 * llevan) y, si no tiene ninguno, su identidad de color, que son los simbolos de
 * mana de su texto. El orden es el de {@link ColorSet#getOrderWeight()}, el
 * mismo que usa la mano ({@link HandOrder}): incoloras, blanco, azul, negro,
 * rojo, verde y luego las de varios colores. Es estable: dos iguales siguen en
 * el orden en que se jugaron.
 *
 * <p>Clase pura: la usan la mesa del escritorio ({@code PlayerField}) y la de
 * Android por el jar.
 */
public final class LandOrder {

    private static final String[] BASIC_TYPES = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
    private static final byte[] BASIC_COLORS = {
        MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK, MagicColor.RED, MagicColor.GREEN};

    private LandOrder() {
    }

    /** Los colores que da esa tierra, como mascara de {@link MagicColor}. 0 si ninguno o no se sabe. */
    public static int colorsOf(final CardView cv) {
        final CardStateView st = cv == null ? null : cv.getCurrentState();
        if (st == null) {
            return 0;
        }
        int mask = 0;
        final CardTypeView type = st.getType();
        if (type != null) {
            for (int i = 0; i < BASIC_TYPES.length; i++) {
                if (type.hasSubtype(BASIC_TYPES[i])) {
                    mask |= BASIC_COLORS[i];
                }
            }
        }
        if (mask == 0 && st.getName() != null) {
            try {
                final CardRules rules = forge.StaticData.instance().getCommonCards().getRules(st.getName(), false);
                if (rules != null && rules.getColorIdentity() != null) {
                    mask = rules.getColorIdentity().getColor();
                }
            } catch (final RuntimeException ignored) {
                // Una ficha o una carta que la base no conoce: incolora.
            }
        }
        return mask;
    }

    /** La clave de orden de una tierra: el peso de su color y su nombre. */
    public static String keyOf(final CardView cv) {
        final CardStateView st = cv == null ? null : cv.getCurrentState();
        final String name = st == null || st.getName() == null ? "" : st.getName().toLowerCase(Locale.ROOT);
        final float weight = ColorSet.fromMask(colorsOf(cv)).getOrderWeight();
        return String.format(Locale.ROOT, "%09.4f|%s", weight, name);
    }

    /** Una copia ordenada; la lista que llega no se toca. */
    public static List<CardView> sorted(final List<CardView> lands) {
        final List<CardView> out = new ArrayList<>(lands);
        // La clave se calcula UNA vez por carta: mirar la base de reglas en cada
        // comparacion seria hacerlo n·log n veces en cada repintado de la mesa.
        final Map<CardView, String> keys = new HashMap<>();
        for (final CardView cv : out) {
            keys.put(cv, keyOf(cv));
        }
        out.sort(Comparator.comparing(keys::get));
        return out;
    }
}
