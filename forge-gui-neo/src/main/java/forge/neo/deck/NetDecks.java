package forge.neo.deck;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.deck.NetDeckCategory;
import forge.game.GameType;
import forge.localinstance.properties.ForgeConstants;
import forge.util.FileUtil;

/**
 * Mazos hechos, bajados de internet.
 *
 * <p>Otra cosa que el motor trae entera y no estabamos usando. Forge mantiene
 * unas listas de <b>categorias</b> en {@code forge-gui/res/lists/} — "EDHREC
 * Average Decks", "Budget Commander", "Commander Clash"... — y cada una es un
 * zip de mazos en el repositorio {@code forge-extras}. La clase que las baja y
 * las lee es {@link NetDeckCategory}, y es publica.
 *
 * <p>Aqui no hay ni una linea de red ni de descompresion: solo se lee el
 * fichero de categorias del formato que toque y se le pide al motor la que se
 * quiera. Lo unico que anyadimos es {@link forge.neo.platform.NeoDownloads},
 * que le da al descargador de Forge los widgets que espera.
 *
 * <p><b>Necesita linea, y es lo unico ademas de las imagenes.</b> Todo lo de
 * aqui falla en silencio y sin bloquear: si no hay internet, {@link
 * #cached(GameType)} sigue devolviendo lo que ya te bajaste y la aplicacion no
 * se entera de que falta algo.
 *
 * <p><b>Donde caen.</b> En {@code %APPDATA%\Forge\decks\net\<categoria>\}, que
 * es la carpeta que el motor ya usa para esto — <b>no</b> en la carpeta de
 * mazos que compartimos con la instalacion normal de Forge. Un mazo bajado no
 * es tuyo: se juega desde su pestanya, y si lo quieres tocar, se abre en el
 * constructor y se guarda con tu nombre.
 */
public final class NetDecks {

    private NetDecks() {
    }

    /** Una categoria: un nombre y de donde sale. */
    public static final class Category {
        private final String name;
        private final GameType gameType;
        private int downloaded = -1;

        Category(final String name, final GameType gameType) {
            this.name = name;
            this.gameType = gameType;
        }

        public String getName() {
            return name;
        }

        /**
         * Cuantos mazos tienes ya de esta categoria. Cero = sin bajar.
         *
         * <p>Se cuenta mirando la carpeta, no abriendo los mazos: son cientos
         * de ficheros y esto se llama al pintar la lista entera.
         */
        public int getDownloaded() {
            if (downloaded < 0) {
                downloaded = countFiles(name);
            }
            return downloaded;
        }

        public boolean isDownloaded() {
            return getDownloaded() > 0;
        }

        /** Vuelve a contar tras una descarga. */
        public void refresh() {
            downloaded = -1;
        }

        GameType getGameType() {
            return gameType;
        }
    }

    /**
     * Las categorias que hay para este formato.
     *
     * <p>Se lee el mismo fichero {@code nombre | url} que lee el motor. No se
     * duplica nada: leerlo es la unica forma de saber los NOMBRES, y el nombre
     * es lo unico que hace falta para pedirle una al motor.
     *
     * <p>Los formatos que Forge no cubre — el draft, por ejemplo — devuelven
     * una lista vacia, y la pantalla que llame se apaga sola.
     */
    public static List<Category> categories(final GameType gameType) {
        final List<Category> out = new ArrayList<>();
        final String file = listFileFor(gameType);
        if (file == null || !FileUtil.doesFileExist(file)) {
            return out;
        }
        for (final String line : FileUtil.readFile(file)) {
            final int bar = line.indexOf('|');
            if (bar > 0) {
                out.add(new Category(line.substring(0, bar).trim(), gameType));
            }
        }
        return out;
    }

    /** Si este formato tiene mazos de internet. */
    public static boolean isSupported(final GameType gameType) {
        return listFileFor(gameType) != null;
    }

    /**
     * Baja una categoria. <b>Tarda, y no se puede llamar desde el hilo de
     * JavaFX.</b>
     *
     * <p>El motor lo comprueba activamente: su {@code WaitCallback.invokeAndWait}
     * empieza con {@code FThreads.assertExecutedByEdt(false)}. Es la misma regla
     * de siempre — bloquear el hilo de interfaz es un cuelgue — solo que aqui
     * el motor la hace cumplir.
     *
     * @return cuantos mazos han llegado, o -1 si no se ha podido
     */
    public static int download(final Category category) {
        try {
            final NetDeckCategory loaded = NetDeckCategory.selectAndLoad(
                    category.getGameType(), category.getName(), true);
            category.refresh();
            if (loaded == null) {
                return -1;
            }
            int n = 0;
            for (final Deck ignored : loaded) {
                n++;
            }
            return n;
        } catch (final RuntimeException e) {
            // Sin linea, o con la url caida. Se dice y se sigue: en el pueblo
            // no hay cobertura y la aplicacion tiene que funcionar igual.
            System.err.println("[neo] no se ha podido bajar " + category.getName() + ": " + e);
            category.refresh();
            return -1;
        }
    }

    /**
     * Los mazos de una categoria que YA estan en el disco.
     *
     * <p>Sin red: con el nombre puesto y sin forzar, el motor se limita a leer
     * la carpeta ({@code NetDeckStorageBase.loadCachedDecks}).
     */
    public static List<Deck> decksOf(final Category category) {
        final List<Deck> out = new ArrayList<>();
        try {
            final NetDeckCategory loaded = NetDeckCategory.selectAndLoad(
                    category.getGameType(), category.getName(), false);
            if (loaded != null) {
                loaded.forEach(out::add);
            }
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido leer " + category.getName() + ": " + e);
        }
        return out;
    }

    /**
     * Todo lo que te has bajado de este formato, junto.
     *
     * <p>Es lo que alimenta la pestanya "de internet" de la pantalla de mazos.
     * No toca la red: solo se abren las categorias que tienen ficheros.
     */
    public static List<Deck> cached(final GameType gameType) {
        final List<Deck> out = new ArrayList<>();
        for (final Category c : categories(gameType)) {
            if (c.isDownloaded()) {
                out.addAll(decksOf(c));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------

    /** Que fichero de categorias le toca a cada formato. Tabla, sin reglas. */
    private static String listFileFor(final GameType gameType) {
        switch (gameType) {
            case Constructed:
            case Gauntlet:
                return ForgeConstants.NET_DECKS_LIST_FILE;
            case Commander:
                return ForgeConstants.NET_DECKS_COMMANDER_LIST_FILE;
            case Brawl:
                return ForgeConstants.NET_DECKS_BRAWL_LIST_FILE;
            case Oathbreaker:
                return ForgeConstants.NET_DECKS_OATHBREAKER_LIST_FILE;
            case TinyLeaders:
                return ForgeConstants.NET_DECKS_TINYLEADERS_LIST_FILE;
            default:
                return null;
        }
    }

    /** Cuantos .dck hay en la carpeta de una categoria. */
    private static int countFiles(final String name) {
        final java.io.File dir = new java.io.File(ForgeConstants.DECK_NET_DIR + name);
        if (!dir.isDirectory()) {
            return 0;
        }
        final String[] files = dir.list((d, f) ->
                f.toLowerCase(java.util.Locale.ROOT).endsWith(".dck"));
        return files == null ? 0 : files.length;
    }
}
