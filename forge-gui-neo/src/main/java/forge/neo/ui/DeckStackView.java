package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import forge.neo.deck.DeckEditor;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.Cursor;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import forge.neo.NeoSettings;
import forge.neo.platform.NeoOs;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * El mazo entero de un vistazo, como la vista visual de Moxfield.
 *
 * <p>Una columna por tipo de carta, y dentro de cada una las cartas apiladas
 * dejando ver la franja del titulo. Asi caben 100 cartas en una pantalla sin
 * renunciar al arte, que es lo que una lista de texto no consigue.
 *
 * <p>Es la vista para <i>repasar</i> el mazo. La de dos columnas es para
 * <i>montarlo</i>. Moxfield hace la misma separacion y funciona.
 */
public class DeckStackView extends ScrollPane {

    private final DeckEditor editor;
    /** Ancho de carta a zoom 1: el que decide la escala de la interfaz. */
    private final double baseWidth;
    private double cardWidth;
    private double cardHeight;
    /**
     * Cuanto se ha acercado o alejado el jugador (Ctrl + rueda).
     *
     * <p>Hace falta porque el tamanyo de partida sale de la escala de la
     * interfaz, y en un monitor 2K a escala 100% la ventana es tan grande que
     * las cartas quedan diminutas y medio mazo sobra por la derecha
     * (reportado, 24-09-2026). Se recuerda entre sesiones.
     */
    private double zoom;
    private static final String ZOOM_KEY = "deck.visual.zoom";
    private static final double ZOOM_MIN = 0.5;
    private static final double ZOOM_MAX = 2.5;
    private static final double ZOOM_STEP = 1.1;
    private final Label zoomHint = new Label();
    private final CardActions actions;

    /**
     * Que se puede hacer con una carta de esta vista.
     *
     * <p>Son las MISMAS acciones que en la lista de la pantalla de edicion, y a
     * proposito: cambiar de vista no deberia cambiar lo que hacen tus manos.
     */
    public interface CardActions {
        /** Ensenyar la carta a tamanyo de lectura. */
        void zoom(PaperCard card);

        /** Menu de la carta: ver, cambiar edicion, quitar. */
        void menu(PaperCard card, Region anchor, double screenX, double screenY, boolean isCommander);
    }

    private final HBox columns = new HBox(16);

