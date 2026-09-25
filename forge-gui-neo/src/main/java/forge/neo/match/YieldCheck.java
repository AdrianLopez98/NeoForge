package forge.neo.match;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.game.Game;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.gamemodes.match.YieldController;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * "Pasar turno" y "control total", jugados (run.cmd yieldcheck).
 *
 * <p>Los dos cambian el pase automatico, y el 25-09-2026 se vio que lo
 * cambiaban en el controlador equivocado: en el de la interfaz, cuando el que
 * decide si la prioridad se pasa sola es el <b>relevo</b> que sienta
 * {@link ManaColor} (ver {@code NeoMatchUI.seated}). No habia error ni aviso:
 * la tecla decia que si y la partida seguia igual. Asi que aqui no se mira lo
 * que dice la interfaz sino lo que va a leer el motor — el
 * {@code YieldController} y el {@code mayAutoPass()} del controlador SENTADO:
 *
 * <ol>
 *   <li>Turno 3, tu fase principal, con el pase automatico apagado (asi solo
 *       puede pasar sola por "pasar turno"): se pulsa "pasar turno". El
 *       sentado queda pasando hasta final de turno y {@code mayAutoPass()}
 *       dice que si; el de la interfaz no se queda con la marca.</li>
 *   <li>Ya en el turno del rival, el motor la ha cancelado ({@code
 *       autoPassCancel} al final de cada turno).</li>
 *   <li>Turno 5, con el pase encendido como viene: "control total" lo apaga
 *       en el sentado, y otra pulsacion lo vuelve a encender.</li>
 * </ol>
 */
public final class YieldCheck {

    private YieldCheck() {
    }

    private static final String FOREST = "Forest|Set:M21";
    private static final FPref AUTO_PASS = FPref.YIELD_AUTO_PASS_NO_ACTIONS;

    private static final int WAIT_PASS = 0;
    private static final int PASSING = 1;
    private static final int WAIT_FULL = 2;
    private static final int DONE = 3;

    private static int passed;
    private static int failed;

    private static final class Seen {
        final AtomicInteger stage = new AtomicInteger(WAIT_PASS);
        final AtomicInteger passTurn = new AtomicInteger(-1);
        volatile String autoPassBefore;
        final AtomicBoolean relayed = new AtomicBoolean();
        final AtomicBoolean eotOnSeated = new AtomicBoolean();
        final AtomicBoolean seatedMayPass = new AtomicBoolean();
        final AtomicBoolean uiClean = new AtomicBoolean();
        final AtomicBoolean cancelledNextTurn = new AtomicBoolean();
        final AtomicBoolean fullOff = new AtomicBoolean();
        final AtomicBoolean fullOffReported = new AtomicBoolean();
        final AtomicBoolean fullBackOn = new AtomicBoolean();
    }

