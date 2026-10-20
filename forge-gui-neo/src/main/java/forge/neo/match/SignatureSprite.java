package forge.neo.match;

/**
 * <b>Un dibujo de la firma de un comandante</b> ({@link CommanderSignature}):
 * una pluma, una hoja, un tajo, una chispa. Un trazado SVG que mide
 * {@link #UNIT} de la base (0,0) a la punta (0,-100), su relleno y, si lo
 * lleva, un trazo encima (el canyon de una pluma, el nervio de una hoja).
 *
 * <p>Las dos interfaces lo dibujan <b>una vez</b> en una imagen pequenya y
 * luego solo la mueven: un trazado redibujado en cada fotograma lo rasteriza
 * la CPU (PC) o cuesta un pase entero de trazado (Android), y una imagen que se
 * mueve es un rectangulo con textura. Los colores van en ARGB para que los lean
 * las dos sin nada de JavaFX ni de Compose.
 */
public final class SignatureSprite {

    /** Lo que mide un dibujo de la base a la punta. */
    public static final double UNIT = 100;

    /** Para la cache de imagenes: un nombre por dibujo. */
    public final String name;
    public final String path;
    /** Un trazo encima, o null. */
    public final String accent;
    /** Las paradas del relleno, de 0 a 1: de izquierda a derecha, o del centro afuera si es radial. */
    public final float[] stops;
    public final int[] colors;
    public final boolean radial;
    /** El contorno, en ARGB; 0 = sin contorno. */
    public final int rim;
    /** El color del trazo de encima. */
    public final int accentColor;

    private SignatureSprite(final String name, final String path, final String accent, final float[] stops,
            final int[] colors, final boolean radial, final int rim, final int accentColor) {
        this.name = name;
        this.path = path;
        this.accent = accent;
        this.stops = stops;
        this.colors = colors;
        this.radial = radial;
        this.rim = rim;
        this.accentColor = accentColor;
    }

    /** La pluma negra de Sephiroth, con su brillo morado de cuervo. */
    public static final SignatureSprite FEATHER = new SignatureSprite("feather", BlackFeathers.VANE,
            BlackFeathers.QUILL, new float[] {0f, 0.55f, 0.85f, 1f},
            new int[] {0xFF040308, 0xFF0D0B18, 0xFF1E1A33, 0xFF3A3260}, false, 0x52AA9BDC, 0x80CDC3F0);

    /** Un tajo: una media luna fina, de la base a la punta. */
    private static final String CRESCENT = "M0,0 C10,-28 10,-72 0,-100 C3.5,-72 3.5,-28 0,0 Z";

