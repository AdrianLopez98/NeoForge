package forge.neo.match;

import java.util.Random;

/**
 * <b>El viento de Tsushima</b> ({@link CommanderSignature#JIN_LEAVES}), para
 * Jin Sakai: hojas rojas de arce cruzan la pantalla de izquierda a derecha
 * dando vueltas, una katana corta la carta grande de un solo tajo, y al
 * posarse saltan hojas de su sitio que se lleva el viento. Ver
 * {@link SignatureMotion}.
 */
public final class JinLeaves implements SignatureMotion {

    private static final int WIND = 26;
    private static final int SLASH_AT = WIND;
    private static final int BURST_AT = SLASH_AT + 2;
    private static final int BURST = 12;
    private static final int COUNT = BURST_AT + BURST;

    /** El tajo de la katana: cuando, y en que direccion (de abajo a la izquierda a arriba a la derecha). */
    private static final double SLASH_WHEN = 0.50;
    private static final double SLASH_ANGLE = 68;
    /** Hasta cuando sopla el viento despues de posarse la carta (y 0,3 s mas apagandose). */
    private static final double WIND_AFTER_LANDING = 0.2;

    private final double w;
    private final double h;
    private final double[] x0 = new double[WIND];
    private final double[] y0 = new double[WIND];
    private final double[] vx = new double[WIND];
    private final double[] vy = new double[WIND];
    private final double[] bob = new double[WIND];
    private final double[] bobW = new double[WIND];
    private final double[] spin = new double[WIND + BURST];
    private final double[] rot0 = new double[WIND + BURST];
    private final double[] tumble = new double[WIND + BURST];
    private final double[] phase = new double[WIND + BURST];
    private final double[] len = new double[WIND];
    private final double[] delay = new double[WIND];
    private final boolean[] front = new boolean[WIND];
    private final double[] burstAngle = new double[BURST];
    private final double[] burstSpeed = new double[BURST];
    private final double[] burstLen = new double[BURST];

    public JinLeaves(final double width, final double height) {
        this.w = width;
        this.h = height;
        final Random r = new Random(0x75C5L);
        for (int i = 0; i < WIND; i++) {
            x0[i] = (-0.35 + r.nextDouble() * 1.1) * w;
            y0[i] = (0.05 + r.nextDouble() * 0.75) * h;
            vx[i] = (0.32 + r.nextDouble() * 0.28) * w;
            vy[i] = (0.02 + r.nextDouble() * 0.06) * h;
            bob[i] = (0.01 + r.nextDouble() * 0.03) * h;
            bobW[i] = 2.5 + r.nextDouble() * 2;
            len[i] = (0.035 + r.nextDouble() * 0.03) * h;
            delay[i] = r.nextDouble() * 0.25;
            front[i] = r.nextDouble() < 0.4;
        }
        for (int i = 0; i < WIND + BURST; i++) {
            spin[i] = (r.nextBoolean() ? 1 : -1) * (120 + r.nextDouble() * 240);
            rot0[i] = r.nextDouble() * 360;
            tumble[i] = 3 + r.nextDouble() * 4;
            phase[i] = r.nextDouble() * Math.PI * 2;
        }
        for (int i = 0; i < BURST; i++) {
            burstAngle[i] = -70 + 140.0 * (i + 0.5) / BURST + (r.nextDouble() - 0.5) * 14;
            burstSpeed[i] = 2.0 + r.nextDouble() * 1.5;
            burstLen[i] = 0.22 + r.nextDouble() * 0.12;
        }
    }

    @Override
    public int count() {
        return COUNT;
    }

    @Override
    public SignatureSprite sprite(final int i) {
        if (i == SLASH_AT) {
            return SignatureSprite.SLASH_GLOW_WHITE;
        }
        if (i == SLASH_AT + 1) {
            return SignatureSprite.SLASH_WHITE;
        }
        return SignatureSprite.MAPLE_LEAF;
    }

