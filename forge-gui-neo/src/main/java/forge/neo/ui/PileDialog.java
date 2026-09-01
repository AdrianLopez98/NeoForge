package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.game.card.CardView;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * <b>Elige una de las dos pilas.</b>
 *
 * <p>Es la pregunta de <i>Fortune's Favor</i> y de toda la familia de cartas que
 * parten un montón en dos ({@code TwoPilesEffect}): alguien separa unas cartas
 * en dos pilas y tú te quedas con una. A veces una de las dos va <b>boca
 * abajo</b>, y ahí está toda la gracia de la carta — apostar sin verla.
 *
 * <p><b>Por qué hace falta un diálogo propio.</b> El motor manda esta pregunta
 * como una lista de cartas normal y corriente:
 *
 * <pre>
 *   [ etiqueta de la pila 1 ] [ cartas de la pila 1, si va boca arriba ]
 *   [ etiqueta de la pila 2 ] [ cartas de la pila 2, si va boca arriba ]
 * </pre>
 *
 * y las <b>etiquetas son {@code CardView} falsos</b> — {@code new
 * CardView(Integer.MIN_VALUE, null, "-- Pile 1 (2 cards) --")}, sin tracker y
 * sin imagen. Pasado por el diálogo de elegir cartas eso salía como cuatro
 * rectángulos del mismo tamaño: dos cartas en blanco con un texto en inglés y
 * dos cartas de verdad sin nada que dijera de qué pila eran. Y si clicabas una
 * carta en vez de una etiqueta, {@code PlayerControllerHuman.chooseCardsPile}
 * <b>vuelve a preguntar en un {@code while (true)}</b>: el diálogo se reabría
 * sin decir por qué.
 *
 * <p><b>Cómo se reconocen las etiquetas sin adivinar.</b> Por el id:
 * {@code Integer.MIN_VALUE} y {@code Integer.MIN_VALUE + 1} son centinelas que
 * ninguna carta de verdad puede tener. Y a qué pila pertenece cada carta lo dice
 * el <b>orden</b>: van detrás de su etiqueta. Las dos cosas son contrato del
 * motor, no una corazonada sobre el texto.
 *
 * <p>Del texto sí se saca una cosa, y con red: <b>cuántas cartas tiene la pila
 * que no ves</b>. El motor lo compone a pelo y sin traducir
 * ({@code "-- Pile 1 (2 cards) --"}), así que se busca el número con una
 * expresión regular — y si no aparece, no se dice. Es un dato que cambia la
 * decisión: no es lo mismo apostar por dos cartas que por cuatro.
 */
public class PileDialog extends VBox {

    /** Las etiquetas de pila que fabrica {@code chooseCardsPile}. */
    private static final int PILE_1_ID = Integer.MIN_VALUE;
    private static final int PILE_2_ID = Integer.MIN_VALUE + 1;

    /** "-- Pile 1 (2 cards) --" — el motor lo escribe así, sin traducir. */
    private static final Pattern HOW_MANY = Pattern.compile("\\((\\d+)\\s");

    private CardView chosen;
    private final List<Region> panels = new ArrayList<>();
    private final List<CardView> labels = new ArrayList<>();
    private final Button accept = new Button(NeoText.get("common.accept"));

    /**
     * ¿Esta lista es una elección de pilas?
     *
     * @return true si trae las dos etiquetas centinela del motor
     */
    public static boolean looksLikePiles(final List<?> options) {
        if (options == null || options.size() < 2) {
            return false;
        }
        boolean one = false;
        boolean two = false;
        for (final Object o : options) {
            if (o instanceof CardView cv) {
                one |= cv.getId() == PILE_1_ID;
                two |= cv.getId() == PILE_2_ID;
            }
        }
        return one && two;
    }

