package forge.neo.sound;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoSettings;

/**
 * <b>Los sonidos propios de Neo Forge</b> (24-10-2026). Pedido en Reddit: "lo que
 * te falta para estar en las grandes ligas es el audio".
 *
 * <p>Generados en local con Stable Audio 3 ({@code D:\AI_LOCAL\Scripts\generar_sfx.py})
 * y elegidos uno a uno por Ana; de donde sale cada fichero, en
 * {@code resources/forge/neo/sound/PROCEDENCIA.md}. Antes se probaron los
 * paquetes CC0 de Kenney y gustaron mas los de Forge.
 *
 * <p>Cada evento tiene un <b>monton</b> de variantes y suena una al azar: lo que
 * mas cansa del audio es oir diez veces seguidas exactamente lo mismo. El de
 * Forge puede ser una mas del monton ({@link #FORGE}): asi se quiso para
 * "destruir". Los eventos que no estan aqui suenan como siempre.
 *
 * <p>Sin JavaFX ni Android: lo usan las dos interfaces. Forge guarda UN clip por
 * evento y lo reutiliza, asi que el azar lo pone cada interfaz en su clip
 * (PC: {@code NeoGuiBase.createAudioClip}; Android: {@code Efectos}) llamando a
 * {@link #pick} en cada reproduccion - y por eso el ajuste vale al momento, sin
 * reiniciar. Los ficheros viven en el jar y se sacan una vez a la cache de
 * Forge, porque los dos reproductores quieren un fichero.
 */
public final class NeoSounds {

    /** El ajuste: sonidos de Neo Forge o los de Forge. */
    public static final String SETTING = "neoSounds";

    /**
     * ENCENDIDO de fabrica desde el 25-10-2026 (Ana: "pon por defecto mis
     * sonidos a on"), con las tres tandas ya elegidas. Del 24 al 25-10 estuvo
     * apagado ("que lo active quien quiera"). Quien lo haya apagado a mano lo
     * sigue teniendo apagado: solo cambia para quien no lo ha tocado.
     */
    public static final boolean DEFAULT = true;

    /** En un monton: "el de Forge de siempre". */
    public static final String FORGE = "";

    /** Sonidos que Forge no tiene: los ponemos nosotros. */
    public static final String COMMANDER_ENTRANCE = "commander_entrance";
    public static final String PACK_OPEN = "pack_open";

    /** Subirlo al cambiar un fichero por otro con el mismo nombre (ver {@link #file}). */
    private static final int VERSION = 3;

    /** Evento (el nombre del sonido de Forge, sin extension) -> sus variantes. */
    private static final Map<String, String[]> POOLS = new java.util.HashMap<>();

    static {
        put("instant", 4);
        put("sorcery", 4);
        put("creature", 4);
        put("draw", 4);
        put("shuffle", 4);
        POOLS.put("destroy", new String[] {"destroy_1.mp3", "destroy_2.mp3", FORGE});
        put(COMMANDER_ENTRANCE, 4);
        put(PACK_OPEN, 4);
        // La segunda tanda (24-10-2026). Lo que Ana dejo con el de Forge (fichas,
        // artefactos, tierras salvo la roja, enderezar, equipar, moneda, fin de
        // turno, poner contadores) no esta: suena el de siempre.
        put("tap", 3);
        put("discard", 2);
        put("flip_card", 4);
        put("block", 4);
        put("artifact_creature", 2);
        put("enchant", 4);
        put("planeswalker", 2);
        put("red_land", 2);
        put("life_loss", 1);
        put("poison", 1);
        put("win_duel", 1);
        put("lose_duel", 1);
        put("daytime", 1);
        put("nighttime", 1);
        put("roll_die", 1);
        put("coins_drop", 1);
        // Y estos Forge los pide pero no trae fichero: hasta ahora no sonaban.
        put("remove_counter", 2);
        put("life_gain", 1);
        put("exile", 2);
        put("sacrifice", 1);
        put("regeneration", 1);
        put("start_duel", 1);
        // La tercera tanda (25-10-2026): las tierras a la medida de las de Forge
        // (1,7 s), salvo la llanura, que se queda con sus caballos. La montanya
        // pasa a ser fuego de hoguera (sustituye a la de la tanda 2).
        put("blue_land", 1);
        put("black_land", 1);
        put("green_land", 1);
        // Las tierras de varios colores, las incoloras y las de "cualquier
        // color": Forge cae aqui (EventVisualizer) y no trae fichero.
        put("other_land", 1);
        put("damage", 1);
        put("equip", 1);
        put("phasing", 2);
    }

