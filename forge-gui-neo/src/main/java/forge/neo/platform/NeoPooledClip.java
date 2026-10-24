package forge.neo.platform;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import forge.neo.sound.NeoSounds;
import forge.sound.IAudioClip;

/**
 * El clip de un evento que tiene sonidos de Neo Forge ({@link NeoSounds}).
 *
 * <p>Forge crea UN clip por evento y lo reutiliza para siempre, asi que la
 * variante al azar no se puede elegir al crearlo: se elige en cada
 * {@link #play}. Y por lo mismo mira el ajuste en cada reproduccion - apagarlo
 * vuelve al de Forge al momento, sin reiniciar.
 */
final class NeoPooledClip implements IAudioClip {

    private final String name;
    /** El de Forge (puede no existir: entonces solo suenan los nuestros). */
    private final IAudioClip forge;
    private final Map<File, IAudioClip> ours = new ConcurrentHashMap<>();
    private volatile IAudioClip last;

    NeoPooledClip(final String name, final IAudioClip forge) {
        this.name = name;
        this.forge = forge;
    }

    @Override
    public void play(final float volume) {
        final File f = NeoSounds.pick(name);
        final IAudioClip c = f == null ? forge : ours.computeIfAbsent(f, NeoAudioClip::new);
        if (c != null) {
            last = c;
            c.play(volume);
        }
    }

    @Override
    public void loop() {
        if (forge != null) {
            last = forge;
            forge.loop();
        }
    }

    @Override
    public boolean isDone() {
        final IAudioClip c = last;
        return c == null || c.isDone();
    }

    @Override
    public void stop() {
        if (forge != null) {
            forge.stop();
        }
        ours.values().forEach(IAudioClip::stop);
    }

    @Override
    public void dispose() {
        stop();
    }
}
