package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.model.FModel;
import forge.player.PlayerControllerHuman;
import forge.util.Localizer;

/**
 * <b>Las pilas con varios rivales</b> ({@code run.cmd twopilescheck}).
 *
 * <p>Discord, 08-10-2026: con <i>Curator of Destinies</i> en Commander, tras
 * mirar las cinco cartas la partida se quedaba esperando sin decir que. Lo que
 * esperaba era que eligieras QUE rival elige la pila, y eso solo se contestaba
 * clicando un retrato (ver {@link PlayerPick}). Hasta el piloto automatico se
 * equivocaba: se elegia a si mismo, el motor descartaba el clic y la partida
 * se quedaba clavada.
 *
 * <p>Partida a cuatro sin ventana; en tu fase principal <i>Curator</i> entra en
 * tu campo y se mira:
 * <ul>
 *   <li>que el motor pregunta por un jugador y que los elegibles son
 *       <b>exactamente los tres rivales</b> (lo que salen como botones);</li>
 *   <li>que el "Chooser:" se reconoce y se explica;</li>
 *   <li>que el separar en pilas lleva su ayuda ({@code NeoMatchUI.pileHint});</li>
 *   <li>y que el efecto <b>se resuelve</b>: las cinco cartas salen de la
 *       biblioteca y van a tu mano o a tu cementerio.</li>
 * </ul>
 */
public final class TwoPilesCheck {

    private TwoPilesCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final List<String> notes = new ArrayList<>();
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicBoolean placed = new AtomicBoolean();
        final AtomicBoolean resolved = new AtomicBoolean();
        final Set<String> offered = new TreeSet<>();
        final Set<String> opponents = new TreeSet<>();
        final AtomicInteger asked = new AtomicInteger();

        NeoGame.onUiForTest = ui -> {
            ui.setAutoPlayHold(() -> {
                // Cada vez que el motor pide algo: si es un jugador, apuntar
                // entre quienes. Se lee aqui, con el input vivo, como la mesa.
                if (placed.get()) {
                    final List<PlayerPick.Choice> now = PlayerPick.pending(ui.getGameController());
                    if (!now.isEmpty()) {
                        asked.incrementAndGet();
                        synchronized (offered) {
                            for (final PlayerPick.Choice c : now) {
                                offered.add(PlayerName.of(c.player()));
                            }
                        }
                    }
                }
                return !done.get() && atMyMain(ui);
            });
            final Thread driver = new Thread(() -> drive(ui, done, placed, resolved, opponents, notes),
                    "twopilescheck");
            driver.setDaemon(true);
            driver.start();
        };
        try {
            final List<Deck> rivals = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                rivals.add(deck("Rival " + i));
            }
            NeoGame.play(deck("Pilas"), 3, NeoMatchUI.Mode.AUTO_PLAY, 60, false, null, null, true,
                    NeoFormat.STANDARD, rivals, 1, null, null);
        } finally {
            NeoGame.onUiForTest = null;
            done.set(true);
        }
        notes.forEach(n -> System.out.println("  " + n));

        check(placed.get(), "Curator of Destinies entra en tu campo en una partida a cuatro");
        check(asked.get() > 0, "el motor pregunta por un JUGADOR (los botones de la barra)");
        check(!opponents.isEmpty() && offered.equals(opponents),
                "los elegibles son los tres rivales y nadie mas " + offered);
        check(resolved.get(), "el efecto se resuelve: las cinco cartas van a tu mano o a tu cementerio");

        final String chooser = Localizer.getInstance().getMessage("lblChooser") + ":";
        final String raw = "Curator of Destinies (241)\n\n" + chooser;
        check(PlayerPick.isChooserPrompt(raw)
                        && PlayerPick.explainChooser(raw, "X").equals("Curator of Destinies (241)\n\nX"),
                "el \"" + chooser + "\" del motor se reconoce y se cambia por la explicacion");
        check(!PlayerPick.isChooserPrompt("Choose a creature"), "y un prompt cualquiera no se toca");
        check(NeoMatchUI.pileHint(Localizer.getInstance().getMessage("lblSelectCardForFaceDownPile")) != null
                        && NeoMatchUI.pileHint(Localizer.getInstance().getMessage("lblDivideCardIntoTwoPiles")) != null
                        && NeoMatchUI.pileHint("Choose cards") == null,
                "separar en pilas lleva su ayuda (y lo demas no)");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de las pilas han fallado");
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

    private static void drive(final NeoMatchUI ui, final AtomicBoolean done, final AtomicBoolean placed,
                              final AtomicBoolean resolved, final Set<String> opponents,
                              final List<String> notes) {
        try {
            for (int i = 0; i < 600 && !atMyMain(ui) && !done.get(); i++) {
                Thread.sleep(50L);
            }
            if (!atMyMain(ui)) {
                notes.add("no se llego a tu fase principal");
                return;
            }
            final Game g = gameOf(ui);
            final Player me = human(g);
            for (final Player p : g.getPlayers()) {
                if (p != me) {
                    opponents.add(PlayerName.of(p.getView()));
                }
            }
            final int library = me.getZone(ZoneType.Library).size();
            final int handAndGrave = me.getZone(ZoneType.Hand).size() + me.getZone(ZoneType.Graveyard).size();
            final Card card = Card.fromPaperCard(
                    FModel.getMagicDb().getCommonCards().getCard("Curator of Destinies"), me);
            final java.util.concurrent.CountDownLatch in = new java.util.concurrent.CountDownLatch(1);
            g.getAction().invoke(() -> {
                // Primero a la mano y luego al campo, con la carta que DEVUELVE
                // moveToHand: si no, no dispara su "cuando entra".
                final Card inHand = g.getAction().moveToHand(card, null);
                g.getAction().moveToPlay(inHand, me, null, null);
                g.getTriggerHandler().runWaitingTriggers();
                in.countDown();
            });
            in.await();
            placed.set(true);
            done.set(true);
            ui.nudgeAutoPlay();
            for (int i = 0; i < 400 && me.getZone(ZoneType.Library).size() > library - 5; i++) {
                Thread.sleep(25L);
            }
            final int nowHandAndGrave = me.getZone(ZoneType.Hand).size() + me.getZone(ZoneType.Graveyard).size();
            notes.add("biblioteca " + library + " -> " + me.getZone(ZoneType.Library).size()
                    + " | mano+cementerio " + handAndGrave + " -> " + nowHandAndGrave);
            resolved.set(me.getZone(ZoneType.Library).size() == library - 5
                    && nowHandAndGrave >= handAndGrave + 5);
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

    /** 60 cartas: 24 Islas y 36 Grizzly Bears. */
    private static Deck deck(final String name) {
        final Deck d = new Deck(name);
        d.getOrCreate(DeckSection.Main).add(FModel.getMagicDb().getCommonCards().getCard("Island", "M21"), 24);
        d.getOrCreate(DeckSection.Main).add(FModel.getMagicDb().getCommonCards().getCard("Grizzly Bears", "6ED"), 36);
        return d;
    }

    private static synchronized void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
