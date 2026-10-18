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
}
