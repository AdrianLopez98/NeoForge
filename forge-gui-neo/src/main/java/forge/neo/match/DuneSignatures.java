package forge.neo.match;

import java.util.Random;

import forge.neo.match.LayeredSignature.Burst;
import forge.neo.match.LayeredSignature.Ctx;
import forge.neo.match.LayeredSignature.Drift;
import forge.neo.match.LayeredSignature.Flow;
import forge.neo.match.LayeredSignature.Halo;
import forge.neo.match.LayeredSignature.Layer;
import forge.neo.match.LayeredSignature.Orbit;
import forge.neo.match.LayeredSignature.Anchor;
import forge.neo.match.LayeredSignature.Sweep;
import forge.neo.match.LayeredSignature.Twinkle;

/**
 * <b>Las firmas de los comandantes de Dune</b> (Secret Lair, noviembre de
 * 2026). Ana, 16-11-2026: <i>"arena con spice y algun gusano, curratela un
 * pelin mas que de normal pero no demasiado ... y despues una a cada nuevo
 * comandante de dune, no hace falta que sea tan currada"</i>.
 *
 * <p><b>Muad'Dib, Prophet Foretold</b> es la currada: la mesa se vuelve
 * desierto (una bruma de arena y dos filas de dunas), al lado de la carta
 * <b>asoma la cabeza un gusano de arena</b> enorme, con la boca abierta de
 * frente y sus dientes de lamprea, y se queda ahi aspirando la arena; y otro
 * pasa por detras de la carta enterrado en las dunas del fondo, que solo
 * ensenya el lomo. Los gusanos de Dune NO saltan (Ana, 16-11-2026: <i>"no
 * saltan y se ven asi como espaguetis ... con que lo hagas saliendo de la arena
 * un poquito la cabeza vale, o surfeando pasando por detras de la carta"</i>).
 * La arena sopla de lado a lado, sube la especia, y al posarse salta arena,
 * especia y el azul de los ojos de Ibad. Las otras siete son mezclas de capas de siempre con el soplo de arena
 * para que se lean como de la misma familia.
 *
 * <p>Las mismas reglas que {@link LayeredSignature}, y por lo mismo: nada de
 * estado por fotograma (todo sale del instante), el desierto se apaga mientras
 * la carta vuela a su sitio, y unos 60 dibujos como mucho. Java puro: lo pintan
 * el escritorio y Android sin tocar sus pintores.
 */
final class DuneSignatures {

    private DuneSignatures() {
    }

    /** Lo que dura el desierto: entero hasta que la carta echa a volar, y apagandose mientras vuela. */
    static double desertFade(final Ctx c) {
        return SignatureMotion.clamp01(c.t / 0.12) * SignatureMotion.clamp01((c.landAt + 0.1 - c.t) / 0.4);
    }

    // ==================================================================
    //  El desierto
    // ==================================================================

    /** La bruma de arena: toda la pantalla, por detras de todo. */
    static final class Haze extends Layer {
        private final double alpha;

        Haze(final double alpha) {
            super(1);
            this.alpha = alpha;
        }

        @Override
        SignatureSprite sprite(final int i) {
            return SignatureSprite.DESERT_HAZE;
        }

        @Override
        boolean big(final int i) {
            return true;
        }

        @Override
        boolean inFront(final int i) {
            return false;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double a = alpha * desertFade(c);
            if (a <= 0) {
                SignatureMotion.hide(out, base);
                return;
            }
            SignatureMotion.putCentered(out, base, c.w / 2, c.h / 2, 0, c.h * 1.1, c.w / c.h, a);
        }
    }

    /**
     * Una fila de dunas de lado a lado, que sube un poco al empezar (la arena
     * se levanta) y se apaga con el resto del desierto.
     */
    static final class Dunes extends Layer {
        private final SignatureSprite sprite;
        private final double baseY;
        private final double height;
        private final double alpha;

        /**
         * @param baseY  donde cae su base, en partes del alto de la pantalla
         * @param height lo que mide de la base a la cresta mas alta, en partes del alto
         */
        Dunes(final SignatureSprite sprite, final double baseY, final double height, final double alpha) {
            super(1);
            this.sprite = sprite;
            this.baseY = baseY;
            this.height = height;
            this.alpha = alpha;
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
            return false;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double a = alpha * desertFade(c);
            if (a <= 0) {
                SignatureMotion.hide(out, base);
                return;
            }
            final double len = c.h * height;
            final double rise = 1 - SignatureMotion.easeOutCubic(SignatureMotion.clamp01(c.t / 0.3));
            SignatureMotion.put(out, base, c.w / 2, c.h * baseY + len * 0.35 * rise, 0, len, c.w * 1.04 / len, a);
        }
    }

