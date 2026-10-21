package forge.neo.match;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import forge.game.card.CardView;

/**
 * <b>La firma de los comandantes famosos</b> en su entrada (Ana, 18-10-2026:
 * <i>"comandantes famosos y buenos tuviesen efectos custom que tengan que ver
 * con ella, por ejemplo en Sephiroth unas plumas negras al entrar"</i>).
 *
 * <p>Solo la TABLA: que comandante lleva que firma. La pintan las dos
 * interfaces ({@code forge.neo.ui.CommanderEntrance} y, en Android,
 * {@code EntradaComandante.kt}), y por eso vive aqui: el jar la comparte y las
 * dos leen la misma lista. Anyadir un comandante a una firma que ya existe es
 * una linea de {@link #BY_NAME}; una firma nueva es su coreografia (como
 * {@link BlackFeathers}) y dibujarla en las dos.
 *
 * <p>Por el nombre en ingles, que es el del motor y no cambia con el idioma, y
 * mirando las dos caras: una carta de dos caras puede entrar por la de atras.
 * Algunas solo cuando el comandante es <b>tuyo</b> ({@link #putMine}): Atraxa,
 * Grand Unifier es Sephiroth en <i>Final Fantasy Through the Ages</i> ("Sephiroth,
 * the Savior"), y la del rival sera casi siempre la de Phyrexia.
 * {@code -Dneo.entrance.signature=BLACK_FEATHERS} la fuerza para probarla con
 * cualquier carta ({@code --entrance-test}).
 */
public enum CommanderSignature {

    /** La entrada de siempre. */
    NONE,

    /**
     * Sephiroth, el angel de un ala: un <b>ala negra</b> que se despliega
     * detras de la carta y se deshace en plumas, <b>plumas negras</b> cayendo
     * por toda la pantalla, y un <b>estallido de plumas</b> al posarse.
     */
    BLACK_FEATHERS,

    /** Cloud: el Omnislash, cinco tajos azules que cruzan la carta ({@link CloudSlashes}). */
    CLOUD_SLASHES,

    /** Jin Sakai: hojas de arce en el viento y un tajo de katana ({@link JinLeaves}). */
    JIN_LEAVES,

    // Los diez comandantes mas jugados de EDH (EDHREC, 20-10-2026), con capas
    // (LayeredSignature). Por el nombre de verdad: valen para todas sus impresiones.

    /** Y'shtola, Night's Blessed: la luna y las estrellas. */
    YSHTOLA_MOON,
    /** Edgar Markov: murcielagos y sangre. */
    EDGAR_BATS,
    /** The Ur-Dragon: fuego y ascuas de los cinco colores. */
    URDRAGON_FIRE,
    /** Atraxa, Praetors' Voice: resplandor dorado y plumas blancas. */
    ATRAXA_LIGHT,
    /** Krenko, Mob Boss: chispas que se multiplican. */
    KRENKO_SWARM,
    /** Vivi Ornitier: bolas de fuego que orbitan y estrellas magicas. */
    VIVI_FIRE,
    /** Sauron, the Dark Lord: el Ojo en llamas y ceniza. */
    SAURON_EYE,
    /** Kaalia of the Vast: angeles, demonios y dragones en espiral. */
    KAALIA_HOSTS,
    /** Teval, the Balanced Scale: hojas que caen y almas que suben. */
    TEVAL_BALANCE,
    /** Fire Lord Azula: fuego azul y rayos. */
    AZULA_LIGHTNING,

    /** Isshin, Two Heavens as One: papel de arroz, las dos pinceladas y la firma de Musashi (Vagabond). */
    ISSHIN_INK,

    // Los diez siguientes de EDHREC (21-10-2026) y Lightning.
    /** Giada, Font of Hope: motas doradas y plumas. */
    GIADA_ANGELS,
    /** Pantlaza, Sun-Favored: el sol y llamas doradas. */
    PANTLAZA_SUN,
    /** Ms. Bumbleflower: petalos y estrellas. */
    BUMBLEFLOWER_PETALS,
    /** Lathril, Blade of the Elves: hojas y tajos verdes. */
    LATHRIL_BLADES,
    /** Toph, the First Metalbender: rocas y metal. */
    TOPH_EARTH,
    /** The Wise Mothman: polillas y radiacion. */
    MOTHMAN_MOTHS,
    /** Jodah, the Unifier: los cinco colores. */
    JODAH_PRISM,
    /** Hearthhull, the Worldseed: estrellas y un planeta. */
    HEARTHHULL_COSMOS,
    /** Nekusar, the Mindrazer: la rueda de cartas y llamas moradas. */
    NEKUSAR_WHEEL,
    /** Kefka, Court Mage: el Juicio de Luz. */
    KEFKA_JUDGMENT,
    /** Lightning, Army of One: rayos rosas y su pluma. */
    LIGHTNING_BOLTS;

