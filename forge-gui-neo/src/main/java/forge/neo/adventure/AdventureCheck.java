package forge.neo.adventure;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

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
    static final String DUEL_SCENE_SHA256 = "72304ce19c13c11091a3ba1128e2091e08352533924265bede6091540d57d3a1";
    static final String DECK_EDIT_SCENE_SHA256 = "f02cd12d60cbd14f26af0de701c3eff171fa164623f7949e94985c30445dbb03";

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
        System.out.println();
        System.out.println(ok + " comprobaciones OK, " + bad + " fallos");
        System.exit(bad == 0 ? 0 : 1);
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
