package forge.neo.match;

import java.util.Random;

/**
 * <b>La coreografia de las plumas negras</b> ({@link CommanderSignature#BLACK_FEATHERS},
 * Sephiroth): donde esta cada pluma en cada instante de la entrada, en numeros.
 *
 * <p>Las dos interfaces la pintan igual porque la cuenta es esta, y vive en el
 * jar por eso: el escritorio pone una forma por pluma y le mueve sus
 * transformaciones, Android dibuja la misma forma en su lienzo. Nada aqui
 * guarda estado por fotograma: {@link #frame} lo deduce todo del instante y de
 * unos parametros sorteados una vez (con semilla fija, asi que cada entrada es
 * igual a la anterior).
 *
 * <p>Tres grupos:
 * <ol>
 *   <li><b>El ala</b> ({@link #WING} plumas en tres filas: remeras, secundarias
 *       y coberteras): sale del hombro de la carta grande, por detras, y se
 *       despliega de abajo arriba con un pequenyo rebote; aletea un poco; y
 *       justo antes de que la carta vuele a su sitio se deshace en plumas que
 *       caen girando.</li>
 *   <li><b>La lluvia</b> ({@link #FALL}): plumas por toda la pantalla que caen
 *       meciendose, unas por detras de la carta y otras por delante.</li>
 *   <li><b>El estallido</b> ({@link #BURST}): al posarse en la mesa salen hacia
 *       arriba en abanico desde su sitio y caen apagandose.</li>
 * </ol>
 *
 * <p>Cada pluma se dibuja con {@link #VANE} y {@link #QUILL}, que miden
 * {@link #UNIT} de la base (0,0) a la punta (0,-100): se traslada a
 * {@code X,Y}, se gira {@code ROT} grados (en el sentido del reloj, 0 = punta
 * hacia arriba) y se escala {@code LEN/UNIT} a lo largo y
 * {@code LEN/UNIT*FLIP} a lo ancho (el giro en el aire).
 */
public final class BlackFeathers implements SignatureMotion {

    /** El contorno de la pluma, en el formato de los trazados SVG. */
    public static final String VANE = "M0,0 C-8,-10 -14,-26 -15,-40 L-10,-44 L-15,-49 "
            + "C-15,-66 -10,-84 -3,-97 Q0,-101 2,-97 C7,-86 9,-68 8,-54 L5,-50 L8,-46 C8,-28 5,-12 0,0 Z";
    /** El canyon, una raya clara por el medio: es lo que la hace leerse sobre la mesa oscura. */
    public static final String QUILL = "M0,12 Q3,-40 0,-94";
    /** Lo que mide la pluma de {@link #VANE} de la base a la punta. */
    public static final double UNIT = 100;

    public static final int WING = 25;
    public static final int FALL = 24;
    public static final int BURST = 12;
    public static final int COUNT = WING + FALL + BURST;

    /** Lo que deja {@link #frame} por pluma, uno detras de otro. */
    public static final int X = 0;
    public static final int Y = 1;
    public static final int ROT = 2;
    public static final int LEN = 3;
    public static final int FLIP = 4;
    public static final int ALPHA = 5;
    public static final int STRIDE = 6;

    /** Cuando empieza a deshacerse el ala, antes de que la carta vuele. */
    private static final double DETACH = 0.78;
    private static final double FOLDED = -176;
    /** Hasta cuando cae la lluvia despues de posarse la carta (y 0,3 s mas apagandose). */
    private static final double RAIN_AFTER_LANDING = 0.2;

    private final double w;
    private final double h;

    // EL ALA, por pluma.
    private final double[] along = new double[WING];
    private final double[] open = new double[WING];
    private final double[] lenF = new double[WING];
    private final double[] delay = new double[WING];
    private final double[] flapAmp = new double[WING];
    private final double[] detach = new double[WING];
    private final double[] driftX = new double[WING];
    private final double[] spin = new double[WING];
    private final double[] phase = new double[WING + FALL + BURST];

    // LA LLUVIA.
    private final double[] fx = new double[FALL];
    private final double[] fy = new double[FALL];
    private final double[] fLen = new double[FALL];
    private final double[] fVy = new double[FALL];
    private final double[] fAmp = new double[FALL];
    private final double[] fOmega = new double[FALL];
    private final double[] fFlip = new double[FALL];
    private final double[] fBase = new double[FALL];
    private final double[] fDelay = new double[FALL];
    private final boolean[] fFront = new boolean[FALL];

    // EL ESTALLIDO.
    private final double[] bAngle = new double[BURST];
    private final double[] bSpeed = new double[BURST];
    private final double[] bLen = new double[BURST];
    private final double[] bSpin = new double[BURST];

