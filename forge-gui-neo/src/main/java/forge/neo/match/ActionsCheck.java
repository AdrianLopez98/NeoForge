package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * El fallo de Coram, sin ventana: que la partida SOBREVIVA.
 *
 * <p><b>Por que hace falta un comprobador para esto.</b> Porque el sintoma no
 * es un fallo visible, es que <b>el juego se cierra entero</b> — sin ganador,
 * sin perdedor y sin una linea en pantalla — y porque la mesa que lo provoca
 * necesita <b>tres cosas a la vez</b> que no se dan por casualidad. El
 * mecanismo esta en {@link SafeActions}; lo corto es:
 *
 * <ul>
 *   <li><b>Coram, the Undertaker</b> en la mesa. Su permiso de jugar desde los
 *       cementerios lleva {@code ThisTurnEnteredFrom_Library}, y esa propiedad
 *       la resuelve el motor con {@code card.getZone()} <b>sin comprobar el
 *       null</b>.</li>
 *   <li><b>Cartas cayendo de la biblioteca al cementerio este turno</b>, o sea
 *       una molida — que es literalmente el disparo de ataque de Coram. Sin
 *       nada en los cementerios la estatica ni se aplica: medido, la misma
 *       mesa sin molino juega <b>30 turnos y cero fallos</b>.</li>
 *   <li>una <b>carta de dos caras</b> entre las que recorre el barrido. Es la
 *       que fabrica la copia con la zona nula: para saber que se puede hacer
 *       con la otra cara, el motor se hace una <b>copia LKI</b>
 *       ({@code Spell.getAlternateHost}), y una copia LKI tiene
 *       {@code savedLastKnownZone} puesto y {@code currentZone} a
 *       <b>null</b>.</li>
 * </ul>
 *
 * <p>Y por encima de las tres, el <b>barrido de acciones</b> encendido, que es
 * quien va preguntando carta por carta que se puede hacer con ella. Ese lo
 * encendemos nosotros ({@code NeoGame.applyEnginePrefs}) para los resaltados y
 * el auto-pass.
 *
 * <p><b>Henzie no hace falta.</b> El informe venia con el de comandante y la
 * primera version de esta prueba lo daba por imprescindible; se probo la misma
 * mesa sin el y falla igual (25 rescates). Su {@code AffectedZone$ Stack}
 * fabrica <i>otra</i> copia LKI por otro camino de {@code GameActionUtil}, y
 * por eso parecia el culpable. Queda escrito para que nadie vuelva a meterlo
 * "por si acaso".
 *
 * <p><b>Lo que se comprueba, y por que en ese orden.</b> No basta con que la
 * partida termine bien: si el barrido no llegara a ejecutarse — porque falta
 * una preferencia, porque la posicion no arranca, porque el controlador no se
 * sento — la partida <b>tambien</b> terminaria bien, y la prueba pasaria en
 * verde sin haber tocado ni una vez el codigo que revienta. Es el mismo fallo
 * que tuvo la sonda vieja de Ascenso (ver las trampas conocidas). Asi que se exige lo
 * contrario: <b>que el fallo del motor se haya producido de verdad</b>
 * ({@code SafeActions.rescues() > 0}) <b>y ademas</b> que la partida siguiera
 * viva despues.
 *
 * <p>De ahi sale gratis el otro aviso que interesa: si algun dia Card-Forge
 * pone el {@code getLastKnownZone()} que falta, esta prueba se pone en rojo
 * diciendo <i>"ya no hace falta el apanyo"</i>, en vez de dejarnos cargando
 * codigo muerto para siempre.
 *
 * <p><b>La contraprueba</b> no se automatiza porque exige otro proceso (la
 * bandera se lee una vez, al cargar la clase). A mano:
 * {@code run.cmd actionscheck -Dneo.actions.guard=false}, y lo que hace es
 * <b>quedarse colgado</b>: el hilo del motor muere, la partida no termina
 * nunca y hay que matar el proceso. Que no acabe <i>es</i> el resultado — y es
 * literalmente lo que vio el jugador, una mesa sin resultado. Si pasara en
 * verde, el blindaje no estaria blindando nada.
 *
 * <p>Se ejecuta con {@code run.cmd actionscheck}.
 */
public final class ActionsCheck {

    private ActionsCheck() {
    }

    private static int passed;
    private static int failed;

    /** La carta del informe, con su impresion fijada. */
    private static final String CORAM = "Coram, the Undertaker|Set:MH3";

    /** Tierras: sin mana el barrido descarta las cartas antes de mirarlas. */
    private static final String FOREST = "Forest|Set:M21";
    private static final String SWAMP = "Swamp|Set:M21";
    private static final String MOUNTAIN = "Mountain|Set:M21";

    /**
     * La carta de dos caras, que es la que fabrica la copia con la zona nula.
     *
     * <p>Tiene que tener <b>otra cara</b> — modal, aventura, transformar o
     * partida: es lo unico que hace que el motor se copie la carta para mirar
     * el otro lado. Con una criatura normal en su sitio, la misma mesa juega 30
     * turnos sin un solo fallo. Medido, no supuesto.
     */
    private static final String TWO_FACED = "Malakir Rebirth|Set:ZNR";

