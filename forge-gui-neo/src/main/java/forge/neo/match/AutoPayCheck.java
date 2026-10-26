package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;
import forge.gamemodes.match.input.InputPassPriority;
import forge.gamemodes.match.input.InputPayMana;
import forge.gui.GuiBase;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;
import forge.player.PlayerControllerHuman;

/**
 * <b>Reproduccion</b> de un informe de Discord (26-10-2026, Android): <i>"it
 * did it with GUTTERSNIPE ... auto pay is where it is messing up"</i>. Un
 * hechizo de 3 pagado con "Auto" que solo gira 2 Montanyas y deja una {R} en
 * la reserva (la captura era Hammer of Bogardan).
 *
 * <p>No es de la bateria. Hace de jugador por el MISMO camino que la mesa
 * ({@code selectCard} desde el hilo de interfaz, y "Auto" con
 * {@code selectButtonOk}), muchas rondas, y en cada una cuenta tierras giradas
 * y reserva: con cinco Montanyas, Guttersnipe ({2}{R}) tiene que dejar 3
 * giradas y la reserva vacia. Tres maneras:
 *
 * <ul>
 *   <li><b>auto</b>: lanzar y "Auto".</li>
 *   <li><b>a mano + auto</b>: girar una Montanya antes (una {R} flotando),
 *       lanzar y "Auto": 2 giradas mas, reserva vacia.</li>
 *   <li><b>auto + undo + auto</b>: lanzar, "Auto", deshacer y volver a
 *       lanzar: lo de la captura ("Undo + recast").</li>
 * </ul>
 *
 * <p>Se ejecuta con {@code run.cmd autopaycheck} ({@code -Dneo.autopay.rounds=N}).
 */
public final class AutoPayCheck {

    private AutoPayCheck() {
    }

    private static final String MOUNTAIN = "Mountain|Set:M21";
    private static final String SPELL = "Guttersnipe|Set:M21";

    public static void run() {
        final int rounds = Integer.getInteger("neo.autopay.rounds", 10);
        final List<String> state = Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=500", "ailife=20",
                // Vedalken Orrery: la partida avanza sola entre ronda y ronda, y asi se
                // puede lanzar en cualquier paso. Spellbook: con 30 en la mano, la limpieza
                // pediria descartar y la partida se quedaria ahi.
                "humanbattlefield=" + (MOUNTAIN + ";").repeat(4) + MOUNTAIN + ";Vedalken Orrery|Set:CNS;Spellbook|Set:M10;Fastbond|Set:LEA",
                "humanhand=" + (SPELL + ";").repeat(5 * rounds) + (MOUNTAIN + ";").repeat(rounds) + SPELL,
                "humanlibrary=" + (MOUNTAIN + ";").repeat(4) + MOUNTAIN,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + (MOUNTAIN + ";").repeat(4) + MOUNTAIN,
                "aigraveyard=", "aiexile=", "aicommand=");
        final TutorialLesson lesson = new TutorialLesson("autopay", state, List.of());
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean finished = new AtomicBoolean();
        final List<String> bad = new ArrayList<>();
        final int[] done = new int[1];

