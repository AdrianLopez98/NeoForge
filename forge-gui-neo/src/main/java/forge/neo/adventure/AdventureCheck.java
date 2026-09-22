package forge.neo.adventure;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

/**
 * La Aventura sin ventana: lo que se rompe EN SILENCIO al rebasar con Forge.
 *
 * <p>Para meter nuestros combates y nuestro editor en el Adventure se copian dos
 * clases de Forge ({@code DuelScene} y {@code DeckEditScene}) con un bloque
 * anyadido, y la copia va antes en el classpath. Si Card-Forge cambia el
 * original, el rebase no da ningun conflicto -nuestra copia no es su fichero- y
 * seguiriamos jugando con su version VIEJA sin que nada fallara. Por eso aqui se
 * guarda la huella del original que se copio, y en cuanto cambie esto se pone en
 * rojo: hay que volver a copiar su version nueva y reponer el bloque NEOFORGE.
 *
 * <p>Comprueba ademas que las copias son las que se cargan (el orden del
 * classpath) y que el lanzador del Adventure esta.
 *
 * <p>{@code java -cp ... forge.neo.adventure.AdventureCheck}, desde
 * {@code forge-gui-neo} (lo lanza {@code tools/comprobar-todo.py}).
 */
public final class AdventureCheck {

    private AdventureCheck() {
    }

    /** Huella del original copiado. Actualizar al volver a copiar. */
    static final String DUEL_SCENE_SHA256 = "767c539c27f2b35cfe3db58e87dd8fe476783d1db448909fab765ad7d397ef47";
    static final String DECK_EDIT_SCENE_SHA256 = "9468170eb70dea10c3ee6462ff87819a15dd6c42efcd9f573bc273a64d309a9a";

    private static int ok;
    private static int bad;

    public static void main(final String[] args) throws Exception {
        System.out.println("== Aventura: las copias de Forge siguen al dia ==");
        original("DuelScene", DUEL_SCENE_SHA256);
        original("DeckEditScene", DECK_EDIT_SCENE_SHA256);
        loadedFromUs("forge.adventure.scene.DuelScene");
        loadedFromUs("forge.adventure.scene.DeckEditScene");
        check("el lanzador del Adventure (forge.app.Main) esta en el classpath",
                resource("forge/app/Main.class") != null);
        check("DuelScene lleva el bloque NEOFORGE",
                source("DuelScene").contains("NeoDuelBridge.play("));
        check("DuelScene lleva el bloque NEOFORGE-2 (el ganador, del match)",
                source("DuelScene").contains("hostedMatch.getMatch().getWinner()"));
        check("DeckEditScene lleva el bloque NEOFORGE",
                source("DeckEditScene").contains("NeoDeckBridge.open("));
        duelHasAWayOut();
        System.out.println();
        System.out.println(ok + " comprobaciones OK, " + bad + " fallos");
        System.exit(bad == 0 ? 0 : 1);
    }

    /**
     * Del duelo de la Aventura se tiene que poder salir <b>viendolo</b>.
     *
     * <p>Aqui no hay tutorial que ensenye Escape — se entra desde el mapa de
     * Forge, en otro proceso — asi que la unica salida tiene que estar a la
     * vista. Reportado en Reddit el 22-09-2026: <i>"I don't seem to find a
     * concede button in the battle screen for adventure mode"</i>.
     *
     * <p>Se comprueba leyendo el codigo, como el resto de este fichero: montar
     * una escena de JavaFX sin ventana para mirar un boton costaria mas que lo
     * que protege, y lo que se rompe en silencio es que alguien quite la
     * llamada al reordenar la pantalla.
     */
    private static void duelHasAWayOut() {
        final String duel = neoSource("forge/neo/adventure/DuelControls.java");
        check("el duelo enciende el boton de la pausa (no solo Escape)",
                duel.contains("enablePauseButton("));
        check("y abre el menu de la Aventura: sin Reiniciar, y salir es rendirse",
                duel.contains("PauseMenu.forAdventure("));
        for (final String key : new String[] {"table.menu", "adventure.concede",
                "adventure.concede.ask", "adventure.concede.detail", "adventure.concede.yes"}) {
            final List<String> missing = languagesWithout(key);
            check("\"" + key + "\" esta en los diez idiomas"
                    + (missing.isEmpty() ? "" : " -> falta en " + missing),
                    missing.isEmpty());
        }
    }

    /** Los idiomas a los que les falta esa clave. */
    private static List<String> languagesWithout(final String key) {
        final List<String> missing = new ArrayList<>();
        final File dir = new File("src/main/resources/forge/neo/lang");
        final File[] files = dir.listFiles((d, n) -> n.startsWith("neo-") && n.endsWith(".properties"));
        if (files == null || files.length == 0) {
            return List.of("(no esta " + dir + ")");
        }
        for (final File f : files) {
            try {
                final Properties p = new Properties();
                try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
                    p.load(r);
                }
                final String value = p.getProperty(key);
                if (value == null || value.isBlank()) {
                    missing.add(f.getName());
                }
            } catch (final Exception e) {
                missing.add(f.getName() + " (" + e + ")");
            }
        }
        return missing;
    }

    /** Un fichero de codigo NUESTRO, leido tal cual. */
    private static String neoSource(final String path) {
        try {
            return Files.readString(new File("src/main/java/" + path).toPath());
        } catch (final Exception e) {
            return "";
        }
    }

    private static void original(final String name, final String expected) throws Exception {
        final File f = new File("../forge-gui-mobile/src/forge/adventure/scene/" + name + ".java");
        if (!f.isFile()) {
            System.out.println("  [--] no esta el codigo de Forge (" + f + "): se salta");
            return;
        }
        final String now = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(f.toPath())));
        check(name + " de Forge es el mismo que copiamos"
                + (now.equals(expected) ? "" : " -> CAMBIO: volver a copiarlo y reponer el bloque NEOFORGE"),
                now.equals(expected));
    }

    private static void loadedFromUs(final String className) {
        final URL url = resource(className.replace('.', '/') + ".class");
        final String where = url == null ? "(no esta)" : url.toString();
        check(className + " se carga de NeoForge y no de forge-gui-mobile: " + where,
                url != null && !where.contains("forge-gui-mobile"));
    }

    private static String source(final String name) {
        final File f = new File("src/main/java/forge/adventure/scene/" + name + ".java");
        try {
            return Files.readString(f.toPath());
        } catch (final Exception e) {
            return "";
        }
    }

    private static URL resource(final String path) {
        return AdventureCheck.class.getClassLoader().getResource(path);
    }

    private static void check(final String what, final boolean pass) {
        if (pass) {
            ok++;
            System.out.println("  [OK] " + what);
        } else {
            bad++;
            System.out.println("  [MAL] " + what);
        }
    }
}
