package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.Game;
import forge.game.GameType;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.model.FModel;
import forge.player.PlayerControllerHuman;

/**
 * <b>Planechase, jugado</b> ({@code run.cmd planechasecheck}).
 *
 * <p>Una partida de Estandar con Planechase ({@link Planechase#wrap}), por el
 * mismo {@code NeoGame.play} que la del inicio. En tu primera fase principal se
 * para al piloto y se mira lo que el jugador veria y haria:
 * <ul>
 *   <li>que la variante esta puesta y hay un plano activo;</li>
 *   <li>que lo que pinta la etiqueta de las fases ({@code TableBinder
 *       .activePlanes}) es ese plano y no otro;</li>
 *   <li>que tienes tu mazo planar y el efecto "Planar Dice" en tu zona de mando;</li>
 *   <li>y que <b>clicarlo como se clica en la mesa</b> ({@code selectCard} desde
 *       el hilo de interfaz) tira el dado de verdad.</li>
 * </ul>
 */
public final class PlanechaseCheck {

    private PlanechaseCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;
        final List<String> notes = new ArrayList<>();
        final AtomicBoolean done = new AtomicBoolean();
        final AtomicBoolean reached = new AtomicBoolean();
        final AtomicBoolean rolled = new AtomicBoolean();

        NeoGame.onUiForTest = ui -> {
            ui.setAutoPlayHold(() -> !done.get() && atMyMain(ui));
            final Thread driver = new Thread(() -> drive(ui, done, reached, rolled, notes), "planechasecheck");
            driver.setDaemon(true);
            driver.start();
        };
        try {
            NeoGame.play(withPlanes(deck("Prueba planechase")), 1, NeoMatchUI.Mode.AUTO_PLAY, 40, false, null, null, true,
                    NeoFormat.STANDARD, List.of(deck("Prueba rival")), 1,
                    Planechase.wrap(NeoFormat.STANDARD, null, true));
        } finally {
            NeoGame.onUiForTest = null;
            done.set(true);
        }
        notes.forEach(n -> System.out.println("  " + n));
        check(reached.get(), "se llega a tu fase principal con Planechase puesto");
        check(rolled.get(), "clicar \"Planar Dice\" en tu zona de mando tira el dado");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de Planechase han fallado");
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

    private static void drive(final NeoMatchUI ui, final AtomicBoolean done, final AtomicBoolean reached,
                              final AtomicBoolean rolled, final List<String> notes) {
        try {
            for (int i = 0; i < 600 && !atMyMain(ui) && !done.get(); i++) {
                Thread.sleep(50L);
            }
            if (!atMyMain(ui)) {
                notes.add("no se llego a tu fase principal");
                return;
            }
            Thread.sleep(300L);
            reached.set(true);
            final Game g = gameOf(ui);
            final Player me = human(g);

            check(g.getRules().hasAppliedVariant(GameType.Planechase), "la variante Planechase esta en las reglas");
            final List<String> engine = new ArrayList<>();
            for (final Card c : g.getActivePlanes()) {
                engine.add(c.getName());
            }
            check(!engine.isEmpty(), "hay un plano activo " + engine);
            final List<String> shown = new ArrayList<>();
            for (final CardView c : TableBinder.activePlanes(ui.getGameView())) {
                shown.add(c.getName());
            }
            check(shown.equals(engine), "la etiqueta de las fases ensenya ese plano " + shown);
            final int deck = me.getZone(ZoneType.PlanarDeck).size();
            check(deck >= 9, "tienes tu mazo planar (" + deck + " cartas)");
            // El tuyo, el de la seccion de planos del mazo (como el lobby de
            // Forge), y no uno al azar: las del mazo planar y la que este
            // activa si es tuya, todas de las diez que lleva.
            boolean mine = deck <= MY_PLANES.size();
            for (final Card c : me.getZone(ZoneType.PlanarDeck)) {
                mine &= MY_PLANES.contains(c.getName());
            }
            check(mine, "es el mazo planar que trae tu mazo, no uno al azar");

            Card dice = null;
            for (final Card c : me.getCardsIn(ZoneType.Command)) {
                if ("Planar Dice".equals(c.getName())) {
                    dice = c;
                }
            }
            check(dice != null, "\"Planar Dice\" esta en tu zona de mando");
            if (dice == null) {
                return;
            }
            final int strength = ui.actionableStrength(dice.getView());
            notes.add("la mesa lo marca como usable con fuerza " + strength);

            final int before = g.getPhaseHandler().getPlanarDiceSpecialActionThisTurn();
            final CardView view = dice.getView();
            final PlayerControllerHuman ctrl = (PlayerControllerHuman) ui.getGameController();
            GuiBase.getInterface().invokeInEdtLater(() -> ctrl.selectCard(view, null, null));
            for (int i = 0; i < 200
                    && g.getPhaseHandler().getPlanarDiceSpecialActionThisTurn() == before; i++) {
                Thread.sleep(25L);
            }
            rolled.set(g.getPhaseHandler().getPlanarDiceSpecialActionThisTurn() == before + 1);
            notes.add("tiradas este turno: " + before + " -> "
                    + g.getPhaseHandler().getPlanarDiceSpecialActionThisTurn());
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

    /** Diez planos (ningun fenomeno): el mazo planar que trae el mazo de prueba. */
    private static final List<String> MY_PLANES = new ArrayList<>();

    private static Deck withPlanes(final Deck d) {
        MY_PLANES.clear();
        final forge.deck.CardPool planes = d.getOrCreate(DeckSection.Planes);
        for (final forge.item.PaperCard p : FModel.getPlanechaseCards().toFlatList()) {
            if (MY_PLANES.size() < 10 && p.getRules().getType().isPlane() && !MY_PLANES.contains(p.getName())) {
                planes.add(p, 1);
                MY_PLANES.add(p.getName());
            }
        }
        return d;
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