    /** El de Cloud: azul y blanco, el Omnislash. */
    public static final SignatureSprite SLASH_BLUE = new SignatureSprite("slashBlue", CRESCENT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0xE63FA9FF, 0xFFFFFFFF, 0xFFA8E6FF}, false, 0, 0);
    /** Su halo: el mismo tajo, ancho y casi transparente. */
    public static final SignatureSprite SLASH_GLOW_BLUE = new SignatureSprite("slashGlowBlue", CRESCENT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0x003FA9FF, 0x7A6CC6FF, 0x003FA9FF}, false, 0, 0);

    /** El de la katana de Jin: blanco con un toque dorado. */
    public static final SignatureSprite SLASH_WHITE = new SignatureSprite("slashWhite", CRESCENT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0xE6FFD98A, 0xFFFFFFFF, 0xFFFFF3D6}, false, 0, 0);
    public static final SignatureSprite SLASH_GLOW_WHITE = new SignatureSprite("slashGlowWhite", CRESCENT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0x00FFE2A0, 0x6EFFEFC8, 0x00FFE2A0}, false, 0, 0);

    /** Una chispa: un rombo alargado, que vuela de punta. */
    public static final SignatureSprite SPARK_BLUE = new SignatureSprite("sparkBlue",
            "M0,0 L7,-42 L0,-100 L-7,-42 Z", null,
            new float[] {0f, 0.5f, 1f}, new int[] {0x9963BDFF, 0xFFFFFFFF, 0x9963BDFF}, false, 0, 0);

    /** La hoja de arce de Tsushima: roja, con su nervio claro. */
    public static final SignatureSprite MAPLE_LEAF = new SignatureSprite("mapleLeaf",
            "M0,0 L-3,-18 L-38,-30 L-30,-38 L-45,-55 L-28,-55 L-30,-72 L-14,-62 L-10,-82 L-4,-75 L0,-100 "
                    + "L4,-75 L10,-82 L14,-62 L30,-72 L28,-55 L45,-55 L30,-38 L38,-30 L3,-18 Z",
            "M0,10 L0,-78",
            new float[] {0f, 0.45f, 0.8f, 1f}, new int[] {0xFF6E0B0B, 0xFFC8231B, 0xFFF0532A, 0xFFFF9A4A},
            false, 0x59FFBE8C, 0x73FFD2AA);

    // ------------------------------------------------------------------
    // Los de los diez comandantes mas jugados (LayeredSignature, 20-10-2026).

    private static SignatureSprite of(final String name, final String path, final String accent,
            final float[] stops, final int[] colors, final boolean radial, final int rim, final int accentColor) {
        return new SignatureSprite(name, path, accent, stops, colors, radial, rim, accentColor);
    }

    /** Un circulo, de la base a la punta. */
    private static final String DISC = "M0,0 C-66,0 -66,-100 0,-100 C66,-100 66,0 0,0 Z";
    /** Un rombo alargado, como la chispa azul. */
    private static final String RHOMB = "M0,0 L7,-42 L0,-100 L-7,-42 Z";
    /** Una estrella de cuatro puntas. */
    private static final String STAR4 = "M0,0 Q5,-45 45,-50 Q5,-55 0,-100 Q-5,-55 -45,-50 Q-5,-45 0,0 Z";
    /** Una llama, de la base a la punta. */
    private static final String FLAME_PATH = "M0,0 C-30,0 -34,-40 -8,-62 C-6,-48 4,-44 6,-56 "
            + "C14,-70 4,-88 0,-100 C26,-82 34,-50 26,-24 C20,-6 10,0 0,0 Z";
    private static final String BOLT = "M0,0 L9,-22 L-2,-26 L12,-50 L1,-53 L10,-78 L-1,-80 L4,-100 "
            + "L-12,-70 L-2,-68 L-14,-44 L-3,-41 L-12,-18 L-3,-15 Z";

    private static SignatureSprite spark(final String name, final int color) {
        final int clear = (color & 0x00FFFFFF) | 0x99000000;
        return of(name, RHOMB, null, new float[] {0f, 0.5f, 1f}, new int[] {clear, 0xFFFFFFFF, clear},
                false, 0, 0);
    }

    /** Y'shtola: la luna creciente, plateada, y sus estrellas. */
    public static final SignatureSprite MOON = of("moon",
            "M10,0 C-55,-10 -55,-90 10,-100 C-25,-80 -25,-20 10,0 Z", null,
            new float[] {0f, 0.6f, 1f}, new int[] {0xFFF4F8FF, 0xFFCFE0FF, 0xFF9DB8F0}, false, 0x66D8E6FF, 0);
    public static final SignatureSprite STAR_SILVER = of("starSilver", STAR4, null,
            new float[] {0f, 0.4f, 1f}, new int[] {0xFFFFFFFF, 0xFFD6E6FF, 0x00A9C4FF}, true, 0, 0);
    public static final SignatureSprite STAR_GOLD = of("starGold", STAR4, null,
            new float[] {0f, 0.4f, 1f}, new int[] {0xFFFFFFFF, 0xFFFFE08A, 0x00FFB830}, true, 0, 0);

    /** Edgar Markov: el murcielago, con las alas a lo ancho (aletea estrechandose), y la gota de sangre. */
    public static final SignatureSprite BAT = of("bat",
            "M0,-40 L-12,-55 L-25,-48 L-38,-62 L-46,-45 L-60,-52 L-50,-30 L-38,-38 L-25,-28 L-12,-36 "
                    + "L-4,-25 L0,-30 L4,-25 L12,-36 L25,-28 L38,-38 L50,-30 L60,-52 L46,-45 L38,-62 "
                    + "L25,-48 L12,-55 Z", null,
            new float[] {0f, 0.5f, 1f}, new int[] {0xFF3A1024, 0xFF7A1E3C, 0xFF3A1024}, false, 0xCCE04868, 0);
    public static final SignatureSprite BLOOD_DROP = of("bloodDrop",
            "M0,0 C-30,0 -30,-40 0,-100 C30,-40 30,0 0,0 Z", null,
            new float[] {0f, 0.45f, 1f}, new int[] {0xFFFF8A8A, 0xFFC0101C, 0xFF5A0008}, true, 0, 0);

    /** Las llamas: naranja (The Ur-Dragon, Kaalia), dorada y azul (Azula). */
    public static final SignatureSprite FLAME = of("flame", FLAME_PATH, null,
            new float[] {0f, 0.4f, 0.75f, 1f}, new int[] {0xFFFFF6B0, 0xFFFFB02E, 0xFFE8461A, 0x00B8200E},
            true, 0, 0);
    public static final SignatureSprite FLAME_GOLD = of("flameGold", FLAME_PATH, null,
            new float[] {0f, 0.4f, 0.75f, 1f}, new int[] {0xFFFFFFFF, 0xFFFFE07A, 0xFFF0A020, 0x00C07010},
            true, 0, 0);
    public static final SignatureSprite FLAME_BLUE = of("flameBlue", FLAME_PATH, null,
            new float[] {0f, 0.4f, 0.75f, 1f}, new int[] {0xFFFFFFFF, 0xFF9FE4FF, 0xFF2E7CFF, 0x001A3CC8},
            true, 0, 0);
    /** El zarpazo de fuego del Ur-Dragon. */
    public static final SignatureSprite SLASH_FIRE = of("slashFire", CRESCENT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0xE6FF7A2A, 0xFFFFF4C8, 0xFFFFB050}, false, 0, 0);
    public static final SignatureSprite SLASH_GLOW_FIRE = of("slashGlowFire", CRESCENT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0x00FF6020, 0x7AFF9040, 0x00FF6020}, false, 0, 0);

    /** Ascuas de los cinco colores (The Ur-Dragon) y chispas roja y dorada. */
    public static final SignatureSprite EMBER_W = spark("emberW", 0xFFFFF4D0);
    public static final SignatureSprite EMBER_U = spark("emberU", 0xFF5AB0FF);
    public static final SignatureSprite EMBER_B = spark("emberB", 0xFFB07CE8);
    public static final SignatureSprite EMBER_R = spark("emberR", 0xFFFF6B47);
    public static final SignatureSprite EMBER_G = spark("emberG", 0xFF5FD38A);
    public static final SignatureSprite SPARK_RED = spark("sparkRed", 0xFFFF4A2A);
    public static final SignatureSprite SPARK_GOLD = spark("sparkGold", 0xFFFFC040);

    /** Atraxa: el resplandor dorado detras de la carta, y la pluma blanca. */
    public static final SignatureSprite GLORY = of("glory", DISC, null,
            new float[] {0f, 0.35f, 0.7f, 1f}, new int[] {0xCCFFFFFF, 0x99FFE7A0, 0x40FFC850, 0x00FFC850},
            true, 0, 0);
    public static final SignatureSprite FEATHER_WHITE = of("featherWhite", BlackFeathers.VANE, BlackFeathers.QUILL,
            new float[] {0f, 0.55f, 0.85f, 1f}, new int[] {0xFFFFFFFF, 0xFFF4EEDC, 0xFFE2D4A8, 0xFFC9B47A},
            false, 0x66FFE9B0, 0x99B89A50);

    /** Vivi: la bola de fuego. */
    public static final SignatureSprite FIREBALL = of("fireball", DISC, null,
            new float[] {0f, 0.35f, 0.7f, 1f}, new int[] {0xFFFFFFE0, 0xFFFFD04A, 0xFFFF6A1E, 0x00D02A0A},
            true, 0, 0);

    /**
     * Sauron: el Ojo, una almendra en llamas tumbada con la pupila rasgada. La
     * pupila es un HUECO (va en sentido contrario al contorno): se ve lo que
     * haya detras, oscuro.
     */
    public static final SignatureSprite EYE = of("eye",
            "M-50,-50 C-25,-82 25,-82 50,-50 C25,-18 -25,-18 -50,-50 Z "
                    + "M0,-74 C-7,-60 -7,-40 0,-26 C7,-40 7,-60 0,-74 Z", null,
            new float[] {0f, 0.3f, 0.65f, 1f}, new int[] {0xFFFFF2B0, 0xFFFFB640, 0xFFE0451A, 0xFF5A0A04},
            true, 0x88FF7A2A, 0);
    /** La ceniza que cae. */
    public static final SignatureSprite ASH = of("ash",
            "M0,0 L-14,-30 L-6,-58 L-18,-80 L0,-100 L16,-74 L8,-50 L18,-24 Z", null,
            new float[] {0f, 0.5f, 1f}, new int[] {0xCC6E6A66, 0xCC9A948C, 0xCC4A4542}, false, 0, 0);

    /** Teval: la hoja y el alma que sube. */
    public static final SignatureSprite LEAF_GREEN = of("leafGreen",
            "M0,0 C-34,-26 -30,-74 0,-100 C30,-74 34,-26 0,0 Z", "M0,6 L0,-86",
            new float[] {0f, 0.5f, 1f}, new int[] {0xFF1E5A2A, 0xFF3E9A4A, 0xFF7CCB6A}, false, 0x5590E0A0, 0x88C8F0B0);
    public static final SignatureSprite MOTE_TEAL = of("moteTeal", DISC, null,
            new float[] {0f, 0.4f, 1f}, new int[] {0xFFE8FFFA, 0xCC6CE0D0, 0x0030A0A0}, true, 0, 0);

    /** Azula: el rayo, con su halo. */
    public static final SignatureSprite LIGHTNING = of("lightning", BOLT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0xFFBFE8FF, 0xFFFFFFFF, 0xFFBFE8FF}, false, 0, 0);
    public static final SignatureSprite LIGHTNING_GLOW = of("lightningGlow", BOLT, null,
            new float[] {0f, 0.5f, 1f}, new int[] {0x003FA9FF, 0x806CC6FF, 0x003FA9FF}, false, 0, 0);
}
