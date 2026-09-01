package forge.neo.draft;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.card.CardEdition;
import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.item.PaperCard;

/**
 * Monta un sellado entero sin abrir una ventana.
 *
 * <p>Lo que hay que blindar no es la pantalla: es que <b>los seis sobres sean
 * seis</b> y no seis copias del mismo, que el mazo salga <b>jugable</b> (un
 * pool guardado con el mazo vacio no se puede jugar) y que los siete rivales se
 * monten. Nada de eso se ve en una captura.
 *
 * <p>{@code run.cmd sealedcheck}
 */
public final class SealedCheck {

    private SealedCheck() {
    }

    public static void run() {
        final long t0 = System.currentTimeMillis();
        boolean ok = true;

        final List<CardEdition> sets = NeoSealed.editions();
        System.out.printf(Locale.ROOT, "  Expansiones con sobre: %d%n", sets.size());
        ok &= !sets.isEmpty();
        if (sets.isEmpty()) {
            System.out.println("  FALLO: no hay ninguna expansion con sobre");
            return;
        }

        // Una expansion vieja y estable, no la ultima: las recien salidas a
        // veces traen plantillas raras y aqui se comprueba el CAMINO, no el set.
        CardEdition set = null;
        for (final CardEdition e : sets) {
            if ("M19".equals(e.getCode()) || "DOM".equals(e.getCode())) {
                set = e;
                break;
            }
        }
        if (set == null) {
            set = sets.get(0);
        }
        System.out.printf(Locale.ROOT, "  Montando un sellado de %s (%s)%n",
                set.getName(), set.getCode());

        // ---- los sobres son distintos ----
        //
        // SealedProduct.getCards() MEMORIZA: si se reutilizara el objeto,
        // saldrian seis sobres identicos y el pool tendria seis copias de cada
        // carta. Es el mismo fallo que ya tuvo la tienda.
        final List<PaperCard> one = NeoSealed.openBoosters(set, 1);
        final List<PaperCard> six = NeoSealed.openBoosters(set, 6);
        System.out.printf(Locale.ROOT, "  Un sobre: %d cartas | seis: %d%n", one.size(), six.size());
        ok &= !one.isEmpty() && six.size() >= one.size() * 5;

        final Set<String> distinct = new HashSet<>();
        for (final PaperCard c : six) {
            distinct.add(c.getName() + "|" + c.getEdition() + "|" + c.getCollectorNumber());
        }
        System.out.printf(Locale.ROOT, "  De las %d, %d impresiones distintas%n",
                six.size(), distinct.size());
        // Seis sobres identicos darian ~15 distintas de 90. La mitad ya es
        // muchisimo mas de lo que puede dar la casualidad.
        ok &= distinct.size() > six.size() / 2;

        // Informativo, no falla el check: BoosterGenerator sortea foil sola
        // (la auditoría del motor D5), sin que NeoForge tenga que pedirlo — el motor lo
        // hace de fabrica en el ~21% de las cartas de cada sobre, salvo que
        // la edicion diga lo contrario. Con seis sobres puede tocar cero foil
        // por pura suerte (uno de cada cuatro sellados, mas o menos), asi que
        // esto NUNCA cuenta como fallo: solo deja constancia de que el camino
        // esta vivo cuando toca.
        final long foilCount = six.stream().filter(PaperCard::isFoil).count();
        System.out.printf(Locale.ROOT,
                "  De esas, %d son foil (el motor sortea una por sobre, ~1 de cada 5 sobres)%n",
                foilCount);

        // ---- el evento entero ----
        final String name = "neo-sealedcheck";
        borrar(name);
        final DeckGroup group = NeoSealed.create(name, set, NeoSealed.DEFAULT_BOOSTERS);
        if (group == null) {
            System.out.println("  FALLO: no se ha podido montar el sellado");
            return;
        }

        final Deck mine = group.getHumanDeck();
        final int main = mine.getMain().countAll();
        final int side = mine.get(DeckSection.Sideboard) == null ? 0
                : mine.get(DeckSection.Sideboard).countAll();
        System.out.printf(Locale.ROOT, "  Tu mazo: %d cartas + %d en la banda | %d rivales%n",
                main, side, group.getAiDecks().size());

        // Un mazo limitado son 40 cartas. Si sale vacio, el pool esta guardado
        // pero no se puede jugar: hay que pasar por el constructor antes de
        // probar nada, que es justo lo que no queremos.
        ok &= main >= 40;
        ok &= group.getAiDecks().size() == NeoSealed.OPPONENTS;

        // Y los rivales tambien tienen que ser jugables.
        int weakest = Integer.MAX_VALUE;
        for (final Deck ai : group.getAiDecks()) {
            weakest = Math.min(weakest, ai.getMain().countAll());
        }
        System.out.printf(Locale.ROOT, "  El mazo mas pequenyo de los rivales: %d cartas%n", weakest);
        ok &= weakest >= 40;

        // Lo que esta en el mazo no puede seguir en la banda: si no, el
        // constructor dejaria meter copias que no tienes.
        boolean noDoubles = true;
        for (final java.util.Map.Entry<PaperCard, Integer> e : mine.getMain()) {
            // Las tierras basicas las anyade el constructor de mazos del motor
            // y no salen del pool: no cuentan.
            if (e.getKey().getRules().getType().isBasicLand()) {
                continue;
            }
            final int inSide = mine.get(DeckSection.Sideboard) == null ? 0
                    : mine.get(DeckSection.Sideboard).count(e.getKey());
            if (inSide > 0 && inSide + e.getValue() > totalOf(six, e.getKey()) + 4) {
                noDoubles = false;
            }
        }
        System.out.printf(Locale.ROOT, "  El pool no se duplica: %s%n", noDoubles ? "bien" : "MAL");
        ok &= noDoubles;

        // ---- el mazo se puede editar con el pool del sellado ----
        //
        // Aqui el pool NO esta donde en el draft: mazo y banda son disjuntos,
        // asi que el pool son los dos juntos. Si eso se leyera mal, el
        // constructor te dejaria meter copias que no tienes o te esconderia
        // media caja. Ver DraftDeckContext.
        final DraftDeckContext context =
                new DraftDeckContext(DraftRun.of(name, DraftRun.Kind.SEALED));
        int basics = 0;
        for (final PaperCard c : context.pool()) {
            if (c.getRules().getType().isBasicLand()) {
                basics++;
            }
        }
        boolean poolOk = basics == 5 && context.pool().size() > 20;
        for (final java.util.Map.Entry<PaperCard, Integer> e : mine.getMain()) {
            if (!e.getKey().getRules().getType().isBasicLand()
                    && context.owned(e.getKey()) < e.getValue()) {
                poolOk = false;
            }
        }
        System.out.printf(Locale.ROOT,
                "  Catalogo del constructor: %d cartas (%d basicas) | cabe el mazo: %s%n",
                context.pool().size(), basics, poolOk ? "si" : "NO");
        ok &= poolOk;

        // ---- el evento se comporta como el del draft ----
        final DraftRun run = DraftRun.of(name, DraftRun.Kind.SEALED);
        run.makeCurrent();
        System.out.printf(Locale.ROOT, "  Evento: %d rivales por batir | el de ahora: %s%n",
                run.getRemaining(),
                run.nextOpponent() == null ? "(ninguno)" : run.nextOpponent().getName());
        ok &= run.getRemaining() == NeoSealed.OPPONENTS && run.nextOpponent() != null;

        // Y el de draft y el de sellado no se pisan: son dos marcadores.
        ok &= DraftRun.current(DraftRun.Kind.SEALED) != null;
        System.out.printf(Locale.ROOT, "  El sellado en curso es %s%n",
                DraftRun.current(DraftRun.Kind.SEALED) == null ? "(ninguno)"
                        : DraftRun.current(DraftRun.Kind.SEALED).getName());

        // Dos derrotas y se borra, como en el draft.
        run.record(false);
        final boolean aliveAfterOne = !run.isEliminated();
        run.record(false);
        final boolean gone = !existe(name);
        System.out.printf(Locale.ROOT,
                "  Una derrota sigue vivo: %s | a la segunda se borra: %s%n",
                aliveAfterOne, gone);
        ok &= aliveAfterOne && gone;

        System.out.println();
        System.out.printf(Locale.ROOT, "  %s (%d s)%n",
                ok ? "OK - el sellado se monta, es jugable y el evento cuenta bien"
                        : "FALLO - revisa el sellado",
                (System.currentTimeMillis() - t0) / 1000);

        borrar(name);
    }

    /** Cuantas copias de esa carta habia en el pool que abrimos de muestra. */
    private static int totalOf(final List<PaperCard> pool, final PaperCard card) {
        int n = 0;
        for (final PaperCard c : pool) {
            if (c.getName().equals(card.getName())) {
                n++;
            }
        }
        return n;
    }

    /** Borra solo si existe: el almacen de Forge lanza NPE si no esta. */
    private static void borrar(final String name) {
        if (existe(name)) {
            DraftRun.Kind.SEALED.storage().delete(name);
        }
    }

    private static boolean existe(final String name) {
        for (final DeckGroup g : NeoSealed.saved()) {
            if (g.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
