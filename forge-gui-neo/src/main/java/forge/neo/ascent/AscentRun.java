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
    //  La run de las maquetas, que no se guarda
    // ------------------------------------------------------------------

    /**
     * Encendido por {@link #demo}: a partir de ahi nada de Ascenso toca el
     * disco. Es para todo el proceso y no se apaga: las maquetas son procesos
     * de usar y tirar ({@code run.cmd ui --ascent-reward --snapshot=...}).
     */
    private static volatile boolean demoMode;
    /** La run de la maqueta, que en modo demo hace de {@code neo.properties}. */
    private static AscentRun demoRun;

    /**
     * La run de las maquetas ({@code --ascent-reward}, {@code --ascent-shop}...),
     * <b>sin guardar nada</b>.
     *
     * <p>Antes montaban la suya con {@link #begin}, que escribe en
     * {@code neo.properties} y deja un {@code .dck} en {@code decks/ascenso/}:
     * el jugador abria Ascenso y se encontraba una run a medias que nunca
     * empezo (24-09-2026, dos veces seguidas en una auditoria de capturas).
     * Y con una run de verdad era peor: la maqueta le regalaba creditos, le
     * quitaba vida y le cerraba un nodo, y eso SI se guardaba.
     *
     * <p>Ahora: si hay una run guardada se usa <b>una copia en memoria</b> (se
     * ve lo mismo que antes y no se toca); si no, se monta una nueva en
     * memoria. Desde aqui {@link #save} solo apunta la run en memoria,
     * {@link #current} devuelve esa y el mazo vive en
     * {@link AscentDecks#keepInMemory() memoria} — asi que da igual lo que
     * se pulse despues en la maqueta, incluido empezar otra run.
     */
    public static synchronized AscentRun demo(final Mode mode, final int maxLife) {
        final AscentRun saved = current();
        demoMode = true;
        AscentDecks.keepInMemory();
        demoRun = saved != null ? saved : begin(mode, 0, maxLife, null);
        return demoRun;
    }

    /** Si estamos en una maqueta: nada de Ascenso se guarda. */
    public static boolean isDemo() {
        return demoMode;
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
        if (demoMode) {
            return demoRun;
        }
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
        if (demoMode) {
            demoRun = this;
            return;
        }
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
        return begin(mode, ascension, maxLife, commander, AscentSeedDeck.NO_COLOURS);
    }

    /**
     * Igual, con los <b>colores</b> que pidio el jugador para el mazo de
     * Estandar.
     *
     * @param colours mascara de {@code MagicColor}, o
     *                {@link AscentSeedDeck#NO_COLOURS} para que salgan al azar.
     *                Se ignora en Commander, donde los manda el comandante
     */
    public static AscentRun begin(final Mode mode, final int ascension, final int maxLife,
                                  final forge.item.PaperCard commander, final byte colours) {
        final String name = deckNameFor(mode);
        final forge.deck.Deck deck =
                AscentSeedDeck.generate(mode, commander, name, ascension, colours);
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
        if (demoMode) {
            demoRun = null;
            return;
        }
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

    /** Que toca despues de resolver un nodo. Lo contesta {@link #advance()}. */
    public enum Step {
        /** Nada: el acto sigue y se vuelve al mapa de siempre. */
        CONTINUE,
        /** El jefe ha caido y quedan actos: el mapa de al lado es OTRO. */
        NEXT_ACT,
        /** No quedan actos. La run esta ganada y hay que cerrarla. */
        RUN_COMPLETED
    }

    /**
     * <b>Lo que el bucle de juego llama al volver de un nodo.</b>
     *
     * <p>Existe porque no existia, y eso dejaba el modo sin final: el jefe esta
     * en la ultima fila y <b>no sale ni un camino de el</b>, asi que al ganarlo
     * {@link #available()} devuelve la lista vacia y el mapa se queda en un
     * callejon sin salida. Y como {@code act} no pasaba nunca de 1,
     * {@link #isCompleted()} — que pide {@code act >= ACTS} — <b>no podia ser
     * cierto jamas</b>: ninguna run era ganable. Reportado jugando el
     * 17-09-2026 ("beat act 1, but it just goes back to the completed map").
     *
     * <p>⚠️ Esto es lo que se llama desde el juego. {@link #nextAct()} sigue
     * siendo publico porque los comprobadores necesitan plantarse en un acto
     * para montar su escenario, pero <b>el bucle no lo llama</b>: si lo hiciera
     * volveria a haber dos sitios que deciden cuando cambia el acto, y el fallo
     * de origen fue exactamente ese — el unico que lo llamaba era el
     * comprobador, o sea que verificaba los actos 2 y 3 ejecutando a mano el
     * paso que en el juego faltaba.
     */
    public Step advance() {
        if (!actCleared()) {
            return Step.CONTINUE;
        }
        return nextAct() ? Step.NEXT_ACT : Step.RUN_COMPLETED;
    }

    /**
     * Pasa al acto siguiente, o dice que la run esta ganada.
     *
     * <p>El progreso de nodos se vacia: cada acto es un mapa nuevo. Los
     * creditos, las reliquias y el mazo se quedan — eso es lo que hace que sea
     * una run y no tres partidas seguidas. La vida, desde el 19-09-2026,
     * <b>se cura entera</b>: ver el comentario de dentro.
     *
     * <p>⚠️ <b>Desde el juego se llama a {@link #advance()}</b>, no a esto.
     * Ver ahi por que.
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
        // Cura completa entre actos (19-09-2026). Es el unico momento en que
        // se regala la vida entera: acabas de tumbar al jefe, el mapa es otro
        // y el suelo de FIGHT_FLOOR ya cubre los combates de dentro del acto.
        // Va AQUI y no en advance(): los comprobadores usan nextAct() para
        // plantarse en un acto, y si solo curara uno de los dos caminos
        // volveriamos a tener dos cambios de acto que no hacen lo mismo.
        life = maxLife;
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

    /**
     * <b>Con cuanta vida empiezas un combate como poco</b>: la mitad de tu
     * maximo.
     *
     * <p>Decidido el 19-09-2026. Con la vida arrastrandose sin suelo, el modo
     * castigaba justo lo que Magic trata como un COSTE: fetchlands,
     * choquelands, mana pirexiano, <i>Necropotence</i>, <i>Toxic Deluge</i>.
     * Pagabas ocho vidas para ganar un duelo y las volvias a pagar en el
     * siguiente — y en Commander esas cartas son lo normal, no la excepcion.
     *
     * <p>Es un suelo y no una cura completa a proposito: con la cura completa
     * el descanso se queda sin decision, las reliquias de vida no sirven, los
     * eventos que cuestan vida salen gratis y las Ascensiones 2 y 8 dejan de
     * hacer nada. Por encima de la mitad todo eso sigue valiendo igual.
     *
     * <p>No toca {@link #life}: el suelo vale para EMPEZAR el duelo, y la vida
     * con la que se sale es la que se apunta. O sea que la vida de la run
     * sigue diciendo la verdad sobre lo que te han hecho.
     */
    private static final double FIGHT_FLOOR = 0.50;

    /**
     * La vida con la que empiezas el proximo combate: la tuya, o la mitad del
     * maximo si tienes menos.
     *
     * <p>Lo usan la partida ({@code AscentBattle}) y la ficha del mapa que dice
     * "tus N vidas contra sus M" — si la ficha leyera {@link #getLife()} a
     * secas, prometeria un duelo que no es el que se juega.
     */
    public int fightLife() {
        return Math.max(life, (int) Math.ceil(maxLife * FIGHT_FLOOR));
    }

    /**
     * Que parte de <b>lo que te falta</b> cura un descanso con Ascension 2 en
     * adelante. Sin Ascension se cura entero.
     *
     * <p>Antes era un 30% del maximo (20% con Ascension 2), y el suelo de
     * {@link #FIGHT_FLOOR} lo dejo sin sentido: con 10 de 40, curar 12 te
     * dejaba en 22 cuando el duelo iba a empezar en 20 de todas formas — la
     * cura real eran 2 vidas. Un nodo entero de la run reducido a eso.
     *
     * <p>Asi que la hoguera cura <b>entero</b> (decidido el 19-09-2026), y con
     * eso la decision vuelve a ser la que tenia que ser: <i>vida llena</i>
     * contra <i>una carta menos en el mazo</i>, que son dos cosas que de verdad
     * se comparan.
     *
     * <p>⚠️ Y por eso la Ascension 2 pasa a ser una <b>fraccion de lo que
     * falta</b> en vez de un porcentaje del maximo: su unico efecto es
     * "descansar cura menos", asi que con la cura entera se habria quedado en
     * un escalon de la escalera que no hace nada — y eso no da ningun error,
     * solo hace que subir de Ascension no signifique lo que dice. Sobre lo que
     * falta nunca deja a tope y siempre cura algo, que son las dos cosas que
     * hacian falta.
     */
    private static final double REST_HEAL_HARD = 0.50;

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
        final int missing = Math.max(0, maxLife - life);
        final int amount = ascension >= 2
                ? (int) Math.ceil(missing * REST_HEAL_HARD)
                : missing;
        return Math.max(1, amount);
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

    /**
     * <b>Cuantas cartas se mueven de golpe en este modo.</b> 1 en Estandar, 2 en
     * Commander.
     *
     * <h2>Por que existe, y por que en UN solo sitio</h2>
     *
     * <p>El mazo de Commander pasó a 60 cartas (§24.1) y eso, solo, hacía peor
     * el bucle: una carta de premio dentro de sesenta no se nota como dentro de
     * veinte, o sea que la run dejaba de <i>sentirse</i> mejorar — que es de lo
     * unico que vive el modo. Decision del autor (05-09-2026): <i>"en Commander
     * todo x2 respecto a cartas, que quitar quites 2 y anyadir anyadas 2"</i>.
     * Si el mazo es el doble, lo que se mueve tiene que ser el doble.
     *
     * <p>Y vive <b>aqui</b>, no repartido: lo consultan los premios
     * ({@link AscentRewards}), el descanso y la tienda ({@link AscentShop}).
     * Con el numero escrito en tres sitios, cambiarlo obligaria a acordarse de
     * los tres — y el que se quedara atras no daria ningun error, solo un modo
     * que se comporta distinto segun por que puerta entres.
     *
     * <p>⚠️ Lo que <b>no</b> escala son los <b>eventos</b>: sus textos dicen el
     * numero exacto ("quitas una carta de tu mazo"), y doblarlo por detras
     * dejaria la frase mintiendo — que es justo la regla que separa un evento de
     * una tragaperras (el plan de Ascenso).
     */
    public int cardBatch() {
        return cardBatch(mode);
    }

    /** Igual, sin tener una run delante. */
    public static int cardBatch(final Mode mode) {
        return mode == Mode.COMMANDER ? 2 : 1;
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
