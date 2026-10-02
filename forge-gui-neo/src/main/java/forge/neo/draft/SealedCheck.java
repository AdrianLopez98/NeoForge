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
        // (la auditoría del motor, apartado D5), sin que NeoForge tenga que pedirlo — el motor lo
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

        // El mazo sale VACIO (23-09-2026) y el pool entero en la banda; luego
        // se pulsa "Montar solo", que es lo que hara quien no quiera montarlo.
        final int emptyAtCreate = group.getHumanDeck().getMain().countAll();
        final int poolAtCreate = DraftCheck.poolTotal(group.getHumanDeck());
        ok &= emptyAtCreate == 0;
        final Deck mine = DraftCheck.autoBuildSaved(DraftRun.of(name, DraftRun.Kind.SEALED));
        ok &= DraftCheck.poolTotal(mine) == poolAtCreate;
        System.out.printf(Locale.ROOT, "  Al crearlo: mazo de %d | pool de %d%n",
                emptyAtCreate, poolAtCreate);
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

        // En modo Arena, dos derrotas y se borra, como en el draft.
        run.setArena(true);
        run.record(false);
        final boolean aliveAfterOne = !run.isEliminated();
        run.record(false);
        final boolean gone = !existe(name);
        System.out.printf(Locale.ROOT,
                "  Una derrota sigue vivo: %s | a la segunda se borra: %s%n",
                aliveAfterOne, gone);
        ok &= aliveAfterOne && gone;

        ok &= mixChecks(sets);
        ok &= jumpstartChecks();

        System.out.println();
        System.out.printf(Locale.ROOT, "  %s (%d s)%n",
                ok ? "OK - el sellado se monta, es jugable y el evento cuenta bien"
                        : "FALLO - revisa el sellado",
                (System.currentTimeMillis() - t0) / 1000);

        borrar(name);
    }

    /**
     * MEZCLA DE EXPANSIONES Y BLOQUES (itch.io, 29-09-2026): el reparto de un
     * bloque, un sellado con sobres de dos expansiones, y un draft cuyas
     * rondas siguen el orden de la mezcla.
     */
    private static boolean mixChecks(final List<CardEdition> sets) {
        boolean ok = true;
        System.out.println();
        System.out.println("  --- Mezcla de expansiones y bloques ---");

        CardEdition dom = null;
        CardEdition m19 = null;
        for (final CardEdition e : sets) {
            if ("DOM".equals(e.getCode())) {
                dom = e;
            } else if ("M19".equals(e.getCode())) {
                m19 = e;
            }
        }
        if (dom == null || m19 == null) {
            System.out.println("  FALLO: no estan DOM y M19 para probar la mezcla");
            return false;
        }

        // ---- los bloques de Forge ----
        final List<forge.model.CardBlock> blocks = PackMix.blocks();
        forge.model.CardBlock khans = null;
        for (final forge.model.CardBlock b : blocks) {
            if ("Khans of Tarkir".equals(b.getName())) {
                khans = b;
            }
        }
        System.out.printf(Locale.ROOT, "  Bloques jugables: %d | Khans: %s%n",
                blocks.size(), khans == null ? "(no esta)" : PackMix.blockCodes(khans));
        ok &= blocks.size() > 50 && khans != null;
        if (khans != null) {
            final String three = String.join(",", PackMix.fromBlock(khans, 3).codes());
            final String six = PackMix.fromBlock(khans, 6).label();
            System.out.printf(Locale.ROOT, "  Khans en draft: %s | en sellado: %s%n", three, six);
            // Se abre primero la mas nueva, y lo que no sale a partes iguales
            // va a la mas vieja: FRF + 2xKTK, como los drafts de bloque.
            ok &= "FRF,KTK,KTK".equals(three) && six.equals("3×FRF + 3×KTK");
        }

        // ---- sellado con dos expansiones ----
        final PackMix mix = new PackMix();
        mix.set(dom, 3);
        mix.set(m19, 3);
        final String name = "NeoCheck mezcla";
        borrar(name);
        final DeckGroup group = NeoSealed.create(name, mix);
        int fromDom = 0;
        int fromM19 = 0;
        if (group != null) {
            for (final java.util.Map.Entry<PaperCard, Integer> e
                    : group.getHumanDeck().getOrCreate(forge.deck.DeckSection.Sideboard)) {
                if ("DOM".equals(e.getKey().getEdition())) {
                    fromDom += e.getValue();
                } else if ("M19".equals(e.getKey().getEdition())) {
                    fromM19 += e.getValue();
                }
            }
        }
        System.out.printf(Locale.ROOT, "  Sellado %s: %d de DOM, %d de M19, %d rivales%n",
                mix.label(), fromDom, fromM19, group == null ? 0 : group.getAiDecks().size());
        ok &= group != null && fromDom > 20 && fromM19 > 20 && group.getAiDecks().size() == NeoSealed.OPPONENTS;
        borrar(name);

        // ---- draft: las rondas siguen el orden de la mezcla ----
        final PackMix draftMix = new PackMix();
        draftMix.set(dom, 2);
        draftMix.set(m19, 1);
        final NeoDraft draft = NeoDraft.start(draftMix);
        String round1 = "?";
        String round3 = "?";
        if (draft != null) {
            round1 = mainEdition(draft.currentCards());
            int guard = 0;
            while (!draft.isDone() && draft.round() < 3 && guard++ < 100) {
                final List<PaperCard> cards = draft.currentCards();
                if (cards.isEmpty()) {
                    break;
                }
                draft.pick(cards.get(0));
            }
            round3 = mainEdition(draft.currentCards());
        }
        System.out.printf(Locale.ROOT, "  Draft %s (%s): ronda 1 de %s, ronda 3 de %s%n",
                draftMix.label(), draft == null ? "no arranca" : draft.productName(), round1, round3);
        ok &= draft != null && "DOM".equals(round1) && "M19".equals(round3);

        System.out.println(ok ? "  OK mezcla y bloques" : "  FALLO en la mezcla o los bloques");
        return ok;
    }

    /**
     * Jumpstart (Discord, 02-10-2026): que salgan los productos de Forge y solo
     * esos, que el evento se monte con TUS dos temas (uno elegido, otro al
     * azar) como mazo de 40 al principal, y que los siete rivales tengan el
     * suyo.
     */
    private static boolean jumpstartChecks() {
        boolean ok = true;
        final List<Jumpstart.Product> products = Jumpstart.products();
        boolean allJump = !products.isEmpty();
        for (final Jumpstart.Product p : products) {
            allJump &= p.name.toLowerCase(Locale.ROOT).contains("jumpstart") && p.themes.size() >= 2;
        }
        final Jumpstart.Product j22 = Jumpstart.byCode("J22");
        System.out.printf(Locale.ROOT, "  Jumpstart: %d productos (J22 con %d temas)%n",
                products.size(), j22 == null ? 0 : j22.themes.size());
        ok &= allJump && j22 != null;
        if (j22 == null) {
            return false;
        }
        final String name = "zz-prueba-jumpstart";
        borrar(name);
        final Jumpstart.Theme chosen = j22.themes.get(0);
        final DeckGroup group = NeoSealed.createJumpstart(name, j22, chosen, Jumpstart.RANDOM);
        int mine = 0;
        int fromTheme = 0;
        int rivalsOk = 0;
        if (group != null) {
            mine = group.getHumanDeck().getMain().countAll();
            // Las cartas del tema elegido: se abre otro sobre igual y se
            // cuenta cuantas de sus cartas estan en el mazo.
            final java.util.Set<String> theme = new HashSet<>();
            for (final PaperCard c : Jumpstart.open(chosen)) {
                theme.add(c.getName());
            }
            for (final java.util.Map.Entry<PaperCard, Integer> e : group.getHumanDeck().getMain()) {
                if (theme.contains(e.getKey().getName())) {
                    fromTheme += e.getValue();
                }
            }
            for (final forge.deck.Deck ai : group.getAiDecks()) {
                if (ai.getMain().countAll() >= 30) {
                    rivalsOk++;
                }
            }
        }
        System.out.printf(Locale.ROOT, "  Jumpstart J22: tu mazo %d cartas (%d de \"%s\"), %d rivales con mazo%n",
                mine, fromTheme, chosen.name, rivalsOk);
        ok &= group != null && mine >= 36 && mine <= 46 && fromTheme >= 10
                && rivalsOk == NeoSealed.OPPONENTS
                && group.getHumanDeck().getOrCreate(forge.deck.DeckSection.Sideboard).isEmpty();
        borrar(name);
        System.out.println(ok ? "  OK Jumpstart" : "  FALLO en Jumpstart");
        return ok;
    }

    /** La expansion de la mayoria de las cartas de un sobre. */
    private static String mainEdition(final List<PaperCard> cards) {
        final java.util.Map<String, Integer> n = new java.util.HashMap<>();
        for (final PaperCard c : cards) {
            n.merge(c.getEdition(), 1, Integer::sum);
        }
        String best = "?";
        int max = 0;
        for (final java.util.Map.Entry<String, Integer> e : n.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return best;
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