    public DeckStackView(final DeckEditor editor, final double cardWidth,
                         final CardActions actions) {
        this.editor = editor;
        // Mas pequenya que en la mesa: aqui lo que importa es cuantas caben,
        // y el nombre se sigue leyendo.
        //
        // Y nunca mas pequenya que un 14% del alto de la pantalla. El ancho que
        // llega viene de la mesa, que hasta el 24-09-2026 tenia un tope FIJO de
        // 150 px: en un 2K a escala 100% la carta se quedaba igual de pequenya
        // en una ventana un tercio mas alta, y el mazo salia diminuto y lejano
        // (queja de Discord). Desde que el tope crece con el alto
        // (UiScale.cardWidth) los dos numeros coinciden casi al pixel; el
        // minimo se queda de red. Lee el alto de UiScale y no de Screen para
        // que -Dneo.win lo mueva igual que al resto.
        this.baseWidth = Math.max(cardWidth * 0.85, UiScale.screenHeight() * 0.14);
        this.zoom = clampZoom(NeoSettings.getDouble(ZOOM_KEY, 1));
        sizeCards();
        this.actions = actions;

        getStyleClass().add("dialog-scroll");
        setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        columns.setPadding(new Insets(4, 26, 26, 26));
        columns.setAlignment(Pos.TOP_LEFT);

        // El aviso de que se puede cambiar el tamanyo: sin el, quien tiene las
        // cartas diminutas no tiene forma de adivinarlo. Con zoom puesto, un
        // click vuelve al tamanyo de siempre (principio 7: una salida que no
        // haya que adivinar).
        zoomHint.getStyleClass().add("home-subtitle");
        zoomHint.setOnMouseClicked(e -> setZoom(1, Double.NaN));
        final HBox hintRow = new HBox(zoomHint);
        hintRow.setAlignment(Pos.CENTER_RIGHT);
        hintRow.setPadding(new Insets(8, 26, 0, 26));
        hintRow.setMinHeight(UiScale.px(HINT_SPACE));
        hintRow.setPrefHeight(UiScale.px(HINT_SPACE));
        final VBox content = new VBox(hintRow, columns);
        content.setFillWidth(false);
        setContent(content);
        // La fila del aviso mide lo que se ve y se desplaza con el scroll: asi
        // se queda a la vista aunque el mazo sobre por la derecha.
        final Runnable pin = () -> {
            final double w = getViewportBounds().getWidth();
            hintRow.setPrefWidth(w);
            hintRow.setMaxWidth(w);
            final double extra = content.getBoundsInLocal().getWidth() - w;
            hintRow.setTranslateX(extra > 0 ? getHvalue() * extra : 0);
        };
        hvalueProperty().addListener((o, x, y) -> pin.run());
        viewportBoundsProperty().addListener((o, x, y) -> pin.run());
        content.boundsInLocalProperty().addListener((o, x, y) -> pin.run());
        updateHint();

        // Ctrl + rueda acerca y aleja, como la mesa (seccion 12b). Va en un
        // filtro para que el ScrollPane no se lleve antes la rueda.
        //
        // La rueda sola, si no hay nada que desplazar a lo alto (lo normal:
        // las columnas se reparten para caber), mueve a lo ancho. Es donde
        // esta el resto del mazo, y la barra de abajo queda muy lejos.
        addEventFilter(ScrollEvent.SCROLL, e -> {
            if (NeoOs.ctrl(e)) {
                if (e.getDeltaY() != 0) {
                    setZoom(zoom * (e.getDeltaY() > 0 ? ZOOM_STEP : 1 / ZOOM_STEP), e.getSceneX());
                }
                e.consume();
                return;
            }
            final double extraH = content.getBoundsInLocal().getHeight() - getViewportBounds().getHeight();
            final double extraW = content.getBoundsInLocal().getWidth() - getViewportBounds().getWidth();
            if (extraH <= 1 && extraW > 1 && e.getDeltaX() == 0 && e.getDeltaY() != 0) {
                setHvalue(clamp01(getHvalue() - e.getDeltaY() / extraW));
                e.consume();
            }
        });
        // Pellizcar en el trackpad, en un Mac. En Windows el pellizco ya llega
        // como Ctrl + rueda.
        if (NeoOs.MAC) {
            addEventFilter(ZoomEvent.ZOOM, e -> {
                setZoom(zoom * e.getZoomFactor(), e.getSceneX());
                e.consume();
            });
        }

        // Cuantas cartas caben a lo alto depende del tamanyo de la ventana, asi
        // que hay que recalcular cuando cambia. Es lo que permite repartir un
        // grupo largo en varias columnas en vez de mandarlo fuera de pantalla.
        viewportBoundsProperty().addListener((o, a, b) -> {
            final int fits = fitsPerColumn(b.getHeight());
            if (fits != perColumn) {
                perColumn = fits;
                refresh();
            }
        });

        refresh();
    }

    /** Cuantas cartas caben apiladas en una columna de este alto. */
    private int fitsPerColumn(final double viewportHeight) {
        final double usable = viewportHeight - UiScale.px(HEADING_SPACE + HINT_SPACE) - 40;
        if (usable < cardHeight) {
            return 1;
        }
        return Math.max(1, (int) ((usable - cardHeight) / (cardHeight * CardPile.peek())) + 1);
    }

    /** Alto que se lleva el titulo de la columna. */
    private static final double HEADING_SPACE = 26;

    /** Alto de la fila del aviso de zoom. */
    private static final double HINT_SPACE = 26;

    private void sizeCards() {
        cardWidth = baseWidth * zoom;
        cardHeight = cardWidth * CardNode.ASPECT;
    }

