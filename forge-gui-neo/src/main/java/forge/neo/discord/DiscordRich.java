package forge.neo.discord;

import java.io.IOException;

/**
 * La presencia de Discord: "Jugando a Neo Forge", con lo que estas haciendo.
 *
 * <p>Pedido por un jugador en itch.io el 22-09-2026. Es <b>adorno</b>: no toca
 * el motor, no toca las reglas y no puede impedir jugar. Toda esta clase esta
 * escrita alrededor de esa frase.
 *
 * <h2>Las cuatro reglas que la sostienen</h2>
 *
 * <ol>
 *   <li><b>Un hilo propio, y demonio.</b> Hablar por la tuberia BLOQUEA (se
 *       escribe y se espera respuesta). Hacerlo desde el hilo de JavaFX seria
 *       el cuelgue clasico de las notas de diseño entrando por una puerta nueva.
 *       Demonio, ademas: un adorno no puede impedir que el juego se cierre.</li>
 *   <li><b>Nada sale de aqui.</b> Ni una excepcion, ni un error en pantalla. Si
 *       Discord no esta, esta ocupado o cambia de protocolo, la presencia
 *       simplemente no aparece y el juego no se entera. {@link #set} y
 *       {@link #update} se pueden llamar desde cualquier hilo y no bloquean
 *       jamas.</li>
 *   <li><b>Se juntan los cambios.</b> Discord acepta unas <b>5
 *       actualizaciones cada 20 segundos</b> y tira las demas sin avisar. En
 *       una partida los cambios llegan a rafagas (fase, turno, pantalla), asi
 *       que se guarda solo <b>el ultimo</b> y se manda cuando toca. Sin esto lo
 *       que se ve es un estado de hace dos minutos, que es peor que nada.</li>
 *   <li><b>Apagable, y apagar significa borrar.</b> Esto lo ven todos los
 *       amigos de quien juega. El interruptor esta en Ajustes, y al apagarlo no
 *       basta con callarse: hay que decirle a Discord que quite lo que ya
 *       habia puesto.</li>
 * </ol>
 *
 * <h2>Por que el hilo no se para nunca</h2>
 *
 * <p>La primera version creaba el hilo al encender y lo mataba al apagar,
 * esperandolo con un {@code join}. Dos problemas, y los dos de los caros:
 * ese {@code join} lo habria ejecutado <b>el hilo de JavaFX</b> (el interruptor
 * vive en Ajustes), o sea segundo y medio de ventana congelada al apagarlo; y
 * encender otra vez antes de que el anterior terminara dejaba dos hilos
 * escribiendo en la misma tuberia, que es justo como se entrelazan dos tramas y
 * se corrompe la conexion.
 *
 * <p>Asi que el hilo se crea una vez y se queda dormido en {@code wait()}, que
 * no cuesta nada. Apagar no lo mata: le pide que borre la presencia y se calle.
 *
 * <p>Quien decide QUE se ensenya es {@link DiscordStatus}; esta clase solo lo
 * lleva. Y quien avisa de los cambios son los dos sitios por los que pasa todo:
 * el cambio de pantalla en {@code NeoApp} y {@code NeoMatchUI.openView}.
 */
public final class DiscordRich {

    private DiscordRich() {
    }

    /**
     * La aplicacion en el portal de Discord, creada el 22-09-2026.
     *
     * <p><b>Es publico y no es un secreto</b>: identifica al juego, no a nadie.
     * Va escrito aqui a proposito — en un fichero de configuracion seria una
     * cosa mas que se puede quedar sin copiar al empaquetar.
     *
     * <p>Se puede cambiar con {@code -Dneo.discord.appId=...} para probar
     * contra otra aplicacion sin recompilar.
     */
    static final String APP_ID = System.getProperty("neo.discord.appId", "1551729585022967880");

    /**
     * La imagen grande, subida en Rich Presence → Recursos de arte.
     *
     * <p>⚠️ <b>Esta clave no se puede renombrar en el portal</b> una vez
     * guardada (lo avisa Discord: hay que borrar el recurso y volver a
     * subirlo). Si algun dia cambia alla, tiene que cambiar aqui el mismo dia:
     * una clave que no existe se ve como un hueco gris.
     */
    static final String LOGO_KEY = "logo";

    /** El texto del globo al pasar por encima de la imagen. */
    static final String LARGE_TEXT = "Neo Forge";

    /**
     * Lo minimo entre dos mensajes.
     *
     * <p>Discord admite 5 cada 20 segundos, o sea uno cada 4. Se deja un margen
     * porque pasarse no da error: descarta el mensaje en silencio.
     */
    private static final long MIN_GAP_MS = 5_000L;

    /** Cada cuanto se vuelve a mirar si Discord se ha abierto. */
    private static final long RETRY_MS = 30_000L;

    /** Lo que se manda para QUITAR la presencia. Details a null es la senyal. */
    static final DiscordActivity CLEAR = new DiscordActivity(null, null, 0L);

    private static final Object LOCK = new Object();

    /** Lo ultimo que se quiere ensenyar. Lo escribe cualquier hilo. */
    private static DiscordActivity pending;
    /**
     * Lo ultimo que Discord tiene puesto de verdad.
     *
     * <p>Lo <b>escribe</b> solo el hilo de aqui, pero lo <b>lee</b> tambien
     * {@link #update} desde el hilo de JavaFX o desde el del motor, asi que
     * volatil: sin eso se podria leer un valor viejo y reiniciar el reloj de
     * Discord en mitad de una partida.
     */
    private static volatile DiscordActivity sent;
    private static long lastSentAt;

    private static Thread worker;
    /** ¿Esta encendida la presencia? Con esto en false solo se manda el borrado. */
    private static volatile boolean enabled;
    private static DiscordIpc ipc;

