package forge.neo.ui;

import forge.neo.card.CardText;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.neo.card.CardNode;
import javafx.scene.layout.Pane;

/**
 * Campo de batalla con el comportamiento de Arena.
 *
 * <p>Resuelve las dos cosas que hacen que una mesa llena siga siendo jugable:
 *
 * <ol>
 *   <li><b>Escalado automatico.</b> Con 4 permanentes las cartas se ven
 *       grandes; con 25 se encogen y, si hace falta, se reparten en varias
 *       filas y se solapan. La mesa NUNCA se desborda ni aparece scroll.</li>
 *   <li><b>Agrupacion de fichas.</b> Once fichas de Zombi identicas no ocupan
 *       once huecos: se apilan en uno con un contador "x11". Sin esto, un mazo
 *       de fichas llena la pantalla y no se ve nada.</li>
 * </ol>
 *
 * <p>Se agrupan FICHAS realmente identicas (mismo nombre, misma fuerza y
 * resistencia, mismo estado de giro y de combate). Y desde el 29-09-2026
 * tambien las CARTAS iguales, como Forge (ajuste {@code NeoSettings.STACK_SAME},
 * encendido de fabrica), con condiciones de mas: mismos contadores, nada
 * enganchado, ni boca abajo ni copia de otra. Lo que hace segura una pila es lo
 * mismo para las dos: se parte en combate, deja de apilar cuando el motor pide
 * senyalar cartas concretas y lo ya elegido sale de la pila.
 */
public class BattlefieldPane extends Pane {

    private static final double GAP = 10;
    private static final double ROW_GAP = 8;

    /** Cuanto puede encogerse una carta respecto al tamano base. */
    private static final double MIN_SCALE = 0.52;

    /** Solapamiento maximo cuando ni encogiendo caben. */
    private static final double MAX_OVERLAP = 0.42;

    private static final int MAX_ROWS = 3;

    /**
     * Lo mas pequenya que se dibuja una carta, pase lo que pase.
     *
     * <p>Publica porque {@link PlayerField} tiene que usar EL MISMO numero: es
     * quien decide el alto de cada fila, y si lo calcula con un ancho de carta
     * menor que este, la fila sale mas baja que las cartas que van dentro y el
     * recorte las siega por abajo — media carta, sin caja de texto ni P/T.
     * Paso de verdad con tres mesas de rival en fila: la fila de tierras se
     * dimensionaba a 13 px de carta y aqui se pintaban a 28.
     */
    public static final double MIN_CARD_WIDTH = 28;

    private double scrollX, scrollMax;
    private final javafx.scene.control.Button previous = scrollButton("‹", -1);
    private final javafx.scene.control.Button next = scrollButton("›", 1);

