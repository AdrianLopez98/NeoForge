package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.neo.card.CardNode;
import forge.neo.match.SignatureMotion;
import forge.neo.match.SignatureSprite;
import forge.neo.match.CommanderSignature;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

/**
 * <b>La entrada de tu comandante</b>: un momento de cine cuando tu comandante
 * entra en la mesa. Pedido por Ana el 18-10-2026, despues de los destellos
 * ({@link ImpactLayer}): <i>"algo epico ... que no sean particulas"</i>.
 *
 * <p>En 1,8 s, y sin coger nunca el raton:
 * <ol>
 *   <li>La mesa se oscurece por los bordes (un foco de cine) y detras se abre
 *       un <b>abanico de rayos</b> de sus colores que gira despacio.</li>
 *   <li>La carta aparece <b>grande en el centro</b>, con un pequenyo rebote, y
 *       debajo su <b>nombre en dorado</b> sobre una banda oscura.</li>
 *   <li>Vuela hasta su sitio en la mesa, y al posarse sale una <b>onda de
 *       choque</b> a ras de mesa (una elipse, que es como se ve un anillo
 *       tumbado) y un fogonazo sobre la carta.</li>
 * </ol>
 *
 * <p>Formas y una carta, nada mas: <b>ningun {@code Effect}</b> (sombras,
 * desenfoques), que es lo que de verdad reserva texturas; el brillo de detras
 * de la carta es un circulo con degradado. Un reloj propio que solo corre
 * mientras dura, y todo se quita al acabar o al salir de la escena.
 *
 * <p>De todos los jugadores (Ana, 18-10-2026: "que te enteres cuando saca el
 * comandante el rival tambien"): el del rival dice de quien es. Se apaga en
 * Ajustes ("Entrada de los comandantes") y con las animaciones.
 *
 * <p><b>Los comandantes famosos llevan firma</b> ({@link CommanderSignature},
 * Ana, 18-10-2026): Sephiroth despliega un ala negra que se deshace en plumas,
 * Cloud su Omnislash, Jin Sakai el viento de Tsushima. La coreografia es la del
 * jar ({@link SignatureMotion}), la misma de Android; aqui cada dibujo
 * ({@link SignatureSprite}) se pinta UNA vez en una imagen y se le mueven sus
 * transformaciones.
 */
final class CommanderEntrance {

    /** Lo que dura entero. */
    static final double DURATION = 1.8;

    private final ImpactLayer layer;
    private final CardView card;
    private final List<Color> colors;
    private final Runnable onDone;
    private final CommanderSignature signature;
    /** Lo que dura esta: un poco mas si lleva firma. */
    private final double duration;
    /** Las plumas de BLACK_FEATHERS: la cuenta y una forma por pluma. */
    private SignatureMotion motion;
    private double[] spritePose;
    private Group spritesBack;
    private Group spritesFront;
    /** "Comandante", o "Comandante de Laura" si es de un rival. */
    private final String subtitleText;
    private final Group root = new Group();
    private long startNs = -1;
    private boolean built;

    private Rectangle vignette;
    /** El abanico de rayos, ya dibujado en una imagen (ver {@link #RAYS_RES}). */
    private ImageView rays;
    private Scale raysScale;
    private Rotate raysRotate;
    private Circle glow;
    private CardNode big;
    private Rectangle band;
    private Text title;
    private Text subtitle;
    private Ellipse shock;
    private Ellipse shock2;
    private Rectangle flash;

