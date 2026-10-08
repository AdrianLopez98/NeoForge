package forge.neo.ui;

import forge.neo.NeoSettings;
import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import forge.neo.deck.DeckEditor;
import forge.neo.deck.DeckImporter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * El deck builder: montar un mazo entero sin salir de la aplicacion.
 *
 * <p>Dos columnas, como Moxfield: a la izquierda TODAS las cartas de Magic con
 * un buscador, a la derecha el mazo que llevas. Se anyade clicando y se quita
 * clicando; el click derecho amplia la carta, igual que en la mesa, porque es
 * el mismo gesto en todas las pantallas.
 *
 * <p><b>El filtro "solo lo que cabe" es lo que hace util esta pantalla.</b> En
 * cuanto hay comandante, el catalogo deja de ofrecer las 33.000 cartas y
 * ensenya solo las que respetan su identidad de color. Esa regla no la
 * calculamos nosotros: la contesta {@code DeckFormat}, igual que la legalidad
 * del mazo entero.
 *
 * <p>Toda la logica vive en {@link DeckEditor}. Aqui solo hay disposicion,
 * clicks y pintura.
 */
public class DeckBuilderScreen extends StackPane {

    /** Cuantos resultados por pagina. */
    private static final int MAX_RESULTS = 60;

    private final DeckEditor editor;
    private final double cardWidth;
    private final Runnable onBack;

    private final TextField search = new TextField();
    private final FlowPane results = new FlowPane(10, 10);
    private final Label resultCount = new Label();
    private final Pager pager = new Pager(MAX_RESULTS, this::fillResults);

    /**
     * Las cartas SIN TECHO del catalogo, aparte y con su rotulo.
     *
     * <p>En un draft o un sellado el catalogo es tu pool y nada mas, con una
     * excepcion que no se puede quitar: las <b>tierras basicas</b>. No salen de
     * ningun sobre — en limitado se ponen las que hagan falta — pero sin ellas
     * no se puede cambiar la base de mana, que es la primera razon por la que
     * uno abre este editor despues de draftear.
     *
     * <p>Asi que se quedan, pero <b>no mezcladas</b>: entre tus 41 picks,
     * ordenadas por nombre, no habia forma de saber cuales elegiste tu. Van
     * abajo, en su propia fila y con su titulo.
     */
    private final FlowPane basics = new FlowPane(8, 6);
    private final Label basicsCaption = new Label(NeoText.get("deck.basics"));
    /** De que edicion salen las basicas ofrecidas (la Aventura y sus Landscape Sketchbook). */
    private final javafx.scene.control.ComboBox<forge.card.CardEdition> basicSetBox =
            new javafx.scene.control.ComboBox<>();
    private List<PaperCard> basicHits = new ArrayList<>();
    private final VBox deckList = new VBox(2);
    private final HBox commanderRow = new HBox(10);
    /**
     * El companero (Lurrus, Yorion...), debajo del comandante.
     *
     * <p>Vive en el banquillo, que esta lista no ensenya: sin su fila, ponerlo
     * seria invisible y no habria donde clicar para quitarlo. Solo ocupa sitio
     * cuando hay uno.
     */
    private final HBox companionRow = new HBox(10);
    private final ManaCurvePane curve = new ManaCurvePane();
    private final Label title = new Label();
    private final Label status = new Label();
    private final Label deckCount = new Label();
    private final List<Button> filterButtons = new ArrayList<>();
    private final Button filterToggle = new Button();
    private final Overlay overlay = new Overlay();

    /** "Scryfall nos ha limitado, vuelve en Ns." El catalogo es donde mas se pide arte. */
    private final ImageCooldownBadge cooldownBadge = new ImageCooldownBadge();

    /** Las dos vistas del mazo: montarlo (dos columnas) y repasarlo (visual). */
    private final BorderPane layout = new BorderPane();
    private Region editView;
    private DeckStackView stackView;
    private boolean visualMode;
    private Button visualButton;
    /**
     * La funda de ESTE mazo.
     *
     * <p>Va en la cabecera, al lado del nombre, porque es lo mismo: no es una
     * accion sobre las cartas, es como se presenta el mazo en la mesa. Lleva la
     * funda dibujada encima — un boton que solo dijera "Funda" obligaria a
     * abrirlo para saber cual llevas puesta.
     */
    private final Button sleeveButton = new Button(NeoText.get("deck.sleeve"));
    /** Todo el mazo foil, o quitarselo (Discord, 03-10-2026). Ver DeckEditor.setAllFoil. */
    private final Button foilAllButton = new Button(NeoText.get("deck.foilAll"));

    private final Button cleanup = new Button(NeoText.get("deck.cleanup"));
    /**
     * Vaciar el banquillo cuando se pasa del limite del formato (Discord,
     * 06-10-2026): el banquillo no se ve en ninguna pantalla, y un mazo
     * importado con uno grande no se podia arreglar.
     */
    private final Button clearSide = new Button();
    private final Button generate = new Button(NeoText.get("deck.generate"));
    private final Button importer = new Button(NeoText.get("deck.import"));

    /**
     * Los dos botones de limitado: "Montar solo" y "Vaciar el mazo".
     *
     * <p>Salen de un informe de itch.io (23-09-2026): el draft y el sellado
     * montaban el mazo solos y no habia forma de vaciarlo — habia que sacar
     * las cartas una a una. Ahora el mazo sale vacio y montarlo solo es una
     * opcion, no una imposicion. Solo existen donde el pool es la banda.
     */
    private final Button autoBuild = new Button(NeoText.get("deck.autoBuild"));
    private final Button clearMain = new Button(NeoText.get("deck.clearMain"));

    /** Filtros de la barra: colores marcados y "solo lo que cabe". */
    private final List<Byte> colours = new ArrayList<>();

    /** Rarezas encendidas. Vacio = todas. */
    private final java.util.Set<forge.card.CardRarity> rarities =
            java.util.EnumSet.noneOf(forge.card.CardRarity.class);

    /** Costes convertidos encendidos (7 significa "7 o mas"). Vacio = todos. */
    private final java.util.Set<Integer> cmcs = new java.util.TreeSet<>();

    /**
     * Tipos encendidos (Criatura, Instantaneo...). Vacio = todos.
     *
     * <p>De los ocho tipos que de verdad aparecen en un mazo — se dejan fuera
     * Trasfondo, Conspiracion, Mazmorra, Fenomeno, Plano, Confabulacion y
     * Vanguardia, que no se juegan en el mazo principal de ningun formato de
     * los que ofrece NeoForge.
     */
    private final java.util.Set<forge.card.CardType.CoreType> types =
            java.util.EnumSet.noneOf(forge.card.CardType.CoreType.class);

    /** Buscar tambien en el texto de reglas, no solo en el nombre y el tipo. */
    private boolean searchRules;

    private final DeckStatsPane stats = new DeckStatsPane();
    private boolean onlyLegal = true;

    /**
     * El catalogo esta ensenyando comandantes en vez de cartas.
     *
     * <p>Es un modo del MISMO catalogo, no un dialogo aparte, para que valgan el
     * buscador y los filtros de color que ya estan ahi. Elegir comandante es
     * buscar una carta concreta entre miles: exactamente el problema que el
     * catalogo ya resuelve.
     */
    private boolean commanderMode;

    /**
     * Y si lo que se esta eligiendo es el <b>hechizo insignia</b> en vez del
     * comandante.
     *
     * <p>Solo puede ser true en Oathbreaker, el unico formato con dos huecos en
     * la zona de mando ({@code DeckEditor.usesSignatureSpell}). En Commander,
     * Brawl y Tiny Leaders vale siempre false y todo lo que cuelga de el es
     * codigo muerto: por eso ninguno de los caminos de siempre cambia.
     */
    private boolean pickingSpell;

    /**
     * "ELIGIENDO COMANDANTE", al lado del titulo del catalogo.
     *
     * <p>Pedido el 15-09-2026: el boton marcado esta abajo, entre otros, y
     * mirando el catalogo no se sabia si estabas eligiendo comandante o
     * buscando cartas. Se ve donde se mira, y solo mientras dura el modo.
     */
    private final Label pickingBadge = pickingBadge();

    private static Label pickingBadge() {
        final Label l = new Label(NeoText.get("deck.pickingCommander"));
        l.getStyleClass().add("picking-commander-badge");
        l.setMinWidth(Region.USE_PREF_SIZE);
        l.setVisible(false);
        l.setManaged(false);
        return l;
    }

    /**
     * El boton que enciende y apaga ese modo.
     *
     * <p>Es un campo, y no una variable de {@code footer()}, porque el modo
     * <b>se apaga solo</b> al elegir comandante: si el boton no fuera
     * alcanzable desde ahi, se quedaria diciendo "Volver al catalogo" con el
     * catalogo ya vuelto.
     */
    private final Button commanderButton = new Button(NeoText.get("deck.pickCommander"));

    /**
     * Su gemelo para el hechizo insignia. Solo existe en Oathbreaker.
     *
     * <p>Dos botones y no uno que alterne: son dos huecos distintos y los dos
     * hay que rellenarlos: con un solo boton no habria forma de ver, sin
     * pulsarlo, que el mazo necesita tambien un hechizo. En los demas formatos
     * ni se ensenya — un boton apagado seguiria diciendo que aqui hay hechizos
     * insignia, y en Commander no los hay (principio 1).
     */
    private final Button spellButton = new Button(NeoText.get("deck.pickSignature"));

    /**
     * Espera antes de buscar mientras se teclea.
     *
     * <p>Sin esto, cada tecla recorre el catalogo entero y construye hasta 60
     * cartas con sus imagenes. Escribir "lightning" haria ese trabajo nueve
     * veces y solo la ultima importa.
     */
    private final javafx.animation.PauseTransition debounce =
            new javafx.animation.PauseTransition(javafx.util.Duration.millis(180));

    public DeckBuilderScreen(final DeckEditor editor, final double cardWidth,
                             final Runnable onBack) {
        this.editor = editor;
        this.cardWidth = Math.max(144, cardWidth * 1.3);
        this.onBack = onBack;
        // "Solo lo que cabe" encendido o no de salida lo decide el contexto
        // (apagado en la Aventura, pedido jugando el 19-09-2026).
        this.onlyLegal = editor.onlyFitsByDefault();

        getStyleClass().addAll("table-root", "deck-builder");
        // La base de letra del editor: 13 px, crecidos en 2K como el resto del
        // texto (su CSS va en em sobre ella). Fija, el editor entero se leia
        // diminuto en 2K (24-09-2026). Va en `layout` y no en la raiz porque
        // NeoApp.applyScale reescribe el estilo de la raiz de la escena.
        UiScale.fixedFont(layout, 13);

        editView = columns();
        layout.setTop(header());
        layout.setCenter(editView);
        layout.setBottom(footer());

        getChildren().addAll(layout, overlay, cooldownBadge);
        StackPane.setAlignment(cooldownBadge, Pos.BOTTOM_LEFT);
        StackPane.setMargin(cooldownBadge, new Insets(0, 0, 16, 26));

        debounce.setOnFinished(e -> refreshCatalogue());
        search.textProperty().addListener((o, a, b) -> debounce.playFromStart());

        refreshCatalogue();
        refreshDeck();
    }

    // ===============================================================
    // Cabecera

    private Region header() {
        title.getStyleClass().add("builder-title");
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);

        final Button rename = new Button(NeoText.get("deck.rename"));
        rename.getStyleClass().add("btn-secondary");
        rename.setOnAction(e -> askName());
        // El mazo de un draft o un sellado no se renombra: su nombre es tambien
        // el del evento y el de su marcador. Ver DeckContext.canRename.
        final boolean renameable = editor.getFormat().canRename();
        rename.setVisible(renameable);
        rename.setManaged(renameable);
        // GUARDAR UNA COPIA (Discord, 08-10-2026): el mazo tal cual, artes y
        // foil incluidos, con otro nombre. Ver DeckEditor.saveAsCopy.
        final Button copy = new Button(NeoText.get("deck.copy"));
        copy.getStyleClass().add("btn-secondary");
        copy.setMinWidth(Region.USE_PREF_SIZE);
        copy.setOnAction(e -> askCopyName());
        final boolean copyable = editor.getFormat().canCopy();
        copy.setVisible(copyable);
        copy.setManaged(copyable);

        status.getStyleClass().add("home-summary");
        // Una linea y punto: es un resumen. Lo que venga del motor puede tener
        // el largo que quiera — y lo tiene, ver oneLine() — pero la cabecera no
        // puede crecer, porque empuja el editor entero fuera de la ventana.
        status.setWrapText(false);
        status.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        final Button edit = new Button(NeoText.get("deck.build"));
        visualButton = new Button(NeoText.get("deck.visual"));
        final Button visual = visualButton;
        edit.getStyleClass().add("segment");
        visual.getStyleClass().add("segment");
        edit.pseudoClassStateChanged(SELECTED, true);
        edit.setOnAction(e -> {
            setVisualMode(false);
            edit.pseudoClassStateChanged(SELECTED, true);
            visual.pseudoClassStateChanged(SELECTED, false);
        });
        visual.setOnAction(e -> {
            setVisualMode(true);
            edit.pseudoClassStateChanged(SELECTED, false);
            visual.pseudoClassStateChanged(SELECTED, true);
        });
        final HBox views = new HBox(4, edit, visual);
        views.setAlignment(Pos.CENTER_RIGHT);

        sleeveButton.getStyleClass().add("btn-secondary");
        sleeveButton.setMinWidth(Region.USE_PREF_SIZE);
        sleeveButton.setOnAction(e -> askSleeve());
        refreshSleeveButton();

        final Label format = new Label(editor.getFormat().getLabel());
        format.getStyleClass().add("builder-format");
        final VBox identity = new VBox(4, format, title);
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        foilAllButton.getStyleClass().add("btn-secondary");
        foilAllButton.setMinWidth(Region.USE_PREF_SIZE);
        foilAllButton.setOnAction(e -> foilAll());
        refreshFoilAllButton();
        final HBox row = new HBox(14, identity, rename, copy, foilAllButton, sleeveButton, views);
        row.setAlignment(Pos.CENTER_LEFT);