        final Thread driver = new Thread(() -> {
            try {
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
                    for (final Player p : g.getPlayers()) {
                        if (!p.isAI() && g.getPhaseHandler().isPlayerTurn(p)
                                && c.getInputQueue().getInput() instanceof InputPassPriority) {
                            game = g;
                            gc = c;
                            me = p;
                        }
                    }
                }
                if (me == null) {
                    bad.add("no se llego a la fase principal");
                    return;
                }
                CTRL[0] = gc;
                Thread.sleep(800L);
                final String[] ways = {"auto", "a mano + auto", "auto + undo + auto", "auto x2", "android"};
                // -Dneo.autopay.only="auto x2": solo esa manera.
                final String only = System.getProperty("neo.autopay.only");
                for (int r = 1; r <= rounds; r++) {
                    for (final String way : ways) {
                        if (only != null && !only.equals(way)) {
                            continue;
                        }
                        reset(me);
                        Thread.sleep(300L);
                        final PlayerControllerHuman ctrl = gc;
                        if (way.startsWith("a mano")) {
                            final Card land = me.getCardsIn(ZoneType.Battlefield).stream().filter(Card::isLand).findFirst().get();
                            edt(() -> ctrl.selectCard(land.getView(), null, null));
                            Thread.sleep(400L);
                        }
                        for (int i = 0; i < 200 && !(ctrl.getInputQueue().getInput() instanceof InputPassPriority); i++) {
                            Thread.sleep(25L);
                        }
                        if (way.equals("android")) {
                            if (androidRound(ctrl, me)) {
                                report(r, way, me, game, bad);
                            } else {
                                bad.add("ronda " + r + ", android: no habia Montanya en la mano");
                            }
                            continue;
                        }
                        if (way.equals("auto x2")) {
                            castWithDoubleAuto(ctrl, me);
                            report(r, way, me, game, bad);
                            continue;
                        }
                        castWithAuto(ctrl, me);
                        if (way.contains("undo")) {
                            edt(ctrl::undoLastAction);
                            Thread.sleep(600L);
                            final String afterUndo = count(me);
                            castWithAuto(ctrl, me);
                            report(r, way + " (tras el undo: " + afterUndo + ")", me, game, bad);
                        } else {
                            report(r, way, me, game, bad);
                        }
                    }
                    done[0]++;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final RuntimeException e) {
                bad.add("excepcion en la prueba: " + e);
                e.printStackTrace();
            } finally {
                finished.set(true);
                final NeoMatchUI ui = gui[0];
                if (ui != null) {
                    ui.nudgeAutoPlay();
                }
            }
        }, "autopaycheck");
        driver.setDaemon(true);

