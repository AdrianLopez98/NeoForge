package forge.neo.ascent;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * El mapa de un acto: filas, nodos y por donde se puede ir.
 *
 * <h2>Como se genera</h2>
 *
 * <p>Se copia la idea de <i>Slay the Spire</i>, que esta resuelta: en vez de
 * repartir nodos y luego unirlos —que es lo que deja islas—, se <b>tiran
 * caminos enteros de abajo arriba</b> y los nodos son, por definicion, los
 * sitios por donde pasa algun camino. Los caminos se cruzan y se solapan, y de
 * ahi salen las bifurcaciones.
 *
 * <p>La consecuencia importante es estructural: <b>todo nodo pertenece al menos
 * a un camino completo</b>, o sea que se puede llegar a el desde el principio y
 * desde el se puede llegar al jefe. Las islas no se arreglan: no se pueden
 * crear.
 *
 * <p>Aun asi {@link #unreachable()} lo comprueba, y {@code ascentcheck} lo
 * exige sobre muchas semillas. Un nodo suelto no revienta nada: deja una run
 * que no se puede terminar, y eso <b>no se ve mirando el mapa</b>.
 *
 * <h2>Determinista</h2>
 *
 * <p>Todo sale de la semilla. Es lo que permite guardar la run como "semilla +
 * que nodos llevas" en vez de serializar el grafo, y que al recargar salga el
 * mismo mapa. Ni un {@code Math.random()} en toda la clase.
 */
public final class AscentMap {

    /** Filas del acto. La 0 es donde se empieza; la ultima es el jefe. */
    public static final int ROWS = 12;

    /** Columnas. Siete es lo que deja sitio a que los caminos se crucen sin amontonarse. */
    public static final int COLS = 7;

    /** Cuantos caminos se tiran. Con seis salen 3-4 nodos por fila. */
    private static final int PATHS = 6;

    /** El tesoro siempre en esta fila: a mitad del acto, como recompensa de haber llegado. */
    private static final int TREASURE_ROW = 5;

    /** Descanso obligatorio justo antes del jefe. */
    private static final int REST_ROW = ROWS - 2;

    /** Ni elites ni descansos antes de esto: al principio no hay con que. */
    private static final int NO_HARD_BEFORE = 4;

    private final long seed;
    private final int act;
    private final int ascension;
    private final Map<String, AscentNode> nodes = new LinkedHashMap<>();
    private final List<List<AscentNode>> rows = new ArrayList<>();

    /** Igual que {@link #AscentMap(long, int, int)}, sin Ascension (nivel 0). */
    public AscentMap(final long seed, final int act) {
        this(seed, act, 0);
    }

    /**
     * Genera el mapa de un acto.
     *
     * @param seed      la semilla; el mismo trio (semilla, acto, Ascension) da
     *                  siempre el mismo mapa
     * @param act       en que acto estamos (1..3), que cambia la mezcla de nodos
     * @param ascension nivel de Ascension: el 1 adelanta las elites una fila
     *                  (§weighted) y el 7 garantiza una mas por acto
     *                  (§forceExtraElite). El resto de niveles no tocan el mapa
     */
    public AscentMap(final long seed, final int act, final int ascension) {
        this.seed = seed;
        this.act = act;
        this.ascension = Math.max(0, ascension);
        for (int r = 0; r < ROWS; r++) {
            rows.add(new ArrayList<>());
        }
        // El acto entra en la semilla para que el acto 2 no sea el 1 otra vez.
        final Random rnd = new Random(seed * 31L + act);
        carvePaths(rnd);
        assignKinds(rnd);
        if (this.ascension >= 7) {
            forceExtraElite(rnd);
        }
    }

    // ------------------------------------------------------------------
    //  1. Los caminos
    // ------------------------------------------------------------------

    /**
     * Tira {@link #PATHS} caminos de la fila 0 a la penultima, y engancha el jefe.
     *
     * <p>Cada camino avanza una fila cada vez y se mueve como mucho una columna
     * a izquierda o derecha. Eso es lo que hace que el dibujo se lea: sin ese
     * limite salen aristas que cruzan media pantalla.
     */
    private void carvePaths(final Random rnd) {
        for (int p = 0; p < PATHS; p++) {
            int col = rnd.nextInt(COLS);
            AscentNode prev = nodeAt(0, col);
            for (int r = 1; r <= REST_ROW; r++) {
                final int delta = rnd.nextInt(3) - 1; // -1, 0 o +1
                col = Math.max(0, Math.min(COLS - 1, col + delta));
                final AscentNode cur = nodeAt(r, col);
                prev.link(cur);
                prev = cur;
            }
        }

        // El jefe: uno solo, en el centro, y TODA la penultima fila lleva a el.
        // Es lo que cierra el acto — da igual por donde hayas subido.
        final AscentNode boss = nodeAt(ROWS - 1, COLS / 2);
        boss.setKind(AscentNode.Kind.BOSS);
        for (final AscentNode n : rows.get(REST_ROW)) {
            n.link(boss);
        }
    }

    /** El nodo de esa casilla, creandolo si es la primera vez que un camino pasa por ahi. */
    private AscentNode nodeAt(final int row, final int col) {
        final String key = row + "," + col;
        AscentNode n = nodes.get(key);
        if (n == null) {
            n = new AscentNode(row, col, AscentNode.Kind.COMBAT);
            nodes.put(key, n);
            rows.get(row).add(n);
        }
        return n;
    }

    // ------------------------------------------------------------------
    //  2. Que hay en cada nodo
    // ------------------------------------------------------------------

    /**
     * Reparte los tipos.
     *
     * <p>Tres filas son fijas y no se sortean, porque son las que dan forma al
     * acto: la primera siempre es combate (empezar en una tienda sin creditos
     * seria un nodo perdido), la {@value #TREASURE_ROW} es el tesoro y la
     * penultima el descanso antes del jefe.
     *
     * <p>El resto se sortea con dos reglas que evitan los mapas que se sienten
     * mal: nada duro demasiado pronto, y <b>nunca dos nodos del mismo tipo
     * seguidos en un mismo camino</b>. Sin la segunda salen tramos de tres
     * tiendas que hacen la ruta obvia.
     */
    private void assignKinds(final Random rnd) {
        for (final AscentNode n : rows.get(0)) {
            n.setKind(AscentNode.Kind.COMBAT);
        }
        for (final AscentNode n : rows.get(TREASURE_ROW)) {
            n.setKind(AscentNode.Kind.TREASURE);
        }
        for (final AscentNode n : rows.get(REST_ROW)) {
            n.setKind(AscentNode.Kind.REST);
        }

        for (int r = 1; r < ROWS - 1; r++) {
            if (r == TREASURE_ROW || r == REST_ROW) {
                continue;
            }
            for (final AscentNode n : rows.get(r)) {
                n.setKind(roll(rnd, r, n));
            }
        }
    }

    /**
     * Que le toca a un nodo.
     *
     * <p>Se reintenta hasta ocho veces para no repetir tipo con un padre. Es un
     * tope y no un bucle: si en ocho tiradas no sale nada distinto, se deja lo
     * que haya. Un mapa un poco repetitivo es un mal menor comparado con
     * colgar la generacion, que es lo que pasaria si la condicion fuera
     * imposible de cumplir.
     */
    private AscentNode.Kind roll(final Random rnd, final int row, final AscentNode node) {
        AscentNode.Kind kind = AscentNode.Kind.COMBAT;
        for (int attempt = 0; attempt < 8; attempt++) {
            kind = weighted(rnd, row);
            if (!repeatsParent(node, kind)) {
                return kind;
            }
        }
        return kind;
    }

    /**
     * El sorteo, con los pesos del acto.
     *
     * <p>Los combates bajan y las elites suben segun avanza la run: en el acto
     * 3 el mapa tiene que dar miedo.
     */
    private AscentNode.Kind weighted(final Random rnd, final int row) {
        final boolean hardAllowed = row >= NO_HARD_BEFORE;
        // Ascension 1: las elites entran una fila antes que el resto de lo
        // "duro" (descanso, tienda). Solo las elites: adelantar tambien el
        // descanso quitaria tension en vez de darla.
        final boolean eliteAllowed = row >= (ascension >= 1 ? NO_HARD_BEFORE - 1 : NO_HARD_BEFORE);
        final int elite = eliteAllowed ? 8 + act * 4 : 0;
        // ⚠️ Nada de descanso justo debajo de la fila de descanso obligatorio.
        // repeatsParent() mira los PADRES, y la fila REST_ROW se fija antes del
        // sorteo, asi que por ahi se colaban dos descansos seguidos: se veia en
        // el volcado del mapa (una R en la fila 9 con la 10 entera de R).
        // Curarse dos veces seguidas ademas rompe la tension, que es el modo.
        final int rest = hardAllowed && row != REST_ROW - 1 ? 10 : 0;
        final int shop = 8;
        final int event = 22;
        final int combat = 100 - elite - rest - shop - event;

        int roll = rnd.nextInt(100);
        if ((roll -= combat) < 0) {
            return AscentNode.Kind.COMBAT;
        }
        if ((roll -= event) < 0) {
            return AscentNode.Kind.EVENT;
        }
        if ((roll -= shop) < 0) {
            return AscentNode.Kind.SHOP;
        }
        if ((roll -= rest) < 0) {
            return AscentNode.Kind.REST;
        }
        return AscentNode.Kind.ELITE;
    }

    /**
     * Ascension 7: una elite mas por acto, <b>garantizada</b>.
     *
     * <p>Subir el peso del sorteo en {@link #weighted} solo mueve una
     * <b>media</b> entre muchos mapas — la sonda mide esa media sobre cientos
     * de tiradas, pero el jugador juega UN mapa, y ahi "la media ha subido" no
     * se nota nunca. Convertir un combate ya sorteado en elite si que se nota,
     * y siempre.
     *
     * <p>Se busca desde la fila del jefe hacia abajo (la primera fila con
     * candidatos gana): una elite de mas pesa mas cerca del final del acto,
     * que es donde tiene que doler un nivel de dificultad elegido a proposito.
     * Se respeta la misma regla que el resto del mapa —{@link #repeatsParent}—
     * para no encadenar dos elites seguidas en un mismo camino.
     */
    private void forceExtraElite(final Random rnd) {
        final int eliteFloor = ascension >= 1 ? NO_HARD_BEFORE - 1 : NO_HARD_BEFORE;
        final List<AscentNode> candidates = new ArrayList<>();
        for (int r = ROWS - 2; r >= eliteFloor; r--) {
            if (r == TREASURE_ROW || r == REST_ROW) {
                continue;
            }
            for (final AscentNode n : rows.get(r)) {
                if (n.getKind() == AscentNode.Kind.COMBAT
                        && !repeatsParent(n, AscentNode.Kind.ELITE)) {
                    candidates.add(n);
                }
            }
            if (!candidates.isEmpty()) {
                break;
            }
        }
        if (!candidates.isEmpty()) {
            candidates.get(rnd.nextInt(candidates.size())).setKind(AscentNode.Kind.ELITE);
        }
    }

    /** Si alguno de los nodos que llevan aqui ya es de ese tipo. */
    private boolean repeatsParent(final AscentNode node, final AscentNode.Kind kind) {
        if (kind == AscentNode.Kind.COMBAT || kind == AscentNode.Kind.EVENT) {
            return false; // los normales si pueden repetirse: son el relleno
        }
        for (final AscentNode other : nodes.values()) {
            if (other.getNext().contains(node) && other.getKind() == kind) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  3. Consultas
    // ------------------------------------------------------------------

    public long getSeed() {
        return seed;
    }

    public int getAct() {
        return act;
    }

    /** Los nodos de una fila, de izquierda a derecha. */
    public List<AscentNode> row(final int row) {
        return rows.get(row);
    }

    /** Por donde se puede empezar. */
    public List<AscentNode> entries() {
        return rows.get(0);
    }

    /** El jefe del acto. */
    public AscentNode boss() {
        return rows.get(ROWS - 1).get(0);
    }

    public AscentNode node(final int row, final int col) {
        return nodes.get(row + "," + col);
    }

    public int size() {
        return nodes.size();
    }

    /** Cuantos nodos hay de cada tipo. Para el comprobador y para afinar los pesos. */
    public Map<AscentNode.Kind, Integer> census() {
        final Map<AscentNode.Kind, Integer> out = new LinkedHashMap<>();
        for (final AscentNode.Kind k : AscentNode.Kind.values()) {
            out.put(k, 0);
        }
        for (final AscentNode n : nodes.values()) {
            out.merge(n.getKind(), 1, Integer::sum);
        }
        return out;
    }

    /**
     * Los nodos a los que no se puede llegar, o desde los que no se llega al jefe.
     *
     * <p><b>Tiene que salir siempre vacio.</b> Es la unica invariante del mapa
     * que, si se rompe, deja una run <i>imposible de terminar</i> sin dar
     * ningun error y sin que se note mirando el dibujo. Por eso se comprueba
     * sobre muchas semillas y no sobre una.
     */
    public List<AscentNode> unreachable() {
        // Alcanzables desde la fila 0, hacia arriba.
        final Set<AscentNode> down = new HashSet<>();
        final Deque<AscentNode> pending = new ArrayDeque<>(entries());
        down.addAll(entries());
        while (!pending.isEmpty()) {
            for (final AscentNode nx : pending.pop().getNext()) {
                if (down.add(nx)) {
                    pending.push(nx);
                }
            }
        }
        // Y los que llegan al jefe, hacia atras.
        final Set<AscentNode> up = new HashSet<>();
        up.add(boss());
        boolean changed = true;
        while (changed) {
            changed = false;
            for (final AscentNode n : nodes.values()) {
                if (up.contains(n)) {
                    continue;
                }
                for (final AscentNode nx : n.getNext()) {
                    if (up.contains(nx)) {
                        up.add(n);
                        changed = true;
                        break;
                    }
                }
            }
        }

        final List<AscentNode> bad = new ArrayList<>();
        for (final AscentNode n : nodes.values()) {
            if (!down.contains(n) || !up.contains(n)) {
                bad.add(n);
            }
        }
        return bad;
    }

    /**
     * Los tramos que rompen la regla de "no dos iguales seguidos en un camino".
     *
     * <p>Solo se mira sobre los tipos que <b>cambian la decision</b>: descanso,
     * tienda y elite. Dos combates seguidos son lo normal — son el relleno.
     *
     * <p>Devuelve las aristas infractoras como {@code "5,2 -> 6,2 (REST)"}.
     */
    public List<String> consecutiveViolations() {
        final Set<AscentNode.Kind> watched = new HashSet<>();
        watched.add(AscentNode.Kind.REST);
        watched.add(AscentNode.Kind.SHOP);
        watched.add(AscentNode.Kind.ELITE);

        final List<String> bad = new ArrayList<>();
        for (final AscentNode n : nodes.values()) {
            if (!watched.contains(n.getKind())) {
                continue;
            }
            for (final AscentNode nx : n.getNext()) {
                if (nx.getKind() == n.getKind()) {
                    bad.add(n.key() + " -> " + nx.key() + " (" + n.getKind() + ")");
                }
            }
        }
        return bad;
    }

    /** El mapa en texto, para el comprobador y para mirarlo sin ventana. */
    public String render() {
        final StringBuilder sb = new StringBuilder();
        for (int r = ROWS - 1; r >= 0; r--) {
            sb.append(String.format("  %2d |", r));
            for (int c = 0; c < COLS; c++) {
                final AscentNode n = node(r, c);
                sb.append(' ').append(n == null ? '.' : glyph(n.getKind()));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static char glyph(final AscentNode.Kind k) {
        switch (k) {
            case COMBAT:   return 'x';
            case ELITE:    return 'E';
            case REST:     return 'R';
            case SHOP:     return '$';
            case TREASURE: return 'T';
            case EVENT:    return '?';
            case BOSS:     return 'B';
            default:       return '.';
        }
    }
}
