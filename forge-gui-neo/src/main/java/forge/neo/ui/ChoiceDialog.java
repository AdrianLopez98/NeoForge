package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import forge.game.card.CardView;
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
import javafx.scene.layout.VBox;

/**
 * Dialogo generico de "elige N de esta lista".
 *
 * <p><b>Esta es la pieza que evita que ninguna carta bloquee la partida.</b> Los
 * metodos genericos de {@code IGuiGame} ({@code getChoices}, {@code one},
 * {@code many}, {@code chooseSingleEntityForEffect}...) cubren la inmensa
 * mayoria de las interacciones raras de las 33.696 cartas. Con un dialogo
 * decente aqui, cualquier carta se puede jugar aunque no tenga una interfaz
 * bonita hecha a medida.
 *
 * <p>Si lo que hay que elegir son cartas, se pintan como cartas: elegir por
 * imagen es mucho mas rapido que leer una lista de nombres.
 *
 * @param <T> tipo de lo que se elige
 */
public class ChoiceDialog<T> extends VBox {

    private final List<T> chosen = new ArrayList<>();
    // Por NODO y no por VALOR: dos copias de la misma carta (dos Sol Ring en
    // un pool de sellado) son dos opciones DISTINTAS aunque sean iguales por
    // valor. Un Set<T> las confundiria — marcar una marcaria las dos, y
    // desmarcar cualquiera de las dos las desmarcaria las dos — porque
    // Set.contains/remove compara por equals(), no por que boton has clicado.
    private final LinkedHashMap<Region, T> selected = new LinkedHashMap<>();
    private final int min;
    private final int max;
    private final Label counter = new Label();
    private final Button accept = new Button();
    private final Button selectAll = new Button();
    private final Button auto = new Button();
    private final javafx.scene.control.CheckBox remember = new javafx.scene.control.CheckBox();

    /**
     * Las opciones y su nodo, en el orden en que se pintan.
     *
     * <p>Hace falta para {@link #selectAllInOrder()}: marcar "todas en orden"
     * es marcarlas en ESTE orden, y el mapa de seleccionadas va por nodo — no
     * hay forma de recorrerlo al reves.
     */
    private final List<Region> nodesInOrder = new ArrayList<>();
    private final List<T> optionsInOrder = new ArrayList<>();

    private final boolean readOnly;

    // Lo que hay que redimensionar cuando por fin se sabe cuanto sitio hay.
    private final Label heading;
    private final FlowPane items;
    private final ScrollPane scroll;
    private final boolean anyCard;
    private boolean sized;

    // ---- LISTAS ENORMES: buscador (Discord, 06-10-2026) ----
    //
    // "Elige un nombre de carta" manda los ~33.000 nombres del juego. Pintarlos
    // como botones congelaba la mesa un buen rato, y luego no habia forma de
    // encontrar uno sin bajar a mano: "couldn't find a box to type the name".
    // Con mas de SEARCH_FROM opciones de texto el dialogo lleva un buscador y
    // solo pinta las primeras SHOWN que coinciden; cada boton se crea la
    // primera vez que hace falta y se guarda, asi que lo marcado sigue marcado
    // al cambiar la busqueda.

    /** A partir de cuantas opciones (de texto) sale el buscador. */
    static final int SEARCH_FROM = 60;
    /** Cuantas coincidencias se pintan a la vez. */
    static final int SHOWN = 120;

    private final boolean searchMode;
    private javafx.scene.control.TextField search;
    private final Label searchNote = new Label();
    private List<T> allOptions;
    private String[] searchKeys;
    private Region[] nodeCache;
    private Function<T, String> searchDisplay;
    private double searchCardWidth;
    /** El ancho de cada opcion de texto, cuando ya se ha medido (layoutChildren). */
    private double optionWidth = -1;

    public ChoiceDialog(final String title, final List<T> options, final int min, final int max,
                        final Function<T, String> display, final double cardWidth,
                        final Consumer<List<T>> onDone) {
        this(title, options, min, max, display, cardWidth, null, onDone);
    }

