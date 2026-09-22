package forge.neo.discord;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

import forge.neo.NeoText;

/**
 * La presencia de Discord, sin Discord ({@code run.cmd discordcheck}).
 *
 * <p>Este adorno tiene una pega que lo hace peligroso de otra manera: <b>cuando
 * se rompe, no se ve desde aqui</b>. Si el JSON sale mal, Discord no contesta
 * con un error — descarta el mensaje y deja puesto el anterior. O sea que el
 * sintoma es "la presencia se quedo en el menu", que nadie relaciona con nada y
 * que solo nota quien te esta mirando el perfil. Justo lo que un comprobador
 * tiene que cazar.
 *
 * <p>Lo que se mira, y por que cada cosa:
 *
 * <ul>
 *   <li><b>Que el JSON aguante los nombres de Magic.</b> Por aqui pasan
 *       {@code Ach! Hans, Run!} y {@code "Rumors of My Death . . ."}, con
 *       comillas dentro del propio nombre de la carta. Una comilla sin escapar
 *       rompe el mensaje entero. Se genera y se vuelve a leer para comprobar
 *       que llega igual.</li>
 *   <li><b>Que nada se pase de 128 caracteres</b>, en ninguno de los diez
 *       idiomas. Pasarse no recorta: Discord tira el mensaje.</li>
 *   <li><b>Que un dialogo encima de la mesa NO cuente como cambio de
 *       pantalla.</b> Es el fallo que se evito a proposito: abrir Ajustes en
 *       mitad de un turno diria "En el menu".</li>
 *   <li><b>Que los textos esten en los diez idiomas.</b> Si falta uno se
 *       ensenya la clave — y aqui la clave se le ensenya a los amigos del
 *       jugador, no a el.</li>
 *   <li><b>Que apagar borre de verdad</b>, que es lo unico que no puede
 *       fallar de todo esto.</li>
 * </ul>
 *
 * <p>No abre ninguna tuberia ni necesita Discord instalado: todo lo que se
 * comprueba son cadenas.
 */
public final class DiscordCheck {

    private DiscordCheck() {
    }

    private static int passed;
    private static int failed;

    private static final String[] LANGS = {
        "en-US", "es-ES", "de-DE", "fr-FR", "it-IT",
        "pt-BR", "ru-RU", "ja-JP", "ko-KR", "zh-CN",
    };

    /** Las claves que tiene que haber en los diez ficheros. */
    private static final String[] KEYS = {
        "settings.discord", "settings.discord.on",
        "discord.menu", "discord.deck", "discord.draft", "discord.sealed",
        "discord.quest", "discord.ascent", "discord.tournament", "discord.online",
        "discord.tutorial", "discord.puzzle", "discord.playing",
        "discord.game.vs", "discord.game.vs1", "discord.game.watch",
        "discord.game.net", "discord.turn",
    };

