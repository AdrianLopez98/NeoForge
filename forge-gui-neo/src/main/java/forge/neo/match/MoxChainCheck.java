package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.phase.PhaseHandler;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Girar fuentes de mana en cadena, rapido</b> ({@code run.cmd moxchaincheck}).
 *
 * <p>Informe de Discord (01-10-2026, Android): <i>"I tap all my mana sources
 * to play an X cost spell ... I tap Mox Opal then not getting the floating
 * mana"</i>, y sin la pregunta del color; "como 1 de cada 8". Con el Mox solo y
 * despacio no se reproduce.
 *
 * <p>Aqui se hace lo que hace el jugador, por el MISMO camino que la mesa
 * ({@code selectCard} desde el hilo de interfaz): las cinco tierras y el Mox,
 * seguidos y sin esperar entre clics, muchas veces. En cada ronda se destapa
 * todo y se vacia la reserva antes, y despues se comprueba:
 *
 * <ul>
 *   <li>que <b>si el Mox acaba girado, su mana esta en la reserva</b> (el
 *       fallo del informe);</li>
 *   <li>que no se ha salido de la fase principal (la reserva se vacia al
 *       cambiar de paso);</li>
 *   <li>cuantas tierras se giraron (un clic perdido no es el fallo, pero se
 *       cuenta).</li>
 * </ul>
 *
 * <p>Sin piloto: la prueba retiene la prioridad todo el rato, asi que un paso
 * de fase no puede venir de la prueba. El color lo contesta el modo sin
 * ventana (el primero, blanco).
 */
public final class MoxChainCheck {

    private MoxChainCheck() {
    }

    private static final String[] LANDS = {"Island", "Swamp", "Mountain", "Plains", "Forest"};