    public static void run() {
        passed = 0;
        failed = 0;

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final Seen seen = new Seen();
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread poller = new Thread(() -> {
            while (alive.get() && seen.stage.get() != DONE) {
                try {
                    step(gui[0], seen);
                    Thread.sleep(50L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // La partida puede estar a medio montar: se vuelve a mirar.
                }
            }
        }, "yieldcheck-poll");
        poller.setDaemon(true);

        System.out.println("  Turno 3: \"pasar turno\" en tu fase principal. Turno 5: \"control total\" dos veces.");
        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, 90, null, false, ui -> {
                    gui[0] = ui;
                    ui.setAutoPlayHold(() -> holding(ui, seen));
                    poller.start();
                });
        alive.set(false);
        System.out.printf(Locale.ROOT, "  Turnos jugados: %d%n", result.turns);

        check(seen.relayed.get(),
                "el asiento tiene su relevo (ManaColor): la prueba prueba algo",
                "no hay relevo en el asiento: la prueba no distingue nada");
        check(seen.eotOnSeated.get(),
                "\"pasar turno\" deja al controlador SENTADO pasando hasta final de turno",
                "\"pasar turno\" no llego al controlador sentado: el motor no lo ve");
        check(seen.seatedMayPass.get(),
                "y el motor pasaria la prioridad sola (mayAutoPass del sentado)",
                "el motor no pasaria la prioridad: te volveria a parar");
        check(seen.uiClean.get(),
                "el controlador de la interfaz no se queda con la marca",
                "la marca se quedo en el controlador de la interfaz, donde nadie la borra");
        check(seen.cancelledNextTurn.get(),
                "en el turno del rival el motor ya la ha cancelado",
                "la marca seguia puesta en el turno del rival");
        check(seen.fullOff.get(),
                "\"control total\" apaga el pase automatico en el sentado",
                "\"control total\" no apago el pase que lee el motor");
        check(seen.fullOffReported.get(),
                "y dice que ha quedado en control total",
                "la tecla no dijo que quedaba en control total");
        check(seen.fullBackOn.get(),
                "otra pulsacion lo vuelve a encender",
                "la segunda pulsacion no volvio a encender el pase");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del pase han fallado");
        }
    }

    /** Retener el piloto en tu fase principal mientras la prueba juega. */
    private static boolean holding(final NeoMatchUI ui, final Seen seen) {
        final int s = seen.stage.get();
        if (s == DONE || s == PASSING || ui.getGameView() == null) {
            return false;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        if (me == null) {
            return false;
        }
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(me) || ph.getPhase() != PhaseType.MAIN1) {
            return false;
        }
        return s == WAIT_PASS || ph.getTurn() > seen.passTurn.get();
    }

    private static void step(final NeoMatchUI ui, final Seen seen) {
        if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return;
        }
        if (!(ui.getGameController() instanceof PlayerControllerHuman gc)) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        if (me == null) {
            return;
        }
        final IGameController seatedGc = NeoMatchUI.seated(gc);
        if (!(seatedGc instanceof PlayerControllerHuman seated)) {
            return;
        }
        final YieldController yields = seated.getYieldController();
        final PhaseHandler ph = game.getPhaseHandler();
        final boolean myMain = ph.isPlayerTurn(me) && ph.getPhase() == PhaseType.MAIN1;
        final boolean priority = gc.getInputQueue().getInput() instanceof InputPassPriority;

        switch (seen.stage.get()) {
            case WAIT_PASS:
                if (myMain && priority) {
                    seen.passTurn.set(ph.getTurn());
                    seen.stage.set(PASSING);
                    GuiBase.getInterface().invokeInEdtLater(() -> {
                        seen.relayed.set(seated != gc);
                        seen.autoPassBefore = yields.getStringPref(AUTO_PASS);
                        // Apagado: asi, si la prioridad se pasa sola, es por
                        // "pasar turno" y por nada mas.
                        yields.setPref(AUTO_PASS, "false");
                        // Sin el OK: se mira el estado con el aviso aun delante.
                        ui.armPassTurn(gc, me.getView(), false);
                        seen.eotOnSeated.set(yields.autoPassUntilEndOfTurn());
                        seen.seatedMayPass.set(seated.mayAutoPass());
                        seen.uiClean.set(seated == gc || !gc.getYieldController().autoPassUntilEndOfTurn());
                        // Y ahora el OK, como la tecla sobre el aviso rutinario.
                        gc.selectButtonOk();
                    });
                }
                break;
            case PASSING:
                if (!ph.isPlayerTurn(me)) {
                    seen.cancelledNextTurn.set(!yields.autoPassUntilEndOfTurn());
                    seen.stage.set(WAIT_FULL);
                }
                break;
            case WAIT_FULL:
                if (myMain && priority && ph.getTurn() > seen.passTurn.get()) {
                    seen.stage.set(DONE);
                    GuiBase.getInterface().invokeInEdtLater(() -> {
                        // Como viene de fabrica en NeoForge.
                        yields.setPref(AUTO_PASS, "true");
                        final Boolean full = ui.flipFullControl(gc);
                        seen.fullOffReported.set(Boolean.TRUE.equals(full));
                        // flipFullControl aplica por respondLater: detras en la cola.
                        GuiBase.getInterface().invokeInEdtLater(() -> {
                            seen.fullOff.set(!yields.getBoolPref(AUTO_PASS));
                            ui.flipFullControl(gc);
                            GuiBase.getInterface().invokeInEdtLater(() -> {
                                seen.fullBackOn.set(yields.getBoolPref(AUTO_PASS));
                                yields.setPref(AUTO_PASS, seen.autoPassBefore);
                                nudgeIfStuck(ui, gc);
                            });
                        });
                    });
                }
                break;
            default:
                break;
        }
    }

    /** Ver MacroCheck.nudgeIfStuck: soltar el piloto solo si sigue en el mismo aviso. */
    private static void nudgeIfStuck(final NeoMatchUI ui, final PlayerControllerHuman gc) {
        final Object before = gc.getInputQueue().getInput();
        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(800L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            final Object now = gc.getInputQueue().getInput();
            if (now != null && now == before) {
                ui.nudgeAutoPlay();
            }
        }, "yieldcheck-nudge");
        t.setDaemon(true);
        t.start();
    }

    private static Player human(final Game game) {
        if (game == null) {
            return null;
        }
        for (final Player p : game.getRegisteredPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static TutorialLesson position() {
        final String library = String.join(";", Collections.nCopies(20, FOREST));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + FOREST,
                "humanhand=",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=" + FOREST,
                "aihand=",
                "ailibrary=" + library,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("yield", state, List.of());
    }

    private static void check(final boolean ok, final String good, final String bad) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK" : "MAL", ok ? good : bad);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
