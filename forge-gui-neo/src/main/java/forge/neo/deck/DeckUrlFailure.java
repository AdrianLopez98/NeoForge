package forge.neo.deck;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Por que ha fallado importar un mazo por enlace, dicho de forma que el
 * jugador sepa que hacer. {@code DeckUrlLoader} (de Forge) solo dice
 * "la solicitud a Moxfield fallo con HTTP 404", y eso no se entiende.
 *
 * <p>Informes de itch.io del 29-09-2026: un mazo de Moxfield que daba 404
 * (era privado: Moxfield da 404 tambien en su propia web) y uno de TappedOut
 * que no se bajaba nunca (TappedOut pone el reto de Cloudflare delante y
 * contesta 403 a cualquier programa, no solo a nosotros).
 *
 * <p>Clase pura, compartida con Android por el jar: nada de API que Android
 * no tenga en la 26. Cada interfaz traduce el {@link Kind} con sus textos.
 */
public final class DeckUrlFailure {

    public enum Kind {
        /** 404: no existe o es privado. */
        NOT_FOUND,
        /** 401/403 (o el reto de Cloudflare): la web no deja bajarlo a un programa. */
        BLOCKED,
        /** 429: demasiadas peticiones seguidas. */
        TOO_MANY,
        /** Cualquier otra cosa: se ensenya el mensaje de Forge tal cual. */
        OTHER
    }

    private static final Pattern STATUS = Pattern.compile("\\b(401|403|404|429)\\b");

    private DeckUrlFailure() {
    }

    /** Lo que ha pasado, a partir del mensaje de la excepcion de {@code DeckUrlLoader}. */
    public static Kind classify(final String message) {
        if (message == null) {
            return Kind.OTHER;
        }
        final Matcher m = STATUS.matcher(message);
        if (!m.find()) {
            return Kind.OTHER;
        }
        switch (m.group(1)) {
            case "404":
                return Kind.NOT_FOUND;
            case "429":
                return Kind.TOO_MANY;
            default:
                return Kind.BLOCKED;
        }
    }

    /** El nombre de la web del enlace, para el mensaje ("Moxfield", "TappedOut"...). */
    public static String site(final String url) {
        final String u = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
        if (u.contains("moxfield")) {
            return "Moxfield";
        }
        if (u.contains("archidekt")) {
            return "Archidekt";
        }
        if (u.contains("tappedout")) {
            return "TappedOut";
        }
        if (u.contains("mtggoldfish")) {
            return "MTGGoldfish";
        }
        return "?";
    }
}
