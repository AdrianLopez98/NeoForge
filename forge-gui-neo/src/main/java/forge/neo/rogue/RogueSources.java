package forge.neo.rogue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Que ficheros de Forge hemos COPIADO para Rogue Commander, y con que huella.</b>
 *
 * <p>La logica del modo esta en {@code forge-gui/gamemodes/rogue} y la usamos
 * tal cual: sus arreglos llegan solos con cada rebase. Pero una parte de la
 * logica vivia en las pantallas Swing de {@code forge-gui-desktop} (los
 * "helpers" de cada nodo, el inicio de la run, el reparto de premios), y esa la
 * copiamos a {@code forge.neo.rogue}. Desde Forge #13023 (25-11-2026) esa
 * logica esta en {@code forge-gui/gamemodes/rogue/flow} sin Swing
 * ({@code RogueFlow} con una interfaz {@code RogueDecisions}); nuestras copias
 * siguen siendo las nuestras, y se vigila el original nuevo. Si Card-Forge la cambia, el rebase
 * NO avisa: la copia se quedaria vieja sin decir nada.
 *
 * <p>Por eso {@code roguecheck} compara la huella de cada original con la de
 * aqui. Si sale en rojo: abrir el original y la copia (misma estructura, lo
 * nuestro marcado {@code NEOFORGE}), repetir el cambio, y poner la huella nueva.
 * La huella se calcula sin los {@code \r}, para que no dependa de como saque
 * git los saltos de linea.
 */
public final class RogueSources {

    private RogueSources() {
    }

    /** Original (ruta desde forge/) → huella SHA-256 de cuando se copio. → copia nuestra. */
    static final Map<String, String[]> COPIED = new LinkedHashMap<>();

    private static final String D = "forge-gui-desktop/src/main/java/forge/screens/home/rogue/";
    private static final String F = "forge-gui/src/main/java/forge/gamemodes/rogue/flow/";

