package forge.neo.card;

import java.util.HashSet;
import java.util.Set;

import forge.StaticData;
import forge.card.PrintSheet;
import forge.item.PaperCard;

/**
 * <b>Las ilustraciones alternativas que en la realidad salian en el sobre
 * normal</b> (Discord, 31-10-2026: <i>"ltr collector booster is missing some
 * cards"</i> — le faltaba la Nazgul #339).
 *
 * <p>LTR trae nueve Nazgul en su sobre normal, cada una con su ilustracion
 * (#100 y #332–#339), y M21 cuatro Teferi, Master of Time (#75 y #275–#277).
 * Forge pone las alternativas en la seccion {@code [alternate art]} del fichero
 * de la edicion, y el sobre de esas dos expansiones tira de su hoja
 * {@code "<CODIGO> cards"}, que solo tiene la seccion {@code [cards]}: esas
 * impresiones no podian salir en ningun sobre. Aqui se anyaden a esa hoja con
 * el mismo peso que la de siempre, que es como iban en el sobre de verdad
 * (cada ilustracion, una carta mas de su rareza: por eso la Nazgul salia
 * tanto).
 *
 * <p>Solo esas dos, comprobado en Scryfall carta a carta ({@code booster:
 * true}). Las que tienen hoja {@code [alternate art]} y no van aqui: KTK (sus
 * «y» son las versiones chinas con el arte retocado, solo de sobres chinos),
 * FIN, FDN, 30A y POR (ediciones especiales, no de sobre normal), y DSK, MID y
 * ECL, cuyo sobre ya se monta con todas las impresiones del set.
 *
 * <p>Toca la hoja en memoria y una sola vez, al arrancar y antes del primer
 * sobre: {@code BoosterGenerator} guarda en cache las hojas que saca de ella.
 * Sin JavaFX: lo llama tambien Android.
 */
public final class AltArtsInPacks {

    private AltArtsInPacks() {
    }

    /** Las expansiones cuyas ilustraciones alternativas iban en el sobre normal. */
    static final String[] EDITIONS = {"LTR", "M21"};

    private static boolean installed;

    /** Lo anyade, una vez. Nunca lanza: un sobre sin la alternativa es lo de antes. */
    public static synchronized int install() {
        if (installed) {
            return 0;
        }
        installed = true;
        int added = 0;
        try {
            final var sheets = StaticData.instance().getPrintSheets();
            for (final String code : EDITIONS) {
                final PrintSheet main = sheets.get(code + " cards");
                final PrintSheet alt = sheets.get(code + " alternate art");
                if (main == null || alt == null) {
                    continue;
                }
                final Set<PaperCard> already = new HashSet<>(main.toFlatList());
                final Set<String> names = new HashSet<>();
                for (final PaperCard c : already) {
                    names.add(c.getName());
                }
                for (final PaperCard c : new HashSet<>(alt.toFlatList())) {
                    // Solo otra ilustracion de una carta que ya esta en el sobre.
                    if (c != null && names.contains(c.getName()) && !already.contains(c)) {
                        main.add(c);
                        added++;
                    }
                }
            }
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se han podido anyadir las ilustraciones alternativas a los sobres: " + e);
        }
        System.out.println("[neo] ilustraciones alternativas en los sobres: " + added);
        return added;
    }
}
