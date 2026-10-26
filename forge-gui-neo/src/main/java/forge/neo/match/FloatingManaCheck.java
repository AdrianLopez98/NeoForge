package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostParser;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * Con mana flotante, el pago automatico deja elegir ({@link FloatingManaChoice}),
 * sin ventana. Cada mesa: tu fase principal, una reserva y unas tierras; con la
 * partida esperando al jugador se pregunta por un coste:
 *
 * <ul>
 *   <li>La del informe: {C}{C} flotando y una Isla, coste {2}: se para.</li>
 *   <li>{C}{C} flotando y nada en la mesa: no hay nada que elegir.</li>
 *   <li>Nada flotando y una Isla: se paga solo, como siempre.</li>
 *   <li>{R} flotando, una Isla y un coste {U}{U}: lo flotante no sirve.</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd floatingcheck}.
 */
public final class FloatingManaCheck {

    private FloatingManaCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String ISLAND = "Island|Set:M21";
    private static final int SECONDS = 20;

    public static void run() {
        passed = 0;
        failed = 0;
        Boolean r = probe("informe: CC flotando + Isla, {2}", "C C", ISLAND, "2");
        check(Boolean.TRUE.equals(r), "con mana flotante y una Isla, el pago automatico se para (" + r + ")");
        r = probe("CC flotando, mesa vacia, {2}", "C C", "", "2");
        check(Boolean.FALSE.equals(r), "sin otra fuente no hay nada que elegir (" + r + ")");
        r = probe("nada flotando + Isla, {2}", "", ISLAND, "2");
        check(Boolean.FALSE.equals(r), "sin mana flotante se paga solo (" + r + ")");
        r = probe("R flotando + Isla, {U}{U}", "R", ISLAND, "U U");
        check(Boolean.FALSE.equals(r), "si lo flotante no sirve para el coste, se paga solo (" + r + ")");
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del mana flotante han fallado");
        }
    }

    /** Monta la mesa y pregunta, con la partida parada en tu fase principal. Null si no se llego a mirar. */
    private static Boolean probe(final String title, final String pool, final String battlefield,
                                 final String cost) {
        System.out.println("  Mesa: " + title);
        final AtomicReference<Boolean> out = new AtomicReference<>();
        final TutorialLesson lesson = new TutorialLesson("floating", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + battlefield,
                // Algo que lanzar: sin acciones, el pase automatico se lleva la
                // fase, y con ella la reserva, antes de mirar.
                "humanhand=Ornithopter|Set:M15",
                "humanmanapool=" + pool,
                "humanlibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + ISLAND, "aihand=",
                "ailibrary=" + (ISLAND + ";").repeat(6) + ISLAND,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.HUMAN, SECONDS, null, false, gui -> {
                    final Thread t = new Thread(() -> {
                        final long until = System.currentTimeMillis() + (SECONDS - 5) * 1000L;
                        while (System.currentTimeMillis() < until && out.get() == null) {
                            try {
                                Thread.sleep(300);
                                if (gui.getGameController() == null) {
                                    continue;
                                }
                                final Player p = ((PlayerControllerHuman) NeoMatchUI.seated(
                                        gui.getGameController())).getPlayer();
                                // El turno del motor: el GameView de la interfaz llega por lotes.
                                if (p.getGame().getPhaseHandler().getTurn() < 3
                                        || !p.getGame().getPhaseHandler().getPhase().isMain()) {
                                    continue;
                                }
                                // La reserva se pone despues de la mesa: si aun no esta, otra vuelta.
                                if (!pool.isEmpty() && p.getManaPool().isEmpty()) {
                                    continue;
                                }
                                final ManaCostBeingPaid c =
                                        new ManaCostBeingPaid(new ManaCost(new ManaCostParser(cost)));
                                out.set(FloatingManaChoice.applies(c, p));
                            } catch (final InterruptedException e) {
                                return;
                            } catch (final RuntimeException e) {
                                // La partida aun no esta lista: otra vuelta.
                            }
                        }
                    }, "floating-probe");
                    t.setDaemon(true);
                    t.start();
                });
        if (out.get() == null) {
            System.out.println("  (no se llego a mirar la mesa)");
        }
        return out.get();
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
