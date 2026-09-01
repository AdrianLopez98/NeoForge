package forge.neo.ui;

/**
 * Escala global de la interfaz.
 *
 * <p>El objetivo es que la mesa se vea IGUAL de proporcionada en 1080p, en 2K y
 * en 4K: mismas proporciones, mismo numero de cartas visibles, texto igual de
 * legible. Lo que cambia es la nitidez, no la composicion.
 *
 * <p>Como funciona:
 * <ul>
 *   <li>El tamano de las cartas lo decide el espacio disponible
 *       ({@link BattlefieldPane}), asi que ya es responsivo por si solo.</li>
 *   <li>El texto y los adornos se escalan con este factor, que se aplica al
 *       tamano de fuente de la raiz. La hoja de estilos usa {@code em}, asi que
 *       todo el texto sigue a ese valor.</li>
 * </ul>
 *
 * <p>Orden de precedencia:
 * <ol>
 *   <li>{@code -Dneo.uiScale=1.25} si esta puesto (util para probar).</li>
 *   <li>El valor guardado en ajustes (pendiente, fase 5).</li>
 *   <li>Automatico, a partir de la altura de la ventana.</li>
 * </ol>
 */
public final class UiScale {

    private UiScale() {
    }

    /** Altura logica de referencia con la que se diseno la interfaz. */
    private static final double REFERENCE_HEIGHT = 1080;

    /** Tamano de fuente base a escala 1.0, en px. */
    public static final double BASE_FONT_PX = 13;

    private static final double MIN = 0.72;
    private static final double MAX = 2.0;

    private static Double override;

    /** Ajuste manual (fase 5: pantalla de ajustes). {@code null} para automatico. */
    public static void setOverride(final Double factor) {
        override = factor;
    }

    public static Double getOverride() {
        return override;
    }

    /** Factor de escala para una ventana de la altura dada. */
    public static double forHeight(final double windowHeight) {
        final String prop = System.getProperty("neo.uiScale");
        if (prop != null) {
            try {
                return clamp(Double.parseDouble(prop));
            } catch (final NumberFormatException ignored) {
                // valor invalido: seguimos con el automatico
            }
        }
        if (override != null) {
            return clamp(override);
        }
        return clamp(windowHeight / REFERENCE_HEIGHT);
    }

    /** Estilo a aplicar a la raiz de la escena para fijar la escala. */
    public static String rootStyle(final double windowHeight) {
        return "-fx-font-size: " + round(BASE_FONT_PX * forHeight(windowHeight)) + "px;";
    }

    private static double clamp(final double v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    private static double round(final double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
