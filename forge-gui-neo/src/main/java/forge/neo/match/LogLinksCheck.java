package forge.neo.match;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.game.card.CardView;

/**
 * <b>Las cartas del registro</b> ({@code run.cmd loglinkscheck}). Ver
 * {@link LogCardLinks}: que trozos de una linea son una carta, con las lineas
 * del informe de Discord (17-10-2026) y los casos raros — un numero que no es
 * de ninguna carta, un nombre que no casa, sin partida.
 */
public final class LogLinksCheck {

    private static int passed;
    private static int failed;

    private LogLinksCheck() {
    }

    public static void run() {
        final CardView oni = card("Oni of Wild Places");
        final CardView tiny = card("Tinybones, Pocket Nuisance");
        final Map<Integer, CardView> byId = new HashMap<>();
        byId.put(175, oni);
        byId.put(73, tiny);

        List<LogCardLinks.Piece> p = LogCardLinks.split("Herbert discards Oni of Wild Places (175).", null, byId::get);
        check("\"discards X (175).\": texto, carta, punto (" + show(p) + ")",
                p.size() == 3 && p.get(1).card() == oni && p.get(1).text().equals("Oni of Wild Places (175)")
                        && p.get(0).card() == null && p.get(2).text().equals("."));

        p = LogCardLinks.split("Remingten cast Tinybones, Pocket Nuisance", tiny, byId::get);
        check("la carta de la entrada, sin numero (" + show(p) + ")",
                p.size() == 2 && p.get(1).card() == tiny && p.get(1).text().equals("Tinybones, Pocket Nuisance"));

        p = LogCardLinks.split("When Tinybones enters, each opponent discards a card. "
                + "[Zone Changer: Tinybones, Pocket Nuisance (73)]", tiny, byId::get);
        check("el nombre largo con numero gana al corto sin numero (" + show(p) + ")",
                p.stream().filter(x -> x.card() != null).count() == 1
                        && p.stream().anyMatch(x -> x.card() == tiny
                        && x.text().equals("Tinybones, Pocket Nuisance (73)")));

        p = LogCardLinks.split("Something (999) happened", null, byId::get);
        check("un numero de ninguna carta se queda en texto", p.size() == 1 && p.get(0).card() == null);

        p = LogCardLinks.split("Herbert discards Something Else (175).", null, byId::get);
        check("si el nombre no casa, solo el numero (" + show(p) + ")",
                p.stream().anyMatch(x -> x.card() == oni && x.text().equals("(175)")));

        p = LogCardLinks.split("Life: Herbert 40 > 37", null, null);
        check("sin partida ni carta, la linea entera en texto", p.size() == 1 && p.get(0).card() == null);

        final StringBuilder joined = new StringBuilder();
        for (final LogCardLinks.Piece x : LogCardLinks.split(
                "Herbert discards Oni of Wild Places (175).", tiny, byId::get)) {
            joined.append(x.text());
        }
        check("los trozos juntos son la linea tal cual",
                joined.toString().equals("Herbert discards Oni of Wild Places (175)."));

        realGame();

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las cartas del registro han fallado");
        }
    }

    /**
     * En una partida de verdad: el numero de "played Plains (N)" lo resuelve el
     * Tracker de la partida ({@link LogCardLinks#lookupIn}) a esa misma carta.
     */
    private static void realGame() {
        final java.util.List<String> state = java.util.Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=", "humanhand=Plains", "humanlibrary=Plains;Plains;Plains",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=", "ailibrary=Plains;Plains;Plains",
                "aigraveyard=", "aiexile=", "aicommand=");
        final forge.neo.tutorial.TutorialLesson lesson =
                new forge.neo.tutorial.TutorialLesson("loglinks", state, java.util.List.of());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        NeoGame.playTutorial(lesson, new forge.neo.tutorial.TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 15, null, false, ui -> gui[0] = ui);
        final forge.game.GameView gv = gui[0] == null ? null : gui[0].getGameView();
        String resolved = null;
        if (gv != null && gv.getGameLog() != null) {
            final java.util.function.IntFunction<CardView> lookup = LogCardLinks.lookupIn(gv);
            for (final forge.game.GameLogEntry e : gv.getGameLog().getAllEntries()) {
                if (e.message() != null && e.message().contains("played Plains (")) {
                    for (final LogCardLinks.Piece x : LogCardLinks.split(e.message(), e.sourceCard(), lookup)) {
                        if (x.card() != null) {
                            resolved = x.text() + " -> " + x.card().getName();
                        }
                    }
                }
            }
        }
        check("en una partida, \"played Plains (N)\" lleva a esa carta (" + resolved + ")",
                resolved != null && resolved.endsWith("-> Plains") && resolved.startsWith("Plains ("));
    }

    private static CardView card(final String name) {
        return CardView.getCardForUi(forge.model.FModel.getMagicDb().getCommonCards().getCard(name));
    }

    private static String show(final List<LogCardLinks.Piece> pieces) {
        final StringBuilder sb = new StringBuilder();
        for (final LogCardLinks.Piece x : pieces) {
            sb.append(x.card() == null ? "" : "[").append(x.text()).append(x.card() == null ? "" : "]").append('|');
        }
        return sb.toString();
    }

    private static void check(final String what, final boolean ok) {
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
