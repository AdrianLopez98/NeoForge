package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * {@code run.cmd commandercolorcheck}: el color elegido de un comandante
 * "comodin" sobrevive a los cambios de zona (ver {@link CommanderColorKeeper}).
 *
 * <p>Discord, 11-10-2026: Clara Oswald con el negro elegido sobrevivia a All Is
 * Dust. El motor pierde el color en la copia que hace al cambiar de zona; aqui
 * se mueve Clara del mando al cementerio (la mudanza que hace copia, como la
 * del mando a la pila al lanzarla) y de ahi a la mesa, por el motor de verdad, y
 * se mira que siga negra y que el filtro de All Is Dust
 * ({@code Permanent.nonColorless}) la coja.
 */
public final class CommanderColorCheck {

    private CommanderColorCheck() {
    }

    private static int passed;
    private static int failed;

    private static final class Seen {
        private final AtomicBoolean done = new AtomicBoolean();
        private final AtomicBoolean startBlack = new AtomicBoolean();
        private final AtomicBoolean newObject = new AtomicBoolean();
        private final AtomicReference<List<String>> afterMove = new AtomicReference<>(List.of());
        private final AtomicBoolean fieldBlack = new AtomicBoolean();
        private final AtomicBoolean dustTakesIt = new AtomicBoolean();
    }

    public static void run() {
        passed = 0;
        failed = 0;
        final Seen seen = new Seen();
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean alive = new AtomicBoolean(true);
        final Thread poller = new Thread(() -> {
            while (alive.get() && !seen.done.get()) {
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
        }, "commandercolorcheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: Clara Oswald en tu zona de mando, con el negro elegido.");
        final TutorialLesson lesson = position();
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY,
                60, null, false, ui -> {
                    gui[0] = ui;
                    ui.setAutoPlayHold(() -> !seen.done.get());
                    poller.start();
                });
        alive.set(false);

        check(seen.startBlack.get(),
                "en el mando es negra (el color elegido antes de empezar)",
                "no empezo negra en el mando: la prueba no prueba nada");
        check(seen.newObject.get(),
                "al cambiar de zona el motor hace una copia nueva (donde se perdia el color)",
                "el motor no hizo copia: la prueba no recorre el camino del fallo");
        check(seen.afterMove.get().contains("black"),
                "y la copia conserva el negro: " + seen.afterMove.get(),
                "la copia llega SIN color: " + seen.afterMove.get());
        check(seen.fieldBlack.get(),
                "en la mesa sigue siendo negra",
                "en la mesa es incolora");
        check(seen.dustTakesIt.get(),
                "All Is Dust (Permanent.nonColorless) se la llevaria",
                "All Is Dust NO se la llevaria: el fallo de Discord");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del color de comandante han fallado");
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
        Player me = null;
        for (final Player p : game.getPlayers()) {
            if (!p.getController().isAI()) {
                me = p;
            }
        }
        if (me == null) {
            return;
        }
        final PhaseHandler ph = game.getPhaseHandler();
        if (!ph.isPlayerTurn(me) || ph.getPhase() != PhaseType.MAIN1
                || !(gc.getInputQueue().getInput() instanceof InputPassPriority)) {
            return;
        }
        Card clara = null;
        for (final Card c : me.getCardsIn(ZoneType.Command)) {
            if ("Clara Oswald".equals(c.getName())) {
                clara = c;
            }
        }
        if (clara == null) {
            seen.done.set(true);
            return;
        }
        // El motor esta parado esperando tu prioridad: nadie mas toca la partida.
        game.getAction().checkStaticAbilities();
        seen.startBlack.set(clara.getColor().hasBlack());

        final Card inYard = game.getAction().moveToGraveyard(clara, null, AbilityKey.newMap());
        seen.newObject.set(inYard != null && inYard != clara);
        final List<String> colors = new ArrayList<>();
        if (inYard != null) {
            inYard.getChosenColors().forEach(colors::add);
        }
        seen.afterMove.set(colors);

        final Card onField = inYard == null ? null
                : game.getAction().moveToPlay(inYard, null, AbilityKey.newMap());
        game.getAction().checkStaticAbilities();
        if (onField != null) {
            seen.fieldBlack.set(onField.getColor().hasBlack());
            seen.dustTakesIt.set(onField.isValid("Permanent.nonColorless", me, null, null));
        }
        seen.done.set(true);
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=40",
                "ailife=40",
                "humancommand=Clara Oswald|IsCommander|ChosenColor:black",
                "humanbattlefield=Swamp;Swamp",
                "humanhand=",
                "humanlibrary=Swamp;Swamp;Swamp",
                "humangraveyard=", "humanexile=",
                "aibattlefield=", "aihand=",
                "ailibrary=Swamp;Swamp;Swamp",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("commandercolor", state, List.of());
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
