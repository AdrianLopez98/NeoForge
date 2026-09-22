package forge.neo.deck;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

import forge.card.CardRules;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckSection;
import forge.game.GameFormat;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * El mazo que se esta editando, y todo lo que se puede preguntar sobre el.
 *
 * <p><b>Aqui no hay ni una regla de construccion escrita por nosotros.</b> Si
 * un mazo es legal lo dice {@link DeckFormat#getDeckConformanceProblem}; si una
 * carta puede ser comandante lo dice {@code isLegalCommander}; y que cartas
 * caben con un comandante dado — la identidad de color, que es donde todo el
 * mundo se equivoca — lo dice
 * {@code DeckFormat.isLegalCardForCommanderPredicate}. Nosotros preguntamos y
 * presentamos la respuesta.
 *
 * <p>Sin JavaFX a proposito: la pantalla es reemplazable, esto no. Ademas asi
 * se puede probar desde una linea de comandos.
 */
public final class DeckEditor {

    /** Cuantas casillas tiene la curva de mana: 0,1,2,...,6 y "7 o mas". */
    public static final int CURVE_SLOTS = 8;

    private final DeckContext format;
    private final Deck deck;
    private boolean dirty;

    /**
     * El indice del catalogo.
     *
     * <p>Con catalogo limitado (la aventura) es un indice sobre TU coleccion,
     * construido al abrir el editor. Con catalogo completo es el global, que se
     * comparte y se construye una sola vez.
     */
    private final CardIndex index;

    /**
     * Con que nombre esta guardado este mazo AHORA MISMO, o {@code null} si
     * todavia no lo esta.
     *
     * <p>Existe por el renombrado, que sin esto no renombraba nada: guardar es
     * {@code storage().add(deck)}, o sea que con el nombre nuevo se escribia un
     * fichero nuevo y <b>el viejo se quedaba donde estaba</b>. Reportado tal
     * cual: <i>"lo renombro y se crea una copia"</i>.
     *
     * <p>No vale mirar el nombre de ahora ni el de partida: hay que llevar la
     * cuenta de con cual esta el {@code .dck} en el disco, que cambia con cada
     * guardado.
     */
    private String savedAs;

    public DeckEditor(final DeckContext format, final Deck deck) {
        this.format = format;
        this.deck = deck;
        this.index = format.isLimited() ? CardIndex.of(format.pool())
                : format.poolFormat() != null ? CardIndex.of(poolCards(format.poolFormat()))
                : CardIndex.get();
        // Si el mazo ya existe con este nombre, es el que hay que sustituir al
        // renombrar. Y si no — un preconstruido de Forge, uno de internet, uno
        // nuevo — no hay nada que borrar nunca: se guarda y punto.
        if (deck != null && deck.getName() != null && format.storage().contains(deck.getName())) {
            this.savedAs = deck.getName();
        }
    }

    /**
     * El catalogo recortado a lo que vale en este {@link GameFormat} (Modern,
     * Pioneer, Pauper...).
     *
     * <p>Se mira {@code getFilterRules()}, no {@code getFilterPrinted()}: la
     * pregunta es "esta CARTA vale aqui" (¿se imprimio alguna vez en un set
     * legal, a una rareza legal?), no "esta IMPRESION concreta vale". Es lo que
     * hace que Pauper — el caso mas estricto — no se fije en de que edicion sale
     * la impresion unica de {@code getUniqueCards()}: si el Rayo se imprimio
     * alguna vez como comun, cuenta, aunque {@code CardIndex} este enseñando la
     * portada de una reimpresion rara.
     */
    private static List<PaperCard> poolCards(final GameFormat pool) {
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard card : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (pool.getFilterRules().test(card)) {
                out.add(card);
            }
        }
        return out;
    }

    /** Un mazo vacio con nombre. */
    public static DeckEditor createNew(final DeckContext format, final String name) {
        final DeckEditor editor = new DeckEditor(format, new Deck(name));
        editor.dirty = true;
        // Un mazo nuevo NO esta guardado, aunque por casualidad se llame igual
        // que uno que si lo esta. Sin esto, renombrarlo borraria el de aquel
        // nombre — un mazo que el jugador no ha abierto siquiera.
        editor.savedAs = null;
        return editor;
    }

    /**
     * Una copia editable de un mazo existente.
     *
     * <p>Se copia en vez de editar el original porque los mazos preconstruidos
     * que trae Forge no son nuestros para modificarlos, y porque asi "descartar
     * los cambios" es simplemente no guardar.
     */
    public static DeckEditor copyOf(final DeckContext format, final Deck source) {
        return new DeckEditor(format, new Deck(source, source.getName()));
    }

    // ---------------------------------------------------------------
    // Consulta

    public Deck getDeck() {
        return deck;
    }

    public DeckContext getFormat() {
        return format;
    }

    /** Si el catalogo es un pool cerrado (tu coleccion) en vez de todo Magic. */
    public boolean isLimited() {
        return format.isLimited();
    }

    /** Cuantas copias de esta carta TIENES. Sin limite fuera de la aventura. */
    public int owned(final PaperCard card) {
        return format.owned(card);
    }

    public String getName() {
        return deck.getName();
    }

    public void setName(final String name) {
        if (name != null && !name.isBlank() && !name.equals(deck.getName())) {
            deck.setName(name.trim());
            dirty = true;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    // ---------------------------------------------------------------
    // La funda del mazo

    /**
     * La funda de este mazo (el identificador de un {@code LookItem}), o null
     * si no lleva ninguna y hay que usar la de Personalizar.
     *
     * <p>Vive en las <b>etiquetas</b> del mazo, que el motor ya guarda y relee
     * del {@code .dck}: asi la funda viaja con el mazo. Ver
     * {@code NeoLook.setDeckSleeve}.
     */
    public String getSleeve() {
        return forge.neo.look.NeoLook.deckSleeveId(deck);
    }

    /** Le pone funda al mazo, o se la quita con {@code null}. */
    public void setSleeve(final String id) {
        if (java.util.Objects.equals(id, getSleeve())) {
            return;
        }
        forge.neo.look.NeoLook.setDeckSleeve(deck, id);
        dirty = true;
    }

    /** Cartas del mazo principal, sin contar el comandante. */
    public int mainCount() {
        return deck.getMain().countAll();
    }

    /**
     * Cuantas copias de esta carta hay en el mazo, <b>contando como cuenta el
     * motor</b>: por NOMBRE y en TODAS las zonas.
     *
     * <p>Las dos mitades de esa frase salieron del mismo fallo, y ninguna es un
     * detalle. {@code DeckFormat.getDeckConformanceProblem} agrupa
     * {@code getAllCardsInASinglePool()} — principal <b>mas banquillo</b> mas
     * zona de mando — por {@code getNormalizedName}. Esto contaba solo el
     * principal y ademas por <b>impresion</b>, asi que decia 0 en los dos casos
     * en que el motor dice 4:
     *
     * <ul>
     *   <li>Un preconstruido de la aventura llega con banquillo (el de
     *       <i>Cavalcade Charge</i> trae 3 Experimental Frenzy ahi). El editor
     *       no lo veia, dejaba meter 3 mas en el principal, y el mazo se
     *       quedaba con 6 y <b>sin poder jugarse</b> — reportado tal cual.</li>
     *   <li>El catalogo ensenya UNA impresion por carta, que no tiene por que
     *       ser la que ya esta en el mazo. Contando por impresion, cuatro
     *       Montanyas de M20 y veinte de ECL eran cero copias cada vez.</li>
     * </ul>
     *
     * <p>Y por lo mismo tapaba el otro techo, el de la coleccion: se podian
     * meter mas copias de las que tienes con solo cambiar de arte.
     */
    public int countOf(final PaperCard card) {
        return card == null ? 0 : countByName(card.getName());
    }

    /** Si "Solo lo que cabe" sale encendido al abrir (ver DeckContext). */
    public boolean onlyFitsByDefault() {
        return format.onlyFitsByDefault();
    }

    /** Lo que el contexto anyade al menu de una carta del catalogo (ver DeckContext). */
    public List<DeckContext.Action> contextActions(final PaperCard card) {
        return format.catalogueActions(card, countOf(card));
    }

    /** Todas las copias de ese nombre en el mazo, mire donde mire el motor. */
    private int countByName(final String name) {
        final String key = normalized(name);
        int sum = 0;
        for (final Map.Entry<DeckSection, CardPool> section : deck) {
            for (final Map.Entry<PaperCard, Integer> e : section.getValue()) {
                if (normalized(e.getKey().getName()).equals(key)) {
                    sum += e.getValue();
                }
            }
        }
        return sum;
    }

    /**
     * El nombre con el que el motor agrupa esta carta.
     *
     * <p>Es lo que hace que la mitad de una aventura y su hechizo, o una carta
     * con nombre de ambientacion, cuenten como la misma. Lo decide
     * {@code CardDb.getNormalizedName}; aqui solo se pregunta.
     */
    private static String normalized(final String name) {
        if (name == null) {
            return "";
        }
        return forge.StaticData.instance().getCommonCards().getNormalizedName(name);
    }

    public List<PaperCard> commanders() {
        return deck.getCommanders();
    }

    /**
     * Rellena el mazo principal con uno que genera el motor PARA el
     * comandante actual, sustituyendo lo que hubiera.
     *
     * <p>{@code DeckgenUtil.generateRandomCommanderDeck} es la misma fachada
     * que usaría un generador de mazo de rival; aquí el comandante ya viene
     * fijado por el jugador, no lo elige el motor. Con {@code isCardGen=true}
     * usa los mazos genéticos de IA
     * ({@code res/geneticaidecks}) para que el resultado tenga algo de
     * sinergia real y no sea 99 cartas sueltas del mismo color.
     *
     * <p>No comprueba nada de legalidad aparte: {@code generateRandomCommanderDeck}
     * ya filtra por la identidad de color y el pozo del formato, así que lo
     * que sale es jugable de por sí — pero se mete con {@link #addAnyway} y
     * no con {@link #add}, igual que una lista pegada, por si el motor alguna
     * vez se pasa de copias.
     *
     * @return cuantas cartas ha metido, o -1 si no hay comandante o el motor
     *         no ha podido generar nada
     */
    public int generateForCommander() {
        // mainCommander() y no commanders().get(0): en Oathbreaker el hechizo
        // insignia esta en la misma zona y puede salir el primero, y generar el
        // mazo "para" un instantaneo no tiene ningun sentido.
        final PaperCard head = mainCommander();
        if (head == null) {
            return -1;
        }
        final Deck generated;
        try {
            generated = forge.deck.DeckgenUtil.generateRandomCommanderDeck(
                    head, deckFormat(), false, true);
        } catch (final RuntimeException e) {
            return -1;
        }
        if (generated == null) {
            return -1;
        }
        deck.getOrCreate(DeckSection.Main).clear();
        int added = 0;
        for (final Map.Entry<PaperCard, Integer> e : generated.getMain()) {
            added += addAnyway(e.getKey(), e.getValue());
        }
        dirty = true;
        return added;
    }

    /** El formato de construccion que aplica, que sale del {@code GameType}. */
    public DeckFormat deckFormat() {
        return format.deckFormat();
    }

    /**
     * Que le falta al mazo para ser legal, o null si ya lo es.
     *
     * <p>Lo contesta el motor entero: numero de cartas, copias de mas, cartas
     * prohibidas, identidad de color del comandante. No lo comprobamos nosotros.
     */
    public String problem() {
        // Traducido: el motor compone estas frases a mano y NUNCA pasa por
        // Localizer, asi que llegan en ingles en los once idiomas. Ver
        // DeckProblem; lo que no se reconoce sale tal cual.
        return DeckProblem.translate(format.conformanceProblem(deck));
    }

    /** Si el formato juega con comandante. */
    public boolean usesCommander() {
        return deckFormat().hasCommander();
    }

    /**
     * Si el formato tiene <b>dos</b> huecos en la zona de mando: el comandante
     * y un <i>hechizo insignia</i>.
     *
     * <p>Solo Oathbreaker ({@code DeckFormat.hasSignatureSpell}). Todo lo que
     * cuelga de aqui es una rama aparte a proposito: Commander, Brawl y Tiny
     * Leaders no entran nunca, asi que no hay forma de que esto les cambie
     * nada. El motor ya sabe de los dos huecos — {@code Deck.getOathbreaker} y
     * {@code Deck.getSignatureSpell} los separan leyendo la misma zona — y lo
     * unico que faltaba era que la interfaz supiera rellenar el segundo.
     */
    public boolean usesSignatureSpell() {
        return deckFormat().hasSignatureSpell();
    }

    /**
     * Si esta carta vale como hechizo insignia <b>en este formato</b>.
     *
     * <p>{@code canBeSignatureSpell} es instantaneo o conjuro, y lo decide el
     * motor. Fuera de Oathbreaker devuelve siempre false, que es lo que
     * mantiene a Commander sin enterarse de que esto existe.
     */
    public boolean isSignatureSpell(final PaperCard card) {
        return card != null && usesSignatureSpell()
                && card.getRules() != null && card.getRules().canBeSignatureSpell();
    }

    /** El hechizo insignia que tiene puesto el mazo, o null. */
    public PaperCard signatureSpell() {
        return usesSignatureSpell() ? deck.getSignatureSpell() : null;
    }

    /**
     * El comandante de verdad, separado del hechizo.
     *
     * <p>En Oathbreaker {@link #commanders()} devuelve los dos: el planeswalker
     * y el hechizo viven en la MISMA zona ({@code DeckSection.Commander}), que
     * es como lo guarda Forge. Quien quiera "el comandante" a secas tiene que
     * pedirlo asi, o acaba ensenyando un instantaneo donde va la carta grande.
     */
    public PaperCard mainCommander() {
        if (usesSignatureSpell()) {
            return deck.getOathbreaker();
        }
        final List<PaperCard> cmd = commanders();
        return cmd.isEmpty() ? null : cmd.get(0);
    }

    /**
     * La curva de mana del mazo principal.
     *
     * <p>Las tierras NO cuentan: no se lanzan, y meterlas en la casilla del 0
     * aplasta el resto de la grafica y no dice nada.
     */
    public int[] manaCurve() {
        final int[] curve = new int[CURVE_SLOTS];
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final CardRules rules = e.getKey().getRules();
            if (rules.getType().isLand()) {
                continue;
            }
            final int cmc = Math.min(rules.getManaCost().getCMC(), CURVE_SLOTS - 1);
            curve[cmc] += e.getValue();
        }
        return curve;
    }

    /** Cuantas cartas hay de cada grupo, en el orden en que se ensenyan. */
    public Map<String, Integer> typeCounts() {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final String group : GROUPS) {
            counts.put(group, 0);
        }
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final String group = groupOf(e.getKey());
            counts.merge(group, e.getValue(), Integer::sum);
        }
        counts.values().removeIf(v -> v == 0);
        return counts;
    }

    /**
     * Cuantos simbolos de cada color pide el mazo.
     *
     * <p>Se cuentan los <b>simbolos del coste</b>, no las cartas: un mazo con
     * diez cartas que piden {G} y una que pide {2}{G}{G}{G} no es "once cartas
     * verdes", es un mazo que necesita mucho mas verde del que parece. Es lo que
     * de verdad decide cuantas tierras de cada color hacen falta.
     *
     * <p>El orden es el de Magic: blanco, azul, negro, rojo, verde.
     */
    public int[] colourPips() {
        final int[] pips = new int[5];
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            countPips(pips, e.getKey(), e.getValue());
        }
        for (final PaperCard c : commanders()) {
            countPips(pips, c, 1);
        }
        return pips;
    }

    private static void countPips(final int[] pips, final PaperCard card, final int copies) {
        final forge.card.mana.ManaCost cost = card.getRules().getManaCost();
        if (cost == null) {
            return;
        }
        pips[0] += cost.getShardCount(forge.card.mana.ManaCostShard.WHITE) * copies;
        pips[1] += cost.getShardCount(forge.card.mana.ManaCostShard.BLUE) * copies;
        pips[2] += cost.getShardCount(forge.card.mana.ManaCostShard.BLACK) * copies;
        pips[3] += cost.getShardCount(forge.card.mana.ManaCostShard.RED) * copies;
        pips[4] += cost.getShardCount(forge.card.mana.ManaCostShard.GREEN) * copies;
    }

    /**
     * El coste medio de lo que se lanza.
     *
     * <p>Sin tierras, por lo mismo que la curva: no se lanzan y meterlas
     * hundiria la media hasta un numero que no significa nada.
     */
    public double averageCmc() {
        int total = 0;
        int count = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                continue;
            }
            total += e.getKey().getRules().getManaCost().getCMC() * e.getValue();
            count += e.getValue();
        }
        return count == 0 ? 0 : total / (double) count;
    }

    /** Cuantas tierras lleva el mazo. Es el numero que mas se mira. */
    public int landCount() {
        int lands = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                lands += e.getValue();
            }
        }
        return lands;
    }

    /** Identificadores de grupo. No se ensenyan: se traducen al pintar. */
    public static final String CREATURES = "creatures";
    /** @see #CREATURES */
    public static final String SPELLS = "spells";
    /** @see #CREATURES */
    public static final String ARTIFACTS = "artifacts";
    /** @see #CREATURES */
    public static final String ENCHANTMENTS = "enchantments";
    /** @see #CREATURES */
    public static final String PLANESWALKERS = "planeswalkers";
    /** @see #CREATURES */
    public static final String BATTLES = "battles";
    /** @see #CREATURES */
    public static final String LANDS = "lands";
    /** @see #CREATURES */
    public static final String OTHER = "other";

    /**
     * Los grupos en los que se parte la lista del mazo, en orden de lectura.
     *
     * <p>Son <b>identificadores</b>, no textos de pantalla: se usan como clave
     * de mapa y para comparar. Lo que se ensenya lo da {@link #groupLabel}.
     */
    public static final String[] GROUPS = {
        CREATURES, SPELLS, ARTIFACTS, ENCHANTMENTS,
        PLANESWALKERS, BATTLES, LANDS, OTHER,
    };

    /** Como se llama un grupo en pantalla, en el idioma elegido. */
    public static String groupLabel(final String group) {
        return group == null ? "" : forge.neo.NeoText.get("group." + group);
    }

    /**
     * En que grupo cae una carta.
     *
     * <p>El orden de las preguntas importa: un "Artifact Creature" es una
     * criatura, y una "Artifact Land" es una tierra. Se pregunta de lo mas
     * especifico a lo mas general, que es como lo lee un jugador.
     */
    public static String groupOf(final PaperCard card) {
        final forge.card.CardType type = card.getRules().getType();
        if (type.isLand()) {
            return LANDS;
        }
        if (type.isCreature()) {
            return CREATURES;
        }
        if (type.isPlaneswalker()) {
            return PLANESWALKERS;
        }
        if (type.isBattle()) {
            return BATTLES;
        }
        if (type.isInstant() || type.isSorcery()) {
            return SPELLS;
        }
        if (type.isArtifact()) {
            return ARTIFACTS;
        }
        if (type.isEnchantment()) {
            return ENCHANTMENTS;
        }
        return OTHER;
    }

    /** Las cartas del mazo de un grupo, ordenadas por coste y nombre. */
    public List<Map.Entry<PaperCard, Integer>> cardsInGroup(final String group) {
        final List<Map.Entry<PaperCard, Integer>> out = new ArrayList<>();
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (groupOf(e.getKey()).equals(group)) {
                out.add(e);
            }
        }
        out.sort(Comparator
                .comparingInt((Map.Entry<PaperCard, Integer> e) ->
                        e.getKey().getRules().getManaCost().getCMC())
                .thenComparing(e -> e.getKey().getName()));
        return out;
    }

    // ---------------------------------------------------------------
    // Edicion

    /**
     * Por que NO se puede meter esta carta, o null si si se puede.
     *
     * <p>Son las tres razones por las que un mazo deja de ser legal, y las tres
     * las contesta el motor:
     *
     * <ol>
     *   <li><b>No es legal en el formato</b> — prohibida, o fuera del pool de
     *       cartas del formato ({@code isLegalCard}).</li>
     *   <li><b>No respeta la identidad de color del comandante</b>. Es el error
     *       clasico de Commander y no se ve a simple vista: una carta con maná
     *       hibrido o con un simbolo en el texto de reglas cuenta
     *       ({@code isLegalCardForCommanderPredicate}).</li>
     *   <li><b>Ya tienes el maximo de copias</b>: 1 en Commander, 4 en Estandar
     *       — salvo las tierras basicas y las cartas que dicen en su texto que
     *       puedes llevar las que quieras, o un numero concreto como los Seven
     *       Dwarves. Todo eso lo resuelve {@code getMaxCardCopies(card)}.</li>
     * </ol>
     *
     * <p>Comprobarlo AL ANYADIR y no solo al guardar es la diferencia entre un
     * editor que te ayuda y uno que te deja montar un mazo invalido y te lo
     * cuenta al final.
     */
    public String rejectionReason(final PaperCard card) {
        if (card == null) {
            return forge.neo.NeoText.get("reject.noCard");
        }
        final DeckFormat df = deckFormat();

        if (!inCardPool(card)) {
            return forge.neo.NeoText.get("reject.notLegal",
                    forge.neo.card.CardText.nameOf(card), format.getLabel());
        }

        final GameFormat pool = format.poolFormat();
        if (pool != null && !pool.getFilterRules().test(card)) {
            // Misma frase que "no es legal en el formato": para quien juega da
            // igual si la razon es la regla de construccion o el pozo de
            // cartas, las dos dicen lo mismo — esta carta no vale aqui.
            return forge.neo.NeoText.get("reject.notLegal",
                    forge.neo.card.CardText.nameOf(card), format.getLabel());
        }

        final Predicate<PaperCard> identity = identityFilter();
        if (identity != null && !identity.test(card)) {
            return forge.neo.NeoText.get("reject.identity",
                    forge.neo.card.CardText.nameOf(card));
        }

        final int max = df.getMaxCardCopies(card);
        if (max != Integer.MAX_VALUE && countOf(card) >= max) {
            return max == 1
                    ? forge.neo.NeoText.get("reject.singleton",
                            format.getLabel(), forge.neo.card.CardText.nameOf(card))
                    : forge.neo.NeoText.get("reject.maxCopies",
                            max, forge.neo.card.CardText.nameOf(card), format.getLabel());
        }

        // Y el otro techo, que no es una regla de Magic: las que TIENES. En la
        // aventura no se monta con lo que existe, se monta con lo que has
        // ganado y comprado.
        final int have = format.owned(card);
        // Por el mismo motivo que en roomFor: en limitado lo colocado es lo que
        // hay en el PRINCIPAL, porque el pozo ya cuenta la banda.
        final int placed = format.poolInSideboard() ? countInMain(card) : countOf(card);
        if (have != Integer.MAX_VALUE && placed >= have) {
            return have == 0
                    ? forge.neo.NeoText.get("reject.notOwned",
                            forge.neo.card.CardText.nameOf(card))
                    : forge.neo.NeoText.get(have == 1 ? "reject.ownOne" : "reject.ownSome",
                            have, forge.neo.card.CardText.nameOf(card));
        }
        return null;
    }

    /**
     * Si esta carta esta en el pozo de cartas del formato.
     *
     * <p>Un solo sitio para las cuatro preguntas que hay que contestar igual
     * — rechazar al anyadir, marcar lo que sobra en el mazo, quitarlo y filtrar
     * el catalogo —, porque contestarlas distinto deja al jugador con un mazo
     * que se puede montar pero no guardar. Y porque hay un contexto que dice
     * que no, la Aventura: ver {@link DeckContext#enforcesCardPool()}.
     */
    private boolean inCardPool(final PaperCard card) {
        return !format.enforcesCardPool() || deckFormat().isLegalCard(card);
    }

    /**
     * Cuantas copias mas de esta carta caben, o {@code Integer.MAX_VALUE}.
     *
     * <p><b>Los dos techos no se miden sobre lo mismo, y juntarlos rompia el
     * limitado.</b>
     *
     * <ul>
     *   <li>El del <b>formato</b> ("cuatro copias de cada carta") lo mide el
     *       motor sobre <b>todas las zonas</b>: cuatro Rayos en el principal y
     *       dos en la banda son seis Rayos para
     *       {@code getDeckConformanceProblem}. Ahi {@link #countOf} es lo
     *       correcto.</li>
     *   <li>El de <b>lo que tienes</b> mide cuantas te quedan por colocar. En
     *       la aventura, las que estan en el mazo ya estan usadas y
     *       {@code countOf} tambien vale. Pero en <b>limitado</b> el pozo
     *       {@code owned} <b>YA INCLUYE</b> las que estan en el mazo y en la
     *       banda - es una sola pila repartida en dos (ver
     *       {@link DeckContext#poolInSideboard}) -, asi que restarle
     *       {@code countOf} da <b>cero para todas las cartas, siempre</b>.</li>
     * </ul>
     *
     * <p>Y cero quiere decir que el catalogo de un sellado <b>no dejaba meter
     * ni una carta</b>: se podia vaciar el mazo y no habia forma de volver a
     * montarlo. Encontrado probando el sellado en Android (12-09-2026), justo
     * despues del otro fallo de la misma pila.
     */
    public int roomFor(final PaperCard card) {
        final int byRules = deckFormat().getMaxCardCopies(card);
        final int roomByRules = byRules == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : Math.max(0, byRules - countOf(card));

        final int owned = format.owned(card);
        // En limitado, lo que esta en la banda es JUSTO lo que se puede mover al
        // mazo: lo colocado es lo que hay en el principal, y nada mas.
        final int used = format.poolInSideboard() ? countInMain(card) : countOf(card);
        final int roomByOwned = owned == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : Math.max(0, owned - used);

        return Math.min(roomByRules, roomByOwned);
    }

    /**
     * Mete copias en el mazo, sin pasarse del limite.
     *
     * @return cuantas ha metido de verdad; 0 si no cabia ninguna
     */
    public int add(final PaperCard card, final int amount) {
        if (card == null || amount <= 0 || rejectionReason(card) != null) {
            return 0;
        }
        final int fit = Math.min(amount, roomFor(card));
        if (fit <= 0) {
            return 0;
        }
        deck.getOrCreate(DeckSection.Main).add(card, fit);
        takeFromPool(card, fit);
        dirty = true;
        return fit;
    }

    /**
     * Mete copias <b>sin preguntar si caben</b>.
     *
     * <p>Solo para importar una lista de fuera. La regla normal — no dejar
     * entrar lo que no cabe — es la correcta cuando montas el mazo carta a
     * carta: el editor te ayuda y te ahorra el disgusto. Pero al pegar una
     * lista entera es justo al reves, y salio de importar un mazo de verdad:
     * se colaron cuarenta y seis cartas, se rechazaron treinta y ocho <b>y no
     * quedaba forma de saber cuales</b> ni de recuperarlas sin volver a
     * buscarlas a mano una por una.
     *
     * <p>Asi que la lista entra entera y lo que no cabe se ve <b>puesto en el
     * mazo</b>, que es donde se puede mirar, cambiar de comandante o quitar de
     * golpe. Nada se escapa por eso: {@link #blockingProblem()} ya impide
     * guardarlo y {@link #isPlayable()} impide jugarlo mientras siga habiendo
     * algo que no cabe.
     *
     * @return cuantas ha metido
     */
    public int addAnyway(final PaperCard card, final int amount) {
        if (card == null || amount <= 0) {
            return 0;
        }
        deck.getOrCreate(DeckSection.Main).add(card, amount);
        takeFromPool(card, amount);
        dirty = true;
        return amount;
    }

    public void remove(final PaperCard card, final int amount) {
        if (card == null || amount <= 0) {
            return;
        }
        final int had = deck.getMain().count(card);
        deck.getMain().remove(card, amount);
        backToPool(card, Math.min(amount, had));
        dirty = true;
    }

    /**
     * Lo que ENTRA en el mazo sale de la banda, cuando la banda es el pozo.
     *
     * <p>Solo en limitado ({@link DeckContext#poolInSideboard}). Ahí el pool es
     * una sola pila repartida entre las dos zonas, y si el mazo crece sin que
     * la banda mengue, el pool <b>crece solo</b>: la próxima vez que se abra el
     * editor habrá más cartas de las que salieron de los sobres.
     *
     * <p>Se quita lo que HAYA, nunca más: las tierras básicas del catálogo no
     * están en la banda (no salieron de ningún sobre) y ahí esto no hace nada,
     * que es justo lo correcto.
     */
    private void takeFromPool(final PaperCard card, final int amount) {
        if (card == null || amount <= 0 || !format.poolInSideboard()) {
            return;
        }
        final CardPool side = deck.get(DeckSection.Sideboard);
        if (side == null) {
            return;
        }
        final int there = side.count(card);
        if (there > 0) {
            side.remove(card, Math.min(amount, there));
        }
    }

    /**
     * Y lo que SALE del mazo vuelve a la banda, por lo mismo.
     *
     * <p>Sin esto, quitar una carta del mazo de un sellado <b>la borra del
     * evento</b>: no vuelve al catálogo, no se puede volver a meter, y el pool
     * se queda con una carta menos para siempre. Sin ningún error.
     *
     * <p>Las básicas <b>no</b> vuelven: las pone {@code SealedDeckBuilder} al
     * montar el mazo y nunca estuvieron en el pool. Devolverlas lo iría
     * inflando con tierras a cada edición.
     */
    private void backToPool(final PaperCard card, final int amount) {
        if (card == null || amount <= 0 || !format.poolInSideboard()) {
            return;
        }
        if (card.getRules() != null && card.getRules().getType().isBasicLand()) {
            return;
        }
        deck.getOrCreate(DeckSection.Sideboard).add(card, amount);
    }

    /**
     * Nombra un comandante.
     *
     * <p>Devuelve false si esa carta no puede serlo en este formato — lo decide
     * {@code DeckFormat.isLegalCommander}, que ya sabe que Oathbreaker quiere un
     * planeswalker y Brawl un legendario del formato.
     *
     * <p>La carta se saca del mazo principal si estaba: el comandante vive en su
     * propia zona y contarlo dos veces haria que el mazo nunca fuera legal.
     */
    public boolean setCommander(final PaperCard card) {
        // Primero: que el formato TENGA zona de mando.
        //
        // El motor no contesta eso. {@code isLegalCommander} responde a otra
        // pregunta — "puede esta carta ser comandante" — y para Estandar cae al
        // caso general y dice que si a cualquier legendaria, porque a nadie se
        // le ocurre preguntarselo ahi. La pantalla no ensenya el boton, pero
        // por aqui se entra tambien al pegar una lista: una decklist de
        // Commander pegada en un mazo de Estandar le colaba el comandante, y de
        // ahi salia un .dck de construido con zona de mando dentro.
        if (card == null || !usesCommander()) {
            return false;
        }
        // Oathbreaker tiene DOS huecos en la misma zona. isLegalCommander solo
        // contesta por el primero (para ese formato es canBeOathbreaker, o sea
        // planeswalkers), asi que sin esta segunda puerta el hechizo insignia
        // no habia forma de ponerlo: ni desde el catalogo ni pegando una lista.
        // El mazo se quedaba en "is missing a signature spell" para siempre.
        final boolean spell = isSignatureSpell(card);
        if (!spell && !deckFormat().isLegalCommander(card.getRules())) {
            return false;
        }
        // Y tienes que TENERLO. Por el catalogo no puede colarse — solo ensenya
        // lo tuyo — pero por la importacion de una lista si, y un mazo con un
        // comandante que no tienes no se puede jugar.
        if (format.owned(card) <= 0) {
            return false;
        }
        deck.getMain().remove(card, deck.getMain().count(card));
        final CardPool pool = deck.getOrCreate(DeckSection.Commander);
        if (usesSignatureSpell()) {
            // Aqui NO se vacia la zona: se sustituye la carta DEL MISMO HUECO.
            // Vaciarla (que es lo que hace la rama de abajo) borraria el
            // planeswalker al elegir el hechizo, y al reves. Es lo mismo que
            // hace la GUI vieja en CEditorConstructed.onAddItems: compara
            // canBeOathbreaker() de la nueva con el de la que ya esta.
            final PaperCard previous = spell ? deck.getSignatureSpell() : deck.getOathbreaker();
            if (previous != null) {
                pool.remove(previous, pool.count(previous));
            }
        } else if (!pool.isEmpty() && !acceptsPartner(card)) {
            // Un comandante nuevo sustituye al anterior salvo que sean
            // companeros; de las parejas ya sabe el motor, asi que se deja
            // anyadir un segundo solo si el mazo sigue siendo conforme con los
            // dos.
            pool.clear();
        }
        pool.add(card, 1);
        dirty = true;
        return true;
    }

    /**
     * Las cartas del mazo que ya NO caben.
     *
     * <p>Pasa al cambiar de comandante: el mazo estaba bien y de repente medio
     * mazo se sale de la identidad de color. El motor lo diria al guardar en una
     * sola linea; poder ensenyar la lista y ofrecer quitarlas es la diferencia
     * entre un aviso y una solucion.
     */
    public List<PaperCard> illegalCards() {
        final List<PaperCard> out = new ArrayList<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        final Predicate<PaperCard> identity = identityFilter();
        final GameFormat pool = format.poolFormat();

        // 1. El principal: todo lo que puede estar mal, y en el orden en que el
        //    jugador ve su mazo.
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final PaperCard card = e.getKey();
            if (!seen.add(normalized(card.getName()))) {
                continue;
            }
            if (!inCardPool(card)
                    || (pool != null && !pool.getFilterRules().test(card))
                    || (identity != null && !identity.test(card))
                    || overCopies(card) || overOwned(card)) {
                out.add(card);
            }
        }

        // 2. Y el banquillo, pero SOLO por el limite de copias del formato.
        //
        //    Que ahi haya copias de mas deja el mazo injugable — el motor las
        //    suma — y sin mirarlo el jugador se quedaba encerrado: las copias
        //    que sobran estan en una zona que ninguna pantalla ensenya.
        final CardPool side = deck.get(DeckSection.Sideboard);
        if (side != null) {
            for (final Map.Entry<PaperCard, Integer> e : side) {
                final PaperCard card = e.getKey();
                if (!seen.add(normalized(card.getName()))) {
                    continue;
                }
                if (overCopies(card)) {
                    out.add(card);
                }
            }
        }
        return out;
    }

    /**
     * Se pasa del limite de copias del formato.
     *
     * <p>Se mide como lo mide el motor: <b>todas las zonas</b>. Cuatro Rayos en
     * el principal y dos en el banquillo son seis Rayos para
     * {@code getDeckConformanceProblem}, y el mazo no se puede jugar.
     */
    private boolean overCopies(final PaperCard card) {
        final int max = deckFormat().getMaxCardCopies(card);
        return max != Integer.MAX_VALUE && countOf(card) > max;
    }

    /**
     * Lleva mas copias de las que TIENES.
     *
     * <p>Este techo no es una regla de Magic — es el de la aventura y el del
     * draft — y por eso se mide <b>solo sobre el mazo principal</b>, que es lo
     * que el jugador pone. El banquillo de un mazo montado por el motor al
     * acabar un draft trae los ultimos picks de cada sobre, <b>que casi nunca
     * son tuyos</b>: contarlos aqui marcaba veinte cartas que nadie ha puesto e
     * impedia guardar un mazo perfectamente legal.
     */
    private boolean overOwned(final PaperCard card) {
        final int have = format.owned(card);
        return have != Integer.MAX_VALUE && countInMain(card) > have;
    }

    /** Copias de esa carta (por nombre) en el mazo principal. */
    private int countInMain(final PaperCard card) {
        final String key = normalized(card.getName());
        int sum = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (normalized(e.getKey().getName()).equals(key)) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    /**
     * Lo que impide GUARDAR el mazo, o null si se puede guardar.
     *
     * <p>Hay dos clases muy distintas de "mazo no legal", y tratarlas igual seria
     * un error:
     *
     * <ul>
     *   <li><b>Incompleto</b> — le faltan cartas para llegar a 100. Eso es un
     *       trabajo a medias, no una trampa: se guarda sin rechistar, porque
     *       perder cuarenta cartas ya elegidas por salir de la pantalla seria
     *       mucho peor que guardar algo que todavia no se puede jugar.</li>
     *   <li><b>Ilegal</b> — lleva cartas prohibidas, fuera de la identidad de
     *       color del comandante, o mas copias de las permitidas. Eso si se
     *       bloquea: es lo que nunca deberia llegar a una partida.</li>
     * </ul>
     *
     * <p>Jugarlo es otra cosa: para eso hace falta {@link #problem()} a null.
     */
    public String blockingProblem() {
        final List<PaperCard> bad = illegalCards();
        if (bad.isEmpty()) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append(bad.size() == 1
                ? forge.neo.NeoText.get("deck.illegal.one")
                : forge.neo.NeoText.get("deck.illegal.many", bad.size()));
        final int shown = Math.min(10, bad.size());
        for (int i = 0; i < shown; i++) {
            sb.append(System.lineSeparator()).append("  ").append(bad.get(i).getName());
        }
        if (bad.size() > shown) {
            sb.append(System.lineSeparator())
              .append("  ").append(forge.neo.NeoText.get("list.more", bad.size() - shown));
        }
        return sb.toString();
    }

    /** Si el mazo se puede jugar tal cual (legal y completo). */
    public boolean isPlayable() {
        return problem() == null;
    }

    /**
     * Quita del mazo todo lo que ya no cabe.
     *
     * <p>De las que solo se pasan de copias se quitan las sobrantes, no la carta
     * entera: llevar cinco Rayos en Estandar es un error de cantidad, no de
     * eleccion.
     *
     * @return cuantas copias se han quitado en total
     */
    public int removeIllegal() {
        int removed = 0;
        final DeckFormat df = deckFormat();
        final Predicate<PaperCard> id = identityFilter();
        for (final PaperCard card : illegalCards()) {
            if (!inCardPool(card) || (id != null && !id.test(card))) {
                // Prohibida o fuera de la identidad de color: fuera entera.
                removed += dropCopies(card, countOf(card));
                continue;
            }
            // Los dos techos se arreglan por separado porque no se miden sobre
            // lo mismo. Primero el de la coleccion, que es del PRINCIPAL — ahi
            // hay que quitar del principal, es donde sobran.
            final int have = format.owned(card);
            if (have != Integer.MAX_VALUE) {
                removed += dropFromMain(card, Math.max(0, countInMain(card) - have));
            }
            // Y luego el del formato, que cuenta todo: ahi se empieza por el
            // banquillo, que es lo que el jugador no ve.
            final int max = df.getMaxCardCopies(card);
            if (max != Integer.MAX_VALUE) {
                removed += dropCopies(card, Math.max(0, countOf(card) - max));
            }
        }
        return removed;
    }

    /**
     * Quita N copias de esa carta, <b>del banquillo primero</b>.
     *
     * <p>El orden importa y no es cosmetico: el banquillo no se ve en ninguna
     * pantalla y en un duelo de la aventura no se usa nunca (cada duelo es UNA
     * partida). Quitando del principal se le vaciaria al jugador la parte que
     * si esta mirando para dejar intacta la que no puede ni abrir.
     *
     * <p>Se quita por NOMBRE, porque por nombre es como se ha contado de mas:
     * las copias sobrantes pueden ser de otra impresion.
     */
    /**
     * Quita N copias de esa carta, por nombre y solo del mazo principal.
     *
     * <p>En limitado vuelven a la banda, igual que con {@link #remove}: esto
     * saca del principal cartas de las que hay más copias de las que tienes, y
     * esas copias <b>existen</b> - están de más en el mazo, no de más en el
     * mundo.
     */
    private int dropFromMain(final PaperCard card, final int amount) {
        final int gone = drop(card, amount, new DeckSection[] {DeckSection.Main});
        backToPool(card, gone);
        return gone;
    }

    /**
     * Quita N copias de donde sea.
     *
     * <p>Estas <b>no</b> vuelven al pozo, y es la diferencia con
     * {@link #dropFromMain}: aquí se quitan copias que se pasan del límite del
     * formato o cartas prohibidas, o sea copias que no deberían existir.
     * Devolverlas a la banda sería deshacer la limpieza que acaba de pedir el
     * jugador.
     */
    private int dropCopies(final PaperCard card, final int amount) {
        return drop(card, amount, new DeckSection[] {DeckSection.Sideboard, DeckSection.Main});
    }

    /** Quita N copias por NOMBRE, recorriendo las zonas en el orden que se le diga. */
    private int drop(final PaperCard card, final int amount, final DeckSection[] order) {
        if (card == null || amount <= 0) {
            return 0;
        }
        final String key = normalized(card.getName());
        int left = amount;
        for (final DeckSection where : order) {
            final CardPool pool = deck.get(where);
            if (pool == null || left <= 0) {
                continue;
            }
            // La lista se copia antes de tocar nada: quitar mientras se recorre
            // el pool lo revienta.
            final List<Map.Entry<PaperCard, Integer>> entries = new ArrayList<>();
            for (final Map.Entry<PaperCard, Integer> e : pool) {
                if (normalized(e.getKey().getName()).equals(key)) {
                    entries.add(Map.entry(e.getKey(), e.getValue()));
                }
            }
            for (final Map.Entry<PaperCard, Integer> e : entries) {
                if (left <= 0) {
                    break;
                }
                final int take = Math.min(left, e.getValue());
                pool.remove(e.getKey(), take);
                left -= take;
                dirty = true;
            }
        }
        return amount - left;
    }

    /** Si el comandante actual admite a este de companyero (regla del motor). */
    private boolean acceptsPartner(final PaperCard candidate) {
        final List<PaperCard> current = deck.getCommanders();
        if (current.size() != 1) {
            return false;
        }
        return current.get(0).getRules().canBePartnerCommanders(candidate.getRules());
    }

    public void removeCommander(final PaperCard card) {
        if (card == null) {
            return;
        }
        commanderPool().remove(card, 1);
        dirty = true;
    }

    /** Reserva vacia compartida: {@code countOf} se llama miles de veces. */
    private static final CardPool NO_COMMANDER = new CardPool();

    private CardPool commanderPool() {
        return deck.has(DeckSection.Commander)
                ? deck.get(DeckSection.Commander) : NO_COMMANDER;
    }

    // ---------------------------------------------------------------
    // Ediciones

    /**
     * Todas las impresiones de una carta, para poder elegir el arte.
     *
     * <p>El catalogo trabaja con {@code getUniqueCards()} — una impresion por
     * carta — porque para montar un mazo da igual de que edicion sale el Sol
     * Ring. Pero una vez esta dentro si importa: es lo que se ve en la mesa.
     *
     * <p>Se ordenan por edicion, que es como se buscan ("la de Modern Horizons").
     */
    public List<PaperCard> printingsOf(final PaperCard card) {
        if (card == null) {
            return List.of();
        }
        // Quien manda es el contexto: en la aventura solo se ofrecen los artes
        // que has abierto, y fuera de ella todos. Ver DeckContext.printingsOf.
        final List<PaperCard> mine = format.printingsOf(card);
        if (mine != null) {
            return mine;
        }
        final List<PaperCard> all =
                new ArrayList<>(FModel.getMagicDb().getCommonCards().getAllCards(card));
        all.sort(Comparator.comparing(PaperCard::getEdition)
                .thenComparing(PaperCard::getCollectorNumber));
        return all;
    }

    /**
     * Cambia el arte de una carta del mazo.
     *
     * <p>Se cambian TODAS las copias de golpe: llevar la misma carta con dos
     * artes distintos es un caso que nadie quiere y que solo lia la lista.
     *
     * <p>Ademas se guarda como arte preferido en las preferencias de Forge, que
     * es lo que hace que la eleccion valga tambien la proxima vez y en el resto
     * de la aplicacion. Es el mecanismo que ya usa la GUI vieja con las
     * estrellitas del catalogo.
     *
     * @return cuantas copias se han cambiado
     */
    public int switchPrinting(final PaperCard from, final PaperCard to) {
        if (from == null || to == null || from.equals(to)) {
            return 0;
        }
        int changed = 0;

        final int inMain = deck.getMain().count(from);
        if (inMain > 0) {
            deck.getMain().remove(from, inMain);
            deck.getMain().add(to, inMain);
            changed += inMain;
        }

        final CardPool cmd = commanderPool();
        final int asCommander = cmd.count(from);
        if (asCommander > 0) {
            cmd.remove(from, asCommander);
            cmd.add(to, asCommander);
            changed += asCommander;
        }

        if (changed > 0) {
            dirty = true;
            rememberArt(to);
        }
        return changed;
    }

    /** Deja esta impresion como la preferida para esa carta, en todo Forge. */
    private static void rememberArt(final PaperCard card) {
        try {
            final forge.gui.card.CardPreferences prefs =
                    forge.gui.card.CardPreferences.getPrefs(card);
            prefs.setPreferredArt(card.getEdition(), card.getArtIndex());
            forge.gui.card.CardPreferences.save();
        } catch (final RuntimeException e) {
            // Que no se recuerde el arte no puede tumbar el editor: la carta ya
            // se ha cambiado en el mazo, que es lo que el jugador pidio.
            System.err.println("[neo] no se ha podido guardar el arte preferido: " + e);
        }
    }

    // ---------------------------------------------------------------
    // Catalogo

    /**
     * Busca en TODAS las cartas de Magic.
     *
     * <p>Se busca sobre {@code getUniqueCards()} — una impresion por carta, la
     * que Forge considera mejor — y no sobre las 95.000 impresiones: al jugador
     * le da igual de que edicion sale el Sol Ring, y con las impresiones la
     * lista sale llena de duplicados.
     *
     * @param query      texto a buscar en el nombre; vacio devuelve las primeras
     * @param onlyLegal  filtrar por lo que cabe en este mazo (formato + identidad
     *                   de color del comandante)
     * @param extra      filtro adicional de la pantalla (color, tipo), o null
     * @param limit      cuantas devolver como mucho
     */
    /** Lo que se ensenya de una busqueda, y cuantas habia en total. */
    public static final class SearchResult {
        public final List<PaperCard> cards;
        public final int total;

        SearchResult(final List<PaperCard> cards, final int total) {
            this.cards = cards;
            this.total = total;
        }
    }

    /**
     * Busca, y de paso cuenta.
     *
     * <p>Una sola pasada sobre el catalogo: la pantalla necesita las dos cosas
     * (que ensenyar y cuantas hay) y recorrer 33.000 cartas dos veces por cada
     * tecla pulsada era el doble de trabajo del necesario.
     */
    public SearchResult find(final String query, final boolean onlyLegal,
                             final Predicate<PaperCard> extra, final int limit) {
        return find(query, onlyLegal, extra, limit, false);
    }

    /**
     * @param searchRules buscar tambien en el texto de reglas, no solo en el
     *                    nombre y el tipo
     */
    public SearchResult find(final String query, final boolean onlyLegal,
                             final Predicate<PaperCard> extra, final int limit,
                             final boolean searchRules) {
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        final Predicate<PaperCard> legal = onlyLegal ? legalFilter() : null;

        final List<PaperCard> hits = new ArrayList<>();
        for (int i = 0; i < index.size(); i++) {
            // El filtro mas barato primero: descarta casi todo sin tocar reglas
            // ni reservar memoria.
            if (searchRules ? !index.matchesText(i, q) : !index.matches(i, q)) {
                continue;
            }
            final PaperCard card = index.cardAt(i);
            if (legal != null && !legal.test(card)) {
                continue;
            }
            if (extra != null && !extra.test(card)) {
                continue;
            }
            hits.add(card);
        }

        // Lo que empieza por lo buscado va primero: si escribes "sol" quieres
        // ver el Sol Ring arriba, no un Consul's Lieutenant.
        hits.sort(Comparator
                .comparingInt((PaperCard c) -> displayName(c).startsWith(q) ? 0 : 1)
                .thenComparing(DeckEditor::displayName));

        final int total = hits.size();
        return new SearchResult(
                total > limit ? new ArrayList<>(hits.subList(0, limit)) : hits, total);
    }

    /** El nombre por el que se ordena y se compara: el que se ve. */
    private static String displayName(final PaperCard card) {
        return forge.neo.card.CardText.nameOf(card).toLowerCase(Locale.ROOT);
    }

    /** Solo las cartas, cuando el total da igual. */
    public List<PaperCard> search(final String query, final boolean onlyLegal,
                                  final Predicate<PaperCard> extra, final int limit) {
        return find(query, onlyLegal, extra, limit).cards;
    }

    /**
     * Que cartas caben en este mazo.
     *
     * <p>Tres preguntas, las tres al motor: si la carta esta en el pozo del
     * formato ({@link #inCardPool}, que en la Aventura no aplica),
     * si vale en el pozo de {@link GameFormat} cuando lo hay (Modern, Pioneer,
     * Pauper...), y — cuando ya hay comandante — si respeta su identidad de
     * color. La ultima es la que hace util el buscador en Commander: sin ella
     * te ofrece 33.000 cartas de las que la mayoria no puedes jugar. La del
     * pozo es en la practica redundante con el indice ya recortado
     * ({@link #DeckEditor}), pero es la misma pregunta que hace
     * {@link #rejectionReason} y conviene que las dos respondan igual.
     */
    public Predicate<PaperCard> legalFilter() {
        final GameFormat pool = format.poolFormat();
        final Predicate<PaperCard> identity = identityFilter();
        Predicate<PaperCard> p = this::inCardPool;
        if (pool != null) {
            p = p.and(pool.getFilterRules());
        }
        if (identity != null) {
            p = p.and(identity);
        }
        return p;
    }

    /**
     * El filtro de identidad de color del comandante, o null si no aplica.
     *
     * <p><b>Cacheado.</b> Construirlo recorre los comandantes y compone varios
     * predicados, y siempre da lo mismo mientras no cambie el comandante.
     * Hacerlo por cada carta — que es lo que pasaba al pintar 60 resultados o al
     * repasar las 100 del mazo — se notaba. Se rehace solo al cambiar de
     * comandante.
     */
    private Predicate<PaperCard> identityFilter() {
        final List<PaperCard> cmd = identitySource();
        if (!deckFormat().hasCommander() || cmd.isEmpty()) {
            cachedIdentityKey = null;
            cachedIdentity = null;
            return null;
        }
        final String key = cmd.toString();
        if (!key.equals(cachedIdentityKey)) {
            cachedIdentity = deckFormat().isLegalCardForCommanderPredicate(cmd);
            cachedIdentityKey = key;
        }
        return cachedIdentity;
    }

    private String cachedIdentityKey;
    private Predicate<PaperCard> cachedIdentity;

    /**
     * Quien FIJA la identidad de color del mazo.
     *
     * <p>Normalmente los comandantes, y ya esta. Pero en Oathbreaker el hechizo
     * insignia vive en la misma zona y {@link #commanders()} lo devuelve
     * tambien: pasarselo a {@code isLegalCardForCommanderPredicate} ensancharia
     * la identidad con los colores del hechizo, y entonces la interfaz daria
     * por buenas cartas que el motor rechaza al guardar. El motor saca
     * {@code cmdCI} <b>solo del oathbreaker</b>
     * ({@code DeckFormat.getDeckConformanceProblem}), asi que aqui igual.
     */
    private List<PaperCard> identitySource() {
        if (!usesSignatureSpell()) {
            return commanders();
        }
        final PaperCard oath = deck.getOathbreaker();
        return oath == null ? List.of() : List.of(oath);
    }

    /** Las cartas que pueden ser comandante en este formato. */
    public List<PaperCard> commanderCandidates(final String query, final int limit) {
        return commandZoneCandidates(query, limit, false);
    }

    /**
     * Las cartas que pueden ser <b>hechizo insignia</b>: instantaneos y
     * conjuros, y solo en Oathbreaker.
     *
     * <p>No se filtran por la identidad de color del oathbreaker <b>a
     * proposito</b>. El motor no la comprueba para esta carta — el bucle de
     * identidad de {@code getDeckConformanceProblem} solo recorre el mazo
     * principal y la banda — y esconder aqui lo que el motor acepta seria
     * inventarnos una regla de construccion, que es justo lo que no hacemos.
     */
    public List<PaperCard> signatureCandidates(final String query, final int limit) {
        return usesSignatureSpell() ? commandZoneCandidates(query, limit, true) : List.of();
    }

    private List<PaperCard> commandZoneCandidates(final String query, final int limit,
                                                  final boolean spell) {
        final String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        final DeckFormat df = deckFormat();
        final List<PaperCard> hits = new ArrayList<>();
        for (int i = 0; i < index.size(); i++) {
            final PaperCard c = index.cardAt(i);
            // Sin las rebalanceadas de Arena ("A-..."): no son las que nadie
            // busca al elegir comandante y, ordenadas por nombre, se ponian
            // delante. Las dos condiciones, como en AscentSeedDeck.commanderPool:
            // isRebalanced() sola casi no casa, porque el nombre ya lleva el prefijo.
            if (c.isRebalanced() || c.getName().startsWith("A-")) {
                continue;
            }
            final boolean fits = spell
                    ? c.getRules() != null && c.getRules().canBeSignatureSpell()
                            && df.isLegalCard(c)
                    : df.isLegalCommander(c.getRules());
            if (index.matches(i, q) && fits) {
                hits.add(c);
            }
        }
        hits.sort(Comparator
                .comparingInt((PaperCard c) -> displayName(c).startsWith(q) ? 0 : 1)
                .thenComparing(DeckEditor::displayName));
        return hits.size() > limit ? new ArrayList<>(hits.subList(0, limit)) : hits;
    }

    // ---------------------------------------------------------------
    // Persistencia

    /**
     * Guarda el mazo en la carpeta del formato.
     *
     * <p>El {@code IStorage} de Forge escribe el {@code .dck} y ademas deja el
     * mazo disponible para la pantalla de inicio sin reiniciar.
     */
    /**
     * Guarda el mazo, y si le has cambiado el nombre lo <b>renombra</b>.
     *
     * <p>El orden importa: primero se escribe el nuevo y despues se borra el
     * viejo. Al reves, un fallo a mitad dejaria el mazo sin ninguna de las dos
     * copias.
     */
    public void save() {
        format.storage().add(deck);
        final String now = deck.getName();
        if (savedAs != null && !savedAs.equals(now) && format.storage().contains(savedAs)) {
            format.storage().delete(savedAs);
        }
        savedAs = now;
        dirty = false;
    }

    /** Con que nombre esta guardado, o null si todavia no lo esta. */
    public String getSavedAs() {
        return savedAs;
    }

    /**
     * Si guardar con este nombre pisaria OTRO mazo distinto.
     *
     * <p>No es lo mismo que {@link #nameExists}: guardar encima de <i>ti
     * mismo</i> es lo normal, y hay que poder hacerlo sin que nadie pregunte
     * nada.
     */
    public boolean wouldOverwriteAnother(final String name) {
        return name != null && !name.equals(savedAs) && nameExists(name);
    }

    /** Si ya existe un mazo guardado con ese nombre en este formato. */
    public boolean nameExists(final String name) {
        return format.storage().contains(name);
    }

    public void delete() {
        format.storage().delete(deck.getName());
    }

    /**
     * El mazo como texto, en el formato que entienden Moxfield y compania.
     *
     * <p>Es el mismo que lee {@link DeckImporter}: cantidad, nombre, y el
     * comandante al final tras una linea en blanco.
     */
    public String toText() {
        final StringBuilder sb = new StringBuilder();
        for (final String group : GROUPS) {
            for (final Map.Entry<PaperCard, Integer> e : cardsInGroup(group)) {
                sb.append(e.getValue()).append(' ').append(e.getKey().getName()).append('\n');
            }
        }
        final List<PaperCard> cmd = commanders();
        if (!cmd.isEmpty()) {
            sb.append('\n');
            for (final PaperCard c : cmd) {
                sb.append("1 ").append(c.getName()).append('\n');
            }
        }
        return sb.toString();
    }
}
