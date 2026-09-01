package forge.neo.tutorial;

import java.util.List;

/**
 * Los gestos de la interfaz que el motor NO ve.
 *
 * <p>Ampliar una carta, acercar la mesa, abrir el cementerio, poner una parada
 * de fase: nada de eso llega al motor, asi que un paso del tutorial que espere
 * uno de ellos no puede enterarse preguntandole a el. Los cuenta
 * {@code TableScreen}, que es donde pasan.
 *
 * <p><b>Por que constantes y no cadenas sueltas.</b> Son dos sitios muy lejanos
 * — quien lo dispara y quien lo espera — y una letra de mas en cualquiera de
 * los dos deja un paso del tutorial <b>imposible de terminar</b>, sin ningun
 * aviso: parece que el gesto "no funciona". Con constantes, ese error no
 * compila.
 */
public final class Gesture {

    private Gesture() {
    }

    /** Click derecho sobre una carta: sale grande. */
    public static final String ZOOM_CARD = "zoom.card";

    /** Ctrl + rueda: la mesa se acerca. */
    public static final String BOARD_ZOOM = "board.zoom";

    /** Boton central o Ctrl + arrastrar: la mesa se mueve. */
    public static final String BOARD_PAN = "board.pan";

    /** Se ha abierto el contenido de una zona (cementerio, exilio, mazo...). */
    public static final String ZONE_OPEN = "zone.open";

    /** Se ha abierto el registro de la partida. */
    public static final String LOG_OPEN = "log.open";

    /** Se ha puesto o quitado una parada en el rail de fases. */
    public static final String PHASE_STOP = "phase.stop";

    /** Escape: se ha abierto el menu de pausa. */
    public static final String PAUSE_OPEN = "pause.open";

    /**
     * Se ha entrado en Ajustes desde el menu de pausa.
     *
     * <p>Es el unico de los gestos que no pasa por la mesa: nace en
     * {@code PauseMenu}, que vive en la capa de encima. Se cuenta igual — le
     * pasa el aviso a {@code TableScreen}, que es quien tiene el espia — porque
     * para el tutorial es lo mismo que los demas: algo que el jugador hace y
     * que el motor no ve.
     */
    public static final String SETTINGS_OPEN = "settings.open";

    /** Todos, para que el comprobador sin ventana pueda cazar un nombre inventado. */
    public static List<String> all() {
        return List.of(ZOOM_CARD, BOARD_ZOOM, BOARD_PAN, ZONE_OPEN, LOG_OPEN, PHASE_STOP,
                PAUSE_OPEN, SETTINGS_OPEN);
    }
}
