package forge.neo.net;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.neo.NeoText;

/**
 * Los avisos que el servidor de Forge manda por el chat, en nuestro idioma y
 * con su significado.
 *
 * <p>El servidor los compone a mano y en ingles ({@code String.format} en
 * {@code FServerManager}, sin {@code Localizer}), asi que en una partida en
 * espanyol la sala decia <i>"Pepe is ready (1/2 players ready)"</i>. No se
 * pueden traducir en origen sin tocar el motor, pero si reconocer al llegar:
 * son frases fijas con el nombre dentro.
 *
 * <p>Y algunas no son solo texto: <i>"Pepe disconnected. Waiting 5:00 for
 * reconnect..."</i> es el UNICO aviso de que la partida se ha quedado en pausa
 * esperando a alguien. Por eso cada frase trae su {@link Kind}: la mesa pinta
 * un dialogo con {@code DISCONNECTED}, y no con el resto.
 *
 * <p>Lo que no se reconoce pasa tal cual: un cambio en Forge nunca puede
 * hacer desaparecer un aviso, como mucho lo deja en ingles. {@code lobbycheck}
 * comprueba que cada frase de esta tabla se sigue leyendo.
 */
public final class NetPhrases {

    /** Que significa el aviso, mas alla de su texto. */
    public enum Kind {
        /** Un aviso normal: se ensenya y ya. */
        PLAIN,
        /** Alguien se ha caido a mitad de partida y la partida le espera. */
        DISCONNECTED,
        /** Ha vuelto. */
        RECONNECTED,
        /** Ya no se le espera: juega la IA por el. */
        REPLACED_BY_AI,
        /**
         * La pista de los comandos del anfitrion. En la sala se ensenya, pero
         * en la mesa sobra: ahi los comandos son botones del dialogo.
         */
        HOST_HINT
    }

    /** Un aviso ya leido. {@code who} es el jugador del que habla, o null. */
    public record Phrase(Kind kind, String text, String who) {
    }

    private record Rule(Pattern pattern, String key, Kind kind, int whoGroup) {
    }

    private static final List<Rule> RULES = List.of(
            rule("^(.+) joined the lobby\\.$", "net.msg.joined", Kind.PLAIN, 1),
            rule("^(.+) left the lobby\\.$", "net.msg.left", Kind.PLAIN, 1),
            rule("^(.+) is ready \\((\\d+)/(\\d+) players ready\\)$", "net.msg.ready", Kind.PLAIN, 1),
            rule("^(.+) is not ready \\((\\d+)/(\\d+) players ready\\)$", "net.msg.notReady", Kind.PLAIN, 1),
            rule("^(.+) disconnected\\. Waiting (\\S+) for reconnect\\.\\.\\.$",
                    "net.msg.disconnected", Kind.DISCONNECTED, 1),
            rule("^(.+): (\\S+) remaining to reconnect\\.$", "net.msg.remaining", Kind.PLAIN, 1),
            rule("^(.+) has reconnected\\.$", "net.msg.reconnected", Kind.RECONNECTED, 1),
            rule("^Host forced AI takeover for (.+)\\.$", "net.msg.aiTakeover", Kind.REPLACED_BY_AI, 1),
            rule("^(.+) did not reconnect in time\\. AI has taken over\\.$",
                    "net.msg.aiTimeout", Kind.REPLACED_BY_AI, 1),
            rule("^Timeout disabled for (.+)\\. Waiting indefinitely for reconnect\\.$",
                    "net.msg.noTimeout", Kind.PLAIN, 1),
            rule("^\\(Host can use /skipreconnect.*$", "net.msg.hostHint", Kind.HOST_HINT, 0),
            rule("^No players are currently disconnected\\.$", "net.msg.noneDisconnected", Kind.PLAIN, 0),
            rule("^Multiple disconnected players\\. Specify a name: (\\S+) <name>$",
                    "net.msg.multiple", Kind.PLAIN, 0),
            rule("^No disconnected player named '(.+)'\\.$", "net.msg.noNamed", Kind.PLAIN, 1),
            rule("^(.+) changed their name to (.+)$", "net.msg.renamed", Kind.PLAIN, 1),
            rule("^(.+) timed out after (\\d+) seconds without a network response\\. Closing connection\\.$",
                    "net.msg.timedOut", Kind.PLAIN, 1),
            rule("^Warning: (.+) is using Forge version (\\S+) \\(host: (\\S+)\\)\\..*$",
                    "net.msg.forgeVersion", Kind.PLAIN, 1),
            rule("^Warning: Could not determine (.+)'s Forge version\\..*$",
                    "net.msg.forgeVersionUnknown", Kind.PLAIN, 1));

    private static Rule rule(final String regex, final String key, final Kind kind, final int who) {
        return new Rule(Pattern.compile(regex, Pattern.DOTALL), key, kind, who);
    }

    private NetPhrases() {
    }

    /**
     * Lee un mensaje del chat.
     *
     * @param source quien lo manda; los avisos del servidor no llevan
     *               (null o vacio). Lo que escribe una persona no se toca
     *               nunca, aunque se parezca a un aviso.
     */
    public static Phrase read(final String source, final String message) {
        final String msg = message == null ? "" : message;
        if (source != null && !source.isBlank()) {
            return new Phrase(Kind.PLAIN, source + ": " + msg, null);
        }
        final String trimmed = msg.trim();
        for (final Rule r : RULES) {
            final Matcher m = r.pattern().matcher(trimmed);
            if (!m.matches()) {
                continue;
            }
            final Object[] args = new Object[m.groupCount()];
            for (int i = 0; i < args.length; i++) {
                args[i] = m.group(i + 1);
            }
            final String who = r.whoGroup() > 0 ? m.group(r.whoGroup()) : null;
            return new Phrase(r.kind(), NeoText.get(r.key(), args), who);
        }
        return new Phrase(Kind.PLAIN, msg, null);
    }

    /** Las claves de texto que usa la tabla, para comprobar que existen. */
    public static List<String> keys() {
        return RULES.stream().map(Rule::key).toList();
    }
}
