package forge.neo.platform;

/**
 * El User-Agent de lo que descargamos (imagenes de Scryfall, arte...).
 *
 * <p>Hasta el 21-10-2026 era {@code BuildInfo.getUserAgent()}; Forge lo quito
 * (#5633150f70, "Centralize the User-Agent") y ahora cada interfaz da el suyo
 * por {@code IGuiBase.getUserAgent()}: el escritorio dice "(gui-desktop)" y el
 * movil "(gui-mobile)". Nosotros, lo mismo que antes mas "(gui-neo)". Aparte
 * de {@link NeoGuiBase} porque nuestras descargas tambien corren sin la
 * interfaz registrada (comprobadores, Android), y Android lo usa por el jar.
 */
public final class NeoUserAgent {

    private NeoUserAgent() {
    }

    public static String get() {
        return "Forge/" + forge.util.BuildInfo.getVersionString() + " (gui-neo)";
    }
}
