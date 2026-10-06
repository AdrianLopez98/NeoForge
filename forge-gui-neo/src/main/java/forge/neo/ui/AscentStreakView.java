package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.ascent.AscentChallenges;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * <b>La racha de retos superados, con su llama</b> (Ana, 06-10-2026: <i>"si
 * lleva cinco dias haciendo el reto diario bien ... y una llamita"</i>).
 *
 * <p>La usan la pestanya Retos de {@link AscentSetupScreen} (con la pista de
 * que hay que superar el de hoy para no perderla) y el resumen del final de la
 * run ({@link AscentOverScreen}), que es lo que se captura para presumir.
 *
 * <p>La llama es un {@link SVGPath} y no un emoji: JavaFX en Windows pinta los
 * emojis de color como un cuadro o en gris, segun la fuente que encuentre.
 */
final class AscentStreakView {

    private AscentStreakView() {
    }

    /** Una llama de 16x19, centrada en su caja. */
    private static final String FLAME = "M8,0 C9,4 13,6 13,11.5 C13,15.5 10.8,19 8,19 C5.2,19 3,15.5 3,11.5"
            + " C3,9 4.5,7.2 5.6,6 C5.6,8 6.4,9.4 7.4,9.8 C7,6.5 7.5,3 8,0 Z";

    /**
     * La llama a la altura de {@code text}: sigue a su letra y no a
     * {@link UiScale}, que en 4K la hacia el doble de alta que el texto.
     */
    static Group flameFor(final Label text) {
        final Group holder = new Group();
        final Runnable fit = () -> {
            final double scale = text.getFont().getSize() * 1.2 / 19.0;
            final Group g = flame();
            g.setScaleX(scale);
            g.setScaleY(scale);
            // Dentro de otro Group: asi su caja ya va escalada y la fila la
            // coloca bien (la escala de un nodo no cuenta para su layout).
            holder.getChildren().setAll(new Group(g));
        };
        text.fontProperty().addListener((o, was, now) -> fit.run());
        fit.run();
        return holder;
    }

    static Group flame() {
        final SVGPath outer = new SVGPath();
        outer.setContent(FLAME);
        outer.setFill(Color.web("#D9692A"));
        final SVGPath inner = new SVGPath();
        inner.setContent(FLAME);
        inner.setFill(Color.web("#F2B33D"));
        inner.setScaleX(0.5);
        inner.setScaleY(0.5);
        inner.setTranslateY(4.5);
        return new Group(outer, inner);
    }

    /** "Racha: 5 dias seguidos" (o semanas), segun el tipo. */
    static String streakText(final AscentChallenges.Kind kind, final int n) {
        final boolean daily = kind == AscentChallenges.Kind.DAILY;
        if (n == 1) {
            return NeoText.get(daily ? "ascent.challenge.streak.day1" : "ascent.challenge.streak.week1");
        }
        return NeoText.get(daily ? "ascent.challenge.streak.days" : "ascent.challenge.streak.weeks", n);
    }

    /**
     * La racha de ese tipo, o {@code null} si nunca has superado ninguno.
     *
     * @param hints si dice "supera el de hoy para no perderla" y la mejor
     */
    static Region of(final AscentChallenges.Kind kind, final boolean hints) {
        final AscentChallenges.Streak st = AscentChallenges.streakFor(kind);
        if (st.current <= 0 && st.best <= 0) {
            return null;
        }
        final VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        box.setId("ascent-streak-" + (kind == AscentChallenges.Kind.DAILY ? "daily" : "weekly"));
        if (st.current > 0) {
            final Label text = new Label(streakText(kind, st.current));
            text.getStyleClass().add("ascent-streak");
            final HBox row = new HBox(UiScale.px(8), flameFor(text), text);
            row.setAlignment(Pos.CENTER);
            box.getChildren().add(row);
            if (hints && !st.doneNow) {
                box.getChildren().add(small(NeoText.get(kind == AscentChallenges.Kind.DAILY
                        ? "ascent.challenge.streak.keepDay" : "ascent.challenge.streak.keepWeek")));
            }
        }
        if (hints && st.best > st.current) {
            box.getChildren().add(small(NeoText.get("ascent.challenge.streak.best", st.best)));
        }
        return box.getChildren().isEmpty() ? null : box;
    }

    private static Label small(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("ascent-info-text");
        return l;
    }
}
