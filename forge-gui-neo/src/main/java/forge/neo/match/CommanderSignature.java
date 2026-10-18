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
    JIN_LEAVES;

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
            default:
                return null;
        }
    }

    /** Lo que dura de mas una entrada con firma, para que se vea su final. */
    public static final double EXTRA_SECONDS = 0.4;

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
