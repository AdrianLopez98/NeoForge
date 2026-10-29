package forge.neo.sound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.eventbus.Subscribe;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.event.GameEvent;
import forge.game.event.GameEventCardDestroyed;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * Destruir suena segun el tipo ({@code run.cmd destroysoundcheck}, ver
 * {@link DestroySound}). En la mesa del rival un artefacto criatura, una
 * criatura, Sol Ring, una Montanya, un encantamiento y un planeswalker; se
 * destruyen uno a uno con la partida parada esperando tu prioridad, y en cada
 * aviso de "destruida" se apunta el monton que elegiria el sonido. Es lo mismo
 * que hace {@link NeoSounds#pick} al sonar: el aviso llega con la carta aun en
 * la mesa y marcada.
 */
public final class DestroySoundCheck {

    private DestroySoundCheck() {
    }

    private static final String[][] EXPECTED = {
        {"Steel Overseer", "destroy_artifact_creature"},
        {"Grizzly Bears", null},
        {"Sol Ring", "destroy_artifact"},
        {"Mountain", "destroy_land"},
        {"Glorious Anthem", "destroy_enchantment"},
        {"Jace Beleren", null},
    };

    public static void run() {
        final Map<String, String> heard = Collections.synchronizedMap(new LinkedHashMap<>());
        final List<String> notes = Collections.synchronizedList(new ArrayList<>());
        final TutorialLesson lesson = new TutorialLesson("destroysound", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=Plains|Set:M21",
                "humanhand=", "humanlibrary=Plains|Set:M21;Plains|Set:M21;Plains|Set:M21",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Steel Overseer|Set:M11;Grizzly Bears|Set:6ED;Sol Ring|Set:C21;"
                        + "Mountain|Set:M21;Glorious Anthem|Set:M10;Jace Beleren|Set:M10|Counters:LOYALTY=3",
                "aihand=", "ailibrary=Island|Set:M21;Island|Set:M21;Island|Set:M21",
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 30, null,
                false, gui -> {
                    gui.setAutoPlayHold(() -> heard.size() < EXPECTED.length && notes.isEmpty());
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
                            // Como NeoSounds.play: lo que habia al sonar el aviso,
                            // y la carta nueva cuando ya se ha movido.
                            final List<Map<Player, Integer>> before = Collections.synchronizedList(new ArrayList<>());
                            game.subscribeToEvents(new Object() {
                                @Subscribe
                                public void on(final GameEvent ev) {
                                    if (ev instanceof GameEventCardDestroyed) {
                                        before.add(DestroySound.snapshot());
                                    }
                                }
                            });
                            Player ai = null;
                            for (final Player p : game.getPlayers()) {
                                if (p != pc.getPlayer()) {
                                    ai = p;
                                }
                            }
                            for (final String[] e : EXPECTED) {
                                Card target = null;
                                for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
                                    if (c.getName().equals(e[0])) {
                                        target = c;
                                    }
                                }
                                if (target == null) {
                                    notes.add("no esta en la mesa: " + e[0]);
                                    continue;
                                }
                                before.clear();
                                game.getAction().destroy(target, null, false, null);
                                heard.put(e[0], before.isEmpty() ? "sin aviso"
                                        : String.valueOf(DestroySound.poolSince(before.get(0))));
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (final RuntimeException e) {
                            notes.add("excepcion: " + e);
                            e.printStackTrace();
                        } finally {
                            gui.nudgeAutoPlay();
                        }
                    }, "destroysoundcheck");
                    t.setDaemon(true);
                    t.start();
                });

        int bad = 0;
        for (final String[] e : EXPECTED) {
            final String got = heard.get(e[0]);
            final boolean ok = String.valueOf(e[1]).equals(got);
            bad += ok ? 0 : 1;
            System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + e[0] + " -> " + got
                    + (ok ? "" : " (tocaba " + e[1] + ")"));
        }
        notes.forEach(n -> System.out.println("  - " + n));
        bad += notes.size();
        System.out.println();
        System.out.println("  " + (EXPECTED.length - Math.min(bad, EXPECTED.length)) + " bien, " + bad + " mal");
        if (bad > 0) {
            throw new IllegalStateException("el sonido de destruir segun el tipo falla");
        }
    }
}