    // ==================================================================
    //  El gusano
    // ==================================================================

    /**
     * Por donde va un gusano: un arco que sale de debajo de la arena en
     * {@code x0}, sube {@code rise} y vuelve a hundirse en {@code x1}, y la
     * velocidad a la que lo recorre. Se mide por LONGITUD de arco, para que los
     * anillos vayan siempre a la misma distancia aunque el arco sea empinado.
     */
    static final class Worm {
        final double x0;
        final double x1;
        final double baseY;
        final double rise;
        final double width;
        final double spacing;
        final int segments;
        final double from;
        final double speed;
        final double surfaceY;
        /** 1 = un arco; menos de 1, casi plano por arriba (el que va surfeando). */
        final double flat;
        private static final int SAMPLES = 160;
        private final double[] lenAt = new double[SAMPLES + 1];
        final double total;

        /**
         * Todo en partes de la pantalla: x en partes del ancho, y y tamanyos en
         * partes del alto; {@code speed} en altos por segundo.
         */
        Worm(final double w, final double h, final double x0, final double x1, final double baseY,
                final double rise, final double width, final int segments, final double from, final double speed,
                final double surfaceY, final double flat) {
            this.flat = flat;
            this.x0 = x0 * w;
            this.x1 = x1 * w;
            this.baseY = baseY * h;
            this.rise = rise * h;
            this.width = width * h;
            this.segments = segments;
            this.spacing = this.width * 0.42;
            this.from = from;
            this.speed = speed * h;
            this.surfaceY = surfaceY * h;
            double acc = 0;
            double px = this.x0;
            double py = this.baseY;
            for (int k = 1; k <= SAMPLES; k++) {
                final double s = (double) k / SAMPLES;
                final double x = this.x0 + (this.x1 - this.x0) * s;
                final double y = this.baseY - this.rise * Math.pow(Math.sin(Math.PI * s), flat);
                acc += Math.hypot(x - px, y - py);
                lenAt[k] = acc;
                px = x;
                py = y;
            }
            total = acc;
        }

        /** El punto del arco que esta a {@code d} de su comienzo. */
        double sAt(final double d) {
            if (d <= 0) {
                return 0;
            }
            if (d >= total) {
                return 1;
            }
            int lo = 0;
            int hi = SAMPLES;
            while (hi - lo > 1) {
                final int mid = (lo + hi) >>> 1;
                if (lenAt[mid] < d) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            final double f = (d - lenAt[lo]) / Math.max(1e-9, lenAt[hi] - lenAt[lo]);
            return (lo + f) / SAMPLES;
        }

        double x(final double s) {
            return x0 + (x1 - x0) * s;
        }

        double y(final double s) {
            return baseY - rise * Math.pow(Math.max(0, Math.sin(Math.PI * s)), flat);
        }

        /** Hacia donde mira el arco en {@code s}: 0 = hacia arriba, en el sentido del reloj. */
        double rot(final double s) {
            final double e = 1e-3;
            final double a = Math.max(0, s - e);
            final double z = Math.min(1, s + e);
            return Math.toDegrees(Math.atan2(x(z) - x(a), -(y(z) - y(a))));
        }

        /** Lo que ha avanzado la cabeza en el instante {@code t}. */
        double head(final double t) {
            return (t - from) * speed;
        }

        /** Lo que mide el cuerpo de la cabeza a la cola. */
        double bodyLength() {
            return segments * spacing;
        }
    }

    /**
     * EL CUERPO DEL GUSANO: la cola primero y la cabeza encima (se pintan en
     * orden), y si lleva boca, la boca sobre la cabeza. Cada anillo va donde
     * estaba la cabeza hace un rato y mira hacia donde va el arco; los que estan
     * debajo de la arena no se pintan (y los que asoman por debajo de la cresta
     * los tapan las dunas, que van despues).
     */
    static final class WormBody extends Layer {
        private final Worm worm;
        private final boolean mouth;
        private final boolean big;

