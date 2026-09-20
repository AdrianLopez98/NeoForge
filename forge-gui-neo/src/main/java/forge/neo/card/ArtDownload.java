package forge.neo.card;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.deck.Deck;
import forge.gui.download.GuiDownloadService;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.util.ImageUtil;

/**
 * <b>Bajar el arte de antemano, para jugar sin internet.</b>
 *
 * <p>Pedido por dos jugadores el 20-09-2026, con el mismo argumento: quieren
 * que las cartas salgan <b>siempre</b> con foto, tambien sin conexion, y el
 * zip no puede traerlas — son 2 GB y la mayoria son cartas que ese jugador no
 * vera en su vida. Asi que se bajan cuando se piden, y solo si se piden.
 *
 * <p><b>Aqui no hay descargador.</b> Lo trae Forge entero
 * ({@code GuiDownloadService}) y con lo que cuesta caro hacer bien: el
 * limitador de Scryfall (que ESPERA el enfriamiento en vez de saltarse cartas
 * cuando es una tanda larga), el {@code User-Agent} que Scryfall exige,
 * reintentar en PNG si no hay JPG, crear las carpetas, cancelar a medias y
 * saltarse lo que ya esta. Lo unico nuestro es <b>la lista</b>: que cartas y a
 * que fichero.
 *
 * <p>Y el fichero es el de {@link OfflineArt}, que es quien lo lee luego. El
 * nombre se calcula con <b>sus</b> dos metodos ({@code fileName} y
 * {@code folder}), nunca copiando la regla: si las dos cuentas se separaran,
 * se bajarian 36.000 imagenes que nadie encuentra y no lo diria ningun error.
 * Lo comprueba {@code run.cmd artecheck}.
 *
 * <p>Una imagen <b>por NOMBRE de carta</b>, no por impresion: son 36.000 en vez
 * de 95.000, cubren el 99,8% de lo que sale en una mesa y pesan 2 GB en vez de
 * una barbaridad. La impresion exacta, si el jugador la tiene descargada, sigue
 * ganando — esto es la red de debajo (ver {@code CardImages}).
 */
public final class ArtDownload extends GuiDownloadService {

    /** Que se baja. */
    public enum Scope {
        /** Todas las cartas del juego. */
        ALL,
        /** Solo las de los mazos del jugador: unos cientos de MB y un minuto. */
        MY_DECKS
    }

    private final Scope scope;

    public ArtDownload(final Scope scope) {
        this.scope = scope;
    }

    @Override
    public String getTitle() {
        return "NeoForge - arte de las cartas";
    }

    /**
     * El peso medido de una carta en disco, en KB.
     *
     * <p>No es una estimacion de despacho: son <b>las que se bajan por este
     * camino</b>, medidas (79 KB de media, 73 de mediana, sobre una muestra
     * bajada de verdad). Sirve para decir cuanto va a ocupar ANTES de empezar,
     * que es lo unico que el jugador necesita para decidir.
     *
     * <p>⚠️ La copia portable de {@code D:} tiene las mismas cartas a 56 KB:
     * las bajo el script del proyecto Android, que las <b>recomprime</b>. Aqui
     * se guarda el JPEG tal cual lo da Scryfall — pesa mas y se ve mejor, que
     * es justo lo que persigue todo el trabajo de nitidez ({@code Resample}).
     * Si algun dia se recomprime, este numero cambia.
     */
    public static final int KB_PER_CARD = 79;

    @Override
    protected Map<String, String> getNeededFiles() throws UnsupportedEncodingException {
        final Map<String, String> out = new LinkedHashMap<>();
        queued.clear();
        // Solo pruebas: -Dneo.art.testLimit=3 baja tres y para. Es lo que
        // permite comprobar de verdad — con red — que la URL sigue sirviendo y
        // que el fichero cae donde OfflineArt lo busca, sin bajarse dos gigas.
        final int limit = Integer.getInteger("neo.art.testLimit", 0);
        for (final PaperCard c : cards()) {
            add(out, c, false);
            if (c.hasBackFace()) {
                add(out, c, true);
            }
            if (limit > 0 && out.size() >= limit) {
                break;
            }
        }
        this.count = out.size();
        return out;
    }

    /** Cuantas trajo la ultima lista. Lo pregunta la pantalla al arrancar. */
    private volatile int count;

    public int getCount() {
        return count;
    }

    /**
     * Las cartas que la ultima lista pidio.
     *
     * <p>La necesita el comprobador, y no le vale el catalogo: la lista <b>se
     * salta lo que ya esta en disco</b>, asi que "las N primeras cartas" y
     * "las N que se han bajado" dejan de ser lo mismo en cuanto hay algo
     * bajado. Comparar las equivocadas daba 36 de 40 y parecia un fallo.
     */
    private final List<PaperCard> queued = new java.util.ArrayList<>();

    public List<PaperCard> getQueued() {
        return queued;
    }

    /**
     * De donde salen las cartas.
     *
     * <p>Todas: el catalogo <b>sin duplicados</b> — una impresion por carta —
     * que es justo lo que pide una imagen por nombre. {@code getAllCards()}
     * daria 95.000 impresiones para acabar bajando las mismas 36.000 fotos.
     */
    private Iterable<PaperCard> cards() {
        if (scope == Scope.ALL) {
            return FModel.getMagicDb().getCommonCards().getUniqueCards();
        }
        final Set<PaperCard> mine = new LinkedHashSet<>();
        for (final forge.neo.match.NeoFormat f : forge.neo.match.NeoFormat.values()) {
            final forge.util.storage.IStorage<Deck> storage = storageOf(f);
            if (storage == null) {
                continue;
            }
            for (final Deck deck : storage) {
                for (final Map.Entry<forge.deck.DeckSection, forge.deck.CardPool> s : deck) {
                    for (final Map.Entry<PaperCard, Integer> e : s.getValue()) {
                        mine.add(e.getKey());
                    }
                }
            }
        }
        return mine;
    }

