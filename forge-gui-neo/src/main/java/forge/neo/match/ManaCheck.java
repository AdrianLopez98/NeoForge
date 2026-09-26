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
 * <p>Y la otra cara del mana combinado ({@link ManaCombo}): que <i>Selvala</i>
 * con una criatura de fuerza 6 reparta sus seis manas en UNA pregunta —y ya
 * repartida con lo que pide el coste— en vez de seis.
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
    /** {G},{T}: X manas en cualquier combinacion, X = la mayor fuerza. */
    private static final String SELVALA = "Selvala, Heart of the Wilds|Set:CN2";
    /** 6/6: Selvala da seis. */
    private static final String BIG = "Colossal Dreadmaw|Set:M21";

    /** El texto con el que pregunta el motor cada color del mana combinado. */
    private static String selectMana() {
        // Se pide cada vez: al cargar la clase el idioma puede no estar puesto.
        return forge.util.Localizer.getInstance().getMessage("lblSelectManaProduce");
    }

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
                "humanbattlefield=" + MOUNTAIN + ";" + MOUNTAIN + ";" + DUAL + ";" + SELVALA + ";" + BIG,
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
        SpellAbility selvala = null;
        boolean big = false;
        for (final Card c : me.getCardsIn(ZoneType.Battlefield)) {
            big |= c.getName().equals("Colossal Dreadmaw");
            for (final SpellAbility sa : c.getManaAbilities()) {
                final AbilityManaPart mp = sa.getManaPart();
                if (c.getName().startsWith("Selvala")) {
                    selvala = sa;
                } else if (mp != null && mp.isComboMana() && combo == null) {
                    combo = sa;
                } else if (mp != null && !mp.isComboMana() && plain == null
                        && c.getName().equals("Mountain")) {
                    plain = sa;
                }
            }
        }

        // La posicion se pone carta a carta (ver arriba): hasta que no esten
        // las tres que se prueban, se vuelve a mirar.
        if (combo == null || selvala == null || !big) {
            return;
        }
        // Y hasta que Selvala no cuente al 6/6. La fuerza se lee desde este
        // hilo con la partida viva, y entre dos lecturas llego a cambiar (visto
        // el 26-09-2026: una corrida de cuatro en rojo por eso).
        if (ManaCombo.batchable(selvala, selectMana()) != 6) {
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

        // --- 4. Selvala: X manas en UNA pregunta ---
        selvala(ui, selvala, combo, notes);

        checked.set(true);
    }

    /**
     * El reparto de Selvala.
     *
     * <p>La partida va en modo automatico, asi que {@code askManaCombo} no
     * ensenya el dialogo: devuelve la sugerencia. Eso es justo lo que se quiere
     * mirar — que sugerencia sale y que el motor recibe un color por llamada
     * sin preguntar mas.
     */
    private static void selvala(final NeoMatchUI ui, final SpellAbility selvala,
                                final SpellAbility dual, final List<String> notes) {
        final String msg = selectMana();
        final int x = ManaCombo.batchable(selvala, msg);
        notes.add("Selvala da " + x + " (la mayor fuerza en mesa)");
        check("Selvala reparte seis manas: se junta en una pregunta", x == 6);
        check("Una dual (un mana) no se junta", ManaCombo.batchable(dual, msg) <= 1);
        check("Otro chooseColor (una proteccion) no se junta",
                ManaCombo.batchable(selvala, "Choose a color") == 0);

        final ColorSet all = ColorSet.fromMask(MagicColor.ALL_COLORS);
        final ColorSet green = ColorSet.fromMask(MagicColor.GREEN);

        // Pagando {3}{G}{U}: G y U, y el sobrante al verde (el color de Selvala).
        final var paying = ManaCombo.suggest(6, all, cost("3 G U"), null, green);
        notes.add("Pagando {3}{G}{U} sugiere " + paying);
        check("Pagando {3}{G}{U}: 1 azul y 5 verdes",
                n(paying, MagicColor.Color.BLUE) == 1 && n(paying, MagicColor.Color.GREEN) == 5
                        && sum(paying) == 6);

        // Con {W}{W}{B} pide mas blanco: el sobrante va al blanco.
        final var white = ManaCombo.suggest(6, all, cost("1 W W B"), null, green);
        check("Pagando {1}{W}{W}{B}: el sobrante al color que mas pide (5 W, 1 B)",
                n(white, MagicColor.Color.WHITE) == 5 && n(white, MagicColor.Color.BLACK) == 1);

        // Hibrido: al color que ya se pide.
        final var hybrid = ManaCombo.suggest(3, all, cost("W/U U"), null, green);
        check("Un hibrido {W/U} con {U} al lado se paga en azul",
                n(hybrid, MagicColor.Color.BLUE) == 3);

        // Mas coste que mana: lo que haya, sin pasarse.
        final var tight = ManaCombo.suggest(2, all, cost("R R G"), null, green);
        check("Si no llega para todo, no se pasa del total", sum(tight) == 2);

        // Sin coste, con un reparto anterior: el mismo.
        final java.util.Map<MagicColor.Color, Integer> before = new java.util.LinkedHashMap<>();
        before.put(MagicColor.Color.RED, 3);
        before.put(MagicColor.Color.GREEN, 3);
        final var again = ManaCombo.suggest(6, all, null, before, green);
        check("Sin coste: como la ultima vez (3 R, 3 G)",
                n(again, MagicColor.Color.RED) == 3 && n(again, MagicColor.Color.GREEN) == 3);

        // Y sin nada: todo a su color.
        final var fresh = ManaCombo.suggest(6, all, null, null, green);
        check("Sin coste ni historia: todo verde", n(fresh, MagicColor.Color.GREEN) == 6);

        // La cola: una pregunta y cinco respuestas calladas.
        final ManaCombo combo = new ManaCombo();
        // Con el total fijo (6), no releido: la cola es lo que se prueba aqui.
        final Byte first = combo.start(ui, null, selvala, all, 6);
        int answered = first == null ? 0 : 1;
        while (combo.next(selvala, all) != null) {
            answered++;
        }
        check("El motor recibe sus seis colores con una sola pregunta", answered == 6);
        check("Y la cola queda vacia", combo.next(selvala, all) == null);

        // Una cola a medias no se la come otra habilidad.
        combo.start(ui, null, selvala, all, 6);
        check("Una cola a medias no contesta a otra habilidad", combo.next(dual, all) == null);
        check("...y se tira", combo.next(selvala, all) == null);
    }

    private static forge.game.mana.ManaCostBeingPaid cost(final String c) {
        return new forge.game.mana.ManaCostBeingPaid(new forge.card.mana.ManaCost(c));
    }

    private static int n(final java.util.Map<MagicColor.Color, Integer> m, final MagicColor.Color c) {
        return m.getOrDefault(c, 0);
    }

    private static int sum(final java.util.Map<MagicColor.Color, Integer> m) {
        int t = 0;
        for (final int v : m.values()) {
            t += v;
        }
        return t;
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
