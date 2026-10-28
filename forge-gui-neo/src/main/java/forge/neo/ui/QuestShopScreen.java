package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.card.CardEdition;
import forge.item.BoosterPack;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import forge.neo.quest.NeoQuest;
import forge.neo.quest.NeoQuestSell;
import forge.neo.quest.NeoQuestShop;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * La tienda de sobres.
 *
 * <p>Es el pilar del modo aventura, y el bucle es corto a proposito: eliges
 * expansion, ves lo que cuesta, pagas, y el sobre se abre delante de ti. Todo
 * en la misma pantalla — mandar al jugador a otro sitio a ver que le ha tocado
 * rompe justo el momento por el que ha pagado.
 *
 * <p>El sobre se abre <b>encima de todo</b>, no en un rincon de la pagina. Es
 * el momento por el que se viene a esta pantalla: con quince cartas metidas
 * debajo del catalogo hay que bajar con la rueda para ver la mitad de lo que
 * has comprado. Las cartas se dimensionan contra el hueco real, asi que salen
 * lo mas grandes que quepan, y las que <b>no tenias</b> llevan su marca.
 *
 * <p>Click derecho sobre cualquiera la amplia a tamanyo de lectura
 * ({@link CardZoom}): en miniatura no se lee ni el nombre.
 *
 * <p><b>Tres mostradores, no uno.</b> La tienda del motor nunca vendio solo
 * sobres, y con solo sobres hay cartas del juego que <b>no se pueden conseguir
 * jamas</b>: las que nunca salieron en sobre. Asi que hay tres pestanyas:
 *
 * <ul>
 *   <li><b>Sobres</b>, por expansion. Lo de siempre.</li>
 *   <li><b>Cartas sueltas</b>: el mostrador que el motor ya repone solo, con
 *       cartas sacadas por rareza de TODA la base de datos. Crece con tus
 *       victorias.</li>
 *   <li><b>Secret Lair</b>: siete cartas de las que solo existen ahi. Ver
 *       {@link NeoQuestShop#secretLairPool()}.</li>
 * </ul>
 *
 * <p>En las tres se elige primero y se compra despues, con un boton aparte.
 * <b>Comprar no se puede deshacer</b>, asi que en esta pantalla no hay ni un
 * solo click que gaste dinero por si mismo.
 */
public class QuestShopScreen extends StackPane {

    /** Que se puede hacer desde la tienda. */
    public interface Actions {
        void back();
    }

    private final double cardWidth;
    private final Actions actions;

    private final BorderPane frame = new BorderPane();
    private final Overlay overlay = new Overlay();

    private final Label credits = new Label();
    private final TextField search = new TextField();
    private final FlowPane editionGrid = new FlowPane(10, 10);
    private final Pager pager;

    private final CheckBox affordableOnly = new CheckBox();
    private final java.util.Map<String, Integer> priceCache = new java.util.HashMap<>();
    private List<CardEdition> filtered = new ArrayList<>();
    private CardEdition chosen;
    private BoosterPack pack;
    private boolean opening;

    private final Label packName = new Label();
    private final Label packPrice = new Label();
    private final Button buy = new Button();

    /**
     * Cuantos sobres se compran de una vez (Discord, 09-10-2026: <i>"add an
     * option to buy multiple packs at once"</i>). Se queda puesto al cambiar de
     * expansion: es como compras, no que compras.
     */
    private int quantity = Math.max(1, Math.min(MAX_QUANTITY,
            Integer.getInteger("neo.shop.quantity", 1)));
    private final Label quantityLabel = new Label();
    private final Button fewer = new Button("\u2212");
    private final Button more = new Button("+");
    /** "Que puede salir" en el sobre elegido (Discord, 09-10-2026). Ver PackPreview. */
    private final Button preview = new Button();

    // --- "En que sobre sale una carta" (Discord, 09-10-2026) ---
    private final TextField findField = new TextField();
    private final javafx.scene.control.ContextMenu findPopup = new javafx.scene.control.ContextMenu();
    private final VBox findBanner = new VBox(4);
    /** Las cartas que salen en algun sobre de la tienda; se cargan aparte la primera vez. */
    private volatile List<String> findNames;
    private boolean findLoading;
    /** La carta buscada (por su nombre en ingles), o null. */
    private String findName;
    /** Donde sale, por codigo de expansion (solo los sobres normales: los que vende esta pestanya). */
    private final java.util.Map<String, forge.neo.quest.PackContents.Source> findOdds = new java.util.LinkedHashMap<>();

    /** Tope de sobres por compra: una caja son 36, y eso ya esta en Cajas. */
    private static final int MAX_QUANTITY = 24;

    // --- pestanyas ---
    private enum Tab { PACKS, COLLECTOR, JUMPSTART, SINGLES, PRECONS, COMMANDER, BOXES, LAIR, SELL }

    private Tab tab = Tab.PACKS;
    private final HBox tabBar = new HBox(6);
    private final StackPane page = new StackPane();

    // --- cartas sueltas ---
    private final TextField singleSearch = new TextField();
    /**
     * Como se ordenan las sueltas (itch.io, 23-10-2026: "a way to sort cards in
     * the Singles shop"). Se recuerda; la clave es la misma en Android.
     */
    private final javafx.scene.control.ComboBox<String> singleSort = new javafx.scene.control.ComboBox<>();
    private final FlowPane singleGrid = new FlowPane(10, 10);
    private final Pager singlePager;
    private final Label singleName = new Label();
    private final Label singlePrice = new Label();
    private final Button buySingle = new Button();
    private final Button buyAllSingles = new Button();
    private List<PaperCard> singles = new ArrayList<>();
    private List<PaperCard> singlesShown = new ArrayList<>();
    /**
     * Cuantas copias hay de cada impresion en el mostrador: la misma carta
     * repetida sale UNA vez con su "xN" (Discord, 15-10-2026: "instead of
     * showing X times the same card, show it only once and put a xNumber").
     */
    private final java.util.Map<PaperCard, Integer> singleCounts = new java.util.HashMap<>();
    /** Los filtros de las sueltas, como en Arena: color, tipo y si la tienes. */
    private final java.util.Set<Byte> singleColours = new java.util.HashSet<>();
    private boolean singleColourless;
    private final java.util.Set<forge.card.CardType.CoreType> singleTypes =
            java.util.EnumSet.noneOf(forge.card.CardType.CoreType.class);
    /** 0 todas, 1 solo las que no tienes, 2 solo las que ya tienes. */
    private int singleOwned;
    private final List<Button> singleFilterButtons = new ArrayList<>();
    private final ScrollPane singleScroll = new ScrollPane();
    /**
     * El ancho de carta de las sueltas: lo que quepa en el hueco (ver
     * {@link #fitSingles}), no uno fijo. Con el fijo salian dos filas
     * pequenyas y media pantalla vacia (Discord, 15-10-2026).
     */
    private double singleCardW = -1;
    private PaperCard chosenSingle;

    // --- preconstruidos y cajas ---
    //
    // Los dos mostradores son la misma pieza con distinto genero: una lista de
    // productos con su precio, uno elegido y un boton. Se comparte
    // ({@link ProductPage}) porque duplicarla es duplicar tambien el cuidado de
    // "elegir no compra", que es lo que de verdad hay que no equivocarse.
    private ProductPage<CardEdition> collectorPane;
    private ProductPage<forge.neo.quest.NeoJumpstart.Product> jumpstartPane;
    private ProductPage<forge.item.PreconDeck> preconsPane;
    /** Los mazos de Commander de Forge, enteros (09-10-2026). Ver NeoQuestShop.commanderDecks. */
    private ProductPage<forge.item.PreconDeck> commanderPane;
    private ProductPage<forge.item.SealedProduct> boxesPane;

    // --- secret lair ---
    //
    // Dos productos en la misma pestanya y con el mismo gesto: el sobre
    // sorpresa (dos exclusivas al azar del saco) y los DROPS con nombre, que
    // traen sus cartas fijas y se ven antes de pagar. Lo que se elige aqui es
    // "cual", y comprar es un segundo click aparte -- como en el resto de la
    // tienda, donde ni un click gasta creditos por si mismo.
    private final Label lairProduct = new Label();
    private final Label lairPrice = new Label();
    private final Label lairNote = new Label();
    private final Button buyLair = new Button();
    private final FlowPane lairPicker = new FlowPane(8, 8);
    private final FlowPane lairGrid = new FlowPane(10, 10);
    private final Label lairPoolCap = new Label();
    private List<PaperCard> lairDrop = new ArrayList<>();

    /** El drop elegido, o null si lo elegido es el sobre sorpresa. */
    private forge.neo.quest.SecretLairDrops.Drop chosenDrop;

    // --- vender ---
    //
    // Es el mostrador al reves y por eso se parece tanto al de sueltas: una
    // rejilla con precio, un buscador, un paginador y una carta elegida. Lo que
    // cambia es de donde sale la lista (tu coleccion, no el mostrador) y que
    // hay un segundo camino, el de golpe, para lo que sobra.
    private final TextField sellSearch = new TextField();
    /** Nombre o lo que te dan (itch.io, 23-10-2026: "sort by cost/value"). */
    private final javafx.scene.control.ComboBox<String> sellSort = new javafx.scene.control.ComboBox<>();
    private final CheckBox sellSpareOnly = new CheckBox();
    private final FlowPane sellGrid = new FlowPane(10, 10);
    private final Pager sellPager;
    private final Label sellName = new Label();
    private final Label sellPrice = new Label();
    private final Label sellNote = new Label();
    private final Button sellOne = new Button();
    private final Button sellSpare = new Button();
    private final Button sellAll = new Button();
    private final Button sellFree = new Button();
    /** Cambiar el arte de la carta elegida, pagando (NeoQuestArt). */
    private final Button sellArt = new Button();
    private final Button sellUnused = new Button();
    private final CheckBox sellFreeOnly = new CheckBox();
    private final Label sellCount = new Label();
    private final Button sellPickAll = new Button();
    private final Button sellPickNone = new Button();
    /** Ver solo lo elegido, y volver ({@link #toggleOnlyPicked}). */
    private final Button sellOnlyPicked = new Button();
    /**
     * Lo que se esta repasando con "Ver lo elegido": lo elegido AL PULSARLO,
     * o null si se ve la coleccion. Es una foto y no {@link #sellPicked}: lo
     * que se desmarca ahi se queda a la vista, sin marco, para poder volver a
     * marcarlo (si desapareciera, la rejilla se recolocaria bajo el raton y el
     * siguiente clic caeria en la carta de al lado).
     */
    private java.util.Set<PaperCard> sellReview;
    /** Buscador y filtros: fuera mientras se repasa lo elegido. */
    private FlowPane sellTools;
    private boolean sellAdvancedWasOpen;
    private CardFilterBar sellFilters;
    private List<PaperCard> sellAllCards = new ArrayList<>();
    private List<PaperCard> sellShown = new ArrayList<>();
    /** Lo elegido, en el orden en que se eligio. Puede ser de varias paginas. */
    private final java.util.Set<PaperCard> sellPicked = new java.util.LinkedHashSet<>();
    /** Donde empieza un tramo de Mayus+clic. */
    private PaperCard sellAnchor;
    /** Cuantas copias de cada impresion usan tus mazos. */
    private java.util.Map<PaperCard, Integer> sellUsed = new java.util.HashMap<>();

    public QuestShopScreen(final double cardWidth, final Actions actions) {
        this.cardWidth = cardWidth;
        this.actions = actions;
        getStyleClass().addAll("table-root", "quest");

        pager = new Pager(30, this::paintEditions);
        singlePager = new Pager(24, this::paintSingles);
        sellPager = new Pager(24, this::paintSell);

        frame.setTop(header());
        frame.setCenter(body());
        // Volver, abajo a la derecha: el mismo sitio en todas las pantallas
        // (las notas de diseño, principio 12). Antes iba en la cabecera.
        frame.setBottom(BackBar.of(actions::back));
        getChildren().addAll(frame, overlay);
        reload();

        // Intro compra la carta suelta elegida (Discord, 24-10-2026). No si se
        // esta escribiendo en un buscador ni con un dialogo abierto encima: ahi
        // Intro ya es de otro.
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() != javafx.scene.input.KeyCode.ENTER || tab != Tab.SINGLES
                    || overlay.isShowing() || buySingle.isDisabled() || getScene() == null
                    || getScene().getFocusOwner() instanceof javafx.scene.control.TextInputControl) {
                return;
            }
            ev.consume();
            takeSingle();
        });

        // Para poder capturar una pestanya concreta sin tocar el raton.
        final int forced = Integer.getInteger("neo.shop.tab", -1);
        if (forced > 0 && forced < Tab.values().length) {
            tab = Tab.values()[forced];
            for (int i = 0; i < tabBar.getChildren().size(); i++) {
                tabBar.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == forced);
            }
            showPage();
            // -Dneo.shop.pickFirst=true: con el primer producto elegido, para
            // capturar el pie con su detalle.
            if (Boolean.getBoolean("neo.shop.pickFirst")) {
                for (final ProductPage<?> p : java.util.Arrays.<ProductPage<?>>asList(
                        collectorPane, preconsPane, commanderPane, boxesPane)) {
                    if (p != null) {
                        p.pickFirst();
                        // -Dneo.shop.openPreview=true: y pulsa "Ver las cartas" /
                        // "Que puede salir" del elegido, como el jugador.
                        // Con la pantalla ya medida: el visor se dimensiona con ella.
                        if (Boolean.getBoolean("neo.shop.openPreview") && p.previewIt.isVisible()) {
                            final javafx.animation.PauseTransition later =
                                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(1500));
                            later.setOnFinished(ev -> p.previewIt.fire());
                            later.play();
                        }
                    }
                }
            }
            // Y en la de vender, con N cartas ya elegidas y los filtros
            // desplegados: es el estado que hay que ver y el que no sale solo.
            final int picks = Integer.getInteger("neo.shop.sellPick", 0);
            if (tab == Tab.SELL && picks > 0) {
                sellPicked.addAll(sellShown.subList(0, Math.min(picks, sellShown.size())));
                sellFilters.advanced().setVisible(true);
                sellFilters.advanced().setManaged(true);
                markPicked();
                refreshSell();
                // -Dneo.shop.sellReview=true: y "Ver lo elegido" pulsado.
                if (Boolean.getBoolean("neo.shop.sellReview")) {
                    toggleOnlyPicked();
                }
            }
        }

        // -Dneo.shop.art="Llanowar Elves" (o "*", la primera que se pueda): en la pestanya de vender, abre el
        // cambio de arte de esa carta; con -Dneo.shop.artPick=N elige ademas la
        // impresion N y sale la pregunta. Para capturarlo sin raton (11-10-2026).
        final String artAtStart = System.getProperty("neo.shop.art");
        if (artAtStart != null && tab == Tab.SELL) {
            final javafx.animation.PauseTransition wait =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
            wait.setOnFinished(e -> {
                for (final PaperCard c : sellShown) {
                    if (("*".equals(artAtStart) || c.getName().equalsIgnoreCase(artAtStart))
                            && forge.neo.quest.NeoQuestArt.canChange(c)) {
                        sellPicked.clear();
                        sellPicked.add(c);
                        markPicked();
                        refreshSell();
                        final int n = Integer.getInteger("neo.shop.artPick", 0);
                        final List<PaperCard> arts = forge.neo.quest.NeoQuestArt.printingsOf(c);
                        if (n > 0 && n <= arts.size()) {
                            askArt(c, arts.get(n - 1), NeoQuestSell.owned(c));
                        } else {
                            chooseArt(c);
                        }
                        return;
                    }
                }
                System.out.println("[tienda] -Dneo.shop.art: no tienes \"" + artAtStart + "\" o no tiene otro arte");
            });
            wait.play();
        }

        // Para capturar el buscador y "Que puede salir" sin raton (09-10-2026):
        // -Dneo.shop.find="Beza, the Bounding Spring" busca esa carta, y
        // -Dneo.shop.preview=BLB (o collector:BLB) abre lo que puede salir en
        // ese sobre. Con un respiro: el panel se mide con el tamanyo de la
        // pantalla, que al construirla todavia es cero.
        final String findAtStart = System.getProperty("neo.shop.find");
        final String previewAtStart = System.getProperty("neo.shop.preview");
        if (findAtStart != null || previewAtStart != null) {
            final javafx.animation.PauseTransition wait =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
            wait.setOnFinished(e -> {
                if (findAtStart != null) {
                    findNames = forge.neo.quest.PackContents.namesIn(
                            NeoQuestShop.editions(), NeoQuestShop.collectorEditions());
                    findCard(findAtStart);
                }
                if (previewAtStart != null) {
                    final boolean collector = previewAtStart.startsWith("collector:");
                    final CardEdition e2 = forge.model.FModel.getMagicDb().getEditions()
                            .get(collector ? previewAtStart.substring(10) : previewAtStart);
                    if (e2 != null && !collector) {
                        choose(e2);
                    }
                    showPreview(e2 != null ? e2 : chosen, collector);
                }
            });
            wait.play();
        }

        // Click derecho: la carta a tamanyo de lectura. Lo que sale de un sobre
        // se ve en miniatura, y mirar QUE te ha tocado es justo por lo que has
        // pagado.
        CardZoom.install(this);
    }

    private Region header() {
        final Label title = new Label(NeoText.get("shop.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("shop.subtitle"));
        sub.getStyleClass().add("home-subtitle");

        credits.getStyleClass().addAll("stat-value", "stat-credits");

        final Label cap = new Label(NeoText.get("quest.credits"));
        cap.getStyleClass().add("caption");
        final VBox money = new VBox(-2, credits, cap);
        money.setAlignment(Pos.CENTER);
        money.getStyleClass().add("stat-tile");
        money.setPadding(new Insets(8, 18, 8, 18));

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(14, new VBox(2, title, sub), gap, money);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(18, 28, 8, 30));
        return row;
    }

    private Region body() {
        // --- expansiones ---
        search.setPromptText(NeoText.get("shop.search"));
        search.getStyleClass().add("text-input");
        search.setPrefColumnCount(20);
        search.textProperty().addListener((o, was, is) -> reload());

        editionGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane sets = new ScrollPane(editionGrid);
        sets.getStyleClass().add("dialog-scroll");
        sets.setFitToWidth(true);
        sets.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sets.setPrefHeight(UiScale.px(190));

        final Label cap = new Label(NeoText.get("shop.editions"));
        cap.getStyleClass().add("caption");

        // --- el sobre elegido ---
        packName.getStyleClass().add("quest-deck-name");
        packPrice.getStyleClass().add("shop-price");
        buy.getStyleClass().add("btn-primary");
        buy.setMinWidth(Region.USE_PREF_SIZE);
        buy.setOnAction(e -> open());

        // Cuantos: pegado al boton de comprar, que es donde ya esta la mano.
        quantityLabel.getStyleClass().add("quest-deck-name");
        quantityLabel.setMinWidth(UiScale.px(44));
        quantityLabel.setAlignment(Pos.CENTER);
        fewer.getStyleClass().add("segment");
        more.getStyleClass().add("segment");
        fewer.setOnAction(e -> setQuantity(quantity - 1));
        more.setOnAction(e -> setQuantity(quantity + 1));
        final HBox stepper = new HBox(4, fewer, quantityLabel, more);
        stepper.setAlignment(Pos.CENTER);
        stepper.setMinWidth(Region.USE_PREF_SIZE);
        final javafx.scene.control.Tooltip howMany = new javafx.scene.control.Tooltip(NeoText.get("shop.quantity"));
        javafx.scene.control.Tooltip.install(stepper, howMany);
        fewer.setTooltip(howMany);
        more.setTooltip(howMany);

        // QUE PUEDE SALIR: antes de comprar, que es cuando se decide.
        preview.setText(NeoText.get("shop.preview"));
        preview.setId("shop-preview");
        preview.getStyleClass().add("btn-secondary");
        preview.setMinWidth(Region.USE_PREF_SIZE);
        preview.setOnAction(e -> showPreview(chosen, false));

        final Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);
        final HBox packRow = new HBox(16, new VBox(2, packName, packPrice), gap2, preview, stepper, buy);
        packRow.setAlignment(Pos.CENTER_LEFT);
        packRow.getStyleClass().add("stat-tile");
        packRow.setPadding(new Insets(14, 18, 14, 18));

        affordableOnly.setText(NeoText.get("shop.affordable"));
        affordableOnly.getStyleClass().add("shop-filter");
        affordableOnly.setOnAction(e -> {
            pager.reset();
            reload();
        });
        final Region gapTools = new Region();
        HBox.setHgrow(gapTools, Priority.ALWAYS);
        buildFinder();
        final HBox tools = new HBox(12, search, affordableOnly, gapTools, findField);
        tools.setAlignment(Pos.CENTER_LEFT);

        final VBox content = new VBox(12, cap, findBanner, tools, sets, pager, packRow);

        final ScrollPane sp = new ScrollPane(content);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        packsPage = sp;

        // El visor de expansiones se queda con el alto que sobre, y la pagina
        // es lo que quepa en el (Pager.fitTo). Con 190 px fijos se veian dos
        // filas y media y el resto de la pantalla vacio, en 1080p y mas en 2K
        // (24-09-2026). Nunca menos de esos 190: en una ventana baja manda el
        // desplazamiento de la pagina entera, como antes.
        final Runnable grow = () -> {
            final double vh = sp.getViewportBounds().getHeight();
            if (vh <= 0 || content.getWidth() <= 0) {
                return;
            }
            // Lo que ocupa todo lo demas, sumado hermano a hermano: restar el
            // visor del total no vale, porque el total se entera antes que el
            // visor de su nuevo alto y la cuenta oscila.
            double others = content.getPadding().getTop() + content.getPadding().getBottom();
            int managed = 0;
            for (final javafx.scene.Node n : content.getChildren()) {
                if (n.isManaged()) {
                    managed++;
                    if (n != sets) {
                        others += n.prefHeight(content.getWidth());
                    }
                }
            }
            others += content.getSpacing() * Math.max(0, managed - 1);
            final double want = Math.max(UiScale.px(190), vh - others - 2);
            if (Math.abs(want - sets.getPrefHeight()) > 1) {
                sets.setPrefHeight(want);
            }
        };
        sp.viewportBoundsProperty().addListener((o, was, is) -> grow.run());
        content.heightProperty().addListener((o, was, is) -> grow.run());
        pager.fitTo(sets, editionGrid, UiScale.px(190), UiScale.px(64), 12);

        tabBar.setAlignment(Pos.CENTER_LEFT);
        tabBar.getChildren().addAll(
                tabButton(NeoText.get("shop.tab.packs"), Tab.PACKS),
                tabButton(NeoText.get("shop.tab.collector"), Tab.COLLECTOR),
                tabButton(NeoText.get("shop.tab.jumpstart"), Tab.JUMPSTART),
                tabButton(NeoText.get("shop.tab.singles"), Tab.SINGLES),
                tabButton(NeoText.get("shop.tab.precons"), Tab.PRECONS),
                tabButton(NeoText.get("shop.tab.commander"), Tab.COMMANDER),
                tabButton(NeoText.get("shop.tab.boxes"), Tab.BOXES),
                tabButton(NeoText.get("shop.tab.lair"), Tab.LAIR),
                tabButton(NeoText.get("shop.tab.sell"), Tab.SELL));
        // En una Quest limitada a unas expansiones sin Secret Lair, su pestanya
        // sobra: no hay nada que vender ahi. Se esconde sin quitarla, porque
        // las pestanyas se casan con Tab por su posicion (neo.shop.tab).
        if (!NeoQuestShop.secretLairAllowed()) {
            final javafx.scene.Node lair = tabBar.getChildren().get(Tab.LAIR.ordinal());
            lair.setVisible(false);
            lair.setManaged(false);
        }

        page.getChildren().add(packsPage);
        VBox.setVgrow(page, Priority.ALWAYS);

        final VBox all = new VBox(10, tabBar, page);
        all.setPadding(new Insets(4, 30, 20, 30));
        return all;
    }

    private Region packsPage;
    private Region singlesPage;
    private Region lairPage;
    private Region sellPage;

    /** Una pestanya del mostrador. */
    private Button tabButton(final String label, final Tab which) {
        final Button b = new Button(label);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.pseudoClassStateChanged(SELECTED, tab == which);
        b.setOnAction(e -> {
            tab = which;
            for (final javafx.scene.Node n : tabBar.getChildren()) {
                n.pseudoClassStateChanged(SELECTED, n == b);
            }
            showPage();
        });
        return b;
    }

    private void showPage() {
        switch (tab) {
            case SINGLES:
                if (singlesPage == null) {
                    singlesPage = buildSinglesPage();
                }
                page.getChildren().setAll(singlesPage);
                reloadSingles();
                break;
            case COLLECTOR:
                if (collectorPane == null) {
                    collectorPane = new ProductPage<CardEdition>(
                            NeoText.get("shop.collector.caption"),
                            NeoText.get("shop.collector.empty"),
                            NeoText.get("shop.pickCollector"),
                            NeoQuestShop::collectorEditions,
                            CardEdition::getName,
                            e -> NeoQuestShop.buyCollector(e, NeoQuestShop.collectorBooster(e)),
                            NeoQuestShop::collectorPrice,
                            NeoText.get("shop.collector.opened"))
                            .showCode(CardEdition::getCode)
                            // Las hojas de arte que trae, y no es un detalle
                            // tecnico: "borderless, extended art" es la razon
                            // por la que este sobre cuesta el triple.
                            .showNote(QuestShopScreen::collectorLabel)
                            // Que puede salir en el de colector, igual que en
                            // el normal (Discord, 09-10-2026).
                            .withPreview(e -> showPreview(e, true))
                            // Y varios de una vez, con "otros N" al abrirlos,
                            // como los normales (Discord, 11-10-2026).
                            .withQuantity(NeoQuestShop::buyCollector);
                }
                page.getChildren().setAll(collectorPane);
                collectorPane.reload();
                break;
            case JUMPSTART:
                // Los Jumpstart de Forge, un tema por casilla (Discord,
                // 20-10-2026: Hei Bai no estaba en ningun sobre). Ver NeoJumpstart.
                if (jumpstartPane == null) {
                    jumpstartPane = new ProductPage<forge.neo.quest.NeoJumpstart.Product>(
                            NeoText.get("shop.jumpstart.caption"),
                            NeoText.get("shop.jumpstart.empty"),
                            NeoText.get("shop.pickJumpstart"),
                            forge.neo.quest.NeoJumpstart::products,
                            p -> p.name,
                            NeoQuestShop::buyJumpstart,
                            NeoQuestShop::jumpstartPrice,
                            NeoText.get("shop.jumpstart.opened"))
                            // Un sobre de verdad: trae UN tema al azar, que se
                            // descubre al abrirlo. Aqui solo se dice cuales puede ser.
                            .showFace(forge.neo.quest.NeoJumpstart.Product::face)
                            .showCode(p -> p.setCode)
                            .showNote(p -> NeoText.get("shop.jumpstart.themes", p.themes.size()))
                            .showDetail(forge.neo.quest.NeoJumpstart.Product::themeList);
                }
                page.getChildren().setAll(jumpstartPane);
                jumpstartPane.reload();
                break;
            case PRECONS:
                if (preconsPane == null) {
                    preconsPane = new ProductPage<forge.item.PreconDeck>(
                            NeoText.get("shop.precons.caption"),
                            NeoText.get("shop.precons.empty"),
                            NeoText.get("shop.pickPrecon"),
                            () -> NeoQuestShop.onSale(forge.item.PreconDeck.class),
                            forge.item.PreconDeck::getName,
                            NeoQuestShop::buyPrecon,
                            NeoQuestShop::priceOfItem,
                            NeoText.get("shop.boughtPrecon"))
                            // La CARTA del mazo: el comandante si lo tiene, y
                            // si no, la carta gorda de la que va. Por el nombre
                            // no se sabe ni de que color es.
                            .showFace(NeoQuestShop::faceOf)
                            .showCode(forge.item.PreconDeck::getEdition)
                            // Y de que va, con las palabras del propio mazo.
                            .showDetail(forge.item.PreconDeck::getDescription)
                            // Y QUE CARTAS trae (Discord, 27-10-2026).
                            .withPreview(this::showDeck, NeoText.get("shop.deckPreview"));
                }
                page.getChildren().setAll(preconsPane);
                preconsPane.reload();
                break;
            case COMMANDER:
                // Los mazos de Commander de Forge (Discord, 09-10-2026: "where
                // are the commander sets in quest?"): el de 100 cartas entero,
                // con su comandante de cara y buscable por el.
                if (commanderPane == null) {
                    commanderPane = new ProductPage<forge.item.PreconDeck>(
                            NeoText.get("shop.cmdDecks.caption"),
                            NeoText.get("shop.cmdDecks.empty"),
                            NeoText.get("shop.pickPrecon"),
                            NeoQuestShop::commanderDecks,
                            QuestShopScreen::deckTitle,
                            NeoQuestShop::buyCommanderDeck,
                            NeoQuestShop::commanderDeckPrice,
                            NeoText.get("shop.boughtPrecon"))
                            .showFace(NeoQuestShop::faceOf)
                            .showCode(forge.item.PreconDeck::getEdition)
                            .showNote(QuestShopScreen::commanderLine)
                            .showDetail(QuestShopScreen::commanderDetail)
                            .withPreview(this::showDeck, NeoText.get("shop.deckPreview"));
                }
                page.getChildren().setAll(commanderPane);
                final long t0 = System.nanoTime();
                commanderPane.reload();
                System.out.printf(Locale.ROOT, "[tienda] mazos de Commander: %d en %d ms%n",
                        NeoQuestShop.commanderDecks().size(), (System.nanoTime() - t0) / 1_000_000);
                break;
            case BOXES:
                if (boxesPane == null) {
                    boxesPane = new ProductPage<forge.item.SealedProduct>(
                            NeoText.get("shop.boxes.caption"),
                            NeoText.get("shop.boxes.empty"),
                            NeoText.get("shop.pickBox"),
                            NeoQuestShop::boxes,
                            NeoQuestShop::productName,
                            NeoQuestShop::buyBox,
                            NeoQuestShop::priceOfItem,
                            NeoText.get("shop.boughtBox"))
                            .showCode(forge.item.SealedProduct::getEdition)
                            .showNote(b -> b.getItemType());
                }
                page.getChildren().setAll(boxesPane);
                boxesPane.reload();
                break;
            case LAIR:
                if (lairPage == null) {
                    lairPage = buildLairPage();
                }
                page.getChildren().setAll(lairPage);
                refreshLair();
                break;
            case SELL:
                if (sellPage == null) {
                    sellPage = buildSellPage();
                }
                page.getChildren().setAll(sellPage);
                reloadSell();
                break;
            case PACKS:
            default:
                page.getChildren().setAll(packsPage);
                break;
        }
    }

    // ---------------------------------------------------------------
    // Cartas sueltas
    // ---------------------------------------------------------------

    /**
     * El mostrador de cartas sueltas.
     *
     * <p>Se pintan como cartas y no como una lista de nombres: aqui se compra
     * por lo que <b>es</b> la carta, y a un nombre suelto hay que hacerle sitio
     * en la cabeza antes de decidir. El precio va debajo de cada una, con el
     * mismo criterio que las expansiones — sin verlo hay que ir clicando una
     * por una para saber cual te puedes permitir.
     */
    private Region buildSinglesPage() {
        singleSearch.setPromptText(NeoText.get("shop.searchCard"));
        singleSearch.getStyleClass().add("text-input");
        singleSearch.setPrefColumnCount(20);
        singleSearch.textProperty().addListener((o, was, is) -> {
            singlePager.reset();
            reloadSingles();
        });

        // Los filtros, en la misma fila que el buscador y en una FlowPane: si
        // no caben (ventana estrecha, escala grande) bajan de linea en vez de
        // salirse por la derecha.
        final FlowPane tools = new FlowPane(12, 8);
        tools.setAlignment(Pos.CENTER_LEFT);
        singleSort.getItems().setAll(NeoText.get("sort.wantedFirst"), NeoText.get("sort.priceLow"),
                NeoText.get("sort.priceHigh"), NeoText.get("sort.manaValue"), NeoText.get("sort.rarity"));
        singleSort.getSelectionModel().select(Math.max(0, Math.min(4,
                forge.neo.NeoSettings.getInt(SINGLES_SORT, 0))));
        singleSort.setId("shop-singles-sort");
        singleSort.setOnAction(e -> {
            forge.neo.NeoSettings.set(SINGLES_SORT,
                    Integer.toString(singleSort.getSelectionModel().getSelectedIndex()));
            singlePager.reset();
            reloadSingles();
        });
        final HBox singleSortBox = new HBox(6, new Label(NeoText.get("sort.caption")), singleSort);
        singleSortBox.setAlignment(Pos.CENTER_LEFT);
        tools.getChildren().addAll(singleSearch, singleColourRow(), singleTypeRow(), singleOwnedRow(), singleSortBox);

        singleGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = singleScroll;
        scroll.setContent(singleGrid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // Alto PEDIDO pequenyo: crece con lo que sobre (Vgrow) y no con lo que
        // mida la rejilla. Si pidiera el alto de su contenido, cartas mas
        // grandes agrandarian el visor, el visor agrandaria las cartas... y la
        // pagina se saldria de la pantalla, cortada por arriba.
        scroll.setPrefViewportHeight(UiScale.px(120));
        scroll.setMinViewportHeight(UiScale.px(120));
        scroll.viewportBoundsProperty().addListener((o, was, is) -> fitSingles());

        singleName.getStyleClass().add("quest-deck-name");
        singlePrice.getStyleClass().add("shop-price");
        buySingle.getStyleClass().add("btn-primary");
        buySingle.setMinWidth(Region.USE_PREF_SIZE);
        buySingle.setText(NeoText.get("shop.buyCard"));
        buySingle.setOnAction(e -> takeSingle());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        // El paginador va en la barra de comprar y sin el rotulo de arriba: a
        // 1080p el hueco de las cartas era de 300 px y solo cabia una fila de
        // las grandes (15-10-2026). Cada linea que no esta es casi una fila.
        // COMPRAR TODO LO QUE SE VE (Discord, 24-10-2026: "a buy all button
        // ... I'm going to get a RSI clicking on everything"): una copia de
        // cada carta que deja el filtro, preguntando antes cuantas y cuanto.
        buyAllSingles.getStyleClass().add("btn-secondary");
        buyAllSingles.setMinWidth(Region.USE_PREF_SIZE);
        buyAllSingles.setText(NeoText.get("shop.buyAll"));
        buyAllSingles.setOnAction(e -> askBuyAllSingles());
        final HBox row = new HBox(16, singlePager, new VBox(2, singleName, singlePrice), gap, buyAllSingles, buySingle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stat-tile");
        row.setPadding(new Insets(10, 18, 10, 18));

        final VBox box = new VBox(10, tools, scroll, row);
        return box;
    }

    /** Los nombres que ya tienes: lo que no este aqui lleva "Nueva". */
    private java.util.Set<String> ownedNames = java.util.Set.of();

    /** Cuantas tienes de cada carta, por nombre: la pastilla "Tienes N". */
    private java.util.Map<String, Integer> ownedByName = java.util.Map.of();

    /**
     * Lo que te falta en tus mazos (por nombre en minusculas): esas sueltas
     * llevan "Para tu mazo", salen LAS PRIMERAS y tienen su filtro. Ver
     * {@code NeoQuest.wantedByName}.
     */
    private java.util.Map<String, Integer> wanted = java.util.Map.of();
    /** Los artes deseados que te faltan: solo esa impresion cuenta. */
    private java.util.Set<String> wantedArts = java.util.Set.of();

    private boolean isWanted(final PaperCard c) {
        return NeoQuestShop.isWanted(c, wanted, wantedArts);
    }

    /** Los botones de "Todas / Nuevas / Las tengo / Para mis mazos". */
    private final List<Button> ownedButtons = new ArrayList<>();

    /**
     * Abre las sueltas con solo lo que te falta en tus mazos: es lo que hace el
     * aviso del boton de la tienda en el cuartel.
     */
    public void openWanted() {
        tab = Tab.SINGLES;
        final int index = Tab.SINGLES.ordinal();
        for (int i = 0; i < tabBar.getChildren().size(); i++) {
            tabBar.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == index);
        }
        singleOwned = 3;
        for (int i = 0; i < ownedButtons.size(); i++) {
            ownedButtons.get(i).pseudoClassStateChanged(SELECTED, i == singleOwned);
        }
        showPage();
        singleFiltersChanged();
    }

    private void reloadSingles() {
        singles = NeoQuestShop.singles();
        ownedNames = forge.neo.quest.NeoQuestCollection.ownedNames();
        ownedByName = ownedCounts();
        wanted = forge.neo.quest.NeoQuest.wantedByName();
        wantedArts = forge.neo.quest.NeoQuest.wantedArts();
        final String q = singleSearch.getText() == null ? ""
                : singleSearch.getText().trim().toLowerCase(Locale.ROOT);
        singlesShown = new ArrayList<>();
        singleCounts.clear();
        for (final PaperCard c : singles) {
            // La misma impresion, una vez: las demas copias van en su "xN".
            if (singleCounts.merge(c, 1, Integer::sum) > 1) {
                continue;
            }
            // Se busca por el nombre que se VE, que en castellano no es el
            // ingles. Es la misma regla que en el deck builder.
            if ((q.isEmpty() || matchesSearch(c, q)) && passesSingleFilters(c)) {
                singlesShown.add(c);
            }
        }
        singlesShown.sort(singlesOrder(singleSort.getSelectionModel().getSelectedIndex()));
        singlePager.setTotal(singlesShown.size());
        if (chosenSingle != null && !singlesShown.contains(chosenSingle)) {
            chosenSingle = null;
        }
        paintSingles();
        refreshSingle();
        refreshMoney();
    }

    /**
     * Si el TIPO o el TEXTO de la carta llevan lo buscado, en ingles o
     * traducido (Discord, 16-10-2026: <i>"searching for things written in the
     * card - such as deathtouch or flash"</i>). Como el buscador del
     * constructor: es como se busca cuando no sabes el nombre de nada. El
     * mostrador son unos cientos de cartas, asi que se mira todo siempre, sin
     * interruptor.
     */
    private static boolean matchesSearch(final PaperCard c, final String q) {
        // Cada palabra en cualquiera de estos, en cualquier orden
        // (forge.neo.deck.SearchTerms, 16-10-2026): "flying vigilance".
        final String shown = forge.neo.card.CardText.nameOf(c).toLowerCase(Locale.ROOT);
        final String name = c.getName().toLowerCase(Locale.ROOT);
        if (c.getRules() == null) {
            return forge.neo.deck.SearchTerms.matches(q, shown, name);
        }
        final String type = c.getRules().getType() == null ? ""
                : c.getRules().getType().toString().toLowerCase(Locale.ROOT);
        final String oracle = c.getRules().getOracleText() == null ? ""
                : c.getRules().getOracleText().toLowerCase(Locale.ROOT);
        final String typeShown = forge.neo.card.CardText.typeOf(c).toLowerCase(Locale.ROOT);
        String translated = null;
        try {
            translated = forge.util.CardTranslation.getTranslatedOracle(c.getName());
        } catch (final RuntimeException ignored) {
            // sin traduccion
        }
        return forge.neo.deck.SearchTerms.matches(q, shown, name, type, oracle, typeShown,
                translated == null ? null : translated.toLowerCase(Locale.ROOT));
    }

    private void paintSingles() {
        singleGrid.getChildren().clear();
        if (singlesShown.isEmpty()) {
            final Label empty = new Label(NeoText.get("shop.singles.empty"));
            empty.getStyleClass().add("home-subtitle");
            singleGrid.getChildren().add(empty);
            return;
        }
        for (int i = singlePager.from();
                i < Math.min(singlesShown.size(), singlePager.to()); i++) {
            singleGrid.getChildren().add(singleTile(singlesShown.get(i)));
        }
    }

    /** La clave del orden de las sueltas (la misma en Android). */
    public static final String SINGLES_SORT = "quest.singlesSort";

    /**
     * El orden de las sueltas: 0, lo que te falta en tus mazos primero (con
     * varias paginas, no tener que pasarlas buscandolo era justo lo que se
     * pedia) y luego por precio; 1 y 2, por precio; 3, por coste de mana; 4,
     * por rareza (las miticas primero). A igualdad, por nombre.
     */
    private java.util.Comparator<PaperCard> singlesOrder(final int choice) {
        final java.util.Comparator<PaperCard> byName = java.util.Comparator.comparing(
                c -> forge.neo.card.CardText.nameOf(c), String.CASE_INSENSITIVE_ORDER);
        final java.util.Comparator<PaperCard> byPrice = java.util.Comparator.comparingInt(NeoQuestShop::priceOfCard);
        switch (choice) {
            case 1:
                return byPrice.thenComparing(byName);
            case 2:
                return byPrice.reversed().thenComparing(byName);
            case 3:
                return java.util.Comparator.comparingInt((PaperCard c) -> c.getRules().getManaCost().getCMC())
                        .thenComparing(byName);
            case 4:
                return java.util.Comparator.comparingInt((PaperCard c) -> -rarityRank(c.getRarity()))
                        .thenComparing(byName);
            default:
                return java.util.Comparator.comparingInt((PaperCard c) -> isWanted(c) ? 0 : 1)
                        .thenComparing(byPrice).thenComparing(byName);
        }
    }

    /** Mitica arriba, comun abajo; lo raro (especial, basica) en medio-bajo. */
    public static int rarityRank(final forge.card.CardRarity r) {
        if (r == null) {
            return 0;
        }
        switch (r) {
            case MythicRare:
                return 5;
            case Rare:
                return 4;
            case Special:
                return 3;
            case Uncommon:
                return 2;
            case Common:
                return 1;
            default:
                return 0;
        }
    }

    // --- los filtros de las sueltas ---

    private boolean passesSingleFilters(final PaperCard c) {
        final forge.card.CardRules rules = c.getRules();
        if (!singleColours.isEmpty() || singleColourless) {
            final forge.card.ColorSet colour = rules.getColor();
            boolean ok = singleColourless && colour.isColorless();
            for (final byte b : singleColours) {
                ok |= colour.hasAnyColor(b);
            }
            if (!ok) {
                return false;
            }
        }
        if (!singleTypes.isEmpty()) {
            boolean ok = false;
            for (final forge.card.CardType.CoreType t : singleTypes) {
                ok |= rules.getType().hasType(t);
            }
            if (!ok) {
                return false;
            }
        }
        if (singleOwned == 3) {
            return isWanted(c);
        }
        if (singleOwned != 0) {
            final boolean fresh = forge.neo.quest.NeoQuestCollection.isNew(c, ownedNames);
            return singleOwned == 1 ? fresh : !fresh;
        }
        return true;
    }

    private void singleFiltersChanged() {
        singlePager.reset();
        reloadSingles();
    }

    /** Los cinco colores e incolora, los mismos botones que el constructor. */
    private HBox singleColourRow() {
        final HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        final String[] letters = {"W", "U", "B", "R", "G"};
        final byte[] masks = {forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE,
                forge.card.MagicColor.BLACK, forge.card.MagicColor.RED, forge.card.MagicColor.GREEN};
        for (int i = 0; i < letters.length; i++) {
            final byte colour = masks[i];
            final Button b = new Button(letters[i]);
            b.getStyleClass().addAll("segment", "colour-filter", "mana-" + letters[i].toLowerCase(Locale.ROOT));
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> {
                if (!singleColours.remove(colour)) {
                    singleColours.add(colour);
                }
                b.pseudoClassStateChanged(SELECTED, singleColours.contains(colour));
                singleFiltersChanged();
            });
            row.getChildren().add(b);
        }
        final Button colourless = new Button("C");
        colourless.getStyleClass().addAll("segment", "colour-filter", "mana-c");
        colourless.setMinWidth(Region.USE_PREF_SIZE);
        colourless.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.colorless")));
        colourless.setOnAction(e -> {
            singleColourless = !singleColourless;
            colourless.pseudoClassStateChanged(SELECTED, singleColourless);
            singleFiltersChanged();
        });
        row.getChildren().add(colourless);
        return row;
    }

    /** Los tipos, con su nombre traducido del motor, como en el constructor. */
    private HBox singleTypeRow() {
        final HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        for (final forge.card.CardType.CoreType t : new forge.card.CardType.CoreType[] {
                forge.card.CardType.CoreType.Creature, forge.card.CardType.CoreType.Instant,
                forge.card.CardType.CoreType.Sorcery, forge.card.CardType.CoreType.Artifact,
                forge.card.CardType.CoreType.Enchantment, forge.card.CardType.CoreType.Planeswalker,
                forge.card.CardType.CoreType.Land}) {
            final Button b = new Button(t.getTranslatedName());
            b.getStyleClass().addAll("segment", "type-filter");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> {
                if (!singleTypes.remove(t)) {
                    singleTypes.add(t);
                }
                b.pseudoClassStateChanged(SELECTED, singleTypes.contains(t));
                singleFiltersChanged();
            });
            row.getChildren().add(b);
        }
        return row;
    }

    /** Todas, solo las nuevas o solo las que ya tienes. */
    private HBox singleOwnedRow() {
        final HBox row = new HBox(4);
        row.setAlignment(Pos.CENTER_LEFT);
        final String[] keys = {"shop.singles.all", "shop.singles.newOnly", "shop.singles.ownedOnly",
                "shop.singles.wantedOnly"};
        final List<Button> group = ownedButtons;
        group.clear();
        for (int i = 0; i < keys.length; i++) {
            final int which = i;
            final Button b = new Button(NeoText.get(keys[i]));
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, which == singleOwned);
            b.setOnAction(e -> {
                singleOwned = which;
                for (final Button o : group) {
                    o.pseudoClassStateChanged(SELECTED, o == b);
                }
                singleFiltersChanged();
            });
            group.add(b);
            row.getChildren().add(b);
        }
        return row;
    }

    /** Margen de cada casilla, a cada lado. */
    private static final double SINGLE_PAD = 6;

    /**
     * Las cartas lo mas grandes que quepan, y la pagina lo que quepa con ese
     * tamanyo (Discord, 15-10-2026: "re-layout ... similar to MTGA, where it is
     * easier to read the cards"). Se prueba con 1, 2, 3... filas y se queda la
     * primera con la que entran al menos 20 en la pagina (como las 21 de Arena; con 10, a 1080p y 4K de verdad salian solo 14 enormes): cuantas menos filas,
     * mas grande la carta. Las casillas tienen tamanyo FIJO y la cuenta deja
     * unos px de margen, asi que la pagina cabe entera sin barra y sin que la
     * FlowPane mande una carta a una fila de mas.
     */
    private void fitSingles() {
        final javafx.geometry.Bounds b = singleScroll.getViewportBounds();
        // Margen de sobra: calculada al pixel, la casilla con su borde de CSS se
        // pasaba un pelo y la pagina salia cortada por arriba (visto a 2K).
        final double width = b.getWidth() - 10;
        final double height = b.getHeight() - 14;
        if (width <= 0 || height <= 0) {
            return;
        }
        final double gap = singleGrid.getHgap();
        final double under = UiScale.px(26);
        // Mas un margen: la casilla lleva su borde de CSS, y una que mida un
        // pelo mas de lo calculado empuja la pagina y la corta por arriba.
        final double extra = SINGLE_PAD * 2 + 2 + under + UiScale.px(12);
        // El minimo NO crece con la escala: a 2K el resto de la pantalla ya
        // crece y deja menos hueco, y un minimo escalado dejaba una sola fila
        // de cinco cartas enormes.
        final double min = 72;
        final double max = UiScale.px(250);
        double w = min;
        int cols = 1;
        int rows = 1;
        for (int r = 1; r <= 6; r++) {
            final double tileH = (height - gap * (r - 1)) / r;
            final double cw = Math.min(max, (tileH - extra) / CardNode.ASPECT);
            if (cw < min) {
                break;
            }
            final int c = (int) Math.floor((width + gap) / (cw + SINGLE_PAD * 2 + gap));
            if (c < 1) {
                continue;
            }
            w = cw;
            cols = c;
            rows = r;
            if (c * r >= 20) {
                break;
            }
        }
        final int size = Math.max(1, cols * rows);
        if (Math.abs(w - singleCardW) < 1 && size == singlePager.getPageSize()) {
            return;
        }
        singleCardW = w;
        final int first = singlePager.from();
        singlePager.setPageSize(size);
        singlePager.goToItem(first);
        singlePager.setTotal(singlesShown.size());
        paintSingles();
    }

    private Region singleTile(final PaperCard card) {
        final double w = singleCardW > 0 ? singleCardW : cardWidth * 0.82;
        final CardNode node = new CardNode(w);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        // Sin levantarse al pasar el raton: la carta crecia, empujaba la pagina
        // y la cortaba por arriba. Aqui se elige con clic y se lee con el
        // derecho.
        node.setHoverEnabled(false);
        node.setCard(forge.game.card.CardView.getCardForUi(card));
        // Las copias de mas, en una pastilla abajo a la derecha (como Arena).
        final StackPane face = new StackPane(node);
        // Del tamanyo calculado, para que la cuenta de fitSingles case.
        final double faceH = w * CardNode.ASPECT;
        face.setMinSize(w, faceH);
        face.setPrefSize(w, faceH);
        face.setMaxSize(w, faceH);
        final int copies = singleCounts.getOrDefault(card, 1);
        if (copies > 1) {
            final Label count = new Label("\u00d7" + copies);
            count.getStyleClass().add("shop-count");
            StackPane.setAlignment(count, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(count, new Insets(0, 4, 4, 0));
            face.getChildren().add(count);
        }
        // Cuantas TIENES ya, abajo a la izquierda y en verde (Discord,
        // 15-10-2026: "a little indication of how many copies of the cards we
        // already own ... easier than having to go back and forth to
        // collection"). Por nombre, como "Nueva": cualquier impresion cuenta.
        // Te falta en algun mazo: arriba a la izquierda, en ambar, como el
        // "falta N" del constructor.
        if (isWanted(card)) {
            final Label want = new Label(NeoText.get("shop.wanted"));
            want.getStyleClass().add("shop-wanted");
            want.setMinWidth(Region.USE_PREF_SIZE);
            StackPane.setAlignment(want, Pos.TOP_LEFT);
            // Bajo la franja del nombre, sobre el arte: arriba del todo tapaba el nombre.
            StackPane.setMargin(want, new Insets(Math.round(w * 0.13), 0, 0, 4));
            face.getChildren().add(want);
        }
        final int have = ownedByName.getOrDefault(card.getName(), 0);
        if (have > 0) {
            final Label owned = new Label(NeoText.get("shop.owned", have));
            owned.getStyleClass().add("shop-owned");
            owned.setMinWidth(Region.USE_PREF_SIZE);
            StackPane.setAlignment(owned, Pos.BOTTOM_LEFT);
            StackPane.setMargin(owned, new Insets(0, 0, 4, 4));
            face.getChildren().add(owned);
        }

        final int price = NeoQuestShop.priceOfCard(card);
        final Label cost = new Label(NeoText.get("shop.credits.short", price));
        cost.getStyleClass().add(NeoQuest.credits() >= price ? "set-price-ok" : "set-price-no");

        // "Nueva": no tienes ninguna con este nombre (Discord, 07-10-2026: "a
        // New tag to singles in the shop that aren't in your collection").
        final HBox under = new HBox(6, cost);
        under.setAlignment(Pos.CENTER);
        if (forge.neo.quest.NeoQuestCollection.isNew(card, ownedNames)) {
            final Label fresh = new Label(NeoText.get("shop.new"));
            fresh.getStyleClass().add("shop-new");
            under.getChildren().add(fresh);
        }
        // Alto fijo para la fila del precio: con el de cada una, una casilla
        // con "Nueva" mediria distinto y la cuenta de filas de fitSingles no
        // casaria con lo pintado.
        final double underH = UiScale.px(26);
        under.setMinHeight(underH);
        under.setPrefHeight(underH);
        under.setMaxHeight(underH);
        final VBox tile = new VBox(2, face, under);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(SINGLE_PAD));
        final double tileW = w + SINGLE_PAD * 2;
        tile.setMinWidth(tileW);
        tile.setPrefWidth(tileW);
        tile.setMaxWidth(tileW);
        tile.pseudoClassStateChanged(PICKED, card.equals(chosenSingle));
        tile.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                chosenSingle = card;
                paintSingles();
                refreshSingle();
                // Doble clic = comprarla (Discord, 24-10-2026): elegir y pulsar
                // COMPRAR carta a carta era el clic de mas.
                if (ev.getClickCount() == 2 && !buySingle.isDisabled()) {
                    takeSingle();
                }
            }
        });
        return tile;
    }

    private void refreshSingle() {
        if (chosenSingle == null) {
            singleName.setText(NeoText.get("shop.pickCard"));
            singlePrice.setText("");
            buySingle.setDisable(true);
            return;
        }
        final int price = NeoQuestShop.priceOfCard(chosenSingle);
        singleName.setText(forge.neo.card.CardText.nameOf(chosenSingle));
        final boolean afford = NeoQuest.credits() >= price;
        singlePrice.setText(afford ? NeoText.get("shop.cardPrice", price)
                : NeoText.get("shop.cannotAfford", price));
        buySingle.setDisable(opening || !afford);
    }

    /**
     * "Comprar todo lo que se ve": una copia de cada carta que deja el filtro,
     * en el orden en que se ven, hasta donde lleguen los creditos. Se pregunta
     * antes (principio 6) con cuantas y cuanto, y si no llega para todas, lo
     * dice. La respuesta marcada es cancelar.
     */
    private void askBuyAllSingles() {
        if (opening || singlesShown.isEmpty()) {
            return;
        }
        final List<PaperCard> plan = new ArrayList<>();
        final java.util.Set<PaperCard> seen = new java.util.HashSet<>();
        long total = 0;
        long left = NeoQuest.credits();
        int notAfford = 0;
        for (final PaperCard c : singlesShown) {
            if (!seen.add(c)) {
                continue;
            }
            final int price = NeoQuestShop.priceOfCard(c);
            if (price <= left) {
                plan.add(c);
                total += price;
                left -= price;
            } else {
                notAfford++;
            }
        }
        if (plan.isEmpty()) {
            singleName.setText(NeoText.get("shop.buyAll.none"));
            return;
        }
        final String body = NeoText.get("shop.buyAll.body", plan.size(), total, NeoQuest.credits() - total)
                + (notAfford > 0 ? "\n" + NeoText.get("shop.buyAll.short", notAfford) : "");
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("shop.buyAll.title", plan.size()), body,
                List.of(NeoText.get("shop.buyAll.go"), NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice != null && choice == 0) {
                        int bought = 0;
                        long paid = 0;
                        for (final PaperCard c : plan) {
                            final NeoQuestShop.Opened got = NeoQuestShop.buySingle(c);
                            if (got == null) {
                                break;
                            }
                            bought++;
                            paid += got.getPaid();
                        }
                        chosenSingle = null;
                        reloadSingles();
                        singleName.setText(NeoText.get("shop.buyAll.done", bought, paid));
                        if (onOpened != null) {
                            onOpened.run();
                        }
                    }
                }));
    }

    /** Comprar la carta elegida. Va a tu coleccion y sale del mostrador. */
    private void takeSingle() {
        if (chosenSingle == null || opening) {
            return;
        }
        final PaperCard card = chosenSingle;
        final NeoQuestShop.Opened bought = NeoQuestShop.buySingle(card);
        if (bought == null) {
            return;
        }
        // Sin panel de "lo que te ha tocado" (Discord, 11-10-2026: "I find it
        // useless and just breaks flow"): la carta ya la has visto al elegirla,
        // y ya has pulsado comprar. Se dice en el pie y se sigue comprando.
        // Si quedan mas copias, sigue elegida: comprar dos del mismo es pulsar
        // dos veces, no volver a buscarla.
        chosenSingle = singleCounts.getOrDefault(card, 1) > 1 ? card : null;
        reloadSingles();
        singleName.setText(NeoText.get("shop.boughtSingle",
                forge.neo.card.CardText.nameOf(card), bought.getPaid()));
        if (onOpened != null) {
            onOpened.run();
        }
    }

    // ---------------------------------------------------------------
    // Secret Lair
    // ---------------------------------------------------------------

    /**
     * El mostrador de Secret Lair.
     *
     * <p>Aqui hay <b>dos productos</b>, y la diferencia entre ellos es justo lo
     * que hace interesante el mostrador:
     *
     * <ul>
     *   <li><b>El sobre sorpresa</b>: dos exclusivas al azar del saco entero.
     *       Barato, y no eliges.</li>
     *   <li><b>Los drops con nombre</b> — Ghost of Tsushima, Sonic, Fallout —
     *       que traen sus cartas fijas y <b>se ven antes de pagar</b>. Mas caro,
     *       porque aqui sabes exactamente que compras.</li>
     * </ul>
     *
     * <p>La lista de drops no la da Forge: la escribimos nosotros. Ver
     * {@link forge.neo.quest.SecretLairDrops}, que explica por que no se puede
     * deducir del fichero de edicion.
     *
     * <p>Elegir no compra. Se marca uno, se ve lo que trae y lo que cuesta, y
     * comprar es otro click — que es la regla de toda la tienda, porque comprar
     * no se puede deshacer.
     */
    private Region buildLairPage() {
        final Label title = new Label(NeoText.get("shop.lair.title"));
        title.getStyleClass().add("home-title");

        final Label what = new Label(NeoText.get("shop.lair.what",
                NeoQuestShop.SECRET_LAIR_SIZE, NeoQuestShop.secretLairPool().size()));
        what.getStyleClass().add("home-subtitle");
        what.setWrapText(true);
        what.setMaxWidth(UiScale.px(760));

        final Label why = new Label(NeoText.get("shop.lair.why"));
        why.getStyleClass().add("home-subtitle");
        why.setWrapText(true);
        why.setMaxWidth(UiScale.px(760));

        // --- el selector: sorpresa y cada drop ---
        final Label pickCap = new Label(NeoText.get("shop.lair.choose"));
        pickCap.getStyleClass().add("caption");

        lairPicker.setAlignment(Pos.TOP_LEFT);
        lairPicker.getChildren().add(lairPick(NeoText.get("shop.lair.surprise"), null));
        for (final forge.neo.quest.SecretLairDrops.Drop drop : NeoQuestShop.drops()) {
            lairPicker.getChildren().add(lairPick(drop.getName(), drop));
        }

        // Para poder capturar un drop concreto sin tocar el raton. Se dispara
        // el boton de verdad en vez de poner el campo a mano: asi lo que se
        // captura es el mismo camino que recorre un click.
        final int forcedDrop = Integer.getInteger("neo.lair.drop", -1);
        if (forcedDrop >= 0 && forcedDrop < NeoQuestShop.drops().size()) {
            ((Button) lairPicker.getChildren().get(forcedDrop + 1)).fire();
        }

        // --- lo elegido ---
        lairProduct.getStyleClass().add("quest-deck-name");
        lairPrice.getStyleClass().add("shop-price");
        lairNote.getStyleClass().add("caption");
        lairNote.setWrapText(true);
        lairNote.setMaxWidth(UiScale.px(520));

        buyLair.getStyleClass().add("btn-primary");
        buyLair.setMinWidth(Region.USE_PREF_SIZE);
        buyLair.setText(NeoText.get("shop.buy"));
        buyLair.setOnAction(e -> takeLair());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(16, new VBox(2, lairProduct, lairPrice, lairNote),
                gap, buyLair);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stat-tile");
        row.setPadding(new Insets(14, 18, 14, 18));

        // --- y lo que trae, a la vista ---
        //
        // No es un adorno ni una filtracion. Con el sorpresa es la diferencia
        // entre "creditos por dos cartas cualquiera" y "creditos por dos de
        // ESTAS"; con un drop es lisa y llanamente lo que compras, y un
        // producto de precio fijo que no ensenya su contenido es una loteria
        // disfrazada. Click derecho las amplia para leerlas.
        lairPoolCap.getStyleClass().add("caption");

        lairGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane poolScroll = new ScrollPane(lairGrid);
        poolScroll.getStyleClass().add("dialog-scroll");
        poolScroll.setFitToWidth(true);
        poolScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(poolScroll, Priority.ALWAYS);

        paintLairCards();

        final VBox box = new VBox(12, title, what, why, pickCap, lairPicker, row,
                lairPoolCap, poolScroll);
        box.setPadding(new Insets(10, 0, 0, 0));
        return box;
    }

    /** Una pastilla del selector: el sorpresa o un drop. */
    private Button lairPick(final String label, final forge.neo.quest.SecretLairDrops.Drop drop) {
        final Button b = new Button(label);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.pseudoClassStateChanged(SELECTED, chosenDrop == drop);
        b.setOnAction(e -> {
            chosenDrop = drop;
            for (final javafx.scene.Node n : lairPicker.getChildren()) {
                n.pseudoClassStateChanged(SELECTED, n == b);
            }
            paintLairCards();
            refreshLair();
        });
        return b;
    }

    /**
     * Pinta lo que trae lo elegido.
     *
     * <p>Del sorpresa se ensenya el saco entero — no cuales van a tocar, que
     * eso no se sabe hasta abrirlo — y de un drop, sus cartas exactas.
     */
    private void paintLairCards() {
        lairGrid.getChildren().clear();
        final List<PaperCard> cards = chosenDrop == null
                ? NeoQuestShop.secretLairPool() : chosenDrop.getCards();
        for (final PaperCard c : cards) {
            final CardNode node = new CardNode(cardWidth * 0.72);
            node.setRotationEnabled(false);
            node.setBadgesVisible(false);
            node.setCard(forge.game.card.CardView.getCardForUi(c));
            lairGrid.getChildren().add(node);
        }
    }

    private void refreshLair() {
        if (lairDrop.isEmpty()) {
            lairDrop = NeoQuestShop.secretLairPack();
        }
        final int price = chosenDrop == null
                ? NeoQuestShop.SECRET_LAIR_PRICE : chosenDrop.getPrice();
        final int size = chosenDrop == null
                ? NeoQuestShop.SECRET_LAIR_SIZE : chosenDrop.getCards().size();
        final boolean afford = NeoQuest.credits() >= price;

        lairProduct.setText(chosenDrop == null
                ? NeoText.get("shop.lair.product") : chosenDrop.getName());
        lairPrice.setText(afford
                ? NeoText.get("shop.packPrice", price, size)
                : NeoText.get("shop.cannotAfford", price));
        // Cuantas de las que trae no salen en ningun otro sitio. Es el dato por
        // el que se elige un drop frente a otro, y no se deduce mirando las
        // cartas: un Path to Exile con arte de Tsushima parece exclusivo y no
        // lo es.
        lairNote.setText(chosenDrop == null ? NeoText.get("shop.lair.surpriseNote")
                : NeoText.get("shop.lair.dropNote", chosenDrop.getExclusiveCount()));
        // "LAS QUE PUEDE TRAER" y "LO QUE TRAE" no son lo mismo, y confundirlos
        // aqui seria mentir: del sorpresa se ensenya el saco del que sale, de un
        // drop se ensenya lo que hay dentro.
        lairPoolCap.setText(NeoText.get(
                chosenDrop == null ? "shop.lair.pool" : "shop.lair.contents"));

        final boolean empty = chosenDrop == null
                ? lairDrop.isEmpty() : chosenDrop.getCards().isEmpty();
        buyLair.setDisable(opening || !afford || empty);
        refreshMoney();
    }

    private void takeLair() {
        if (opening) {
            return;
        }
        final NeoQuestShop.Opened opened = chosenDrop != null
                ? NeoQuestShop.buyDrop(chosenDrop)
                : lairDrop.isEmpty() ? null : NeoQuestShop.buySecretLair(lairDrop);
        if (opened == null) {
            return;
        }
        opening = true;
        refreshMoney();
        overlay.setOnBackgroundClick(this::closeOpened);
        overlay.show(CardHaul.panel(
                chosenDrop != null ? chosenDrop.getName() : NeoText.get("shop.lair.opened"),
                NeoText.get("shop.openedDetail", opened.getCards().size(),
                        opened.getNewCount(), opened.getPaid()),
                opened.getCards(), opened::isNew, null,
                cardWidth, getWidth(), getHeight(),
                this::closeOpened));

        // El siguiente sorpresa es OTRO. Sin esto se volverian a "abrir" las
        // mismas cartas, que es la misma trampa que ya tenian los sobres.
        // Un drop no se remueve: trae siempre lo mismo, que es su gracia.
        lairDrop = NeoQuestShop.secretLairPack();
        refreshLair();
        if (onOpened != null) {
            onOpened.run();
        }
    }

    // ---------------------------------------------------------------
    // Vender
    // ---------------------------------------------------------------

    /**
     * El mostrador al reves: tu coleccion, con lo que te dan por cada carta.
     *
     * <p>Es la otra mitad de la tienda, y hacia falta por una razon practica:
     * la aventura te llena el bolsillo de cartas que no puedes poner en ningun
     * mazo — en Commander, cada segunda copia — y sin poder venderlas el dinero
     * solo entra ganando duelos.
     *
     * <p>Hasta el 27-09-2026 era de una en una y sin filtros. Un informe de
     * itch.io lo dijo tal cual — <i>"selling cards is tedious"</i> — y pedia
     * cuatro cosas, que son las cuatro de ahora:
     *
     * <ul>
     *   <li><b>Elegir varias</b>: un clic elige o suelta, Mayus+clic elige un
     *       tramo, y "Elegir las N" elige todo lo que deja el filtro.</li>
     *   <li><b>Los filtros del constructor</b> ({@link CardFilterBar}): color,
     *       tipo, rareza y coste, con los mismos botones.</li>
     *   <li><b>Sin las de tus mazos</b>: ensenya solo lo que se puede vender
     *       sin tocar un mazo. Se cuenta por impresion y con el mayor de tus
     *       mazos, que es como lo cuenta el motor al vender
     *       ({@link NeoQuestSell#usedByDecks()}).</li>
     *   <li><b>El boton gordo</b>: vender todo lo que no va en ningun mazo.
     *       Pregunta antes, y la respuesta marcada es la segura.</li>
     * </ul>
     *
     * <p>Y como todo lo que gasta o cobra en esta pantalla: <b>elegir no
     * vende</b>. Vender es de lo poco que no se deshace — lo unico que hay es
     * que la carta vuelve al mostrador, mas cara. Por eso vender un lote de
     * varias cartas tambien pregunta: el boton dice cuantas, pero no cuales.
     */
    private Region buildSellPage() {
        sellFilters = new CardFilterBar(() -> {
            sellPager.reset();
            reloadSell();
        });

        sellSearch.setPromptText(NeoText.get("shop.searchCard"));
        sellSearch.getStyleClass().add("text-input");
        sellSearch.setPrefColumnCount(18);
        sellSearch.textProperty().addListener((o, was, is) -> {
            sellPager.reset();
            reloadSell();
        });

        for (final CheckBox box : new CheckBox[] {sellSpareOnly, sellFreeOnly}) {
            box.getStyleClass().add("shop-filter");
            box.setMinWidth(Region.USE_PREF_SIZE);
            box.setOnAction(e -> {
                sellPager.reset();
                reloadSell();
            });
        }
        sellSpareOnly.setText(NeoText.get("shop.sell.spareOnly"));
        sellFreeOnly.setText(NeoText.get("shop.sell.freeOnly"));

        final Label cap = new Label(NeoText.get("shop.sell.caption"));
        cap.getStyleClass().add("caption");

        // --- los dos botones de golpe, sobre toda la coleccion ---
        sellAll.getStyleClass().add("btn-secondary");
        sellAll.setMinWidth(Region.USE_PREF_SIZE);
        sellAll.setOnAction(e -> takeSellAll());

        sellUnused.getStyleClass().add("btn-danger");
        sellUnused.setMinWidth(Region.USE_PREF_SIZE);
        sellUnused.setOnAction(e -> askSellUnused());

        final Region gapHead = new Region();
        HBox.setHgrow(gapHead, Priority.ALWAYS);
        final HBox head = new HBox(12, cap, gapHead, sellAll, sellUnused);
        head.setAlignment(Pos.CENTER_LEFT);

        // Un FlowPane y no un HBox: con los colores y las dos casillas, a 1280
        // de ancho no cabe en una fila, y recortar un filtro es esconderlo.
        sellSort.getItems().setAll(NeoText.get("sort.name"), NeoText.get("sort.value"));
        sellSort.getSelectionModel().select(Integer.getInteger("neo.shop.sellSort", 0)); // pruebas: 1 = valor
        sellSort.setId("shop-sell-sort");
        sellSort.setOnAction(e -> {
            sellPager.reset();
            reloadSell();
        });
        final HBox sortBox = new HBox(6, new Label(NeoText.get("sort.caption")), sellSort);
        sortBox.setAlignment(Pos.CENTER_LEFT);
        final FlowPane tools = new FlowPane(12, 8, sellSearch, sellFilters.bar(), sortBox,
                sellSpareOnly, sellFreeOnly);
        tools.setAlignment(Pos.CENTER_LEFT);
        sellTools = tools;

        sellGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(sellGrid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // --- cuantas hay, cuantas elegidas, y el paginador ---
        sellCount.getStyleClass().add("dialog-counter");
        sellCount.setMinWidth(Region.USE_PREF_SIZE);
        sellPickAll.getStyleClass().add("segment");
        sellPickAll.setMinWidth(Region.USE_PREF_SIZE);
        sellPickAll.setOnAction(e -> {
            sellPicked.addAll(sellShown);
            markPicked();
            refreshSell();
        });
        sellPickNone.getStyleClass().add("segment");
        sellPickNone.setMinWidth(Region.USE_PREF_SIZE);
        sellPickNone.setText(NeoText.get("shop.sell.pickNone"));
        sellPickNone.setOnAction(e -> {
            sellPicked.clear();
            sellAnchor = null;
            if (sellReview != null) {
                // Sin nada elegido no hay nada que repasar: a la coleccion.
                toggleOnlyPicked();
                return;
            }
            markPicked();
            refreshSell();
        });
        // Pedido (Discord, 14-10-2026): con cientos de cartas elegidas, el
        // marco de cada una y la cuenta no dejan repasar que se va a vender.
        sellOnlyPicked.getStyleClass().add("segment");
        sellOnlyPicked.setMinWidth(Region.USE_PREF_SIZE);
        sellOnlyPicked.setOnAction(e -> toggleOnlyPicked());
        final Region gapPages = new Region();
        HBox.setHgrow(gapPages, Priority.ALWAYS);
        final HBox pageBar = new HBox(10, sellCount, sellPickAll, sellPickNone,
                sellOnlyPicked, gapPages, sellPager);
        pageBar.setAlignment(Pos.CENTER_LEFT);

        // --- lo elegido ---
        sellName.getStyleClass().add("quest-deck-name");
        sellPrice.getStyleClass().add("shop-price");
        sellNote.getStyleClass().add("caption");
        sellNote.setWrapText(true);
        sellNote.setMaxWidth(UiScale.px(520));

        sellOne.getStyleClass().add("btn-primary");
        sellOne.setMinWidth(Region.USE_PREF_SIZE);
        sellOne.setText(NeoText.get("shop.sell.one"));
        sellOne.managedProperty().bind(sellOne.visibleProperty());
        sellOne.setOnAction(e -> takeSell(Mode.ONE));

        sellSpare.getStyleClass().add("btn-secondary");
        sellSpare.setMinWidth(Region.USE_PREF_SIZE);
        sellSpare.setOnAction(e -> takeSell(Mode.SPARE));

        sellFree.getStyleClass().add("btn-secondary");
        sellFree.setMinWidth(Region.USE_PREF_SIZE);
        sellFree.setOnAction(e -> takeSell(Mode.FREE));

        // Cambiar el arte (Discord, 11-10-2026): aqui, junto a vender, porque es
        // donde se mira una impresion concreta de tu coleccion.
        sellArt.getStyleClass().add("btn-secondary");
        sellArt.setMinWidth(Region.USE_PREF_SIZE);
        sellArt.setText(NeoText.get("shop.art.button"));
        sellArt.managedProperty().bind(sellArt.visibleProperty());
        sellArt.setVisible(false);
        sellArt.setOnAction(e -> {
            if (sellPicked.size() == 1) {
                chooseArt(sellPicked.iterator().next());
            }
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final VBox info = new VBox(2, sellName, sellPrice, sellNote);
        info.setMinWidth(0);
        final HBox row = new HBox(12, info, gap, sellArt, sellSpare, sellFree, sellOne);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stat-tile");
        row.setPadding(new Insets(14, 18, 14, 18));

        return new VBox(10, head, tools, sellFilters.advanced(), scroll, pageBar, row);
    }

    /** Cuantas copias de cada carta elegida vende cada boton. */
    private enum Mode { ONE, SPARE, FREE }

    /**
     * Ver solo lo elegido, o volver a la coleccion. Mientras se repasa, el
     * buscador y los filtros se van: si siguieran, esconderian parte de lo
     * elegido, y a la vista sin mandar serian controles que no hacen lo que
     * parecen (las notas de diseño, principio 1). Al volver estan como se dejaron.
     */
    private void toggleOnlyPicked() {
        if (sellReview == null && sellPicked.isEmpty()) {
            return;
        }
        if (sellReview == null) {
            sellReview = new java.util.HashSet<>(sellPicked);
            sellAdvancedWasOpen = sellFilters.advanced().isVisible();
        } else {
            sellReview = null;
        }
        final boolean reviewing = sellReview != null;
        sellTools.setVisible(!reviewing);
        sellTools.setManaged(!reviewing);
        final boolean advanced = !reviewing && sellAdvancedWasOpen;
        sellFilters.advanced().setVisible(advanced);
        sellFilters.advanced().setManaged(advanced);
        sellPager.reset();
        reloadSell();
    }

    private void reloadSell() {
        // La coleccion se lee entera cada vez porque vender la cambia, y con
        // una copia vieja se podrian vender cartas que ya no estan. Lo de los
        // mazos, igual: se calcula una vez aqui y no una por carta.
        sellUsed = NeoQuestSell.usedByDecks();
        sellAllCards = new ArrayList<>();
        for (final java.util.Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            if (e.getKey() != null && e.getValue() > 0) {
                sellAllCards.add(e.getKey());
            }
        }
        sellAllCards.sort(java.util.Comparator.comparing(
                c -> forge.neo.card.CardText.nameOf(c), String.CASE_INSENSITIVE_ORDER));
        if (sellSort.getSelectionModel().getSelectedIndex() == 1) {
            // La mas cara primero; a igual precio sigue el orden por nombre (sort estable).
            sellAllCards.sort(java.util.Comparator.comparingInt((PaperCard c) -> -NeoQuestSell.priceOf(c)));
        }

        final String q = sellSearch.getText() == null ? ""
                : sellSearch.getText().trim().toLowerCase(Locale.ROOT);
        sellShown = new ArrayList<>();
        if (sellReview != null) {
            // Lo elegido que sigue en la coleccion (vendido, se va), sin filtros.
            for (final PaperCard c : sellAllCards) {
                if (sellReview.contains(c)) {
                    sellShown.add(c);
                }
            }
            if (sellShown.isEmpty()) {
                // Vendido todo lo que se repasaba: a la coleccion.
                toggleOnlyPicked();
                return;
            }
        }
        for (final PaperCard c : sellReview != null ? List.<PaperCard>of() : sellAllCards) {
            if (sellSpareOnly.isSelected() && NeoQuestSell.spareCopies(c) <= 0) {
                continue;
            }
            if (sellFreeOnly.isSelected() && NeoQuestSell.notInDecks(c, sellUsed) <= 0) {
                continue;
            }
            if (sellFilters != null && !sellFilters.test(c)) {
                continue;
            }
            // Por el nombre que se VE, como en el resto de la aplicacion.
            // Y por su tipo y su texto, como el resto de buscadores.
            if (q.isEmpty() || matchesSearch(c, q)) {
                sellShown.add(c);
            }
        }
        sellPager.setTotal(sellShown.size());
        // Lo que el filtro esconde deja de estar elegido: vender cartas que no
        // se ven es justo la sorpresa que no puede dar un boton de vender.
        sellPicked.retainAll(new java.util.HashSet<>(sellShown));
        if (sellAnchor != null && !sellPicked.contains(sellAnchor)) {
            sellAnchor = null;
        }
        paintSell();
        refreshSell();
        refreshMoney();
    }

    private void paintSell() {
        sellGrid.getChildren().clear();
        if (sellShown.isEmpty()) {
            final Label empty = new Label(NeoText.get(
                    sellAllCards.isEmpty() ? "shop.sell.empty" : "shop.sell.noMatch"));
            empty.getStyleClass().add("home-subtitle");
            sellGrid.getChildren().add(empty);
            return;
        }
        for (int i = sellPager.from(); i < Math.min(sellShown.size(), sellPager.to()); i++) {
            sellGrid.getChildren().add(sellTile(sellShown.get(i)));
        }
    }

    private Region sellTile(final PaperCard card) {
        final CardNode node = new CardNode(cardWidth * 0.82);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(forge.game.card.CardView.getCardForUi(card));

        final int price = NeoQuestSell.priceOf(card);
        final int have = NeoQuestSell.owned(card);
        final int spare = NeoQuestSell.spareCopies(card);
        final int inDecks = sellUsed.getOrDefault(card, 0);

        // Cuantas tienes y cuantas SOBRAN, en la propia carta. Sin eso hay que
        // clicarlas una a una para saber cual esta repetida, que es justo lo
        // que se viene a hacer a esta pantalla.
        final Label cost = new Label(NeoText.get("shop.sell.tile", price, have));
        cost.getStyleClass().add(spare > 0 ? "set-price-ok" : "set-price-no");
        // La estrella: marcada, las ventas automaticas se la saltan
        // (NeoQuestSell.isKept). Cambiarla cambia lo que dan los botones.
        final VBox tile = new VBox(2, FavoriteStar.wrapKept(node, card, () -> {
            paintSell();
            refreshSell();
        }), cost);
        // Y si la usa un mazo, tambien: venderla la saca de ahi.
        if (inDecks > 0) {
            final Label used = new Label(NeoText.get("shop.sell.inDecks", inDecks));
            used.getStyleClass().add("set-note");
            tile.getChildren().add(used);
        }
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(6, 6, 6, 6));
        tile.setUserData(card);
        tile.pseudoClassStateChanged(PICKED, sellPicked.contains(card));
        tile.setOnMouseClicked(ev -> {
            if (ev.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            if (ev.isShiftDown() && sellAnchor != null && sellShown.contains(sellAnchor)) {
                // Un tramo, en el orden en que se ven. Tambien entre paginas:
                // el ancla puede estar en la anterior.
                final int a = sellShown.indexOf(sellAnchor);
                final int b = sellShown.indexOf(card);
                sellPicked.addAll(sellShown.subList(Math.min(a, b), Math.max(a, b) + 1));
            } else if (!sellPicked.remove(card)) {
                sellPicked.add(card);
                sellAnchor = card;
            }
            markPicked();
            refreshSell();
        });
        return tile;
    }

    /** Enciende y apaga el marco de las elegidas sin rehacer las cartas. */
    private void markPicked() {
        for (final javafx.scene.Node n : sellGrid.getChildren()) {
            if (n.getUserData() instanceof PaperCard) {
                n.pseudoClassStateChanged(PICKED, sellPicked.contains((PaperCard) n.getUserData()));
            }
        }
    }

    /** Cuantas copias de ESA carta vende cada boton. */
    private int howMany(final PaperCard card, final Mode mode) {
        switch (mode) {
            case ONE:
                return 1;
            case SPARE:
                return NeoQuestSell.spareCopies(card);
            case FREE:
            default:
                return NeoQuestSell.notInDecks(card, sellUsed);
        }
    }

    /** El lote que venderia un boton con lo elegido ahora. */
    private java.util.Map<PaperCard, Integer> planFor(final Mode mode) {
        final java.util.Map<PaperCard, Integer> plan = new java.util.LinkedHashMap<>();
        for (final PaperCard c : sellPicked) {
            final int n = howMany(c, mode);
            if (n > 0) {
                plan.put(c, n);
            }
        }
        return plan;
    }

    private void refreshSell() {
        // Los botones de golpe dicen lo que van a dar ANTES de pulsarlos: si
        // no, son botones que se llevan parte de tu coleccion a cambio de una
        // sorpresa.
        final NeoQuestSell.Sold spare = NeoQuestSell.preview();
        sellAll.setText(spare.isEmpty() ? NeoText.get("shop.sell.allNone")
                : NeoText.get("shop.sell.all", spare.getCopies(), spare.getCredits()));
        sellAll.setDisable(opening || spare.isEmpty());

        final NeoQuestSell.Sold unused = NeoQuestSell.preview(NeoQuestSell.notInDecksPlan());
        sellUnused.setText(unused.isEmpty() ? NeoText.get("shop.sell.unusedNone")
                : NeoText.get("shop.sell.unused", unused.getCopies(), unused.getCredits()));
        sellUnused.setDisable(opening || unused.isEmpty());

        sellCount.setText(NeoText.get("shop.sell.count", sellShown.size(), sellPicked.size()));
        sellPickAll.setText(NeoText.get("shop.sell.pickAll", sellShown.size()));
        sellPickAll.setDisable(sellShown.isEmpty() || sellPicked.size() == sellShown.size());
        sellPickNone.setDisable(sellPicked.isEmpty());
        sellOnlyPicked.setText(sellReview != null ? NeoText.get("shop.sell.showAll")
                : NeoText.get("shop.sell.showPicked", sellPicked.size()));
        sellOnlyPicked.setDisable(sellReview == null && sellPicked.isEmpty());

        final int spareCount = count(planFor(Mode.SPARE));
        final int freeCount = count(planFor(Mode.FREE));
        sellSpare.setText(NeoText.get("shop.sell.spare", spareCount));
        sellSpare.setDisable(opening || spareCount <= 0);
        sellFree.setText(NeoText.get("shop.sell.free", freeCount));
        sellFree.setDisable(opening || freeCount <= 0);

        sellArt.setVisible(false);
        if (sellPicked.isEmpty()) {
            sellName.setText(NeoText.get("shop.sell.pick"));
            sellPrice.setText("");
            sellNote.setText(NeoText.get("shop.sell.hint"));
            sellOne.setVisible(true);
            sellOne.setDisable(true);
            return;
        }

        if (sellPicked.size() > 1) {
            // "Vender una" de cada no esta: con varias elegidas es el boton que
            // se llevaria la ultima copia de cartas que no se estan mirando.
            sellOne.setVisible(false);
            sellName.setText(NeoText.get("shop.sell.picked", sellPicked.size()));
            sellPrice.setText(NeoText.get("shop.sell.pickedWorth",
                    NeoQuestSell.preview(planFor(Mode.FREE)).getCredits()));
            sellNote.setText(NeoText.get("shop.sell.hint"));
            return;
        }

        final PaperCard card = sellPicked.iterator().next();
        final int price = NeoQuestSell.priceOf(card);
        final int have = NeoQuestSell.owned(card);
        final int extra = NeoQuestSell.spareCopies(card);
        final int inDecks = sellUsed.getOrDefault(card, 0);
        sellOne.setVisible(true);
        sellName.setText(forge.neo.card.CardText.nameOf(card) + "  ·  " + card.getEdition());
        sellPrice.setText(NeoText.get("shop.sell.price", price, have));
        // Si la usa un mazo, eso primero: es lo que se rompe. Si no, avisar
        // cuando vender deja la carta a cero, y decir tambien que se pierde
        // ESE ARTE: en la aventura el arte es contenido, y si esta era tu unica
        // copia de esa impresion, deja de poder ponerse en un mazo.
        sellNote.setText(inDecks > 0 ? NeoText.get("shop.sell.usedHere", inDecks)
                : have <= 1 ? NeoText.get("shop.sell.lastCopy")
                : extra > 0 ? NeoText.get("shop.sell.hasSpare", extra)
                : NeoText.get("shop.sell.noSpareHere"));
        sellOne.setDisable(opening || have <= 0);
        sellArt.setVisible(forge.neo.quest.NeoQuestArt.canChange(card));
        sellArt.setDisable(opening);
    }

    /**
     * Elegir otro arte para una carta de tu coleccion, pagando (Discord,
     * 11-10-2026: <i>"a card art changer ... Change price could be based on
     * it's rarity"</i>). El selector de arte de siempre, con lo que cuesta cada
     * una debajo y cuantas copias; al elegir, se pregunta con la cuenta hecha.
     * Ver {@link forge.neo.quest.NeoQuestArt}.
     */
    private void chooseArt(final PaperCard card) {
        final List<PaperCard> arts = new ArrayList<>();
        arts.add(card);
        arts.addAll(forge.neo.quest.NeoQuestArt.printingsOf(card));
        if (arts.size() <= 1 || opening) {
            return;
        }
        final int have = NeoQuestSell.owned(card);
        overlay.setOnBackgroundClick(overlay::hide);
        final PrintingDialog[] dialog = new PrintingDialog[1];
        dialog[0] = new PrintingDialog(card, arts, cardWidth, picked -> {
            if (picked.equals(card)) {
                return;
            }
            final int n = Math.max(1, Math.min(have, dialog[0].copies()));
            askArt(card, picked, n);
        }, overlay::hide, NeoText.get("shop.art.title", forge.neo.card.CardText.nameOf(card)),
                NeoText.get("shop.art.hint"),
                Math.min(UiScale.px(520), cardWidth * forge.neo.card.CardNode.ASPECT * 2 + 60))
                .offerCopies(have);
        dialog[0].withHelp(NeoText.get("shop.art.hint"))
                .withNote(p -> NeoText.get("shop.art.price", forge.neo.quest.NeoQuestArt.priceOf(p)),
                        p -> NeoQuest.credits() >= forge.neo.quest.NeoQuestArt.priceOf(p));
        overlay.show(dialog[0]);
    }

    private void askArt(final PaperCard from, final PaperCard to, final int copies) {
        final int total = forge.neo.quest.NeoQuestArt.priceOf(to) * copies;
        final boolean afford = NeoQuest.credits() >= total;
        overlay.setOnBackgroundClick(overlay::hide);
        if (!afford) {
            overlay.show(new ConfirmDialog(NeoText.get("shop.art.confirm.title"),
                    NeoText.get("shop.art.cannotAfford", total, NeoQuest.credits()),
                    List.of(NeoText.get("common.back")), 0, choice -> overlay.hide()));
            return;
        }
        overlay.show(new ConfirmDialog(NeoText.get("shop.art.confirm.title"),
                NeoText.get("shop.art.confirm", copies, forge.neo.card.CardText.nameOf(from),
                        from.getEdition(), to.getEdition(), total),
                List.of(NeoText.get("shop.art.confirm.go"), NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice == 0 && forge.neo.quest.NeoQuestArt.change(from, to, copies) >= 0) {
                        // La nueva queda elegida: es la que acabas de pagar.
                        sellPicked.clear();
                        reloadSell();
                        for (final PaperCard c : sellShown) {
                            if (c.equals(to)) {
                                sellPicked.add(c);
                                break;
                            }
                        }
                        markPicked();
                        refreshSell();
                        if (onOpened != null) {
                            onOpened.run();
                        }
                    }
                }));
    }

    private static int count(final java.util.Map<PaperCard, Integer> plan) {
        int n = 0;
        for (final int v : plan.values()) {
            n += v;
        }
        return n;
    }

    private void takeSell(final Mode mode) {
        if (sellPicked.isEmpty() || opening) {
            return;
        }
        final java.util.Map<PaperCard, Integer> plan = planFor(mode);
        if (plan.isEmpty()) {
            return;
        }
        // Una carta: el boton ya dice cual y cuantas, y asi ha sido siempre.
        // Varias: el boton dice cuantas copias pero no CUALES, y eso se
        // pregunta.
        if (sellPicked.size() == 1) {
            sellPlan(plan);
            return;
        }
        final NeoQuestSell.Sold p = NeoQuestSell.preview(plan);
        askToSell(NeoText.get("shop.sell.confirm.body", p.getCards().size(), p.getCredits()),
                p.getCopies(), () -> sellPlan(plan));
    }

    /** El boton gordo: todo lo que no va en un mazo. Siempre pregunta. */
    private void askSellUnused() {
        if (opening) {
            return;
        }
        final java.util.Map<PaperCard, Integer> plan = NeoQuestSell.notInDecksPlan();
        final NeoQuestSell.Sold p = NeoQuestSell.preview(plan);
        if (p.isEmpty()) {
            return;
        }
        askToSell(NeoText.get("shop.sell.confirm.unused", p.getCopies(), p.getCards().size(),
                p.getCredits(), NeoQuest.decks().size()), p.getCopies(), () -> sellPlan(plan));
    }

    /**
     * Pregunta antes de vender un lote. La respuesta marcada es CANCELAR: lo
     * que no se deshace no puede ser lo que sale de un Intro por inercia.
     */
    private void askToSell(final String body, final int copies, final Runnable go) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new ConfirmDialog(NeoText.get("shop.sell.confirm.title", copies), body,
                List.of(NeoText.get("shop.sell.confirm.go"), NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice == 0) {
                        go.run();
                    }
                }));
    }

    private void sellPlan(final java.util.Map<PaperCard, Integer> plan) {
        final NeoQuestSell.Sold sold = NeoQuestSell.sell(plan);
        if (sold.isEmpty()) {
            return;
        }
        // Ni overlay ni panel: no se ha ABIERTO nada, se ha vendido. Ensenyar
        // aqui el mismo panel de "esto es lo que te llevas" seria mentir sobre
        // lo que acaba de pasar. Lo que ya no tienes deja de estar elegido
        // (lo hace reloadSell); lo que te queda, sigue.
        reloadSell();
        if (onOpened != null) {
            onOpened.run();
        }
    }

    private void takeSellAll() {
        if (opening) {
            return;
        }
        final NeoQuestSell.Sold sold = NeoQuestSell.sell();
        if (sold.isEmpty()) {
            return;
        }
        reloadSell();
        if (onOpened != null) {
            onOpened.run();
        }
    }

    // ---------------------------------------------------------------

    private void reload() {
        final String q = search.getText() == null ? ""
                : search.getText().trim().toLowerCase(Locale.ROOT);
        filtered = new ArrayList<>();
        final List<CardEdition> source = new ArrayList<>();
        if (findName != null) {
            // Con una carta buscada, solo los sobres donde sale, y PRIMERO EL
            // MAS BARATO PARA CONSEGUIRLA: lo que cuesta de media una copia
            // (precio del sobre por cuantos sobres hacen falta). El mas
            // probable de todos era Alpha, a 75.000 creditos el sobre.
            for (final forge.neo.quest.PackContents.Source s : findOdds.values()) {
                source.add(s.getEdition());
            }
            source.sort(java.util.Comparator.comparingDouble(this::costPerCopy));
        } else {
            source.addAll(NeoQuestShop.editions());
        }
        for (final CardEdition e : source) {
            final boolean matches = q.isEmpty()
                    || e.getName().toLowerCase(Locale.ROOT).contains(q)
                    || e.getCode().toLowerCase(Locale.ROOT).contains(q);
            if (matches && (!affordableOnly.isSelected() || NeoQuest.credits() >= priceOf(e))) {
                filtered.add(e);
            }
        }
        pager.setTotal(filtered.size());
        paintEditions();
        if ((chosen == null || (findName != null && !filtered.contains(chosen))) && !filtered.isEmpty()) {
            choose(filtered.get(0));
        } else if (findName != null && filtered.isEmpty()) {
            // Buscando una carta y sin un sobre a la vista (solo sale en uno
            // caro con "lo que puedo pagar" puesto, o solo en un colector): la
            // fila de abajo no puede quedarse con el sobre de antes y sus
            // botones encendidos.
            chosen = null;
            pack = null;
            refreshPack();
        }
        refreshMoney();
    }

    /**
     * Solo la pagina actual.
     *
     * <p>Hay cientos de expansiones con sobre: pintarlas todas deja la pantalla
     * pegada, y recortar sin paginador seria mentir sobre lo que hay.
     */
    private void paintEditions() {
        editionGrid.getChildren().clear();
        for (int i = pager.from(); i < Math.min(filtered.size(), pager.to()); i++) {
            final CardEdition e = filtered.get(i);
            editionGrid.getChildren().add(editionTile(e));
        }
    }

    private Region editionTile(final CardEdition e) {
        final Label code = new Label(e.getCode());
        code.getStyleClass().add("set-code");

        final Label name = new Label(e.getName());
        name.getStyleClass().add("set-name");
        name.setWrapText(true);
        name.setMaxWidth(UiScale.px(170));
        name.setMinHeight(Region.USE_PREF_SIZE);

        // El precio, EN LA CASILLA. Van de 300 a 35.000 creditos, asi que sin
        // verlo aqui hay que ir clicando expansion por expansion para saber
        // cual te puedes permitir — que es justo la decision que se viene a
        // tomar a esta pantalla.
        final int price = priceOf(e);
        final Label cost = new Label(NeoText.get("shop.credits.short", price));
        cost.getStyleClass().add(NeoQuest.credits() >= price ? "set-price-ok" : "set-price-no");

        final VBox tile = new VBox(3, code, name, cost);
        final forge.neo.quest.PackContents.Source where = findName == null ? null : findOdds.get(e.getCode());
        if (where != null) {
            // Cada cuanto sale la carta buscada en ESTE sobre: es la razon por
            // la que la casilla esta en la lista.
            final Label odds = new Label(oddsText(where.getPull()));
            odds.getStyleClass().add("set-odds");
            final Label perCopy = new Label(NeoText.get("shop.find.costPer", Math.round(costPerCopy(e))));
            perCopy.getStyleClass().add("set-odds-cost");
            tile.getChildren().addAll(odds, perCopy);
        }
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(8, 12, 8, 12));
        tile.setPrefWidth(UiScale.px(190));
        tile.pseudoClassStateChanged(PICKED, chosen != null && chosen.getCode().equals(e.getCode()));
        tile.setOnMouseClicked(ev -> choose(e));
        return tile;
    }

    // ---------------------------------------------------------------
    //  Los mazos de Commander (09-10-2026)
    // ---------------------------------------------------------------

    /** "Ahoy Mateys [LCC] [2023]" -> "Ahoy Mateys": el codigo ya va en la casilla. */
    private static String deckTitle(final forge.item.PreconDeck p) {
        return NeoQuestShop.cleanDeckName(p.getName());
    }

    /** El comandante, en tu idioma: lo que se busca y por lo que se elige un mazo. */
    private static String commanderLine(final forge.item.PreconDeck p) {
        if (p.getDeck() == null || p.getDeck().getCommanders().isEmpty()) {
            return "";
        }
        final List<String> names = new ArrayList<>();
        for (final PaperCard c : p.getDeck().getCommanders()) {
            names.add(forge.neo.card.CardText.nameOf(c));
        }
        return String.join(" + ", names);
    }

    /** El pie al elegirlo: cuantas cartas trae y cuantas de ellas no tienes. */
    private static String commanderDetail(final forge.item.PreconDeck p) {
        if (p.getDeck() == null) {
            return "";
        }
        final java.util.Set<String> owned = forge.neo.quest.NeoQuestCollection.ownedNames();
        int total = 0;
        int fresh = 0;
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (final java.util.Map.Entry<PaperCard, Integer> e : p.getDeck().getAllCardsInASinglePool()) {
            total += e.getValue();
            if (seen.add(e.getKey().getName()) && !owned.contains(e.getKey().getName())) {
                fresh++;
            }
        }
        final String detail = NeoText.get("shop.cmdDecks.detail", total, fresh);
        // En una Quest de Estandar no hay zona de mando y el comandante entra
        // en el mazo (NeoQuestShop.buyCommanderDeck): mejor saberlo antes.
        return NeoQuest.modalidad() == NeoQuest.Modalidad.COMMANDER ? detail
                : detail + "  ·  " + NeoText.get("shop.cmdDecks.standard");
    }

    // ---------------------------------------------------------------
    //  Que puede salir, y en que sobre sale una carta (09-10-2026)
    // ---------------------------------------------------------------

    /**
     * Lo que cuesta de media una copia de la carta buscada en ese sobre: su
     * precio entre cuantas trae de media. Infinito si no sale.
     */
    private double costPerCopy(final CardEdition e) {
        final forge.neo.quest.PackContents.Source s = findOdds.get(e.getCode());
        if (s == null || s.getPull().getPerPack() <= 0) {
            return Double.MAX_VALUE;
        }
        return priceOf(e) / s.getPull().getPerPack();
    }

    /** El sobre donde sale mas barata, o null. */
    private forge.neo.quest.PackContents.Source cheapest() {
        forge.neo.quest.PackContents.Source best = null;
        for (final forge.neo.quest.PackContents.Source s : findOdds.values()) {
            if (best == null || costPerCopy(s.getEdition()) < costPerCopy(best.getEdition())) {
                best = s;
            }
        }
        return best;
    }

    /** "1 de cada 137" / "En todos los sobres". */
    private static String oddsText(final forge.neo.quest.PackContents.Pull p) {
        return p.getOneIn() <= 1 ? NeoText.get("shop.odds.every") : NeoText.get("shop.odds.oneIn", p.getOneIn());
    }

    /**
     * Lo que puede salir en el sobre de esa expansion (o en el de colector),
     * encima de todo como un sobre abierto. Con una carta buscada, ya buscada
     * dentro.
     */
    private void showPreview(final CardEdition edition, final boolean collector) {
        if (edition == null) {
            return;
        }
        final List<forge.neo.quest.PackContents.Pull> pulls = collector
                ? forge.neo.quest.PackContents.ofCollector(edition)
                : forge.neo.quest.PackContents.of(edition);
        final forge.item.BoosterPack sample = collector ? NeoQuestShop.collectorBooster(edition) : pack;
        final int perPack = sample == null ? 0 : sample.getTotalCards();
        final String title = collector
                ? NeoText.get("shop.preview.collector", edition.getName()) : edition.getName();
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new PackPreview(title, pulls, ownedCounts(), perPack, getWidth(), getHeight(),
                findName == null ? null : forge.neo.card.CardText.nameOf(cardNamed(findName)), overlay::hide));
    }

    /**
     * Las cartas que trae un mazo preconstruido, encima de todo como lo de un
     * sobre (Discord, 27-10-2026: <i>"add the ability to see what cards come
     * in a deck/precon"</i>).
     */
    private void showDeck(final forge.item.PreconDeck precon) {
        if (precon == null || precon.getDeck() == null) {
            return;
        }
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(PackPreview.ofDeck(deckTitle(precon), precon.getDeck(), ownedCounts(),
                getWidth(), getHeight(), overlay::hide));
    }

    /**
     * Lo que puede salir en el sobre de una expansion, fuera de la tienda: el
     * elegidor del sobre de premio (ver {@code ChoiceDialog.setEditionPeek}).
     */
    public static PackPreview previewOf(final CardEdition edition, final double w, final double h,
                                        final Runnable onClose) {
        final forge.item.BoosterPack sample = NeoQuestShop.boosterOf(edition);
        return new PackPreview(edition.getName(), forge.neo.quest.PackContents.of(edition), ownedCounts(),
                sample == null ? 0 : sample.getTotalCards(), w, h, null, onClose);
    }

    /** Cuantas tienes de cada carta, por nombre (como cuenta la tienda lo que es nuevo). */
    private static java.util.Map<String, Integer> ownedCounts() {
        final java.util.Map<String, Integer> out = new java.util.HashMap<>();
        if (NeoQuest.isActive()) {
            for (final java.util.Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
                out.merge(e.getKey().getName(), e.getValue(), Integer::sum);
            }
        }
        return out;
    }

    private static PaperCard cardNamed(final String name) {
        return name == null ? null : forge.model.FModel.getMagicDb().getCommonCards().getCard(name);
    }

    /**
     * El buscador de cartas: escribes, sugiere las que salen en algun sobre de
     * la tienda (en ingles y en tu idioma) y al elegir una la rejilla se queda
     * con los sobres donde sale.
     */
    private void buildFinder() {
        findField.setPromptText(NeoText.get("shop.find.prompt"));
        findPopup.getStyleClass().add("find-popup");
        findField.getStyleClass().add("text-input");
        findField.setPrefColumnCount(24);
        findField.setId("shop-find-card");
        findField.textProperty().addListener((o, was, is) -> {
            if (findName != null && !is.equals(forge.neo.card.CardText.nameOf(cardNamed(findName)))) {
                // Escribir otra cosa deja de buscar la de antes.
                clearFind(false);
            }
            suggest();
        });
        findField.setOnAction(e -> {
            // Enter: la primera sugerencia.
            final List<String> hits = matches(findField.getText());
            if (!hits.isEmpty()) {
                findCard(hits.get(0));
            }
        });
        findField.focusedProperty().addListener((o, was, is) -> {
            if (is) {
                loadNames();
            }
        });
        findBanner.getStyleClass().addAll("stat-tile", "find-banner");
        findBanner.setPadding(new Insets(10, 16, 10, 16));
        findBanner.setManaged(false);
        findBanner.setVisible(false);
    }

    /** Las cartas de todos los sobres de la tienda, en otro hilo (la primera vez monta todos los sobres). */
    private void loadNames() {
        if (findNames != null || findLoading) {
            return;
        }
        findLoading = true;
        final Thread t = new Thread(() -> {
            List<String> names = null;
            try {
                names = forge.neo.quest.PackContents.namesIn(
                        NeoQuestShop.editions(), NeoQuestShop.collectorEditions());
            } catch (final RuntimeException e) {
                System.err.println("[tienda] no se han podido leer las cartas de los sobres: " + e);
            } finally {
                // Siempre de vuelta: si falla, findLoading se suelta y el
                // proximo foco lo vuelve a intentar.
                final List<String> got = names;
                javafx.application.Platform.runLater(() -> {
                    findNames = got;
                    findLoading = false;
                    if (got != null && findField.isFocused()) {
                        suggest();
                    }
                });
            }
        }, "neo-shop-names");
        t.setDaemon(true);
        t.start();
    }

    /** Hasta doce, primero las que empiezan por lo escrito. Busca en ingles y en tu idioma. */
    private List<String> matches(final String text) {
        final List<String> names = findNames;
        final String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        final List<String> starts = new ArrayList<>();
        final List<String> contains = new ArrayList<>();
        if (names == null || q.length() < 2) {
            return starts;
        }
        for (final String n : names) {
            final String en = n.toLowerCase(Locale.ROOT);
            final String tr = forge.util.CardTranslation.getTranslatedName(n).toLowerCase(Locale.ROOT);
            if (en.startsWith(q) || tr.startsWith(q)) {
                starts.add(n);
            } else if (en.contains(q) || tr.contains(q)) {
                contains.add(n);
            }
            if (starts.size() >= 12) {
                break;
            }
        }
        starts.addAll(contains);
        return starts.size() > 12 ? new ArrayList<>(starts.subList(0, 12)) : starts;
    }

    private void suggest() {
        if (findName != null || !findField.isFocused()) {
            findPopup.hide();
            return;
        }
        final List<String> hits = matches(findField.getText());
        if (hits.isEmpty()) {
            findPopup.hide();
            return;
        }
        final List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        for (final String n : hits) {
            final String tr = forge.util.CardTranslation.getTranslatedName(n);
            final Label l = new Label(tr.equals(n) ? n : tr + "   ·   " + n);
            l.getStyleClass().add("find-suggestion");
            final javafx.scene.control.CustomMenuItem item = new javafx.scene.control.CustomMenuItem(l, true);
            item.setOnAction(e -> findCard(n));
            items.add(item);
        }
        findPopup.getItems().setAll(items);
        if (!findPopup.isShowing()) {
            findPopup.show(findField, javafx.geometry.Side.BOTTOM, 0, 2);
        }
    }

    /** Busca esa carta: la rejilla se queda con sus sobres y arriba sale donde y cada cuanto. */
    private void findCard(final String name) {
        findPopup.hide();
        findName = name;
        final PaperCard card = cardNamed(name);
        final String shownName = card == null ? name : forge.neo.card.CardText.nameOf(card);
        if (!shownName.equals(findField.getText())) {
            findField.setText(shownName);
        }
        findName = name;
        findOdds.clear();
        final List<CardEdition> collectors = new ArrayList<>();
        for (final forge.neo.quest.PackContents.Source s : forge.neo.quest.PackContents.whereIs(
                name, NeoQuestShop.editions(), NeoQuestShop.collectorEditions())) {
            if (s.isCollector()) {
                collectors.add(s.getEdition());
            } else {
                findOdds.put(s.getEdition().getCode(), s);
            }
        }
        paintBanner(card, shownName, collectors);
        pager.reset();
        chosen = null;
        pack = null;
        reload();
    }

    private void clearFind(final boolean clearText) {
        findName = null;
        findOdds.clear();
        findBanner.getChildren().clear();
        findBanner.setManaged(false);
        findBanner.setVisible(false);
        if (clearText) {
            findField.clear();
        }
        pager.reset();
        reload();
    }

    /**
     * Arriba de la rejilla: la carta, en cuantos sobres sale, el mas probable,
     * y si ademas sale en un colector o esta en el mostrador de sueltas.
     */
    private void paintBanner(final PaperCard card, final String shownName, final List<CardEdition> collectors) {
        final List<javafx.scene.Node> lines = new ArrayList<>();
        final Label title = new Label(findOdds.isEmpty()
                ? NeoText.get("shop.find.none", shownName)
                : findOdds.size() == 1 ? NeoText.get("shop.find.inOne", shownName)
                : NeoText.get("shop.find.inMany", shownName, findOdds.size()));
        title.getStyleClass().add("quest-deck-name");
        title.setWrapText(true);
        lines.add(title);
        final forge.neo.quest.PackContents.Source best = cheapest();
        if (best != null) {
            final Label line = new Label(NeoText.get("shop.find.best", best.getEdition().getName(),
                    oddsText(best.getPull()), Math.round(costPerCopy(best.getEdition()))));
            line.getStyleClass().add("find-line");
            lines.add(line);
        }
        if (!collectors.isEmpty()) {
            final List<String> names = new ArrayList<>();
            for (final CardEdition e : collectors) {
                names.add(e.getName());
            }
            final Label line = new Label(NeoText.get("shop.find.collector", String.join(", ", names)));
            line.getStyleClass().add("find-line");
            line.setWrapText(true);
            lines.add(line);
        }
        // En el mostrador de sueltas: comprarla directamente es mas barato que
        // buscarla a sobres, y el que empieza no lo sabe.
        PaperCard single = null;
        for (final PaperCard c : NeoQuestShop.singles()) {
            if (c.getName().equals(findName)) {
                single = c;
                break;
            }
        }
        final List<javafx.scene.Node> actions = new ArrayList<>();
        if (single != null) {
            final Label line = new Label(NeoText.get("shop.find.single", NeoQuestShop.priceOfCard(single)));
            line.getStyleClass().addAll("find-line", "find-single");
            lines.add(line);
            final Button go = new Button(NeoText.get("shop.find.goSingles"));
            go.getStyleClass().add("btn-secondary");
            final PaperCard target = single;
            go.setOnAction(e -> {
                // A la carta, no solo a la pestanya: el mostrador va de 24 en 24.
                ((Button) tabBar.getChildren().get(Tab.SINGLES.ordinal())).fire();
                singleSearch.setText(forge.neo.card.CardText.nameOf(target));
            });
            actions.add(go);
        }
        if (findOdds.isEmpty() && (NeoQuest.chosenSets() != null || NeoQuest.worldSets() != null)) {
            final Label line = new Label(NeoText.get("shop.find.limited"));
            line.getStyleClass().add("find-line");
            line.setWrapText(true);
            lines.add(line);
        }

        final VBox text = new VBox(3);
        text.getChildren().setAll(lines);
        HBox.setHgrow(text, Priority.ALWAYS);
        final javafx.scene.Node thumb;
        if (card != null) {
            final forge.neo.card.CardNode node = new forge.neo.card.CardNode(UiScale.px(62));
            node.setRotationEnabled(false);
            node.setBadgesVisible(false);
            final PaperCard face = best == null ? card : best.getPull().getCard();
            node.setCard(forge.game.card.CardView.getCardForUi(face));
            node.setCursor(javafx.scene.Cursor.HAND);
            node.setOnMouseClicked(e -> CardZoom.show(node, forge.game.card.CardView.getCardForUi(face)));
            thumb = node;
        } else {
            thumb = new Region();
        }
        final Button clear = new Button(NeoText.get("shop.find.clear"));
        clear.setId("shop-find-clear");
        clear.getStyleClass().add("btn-secondary");
        clear.setOnAction(e -> clearFind(true));
        actions.add(clear);
        final HBox buttons = new HBox(8);
        buttons.getChildren().setAll(actions);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        final HBox row = new HBox(14, thumb, text, buttons);
        row.setAlignment(Pos.CENTER_LEFT);
        findBanner.getChildren().setAll(row);
        findBanner.setManaged(true);
        findBanner.setVisible(true);
    }

    /**
     * Lo que cuesta el sobre de esa expansion.
     *
     * <p>Se cachea porque montar el sobre para preguntar el precio no es
     * gratis, y esto se llama por cada casilla en cada repintado.
     */
    private int priceOf(final CardEdition e) {
        return priceCache.computeIfAbsent(e.getCode(),
                code -> NeoQuestShop.priceOf(NeoQuestShop.boosterOf(e)));
    }

    private void choose(final CardEdition edition) {
        this.chosen = edition;
        this.pack = NeoQuestShop.boosterOf(edition);
        paintEditions();
        refreshPack();
    }

    private void refreshPack() {
        quantityLabel.setText("\u00d7" + quantity);
        fewer.setDisable(quantity <= 1);
        preview.setDisable(chosen == null);
        if (pack == null) {
            packName.setText(NeoText.get(chosen == null ? "shop.pickCollector" : "shop.noPack"));
            packPrice.setText("");
            buy.setDisable(true);
            buy.setText(NeoText.get("shop.buy"));
            more.setDisable(true);
            return;
        }
        final int price = NeoQuestShop.priceOf(pack);
        final int total = price * quantity;
        final boolean short_ = NeoQuest.credits() < total;
        packName.setText(pack.getName());
        if (quantity == 1) {
            packPrice.setText(NeoText.get(!opening && short_ ? "shop.cannotAfford" : "shop.packPrice",
                    price, pack.getTotalCards()));
            buy.setText(NeoText.get("shop.buy"));
        } else {
            packPrice.setText(NeoText.get(!opening && short_ ? "shop.cannotAffordMany" : "shop.packPriceMany",
                    quantity, total, pack.getTotalCards() * quantity));
            buy.setText(NeoText.get("shop.buyMany", quantity));
        }
        buy.setDisable(opening || short_);
        // Mas solo si llega: subir a un numero que no puedes pagar es apagar
        // el boton de comprar sin que se vea por que.
        more.setDisable(quantity >= MAX_QUANTITY || NeoQuest.credits() < price * (quantity + 1));
    }

    private void setQuantity(final int n) {
        quantity = Math.max(1, Math.min(MAX_QUANTITY, n));
        refreshPack();
    }

    private void refreshMoney() {
        credits.setText(String.valueOf(NeoQuest.credits()));
    }

    // ---------------------------------------------------------------

    /**
     * Comprar y abrir.
     *
     * <p>Las cartas van a tu coleccion en el mismo momento — lo hace el motor
     * al cobrar — asi que esta pantalla solo tiene que ensenyarlas. No hay
     * "guardar": un sobre abierto no se devuelve.
     */
    private boolean open() {
        if (pack == null || opening) {
            return false;
        }
        final int count = quantity;
        final NeoQuestShop.Opened opened = count == 1
                ? NeoQuestShop.buyAndOpen(pack)
                : NeoQuestShop.buyAndOpen(chosen, count);
        if (opened == null) {
            return false;
        }
        opening = true;
        refreshMoney();
        overlay.setOnBackgroundClick(this::closeOpened);
        final PackOpening panel = openedPanel(opened, count);
        // Y otro igual sin salir de aqui, al lado de volver: ver setAgain.
        final int next = NeoQuestShop.priceOf(pack) * count;
        panel.setAgain((count == 1 ? NeoText.get("shop.again") : NeoText.get("shop.againMany", count))
                        + "  \u00b7  " + NeoText.get("shop.credits.short", next),
                NeoQuest.credits() >= next, () -> {
                    opening = false;
                    return open();
                });
        overlay.show(panel);

        // El sobre siguiente es otro sobre: se genera uno nuevo para la misma
        // expansion. Sin esto volverias a "abrir" exactamente las mismas cartas,
        // porque SealedProduct memoriza su contenido.
        this.pack = NeoQuestShop.boosterOf(chosen);
        // Y OJO: "opening" sigue puesto hasta que cierres lo que has abierto.
        // Mientras miras tus cartas el boton de comprar esta apagado, porque un
        // sobre comprado no se devuelve y una pulsacion de mas detras del
        // dialogo te cuesta el dinero sin que llegues a verlo.
        refreshPack();
        reload();

        if (onOpened != null) {
            onOpened.run();
        }
        return true;
    }

    /**
     * Lo que ha salido, ocupando la pantalla.
     *
     * <p>La forma es {@link CardHaul}, la misma que sale al ganar un duelo: dar
     * cartas es dar cartas, y verlo distinto segun de donde vengan no aporta
     * nada.
     */
    private PackOpening openedPanel(final NeoQuestShop.Opened opened, final int count) {
        final PackOpening panel = new PackOpening(
                NeoText.get("shop.opened", count == 1 ? pack.getName() : count + " \u00d7 " + pack.getName()),
                NeoText.get("shop.openedDetail", opened.getCards().size(),
                        opened.getNewCount(), opened.getPaid()),
                opened.getCards(), opened::isNew,
                getWidth(), getHeight(),
                this::closeOpened);
        panel.setWanted(opened::wantedIn);
        panel.setValue(NeoQuestSell::priceOf);
        return panel;
    }

    private void closeOpened() {
        overlay.hide();
        opening = false;
        refreshPack();
        if (tab == Tab.SINGLES) {
            reloadSingles();
        } else if (tab == Tab.LAIR) {
            refreshLair();
        } else if (tab == Tab.SELL) {
            reloadSell();
        } else if (tab == Tab.COLLECTOR && collectorPane != null) {
            collectorPane.reload();
        } else if (tab == Tab.PRECONS && preconsPane != null) {
            preconsPane.reload();
        } else if (tab == Tab.COMMANDER && commanderPane != null) {
            commanderPane.reload();
        } else if (tab == Tab.BOXES && boxesPane != null) {
            boxesPane.reload();
        } else if (tab == Tab.JUMPSTART && jumpstartPane != null) {
            // Para que salga su "Comprado xN" (QuestPurchases).
            jumpstartPane.reload();
        }
    }

    /**
     * Como se presenta una expansion en la pestanya de colector.
     *
     * <p>Se dicen las hojas de arte que trae, y no es un detalle tecnico: es lo
     * unico que distingue un sobre de colector de uno normal caro. "Borderless,
     * extended art" es la razon por la que pagas el triple.
     */
    private static String collectorLabel(final CardEdition e) {
        return String.join(", ", NeoQuestShop.artSheetsOf(e));
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }

    // ---------------------------------------------------------------
    // Herramientas de prueba
    // ---------------------------------------------------------------

    /**
     * Pulsa el boton de comprar, como haria una persona.
     *
     * <p>Se dispara el boton de verdad en vez de llamar a {@code open()}: asi
     * la prueba comprueba tambien que el boton esta activo y enganchado, que es
     * la mitad de lo que puede estar roto.
     */
    /**
     * Pulsa "otro sobre" en la apertura que este puesta, como una persona.
     *
     * @return false si no esta, o esta apagado
     */
    /** Lo que trae el sobre que se compraria ahora (para las pruebas: --shop-auto). */
    public List<PaperCard> packPreview() {
        return pack == null ? List.of() : pack.getCards();
    }

    public boolean autoAgain() {
        final javafx.scene.Node n = getScene() == null ? null : getScene().lookup("#opening-again");
        if (n instanceof Button b && b.isVisible() && !b.isDisabled()) {
            b.fire();
            return true;
        }
        return false;
    }

    public void autoOpen() {
        switch (tab) {
            case SINGLES:
                // Hay que elegir carta antes: el boton solo se enciende con una
                // elegida, que es justo lo que la prueba tiene que recorrer.
                if (chosenSingle == null && !singlesShown.isEmpty()) {
                    chosenSingle = singlesShown.get(0);
                    refreshSingle();
                }
                buySingle.fire();
                break;
            case COLLECTOR:
                if (collectorPane != null) {
                    collectorPane.autoBuy();
                }
                break;
            case PRECONS:
                if (preconsPane != null) {
                    preconsPane.autoBuy();
                }
                break;
            case COMMANDER:
                if (commanderPane != null) {
                    commanderPane.autoBuy();
                }
                break;
            case BOXES:
                if (boxesPane != null) {
                    boxesPane.autoBuy();
                }
                break;
            case LAIR:
                buyLair.fire();
                break;
            case PACKS:
            default:
                buy.fire();
                break;
        }
    }

    /** Se avisa justo despues de abrir un sobre. Para poder capturar a mitad. */
    public void setOnOpened(final Runnable hook) {
        this.onOpened = hook;
    }

    private Runnable onOpened;

    // ---------------------------------------------------------------

    /**
     * Un mostrador de productos: preconstruidos, cajas, lo que venga.
     *
     * <p>Es <b>la misma forma que la pestanya de sobres</b>, y eso no es un
     * capricho: buscador, rejilla de casillas con el precio a la vista,
     * paginador y, abajo del todo, lo elegido con su boton. Estos mostradores
     * eran una lista de renglones de punta a punta de la pantalla y se notaba
     * al cambiar de pestanya — el jugador lo dijo tal cual: <i>"queda raro, es
     * distinto al resto"</i>. Dos formas distintas para la misma tarea obligan
     * a reaprender donde esta cada cosa en cada pestanya.
     *
     * <p>Lo que hay que no equivocarse es siempre lo mismo — <b>elegir no
     * compra</b> — y teniendolo en una sola pieza no hay dos sitios donde se
     * pueda colar.
     *
     * <p>Lo unico que cambia de un mostrador a otro es <b>como se presenta</b>
     * cada producto, y eso son cuatro funciones opcionales: la etiqueta
     * pequenya de arriba ({@link #showCode}), la linea de debajo del nombre
     * ({@link #showNote}), la <b>carta</b> que lo representa
     * ({@link #showFace}) y el texto largo del pie ({@link #showDetail}). Un
     * mostrador que no las pone sale como una casilla de nombre y precio, igual
     * que las expansiones.
     *
     * @param <T> el tipo de producto
     */
    private final class ProductPage<T> extends VBox {

        private final java.util.function.Supplier<List<T>> stock;
        private final java.util.function.Function<T, String> naming;
        private final java.util.function.Function<T, NeoQuestShop.Opened> purchase;
        private final java.util.function.ToIntFunction<T> pricing;
        private final String haulTitle;
        private final String pickPrompt;

        // Como se presenta cada producto. Todas opcionales.
        private java.util.function.Function<T, String> coding;
        private java.util.function.Function<T, String> noting;
        private java.util.function.Function<T, PaperCard> facing;
        private java.util.function.Function<T, String> detailing;

        private final TextField find = new TextField();
        private final CheckBox affordable = new CheckBox();
        private final FlowPane grid = new FlowPane(10, 10);
        private final Pager paging;
        private final Label chosenName = new Label();
        private final Label chosenPrice = new Label();
        private final Label chosenNote = new Label();
        private final Button take = new Button();
        /** "Que puede salir", solo si la pagina lo pide ({@link #withPreview}). */
        private final Button previewIt = new Button();
        private java.util.function.Consumer<T> previewing;
        private final Label emptyLabel;

        /**
         * Comprar varios de una vez, solo si la pagina lo pide
         * ({@link #withQuantity}). Sin esto el mostrador es de uno en uno, como
         * siempre.
         */
        private java.util.function.BiFunction<T, Integer, NeoQuestShop.Opened> purchaseMany;
        private int many = Math.max(1, Math.min(MAX_QUANTITY, Integer.getInteger("neo.shop.quantity", 1)));
        private final Button fewerIt = new Button("\u2212");
        private final Button moreIt = new Button("+");
        private final Label manyLabel = new Label();
        private final HBox manyBox = new HBox(4, fewerIt, manyLabel, moreIt);

        private List<T> items = new ArrayList<>();
        private List<T> shown = new ArrayList<>();
        private T picked;

        ProductPage(final String caption, final String emptyText, final String pickPrompt,
                    final java.util.function.Supplier<List<T>> stock,
                    final java.util.function.Function<T, String> naming,
                    final java.util.function.Function<T, NeoQuestShop.Opened> purchase,
                    final java.util.function.ToIntFunction<T> pricing,
                    final String haulTitle) {
            this.stock = stock;
            this.naming = naming;
            this.purchase = purchase;
            this.pricing = pricing;
            this.haulTitle = haulTitle;
            this.pickPrompt = pickPrompt;
            this.emptyLabel = new Label(emptyText);
            emptyLabel.getStyleClass().add("home-subtitle");

            paging = new Pager(24, this::paint);

            final Label cap = new Label(caption);
            cap.getStyleClass().add("caption");

            find.setPromptText(NeoText.get("shop.searchProduct"));
            find.getStyleClass().add("text-input");
            find.setPrefColumnCount(20);
            find.textProperty().addListener((o, was, is) -> {
                paging.reset();
                filter();
            });

            affordable.setText(NeoText.get("shop.affordable"));
            affordable.getStyleClass().add("shop-filter");
            affordable.setOnAction(e -> {
                paging.reset();
                filter();
            });

            final Region gapTools = new Region();
            HBox.setHgrow(gapTools, Priority.ALWAYS);
            final HBox tools = new HBox(12, find, affordable, gapTools);
            tools.setAlignment(Pos.CENTER_LEFT);

            grid.setAlignment(Pos.TOP_LEFT);
            final ScrollPane scroll = new ScrollPane(grid);
            scroll.getStyleClass().add("dialog-scroll");
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            VBox.setVgrow(scroll, Priority.ALWAYS);

            chosenName.getStyleClass().add("quest-deck-name");
            chosenPrice.getStyleClass().add("shop-price");
            // De que va el producto, cuando el motor lo cuenta: un
            // preconstruido trae su descripcion escrita en el fichero, y por el
            // nombre solo no hay forma de saber a que juega.
            chosenNote.getStyleClass().add("home-subtitle");
            chosenNote.setWrapText(true);
            chosenNote.setMaxWidth(UiScale.px(720));
            chosenNote.setMinHeight(Region.USE_PREF_SIZE);
            chosenNote.setVisible(false);
            chosenNote.setManaged(false);

            take.getStyleClass().add("btn-primary");
            take.setMinWidth(Region.USE_PREF_SIZE);
            take.setText(NeoText.get("shop.buyCard"));
            take.setOnAction(e -> buyPicked());

            final Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            previewIt.setText(NeoText.get("shop.preview"));
            previewIt.getStyleClass().add("btn-secondary");
            previewIt.setMinWidth(Region.USE_PREF_SIZE);
            previewIt.setManaged(false);
            previewIt.setVisible(false);
            previewIt.setOnAction(e -> {
                if (previewing != null && picked != null) {
                    previewing.accept(picked);
                }
            });
            // Cuantos: pegado a comprar, igual que en la pestanya de sobres.
            manyLabel.getStyleClass().add("quest-deck-name");
            manyLabel.setMinWidth(UiScale.px(44));
            manyLabel.setAlignment(Pos.CENTER);
            fewerIt.getStyleClass().add("segment");
            moreIt.getStyleClass().add("segment");
            fewerIt.setOnAction(e -> setMany(many - 1));
            moreIt.setOnAction(e -> setMany(many + 1));
            manyBox.setAlignment(Pos.CENTER);
            manyBox.setMinWidth(Region.USE_PREF_SIZE);
            final javafx.scene.control.Tooltip howMany =
                    new javafx.scene.control.Tooltip(NeoText.get("shop.quantity"));
            javafx.scene.control.Tooltip.install(manyBox, howMany);
            fewerIt.setTooltip(howMany);
            moreIt.setTooltip(howMany);
            manyBox.setManaged(false);
            manyBox.setVisible(false);

            final HBox row = new HBox(16,
                    new VBox(2, chosenName, chosenPrice, chosenNote), gap, previewIt, manyBox, take);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("stat-tile");
            row.setPadding(new Insets(14, 18, 14, 18));

            setSpacing(12);
            getChildren().addAll(cap, tools, scroll, paging, row);
        }

        /** La etiqueta pequenya de arriba de la casilla (el codigo del set). */
        ProductPage<T> showCode(final java.util.function.Function<T, String> f) {
            this.coding = f;
            return this;
        }

        /** Un boton "Que puede salir" junto a comprar. */
        ProductPage<T> withPreview(final java.util.function.Consumer<T> f) {
            this.previewing = f;
            previewIt.setManaged(f != null);
            previewIt.setVisible(f != null);
            return this;
        }

        /** Lo mismo con otro rotulo: en un mazo no "puede salir" nada, se ve lo que trae. */
        ProductPage<T> withPreview(final java.util.function.Consumer<T> f, final String label) {
            previewIt.setText(label);
            return withPreview(f);
        }

        /**
         * Un selector de cuantos junto a comprar, y "otros N" al abrirlos. Para
         * lo que es un sobre: el mismo producto se puede comprar otra vez.
         */
        ProductPage<T> withQuantity(final java.util.function.BiFunction<T, Integer, NeoQuestShop.Opened> f) {
            this.purchaseMany = f;
            manyBox.setManaged(f != null);
            manyBox.setVisible(f != null);
            return this;
        }

        private void setMany(final int n) {
            many = Math.max(1, Math.min(MAX_QUANTITY, n));
            refresh();
        }

        /** Cuantos compra el boton: siempre 1 si la pagina no lo pide. */
        private int count() {
            return purchaseMany == null ? 1 : many;
        }

        /** La linea suelta debajo del nombre (las hojas de arte, el tipo). */
        ProductPage<T> showNote(final java.util.function.Function<T, String> f) {
            this.noting = f;
            return this;
        }

        /** La carta con la que se ensenya el producto. */
        ProductPage<T> showFace(final java.util.function.Function<T, PaperCard> f) {
            this.facing = f;
            return this;
        }

        /** El texto largo del pie, al elegirlo. */
        ProductPage<T> showDetail(final java.util.function.Function<T, String> f) {
            this.detailing = f;
            return this;
        }

        void reload() {
            items = stock.get();
            if (picked != null && !items.contains(picked)) {
                picked = null;
            }
            filter();
            refreshMoney();
        }

        /** Lo que pasa el buscador y el filtro de precio. */
        private void filter() {
            final String q = find.getText() == null ? ""
                    : find.getText().trim().toLowerCase(Locale.ROOT);
            shown = new ArrayList<>();
            for (final T item : items) {
                if (!q.isEmpty() && !forge.neo.deck.SearchTerms.matches(q, haystack(item))) {
                    continue;
                }
                if (affordable.isSelected() && NeoQuest.credits() < pricing.applyAsInt(item)) {
                    continue;
                }
                shown.add(item);
            }
            paging.setTotal(shown.size());
            paint();
            refresh();
        }

        private String haystack(final T item) {
            final StringBuilder sb = new StringBuilder(naming.apply(item));
            if (coding != null) {
                sb.append(' ').append(nullSafe(coding.apply(item)));
            }
            if (noting != null) {
                sb.append(' ').append(nullSafe(noting.apply(item)));
            }
            return sb.toString().toLowerCase(Locale.ROOT);
        }

        /**
         * Solo la pagina actual.
         *
         * <p>Mismo motivo que en las expansiones: con la carta pintada, montar
         * cientos de casillas de golpe deja la pantalla pegada — y recortar sin
         * paginador seria mentir sobre lo que hay en el mostrador.
         */
        private void paint() {
            grid.getChildren().clear();
            if (shown.isEmpty()) {
                grid.getChildren().add(items.isEmpty() ? emptyLabel : noMatch());
                return;
            }
            for (int i = paging.from(); i < Math.min(shown.size(), paging.to()); i++) {
                grid.getChildren().add(tileFor(shown.get(i)));
            }
        }

        private Label noMatch() {
            final Label l = new Label(NeoText.get("shop.noMatch"));
            l.getStyleClass().add("home-subtitle");
            return l;
        }

        /**
         * Una casilla, con la misma forma que las de la pestanya de sobres.
         *
         * <p>Y con la <b>carta</b> arriba cuando el mostrador la da. Un
         * preconstruido en una lista es solo un nombre: por <i>"Abzan
         * Siege"</i> no se sabe ni de que color es, ni quien lo lidera, ni a
         * que juega. Con la carta encima se sabe sin leer nada, que es el
         * principio 3 de las notas de diseño aplicado fuera de la mesa.
         */
        private Region tileFor(final T item) {
            final VBox tile = new VBox(3);
            tile.getStyleClass().add("set-tile");
            tile.setPadding(new Insets(8, 12, 8, 12));

            double text = 166;
            if (facing != null) {
                final double artW = cardWidth * 0.86;
                text = artW;
                final CardNode node = new CardNode(artW);
                node.setRotationEnabled(false);
                node.setBadgesVisible(false);
                final PaperCard face = facing.apply(item);
                if (face != null) {
                    node.setCard(forge.game.card.CardView.getCardForUi(face));
                }
                tile.getChildren().add(node);
            }
            tile.setPrefWidth(text + 24);

            if (coding != null) {
                final String code = nullSafe(coding.apply(item));
                if (!code.isEmpty()) {
                    final Label c = new Label(code);
                    c.getStyleClass().add("set-code");
                    tile.getChildren().add(c);
                }
            }

            final Label name = new Label(naming.apply(item));
            name.getStyleClass().add("set-name");
            name.setWrapText(true);
            name.setMaxWidth(text);
            name.setMinHeight(Region.USE_PREF_SIZE);
            tile.getChildren().add(name);

            if (noting != null) {
                final String note = nullSafe(noting.apply(item));
                if (!note.isEmpty()) {
                    final Label n = new Label(note);
                    n.getStyleClass().add("set-note");
                    n.setWrapText(true);
                    n.setMaxWidth(text);
                    n.setMinHeight(Region.USE_PREF_SIZE);
                    tile.getChildren().add(n);
                }
            }

            // El precio, EN LA CASILLA: sin verlo aqui hay que ir clicando
            // producto por producto para saber cual te puedes permitir.
            final int price = pricing.applyAsInt(item);
            final Label cost = new Label(NeoText.get("shop.credits.short", price));
            cost.getStyleClass().add(
                    NeoQuest.credits() >= price ? "set-price-ok" : "set-price-no");
            tile.getChildren().add(cost);

            // Y si ya lo has comprado, cuantas veces (Discord, 28-10-2026: el
            // mazo de Jace solo se notaba por "0 que te faltan").
            final int bought = forge.neo.quest.QuestPurchases.count(item);
            if (bought > 0) {
                final Label b = new Label(NeoText.get("shop.bought", bought));
                b.getStyleClass().add("set-bought");
                b.setMinWidth(Region.USE_PREF_SIZE);
                tile.getChildren().add(b);
            }

            tile.pseudoClassStateChanged(PICKED, item == picked);
            tile.setOnMouseClicked(ev -> {
                if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    picked = item;
                    paint();
                    refresh();
                }
            });
            return tile;
        }

        private void refresh() {
            previewIt.setDisable(picked == null);
            final int n = count();
            manyLabel.setText("\u00d7" + n);
            fewerIt.setDisable(n <= 1);
            take.setText(n == 1 ? NeoText.get("shop.buyCard") : NeoText.get("shop.buyMany", n));
            if (picked == null) {
                chosenName.setText(pickPrompt);
                chosenPrice.setText("");
                setDetail(null);
                take.setDisable(true);
                moreIt.setDisable(n >= MAX_QUANTITY);
                return;
            }
            final int price = pricing.applyAsInt(picked);
            final int total = price * n;
            final boolean afford = NeoQuest.credits() >= total;
            chosenName.setText(naming.apply(picked));
            if (n == 1) {
                chosenPrice.setText(afford ? NeoText.get("shop.cardPrice", price)
                        : NeoText.get("shop.cannotAfford", price));
            } else {
                chosenPrice.setText(NeoText.get(afford ? "shop.priceMany" : "shop.cannotAffordMany", n, total));
            }
            setDetail(detailing == null ? null : detailing.apply(picked));
            take.setDisable(opening || !afford);
            // Mas solo si llega, como en los sobres normales: subir a un numero
            // que no puedes pagar apaga comprar sin que se vea por que.
            moreIt.setDisable(n >= MAX_QUANTITY || NeoQuest.credits() < price * (n + 1));
        }

        private void setDetail(final String text) {
            final boolean has = text != null && !text.isBlank();
            chosenNote.setText(has ? text.trim() : "");
            chosenNote.setVisible(has);
            chosenNote.setManaged(has);
        }

        private void buyPicked() {
            if (picked == null || opening) {
                return;
            }
            buy(picked, count());
        }

        private boolean buy(final T item, final int n) {
            final String productName = n == 1 ? naming.apply(item) : n + " \u00d7 " + naming.apply(item);
            final NeoQuestShop.Opened bought = n == 1 || purchaseMany == null
                    ? purchase.apply(item) : purchaseMany.apply(item, n);
            if (bought == null) {
                return false;
            }
            opening = true;
            // Con cantidad se queda elegido: el mismo sobre se vuelve a comprar.
            // Sin ella (un preconstruido, una caja) puede que ya no este.
            if (purchaseMany == null) {
                picked = null;
            }
            refresh();
            refreshMoney();
            overlay.setOnBackgroundClick(QuestShopScreen.this::closeOpened);
            if (tab == Tab.BOXES || tab == Tab.COLLECTOR) {
                final PackOpening panel = new PackOpening(haulTitle + " · " + productName,
                        NeoText.get("shop.openedDetail", bought.getCards().size(), bought.getNewCount(), bought.getPaid()),
                        bought.getCards(), bought::isNew, QuestShopScreen.this.getWidth(), QuestShopScreen.this.getHeight(),
                        QuestShopScreen.this::closeOpened);
                panel.setWanted(bought::wantedIn);
                panel.setValue(NeoQuestSell::priceOf);
                if (purchaseMany != null) {
                    // Otro igual sin salir de aqui, como en los sobres normales.
                    final int next = pricing.applyAsInt(item) * n;
                    panel.setAgain((n == 1 ? NeoText.get("shop.again") : NeoText.get("shop.againMany", n))
                                    + "  \u00b7  " + NeoText.get("shop.credits.short", next),
                            NeoQuest.credits() >= next, () -> {
                                opening = false;
                                return buy(item, n);
                            });
                }
                overlay.show(panel);
            } else overlay.show(CardHaul.panel(
                    haulTitle,
                    NeoText.get("shop.openedDetail", bought.getCards().size(),
                            bought.getNewCount(), bought.getPaid()),
                    bought.getCards(), bought::isNew, null,
                    // El de la PANTALLA, como en la rama de arriba: aqui dentro
                    // getHeight() es el de esta pagina (~medio alto), y el
                    // panel se media para un hueco que no es el suyo.
                    cardWidth, QuestShopScreen.this.getWidth(), QuestShopScreen.this.getHeight(),
                    QuestShopScreen.this::closeOpened));
            if (onOpened != null) {
                onOpened.run();
            }
            return true;
        }

        /** Herramienta de prueba: elige el primero y pulsa el boton de verdad. */
        void autoBuy() {
            pickFirst();
            take.fire();
        }

        /** Herramienta de prueba: elige el primero, para ver el pie (-Dneo.shop.pickFirst). */
        void pickFirst() {
            if (picked == null && !shown.isEmpty()) {
                picked = shown.get(0);
                paint();
                refresh();
            }
        }
    }

    private static String nullSafe(final String s) {
        return s == null ? "" : s;
    }
}
