package forge.neo.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import forge.card.CardEdition;
import forge.game.GameFormat;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.deck.CardLibrary;
import forge.neo.quest.NeoQuest;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * La enciclopedia: todas las cartas, para mirarlas.
 *
 * <p>Pedido en itch.io el 27-09-2026: <i>"browse and filter cards without
 * having to enter the deck-building screen"</i>. Es una pregunta que no hacia
 * ninguna pantalla — "que cartas existen" — y por eso tiene casilla propia en
 * el menu (la auditoría del motor). El constructor enseña el catalogo, pero siempre
 * a traves de un mazo: con su formato, su comandante y su "solo lo que cabe".
 *
 * <p><b>Solo se mira</b>, como la coleccion de la Quest: sin anyadir a nada,
 * nada que se pueda tocar sin querer. Un clic amplia la carta dentro de la
 * lista entera de resultados, asi que la rueda pasa a la siguiente — es la
 * forma de "hojear" que pedia el informe.
 *
 * <p>Los filtros son los del constructor ({@link CardFilterBar}, mismas
 * claves y mismos botones) mas lo que solo tiene sentido aqui: expansion
 * (con SU impresion: su arte y su rareza), formato, coleccion y "lo mas
 * nuevo". Buscar lo hace {@link CardLibrary}, sin JavaFX, que es lo que
 * prueba {@code librarycheck}.
 */
public class CardLibraryScreen extends BorderPane {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /**
     * Tamanyos de carta, como multiplo del de la mesa. De fabrica el segundo:
     * con el de la mesa, en 1080p cabia fila y media, y hojear es ver muchas.
     */
    private static final double[] SIZES = {0.6, 0.75, 0.95, 1.2, 1.5};
    /** Se recuerda mientras dure la sesion: volver no deberia deshacerlo. */
    private static int sizeStep = 1;
    /**
     * Si el jugador ha tocado el tamanyo. Mientras no, la rejilla baja sola un
     * escalon hasta que quepan dos filas: en 2K la carta de la mesa es tan
     * grande que con los filtros encima cabia UNA, y media pantalla vacia.
     */
    private static boolean sizeChosen;

    private final double baseWidth;

    private final TextField search = new TextField();
    private final Button rulesButton = new Button(NeoText.get("deck.rulesText"));
    private boolean rulesText;
    private final CardFilterBar filters = new CardFilterBar(this::refilter);
    private final ComboBox<SetChoice> set = new ComboBox<>();
    private final ComboBox<GameFormat> format = new ComboBox<>();
    private final ComboBox<CardLibrary.Sort> sort = new ComboBox<>();
    private final ComboBox<CardLibrary.Ownership> ownership = new ComboBox<>();
    private final ComboBox<String> questSave = new ComboBox<>();
    private final Button colourMode = new Button();
    private final Label summary = new Label();
    private final Label resultCount = new Label();
    private final VBox gridHolder = new VBox();
    private final HBox pagerSlot = new HBox();

    private FlowPane grid;
    private Pager pager;

    private CardLibrary library;
    private List<PaperCard> results = Collections.emptyList();
    /** Los nombres de la coleccion elegida, o null si aun no se ha leido. */
    private Set<String> owned;
    private int ownedFor = -1;

    /** Una fila del desplegable de expansiones; null = todas. */
    private record SetChoice(CardEdition edition) {
        @Override
        public String toString() {
            if (edition == null) {
                return NeoText.get("library.set.all");
            }
            final String year = edition.getDate() == null ? ""
                    : "  ·  " + (1900 + edition.getDate().getYear());
            return edition.getName() + "  (" + edition.getCode() + ")" + year;
        }
    }

