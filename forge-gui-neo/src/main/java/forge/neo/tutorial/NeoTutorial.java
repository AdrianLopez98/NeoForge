package forge.neo.tutorial;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.game.event.GameEventAttackersDeclared;
import forge.game.event.GameEventBlockersDeclared;
import forge.game.event.GameEventCombatEnded;
import forge.game.phase.PhaseType;
import forge.neo.NeoSettings;
import forge.neo.tutorial.TutorialStep.Spot;

/**
 * El tutorial: tres mesas preparadas y los controles que se aprenden en cada una.
 *
 * <p><b>Por que existe.</b> Esta interfaz tiene gestos que no se descubren
 * solos: el click derecho para leer una carta, Ctrl+rueda para acercar la mesa,
 * clicar el rail de fases para pararte en el turno del rival, arrastrar de la
 * mano a la mesa. Ninguno esta escrito en ningun sitio dentro de la partida, y
 * quien no los conozca juega peor sin saber por que.
 *
 * <p><b>Por que una mesa preparada y no una partida normal.</b> Porque un
 * tutorial que dice "lanza una criatura" tiene que poder garantizar que hay una
 * criatura en la mano. Se usa el mismo dialecto que los puzzles de Forge
 * ({@code forge.game.GameState}), que es texto plano y ya esta probado; ver
 * {@link TutorialState}.
 *
 * <p><b>Que NO hace.</b> No ensenya a jugar a Magic: para eso estan las cartas,
 * que traen su texto escrito. Ensenya a manejar <i>esta</i> interfaz, que es lo
 * unico que el jugador no puede leer en ninguna parte.
 */
public final class NeoTutorial {

    private NeoTutorial() {
    }

    /** Lecciones terminadas, por id y separadas por comas. */
    private static final String DONE = "tutorialDone";

    /** Si el tutorial ya se ha ofrecido alguna vez en esta instalacion. */
    private static final String SEEN = "tutorialSeen";

    // ---------------------------------------------------------------
    // Progreso
    // ---------------------------------------------------------------

    /**
     * true la primerisima vez que se abre el juego.
     *
     * <p>Se apunta en <b>nuestro</b> fichero de ajustes, no en el de Forge: la
     * carpeta {@code %APPDATA%\Forge\} se comparte con la instalacion normal y
     * ahi no escribimos nada.
     */
    public static boolean isFirstRun() {
        if (NeoSettings.getBool(SEEN, false)) {
            return false;
        }
        // Y no basta con que falte la marca: la marca es de hoy, y quien lleva
        // meses jugando no tiene ninguna. Si ya hay fichero de ajustes, esta no
        // es su primera vez — se le ofrece el tutorial en el menu, como un modo
        // mas, y no poniendoselo delante.
        return !NeoSettings.exists();
    }

    /** El tutorial ya se ha ofrecido: no volver a saltar solo. */
    public static void markSeen() {
        NeoSettings.setBool(SEEN, true);
        NeoSettings.save();
    }

    /** La bienvenida sigue pendiente aunque ya haya fichero de ajustes. */
    private static final String PENDING = "welcomePending";

    /**
     * Deja la bienvenida a deber para el proximo arranque.
     *
     * <p>Hace falta por un caso concreto: elegir en la pantalla de idioma uno
     * distinto del que se cargo obliga a <b>volver a abrir el juego</b>, y para
     * entonces ya hay {@code neo.properties} — o sea que
     * {@link #isFirstRun()} dice que no, y el jugador se quedaria sin el
     * tutorial justamente por haber cambiado de idioma. Se apunta antes de
     * cerrar y se cobra al abrir.
     */
    public static void oweWelcome() {
        NeoSettings.setBool(PENDING, true);
        NeoSettings.save();
    }

    /** true una sola vez: si se debia una bienvenida, se da por dada. */
    public static boolean claimOwedWelcome() {
        if (!NeoSettings.getBool(PENDING, false)) {
            return false;
        }
        NeoSettings.setBool(PENDING, false);
        NeoSettings.save();
        return true;
    }

