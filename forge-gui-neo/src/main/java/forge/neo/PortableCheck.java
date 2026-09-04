package forge.neo;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

/**
 * Que el fichero de perfil que le dejamos a Forge vuelva IGUAL, con acentos.
 *
 * <p><b>De donde sale esta prueba.</b> El 01-09-2026 alguien que se bajo el zip
 * no pudo ni abrirlo: <i>cannot create profile directory:
 * C:\Users\U&Aring;&frac14;ytkownik\...</i>. Su usuario de Windows se llama
 * <i>Uzytkownik</i> —con la zeta con punto del polaco— y {@link NeoPortable}
 * escribia el {@code forge.profile.properties} con un {@code Writer} en UTF-8
 * mientras Forge lo lee con {@code Properties.load(InputStream)}
 * ({@code ForgeProfileProperties:64}), que decodifica <b>siempre</b> en
 * ISO-8859-1. Los bytes crudos volvian convertidos en otra ruta; esa carpeta no
 * existia y encima colgaba de {@code C:\Users\}, donde un usuario normal no
 * puede crear nada.
 *
 * <p><b>Por que hace falta una prueba y no basta con el arreglo.</b> Porque el
 * fallo <b>no se ve desde aqui</b>: esta maquina se llama {@code Ana}, o sea
 * ASCII puro, y con una ruta ASCII las dos formas de escribir dan exactamente
 * el mismo fichero. Estuvo roto desde el primer dia y jugando no habia forma de
 * enterarse; lo destapo el ordenador de otro. Y una ruta con acentos no es un
 * caso raro: vale cualquier {@code Jose}, {@code Munoz} o nombre polaco, checo
 * o turco.
 *
 * <p><b>Que se comprueba, y por que asi.</b> La garantia buena no es "se lee
 * bien con ISO-8859-1", que ataria el arreglo a como lee Forge <i>hoy</i>: es
 * que el fichero salga en <b>ASCII puro</b>, con lo demas escapado a su codigo
 * Unicode. Asi vuelve intacto lo lea Forge como lo lea —{@code load(InputStream)}
 * y {@code load(Reader)} deshacen los dos ese escape—, y el dia que Card-Forge
 * cambie de lector esto sigue en verde sin que nadie lo toque.
 *
 * <p>No carga el motor: {@code NeoPortable} corre antes que el, y para
 * comprobar lo que escribe no hacen falta las cartas. Por eso {@code NeoMain}
 * la despacha antes de {@code NeoBoot.loadEngine}.
 */
public final class PortableCheck {

    private PortableCheck() {
    }

    private static int passed;
    private static int failed;

    /** Nombres de carpeta que rompian esto, de menos a mas exotico. */
    private static final String[] NAMES = {
        "NeoForge",                            // control: ASCII puro, el unico caso que se probaba
        "Jose Ramon Munoz",                    // ASCII, pero con espacios
        "Jos\u00e9 Ram\u00f3n Mu\u00f1oz",     // acentos y enye: DENTRO de Latin-1
        "U\u017cytkownik",                     // el caso reportado: FUERA de Latin-1
        "\u041c\u043e\u0439 Forge",            // cirilico
        "\u30d5\u30a9\u30fc\u30b8",            // japones
    };