    /** Donde se posa: el ultimo sitio conocido de la carta en la mesa. */
    private Bounds slot;
    private double bigW;
    private double bigH;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(final long now) {
            step(now);
        }
    };

    CommanderEntrance(final ImpactLayer layer, final CardView card, final List<Color> colors,
            final String subtitleText, final CommanderSignature signature, final Runnable onDone) {
        this.subtitleText = subtitleText;
        this.layer = layer;
        this.card = card;
        this.colors = colors == null || colors.isEmpty() ? List.of(GOLD) : colors;
        this.onDone = onDone;
        this.signature = signature == null ? CommanderSignature.NONE : signature;
        this.duration = DURATION + (signature == CommanderSignature.NONE ? 0 : CommanderSignature.EXTRA_SECONDS);
        root.setMouseTransparent(true);
        root.setManaged(false);
    }

    static final Color GOLD = Color.web("#F2C66D");

    /** A que resolucion se dibuja el abanico de rayos: un tercio basta. */
    private static final double RAYS_RES = 0.35;

    private static final double FREEZE_AT = freezeAt();

    private static double freezeAt() {
        try {
            final String v = System.getProperty("neo.entrance.freezeAt");
            return v == null ? Double.MAX_VALUE : Double.parseDouble(v);
        } catch (final NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    void start() {
        layer.getChildren().add(root);
        timer.start();
    }

    /** Fuera de la escena, o se ha apagado. */
    void stop() {
        timer.stop();
        layer.getChildren().remove(root);
    }

    private void step(final long now) {
        if (layer.getScene() == null || layer.getWidth() < 10 || layer.getHeight() < 10) {
            finish();
            return;
        }
        if (startNs < 0) {
            startNs = now;
        }
        // -Dneo.entrance.freezeAt=S: se queda quieta en ese instante, para capturarla.
        // Mas despacio si la firma lo pide (Isshin): todo se estira a la vez.
        final double t = Math.min((now - startNs) / 1e9 / signature.slowdown(), FREEZE_AT);
        final Bounds b = layer.locate(card);
        if (b != null && b.getWidth() > 1 && b.getHeight() > 1) {
            slot = b;
        }
        if (!built) {
            build();
        }
        if (t >= duration) {
            finish();
            return;
        }
        paint(t);
    }

    private void finish() {
        stop();
        if (onDone != null) {
            onDone.run();
        }
    }

    // ---------------------------------------------------------------

    private void build() {
        built = true;
        final double w = layer.getWidth();
        final double h = layer.getHeight();
        final double cx = w / 2;
        final double cy = h * 0.46;

        vignette = new Rectangle(0, 0, w, h);
        vignette.setFill(new RadialGradient(0, 0, 0.5, 0.46, 0.75, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(0, 0, 0, 0.0)),
                new Stop(0.45, Color.rgb(0, 0, 0, 0.35)),
                new Stop(1, Color.rgb(0, 0, 0, 0.85))));
        vignette.setOpacity(0);

        // EL ABANICO DE RAYOS: cunyas finas desde el centro, de sus colores.
        final Group fan = new Group();
        final double reach = Math.hypot(w, h) * 0.6;
        final int n = 18;
        for (int i = 0; i < n; i++) {
            final double a = i * Math.PI * 2 / n;
            final double half = Math.PI / n * (i % 2 == 0 ? 0.42 : 0.22);
            final Polygon ray = new Polygon(
                    0, 0,
                    Math.cos(a - half) * reach, Math.sin(a - half) * reach,
                    Math.cos(a + half) * reach, Math.sin(a + half) * reach);
            final Color c = colors.get(i % colors.size());
            ray.setFill(new RadialGradient(0, 0, 0, 0, reach, false, CycleMethod.NO_CYCLE,
                    new Stop(0, c.deriveColor(0, 0.8, 1.3, 0.42)),
                    new Stop(0.55, c.deriveColor(0, 1, 1, 0.12)),
                    new Stop(1, c.deriveColor(0, 1, 1, 0))));
            fan.getChildren().add(ray);
        }
        // DIBUJADO UNA VEZ, a poca resolucion, y luego se gira la imagen. Como
        // 18 poligonos del tamanyo de la pantalla girando, JavaFX los
        // rasterizaba en la CPU en cada fotograma: medido, la mesa bajaba de 62
        // a 37 fps mientras duraba la entrada. Son degradados suaves: a un
        // tercio de resolucion no se nota, y la imagen se suelta al acabar.
        fan.getTransforms().add(new Scale(RAYS_RES, RAYS_RES));
        final javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        final Bounds fb = fan.getBoundsInParent();
        rays = new ImageView(fan.snapshot(params, null));
        rays.setSmooth(true);
        rays.setFitWidth(rays.getImage().getWidth() / RAYS_RES);
        rays.setFitHeight(rays.getImage().getHeight() / RAYS_RES);
        // El centro del abanico, dentro de la imagen ya agrandada.
        final double px = -Math.floor(fb.getMinX()) / RAYS_RES;
        final double py = -Math.floor(fb.getMinY()) / RAYS_RES;
        rays.setLayoutX(cx - px);
        rays.setLayoutY(cy - py);
        raysScale = new Scale(1, 1, px, py);
        raysRotate = new Rotate(0, px, py);
        rays.getTransforms().addAll(raysScale, raysRotate);
        rays.setMouseTransparent(true);
        rays.setOpacity(0);

        bigH = h * 0.6;
        bigW = bigH * 5 / 7;

        glow = new Circle(cx, cy, bigH * 0.62, new RadialGradient(0, 0, 0.5, 0.5, 0.5, true,
                CycleMethod.NO_CYCLE,
                new Stop(0, colors.get(0).deriveColor(0, 0.7, 1.4, 0.75)),
                new Stop(0.55, colors.get(colors.size() - 1).deriveColor(0, 1, 1, 0.25)),
                new Stop(1, Color.TRANSPARENT)));
        glow.setOpacity(0);

        big = new CardNode(bigW);
        big.setCard(card);
        big.setMouseTransparent(true);
        big.setOpacity(0);

        // LA BANDA DEL NOMBRE, como un rotulo de cine.
        final double bandH = Math.max(46, h * 0.1);
        band = new Rectangle(0, 0, w, bandH);
        band.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(8, 10, 14, 0)),
                new Stop(0.25, Color.rgb(8, 10, 14, 0.78)),
                new Stop(0.75, Color.rgb(8, 10, 14, 0.78)),
                new Stop(1, Color.rgb(8, 10, 14, 0))));
        band.setOpacity(0);
        final double bandY = Math.min(h - bandH - 6, cy + bigH / 2 - bandH * 0.35);
        band.setY(bandY);

        title = new Text(forge.neo.card.CardText.nameOf(card).toUpperCase(java.util.Locale.ROOT));
        title.setFont(Font.font(null, FontWeight.BOLD, Math.max(22, bandH * 0.42)));
        title.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#FFF4CF")),
                new Stop(0.55, GOLD),
                new Stop(1, Color.web("#B9822F"))));
        title.setStroke(Color.rgb(40, 24, 6, 0.85));
        title.setStrokeWidth(1.2);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setOpacity(0);
        subtitle = new Text((subtitleText == null ? forge.neo.NeoText.get("entrance.commander") : subtitleText)
                .toUpperCase(java.util.Locale.ROOT));
        subtitle.setFont(Font.font(null, FontWeight.SEMI_BOLD, Math.max(11, bandH * 0.2)));
        subtitle.setFill(Color.web("#E6EBF2", 0.85));
        subtitle.setOpacity(0);
        title.setLayoutX(cx - title.getLayoutBounds().getWidth() / 2);
        title.setLayoutY(bandY + bandH * 0.52);
        subtitle.setLayoutX(cx - subtitle.getLayoutBounds().getWidth() / 2);
        subtitle.setLayoutY(bandY + bandH * 0.86);

        shock = new Ellipse();
        shock.setFill(Color.TRANSPARENT);
        shock.setStroke(GOLD);
        shock.setOpacity(0);
        shock2 = new Ellipse();
        shock2.setFill(Color.TRANSPARENT);
        shock2.setStroke(colors.get(0).deriveColor(0, 1, 1.2, 1));
        shock2.setOpacity(0);
        flash = new Rectangle();
        flash.setArcWidth(10);
        flash.setArcHeight(10);
        flash.setFill(Color.web("#FFF4CF"));
        flash.setOpacity(0);

        root.getChildren().addAll(vignette, rays, glow, big, band, title, subtitle, shock, shock2, flash);
        if (signature != CommanderSignature.NONE) {
            buildSignature(w, h);
        }
    }

    /**
     * UN dibujo de la firma ({@link SignatureSprite}) pintado una vez en una
     * imagen, y todos los de ese dibujo son esa imagen girada y escalada. Con un
     * trazado por pluma, JavaFX rasteriza cada uno en la CPU en cada fotograma
     * en cuanto se mueve: medido, de 37 a 29 fps con una entrada tras otra. Una
     * imagen que se mueve es un rectangulo con textura para la tarjeta, y no
     * cuesta casi nada. Dos tamanyos, para que ni lo grande (el ala, los tajos)
     * se vea borroso ni lo pequenyo (la lluvia, las chispas) parpadee al
     * encogerlo. Se hacen la primera vez que hacen falta y se quedan: unos
     * pocos cientos de KB en total.
     */
    static final class SpriteImage {
        final Image image;
        /** Pixeles de la imagen por cada unidad del dibujo. */
        final double pxPerUnit;
        /** Donde cae la base del dibujo (el 0,0 del trazado) dentro de la imagen. */
        final double baseX;
        final double baseY;

        SpriteImage(final SignatureSprite sprite, final double scale, final double rimPx) {
            final SVGPath shape = new SVGPath();
            shape.setContent(sprite.path);
            shape.setFill(fillOf(sprite));
            if (sprite.rim != 0) {
                shape.setStroke(argb(sprite.rim));
                shape.setStrokeWidth(rimPx / scale);
            }
            final Group g = new Group(shape);
            if (sprite.accent != null) {
                final SVGPath accent = new SVGPath();
                accent.setContent(sprite.accent);
                accent.setFill(null);
                accent.setStroke(argb(sprite.accentColor));
                accent.setStrokeWidth(rimPx * 1.2 / scale);
                g.getChildren().add(accent);
            }
            g.getTransforms().add(new Scale(scale, scale));
            final javafx.scene.SnapshotParameters params = new javafx.scene.SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            final Bounds bb = g.getBoundsInParent();
            image = g.snapshot(params, null);
            pxPerUnit = scale;
            baseX = -Math.floor(bb.getMinX());
            baseY = -Math.floor(bb.getMinY());
        }

        private static javafx.scene.paint.Paint fillOf(final SignatureSprite sprite) {
            final Stop[] stops = new Stop[sprite.stops.length];
            for (int i = 0; i < stops.length; i++) {
                stops[i] = new Stop(sprite.stops[i], argb(sprite.colors[i]));
            }
            return sprite.radial
                    ? new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE, stops)
                    : new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE, stops);
        }

        private static Color argb(final int c) {
            return Color.rgb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, ((c >>> 24) & 0xFF) / 255.0);
        }
    }

    /** Las imagenes de los dibujos, por nombre y tamanyo. Solo en el hilo de interfaz. */
    private static final java.util.Map<String, SpriteImage> SPRITES = new java.util.HashMap<>();

    static SpriteImage imageOf(final SignatureSprite sprite, final boolean bigOne) {
        return SPRITES.computeIfAbsent(sprite.name + (bigOne ? "@big" : "@small"),
                k -> bigOne ? new SpriteImage(sprite, 4.4, 2.8) : new SpriteImage(sprite, 1.3, 1.5));
    }

    private ImageView[] spriteViews;
    private SpriteImage[] spriteImages;

    private void buildSignature(final double w, final double h) {
        motion = signature.motion(w, h);
        if (motion == null) {
            return;
        }
        final int n = motion.count();
        spritePose = new double[n * SignatureMotion.STRIDE];
        spriteViews = new ImageView[n];
        spriteImages = new SpriteImage[n];
        spritesBack = new Group();
        spritesFront = new Group();
        for (int i = 0; i < n; i++) {
            final SpriteImage si = imageOf(motion.sprite(i), motion.big(i));
            final ImageView v = new ImageView(si.image);
            v.setSmooth(true);
            v.setManaged(false);
            v.setVisible(false);
            // Base del dibujo -> su sitio: mover, girar, escalar y poner la base en el 0,0.
            v.getTransforms().addAll(new Translate(), new Rotate(), new Scale(), new Translate(-si.baseX, -si.baseY));
            spriteViews[i] = v;
            spriteImages[i] = si;
            (motion.inFront(i) ? spritesFront : spritesBack).getChildren().add(v);
        }
        // Lo de detras, por detras de la carta; lo demas, delante.
        root.getChildren().add(root.getChildren().indexOf(big), spritesBack);
        root.getChildren().add(root.getChildren().indexOf(band), spritesFront);
    }

    private void paintSignature(final double t, final double cardX, final double cardY, final double cardScale,
            final double landAt) {
        if (motion == null) {
            return;
        }
        motion.frame(t, duration, cardX, cardY, bigW * cardScale, bigH * cardScale, landAt,
                slot == null ? Double.NaN : slot.getCenterX(), slot == null ? 0 : slot.getCenterY(),
                slot == null ? 0 : slot.getHeight(), spritePose);
        for (int i = 0; i < spriteViews.length; i++) {
            final int o = i * SignatureMotion.STRIDE;
            final double alpha = spritePose[o + SignatureMotion.ALPHA];
            final double len = spritePose[o + SignatureMotion.LEN];
            final ImageView v = spriteViews[i];
            if (alpha < 0.004 || len < 1) {
                v.setVisible(false);
                continue;
            }
            v.setVisible(true);
            v.setOpacity(alpha);
            final Translate tr = (Translate) v.getTransforms().get(0);
            tr.setX(spritePose[o + SignatureMotion.X]);
            tr.setY(spritePose[o + SignatureMotion.Y]);
            ((Rotate) v.getTransforms().get(1)).setAngle(spritePose[o + SignatureMotion.ROT]);
            final double sc0 = len / (SignatureSprite.UNIT * spriteImages[i].pxPerUnit);
            final Scale sc = (Scale) v.getTransforms().get(2);
            sc.setX(sc0 * spritePose[o + SignatureMotion.FLIP]);
            sc.setY(sc0);
        }
    }

    private static double clamp01(final double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Sube y baja: 0 fuera de [a,d], 1 entre b y c. */
    private static double envelope(final double t, final double a, final double b, final double c, final double d) {
        if (t <= a || t >= d) {
            return 0;
        }
        if (t < b) {
            return (t - a) / (b - a);
        }
        if (t <= c) {
            return 1;
        }
        return 1 - (t - c) / (d - c);
    }

    private void paint(final double t) {
        final double w = layer.getWidth();
        final double h = layer.getHeight();
        final double cx = w / 2;
        final double cy = h * 0.46;

        vignette.setOpacity(envelope(t, 0, 0.22, 1.05, 1.45));

        // Los rayos: se abren girando, y se apagan cuando la carta se va.
        final double rIn = clamp01(t / 0.35);
        rays.setOpacity(0.95 * envelope(t, 0, 0.25, 0.85, 1.15));
        final double rs = 0.35 + 0.65 * (1 - Math.pow(1 - rIn, 3));
        raysScale.setX(rs);
        raysScale.setY(rs);
        raysRotate.setAngle(t * 22);

        glow.setOpacity(envelope(t, 0.05, 0.3, 0.85, 1.1));
        final double gs = 0.9 + 0.08 * Math.sin(t * 9);
        glow.setScaleX(gs);
        glow.setScaleY(gs);

        // LA CARTA: aparece con rebote, se queda, y vuela a su sitio.
        final double flyStart = 0.95;
        final double flyEnd = 1.32;
        double x = cx;
        double y = cy;
        double scale;
        if (t < 0.28) {
            final double p = t / 0.28;
            // Rebote: pasa un poco del tamanyo y vuelve.
            final double c1 = 1.70158;
            final double c3 = c1 + 1;
            final double back = 1 + c3 * Math.pow(p - 1, 3) + c1 * Math.pow(p - 1, 2);
            scale = 0.55 + 0.45 * back;
            big.setOpacity(clamp01(t / 0.12));
        } else if (t < flyStart || slot == null) {
            scale = 1;
            big.setOpacity(slot == null && t > flyStart ? clamp01(1 - (t - flyStart) / 0.3) : 1);
        } else {
            final double p = clamp01((t - flyStart) / (flyEnd - flyStart));
            final double e = p * p * p;
            x = cx + (slot.getCenterX() - cx) * e;
            y = cy + (slot.getCenterY() - cy) * e;
            final double target = Math.min(slot.getWidth() / bigW, slot.getHeight() / bigH);
            scale = 1 + (target - 1) * e;
            // Cuando se posa, la de verdad ya esta debajo.
            big.setOpacity(t < flyEnd ? 1 : clamp01(1 - (t - flyEnd) / 0.08));
        }
        big.setScaleX(scale);
        big.setScaleY(scale);
        big.relocate(x - bigW / 2, y - bigH / 2);
        // El ala va con la carta mientras esta en el centro; cuando vuela, ya
        // se ha deshecho y sus plumas caen donde estaban, no detras de ella.
        if (t < flyStart) {
            paintSignature(t, x, y, scale, flyEnd);
        } else {
            paintSignature(t, cx, cy, 1, flyEnd);
        }

        final double nameOn = envelope(t, 0.18, 0.36, 0.82, 0.98);
        band.setOpacity(nameOn);
        title.setOpacity(nameOn);
        subtitle.setOpacity(nameOn);
        // Las letras entran desde un poco mas abiertas: un rotulo, no un cartel.
        final double ts = 1.08 - 0.08 * clamp01((t - 0.18) / 0.3);
        title.setScaleX(ts);
        title.setScaleY(ts);

        // AL POSARSE: onda de choque tumbada y fogonazo sobre la carta.
        if (slot != null && t >= flyEnd) {
            final double p = clamp01((t - flyEnd) / 0.45);
            final double e = 1 - Math.pow(1 - p, 3);
            final double base = Math.max(slot.getWidth(), slot.getHeight() * 5 / 7);
            final double sx = slot.getCenterX();
            final double sy = slot.getMaxY() - slot.getHeight() * 0.12;
            shock.setCenterX(sx);
            shock.setCenterY(sy);
            shock.setRadiusX(base * (0.55 + 1.6 * e));
            shock.setRadiusY(base * (0.55 + 1.6 * e) * 0.32);
            shock.setStrokeWidth(Math.max(1, base * 0.09 * (1 - e)));
            shock.setOpacity(1 - p);
            final double p2 = clamp01((t - flyEnd - 0.08) / 0.4);
            final double e2 = 1 - Math.pow(1 - p2, 3);
            shock2.setCenterX(sx);
            shock2.setCenterY(sy);
            shock2.setRadiusX(base * (0.4 + 1.1 * e2));
            shock2.setRadiusY(base * (0.4 + 1.1 * e2) * 0.32);
            shock2.setStrokeWidth(Math.max(1, base * 0.06 * (1 - e2)));
            shock2.setOpacity(p2 <= 0 ? 0 : 1 - p2);
            flash.setX(slot.getMinX());
            flash.setY(slot.getMinY());
            flash.setWidth(slot.getWidth());
            flash.setHeight(slot.getHeight());
            flash.setOpacity(0.75 * (1 - clamp01((t - flyEnd) / 0.3)));
        }
    }

    /** Los colores del comandante, o el oro si es incoloro. */
    static List<Color> colorsFor(final List<Color> cardColors) {
        final List<Color> out = new ArrayList<>(cardColors);
        if (out.isEmpty()) {
            out.add(GOLD);
        }
        return out;
    }
}
