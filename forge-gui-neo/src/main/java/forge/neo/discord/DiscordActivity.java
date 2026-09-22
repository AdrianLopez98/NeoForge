package forge.neo.discord;

import java.util.Objects;

/**
 * Lo que se ve en el perfil de Discord: dos renglones y una imagen.
 *
 * <p>Inmutable a proposito. Esto viaja del hilo de JavaFX (que es quien sabe
 * que pantalla hay abierta) al hilo de {@link DiscordRich} (que es quien habla
 * por la tuberia), y un objeto que no cambia se puede pasar entre hilos sin
 * cerrojos ni copias.
 *
 * <p>Asi queda en Discord:
 * <pre>
 *   [logo]  Neo Forge
 *           Commander · contra 3 rivales      &lt;- details
 *           Turno 7                           &lt;- state
 *           hace 12 minutos                   &lt;- del reloj
 * </pre>
 *
 * <p><b>Lo que NO se manda, y es una decision:</b> ni el nombre del rival, ni
 * su IP, ni nada de una partida en red. Esto lo ven todos los amigos de quien
 * juega, y un dato de otra persona no es nuestro para repartirlo. Una partida
 * privada dice "partida privada" y ya.
 */
final class DiscordActivity {

    /**
     * El limite de Discord por renglon. Pasarse no da error: <b>corta el
     * mensaje entero</b> y no se ve nada, que es mucho peor que una frase
     * acortada. Por eso se recorta aqui y no se confia en que quepa.
     */
    private static final int MAX = 128;

    /** Renglon de arriba: que esta haciendo. */
    final String details;
    /** Renglon de abajo: el detalle (turno, piso, ronda). Puede ser null. */
    final String state;
    /**
     * Cuando empezo esto, en segundos desde 1970; 0 para no ensenyar reloj.
     *
     * <p>Se conserva entre actualizaciones mientras no cambie {@link #details}:
     * si se reiniciara en cada turno, el reloj de Discord se quedaria siempre
     * en "hace unos segundos" y no contaria nada.
     */
    final long startedAt;

    DiscordActivity(final String details, final String state, final long startedAt) {
        this.details = clip(details);
        this.state = clip(state);
        this.startedAt = startedAt;
    }

    private static String clip(final String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        return s.length() <= MAX ? s : s.substring(0, MAX - 1) + "…";
    }

    /**
     * Lo mismo pero empezando ahora.
     *
     * <p>Se llama al entrar en algo nuevo (una partida, el constructor de
     * mazos): ahi el reloj SI tiene que volver a cero.
     */
    static DiscordActivity now(final String details, final String state) {
        return new DiscordActivity(details, state, System.currentTimeMillis() / 1000L);
    }

    /** Lo mismo con otro renglon de abajo, conservando el reloj. */
    DiscordActivity withState(final String newState) {
        return new DiscordActivity(details, newState, startedAt);
    }

    /**
     * ¿Ensenya lo mismo que esa otra?
     *
     * <p>Lo usa {@link DiscordRich} para no gastar una de las pocas
     * actualizaciones que Discord acepta en mandar algo identico a lo que ya
     * hay puesto.
     */
    boolean sameAs(final DiscordActivity other) {
        return other != null
                && Objects.equals(details, other.details)
                && Objects.equals(state, other.state)
                && startedAt == other.startedAt;
    }

    /**
     * El cuerpo del {@code SET_ACTIVITY}, en JSON escrito a mano.
     *
     * <p>Son seis campos: montarlo con {@code StringBuilder} cuesta menos que
     * arrastrar una libreria de JSON hasta el {@code .exe} y hasta el
     * {@code .dmg}.
     *
     * <p><b>No lleva {@code party}.</b> Ese campo es el que enciende los
     * botones de "unirse a la partida" de Discord, y eso pediria abrir un
     * puerto y dejar entrar a desconocidos a tu juego. La cuenta de rivales va
     * escrita en el texto, que es lo unico que se queria.
     */
    String toJson(final String appId, final String largeText) {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("{\"cmd\":\"SET_ACTIVITY\",\"nonce\":\"")
          .append(java.util.UUID.randomUUID())
          .append("\",\"args\":{\"pid\":").append(ProcessHandle.current().pid())
          .append(",\"activity\":{");
        boolean first = true;
        first = field(sb, first, "details", details);
        first = field(sb, first, "state", state);
        if (startedAt > 0) {
            if (!first) {
                sb.append(',');
            }
            sb.append("\"timestamps\":{\"start\":").append(startedAt).append('}');
            first = false;
        }
        if (!first) {
            sb.append(',');
        }
        sb.append("\"assets\":{\"large_image\":\"").append(esc(DiscordRich.LOGO_KEY))
          .append("\",\"large_text\":\"").append(esc(largeText)).append("\"}");
        sb.append("}}}");
        return sb.toString();
    }

    private static boolean field(final StringBuilder sb, final boolean first,
                                 final String name, final String value) {
        if (value == null) {
            return first;
        }
        if (!first) {
            sb.append(',');
        }
        sb.append('"').append(name).append("\":\"").append(esc(value)).append('"');
        return false;
    }

    /**
     * Escapado de JSON.
     *
     * <p>Hace falta de verdad: por aqui pasan nombres de mazo que escribe el
     * jugador y nombres de carta de Magic, que vienen con comillas y barras
     * ({@code Ach! Hans, Run!}, {@code "Rumors of My Death . . ."}). Una
     * comilla sin escapar rompe el JSON y Discord descarta el mensaje entero,
     * asi que el sintoma seria "la presencia no cambia con ese mazo" — de los
     * que cuestan una tarde.
     */
    private static String esc(final String s) {
        final StringBuilder out = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return details + (state == null ? "" : " | " + state);
    }
}
