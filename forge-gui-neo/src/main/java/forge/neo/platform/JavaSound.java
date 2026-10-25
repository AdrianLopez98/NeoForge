package forge.neo.platform;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.SourceDataLine;

import forge.sound.IAudioClip;
import forge.sound.IAudioMusic;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.MP3Decoder;
import javazoom.jl.decoder.OutputBuffer;

/**
 * <b>El sonido sin JavaFX Media</b>, para Linux.
 *
 * <p>Reportado en Discord el 24-10-2026 desde una ROG Xbox Ally X con SteamOS
 * (y el log, de una Steam Deck): ni un sonido. Todos los de Forge y los
 * nuestros son MP3, y en Linux JavaFX no los decodifica: se lo pide al FFmpeg
 * del sistema, y solo a ciertas versiones. SteamOS no trae uno que le valga, y
 * JavaFX se queda en <i>"MediaException: Could not create player!"</i> sin que
 * se oiga nada. En las maquinas de GitHub no se vio porque Ubuntu si lo trae (y
 * no tienen altavoz).
 *
 * <p>Asi que en Linux el MP3 se decodifica aqui, en Java, con el
 * {@code jlayer} que ya trae libGDX para la Aventura (el mismo
 * {@code MP3Decoder} con el que libGDX lee sus MP3), y sale por
 * {@code javax.sound}: ALSA, que en SteamOS va a PipeWire. No depende de
 * ninguna libreria del sistema.
 *
 * <p>En Windows y Mac sigue JavaFX, que ahi funciona. Se fuerza con
 * {@code -Dneo.audio=java} o {@code -Dneo.audio=fx}, que es como se prueba
 * esto desde Windows.
 *
 * <p>Ningun fallo de audio sale de aqui: sin sonido se puede jugar.
 */
public final class JavaSound {

    private JavaSound() {
    }

    /** Si el sonido va por aqui y no por JavaFX. */
    public static boolean enabled() {
        final String forced = System.getProperty("neo.audio", "");
        if ("java".equalsIgnoreCase(forced)) {
            return true;
        }
        if ("fx".equalsIgnoreCase(forced)) {
            return false;
        }
        return NeoOs.LINUX;
    }

    /** El efecto de un fichero, por donde toque: aqui o JavaFX ({@link NeoAudioClip}). */
    public static IAudioClip clip(final File file) {
        if (!enabled()) {
            return new NeoAudioClip(file);
        }
        announce();
        return new SoundClip(file);
    }

    private static final java.util.concurrent.atomic.AtomicBoolean ANNOUNCED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static final boolean DEBUG = Boolean.getBoolean("neo.audio.debug");

    /** Una vez, en el registro: es lo primero que se mira en el log de un jugador sin sonido. */
    private static void announce() {
        if (ANNOUNCED.compareAndSet(false, true)) {
            System.out.println("[sonido] los MP3 van por javax.sound, sin JavaFX Media (" + System.getProperty("os.name") + ")");
        }
    }

    /** La musica de una ruta, por donde toque: aqui o JavaFX ({@link NeoAudioMusic}). */
    public static IAudioMusic music(final String path) {
        if (!enabled()) {
            return new NeoAudioMusic(path);
        }
        announce();
        return new Music(path);
    }