    /**
     * La molida, que es lo que el jugador hacia con Coram.
     *
     * <p>Es el ingrediente que menos se ve venir y el que esta medido: sin el,
     * la misma mesa juega <b>30 turnos sin un fallo</b>. La estatica de Coram
     * mira el <b>cementerio</b> ({@code AffectedZone$ Graveyard}), asi que con
     * los cementerios vacios no llega a filtrar nada.
     *
     * <p>Aqui la hace sola una carta con disparo de mantenimiento, para no
     * tener que declarar un ataque desde un comprobador sin ventana. Para el
     * jugador el molino era el propio disparo de ataque de Coram — de ahi que
     * le pasara <b>las dos veces que ataco con el</b>.
     */
    private static final String MILL = "Dreamborn Muse|Set:LRW";

    /**
     * Cuantas copias de la carta de dos caras van en la biblioteca.
     *
     * <p>La propiedad sale antes por {@code enteredThisTurn()}, asi que una
     * carta colocada por la posicion y quieta en la mano no reproduce nada:
     * tiene que estar <b>recien robada o recien molida</b>. Con la biblioteca
     * llena de ella, cada turno entra una nueva por las dos vias.
     *
     * <p>La primera version de esta prueba la puso en la mano al montar la
     * mesa, jugo diez turnos y no fallo nunca. Se vio porque el comprobador
     * exige que el fallo se PRODUZCA: si solo hubiera mirado que la partida
     * termina bien, habria pasado en verde.
     */
    private static final int LIBRARY_COPIES = 12;

    public static void run() {
        passed = 0;
        failed = 0;
        SafeActions.resetRescues();

        // Sin ventana no se pasa por applyEnginePrefs (que solo corre en modo
        // HUMAN), asi que el barrido estaria APAGADO y no se ejecutaria la
        // linea que revienta. Ver el javadoc de arriba.
        SafeActions.forceScanOnForTest();

        final TutorialLesson lesson = position();
        final TutorialState state = new TutorialState(lesson.getState());

        System.out.println("  Mesa: Coram + molino, y cartas de dos caras robandose y moliendose.");
        System.out.println("  (es la partida que se cerraba sola; ver SafeActions)");

        final NeoGame.Result result = NeoGame.playTutorial(
                lesson, state, NeoMatchUI.Mode.AUTO_PLAY, 40, null, false, null);

        final int rescues = SafeActions.rescues();
        System.out.printf(Locale.ROOT, "  Turnos jugados: %d | rescates del barrido: %d%n",
                result.turns, rescues);

        // 1. El fallo del motor se ha producido DE VERDAD.
        check(rescues > 0,
                "el barrido revento y se rescato (" + rescues + " vez/veces)",
                "el barrido NUNCA fallo: o la posicion no monto la mesa, o el barrido "
                        + "esta apagado, o Card-Forge ya lo ha arreglado y este apanyo "
                        + "sobra. Mirar las tres antes de tocar nada");

        // 2. Y aun asi la partida LLEGO AL FINAL. Se mira `completed` y no los
        //    turnos: sin blindaje el hilo del motor muere y la partida se
        //    queda colgada hasta que la corta el tope de tiempo, que es
        //    exactamente lo que vio el jugador (mesa muerta, sin resultado).
        //    Contar turnos no distinguiria eso de una partida corta de verdad.
        check(result.completed,
                "la partida llego al final (" + result.turns + " turnos"
                        + (result.winner == null ? "" : ", gana " + result.winner) + ")",
                "la partida no termino: se quedo en " + result.turns + " turno(s) y hubo "
                        + "que cortarla por tiempo. El hilo del motor murio igual, o sea "
                        + "que el blindaje no cubre esta puerta");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del barrido han fallado");
        }
    }

    /**
     * La mesa del informe, reducida a lo minimo que reproduce el fallo.
     *
     * <p>No hace falta atacar: lo que el jugador vio como <i>"ataco con Coram y
     * el juego se cierra"</i> era en realidad <i>"acaban de caer cartas de la
     * biblioteca al cementerio"</i>, y atacar era su forma de conseguirlo. Aqui
     * lo hace el molino de mantenimiento, que ademas pasa en todos los turnos y
     * no solo en los de combate.
     *
     * <p>Cada ingrediente de aqui esta comprobado que hace falta: quitando el
     * molino, o cambiando la carta de dos caras por una normal, la prueba se
     * pone en rojo diciendo que el fallo no llego a producirse.
     */
    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=40",
                "ailife=40",
                "humanbattlefield=" + CORAM + ";" + MILL + ";" + FOREST + ";" + SWAMP
                        + ";" + MOUNTAIN + ";" + FOREST,
                "humanhand=" + TWO_FACED,
                "humanlibrary=" + (TWO_FACED + ";").repeat(LIBRARY_COPIES) + FOREST,
                "humangraveyard=",
                "humanexile=",
                "humancommand=",
                "aibattlefield=",
                "aihand=",
                "ailibrary=" + (FOREST + ";").repeat(LIBRARY_COPIES) + MOUNTAIN,
                "aigraveyard=",
                "aiexile=",
                "aicommand=");
        return new TutorialLesson("actions", state, List.of());
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + good);
        } else {
            failed++;
            System.out.println("  MAL  " + bad);
        }
    }
}
