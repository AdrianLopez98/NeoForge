package forge.neo.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import forge.game.GameView;
import forge.game.card.CardView;
import forge.trackable.Tracker;
import forge.trackable.TrackableTypes;

/**
 * <b>Las cartas de una pregunta, las de ESTA partida</b> (para el invitado en red).
 *
 * <p><b>El fallo</b> (Discord, 28-10-2026: <i>"for decks with companions we are
 * not able to pick them - or it was bugged, because it showed the last
 * commander i was using in the previous match"</i>). Es una carrera de la red de
 * Forge. Al empezar una partida, el anfitrion manda el estado entero (con el
 * compañero, en el banquillo) y justo detras la pregunta del compañero, con la
 * carta como numero ({@code IdRef}). El invitado traduce ese numero a carta AL
 * RECIBIR el mensaje ({@code TrackableSerializer.resolve}, en el hilo de red),
 * antes de que se haya aplicado el estado que llego justo antes
 * ({@code applyDelta}, en el hilo de interfaz). Y su registro de cartas es el
 * de la conexion entera ({@code GameClientHandler} no lo vacia entre partidas):
 * en la segunda partida, el numero 201 era todavia la carta 201 de la ANTERIOR.
 * El dialogo enseñaba "Sephiroth (201)" en vez de "Umori (201)", lo que se
 * contestaba no le cuadraba al anfitrion y la partida moria al empezar,
 * devolviendo al invitado a la sala sin aviso. Reproducido con dos procesos
 * ({@code --lobby-join}), cambiando de mazo entre la primera y la segunda.
 *
 * <p><b>El arreglo</b>, desde fuera y sin tocar Forge: cuando la pregunta llega
 * a nuestra interfaz el estado ya esta aplicado, asi que se vuelve a buscar cada
 * carta por su numero. Si la del registro tiene OTRO nombre, la que venia era
 * de la partida anterior y se cambia. Con el mismo nombre no se toca: puede ser
 * una copia a proposito (la carta tal y como estaba al moverse de zona). Una
 * carta que ni siquiera llego ({@code null}) se quita de la lista.
 *
 * <p>Vaciar el registro al acabar cada partida NO sirve (se probo): el numero
 * llega antes que la carta y se traduce a {@code null}.
 */
public final class GuestCards {

    private GuestCards() {
    }

    /** Las opciones con cada carta cambiada por la de esta partida. En una partida local, las mismas. */
    public static <T> List<T> fresh(final List<T> options, final GameView gv) {
        if (options == null || gv == null || gv.getGame() != null) {
            return options;   // con la partida en esta maquina no hay red de por medio
        }
        final Tracker tracker = gv.getTracker();
        if (tracker == null) {
            return options;
        }
        List<T> out = null;
        for (int i = 0; i < options.size(); i++) {
            final T o = options.get(i);
            Object now = o;
            if (o instanceof CardView cv) {
                final CardView tracked = tracker.getObj(TrackableTypes.CardViewType, cv.getId());
                if (tracked != null && tracked != cv && !Objects.equals(tracked.getName(), cv.getName())) {
                    System.out.println("[red] carta de la partida anterior en una pregunta: " + cv
                            + " -> " + tracked);
                    now = tracked;
                }
            }
            if (now != o || o == null) {
                if (out == null) {
                    out = new ArrayList<>(options.subList(0, i));
                }
            }
            if (out != null && now != null) {
                @SuppressWarnings("unchecked")
                final T t = (T) now;
                out.add(t);
            }
        }
        return out == null ? options : out;
    }
}