    /**
     * Un hilo para abrir lineas: abrir una tarda unos milisegundos, y quien
     * pide el sonido es el hilo de la partida.
     */
    private static final ExecutorService LINES = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "neo-sound");
        t.setDaemon(true);
        return t;
    });

    /** Un efecto ya decodificado entero: suena muchas veces y es corto. */
    private static final class Pcm {
        final AudioFormat format;
        final byte[] data;

        Pcm(final AudioFormat format, final byte[] data) {
            this.format = format;
            this.data = data;
        }
    }

    private static final Map<File, Pcm> DECODED = new ConcurrentHashMap<>();

    /**
     * El MP3 entero a PCM de 16 bits. Como lo hace libGDX
     * ({@code Mp3.Sound}): un frame que no se entiende se salta.
     */
    static Pcm decode(final File file) throws IOException {
        final Pcm known = DECODED.get(file);
        if (known != null) {
            return known;
        }
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), 32 * 1024)) {
            final Decoded d = new Decoded(in);
            final ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
            byte[] chunk;
            while ((chunk = d.next()) != null) {
                out.write(chunk, 0, d.length);
            }
            if (d.format == null) {
                throw new IOException("no es un MP3: " + file.getName());
            }
            final Pcm pcm = new Pcm(d.format, out.toByteArray());
            DECODED.put(file, pcm);
            return pcm;
        }
    }

    /**
     * Para el comprobador ({@code audiocheck}): decodifica entero un MP3 y dice
     * cuantos bytes de PCM salen y en que formato, sin sonar.
     */
    public static String probe(final InputStream in) throws IOException {
        final Decoded d = new Decoded(new BufferedInputStream(in, 32 * 1024));
        long bytes = 0;
        while (d.next() != null) {
            bytes += d.length;
        }
        d.close();
        if (d.format == null || bytes == 0) {
            throw new IOException("sin audio");
        }
        return bytes + " bytes, " + (int) d.format.getSampleRate() + " Hz, " + d.format.getChannels() + " canal(es)";
    }

    /**
     * Para el comprobador: si este equipo deja abrir una linea de sonido. En las
     * maquinas de GitHub no hay tarjeta, y eso no es un fallo nuestro.
     */
    public static String probeLine() {
        try {
            final AudioFormat f = new AudioFormat(44100, 16, 2, true, false);
            final SourceDataLine l = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, f));
            l.open(f);
            l.close();
            return null;
        } catch (final Exception | LinkageError e) {
            return e.toString();
        }
    }

    /** Un MP3 leido frame a frame: para el efecto entero y para la musica en streaming. */
    private static final class Decoded {
        private final Bitstream bits;
        private MP3Decoder decoder;
        private OutputBuffer buffer;
        AudioFormat format;
        int length;

        Decoded(final InputStream in) {
            this.bits = new Bitstream(in);
        }

        /** Los bytes del frame siguiente (validos hasta {@link #length}), o null al acabar. */
        byte[] next() throws IOException {
            try {
                while (true) {
                    final Header h = bits.readFrame();
                    if (h == null) {
                        return null;
                    }
                    if (decoder == null) {
                        final int channels = h.mode() == Header.SINGLE_CHANNEL ? 1 : 2;
                        buffer = new OutputBuffer(channels, false);
                        decoder = new MP3Decoder();
                        decoder.setOutputBuffer(buffer);
                        format = new AudioFormat(h.frequency(), 16, channels, true, false);
                    }
                    try {
                        decoder.decodeFrame(h, bits);
                    } catch (final Exception skip) {
                        // Un frame roto no para el resto, como en libGDX.
                    }
                    bits.closeFrame();
                    length = buffer.reset();
                    if (length > 0) {
                        return buffer.getBuffer();
                    }
                }
            } catch (final javazoom.jl.decoder.BitstreamException e) {
                throw new IOException(e);
            }
        }

        void close() {
            try {
                bits.close();
            } catch (final Exception ignored) {
                // Ya no hace falta.
            }
        }
    }

    /** Volumen de 0 a 1 en el control de ganancia de la linea, si lo tiene. */
    static void setVolume(final Line line, final float volume) {
        try {
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                final FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                final float v = Math.max(0f, Math.min(1f, volume));
                final float db = v <= 0.0001f ? gain.getMinimum() : (float) (20 * Math.log10(v));
                gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
            }
        } catch (final RuntimeException ignored) {
            // Sin control de volumen suena a tope, que es mejor que no sonar.
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean WARNED =
            new java.util.concurrent.atomic.AtomicBoolean();

    /** El primer fallo se escribe en el registro, y solo el primero: es para el log del jugador. */
    static void warn(final String what, final Throwable e) {
        if (WARNED.compareAndSet(false, true)) {
            System.err.println("[sonido] " + what + ": " + e);
        }
    }

    // ------------------------------------------------------------------

    /** Un efecto: cada {@link #play} abre su propia linea, asi se solapan. */
    static final class SoundClip implements IAudioClip {

        private final File file;
        private final Set<Clip> active = ConcurrentHashMap.newKeySet();

        SoundClip(final File file) {
            this.file = file;
            // Se decodifica ya, fuera del hilo de la partida: el primer "tap"
            // no puede esperar a que se lea el fichero.
            LINES.execute(() -> {
                try {
                    decode(file);
                } catch (final Exception e) {
                    warn(file.getName(), e);
                }
            });
        }

        @Override
        public void play(final float volume) {
            start(volume, false);
        }

        @Override
        public void loop() {
            start(1f, true);
        }

        private void start(final float volume, final boolean loop) {
            LINES.execute(() -> {
                try {
                    final Pcm pcm = decode(file);
                    final Clip clip = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, pcm.format));
                    clip.open(pcm.format, pcm.data, 0, pcm.data.length);
                    setVolume(clip, volume);
                    clip.addLineListener(ev -> {
                        if (ev.getType() == LineEvent.Type.STOP) {
                            active.remove(clip);
                            // Cerrar dentro del aviso puede bloquear la linea:
                            // se cierra desde el hilo del sonido.
                            LINES.execute(clip::close);
                        }
                    });
                    active.add(clip);
                    if (DEBUG) {
                        System.out.println("[sonido] suena " + file.getName() + " (" + pcm.data.length + " bytes)");
                    }
                    if (loop) {
                        clip.loop(Clip.LOOP_CONTINUOUSLY);
                    } else {
                        clip.start();
                    }
                } catch (final Exception e) {
                    warn(file.getName(), e);
                }
            });
        }

        @Override
        public boolean isDone() {
            return active.isEmpty();
        }

        @Override
        public void stop() {
            for (final Clip c : active) {
                c.stop();
            }
        }

        @Override
        public void dispose() {
            stop();
        }
    }

    // ------------------------------------------------------------------

    /**
     * La musica: en streaming, frame a frame, por una {@link SourceDataLine}
     * en su propio hilo. Pausar es dejar de escribir en la linea.
     */
    static final class Music implements IAudioMusic {

        private final File file;
        private volatile float volume = 1f;
        private volatile boolean playing;
        private volatile boolean paused;
        private volatile boolean stopped;
        private volatile SourceDataLine line;
        private Thread thread;

        Music(final String path) {
            this.file = new File(path);
        }

        @Override
        public synchronized void play(final Runnable onComplete) {
            if (thread != null) {
                return;
            }
            playing = true;
            stopped = false;
            thread = new Thread(() -> run(onComplete), "neo-music");
            thread.setDaemon(true);
            thread.start();
        }

        private void run(final Runnable onComplete) {
            Decoded d = null;
            boolean finished = false;
            try (InputStream in = new BufferedInputStream(new FileInputStream(file), 64 * 1024)) {
                d = new Decoded(in);
                byte[] chunk;
                while (!stopped && (chunk = d.next()) != null) {
                    if (line == null) {
                        final SourceDataLine l = (SourceDataLine) AudioSystem.getLine(
                                new DataLine.Info(SourceDataLine.class, d.format));
                        l.open(d.format);
                        JavaSound.setVolume(l, volume);
                        l.start();
                        line = l;
                        if (DEBUG) {
                            System.out.println("[sonido] musica " + file.getName() + " (" + d.format + ")");
                        }
                    }
                    while (paused && !stopped) {
                        Thread.sleep(50);
                    }
                    if (!stopped) {
                        line.write(chunk, 0, d.length);
                    }
                }
                if (!stopped && line != null) {
                    line.drain();
                    finished = true;
                }
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final Exception e) {
                warn(file.getName(), e);
            } finally {
                if (d != null) {
                    d.close();
                }
                final SourceDataLine l = line;
                line = null;
                if (l != null) {
                    l.stop();
                    l.close();
                }
                playing = false;
                synchronized (this) {
                    thread = null;
                }
            }
            // Como MediaPlayer.setOnEndOfMedia: solo si se ha acabado sola.
            if (finished && onComplete != null) {
                onComplete.run();
            }
        }

        @Override
        public void pause() {
            paused = true;
            final SourceDataLine l = line;
            if (l != null) {
                l.stop();
            }
        }

        @Override
        public void resume() {
            paused = false;
            final SourceDataLine l = line;
            if (l != null) {
                l.start();
            }
        }

        @Override
        public void stop() {
            stopped = true;
            playing = false;
            final SourceDataLine l = line;
            if (l != null) {
                l.stop();
                l.flush();
            }
        }

        @Override
        public void dispose() {
            stop();
        }

        @Override
        public void setVolume(final float value) {
            volume = Math.max(0f, Math.min(1f, value));
            final SourceDataLine l = line;
            if (l != null) {
                JavaSound.setVolume(l, volume);
            }
        }

        @Override
        public boolean isPlaying() {
            return playing;
        }
    }
}
