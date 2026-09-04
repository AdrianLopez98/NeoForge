package forge.neo.platform;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.jupnp.UpnpServiceConfiguration;

import forge.gamemodes.match.HostedMatch;
import forge.gui.download.GuiDownloadService;
import forge.gui.interfaces.IGuiBase;
import forge.gui.interfaces.IGuiGame;
import forge.item.PaperCard;
import forge.localinstance.skin.FSkinProp;
import forge.localinstance.skin.ISkinImage;
import forge.sound.IAudioClip;
import forge.sound.IAudioMusic;
import forge.util.FSerializableFunction;
import forge.util.ImageFetcher;

/**
 * Implementacion de {@link IGuiBase} para la interfaz Neo.
 *
 * <p>Esta clase es el "adaptador de plataforma": todo lo que el motor necesita
 * del entorno (hilo de UI, rutas, imagenes, audio, dialogos) pasa por aqui.
 *
 * <p><b>Aqui no se revienta.</b> El disenyo original era "fallar ruidoso": lo
 * que no estuviera hecho lanzaba {@link UnsupportedOperationException} para que
 * el stack trace dijera que pieza faltaba. Era buena idea mientras la
 * aplicacion no jugaba, y ha dejado de serlo: casi todos estos metodos los
 * llama el motor desde <b>hilos que nadie mira</b> — el que reparte las
 * recompensas al ganar, el que actualiza los logros al acabar la partida. Ahi
 * una excepcion no sale por ningun sitio: se lleva el hilo por delante, el
 * aviso que venia detras no llega, y lo que ve el jugador es un cuelgue. Con
 * 33.700 cartas, que una toque uno de esos caminos era cuestion de tiempo.
 *
 * <p>Asi que cada metodo hace lo inocuo que corresponda: ensenyar el dialogo si
 * hay ventana, y si no, contestar lo que el motor entienda como "por defecto" o
 * "he cancelado", dejando una linea por consola. La unica excepcion que queda
 * es {@code getNewGuiGame} sin fabrica, que no es un camino de carta sino un
 * error de montaje nuestro.
 *
 * <p>IMPORTANTE: el hilo de UI es el de JavaFX en cuanto hay ventana
 * ({@link FxUiDispatcher}). Ver las notas de diseño seccion 5.
 */
public class NeoGuiBase implements IGuiBase {

    /**
     * Directorio de assets: la carpeta que contiene {@code res/}.
     *
     * <p>Se puede fijar con {@code -Dforge.assetsDir=...}. Por defecto apunta a
     * {@code ../forge-gui/}, que es lo correcto cuando el directorio de trabajo
     * es este modulo (igual que hace GuiDesktop en modo desarrollo).
     */
    private static final String ASSETS_DIR =
            System.getProperty("forge.assetsDir", "../forge-gui/");

    /**
     * Fabrica de interfaces de partida.
     *
     * <p>El motor la usa cuando necesita crear una GUI por su cuenta — sobre
     * todo en {@code HostedMatch.startGame()}, que registra un espectador
     * automaticamente si no hay ningun jugador humano.
     */
    private static java.util.function.Supplier<IGuiGame> guiGameFactory;

    public static void setGuiGameFactory(final java.util.function.Supplier<IGuiGame> factory) {
        guiGameFactory = factory;
    }

    /** Politica de hilos. Fase 1: hilo dedicado. Fase 2: hilo de JavaFX. */
    private final UiDispatcher dispatcher;

    public NeoGuiBase() {
        this(new ConsoleUiDispatcher());
    }

