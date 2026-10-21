package forge.neo.ui;

import forge.neo.match.SignatureMotion;
import forge.neo.match.SignatureSprite;
import forge.neo.match.SpellSignature;
import javafx.animation.AnimationTimer;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

/**
 * <b>El efecto de un hechizo famoso al resolverse</b> ({@link SpellSignature}):
 * el meteorito de Blasphemous Act, el fogonazo de Wrath of God... Sin carta en
 * medio y sin parar la mesa: la capa sigue dejando pasar el raton.
 *
 * <p>Lo mismo que la parte de la firma de {@link CommanderEntrance} — cada
 * dibujo es una imagen pintada una vez ({@code CommanderEntrance.imageOf},
 * compartida) que solo se mueve, gira y escala — mas un fogonazo (UN
 * rectangulo que sube y baja de opacidad) y el temblor de la mesa al golpe.
 * Un solo temporizador mientras dura, y se quita solo.
 */
final class SpellBlast {

    private final ImpactLayer layer;
    private final SpellSignature signature;
    /** Sobre que: la carta objetivo, o null para la mesa entera. */
    private final Bounds target;
    private final Runnable onImpact;
    private final Runnable onDone;

    private final Group root = new Group();
    private Rectangle flash;
    private SignatureMotion motion;
    private ImageView[] views;
    private CommanderEntrance.SpriteImage[] images;
    private double[] pose;
    private long startNs = -1;
    private boolean struck;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override
        public void handle(final long now) {
            step(now);
        }
    };

    /** -Dneo.spell.freezeAt=S: se queda quieto en ese instante, para capturarlo. */
    private static final double FREEZE_AT = freezeAt();

    private static double freezeAt() {
        try {
            final String v = System.getProperty("neo.spell.freezeAt");
            return v == null ? Double.MAX_VALUE : Double.parseDouble(v);
        } catch (final NumberFormatException e) {
            return Double.MAX_VALUE;
        }
    }

    SpellBlast(final ImpactLayer layer, final SpellSignature signature, final Bounds target,
            final Runnable onImpact, final Runnable onDone) {
        this.layer = layer;
        this.signature = signature;
        this.target = target;
        this.onImpact = onImpact;
        this.onDone = onDone;
        root.setMouseTransparent(true);
        root.setManaged(false);
    }

    void start() {
        final double w = layer.getWidth();
        final double h = layer.getHeight();
        motion = signature.motion(w, h);
        if (motion == null || w < 10 || h < 10) {
            onDone.run();
            return;
        }
        if (signature.flashColor != 0) {
            final int c = signature.flashColor;
            flash = new Rectangle(w, h, Color.rgb((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF));
            flash.setOpacity(0);
            root.getChildren().add(flash);
        }
        final int n = motion.count();
        pose = new double[n * SignatureMotion.STRIDE];
        views = new ImageView[n];
        images = new CommanderEntrance.SpriteImage[n];
        for (int i = 0; i < n; i++) {
            final SignatureSprite sprite = motion.sprite(i);
            final CommanderEntrance.SpriteImage si = CommanderEntrance.imageOf(sprite, motion.big(i));
            final ImageView v = new ImageView(si.image);
            v.setSmooth(true);
            v.setManaged(false);
            v.setVisible(false);
            v.getTransforms().addAll(new Translate(), new Rotate(), new Scale(), new Translate(-si.baseX, -si.baseY));
            views[i] = v;
            images[i] = si;
            root.getChildren().add(v);
        }
        layer.getChildren().add(root);
        timer.start();
    }

    void stop() {
        timer.stop();
        layer.getChildren().remove(root);
    }

    private void step(final long now) {
        if (layer.getScene() == null || !ImpactLayer.isEnabled()) {
            finish();
            return;
        }
        if (startNs < 0) {
            startNs = now;
        }
        final double t = Math.min((now - startNs) / 1e9, FREEZE_AT);
        if (t >= SpellSignature.DURATION) {
            finish();
            return;
        }
        final double w = layer.getWidth();
        final double h = layer.getHeight();
        // Donde: la carta objetivo, o un rectangulo de medio alto en el centro
        // de la mesa (las capas lo tratan como "la carta").
        final double cx;
        final double cy;
        final double ch;
        final double cw;
        if (target != null) {
            cx = target.getCenterX();
            cy = target.getCenterY();
            // Con un minimo: sobre una carta pequenya de la mesa no se veia.
            ch = Math.max(h * 0.24, target.getHeight() * 1.6);
            cw = ch * 0.72;
        } else {
            cx = w / 2;
            cy = h * 0.46;
            ch = h * 0.5;
            cw = ch * 0.72;
        }
        final double impact = SpellSignature.IMPACT_AT;
        motion.frame(t, SpellSignature.DURATION, cx, cy, cw, ch, impact, cx, cy, ch, pose);
        for (int i = 0; i < views.length; i++) {
            final int o = i * SignatureMotion.STRIDE;
            final double alpha = pose[o + SignatureMotion.ALPHA];
            final double len = pose[o + SignatureMotion.LEN];
            final ImageView v = views[i];
            if (alpha < 0.004 || len < 1) {
                v.setVisible(false);
                continue;
            }
            v.setVisible(true);
            v.setOpacity(alpha);
            final Translate tr = (Translate) v.getTransforms().get(0);
            tr.setX(pose[o + SignatureMotion.X]);
            tr.setY(pose[o + SignatureMotion.Y]);
            ((Rotate) v.getTransforms().get(1)).setAngle(pose[o + SignatureMotion.ROT]);
            final double sc0 = len / (SignatureSprite.UNIT * images[i].pxPerUnit);
            final Scale sc = (Scale) v.getTransforms().get(2);
            sc.setX(sc0 * pose[o + SignatureMotion.FLIP]);
            sc.setY(sc0);
        }
        if (flash != null) {
            flash.setWidth(w);
            flash.setHeight(h);
            // Sube de golpe al golpe y se apaga en un tercio de segundo.
            final double u = t - impact;
            final double a = u < -0.04 ? 0 : u < 0 ? (u + 0.04) / 0.04 : Math.max(0, 1 - u / 0.35);
            flash.setOpacity(signature.flashPeak * a);
        }
        if (!struck && t >= impact) {
            struck = true;
            if (signature.shake && onImpact != null) {
                onImpact.run();
            }
        }
    }

    private void finish() {
        stop();
        onDone.run();
    }
}
