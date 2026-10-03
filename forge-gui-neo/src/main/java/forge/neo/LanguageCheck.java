package forge.neo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.localinstance.properties.ForgeConstants;
import forge.util.Localizer;

/**
 * Los idiomas, sin ventana ({@code run.cmd langcheck}).
 *
 * <p>Nace con el <b>arabe</b> (1.0.11), el primer idioma que no trae Forge y
 * el primero que se escribe de derecha a izquierda. La regla que puso Ana al
 * empezarlo: <i>"no rompas nada de lo que hay; si se rompe algo, que sea del
 * arabe"</i>. Asi que casi todo lo que mira esto es lo de SIEMPRE:
 *
 * <ul>
 *   <li><b>Los diez de Forge, intactos</b>: de fabrica la lista es la de
 *       siempre con el arabe al final (como uno mas desde el 02-10-2026), y
 *       escondiendolo ({@code -Dneo.arabic=false}) exactamente la de antes;
 *       con el interruptor del arabe
 *       apagado la lista es la de antes, en el mismo orden; y con el
 *       encendido, la misma lista con el arabe al final.</li>
 *   <li><b>Que el motor siga leyendo cada uno de SU fichero</b>: el arabe lo
 *       encuentra el {@code Localizer} en la raiz de nuestro jar, porque mira
 *       primero el classpath; esto comprueba que ese atajo no le quita el
 *       fichero a ningun otro.</li>
 *   <li><b>El arabe</b>: que cargue el nuestro, que escriba las cifras
 *       occidentales (20, no ٢٠ — por eso es "ar-MA"), y que su fichero del
 *       motor tenga las mismas claves y los mismos huecos que el ingles.</li>
 *   <li><b>Que forzarlo escondido no lo cuele</b>.</li>
 * </ul>
 *
 * <p>No toca el {@code neo.properties} del jugador: solo lo lee.
 */
public final class LanguageCheck {

    private LanguageCheck() {
    }

    private static int passed;
    private static int failed;

    /** Los diez de Forge, en el orden del selector. */
    private static final String[] TEN = {
        "en-US", "es-ES", "de-DE", "fr-FR", "it-IT",
        "pt-BR", "ru-RU", "ja-JP", "ko-KR", "zh-CN",
    };

    private static final Pattern HOLE = Pattern.compile("\\{(\\d+)[^}]*\\}");

