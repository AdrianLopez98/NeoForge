package forge.neo.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * Comprueba, sin ventana, que una tierra de dos colores pregunta el color.
 *
 * <p><b>Por que hace falta un comprobador para esto.</b> El fallo solo aparece
 * con una mesa muy concreta — la que se reporto jugando: <i>dos montanyas y una
 * dual rojo/verde, y un hechizo que pide rojo y verde</i> — y con el pago de
 * mana a mano. Esperar a que salga en una partida contra la IA es cuestion de
 * suerte, y el sintoma es <b>silencioso</b>: no hay excepcion ni aviso, solo un
 * color que se elige solo y un pago que ya no se puede completar.
 *
 * <p>Aqui la mesa se monta a proposito, con el mismo dialecto de posicion que
 * el tutorial y los puzzles ({@code GameState}), y se comprueban las dos
 * mitades del arreglo:
 *
 * <ol>
 *   <li>que el asiento del jugador tiene <b>nuestro</b> controlador (o sea que
 *       {@code NeoMatchUI.openView} lo instalo de verdad),
 *   <li>que sobre la habilidad REAL de la dual, {@code ManaColor.widen}
 *       deshace el estrechamiento del motor y devuelve los <b>dos</b> colores,
 *   <li>y que no toca lo que no debe: una tierra basica y una habilidad que no
 *       es de mana combinado se quedan como estaban.
 * </ol>
 *
 * <p>Se ejecuta con {@code run.cmd manacheck}.
 */
public final class ManaCheck {

    private ManaCheck() {
    }

    private static int passed;
    private static int failed;

    /**
     * Si llegamos a ver la mesa puesta, aunque no encontraramos la dual.
     *
     * <p>Solo sirve para que el aviso final diga la verdad: una cosa es que la
     * posicion no arrancara y otra que arrancara sin la carta que se prueba.
     */
    private static boolean sawTable;

    /** Si la dual llego a estar en la mesa. */
    private static boolean sawDual;

    /** La dual del ejemplo: {@code {T}: Add {R} or {G}} (y ademas {C}). */
    private static final String DUAL = "Karplusan Forest|Set:10E";
    private static final String MOUNTAIN = "Mountain|Set:M21";
    private static final String FOREST = "Forest|Set:M21";
    /** Cuesta {R}{G}: hacen falta los DOS colores a la vez. */
    private static final String SPELL = "Burning-Tree Emissary|Set:GTC";

    public static void run() {
        passed = 0;
        failed = 0;
        sawTable = false;
        sawDual = false;

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());
        final AtomicBoolean checked = new AtomicBoolean(false);
        final List<String> notes = new ArrayList<>();