    /**
     * Con algo ya elegido de entrada.
     *
     * <p>El motor a veces dice cual es la opcion de siempre (el formato de
     * sobre que elegiste la ultima vez, por ejemplo). Marcarla es la diferencia
     * entre un dialogo que se contesta con un click y uno en el que hay que
     * adivinar que se espera de ti.
     */
    public ChoiceDialog(final String title, final List<T> options, final int min, final int max,
                        final Function<T, String> display, final double cardWidth,
                        final List<T> preselected,
                        final Consumer<List<T>> onDone) {
        this(title, options, min, max, display, cardWidth, preselected, false, onDone);
    }

    /**
     * Con el ORDEN a la vista: cada opcion marcada lleva su numero (1, 2, 3...).
     *
     * <p>Para cuando lo que se contesta es una secuencia y no un conjunto:
     * ordenar disparos simultaneos, cartas al fondo de la biblioteca. El
     * dialogo ya devolvia las opciones en el orden en que se clicaban, pero no
     * lo ensenyaba — reportado desde itch.io el 24-09-2026: <i>"doesn't show
     * which one we choose to be first and so on"</i>. Con dos opciones
     * marcadas se ven igual, y no hay forma de saber cual resuelve antes.
     */
    public ChoiceDialog(final String title, final List<T> options, final int min, final int max,
                        final Function<T, String> display, final double cardWidth,
                        final List<T> preselected, final boolean ordered,
                        final Consumer<List<T>> onDone) {
        // min/max negativos significan "no hay nada que elegir, solo enseñar":
        // es lo que manda IGuiGame.reveal(). Sin esto sale un dialogo de
        // eleccion absurdo con el contador a "0 de 0".
        this.readOnly = min < 0 || max <= 0;
        this.min = readOnly ? 0 : min;
        this.max = readOnly ? 0 : Math.max(min, max);
        // Con una sola que elegir no hay orden que ensenyar.
        this.ordered = ordered && !readOnly && this.max > 1;

        getStyleClass().add("dialog");
        setSpacing(12);
        setPadding(new Insets(18));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        heading = new Label(title == null ? NeoText.get("choice.title") : title);
        heading.getStyleClass().add("dialog-title");
        heading.setWrapText(true);
        heading.setMaxWidth(UiScale.px(760));

        items = new FlowPane(10, 10);
        items.setAlignment(Pos.CENTER);
        items.setPrefWrapLength(UiScale.px(760));

        boolean card0 = false;
        for (final T option : options) {
            if (option instanceof CardView || option instanceof forge.item.PaperCard) {
                card0 = true;
                break;
            }
        }
        searchMode = !card0 && !readOnly && options.size() > SEARCH_FROM;
        for (final T option : searchMode ? List.<T>of() : options) {
            final Region node = optionNode(option, display, cardWidth, items);
            if (readOnly) {
                node.setOnMouseClicked(null);
                // Una carta NO se deshabilita: tiene que seguir ampliandose con
                // el click derecho, que es la unica forma de leer lo revelado.
                // Un boton de texto si, que conserva su accion.
                if (!(node instanceof CardNode)) {
                    node.setDisable(true);
                }
                node.setOpacity(0.95);
            }
            items.getChildren().add(this.ordered ? withBadge(node) : node);
            nodesInOrder.add(node);
            optionsInOrder.add(option);
        }

        boolean card = false;
        for (final T option : options) {
            if (option instanceof CardView || option instanceof forge.item.PaperCard) {
                card = true;
                break;
            }
        }
        anyCard = card;

        // La pastilla sobresale de la esquina; sin este margen el visor la
        // corta en la primera fila y en la primera columna.
        final Insets badgeRoom = this.ordered ? new Insets(8, 4, 4, 8) : Insets.EMPTY;
        // Y la carta con el raton encima crece y SUBE (CardNode.hoverIn): el
        // visor la cortaba por arriba en la primera fila y por los lados en
        // las de los extremos (itch.io, 30-09-2026: "Looking at cards in ...
        // library" con la carta ampliada sin cabeza). Se le deja el sitio que
        // va a ocupar, con la misma cuenta que usa ella.
        final Insets hoverRoom = anyCard ? CardNode.hoverRoomFor(cardWidth) : Insets.EMPTY;
        items.setPadding(new Insets(
                Math.max(badgeRoom.getTop(), hoverRoom.getTop()),
                Math.max(badgeRoom.getRight(), hoverRoom.getRight()),
                Math.max(badgeRoom.getBottom(), hoverRoom.getBottom()),
                Math.max(badgeRoom.getLeft(), hoverRoom.getLeft())));
        scroll = new ScrollPane(items);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        // Nunca barra horizontal: el texto ENVUELVE. La que salia se comia un
        // renglon de los pocos que habia y encima dejaba media opcion fuera de
        // la vista, que es lo peor de los dos mundos.
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // El alto de verdad se pone en layoutChildren: aqui todavia no hay
        // escena y no se sabe cuanta ventana hay. Esto es solo el arranque.
        scroll.setPrefViewportHeight(anyCard
                ? cardWidth * CardNode.ASPECT + 40 + items.getPadding().getTop() + items.getPadding().getBottom()
                : 200);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        counter.getStyleClass().add("dialog-counter");
        counter.setVisible(!readOnly);

        accept.getStyleClass().add("btn-primary");
        accept.setOnAction(e -> {
            chosen.clear();
            chosen.addAll(selected.values());
            onDone.accept(new ArrayList<>(chosen));
        });

        // "Marcar todas, en el orden en que salen".
        //
        // Nace de una peticion jugando: el motor pidio ordenar 35 disparos
        // simultaneos (una criatura muriendo con tres Vengadoras y un Sephiroth
        // en mesa) y habia que clicar los 35 uno a uno. La mayoria de las veces
        // el orden da igual — son copias del mismo disparo — y lo unico que
        // quieres es decir "asi esta bien".
        //
        // Marca en el ORDEN EN QUE APARECEN, que es justo lo que significa
        // aceptar el orden propuesto: `selected` es un LinkedHashMap y el
        // resultado sale de `values()`, o sea en orden de insercion.
        //
        // Solo cuando hay varias que elegir y de verdad ahorra clicks: con dos
        // o tres opciones es un boton de mas para leer.
        // "Auto": aceptar el orden propuesto de un golpe, como el boton del
        // mismo nombre de Forge. Pedido en itch.io el 28-09-2026: "Is there no
        // way to auto sort simultaneous triggered abilities? I think normal
        // Forge had an auto button for that". Solo cuando hay que ordenarlas
        // TODAS (disparos simultaneos): si se puede dejar alguna fuera, "todas
        // en este orden" no es la unica respuesta razonable y no se adivina.
        final boolean autoFits = this.ordered && this.min == options.size();
        auto.setId("choice-auto");
        auto.getStyleClass().add("btn-secondary");
        auto.setText(NeoText.get("choice.auto"));
        auto.setVisible(autoFits);
        auto.setManaged(autoFits);
        // Contesta DIRECTAMENTE con el orden de la pantalla, como el Auto de
        // Forge (addAll + finish): no hace falta Aceptar despues. Antes llenaba
        // la seleccion por dentro y "pulsaba" Aceptar con fire(), pero Aceptar
        // seguia deshabilitado (no se habia refrescado) y fire() no hace nada
        // en un boton deshabilitado: no pasaba nada a la vista y la seleccion
        // quedaba llena sin marcar, asi que los clics siguientes parecian
        // encender y apagar al azar (itch.io, 29-09-2026).
        //
        // Y como el de Forge, respeta lo que ya hayas ordenado: eso va primero,
        // en tu orden, y el resto detras en el de la pantalla.
        auto.setOnAction(e -> {
            chosen.clear();
            chosen.addAll(selected.values());
            for (int i = 0; i < nodesInOrder.size(); i++) {
                if (!selected.containsKey(nodesInOrder.get(i))) {
                    chosen.add(optionsInOrder.get(i));
                }
            }
            onDone.accept(new ArrayList<>(chosen));
        });

        // Con "Auto" a la vista, "marcar todas" sobra: hace lo mismo con un
        // click de mas.
        final boolean worthIt = !readOnly && max > 1 && options.size() > 3 && !autoFits;
        selectAll.getStyleClass().add("btn-secondary");
        selectAll.setText(NeoText.get("choice.selectAllInOrder"));
        selectAll.setVisible(worthIt);
        selectAll.setManaged(worthIt);
        selectAll.setOnAction(e -> selectAllInOrder());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        remember.setText(NeoText.get("choice.rememberOrder"));
        remember.getStyleClass().add("dialog-counter");
        remember.setVisible(false);
        remember.setManaged(false);
        final HBox footer = new HBox(10, counter, remember, gap, selectAll, auto, accept);
        footer.setAlignment(Pos.CENTER_LEFT);

        if (searchMode) {
            // "Todas en orden" y "Auto" son para listas cortas que se ven
            // enteras; con un buscador no se sabe que es "todas".
            selectAll.setVisible(false);
            selectAll.setManaged(false);
            auto.setVisible(false);
            auto.setManaged(false);
            allOptions = new ArrayList<>(options);
            searchDisplay = display;
            searchCardWidth = cardWidth;
            nodeCache = new Region[allOptions.size()];
            searchKeys = new String[allOptions.size()];
            for (int i = 0; i < searchKeys.length; i++) {
                final T o = allOptions.get(i);
                searchKeys[i] = fold(display == null ? String.valueOf(o) : display.apply(o));
            }
            search = new javafx.scene.control.TextField();
            search.setId("choice-search");
            search.getStyleClass().add("text-input");
            search.setPromptText(NeoText.get("choice.search.prompt"));
            search.textProperty().addListener((obs, was, now) -> refilter());
            // Intro elige la primera coincidencia: escribir "lightning bolt" e
            // Intro, sin tocar el raton.
            search.setOnAction(e -> {
                final Region first = items.getChildren().isEmpty() ? null
                        : (Region) items.getChildren().get(0);
                final int idx = indexOfNode(first);
                if (idx >= 0 && !selected.containsKey(first)) {
                    toggle(allOptions.get(idx), first);
                }
            });
            searchNote.getStyleClass().add("dialog-counter");
            getChildren().addAll(heading, search, searchNote, scroll, footer);
            refilter();
            sceneProperty().addListener((obs, was, now) -> {
                if (now != null) {
                    javafx.application.Platform.runLater(search::requestFocus);
                }
            });
        } else {
            getChildren().addAll(heading, scroll, footer);
        }

        // Lo que venga ya elegido, marcado. Se hace despues de construir los
        // nodos porque marcar es justo lo mismo que clicarlos.
        //
        // OJO con los duplicados: dos copias iguales (dos Bosque) tienen el
        // MISMO indexOf la primera vez. Cada nodo ya usado se descarta antes
        // de buscar el siguiente, para que dos Bosque preseleccionados marquen
        // dos nodos DISTINTOS y no el mismo dos veces.
        if (preselected != null && !readOnly) {
            final List<Integer> used = new ArrayList<>();
            for (final T option : preselected) {
                int idx = -1;
                for (int i = 0; i < options.size(); i++) {
                    if (!used.contains(i) && Objects.equals(options.get(i), option)) {
                        idx = i;
                        break;
                    }
                }
                if (idx >= 0) {
                    used.add(idx);
                    toggle(option, searchMode ? searchNode(idx) : nodesInOrder.get(idx));
                }
            }
        }
        updateState();
    }

