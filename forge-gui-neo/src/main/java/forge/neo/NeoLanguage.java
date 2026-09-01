package forge.neo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;

/**
 * En que idioma se juega.
 *
 * <p><b>Esto no hay que traducirlo: Forge ya viene traducido.</b> Trae diez
 * idiomas de interfaz en {@code res/languages} \u2014 castellano incluido \u2014 y ademas
 * los <b>nombres de las cartas</b> traducidos en ocho
 * ({@code cardnames-es-ES.txt}, siete megas de nombres). Lo unico que faltaba
 * era dejar elegir.
 *
 * <p>Quien lo aplica todo es el propio motor a partir de una sola preferencia,
 * {@code FPref.UI_LANGUAGE}: con ella {@code FModel.initialize} arranca el
 * {@code Localizer} (los mensajes), {@code Lang} (los plurales) y
 * {@code CardTranslation} (los nombres de carta).
 *
 * <p><b>El momento importa.</b> Los nombres de carta se precargan <i>antes</i>
 * de leer las cartas \u2014 el propio Forge lo comenta: <i>"Do this first so
 * PaperCards see the real preference"</i> \u2014 asi que no vale cambiarlo despues.
 * Por eso se aplica por el gancho {@code adjustPrefs} que {@code FModel}
 * ofrece justo para esto, y por eso <b>cambiar de idioma pide reiniciar</b>,
 * igual que en el Forge de siempre.
 *
 * <p><b>Y se repone.</b> Compartimos {@code %APPDATA%\\Forge\\} con la
 * instalacion normal del usuario, y el motor guarda sus preferencias por su
 * cuenta en varios sitios ({@code GamePlayerUtil}, por ejemplo). Si dejaramos
 * el idioma puesto ahi, un dia le cambiariamos la GUI vieja por detras. Se
 * aplica para arrancar, se repone en cuanto el motor ha leido lo que
 * necesitaba, y el valor de verdad vive en nuestro {@code neo.properties}.
 */
public final class NeoLanguage {

    private NeoLanguage() {
    }

    /** La clave en {@code neo.properties}. */
    public static final String SETTING = "language";

    /** El de fabrica: el mismo que el motor. */
    public static final String DEFAULT = "en-US";

    /**
     * Como se llama cada idioma <b>en su propio idioma</b>.
     *
     * <p>Es lo que hace usable un selector de idiomas: si estas mirando la
     * aplicacion en un idioma que no entiendes, "Aleman" no te sirve de nada y
     * "Deutsch" si.
     */
    private static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        // Escapes unicode a proposito: asi el fichero sigue siendo ASCII puro y
        // el nombre no depende de con que codificacion se lea el fuente.
        // El ingles el PRIMERO, porque es el de fabrica (DEFAULT): quien abre
        // esto por primera vez lo ve en ingles, y la casilla que ya esta
        // marcada tiene que ser la primera que mire. El castellano detras.
        NAMES.put("en-US", "English");
        NAMES.put("es-ES", "Espa\u00f1ol");
        NAMES.put("de-DE", "Deutsch");
        NAMES.put("fr-FR", "Fran\u00e7ais");
        NAMES.put("it-IT", "Italiano");
        NAMES.put("pt-BR", "Portugu\u00eas (Brasil)");
        NAMES.put("ru-RU", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439");
        NAMES.put("ja-JP", "\u65e5\u672c\u8a9e");
        NAMES.put("ko-KR", "\ud55c\uad6d\uc5b4");
        NAMES.put("zh-CN", "\u7b80\u4f53\u4e2d\u6587");
    }

    /** Un idioma disponible. */
    public static final class Option {
        private final String id;
        private final String label;
        private final boolean cardNames;

