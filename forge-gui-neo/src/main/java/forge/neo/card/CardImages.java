package forge.neo.card;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import forge.ImageKeys;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.util.ImageUtil;
import javafx.application.Platform;
import javafx.scene.image.Image;

/**
 * Resuelve la imagen de una carta y la cachea ya decodificada.
 *
 * <p>REGLA DE RENDIMIENTO: decodificar un JPEG cuesta milisegundos; hacerlo por
 * frame hunde los fps. Aqui se decodifica UNA vez por clave y se reutiliza el
 * objeto {@link Image}, que JavaFX sube a textura por su cuenta.
 *
 * <p>Flujo para una clave de imagen:
 * <ol>
 *   <li>Si esta en cache, se devuelve.</li>
 *   <li>Si {@link ImageKeys#getImageFile} encuentra el fichero en disco, se
 *       carga en segundo plano.</li>
 *   <li>Si no, se pide a {@code ImageFetcher} que la baje de Scryfall y se
 *       avisa cuando llegue.</li>
 * </ol>
 * Mientras tanto {@link #get} devuelve {@code null} y quien pinta debe usar el
 * dibujo procedural de respaldo.
 */
public final class CardImages {

    private CardImages() {
    }

    /**
     * Cuantas imagenes se guardan a la vez.
     *
     * <p>400 es el mismo numero que trae Forge de fabrica
     * ({@code FPref.UI_IMAGE_CACHE_MAXIMUM}). A 1,27 MB cada una son unos
     * 500 MB en el peor caso, que dentro de {@code -Xmx2g} deja sitio de sobra
     * para el motor y las 33.696 cartas.
     *
     * <p>Es un numero fijo y <b>no se lee de las preferencias de Forge a
     * proposito</b>: esta clase se toca muy pronto y {@code FModel} puede no
     * estar inicializado todavia, asi que preguntarle aqui seria cambiar un
     * problema de memoria por uno de orden de arranque.
     */
    private static final int MAX_CACHED = 400;

    /**
     * Las imagenes ya decodificadas, <b>con techo</b>.
     *
     * <p><b>Por que hay techo.</b> Esto era un {@code ConcurrentHashMap} que
     * solo se vaciaba al cambiar el idioma del arte, o sea que crecia sin
     * limite durante toda la sesion. Y no es poca cosa: una carta de Scryfall
     * son 488x680, que en disco ocupa unos 75 KB pero <b>decodificada son
     * 1,27 MB</b> — lo que ocupa es el mapa de bits, no el fichero. Con
     * {@code -Xmx2g} eso da unas <b>1.400 cartas distintas por sesion</b>
     * antes de quedarse sin memoria, y pasear por la coleccion de una aventura
     * o paginar el catalogo del deck builder pasa de 1.400 sin despeinarse.
     *
     * <p>No es un invento nuestro: es lo que hace Forge en
     * {@code forge-gui-desktop/src/main/java/forge/ImageCache.java}, con el
     * mismo {@code CacheBuilder}, el mismo tope por defecto y la misma
     * caducidad por falta de uso.
     *
     * <p><b>Expulsar una imagen que esta en pantalla no la borra de la
     * pantalla.</b> El {@code ImageView} que la pinta guarda su propia
     * referencia, asi que la carta se sigue viendo; lo unico que se suelta es
     * NUESTRA copia. Y volver a necesitarla es barato porque {@link #load}
     * mira el disco antes que la red: se vuelve a leer el fichero, no se
     * vuelve a descargar. Por eso esto no puede provocar ni parpadeos ni
     * enfriamientos de Scryfall.
     *
     * <p>Ademas de contarlas, {@code softValues()}: si aun asi faltara
     * memoria, el recolector puede llevarse imagenes que nadie esta mirando
     * antes que tirar la aplicacion. El techo es la regla, esto es la red.
     */
    private static final com.google.common.cache.Cache<String, Image> CACHE =
            com.google.common.cache.CacheBuilder.newBuilder()
                    .maximumSize(MAX_CACHED)
                    .expireAfterAccess(15, java.util.concurrent.TimeUnit.MINUTES)
                    .softValues()
                    .build();

    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    /**
     * Las que no llegaron, <b>con la hora a la que se dejo de intentar</b>.
     *
     * <p>Antes esto era un conjunto y era un billete sin vuelta: una carta que
     * cayera aqui se quedaba sin arte <b>el resto de la sesion</b>, aunque el
     * motivo fuera pasajero. Y casi siempre lo es — Scryfall sirve <b>una
     * peticion cada 100 ms</b>, asi que una pagina del catalogo o el principio
     * de una partida meten decenas de imagenes en cola y muchas ni se llegan a
     * pedir antes de que salte el vigilante. Reportado jugando: <i>"nuevo mazo,
     * ¿por que no veo mis artes?"</i>.
     *
     * <p>Ahora se abandona <b>por ahora</b>: pasado {@link #RETRY_FAILED_MS},
     * la siguiente vez que alguien pida esa carta se vuelve a intentar desde
     * cero. Como quien pinta la pide en cada refresco, las que estan en
     * pantalla se recuperan solas.
     */
    private static final Map<String, Long> FAILED = new ConcurrentHashMap<>();

