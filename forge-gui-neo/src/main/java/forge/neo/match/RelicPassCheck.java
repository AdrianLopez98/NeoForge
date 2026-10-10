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
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbilityStackInstance;
import forge.gamemodes.match.input.InputPassPriority;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * Los disparos de TUS reliquias se dejan pasar solos (run.cmd relicpasscheck).
 *
 * <p>Discord (Tommy, 10-10-2026): con veinte reliquias, un OK por disparo y
 * turno sin nada que decidir. Lo hace {@code NeoMatchUI.passRelicTriggerSoon}.
 * Aqui se juega una partida con <b>Wellspring Stone</b> en las dos zonas de
 * mando ("al empezar tu fase principal precombate, anade {C}") y el piloto
 * automatico <b>retenido</b> ({@code setAutoPlayHold}): asi lo unico que puede
 * pasar la prioridad es lo que se prueba.
 *
 * <ol>
 *   <li>En tu turno, su disparo sube al stack con la prioridad en tu mano, y se
 *       resuelve SIN que nadie pulse nada: luego estas en tu fase principal con
 *       el stack vacio y un {C} en la reserva.</li>
 *   <li>En el del rival, el disparo de SU reliquia se queda esperando: ese no
 *       es tuyo y puedes querer responder.</li>
 * </ol>
 */
public final class RelicPassCheck {

    private RelicPassCheck() {
    }

    private static final String FOREST = "Forest|Set:M21";
    private static final String RELIC = "Wellspring Stone";

    private static final int WAIT_MINE = 0;
    private static final int RUN = 1;
    private static final int WAIT_THEIRS = 2;
    private static final int DONE = 3;

    private static int passed;
    private static int failed;

    private static final class Seen {
        final AtomicInteger stage = new AtomicInteger(WAIT_MINE);
        final AtomicBoolean sawMineOnTop = new AtomicBoolean();
        final AtomicBoolean minePassed = new AtomicBoolean();
        final AtomicInteger colorless = new AtomicInteger(-1);
        final AtomicBoolean sawTheirsOnTop = new AtomicBoolean();
        final AtomicLong theirsSince = new AtomicLong();
        final AtomicBoolean theirsWaited = new AtomicBoolean();
        final AtomicBoolean inCommand = new AtomicBoolean();
    }

