package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.combat.Combat;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Comprueba, sin ventana, que el piloto automatico sabe contestar a un
 * <b>bloqueo obligado</b>. Es el gemelo de {@link AttackCheck}.
 *
 * <p>El 15-09-2026 {@code comprobar-todo.py} corto {@code questcheck} a los
 * 300 s. Ultima linea: <i>"Spirit Token (124) must block an attacker, but has
 * not been assigned to block any."</i> El piloto {@code AUTO_PLAY} pulsaba OK
 * sin bloqueadores, {@code InputBlock.onOk} lo rechazaba
 * ({@code CombatUtil.validateBlocks}) y se limitaba a ensenyar el aviso: no
 * vuelve a preguntar, asi que no llegaba otra pulsacion y el hilo de la
 * partida se quedaba en {@code InputSyncronizedBase.showAndWait} para
 * siempre. Al contrario que el ataque, aqui no gasta CPU: se queda parado.
 *
 * <p>Dos mesas, una por cada camino de {@code validateBlocks} que exige
 * bloquear:
 * <ol>
 *   <li><b>Lure</b> sobre el Juggernaut de la IA: "todas las criaturas que
 *       puedan bloquearla lo hacen" — el mismo mensaje que colgo
 *       {@code questcheck};</li>
 *   <li><b>Watchdog</b> en tu lado: "bloquea cada combate si puede".</li>
 * </ol>
 *
 * <p>En las dos se exige que tu criatura <b>ha bloqueado</b> (si no, el verde
 * no demostraria que se paso por el bloqueo obligado) y que la partida
 * <b>llega al final</b> en vez de cortarla el tope de tiempo, que es la firma
 * del cuelgue. Con {@code -Dneo.autoplay.blockGuard=false} se apaga el arreglo
 * y esto tiene que salir en rojo. Se ejecuta con {@code run.cmd blockcheck}.
 */
public final class BlockCheck {

    private BlockCheck() {
    }

    private static final String JUGGERNAUT = "Juggernaut|Set:M15";
    private static final String LURE = "Lure|Set:8ED";
    private static final String BEARS = "Grizzly Bears|Set:8ED";
    private static final String WATCHDOG = "Watchdog|Set:TMP";
    private static final String FOREST = "Forest|Set:M21";

    /** Tope por partida: la IA necesita unos cinco ataques para ganar. */
    private static final int SECONDS = 120;

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        System.out.println("  Mesa 1: Juggernaut de la IA con Lure, contra tus Grizzly Bears.");
        scenario("Grizzly Bears", position(
                JUGGERNAUT + "|Id:1;" + LURE + "|AttachedTo:1;" + FOREST,
                BEARS + ";" + FOREST));

        System.out.println();
        System.out.println("  Mesa 2: Juggernaut de la IA contra tu Watchdog (bloquea cada combate si puede).");
        scenario("Watchdog", position(
                JUGGERNAUT + ";" + FOREST,
                WATCHDOG + ";" + FOREST));

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del bloqueo obligado han fallado");
        }
    }

    private static void scenario(final String blockerName, final TutorialLesson lesson) {
        final TutorialState state = new TutorialState(lesson.getState());

        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean alive = new AtomicBoolean(true);
        final AtomicBoolean sawBlocker = new AtomicBoolean(false);
        final AtomicBoolean sawBlock = new AtomicBoolean(false);
        final Thread poller = new Thread(() -> {
            while (alive.get()) {
                try {
                    watch(gui[0], blockerName, sawBlocker, sawBlock);
                    Thread.sleep(25L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // Todavia no hay partida, o el combate cambia mientras se mira.
                }
            }
        }, "blockcheck-poll");
        poller.setDaemon(true);

        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, SECONDS, null, false, ui -> {
                    gui[0] = ui;
                    poller.start();
                });
        alive.set(false);

        System.out.printf(Locale.ROOT, "  Turnos jugados: %d%n", result.turns);
        check(sawBlocker.get(),
                blockerName + " llego a la mesa",
                "la posicion no puso " + blockerName + " en la mesa: la prueba no prueba nada");
        check(sawBlock.get(),
                blockerName + " bloqueo: el piloto declaro el bloqueo obligado",
                blockerName + " no bloqueo nunca: el bloqueo obligado no se declaro");
        check(result.completed,
                "la partida llego al final (" + result.turns + " turnos)",
                "la partida no termino: se corto por tiempo en el turno " + result.turns
                        + ". Es la firma del OK rechazado en la declaracion de bloqueadores");
    }

    /**
     * Turno 3, fase principal de la IA: lo siguiente es su combate, y su
     * Juggernaut (que ataca cada combate si puede) no esta mareado. Tu criatura
     * muere en el primer bloqueo; despues nadie bloquea y la IA gana sola.
     */
    private static TutorialLesson position(final String aiBattlefield, final String humanBattlefield) {
        final String library = String.join(";", java.util.Collections.nCopies(12, FOREST));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=ai",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + humanBattlefield,
                "humanhand=",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=" + aiBattlefield,
                "aihand=",
                "ailibrary=" + library,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("block", state, List.of());
    }

    private static void watch(final NeoMatchUI ui, final String blockerName,
                              final AtomicBoolean sawBlocker, final AtomicBoolean sawBlock) {
        if (ui == null || ui.getGameView() == null) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        if (game == null) {
            return;
        }
        for (final Player p : game.getRegisteredPlayers()) {
            if (p.isAI()) {
                continue;
            }
            final Combat combat = game.getCombat();
            for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
                if (blockerName.equals(c.getName())) {
                    sawBlocker.set(true);
                    if (combat != null && combat.getAllBlockers().contains(c)) {
                        sawBlock.set(true);
                    }
                }
            }
            // Respaldo por si el sondeo no cae dentro del combate: la unica
            // forma de que acabe en el cementerio es bloquear al Juggernaut.
            for (final Card c : p.getCardsIn(ZoneType.Graveyard)) {
                if (blockerName.equals(c.getName())) {
                    sawBlock.set(true);
                }
            }
        }
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
