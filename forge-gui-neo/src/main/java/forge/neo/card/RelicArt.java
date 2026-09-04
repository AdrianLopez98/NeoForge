package forge.neo.card;

import forge.neo.NeoText;
import forge.neo.ascent.AscentRelic;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;

/**
 * La cara de una reliquia de Ascenso, dibujada entera.
 *
 * <h2>El fallo que arregla</h2>
 *
 * <p>Una reliquia es una carta <b>nuestra</b>: no existe en Magic, Scryfall no
 * tiene arte que bajar y nunca lo va a tener. Asi que caia en el respaldo
 * generico de {@link CardNode} — nombre, coste y tipo — y como no tiene coste
 * ni es criatura, lo que se veia era <b>un rectangulo oscuro con el nombre
 * arriba</b>. Ampliarla con el click derecho no anyadia nada. Reportado
 * jugando el 03-09-2026: <i>"clickas en ellas y no tienen descripcion, no
 * sabemos que hacen"</i>.
 *
 * <p>Y es el peor sitio donde podia pasar: las reliquias son <b>lo unico que se
 * arrastra entre combates</b>, la barra del mapa lleva hasta ocho, y un jefe te
 * hace elegir una de tres. Elegir sin saber que hace cada una no es una
 * decision, es una tirada.
 *
 * <h2>Que se dibuja</h2>
 *
 * <p>Una carta de verdad, con sus cuatro franjas — <b>nombre, arte, linea de
 * tipo y caja de texto</b> — porque para el jugador ES una carta y tiene que
 * leerse como las demas: mira donde mira siempre. El arte es
 * {@link RelicEmblem}, figuras dibujadas en codigo.
 *
 * <h2>Por que el texto sale en ingles</h2>
 *
 * <p>Es el Oracle del script ({@link AscentRelic#getText()}). Las cartas
 * nuestras no estan en los ficheros de traduccion del motor, exactamente igual
 * que los objetos que Forge se inventa para su modo Adventure — que salen en
 * ingles en los diez idiomas. Lo que SI va traducido es nuestro cromo: la
 * linea de tipo ("Reliquia · Rara").
 *
 * <h2>Por que la paleta esta en Java y no en el CSS</h2>
 *
 * <p>La convencion del proyecto es estilo en CSS. Aqui se rompe a proposito y
 * en un solo sitio: el mismo color tiene que teñir <b>el marco (CSS) y el
 * emblema (figuras JavaFX)</b>, y una figura no lee una variable de la hoja de
 * estilos. Con la paleta duplicada en los dos lados, cambiar el dorado de las
 * de jefe en uno solo dejaria el emblema de otro color que su marco — y eso no
 * se ve hasta que alguien saca una reliquia de jefe. Una sola fuente de verdad,
 * aqui, y el CSS se queda con lo que no cambia.
 */
public final class RelicArt extends VBox {

    /** Los cuatro tonos de una rareza: fondo, marco, tinta y hueco. */
    private static final class Palette {
        final Color back;
        final Color frame;
        final Color ink;
        final Color hole;

        Palette(final String back, final String frame, final String ink, final String hole) {
            this.back = Color.web(back);
            this.frame = Color.web(frame);
            this.ink = Color.web(ink);
            this.hole = Color.web(hole);
        }
    }

    // Los cuatro escalones se leen de un vistazo y en este orden: acero, azul
    // (el acento del juego), oro (el aviso) y violeta. Son los tokens de la
    // seccion 6 del las notas de diseño donde los hay; el violeta es nuevo porque las
    // legendarias no se parecen a nada mas del juego, que es justo su gracia.
    private static final Palette COMMON =
            new Palette("#212832", "#7A8798", "#D3DCE7", "#1A2028");
    private static final Palette RARE =
            new Palette("#16222F", "#4A9BE0", "#B7DBFF", "#0F1922");
    private static final Palette BOSS =
            new Palette("#2A2110", "#E0A63C", "#FFDFA0", "#1E1709");
    private static final Palette LEGENDARY =
            new Palette("#251438", "#C071E8", "#F0CCFF", "#190D26");

    private static Palette paletteOf(final AscentRelic.Rarity rarity) {
        if (rarity == null) {
            return COMMON;
        }
        switch (rarity) {
            case RARE:      return RARE;
            case BOSS:      return BOSS;
            case LEGENDARY: return LEGENDARY;
            case COMMON:
            default:        return COMMON;
        }
    }