    public CardLibraryScreen(final double cardWidth, final Runnable onBack) {
        this.baseWidth = cardWidth;
        getStyleClass().addAll("table-root", "deck-builder", "library");

        setTop(header(onBack));
        setCenter(body());
        // La ayuda a la izquierda y Volver abajo a la derecha, en la misma
        // barra: el sitio de Volver en todas las pantallas (las notas de diseño,
        // principio 12).
        final Label hint = new Label(NeoText.get("library.hint"));
        hint.getStyleClass().add("home-summary");
        hint.setMinWidth(0);
        setBottom(BackBar.of(onBack, hint));
        rebuildGrid();
        CardZoom.install(this);

        summary.setText(NeoText.get("library.loading"));
        resultCount.setText(NeoText.get("library.loading"));
        if (CardLibrary.isReady()) {
            ready(CardLibrary.get());
        } else {
            // Recorrer las 95.000 impresiones: fuera del hilo de interfaz.
            final Thread t = new Thread(() -> {
                final CardLibrary lib = CardLibrary.get();
                Platform.runLater(() -> ready(lib));
            }, "neo-library");
            t.setDaemon(true);
            t.start();
        }
    }

    // ---------------------------------------------------------------
    // Montaje
    // ---------------------------------------------------------------

    private Region header(final Runnable onBack) {
        final Label title = new Label(NeoText.get("library.title"));
        title.getStyleClass().add("builder-title");
        summary.getStyleClass().add("home-summary");

        final HBox row = new HBox(14, new VBox(2, title, summary));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("builder-header");
        row.setPadding(new Insets(16, 28, 12, 30));
        return row;
    }

