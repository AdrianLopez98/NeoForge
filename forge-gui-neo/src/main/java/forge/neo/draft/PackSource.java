package forge.neo.draft;

import java.util.List;

import forge.item.PaperCard;

/**
 * De donde salen los sobres de un draft.
 *
 * <p>Existe por una razon concreta: el draft <b>en red</b> tiene que verse
 * exactamente igual que el de siempre. La pantalla de picks
 * ({@code forge.neo.ui.DraftScreen}) es de las que mas trabajo tienen encima —
 * la rejilla que se mide sola, el vuelo del pick, la tira de lo que llevas, el
 * rating detras de su casilla, el clic derecho para leer la carta — y tener dos
 * copias de eso seria garantizar que una se queda vieja.
 *
 * <p><b>La diferencia entre las dos es de quien manda</b>, y es justo lo que
 * esconde este interfaz:
 *
 * <ul>
 *   <li>{@link NeoDraft} <b>tira</b>: la pantalla le pide el sobre y el motor
 *       lo tiene ahi mismo. Contestar es instantaneo y nunca hay que
 *       esperar.</li>
 *   <li>{@code forge.neo.net.NetDraftSource} <b>recibe</b>: el sobre llega por
 *       el cable cuando el anfitrion lo manda, asi que hay ratos <b>sin sobre
 *       en la mano</b> ({@link #waiting()}), hay <b>reloj</b>
 *       ({@link #secondsLeft()}) y hay gente delante y detras cuya cola se
 *       puede ver ({@link #seatNotes()}).</li>
 * </ul>
 *
 * <p>Los tres metodos de red llevan valor por defecto a proposito: el draft
 * de siempre no sabe de nada de eso y no tiene por que contestar.
 */
public interface PackSource {

    /** Las cartas que quedan en el sobre que tienes delante. */
    List<PaperCard> currentCards();

    /** Lo que llevas cogido, en orden. */
    List<PaperCard> picked();

    /**
     * Coge esta carta.
     *
     * <p>Devuelve la que de verdad se ha cogido, que <b>puede no ser la
     * pedida</b>: hay cartas que cambian el propio draft (el Archdemon of
     * Paliano te obliga a coger la primera, "Lore Seeker" y companyia te hacen
     * saltarte un pick). Null si no se ha cogido ninguna.
     */
    PaperCard pick(PaperCard card);

    /** Sobre 1, 2 o 3. */
    int round();

    /** Que pick es este dentro del sobre, empezando por 1. */
    int pickNumber();

    /** Cuantas cartas quedan en el sobre. */
    int cardsLeft();

    /** Ya no quedan picks. */
    boolean isDone();

    /** De que son los sobres, para la cabecera. Puede ser null. */
    String productName();

    /**
     * Que picks quiere el jugador en el mazo principal, segun los va
     * repartiendo durante el draft. El resto va al banquillo.
     *
     * <p>Lo pidio en itch.io quien juega limitado a diario (23-09-2026): Forge
     * y Arena dejan montar el mazo MIENTRAS se draftea, para ver cuantos
     * jugables llevas y donde estan los huecos. Por defecto no hace nada: el
     * draft en red guarda su pool de otra forma.
     */
    default void planMain(final List<PaperCard> main) {
    }

    // ------------------------------------------------------------------
    // Solo el draft en red contesta a esto
    // ------------------------------------------------------------------

    /**
     * No hay sobre que mirar: se espera a que llegue.
     *
     * <p>Con la rejilla vacia y sin decir nada, esperar es indistinguible de
     * estar roto — y en un draft asincrono puedes pasarte medio minuto asi
     * porque el de al lado esta pensando.
     */
    default boolean waiting() {
        return false;
    }

    /**
     * Segundos que quedan para que el pick se haga solo, o 0 si no hay reloj.
     *
     * <p>El plazo lo pone el anfitrion al montar el evento y lo cuenta el
     * servidor ({@code BoosterDraftHost}): esto es solo lo que hay que
     * ensenyar. Cuando se acaba, el pick lo hace el servidor y llega como un
     * "elegido automaticamente".
     */
    default int secondsLeft() {
        return 0;
    }

    /**
     * Una linea por asiento del pod, ya escrita: quien es y cuantos sobres
     * tiene esperando.
     *
     * <p>Es el dato que convierte una espera en informacion: dice si el que
     * tarda eres tu o es otro.
     */
    default List<String> seatNotes() {
        return List.of();
    }
}
