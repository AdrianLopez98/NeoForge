package forge.neo.ascent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import forge.deck.Deck;
import forge.game.GameType;
import forge.game.player.PlayerView;
import forge.game.player.RegisteredPlayer;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.player.GamePlayerUtil;

/**
 * <b>Medir las mesas de cuatro</b> (27-10-2026): la misma casilla del mapa
 * jugada a 1 contra 1 y como mesa de cuatro, con <b>una IA llevando tu
 * mazo</b>, varias veces cada una. Dice cuantas se ganan, con cuanta vida se
 * sale y cuanto duran.
 *
 * <p>No es un comprobador (no esta en la bateria ni se pone en rojo): es una
 * medida, y tarda — cada partida a cuatro son tres IA pensando. Sirve para
 * afinar {@link AscentBattle#MELEE_LIFE} y {@link AscentBattle#FINAL_TABLE_LIFE}
 * con numeros en vez de a ojo. La IA no juega como una persona, asi que lo que
 * vale es la <b>comparacion</b> entre las dos formas de la misma casilla, no
 * el porcentaje suelto.
 *
 * <p>{@code run.cmd ascenttable [--games=N] [--final]}: con {@code --final},
 * la mesa del final (tres jefes) contra el jefe 1 contra 1 del acto 3, con el
 * mazo de salida — o sea el peor caso.
 */
public final class AscentTableProbe {

    private AscentTableProbe() {
    }

    /** Como se llama el asiento de tu mazo: es como se reconoce al ganador. */
    private static final String YOU = "Ascent Probe";

