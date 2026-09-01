package forge.neo.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.common.collect.Multiset;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.card.CounterType;
import forge.game.player.PlayerView;
import forge.game.zone.ZoneType;
import forge.neo.NeoText;
import forge.neo.card.CardNode;
import forge.neo.card.CardText;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Ampliar una carta con el click derecho, en cualquier pantalla.
 *
 * <p>En la mesa esto ya existia y es el gesto que todo el mundo acaba usando:
 * la carta a tamanyo de lectura, en el centro, y se cierra a la minima. Fuera
 * de la mesa no estaba, y ahi las cartas son mucho mas pequenyas — el rival de
 * un duelo, lo que sale de un sobre — asi que hace todavia mas falta.
 *
 * <p>La instalacion es de <b>una linea por pantalla</b> y no hay que enganchar
 * carta por carta: se pone un filtro de raton en la pantalla entera y, al
 * clicar con el derecho, se busca hacia arriba el {@link CardNode} que haya
 * bajo el raton. Asi las cartas que aparecen despues (un sobre que se abre, una
 * lista que se repinta) quedan cubiertas sin acordarse de nada.
 *
 * <p>La capa grande se monta cambiando la <b>raiz de la escena</b> por un
 * {@link Layer} que lleva dentro la pantalla de antes. Es el mismo apanyo que
 * usan los dialogos de {@code NeoApp}, y tiene dos ventajas: la pantalla de
 * debajo no se entera de nada, y la carta puede ocupar toda la ventana aunque
 * la pantalla sea un {@code BorderPane} sin capas.
 */
public final class CardZoom {

    private CardZoom() {
    }

