package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.game.Game;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Spike Feeder con Light of Promise.</b> Discord, 30-09-2026: con dos
 * contadores, quitar uno para ganar 2 vidas deberia disparar Light of Promise
 * ("whenever you gain life, put that many +1/+1 counters on this creature") y
 * dejarla con 3; el jugador gano la vida y no le salio ningun contador. Su
 * registro lo confirma (turno 13: +4 vidas en dos veces, y al cementerio).
 *
 * <p>Se activa "gana 2 vidas" por el MISMO camino que un clic del jugador
 * ({@code IGameController.selectAbility}) y se deja jugar al piloto hasta que
 * la pila se vacia. {@code run.cmd lifegaincheck}.
 */
public final class LifeGainCheck {

    private LifeGainCheck() {
    }

    private static final String FEEDER = "Spike Feeder|Id:100|Counters:P1P1=2|SummonSick:False";
    private static final String AURA = "Light of Promise|AttachedTo:100";
    private static final String FOREST = "Forest|Set:M21";

    public static void run() {
        System.out.println("  Mesa: Spike Feeder con 2 contadores y Light of Promise encima.");
        System.out.println("  Se quita un contador para ganar 2 vidas: tendria que quedarse con 3.");

        final AtomicBoolean activated = new AtomicBoolean(false);
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicInteger lifeBefore = new AtomicInteger(-1);
        final AtomicInteger lifeAfter = new AtomicInteger(-1);
        final AtomicInteger countersAfter = new AtomicInteger(-1);
        final StringBuilder notes = new StringBuilder();

        final TutorialLesson lesson = position();
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 40, null, false, ui -> {
                    ui.setAutoPlayHold(() -> !activated.get() && myMain(ui));
                    final Thread pilot = new Thread(() -> {
                        try {
                            for (int i = 0; i < 600 && !done.get(); i++) {
                                Thread.sleep(50L);
                                try {
                                    step(ui, activated, done, lifeBefore, lifeAfter, countersAfter, notes);
                                } catch (final RuntimeException e) {
                                    // Partida a medio montar: se reintenta. La
                                    // primera se apunta, por si no es eso.
                                    if (notes.indexOf("fallo:") < 0) {
                                        notes.append("fallo: ").append(e).append('\n');
                                    }
                                }
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            ui.setAutoPlayHold(null);
                            ui.nudgeAutoPlay();
                        }
                    }, "lifegaincheck");
                    pilot.setDaemon(true);
                    pilot.start();
                });

        System.out.println("  " + notes.toString().trim().replace("\n", "\n  "));
        final boolean ok = activated.get() && countersAfter.get() == 3;
        if (!activated.get()) {
            System.out.println("  [MAL]  no se llego a activar la habilidad: la prueba no demuestra nada");
        } else {
            System.out.printf(Locale.ROOT, "  vida %d -> %d, contadores de Spike Feeder al final: %d%n",
                    lifeBefore.get(), lifeAfter.get(), countersAfter.get());
            System.out.println(ok
                    ? "  [ok]   Light of Promise ha puesto sus 2 contadores"
                    : "  [MAL]  Light of Promise no ha puesto los contadores (esperados 3)");
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", ok ? 1 : 0, ok ? 0 : 1);
        if (!ok) {
            throw new IllegalStateException("Spike Feeder y Light of Promise: no cuadra");
        }
    }

    private static void step(final NeoMatchUI ui, final AtomicBoolean activated, final AtomicBoolean done,
            final AtomicInteger lifeBefore, final AtomicInteger lifeAfter,
            final AtomicInteger countersAfter, final StringBuilder notes) {
        if (ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        final Card feeder = find(me);
        if (!activated.get()) {
            if (!myMain(ui) || !(ui.getGameController() instanceof PlayerControllerHuman gc)
                    || !(gc.getInputQueue().getInput() instanceof InputPassPriority) || feeder == null) {
                return;
            }
            notes.append("antes: ").append(feeder.getCounters(CounterEnumType.P1P1))
                    .append(" contadores, encantada por ").append(feeder.getEnchantedBy()).append('\n');
            SpellAbility gain = null;
            for (final SpellAbility sa : feeder.getSpellAbilities()) {
                if (sa.getApi() == ApiType.GainLife) {
                    gain = sa;
                }
            }
            if (gain == null) {
                notes.append("Spike Feeder no tiene la habilidad de ganar vida\n");
                done.set(true);
                return;
            }
            lifeBefore.set(me.getLife());
            activated.set(true);
            // Lo que hace el clic del jugador una vez elegida la habilidad del
            // menu: InputPassPriority.selectAbility (el selectAbility del
            // controlador solo vale tras ofrecer la lista, spellViewCache).
            final SpellAbility chosen = gain;
            final InputPassPriority input = (InputPassPriority) gc.getInputQueue().getInput();
            // En un hilo aparte: la pregunta del coste ("quitar un contador?")
            // lo bloquea hasta que el piloto contesta.
            final Thread click = new Thread(() -> {
                try {
                    notes.append("  selectAbility -> ").append(input.selectAbility(chosen)).append('\n');
                } catch (final RuntimeException e) {
                    notes.append("  selectAbility fallo: ").append(e).append('\n');
                }
            }, "lifegaincheck-clic");
            click.setDaemon(true);
            click.start();
            return;
        }
        // Tras activar: apuntar cada cambio, y esperar a que se gane la vida y
        // la pila quede vacia.
        final String now = "vida " + me.getLife() + " | pila " + game.getStack().size()
                + (game.getStack().isEmpty() ? "" : " (" + game.getStack().peekAbility() + ")")
                + " | contadores " + (feeder == null ? "-" : feeder.getCounters(CounterEnumType.P1P1))
                + " | fase " + game.getPhaseHandler().getPhase()
                + " | espera " + (ui.getGameController() instanceof PlayerControllerHuman g
                        ? g.getInputQueue().getInput() : null);
        if (!now.equals(lastSeen[0])) {
            lastSeen[0] = now;
            notes.append("  ").append(now).append('\n');
        }
        // La pila vacia UN SEGUNDO seguido: esto lee el motor desde otro hilo,
        // y entre que sale la habilidad y entra el disparo de Light of Promise
        // hay un instante con la pila vacia (la bateria lo pillo el 30-09).
        // Vale en cuanto hay 3 con la pila vacia, o cuando la partida ha pasado
        // de fase: una fase solo avanza con la pila resuelta entera, asi que el
        // disparo ya ha tenido su ocasion. Esperar "un segundo quieta" no
        // valia: en automatico la partida corre y la pila no se esta quieta.
        final boolean settled = me.getLife() > lifeBefore.get() && game.getStack().isEmpty()
                && ((feeder != null && feeder.getCounters(CounterEnumType.P1P1) == 3)
                    || game.getPhaseHandler().getPhase() != PhaseType.MAIN1
                    || feeder == null);
        if (settled) {
            lifeAfter.set(me.getLife());
            countersAfter.set(feeder == null ? 0 : feeder.getCounters(CounterEnumType.P1P1));
            notes.append("despues: ").append(feeder == null ? "Spike Feeder ya no esta en la mesa"
                    : feeder.getCounters(CounterEnumType.P1P1) + " contadores").append('\n');
            done.set(true);
        }
    }

    private static final String[] lastSeen = {null};

    private static Card find(final Player me) {
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if ("Spike Feeder".equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    private static boolean myMain(final NeoMatchUI ui) {
        if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return false;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        return me != null && game.getPhaseHandler().isPlayerTurn(me)
                && game.getPhaseHandler().getPhase() == PhaseType.MAIN1 && game.getStack().isEmpty();
    }

    private static Player human(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + FEEDER + ";" + AURA + ";" + FOREST + ";" + FOREST,
                "humanhand=",
                "humanlibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("lifegain", state, List.of());
    }
}
