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

import forge.StaticData;
import forge.card.CardEdition;
import forge.card.CardSplitType;
import forge.deck.Deck;
import forge.gui.download.CdnUuidCache;
import forge.gui.download.GuiDownloadService;
import forge.gui.download.ScryfallBulkDataSync;
import forge.item.IPaperCard;
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
 *
 * <p><b>Y se baja de la CDN, no de la API</b> (23-09-2026). La primera version
 * pedia cada carta a {@code api.scryfall.com/cards/named}, que Scryfall limita
 * a <b>dos por segundo</b>: 40.000 fotos, mas los enfriamientos de los 429,
 * salian a <b>34 horas</b> (reportado en Discord). Las fotos de verdad viven en
 * {@code cards.scryfall.io}, que no tiene limite, pero para pedirlas hay que
 * saber el identificador de Scryfall de cada impresion. Eso tambien lo trae
 * Forge: {@code ScryfallBulkDataSync} baja su indice entero (~75 MB, una vez) y
 * lo deja en {@code CdnUuidCache}, que es lo mismo que hace su propio
 * descargador de ediciones. La API queda solo de red para lo que el indice no
 * tenga.
 */
public final class ArtDownload extends GuiDownloadService {

    /** Que se baja. */
    public enum Scope {
        /** Todas las cartas del juego. */
        ALL,
        /** Solo las de los mazos del jugador: unos cientos de MB y un minuto. */
        MY_DECKS,
        /**
         * TODAS las impresiones y artes (~95.000, ~7 GB), como el descargador
         * de Forge. Pedido en Discord el 23-09-2026. No lo baja esta clase:
         * lo baja el propio Forge ({@link #everyPrinting()}), a la cache normal
         * de impresiones, que es la que {@code CardImages} mira primero.
         */
        EVERY_PRINTING
    }

    /**
     * El descargador para {@link Scope#EVERY_PRINTING}: el de Forge tal cual
     * ({@code GuiDownloadFilteredCardImages}), que ya va por la CDN con el
     * mismo indice ({@code CdnUuidCache}) y deja cada impresion con el nombre
     * que el motor espera. Nada que copiar ni que mantener alineado.
     */
    public static GuiDownloadService everyPrinting() {
        return new forge.gui.download.GuiDownloadFilteredCardImages(c -> true);
    }

    private final Scope scope;

    /**
     * Si faltan mas juegos que estos en el indice, se baja el indice entero en
     * vez de ir juego a juego. El mismo umbral que el descargador de Forge
     * ({@code GuiDownloadFilteredCardImages}).
     */
    private static final int BULK_SYNC_THRESHOLD = 15;

    /**
     * Quien quiere saber que se esta bajando el indice, y cuanto va (de 0 a 1,
     * o -1 si no se sabe). Lo pinta la pantalla con su texto traducido: el del
     * motor viene en ingles.
     */
    private volatile java.util.function.DoubleConsumer onIndex;

    public ArtDownload(final Scope scope) {
        this.scope = scope;
    }

    public void setOnIndex(final java.util.function.DoubleConsumer listener) {
        this.onIndex = listener;
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
        return list(true);
    }

    /** Una foto por bajar: la carta, que cara y a que fichero. */
    private static final class Want {
        final PaperCard card;
        final String name;
        final boolean back;

        Want(final PaperCard card, final String name, final boolean back) {
            this.card = card;
            this.name = name;
            this.back = back;
        }
    }

