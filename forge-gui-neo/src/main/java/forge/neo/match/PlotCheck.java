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
import forge.game.card.CardView;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Lanzar una carta planeada</b> ({@code run.cmd plotcheck}).
 *
 * <p>Reportado en itch.io el 27-09-2026: <i>"I plotted Sunbird's Invocation
 * with Make Your Own Luck. There is no way to play the exiled card in a later
 * turn"</i>. El motor sabe lanzarla ({@code GameActionUtil}, rama "Plotted"),
 * pero no la pone en {@code PlayerView.getFlashback()} — el filtro de
 * {@code PlayerZone} conoce el presagio y la aventura y se olvida de lo
 * planeado —, y el visor del exilio solo deja clicar lo que esta en esa lista.
 * Lo completa {@code NeoMatchUI.plotted}; esto comprueba que funciona.
 *
 * <p>Se juega entero y por el camino de verdad:
 *
 * <ol>
 *   <li>Se <b>planea</b> una carta con su propia habilidad Plot (la del
 *       motor, {@code CardFactoryUtil}), no poniendole la marca a mano: asi
 *       llega el mismo {@code GameEventCardPlotted} que en una partida.</li>
 *   <li>Ese mismo turno <b>no</b> tiene que salir como lanzable.</li>
 *   <li>En tu turno siguiente, en la fase principal, <b>si</b>.</li>
 *   <li>Y clicarla — el mismo {@code selectCard} que manda el visor — la
 *       <b>saca del exilio</b>.</li>
 * </ol>
 *
 * <p>Ademas cuenta, sin fallar por ello, si el motor ya la trae el solo en
 * {@code getFlashback()}: el dia que lo arreglen rio arriba, lo dira aqui.
 */
public final class PlotCheck {

    private PlotCheck() {
    }

    /** Plot {1}{R}, y lanzada gratis se queda en la mesa: facil de ver. */
    private static final String CARD = "Slickshot Show-Off";

    private static final int WAIT_PLOT = 0;
    private static final int PLOTTED = 1;
    private static final int WAIT_CAST = 2;
    private static final int CASTING = 3;
    private static final int DONE = 4;
    private static final int REEXILE = 5;
    private static final int REEXILED = 6;

    private static int passed;
    private static int failed;

