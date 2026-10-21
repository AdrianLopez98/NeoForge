package forge.neo.match;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * <b>Una firma hecha de capas</b> ({@link CommanderSignature}): las diez de los
 * comandantes mas jugados de EDH (Ana, 20-10-2026: <i>"busca los 10 mas usados
 * de edh y hazselas, que sean igual que cloud ... que no consuma de mas ni
 * rompa nada"</i>).
 *
 * <p>Cada capa es una pieza que ya existia suelta en {@link BlackFeathers},
 * {@link CloudSlashes} o {@link JinLeaves} (lo que cae, lo que sopla, un tajo,
 * el estallido al posarse), y una firma es una mezcla de capas con sus dibujos.
 * Las mismas reglas que las otras tres, que son las que las hacen baratas:
 *
 * <ul>
 *   <li><b>Sin estado por fotograma</b>: todo sale del instante y de unos
 *       parametros sorteados una vez con semilla fija.</li>
 *   <li><b>Lo que se mueve por TODA la pantalla se apaga al posarse la
 *       carta</b> (mas 0,2 s): obliga a repintarla entera.</li>
 *   <li><b>Nunca mas de unos 60 dibujos</b>, como las plumas de Sephiroth, y
 *       cada uno es una imagen pintada una vez que solo se mueve.</li>
 * </ul>
 *
 * <p>Java puro, en el jar: la pintan el escritorio y Android con los pintores
 * de siempre ({@code CommanderEntrance}, {@code EntradaComandante.kt}).
 */
public final class LayeredSignature implements SignatureMotion {

    /** Lo que una capa necesita saber del instante. */
    static final class Ctx {
        double t;
        double duration;
        double cardX;
        double cardY;
        double cardW;
        double cardH;
        double landAt;
        double slotX;
        double slotY;
        double slotH;
        double w;
        double h;

        /** Lo que se mueve por toda la pantalla: entero hasta posarse y 0,3 s apagandose. */
        double screenFade() {
            return SignatureMotion.clamp01((landAt + 0.2 - t) / 0.3);
        }

        double endFade() {
            return SignatureMotion.clamp01((duration - t) / 0.3);
        }

        /** El margen con los bordes de lo que no puede salirse (el Ojo de Sauron). */
        double margin() {
            return Math.max(6, h * 0.02);
        }
    }

    /** Una capa: unos cuantos dibujos con su coreografia. */
    abstract static class Layer {
        final int n;

        Layer(final int n) {
            this.n = n;
        }

        abstract SignatureSprite sprite(int i);

        boolean big(final int i) {
            return false;
        }

        abstract boolean inFront(int i);

        abstract void frame(Ctx c, double[] out, int base);
    }

    private final List<Layer> layers = new ArrayList<>();
    private final int[] start;
    private final int count;
    private final Ctx ctx = new Ctx();

    LayeredSignature(final double width, final double height, final Layer... ls) {
        ctx.w = width;
        ctx.h = height;
        start = new int[ls.length];
        int c = 0;
        for (int k = 0; k < ls.length; k++) {
            layers.add(ls[k]);
            start[k] = c;
            c += ls[k].n;
        }
        count = c;
    }

    private int layerOf(final int i) {
        for (int k = start.length - 1; k >= 0; k--) {
            if (i >= start[k]) {
                return k;
            }
        }
        return 0;
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public SignatureSprite sprite(final int i) {
        final int k = layerOf(i);
        return layers.get(k).sprite(i - start[k]);
    }

    @Override
    public boolean big(final int i) {
        final int k = layerOf(i);
        return layers.get(k).big(i - start[k]);
    }

    @Override
    public boolean inFront(final int i) {
        final int k = layerOf(i);
        return layers.get(k).inFront(i - start[k]);
    }

    @Override
    public void frame(final double t, final double duration, final double cardX, final double cardY,
            final double cardW, final double cardH, final double landAt,
            final double slotX, final double slotY, final double slotH, final double[] out) {
        ctx.t = t;
        ctx.duration = duration;
        ctx.cardX = cardX;
        ctx.cardY = cardY;
        ctx.cardW = cardW;
        ctx.cardH = cardH;
        ctx.landAt = landAt;
        ctx.slotX = slotX;
        ctx.slotY = slotY;
        ctx.slotH = slotH;
        for (int k = 0; k < layers.size(); k++) {
            layers.get(k).frame(ctx, out, start[k]);
        }
    }

    // ==================================================================
    //  Las capas
    // ==================================================================

    /** Como se mueve lo que cruza la pantalla. */
    enum Flow { FALL, RISE, LEFT, RIGHT }

    /**
     * LO QUE CRUZA LA PANTALLA: cae (plumas, ceniza, hojas), sube (motas),
     * o vuela de lado (murcielagos). Con giro o aleteando ({@code flap}: el
     * dibujo se estrecha y se ensancha, como un ala).
     */
    static final class Drift extends Layer {
        private final SignatureSprite[] sprites;
        private final Flow flow;
        private final boolean flap;
        private final double[] x0;
        private final double[] y0;
        private final double[] speed;
        private final double[] sway;
        private final double[] swayW;
        private final double[] phase;
        private final double[] rot0;
        private final double[] spin;
        private final double[] len;
        private final double[] delay;
        private final boolean[] front;

        Drift(final int n, final Flow flow, final double size, final double frontShare, final boolean spin,
                final boolean flap, final long seed, final SignatureSprite... sprites) {
            super(n);
            this.sprites = sprites;
            this.flow = flow;
            this.flap = flap;
            x0 = new double[n];
            y0 = new double[n];
            speed = new double[n];
            sway = new double[n];
            swayW = new double[n];
            phase = new double[n];
            rot0 = new double[n];
            this.spin = new double[n];
            len = new double[n];
            delay = new double[n];
            front = new boolean[n];
            final Random r = new Random(seed);
            for (int i = 0; i < n; i++) {
                x0[i] = r.nextDouble();
                y0[i] = r.nextDouble();
                speed[i] = 0.22 + r.nextDouble() * 0.2;
                sway[i] = 0.01 + r.nextDouble() * 0.03;
                swayW[i] = 2 + r.nextDouble() * 2.5;
                phase[i] = r.nextDouble() * Math.PI * 2;
                rot0[i] = spin ? r.nextDouble() * 360 : (r.nextDouble() - 0.5) * 30;
                this.spin[i] = spin ? (r.nextBoolean() ? 1 : -1) * (60 + r.nextDouble() * 160) : 0;
                len[i] = size * (0.7 + r.nextDouble() * 0.6);
                delay[i] = r.nextDouble() * 0.3;
                front[i] = r.nextDouble() < frontShare;
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return sprites[i % sprites.length];
        }

        @Override
        boolean inFront(final int i) {
            return front[i];
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = c.screenFade();
            for (int i = 0; i < n; i++) {
                final double tau = c.t - delay[i];
                if (tau <= 0 || fade <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double wob = Math.sin(swayW[i] * tau + phase[i]);
                double cx;
                double cy;
                double rot = rot0[i] + spin[i] * tau;
                switch (flow) {
                    case RISE:
                        cx = x0[i] * c.w + sway[i] * c.w * wob;
                        cy = (1.05 - y0[i] * 0.4) * c.h - speed[i] * c.h * tau;
                        break;
                    case LEFT:
                        cx = (1.1 - x0[i] * 0.5) * c.w - speed[i] * 1.6 * c.w * tau;
                        cy = (0.08 + y0[i] * 0.6) * c.h + sway[i] * c.h * 2 * wob;
                        rot = 12 * wob;
                        break;
                    case RIGHT:
                        cx = (-0.1 + x0[i] * 0.5) * c.w + speed[i] * 1.6 * c.w * tau;
                        cy = (0.08 + y0[i] * 0.6) * c.h + sway[i] * c.h * 2 * wob;
                        rot = 12 * wob;
                        break;
                    case FALL:
                    default:
                        cx = x0[i] * c.w + sway[i] * c.w * wob;
                        cy = (-0.08 + y0[i] * 0.45) * c.h + speed[i] * c.h * tau;
                        break;
                }
                final double flip = flap ? 0.35 + 0.65 * Math.abs(Math.sin(14 * tau + phase[i]))
                        : 0.6 + 0.4 * Math.abs(Math.cos(3 * tau + phase[i]));
                final double alpha = 0.95 * SignatureMotion.clamp01(tau / 0.2) * fade;
                SignatureMotion.putCentered(out, base + i, cx, cy, rot, len[i] * c.h, flip, alpha);
            }
        }
    }

    /**
     * LLAMAS (o motas) QUE SUBEN DESDE LA CARTA: nacen a sus pies, suben un
     * tramo meciendose y se apagan, una y otra vez, hasta que se posa.
     */
    static final class Rise extends Layer {
        private final SignatureSprite[] sprites;
        private final double[] off;
        private final double[] period;
        private final double[] phase;
        private final double[] len;
        private final boolean[] front;
        private final double height;

        Rise(final int n, final double size, final double height, final double frontShare, final long seed,
                final SignatureSprite... sprites) {
            super(n);
            this.sprites = sprites;
            this.height = height;
            off = new double[n];
            period = new double[n];
            phase = new double[n];
            len = new double[n];
            front = new boolean[n];
            final Random r = new Random(seed);
            for (int i = 0; i < n; i++) {
                off[i] = (r.nextDouble() - 0.5) * 3.4;
                period[i] = 0.45 + r.nextDouble() * 0.35;
                phase[i] = r.nextDouble();
                len[i] = size * (0.6 + r.nextDouble() * 0.7);
                front[i] = r.nextDouble() < frontShare;
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return sprites[i % sprites.length];
        }

        @Override
        boolean inFront(final int i) {
            return front[i];
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = SignatureMotion.clamp01((c.landAt - c.t) / 0.25) * SignatureMotion.clamp01(c.t / 0.2);
            for (int i = 0; i < n; i++) {
                if (fade <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double f = (c.t / period[i] + phase[i]) % 1.0;
                final double cx = c.cardX + c.cardW * 0.5 * off[i] + c.cardW * 0.05 * Math.sin(f * 6.3 + phase[i] * 9);
                final double cy = c.cardY + c.cardH * 0.45 - f * c.cardH * height;
                final double l = len[i] * c.cardH * (1 - 0.5 * f);
                final double alpha = Math.sin(Math.PI * f) * fade;
                SignatureMotion.putCentered(out, base + i, cx, cy, (off[i] * 12), l, 0.8 + 0.2 * Math.sin(f * 9), alpha);
            }
        }
    }

    /**
     * ESTRELLAS QUIETAS QUE TITILAN, repartidas por la pantalla o alrededor de
     * la carta ({@code aroundCard}).
     */
    static final class Twinkle extends Layer {
        private final SignatureSprite sprite;
        private final boolean aroundCard;
        private final double[] x;
        private final double[] y;
        private final double[] wHz;
        private final double[] phase;
        private final double[] len;
        private final double[] delay;
        private final boolean[] front;

        Twinkle(final int n, final boolean aroundCard, final double size, final double frontShare, final long seed,
                final SignatureSprite sprite) {
            super(n);
            this.sprite = sprite;
            this.aroundCard = aroundCard;
            x = new double[n];
            y = new double[n];
            wHz = new double[n];
            phase = new double[n];
            len = new double[n];
            delay = new double[n];
            front = new boolean[n];
            final Random r = new Random(seed);
            for (int i = 0; i < n; i++) {
                if (aroundCard) {
                    final double a = r.nextDouble() * Math.PI * 2;
                    final double d = 0.55 + r.nextDouble() * 0.5;
                    x[i] = Math.sin(a) * d * 0.9;
                    y[i] = -Math.cos(a) * d * 0.62;
                } else {
                    x[i] = r.nextDouble();
                    y[i] = r.nextDouble() * 0.85;
                }
                wHz[i] = 5 + r.nextDouble() * 5;
                phase[i] = r.nextDouble() * Math.PI * 2;
                len[i] = size * (0.6 + r.nextDouble() * 0.8);
                delay[i] = 0.05 + r.nextDouble() * 0.45;
                front[i] = r.nextDouble() < frontShare;
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return sprite;
        }

        @Override
        boolean inFront(final int i) {
            return front[i];
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = aroundCard ? SignatureMotion.clamp01((c.landAt - c.t) / 0.2) : c.screenFade();
            for (int i = 0; i < n; i++) {
                final double tau = c.t - delay[i];
                if (tau <= 0 || fade <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double cx = aroundCard ? c.cardX + x[i] * c.cardH : x[i] * c.w;
                final double cy = aroundCard ? c.cardY + y[i] * c.cardH : y[i] * c.h;
                final double pulse = 0.55 + 0.45 * Math.sin(wHz[i] * tau + phase[i]);
                final double grow = SignatureMotion.easeOutCubic(SignatureMotion.clamp01(tau / 0.15));
                final double l = len[i] * c.cardH * (0.7 + 0.3 * pulse) * grow;
                SignatureMotion.putCentered(out, base + i, cx, cy, 0, l, 1, pulse * fade);
            }
        }
    }

    /**
     * UNO GRANDE DETRAS (o delante) DE LA CARTA: la luna, el ojo, un resplandor.
     * Aparece creciendo, late, puede girar, y se va al posarse.
     */
    static final class Halo extends Layer {
        private final SignatureSprite sprite;
        private final boolean front;
        private final double from;
        private final double size;
        private final double spinDeg;
        private final double dx;
        private final double dy;
        private final double rot;
        /** Media anchura y media altura del dibujo, en partes de su largo; 0 = no se encaja (un resplandor). */
        private final double halfW;
        private final double halfH;
        /** Donde va: donde digan dx/dy, encima de la carta o abrazando su borde izquierdo. */
        private final Anchor anchor;
        /** Cuanto se ensancha el dibujo (1 = como esta). */
        private final double stretch;

        Halo(final SignatureSprite sprite, final boolean front, final double from, final double size,
                final double spinDeg, final double dx, final double dy, final double rot) {
            this(sprite, front, from, size, spinDeg, dx, dy, rot, 0, 0, Anchor.CARD, 1);
        }

        Halo(final SignatureSprite sprite, final boolean front, final double from, final double size,
                final double spinDeg, final double dx, final double dy, final double rot,
                final double halfW, final double halfH, final Anchor anchor, final double stretch) {
            super(1);
            this.halfW = halfW;
            this.halfH = halfH;
            this.anchor = anchor;
            this.stretch = stretch;
            this.sprite = sprite;
            this.front = front;
            this.from = from;
            this.size = size;
            this.spinDeg = spinDeg;
            this.dx = dx;
            this.dy = dy;
            this.rot = rot;
        }

        @Override
        SignatureSprite sprite(final int i) {
            return sprite;
        }

        @Override
        boolean big(final int i) {
            return true;
        }

        @Override
        boolean inFront(final int i) {
            return front;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double u = c.t - from;
            final double out1 = SignatureMotion.clamp01((c.landAt - c.t) / 0.22);
            if (u <= 0 || out1 <= 0) {
                SignatureMotion.hide(out, base);
                return;
            }
            final double grow = SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.3));
            final double pulse = 1 + 0.04 * Math.sin(u * 7);
            double l = c.cardH * size * (0.6 + 0.4 * grow) * pulse;
            double cx = c.cardX + c.cardW * dx;
            double cy = c.cardY + c.cardH * dy;
            if (halfW > 0 && halfH > 0) {
                // Lo que mide en pantalla, ya girado y ensanchado.
                final double a = Math.toRadians(rot + spinDeg * u);
                final double hw = halfW * stretch;
                final double ex = Math.abs(Math.cos(a)) * hw + Math.abs(Math.sin(a)) * halfH;
                final double ey = Math.abs(Math.sin(a)) * hw + Math.abs(Math.cos(a)) * halfH;
                final double m = c.margin();
                if (anchor == Anchor.ABOVE) {
                    // En el hueco entre el borde de arriba y la carta, sin tocarla:
                    // ni la tapa ni queda medio escondido detras.
                    final double top = m * 0.5;
                    final double bottom = c.cardY - c.cardH / 2 - m * 0.5;
                    l = Math.min(l, (bottom - top) / (2 * ey));
                    cy = (top + bottom) / 2;
                } else if (anchor == Anchor.LEFT) {
                    // Fuera de la carta, pegado a su borde izquierdo: DELANTE, para
                    // que no se pierda detras, pero sin taparle el arte.
                    cx = c.cardX - c.cardW / 2 - ex * l * 0.55;
                    cy = c.cardY + c.cardH * dy;
                } else if (anchor == Anchor.RIGHT) {
                    // Lo mismo por la derecha (el sol de Pantlaza).
                    cx = c.cardX + c.cardW / 2 + ex * l * 0.55;
                    cy = c.cardY + c.cardH * dy;
                }
                // Y entero dentro de la pantalla: mas pequenyo si no cabe.
                l = Math.min(l, Math.min(cx - m, c.w - m - cx) / ex);
                l = Math.min(l, Math.min(cy - m, c.h - m - cy) / ey);
                if (l <= c.cardH * 0.12) {
                    // No hay sitio para que se lea: mejor no ponerlo.
                    SignatureMotion.hide(out, base);
                    return;
                }
            }
            SignatureMotion.putCentered(out, base, cx, cy,
                    rot + spinDeg * u, l, stretch, 0.92 * SignatureMotion.clamp01(u / 0.18) * out1);
        }
    }

    /** Donde se pone un {@link Halo}. */
    enum Anchor { CARD, ABOVE, LEFT, RIGHT }

    /** UN TAJO (o un rayo) QUE CRUZA LA CARTA, con su halo si lo lleva: como los de Cloud. */
    static final class Sweep extends Layer {
        private final SignatureSprite glow;
        private final SignatureSprite edge;
        private final double[] when;
        private final double[] angle;
        private final double size;

        Sweep(final SignatureSprite glow, final SignatureSprite edge, final double size, final double[] when,
                final double[] angle) {
            super(when.length * 2);
            this.glow = glow;
            this.edge = edge;
            this.when = when;
            this.angle = angle;
            this.size = size;
        }

        @Override
        SignatureSprite sprite(final int i) {
            return i % 2 == 0 ? glow : edge;
        }

        @Override
        boolean big(final int i) {
            return true;
        }

        @Override
        boolean inFront(final int i) {
            return true;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            for (int k = 0; k < when.length; k++) {
                final double u = c.t - when[k];
                if (u <= 0) {
                    SignatureMotion.hide(out, base + 2 * k);
                    SignatureMotion.hide(out, base + 2 * k + 1);
                    continue;
                }
                final double max = c.cardH * size;
                final double g = SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.07));
                final double alpha = u < 0.12 ? 1 : SignatureMotion.clamp01(1 - (u - 0.12) / 0.22);
                final double rad = Math.toRadians(angle[k]);
                final double bx = c.cardX - Math.sin(rad) * max / 2;
                final double by = c.cardY + Math.cos(rad) * max / 2;
                SignatureMotion.put(out, base + 2 * k, bx, by, angle[k], max * g, 3.0, 0.85 * alpha);
                SignatureMotion.put(out, base + 2 * k + 1, bx, by, angle[k], max * g, 0.7 + 0.3 * alpha, alpha);
            }
        }
    }

    /**
     * GIRAN ALREDEDOR DE LA CARTA y, en {@code release}, salen disparados hacia
     * fuera (bolas de fuego). Con {@code inward}, al reves: entran en espiral
     * desde lejos hasta la carta (las huestes de Kaalia).
     */
    static final class Orbit extends Layer {
        private final SignatureSprite[] sprites;
        private final double from;
        private final double release;
        private final double radius;
        private final double turns;
        private final double size;
        private final boolean inward;

        Orbit(final int n, final double from, final double release, final double radius, final double turns,
                final double size, final boolean inward, final SignatureSprite... sprites) {
            super(n);
            this.sprites = sprites;
            this.from = from;
            this.release = release;
            this.radius = radius;
            this.turns = turns;
            this.size = size;
            this.inward = inward;
        }

        @Override
        SignatureSprite sprite(final int i) {
            return sprites[i % sprites.length];
        }

        @Override
        boolean inFront(final int i) {
            return i % 2 == 0;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            for (int i = 0; i < n; i++) {
                final double u = c.t - from - (inward ? i * 0.025 : 0);
                if (u <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double span = release - from;
                final double p = SignatureMotion.clamp01(u / span);
                final double a0 = Math.PI * 2 * i / n;
                double a;
                double r;
                double alpha;
                if (inward) {
                    // De lejos hasta la carta, cada vez mas deprisa.
                    final double e = p * p;
                    a = a0 + turns * Math.PI * 2 * e;
                    r = c.cardH * radius * (1 - e) + c.cardH * 0.08;
                    alpha = SignatureMotion.clamp01(u / 0.12) * (p < 0.92 ? 1 : SignatureMotion.clamp01((1 - p) / 0.08));
                } else if (c.t < release) {
                    a = a0 + turns * Math.PI * 2 * p;
                    r = c.cardH * radius * SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.2));
                    alpha = SignatureMotion.clamp01(u / 0.12);
                } else {
                    // Sueltas: hacia fuera, apagandose.
                    final double v = c.t - release;
                    a = a0 + turns * Math.PI * 2;
                    r = c.cardH * radius * (1 + 3.5 * v);
                    alpha = SignatureMotion.clamp01(1 - v / 0.35);
                }
                if (alpha <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double cx = c.cardX + Math.sin(a) * r * 0.85;
                final double cy = c.cardY - Math.cos(a) * r * 0.62;
                final double rot = Math.toDegrees(a) + (inward ? 180 : 90);
                SignatureMotion.putCentered(out, base + i, cx, cy, rot, c.cardH * size, 1, alpha);
            }
        }
    }

    /**
     * SE MULTIPLICAN: una chispa sale de la carta, se parte en dos, y cada una
     * en dos... (los trasgos de Krenko). {@code generations} generaciones:
     * 1 + 2 + 4 + ... dibujos.
     */
    static final class Doubling extends Layer {
        private final SignatureSprite[] sprites;
        private final int generations;
        private final double from;
        private final double step;
        private final double[] angle;

        Doubling(final int generations, final double from, final double step, final long seed,
                final SignatureSprite... sprites) {
            super((1 << generations) - 1);
            this.sprites = sprites;
            this.generations = generations;
            this.from = from;
            this.step = step;
            angle = new double[n];
            final Random r = new Random(seed);
            for (int i = 0; i < n; i++) {
                angle[i] = r.nextDouble() * 360;
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return sprites[i % sprites.length];
        }

        @Override
        boolean inFront(final int i) {
            return true;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            int i = 0;
            for (int g = 0; g < generations; g++) {
                final int inGen = 1 << g;
                final double born = from + g * step;
                for (int j = 0; j < inGen; j++, i++) {
                    final double u = c.t - born;
                    if (u <= 0 || u > step * 3.2) {
                        SignatureMotion.hide(out, base + i);
                        continue;
                    }
                    // Repartidas por igual alrededor, con un poco de azar.
                    final double a = Math.toRadians(360.0 * (j + 0.5) / inGen + angle[i] * 0.15);
                    final double dist = c.cardH * (0.5 + 0.35 * g / generations + 1.3 * u);
                    final double cx = c.cardX + Math.sin(a) * dist;
                    final double cy = c.cardY - Math.cos(a) * dist * 0.8;
                    final double alpha = SignatureMotion.clamp01(u / 0.04) * SignatureMotion.clamp01(1 - u / (step * 3.2));
                    SignatureMotion.putCentered(out, base + i, cx, cy, Math.toDegrees(a),
                            c.cardH * (0.2 - 0.02 * g), 1, alpha);
                }
            }
        }
    }

    /**
     * EL PAPEL DE ARROZ (Isshin): la mesa pasa a ser una vinyeta de Vagabond
     * mientras la carta esta en el centro. Es el FONDO: entre la mesa y la
     * carta, no detras de nada que haya que ver. Se va antes de que la carta
     * llegue a su sitio, para que se vea la mesa de verdad.
     */
    static final class Paper extends Layer {
        Paper() {
            super(1);
        }

        @Override
        SignatureSprite sprite(final int i) {
            return SignatureSprite.PAPER;
        }

        @Override
        boolean inFront(final int i) {
            return false;
        }

        static double fade(final Ctx c) {
            return SignatureMotion.clamp01(c.t / 0.14) * SignatureMotion.clamp01((c.landAt - c.t) / 0.35);
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double a = 0.95 * fade(c);
            if (a <= 0) {
                SignatureMotion.hide(out, base);
                return;
            }
            SignatureMotion.putCentered(out, base, c.w / 2, c.h / 2, 0, c.h * 1.1, c.w / c.h, a);
        }
    }

    /**
     * LAS DOS ESPADAS: dos pinceladas negras enormes en cruz sobre el papel,
     * una tras otra, y la tinta que salta al pasar el pincel. Pintadas EN el
     * papel, asi que la carta va delante; pero cruzan toda la pantalla y solo
     * les tapa la franja del medio.
     */
    static final class TwoSwords extends Layer {
        private final double[] when;
        private final double[] rot;
        private final int spots;
        private final double[] q;
        private final double[] side;
        private final double[] dist;
        private final double[] size;

        TwoSwords(final double[] when, final double[] rot, final int spots, final long seed) {
            super(when.length * (1 + spots));
            this.when = when;
            this.rot = rot;
            this.spots = spots;
            final int m = when.length * spots;
            q = new double[m];
            side = new double[m];
            dist = new double[m];
            size = new double[m];
            final Random r = new Random(seed);
            for (int i = 0; i < m; i++) {
                q[i] = 0.12 + r.nextDouble() * 0.8;
                side[i] = r.nextBoolean() ? 1 : -1;
                dist[i] = 0.015 + r.nextDouble() * 0.07;
                size[i] = 0.01 + r.nextDouble() * 0.028;
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return i < when.length ? SignatureSprite.INK_STROKE : SignatureSprite.INK_SPOT;
        }

        @Override
        boolean big(final int i) {
            return i < when.length;
        }

        @Override
        boolean inFront(final int i) {
            return false;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = Paper.fade(c);
            // Enteras en pantalla: cortadas por arriba parecian cerillas.
            final double len = Math.hypot(c.w, c.h) * 0.56;
            final double half = c.h * 0.06;
            // Donde esta la carta mientras se ve grande (CommanderEntrance: 0,46 del alto).
            final double cx = c.w / 2;
            final double cy = c.h * 0.46;
            for (int s = 0; s < when.length; s++) {
                final double u = c.t - when[s];
                final double rad = Math.toRadians(rot[s]);
                final double dx = Math.sin(rad);
                final double dy = -Math.cos(rad);
                final double bx = cx - dx * len / 2;
                final double by = cy - dy * len / 2;
                if (u <= 0 || fade <= 0) {
                    SignatureMotion.hide(out, base + s);
                } else {
                    final double g = SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.09));
                    SignatureMotion.put(out, base + s, bx, by, rot[s], len * g, 0.5, fade);
                }
                for (int k = 0; k < spots; k++) {
                    final int j = s * spots + k;
                    final int o = base + when.length + j;
                    final double v = u - 0.02 - q[j] * 0.09;
                    if (v <= 0 || fade <= 0) {
                        SignatureMotion.hide(out, o);
                        continue;
                    }
                    final double d = half + c.h * dist[j] * SignatureMotion.easeOutCubic(SignatureMotion.clamp01(v / 0.12));
                    final double px = bx + dx * len * q[j] - dy * side[j] * d;
                    final double py = by + dy * len * q[j] + dx * side[j] * d;
                    // La gota apunta hacia donde salio disparada.
                    final double away = Math.toDegrees(Math.atan2(-dy * side[j], -dx * side[j]));
                    SignatureMotion.putCentered(out, o, px, py, away, c.h * size[j], 0.7,
                            SignatureMotion.clamp01(v / 0.03) * fade);
                }
            }
        }
    }

    /**
     * LA FIRMA DE MUSASHI: su nombre, \u6b66\u8535, a pincel junto a la carta (un
     * kanji y luego el otro, empapandose de tinta), y debajo su sello rojo con el
     * nombre en blanco, como los que llevan sus pinturas. Pasado el brazo de la
     * cruz: encima de la tinta negra no se leia.
     */
    static final class MusashiSeal extends Layer {
        private final double from;
        private final double step;

        MusashiSeal(final double from, final double step) {
            super(4);
            this.from = from;
            this.step = step;
        }

        @Override
        SignatureSprite sprite(final int i) {
            switch (i) {
                case 0:
                    return SignatureSprite.KANJI_TAKE;
                case 1:
                    return SignatureSprite.KANJI_ZO;
                case 2:
                    return SignatureSprite.HANKO;
                default:
                    return SignatureSprite.HANKO_TEXT;
            }
        }

        @Override
        boolean big(final int i) {
            return true;
        }

        @Override
        boolean inFront(final int i) {
            return true;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = Paper.fade(c);
            final double m = c.margin();
            // La columna, a la derecha, PASADO el brazo de la cruz. Y sin bajar
            // hasta la banda del nombre: mide 2,75 casillas.
            final double right = c.w / 2 + c.cardW / 2;
            final double x0 = Math.max(right + m, c.w * 0.8);
            final double k = Math.max(0, Math.min(Math.min(c.cardW * 0.6, c.cardH * 0.3), c.w - m - x0));
            final double y0 = c.h * 0.46 - c.cardH * 0.44;
            final double mx = x0 + k * 0.5;
            for (int i = 0; i < 2; i++) {
                final double u = c.t - from - step * i;
                if (u <= 0 || fade <= 0 || k <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                // Se empapa: llega un poco mas grande y se asienta.
                final double sc = 1 + 0.12 * (1 - SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.1)));
                SignatureMotion.putCentered(out, base + i, mx, y0 + k * (0.5 + 0.98 * i), 0, k * sc, 1,
                        SignatureMotion.clamp01(u / 0.06) * fade);
            }
            final double u = c.t - from - step * 2;
            for (int i = 2; i < 4; i++) {
                if (u <= 0 || fade <= 0 || k <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                // Se estampa: llega un poco grande y se asienta.
                final double sc = 1 + 0.3 * (1 - SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.08)));
                SignatureMotion.putCentered(out, base + i, mx, y0 + k * 2.42, -3, k * 0.62 * sc, 1,
                        SignatureMotion.clamp01(u / 0.04) * fade);
            }
        }
    }

    /**
     * COLUMNAS DE LUZ que caen a los lados de la carta, una tras otra, y se
     * apagan (el Juicio de Luz de Kefka). Nunca por encima de la carta: los
     * huecos van a izquierda y derecha.
     */
    static final class Beams extends Layer {
        private final double from;
        private final double step;
        private final double life;
        private final double[] x;

        Beams(final int n, final double from, final double step, final double life, final long seed) {
            this(n, from, step, life, seed, true);
        }

        /** @param sidesOnly solo a los lados (hay carta en medio); si no, por toda la mesa (un Wrath of God) */
        Beams(final int n, final double from, final double step, final double life, final long seed,
                final boolean sidesOnly) {
            super(n);
            this.from = from;
            this.step = step;
            this.life = life;
            x = new double[n];
            final Random r = new Random(seed);
            for (int i = 0; i < n; i++) {
                if (sidesOnly) {
                    // Alternando lados, en la franja de fuera de la carta.
                    final double off = 0.08 + r.nextDouble() * 0.28;
                    x[i] = i % 2 == 0 ? off : 1 - off;
                } else {
                    // Repartidas de lado a lado, con un poco de azar.
                    x[i] = (i + 0.2 + r.nextDouble() * 0.6) / n;
                }
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return SignatureSprite.BEAM;
        }

        @Override
        boolean big(final int i) {
            return true;
        }

        @Override
        boolean inFront(final int i) {
            return true;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = c.screenFade();
            for (int i = 0; i < n; i++) {
                final double u = c.t - from - step * i;
                if (u <= 0 || u >= life || fade <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double len = c.h * 1.06;
                // Cae de golpe y se apaga; ancha al principio, fina al irse.
                final double grow = SignatureMotion.easeOutCubic(SignatureMotion.clamp01(u / 0.06));
                final double width = c.h * 0.11 * (1 - 0.5 * u / life);
                SignatureMotion.put(out, base + i, c.w * x[i], c.h * 1.03, 0, len * grow,
                        width / (0.12 * len), Math.sin(Math.PI * u / life) * fade);
            }
        }
    }

    /**
     * UN METEORITO (Blasphemous Act, {@link SpellSignature}): cae en diagonal
     * desde arriba a la izquierda, cada vez mas deprisa, con su cola de fuego, y
     * se apaga justo al golpe ({@code landAt}), que es cuando estalla lo demas.
     * La cabeza es una bola de fuego y la cola, llamas que apuntan hacia atras.
     */
    static final class Meteor extends Layer {
        private final double size;

        Meteor(final int trail, final double size) {
            super(1 + trail);
            this.size = size;
        }

        @Override
        SignatureSprite sprite(final int i) {
            return i == 0 ? SignatureSprite.FIREBALL : i % 2 == 0 ? SignatureSprite.FLAME_GOLD : SignatureSprite.FLAME;
        }

        @Override
        boolean big(final int i) {
            return true;
        }

        @Override
        boolean inFront(final int i) {
            return true;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            if (c.t >= c.landAt || c.landAt <= 0) {
                for (int i = 0; i < n; i++) {
                    SignatureMotion.hide(out, base + i);
                }
                return;
            }
            final double sx = c.cardX - c.w * 0.55;
            final double sy = c.cardY - c.h * 0.8;
            final double dx = c.cardX - sx;
            final double dy = c.cardY - sy;
            final double rot = Math.toDegrees(Math.atan2(dx, -dy));
            final double len = c.cardH * size;
            for (int i = 0; i < n; i++) {
                // Cada vez mas deprisa: p = u^2.  La cola va un poco por detras.
                final double u = SignatureMotion.clamp01(c.t / c.landAt - i * 0.045);
                final double p = u * u;
                final double x = sx + dx * p;
                final double y = sy + dy * p;
                if (i == 0) {
                    SignatureMotion.putCentered(out, base, x, y, 0, len, 1, SignatureMotion.clamp01(c.t / 0.06));
                } else {
                    // La llama sale de la cabeza y apunta hacia atras.
                    final double l = len * (1.5 - 0.15 * i);
                    SignatureMotion.put(out, base + i, x, y, rot + 180, l, 0.9,
                            0.85 * (1 - (double) i / n) * SignatureMotion.clamp01(c.t / 0.06));
                }
            }
        }
    }

    /** AL POSARSE: saltan de su sitio en abanico (o en corona, {@code spread} 360). */
    static final class Burst extends Layer {
        private final SignatureSprite[] sprites;
        private final double[] angle;
        private final double[] speed;
        private final double[] len;
        private final double gravity;
        private final boolean spinning;
        private final double[] spin;

        Burst(final int n, final double spread, final double size, final double gravity, final boolean spinning,
                final long seed, final SignatureSprite... sprites) {
            super(n);
            this.sprites = sprites;
            this.gravity = gravity;
            this.spinning = spinning;
            angle = new double[n];
            speed = new double[n];
            len = new double[n];
            spin = new double[n];
            final Random r = new Random(seed);
            for (int i = 0; i < n; i++) {
                angle[i] = -spread / 2 + spread * (i + 0.5) / n + (r.nextDouble() - 0.5) * 12;
                speed[i] = 2.5 + r.nextDouble() * 2;
                len[i] = size * (0.75 + r.nextDouble() * 0.5);
                spin[i] = (r.nextBoolean() ? 1 : -1) * (120 + r.nextDouble() * 200);
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return sprites[i % sprites.length];
        }

        @Override
        boolean inFront(final int i) {
            return true;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double end = c.endFade();
            for (int i = 0; i < n; i++) {
                final double tau = c.t - c.landAt;
                if (tau <= 0 || Double.isNaN(c.slotX) || c.slotH <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double a = Math.toRadians(angle[i]);
                final double dist = c.slotH * speed[i] * (1 - Math.exp(-5 * tau)) / 5;
                final double cx = c.slotX + Math.sin(a) * dist;
                final double cy = c.slotY - c.slotH * 0.1 - Math.cos(a) * dist + c.slotH * gravity * tau * tau;
                final double l = Math.max(12, c.slotH * len[i]) * (1 - 0.4 * SignatureMotion.clamp01(tau / 0.5));
                final double rot = spinning ? angle[i] + spin[i] * tau : angle[i];
                final double alpha = SignatureMotion.clamp01(tau / 0.04)
                        * SignatureMotion.clamp01(1 - (tau - 0.25) / 0.35) * end;
                SignatureMotion.putCentered(out, base + i, cx, cy, rot, l, 1, alpha);
            }
        }
    }

    // ==================================================================
    //  Las diez
    // ==================================================================

    /** Y'shtola, Night's Blessed: la luna creciente detras, estrellas que titilan y un estallido de estrellas. */
    static LayeredSignature yshtola(final double w, final double h) {
        return new LayeredSignature(w, h,
                // La luna DELANTE de la carta, abrazando su borde izquierdo: detras
                // se perdia (Ana, 20-10-2026). Entera dentro de la pantalla.
                new Halo(SignatureSprite.MOON, true, 0.15, 1.15, 6, 0, -0.04, -12, 0.4, 0.52, Anchor.LEFT, 1),
                new Twinkle(24, false, 0.06, 0.35, 0x4D00L, SignatureSprite.STAR_SILVER),
                new Twinkle(8, true, 0.09, 0.5, 0x4D01L, SignatureSprite.STAR_SILVER),
                new Burst(14, 160, 0.26, 0.15, true, 0x4D02L, SignatureSprite.STAR_SILVER));
    }

    /** Edgar Markov: una bandada de murcielagos cruza la pantalla y, al posarse, salpica sangre. */
    static LayeredSignature edgar(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(22, Flow.LEFT, 0.12, 0.45, false, true, 0xED6AL, SignatureSprite.BAT),
                new Burst(16, 150, 0.2, 2.2, false, 0xED6BL, SignatureSprite.BLOOD_DROP));
    }

    /** The Ur-Dragon: llamas que suben de la carta y un estallido de ascuas de los cinco colores. */
    static LayeredSignature urDragon(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Rise(22, 0.22, 0.95, 0.3, 0x0D7AL, SignatureSprite.FLAME, SignatureSprite.FLAME_GOLD),
                new Sweep(SignatureSprite.SLASH_GLOW_FIRE, SignatureSprite.SLASH_FIRE, 1.4,
                        new double[] {0.55}, new double[] {-72}),
                new Burst(15, 170, 0.32, 0.4, false, 0x0D7BL,
                        SignatureSprite.EMBER_W, SignatureSprite.EMBER_U, SignatureSprite.EMBER_B,
                        SignatureSprite.EMBER_R, SignatureSprite.EMBER_G));
    }

    /** Atraxa, Praetors' Voice: un resplandor dorado detras, plumas blancas cayendo y un estallido de plumas. */
    static LayeredSignature atraxa(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Halo(SignatureSprite.GLORY, false, 0.1, 1.9, 20, 0, 0, 0),
                new Drift(24, Flow.FALL, 0.075, 0.4, true, false, 0xA7AAL, SignatureSprite.FEATHER_WHITE),
                new Burst(12, 160, 0.35, 0.3, true, 0xA7ABL, SignatureSprite.FEATHER_WHITE));
    }

    /** Krenko, Mob Boss: chispas rojas que se multiplican (1, 2, 4, 8, 16), como sus trasgos. */
    static LayeredSignature krenko(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Doubling(5, 0.28, 0.13, 0x6081L, SignatureSprite.SPARK_RED, SignatureSprite.SPARK_GOLD),
                new Burst(16, 360, 0.3, 0.2, false, 0x6082L, SignatureSprite.SPARK_RED, SignatureSprite.SPARK_GOLD));
    }

    /** Vivi Ornitier: bolas de fuego que giran alrededor de la carta y salen disparadas, y estrellas magicas. */
    static LayeredSignature vivi(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Orbit(8, 0.2, 0.9, 0.85, 1.25, 0.16, false, SignatureSprite.FIREBALL),
                new Twinkle(12, true, 0.07, 0.5, 0x7171L, SignatureSprite.STAR_GOLD),
                new Burst(14, 160, 0.3, 0.3, false, 0x7172L, SignatureSprite.SPARK_GOLD, SignatureSprite.SPARK_RED));
    }

    /**
     * Sauron, the Dark Lord: el Ojo en llamas ENCIMA de la carta, entero en el
     * hueco que quede hasta el borde (con la carta grande se cortaba por arriba
     * y quedaba medio detras: Ana, 20-10-2026), ceniza cayendo y una corona de
     * ascuas.
     */
    static LayeredSignature sauron(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Halo(SignatureSprite.EYE, true, 0.12, 1.3, 0, 0, -0.66, 0, 0.54, 0.36, Anchor.ABOVE, 1.5),
                new Drift(24, Flow.FALL, 0.03, 0.4, true, false, 0x5A0AL, SignatureSprite.ASH),
                new Burst(16, 360, 0.28, 0.1, false, 0x5A0BL, SignatureSprite.SPARK_GOLD, SignatureSprite.SPARK_RED));
    }

    /** Kaalia of the Vast: angeles, demonios y dragones (plumas blancas, negras y llamas) entran en espiral. */
    static LayeredSignature kaalia(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Orbit(18, 0.15, 0.82, 1.5, 1.0, 0.2, true,
                        SignatureSprite.FEATHER_WHITE, SignatureSprite.FEATHER, SignatureSprite.FLAME),
                new Burst(15, 170, 0.3, 0.3, true, 0x4A41L,
                        SignatureSprite.FEATHER_WHITE, SignatureSprite.FEATHER, SignatureSprite.FLAME));
    }

    /** Teval, the Balanced Scale: hojas que caen y almas que suben, a la vez; y un estallido de motas. */
    static LayeredSignature teval(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(16, Flow.FALL, 0.05, 0.4, true, false, 0x7E7AL, SignatureSprite.LEAF_GREEN),
                new Drift(16, Flow.RISE, 0.05, 0.4, false, false, 0x7E7BL, SignatureSprite.MOTE_TEAL),
                new Burst(14, 360, 0.22, 0, false, 0x7E7CL, SignatureSprite.MOTE_TEAL, SignatureSprite.LEAF_GREEN));
    }

    /**
     * Isshin, Two Heavens as One: Musashi en Vagabond, sin el personaje (Ana,
     * 20-10-2026). La mesa se vuelve papel de arroz, dos pinceladas negras la
     * cruzan en X (las dos espadas, el Niten Ichi-ryu), junto a la carta se
     * escribe a pincel su nombre, \u6b66\u8535, y se estampa su sello rojo, y al posarse salta
     * tinta con una gota roja.
     */
    static LayeredSignature isshin(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Paper(),
                new TwoSwords(new double[] {0.12, 0.26}, new double[] {122, 238}, 7, 0x1551L),
                new MusashiSeal(0.38, 0.17),
                new Burst(14, 360, 0.24, 0.5, false, 0x1552L,
                        SignatureSprite.INK_DROP, SignatureSprite.INK_DROP, SignatureSprite.INK_DROP,
                        SignatureSprite.INK_DROP_RED));
    }

    /** Fire Lord Azula: fuego azul que sube de la carta, dos rayos que la cruzan y un estallido azul. */
    static LayeredSignature azula(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Rise(22, 0.22, 0.95, 0.3, 0xA2A1L, SignatureSprite.FLAME_BLUE),
                new Sweep(SignatureSprite.LIGHTNING_GLOW, SignatureSprite.LIGHTNING, 1.45,
                        new double[] {0.42, 0.6}, new double[] {-20, 25}),
                new Burst(15, 170, 0.3, 0.3, false, 0xA2A2L, SignatureSprite.FLAME_BLUE, SignatureSprite.SPARK_BLUE));
    }

    // ==================================================================
    //  Los diez siguientes de EDHREC y Lightning (Ana, 21-10-2026: "como los
    //  anteriores, no como Isshin")
    // ==================================================================

    /** Giada, Font of Hope: motas doradas que suben, plumas blancas y un estallido de plumas y estrellas. */
    static LayeredSignature giada(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Rise(18, 0.12, 0.95, 0.3, 0x61ADL, SignatureSprite.MOTE_GOLD),
                new Drift(16, Flow.FALL, 0.07, 0.4, true, false, 0x61AEL, SignatureSprite.FEATHER_WHITE),
                new Burst(14, 170, 0.3, 0.3, true, 0x61AFL, SignatureSprite.FEATHER_WHITE, SignatureSprite.STAR_GOLD));
    }

    /** Pantlaza, Sun-Favored: el sol encima de la carta, llamas doradas que suben y ascuas. */
    static LayeredSignature pantlaza(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Halo(SignatureSprite.SUN, true, 0.1, 0.8, 25, 0, -0.18, 0, 0.5, 0.5, Anchor.RIGHT, 1),
                new Rise(18, 0.2, 0.9, 0.3, 0x9A17L, SignatureSprite.FLAME_GOLD, SignatureSprite.FLAME),
                new Burst(16, 360, 0.3, 0.2, false, 0x9A18L, SignatureSprite.SPARK_GOLD, SignatureSprite.EMBER_R));
    }

    /** Ms. Bumbleflower: petalos rosas y amarillos que caen, estrellas alrededor y un estallido de petalos. */
    static LayeredSignature bumbleflower(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(26, Flow.FALL, 0.06, 0.45, true, false, 0xB0B1L,
                        SignatureSprite.PETAL_PINK, SignatureSprite.PETAL_YELLOW),
                new Twinkle(10, true, 0.07, 0.5, 0xB0B2L, SignatureSprite.STAR_GOLD),
                new Burst(14, 170, 0.26, 0.2, true, 0xB0B3L, SignatureSprite.PETAL_PINK, SignatureSprite.PETAL_YELLOW));
    }

    /** Lathril, Blade of the Elves: hojas que caen, dos tajos verdes en cruz y un estallido de hojas. */
    static LayeredSignature lathril(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(18, Flow.FALL, 0.06, 0.4, true, false, 0x1A71L, SignatureSprite.LEAF_GREEN),
                new Sweep(SignatureSprite.SLASH_GLOW_GREEN, SignatureSprite.SLASH_GREEN, 1.4,
                        new double[] {0.48, 0.62}, new double[] {-60, 60}),
                new Burst(14, 170, 0.28, 0.3, true, 0x1A72L, SignatureSprite.LEAF_GREEN));
    }

    /** Toph, the First Metalbender: rocas que suben, esquirlas de metal girando y un estallido de roca y metal. */
    static LayeredSignature toph(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Rise(14, 0.22, 0.75, 0.3, 0x7091L, SignatureSprite.ROCK),
                new Orbit(8, 0.2, 0.85, 0.85, 1.0, 0.13, false, SignatureSprite.SPARK_STEEL),
                new Burst(16, 360, 0.3, 0.6, true, 0x7092L, SignatureSprite.ROCK, SignatureSprite.SPARK_STEEL));
    }

    /** The Wise Mothman: polillas que cruzan aleteando, motas verdes de radiacion y un estallido verde. */
    static LayeredSignature mothman(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(14, Flow.RIGHT, 0.1, 0.45, false, true, 0x3071L, SignatureSprite.MOTH),
                new Twinkle(20, false, 0.05, 0.35, 0x3072L, SignatureSprite.MOTE_GREEN),
                new Burst(14, 360, 0.24, 0.1, false, 0x3073L, SignatureSprite.MOTE_GREEN, SignatureSprite.SPARK_GREEN));
    }

    /** Jodah, the Unifier: ascuas de los cinco colores girando alrededor y un estallido de los cinco. */
    static LayeredSignature jodah(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Orbit(10, 0.15, 0.85, 0.9, 1.4, 0.13, false,
                        SignatureSprite.EMBER_W, SignatureSprite.EMBER_U, SignatureSprite.EMBER_B,
                        SignatureSprite.EMBER_R, SignatureSprite.EMBER_G),
                new Twinkle(10, true, 0.07, 0.5, 0x10DAL, SignatureSprite.STAR_GOLD),
                new Burst(15, 360, 0.28, 0.2, false, 0x10DBL,
                        SignatureSprite.EMBER_W, SignatureSprite.EMBER_U, SignatureSprite.EMBER_B,
                        SignatureSprite.EMBER_R, SignatureSprite.EMBER_G));
    }

    /** Hearthhull, the Worldseed: estrellas por toda la pantalla, un planeta junto a la carta y un estallido de roca. */
    static LayeredSignature hearthhull(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Twinkle(26, false, 0.05, 0.35, 0x4EA1L, SignatureSprite.STAR_SILVER),
                new Halo(SignatureSprite.PLANET, true, 0.12, 0.68, 6, 0, -0.05, -14, 0.62, 0.5, Anchor.LEFT, 1),
                new Burst(14, 360, 0.26, 0.3, true, 0x4EA2L, SignatureSprite.ROCK, SignatureSprite.STAR_SILVER));
    }

    /** Nekusar, the Mindrazer: cartas girando como una rueda, llamas moradas y un estallido rojo y morado. */
    static LayeredSignature nekusar(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Orbit(8, 0.15, 0.85, 0.9, 1.5, 0.16, false, SignatureSprite.CARD_BACK),
                new Rise(16, 0.18, 0.8, 0.3, 0x2E5AL, SignatureSprite.FLAME_PURPLE),
                new Burst(14, 360, 0.28, 0.2, false, 0x2E5BL, SignatureSprite.SPARK_RED, SignatureSprite.SPARK_PURPLE));
    }

    /** Kefka, Court Mage: columnas de luz a los lados (el Juicio de Luz), chispas moradas y un estallido. */
    static LayeredSignature kefka(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Beams(6, 0.18, 0.09, 0.42, 0x6EFCL),
                new Twinkle(14, false, 0.06, 0.4, 0x6EFDL, SignatureSprite.SPARK_PURPLE),
                new Burst(16, 360, 0.3, 0.2, false, 0x6EFEL, SignatureSprite.SPARK_GOLD, SignatureSprite.SPARK_PURPLE));
    }

    /** Lightning, Army of One: tres rayos rosas y blancos, su pluma blanca y un estallido rosa. */
    static LayeredSignature lightning(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Sweep(SignatureSprite.LIGHTNING_GLOW_PINK, SignatureSprite.LIGHTNING_PINK, 1.45,
                        new double[] {0.36, 0.5, 0.64}, new double[] {-25, 22, -6}),
                new Drift(8, Flow.FALL, 0.08, 0.4, true, false, 0x11F7L, SignatureSprite.FEATHER_WHITE),
                new Burst(15, 170, 0.3, 0.3, false, 0x11F8L, SignatureSprite.SPARK_PINK, SignatureSprite.SPARK_BLUE));
    }
}
