package forge.neo.match;

/**
 * <b>La coreografia de la firma de un comandante</b>, en numeros: donde esta
 * cada uno de sus dibujos ({@link SignatureSprite}) en cada instante de la
 * entrada. La pintan las dos interfaces igual porque la cuenta es esta, y vive
 * en el jar por eso. Ninguna guarda estado por fotograma: todo sale del
 * instante y de unos parametros sorteados una vez con semilla fija.
 *
 * <p>Cada dibujo se pinta trasladado a {@code X,Y} (donde cae su base),
 * girado {@code ROT} grados en el sentido del reloj (0 = punta hacia arriba)
 * y escalado {@code LEN/UNIT} a lo largo y {@code LEN/UNIT*FLIP} a lo ancho.
 */
public interface SignatureMotion {

    int X = 0;
    int Y = 1;
    int ROT = 2;
    int LEN = 3;
    int FLIP = 4;
    int ALPHA = 5;
    int STRIDE = 6;

    /** Cuantos dibujos lleva. */
    int count();

    /** Que dibujo es el {@code i}. */
    SignatureSprite sprite(int i);

    /** Si se pinta grande (de la imagen grande): el ala, los tajos. */
    boolean big(int i);

    /** Si va por delante de la carta grande (los demas, por detras). */
    boolean inFront(int i);

    /**
     * El instante {@code t} (segundos desde que empieza la entrada).
     *
     * @param duration lo que dura la entrada entera
     * @param cardX centro de la carta grande ahora
     * @param cardY centro de la carta grande ahora
     * @param cardW ancho de la carta grande ahora (ya escalada)
     * @param cardH alto de la carta grande ahora (ya escalada)
     * @param landAt cuando se posa en su sitio
     * @param slotX centro de su sitio en la mesa (NaN si no se sabe)
     * @param slotY centro de su sitio en la mesa
     * @param slotH alto de su sitio en la mesa
     * @param out {@link #count()} * {@link #STRIDE} numeros
     */
    void frame(double t, double duration, double cardX, double cardY, double cardW, double cardH, double landAt,
            double slotX, double slotY, double slotH, double[] out);

    // ------------------------------------------------------------------
    // Lo que comparten las coreografias.

    static void put(final double[] out, final int i, final double x, final double y, final double rot,
            final double len, final double flip, final double alpha) {
        final int o = i * STRIDE;
        out[o + X] = x;
        out[o + Y] = y;
        out[o + ROT] = rot;
        out[o + LEN] = len;
        out[o + FLIP] = flip;
        out[o + ALPHA] = alpha;
    }

    /** Pone un dibujo de largo {@code len} con su CENTRO en (cx, cy): la forma gira por su base. */
    static void putCentered(final double[] out, final int i, final double cx, final double cy, final double rot,
            final double len, final double flip, final double alpha) {
        final double rad = Math.toRadians(rot);
        put(out, i, cx - Math.sin(rad) * len / 2, cy + Math.cos(rad) * len / 2, rot, len, flip, alpha);
    }

    static void hide(final double[] out, final int i) {
        put(out, i, 0, 0, 0, 0, 1, 0);
    }

    static double clamp01(final double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    static double easeOutCubic(final double p) {
        final double q = 1 - p;
        return 1 - q * q * q;
    }
}