    public static void run() {
        final int rounds = Integer.getInteger("neo.mox.rounds", 40);
        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean finished = new AtomicBoolean();
        final AtomicInteger moxNoMana = new AtomicInteger();
        final AtomicInteger phaseLeft = new AtomicInteger();
        final AtomicInteger moxNotTapped = new AtomicInteger();
        final AtomicInteger landsLost = new AtomicInteger();
        final AtomicInteger done = new AtomicInteger();
        final List<String> notes = new ArrayList<>();

        final Thread driver = new Thread(() -> {
            try {
                // Esperar a la fase principal con la prioridad.
                Game game = null;
                PlayerControllerHuman gc = null;
                Player me = null;
                for (int i = 0; i < 600 && me == null; i++) {
                    Thread.sleep(50L);
                    final NeoMatchUI ui = gui[0];
                    if (ui == null || ui.getGameView() == null || ui.getGameView().getGame() == null
                            || !(ui.getGameController() instanceof PlayerControllerHuman c)) {
                        continue;
                    }
                    final Game g = ui.getGameView().getGame();
                    final Player p = human(g);
                    if (p != null && g.getPhaseHandler().isPlayerTurn(p)
                            && g.getPhaseHandler().getPhase() == PhaseType.MAIN1
                            && c.getInputQueue().getInput() instanceof InputPassPriority) {
                        game = g;
                        gc = c;
                        me = p;
                    }
                }
                if (me == null) {
                    notes.add("no se llego a la fase principal");
                    return;
                }
                Thread.sleep(800L);
                for (int r = 1; r <= rounds; r++) {
                    // Todo destapado y la reserva vacia, desde el hilo del motor
                    // no: el motor esta parado esperando la prioridad.
                    for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
                        c.untap();
                    }
                    me.getManaPool().clearPool(false);
                    Thread.sleep(300L);

                    final List<CardView> order = new ArrayList<>();
                    for (final String n : LANDS) {
                        order.add(named(me, n).getView());
                    }
                    final Card mox = named(me, "Mox Opal");
                    order.add(mox.getView());
                    final PlayerControllerHuman ctrl = gc;
                    // Los seis clics de golpe, como dedos rapidos: cada uno se
                    // encola en el hilo de interfaz sin esperar al anterior.
                    // -Dneo.mox.gapMs: el hueco entre clics. 0 es imposible para un
                    // dedo (el motor tira los clics que llegan mientras atiende el
                    // anterior); 80-150 es alguien rapido de verdad.
                    final long gap = Long.getLong("neo.mox.gapMs", 150L);
                    for (final CardView cv : order) {
                        GuiBase.getInterface().invokeInEdtLater(() -> ctrl.selectCard(cv, null, null));
                        if (gap > 0) {
                            Thread.sleep(gap);
                        }
                    }
                    Thread.sleep(1500L);

                    final PhaseHandler ph = game.getPhaseHandler();
                    final boolean inMain = ph.isPlayerTurn(me) && ph.getPhase() == PhaseType.MAIN1;
                    int tappedLands = 0;
                    for (final String n : LANDS) {
                        if (named(me, n).isTapped()) {
                            tappedLands++;
                        }
                    }
                    final boolean moxTapped = mox.isTapped();
                    final int pool = me.getManaPool().totalMana();
                    // Cada tierra da 1 y el Mox 1: con todo girado deberia haber
                    // tantos como fuentes giradas.
                    final int expected = tappedLands + (moxTapped ? 1 : 0);
                    if (!inMain) {
                        phaseLeft.incrementAndGet();
                        notes.add("ronda " + r + ": salio de la fase principal (" + ph.getPhase() + ")");
                        break;
                    }
                    if (moxTapped && pool < expected) {
                        moxNoMana.incrementAndGet();
                        notes.add("ronda " + r + ": Mox girado, reserva " + pool + " de " + expected
                                + " (" + tappedLands + " tierras)");
                    }
                    if (!moxTapped) {
                        moxNotTapped.incrementAndGet();
                    }
                    landsLost.addAndGet(LANDS.length - tappedLands);
                    System.out.printf(Locale.ROOT, "  ronda %d: %d tierras, Mox %s, reserva %d, fase %s%n",
                            r, tappedLands, moxTapped ? "girado" : "sin girar", pool, ph.getPhase());
                    done.incrementAndGet();
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RuntimeException e) {
                notes.add("excepcion en la prueba: " + e);
                e.printStackTrace();
            } finally {
                finished.set(true);
                final NeoMatchUI ui = gui[0];
                if (ui != null) {
                    ui.nudgeAutoPlay();
                }
            }
        }, "moxchaincheck");
        driver.setDaemon(true);

        System.out.println("  Mesa: Mox Opal, tres Ornithopter y cinco tierras distintas; "
                + rounds + " rondas de seis clics seguidos.");
        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 300, null, false, ui -> {
            gui[0] = ui;
            // La prueba retiene la prioridad mientras dura: un paso de fase no
            // puede venir del piloto.
            ui.setAutoPlayHold(() -> !finished.get());
            driver.start();
        });

        System.out.printf(Locale.ROOT, "%n  rondas completas: %d de %d%n", done.get(), rounds);
        System.out.printf(Locale.ROOT, "  Mox girado SIN su mana: %d%n", moxNoMana.get());
        System.out.printf(Locale.ROOT, "  salidas de la fase principal: %d%n", phaseLeft.get());
        System.out.printf(Locale.ROOT, "  Mox sin girar (clic sin efecto): %d   tierras sin girar: %d%n",
                moxNotTapped.get(), landsLost.get());
        notes.forEach(n -> System.out.println("  - " + n));
        final boolean ok = done.get() == rounds && moxNoMana.get() == 0 && phaseLeft.get() == 0;
        System.out.println(ok ? "\n  1 bien, 0 mal" : "\n  0 bien, 1 mal");
        if (!ok) {
            throw new IllegalStateException("el Mox en cadena ha fallado");
        }
    }

    private static Card named(final Player me, final String name) {
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        throw new IllegalStateException("no esta " + name);
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
                "humanbattlefield=Mox Opal;Ornithopter;Ornithopter;Ornithopter;Island;Swamp;Mountain;Plains;Forest",
                "humanhand=Blaze;Stroke of Genius",
                "humanlibrary=Island;Island;Island;Island;Island",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=Island;Island;Island",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("moxchain", state, List.of());
    }
}
