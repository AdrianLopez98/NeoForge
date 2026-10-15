package forge.neo.deck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.deck.CardArchetypeLDAGenerator;
import forge.deck.CardRelationMatrixGenerator;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckgenUtil;
import forge.deck.io.CardThemedLDAIO;
import forge.deck.io.CardThemedMatrixIO;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * Que {@link FastDeckGen} deja las tablas del generador IGUAL que las dejaria
 * Forge: mismos formatos, mismas claves y mismos tamanyos que leyendolas con
 * {@code CardThemedMatrixIO}/{@code CardThemedLDAIO} a su manera; la
 * preferencia repuesta; y un mazo por sinergia de Commander y uno por
 * arquetipo generados de verdad. Sin red.
 */
public final class FastDeckGenCheck {

    private FastDeckGenCheck() {
    }

    private static int bad;

    public static void run() {
        bad = 0;
        check("DECKGEN_CARDBASED repuesta a true",
                FModel.getPreferences().getPrefBoolean(FPref.DECKGEN_CARDBASED));
        for (final DeckFormat f : new DeckFormat[] {DeckFormat.Commander, DeckFormat.Oathbreaker}) {
            final String name = f.toString();
            final Map<String, List<Map.Entry<PaperCard, Integer>>> ours = CardRelationMatrixGenerator.cardPools.get(name);
            final long t0 = System.currentTimeMillis();
            final HashMap<String, List<Map.Entry<PaperCard, Integer>>> theirs = CardThemedMatrixIO.loadMatrix(name);
            final long ms = System.currentTimeMillis() - t0;
            check("matriz " + name + ": " + size(ours) + " cartas (Forge: " + size(theirs) + ", leida a su manera en " + ms + " ms)",
                    ours != null && theirs != null && ours.keySet().equals(theirs.keySet()) && sameLists(ours, theirs));
        }
        for (final String f : CardArchetypeLDAGenerator.ldaPools.keySet()) {
            final Map<String, ?> ours = CardArchetypeLDAGenerator.ldaPools.get(f);
            final Map<String, ?> theirs = CardThemedLDAIO.loadLDA(f);
            final List<?> raw = CardThemedLDAIO.loadRawLDA(f);
            check("LDA " + f + ": " + size(ours) + " (Forge: " + size(theirs) + "), arquetipos " + size(CardArchetypeLDAGenerator.ldaArchetypes.get(f))
                            + " (Forge, podados: " + (raw == null ? -1 : CardArchetypeLDAGenerator.pruneArchetypes((List) raw).size()) + ")",
                    ours != null && theirs != null && ours.keySet().equals(theirs.keySet())
                            && raw != null && CardArchetypeLDAGenerator.ldaArchetypes.get(f).size()
                            == CardArchetypeLDAGenerator.pruneArchetypes((List) raw).size());
        }
        check("7 formatos LDA cargados", CardArchetypeLDAGenerator.ldaPools.size() == 7);
        final PaperCard cmd = FModel.getMagicDb().getCommonCards().getCard("Atraxa, Praetors' Voice");
        final Deck d = DeckgenUtil.generateRandomCommanderDeck(cmd, DeckFormat.Commander, true, true);
        check("mazo de Commander por sinergia (Atraxa): " + (d == null ? 0 : d.getMain().countAll()) + " cartas",
                d != null && d.getMain().countAll() >= 90);
        final Deck a = DeckgenUtil.buildLDACArchetypeDeck(FModel.getFormats().getModern(), true);
        check("mazo de Modern por arquetipo: " + (a == null ? 0 : a.getMain().countAll()) + " cartas",
                a != null && a.getMain().countAll() >= 60);
        System.out.println(bad == 0 ? "  TODO BIEN" : "  " + bad + " FALLO(S)");
        if (bad > 0) {
            throw new IllegalStateException(bad + " comprobaciones del generador de mazos han fallado");
        }
    }

    private static boolean sameLists(final Map<String, List<Map.Entry<PaperCard, Integer>>> a,
                                     final Map<String, List<Map.Entry<PaperCard, Integer>>> b) {
        for (final Map.Entry<String, List<Map.Entry<PaperCard, Integer>>> e : a.entrySet()) {
            final List<Map.Entry<PaperCard, Integer>> other = b.get(e.getKey());
            if (other == null || other.size() != e.getValue().size()) {
                return false;
            }
        }
        return true;
    }

    private static int size(final Object o) {
        return o instanceof Map ? ((Map<?, ?>) o).size() : o instanceof List ? ((List<?>) o).size() : -1;
    }

    private static void check(final String what, final boolean ok) {
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + what);
        if (!ok) {
            bad++;
        }
    }
}
