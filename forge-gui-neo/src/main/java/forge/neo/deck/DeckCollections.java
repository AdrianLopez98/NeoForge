package forge.neo.deck;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import forge.deck.Deck;
import forge.deck.io.DeckStorage;
import forge.localinstance.properties.ForgeConstants;
import forge.neo.match.NeoFormat;
import forge.util.storage.IStorage;
import forge.util.storage.StorageImmediatelySerialized;

/**
 * Colecciones de mazos: <b>subcarpetas</b> de la carpeta del formato.
 *
 * <p>Pedido en itch.io el 27-09-2026: "Mis mazos / De Forge / De internet" no
 * basta cuando juegas Estandar de ahora y a ratos sets de 2010, y no quieres
 * todo mezclado en la misma pestanya.
 *
 * <p><b>Por que carpetas de verdad y no etiquetas en un fichero nuestro.</b>
 * Forge ya las entiende: {@code decks\commander} y {@code decks\constructed} se
 * cargan con subcarpetas ({@code CardCollections}, {@code withSubFolders=true})
 * y su GUI vieja las ensenya. Asi que una coleccion creada aqui se ve alli, una
 * carpeta que ya tuvieras montada alli aparece aqui sola, y renombrar un mazo
 * no lo saca de su coleccion — con etiquetas por nombre, si.
 *
 * <p><b>Por que no se usa el {@code getFolders()} de Forge.</b> Dos trampas en
 * su {@code StorageImmediatelySerialized}: crear una carpeta
 * ({@code getOrCreateSubfolder}) le pasa el serializador del PADRE, asi que lo
 * que guardes "dentro" se escribe en la carpeta de arriba; y
 * {@code StorageNestedFolders.add} lanza {@code UnsupportedOperationException}.
 * Ademas Brawl, Oathbreaker y Tiny Leaders ni siquiera se cargan con
 * subcarpetas. Aqui cada coleccion es su propio almacen, apuntando a su propia
 * carpeta, y se lee del disco la primera vez que se pide.
 *
 * <p>Los almacenes se guardan en un mapa para que la pantalla de mazos y el
 * constructor miren el MISMO objeto durante la sesion: si cada uno se hiciera
 * el suyo, guardar en uno no se veria en el otro hasta reiniciar.
 */
public final class DeckCollections {

    private DeckCollections() {
    }

    /** Un almacen por carpeta, por ruta absoluta. */
    private static final Map<String, IStorage<Deck>> CACHE = new HashMap<>();

    /** Lo mas largo que se deja poner de nombre: tiene que caber en una pestanya. */
    public static final int MAX_NAME = 32;

    /**
     * Si este formato admite colecciones.
     *
     * <p>Los que guardan mazos sueltos en su propia carpeta. Draft y sellado
     * no: ahi un "mazo" es un evento entero con los siete rivales dentro.
     */
    public static boolean isSupported(final NeoFormat format) {
        return root(format) != null;
    }

    /** La carpeta de mazos del formato, o null si no admite colecciones. */
    private static File root(final NeoFormat format) {
        if (format == null) {
            return null;
        }
        switch (format) {
            case COMMANDER:
            case PREDH:
                return new File(ForgeConstants.DECK_COMMANDER_DIR);
            case ESTANDAR:
                return new File(ForgeConstants.DECK_CONSTRUCTED_DIR);
            case BRAWL:
                return new File(ForgeConstants.DECK_BRAWL_DIR);
            case OATHBREAKER:
                return new File(ForgeConstants.DECK_OATHBREAKER_DIR);
            case TINY_LEADERS:
                return new File(ForgeConstants.DECK_TINY_LEADERS_DIR);
            default:
                return null;
        }
    }

