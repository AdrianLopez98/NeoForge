package forge.neo.card;

import java.util.Locale;

import forge.deck.CardPool;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * <b>Con "Ocultar las cartas sin arte", no queda ninguna en el juego</b>
 * ({@code run.cmd artlesscheck}). Ver {@link ArtlessCards}.
 *
 * <p>Arranca el motor con el ajuste puesto ({@code NeoMain} pone
 * {@code -Dneo.cards.hideArtless} antes de cargar) y mira:
 * <ol>
 *   <li>que no quede ninguna carta en la edicion desconocida ("???") salida de
 *       un fichero de carta;</li>
 *   <li>que el motor SI conozca A-Faceless Haven (su fichero esta) y aun asi no
 *       la deje en el juego: ni en la base de cartas, ni en el catalogo, ni entre
 *       los comandantes (A-Baba Lysaga);</li>
 *   <li>que un mazo tuyo con A-Faceless Haven la conserve (el motor la carga a
 *       peticion: el ajuste quita lo que se ofrece, no toca tus mazos);</li>
 *   <li>que una A- CON expansion e imagen (A-Dawnbringer Cleric) siga: el ajuste
 *       quita las sin arte, no todas las Alchemy;</li>
 *   <li>que las reliquias de Ascenso, que tambien van a "???" pero son nuestras,
 *       sigan estando.</li>
 * </ol>
 */
public final class ArtlessCheck {

    private ArtlessCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final forge.card.CardDb db = FModel.getMagicDb().getCommonCards();

        check(ArtlessCards.hiddenThisSession(), "el motor ha arrancado con las cartas sin arte fuera",
                "el ajuste no ha llegado al motor: se arranco con ellas dentro");

        final int left = ArtlessCards.present().size();
        check(left == 0, "ninguna carta sin expansion en el juego",
                left + " cartas sin expansion siguen en el juego: " + ArtlessCards.present());

        final String faceless = "A-Faceless Haven";
        check(db.getRules(faceless, false) != null && db.getCard(faceless) == null,
                faceless + ": el motor la conoce (su fichero esta) y no la deja en el juego",
                faceless + ": reglas " + (db.getRules(faceless, false) != null) + ", carta " + db.getCard(faceless));

        boolean inCatalog = false;
        for (final PaperCard p : db.getUniqueCards()) {
            inCatalog |= faceless.equals(p.getName());
        }
        check(!inCatalog, "y no sale en el catalogo (constructor y Enciclopedia)",
                faceless + " sigue en el catalogo");

        boolean baba = false;
        for (final java.util.Map.Entry<PaperCard, Integer> e : FModel.getCommanderPool()) {
            baba |= "A-Baba Lysaga, Night Witch".equals(e.getKey().getName());
        }
        check(!baba, "A-Baba Lysaga, Night Witch no esta entre los comandantes",
                "A-Baba Lysaga, Night Witch sigue entre los comandantes");

        // Un mazo TUYO que la lleve la conserva: el motor la carga a peticion
        // al leerlo (StaticData.attemptToLoadCard, sin mirar el interruptor).
        // Es lo que se quiere: el ajuste quita lo que se OFRECE, no cambia tus
        // mazos sin preguntar. Va al final de lo que mira la base de cartas,
        // porque desde aqui esa carta vuelve a existir en esta sesion.
        final CardPool deck = new CardPool();
        deck.add(faceless, 1);
        String loaded = null;
        for (final java.util.Map.Entry<PaperCard, Integer> e : deck) {
            loaded = e.getKey().getName();
        }
        check(faceless.equals(loaded), "un mazo tuyo que la lleve la conserva (" + loaded + ")",
                "un mazo con " + faceless + " carga " + loaded);

        final PaperCard withArt = db.getCard("A-Dawnbringer Cleric");
        check(withArt != null, "A-Dawnbringer Cleric, Alchemy con expansion e imagen, sigue ("
                        + (withArt == null ? "-" : withArt.getEdition()) + ")",
                "A-Dawnbringer Cleric ha desaparecido: el ajuste quita mas de la cuenta");

        forge.neo.ascent.AscentRelics.install();
        final java.util.List<forge.neo.ascent.AscentRelic> relics = forge.neo.ascent.AscentRelics.all();
        final PaperCard relic = relics.isEmpty() ? null : forge.neo.ascent.AscentRelics.cardOf(relics.get(0));
        check(relic != null, "las reliquias de Ascenso siguen (" + (relic == null ? "-" : relic.getName()) + ")",
                "las reliquias de Ascenso han desaparecido");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las cartas sin arte han fallado");
        }
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [OK ] " + good);
        } else {
            failed++;
            System.out.println("  [MAL] " + bad);
        }
    }
}
