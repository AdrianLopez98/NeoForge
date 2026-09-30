package forge.neo.match;

import java.util.Arrays;
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
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Lanzar con Armonizar (Harmonize) desde el cementerio</b>
 * ({@code run.cmd harmonizecheck}).
 *
 * <p>Reportado el 30-09-2026: <i>"Nature's Rhythm not castable from graveyard
 * using Harmonize"</i>, con el visor del cementerio diciendo "25 cartas se
 * pueden lanzar desde aqui" y esa sin marcar. El mismo olvido del motor que el
 * caos ({@link MayhemCheck}): {@code GameActionUtil} sabe lanzarla, pero
 * {@code PlayerZone.OwnCardsActivationFilter} no la pone en
 * {@code PlayerView.getFlashback()}, y el visor solo deja clicar lo de esa
 * lista. Lo completa {@link OutsideCasts#fromGraveyard}.
 *
 * <p>En el cementerio, <i>Unending Whisper</i> (armonizar {5}{U}: roba una
 * carta) y <i>Divination</i> (el control: sin armonizar, no se puede). Seis
 * Islas. En tu fase principal:
 *
 * <ol>
 *   <li>El motor ofrece lanzar la de armonizar y no la otra;
 *       {@link OutsideCasts} y la interfaz dicen lo mismo.</li>
 *   <li>Clicarla — el mismo {@code selectCard} que manda el visor — la saca
 *       del cementerio, se paga, se roba una carta y acaba <b>exiliada</b>,
 *       que es lo que hace armonizar.</li>
 * </ol>
 *
 * <p>Y cuenta, sin fallar por ello, si el motor ya la trae el solo en
 * {@code getFlashback()}: el dia que lo arreglen rio arriba, lo dira aqui.
 */
public final class HarmonizeCheck {

    private HarmonizeCheck() {
    }

    private static final String CARD = "Unending Whisper";
    private static final String CONTROL = "Divination";
    private static final String ISLAND = "Island|Set:M21";

    private static final int WAIT = 0;
    private static final int CASTING = 1;
    private static final int DONE = 2;

    private static int passed;
    private static int failed;

    private static final class Seen {
        private final AtomicInteger stage = new AtomicInteger(WAIT);
        private final AtomicLong since = new AtomicLong();
        private final AtomicInteger cardId = new AtomicInteger(-1);
        private final AtomicInteger handBefore = new AtomicInteger(-1);
        private final AtomicBoolean setUp = new AtomicBoolean();
        private final AtomicBoolean engineCanCast = new AtomicBoolean();
        private final AtomicBoolean engineCastsControl = new AtomicBoolean(true);
        private final AtomicBoolean sharedMarks = new AtomicBoolean();
        private final AtomicBoolean uiMarks = new AtomicBoolean();
        private final AtomicBoolean controlMarked = new AtomicBoolean(true);
        private final AtomicBoolean engineListsIt = new AtomicBoolean();
        private final AtomicBoolean leftGraveyard = new AtomicBoolean();
        private final AtomicBoolean exiled = new AtomicBoolean();
        private final AtomicBoolean drew = new AtomicBoolean();
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
        }, "harmonizecheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: " + CARD + " (armonizar {5}{U}) y " + CONTROL
                + " en el cementerio, seis Islas.");
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
            gui[0] = ui;
            // Retenido hasta que sale del cementerio (si no, el piloto pasa la
            // prioridad antes de que llegue el clic), y suelto despues: el
            // hechizo tiene que resolverse, y eso pide pasar la prioridad.
            ui.setAutoPlayHold(() -> seen.stage.get() < DONE && !seen.leftGraveyard.get());
            poller.start();
        });
        alive.set(false);

        check(seen.setUp.get(),
                "las dos estan en el cementerio en tu fase principal",
                "no se llego a montar la mesa: la prueba no prueba nada");
        check(seen.engineCanCast.get(),
                "el motor SI deja lanzar la de armonizar (getAllPossibleAbilities)",
                "el motor no la ofrece: la prueba no prueba nada");
        check(!seen.engineCastsControl.get(),
                "y no la de control, que no tiene armonizar",
                "el motor ofrece la de control: el control no controla nada");
        check(seen.sharedMarks.get(),
                "OutsideCasts.fromGraveyard la da por lanzable (lo que usa tambien Android)",
                "OutsideCasts NO la da por lanzable: Android tampoco la veria");
        check(seen.uiMarks.get(),
                "y la interfaz la marca, asi que el visor del cementerio deja clicarla",
                "la interfaz NO la marca: se ve en el cementerio y no hay forma de lanzarla");
        check(!seen.controlMarked.get(),
                "la de control no sale lanzable (el motor tampoco deja)",
                "sale lanzable una carta sin armonizar: el motor no la dejaria");
        check(seen.leftGraveyard.get(),
                "clicarla (el mismo selectCard que el visor) la saca del cementerio",
                "el click no la saco del cementerio");
        check(seen.drew.get() && seen.exiled.get(),
                "se paga, roba una carta y acaba exiliada, como manda armonizar",
                "salio del cementerio pero no termino: robo=" + seen.drew.get()
                        + ", exiliada=" + seen.exiled.get());
        System.out.println("  [info] el motor " + (seen.engineListsIt.get()
                ? "YA la trae en getFlashback(): arreglado rio arriba, OutsideCasts.graveyardKeyword sobra"
                : "sigue sin traerla en getFlashback(): OutsideCasts.graveyardKeyword hace falta"));

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de armonizar han fallado");
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
                if (!myMain || !priority) {
                    seen.since.set(now);
                    return;
                }
                // Un segundo con la prioridad quieta: la mesa ya esta pintada.
                if (now - seen.since.get() < 1000) {
                    return;
                }
                final Card card = named(me, ZoneType.Graveyard, CARD);
                final Card control = named(me, ZoneType.Graveyard, CONTROL);
                seen.setUp.set(card != null && control != null);
                if (card == null) {
                    seen.stage.set(DONE);
                    return;
                }
                seen.cardId.set(card.getId());
                seen.handBefore.set(me.getCardsIn(ZoneType.Hand).size());
                seen.engineCanCast.set(!card.getAllPossibleAbilities(me, true).isEmpty());
                seen.engineCastsControl.set(control != null
                        && !control.getAllPossibleAbilities(me, true).isEmpty());
                seen.sharedMarks.set(OutsideCasts.fromGraveyard(card));
                // isMayhemCastable y no isPlayableOutside: esa ademas pide
                // interactive(), que en el piloto automatico es falso.
                seen.uiMarks.set(ui.isMayhemCastable(card.getView()));
                seen.controlMarked.set(control != null && (OutsideCasts.fromGraveyard(control)
                        || ui.isMayhemCastable(control.getView())));
                for (final CardView cv : me.getView().getFlashback()) {
                    if (cv != null && cv.getId() == card.getId()) {
                        seen.engineListsIt.set(true);
                    }
                }
                seen.since.set(now);
                seen.stage.set(CASTING);
                // El MISMO metodo que llama el visor al clicar la carta.
                final CardView target = card.getView();
                GuiBase.getInterface().invokeInEdtLater(() -> gc.selectCard(target, null, null));
                break;
            }
            case CASTING: {
                if (!seen.leftGraveyard.get()
                        && byId(me, ZoneType.Graveyard, seen.cardId.get()) == null) {
                    seen.leftGraveyard.set(true);
                    ui.nudgeAutoPlay();
                }
                if (me.getCardsIn(ZoneType.Hand).size() > seen.handBefore.get()) {
                    seen.drew.set(true);
                }
                if (byId(me, ZoneType.Exile, seen.cardId.get()) != null
                        || named(me, ZoneType.Exile, CARD) != null) {
                    seen.exiled.set(true);
                }
                if (seen.drew.get() && seen.exiled.get()) {
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

    private static Card named(final Player me, final ZoneType zone, final String name) {
        for (final Card c : me.getCardsIn(zone)) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
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
        final String islands = String.join(";", ISLAND, ISLAND, ISLAND, ISLAND, ISLAND, ISLAND);
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + islands,
                "humanhand=",
                "humanlibrary=" + ISLAND + ";" + ISLAND + ";" + ISLAND,
                "humangraveyard=" + CARD + ";" + CONTROL,
                "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + ISLAND + ";" + ISLAND + ";" + ISLAND,
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("harmonize", state, List.of());
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