    /**
     * Su coreografia, para una pantalla de ese tamanyo; null en {@link #NONE}.
     * Se crea una por entrada: sortea sus parametros una vez y ya no guarda nada.
     */
    public SignatureMotion motion(final double width, final double height) {
        switch (this) {
            case BLACK_FEATHERS:
                return new BlackFeathers(width, height);
            case CLOUD_SLASHES:
                return new CloudSlashes(width, height);
            case JIN_LEAVES:
                return new JinLeaves(width, height);
            case YSHTOLA_MOON:
                return LayeredSignature.yshtola(width, height);
            case EDGAR_BATS:
                return LayeredSignature.edgar(width, height);
            case URDRAGON_FIRE:
                return LayeredSignature.urDragon(width, height);
            case ATRAXA_LIGHT:
                return LayeredSignature.atraxa(width, height);
            case KRENKO_SWARM:
                return LayeredSignature.krenko(width, height);
            case VIVI_FIRE:
                return LayeredSignature.vivi(width, height);
            case SAURON_EYE:
                return LayeredSignature.sauron(width, height);
            case KAALIA_HOSTS:
                return LayeredSignature.kaalia(width, height);
            case TEVAL_BALANCE:
                return LayeredSignature.teval(width, height);
            case AZULA_LIGHTNING:
                return LayeredSignature.azula(width, height);
            case ISSHIN_INK:
                return LayeredSignature.isshin(width, height);
            case GIADA_ANGELS:
                return LayeredSignature.giada(width, height);
            case PANTLAZA_SUN:
                return LayeredSignature.pantlaza(width, height);
            case BUMBLEFLOWER_PETALS:
                return LayeredSignature.bumbleflower(width, height);
            case LATHRIL_BLADES:
                return LayeredSignature.lathril(width, height);
            case TOPH_EARTH:
                return LayeredSignature.toph(width, height);
            case MOTHMAN_MOTHS:
                return LayeredSignature.mothman(width, height);
            case JODAH_PRISM:
                return LayeredSignature.jodah(width, height);
            case HEARTHHULL_COSMOS:
                return LayeredSignature.hearthhull(width, height);
            case NEKUSAR_WHEEL:
                return LayeredSignature.nekusar(width, height);
            case KEFKA_JUDGMENT:
                return LayeredSignature.kefka(width, height);
            case LIGHTNING_BOLTS:
                return LayeredSignature.lightning(width, height);
            default:
                return null;
        }
    }

    /** Lo que dura de mas una entrada con firma, para que se vea su final. */
    public static final double EXTRA_SECONDS = 0.4;

    /**
     * Cuanto mas despacio va la entrada ENTERA (carta, rayos y firma): 1 en
     * todas menos Isshin, que va un 30% mas lenta para que se lea la tinta
     * (Ana, 21-10-2026: "un 30% mas larga ... pero solo esta"). Los dos
     * pintores dividen el tiempo por esto.
     */
    public double slowdown() {
        return this == ISSHIN_INK ? 1.3 : 1;
    }

    private static final Map<String, CommanderSignature> BY_NAME = new HashMap<>();
    /** Las que solo llevan firma si el comandante es tuyo. */
    private static final Map<String, CommanderSignature> MINE_ONLY = new HashMap<>();

