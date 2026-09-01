package forge.neo.ui;

import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

/**
 * La bandera de cada idioma, dibujada.
 *
 * <p><b>Por que dibujada y no un emoji.</b> Los emoji de bandera son pares de
 * letras indicadoras regionales, y <b>Windows no los pinta como banderas</b>:
 * las fuentes del sistema los dejan como dos letras sueltas. Un icono que en la
 * maquina del usuario sale como "ES" no es un icono.
 *
 * <p><b>Y por que no imagenes.</b> Diez PNG mas en el jar habria que
 * mantenerlos, escalarlos para cada tamanyo de interfaz y volver a generarlos si
 * alguna vez se cambia la escala. Estas son cuatro figuras por bandera, escalan
 * solas y no ocupan nada.
 *
 * <p>Son <b>simplificaciones a proposito</b>: a 20x13 pixeles lo unico que
 * importa es que se reconozca de un vistazo cual es cual, que es exactamente
 * para lo que estan aqui. El nombre del idioma sigue al lado, escrito en su
 * propio idioma, y es el que manda.
 */
public final class FlagIcon {

    private FlagIcon() {
    }

    /** Proporcion clasica de bandera. */
    private static final double RATIO = 1.5;

    /**
     * La bandera de ese idioma, o {@code null} si no tenemos ninguna.
     *
     * <p>Devolver null no es un fallo: quien la pide se queda sin icono y
     * ensenya el nombre a secas, que es lo que pasaba antes. Un idioma nuevo
     * que llegue con una actualizacion de Forge sale sin bandera, no roto.
     *
     * @param langId identificador de {@link forge.neo.NeoLanguage} ({@code es-ES})
     * @param height alto en pixeles; el ancho sale de la proporcion
     */
    public static Node of(final String langId, final double height) {
        if (langId == null) {
            return null;
        }
        final double h = Math.max(8, height);
        final double w = h * RATIO;
        final Pane flag = new Pane();
        flag.setPrefSize(w, h);
        flag.setMinSize(w, h);
        flag.setMaxSize(w, h);
        flag.setMouseTransparent(true);

        final boolean known = paint(flag, langId, w, h);
        if (!known) {
            return null;
        }

        // Un borde fino: sin el, una bandera con el borde blanco (Japon, Corea)
        // se derrama sobre el fondo del boton y deja de leerse como bandera.
        final Rectangle edge = new Rectangle(w, h);
        edge.setFill(Color.TRANSPARENT);
        edge.setStroke(Color.web("#000000", 0.45));
        edge.setStrokeWidth(1);
        flag.getChildren().add(edge);

        final Rectangle clip = new Rectangle(w, h);
        clip.setArcWidth(2);
        clip.setArcHeight(2);
        flag.setClip(clip);
        return flag;
    }