    public NeoGuiBase(final UiDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public UiDispatcher getDispatcher() {
        return dispatcher;
    }

    // ---------------------------------------------------------------
    // Lo que SI necesita la fase 0
    // ---------------------------------------------------------------

    @Override
    public String getAssetsDir() {
        return ASSETS_DIR;
    }

    @Override
    public boolean isRunningOnDesktop() {
        return true;
    }

    @Override
    public boolean isLibgdxPort() {
        return false;
    }

    @Override
    public String getCurrentVersion() {
        // "git" en la cadena hace que Forge se considere en modo desarrollo.
        return "neo-dev-git";
    }

    // --- Hilos ------------------------------------------------------
    // EL PUNTO CRITICO DEL PROYECTO (ver las notas de diseño seccion 5).
    //
    // El motor comprueba activamente en que hilo esta con
    // FThreads.assertExecutedByEdt(). Si isGuiThread() miente, se niega a
    // bloquearse esperando al jugador y la partida ni siquiera arranca.
    //
    // Toda la politica de hilos vive en UiDispatcher: en la fase 2 se cambia
    // ConsoleUiDispatcher por uno de JavaFX y aqui no se toca nada.

    @Override
    public void invokeInEdtNow(final Runnable runnable) {
        dispatcher.runAndWait(runnable);
    }

    @Override
    public void invokeInEdtLater(final Runnable runnable) {
        dispatcher.runLater(runnable);
    }

    @Override
    public void invokeInEdtAndWait(final Runnable proc) {
        dispatcher.runAndWait(proc);
    }

    @Override
    public boolean isGuiThread() {
        return dispatcher.isUiThread();
    }

    @Override
    public void runBackgroundTask(final String message, final Runnable task) {
        // El nombre empieza por "Game" por convencion de Forge: marca el hilo
        // como "no es el de UI".
        final Thread t = new Thread(task, "Game-neo-bg");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public float getScreenScale() {
        return 1f;
    }

    @Override
    public boolean hasNetGame() {
        return false;
    }

    @Override
    public HostedMatch hostMatch() {
        final HostedMatch match = new HostedMatch();
        // Un fallo de la IA mata el bucle de la partida y deja a todo el mundo
        // encerrado en una mesa muerta. Se blinda AQUI porque este es el unico
        // sitio por el que pasan todas las partidas: el match lo pide el motor
        // a IGuiBase, la monte quien la monte (nosotros, el lobby de red o la
        // aventura). Ver SafeAi.
        match.setStartGameHook(forge.neo.match.SafeAi.hook(match, null));
        // Gancho para la aventura: ahi el match NO lo montamos nosotros, lo
        // monta QuestUtil, y hace falta quedarse con el para saber cuando
        // termina. Sin esto habria que reimplementar cuarenta lineas de reglas
        // del motor solo para tener la referencia.
        final java.util.function.UnaryOperator<HostedMatch> hook = onMatchCreated;
        return hook == null ? match : hook.apply(match);
    }

    private static volatile java.util.function.UnaryOperator<HostedMatch> onMatchCreated;

    /** Se llama con cada match que cree el motor. null para desengancharlo. */
    public static void setOnMatchCreated(
            final java.util.function.UnaryOperator<HostedMatch> hook) {
        onMatchCreated = hook;
    }

    // ---------------------------------------------------------------
    // El resto de la plataforma
    // ---------------------------------------------------------------

    /**
     * Lo unico que sigue reventando, y a proposito.
     *
     * <p>Se reserva para lo que no es un camino del motor sino un fallo de
     * montaje nuestro: pedir una interfaz de partida sin haber registrado la
     * fabrica. Eso pasa al arrancar, en el hilo que arranca, y ahi el ruido si
     * llega a alguien.
     */
    private static UnsupportedOperationException todo(final String what) {
        return new UnsupportedOperationException(
                "NeoGuiBase: '" + what + "' aun no implementado. "
                        + "Ver las notas de diseño para saber en que fase toca.");
    }

    /** Descarga de imagenes de carta (Scryfall), compartida por toda la app. */
    private final ImageFetcher imageFetcher = new NeoImageFetcher();

    @Override
    public ImageFetcher getImageFetcher() {
        return imageFetcher;
    }
    // ---- iconos de "piel" (skin) ----
    //
    // Estos cuatro los llama el motor SOLO para dibujar pantallas que nosotros
    // no tenemos (los trofeos de los logros, sobre todo). Devuelven un icono
    // vacio en vez de reventar, y la razon es importante:
    //
    // Al terminar una partida de la aventura, Forge actualiza los logros
    // (PlayerControllerHuman.updateAchievements) DESDE EL HILO DE INTERFAZ. Si
    // esto lanza, ese hilo se muere con la excepcion, el aviso de "match
    // terminado" no llega nunca y la partida se queda colgada para siempre.
    // O sea: fallar con ruido aqui es peor que no hacer nada, porque el ruido
    // se lo lleva un hilo que nadie mira y lo que ve el jugador es un cuelgue.
    //
    // Cuando exista pantalla de logros, esto se rellena de verdad.
    private static final ISkinImage NO_ICON = new ISkinImage() { };

    @Override public ISkinImage getSkinIcon(FSkinProp p) { return NO_ICON; }
    @Override public ISkinImage getUnskinnedIcon(String path) { return NO_ICON; }
    @Override public ISkinImage getCardArt(PaperCard c, boolean backFace) { return NO_ICON; }
    @Override public ISkinImage createLayeredImage(PaperCard c, FSkinProp bg, String overlay, float opacity) { return NO_ICON; }
    @Override public void clearImageCache() { /* nada que limpiar todavia */ }

    @Override
    public String encodeSymbols(final String str, final boolean formatReminderText) {
        // Sin formato de simbolos por ahora: devolvemos el texto tal cual.
        return str;
    }

    // El motor los usa para repartir cara y funda a cada IA. Devolviendo 0
    // — que es lo que habia — les tocaba a todas la misma.
    @Override public int getAvatarCount() { return forge.neo.look.NeoLook.builtInAvatarCount(); }
    @Override public int getSleevesCount() { return forge.neo.look.NeoLook.builtInSleeveCount(); }
    @Override public void preventSystemSleep(boolean prevent) { /* no-op */ }
    /**
     * Las descargas del motor: mazos de internet, imagenes, actualizaciones.
     *
     * <p>Sin dialogo: {@link NeoDownloads} le da al servicio los widgets que
     * espera y pulsa su boton por nosotros. No bloquea — quien llama espera por
     * su cuenta, que es como lo hace el motor.
     */
    @Override
    public void download(final GuiDownloadService service, final Consumer<Boolean> callback) {
        NeoDownloads.run(service, callback);
    }

    /**
     * Al portapapeles.
     *
     * <p>Se usa el de JavaFX si hay ventana; si no, el de AWT, que funciona sin
     * escena. Y si tampoco — un servidor sin pantalla, un comprobador — se
     * queda en un aviso: copiar algo no es una operacion por la que merezca la
     * pena tirar una partida.
     */
    @Override
    public void copyToClipboard(final String text) {
        if (text == null) {
            return;
        }
        try {
            if (javafx.application.Platform.isFxApplicationThread()) {
                final javafx.scene.input.ClipboardContent content =
                        new javafx.scene.input.ClipboardContent();
                content.putString(text);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                return;
            }
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido copiar al portapapeles: " + e);
        }
    }

    /** Abrir una direccion en el navegador del sistema. */
    @Override
    public void browseToUrl(final String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            }
        } catch (final Exception e) {
            System.err.println("[neo] no se ha podido abrir " + url + ": " + e);
        }
    }