    private javafx.scene.control.Button scrollButton(String text, int direction) {
        var button = new javafx.scene.control.Button(text);
        button.getStyleClass().add("arena-scroll");
        UiScale.fixedFont(button, 18);
        button.setAccessibleText(direction < 0 ? "Scroll left" : "Scroll right");
        button.setManaged(false);
        button.setVisible(false);
        button.setViewOrder(-100);
        button.setOnAction(e -> {
            scrollX = Math.max(0, Math.min(scrollMax, scrollX + direction * getWidth() * .55));
            requestLayout();
        });
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, e -> e.consume());
        return button;
    }

    private final double baseCardWidth;
    private double maxCardWidth;
    private final List<Entry> entries = new ArrayList<>();
    private Consumer<CardNode> onHover;
    private Consumer<CardNode> onClick;

    /** Hacia donde avanzan las criaturas al entrar en combate. Ver CardNode. */
    private int combatDirection;

    /** Cuanto puede salirse el contenido del recorte, arriba y abajo. */
    private double overflowTop;
    private double overflowBottom;

    private final javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();

    /** Una posicion en la mesa: una carta, o un grupo de fichas identicas. */
    private static final class Entry {
        final CardStackNode node;
        Entry(final CardStackNode node) {
            this.node = node;
        }
    }

    public BattlefieldPane(final double baseCardWidth) {
        this.baseCardWidth = baseCardWidth;
        this.maxCardWidth = baseCardWidth;
        setPickOnBounds(false);

        // Recortar al area asignada: si por lo que sea una carta se sale, se
        // corta aqui en vez de pintarse encima de la barra o de la mano.
        setClip(clip);
        updateClip();
        setOnScroll(e -> {
            if (!forge.neo.platform.NeoOs.ctrl(e) && scrollMax > .5) {
                double delta = Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY()) ? e.getDeltaX() : e.getDeltaY();
                scrollX = Math.max(0, Math.min(scrollMax, scrollX - delta));
                requestLayout();
                e.consume();
            }
        });
    }

    /**
     * Margen por el que el contenido puede salirse del recorte.
     *
     * <p>Hace falta porque las criaturas AVANZAN hacia la linea de combate al
     * atacar: con el recorte pegado a la fila, ese paso adelante se cortaba a
     * la mitad. Solo se abre hacia la linea de combate, que es donde hay hueco;
     * hacia la barra y la mano se sigue recortando.
     */
    public void setOverflow(final double top, final double bottom) {
        this.overflowTop = top;
        this.overflowBottom = bottom;
        updateClip();

    }

    /**
     * Cuanto puede crecer una carta con el hover, para dejarle sitio al
     * recorte en las cuatro direcciones.
     *
     * <p>Antes el ancho del recorte iba atado sin mas al ancho de la fila
     * (0 de margen a los lados) y el alto solo abria hacia la linea de
     * combate ({@link #setOverflow}), pensado para el paso adelante del
     * atacante. Con el ajuste nuevo de hover ({@code NeoSettings.hoverZoom},
     * hasta 150 %) una carta de tierra o de tierra o artefacto —que no
     * avanza nunca— crecia y se segaba en el propio borde de su fila: el
     * caso reportado jugando, una <i>Naya Panorama</i> a la que el hover le
     * cortaba el nombre entero. Se usa {@code maxCardWidth}, el ancho mas
     * grande que puede tener esta fila, para no tener que esperar a que
     * {@code layoutChildren} calcule el ancho real de cada pasada.
     */
    private void updateClip() {
        final double zoom = forge.neo.NeoSettings.hoverZoom();
        final double cardH = maxCardWidth * CardNode.ASPECT;
        final double liftUp = CardNode.hoverLiftFor(zoom, maxCardWidth);
        final double growH = (zoom - 1) * cardH / 2 + 6;
        final double growW = (zoom - 1) * maxCardWidth / 2 + 6;
        final double top = overflowTop + liftUp + growH;
        final double bottom = overflowBottom + growH + attachOverhang;
        clip.setX(-growW);
        clip.setY(-top);
        clip.setWidth(getWidth() + growW * 2);
        clip.setHeight(getHeight() + top + bottom);
    }

    /**
     * Cuanto asoma por debajo lo enganchado a las cartas de esta fila.
     *
     * <p>Hace falta abrir el recorte por dos motivos, y el segundo es el que
     * importa: un clip no solo recorta el DIBUJO, tambien el raton. Con la fila
     * cerrada justo al alto de la carta, un aura no solo no se veia — es que no
     * se podia clicar, asi que un equipo de la mesa no habia forma de activarlo.
     */
    private double attachOverhang;

    /** Hacia donde avanzan las criaturas de esta fila al combatir. */
    public void setCombatDirection(final int dir) {
        this.combatDirection = dir;
        for (final Entry e : entries) {
            e.node.getFront().setCombatDirection(dir);
        }
    }

    // Un Pane calcula su tamano preferido a partir de los hijos, que aqui son
    // cartas grandes: eso le hace pedir mucho mas alto del que hay y empuja
    // fuera de la ventana a la barra y a la mano. Pedimos poco y dejamos que el
    // VBox nos de el espacio sobrante con Vgrow; el layout se adapta solo.

    @Override
    protected double computeMinHeight(final double width) {
        return baseCardWidth * CardNode.ASPECT * 0.42;
    }

    @Override
    protected double computePrefHeight(final double width) {
        return computeMinHeight(width);
    }

    @Override
    protected double computeMaxHeight(final double width) {
        return Double.MAX_VALUE;
    }

    @Override
    protected double computePrefWidth(final double height) {
        return baseCardWidth * 3;
    }

    /**
     * Numero de huecos que ocupa la fila (las fichas agrupadas cuentan como uno).
     */
    public int slotCount() {
        return entries.size();
    }

    /**
     * Tamano de carta que le vendria bien a esta fila con el ancho dado, sin
     * pasarse del tamano base. Lo usa {@link PlayerField} para que todas las
     * filas acaben con cartas del MISMO tamano: si cada fila decide por su
     * cuenta, salen tres tamanos distintos y la mesa parece una piramide.
     */
    public double desiredCardWidth(final double availW) {
        final int n = entries.size();
        if (n == 0) {
            return 0;
        }
        return Math.min(baseCardWidth, Math.max(1, availW));
    }

    /** Tope de tamano impuesto desde fuera, para igualar entre filas. */
    public void setMaxCardWidth(final double w) {
        this.maxCardWidth = Math.min(baseCardWidth, Math.max(20, w));
    }

    public void setOnCardHover(final Consumer<CardNode> handler) {
        this.onHover = handler;
    }

    public void setOnCardClick(final Consumer<CardNode> handler) {
        this.onClick = handler;
    }

    /** Que hacer al clicar el "xN" de una pila de fichas: recibe todas sus cartas. */
    public void setOnStackCountClick(final Consumer<java.util.List<forge.game.card.CardView>> handler) {
        this.onStackCountClick = handler;
    }

    private Consumer<java.util.List<forge.game.card.CardView>> onStackCountClick;

    /**
     * Que hacer al clicar el contador de lo enganchado, cuando va apilado
     * ({@code NeoSettings.ATTACHMENTS_STACKED}). La mesa lo abre en el visor.
     */
    public void setOnAttachPeek(
            final java.util.function.BiConsumer<forge.game.card.CardView,
                    java.util.List<forge.game.card.CardView>> handler) {
        this.onAttachPeek = handler;
    }

    private java.util.function.BiConsumer<forge.game.card.CardView,
            java.util.List<forge.game.card.CardView>> onAttachPeek;

    /** ¿Alguna carta de esta fila lleva algo enganchado? Lo pregunta el reparto. */
    public boolean hasAttachments() {
        for (final Entry e : entries) {
            if (!e.node.getAttachments().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------

    /**
     * Cache de nodos por clave de grupo.
     *
     * <p>REGLA DE RENDIMIENTO de las notas de diseño: no reconstruir el grafo de escena en
     * cada actualizacion. El motor avisa de cambios decenas de veces por turno;
     * si en cada aviso se crean 30 CardNode nuevos, se pierden las imagenes ya
     * decodificadas, se reinician las animaciones y los fps se hunden. Aqui se
     * reutiliza el mismo nodo mientras la carta siga en la mesa.
     */
    private final Map<String, CardStackNode> nodeCache = new LinkedHashMap<>();

    public void setCards(final List<CardView> cards) {
        setCards(cards, java.util.Collections.emptyMap());
    }

    public void setCards(final List<CardView> cards,
                         final Map<Integer, List<CardView>> attachedTo) {
        lastCards = cards;
        lastAttachedTo = attachedTo;
        final Map<String, List<CardView>> groups = groupCards(cards);

        // Lo que ESTABA y ya no esta: se muere en pantalla en vez de
        // desaparecer de un fotograma al siguiente. Hay que mirarlo antes de
        // tocar nada, porque retainAll de mas abajo se lleva la prueba.
        // Que cartas hay ahora, POR ID. La clave de grupo no sirve para decidir
        // si algo ha entrado o se ha ido: una ficha que empieza a atacar deja
        // de agruparse y cambia de clave sin moverse de la mesa. Por el id, no
        // hay confusion posible.
        final java.util.Set<Integer> nowIds = idsOf(cards);
        final boolean quiet = skipRemovals;
        skipRemovals = false;

        if (!quiet) {
            for (final Map.Entry<String, CardStackNode> old : nodeCache.entrySet()) {
                if (!groups.containsKey(old.getKey()) && isGone(old.getValue(), nowIds)) {
                    startGhost(old.getValue());
                }
            }
        }

        entries.clear();
        // Lo que va a quedar dentro, en orden. Los fantasmas van al fondo: lo
        // vivo se pinta por encima.
        final List<javafx.scene.Node> shown = new ArrayList<>(ghosts);

        for (final Map.Entry<String, List<CardView>> group : groups.entrySet()) {
            final String key = group.getKey();
            final List<CardView> members = group.getValue();

            CardStackNode node = nodeCache.get(key);
            if (node == null || node.getStackSize() != members.size()) {
                // Solo se crea de cero si es nueva o si cambio el tamano de la
                // pila (una ficha mas o una menos cambia el dibujo).
                final boolean brandNew = node == null;
                node = new CardStackNode(baseCardWidth, members.get(0), members.size());
                wire(node);
                nodeCache.put(key, node);
                // El nodo nuevo nace como esta la carta (CardNode.refresh). Si
                // la vimos de otra manera, es que se acaba de girar o enderezar:
                // eso si se anima.
                final Boolean wasTapped = shownTapped.get(members.get(0).getId());
                if (wasTapped != null) {
                    node.getFront().animateTapFrom(wasTapped);
                }
                if (brandNew && !quiet && isArriving(members.get(0))) {
                    trace("entra", members.get(0));
                    // Solo lo que acaba de LLEGAR a la zona. Una pila que crece
                    // de 3 a 4 fichas tambien se reconstruye, y ahi una entrada
                    // en grande seria mentira: la pila ya estaba.
                    Anim.enter(node);
                }
            } else {
                node.getFront().setCard(members.get(0));
            }

            node.setMembers(members);
            node.setAttachmentHandlers(this::fireClick, this::fireHover);
            node.setOnAttachPeek((host, carried) -> {
                if (onAttachPeek != null) {
                    onAttachPeek.accept(host, carried);
                }
            });
            node.setAttachments(attachedTo.get(members.get(0).getId()));
            node.getFront().setCombatDirection(combatDirection);

            entries.add(new Entry(node));
            shown.add(node);
        }

        // Soltar los nodos de cartas que ya no estan en esta zona.
        nodeCache.keySet().retainAll(groups.keySet());
        shownTapped.clear();
        for (final List<CardView> members : groups.values()) {
            for (final CardView cv : members) {
                if (cv != null && cv.getId() >= 0) {
                    shownTapped.put(cv.getId(), cv.isTapped());
                }
            }
        }
        lastIds.clear();
        lastIds.addAll(nowIds);
        shown.add(previous);
        shown.add(next);
        // Y SOLO si cambia algo. Esto se llama en cada repintado de la mesa, y
        // sacar una carta de la escena, aunque vuelva en el mismo instante,
        // le quita a JavaFX el nodo que se pulso: el soltar llega a la fila y
        // el clic no existe. Con repintados seguidos ningun clic llegaba a la
        // mesa (Discord, 14-10-2026, ver NeoMatchUI.probing); lo mismo que
        // le paso a la mano el 11-10-2026 (TableScreen.setHand).
        if (!getChildren().equals(shown)) {
            getChildren().setAll(shown);
        }
        requestLayout();
    }

    /**
     * Cartas que ya no estan en la zona pero que siguen pintandose mientras se
     * desvanecen.
     *
     * <p>Viven fuera de {@code entries}, asi que {@code layoutChildren} no las
     * coloca: se quedan exactamente donde estaban, que es justo lo que hace
     * falta para que se entienda cual ha muerto.
     */
    private final List<CardStackNode> ghosts = new ArrayList<>();

    /**
     * La carta de delante de la pila que lleva esa carta DENTRO (el "xN"). La
     * mesa solo indexa la de delante, asi que el objetivo de un Bolt que es la
     * tercera de cinco fichas iguales no tendria donde destellar.
     */
    public CardNode stackHolding(final forge.game.card.CardView card) {
        if (card == null) {
            return null;
        }
        for (final Entry e : entries) {
            for (final forge.game.card.CardView m : e.node.getMembers()) {
                if (m != null && m.getId() == card.getId()) {
                    return e.node.getFront();
                }
            }
        }
        return null;
    }

    /**
     * El fantasma de esa carta, si se esta yendo ahora mismo. Para los
     * destellos ({@link ImpactLayer}): el hechizo se resuelve y su objetivo ya
     * va camino del cementerio, pero el destello tiene que salir donde estaba.
     */
    public CardNode ghostOf(final forge.game.card.CardView card) {
        if (card == null) {
            return null;
        }
        for (final CardStackNode g : ghosts) {
            final forge.game.card.CardView cv = g.getFront().getCard();
            if (cv != null && cv.getId() == card.getId()) {
                return g.getFront();
            }
        }
        return null;
    }

    /**
     * Como se pinto cada carta (por id) la vez anterior: girada o no. El
     * CardView no sirve para esto, porque el motor lo cambia por debajo y ya
     * dice como esta AHORA. Ver el alta de nodos en setCards.
     */
    private final Map<Integer, Boolean> shownTapped = new java.util.HashMap<>();

    /** Los ids de carta que habia en esta zona la vez anterior. */
    private final java.util.Set<Integer> lastIds = new java.util.HashSet<>();

    private static java.util.Set<Integer> idsOf(final List<CardView> cards) {
        final java.util.Set<Integer> out = new java.util.HashSet<>();
        for (final CardView cv : cards) {
            if (cv != null) {
                out.add(cv.getId());
            }
        }
        return out;
    }

    /** true si la carta de este nodo ya no esta en la zona (no solo su grupo). */
    private static boolean isGone(final CardStackNode node, final java.util.Set<Integer> nowIds) {
        final CardView cv = node == null ? null : node.getFront().getCard();
        if (cv == null) {
            return true;
        }
        // Las vistas de maqueta comparten un id negativo: ahi no se puede
        // comparar por id y se anima el cambio tal cual.
        return cv.getId() < 0 || !nowIds.contains(cv.getId());
    }

    /** true si esta carta NO estaba en la zona la vez anterior. */
    private boolean isArriving(final CardView cv) {
        return cv == null || cv.getId() < 0 || !lastIds.contains(cv.getId());
    }

    /** {@code -Dneo.anim.debug=true} dice que entra y que se va de cada zona. */
    private static void trace(final String what, final CardView card) {
        if (Boolean.getBoolean("neo.anim.debug")) {
            System.out.printf("[anim] %s: %s%n", what,
                    card == null || card.getCurrentState() == null
                            ? "?" : CardText.nameOf(card.getCurrentState()));
        }
    }

    private boolean skipRemovals;

    /**
     * No animar la salida en la PROXIMA actualizacion.
     *
     * <p>Hace falta porque el campo del rival es UNO solo y se reutiliza para
     * los tres oponentes de una partida a cuatro: al cambiar de pestanya se
     * sustituye la mesa entera, y sin esto parecerian morirse de golpe todos
     * los permanentes del rival anterior. No ha muerto nada; estamos mirando a
     * otro sitio.
     */
    public void skipRemovalAnimation() {
        this.skipRemovals = true;
    }

    /**
     * Nota: apaga la entrada TAMBIEN. Al cambiar de rival no ha entrado nada
     * nuevo en la mesa; estamos mirando a otro sitio.
     */

    /**
     * Donde estaba (en la escena) cada carta que se ha ido hace poco, por id.
     * Para los destellos, cuando ni el fantasma queda: el aviso de "esto te
     * acaba de pasar" para el motor a mitad de la resolucion, y el destello
     * llega cuando pulsas Entendido.
     */
    private final java.util.Map<Integer, Object[]> gone = new java.util.HashMap<>();
    private static final long GONE_NS = 30_000_000_000L;

    /** Ver {@link #gone}. En coordenadas de la escena, o null. */
    public javafx.geometry.Bounds lastSceneBoundsOf(final forge.game.card.CardView card) {
        final Object[] g = card == null ? null : gone.get(card.getId());
        return g == null || System.nanoTime() - (Long) g[1] > GONE_NS
                ? null : (javafx.geometry.Bounds) g[0];
    }

    private void rememberGone(final CardStackNode node) {
        final forge.game.card.CardView cv = node.getFront().getCard();
        if (cv == null || node.getScene() == null) {
            return;
        }
        final long now = System.nanoTime();
        gone.values().removeIf(g -> now - (Long) g[1] > GONE_NS);
        gone.put(cv.getId(), new Object[] {
                node.getFront().localToScene(node.getFront().getBoundsInLocal()), now});
    }

    private void startGhost(final CardStackNode node) {
        if (node == null || ghosts.contains(node)) {
            return;
        }
        rememberGone(node);
        ghosts.add(node);
        trace("se va", node.getFront().getCard());
        // Si va un hechizo hacia ella, espera a recibirlo (GhostHold).
        final forge.game.card.CardView cv = node.getFront().getCard();
        final long wait = cv == null ? 0 : GhostHold.remainingMs(cv.getId());
        if (wait > 0) {
            leaveLater(node, wait);
        } else {
            leaveNow(node);
        }
    }

    private void leaveNow(final CardStackNode node) {
        if (node.getProperties().remove(HOLD_KEY) != null && Boolean.getBoolean("neo.impact.log")) {
            System.out.printf("[fantasma] %d se va tras esperar%n", System.nanoTime() / 1_000_000L);
        }
        Anim.leave(node, () -> {
            ghosts.remove(node);
            getChildren().remove(node);
        });
    }

    private static final String HOLD_KEY = "neo.ghost.hold";

    private void leaveLater(final CardStackNode node, final long ms) {
        if (Boolean.getBoolean("neo.impact.log")) {
            System.out.printf("[fantasma] %d espera %d ms: %s%n", System.nanoTime() / 1_000_000L, ms,
                    node.getFront().getCard());
        }
        node.setMouseTransparent(true);
        final Object old = node.getProperties().remove(HOLD_KEY);
        if (old instanceof javafx.animation.PauseTransition p) {
            p.stop();
        }
        final javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(ms));
        wait.setOnFinished(e -> leaveNow(node));
        node.getProperties().put(HOLD_KEY, wait);
        wait.play();
    }

    /**
     * Va un hechizo hacia esa carta: si ya se estaba yendo, que espere entera a
     * recibirlo ({@link GhostHold}). El aviso del motor puede llegar despues
     * de que la mesa la haya quitado.
     */
    public void holdGhost(final forge.game.card.CardView card) {
        if (card == null) {
            return;
        }
        for (final CardStackNode g : ghosts) {
            final forge.game.card.CardView cv = g.getFront().getCard();
            if (cv != null && cv.getId() == card.getId()) {
                Anim.holdLeave(g);
                leaveLater(g, GhostHold.remainingMs(card.getId()));
                return;
            }
        }
    }

    private void wire(final CardStackNode node) {
        node.getFront().hoverProperty().addListener((o, was, is) -> {
            if (is) {
                fireHover(node.getFront());
            }
        });
        // liftedProperty y no hoverProperty: con el raton en el hueco que deja
        // la carta al subir, JavaFX ya dice que no esta encima pero sigue
        // ampliada (CardNode.leaving), y se quedaba detras de la de al lado.
        node.getFront().liftedProperty().addListener((o, was, is) -> {
            // La carta con el raton encima, DELANTE DE TODO: de sus vecinas de
            // fila (las de la derecha se pintan despues) y de las otras filas
            // (la de criaturas se pintaba encima de la de tierras). Con la
            // carta ampliada al pasar el raton se veia cortada por la de al
            // lado (itch.io, 29-09-2026). Lo recoloca el layout, que respeta
            // la que esta bajo el raton.
            node.setViewOrder(is ? -10 : 0);
            setViewOrder(is ? -1 : 0);
            // Y el campo entero, por encima del del rival (y al reves).
            if (getParent() instanceof PlayerField field) {
                field.setViewOrder(is ? -1 : 0);
            }
            requestLayout();
        });
        node.getFront().setOnMouseClicked(e -> {
            // El derecho lo usa la mesa para ampliar la carta.
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                // Sobre el "xN": la pila entera (al atacar, atacan todas).
                if (onStackCountClick != null && node.countHit(e.getSceneX(), e.getSceneY())) {
                    onStackCountClick.accept(node.getMembers());
                    return;
                }
                fireClick(node.getFront());
            }
        });
    }

    private void fireClick(final CardNode node) {
        if (onClick != null) {
            onClick.accept(node);
        }
    }

    private void fireHover(final CardNode node) {
        if (onHover != null) {
            onHover.accept(node);
        }
    }

    /**
     * Si esta ficha se puede apilar con sus gemelas.
     *
     * <p><b>Agrupar esconde individuos, y a un individuo hay que poder
     * clicarlo.</b> Una pila de fichas identicas es un solo nodo, y al clicarlo
     * siempre se elige la PRIMERA. Mientras son adorno de fondo da igual; en
     * cuanto el motor espera que senyales una en concreto, es un callejon sin
     * salida: fallo real con dos fichas atacando iguales, donde solo se podia
     * bloquear a una porque las dos eran el mismo nodo.
     *
     * <p>Por eso NO se agrupan las fichas que estan en combate. Con una
     * seleccion en curso SI, desde el 22-10-2026: la pila se parte en elegidas,
     * libres y no elegibles (ver {@link #setSelectableTest}), asi que clicar
     * cualquier pila es clicar una que vale. El resto del tiempo se apilan, que
     * es para lo que sirve: once Zombis no pueden ocupar once huecos.
     */
    private boolean canGroup(final CardView token) {
        return grouping && !token.isAttacking() && !token.isBlocking();
    }

    /**
     * Se apaga mientras el motor espera que el jugador senyale cartas concretas.
     */
    public void setGroupingEnabled(final boolean on) {
        if (this.grouping != on) {
            this.grouping = on;
            // Y se reparte YA, con las cartas de la ultima vez. Las pilas se
            // forman en setCards, no al colocar: con solo pedir layout el
            // cambio esperaba al siguiente aviso del motor, y mientras el
            // motor espera tu eleccion no manda ninguno. Reportado con
            // Strefan: sacrificar dos fichas de Sangre de una pila x3 elegia
            // la primera y la segunda no habia forma de senyalarla.
            if (lastCards != null) {
                final boolean pending = skipRemovals;
                // No ha entrado ni salido nada: solo cambia el dibujo.
                skipRemovals = true;
                setCards(lastCards, lastAttachedTo);
                skipRemovals = pending;
            } else {
                requestLayout();
            }
        }
    }

    /** Lo ultimo que se pinto, para poder repartirlo de nuevo sin el motor. */
    private List<CardView> lastCards;
    private Map<Integer, List<CardView>> lastAttachedTo = java.util.Collections.emptyMap();

    public boolean isGroupingEnabled() {
        return grouping;
    }

    private boolean grouping = true;

    /**
     * Si esta carta ya esta ELEGIDA en la seleccion que el motor esta pidiendo.
     *
     * <p>Lo pone la mesa desde {@code NeoMatchUI.refreshHighlights}, que es
     * justo cuando el motor marca o desmarca algo.
     */
    private java.util.function.Predicate<CardView> pickedTest;

    /**
     * <b>Que una ficha ya elegida deje de ser indistinguible de sus gemelas.</b>
     *
     * <p>Reportado en itch.io el 20-09-2026 con Clever Concealment: dos fichas
     * iguales apiladas, convocar pide girar dos, clicas la pila y solo entra
     * <b>una</b> — la de delante. La segunda no habia forma de elegirla,
     * porque al clicar la pila se manda siempre la primera y el motor ya la
     * tenia. El mismo caso con dos fichas de Sangre que hay que sacrificar.
     *
     * <p>La clave de agrupacion ya separaba lo que el jugador distingue
     * (girada, atacando, con danyo). "Ya elegida" es exactamente eso y
     * faltaba: en cuanto una entra, la pila <b>se parte</b> — la elegida a un
     * lado y la que queda al otro — y la segunda se clica como cualquier otra
     * carta. Que es, ademas, lo que el jugador esperaba ver.
     */
    public void setPickedTest(final java.util.function.Predicate<CardView> test) {
        this.pickedTest = test;
        regroupIfNeeded();
    }

    /**
     * Si esta carta es de las que el motor deja elegir AHORA.
     *
     * <p><b>Las pilas ya no se deshacen al elegir</b> (Discord, 22-10-2026:
     * <i>"a way to have tokens not fan out when selecting them as having a lot
     * of them make the game crash"</i>). Antes, cualquier eleccion desapilaba
     * la mesa entera: con trescientas fichas eran trescientas cartas con su
     * arte de golpe, y la partida se quedaba sin memoria. Ahora la pila se
     * queda, y para que clicarla sea siempre clicar UNA que vale se parte en
     * elegibles y no elegibles (la propia ficha que pide "sacrifica OTRA" es
     * gemela de las demas), igual que ya se partia en elegidas y libres. El
     * "xN" pregunta cuantas: {@code NeoMatchUI.pickFromStack}.
     */
    public void setSelectableTest(final java.util.function.Predicate<CardView> test) {
        this.selectableTest = test;
        regroupIfNeeded();
    }

    private java.util.function.Predicate<CardView> selectableTest;

    /**
     * Si algo del stack apunta ya a esta carta.
     *
     * <p>Reportado en Discord el 29-10-2026: Erode a una de dos fichas de
     * Soldado apiladas, Pyromancer Ascension lo copia, y la copia elige
     * objetivo nuevo... clicando la pila, que manda siempre la de delante: la
     * MISMA que ya llevaba el original. La copia se quedo sin objetivo al
     * resolverse el primero. Las dos fichas eran elegibles, asi que la pila no
     * se partia por {@link #setSelectableTest}; lo que las distingue para el
     * jugador es que a una le apunta una flecha. Ahora la apuntada va aparte,
     * con su flecha, y la otra se clica como siempre. Vale tambien cuando es
     * el rival el que apunta a una de tus fichas: se ve CUAL.
     */
    public void setTargetedTest(final java.util.function.Predicate<CardView> test) {
        this.targetedTest = test;
        regroupIfNeeded();
    }

    private java.util.function.Predicate<CardView> targetedTest;

    private boolean targeted(final CardView cv) {
        final java.util.function.Predicate<CardView> test = targetedTest;
        return test != null && test.test(cv);
    }

    private boolean selectableNow(final CardView cv) {
        final java.util.function.Predicate<CardView> test = selectableTest;
        return test != null && test.test(cv);
    }

    /**
     * Vuelve a repartir SOLO si la agrupacion cambia.
     *
     * <p>El motor marca y desmarca constantemente durante el combate; rehacer
     * la fila en cada aviso seria pagar un reparto entero por nada. Comparar
     * las claves es contar cadenas de una veintena de cartas.
     */
    private void regroupIfNeeded() {
        if (lastCards == null || lastCards.isEmpty()) {
            return;
        }
        final Map<String, List<CardView>> groups = groupCards(lastCards);
        if (groups.keySet().equals(nodeCache.keySet()) && sameSizes(groups)) {
            return;
        }
        setCards(lastCards, lastAttachedTo);
    }

    /**
     * Y con las mismas claves, si cada pila sigue teniendo las mismas cartas.
     *
     * <p>Las claves solas no bastaban (itch.io, 05-10-2026, improvisar con tres
     * Ornitopteros): con una ya elegida hay una pila de elegidas y otra de
     * libres, y al elegir la segunda las claves siguen siendo esas dos — solo
     * cambia cuantas hay en cada una. Sin reagrupar, la marca caia en la pila de
     * libres, y al repintar la mesa la pila de elegidas salia nueva y sin marca:
     * no se veia cual estaba cogida y clicarla la soltaba.
     */
    private boolean sameSizes(final Map<String, List<CardView>> groups) {
        for (final Map.Entry<String, List<CardView>> g : groups.entrySet()) {
            final CardStackNode node = nodeCache.get(g.getKey());
            if (node == null || node.getStackSize() != g.getValue().size()) {
                return false;
            }
        }
        return true;
    }

    private boolean picked(final CardView cv) {
        final java.util.function.Predicate<CardView> test = pickedTest;
        return test != null && test.test(cv);
    }

    /**
     * Clave de agrupacion. Solo las fichas se agrupan, y solo si son
     * indistinguibles: si una esta girada y otra no, van por separado, porque
     * para el jugador son cosas distintas.
     */
    private Map<String, List<CardView>> groupCards(final List<CardView> cards) {
        final Map<String, List<CardView>> groups = new LinkedHashMap<>();
        int unique = 0;
        for (final CardView cv : cards) {
            final String key;
            if (cv != null && canGroup(cv) && (cv.isToken() || sameCardsStack(cv))) {
                final CardStateView st = cv.getCurrentState();
                key = (cv.isToken() ? "tok|" : "same|") + (st == null ? "?" : st.getName())
                        + "|" + countersKey(cv)
                        + "|" + linkedKey(cv)
                        + "|" + (st == null ? "" : st.getPower() + "/" + st.getToughness())
                        + "|" + cv.isTapped()
                        + "|" + cv.isSick()
                        + "|" + cv.isAttacking()
                        + "|" + cv.isBlocking()
                        + "|" + cv.getDamage()
                        // Y si ya esta elegida: ver setPickedTest.
                        + "|" + picked(cv)
                        // Y si se puede elegir: ver setSelectableTest.
                        + "|" + selectableNow(cv)
                        // Y si algo del stack le apunta: ver setTargetedTest.
                        + "|" + targeted(cv);
            } else if (cv != null && cv.getId() >= 0) {
                // El id de la vista es estable mientras la carta siga ahi, y es
                // lo que permite reutilizar su nodo entre refrescos.
                key = "card|" + cv.getId();
            } else {
                // Las CardView creadas solo para interfaz (getCardForUi) NO
                // tienen id valido: comparten uno negativo. Agruparlas por id
                // las colapsaria todas en una pila con un "x4" absurdo. Cada
                // una va por su cuenta.
                key = "card|u" + (unique++);
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(cv);
        }
        return groups;
    }

    /**
     * Si esta CARTA (no ficha) puede ir en una pila con sus iguales. Lo que la
     * haria distinta a la vista o al jugar no se apila: con algo enganchado,
     * boca abajo, fuera de fase, copia de otra carta, el comandante, o sin id
     * estable.
     */
    private static boolean sameCardsStack(final CardView cv) {
        if (!forge.neo.NeoSettings.stackSameCards() || cv.getId() < 0) {
            return false;
        }
        if (cv.isFaceDown() || cv.isPhasedOut() || cv.isCloned() || cv.isCommander()) {
            return false;
        }
        return cv.getAttachedCards() == null || cv.getAttachedCards().isEmpty();
    }

    /**
     * <b>Lo que lleva ATADO, para que dos iguales a la vista pero distintas no se
     * apilen</b> (Discord, 29-10-2026, con video: dos copias de un mismo
     * encantamiento, una con la criatura del jugador exiliada debajo y la otra
     * sin nada; apiladas, clicar la pila siempre mandaba la de delante y no habia
     * forma de devolver la que importaba). Lo exiliado, lo enganchado por
     * codificar o imprimir, de quien roba el control, lo elegido (tipo, color,
     * nombre, numero, jugador, modo) y los marcadores del motor.
     */
    private static String linkedKey(final CardView cv) {
        final StringBuilder sb = new StringBuilder();
        ids(sb, cv.getExiledCards());
        ids(sb, cv.getUntilLeavesBattlefield());
        ids(sb, cv.getEncodedCards());
        ids(sb, cv.getImprintedCards());
        ids(sb, cv.getGainControlTargets());
        ids(sb, cv.getChosenCards());
        sb.append('|').append(cv.getPairedWith() == null ? "" : cv.getPairedWith().getId())
                .append('|').append(cv.getChosenType()).append('|').append(cv.getChosenType2())
                .append('|').append(cv.getChosenNumber()).append('|').append(cv.getChosenColors())
                .append('|').append(cv.getChosenPlayer() == null ? "" : cv.getChosenPlayer().getId())
                .append('|').append(cv.getChosenMode()).append('|').append(cv.getNamedCard())
                .append('|').append(cv.getMarkerText());
        return sb.toString();
    }

    private static void ids(final StringBuilder sb, final Iterable<forge.game.card.CardView> cards) {
        sb.append('[');
        if (cards != null) {
            final java.util.TreeSet<Integer> sorted = new java.util.TreeSet<>();
            for (final forge.game.card.CardView c : cards) {
                if (c != null) {
                    sorted.add(c.getId());
                }
            }
            sb.append(sorted);
        }
        sb.append(']');
    }

    /** Los contadores, ordenados, para que dos cartas con distintos no se apilen. */
    private static String countersKey(final CardView cv) {
        final com.google.common.collect.Multiset<forge.game.card.CounterType> c = cv.getCounters();
        if (c == null || c.isEmpty()) {
            return "";
        }
        final java.util.TreeMap<String, Integer> sorted = new java.util.TreeMap<>();
        for (final com.google.common.collect.Multiset.Entry<forge.game.card.CounterType> e : c.entrySet()) {
            sorted.put(String.valueOf(e.getElement()), e.getCount());
        }
        return sorted.toString();
    }

    public void refreshAll() {
        for (final Entry e : entries) {
            e.node.getFront().refresh();
            for (final CardNode a : e.node.getAttachments()) {
                a.refresh();
            }
        }
    }

    /**
     * Todas las cartas pintadas en esta fila, <b>lo enganchado incluido</b>.
     *
     * <p>Que un aura o un equipo esten aqui es lo que hace que el motor los
     * pueda usar: por esta lista pasan el indice de carta a nodo (las flechas),
     * el resaltado de "puedes clicar esto" y la busqueda de que hay bajo el
     * raton al arrastrar. Fuera de ella, una carta esta pintada pero no existe.
     *
     * <p>Van DESPUES de sus anfitrionas a proposito: quien busca por geometria
     * se queda con la ultima que encaja, y lo enganchado se pinta por delante.
     */
    public List<CardNode> nodes() {
        final List<CardNode> out = new ArrayList<>();
        for (final Entry e : entries) {
            out.add(e.node.getFront());
        }
        for (final Entry e : entries) {
            out.addAll(e.node.getAttachments());
        }
        return out;
    }

    // ---------------------------------------------------------------
    // Layout responsivo
    // ---------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        updateClip();
        final int n = entries.size();
        if (n == 0) {
            previous.setVisible(false); next.setVisible(false); scrollX = scrollMax = 0;
            return;
        }
        final double availW = Math.max(1, getWidth());
        final double availH = Math.max(1, getHeight());
        final ArenaRowLayout.Row row = ArenaRowLayout.measure(n, availW, maxCardWidth, availH);
        final double cardW = row.cardWidth();
        final double cardH = cardW * CardNode.ASPECT;

        // Las tapadas giran 90 grados y tumbadas miden lo que mide de alto una
        // derecha: con el mismo hueco que las demas se montaban encima de sus
        // vecinas (reportado en r/forgeMTG el 15-09-2026). Cada una se lleva el
        // ancho que le falta, centrada en su hueco, y el paso del resto se
        // calcula con lo que queda. Si no hay sitio, se aprieta igual que antes.
        int tappedCount = 0;
        for (final Entry e : entries) {
            if (e.node.getFront().isTapped()) {
                tappedCount++;
            }
        }
        final double extra = cardW * (CardNode.ASPECT - 1);
        final double step = n == 1 ? 0 : Math.max(cardW * .48,
                Math.min(cardW + 10, (availW - tappedCount * extra - cardW) / (n - 1)));
        final double content = cardW + step * (n - 1) + tappedCount * extra;
        scrollMax = Math.max(0, content - availW);
        scrollX = Math.max(0, Math.min(scrollX, scrollMax));
        final double x0 = Math.max(0, (availW - content) / 2);
        double shift = 0;
        for (int i = 0; i < n; i++) {
            final CardStackNode node = entries.get(i).node;
            final boolean tapped = node.getFront().isTapped();
            node.setCardWidth(cardW);
            node.resize(cardW, cardH);
            node.setLayoutX(x0 + i * step + shift + (tapped ? extra / 2 : 0) - scrollX);
            if (tapped) {
                shift += extra;
            }
            node.setLayoutY(Math.max(0, (availH - cardH) / 2));
            node.setViewOrder(node.getFront().isLifted() ? -10 : -i * .001);
        }
        // CUANTO SE VE DE CADA UNA: la de la derecha va encima, asi que de cada
        // carta derecha asoma hasta donde empieza la siguiente. Es lo que deja el
        // "xN" de una pila a la vista (CardStackNode.setVisibleWidth). Una
        // girada mide distinto de lado y su contador gira con ella: sin tope.
        for (int i = 0; i < n; i++) {
            final CardStackNode node = entries.get(i).node;
            if (i == n - 1 || node.getFront().isTapped()) {
                node.setVisibleWidth(0);
                continue;
            }
            final CardStackNode next = entries.get(i + 1).node;
            final double nextLeft = next.getLayoutX()
                    - (next.getFront().isTapped() ? extra / 2 : 0);
            node.setVisibleWidth(nextLeft - node.getLayoutX());
        }

        previous.setVisible(scrollMax > .5);
        next.setVisible(scrollMax > .5);
        previous.setDisable(scrollX <= .5);
        next.setDisable(scrollX >= scrollMax - .5);
        final double b = UiScale.px(24);
        previous.resizeRelocate(2, 2, b, b);
        next.resizeRelocate(Math.max(b + 2, availW - b - 2), 2, b, b);
        // El recorte se abre por debajo lo que asome lo enganchado. Se hace
        // aqui y no al recibir las cartas porque depende del tamano de carta,
        // que solo se sabe una vez repartido el ancho de la fila.
        double overhang = 0;
        for (final Entry e : entries) {
            overhang = Math.max(overhang, e.node.attachOverhang());
        }
        if (Math.abs(overhang - attachOverhang) > 0.5) {
            attachOverhang = overhang;
            updateClip();
        }
    }
}