    /** @return false si ese idioma no tiene bandera dibujada */
    private static boolean paint(final Pane flag, final String langId,
                                 final double w, final double h) {
        switch (langId) {
            case "en-US":
                // Las trece franjas y el canton. Las estrellas a este tamanyo
                // serian ruido: bastan las franjas y el rectangulo azul.
                stripes(flag, w, h, 7, Color.web("#B22234"), Color.WHITE);
                add(flag, rect(0, 0, w * 0.42, h * 7 / 13.0, Color.web("#3C3B6E")));
                return true;
            case "es-ES":
                // La amarilla es el doble de alta que las rojas.
                add(flag, rect(0, 0, w, h * 0.25, Color.web("#AA151B")));
                add(flag, rect(0, h * 0.25, w, h * 0.5, Color.web("#F1BF00")));
                add(flag, rect(0, h * 0.75, w, h * 0.25, Color.web("#AA151B")));
                return true;
            case "de-DE":
                bands(flag, w, h, false, Color.web("#000000"), Color.web("#DD0000"),
                        Color.web("#FFCE00"));
                return true;
            case "fr-FR":
                bands(flag, w, h, true, Color.web("#002395"), Color.WHITE,
                        Color.web("#ED2939"));
                return true;
            case "it-IT":
                bands(flag, w, h, true, Color.web("#008C45"), Color.web("#F4F5F0"),
                        Color.web("#CD212A"));
                return true;
            case "ru-RU":
                bands(flag, w, h, false, Color.WHITE, Color.web("#0039A6"),
                        Color.web("#D52B1E"));
                return true;
            case "ja-JP":
                add(flag, rect(0, 0, w, h, Color.WHITE));
                add(flag, new Circle(w / 2, h / 2, h * 0.3, Color.web("#BC002D")));
                return true;
            case "ko-KR": {
                // El taegeuk, simplificado a dos mitades. A este tamanyo la
                // curva en S no se distingue y las dos mitades si.
                add(flag, rect(0, 0, w, h, Color.WHITE));
                final Circle red = new Circle(w / 2, h / 2, h * 0.28, Color.web("#CD2E3A"));
                final Circle blue = new Circle(w / 2, h / 2, h * 0.28, Color.web("#0047A0"));
                blue.setClip(rect(0, h / 2, w, h / 2, Color.BLACK));
                add(flag, red);
                add(flag, blue);
                return true;
            }
            case "zh-CN":
                add(flag, rect(0, 0, w, h, Color.web("#EE1C25")));
                add(flag, star(w * 0.22, h * 0.34, h * 0.22, Color.web("#FFFF00")));
                return true;
            case "pt-BR": {
                add(flag, rect(0, 0, w, h, Color.web("#009C3B")));
                final Polygon diamond = new Polygon(
                        w / 2, h * 0.12,
                        w * 0.88, h / 2,
                        w / 2, h * 0.88,
                        w * 0.12, h / 2);
                diamond.setFill(Color.web("#FFDF00"));
                add(flag, diamond);
                add(flag, new Circle(w / 2, h / 2, h * 0.2, Color.web("#002776")));
                return true;
            }
            default:
                return false;
        }
    }

    // ---------------------------------------------------------------

    /** Tres bandas iguales, verticales o horizontales. */
    private static void bands(final Pane flag, final double w, final double h,
                              final boolean vertical, final Color a, final Color b,
                              final Color c) {
        final Color[] colours = {a, b, c};
        for (int i = 0; i < 3; i++) {
            if (vertical) {
                add(flag, rect(w * i / 3.0, 0, w / 3.0, h, colours[i]));
            } else {
                add(flag, rect(0, h * i / 3.0, w, h / 3.0, colours[i]));
            }
        }
    }

    /** Franjas horizontales alternas. */
    private static void stripes(final Pane flag, final double w, final double h,
                                final int count, final Color odd, final Color even) {
        final int total = count * 2 - 1;
        for (int i = 0; i < total; i++) {
            add(flag, rect(0, h * i / total, w, h / total + 0.5,
                    i % 2 == 0 ? odd : even));
        }
    }

    /** Una estrella de cinco puntas centrada en (cx, cy). */
    private static Polygon star(final double cx, final double cy, final double r,
                                final Color fill) {
        final double[] points = new double[20];
        for (int i = 0; i < 10; i++) {
            final double radius = i % 2 == 0 ? r : r * 0.42;
            // -90 grados para que la punta mire hacia arriba.
            final double angle = Math.toRadians(i * 36 - 90);
            points[i * 2] = cx + radius * Math.cos(angle);
            points[i * 2 + 1] = cy + radius * Math.sin(angle);
        }
        final Polygon p = new Polygon(points);
        p.setFill(fill);
        return p;
    }

    private static Rectangle rect(final double x, final double y, final double w,
                                  final double h, final Color fill) {
        final Rectangle r = new Rectangle(w, h, fill);
        r.setX(x);
        r.setY(y);
        return r;
    }

    private static void add(final Pane flag, final Node node) {
        flag.getChildren().add(node);
    }
}