    /**
     * El color del marco de esa rareza.
     *
     * <p>Publico porque la <b>pastilla de la barra del rival</b>
     * ({@code PlayerBar.setRelics}) tiene que ir del mismo color que la carta:
     * son la misma reliquia vista de dos maneras, y con dos paletas serian dos
     * cosas distintas.
     */
    public static Color frameOf(final AscentRelic.Rarity rarity) {
        return paletteOf(rarity).frame;
    }

    /** La tinta del emblema de esa rareza. Ver {@link #frameOf}. */
    public static Color inkOf(final AscentRelic.Rarity rarity) {
        return paletteOf(rarity).ink;
    }

    /** El color de los huecos del emblema de esa rareza. Ver {@link #frameOf}. */
    public static Color holeOf(final AscentRelic.Rarity rarity) {
        return paletteOf(rarity).hole;
    }

    /** El nombre traducido de esa rareza ("Comun", "De jefe"...). */
    public static String rarityName(final AscentRelic.Rarity rarity) {
        return NeoText.get(rarityKey(rarity));
    }

    private static String rarityKey(final AscentRelic.Rarity rarity) {
        if (rarity == null) {
            return "ascent.relic.rarity.common";
        }
        switch (rarity) {
            case RARE:      return "ascent.relic.rarity.rare";
            case BOSS:      return "ascent.relic.rarity.boss";
            case LEGENDARY: return "ascent.relic.rarity.legendary";
            case COMMON:
            default:        return "ascent.relic.rarity.common";
        }
    }

    private final Label nameLabel = new Label();
    private final StackPane artPane = new StackPane();
    private final Label typeLabel = new Label();
    private final Label textLabel = new Label();
    private final StackPane textBox = new StackPane(textLabel);

    private String relicId;
    private AscentRelic.Rarity rarity;
    private double width;
    private double height;

    public RelicArt() {
        getStyleClass().add("relic-face");
        setAlignment(Pos.TOP_CENTER);

        nameLabel.getStyleClass().add("relic-name");
        nameLabel.setWrapText(true);
        nameLabel.setTextAlignment(TextAlignment.CENTER);
        nameLabel.setAlignment(Pos.CENTER);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        artPane.getStyleClass().add("relic-window");
        artPane.setAlignment(Pos.CENTER);
        VBox.setVgrow(artPane, Priority.ALWAYS);

        typeLabel.getStyleClass().add("relic-type");
        typeLabel.setAlignment(Pos.CENTER);
        typeLabel.setMaxWidth(Double.MAX_VALUE);

        textLabel.getStyleClass().add("relic-text");
        textLabel.setWrapText(true);
        textLabel.setTextAlignment(TextAlignment.CENTER);
        textLabel.setAlignment(Pos.CENTER);
        textBox.getStyleClass().add("relic-textbox");
        textBox.setAlignment(Pos.CENTER);

        getChildren().addAll(nameLabel, artPane, typeLabel, textBox);
        setMouseTransparent(true);
    }

    /**
     * Que reliquia se pinta.
     *
     * <p>Es idempotente y barato repetirlo: {@link CardNode#refresh()} se llama
     * en cada repintado de la mesa, asi que si es la misma reliquia no se
     * vuelve a construir el emblema.
     */
    public void setRelic(final AscentRelic relic) {
        if (relic == null || relic.getId().equals(relicId)) {
            return;
        }
        relicId = relic.getId();
        rarity = relic.getRarity();
        nameLabel.setText(relic.getCardName());
        textLabel.setText(relic.getText() == null ? "" : relic.getText());
        typeLabel.setText(NeoText.get("ascent.relic.type")
                + "  ·  " + NeoText.get(rarityKey(rarity)));
        redraw();
    }

