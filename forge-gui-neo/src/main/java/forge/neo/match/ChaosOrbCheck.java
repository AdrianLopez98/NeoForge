package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.StaticData;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * {@code run.cmd chaosorbcheck}: Chaos Orb con la errata de Old School
 * ({@link ChaosOrbErrata}), en una partida de verdad.
 *
 * <ol>
 *   <li>Sin partida: la errata solo con Old School 93/94; cualquier otro
 *       formato (o ninguno) vuelve a la del Oracle.</li>
 *   <li>Dos partidas, con la errata y sin ella: la IA tira <b>25 Orbes</b> sobre
 *       40 criaturas tuyas, uno a uno, y se cuenta cuantos permanentes destruye
 *       cada tiro. Con la errata, <b>nunca mas de uno</b>; con la del Oracle,
 *       alguna vez dos o mas — que es lo que demuestra que la prueba ve la
 *       diferencia y no pasa por casualidad.</li>
 * </ol>
 */
public final class ChaosOrbCheck {

    private ChaosOrbCheck() {
    }

    /** Quien tira, cuantos Orbes y contra cuantas criaturas. */
    private record Setup(boolean humanThrows, int orbs, int targets) {
    }

    /** La IA tira 25 Orbes sobre 40 criaturas tuyas. */
    private static final Setup AI_THROWS = new Setup(false, 25, 40);
    /** Tu tiras 8 sobre 20 suyas: el dialogo de elegir, contestado por el piloto. */
    private static final Setup YOU_THROW = new Setup(true, 8, 20);

    private static int good;
    private static int bad;

    public static void run() {
        good = 0;
        bad = 0;

        // 1. Solo en Old School.
        ChaosOrbErrata.forFormat(NeoFormat.OLD_SCHOOL);
        final boolean onOld = ChaosOrbErrata.isApplied() && oracle().contains("destroy one");
        ChaosOrbErrata.forFormat(NeoFormat.STANDARD);
        final boolean offStd = !ChaosOrbErrata.isApplied() && oracle().contains("destroy all");
        ChaosOrbErrata.forFormat(NeoFormat.OLD_SCHOOL);
        ChaosOrbErrata.forFormat(null);
        final boolean offNull = !ChaosOrbErrata.isApplied() && oracle().contains("destroy all");
        check(onOld, "Old School 93/94: Chaos Orb con la errata (destroy one)");
        check(offStd, "Estandar: vuelve la del Oracle (destroy all)");
        check(offNull, "una partida sin formato (puzzle, red, tutorial): la del Oracle");

        // 2. En la mesa.
        final int[] errata = throwOrbs(true, AI_THROWS);
        final int[] oracle = throwOrbs(false, AI_THROWS);
        final int[] yours = throwOrbs(true, YOU_THROW);
        System.out.printf(Locale.ROOT, "  [info] con errata: %d tiros que destruyen algo, maximo %d por tiro%n",
                errata[0], errata[1]);
        System.out.printf(Locale.ROOT, "  [info] del Oracle: %d tiros que destruyen algo, maximo %d por tiro%n",
                oracle[0], oracle[1]);
        check(errata[0] >= 3 && errata[1] == 1,
                "con la errata, cada Orbe destruye UN permanente como mucho (" + errata[0] + " tiros que aciertan)");
        check(oracle[1] >= 2,
                "sin ella, alguno destruye dos o mas: la prueba distingue de verdad");
        check(errata[2] == 0, "con la errata, la IA no se rompe nada suyo teniendo tuyo tocado");
        System.out.printf(Locale.ROOT, "  [info] tirando tu: %d tiros que destruyen algo, maximo %d por tiro%n",
                yours[0], yours[1]);
        check(yours[0] >= 1 && yours[1] == 1 && yours[3] == 1,
                "tirando tu (te pregunta cual): uno como mucho, y la partida no se queda colgada");

        System.clearProperty("neo.chaosorb.force");
        ChaosOrbErrata.forFormat(null);
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", good, bad);
        if (bad > 0) {
            throw new IllegalStateException("Chaos Orb de Old School: no cuadra");
        }
    }

    private static String oracle() {
        return String.valueOf(StaticData.instance().getCommonCards().getRules(ChaosOrbErrata.NAME, false).getOracleText())
                .toLowerCase(Locale.ROOT);
    }

