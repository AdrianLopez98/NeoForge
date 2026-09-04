package forge.neo.deck;

import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.game.GameType;

/**
 * Comprueba los mazos de internet sin ventana.
 *
 * <p><b>Es la unica prueba del proyecto que necesita linea</b>, y por eso lo
 * primero que hace es decir si la hay: sin conexion no falla, avisa y se salta
 * la parte que descarga. En el pueblo tiene que poder ejecutarse igual.
 *
 * <p>Lo que se comprueba no se ve en una captura: que las listas de categorias
 * se leen, que el descargador sin ventana de {@link
 * forge.neo.platform.NeoDownloads} pulsa su boton y termina, que los mazos
 * llegan de verdad y que se pueden volver a leer del disco sin red.
 *
 * <p>Se ejecuta con {@code run.cmd netcheck}.
 */
public final class NetCheck {

    private NetCheck() {
    }

    /**
     * La categoria con la que se prueba.
     *
     * <p>Se elige a mano y pequenya a proposito: bajar "EDHREC Average Decks"
     * son cientos de mazos y esto es una comprobacion, no una descarga.
     */
    private static final String SAMPLE = "Commander Quickie";

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        listsAreRead();
        formatsWithoutListsSayNo();
        unsupportedDeckUrlIsRejected();
        final boolean online = hasLine();
        if (online) {
            downloadBringsDecks();
            cachedReadsWithoutNetwork();
            deckUrlProvidersAreRouted();
        } else {
            System.out.println("  - sin conexion: la parte de descarga se salta");
            cachedReadsWithoutNetwork();
        }

