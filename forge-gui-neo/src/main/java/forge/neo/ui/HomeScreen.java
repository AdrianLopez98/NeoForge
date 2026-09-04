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
    private static final String[] AI_PROFILES = {"Cautious", "Default", "Reckless", "Experimental"};

    /** Que hacer al pulsar JUGAR. */
    public interface StartHandler {
        /**
         * @param opponentDecks un mazo por rival; un hueco a null significa
         *                      "el que sea", y lo elige la propia pantalla
         */
        void start(Deck deck, int opponents, String aiProfile, boolean watch,
                   List<Deck> opponentDecks);
    }

    /**
     * Abrir el deck builder.
     *
     * <p>{@code deck} viene a null cuando se quiere empezar uno de cero. Es la
     * misma pantalla en los dos casos: montar y retocar son lo mismo.
     */
    public interface EditHandler {
        void edit(Deck deck);
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

    /** Que pestanya se esta mirando. */
    private enum Tab { MINE, STOCK, NET }

    private Tab showing = Tab.MINE;

    /** Cuantos mazos por pagina. Con cientos de golpe, la rejilla se arrastra. */
    private static final int MAX_TILES = 60;

    private final javafx.scene.control.TextField search = new javafx.scene.control.TextField();
    private final FlowPane grid = new FlowPane(18, 18);
    private final Label gridCount = new Label();
    private final java.util.EnumMap<Tab, Button> tabButtons = new java.util.EnumMap<>(Tab.class);
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
    private final List<Button> opponentButtons = new ArrayList<>();
    private Region opponentRow;

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
                NeoSettings.getInt(NeoSettings.OPPONENTS, format.getDefaultOpponents()), 1, 3);
        this.aiProfile = NeoSettings.get(NeoSettings.AI_PROFILE, "Default");

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
        // Si no tienes ninguno propio, se abre por el catalogo de Forge: una
        // pestanya vacia como primera impresion no ayuda a nadie.
        showing = mine.isEmpty() ? Tab.STOCK : Tab.MINE;

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
        if (initial != null) {
            // Se abre por la pestanya donde de verdad esta ese mazo.
            showing = tabOf(initial);
            markTabs();
            fillGrid();
        } else if (!decks.isEmpty()) {
            initial = source().isEmpty() ? decks.get(0) : source().get(0);
        }
        select(initial);
        maybeShowDeleteTest();
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
        newDeck.setOnAction(e -> onEdit.edit(null));

        unshrinkable(edit).getStyleClass().add("btn-secondary");
        edit.setOnAction(e -> {
            if (selected != null) {
                onEdit.edit(selected);
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

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox bar = new HBox(8);
        bar.getChildren().addAll(tabButtons.values());
        bar.getChildren().addAll(gap, pager, search, gridCount);
        bar.setAlignment(Pos.CENTER_LEFT);
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
            markTabs();
            pager.reset();
            fillGrid();
        });
        return b;
    }

    /** Los mazos de la pestanya que se esta mirando. */
    private List<Deck> source() {
        switch (showing) {
            case STOCK: return stock;
            case NET: return net;
            default: return mine;
        }
    }

    /** En que pestanya vive este mazo. */
    private Tab tabOf(final Deck deck) {
        if (format.isMine(deck)) {
            return Tab.MINE;
        }
        return net.contains(deck) ? Tab.NET : Tab.STOCK;
    }

    private void markTabs() {
        for (final java.util.Map.Entry<Tab, Button> e : tabButtons.entrySet()) {
            e.getValue().pseudoClassStateChanged(SELECTED, e.getKey() == showing);
        }
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
            if (format.canDelete(deck)) {
                tile.setOnDelete(() -> confirmDelete(deck));
            }
            tiles.add(tile);
            grid.getChildren().add(tile);
        }

        if (hits.isEmpty()) {
            final Label empty = new Label(showing == Tab.MINE
                    ? NeoText.get("home.empty.mine", NeoText.get("home.newDeck"))
                    : showing == Tab.NET ? NeoText.get("home.empty.net")
                    : NeoText.get("home.empty.stock"));
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
            if (selected != null && format.canDelete(selected)) {
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
                    if (!format.delete(deck)) {
                        return;
                    }
                    decks.remove(deck);
                    mine.remove(deck);
                    stock.remove(deck);
                    net.remove(deck);
                    if (deck.equals(selected)) {
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
                            NeoSettings.setInt(NeoSettings.OPPONENTS, opponents);
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

        while (opponentDecks.size() < opponents) {
            opponentDecks.add(null);
        }

        final Label caption = new Label(NeoText.get("home.against"));
        caption.getStyleClass().add("caption");
        row.getChildren().add(caption);

        for (int i = 0; i < opponents; i++) {
            final int index = i;
            final Button b = new Button();
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setOnAction(e -> pickOpponentDeck(index));
            opponentButtons.add(b);
            row.getChildren().add(b);
        }
        refreshOpponentLabels();
    }

    private void refreshOpponentLabels() {
        for (int i = 0; i < opponentButtons.size(); i++) {
            final Deck d = i < opponentDecks.size() ? opponentDecks.get(i) : null;
            opponentButtons.get(i).setText(NeoText.get("home.aiDeck", i + 1,
                    d == null ? NeoText.get("home.random") : shorten(d.getName())));
        }
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
        picker = new DeckPickerDialog(NeoText.get("home.pickRival", index + 1), mine, stock,
                tileWidth * 0.72,
                chosen -> {
                    overlay.hide();
                    while (opponentDecks.size() <= index) {
                        opponentDecks.add(null);
                    }
                    opponentDecks.set(index, chosen);
                    refreshOpponentLabels();
                    updateSummary();
                },
                overlay::hide,
                format.isCommanderStyle() ? this::generateOpponentDeck : null);
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
     */
    private forge.deck.Deck generateOpponentDeck() {
        try {
            return forge.deck.DeckgenUtil.generateCommanderDeck(true, format.getGameType());
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
        if (selected == null) {
            return;
        }
        NeoSettings.set(NeoSettings.DECK, selected.getName());
        NeoSettings.setInt(NeoSettings.OPPONENTS, opponents);
        NeoSettings.set(NeoSettings.AI_PROFILE, aiProfile);
        NeoSettings.save();
        onStart.start(selected, opponents, aiProfile, watch, resolvedOpponentDecks());
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
                out.add(chosen);
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
            final Button b = new Button(value);
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
        }
        edit.setDisable(deck == null);
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
        if (selected == null) {
            summary.setText(NeoText.get("home.noDecks"));
            play.setDisable(true);
            return;
        }

        final String problem =
                forge.neo.deck.DeckProblem.translate(format.getGameType()
                        .getDeckFormat().getDeckConformanceProblem(selected));

        if (problem == null) {
            summary.setText(NeoText.get("home.summary",
                    format.getLabel(), selected.getName(), selected.getMain().countAll(),
                    opponents + 1, aiProfile));
        } else {
            summary.setText(NeoText.get("home.summary.invalid",
                    format.getLabel(), selected.getName(), problem));
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