        Option(final String id, final String label, final boolean cardNames) {
            this.id = id;
            this.label = label;
            this.cardNames = cardNames;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        /** Si ademas trae los NOMBRES DE LAS CARTAS traducidos. */
        public boolean hasCardNames() {
            return cardNames;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Los idiomas que hay de verdad, mirando la carpeta.
     *
     * <p>No una lista escrita a mano: si una actualizacion de Forge anyade un
     * idioma, aparece solo. Se ordenan dejando primero los que conocemos por
     * nombre, y el resto detras con su codigo.
     */
    public static List<Option> available() {
        final List<Option> out = new ArrayList<>();
        final File dir = new File(ForgeConstants.LANG_DIR);
        final File[] files = dir.listFiles();
        if (files == null) {
            return List.of(new Option(DEFAULT, NAMES.get(DEFAULT), false));
        }

        final List<String> found = new ArrayList<>();
        for (final File f : files) {
            final String name = f.getName();
            if (name.endsWith(".properties")) {
                found.add(name.substring(0, name.length() - ".properties".length()));
            }
        }

        // Primero los que sabemos nombrar, en el orden de la tabla: el ingles
        // arriba, que es el de fabrica.
        for (final Map.Entry<String, String> e : NAMES.entrySet()) {
            if (found.remove(e.getKey())) {
                out.add(new Option(e.getKey(), e.getValue(), hasCardNames(e.getKey())));
            }
        }
        for (final String id : found) {
            out.add(new Option(id, id, hasCardNames(id)));
        }
        return out;
    }

    /** Si ese idioma trae ademas los nombres de las cartas. */
    private static boolean hasCardNames(final String id) {
        return new File(ForgeConstants.LANG_DIR, "cardnames-" + id + ".txt").isFile();
    }

    /**
     * El idioma con el que se abre la <b>primerisima</b> vez.
     *
     * <p>El de Windows, si lo tenemos; si no, ingles. Salio de darselo a
     * alguien: la primera pantalla que ve es el tutorial, y salia en ingles
     * aunque su ordenador entero estuviera en castellano. Preguntar esta bien
     * — y se pregunta, ver {@code LanguageScreen} — pero <b>la respuesta que
     * viene marcada tiene que ser la buena</b>, porque es la que casi nadie va
     * a cambiar.
     *
     * <p>Se mira el idioma a secas ("es") y no el pais ("es-ES") a proposito:
     * quien tiene Windows en espanyol de Mexico prefiere el juego en espanyol
     * antes que en ingles.
     */
    static String firstRunDefault() {
        final String lang = java.util.Locale.getDefault().getLanguage();
        if (lang == null || lang.isBlank()) {
            return DEFAULT;
        }
        String fallback = null;
        for (final Option o : available()) {
            if (o.getId().equalsIgnoreCase(lang)) {
                return o.getId();
            }
            if (fallback == null && o.getId().toLowerCase(java.util.Locale.ROOT)
                    .startsWith(lang.toLowerCase(java.util.Locale.ROOT) + "-")) {
                fallback = o.getId();
            }
        }
        return fallback == null ? DEFAULT : fallback;
    }

    /** El idioma elegido, o el de fabrica. */
    public static String current() {
        final String saved = NeoSettings.get(SETTING, null);
        if (saved == null) {
            // Todavia no se ha elegido nunca: manda el idioma del ordenador.
            return firstRunDefault();
        }
        for (final Option o : available()) {
            if (o.getId().equals(saved)) {
                return saved;
            }
        }
        // El guardado ya no existe (una actualizacion se lo llevo): no se
        // arranca en un idioma que no hay.
        return DEFAULT;
    }

    /** Como se llama el idioma elegido. */
    public static String currentLabel() {
        final String id = current();
        for (final Option o : available()) {
            if (o.getId().equals(id)) {
                return o.getLabel();
            }
        }
        return id;
    }

    /** Deja elegido otro idioma. Se nota al reiniciar. */
    public static void set(final String id) {
        NeoSettings.set(SETTING, id == null || id.isBlank() ? DEFAULT : id);
        NeoSettings.save();
    }

    // ---------------------------------------------------------------
    // El enganche con el motor
    // ---------------------------------------------------------------

    /** Lo que habia antes de que lo tocaramos, para poder reponerlo. */
    private static volatile String engineValueBefore;

    /**
     * El gancho que se le pasa a {@code FModel.initialize}.
     *
     * <p>Se aplica ANTES de que el motor lea nada, que es el unico momento en
     * el que sirve: los nombres de carta se precargan antes de leer las cartas.
     */
    public static java.util.function.Function<ForgePreferences, Void> hook() {
        return prefs -> {
            final String wanted = current();
            engineValueBefore = prefs.getPref(FPref.UI_LANGUAGE);
            if (!wanted.equals(engineValueBefore)) {
                prefs.setPref(FPref.UI_LANGUAGE, wanted);
            }
            return null;
        };
    }

    /**
     * Repone el idioma que tenia el usuario en SU Forge.
     *
     * <p>Se llama en cuanto {@code FModel.initialize} ha terminado. Para
     * entonces el {@code Localizer}, {@code Lang} y {@code CardTranslation} ya
     * tienen cargado lo suyo, asi que reponer la preferencia no deshace nada
     * \u2014 y evita que un {@code save()} del motor (los hay: cambiar de nombre de
     * jugador, un perfil de IA desconocido) le escriba nuestro idioma en el
     * fichero que comparte con la GUI vieja.
     */
    public static void restoreEngineValue() {
        final String before = engineValueBefore;
        if (before == null) {
            return;
        }
        try {
            if (!before.equals(forge.model.FModel.getPreferences().getPref(FPref.UI_LANGUAGE))) {
                forge.model.FModel.getPreferences().setPref(FPref.UI_LANGUAGE, before);
            }
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido reponer el idioma del motor: " + e);
        }
        engineValueBefore = null;
    }
}