    /**
     * Tiros que destruyen algo, el maximo destruido en un tiro, cuantos
     * permanentes suyos rompio quien tira, y 1 si se tiraron todos (sin colgarse).
     */
    private static int[] throwOrbs(final boolean withErrata, final Setup setup) {
        System.setProperty("neo.chaosorb.force", String.valueOf(withErrata));
        final int[] out = new int[4];
        final AtomicBoolean done = new AtomicBoolean(false);
        final StringBuilder notes = new StringBuilder();
        final TutorialLesson lesson = position(setup);
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 90, null, setup.humanThrows(), ui -> {
                    ui.setAutoPlayHold(() -> !done.get());
                    final Thread pilot = new Thread(() -> {
                        try {
                            for (int i = 0; i < 600 && !done.get(); i++) {
                                Thread.sleep(50L);
                                try {
                                    if (ready(ui, setup)) {
                                        try {
                                            if (setup.humanThrows()) {
                                                flipByClick(ui, ui.getGameView().getGame(), out, notes);
                                            } else {
                                                flipAll(ui.getGameView().getGame(), setup, out, notes);
                                            }
                                        } finally {
                                            done.set(true);
                                        }
                                    }
                                } catch (final RuntimeException e) {
                                    notes.append("fallo: ").append(e).append('\n');
                                    done.set(true);
                                }
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.set(true);
                            ui.setAutoPlayHold(null);
                            ui.nudgeAutoPlay();
                        }
                    }, "chaosorbcheck");
                    pilot.setDaemon(true);
                    pilot.start();
                });
        if (notes.length() > 0) {
            System.out.println("  " + notes.toString().trim().replace("\n", "\n  "));
        }
        return out;
    }

