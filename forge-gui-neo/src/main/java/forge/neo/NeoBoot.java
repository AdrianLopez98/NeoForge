package forge.neo;

import java.util.Locale;

import forge.gui.interfaces.IProgressBar;
import forge.model.FModel;

/**
 * Cargar el motor, y que mientras tanto se vea algo.
 *
 * <p><b>El problema que resuelve.</b> Leer las 33.666 cartas y las 680
 * ediciones tarda unos 11 segundos con todo en cache — y bastante mas la
 * primera vez, con el antivirus estrenando la carpeta. Eso pasaba <b>antes de
 * que existiera una sola ventana</b>: {@code NeoMain} llamaba a
 * {@code FModel.initialize} y solo despues a {@code Application.launch}. Desde
 * fuera es indistinguible de un doble clic que no ha funcionado, y lo que hace
 * el jugador entonces es volver a clicar (ver {@link NeoLock}).
 *
 * <p><b>Lo que se hace ahora.</b> La ventana se abre <b>primero</b>, con la
 * pantalla de carga, y el motor se lee en un hilo de fondo con su barra de
 * progreso. Es lo mismo que hace la GUI vieja desde siempre
 * ({@code Singletons} le pasa la barra de su splash a {@code FModel}), o sea
 * que cargar el modelo fuera del hilo de interfaz es el camino previsto y no
 * un truco.
 *
 * <p><b>Y el texto sale gratis y traducido.</b> El motor va nombrando por su
 * cuenta lo que esta haciendo — {@code splash.loading.examining-cards},
 * {@code splash.loading.cards-archive}, {@code splash.loading.decks} — y esas
 * claves estan en los diez idiomas de {@code res/languages}. La pantalla de
 * carga no inventa ni una frase: repite lo que el motor dice que esta
 * haciendo.
 *
 * <p>⚠️ El progreso llega ya saltado al hilo de interfaz: {@code FModel} envuelve
 * cada aviso en {@code FThreads.invokeInEdtLater}. O sea que quien implemente
 * {@link IProgressBar} <b>no debe</b> volver a saltar de hilo, pero si tiene
 * que aguantar que le llamen desde cualquiera de los dos.
 */
public final class NeoBoot {

    private NeoBoot() {
    }

    private static volatile boolean loaded;

    /** Si el motor ya esta leido. */
    public static boolean isLoaded() {
        return loaded;
    }

    /**
     * Cuantos milisegundos han pasado desde el doble clic.
     *
     * <p>Se pregunta al sistema operativo cuando arranco ESTE proceso, no
     * cuando se cargo esta clase: lo que se quiere medir es justamente lo que
     * pasa antes de que corra codigo nuestro — levantar la maquina virtual,
     * abrir ochenta y cinco jar, el antivirus mirandolos —, que es la parte
     * que no se puede tapar desde Java y de la que si no, no habria ni un
     * numero.
     *
     * <p>Sirve para dos cosas: que el registro diga cuanto tardo en verse la
     * ventana el dia que alguien se queje, y poder comprobar desde fuera que
     * un cambio de arranque ha servido para algo.
     *
     * <p>Si el sistema no lo dice, se mide desde que se cargo esta clase: sale
     * un numero mas pequenyo que el real, pero nunca uno inventado.
     */
    public static long sinceStart() {
        try {
            final java.util.Optional<java.time.Instant> inicio =
                    ProcessHandle.current().info().startInstant();
            if (inicio.isPresent()) {
                return java.time.Duration.between(inicio.get(), java.time.Instant.now())
                        .toMillis();
            }
        } catch (final RuntimeException e) {
            // Da igual: se cae al reloj de abajo.
        }
        return System.currentTimeMillis() - CARGADA;
    }

    /** Cuando se cargo esta clase, como respaldo de {@link #sinceStart()}. */
    private static final long CARGADA = System.currentTimeMillis();

    /**
     * Lee la base de cartas. Bloquea hasta terminar.
     *
     * <p>Se llama desde un hilo de fondo cuando hay ventana, y desde el hilo
     * principal cuando no la hay (los comprobadores, {@code play},
     * {@code watch}). La barra puede ser {@code null}: entonces no se informa
     * de nada, que es lo correcto sin pantalla.
     *
     * @param bar donde contar el avance, o {@code null}
     */
    public static void loadEngine(final IProgressBar bar) {
        if (loaded) {
            return;
        }
        System.out.println("[2/2] Cargando base de cartas...");
        final long t0 = System.currentTimeMillis();
        // El idioma se aplica AQUI y no despues: el motor precarga los nombres
        // de carta traducidos antes de leer las cartas. Ver NeoLanguage.
        System.out.println("        idioma: " + NeoLanguage.currentLabel());
        FModel.initialize(bar, NeoLanguage.hook());
        NeoLanguage.restoreEngineValue();
        loaded = true;
        System.out.printf(Locale.ROOT, "        %,d cartas . %,d ediciones . %ds%n",
                FModel.getMagicDb().getCommonCards().getAllCards().size(),
                FModel.getMagicDb().getEditions().size(),
                (System.currentTimeMillis() - t0) / 1000);
    }

    /**
     * Deja los textos del motor listos ANTES de que haya ventana.
     *
     * <p><b>Sin esto la ventana no llega a abrirse.</b> Al mover la carga del
     * motor detras de {@code Application.launch}, lo primero que se construye
     * es {@code NeoApp} — y uno de sus campos vale {@code NeoFormat.COMMANDER},
     * que arrastra el inicializador estatico de {@code forge.game.GameType}.
     * Ese pide el nombre traducido de cada modo de juego al {@code Localizer},
     * que hasta ahora arrancaba dentro de {@code FModel.initialize}. Resultado:
     * {@code ExceptionInInitializerError} antes de pintar un solo pixel.
     *
     * <p>Podria arreglarse haciendo perezoso ese campo, pero seria tapar UN
     * caso: cualquier clase del motor que se toque antes de tiempo vuelve a
     * caer en lo mismo, y siempre con un error que no se parece en nada a su
     * causa. Arrancar el {@code Localizer} aqui lo cierra entero.
     *
     * <p>Sale barato — leer un {@code .properties} de {@code res/languages} —
     * y no se pisa con nadie: {@code FModel.initialize} lo vuelve a llamar
     * despues con el mismo idioma, porque el que se usa aqui es exactamente el
     * que aplica {@link NeoLanguage#hook()}.
     */
    public static void prepareTexts() {
        final String idioma = NeoLanguage.current();
        forge.util.Lang.createInstance(idioma);
        forge.util.Localizer.getInstance().initialize(idioma,
                forge.localinstance.properties.ForgeConstants.LANG_DIR);
    }

}