    // ---- ensenyar cartas, FUERA de una partida ----
    //
    // Los reparte la aventura: el contenido de un sobre, las cartas de un
    // torneo, las que desbloquea una expansion. Es un "mira lo que te llevas",
    // no una eleccion — y eso es exactamente un reveal, o sea getChoices con
    // min y max negativos. Asi sale por el MISMO dialogo que todo lo demas.

    @Override
    public void showCardList(final String title, final String msg, final List<PaperCard> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        getChoices(header(title, msg), -1, -1, list, null, null);
    }

    /**
     * Igual, pero para las cajas de sobres.
     *
     * <p>Devuelve si el jugador quiere saltarse el resto de sobres de la caja.
     * Nunca: verlos es justamente lo que se ha venido a hacer.
     */
    @Override
    public boolean showBoxedProduct(final String title, final String msg, final List<PaperCard> list) {
        showCardList(title, msg, list);
        return false;
    }

    /** Titulo y mensaje en una sola linea, que es lo que cabe en el dialogo. */
    private static String header(final String title, final String msg) {
        if (title == null || title.isBlank()) {
            return msg;
        }
        if (msg == null || msg.isBlank()) {
            return title;
        }
        return title + " — " + msg;
    }
    /**
     * El informe de error de Forge.
     *
     * <p>Lo llama el manejador global de excepciones, o sea que llega
     * <b>justo cuando algo ya ha fallado</b>. Si esto lanza a su vez, la
     * excepcion de verdad se pierde y en su lugar sale la nuestra, que no dice
     * nada. Se escribe por consola y punto.
     */
    @Override
    public void showBugReportDialog(final String title, final String text, final boolean exitBtn) {
        System.err.println("[neo] " + title);
        System.err.println(text);

        // ---- y si el que se ha caido es EL HILO DE LA PARTIDA ----
        //
        // Este metodo lo llama {@code BugReporter.reportException}, y lo llama
        // <b>desde el hilo que se esta muriendo</b>. O sea que aqui, y solo
        // aqui, se puede saber que la partida acaba de romperse.
        //
        // Importa porque es lo que separa un cuelgue de un aviso. Caso real: la
        // IA de Forge revento con un NPE en AiBlockController y se llevo el
        // hilo Game-0. Lo que vieron los dos jugadores fue una mesa que dejo de
        // responder — sin prioridad, sin OK y con "Salir" sin efecto — y ni una
        // palabra de que habia pasado. La traza estaba en neo.log, donde nadie
        // mira mientras juega.
        //
        // No se puede arreglar la partida: el hilo ES la partida (la maquina de
        // estados vive en su pila de llamadas). Lo unico que se puede hacer es
        // decirlo y dejar salir.
        if (Thread.currentThread().getName().startsWith("Game-")) {
            final java.util.function.Consumer<String> hook = engineCrash;
            if (hook != null && CRASH_TOLD.compareAndSet(false, true)) {
                try {
                    hook.accept(title);
                } catch (final RuntimeException e) {
                    // Estamos en el hilo de una partida que ya se ha caido:
                    // aqui no se puede empeorar nada, pero tampoco tirar mas.
                    System.err.println("[neo] no se ha podido avisar del fallo: " + e);
                }
            }
        }
    }