    /** Cada Orbe de quien tira, resuelto por el motor (sin pagar), contando lo que destruye. */
    private static void flipAll(final Game game, final Setup setup, final int[] out, final StringBuilder notes) {
        final Player me = human(game);
        final Player ai = setup.humanThrows() ? me : me.getSingleOpponent();
        final List<Card> orbs = new ArrayList<>();
        for (final Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (ChaosOrbErrata.NAME.equals(c.getName())) {
                orbs.add(c);
            }
        }
        if (orbs.size() != setup.orbs()) {
            final java.util.Map<String, Integer> seen = new java.util.TreeMap<>();
            for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
                seen.merge(c.getController().getName() + ":" + c.getName(), 1, Integer::sum);
            }
            notes.append("[info] turno ").append(game.getPhaseHandler().getTurn()).append(", Orbes de la IA: ")
                    .append(orbs.size()).append("; en la mesa: ").append(seen).append('\n');
        }
        for (final Card orb : orbs) {
            final Set<Integer> before = others(game);
            final Set<Integer> mineBefore = ids(ai);
            for (final SpellAbility sa : orb.getSpellAbilities()) {
                if (sa.getApi() == ApiType.FlipOntoBattlefield) {
                    sa.setActivatingPlayer(ai);
                    AbilityUtils.resolve(sa);
                    game.getAction().checkStateEffects(true);
                    break;
                }
            }
            final Set<Integer> gone = new HashSet<>(before);
            gone.removeAll(others(game));
            if (!gone.isEmpty()) {
                out[0]++;
                out[1] = Math.max(out[1], gone.size());
            }
            final Set<Integer> mineGone = new HashSet<>(mineBefore);
            mineGone.removeAll(ids(ai));
            out[2] += mineGone.size();
        }
        out[3] = 1;
    }

    /**
     * Tus Orbes, como los tiraria el jugador: clic en la carta (paga el Mox
     * solo), pasar prioridad y que el motor lo resuelva en SU hilo, con sus
     * preguntas (donde apuntas, cual destruye) contestadas por el piloto. Resolverlo
     * a mano desde aqui no vale para el humano: el motor espera la respuesta
     * en el hilo de la partida (pasaba igual con la carta del Oracle).
     */
    private static void flipByClick(final NeoMatchUI ui, final Game game, final int[] out,
                                    final StringBuilder notes) {
        final Player me = human(game);
        final List<Card> orbs = new ArrayList<>();
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if (ChaosOrbErrata.NAME.equals(c.getName())) {
                orbs.add(c);
            }
        }
        for (final Card orb : orbs) {
            final Set<Integer> before = others(game);
            final Set<Integer> mineBefore = ids(me);
            final forge.gamemodes.match.input.Input click = waitInput(ui, 5000);
            if (click == null) {
                notes.append("[info] sin prioridad para tirar el Orbe\n");
                return;
            }
            click.selectCard(orb, null, null);
            // El {1}: como el jugador, con "Auto" en el pago (un Mox).
            final long until = System.currentTimeMillis() + 5000;
            while (game.getStack().isEmpty() && orb.isInPlay() && System.currentTimeMillis() < until) {
                if (ui.getGameController() instanceof forge.player.PlayerControllerHuman gc
                        && gc.getInputQueue().getInput() instanceof forge.gamemodes.match.input.InputPayMana pay) {
                    pay.selectButtonOK();
                }
                sleep(60);
            }
            // Y luego, como un jugador: pasar prioridad para que se resuelva, y
            // contestar lo que pregunte (donde apuntas, cual destruye) clicando
            // la primera carta que acepte y OK.
            final java.util.Set<String> asked = new java.util.LinkedHashSet<>();
            final long resolved = System.currentTimeMillis() + 20000;
            while ((orb.isInPlay() || !game.getStack().isEmpty()) && System.currentTimeMillis() < resolved) {
                final Object in = ui.getGameController() instanceof forge.player.PlayerControllerHuman gc
                        ? gc.getInputQueue().getInput() : null;
                if (in instanceof forge.gamemodes.match.input.InputPassPriority pp && !game.getStack().isEmpty()) {
                    pp.selectButtonOK();
                } else if (in instanceof forge.gamemodes.match.input.InputSelectManyBase<?> pick) {
                    asked.add(pick.getClass().getSimpleName());
                    final List<Card> candidates = new ArrayList<>(me.getSingleOpponent().getCardsIn(ZoneType.Battlefield));
                    candidates.addAll(me.getCardsIn(ZoneType.Battlefield));
                    for (final Card c : candidates) {
                        if (pick.selectCard(c, null, null)) {
                            break;
                        }
                    }
                    pick.selectButtonOK();
                }
                sleep(80);
            }
            if (!asked.isEmpty() && out[3] == 0 && notes.indexOf("pregunta") < 0) {
                notes.append("[info] te pregunta: ").append(asked).append("\n");
            }
            if (orb.isInPlay()) {
                final Object now = ui.getGameController() instanceof forge.player.PlayerControllerHuman gc
                        ? gc.getInputQueue().getInput() : null;
                notes.append("[info] el Orbe no se ha resuelto (se queda colgado): pila=")
                        .append(game.getStack().size()).append(", girado=").append(orb.isTapped())
                        .append(", espera=").append(now == null ? "nada" : now.getClass().getSimpleName())
                        .append(", fase=").append(game.getPhaseHandler().getPhase())
                        .append(", prioridad=").append(game.getPhaseHandler().getPriorityPlayer())
                        .append("\n");
                return;
            }
            final Set<Integer> gone = new HashSet<>(before);
            gone.removeAll(others(game));
            if (!gone.isEmpty()) {
                out[0]++;
                out[1] = Math.max(out[1], gone.size());
            }
            final Set<Integer> mineGone = new HashSet<>(mineBefore);
            mineGone.removeAll(ids(me));
            out[2] += mineGone.size();
        }
        out[3] = 1;
    }

    /** La entrada de "tienes prioridad" del jugador, cuando la haya. */
    private static forge.gamemodes.match.input.Input waitInput(final NeoMatchUI ui, final long ms) {
        final long until = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < until) {
            if (ui.getGameController() instanceof forge.player.PlayerControllerHuman gc
                    && gc.getInputQueue().getInput() instanceof forge.gamemodes.match.input.InputPassPriority in) {
                return in;
            }
            sleep(30);
        }
        return null;
    }

    private static void sleep(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Los permanentes de todos que no son Orbes. */
    private static Set<Integer> others(final Game game) {
        final Set<Integer> out = new HashSet<>();
        for (final Card c : game.getCardsIn(ZoneType.Battlefield)) {
            if (!ChaosOrbErrata.NAME.equals(c.getName())) {
                out.add(c.getId());
            }
        }
        return out;
    }

    private static Set<Integer> ids(final Player p) {
        final Set<Integer> out = new HashSet<>();
        for (final Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (!ChaosOrbErrata.NAME.equals(c.getName())) {
                out.add(c.getId());
            }
        }
        return out;
    }

    private static boolean ready(final NeoMatchUI ui, final Setup setup) {
        if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null) {
            return false;
        }
        final Game game = ui.getGameView().getGame();
        final Player me = human(game);
        if (me == null || game.getPhaseHandler().getPhase() != PhaseType.MAIN1 || !game.getStack().isEmpty()) {
            return false;
        }
        // La mesa se coloca DESPUES de empezar la partida, y a trozos: hasta que
        // no este entera (los Orbes, las criaturas y los 2 gigantes) no se tira nada.
        final Player thrower = setup.humanThrows() ? me : me.getSingleOpponent();
        final Player target = setup.humanThrows() ? me.getSingleOpponent() : me;
        int orbs = 0;
        for (final Card c : thrower.getCardsIn(ZoneType.Battlefield)) {
            if (ChaosOrbErrata.NAME.equals(c.getName())) {
                orbs++;
            }
        }
        final int extra = setup.humanThrows() ? setup.orbs() : 2;
        return orbs == setup.orbs() && target.getCardsIn(ZoneType.Battlefield).size() == setup.targets()
                && thrower.getCardsIn(ZoneType.Battlefield).size() == setup.orbs() + extra;
    }

    private static Player human(final Game game) {
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static TutorialLesson position(final Setup setup) {
        final String orbs = String.join(";", java.util.Collections.nCopies(setup.orbs(), ChaosOrbErrata.NAME))
                + (setup.humanThrows()
                        ? ";" + String.join(";", java.util.Collections.nCopies(setup.orbs(), "Mox Pearl"))
                        : ";Hill Giant;Hill Giant");
        final String bears = String.join(";", java.util.Collections.nCopies(setup.targets(), "Grizzly Bears"));
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + (setup.humanThrows() ? orbs : bears),
                "humanhand=",
                "humanlibrary=Grizzly Bears;Grizzly Bears;Grizzly Bears",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=" + (setup.humanThrows() ? bears : orbs),
                "aihand=",
                "ailibrary=Grizzly Bears;Grizzly Bears;Grizzly Bears",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("chaosorb", state, List.of());
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            good++;
        } else {
            bad++;
        }
        System.out.println((ok ? "  [ok]   " : "  [MAL]  ") + what);
    }
}