        System.out.println("  Mesa: cinco Montanyas y Guttersnipe ({2}{R}); " + rounds + " rondas de tres maneras.");
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 300, null,
                false, ui -> {
                    gui[0] = ui;
                    ui.setAutoPlayHold(() -> !finished.get());
                    driver.start();
                });

        System.out.printf(Locale.ROOT, "%n  rondas completas: %d de %d%n", done[0], rounds);
        bad.forEach(n -> System.out.println("  - " + n));
        System.out.println(bad.isEmpty() ? "\n  todo cuadra" : "\n  " + bad.size() + " pagos que no cuadran");
    }

    /**
     * Todo destapado y la reserva vacia. Los Guttersnipe lanzados se quedan en
     * el stack (la prueba retiene la prioridad): hay uno por ronda en la mano.
     */
    private static void reset(final Player me) {
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            c.untap();
        }
        me.getManaPool().clearPool(false);
    }

    private static void castWithAuto(final PlayerControllerHuman ctrl, final Player me) throws InterruptedException {
        Card spell = null;
        for (final Card c : me.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals("Guttersnipe")) {
                spell = c;
            }
        }
        if (spell == null) {
            return;
        }
        for (int i = 0; i < 200 && !(ctrl.getInputQueue().getInput() instanceof InputPassPriority); i++) {
            Thread.sleep(25L);
        }
        final Card toCast = spell;
        edt(() -> ctrl.selectCard(toCast.getView(), null, null));
        for (int i = 0; i < 40 && !(ctrl.getInputQueue().getInput() instanceof InputPayMana); i++) {
            Thread.sleep(25L);
        }
        if (ctrl.getInputQueue().getInput() instanceof InputPayMana) {
            edt(ctrl::selectButtonOk);
        }
        Thread.sleep(700L);
    }

    /**
     * Lo del informe: el pago automatico pulsa "Auto" y el jugador tambien,
     * casi a la vez. En Android las dos salen del hilo de entrada, no del de
     * interfaz: aqui una de cada, separadas por {@code -Dneo.autopay.gapMs}.
     */
    private static void castWithDoubleAuto(final PlayerControllerHuman ctrl, final Player me)
            throws InterruptedException {
        Card spell = null;
        for (final Card c : me.getCardsIn(ZoneType.Hand)) {
            if (c.getName().equals("Guttersnipe")) {
                spell = c;
            }
        }
        if (spell == null) {
            return;
        }
        for (int i = 0; i < 200 && !(ctrl.getInputQueue().getInput() instanceof InputPassPriority); i++) {
            Thread.sleep(25L);
        }
        final Card toCast = spell;
        edt(() -> ctrl.selectCard(toCast.getView(), null, null));
        for (int i = 0; i < 40 && !(ctrl.getInputQueue().getInput() instanceof InputPayMana); i++) {
            Thread.sleep(25L);
        }
        if (ctrl.getInputQueue().getInput() instanceof InputPayMana) {
            edt(ctrl::selectButtonOk);
            Thread.sleep(Long.getLong("neo.autopay.gapMs", 5L));
            final Thread other = new Thread(() -> {
                try {
                    ctrl.selectButtonOk();
                } catch (final RuntimeException e) {
                    System.out.println("  (segundo Auto: " + e + ")");
                }
            }, "player-input");
            other.start();
        }
        Thread.sleep(900L);
        // Si se quedo pidiendo mana, un tercer "Auto", como el jugador.
        if (ctrl.getInputQueue().getInput() instanceof InputPayMana) {
            System.out.println("  (se quedo pidiendo mana: " + count(me) + "; tercer Auto)");
            edt(ctrl::selectButtonOk);
            Thread.sleep(700L);
        }
    }

    /**
     * Como Android: los gestos NO salen del hilo de interfaz sino de otro (el
     * de entrada), sin esperar. Jugar una Montanya (Fastbond deja jugar una por
     * ronda), lanzar Guttersnipe en el acto y "Auto" en cuanto aparece el pago.
     * El hueco entre la tierra y el hechizo, {@code -Dneo.autopay.landGapMs}.
     */
    private static boolean androidRound(final PlayerControllerHuman ctrl, final Player me)
            throws InterruptedException {
        Card land = null;
        Card spell = null;
        for (final Card c : me.getCardsIn(ZoneType.Hand)) {
            if (c.isLand() && land == null) {
                land = c;
            } else if (c.getName().equals("Guttersnipe") && spell == null) {
                spell = c;
            }
        }
        if (land == null || spell == null) {
            return false;
        }
        for (int i = 0; i < 200 && !(ctrl.getInputQueue().getInput() instanceof InputPassPriority); i++) {
            Thread.sleep(10L);
        }
        ctrl.selectCard(land.getView(), null, null);
        Thread.sleep(Long.getLong("neo.autopay.landGapMs", 0L));
        // La tierra deja su recalculo en el hilo de interfaz; el hechizo sale ya.
        for (int i = 0; i < 400 && !(ctrl.getInputQueue().getInput() instanceof InputPassPriority); i++) {
            Thread.sleep(1L);
        }
        ctrl.selectCard(spell.getView(), null, null);
        for (int i = 0; i < 2000 && !(ctrl.getInputQueue().getInput() instanceof InputPayMana); i++) {
            Thread.sleep(1L);
        }
        if (ctrl.getInputQueue().getInput() instanceof InputPayMana) {
            ctrl.selectButtonOk();
        }
        Thread.sleep(900L);
        if (ctrl.getInputQueue().getInput() instanceof InputPayMana) {
            System.out.println("  (se quedo pidiendo mana: " + count(me) + ")");
            ctrl.selectButtonOk();
            Thread.sleep(700L);
        }
        return true;
    }

    private static void report(final int round, final String way, final Player me, final Game game,
            final List<String> bad) {
        final long tapped = tapped(me);
        final int red = me.getManaPool().getAmountOfColor(MagicColor.RED);
        final long inHand = me.getCardsIn(ZoneType.Hand).stream()
                .filter(c -> c.getName().equals("Guttersnipe")).count();
        final boolean onStack = stackBefore[0] < 0 || inHand < stackBefore[0];
        stackBefore[0] = inHand;
        final Object input = CTRL[0] == null ? null : CTRL[0].getInputQueue().getInput();
        final String line = String.format(Locale.ROOT, "  ronda %d, %s: %s, Guttersnipe %s | %s %s turno de %s | entrada %s",
                round, way, count(me), onStack ? "lanzado" : "NO lanzado", game.getPhaseHandler().getTurn(),
                game.getPhaseHandler().getPhase(), game.getPhaseHandler().getPlayerTurn().getName(),
                input == null ? null : input.getClass().getSimpleName());
        System.out.println(line);
        if (!onStack || tapped != 3 || red != 0) {
            bad.add(line.trim());
        }
    }

    private static final PlayerControllerHuman[] CTRL = new PlayerControllerHuman[1];

    /** Guttersnipe en la mano tras la ronda anterior (-1: primera). */
    private static final long[] stackBefore = {-1};

    private static String count(final Player me) {
        return tapped(me) + " giradas, {R} en reserva " + me.getManaPool().getAmountOfColor(MagicColor.RED);
    }

    private static long tapped(final Player me) {
        return me.getCardsIn(ZoneType.Battlefield).stream().filter(c -> c.isLand() && c.isTapped()).count();
    }

    private static void edt(final Runnable r) {
        GuiBase.getInterface().invokeInEdtLater(r);
    }
}
