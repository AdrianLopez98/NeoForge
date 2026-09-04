package forge.neo.ascent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import forge.neo.NeoSettings;

/**
 * El estado de una run de Ascenso. Todo lo que sobrevive a cerrar el juego.
 *
 * <h2>Como se guarda</h2>
 *
 * <p>En {@code neo.properties}, a mano, como {@code DraftRun} — <b>nada de
 * XStream</b>, que es lo que obliga a los siete {@code --add-opens} y lo que
 * revienta al guardar una aventura. El mazo va aparte, en un {@code .dck}
 * normal, que es formato del motor y ya sabemos leer y escribir.
 *
 * <h2>El mapa NO se guarda</h2>
 *
 * <p>Se guardan la <b>semilla</b> y <b>que nodos llevas resueltos</b>.
 * {@link AscentMap} es determinista, asi que el mapa se reconstruye igual al
 * recargar. Guardar el grafo seria guardar algo que ya sabemos calcular, y
 * ademas dejaria de casar en cuanto se tocaran los pesos del generador.
 *
 * <h2>Una sola run a la vez</h2>
 *
 * <p>A proposito. Lo que da valor a una decision es que no puedes volver
 * atras; con tres runs en marcha, "elegir camino" deja de significar nada.
 * Abandonar es explicito ({@link #discard()}), no un descuido.
 */
public final class AscentRun {

    /** Con que reglas se juega la run. Es una pregunta del principio, no un ajuste. */
    public enum Mode {
        /** 30 cartas, avatar Vanguard de personaje, 20 vidas. El roguelike puro. */
        STANDARD,
        /** Comandante de ancla, 21 cartas, 40 vidas, recompensas filtradas por identidad. */
        COMMANDER
    }

    /** Cuantos actos tiene una run completa. */
    public static final int ACTS = 3;

    private static final String PREFIX = "ascent.";
    private static final String ACTIVE = PREFIX + "active";

    private Mode mode;
    private long seed;
    private int act;
    private int life;
    private int maxLife;
    private int credits;
    private int ascension;
    /** El nodo en el que estas, o {@code null} si todavia no has entrado en el acto. */
    private String currentNode;
    private final Set<String> clearedNodes = new LinkedHashSet<>();
    private final List<String> relics = new ArrayList<>();
    /** El nombre del mazo de la run, que es un .dck en la carpeta de Ascenso. */
    private String deckName;

    private AscentRun() {
    }

    // ------------------------------------------------------------------
    //  Empezar, cargar, guardar, abandonar
    // ------------------------------------------------------------------

    /**
     * Empieza una run nueva. <b>Pisa la que hubiera</b>: solo hay una.
     *
     * @param mode      con que reglas
     * @param ascension nivel de dificultad desbloqueado que se quiere jugar
     * @param maxLife   vida maxima de partida (la pone el personaje)
     * @param deckName  el mazo semilla, ya guardado
     */
    public static AscentRun start(final Mode mode, final int ascension,
                                  final int maxLife, final String deckName) {
        final AscentRun run = new AscentRun();
        run.mode = mode;
        run.seed = System.nanoTime();
        run.act = 1;
        run.maxLife = maxLife;
        // Ascension 8: se empieza magullado, al 90% del techo. Redondeado
        // hacia arriba y con suelo de 1: una run no puede nacer muerta.
        run.life = ascension >= 8
                ? Math.max(1, (int) Math.round(maxLife * 0.90)) : maxLife;
        run.credits = 0;
        run.ascension = ascension;
        run.deckName = deckName;
        run.currentNode = null;
        run.save();
        return run;
    }

    /** La run en curso, o {@code null} si no hay ninguna. */
    public static AscentRun current() {
        if (!NeoSettings.getBool(ACTIVE, false)) {
            return null;
        }
        final AscentRun run = new AscentRun();
        run.mode = "COMMANDER".equals(NeoSettings.get(PREFIX + "mode", "STANDARD"))
                ? Mode.COMMANDER : Mode.STANDARD;
        try {
            run.seed = Long.parseLong(NeoSettings.get(PREFIX + "seed", "0"));
        } catch (final NumberFormatException e) {
            run.seed = 0L;
        }
        run.act = NeoSettings.getInt(PREFIX + "act", 1);
        run.maxLife = NeoSettings.getInt(PREFIX + "maxLife", 20);
        run.life = NeoSettings.getInt(PREFIX + "life", run.maxLife);
        run.credits = NeoSettings.getInt(PREFIX + "credits", 0);
        run.ascension = NeoSettings.getInt(PREFIX + "ascension", 0);
        run.deckName = NeoSettings.get(PREFIX + "deck", null);
        run.currentNode = NeoSettings.get(PREFIX + "node", null);
        split(NeoSettings.get(PREFIX + "cleared", ""), run.clearedNodes);
        final List<String> rel = new ArrayList<>();
        split(NeoSettings.get(PREFIX + "relics", ""), rel);
        run.relics.addAll(rel);
        return run;
    }