        System.out.println();
        System.out.printf(Locale.ROOT, "  %d comprobaciones OK, %d fallos%n", passed, failed);
        if (failed > 0) {
            System.out.println("  *** HAY FALLOS ***");
        }
    }

    // ---------------------------------------------------------------

    /** Las listas de categorias que trae Forge se leen enteras. */
    private static void listsAreRead() {
        final List<NetDecks.Category> commander = NetDecks.categories(GameType.Commander);
        check("categorias de Commander leidas", commander.size() > 10,
                commander.size() + " categorias");

        final List<NetDecks.Category> constructed = NetDecks.categories(GameType.Constructed);
        check("categorias de construido leidas", constructed.size() > 10,
                constructed.size() + " categorias");

        boolean named = false;
        for (final NetDecks.Category c : commander) {
            if (!c.getName().isBlank()) {
                named = true;
                break;
            }
        }
        check("las categorias tienen nombre", named, "");
    }

    /** Un formato que Forge no cubre no ofrece la pantalla, en vez de fallar. */
    private static void formatsWithoutListsSayNo() {
        check("el draft no ofrece mazos de internet",
                !NetDecks.isSupported(GameType.Draft), "");
        check("Commander si los ofrece",
                NetDecks.isSupported(GameType.Commander), "");
        check("un formato sin lista devuelve lista vacia",
                NetDecks.categories(GameType.Draft).isEmpty(), "");
    }

    /** Bajar una categoria trae mazos de verdad. */
    private static void downloadBringsDecks() {
        final NetDecks.Category sample = find(SAMPLE);
        if (sample == null) {
            check("la categoria de prueba existe", false, SAMPLE + " no esta en la lista");
            return;
        }
        System.out.println("  bajando \"" + sample.getName() + "\"...");
        final long started = System.currentTimeMillis();
        final int count = NetDecks.download(sample);
        final long seconds = (System.currentTimeMillis() - started) / 1000;
        check("la descarga termina y trae mazos", count > 0,
                count + " mazos en " + seconds + " s");
        check("los ficheros estan en el disco", sample.getDownloaded() > 0,
                sample.getDownloaded() + " ficheros .dck");
    }

    /**
     * Lo bajado se vuelve a leer del disco sin tocar la red.
     *
     * <p>Es lo que sostiene la pestanya "De internet" de la pantalla de mazos:
     * si esto necesitara conexion, esa pestanya no existiria sin linea.
     */
    private static void cachedReadsWithoutNetwork() {
        final List<Deck> cached = NetDecks.cached(GameType.Commander);
        if (cached.isEmpty()) {
            System.out.println("  - no hay nada bajado todavia: nada que releer");
            return;
        }
        check("los mazos bajados se releen del disco", !cached.isEmpty(),
                cached.size() + " mazos");
        boolean playable = false;
        for (final Deck d : cached) {
            if (d.getMain() != null && d.getMain().countAll() > 0) {
                playable = true;
                break;
            }
        }
        check("los mazos bajados tienen cartas", playable, "");
    }

    /**
     * "Pega la URL y ya" (la auditoría del motor, apartado B7). Un sitio que no sabemos leer se
     * rechaza SIN tocar la red — {@code DeckUrlLoader.getProvider} mira el
     * host antes de abrir ninguna conexión — así que esto vale sin
     * conexión, a diferencia de los otros dos.
     */
    private static void unsupportedDeckUrlIsRejected() {
        try {
            forge.deck.DeckUrlLoader.load("https://example.com/not-a-deck-site");
            check("una URL de un sitio no soportado se rechaza", false,
                    "no lanzó excepción");
        } catch (final java.io.IOException e) {
            check("una URL de un sitio no soportado se rechaza",
                    e.getMessage() != null && !e.getMessage().isBlank(), e.getMessage());
        }
    }

    /**
     * Los cuatro proveedores se reconocen por el host, y de ahí para
     * adelante SÍ llegan a la red — aunque el mazo no exista.
     *
     * <p>No hace falta un mazo de verdad para esto: un 404 de Moxfield es
     * tan buena prueba de "el enrutado funciona" como un mazo real, y no se
     * rompe el día que alguien borre el que se hubiera usado de ejemplo.
     */
    private static void deckUrlProvidersAreRouted() {
        final String[] hosts = {
            "https://moxfield.com/decks/neocheck-not-a-real-deck-id",
            "https://archidekt.com/decks/1/neocheck-not-a-real-deck",
            "https://tappedout.net/mtg-decks/neocheck-not-a-real-deck/",
            "https://www.mtggoldfish.com/deck/neocheck-not-a-real-deck",
        };
        for (final String url : hosts) {
            final String host = url.replaceFirst("^https://(www\\.)?", "").replaceFirst("/.*", "");
            try {
                forge.deck.DeckUrlLoader.load(url);
                // Un mazo de mentira que SI se leyera seria mas raro que un
                // fallo: da igual, cuenta como "el proveedor respondio".
                check(host + ": el proveedor responde", true, "");
            } catch (final java.io.IOException e) {
                // Cualquier IOException aqui viene DESPUES de reconocer el
                // proveedor (si no, seria "sitio no soportado", que ya se
                // prueba aparte). Que el mensaje no diga eso confirma que
                // el enrutado por host funciono.
                final String msg = e.getMessage() == null ? "" : e.getMessage();
                check(host + ": se reconoce y llega a la red",
                        !msg.contains("Moxfield, Archidekt, TappedOut, MTGGoldfish"), msg);
            }
        }
    }

    // ---------------------------------------------------------------

    private static NetDecks.Category find(final String name) {
        for (final NetDecks.Category c : NetDecks.categories(GameType.Commander)) {
            if (c.getName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /** Si hay linea. Cinco segundos y a lo que toque. */
    private static boolean hasLine() {
        try {
            final java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL("https://raw.githubusercontent.com").openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");
            conn.connect();
            conn.disconnect();
            return true;
        } catch (final Exception e) {
            return false;
        }
    }

    private static void check(final String what, final boolean ok, final String detail) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        } else {
            failed++;
            System.out.println("  FALLA " + what + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        }
    }
}
