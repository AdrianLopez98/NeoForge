package forge.neo.tutorial;

import java.util.function.Predicate;

import forge.neo.NeoText;

/**
 * Un paso del tutorial: una cosa que explicar y una forma de saber que ya se ha
 * entendido.
 *
 * <p>Hay tres clases de paso, y la diferencia importa:
 *
 * <ul>
 *   <li><b>De leer</b> — no se espera nada. Lleva un boton "Siguiente" y ya.</li>
 *   <li><b>De gesto</b> — se espera un gesto de la interfaz que el motor no ve
 *       (ampliar una carta con el click derecho, acercar la mesa con la rueda,
 *       abrir el cementerio). Lo cuenta la propia mesa; ver
 *       {@code TableScreen.setGestureSpy}.</li>
 *   <li><b>De partida</b> — se espera algo que <b>si</b> ve el motor: jugar una
 *       tierra, lanzar un hechizo, declarar atacantes. Lo contesta
 *       {@link TutorialProbe}.</li>
 * </ul>
 *
 * <p><b>Un paso de gesto o de partida NO lleva boton de continuar.</b> Es el
 * principio 1 de la seccion 10b: un boton que no hace lo que parece es peor que
 * no tenerlo, y un "Siguiente" al lado de "juega una tierra" invita a saltarse
 * justo lo unico que hay que hacer. Lo que si lleva siempre es una salida
 * pequenya ("Saltar este paso"), que es el principio 7: por muy bien que
 * calculemos cuando algo esta hecho, alguien se va a quedar atascado.
 *
 * <p>Los textos no estan aqui: se piden a {@link NeoText} con la clave del
 * paso, para que el tutorial se traduzca como el resto de la interfaz.
 */
public final class TutorialStep {

    /** A donde hay que mirar mientras se lee el paso. */
    public enum Spot {
        /** A ningun sitio en concreto. */
        NONE,
        HAND,
        BOARD_SELF,
        BOARD_OPP,
        /** La columna de la derecha: el prompt y el boton grande. */
        SIDE,
        PHASES,
        STACK,
        SELF_BAR,
        OPP_BAR,
        COMMAND,
        LOG
    }

    private final String key;
    private final Spot spot;
    private final String gesture;
    private final Predicate<TutorialProbe> watch;

    private TutorialStep(final String key, final Spot spot, final String gesture,
                         final Predicate<TutorialProbe> watch) {
        this.key = key;
        this.spot = spot;
        this.gesture = gesture;
        this.watch = watch;
    }

    /** Un paso que solo hay que leer. */
    public static TutorialStep read(final String key, final Spot spot) {
        return new TutorialStep(key, spot, null, null);
    }

    /** Un paso que se cierra haciendo un gesto de la interfaz. */
    public static TutorialStep gesture(final String key, final Spot spot, final String gesture) {
        return new TutorialStep(key, spot, gesture, null);
    }

    /** Un paso que se cierra cuando el motor cuenta algo concreto. */
    public static TutorialStep watch(final String key, final Spot spot,
                                     final Predicate<TutorialProbe> watch) {
        return new TutorialStep(key, spot, null, watch);
    }

    public String getKey() {
        return key;
    }

    public Spot getSpot() {
        return spot;
    }

    /** true si basta con leerlo: entonces, y solo entonces, hay boton. */
    public boolean isRead() {
        return gesture == null && watch == null;
    }

    /**
     * Que gesto de la interfaz espera, o {@code null} si no espera ninguno.
     *
     * <p>Lo necesita el comprobador sin ventana para cazar un nombre inventado:
     * un paso que espera un gesto que la mesa no dispara nunca no falla, se
     * queda ahi para siempre. Ver {@link Gesture}.
     */
    public String getGesture() {
        return gesture;
    }

    public String getTitle() {
        return NeoText.get(key + ".title");
    }

    public String getBody() {
        return NeoText.get(key + ".body");
    }

    /** true si este gesto de la interfaz cierra el paso. */
    public boolean closedBy(final String what) {
        return gesture != null && gesture.equals(what);
    }

    /** true si lo que acaba de contar el motor cierra el paso. */
    public boolean closedBy(final TutorialProbe probe) {
        return watch != null && probe != null && watch.test(probe);
    }
}
