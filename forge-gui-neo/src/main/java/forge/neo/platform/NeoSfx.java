package forge.neo.platform;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import forge.neo.sound.NeoSounds;

/**
 * Los sonidos de Neo Forge que Forge no tiene (la entrada del comandante, abrir
 * un sobre): no pasan por su SoundSystem, asi que el volumen y el "efectos
 * apagados" se miran aqui ({@link NeoSounds#volume}).
 */
public final class NeoSfx {

    private static final Map<File, forge.sound.IAudioClip> CLIPS = new ConcurrentHashMap<>();

    private NeoSfx() {
    }

    /**
     * Suena una variante del evento. {@code false} si no hay sonido nuestro (el
     * ajuste apagado): quien llama decide si pone el de Forge.
     */
    public static boolean play(final String event) {
        final File f = NeoSounds.pick(event);
        if (f == null) {
            return false;
        }
        final float volume = NeoSounds.volume();
        if (volume > 0) {
            try {
                CLIPS.computeIfAbsent(f, JavaSound::clip).play(volume);
            } catch (final RuntimeException e) {
                System.err.println("[sonido] " + event + ": " + e);
            }
        }
        return true;
    }
}
