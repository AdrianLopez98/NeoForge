package forge.neo.adventure;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.card.CardText;
import forge.neo.ui.CardZoom;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Elegir el comandante de una Aventura nueva: tres cartas, una se elige.
 *
 * <p>La carta en grande y nada mas, porque se elige mirandola; el clic derecho
 * la amplia para leerla (y solo el izquierdo elige: elegir no se deshace). Y
 * una salida que no haya que adivinar (principio 7): quedarse con el mazo de
 * siempre.
 *
 * <p>Y dos botones mas (Discord, 01-11-2026: <i>"a way to choose our commander
 * for Adventure, or give us a reroll button"</i>), sin quitar el sorteo, que
 * tambien gusta: <b>Otros tres</b>, que no repite los ya vistos, y <b>Elegir el
 * mio</b>, un buscador con todos los comandantes de ese color
 * ({@link StarterDeck#search}). El color sigue siendo el del Adventure.
 */
final class CommanderPickScreen extends BorderPane {

    private final HBox cards = new HBox(36);
    private final Label status = new Label();
    private final Button fixed = new Button(NeoText.get("adventure.pick.fixed"));
    /** Ya se ha elegido: sin apagar las cartas (apagadas se ven grises justo al elegir). */
    private boolean locked;
    private final List<Runnable> picks = new ArrayList<>();
    /** Los de la pagina del buscador que se ve. */
    private final List<Runnable> searchPicks = new ArrayList<>();
    private final Button reroll = new Button(NeoText.get("adventure.pick.reroll"));
    private final Button own = new Button(NeoText.get("adventure.pick.own"));
    private final Label sub = new Label(NeoText.get("adventure.pick.subtitle"));
    private final double cardWidth;
    private final Consumer<PaperCard> onPick;
    private final BiFunction<String, Boolean, List<PaperCard>> search;
    /** "Todos los colores": el buscador deja de mirar solo los del color del Adventure. */
    private final javafx.scene.control.ToggleButton anyColor =
            new javafx.scene.control.ToggleButton(NeoText.get("adventure.pick.allColors"));
    private String query = "";
    /** Todos los que se han ensenyado, para que "Otros tres" no los repita. */
    private final List<PaperCard> seen = new ArrayList<>();
    private VBox threeView;
    private VBox searchView;
    private final FlowPane results = new FlowPane();
    private final Label found = new Label();
    private final Button prev = new Button("\u2039");
    private final Button next = new Button("\u203A");
    private List<PaperCard> hits = List.of();
    private int page;
    private static final int PAGE = 8;

    /**
     * @param options  los tres comandantes
     * @param onPick   con el elegido
     * @param onFixed  si prefiere el mazo de siempre
     * @param reroll   otros tres, sin los ya vistos
     * @param search   los que casan con lo buscado: de ese color, o de todos
     */
    CommanderPickScreen(final List<PaperCard> options, final double cardWidth,
                        final Consumer<PaperCard> onPick, final Runnable onFixed,
                        final Function<List<PaperCard>, List<PaperCard>> reroll,
                        final BiFunction<String, Boolean, List<PaperCard>> search) {
        this.cardWidth = cardWidth;
        this.onPick = onPick;
        this.search = search;
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("adventure.pick.title"));
        title.getStyleClass().add("home-title");
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        final VBox top = new VBox(6, title, sub);
        top.setAlignment(Pos.CENTER);
        top.setPadding(new Insets(36, 40, 10, 40));
        setTop(top);

        cards.setAlignment(Pos.CENTER);
        showOptions(options);
        status.getStyleClass().add("home-subtitle");
        threeView = new VBox(24, cards);
        threeView.setAlignment(Pos.CENTER);
        searchView = buildSearch();
        final VBox center = new VBox(18, threeView, status);
        center.setAlignment(Pos.CENTER);
        setCenter(center);

        this.reroll.getStyleClass().add("btn-secondary");
        this.reroll.setOnAction(e -> {
            if (!locked) {
                showOptions(reroll.apply(new ArrayList<>(seen)));
            }
        });
        own.getStyleClass().add("btn-secondary");
        own.setOnAction(e -> toggleSearch(center));

        fixed.getStyleClass().add("btn-secondary");
        fixed.setOnAction(e -> {
            locked = true;
            fixed.setDisable(true);
            onFixed.run();
        });
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        // Lo que no se deshace (quedarse el de siempre) a la izquierda; lo
        // demas a la derecha, como el pie de las otras pantallas.
        final HBox bottom = new HBox(12, fixed, gap, this.reroll, own);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(10, 40, 36, 40));
        setBottom(bottom);

        CardZoom.install(this);
    }

    /** Pone estas cartas como las opciones grandes. */
    private void showOptions(final List<PaperCard> options) {
        cards.getChildren().clear();
        picks.clear();
        for (final PaperCard pc : options) {
            seen.add(pc);
            final Label name = new Label(CardText.nameOf(pc));
            name.getStyleClass().add("home-subtitle");
            final VBox box = new VBox(12, card(pc, cardWidth, picks), name);
            box.setAlignment(Pos.CENTER);
            cards.getChildren().add(box);
        }
    }

    /** Una carta que se elige con el clic izquierdo (el derecho la amplia). */
    private CardNode card(final PaperCard pc, final double width, final List<Runnable> into) {
        final CardNode node = new CardNode(width);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(pc));
        node.setCursor(javafx.scene.Cursor.HAND);
        final Runnable pick = () -> {
            if (!locked) {
                building(pc);
                onPick.accept(pc);
            }
        };
        into.add(pick);
        node.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                pick.run();
            }
        });
        return node;
    }

    /** El buscador: todos los de ese color, por paginas. */
    private VBox buildSearch() {
        final TextField field = new TextField();
        field.setPromptText(NeoText.get("ascent.setup.search"));
        field.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("ascent.setup.search.tip")));
        field.setPrefWidth(cardWidth * 1.6);
        field.setMaxWidth(cardWidth * 1.6);
        field.textProperty().addListener((o, a, b) -> {
            query = b == null ? "" : b;
            research();
        });
        anyColor.getStyleClass().add("btn-secondary");
        anyColor.setOnAction(e -> {
            sub.setText(NeoText.get(anyColor.isSelected() ? "adventure.pick.ownSubtitleAll"
                    : "adventure.pick.ownSubtitle"));
            research();
        });
        final HBox searchRow = new HBox(12, field, anyColor);
        searchRow.setAlignment(Pos.CENTER);
        results.setHgap(16);
        results.setVgap(16);
        results.setAlignment(Pos.CENTER);
        results.setPrefWrapLength(cardWidth * 0.55 * 4 + 16 * 4);
        found.getStyleClass().add("home-subtitle");
        prev.getStyleClass().add("btn-secondary");
        next.getStyleClass().add("btn-secondary");
        prev.setOnAction(e -> {
            page--;
            fillResults();
        });
        next.setOnAction(e -> {
            page++;
            fillResults();
        });
        final HBox pager = new HBox(12, prev, found, next);
        pager.setAlignment(Pos.CENTER);
        final VBox box = new VBox(14, searchRow, results, pager);
        box.setAlignment(Pos.CENTER);
        hits = search.apply("", false);
        return box;
    }

    private void research() {
        hits = search.apply(query, anyColor.isSelected());
        page = 0;
        fillResults();
    }

    private void fillResults() {
        final int pages = Math.max(1, (hits.size() + PAGE - 1) / PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        results.getChildren().clear();
        searchPicks.clear();
        final int from = page * PAGE;
        for (int i = from; i < Math.min(hits.size(), from + PAGE); i++) {
            results.getChildren().add(card(hits.get(i), cardWidth * 0.55, searchPicks));
        }
        found.setText(hits.isEmpty() ? NeoText.get("adventure.pick.none")
                : NeoText.get("adventure.pick.found", from + 1, Math.min(hits.size(), from + PAGE),
                        hits.size()));
        prev.setDisable(page == 0);
        next.setDisable(page >= pages - 1);
    }

    /** Entre los tres y el buscador. */
    private void toggleSearch(final VBox center) {
        if (locked) {
            return;
        }
        final boolean toSearch = center.getChildren().get(0) == threeView;
        if (toSearch) {
            fillResults();
        }
        center.getChildren().set(0, toSearch ? searchView : threeView);
        own.setText(NeoText.get(toSearch ? "adventure.pick.backToThree" : "adventure.pick.own"));
        sub.setText(NeoText.get(!toSearch ? "adventure.pick.subtitle"
                : anyColor.isSelected() ? "adventure.pick.ownSubtitleAll" : "adventure.pick.ownSubtitle"));
        reroll.setVisible(!toSearch);
        reroll.setManaged(!toSearch);
    }

    /** Solo pruebas: abre el buscador, como el boton. */
    void openSearchForTest() {
        own.fire();
        // Con "todos los colores": el primero ya no es del color del Adventure.
        anyColor.fire();
    }

    /** Elegido: ya no se puede tocar nada mientras se monta el mazo. */
    private void building(final PaperCard pc) {
        locked = true;
        fixed.setDisable(true);
        reroll.setDisable(true);
        own.setDisable(true);
        status.setText(NeoText.get("adventure.pick.building", CardText.nameOf(pc)));
    }

    /** Solo pruebas: elige la opcion {@code i} como un clic. */
    void pickForTest(final int i) {
        picks.get(i).run();
    }

    /** Solo pruebas: elige el {@code i} de la pagina del buscador. */
    void pickSearchForTest(final int i) {
        searchPicks.get(i).run();
    }

    void failed() {
        status.setText(NeoText.get("adventure.pick.failed"));
    }
}