    /** Las colecciones de este formato, por orden alfabetico. */
    public static List<String> list(final NeoFormat format) {
        final List<String> out = new ArrayList<>();
        final File root = root(format);
        final File[] dirs = root == null ? null
                : root.listFiles(f -> f.isDirectory() && !f.isHidden()
                        && !f.getName().startsWith("."));
        if (dirs != null) {
            for (final File d : dirs) {
                out.add(d.getName());
            }
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** El almacen de una coleccion, o null si no existe. */
    public static synchronized IStorage<Deck> storage(final NeoFormat format, final String name) {
        final File root = root(format);
        if (root == null || name == null) {
            return null;
        }
        final File dir = new File(root, name);
        if (!dir.isDirectory()) {
            return null;
        }
        return CACHE.computeIfAbsent(dir.getAbsolutePath(),
                k -> new StorageImmediatelySerialized<>(name,
                        new DeckStorage(dir, ForgeConstants.DECK_BASE_DIR)));
    }

    /** Los mazos de una coleccion. */
    public static List<Deck> decks(final NeoFormat format, final String name) {
        final List<Deck> out = new ArrayList<>();
        final IStorage<Deck> s = storage(format, name);
        if (s != null) {
            s.forEach(out::add);
        }
        return out;
    }

    /**
     * Que tiene de malo este nombre para una coleccion, o null si vale.
     *
     * <p>Es un nombre de carpeta: fuera lo que Windows no deja, y fuera lo que
     * ya exista <b>sin mirar mayusculas</b>, porque en Windows "Viejos" y
     * "viejos" son la misma carpeta.
     */
    public static String problem(final NeoFormat format, final String name, final String except) {
        final String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            return "home.collection.error.empty";
        }
        if (n.length() > MAX_NAME || n.startsWith(".") || n.endsWith(".")
                || !n.equals(n.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", ""))) {
            return "home.collection.error.chars";
        }
        for (final String other : list(format)) {
            if (other.equalsIgnoreCase(n) && (except == null || !other.equalsIgnoreCase(except))) {
                return "home.collection.error.exists";
            }
        }
        return null;
    }

    /** Crea una coleccion vacia. Devuelve true si ya esta en el disco. */
    public static boolean create(final NeoFormat format, final String name) {
        if (problem(format, name, null) != null) {
            return false;
        }
        final File dir = new File(root(format), name.trim());
        return dir.mkdirs() || dir.isDirectory();
    }

    /** Le cambia el nombre. Los mazos de dentro van con ella. */
    public static synchronized boolean rename(final NeoFormat format, final String from,
                                              final String to) {
        final File root = root(format);
        if (root == null || problem(format, to, from) != null) {
            return false;
        }
        final File src = new File(root, from);
        final File dst = new File(root, to.trim());
        if (!src.isDirectory()) {
            return false;
        }
        // Solo cambian las mayusculas: en Windows es la misma carpeta, y
        // renameTo directo a veces no hace nada. Se pasa por un nombre de paso.
        if (src.getName().equalsIgnoreCase(dst.getName()) && !src.getName().equals(dst.getName())) {
            final File tmp = new File(root, from + ".neo-renombrando");
            if (!src.renameTo(tmp) || !tmp.renameTo(dst)) {
                return false;
            }
        } else if (!src.renameTo(dst)) {
            return false;
        }
        CACHE.remove(src.getAbsolutePath());
        return true;
    }

    /**
     * Borra una coleccion <b>sin borrar ningun mazo</b>: los suyos vuelven a
     * "Mis mazos".
     *
     * <p>Lo que se quita es la carpeta, no lo que hay dentro. Si en "Mis mazos"
     * ya hay uno con el mismo nombre, el que vuelve se trae un " (2)" detras
     * en vez de pisarlo. Devuelve cuantos mazos se han movido, o -1 si algo ha
     * fallado (y entonces la carpeta se queda donde estaba).
     */
    public static synchronized int delete(final NeoFormat format, final String name) {
        final IStorage<Deck> from = storage(format, name);
        if (from == null) {
            return -1;
        }
        final IStorage<Deck> to = format.storage();
        int moved = 0;
        for (final Deck d : new ArrayList<>(decks(format, name))) {
            final Deck copy = new Deck(d, freeName(to, d.getName()));
            to.add(copy);
            if (!to.contains(copy.getName())) {
                return -1;
            }
            from.delete(d.getName());
            moved++;
        }
        final File dir = new File(root(format), name);
        CACHE.remove(dir.getAbsolutePath());
        // Si queda algo que no es un mazo (un fichero de otro programa), la
        // carpeta no se puede borrar: se deja, y se dice que ha fallado.
        return dir.delete() ? moved : -1;
    }

    /**
     * Mueve un mazo a otra coleccion ({@code to == null} = "Mis mazos").
     *
     * <p>Si el mazo no es tuyo (un preconstruido de Forge, uno de internet) no
     * se mueve: se <b>copia</b>, que es lo unico que tiene sentido — el
     * original vuelve a salir en cada arranque.
     *
     * <p>Primero se escribe el nuevo y despues se borra el viejo: al reves, un
     * fallo a mitad dejaria el mazo sin ninguna de las dos copias.
     *
     * @param from la coleccion donde esta ahora, {@code null} para "Mis mazos"
     * @param own  si el mazo es tuyo (si hay que borrar el de origen)
     * @return null si ha ido bien, o la clave de texto del problema
     */
    public static synchronized String move(final NeoFormat format, final Deck deck,
                                           final String from, final boolean own,
                                           final String to) {
        final IStorage<Deck> dst = to == null ? format.storage() : storage(format, to);
        final IStorage<Deck> src = !own ? null
                : from == null ? format.storage() : storage(format, from);
        if (dst == null || deck == null) {
            return "home.collection.error.move";
        }
        if (dst == src) {
            return null;
        }
        if (dst.contains(deck.getName())) {
            return "home.collection.error.taken";
        }
        dst.add(new Deck(deck, deck.getName()));
        if (!dst.contains(deck.getName())) {
            return "home.collection.error.move";
        }
        if (src != null && src.contains(deck.getName())) {
            src.delete(deck.getName());
        }
        return null;
    }

    /** Un nombre que no este cogido en ese almacen: "X", "X (2)", "X (3)"... */
    private static String freeName(final IStorage<Deck> in, final String base) {
        if (!in.contains(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            final String n = String.format(Locale.ROOT, "%s (%d)", base, i);
            if (!in.contains(n)) {
                return n;
            }
        }
    }
}
