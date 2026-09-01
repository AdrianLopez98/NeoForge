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
import javafx.scene.input.MouseButton;
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
    private final double cardWidth;
    private final double cardHeight;
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
        this.cardWidth = cardWidth * 0.85;
        this.cardHeight = this.cardWidth * CardNode.ASPECT;
        this.actions = actions;

        getStyleClass().add("dialog-scroll");
        setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        columns.setPadding(new Insets(14, 26, 26, 26));
        columns.setAlignment(Pos.TOP_LEFT);
        setContent(columns);

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
        final double usable = viewportHeight - HEADING_SPACE - 40;
        if (usable < cardHeight) {
            return 1;
        }
        return Math.max(1, (int) ((usable - cardHeight) / (cardHeight * CardPile.peek())) + 1);
    }

    /** Alto que se lleva el titulo de la columna. */
    private static final double HEADING_SPACE = 26;

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
        box.setMinWidth(cardWidth);
        box.setPrefWidth(cardWidth);
        box.setMaxWidth(cardWidth);
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

        // Al pasar por encima, la carta se pinta por delante de las de abajo
        // para poder leerla entera sin ampliarla.
        //
        // Se hace con setViewOrder y NO con toFront(). toFront() mueve el nodo
        // al final de la lista de hijos, y CardPile coloca cada carta segun su
        // posicion EN ESA LISTA: la carta saltaba al fondo de la pila y todas
        // las de abajo se recolocaban solas. setViewOrder cambia el orden de
        // PINTADO sin tocar el de los hijos, que es justo lo que hace falta.
        box.setOnMouseEntered(e -> box.setViewOrder(-1));
        box.setOnMouseExited(e -> box.setViewOrder(0));
        return box;
    }

}
