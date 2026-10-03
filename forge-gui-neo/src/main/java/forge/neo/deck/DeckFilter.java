package forge.neo.deck;

import java.util.Collection;
import java.util.Locale;

import forge.card.CardRarity;
import forge.card.CardRules;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.item.PaperCard;
import forge.neo.card.CardText;

/**
 * <b>Que cartas del mazo se estan mirando</b>: el filtro de la columna del
 * mazo, el mismo en el escritorio y en Android (por el jar).
 *
 * <p>Empezo siendo color y texto (Discord, 03-10-2026). Se quedo corto
 * enseguida (itch.io, 03-10-2026): <i>"why made the filter in the deck soo
 * limited ? Imagine editing your 100 card 2 deck color the best a filter can
 * do with current deck filter only split the deck into 2"</i>. Ahora tiene lo
 * mismo que el catalogo: color, tipo, coste y rareza, y el texto.
 *
 * <p>Es solo de la pantalla: esconde filas, no toca el mazo. Sin JavaFX y sin
 * API de Java que Android no tenga en la 26.
 */
public final class DeckFilter {

    private DeckFilter() {
    }

    /**
     * @param colours    mascara de {@code MagicColor} (W U B R G); 0 = cualquiera.
     *                   Es la IDENTIDAD, como en el catalogo, y vale cualquiera de
     *                   los marcados
     * @param colourless las incoloras (artefactos y tierras, en un mazo)
     * @param text       en el nombre (traducido y en ingles) o en la linea de tipo
     * @param types      vacio = cualquier tipo; si no, alguno de ellos
     * @param costs      0..7, y 7 es "7 o mas"; vacio = cualquier coste
     * @param rarities   vacio = cualquier rareza
     */
    public static boolean accepts(final PaperCard card, final int colours, final boolean colourless,
                                  final String text, final Collection<CardType.CoreType> types,
                                  final Collection<Integer> costs, final Collection<CardRarity> rarities) {
        if (card == null) {
            return false;
        }
        final CardRules rules = card.getRules();
        if (colours != 0 || colourless) {
            final ColorSet id = rules.getColorIdentity();
            final boolean ok = (colourless && id.isColorless())
                    || (colours != 0 && id.hasAnyColor(colours));
            if (!ok) {
                return false;
            }
        }
        if (types != null && !types.isEmpty()) {
            boolean any = false;
            for (final CardType.CoreType t : types) {
                if (rules.getType().hasType(t)) {
                    any = true;
                    break;
                }
            }
            if (!any) {
                return false;
            }
        }
        if (costs != null && !costs.isEmpty()
                && !costs.contains(Math.min(rules.getManaCost().getCMC(), 7))) {
            return false;
        }
        if (rarities != null && !rarities.isEmpty() && !rarities.contains(card.getRarity())) {
            return false;
        }
        final String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        // El nombre en el idioma de la partida Y en ingles, y la linea de tipo:
        // se busca "elfo" igual que "Llanowar" o "artifact".
        return CardText.nameOf(card).toLowerCase(Locale.ROOT).contains(q)
                || card.getName().toLowerCase(Locale.ROOT).contains(q)
                || rules.getType().toString().toLowerCase(Locale.ROOT).contains(q);
    }
}
