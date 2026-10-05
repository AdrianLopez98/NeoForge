package forge.neo.card;

import java.util.Locale;

/**
 * Fichas que Forge pide a Scryfall con un numero que alli no existe.
 *
 * <p>Reportado en itch.io el 05-10-2026: <i>"Is it normal for the ring
 * card/token from host of mordor deck doesn't have an art?"</i>. El Anillo (lo
 * crea {@code Player.createTheRing} con la clave {@code the_ring}) esta en el
 * fichero de la edicion LTR como numero {@code 13}, y en Scryfall es
 * {@code tltr/H13}: {@code tltr/13} da 404 y la carta sale sin arte, tambien en
 * el Forge de siempre.
 *
 * <p>El fichero de Forge no se toca (regla de oro): se corrige la DIRECCION
 * aqui, justo antes de pedirla, y el fichero de cache se queda con su nombre
 * ({@code tokens/LTR/13_the_ring.jpg}), que es el que Forge busca. El dia que
 * lo arreglen alli, esto deja de hacer falta sin romper nada.
 *
 * <p>Java puro y sin API que Android no tenga en la 26: lo usan los dos
 * descargadores, el del escritorio ({@code NeoImageFetcher}) y el de Android
 * ({@code AndroidImageFetcher}), por el jar.
 */
public final class TokenImageFixes {

    private TokenImageFixes() {
    }

    /** Trozo de la direccion que esta mal, y por que se cambia. */
    private static final String[][] FIXES = {
        {"/tltr/13/", "/tltr/H13/"},
    };

    /** La direccion corregida, si es una de las de arriba; si no, la misma. */
    public static String fix(final String url) {
        if (url == null) {
            return null;
        }
        final String lower = url.toLowerCase(Locale.ROOT);
        for (final String[] f : FIXES) {
            final int at = lower.indexOf(f[0]);
            if (at >= 0) {
                return url.substring(0, at) + f[1] + url.substring(at + f[0].length());
            }
        }
        return url;
    }
}
