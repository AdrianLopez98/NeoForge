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

    /**
     * Cartas en fila que caben en el ancho de la mesa: de ahi sale el ancho de
     * carta que se reparte a TODAS las pantallas.
     */
    private static final int CARDS_ACROSS = 11;

    /** Tope del ancho de carta a 1080 de alto, en px. */
    private static final double CARD_CAP_1080 = 150;

    /**
     * El alto de la pantalla para la que se reparten las medidas: la de verdad,
     * o la de {@code -Dneo.win}. La ponen quienes abren la ventana.
     */
    private static double screenHeight = REFERENCE_HEIGHT;

    /** Lo llaman NeoApp y la ventana de la Aventura al abrir la ventana. */
    public static void setScreenHeight(final double height) {
        screenHeight = height;
    }

    public static double screenHeight() {
        return screenHeight;
    }

    /** El factor del texto que se aplico la ultima vez ({@link #rootStyle}). */
    private static double textFactor = Double.NaN;

    /**
     * Cuanto crece una medida en px respecto de 1080p. Nunca menos de 1: con
     * 1080 de alto o menos, todo se queda exactamente como estaba.
     *
     * <p>Es el MISMO factor por el que crece el texto — el ultimo que se aplico
     * a la raiz, con la escala de Ajustes incluida —, y hasta que se aplica
     * alguno, el de la pantalla. Una caja de ancho fijo en px con la letra un
     * tercio mas grande dentro parte el texto en mas lineas, corta con "..." y
     * deja media pantalla vacia; una carta con tope fijo sale igual de pequenya
     * que en 1080p en una ventana un tercio mas alta. Los dos son el fallo del
     * 2K del 24-09-2026.
     */
    public static double growth() {
        // Con -Dneo.win la pantalla es de mentira y la ventana de verdad no puede
        // pasar del monitor fisico: la letra de la raiz se calcularia con ese
        // alto, no con el simulado, y las capturas de 2K saldrian como 1080p.
        if (Double.isNaN(textFactor) || System.getProperty("neo.win") != null) {
            return growth(screenHeight);
        }
        return Math.max(1, textFactor);
    }

    private static double growth(final double height) {
        return Math.max(1, Math.min(MAX, height / REFERENCE_HEIGHT));
    }

    /** {@code px} pensados para 1080p, llevados a la pantalla de ahora. */
    public static double px(final double px) {
        return px * growth();
    }

    /**
     * Pone a {@code node} una letra de {@code px} (medida en 1080p) crecida con
     * {@link #growth}, AnYADIDA al estilo que ya tenga. Es para las pocas reglas
     * del CSS que van en px en vez de em: en 1080p deja exactamente el mismo
     * numero, y en 2K lo crece como el resto del texto. Pasarlas a em no valia:
     * en un 1080p maximizado la raiz se queda en unos 12 px, y la letra habria
     * encogido ahi.
     */
    public static <T extends javafx.scene.Node> T fixedFont(final T node, final double px) {
        final String own = node.getStyle() == null ? "" : node.getStyle();
        node.setStyle(own + "-fx-font-size: " + round(px(px)) + "px;");
        return node;
    }

    /**
     * Fija un circulo de {@code px} de diametro (medido en 1080p), crecido con
     * {@link #growth}, anyadido al estilo que ya tenga. Para los pips del CSS
     * con min y max en px: con la letra crecida dentro y el circulo fijo, en
     * 2K el numero se salia del pip.
     */
    public static <T extends javafx.scene.Node> T fixedCircle(final T node, final double px) {
        final double d = round(px(px));
        final String own = node.getStyle() == null ? "" : node.getStyle();
        node.setStyle(own + "-fx-min-width: " + d + "px; -fx-max-width: " + d + "px;"
                + "-fx-min-height: " + d + "px; -fx-max-height: " + d + "px;"
                + "-fx-background-radius: " + round(d / 2) + "px;");
        return node;
    }

    /** Ancho de la columna lateral de la mesa. */
    public static double sideWidth(final double width) {
        return Math.max(240, width * 0.19);
    }

    /**
     * El ancho de carta que se reparte a todas las pantallas.
     *
     * <p>Sale del ancho (once cartas en fila, descontando la columna lateral),
     * con un tope de 150 px a 1080 de alto <b>que crece con el texto</b>. Hasta
     * el 24-09-2026 el tope era fijo: en 1920x1080 no se nota (salen 136), pero
     * en 2560x1440 a escala 100% se llegaba a el, y todas las pantallas de
     * cartas salian igual de pequenyas que en 1080p con la letra un tercio mas
     * grande. Ahora salen 183. Con 1080 de alto o menos, el numero es
     * exactamente el de antes.
     */
    public static double cardWidth(final double width, final double height) {
        return Math.max(78, Math.min(CARD_CAP_1080 * growth(height),
                (width - sideWidth(width) - 60) / CARDS_ACROSS));
    }

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
        textFactor = forHeight(windowHeight);
        return "-fx-font-size: " + round(BASE_FONT_PX * textFactor) + "px;";
    }

    private static double clamp(final double v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    private static double round(final double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