    /**
     * Las que de verdad no tienen imagen que pedir. Estas si son para siempre.
     *
     * <p>Es el unico caso honesto de "no lo vuelvas a intentar": cartas sin
     * numero de coleccionista o personalizadas, que no existen impresas. Ver
     * {@link #cannotBeDownloaded}.
     */
    private static final Set<String> NO_IMAGE = ConcurrentHashMap.newKeySet();

    /** Cuanto se deja pasar antes de darle otra oportunidad a una imagen. */
    private static final long RETRY_FAILED_MS = 90_000;

    /**
     * Traduce una CLAVE de imagen a la RUTA del fichero.
     *
     * <p>Esto es facil de pasar por alto y sin ello no se ve ni una imagen:
     * {@code CardStateView.getImageKey()} devuelve una clave de busqueda del
     * tipo {@code c:Cabal Coffers|MB2|1}, pero {@link ImageKeys#getImageFile}
     * espera una ruta relativa del tipo {@code MB2/Cabal Coffers.full.jpg}.
     * Pasarle la clave sin traducir hace que no encuentre nada y, peor, que la
     * apunte en su cache negativa para siempre.
     */
    private static String toFilename(final String imageKey) {
        if (imageKey == null || !imageKey.startsWith(ImageKeys.CARD_PREFIX)) {
            return imageKey;
        }
        try {
            final PaperCard pc = ImageUtil.getPaperCardFromImageKey(imageKey);
            if (pc == null) {
                return imageKey;
            }
            return imageKey.endsWith(ImageKeys.BACKFACE_POSTFIX)
                    ? pc.getCardAltImageKey()
                    : pc.getCardImageKey();
        } catch (final Exception e) {
            return imageKey;
        }
    }

    private static File findOnDisk(final String imageKey) {
        return ImageKeys.getImageFile(toFilename(imageKey));
    }

    /**
     * La imagen en el idioma del juego, si ya esta bajada.
     *
     * <p>Va aparte de {@link #findOnDisk} porque {@code ImageKeys} solo mira en
     * la carpeta de Forge, que compartimos con la instalacion normal del
     * usuario. Ver {@link CardArt}.
     */
    private static File findLocalized(final String imageKey) {
        if (!CardArt.isLocalized()) {
            return null;
        }
        final File f = CardArt.fileFor(toFilename(imageKey));
        return f != null && f.exists() ? f : null;
    }

    /** Cartas a las que ya se les ha pedido el arte traducido en esta sesion. */
    private static final Set<String> LOCALIZED_ASKED = ConcurrentHashMap.newKeySet();

    /**
     * Se avisa aqui (en el hilo de JavaFX) cuando han llegado imagenes nuevas.
     *
     * <p><b>No lleva la clave, y es a proposito.</b> Quien escucha repinta el
     * arbol entero — no sabe que nodo mira que carta — asi que una llamada por
     * imagen es una pasada por el arbol por imagen. Importando un mazo de 97
     * cartas eso son 97 barridos completos de la pantalla, y en el hilo de
     * interfaz: la aplicacion se arrastra justo mientras estan llegando las
     * imagenes, que es cuando peor sienta. Ahora se agrupan; ver
     * {@link #NOTIFY_GAP}.
     */
    public interface Listener {
        void imagesReady();
    }

