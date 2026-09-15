package forge.neo.match;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.interfaces.IMacroSystem;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;
import forge.player.RecordActionsMacroSystem;

/**
 * Las macros de Forge, jugadas de verdad (run.cmd macrocheck).
 *
 * <p>Pedidas en r/forgeMTG el 15-09-2026 ("REC Macro"). La grabacion y la
 * reproduccion son del motor ({@code RecordActionsMacroSystem}), pero se
 * enganchan en los metodos del controlador por los que pasan NUESTROS clics
 * ({@code PlayerControllerHuman.selectCard}) y la reproduccion corre en
 * nuestro hilo de interfaz ({@code FThreads.delayInEDT}). Eso es justo lo que
 * no se puede dar por hecho, y lo que se comprueba:
 *
 * <ol>
 *   <li>En tu primera fase principal se graba un click en un Bosque, por el
 *       mismo metodo que llama la mesa, y el Bosque se gira.</li>
 *   <li>Al parar, la macro tiene esa accion y el motor ya no graba.</li>
 *   <li>En tu siguiente turno, con el Bosque ya enderezado, se repite la
 *       macro — lo que hace el Mayus+3 de Forge — y el Bosque se gira SOLO.</li>
 *   <li>La reproduccion termina sola.</li>
 * </ol>
 *
 * <p>El piloto automatico se retiene en esas dos fases principales
 * ({@code NeoMatchUI.setAutoPlayHold}): si no, pasaria la prioridad antes de
 * que la prueba pudiera clicar nada.
 */
public final class MacroCheck {

    private MacroCheck() {
    }

    private static final String FOREST = "Forest|Set:M21";

