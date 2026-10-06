package forge.neo.ui;

import forge.neo.NeoText;
import javafx.animation.PauseTransition;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;

/**
 * <b>El codigo de la run, a la vista y a un clic de copiarse</b>
 * ({@link forge.neo.ascent.AscentSeed}).
 *
 * <p>Pedido en Discord el 06-10-2026: lo que importa es que el jugador vea
 * <i>que</i> semilla esta jugando, por si la quiere compartir. Por eso no va
 * detras de un menu: es un boton con el codigo escrito, en el mapa y en el
 * resumen final, y al pulsarlo se copia y dice "Copiado" un momento.
 */
public final class AscentCodeButton {

    private AscentCodeButton() {
    }

    /** El boton, o {@code null} si la run no tiene codigo (las de antes de que existiera). */
    public static Button of(final String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        final String label = NeoText.get("ascent.seed.codeButton", code);
        final Button b = new Button(label);
        b.setId("ascent-code");
        b.getStyleClass().add("ascent-button");
        b.setTooltip(new Tooltip(NeoText.get("ascent.seed.copyHint")));
        final PauseTransition back = new PauseTransition(Duration.millis(1500));
        back.setOnFinished(e -> b.setText(label));
        b.setOnAction(e -> {
            forge.gui.GuiBase.getInterface().copyToClipboard(code);
            b.setText(NeoText.get("ascent.seed.copied"));
            back.playFromStart();
        });
        return b;
    }
}
