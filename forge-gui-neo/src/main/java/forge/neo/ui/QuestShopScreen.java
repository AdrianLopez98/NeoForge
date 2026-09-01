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

    // --- pestanyas ---
    private enum Tab { PACKS, COLLECTOR, SINGLES, PRECONS, BOXES, LAIR, SELL }

    private Tab tab = Tab.PACKS;
    private final HBox tabBar = new HBox(6);
    private final StackPane page = new StackPane();

    // --- cartas sueltas ---
    private final TextField singleSearch = new TextField();
    private final FlowPane singleGrid = new FlowPane(10, 10);
    private final Pager singlePager;
    private final Label singleName = new Label();
    private final Label singlePrice = new Label();
    private final Button buySingle = new Button();
    private List<PaperCard> singles = new ArrayList<>();
    private List<PaperCard> singlesShown = new ArrayList<>();
    private PaperCard chosenSingle;

    // --- preconstruidos y cajas ---
    //
    // Los dos mostradores son la misma pieza con distinto genero: una lista de
    // productos con su precio, uno elegido y un boton. Se comparte
    // ({@link ProductPage}) porque duplicarla es duplicar tambien el cuidado de
    // "elegir no compra", que es lo que de verdad hay que no equivocarse.
    private ProductPage<CardEdition> collectorPane;
    private ProductPage<forge.item.PreconDeck> preconsPane;
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
    private final CheckBox sellSpareOnly = new CheckBox();
    private final FlowPane sellGrid = new FlowPane(10, 10);
    private final Pager sellPager;
    private final Label sellName = new Label();
    private final Label sellPrice = new Label();
    private final Label sellNote = new Label();
    private final Button sellOne = new Button();
    private final Button sellSpare = new Button();
    private final Button sellAll = new Button();
    private List<PaperCard> sellAllCards = new ArrayList<>();
    private List<PaperCard> sellShown = new ArrayList<>();
    private PaperCard chosenSell;

    public QuestShopScreen(final double cardWidth, final Actions actions) {
        this.cardWidth = cardWidth;
        this.actions = actions;
        getStyleClass().addAll("table-root", "quest");

        pager = new Pager(30, this::paintEditions);
        singlePager = new Pager(24, this::paintSingles);
        sellPager = new Pager(24, this::paintSell);

        frame.setTop(header());
        frame.setCenter(body());
        getChildren().addAll(frame, overlay);
        reload();

        // Para poder capturar una pestanya concreta sin tocar el raton.
        final int forced = Integer.getInteger("neo.shop.tab", -1);
        if (forced > 0 && forced < Tab.values().length) {
            tab = Tab.values()[forced];
            for (int i = 0; i < tabBar.getChildren().size(); i++) {
                tabBar.getChildren().get(i).pseudoClassStateChanged(SELECTED, i == forced);
            }
            showPage();
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

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(14, new VBox(2, title, sub), gap, money, back);
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
        sets.setPrefHeight(190);

        final Label cap = new Label(NeoText.get("shop.editions"));
        cap.getStyleClass().add("caption");

        // --- el sobre elegido ---
        packName.getStyleClass().add("quest-deck-name");
        packPrice.getStyleClass().add("shop-price");
        buy.getStyleClass().add("btn-primary");
        buy.setMinWidth(Region.USE_PREF_SIZE);
        buy.setOnAction(e -> open());

        final Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);
        final HBox packRow = new HBox(16, new VBox(2, packName, packPrice), gap2, buy);
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
        final HBox tools = new HBox(12, search, affordableOnly, gapTools);
        tools.setAlignment(Pos.CENTER_LEFT);

        final VBox content = new VBox(12, cap, tools, sets, pager, packRow);

        final ScrollPane sp = new ScrollPane(content);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        packsPage = sp;

        tabBar.setAlignment(Pos.CENTER_LEFT);
        tabBar.getChildren().addAll(
                tabButton(NeoText.get("shop.tab.packs"), Tab.PACKS),
                tabButton(NeoText.get("shop.tab.collector"), Tab.COLLECTOR),
                tabButton(NeoText.get("shop.tab.singles"), Tab.SINGLES),
                tabButton(NeoText.get("shop.tab.precons"), Tab.PRECONS),
                tabButton(NeoText.get("shop.tab.boxes"), Tab.BOXES),
                tabButton(NeoText.get("shop.tab.lair"), Tab.LAIR),
                tabButton(NeoText.get("shop.tab.sell"), Tab.SELL));

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
                            .showNote(QuestShopScreen::collectorLabel);
                }
                page.getChildren().setAll(collectorPane);
                collectorPane.reload();
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
                            .showDetail(forge.item.PreconDeck::getDescription);
                }
                page.getChildren().setAll(preconsPane);
                preconsPane.reload();
                break;
            case BOXES:
                if (boxesPane == null) {
                    boxesPane = new ProductPage<forge.item.SealedProduct>(
                            NeoText.get("shop.boxes.caption"),
                            NeoText.get("shop.boxes.empty"),
                            NeoText.get("shop.pickBox"),
                            NeoQuestShop::boxes,
                            forge.item.SealedProduct::getName,
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

        final Label cap = new Label(NeoText.get("shop.singles.caption"));
        cap.getStyleClass().add("caption");

        final Region gapTools = new Region();
        HBox.setHgrow(gapTools, Priority.ALWAYS);
        final HBox tools = new HBox(12, singleSearch, gapTools);
        tools.setAlignment(Pos.CENTER_LEFT);

        singleGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(singleGrid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        singleName.getStyleClass().add("quest-deck-name");
        singlePrice.getStyleClass().add("shop-price");
        buySingle.getStyleClass().add("btn-primary");
        buySingle.setMinWidth(Region.USE_PREF_SIZE);
        buySingle.setText(NeoText.get("shop.buyCard"));
        buySingle.setOnAction(e -> takeSingle());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(16, new VBox(2, singleName, singlePrice), gap, buySingle);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stat-tile");
        row.setPadding(new Insets(14, 18, 14, 18));

        final VBox box = new VBox(12, cap, tools, scroll, singlePager, row);
        return box;
    }

    private void reloadSingles() {
        singles = NeoQuestShop.singles();
        final String q = singleSearch.getText() == null ? ""
                : singleSearch.getText().trim().toLowerCase(Locale.ROOT);
        singlesShown = new ArrayList<>();
        for (final PaperCard c : singles) {
            // Se busca por el nombre que se VE, que en castellano no es el
            // ingles. Es la misma regla que en el deck builder.
            final String shown = forge.neo.card.CardText.nameOf(c).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || shown.contains(q)
                    || c.getName().toLowerCase(Locale.ROOT).contains(q)) {
                singlesShown.add(c);
            }
        }
        singlePager.setTotal(singlesShown.size());
        if (chosenSingle != null && !singlesShown.contains(chosenSingle)) {
            chosenSingle = null;
        }
        paintSingles();
        refreshSingle();
        refreshMoney();
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

    private Region singleTile(final PaperCard card) {
        final CardNode node = new CardNode(cardWidth * 0.82);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(forge.game.card.CardView.getCardForUi(card));

        final int price = NeoQuestShop.priceOfCard(card);
        final Label cost = new Label(NeoText.get("shop.credits.short", price));
        cost.getStyleClass().add(NeoQuest.credits() >= price ? "set-price-ok" : "set-price-no");

        final VBox tile = new VBox(2, node, cost);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(6, 6, 6, 6));
        tile.pseudoClassStateChanged(PICKED, card.equals(chosenSingle));
        tile.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                chosenSingle = card;
                paintSingles();
                refreshSingle();
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

    /** Comprar la carta elegida. Va a tu coleccion y sale del mostrador. */
    private void takeSingle() {
        if (chosenSingle == null || opening) {
            return;
        }
        final NeoQuestShop.Opened bought = NeoQuestShop.buySingle(chosenSingle);
        if (bought == null) {
            return;
        }
        opening = true;
        chosenSingle = null;
        refreshSingle();
        refreshMoney();
        overlay.setOnBackgroundClick(this::closeOpened);
        overlay.show(CardHaul.panel(
                NeoText.get("shop.boughtCard"),
                NeoText.get("shop.openedDetail", bought.getCards().size(),
                        bought.getNewCount(), bought.getPaid()),
                bought.getCards(), bought::isNew, null,
                cardWidth, getWidth(), getHeight(),
                this::closeOpened));
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
        what.setMaxWidth(760);

        final Label why = new Label(NeoText.get("shop.lair.why"));
        why.getStyleClass().add("home-subtitle");
        why.setWrapText(true);
        why.setMaxWidth(760);

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
        lairNote.setMaxWidth(520);

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
     * <p>Dos formas, y las dos hacen falta:
     *
     * <ul>
     *   <li><b>Las repetidas, de golpe</b>: un boton que vende todo lo que
     *       sobra segun las reglas de tu modalidad. Es lo que se usa el 90% de
     *       las veces, y dice cuanto va a dar ANTES de darle.</li>
     *   <li><b>Una carta concreta</b>: la eliges y vendes las copias que
     *       quieras, incluida la ultima. Quien quiera deshacerse de algo
     *       manda.</li>
     * </ul>
     *
     * <p>Y como todo lo que gasta o cobra en esta pantalla: <b>elegir no
     * vende</b>. Vender es de lo poco que no se deshace — lo unico que hay es
     * que la carta vuelve al mostrador, mas cara.
     */
    private Region buildSellPage() {
        sellSearch.setPromptText(NeoText.get("shop.searchCard"));
        sellSearch.getStyleClass().add("text-input");
        sellSearch.setPrefColumnCount(20);
        sellSearch.textProperty().addListener((o, was, is) -> {
            sellPager.reset();
            reloadSell();
        });

        sellSpareOnly.setText(NeoText.get("shop.sell.spareOnly"));
        sellSpareOnly.getStyleClass().add("shop-filter");
        sellSpareOnly.setOnAction(e -> {
            sellPager.reset();
            reloadSell();
        });

        final Label cap = new Label(NeoText.get("shop.sell.caption"));
        cap.getStyleClass().add("caption");

        // --- vender las repetidas de golpe ---
        sellAll.getStyleClass().add("btn-secondary");
        sellAll.setMinWidth(Region.USE_PREF_SIZE);
        sellAll.setOnAction(e -> takeSellAll());

        final Region gapTools = new Region();
        HBox.setHgrow(gapTools, Priority.ALWAYS);
        final HBox tools = new HBox(12, sellSearch, sellSpareOnly, gapTools, sellAll);
        tools.setAlignment(Pos.CENTER_LEFT);

        sellGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(sellGrid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // --- la carta elegida ---
        sellName.getStyleClass().add("quest-deck-name");
        sellPrice.getStyleClass().add("shop-price");
        sellNote.getStyleClass().add("caption");
        sellNote.setWrapText(true);
        sellNote.setMaxWidth(520);

        sellOne.getStyleClass().add("btn-primary");
        sellOne.setMinWidth(Region.USE_PREF_SIZE);
        sellOne.setText(NeoText.get("shop.sell.one"));
        sellOne.setOnAction(e -> takeSell(1));

        sellSpare.getStyleClass().add("btn-secondary");
        sellSpare.setMinWidth(Region.USE_PREF_SIZE);
        sellSpare.setOnAction(e -> takeSell(
                chosenSell == null ? 0 : NeoQuestSell.spareCopies(chosenSell)));

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(12, new VBox(2, sellName, sellPrice, sellNote),
                gap, sellSpare, sellOne);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stat-tile");
        row.setPadding(new Insets(14, 18, 14, 18));

        return new VBox(12, cap, tools, scroll, sellPager, row);
    }

    private void reloadSell() {
        // La coleccion se lee entera cada vez porque vender la cambia, y con
        // una copia vieja se podrian vender cartas que ya no estan.
        sellAllCards = new ArrayList<>();
        for (final java.util.Map.Entry<PaperCard, Integer> e : NeoQuest.collection()) {
            if (e.getKey() != null && e.getValue() > 0) {
                sellAllCards.add(e.getKey());
            }
        }
        sellAllCards.sort(java.util.Comparator.comparing(
                c -> forge.neo.card.CardText.nameOf(c), String.CASE_INSENSITIVE_ORDER));

        final String q = sellSearch.getText() == null ? ""
                : sellSearch.getText().trim().toLowerCase(Locale.ROOT);
        sellShown = new ArrayList<>();
        for (final PaperCard c : sellAllCards) {
            if (sellSpareOnly.isSelected() && NeoQuestSell.spareCopies(c) <= 0) {
                continue;
            }
            // Por el nombre que se VE, como en el resto de la aplicacion.
            final String shown = forge.neo.card.CardText.nameOf(c).toLowerCase(Locale.ROOT);
            if (q.isEmpty() || shown.contains(q)
                    || c.getName().toLowerCase(Locale.ROOT).contains(q)) {
                sellShown.add(c);
            }
        }
        sellPager.setTotal(sellShown.size());
        if (chosenSell != null && !sellShown.contains(chosenSell)) {
            chosenSell = null;
        }
        paintSell();
        refreshSell();
        refreshMoney();
    }

    private void paintSell() {
        sellGrid.getChildren().clear();
        if (sellShown.isEmpty()) {
            final Label empty = new Label(NeoText.get(sellSpareOnly.isSelected()
                    ? "shop.sell.noSpare" : "shop.sell.empty"));
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

        // Cuantas tienes y cuantas SOBRAN, en la propia carta. Sin eso hay que
        // clicarlas una a una para saber cual esta repetida, que es justo lo
        // que se viene a hacer a esta pantalla.
        final Label cost = new Label(NeoText.get("shop.sell.tile", price, have));
        cost.getStyleClass().add(spare > 0 ? "set-price-ok" : "set-price-no");

        final VBox tile = new VBox(2, node, cost);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(6, 6, 6, 6));
        tile.pseudoClassStateChanged(PICKED, card.equals(chosenSell));
        tile.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                chosenSell = card;
                paintSell();
                refreshSell();
            }
        });
        return tile;
    }

    private void refreshSell() {
        // El boton de golpe dice lo que va a dar ANTES de pulsarlo: si no, es
        // un boton que se lleva parte de tu coleccion a cambio de una sorpresa.
        final NeoQuestSell.Sold spare = NeoQuestSell.preview();
        sellAll.setText(spare.isEmpty() ? NeoText.get("shop.sell.allNone")
                : NeoText.get("shop.sell.all", spare.getCopies(), spare.getCredits()));
        sellAll.setDisable(opening || spare.isEmpty());

        if (chosenSell == null) {
            sellName.setText(NeoText.get("shop.sell.pick"));
            sellPrice.setText("");
            sellNote.setText("");
            sellOne.setDisable(true);
            sellSpare.setDisable(true);
            sellSpare.setText(NeoText.get("shop.sell.spare", 0));
            return;
        }
        final int price = NeoQuestSell.priceOf(chosenSell);
        final int have = NeoQuestSell.owned(chosenSell);
        final int extra = NeoQuestSell.spareCopies(chosenSell);
        sellName.setText(forge.neo.card.CardText.nameOf(chosenSell)
                + "  ·  " + chosenSell.getEdition());
        sellPrice.setText(NeoText.get("shop.sell.price", price, have));
        // Avisar cuando vender deja la carta a cero, y decir tambien que se
        // pierde ESE ARTE: en la aventura el arte es contenido, y si esta era
        // tu unica copia de esa impresion, deja de poder ponerse en un mazo.
        sellNote.setText(have <= 1 ? NeoText.get("shop.sell.lastCopy")
                : extra > 0 ? NeoText.get("shop.sell.hasSpare", extra)
                : NeoText.get("shop.sell.noSpareHere"));
        sellOne.setDisable(opening || have <= 0);
        sellSpare.setText(NeoText.get("shop.sell.spare", extra));
        sellSpare.setDisable(opening || extra <= 0);
    }

    private void takeSell(final int quantity) {
        if (chosenSell == null || quantity <= 0 || opening) {
            return;
        }
        final PaperCard card = chosenSell;
        final int got = NeoQuestSell.sellOne(card, quantity);
        if (got <= 0) {
            return;
        }
        // Ni overlay ni panel: no se ha ABIERTO nada, se ha vendido. Ensenyar
        // aqui el mismo panel de "esto es lo que te llevas" seria mentir sobre
        // lo que acaba de pasar.
        if (NeoQuestSell.owned(card) <= 0) {
            chosenSell = null;
        }
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
        chosenSell = null;
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
        for (final CardEdition e : NeoQuestShop.editions()) {
            final boolean matches = q.isEmpty()
                    || e.getName().toLowerCase(Locale.ROOT).contains(q)
                    || e.getCode().toLowerCase(Locale.ROOT).contains(q);
            if (matches && (!affordableOnly.isSelected() || NeoQuest.credits() >= priceOf(e))) {
                filtered.add(e);
            }
        }
        pager.setTotal(filtered.size());
        paintEditions();
        if (chosen == null && !filtered.isEmpty()) {
            choose(filtered.get(0));
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
        name.setMaxWidth(170);
        name.setMinHeight(Region.USE_PREF_SIZE);

        // El precio, EN LA CASILLA. Van de 300 a 35.000 creditos, asi que sin
        // verlo aqui hay que ir clicando expansion por expansion para saber
        // cual te puedes permitir — que es justo la decision que se viene a
        // tomar a esta pantalla.
        final int price = priceOf(e);
        final Label cost = new Label(NeoText.get("shop.credits.short", price));
        cost.getStyleClass().add(NeoQuest.credits() >= price ? "set-price-ok" : "set-price-no");

        final VBox tile = new VBox(3, code, name, cost);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(8, 12, 8, 12));
        tile.setPrefWidth(190);
        tile.pseudoClassStateChanged(PICKED, chosen != null && chosen.getCode().equals(e.getCode()));
        tile.setOnMouseClicked(ev -> choose(e));
        return tile;
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
        if (pack == null) {
            packName.setText(NeoText.get("shop.noPack"));
            packPrice.setText("");
            buy.setDisable(true);
            buy.setText(NeoText.get("shop.buy"));
            return;
        }
        final int price = NeoQuestShop.priceOf(pack);
        packName.setText(pack.getName());
        packPrice.setText(NeoText.get("shop.packPrice", price, pack.getTotalCards()));
        buy.setText(NeoText.get("shop.buy"));
        buy.setDisable(opening || NeoQuest.credits() < price);
        if (!opening && NeoQuest.credits() < price) {
            packPrice.setText(NeoText.get("shop.cannotAfford", price));
        }
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
    private void open() {
        if (pack == null || opening) {
            return;
        }
        final NeoQuestShop.Opened opened = NeoQuestShop.buyAndOpen(pack);
        if (opened == null) {
            return;
        }
        opening = true;
        refreshMoney();
        overlay.setOnBackgroundClick(this::closeOpened);
        overlay.show(openedPanel(opened));

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
    }

    /**
     * Lo que ha salido, ocupando la pantalla.
     *
     * <p>La forma es {@link CardHaul}, la misma que sale al ganar un duelo: dar
     * cartas es dar cartas, y verlo distinto segun de donde vengan no aporta
     * nada.
     */
    private Region openedPanel(final NeoQuestShop.Opened opened) {
        return CardHaul.panel(
                NeoText.get("shop.opened", pack.getName()),
                NeoText.get("shop.openedDetail", opened.getCards().size(),
                        opened.getNewCount(), opened.getPaid()),
                opened.getCards(), opened::isNew, null,
                cardWidth, getWidth(), getHeight(),
                this::closeOpened);
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
        } else if (tab == Tab.BOXES && boxesPane != null) {
            boxesPane.reload();
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
        private final Label emptyLabel;

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
            chosenNote.setMaxWidth(720);
            chosenNote.setMinHeight(Region.USE_PREF_SIZE);
            chosenNote.setVisible(false);
            chosenNote.setManaged(false);

            take.getStyleClass().add("btn-primary");
            take.setMinWidth(Region.USE_PREF_SIZE);
            take.setText(NeoText.get("shop.buyCard"));
            take.setOnAction(e -> buyPicked());

            final Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            final HBox row = new HBox(16,
                    new VBox(2, chosenName, chosenPrice, chosenNote), gap, take);
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
                if (!q.isEmpty() && !haystack(item).contains(q)) {
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
            if (picked == null) {
                chosenName.setText(pickPrompt);
                chosenPrice.setText("");
                setDetail(null);
                take.setDisable(true);
                return;
            }
            final int price = pricing.applyAsInt(picked);
            final boolean afford = NeoQuest.credits() >= price;
            chosenName.setText(naming.apply(picked));
            chosenPrice.setText(afford ? NeoText.get("shop.cardPrice", price)
                    : NeoText.get("shop.cannotAfford", price));
            setDetail(detailing == null ? null : detailing.apply(picked));
            take.setDisable(opening || !afford);
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
            final NeoQuestShop.Opened bought = purchase.apply(picked);
            if (bought == null) {
                return;
            }
            opening = true;
            picked = null;
            refresh();
            refreshMoney();
            overlay.setOnBackgroundClick(QuestShopScreen.this::closeOpened);
            overlay.show(CardHaul.panel(
                    haulTitle,
                    NeoText.get("shop.openedDetail", bought.getCards().size(),
                            bought.getNewCount(), bought.getPaid()),
                    bought.getCards(), bought::isNew, null,
                    cardWidth, getWidth(), getHeight(),
                    QuestShopScreen.this::closeOpened));
            if (onOpened != null) {
                onOpened.run();
            }
        }

        /** Herramienta de prueba: elige el primero y pulsa el boton de verdad. */
        void autoBuy() {
            if (picked == null && !shown.isEmpty()) {
                picked = shown.get(0);
                refresh();
            }
            take.fire();
        }
    }

    private static String nullSafe(final String s) {
        return s == null ? "" : s;
    }
}
