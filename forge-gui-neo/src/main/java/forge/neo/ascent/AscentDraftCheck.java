package forge.neo.ascent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import forge.card.CardRarity;
import forge.deck.Deck;
import forge.item.PaperCard;

/**
 * <b>El mazo de salida drafteado de Ascenso</b> ({@link AscentDraft}).
 * {@code run.cmd ascentdraftcheck}. Sin ventana y sin tocar la run guardada:
 * se mira la receta y su codigo, los sobres, los picks y el mazo que sale.
 */
public final class AscentDraftCheck {

    private AscentDraftCheck() {
    }

    private static int good;
    private static int bad;

    public static void run() {
        good = 0;
        bad = 0;
        final AscentSeed.Recipe r = AscentSeed.fresh(AscentRun.Mode.STANDARD, 0, AscentSeedDeck.NO_COLOURS, null,
                AscentPool.ALL).withDraft(true);

        // 1. El codigo lleva el draft, y leerlo lo devuelve.
        final String code = r.code();
        final AscentSeed.Parsed back = AscentSeed.parse(code);
        check(code.contains("-DR-"), "el codigo lleva la marca DR: " + code);
        check(back.ok() && back.recipe.draft && back.recipe.seed == r.seed, "leer el codigo devuelve el draft");
        check(!AscentSeed.parse(r.withDraft(false).code()).recipe.draft, "sin draft, el codigo no lo lleva");
        final AscentSeed.Recipe cmd = AscentSeed.fresh(AscentRun.Mode.COMMANDER, 0, AscentSeedDeck.NO_COLOURS,
                null, AscentPool.ALL).withDraft(true);
        check(!cmd.draft, "en Commander no hay draft");

        // 2. Los sobres: 12 cartas, una infrecuente, el resto comunes, sin basicas.
        final AscentDraft a = new AscentDraft(r);
        final AscentDraft b = new AscentDraft(back.recipe);
        check(names(a.currentCards()).equals(names(b.currentCards())), "el mismo codigo abre el mismo sobre ("
                + a.productName() + ")");
        boolean shape = true;
        final StringBuilder why = new StringBuilder();
        // 3. Dieciocho picks, siempre el primero: en los dos, lo mismo.
        for (int i = 0; i < AscentDraft.PICKS && !a.isDone(); i++) {
            final List<PaperCard> pack = a.currentCards();
            if (a.pickNumber() == 1) {
                int unc = 0;
                int com = 0;
                for (final PaperCard c : pack) {
                    if (c.getRarity() == CardRarity.Uncommon) {
                        unc++;
                    } else if (c.getRarity() == CardRarity.Common) {
                        com++;
                    }
                    if (c.getRules().getType().isBasicLand()) {
                        shape = false;
                        why.append(" basica ").append(c.getName());
                    }
                }
                if (pack.size() != AscentDraft.PACK_SIZE || unc != AscentDraft.UNCOMMONS
                        || com != AscentDraft.PACK_SIZE - AscentDraft.UNCOMMONS) {
                    shape = false;
                    why.append(String.format(Locale.ROOT, " sobre %d de %s: %d cartas, %d infrecuentes, %d comunes;",
                            a.round(), a.productName(), pack.size(), unc, com));
                }
            }
            a.pick(pack.get(0));
            b.pick(b.currentCards().get(0));
        }
        check(shape, "cada sobre: " + AscentDraft.PACK_SIZE + " cartas, " + AscentDraft.UNCOMMONS
                + " infrecuente y el resto comunes, sin basicas" + why);
        check(a.isDone() && a.picked().size() == AscentDraft.PICKS, "se acaba con " + AscentDraft.PICKS
                + " picks (" + a.picked().size() + ")");
        check(names(a.picked()).equals(names(b.picked())), "con los mismos picks, lo mismo en los dos");

        // 4. "Montar solo" en el constructor: 30 cartas, 18 hechizos y 12
        //    basicas de dos tipos como mucho, todo del pool.
        final List<Deck> saved = new ArrayList<>();
        final forge.neo.draft.AscentPoolContext ctx = new forge.neo.draft.AscentPoolContext(a.picked(), "prueba",
                saved::add);
        check(ctx.startingDeck().getMain().countAll() == 0
                && ctx.startingDeck().get(forge.deck.DeckSection.Sideboard).countAll() == AscentDraft.PICKS,
                "el constructor empieza con el mazo vacio y los " + AscentDraft.PICKS + " picks en la banda");
        final forge.deck.CardPool built = ctx.autoBuild(a.picked());
        int basics = 0;
        int spells = 0;
        int cmc = 0;
        boolean fromPool = true;
        final Set<String> kinds = new HashSet<>();
        final List<PaperCard> left = new ArrayList<>(a.picked());
        for (final Map.Entry<PaperCard, Integer> e : built) {
            if (e.getKey().getRules().getType().isBasicLand()) {
                basics += e.getValue();
                kinds.add(e.getKey().getName());
            } else {
                spells += e.getValue();
                for (int i = 0; i < e.getValue(); i++) {
                    fromPool &= left.remove(e.getKey());
                    cmc += e.getKey().getRules().getManaCost().getCMC();
                }
            }
        }
        check(built.countAll() == AscentSeedDeck.STANDARD_SIZE && spells == AscentDraft.SPELLS
                && basics == AscentSeedDeck.STANDARD_SIZE - AscentDraft.SPELLS && kinds.size() <= 2,
                "Montar solo: " + spells + " hechizos y " + basics + " basicas " + kinds);
        check(fromPool, "Montar solo solo usa cartas del pool");
        System.out.printf(Locale.ROOT, "  [info] coste medio de los hechizos: %.2f%n", spells == 0 ? 0 : cmc / (double) spells);

        // 5. Solo 30 justas: con 29 no vale, y guardar solo empieza la run con 30.
        final Deck full = new Deck("prueba");
        full.getMain().addAll(built);
        check(ctx.conformanceProblem(full) == null, "un mazo de 30 vale");
        final Deck short29 = new Deck("prueba");
        short29.getMain().addAll(built);
        short29.getMain().remove(short29.getMain().toFlatList().get(0));
        check(ctx.conformanceProblem(short29) != null, "uno de 29 no: " + ctx.conformanceProblem(short29));
        ctx.storage().add(short29);
        check(saved.isEmpty(), "guardar uno de 29 no empieza la run");
        ctx.storage().add(full);
        check(saved.size() == 1, "guardar el de 30 si");

        // 6. Ascension 3: la maldita, sin cambiar el tamano.
        final Deck cursed = AscentDraft.finish(full, 3, AscentPool.ALL);
        boolean millstone = false;
        for (final Map.Entry<PaperCard, Integer> e : cursed.getMain()) {
            millstone |= "Millstone".equals(e.getKey().getName());
        }
        check(millstone && cursed.getMain().countAll() == AscentSeedDeck.STANDARD_SIZE,
                "Ascension 3: Millstone dentro y 30 cartas");

        // 7. Con expansiones elegidas, los sobres son de ellas.
        final AscentPool zen = AscentPool.set("ZEN");
        final AscentDraft z = new AscentDraft(AscentSeed.fresh(AscentRun.Mode.STANDARD, 0,
                AscentSeedDeck.NO_COLOURS, null, zen).withDraft(true));
        boolean allZen = true;
        for (int i = 0; i < AscentDraft.PICKS && !z.isDone(); i++) {
            for (final PaperCard c : z.currentCards()) {
                allZen &= "ZEN".equalsIgnoreCase(c.getEdition());
            }
            z.pick(z.currentCards().get(0));
        }
        check(allZen, "con Zendikar elegida, todos los sobres son de Zendikar");

        // 8. Una expansion sin comunes (Secret Lair): no revienta, saca sobres de algo.
        boolean sld;
        try {
            final AscentDraft s = new AscentDraft(AscentSeed.fresh(AscentRun.Mode.STANDARD, 0,
                    AscentSeedDeck.NO_COLOURS, null, AscentPool.set("SLD")).withDraft(true));
            sld = s.currentCards().size() == AscentDraft.PACK_SIZE;
            System.out.println("  [info] con Secret Lair elegida, el sobre es de: " + s.productName());
        } catch (final RuntimeException e) {
            sld = false;
            System.out.println("  " + e);
        }
        check(sld, "con una expansion sin comunes no revienta");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", good, bad);
        if (bad > 0) {
            throw new IllegalStateException("el draft de salida de Ascenso: no cuadra");
        }
    }

    private static List<String> names(final List<PaperCard> cards) {
        final List<String> out = new ArrayList<>();
        for (final PaperCard c : cards) {
            out.add(c.getName() + "|" + c.getEdition());
        }
        return out;
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            good++;
        } else {
            bad++;
        }
        System.out.println((ok ? "  [ok]   " : "  [MAL]  ") + what);
    }
}
