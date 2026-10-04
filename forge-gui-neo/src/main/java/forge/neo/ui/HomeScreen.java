package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import forge.deck.Deck;
import forge.item.PaperCard;
import forge.neo.NeoSettings;
import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Pantalla de inicio: elegir mazo y empezar a jugar.
 *
 * <p>Copia la idea de Arena: <b>el mazo se elige mirando, no leyendo</b>. Cada
 * mazo se presenta con la carta de su comandante a buen tamano, que es como
 * todo el mundo lo recuerda ("el de Sephiroth"), en vez de con una lista de
 * nombres. El resto de ajustes son una sola fila abajo, porque se tocan una vez
 * y ya.
 *
 * <p>Lo que se elige aqui se guarda en {@link NeoSettings}, asi que la proxima
 * vez la pantalla se abre tal y como la dejaste y solo hay que dar a JUGAR.
 */
public class HomeScreen extends javafx.scene.layout.StackPane {

    /** Perfiles de IA que trae Forge en {@code forge-gui/res/ai/}. */
    /**
     * Las formas de jugar de Forge y "Al azar" (de un pozo que se elige en
     * Personalizar -> Rivales). Cada rival puede llevar ademas la suya: ver
     * {@link forge.neo.look.RivalSetup}.
     */
    private static final String[] AI_PROFILES = {"Cautious", "Default", "Reckless", "Experimental",
            forge.neo.look.RivalSetup.RANDOM};

    /** Que hacer al pulsar JUGAR. */
    public interface StartHandler {
        /**
         * @param opponentDecks un mazo por rival; un hueco a null significa
         *                      "el que sea", y lo elige la propia pantalla
         * @param teams         el equipo de cada asiento, el tuyo primero, o
         *                      null si es todos contra todos (ver NeoTeams)
         */
        void start(Deck deck, int opponents, String aiProfile, boolean watch,
                   List<Deck> opponentDecks, int[] teams);
    }

    /**
     * Abrir el deck builder.
     *
     * <p>{@code deck} viene a null cuando se quiere empezar uno de cero. Es la
     * misma pantalla en los dos casos: montar y retocar son lo mismo.
     */
    public interface EditHandler {
        /**
         * @param where donde se guarda: el formato, o la coleccion en la que
         *              vive el mazo (o la pestanya abierta, si es nuevo)
         */
        void edit(Deck deck, forge.neo.deck.DeckContext where);
    }

    private final List<Deck> decks;
    private final List<DeckTile> tiles = new ArrayList<>();
    private final double tileWidth;

    /**
     * Los mazos partidos en dos: los tuyos y los que trae Forge.
     *
     * <p>Con los preconstruidos dentro son cientos, y tus dos mazos quedaban
     * ahogados en medio. Son dos cosas distintas — lo que has montado tu y el
     * catalogo que viene de serie — y se buscan de forma distinta.
     */
    private final List<Deck> mine = new ArrayList<>();
    private final List<Deck> stock = new ArrayList<>();

    /**
     * Y los que te has bajado de internet.
     *
     * <p>Van en su propia pestanya y no mezclados con los tuyos: no los has
     * montado tu, viven en otra carpeta ({@code decks
et}) y no se tocan.
     * Para quedarte con uno, se abre en el constructor y se guarda con tu
     * nombre — que es cuando pasa a ser tuyo de verdad.
     */
    private final List<Deck> net = new ArrayList<>();

    /**
     * Y las colecciones que te hayas montado: "Sets de 2010", "Estandar de
     * ahora"... Cada una es una carpeta dentro de la del formato (ver
     * {@link forge.neo.deck.DeckCollections}) y va en su propia pestanya,
     * entre "Mis mazos" y los de Forge. Pedido en itch.io el 27-09-2026.
     *
     * <p>"Mis mazos" son los que estan sueltos, fuera de toda coleccion: un
     * mazo esta en un sitio o en otro, nunca en los dos.
     */
    private final java.util.Map<String, List<Deck>> collections = new java.util.TreeMap<>(
            String.CASE_INSENSITIVE_ORDER);

    /** Que pestanya se esta mirando. */
    private enum Tab { MINE, STOCK, NET, COLLECTION }

    private Tab showing = Tab.MINE;

    /** Con {@code showing == COLLECTION}, cual. */
    private String collection;

    /**
     * La ultima pestanya que se miro, mientras dure la sesion.
     *
     * <p>Al volver del constructor la pantalla se rehace entera, y abrirla
     * por la pestanya del ultimo mazo elegido te sacaba de la coleccion en la
     * que estabas — justo despues de montar un mazo nuevo en ella.
     */
    private static forge.neo.match.NeoFormat lastViewFormat;
    private static Tab lastViewTab;
    private static String lastViewCollection;

    /** Cuantos mazos por pagina. Con cientos de golpe, la rejilla se arrastra. */
    private static final int MAX_TILES = 60;

    private final javafx.scene.control.TextField search = new javafx.scene.control.TextField();
    private final FlowPane grid = new FlowPane(18, 18);
    private final Label gridCount = new Label();
    private final java.util.EnumMap<Tab, Button> tabButtons = new java.util.EnumMap<>(Tab.class);
    private final java.util.Map<String, Button> collectionButtons = new java.util.LinkedHashMap<>();
    /** La fila de pestanyas. Se parte en dos lineas si no cabe (principio 5). */
    private final FlowPane tabRow = new FlowPane(8, 6);
    private final Pager pager = new Pager(MAX_TILES, this::fillGrid);

    /**
     * La capa de dialogos de esta pantalla.
     *
     * <p>El selector del mazo de rival se levanta AQUI y no cambiando la raiz de
     * la escena. Cambiarla metia esta pantalla dentro de otro panel, y al volver
     * JavaFX no acepta como raiz un nodo que ya tiene padre: se quedaba colgado
     * al dar a Aceptar.
     */
    private final Overlay overlay = new Overlay();

    private Deck selected;

    /**
     * TU MAZO AL AZAR (itch.io, 04-10-2026: <i>"Could you add a 'Random Deck From
     * My List' when booting up a commander vs ai game? I meant for myself"</i>).
     * Los rivales ya podian ir al azar; tu no.
     *
     * <p>De que lista se sortea, o null si has elegido un mazo:
     * {@code "mine"}, {@code "stock"}, {@code "net"} o {@code "c:<coleccion>"} —
     * la pestanya que estabas mirando al pulsar "Al azar". Con esto puesto
     * {@link #selected} es null. Se sortea al EMPEZAR ({@link #launch}), solo
     * entre los legales, y se recuerda por formato ({@link #randomKey}).
     */
    private String randomFrom;
    private final Button randomMine = new Button(NeoText.get("home.random.mine"));
    private int opponents;
    private String aiProfile;

    /**
     * Con que juega cada rival. Un null = al azar.
     *
     * <p>Antes los tres rivales jugaban tu mismo mazo, o sea que una partida a
     * cuatro era contra tres copias de ti. Con los preconstruidos de Forge
     * disponibles, cada uno puede llevar algo distinto.
     */
    private final List<Deck> opponentDecks = new ArrayList<>();

    /**
     * Los rivales que van "al azar de una coleccion": asiento -> coleccion. Un
     * asiento aqui tiene {@code null} en {@link #opponentDecks} y se sortea al
     * empezar DENTRO de esa coleccion (ver {@link #resolvedOpponentDecks}).
     */
    private final java.util.Map<Integer, String> opponentPools = new java.util.HashMap<>();

    /**
     * El comandante que lleva cada rival con mazo fijado, si no es el de
     * siempre (Forge #12052, 29-09-2026). Se borra al cambiarle el mazo.
     */
    private final java.util.Map<Integer, List<PaperCard>> opponentCommanders = new java.util.HashMap<>();

    /**
     * "Comandante: X" de tu mazo, en formatos con comandante; null en el resto.
     * Solo se habilita si el mazo tiene a quien elegir, y eso lo calcula el
     * motor en otro hilo (ver CommanderPickDialog) y se guarda por mazo.
     */
    private Button commanderButton;
    private final java.util.Map<Deck, Boolean> commanderChoices = new java.util.IdentityHashMap<>();
    private final List<Button> opponentButtons = new ArrayList<>();
    private Region opponentRow;

    /**
     * El equipo de cada asiento: el 0 eres tu, el {@code i + 1} el rival
     * {@code i}. Ver {@link forge.neo.match.NeoTeams}.
     *
     * <p>Como en el lobby de Forge: un desplegable "Equipo" <b>por jugador,
     * el tuyo incluido</b>, junto a lo demas de ese asiento, y de fabrica cada
     * uno en el suyo — todos contra todos, la partida de siempre.
     */
    private int[] teams = new int[0];
    private final List<javafx.scene.control.ComboBox<Integer>> teamBoxes = new ArrayList<>();
    private Label opponentCaption;

    private final Label summary = new Label();
    private final Button play = new Button(NeoText.get("home.play"));
    private final Button edit = new Button(NeoText.get("home.edit"));