    /**
     * La lista.
     *
     * @param resolve {@code false} solo cuenta: no baja el indice de Scryfall
     *                (75 MB) para saber un numero
     */
    private Map<String, String> list(final boolean resolve) throws UnsupportedEncodingException {
        queued.clear();
        // Solo pruebas: -Dneo.art.testLimit=3 baja tres y para. Es lo que
        // permite comprobar de verdad — con red — que la URL sigue sirviendo y
        // que el fichero cae donde OfflineArt lo busca, sin bajarse dos gigas.
        final int limit = Integer.getInteger("neo.art.testLimit", 0);
        final Map<String, Want> wants = new LinkedHashMap<>();
        for (final PaperCard c : cards()) {
            add(wants, c, false);
            if (c.hasBackFace()) {
                add(wants, c, true);
            }
            if (limit > 0 && wants.size() >= limit) {
                break;
            }
        }
        if (resolve && !wants.isEmpty()) {
            warmIndex(wants.values());
        }
        final Map<String, String> out = new LinkedHashMap<>();
        int cdn = 0;
        for (final Map.Entry<String, Want> e : wants.entrySet()) {
            final Want w = e.getValue();
            String url = resolve ? cdnUrl(w.card, w.back) : null;
            if (url != null) {
                cdn++;
            } else {
                url = apiUrl(w.name, w.back);
            }
            out.put(e.getKey(), url);
        }
        if (resolve) {
            System.out.println("[arte] " + out.size() + " por bajar: " + cdn + " de la CDN y "
                    + (out.size() - cdn) + " por la API (2/s)");
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
        if (scope != Scope.MY_DECKS) {
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
    private void add(final Map<String, Want> out, final PaperCard c, final boolean back) {
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
        out.put(path, new Want(c, name, back));
    }

    /**
     * Que el indice de Scryfall este en disco, para poder ir por la CDN.
     *
     * <p>Si faltan pocos juegos no se hace nada: esas cartas iran por la API,
     * como antes, y para unos pocos mazos no se nota. Si faltan muchos (la
     * primera vez que se baja TODO), se baja el indice entero de una vez: ~75
     * MB y un par de minutos, contra un dia y medio de API.
     */
    private void warmIndex(final Iterable<Want> wants) {
        final Set<String> needSync = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (final Want w : wants) {
            final String code = scryfallCode(w.card);
            if (code != null && !CdnUuidCache.isSetCached(code)) {
                needSync.add(code);
            }
        }
        if (needSync.size() <= BULK_SYNC_THRESHOLD) {
            return;
        }
        final java.util.function.DoubleConsumer listener = onIndex;
        if (listener != null) {
            listener.accept(-1);
        }
        // Se avisa solo cuando cambia el porcentaje: el motor lo cuenta por
        // lineas leidas, y eso son miles de runLater para una etiqueta.
        final int[] last = {-2};
        final int sets = ScryfallBulkDataSync.sync(ScryfallBulkDataSync.BULK_TYPE_DEFAULT_CARDS, null,
                (message, fraction) -> {
                    final int pct = fraction < 0 ? -1 : (int) Math.round(fraction * 100);
                    if (listener != null && pct != last[0]) {
                        last[0] = pct;
                        listener.accept(pct < 0 ? -1 : pct / 100.0);
                    }
                }, () -> cancel);
        System.out.println("[arte] indice de Scryfall: " + sets + " juegos ("
                + needSync.size() + " faltaban)");
    }

    /** El codigo de Scryfall del juego de esa impresion, o null. */
    private static String scryfallCode(final PaperCard p) {
        final CardEdition edition = StaticData.instance().getEditions().get(p.getEdition());
        if (edition == null) {
            return null;
        }
        final String code = edition.getScryfallCode();
        return code == null || code.isEmpty() ? null : code;
    }

    /**
     * La foto en la CDN, si el indice la conoce.
     *
     * <p>Se prueba primero la impresion elegida y, si esa no esta (una promo
     * sin numero, un juego que Scryfall llama distinto), las demas impresiones
     * de la misma carta: aqui se quiere UNA foto de la carta, no esa en
     * concreto.
     *
     * <p>La trasera solo va por aqui si es una doble cara de verdad
     * (transformar o modal): en la CDN es el mismo identificador con
     * {@code /back/}. Las meld, flip y compania tienen la "otra cara" en otra
     * carta o dentro de la misma foto, y eso lo resuelve bien la API por
     * nombre, que es lo que habia.
     */
    private static String cdnUrl(final PaperCard c, final boolean back) {
        if (back) {
            final CardSplitType split = c.getRules().getSplitType();
            if (split != CardSplitType.Transform && split != CardSplitType.Modal) {
                return null;
            }
        }
        final String face = back ? "back" : "";
        final String url = cdnUrlOf(c, face);
        if (url != null) {
            return url;
        }
        for (final PaperCard p : FModel.getMagicDb().getCommonCards().getAllCards(c.getName())) {
            final String other = cdnUrlOf(p, face);
            if (other != null) {
                return other;
            }
        }
        return null;
    }

    private static String cdnUrlOf(final PaperCard p, final String face) {
        final String cn = p.getCollectorNumber();
        if (cn == null || cn.isEmpty() || "0".equals(cn) || IPaperCard.NO_COLLECTOR_NUMBER.equals(cn)) {
            return null;
        }
        final String code = scryfallCode(p);
        if (code == null) {
            return null;
        }
        try {
            // Solo lectura: nunca dispara una sincronizacion por su cuenta.
            return CdnUuidCache.getCdnUrlIfCached(code, cn, "en", face, "normal");
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * La de antes: la carta por su nombre exacto en la API. Lenta (2/s) pero
     * no necesita indice. "fuzzy" es lo que usa el descargador de Forge porque
     * el va por impresion y arrastra el codigo de edicion; aqui se pide el
     * nombre y punto, asi que exact no puede equivocarse de carta — y si no
     * existe, el servicio lo cuenta como saltada y sigue.
     */
    private static String apiUrl(final String name, final boolean back) throws UnsupportedEncodingException {
        final StringBuilder url = new StringBuilder(ForgeConstants.URL_PIC_SCRYFALL_DOWNLOAD)
                .append("named?exact=")
                .append(URLEncoder.encode(name, StandardCharsets.UTF_8.name()));
        if (back) {
            url.append("&face=back");
        }
        url.append("&format=image&version=normal");
        return url.toString();
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
            return new ArtDownload(scope).list(false).size();
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