    /** El hueco que ocupa la carta. Lo llama {@code CardNode.setCardWidth}. */
    public void setSize(final double w, final double h) {
        if (w == width && h == height) {
            return;
        }
        width = w;
        height = h;
        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);
        redraw();
    }

    // ------------------------------------------------------------------

    /**
     * Rehace la cara entera al tamanyo actual.
     *
     * <p>Todo va en proporcion al ancho de la carta y nada en pixeles fijos:
     * la misma reliquia se pinta a 60 px en la barra del mapa y a 400 px
     * ampliada con el click derecho.
     */
    private void redraw() {
        if (width <= 0 || relicId == null) {
            return;
        }
        final Palette p = paletteOf(rarity);
        final double pad = Math.max(3, width * 0.05);
        final double radius = width * 0.05;
        final double fs = Math.max(6, width * 0.078);

        // La caja de texto solo cuando se puede LEER.
        //
        // Una carta de Magic tiene su texto a ~4,5% del ancho, y a los tamanyos
        // a los que se ensenya una reliquia en el juego (100 px en la fila de
        // premios, 60 en la barra del mapa) eso son 4 o 5 pixeles: una mancha
        // gris que solo dice "aqui pone algo". Subir la letra tampoco vale — se
        // saldria de la caja. Asi que por debajo del umbral la carta se queda
        // en emblema y nombre, que es lo que hace falta para RECONOCERLA, y el
        // texto se lee ampliandola con el click derecho, que es el gesto de
        // leer una carta en todo el juego.
        final boolean readable = width >= 170;
        textBox.setVisible(readable);
        textBox.setManaged(readable);

        setPadding(new Insets(pad));
        setSpacing(Math.max(2, width * 0.028));

        // El marco: dos anillos (uno de metal, otro fino por dentro) sobre un
        // degradado vertical. Es lo que hace que se lea como una carta y no
        // como un panel de la interfaz.
        setStyle("-fx-background-color: " + hex(p.frame) + ", "
                + "linear-gradient(to bottom, " + hex(p.back.brighter()) + " 0%, "
                + hex(p.back) + " 55%, " + hex(p.back.darker()) + " 100%);"
                + "-fx-background-insets: 0, " + (width * 0.022) + ";"
                + "-fx-background-radius: " + radius + ", " + (radius * 0.7) + ";");

        nameLabel.setStyle("-fx-font-size:" + fs + "px; -fx-text-fill: " + hex(p.ink) + ";");
        typeLabel.setStyle("-fx-font-size:" + (fs * 0.62) + "px; -fx-text-fill: "
                + hex(p.frame) + ";");
        textLabel.setStyle("-fx-font-size:" + (fs * 0.72) + "px; -fx-text-fill: "
                + hex(p.ink) + ";");

        // La caja de texto: mas oscura que la carta, como en una carta de
        // verdad. Sin fondo propio el texto flota sobre el degradado y deja de
        // leerse como "lo que hace esta carta".
        textBox.setPadding(new Insets(Math.max(2, width * 0.035)));
        textBox.setStyle("-fx-background-color: " + rgba(p.hole, 0.72) + ";"
                + "-fx-background-radius: " + (radius * 0.6) + ";"
                + "-fx-border-color: " + rgba(p.frame, 0.45) + ";"
                + "-fx-border-radius: " + (radius * 0.6) + ";"
                + "-fx-border-width: 1;");

        // La ventana del arte: un resplandor radial del color de la rareza,
        // que es lo que separa de un vistazo una comun de una legendaria
        // aunque no se lea ni una letra.
        artPane.setStyle("-fx-background-color: radial-gradient(center 50% 45%, radius 62%, "
                + rgba(p.frame, 0.30) + " 0%, " + rgba(p.hole, 0.85) + " 100%);"
                + "-fx-background-radius: " + (radius * 0.6) + ";"
                + "-fx-border-color: " + rgba(p.frame, 0.55) + ";"
                + "-fx-border-radius: " + (radius * 0.6) + ";"
                + "-fx-border-width: 1;");

        // El emblema se dimensiona contra el ANCHO, no contra el alto de la
        // ventana: el alto es lo que sobra despues del nombre y del texto, y
        // con un texto de tres lineas se quedaria en nada. Un dibujo que
        // encoge segun lo largo que sea el texto se ve como un fallo.
        //
        // Y va suelto en el StackPane, sin caja de tamanyo fijo alrededor: el
        // emblema se dibuja centrado en el ORIGEN, o sea con coordenadas
        // negativas, y StackPane ya coloca por los limites reales del nodo
        // (relocate descuenta el minX). Meterlo en una caja y empujarlo a mano
        // lo descentraria justo media figura.
        // Sin caja de texto sobra la mitad de la carta: el emblema crece para
        // ocuparla. Si se quedara del mismo tamanyo, una reliquia pequenya se
        // veria como un dibujo diminuto en medio de un marco vacio — que es
        // justo lo que parecia un fallo antes de que hubiera dibujo.
        final double emblem = width * (readable ? 0.52 : 0.66);
        artPane.getChildren().setAll(
                RelicEmblem.of(RelicEmblem.motifOf(relicId), emblem, p.ink, p.hole));
    }

    private static String hex(final Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private static String rgba(final Color c, final double alpha) {
        return "rgba(" + (int) Math.round(c.getRed() * 255) + ","
                + (int) Math.round(c.getGreen() * 255) + ","
                + (int) Math.round(c.getBlue() * 255) + "," + alpha + ")";
    }
}
