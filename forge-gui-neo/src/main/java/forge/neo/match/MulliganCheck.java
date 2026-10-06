package forge.neo.match;

import java.util.List;
import java.util.Locale;

import forge.StaticData;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.model.FModel;

/**
 * El mulligan amistoso, sin ventana (ver {@link FriendlyMulligan}).
 *
 * <p>Dos partidas de Estandar en las que el humano pide tres mulligans seguidos
 * (sin preguntar: lo fuerza la prueba en el mismo sitio donde contestaria), y
 * se apunta cuantas cartas tiene cada vez que se le pregunta si se queda la
 * mano:
 *
 * <ul>
 *   <li><b>London</b>, de control: 7, 6, 5 y 4. Si no baja, la prueba no esta
 *       mirando lo que cree.</li>
 *   <li><b>Amistoso</b>: 7 las cuatro veces.</li>
 *   <li>La IA, en las dos, juega London normal: el amistoso es solo para el
 *       jugador.</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd mulligancheck}.
 */
public final class MulliganCheck {

    private MulliganCheck() {
    }

    private static int passed;
    private static int failed;

    private static final int SECONDS = 20;

    public static void run() {
        passed = 0;
        failed = 0;
        final Deck mine = deck("Prueba mulligan", 24);
        final Deck rival = deck("Prueba rival", 24);
        try {
            List<String> seen = play("London", mine, rival);
            check(human(seen).equals(List.of("H:7", "H:6", "H:5", "H:4")),
                    "London (control): la mano baja con cada mulligan " + human(seen));

            seen = play(FriendlyMulligan.RULE, mine, rival);
            check(human(seen).equals(List.of("H:7", "H:7", "H:7", "H:7")),
                    "amistoso: tres mulligans y siempre siete " + human(seen));
        } finally {
            FriendlyMulligan.resetForTest(-1);
            StaticData.instance().setMulliganRule(FriendlyMulligan.apply("London"));
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del mulligan han fallado");
        }
    }

    private static List<String> play(final String rule, final Deck mine, final Deck rival) {
        System.out.println("  Regla: " + rule);
        // Lo que hace applyEnginePrefs, que en AUTO_PLAY no se llama.
        StaticData.instance().setMulliganRule(FriendlyMulligan.apply(rule));
        FriendlyMulligan.resetForTest(3);
        NeoGame.play(mine, 1, NeoMatchUI.Mode.AUTO_PLAY, SECONDS, false, null, null, true,
                NeoFormat.STANDARD, List.of(rival), 1);
        return FriendlyMulligan.promptsForTest();
    }

    private static List<String> human(final List<String> seen) {
        return seen.stream().filter(s -> s.startsWith("H:")).toList();
    }

    /** 60 cartas: {@code lands} Islas y el resto Grizzly Bears. */
    private static Deck deck(final String name, final int lands) {
        final Deck d = new Deck(name);
        d.getOrCreate(DeckSection.Main).add(
                FModel.getMagicDb().getCommonCards().getCard("Island", "M21"), lands);
        d.getOrCreate(DeckSection.Main).add(
                FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears", "6ED"), 60 - lands);
        return d;
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