    public static void run() {
        passed = 0;
        failed = 0;
        prepareTexts();
        jsonSurvivesCardNames();
        nothingOverflows();
        dialogsAreNotAScreenChange();
        screensAreRecognised();
        turningItOffClears();
        textsInEveryLanguage();
        liveIfDiscordIsOpen();
        System.out.println();
        System.out.printf(Locale.ROOT, "  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de Discord han fallado");
        }
    }

    /**
     * Lo minimo para que {@link NeoText} conteste, sin cargar el motor.
     *
     * <p>Esta prueba corre <b>antes</b> de que {@code NeoMain} registre la
     * plataforma, que es lo que la hace costar dos segundos y no quince: las
     * cartas no pintan nada aqui. Pero {@code NeoText} pregunta el idioma, y
     * para eso acaba tocando {@code ForgeConstants}, cuyo bloque estatico pide
     * {@code GuiBase.getInterface().getAssetsDir()} y revienta con un
     * {@code NullPointerException} envuelto en {@code ExceptionInInitializerError}
     * si no hay ninguna. Con registrar la nuestra basta; no arranca nada.
     *
     * <p>Y el idioma se fija a ingles para que la prueba diga lo mismo en el
     * ordenador de cualquiera: si leyera el del jugador, las frases del final
     * saldrian distintas segun quien la corra.
     */
    private static void prepareTexts() {
        if (forge.gui.GuiBase.getInterface() == null) {
            forge.gui.GuiBase.setInterface(new forge.neo.platform.NeoGuiBase());
        }
        System.setProperty("neo.language", "en-US");
        NeoText.reload();
    }

    // ------------------------------------------------------------------

    /**
     * El JSON, con lo peor que le puede llegar.
     *
     * <p>Los tres primeros son nombres de carta reales: existen en
     * {@code cardsfolder} y pueden acabar en el renglon de estado el dia que se
     * ensenye el comandante. Los otros dos son lo que rompe un JSON escrito a
     * mano.
     */
    private static void jsonSurvivesCardNames() {
        System.out.println("  El JSON aguanta");
        final String[] nasty = {
            "Ach! Hans, Run!",
            "\"Rumors of My Death . . .\"",
            "Look at Me, I'm the DCI",
            "barra \\ invertida",
            "salto\nde linea",
            "tabulador\ty comillas \"dobles\"",
        };
        for (final String s : nasty) {
            final String json = DiscordRich.payload(new DiscordActivity(s, "Turno 3", 1_700_000_000L));
            final String back = Json.stringAt(json, "details");
            check(s.equals(back),
                    "vuelve igual: " + s.replace("\n", "\\n").replace("\t", "\\t"));
        }
        // Y que la trama entera sea JSON valido, no solo ese campo.
        final String json = DiscordRich.payload(
                DiscordActivity.now("Commander \u00b7 contra 3 rivales", "Turno 12"));
        check(Json.wellFormed(json), "la trama entera es JSON valido");
        check(json.contains("\"large_image\":\"" + DiscordRich.LOGO_KEY + "\""),
                "lleva la imagen '" + DiscordRich.LOGO_KEY + "' (la clave del portal)");
        check(json.contains("\"cmd\":\"SET_ACTIVITY\""), "es un SET_ACTIVITY");
        System.out.println();
    }

    /**
     * El limite de 128. Se prueba con el idioma mas largo de los que tenemos y
     * con un formato inventado absurdamente largo, que es lo que podria llegar
     * de un {@code GameType} nuevo de Forge.
     */
    private static void nothingOverflows() {
        System.out.println("  Nada se pasa de 128");
        final String huge = "X".repeat(400);
        final DiscordActivity a = DiscordActivity.now(huge, huge);
        check(a.details.length() <= 128, "el renglon de arriba se recorta (" + a.details.length() + ")");
        check(a.state.length() <= 128, "el renglon de abajo se recorta (" + a.state.length() + ")");
        check(a.details.endsWith("\u2026"), "y se ve que esta recortado");

        // Y los textos de verdad, en los diez idiomas, con el formato mas
        // largo que trae Forge.
        final String longestFormat = "Commander Gauntlet";
        int worst = 0;
        String worstLang = "";
        for (final String lang : LANGS) {
            final Properties p = read(lang);
            for (final String k : new String[] {"discord.game.vs", "discord.game.net",
                    "discord.game.watch", "discord.game.vs1"}) {
                final String v = p.getProperty(k);
                if (v == null) {
                    continue;
                }
                final int len = v.replace("{0}", longestFormat).replace("{1}", "99").length();
                if (len > worst) {
                    worst = len;
                    worstLang = lang + " " + k;
                }
            }
        }
        check(worst <= 128, "el peor texto real cabe: " + worst + " caracteres (" + worstLang + ")");
        System.out.println();
    }

    /**
     * ⚠️ La que de verdad importa.
     *
     * <p>Los dialogos se montan como {@code new StackPane(pantalla, capa)} y
     * eso <b>cambia la raiz de la escena</b>, que es justo lo que escucha la
     * presencia. Sin el filtro, abrir Ajustes o ampliar una carta en mitad de
     * una partida le contaria a tus amigos que te has ido al menu.
     */
    private static void dialogsAreNotAScreenChange() {
        System.out.println("  Un dialogo encima no es irse");
        check(DiscordStatus.keyFor(javafx.scene.layout.StackPane.class) == null,
                "el StackPane de los dialogos no cambia nada");
        check(DiscordStatus.keyFor(javafx.scene.layout.BorderPane.class) == null,
                "ni ningun otro contenedor de JavaFX");
        check(DiscordStatus.keyFor(null) == null, "ni una raiz nula");
        System.out.println();
    }

    /**
     * Que cada pantalla diga lo suyo.
     *
     * <p>Las clases se nombran por su nombre y no por {@code Clase.class} a
     * proposito: cargar {@code forge.neo.ui.*} aqui arrastraria JavaFX entero y
     * el comprobador dejaria de funcionar sin ventana.
     */
    private static void screensAreRecognised() {
        System.out.println("  Cada pantalla dice lo suyo");
        expect("MainMenu", "discord.menu");
        expect("HomeScreen", "discord.menu");
        expect("AchievementsScreen", "discord.menu");
        expect("DeckBuilderScreen", "discord.deck");
        expect("AscentMapScreen", "discord.ascent");
        expect("AscentRewardScreen", "discord.ascent");
        expect("QuestScreen", "discord.quest");
        expect("QuestShopScreen", "discord.quest");
        expect("DraftRunScreen", "discord.draft");
        expect("SealedScreen", "discord.sealed");
        expect("TournamentRunScreen", "discord.tournament");
        expect("TutorialScreen", "discord.tutorial");
        expect("PuzzleScreen", "discord.puzzle");
        expect("LobbyScreen", "discord.online");
        expect("OnlineMenu", "discord.online");
        // Y las dos que NO tienen que decir nada.
        expect("TableScreen", null);
        expect("LoadingScreen", null);
        expect("LanguageScreen", null);
        System.out.println();
    }

    /**
     * Que esa pantalla de esa clave, <b>y que la clave exista</b>.
     *
     * <p>Lo segundo es lo que cierra el circulo: una clave mal escrita en
     * {@code DiscordStatus} no revienta \u2014 {@code NeoText} devuelve la clave tal
     * cual, y el jugador acabaria ensenyando "discord.ascnet" a sus amigos.
     * Comprobando aqui que esta en {@link #KEYS}, y en los diez idiomas mas
     * abajo, no puede pasar.
     */
    private static void expect(final String screen, final String key) {
        final String got = DiscordStatus.screenKey(screen);
        final boolean same = java.util.Objects.equals(key, got);
        check(same, screen + " \u2192 " + (key == null ? "(nada)" : key)
                + (same ? "" : " pero da " + got));
        if (got != null) {
            check(java.util.Arrays.asList(KEYS).contains(got),
                    "  y '" + got + "' es una clave que existe");
        }
    }

    /** Apagar tiene que BORRAR, no solo callarse. */
    private static void turningItOffClears() {
        System.out.println("  Apagar borra");
        final String json = DiscordRich.payload(DiscordRich.CLEAR);
        check(json.contains("\"activity\":null"), "el adios manda activity nula");
        check(Json.wellFormed(json), "y sigue siendo JSON valido");
        check(!json.contains("large_image"), "sin imagen ni textos pegados");
        System.out.println();
    }

    /**
     * Los textos, en los diez. Y ademas que las frases con huecos los tengan:
     * un {@code {0}} perdido deja "contra 3 rivales" sin decir de que formato.
     */
    private static void textsInEveryLanguage() {
        System.out.println("  Los diez idiomas");
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
                for (final String k : new String[] {"discord.game.vs", "discord.game.vs1",
                        "discord.game.watch", "discord.game.net"}) {
                    if (!p.getProperty(k).contains("{0}")) {
                        missing = k + " (sin {0}: no diria el formato)";
                    }
                }
                if (!p.getProperty("discord.game.vs").contains("{1}")) {
                    missing = "discord.game.vs (sin {1}: no diria cuantos rivales)";
                }
                if (!p.getProperty("discord.turn").contains("{0}")) {
                    missing = "discord.turn (sin {0}: no diria el turno)";
                }
            }
            check(missing == null,
                    lang + ": los " + KEYS.length + " textos"
                            + (missing == null ? "" : " (falta " + missing + ")"));
        }

        // Y que al rellenarlos no quede ningun hueco a la vista, que es lo que
        // se veria si una clave se escribiera mal en el codigo.
        System.out.println();
        System.out.println("  Las frases, ya rellenas");
        final String[] made = {
            DiscordStatus.gameDetails("Commander", 3, 1, false),
            DiscordStatus.gameDetails("Commander", 1, 1, false),
            DiscordStatus.gameDetails("Commander", 3, 0, false),
            DiscordStatus.gameDetails("Brawl", 1, 1, true),
            DiscordStatus.gameState(7),
        };
        for (final String s : made) {
            check(s != null && !s.contains("{") && !s.startsWith("discord."),
                    "sale una frase, no una clave: " + s);
        }
        // Y la regla de privacidad, escrita como comprobacion: en red no se
        // dice contra cuantos se juega.
        final String net = DiscordStatus.gameDetails("Commander", 3, 1, true);
        check(!net.contains("3"), "en red no se dice contra cuantos: " + net);
        System.out.println();
    }

    /**
     * Y si hay un Discord delante, hablar con el de verdad.
     *
     * <p>Todo lo de arriba son cadenas: comprueba lo que <b>decimos</b>, no que
     * al otro lado alguien lo entienda. El apreton de manos, en cambio, solo se
     * puede probar contra el cliente de verdad — y es donde estaria el fallo si
     * Discord cambiara el protocolo o si el identificador de la aplicacion
     * estuviera mal escrito. Un digito de menos en el {@code APP_ID} pasa todas
     * las demas pruebas y no funciona nunca.
     *
     * <p><b>No falla si Discord no esta abierto</b>, que es lo normal en una
     * bateria. Se dice y se sigue, como hace {@code netcheck} con la linea.
     *
     * <p>⚠️ Con Discord abierto esto <b>pone y quita</b> una presencia de
     * prueba durante un segundo. Es en el Discord de quien corre la prueba, en
     * su propio ordenador, y se borra al terminar.
     */
    private static void liveIfDiscordIsOpen() {
        System.out.println("  Con Discord delante (opcional)");
        final DiscordIpc ipc = DiscordIpc.open();
        if (ipc == null) {
            System.out.println("    --   Discord no esta abierto: la tuberia no se puede probar");
            System.out.println("         (no es un fallo; con Discord abierto esta prueba dice mas)");
            System.out.println();
            return;
        }
        try {
            ipc.send(DiscordIpc.OP_HANDSHAKE, "{\"v\":1,\"client_id\":\"" + DiscordRich.APP_ID + "\"}");
            final String ready = ipc.receive();
            check(ready.contains("READY"),
                    "el apreton de manos vale: el APP_ID " + DiscordRich.APP_ID + " existe");
            // Y el mensaje entero, tal cual lo mandaria jugando.
            ipc.send(DiscordIpc.OP_FRAME, DiscordRich.payload(
                    DiscordActivity.now("Commander · " + NeoText.get("discord.playing"), "discordcheck")));
            final String answer = ipc.receive();
            check(!answer.contains("\"evt\":\"ERROR\""),
                    "Discord acepta la presencia" + (answer.contains("\"evt\":\"ERROR\"")
                            ? " -> " + answer : ""));
            // Y se quita, que esto era una prueba.
            ipc.send(DiscordIpc.OP_FRAME, DiscordRich.payload(DiscordRich.CLEAR));
            ipc.receive();
            System.out.println("    --   presencia de prueba puesta y quitada");
        } catch (final java.io.IOException e) {
            check(false, "hablando con Discord: " + e);
        } finally {
            ipc.close();
        }
        System.out.println();
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

    /**
     * Un lector de JSON de andar por casa, <b>solo para este comprobador</b>.
     *
     * <p>No se usa una libreria a proposito, aunque {@code gson} este en el
     * classpath: viene arrastrado por el motor, y atar una prueba nuestra a una
     * dependencia que no hemos declarado es como se rompe una build el dia que
     * Forge deja de usarla. Para leer un campo y contar llaves sobra con esto.
     */
    private static final class Json {

        private Json() {
        }

        /** El valor de {@code "name": "..."}, ya desescapado. */
        static String stringAt(final String json, final String name) {
            final int at = json.indexOf('"' + name + "\":\"");
            if (at < 0) {
                return null;
            }
            final StringBuilder out = new StringBuilder();
            for (int i = at + name.length() + 4; i < json.length(); i++) {
                final char c = json.charAt(i);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                final char e = json.charAt(++i);
                switch (e) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                    }
                    default -> out.append(e);
                }
            }
            return null;
        }

        /** Llaves equilibradas y ninguna cadena abierta. */
        static boolean wellFormed(final String json) {
            int depth = 0;
            boolean inString = false;
            for (int i = 0; i < json.length(); i++) {
                final char c = json.charAt(i);
                if (inString) {
                    if (c == '\\') {
                        i++;
                    } else if (c == '"') {
                        inString = false;
                    } else if (c < 0x20) {
                        // Un salto de linea crudo dentro de una cadena es
                        // exactamente lo que rompe el mensaje.
                        return false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth < 0) {
                        return false;
                    }
                }
            }
            return depth == 0 && !inString;
        }
    }
}
