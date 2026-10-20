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
 *   <li>En ese turno del rival se vuelve a pulsar, y el rival lanza unos
 *       Llanowar Elves: el pase se tiene que cortar ahi, como en Forge
 *       ({@code YIELD_INTERRUPT_ON_OPPONENT_SPELL}). Discord, 15-10-2026: no se
 *       cortaba, porque el aviso del hechizo iba al controlador de la interfaz
 *       y el pase estaba en el sentado. Ahora los dos comparten el suyo.</li>
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
    private static final int OPP_PASSING = 4;
    private static final String ELVES = "Llanowar Elves";

    private static int passed;
    private static int failed;

    private static final class Seen {
        final AtomicInteger stage = new AtomicInteger(WAIT_PASS);
        final AtomicInteger passTurn = new AtomicInteger(-1);
        volatile String autoPassBefore;
        final AtomicBoolean relayed = new AtomicBoolean();
        final AtomicBoolean eotOnSeated = new AtomicBoolean();
        final AtomicBoolean seatedMayPass = new AtomicBoolean();
        final AtomicBoolean shared = new AtomicBoolean();
        final AtomicBoolean interruptReaches = new AtomicBoolean();
        final AtomicBoolean cancelReaches = new AtomicBoolean();
        final AtomicBoolean cancelledNextTurn = new AtomicBoolean();
        final AtomicBoolean oppArmed = new AtomicBoolean();
        final AtomicInteger elvesBefore = new AtomicInteger();
        final java.util.concurrent.atomic.AtomicReference<String> oppWhere =
                new java.util.concurrent.atomic.AtomicReference<>();
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
                    // Al cambiar de turno, mas a menudo: el del rival llega a su
                    // fase principal en nada, y hay que armar el pase antes.
                    Thread.sleep(seen.stage.get() == PASSING ? 1L : 50L);
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
        check(seen.shared.get(),
                "la interfaz y el sentado tienen el MISMO pase (el aviso, el Cancelar y los cortes de Forge lo ven)",
                "la interfaz y el sentado tienen pases distintos: Cancelar y los cortes no llegan");
        check(seen.interruptReaches.get(),
                "el corte de Forge por un hechizo del rival para el pase que lee el motor",
                "el corte de Forge no llega al pase que lee el motor");
        check(seen.cancelReaches.get(),
                "y su boton Cancelar tambien",
                "el Cancelar de Forge no llega al pase que lee el motor");
        check(seen.cancelledNextTurn.get(),
                "en el turno del rival el motor ya la ha cancelado",
                "la marca seguia puesta en el turno del rival");
        check(seen.oppArmed.get(),
                "en el turno del rival se vuelve a pulsar \"pasar turno\"",
                "no se pudo armar el pase en el turno del rival");
        check(seen.oppWhere.get() != null,
                "y en una partida de verdad lo corta lo que hace el rival, como en Forge (en " + seen.oppWhere.get() + ")",
                "el pase siguio hasta el final del turno del rival: ni su ataque ni sus hechizos lo cortaron");
        check(seen.fullOff.get(),
                "\"control total\" apaga el pase automatico en el sentado",
                "\"control total\" no apago el pase que lee el motor");
        check(seen.fullOffReported.get(),
                "y dice que ha quedado en control total",
                "la tecla no dijo que quedaba en control total");
        check(seen.fullBackOn.get(),
                "otra pulsacion lo vuelve a encender",
                "la segunda pulsacion no volvio a encender el pase");

        // Y el ajuste de que "Fin de turno" se corte (Discord, 20-10-2026):
        // apagado, las dos interrupciones de fabrica dejan de valer; encendido
        // vuelven. Se mira lo que lee el motor, no lo que dice la interfaz.
        final forge.localinstance.properties.ForgePreferences prefs = forge.model.FModel.getPreferences();
        forge.neo.NeoSettings.setBool(forge.neo.NeoSettings.END_TURN_INTERRUPTS, false);
        NeoGame.refreshSmartPass();
        final boolean offOk = !prefs.getPrefBoolean(FPref.YIELD_INTERRUPT_ON_ATTACKERS)
                && !prefs.getPrefBoolean(FPref.YIELD_INTERRUPT_ON_OPPONENT_SPELL);
        forge.neo.NeoSettings.setBool(forge.neo.NeoSettings.END_TURN_INTERRUPTS, true);
        NeoGame.refreshSmartPass();
        final boolean onOk = prefs.getPrefBoolean(FPref.YIELD_INTERRUPT_ON_ATTACKERS)
                && prefs.getPrefBoolean(FPref.YIELD_INTERRUPT_ON_OPPONENT_SPELL);
        check(offOk, "con el ajuste apagado, ni el ataque ni el hechizo del rival cortan \"Fin de turno\"",
                "el ajuste apagado no quita los cortes del motor");
        check(onOk, "y encendido (de fabrica) vuelven los dos cortes",
                "al encender el ajuste no vuelven los cortes");

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
                        seen.shared.set(gc.getYieldController() == seated.getYieldController()
                                && gc.getYieldController().autoPassUntilEndOfTurn());
                        // Lo que hace FControlGameEventHandler con un hechizo del
                        // rival: applyInterrupt sobre el controlador que le dio
                        // HostedMatch, que es el de la interfaz.
                        gc.getYieldController().applyInterrupt();
                        seen.interruptReaches.set(!yields.autoPassUntilEndOfTurn() && !seated.mayAutoPass());
                        // Y el Cancelar del aviso "Cediendo hasta el final del
                        // turno" (InputLockUI.selectButtonCancel), igual.
                        ui.armPassTurn(gc, me.getView(), false);
                        gc.getYieldController().clearActiveYieldAndDispatch();
                        seen.cancelReaches.set(!yields.autoPassUntilEndOfTurn());
                        ui.armPassTurn(gc, me.getView(), false);
                        // Y ahora el OK, como la tecla sobre el aviso rutinario.
                        gc.selectButtonOk();
                    });
                }
                break;
            case PASSING:
                if (!ph.isPlayerTurn(me)) {
                    seen.cancelledNextTurn.set(!yields.autoPassUntilEndOfTurn());
                    seen.stage.set(OPP_PASSING);
                    // "Pasar turno" en el turno del rival, antes de que lance
                    // nada: lo que tiene que cortar el pase es su hechizo. Desde
                    // aqui y no por el hilo de la interfaz, que llega tarde: su
                    // mantenimiento y su robo pasan en un suspiro.
                    // Los elfos que ya tenia: cuenta uno NUEVO, lanzado con el pase puesto.
                    seen.elvesBefore.set(opponentElves(game, me));
                    {
                        YieldController.endTurn(seated, me.getView());
                        seen.oppArmed.set(yields.autoPassUntilEndOfTurn());
                    }
                }
                break;
            case OPP_PASSING:
                if (ph.isPlayerTurn(me)) {
                    seen.stage.set(WAIT_FULL);
                } else if (seen.oppArmed.get() && seen.oppWhere.get() == null && !yields.autoPassUntilEndOfTurn()
                        && ph.getPhase() != null && ph.getPhase().isBefore(PhaseType.END_OF_TURN)) {
                    // Cortado antes de acabar su turno: sin el arreglo solo se
                    // quitaba en su limpieza. Lo corta su ataque (el elfo te
                    // ataca: YIELD_INTERRUPT_ON_ATTACKERS) o su hechizo.
                    seen.oppWhere.set(ph.getPhase() + (opponentElves(game, me) > seen.elvesBefore.get()
                            ? ", con un elfo nuevo lanzado" : ""));
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

    /** Cuantos elfos tiene el rival en la pila y en la mesa. */
    private static int opponentElves(final Game game, final Player me) {
        int n = 0;
        for (final forge.game.spellability.SpellAbilityStackInstance si : game.getStack()) {
            if (si.getActivatingPlayer() != me && si.getSourceCard() != null
                    && ELVES.equals(si.getSourceCard().getName())) {
                n++;
            }
        }
        for (final Player p : game.getRegisteredPlayers()) {
            if (p == me) {
                continue;
            }
            for (final forge.game.card.Card c : p.getCardsIn(forge.game.zone.ZoneType.Battlefield)) {
                if (ELVES.equals(c.getName())) {
                    n++;
                }
            }
        }
        return n;
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
                "aihand=" + ELVES + "|Set:M19",
                // Solo elfos: con su bosque, el rival lanza uno en cada turno.
                "ailibrary=" + String.join(";", Collections.nCopies(20, ELVES + "|Set:M19")),
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