        WormBody(final Worm worm, final boolean mouth, final boolean big) {
            super(worm.segments + 1 + (mouth ? 1 : 0));
            this.worm = worm;
            this.mouth = mouth;
            this.big = big;
        }

        @Override
        SignatureSprite sprite(final int i) {
            if (i < worm.segments) {
                return SignatureSprite.WORM_SEGMENT;
            }
            return i == worm.segments ? SignatureSprite.WORM_HEAD : SignatureSprite.WORM_MOUTH;
        }

        @Override
        boolean big(final int i) {
            return big;
        }

        @Override
        boolean inFront(final int i) {
            return false;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = desertFade(c);
            final double head = worm.head(c.t);
            final double segLen = worm.spacing * 1.6;
            for (int i = 0; i < worm.segments; i++) {
                // i = 0 es la punta de la cola; el ultimo, el anillo pegado a la cabeza.
                final int k = worm.segments - i;
                final double d = head - k * worm.spacing;
                if (fade <= 0 || d < 0 || d > worm.total) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double s = worm.sAt(d);
                final double wk = worm.width * (1 - 0.42 * k / worm.segments);
                SignatureMotion.putCentered(out, base + i, worm.x(s), worm.y(s), worm.rot(s), segLen, wk / segLen, fade);
            }
            final double headLen = worm.width * 1.2;
            final int h = base + worm.segments;
            if (fade <= 0 || head < 0 || head > worm.total) {
                SignatureMotion.hide(out, h);
                if (mouth) {
                    SignatureMotion.hide(out, h + 1);
                }
                return;
            }
            final double s = worm.sAt(head);
            final double rot = worm.rot(s);
            final double hx = worm.x(s);
            final double hy = worm.y(s);
            SignatureMotion.putCentered(out, h, hx, hy, rot, headLen, worm.width * 1.05 / headLen, fade);
            if (mouth) {
                // En la punta, aplastada a lo largo: la boca abierta vista de frente.
                final double rad = Math.toRadians(rot);
                final double fx = hx + Math.sin(rad) * headLen * 0.36;
                final double fy = hy - Math.cos(rad) * headLen * 0.36;
                final double ml = worm.width * 0.5;
                SignatureMotion.putCentered(out, h + 1, fx, fy, rot, ml, worm.width * 1.45 / ml, fade);
            }
        }
    }

    /**
     * LA BOCA: un gusano enorme que asoma de la arena al lado de la carta y se
     * queda ahi con la boca abierta, aspirando (Ana, 16-11-2026: <i>"en la
     * serie lo que son es como aspiradoras ... no saltan"</i>; y luego, con dos
     * fotos: <i>"que luzca un poco mas 3d y tenga forma mas gorda y
     * uniforme"</i>). Un tubo del mismo ancho que la boca, un poco inclinado
     * hacia la carta, que sube entero (no se estira: sus anillos no cambian).
     *
     * <p>Nueve dibujos, en orden de atras adelante: dos nubes de polvo detras,
     * el cuerpo, la sombra del labio sobre el, el labio, la garganta, el monton
     * de arena al pie y dos nubes de polvo delante.
     */
    static final class Maw extends Layer {
        private static final int DUST_BACK = 0;
        private static final int BODY = 2;
        private static final int SHADE = 3;
        private static final int LIP = 4;
        private static final int THROAT = 5;
        private static final int MOUND = 6;
        private static final int DUST_FRONT = 7;
        /** Lo aplastada que se ve la boca: de frente y un poco desde arriba. */
        static final double TILT = 0.66;

        private final double x;
        private final double y;
        private final double size;

        /**
         * @param x    centro de la boca, en partes del ancho
         * @param y    donde se queda la boca, en partes del alto
         * @param size lo que mide la boca de lado a lado, en partes del alto
         */
        Maw(final double x, final double y, final double size) {
            super(9);
            this.x = x;
            this.y = y;
            this.size = size;
        }

        @Override
        SignatureSprite sprite(final int i) {
            switch (i) {
                case BODY:
                    return SignatureSprite.WORM_BODY;
                case SHADE:
                    return SignatureSprite.WORM_SHADE;
                case LIP:
                    return SignatureSprite.WORM_LIP;
                case THROAT:
                    return SignatureSprite.WORM_THROAT;
                case MOUND:
                    return SignatureSprite.SAND_MOUND;
                default:
                    return SignatureSprite.SAND_PUFF;
            }
        }

