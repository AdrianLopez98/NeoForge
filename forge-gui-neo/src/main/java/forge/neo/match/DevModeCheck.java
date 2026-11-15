package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.interfaces.IDevModeCheats;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * {@code run.cmd devmodecheck}: el modo desarrollador (NeoSettings.DEV_MODE)
 * con las trampas de Forge en una partida de verdad, lanzadas como las lanza el
 * menu Dev: desde un hilo que no es ni el de JavaFX ni el del motor
 * ({@code PauseMenu.devButton}). Que hagan lo que dicen, y sobre todo que
 * VUELVAN: una trampa que pregunta algo y se queda esperando para siempre
 * dejaria la partida muerta.
 */
public final class DevModeCheck {

    private DevModeCheck() {
    }

    public static void run() {
        final List<String> results = Collections.synchronizedList(new ArrayList<>());
        final List<String> notes = Collections.synchronizedList(new ArrayList<>());
        final TutorialLesson lesson = new TutorialLesson("devmode", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=Plains|Set:M21|Tapped;Plains|Set:M21|Tapped;Grizzly Bears|Set:8ED|Tapped",
                "humanhand=", "humanlibrary=Plains|Set:M21;Plains|Set:M21;Plains|Set:M21",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Mountain|Set:M21",
                "aihand=", "ailibrary=Island|Set:M21;Island|Set:M21;Island|Set:M21",
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 60, null,
                false, gui -> {
                    gui.setAutoPlayHold(() -> results.size() < 3 && notes.isEmpty());
                    final Thread t = new Thread(() -> {
                        try {
                            Game game = null;
                            PlayerControllerHuman pc = null;
                            for (int i = 0; i < 400 && game == null; i++) {
                                Thread.sleep(50);
                                if (gui.getGameController() instanceof PlayerControllerHuman c
                                        && c.getInputQueue().getInput() instanceof InputPassPriority) {
                                    pc = c;
                                    game = c.getPlayer().getGame();
                                }
                            }
                            if (game == null) {
                                notes.add("la partida no llego a tu prioridad");
                                return;
                            }
                            final IDevModeCheats cheats = gui.getGameController().cheat();
                            final Player me = pc.getPlayer();

                            // 1. Mana: 7 de cada color y 7 incoloro.
                            final boolean manaBack = runOff(cheats::generateMana);
                            Thread.sleep(500);
                            final int pool = me.getManaPool().totalMana();
                            results.add(ok(manaBack && pool >= 42, "Anadir mana: " + pool + " en la reserva"));

                            // 2. Tierras sin limite: un interruptor, sin pregunta.
                            final boolean landsBack = runOff(() -> cheats.setCanPlayUnlimitedLands(true));
                            results.add(ok(landsBack,
                                    "Tierras sin limite: vuelve"));

                            // 3. Enderezar: pregunta CUALES (cartas de la mesa). El piloto
                            // contesta; lo que importa es que vuelve y la partida sigue.
                            final boolean untapBack = runOff(cheats::untapPermanents);
                            Thread.sleep(500);
                            int tapped = 0;
                            for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
                                tapped += c.isTapped() ? 1 : 0;
                            }
                            results.add(ok(untapBack, "Enderezar permanentes: vuelve (giradas ahora: " + tapped + ")"));
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (final RuntimeException e) {
                            notes.add("excepcion: " + e);
                            e.printStackTrace();
                        } finally {
                            gui.nudgeAutoPlay();
                        }
                    }, "devmodecheck");
                    t.setDaemon(true);
                    t.start();
                });

        int bad = 0;
        for (final String r : results) {
            bad += r.startsWith("[MAL]") ? 1 : 0;
            System.out.println("  " + r);
        }
        notes.forEach(n -> System.out.println("  - " + n));
        bad += notes.size();
        if (results.size() < 3) {
            bad++;
            System.out.println("  - solo " + results.size() + " de 3 pasos");
        }
        System.out.println();
        System.out.println("  " + (results.size() - Math.min(bad, results.size())) + " bien, " + bad + " mal");
        if (bad > 0) {
            throw new IllegalStateException("el modo desarrollador falla");
        }
    }

    /** Como el menu Dev: en su propio hilo. true si vuelve en 15 s. */
    private static boolean runOff(final Runnable cheat) throws InterruptedException {
        final Thread t = new Thread(cheat, "neo-dev-cheat");
        t.setDaemon(true);
        t.start();
        t.join(TimeUnit.SECONDS.toMillis(15));
        return !t.isAlive();
    }

    private static String ok(final boolean good, final String what) {
        return (good ? "[OK ] " : "[MAL] ") + what;
    }
}
