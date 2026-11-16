package forge.neo.match;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.input.InputPassPriority;
import forge.model.FModel;
import forge.player.PlayerControllerHuman;

/**
 * <b>Two-Headed Giant, jugado</b> ({@code run.cmd twoheadedcheck}).
 *
 * <p>Primero, sin partida, los asientos de {@link TwoHeadedGiant}: las vidas
 * que pone {@code forVariants} (30, y 60 con Commander, que es como lo hace
 * Forge) y los equipos de dos en dos, jugando tu y observando.
 *
 * <p>Luego una partida de Estandar a cuatro con 2HG, por el mismo
 * {@code NeoGame.play} que la del inicio. En tu primera fase principal se mira
 * lo que hay: la variante en las reglas, los equipos (tu con el rival 1, y
 * sentados juntos), que el turno es de tu equipo entero y las 30 vidas. Y
 * mientras dura la partida se vigila que los dos de un equipo tengan
 * <b>siempre la misma vida</b>, que es lo que mas se nota si algo no va.
 */
public final class TwoHeadedGiantCheck {

    private TwoHeadedGiantCheck() {
    }

    /** Los nombres del equipo rival, para saber quien ha ganado. */
    private static final List<String> ENEMIES = new java.util.concurrent.CopyOnWriteArrayList<>();

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        ENEMIES.clear();
        seats();
        System.out.println();
        play();
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de Two-Headed Giant han fallado");
        }
    }

    /** Los asientos, sin partida. */
    private static void seats() {
        check(TwoHeadedGiant.appliesTo(GameType.Commander) && TwoHeadedGiant.appliesTo(GameType.Constructed)
                        && !TwoHeadedGiant.appliesTo(GameType.Brawl) && !TwoHeadedGiant.appliesTo(GameType.Oathbreaker)
                        && !TwoHeadedGiant.appliesTo(GameType.Draft),
                "vale en Commander y en los construidos; no en Brawl, Oathbreaker ni draft");
        check(!TwoHeadedGiant.applies(true, GameType.Commander, 2)
                        && TwoHeadedGiant.applies(true, GameType.Commander, 3)
                        && !TwoHeadedGiant.applies(false, GameType.Commander, 3),
                "solo a cuatro, y solo encendido");
        check(TwoHeadedGiant.wrap(NeoFormat.COMMANDER, null, true, 2) == null
                        && TwoHeadedGiant.wrap(NeoFormat.BRAWL, null, true, 3) == null,
                "sin cuatro jugadores o en otro formato no toca los asientos");

        final Deck d = deck("asiento");
        final RegisteredPlayer std = TwoHeadedGiant.seat(d, 4, TwoHeadedGiant.variants(GameType.Constructed), 0);
        final RegisteredPlayer edh = TwoHeadedGiant.seat(d, 4, TwoHeadedGiant.variants(GameType.Commander), 0);
        check(std.getStartingLife() == 30, "construido: 30 vidas por equipo (" + std.getStartingLife() + ")");
        check(edh.getStartingLife() == 60, "Commander: 60 vidas por equipo (" + edh.getStartingLife() + ")");

        final NeoGame.Seating playing = TwoHeadedGiant.wrap(NeoFormat.STANDARD, null, true, 3);
        final int[] got = {
                playing.human(d, 4).getTeamNumber(),
                playing.opponent(0, d, 4).getTeamNumber(),
                playing.opponent(1, d, 4).getTeamNumber(),
                playing.opponent(2, d, 4).getTeamNumber()};
        check(got[0] == 0 && got[1] == 0 && got[2] == 1 && got[3] == 1,
                "jugando: tu y el rival 1 contra los rivales 2 y 3 (" + java.util.Arrays.toString(got) + ")");
        check(playing.variants().equals(EnumSet.of(GameType.Constructed, GameType.TwoHeadedGiant)),
                "las variantes: las del formato y 2HG");

        final NeoGame.Seating watching = TwoHeadedGiant.wrap(NeoFormat.STANDARD, null, true, 3);
        final int[] seen = {
                watching.opponent(0, d, 4).getTeamNumber(),
                watching.opponent(1, d, 4).getTeamNumber(),
                watching.opponent(2, d, 4).getTeamNumber(),
                watching.opponent(3, d, 4).getTeamNumber()};
        // Observando, la ultima IA juega TU mazo en tu asiento: va con el rival 1.
        check(seen[0] == 0 && seen[1] == 1 && seen[2] == 1 && seen[3] == 0,
                "observando: la IA con tu mazo va con el rival 1 (" + java.util.Arrays.toString(seen) + ")");
    }

    private static void play() {
        final List<String> notes = new ArrayList<>();
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicBoolean reached = new AtomicBoolean();
        final AtomicBoolean gameOver = new AtomicBoolean();
        final AtomicInteger split = new AtomicInteger();
        final AtomicInteger viewSplit = new AtomicInteger();
        final AtomicInteger lowest = new AtomicInteger(Integer.MAX_VALUE);
        NeoGame.onUiForTest = ui -> {
            ui.setAutoPlayHold(() -> !done.get() && atMyMain(ui));
            final Thread driver = new Thread(() -> drive(ui, done, reached, notes), "twoheadedcheck");
            driver.setDaemon(true);
            driver.start();
            final Thread watch = new Thread(() -> watchLife(ui, gameOver, split, viewSplit, lowest), "twoheadedcheck-life");
            watch.setDaemon(true);
            watch.start();
        };
        final java.util.concurrent.atomic.AtomicReference<String> crash = new java.util.concurrent.atomic.AtomicReference<>();
        forge.neo.platform.NeoGuiBase.clearEngineCrash();
        forge.neo.platform.NeoGuiBase.setOnEngineCrash(crash::set);
        NeoGame.Result r;
        try {
            // Tu equipo solo con Islas: asi el otro ataca y la vida compartida
            // se pone a prueba de verdad (osos contra osos nadie ataca).
            r = NeoGame.play(islands("Prueba 2HG"), 3, NeoMatchUI.Mode.AUTO_PLAY, 120, false, null, null,
                    true, NeoFormat.STANDARD,
                    List.of(islands("Companyero"), deck("Rival dos"), deck("Rival tres")), 1,
                    TwoHeadedGiant.wrap(NeoFormat.STANDARD, null, true, 3));
        } finally {
            NeoGame.onUiForTest = null;
            forge.neo.platform.NeoGuiBase.setOnEngineCrash(null);
            done.set(true);
            gameOver.set(true);
        }
        notes.forEach(n -> System.out.println("  " + n));
        check(reached.get(), "se llega a tu fase principal");
        System.out.printf(Locale.ROOT, "  partida: %s en %d turnos, gana %s%n",
                r.completed ? "acabada" : "SIN ACABAR", r.turns, r.winner);
        check(crash.get() == null && (r.completed || r.turns >= 3),
                "la partida avanza sin romperse" + (crash.get() == null ? "" : " (se rompio: " + crash.get() + ")"));
        check(split.get() == 0, "los dos de cada equipo tienen siempre la misma vida"
                + (split.get() == 0 ? "" : " (" + split.get() + " veces distinta)"));
        check(viewSplit.get() == 0, "y la mesa las ve iguales"
                + (viewSplit.get() == 0 ? "" : " (" + viewSplit.get() + " veces distinta)"));
        check(lowest.get() < 30, "el dano le llega al equipo (la vida mas baja vista: "
                + (lowest.get() == Integer.MAX_VALUE ? "?" : lowest.get()) + ")");
        check(r.completed && r.winner != null && ENEMIES.contains(r.winner),
                "gana el equipo rival (" + r.winner + ")");
    }

    /**
     * Vigila la vida compartida mientras dura la partida. Se lee desde otro
     * hilo, asi que una diferencia solo cuenta si sigue en la lectura
     * siguiente (el motor pone la vida a los dos casi a la vez, no a la vez).
     */
    private static void watchLife(final NeoMatchUI ui, final AtomicBoolean over,
                                  final AtomicInteger split, final AtomicInteger viewSplit,
                                  final AtomicInteger lowest) {
        boolean wasSplit = false;
        int viewRun = 0;
        while (!over.get()) {
            try {
                Thread.sleep(40L);
                final Game g = gameOf(ui);
                if (g == null) {
                    continue;
                }
                boolean now = false;
                for (final Player p : g.getPlayers()) {
                    lowest.accumulateAndGet(p.getLife(), Math::min);
                    for (final Player q : g.getPlayers()) {
                        if (p != q && p.getTeam() == q.getTeam() && p.getLife() != q.getLife()) {
                            now = true;
                        }
                    }
                }
                if (now && wasSplit) {
                    split.incrementAndGet();
                }
                wasSplit = now;
                // Y lo que ve la mesa (las PlayerView): la vida de los dos tiene
                // que llegar igual. Cuenta si dura mas de medio segundo.
                boolean viewNow = false;
                for (final forge.game.player.PlayerView p : ui.getGameView().getPlayers()) {
                    for (final forge.game.player.PlayerView q : ui.getGameView().getPlayers()) {
                        if (p != q && p.sharesTeamWith(q) && p.getLife() != q.getLife()) {
                            viewNow = true;
                        }
                    }
                }
                viewRun = viewNow ? viewRun + 1 : 0;
                if (viewRun == 12) {
                    viewSplit.incrementAndGet();
                    final StringBuilder sb = new StringBuilder("  [vista] vidas distintas:");
                    for (final forge.game.player.PlayerView p : ui.getGameView().getPlayers()) {
                        sb.append(' ').append(p.getName()).append('=').append(p.getLife());
                    }
                    System.out.println(sb);
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (final RuntimeException e) {
                // la partida cambiando por debajo: se mira en la siguiente
            }
        }
    }

    private static Game gameOf(final NeoMatchUI ui) {
        return ui == null || ui.getGameView() == null ? null : ui.getGameView().getGame();
    }

    private static Player human(final Game g) {
        for (final Player p : g.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    /** Tu fase principal (la de tu equipo), la pila vacia y el motor esperando que juegues. */
    private static boolean atMyMain(final NeoMatchUI ui) {
        try {
            final Game g = gameOf(ui);
            final Player me = g == null ? null : human(g);
            return me != null && g.getPhaseHandler().isPlayerTurn(me)
                    && g.getPhaseHandler().getPhase() == PhaseType.MAIN1 && g.getStack().isEmpty()
                    && ui.getGameController() instanceof PlayerControllerHuman c
                    && c.getInputQueue().getInput() instanceof InputPassPriority;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    private static void drive(final NeoMatchUI ui, final AtomicBoolean done,
                              final AtomicBoolean reached, final List<String> notes) {
        try {
            for (int i = 0; i < 2400 && !atMyMain(ui) && !done.get(); i++) {
                Thread.sleep(25L);
            }
            if (!atMyMain(ui)) {
                notes.add("no se llego a tu fase principal");
                return;
            }
            reached.set(true);
            final Game g = gameOf(ui);
            final Player me = human(g);
            check(g.getRules().hasAppliedVariant(GameType.TwoHeadedGiant) && g.getRules().hasSharedTeamTurns(),
                    "la variante esta en las reglas, con turnos por equipo");
            final List<Player> all = new ArrayList<>(g.getPlayers());
            for (final Player p : all) {
                notes.add(String.format(Locale.ROOT, "%-12s vida %d (empezo con %d), equipo %d%s",
                        p.getName(), p.getLife(), p.getStartingLife(), p.getTeam(), p == me ? "  <- tu" : ""));
            }
            check(all.size() == 4, "cuatro jugadores");
            Player mate = null;
            for (final Player p : all) {
                if (p != me && p.getTeam() == me.getTeam()) {
                    mate = p;
                }
            }
            check(mate != null && me.sharesTeamWith(mate) && !me.isOpponentOf(mate),
                    "tienes companyero" + (mate == null ? "" : " (" + mate.getName() + ")"));
            boolean thirty = true;
            for (final Player p : all) {
                thirty &= p.getStartingLife() == 30;
            }
            check(thirty, "todos empiezan con 30 (la vida del equipo)");
            check(mate != null && g.getPhaseHandler().isPlayerTurn(mate),
                    "tu turno es tambien el de tu companyero (lo lleva "
                            + g.getPhaseHandler().getPlayerTurn().getName() + ")");
            // Los companyeros sentados juntos (CR 805.1): en el orden de la
            // partida, el de al lado de uno es su companyero o el anterior lo es.
            boolean adjacent = true;
            for (int i = 0; i < all.size(); i++) {
                final Player p = all.get(i);
                final Player next = all.get((i + 1) % all.size());
                final Player prev = all.get((i + all.size() - 1) % all.size());
                adjacent &= next.getTeam() == p.getTeam() || prev.getTeam() == p.getTeam();
            }
            check(adjacent, "los companyeros se sientan juntos");
            final GameType[] none = {GameType.Planechase, GameType.Archenemy, GameType.ArchenemyRumble};
            boolean alone = true;
            for (final GameType t : none) {
                alone &= !g.getRules().hasAppliedVariant(t);
            }
            check(alone, "sin Planechase ni Archenemy");
            for (final Player p : all) {
                if (p.getTeam() != me.getTeam()) {
                    ENEMIES.add(p.getName());
                }
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final RuntimeException e) {
            notes.add("excepcion en la prueba: " + e);
            e.printStackTrace();
        } finally {
            done.set(true);
            ui.nudgeAutoPlay();
        }
    }

    /** 60 Islas: un equipo que no ataca nunca. */
    private static Deck islands(final String name) {
        final Deck d = new Deck(name);
        d.getOrCreate(DeckSection.Main).add(FModel.getMagicDb().getCommonCards().getCard("Island", "M21"), 60);
        return d;
    }

    /** 60 cartas: 24 Bosques y 36 Grizzly Bears, que se lanzan y atacan. */
    private static Deck deck(final String name) {
        final Deck d = new Deck(name);
        d.getOrCreate(DeckSection.Main).add(FModel.getMagicDb().getCommonCards().getCard("Forest", "M21"), 24);
        d.getOrCreate(DeckSection.Main).add(FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears", "6ED"), 36);
        return d;
    }

    private static synchronized void check(final boolean ok, final String what) {
        System.out.println("  [" + (ok ? "ok" : "MAL") + "]  " + what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
