package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.ai.ComputerUtilMana;
import forge.ai.PlayerControllerAi;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Las fuentes de mana que el planificador del motor no sabe ver.
 *
 * <p>Comprueba tres cosas distintas, y las tres hacen falta:
 *
 * <ol>
 *   <li><b>Que el hueco del motor sigue ahi.</b> Con Bosque + Pradera
 *       Hierbasol y un coste de dos colores, {@code getManaSourcesToPayCost}
 *       devuelve {@code null} (o sea que el boton "Auto" no se enciende) y
 *       {@code canPayManaCost} dice {@code false}. Con Bosque + Llanura, la
 *       misma mesa por lo demas, las dos cosas van bien. <b>El control no es
 *       decorativo</b>: sin el, un {@code null} podria venir de la mesa o del
 *       coste y no del hueco. Si algun dia Card-Forge lo arregla, esta
 *       comprobacion se pone en rojo y sabremos que nuestro apanyo ya sobra.
 *   <li><b>Que lo reconocemos.</b> {@code FilterSources} encuentra la Pradera
 *       y <b>no</b> el Bosque ni la Llanura: el criterio tiene que ser
 *       estrecho, o nos pondriamos a adivinar donde el motor si acierta.
 *</ol>
 *
 * <p><b>Lo que este comprobador NO prueba, y por que.</b> El bucle entero del
 * apanyo — activar la fuente, que el motor pida su {@code {1}}, pagarlo y
 * completar el coste — no se puede comprobar sin ventana: en modo automatico
 * cada fase dura milisegundos, asi que no hay hueco en el que lanzar un
 * hechizo sea legal, y el pago no llega a empezar nunca. Se intento sondeando
 * cada 120 ms y lanzando el hechizo a mano con
 * {@code playChosenSpellAbility}, y no se caza. Eso se prueba <b>jugando</b>,
 * con {@code run.cmd ui --live --filter-land} y el interruptor de Ajustes
 * encendido (o {@code -Dneo.blindMana=true}).
 *
 * <p>Por eso el interruptor viene <b>apagado</b>: lo que si esta demostrado es
 * el hueco del motor y que lo reconocemos bien, no que el apanyo lo resuelva.
 *
 * <p>Se ejecuta con {@code run.cmd filtercheck}.
 */
public final class FilterCheck {

    private FilterCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String PRAIRIE = "Sungrass Prairie|Set:TDC";
    private static final String FOREST = "Forest|Set:M21";
    private static final String PLAINS = "Plains|Set:M21";

    /** Cuesta verde y blanco: hacen falta los dos colores a la vez. */
    private static final String SPELL = "Watchwolf|Set:RAV";
    private static final String SPELL_NAME = "Watchwolf";