    /** Aviso de "el hilo de la partida se ha muerto". Lo pone la pantalla. */
    private static volatile java.util.function.Consumer<String> engineCrash;

    private static final java.util.concurrent.atomic.AtomicBoolean CRASH_TOLD =
            new java.util.concurrent.atomic.AtomicBoolean();

    public static void setOnEngineCrash(final java.util.function.Consumer<String> hook) {
        engineCrash = hook;
    }

    /** Rearma el aviso al empezar una partida nueva. */
    public static void clearEngineCrash() {
        CRASH_TOLD.set(false);
    }
    /**
     * "Enhorabuena, has conseguido un trofeo".
     *
     * <p>Lo llama el motor al actualizar los logros cuando ganas una partida de
     * la aventura, y lo hace DESDE EL HILO DE INTERFAZ. No puede lanzar: ver la
     * nota de los iconos mas arriba. Se guarda el aviso y quien pinte la
     * pantalla de recompensas lo recoge con {@link #drainNotices()}.
     */
    @Override
    public void showImageDialog(final ISkinImage img, final String msg, final String title) {
        final String line = (title == null || title.isBlank() ? "" : title + ": ")
                + (msg == null ? "" : msg);
        if (!line.isBlank()) {
            synchronized (NOTICES) {
                NOTICES.add(line);
            }
            System.out.println("  [logro] " + line);
        }
    }

    private static final java.util.List<String> NOTICES = new java.util.ArrayList<>();