    /** Un formato puede no tener carpeta propia; que eso no tire la lista. */
    private static forge.util.storage.IStorage<Deck> storageOf(final forge.neo.match.NeoFormat f) {
        try {
            return f.storage();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Anyade una carta a la lista, si no la tenemos ya en disco.
     *
     * @param back la cara de atras, que tiene foto propia
     */
    private void add(final Map<String, String> out, final PaperCard c, final boolean back)
            throws UnsupportedEncodingException {
        final String name = back ? ImageUtil.getNameToUse(c, "back")
                : c.getRules().getMainPart().getName();
        if (name == null || name.isEmpty()) {
            return;
        }
        // Las REBALANCEADAS de Arena no se piden: Scryfall no las sirve por
        // ese nombre ("A-Divide by Zero") y cada una seria una peticion tirada
        // y una carta contada como fallo. El motor las trae, pero el deck
        // builder ya las deja fuera por lo mismo, y el propio comprobador de
        // arte las descuenta desde que existe.
        if (name.startsWith("A-") || c.isRebalanced()) {
            return;
        }
        final File dest = destination(name);
        if (dest == null || dest.exists()) {
            return;
        }
        // ⚠️ El servicio URL-DECODIFICA la ruta de destino antes de escribir
        // (GuiDownloadService.decodeURL), porque sus propias claves vienen
        // codificadas. O sea que un "+" en el nombre de una carta se convierte
        // en un ESPACIO: "+2 Mace" se guardaba como " 2 Mace.jpg" y el lector
        // no lo encontraba nunca. Se escapa lo justo que esa decodificacion
        // toca — el porcentaje primero, o se escaparia el escape.
        final String path = dest.getPath().replace("%", "%25").replace("+", "%2B");
        if (out.containsKey(path)) {
            return;
        }
        if (!back) {
            queued.add(c);
        }
        // La carta por su nombre exacto. "fuzzy" es lo que usa el descargador
        // de Forge porque el va por impresion y arrastra el codigo de edicion;
        // aqui se pide el nombre y punto, asi que exact no puede equivocarse de
        // carta — y si no existe, el servicio lo cuenta como saltada y sigue.
        final StringBuilder url = new StringBuilder(ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD)
                .append("named?exact=")
                .append(URLEncoder.encode(name, StandardCharsets.UTF_8.name()));
        if (back) {
            url.append("&face=back");
        }
        url.append("&format=image&version=normal");
        out.put(path, url.toString());
    }

    /**
     * Donde va la foto de esa carta: <b>lo decide {@link OfflineArt}</b>.
     *
     * <p>Publico porque el comprobador lo usa para lo unico que puede romperse
     * en silencio aqui: que esto y el lector dejen de apuntar al mismo sitio.
     */
    public static File destination(final String cardName) {
        final String clean = OfflineArt.fileName(cardName);
        if (clean.isEmpty()) {
            return null;
        }
        return new File(new File(OfflineArt.dir(), OfflineArt.folder(clean)), clean + ".jpg");
    }

    /**
     * <b>Lo que hay que hacer cuando la descarga para</b> (entera o a medias).
     *
     * <p>Las fotos nuevas no se ven solas: {@link OfflineArt} recuerda los "no
     * esta" de toda la sesion y {@code CardImages} guarda lo ya decodificado y
     * una lista de claves sin imagen. Sin esto, el jugador baja dos gigas y la
     * mesa sigue exactamente igual hasta que reinicie — que es justo el fallo
     * que cazo el comprobador la primera vez que se probo de verdad.
     *
     * <p>Esta aqui, y no en la pantalla, porque lo llaman los dos: la pantalla
     * y el comprobador. Si estuviera solo en la pantalla, el comprobador diria
     * que todo va bien probando otra cosa.
     */
    public static void afterDownload() {
        OfflineArt.forget();
        CardImages.clear();
        CardImages.repaint();
    }

    /**
     * Cuantas fotos faltan, sin bajar nada.
     *
     * <p>Se anda el catalogo entero (unos 33.000) mirando si el fichero esta:
     * un segundo largo en disco duro, mas en un USB. Va en un hilo de fondo y
     * por eso se devuelve un numero y no se pinta nada aqui.
     */
    public static int missing(final Scope scope) {
        try {
            return new ArtDownload(scope).getNeededFiles().size();
        } catch (final UnsupportedEncodingException e) {
            return 0;
        }
    }

    /** Lo que ocupara, en MB, para poder decirlo antes de empezar. */
    public static int megabytes(final int howMany) {
        return (int) Math.round(howMany * KB_PER_CARD / 1024.0);
    }

    /** Las cartas que la lista mirara, para el comprobador. */
    public static List<PaperCard> sample(final int howMany) {
        final List<PaperCard> out = new java.util.ArrayList<>();
        for (final PaperCard c : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            out.add(c);
            if (out.size() >= howMany) {
                break;
            }
        }
        return out;
    }
}
