package forge.neo.match;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.player.PlayerControllerHuman;

/**
 * <b>Archenemy, jugado</b> ({@code run.cmd archenemycheck}).
 *
 * <p>Primero, sin partida, el mazo de esquemas al azar ({@link
 * Archenemy#randomSchemes}): 20, sin repetir, todos esquemas y con 4 en curso
 * como mucho — que es lo que impide que el mazo se vacie y el motor reviente
 * (ver la clase).
 *
 * <p>Luego una partida de Estandar contra dos rivales por cada forma de elegir
 * al archienemigo ({@link Archenemy.Who}), por el mismo {@code NeoGame.play}
 * que la del inicio. En tu primera fase principal se mira lo que hay: la
 * variante en las reglas, quien es archienemigo, sus 40 vidas y su mazo de
 * esquemas, los equipos y quien ha empezado. Y luego se deja acabar la
 * partida, que es donde se veria un esquema que revienta.
 */
public final class ArchenemyCheck {

    private ArchenemyCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        randomDecks();
        for (final Archenemy.Who who : Archenemy.Who.values()) {
            System.out.println();
            System.out.println("  --- el archienemigo: " + who.id + " ---");
            play(who);
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de Archenemy han fallado");
        }
    }

    /** Muchos mazos al azar: del tamanyo, sin repetir, solo esquemas, pocos en curso. */
    private static void randomDecks() {
        int worstOngoing = 0;
        boolean ok = true;
        for (int i = 0; i < 200; i++) {
            final List<PaperCard> d = Archenemy.randomSchemes();
            final Set<String> names = new HashSet<>();
            int ongoing = 0;
            for (final PaperCard c : d) {
                ok &= c.getRules().getType().isScheme() && names.add(c.getName());
                if (Archenemy.isOngoing(c)) {
                    ongoing++;
                }
            }
            ok &= d.size() == Archenemy.SCHEMES && ongoing <= Archenemy.MAX_ONGOING;
            worstOngoing = Math.max(worstOngoing, ongoing);
        }
        check(ok, "200 mazos de esquemas al azar: 20, sin repetir, como mucho "
                + worstOngoing + " en curso (tope " + Archenemy.MAX_ONGOING + ")");
    }

    private static void play(final Archenemy.Who who) {
        final List<String> notes = new ArrayList<>();
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicBoolean reached = new AtomicBoolean();
        NeoGame.onUiForTest = ui -> {
            ui.setAutoPlayHold(() -> !done.get() && atMyMain(ui));
            final Thread driver = new Thread(() -> drive(ui, who, done, reached, notes), "archenemycheck");
            driver.setDaemon(true);
            driver.start();
        };
        // El hilo de la partida muerto es lo que se veria si un esquema
        // revienta (las trampas conocidas): "La partida se ha roto".
        final java.util.concurrent.atomic.AtomicReference<String> crash = new java.util.concurrent.atomic.AtomicReference<>();
        forge.neo.platform.NeoGuiBase.clearEngineCrash();
        forge.neo.platform.NeoGuiBase.setOnEngineCrash(crash::set);
        NeoGame.Result r;
        try {
            r = NeoGame.play(deck("Prueba archienemigo"), 2, NeoMatchUI.Mode.AUTO_PLAY, 75, false, null, null,
                    true, NeoFormat.STANDARD, List.of(deck("Rival uno"), deck("Rival dos")), 1,
                    Archenemy.wrap(NeoFormat.STANDARD, null, true, who));
        } finally {
            NeoGame.onUiForTest = null;
            forge.neo.platform.NeoGuiBase.setOnEngineCrash(null);
            done.set(true);
        }
        notes.forEach(n -> System.out.println("  " + n));
        check(reached.get(), who.id + ": se llega a tu fase principal");
        System.out.printf(Locale.ROOT, "  partida: %s en %d turnos, gana %s%n",
                r.completed ? "acabada" : "SIN ACABAR", r.turns, r.winner);
        // El piloto no juega ni una tierra: contra 40 vidas la partida puede no
        // acabar a tiempo, y eso no es un fallo. Lo que no puede es romperse.
        check(crash.get() == null && (r.completed || r.turns >= 3),
                who.id + ": la partida avanza sin romperse"
                        + (crash.get() == null ? "" : " (se rompio: " + crash.get() + ")"));
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

    /** Tu fase principal, la pila vacia y el motor esperando que juegues. */
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

    private static void drive(final NeoMatchUI ui, final Archenemy.Who who, final AtomicBoolean done,
                              final AtomicBoolean reached, final List<String> notes) {
        try {
            // Quien juega el primer turno: se apunta en cuanto se ve.
            Player first = null;
            for (int i = 0; i < 2400 && !atMyMain(ui) && !done.get(); i++) {
                final Game g = gameOf(ui);
                if (first == null && g != null && g.getPhaseHandler().getTurn() == 1) {
                    first = g.getPhaseHandler().getPlayerTurn();
                }
                Thread.sleep(25L);
            }
            if (!atMyMain(ui)) {
                notes.add("no se llego a tu fase principal");
                return;
            }
            reached.set(true);
            final Game g = gameOf(ui);
            final Player me = human(g);
            final GameType variant = Archenemy.variantFor(who);
            check(g.getRules().hasAppliedVariant(variant), who.id + ": la variante " + variant + " esta en las reglas");

            final List<Player> villains = new ArrayList<>();
            for (final Player p : g.getPlayers()) {
                // isArchenemy() es "tiene mazo de esquemas": se mira tambien
                // lo que haya en marcha en la zona de mando.
                final int schemes = p.getZone(ZoneType.SchemeDeck).size() + schemesInCommand(p);
                if (schemes > 0) {
                    villains.add(p);
                }
                notes.add(String.format(Locale.ROOT, "%-12s vida %d (empezo con %d), equipo %d, %d esquemas%s",
                        p.getName(), p.getLife(), p.getStartingLife(), p.getTeam(), schemes,
                        p == me ? "  <- tu" : ""));
            }
            switch (who) {
                case ME:
                    check(villains.size() == 1 && villains.get(0) == me, "me: tu eres el unico archienemigo");
                    break;
                case RIVAL:
                    check(villains.size() == 1 && villains.get(0) != me, "rival: el archienemigo es un rival, y uno solo");
                    break;
                default:
                    check(villains.size() == g.getPlayers().size(), "all: todos son archienemigos");
                    break;
            }
            boolean life = true;
            boolean decks = true;
            for (final Player p : villains) {
                life &= p.getStartingLife() == 40;
                decks &= p.getZone(ZoneType.SchemeDeck).size() + schemesInCommand(p) == Archenemy.SCHEMES;
            }
            check(life, who.id + ": el archienemigo empieza con 40 vidas");
            check(decks, who.id + ": su mazo de esquemas es de " + Archenemy.SCHEMES);

            if (who != Archenemy.Who.ALL) {
                final Player villain = villains.isEmpty() ? null : villains.get(0);
                boolean teams = villain != null;
                for (final Player p : g.getPlayers()) {
                    if (villain != null && p != villain) {
                        teams &= p.getTeam() != villain.getTeam() && !p.isOpponentOf(otherHero(g, villain, p));
                    }
                }
                check(teams, who.id + ": los demas van juntos contra el archienemigo");
                check(first != null && first == villain, who.id + ": empieza el archienemigo ("
                        + (first == null ? "?" : first.getName()) + ")");
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

    /** Otro jugador que no es el archienemigo ni {@code p}, o {@code p} si no hay. */
    private static Player otherHero(final Game g, final Player villain, final Player p) {
        for (final Player q : g.getPlayers()) {
            if (q != villain && q != p) {
                return q;
            }
        }
        return p;
    }

    private static int schemesInCommand(final Player p) {
        int n = 0;
        for (final Card c : p.getCardsIn(ZoneType.Command)) {
            if (c.isScheme()) {
                n++;
            }
        }
        return n;
    }

    /** 60 cartas: 24 Islas y 36 Grizzly Bears. */
    private static Deck deck(final String name) {
        final Deck d = new Deck(name);
        d.getOrCreate(DeckSection.Main).add(FModel.getMagicDb().getCommonCards().getCard("Island", "M21"), 24);
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