    /**
     * Deja toda la pantalla lista para el click derecho.
     *
     * @param screen la pantalla entera, no una carta suelta
     */
    public static void install(final Region screen) {
        if (screen == null) {
            return;
        }
        screen.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.SECONDARY) {
                return;
            }
            final CardNode node = cardNodeAt(e.getTarget());
            if (node != null && node.getCard() != null) {
                show(node, node.getCard());
                e.consume();
            }
        });
    }

    /** La carta grande, ya. */
    public static void show(final Node anchor, final CardView card) {
        if (anchor == null || card == null) {
            return;
        }
        final Scene scene = anchor.getScene();
        if (scene == null) {
            return;
        }
        final Parent root = scene.getRoot();
        final Layer layer = root instanceof Layer ? (Layer) root : new Layer(scene, root);
        layer.show(card, scene.getHeight(), scene.getWidth());
    }

    /** La carta a tamanyo de lectura: el alto de la ventana manda, no un fijo. */
    private static double widthFor(final double sceneHeight) {
        return Math.max(320, sceneHeight * 0.86) / CardNode.ASPECT;
    }

    /** Cuanto mide la ficha de estado respecto a la carta ampliada. */
    private static final double SIDE_RATIO = 0.6;

    /**
     * La carta ampliada y, si hay algo que contar, su ficha de estado al lado.
     *
     * <p>Es publico porque la MESA amplia por su cuenta ({@code TableScreen}
     * tiene su propia capa) y es justo donde mas falta hace: es la unica
     * pantalla donde una carta tiene estado. Si esto viviera solo aqui, el
     * arreglo se veria en todas partes MENOS donde se reporto el fallo — que es
     * exactamente lo que hay que evitar cuando algo tiene que valer para todos
     * los modos.
     *
     * @param bigWidth    ancho que tendria la carta sin ficha al lado
     * @param sceneWidth  ancho de la ventana, para que quepan las dos
     * @param onCardClick que hacer al clicar la carta grande (puede ser null)
     */
    public static Region compose(final CardView card, final double bigWidth,
                                 final double sceneWidth,
                                 final Runnable onCardClick) {
        // La ficha ocupa a la derecha, asi que la carta encoge para que quepan
        // las dos. Sin estado que contar no hay ficha, y entonces la carta
        // ampliada se queda exactamente como estaba.
        // `related` va FUERA de hasState a proposito: hasState exige estar en
        // el campo de batalla, y "la tiene exiliada" se mira justamente
        // desde el EXILIO. Dentro no se habria visto nunca.
        final boolean side = hasState(card) || otherFace(card) != null
                || related(card) != null || canReadLiveText(card);
        double w = bigWidth;
        if (side) {
            w = Math.min(w, sceneWidth * 0.9 / (1 + SIDE_RATIO));
        }
        final CardNode big = new CardNode(w);
        // Derecha SIEMPRE y sin pastillas: esta vista es para leerla.
        big.setRotationEnabled(false);
        big.setHoverEnabled(false);
        big.setBadgesVisible(false);
        if (side) {
            // Los contadores van enumerados en la ficha, con su nombre entero:
            // la pastilla encima del titulo aqui solo tapa.
            big.setCountersVisible(false);
        }
        big.setCard(card);
        if (onCardClick != null) {
            big.setOnMouseClicked(e -> {
                e.consume();
                onCardClick.run();
            });
        }
        if (!side) {
            return new StackPane(big);
        }
        final HBox row = new HBox(w * 0.06, big, statePanel(card, w * SIDE_RATIO));
        row.setAlignment(Pos.CENTER);
        return new StackPane(row);
    }

    /**
     * Si esta carta tiene algo que contar que la imagen NO cuenta.
     *
     * <p>La imagen ensenya lo IMPRESO y nada mas. En la mesa una criatura puede
     * llevar contadores, danyo marcado y auras encima, y entonces lo impreso es
     * justo lo que no hay que creerse: caso reportado jugando, una criatura que
     * ponia 0/7 en la carta, 44/46 en la mesa, y ni un sitio donde ver de donde
     * salia la diferencia. Fuera de la mesa — el catalogo, un sobre — no hay
     * estado que contar y la carta ampliada se queda como estaba.
     */
    private static boolean hasState(final CardView card) {
        if (card == null || card.getZone() != ZoneType.Battlefield) {
            return false;
        }
        final CardStateView st = card.getCurrentState();
        return st != null
                && (st.isCreature()
                    || card.getDamage() > 0
                    || (card.getCounters() != null && !card.getCounters().isEmpty())
                    || (card.getAttachedCards() != null && !card.getAttachedCards().isEmpty())
                    || !heldBy(card).isEmpty());
    }

    /** Lo que de verdad es esta carta ahora mismo, al lado de la ampliada. */
    private static Region statePanel(final CardView card, final double width) {
        final VBox box = new VBox(10);
        box.getStyleClass().add("zoom-state");
        box.setPrefWidth(width);
        box.setMinWidth(width);
        box.setMaxWidth(width);
        box.setAlignment(Pos.TOP_LEFT);
        // Que mida lo que ocupe su contenido y no lo que mida la carta: desde
        // que el panel sale para CUALQUIER permanente en juego (por el boton
        // de "ver el texto actual"), lo normal es que lleve dos lineas, y un
        // panel del alto de la carta con dos lineas dentro es un agujero.
        box.setMaxHeight(Region.USE_PREF_SIZE);

        final CardStateView st = card.getCurrentState();
        if (st != null && st.isCreature()) {
            box.getChildren().add(ptBlock(card, st));
        }

        final Region counters = countersBlock(card);
        if (counters != null) {
            box.getChildren().add(counters);
        }

        final int dmg = card.getDamage();
        if (dmg > 0) {
            final Label l = new Label(NeoText.get("zoom.damage", String.valueOf(dmg)));
            l.getStyleClass().addAll("zoom-line", "zoom-damage");
            box.getChildren().add(l);
        }

        final Region attached = attachedBlock(card, width);
        if (attached != null) {
            box.getChildren().add(attached);
        }

        final Region other = otherFaceBlock(card, width);
        if (other != null) {
            box.getChildren().add(other);
        }

        final Region held = heldBlock(card, width);
        if (held != null) {
            box.getChildren().add(held);
        }

        final Region rel = relatedBlock(card, width);
        if (rel != null) {
            box.getChildren().add(rel);
        }

        final Region live = liveTextBlock(card, width);
        if (live != null) {
            box.getChildren().add(live);
        }
        return box;
    }

    /** Solo en el campo de batalla: es donde el texto puede haber cambiado. */
    private static boolean canReadLiveText(final CardView card) {
        return card != null && card.getZone() == ZoneType.Battlefield
                && !CardText.liveRulesOf(card.getCurrentState()).isEmpty();
    }

    /**
     * "Ver el texto actual": lo que el motor dice que la carta hace AHORA.
     *
     * <p>Va <b>plegado y a peticion</b>, y las dos cosas importan:
     *
     * <ul>
     *   <li><b>Plegado</b> porque en una carta normal es lo mismo que ya pone
     *       la imagen — ensenyarlo siempre seria el texto repetido en todas las
     *       cartas de la mesa, y ademas en ingles.</li>
     *   <li><b>A peticion</b> porque no se puede saber si ha cambiado: el motor
     *       no lo publica, y adivinarlo daba falsos positivos en media mesa
     *       (ver {@code CardText.liveRulesOf}). Un boton no afirma nada; un
     *       aviso automatico si, y se equivocaria.</li>
     * </ul>
     *
     * <p>Es la unica forma de leer una criatura a la que le han
     * <b>intercambiado el cuadro de texto</b> (<i>Deadpool, Trading Card</i>) o
     * a la que se lo han <b>quitado entero</b> (<i>Song of the Dryads</i> sobre
     * tu comandante): el arte sigue siendo el impreso y no va a cambiar.
     */
    private static Region liveTextBlock(final CardView card, final double width) {
        final String live = CardText.liveRulesOf(card.getCurrentState());
        if (live.isEmpty() || card.getZone() != ZoneType.Battlefield) {
            return null;
        }

        final Label body = new Label(live);
        body.getStyleClass().addAll("zoom-line", "zoom-live-text");
        body.setWrapText(true);
        body.setMaxWidth(width);

        // Un texto vivo puede ser largo (una criatura con tres auras encima):
        // se le pone techo y se desplaza, en vez de estirar el panel fuera de
        // la ventana. El techo es mas alto de lo que parece falta a primera
        // vista a proposito: es texto robado de OTRA carta (Deadpool, Trading
        // Card) y compite en legibilidad con la carta grande de al lado.
        final javafx.scene.control.ScrollPane scroll =
                new javafx.scene.control.ScrollPane(body);
        scroll.getStyleClass().add("zoom-live-scroll");
        scroll.setFitToWidth(true);
        // Un alto PREFERIDO, no solo un tope: sin esto un ScrollPane vacio de
        // instrucciones se queda con su alto por defecto (unas tres lineas) y
        // el "mas grande" de setMaxHeight no se nota hasta que el texto es
        // largo de verdad.
        scroll.setPrefHeight(210);
        scroll.setMaxHeight(340);
        scroll.setVisible(false);
        scroll.setManaged(false);

        final javafx.scene.control.Button toggle =
                new javafx.scene.control.Button(NeoText.get("zoom.liveTextShow"));
        toggle.getStyleClass().add("zoom-live-toggle");
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setOnAction(e -> {
            final boolean show = !scroll.isVisible();
            scroll.setVisible(show);
            scroll.setManaged(show);
            toggle.setText(NeoText.get(show ? "zoom.liveTextHide" : "zoom.liveTextShow"));
            e.consume();
        });

        final VBox box = new VBox(6, toggle, scroll);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * La carta con la que esta atada, y por que.
     *
     * <p>Tres relaciones que el motor publica desde siempre y que no se
     * veian en ninguna parte:
     *
     * <ul>
     *   <li><b>Emparejada</b> ({@code getPairedWith}) — almas gemelas. Las
     *       dos criaturas se dan algo mientras sigan emparejadas, y cual es
     *       la otra no estaba escrito por ningun sitio.</li>
     *   <li><b>Es una copia de</b> ({@code getCloneOrigin}) — un clon
     *       ensenya la carta copiada, asi que sin esto no hay forma de
     *       saber cual de las dos criaturas iguales es la de verdad.</li>
     *   <li><b>La tiene exiliada</b> ({@code getExiledWith}) — el inverso
     *       de "se lleva exiliado": estas mirando la carta desterrada y lo
     *       que quieres saber es que hay que matar para recuperarla.</li>
     * </ul>
     *
     * @return la clave del texto y la carta, o null si no hay relacion
     */
    private static Object[] related(final CardView card) {
        if (card == null) {
            return null;
        }
        try {
            if (card.getPairedWith() != null) {
                return new Object[] {"zoom.pairedWith", card.getPairedWith()};
            }
            if (card.getCloneOrigin() != null) {
                return new Object[] {"zoom.cloneOf", card.getCloneOrigin()};
            }
            if (card.getExiledWith() != null) {
                return new Object[] {"zoom.exiledWith", card.getExiledWith()};
            }
        } catch (final RuntimeException e) {
            return null;
        }
        return null;
    }

    private static Region relatedBlock(final CardView card, final double width) {
        final Object[] rel = related(card);
        if (rel == null) {
            return null;
        }
        final CardView other = (CardView) rel[1];
        final Label title = new Label(NeoText.get((String) rel[0]));
        title.getStyleClass().add("zoom-head");

        final CardNode node = new CardNode(width * 0.66);
        node.setRotationEnabled(false);
        node.setHoverEnabled(false);
        node.setCard(other);

        final Label name = new Label(other.getCurrentState() == null
                ? other.getName() : CardText.nameOf(other.getCurrentState()));
        name.getStyleClass().addAll("zoom-line", "zoom-dim");
        name.setWrapText(true);
        name.setMaxWidth(width);

        final VBox box = new VBox(6, title, node, name);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * Lo que esta carta se llevo, y devolvera si se va.
     *
     * <p>Es media Commander: {@code Fiend Hunter}, {@code Grasp of Fate},
     * {@code Leyline Binding}... exilian algo "hasta que esto salga del campo".
     * El motor lleva la cuenta ({@code getUntilLeavesBattlefield}, y
     * {@code getExiledCards} para lo exiliado sin mas) y nosotros no la
     * ensenyabamos: veias una carta en el exilio y no habia forma de saber
     * quien la retenia, ni si te compensaba matar tu propio encantamiento.
     */
    private static java.util.List<CardView> heldBy(final CardView card) {
        final java.util.List<CardView> out = new java.util.ArrayList<>();
        if (card == null) {
            return out;
        }
        try {
            for (final CardView cv : card.getUntilLeavesBattlefield()) {
                out.add(cv);
            }
            for (final CardView cv : card.getExiledCards()) {
                if (!out.contains(cv)) {
                    out.add(cv);
                }
            }
        } catch (final RuntimeException e) {
            return out;
        }
        return out;
    }

    private static Region heldBlock(final CardView card, final double width) {
        final java.util.List<CardView> held = heldBy(card);
        if (held.isEmpty()) {
            return null;
        }
        final VBox box = new VBox(6);
        final Label title = new Label(NeoText.get("zoom.holding"));
        title.getStyleClass().add("zoom-head");
        box.getChildren().add(title);

        final FlowPane flow = new FlowPane(8, 8);
        final double w = held.size() > 2 ? width * 0.46 : width * 0.66;
        for (final CardView cv : held) {
            final CardNode n = new CardNode(w);
            n.setRotationEnabled(false);
            n.setHoverEnabled(false);
            n.setCard(cv);
            final Label name = new Label(cv.getCurrentState() == null
                    ? cv.getName() : CardText.nameOf(cv.getCurrentState()));
            name.getStyleClass().addAll("zoom-line", "zoom-dim");
            name.setWrapText(true);
            name.setMaxWidth(w);
            flow.getChildren().add(new VBox(3, n, name));
        }
        box.getChildren().add(flow);
        return box;
    }

    /**
     * La OTRA cara, si la carta tiene dos.
     *
     * <p>Una carta que se transforma, una de dos caras modal, una partida: el
     * motor publica las dos caras ({@code getAlternateState},
     * {@code getLeftSplitState} / {@code getRightSplitState}) y nosotros solo
     * ensenyabamos la de arriba. Para decidir si lanzar una MDFC por la cara de
     * tierra hay que <b>ver</b> la cara de tierra, y no habia forma.
     *
     * @return null si la carta tiene una sola cara
     */
    /**
     * Quien somos "nosotros" en la partida abierta ahora mismo.
     *
     * <p>Existe por UNA cosa: {@code Card.getAlternateState()} devuelve la cara
     * de VERDAD de una carta boca abajo (morfo, manifestado) sin mirar quien
     * pregunta — es asi para que TU propio morfo se pueda leer con el click
     * derecho, pero sin este guardia el mismo camino ensenyaria el morfo DEL
     * RIVAL, que es justo la informacion que el juego esconde. La comprobacion
     * de verdad la hace el motor ({@code CardView.canFaceDownBeShownToAny},
     * la MISMA que usa {@code AbstractGuiGame} para decidir que arte mandar);
     * aqui solo hace falta pasarle quien mira. Se pone al abrir cada partida
     * ({@code NeoMatchUI.openView}, principio 8 de las notas de diseño) y por eso
     * vale para las cuatro formas de arrancar una: menu, aventura, partida
     * privada y los caminos de prueba.
     *
     * <p>En observador ({@code Mode.OBSERVE}, sin asiento propio) queda
     * vacio, y {@code canFaceDownBeShownToAny(vacio)} devuelve {@code true}
     * (mismo criterio del motor): mirar sin jugar SI ensenya los boca abajo,
     * que es justo lo que hace falta para verificar con capturas.
     */
    private static volatile Iterable<PlayerView> localViewers = java.util.Collections.emptyList();

    public static void setLocalViewers(final Iterable<PlayerView> viewers) {
        localViewers = viewers == null ? java.util.Collections.emptyList() : viewers;
    }

    private static CardStateView otherFace(final CardView card) {
        if (card == null) {
            return null;
        }
        // Boca abajo y NO nuestra: el motor no protege esto por su cuenta
        // (ver el porque en el javadoc de localViewers), asi que el guardia
        // va aqui.
        if (card.isFaceDown() && !card.canFaceDownBeShownToAny(localViewers)) {
            return null;
        }
        try {
            final CardStateView current = card.getCurrentState();
            // En una carta partida las dos mitades son "la otra": se ensenya la
            // que no se este viendo ya.
            if (card.isSplitCard() && card.hasRightSplitState()) {
                final CardStateView right = card.getRightSplitState();
                return right != null && right != current ? right : card.getLeftSplitState();
            }
            final CardStateView alt = card.getAlternateState();
            return alt != null && alt != current ? alt : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static Region otherFaceBlock(final CardView card, final double width) {
        final CardStateView face = otherFace(card);
        if (face == null) {
            return null;
        }
        // Una criatura que SE PREPARA tiene su propio bloque: su hechizo no es
        // "la otra cara", y ademas ya se lee entero en la carta grande.
        if (isPreparedCard(card)) {
            return preparedBlock(card, width);
        }
        final VBox box = new VBox(6);
        final Label title = new Label(NeoText.get("zoom.otherFace"));
        title.getStyleClass().add("zoom-head");

        final double w = width * 0.72;
        final CardNode node = new CardNode(w);
        node.setRotationEnabled(false);
        node.setHoverEnabled(false);
        node.setBadgesVisible(false);
        node.setCountersVisible(false);
        node.setFace(card, face);

        final Label name = new Label(CardText.nameOf(face));
        name.getStyleClass().addAll("zoom-line", "zoom-dim");
        name.setWrapText(true);
        name.setMaxWidth(width);

        box.getChildren().addAll(title, node, name);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * El hechizo de una criatura que <b>se prepara</b>, y como lanzarlo.
     *
     * <p><b>Sin repetir la carta.</b> Estas 54 se imprimen con el hechizo en un
     * recuadro al lado del texto, o sea que la carta grande de la izquierda ya
     * lo ensenya entero y legible: poner al lado una miniatura de la MISMA
     * carta no anyade nada y encima obliga a leerla dos veces.
     *
     * <p>Y sin el nombre suelto, que ademas saldria <b>en ingles</b>: Forge
     * traduce los nombres de carta desde {@code res/languages/cardnames-*.txt}
     * y ahi solo esta la criatura, no su cara de hechizo. En la imagen de la
     * carta si sale traducido, porque es la impresion de Scryfall en tu idioma
     * — asi que un rotulo en ingles justo debajo de esa imagen se lee como un
     * fallo nuestro.
     *
     * <p>Lo que si hace falta es lo unico que no esta impreso: si esta
     * preparada <b>ahora</b> y que hay que hacer para lanzarlo.
     */
    private static Region preparedBlock(final CardView card, final double width) {
        final VBox box = new VBox(6);
        final Label title = new Label(NeoText.get("zoom.preparedSpell"));
        title.getStyleClass().add("zoom-head");

        final boolean ready = card.getPreparedSpell() != null;
        final Label how = new Label(NeoText.get(
                ready ? "zoom.preparedNow" : "zoom.preparedNot"));
        how.getStyleClass().addAll("zoom-line", ready ? "zoom-ready" : "zoom-dim");
        how.setWrapText(true);
        how.setMaxWidth(width);
        how.setMinHeight(Region.USE_PREF_SIZE);

        box.getChildren().addAll(title, how);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    /**
     * Si esta carta lleva un hechizo dentro que se lanza <b>preparandose</b>.
     *
     * <p>Estructural a proposito: {@code hasPreparedSpell()} mira si la carta
     * TIENE cara de hechizo, no si esta preparada ahora. Es lo que hace falta
     * para rotular: en la mano o en el catalogo no hay nada preparado y la cara
     * de al lado sigue sin ser "la otra cara".
     */
    private static boolean isPreparedCard(final CardView card) {
        try {
            return card != null && card.hasPreparedSpell();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** La P/T de ahora, y debajo la impresa cuando no coinciden. */
    private static Region ptBlock(final CardView card, final CardStateView st) {
        final VBox box = new VBox(2);
        final int pow = st.getPower();
        final int tou = st.getToughness();
        final Label now = new Label(pow + "/" + tou);
        now.getStyleClass().add("zoom-pt");

        final int[] printed = CardNode.printedPowerToughness(st);
        final boolean up = printed != null && (pow > printed[0] || tou > printed[1]);
        final boolean down = printed != null && (pow < printed[0] || tou < printed[1]);
        now.pseudoClassStateChanged(BUFFED, up && !down);
        now.pseudoClassStateChanged(WEAKENED, down);
        box.getChildren().add(now);

        if (printed != null && (printed[0] != pow || printed[1] != tou)) {
            final Label was = new Label(NeoText.get("zoom.printed",
                    printed[0] + "/" + printed[1]));
            was.getStyleClass().addAll("zoom-line", "zoom-dim");
            box.getChildren().add(was);
        }
        if (card.getDamage() > 0 && card.getLethalDamage() <= 0) {
            final Label lethal = new Label(NeoText.get("zoom.lethal"));
            lethal.getStyleClass().addAll("zoom-line", "zoom-damage");
            box.getChildren().add(lethal);
        }
        return box;
    }

    private static final javafx.css.PseudoClass BUFFED =
            javafx.css.PseudoClass.getPseudoClass("buffed");
    private static final javafx.css.PseudoClass WEAKENED =
            javafx.css.PseudoClass.getPseudoClass("weakened");

    /**
     * Los contadores, con el color y el nombre que dice el motor.
     *
     * <p>El {@code CounterType} trae los dos, asi que no hay lista fija: un
     * contador raro sale igual de bien que un +1/+1.
     */
    private static Region countersBlock(final CardView card) {
        final Multiset<CounterType> all = card.getCounters();
        if (all == null || all.isEmpty()) {
            return null;
        }
        final FlowPane flow = new FlowPane(6, 6);
        final List<CounterType> types = new ArrayList<>(all.elementSet());
        types.sort(Comparator.comparing(CounterType::getName));
        for (final CounterType t : types) {
            final Label pill = new Label(t.getCounterOnCardDisplayName() + "  x" + all.count(t));
            pill.getStyleClass().add("zoom-counter");
            final Color bg = Color.rgb(t.getRed(), t.getGreen(), t.getBlue());
            pill.setStyle("-fx-background-color:" + web(bg) + ";"
                    + "-fx-text-fill:" + (luminance(bg) > 0.55 ? "#101418" : "white") + ";");
            flow.getChildren().add(pill);
        }
        return flow;
    }

    /**
     * Lo que lleva encima: auras, equipo, fortificaciones.
     *
     * <p>Aqui se ven a tamanyo de LEER, que es lo que en la mesa no cabe. Da
     * igual de quien sean: el motor publica lo enganchado a una carta lo
     * controle quien lo controle.
     */
    private static Region attachedBlock(final CardView card, final double width) {
        final java.util.Collection<CardView> on = card.getAttachedCards();
        if (on == null || on.isEmpty()) {
            return null;
        }
        final VBox box = new VBox(6);
        final Label title = new Label(NeoText.get("zoom.attached"));
        title.getStyleClass().add("zoom-head");
        box.getChildren().add(title);

        final FlowPane flow = new FlowPane(8, 8);
        final double w = on.size() > 2 ? width * 0.46 : width * 0.66;
        for (final CardView cv : on) {
            final CardNode n = new CardNode(w);
            n.setRotationEnabled(false);
            n.setHoverEnabled(false);
            n.setCard(cv);
            final Label name = new Label(cv.getCurrentState() == null
                    ? cv.getName() : CardText.nameOf(cv.getCurrentState()));
            name.getStyleClass().addAll("zoom-line", "zoom-dim");
            name.setWrapText(true);
            name.setMaxWidth(w);
            final VBox one = new VBox(3, n, name);
            one.setAlignment(Pos.TOP_CENTER);
            flow.getChildren().add(one);
        }
        box.getChildren().add(flow);
        return box;
    }

    private static String web(final Color c) {
        return String.format("#%02X%02X%02X", (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    private static double luminance(final Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }

    /**
     * La carta que tiene el ratón encima ahora mismo, o null si ninguna.
     *
     * <p>No hace falta llevar la cuenta del ratón a mano: {@code isHover()} ya
     * es verdad exactamente en el nodo de debajo del cursor, así que basta con
     * recorrer el árbol y preguntar. Lo usa el atajo de teclado <b>Z</b> — la
     * misma ampliación que ya da el click derecho, pero sin soltar el ratón de
     * donde está.
     */
    public static CardNode hoveredCardNode(final javafx.scene.Node root) {
        if (root == null || !root.isHover()) {
            return null;
        }
        if (root instanceof CardNode) {
            return (CardNode) root;
        }
        if (root instanceof Parent) {
            for (final Node child : ((Parent) root).getChildrenUnmodifiable()) {
                final CardNode found = hoveredCardNode(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static CardNode cardNodeAt(final Object target) {
        Node n = target instanceof Node ? (Node) target : null;
        while (n != null) {
            if (n instanceof CardNode cn) {
                return cn;
            }
            n = n.getParent();
        }
        return null;
    }

    /**
     * Si el click ha caido sobre un control de verdad (boton, scroll...) en
     * vez de sobre la carta o el hueco vacio de alrededor.
     *
     * <p>Lo usa {@code Layer} para no cerrarse a si misma cuando el click es
     * en realidad para el boton "Ver el texto actual" — o cualquier otro
     * control que {@link #statePanel} anyada en el futuro. Publico porque
     * {@code TableScreen} tiene su PROPIA capa de ampliacion (la de la mesa
     * durante una partida) con el mismo filtro de "cerrar con cualquier
     * click", y por tanto el mismo fallo.
     */
    public static boolean isInteractiveTarget(final Object target) {
        Node n = target instanceof Node ? (Node) target : null;
        while (n != null) {
            if (n instanceof javafx.scene.control.Control) {
                return true;
            }
            n = n.getParent();
        }
        return false;
    }

    /**
     * La capa: la pantalla de antes debajo y la carta encima.
     *
     * <p>Al cerrarse devuelve la raiz de la escena a como estaba. Se guarda el
     * estilo de la raiz vieja porque ahi vive la escala de interfaz
     * ({@code UiScale}): sin copiarlo, ampliar una carta cambiaria el tamanyo
     * de letra de toda la pantalla.
     */
    private static final class Layer extends StackPane {
        private final Scene scene;
        private final Parent previous;
        private final Overlay overlay = new Overlay();

        Layer(final Scene scene, final Parent previous) {
            this.scene = scene;
            this.previous = previous;
            setStyle(previous.getStyle());
            overlay.setOnBackgroundClick(this::close);
            // Un click en CUALQUIER sitio la cierra, y por eso va como filtro y
            // no como manejador de fondo: el hueco que rodea a la carta lo
            // ocupa un contenedor sin fondo, y un Region sin fondo no se puede
            // clicar — ahi el click se perdia y parecia que solo cerraba
            // clicando encima de la carta.
            //
            // EXCEPTO sobre un control de verdad. El "Ver el texto actual" de
            // Deadpool vive dentro de esta misma capa (statePanel), y un
            // filtro que consume TODO se adelanta a su propio boton: nunca
            // llegaba a desplegarse, y cada click lo que hacia era cerrar la
            // ampliacion entera. Reportado jugando: "el boton de leer el
            // texto actual cierra la ventana en vez de ensenyarme el texto
            // robado".
            overlay.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
                if (isInteractiveTarget(e.getTarget())) {
                    return;
                }
                e.consume();
                close();
            });
            getChildren().addAll(previous, overlay);
            scene.setRoot(this);
        }

        void show(final CardView card, final double sceneHeight, final double sceneWidth) {
            overlay.show(compose(card, widthFor(sceneHeight), sceneWidth, null));
            // Escape cierra, igual que en la mesa. Se quita antes de poner
            // para no acumular uno por ampliacion, y al cerrar del todo.
            scene.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, escape);
            scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, escape);
        }

        private final javafx.event.EventHandler<javafx.scene.input.KeyEvent> escape = e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                e.consume();
                close();
            }
        };

        private void close() {
            scene.removeEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, escape);
            overlay.hide();
            getChildren().remove(previous);
            scene.setRoot(previous);
        }
    }
}