    private static final Set<Listener> LISTENERS = ConcurrentHashMap.newKeySet();

    public static void addListener(final Listener l) {
        LISTENERS.add(l);
    }

    public static void removeListener(final Listener l) {
        LISTENERS.remove(l);
    }

    /**
     * Imagen ya disponible para esta clave, o {@code null}.
     * Si no esta, dispara la carga/descarga en segundo plano.
     */
    public static Image get(final String imageKey) {
        if (imageKey == null || imageKey.isEmpty()) {
            return null;
        }
        final Image cached = CACHE.getIfPresent(imageKey);
        if (cached != null) {
            return cached;
        }
        if (NO_IMAGE.contains(imageKey)) {
            return null;
        }
        final Long gaveUpAt = FAILED.get(imageKey);
        if (gaveUpAt != null) {
            if (System.currentTimeMillis() - gaveUpAt < RETRY_FAILED_MS) {
                return null;
            }
            // Otra oportunidad, y con el contador a cero: lo que fallo casi
            // nunca era la carta, era la cola.
            FAILED.remove(imageKey);
            TRIES.remove(imageKey);
            if (DEBUG) {
                System.out.println("[neo-img] otra oportunidad para " + imageKey);
            }
        }
        if (!PENDING.add(imageKey)) {
            return null;
        }
        watch(imageKey);
        load(imageKey);
        return null;
    }

    // ---------------------------------------------------------------
    // El vigilante: que "pedida" no signifique nunca "olvidada"
    // ---------------------------------------------------------------

    /**
     * Cuanto se le da a una peticion antes de darla por perdida.
     *
     * <p>Generoso a proposito: Scryfall va a una peticion cada 100 ms y en una
     * tanda grande hay cola. Esto no es un tiempo de espera de red, es el
     * plazo tras el cual se acepta que <b>no va a llegar aviso ninguno</b>.
     */
    private static final long WATCHDOG_MS = 20_000;

    /** Cuantas veces se vuelve a intentar una imagen que no llego. */
    private static final int MAX_TRIES = 3;

    private static final Map<String, Integer> TRIES = new ConcurrentHashMap<>();

    /**
     * Vigila una peticion, porque <b>puede no contestar nunca</b>.
     *
     * <p>Aqui estaba el fallo de "busco una carta y no carga". {@code PENDING}
     * era un billete de ida: se anotaba la clave y se confiaba en que el aviso
     * de descarga llegara para quitarla. Pero {@code ImageFetcher.fetchImage}
     * se vuelve por donde ha venido <b>sin llamar al callback</b> en varios
     * casos, y uno de ellos es el que salta justo despues de importar un mazo:
     * si Scryfall nos ha limitado (429), {@code setupObserver} descarta la
     * peticion y se queda en un enfriamiento de <b>cinco minutos</b>. La clave
     * se quedaba en {@code PENDING} para siempre, y como {@link #get} no vuelve
     * a intentar lo que ya esta pedido, esa carta salia en blanco <b>el resto
     * de la sesion</b> — incluso pasado el enfriamiento, incluso buscandola a
     * mano. Sin un solo mensaje de error.
     *
     * <p>Asi que a los {@value #WATCHDOG_MS} ms sin imagen se suelta la clave y
     * se pide un repintado, que hace que quien la necesite la vuelva a pedir.
     * Hasta {@value #MAX_TRIES} veces: una carta que de verdad no tiene imagen
     * no puede quedarse reintentando para siempre. Y mientras dure el
     * enfriamiento de Scryfall <b>el intento no cuenta</b>, porque no se ha
     * llegado a preguntar nada.
     */
    /**
     * El plazo real, que <b>crece con la cola</b>.
     *
     * <p>Scryfall se sirve a una peticion cada 100 ms pase lo que pase, asi que
     * con cincuenta imagenes pedidas la ultima tarda cinco segundos solo en
     * <i>intentarse</i>. Con un plazo fijo, abrir el catalogo o empezar una
     * partida marcaba como imposibles un monton de cartas por las que no se
     * habia llegado a preguntar. El plazo no mide la red: mide cuanto se
     * espera antes de aceptar que <b>no va a llegar aviso ninguno</b>, y eso
     * depende de cuantos haya delante.
     */
    private static long watchdogMs() {
        return Math.min(MAX_WATCHDOG_MS, WATCHDOG_MS + PENDING.size() * 150L);
    }

