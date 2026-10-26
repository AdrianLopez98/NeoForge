package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.neo.NeoSettings;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * Tu mano en el registro cada turno ({@code run.cmd handlogcheck}, ver
 * {@link HandLog}): con el ajuste puesto, una partida de unos turnos deja una
 * linea de mano por turno, con lo que tenias; con el ajuste quitado, ninguna.
 * Deja el ajuste como estaba.
 */
public final class HandLogCheck {

    private static int passed;
    private static int failed;

    private HandLogCheck() {
    }

    public static void run() {
        final boolean before = HandLog.enabled();
        try {
            NeoSettings.setBool(HandLog.SETTING, true);
            final List<String> on = play();
            check(on.size() >= 3, "encendido: " + on.size() + " lineas de mano en " + "unos turnos",
                    "encendido: solo " + on.size() + " lineas de mano");
            check(!on.isEmpty() && on.get(0).contains("Island") && on.get(0).contains("Counterspell"),
                    "la primera dice lo que tenias: " + (on.isEmpty() ? "" : on.get(0)),
                    "la primera no tiene las cartas de la mano: " + on);
            NeoSettings.setBool(HandLog.SETTING, false);
            final List<String> off = play();
            check(off.isEmpty(), "apagado: ninguna", "apagado: " + off.size() + " lineas");
        } finally {
            NeoSettings.setBool(HandLog.SETTING, before);
        }
        System.out.println();
        System.out.println("  " + passed + " bien, " + failed + " mal");
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de la mano en el registro han fallado");
        }
    }

    /** Una partida corta; devuelve las lineas de mano del registro, de la primera a la ultima. */
    private static List<String> play() {
        final AtomicReference<Game> game = new AtomicReference<>();
        final TutorialLesson lesson = new TutorialLesson("handlog", Arrays.asList(
                "turn=1", "activeplayer=human", "activephase=UPKEEP",
                "humanlife=20", "ailife=20",
                "humanbattlefield=", "humanhand=Island|Set:M21;Counterspell|Set:7ED;Island|Set:M21",
                "humanlibrary=Island|Set:M21;Island|Set:M21;Island|Set:M21;Island|Set:M21;Island|Set:M21",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=Mountain|Set:M21;Mountain|Set:M21;Mountain|Set:M21;Mountain|Set:M21",
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 12, null,
                false, gui -> {
                    final Thread t = new Thread(() -> {
                        for (int i = 0; i < 200 && game.get() == null; i++) {
                            try {
                                Thread.sleep(50);
                            } catch (final InterruptedException e) {
                                return;
                            }
                            if (gui.getGameController() instanceof PlayerControllerHuman c) {
                                game.set(c.getPlayer().getGame());
                            }
                        }
                    }, "handlogcheck");
                    t.setDaemon(true);
                    t.start();
                });
        final List<String> out = new ArrayList<>();
        final Game g = game.get();
        if (g == null) {
            return out;
        }
        final String marker = forge.neo.NeoText.get("log.hand", "\u0000", "\u0001", "\u0002").split("\u0000")[0];
        final List<GameLogEntry> all = new ArrayList<>(g.getGameLog().getLogEntries(null));
        java.util.Collections.reverse(all);
        for (final GameLogEntry e : all) {
            if (e.type() == GameLogEntryType.INFORMATION && e.message().startsWith(marker)) {
                out.add(e.message());
            }
        }
        return out;
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
