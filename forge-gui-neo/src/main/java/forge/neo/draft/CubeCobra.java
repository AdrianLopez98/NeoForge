package forge.neo.draft;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.gamemodes.limited.CustomLimited;
import forge.item.SealedTemplate;
import forge.localinstance.properties.ForgeConstants;

/**
 * <b>Los cubos de CubeCobra</b> (Discord, 25-10-2026: <i>"import custom
 * blocks from CubeCobra, just like Forge does"</i>).
 *
 * <p>Forge ya trae el importador ({@code CubeImporter}), pero no se usa tal
 * cual por dos cosas: pregunta el enlace con un dialogo suyo, y al acabar hace
 * {@code FModel.getPreferences().save()} — que escribiria en
 * {@code %APPDATA%\Forge} el volumen y los ajustes que nosotros solo ponemos en
 * memoria. Asi que aqui va lo mismo con las piezas publicas: la MISMA direccion
 * ({@code cubecobra.com/cube/download/forge/<id>}, que devuelve un {@code .dck}),
 * {@code DeckSerializer} y un {@code CustomLimited} con los ajustes del
 * importador de Forge (sobres de 15 sin rarezas, tres por jugador, singleton).
 *
 * <p>Se descarga <b>una vez</b>, cuando el jugador lo pide, con tope de tiempo
 * (memoria "red sin bucles ni sondeos"), y se guarda en
 * {@code <datos>/neo/cubes/<id>.dck}: <b>nunca en {@code res\}</b>, que es de
 * fabrica y lo espeja {@code replicar.bat}. Desde ahi sale en la lista de
 * cubos como los de Forge ({@link NeoDraft#cubes}) y ya no hace falta red.
 *
 * <p>Pura y en el jar: la usa tambien NeoForge Android. Nada de API de Java
 * que Android no tenga en la 26.
 */
public final class CubeCobra {

    private CubeCobra() {
    }

    private static final String DOWNLOAD = "https://cubecobra.com/cube/download/forge/";
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    /** Nombre mostrado → fichero, de los importados. Lo rellena {@link #imported}. */
    private static final Map<String, File> FILES = new HashMap<>();

    /** Donde se guardan. {@code -Dneo.cubes.dir} para las pruebas. */
    public static File folder() {
        final String test = System.getProperty("neo.cubes.dir");
        return test != null ? new File(test)
                : new File(ForgeConstants.USER_DIR, "neo" + File.separator + "cubes");
    }

    /**
     * El ID del cubo a partir de lo que se pegue: el ID solo, o un enlace
     * ({@code cubecobra.com/cube/overview/<id>}, {@code /list/<id>}...). Como
     * {@code CubeImporter.parseCubeId}, sin la parte de despues de {@code ?} o
     * {@code #}. {@code null} si no parece un ID.
     */
    public static String parseId(final String input) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        final int cut = indexOfAny(s, '?', '#');
        if (cut >= 0) {
            s = s.substring(0, cut);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        final int slash = s.lastIndexOf('/');
        final String id = slash >= 0 ? s.substring(slash + 1) : s;
        return id.matches("^[a-zA-Z0-9\\-_]+$") ? id : null;
    }

    private static int indexOfAny(final String s, final char a, final char b) {
        final int i = s.indexOf(a);
        final int j = s.indexOf(b);
        return i < 0 ? j : j < 0 ? i : Math.min(i, j);
    }

    /** Lo que sale mal, con el motivo ya dicho para la pantalla. */
    public static final class ImportException extends IOException {
        public final Reason reason;

        ImportException(final Reason reason, final String detail) {
            super(reason + (detail == null ? "" : ": " + detail));
            this.reason = reason;
        }
    }

    /** Por que no se ha podido: cada uno lleva su texto en la pantalla. */
    public enum Reason { BAD_ID, NOT_FOUND, NETWORK, EMPTY }

