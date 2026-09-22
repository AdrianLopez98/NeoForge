package forge.neo.discord;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

/**
 * La tuberia hacia el cliente de Discord. <b>Sin dependencias nuevas.</b>
 *
 * <p>Discord no pide una libreria para esto: su cliente abre al arrancar un
 * socket <b>local</b> y habla un protocolo de tres lineas — tramas de
 * {@code [opcode int32 LE][longitud int32 LE][JSON UTF-8]}. Se puede hablar con
 * el desde Java pelado.
 *
 * <p><b>Por que importa no meter una libreria.</b> Lo habitual
 * ({@code java-discord-rpc}) es JNA envolviendo una {@code .dll} nativa por
 * plataforma. Eso hay que colarlo en {@code jpackage}, repetirlo para el
 * {@code .dmg} que compila GitHub Actions (§14a) y volver a mirarlo en cada
 * actualizacion. Todo para hablar un protocolo que cabe en esta clase. Un
 * adorno no puede complicar el empaquetado del juego.
 *
 * <p><b>Donde esta el socket</b>, que es lo unico que cambia entre sistemas:
 * <ul>
 *   <li><b>Windows</b>: la tuberia con nombre {@code \\.\pipe\discord-ipc-N},
 *       que se abre como un fichero normal. Si no existe, {@code new
 *       RandomAccessFile} falla <b>al instante</b> — que es justo lo que
 *       queremos cuando Discord no esta abierto.</li>
 *   <li><b>Mac</b>: un socket de dominio Unix en {@code $TMPDIR/discord-ipc-N}.
 *       Java 17 ya lo trae de serie ({@link UnixDomainSocketAddress}), asi que
 *       tampoco aqui hace falta nada nativo.</li>
 * </ul>
 *
 * <p>La {@code N} va de 0 a 9: si tienes Discord y Discord PTB abiertos, el
 * segundo coge el 1. Se prueban los diez y se usa el primero que conteste.
 *
 * <p>⚠️ <b>Esta clase bloquea</b>: escribe y se queda esperando la respuesta.
 * No se llama nunca desde el hilo de JavaFX — solo desde el hilo propio de
 * {@link DiscordRich}, que para eso existe.
 */
final class DiscordIpc implements Closeable {

    /** El apreton de manos: se manda una vez, al abrir. */
    static final int OP_HANDSHAKE = 0;
    /** Una orden normal ({@code SET_ACTIVITY}). */
    static final int OP_FRAME = 1;
    /** "Me voy" — o "te echo", si lo manda Discord. */
    static final int OP_CLOSE = 2;
    /** Discord comprueba que seguimos vivos; hay que devolverle un PONG. */
    static final int OP_PING = 3;
    static final int OP_PONG = 4;

    /** Cuantas tuberias se prueban (Discord, PTB, Canary... cada uno coge una). */
    private static final int SOCKETS = 10;

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    /** En Windows; null en los demas. */
    private final RandomAccessFile pipe;
    /** Fuera de Windows; null en Windows. */
    private final SocketChannel socket;

    private DiscordIpc(final RandomAccessFile pipe, final SocketChannel socket) {
        this.pipe = pipe;
        this.socket = socket;
    }

    /**
     * Abre la primera tuberia que conteste, o devuelve {@code null} si no hay
     * ninguna — que es lo normal: casi nadie tiene Discord abierto siempre.
     *
     * <p>No lanza excepcion por no encontrarla a proposito. "Discord no esta"
     * no es un error de nada: es el caso corriente, y tratarlo como un fallo
     * llenaria el registro de ruido en cada reintento.
     */
    static DiscordIpc open() {
        for (int i = 0; i < SOCKETS; i++) {
            final DiscordIpc ipc = openOne(i);
            if (ipc != null) {
                return ipc;
            }
        }
        return null;
    }

    private static DiscordIpc openOne(final int index) {
        try {
            if (WINDOWS) {
                return new DiscordIpc(new RandomAccessFile(
                        new File("\\\\.\\pipe\\discord-ipc-" + index), "rw"), null);
            }
            final Path path = unixDir().resolve("discord-ipc-" + index);
            if (!path.toFile().exists()) {
                return null;
            }
            final SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
            ch.connect(UnixDomainSocketAddress.of(path));
            return new DiscordIpc(null, ch);
        } catch (final IOException | RuntimeException e) {
            // No existe, esta ocupada, o el sistema no habla sockets Unix.
            // Cualquiera de las tres significa lo mismo: probar la siguiente.
            return null;
        }
    }

    /**
     * La carpeta donde Discord deja su socket fuera de Windows.
     *
     * <p>El orden es el que usa el propio Discord: la carpeta de ejecucion del
     * usuario si la hay, y si no la de temporales. En un Mac es {@code $TMPDIR}.
     */
    private static Path unixDir() {
        for (final String var : new String[] {"XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP"}) {
            final String v = System.getenv(var);
            if (v != null && !v.isEmpty()) {
                return Path.of(v);
            }
        }
        return Path.of("/tmp");
    }

    /** Manda una trama: opcode y JSON. */
    void send(final int opcode, final String json) throws IOException {
        final byte[] body = json.getBytes(StandardCharsets.UTF_8);
        final ByteBuffer buf = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(opcode);
        buf.putInt(body.length);
        buf.put(body);
        if (pipe != null) {
            pipe.write(buf.array());
            return;
        }
        buf.flip();
        while (buf.hasRemaining()) {
            socket.write(buf);
        }
    }

    /**
     * Espera la respuesta a lo ultimo que se mando y devuelve su JSON.
     *
     * <p><b>Hay que leerla aunque no nos diga nada.</b> Discord contesta a cada
     * orden, y si no vaciamos su lado de la tuberia acaba llenandose: a partir
     * de ahi nuestras escrituras se bloquean y la presencia se queda congelada
     * sin que nadie diga nada. O sea que esto no es cortesia, es lo que evita
     * un cuelgue a la media hora de partida.
     *
     * <p>Un PING por el medio se contesta aqui mismo y se sigue esperando: si
     * se tomara por la respuesta, todo lo que venga despues iria desfasado un
     * mensaje.
     */
    String receive() throws IOException {
        while (true) {
            final ByteBuffer h = ByteBuffer.wrap(readFully(8)).order(ByteOrder.LITTLE_ENDIAN);
            final int opcode = h.getInt();
            final int length = h.getInt();
            if (length < 0 || length > 1 << 20) {
                throw new IOException("trama con longitud imposible: " + length);
            }
            final String body = new String(readFully(length), StandardCharsets.UTF_8);
            if (opcode == OP_PING) {
                send(OP_PONG, body);
                continue;
            }
            if (opcode == OP_CLOSE) {
                throw new IOException("Discord ha cerrado la conexion: " + body);
            }
            return body;
        }
    }

    private byte[] readFully(final int n) throws IOException {
        final byte[] out = new byte[n];
        if (n == 0) {
            return out;
        }
        if (pipe != null) {
            pipe.readFully(out);
            return out;
        }
        final ByteBuffer buf = ByteBuffer.wrap(out);
        while (buf.hasRemaining()) {
            if (socket.read(buf) < 0) {
                throw new IOException("la tuberia se ha cerrado");
            }
        }
        return out;
    }

    @Override
    public void close() {
        try {
            if (pipe != null) {
                pipe.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (final IOException ignored) {
            // Cerrando ya no hay nada que salvar.
        }
    }
}