    private Region body() {
        search.setPromptText(NeoText.get("deck.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(20);
        HBox.setHgrow(search, Priority.SOMETIMES);
        search.textProperty().addListener((o, was, is) -> refilter());

        // El texto de reglas, como en el constructor: "sacrifice", "flying".
        rulesButton.getStyleClass().add("segment");
        rulesButton.setMinWidth(Region.USE_PREF_SIZE);
        rulesButton.setOnAction(e -> {
            rulesText = !rulesText;
            rulesButton.pseudoClassStateChanged(SELECTED, rulesText);
            search.setPromptText(NeoText.get(rulesText ? "deck.search.rules" : "deck.search"));
            refilter();
        });

        // Color de la carta o identidad: aqui se pregunta de las dos maneras.
        colourMode.getStyleClass().add("segment");
        colourMode.setMinWidth(Region.USE_PREF_SIZE);
        colourMode.setOnAction(e -> {
            filters.setByIdentity(!filters.isByIdentity());
            updateColourMode();
        });
        updateColourMode();

        final Button smaller = sizeButton("−", -1);
        final Button bigger = sizeButton("+", 1);
        final HBox sizes = new HBox(4, caption(NeoText.get("library.size")), smaller, bigger);
        sizes.setAlignment(Pos.CENTER_LEFT);

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row1 = new HBox(10, search, rulesButton, filters.bar(), colourMode, gap, sizes);
        row1.setAlignment(Pos.CENTER_LEFT);

        // Segunda fila: lo que no tiene el constructor.
        set.setVisibleRowCount(18);
        set.setPrefWidth(UiScale.px(300));
        set.setOnAction(e -> refilter());
        final Button latest = new Button(NeoText.get("library.latest"));
        latest.getStyleClass().add("segment");
        latest.setMinWidth(Region.USE_PREF_SIZE);
        latest.setOnAction(e -> selectSet(library == null ? null : library.latestRelease()));

        format.getItems().add(null);
        for (final GameFormat f : FModel.getFormats().getSanctionedList()) {
            format.getItems().add(f);
        }
        for (final GameFormat f : FModel.getFormats().getCasualList()) {
            format.getItems().add(f);
        }
        format.setConverter(new StringConverter<>() {
            @Override
            public String toString(final GameFormat f) {
                return f == null ? NeoText.get("library.format.any") : f.getName();
            }

            @Override
            public GameFormat fromString(final String s) {
                return null;
            }
        });
        format.setButtonCell(formatCell());
        format.setCellFactory(l -> formatCell());
        format.getSelectionModel().select(0);
        format.setOnAction(e -> refilter());

        sort.getItems().addAll(CardLibrary.Sort.values());
        sort.setConverter(new StringConverter<>() {
            @Override
            public String toString(final CardLibrary.Sort s) {
                if (s == null) {
                    return "";
                }
                switch (s) {
                    case COST: return NeoText.get("sort.cost");
                    case NEWEST: return NeoText.get("sort.newest");
                    case TYPE: return NeoText.get("sort.type");
                    default: return NeoText.get("sort.name");
                }
            }

            @Override
            public CardLibrary.Sort fromString(final String s) {
                return null;
            }
        });
        sort.getSelectionModel().select(CardLibrary.Sort.NAME);
        sort.setOnAction(e -> refilter());

        final HBox row2 = new HBox(10,
                caption(NeoText.get("library.set")), set, latest,
                caption(NeoText.get("library.format")), format);
        row2.setAlignment(Pos.CENTER_LEFT);

        // La coleccion: la de la Quest, que es la unica que hay. Sin partidas
        // de Quest no se ensenya — un filtro que no puede filtrar nada es
        // un control que no hace lo que parece (principio 1).
        final List<String> saves = NeoQuest.saves();
        final HBox ownRow = new HBox(8);
        ownRow.setAlignment(Pos.CENTER_LEFT);
        if (!saves.isEmpty()) {
            ownership.getItems().addAll(CardLibrary.Ownership.values());
            ownership.setConverter(new StringConverter<>() {
                @Override
                public String toString(final CardLibrary.Ownership o) {
                    if (o == null) {
                        return "";
                    }
                    switch (o) {
                        case OWNED: return NeoText.get("library.own.owned");
                        case MISSING: return NeoText.get("library.own.missing");
                        default: return NeoText.get("library.own.all");
                    }
                }

                @Override
                public CardLibrary.Ownership fromString(final String s) {
                    return null;
                }
            });
            ownership.getSelectionModel().select(CardLibrary.Ownership.ALL);
            ownership.setOnAction(e -> refilter());
            questSave.getItems().addAll(saves);
            final String current = NeoQuest.isActive() ? NeoQuest.name() : null;
            questSave.getSelectionModel().select(
                    current != null && saves.contains(current) ? current : saves.get(0));
            questSave.setOnAction(e -> {
                owned = null;
                refilter();
            });
            // De que Quest, solo cuenta si se filtra por coleccion.
            questSave.disableProperty().bind(ownership.valueProperty()
                    .isEqualTo(CardLibrary.Ownership.ALL));
            ownRow.getChildren().addAll(caption(NeoText.get("library.own")), ownership);
            // El nombre de la Quest solo si hay que elegir entre varias.
            if (saves.size() > 1) {
                ownRow.getChildren().add(questSave);
            }
        }

        final Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);
        final HBox row2b = new HBox(10, ownRow, gap2,
                caption(NeoText.get("sort.caption")), sort, resultCount);
        row2b.setAlignment(Pos.CENTER_LEFT);
        resultCount.getStyleClass().add("caption");

        final FlowPane rows = new FlowPane(24, 8, row2, row2b);
        rows.setAlignment(Pos.CENTER_LEFT);

        VBox.setVgrow(gridHolder, Priority.ALWAYS);

        pagerSlot.setAlignment(Pos.CENTER_LEFT);
        pagerSlot.setMinWidth(Region.USE_PREF_SIZE);

        final VBox content = new VBox(10, row1, filters.advanced(), rows, gridHolder, pagerSlot);
        content.getStyleClass().add("builder-catalogue");
        content.setPadding(new Insets(12, 16, 10, 16));
        final StackPane frame = new StackPane(content);
        frame.setPadding(new Insets(14, 24, 16, 24));
        return frame;
    }

    private static ListCell<GameFormat> formatCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(final GameFormat f, final boolean empty) {
                super.updateItem(f, empty);
                setText(empty ? null
                        : f == null ? NeoText.get("library.format.any") : f.getName());
            }
        };
    }

    /**
     * La rejilla y su paginador, otra vez.
     *
     * <p>Cambiar el tamanyo de carta cambia cuantas caben, y {@code Pager.fitTo}
     * se queda con el ancho de casilla que se le dio: lo limpio es montarla
     * de nuevo, conservando la carta que estaba primera a la vista.
     */
    private void rebuildGrid() {
        final int first = pager == null ? 0 : pager.from();
        grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // Sin barra vertical: la pagina es lo que cabe (Pager.fitTo), y si una
        // fila no entra entera el Pager la quita. Con barra, el Pager tiene que
        // reservarle sitio y cuenta una columna menos de las que se pintan.
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        pager = new Pager(40, this::paint);
        pager.setScrollbarReserve(0);
        pager.getStyleClass().add("builder-pagebar");
        final double w = cardWidth();
        // El ancho con el padding de la casilla (medido: carta + 6, y dos de
        // holgura). No puede quedarse corto: contaria una columna de mas, la
        // pagina desbordaria y el Pager le quitaria una fila para siempre. Si
        // aun asi mide mas, el Pager se queda con lo medido.
        final double tileH = w * 7 / 5 + 8;
        pager.fitTo(scroll, grid, w + 8, tileH, 6);
        if (!sizeChosen && sizeStep > 0) {
            scroll.viewportBoundsProperty().addListener(new javafx.beans.value.ChangeListener<>() {
                @Override
                public void changed(final javafx.beans.value.ObservableValue<? extends javafx.geometry.Bounds> o,
                                    final javafx.geometry.Bounds was, final javafx.geometry.Bounds is) {
                    if (is.getHeight() <= 0) {
                        return;
                    }
                    o.removeListener(this);
                    if (!sizeChosen && sizeStep > 0 && is.getHeight() < 2 * tileH + 10) {
                        sizeStep--;
                        Platform.runLater(CardLibraryScreen.this::rebuildGrid);
                    }
                }
            });
        }
        gridHolder.getChildren().setAll(scroll);
        pagerSlot.getChildren().setAll(pager);
        pager.setTotal(results.size());
        // Volver a la pagina donde estaba esa carta (goToItem ya repinta).
        pager.goToItem(first);
        paint();
    }

    private double cardWidth() {
        return baseWidth * SIZES[sizeStep];
    }

    private Button sizeButton(final String text, final int delta) {
        final Button b = new Button(text);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            final int next = Math.max(0, Math.min(SIZES.length - 1, sizeStep + delta));
            sizeChosen = true;
            if (next != sizeStep) {
                sizeStep = next;
                rebuildGrid();
            }
        });
        return b;
    }

    private void updateColourMode() {
        colourMode.setText(NeoText.get(filters.isByIdentity()
                ? "library.colour.identity" : "library.colour.card"));
    }

    // ---------------------------------------------------------------
    // Datos
    // ---------------------------------------------------------------

    /** La enciclopedia ya esta: se rellenan las expansiones y se busca. */
    private void ready(final CardLibrary lib) {
        library = lib;
        set.getItems().add(new SetChoice(null));
        for (final CardEdition ed : lib.editions()) {
            set.getItems().add(new SetChoice(ed));
        }
        set.getSelectionModel().select(0);
        summary.setText(NeoText.get("library.summary", lib.size(), lib.editions().size()));
        applyStartupFlags();
        refilter();
    }

    private void selectSet(final CardEdition edition) {
        for (final SetChoice c : set.getItems()) {
            if (c.edition() == edition) {
                set.getSelectionModel().select(c);
                return;
            }
        }
    }

    /**
     * Solo pruebas: {@code -Dneo.library.query=elf}, {@code .set=DSK},
     * {@code .format=Modern}, {@code .sort=NEWEST}, {@code .own=OWNED},
     * {@code .latest=true}. Para capturar un estado sin ratón.
     */
    private void applyStartupFlags() {
        final String q = System.getProperty("neo.library.query");
        if (q != null) {
            search.setText(q);
        }
        final String code = System.getProperty("neo.library.set");
        if (code != null) {
            selectSet(FModel.getMagicDb().getEditions().get(code));
        }
        if (Boolean.getBoolean("neo.library.latest")) {
            selectSet(library.latestRelease());
        }
        final String f = System.getProperty("neo.library.format");
        if (f != null) {
            format.getSelectionModel().select(FModel.getFormats().getFormat(f));
        }
        final String s = System.getProperty("neo.library.sort");
        if (s != null) {
            try {
                sort.getSelectionModel().select(CardLibrary.Sort.valueOf(s));
            } catch (final IllegalArgumentException ignored) {
                // una bandera mal escrita deja el orden de siempre
            }
        }
        final String o = System.getProperty("neo.library.own");
        if (o != null && !ownership.getItems().isEmpty()) {
            try {
                ownership.getSelectionModel().select(CardLibrary.Ownership.valueOf(o));
            } catch (final IllegalArgumentException ignored) {
                // idem
            }
        }
    }

    private void refilter() {
        if (library == null) {
            return;
        }
        final CardLibrary.Ownership own = ownership.getValue() == null
                ? CardLibrary.Ownership.ALL : ownership.getValue();
        if (own != CardLibrary.Ownership.ALL && owned == null) {
            loadOwned();
            return;
        }

        final CardLibrary.Query q = new CardLibrary.Query();
        q.text = search.getText();
        q.rulesText = rulesText;
        q.extra = filters.isActive() ? filters.predicate() : null;
        final SetChoice sc = set.getValue();
        q.setCode = sc == null || sc.edition() == null ? null : sc.edition().getCode();
        q.format = format.getValue();
        q.sort = sort.getValue();
        q.ownership = own;
        q.owned = owned;
        results = library.find(q);

        pager.reset();
        pager.setTotal(results.size());
        paint();
        resultCount.setText(results.size() == library.size()
                ? NeoText.get("count.cards", results.size())
                : NeoText.get("collection.someOf", results.size(), library.size()));
    }

    /**
     * Lee la coleccion de la Quest elegida.
     *
     * <p>La que esta en curso se tiene ya en memoria. Otra hay que leerla del
     * disco, y eso no se hace en el hilo de interfaz: una partida de Quest
     * larga pesa lo suyo. Se lee SIN cargarla ({@link NeoQuest#peek}), asi
     * que mirar no cambia cual es la aventura en curso.
     */
    private void loadOwned() {
        final String name = questSave.getValue();
        final int ticket = ++ownedFor;
        resultCount.setText(NeoText.get("library.loading"));
        if (name == null) {
            owned = Collections.emptySet();
            refilter();
            return;
        }
        if (NeoQuest.isActive() && name.equals(NeoQuest.name())) {
            owned = CardLibrary.namesOf(NeoQuest.collection());
            refilter();
            return;
        }
        final Thread t = new Thread(() -> {
            final forge.gamemodes.quest.data.QuestData data = NeoQuest.peek(name);
            final Set<String> names = data == null || data.getAssets() == null
                    ? Collections.emptySet()
                    : CardLibrary.namesOf(data.getAssets().getCardPool());
            Platform.runLater(() -> {
                // Si entretanto se eligio otra, esta lectura ya no vale.
                if (ticket == ownedFor && getScene() != null) {
                    owned = names;
                    refilter();
                }
            });
        }, "neo-library-quest");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------
    // Pintar
    // ---------------------------------------------------------------

    /** Solo la pagina actual: pintar 33.000 cartas dejaria la app pegada. */
    private void paint() {
        final List<Region> tiles = new ArrayList<>();
        for (int i = pager.from(); i < Math.min(results.size(), pager.to()); i++) {
            tiles.add(tile(i));
        }
        grid.getChildren().setAll(tiles);
    }

    private Region tile(final int index) {
        final CardNode node = new CardNode(cardWidth());
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(results.get(index)));

        final StackPane box = new StackPane(node);
        box.getStyleClass().add("catalogue-tile");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // Clic = grande, dentro de TODOS los resultados: la rueda y las flechas
        // pasan a la siguiente, tambien de otra pagina. Asi se hojea.
        box.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                final List<PaperCard> list = results;
                CardZoom.show(node, list.size(), index,
                        i -> CardView.getCardForUi(list.get(i)));
            }
        });
        return box;
    }

    private static Label caption(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("caption");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
