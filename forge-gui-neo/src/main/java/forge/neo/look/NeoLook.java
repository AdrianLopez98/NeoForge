package forge.neo.look;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoSettings;

/**
 * La personalizacion: tu nombre, tu avatar, el tapete, las fundas y la musica.
 *
 * <p>Aqui vive el <b>modelo</b>: donde estan las cosas, cuales trae Forge de
 * fabrica, cuales ha importado el jugador y como se importa una nueva. Lo que
 * se pinta con todo esto es {@code forge.neo.ui.LookScreen}.
 *
 * <h2>Dos origenes, una misma lista</h2>
 *
 * Todo lo que se puede elegir viene de uno de dos sitios, y en la pantalla se
 * mezclan sin distinguirlos:
 *
 * <ul>
 *   <li><b>De fabrica</b>: lo que trae Forge en {@code res/skins/default}. Son
 *       hojas de sprites (130 avatares, 40 fundas) y unas cuantas imagenes de
 *       fondo. Ver {@link Sprites}.</li>
 *   <li><b>Importado</b>: un fichero que el jugador ha elegido con el
 *       explorador. Se <b>copia</b> a una carpeta nuestra, no se enlaza: si se
 *       guardara la ruta original, mover o borrar el fichero dejaria un avatar
 *       roto y sin explicacion.</li>
 * </ul>
 *
 * <h2>Donde se copia lo importado, y por que ahi</h2>
 *
 * <pre>
 * %APPDATA%\Forge\neo\avatars\      avatares importados
 * %APPDATA%\Forge\neo\playmats\     tapetes importados
 * %APPDATA%\Forge\neo\sleeves\      fundas importadas
 * %APPDATA%\Forge\custom\music\...  la musica, ver {@link NeoMusic}
 * </pre>
 *
 * <p>Carpeta {@code neo\} propia porque compartimos {@code %APPDATA%\Forge\}
 * con la instalacion normal del usuario y no vamos a dejarle ficheros sueltos
 * por en medio. Y <b>fuera del repositorio</b>, que es la regla de oro aplicada
 * a los datos: borrar esa carpeta deja el juego como estaba.
 *
 * <p>La musica es la excepcion y va en la carpeta de Forge a proposito: ahi el
 * motor la encuentra <i>solo</i>, sin que tengamos que reproducir nada a mano.
 */
public final class NeoLook {

    private NeoLook() {
    }

    // ---------------------------------------------------------------
    // Las claves en neo.properties
    // ---------------------------------------------------------------

    /** Tu nombre en la mesa. Vacio = el que tenga Forge. */
    public static final String PLAYER_NAME = "playerName";

    /** Tu avatar, con la forma {@code sprite:12} o {@code file:loquesea.png}. */
    public static final String AVATAR = "avatar";

    /** Tu funda. */
    public static final String SLEEVE = "sleeve";

    /** El tapete, o {@code none} para la mesa lisa de siempre. */
    public static final String PLAYMAT = "playmat";

    /** Los nombres de los rivales, separados por barras verticales. */
    public static final String AI_NAMES = "aiNames";

    /** Ningun tapete: la mesa oscura de siempre. */
    public static final String NO_PLAYMAT = "none";

    // ---------------------------------------------------------------
    // Las carpetas
    // ---------------------------------------------------------------

    /** La carpeta nuestra dentro de los datos de Forge. */
    public static File root() {
        return new File(ForgeConstants.USER_DIR, "neo");
    }

    public static File avatarsDir() {
        return sub("avatars");
    }

    public static File playmatsDir() {
        return sub("playmats");
    }

    public static File sleevesDir() {
        return sub("sleeves");
    }

