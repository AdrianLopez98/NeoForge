package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;
import forge.gamemodes.match.input.InputPassPriority;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.neo.ui.AbilityMenu;

/**
 * <b>El menu de habilidades junta las iguales</b> ({@code run.cmd abilitygroupcheck}).
 *
 * <p>Reportado en Discord el 01-10-2026 con <i>Marvin, Murderous Mimic</i>, que
 * tiene las habilidades activadas de todas tus criaturas: con muchas criaturas
 * con la misma habilidad, el menu salia con una docena de recuadros identicos
 * (y encima marcados "coste alternativo", que es de los hechizos) y lo demas
 * se quedaba fuera de la pantalla.
 *
 * <p>Marvin con tres <i>Prodigal Sorcerer</i> (la misma habilidad tres veces) y
 * un <i>Elvish Visionary</i> sin habilidad activada de control. Se piden al
 * motor las habilidades de Marvin y se comprueba lo que saldria en el menu:
 * la de Prodigal Sorcerer UNA vez con "×3", sin "coste alternativo", y que la
 * linea apunte a una habilidad de verdad del motor con ese texto.
 */
public final class AbilityGroupCheck {

    private AbilityGroupCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicReference<List<String>> raw = new AtomicReference<>();
        final AtomicReference<List<String>> grouped = new AtomicReference<>();
        final AtomicReference<String> pointsTo = new AtomicReference<>();
        final AtomicBoolean alive = new AtomicBoolean(true);
        // Se lee con la partida QUIETA: el piloto se retiene en tu prioridad
        // de la primera fase principal hasta tener la lectura (o 20 s). Antes
        // se leia al vuelo, y si caia mientras el motor montaba la posicion
        // Marvin salia con dos de las tres habilidades (rojo suelto el 04 y
        // el 07-10-2026, sin nada roto en el juego).
        final long holdUntil = System.currentTimeMillis() + 20_000L;

        final Thread poller = new Thread(() -> {
            while (alive.get() && grouped.get() == null && System.currentTimeMillis() < holdUntil) {
                try {
                    final NeoMatchUI ui = gui[0];
                    if (ui != null && ui.getGameView() != null && ui.getGameView().getGame() != null
                            && ui.getGameController() instanceof forge.player.PlayerControllerHuman ctrl
                            && ctrl.getInputQueue().getInput() instanceof InputPassPriority) {
                        final Game game = ui.getGameView().getGame();
                        final Player me = human(game);
                        if (me != null && game.getPhaseHandler().isPlayerTurn(me)
                                && game.getPhaseHandler().getPhase() == PhaseType.MAIN1) {
                            Card marvin = null;
                            for (final Card c : me.getCardsIn(forge.game.zone.ZoneType.Battlefield)) {
                                if (c.getName().startsWith("Marvin")) {
                                    marvin = c;
                                }
                            }
                            if (marvin != null) {
                                final List<SpellAbilityView> views = new ArrayList<>();
                                final List<String> texts = new ArrayList<>();
                                for (final SpellAbility sa : marvin.getAllPossibleAbilities(me, true)) {
                                    final SpellAbilityView v = sa.getView();
                                    views.add(v);
                                    texts.add(v.getDescription());
                                }
                                if (views.size() > 1) {
                                    raw.set(texts);
                                    final List<String> labels = AbilityMenu.groupedLabels(marvin.getView(), views);
                                    final int[] original = AbilityMenu.groupedOriginal(marvin.getView(), views);
                                    for (int k = 0; k < labels.size(); k++) {
                                        if (labels.get(k).contains("×3")) {
                                            pointsTo.set(views.get(original[k]).getDescription());
                                        }
                                    }
                                    grouped.set(labels);
                                }
                            }
                        }
                    }
                    Thread.sleep(50L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // La partida puede estar a medio montar: se vuelve a mirar.
                }
            }
            // Leida (o agotado el plazo): el piloto sigue.
            if (gui[0] != null) {
                gui[0].nudgeAutoPlay();
            }
        }, "abilitygroupcheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: Marvin, Murderous Mimic con tres Prodigal Sorcerer y un Elvish Visionary.");
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 30, null, false, ui -> {
            gui[0] = ui;
            ui.setAutoPlayHold(() -> grouped.get() == null && System.currentTimeMillis() < holdUntil
                    && humanMain1(ui));
            poller.start();
        });
        alive.set(false);

        final List<String> before = raw.get();
        final List<String> after = grouped.get();
        System.out.println("  El motor ofrece " + (before == null ? "?" : before.size()) + ":");
        if (before != null) {
            before.forEach(s -> System.out.println("    - " + s));
        }
        System.out.println("  El menu ensenya " + (after == null ? "?" : after.size()) + ":");
        if (after != null) {
            after.forEach(s -> System.out.println("    | " + s.replace("\n", " / ")));
        }
        check(before != null && before.size() >= 3,
                "el motor da a Marvin las habilidades prestadas (" + (before == null ? 0 : before.size()) + ")",
                "el motor no le da a Marvin las habilidades: la prueba no prueba nada");
        check(after != null && before != null && after.size() < before.size(),
                "el menu tiene menos lineas que habilidades: las iguales van juntas",
                "el menu repite las habilidades iguales");
        check(after != null && after.stream().filter(s -> s.contains("×3")).count() == 1,
                "la de Prodigal Sorcerer sale UNA vez, con ×3",
                "no hay una linea con ×3");
        check(after != null && after.stream().noneMatch(s -> s.contains("\n")),
                "ninguna dice \"coste alternativo\" (eso es de los hechizos)",
                "alguna linea lleva la nota de coste alternativo");
        check(pointsTo.get() != null && after != null && after.stream()
                        .anyMatch(s -> s.startsWith(pointsTo.get())),
                "la linea ×3 activa una habilidad del motor con ese mismo texto",
                "la linea ×3 apunta a otra habilidad");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del menu de habilidades han fallado");
        }
    }

    /** Tu turno, primera fase principal: donde se retiene al piloto para leer. */
    private static boolean humanMain1(final NeoMatchUI ui) {
        try {
            final Game game = ui.getGameView() == null ? null : ui.getGameView().getGame();
            final Player me = game == null ? null : human(game);
            return me != null && game.getPhaseHandler().isPlayerTurn(me)
                    && game.getPhaseHandler().getPhase() == PhaseType.MAIN1;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static Player human(final Game game) {
        Player me = null;
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                me = p;
            }
        }
        return me;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=Marvin, Murderous Mimic;Prodigal Sorcerer;Prodigal Sorcerer;"
                        + "Prodigal Sorcerer;Elvish Visionary;Island;Island",
                "humanhand=",
                "humanlibrary=Island;Island;Island",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=Island;Island;Island",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("abilitygroup", state, List.of());
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
