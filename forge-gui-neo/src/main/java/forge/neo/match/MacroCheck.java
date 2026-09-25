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
import forge.gamemodes.match.YieldController;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.interfaces.IMacroSystem;
import forge.localinstance.properties.ForgePreferences.FPref;
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
 *   <li>Girarlo dispara el Manabarbs: un disparo en el stack en el que no
 *       tienes nada que hacer. Con el pase automatico de NeoForge ese OK no
 *       se pedia y la macro no lo grababa (reportado el 25-09-2026: "no
 *       registra las confirmaciones del stack"). Se comprueba que mientras se
 *       graba el pase esta apagado, que el OK sale y que queda grabado.</li>
 *   <li>Al parar, la macro tiene las dos acciones, el motor ya no graba y el
 *       pase automatico vuelve a como estaba.</li>
 *   <li>En tu siguiente turno, con el Bosque ya enderezado, se repite la
 *       macro — lo que hace el Mayus+3 de Forge — y el Bosque se gira SOLO, y
 *       el disparo lo pasa la propia macro.</li>
 *   <li>La reproduccion termina sola y el pase automatico vuelve.</li>
 * </ol>
 *
 * <p>El piloto automatico se retiene en esas dos fases principales
 * ({@code NeoMatchUI.setAutoPlayHold}): si no, pasaria la prioridad antes de
 * que la prueba pudiera clicar nada. Y el pase automatico se enciende a mano
 * al empezar cada fase de la prueba: sin jugador ({@code Mode.AUTO_PLAY}) no
 * pasa por {@code NeoGame.applyEnginePrefs}, y sin el encendido la prueba no
 * probaria nada.
 */
public final class MacroCheck {

    private MacroCheck() {
    }

    private static final String FOREST = "Forest|Set:M21";
    private static final String MANABARBS = "Manabarbs|Set:M10";

    private static final int WAIT_RECORD = 0;
    private static final int RECORDING = 1;
    private static final int WAIT_PLAY = 2;
    private static final int PLAYING = 3;
    private static final int DONE = 4;
    /** Parando la grabacion: el piloto sigue retenido hasta que el hilo de interfaz acabe. */
    private static final int STOPPING = 5;

    private static final FPref AUTO_PASS = FPref.YIELD_AUTO_PASS_NO_ACTIONS;

    private static volatile String lastDebug;

    private static int passed;
    private static int failed;

    /** Lo que va viendo la prueba. Lo escriben el sondeo y el hilo de interfaz. */
    private static final class Seen {
        final AtomicInteger stage = new AtomicInteger(WAIT_RECORD);
        final AtomicInteger recordTurn = new AtomicInteger(-1);
        final AtomicInteger recorded = new AtomicInteger(-1);
        final AtomicLong since = new AtomicLong();
        /** El pase automatico que tenia la partida antes de la prueba. */
        volatile String autoPassBefore;
        final AtomicBoolean autoPassOffRecording = new AtomicBoolean();
        final AtomicBoolean okPressed = new AtomicBoolean();
        final AtomicBoolean stoppedRecording = new AtomicBoolean();
        final AtomicBoolean autoPassBackAfterRecording = new AtomicBoolean();
        final AtomicBoolean untappedBeforePlay = new AtomicBoolean();
        final AtomicBoolean autoPassOffReplaying = new AtomicBoolean();
        final AtomicBoolean replayTapped = new AtomicBoolean();
        final AtomicBoolean replayFinished = new AtomicBoolean();
        final AtomicBoolean stackEmptyAtReplayEnd = new AtomicBoolean();
        final AtomicBoolean autoPassBackAfterReplay = new AtomicBoolean();
        final AtomicLong replayEndedAt = new AtomicLong();
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
        }, "macrocheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: un Bosque y un Manabarbs tuyos. Turno 3: se graba un click en el"
                + " Bosque y el OK del disparo. Turno 5: se repite la macro.");
        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, 90, null, false, ui -> {
                    gui[0] = ui;
                    ui.setAutoPlayHold(() -> holding(ui, seen));
                    poller.start();
                });
        alive.set(false);

        System.out.printf(Locale.ROOT, "  Turnos jugados: %d | acciones grabadas: %d%n",
                result.turns, seen.recorded.get());

        check(seen.autoPassOffRecording.get(),
                "mientras se graba, el pase automatico esta apagado",
                "el pase automatico seguia encendido grabando: los OK del stack no se veran");
        check(seen.okPressed.get(),
                "el disparo del Manabarbs pidio su OK",
                "el disparo se resolvio sin pedir OK: el pase automatico se lo salto");
        check(seen.recorded.get() >= 2,
                "se grabaron el click en el Bosque y el OK del disparo (" + seen.recorded.get() + " acciones)",
                "la macro grabo " + seen.recorded.get() + " accion/es: falta el OK del stack"
                        + " (o el click de la mesa no llega a la grabacion)");
        check(seen.stoppedRecording.get(),
                "al parar, el motor deja de grabar",
                "el motor seguia grabando despues de pararla");
        check(seen.autoPassBackAfterRecording.get(),
                "al parar, el pase automatico vuelve a estar encendido",
                "al parar la grabacion el pase automatico se quedo apagado");
        check(seen.untappedBeforePlay.get(),
                "en el turno siguiente el Bosque estaba enderezado",
                "no se llego al turno siguiente con el Bosque enderezado: la prueba no prueba nada");
        check(seen.autoPassOffReplaying.get(),
                "mientras se reproduce, el pase automatico esta apagado",
                "el pase automatico seguia encendido reproduciendo");
        check(seen.replayTapped.get(),
                "la macro volvio a girar el Bosque ella sola",
                "la reproduccion no giro el Bosque");
        check(seen.replayFinished.get(),
                "la reproduccion termino sola",
                "la reproduccion no termino (se corto por tiempo)");
        check(seen.stackEmptyAtReplayEnd.get(),
                "el OK del disparo lo dio la propia macro",
                "la reproduccion acabo con el disparo aun en el stack: el OK grabado no se repitio");
        check(seen.autoPassBackAfterReplay.get(),
                "al acabar la reproduccion, el pase automatico vuelve",
                "al acabar la reproduccion el pase automatico se quedo apagado");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las macros han fallado");
        }
    }

    /** Retener el piloto en tu fase principal mientras la prueba juega. */
    private static boolean holding(final NeoMatchUI ui, final Seen seen) {
        final int s = seen.stage.get();
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
        return ph.getTurn() > seen.recordTurn.get();
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
        final Card forest = me == null ? null : forest(me);
        if (forest == null) {
            return;
        }
        final PhaseHandler ph = game.getPhaseHandler();
        final boolean myMain = ph.isPlayerTurn(me) && ph.getPhase() == PhaseType.MAIN1;
        final boolean priority = gc.getInputQueue().getInput() instanceof InputPassPriority;
        final boolean stackEmpty = game.getStack().isEmpty();
        final IMacroSystem macros = gc.macros();
        // El del asiento: es el que decide si la prioridad se pasa sola.
        final YieldController yields = NeoMatchUI.seated(gc).getYieldController();
        final long now = System.currentTimeMillis();
        if (Boolean.getBoolean("neo.macrocheck.debug")) {
            final String line = seen.stage.get() + " " + ph.getPhase() + " tapped=" + forest.isTapped()
                    + " stack=" + game.getStack().size() + " input="
                    + gc.getInputQueue().getInput() + " autopass=" + yields.getStringPref(AUTO_PASS)
                    + " life=" + me.getLife();
            if (!line.equals(lastDebug)) {
                lastDebug = line;
                System.out.println("[macrocheck] " + line);
            }
        }

        switch (seen.stage.get()) {
            case WAIT_RECORD:
                if (myMain && priority && !forest.isTapped()) {
                    seen.recordTurn.set(ph.getTurn());
                    seen.stage.set(RECORDING);
                    seen.since.set(now);
                    GuiBase.getInterface().invokeInEdtLater(() -> {
                        seen.autoPassBefore = yields.getStringPref(AUTO_PASS);
                        // Como lo trae NeoForge de fabrica.
                        yields.setPref(AUTO_PASS, "true");
                        // Lo que hace el boton de grabar de la mesa.
                        ui.holdAutoPassForMacro(gc);
                        ((RecordActionsMacroSystem) macros).startRecording();
                        seen.autoPassOffRecording.set(!yields.getBoolPref(AUTO_PASS));
                        // El MISMO metodo que llama la mesa al clicar una carta.
                        gc.selectCard(forest.getView(), null, null);
                    });
                }
                break;
            case RECORDING:
                if (forest.isTapped() && !stackEmpty && priority && !seen.okPressed.get()) {
                    // El OK del jugador sobre el disparo: el mismo que el boton.
                    seen.okPressed.set(true);
                    GuiBase.getInterface().invokeInEdtLater(gc::selectButtonOk);
                } else if ((forest.isTapped() && stackEmpty && priority && seen.okPressed.get())
                        || now - seen.since.get() > 8000) {
                    seen.stage.set(STOPPING); // antes de encargarlo: el hilo de interfaz lo pisa al acabar
                    GuiBase.getInterface().invokeInEdtLater(() -> {
                        ((RecordActionsMacroSystem) macros).finishRecording();
                        seen.stoppedRecording.set(!macros.isRecording());
                        seen.recorded.set(actionsIn(macros.playbackText()));
                        // Lo que hace el boton de la mesa al volver de pararla.
                        ui.syncMacroAutoPass(gc);
                        seen.autoPassBackAfterRecording.set(yields.getBoolPref(AUTO_PASS));
                        // Y para el resto de la prueba, como estaba: el piloto
                        // necesita ver tu fase principal del turno que viene.
                        yields.setPref(AUTO_PASS, seen.autoPassBefore);
                        seen.stage.set(WAIT_PLAY);
                        nudgeIfStuck(ui, gc);
                    });
                }
                break;
            case WAIT_PLAY:
                if (myMain && priority && ph.getTurn() > seen.recordTurn.get()) {
                    seen.untappedBeforePlay.set(!forest.isTapped());
                    seen.stage.set(PLAYING);
                    seen.since.set(now);
                    // Lo que hace el "repetir" de Forge (Mayus+3). Sin jugador,
                    // la pregunta de cuantas veces la contesta el valor de
                    // fabrica: 1.
                    GuiBase.getInterface().invokeInEdtLater(() -> {
                        yields.setPref(AUTO_PASS, "true");
                        ui.holdAutoPassForMacro(gc);
                        seen.autoPassOffReplaying.set(!yields.getBoolPref(AUTO_PASS));
                        macros.repeatRememberedActions();
                    });
                }
                break;
            case PLAYING:
                if (forest.isTapped()) {
                    seen.replayTapped.set(true);
                }
                if (!seen.replayFinished.get()) {
                    if (now - seen.since.get() > 600 && !macros.isReplaying()) {
                        seen.replayFinished.set(true);
                        seen.stackEmptyAtReplayEnd.set(stackEmpty);
                        seen.replayEndedAt.set(now);
                    } else if (now - seen.since.get() > 20000) {
                        finish(ui, gc, seen);
                    }
                } else if (yields.getBoolPref(AUTO_PASS)) {
                    // La vuelta del pase la hace updateButtons, por su cuenta.
                    seen.autoPassBackAfterReplay.set(true);
                    finish(ui, gc, seen);
                } else if (now - seen.replayEndedAt.get() > 3000) {
                    finish(ui, gc, seen);
                }
                break;
            default:
                break;
        }
    }

    private static void finish(final NeoMatchUI ui, final PlayerControllerHuman gc, final Seen seen) {
        gc.getYieldController().setPref(AUTO_PASS, seen.autoPassBefore);
        seen.stage.set(DONE);
        nudgeIfStuck(ui, gc);
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
                "humanbattlefield=" + FOREST + ";" + MANABARBS,
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
