package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Grenzo, Crooked Jailer no deja jugar las tierras del cementerio rival</b>
 * (Discord, 08-11-2026). Ver {@link PhantomLandPlay}. {@code run.cmd phantomlandcheck}.
 *
 * <p>Grenzo en tu mesa, un Island en el cementerio del rival y un Forest en tu
 * mano (el control). Se mira: que el motor sigue ofreciendo el Island (si no,
 * Card-Forge lo ha arreglado y se dice), que no se ilumina, que clicarlo con el
 * mismo {@code selectCard} de un clic no lo juega, y que el Forest de la mano
 * si se juega igual.
 */
public final class PhantomLandCheck {

    private PhantomLandCheck() {
    }

    private static final String GRENZO = "Grenzo, Crooked Jailer|SummonSick:False";
    private static final String ISLAND = "Island";
    private static final String FOREST = "Forest";

    public static void run() {
        System.out.println("  Mesa: Grenzo en tu mesa, un Island en el cementerio del rival y un Forest en tu mano.");
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicInteger good = new AtomicInteger();
        final AtomicInteger bad = new AtomicInteger();
        final StringBuilder notes = new StringBuilder();

        final TutorialLesson lesson = position();
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 40, null, false, ui -> {
                    ui.setAutoPlayHold(() -> !done.get() && myMain(ui));
                    final Thread pilot = new Thread(() -> {
                        try {
                            for (int i = 0; i < 600 && !done.get(); i++) {
                                Thread.sleep(50L);
                                try {
                                    step(ui, done, good, bad, notes);
                                } catch (final RuntimeException e) {
                                    if (notes.indexOf("fallo:") < 0) {
                                        notes.append("fallo: ").append(e).append('\n');
                                    }
                                }
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.set(true);
                            ui.setAutoPlayHold(null);
                            ui.nudgeAutoPlay();
                        }
                    }, "phantomlandcheck");
                    pilot.setDaemon(true);
                    pilot.start();
                });

        System.out.println("  " + notes.toString().trim().replace("\n", "\n  "));
        if (good.get() + bad.get() == 0) {
            bad.incrementAndGet();
            System.out.println("  [MAL]  no se llego a mirar nada: la prueba no demuestra nada");
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", good.get(), bad.get());
        if (bad.get() > 0) {
            throw new IllegalStateException("Grenzo y las tierras del rival: no cuadra");
        }
    }

    private static void step(final NeoMatchUI ui, final AtomicBoolean done, final AtomicInteger good,
            final AtomicInteger bad, final StringBuilder notes) {
        if (done.get() || !myMain(ui) || !(ui.getGameController() instanceof PlayerControllerHuman gc)
                || !(gc.getInputQueue().getInput() instanceof InputPassPriority input)) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        final Player ai = me.getSingleOpponent();
        final Card island = first(ai.getCardsIn(ZoneType.Graveyard), ISLAND);
        final Card forest = first(me.getCardsIn(ZoneType.Hand), FOREST);
        if (island == null || forest == null) {
            return;
        }
        // done se pone al TERMINAR: mientras tanto el piloto no puede pasar
        // (setAutoPlayHold), o se iria de la fase entre clic y clic.
        try {
            look(ui, gc, input, me, island, forest, good, bad, notes);
        } finally {
            done.set(true);
        }
    }

    private static void look(final NeoMatchUI ui, final PlayerControllerHuman gc, final InputPassPriority input,
            final Player me, final Card island, final Card forest, final AtomicInteger good,
            final AtomicInteger bad, final StringBuilder notes) {

        // El controlador del jugador (el que relevamos), no el que tiene la mesa.
        final boolean guarded = me.getController() instanceof SafeActions.Guarded;
        check(guarded, "el controlador del jugador es SafeActions.Guarded", good, bad, notes);
        final PlayerControllerHuman seat = guarded ? (PlayerControllerHuman) me.getController() : gc;

        final List<SpellAbility> offered = island.getAllPossibleAbilities(me, true);
        final boolean engineBug = offered.stream().anyMatch(SpellAbility::isLandAbility);
        notes.append(engineBug
                ? "[info] el motor sigue ofreciendo el Island del rival (el fallo sigue rio arriba)\n"
                : "[info] el motor YA NO ofrece el Island del rival: Card-Forge lo ha arreglado, PhantomLandPlay sobra\n");
        check(PhantomLandPlay.strip(offered, me).stream().noneMatch(SpellAbility::isLandAbility),
                "PhantomLandPlay quita la jugada del Island", good, bad, notes);
        // Desde el 09-11-2026 Forge lo arregla rio arriba (#12770): el motor ya no
        // ofrece esa jugada, y entonces no hay "tierra fantasma" que detectar (el
        // envoltorio deja de saltar, como dice las notas de diseño). Mientras el motor la
        // siga ofreciendo, tiene que detectarla. Lo que ve el jugador se mira abajo.
        check(!engineBug || PhantomLandPlay.phantom(island, me),
                engineBug ? "el Island del rival cuenta como tierra sin permiso"
                        : "el motor ya no ofrece el Island del rival (Forge #12770): nada que detectar",
                good, bad, notes);
        check(!PhantomLandPlay.phantom(forest, me), "el Forest de tu mano no", good, bad, notes);

        final List<CardView> lit = seat.getActionableCards(false, null);
        check(lit.stream().noneMatch(v -> v != null && v.getId() == island.getId()),
                "el Island del rival no se ilumina", good, bad, notes);
        check(lit.stream().anyMatch(v -> v != null && v.getId() == forest.getId()),
                "el Forest de tu mano si se ilumina", good, bad, notes);

        final int before = me.getLandsPlayedThisTurn();
        input.selectCard(island, null, null);
        // La jugada se resuelve en el hilo del motor: darle tiempo a llegar.
        final long wait = System.currentTimeMillis() + 2000;
        while (island.isInZone(ZoneType.Graveyard) && System.currentTimeMillis() < wait) {
            try {
                Thread.sleep(50L);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        check(island.isInZone(ZoneType.Graveyard) && me.getLandsPlayedThisTurn() == before,
                "clicar el Island del rival no lo juega", good, bad, notes);

        // El control: el mismo clic sobre una tierra de verdad si la juega.
        final Object now = gc.getInputQueue().getInput();
        if (now instanceof InputPassPriority again) {
            again.selectCard(forest, null, null);
            final long until = System.currentTimeMillis() + 5000;
            while (!forest.isInZone(ZoneType.Battlefield) && System.currentTimeMillis() < until) {
                try {
                    Thread.sleep(50L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            final Card played = first(me.getCardsIn(ZoneType.Battlefield), FOREST);
            check(played != null, "clicar el Forest de la mano si lo juega (el clic funciona)", good, bad, notes);
        } else {
            check(false, "el motor sigue esperando prioridad tras clicar el Island", good, bad, notes);
        }
    }

    private static void check(final boolean ok, final String what, final AtomicInteger good,
            final AtomicInteger bad, final StringBuilder notes) {
        (ok ? good : bad).incrementAndGet();
        notes.append(ok ? "[ok]   " : "[MAL]  ").append(what).append('\n');
    }

    private static Card first(final Iterable<Card> cards, final String name) {
        for (final Card c : cards) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static boolean myMain(final NeoMatchUI ui) {
        if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return false;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        return me != null && game.getPhaseHandler().isPlayerTurn(me)
                && game.getPhaseHandler().getPhase() == PhaseType.MAIN1 && game.getStack().isEmpty();
    }

    private static Player human(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + GRENZO,
                "humanhand=" + FOREST,
                "humanlibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + ISLAND + ";" + ISLAND + ";" + ISLAND,
                "aigraveyard=" + ISLAND, "aiexile=", "aicommand=");
        return new TutorialLesson("phantomland", state, List.of());
    }
}