        // La partida se juega sola y en otro hilo; nosotros solo necesitamos
        // asomarnos una vez, cuando ya hay mesa. Igual que hace TutorialCheck.
        final NeoMatchUI[] gui = new NeoMatchUI[1];
        final AtomicBoolean alive = new AtomicBoolean(true);
        final Thread poller = new Thread(() -> {
            while (alive.get() && !checked.get()) {
                try {
                    inspect(gui[0], checked, notes);
                    Thread.sleep(100L);
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (final RuntimeException e) {
                    // Todavia no hay partida: se reintenta.
                }
            }
        }, "manacheck-poll");
        poller.setDaemon(true);

        NeoGame.playTutorial(lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 25, null, false, ui -> {
            gui[0] = ui;
            poller.start();
        });
        alive.set(false);

        System.out.println("  Mesa: " + state.getSummary());
        for (final String n : notes) {
            System.out.println("  " + n);
        }
        if (!checked.get()) {
            // Tres cosas distintas, y hace falta saber cual: que la posicion
            // no arrancara, que arrancara sin la carta que se prueba, o que la
            // carta estuviera y openView no llegara a instalar el controlador.
            // Las dos ultimas SI serian fallos de verdad.
            if (!sawTable) {
                fail("no se llego a mirar la partida (no arranco la posicion)");
            } else if (!sawDual) {
                fail("la mesa se puso pero la dual no llego a ella (nunca hubo mana combinado)");
            } else {
                fail("la dual estaba en la mesa pero openView nunca instalo nuestro controlador");
            }
        }

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de mana han fallado");
        }
    }

    /**
     * La mesa del ejemplo reportado.
     *
     * <p>Dos montanyas y la dual: si la dual se queda en rojo por su cuenta, el
     * verde no sale de ningun sitio y el hechizo de la mano no se puede pagar.
     */
    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                "humanbattlefield=" + MOUNTAIN + ";" + MOUNTAIN + ";" + DUAL,
                "humanhand=" + SPELL,
                "humanlibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=",
                "aihand=",
                "ailibrary=" + FOREST + ";" + FOREST + ";" + FOREST,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("mana", state, List.of());
    }

    private static void inspect(final NeoMatchUI ui, final AtomicBoolean checked,
                                final List<String> notes) {
        if (ui == null || ui.getGameView() == null) {
            return;
        }
        final Game game = ui.getGameView().getGame();
        if (game == null) {
            return;
        }
        Player me = null;
        for (final Player p : game.getPlayers()) {
            if (!p.isAI()) {
                me = p;
                break;
            }
        }
        if (me == null || me.getCardsIn(ZoneType.Battlefield).isEmpty()) {
            return;
        }
        sawTable = true;

        // --- la dual, con su habilidad de verdad ---
        //
        // ⚠️ Se busca ANTES de comprobar nada, y si todavia no esta se sale sin
        // apuntar nada para volver a intentarlo. La posicion NO se aplica de
        // golpe: se pone carta a carta, asi que hay un instante en el que las
        // dos montanyas ya estan en la mesa y la dual no. El sondeo se asomaba
        // ahi — la mesa ya no estaba vacia, que era la unica condicion — daba
        // "no hay ninguna habilidad de mana combinado", ponia checked a true y
        // no volvia a mirar: prueba en rojo por una CARRERA, no porque el
        // arreglo de la dual fallara.
        //
        // Medido el 30-08-2026, tres corridas seguidas del mismo binario: dos
        // en rojo y una en verde. Una red de seguridad que falla la mitad de
        // las veces no sirve como red — se deja de mirar.
        //
        // Si la dual no llega nunca, no se queda colgado: la partida tiene su
        // tope de tiempo y el aviso de run() lo dice con sawTable.
        SpellAbility combo = null;
        SpellAbility plain = null;
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            for (final SpellAbility sa : c.getManaAbilities()) {
                final AbilityManaPart mp = sa.getManaPart();
                if (mp != null && mp.isComboMana() && combo == null) {
                    combo = sa;
                } else if (mp != null && !mp.isComboMana() && plain == null
                        && c.getName().equals("Mountain")) {
                    plain = sa;
                }
            }
        }

        if (combo == null) {
            return;
        }
        sawDual = true;

        // Y lo MISMO con el asiento, por la misma razon y no por si acaso.
        //
        // Nuestro controlador lo instala openView, pero la posicion se aplica
        // ANTES, en el startGameHook. O sea que hay una segunda ventana — la
        // dual ya en la mesa y el controlador todavia sin poner — y el sondeo
        // tambien cae dentro de vez en cuando: salio "[MAL] El jugador tiene
        // nuestro controlador" con las cinco comprobaciones de la dual en
        // verde, que es la firma de una carrera y no la de un fallo.
        //
        // Jugando esa ventana no existe para nadie: ahi todavia no se reparte
        // prioridad, asi que no hay a quien preguntarle un color.
        if (!ManaColor.isInstalled(me)) {
            return;
        }

        // --- 1. el asiento ---
        check("El jugador tiene nuestro controlador (openView lo instalo)",
                ManaColor.isInstalled(me));

        // --- 2. la dual ---
        {
            notes.add("Dual encontrada: " + combo.getHostCard().getName()
                    + "  (" + combo.getManaPart().getComboColors(combo) + ")");

            // Esto es EXACTAMENTE lo que le llega hoy a chooseColor: el motor ha
            // cogido colorsNeeded[0] y ha dejado un solo color.
            final ColorSet narrowed = ColorSet.fromMask(MagicColor.RED);
            final ColorSet widened = ManaColor.widen(combo, narrowed);
            notes.add("El motor pasaba " + narrowed.countColors() + " color; ahora se preguntan "
                    + widened.countColors());
            check("La dual ofrece los DOS colores, no el que decidio el motor",
                    widened.countColors() == 2);
            check("Y son rojo y verde, los que la tierra sabe hacer de verdad",
                    widened.hasAnyColor(MagicColor.RED) && widened.hasAnyColor(MagicColor.GREEN));
            check("Con la lista ya completa no la toca",
                    ManaColor.widen(combo, widened).countColors() == 2);
        }

        // --- 3. lo que NO debe tocar ---
        if (plain != null) {
            check("Una montanya se queda con su unico color",
                    ManaColor.widen(plain, ColorSet.fromMask(MagicColor.RED)).countColors() == 1);
        }
        check("Sin habilidad (elegir color de una proteccion) no toca nada",
                ManaColor.widen(null, ColorSet.fromMask(MagicColor.RED)).countColors() == 1);

        checked.set(true);
    }

    private static void check(final String what, final boolean ok) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK" : "MAL", what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }

    private static void fail(final String what) {
        check(what, false);
    }
}
