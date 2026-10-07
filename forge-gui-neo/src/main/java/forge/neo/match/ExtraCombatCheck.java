package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputAttack;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Un combate extra</b> ({@code run.cmd extracombatcheck}).
 *
 * <p>Discord (06-10-2026): <i>"Relentless Assault ... didn't add additional
 * phases and ended the turn instead"</i>. Aqui se lanza en la segunda fase
 * principal, por el mismo camino que la mesa ({@code selectCard} desde el hilo
 * de interfaz), y luego se pasa con OK, que es lo que hace el boton "Pasar".
 * Se apunta por que fases pasa el turno y si se pregunta declarar atacantes.
 *
 * <p>Con {@code -Dneo.extra.endTurn=true} se pasa con "Fin de turno" en vez de
 * con OK, que se salta el combate extra igual que en el Forge original.
 */
public final class ExtraCombatCheck {

    private ExtraCombatCheck() {
    }

    public static void run() {
        final TutorialLesson lesson = new TutorialLesson("extracombat", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN2",
                "humanlife=20", "ailife=20",
                "humanbattlefield=Mountain;Mountain;Mountain;Mountain;Grizzly Bears|Set:6ED",
                "humanhand=Relentless Assault",
                "humanlibrary=Mountain;Mountain;Mountain;Mountain;Mountain",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=Island;Island;Island;Island;Island",
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean hold = new AtomicBoolean(true);
        final List<String> phases = new ArrayList<>();
        final AtomicBoolean askedAttack = new AtomicBoolean();
        // Mientras dura el turno del hechizo. El piloto contesta en
        // milisegundos: mirando la cola cada 5 ms, un InputAttack podia
        // empezar y acabar entre dos miradas (rojo suelto en la bateria del
        // 07-10-2026, con el combate extra jugado de verdad). Por eso se apunta
        // tambien desde el freno del piloto, que se consulta con la pregunta
        // viva, justo antes de contestarla.
        final AtomicBoolean watching = new AtomicBoolean();
        final AtomicBoolean cast = new AtomicBoolean();
        final List<String> notes = new ArrayList<>();

        final Thread driver = new Thread(() -> {
            try {
                Game game = null;
                PlayerControllerHuman gc = null;
                Player me = null;
                for (int i = 0; i < 600 && me == null; i++) {
                    Thread.sleep(50L);
                    final NeoMatchUI ui = gui[0];
                    if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null
                            || !(ui.getGameController() instanceof PlayerControllerHuman c)) {
                        continue;
                    }
                    final Game g = ui.getGameView().getGame();
                    for (final Player p : g.getPlayers()) {
                        if (!p.isAI() && g.getPhaseHandler().isPlayerTurn(p)
                                && g.getPhaseHandler().getPhase() == PhaseType.MAIN2
                                && c.getInputQueue().getInput() instanceof InputPassPriority) {
                            game = g;
                            gc = c;
                            me = p;
                        }
                    }
                }
                if (me == null) {
                    notes.add("no se llego a la segunda fase principal");
                    return;
                }
                Thread.sleep(500L);
                Card spell = null;
                for (final Card c : me.getCardsIn(ZoneType.Hand)) {
                    if ("Relentless Assault".equals(c.getName())) {
                        spell = c;
                    }
                }
                final PlayerControllerHuman ctrl = gc;
                final Card toCast = spell;
                GuiBase.getInterface().invokeInEdtLater(() -> ctrl.selectCard(toCast.getView(), null, null));
                // Que salga de la mano (al stack, o a pagar) antes de soltar al
                // piloto: soltarlo a la vez pasaba la prioridad antes del clic.
                for (int i = 0; i < 200 && me.getCardsIn(ZoneType.Hand).contains(toCast); i++) {
                    Thread.sleep(25L);
                }
                notes.add("tras el clic: " + (me.getCardsIn(ZoneType.Hand).contains(toCast)
                        ? "sigue en la mano" : "fuera de la mano") + ", entrada "
                        + ctrl.getInputQueue().getInput());
                final int turn = game.getPhaseHandler().getTurn();
                watching.set(true);
                hold.set(false);
                gui[0].nudgeAutoPlay();
                if (Boolean.getBoolean("neo.extra.endTurn")) {
                    // Esperar a que este en el cementerio y pulsar "Fin de turno".
                    for (int i = 0; i < 200 && !me.getCardsIn(ZoneType.Graveyard).contains(toCast); i++) {
                        Thread.sleep(25L);
                    }
                    gui[0].passTurn();
                }
                for (int i = 0; i < 2000 && game.getPhaseHandler().getTurn() == turn; i++) {
                    if (me.getCardsIn(ZoneType.Graveyard).contains(toCast)) {
                        cast.set(true);
                    }
                    note(phases, game);
                    if (ctrl.getInputQueue().getInput() instanceof InputAttack) {
                        askedAttack.set(true);
                    }
                    Thread.sleep(5L);
                }
                watching.set(false);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RuntimeException e) {
                notes.add("excepcion en la prueba: " + e);
                e.printStackTrace();
            } finally {
                hold.set(false);
            }
        }, "extracombatcheck");
        driver.setDaemon(true);

        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 40,
                null, false, ui -> {
                    gui[0] = ui;
                    ui.setAutoPlayHold(() -> {
                        if (watching.get() && ui.getGameController() instanceof PlayerControllerHuman c) {
                            final Game g = ui.getGameView() == null ? null : ui.getGameView().getGame();
                            if (g != null) {
                                note(phases, g);
                            }
                            if (c.getInputQueue().getInput() instanceof InputAttack) {
                                askedAttack.set(true);
                            }
                        }
                        return hold.get();
                    });
                    driver.start();
                });

        final List<String> seen;
        synchronized (phases) {
            seen = new ArrayList<>(phases);
        }
        System.out.printf(Locale.ROOT, "  lanzada: %s%n  fases tras lanzarla: %s%n  pregunto atacantes: %s%n",
                cast.get(), seen, askedAttack.get());
        notes.forEach(n -> System.out.println("  - " + n));
        // Despues de la MAIN2 en la que se lanza: combate (con declarar
        // atacantes) y otra MAIN2.
        final int main = seen.indexOf("MAIN2");
        final boolean extra = main >= 0 && seen.subList(main + 1, seen.size()).contains("COMBAT_DECLARE_ATTACKERS")
                && seen.lastIndexOf("MAIN2") > seen.indexOf("COMBAT_DECLARE_ATTACKERS");
        final boolean ok = cast.get() && extra && askedAttack.get();
        System.out.println(ok ? "%n  1 bien, 0 mal".formatted() : "%n  0 bien, 1 mal".formatted());
        if (!ok) {
            throw new IllegalStateException("el combate extra no ha salido como debe");
        }
    }

    /**
     * Apunta la fase si es distinta de la ultima. La llaman dos hilos (la
     * prueba y el piloto): leer y anyadir van juntos para que el orden sea el
     * de verdad.
     */
    private static void note(final List<String> phases, final Game game) {
        synchronized (phases) {
            final String s = String.valueOf(game.getPhaseHandler().getPhase());
            if (phases.isEmpty() || !phases.get(phases.size() - 1).equals(s)) {
                phases.add(s);
            }
        }
    }
}