        @Override
        boolean big(final int i) {
            return true;
        }

        @Override
        boolean inFront(final int i) {
            return false;
        }

        /** Lo que mide la boca de lado a lado: en una pantalla estrecha, lo que quepa. */
        static double diameter(final Ctx c, final double size) {
            return Math.min(c.h * size, c.w * 0.4);
        }

        /** Lo inclinado que va, en grados hacia la carta: se mece un poco. */
        static double lean(final Ctx c) {
            return 6 + 1.5 * Math.sin(c.t * 2.2);
        }

        /** El centro de la boca ahora: sube despacio de la arena y luego respira. */
        static double mouthX(final Ctx c, final double x) {
            return c.w * x;
        }

        static double mouthY(final Ctx c, final double y) {
            final double up = SignatureMotion.easeOutCubic(SignatureMotion.clamp01((c.t - 0.04) / 0.6));
            return c.h * (y + 0.8 * (1 - up)) + c.h * 0.008 * Math.sin(c.t * 5);
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = desertFade(c);
            if (fade <= 0) {
                for (int i = 0; i < n; i++) {
                    SignatureMotion.hide(out, base + i);
                }
                return;
            }
            final double d = diameter(c, size) * (1 + 0.02 * Math.sin(c.t * 9));
            final double rot = lean(c);
            final double rad = Math.toRadians(rot);
            // El eje del cuerpo, hacia arriba.
            final double ux = Math.sin(rad);
            final double uy = -Math.cos(rad);
            final double mx = mouthX(c, x);
            final double my = mouthY(c, y);

            // El cuerpo: de largo fijo, colgado de la boca. Su pie queda bajo la pantalla.
            final double bodyLen = c.h;
            SignatureMotion.put(out, base + BODY, mx - ux * bodyLen, my - uy * bodyLen, rot, bodyLen, d / bodyLen, fade);

            // La sombra del labio, justo debajo de el.
            final double lipH = d * TILT * 1.04;
            final double sh = d * 0.3;
            SignatureMotion.putCentered(out, base + SHADE, mx - ux * (lipH * 0.5 + sh * 0.12),
                    my - uy * (lipH * 0.5 + sh * 0.12), rot, sh, d * 1.02 / sh, 0.85 * fade);

            // El labio y, dentro, la garganta.
            SignatureMotion.putCentered(out, base + LIP, mx, my, rot, lipH, d * 1.06 / lipH, fade);
            final double throatH = lipH * 0.7;
            SignatureMotion.putCentered(out, base + THROAT, mx - ux * d * 0.012, my - uy * d * 0.012, rot,
                    throatH, d * 0.74 / throatH, fade);

            // Donde el cuerpo se mete en la arena.
            final double ground = c.h * 0.985;
            final double gx = mx - Math.tan(rad) * (ground - my);
            final double moundH = c.h * 0.17;
            SignatureMotion.put(out, base + MOUND, gx, c.h * 1.03, 0, moundH, d * 1.7 / moundH, fade);

            // El polvo que levanta: dos nubes detras del cuerpo y dos delante, que
            // suben y se abren despacio.
            for (int k = 0; k < 4; k++) {
                final boolean front = k >= 2;
                final double side = k % 2 == 0 ? -1 : 1;
                final double f = (c.t * 0.45 + k * 0.27) % 1.0;
                final double px = gx + side * d * (front ? 0.55 : 0.42) * (1 + 0.5 * f);
                final double py = ground - c.h * (front ? 0.03 : 0.1) - c.h * 0.1 * f;
                final double l = c.h * (front ? 0.2 : 0.26) * (0.7 + 0.6 * f);
                final double a = (front ? 0.7 : 0.55) * Math.sin(Math.PI * f) * fade;
                SignatureMotion.putCentered(out, base + (front ? DUST_FRONT + k - 2 : DUST_BACK + k), px, py, 0, l,
                        1.5, Math.max(0, a));
            }
        }
    }

    /**
     * LO QUE ASPIRA: arena (y algo de especia) que va entrando en la boca
     * desde alrededor, cada vez mas pequenya y girando, una y otra vez.
     */
    static final class Vacuum extends Layer {
        private final double x;
        private final double y;
        private final double size;
        private final double[] angle;
        private final double[] dist;
        private final double[] period;
        private final double[] phase;
        private final double[] len;

