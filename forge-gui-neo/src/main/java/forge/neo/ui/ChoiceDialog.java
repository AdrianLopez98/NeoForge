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
        // min/max negativos significan "no hay nada que elegir, solo enseñar":
        // es lo que manda IGuiGame.reveal(). Sin esto sale un dialogo de
        // eleccion absurdo con el contador a "0 de 0".
        this.readOnly = min < 0 || max <= 0;
        this.min = readOnly ? 0 : min;
        this.max = readOnly ? 0 : Math.max(min, max);

        getStyleClass().add("dialog");
        setSpacing(12);
        setPadding(new Insets(18));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        heading = new Label(title == null ? NeoText.get("choice.title") : title);
        heading.getStyleClass().add("dialog-title");
        heading.setWrapText(true);
        heading.setMaxWidth(760);

        items = new FlowPane(10, 10);
        items.setAlignment(Pos.CENTER);
        items.setPrefWrapLength(760);

        for (final T option : options) {
            final Region node = optionNode(option, display, cardWidth, items);
            if (readOnly) {
                node.setOnMouseClicked(null);
                node.setDisable(true);
                node.setOpacity(0.95);
            }
            items.getChildren().add(node);
            nodesInOrder.add(node);
            optionsInOrder.add(option);
        }

        scroll = new ScrollPane(items);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        // Nunca barra horizontal: el texto ENVUELVE. La que salia se comia un
        // renglon de los pocos que habia y encima dejaba media opcion fuera de
        // la vista, que es lo peor de los dos mundos.
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        boolean card = false;
        for (final T option : options) {
            if (option instanceof CardView || option instanceof forge.item.PaperCard) {
                card = true;
                break;
            }
        }
        anyCard = card;
        // El alto de verdad se pone en layoutChildren: aqui todavia no hay
        // escena y no se sabe cuanta ventana hay. Esto es solo el arranque.
        scroll.setPrefViewportHeight(anyCard ? cardWidth * CardNode.ASPECT + 40 : 200);
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
        final boolean worthIt = !readOnly && max > 1 && options.size() > 3;
        selectAll.getStyleClass().add("btn-secondary");
        selectAll.setText(NeoText.get("choice.selectAllInOrder"));
        selectAll.setVisible(worthIt);
        selectAll.setManaged(worthIt);
        selectAll.setOnAction(e -> selectAllInOrder());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, counter, gap, selectAll, accept);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, scroll, footer);

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
                if (idx >= 0 && items.getChildren().get(idx) instanceof Region node) {
                    used.add(idx);
                    toggle(option, node);
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
            node.setOnMouseClicked(e -> toggle(option, node));
            return node;
        }
        // Y una carta en papel tambien es una carta. Llega asi desde IGuiBase
        // (las recompensas de la aventura, el premio de un sobre): sin esto
        // saldria como una linea de texto con el nombre.
        if (option instanceof forge.item.PaperCard pc) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(pc));
            node.setOnMouseClicked(e -> toggle(option, node));
            return node;
        }
        final Button b = new Button(display == null ? String.valueOf(option) : display.apply(option));
        b.getStyleClass().add("choice-item");
        b.setWrapText(true);
        b.setMaxWidth(340);
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
            final double wrap = Math.max(560, Math.min(1280, scene.getWidth() * 0.78));
            items.setPrefWrapLength(wrap);
            heading.setMaxWidth(wrap);
            // Dos por fila cuando son parrafos: mas estrecho no se lee, y a una
            // sola columna un dialogo de seis opciones no cabe en la ventana.
            if (!anyCard) {
                final double each = (wrap - 30) / 2;
                for (final javafx.scene.Node n : items.getChildren()) {
                    if (n instanceof Region r) {
                        r.setMaxWidth(each);
                        r.setPrefWidth(each);
                    }
                }
            }
            final double room = Math.max(220, scene.getHeight() * 0.74);
            final double needed = items.prefHeight(wrap) + 16;
            scroll.setPrefViewportHeight(Math.min(room, needed));
        }
        super.layoutChildren();
    }

    private void updateState() {
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
