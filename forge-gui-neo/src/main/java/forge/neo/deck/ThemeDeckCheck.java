package forge.neo.deck;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckgenUtil;
import forge.game.GameType;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.ascent.AscentSynergy;
import forge.neo.ascent.CommanderTheme;

/**
 * <b>El mazo de un comandante que Forge no conoce, con su tema</b>
 * ({@code run.cmd themedeckcheck}, 16-11-2026). Para unos cuantos que la
 * matriz no conoce y cuya carta da pistas (Paul Atreides, Ducal Heir: +1/+1;
 * Reverend Mother Jessica: prowess; Codie: preparar; Tam: planeswalkers),
 * monta el mazo de siempre de Forge (sus colores) y el nuevo
 * ({@link GeneratedDecks#forCommander}) y cuenta las cartas del tema en cada
 * uno: el nuevo tiene que ser legal, de su tamanyo y llevar bastantes mas. Y
 * que "Generame uno" ({@link GeneratedDecks#commanderDeck}) siga dando mazos
 * legales.
 */
public final class ThemeDeckCheck {

    private ThemeDeckCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final DeckFormat format = DeckFormat.Commander;
        int tried = 0;
        for (final String name : new String[] {"Paul Atreides, Ducal Heir", "Reverend Mother Jessica",
                "Codie, Ravenous Codex", "Tam, the Possibility", "Muad'Dib, Prophet Foretold"}) {
            final PaperCard cmd = FModel.getMagicDb().getCommonCards().getUniqueByName(name);
            if (cmd == null) {
                System.out.println("  (" + name + " no esta en este arbol)");
                continue;
            }
            if (AscentSynergy.knows(cmd)) {
                System.out.println("  (" + name + " ya lo conoce la matriz)");
                continue;
            }
            tried++;
            final List<String> clues = CommanderTheme.clues(cmd);
            check(!clues.isEmpty(), name + ": la carta da pistas " + clues);
            final Set<String> theme = new HashSet<>();
            for (final Map.Entry<PaperCard, Integer> e : CommanderTheme.poolOf(cmd)) {
                theme.add(e.getKey().getName());
            }
            int oldThemed = 0;
            int newThemed = 0;
            final int runs = 3;
            for (int i = 0; i < runs; i++) {
                final Deck before = DeckgenUtil.generateRandomCommanderDeck(cmd, format, false, false);
                oldThemed += count(before, theme);
                final long t0 = System.currentTimeMillis();
                final Deck now = GeneratedDecks.forCommander(cmd, format, false);
                final long ms = System.currentTimeMillis() - t0;
                newThemed += count(now, theme);
                final String problem = format.getDeckConformanceProblem(now);
                check(problem == null, name + ": legal (" + (problem == null ? "si" : problem) + ", " + ms + " ms)");
                check(now.getMain().countAll() + now.getCommanders().size() == 100,
                        name + ": 100 cartas (" + (now.getMain().countAll() + now.getCommanders().size()) + ")");
            }
            System.out.printf(Locale.ROOT, "  %-28s tema %s: antes %.1f cartas del tema, ahora %.1f%n",
                    name, clues, oldThemed / (double) runs, newThemed / (double) runs);
            check(newThemed >= oldThemed + 3 * runs && newThemed >= 12 * runs,
                    name + ": bastantes mas cartas del tema que el de colores");
        }
        check(tried >= 2, "al menos dos comandantes sin matriz con los que probar (" + tried + ")");

        // Y uno que la matriz SI conoce: sigue saliendo con la matriz, legal.
        final PaperCard krenko = FModel.getMagicDb().getCommonCards().getUniqueByName("Krenko, Mob Boss");
        if (krenko != null) {
            final Deck d = GeneratedDecks.forCommander(krenko, format, false);
            check(format.getDeckConformanceProblem(d) == null, "Krenko (con matriz): legal");
        }

        // "Generame uno": el comandante al azar y su mazo, para la IA.
        for (int i = 0; i < 6; i++) {
            final Deck d = GeneratedDecks.commanderDeck(true, GameType.Commander);
            final String problem = d == null ? "sin mazo" : format.getDeckConformanceProblem(d);
            check(problem == null, "Generame uno #" + (i + 1) + " "
                    + (d == null ? "" : d.getCommanders()) + ": " + (problem == null ? "legal" : problem));
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del mazo con tema han fallado");
        }
    }

    private static int count(final Deck d, final Set<String> theme) {
        int n = 0;
        for (final PaperCard c : d.getMain().toFlatList()) {
            if (theme.contains(c.getName())) {
                n++;
            }
        }
        return n;
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        System.out.println("  " + (ok ? "OK   " : "FALLO") + " " + what);
    }
}
