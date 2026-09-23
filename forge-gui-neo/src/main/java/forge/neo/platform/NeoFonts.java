package forge.neo.platform;

import java.io.InputStream;

import javafx.scene.Scene;
import javafx.scene.text.Font;

/**
 * La letra de repuesto, para cuando no hay Segoe UI.
 *
 * <p><b>Existe por Linux.</b> {@code neo.css} pide "Segoe UI", que es de
 * Windows. En la Steam Deck y en Linux el juego corre con Proton/Wine, que no
 * la trae, y JavaFX cae en lo primero que encuentra: texto borroso, apretado y
 * desigual. Reportado en el Discord varias veces ("en SteamOS el texto se ve
 * mucho peor que en Windows").
 *
 * <p>El remedio es llevar la letra dentro: <b>Inter</b> (licencia OFL, la
 * licencia va al lado en {@code fonts/Inter-OFL.txt}), que ya era la segunda
 * de la lista de {@code neo.css}. Solo se cargan cuatro cortes —normal,
 * negrita y sus cursivas— porque JavaFX, al buscar una letra, solo distingue
 * negrita de no negrita: un 600 sale normal y un 800 o 900 sale en negrita,
 * igual que con Segoe UI.
 *
 * <p><b>No puede cambiar Windows ni Mac</b>, y no por intencion sino por
 * construccion:
 * <ul>
 *   <li>Si el sistema tiene Segoe UI —cualquier Windows de verdad— no se hace
 *       nada: ni se cargan las letras ni se anyade la hoja.</li>
 *   <li>En Mac tampoco: alli se ve bien con la letra del sistema, se ha
 *       probado asi y no se toca.</li>
 * </ul>
 *
 * <p>Se mira si <em>falta la letra</em>, no si el sistema "es Linux": con
 * Proton el juego se cree en Windows ({@code os.name} dice Windows), y es
 * justo el caso que hay que arreglar.
 *
 * <p>Para probarlo en Windows: {@code -Dneo.font.fallback=true} fuerza el
 * repuesto aunque haya Segoe UI.
 */
public final class NeoFonts {

    private static final String[] FILES = {
        "Inter-Regular.ttf", "Inter-Bold.ttf", "Inter-Italic.ttf", "Inter-BoldItalic.ttf",
    };

    /** null = sin decidir todavia. */
    private static Boolean useFallback;

    private NeoFonts() {
    }

    /**
     * Hilo de JavaFX: pone la letra de repuesto en esta escena si hace falta.
     * Va DESPUES de anyadir {@code neo.css}, para que su {@code .root} gane.
     */
    public static void apply(final Scene scene) {
        if (scene == null || !needsFallback()) {
            return;
        }
        final var css = NeoFonts.class.getResource("/forge/neo/fonts/neo-fonts.css");
        if (css != null && !scene.getStylesheets().contains(css.toExternalForm())) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    private static synchronized boolean needsFallback() {
        if (useFallback == null) {
            useFallback = decide();
        }
        return useFallback;
    }

    private static boolean decide() {
        final boolean forced = Boolean.getBoolean("neo.font.fallback");
        if (!forced) {
            if (NeoOs.MAC) {
                return false;
            }
            try {
                if (Font.getFamilies().contains("Segoe UI")) {
                    return false;
                }
            } catch (final RuntimeException e) {
                // Si ni siquiera se puede preguntar, mejor no tocar nada.
                return false;
            }
        }
        int loaded = 0;
        for (final String f : FILES) {
            try (InputStream in = NeoFonts.class.getResourceAsStream("/forge/neo/fonts/" + f)) {
                if (in != null && Font.loadFont(in, 12) != null) {
                    loaded++;
                }
            } catch (final Exception e) {
                // Una que falle no impide las demas.
            }
        }
        System.out.println("[neo] Sin Segoe UI" + (forced ? " (forzado)" : "")
                + ": letra de repuesto Inter, " + loaded + " de " + FILES.length + " cortes cargados");
        // Si no se ha cargado ni la normal, la hoja no serviria de nada.
        return loaded > 0;
    }
}