    @Override
    public boolean big(final int i) {
        return i == SLASH_AT || i == SLASH_AT + 1;
    }

    @Override
    public boolean inFront(final int i) {
        return i >= WIND || front[i];
    }

    @Override
    public void frame(final double t, final double duration, final double cardX, final double cardY,
            final double cardW, final double cardH, final double landAt,
            final double slotX, final double slotY, final double slotH, final double[] out) {
        // EL VIENTO: de izquierda a derecha por toda la pantalla, y se calma al
        // posarse la carta (lo que se mueve por TODA la pantalla obliga a
        // repintarla entera: ver BlackFeathers).
        final double windFade = SignatureMotion.clamp01((landAt + WIND_AFTER_LANDING - t) / 0.30);
        for (int i = 0; i < WIND; i++) {
            final double tau = t - delay[i];
            if (tau <= 0) {
                SignatureMotion.hide(out, i);
                continue;
            }
            final double cx = x0[i] + vx[i] * tau;
            final double cy = y0[i] + vy[i] * tau + bob[i] * Math.sin(bobW[i] * tau + phase[i]);
            final double rot = rot0[i] + spin[i] * tau;
            final double flip = 0.25 + 0.75 * Math.abs(Math.cos(tumble[i] * tau + phase[i]));
            SignatureMotion.putCentered(out, i, cx, cy, rot, len[i], flip,
                    0.95 * SignatureMotion.clamp01(tau / 0.25) * windFade);
        }
        // LA KATANA: un solo tajo que barre la carta, se queda y se apaga.
        final double u = t - SLASH_WHEN;
        if (u <= 0) {
            SignatureMotion.hide(out, SLASH_AT);
            SignatureMotion.hide(out, SLASH_AT + 1);
        } else {
            final double max = cardH * 1.15;
            final double g = SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.06));
            final double alpha = u < 0.14 ? 1 : SignatureMotion.clamp01(1 - (u - 0.14) / 0.25);
            final double rad = Math.toRadians(SLASH_ANGLE);
            final double bx = cardX - Math.sin(rad) * max / 2;
            final double by = cardY + cardH * 0.05 + Math.cos(rad) * max / 2;
            SignatureMotion.put(out, SLASH_AT, bx, by, SLASH_ANGLE, max * g, 3.4, 0.9 * alpha);
            SignatureMotion.put(out, SLASH_AT + 1, bx, by, SLASH_ANGLE, max * g, 0.6 + 0.4 * alpha, alpha);
        }
        // AL POSARSE: hojas que saltan de su sitio y se lleva el viento.
        final double endFade = SignatureMotion.clamp01((duration - t) / 0.3);
        for (int i = 0; i < BURST; i++) {
            final int k = WIND + i;
            final double tau = t - landAt;
            if (tau <= 0 || Double.isNaN(slotX) || slotH <= 0) {
                SignatureMotion.hide(out, BURST_AT + i);
                continue;
            }
            final double a = Math.toRadians(burstAngle[i]);
            final double dist = slotH * burstSpeed[i] * (1 - Math.exp(-5 * tau)) / 5;
            final double cx = slotX + Math.sin(a) * dist + slotH * 1.2 * tau;
            final double cy = slotY - slotH * 0.1 - Math.cos(a) * dist + slotH * 0.3 * tau * tau;
            final double l = Math.max(14, slotH * burstLen[i]);
            final double rot = rot0[k] + spin[k] * tau;
            final double flip = 0.3 + 0.7 * Math.abs(Math.cos(tumble[k] * tau + phase[k]));
            final double alpha = SignatureMotion.clamp01(tau / 0.05)
                    * SignatureMotion.clamp01(1 - (tau - 0.4) / 0.4) * endFade;
            SignatureMotion.putCentered(out, BURST_AT + i, cx, cy, rot, l, flip, alpha);
        }
    }
}
