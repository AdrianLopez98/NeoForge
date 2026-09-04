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
 * <p>Solo se agrupan FICHAS realmente identicas (mismo nombre, misma fuerza y
 * resistencia, mismo estado de giro y de combate). Dos cartas normales nunca se
 * apilan aunque se llamen igual: cada permanente es una entidad distinta con la
 * que se puede interactuar por separado.
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
        clip.widthProperty().bind(widthProperty());
        setClip(clip);
        updateClip();
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

    private void updateClip() {
        clip.setY(-overflowTop);
        clip.setHeight(getHeight() + overflowTop + overflowBottom + attachOverhang);
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
        return Math.min(baseCardWidth, (availW - GAP * (n - 1)) / n);
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
        getChildren().clear();
        // Los fantasmas van al fondo: lo vivo se pinta por encima.
        getChildren().addAll(ghosts);

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

            node.setAttachmentHandlers(this::fireClick, this::fireHover);
            node.setAttachments(attachedTo.get(members.get(0).getId()));
            node.getFront().setCombatDirection(combatDirection);

            entries.add(new Entry(node));
            getChildren().add(node);
        }

        // Soltar los nodos de cartas que ya no estan en esta zona.
        nodeCache.keySet().retainAll(groups.keySet());
        lastIds.clear();
        lastIds.addAll(nowIds);
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

    private void startGhost(final CardStackNode node) {
        if (node == null || ghosts.contains(node)) {
            return;
        }
        ghosts.add(node);
        trace("se va", node.getFront().getCard());
        Anim.leave(node, () -> {
            ghosts.remove(node);
            getChildren().remove(node);
        });
    }

    private void wire(final CardStackNode node) {
        node.getFront().hoverProperty().addListener((o, was, is) -> {
            if (is) {
                fireHover(node.getFront());
            }
        });
        node.getFront().setOnMouseClicked(e -> {
            // El derecho lo usa la mesa para ampliar la carta.
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
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
     * <p>Por eso NO se agrupan las fichas que estan en combate ni cuando hay una
     * seleccion en curso. El resto del tiempo se apilan, que es para lo que
     * sirve: once Zombis no pueden ocupar once huecos.
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
            requestLayout();
        }
    }

    public boolean isGroupingEnabled() {
        return grouping;
    }

    private boolean grouping = true;

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
            if (cv != null && cv.isToken() && canGroup(cv)) {
                final CardStateView st = cv.getCurrentState();
                key = "tok|" + (st == null ? "?" : st.getName())
                        + "|" + (st == null ? "" : st.getPower() + "/" + st.getToughness())
                        + "|" + cv.isTapped()
                        + "|" + cv.isSick()
                        + "|" + cv.isAttacking()
                        + "|" + cv.isBlocking()
                        + "|" + cv.getDamage();
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
            return;
        }
        // El ancho REAL, sin suelos inventados. Un suelo aqui es un fallo
        // silencioso: el contenido se reparte sobre un ancho que no existe y
        // el recorte (setClip) se come lo que sobra. Sintoma real jugando: un
        // encantamiento suelto al lado de diez tierras salia cortado por la
        // mitad, porque su grupo medi­a 148 px y se maquetaba sobre 200.
        final double availW = Math.max(1, getWidth());
        final double availH = Math.max(1, getHeight());

        // 1. Buscar cuantas filas hacen falta para que la carta no baje del
        //    tamano minimo legible.
        int rows = 1;
        double cardW = maxCardWidth;
        int perRow = n;
        final double minW = maxCardWidth * MIN_SCALE;

        while (rows <= MAX_ROWS) {
            perRow = (int) Math.ceil(n / (double) rows);
            cardW = (availW - GAP * (perRow - 1)) / perRow;
            if (cardW >= minW) {
                break;
            }
            rows++;
        }
        rows = Math.min(rows, MAX_ROWS);
        perRow = (int) Math.ceil(n / (double) rows);

        // 2. La altura tambien manda: con varias filas puede que no quepan.
        cardW = Math.min(cardW, maxCardWidth);
        final double maxCardH = (availH - ROW_GAP * (rows - 1)) / rows;
        cardW = Math.min(cardW, maxCardH / CardNode.ASPECT);

        cardW = Math.max(cardW, MIN_CARD_WIDTH);

        // 3. El suelo de 28 px puede haber DESHECHO el limite de altura del
        //    paso 2, y entonces las filas ya no caben en el hueco.
        //
        //    Sin esto, lo que sobra no se ve: `updateClip` recorta el pane y
        //    las cartas salen <b>cortadas por abajo</b> — media carta, sin caja
        //    de texto y sin P/T. Reportado jugando con tres mesas de rival en
        //    fila, que es donde el hueco se queda bajo de verdad.
        //
        //    La salida es la misma que ya usa el paso 4 para el ancho, y por el
        //    mismo motivo: MENOS filas y mas solape. Una carta apretada se lee
        //    mal; una carta cortada por la mitad no se lee.
        final double rowH = cardW * CardNode.ASPECT;
        final int rowsThatFit = Math.max(1,
                (int) Math.floor((availH + ROW_GAP) / (rowH + ROW_GAP)));
        if (rowsThatFit < rows) {
            rows = rowsThatFit;
            perRow = (int) Math.ceil(n / (double) rows);
        }

        // 4. Si aun asi no caben a lo ancho, se solapan (como Arena con las
        //    tierras) en vez de encoger mas y volverse ilegibles.
        double step = cardW + GAP;
        final double neededW = step * (perRow - 1) + cardW;
        if (neededW > availW && perRow > 1) {
            final double exact = (availW - cardW) / (perRow - 1);
            step = Math.max(exact, cardW * (1 - MAX_OVERLAP));
            // MAX_OVERLAP es un tope de LEGIBILIDAD, no una licencia para
            // desbordar: si respetarlo dejaria la ultima carta fuera del
            // recorte, se solapa mas. Una carta apretada se lee mal; una carta
            // cortada por la mitad no se lee.
            if (step * (perRow - 1) + cardW > availW) {
                step = exact;
            }
        }

        final double cardH = cardW * CardNode.ASPECT;
        final double usedH = cardH * rows + ROW_GAP * (rows - 1);
        final double y0 = Math.max(0, (availH - usedH) / 2);

        int i = 0;
        for (int r = 0; r < rows; r++) {
            final int countThisRow = Math.min(perRow, n - r * perRow);
            if (countThisRow <= 0) {
                break;
            }
            final double rowW = step * (countThisRow - 1) + cardW;
            final double x0 = Math.max(0, (availW - rowW) / 2);
            for (int c = 0; c < countThisRow; c++, i++) {
                final CardStackNode node = entries.get(i).node;
                node.setCardWidth(cardW);
                node.resize(cardW, cardH);
                node.setLayoutX(x0 + c * step);
                node.setLayoutY(y0 + r * (cardH + ROW_GAP));
                // Las de la derecha por encima, para que el solape se lea bien.
                node.setViewOrder(-c * 0.001);
            }
        }

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
