package forge.neo.ui;

import javafx.scene.control.Label;

/**
 * Una pregunta del motor que se contesta con las teclas 1 a 9.
 *
 * <p>Pedido en Discord el 16-10-2026, para las macros y los combos repetitivos:
 * <i>"assign the available choices to number keys ... triggered not only when
 * the key is initially pressed, but also while the key is already being held
 * down"</i>. La opcion N es la N-esima que se ve, y lleva su numero pintado al
 * lado ({@link #hint}). Las teclas las reparte {@code TableKeys}, que es por
 * donde pasan TODAS las mesas (tambien el duelo de la Aventura).
 *
 * <p>Mantener la tecla pulsada contesta la pregunta siguiente con el mismo
 * numero: es justo lo que se pidio. Por eso en una eleccion de VARIAS la
 * repeticion no hace nada — marcaria y desmarcaria la misma opcion sin parar.
 */
public interface NumberedChoice {

    /**
     * @param n      de 1 a 9
     * @param repeat la tecla ya estaba pulsada (la repeticion del sistema)
     * @return si la tecla se ha usado
     */
    boolean pickNumber(int n, boolean repeat);

    /** El "1", "2"... que va delante de una opcion. */
    static Label hint(final int n) {
        final Label l = new Label(String.valueOf(n));
        l.getStyleClass().add("key-hint");
        l.setMinWidth(Label.USE_PREF_SIZE);
        return l;
    }
}