    /**
     * <b>Que al caer en una mesa se acaba la partida</b> ({@link AscentTableEnd}):
     * la primera mesa de una run, contigo sentado de verdad pero sin jugar
     * (el piloto automatico solo pasa), por el mismo camino que el
     * juego ({@code AscentBattle.fight}, con {@code Ending.ASCENT}). Bien si
     * pierdes y la partida termina con dos rivales o mas aun en pie: sin la regla,
     * Forge la seguiria hasta que quedara uno.
     */
    public static boolean fall() {
        AscentRelics.install();
        final AscentCheckGuard prestado = new AscentCheckGuard();
        AscentRun run = null;
        try {
            run = AscentRun.begin(AscentRun.Mode.COMMANDER, 0, 40, null, null, AscentSeedDeck.NO_COLOURS,
                    AscentPool.ALL, true);
            final AscentNode node = firstMelee(run);
            if (node == null) {
                System.out.println("  [MAL] la run no trae ninguna mesa");
                return false;
            }
            final AtomicReference<NeoMatchUI> ui = new AtomicReference<>();
            NeoGame.setOnUiForTest(ui::set);
            AscentTableEnd.lastStanding = -1;
            // Tu asiento con 1 vida: si no, las IA no van a por ti (no amenazas a
            // nadie: el piloto solo pasa), se matan entre ellas y caes el ultimo,
            // que es cuando la partida se acabaria igual.
            final AscentBattle.Plan plan = AscentBattle.plan(run, node);
            final NeoGame.Seating normal = AscentBattle.seating(plan);
            final NeoGame.Seating fragile = new NeoGame.Seating() {
                @Override
                public java.util.EnumSet<forge.game.GameType> variants() {
                    return normal.variants();
                }

                @Override
                public forge.game.player.RegisteredPlayer human(final Deck deck, final int seats) {
                    final forge.game.player.RegisteredPlayer seat = normal.human(deck, seats);
                    seat.setStartingLife(1);
                    return seat;
                }

                @Override
                public forge.game.player.RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                    return normal.opponent(i, deck, seats);
                }

                @Override
                public forge.LobbyPlayer player(final int i, final String aiProfile) {
                    return normal.player(i, aiProfile);
                }
            };
            final List<Deck> decks = new ArrayList<>();
            for (final AscentBattle.Rival r : plan.rivals) {
                decks.add(r.deck);
            }
            final NeoGame.Result result;
            try {
                result = NeoGame.play(AscentDecks.load(run), plan.rivals.size(), NeoMatchUI.Mode.AUTO_PLAY, 1800,
                        false, null, plan.aiProfile, true, NeoFormat.COMMANDER, decks, 1, fragile,
                        NeoMatchUI.Ending.ASCENT);
            } finally {
                NeoGame.setOnUiForTest(null);
            }
            final boolean won = forge.neo.look.NeoPlayers.human().getName().equals(result.winner);
            // Cuantos quedaban AL CERRAR: despues el motor da por perdidos a todos.
            final int standing = AscentTableEnd.lastStanding;
            boolean youLost = false;
            final NeoMatchUI gui = ui.get();
            if (gui != null && gui.getGameView() != null) {
                for (final PlayerView p : gui.getGameView().getPlayers()) {
                    youLost |= p.getHasLost() && p.getName().equals(forge.neo.look.NeoPlayers.human().getName());
                }
            }
            final boolean ok = !won && youLost && standing >= 2;
            System.out.println("  [" + (ok ? "ok" : "MAL") + "]   al caer en la mesa: ganada=" + won
                    + ", has caido=" + youLost + ", rivales en pie al cerrar=" + standing);
            return ok;
        } finally {
            if (run != null) {
                run.discard();
            }
            prestado.restore();
        }
    }

    public static void run(final int games, final boolean finalTable) {
        AscentRelics.install();
        final AscentCheckGuard prestado = new AscentCheckGuard();
        AscentRun run = null;
        try {
            run = AscentRun.begin(AscentRun.Mode.COMMANDER, 0, 40, null, null, AscentSeedDeck.NO_COLOURS,
                    AscentPool.ALL, true);
            if (finalTable) {
                final java.util.Random rnd = new java.util.Random(1L);
                while (run.getAct() < AscentRun.ACTS) {
                    walkToBoss(run, rnd);
                    run.advance();
                }
            }
            final AscentNode node = finalTable ? run.map().boss() : firstMelee(run);
            if (node == null) {
                System.out.println("  [MAL] la run no trae ninguna mesa");
                return;
            }
            final Deck deck = AscentDecks.load(run);
            final AscentBattle.Plan table = AscentBattle.plan(run, node);
            // La MISMA casilla sin mesas: en el mapa 1 contra 1 es un combate (o
            // el jefe de siempre). El nodo del mapa con mesas lleva el tipo MELEE
            // y daria otra vez la mesa.
            run.setMeleeForTest(false);
            final AscentNode same = run.map().node(node.getRow(), node.getCol());
            final AscentBattle.Plan duel = AscentBattle.plan(run, same);
            run.setMeleeForTest(true);
            System.out.println("  Tu mazo: " + deck.getName() + " | nodo " + node.key()
                    + " acto " + run.getAct());
            System.out.println("  1 contra 1: " + duel);
            System.out.println("  A cuatro:   " + table);
            final Stats one = measure("1 contra 1", duel, deck, games);
            final Stats four = measure("a cuatro", table, deck, games);
            System.out.println();
            System.out.println("  RESUMEN (" + games + " partidas de cada)");
            System.out.println("    " + one);
            System.out.println("    " + four);
        } finally {
            if (run != null) {
                run.discard();
            }
            prestado.restore();
        }
    }

    private static final class Stats {
        final String name;
        int won;
        int played;
        int lifeSum;
        int turnsSum;
        long millis;

        Stats(final String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%-10s ganadas %d de %d | vida media al ganar %s | %.1f turnos"
                            + " | %.1f min por partida", name, won, played,
                    won == 0 ? "-" : String.valueOf(lifeSum / won),
                    played == 0 ? 0.0 : (double) turnsSum / played,
                    played == 0 ? 0.0 : millis / 60000.0 / played);
        }
    }

    private static Stats measure(final String name, final AscentBattle.Plan plan, final Deck deck,
                                 final int games) {
        final Stats st = new Stats(name);
        final List<Deck> decks = new ArrayList<>();
        decks.add(deck);
        for (final AscentBattle.Rival r : plan.rivals) {
            decks.add(r.deck);
        }
        for (int g = 0; g < games; g++) {
            final AtomicReference<NeoMatchUI> ui = new AtomicReference<>();
            NeoGame.setOnUiForTest(ui::set);
            final long t0 = System.currentTimeMillis();
            final NeoGame.Result result;
            try {
                result = NeoGame.play(deck, plan.rivals.size(), NeoMatchUI.Mode.OBSERVE, 1800, false,
                        null, plan.aiProfile, true, NeoFormat.COMMANDER, decks, 1, seating(plan),
                        NeoMatchUI.Ending.NORMAL);
            } finally {
                NeoGame.setOnUiForTest(null);
            }
            final long t = System.currentTimeMillis() - t0;
            st.played++;
            st.millis += t;
            st.turnsSum += result.turns;
            final boolean won = YOU.equals(result.winner);
            int life = -1;
            final NeoMatchUI gui = ui.get();
            if (gui != null && gui.getGameView() != null) {
                for (final PlayerView p : gui.getGameView().getPlayers()) {
                    if (YOU.equals(p.getName())) {
                        life = p.getLife();
                    }
                }
            }
            if (won) {
                st.won++;
                st.lifeSum += Math.max(0, life);
            }
            System.out.printf(Locale.ROOT, "  [%s %d/%d] %s | gana %s | tu vida %d | %d turnos | %.1f min%n",
                    name, g + 1, games, won ? "GANADA" : "perdida", result.winner, life, result.turns,
                    t / 60000.0);
        }
        return st;
    }

    /** Todos IA: tu mazo en el asiento 0, con tu vida y tus reliquias; los rivales detras. */
    private static NeoGame.Seating seating(final AscentBattle.Plan plan) {
        return new NeoGame.Seating() {
            @Override
            public EnumSet<GameType> variants() {
                return plan.variants;
            }

            @Override
            public RegisteredPlayer human(final Deck deck, final int seats) {
                return AscentBattle.humanSeat(plan, deck, seats);
            }

            @Override
            public RegisteredPlayer opponent(final int i, final Deck deck, final int seats) {
                return i == 0 ? AscentBattle.humanSeat(plan, deck, seats)
                        : AscentBattle.opponentSeat(plan, i - 1, seats);
            }

            @Override
            public forge.LobbyPlayer player(final int i, final String aiProfile) {
                return i == 0 ? GamePlayerUtil.createAiPlayer(YOU, 0, aiProfile)
                        : forge.neo.look.NeoPlayers.passingAi(aiProfile);
            }
        };
    }

    private static AscentNode firstMelee(final AscentRun run) {
        final AscentMap map = run.map();
        for (int r = 0; r < AscentMap.ROWS; r++) {
            for (final AscentNode n : map.row(r)) {
                if (n.getKind() == AscentNode.Kind.MELEE) {
                    return n;
                }
            }
        }
        return null;
    }

    private static void walkToBoss(final AscentRun run, final java.util.Random rnd) {
        int turns = 0;
        while (!run.actCleared() && ++turns < AscentMap.ROWS * AscentMap.COLS) {
            final List<AscentNode> options = run.available();
            if (options.isEmpty() || !run.clear(options.get(rnd.nextInt(options.size())))) {
                return;
            }
        }
    }
}
