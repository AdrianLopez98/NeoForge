package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.List;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Elegir con que edicion — o sea, con que arte — llevas una carta.
 *
 * <p>Es el <i>switch printing</i> de Moxfield. Da igual para las reglas y no da
 * igual para nada mas: la carta que eliges es la que vas a ver en la mesa toda
 * la partida.
 *
 * <p>Se ensenyan las cartas, no una lista de codigos de edicion, porque la
 * pregunta que se esta haciendo el jugador es <i>cual me gusta mas</i>. El
 * codigo y el numero van debajo para quien busque una concreta.
 */
public class PrintingDialog extends VBox {

    /** Cuantas impresiones se pintan a la vez. */
    static final int PAGE = 40;

    public PrintingDialog(final PaperCard current, final List<PaperCard> printings,
                          final double cardWidth, final Consumer<PaperCard> onPick,
                          final Runnable onCancel) {
        this(current, printings, cardWidth, onPick, onCancel,
                NeoText.get("printing.title", CardText.nameOf(current)),
                printings.size() == 1 ? NeoText.get("printing.only") : NeoText.get("printing.hint"),
                Math.min(UiScale.px(520), cardWidth * CardNode.ASPECT * 2 + 60));
    }

    /**
     * Con titulo, ayuda y alto propios. Lo usa la enciclopedia, donde no se
     * cambia el arte de un mazo sino que se MIRAN: ahi caben mas filas, y
     * con el clic derecho se amplia cada una.
     */
    public PrintingDialog(final PaperCard current, final List<PaperCard> printings,
                          final double cardWidth, final Consumer<PaperCard> onPick,
                          final Runnable onCancel, final String title, final String help,
                          final double viewportHeight) {
        getStyleClass().add("dialog");
        setSpacing(12);
        setPadding(new Insets(20, 24, 18, 24));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");

        final Label hint = new Label(help);
        this.hint = hint;
        hint.getStyleClass().add("dialog-text");
        hint.setWrapText(true);
        hint.setMaxWidth(UiScale.px(720));

        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.CENTER);
        grid.setPrefWrapLength(Math.max(640, cardWidth * 5));

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(viewportHeight);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        final Label count = new Label(printings.size() == 1
                ? NeoText.get("printing.count.one")
                : NeoText.get("printing.count", printings.size()));
        count.getStyleClass().add("dialog-counter");

        final Button cancel = new Button(NeoText.get("common.cancel"));
        cancel.getStyleClass().add("btn-secondary");
        cancel.setOnAction(e -> onCancel.run());