        final VBox box = new VBox(4, row, status);
        box.setPadding(new Insets(18, 24, 14, 24));
        box.getStyleClass().add("builder-header");
        return box;
    }

    // ===============================================================
    // Las dos columnas

    private Region columns() {
        final Region left = catalogue();
        final Region right = deckPanel();

        final HBox row = new HBox(16, left, right);
        row.setPadding(new Insets(0, 24, 0, 24));
        row.getStyleClass().add("builder-columns");
        left.setMinWidth(0);
        HBox.setHgrow(left, Priority.ALWAYS);
        // El mazo tiene ancho fijo: es una lista de texto y no gana nada con mas
        // sitio, mientras que el catalogo siempre agradece una columna mas.
        right.prefWidthProperty().bind(widthProperty().multiply(.28).add(18));
        right.setMinWidth(UiScale.px(310));
        right.setMaxWidth(UiScale.px(430));
        return row;
    }

    /** La columna de la izquierda: buscar entre todas las cartas de Magic. */
    private Region catalogue() {
        search.setPromptText(NeoText.get("deck.search"));
        search.getStyleClass().add("text-input");
        HBox.setHgrow(search, Priority.ALWAYS);

        final HBox filters = new HBox(6);
        filters.setAlignment(Pos.CENTER_LEFT);
        filters.getChildren().add(colourFilter("W", MagicColor.WHITE));
        filters.getChildren().add(colourFilter("U", MagicColor.BLUE));
        filters.getChildren().add(colourFilter("B", MagicColor.BLACK));
        filters.getChildren().add(colourFilter("R", MagicColor.RED));
        filters.getChildren().add(colourFilter("G", MagicColor.GREEN));
        // Incolora y multicolor, como el filtro de color del editor de Forge
        // (CardColorFilter: WUBRG + incoloro + multicolor). Pedido en itch.io
        // el 29-09-2026: "noticeably the lack of colorless/artifact filtering".
        filters.getChildren().add(extraColourFilter("C", "mana-c", NeoText.get("deck.colorless"), true));
        filters.getChildren().add(extraColourFilter("M", "mana-m", NeoText.get("deck.multicolor"), false));

        final Button legal = new Button(NeoText.get("deck.onlyFits"));
        legal.getStyleClass().add("segment");
        filterButtons.add(legal);
        legal.pseudoClassStateChanged(SELECTED, onlyLegal);
        legal.setOnAction(e -> {
            onlyLegal = !onlyLegal;
            legal.pseudoClassStateChanged(SELECTED, onlyLegal);
            refreshCatalogue();
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        resultCount.getStyleClass().add("dialog-counter");

        search.setMinWidth(UiScale.px(130));
        filterToggle.setId("builder-filters");
        filterToggle.getStyleClass().add("segment");
        filterToggle.setMinWidth(Region.USE_PREF_SIZE);
        filterToggle.setOnAction(e -> {
            final boolean show = !filterRow.isVisible();
            filterRow.setVisible(show);
            filterRow.setManaged(show);
            updateFilterToggle();
        });
        final HBox bar = new HBox(8, search, filters, filterToggle);
        bar.setAlignment(Pos.CENTER_LEFT);

        // ---- segunda fila: los filtros finos ----
        //
        // Van en su propia fila y no mezclados con los colores porque son de
        // otra clase de pregunta: el color dice "de que puedo jugar esto", la
        // rareza y el coste dicen "que quiero para este hueco del mazo".
        final HBox rarityRow = new HBox(4);
        rarityRow.setAlignment(Pos.CENTER_LEFT);
        rarityRow.getChildren().addAll(
                rarityFilter("C", forge.card.CardRarity.Common),
                rarityFilter("I", forge.card.CardRarity.Uncommon),
                rarityFilter("R", forge.card.CardRarity.Rare),
                rarityFilter("M", forge.card.CardRarity.MythicRare));

        final HBox cmcRow = new HBox(4);
        cmcRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i <= 7; i++) {
            cmcRow.getChildren().add(cmcFilter(i));
        }

        // Ocho botones, no una lista desplegable: son pocos, se usan a
        // menudo y asi se ven encendidos de un vistazo, igual que la rareza.
        final FlowPane typeRow = new FlowPane(5, 5);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        typeRow.getChildren().addAll(
                typeFilter(forge.card.CardType.CoreType.Creature),
                typeFilter(forge.card.CardType.CoreType.Instant),
                typeFilter(forge.card.CardType.CoreType.Sorcery),
                typeFilter(forge.card.CardType.CoreType.Artifact),
                typeFilter(forge.card.CardType.CoreType.Enchantment),
                typeFilter(forge.card.CardType.CoreType.Planeswalker),
                typeFilter(forge.card.CardType.CoreType.Battle),
                typeFilter(forge.card.CardType.CoreType.Land));

        final Button rules = new Button(NeoText.get("deck.rulesText"));
        rules.getStyleClass().add("segment");
        filterButtons.add(rules);
        rules.setMinWidth(Region.USE_PREF_SIZE);
        rules.setOnAction(e -> {
            searchRules = !searchRules;
            rules.pseudoClassStateChanged(SELECTED, searchRules);
            search.setPromptText(searchRules
                    ? NeoText.get("deck.search.rules")
                    : NeoText.get("deck.search"));
            refreshCatalogue();
        });

        // "Ocultar las ya puestas" (Discord, 03-10-2026: "having it still in
        // the list is really cluttering the list"). Apagado de fabrica - lo
        // que ya no cabe se ve apagado y sigue ahi, porque es parte de tu
        // coleccion - y RECORDADO: quien no quiere verlo no lo quiere nunca.
        hideUsedButton.setId("builder-hide-used");
        hideUsedButton.getStyleClass().add("segment");
        hideUsedButton.setMinWidth(Region.USE_PREF_SIZE);
        hideUsedButton.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.hideUsed.tip")));
        filterButtons.add(hideUsedButton);
        hideUsedButton.pseudoClassStateChanged(SELECTED, hideUsed);
        hideUsedButton.setOnAction(e -> {
            setHideUsed(!hideUsed);
            refreshCatalogue();
        });

        final Button clear = new Button(NeoText.get("deck.clearFilters"));
        clear.getStyleClass().add("segment");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setOnAction(e -> clearFilters());

        final Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);

        // Fuerza y resistencia, como los rangos de la busqueda avanzada de
        // Forge (CardPowerFilter / CardToughnessFilter), con los mismos
        // botones que el coste: 7 es "7 o mas".
        final HBox powerRow = new HBox(4);
        powerRow.setAlignment(Pos.CENTER_LEFT);
        final HBox toughRow = new HBox(4);
        toughRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i <= 7; i++) {
            powerRow.getChildren().add(statFilter(i, powers));
            toughRow.getChildren().add(statFilter(i, toughnesses));
        }
        // Con techo: hay expansiones de nombre muy largo y el desplegable se
        // estiraria hasta comerse la fila entera.
        setBox.setPrefWidth(UiScale.px(230));
        setBox.setMaxWidth(UiScale.px(230));
        setBox.setId("builder-set");
        setBox.getStyleClass().add("builder-sort");
        setBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final forge.card.CardEdition ed) {
                if (ed == null) {
                    return NeoText.get("library.set.all");
                }
                final String year = ed.getDate() == null ? ""
                        : " (" + new java.text.SimpleDateFormat("yyyy").format(ed.getDate()) + ")";
                return ed.getName() + year;
            }

            @Override
            public forge.card.CardEdition fromString(final String s) {
                return null;
            }
        });
        setBox.getItems().add(null);
        setBox.getSelectionModel().select(0);
        setBox.setOnAction(e -> refreshCatalogue());
        loadEditions();
        formatBox.setId("builder-format");
        formatBox.getStyleClass().add("builder-sort");
        formatBox.getItems().add(null);
        forge.model.FModel.getFormats().getSanctionedList().forEach(formatBox.getItems()::add);
        forge.model.FModel.getFormats().getCasualList().forEach(formatBox.getItems()::add);
        formatBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final forge.game.GameFormat f) {
                return f == null ? NeoText.get("library.format.any") : f.getName();
            }

            @Override
            public forge.game.GameFormat fromString(final String s) {
                return null;
            }
        });
        formatBox.getSelectionModel().select(0);
        formatBox.setOnAction(e -> refreshCatalogue());

        final FlowPane fine = new FlowPane(10, 8,
                new HBox(6, label(NeoText.get("deck.rarity")), rarityRow),
                new HBox(6, label(NeoText.get("deck.cmc")), cmcRow),
                new HBox(6, label(caps("deck.power")), powerRow),
                new HBox(6, label(caps("deck.toughness")), toughRow),
                new HBox(6, label(caps("deck.set")), setBox),
                new HBox(6, label(caps("library.format")), formatBox),
                rules, hideUsedButton, clear);
        fine.setAlignment(Pos.CENTER_LEFT);

        // El tipo va en SU PROPIA fila, antes que rareza y coste: es lo
        // primero que se mira al montar un mazo ("cuantas criaturas llevo")
        // y con ocho botones mas los de rareza y coste, todo junto no cabia
        // en 1280 de ancho sin recortar.
        final VBox typeRowLabeled = new VBox(6, label(NeoText.get("deck.type")), typeRow);
        typeRowLabeled.setAlignment(Pos.CENTER_LEFT);

        filterRow = new VBox(10, typeRowLabeled, fine);
        filterRow.setId("builder-advanced");
        filterRow.getStyleClass().add("builder-advanced");
        // -Dneo.builder.filters=true los abre al entrar: solo para capturarlos.
        final boolean openFilters = Boolean.getBoolean("neo.builder.filters");
        filterRow.setVisible(openFilters);
        filterRow.setManaged(openFilters);

        results.setAlignment(Pos.TOP_LEFT);
        results.setPadding(new Insets(16, 4, 16, 0));
        results.setHgap(10);
        results.setVgap(16);

        basics.setAlignment(Pos.CENTER_LEFT);
        basicsCaption.getStyleClass().add("caption");
        basicsCaption.setMinWidth(Region.USE_PREF_SIZE);
        showBasics(false);

        final ScrollPane scroll = new ScrollPane(results);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Con catalogo limitado esta columna ya no es "el catalogo de Magic":
        // es TU COLECCION. Decirlo cambia lo que el jugador espera encontrar.
        final HBox caption = new HBox(10,
                label(editor.getFormat().catalogueLabel()), pickingBadge,
                gap, legal);
        // Ordenar, como las columnas del editor de Forge. En la Aventura,
        // ademas, "lo ultimo primero".
        caption.getChildren().add(caption.getChildren().indexOf(legal), sortBox());
        caption.getChildren().add(caption.getChildren().indexOf(legal), sortDirection);
        // Y en la Aventura, copiar la coleccion entera (lo tiene su editor).
        if (editor.getFormat().collectionText() != null) {
            final Button copy = new Button(NeoText.get("deck.copyCollection"));
            copy.setId("builder-copy-collection");
            copy.getStyleClass().add("segment");
            copy.setMinWidth(Region.USE_PREF_SIZE);
            copy.setOnAction(e -> copyCollection());
            caption.getChildren().add(caption.getChildren().indexOf(legal), copy);
        }
        if (editor.bulkAutoSell(List.of()) != null) {
            final Button sell = new Button(NeoText.get("deck.autoSellFiltered"));
            sell.setId("builder-autosell-filtered");
            sell.getStyleClass().add("segment");
            sell.setMinWidth(Region.USE_PREF_SIZE);
            sell.setOnAction(e -> askAutoSellFiltered());
            caption.getChildren().add(caption.getChildren().indexOf(legal), sell);
        }
        caption.setAlignment(Pos.CENTER_LEFT);

        // Las basicas van FUERA del scroll, en una franja al pie de la columna.
        //
        // Dentro se solapaban con la primera carta — el rotulo asomaba media
        // palabra por detras — y ademas obligaban a bajar hasta el final del
        // pool para llegar a ellas. Fuera no pueden pisarse con nada y estan
        // siempre a mano, que es lo que se quiere de una fila de cinco cartas
        // que no cambia nunca.
        //
        // Y son BOTONES, no cartas: con cartas enteras la franja pedia ~230 px
        // y le dejaba a la rejilla una sola fila, cortada por abajo — el editor
        // de la Aventura parecia tener cinco cartas (reportado el 17-09-2026).
        // Una basica no hay que mirarla para elegirla.
        final Region pageGap = new Region();
        HBox.setHgrow(pageGap, Priority.ALWAYS);
        final HBox pageBar = new HBox(12, resultCount, pageGap, pager);
        pageBar.setAlignment(Pos.CENTER_LEFT);
        pageBar.getStyleClass().add("builder-pagebar");
        resultCount.setMinWidth(0);
        // DE QUE EDICION SON (la Aventura, 08-10-2026): sus Landscape Sketchbook
        // desbloquean ediciones de basicas, y aqui se elige cual. Con una sola
        // no hay nada que elegir y el desplegable no sale.
        final List<forge.card.CardEdition> landSets = editor.basicLandEditions();
        basicSetBox.getStyleClass().add("builder-sort");
        basicSetBox.setId("builder-basic-set");
        basicSetBox.setMaxWidth(UiScale.px(230));
        basicSetBox.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final forge.card.CardEdition ed) {
                return ed == null ? "" : ed.getName();
            }

            @Override
            public forge.card.CardEdition fromString(final String s) {
                return null;
            }
        });
        basicSetBox.getItems().setAll(landSets);
        basicSetBox.getSelectionModel().select(editor.basicLandSet());
        basicSetBox.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.basics.set")));
        basicSetBox.setOnAction(e -> {
            editor.setBasicLandSet(basicSetBox.getValue());
            refreshCatalogue();
        });
        final boolean chooseLandSet = landSets.size() > 1;
        basicSetBox.visibleProperty().bind(basics.visibleProperty().and(
                javafx.beans.binding.Bindings.createBooleanBinding(() -> chooseLandSet)));
        basicSetBox.managedProperty().bind(basicSetBox.visibleProperty());
        final HBox basicsRow = new HBox(12, basicsCaption, basicSetBox, basics);
        basicsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(basics, Priority.ALWAYS);
        basicsCaption.managedProperty().bind(basics.managedProperty());
        basicsRow.visibleProperty().bind(basics.visibleProperty());
        basicsRow.managedProperty().bind(basics.managedProperty());
        final VBox box = new VBox(10, caption, bar, filterRow, scroll,
                basicsRow, pageBar);
        box.getStyleClass().add("builder-catalogue");
        box.setPadding(new Insets(14, 16, 8, 16));
        return box;
    }

    private Region filterRow;

    /** Donde se recuerda "Ocultar las ya puestas" (la misma clave que Android). */
    private static final String HIDE_USED_KEY = "deckEditorHideUsed";

    /** Ver el boton en {@link #catalogue()}: sin lo que ya esta todo en el mazo. */
    private boolean hideUsed = NeoSettings.getBool(HIDE_USED_KEY, false);
    private final Button hideUsedButton = new Button(NeoText.get("deck.hideUsed"));

    private void setHideUsed(final boolean on) {
        hideUsed = on;
        NeoSettings.setBool(HIDE_USED_KEY, on);
        hideUsedButton.pseudoClassStateChanged(SELECTED, on);
    }

    /**
     * Lo que se eligio la ultima vez: la Aventura abre un editor nuevo cada
     * vez, y quien ordena por lo ultimo lo quiere asi en la siguiente.
     */
    private static boolean newestFirstChosen;

    /** El orden elegido la ultima vez, por la misma razon. */
    private static DeckEditor.Sort sortChosen;

    /**
     * Y si iba al reves (itch.io, 03-10-2026: <i>"You can't change sort
     * ascending or decending"</i>). Cambiar de orden lo devuelve al derecho,
     * como una columna de tabla.
     */
    private static boolean sortReversedChosen;

    /** La flecha del orden del catalogo. */
    private final Button sortDirection = new Button();

    /**
     * La flecha de un orden: arriba de menos a mas, abajo de mas a menos. El
     * sentido "de fabrica" depende del orden (el coste sube, la rareza baja),
     * asi que se calcula con los dos.
     */
    private static void showDirection(final Button b, final DeckEditor.Sort s, final boolean reversed) {
        final boolean up = DeckEditor.ascendingByDefault(s) != reversed;
        b.setText(up ? "\u25B2" : "\u25BC");
        b.setTooltip(new javafx.scene.control.Tooltip(NeoText.get(up ? "deck.sort.up" : "deck.sort.down")));
    }

    private static Button directionButton(final String id) {
        final Button b = new Button();
        b.setId(id);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        return b;
    }

    /** El orden del MAZO, dentro de cada tipo (itch.io: "No sort in deck, Why ?"). */
    private static DeckEditor.Sort deckSortChosen;
    private static boolean deckSortReversedChosen;
    private final Button deckSortDirection = directionButton("builder-deck-sort-direction");

    /**
     * El selector de orden. Los criterios de las columnas del editor de Forge;
     * "lo ultimo primero" solo donde se lleva la cuenta (la Aventura).
     */
    private javafx.scene.control.ComboBox<DeckEditor.Sort> sortBox() {
        final javafx.scene.control.ComboBox<DeckEditor.Sort> box = new javafx.scene.control.ComboBox<>();
        box.setId("builder-sort");
        box.getStyleClass().add("builder-sort");
        for (final DeckEditor.Sort s : DeckEditor.Sort.values()) {
            if ((s != DeckEditor.Sort.NEWEST || editor.tracksAcquisition())
                    && (s != DeckEditor.Sort.PRICE || editor.hasPrices())
                    && (s != DeckEditor.Sort.COUNT || editor.isLimited())) {
                box.getItems().add(s);
            }
        }
        final javafx.util.StringConverter<DeckEditor.Sort> names = new javafx.util.StringConverter<>() {
            @Override
            public String toString(final DeckEditor.Sort s) {
                return s == null ? "" : NeoText.get("deck.sort", sortName(s));
            }

            @Override
            public DeckEditor.Sort fromString(final String t) {
                return null;
            }
        };
        box.setConverter(names);
        DeckEditor.Sort start = sortChosen;
        if (start == null) {
            start = newestFirstChosen && editor.tracksAcquisition()
                    ? DeckEditor.Sort.NEWEST : DeckEditor.Sort.NAME;
        }
        if (!box.getItems().contains(start)) {
            start = DeckEditor.Sort.NAME;
        }
        box.setValue(start);
        editor.setSort(start);
        editor.setSortReversed(sortReversedChosen);
        sortDirection.setId("builder-sort-direction");
        sortDirection.getStyleClass().add("segment");
        sortDirection.setMinWidth(Region.USE_PREF_SIZE);
        showDirection(sortDirection, start, sortReversedChosen);
        sortDirection.setOnAction(e -> {
            sortReversedChosen = !sortReversedChosen;
            editor.setSortReversed(sortReversedChosen);
            showDirection(sortDirection, editor.getSort(), sortReversedChosen);
            refreshCatalogue();
        });
        if (editor.tracksAcquisition()) {
            box.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.newest.tip")));
        }
        box.valueProperty().addListener((o, was, is) -> {
            if (is == null) {
                return;
            }
            sortChosen = is;
            newestFirstChosen = is == DeckEditor.Sort.NEWEST;
            editor.setSort(is);
            // Otro orden empieza al derecho, como una columna de tabla.
            sortReversedChosen = false;
            editor.setSortReversed(false);
            showDirection(sortDirection, is, false);
            refreshCatalogue();
        });
        return box;
    }

    private static String sortName(final DeckEditor.Sort s) {
        switch (s) {
            case NEWEST:
                return NeoText.get("deck.sort.acquired");
            case COST:
                return NeoText.get("deck.sort.cost");
            case COLOR:
                return NeoText.get("deck.sort.color");
            case TYPE:
                return NeoText.get("deck.sort.type");
            case RARITY:
                return NeoText.get("deck.sort.rarity");
            case SET:
                return NeoText.get("deck.sort.set");
            case POWER:
                return NeoText.get("deck.sort.power");
            case TOUGHNESS:
                return NeoText.get("deck.sort.toughness");
            case PRICE:
                return NeoText.get("deck.sort.price");
            case COUNT:
                return NeoText.get("deck.sort.count");
            default:
                return NeoText.get("deck.sort.name");
        }
    }

    /** La coleccion entera, para copiarla (ver DeckContext.collectionText). */
    private void copyCollection() {
        final String text = editor.getFormat().collectionText();
        overlay.show(TextDialog.block(NeoText.get("deck.copyCollection.title"),
                NeoText.get("deck.copyCollection.hint"),
                text == null ? "" : text, NeoText.get("deck.export.copy"),
                t -> {
                    final javafx.scene.input.ClipboardContent content =
                            new javafx.scene.input.ClipboardContent();
                    content.putString(t);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                    overlay.hide();
                },
                overlay::hide));
    }

    private final java.util.Set<Integer> powers = new java.util.HashSet<>();
    private final java.util.Set<Integer> toughnesses = new java.util.HashSet<>();
    private final javafx.scene.control.ComboBox<forge.card.CardEdition> setBox =
            new javafx.scene.control.ComboBox<>();
    private final javafx.scene.control.ComboBox<forge.game.GameFormat> formatBox =
            new javafx.scene.control.ComboBox<>();
    /** Las cartas que tienen alguna impresion en cada expansion (CardLibrary). */
    private volatile forge.neo.deck.CardLibrary library;

    /** Un valor de fuerza o resistencia del filtro. El 7 es "7 o mas". */
    private Button statFilter(final int value, final java.util.Set<Integer> into) {
        final Button b = new Button(value == 7 ? "7+" : String.valueOf(value));
        b.getStyleClass().addAll("segment", "cmc-filter");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            if (!into.remove(value)) {
                into.add(value);
            }
            b.pseudoClassStateChanged(SELECTED, into.contains(value));
            refreshCatalogue();
        });
        filterButtons.add(b);
        return b;
    }

    /**
     * Las expansiones, fuera del hilo de interfaz: salen del mismo indice que
     * la enciclopedia, que la primera vez tarda unas decimas en montarse.
     */
    private void loadEditions() {
        final Thread t = new Thread(() -> {
            final forge.neo.deck.CardLibrary lib = forge.neo.deck.CardLibrary.get();
            javafx.application.Platform.runLater(() -> {
                library = lib;
                setBox.getItems().addAll(lib.editions());
            });
        }, "neo-builder-sets");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Si la carta cabe en la expansion elegida. En una coleccion (la Aventura)
     * cuenta la impresion que TIENES; en el catalogo de todo Magic, que haya
     * alguna impresion de esa carta en la expansion.
     */
    private boolean inChosenSet(final PaperCard card, final forge.card.CardEdition ed) {
        if (ed.getCode().equalsIgnoreCase(card.getEdition())) {
            return true;
        }
        if (editor.getFormat().pool() != null) {
            return false;
        }
        final forge.neo.deck.CardLibrary lib = library;
        return lib != null && lib.hasPrintingIn(ed.getCode(), card);
    }

    /** "Autovender lo filtrado": pregunta, con cuantas y por cuanto. */
    private void askAutoSellFiltered() {
        final List<PaperCard> all = editor.find(lastQuery, onlyLegal, lastFine,
                Integer.MAX_VALUE, searchRules).cards;
        final forge.neo.deck.DeckContext.BulkSell plan = editor.bulkAutoSell(all);
        if (plan == null || plan.copies() == 0) {
            overlay.show(new ConfirmDialog(NeoText.get("deck.autoSellFiltered"),
                    NeoText.get("deck.autoSellFiltered.none"),
                    List.of(NeoText.get("banner.understood")), 0, i -> overlay.hide()));
            return;
        }
        overlay.show(new ConfirmDialog(NeoText.get("deck.autoSellFiltered.ask"),
                NeoText.get("deck.autoSellFiltered.detail", plan.copies(), plan.cards(), plan.value()),
                List.of(NeoText.get("deck.autoSellFiltered.yes"), NeoText.get("common.cancel")), 1,
                i -> {
                    overlay.hide();
                    if (i != null && i == 0) {
                        plan.run().run();
                        refreshCatalogue();
                    }
                }));
    }

    /** La ultima busqueda y sus filtros, para "autovender lo filtrado". */
    private String lastQuery = "";
    private Predicate<PaperCard> lastFine;

    /** Incolora o multicolor: van con los colores y se suman a ellos. */
    private boolean colourlessOn;
    private boolean multicolourOn;

    private Button extraColourFilter(final String letter, final String style, final String tip,
                                     final boolean colourless) {
        final Button b = new Button(letter);
        b.getStyleClass().addAll("segment", "colour-filter", style);
        b.setTooltip(new javafx.scene.control.Tooltip(tip));
        b.setOnAction(e -> {
            final boolean on;
            if (colourless) {
                colourlessOn = !colourlessOn;
                on = colourlessOn;
            } else {
                multicolourOn = !multicolourOn;
                on = multicolourOn;
            }
            b.pseudoClassStateChanged(SELECTED, on);
            refreshCatalogue();
        });
        filterButtons.add(b);
        return b;
    }

    /**
     * "Lo ultimo primero": la coleccion ordenada por cuando entro cada carta.
     *
     * <p>Pedido en itch.io el 27-09-2026 para la Aventura, que es donde las
     * cartas se consiguen de una en una (premios, tiendas, sobres) y lo que se
     * quiere al abrir el editor es ver que ha caido. Un boton y no una lista
     * de ordenes: es la unica pregunta que el nombre no contesta. El rotulo
     * de ayuda dice desde cuando se lleva la cuenta, porque lo de antes sale
     * por nombre y sin eso pareceria que ordena mal.
     */
    private Button newestButton() {
        final Button b = new Button(NeoText.get("deck.newest"));
        b.setId("builder-newest");
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.newest.tip")));
        editor.setNewestFirst(newestFirstChosen);
        b.pseudoClassStateChanged(SELECTED, newestFirstChosen);
        b.setOnAction(e -> {
            newestFirstChosen = !newestFirstChosen;
            editor.setNewestFirst(newestFirstChosen);
            b.pseudoClassStateChanged(SELECTED, newestFirstChosen);
            refreshCatalogue();
        });
        return b;
    }

    /**
     * Una rareza del filtro.
     *
     * <p>Se pregunta al {@code PaperCard}, que sabe la rareza de ESA impresion.
     * No la deducimos de nada.
     */
    private Button rarityFilter(final String letter, final forge.card.CardRarity rarity) {
        final Button b = new Button(letter);
        b.getStyleClass().addAll("segment", "rarity-filter",
                "rarity-" + letter.toLowerCase(java.util.Locale.ROOT));
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            if (!rarities.remove(rarity)) {
                rarities.add(rarity);
            }
            b.pseudoClassStateChanged(SELECTED, rarities.contains(rarity));
            refreshCatalogue();
        });
        filterButtons.add(b);
        return b;
    }

    /** Un coste del filtro. El 7 son "7 o mas", como en la curva. */
    private Button cmcFilter(final int cmc) {
        final Button b = new Button(cmc == 7 ? "7+" : String.valueOf(cmc));
        b.getStyleClass().addAll("segment", "cmc-filter");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            if (!cmcs.remove(cmc)) {
                cmcs.add(cmc);
            }
            b.pseudoClassStateChanged(SELECTED, cmcs.contains(cmc));
            refreshCatalogue();
        });
        filterButtons.add(b);
        return b;
    }

    /**
     * Un tipo del filtro. El texto sale de {@code CoreType.getTranslatedName()}
     * — el mismo que ya usa el motor para pintar el tipo de la carta — asi que
     * no hace falta ninguna clave de idioma nueva para el boton en si.
     */
    private Button typeFilter(final forge.card.CardType.CoreType type) {
        final Button b = new Button(type.getTranslatedName());
        b.getStyleClass().addAll("segment", "type-filter");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            if (!types.remove(type)) {
                types.add(type);
            }
            b.pseudoClassStateChanged(SELECTED, types.contains(type));
            refreshCatalogue();
        });
        filterButtons.add(b);
        return b;
    }

    /**
     * Apaga todos los filtros de golpe.
     *
     * <p>Con cinco colores, cuatro rarezas y ocho costes es facil dejarse uno
     * encendido y quedarse mirando un catalogo vacio sin entender por que. Un
     * boton que lo limpia todo evita esa pregunta.
     */
    private void clearFilters() {
        colours.clear();
        colourlessOn = false;
        multicolourOn = false;
        powers.clear();
        toughnesses.clear();
        setBox.getSelectionModel().select(0);
        formatBox.getSelectionModel().select(0);
        rarities.clear();
        cmcs.clear();
        types.clear();
        searchRules = false;
        onlyLegal = false;
        for (final Button n : filterButtons) {
            n.pseudoClassStateChanged(SELECTED, false);
        }
        setHideUsed(false);
        search.setPromptText(NeoText.get("deck.search"));
        refreshCatalogue();
    }

    /**
     * Un color del filtro.
     *
     * <p>Filtra por <b>identidad de color</b>, no por coste: es lo que decide
     * que cabe en un mazo de Commander, y ademas es lo que espera quien busca
     * "cartas verdes" — un hechizo con coste hibrido o una tierra que produce
     * verde cuentan.
     */
    private Button colourFilter(final String letter, final byte colour) {
        final Button b = new Button(letter);
        b.getStyleClass().addAll("segment", "colour-filter", "mana-" + letter.toLowerCase(java.util.Locale.ROOT));
        b.setOnAction(e -> {
            if (colours.contains(colour)) {
                colours.remove(Byte.valueOf(colour));
            } else {
                colours.add(colour);
            }
            b.pseudoClassStateChanged(SELECTED, colours.contains(colour));
            refreshCatalogue();
        });
        filterButtons.add(b);
        return b;
    }

    /** La columna de la derecha: lo que llevas. */
    private Region deckPanel() {
        commanderRow.setAlignment(Pos.CENTER_LEFT);
        // Sin comandantes no ocupa ni el espaciado del VBox, que si no deja un
        // hueco arriba del mazo sin nada que lo explique.
        commanderRow.setVisible(editor.usesCommander());
        commanderRow.setManaged(editor.usesCommander());

        final ScrollPane scroll = new ScrollPane(deckList);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        deckCount.getStyleClass().add("builder-deck-count");
        deckCount.setMinWidth(0);
        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        deckFilterToggle.setId("builder-deck-filter");
        deckFilterToggle.getStyleClass().add("segment");
        deckFilterToggle.setMinWidth(Region.USE_PREF_SIZE);
        deckFilterToggle.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.filterDeck.tip")));
        deckFilterToggle.setOnAction(e -> {
            final boolean show = !deckFilterRow.isVisible();
            deckFilterRow.setVisible(show);
            deckFilterRow.setManaged(show);
            refreshDeckFilterToggle();
        });
        final HBox title = new HBox(8, label(NeoText.get("deck.theDeck")), gap, deckCount);
        title.setAlignment(Pos.CENTER_LEFT);
        // El orden y el filtro en su propia fila: en la misma que el titulo no
        // cabian y se cortaban los dos ("THE D...", "99 ca..."). Juntos, porque
        // son la misma pregunta: como quiero ver el mazo.
        final Region gap3 = new Region();
        HBox.setHgrow(gap3, Priority.ALWAYS);
        final HBox view = new HBox(6, deckSortBox(), deckSortDirection, gap3, deckFilterToggle);
        view.setAlignment(Pos.CENTER_LEFT);
        final VBox heading = new VBox(6, title, view);
        deckFilterRow = deckFilterRow();
        refreshDeckFilterToggle();
        final javafx.scene.control.TitledPane details = new javafx.scene.control.TitledPane(NeoText.get("stats.caption"), stats);
        details.setExpanded(false);
        details.setAnimated(false);
        details.getStyleClass().add("builder-statistics");
        companionRow.setAlignment(Pos.CENTER_LEFT);
        final VBox box = new VBox(8, heading, deckFilterRow, commanderRow, companionRow, scroll,
                curve, details);
        scroll.setMinHeight(UiScale.px(60));
        box.setId("builder-deck-panel");
        box.getStyleClass().add("deck-panel");
        box.setPadding(new Insets(12, 14, 12, 14));
        return box;
    }

    /**
     * El orden del mazo, dentro de cada grupo de tipo. Solo los ordenes que
     * tienen sentido en un mazo ({@code DeckEditor.deckSorts}); de fabrica,
     * por coste, que es como iba siempre.
     */
    private javafx.scene.control.ComboBox<DeckEditor.Sort> deckSortBox() {
        final javafx.scene.control.ComboBox<DeckEditor.Sort> box = new javafx.scene.control.ComboBox<>();
        box.setId("builder-deck-sort");
        box.getStyleClass().add("builder-sort");
        box.getItems().addAll(editor.deckSorts());
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final DeckEditor.Sort s) {
                return s == null ? "" : NeoText.get("deck.sort", sortName(s));
            }

            @Override
            public DeckEditor.Sort fromString(final String t) {
                return null;
            }
        });
        box.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.sort.deck.tip")));
        final DeckEditor.Sort start = deckSortChosen != null && box.getItems().contains(deckSortChosen)
                ? deckSortChosen : DeckEditor.Sort.COST;
        box.setValue(start);
        editor.setDeckSort(start, deckSortReversedChosen);
        showDirection(deckSortDirection, start, deckSortReversedChosen);
        box.valueProperty().addListener((o, was, is) -> {
            if (is == null) {
                return;
            }
            deckSortChosen = is;
            deckSortReversedChosen = false;
            editor.setDeckSort(is, false);
            showDirection(deckSortDirection, is, false);
            refreshDeck();
        });
        deckSortDirection.setOnAction(e -> {
            deckSortReversedChosen = !deckSortReversedChosen;
            editor.setDeckSort(editor.getDeckSort(), deckSortReversedChosen);
            showDirection(deckSortDirection, editor.getDeckSort(), deckSortReversedChosen);
            refreshDeck();
        });
        return box;
    }

    // ---- el filtro del mazo ----
    //
    // Discord, 03-10-2026: "sometime you just wanna change a couple card in
    // spessific color, then you need to scroll and search that one card". Es
    // solo de la pantalla: esconde filas, no toca el mazo. El color es la
    // IDENTIDAD, como en el catalogo, y vale cualquiera de los marcados.

    private final java.util.Set<Byte> deckColours = new java.util.LinkedHashSet<>();
    private boolean deckColourless;
    private final TextField deckSearch = new TextField();
    private final Button deckFilterToggle = new Button();
    private VBox deckFilterRow;
    /**
     * Tipo, coste y rareza, ademas de color y texto (itch.io, 03-10-2026:
     * <i>"Imagine editing your 100 card 2 deck color the best a filter can do
     * ... only split the deck into 2"</i>). El criterio es el de
     * {@code DeckFilter}, el mismo que en Android.
     */
    private final java.util.Set<forge.card.CardType.CoreType> deckTypes =
            java.util.EnumSet.noneOf(forge.card.CardType.CoreType.class);
    private final java.util.Set<Integer> deckCosts = new java.util.TreeSet<>();
    private final java.util.Set<forge.card.CardRarity> deckRarities =
            java.util.EnumSet.noneOf(forge.card.CardRarity.class);
    private final List<Button> deckFineButtons = new ArrayList<>();

    /** Un boton del filtro del mazo que enciende o apaga un valor de un conjunto. */
    private <T> Button deckToggle(final String text, final java.util.Set<T> set, final T value,
                                  final String... styles) {
        final Button b = new Button(text);
        b.getStyleClass().add("segment");
        b.getStyleClass().addAll(styles);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            if (!set.remove(value)) {
                set.add(value);
            }
            b.pseudoClassStateChanged(SELECTED, set.contains(value));
            refreshDeck();
        });
        deckFineButtons.add(b);
        return b;
    }

    private VBox deckFilterRow() {
        final HBox colourRow = new HBox(4);
        colourRow.setAlignment(Pos.CENTER_LEFT);
        final String[] letters = {"W", "U", "B", "R", "G"};
        final byte[] masks = {MagicColor.WHITE, MagicColor.BLUE, MagicColor.BLACK,
                MagicColor.RED, MagicColor.GREEN};
        final List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < letters.length; i++) {
            final byte colour = masks[i];
            final Button b = new Button(letters[i]);
            b.getStyleClass().addAll("segment", "colour-filter",
                    "mana-" + letters[i].toLowerCase(java.util.Locale.ROOT));
            b.setOnAction(e -> {
                if (!deckColours.remove(colour)) {
                    deckColours.add(colour);
                }
                b.pseudoClassStateChanged(SELECTED, deckColours.contains(colour));
                refreshDeck();
            });
            buttons.add(b);
        }
        // Incoloras aparte: en un mazo son artefactos y tierras, que es justo lo
        // que se repasa cuando sobra o falta mana.
        final Button colourless = new Button("C");
        colourless.getStyleClass().addAll("segment", "colour-filter", "mana-c");
        colourless.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.colorless")));
        colourless.setOnAction(e -> {
            deckColourless = !deckColourless;
            colourless.pseudoClassStateChanged(SELECTED, deckColourless);
            refreshDeck();
        });
        buttons.add(colourless);
        colourRow.getChildren().addAll(buttons);

        final Button clear = new Button("×");
        clear.getStyleClass().add("segment");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("deck.clearFilters")));
        clear.setOnAction(e -> {
            deckColours.clear();
            deckColourless = false;
            for (final Button b : buttons) {
                b.pseudoClassStateChanged(SELECTED, false);
            }
            deckSearch.clear();
            deckTypes.clear();
            deckCosts.clear();
            deckRarities.clear();
            for (final Button b : deckFineButtons) {
                b.pseudoClassStateChanged(SELECTED, false);
            }
            refreshDeck();
        });

        deckSearch.setPromptText(NeoText.get("deck.filterDeck.search"));
        deckSearch.getStyleClass().add("text-input");
        // -Dneo.builder.deckSearch=texto la rellena ANTES del oyente: escrito
        // despues, repintaria el mazo con la pantalla aun a medio montar.
        final String typed = System.getProperty("neo.builder.deckSearch", "");
        deckSearch.setText(typed);
        deckSearch.textProperty().addListener((o, was, is) -> refreshDeck());
        deckSearch.setMinWidth(UiScale.px(70));
        deckSearch.setPrefColumnCount(6);
        HBox.setHgrow(deckSearch, Priority.ALWAYS);

        // UNA sola fila, colores + texto + x: en 1080 la columna del mazo ya
        // va justa de alto (comandante, curva, estadisticas), y con dos filas
        // a la lista le quedaban dos cartas a la vista.
        final HBox top = new HBox(4, colourRow, deckSearch, clear);
        top.setAlignment(Pos.CENTER_LEFT);

        // Y debajo, tipo, coste y rareza. Solo ocupan sitio con el filtro
        // abierto, que es cuando se esta filtrando.
        final FlowPane typeRow = new FlowPane(4, 4);
        for (final forge.card.CardType.CoreType t : new forge.card.CardType.CoreType[] {
                forge.card.CardType.CoreType.Creature, forge.card.CardType.CoreType.Instant,
                forge.card.CardType.CoreType.Sorcery, forge.card.CardType.CoreType.Artifact,
                forge.card.CardType.CoreType.Enchantment, forge.card.CardType.CoreType.Planeswalker,
                forge.card.CardType.CoreType.Battle, forge.card.CardType.CoreType.Land}) {
            typeRow.getChildren().add(deckToggle(t.getTranslatedName(), deckTypes, t, "type-filter"));
        }
        final HBox costRow = new HBox(4);
        costRow.setAlignment(Pos.CENTER_LEFT);
        costRow.getChildren().add(label(NeoText.get("deck.cmc")));
        for (int i = 0; i <= 7; i++) {
            costRow.getChildren().add(deckToggle(i == 7 ? "7+" : String.valueOf(i), deckCosts, i, "cmc-filter"));
        }
        final String[] letters2 = {"C", "I", "R", "M"};
        final forge.card.CardRarity[] rarityValues = {forge.card.CardRarity.Common,
                forge.card.CardRarity.Uncommon, forge.card.CardRarity.Rare, forge.card.CardRarity.MythicRare};
        final HBox rarityRow = new HBox(4);
        rarityRow.setAlignment(Pos.CENTER_LEFT);
        rarityRow.getChildren().add(label(NeoText.get("deck.rarity")));
        for (int i = 0; i < letters2.length; i++) {
            rarityRow.getChildren().add(deckToggle(letters2[i], deckRarities, rarityValues[i], "rarity-filter",
                    "rarity-" + letters2[i].toLowerCase(java.util.Locale.ROOT)));
        }
        final FlowPane fine = new FlowPane(10, 4, costRow, rarityRow);

        final VBox row = new VBox(4, top, typeRow, fine);
        row.setId("builder-deck-filter-row");
        // Que salten de linea al ancho de la columna: sin esto un FlowPane mide
        // su alto con su ancho de fabrica y deja coste y rareza en dos lineas
        // aunque quepan en una.
        typeRow.prefWrapLengthProperty().bind(row.widthProperty());
        fine.prefWrapLengthProperty().bind(row.widthProperty());
        // -Dneo.builder.deckFilter=true la abre al entrar (y deckSearch, ver arriba):
        // solo para capturarla.
        final boolean open = Boolean.getBoolean("neo.builder.deckFilter") || !typed.isEmpty();
        row.setVisible(open);
        row.setManaged(open);
        return row;
    }

    private boolean deckFilterActive() {
        return !deckColours.isEmpty() || deckColourless || !deckSearch.getText().isBlank()
                || !deckTypes.isEmpty() || !deckCosts.isEmpty() || !deckRarities.isEmpty();
    }

    private boolean deckFilterAccepts(final PaperCard card) {
        int mask = 0;
        for (final byte c : deckColours) {
            mask |= c;
        }
        return forge.neo.deck.DeckFilter.accepts(card, mask, deckColourless, deckSearch.getText(),
                deckTypes, deckCosts, deckRarities);
    }

    /** El punto dice que hay filtro aunque la fila este plegada: un mazo de 40 que ensenya 6 parece roto. */
    private void refreshDeckFilterToggle() {
        final boolean active = deckFilterActive();
        final boolean open = deckFilterRow != null && deckFilterRow.isVisible();
        deckFilterToggle.setText(NeoText.get("deck.filterDeck") + (active ? "  •" : ""));
        deckFilterToggle.pseudoClassStateChanged(SELECTED, active || open);
    }

    /** Un rotulo de filtro en mayusculas, como TIPO, RAREZA y COSTE. */
    private static String caps(final String key) {
        return NeoText.get(key).toUpperCase(java.util.Locale.ROOT);
    }

    private static Label label(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("caption");
        return l;
    }

    // ===============================================================
    // Pie

    private Region footer() {
        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setOnAction(e -> leave());

        final Button commander = commanderButton;
        commander.getStyleClass().add("btn-secondary");
        // En un formato SIN comandante no se desactiva: se quita. Un boton
        // apagado sigue diciendo que aqui hay comandantes, y en Estandar no los
        // hay — no es que ahora no puedas, es que no existe (principio 1).
        final boolean withCommander = editor.usesCommander();
        commander.setVisible(withCommander);
        commander.setManaged(withCommander);
        commander.setOnAction(e -> openPicker(!(commanderMode && !pickingSpell), false));

        // El hueco del hechizo, solo donde existe.
        final boolean withSpell = editor.usesSignatureSpell();
        spellButton.getStyleClass().add("btn-secondary");
        spellButton.setVisible(withSpell);
        spellButton.setManaged(withSpell);
        spellButton.setOnAction(e -> openPicker(!pickingSpell, true));
        if (withSpell) {
            commander.setText(NeoText.get("deck.pickOathbreaker"));
        }

        // Solo con comandante puesto: sin uno no hay identidad de color con
        // la que el motor pueda elegir cartas, y es la MISMA condicion que
        // decide si "Elegir comandante" se ve. La visibilidad de verdad se
        // recalcula en refreshCommanderRow(), que es donde se entera de
        // cuando cambia el comandante.
        generate.getStyleClass().add("btn-secondary");
        generate.setOnAction(e -> generateDeck());

        importer.getStyleClass().add("btn-secondary");
        importer.setOnAction(e -> importList());

        final Button export = new Button(NeoText.get("deck.export"));
        export.getStyleClass().add("btn-secondary");
        export.setOnAction(e -> exportList());

        final boolean limitedPool = editor.canAutoBuild();
        autoBuild.getStyleClass().add("btn-secondary");
        autoBuild.setOnAction(e -> autoBuildDeck());
        autoBuild.setVisible(limitedPool);
        autoBuild.setManaged(limitedPool);
        clearMain.getStyleClass().add("btn-secondary");
        clearMain.setOnAction(e -> clearMainDeck());
        clearMain.setVisible(limitedPool);
        clearMain.setManaged(limitedPool);

        cleanup.getStyleClass().add("btn-secondary");
        cleanup.setOnAction(e -> removeIllegal());
        cleanup.setVisible(false);
        cleanup.setManaged(false);
        clearSide.getStyleClass().add("btn-secondary");
        clearSide.setId("deck-clear-sideboard");
        clearSide.setOnAction(e -> clearSideboard());
        clearSide.setVisible(false);
        clearSide.setManaged(false);

        final Button save = new Button(NeoText.get("common.save"));
        save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> save());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        // Ningun boton se encoge por debajo de su texto: cuando la fila no cabe,
        // JavaFX lo corta con puntos suspensivos y queda un pie ilegible.
        for (final Button b : new Button[] {back, cleanup, clearSide, commander, spellButton, generate,
                autoBuild, clearMain, importer, export, save}) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }

        final FlowPane actions =
                new FlowPane(8, 8, cleanup, clearSide, commander, spellButton, generate, autoBuild, clearMain,
                        importer, export);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(0);
        HBox.setHgrow(actions, Priority.ALWAYS);
        save.setId("builder-save");
        back.setId("builder-back");
        // Volver, abajo a la derecha y junto a la accion principal: el mismo
        // sitio en todas las pantallas (las notas de diseño, principio 12).
        final HBox row = new HBox(14, actions, back, save);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("home-footer");
        row.setPadding(new Insets(14, 26, 18, 26));
        return row;
    }

    /**
     * Enciende o apaga el modo "elegir comandante", y lo <b>dice</b>.
     *
     * <p>Mientras esta encendido el catalogo no ensenya cartas, ensenya
     * comandantes: es un cambio grande y tiene que verse en el boton que lo ha
     * provocado. El texto cambia y ademas se queda marcado — con
     * {@code .btn-secondary:selected}, que antes no existia en la hoja de
     * estilos y por eso el boton se veia igual encendido que apagado.
     */
    private void setCommanderMode(final boolean on) {
        openPicker(on, false);
    }

    /**
     * Abre el selector de la zona de mando en uno de sus dos huecos.
     *
     * <p>{@code spell} solo puede ser true en Oathbreaker. Los dos botones se
     * marcan por separado y la chapa dice cual estas rellenando: son dos
     * preguntas distintas ("quien es tu oathbreaker" y "cual es su hechizo"), y
     * con una sola chapa que dijera "eligiendo comandante" no habria forma de
     * saber en cual estas.
     */
    private void openPicker(final boolean on, final boolean spell) {
        commanderMode = on;
        pickingSpell = on && spell;
        final boolean twoSlots = editor.usesSignatureSpell();
        commanderButton.setText(NeoText.get(on && !pickingSpell ? "deck.backToCatalogue"
                : twoSlots ? "deck.pickOathbreaker" : "deck.pickCommander"));
        commanderButton.pseudoClassStateChanged(SELECTED, on && !pickingSpell);
        spellButton.setText(NeoText.get(pickingSpell
                ? "deck.backToCatalogue" : "deck.pickSignature"));
        spellButton.pseudoClassStateChanged(SELECTED, pickingSpell);
        pickingBadge.setText(NeoText.get(pickingSpell ? "deck.pickingSignature"
                : twoSlots ? "deck.pickingOathbreaker" : "deck.pickingCommander"));
        pickingBadge.setVisible(on);
        pickingBadge.setManaged(on);
        refreshCatalogue();
    }

    /**
     * Empieza el mazo por su comandante: abre ya en "elegir comandante".
     *
     * <p>Pedido el 15-09-2026: en Commander y Brawl lo primero que se decide es
     * el comandante (de el sale la identidad de color, o sea que cartas caben),
     * y el boton que lo elige esta abajo, entre otros cinco — quien no lo ve
     * empieza a meter cartas que luego no le caben. Asi que el catalogo sale ya
     * ensenyando comandantes y el boton ya marcado; al elegir uno se vuelve
     * solo al catalogo normal (ver el click de la tesela).
     *
     * <p>Solo si el mazo usa comandante y aun no tiene ninguno. En que formatos
     * se llama lo decide quien abre el editor ({@code NeoApp.showDeckBuilder}).
     */
    public void startByPickingCommander() {
        if (!editor.usesCommander() || commanderMode) {
            return;
        }
        if (editor.commanders().isEmpty()) {
            openPicker(true, false);
        } else if (editor.usesSignatureSpell() && editor.signatureSpell() == null) {
            // Mazo de Oathbreaker con su planeswalker y sin hechizo: se entra
            // por el hueco que falta. Es el caso de abrir un mazo a medias.
            openPicker(true, true);
        }
    }

    // ===============================================================
    // Catalogo

    private void updateFilterToggle() {
        final int active = colours.size() + (colourlessOn ? 1 : 0) + (multicolourOn ? 1 : 0)
                + rarities.size() + cmcs.size() + types.size() + (searchRules ? 1 : 0)
                + powers.size() + toughnesses.size() + (hideUsed ? 1 : 0)
                + (setBox.getValue() != null ? 1 : 0) + (formatBox.getValue() != null ? 1 : 0);
        filterToggle.setText(NeoText.get("deck.filters") + (active == 0 ? "" : " · " + active)
                + (filterRow != null && filterRow.isVisible() ? "  −" : "  +"));
        filterToggle.pseudoClassStateChanged(SELECTED, active > 0 || (filterRow != null && filterRow.isVisible()));
    }

    private void refreshCatalogue() {
        updateFilterToggle();
        // Dentro del grupo de color se SUMA: verde, o incolora, o multicolor.
        final Predicate<PaperCard> colourFilter =
                colours.isEmpty() && !colourlessOn && !multicolourOn ? null : card -> {
            final ColorSet id = card.getRules().getColorIdentity();
            for (final byte c : colours) {
                if (id.hasAnyColor(c)) {
                    return true;
                }
            }
            if (colourlessOn && id.isColorless()) {
                return true;
            }
            return multicolourOn && id.countColors() > 1;
        };

        // Lo ya puesto del todo se pregunta UNA vez, sobre el mazo: carta a
        // carta del catalogo serian 33.000 recorridos (ver usedUpNames). En
        // modo comandante no pinta nada: ahi se elige quien manda.
        final java.util.Set<String> usedUp = hideUsed && !commanderMode
                ? editor.usedUpNames() : java.util.Set.of();

        // Los filtros finos se componen con el de color en un solo predicado:
        // el buscador ya recorre el catalogo una vez y pasa cada carta por el.
        final Predicate<PaperCard> fine = card -> {
            if (usedUp.contains(card.getName())) {
                return false;
            }
            if (!rarities.isEmpty() && !rarities.contains(card.getRarity())) {
                return false;
            }
            if (!types.isEmpty() && java.util.Collections.disjoint(
                    types, card.getRules().getType().getCoreTypes())) {
                return false;
            }
            if (!cmcs.isEmpty()) {
                final int cmc = card.getRules().getManaCost().getCMC();
                // El 7 es "7 o mas", igual que la ultima barra de la curva.
                if (!cmcs.contains(Math.min(cmc, 7))) {
                    return false;
                }
            }
            // Fuerza y resistencia: solo criaturas, como en Forge.
            if (!powers.isEmpty() || !toughnesses.isEmpty()) {
                if (!card.getRules().getType().isCreature()) {
                    return false;
                }
                if (!powers.isEmpty() && !powers.contains(
                        Math.max(0, Math.min(card.getRules().getIntPower(), 7)))) {
                    return false;
                }
                if (!toughnesses.isEmpty() && !toughnesses.contains(
                        Math.max(0, Math.min(card.getRules().getIntToughness(), 7)))) {
                    return false;
                }
            }
            final forge.card.CardEdition ed = setBox.getValue();
            if (ed != null && !inChosenSet(card, ed)) {
                return false;
            }
            final forge.game.GameFormat gf = formatBox.getValue();
            if (gf != null && !gf.getFilterRules().test(card)) {
                return false;
            }
            return colourFilter == null || colourFilter.test(card);
        };

        final String query = search.getText();
        lastQuery = query;
        lastFine = fine;
        if (commanderMode) {
            // En modo comandante manda una sola regla, la del motor: que cartas
            // pueden serlo. Los filtros de color y "solo lo que cabe" no pintan
            // nada aqui, porque es el comandante quien FIJA la identidad.
            catalogueHits = pickingSpell
                    ? editor.signatureCandidates(query, FETCH_LIMIT)
                    : editor.commanderCandidates(query, FETCH_LIMIT);
            catalogueTotal = catalogueHits.size();
        } else {
            // Una sola pasada por el catalogo para las cartas Y el total.
            final DeckEditor.SearchResult found =
                    editor.find(query, onlyLegal, fine, FETCH_LIMIT, searchRules);
            catalogueHits = found.cards;
            catalogueTotal = found.total;
        }

        // Las que no tienen techo salen del monton y se van a su fila. Es
        // exactamente "esto no es tuyo, es del formato": en un draft, las
        // basicas; en la aventura, nada, porque las que tienes se cuentan.
        basicHits = new ArrayList<>();
        if (editor.isLimited() && !commanderMode) {
            final List<PaperCard> rest = new ArrayList<>();
            for (final PaperCard c : catalogueHits) {
                if (editor.owned(c) == Integer.MAX_VALUE) {
                    basicHits.add(c);
                } else {
                    rest.add(c);
                }
            }
            catalogueTotal -= catalogueHits.size() - rest.size();
            catalogueHits = rest;
        }

        // Filtro nuevo, busqueda desde la primera pagina.
        pager.reset();
        fillResults();
    }

    /**
     * Pinta la pagina actual del catalogo.
     *
     * <p>Aparte de {@link #refreshCatalogue()} porque pasar de pagina no tiene
     * que volver a recorrer las 33.000 cartas: la busqueda ya esta hecha, lo
     * unico que cambia es que trozo se pinta.
     */
    private void fillResults() {
        pager.setTotal(catalogueHits.size());

        results.getChildren().clear();
        for (int i = pager.from(); i < pager.to(); i++) {
            results.getChildren().add(catalogueTile(catalogueHits.get(i)));
        }

        // La fila de las basicas no se pagina: son cinco y estan siempre. Y si
        // el buscador no deja ninguna, desaparece entera en vez de dejar un
        // titulo con nada debajo.
        basics.getChildren().clear();
        for (final PaperCard c : basicHits) {
            basics.getChildren().add(basicChip(c));
        }
        showBasics(!basicHits.isEmpty());

        if (catalogueHits.isEmpty() && basicHits.isEmpty()) {
            final Label empty = new Label(!commanderMode ? NeoText.get("deck.noCard")
                    : pickingSpell ? NeoText.get("deck.noSignature")
                            : NeoText.get("deck.noCommander"));
            empty.getStyleClass().add("home-subtitle");
            results.getChildren().add(empty);
        }

        // Si la busqueda da mas de lo que se trae, se dice: un "1.000 cartas"
        // a secas cuando hay 7.659 seria mentir sobre lo que estas viendo.
        resultCount.setText(catalogueTotal > catalogueHits.size()
                ? NeoText.get("deck.someOf", catalogueHits.size(), catalogueTotal)
                : catalogueTotal == 1 ? NeoText.get("count.card")
                        : NeoText.get("count.cards", catalogueTotal));
    }

    /** Enciende o apaga la fila de las basicas, rotulo incluido. */
    private void showBasics(final boolean on) {
        basicsCaption.setVisible(on);
        basics.setVisible(on);
        basics.setManaged(on);
    }

    /**
     * Cuantos resultados se traen de una busqueda.
     *
     * <p>Muy por encima de lo que se pinta — son solo referencias, y paginar
     * sobre ellas es gratis — pero con tope: sin el, una busqueda vacia ordena
     * 33.000 cartas en cada tecla y se nota.
     */
    private static final int FETCH_LIMIT = 1000;

    private List<PaperCard> catalogueHits = new ArrayList<>();
    private int catalogueTotal;

    /**
     * Una carta del catalogo.
     *
     * <p>Click izquierdo anyade una copia; click derecho la amplia para leerla.
     * Si ya la llevas, se ve cuantas encima — asi no hay que ir a la lista de la
     * derecha a comprobarlo.
     */
    private Region catalogueTile(final PaperCard card) {
        return catalogueTile(card, cardWidth);
    }

    private Region catalogueTile(final PaperCard card, final double displayWidth) {
        final CardNode node = new CardNode(displayWidth);
        node.setHoverEnabled(false);
        node.setRotationEnabled(false);
        node.setBadgesVisible(false);
        node.setCard(CardView.getCardForUi(card));

        final Label count = new Label();
        count.getStyleClass().add("catalogue-count");
        final int have = editor.countOf(card);
        count.setText(countLabel(have, editor.owned(card)));
        count.setVisible(!count.getText().isEmpty());

        // Una carta que ya no cabe se ve apagada. Es mas honesto que dejarla
        // igual y contestar con un aviso solo cuando la clicas.
        if (!commanderMode && editor.rejectionReason(card) != null) {
            node.setOpacity(0.4);
        }

        final StackPane box = new StackPane(node, count);
        StackPane.setAlignment(count, Pos.TOP_RIGHT);
        StackPane.setMargin(count, new Insets(4));
        box.getStyleClass().add("catalogue-tile");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        box.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                catalogueMenu(card, box, e.getScreenX(), e.getScreenY());
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (commanderMode) {
                if (!editor.setCommander(card)) {
                    message(NeoText.get(pickingSpell ? "deck.notSignature.title"
                                    : "deck.notCommander.title"),
                            NeoText.get(pickingSpell ? "deck.notSignature"
                                            : "deck.notCommander",
                                    CardText.nameOf(card),
                                    editor.getFormat().getLabel()));
                    return;
                }
                refreshDeck();
                // Oathbreaker se monta en dos pasos: elegido el planeswalker,
                // se pasa SOLO al hechizo. El mazo no es legal sin los dos, y
                // devolver al catalogo entre uno y otro es la forma de que el
                // segundo no se encuentre (el mazo se quedaba en "is missing a
                // signature spell" sin decir donde se arregla).
                if (!pickingSpell && editor.usesSignatureSpell()
                        && editor.signatureSpell() == null) {
                    openPicker(true, true);
                    return;
                }
                // Elegido el comandante, se sale del modo. Quedarse dentro
                // dejaba el catalogo ensenyando SOLO comandantes justo cuando
                // toca montar el mazo, y el unico aviso de que seguias ahi era
                // un boton que no parecia pulsado. Para un companyero
                // (partner/trasfondo) se vuelve a entrar, que es un click.
                //
                // setCommanderMode ya refresca el catalogo, y hace falta: al
                // fijar comandante cambia la identidad de color, o sea que
                // cambian las cartas que caben.
                setCommanderMode(false);
                return;
            }
            final String no = editor.rejectionReason(card);
            if (no != null) {
                message(NeoText.get("deck.doesNotFit"), no);
                return;
            }
            editor.add(card, 1);
            final int now = editor.countOf(card);
            count.setText(countLabel(now, editor.owned(card)));
            count.setVisible(!count.getText().isEmpty());
            // Al llegar al limite la carta se apaga sin tener que rebuscar.
            if (editor.rejectionReason(card) != null) {
                node.setOpacity(0.4);
            }
            refreshDeck();
        });
        return box;
    }

    /**
     * Una tierra basica de la franja: un boton con su nombre y cuantas llevas.
     *
     * <p>Click izquierdo mete una, igual que la tesela; el derecho abre el
     * mismo menu (ver la carta, quitar una...).
     */
    private Region basicChip(final PaperCard card) {
        final Button chip = new Button();
        chip.getStyleClass().add("segment");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        final Runnable label = () -> {
            final int have = editor.countOf(card);
            chip.setText(CardText.nameOf(card) + (have > 0 ? "   ×" + have : "") + "   +");
        };
        label.run();
        chip.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                catalogueMenu(card, chip, e.getScreenX(), e.getScreenY());
                return;
            }
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            final String no = editor.rejectionReason(card);
            if (no != null) {
                message(NeoText.get("deck.doesNotFit"), no);
                return;
            }
            editor.add(card, 1);
            label.run();
            refreshDeck();
        });
        return chip;
    }

    /**
     * La pastilla de la esquina: cuantas llevas y de cuantas.
     *
     * <p>Con pool limitado dice "las que llevas / las que tienes", que es la
     * pregunta de la aventura y del draft: no es "cuantas caben", es "cuantas
     * me quedan sin meter".
     *
     * <p>Pero <b>sin techo no hay denominador</b>. Las tierras basicas de un
     * draft son ilimitadas, o sea {@code Integer.MAX_VALUE}, y eso salia tal
     * cual en la carta: <i>"0/2147483647"</i>. Ahi la pastilla vuelve a ser la
     * de siempre, un simple {@code ×N}.
     */
    private String countLabel(final int have, final int owned) {
        if (editor.isLimited() && owned != Integer.MAX_VALUE) {
            return have + "/" + owned;
        }
        return have > 0 ? "×" + have : "";
    }

    // ===============================================================
    // El mazo

    /**
     * Cambia entre montar el mazo y repasarlo.
     *
     * <p>La vista visual necesita la pantalla entera, asi que sustituye a las
     * dos columnas en vez de meterse en la de la derecha, que es estrecha.
     */
    private void setVisualMode(final boolean on) {
        if (visualMode == on) {
            return;
        }
        visualMode = on;
        if (on) {
            if (stackView == null) {
                stackView = new DeckStackView(editor, cardWidth, new DeckStackView.CardActions() {
                    @Override
                    public void zoom(final PaperCard card) {
                        DeckBuilderScreen.this.zoom(card);
                    }

                    @Override
                    public void menu(final PaperCard card, final Region anchor,
                                     final double screenX, final double screenY,
                                     final boolean isCommander) {
                        if (isCommander) {
                            commanderMenu(card, anchor, screenX, screenY);
                        } else {
                            cardMenu(card, anchor, screenX, screenY);
                        }
                    }
                });
            } else {
                stackView.refresh();
            }
            layout.setCenter(stackView);
        } else {
            layout.setCenter(editView);
            refreshCatalogue();
        }
    }

    /**
     * El problema del mazo, en una linea.
     *
     * <p>Cuando el motor dice que hay cartas fuera de la identidad de color,
     * <b>las lista todas, una por linea</b>. Con un mazo importado de cuarenta
     * y seis cartas ilegales, esa cabecera se comia la pantalla entera y
     * empujaba el resto del editor fuera de la ventana: no habia forma de
     * llegar ni al boton de volver. Antes no se veia porque lo ilegal ni
     * llegaba a entrar.
     *
     * <p>La cabecera es un <b>resumen</b>: dice que pasa y cuantas son. La
     * lista completa esta donde se puede hacer algo con ella — cada carta
     * marcada en el mazo, y el boton de quitarlas.
     */
    private static String oneLine(final String problem) {
        final String[] lines = problem.split("\r?\n");
        if (lines.length <= 1) {
            return problem;
        }
        int rest = 0;
        for (int i = 1; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                rest++;
            }
        }
        return rest == 0 ? lines[0].trim()
                : lines[0].trim() + " " + lines[1].trim()
                        + (rest > 1 ? "  " + NeoText.get("list.more", rest - 1) : "");
    }

    private void refreshDeck() {
        title.setText(editor.getName() + (editor.isDirty() ? " *" : ""));
        refreshFoilAllButton();

        // Estado: cuantas cartas y que le falta para ser legal. El problema lo
        // dice el motor palabra por palabra; no lo reescribimos — pero SI lo
        // recortamos, ver oneLine().
        final String problem = editor.problem();
        final int main = editor.mainCount();
        deckCount.setText(NeoText.get("count.cards", main));
        status.setText(NeoText.get("deck.status",
                editor.getFormat().getLabel(), main,
                // "58 cartas + comandante" se queda corto en Oathbreaker: la
                // zona de mando lleva DOS cartas y el mazo son 60.
                editor.commanders().isEmpty() ? ""
                        : NeoText.get(editor.usesSignatureSpell()
                                && editor.commanders().size() >= 2
                                        ? "deck.plusSignature" : "deck.plusCommander"),
                problem == null ? NeoText.get("deck.ready") : oneLine(problem)));
        status.pseudoClassStateChanged(INVALID, problem != null);

        refreshCommanderRow();
        refreshCompanionRow();

        // Las que ya no caben se marcan en la propia lista. Es informacion, no
        // una accion: no se toca el mazo por nuestra cuenta.
        final java.util.Set<String> illegal = new java.util.HashSet<>();
        for (final PaperCard c : editor.illegalCards()) {
            illegal.add(c.getName());
        }
        cleanup.setVisible(!illegal.isEmpty());
        cleanup.setManaged(!illegal.isEmpty());
        final boolean sideTooBig = editor.sideboardOverflow() > 0;
        clearSide.setText(NeoText.get("deck.clearSideboard", editor.sideboardWithoutCompanion()));
        clearSide.setVisible(sideTooBig);
        clearSide.setManaged(sideTooBig);
        clearMain.setDisable(main == 0);

        // Con el filtro del mazo puesto, un grupo sin nada que ensenyar no sale,
        // y su numero es el de lo que se ve: "Criaturas (12)" encima de tres
        // filas haria pensar que faltan nueve.
        final boolean filtering = deckFilterActive();
        int shown = 0;
        deckList.getChildren().clear();
        for (final Map.Entry<String, Integer> group : editor.typeCounts().entrySet()) {
            final List<Region> rows = new ArrayList<>();
            int inGroup = 0;
            for (final Map.Entry<PaperCard, Integer> e : editor.cardsInGroup(group.getKey())) {
                if (filtering && !deckFilterAccepts(e.getKey())) {
                    continue;
                }
                final Region row = deckRow(e.getKey(), e.getValue());
                row.pseudoClassStateChanged(INVALID, illegal.contains(e.getKey().getName()));
                rows.add(row);
                inGroup += e.getValue();
            }
            if (rows.isEmpty()) {
                continue;
            }
            shown += inGroup;
            final Label heading = new Label(DeckEditor.groupLabel(group.getKey())
                    + "  (" + (filtering ? inGroup : group.getValue()) + ")");
            heading.getStyleClass().add("deck-group");
            deckList.getChildren().add(heading);
            deckList.getChildren().addAll(rows);
        }
        // EL BANQUILLO, al final (Ana, 06-10-2026: no se podia consultar). Un
        // mazo importado de Moxfield lo trae con el "maybeboard", y sin verlo
        // no habia forma de saber por que el mazo no valia.
        if (!filtering) {
            final List<Map.Entry<PaperCard, Integer>> side = editor.sideboardCards();
            if (!side.isEmpty()) {
                int inSide = 0;
                final List<Region> rows = new ArrayList<>();
                for (final Map.Entry<PaperCard, Integer> e : side) {
                    rows.add(sideboardRow(e.getKey(), e.getValue()));
                    inSide += e.getValue();
                }
                final Label heading = new Label(NeoText.get("deck.sideboard", inSide));
                heading.getStyleClass().add("deck-group");
                heading.setId("deck-sideboard");
                deckList.getChildren().add(heading);
                deckList.getChildren().addAll(rows);
            }
        }
        if (filtering) {
            deckCount.setText(NeoText.get("count.cards", main) + "  ·  "
                    + NeoText.get("deck.filterDeck.showing", shown));
        }
        refreshDeckFilterToggle();
        if (deckList.getChildren().isEmpty()) {
            final Label empty = new Label(NeoText.get(filtering && main > 0
                    ? "deck.filterDeck.none" : "deck.empty"));
            empty.getStyleClass().add("home-subtitle");
            empty.setWrapText(true);
            deckList.getChildren().add(empty);
        }

        curve.update(editor);
        stats.update(editor);
        // Con el filtro del mazo PUESTO la curva se esconde (Ana, 03-10-2026):
        // en 1080 a la lista le quedaban dos o tres cartas a la vista, y la
        // curva habla del mazo entero, no de lo que se esta mirando. Con la
        // fila abierta y vacia se queda: ahi la lista ya se ve entera.
        curve.setVisible(!filtering);
        curve.setManaged(!filtering);

        // La vista visual se repinta solo si esta puesta; construirla cuando no
        // se ve seria trabajo tirado.
        if (visualMode && stackView != null) {
            stackView.refresh();
        }
    }

    /**
     * Una linea del mazo.
     *
     * <p>Los botones de quitar y anyadir aparecen al pasar el raton, como en
     * Moxfield. Antes un click suelto en la fila quitaba una carta, que es a la
     * vez poco descubrible y demasiado facil de hacer sin querer: para retocar
     * cantidades hay que ver donde estas pulsando.
     *
     * <p>El click en el nombre amplia la carta, que es el gesto de leer.
     */
    private Region deckRow(final PaperCard card, final int amount) {
        final Label qty = new Label(String.valueOf(amount));
        qty.getStyleClass().add("deck-row-qty");
        qty.setMinWidth(UiScale.px(22));

        final Label name = new Label(CardText.nameOf(card));
        name.getStyleClass().add("deck-row-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        final Region cost = manaCost(card);

        final Button less = stepper("-", () -> {
            editor.remove(card, 1);
            refreshDeck();
            refreshCatalogue();
        });
        final Button more = stepper("+", () -> {
            final String no = editor.rejectionReason(card);
            if (no != null) {
                message(NeoText.get("deck.noMoreCopies"), no);
                return;
            }
            editor.add(card, 1);
            refreshDeck();
            refreshCatalogue();
        });

        final HBox steps = new HBox(2, less, more);
        steps.setAlignment(Pos.CENTER_RIGHT);
        steps.setVisible(true);

        final HBox content = new HBox(7, qty, name, cost, steps);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(7, 8, 7, 8));
        final StackPane row = new StackPane(new DeckArtStrip(card), content);
        name.setMinWidth(0);
        name.setTooltip(new javafx.scene.control.Tooltip(CardText.nameOf(card)));
        row.setMinHeight(UiScale.px(44));
        row.setPrefHeight(UiScale.px(44));
        row.getStyleClass().add("deck-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(Insets.EMPTY);


        // Izquierdo lee la carta; derecho abre el menu de la carta.
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                cardMenu(card, row, e.getScreenX(), e.getScreenY());
            } else if (e.getButton() == MouseButton.PRIMARY) {
                zoom(card);
            }
        });
        return row;
    }

    /** Una linea del banquillo: quitarla, o pasarla al mazo. */
    private Region sideboardRow(final PaperCard card, final int amount) {
        final Label qty = new Label(String.valueOf(amount));
        qty.getStyleClass().add("deck-row-qty");
        qty.setMinWidth(UiScale.px(22));
        final Label name = new Label(CardText.nameOf(card));
        name.getStyleClass().add("deck-row-name");
        name.setMaxWidth(Double.MAX_VALUE);
        name.setMinWidth(0);
        HBox.setHgrow(name, Priority.ALWAYS);
        final Button toMain = stepper("↑", () -> {
            editor.moveSideboardToMain(card);
            refreshDeck();
            refreshCatalogue();
        });
        javafx.scene.control.Tooltip.install(toMain,
                new javafx.scene.control.Tooltip(NeoText.get("deck.sideboard.toMain")));
        final Button less = stepper("-", () -> {
            editor.removeFromSideboard(card, 1);
            refreshDeck();
            refreshCatalogue();
        });
        final HBox content = new HBox(7, qty, name, manaCost(card), new HBox(2, toMain, less));
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(7, 8, 7, 8));
        final StackPane row = new StackPane(new DeckArtStrip(card), content);
        row.setMinHeight(UiScale.px(44));
        row.setPrefHeight(UiScale.px(44));
        row.getStyleClass().add("deck-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setOpacity(0.8);
        row.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                zoom(card);
            }
        });
        return row;
    }

    /** Coste de mana compacto para la lista de cartas. */
    private Region manaCost(final PaperCard card) {
        final HBox pips = new HBox(2);
        pips.setAlignment(Pos.CENTER_RIGHT);
        pips.setMinWidth(Region.USE_PREF_SIZE);
        final String value = card.getRules().getManaCost().toString();
        final java.util.regex.Matcher symbols = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(value);
        while (symbols.find()) {
            final String symbol = symbols.group(1);
            final Label pip = new Label(symbol);
            pip.getStyleClass().add("builder-mana-pip");
            if (!symbol.isEmpty() && "WUBRG".contains(symbol.substring(0, 1))) {
                pip.getStyleClass().add("builder-mana-" + symbol.substring(0, 1).toLowerCase(java.util.Locale.ROOT));
            }
            pip.setMinWidth(symbol.length() > 1 ? 23 : 15);
            pips.getChildren().add(pip);
        }
        javafx.scene.control.Tooltip.install(pips, new javafx.scene.control.Tooltip(value));
        return pips;
    }

    /**
     * El menu del comandante.
     *
     * <p>Quitarlo va en el menu y no en el click, porque quitar el comandante
     * cambia la identidad de color y por tanto que cabe en el mazo entero: es
     * demasiado gordo para que pase por rozar la carta.
     */
    private void commanderMenu(final PaperCard card, final Region anchor,
                               final double screenX, final double screenY) {
        showCardMenu(card, CardActionMenu.actions(
                printingAction(card),
                foilAction(card),
                new CardActionMenu.Action(
                        NeoText.get(editor.isSignatureSpell(card)
                                ? "deck.dropSignature" : "deck.dropCommander"),
                        NeoText.get(editor.isSignatureSpell(card)
                                ? "deck.dropSignature.detail"
                                : "deck.dropCommander.detail"), true,
                        () -> {
                            editor.removeCommander(card);
                            refreshDeck();
                            refreshCatalogue();
                        })));
    }

    /**
     * El menu de una carta del mazo, al click derecho.
     *
     * <p>Es lo que ofrece Moxfield sobre una carta ya metida: verla, cambiarle
     * la edicion y quitarla. Se abre con click derecho porque es el mismo gesto
     * que en la mesa para "quiero hacer algo con esta carta concreta".
     */
    private void cardMenu(final PaperCard card, final Region anchor,
                          final double screenX, final double screenY) {
        final int have = editor.countOf(card);

        CardActionMenu.Action commander = null;
        if (editor.usesCommander()
                && (editor.deckFormat().isLegalCommander(card.getRules())
                        || editor.isSignatureSpell(card))) {
            // En Oathbreaker la misma entrada sirve para los dos huecos, y dice
            // cual: "Hacer comandante" sobre un instantaneo no se entiende.
            final boolean spell = editor.isSignatureSpell(card);
            commander = new CardActionMenu.Action(
                    NeoText.get(spell ? "deck.makeSignature" : "deck.makeCommander"),
                    NeoText.get(spell ? "deck.makeSignature.detail"
                            : "deck.makeCommander.detail"), true,
                    () -> {
                        editor.setCommander(card);
                        refreshDeck();
                        refreshCatalogue();
                    });
        }

        showCardMenu(card, CardActionMenu.actions(
                printingAction(card),
                foilAction(card),
                commander,
                companionAction(card),
                new CardActionMenu.Action(NeoText.get("deck.removeOne"),
                        have > 1 ? NeoText.get("deck.willKeep", have - 1) : null, have > 0,
                        () -> {
                            editor.remove(card, 1);
                            refreshDeck();
                            refreshCatalogue();
                        }),
                have > 1 ? new CardActionMenu.Action(NeoText.get("deck.removeAll"),
                        NeoText.get("deck.allCopies", have), true,
                        () -> {
                            editor.remove(card, have);
                            refreshDeck();
                            refreshCatalogue();
                        }) : null,
                // Al banquillo (Ana, 06-10-2026: "como anyado algo al banquillo?").
                editor.hasSideboard() && editor.copiesOfPrintingInMain(card) > 0
                        ? new CardActionMenu.Action(NeoText.get("deck.toSideboard"), null, true,
                                () -> {
                                    editor.moveMainToSideboard(card);
                                    refreshDeck();
                                    refreshCatalogue();
                                }) : null));
    }

    /**
     * "Hacerlo companero", solo sobre las cartas con la palabra clave y en los
     * mazos que admiten banquillo. En las demas no sale: apagada diria que
     * cualquier carta podria serlo.
     */
    private CardActionMenu.Action companionAction(final PaperCard card) {
        if (!editor.usesCompanion() || !DeckEditor.isCompanionCard(card)
                || card.equals(editor.companion())) {
            return null;
        }
        // Lurrus de comandante: ofrecerlo acabaria en "solo una copia", que no
        // explica nada. Para pasarlo a companero, primero se quita de ahi.
        for (final PaperCard c : editor.commanders()) {
            if (c.getName().equals(card.getName())) {
                return null;
            }
        }
        return new CardActionMenu.Action(NeoText.get("deck.makeCompanion"),
                NeoText.get("deck.makeCompanion.detail"), true,
                () -> {
                    final String no = editor.setCompanion(card);
                    refreshDeck();
                    refreshCatalogue();
                    if (no != null) {
                        message(NeoText.get("deck.notCompanion.title"), no);
                    }
                });
    }

    /** El menu del companero: leerlo, su arte y quitarlo. */
    private void companionMenu(final PaperCard card) {
        showCardMenu(card, CardActionMenu.actions(
                printingAction(card),
                foilAction(card),
                new CardActionMenu.Action(NeoText.get("deck.dropCompanion"), null, true,
                        () -> {
                            editor.removeCompanion();
                            refreshDeck();
                            refreshCatalogue();
                        })));
    }

    private void refreshCompanionRow() {
        companionRow.getChildren().clear();
        final PaperCard card = editor.companion();
        companionRow.setVisible(card != null);
        companionRow.setManaged(card != null);
        if (card == null) {
            return;
        }
        final Label tag = new Label(NeoText.get("deck.companion"));
        tag.getStyleClass().add("caption");
        tag.setMinWidth(Region.USE_PREF_SIZE);
        final Label name = new Label(CardText.nameOf(card));
        name.setWrapText(true);
        name.getStyleClass().add("builder-commander-name");
        final StackPane node = new StackPane(new DeckArtStrip(card), name);
        node.getStyleClass().add("builder-commander");
        // Deja de caber si se elige despues un comandante de otro color: se
        // marca como las del mazo, y "Quitar lo que no cabe" lo incluye.
        node.pseudoClassStateChanged(INVALID, editor.illegalCards().contains(card));
        node.setMinHeight(UiScale.px(44));
        node.setPrefHeight(UiScale.px(44));
        node.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(node, Priority.ALWAYS);
        node.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                companionMenu(card);
            } else if (e.getButton() == MouseButton.PRIMARY) {
                zoom(card);
            }
        });
        companionRow.getChildren().addAll(tag, node);
    }

    /**
     * La opcion de cambiar el arte.
     *
     * <p>Si la carta solo tiene una edicion, la opcion sigue estando pero
     * apagada y diciendolo. Que desaparezca haria pensar que el editor no sabe
     * hacerlo.
     */
    private CardActionMenu.Action printingAction(final PaperCard card) {
        final int printings = editor.printingsOf(card).size();
        return new CardActionMenu.Action(NeoText.get("deck.changePrinting"),
                printings > 1 ? NeoText.get("deck.arts", printings)
                        : NeoText.get("printing.only"),
                printings > 1,
                () -> choosePrinting(card));
    }

    /**
     * Marcar (o quitar) el foil de una carta del mazo (la auditoría del motor, apartado D5).
     *
     * <p>Cambia TODAS las copias de golpe, igual que cambiar el arte — de
     * hecho es exactamente el mismo mecanismo ({@code switchPrinting}): una
     * foil es una impresion mas, solo que con el brillo puesto.
     *
     * <p><b>Fuera de un pool cerrado, libre.</b> Montar un mazo de Commander
     * es un ejercicio de construccion y el foil es cosmetico, igual que el
     * arte: se marca el que se quiera.
     *
     * <p><b>Dentro de la aventura o de un draft, no.</b> Ahi el foil deja de
     * ser un adorno y pasa a ser parte de lo que coleccionas — si un sobre
     * te ha dado el Rayo normal, llevas el normal, y el foil hay que
     * ganarselo abriendolo. Por eso solo se ofrece "marcar como foil" si
     * el contexto incluye la version foil de ESTA impresion exacta. La regla
     * vive en {@code DeckEditor.canFoil}, la misma del boton "Todas foil";
     * y desde el 03-10-2026 tambien las basicas de la aventura y del draft
     * piden el foil abierto (el arte de una basica es gratis, el brillo no).
     */
    private CardActionMenu.Action foilAction(final PaperCard card) {
        final boolean toFoil = !card.isFoil();
        final boolean allowed = !toFoil || editor.canFoil(card);
        return new CardActionMenu.Action(
                NeoText.get(toFoil ? "deck.makeFoil" : "deck.removeFoil"),
                allowed ? null : NeoText.get("deck.foilLocked"),
                allowed,
                () -> {
                    final PaperCard target = toFoil ? card.getFoiled() : card.getUnFoiled();
                    if (editor.switchPrinting(card, target) > 0) {
                        refreshDeck();
                        refreshCatalogue();
                    }
                });
    }

    /**
     * Todo el mazo foil de golpe, o quitarselo si ya lo es entero. Mismas
     * reglas que el de una carta: en Quest, draft o sellado, solo las que te
     * han salido foil.
     */
    private void foilAll() {
        final boolean toFoil = !editor.isAllFoil();
        if (editor.setAllFoil(toFoil) > 0) {
            refreshDeck();
            refreshCatalogue();
        }
        refreshFoilAllButton();
    }

    private void refreshFoilAllButton() {
        final boolean all = editor.isAllFoil();
        foilAllButton.setText(NeoText.get(all ? "deck.foilAll.remove" : "deck.foilAll"));
        foilAllButton.setTooltip(new javafx.scene.control.Tooltip(NeoText.get(
                editor.isLimited() ? "deck.foilAll.limited" : "deck.foilAll.tip")));
    }

    /** Levanta el menu de una carta sobre la capa de dialogos. */
    private void showCardMenu(final PaperCard card, final List<CardActionMenu.Action> actions) {
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new CardActionMenu(card, actions, cardWidth,
                action -> {
                    overlay.hide();
                    action.run.run();
                },
                overlay::hide));
    }

    /** Abre el selector de edicion y aplica lo elegido. */
    private void choosePrinting(final PaperCard card) {
        final List<PaperCard> printings = editor.printingsOf(card);
        if (printings.size() <= 1) {
            return;
        }
        overlay.setOnBackgroundClick(overlay::hide);
        final PrintingDialog[] dialog = new PrintingDialog[1];
        dialog[0] = new PrintingDialog(card, printings, cardWidth, picked -> {
            overlay.hide();
            // Las copias que diga el contador (de fabrica todas): nueve Nazgul
            // con nueve artes, treinta llanuras de dos (Discord, 06-10-2026).
            final int changed = editor.switchPrinting(card, picked, dialog[0].copies());
            if (changed > 0) {
                refreshDeck();
                refreshCatalogue();
            }
        }, overlay::hide).offerCopies(editor.copiesOfPrintingInMain(card));
        overlay.show(dialog[0]);
    }

    /** Boton pequenyo de mas/menos de una fila del mazo. */
    private Button stepper(final String text, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().add("stepper");
        b.setOnAction(e -> action.run());
        // Sin esto el click llega tambien a la fila y ademas amplia la carta.
        b.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);
        return b;
    }

    private void refreshCommanderRow() {
        commanderRow.getChildren().clear();
        final List<PaperCard> cmd = editor.commanders();
        // "Generar mazo" solo tiene sentido con comandante puesto: es de ahi
        // de donde sale la identidad de color con la que el motor elige
        // cartas. Se recalcula aqui porque este metodo es quien se entera de
        // cuando cambia el comandante.
        //
        // mainCommander() y no !cmd.isEmpty(): en Oathbreaker la zona puede
        // tener solo el hechizo, y generar el mazo "para" un instantaneo no
        // sale de ningun sitio.
        final boolean canGenerate = editor.usesCommander() && editor.mainCommander() != null;
        generate.setVisible(canGenerate);
        generate.setManaged(canGenerate);
        if (cmd.isEmpty()) {
            if (editor.usesCommander()) {
                final Label none = new Label(NeoText.get("deck.noCommanderYet"));
                none.getStyleClass().add("home-subtitle");
                commanderRow.getChildren().add(none);
            }
            return;
        }
        for (final PaperCard card : cmd) {
            final Label name = new Label(CardText.nameOf(card));
            name.setWrapText(true);
            name.getStyleClass().add("builder-commander-name");
            final StackPane node = new StackPane(new DeckArtStrip(card), name);
            node.getStyleClass().add("builder-commander");
            node.setMinHeight(UiScale.px(58));
            node.setPrefHeight(UiScale.px(58));
            node.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(node, Priority.ALWAYS);
            node.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.SECONDARY) {
                    commanderMenu(card, node, e.getScreenX(), e.getScreenY());
                } else if (e.getButton() == MouseButton.PRIMARY) {
                    zoom(card);
                }
            });
            commanderRow.getChildren().add(node);
        }
    }

    // ===============================================================
    // Acciones

    /**
     * Quita las cartas que no caben, a peticion del jugador.
     *
     * <p>Nunca se hace solo. Cambiar de comandante puede dejar medio mazo fuera
     * de la identidad de color, y vaciarlo por su cuenta obligaria a volver a
     * meterlo todo a mano — que es mucho peor que tener el mazo un rato en un
     * estado que ya se ve marcado. El boton solo aparece cuando hay algo que
     * quitar.
     */
    private void removeIllegal() {
        final List<PaperCard> bad = editor.illegalCards();
        if (bad.isEmpty()) {
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(NeoText.get(bad.size() == 1 ? "deck.willRemove.one" : "deck.willRemove"))
          .append(System.lineSeparator());
        final List<String> names = new ArrayList<>();
        for (final PaperCard c : bad) {
            names.add(CardText.nameOf(c));
        }
        appendSome(sb, names);

        overlay.setOnBackgroundClick(null);
        overlay.show(new ConfirmDialog(NeoText.get("deck.cleanup"), sb.toString(),
                java.util.List.of(NeoText.get("deck.removeThem"), NeoText.get("common.cancel")), 0,
                choice -> {
                    overlay.hide();
                    if (choice == 0) {
                        editor.removeIllegal();
                        refreshDeck();
                        refreshCatalogue();
                    }
                }));
    }

    /** Vaciar el banquillo, preguntando antes: no se deshace. */
    private void clearSideboard() {
        final int n = editor.sideboardWithoutCompanion();
        if (n <= 0) {
            return;
        }
        overlay.setOnBackgroundClick(null);
        overlay.show(new ConfirmDialog(NeoText.get("deck.clearSideboard", n),
                NeoText.get("deck.clearSideboard.ask", n),
                java.util.List.of(NeoText.get("deck.clearSideboard.yes"), NeoText.get("common.cancel")), 0,
                choice -> {
                    overlay.hide();
                    if (choice == 0) {
                        editor.clearSideboard();
                        refreshDeck();
                        refreshCatalogue();
                    }
                }));
    }

    private void importList() {
        overlay.show(TextDialog.block(NeoText.get("deck.import.title"),
                NeoText.get("deck.import.hint"), "", NeoText.get("deck.import.go"),
                text -> {
                    overlay.hide();
                    // Mismo cuadro para las dos cosas: si lo pegado es una
                    // URL de las que sabemos leer, se manda por ahí; si no,
                    // es la lista de siempre. Un jugador no tiene por qué
                    // saber que por dentro son caminos distintos.
                    if (looksLikeDeckUrl(text)) {
                        applyImportUrl(text.trim());
                    } else {
                        applyImport(text);
                    }
                },
                overlay::hide));
    }

    /**
     * Mete en el mazo lo que se haya pegado.
     *
     * <p>Se reutiliza el importador de Forge tal cual: reconoce el nombre, lo
     * resuelve a una impresion concreta y dice que lineas no ha entendido.
     *
     * <p><b>El comandante se fija ANTES que las cartas</b>, y no es un detalle:
     * es el comandante quien decide la identidad de color, o sea que cuales
     * caben.
     *
     * <p><b>Y la lista entra ENTERA, quepa o no.</b> Antes se filtraba al
     * meterla, y eso se probo con un mazo de verdad: entraron 46 cartas, se
     * quedaron fuera 38 — y fuera es fuera, no habia forma de mirarlas ni de
     * recuperarlas salvo volver a buscarlas a mano una por una. Filtrar al
     * anyadir esta bien montando el mazo carta a carta; al pegar una lista de
     * fuera es lo contrario de lo que hace falta.
     *
     * <p>Que nada se cuele por eso lo garantiza otra cosa, y ya estaba:
     * mientras haya algo que no cabe, el mazo <b>no se puede guardar ni
     * jugar</b> ({@code DeckEditor.blockingProblem}). Asi que se avisa, se
     * ensenya lo que sobra y se ofrece quitarlo de golpe — pero quien decide
     * que hacer con su lista es el jugador.
     */
    private void applyImport(final String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        final DeckImporter.Result result =
                DeckImporter.importCommander(text, editor.getName());
        if (result.deck == null) {
            message(NeoText.get("deck.import.failed"),
                    NeoText.get("deck.import.failed.detail"));
            return;
        }
        applyImportedDeck(result.deck, result.unknown, result.problems);
    }

    /**
     * Mete un mazo YA RESUELTO en el editor y cuenta qué ha pasado.
     *
     * <p>El sitio común de {@link #applyImport} (una lista pegada, resuelta
     * por {@code DeckImporter}) y {@link #applyImportUrl} (Moxfield,
     * Archidekt... resuelto por {@code DeckUrlLoader}): las dos acaban con
     * un {@code Deck} entero y la única diferencia es de dónde salió.
     *
     * <p><b>El comandante se fija ANTES que las cartas</b>: es el comandante
     * quien decide la identidad de color, o sea que cuáles caben.
     *
     * <p><b>Y el mazo entra ENTERO, quepa o no.</b> Antes se filtraba al
     * meterlo, y eso se probó con un mazo de verdad: entraron 46 cartas, se
     * quedaron fuera 38 — y fuera es fuera, no había forma de mirarlas ni de
     * recuperarlas salvo volver a buscarlas a mano una por una. Que nada se
     * cuele por eso lo garantiza otra cosa, y ya estaba:
     * {@code DeckEditor.blockingProblem}.
     */
    private void applyImportedDeck(final Deck deck, final int unknown, final List<String> problems) {
        final List<String> badCommanders = new ArrayList<>();
        // El companero puede venir en tres sitios: su propio encabezado
        // ("Companion", Arena), el banquillo (Moxfield) o - cuando el banquillo
        // es de una carta - tomado por comandante (DeckImporter). En un mazo sin
        // zona de mando lo tercero es seguro que era un companero: salia como
        // "no se pudo hacer comandante" (itch.io, 27-09-2026).
        final List<PaperCard> companions = DeckImporter.companionsOf(deck, editor.usesCommander());
        for (final PaperCard c : deck.getCommanders()) {
            if (companions.contains(c)) {
                continue;
            }
            if (!editor.setCommander(c)) {
                badCommanders.add(CardText.nameOf(c));
            }
        }
        // Se cuenta el resultado, no las llamadas: si la lista trae dos
        // candidatos y no son pareja, el segundo sustituye al primero — decir
        // los dos seria mentir sobre con que se ha quedado el mazo.
        final List<String> newCommanders = new ArrayList<>();
        for (final PaperCard c : editor.commanders()) {
            newCommanders.add(CardText.nameOf(c));
        }

        int added = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            added += editor.addAnyway(e.getKey(), e.getValue());
        }
        // Despues del principal: setCompanion saca la carta del principal si
        // estaba, y al reves se colaria dos veces. Se queda el primero que valga.
        final List<String> badCompanions = new ArrayList<>();
        boolean companionSet = false;
        for (final PaperCard c : companions) {
            if (companionSet) {
                break;
            }
            final String no = editor.setCompanion(c);
            if (no == null) {
                companionSet = true;
            } else {
                badCompanions.add(CardText.nameOf(c) + " - " + oneLine(no));
            }
        }

        refreshDeck();
        refreshCatalogue();

        final List<PaperCard> illegal = editor.illegalCards();
        final PaperCard companion = editor.companion();
        if (unknown == 0 && illegal.isEmpty() && badCommanders.isEmpty() && badCompanions.isEmpty()) {
            message(NeoText.get("deck.import.ok"), NeoText.get("deck.import.added", added)
                    + (companion == null ? "" : "\n\n" + NeoText.get("deck.import.companion",
                            CardText.nameOf(companion))));
            return;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(NeoText.get("deck.import.added", added)).append('\n');
        // De quien es el mazo se dice SIEMPRE, aunque haya ido bien. Es lo que
        // decide que cartas caben, o sea el motivo de casi todo lo que venga
        // debajo — y cuando la lista lo trae en el banquillo (Moxfield), es una
        // suposicion nuestra: hay que poder verla y cambiarla.
        if (!newCommanders.isEmpty()) {
            sb.append('\n').append(NeoText.get("deck.import.commander",
                    String.join(", ", newCommanders))).append('\n');
        }
        if (companion != null) {
            sb.append('\n').append(NeoText.get("deck.import.companion",
                    CardText.nameOf(companion))).append('\n');
        }
        if (!badCommanders.isEmpty()) {
            sb.append('\n').append(NeoText.get("deck.import.badCommander")).append(":\n");
            appendSome(sb, badCommanders);
        }
        if (!badCompanions.isEmpty()) {
            sb.append('\n').append(NeoText.get("deck.import.badCompanion")).append(":\n");
            appendSome(sb, badCompanions);
        }
        if (unknown > 0) {
            sb.append('\n').append(NeoText.get("deck.import.unknown", unknown))
              .append(":\n");
            appendSome(sb, problems);
        }
        if (!illegal.isEmpty()) {
            final List<String> names = new ArrayList<>();
            for (final PaperCard c : illegal) {
                final String why = editor.rejectionReason(c);
                names.add(CardText.nameOf(c) + (why == null ? "" : " - " + why));
            }
            sb.append('\n').append(NeoText.get("deck.import.kept")).append(":\n");
            appendSome(sb, names);
        }
        message(NeoText.get("deck.import.warnings"), sb.toString());
    }

    /**
     * "Pega la URL y ya" (la auditoría del motor, apartado B7): Moxfield, Archidekt, TappedOut,
     * MTGGoldfish. Mismo cuadro que pegar una lista — {@link #importList}
     * mira si lo pegado es una URL y manda aquí en vez de a
     * {@link #applyImport} — así que no hay botón nuevo que aprender.
     *
     * <p><b>Es red: no puede correr en el hilo de JavaFX.</b> Mismo patrón
     * que {@code NetDecksScreen.start}: un hilo aparte, y el resultado
     * SIEMPRE vuelve al hilo de interfaz con {@code Platform.runLater} — pase
     * lo que pase, para que un fallo de red no deje la pantalla congelada a
     * medio "descargando…". Mientras tanto, {@link #importer} se apaga y
     * cambia de texto — dos descargas a la vez no tienen dónde ir, y así el
     * jugador ve que su URL se ha aceptado sin tener que pulsar otra vez.
     */
    /**
     * El "HTTP 404" de Forge, dicho de forma que se sepa que hacer: privado,
     * bloqueado (TappedOut y su Cloudflare) o demasiadas peticiones. Ver
     * {@link forge.neo.deck.DeckUrlFailure}.
     */
    private static String explainUrlFailure(final String url, final String message) {
        final String site = forge.neo.deck.DeckUrlFailure.site(url);
        switch (forge.neo.deck.DeckUrlFailure.classify(message)) {
            case NOT_FOUND:
                return NeoText.get("deck.import.url.notFound", site);
            case BLOCKED:
                return NeoText.get("deck.import.url.blocked", site);
            case TOO_MANY:
                return NeoText.get("deck.import.url.tooMany", site);
            default:
                return message;
        }
    }

    private void applyImportUrl(final String url) {
        if (busyImportingUrl) {
            return;
        }
        busyImportingUrl = true;
        final String importerLabel = importer.getText();
        importer.setDisable(true);
        importer.setText(NeoText.get("deck.import.url.working"));

        final Thread t = new Thread(() -> {
            Deck deck = null;
            String failure = null;
            try {
                deck = forge.deck.DeckUrlLoader.load(url).getDeck();
            } catch (final java.io.IOException | RuntimeException e) {
                failure = explainUrlFailure(url, e.getMessage());
            }
            final Deck loaded = deck;
            final String why = failure;
            javafx.application.Platform.runLater(() -> {
                busyImportingUrl = false;
                importer.setDisable(false);
                importer.setText(importerLabel);
                if (loaded == null) {
                    message(NeoText.get("deck.import.failed"),
                            why == null ? NeoText.get("deck.import.failed.detail") : why);
                    return;
                }
                applyImportedDeck(loaded, 0, List.of());
            });
        }, "Game-neo-deckurl");
        t.setDaemon(true);
        t.start();
    }

    /** Hay una descarga de mazo por URL en marcha. */
    private boolean busyImportingUrl;

    /** Si lo que se ha pegado es una URL en vez de una lista de cartas. */
    private static boolean looksLikeDeckUrl(final String text) {
        final String t = text == null ? "" : text.trim();
        if (t.isEmpty() || t.contains("\n") || t.contains(" ")) {
            return false;
        }
        final String lower = t.toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://")
                || lower.contains("moxfield.com") || lower.contains("archidekt.com")
                || lower.contains("tappedout.net") || lower.contains("mtggoldfish.com");
    }

    /**
     * "Genérame un mazo con este comandante" (la auditoría del motor, apartado B6).
     *
     * <p>Sustituye el principal entero, así que con algo ya montado se
     * pregunta primero — es exactamente el principio 6 de las notas de diseño:
     * lo que no se puede deshacer, se pregunta antes de hacerlo. Con el mazo
     * vacío (recién elegido el comandante, el caso normal) no hay nada que
     * perder y se genera directamente.
     */
    private void generateDeck() {
        if (editor.mainCount() > 0) {
            confirmYesNo(NeoText.get("deck.generate.confirm.title"),
                    NeoText.get("deck.generate.confirm.body", editor.mainCount()),
                    this::doGenerateDeck);
        } else {
            doGenerateDeck();
        }
    }

    private void doGenerateDeck() {
        final int added = editor.generateForCommander();
        refreshDeck();
        refreshCatalogue();
        if (added < 0) {
            message(NeoText.get("deck.generate.failed.title"),
                    NeoText.get("deck.generate.failed.body"));
        }
    }

    /**
     * "Montar solo": el motor monta 40 cartas con tu pool.
     *
     * <p>Sustituye el mazo, asi que con algo ya puesto pregunta (principio 6),
     * igual que "Generar mazo".
     */
    private void autoBuildDeck() {
        if (editor.mainCount() > 0) {
            confirmYesNo(NeoText.get("deck.autoBuild.confirm.title"),
                    NeoText.get("deck.autoBuild.confirm.body", editor.mainCount()),
                    this::doAutoBuild);
        } else {
            doAutoBuild();
        }
    }

    private void doAutoBuild() {
        final int added = editor.autoBuildFromPool();
        refreshDeck();
        refreshCatalogue();
        if (added < 0) {
            message(NeoText.get("deck.generate.failed.title"),
                    NeoText.get("deck.autoBuild.failed.body"));
        }
    }

    /**
     * Piloto de prueba ({@code --lobby-auto}): "Montar solo", guardar y volver.
     * Por los mismos metodos que los botones.
     */
    public void autoBuildSaveLeaveForTest() {
        doAutoBuild();
        save();
        System.out.println("[lobby-auto] mazo montado: " + editor.getName()
                + " (" + editor.mainCount() + " cartas)");
        leave();
    }

    /** "Vaciar el mazo": todo vuelve al pool, para empezar de cero. */
    private void clearMainDeck() {
        if (editor.mainCount() == 0) {
            return;
        }
        confirmYesNo(NeoText.get("deck.clearMain.confirm.title"),
                NeoText.get("deck.clearMain.confirm.body", editor.mainCount()),
                () -> {
                    editor.clearToPool();
                    refreshDeck();
                    refreshCatalogue();
                });
    }

    /** Diálogo de sí/no genérico, para acciones que no se pueden deshacer. */
    private void confirmYesNo(final String title, final String body, final Runnable onYes) {
        final ConfirmDialog dialog = new ConfirmDialog(title, body,
                List.of(NeoText.get("common.yes"), NeoText.get("common.no")), 1,
                index -> {
                    overlay.hide();
                    if (index == 0) {
                        onYes.run();
                    }
                });
        overlay.show(dialog);
    }

    /** Lista los primeros de una lista larga, diciendo cuantos se dejan fuera. */
    private static void appendSome(final StringBuilder sb, final List<String> items) {
        final int shown = Math.min(10, items.size());
        for (int i = 0; i < shown; i++) {
            sb.append("  ").append(items.get(i)).append('\n');
        }
        if (items.size() > shown) {
            sb.append("  ").append(NeoText.get("list.more", items.size() - shown))
              .append('\n');
        }
    }

    /**
     * Ensenya el mazo como texto para copiarlo.
     *
     * <p>No se escribe a un fichero: se deja en un cuadro seleccionable, que es
     * lo que hace falta para pegarlo en Moxfield.
     */
    private void exportList() {
        overlay.show(TextDialog.block(NeoText.get("deck.export.title"),
                NeoText.get("deck.export.hint"),
                editor.toText(), NeoText.get("deck.export.copy"),
                text -> {
                    final javafx.scene.input.ClipboardContent content =
                            new javafx.scene.input.ClipboardContent();
                    content.putString(text);
                    javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                    overlay.hide();
                },
                overlay::hide));
    }

    private void askName() {
        overlay.show(TextDialog.line(NeoText.get("deck.name.title"),
                NeoText.get("deck.name.hint"),
                editor.getName(),
                name -> {
                    overlay.hide();
                    rename(name);
                },
                overlay::hide));
    }

    /**
     * Guardar una copia: pide el nombre ("Nombre (2)" de entrada), la guarda y
     * sigue editando la copia. El original no se toca.
     */
    private void askCopyName() {
        overlay.show(TextDialog.line(NeoText.get("deck.copy.title"),
                NeoText.get("deck.copy.hint"),
                editor.copyName(),
                name -> {
                    overlay.hide();
                    if (name == null || name.isBlank()) {
                        return;
                    }
                    if (!editor.saveAsCopy(name)) {
                        message(NeoText.get("deck.name.taken"), NeoText.get("deck.copy.taken", name.trim()));
                        return;
                    }
                    refreshDeck();
                    message(NeoText.get("deck.copy.ok"), NeoText.get("deck.copy.ok.detail", editor.getName()));
                },
                overlay::hide));
    }

    /**
     * Le pone el nombre nuevo, avisando si con eso se carga otro mazo.
     *
     * <p>Renombrar <b>renombra</b>: al guardar desaparece el fichero del nombre
     * viejo. Eso es lo que se espera y lo que se pidió — antes se quedaban los
     * dos y salia una copia — pero abre una puerta que antes no existia: si el
     * nombre nuevo es el de OTRO mazo tuyo, guardar lo pisa. Y eso tampoco se
     * puede deshacer, asi que se pregunta (principio 6).
     */
    private void rename(final String name) {
        if (name == null || name.isBlank() || name.equals(editor.getName())) {
            return;
        }
        if (!editor.wouldOverwriteAnother(name.trim())) {
            editor.setName(name);
            refreshDeck();
            return;
        }
        overlay.setOnBackgroundClick(null);
        overlay.show(new ConfirmDialog(NeoText.get("deck.name.taken"),
                NeoText.get("deck.name.taken.detail", name.trim()),
                java.util.List.of(NeoText.get("deck.name.taken.yes"),
                        NeoText.get("common.cancel")), 1,
                choice -> {
                    overlay.hide();
                    if (choice != null && choice == 0) {
                        editor.setName(name);
                        refreshDeck();
                    }
                }));
    }

    /**
     * Guarda el mazo.
     *
     * <p><b>Un mazo con cartas ilegales no se guarda.</b> Es lo unico que no
     * puede pasar: un {@code .dck} con cartas fuera de la identidad de color de
     * su comandante acabaria en la pantalla de inicio como si fuera jugable.
     *
     * <p>Un mazo <i>incompleto</i> si se guarda. Que le falten cartas para
     * llegar a 100 no es una trampa, es un trabajo a medias, y negarse a
     * guardarlo obligaria a montar el mazo entero de una sentada o perderlo.
     */
    private void save() {
        if (editor.getName() == null || editor.getName().isBlank()) {
            askName();
            return;
        }

        final String blocking = editor.blockingProblem();
        if (blocking != null) {
            overlay.setOnBackgroundClick(null);
            overlay.show(new ConfirmDialog(NeoText.get("deck.save.blocked"),
                    blocking + System.lineSeparator() + System.lineSeparator()
                    + NeoText.get("deck.save.blocked.detail"),
                    java.util.List.of(NeoText.get("deck.save.removeNow"),
                            NeoText.get("common.cancel")), 0,
                    choice -> {
                        overlay.hide();
                        if (choice == 0) {
                            editor.removeIllegal();
                            refreshDeck();
                            refreshCatalogue();
                            save();
                        }
                    }));
            return;
        }

        editor.save();
        refreshDeck();

        final String problem = editor.problem();
        if (problem == null) {
            message(NeoText.get("deck.save.ok"),
                    NeoText.get("deck.save.ok.detail", editor.getName()));
        } else {
            message(NeoText.get("deck.save.incomplete"),
                    NeoText.get("deck.save.incomplete.detail", editor.getName(),
                            editor.getFormat().getLabel())
                    + System.lineSeparator() + System.lineSeparator() + problem);
        }
    }

    /** Volver, avisando si hay cambios sin guardar. */
    private void leave() {
        if (!editor.isDirty()) {
            onBack.run();
            return;
        }
        overlay.setOnBackgroundClick(null);
        overlay.show(new ConfirmDialog(NeoText.get("deck.unsaved"),
                NeoText.get("deck.unsaved.detail", editor.getName()),
                java.util.List.of(NeoText.get("deck.unsaved.save"),
                        NeoText.get("deck.unsaved.leave"),
                        NeoText.get("deck.unsaved.stay")),
                0,
                choice -> {
                    overlay.hide();
                    if (choice == 2) {
                        return;
                    }
                    if (choice == 0) {
                        editor.save();
                    }
                    onBack.run();
                }));
    }

    /**
     * Menu de click derecho sobre una carta DEL CATALOGO.
     *
     * <p>Antes el click derecho solo ampliaba. Ampliar sigue siendo la primera
     * opcion — es lo que mas se hace — pero desde el catalogo tambien se quiere
     * meter varias copias de golpe (cuatro en Estandar) o nombrar comandante
     * sin cambiar de modo. Es el mismo menu que ya existia para las cartas del
     * mazo, con las acciones que tienen sentido a este lado.
     */
    private void catalogueMenu(final PaperCard card, final Region anchor,
                               final double screenX, final double screenY) {
        final List<CardActionMenu.Action> actions = new ArrayList<>();
        actions.add(new CardActionMenu.Action(NeoText.get("deck.viewCard"), () -> zoom(card)));

        final String no = editor.rejectionReason(card);
        final int room = editor.roomFor(card);
        actions.add(new CardActionMenu.Action(NeoText.get("deck.addOne"), no, no == null, () -> {
            editor.add(card, 1);
            refreshDeck();
            refreshCatalogue();
        }));
        if (no == null && room > 1) {
            final int many = Math.min(room, 4);
            actions.add(new CardActionMenu.Action(NeoText.get("deck.addMany", many), null, true, () -> {
                editor.add(card, many);
                refreshDeck();
                refreshCatalogue();
            }));
        }
        if (editor.hasSideboard()) {
            actions.add(new CardActionMenu.Action(NeoText.get("deck.addToSideboard"), no, no == null, () -> {
                final String why = editor.addToSideboard(card);
                refreshDeck();
                refreshCatalogue();
                if (why != null) {
                    message(NeoText.get("deck.noMoreCopies"), why);
                }
            }));
        }
        if (editor.countOf(card) > 0) {
            actions.add(new CardActionMenu.Action(NeoText.get("deck.removeOne"), null, true, () -> {
                editor.remove(card, 1);
                refreshDeck();
                refreshCatalogue();
            }));
        }
        if (editor.usesCommander()) {
            // En Oathbreaker un instantaneo o conjuro entra por el OTRO hueco,
            // y la entrada lo dice: la misma opcion diciendo "Hacer comandante"
            // sobre un Counterspell no se entiende, y apagada seria mentira.
            final boolean spell = editor.isSignatureSpell(card);
            final boolean can = spell
                    || editor.deckFormat().isLegalCommander(card.getRules());
            actions.add(new CardActionMenu.Action(
                    NeoText.get(spell ? "deck.makeSignature" : "deck.makeCommander"),
                    can ? null : NeoText.get("deck.cannotCommand",
                            editor.getFormat().getLabel()),
                    can, () -> {
                        editor.setCommander(card);
                        refreshDeck();
                        // Igual que el click en la tesela: elegido el
                        // comandante, se sale del modo (y eso ya refresca).
                        if (commanderMode) {
                            setCommanderMode(false);
                        } else {
                            refreshCatalogue();
                        }
                    }));
        }
        final CardActionMenu.Action companion = companionAction(card);
        if (companion != null) {
            actions.add(companion);
        }
        // Lo propio del contexto: en la Aventura, mandar a autovender y sacar.
        for (final forge.neo.deck.DeckContext.Action extra : editor.contextActions(card)) {
            actions.add(new CardActionMenu.Action(extra.label(), extra.note(), extra.enabled(), () -> {
                extra.run().run();
                refreshDeck();
                refreshCatalogue();
            }));
        }

        overlay.setOnBackgroundClick(overlay::hide);
        // Elegir una opcion la EJECUTA, ademas de cerrar el menu. Solo se cerraba:
        // ninguna opcion del catalogo hacia nada (ni anyadir ni el autovender de
        // la Aventura), reportado el 19-09-2026. El menu de las cartas del mazo
        // (showCardMenu) ya lo hacia bien.
        overlay.show(new CardActionMenu(card, actions, menuCardWidth(cardWidth * 2.2),
                a -> {
                    overlay.hide();
                    a.run.run();
                }, overlay::hide));
    }

    /**
     * El ancho que se le pasa al menu de una carta, con techo por el ALTO.
     *
     * <p>El menu pinta la carta a 1,9 veces lo que recibe, y el del catalogo
     * recibe 2,2 cartas: 4,2 veces el ancho de una tesela. En una pantalla
     * ancha eso pasaba del alto de la ventana y la carta se salia por arriba y
     * por abajo (reportado jugando el 15-09-2026). Donde cabe, se queda como
     * estaba; donde no, la carta ocupa como mucho el 72% del alto.
     */
    private double menuCardWidth(final double wanted) {
        final double h = getHeight() > 0 ? getHeight()
                : getScene() != null ? getScene().getHeight() : 0;
        if (h <= 0) {
            return wanted;
        }
        return Math.min(wanted, h * 0.72 / (1.9 * forge.neo.card.CardNode.ASPECT));
    }

    // ===============================================================
    // Auxiliares

    /**
     * La carta a tamanyo de lectura, por el MISMO camino que el resto de la
     * aplicacion.
     *
     * <p>Antes se levantaba a mano sobre la capa de dialogos de esta pantalla,
     * y eso la dejaba <b>imposible de cerrar</b>: el contenido era un
     * {@code StackPane} pelado, que en un {@code StackPane} se estira hasta
     * llenar la capa entera, asi que el unico click que {@code Overlay} contaba
     * como "de fondo" era el del borde de 40 px de la ventana. Ni sobre la
     * carta, ni al lado, ni con Escape — esa capa no escuchaba el teclado.
     * Reportado jugando el 08-09-2026: <i>"amplio una carta en el deck builder
     * y no hay forma de volver; he tenido que reiniciar seis o siete veces
     * montando un solo mazo"</i>.
     *
     * <p>{@link CardZoom} es lo que usan las otras doce pantallas y ya cierra
     * con Escape y con un click en cualquier sitio (principio 7), asi que el
     * arreglo no es anyadir una salida mas: es dejar de tener un camino propio.
     * De paso trae las palabras clave explicadas.
     */
    private void zoom(final PaperCard card) {
        // Con la rueda se pasa a la carta de al lado (pedido en r/forgeMTG el
        // 15-09-2026, como en el Forge de siempre): la del mazo si la carta
        // esta en el mazo, en el orden en que se ve; si no, la pagina del
        // catalogo que tienes delante.
        List<PaperCard> ring = deckOrder();
        int index = ring.indexOf(card);
        if (index < 0) {
            ring = new ArrayList<>(catalogueHits.subList(
                    Math.max(0, Math.min(pager.from(), catalogueHits.size())),
                    Math.max(0, Math.min(pager.to(), catalogueHits.size()))));
            index = ring.indexOf(card);
        }
        if (index < 0) {
            CardZoom.show(this, CardView.getCardForUi(card));
            return;
        }
        final List<PaperCard> list = ring;
        CardZoom.show(this, list.size(), index, i -> CardView.getCardForUi(list.get(i)));
    }

    /** El mazo en el orden en que se ve: comandante y luego grupo a grupo. */
    private List<PaperCard> deckOrder() {
        final List<PaperCard> out = new ArrayList<>(editor.commanders());
        for (final String group : editor.typeCounts().keySet()) {
            for (final Map.Entry<PaperCard, Integer> e : editor.cardsInGroup(group)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    // ===============================================================
    // La funda del mazo

    /**
     * Pone la funda del mazo en el boton de la cabecera.
     *
     * <p>Si el mazo no lleva ninguna se ensenya la de Personalizar, que es la
     * que se va a ver en la mesa, y el texto lo dice: mentir aqui obligaria a
     * empezar una partida para descubrir con que fundas juegas.
     */
    private void refreshSleeveButton() {
        final String id = editor.getSleeve();
        final forge.neo.look.LookItem item = id == null
                ? forge.neo.look.NeoLook.currentSleeve()
                : forge.neo.look.NeoLook.sleeveOf(editor.getDeck());
        sleeveButton.setText(NeoText.get(id == null ? "deck.sleeve.default" : "deck.sleeve"));

        final javafx.scene.image.Image image = item.image();
        if (image == null) {
            sleeveButton.setGraphic(null);
            return;
        }
        final javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
        view.setFitWidth(UiScale.px(20));
        view.setFitHeight(UiScale.px(28));
        view.setPreserveRatio(false);
        view.setSmooth(true);
        sleeveButton.setGraphic(view);
    }

    /**
     * "Con que fundas juega este mazo".
     *
     * <p>La primera casilla es <b>"la mia de siempre"</b>, y es la de fabrica:
     * un mazo sin funda propia usa la de Personalizar. Sin esa casilla, poner
     * una funda seria irreversible desde aqui.
     */
    private void askSleeve() {
        final Label heading = new Label(NeoText.get("deck.sleeve.title"));
        heading.getStyleClass().add("dialog-title");

        final Label hint = new Label(NeoText.get("deck.sleeve.hint"));
        hint.getStyleClass().add("dialog-text");
        hint.setWrapText(true);
        hint.setMaxWidth(UiScale.px(560));
        hint.setMinHeight(Region.USE_PREF_SIZE);

        final String current = editor.getSleeve();
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setPrefWrapLength(UiScale.px(560));
        grid.getChildren().add(sleeveTile(null, NeoText.get("deck.sleeve.yours"), current));
        for (final forge.neo.look.LookItem item : forge.neo.look.NeoLook.sleeves()) {
            grid.getChildren().add(sleeveTile(item, null, current));
        }

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(UiScale.px(360));

        final Button close = new Button(NeoText.get("common.back"));
        close.getStyleClass().add("btn-secondary");
        close.setOnAction(e -> overlay.hide());

        final HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);

        final VBox box = new VBox(12, heading, hint, scroll, footer);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(22, 26, 20, 26));
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(box);
    }

    /** Una funda de la rejilla. {@code item} a null es "la mia de siempre". */
    private Region sleeveTile(final forge.neo.look.LookItem item, final String caption,
                              final String current) {
        final StackPane face = new StackPane();
        face.getStyleClass().add("look-tile");
        face.setPrefSize(UiScale.px(72), UiScale.px(100));
        face.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        face.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        face.pseudoClassStateChanged(PICKED,
                item == null ? current == null : item.getId().equals(current));

        final javafx.scene.image.Image image = item == null ? null : item.image();
        if (image != null) {
            final javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
            view.setFitWidth(UiScale.px(66));
            view.setFitHeight(UiScale.px(94));
            view.setPreserveRatio(false);
            view.setSmooth(true);
            face.getChildren().add(view);
        } else {
            final Label label = new Label(caption == null ? "?" : caption);
            label.getStyleClass().add("home-subtitle");
            label.setWrapText(true);
            label.setMaxWidth(UiScale.px(62));
            label.setMinHeight(Region.USE_PREF_SIZE);
            label.setAlignment(Pos.CENTER);
            face.getChildren().add(label);
        }

        face.setCursor(javafx.scene.Cursor.HAND);
        face.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) {
                return;
            }
            editor.setSleeve(item == null ? null : item.getId());
            refreshSleeveButton();
            refreshDeck();
            overlay.hide();
        });
        return face;
    }

    private void message(final String title, final String body) {
        final Label heading = new Label(title);
        heading.getStyleClass().add("dialog-title");

        final Label text = new Label(body);
        text.getStyleClass().add("dialog-text");
        text.setWrapText(true);
        text.setMaxWidth(UiScale.px(460));
        text.setMinHeight(Region.USE_PREF_SIZE);

        final Button ok = new Button(NeoText.get("banner.understood"));
        ok.getStyleClass().add("btn-primary");
        ok.setOnAction(e -> overlay.hide());

        final HBox footer = new HBox(ok);
        footer.setAlignment(Pos.CENTER_RIGHT);

        final VBox box = new VBox(12, heading, text, footer);
        box.getStyleClass().add("dialog");
        box.setPadding(new Insets(22, 26, 20, 26));
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        overlay.setOnBackgroundClick(null);
        overlay.show(box);
    }

    /** Vuelve a leer las imagenes que hayan llegado de Scryfall. */
    public void refreshArt() {
        // Todo el arbol, dialogos abiertos incluidos. Enumerar a mano las listas
        // de cartas dejaba fuera lo que hubiera en el overlay: el selector de
        // ediciones salia con marcadores la primera vez y bien la segunda.
        CardNode.refreshAllIn(this);
        for (final javafx.scene.Node node : lookupAll(".deck-art-strip")) {
            if (node instanceof DeckArtStrip strip) strip.refresh();
        }
    }

    /** El editor de detras. Herramienta de prueba. */
    public DeckEditor editorForTest() {
        return editor;
    }

    /** Pulsa "Generar mazo" (para la captura de prueba). */
    public void generateForTest() {
        generate.fire();
    }

    /** Abre el selector de funda del mazo (para la captura de prueba). */
    public void showSleevePicker() {
        askSleeve();
    }

    /** Enciende el modo "elegir comandante" (para la captura de prueba). */
    public void toggleCommanderModeForTest() {
        commanderButton.fire();
    }

    /**
     * Elige el primer candidato del hueco que este abierto (para la captura).
     *
     * <p>Es la unica forma de comprobar sin raton el ENCADENADO de Oathbreaker:
     * que al elegir el planeswalker se pasa solo al hechizo insignia. Una
     * captura del selector abierto no lo demuestra — el fallo estaria en el
     * paso siguiente.
     */
    public void pickFirstCandidateForTest() {
        if (!commanderMode || catalogueHits.isEmpty()) {
            System.out.println("[mazo] no hay selector abierto");
            return;
        }
        final PaperCard card = catalogueHits.get(0);
        if (!editor.setCommander(card)) {
            System.out.println("[mazo] rechazada: " + card.getName());
            return;
        }
        System.out.printf("[mazo] elegida %s para el hueco %s%n",
                card.getName(), pickingSpell ? "del hechizo" : "del comandante");
        refreshDeck();
        if (!pickingSpell && editor.usesSignatureSpell()
                && editor.signatureSpell() == null) {
            openPicker(true, true);
            System.out.println("[mazo] encadena al hechizo insignia");
            return;
        }
        setCommanderMode(false);
    }

    /**
     * Abre el menu de la primera carta del mazo (para la captura de prueba).
     *
     * <p>Este menu no se puede comprobar de otra forma: hace falta un click
     * derecho sobre una carta concreta.
     */
    public void showFirstCardMenu() {
        for (final Map.Entry<String, Integer> group : editor.typeCounts().entrySet()) {
            final List<Map.Entry<PaperCard, Integer>> cards =
                    editor.cardsInGroup(group.getKey());
            if (!cards.isEmpty()) {
                cardMenu(cards.get(0).getKey(), this, 0, 0);
                return;
            }
        }
    }

    /**
     * Amplia la primera carta del mazo, por el camino de verdad
     * ({@code --decks --zoom}).
     *
     * <p>Existe porque lo que hay que comprobar no es que la carta salga
     * grande — eso se ve — sino que se pueda <b>cerrar</b>, y para eso hace
     * falta que la ampliacion la levante el mismo metodo que el click del
     * jugador. Ver el javadoc de {@link #zoom(PaperCard)}.
     */
    public void zoomFirstCardForTest() {
        for (final Map.Entry<String, Integer> group : editor.typeCounts().entrySet()) {
            final List<Map.Entry<PaperCard, Integer>> cards =
                    editor.cardsInGroup(group.getKey());
            if (!cards.isEmpty()) {
                zoom(cards.get(0).getKey());
                return;
            }
        }
    }

    /**
     * El selector de arte de la primera carta del mazo con mas de una copia
     * ({@code --printing}), para capturar la eleccion "todas / solo una".
     */
    public void choosePrintingForTest() {
        for (final Map.Entry<String, Integer> group : editor.typeCounts().entrySet()) {
            for (final Map.Entry<PaperCard, Integer> e : editor.cardsInGroup(group.getKey())) {
                if (e.getValue() > 1 && editor.printingsOf(e.getKey()).size() > 1) {
                    choosePrinting(e.getKey());
                    return;
                }
            }
        }
    }

    /** Escribe en el buscador desde fuera (lo usa la captura de verificacion). */
    public void searchFor(final String query) {
        search.setText(query);
        refreshCatalogue();
    }

    /** Pega una decklist desde fuera, como si viniera del cuadro de texto. */
    public void pasteList(final String text) {
        applyImport(text);
    }

    /** Abre la vista visual desde fuera (lo usa la captura de verificacion). */
    public void showVisualView() {
        if (visualButton != null) {
            visualButton.fire();
        }
    }

    /** El mazo que se esta editando, para quien quiera jugarlo despues. */
    public Deck getDeck() {
        return editor.getDeck();
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
    private static final javafx.css.PseudoClass INVALID =
            javafx.css.PseudoClass.getPseudoClass("invalid");
    /** La casilla elegida de una rejilla (la funda del mazo). Igual que en LookScreen. */
    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");
}