    public static boolean isDone(final String lessonId) {
        return done().contains(lessonId);
    }

    public static void markDone(final String lessonId) {
        final Set<String> ids = done();
        if (ids.add(lessonId)) {
            NeoSettings.set(DONE, String.join(",", ids));
            NeoSettings.save();
        }
    }

    /** Borra el progreso. Lo usa el boton de volver a empezar de la pantalla. */
    public static void reset() {
        NeoSettings.set(DONE, "");
        NeoSettings.save();
    }

    public static boolean allDone() {
        for (final TutorialLesson lesson : lessons()) {
            if (!lesson.isDone()) {
                return false;
            }
        }
        return true;
    }

    /** Cuantas lecciones llevas terminadas. */
    public static int doneCount() {
        int n = 0;
        for (final TutorialLesson lesson : lessons()) {
            if (lesson.isDone()) {
                n++;
            }
        }
        return n;
    }

    /** La primera leccion sin terminar, o la primera de todas si ya estan. */
    public static TutorialLesson nextLesson() {
        final List<TutorialLesson> all = lessons();
        for (final TutorialLesson lesson : all) {
            if (!lesson.isDone()) {
                return lesson;
            }
        }
        return all.get(0);
    }

    public static TutorialLesson lessonById(final String id) {
        for (final TutorialLesson lesson : lessons()) {
            if (lesson.getId().equalsIgnoreCase(id)) {
                return lesson;
            }
        }
        return null;
    }

    /** La leccion siguiente a esta, o {@code null} si era la ultima. */
    public static TutorialLesson after(final TutorialLesson lesson) {
        final List<TutorialLesson> all = lessons();
        for (int i = 0; i < all.size() - 1; i++) {
            if (all.get(i).getId().equals(lesson.getId())) {
                return all.get(i + 1);
            }
        }
        return null;
    }