    private final forge.neo.match.NeoFormat format;
    private final Runnable onBack;
    private final EditHandler onEdit;
    private final Runnable onDownload;

    public HomeScreen(final forge.neo.match.NeoFormat format, final List<Deck> decks,
                      final double tileWidth, final StartHandler onStart, final Runnable onBack,
                      final EditHandler onEdit, final Runnable onDownload) {
        this.format = format;
        this.decks = decks;
        this.tileWidth = tileWidth;
        this.onBack = onBack;
        this.onEdit = onEdit;
        this.onDownload = onDownload;
        this.opponents = clamp(
                NeoSettings.opponents(format), 1, 3);
        this.aiProfile = NeoSettings.get(NeoSettings.AI_PROFILE, "Default");
        this.teams = forge.neo.match.NeoTeams.fromSetting(
                NeoSettings.teams(format), opponents);

        for (final Deck d : decks) {
            if (format.isMine(d)) {
                mine.add(d);
            } else {
                stock.add(d);
            }
        }
        // Los bajados de internet se leen del disco, sin tocar la red.
        if (forge.neo.deck.NetDecks.isSupported(format.getGameType())) {
            net.addAll(forge.neo.deck.NetDecks.cached(format.getGameType()));
        }
        // Las colecciones, despues del reparto: format.isMine solo mira la
        // carpeta de arriba, y estos no estan ahi. Entran tambien en `decks`
        // para que cuenten como rivales al azar y para volver a encontrarlos.
        if (forge.neo.deck.DeckCollections.isSupported(format)) {
            for (final String name : forge.neo.deck.DeckCollections.list(format)) {
                final List<Deck> in = forge.neo.deck.DeckCollections.decks(format, name);
                collections.put(name, new ArrayList<>(in));
                decks.addAll(in);
            }
        }
        // Si no tienes ninguno propio, se abre por el catalogo de Forge: una
        // pestanya vacia como primera impresion no ayuda a nadie.
        showing = mine.isEmpty() && collections.isEmpty() ? Tab.STOCK : Tab.MINE;

        getStyleClass().addAll("table-root", "home");

        final BorderPane layout = new BorderPane();
        layout.setTop(header());
        layout.setCenter(deckGrid());
        layout.setBottom(footer(onStart));
        getChildren().addAll(layout, overlay);

        // Se abre por donde lo dejaste la ultima vez.
        final String last = NeoSettings.get(NeoSettings.DECK, null);
        Deck initial = null;
        for (final Deck d : decks) {
            if (d.getName().equals(last)) {
                initial = d;
                break;
            }
        }
        if (initial == null) {
            for (final Deck d : net) {
                if (d.getName().equals(last)) {
                    initial = d;
                    break;
                }
            }
        }
        // Para poder capturar una pestanya concreta sin tocar el raton.
        final int forced = Integer.getInteger("neo.home.tab", -1);
        if (forced >= 0 && forced < Tab.values().length
                && tabButtons.containsKey(Tab.values()[forced])) {
            showing = Tab.values()[forced];
            markTabs();
            fillGrid();
            select(source().isEmpty() ? null : source().get(0));
            maybeShowDeleteTest();
            return;
        }
        // Para capturar una coleccion concreta: -Dneo.home.collection=Nombre
        final String forcedCollection = System.getProperty("neo.home.collection");
        if (forcedCollection != null && collections.containsKey(forcedCollection)) {
            showCollection(forcedCollection);
            select(source().isEmpty() ? null : source().get(0));
            return;
        }
        if (lastViewFormat == format && lastViewTab != null
                && (lastViewTab == Tab.COLLECTION
                        ? collections.containsKey(lastViewCollection)
                        : tabButtons.containsKey(lastViewTab))) {
            // Se vuelve a la pestanya que estabas mirando en esta sesion.
            showing = lastViewTab;
            collection = lastViewTab == Tab.COLLECTION ? lastViewCollection : null;
            markTabs();
            fillGrid();
            if (initial == null || !source().contains(initial)) {
                initial = source().isEmpty() ? null : source().get(0);
            }
        } else if (initial != null) {
            // Se abre por la pestanya donde de verdad esta ese mazo.
            showing = tabOf(initial);
            collection = collectionOf(initial);
            markTabs();
            fillGrid();
        } else if (!decks.isEmpty()) {
            initial = source().isEmpty() ? decks.get(0) : source().get(0);
        }
        select(initial);
        restoreRandom();
        maybeShowDeleteTest();
        // -Dneo.home.playAt=N: pulsa JUGAR a los N ms, por el boton de verdad
        // (asi se prueba el mazo al azar de punta a punta sin raton).
        final int playAt = Integer.getInteger("neo.home.playAt", -1);
        if (playAt >= 0) {
            final javafx.animation.PauseTransition p =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(playAt));
            p.setOnFinished(e -> play.fire());
            p.play();
        }
    }

    // ---------------------------------------------------------------
    //  Tu mazo al azar
    // ---------------------------------------------------------------

    /** Donde se recuerda, uno por formato: jugar al azar en Commander no es jugarlo en Estandar. */
    private String randomKey() {
        return "deckRandom." + format.name();
    }

    /** La lista que se esta mirando, como la guarda {@link #randomFrom}. */
    private String currentListKey() {
        switch (showing) {
            case STOCK: return "stock";
            case NET: return "net";
            case COLLECTION: return collection == null ? "mine" : "c:" + collection;
            default: return "mine";
        }
    }

    /** Los mazos de esa lista; vacia si ya no existe (una coleccion borrada). */
    private List<Deck> randomPool() {
        if (randomFrom == null) {
            return java.util.Collections.emptyList();
        }
        if (randomFrom.startsWith("c:")) {
            final List<Deck> in = collections.get(randomFrom.substring(2));
            return in == null ? java.util.Collections.emptyList() : in;
        }
        switch (randomFrom) {
            case "stock": return stock;
            case "net": return net;
            default: return mine;
        }
    }

    /** Como se llama esa lista en la linea de estado. */
    private String randomPoolName() {
        if (randomFrom == null) {
            return "";
        }
        if (randomFrom.startsWith("c:")) {
            return randomFrom.substring(2);
        }
        switch (randomFrom) {
            case "stock": return NeoText.get("home.randomPool.stock");
            case "net": return NeoText.get("home.randomPool.net");
            default: return NeoText.get("home.randomPool.mine");
        }
    }

    /** "Al azar": a partir de ahora tu mazo sale de la lista que estas viendo. */
    private void chooseRandom() {
        if (source().isEmpty()) {
            return;
        }
        final String key = currentListKey();
        select(null);
        randomFrom = key;
        NeoSettings.set(randomKey(), key);
        NeoSettings.save();
        markRandom();
        updateSummary();
    }

    /** Si la ultima vez fuiste al azar en este formato, sigues al azar. */
    private void restoreRandom() {
        if (Integer.getInteger("neo.home.tab", -1) >= 0
                || System.getProperty("neo.home.collection") != null) {
            return;
        }
        final String saved = NeoSettings.get(randomKey(), "");
        if (saved == null || saved.isBlank()) {
            return;
        }
        randomFrom = saved;
        if (randomPool().isEmpty()) {
            // La lista ya no tiene mazos (o la coleccion no existe): se vuelve
            // a lo de siempre en vez de dejar JUGAR apagado sin motivo.
            randomFrom = null;
            return;
        }
        select(null);
        randomFrom = saved;
        markRandom();
        updateSummary();
    }

    private void markRandom() {
        randomMine.pseudoClassStateChanged(SELECTED, randomFrom != null);
    }

    /**
     * El sorteo: uno de la lista que se pueda jugar en este formato, con su
     * comandante elegido si lo tiene. null si no hay ninguno legal.
     */
    private Deck drawRandom() {
        final List<Deck> pool = new ArrayList<>(randomPool());
        java.util.Collections.shuffle(pool);
        final forge.deck.DeckFormat df = format.getGameType().getDeckFormat();
        for (final Deck d : pool) {
            final Deck playable = forge.neo.deck.CommanderChoice.applies(format.getGameType())
                    ? forge.neo.deck.CommanderChoice.apply(d,
                            forge.neo.deck.CommanderChoice.remembered(d, df))
                    : d;
            if (df.getDeckConformanceProblem(playable) == null) {
                return playable;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------

    private Region header() {
        final Label title = new Label(format.getLabel().toUpperCase(java.util.Locale.ROOT));
        title.getStyleClass().add("home-title");

        final Label subtitle = new Label(decks.isEmpty()
                ? NeoText.get("home.subtitle.empty")
                : NeoText.get("home.subtitle"));
        subtitle.getStyleClass().add("home-subtitle");

        final VBox titles = new VBox(2, title, subtitle);
        titles.setAlignment(Pos.CENTER);

        // Gestionar mazos va ARRIBA y jugar ABAJO. Son dos cosas distintas, y
        // ademas en una pantalla de 1280 de ancho no caben en la misma fila:
        // metidos en el pie salian todos los botones cortados.
        unshrinkable(newDeck).getStyleClass().add("btn-secondary");
        newDeck.setOnAction(e -> onEdit.edit(null, contextFor(null)));

        unshrinkable(edit).getStyleClass().add("btn-secondary");
        edit.setOnAction(e -> {
            if (selected != null) {
                onEdit.edit(selected, contextFor(selected));
            }
        });

        final HBox tools = new HBox(8, newDeck, edit);
        tools.setAlignment(Pos.CENTER_RIGHT);

        // Bajar mazos hechos. Solo para los formatos de los que Forge mantiene
        // listas, y siempre despues de "Nuevo" y "Editar": montar el tuyo es lo
        // principal y esto es el atajo para cuando no te apetece.
        if (onDownload != null && forge.neo.deck.NetDecks.isSupported(format.getGameType())) {
            final Button download = new Button(NeoText.get("home.download"));
            unshrinkable(download).getStyleClass().add("btn-secondary");
            download.setOnAction(e -> onDownload.run());
            tools.getChildren().add(download);
        }

        final javafx.scene.layout.BorderPane bar = new javafx.scene.layout.BorderPane();
        bar.setCenter(titles);
        bar.setRight(tools);
        javafx.scene.layout.BorderPane.setAlignment(tools, Pos.CENTER_RIGHT);
        bar.setPadding(new Insets(20, 26, 14, 26));
        return bar;
    }

    private final Button newDeck = new Button(NeoText.get("home.newDeck"));

    private Region deckGrid() {
        grid.setAlignment(Pos.CENTER);
        grid.setPadding(new Insets(10, 30, 10, 30));

        tabButtons.put(Tab.MINE, tab(NeoText.get("home.tab.mine", mine.size()), Tab.MINE));
        tabButtons.put(Tab.STOCK, tab(NeoText.get("home.tab.stock", stock.size()), Tab.STOCK));
        // La pestanya de internet solo aparece si te has bajado algo. Una
        // pestanya con "0" invita a clicar para no encontrar nada; el sitio de
        // bajarlos es el boton de arriba, que dice lo que hace.
        if (!net.isEmpty()) {
            tabButtons.put(Tab.NET, tab(NeoText.get("home.tab.net", net.size()), Tab.NET));
        }
        rebuildTabRow();

        // "Buscar mazo o comandante" solo donde hay comandantes. En Estandar,
        // en draft y en sellado no los hay, y nombrarlos ahi es prometer algo
        // que el formato no tiene.
        search.setPromptText(NeoText.get(format.isCommanderStyle()
                ? "home.search" : "home.search.deck"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(18);
        search.textProperty().addListener((o, a, b) -> {
            pager.reset();
            fillGrid();
        });

        gridCount.getStyleClass().add("dialog-counter");

        // Las pestanyas a la izquierda, partiendose en dos lineas si hay muchas
        // colecciones; paginador y buscador a la derecha, siempre enteros.
        tabRow.setAlignment(Pos.CENTER_LEFT);
        tabRow.setMinWidth(0);
        // AL AZAR: tu mazo, sorteado de la lista que estas viendo. Va junto al
        // buscador porque es lo mismo: actua sobre lo que se ve.
        randomMine.getStyleClass().add("segment");
        randomMine.setId("home-random-mine");
        randomMine.setMinWidth(Region.USE_PREF_SIZE);
        randomMine.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("home.random.mine.tip")));
        randomMine.setOnAction(e -> chooseRandom());
        final HBox right = new HBox(8, pager, randomMine, search, gridCount);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(Region.USE_PREF_SIZE);
        final BorderPane bar = new BorderPane();
        bar.setCenter(tabRow);
        bar.setRight(right);
        BorderPane.setAlignment(tabRow, Pos.CENTER_LEFT);
        BorderPane.setAlignment(right, Pos.TOP_RIGHT);
        BorderPane.setMargin(right, new Insets(0, 0, 0, 12));
        bar.setPadding(new Insets(0, 30, 8, 30));

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        fillGrid();

        final VBox box = new VBox(6, bar, scroll);
        return box;
    }

    /** Una pestanya: mis mazos / los de Forge / los de internet. */
    private Button tab(final String label, final Tab which) {
        final Button b = new Button(label);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.pseudoClassStateChanged(SELECTED, showing == which);
        b.setOnAction(e -> {
            showing = which;
            collection = null;
            markTabs();
            pager.reset();
            fillGrid();
        });
        return b;
    }

    /**
     * Rehace la fila de pestanyas: Mis mazos, las colecciones, el "+", los de
     * Forge y los de internet.
     *
     * <p>Las colecciones van pegadas a "Mis mazos" porque son eso mismo —
     * mazos tuyos — repartidos en cajones.
     */
    private void rebuildTabRow() {
        collectionButtons.clear();
        tabRow.getChildren().clear();
        tabRow.getChildren().add(tabButtons.get(Tab.MINE));
        for (final java.util.Map.Entry<String, List<Deck>> e : collections.entrySet()) {
            final Button b = collectionTab(e.getKey(), e.getValue().size());
            collectionButtons.put(e.getKey(), b);
            tabRow.getChildren().add(b);
        }
        if (forge.neo.deck.DeckCollections.isSupported(format)) {
            final Button add = new Button("+");
            add.getStyleClass().add("segment");
            add.setMinWidth(Region.USE_PREF_SIZE);
            add.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("home.collection.new")));
            add.setAccessibleText(NeoText.get("home.collection.new"));
            add.setOnAction(e -> askNewCollection(null));
            tabRow.getChildren().add(add);
        }
        tabRow.getChildren().add(tabButtons.get(Tab.STOCK));
        if (tabButtons.containsKey(Tab.NET)) {
            tabRow.getChildren().add(tabButtons.get(Tab.NET));
        }
        markTabs();
    }

    /**
     * La pestanya de una coleccion. Clic la abre; clic derecho, renombrar o
     * borrar — y el tooltip lo dice, porque un clic derecho no se adivina.
     */
    private Button collectionTab(final String name, final int count) {
        final Button b = new Button(NeoText.get("home.tab.collection", name, count));
        b.getStyleClass().add("segment");
        b.setMnemonicParsing(false);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("home.collection.tabHint")));
        b.setOnAction(e -> showCollection(name));
        b.setOnContextMenuRequested(e -> {
            final javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
            menu.getStyleClass().add("card-menu");
            final javafx.scene.control.MenuItem rename =
                    new javafx.scene.control.MenuItem(NeoText.get("home.collection.rename"));
            rename.setOnAction(a -> askRenameCollection(name));
            final javafx.scene.control.MenuItem delete =
                    new javafx.scene.control.MenuItem(NeoText.get("home.collection.delete"));
            delete.setOnAction(a -> confirmDeleteCollection(name));
            menu.getItems().addAll(rename, delete);
            menu.show(b, e.getScreenX(), e.getScreenY());
            e.consume();
        });
        return b;
    }

    private void showCollection(final String name) {
        showing = Tab.COLLECTION;
        collection = name;
        markTabs();
        pager.reset();
        fillGrid();
    }

    /** Los mazos de la pestanya que se esta mirando. */
    private List<Deck> source() {
        switch (showing) {
            case STOCK: return stock;
            case NET: return net;
            case COLLECTION:
                final List<Deck> in = collection == null ? null : collections.get(collection);
                return in == null ? mine : in;
            default: return mine;
        }
    }

    /** En que pestanya vive este mazo. */
    private Tab tabOf(final Deck deck) {
        if (collectionOf(deck) != null) {
            return Tab.COLLECTION;
        }
        if (format.isMine(deck)) {
            return Tab.MINE;
        }
        return net.stream().anyMatch(d -> d == deck) ? Tab.NET : Tab.STOCK;
    }

    /**
     * La coleccion en la que esta este mazo, o null si no esta en ninguna.
     *
     * <p>Por objeto, no por nombre: "Mono rojo" puede estar a la vez suelto
     * y en una coleccion, y son dos mazos distintos.
     */
    private String collectionOf(final Deck deck) {
        for (final java.util.Map.Entry<String, List<Deck>> e : collections.entrySet()) {
            for (final Deck d : e.getValue()) {
                if (d == deck) {
                    return e.getKey();
                }
            }
        }
        return null;
    }

    /** Si el mazo es tuyo: suelto en "Mis mazos" o dentro de una coleccion. */
    private boolean isOwn(final Deck deck) {
        return collectionOf(deck) != null || mine.stream().anyMatch(d -> d == deck);
    }

    /**
     * Donde guarda el constructor: la coleccion del mazo, o — si es nuevo —
     * la que tengas abierta. Un preconstruido de Forge o uno de internet se
     * guardan en "Mis mazos", como siempre.
     */
    private forge.neo.deck.DeckContext contextFor(final Deck deck) {
        final String in = deck == null
                ? (showing == Tab.COLLECTION ? collection : null)
                : collectionOf(deck);
        return in == null ? format : new forge.neo.deck.CollectionContext(format, in);
    }

    private void markTabs() {
        for (final java.util.Map.Entry<Tab, Button> e : tabButtons.entrySet()) {
            e.getValue().pseudoClassStateChanged(SELECTED, e.getKey() == showing);
        }
        for (final java.util.Map.Entry<String, Button> e : collectionButtons.entrySet()) {
            e.getValue().pseudoClassStateChanged(SELECTED,
                    showing == Tab.COLLECTION && e.getKey().equalsIgnoreCase(collection));
        }
        lastViewFormat = format;
        lastViewTab = showing;
        lastViewCollection = collection;
    }

    // ---------------------------------------------------------------
    // Colecciones

    /**
     * El menu de "Mover": a "Mis mazos", a cada coleccion y a una nueva.
     *
     * <p>Si el mazo no es tuyo el menu dice <b>Copiar</b>, porque es lo que
     * pasa: el preconstruido sigue en "Los de Forge" y en la coleccion queda
     * una copia tuya.
     */
    private void showMoveMenu(final Deck deck, final javafx.scene.Node anchor) {
        final boolean own = isOwn(deck);
        final String here = collectionOf(deck);
        final javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu();
        menu.getStyleClass().add("card-menu");

        final javafx.scene.control.MenuItem title = new javafx.scene.control.MenuItem(
                NeoText.get(own ? "home.collection.moveTo" : "home.collection.copyTo"));
        title.setDisable(true);
        menu.getItems().add(title);

        if (here != null || !own) {
            final javafx.scene.control.MenuItem toMine = new javafx.scene.control.MenuItem(
                    NeoText.get("home.collection.toMine"));
            toMine.setOnAction(e -> moveDeck(deck, null));
            menu.getItems().add(toMine);
        }
        for (final String name : collections.keySet()) {
            if (name.equalsIgnoreCase(here)) {
                continue;
            }
            final javafx.scene.control.MenuItem item = new javafx.scene.control.MenuItem(name);
            item.setMnemonicParsing(false);
            item.setOnAction(e -> moveDeck(deck, name));
            menu.getItems().add(item);
        }
        menu.getItems().add(new javafx.scene.control.SeparatorMenuItem());
        final javafx.scene.control.MenuItem create = new javafx.scene.control.MenuItem(
                NeoText.get("home.collection.newEllipsis"));
        create.setOnAction(e -> askNewCollection(deck));
        menu.getItems().add(create);

        menu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    /** Lleva el mazo a {@code to} ({@code null} = "Mis mazos") y repinta. */
    private void moveDeck(final Deck deck, final String to) {
        final boolean own = isOwn(deck);
        final String from = collectionOf(deck);
        final String problem = forge.neo.deck.DeckCollections.move(format, deck, from, own, to);
        if (problem != null) {
            showProblem(NeoText.get(problem, deck.getName()));
            return;
        }
        final forge.util.storage.IStorage<Deck> dst = to == null ? format.storage()
                : forge.neo.deck.DeckCollections.storage(format, to);
        final Deck moved = dst == null ? null : dst.get(deck.getName());
        if (moved == null) {
            return;
        }
        if (own) {
            mine.removeIf(d -> d == deck);
            if (from != null && collections.containsKey(from)) {
                collections.get(from).removeIf(d -> d == deck);
            }
            decks.removeIf(d -> d == deck);
        }
        (to == null ? mine : collections.computeIfAbsent(to, k -> new ArrayList<>())).add(moved);
        decks.add(moved);
        if (selected == deck) {
            selected = moved;
        }
        retitleTabs();
        fillGrid();
    }

    /**
     * Pide el nombre de una coleccion nueva. Con {@code thenMove}, al crearla
     * se mete ahi ese mazo — es el "Nueva coleccion..." del menu de Mover.
     */
    private void askNewCollection(final Deck thenMove) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(TextDialog.line(NeoText.get("home.collection.new"),
                NeoText.get("home.collection.hint"), "",
                name -> {
                    overlay.hide();
                    final String n = name == null ? "" : name.trim();
                    final String problem = forge.neo.deck.DeckCollections.problem(format, n, null);
                    if (problem != null) {
                        showProblem(NeoText.get(problem, n));
                        return;
                    }
                    if (!forge.neo.deck.DeckCollections.create(format, n)) {
                        showProblem(NeoText.get("home.collection.error.disk", n));
                        return;
                    }
                    collections.put(n, new ArrayList<>());
                    rebuildTabRow();
                    showCollection(n);
                    if (thenMove != null) {
                        moveDeck(thenMove, n);
                    }
                },
                overlay::hide));
    }

    private void askRenameCollection(final String from) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(TextDialog.line(NeoText.get("home.collection.rename"),
                NeoText.get("home.collection.hint"), from,
                name -> {
                    overlay.hide();
                    final String n = name == null ? "" : name.trim();
                    if (n.equals(from)) {
                        return;
                    }
                    final String problem = forge.neo.deck.DeckCollections.problem(format, n, from);
                    if (problem != null) {
                        showProblem(NeoText.get(problem, n));
                        return;
                    }
                    if (!forge.neo.deck.DeckCollections.rename(format, from, n)) {
                        showProblem(NeoText.get("home.collection.error.disk", n));
                        return;
                    }
                    // Se vuelven a leer: el almacen viejo apuntaba a la carpeta
                    // de antes, y los mazos que colgaban de el tambien.
                    final List<Deck> old = collections.remove(from);
                    if (old != null) {
                        decks.removeAll(old);
                    }
                    final List<Deck> now = forge.neo.deck.DeckCollections.decks(format, n);
                    collections.put(n, new ArrayList<>(now));
                    decks.addAll(now);
                    if (selected != null && old != null && old.contains(selected)) {
                        selected = findByName(now, selected.getName());
                    }
                    rebuildTabRow();
                    showCollection(n);
                },
                overlay::hide));
    }

    /**
     * Borrar una coleccion <b>no borra ningun mazo</b>: vuelven a "Mis mazos".
     * Se pregunta igual, porque deshacerla a mano es ir moviendolos uno a uno.
     */
    private void confirmDeleteCollection(final String name) {
        final int count = collections.getOrDefault(name, List.of()).size();
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(
                NeoText.get("home.collection.delete.ask", name),
                NeoText.get("home.collection.delete.detail", count),
                java.util.List.of(NeoText.get("home.collection.delete.yes"),
                        NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice == null || choice != 0) {
                        return;
                    }
                    final List<Deck> old = collections.remove(name);
                    final int moved = forge.neo.deck.DeckCollections.delete(format, name);
                    if (old != null) {
                        decks.removeAll(old);
                    }
                    reloadMine();
                    // Si ha fallado, la carpeta sigue ahi con lo que no se
                    // haya podido sacar: se vuelve a leer tal cual este.
                    for (final String still : forge.neo.deck.DeckCollections.list(format)) {
                        if (still.equalsIgnoreCase(name)) {
                            final List<Deck> left = forge.neo.deck.DeckCollections.decks(format, still);
                            collections.put(still, new ArrayList<>(left));
                            decks.addAll(left);
                        }
                    }
                    if (selected != null && old != null && old.contains(selected)) {
                        selected = findByName(mine, selected.getName());
                    }
                    showing = Tab.MINE;
                    collection = null;
                    rebuildTabRow();
                    retitleTabs();
                    pager.reset();
                    fillGrid();
                    if (moved < 0) {
                        showProblem(NeoText.get("home.collection.error.delete", name));
                    }
                }));
    }

    /** "Mis mazos" otra vez desde el almacen del formato, que es la verdad. */
    private void reloadMine() {
        // Por objeto: removeAll tira de Deck.equals, que compara el nombre, y
        // se llevaba tambien el preconstruido que se llama como uno tuyo.
        decks.removeIf(d -> mine.stream().anyMatch(m -> m == d));
        mine.clear();
        format.storage().forEach(mine::add);
        decks.addAll(mine);
    }

    private static Deck findByName(final List<Deck> in, final String name) {
        for (final Deck d : in) {
            if (d.getName().equals(name)) {
                return d;
            }
        }
        return null;
    }

    private void showProblem(final String message) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("home.collection.error.title"), message,
                java.util.List.of(NeoText.get("common.accept")), 0, choice -> overlay.hide()));
    }

    /**
     * Rellena la rejilla con la pestanya y la busqueda actuales.
     *
     * <p>Se corta en {@link #MAX_TILES}: cada mazo es una carta con su imagen, y
     * pintar los cientos de preconstruidos de golpe deja la pantalla pegada. El
     * buscador es la forma de llegar al resto, y el contador dice cuantos se han
     * quedado fuera para que no parezca que no estan.
     */
    private void fillGrid() {
        final List<Deck> source = source();
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(java.util.Locale.ROOT);

        final List<Deck> hits = new ArrayList<>();
        for (final Deck d : source) {
            if (q.isEmpty() || matches(d, q)) {
                hits.add(d);
            }
        }

        pager.setTotal(hits.size());

        grid.getChildren().clear();
        tiles.clear();
        for (int i = pager.from(); i < pager.to(); i++) {
            final Deck deck = hits.get(i);
            final DeckTile tile = new DeckTile(deck, tileWidth);
            tile.setOnMouseClicked(e -> {
                select(deck);
                // Doble click = elegir y empezar, como en cualquier lanzador.
                if (e.getClickCount() >= 2) {
                    play.fire();
                }
            });
            // La papelera, solo en los tuyos. Un preconstruido de Forge volveria
            // a salir al arrancar (se lee de res/) y uno de internet se rehace
            // con la siguiente descarga: ahi el boton seria mentira.
            if (format.canDelete(deck) || collectionOf(deck) != null) {
                tile.setOnDelete(() -> confirmDelete(deck));
            }
            // Mover a otra coleccion (o copiar, si no es tuyo). Solo donde hay
            // colecciones: draft y sellado no las tienen.
            if (forge.neo.deck.DeckCollections.isSupported(format)) {
                tile.setOnMove(anchor -> {
                    select(deck);
                    showMoveMenu(deck, anchor);
                });
            }
            tiles.add(tile);
            grid.getChildren().add(tile);
        }

        if (hits.isEmpty()) {
            final Label empty = new Label(showing == Tab.MINE
                    ? NeoText.get("home.empty.mine", NeoText.get("home.newDeck"))
                    : showing == Tab.NET ? NeoText.get("home.empty.net")
                    : showing == Tab.COLLECTION ? NeoText.get("home.empty.collection",
                            NeoText.get("home.collection.move"), NeoText.get("home.newDeck"))
                    : NeoText.get("home.empty.stock"));
            empty.setWrapText(true);
            empty.setMaxWidth(UiScale.px(560));
            empty.getStyleClass().add("home-subtitle");
            grid.getChildren().add(empty);
        }

        gridCount.setText(hits.size() == 1
                ? NeoText.get("home.deckCount.one")
                : NeoText.get("home.deckCount", hits.size()));

        // La seleccion tiene que seguir estando entre los resultados. Si la
        // busqueda o la pestanya la han dejado fuera, se coge el primero; pero
        // NO se toca solo por cambiar de pagina, o pasar paginas te cambiaria
        // el mazo elegido sin pedirlo.
        if (selected != null && !hits.contains(selected)) {
            select(hits.isEmpty() ? null : hits.get(0));
        } else {
            select(selected);
        }
    }

    /**
     * Pregunta antes de borrar, con el nombre delante.
     *
     * <p>Un mazo borrado <b>no se recupera</b>: se va el {@code .dck} de la
     * carpeta que compartimos con la instalacion normal de Forge, o sea que
     * tambien desaparece de alli. Lo que no tiene vuelta atras no puede pasar
     * por un click de mas (principio 6).
     *
     * <p>Y en draft y sellado lo que se borra <b>es el evento entero</b>, no un
     * mazo: tu mazo y los de los siete rivales viven juntos y no se pueden
     * separar. Se dice, porque no se adivina.
     */
    /**
     * Abre la pregunta de borrar sin borrar nada.
     *
     * <p>{@code -Dneo.home.askDeleteAt=N}. Solo para capturar el dialogo: es lo
     * unico de esta pantalla que no se puede fotografiar de otra forma, porque
     * hace falta un raton encima de una baldosa.
     */
    private void maybeShowDeleteTest() {
        final long at = Long.getLong("neo.home.askDeleteAt", -1L);
        if (at < 0) {
            return;
        }
        final javafx.animation.PauseTransition t =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(at));
        t.setOnFinished(e -> {
            if (selected != null && (format.canDelete(selected) || collectionOf(selected) != null)) {
                confirmDelete(selected);
            }
        });
        t.play();
    }

    private void confirmDelete(final Deck deck) {
        final boolean event = format == forge.neo.match.NeoFormat.DRAFT
                || format == forge.neo.match.NeoFormat.SELLADO;
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(
                NeoText.get("deck.delete.ask"),
                NeoText.get(event ? "deck.delete.event" : "deck.delete.detail", deck.getName()),
                java.util.List.of(NeoText.get("deck.delete.yes"), NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice == null || choice != 0) {
                        return;
                    }
                    final String in = collectionOf(deck);
                    if (in != null) {
                        final forge.util.storage.IStorage<Deck> s =
                                forge.neo.deck.DeckCollections.storage(format, in);
                        if (s == null) {
                            return;
                        }
                        s.delete(deck.getName());
                        if (s.contains(deck.getName())) {
                            return;
                        }
                        collections.get(in).removeIf(d -> d == deck);
                    } else if (!format.delete(deck)) {
                        return;
                    } else {
                        mine.removeIf(d -> d == deck);
                    }
                    // Por objeto, como todo lo de arriba: Deck.equals compara
                    // el NOMBRE, y borrar tu "Abzan Armor [TDC] [2025]" se
                    // llevaba de la pestanya de Forge el preconstruido que se
                    // llama igual.
                    decks.removeIf(d -> d == deck);
                    stock.removeIf(d -> d == deck);
                    net.removeIf(d -> d == deck);
                    if (deck == selected) {
                        selected = null;
                    }
                    retitleTabs();
                    fillGrid();
                }));
    }

    /** Los contadores de las pestanyas, despues de borrar. */
    private void retitleTabs() {
        final Button mineTab = tabButtons.get(Tab.MINE);
        if (mineTab != null) {
            mineTab.setText(NeoText.get("home.tab.mine", mine.size()));
        }
        final Button stockTab = tabButtons.get(Tab.STOCK);
        if (stockTab != null) {
            stockTab.setText(NeoText.get("home.tab.stock", stock.size()));
        }
        final Button netTab = tabButtons.get(Tab.NET);
        if (netTab != null) {
            netTab.setText(NeoText.get("home.tab.net", net.size()));
        }
        for (final java.util.Map.Entry<String, Button> e : collectionButtons.entrySet()) {
            final List<Deck> in = collections.get(e.getKey());
            e.getValue().setText(NeoText.get("home.tab.collection", e.getKey(),
                    in == null ? 0 : in.size()));
        }
    }

    /** Busca por nombre de mazo y tambien por el de su comandante. */
    private static boolean matches(final Deck deck, final String q) {
        if (deck.getName().toLowerCase(java.util.Locale.ROOT).contains(q)) {
            return true;
        }
        final PaperCard face = DeckTile.commanderOf(deck);
        return face != null
                && (face.getName().toLowerCase(java.util.Locale.ROOT).contains(q)
                    || forge.neo.card.CardText.nameOf(face)
                            .toLowerCase(java.util.Locale.ROOT).contains(q));
    }

    private Region footer(final StartHandler onStart) {
        summary.getStyleClass().add("home-summary");

        final HBox options = new HBox(30,
                choice(NeoText.get("home.opponents"), new String[] {"1", "2", "3"},
                        String.valueOf(opponents),
                        v -> {
                            opponents = Integer.parseInt(v);
                            NeoSettings.setOpponents(format, opponents);
                            resizeTeams();
                            rebuildOpponentRow();
                            updateSummary();
                        }),
                choice(NeoText.get("home.ai"), AI_PROFILES, aiProfile,
                        v -> {
                            aiProfile = v;
                            NeoSettings.set(NeoSettings.AI_PROFILE, v);
                            updateSummary();
                        }),
                scaleChoice());
        options.setAlignment(Pos.CENTER_LEFT);

        unshrinkable(play).getStyleClass().addAll("btn-primary", "btn-play");
        play.setOnAction(e -> launch(onStart, false));

        final Button watch = new Button(NeoText.get("home.watch"));
        unshrinkable(watch).getStyleClass().add("btn-secondary");
        watch.setOnAction(e -> launch(onStart, true));

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        final Button back = new Button(NeoText.get("common.back"));
        unshrinkable(back).getStyleClass().add("btn-secondary");
        back.setOnAction(e -> onBack.run());

        final HBox row = new HBox(20, options, gap, back, watch, play);
        row.setAlignment(Pos.CENTER_LEFT);

        opponentRow = opponentRow();
        rebuildOpponentRow();

        final VBox box = new VBox(10, summary, opponentRow, row);
        box.getStyleClass().add("home-footer");
        box.setPadding(new Insets(16, 30, 22, 30));
        return box;
    }

    /**
     * Un boton por rival: con que mazo juega cada uno.
     *
     * <p>Se clica y sale la lista de mazos del formato, con los preconstruidos
     * de Forge incluidos. "Al azar" deja que la pantalla elija uno distinto para
     * cada rival, que es lo que se quiere casi siempre.
     */
    private Region opponentRow() {
        final HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void rebuildOpponentRow() {
        if (opponentRow == null) {
            return;
        }
        final HBox row = (HBox) opponentRow;
        row.getChildren().clear();
        opponentButtons.clear();
        teamBoxes.clear();

        while (opponentDecks.size() < opponents) {
            opponentDecks.add(null);
        }

        // Con que comandante juegas (Forge #12052): lo primero de la fila, que
        // es lo que va con TU mazo. Solo en formatos con comandante.
        commanderButton = null;
        if (forge.neo.deck.CommanderChoice.applies(format.getGameType())) {
            commanderButton = new Button();
            commanderButton.setId("home-commander");
            commanderButton.getStyleClass().add("segment");
            commanderButton.setMinWidth(Region.USE_PREF_SIZE);
            commanderButton.setOnAction(e -> pickCommander(-1));
            final Region sep = new Region();
            sep.setMinWidth(14);
            row.getChildren().addAll(commanderButton, sep);
            refreshCommander();
        }

        // Con un solo rival no hay equipos que hacer: tu y el, uno contra uno,
        // y la fila se queda como estaba antes de que hubiera equipos.
        final boolean withTeams = opponents > 1;
        if (withTeams) {
            // Tu asiento tambien lleva su "Equipo", como en el lobby de Forge.
            final Label you = new Label(NeoText.get("home.you"));
            you.getStyleClass().add("caption");
            row.getChildren().add(seat(you, teamBox(0)));
        }

        opponentCaption = new Label();
        opponentCaption.getStyleClass().add("caption");
        row.getChildren().add(opponentCaption);

        for (int i = 0; i < opponents; i++) {
            final int index = i;
            final Button b = new Button();
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> pickOpponentDeck(index));
            opponentButtons.add(b);
            final HBox who = rivalCommanderButton(index) == null ? new HBox(2, b)
                    : new HBox(2, b, rivalCommanderButton(index));
            who.getChildren().add(seatKindButton(index));
            who.setAlignment(Pos.CENTER_LEFT);
            row.getChildren().add(withTeams ? seat(who, teamBox(i + 1)) : who);
        }
        refreshOpponentLabels();
    }

    /**
     * La corona junto al mazo de un rival: con que comandante juega. Solo si
     * se le ha fijado un mazo (al azar no hay mazo que mirar todavia) y el
     * formato lleva comandante. Se crea una vez por asiento y se reutiliza.
     */
    private Button rivalCommanderButton(final int index) {
        final Deck d = index < opponentDecks.size() ? opponentDecks.get(index) : null;
        if (d == null || !forge.neo.deck.CommanderChoice.applies(format.getGameType())
                || d.getCommanders().isEmpty()) {
            rivalCommanderButtons.remove(index);
            return null;
        }
        return rivalCommanderButtons.computeIfAbsent(index, i -> {
            final Button b = new Button(NeoText.get("commander.pick.short"));
            b.getStyleClass().addAll("segment", "rival-commander");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setTooltip(new javafx.scene.control.Tooltip(
                    NeoText.get("commander.pick.rivalTip", i + 1, commanderNames(d, opponentCommanders.get(i)))));
            b.setOnAction(e -> pickCommander(i));
            return b;
        });
    }

    private final java.util.Map<Integer, Button> rivalCommanderButtons = new java.util.HashMap<>();

    /**
     * IA o PERSONA en este asiento (hot seat, itch.io 04-10-2026: <i>"Forge
     * allows for setting up all players in a match to human - switch from AI to
     * human during game setup"</i>). Una persona juega ese mazo en este mismo
     * ordenador: la mesa se gira hacia quien tenga que decidir, con una cortina
     * para no verle la mano al otro. Se recuerda (RivalSetup.HUMANS).
     */
    private Button seatKindButton(final int index) {
        final Button b = new Button();
        b.getStyleClass().addAll("segment", "seat-kind");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("home.seat.tip")));
        final Runnable paint = () -> {
            final boolean human = forge.neo.look.RivalSetup.isHuman(index);
            b.setText(NeoText.get(human ? "home.seat.human" : "home.seat.ai"));
            b.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("selected"), human);
        };
        paint.run();
        b.setOnAction(e -> {
            forge.neo.look.RivalSetup.setHuman(index, !forge.neo.look.RivalSetup.isHuman(index));
            paint.run();
            updateSummary();
        });
        return b;
    }

    /** Tu mazo tal y como se va a jugar: con el comandante elegido, si hay. */
    private Deck effectiveDeck() {
        if (selected == null || !forge.neo.deck.CommanderChoice.applies(format.getGameType())) {
            return selected;
        }
        return forge.neo.deck.CommanderChoice.apply(selected,
                forge.neo.deck.CommanderChoice.remembered(selected, format.getGameType().getDeckFormat()));
    }

    /** "Atraxa" o "Tymna + Thrasios", traducidos: quien lleva el mazo ahora. */
    private static String commanderNames(final Deck deck, final List<PaperCard> pick) {
        final List<PaperCard> who = pick != null ? pick : deck.getCommanders();
        final List<String> names = new ArrayList<>();
        for (final PaperCard c : who) {
            names.add(forge.neo.card.CardText.nameOf(c));
        }
        return String.join(" + ", names);
    }

    /**
     * Pone el texto del boton de tu comandante y lo habilita si hay a quien
     * elegir. Eso lo sabe el motor mirando cada carta del mazo, asi que se
     * pregunta en otro hilo la primera vez y se guarda por mazo.
     */
    private void refreshCommander() {
        final Button b = commanderButton;
        if (b == null) {
            return;
        }
        final Deck deck = selected;
        if (deck == null || deck.getCommanders().isEmpty()) {
            b.setVisible(false);
            b.setManaged(false);
            return;
        }
        b.setVisible(true);
        b.setManaged(true);
        final forge.deck.DeckFormat df = format.getGameType().getDeckFormat();
        final List<PaperCard> pick = forge.neo.deck.CommanderChoice.remembered(deck, df);
        b.setText(NeoText.get("commander.pick.button", commanderNames(deck, pick)));
        final Boolean known = commanderChoices.get(deck);
        if (known != null) {
            b.setDisable(!known);
            b.setTooltip(known ? null : new javafx.scene.control.Tooltip(NeoText.get("commander.pick.none")));
            return;
        }
        b.setDisable(true);
        final Thread t = new Thread(() -> {
            boolean has;
            try {
                has = forge.neo.deck.CommanderChoice.hasChoices(deck,
                        forge.neo.deck.CommanderChoice.options(deck, df), df);
            } catch (final RuntimeException e) {
                has = false;
            }
            final boolean result = has;
            javafx.application.Platform.runLater(() -> {
                commanderChoices.put(deck, result);
                if (selected == deck) {
                    refreshCommander();
                }
            });
        }, "neo-commander-choices");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Solo pruebas ({@code --pick-commander}): elige el mazo que contenga
     * {@code deckName} en el nombre (de los tuyos o de los de Forge) y abre el
     * dialogo de su comandante.
     */
    public void openCommanderPicker(final String deckName) {
        // -Dneo.commander.rivalDeck=X: ese mazo para el primer rival, para ver
        // su corona. Y si no se pide tu mazo, el dialogo que se abre es el suyo.
        final String rivalName = System.getProperty("neo.commander.rivalDeck");
        if (rivalName != null && !rivalName.isBlank()) {
            final String q = rivalName.toLowerCase(java.util.Locale.ROOT);
            for (final Deck d : decks) {
                if (d.getName().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    while (opponentDecks.isEmpty()) {
                        opponentDecks.add(null);
                    }
                    opponentDecks.set(0, d);
                    rebuildOpponentRow();
                    break;
                }
            }
            if (deckName == null || deckName.isBlank()) {
                if (!Boolean.getBoolean("neo.commander.noDialog")) {
                    pickCommander(0);
                }
                return;
            }
        }
        if (deckName != null && !deckName.isBlank()) {
            final String q = deckName.toLowerCase(java.util.Locale.ROOT);
            for (final Deck d : decks) {
                if (d.getName().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    select(d);
                    break;
                }
            }
        }
        if (!Boolean.getBoolean("neo.commander.noDialog")) {
            pickCommander(-1);
        }
    }

    /**
     * El dialogo de elegir comandante: -1 para tu mazo, o el asiento de un
     * rival. Lo elegido vale para la proxima partida; el mazo no se toca.
     */
    private void pickCommander(final int rival) {
        final Deck deck = rival < 0 ? selected
                : rival < opponentDecks.size() ? opponentDecks.get(rival) : null;
        if (deck == null) {
            return;
        }
        final forge.deck.DeckFormat df = format.getGameType().getDeckFormat();
        final List<PaperCard> current = rival < 0
                ? forge.neo.deck.CommanderChoice.remembered(deck, df) : opponentCommanders.get(rival);
        final CommanderPickDialog dialog = new CommanderPickDialog(deck, df,
                current == null ? deck.getCommanders() : current, tileWidth * 0.62,
                picked -> {
                    overlay.hide();
                    if (rival < 0) {
                        forge.neo.deck.CommanderChoice.remember(deck, df, picked);
                        refreshCommander();
                    } else {
                        if (forge.neo.deck.CommanderChoice.same(picked, deck.getCommanders())) {
                            opponentCommanders.remove(rival);
                        } else {
                            opponentCommanders.put(rival, picked);
                        }
                        rivalCommanderButtons.remove(rival);
                        rebuildOpponentRow();
                    }
                    updateSummary();
                },
                overlay::hide);
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(dialog);
    }

    /**
     * Lo de un asiento, junto: quien es (o con que mazo juega) y su equipo.
     * Van pegados porque son del mismo jugador; entre asientos queda el hueco
     * de la fila.
     */
    private static HBox seat(final javafx.scene.Node who,
                             final javafx.scene.control.ComboBox<Integer> team) {
        final Label caption = new Label(forge.util.Localizer.getInstance().getMessage("lblTeam"));
        caption.getStyleClass().add("caption");
        final HBox box = new HBox(4, who, caption, team);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 8, 0, 0));
        return box;
    }

    /**
     * El desplegable de equipo del asiento {@code seat}: 1 a 8, como el de
     * Forge ({@code PlayerPanel.populateTeamsComboBoxes}). Se guarda en cuanto
     * se toca, igual que el resto de esta pantalla.
     */
    private javafx.scene.control.ComboBox<Integer> teamBox(final int seat) {
        final javafx.scene.control.ComboBox<Integer> box = new javafx.scene.control.ComboBox<>();
        box.getStyleClass().add("team-combo");
        for (int t = 1; t <= forge.neo.match.NeoTeams.MAX_TEAMS; t++) {
            box.getItems().add(t);
        }
        box.setValue(seat < teams.length ? teams[seat] + 1 : seat + 1);
        box.setOnAction(e -> {
            final Integer picked = box.getValue();
            if (picked == null || seat >= teams.length) {
                return;
            }
            teams[seat] = picked - 1;
            NeoSettings.setTeams(format, forge.neo.match.NeoTeams.toSetting(teams));
            refreshOpponentLabels();
            updateSummary();
        });
        teamBoxes.add(box);
        return box;
    }

    private void refreshOpponentLabels() {
        for (int i = 0; i < opponentButtons.size(); i++) {
            final Deck d = i < opponentDecks.size() ? opponentDecks.get(i) : null;
            final String pool = d == null ? opponentPools.get(i) : null;
            opponentButtons.get(i).setText(NeoText.get("home.aiDeck", i + 1,
                    d != null ? shorten(d.getName())
                            : pool != null ? NeoText.get("home.randomFrom", shorten(pool))
                            : NeoText.get("home.random")));
        }
        if (opponentCaption != null) {
            // "Juegan contra ti" deja de ser verdad en cuanto hay alguien en
            // tu equipo.
            opponentCaption.setText(NeoText.get(hasTeams() ? "home.atTable" : "home.against"));
        }
    }

    /**
     * Ajusta los equipos al numero de rivales, conservando lo ya elegido. Un
     * asiento nuevo entra en su propio equipo, como en Forge.
     *
     * <p>Aqui NO se corrige un reparto de un solo equipo: a medio elegir es
     * normal pasar por ahi (Forge tampoco lo impide). Lo que se impide es
     * jugarlo — ver {@link #updateSummary}.
     */
    private void resizeTeams() {
        final int[] out = forge.neo.match.NeoTeams.freeForAll(opponents);
        for (int i = 0; i < out.length && i < teams.length; i++) {
            out[i] = teams[i];
        }
        teams = out;
        NeoSettings.setTeams(format, forge.neo.match.NeoTeams.toSetting(teams));
    }

    /** Si la partida va por equipos: alguien comparte equipo con alguien. */
    private boolean hasTeams() {
        return opponents > 1 && !forge.neo.match.NeoTeams.isFreeForAll(teams);
    }

    /** Si el reparto no se puede jugar: todos en el mismo equipo. */
    private boolean notEnoughTeams() {
        return opponents > 1 && !forge.neo.match.NeoTeams.isEnoughTeams(teams);
    }

    /** Los nombres de los preconstruidos son largos; en un boton no caben. */
    private static String shorten(final String name) {
        return name.length() <= 22 ? name : name.substring(0, 21) + "\u2026";
    }

    /**
     * Elegir con que juega ese rival.
     *
     * <p>Se ensenya la misma rejilla visual que para tu propio mazo: la carta
     * del comandante, con pestanyas y buscador. Elegir el mazo del rival no
     * deberia sentirse distinto de elegir el tuyo.
     */
    public void openOpponentPicker(final int index, final boolean stockTab) {
        pickOpponentDeck(index);
        if (stockTab && picker != null) {
            picker.showStock();
        }
    }

    private void pickOpponentDeck(final int index) {
        // "Los tuyos" del selector del rival: los SUELTOS. Los de las colecciones
        // van en su pestanya (setCollections), como en la pantalla de mazos.
        picker = new DeckPickerDialog(NeoText.get("home.pickRival", index + 1), mine, stock,
                tileWidth * 0.72,
                chosen -> {
                    overlay.hide();
                    while (opponentDecks.size() <= index) {
                        opponentDecks.add(null);
                    }
                    opponentDecks.set(index, chosen);
                    opponentPools.remove(index);
                    opponentCommanders.remove(index);
                    rivalCommanderButtons.remove(index);
                    rebuildOpponentRow();
                    refreshOpponentLabels();
                    updateSummary();
                },
                overlay::hide,
                format.isCommanderStyle() ? this::generateOpponentDeck : null);
        picker.setCollections(collections);
        picker.setRandomFromCollections(collections, name -> {
            overlay.hide();
            while (opponentDecks.size() <= index) {
                opponentDecks.add(null);
            }
            opponentDecks.set(index, null);
            opponentPools.put(index, name);
            opponentCommanders.remove(index);
            rivalCommanderButtons.remove(index);
            rebuildOpponentRow();
            refreshOpponentLabels();
            updateSummary();
        });
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(picker);
    }

    /**
     * "Genérame uno" (la auditoría del motor, apartado B6): un mazo de verdad para un
     * comandante legal al azar, no un reparto entre los que ya tenías.
     *
     * <p>{@code DeckgenUtil.generateCommanderDeck} ya hace las dos cosas —
     * elegir el comandante Y montarle el mazo — así que no hay nada que
     * reinventar aquí. {@code isCardGen=true} vive dentro de esa llamada:
     * usa los mazos genéticos de IA para que el rival tenga algo de
     * sinergia real.
     *
     * <p>Pasa por {@code GeneratedDecks}: el generador mete a veces Gleemox,
     * que ningun formato deja jugar, y la IA salia con ella.
     */
    private forge.deck.Deck generateOpponentDeck() {
        try {
            return forge.neo.deck.GeneratedDecks.commanderDeck(true, format.getGameType());
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private DeckPickerDialog picker;

    /**
     * Empieza la partida y deja la pantalla como esta para la proxima vez.
     *
     * <p>Se guarda TODO lo elegido, no solo lo que se haya tocado: asi el
     * fichero de ajustes refleja siempre con que se jugo la ultima vez, aunque
     * el jugador no cambiara nada.
     */
    private void launch(final StartHandler onStart, final boolean watch) {
        // Todos en el mismo equipo: JUGAR ya esta apagado (updateSummary), pero
        // VER no, y con un solo equipo el motor da la partida por acabada
        // antes de empezar.
        if (selected == null && randomFrom != null && !notEnoughTeams()) {
            // Al azar: se sortea ahora, solo entre los que se pueden jugar.
            final Deck drawn = drawRandom();
            if (drawn == null) {
                summary.setText(NeoText.get("home.random.noneLegal", randomPoolName()));
                summary.pseudoClassStateChanged(INVALID, true);
                play.setDisable(true);
                return;
            }
            NeoSettings.setOpponents(format, opponents);
            NeoSettings.set(NeoSettings.AI_PROFILE, aiProfile);
            NeoSettings.setTeams(format, forge.neo.match.NeoTeams.toSetting(teams));
            NeoSettings.save();
            System.out.println("[home] mazo al azar de " + randomPoolName() + ": " + drawn.getName());
            onStart.start(drawn, opponents, aiProfile, watch, resolvedOpponentDecks(),
                    hasTeams() ? teams.clone() : null);
            return;
        }
        if (selected == null || notEnoughTeams()) {
            return;
        }
        NeoSettings.set(NeoSettings.DECK, selected.getName());
        NeoSettings.setOpponents(format, opponents);
        NeoSettings.set(NeoSettings.AI_PROFILE, aiProfile);
        NeoSettings.setTeams(format, forge.neo.match.NeoTeams.toSetting(teams));
        NeoSettings.save();
        onStart.start(effectiveDeck(), opponents, aiProfile, watch, resolvedOpponentDecks(),
                hasTeams() ? teams.clone() : null);
    }

    /**
     * Los mazos de los rivales, con los "al azar" ya resueltos.
     *
     * <p>Se elige uno distinto para cada rival mientras haya de sobra: tres
     * rivales con el mismo mazo es justo lo que se queria evitar. Si no hay
     * suficientes mazos, se repite antes que dejar a alguien sin nada.
     */
    private List<Deck> resolvedOpponentDecks() {
        final List<Deck> out = new ArrayList<>();
        // La bolsa lleva TODOS los mazos siempre: lo unico que cambia con el
        // ajuste es el orden en que salen. Ver forge.neo.deck.DeckAge — de los
        // 505 preconstruidos de Estandar que trae Forge, 392 son de antes de
        // 2018, asi que un sorteo plano saca casi siempre un rival de hace
        // veinte anyos y con escaneos que a tamanyo de mesa no se leen.
        final List<Deck> pool = NeoSettings.getBool(NeoSettings.MODERN_RIVALS, true)
                ? forge.neo.deck.DeckAge.shuffleFavouringModern(decks, new java.util.Random())
                : new ArrayList<>(decks);
        if (!NeoSettings.getBool(NeoSettings.MODERN_RIVALS, true)) {
            java.util.Collections.shuffle(pool);
        }
        final java.util.Set<String> used = new java.util.HashSet<>();

        for (int i = 0; i < opponents; i++) {
            final Deck chosen = i < opponentDecks.size() ? opponentDecks.get(i) : null;
            if (chosen != null) {
                out.add(forge.neo.deck.CommanderChoice.apply(chosen, opponentCommanders.get(i)));
                continue;
            }
            // Al azar DE UNA COLECCION: el sorteo, dentro de ella. Si se ha
            // quedado vacia (borrada o sin mazos), cae al azar de siempre.
            final String poolName = opponentPools.get(i);
            final List<Deck> fromPool = poolName == null ? null : collections.get(poolName);
            if (fromPool != null && !fromPool.isEmpty()) {
                final List<Deck> shuffled = new ArrayList<>(fromPool);
                java.util.Collections.shuffle(shuffled);
                Deck inPool = null;
                for (final Deck d : shuffled) {
                    if (used.add(d.getName())) {
                        inPool = d;
                        break;
                    }
                }
                out.add(inPool != null ? inPool : shuffled.get(0));
                continue;
            }
            Deck pick = null;
            for (final Deck d : pool) {
                if (used.add(d.getName())) {
                    pick = d;
                    break;
                }
            }
            if (pick == null && !pool.isEmpty()) {
                pick = pool.get(i % pool.size());
            }
            out.add(pick);
        }
        return out;
    }

    /**
     * Fila de botones excluyentes.
     *
     * <p>Se prefiere a un desplegable porque las opciones son pocas y se ven
     * todas de golpe: no hay que abrir nada para saber que se puede elegir.
     */
    private Region choice(final String caption, final String[] values, final String current,
                          final Consumer<String> onPick) {
        final Label label = new Label(caption);
        label.getStyleClass().add("caption");

        final HBox row = new HBox(4);
        final List<Button> buttons = new ArrayList<>();
        for (final String value : values) {
            final Button b = new Button(LookScreen.aiLabel(value));
            b.getStyleClass().add("segment");
            // Sin esto, cuando la fila no cabe JavaFX encoge los botones por
            // debajo de su texto y lo corta con puntos suspensivos: salian
            // "Cautio...", "Reckl...", e incluso "A..." en la escala.
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, value.equals(current));
            b.setOnAction(e -> {
                for (final Button other : buttons) {
                    other.pseudoClassStateChanged(SELECTED, other == b);
                }
                onPick.accept(value);
            });
            buttons.add(b);
            row.getChildren().add(b);
        }

        final VBox box = new VBox(4, label, row);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /**
     * Escala de la interfaz.
     *
     * <p>"Auto" la deduce de la altura de la ventana y es lo correcto casi
     * siempre; los valores fijos estan para pantallas con escalado raro de
     * Windows, donde el automatico se queda corto o se pasa.
     */
    private Region scaleChoice() {
        final Double saved = NeoSettings.getScale();
        final String current = saved == null ? "Auto"
                : String.format(java.util.Locale.ROOT, "%.0f%%", saved * 100);
        return choice(NeoText.get("home.scale"),
                new String[] {"Auto", "90%", "100%", "115%", "130%"}, current,
                v -> {
                    final Double scale = "Auto".equals(v) ? null
                            : Double.valueOf(Integer.parseInt(v.replace("%", "")) / 100.0);
                    UiScale.setOverride(scale);
                    NeoSettings.setScale(scale);
                    if (getScene() != null && getScene().getRoot() != null) {
                        getScene().getRoot().setStyle(UiScale.rootStyle(getScene().getHeight()));
                    }
                });
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    // ---------------------------------------------------------------

    /** Un boton que nunca se encoge por debajo de su texto. */
    private static Button unshrinkable(final Button b) {
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    private void select(final Deck deck) {
        this.selected = deck;
        for (final DeckTile tile : tiles) {
            tile.setSelected(tile.getDeck() == deck);
        }
        if (deck != null) {
            NeoSettings.set(NeoSettings.DECK, deck.getName());
            // Elegir un mazo es dejar de ir al azar.
            if (randomFrom != null) {
                randomFrom = null;
                NeoSettings.set(randomKey(), "");
                markRandom();
            }
        }
        edit.setDisable(deck == null);
        refreshCommander();
        // JUGAR lo habilita updateSummary, que es quien sabe si el mazo es legal.
        updateSummary();
    }

    /**
     * La linea de estado, que ademas decide si se puede jugar.
     *
     * <p>Un mazo a medias — montado en el deck builder y guardado sin terminar —
     * no se puede jugar, y hay que decirlo AQUI. Si no, se pulsa JUGAR y lo que
     * pasa es que el motor se queja en ingles o la partida sale rara, sin que
     * nadie explique que al mazo le faltan sesenta cartas.
     *
     * <p>Quien contesta si es jugable es el motor
     * ({@code DeckFormat.getDeckConformanceProblem}), no nosotros.
     */
    private void updateSummary() {
        if (selected == null && randomFrom != null) {
            final int n = randomPool().size();
            final boolean noTeams = notEnoughTeams();
            summary.setText(noTeams
                    ? forge.util.Localizer.getInstance().getMessage("lblNotEnoughTeams")
                    : NeoText.get("home.summary.random", format.getLabel(), randomPoolName(), n,
                            opponents + 1, LookScreen.aiSummary(aiProfile, opponents)));
            summary.pseudoClassStateChanged(INVALID, n == 0 || noTeams);
            play.setDisable(n == 0 || noTeams);
            return;
        }
        if (selected == null) {
            summary.setText(NeoText.get("home.noDecks"));
            play.setDisable(true);
            return;
        }

        String problem =
                forge.neo.deck.DeckProblem.translate(format.getGameType()
                        .getDeckFormat().getDeckConformanceProblem(effectiveDeck()));
        // Todos en el mismo equipo no se puede jugar, y se dice aqui con el
        // mismo texto con el que lo dice Forge al darle a empezar.
        if (problem == null && notEnoughTeams()) {
            problem = forge.util.Localizer.getInstance().getMessage("lblNotEnoughTeams");
        }

        if (problem == null && hasTeams()) {
            // "2 contra 2" en vez de "partida a 4": con equipos, lo que hay que
            // saber de un vistazo es como va el reparto. El tuyo primero.
            final java.util.Map<Integer, Integer> sizes = new java.util.LinkedHashMap<>();
            for (final int t : teams) {
                sizes.merge(t, 1, Integer::sum);
            }
            final StringBuilder split = new StringBuilder();
            for (final int n : sizes.values()) {
                if (split.length() > 0) {
                    split.append(NeoText.get("home.teams.vs"));
                }
                split.append(n);
            }
            summary.setText(NeoText.get("home.summary.teams",
                    format.getLabel(), selected.getName(), selected.getMain().countAll(),
                    split.toString(), LookScreen.aiSummary(aiProfile, opponents)));
        } else if (problem == null) {
            summary.setText(NeoText.get("home.summary",
                    format.getLabel(), selected.getName(), selected.getMain().countAll(),
                    opponents + 1, LookScreen.aiSummary(aiProfile, opponents)));
        } else {
            summary.setText(NeoText.get("home.summary.invalid",
                    format.getLabel(), selected.getName(), problem));
        }
        // Hot seat: que se vea que esta partida la juegan varias personas.
        if (problem == null) {
            int people = 1;
            for (int i = 0; i < opponents; i++) {
                if (forge.neo.look.RivalSetup.isHuman(i)) {
                    people++;
                }
            }
            if (people > 1) {
                summary.setText(summary.getText() + "  ·  " + NeoText.get("home.seat.summary", people));
            }
        }
        summary.pseudoClassStateChanged(INVALID, problem != null);
        play.setDisable(problem != null);
    }

    private static final javafx.css.PseudoClass INVALID =
            javafx.css.PseudoClass.getPseudoClass("invalid");

    /** Vuelve a leer las imagenes que hayan llegado de Scryfall. */
    public void refreshArt() {
        for (final DeckTile tile : tiles) {
            tile.refresh();
        }
        if (picker != null && picker.getParent() != null) {
            picker.refreshArt();
        }
    }

    private static int clamp(final int v, final int lo, final int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

}
