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
        // El arabe (ARABIC, mas abajo; aqui con su valor porque un campo
        // estatico no se puede usar antes de declararlo).
        NAMES.put("ar-MA", "\u0627\u0644\u0639\u0631\u0628\u064a\u0629");
    }

    /**
     * <b>El arabe</b> (pedido en Discord el 02-10-2026), el primer idioma que
     * NO trae Forge: su fichero del motor lo ponemos nosotros (ver
     * {@link #PROVIDED}) y se escribe de derecha a izquierda.
     *
     * <p><b>Por que "ar-MA" y no "ar-SA".</b> El {@code Localizer} de Forge
     * formatea sus mensajes con el {@code Locale} de este codigo, y Java en
     * arabe de Arabia, Egipto o los Emiratos escribe las cifras arabigo-indicas
     * (\u0662\u0660 en vez de 20), mientras las cartas llevan 20 en el coste, la fuerza y
     * la vida. El de Marruecos (como Argelia y Tunez) usa las occidentales.
     * El jugador no ve el codigo: ve "\u0627\u0644\u0639\u0631\u0628\u064a\u0629".
     */
    public static final String ARABIC = "ar-MA";

    /**
     * Los idiomas cuyo fichero del MOTOR trae NeoForge, porque Forge no lo
     * tiene. Viven en la raiz de nuestro jar ({@code ar-MA.properties}) y el
     * {@code Localizer} los encuentra solo: los busca con un cargador sobre
     * {@code res/languages} cuyo padre es el classpath de la aplicacion, y el
     * padre se mira primero. Asi no se toca ni un fichero de Forge.
     */
    private static final String[] PROVIDED = {ARABIC};

    /**
     * Si el arabe sale en la lista de idiomas. <b>Si, como los demas</b>
     * (Ana, 02-10-2026: tenerlo aparte no tiene sentido). Mientras se hacia
     * fue al reves, detras de {@code -Dneo.arabic=true}; ahora queda solo como
     * valvula: {@code -Dneo.arabic=false} lo vuelve a esconder (y entonces la
     * lista es exactamente la de los diez de Forge) por si un dia hiciera falta
     * de urgencia.
     */
    public static boolean arabicEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("neo.arabic"));
    }

    /** Si ese idioma se escribe de derecha a izquierda. */
    public static boolean isRightToLeft(final String id) {
        return id != null && id.startsWith("ar-");
    }

    /**
     * Si el idioma elegido se escribe de derecha a izquierda.
     *
     * <p>Con el arabe escondido ({@code -Dneo.arabic=false}) ni se mira el
     * idioma: la respuesta es "no" sin leer ajustes ni carpetas.
     */
    public static boolean isRightToLeft() {
        if (!arabicEnabled()) {
            return false;
        }
        return isRightToLeft(current());
    }

    /**
     * <b>Solo Android</b>: deja los ficheros del motor de {@link #PROVIDED} en
     * {@code res/languages} del dispositivo, que es donde alli los busca el
     * {@code Localizer} (su cargador no ve el APK). Se reescribe solo si ha
     * cambiado, y nunca falla hacia fuera: sin el fichero, el motor cae al
     * ingles como con cualquier idioma que no tiene.
     *
     * <p><b>En el PC no se llama nunca</b>: alli {@code res/languages} es la
     * carpeta del repositorio de Forge, y un fichero nuevo ahi es justo lo que
     * prohibe la regla de oro. En el PC el motor ya lo encuentra en el jar.
     *
     * <p>Java de la 8 a proposito (lo usa Android 8, API 26).
     */
    public static void installProvided(final File langDir) {
        for (final String id : PROVIDED) {
            final String name = id + ".properties";
            try (java.io.InputStream in = NeoLanguage.class.getResourceAsStream("/" + name)) {
                if (in == null) {
                    continue;
                }
                final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                final byte[] chunk = new byte[16384];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    buf.write(chunk, 0, n);
                }
                final byte[] wanted = buf.toByteArray();
                final File target = new File(langDir, name);
                if (target.isFile() && target.length() == wanted.length
                        && java.util.Arrays.equals(readAll(target), wanted)) {
                    continue;
                }
                langDir.mkdirs();
                try (java.io.OutputStream out = new java.io.FileOutputStream(target)) {
                    out.write(wanted);
                }
            } catch (final java.io.IOException | RuntimeException e) {
                System.err.println("[neo] no se ha podido dejar " + name + " en " + langDir + ": " + e);
            }
        }
    }

    /**
     * <b>Solo Android</b>, y lo contrario de {@link #installProvided}: quita
     * de {@code res/languages} los ficheros que pusimos nosotros (por su nombre
     * exacto, nada mas). Se llama cuando el idioma elegido NO es uno de los
     * nuestros: la pantalla de ajustes del Forge de movil (la de dentro de la
     * Aventura) lista los idiomas mirando esa carpeta, y el arabe no puede
     * aparecerle a quien juega en otro idioma.
     */
    public static void removeProvided(final File langDir) {
        for (final String id : PROVIDED) {
            final File f = new File(langDir, id + ".properties");
            if (f.isFile() && !f.delete()) {
                System.err.println("[neo] no se ha podido quitar " + f);
            }
        }
    }

    private static byte[] readAll(final File f) throws java.io.IOException {
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            final byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        }
    }

    /** Si el fichero del motor de ese idioma lo trae NeoForge (y no Forge). */
    public static boolean isProvided(final String id) {
        for (final String p : PROVIDED) {
            if (p.equals(id)) {
                return true;
            }
        }
        return false;
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
        return available(new File(ForgeConstants.LANG_DIR));
    }

    /** Lo mismo mirando otra carpeta: para langcheck, que imita la de Android. */
    static List<Option> available(final File dir) {
        final List<Option> out = new ArrayList<>();
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
        // Los que pone NeoForge (el arabe), solo con su interruptor y si su
        // fichero del motor esta de verdad (en el jar, o copiado a
        // res/languages, que es lo que hace Android: alli el Localizer no ve el
        // classpath). Apagado, esta lista es exactamente la de antes, AUNQUE el
        // fichero este en la carpeta.
        for (final String id : PROVIDED) {
            final boolean there = found.remove(id)
                    || NeoLanguage.class.getResource("/" + id + ".properties") != null;
            if (there && arabicEnabled()) {
                found.add(id);
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
        // -Dneo.language=en-US: otro idioma SIN escribir el ajuste. Para grabar
        // (el trailer en ingles) sin cambiarle el idioma al jugador.
        final String forced = System.getProperty("neo.language");
        if (forced != null) {
            for (final Option o : available()) {
                if (o.getId().equals(forced)) {
                    return forced;
                }
            }
        }
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
            // El arabe trae su propio Lang (posesivos y ordinales): tiene que
            // estar puesto antes de que FModel llame a Lang.createInstance,
            // que es justo despues de este gancho. Otro idioma: no hace nada.
            ArabicLang.installIfArabic(wanted);
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