    /**
     * Enciende o apaga la presencia.
     *
     * <p>Lo llama el arranque (con lo que diga Ajustes) y el propio interruptor
     * de Ajustes, para que el cambio se note sin reiniciar. <b>No bloquea</b>:
     * lo puede llamar el hilo de JavaFX.
     */
    public static synchronized void setEnabled(final boolean on) {
        if (on == enabled && worker != null) {
            return;
        }
        enabled = on;
        if (on) {
            if (worker == null) {
                worker = new Thread(DiscordRich::loop, "neo-discord");
                // Demonio: si el juego se cierra con esto a medias, que no lo
                // retenga ni un milisegundo.
                worker.setDaemon(true);
                worker.start();
            }
            return;
        }
        // Apagar es pedir el borrado por la via normal, para que salga por el
        // MISMO hilo que abrio la tuberia. Si no hay hilo es que nunca se
        // encendio: no hay nada puesto que borrar, y dejar el encargo ahi haria
        // que el dia que se encienda lo primero que hiciera fuera un borrado.
        if (worker == null) {
            return;
        }
        synchronized (LOCK) {
            pending = CLEAR;
            LOCK.notifyAll();
        }
    }

    /** ¿Esta encendida? */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Ensenya esto. <b>Se puede llamar desde el hilo de JavaFX</b>: solo deja
     * el dato y vuelve.
     *
     * <p>Si la presencia esta apagada no hace nada, ni siquiera guarda: asi el
     * dia que se encienda no aparece de golpe algo de hace media hora.
     */
    static void set(final DiscordActivity activity) {
        if (!enabled || activity == null) {
            return;
        }
        synchronized (LOCK) {
            pending = activity;
            LOCK.notifyAll();
        }
    }

    /**
     * Lo de antes, conservando el reloj si el renglon de arriba no ha cambiado.
     *
     * <p>Es lo que hace que "hace 12 minutos" cuente la partida entera en vez
     * de reiniciarse en cada turno.
     */
    public static void update(final String details, final String state) {
        if (!enabled || details == null) {
            return;
        }
        final DiscordActivity previous;
        synchronized (LOCK) {
            previous = pending != null && pending.details != null ? pending : sent;
        }
        if (previous != null && details.equals(previous.details)) {
            set(previous.withState(state));
        } else {
            set(DiscordActivity.now(details, state));
        }
    }

    // ------------------------------------------------------------------
    //  El hilo
    // ------------------------------------------------------------------

    private static void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                step();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final IOException | RuntimeException e) {
                // La tuberia se ha caido — Discord cerrado a media partida es
                // lo normal. Se suelta y se vuelve a intentar mas tarde.
                dropConnection();
                sleepQuietly(RETRY_MS);
            }
        }
        dropConnection();
    }

    private static void step() throws IOException, InterruptedException {
        final DiscordActivity want;
        synchronized (LOCK) {
            while (pending == null || pending.sameAs(sent)) {
                LOCK.wait(RETRY_MS);
            }
            want = pending;
        }

        final boolean farewell = want.details == null;

        // El freno, que el adios se salta: si nos vamos, lo que importa es que
        // la presencia desaparezca, no ahorrar un mensaje.
        //
        // Se duerme y se VUELVE A EMPEZAR en vez de mandar lo que habia:
        // mientras dormiamos puede haber llegado algo mas nuevo, y lo que
        // interesa es lo ultimo, no lo primero de la rafaga.
        if (!farewell) {
            final long since = System.currentTimeMillis() - lastSentAt;
            if (since < MIN_GAP_MS) {
                Thread.sleep(MIN_GAP_MS - since);
                return;
            }
        }

        if (ipc == null && !connect()) {
            if (farewell) {
                // No hay Discord y lo unico pendiente era borrar: no hay nada
                // que borrar. Se da por hecho, o el hilo se quedaria
                // reintentandolo cada treinta segundos para siempre.
                sent = want;
                return;
            }
            sleepQuietly(RETRY_MS);
            return;
        }

        ipc.send(DiscordIpc.OP_FRAME, payload(want));
        // Y se lee la respuesta SIEMPRE, aunque no nos diga nada: ver
        // DiscordIpc.receive.
        ipc.receive();
        sent = want;
        lastSentAt = System.currentTimeMillis();
    }

    /** El JSON del mensaje, o el de "quita lo que hay" si es la senyal CLEAR. */
    static String payload(final DiscordActivity want) {
        if (want.details == null) {
            return "{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"" + java.util.UUID.randomUUID()
                    + "\",\"args\":{\"pid\":" + ProcessHandle.current().pid()
                    + ",\"activity\":null}}";
        }
        return want.toJson(APP_ID, LARGE_TEXT);
    }

    /**
     * Abre la tuberia y se presenta. {@code false} si Discord no esta — que no
     * es un fallo, es el caso corriente.
     */
    private static boolean connect() throws IOException {
        final DiscordIpc fresh = DiscordIpc.open();
        if (fresh == null) {
            return false;
        }
        try {
            fresh.send(DiscordIpc.OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + APP_ID + "\"}");
            // La respuesta al apreton de manos (READY) hay que consumirla o se
            // quedaria delante de la respuesta al primer SET_ACTIVITY.
            fresh.receive();
        } catch (final IOException e) {
            fresh.close();
            throw e;
        }
        ipc = fresh;
        // Conexion nueva: Discord no tiene nada puesto, asi que lo que
        // creiamos mandado ya no vale.
        sent = null;
        return true;
    }

    private static void dropConnection() {
        final DiscordIpc open = ipc;
        ipc = null;
        sent = null;
        if (open != null) {
            open.close();
        }
    }

    private static void sleepQuietly(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
