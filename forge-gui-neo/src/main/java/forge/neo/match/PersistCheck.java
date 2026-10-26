package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import forge.game.Game;
import forge.game.GameLogEntry;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Reproduccion</b> de un informe de Discord (26-10-2026): Cauldron of Souls
 * da persistencia a Atsushi, the Blazing Sky, el rival lanza Zodiark, Umbral
 * God, se sacrifica Atsushi... y no vuelve. Ni salta su propio "cuando muera".
 *
 * <p>No es un comprobador de la bateria: es para ver que hace el MOTOR (las
 * reglas son de Forge). Todo lo monta el dialecto de los puzzles de Forge, sin
 * tocar el hilo del motor: {@code humanprecast} resuelve, con Cauldron of Souls
 * de fuente, la misma habilidad que ella (persistencia hasta fin de turno), y
 * {@code aiputonstack} pone a Zodiark en la pila de la IA. Tres mesas:
 *
 * <ul>
 *   <li><b>A</b>: tus dos Kitchen Finks (persistencia de serie).</li>
 *   <li><b>B</b>: Atsushi y unos Grizzly Bears, con la persistencia de Cauldron.</li>
 *   <li><b>C</b>: Atsushi y los Bears sin ella: su disparo de muerte solo.</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd persistcheck} y cuenta lo que ve.
 */
public final class PersistCheck {

    private PersistCheck() {
    }

    private static final String SWAMP = "Swamp|Set:M21";
    private static final String FINKS = "Kitchen Finks|Set:SHM";
    private static final String ATSUSHI = "Atsushi, the Blazing Sky|Set:FDC";
    private static final String BEARS = "Grizzly Bears|Set:6ED";
    private static final String CAULDRON = "Cauldron of Souls|Set:C16";
    /** La habilidad de Cauldron of Souls sobre todas tus criaturas: persistencia hasta fin de turno. */
    private static final String CAULDRON_PUMP =
            "Cauldron of Souls:CustomScript:AB$ PumpAll | Cost$ 0 | ValidCards$ Creature.YouCtrl | KW$ Persist";
    private static final int SECONDS = 40;

    public static void run() {
        play("A: dos Kitchen Finks (persistencia de serie)", FINKS + ";" + FINKS, null);
        play("B: Atsushi y Grizzly Bears, con la persistencia de Cauldron of Souls",
                ATSUSHI + ";" + BEARS + ";" + CAULDRON, CAULDRON_PUMP);
        play("C: Atsushi y Grizzly Bears, sin Cauldron", ATSUSHI + ";" + BEARS, null);
    }

    private static void play(final String title, final String humanField, final String precast) {
        System.out.println();
        System.out.println("  Mesa " + title);
        final AtomicReference<String> report = new AtomicReference<>();
        final List<String> lines = new ArrayList<>(Arrays.asList(
                "turn=4", "activeplayer=ai", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + humanField,
                "humanhand=",
                "humanlibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + SWAMP, "aihand=",
                "ailibrary=" + (SWAMP + ";").repeat(6) + SWAMP,
                "aigraveyard=", "aiexile=", "aicommand=",
                "aiputonstack=Zodiark, Umbral God"));
        if (precast != null) {
            lines.add("humanprecast=" + precast);
        }
        final TutorialLesson lesson = new TutorialLesson("persist", lines, List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, SECONDS, null, false, gui -> {
                    final Thread t = new Thread(() -> {
                        final long until = System.currentTimeMillis() + (SECONDS - 5) * 1000L;
                                        while (System.currentTimeMillis() < until && report.get() == null) {
                            try {
                                Thread.sleep(100);
                                if (gui.getGameController() == null) {
                                    continue;
                                }
                                final Player me = ((PlayerControllerHuman) NeoMatchUI.seated(
                                        gui.getGameController())).getPlayer();
                                final Game game = me.getGame();
                                // Zodiark se resuelve en el turno 4 de la IA: en cuanto empieza el
                                // 5 (el tuyo) ya se ha resuelto todo lo suyo, con sus disparos.
                                if (game.getPhaseHandler().getTurn() < 5) {
                                    continue;
                                }
                                final StringBuilder sb = new StringBuilder();
                                sb.append("  En tu mesa:");
                                for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
                                    if (c.isCreature()) {
                                        sb.append(" [").append(c.getName()).append(" -1/-1:")
                                                .append(c.getCounters(CounterEnumType.M1M1)).append("]");
                                    }
                                }
                                sb.append("\n  En tu cementerio:");
                                for (final Card c : me.getCardsIn(ZoneType.Graveyard)) {
                                    sb.append(" [").append(c.getName()).append("]");
                                }
                                sb.append("\n  En tu exilio: ").append(me.getCardsIn(ZoneType.Exile).size())
                                        .append(" | fichas tuyas: ").append(me.getCardsIn(ZoneType.Battlefield).stream()
                                                .filter(Card::isToken).count());
                                sb.append("\n  Registro:");
                                for (final GameLogEntry e : game.getGameLog().getLogEntries(null)) {
                                    final String m = e.message();
                                    if (m.contains("triggered") || m.contains("Graveyard") || m.contains("Battlefield")
                                            || m.contains("ersist")) {
                                        sb.append("\n     | ").append(m);
                                    }
                                }
                                report.set(sb.toString());
                            } catch (final InterruptedException e) {
                                return;
                            } catch (final RuntimeException e) {
                                // La partida aun no esta lista: otra vuelta.
                            }
                        }
                    }, "persist-probe");
                    t.setDaemon(true);
                    t.start();
                });
        System.out.println(report.get() == null ? "  (Zodiark no llego a la mesa)" : report.get());
    }
}
