package forge.neo.net;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * Que version de NeoForge es esta, para compararla con la de los demas.
 *
 * <p><b>Por que no basta con lo que ya hace Forge.</b> Al entrar un invitado,
 * el servidor compara las versiones y avisa si no coinciden — pero compara
 * {@code BuildInfo.getVersionString()}, y todas nuestras compilaciones dicen lo
 * mismo ({@code 2.0.15-SNAPSHOT}) aunque esten encima de commits de Forge de
 * meses distintos. Dos amigos con zips de fechas distintas no reciben ningun
 * aviso, y lo que les llega son fallos raros: un mazo con una carta que el
 * otro no conoce, un paquete que no se deja leer.
 *
 * <p>La huella son los propios jars: el nuestro y los cuatro del motor. Un zip
 * copiado da la misma; cualquier recompilacion, otra. En el arbol de desarrollo
 * (clases sueltas en {@code target/classes}) sale {@link #DEV} y no se compara:
 * ahi recompilamos a cada rato y el aviso seria ruido.
 *
 * <p>Viaja por el <b>chat</b>, con una marca que la pantalla no ensenya
 * ({@link #MARKER}): el paquete de entrada de Forge no tiene donde meterla y
 * tocarlo seria tocar el motor.
 */
public final class NetBuild {

    /** Lo que empieza una linea de chat que en realidad es una huella. */
    public static final String MARKER = "[neoforge-build:";

    public static final String DEV = "dev";

    private static volatile String cached;

    private NetBuild() {
    }

    /** La huella de esta copia. La primera vez lee ~40 MB: no desde el hilo de JavaFX. */
    public static String id() {
        String out = cached;
        if (out == null) {
            out = compute();
            cached = out;
        }
        return out;
    }

    /** La linea de chat que lleva la huella. */
    public static String message() {
        return MARKER + id() + "]";
    }

    /** La huella que trae una linea de chat, o null si la linea es normal. */
    public static String parse(final String text) {
        if (text == null) {
            return null;
        }
        final String t = text.trim();
        if (!t.startsWith(MARKER) || !t.endsWith("]")) {
            return null;
        }
        return t.substring(MARKER.length(), t.length() - 1);
    }

    /** Si dos huellas son de versiones distintas. Con una en desarrollo, nunca. */
    public static boolean differs(final String a, final String b) {
        if (a == null || b == null || DEV.equals(a) || DEV.equals(b)) {
            return false;
        }
        return !a.equals(b);
    }

    private static String compute() {
        final Class<?>[] parts = {
                forge.neo.NeoText.class,                    // el nuestro
                forge.card.CardRules.class,                 // forge-core
                forge.game.Game.class,                      // forge-game
                forge.ai.AiController.class,                // forge-ai
                forge.gamemodes.match.GameLobby.class,      // forge-gui
        };
        try {
            final MessageDigest sha = MessageDigest.getInstance("SHA-256");
            final byte[] buf = new byte[1 << 16];
            for (final Class<?> c : parts) {
                final File f = new File(c.getProtectionDomain().getCodeSource()
                        .getLocation().toURI());
                if (!f.isFile()) {
                    return DEV;
                }
                try (InputStream in = Files.newInputStream(f.toPath())) {
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        sha.update(buf, 0, n);
                    }
                }
            }
            final byte[] d = sha.digest();
            final StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                sb.append(String.format("%02x", d[i] & 0xff));
            }
            return sb.toString();
        } catch (final Exception | LinkageError e) {
            System.out.println("[lobby] no se ha podido calcular la huella de la version: " + e);
            return DEV;
        }
    }
}