    static {
        put(BLACK_FEATHERS,
                "Sephiroth, Fabled SOLDIER", "Sephiroth, One-Winged Angel",
                "Sephiroth, Planet's Heir", "Sephiroth, Fallen Hero");
        // "Sephiroth, the Savior" (FCA 49), el comandante del autor (18-10-2026).
        putMine(BLACK_FEATHERS, "Atraxa, Grand Unifier");
        // Los otros dos del autor (18-10-2026: "solo haz para jin sakai y cloud").
        put(CLOUD_SLASHES, "Cloud, Ex-SOLDIER", "Cloud, Midgar Mercenary", "Cloud, Planet's Champion");
        put(JIN_LEAVES, "Jin Sakai, Ghost of Tsushima");
        // Los diez mas jugados de EDH (EDHREC, 20-10-2026).
        put(YSHTOLA_MOON, "Y'shtola, Night's Blessed");
        put(EDGAR_BATS, "Edgar Markov");
        put(URDRAGON_FIRE, "The Ur-Dragon");
        put(ATRAXA_LIGHT, "Atraxa, Praetors' Voice");
        put(KRENKO_SWARM, "Krenko, Mob Boss");
        put(VIVI_FIRE, "Vivi Ornitier");
        put(SAURON_EYE, "Sauron, the Dark Lord");
        put(KAALIA_HOSTS, "Kaalia of the Vast");
        put(TEVAL_BALANCE, "Teval, the Balanced Scale");
        put(AZULA_LIGHTNING, "Fire Lord Azula");
        // Pedida por Ana (20-10-2026): Musashi, el de Vagabond, sin el personaje.
        put(ISSHIN_INK, "Isshin, Two Heavens as One");
        // Los diez siguientes de EDHREC (21-10-2026) y Lightning (Final Fantasy XIII).
        put(GIADA_ANGELS, "Giada, Font of Hope");
        put(PANTLAZA_SUN, "Pantlaza, Sun-Favored");
        put(BUMBLEFLOWER_PETALS, "Ms. Bumbleflower");
        put(LATHRIL_BLADES, "Lathril, Blade of the Elves");
        put(TOPH_EARTH, "Toph, the First Metalbender");
        put(MOTHMAN_MOTHS, "The Wise Mothman");
        put(JODAH_PRISM, "Jodah, the Unifier");
        put(HEARTHHULL_COSMOS, "Hearthhull, the Worldseed");
        put(NEKUSAR_WHEEL, "Nekusar, the Mindrazer");
        put(KEFKA_JUDGMENT, "Kefka, Court Mage", "Kefka, Ruler of Ruin");
        put(LIGHTNING_BOLTS, "Lightning, Army of One");
    }

    private static void put(final CommanderSignature signature, final String... names) {
        for (final String n : names) {
            BY_NAME.put(n.toLowerCase(Locale.ROOT), signature);
        }
    }

    private static void putMine(final CommanderSignature signature, final String... names) {
        for (final String n : names) {
            MINE_ONLY.put(n.toLowerCase(Locale.ROOT), signature);
        }
    }

    /**
     * La firma de ese nombre de carta (en ingles); {@link #NONE} si no tiene.
     *
     * @param mine si el comandante es del jugador de esta pantalla
     */
    public static CommanderSignature of(final String englishName, final boolean mine) {
        if (englishName == null) {
            return NONE;
        }
        final String key = englishName.toLowerCase(Locale.ROOT);
        CommanderSignature s = BY_NAME.get(key);
        if (s == null && mine) {
            s = MINE_ONLY.get(key);
        }
        return s == null ? NONE : s;
    }

    /**
     * La firma de ese comandante, mirando sus dos caras. Nunca lanza.
     *
     * @param mine si es del jugador de esta pantalla
     */
    public static CommanderSignature of(final CardView card, final boolean mine) {
        final String forced = System.getProperty("neo.entrance.signature");
        if (forced != null) {
            try {
                return valueOf(forced.trim().toUpperCase(Locale.ROOT));
            } catch (final IllegalArgumentException ignored) {
                // Una bandera mal escrita no rompe la entrada: la de la carta.
            }
        }
        if (card == null) {
            return NONE;
        }
        try {
            // EL NOMBRE DE VERDAD (getOracleName), no el que se ve: getName() es
            // el nombre alternativo de la impresion, y la Atraxa de FCA se llama
            // ahi "Sephiroth, the Savior". Despues el que se ve, por si acaso.
            CommanderSignature s = of(card.getOracleName(), mine);
            if (s == NONE && card.getCurrentState() != null) {
                s = of(card.getCurrentState().getOracleName(), mine);
            }
            if (s == NONE && card.getAlternateState() != null) {
                s = of(card.getAlternateState().getOracleName(), mine);
            }
            if (s == NONE) {
                s = of(card.getName(), mine);
            }
            return s;
        } catch (final RuntimeException e) {
            return NONE;
        }
    }
}
