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

    /**
     * El "1", "2"... que va delante de una opcion.
     *
     * <p>Solo se ve dentro de la capa de dialogos de una mesa, que es lo unico
     * que escucha {@code TableKeys}. Los mismos dialogos salen tambien fuera de
     * partida (el sobre de premio de la Quest, Discord 18-10-2026), y ahi un
     * numero que la tecla no pulsa es un control que no hace lo que parece
     * (principio 1). Se mira al entrar en escena: el dialogo ya esta colgado
     * de su capa.
     *
     * <p>Fuera de la mesa se QUITA del boton, no se esconde: el skin de un
     * {@code Labeled} decide al montarse si cuenta con el grafico y no vuelve a
     * mirarlo, asi que uno escondido deja su hueco y uno sin gestionar ya no
     * sale nunca. Y en un {@code runLater}, porque esto llega a mitad de meter
     * el dialogo en la escena.
     */
    static Label hint(final int n) {
        final Label l = new Label(String.valueOf(n));
        l.getStyleClass().add("key-hint");
        l.setMinWidth(Label.USE_PREF_SIZE);
        l.sceneProperty().addListener((obs, was, now) -> {
            if (now == null || inTableDialogs(l)) {
                return;
            }
            final javafx.scene.Parent owner = l.getParent();
            if (owner instanceof javafx.scene.control.Labeled labeled) {
                javafx.application.Platform.runLater(() -> {
                    if (labeled.getGraphic() == l) {
                        labeled.setGraphic(null);
                    }
                });
            } else {
                l.setVisible(false);
            }
        });
        return l;
    }

    /** Si el nodo cuelga de la capa de dialogos del motor de una mesa. */
    private static boolean inTableDialogs(final javafx.scene.Node node) {
        javafx.scene.Node overlay = null;
        for (javafx.scene.Node p = node.getParent(); p != null; p = p.getParent()) {
            if (p instanceof TableScreen table) {
                return overlay != null && overlay == table.getOverlay();
            }
            if (p instanceof Overlay) {
                overlay = p;
            }
        }
        return false;
    }
}
