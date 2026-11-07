package forge.neo.ui;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import forge.neo.card.ManaSymbols;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * Los simbolos de mana de Forge, recortados para JavaFX (ver
 * {@link ManaSymbols}, que dice cuales y donde estan).
 *
 * <p>Se cachean las IMAGENES ya recortadas, nunca nodos: una cache estatica de
 * nodos dejaria vivas pantallas enteras (principio 11). Son unas pocas decenas
 * de 80x80, una vez por partida de vida del programa.
 */
public final class ManaIcons {

    private ManaIcons() {
    }

    private static final Map<String, Image> SHEETS = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Image>> ICONS = new ConcurrentHashMap<>();

    /** El icono de ese simbolo ("U", "{2/W}", "T"...), o null si no hay o el ajuste esta apagado. */
    public static Image icon(final String token) {
        if (!ManaSymbols.enabled()) {
            return null;
        }
        return icon(ManaSymbols.cell(token));
    }

    public static Image icon(final ManaSymbols.Cell cell) {
        if (cell == null) {
            return null;
        }
        return ICONS.computeIfAbsent(cell.key(), k -> {
            try {
                final Image sheet = SHEETS.computeIfAbsent(cell.file.getAbsolutePath(),
                        f -> new Image(cell.file.toURI().toString()));
                if (sheet.isError() || sheet.getPixelReader() == null
                        || cell.x + cell.w > sheet.getWidth() || cell.y + cell.h > sheet.getHeight()) {
                    return Optional.empty();
                }
                return Optional.of(new WritableImage(sheet.getPixelReader(), cell.x, cell.y, cell.w, cell.h));
            } catch (final RuntimeException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Un icono a {@code size} px, o null. */
    public static ImageView view(final String token, final double size) {
        final Image img = icon(token);
        if (img == null) {
            return null;
        }
        final ImageView v = new ImageView(img);
        v.setFitWidth(size);
        v.setFitHeight(size);
        v.setSmooth(true);
        v.setPreserveRatio(true);
        return v;
    }

    /**
     * El mana flotante de un color: su icono y, si hay mas de uno, cuantos en
     * una pastillita en la esquina (como la chapa de Android, decision 568):
     * el numero ENCIMA del dibujo no se leeria. Null si no hay icono.
     */
    public static Node pip(final String letter, final int amount, final double size) {
        final ImageView v = view(letter, size);
        if (v == null) {
            return null;
        }
        // Con dos o tres cifras ("123", un combo de mana a tope) la pastillita
        // asoma mas: se le reserva sitio a la derecha para que no tape el
        // simbolo de al lado.
        final int digits = String.valueOf(Math.max(amount, 0)).length();
        final double extra = amount > 1 ? size * (0.15 + 0.22 * (digits - 1)) : 0;
        final StackPane box = new StackPane(v);
        StackPane.setAlignment(v, Pos.CENTER_LEFT);
        box.setMinSize(size + extra, size);
        box.setPrefSize(size + extra, size);
        box.setMaxSize(size + extra, size);
        if (amount > 1) {
            final Label n = new Label(String.valueOf(amount));
            n.getStyleClass().add("mana-pip-count");
            StackPane.setAlignment(n, Pos.TOP_RIGHT);
            n.setTranslateY(-size * 0.25);
            n.setMouseTransparent(true);
            box.getChildren().add(n);
        }
        return box;
    }

    /**
     * Rellena {@code flow} con el texto y los simbolos dentro, con la letra y
     * el color dados (los de la etiqueta a la que sustituye). Los iconos miden
     * lo que la letra, un pelin mas, y van centrados en la linea.
     */
    public static void fill(final TextFlow flow, final String text, final Font font, final Paint fill) {
        flow.getChildren().clear();
        if (text == null) {
            return;
        }
        if (!ManaSymbols.enabled()) {
            final Text t = new Text(text);
            t.setFont(font);
            t.setFill(fill);
            flow.getChildren().add(t);
            return;
        }
        final double size = font.getSize() * 1.05;
        for (final ManaSymbols.Piece p : ManaSymbols.split(text)) {
            final Image img = p.symbol == null ? null : icon(p.symbol);
            if (img != null) {
                final ImageView v = new ImageView(img);
                v.setFitWidth(size);
                v.setFitHeight(size);
                v.setSmooth(true);
                v.setTranslateY(size * 0.18);
                flow.getChildren().add(v);
            } else {
                final Text t = new Text(p.text);
                t.setFont(font);
                t.setFill(fill);
                flow.getChildren().add(t);
            }
        }
    }

    /**
     * <b>Los simbolos dentro de una etiqueta o un boton cuyo texto viene del
     * motor</b> (itch.io, 07-11-2026: el menu de habilidades — «Bestow
     * {3}{G}{W}{U}» —, «Pay Mana Cost: {3}{G}{G}» y los dialogos de elegir).
     * El texto NO se toca: sigue siendo el del {@code Labeled} (lo leen las
     * teclas, la busqueda y los comprobadores); solo se pinta como un
     * {@code TextFlow} con iconos puesto de grafico, con la letra y el color
     * que le da el CSS. Lo que ya llevase de grafico (el numero de la tecla)
     * va delante. Sin simbolos, o con el ajuste apagado, se queda como estaba.
     *
     * <p>Llamar DESPUES de ponerle el grafico. Se rehace solo al cambiar el
     * texto, la letra, el color o el ancho (el {@code TextFlow} parte las
     * lineas al ancho de la etiqueta: un {@code Labeled} pide a su grafico el
     * alto sin ancho, y sin esto el parrafo salia en una linea cortada).
     */
    public static void decorate(final javafx.scene.control.Labeled l) {
        if (l == null || l.getProperties().containsKey(DECORATED)) {
            return;
        }
        l.getProperties().put(DECORATED, Boolean.TRUE);
        final Node lead = l.getGraphic();
        final javafx.scene.control.ContentDisplay plain = l.getContentDisplay();
        final double[] wrap = {-1};
        final TextFlow flow = new TextFlow() {
            @Override
            protected double computePrefWidth(final double height) {
                final double natural = super.computePrefWidth(height);
                return wrap[0] > 0 ? Math.min(natural, wrap[0]) : natural;
            }

            @Override
            protected double computePrefHeight(final double width) {
                return super.computePrefHeight(width < 0 && wrap[0] > 0 ? wrap[0] : width);
            }
        };
        flow.setMouseTransparent(true);
        final javafx.scene.layout.HBox box = lead == null ? null : new javafx.scene.layout.HBox();
        if (box != null) {
            box.setAlignment(Pos.CENTER_LEFT);
            box.setMouseTransparent(true);
        }
        final Runnable measure = () -> {
            double w = l.getWidth() > 0 ? l.getWidth()
                    : l.getMaxWidth() > 0 && l.getMaxWidth() < 1e5 ? l.getMaxWidth() : -1;
            if (w > 0) {
                w -= l.snappedLeftInset() + l.snappedRightInset();
                if (lead != null) {
                    w -= lead.prefWidth(-1) + l.getGraphicTextGap();
                }
            }
            final double next = w > 8 ? w : -1;
            if (next != wrap[0]) {
                wrap[0] = next;
                flow.requestLayout();
                l.requestLayout();
            }
        };
        final Runnable refresh = () -> {
            final String text = l.getText();
            if (!ManaSymbols.enabled() || !ManaSymbols.hasSymbols(text)) {
                if (l.getGraphic() != lead) {
                    if (box != null) {
                        box.getChildren().clear();
                    }
                    l.setGraphic(lead);
                    l.setContentDisplay(plain);
                }
                return;
            }
            fill(flow, text, l.getFont(), l.getTextFill());
            flow.setTextAlignment(l.getTextAlignment());
            if (box != null) {
                box.setSpacing(l.getGraphicTextGap());
                box.getChildren().setAll(lead, flow);
                l.setGraphic(box);
            } else {
                l.setGraphic(flow);
            }
            l.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            measure.run();
        };
        l.textProperty().addListener((o, a, b) -> refresh.run());
        l.fontProperty().addListener((o, a, b) -> refresh.run());
        l.textFillProperty().addListener((o, a, b) -> refresh.run());
        l.widthProperty().addListener((o, a, b) -> measure.run());
        refresh.run();
    }

    private static final String DECORATED = "neo.manaIcons.decorated";
}