    private static final class Seen {
        private final AtomicInteger stage = new AtomicInteger(WAIT_PLOT);
        private final AtomicInteger plotTurn = new AtomicInteger(-1);
        private final AtomicLong since = new AtomicLong();
        private final AtomicBoolean plotted = new AtomicBoolean();
        private final AtomicBoolean markedSameTurn = new AtomicBoolean();
        private final AtomicBoolean markedLater = new AtomicBoolean();
        private final AtomicBoolean engineCanCast = new AtomicBoolean();
        private final AtomicBoolean leftExile = new AtomicBoolean();
        private final AtomicBoolean engineListsIt = new AtomicBoolean();
        private final AtomicBoolean reexiled = new AtomicBoolean();
        private final AtomicBoolean markedAfterReexile = new AtomicBoolean();
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
        }, "plotcheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: " + CARD + " en la mano. Turno 3: se planea."
                + " Turno 5: se lanza desde el exilio.");
        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, 90, null, false, ui -> {
                    gui[0] = ui;
                    ui.setAutoPlayHold(() -> holding(ui, seen));
                    poller.start();
                });
        alive.set(false);

        System.out.printf(Locale.ROOT, "  Turnos jugados: %d%n", result.turns);

        check(seen.plotted.get(),
                "la carta se planea con su propia habilidad y acaba en el exilio",
                "no se llego a planear: la posicion no se monto como se esperaba");
        check(!seen.markedSameTurn.get(),
                "el turno en que se planea NO sale como lanzable (el motor tampoco deja)",
                "sale como lanzable el mismo turno en que se planeo: el motor no deja");
        check(seen.engineCanCast.get(),
                "en tu turno siguiente el motor SI la deja lanzar (getAllPossibleAbilities)",
                "el motor no llego a ofrecerla en el turno siguiente: la prueba no prueba nada");
        check(seen.markedLater.get(),
                "y la interfaz la marca como lanzable, asi que el visor del exilio deja clicarla",
                "la interfaz NO la marca: se veria en el exilio y no habria forma de lanzarla");
        check(seen.leftExile.get(),
                "clicarla (el mismo selectCard que el visor) la saca del exilio",
                "el click no la saco del exilio");
        check(seen.reexiled.get() && !seen.markedAfterReexile.get(),
                "si despues vuelve a acabar exiliada sin planear, ya NO sale lanzable",
                seen.reexiled.get()
                        ? "vuelta a exiliar sin planear, sigue saliendo lanzable: marca fantasma"
                        : "no se llego a volver a exiliar: la prueba no prueba nada");
        System.out.println("  [info] el motor " + (seen.engineListsIt.get()
                ? "YA la trae en getFlashback(): arreglado rio arriba, NeoMatchUI.plotted sobra"
                : "sigue sin traerla en getFlashback(): NeoMatchUI.plotted hace falta"));

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de lo planeado han fallado");
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
        if (s == WAIT_PLOT || s == PLOTTED || s == REEXILED) {
            return true;
        }
        if (s == REEXILE) {
            // Soltar hasta que el hechizo se resuelva; luego parar para exiliarla.
            return game.getStack().isEmpty();
        }
        return ph.getTurn() > seen.plotTurn.get();
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
        final PhaseHandler ph = game.getPhaseHandler();
        final boolean myMain = ph.isPlayerTurn(me) && ph.getPhase() == PhaseType.MAIN1;
        final boolean priority = gc.getInputQueue().getInput() instanceof InputPassPriority;
        final long now = System.currentTimeMillis();
        final Card exiled = find(me, ZoneType.Exile);

        // Lo que la interfaz diria en cada momento, se mire cuando se mire.
        if (exiled != null && seen.plotTurn.get() >= 0) {
            final boolean marked = ui.isPlottedCastable(exiled.getView());
            if (ph.getTurn() == seen.plotTurn.get() && marked) {
                seen.markedSameTurn.set(true);
            }
            for (final CardView cv : me.getView().getFlashback()) {
                if (cv != null && cv.getId() == exiled.getId()) {
                    seen.engineListsIt.set(true);
                }
            }
        }

        switch (seen.stage.get()) {
            case WAIT_PLOT: {
                final Card inHand = find(me, ZoneType.Hand);
                if (myMain && priority && inHand != null) {
                    SpellAbility plot = null;
                    for (final SpellAbility sa : inHand.getSpellAbilities()) {
                        if (sa.isPlotting()) {
                            plot = sa;
                        }
                    }
                    if (plot == null) {
                        seen.stage.set(DONE);
                        return;
                    }
                    seen.plotTurn.set(ph.getTurn());
                    seen.stage.set(PLOTTED);
                    seen.since.set(now);
                    // La habilidad Plot del motor, resuelta tal cual: exilia,
                    // marca y dispara GameEventCardPlotted. El coste no se paga
                    // (no es lo que se prueba) y el motor esta parado esperando
                    // prioridad, asi que no hay nadie mas tocando la partida.
                    plot.setActivatingPlayer(me);
                    plot.resolve();
                }
                break;
            }
            case PLOTTED:
                if (exiled != null && exiled.isPlotted()) {
                    seen.plotted.set(true);
                }
                // Un rato para que el evento llegue y se mire en este turno.
                if (now - seen.since.get() > 1500) {
                    seen.stage.set(WAIT_CAST);
                    ui.nudgeAutoPlay();
                }
                break;
            case WAIT_CAST:
                if (myMain && priority && ph.getTurn() > seen.plotTurn.get()
                        && game.getStack().isEmpty() && exiled != null) {
                    seen.engineCanCast.set(!exiled.getAllPossibleAbilities(me, true).isEmpty());
                    seen.markedLater.set(ui.isPlottedCastable(exiled.getView()));
                    seen.stage.set(CASTING);
                    seen.since.set(now);
                    // El MISMO metodo que llama el visor al clicar la carta.
                    final CardView target = exiled.getView();
                    GuiBase.getInterface().invokeInEdtLater(
                            () -> gc.selectCard(target, null, null));
                }
                break;
            case CASTING:
                if (exiled == null) {
                    seen.leftExile.set(true);
                }
                if (exiled == null) {
                    seen.stage.set(REEXILE);
                    ui.nudgeAutoPlay();
                } else if (now - seen.since.get() > 8000) {
                    seen.stage.set(DONE);
                    ui.nudgeAutoPlay();
                }
                break;
            case REEXILE: {
                // Ya en la mesa: se exilia a secas, sin planear. Es el caso de
                // la carta lanzada que mas tarde acaba otra vez en el exilio.
                final Card onField = find(me, ZoneType.Battlefield);
                if (myMain && priority && game.getStack().isEmpty() && onField != null) {
                    seen.stage.set(REEXILED);
                    seen.since.set(now);
                    game.getAction().exile(onField, null, null);
                }
                break;
            }
            case REEXILED:
                if (exiled != null) {
                    seen.reexiled.set(true);
                    if (ui.isPlottedCastable(exiled.getView())) {
                        seen.markedAfterReexile.set(true);
                    }
                }
                if (now - seen.since.get() > 1500) {
                    seen.stage.set(DONE);
                    ui.nudgeAutoPlay();
                }
                break;
            default:
                break;
        }
    }

    private static Card find(final Player me, final ZoneType zone) {
        for (final Card c : me.getCardsIn(zone).threadSafeIterable()) {
            if (CARD.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static Player human(final Game game) {
        if (game == null) {
            return null;
        }
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static TutorialLesson position() {
        final String lands = String.join(";", Collections.nCopies(20, "Mountain"));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=",
                "humanhand=" + CARD,
                "humanlibrary=" + lands,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=",
                "aihand=",
                "ailibrary=" + String.join(";", Collections.nCopies(20, "Forest")),
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("plot", state, List.of());
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