    /** Vuelca la run a {@code neo.properties}. */
    public void save() {
        NeoSettings.setBool(ACTIVE, true);
        NeoSettings.set(PREFIX + "mode", mode.name());
        NeoSettings.set(PREFIX + "seed", String.valueOf(seed));
        NeoSettings.setInt(PREFIX + "act", act);
        NeoSettings.setInt(PREFIX + "life", life);
        NeoSettings.setInt(PREFIX + "maxLife", maxLife);
        NeoSettings.setInt(PREFIX + "credits", credits);
        NeoSettings.setInt(PREFIX + "ascension", ascension);
        NeoSettings.set(PREFIX + "deck", deckName);
        NeoSettings.set(PREFIX + "node", currentNode);
        NeoSettings.set(PREFIX + "cleared", String.join(";", clearedNodes));
        NeoSettings.set(PREFIX + "relics", String.join(";", relics));
        NeoSettings.save();
    }

    /**
     * Empieza una run nueva, mazo incluido.
     *
     * <p>Es el camino por el que se empieza de verdad: {@link #start} solo
     * anota el marcador, y una run sin mazo guardado no se puede jugar. Aqui se
     * genera el mazo de salida ({@link AscentSeedDeck}), se guarda en la carpeta
     * de Ascenso y se apunta su nombre.
     *
     * @param commander en Commander, el que eligio el jugador, o {@code null}
     *                  para que salga uno al azar. En Estandar se ignora
     */
    public static AscentRun begin(final Mode mode, final int ascension, final int maxLife,
                                  final forge.item.PaperCard commander) {
        final String name = deckNameFor(mode);
        final forge.deck.Deck deck = AscentSeedDeck.generate(mode, commander, name, ascension);
        AscentDecks.save(deck);
        return start(mode, ascension, maxLife, name);
    }

    /**
     * Como se llama el mazo de una run.
     *
     * <p>Lleva la fecha porque el fichero sobrevive a la run si algo va mal al
     * borrarlo, y dos cadaveres con el mismo nombre se pisan sin decirlo.
     */
    private static String deckNameFor(final Mode mode) {
        // La fecha para poder reconocerlo de un vistazo, y una etiqueta corta
        // para que dos runs del mismo dia no compartan fichero: si lo
        // compartieran, empezar la segunda pisaria el mazo de la primera y
        // abandonar cualquiera de las dos borraria el de la otra.
        final String tag = Long.toString(System.nanoTime() & 0xFFFFFFL, 36);
        return "Ascenso " + (mode == Mode.COMMANDER ? "Commander" : "Estandar")
                + " " + java.time.LocalDate.now() + " " + tag;
    }

    /**
     * Se lleva todos los ajustes de Ascenso, para poder reponerlos luego.
     *
     * <p>Lo usa {@code AscentCheck}, que recorre veinte runs de mentira: sin
     * esto, pasar los comprobadores <b>borraria la run de verdad</b> del
     * jugador, y en un modo donde no se puede volver atras eso no se arregla.
     */
    static java.util.Map<String, String> snapshot() {
        final java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        for (final String key : NeoSettings.keysWithPrefix(PREFIX)) {
            out.put(key, NeoSettings.get(key, null));
        }
        return out;
    }

    /** Repone lo que se llevo {@link #snapshot()}, y borra lo que sobre. */
    static void restore(final java.util.Map<String, String> saved) {
        for (final String key : NeoSettings.keysWithPrefix(PREFIX)) {
            NeoSettings.set(key, null);
        }
        for (final java.util.Map.Entry<String, String> e : saved.entrySet()) {
            NeoSettings.set(e.getKey(), e.getValue());
        }
        NeoSettings.save();
    }

    /**
     * Se acabo la run: se borra el marcador entero, y el mazo con el.
     *
     * <p>No se conserva nada. Que perder duela es el modo — los desbloqueos y
     * la Ascension maxima viven aparte precisamente para que sobrevivan a esto.
     *
     * <p>El {@code .dck} se borra tambien: si no, en un mes la carpeta de
     * Ascenso tiene cuarenta mazos muertos que nadie va a mirar.
     */
    public void discard() {
        AscentDecks.remove(deckName);
        for (final String k : new String[]{"mode", "seed", "act", "life", "maxLife",
                "credits", "ascension", "deck", "node", "cleared", "relics"}) {
            NeoSettings.set(PREFIX + k, null);
        }
        NeoSettings.setBool(ACTIVE, false);
        NeoSettings.save();
    }

