package forge.neo.update;

import java.util.concurrent.atomic.AtomicBoolean;

import forge.neo.NeoSettings;
import forge.neo.NeoVersion;
import forge.neo.platform.NeoOs;
import javafx.application.Platform;

/**
 * El aviso de version nueva en el menu del PC: cuando preguntar, a quien
 * avisar y que se recuerda. Lo que no depende de nada (comparar, leer a
 * itch.io, el canal) esta en {@link ItchVersion}, que comparte con Android.
 *
 * <p>Pedido en itch.io el 28-09-2026: <i>"it will be good if when the game
 * detects there's a new version, it gives a prompt then when click brings us to
 * itch.io"</i>. Quien baja el zip a mano no se entera de los arreglos si no
 * vuelve a mirar la pagina; quien usa la app de itch ya se actualiza solo.
 *
 * <h2>Lo que no puede hacer nunca</h2>
 *
 * <p>Es adorno, como la presencia de Discord, y todo sale de ahi:
 *
 * <ul>
 *   <li><b>Sin internet no pasa nada.</b> Un hilo propio y demonio, con tope
 *       de tiempo, y cualquier fallo —sin linea, sin DNS, un proxy, un 500,
 *       una respuesta rara— es "no se sabe", que se trata igual que "estas al
 *       dia": no se ensenya nada. En la copia del pueblo no sale jamas.</li>
 *   <li><b>No bloquea el arranque</b> ni el hilo de JavaFX: el resultado
 *       llega por {@code Platform.runLater} y el menu se entera por
 *       {@link #whenKnown}.</li>
 *   <li><b>Se pregunta una vez por arranque</b>, no cada vez que se vuelve al
 *       menu.</li>
 *   <li><b>"Ahora no" se recuerda</b> ({@link #DISMISSED}): esa version ya no
 *       vuelve a salir en grande, solo el puntito del boton de itch. La
 *       siguiente, si.</li>
 * </ul>
 *
 * <p>Se apaga en Ajustes ({@link NeoSettings#UPDATE_CHECK}): preguntar a
 * itch.io le ensenya tu IP, y quien no quiera eso tiene que poder evitarlo.
 *
 * <p>⚠️ Solo funciona si el numero que se escribe al subir a itch.io es el
 * mismo que {@code <neo.version>} del pom. Si se sube la 4.2 con el jar
 * diciendo 4.1, quien la baje vera el aviso de la 4.2 teniendola ya.
 */
public final class NeoUpdate {

    /** La pagina del juego: a donde llevan el boton y el aviso. */
    public static final String PAGE_URL = ItchVersion.PAGE_URL;

    /** La version a la que se le dio "Ahora no". */
    public static final String DISMISSED = "update.dismissed";

    private static final AtomicBoolean STARTED = new AtomicBoolean();

    /**
     * Cuantas veces se ha puesto en marcha. Tiene que ser 0 o 1 en todo el
     * arranque: una consulta por abrir el juego, nunca un sondeo. Lo mira
     * {@link UpdateCheck}.
     */
    private static final java.util.concurrent.atomic.AtomicInteger STARTS =
            new java.util.concurrent.atomic.AtomicInteger();

    /** La de itch.io, o {@code null}. */
    private static volatile String latest;
    /** Ya se ha contestado (bien o mal). Solo hilo de JavaFX. */
    private static boolean known;
    /** Quien espera la respuesta: el menu que se esta ensenyando. Solo hilo de JavaFX. */
    private static Runnable waiting;

    private NeoUpdate() {
    }

