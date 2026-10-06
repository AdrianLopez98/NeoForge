package forge.neo.deck;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.util.FileSection;
import forge.util.FileUtil;

/**
 * <b>Un mazo roto no puede tumbar la lista entera.</b>
 *
 * <p>Ana, 06-10-2026: un {@code .dck} que Forge no sabe leer hace que su
 * {@code StorageReaderFolder} lance "An object stored in ... failed to load" a
 * mitad de la carpeta, y con eso no se carga NINGUN mazo de ese formato: la
 * pantalla de mazos se quedaba colgada. El lector es de Forge y no se toca
 * (regla de oro), asi que el mazo roto se APARTA: se renombra a
 * {@code nombre.dck.roto} (Forge solo lee {@code .dck}), no se borra nada, y la
 * pantalla de mazos avisa de cual fue para que el jugador pueda mirarlo.
 *
 * <p>Pura y compartida con Android por el jar.
 */
public final class BrokenDecks {

    private BrokenDecks() {
    }

    /**
     * <b>Al arrancar, antes de que nadie lea los mazos.</b> Hace falta ANTES y no
     * al fallar: en la carpeta de Construido el {@code DeckStorage} de Forge no
     * lanza nada con un mazo que no sabe leer — lo BORRA
     * ({@code adjustFileLocation}: {@code file.delete()}). Las carpetas de mazos
     * se leen tarde ({@code CardCollections} es perezoso), asi que justo despues
     * de {@code FModel.initialize} llega a tiempo. Lo llaman {@code NeoBoot} y
     * el {@code EngineBoot} de Android. Nunca tumba el arranque.
     */
    public static void atStartup() {
        try {
            final long t0 = System.currentTimeMillis();
            final List<String> moved = quarantine(new File(forge.localinstance.properties.ForgeConstants.DECK_BASE_DIR));
            System.out.println("        mazos ilegibles apartados: " + moved.size() + " ("
                    + (System.currentTimeMillis() - t0) + " ms)");
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se pudieron revisar los mazos: " + e);
        }
    }

    /** Lo apartado y aun no avisado (los nombres de fichero). */
    private static final List<String> PENDING = new ArrayList<>();

    /**
     * Mira todos los {@code .dck} de esa carpeta (y subcarpetas) y aparta los
     * que no se pueden leer.
     *
     * @return los que se han apartado
     */
    public static synchronized List<String> quarantine(final File dir) {
        final List<String> out = new ArrayList<>();
        walk(dir, out);
        PENDING.addAll(out);
        return out;
    }

    private static void walk(final File dir, final List<String> out) {
        final File[] files = dir == null ? null : dir.listFiles();
        if (files == null) {
            return;
        }
        for (final File f : files) {
            if (f.isDirectory()) {
                walk(f, out);
            } else if (f.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".dck") && !readable(f)) {
                File target = new File(f.getPath() + ".roto");
                for (int i = 2; target.exists(); i++) {
                    target = new File(f.getPath() + ".roto" + i);
                }
                if (f.renameTo(target)) {
                    out.add(f.getName());
                    System.err.println("[neo] mazo que no se puede leer, apartado: " + f + " -> " + target.getName());
                }
            }
        }
    }

    /** Si Forge lo sabria leer: lo mismo que hace su {@code DeckStorage.read}. */
    static boolean readable(final File f) {
        try {
            final Deck d = DeckSerializer.fromSections(FileSection.parseSections(FileUtil.readFile(f)));
            return d != null;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** Los apartados desde la ultima vez que se pregunto, para avisar UNA vez. */
    public static synchronized List<String> takePending() {
        final List<String> out = new ArrayList<>(PENDING);
        PENDING.clear();
        return out;
    }

    /** Si el fallo es el de un mazo que no se puede leer. */
    public static boolean isLoadFailure(final Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            final String m = c.getMessage();
            if (m != null && m.contains("failed to load")) {
                return true;
            }
        }
        return false;
    }
}