    /** @param width ancho de la pantalla; @param height su alto (en las mismas unidades que se pintan). */
    public BlackFeathers(final double width, final double height) {
        this.w = width;
        this.h = height;
        final Random r = new Random(0x5E9D1L);
        int k = 0;
        // Remeras: del extremo del brazo, de arriba-izquierda hacia la izquierda.
        for (int i = 0; i < 8; i++, k++) {
            along[k] = 1.0 - 0.16 * i / 7;
            open[k] = -58 - 50.0 * i / 7;
            lenF[k] = 0.50 - 0.10 * i / 7;
            delay[k] = 0.10 + 0.02 * (7 - i);
            flapAmp[k] = 5;
        }
        // Secundarias: cuelgan del brazo, hacia abajo y afuera.
        for (int i = 0; i < 8; i++, k++) {
            along[k] = 0.10 + 0.70 * i / 7;
            open[k] = -168 + 46.0 * i / 7;
            lenF[k] = 0.36 + 0.08 * i / 7;
            delay[k] = 0.04 + 0.02 * i;
            flapAmp[k] = 3;
        }
        // Coberteras: cortas, por encima, tapando las raices.
        for (int i = 0; i < 9; i++, k++) {
            along[k] = 0.06 + 0.88 * i / 8;
            open[k] = -150 + 65.0 * i / 8;
            lenF[k] = 0.19 - 0.04 * i / 8;
            delay[k] = 0.06 + 0.02 * i;
            flapAmp[k] = 2;
        }
        for (int i = 0; i < WING; i++) {
            detach[i] = DETACH + r.nextDouble() * 0.14;
            driftX[i] = -(0.10 + r.nextDouble() * 0.30);
            spin[i] = (r.nextBoolean() ? 1 : -1) * (90 + r.nextDouble() * 160);
        }
        for (int i = 0; i < phase.length; i++) {
            phase[i] = r.nextDouble() * Math.PI * 2;
        }
        for (int i = 0; i < FALL; i++) {
            fx[i] = r.nextDouble();
            fy[i] = -0.18 + r.nextDouble() * 0.8;
            fLen[i] = 0.04 + r.nextDouble() * 0.05;
            fVy[i] = 0.09 + r.nextDouble() * 0.10;
            fAmp[i] = 0.5 + r.nextDouble() * 0.9;
            fOmega[i] = 1.8 + r.nextDouble() * 1.6;
            fFlip[i] = 2.5 + r.nextDouble() * 3.5;
            fBase[i] = (r.nextDouble() - 0.5) * 100 + (r.nextBoolean() ? 0 : 180);
            fDelay[i] = r.nextDouble() * 0.35;
            fFront[i] = r.nextDouble() < 0.35;
        }
        for (int i = 0; i < BURST; i++) {
            bAngle[i] = -Math.PI / 2 + ((i + 0.5) / BURST - 0.5) * Math.PI * 1.3 + (r.nextDouble() - 0.5) * 0.25;
            bSpeed[i] = 3.0 + r.nextDouble() * 2.0;
            bLen[i] = 0.40 + r.nextDouble() * 0.25;
            bSpin[i] = (r.nextBoolean() ? 1 : -1) * (60 + r.nextDouble() * 120);
        }
    }

    @Override
    public int count() {
        return COUNT;
    }

    @Override
    public SignatureSprite sprite(final int i) {
        return SignatureSprite.FEATHER;
    }

    /** El ala, de la imagen grande; la lluvia y el estallido, de la pequenya. */
    @Override
    public boolean big(final int i) {
        return i < WING;
    }