    static {
        // Desde Forge #13023 (25-11-2026) los helpers de cada nodo viven en
        // forge-gui/gamemodes/rogue/flow, ya sin Swing (RogueDecisions).
        put(F + "NodeBazaarHelper.java", "ed840f23b16a31339c39e2a4aacd6c914f6be239ba5e5b0449618ec3e1da6093", "RogueBazaar");
        put(F + "NodeChestHelper.java", "e740ba19ffc3248ca73a8706b336fd89f927c8d354be7484a0cd854fb90297de", "RogueChest");
        put(F + "NodeEventHelper.java", "789f1e29c40782e09219dcbb050727fd363d66c6df4579567aaac91eafacbbb2", "RogueEvents");
        put(F + "NodeSanctumHelper.java", "d4b16825498e43d5d3af16c405595627355e9def47311604aa6ac4ca8f3cf9eb", "RogueSanctum");
        put(F + "NodeEffectResultHelper.java", "db18bf0e21dd4a04085efad70eb8857d08c2da4d36e9a91a7836f221a4d94e8a", "RogueEffectResults");
        put(F + "EffectResultHelper.java", "af2c722feebe633374470cfaf33c4104daa8d9e48287a0654df47e565452989c", "RogueCardEffects");
        put(F + "NodePlaneboundHelper.java", "bcc282ae211176fe233c2769471e687308ed7cebf2e1eddc05346aaa70f64d51", "RogueDuel");
        put("forge-gui-desktop/src/main/java/forge/screens/match/RogueWinLose.java",
            "9c57d0852736eddb07dd765f67517a98fc4befa3957688baeef01fb9e7ea0bb7", "RogueDuel");
        // El flujo de la run (mapa, nodos, fin de partida) y el inicio: antes en
        // CSubmenuRogueMap/Start de Swing, desde #13023 en RogueFlow/RogueStart.
        put(F + "RogueFlow.java", "fa5b717a5a10753cd8ef60987904d721a17140ffb925bbe933e41d9275f9a645", "RogueSession y RogueDuel");
        put(F + "RogueStart.java", "cfa99375656bcc6064fa610445b102daa2afc5b169b6a1c56130a9e2c1468851", "RogueSession y RogueStartRules");
        put(D + "CSubmenuRogueMap.java", "7b43507ad56bb03bb9afaf2a1ca5503abfd3bfac96161368590032e2504c6e85", "RogueSession");
        put(D + "CSubmenuRogueStart.java", "08fde8917d631e86158045b08b0036de9bab310d7713c2fee356154a6ae6fb09", "RogueSession");
        put(D + "PathVisualizerPanel.java", "527326a9cffd96fd479230e4f8682bbbca7db064e23fbe4a4461d73097d9fd5f",
            "RogueMapModel, RoguePathLayout y ui.RogueMapScreen");
        // Que nodos salen boca abajo: desde #13023 lo calcula RoguePath.isFaceDown.
        put("forge-gui/src/main/java/forge/gamemodes/rogue/path/RoguePath.java",
            "197020f8de6343ec96a535cdceb28ce104b1116d44c6c1c231c9e1052b22cce5", "RogueMapModel");
        // El dibujo de cada nodo del mapa (24-11-2026: fiel al de Forge).
        put(D + "NodePanel.java", "56c2aa85cc070307d7839a4095453a3bb31fe95f18b5530a15eee4ac60fd9b73", "ui.RogueMapScreen");
        put(D + "NodeCircularPanel.java", "dbfcfb6c761ee28f6fe1c6f06a77af1e26b9613294c25a797b011a419a9f1e3e", "ui.RogueMapScreen");
        put(D + "NodePlaneboundPanel.java", "02d836e5d58161de9fcd71199c4779b647ece33782752cb650a5b5dd7c8a356d",
            "ui.RogueMapScreen y ui.RogueNodeIcons");
        put(D + "NodeSanctumPanel.java", "c1a010753dad4ea4ad68a420148bfc58bda172238435dca5913f9cd3676be8b3", "ui.RogueNodeIcons");
        put(D + "NodeBazaarPanel.java", "d2d9f1b81efc6529234afede4b8f889a2cda78349729f60e022825eaf8207e4d", "ui.RogueNodeIcons");
        put(D + "NodeChestPanel.java", "922c2d33ed33df0704703bfaf77da71518401a7604bd60a339341db2017502bf", "ui.RogueNodeIcons");
        put(D + "NodeEventPanel.java", "80f3f8038dd1cc0a117c8d4c2667d10639670db8a9f0e26bba981187622aa27e", "ui.RogueNodeIcons");
        // Las reglas que Forge tiene en sus pantallas (24-11-2026, para Android).
        put(D + "VSubmenuRogueStart.java", "f0c7917821324ae6f3d4246e346ef36856c986f56f9552b8d8066e5a383d2b69", "RogueStartRules");
        put(D + "CSubmenuRogueAether.java", "92a74bacea0c7da68cd511f4c1e957a3e80e92293e36071792743d893d4ea133", "RogueMeta");
        put(D + "VSubmenuRogueAether.java", "e315e66dc9add0a0024e419a110b5550ab66b78b85ee980803adeaccc41090d1", "RogueMeta");
        put(D + "VSubmenuRogueHistory.java", "efc05429dee69ca9c27551a563e3094836090141ecf2d88effd0481a804cc670", "RogueMeta");
        put(D + "CommanderSelectionDialog.java", "d38ef779556616b8c3ecba4e95e2bc02c2940a33a11a81a15ce1c1fc52926ef6", "RogueSession");
        put("forge-gui-desktop/src/main/java/forge/screens/deckeditor/controllers/CEditorRogue.java",
            "dc9c5307cfef2d0962b9a36cdce8e0c8a0b6a5e872885009c80aa01064e7c81c", "RogueDeckRules");
        put("forge-gui/src/main/java/forge/gamemodes/rogue/RogueDeckEdits.java",
            "e3a47c35174d224cf41746102c5bf53c8eb7ee8d2eab503fe20c3d4649b51fa0", "RogueDeckRules");
    }

    private static void put(final String original, final String sha, final String copy) {
        COPIED.put(original, new String[] {sha, copy});
    }

    /**
     * Los originales que han cambiado desde que se copiaron ("ruta → copia"),
     * o null si no esta el codigo de Forge (se mira desde forge/forge-gui-neo).
     */
    public static List<String> changed() {
        final File root = new File("..");
        if (!new File(root, "forge-gui-desktop").isDirectory()) {
            return null;
        }
        final List<String> out = new ArrayList<>();
        for (final Map.Entry<String, String[]> e : COPIED.entrySet()) {
            final File f = new File(root, e.getKey());
            final String now = f.isFile() ? sha(f) : "(no esta)";
            if (!e.getValue()[0].equals(now)) {
                out.add(e.getKey() + " -> " + e.getValue()[1]);
            }
        }
        return out;
    }

    private static String sha(final File f) {
        try {
            final byte[] raw = Files.readAllBytes(f.toPath());
            final ByteArrayOutputStream clean = new ByteArrayOutputStream(raw.length);
            for (final byte b : raw) {
                if (b != '\r') {
                    clean.write(b);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(clean.toByteArray()));
        } catch (final Exception e) {
            return "(" + e + ")";
        }
    }
}
