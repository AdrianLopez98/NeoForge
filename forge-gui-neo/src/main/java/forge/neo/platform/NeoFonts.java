package forge.neo.platform;

import java.io.File;
import java.io.InputStream;

import javafx.scene.Scene;
import javafx.scene.text.Font;

/**
 * La letra de repuesto, para cuando no hay Segoe UI, y el texto suavizado
 * bajo Wine.
 *
 * <p><b>Existe por Linux.</b> {@code neo.css} pide "Segoe UI", que es de
 * Windows. En la Steam Deck y en Linux el juego corre con Proton/Wine, que no
 * la trae, y JavaFX cae en lo primero que encuentra. Reportado en el Discord
 * varias veces ("en SteamOS el texto se ve mucho peor que en Windows").
 *
 * <p>La letra que va dentro es <b>Inter</b> (licencia OFL, la licencia va al
 * lado en {@code fonts/Inter-OFL.txt}), que ya era la segunda de la lista de
 * {@code neo.css}. Solo se cargan cuatro cortes —normal, negrita y sus
 * cursivas— porque JavaFX, al buscar una letra, solo distingue negrita de no
 * negrita: un 600 sale normal y un 800 o 900 sale en negrita, igual que con
 * Segoe UI.
 *
 * <h2>Bajo Wine, ademas, el texto por el camino LCD</h2>
 *
 * <p>La letra sola lo empeoro (23-09-2026, captura del Discord): el texto
 * salia en pixeles duros, sin suavizar. La causa no es la letra sino
 * <b>por donde la dibuja JavaFX</b>. En Windows, el texto gris normal se
 * rasteriza con Direct2D ({@code DWGlyph.getD2DMask}), y el Direct2D de Wine
 * no suaviza: cualquier letra sale dentada. La que ponia Wine por su cuenta
 * estaba pensada para verse sin suavizado; Inter no, y por eso se vio peor.
 *
 * <p><b>El texto LCD</b> ({@code neo-wine.css}), lo que se usa bajo Wine.
 * Va por {@code IDWriteGlyphRunAnalysis}, que Wine si suaviza, pero JavaFX
 * solo lo usa con color opaco, sin efecto y sin escala: medido en una captura
 * de pantalla de verdad, "IDIOMA" seguia por Direct2D. El jugador no noto
 * cambio, pero es lo que arranca.
 *
 * <p><b>Segundo intento, que colgo el juego: {@code prism.fontSizeLimit=1}.</b> Por encima de ese
 * tamanyo JavaFX no pide la imagen de la letra: coge su contorno y lo rellena
 * con su propio dibujante, que suaviza siempre y no pasa por Direct2D. Con el
 * limite a 1 punto eso vale para TODO el texto. Medido en Windows con una
 * partida automatica de 100 s: 58,5 fps sin el cambio y 58,7 con el, y el
 * peor frame igual. La propiedad la lee JavaFX UNA vez al arrancar, por eso
 * va en {@link #beforeJavaFx}, desde lo primero de {@code NeoMain.main}.
 *
 * <p><b>Pero bajo Proton de verdad (CachyOS, 23-09-2026) se quedo clavado en
 * la pantalla de carga</b>: el "Loading..." salia ya suave, el registro decia
 * "listo para jugar" y la ventana no volvia a pintarse. En Windows, con el
 * entorno de Proton simulado y con el pipeline por software, va a 60 fps: lo
 * que falla es algo de Wine al sacar el contorno de las letras, y desde aqui
 * no se puede ver. Asi que queda <b>apagado</b> salvo que se pida, y por
 * defecto bajo Wine se vuelve a lo ultimo que arrancaba: Inter + LCD.
 *
 * <p>Wine se reconoce por lo que deja: sus variables de entorno (Proton las
 * pasa) o {@code winecfg.exe} en {@code system32}, que no existe en un
 * Windows de verdad.
 *
 * <p><b>No puede cambiar Windows ni Mac</b>, por construccion: en un Windows
 * de verdad hay Segoe UI y no hay Wine, asi que no se toca nada; en Mac no se
 * mira nada.
 *
 * <p>Todo se fuerza o se apaga sin recompilar, con una linea
 * {@code java-options=...} en el {@code NeoForge.cfg} de {@code app}. Es como
 * se prueba con quien lo juega en Linux, porque aqui no hay forma de verlo:
 * <ul>
 *   <li>{@code -Dneo.font.fallback=true|false}: la letra Inter.</li>
 *   <li>{@code -Dneo.text.shapes=true|false}: las letras dibujadas por JavaFX
 *       (apagado si no se pide: colgo el juego bajo Proton).</li>
 *   <li>{@code -Dneo.text.lcd=true|false}: el texto por el camino LCD.</li>
 * </ul>
 * Sin ellas, decide solo.
 */
