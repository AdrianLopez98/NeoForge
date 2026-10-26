package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.eventbus.Subscribe;

import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.event.EventValueChangeType;
import forge.game.event.GameEvent;
import forge.game.event.GameEventManaPool;
import forge.game.event.GameEventSpellAbilityCast;
import forge.game.event.GameEventTurnPhase;
import forge.game.player.Player;
import forge.game.zone.Zone;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Reproduccion</b> de un informe de Discord (26-10-2026, Android, duelo de
 * la Aventura): con Electro, Assaulting Battery <b>en la zona de mando</b>,
 * Hammer of Bogardan ({1}{R}{R}) se pago con dos Montanyas y aun quedo una
 * {R} en la reserva. Su teoria: <i>"the engine thinks Electro is on the field
 * and resolving the +1 red for playing an instant/sorcery"</i>.
 *
 * <p>No es de la bateria. La IA lleva Electro y lanza quemadores; se apunta
 * cada mana que entra en su reserva (y en que fase), cada hechizo, y cuanta
 * {R} le queda al cambiar de fase (la otra mitad de Electro: no se vacia).
 *
 * <ul>
 *   <li><b>A</b>: Electro en la zona de mando, sin haber salido nunca.</li>
 *   <li><b>B</b>: Electro en la mesa (para comparar: aqui SI tiene que dar {R}).</li>
 *   <li><b>C</b>: Electro en la mesa, muere al empezar y vuelve a la zona de mando
 *       (la captura: "+2" de impuesto, o sea que ya habia salido una vez).</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd electrocheck}.
 */
public final class ElectroCheck {

    private ElectroCheck() {
    }

    private static final String MOUNTAIN = "Mountain|Set:M21";
    private static final String ELECTRO = "Electro, Assaulting Battery|IsCommander";
    private static final String BURN = "Lava Spike|Set:CHK;Lightning Bolt|Set:M10;Shock|Set:M21;Hammer of Bogardan|Set:MIR";
    private static final String KILL_ALL =
            "Mountain:CustomScript:AB$ DestroyAll | Cost$ 0 | ValidCards$ Creature";
    private static final int SECONDS = 30;

    public static void run() {
        // Sin IsCommander: si no, la IA lo lanza en el primer turno y ya no esta en la zona de mando.
        play("A: Electro en la zona de mando toda la partida", "", "Electro, Assaulting Battery", null);
        play("B: Electro en la mesa (aqui SI da {R})", ELECTRO, "", null);
        play("C: Electro en la mesa, muere al empezar y vuelve a la zona de mando", ELECTRO, "", KILL_ALL);
    }

    private static void play(final String title, final String aiExtraField, final String aiCommand,
            final String precast) {
        System.out.println();
        System.out.println("  Mesa " + title);
        final List<String> notes = Collections.synchronizedList(new ArrayList<>());
        final List<String> lines = new ArrayList<>(Arrays.asList(
                "turn=4", "activeplayer=ai", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + MOUNTAIN,
                "humanhand=",
                "humanlibrary=" + (MOUNTAIN + ";").repeat(6) + MOUNTAIN,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + (MOUNTAIN + ";").repeat(4) + MOUNTAIN
                        + (aiExtraField.isEmpty() ? "" : ";" + aiExtraField),
                "aihand=" + BURN,
                "ailibrary=" + (MOUNTAIN + ";").repeat(6) + MOUNTAIN,
                "aigraveyard=", "aiexile=", "aicommand=" + aiCommand));
        if (precast != null) {
            lines.add("humanprecast=" + precast);
        }
        final TutorialLesson lesson = new TutorialLesson("electro", lines, List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, SECONDS, null, false, gui -> {
                    final Thread t = new Thread(() -> {
                        final long until = System.currentTimeMillis() + (SECONDS - 5) * 1000L;
                        final AtomicBoolean hooked = new AtomicBoolean();
                        while (System.currentTimeMillis() < until && !hooked.get()) {
                            try {
                                Thread.sleep(50);
                                if (gui.getGameController() == null) {
                                    continue;
                                }
                                final Player me = ((PlayerControllerHuman) NeoMatchUI.seated(
                                        gui.getGameController())).getPlayer();
                                final Game game = me.getGame();
                                Player ai = null;
                                for (final Player p : game.getPlayers()) {
                                    if (p != me) {
                                        ai = p;
                                    }
                                }
                                if (ai == null) {
                                    continue;
                                }
                                game.subscribeToEvents(new Ears(game, ai, notes));
                                hooked.set(true);
                            } catch (final InterruptedException e) {
                                return;
                            } catch (final RuntimeException e) {
                                // La partida aun no esta lista: otra vuelta.
                            }
                        }
                    }, "electro-probe");
                    t.setDaemon(true);
                    t.start();
                });
        synchronized (notes) {
            if (notes.isEmpty()) {
                System.out.println("    (no se apunto nada)");
            }
            for (final String n : notes) {
                System.out.println("    " + n);
            }
        }
    }

    /** Escucha en el hilo del motor (el bus es sincrono): se puede leer el estado sin carreras. */
    public static final class Ears {
        private final Game game;
        private final Player ai;
        private final List<String> notes;

        Ears(final Game game, final Player ai, final List<String> notes) {
            this.game = game;
            this.ai = ai;
            this.notes = notes;
        }

        @Subscribe
        public void receive(final GameEvent ev) {
            try {
                if (ev instanceof GameEventSpellAbilityCast cast && cast.sa() != null
                        && cast.sa().getHostCard() != null && cast.sa().getHostCard().getController() != null
                        && cast.sa().getHostCard().getController().getId() == ai.getId()) {
                    notes.add(where() + "LANZA " + cast.sa().getHostCard().getName()
                            + " | tierras giradas: " + tapped() + " | {R} en reserva: " + red());
                } else if (ev instanceof GameEventManaPool pool && pool.player().getId() == ai.getId()
                        && pool.mode() == EventValueChangeType.Added) {
                    notes.add(where() + "  + mana " + pool.colors() + " | {R} en reserva: " + red()
                            + " | tierras giradas: " + tapped());
                } else if (ev instanceof GameEventTurnPhase && red() > 0) {
                    notes.add(where() + "  cambio de fase con {R} en reserva: " + red());
                }
            } catch (final RuntimeException e) {
                notes.add("(fallo al apuntar: " + e + ")");
            }
        }

        private int red() {
            return ai.getManaPool().getAmountOfColor(MagicColor.RED);
        }

        private long tapped() {
            return ai.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isLand() && c.isTapped()).count();
        }

        private String where() {
            String electro = "?";
            for (final Card c : game.getCardsInGame()) {
                if (c.getName().startsWith("Electro")) {
                    final Zone z = game.getZoneOf(c);
                    electro = z == null ? "-" : z.getZoneType().name();
                }
            }
            return "[T" + game.getPhaseHandler().getTurn() + " " + game.getPhaseHandler().getPhase()
                    + " | Electro: " + electro + "] ";
        }
    }
}