    private NeoSounds() {
    }

    /** {@code n} variantes: base_1.mp3 ... base_n.mp3. */
    private static void put(final String base, final int n) {
        final String[] pool = new String[n];
        for (int i = 0; i < n; i++) {
            pool[i] = base + "_" + (i + 1) + ".mp3";
        }
        POOLS.put(base, pool);
    }

    public static boolean enabled() {
        try {
            return NeoSettings.getBool(SETTING, DEFAULT);
        } catch (final RuntimeException e) {
            return DEFAULT;
        }
    }

    /** Los eventos con sonidos nuestros (para el comprobador). */
    static java.util.Set<String> events() {
        return POOLS.keySet();
    }

    /** Las variantes de un evento ({@link #FORGE} = la de Forge). */
    static String[] pool(final String event) {
        return POOLS.get(event).clone();
    }

    /** Si ese evento tiene sonidos nuestros (aunque ahora esten apagados). */
    public static boolean has(final String forgeName) {
        return POOLS.containsKey(base(forgeName));
    }

    /**
     * El fichero que tiene que sonar ahora para ese evento, o {@code null} para
     * el de Forge: con el ajuste apagado, si no tiene monton, si al azar toca el
     * de Forge, o si el fichero no se ha podido sacar del jar.
     */
    public static File pick(final String forgeName) {
        if (!enabled()) {
            return null;
        }
        final String[] pool = POOLS.get(base(forgeName));
        if (pool == null) {
            return null;
        }
        final String choice = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        return choice.isEmpty() ? null : file(choice);
    }

    /**
     * El volumen de los efectos que pone el jugador (0 = sin sonido), para los
     * sonidos que Forge no tiene y no pasan por su SoundSystem: la entrada del
     * comandante y abrir un sobre.
     */
    public static float volume() {
        try {
            final forge.localinstance.properties.ForgePreferences prefs = forge.model.FModel.getPreferences();
            if (!prefs.getPrefBoolean(forge.localinstance.properties.ForgePreferences.FPref.UI_ENABLE_SOUNDS)) {
                return 0f;
            }
            return Math.max(0, Math.min(100,
                    prefs.getPrefInt(forge.localinstance.properties.ForgePreferences.FPref.UI_VOL_SOUNDS))) / 100f;
        } catch (final RuntimeException e) {
            return 0f;
        }
    }

    /** "instant.mp3", "instant" o una ruta: el nombre del evento. */
    private static String base(final String name) {
        if (name == null) {
            return "";
        }
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        final int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** El fichero en la cache de Forge, sacado del jar la primera vez. */
    static synchronized File file(final String name) {
        try {
            // Con version: si se cambia un sonido por otro del mismo nombre, se
            // sube el numero y la cache vieja deja de valer.
            final File dir = new File(ForgeConstants.CACHE_DIR, "neo-sound-" + VERSION);
            final File out = new File(dir, name);
            if (out.isFile() && out.length() > 0) {
                return out;
            }
            try (InputStream in = NeoSounds.class.getResourceAsStream("/forge/neo/sound/" + name)) {
                if (in == null) {
                    return null;
                }
                dir.mkdirs();
                final File tmp = new File(dir, name + ".tmp");
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return out;
        } catch (final Exception e) {
            System.err.println("[sonido] no se ha podido sacar " + name + ": " + e);
            return null;
        }
    }
}
