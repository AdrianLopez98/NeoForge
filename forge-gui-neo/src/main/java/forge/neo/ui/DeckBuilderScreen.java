package forge.neo.ui;

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
    private final FlowPane basics = new FlowPane(10, 10);
    private final Label basicsCaption = new Label(NeoText.get("deck.basics"));
    private List<PaperCard> basicHits = new ArrayList<>();
    private final VBox deckList = new VBox(2);
    private final HBox commanderRow = new HBox(10);
    private final ManaCurvePane curve = new ManaCurvePane();
    private final Label title = new Label();
    private final Label status = new Label();
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

    private final Button cleanup = new Button(NeoText.get("deck.cleanup"));
    private final Button generate = new Button(NeoText.get("deck.generate"));
    private final Button importer = new Button(NeoText.get("deck.import"));

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
     * El boton que enciende y apaga ese modo.
     *
     * <p>Es un campo, y no una variable de {@code footer()}, porque el modo
     * <b>se apaga solo</b> al elegir comandante: si el boton no fuera
     * alcanzable desde ahi, se quedaria diciendo "Volver al catalogo" con el
     * catalogo ya vuelto.
     */
    private final Button commanderButton = new Button(NeoText.get("deck.pickCommander"));

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
        this.cardWidth = cardWidth;
        this.onBack = onBack;

        getStyleClass().addAll("table-root", "deck-builder");

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
        title.getStyleClass().add("home-title");
        title.setStyle("-fx-font-size: 1.6em;");

        final Button rename = new Button(NeoText.get("deck.rename"));
        rename.getStyleClass().add("btn-secondary");
        rename.setOnAction(e -> askName());
        // El mazo de un draft o un sellado no se renombra: su nombre es tambien
        // el del evento y el de su marcador. Ver DeckContext.canRename.
        final boolean renameable = editor.getFormat().canRename();
        rename.setVisible(renameable);
        rename.setManaged(renameable);

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

        final HBox row = new HBox(14, title, rename, sleeveButton, gap, views);
        row.setAlignment(Pos.CENTER_LEFT);

        final VBox box = new VBox(4, row, status);
        box.setPadding(new Insets(18, 26, 12, 26));
        return box;
    }

    // ===============================================================
    // Las dos columnas

    private Region columns() {
        final Region left = catalogue();
        final Region right = deckPanel();

        final HBox row = new HBox(18, left, right);
        row.setPadding(new Insets(0, 26, 0, 26));
        HBox.setHgrow(left, Priority.ALWAYS);
        // El mazo tiene ancho fijo: es una lista de texto y no gana nada con mas
        // sitio, mientras que el catalogo siempre agradece una columna mas.
        right.setPrefWidth(360);
        right.setMinWidth(320);
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

        final Button legal = new Button(NeoText.get("deck.onlyFits"));
        legal.getStyleClass().add("segment");
        legal.pseudoClassStateChanged(SELECTED, onlyLegal);
        legal.setOnAction(e -> {
            onlyLegal = !onlyLegal;
            legal.pseudoClassStateChanged(SELECTED, onlyLegal);
            refreshCatalogue();
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        resultCount.getStyleClass().add("dialog-counter");

        final HBox bar = new HBox(10, search, filters, legal);
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
        final HBox typeRow = new HBox(4);
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
        rules.setMinWidth(Region.USE_PREF_SIZE);
        rules.setOnAction(e -> {
            searchRules = !searchRules;
            rules.pseudoClassStateChanged(SELECTED, searchRules);
            search.setPromptText(searchRules
                    ? NeoText.get("deck.search.rules")
                    : NeoText.get("deck.search"));
            refreshCatalogue();
        });

        final Button clear = new Button(NeoText.get("deck.clearFilters"));
        clear.getStyleClass().add("segment");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setOnAction(e -> clearFilters());

        final Region gap2 = new Region();
        HBox.setHgrow(gap2, Priority.ALWAYS);
        final HBox fine = new HBox(10, label(NeoText.get("deck.rarity")), rarityRow,
                label(NeoText.get("deck.cmc")), cmcRow, rules, gap2, clear);
        fine.setAlignment(Pos.CENTER_LEFT);

        // El tipo va en SU PROPIA fila, antes que rareza y coste: es lo
        // primero que se mira al montar un mazo ("cuantas criaturas llevo")
        // y con ocho botones mas los de rareza y coste, todo junto no cabia
        // en 1280 de ancho sin recortar.
        final HBox typeRowLabeled = new HBox(10, label(NeoText.get("deck.type")), typeRow);
        typeRowLabeled.setAlignment(Pos.CENTER_LEFT);

        filterRow = new VBox(6, typeRowLabeled, fine);

        results.setAlignment(Pos.TOP_LEFT);
        results.setPadding(new Insets(12, 4, 12, 0));

        basics.setAlignment(Pos.TOP_LEFT);
        basicsCaption.getStyleClass().add("caption");
        showBasics(false);

        final ScrollPane scroll = new ScrollPane(results);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Con catalogo limitado esta columna ya no es "el catalogo de Magic":
        // es TU COLECCION. Decirlo cambia lo que el jugador espera encontrar.
        final HBox caption = new HBox(10,
                label(editor.getFormat().catalogueLabel()),
                gap, pager, resultCount);
        caption.setAlignment(Pos.CENTER_LEFT);

        // Las basicas van FUERA del scroll, en una franja al pie de la columna.
        //
        // Dentro se solapaban con la primera carta — el rotulo asomaba media
        // palabra por detras — y ademas obligaban a bajar hasta el final del
        // pool para llegar a ellas. Fuera no pueden pisarse con nada y estan
        // siempre a mano, que es lo que se quiere de una fila de cinco cartas
        // que no cambia nunca.
        final VBox box = new VBox(8, caption, bar, filterRow, scroll,
                basicsCaption, basics);
        box.setPadding(new Insets(0, 0, 10, 0));
        return box;
    }

    private Region filterRow;

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
        rarities.clear();
        cmcs.clear();
        types.clear();
        searchRules = false;
        onlyLegal = false;
        for (final javafx.scene.Node n : lookupAll(".segment")) {
            n.pseudoClassStateChanged(SELECTED, false);
        }
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

        final VBox box = new VBox(8, label(NeoText.get("deck.theDeck")), commanderRow, scroll, curve, stats);
        box.getStyleClass().add("deck-panel");
        box.setPadding(new Insets(12, 14, 12, 14));
        return box;
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
        commander.setOnAction(e -> setCommanderMode(!commanderMode));

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

        cleanup.getStyleClass().add("btn-secondary");
        cleanup.setOnAction(e -> removeIllegal());
        cleanup.setVisible(false);
        cleanup.setManaged(false);

        final Button save = new Button(NeoText.get("common.save"));
        save.getStyleClass().add("btn-primary");
        save.setOnAction(e -> save());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        // Ningun boton se encoge por debajo de su texto: cuando la fila no cabe,
        // JavaFX lo corta con puntos suspensivos y queda un pie ilegible.
        for (final Button b : new Button[] {back, cleanup, commander, generate, importer, export, save}) {
            b.setMinWidth(Region.USE_PREF_SIZE);
        }

        final HBox row = new HBox(10, back, gap, cleanup, commander, generate, importer, export, save);
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
        commanderMode = on;
        commanderButton.setText(NeoText.get(on ? "deck.backToCatalogue" : "deck.pickCommander"));
        commanderButton.pseudoClassStateChanged(SELECTED, on);
        refreshCatalogue();
    }

    // ===============================================================
    // Catalogo

    private void refreshCatalogue() {
        final Predicate<PaperCard> colourFilter = colours.isEmpty() ? null : card -> {
            final ColorSet id = card.getRules().getColorIdentity();
            for (final byte c : colours) {
                if (id.hasAnyColor(c)) {
                    return true;
                }
            }
            return false;
        };

        // Los filtros finos se componen con el de color en un solo predicado:
        // el buscador ya recorre el catalogo una vez y pasa cada carta por el.
        final Predicate<PaperCard> fine = card -> {
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
            return colourFilter == null || colourFilter.test(card);
        };

        final String query = search.getText();
        if (commanderMode) {
            // En modo comandante manda una sola regla, la del motor: que cartas
            // pueden serlo. Los filtros de color y "solo lo que cabe" no pintan
            // nada aqui, porque es el comandante quien FIJA la identidad.
            catalogueHits = editor.commanderCandidates(query, FETCH_LIMIT);
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
            basics.getChildren().add(catalogueTile(c));
        }
        showBasics(!basicHits.isEmpty());

        if (catalogueHits.isEmpty() && basicHits.isEmpty()) {
            final Label empty = new Label(commanderMode
                    ? NeoText.get("deck.noCommander")
                    : NeoText.get("deck.noCard"));
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
        for (final javafx.scene.Node n : new javafx.scene.Node[] {basicsCaption, basics}) {
            n.setVisible(on);
            n.setManaged(on);
        }
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
        final CardNode node = new CardNode(cardWidth);
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
                    message(NeoText.get("deck.notCommander.title"),
                            NeoText.get("deck.notCommander", CardText.nameOf(card),
                                    editor.getFormat().getLabel()));
                    return;
                }
                refreshDeck();
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

        // Estado: cuantas cartas y que le falta para ser legal. El problema lo
        // dice el motor palabra por palabra; no lo reescribimos — pero SI lo
        // recortamos, ver oneLine().
        final String problem = editor.problem();
        final int main = editor.mainCount();
        status.setText(NeoText.get("deck.status",
                editor.getFormat().getLabel(), main,
                editor.commanders().isEmpty() ? "" : NeoText.get("deck.plusCommander"),
                problem == null ? NeoText.get("deck.ready") : oneLine(problem)));
        status.pseudoClassStateChanged(INVALID, problem != null);

        refreshCommanderRow();

        // Las que ya no caben se marcan en la propia lista. Es informacion, no
        // una accion: no se toca el mazo por nuestra cuenta.
        final java.util.Set<String> illegal = new java.util.HashSet<>();
        for (final PaperCard c : editor.illegalCards()) {
            illegal.add(c.getName());
        }
        cleanup.setVisible(!illegal.isEmpty());
        cleanup.setManaged(!illegal.isEmpty());

        deckList.getChildren().clear();
        for (final Map.Entry<String, Integer> group : editor.typeCounts().entrySet()) {
            final Label heading = new Label(DeckEditor.groupLabel(group.getKey())
                    + "  (" + group.getValue() + ")");
            heading.getStyleClass().add("deck-group");
            deckList.getChildren().add(heading);
            for (final Map.Entry<PaperCard, Integer> e : editor.cardsInGroup(group.getKey())) {
                final Region row = deckRow(e.getKey(), e.getValue());
                row.pseudoClassStateChanged(INVALID, illegal.contains(e.getKey().getName()));
                deckList.getChildren().add(row);
            }
        }
        if (deckList.getChildren().isEmpty()) {
            final Label empty = new Label(NeoText.get("deck.empty"));
            empty.getStyleClass().add("home-subtitle");
            empty.setWrapText(true);
            deckList.getChildren().add(empty);
        }

        curve.update(editor);
        stats.update(editor);

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
        qty.setMinWidth(22);

        final Label name = new Label(CardText.nameOf(card));
        name.getStyleClass().add("deck-row-name");
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);

        final Label cost = new Label(card.getRules().getManaCost().toString());
        cost.getStyleClass().add("deck-row-cost");

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
        steps.setVisible(false);

        final HBox row = new HBox(8, qty, name, cost, steps);
        row.getStyleClass().add("deck-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(3, 6, 3, 6));

        row.setOnMouseEntered(e -> {
            steps.setVisible(true);
            cost.setVisible(false);
        });
        row.setOnMouseExited(e -> {
            steps.setVisible(false);
            cost.setVisible(true);
        });

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
                new CardActionMenu.Action(NeoText.get("deck.dropCommander"),
                        NeoText.get("deck.dropCommander.detail"), true,
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
                && editor.deckFormat().isLegalCommander(card.getRules())) {
            commander = new CardActionMenu.Action(NeoText.get("deck.makeCommander"),
                    NeoText.get("deck.makeCommander.detail"), true,
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
                        }) : null));
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
     * {@code editor.printingsOf(card)} ya incluye la version foil de ESTA
     * impresion exacta: es la misma comprobacion que ya usa
     * {@code QuestDeckContext}/{@code DraftDeckContext} para el arte, sin
     * inventar una regla nueva.
     */
    private CardActionMenu.Action foilAction(final PaperCard card) {
        final boolean toFoil = !card.isFoil();
        final boolean allowed = !toFoil || !editor.isLimited() || ownsFoilOf(card);
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

    /** Si tienes (has abierto) la version foil de esta impresion exacta. */
    private boolean ownsFoilOf(final PaperCard card) {
        return editor.printingsOf(card).contains(card.getFoiled());
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
        overlay.show(new PrintingDialog(card, printings, cardWidth, picked -> {
            overlay.hide();
            if (editor.switchPrinting(card, picked) > 0) {
                refreshDeck();
                refreshCatalogue();
            }
        }, overlay::hide));
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
        generate.setVisible(editor.usesCommander() && !cmd.isEmpty());
        generate.setManaged(editor.usesCommander() && !cmd.isEmpty());
        if (cmd.isEmpty()) {
            if (editor.usesCommander()) {
                final Label none = new Label(NeoText.get("deck.noCommanderYet"));
                none.getStyleClass().add("home-subtitle");
                commanderRow.getChildren().add(none);
            }
            return;
        }
        for (final PaperCard card : cmd) {
            final CardNode node = new CardNode(cardWidth * 0.8);
            node.setRotationEnabled(false);
            node.setBadgesVisible(false);
            node.setCard(CardView.getCardForUi(card));
            node.setCommanderStyle(true);
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
        for (final PaperCard c : deck.getCommanders()) {
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

        refreshDeck();
        refreshCatalogue();

        final List<PaperCard> illegal = editor.illegalCards();
        if (unknown == 0 && illegal.isEmpty() && badCommanders.isEmpty()) {
            message(NeoText.get("deck.import.ok"), NeoText.get("deck.import.added", added));
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
        if (!badCommanders.isEmpty()) {
            sb.append('\n').append(NeoText.get("deck.import.badCommander")).append(":\n");
            appendSome(sb, badCommanders);
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
                failure = e.getMessage();
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
        if (editor.countOf(card) > 0) {
            actions.add(new CardActionMenu.Action(NeoText.get("deck.removeOne"), null, true, () -> {
                editor.remove(card, 1);
                refreshDeck();
                refreshCatalogue();
            }));
        }
        if (editor.usesCommander()) {
            final boolean can = editor.deckFormat().isLegalCommander(card.getRules());
            actions.add(new CardActionMenu.Action(NeoText.get("deck.makeCommander"),
                    can ? null : NeoText.get("deck.cannotCommand",
                            editor.getFormat().getLabel()),
                    can, () -> {
                        editor.setCommander(card);
                        refreshDeck();
                        refreshCatalogue();
                    }));
        }

        overlay.setOnBackgroundClick(overlay::hide);
        overlay.show(new CardActionMenu(card, actions, cardWidth * 2.2,
                a -> overlay.hide(), overlay::hide));
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
        CardZoom.show(this, CardView.getCardForUi(card));
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
        view.setFitWidth(20);
        view.setFitHeight(28);
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
        hint.setMaxWidth(560);
        hint.setMinHeight(Region.USE_PREF_SIZE);

        final String current = editor.getSleeve();
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setPrefWrapLength(560);
        grid.getChildren().add(sleeveTile(null, NeoText.get("deck.sleeve.yours"), current));
        for (final forge.neo.look.LookItem item : forge.neo.look.NeoLook.sleeves()) {
            grid.getChildren().add(sleeveTile(item, null, current));
        }

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(360);

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
        face.setPrefSize(72, 100);
        face.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        face.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        face.pseudoClassStateChanged(PICKED,
                item == null ? current == null : item.getId().equals(current));

        final javafx.scene.image.Image image = item == null ? null : item.image();
        if (image != null) {
            final javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
            view.setFitWidth(66);
            view.setFitHeight(94);
            view.setPreserveRatio(false);
            view.setSmooth(true);
            face.getChildren().add(view);
        } else {
            final Label label = new Label(caption == null ? "?" : caption);
            label.getStyleClass().add("home-subtitle");
            label.setWrapText(true);
            label.setMaxWidth(62);
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
        text.setMaxWidth(460);
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