    /** Se lleva los avisos pendientes y los borra. */
    public static java.util.List<String> drainNotices() {
        synchronized (NOTICES) {
            final java.util.List<String> out = new java.util.ArrayList<>(NOTICES);
            NOTICES.clear();
            return out;
        }
    }
    /**
     * "Elige una de estas respuestas", fuera de una partida.
     *
     * <p>Por debajo es el mismo dialogo de eleccion que todo lo demas: las
     * opciones son cadenas y lo que se devuelve es <b>en que posicion</b> esta
     * la elegida, que es lo que espera {@code SOptionPane}. De aqui salen
     * ademas todos sus {@code showConfirmDialog} y {@code showMessageDialog},
     * que no son mas que esto con dos botones o con uno.
     *
     * <p>Sin ventana se devuelve la opcion por defecto, que en Forge siempre es
     * la conservadora.
     */
    @Override
    public int showOptionDialog(final String message, final String title, final FSkinProp icon,
                                final List<String> options, final int defaultOption) {
        if (options == null || options.isEmpty()) {
            return defaultOption;
        }
        final int def = defaultOption >= 0 && defaultOption < options.size() ? defaultOption : 0;
        final List<String> picked = getChoices(header(title, message), 1, 1, options,
                List.of(options.get(def)), null);
        if (picked.isEmpty()) {
            return def;
        }
        final int index = options.indexOf(picked.get(0));
        return index < 0 ? def : index;
    }

    /**
     * "Escribe algo": el nombre de un mazo, una direccion, un numero.
     *
     * <p>Sin ventana se devuelve <b>null</b>, que para el motor es "cancelar",
     * y eso importa: {@code SGuiChoose.getInteger} repite la pregunta en un
     * bucle hasta que le den un numero valido o un null. Devolver el valor
     * inicial ahi seria un cuelgue con la CPU al maximo.
     */
    @Override
    public String showInputDialog(final String message, final String title, final FSkinProp icon,
                                  final String initialInput, final List<String> inputOptions,
                                  final boolean isNumeric) {
        // Con lista de opciones no hay nada que escribir: es una eleccion.
        if (inputOptions != null && !inputOptions.isEmpty()) {
            final int i = showOptionDialog(message, title, icon, inputOptions, 0);
            return inputOptions.get(Math.max(0, Math.min(i, inputOptions.size() - 1)));
        }
        final Asker a = asker;
        if (a != null) {
            return a.ask(header(title, message), initialInput, isNumeric);
        }
        System.out.println("[neo] no hay quien conteste a: " + header(title, message));
        return null;
    }

    /** Quien sabe pedir un texto. Lo pone la app cuando hay ventana. */
    public interface Asker {
        String ask(String message, String initial, boolean numeric);
    }

    private static volatile Asker asker;

    public static void setAsker(final Asker a) {
        asker = a;
    }

    /**
     * Elegir un fichero del disco.
     *
     * <p>Solo lo pide el modo desarrollador de Forge, para cargar una posicion
     * de partida guardada. null es "he cancelado", y el motor lo trata bien.
     */
    @Override
    public String showFileDialog(final String title, final String defaultDir) {
        System.out.println("[neo] no hay selector de ficheros: " + title);
        return null;
    }

    /**
     * Donde guardar un fichero.
     *
     * <p>Se devuelve el que propone el motor en vez de null <b>a proposito</b>:
     * quien mas lo llama es el informe de error, que escribe en lo que le
     * devuelvas sin comprobar nada. Un null ahi cambia un fallo por otro.
     */
    @Override
    public File getSaveFile(final File defaultFile) {
        return defaultFile;
    }

