package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * Un mazo, representado por la carta de su comandante.
 *
 * <p>Es la idea que sostiene la pantalla de inicio: <b>el mazo se elige mirando,
 * no leyendo</b>. Todo el mundo recuerda sus mazos por el comandante ("el de
 * Sephiroth"), no por el nombre del fichero.
 *
 * <p>Si el mazo no tiene comandante — no deberia pasar en Commander — se queda
 * la primera carta: mejor una portada rara que un hueco gris.
 *
 * <p>Vive aparte para poder usarse en los dos sitios donde se elige un mazo: la
 * pantalla de inicio y el selector del mazo de cada rival.
 */
public final class DeckTile extends VBox {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Deck deck;
    private final CardNode cover;

    public DeckTile(final Deck deck, final double width) {
        this.deck = deck;
        getStyleClass().add("deck-tile");
        setSpacing(8);
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(10));

        cover = new CardNode(width);
        cover.setHoverEnabled(false);
        final PaperCard face = commanderOf(deck);
        if (face != null) {
            cover.setCard(CardView.getCardForUi(face));
            // El marco de comandante SOLO si de verdad hay uno. La portada cae
            // a la primera carta del mazo cuando no lo hay, y ponerle el marco
            // igual pintaba un indicador de comandante en cada mazo de
            // Estandar, formato que no tiene comandantes.
            cover.setCommanderStyle(hasCommander(deck));
        }

        final Label name = new Label(deck == null ? "" : deck.getName());
        name.getStyleClass().add("deck-tile-name");
        name.setWrapText(true);
        name.setMaxWidth(width);
        name.setAlignment(Pos.CENTER);
        name.setTextAlignment(TextAlignment.CENTER);

        final Label sub = new Label(face == null
                ? NeoText.get("count.cards", deck.getMain().countAll())
                : CardText.nameOf(face));
        sub.getStyleClass().add("deck-tile-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(width);
        sub.setAlignment(Pos.CENTER);
        sub.setTextAlignment(TextAlignment.CENTER);

        getChildren().addAll(new StackPane(cover), name, sub);
    }

    /** La papelera de este mazo, si lo suyo es que se pueda borrar. */
    private Button bin;

    /**
     * Pone la papelera en la esquina de la portada.
     *
     * <p><b>Se ve en el mazo elegido y en el que tienes debajo del raton</b>, no
     * en los sesenta a la vez. Las dos condiciones hacen falta y por motivos
     * distintos: en el elegido, porque si no <i>nadie descubre que existe</i> —
     * un boton que solo aparece al pasar por encima hay que saber buscarlo; al
     * pasar por encima, porque borrar un mazo que no es el elegido no deberia
     * obligar a elegirlo antes.
     *
     * <p>Lo que no se hace es encenderlas todas: es lo unico de esta pantalla
     * que no se puede deshacer, y una rejilla llena de papeleras invita justo a
     * lo que no hay que hacer.
     *
     * <p>Y consume su propio click: encima hay una baldosa entera que significa
     * "elige este mazo", asi que sin esto borrar seleccionaria tambien.
     */
    public void setOnDelete(final Runnable onDelete) {
        if (onDelete == null || !(getChildren().get(0) instanceof StackPane box)) {
            return;
        }
        bin = new Button(NeoText.get("deck.delete"));
        // Estilo propio y no el `btn-danger` de siempre, por una razon que solo
        // se ve mirandolo: aquel es transparente con el texto apagado, y encima
        // del arte de una carta — que es dibujo a todo color — no se lee. Se
        // dibujo bien desde el primer momento y aun asi parecia que no estaba.
        bin.getStyleClass().add("deck-tile-bin");
        bin.setFocusTraversable(false);
        bin.setVisible(false);
        bin.setOnAction(e -> onDelete.run());
        bin.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                javafx.event.Event::consume);

        StackPane.setAlignment(bin, Pos.TOP_RIGHT);
        StackPane.setMargin(bin, new Insets(6));
        box.getChildren().add(bin);

        hoverProperty().addListener((o, was, now) -> updateBin());
    }

    private void updateBin() {
        if (bin != null) {
            bin.setVisible(isHover() || selected);
        }
    }

    /** Si el mazo lleva comandante de verdad, no una portada de repuesto. */
    public static boolean hasCommander(final Deck deck) {
        return deck != null && deck.has(DeckSection.Commander)
                && !deck.get(DeckSection.Commander).isEmpty();
    }

    /** El comandante del mazo, o la primera carta si no lo tiene. */
    public static PaperCard commanderOf(final Deck deck) {
        if (deck == null) {
            return null;
        }
        if (deck.has(DeckSection.Commander)) {
            for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                return e.getKey();
            }
        }
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            return e.getKey();
        }
        return null;
    }

    public Deck getDeck() {
        return deck;
    }

    private boolean selected;

    public void setSelected(final boolean on) {
        selected = on;
        pseudoClassStateChanged(SELECTED, on);
        cover.setSelectable(on);
        updateBin();
    }

    public void refresh() {
        cover.refresh();
    }
}