    /** Si esa pluma va por delante de la carta grande (las demas, por detras). */
    @Override
    public boolean inFront(final int i) {
        if (i < WING) {
            return false;
        }
        if (i < WING + FALL) {
            return fFront[i - WING];
        }
        return true;
    }

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
     * @param out {@link #COUNT} * {@link #STRIDE} numeros
     */
    @Override
    public void frame(final double t, final double duration, final double cardX, final double cardY,
            final double cardW, final double cardH, final double landAt,
            final double slotX, final double slotY, final double slotH, final double[] out) {
        // EL ALA. El hombro, por detras de la esquina de arriba a la izquierda.
        final double u = cardH;
        final double sx = cardX - cardW * 0.5 * 0.5;
        final double sy = cardY - cardH * 0.5 * 0.42;
        final double arm = easeOutCubic(clamp01((t - 0.04) / 0.30));
        final double wx = sx - 0.56 * u * arm;
        final double wy = sy - 0.20 * u * arm;
        final double flapOn = clamp01((t - 0.40) / 0.15);
        for (int i = 0; i < WING; i++) {
            final int o = i * STRIDE;
            final double p = clamp01((t - delay[i]) / 0.30);
            final double back = easeOutBack(p);
            double x = sx + (wx - sx) * along[i];
            double y = sy + (wy - sy) * along[i];
            double rot = FOLDED + (open[i] - FOLDED) * back
                    + flapAmp[i] * flapOn * Math.sin((t - 0.40) * 6.5 + along[i] * 0.8);
            final double len = u * lenF[i] * (0.35 + 0.65 * easeOutCubic(p));
            double flip = 1;
            double alpha = clamp01((t - delay[i] + 0.02) / 0.08);
            final double tau = t - detach[i];
            if (tau > 0) {
                // SE DESHACE: cada pluma se suelta, cae y gira.
                x += driftX[i] * u * tau;
                y += u * (0.20 * tau + 0.9 * tau * tau);
                rot += spin[i] * tau;
                flip = 0.4 + 0.6 * Math.abs(Math.cos(tau * 8 + phase[i]));
                alpha *= clamp01(1 - tau / 0.30);
            }
            put(out, o, x, y, rot, len, flip, alpha);
        }
        // LA LLUVIA, por toda la pantalla. Se mece alrededor de su centro, y
        // se apaga cuando la carta se posa: lo que se mueve por TODA la
        // pantalla obliga a repintarla entera, y a 4K eso baja de 60 a ~40 fps
        // pase lo que pase debajo (medido). Despues solo queda el estallido,
        // que es pequenyo y alrededor de su sitio.
        final double rainFade = clamp01((landAt + RAIN_AFTER_LANDING - t) / 0.30);
        final double endFade = clamp01((duration - t) / 0.35);
        for (int i = 0; i < FALL; i++) {
            final int o = (WING + i) * STRIDE;
            final double tau = t - fDelay[i];
            if (tau <= 0) {
                put(out, o, 0, 0, 0, 0, 1, 0);
                continue;
            }
            final double len = h * fLen[i];
            final double ph = fOmega[i] * tau + phase[WING + i];
            final double cx = fx[i] * w + fAmp[i] * len * Math.sin(ph);
            final double cy = (fy[i] + fVy[i] * tau) * h;
            final double rot = fBase[i] + 35 * Math.cos(ph);
            final double rad = Math.toRadians(rot);
            // La forma gira por su base: se pone la base para que el centro quede en (cx,cy).
            final double x = cx - Math.sin(rad) * len / 2;
            final double y = cy + Math.cos(rad) * len / 2;
            final double flip = 0.35 + 0.65 * Math.abs(Math.cos(fFlip[i] * tau + phase[WING + i]));
            put(out, o, x, y, rot, len, flip, 0.9 * clamp01(tau / 0.30) * rainFade);
        }
        // EL ESTALLIDO al posarse.
        for (int i = 0; i < BURST; i++) {
            final int o = (WING + FALL + i) * STRIDE;
            final double tau = t - landAt;
            if (tau <= 0 || Double.isNaN(slotX) || slotH <= 0) {
                put(out, o, 0, 0, 0, 0, 1, 0);
                continue;
            }
            final double dist = slotH * bSpeed[i] * (1 - Math.exp(-5 * tau)) / 5;
            final double cx = slotX + Math.cos(bAngle[i]) * dist;
            final double cy = slotY - slotH * 0.1 + Math.sin(bAngle[i]) * dist + slotH * 0.6 * tau * tau;
            final double len = Math.max(20, slotH * bLen[i]);
            final double rot = Math.toDegrees(bAngle[i]) + 90 + bSpin[i] * tau;
            final double rad = Math.toRadians(rot);
            final double x = cx - Math.sin(rad) * len / 2;
            final double y = cy + Math.cos(rad) * len / 2;
            final double flip = 0.4 + 0.6 * Math.abs(Math.cos(7 * tau + phase[WING + FALL + i]));
            final double alpha = clamp01(tau / 0.05) * clamp01(1 - (tau - 0.45) / 0.40) * endFade;
            put(out, o, x, y, rot, len, flip, alpha);
        }
    }

    private static void put(final double[] out, final int o, final double x, final double y, final double rot,
            final double len, final double flip, final double alpha) {
        out[o + X] = x;
        out[o + Y] = y;
        out[o + ROT] = rot;
        out[o + LEN] = len;
        out[o + FLIP] = flip;
        out[o + ALPHA] = alpha;
    }

    private static double clamp01(final double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double easeOutCubic(final double p) {
        final double q = 1 - p;
        return 1 - q * q * q;
    }

    /** Pasa un poco y vuelve: el ala se abre de golpe, como un chasquido. */
    private static double easeOutBack(final double p) {
        final double c1 = 1.70158;
        final double c3 = c1 + 1;
        final double q = p - 1;
        return 1 + c3 * q * q * q + c1 * q * q;
    }
}