    private static final int WAIT_RECORD = 0;
    private static final int RECORDING = 1;
    private static final int WAIT_PLAY = 2;
    private static final int PLAYING = 3;
    private static final int DONE = 4;
    /** Parando la grabacion: el piloto sigue retenido hasta que el hilo de interfaz acabe. */
    private static final int STOPPING = 5;

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());

        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicInteger stage = new AtomicInteger(WAIT_RECORD);
        final AtomicInteger recordTurn = new AtomicInteger(-1);
        final AtomicInteger recorded = new AtomicInteger(-1);
        final AtomicBoolean stoppedRecording = new AtomicBoolean(false);
        final AtomicBoolean untappedBeforePlay = new AtomicBoolean(false);
        final AtomicBoolean replayTapped = new AtomicBoolean(false);
        final AtomicBoolean replayFinished = new AtomicBoolean(false);
        final AtomicLong since = new AtomicLong();
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread poller = new Thread(() -> {
            while (alive.get() && stage.get() != DONE) {
                try {
                    step(gui[0], stage, recordTurn, recorded, stoppedRecording,
                            untappedBeforePlay, replayTapped, replayFinished, since);
                    Thread.sleep(50L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // La partida puede estar a medio montar: se vuelve a mirar.
                }
            }
        }, "macrocheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: un Bosque tuyo. Turno 3: se graba un click en el Bosque."
                + " Turno 5: se repite la macro.");
        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, 90, null, false, ui -> {
                    gui[0] = ui;
                    ui.setAutoPlayHold(() -> holding(ui, stage, recordTurn));
                    poller.start();
                });
        alive.set(false);

        System.out.printf(Locale.ROOT, "  Turnos jugados: %d | acciones grabadas: %d%n",
                result.turns, recorded.get());

        check(recorded.get() >= 1,
                "el click en el Bosque se grabo (" + recorded.get() + " accion/es)",
                "la macro no grabo nada: el click de la mesa no llega a la grabacion");
        check(stoppedRecording.get(),
                "al parar, el motor deja de grabar",
                "el motor seguia grabando despues de pararla");
        check(untappedBeforePlay.get(),
                "en el turno siguiente el Bosque estaba enderezado",
                "no se llego al turno siguiente con el Bosque enderezado: la prueba no prueba nada");
        check(replayTapped.get(),
                "la macro volvio a girar el Bosque ella sola",
                "la reproduccion no giro el Bosque");
        check(replayFinished.get(),
                "la reproduccion termino sola",
                "la reproduccion no termino (se corto por tiempo)");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las macros han fallado");
        }
    }

    /** Retener el piloto en tu fase principal mientras la prueba juega. */
    private static boolean holding(final NeoMatchUI ui, final AtomicInteger stage,
                                   final AtomicInteger recordTurn) {
        final int s = stage.get();
        if (s == DONE || ui.getGameView() == null) {
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
        if (s == WAIT_RECORD || s == RECORDING || s == STOPPING) {
            return true;
        }
        return ph.getTurn() > recordTurn.get();
    }

    private static void step(final NeoMatchUI ui, final AtomicInteger stage,
                             final AtomicInteger recordTurn, final AtomicInteger recorded,
                             final AtomicBoolean stoppedRecording,
                             final AtomicBoolean untappedBeforePlay,
                             final AtomicBoolean replayTapped,
                             final AtomicBoolean replayFinished, final AtomicLong since) {
        if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return;
        }
        if (!(ui.getGameController() instanceof PlayerControllerHuman gc)) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        final Card forest = me == null ? null : forest(me);
        if (forest == null) {
            return;
        }
        final PhaseHandler ph = game.getPhaseHandler();
        final boolean myMain = ph.isPlayerTurn(me) && ph.getPhase() == PhaseType.MAIN1;
        final boolean priority = gc.getInputQueue().getInput() instanceof InputPassPriority;
        final IMacroSystem macros = gc.macros();
        final long now = System.currentTimeMillis();

        switch (stage.get()) {
            case WAIT_RECORD:
                if (myMain && priority && !forest.isTapped()) {
                    recordTurn.set(ph.getTurn());
                    stage.set(RECORDING);
                    since.set(now);
                    GuiBase.getInterface().invokeInEdtLater(() -> {
                        ((RecordActionsMacroSystem) macros).startRecording();
                        // El MISMO metodo que llama la mesa al clicar una carta.
                        gc.selectCard(forest.getView(), null, null);
                    });
                }
                break;
            case RECORDING:
                if (forest.isTapped()) {
                    stage.set(STOPPING); // antes de encargarlo: el hilo de interfaz lo pisa al acabar
                    GuiBase.getInterface().invokeInEdtLater(() -> {
                        ((RecordActionsMacroSystem) macros).finishRecording();
                        stoppedRecording.set(!macros.isRecording());
                        recorded.set(actionsIn(macros.playbackText()));
                        stage.set(WAIT_PLAY);
                        nudgeIfStuck(ui, gc);
                    });
                } else if (now - since.get() > 8000) {
                    stage.set(DONE);
                    nudgeIfStuck(ui, gc);
                }
                break;
            case WAIT_PLAY:
                if (myMain && priority && ph.getTurn() > recordTurn.get()) {
                    untappedBeforePlay.set(!forest.isTapped());
                    stage.set(PLAYING);
                    since.set(now);
                    // Lo que hace el "repetir" de Forge (Mayus+3). Sin jugador,
                    // la pregunta de cuantas veces la contesta el valor de
                    // fabrica: 1.
                    GuiBase.getInterface().invokeInEdtLater(macros::repeatRememberedActions);
                }
                break;
            case PLAYING:
                if (forest.isTapped()) {
                    replayTapped.set(true);
                }
                if (now - since.get() > 600 && !macros.isReplaying()) {
                    replayFinished.set(true);
                    stage.set(DONE);
                    nudgeIfStuck(ui, gc);
                } else if (now - since.get() > 20000) {
                    stage.set(DONE);
                    nudgeIfStuck(ui, gc);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Soltar el piloto solo si hace falta. Al acabar un paso de la prueba el
     * motor puede estar ya esperando (y nadie le va a repintar los botones) o
     * puede que un repintado le llegue igualmente; pulsar OK a ciegas en el
     * segundo caso pasa la prioridad DOS veces sobre el mismo aviso
     * (NoSuchElementException en InputQueue.removeInput). Asi que se espera un
     * poco y se pulsa solo si sigue en el mismo aviso.
     */
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
        }, "macrocheck-nudge");
        t.setDaemon(true);
        t.start();
    }

    /** "0 / 1" → 1. */
    private static int actionsIn(final String playbackText) {
        if (playbackText == null) {
            return 0;
        }
        final int slash = playbackText.lastIndexOf('/');
        try {
            return Integer.parseInt(playbackText.substring(slash + 1).trim());
        } catch (final RuntimeException e) {
            return 0;
        }
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

    private static Card forest(final Player me) {
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if ("Forest".equals(c.getName())) {
                return c;
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
        return new TutorialLesson("macro", state, List.of());
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
