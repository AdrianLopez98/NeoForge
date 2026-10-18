package forge.neo.match;

import java.util.Random;

/**
 * <b>El Omnislash de Cloud</b> ({@link CommanderSignature#CLOUD_SLASHES}): cinco
 * tajos azules y blancos cruzan la carta grande uno detras de otro, cada uno
 * con su halo y soltando chispas, y al posarse en la mesa salta un abanico de
 * chispas. Todo por delante de la carta. Ver {@link SignatureMotion}.
 */
public final class CloudSlashes implements SignatureMotion {

    private static final int SLASHES = 5;
    private static final int SPARKS_PER_SLASH = 6;
    private static final int BURST = 14;
    private static final int SPARKS_AT = SLASHES * 2;
    private static final int BURST_AT = SPARKS_AT + SLASHES * SPARKS_PER_SLASH;
    private static final int COUNT = BURST_AT + BURST;

    /** Cuando sale cada tajo, su angulo y donde cruza (en fraccion de la carta). */
    private static final double[] WHEN = {0.30, 0.40, 0.50, 0.60, 0.74};
    private static final double[] ANGLE = {-58, 52, -12, 78, -38};
    private static final double[] DX = {-0.05, 0.06, 0, -0.04, 0};
    private static final double[] DY = {-0.08, 0.05, -0.02, 0.07, 0};

    private final double[] sparkAngle = new double[SLASHES * SPARKS_PER_SLASH];
    private final double[] sparkSpeed = new double[SLASHES * SPARKS_PER_SLASH];
    private final double[] sparkLen = new double[SLASHES * SPARKS_PER_SLASH];
    private final double[] burstAngle = new double[BURST];
    private final double[] burstSpeed = new double[BURST];
    private final double[] burstLen = new double[BURST];

    public CloudSlashes(final double width, final double height) {
        final Random r = new Random(0xC10D5L);
        for (int k = 0; k < SLASHES; k++) {
            for (int j = 0; j < SPARKS_PER_SLASH; j++) {
                final int i = k * SPARKS_PER_SLASH + j;
                // A los dos lados del tajo, en abanico.
                sparkAngle[i] = ANGLE[k] + (j % 2 == 0 ? 90 : -90) + (r.nextDouble() - 0.5) * 100;
                sparkSpeed[i] = 1.5 + r.nextDouble() * 1.5;
                sparkLen[i] = 0.05 + r.nextDouble() * 0.04;
            }
        }
        for (int i = 0; i < BURST; i++) {
            burstAngle[i] = -75 + 150.0 * (i + 0.5) / BURST + (r.nextDouble() - 0.5) * 12;
            burstSpeed[i] = 3.0 + r.nextDouble() * 2.0;
            burstLen[i] = 0.35 + r.nextDouble() * 0.2;
        }
    }

    @Override
    public int count() {
        return COUNT;
    }

    @Override
    public SignatureSprite sprite(final int i) {
        if (i < SPARKS_AT) {
            return i % 2 == 0 ? SignatureSprite.SLASH_GLOW_BLUE : SignatureSprite.SLASH_BLUE;
        }
        return SignatureSprite.SPARK_BLUE;
    }

    @Override
    public boolean big(final int i) {
        return i < SPARKS_AT;
    }

    @Override
    public boolean inFront(final int i) {
        return true;
    }

    @Override
    public void frame(final double t, final double duration, final double cardX, final double cardY,
            final double cardW, final double cardH, final double landAt,
            final double slotX, final double slotY, final double slotH, final double[] out) {
        for (int k = 0; k < SLASHES; k++) {
            final double u = t - WHEN[k];
            final double max = cardH * (k == SLASHES - 1 ? 1.55 : 1.2 + 0.05 * k);
            final double midX = cardX + cardW * DX[k];
            final double midY = cardY + cardH * DY[k];
            if (u <= 0) {
                SignatureMotion.hide(out, 2 * k);
                SignatureMotion.hide(out, 2 * k + 1);
            } else {
                // EL TAJO: barre de un extremo al otro (la base quieta y la
                // punta avanzando), se queda un instante y se apaga.
                final double g = SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.07));
                final double alpha = u < 0.12 ? 1 : SignatureMotion.clamp01(1 - (u - 0.12) / 0.22);
                final double rad = Math.toRadians(ANGLE[k]);
                final double bx = midX - Math.sin(rad) * max / 2;
                final double by = midY + Math.cos(rad) * max / 2;
                SignatureMotion.put(out, 2 * k, bx, by, ANGLE[k], max * g, 3.2, 0.9 * alpha);
                SignatureMotion.put(out, 2 * k + 1, bx, by, ANGLE[k], max * g, 0.6 + 0.4 * alpha, alpha);
            }
            // LAS CHISPAS, del centro del tajo hacia los lados.
            for (int j = 0; j < SPARKS_PER_SLASH; j++) {
                final int i = k * SPARKS_PER_SLASH + j;
                final double tau = u - 0.04;
                if (tau <= 0 || tau > 0.45) {
                    SignatureMotion.hide(out, SPARKS_AT + i);
                    continue;
                }
                final double a = Math.toRadians(sparkAngle[i]);
                final double dist = cardH * sparkSpeed[i] * (1 - Math.exp(-4 * tau)) / 4;
                final double cx = midX + Math.sin(a) * dist;
                final double cy = midY - Math.cos(a) * dist + cardH * 0.4 * tau * tau;
                final double len = cardH * sparkLen[i] * (1 - 0.5 * SignatureMotion.clamp01(tau / 0.4));
                final double alpha = SignatureMotion.clamp01(tau / 0.03) * SignatureMotion.clamp01(1 - tau / 0.42);
                SignatureMotion.putCentered(out, SPARKS_AT + i, cx, cy, sparkAngle[i], len, 1, alpha);
            }
        }
        // AL POSARSE: un abanico de chispas hacia arriba.
        final double endFade = SignatureMotion.clamp01((duration - t) / 0.3);
        for (int i = 0; i < BURST; i++) {
            final double tau = t - landAt;
            if (tau <= 0 || Double.isNaN(slotX) || slotH <= 0) {
                SignatureMotion.hide(out, BURST_AT + i);
                continue;
            }
            final double a = Math.toRadians(burstAngle[i]);
            final double dist = slotH * burstSpeed[i] * (1 - Math.exp(-5 * tau)) / 5;
            final double cx = slotX + Math.sin(a) * dist;
            final double cy = slotY - slotH * 0.1 - Math.cos(a) * dist;
            final double len = Math.max(14, slotH * burstLen[i]) * (1 - 0.4 * SignatureMotion.clamp01(tau / 0.5));
            final double alpha = SignatureMotion.clamp01(tau / 0.04)
                    * SignatureMotion.clamp01(1 - (tau - 0.25) / 0.35) * endFade;
            SignatureMotion.putCentered(out, BURST_AT + i, cx, cy, burstAngle[i], len, 1, alpha);
        }
    }
}
