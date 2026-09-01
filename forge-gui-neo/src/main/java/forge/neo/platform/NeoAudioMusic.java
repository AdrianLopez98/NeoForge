package forge.neo.platform;

import java.io.File;

import forge.sound.IAudioMusic;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

/**
 * La musica de fondo.
 *
 * <p>A diferencia de un efecto, aqui interesa reproducir en streaming y poder
 * pausar, reanudar y cambiar el volumen sobre la marcha, que es exactamente lo
 * que da {@link MediaPlayer}.
 *
 * <p>El {@code SoundSystem} de Forge encadena pistas solo: cuando una termina
 * llama al {@code onComplete} que le pasamos y elige otra de la lista.
 */
public class NeoAudioMusic implements IAudioMusic {

    private MediaPlayer player;
    private volatile boolean playing;
    private double volume = 1.0;

    /**
     * @param path ruta completa del fichero de musica
     */
    public NeoAudioMusic(final String path) {
        try {
            this.player = new MediaPlayer(new Media(new File(path).toURI().toString()));
            this.player.setVolume(volume);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido cargar la musica: " + path);
            this.player = null;
        }
    }

    @Override
    public void play(final Runnable onComplete) {
        if (player == null) {
            return;
        }
        run(() -> {
            player.setOnEndOfMedia(() -> {
                playing = false;
                if (onComplete != null) {
                    onComplete.run();
                }
            });
            playing = true;
            player.play();
        });
    }

    @Override
    public void pause() {
        run(() -> {
            if (player != null) {
                player.pause();
            }
        });
    }

    @Override
    public void resume() {
        run(() -> {
            if (player != null) {
                player.play();
            }
        });
    }

    @Override
    public void stop() {
        playing = false;
        run(() -> {
            if (player != null) {
                player.stop();
            }
        });
    }

    @Override
    public void dispose() {
        playing = false;
        run(() -> {
            if (player != null) {
                player.dispose();
                player = null;
            }
        });
    }

    @Override
    public void setVolume(final float value) {
        volume = Math.max(0, Math.min(1, value));
        run(() -> {
            if (player != null) {
                player.setVolume(volume);
            }
        });
    }

    @Override
    public boolean isPlaying() {
        return playing;
    }

    private static void run(final Runnable action) {
        final Runnable safe = () -> {
            try {
                action.run();
            } catch (final RuntimeException e) {
                System.err.println("[neo] fallo de musica: " + e);
            }
        };
        if (Platform.isFxApplicationThread()) {
            safe.run();
        } else {
            try {
                Platform.runLater(safe);
            } catch (final IllegalStateException ignored) {
                // Sin toolkit no hay musica; no es motivo para fallar.
            }
        }
    }
}