public final class NeoFonts {

    private static final String[] FILES = {
        "Inter-Regular.ttf", "Inter-Bold.ttf", "Inter-Italic.ttf", "Inter-BoldItalic.ttf",
    };

    private static final String[] WINE_ENV = {
        "WINEPREFIX", "WINELOADER", "WINEDLLPATH", "WINEDLLOVERRIDES", "STEAM_COMPAT_DATA_PATH",
    };

    /** null = sin decidir todavia. */
    private static Boolean useFallback;
    private static Boolean useLcd;

    private NeoFonts() {
    }

    /**
     * Hilo de JavaFX: pone la letra de repuesto y el texto LCD en esta escena
     * si hace falta. Va DESPUES de anyadir {@code neo.css}, para que gane.
     */
    public static void apply(final Scene scene) {
        if (scene == null) {
            return;
        }
        if (needsFallback()) {
            addSheet(scene, "neo-fonts.css");
        }
        if (needsLcd()) {
            addSheet(scene, "neo-wine.css");
        }
    }

    private static void addSheet(final Scene scene, final String name) {
        final var css = NeoFonts.class.getResource("/forge/neo/fonts/" + name);
        if (css != null && !scene.getStylesheets().contains(css.toExternalForm())) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    /** TRUE o FALSE si se ha forzado; null si decide solo. */
    private static Boolean forced(final String key) {
        final String v = System.getProperty(key);
        return v == null || v.isBlank() ? null : Boolean.valueOf(v.trim());
    }

    /** Lo que decidio {@link #beforeJavaFx}, para contarlo en el registro. */
    private static String shapesNote;

    /**
     * Antes de arrancar JavaFX: las letras como formas, solo si se piden. Si alguien
     * ya ha puesto {@code prism.fontSizeLimit} a mano, manda la suya.
     */
    public static void beforeJavaFx() {
        if (System.getProperty("prism.fontSizeLimit") != null) {
            return;
        }
        final Boolean f = forced("neo.text.shapes");
        // Solo si se pide: bajo Wine de verdad se quedo colgado (ver arriba).
        final boolean shapes = Boolean.TRUE.equals(f);
        if (shapes) {
            System.setProperty("prism.fontSizeLimit", "1");
            // El registro todavia no existe: se cuenta en el primer apply().
            shapesNote = "[neo] Letras dibujadas por JavaFX (forzado)";
        }
    }

    private static synchronized boolean needsLcd() {
        if (useLcd == null) {
            if (shapesNote != null) {
                System.out.println(shapesNote);
            }
            final Boolean f = forced("neo.text.lcd");
            useLcd = f != null ? f : (!NeoOs.MAC && isWine());
            if (useLcd) {
                System.out.println("[neo] Texto por el camino LCD" + (f != null ? " (forzado)" : " (Wine)"));
            }
        }
        return useLcd;
    }

    /** Wine o Proton. En un Windows de verdad no hay nada de esto. */
    static boolean isWine() {
        try {
            for (final String v : WINE_ENV) {
                final String e = System.getenv(v);
                if (e != null && !e.isEmpty()) {
                    return true;
                }
            }
            final String win = System.getenv("WINDIR");
            if (win != null) {
                final File sys = new File(win, "system32");
                return new File(sys, "winecfg.exe").isFile() || new File(sys, "wineboot.exe").isFile();
            }
        } catch (final RuntimeException e) {
            // Sin permiso para mirar: se da por Windows, que es no tocar nada.
        }
        return false;
    }

    private static synchronized boolean needsFallback() {
        if (useFallback == null) {
            useFallback = decide();
        }
        return useFallback;
    }

    private static boolean decide() {
        final Boolean f = forced("neo.font.fallback");
        if (Boolean.FALSE.equals(f)) {
            return false;
        }
        final boolean forced = Boolean.TRUE.equals(f);
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
        for (final String file : FILES) {
            try (InputStream in = NeoFonts.class.getResourceAsStream("/forge/neo/fonts/" + file)) {
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
