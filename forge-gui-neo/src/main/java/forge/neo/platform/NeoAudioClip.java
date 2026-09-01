package forge.neo.platform;

import java.io.File;

import forge.sound.IAudioClip;
import javafx.application.Platform;
import javafx.scene.media.AudioClip;

/**
 * Un efecto de sonido corto (tapar, atacar, robar carta...).
 *
 * <p>Envuelve {@link AudioClip} de JavaFX, que esta pensado justo para esto:
 * carga el sonido entero en memoria y permite que suene varias veces solapado.
 * Es lo que hace falta cuando tapas cuatro tierras seguidas.
 *
 * <p>Quien decide QUE suena y CUANDO es el {@code SoundSystem} de Forge, que
 * {@code HostedMatch} ya suscribe a los eventos de la partida. Nosotros solo
 * ponemos el altavoz.
 */
public class NeoAudioClip implements IAudioClip {

    private final AudioClip clip;

    /**
     * @param file fichero de sonido ya resuelto en disco
     */
    public NeoAudioClip(final File file) {
        this.clip = new AudioClip(file.toURI().toString());
    }

    @Override
    public void play(final float volume) {
        // El motor llama desde el hilo de la partida. JavaFX no promete que sus
        // objetos de media acepten eso, y un fallo de audio no puede tumbar una
        // partida: al hilo de interfaz y envuelto en try/catch.
        run(() -> clip.play(clamp(volume)));
    }

    @Override
    public void loop() {
        run(() -> {
            clip.setCycleCount(AudioClip.INDEFINITE);
            clip.play();
        });
    }

    @Override
    public boolean isDone() {
        return !clip.isPlaying();
    }

    @Override
    public void stop() {
        run(clip::stop);
    }

    @Override
    public void dispose() {
        stop();
    }

    private static double clamp(final float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static void run(final Runnable action) {
        final Runnable safe = () -> {
            try {
                action.run();
            } catch (final RuntimeException e) {
                // Sin sonido se puede jugar; sin partida no.
                System.err.println("[neo] fallo de audio: " + e);
            }
        };
        if (Platform.isFxApplicationThread()) {
            safe.run();
        } else {
            try {
                Platform.runLater(safe);
            } catch (final IllegalStateException ignored) {
                // El toolkit no esta arrancado (modo consola): no hay sonido.
            }
        }
    }
}