        Vacuum(final int n, final double x, final double y, final double size, final long seed) {
            super(n);
            this.x = x;
            this.y = y;
            this.size = size;
            angle = new double[n];
            dist = new double[n];
            period = new double[n];
            phase = new double[n];
            len = new double[n];
            final Random r = new Random(seed);
            for (int i = 0; i < n; i++) {
                // De los lados y de arriba: por debajo esta su propio cuerpo.
                angle[i] = -115 + 230 * (i + r.nextDouble()) / n;
                dist[i] = 0.8 + r.nextDouble() * 0.55;
                period[i] = 0.5 + r.nextDouble() * 0.3;
                phase[i] = r.nextDouble();
                len[i] = 0.05 + r.nextDouble() * 0.04;
            }
        }

        @Override
        SignatureSprite sprite(final int i) {
            return i % 3 == 2 ? SignatureSprite.SPICE : SignatureSprite.SAND_PUFF;
        }

        @Override
        boolean inFront(final int i) {
            return false;
        }

        @Override
        void frame(final Ctx c, final double[] out, final int base) {
            final double fade = desertFade(c) * SignatureMotion.clamp01((c.t - 0.4) / 0.2);
            final double cx = Maw.mouthX(c, x);
            final double cy = Maw.mouthY(c, y);
            final double d = Maw.diameter(c, size);
            for (int i = 0; i < n; i++) {
                if (fade <= 0) {
                    SignatureMotion.hide(out, base + i);
                    continue;
                }
                final double f = (c.t / period[i] + phase[i]) % 1.0;
                // Entra acelerando y girando un poco: un remolino hacia la garganta.
                final double k = 1 - f * f;
                final double a = Math.toRadians(angle[i] + 70 * f);
                final double r = d * dist[i] * k;
                final double px = cx + Math.sin(a) * r;
                final double py = cy - Math.cos(a) * r * Maw.TILT;
                final double l = c.h * len[i] * (0.3 + 0.7 * k);
                final double alpha = 0.95 * Math.sin(Math.PI * Math.min(1, f * 1.15)) * fade;
                SignatureMotion.putCentered(out, base + i, px, py, angle[i], l, 1.2, Math.max(0, alpha));
            }
        }
    }

    /** La arena que sopla de lado a lado: la firma de toda la familia. */
    private static Drift sandWind(final int n, final long seed) {
        return new Drift(n, Flow.RIGHT, 0.16, 0.35, false, false, seed, SignatureSprite.SAND_STREAK);
    }

    // ==================================================================
    //  Los comandantes
    // ==================================================================

    /**
     * Muad'Dib, Prophet Foretold: el desierto, la boca del gusano aspirando al
     * lado de la carta y otro que pasa por detras, arena que sopla, especia que
     * sube y, al posarse, arena, especia y el azul de los ojos de Ibad.
     */
    static LayeredSignature muadDib(final double w, final double h) {
        // El que va surfeando: casi plano por la cresta de las dunas del fondo,
        // de derecha a izquierda y por detras de la carta. Solo asoma el lomo.
        final Worm surf = new Worm(w, h, 1.12, -0.12, 0.93, 0.25, 0.12, 14, -0.15, 1.6, 0.62, 0.18);
        return new LayeredSignature(w, h,
                new Haze(0.62),
                new WormBody(surf, true, false),
                new Dunes(SignatureSprite.DUNES_FAR, 0.93, 0.33, 0.95),
                new Dunes(SignatureSprite.DUNES_NEAR, 1.02, 0.26, 1),
                new Maw(0.17, 0.47, 0.54),
                new Vacuum(10, 0.17, 0.47, 0.54, 0xD0E1L),
                sandWind(6, 0xD0E2L),
                new Drift(8, Flow.RISE, 0.022, 0.5, false, false, 0xD0E3L, SignatureSprite.SPICE),
                new Burst(12, 360, 0.22, 0.4, false, 0xD0E4L,
                        SignatureSprite.SAND_PUFF, SignatureSprite.SPICE, SignatureSprite.SPICE,
                        SignatureSprite.SPARK_IBAD));
    }

    /** Paul Atreides, Ducal Heir: plumas del halcon de los Atreides, arena y un estallido de plumas y oro. */
    static LayeredSignature paul(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(14, Flow.FALL, 0.085, 0.4, true, false, 0xD1A1L, SignatureSprite.FEATHER_HAWK),
                sandWind(6, 0xD1A2L),
                new Burst(14, 170, 0.26, 0.3, true, 0xD1A3L, SignatureSprite.FEATHER_HAWK, SignatureSprite.MOTE_GOLD));
    }

