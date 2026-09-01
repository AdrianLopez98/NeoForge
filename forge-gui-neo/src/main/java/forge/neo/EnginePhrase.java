package forge.neo;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import forge.localinstance.properties.ForgeConstants;

/**
 * Reconocer una frase del motor, venga en el idioma que venga.
 *
 * <p><b>Por que hace falta.</b> Varias cosas de esta interfaz se disparan al
 * <i>reconocer</i> un texto que compone Forge: que el prompt es el rutinario de
 * prioridad, que te estan preguntando si adivinar va arriba o abajo, que el
 * boton OK se llama ahora "Auto" porque hay un pago de mana en curso. Eso se
 * hacia reconstruyendo la frase con <b>nuestro</b> {@code Localizer}, lo cual
 * vale mientras quien la escribe y quien la lee sean el mismo proceso.
 *
 * <p><b>En una partida en red no lo son.</b> El motor corre en el anfitrion y
 * la frase llega por el cable ya traducida <b>al idioma del anfitrion</b>. Si
 * el invitado juega en otro idioma, ninguna de esas comparaciones acierta y las
 * funciones se apagan <b>en silencio</b>: adivinar 1 vuelve a ser invisible, el
 * pago de mana deja de detectarse, el prompt no se reescribe. Nada falla a
 * gritos; simplemente deja de pasar.
 *
 * <p><b>Como se resuelve.</b> Los ficheros de idioma de Forge estan en disco
 * ({@code res/languages/*.properties}) y traen la misma clave en los diez
 * idiomas. Se comparan los <b>trozos literales</b> de la plantilla, no la
 * frase formateada:
 *
 * <pre>
 *   lblPutCardOnTopOrBottomLibrary = ¿Poner {0} en la parte superior o en el fondo de tu biblioteca?
 *                                    └────┘      └──────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Comparar por trozos tiene dos ventajas sobre formatear y comparar entero:
 * no hay que reproducir el {@code MessageFormat} de Forge (que ademas hace
 * malabares de codificacion), y el argumento — casi siempre <b>el nombre de la
 * carta</b>, que cada lado traduce por su cuenta — cae en el hueco y deja de
 * importar.
 *
 * <p>Los trozos de menos de cuatro caracteres se ignoran: no afirman nada y
 * podrian dar un positivo falso. Si de una plantilla no queda ningun trozo
 * util, ese idioma no puede afirmar nada y se salta — <b>nunca</b> se da por
 * reconocido algo que no lo esta.
 */
public final class EnginePhrase {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\d+\\}");

    /** Un trozo mas corto que esto no distingue nada. */
    private static final int MIN_FRAGMENT = 4;

    /** Fichero de idioma -> sus claves. Se lee una vez. */
    private static final Map<String, Properties> BUNDLES = new HashMap<>();

    /** Clave -> su texto en cada idioma. */
    private static final Map<String, List<String>> TEMPLATES = new HashMap<>();

    private EnginePhrase() {
    }

    /**
     * ¿El texto del motor contiene esa frase, en cualquier idioma?
     *
     * @param key clave del {@code Localizer} de Forge, p.ej. {@code lblAuto}
     */
    public static boolean contains(final String message, final String key) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        for (final String template : templates(key)) {
            if (matches(message, template)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿El texto es exactamente esa frase, en cualquier idioma?
     *
     * <p>Para etiquetas de boton, donde "contiene" seria demasiado flojo.
     */
    public static boolean equalsAny(final String text, final String key) {
        if (text == null) {
            return false;
        }
        final String t = text.trim();
        for (final String template : templates(key)) {
            if (t.equals(unescape(template).trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * ¿El texto empieza por esa frase seguida de {@code sufijo}?
     *
     * <p>Para el prompt rutinario, que es {@code "Prioridad: ..."} y hay que
     * distinguirlo por el principio y no por el contenido.
     */
    public static boolean startsWith(final String text, final String key, final String suffix) {
        if (text == null) {
            return false;
        }
        for (final String template : templates(key)) {
            final String head = unescape(template).trim() + (suffix == null ? "" : suffix);
            if (!head.isBlank() && text.startsWith(head)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------

    /** ¿Estan en el mensaje todos los trozos literales de esta plantilla? */
    private static boolean matches(final String message, final String template) {
        boolean anyUseful = false;
        for (final String raw : PLACEHOLDER.split(template)) {
            final String fragment = unescape(raw).trim();
            if (fragment.length() < MIN_FRAGMENT) {
                continue;
            }
            if (!message.contains(fragment)) {
                return false;
            }
            anyUseful = true;
        }
        return anyUseful;
    }

    /**
     * {@code MessageFormat} usa la comilla simple para escapar, y una comilla
     * de verdad se escribe doblada. En la plantilla cruda se ve {@code ''} donde
     * el texto final lleva {@code '}.
     */
    private static String unescape(final String s) {
        return s.replace("''", "'");
    }

    /** El texto de esa clave en todos los idiomas que trae Forge. */
    private static synchronized List<String> templates(final String key) {
        final List<String> cached = TEMPLATES.get(key);
        if (cached != null) {
            return cached;
        }
        final List<String> out = new ArrayList<>();
        for (final Properties p : bundles()) {
            final String v = p.getProperty(key);
            if (v != null && !v.isBlank() && !out.contains(v)) {
                out.add(v);
            }
        }
        // Si no hay ni un idioma con esa clave, se cae al Localizer de siempre:
        // peor es no reconocer nada.
        if (out.isEmpty()) {
            final String own = forge.util.Localizer.getInstance().getMessage(key);
            if (own != null && !own.startsWith("INVALID PROPERTY")) {
                out.add(own);
            }
        }
        final List<String> frozen = Collections.unmodifiableList(out);
        TEMPLATES.put(key, frozen);
        return frozen;
    }

    /** Los ficheros de idioma, leidos una sola vez. */
    private static synchronized List<Properties> bundles() {
        if (!BUNDLES.isEmpty()) {
            return new ArrayList<>(BUNDLES.values());
        }
        final File dir = new File(ForgeConstants.LANG_DIR);
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                final String name = f.getName();
                if (!name.endsWith(".properties")) {
                    continue;
                }
                final Properties p = new Properties();
                try (Reader r = new InputStreamReader(
                        new FileInputStream(f), StandardCharsets.UTF_8)) {
                    p.load(r);
                    BUNDLES.put(name, p);
                } catch (final Exception e) {
                    System.out.println("[neo] no se ha podido leer " + name + ": " + e);
                }
            }
        }
        if (BUNDLES.isEmpty()) {
            System.out.println("[neo] aviso: no hay ficheros de idioma en " + ForgeConstants.LANG_DIR);
        }
        return new ArrayList<>(BUNDLES.values());
    }

    /** Cuantos idiomas se han podido leer. Lo usa el comprobador. */
    public static int languageCount() {
        bundles();
        return BUNDLES.size();
    }

    /** Los textos conocidos de una clave. Lo usa el comprobador. */
    public static List<String> known(final String key) {
        return templates(key);
    }
}
