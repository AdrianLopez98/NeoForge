package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * <b>Ciclar sin querer</b> ({@code run.cmd handabilitycheck}). Ver
 * {@link ActivateGuard#handAbilityFor}.
 *
 * <p>Una partida de verdad, en el turno de la IA, con la prioridad en tu mano
 * (modo humano sin mesa: nadie contesta por ti, asi que el motor se queda
 * esperando con {@code InputPassPriority}). En la mano: Krosan Tusker (criatura
 * con ciclo: en turno ajeno no se puede lanzar, solo ciclar), Lightning Bolt
 * (se puede lanzar: lo que se quiere) y una Mountain (tierra: no se juega en
 * turno ajeno, y aunque se pudiera, no es una habilidad).
 */
public final class HandAbilityCheck {

    private static int passed;
    private static int failed;

    private HandAbilityCheck() {
    }

    public static void run() {
        final List<String> state = Arrays.asList(
                "turn=3", "activeplayer=ai", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=Mountain;Mountain;Forest;Forest",
                "humanhand=Krosan Tusker;Lightning Bolt;Mountain",
                "humanlibrary=Forest;Forest;Forest;Forest;Forest",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Plains", "aihand=", "ailibrary=Plains;Plains;Plains;Plains;Plains",
                "aigraveyard=", "aiexile=", "aicommand=");
        final TutorialLesson lesson = new TutorialLesson("handability", state, List.of());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final Thread game = new Thread(() -> NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.HUMAN, 40, null, false, ui -> gui[0] = ui), "handability-game");
        game.setDaemon(true);
        game.start();

        // Hasta que el motor espere tu prioridad con la Tusker en la mano.
        CardView tusker = null;
        CardView bolt = null;
        CardView mountain = null;
        String cycle = null;
        final long until = System.nanoTime() + 40_000_000_000L;
        while (System.nanoTime() < until) {
            final NeoMatchUI ui = gui[0];
            if (ui != null && ui.getGameView() != null) {
                for (final PlayerView p : ui.getGameView().getPlayers()) {
                    if (p.getHand() == null) {
                        continue;
                    }
                    for (final CardView c : p.getHand()) {
                        final String n = c.getName();
                        if ("Krosan Tusker".equals(n)) {
                            tusker = c;
                        } else if ("Lightning Bolt".equals(n)) {
                            bolt = c;
                        } else if ("Mountain".equals(n)) {
                            mountain = c;
                        }
                    }
                }
                if (tusker != null && ActivateGuard.handAbilityFor(ui.getGameController(), tusker) != null) {
                    cycle = ActivateGuard.describe(ui.getGameController(), tusker);
                    break;
                }
            }
            try {
                Thread.sleep(150);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        final NeoMatchUI ui = gui[0];
        System.out.println("  Krosan Tusker: " + cycle);
        check("en turno ajeno, la Tusker solo puede ciclarse: se pregunta (" + cycle + ")",
                cycle != null && cycle.toLowerCase(Locale.ROOT).contains("cycling"));
        check("y la pregunta es de la mano", ui != null && tusker != null
                && ActivateGuard.fromHand(ui.getGameController(), tusker));
        check("el Lightning Bolt se puede lanzar: no se pregunta", ui != null && bolt != null
                && ActivateGuard.handAbilityFor(ui.getGameController(), bolt) == null);
        check("una tierra no es una habilidad: no se pregunta", ui != null && mountain != null
                && ActivateGuard.handAbilityFor(ui.getGameController(), mountain) == null);
        check("lo de la mano no cuenta como habilidad de un permanente", ui != null && tusker != null
                && ActivateGuard.abilityFor(ui.getGameController(), tusker) == null);
        check("encendido de fabrica", forge.neo.NeoSettings.confirmHandAbility());

        if (ui != null) {
            ui.shutdown();
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de ciclar sin querer han fallado");
        }
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
