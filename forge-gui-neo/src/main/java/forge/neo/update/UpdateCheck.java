package forge.neo.update;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import forge.neo.NeoText;
import forge.neo.NeoVersion;

/**
 * El aviso de version nueva, sin ventana ({@code run.cmd updatecheck}).
 *
 * <p>Lo peligroso de este adorno no es que falle, es <b>como</b> falla: un
 * aviso que no sale no lo nota nadie, y uno que sale siempre (la 4.10 "mas
 * vieja" que la 4.9, un error de itch.io leido como version) solo lo nota quien
 * ya se ha cansado de cerrarlo. Lo que se mira:
 *
 * <ul>
 *   <li><b>Que se compare por numeros</b>: 4.10 &gt; 4.9, 4.1 = 4.1.0, y lo
 *       que no se entiende nunca es "hay una nueva".</li>
 *   <li><b>Que la respuesta de itch.io se lea bien</b>, y que su
 *       {@code {"errors":[...]}} no sea una version.</li>
 *   <li><b>Que "Ahora no" calle esa version y no la siguiente.</b></li>
 *   <li><b>Que los canales se llamen como en itch.io</b>, con espacios.</li>
 *   <li><b>Que sin linea no pase nada</b>: una peticion que no puede salir
 *       devuelve {@code null} y no lanza.</li>
 *   <li><b>Los diez idiomas.</b></li>
 * </ul>
 *
 * <p>Y una parte opcional: si hay linea, pregunta a itch.io de verdad por los
 * tres canales del PC. Sin linea lo dice y sigue, como {@code netcheck}.
 * No toca el {@code neo.properties} del jugador.
 */
public final class UpdateCheck {

    private UpdateCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String[] LANGS = {
        "en-US", "es-ES", "de-DE", "fr-FR", "it-IT",
        "pt-BR", "ru-RU", "ja-JP", "ko-KR", "zh-CN", "ar-MA",
    };

    private static final String[] KEYS = {
        "menu.itch", "menu.itch.newer", "update.available", "update.open",
        "update.later", "settings.update", "settings.update.on",
    };

