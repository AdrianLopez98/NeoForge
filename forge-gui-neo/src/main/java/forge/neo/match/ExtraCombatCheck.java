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
                hold.set(false);
                gui[0].nudgeAutoPlay();
                final int turn = game.getPhaseHandler().getTurn();
                if (Boolean.getBoolean("neo.extra.endTurn")) {
                    // Esperar a que este en el cementerio y pulsar "Fin de turno".
                    for (int i = 0; i < 200 && !me.getCardsIn(ZoneType.Graveyard).contains(toCast); i++) {
                        Thread.sleep(25L);
                    }
                    gui[0].passTurn();
                }
                PhaseType last = null;
                for (int i = 0; i < 2000 && game.getPhaseHandler().getTurn() == turn; i++) {
                    if (me.getCardsIn(ZoneType.Graveyard).contains(toCast)) {
                        cast.set(true);
                    }
                    final PhaseType ph = game.getPhaseHandler().getPhase();
                    if (ph != last) {
                        phases.add(String.valueOf(ph));
                        last = ph;
                    }
                    if (ctrl.getInputQueue().getInput() instanceof InputAttack) {
                        askedAttack.set(true);
                    }
                    Thread.sleep(5L);
                }
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
                    ui.setAutoPlayHold(hold::get);
                    driver.start();
                });

        System.out.printf(Locale.ROOT, "  lanzada: %s%n  fases tras lanzarla: %s%n  pregunto atacantes: %s%n",
                cast.get(), phases, askedAttack.get());
        notes.forEach(n -> System.out.println("  - " + n));
        // Despues de la MAIN2 en la que se lanza: combate (con declarar
        // atacantes) y otra MAIN2.
        final int main = phases.indexOf("MAIN2");
        final boolean extra = main >= 0 && phases.subList(main + 1, phases.size()).contains("COMBAT_DECLARE_ATTACKERS")
                && phases.lastIndexOf("MAIN2") > phases.indexOf("COMBAT_DECLARE_ATTACKERS");
        final boolean ok = cast.get() && extra && askedAttack.get();
        System.out.println(ok ? "%n  1 bien, 0 mal".formatted() : "%n  0 bien, 1 mal".formatted());
        if (!ok) {
            throw new IllegalStateException("el combate extra no ha salido como debe");
        }
    }
}