    /**
     * Descarga el cubo y lo guarda. <b>Hace red</b>: nunca desde el hilo de
     * la interfaz. Devuelve el nombre con el que sale en la lista (el que
     * hay que pasar a {@link NeoDraft#startCube}).
     */
    public static String download(final String input) throws ImportException {
        final String id = parseId(input);
        if (id == null) {
            throw new ImportException(Reason.BAD_ID, input);
        }
        final byte[] body;
        try {
            final HttpURLConnection c = (HttpURLConnection) new URL(DOWNLOAD + id).openConnection();
            c.setConnectTimeout(10_000);
            c.setReadTimeout(20_000);
            c.setRequestProperty("User-Agent", "NeoForge");
            final int code = c.getResponseCode();
            if (code == 404) {
                throw new ImportException(Reason.NOT_FOUND, id);
            }
            if (code != 200) {
                throw new ImportException(Reason.NETWORK, "HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                body = readCapped(in);
            } finally {
                c.disconnect();
            }
        } catch (final ImportException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            throw new ImportException(Reason.NETWORK, String.valueOf(e.getMessage()));
        }
        return save(id, body);
    }

    /**
     * Guarda un {@code .dck} de cubo como importado, comprobando antes que se
     * lee. Separado de la descarga para poder probarlo sin red.
     */
    public static String save(final String id, final byte[] dck) throws ImportException {
        final File dir = folder();
        dir.mkdirs();
        final File tmp = new File(dir, id + ".dck.part");
        try (OutputStream out = new java.io.FileOutputStream(tmp)) {
            out.write(dck);
        } catch (final IOException e) {
            throw new ImportException(Reason.NETWORK, String.valueOf(e.getMessage()));
        }
        final Deck deck = read(tmp);
        if (deck == null || deck.getMain().isEmpty()) {
            tmp.delete();
            // CubeCobra devuelve una pagina, no un 404, si el cubo no existe.
            throw new ImportException(Reason.NOT_FOUND, id);
        }
        final File dest = new File(dir, id + ".dck");
        dest.delete();
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            throw new ImportException(Reason.NETWORK, "rename " + dest);
        }
        // El nombre con el que sale en la lista, contando con los de Forge.
        for (final CustomLimited c : NeoDraft.cubes()) {
            if (dest.equals(fileOf(c))) {
                return c.getName();
            }
        }
        return deck.getName();
    }

    private static byte[] readCapped(final InputStream in) throws IOException {
        final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        final byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) {
                throw new IOException("too big");
            }
        }
        return out.toByteArray();
    }

    private static Deck read(final File f) {
        try {
            return DeckSerializer.fromFile(f);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido leer el cubo " + f + ": " + e);
            return null;
        }
    }

    /**
     * Los cubos importados, como {@code CustomLimited}. Un nombre que ya
     * exista en {@code taken} (los de Forge, u otro importado) se distingue
     * con el ID: {@code startCube} los busca por nombre.
     */
    public static synchronized List<CustomLimited> imported(final Set<String> taken) {
        FILES.clear();
        final List<CustomLimited> out = new ArrayList<>();
        final File[] files = folder().listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".dck"));
        if (files == null) {
            return out;
        }
        java.util.Arrays.sort(files);
        for (final File f : files) {
            final Deck deck = read(f);
            if (deck == null || deck.getMain().isEmpty()) {
                continue;
            }
            final String id = f.getName().substring(0, f.getName().length() - 4);
            String name = deck.getName() == null || deck.getName().trim().isEmpty() ? id : deck.getName().trim();
            if (taken.contains(name)) {
                name = name + " (" + id + ")";
            }
            taken.add(name);
            final CustomLimited cd = new CustomLimited(name, SealedTemplate.genericNoSlotBooster.getSlots());
            cd.setNumPacks(3);
            cd.setSingleton(true);
            cd.setCustomRankingsFile("rankings_cubecobra.txt");
            cd.setCardPool(deck.getMain());
            FILES.put(name, f);
            out.add(cd);
        }
        return out;
    }

    /** Si ese cubo es uno importado (de los que lista {@link #imported}). */
    public static synchronized boolean isImported(final CustomLimited cube) {
        return cube != null && FILES.containsKey(cube.getName());
    }

    private static synchronized File fileOf(final CustomLimited cube) {
        return FILES.get(cube.getName());
    }

    /** Quita un cubo importado. true si estaba. */
    public static synchronized boolean delete(final CustomLimited cube) {
        final File f = cube == null ? null : FILES.remove(cube.getName());
        return f != null && f.delete();
    }

    /** Para las pruebas: los nombres de los importados. */
    static synchronized Collection<String> importedNames() {
        return new ArrayList<>(FILES.keySet());
    }
}