    private Region optionNode(final T option, final Function<T, String> display,
                              final double cardWidth, final FlowPane items) {
        // Si es una carta se pinta como carta; si no, como boton de texto.
        if (option instanceof CardView cv) {
            final CardNode node = new CardNode(cardWidth);
            node.setCard(cv);
            node.setOnMouseClicked(e -> {
                // El derecho amplia la carta (CardZoom / la mesa): no elige.
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    toggle(option, node);
                }
            });
            return node;
        }
        // Y una carta en papel tambien es una carta. Llega asi desde IGuiBase
        // (las recompensas de la aventura, el premio de un sobre): sin esto
        // saldria como una linea de texto con el nombre.
        if (option instanceof forge.item.PaperCard pc) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(pc));
            node.setOnMouseClicked(e -> {
                // El derecho amplia la carta (CardZoom / la mesa): no elige.
                if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    toggle(option, node);
                }
            });
            return node;
        }
        final Button b = new Button(display == null ? String.valueOf(option) : display.apply(option));
        b.getStyleClass().add("choice-item");
        b.setWrapText(true);
        b.setMaxWidth(UiScale.px(340));
        // El texto envuelve, asi que el alto depende del ancho: sin esto el
        // boton se queda con el alto de una linea y el parrafo sale cortado.
        b.setMinHeight(Region.USE_PREF_SIZE);
        // Un parrafo se lee alineado a la izquierda; una etiqueta corta
        // ("Ixalan") queda mejor centrada. Lo decide su propia longitud.
        if (b.getText() != null && b.getText().length() > 48) {
            b.setAlignment(Pos.CENTER_LEFT);
            b.setTextAlignment(javafx.scene.text.TextAlignment.LEFT);
        }
        b.setOnAction(e -> toggle(option, b));
        return b;
    }

    /**
     * Marca todas las opciones, de arriba abajo, hasta el maximo.
     *
     * <p>Si ya estaban todas marcadas, las desmarca: el mismo boton deshace lo
     * que acaba de hacer, que es lo que se espera de el cuando te has
     * equivocado — y aqui equivocarse es facil, porque marca 35 cosas de golpe.
     */
    private void selectAllInOrder() {
        final int room = Math.min(max, nodesInOrder.size());
        final boolean undo = selected.size() >= room;

        // Se limpia siempre antes: marcar "en orden" tiene que dar el orden de
        // la pantalla, y no el de lo que hubiera clicado antes por su cuenta.
        for (final Region node : new ArrayList<>(selected.keySet())) {
            unmark(node);
        }
        selected.clear();

        if (!undo) {
            for (int i = 0; i < nodesInOrder.size() && selected.size() < room; i++) {
                final Region node = nodesInOrder.get(i);
                selected.put(node, optionsInOrder.get(i));
                mark(node);
            }
        }
        updateState();
    }

    private void mark(final Region node) {
        if (!node.getStyleClass().contains("chosen")) {
            node.getStyleClass().add("chosen");
        }
        if (node instanceof CardNode cn) {
            cn.setSelectable(true);
        }
    }

    private void unmark(final Region node) {
        node.getStyleClass().remove("chosen");
        if (node instanceof CardNode cn) {
            cn.setSelectable(false);
        }
    }

    private void toggle(final T option, final Region node) {
        if (selected.containsKey(node)) {
            selected.remove(node);
        } else {
            // Cuando solo se puede elegir uno, elegir otro sustituye al anterior.
            if (max == 1) {
                selected.clear();
                for (final javafx.scene.Node n : lookupAll(".chosen")) {
                    n.getStyleClass().remove("chosen");
                    if (n instanceof CardNode cn) {
                        cn.setSelectable(false);
                    }
                }
            }
            if (selected.size() < max) {
                selected.put(node, option);
            }
        }
        final boolean on = selected.containsKey(node);
        node.getStyleClass().remove("chosen");
        if (on) {
            node.getStyleClass().add("chosen");
        }
        if (node instanceof CardNode cn) {
            cn.setSelectable(on);
        }
        updateState();
    }

    /**
     * El tamanyo, medido contra la ventana de verdad.
     *
     * <p>Antes se adivinaba en el constructor con dos formulas fijas, y una de
     * ellas daba <b>66 pixeles</b> para tres opciones: 46 por fila suponiendo
     * cuatro por fila y una linea cada una. Eso vale para "elige un color" y no
     * vale para lo que de verdad llega por aqui — <i>ordenar disparos
     * simultaneos</i>, donde cada opcion es el texto entero del disparo con su
     * origen y sus objetivos. Sintoma reportado jugando: un recuadro de dos
     * dedos de alto, con barra horizontal, en el que no se lee ni una opcion
     * completa y hay que elegir el orden en el que se resuelven.
     *
     * <p>Se mide aqui y no en el constructor porque en el constructor no hay
     * escena todavia y {@code getScene()} vale null: es la misma trampa que ya
     * documenta las notas de diseño. Y se le pide {@code prefViewportHeight}, porque a un
     * {@code ScrollPane} el {@code maxHeight} no le hace crecer.
     *
     * <p>Reglas: el ancho es casi toda la ventana (con un techo para que en un
     * monitor ancho no salga una linea de texto de punta a punta), y el alto es
     * lo que pida el contenido hasta un tope de tres cuartos de ventana. O sea
     * que si cabe entero no hay barra, y si no cabe, se ve lo maximo posible.
     */
    @Override
    protected void layoutChildren() {
        final javafx.scene.Scene scene = getScene();
        if (!sized && scene != null && scene.getWidth() > 0 && scene.getHeight() > 0) {
            sized = true;
            // Los topes son de 1080p y crecen con la letra (UiScale.px).
            final double wrap = Math.max(UiScale.px(560),
                    Math.min(UiScale.px(1280), scene.getWidth() * 0.78));
            items.setPrefWrapLength(wrap);
            heading.setMaxWidth(wrap);
            // Dos por fila cuando son parrafos: mas estrecho no se lee, y a una
            // sola columna un dialogo de seis opciones no cabe en la ventana.
            if (!anyCard) {
                final double each = (wrap - 30) / 2;
                // nodesInOrder y no items: con el orden a la vista cada opcion
                // va dentro de su envoltorio con la pastilla, y el ancho lo
                // tiene que recibir el boton, no la caja.
                for (final Region r : nodesInOrder) {
                    r.setMaxWidth(each);
                    r.setPrefWidth(each);
                }
                optionWidth = each;
                if (searchMode) {
                    for (final javafx.scene.Node n : items.getChildren()) {
                        ((Region) n).setMaxWidth(each);
                        ((Region) n).setPrefWidth(each);
                    }
                }
            }
            // Lo que va encima de las opciones sale de ese mismo alto: sin
            // restarlo, con la fila de contexto puesta el dialogo se salia de
            // la ventana por abajo y con el el boton de aceptar.
            final double above = context == null ? 0 : context.prefHeight(wrap) + getSpacing();
            final double room = Math.max(220, scene.getHeight() * 0.74 - above);
            final double needed = items.prefHeight(wrap) + 16;
            scroll.setPrefViewportHeight(Math.min(room, needed));
        }
        super.layoutChildren();
    }

    /**
     * Ofrece "usar siempre este orden en esta partida".
     *
     * <p>Solo cuando el motor lo ofrece ({@code rememberOption} de
     * {@code IGuiGame.order}, que hoy es ordenar disparos simultaneos): la
     * memoria es suya ({@code PlayerControllerHuman.orderedSALookup}) y dura la
     * partida. Marcada de fabrica, como en Forge. Se olvida desde el menu de
     * Escape.
     */
    public void offerRemember(final boolean initial) {
        remember.setSelected(initial);
        remember.setVisible(true);
        remember.setManaged(true);
    }

    /** Si el jugador quiere que este orden se aplique solo la proxima vez. */
    public boolean remember() {
        return remember.isVisible() && remember.isSelected();
    }

    /** Lo que va entre el titulo y las opciones. Ver {@link #setContext}. */
    private Region context;

    /**
     * Algo que hay que ver ANTES de elegir, entre el titulo y las opciones.
     *
     * <p>Hoy lo usa el modo de un disparo para ensenyar de que carta habla
     * ({@code forge.neo.match.TriggerSubject}): con tres disparos iguales, el
     * titulo y los modos son identicos y lo unico que cambia es esto. Va fuera
     * del visor con barra a proposito: si hubiera que desplazarse para verlo,
     * se elegiria sin verlo.
     */
    public void setContext(final Region content) {
        if (context != null) {
            getChildren().remove(context);
        }
        context = content;
        if (content != null) {
            getChildren().add(getChildren().indexOf(heading) + 1, content);
        }
        sized = false;
        requestLayout();
    }

    private final boolean ordered;
    /** La pastilla de cada opcion, solo con {@link #ordered}. */
    private final java.util.Map<Region, Label> badges = new java.util.HashMap<>();

    /**
     * La opcion dentro de una caja con su pastilla de orden en la esquina.
     *
     * <p>La pastilla no coge el raton: el click tiene que seguir llegando a la
     * carta o al boton de debajo.
     */
    private Region withBadge(final Region node) {
        final Label badge = new Label();
        badge.getStyleClass().add("choice-order");
        badge.setMouseTransparent(true);
        badge.setVisible(false);
        badges.put(node, badge);
        final javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(node, badge);
        javafx.scene.layout.StackPane.setAlignment(badge, Pos.TOP_LEFT);
        javafx.scene.layout.StackPane.setMargin(badge, new Insets(-6, 0, 0, -6));
        // La carta ya se pone delante al pasar el raton, pero solo entre sus
        // hermanas: aqui su hermana es la pastilla, y la caja de al lado la
        // seguia tapando al crecer. Se sube la caja entera.
        node.hoverProperty().addListener((o, was, is) -> box.setViewOrder(is ? -1 : 0));
        return box;
    }

    /** Numera las marcadas en el orden en que se clicaron: el que se devuelve. */
    private void renumber() {
        if (!ordered) {
            return;
        }
        for (final Label badge : badges.values()) {
            badge.setVisible(false);
        }
        int i = 1;
        for (final Region node : selected.keySet()) {
            final Label badge = badges.get(node);
            if (badge != null) {
                badge.setText(String.valueOf(i));
                badge.setVisible(true);
            }
            i++;
        }
    }

    /** Pinta las primeras {@link #SHOWN} opciones que contienen lo escrito. */
    private void refilter() {
        final String q = fold(search.getText() == null ? "" : search.getText().trim());
        final List<javafx.scene.Node> shown = new ArrayList<>();
        int matches = 0;
        // Dos pasadas: primero lo que EMPIEZA por lo escrito ("light" ->
        // Lightning Bolt), despues lo que solo lo contiene. Cada una en el orden
        // del motor, que es alfabetico.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < searchKeys.length; i++) {
                final boolean hit = q.isEmpty() ? pass == 0
                        : pass == 0 ? searchKeys[i].startsWith(q)
                        : !searchKeys[i].startsWith(q) && searchKeys[i].contains(q);
                if (hit) {
                    if (shown.size() < SHOWN) {
                        shown.add(searchNode(i));
                    }
                    matches++;
                }
            }
        }
        items.getChildren().setAll(shown);
        searchNote.setText(matches == 0 ? NeoText.get("choice.search.none")
                : matches > shown.size() ? NeoText.get("choice.search.more", shown.size(), matches)
                : "");
        searchNote.setVisible(!searchNote.getText().isEmpty());
        searchNote.setManaged(searchNote.isVisible());
        scroll.setVvalue(0);
    }

    /** El boton de la opcion {@code i}, creado la primera vez que hace falta. */
    private Region searchNode(final int i) {
        Region node = nodeCache[i];
        if (node == null) {
            node = optionNode(allOptions.get(i), searchDisplay, searchCardWidth, items);
            if (optionWidth > 0) {
                node.setMaxWidth(optionWidth);
                node.setPrefWidth(optionWidth);
            }
            nodeCache[i] = node;
        }
        return node;
    }

    private int indexOfNode(final Region node) {
        if (node == null) {
            return -1;
        }
        for (int i = 0; i < nodeCache.length; i++) {
            if (nodeCache[i] == node) {
                return i;
            }
        }
        return -1;
    }

    /** Minusculas y sin tildes: "aether" encuentra "Æther" y "lim-dul" "Lim-Dûl". */
    private static String fold(final String s) {
        final String n = java.text.Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT),
                java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.replace("æ", "ae");
    }

    private void updateState() {
        renumber();
        if (readOnly) {
            accept.setDisable(false);
            accept.setText(NeoText.get("banner.understood"));
            return;
        }
        final int n = selected.size();
        if (min == max) {
            counter.setText(NeoText.get("choice.of", n, min));
        } else {
            counter.setText(NeoText.get("choice.range", n, min, max));
        }
        accept.setDisable(n < min);
        accept.setText(n < min ? NeoText.get("choice.more", min - n) : NeoText.get("common.accept"));
    }
}