    /** Techo del plazo: mas alla de esto ya no se esta esperando, se cuelga. */
    private static final long MAX_WATCHDOG_MS = 150_000;

    private static void watch(final String imageKey) {
        TIMER.schedule(() -> {
            if (CACHE.getIfPresent(imageKey) != null || !PENDING.contains(imageKey)) {
                return;
            }
            if (coolingDown()) {
                watch(imageKey);
                return;
            }
            final int tries = TRIES.merge(imageKey, 1, Integer::sum);
            PENDING.remove(imageKey);
            if (tries >= MAX_TRIES) {
                giveUpForNow(imageKey);
                if (DEBUG) {
                    System.out.println("[neo-img] me rindo POR AHORA con " + imageKey
                            + " (se reintenta en " + (RETRY_FAILED_MS / 1000) + "s)");
                }
                return;
            }
            if (DEBUG) {
                System.out.println("[neo-img] sin respuesta, reintento " + tries
                        + " de " + imageKey);
            }
            scheduleRepaint();
        }, watchdogMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Cartas para las que <b>no hay imagen que pedir</b>, y se sabe de antemano.
     *
     * <p>Las de Alchemy (las {@code A-...}) no tienen numero de coleccionista
     * ni edicion de papel: no existen impresas. {@code ImageFetcher} lo
     * comprueba, escribe <i>"does not have a collector number, skipping
     * scryfall download"</i> y <b>se vuelve sin avisar a nadie</b>. Se pregunta
     * aqui la misma condicion para no montar una descarga que ya sabemos que no
     * va a ninguna parte — y sobre todo para que el vigilante no se pase un
     * minuto reintentandolas.
     */
    private static boolean cannotBeDownloaded(final String imageKey) {
        if (imageKey == null || !imageKey.startsWith(ImageKeys.CARD_PREFIX)) {
            return false;
        }
        try {
            final PaperCard pc = ImageUtil.getPaperCardFromImageKey(imageKey);
            return pc != null && (pc.getRules().isCustom()
                    || forge.item.IPaperCard.NO_COLLECTOR_NUMBER.equals(pc.getCollectorNumber()));
        } catch (final Exception e) {
            return false;
        }
    }

    /**
     * Abandona esta imagen <b>por ahora</b>, no para siempre.
     *
     * <p>La diferencia es todo el arreglo: ver {@link #FAILED}.
     */
    private static void giveUpForNow(final String imageKey) {
        FAILED.put(imageKey, System.currentTimeMillis());
    }

    /** Si Scryfall nos ha cortado y estamos esperando a que se le pase. */
    private static boolean coolingDown() {
        return GuiBase.getInterface().getImageFetcher()
                instanceof forge.neo.platform.NeoImageFetcher neo && neo.isCoolingDown();
    }

    /**
     * Cuantos segundos quedan de enfriamiento de Scryfall, o 0 si no hay
     * ninguno en marcha.
     *
     * <p>Lo usa {@link forge.neo.ui.ImageCooldownBadge} para avisar en
     * pantalla: sin esto, un corte real de Scryfall (HTTP 429, cinco minutos
     * sin descargas — ver {@link #FAILED}) se ve identico a un fallo nuestro,
     * cartas sin arte y ni una pista de por que. Reportado jugando: <i>"nuevo
     * mazo, le doy a jugar, y las cartas no tienen arte"</i>.
     */
    public static long coolingDownSecondsLeft() {
        return GuiBase.getInterface().getImageFetcher()
                instanceof forge.neo.platform.NeoImageFetcher neo ? neo.coolingDownSecondsLeft() : 0;
    }

    public static boolean isCached(final String imageKey) {
        return imageKey != null && CACHE.getIfPresent(imageKey) != null;
    }

    /** Si ya se ha avisado de que la cache toco techo. */
    private static final java.util.concurrent.atomic.AtomicBoolean CEILING_LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Deja constancia, UNA vez, de que la cache ha llegado a su tope.
     *
     * <p>Es la unica forma de comprobar que el techo funciona sin montar
     * andamiaje: se juega un rato, se pasea por el catalogo y se mira el
     * registro. Si la linea sale, la cache esta expulsando; si no sale, es que
     * en esa sesion no se llegaron a ver {@value #MAX_CACHED} cartas distintas
     * y por tanto no habia nada que expulsar.
     *
     * <p>Y si algun dia se reporta <i>"las cartas se quedan en blanco"</i>,
     * esta linea es lo primero que hay que mirar: dice si el problema empieza
     * justo cuando la cache empieza a soltar imagenes.
     */
    private static void noteCeiling() {
        if (CACHE.size() >= MAX_CACHED && CEILING_LOGGED.compareAndSet(false, true)) {
            System.out.printf("[neo-img] cache llena: %d imagenes, a partir de ahora"
                    + " se sueltan las que menos se usan%n", MAX_CACHED);
        }
    }

    public static int cacheSize() {
        return (int) CACHE.size();
    }

    /**
     * Tira la cache y vuelve a empezar.
     *
     * <p>Solo hace falta al cambiar el idioma del arte: las imagenes ya
     * decodificadas son de otro idioma, y sin esto seguirian en pantalla hasta
     * reiniciar. Tambien se limpia {@code FAILED}, porque una carta que no
     * existia en un idioma puede existir en el otro.
     */
    public static void clear() {
        CACHE.invalidateAll();
        FAILED.clear();
        NO_IMAGE.clear();
        LOCALIZED_ASKED.clear();
        TRIES.clear();
    }

    /**
     * Busca la imagen, y si hace falta la pide.
     *
     * <p><b>El orden importa y no es obvio.</b> Jugando en castellano, la carta
     * castellana manda; pero si todavia no esta bajada, lo que NO se puede
     * hacer es dejar la mesa en blanco mientras Scryfall contesta — y menos
     * teniendo la inglesa ahi al lado. Asi que:
     *
     * <ol>
     *   <li>Si esta la traducida, esa.</li>
     *   <li>Si no, se pide — y <b>mientras tanto se ensenya la inglesa</b> si
     *       la hay. Cuando llegue la traducida, {@code imageReady} repinta y la
     *       carta se cambia sola.</li>
     *   <li>Si no hay ninguna de las dos, el camino de siempre.</li>
     * </ol>
     */
    private static void load(final String imageKey) {
        // Mirar el disco es I/O: fuera del hilo de interfaz.
        final Runnable job = () -> {
            try {
                final File localized = findLocalized(imageKey);
                if (localized != null) {
                    decode(imageKey, localized);
                    return;
                }

                final boolean askLocalized = CardArt.isLocalized()
                        && LOCALIZED_ASKED.add(imageKey);

                final File file = findOnDisk(imageKey);
                if (file != null && file.exists()) {
                    decode(imageKey, file);
                    if (askLocalized) {
                        requestLocalized(imageKey);
                    }
                    return;
                }
                if (cannotBeDownloaded(imageKey)) {
                    PENDING.remove(imageKey);
                    NO_IMAGE.add(imageKey);
                    return;
                }
                if (askLocalized) {
                    // Sin nada que ensenyar: se intenta la traducida y, la
                    // haya o no, se sigue con el camino de siempre.
                    requestLocalized(imageKey);
                } else {
                    requestDownload(imageKey);
                }
            } catch (final Exception e) {
                PENDING.remove(imageKey);
                giveUpForNow(imageKey);
            }
        };
        LOADERS.submit(job);
    }

    /**
     * Los hilos que buscan y piden imagenes. <b>Pocos, y a proposito.</b>
     *
     * <p>Antes se lanzaba un hilo suelto por imagen. Con una carta en pantalla
     * da igual; al importar un mazo de 97 son 97 hilos que se ponen a la vez a
     * dar vueltas por el disco — y buscar una imagen que <i>no esta</i> no es
     * barato: {@code ImageKeys.getImageFile} prueba una docena de variantes de
     * nombre y encima puede encolar una busqueda por edicion en
     * {@code ThreadUtil.getServicePool()}, que es <b>el mismo pool que hace las
     * descargas</b>. O sea que buscar acaba compitiendo con descargar.
     *
     * <p>Y no se pierde nada teniendolos contados: Scryfall se sirve a una
     * peticion cada 100 ms pase lo que pase ({@code ImageFetcher.paceScryfall}),
     * asi que mas hilos no traen ni una imagen antes.
     */
    private static final java.util.concurrent.ExecutorService LOADERS =
            java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
                final Thread t = new Thread(r, "neo-img-load");
                t.setDaemon(true);
                return t;
            });

    /**
     * Pide la descarga a Forge.
     *
     * <p>OJO: {@code ImageFetcher.fetchImage} hace
     * {@code FThreads.assertExecutedByEdt(true)} — SOLO se puede llamar desde el
     * hilo de interfaz, o lanza {@code IllegalStateException}. La descarga en si
     * la hace el fetcher en su propio pool, asi que llamarlo desde el hilo de UI
     * no bloquea nada.
     */
    /**
     * Pide el arte en el idioma del juego.
     *
     * <p>El {@code whenDone} se llama pase lo que pase, y es donde se decide:
     * si ha llegado la traducida, se cambia por ella; si no y no habia nada que
     * ensenyar, se sigue con la descarga de siempre.
     */
    private static void requestLocalized(final String imageKey) {
        final forge.util.ImageFetcher fetcher = GuiBase.getInterface().getImageFetcher();
        if (!(fetcher instanceof forge.neo.platform.NeoImageFetcher neo)) {
            requestDownload(imageKey);
            return;
        }
        neo.fetchLocalized(imageKey, () -> {
            final File f = findLocalized(imageKey);
            if (f != null) {
                decode(imageKey, f);
                return;
            }
            if (CACHE.getIfPresent(imageKey) == null) {
                // No habia nada puesto: toca el camino normal.
                requestDownload(imageKey);
            }
        });
    }

    private static void requestDownload(final String imageKey) {
        GuiBase.getInterface().invokeInEdtLater(() -> {
            try {
                GuiBase.getInterface().getImageFetcher().fetchImage(imageKey, () -> resolveAfterDownload(imageKey));
            } catch (final Exception e) {
                System.err.println("[neo] no se pudo pedir la imagen " + imageKey + ": " + e);
                PENDING.remove(imageKey);
                giveUpForNow(imageKey);
            }
        });
    }

    /**
     * Donde deja el fichero una descarga que acaba de terminar.
     *
     * <p>Se calcula igual que {@code ImageFetcher.fetchImage}: la carpeta de
     * cartas de Forge mas {@code getCardImageKey()}. Y la variante
     * {@code .fullborder}, porque Scryfall sirve la carta con borde completo y
     * {@code doFetch} renombra el destino al guardarla.
     */
    private static File downloadedFile(final String imageKey) {
        final String name = toFilename(imageKey);
        if (name == null || name.isEmpty()) {
            return null;
        }
        final File dir = new File(forge.localinstance.properties.ForgeConstants.CACHE_CARD_PICS_DIR);
        for (final String candidate : new String[] {
                name.replace(".full", ".fullborder") + ".jpg",
                name + ".jpg",
                // Y el recorte de arte, que es otra preferencia del jugador
                // (UI_CARD_ART_FORMAT) y deja otro nombre de fichero.
                name.replace(".full", ".artcrop") + ".jpg"}) {
            final File f = new File(dir, candidate);
            if (f.exists()) {
                return f;
            }
        }
        return null;
    }

    /**
     * Coge la imagen recien descargada.
     *
     * <p><b>Se mira el fichero directamente, y no es un atajo: era el
     * problema.</b> El aviso solo llega cuando la descarga ha ido <i>bien</i>,
     * asi que el fichero esta puesto — lo que pasa es que {@code ImageKeys} no
     * lo sabe: lleva dos caches negativas ({@code missingCards} y
     * {@code toFind}) que siguen diciendo "no esta". La version anterior lo
     * resolvia llamando a {@code clearMissingCards()} en un bucle de hasta 8
     * intentos con 350 ms de espera. Con una carta, invisible; con un mazo
     * entero, 97 hilos vaciando <b>a la vez</b> una cache global que es de
     * todos — asi que cada busqueda de cada carta volvia a hacer el recorrido
     * completo por el disco, y encima cada fallo encola una busqueda por
     * edicion en el pool que hace las descargas. Las imagenes tardaban una
     * eternidad justamente por lo que se hizo para que aparecieran antes.
     *
     * <p>Si aun asi no aparece — otra fuente que no sea Scryfall, un nombre
     * raro — se cae al camino de siempre, una vez y sin bucle.
     */
    /** El camino de siempre: vaciar la cache negativa y preguntar a Forge. */
    private static boolean lookUpOnDisk(final String imageKey) {
        ImageKeys.clearMissingCards();
        final File f = findOnDisk(imageKey);
        if (f != null && f.exists()) {
            decode(imageKey, f);
            return true;
        }
        return false;
    }

    private static void resolveAfterDownload(final String imageKey) {
        LOADERS.submit(() -> {
            final File direct = downloadedFile(imageKey);
            if (direct != null) {
                decode(imageKey, direct);
                return;
            }
            if (lookUpOnDisk(imageKey)) {
                return;
            }
            // Un solo reintento, y ESPERANDO EN EL RELOJ, no en un hilo de
            // carga: la unica cache que no se puede vaciar desde fuera es
            // `toFind`, que se limpia sola en cuanto termina la busqueda por
            // edicion que la puso. Dormir aqui ataria uno de los cuatro hilos
            // que estan sacando las demas imagenes.
            TIMER.schedule(() -> LOADERS.submit(() -> {
                if (!lookUpOnDisk(imageKey)) {
                    PENDING.remove(imageKey);
                    giveUpForNow(imageKey);
                }
            }), 300, java.util.concurrent.TimeUnit.MILLISECONDS);
        });
    }

    private static void decode(final String imageKey, final File file) {
        try {
            final Image img = new Image(file.toURI().toString(), false);
            if (img.isError()) {
                giveUpForNow(imageKey);
            } else {
                CACHE.put(imageKey, img);
                noteCeiling();
                notifyReady(imageKey);
            }
        } catch (final Exception e) {
            giveUpForNow(imageKey);
        } finally {
            PENDING.remove(imageKey);
        }
    }

    /**
     * Lo que se espera antes de repintar, para juntar las imagenes que llegan
     * a la vez.
     *
     * <p>Scryfall se sirve a un ritmo de una peticion cada 100 ms, asi que en
     * una tanda grande llega una imagen cada poco: con este hueco caben varias
     * en cada barrido y la pantalla sigue pareciendo instantanea (150 ms es el
     * suelo de las animaciones de la seccion 6, o sea lo que ya se considera
     * "en el acto").
     */
    private static final long NOTIFY_GAP = 120;

    private static final java.util.concurrent.atomic.AtomicBoolean NOTIFY_PENDING =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** {@code -Dneo.images.debug=true}: cuantas imagenes lleva la tanda. */
    private static final boolean DEBUG = Boolean.getBoolean("neo.images.debug");
    private static final java.util.concurrent.atomic.AtomicInteger BATCH =
            new java.util.concurrent.atomic.AtomicInteger();

    private static void notifyReady(final String key) {
        BATCH.incrementAndGet();
        scheduleRepaint();
    }

    /** Pide UN repintado, junto con lo que llegue en los proximos 120 ms. */
    private static void scheduleRepaint() {
        if (LISTENERS.isEmpty()) {
            return;
        }
        // Solo se programa un aviso: las imagenes que lleguen mientras tanto
        // entran en el mismo barrido gratis.
        if (!NOTIFY_PENDING.compareAndSet(false, true)) {
            return;
        }
        TIMER.schedule(() -> {
            NOTIFY_PENDING.set(false);
            if (DEBUG) {
                System.out.println("[neo-img] repintando: " + BATCH.getAndSet(0)
                        + " imagen(es) en esta tanda");
            } else {
                BATCH.set(0);
            }
            final Runnable fire = () -> {
                for (final Listener l : LISTENERS) {
                    try {
                        l.imagesReady();
                    } catch (final Exception ignored) {
                        // un listener roto no debe tumbar la carga
                    }
                }
            };
            if (Platform.isFxApplicationThread()) {
                fire.run();
            } else {
                try {
                    Platform.runLater(fire);
                } catch (final IllegalStateException ignored) {
                    // toolkit no arrancado (modo consola)
                }
            }
        }, NOTIFY_GAP, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static final java.util.concurrent.ScheduledExecutorService TIMER =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread t = new Thread(r, "neo-img-notify");
                t.setDaemon(true);
                return t;
            });
}
