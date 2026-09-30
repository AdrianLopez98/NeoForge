package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.combat.CombatView;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputBlock;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Bloquear con varias fichas de una pila</b> — {@code run.cmd stackblockcheck}.
 *
 * <p>Discord, 30-09-2026 (Android): 11 fichas de Goblin, dos 2/2 atacando, y
 * no habia forma de bloquear cada uno con dos. Mientras se declaran, el motor
 * pone los bloqueadores solo en la lista "planned" del combate ({@code
 * GameView.updateCombat}: el ataque aun no esta "bloqueado"), asi que
 * {@code CombatView.isBlocking} es falso. Android apilaba mirando ESE, la ficha
 * asignada seguia en su pila y el siguiente toque mandaba LA MISMA, que el
 * motor quitaba del bloqueo; y sin baldosa propia, tampoco tenia flecha. El
 * escritorio mira {@code CardView.isBlocking}, que SI es verdadero al asignar,
 * y por eso aqui no pasaba (Android, decision 185).
 *
 * <p>Aqui, en una partida de verdad y por el camino del clic
 * ({@code selectCard}): los dos datos en los que se apoya cada lado, y que,
 * tocando cada vez "la pila" — la primera ficha que siga en ella — se
 * bloquea un atacante con dos y el otro con otros dos.
 */
public final class StackBlockCheck {

    private StackBlockCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        System.out.println("  Mesa: el rival ataca con dos 2/2; tu tienes 11 fichas de Goblin 1/1.");

        final AtomicBoolean done = new AtomicBoolean(false);
        final StringBuilder notes = new StringBuilder();
        final boolean[] plannedSeen = {false};
        final boolean[] blockingFalse = {false};
        final int[] blockers = {-1, -1};

        final TutorialLesson lesson = position();
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 40, null, false, ui -> {
                    ui.setAutoPlayHold(() -> !done.get() && blocking(ui));
                    final Thread pilot = new Thread(() -> {
                        try {
                            for (int i = 0; i < 600 && !done.get(); i++) {
                                Thread.sleep(50L);
                                try {
                                    if (blocking(ui)) {
                                        act(ui, notes, plannedSeen, blockingFalse, blockers);
                                        done.set(true);
                                    }
                                } catch (final RuntimeException e) {
                                    if (notes.indexOf("fallo:") < 0) {
                                        notes.append("fallo: ").append(e).append('\n');
                                    }
                                }
                            }
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.set(true);
                            ui.setAutoPlayHold(null);
                            ui.nudgeAutoPlay();
                        }
                    }, "stackblockcheck");
                    pilot.setDaemon(true);
                    pilot.start();
                });

        if (notes.length() > 0) {
            System.out.println("  " + notes.toString().trim().replace("\n", "\n  "));
        }
        check(plannedSeen[0], "la ficha asignada sale en los bloqueadores 'planned' del combate (lo que mira Android)",
                "la ficha asignada no sale en planned: el arreglo de Android no la sacaria de la pila");
        check(blockingFalse[0], "la carta dice isBlocking() y el combate no: por eso el escritorio iba bien y Android no",
                "no es como se explica: revisar la decision 185 de Android");
        check(blockers[0] == 2 && blockers[1] == 2,
                "tocando la pila cada vez: dos Goblins en cada atacante (" + blockers[0] + " y " + blockers[1] + ")",
                "no quedan dos y dos: " + blockers[0] + " y " + blockers[1]);
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del bloqueo con fichas han fallado");
        }
    }

    /** El motor espera tus bloqueos. */
    private static boolean blocking(final NeoMatchUI ui) {
        return ui.getGameController() instanceof PlayerControllerHuman gc
                && gc.getInputQueue().getInput() instanceof InputBlock;
    }

    private static void act(final NeoMatchUI ui, final StringBuilder notes, final boolean[] plannedSeen,
                            final boolean[] blockingFalse, final int[] blockers) throws InterruptedException {
        final Game game = ui.getGameView().getGame();
        Player me = null;
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                me = p;
            }
        }
        final List<Card> attackers = new ArrayList<>(game.getCombat().getAttackers());
        final List<Card> goblins = new ArrayList<>();
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.isCreature()) {
                goblins.add(c);
            }
        }
        notes.append(attackers.size()).append(" atacantes, ").append(goblins.size()).append(" fichas\n");
        final PlayerControllerHuman gc = (PlayerControllerHuman) ui.getGameController();
        for (int a = 0; a < 2 && a < attackers.size(); a++) {
            // Clic en el atacante: a quien se bloquea ahora.
            gc.selectCard(attackers.get(a).getView(), null, null);
            Thread.sleep(150L);
            for (int k = 0; k < 2; k++) {
                // Clic en "la pila": su primera ficha, la que la mesa deja
                // apilada (lo que no esta ya asignado).
                // La regla de Android tras el arreglo: fuera de la pila lo que
                // el combate da por bloqueando o tiene como planned.
                final Set<Integer> planned = plannedIds(ui.getGameView().getCombat());
                final CombatView before = ui.getGameView().getCombat();
                Card top = null;
                for (final Card g : goblins) {
                    final CardView v = g.getView();
                    final boolean combatBlocking = before != null && before.isBlocking(v);
                    if (!combatBlocking && !planned.contains(v.getId())) {
                        top = g;
                        break;
                    }
                }
                if (top == null) {
                    notes.append("no queda ficha en la pila\n");
                    return;
                }
                gc.selectCard(top.getView(), null, null);
                Thread.sleep(150L);
                final CombatView now = ui.getGameView().getCombat();
                if (plannedIds(now).contains(top.getId())) {
                    plannedSeen[0] = true;
                    // La carta si, el combate no.
                    blockingFalse[0] |= top.getView().isBlocking() && (now == null || !now.isBlocking(top.getView()));
                }
            }
            final CombatView cv = ui.getGameView().getCombat();
            final Iterable<CardView> p = cv == null ? null : cv.getPlannedBlockers(attackers.get(a).getView());
            int n = 0;
            if (p != null) {
                for (final CardView ignored : p) {
                    n++;
                }
            }
            blockers[a] = n;
        }
    }

    /** Los ids de los bloqueadores 'planned' de todos los atacantes. */
    private static Set<Integer> plannedIds(final CombatView combat) {
        final Set<Integer> ids = new java.util.HashSet<>();
        if (combat == null || combat.getAttackers() == null) {
            return ids;
        }
        for (final CardView attacker : combat.getAttackers()) {
            final Iterable<CardView> p = combat.getPlannedBlockers(attacker);
            if (p != null) {
                for (final CardView b : p) {
                    ids.add(b.getId());
                }
            }
        }
        return ids;
    }

    private static TutorialLesson position() {
        final List<String> goblins = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            goblins.add("T:r_1_1_goblin");
        }
        final List<String> state = Arrays.asList(
                "turn=4",
                "activeplayer=ai",
                "activephase=COMBAT_DECLARE_BLOCKERS",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + String.join(";", goblins),
                "humanhand=",
                "humanlibrary=Mountain;Mountain;Mountain",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=Grizzly Bears|Set:M21|Attacking;Grizzly Bears|Set:M21|Attacking",
                "aihand=",
                "ailibrary=Forest;Forest;Forest",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("stackblock", state, List.of());
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [ok]   " + good);
        } else {
            failed++;
            System.out.println("  [MAL]  " + bad);
        }
    }
}