        // PAGINADO (Ana, 06-10-2026: "va muy lag por tener todos cargados"):
        // un Swamp tiene 748 impresiones y cada una es una carta con su arte.
        // Se pintan de PAGE en PAGE; la actual va en la primera pagina.
        final java.util.List<PaperCard> ordered = new java.util.ArrayList<>(printings);
        if (ordered.remove(current)) {
            ordered.add(0, current);
        }
        // EL SELECTOR DE COLECCION (Ana, 06-10-2026: "asi se encuentra mas
        // facil el arte"): las colecciones de esta carta, la mas nueva arriba.
        final java.util.List<PaperCard> shown = new java.util.ArrayList<>(ordered);
        final java.util.Map<String, String> setNames = new java.util.LinkedHashMap<>();
        final java.util.List<PaperCard> bySet = new java.util.ArrayList<>(ordered);
        bySet.sort(java.util.Comparator.comparing((PaperCard c) -> editionDate(c)).reversed());
        for (final PaperCard c : bySet) {
            setNames.putIfAbsent(c.getEdition(), editionName(c));
        }
        final javafx.scene.control.ComboBox<String> setBox = new javafx.scene.control.ComboBox<>();
        setBox.getItems().add(NeoText.get("printing.set.all"));
        setBox.getItems().addAll(setNames.values());
        setBox.getSelectionModel().select(0);
        setBox.setVisibleRowCount(16);
        setBox.setId("printing-set");
        setBox.getStyleClass().add("team-combo");
        setBox.setPrefWidth(UiScale.px(300));
        setBox.setMaxWidth(UiScale.px(300));
        final Label pageLabel = new Label();
        pageLabel.getStyleClass().add("dialog-counter");
        final Button prev = new Button("‹");
        final Button next = new Button("›");
        prev.getStyleClass().add("segment");
        next.getStyleClass().add("segment");
        final int[] page = {0};
        final Runnable show = () -> {
            final int pages = Math.max(1, (shown.size() + PAGE - 1) / PAGE);
            final java.util.List<javafx.scene.Node> tiles = new java.util.ArrayList<>();
            final int from = page[0] * PAGE;
            for (final PaperCard printing : shown.subList(from, Math.min(shown.size(), from + PAGE))) {
                tiles.add(tile(printing, printing.equals(current), cardWidth, onPick));
            }
            grid.getChildren().setAll(tiles);
            scroll.setVvalue(0);
            pageLabel.setText(NeoText.get("pager.page", page[0] + 1, pages));
            prev.setDisable(page[0] == 0);
            next.setDisable(page[0] >= pages - 1);
        };
        prev.setOnAction(e -> { page[0] = Math.max(0, page[0] - 1); show.run(); });
        next.setOnAction(e -> { page[0] = page[0] + 1; show.run(); });
        final HBox pager = new HBox(8, prev, pageLabel, next);
        pager.setAlignment(Pos.CENTER);
        setBox.valueProperty().addListener((o, was, now) -> {
            final int i = setBox.getSelectionModel().getSelectedIndex();
            final String code = i <= 0 ? null : new java.util.ArrayList<>(setNames.keySet()).get(i - 1);
            shown.clear();
            for (final PaperCard c : ordered) {
                if (code == null || code.equals(c.getEdition())) {
                    shown.add(c);
                }
            }
            page[0] = 0;
            show.run();
        });
        show.run();
        // -Dneo.printing.set=N deja elegida la coleccion N, para capturarla.
        final int presetSet = Integer.getInteger("neo.printing.set", 0);
        if (presetSet > 0 && presetSet < setBox.getItems().size()) {
            setBox.getSelectionModel().select(presetSet);
        }
        // Mas rapida con la rueda: con cartas grandes, el paso de fabrica de
        // JavaFX avanza apenas un tercio de carta (Ana, 06-10-2026).
        scroll.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, ev -> {
            final double content = grid.getHeight() - scroll.getViewportBounds().getHeight();
            if (content > 0 && ev.getDeltaY() != 0) {
                scroll.setVvalue(Math.max(0, Math.min(1, scroll.getVvalue() - ev.getDeltaY() * 3.5 / content)));
                ev.consume();
            }
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);
        final HBox footer = new HBox(10, count, setBox, gap, pager, gap2, cancel);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, hint, scroll, footer);
    }

    /** Cuantas copias se cambian: de fabrica todas, que es lo que hacia siempre. */
    private int copies = Integer.MAX_VALUE;
    /** La ayuda: con el contador ya no es verdad que "se cambiaran todas". */
    private Label hint;

    public int copies() {
        return copies;
    }

    /**
     * Con varias copias de esta impresion en el mazo, un contador de cuantas
     * cambian (Discord, 06-10-2026: nueve Nazgul con nueve artes; Ana: treinta
     * llanuras, quince de cada). Empieza en todas.
     */
    public PrintingDialog offerCopies(final int have) {
        if (have <= 1) {
            return this;
        }
        copies = Math.max(1, Math.min(have, Integer.getInteger("neo.printing.copies", have)));
        final Label what = new Label(NeoText.get("printing.copies"));
        what.getStyleClass().add("dialog-text");
        final Label value = new Label();
        value.setId("printing-copies");
        value.getStyleClass().add("dialog-title");
        value.setMinWidth(UiScale.px(70));
        value.setAlignment(Pos.CENTER);
        final Button less = new Button("-");
        final Button more = new Button("+");
        final Button all = new Button(NeoText.get("printing.copies.all", have));
        for (final Button b : new Button[] {less, more, all}) {
            b.getStyleClass().add("segment");
        }
        final Runnable sync = () -> {
            value.setText(NeoText.get("printing.copies.of", copies, have));
            less.setDisable(copies <= 1);
            more.setDisable(copies >= have);
            all.setDisable(copies >= have);
        };
        less.setOnAction(e -> { copies = Math.max(1, copies - 1); sync.run(); });
        more.setOnAction(e -> { copies = Math.min(have, copies + 1); sync.run(); });
        all.setOnAction(e -> { copies = have; sync.run(); });
        sync.run();
        hint.setText(NeoText.get("printing.hint.copies"));
        final HBox row = new HBox(8, what, less, value, more, all);
        row.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(2, row);
        return this;
    }

    /** Una edicion: la carta, y debajo el codigo del set y su numero. */
    private static Region tile(final PaperCard printing, final boolean isCurrent,
                               final double cardWidth, final Consumer<PaperCard> onPick) {
        final CardNode node = new CardNode(cardWidth);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(printing));
        node.setSelectable(isCurrent);

        final Label label = new Label(editionName(printing));
        label.getStyleClass().add("printing-label");
        label.setWrapText(true);
        label.setMaxWidth(cardWidth);
        label.setAlignment(Pos.CENTER);
        label.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        final VBox box = new VBox(4, node, label);
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().add("catalogue-tile");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // Solo el izquierdo: el derecho amplia la carta (CardZoom) y no puede
        // elegirla a la vez.
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                onPick.accept(printing);
            }
        });

        if (isCurrent) {
            final Label mark = new Label(NeoText.get("printing.current"));
            mark.getStyleClass().add("catalogue-count");
            final StackPane stack = new StackPane(box, mark);
            StackPane.setAlignment(mark, Pos.TOP_RIGHT);
            StackPane.setMargin(mark, new Insets(4));
            stack.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            return stack;
        }
        return box;
    }

    /**
     * "MH2 · Modern Horizons 2", o solo el codigo si no se conoce la edicion.
     *
     * <p>El codigo es lo que la gente sabe de memoria y el nombre lo que
     * reconoce; caben los dos.
     */
    private static java.util.Date editionDate(final PaperCard card) {
        try {
            final forge.card.CardEdition ed = FModel.getMagicDb().getEditions().get(card.getEdition());
            if (ed != null && ed.getDate() != null) {
                return ed.getDate();
            }
        } catch (final RuntimeException ignored) {
            // sin fecha: al final
        }
        return new java.util.Date(0);
    }

    private static String editionName(final PaperCard card) {
        final String code = card.getEdition();
        try {
            final forge.card.CardEdition ed = FModel.getMagicDb().getEditions().get(code);
            if (ed != null && ed.getName() != null && !ed.getName().equals(code)) {
                return code + " · " + ed.getName();
            }
        } catch (final RuntimeException e) {
            // Una edicion desconocida no vale una excepcion: con el codigo basta.
        }
        return code;
    }
}