    public static void run() {
        passed = 0;
        failed = 0;

        final Probe filter = probe("filtro", FOREST, PRAIRIE, false);
        final Probe control = probe("control", FOREST, PLAINS, false);

        check(filter.seen, "la posicion del filtro arranco");
        check(control.seen, "la posicion de control arranco");
        check(!filter.autoAvailable,
                "el motor NO enciende el Auto con la tierra de filtro"
                + " (si esto falla, Card-Forge lo ha arreglado y sobra el apanyo)");
        check(!filter.canPay, "el motor cree que no puede pagar con la tierra de filtro");
        check(control.autoAvailable, "y en cambio SI lo enciende con dos tierras normales");
        check(control.canPay, "y cree que si puede pagar");

        check(filter.blind.contains("Sungrass Prairie"),
                "FilterSources encuentra la Pradera Hierbasol");
        check(filter.blind.size() == 1,
                "y no senyala nada mas de esa mesa (encontro: " + filter.blind + ")");
        check(control.blind.isEmpty(),
                "y no senyala una tierra normal (encontro: " + control.blind + ")");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(
                    failed + " comprobacion(es) de fuentes ciegas han fallado");
        }
    }

    /** Lo que se saca de una posicion. */
    private static final class Probe {
        private boolean seen;
        private boolean autoAvailable;
        private boolean canPay;
        private boolean castTheSpell;
        private final List<String> blind = new ArrayList<>();
    }

    private static Probe probe(final String title, final String landA, final String landB,
                               final boolean withFix) {
        final Probe out = new Probe();
        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicBoolean alive = new AtomicBoolean(true);
        final NeoMatchUI[] gui = new NeoMatchUI[1];

        NeoMatchUI.setBlindSourceFallback(withFix);

        final Thread poller = new Thread(() -> {
            while (alive.get()) {
                try {
                    if (!done.get()) {
                        inspect(gui[0], done, out);
                    }
                    watchForCast(gui[0], out);
                    Thread.sleep(120L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // Todavia no hay partida, o el motor esta a medias: se reintenta.
                }
            }
        }, "filtercheck-" + title);
        poller.setDaemon(true);

        final TutorialLesson lesson = position(landA, landB);
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()),
                NeoMatchUI.Mode.AUTO_PLAY, 30, null, withFix, ui -> {
                    gui[0] = ui;
                    poller.start();
                });
        alive.set(false);
        NeoMatchUI.setBlindSourceFallback(false);
        return out;
    }

    private static TutorialLesson position(final String landA, final String landB) {
        return new TutorialLesson("filtro", Arrays.asList(
                "turn=3", "activeplayer=human", "activephase=MAIN1",
                "humanlife=20", "ailife=20",
                "humanbattlefield=" + landA + ";" + landB,
                "humanhand=" + SPELL,
                "humanlibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=", "aihand=",
                "ailibrary=" + PLAINS + ";" + PLAINS + ";" + PLAINS,
                "aigraveyard=", "aiexile=", "aicommand="), List.of());
    }

    /** Ha llegado el hechizo a la mesa: es lo que prueba que el pago se completo. */
    private static void watchForCast(final NeoMatchUI ui, final Probe out) {
        final Player me = human(ui);
        if (me == null) {
            return;
        }
        for (final Card c : me.getCardsIn(ZoneType.Battlefield).threadSafeIterable()) {
            if (SPELL_NAME.equals(c.getName())) {
                out.castTheSpell = true;
            }
        }
    }

    private static Player human(final NeoMatchUI ui) {
        if (ui == null || ui.getGameView() == null) {
            return null;
        }
        final Game game = ui.getGameView().getGame();
        if (game == null) {
            return null;
        }
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                return p;
            }
        }
        return null;
    }

    private static void inspect(final NeoMatchUI ui, final AtomicBoolean done, final Probe out) {
        final Player me = human(ui);
        if (me == null || me.getCardsIn(ZoneType.Battlefield).isEmpty()
                || me.getCardsIn(ZoneType.Hand).isEmpty()) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        Card spell = null;
        for (final Card c : me.getCardsIn(ZoneType.Hand)) {
            if (SPELL_NAME.equals(c.getName())) {
                spell = c;
            }
        }
        if (spell == null || spell.getFirstSpellAbility() == null) {
            return;
        }
        final SpellAbility sa = spell.getFirstSpellAbility();
        sa.setActivatingPlayer(me);

        // Exactamente lo que hace InputPayMana para decidir si enciende "Auto".
        final ManaCostBeingPaid cost = new ManaCostBeingPaid(spell.getManaCost());
        final Object[] sources = new Object[1];
        final boolean[] can = new boolean[1];
        me.runWithController(() -> {
            sources[0] = ComputerUtilMana.getManaSourcesToPayCost(cost, sa, me, false);
            can[0] = ComputerUtilMana.canPayManaCost(sa, me, 0, false);
        }, new PlayerControllerAi(game, me, me.getOriginalLobbyPlayer()));

        out.autoAvailable = sources[0] != null;
        out.canPay = can[0];
        for (final Card c : FilterSources.of(game, me)) {
            out.blind.add(c.getName());
        }
        out.seen = true;
        done.set(true);
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