    /**
     * Pregunta a itch.io, una vez por arranque y en su propio hilo. Nunca
     * bloquea ni lanza.
     *
     * <p>{@code -Dneo.update.fake=4.9} contesta eso sin salir a la red: es para
     * capturar el aviso con {@code --snapshot} sin tener que publicar nada.
     */
    public static void start() {
        if (!NeoSettings.updateCheck() || !STARTED.compareAndSet(false, true)) {
            return;
        }
        STARTS.incrementAndGet();
        final String fake = System.getProperty("neo.update.fake");
        if (fake != null) {
            deliver(ItchVersion.clean(fake));
            return;
        }
        final String mine = NeoVersion.neoVersion();
        if (mine == null) {
            // Sin version propia (clases sin pasar por Maven) no hay con que
            // comparar: cualquier cosa que llegara seria "mas nueva".
            deliver(null);
            return;
        }
        final Thread t = new Thread(() -> {
            String found = null;
            try {
                found = ItchVersion.fetch(ItchVersion.url(channel()), "NeoForge/" + mine);
            } catch (final Throwable e) {
                // Nada de aqui puede llegar al jugador.
                System.err.println("[neo] version nueva: " + e);
            } finally {
                deliver(found);
            }
        }, "neo-update-check");
        t.setDaemon(true);
        t.start();
    }

    /** Lo que ha contestado itch.io, ya en el hilo de JavaFX. */
    private static void deliver(final String found) {
        try {
            Platform.runLater(() -> {
                latest = found;
                known = true;
                final Runnable w = waiting;
                // Se suelta al contestar, pase lo que pase: el menu que espera
                // no puede quedarse agarrado para siempre (principio 11).
                waiting = null;
                if (w != null) {
                    try {
                        w.run();
                    } catch (final RuntimeException e) {
                        System.err.println("[neo] version nueva: " + e);
                    }
                }
            });
        } catch (final IllegalStateException e) {
            // Sin JavaFX arrancado (una prueba sin ventana): nadie lo va a ver.
            latest = found;
        }
    }

    /**
     * Avisa cuando se sepa la respuesta. Si ya se sabe, no hace nada: quien
     * llama ya la ha leido al montarse.
     *
     * <p>Solo espera <b>uno</b>, el ultimo: el menu se vuelve a montar cada vez
     * que se entra, y el anterior ya no se ve. Y solo si hay una pregunta en
     * marcha: apagado (o en una captura) no va a contestar nadie, y el menu se
     * quedaria agarrado para siempre (principio 11). Hilo de JavaFX.
     */
    public static void whenKnown(final Runnable r) {
        if (!known && STARTED.get()) {
            waiting = r;
        }
    }

    /** Pregunta en el acto si se enciende desde Ajustes y aun no se habia hecho. */
    public static void enabledChanged(final boolean on) {
        if (on) {
            start();
        }
    }

    /**
     * La version mas nueva que hay en itch.io, si es mas nueva que esta, o
     * {@code null}. Aunque se le haya dado "Ahora no": es lo que enciende el
     * puntito del boton.
     */
    public static String newer() {
        if (!NeoSettings.updateCheck()) {
            return null;
        }
        return ItchVersion.offerFor(NeoVersion.neoVersion(), latest, null);
    }

    /** La version de la que hay que avisar en grande, o {@code null}. */
    public static String offer() {
        if (!NeoSettings.updateCheck()) {
            return null;
        }
        return ItchVersion.offerFor(NeoVersion.neoVersion(), latest, NeoSettings.get(DISMISSED, null));
    }

    /** Cuantas veces se ha puesto en marcha en este arranque: 0 o 1. */
    static int starts() {
        return STARTS.get();
    }

    /** "Ahora no": esa version ya no vuelve a salir en grande. */
    public static void dismiss(final String version) {
        NeoSettings.set(DISMISSED, version);
        NeoSettings.save();
    }

    /**
     * El canal de itch.io de esta copia ({@link ItchVersion#channelFor}).
     * {@code -Dneo.update.channel} lo cambia.
     */
    static String channel() {
        final String forced = System.getProperty("neo.update.channel");
        if (forced != null && !forced.isBlank()) {
            return forced;
        }
        return ItchVersion.channelFor(NeoOs.MAC, System.getProperty("os.arch", ""));
    }
}