    // ------------------------------------------------------------------
    //  El mapa y el avance
    // ------------------------------------------------------------------

    /** El mapa del acto en curso, reconstruido de la semilla. */
    public AscentMap map() {
        final AscentMap map = new AscentMap(seed, act, ascension);
        for (int r = 0; r < AscentMap.ROWS; r++) {
            for (final AscentNode n : map.row(r)) {
                n.setCleared(clearedNodes.contains(n.key()));
            }
        }
        return map;
    }

    /**
     * A que nodos se puede ir ahora mismo.
     *
     * <p>Al empezar un acto, a cualquiera de la fila 0. Despues, solo a los que
     * salen del nodo en el que estas. <b>Esta es la regla que hace que el mapa
     * sea una decision</b>: si se pudiera ir a cualquiera, no habria ruta.
     */
    public List<AscentNode> available() {
        final AscentMap map = map();
        if (currentNode == null) {
            return map.entries();
        }
        for (int r = 0; r < AscentMap.ROWS; r++) {
            for (final AscentNode n : map.row(r)) {
                if (n.key().equals(currentNode)) {
                    return n.getNext();
                }
            }
        }
        return Collections.emptyList();
    }

    /** Si desde donde estas se puede ir a ese nodo. */
    public boolean canEnter(final AscentNode node) {
        for (final AscentNode n : available()) {
            if (n.key().equals(node.key())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marca un nodo como resuelto y se planta en el.
     *
     * @return {@code true} si era un movimiento legal
     */
    public boolean clear(final AscentNode node) {
        if (!canEnter(node)) {
            return false;
        }
        currentNode = node.key();
        clearedNodes.add(node.key());
        save();
        return true;
    }

    /** Si el jefe de este acto ya cayo. */
    public boolean actCleared() {
        return clearedNodes.contains(map().boss().key());
    }

    /**
     * Pasa al acto siguiente, o dice que la run esta ganada.
     *
     * <p>El progreso de nodos se vacia: cada acto es un mapa nuevo. La vida,
     * los creditos, las reliquias y el mazo se quedan — eso es lo que hace que
     * sea una run y no tres partidas seguidas.
     *
     * @return {@code false} si ya no hay mas actos (o sea, run completada)
     */
    public boolean nextAct() {
        if (act >= ACTS) {
            return false;
        }
        act++;
        clearedNodes.clear();
        currentNode = null;
        save();
        return true;
    }

    // ------------------------------------------------------------------
    //  Vida, creditos y reliquias
    // ------------------------------------------------------------------

    /**
     * Aplica el resultado de un combate.
     *
     * @param remainingLife la vida con la que se salio del duelo
     * @return {@code true} si la run sigue viva
     */
    public boolean recordLife(final int remainingLife) {
        life = Math.max(0, Math.min(maxLife, remainingLife));
        save();
        return life > 0;
    }

    /** Que parte de tu vida maxima cura un descanso. */
    private static final double REST_HEAL = 0.30;

    /** Y con Ascension 2 en adelante, menos: es lo que endurece ese nivel. */
    private static final double REST_HEAL_HARD = 0.20;

    /**
     * Cuanto cura un descanso ahora mismo.
     *
     * <p>Vive aqui y no en la pantalla a proposito: es <b>logica del modo</b> —
     * la cura es una de las dos mitades de la decision mas pensada de la run, y
     * el nivel 2 de Ascension la recorta. Metida en un {@code Screen} de JavaFX
     * no se podria comprobar sin ventana, que es la regla de la que vive este
     * modo entero.
     *
     * <p>Nunca devuelve cero: un descanso que no cura nada es un nodo que
     * miente. Si ya estas a tope, quien llame tiene que ofrecer <b>quitar
     * carta</b> y no curar — eso lo decide la pantalla, que para eso mira
     * {@link #getLife()}.
     */
    public int restHeal() {
        final double rate = ascension >= 2 ? REST_HEAL_HARD : REST_HEAL;
        final int amount = (int) Math.round(maxLife * rate);
        return Math.max(1, Math.min(amount, Math.max(1, maxLife - life)));
    }

    /** Cura, sin pasar del maximo. */
    public void heal(final int amount) {
        life = Math.min(maxLife, life + amount);
        save();
    }

    /**
     * Quita vida, <b>sin poder matar</b>.
     *
     * <p>Lo segundo es una decision del modo, no un descuido. Lo usan los
     * eventos ({@link AscentEvent}), y ahi el jugador esta eligiendo de una
     * lista de texto: acabar una run de cuarenta minutos en un menu, sin haber
     * jugado una carta, es el peor final posible. Que duela, si; que mate, no.
     * Para eso estan los duelos.
     *
     * @return cuanta vida se ha perdido de verdad (puede ser menos de la pedida)
     */
    public int hurt(final int amount) {
        final int before = life;
        life = Math.max(1, life - Math.max(0, amount));
        save();
        return before - life;
    }

    /**
     * Sube la vida maxima, y cura lo mismo.
     *
     * <p>Curar de paso no es un regalo: subir el techo sin subir la vida es un
     * premio que no se nota hasta el proximo descanso, y en un roguelike el
     * premio tiene que sentirse cuando te lo dan.
     */
    /**
     * Baja la vida maxima, con suelo.
     *
     * <p>Es el precio de las cosas que valen de verdad (el pacto): un coste que
     * <b>no se recupera descansando</b>, y por eso pesa distinto que perder
     * vida. El suelo existe porque un techo de 3 no es una decision arriesgada,
     * es una run muerta que todavia no lo sabe.
     *
     * @return cuanto ha bajado de verdad
     */
    public int lowerMaxLife(final int amount) {
        final int before = maxLife;
        maxLife = Math.max(MIN_MAX_LIFE, maxLife - Math.max(0, amount));
        life = Math.min(life, maxLife);
        save();
        return before - maxLife;
    }

    /** Por debajo de esto no puede bajar el techo de vida. */
    public static final int MIN_MAX_LIFE = 10;

    public void raiseMaxLife(final int amount) {
        if (amount <= 0) {
            return;
        }
        maxLife += amount;
        life = Math.min(maxLife, life + amount);
        save();
    }

    public void addCredits(final int amount) {
        credits = Math.max(0, credits + amount);
        save();
    }

    /** Gasta, si llega. */
    public boolean spend(final int amount) {
        if (amount > credits) {
            return false;
        }
        credits -= amount;
        save();
        return true;
    }

    /** Anyade una reliquia. Las repetidas no se apilan: no haria nada. */
    public boolean addRelic(final AscentRelic relic) {
        if (relic == null || relics.contains(relic.getId())) {
            return false;
        }
        relics.add(relic.getId());
        save();
        return true;
    }

    /**
     * Quita una reliquia.
     *
     * <p>Lo unico que la quita hoy es la fragua ({@code AscentEvent}), que
     * refunde una comun en una rara. No hay ningun otro camino a proposito: una
     * reliquia perdida sin haberlo pedido seria el peor tipo de sorpresa —
     * pasan mucho rato en pantalla y se cuenta con ellas.
     *
     * @return {@code true} si la llevabas y ya no
     */
    public boolean removeRelic(final AscentRelic relic) {
        if (relic == null || !relics.remove(relic.getId())) {
            return false;
        }
        save();
        return true;
    }

    /** Las reliquias que llevas, ya resueltas contra el catalogo. */
    public List<AscentRelic> relics() {
        final List<AscentRelic> out = new ArrayList<>();
        for (final String id : relics) {
            final AscentRelic r = AscentRelics.byId(id);
            // Una reliquia que ya no existe (se quito del catalogo entre
            // versiones) se ignora en vez de reventar la run guardada.
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  Consultas
    // ------------------------------------------------------------------

    public Mode getMode() {
        return mode;
    }

    public long getSeed() {
        return seed;
    }

    public int getAct() {
        return act;
    }

    public int getLife() {
        return life;
    }

    public int getMaxLife() {
        return maxLife;
    }

    public int getCredits() {
        return credits;
    }

    public int getAscension() {
        return ascension;
    }

    public String getDeckName() {
        return deckName;
    }

    public void setDeckName(final String deckName) {
        this.deckName = deckName;
        save();
    }

    /** Cuantos nodos llevas resueltos en este acto. */
    public int getCleared() {
        return clearedNodes.size();
    }

    /** Si la run esta ganada del todo. */
    public boolean isCompleted() {
        return act >= ACTS && actCleared();
    }

    private static void split(final String raw, final java.util.Collection<String> into) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (final String part : raw.split(";")) {
            if (!part.isBlank()) {
                into.add(part.trim());
            }
        }
    }

    @Override
    public String toString() {
        return "Ascenso[" + mode + " acto " + act + " vida " + life + "/" + maxLife
                + " creditos " + credits + " reliquias " + relics.size() + "]";
    }
}