    /**
     * Ordenar una lista, fuera de una partida.
     *
     * <p>No tiene ni un solo sitio que lo llame en el motor — el {@code order}
     * que usan las cartas es el de {@code IGuiGame}, que resuelve
     * {@code NeoMatchUI}. Se deja el orden que venga dado, que es la respuesta
     * inocua, y se anota por si algun dia aparece uno.
     */
    @Override
    public <T> List<T> order(final String title, final String top, final int min, final int max,
                             final List<T> sourceChoices, final List<T> destChoices) {
        final List<T> out = new java.util.ArrayList<>();
        if (destChoices != null) {
            out.addAll(destChoices);
        }
        if (sourceChoices != null) {
            out.addAll(sourceChoices);
        }
        System.out.println("[neo] orden por defecto para: " + title);
        return out;
    }
    // ---- elegir algo, FUERA de una partida ----
    //
    // Ojo: esto NO es el getChoices de IGuiGame (ese lo resuelve NeoMatchUI).
    // Este entra por SGuiChoose y lo usan los modos: la aventura pregunta de
    // que expansion quieres el sobre de premio al ganar un duelo, por ejemplo.
    //
    // Estaba sin implementar, y el sintoma era feo: la excepcion saltaba en el
    // hilo de las recompensas — que nadie mira — y lo que veia el jugador era
    // que su sobre de premio no llegaba nunca. Se aplica aqui la misma regla
    // que en los iconos: en un camino que corre lejos del jugador, reventar es
    // peor que resolver.

    /** Quien sabe ensenyar la pregunta. Lo pone la app cuando hay ventana. */
    public interface Chooser {
        <T> List<T> choose(String message, int min, int max,
                           List<T> options, List<T> preselected,
                           java.util.function.Function<T, String> display);
    }

    private static volatile Chooser chooser;

    public static void setChooser(final Chooser c) {
        chooser = c;
    }

    /**
     * El elegidor que hay puesto ahora, para guardarlo y devolverlo.
     *
     * <p>Lo usa el draft de cubo (la auditoría del motor, apartado C2): el motor sólo sabe
     * preguntar "qué cubo" con un {@code SGuiChoose} de toda la vida, y la
     * respuesta ya se eligió en nuestra propia pantalla. Se instala un
     * elegidor de usar-y-tirar que contesta esa única pregunta sin
     * enseñar nada, y este getter es lo que permite devolver el de
     * siempre después — sin él, cualquier otra pregunta de fuera de
     * partida que llegara más tarde (la aventura, por ejemplo) se
     * quedaría contestándose sola para siempre.
     */
    public static Chooser getChooser() {
        return chooser;
    }

    @Override
    public <T> List<T> getChoices(final String msg, final int min, final int max,
                                  final Collection<T> choices, final Collection<T> selected,
                                  final FSerializableFunction<T, String> display) {
        final List<T> options = choices == null ? List.of() : new java.util.ArrayList<>(choices);
        final List<T> pre = selected == null ? List.<T>of() : new java.util.ArrayList<>(selected);
        if (options.isEmpty()) {
            return List.of();
        }
        final Chooser c = chooser;
        if (c != null) {
            final List<T> answer = c.choose(inSpanish(msg), min, max, options, pre,
                    display == null ? null : display::apply);
            if (answer != null && (min <= 0 || !answer.isEmpty())) {
                return answer;
            }
        }
        // Sin ventana (los comprobadores) o si el dialogo no ha podido salir:
        // lo preseleccionado, y si no lo hay, lo primero. Nunca una lista
        // vacia cuando el motor espera al menos una: eso revienta mas arriba.
        if (min <= 0) {
            return List.of();
        }
        final List<T> fallback = new java.util.ArrayList<>(
                pre.isEmpty() ? List.of(options.get(0)) : pre);
        System.out.println("[neo] elijo por ti: " + msg + " -> " + fallback.get(0));
        return fallback.subList(0, Math.min(fallback.size(), Math.max(1, max)));
    }

    /**
     * Las preguntas del motor que llegan aqui, en castellano y con contexto.
     *
     * <p>Son cuatro contadas y todas de la aventura. El texto de Forge dice
     * <i>que</i> hay que elegir pero no <i>por que</i> te lo estan preguntando:
     * "Choose bonus booster format" en mitad de la pantalla, despues de ganar,
     * no se entiende. Que es un PREMIO es la mitad del mensaje.
     *
     * <p>No es un sistema de traduccion: es una tabla de cuatro entradas. Lo
     * que no este, pasa tal cual.
     */
    private static String inSpanish(final String msg) {
        if (msg == null) {
            return null;
        }
        switch (msg.trim()) {
            case "Choose bonus booster format":
                return forge.neo.NeoText.get("prize.format");
            case "Choose bonus booster set":
                return forge.neo.NeoText.get("prize.set");
            case "Which booster type do you choose?":
                return forge.neo.NeoText.get("prize.type");
            default:
                return msg;
        }
    }

