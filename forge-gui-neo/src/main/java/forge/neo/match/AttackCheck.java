package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Comprueba, sin ventana, que el piloto automatico sabe contestar a un
 * <b>ataque obligado</b>.
 *
 * <p>El 14-09-2026 {@code questcheck} se paso 90 minutos al 100% de CPU. El
 * piloto {@code AUTO_PLAY} pulsaba OK con el ataque vacio, el motor lo
 * rechazaba ({@code CombatUtil.validateAttackers}: hay una criatura que
 * "ataca cada combate si puede") y volvia a preguntar, para siempre. En
 * {@code questcheck} dependia de que el mazo al azar trajera una criatura
 * asi; aqui la mesa se monta a proposito para que pase <b>siempre</b>.
 *
 * <p>Se exigen las dos cosas, y hacen falta las dos:
 * <ol>
 *   <li>que Juggernaut <b>ha atacado</b> (el rival pierde vida) — si no, la
 *       partida podria terminar por otra via sin haber pasado por el ataque
 *       obligado, y el verde no demostraria nada;</li>
 *   <li>y que la partida <b>llega al final</b> en vez de cortarla el tope de
 *       tiempo, que es la firma del bucle.</li>
 * </ol>
 *
 * <p>Con {@code -Dneo.autoplay.attackGuard=false} se apaga el arreglo y esto
 * tiene que salir en rojo. Se ejecuta con {@code run.cmd attackcheck}.
 */
public final class AttackCheck {

    private AttackCheck() {
    }

    private static final String JUGGERNAUT = "Juggernaut|Set:M15";
    private static final String FOREST = "Forest|Set:M21";

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());

        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean alive = new AtomicBoolean(true);
        final AtomicBoolean sawJuggernaut = new AtomicBoolean(false);
        final AtomicInteger aiLifeMin = new AtomicInteger(Integer.MAX_VALUE);
        final Thread poller = new Thread(() -> {
            while (alive.get()) {
                try {
                    watch(gui[0], sawJuggernaut, aiLifeMin);
                    Thread.sleep(50L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // Todavia no hay partida, o la mesa se esta montando.
                }
            }
        }, "attackcheck-poll");
        poller.setDaemon(true);

        System.out.println("  Mesa: tu Juggernaut (ataca cada combate si puede) contra una IA sin criaturas.");
        final NeoGame.Result result = NeoGame.playTutorial(lesson, state,
                NeoMatchUI.Mode.AUTO_PLAY, 60, null, false, ui -> {
                    gui[0] = ui;
                    poller.start();
                });
        alive.set(false);

        final int minLife = aiLifeMin.get();
        System.out.printf(Locale.ROOT, "  Turnos jugados: %d | vida minima de la IA: %s%n",
                result.turns, minLife == Integer.MAX_VALUE ? "?" : String.valueOf(minLife));

        check(sawJuggernaut.get(),
                "Juggernaut llego a la mesa",
                "la posicion no puso a Juggernaut en la mesa: la prueba no prueba nada");
        check(minLife < 20,
                "Juggernaut ataco: el piloto declaro el ataque obligado",
                "la IA no perdio ni una vida: el ataque obligado no se declaro nunca");
        check(result.completed,
                "la partida llego al final (" + result.turns + " turnos)",
                "la partida no termino: se corto por tiempo en el turno " + result.turns
                        + ". Es la firma del bucle de OK contra el ataque rechazado");

        guard();

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del ataque obligado han fallado");
        }
    }

    /**
     * "No has declarado ningun atacante: ¿seguro?" ({@link AttackGuard},
     * Discord, 04-11-2026). Con un Oso que puede atacar (no esta obligado), en
     * el momento exacto en que el motor pide atacantes: sin atacantes se
     * avisa; con el Oso atacando, no; y el ajuste manda (apagado de fabrica).
     * Despues se deja todo como estaba; la partida se corta por tiempo, que
     * aqui da igual.
     */
    private static void guard() {
        final List<String> base = new java.util.ArrayList<>(position().getState());
        base.replaceAll(l -> l.startsWith("humanbattlefield=")
                ? "humanbattlefield=Grizzly Bears|Set:8ED;" + FOREST : l);
        final TutorialLesson lesson = new TutorialLesson("attack-guard", base, List.of());
        final TutorialState state = new TutorialState(lesson.getState());
        final java.util.concurrent.atomic.AtomicReference<Boolean> empty = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Boolean> attacking = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Boolean> setting = new java.util.concurrent.atomic.AtomicReference<>();
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 15, null, false, ui -> {
            ui.setAutoPlayHold(() -> {
                if (empty.get() != null || !(ui.getGameController() instanceof forge.player.PlayerControllerHuman gc)
                        || !(gc.getInputQueue().getInput() instanceof forge.gamemodes.match.input.InputAttack)) {
                    return false;
                }
                final forge.game.combat.Combat combat = gc.getGame().getCombat();
                final Card bears = gc.getPlayer().getCardsIn(ZoneType.Battlefield).stream()
                        .filter(c -> "Grizzly Bears".equals(c.getName())).findFirst().orElse(null);
                empty.set(AttackGuard.wouldSkipAttack(gc));
                setting.set(AttackGuard.shouldAsk(gc) == (forge.neo.NeoSettings.confirmNoAttack() && empty.get()));
                if (bears != null && combat != null && !combat.getDefenders().isEmpty()) {
                    combat.addAttacker(bears, combat.getDefenders().get(0));
                    attacking.set(AttackGuard.wouldSkipAttack(gc));
                    combat.removeFromCombat(bears);
                }
                return false;
            });
        });
        check(Boolean.TRUE.equals(empty.get()),
                "aviso de no atacar: sin atacantes y con el oso libre, se avisa",
                empty.get() == null ? "no se llego a declarar atacantes: la prueba no prueba nada"
                        : "sin atacantes y con el oso libre NO se avisa");
        check(Boolean.FALSE.equals(attacking.get()),
                "aviso de no atacar: con el oso atacando, no se avisa",
                "con el oso atacando se avisa igual (" + attacking.get() + ")");
        check(Boolean.TRUE.equals(setting.get()),
                "aviso de no atacar: el ajuste manda (ahora " + (forge.neo.NeoSettings.confirmNoAttack() ? "encendido" : "apagado, como de fabrica") + ")",
                "el ajuste no manda: se preguntaria sin el, o no con el");
    }

    /**
     * Turno 3, tu fase principal: lo siguiente es el combate, y Juggernaut no
     * esta mareado (en esta notacion solo lo esta lo que lleva
     * {@code |SummonSick}). La IA no tiene criaturas, asi que nadie bloquea y
     * cuatro ataques de 5 la matan: la partida termina sola.
     */
    private static TutorialLesson position() {
        final String library = String.join(";", java.util.Collections.nCopies(12, FOREST));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + JUGGERNAUT + ";" + FOREST,
                "humanhand=",
                "humanlibrary=" + library,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=" + FOREST,
                "aihand=",
                "ailibrary=" + library,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("attack", state, List.of());
    }

    private static void watch(final NeoMatchUI ui, final AtomicBoolean sawJuggernaut,
                              final AtomicInteger aiLifeMin) {
        if (ui == null || ui.getGameView() == null) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        if (game == null) {
            return;
        }
        for (final Player p : game.getRegisteredPlayers()) {
            if (p.isAI()) {
                aiLifeMin.accumulateAndGet(p.getLife(), Math::min);
            } else {
                for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
                    if ("Juggernaut".equals(c.getName())) {
                        sawJuggernaut.set(true);
                    }
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
