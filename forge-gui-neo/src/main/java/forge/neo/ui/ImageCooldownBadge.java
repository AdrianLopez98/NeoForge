package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardImages;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.Label;
import javafx.util.Duration;

/**
 * "Scryfall nos ha limitado, vuelve en Ns."
 *
 * <p>Reportado jugando: recien importado un mazo, media mano sale sin arte y
 * no hay forma de saber si esta roto o si solo hace falta esperar. Lo segundo
 * es lo normal — Scryfall raciona a una peticion cada 100 ms, y una rafaga
 * grande (el catalogo del deck builder, o empezar una partida con un mazo
 * nuevo entero por descargar) puede hacer que nos corte de verdad con un 429,
 * que dispara un enfriamiento (lo que diga el <b>Retry-After</b>) — mecanismo que ya trae
 * Forge y no se toca (regla de oro). Mientras dura, toda carta que no
 * tuvieras ya en cache sale con el dibujo de repuesto, y sin este aviso eso
 * es indistinguible de "esto esta roto".
 *
 * <p>Puramente informativo: no se puede clicar (seccion 10b, principio 1 —
 * los botones que actuan sobre la partida viven en un solo sitio, esto no es
 * uno de ellos). Se controla sola: arranca su reloj al engancharse a una
 * {@code Scene} y lo para al salir de ella, asi que basta con anyadirla al
 * arbol y colocarla.
 */
public class ImageCooldownBadge extends Label {

    private final Timeline poll = new Timeline(new KeyFrame(Duration.seconds(1), e -> refresh()));

    public ImageCooldownBadge() {
        getStyleClass().add("cooldown-badge");
        setMouseTransparent(true);
        setVisible(false);
        setManaged(false);
        poll.setCycleCount(Timeline.INDEFINITE);

        sceneProperty().addListener((o, was, is) -> {
            if (is != null) {
                refresh();
                poll.play();
            } else {
                poll.stop();
            }
        });
    }

    private void refresh() {
        final long left = CardImages.coolingDownSecondsLeft();
        final boolean on = left > 0;
        if (isVisible() != on) {
            setVisible(on);
            setManaged(on);
            if (getParent() != null) {
                getParent().requestLayout();
            }
        }
        if (on) {
            setText(NeoText.get("table.imagesCooldown", left));
        }
    }
}