    @Override
    public PaperCard chooseCard(final String title, final String msg, final List<PaperCard> list) {
        final List<PaperCard> picked = getChoices(
                msg == null || msg.isBlank() ? title : msg, 1, 1, list, null, null);
        return picked.isEmpty() ? null : picked.get(0);
    }

    // --- sonido ---
    //
    // No hay nada mas que hacer para tener sonido: HostedMatch ya suscribe el
    // SoundSystem de Forge a los eventos de la partida y ya elige la lista de
    // musica. Lo unico que faltaba era el altavoz, y es esto.

    /** Traza cada sonido que se carga. Se enciende con -Dneo.audio.debug=true */
    private static final boolean AUDIO_DEBUG = Boolean.getBoolean("neo.audio.debug");

    /** Formatos que sabe reproducir JavaFX. Los sonidos de Forge son .mp3. */
    @Override
    public boolean isSupportedAudioFormat(final File f) {
        if (f == null) {
            return false;
        }
        final String name = f.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".aiff")
                || name.endsWith(".aif") || name.endsWith(".m4a");
    }

    @Override
    public IAudioClip createAudioClip(final String filename) {
        // El nombre viene suelto ("tap.mp3"); quien sabe en que carpeta esta es
        // el propio SoundSystem, que mira perfiles de sonido y carpetas de
        // usuario antes que la de serie.
        final File file = forge.sound.SoundSystem.instance.getSoundResource(filename);
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            if (AUDIO_DEBUG) {
                System.out.println("[audio] " + filename + " -> " + file.getAbsolutePath());
            }
            return new NeoAudioClip(file);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido cargar el sonido " + filename);
            return null;
        }
    }

    @Override
    public IAudioMusic createAudioMusic(final String filename) {
        // Aqui si llega la ruta completa: la resuelve MusicPlaylist.
        return new NeoAudioMusic(filename);
    }
    // El "sistema de sonido alternativo" es una via de Forge para lanzar un
    // reproductor externo. No hace falta: createAudioClip ya suena.
    @Override public void startAltSoundSystem(String filename, boolean sync) { }

    // La tienda y el bazar de la aventura, pedidos POR EL MOTOR.
    //
    // Nosotros no entramos por aqui: nuestra tienda la abre QuestScreen y habla
    // con NeoQuestShop directamente. Esto es el camino de la GUI vieja, que se
    // deja apuntado en vez de reventar — el bazar, ademas, todavia no existe.
    @Override public void showSpellShop() { System.out.println("[neo] la tienda se abre desde la aventura"); }
    @Override public void showBazaar() { System.out.println("[neo] el bazar todavia no esta"); }

    @Override
    public IGuiGame getNewGuiGame() {
        if (guiGameFactory == null) {
            throw todo("getNewGuiGame (sin fabrica registrada: llama a NeoGuiBase.setGuiGameFactory)");
        }
        return guiGameFactory.get();
    }
    /**
     * UPnP: que el router abra el puerto solo al hospedar una partida.
     *
     * <p>Devolvia null, que es "esta plataforma no sabe de UPnP". En escritorio
     * si sabe — jupnp ya viene en el classpath por dependencia de forge-gui —
     * y es la diferencia entre que un amigo pueda entrar por internet o tengas
     * que abrir el 36743 a mano en el router.
     *
     * <p>Es opcional a proposito: en LAN no hace falta, el jugador puede decir
     * que no cuando se le pregunta, y {@code FServerManager.mapNatPort} captura
     * hasta los {@code LinkageError} — o sea que si esto fallara, se hospeda
     * igual y solo se pierde la apertura automatica.
     */
    @Override
    public UpnpServiceConfiguration getUpnpPlatformService() {
        return new org.jupnp.DefaultUpnpServiceConfiguration();
    }
}