    public static void run() {
        passed = 0;
        failed = 0;
        comparesByNumbers();
        readsItchAnswers();
        offersOnlyWhatIsNew();
        channelsAsInItch();
        offlineIsSilent();
        asksOnlyOnce();
        androidCanUseIt();
        textsInEveryLanguage();
        liveIfOnline();
        System.out.println();
        System.out.printf(Locale.ROOT, "  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del aviso de version han fallado");
        }
    }

    private static void comparesByNumbers() {
        System.out.println("  Se compara por numeros");
        check(ItchVersion.compare("4.2", "4.1") > 0, "4.2 es mas nueva que 4.1");
        check(ItchVersion.compare("4.10", "4.9") > 0, "4.10 es mas nueva que 4.9 (no por letras)");
        check(ItchVersion.compare("5.0", "4.9") > 0, "5.0 es mas nueva que 4.9");
        check(ItchVersion.compare("1.0.3", "1.0.2") > 0, "1.0.3 es mas nueva que 1.0.2 (Android)");
        // Un solo numero para PC y Android desde el 30-09-2026: el PC paso de
        // la 4.7 a la 1.0.8. Lo viejo del PC (dos cifras, 2 o mas) va antes.
        check(ItchVersion.compare("1.0.8", "4.7") > 0, "1.0.8 (numero unico) es mas nueva que 4.7 (el esquema viejo del PC)");
        check(ItchVersion.compare("4.6", "1.0.8") < 0, "y 4.6 mas vieja que 1.0.8");
        check(ItchVersion.offerFor("1.0.8", "4.6", null) == null, "con la 1.0.8, la 4.6 de itch.io no se ofrece");
        check("1.0.9".equals(ItchVersion.offerFor("1.0.8", "1.0.9", null)), "y la 1.0.9 si");
        check(ItchVersion.compare("1.1", "1.0.9") > 0, "1.1 es mas nueva que 1.0.9 (dos cifras pero empieza por 1)");
        check(ItchVersion.compare("2.0.0", "1.9.9") > 0, "una 2.0.0 futura (tres cifras) es nueva");
        check(ItchVersion.compare("v4.2", "4.2") == 0, "la v de delante no cuenta");
        check(ItchVersion.compare("4.2 beta", "4.1") == 0, "lo que no se entiende no es mas nuevo");
        check(ItchVersion.compare("", "4.1") == 0, "ni una cadena vacia");
        System.out.println();
    }

    private static void readsItchAnswers() {
        System.out.println("  Las respuestas de itch.io");
        check("4.1".equals(ItchVersion.parse("{\"latest\":\"4.1\"}")), "{\"latest\":\"4.1\"} -> 4.1");
        check("1.0.2".equals(ItchVersion.parse("{ \"latest\" : \"1.0.2\" }")), "con espacios -> 1.0.2");
        check(ItchVersion.parse("{\"errors\":[\"invalid channel\"]}") == null, "un error no es una version");
        check(ItchVersion.parse("{\"latest\":\"fix del sabado\"}") == null, "un texto libre tampoco");
        check(ItchVersion.parse("<html>504 Gateway Timeout</html>") == null, "ni una pagina de error");
        check(ItchVersion.parse(null) == null, "ni nada");
        System.out.println();
    }

    private static void offersOnlyWhatIsNew() {
        System.out.println("  De que se avisa");
        check(ItchVersion.offerFor("4.1", "4.1", null) == null, "al dia: nada");
        check("4.2".equals(ItchVersion.offerFor("4.1", "4.2", null)), "hay 4.2 y tienes 4.1: se avisa");
        check(ItchVersion.offerFor("4.2", "4.1", null) == null, "itch.io por detras (4.1 subida despues): nada");
        check(ItchVersion.offerFor("4.1", "4.2", "4.2") == null, "\"Ahora no\" a la 4.2 la calla");
        check("4.3".equals(ItchVersion.offerFor("4.1", "4.3", "4.2")), "pero no calla la 4.3");
        check("4.2".equals(ItchVersion.offerFor("4.1", "4.2", "basura")),
                "una descartada ilegible no calla nada");
        check(ItchVersion.offerFor(null, "4.2", null) == null, "sin version propia: nada");
        check(ItchVersion.offerFor("4.1", null, null) == null, "sin respuesta (sin linea): nada");
        System.out.println();
    }

    private static void channelsAsInItch() {
        System.out.println("  Los canales, como en itch.io");
        check("win64".equals(ItchVersion.channelFor(false, "amd64")), "Windows -> win64");
        check("win64".equals(ItchVersion.channelFor(false, "x86_64")), "Linux -> win64 (baja el mismo zip)");
        check("mac apple silicon".equals(ItchVersion.channelFor(true, "aarch64")), "Mac M1 -> mac apple silicon");
        check("mac apple intel".equals(ItchVersion.channelFor(true, "x86_64")), "Mac Intel -> mac apple intel");
        final String url = ItchVersion.url("mac apple silicon");
        check(url.endsWith("channel_name=mac%20apple%20silicon"), "los espacios van como %20: " + url);
        check(url.contains("target=dokkodolabs/neo-forge"), "pregunta por el juego de verdad");
        check(ItchVersion.PAGE_URL.equals("https://dokkodolabs.itch.io/neo-forge"), "y lleva a su pagina");
        System.out.println();
    }

    /** El puerto 1 de la propia maquina no contesta nunca: es "sin linea" en un instante. */
    private static void offlineIsSilent() {
        System.out.println("  Sin linea no pasa nada");
        final long t0 = System.nanoTime();
        String got = "no ha vuelto";
        try {
            got = ItchVersion.fetch("http://127.0.0.1:1/api/1/x/wharf/latest", "NeoForge/check");
        } catch (final Throwable e) {
            check(false, "ha lanzado " + e);
        }
        final long ms = (System.nanoTime() - t0) / 1_000_000;
        check(got == null, "una peticion que no sale devuelve null");
        check(ms < 11_000, "y no se queda colgada (" + ms + " ms)");
        check(ItchVersion.fetch("esto no es una url", null) == null, "una direccion rota tampoco lanza");
        System.out.println();
    }

    /**
     * Lo que mas importa de todo esto (Ana, 28-09-2026): <b>ni bucles ni
     * consultas todo el rato</b>. Una por arranque, y ni volver al menu, ni
     * tocar el ajuste, ni leer lo que se sabe vuelven a preguntar. Se prueba
     * con {@code neo.update.fake}, que contesta sin salir a la red.
     */
    private static void asksOnlyOnce() {
        System.out.println("  Una consulta por arranque, nunca un sondeo");
        // NeoSettings pregunta rutas a ForgeConstants, y eso pide una plataforma
        // (ver DiscordCheck.prepareTexts). Solo lee: no se guarda nada.
        if (forge.gui.GuiBase.getInterface() == null) {
            forge.gui.GuiBase.setInterface(new forge.neo.platform.NeoGuiBase());
        }
        final String before = System.getProperty("neo.update.fake");
        System.setProperty("neo.update.fake", "9.9");
        try {
            for (int i = 0; i < 20; i++) {
                NeoUpdate.start();
                NeoUpdate.enabledChanged(true);
            }
            check(NeoUpdate.starts() <= 1,
                    "veinte arranques y veinte clics en el ajuste: " + NeoUpdate.starts() + " consulta(s)");
            for (int i = 0; i < 20; i++) {
                NeoUpdate.newer();
                NeoUpdate.offer();
                NeoUpdate.whenKnown(() -> { });
            }
            check(NeoUpdate.starts() <= 1, "leer lo que se sabe (volver al menu) no vuelve a preguntar");
        } finally {
            if (before == null) {
                System.clearProperty("neo.update.fake");
            } else {
                System.setProperty("neo.update.fake", before);
            }
        }
        System.out.println();
    }

    /**
     * {@link ItchVersion} viaja en el jar al APK, que arranca en la API 26. Lo
     * que Android solo trae desde la 33 ({@code readNBytes},
     * {@code URLEncoder.encode(String, Charset)}) compila aqui sin decir nada y
     * alli es un {@code NoSuchMethodError}: un {@code Error}, que se salta el
     * {@code catch (Exception)}. Se mira en el propio .class.
     */
    private static void androidCanUseIt() {
        System.out.println("  Android la puede usar (API 26)");
        String pool = null;
        try (InputStream in = ItchVersion.class.getResourceAsStream("ItchVersion.class")) {
            if (in != null) {
                pool = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            }
        } catch (final java.io.IOException e) {
            pool = null;
        }
        check(pool != null, "se lee su .class");
        if (pool == null) {
            return;
        }
        check(!pool.contains("readNBytes") && !pool.contains("readAllBytes"),
                "sin InputStream.readNBytes / readAllBytes");
        check(!pool.contains("(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;"),
                "sin URLEncoder.encode(String, Charset)");
        check(!pool.contains("javafx/"), "sin JavaFX");
        check(!pool.contains("forge/neo/NeoSettings"), "sin los ajustes del PC");
        System.out.println();
    }

    private static void textsInEveryLanguage() {
        System.out.println("  Los once idiomas (los diez de Forge y el arabe)");
        for (final String lang : LANGS) {
            final Properties p = read(lang);
            if (p == null) {
                check(false, lang + ": no se puede leer el fichero");
                continue;
            }
            String missing = null;
            for (final String k : KEYS) {
                final String v = p.getProperty(k);
                if (v == null || v.trim().isEmpty()) {
                    missing = k;
                }
            }
            if (missing == null) {
                for (final String k : new String[] {"menu.itch.newer", "update.available"}) {
                    if (!p.getProperty(k).contains("{0}")) {
                        missing = k + " (sin {0}: no diria que version)";
                    }
                }
            }
            check(missing == null, lang + ": los " + KEYS.length + " textos"
                    + (missing == null ? "" : " (falta " + missing + ")"));
        }
        System.out.println();
    }

    private static void liveIfOnline() {
        System.out.println("  itch.io de verdad (opcional)");
        final String mine = NeoVersion.neoVersion();
        boolean any = false;
        for (final String ch : new String[] {"win64", "mac apple silicon", "mac apple intel"}) {
            final String v = ItchVersion.fetch(ItchVersion.url(ch), "NeoForge/check");
            if (v == null) {
                continue;
            }
            any = true;
            final String offer = ItchVersion.offerFor(mine, v, null);
            System.out.println("    --   " + ch + ": itch.io tiene la " + v + ", esta es la "
                    + (mine == null ? "?" : mine)
                    + (offer == null ? " -> sin aviso" : " -> avisaria de la " + offer));
        }
        if (!any) {
            System.out.println("    --   sin linea (o itch.io no contesta): no se comprueba, y es justo lo");
            System.out.println("         que tiene que pasar en el juego: nada");
        }
    }

    private static Properties read(final String lang) {
        final Properties p = new Properties();
        try (InputStream in = NeoText.class.getResourceAsStream(
                "/forge/neo/lang/neo-" + lang + ".properties")) {
            if (in == null) {
                return null;
            }
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (final java.io.IOException e) {
            return null;
        }
        return p;
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("    OK   " + what);
        } else {
            failed++;
            System.out.println("    MAL  " + what);
        }
    }
}