    private static double clampZoom(final double z) {
        return Double.isNaN(z) ? 1 : Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, z));
    }

    private static double clamp01(final double v) {
        return Math.max(0, Math.min(1, v));
    }

    private void updateHint() {
        final String pct = Math.round(zoom * 100) + "%";
        zoomHint.setText(NeoText.get("deck.zoom.hint", NeoOs.ctrlLabel(), pct)
                + (Math.abs(zoom - 1) > 0.01 ? "  ·  " + NeoText.get("deck.zoom.reset") : ""));
        zoomHint.setCursor(Math.abs(zoom - 1) > 0.01 ? Cursor.HAND : Cursor.DEFAULT);
    }

    /**
     * Cambia el tamanyo de las cartas y rehace las columnas, dejando quieta la
     * columna que hay bajo el raton ({@code sceneX}; NaN = no importa).
     */
    private void setZoom(final double wanted, final double sceneX) {
        final double z = clampZoom(wanted);
        if (Math.abs(z - zoom) < 0.001) {
            return;
        }
        // Donde cae el raton, en fraccion del ancho del contenido, y a cuanto
        // del borde izquierdo de lo que se ve.
        final double contentW = columns.getWidth();
        double fraction = Double.NaN;
        double offset = 0;
        if (!Double.isNaN(sceneX) && contentW > 0) {
            final double x = columns.sceneToLocal(sceneX, 0).getX();
            fraction = x / contentW;
            offset = sceneToLocal(sceneX, 0).getX();
        }

        zoom = z;
        sizeCards();
        NeoSettings.setDouble(ZOOM_KEY, zoom);
        NeoSettings.save();
        updateHint();
        perColumn = fitsPerColumn(getViewportBounds().getHeight());
        refresh();

        if (!Double.isNaN(fraction)) {
            layout();
            final double newW = columns.getWidth();
            final double extra = getContent().getBoundsInLocal().getWidth() - getViewportBounds().getWidth();
            if (extra > 0) {
                setHvalue(clamp01((fraction * newW - offset) / extra));
            }
        }
    }

    /** Cuantas caben por columna ahora mismo; 0 = todavia no se sabe. */
    private int perColumn;

    /** Vuelve a montar las columnas con lo que hay ahora en el mazo. */
    public final void refresh() {
        columns.getChildren().clear();

        final List<PaperCard> commanders = editor.commanders();
        if (!commanders.isEmpty()) {
            final List<Region> cards = new ArrayList<>();
            for (final PaperCard c : commanders) {
                cards.add(stackedCard(c, 1, true));
            }
            addGroup(NeoText.get("deck.commander"), commanders.size(), cards);
        }

        for (final Map.Entry<String, Integer> group : editor.typeCounts().entrySet()) {
            final List<Region> cards = new ArrayList<>();
            for (final Map.Entry<PaperCard, Integer> e : editor.cardsInGroup(group.getKey())) {
                cards.add(stackedCard(e.getKey(), e.getValue(), false));
            }
            addGroup(forge.neo.deck.DeckEditor.groupLabel(group.getKey()), group.getValue(), cards);
        }

        if (columns.getChildren().isEmpty()) {
            final Label empty = new Label(NeoText.get("deck.empty.short"));
            empty.getStyleClass().add("home-subtitle");
            columns.getChildren().add(empty);
        }
        openProbe();
    }

    /**
     * {@code -Dneo.deck.hover=N}: abre la primera pila que tenga carta debajo de
     * la N, como si le pasara el raton. Es para verla en una captura, que no
     * tiene raton.
     */
    private void openProbe() {
        final Integer at = Integer.getInteger("neo.deck.hover");
        if (at == null) {
            return;
        }
        for (final javafx.scene.Node col : columns.getChildren()) {
            if (col instanceof VBox) {
                for (final javafx.scene.Node n : ((VBox) col).getChildren()) {
                    if (n instanceof CardPile && ((CardPile) n).getChildren().size() > at + 1) {
                        ((CardPile) n).openAt(((CardPile) n).getChildren().get(at));
                        return;
                    }
                }
            }
        }
    }

    /**
     * Mete un grupo, partiendolo en varias columnas si no cabe a lo alto.
     *
     * <p>Es lo que hace Moxfield: antes que mandar treinta criaturas fuera de la
     * pantalla, se sigue en la columna de al lado. A lo ancho sobra sitio casi
     * siempre, y el objetivo de esta vista es ver el mazo entero de una vez.
     *
     * <p>La continuacion lleva el mismo titulo con "(sigue)" para que no parezca
     * un grupo distinto.
     */
    private void addGroup(final String name, final int count, final List<Region> cards) {
        if (cards.isEmpty()) {
            return;
        }
        final int max = perColumn > 0 ? perColumn : cards.size();
        for (int from = 0; from < cards.size(); from += max) {
            final int to = Math.min(cards.size(), from + max);
            final CardPile pile = new CardPile(cardWidth, cardHeight);
            pile.getChildren().addAll(cards.subList(from, to));
            columns.getChildren().add(from == 0
                    ? column(name, count, pile)
                    : columnContinued(name, pile));
        }
    }

    /** La segunda columna y siguientes de un grupo que no cabia. */
    private Region columnContinued(final String name, final CardPile pile) {
        final Label heading = new Label(name + "  " + NeoText.get("deck.more"));
        heading.getStyleClass().addAll("deck-group", "deck-group-cont");
        heading.setMinHeight(Region.USE_PREF_SIZE);
        return columnBox(heading, pile);
    }

    /** Una columna: titulo con el recuento y debajo la pila. */
    private Region column(final String name, final int count, final CardPile pile) {
        final Label heading = new Label(name + "  (" + count + ")");
        heading.getStyleClass().add("deck-group");
        heading.setMinHeight(Region.USE_PREF_SIZE);

        return columnBox(heading, pile);
    }

    private Region columnBox(final Label heading, final CardPile pile) {
        final VBox box = new VBox(4, heading, pile);
        box.setAlignment(Pos.TOP_LEFT);
        // Sin esto el HBox estira las columnas al alto de la mas larga y las
        // pilas cortas quedan flotando.
        box.setFillWidth(false);
        // La columna mide lo que la carta o lo que el titulo, lo que sea mas
        // ancho: con el zoom alejado la carta es mas estrecha que "Comandante
        // (1)", y recortarlo a "Comanda..." no dice nada.
        heading.setMinWidth(Region.USE_PREF_SIZE);
        box.setMinWidth(Region.USE_PREF_SIZE);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        return box;
    }

    /**
     * Una carta de la pila.
     *
     * <p>Click derecho la amplia y click izquierdo quita una copia, los mismos
     * gestos que en la pantalla de edicion: cambiar de vista no deberia cambiar
     * lo que hacen tus manos.
     */
    private Region stackedCard(final PaperCard card, final int amount, final boolean isCommander) {
        final CardNode node = new CardNode(cardWidth);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(card));
        if (isCommander) {
            node.setCommanderStyle(true);
        }

        final StackPane box = new StackPane(node);

        // Varias copias se ensenyan como una carta con su "×N": apilar cuatro
        // Islas identicas ocupa sitio y no dice nada que el numero no diga.
        if (amount > 1) {
            final Label count = new Label("×" + amount);
            count.getStyleClass().add("catalogue-count");
            box.getChildren().add(count);
            StackPane.setAlignment(count, Pos.TOP_RIGHT);
            StackPane.setMargin(count, new Insets(3));
        }

        box.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                actions.menu(card, box, e.getScreenX(), e.getScreenY(), isCommander);
            } else if (e.getButton() == MouseButton.PRIMARY) {
                actions.zoom(card);
            }
        });

        // Al pasar por encima, la pila se abre por debajo de esta carta para
        // leerla entera sin taparle el paso a la de abajo: ver CardPile.
        //
        // NO se pinta por delante (setViewOrder/toFront): JavaFX reparte el
        // raton en el mismo orden en que pinta, y la carta entera tapaba las
        // franjas de las de debajo, que dejaban de poder senyalarse.
        box.setOnMouseEntered(e -> {
            if (box.getParent() instanceof CardPile) {
                ((CardPile) box.getParent()).openAt(box);
            }
        });
        return box;
    }

}