    public static void run() {
        passed = 0;
        failed = 0;
        final String gateBefore = System.getProperty("neo.arabic");
        final String forcedBefore = System.getProperty("neo.language");
        try {
            theTenAsAlways();
            theTenStillReadTheirOwnFile();
            arabicBehindItsSwitch();
            androidCopyStaysBehindTheSwitch();
            arabicLoadsOurs();
            arabicFileMatchesEnglish();
            arabicLang();
        } finally {
            restore("neo.arabic", gateBefore);
            restore("neo.language", forcedBefore);
            NeoText.reload();
            // El motor, otra vez en el idioma con el que arranco esta prueba.
            Localizer.getInstance().initialize(NeoLanguage.current(), ForgeConstants.LANG_DIR);
        }
        System.out.println();
        System.out.printf(Locale.ROOT, "  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de idiomas han fallado");
        }
    }

    // ---------------------------------------------------------------

    private static void theTenAsAlways() {
        System.out.println("  Los diez de siempre");
        System.clearProperty("neo.arabic");
        final List<String> byDefault = ids();
        final List<String> withArabic = new ArrayList<>(List.of(TEN));
        withArabic.add(NeoLanguage.ARABIC);
        check(byDefault.equals(withArabic), "de fabrica: los diez de siempre, en su orden, y el arabe al final " + byDefault);
        System.setProperty("neo.arabic", "false");
        final List<String> off = ids();
        check(off.equals(List.of(TEN)), "escondido (-Dneo.arabic=false): los diez, en su orden y nada mas " + off);
        for (final String id : TEN) {
            check(!NeoLanguage.isRightToLeft(id), id + " se escribe de izquierda a derecha");
            check(!NeoLanguage.isProvided(id), id + " lo trae Forge, no nosotros");
        }
        System.setProperty("neo.arabic", "true");
        final List<String> on = ids();
        final List<String> expected = new ArrayList<>(List.of(TEN));
        expected.add(NeoLanguage.ARABIC);
        check(on.equals(expected), "-Dneo.arabic=true, lo mismo que de fabrica " + on);
    }

    private static void theTenStillReadTheirOwnFile() {
        System.out.println("  Cada idioma del motor lee SU fichero de res/languages");
        System.setProperty("neo.arabic", "true");
        for (final String id : TEN) {
            final Properties forge = read(new File(ForgeConstants.LANG_DIR, id + ".properties"));
            final String key = plainKey(forge);
            if (key == null) {
                check(false, id + ": no encuentro una clave sencilla para comparar");
                continue;
            }
            Localizer.getInstance().initialize(id, ForgeConstants.LANG_DIR);
            final String got = Localizer.getInstance().getMessage(key);
            check(forge.getProperty(key).equals(got),
                    id + ": \"" + key + "\" sale de su fichero de Forge (" + got + ")");
        }
    }

    private static void arabicBehindItsSwitch() {
        System.out.println("  El arabe, y su valvula para esconderlo");
        System.setProperty("neo.arabic", "false");
        System.setProperty("neo.language", NeoLanguage.ARABIC);
        check(!NeoLanguage.ARABIC.equals(NeoLanguage.current()),
                "escondido, forzarlo no lo cuela (sale " + NeoLanguage.current() + ")");
        System.setProperty("neo.arabic", "true");
        check(NeoLanguage.ARABIC.equals(NeoLanguage.current()), "encendido y forzado, si");
        check("العربية".equals(NeoLanguage.currentLabel()),
                "se llama en su idioma: " + NeoLanguage.currentLabel());
        check(NeoLanguage.isRightToLeft(), "se escribe de derecha a izquierda");
        check(NeoLanguage.isProvided(NeoLanguage.ARABIC), "su fichero del motor lo trae NeoForge");
        boolean names = true;
        for (final NeoLanguage.Option o : NeoLanguage.available()) {
            if (o.getId().equals(NeoLanguage.ARABIC)) {
                names = o.hasCardNames();
            }
        }
        check(!names, "las cartas, en ingles: no hay nombres de carta en arabe");
        System.clearProperty("neo.language");
    }

    /**
     * Android: alli el fichero del motor se COPIA a res/languages (su
     * Localizer no ve el jar). Una carpeta asi no puede colar el arabe con el
     * arabe escondido, y la copia se hace una vez, no en cada arranque.
     */
    private static void androidCopyStaysBehindTheSwitch() {
        System.out.println("  Android: el fichero copiado a res/languages");
        File dir = null;
        try {
            dir = Files.createTempDirectory("neo-langcheck").toFile();
            for (final String id : TEN) {
                Files.write(new File(dir, id + ".properties").toPath(), new byte[0]);
            }
            NeoLanguage.installProvided(dir);
            final File copied = new File(dir, NeoLanguage.ARABIC + ".properties");
            final Properties back = read(copied);
            check(copied.isFile() && back.size() == resource("/" + NeoLanguage.ARABIC + ".properties").size(),
                    "installProvided lo deja entero en la carpeta (" + back.size() + " claves)");
            final long when = copied.lastModified();
            copied.setLastModified(when - 60_000);
            NeoLanguage.installProvided(dir);
            check(copied.lastModified() == when - 60_000, "y si ya esta igual, no lo vuelve a escribir");
            System.setProperty("neo.arabic", "false");
            final List<String> off = new ArrayList<>();
            for (final NeoLanguage.Option o : NeoLanguage.available(dir)) {
                off.add(o.getId());
            }
            check(off.equals(List.of(TEN)), "con el fichero ahi y el arabe escondido: los diez " + off);
            System.setProperty("neo.arabic", "true");
            final List<String> on = new ArrayList<>();
            for (final NeoLanguage.Option o : NeoLanguage.available(dir)) {
                on.add(o.getId());
            }
            check(on.size() == TEN.length + 1 && on.get(TEN.length).equals(NeoLanguage.ARABIC),
                    "encendido: los diez y el arabe al final, una sola vez " + on);
            // Con otro idioma elegido, Android lo QUITA de la carpeta (la lista
            // de idiomas del Forge de movil la mira): solo el suyo, por nombre.
            NeoLanguage.removeProvided(dir);
            final String[] left = dir.list();
            check(!copied.exists() && left != null && left.length == TEN.length,
                    "removeProvided quita el arabe y deja los diez ficheros de Forge ("
                            + (left == null ? 0 : left.length) + ")");
        } catch (final IOException e) {
            check(false, "no se ha podido montar la carpeta de prueba: " + e);
        } finally {
            if (dir != null) {
                final File[] left = dir.listFiles();
                if (left != null) {
                    for (final File f : left) {
                        f.delete();
                    }
                }
                dir.delete();
            }
        }
    }

    private static void arabicLoadsOurs() {
        System.out.println("  El motor en arabe lee el fichero de NeoForge");
        final Properties ours = resource("/" + NeoLanguage.ARABIC + ".properties");
        check(!ours.isEmpty(), "el fichero esta en la raiz del jar (" + ours.size() + " claves)");
        Localizer.getInstance().initialize(NeoLanguage.ARABIC, ForgeConstants.LANG_DIR);
        // Una clave cuyo arabe NO sea el ingles: si el motor no encontrara el
        // nuestro caeria al ingles en silencio, y con una clave sin traducir
        // eso pasaria por bueno.
        final Properties en = read(new File(ForgeConstants.LANG_DIR, "en-US.properties"));
        String key = null;
        for (final String k : new TreeSet<>(ours.stringPropertyNames())) {
            final String v = ours.getProperty(k);
            if (k.startsWith("lbl") && v.indexOf('{') < 0 && v.indexOf('\'') < 0
                    && !v.equals(en.getProperty(k))) {
                key = k;
                break;
            }
        }
        if (key == null) {
            System.out.println("    [--] todavia no hay ninguna clave traducida para distinguirlo del ingles");
        } else {
            final String got = Localizer.getInstance().getMessage(key);
            check(ours.getProperty(key).equals(got) && !got.equals(en.getProperty(key)),
                    "\"" + key + "\" sale del nuestro y no del ingles (" + got + ")");
        }
        // Las cifras: el motor formatea con el Locale del codigo.
        final String twenty = new MessageFormat("{0}", new Locale("ar", "MA")).format(new Object[]{20});
        check("20".equals(twenty), "ar-MA escribe 20 y no ٢٠ (" + twenty + ")");
        final String withNumber = numericKey(ours);
        if (withNumber != null) {
            final String msg = Localizer.getInstance().getMessage(withNumber, 20, 20, 20, 20);
            check(msg.contains("20") && msg.chars().noneMatch(c -> c >= 0x0660 && c <= 0x0669),
                    "y un mensaje del motor con un numero tambien: " + msg);
        }
    }

    private static void arabicFileMatchesEnglish() {
        System.out.println("  El fichero del motor en arabe cuadra con el ingles");
        final Properties en = read(new File(ForgeConstants.LANG_DIR, "en-US.properties"));
        final Properties ar = resource("/" + NeoLanguage.ARABIC + ".properties");
        final TreeSet<String> missing = new TreeSet<>(en.stringPropertyNames());
        missing.removeAll(ar.stringPropertyNames());
        final TreeSet<String> extra = new TreeSet<>(ar.stringPropertyNames());
        extra.removeAll(en.stringPropertyNames());
        check(missing.isEmpty(), "no le falta ninguna clave" + sample(missing));
        check(extra.isEmpty(), "no le sobra ninguna" + sample(extra));
        final List<String> holes = new ArrayList<>();
        final List<String> broken = new ArrayList<>();
        for (final String k : en.stringPropertyNames()) {
            final String a = ar.getProperty(k);
            if (a == null) {
                continue;
            }
            if (!holesOf(en.getProperty(k)).equals(holesOf(a))) {
                holes.add(k);
            }
            if (parses(en.getProperty(k)) && !parses(a)) {
                broken.add(k);
            }
        }
        check(holes.isEmpty(), "los mismos huecos {0}, {1}... que el ingles" + sample(holes));
        check(broken.isEmpty(), "ningun mensaje que el motor no sepa leer" + sample(broken));
    }

    /** Los posesivos y ordinales que el motor monta solo (ArabicLang). */
    private static void arabicLang() {
        System.out.println("  Lo que el motor monta solo: posesivos y ordinales");
        ArabicLang.installIfArabic("es-ES");
        forge.util.Lang.createInstance("es-ES");
        check(!(forge.util.Lang.getInstance() instanceof ArabicLang),
                "con el castellano, el Lang de Forge de siempre (" + forge.util.Lang.getInstance().getClass().getSimpleName() + ")");
        ArabicLang.installIfArabic(NeoLanguage.ARABIC);
        forge.util.Lang.createInstance(NeoLanguage.ARABIC);
        final forge.util.Lang lang = forge.util.Lang.getInstance();
        check(lang instanceof ArabicLang, "con el arabe, el nuestro (" + lang.getClass().getSimpleName() + ")");
        Localizer.getInstance().initialize(NeoLanguage.ARABIC, ForgeConstants.LANG_DIR);
        final String you = Localizer.getInstance().getMessage("lblYou");
        final String prompt = forge.util.TextUtil.fastReplace(
                Localizer.getInstance().getMessage("lblSelectCardFromPlayerZone", "{player's}",
                        Localizer.getInstance().getMessage("lblGraveyard")),
                "{player's}", lang.getPossesive(you));
        check(prompt.contains("لديك") && !prompt.contains("your") && !prompt.contains("'s"),
                "\"elige una carta de tu cementerio\": " + prompt);
        check(lang.getPossesive("Ana").contains("Ana") && !lang.getPossesive("Ana").contains("'s"),
                "el de otro jugador: " + lang.getPossesive("Ana"));
        check("الأول".equals(lang.getOrdinal(1)), "el primero: " + lang.getOrdinal(1));
        final String spells = "الأفضل: 5 " + "تعويذة" + "s (1/10/26)";
        check(!ArabicLang.tidy(spells).contains("ةs") && ArabicLang.tidy(spells).contains("(1/10/26)"),
                "la \"s\" inglesa pegada a una palabra arabe se va: " + ArabicLang.tidy(spells));
        check("Llanowar Elves".equals(ArabicLang.tidy("Llanowar Elves")), "un nombre de carta en ingles se queda igual");
        forge.util.Lang.createInstance(NeoLanguage.current());
    }

    // ---------------------------------------------------------------

    private static List<String> ids() {
        final List<String> out = new ArrayList<>();
        for (final NeoLanguage.Option o : NeoLanguage.available()) {
            out.add(o.getId());
        }
        return out;
    }

    /** Una clave cuyo texto sale igual por MessageFormat: sin huecos ni comillas. */
    private static String plainKey(final Properties p) {
        for (final String k : new TreeSet<>(p.stringPropertyNames())) {
            final String v = p.getProperty(k);
            if (k.startsWith("lbl") && v.length() > 3 && v.indexOf('{') < 0 && v.indexOf('\'') < 0) {
                return k;
            }
        }
        return null;
    }

    /** Una clave con un hueco {0} y sin comillas, para ver las cifras. */
    private static String numericKey(final Properties p) {
        for (final String k : new TreeSet<>(p.stringPropertyNames())) {
            final String v = p.getProperty(k);
            if (v.contains("{0}") && v.indexOf('\'') < 0 && !v.contains("choice")) {
                return k;
            }
        }
        return null;
    }

    private static TreeSet<String> holesOf(final String v) {
        final TreeSet<String> out = new TreeSet<>();
        final Matcher m = HOLE.matcher(v);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static boolean parses(final String v) {
        try {
            new MessageFormat(v, Locale.ENGLISH);
            return true;
        } catch (final IllegalArgumentException e) {
            return false;
        }
    }

    private static String sample(final java.util.Collection<String> keys) {
        if (keys.isEmpty()) {
            return "";
        }
        final List<String> first = new ArrayList<>(keys).subList(0, Math.min(8, keys.size()));
        return " (" + keys.size() + ": " + String.join(", ", first) + (keys.size() > 8 ? "..." : "") + ")";
    }

    private static Properties read(final File f) {
        final Properties p = new Properties();
        try (Reader r = new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (final IOException e) {
            System.out.println("    no se ha podido leer " + f + ": " + e);
        }
        return p;
    }

    private static Properties resource(final String path) {
        final Properties p = new Properties();
        try (InputStream in = LanguageCheck.class.getResourceAsStream(path)) {
            if (in != null) {
                try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    p.load(r);
                }
            }
        } catch (final IOException e) {
            System.out.println("    no se ha podido leer " + path + ": " + e);
        }
        return p;
    }

    private static void restore(final String prop, final String value) {
        if (value == null) {
            System.clearProperty(prop);
        } else {
            System.setProperty(prop, value);
        }
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("    [ok] " + what);
        } else {
            failed++;
            System.out.println("    [MAL] " + what);
        }
    }
}
