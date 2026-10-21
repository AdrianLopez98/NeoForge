package forge.neo.match;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * <b>Los hechizos famosos con su propio efecto al resolverse</b> (Ana,
 * 21-10-2026): las limpiezas de mesa y las quitadas mas jugadas de EDH. Un
 * Blasphemous Act que resuelve hace caer un meteorito; un Wrath of God, un
 * fogonazo de luz. <b>Solo si resuelve</b>: lo dispara
 * {@code GameEventSpellResolved}, que no llega si lo contrarrestan, y tampoco
 * si se queda sin objetivos.
 *
 * <p>No es una entrada de comandante: no hay carta en el centro ni se para la
 * mesa. Son los mismos dibujos baratos de {@link LayeredSignature} (imagenes
 * pintadas una vez que solo se mueven), un fogonazo (un rectangulo que sube y
 * baja de opacidad) y, en los gordos, un temblor de la mesa. Las limpiezas
 * ocupan la mesa entera; las quitadas, la carta que golpean ({@link #atTarget}).
 *
 * <p>Java puro, en el jar: el escritorio lo pinta con {@code SpellBlast} y
 * Android con su pintor.
 */
public enum SpellSignature {

    NONE(0, 0, false, false),
    /** Un meteorito en diagonal, fogonazo naranja, temblor y ascuas y rocas. */
    BLASPHEMOUS_ACT(0xFFFF8A2A, 0.45, true, false),
    /** Columnas de luz sobre la mesa, fogonazo blanco y estrellas doradas. */
    WRATH_OF_GOD(0xFFFFF8E6, 0.6, true, false),
    /** La mesa se oscurece en morado, un anillo de humo y ceniza. */
    DAMNATION(0xFF14061E, 0.6, true, false),
    /** Burbujas verdes por toda la mesa y un fogonazo verde. */
    TOXIC_DELUGE(0xFF6CE04A, 0.3, false, false),
    /** Un remolino azul que se lo traga todo hacia el centro. */
    CYCLONIC_RIFT(0xFF4AA8FF, 0.35, false, false),
    /** Un barrido de luz blanca con estrellas plateadas. */
    FAREWELL(0xFFFFFFFF, 0.6, false, false),
    /** Tres rayos blancos y azules con fogonazo. */
    SUPREME_VERDICT(0xFFD8EEFF, 0.5, true, false),
    /** Un tajo blanco sobre el objetivo y chispas doradas. */
    SWORDS_TO_PLOWSHARES(0, 0, false, true),
    /** Un portal de luz en el objetivo y motas plateadas que suben. */
    PATH_TO_EXILE(0, 0, false, true),
    /** Un remolino rojo y morado que se traga el objetivo. */
    CHAOS_WARP(0, 0, false, true);

    /** El color del fogonazo (ARGB), o 0 sin fogonazo. */
    public final int flashColor;
    /** Cuanto tapa el fogonazo en su punto mas alto (0..1). */
    public final double flashPeak;
    /** Si la mesa tiembla al golpe. */
    public final boolean shake;
    /** Sobre la carta objetivo, no sobre la mesa entera. */
    public final boolean atTarget;

    SpellSignature(final int flashColor, final double flashPeak, final boolean shake, final boolean atTarget) {
        this.flashColor = flashColor;
        this.flashPeak = flashPeak;
        this.shake = shake;
        this.atTarget = atTarget;
    }

    /** Lo que dura entero, en segundos. */
    public static final double DURATION = 1.5;
    /** Cuando golpea: el fogonazo, el temblor y el estallido. */
    public static final double IMPACT_AT = 0.42;

    /** Su coreografia, para una mesa de ese tamanyo; null en {@link #NONE}. */
    public SignatureMotion motion(final double width, final double height) {
        switch (this) {
            case BLASPHEMOUS_ACT:
                return SpellBlasts.blasphemousAct(width, height);
            case WRATH_OF_GOD:
                return SpellBlasts.wrathOfGod(width, height);
            case DAMNATION:
                return SpellBlasts.damnation(width, height);
            case TOXIC_DELUGE:
                return SpellBlasts.toxicDeluge(width, height);
            case CYCLONIC_RIFT:
                return SpellBlasts.cyclonicRift(width, height);
            case FAREWELL:
                return SpellBlasts.farewell(width, height);
            case SUPREME_VERDICT:
                return SpellBlasts.supremeVerdict(width, height);
            case SWORDS_TO_PLOWSHARES:
                return SpellBlasts.swords(width, height);
            case PATH_TO_EXILE:
                return SpellBlasts.path(width, height);
            case CHAOS_WARP:
                return SpellBlasts.chaosWarp(width, height);
            default:
                return null;
        }
    }

    private static final Map<String, SpellSignature> BY_NAME = new HashMap<>();

    static {
        BY_NAME.put("blasphemous act", BLASPHEMOUS_ACT);
        BY_NAME.put("wrath of god", WRATH_OF_GOD);
        BY_NAME.put("damnation", DAMNATION);
        BY_NAME.put("toxic deluge", TOXIC_DELUGE);
        BY_NAME.put("cyclonic rift", CYCLONIC_RIFT);
        BY_NAME.put("farewell", FAREWELL);
        BY_NAME.put("supreme verdict", SUPREME_VERDICT);
        BY_NAME.put("swords to plowshares", SWORDS_TO_PLOWSHARES);
        BY_NAME.put("path to exile", PATH_TO_EXILE);
        BY_NAME.put("chaos warp", CHAOS_WARP);
    }

    /** El de esa carta por su nombre en ingles, o {@link #NONE}. */
    public static SpellSignature of(final String englishName) {
        if (englishName == null) {
            return NONE;
        }
        final SpellSignature s = BY_NAME.get(englishName.toLowerCase(Locale.ROOT));
        return s == null ? NONE : s;
    }
}
