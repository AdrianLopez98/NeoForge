package forge.neo.ascent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckgenUtil;
import forge.item.PaperCard;
import forge.util.MyRandom;

/**
 * <b>¿Da el generador de Forge el mismo mazo con la misma semilla?</b>
 *
 * <p>Ana, 28-10-2026: rivales de Ascenso con mazos generados (bracket 3 y 4)
 * en los actos altos — pero solo si el reto del dia sigue siendo el mismo
 * para todos. Esto genera unos mazos con {@link MyRandom} sembrado, dos veces
 * en el mismo proceso, e imprime su huella para compararla con la de otro
 * proceso (y con la de Android). No es un comprobador: es la medida.
 */
public final class AscentDeckgenProbe {

    private AscentDeckgenProbe() {
    }

    private static final String[] COMMANDERS = {
        "Atraxa, Praetors' Voice", "Krenko, Mob Boss", "Edgar Markov", "Muldrotha, the Gravetide",
    };

    /** @return true si dentro del proceso salen iguales las dos veces */
    public static boolean run(final long seed, final int bracket) {
        boolean same = true;
        for (final String name : COMMANDERS) {
            PaperCard cmd = null;
            for (final PaperCard c : AscentSeedDeck.commanderPool()) {
                if (c.getName().equals(name)) {
                    cmd = c;
                    break;
                }
            }
            if (cmd == null) {
                System.out.println("[deckgen] no esta " + name);
                continue;
            }
            final String a = print(generate(cmd, seed, bracket));
            final String b = print(generate(cmd, seed, bracket));
            same &= a.equals(b);
            System.out.println("[deckgen] " + name + " semilla " + seed + " bracket " + bracket
                    + " huella " + Integer.toHexString(a.hashCode()) + " / " + Integer.toHexString(b.hashCode())
                    + (a.equals(b) ? " IGUALES" : " DISTINTOS"));
        }
        return same;
    }

    static Deck generate(final PaperCard cmd, final long seed, final int bracket) {
        final Random before = MyRandom.getRandom();
        MyRandom.setRandom(new Random(seed));
        try {
            return DeckgenUtil.generateRandomCommanderDeck(cmd, DeckFormat.Commander, true, true, bracket);
        } finally {
            MyRandom.setRandom(before);
        }
    }

    static String print(final Deck d) {
        if (d == null) {
            return "null";
        }
        final List<String> lines = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : d.getMain()) {
            lines.add(e.getValue() + " " + e.getKey().getName() + "|" + e.getKey().getEdition());
        }
        if (d.has(forge.deck.DeckSection.Commander)) {
            for (final Map.Entry<PaperCard, Integer> e : d.get(forge.deck.DeckSection.Commander)) {
                lines.add("C " + e.getKey().getName());
            }
        }
        java.util.Collections.sort(lines);
        return String.join("\n", lines);
    }
}