    public static void run() {
        passed = 0;
        failed = 0;
        forge.neo.ascent.AscentRelics.install();
        final String before = forge.neo.NeoSettings.get(forge.neo.NeoSettings.RELIC_PASS, null);
        forge.neo.NeoSettings.setBool(forge.neo.NeoSettings.RELIC_PASS, true);

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final Seen seen = new Seen();
        final AtomicBoolean alive = new AtomicBoolean(true);

        final Thread poller = new Thread(() -> {
            while (alive.get() && seen.stage.get() != DONE) {
                try {
                    step(gui[0], seen);
                    Thread.sleep(30L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // La partida puede estar a medio montar: se vuelve a mirar.
                }
            }
        }, "relicpasscheck-poll");
        poller.setDaemon(true);

        System.out.println("  " + RELIC + " en las dos zonas de mando; el piloto, retenido.");
        try {
            final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                    NeoMatchUI.Mode.AUTO_PLAY, 45, null, false, ui -> {
                        gui[0] = ui;
                        ui.setAutoPlayHold(() -> holding(ui, seen));
                        poller.start();
                    });
            alive.set(false);
            System.out.printf(Locale.ROOT, "  Turnos jugados: %d%n", result.turns);
        } finally {
            // null lo quita: se queda como estaba.
            forge.neo.NeoSettings.set(forge.neo.NeoSettings.RELIC_PASS, before);
        }

        check(seen.inCommand.get(),
                RELIC + " esta en tu zona de mando",
                RELIC + " no ha llegado a tu zona de mando: la prueba no prueba nada");
        check(seen.sawMineOnTop.get(),
                "tu disparo sube al stack con la prioridad en tu mano: la prueba prueba algo",
                "no se vio nunca el disparo de tu reliquia arriba del stack");
        check(seen.minePassed.get(),
                "y se resuelve solo, con el piloto retenido: nadie ha pulsado OK",
                "tu disparo se quedo esperando un OK");
        check(seen.colorless.get() == 1,
                "despues tienes el {C} en la reserva (" + seen.colorless.get() + ")",
                "la reserva no tiene el {C} que anade (" + seen.colorless.get() + ")");
        check(seen.sawTheirsOnTop.get(),
                "el disparo de la reliquia del RIVAL sube con la prioridad en tu mano",
                "no se vio el disparo de la reliquia del rival");
        check(seen.theirsWaited.get(),
                "y ese NO se deja pasar: te espera, por si quieres responder",
                "el del rival tambien se dejo pasar solo");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las reliquias han fallado");
        }
    }

    /**
     * El piloto, retenido SOLO con el disparo de una Wellspring Stone encima:
     * ahi lo unico que puede pasar la prioridad es lo que se prueba. El resto
     * del tiempo juega solo (si no, la partida se queda en tu mantenimiento).
     */
    private static boolean holding(final NeoMatchUI ui, final Seen seen) {
        if (seen.stage.get() == DONE || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return false;
        }
        // Y desde que se vio el tuyo hasta apuntar como ha quedado: suelto, el
        // piloto pasaria la prioridad antes de que se pudiera mirar.
        if (seen.stage.get() == WAIT_MINE && seen.sawMineOnTop.get()) {
            return true;
        }
        final Game game = ui.getGameView().getGame();
        final SpellAbilityStackInstance top = game.getStack().isEmpty() ? null : game.getStack().peek();
        return top != null && top.isTrigger() && RELIC.equals(top.getSourceCard().getName());
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
        final boolean priority = gc.getInputQueue().getInput() instanceof InputPassPriority;
        final SpellAbilityStackInstance top = game.getStack().isEmpty() ? null : game.getStack().peek();
        final Card source = top == null ? null : top.getSourceCard();
        final boolean relicOnTop = source != null && RELIC.equals(source.getName()) && top.isTrigger();
        final boolean myTurn = game.getPhaseHandler().isPlayerTurn(me);
        if (!seen.inCommand.get()) {
            for (final Card c : me.getCardsIn(forge.game.zone.ZoneType.Command)) {
                if (RELIC.equals(c.getName())) {
                    seen.inCommand.set(true);
                }
            }
        }

        switch (seen.stage.get()) {
            case WAIT_MINE:
                if (priority && relicOnTop && myTurn) {
                    seen.sawMineOnTop.set(true);
                }
                if (seen.sawMineOnTop.get() && priority && top == null && myTurn
                        && game.getPhaseHandler().getPhase() == PhaseType.MAIN1) {
                    seen.minePassed.set(true);
                    seen.colorless.set(me.getManaPool().getAmountOfColor(
                            (byte) forge.card.mana.ManaAtom.COLORLESS));
                    seen.stage.set(RUN);
                    ui.nudgeAutoPlay();
                }
                break;
            case RUN:
                if (priority && relicOnTop && !myTurn) {
                    seen.sawTheirsOnTop.set(true);
                    seen.theirsSince.set(System.currentTimeMillis());
                    seen.stage.set(WAIT_THEIRS);
                }
                break;
            case WAIT_THEIRS:
                if (System.currentTimeMillis() - seen.theirsSince.get() > 1500L) {
                    seen.theirsWaited.set(priority && relicOnTop && !myTurn);
                    seen.stage.set(DONE);
                    ui.nudgeAutoPlay();
                }
                break;
            default:
                break;
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

    /** Turno 2 del rival, ya en su segunda principal: el tuyo empieza enseguida. */
    private static TutorialLesson position() {
        final String library = String.join(";", Collections.nCopies(8, FOREST));
        final List<String> state = Arrays.asList(
                "turn=2",
                "activeplayer=ai",
                "activephase=MAIN2",
                "humanlife=20",
                "ailife=20",
                // Un instantaneo que se puede lanzar: sin nada que hacer, el
                // pase automatico del motor ya pasaba solo y la prueba no
                // probaria nada. Con algo en la mano, como Tommy, se para.
                "humanbattlefield=Mountain",
                "humanhand=Shock",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=" + RELIC,
                "aibattlefield=" + FOREST,
                "aihand=",
                "ailibrary=" + library,
                "aigraveyard=",
                "aiexile=",
                "aicommand=" + RELIC);
        return new TutorialLesson("relicpass", state, List.of());
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