    private static File sub(final String name) {
        final File dir = new File(root(), name);
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    // ---------------------------------------------------------------
    // Que hay para elegir
    // ---------------------------------------------------------------

    /**
     * Los avatares: los {@value Sprites#AVATAR_COUNT_HINT} de Forge y los tuyos.
     *
     * <p>Los importados van <b>primero</b>: si te has molestado en traer uno,
     * no tiene sentido que quede sepultado detras de ciento treinta.
     */
    public static List<LookItem> avatars() {
        final List<LookItem> out = new ArrayList<>(imported(avatarsDir()));
        out.addAll(Sprites.avatars());
        return out;
    }

    /** Cuantos avatares trae Forge. Lo pregunta el motor. */
    public static int builtInAvatarCount() {
        return Sprites.avatarCount();
    }

    /** Cuantas fundas trae Forge. */
    public static int builtInSleeveCount() {
        return Sprites.sleeveCount();
    }

    /**
     * El avatar que le toca a un rival, por el indice que reparte el motor.
     *
     * <p>El tuyo lo eliges tu y puede ser un PNG cualquiera; el de una IA sale
     * de la hoja de Forge, que es lo que el motor sabe repartir.
     */
    public static LookItem builtInAvatar(final int index) {
        final List<LookItem> all = Sprites.avatars();
        if (all.isEmpty()) {
            return LookItem.none();
        }
        return all.get(Math.floorMod(index, all.size()));
    }

    /**
     * La funda que le toca a un rival, por el indice que reparte el motor.
     *
     * <p>Igual que {@link #builtInAvatar}: la tuya la eliges tu y puede ser
     * cualquier PNG; la de una IA sale de la hoja de Forge, que es lo unico
     * que el motor sabe repartir ({@code PlayerView.getSleeveIndex()}).
     */
    public static LookItem builtInSleeve(final int index) {
        final List<LookItem> all = Sprites.sleeves();
        if (all.isEmpty()) {
            return LookItem.none();
        }
        return all.get(Math.floorMod(index, all.size()));
    }

    /** Las fundas: las de Forge y las tuyas. */
    public static List<LookItem> sleeves() {
        final List<LookItem> out = new ArrayList<>(imported(sleevesDir()));
        out.addAll(Sprites.sleeves());
        return out;
    }

    /**
     * Los tapetes: "ninguno", los fondos que trae el skin y los tuyos.
     *
     * <p>"Ninguno" va el primero y es lo de siempre — la mesa oscura y lisa
     * sobre la que se decidio toda la estetica. Un tapete con textura compite
     * con el arte de las cartas, asi que tiene que ser una eleccion, no lo que
     * te encuentras puesto.
     */
    public static List<LookItem> playmats() {
        final List<LookItem> out = new ArrayList<>();
        out.add(LookItem.none());
        out.addAll(imported(playmatsDir()));
        for (final String file : BUILT_IN_PLAYMATS) {
            final File f = new File(ForgeConstants.DEFAULT_SKINS_DIR, file);
            if (f.isFile()) {
                out.add(LookItem.ofFile("skin:" + file, prettyName(file), f));
            }
        }
        return out;
    }

    /** Los fondos que trae el skin y valen como tapete. */
    private static final String[] BUILT_IN_PLAYMATS = {
        "bg_match.jpg", "bg_day.jpg", "bg_night.jpg", "bg_texture.jpg", "bg_space.png",
    };

    /** Lo que el jugador haya dejado en una de nuestras carpetas. */
    private static List<LookItem> imported(final File dir) {
        final List<LookItem> out = new ArrayList<>();
        final File[] files = dir.listFiles();
        if (files == null) {
            return out;
        }
        Arrays.sort(files);
        for (final File f : files) {
            if (f.isFile() && isImage(f.getName())) {
                out.add(LookItem.ofFile("file:" + f.getName(), prettyName(f.getName()), f));
            }
        }
        return out;
    }

    private static boolean isImage(final String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
                || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    /** "mi_avatar_guapo.png" -> "mi avatar guapo". */
    private static String prettyName(final String fileName) {
        final int dot = fileName.lastIndexOf('.');
        final String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        return base.replace('_', ' ').replace('-', ' ').trim();
    }

    // ---------------------------------------------------------------
    // Buscar uno por su identificador
    // ---------------------------------------------------------------

    /** El avatar elegido, o el primero que haya. Nunca null. */
    public static LookItem currentAvatar() {
        return pick(avatars(), NeoSettings.get(AVATAR, null));
    }

    /** La funda elegida en Personalizar: la tuya de siempre. Nunca null. */
    public static LookItem currentSleeve() {
        return pick(sleeves(), NeoSettings.get(SLEEVE, null));
    }

    // ---------------------------------------------------------------
    // La funda DE UN MAZO
    // ---------------------------------------------------------------
    //
    // Un mazo puede llevar la suya, y entonces manda sobre la de Personalizar
    // mientras se juega con el. Es lo que se espera de unas fundas: se compran
    // para un mazo, no para uno mismo.
    //
    // Se guarda en las ETIQUETAS del mazo (Deck.getTags()), que es el unico
    // hueco libre que el motor ya guarda y recupera del .dck (DeckSerializer
    // escribe "Tags=" y DeckFileHeader lo relee). Asi la funda viaja CON el
    // mazo: se exporta, se copia a la version portable y sigue puesta, sin una
    // tabla nuestra de nombres que se rompa al renombrarlo.
    //
    // Ojo: las etiquetas se separan por COMAS, asi que un identificador con una
    // coma partiria la etiqueta en dos. Por eso importInto() se las quita al
    // nombre del fichero -- ver freeName().

    /** El prefijo de la etiqueta donde vive la funda del mazo. */
    private static final String SLEEVE_TAG = "neo-sleeve=";

    /** El identificador de la funda de este mazo, o null si no lleva ninguna. */
    public static String deckSleeveId(final Deck deck) {
        if (deck == null) {
            return null;
        }
        for (final String tag : deck.getTags()) {
            if (tag != null && tag.startsWith(SLEEVE_TAG)) {
                final String id = tag.substring(SLEEVE_TAG.length()).trim();
                return id.isEmpty() ? null : id;
            }
        }
        return null;
    }

    /**
     * Le pone funda a un mazo, o se la quita con {@code null}.
     *
     * <p>Se quitan primero las que hubiera: el conjunto de etiquetas admite
     * varias y dos fundas en el mismo mazo no significan nada.
     */
    public static void setDeckSleeve(final Deck deck, final String id) {
        if (deck == null) {
            return;
        }
        deck.getTags().removeIf(tag -> tag != null && tag.startsWith(SLEEVE_TAG));
        if (id != null && !id.isBlank()) {
            deck.getTags().add(SLEEVE_TAG + id.trim());
        }
    }

    /** La funda de este mazo; si no lleva, la tuya. Nunca null. */
    public static LookItem sleeveOf(final Deck deck) {
        final String id = deckSleeveId(deck);
        return id == null ? currentSleeve() : pick(sleeves(), id);
    }

    /** Con que mazo se esta jugando ahora mismo. Null fuera de partida. */
    private static Deck deckInPlay;

    /**
     * Apunta el mazo con el que se va a jugar, para que la mesa pinte SU funda.
     *
     * <p>Lo pone {@code NeoGame.play}, que es por donde pasan las partidas
     * normales, las del draft/sellado y las del torneo. Los modos que montan la
     * partida por su cuenta (el duelo de la aventura, que lo arma
     * {@code QuestUtil}) no lo ponen y se quedan con la funda de Personalizar,
     * que es la respuesta correcta cuando no se sabe de que mazo se trata.
     */
    public static void setDeckInPlay(final Deck deck) {
        deckInPlay = deck;
    }

    /** La funda que toca pintar en la mesa: la del mazo en juego, o la tuya. */
    public static LookItem sleeveInPlay() {
        return sleeveOf(deckInPlay);
    }

    /** El tapete elegido; de fabrica, ninguno. */
    public static LookItem currentPlaymat() {
        return pick(playmats(), NeoSettings.get(PLAYMAT, NO_PLAYMAT));
    }

    private static LookItem pick(final List<LookItem> all, final String id) {
        if (all.isEmpty()) {
            return LookItem.none();
        }
        if (id != null) {
            for (final LookItem item : all) {
                if (item.getId().equals(id)) {
                    return item;
                }
            }
        }
        // El guardado ya no esta (lo has borrado de la carpeta): se cae al
        // primero en vez de dejar la pantalla sin nada.
        return all.get(0);
    }

    // ---------------------------------------------------------------
    // Importar
    // ---------------------------------------------------------------

    /**
     * Trae un fichero a una de nuestras carpetas.
     *
     * <p>Se copia, no se mueve: el fichero del jugador es suyo y no se toca.
     * Si ya hay uno con ese nombre se le anyade un numero, que es lo que hace
     * cualquier gestor de descargas y lo que la gente espera.
     *
     * @return el elemento ya listo para elegir, o null si no se ha podido
     */
    public static LookItem importInto(final File dir, final File source) {
        if (source == null || !source.isFile()) {
            return null;
        }
        try {
            final File dest = freeName(dir, source.getName());
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return LookItem.ofFile("file:" + dest.getName(), prettyName(dest.getName()), dest);
        } catch (final IOException | SecurityException e) {
            System.err.println("[neo] no se ha podido importar " + source + ": " + e);
            return null;
        }
    }

    /**
     * Un nombre que no pise a nadie: "foto.png", "foto 2.png", "foto 3.png"...
     *
     * <p>Y <b>sin comas</b>: el identificador de una funda importada es
     * {@code file:<nombre>}, y ese identificador acaba dentro de una etiqueta
     * del mazo, que el motor separa por comas ({@code DeckFileHeader}). Una
     * coma en el nombre partiria la etiqueta en dos y la funda se perderia al
     * volver a abrir el mazo, sin decir nada.
     */
    static File freeName(final File dir, final String rawName) {
        final String fileName = rawName.replace(',', ' ');
        File candidate = new File(dir, fileName);
        if (!candidate.exists()) {
            return candidate;
        }
        final int dot = fileName.lastIndexOf('.');
        final String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        final String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 2; i < 1000; i++) {
            candidate = new File(dir, base + " " + i + ext);
            if (!candidate.exists()) {
                return candidate;
            }
        }
        return candidate;
    }

    /** Borra un elemento importado. Los de fabrica no se pueden borrar. */
    public static boolean delete(final LookItem item) {
        if (item == null || !item.isImported() || item.getFile() == null) {
            return false;
        }
        return item.getFile().delete();
    }

    // ---------------------------------------------------------------
    // Tu nombre y el de los rivales
    // ---------------------------------------------------------------

    /** Como te llamas en la mesa. Vacio si no lo has puesto. */
    public static String playerName() {
        return NeoSettings.get(PLAYER_NAME, "").trim();
    }

    public static void setPlayerName(final String name) {
        NeoSettings.set(PLAYER_NAME, name == null ? "" : name.trim());
        NeoSettings.save();
    }

    /**
     * Como se llama el rival numero {@code index} (desde 0).
     *
     * <p>Si no le has puesto nombre, uno del generador de Forge — que tiene
     * cientos, de fantasia y normales. Es mucho mejor que "IA-1": una partida
     * contra Sythril, Kaldar y Occelot se recuerda, y una contra IA-1, IA-2 e
     * IA-3 no.
     */
    public static String aiName(final int index) {
        final List<String> saved = aiNames();
        if (index < saved.size() && !saved.get(index).isBlank()) {
            return saved.get(index);
        }
        return "";
    }

    /** Los nombres que hayas fijado, en orden. Los huecos van vacios. */
    public static List<String> aiNames() {
        final String raw = NeoSettings.get(AI_NAMES, "");
        final List<String> out = new ArrayList<>();
        if (raw.isBlank()) {
            return out;
        }
        for (final String part : raw.split("\\|", -1)) {
            out.add(part.trim());
        }
        return out;
    }

    public static void setAiNames(final List<String> names) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            // La barra es el separador: si alguien la escribe en un nombre, se
            // guardaria un nombre de mas al releerlo.
            sb.append(names.get(i) == null ? "" : names.get(i).replace("|", " ").trim());
        }
        NeoSettings.set(AI_NAMES, sb.toString());
        NeoSettings.save();
    }
}