    private static Set<String> done() {
        final Set<String> out = new LinkedHashSet<>();
        for (final String s : NeoSettings.get(DONE, "").split(",")) {
            if (!s.isBlank()) {
                out.add(s.trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------
    // Las cartas del tutorial, con su impresion FIJADA
    // ---------------------------------------------------------------

    /**
     * Por que cada carta lleva su {@code Set:}.
     *
     * <p>Sin decir la edicion, el motor coge la impresion que le da la gana - y
     * para las cartas viejas eso suele ser la primera, o sea marco antiguo, arte
     * oscuro y texto diminuto. Salio probando esto: unos Llanowar Elves de arte
     * raro que <i>alguien que empieza no puede ni leer</i>. En un tutorial eso
     * no es un detalle: la carta es lo que se esta ensenyando a mirar.
     *
     * <p>Asi que todas van fijadas a una impresion de marco moderno. Y por lo
     * mismo el 2/2 vainilla es un <b>Runeclaw Bear</b> y no un Grizzly Bears:
     * son la misma carta a efectos de juego, pero del oso solo hay impresiones
     * hasta 2007.
     *
     * <p>Ojo al tocar esto: si la edicion no tiene esa carta, el motor la
     * <b>se salta en silencio</b> y la leccion arranca con la zona vacia. Lo
     * caza {@code run.cmd tutorialcheck}, que comprueba nombre Y edicion.
     */
    private static final String FOREST = "Forest|Set:M21";
    private static final String MOUNTAIN = "Mountain|Set:M21";
    private static final String ISLAND = "Island|Set:M21";
    private static final String BEAR = "Runeclaw Bear|Set:M15";
    private static final String ELVES = "Llanowar Elves|Set:M19";
    private static final String BOLT = "Lightning Bolt|Set:M11";
    private static final String GROWTH = "Giant Growth|Set:M11";
    private static final String PYROMANCER = "Prodigal Pyromancer|Set:M11";
    private static final String DRAKE = "Wind Drake|Set:KLD";
    private static final String RADHA = "Radha, Heir to Keld|Set:DMR";

    /** Una linea de zona: {@code humanhand=Forest|Set:M21;...}. */
    private static String zone(final String key, final String... cards) {
        return key + "=" + String.join(";", cards);
    }

    /** Un mazo de N cartas alternando las que se le pasen. */
    private static String library(final int size, final String... cards) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                sb.append(";");
            }
            sb.append(cards[i % cards.length]);
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Las lecciones
    // ---------------------------------------------------------------

    public static List<TutorialLesson> lessons() {
        final List<TutorialLesson> out = new ArrayList<>();
        out.add(table());
        out.add(combat());
        out.add(tricks());
        return out;
    }

    /**
     * Leccion 1 - la mesa, y tu turno de principio a fin.
     *
     * <p>Tres tierras puestas, una tierra y dos criaturas en la mano: lo justo
     * para jugar una tierra, lanzar algo y ver como funciona el stack sin que
     * sobre nada en la mesa.
     */
    private static TutorialLesson table() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                zone("humanbattlefield", FOREST, FOREST, MOUNTAIN),
                zone("humanhand", FOREST, BEAR, ELVES, BOLT),
                "humanlibrary=" + library(12, FOREST, MOUNTAIN, FOREST, BEAR),
                zone("humangraveyard", GROWTH),
                "humanexile=",
                "humancommand=",
                zone("aibattlefield", ISLAND, ISLAND, ISLAND),
                "aihand=",
                "ailibrary=" + library(12, ISLAND),
                "aigraveyard=",
                "aiexile=",
                "aicommand=");

        final String k = "tutorial.mesa.";
        final List<TutorialStep> steps = Arrays.asList(
                TutorialStep.read(k + "welcome", Spot.NONE),
                TutorialStep.read(k + "you", Spot.SELF_BAR),
                TutorialStep.read(k + "rival", Spot.OPP_BAR),
                TutorialStep.gesture(k + "zoom", Spot.BOARD_SELF, Gesture.ZOOM_CARD),
                TutorialStep.gesture(k + "board", Spot.BOARD_OPP, Gesture.BOARD_ZOOM),
                TutorialStep.read(k + "phases", Spot.PHASES),
                TutorialStep.read(k + "button", Spot.SIDE),
                TutorialStep.watch(k + "land", Spot.HAND, TutorialProbe::playedLand),
                TutorialStep.read(k + "mana", Spot.BOARD_SELF),
                TutorialStep.watch(k + "cast", Spot.HAND, p -> p.castSpell("creature")),
                TutorialStep.read(k + "stack", Spot.STACK),
                TutorialStep.read(k + "sick", Spot.BOARD_SELF),
                TutorialStep.gesture(k + "zones", Spot.SELF_BAR, Gesture.ZONE_OPEN),
                TutorialStep.gesture(k + "log", Spot.LOG, Gesture.LOG_OPEN),
                TutorialStep.read(k + "end", Spot.NONE));

        return new TutorialLesson("mesa", state, steps);
    }

    /**
     * Leccion 2 - el combate.
     *
     * <p>Dos criaturas tuyas listas (la posicion las deja sin mareo de
     * invocacion) y un 2/2 volador enfrente, para que el rival bloquee a una y
     * la otra pase: las dos mitades del combate en un solo ataque.
     */
    private static TutorialLesson combat() {
        final List<String> state = Arrays.asList(
                "turn=5",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=20",
                zone("humanbattlefield", FOREST, FOREST, FOREST, MOUNTAIN, BEAR, ELVES),
                zone("humanhand", GROWTH, BOLT, BEAR),
                "humanlibrary=" + library(10, FOREST, MOUNTAIN),
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                zone("aibattlefield", ISLAND, ISLAND, ISLAND, DRAKE),
                "aihand=",
                "ailibrary=" + library(10, ISLAND),
                "aigraveyard=",
                "aiexile=",
                "aicommand=");

        final String k = "tutorial.combate.";
        final List<TutorialStep> steps = Arrays.asList(
                TutorialStep.read(k + "intro", Spot.BOARD_SELF),
                // Ojo: NO "estas en el paso de atacantes", que es cierto
                // durante un suspiro. Aqui se pregunta si la partida ha
                // LLEGADO a ese paso en tu turno. Con la version instantanea,
                // el jugador atacaba, ganaba el combate entero y el paso
                // seguia puesto.
                TutorialStep.watch(k + "go", Spot.SIDE,
                        p -> p.reachedOnMyTurn(PhaseType.COMBAT_DECLARE_ATTACKERS)),
                TutorialStep.watch(k + "declare", Spot.BOARD_SELF,
                        p -> p.declaredAttackers()
                                || p.is(GameEventAttackersDeclared.class)),
                TutorialStep.read(k + "arrows", Spot.BOARD_OPP),
                TutorialStep.watch(k + "blocks", Spot.BOARD_OPP,
                        p -> p.is(GameEventBlockersDeclared.class)
                                || p.is(GameEventCombatEnded.class)
                                || p.reachedOnMyTurn(PhaseType.COMBAT_DAMAGE)),
                TutorialStep.read(k + "damage", Spot.OPP_BAR),
                TutorialStep.read(k + "blocking", Spot.BOARD_SELF),
                TutorialStep.read(k + "why", Spot.SIDE),
                TutorialStep.read(k + "end", Spot.NONE));

        return new TutorialLesson("combate", state, steps);
    }

    /**
     * Leccion 3 - habilidades, instantaneos y el comandante.
     *
     * <p>El Prodigal Pyromancer es la carta del dia: tiene <b>una habilidad
     * activada con objetivo</b>, o sea que con ella se ensenya de golpe el
     * click sobre una carta de la mesa, el menu de habilidades, elegir objetivo
     * y la flecha que lo dibuja.
     */
    private static TutorialLesson tricks() {
        final List<String> state = Arrays.asList(
                "turn=7",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=18",
                zone("humanbattlefield", FOREST, FOREST, MOUNTAIN, MOUNTAIN,
                        PYROMANCER, ELVES),
                zone("humanhand", BOLT, GROWTH, BEAR),
                "humanlibrary=" + library(10, FOREST, MOUNTAIN),
                "humangraveyard=",
                "humanexile=",
                zone("humancommand", RADHA + "|IsCommander"),
                zone("aibattlefield", ISLAND, ISLAND, ISLAND, DRAKE),
                "aihand=",
                "ailibrary=" + library(10, ISLAND),
                "aigraveyard=",
                "aiexile=",
                "aicommand=");

        final String k = "tutorial.trucos.";
        final List<TutorialStep> steps = Arrays.asList(
                TutorialStep.read(k + "intro", Spot.BOARD_SELF),
                TutorialStep.watch(k + "activate", Spot.BOARD_SELF,
                        TutorialProbe::activatedAbility),
                TutorialStep.read(k + "targets", Spot.BOARD_OPP),
                TutorialStep.watch(k + "instant", Spot.HAND, p -> p.castSpell("instant")),
                TutorialStep.gesture(k + "stops", Spot.PHASES, Gesture.PHASE_STOP),
                TutorialStep.read(k + "commander", Spot.COMMAND),
                TutorialStep.read(k + "undo", Spot.NONE),
                // Los ajustes se ENSENYAN, no se cuentan. Ahi estan el mana
                // automatico, el auto-pass, cuanto frenar a la IA y el aviso
                // antes de dejar tu fase principal: cosas que cambian como se
                // juega y que no se descubren solas, porque el juego funciona
                // igual de bien sin tocarlas y nunca las nombra.
                //
                // El paso se cierra al ENTRAR EN AJUSTES, no al abrir el menu:
                // el menu de pausa se abre sin querer, y entonces el paso se
                // habria cerrado sin que nadie viera nada. Por eso todo lo que
                // hay que hacer — Escape y luego Ajustes — se dice en ESTE
                // paso: mientras el menu esta abierto, la banda del tutorial
                // queda detras de el y el paso siguiente no se puede leer.
                TutorialStep.gesture(k + "pause", Spot.NONE, Gesture.SETTINGS_OPEN),
                TutorialStep.read(k + "settings", Spot.NONE),
                TutorialStep.read(k + "end", Spot.NONE));

        return new TutorialLesson("trucos", state, steps);
    }
}