    /** Reverend Mother Jessica: el resplandor azul del Agua de Vida, gotas que entran en espiral y la Voz. */
    static LayeredSignature jessica(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Halo(SignatureSprite.GLOW_BLUE, false, 0.1, 1.5, 0, 0, 0, 0),
                new Orbit(8, 0.15, 0.85, 1.0, 1.2, 0.08, true, SignatureSprite.WATER_DROP),
                sandWind(6, 0xD2A2L),
                new Burst(14, 360, 0.24, 0.2, false, 0xD2A3L, SignatureSprite.SPARK_BLUE, SignatureSprite.SPARK_PURPLE));
    }

    /** Padishah Emperor Shaddam IV: la corona del monarca, el oro que cae (los tesoros) y un estallido de oro. */
    static LayeredSignature shaddam(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Halo(SignatureSprite.CROWN, true, 0.12, 0.8, 0, 0, -0.6, 0, 0.5, 0.46, Anchor.ABOVE, 1.2),
                new Drift(14, Flow.FALL, 0.06, 0.4, true, false, 0xD3A1L, SignatureSprite.COIN),
                sandWind(6, 0xD3A2L),
                new Burst(14, 360, 0.26, 0.6, true, 0xD3A3L, SignatureSprite.COIN, SignatureSprite.SPARK_GOLD));
    }

    /** Baron Vladimir Harkonnen: humo negro y rojo que sube, sangre que cae y chispas rojas. */
    static LayeredSignature baron(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(12, Flow.RISE, 0.2, 0.3, false, false, 0xD4A1L, SignatureSprite.SMOKE_BLACK),
                new Drift(10, Flow.FALL, 0.05, 0.4, true, false, 0xD4A2L, SignatureSprite.BLOOD_DROP),
                sandWind(6, 0xD4A3L),
                new Burst(14, 360, 0.26, 0.3, false, 0xD4A4L, SignatureSprite.SPARK_RED, SignatureSprite.BLOOD_DROP));
    }

    /**
     * Chani, Blade of Sietch Tabr: el desierto de los Fremen (las dunas de
     * delante) y dos tajos del crys (dobles: double strike).
     */
    static LayeredSignature chani(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Haze(0.45),
                new Dunes(SignatureSprite.DUNES_NEAR, 1.02, 0.30, 1),
                sandWind(8, 0xD5A1L),
                new Sweep(SignatureSprite.SLASH_GLOW_WHITE, SignatureSprite.SLASH_WHITE, 1.4,
                        new double[] {0.45, 0.6}, new double[] {-55, 55}),
                new Burst(14, 360, 0.24, 0.4, false, 0xD5A2L, SignatureSprite.SAND_PUFF, SignatureSprite.SPICE));
    }

    /** Mohiam, Imperial Truthsayer: las cartas de la biblioteca que mira, humo de la Bene Gesserit y el veneno. */
    static LayeredSignature mohiam(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Orbit(8, 0.15, 0.85, 0.9, 1.5, 0.16, false, SignatureSprite.CARD_BACK),
                new Drift(10, Flow.RISE, 0.1, 0.3, false, false, 0xD6A1L, SignatureSprite.SMOKE),
                sandWind(6, 0xD6A2L),
                new Burst(14, 360, 0.26, 0.2, false, 0xD6A3L, SignatureSprite.SPARK_PURPLE, SignatureSprite.SPARK_GREEN));
    }

    /** Princess Irulan Corrino: las cartas que se roban, estrellas de oro y un estallido dorado. */
    static LayeredSignature irulan(final double w, final double h) {
        return new LayeredSignature(w, h,
                new Drift(10, Flow.FALL, 0.08, 0.4, true, false, 0xD7A1L, SignatureSprite.CARD_BACK),
                new Twinkle(10, true, 0.05, 0.4, 0xD7A2L, SignatureSprite.STAR_GOLD),
                sandWind(6, 0xD7A3L),
                new Burst(14, 360, 0.24, 0.2, false, 0xD7A4L,
                        SignatureSprite.MOTE_GOLD, SignatureSprite.SPARK_GOLD, SignatureSprite.SPARK_BLUE));
    }
}