    public PileDialog(final String title, final List<CardView> options, final double cardWidth,
                      final Consumer<CardView> onDone) {
        getStyleClass().add("dialog");
        setSpacing(14);
        setPadding(new Insets(18));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(title == null || title.isBlank()
                ? NeoText.get("pile.title") : title);
        heading.getStyleClass().add("dialog-title");

        final Label help = new Label(NeoText.get("pile.help"));
        help.getStyleClass().add("dialog-counter");

        // Repartir: cada carta va con la etiqueta que tiene delante.
        final List<CardView> currentCards = new ArrayList<>();
        final HBox piles = new HBox(18);
        piles.setAlignment(Pos.TOP_CENTER);
        CardView currentLabel = null;
        for (final CardView cv : options) {
            if (cv == null) {
                continue;
            }
            if (cv.getId() == PILE_1_ID || cv.getId() == PILE_2_ID) {
                if (currentLabel != null) {
                    piles.getChildren().add(panelFor(currentLabel, currentCards, cardWidth));
                }
                currentLabel = cv;
                currentCards.clear();
            } else if (currentLabel != null) {
                currentCards.add(cv);
            }
        }
        if (currentLabel != null) {
            piles.getChildren().add(panelFor(currentLabel, currentCards, cardWidth));
        }

        accept.getStyleClass().add("btn-primary");
        accept.setDisable(true);
        accept.setOnAction(e -> onDone.accept(chosen));

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, gap, accept);
        footer.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(heading, help, piles, footer);
    }

    /** Una pila: su nombre, lo que se ve dentro y un click que la elige. */
    private Region panelFor(final CardView label, final List<CardView> cards,
                            final double cardWidth) {
        final int index = panels.size();
        labels.add(label);

        final Label name = new Label(NeoText.get("pile.name", index + 1));
        name.getStyleClass().add("pile-name");

        final VBox box = new VBox(10);
        box.getStyleClass().add("pile-panel");
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(12));
        box.getChildren().add(name);

        if (cards.isEmpty()) {
            // La pila que NO ves: es toda la gracia de la carta, así que se
            // dice cuántas cartas tiene y que no se pueden mirar.
            final int howMany = howMany(label);
            final Label hidden = new Label(NeoText.get("pile.faceDown"));
            hidden.getStyleClass().add("pile-hidden");
            hidden.setWrapText(true);
            hidden.setAlignment(Pos.CENTER);
            hidden.setPrefSize(cardWidth, cardWidth * CardNode.ASPECT);
            hidden.setMinSize(cardWidth, cardWidth * CardNode.ASPECT);
            box.getChildren().add(hidden);
            box.getChildren().add(count(howMany, cards.size()));
        } else {
            final FlowPane flow = new FlowPane(8, 8);
            flow.setAlignment(Pos.CENTER);
            flow.setPrefWrapLength(Math.min(cards.size(), 3) * (cardWidth + 8));
            for (final CardView cv : cards) {
                final CardNode node = new CardNode(cardWidth);
                node.setRotationEnabled(false);
                node.setHoverEnabled(false);
                node.setCard(cv);
                // El click tiene que elegir la PILA, no la carta.
                node.setMouseTransparent(true);
                flow.getChildren().add(node);
            }
            box.getChildren().add(flow);
            box.getChildren().add(count(howMany(label), cards.size()));
        }

        box.setCursor(javafx.scene.Cursor.HAND);
        box.setOnMouseClicked(e -> pick(index));
        panels.add(box);
        return box;
    }

    /** "2 cartas", diciendo la verdad aunque el texto del motor no se entienda. */
    private static Label count(final int fromLabel, final int visible) {
        final int n = fromLabel > 0 ? fromLabel : visible;
        final Label l = new Label(n == 1 ? NeoText.get("pile.oneCard")
                : NeoText.get("pile.cards", n));
        l.getStyleClass().add("dialog-counter");
        return l;
    }

    /** Cuántas cartas dice la etiqueta que tiene. 0 si no se puede saber. */
    private static int howMany(final CardView label) {
        try {
            final String name = label.getName();
            if (name == null) {
                return 0;
            }
            final Matcher m = HOW_MANY.matcher(name);
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    private void pick(final int index) {
        chosen = labels.get(index);
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).pseudoClassStateChanged(PICKED, i == index);
        }
        accept.setDisable(false);
    }

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");
}
