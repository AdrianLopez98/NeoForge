package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Lanzar con Caos (Mayhem) desde el cementerio</b> ({@code run.cmd mayhemcheck}).
 *
 * <p>Reportado en itch.io el 28-09-2026: <i>"I can't figure out how to cast
 * from the graveyard using Mayhem. Even with the correct mana available, I
 * don't see an option when looking at the graveyard. It is definitely the same
 * turn it was discarded"</i>. El motor sabe lanzarla ({@code GameActionUtil},
 * rama "Mayhem"), pero no la pone en {@code PlayerView.getFlashback()}, y el
 * visor del cementerio solo deja clicar lo de esa lista. Lo completa
 * {@link OutsideCasts#mayhem}, que usan {@code NeoMatchUI.isMayhemCastable} y
 * Android; esto comprueba que funciona.
 *
 * <p>Dos <i>Spider-Islanders</i> en la mano y dos Montanyas. En tu fase
 * principal:
 *
 * <ol>
 *   <li>Una se <b>descarta</b> de verdad ({@code Player.discard}), la otra va
 *       al cementerio <b>sin descartarse</b> (el control: esa no vale).</li>
 *   <li>El motor ofrece lanzar la descartada, y {@link OutsideCasts} y la
 *       interfaz la marcan. La otra no.</li>
 *   <li>Clicarla — el mismo {@code selectCard} que manda el visor — la
 *       <b>saca del cementerio</b>.</li>
 * </ol>
 *
 * <p>Ademas cuenta, sin fallar por ello, si el motor ya la trae el solo en
 * {@code getFlashback()}: el dia que lo arreglen rio arriba, lo dira aqui.
 */
public final class MayhemCheck {

    private MayhemCheck() {
    }

    private static final String CARD = "Spider-Islanders";
    private static final String MOUNTAIN = "Mountain|Set:M21";

    private static final int WAIT = 0;
    private static final int DISCARDED = 1;
    private static final int CASTING = 2;
    private static final int DONE = 3;

    private static int passed;
    private static int failed;

    private static final class Seen {
        private final AtomicInteger stage = new AtomicInteger(WAIT);
        private final AtomicLong since = new AtomicLong();
        private final AtomicInteger discardedId = new AtomicInteger(-1);
        private final AtomicInteger controlId = new AtomicInteger(-1);
        private final AtomicBoolean setUp = new AtomicBoolean();
        private final AtomicBoolean engineCanCast = new AtomicBoolean();
        private final AtomicBoolean sharedMarks = new AtomicBoolean();
        private final AtomicBoolean uiMarks = new AtomicBoolean();
        private final AtomicBoolean controlMarked = new AtomicBoolean(true);
        private final AtomicBoolean engineListsIt = new AtomicBoolean();
        private final AtomicBoolean leftGraveyard = new AtomicBoolean();
        private final AtomicBoolean onBattlefield = new AtomicBoolean();
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
        }, "mayhemcheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: dos " + CARD + " en la mano y dos Montanyas. Una se descarta;");
        System.out.println("  la otra va al cementerio sin descartarse.");
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
            gui[0] = ui;
            // Retenido tambien mientras se lanza: si se suelta con el clic en
            // el aire, el piloto pasa la prioridad antes de que llegue.
            // Y se suelta en cuanto ha salido del cementerio: el hechizo tiene
            // que resolverse, y eso pide pasar la prioridad.
            ui.setAutoPlayHold(() -> seen.stage.get() < DONE && !seen.leftGraveyard.get());
            poller.start();
        });
        alive.set(false);

        check(seen.setUp.get(),
                "una se descarta de verdad y la otra llega al cementerio sin descartarse",
                "no se llego a montar la mesa: la prueba no prueba nada");
        check(seen.engineCanCast.get(),
                "el motor SI deja lanzar la descartada (getAllPossibleAbilities)",
                "el motor no la ofrece: la prueba no prueba nada");
        check(seen.sharedMarks.get(),
                "OutsideCasts.mayhem la da por lanzable (lo que usa tambien Android)",
                "OutsideCasts.mayhem NO la da por lanzable: Android tampoco la veria");
        check(seen.uiMarks.get(),
                "y la interfaz la marca, asi que el visor del cementerio deja clicarla",
                "la interfaz NO la marca: se veria en el cementerio y no habria forma de lanzarla");
        check(!seen.controlMarked.get(),
                "la que NO se descarto no sale lanzable (el motor tampoco deja)",
                "sale lanzable una carta que no se descarto: el motor no la dejaria");
        check(seen.leftGraveyard.get(),
                "clicarla (el mismo selectCard que el visor) la saca del cementerio",
                "el click no la saco del cementerio");
        check(seen.onBattlefield.get(),
                "y se paga su coste de caos y acaba en la mesa",
                "salio del cementerio pero no llego a la mesa: el lanzamiento no termino");
        System.out.println("  [info] el motor " + (seen.engineListsIt.get()
                ? "YA la trae en getFlashback(): arreglado rio arriba, OutsideCasts.mayhem sobra"
                : "sigue sin traerla en getFlashback(): OutsideCasts.mayhem hace falta"));

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del caos han fallado");
        }
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

        switch (seen.stage.get()) {
            case WAIT: {
                final List<Card> hand = named(me, ZoneType.Hand);
                if (!myMain || !priority || hand.size() < 2) {
                    return;
                }
                final Card discard = hand.get(0);
                final Card control = hand.get(1);
                seen.discardedId.set(discard.getId());
                seen.controlId.set(control.getId());
                // El motor esta parado esperando tu prioridad: nadie mas toca
                // la partida. El descarte es el del motor, con su marca.
                me.discard(discard, null, false, AbilityKey.newMap());
                game.getAction().moveToGraveyard(control, null, AbilityKey.newMap());
                seen.since.set(now);
                seen.stage.set(DISCARDED);
                break;
            }
            case DISCARDED: {
                if (now - seen.since.get() < 1000 || !priority) {
                    return;
                }
                final Card discarded = byId(me, ZoneType.Graveyard, seen.discardedId.get());
                final Card control = byId(me, ZoneType.Graveyard, seen.controlId.get());
                seen.setUp.set(discarded != null && discarded.wasDiscarded()
                        && control != null && !control.wasDiscarded());
                if (discarded == null) {
                    seen.stage.set(DONE);
                    return;
                }
                seen.engineCanCast.set(!discarded.getAllPossibleAbilities(me, true).isEmpty());
                seen.sharedMarks.set(OutsideCasts.mayhem(discarded));
                seen.uiMarks.set(ui.isMayhemCastable(discarded.getView()));
                seen.controlMarked.set(control != null && (OutsideCasts.mayhem(control)
                        || ui.isMayhemCastable(control.getView())));
                for (final CardView cv : me.getView().getFlashback()) {
                    if (cv != null && cv.getId() == discarded.getId()) {
                        seen.engineListsIt.set(true);
                    }
                }
                seen.since.set(now);
                seen.stage.set(CASTING);
                // El MISMO metodo que llama el visor al clicar la carta.
                final CardView target = discarded.getView();
                GuiBase.getInterface().invokeInEdtLater(() -> gc.selectCard(target, null, null));
                break;
            }
            case CASTING: {
                if (!seen.leftGraveyard.get()
                        && byId(me, ZoneType.Graveyard, seen.discardedId.get()) == null) {
                    seen.leftGraveyard.set(true);
                    ui.nudgeAutoPlay();
                }
                // Y que acabe en la mesa: pagado el {1}{R} y resuelta.
                if (!named(me, ZoneType.Battlefield).isEmpty()) {
                    seen.onBattlefield.set(true);
                    seen.stage.set(DONE);
                } else if (now - seen.since.get() > 15000) {
                    seen.stage.set(DONE);
                }
                break;
            }
            default:
                break;
        }
    }

    private static List<Card> named(final Player me, final ZoneType zone) {
        final List<Card> out = new java.util.ArrayList<>();
        for (final Card c : me.getCardsIn(zone)) {
            if (CARD.equals(c.getName())) {
                out.add(c);
            }
        }
        return out;
    }

    private static Card byId(final Player me, final ZoneType zone, final int id) {
        for (final Card c : me.getCardsIn(zone)) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
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
                "humanbattlefield=" + MOUNTAIN + ";" + MOUNTAIN,
                "humanhand=" + CARD + ";" + CARD,
                "humanlibrary=" + MOUNTAIN + ";" + MOUNTAIN + ";" + MOUNTAIN,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + MOUNTAIN + ";" + MOUNTAIN + ";" + MOUNTAIN,
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("mayhem", state, List.of());
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