    public static void run() throws IOException {
        passed = 0;
        failed = 0;

        final File tmp = Files.createTempDirectory("neo-portable-check").toFile();
        System.out.println("  banco de pruebas: " + tmp);
        System.out.println();

        for (final String name : NAMES) {
            one(new File(tmp, name));
            System.out.println();
        }

        strayIsCleanedUp(new File(tmp, "restos"));
        System.out.println();

        System.out.printf(Locale.ROOT, "  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(
                    failed + " comprobacion(es) del perfil portable han fallado");
        }
    }

    /**
     * Un caso: se monta la carpeta, se aplica el modo portable y se relee el
     * fichero por los dos caminos posibles.
     *
     * <p>Se llama a {@link NeoPortable#apply()} de verdad, con sus propiedades
     * puestas, en vez de a un metodo interno: lo que hay que probar es el
     * camino que recorre el juego al arrancar, no un trozo suelto de el.
     */
    private static void one(final File home) throws IOException {
        System.out.println("  " + home.getName());
        if (!home.isDirectory() && !home.mkdirs()) {
            check(false, "se puede crear la carpeta (el disco admite ese nombre)");
            return;
        }

        final File datos = new File(home, "datos");
        System.setProperty("forge.assetsDir", home.getPath() + File.separator);
        System.setProperty("neo.dataDir", datos.getPath());
        NeoPortable.apply();

        if (!NeoPortable.isPortable()) {
            check(false, "el modo portable se activa");
            return;
        }
        check(datos.getCanonicalFile().equals(NeoPortable.dataDir()),
                "los datos apuntan a la carpeta pedida");

        final File profile = new File(home, "forge.profile.properties");
        if (!profile.isFile()) {
            check(false, "se ha escrito el forge.profile.properties");
            return;
        }

        // 1. ASCII puro. Es LA garantia: sin ella todo lo demas depende de con
        //    que lector se lea, y el lector no es nuestro.
        final byte[] raw = Files.readAllBytes(profile.toPath());
        int bad = -1;
        for (int i = 0; i < raw.length; i++) {
            if ((raw[i] & 0xFF) > 0x7E) {
                bad = i;
                break;
            }
        }
        check(bad < 0, "el fichero es ASCII puro"
                + (bad < 0 ? "" : " (byte " + bad + " = 0x"
                        + Integer.toHexString(raw[bad] & 0xFF) + ")"));

        final String esperado = NeoPortable.dataDir().getPath();

        // 2. Como lo lee Forge HOY: Properties.load(InputStream), ISO-8859-1.
        final Properties porStream = new Properties();
        try (InputStream in = new FileInputStream(profile)) {
            porStream.load(in);
        }
        check(esperado.equals(porStream.getProperty("userDir")),
                "vuelve igual con load(InputStream), que es como lee Forge"
                + diff(esperado, porStream.getProperty("userDir")));

        // 3. Y si algun dia leyera con un Reader en UTF-8, tambien.
        final Properties porReader = new Properties();
        try (Reader r = new InputStreamReader(new FileInputStream(profile),
                StandardCharsets.UTF_8)) {
            porReader.load(r);
        }
        check(esperado.equals(porReader.getProperty("userDir")),
                "y tambien con load(Reader UTF-8), por si cambia de lector");

        // 4. Las otras tres rutas, que se escapan igual de facil.
        final String cache = new File(NeoPortable.dataDir(), "cache").getPath();
        final String decks = new File(NeoPortable.dataDir(), "decks").getPath();
        final String pics = porStream.getProperty("cardPicsDir");
        check(cache.equals(porStream.getProperty("cacheDir"))
                && decks.equals(porStream.getProperty("decksDir"))
                && pics != null && pics.startsWith(esperado),
                "las cuatro rutas caen dentro de la carpeta del juego");

        // 5. Y esa ruta es una carpeta que EXISTE: que el nombre sobreviva al
        //    fichero no sirve de nada si luego mkdirs no puede crearla, que es
        //    justo como se manifestaba el fallo.
        check(new File(porStream.getProperty("userDir")).isDirectory(),
                "y esa ruta es una carpeta que existe");
    }

    /**
     * Que un perfil portable OLVIDADO se quite solo en el siguiente arranque
     * normal — y que uno del usuario no se toque.
     *
     * <p>Esto no es una comprobacion de cortesia: el fallo que cubre paso de
     * verdad el 03-09-2026 y fue de los caros de encontrar, porque <b>no falla
     * nada</b>. {@link NeoPortable#write} deja el {@code forge.profile.properties}
     * en el directorio de assets, que en el arbol de desarrollo es
     * {@code forge/forge-gui/} — una carpeta compartida por TODAS las
     * ejecuciones. Una prueba con {@code -Dneo.dataDir} lo escribe apuntando a
     * su carpeta temporal y no lo quita, y a partir de ahi el juego normal
     * arranca leyendo los datos de ahi: mazos, fundas, tapete, musica y
     * ajustes <b>desaparecidos</b>, sin un solo error. Reportado jugando.
     *
     * <p>Las dos mitades hacen falta y son igual de importantes: que el
     * nuestro se quite, y que <b>el del usuario NO</b> — Forge admite que uno
     * ponga su propio perfil a mano para llevarse los datos a otro disco, y
     * borrarselo seria el mismo fallo del reves.
     */
    private static void strayIsCleanedUp(final File home) throws IOException {
        System.out.println("  perfil olvidado por una prueba anterior");
        if (!home.isDirectory() && !home.mkdirs()) {
            check(false, "se puede crear la carpeta");
            return;
        }
        System.setProperty("forge.assetsDir", home.getPath() + File.separator);
        final File profile = new File(home, "forge.profile.properties");

        // 1. Lo escribe una ejecucion portable, como haria portablecheck.
        System.setProperty("neo.dataDir", new File(home, "datos").getPath());
        NeoPortable.apply();
        check(profile.isFile(), "una ejecucion portable deja el perfil escrito");

        // 2. Y el siguiente arranque NORMAL (sin la bandera) se lo encuentra.
        System.clearProperty("neo.dataDir");
        NeoPortable.apply();
        check(!profile.isFile(),
                "un arranque normal lo quita: los datos vuelven a la carpeta de siempre");

        // 3. Pero el que haya puesto el usuario a mano se queda donde esta.
        Files.write(profile.toPath(),
                ("#mi perfil, puesto a mano" + System.lineSeparator()
                        + "userDir=D\\:\\\\MisDatos" + System.lineSeparator())
                        .getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
        NeoPortable.apply();
        check(profile.isFile(), "y el perfil que puso el usuario a mano NO se toca");
    }

    private static String diff(final String esperado, final String leido) {
        if (esperado.equals(leido)) {
            return "";
        }
        return System.lineSeparator() + "           esperaba: " + esperado
                + System.lineSeparator() + "              vino: " + leido;
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("    OK   " + what);
        } else {
            failed++;
            System.out.println("    MAL  " + what);
        }
    }
}
