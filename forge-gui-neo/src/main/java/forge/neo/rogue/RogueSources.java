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
 * logica vive en las pantallas Swing de {@code forge-gui-desktop} (los
 * "helpers" de cada nodo, el inicio de la run, el reparto de premios), y esa la
 * hemos copiado a {@code forge.neo.rogue}. Si Card-Forge la cambia, el rebase
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

    static {
        put(D + "NodeBazaarHelper.java", "4b1e22ad02b876b8d4c86356710c729e29cedaf5578a76eede955671fae8f73a", "RogueBazaar");
        put(D + "NodeChestHelper.java", "ffba2a8ce8736d8c271b93c68cc0a2e43100323256f247177222a68407bc075a", "RogueChest");
        put(D + "NodeEventHelper.java", "afc78a83fb08f353307e564272ea825085a066286c326dc82b13000529b29c45", "RogueEvents");
        put(D + "NodeSanctumHelper.java", "cfb4784c17607a3cc15a5672a9acfbc60a8e2e13e79ded4ec9706f855e9b15c8", "RogueSanctum");
        put(D + "NodeEffectResultHelper.java", "0333280db82342e83b427016730658ad0190cae5f7807db65190c86d342bf745", "RogueEffectResults");
        put(D + "EffectResultHelper.java", "a84cfbcca8e47badeb5e071ecb63119c662b012676e7bca1dbe4c158c00ca375", "RogueCardEffects");
        put(D + "NodePlaneboundHelper.java", "78ea426c83c4a7fdd7c0ea6c384adb38619131cca37928842f52e41028372f27", "RogueDuel");
        put("forge-gui-desktop/src/main/java/forge/screens/match/RogueWinLose.java",
            "6eb34ee7407c397a74752914e2c5f5d7ecfdcfc1b3a239a47989f92b68e350e4", "RogueDuel");
        put(D + "CSubmenuRogueMap.java", "e7b258de3e54dd808cef98ddd610f5fb6c08c57099817582d29b44399d794e9c", "RogueSession");
        put(D + "CSubmenuRogueStart.java", "6df7608a1e5c9b2bc27e42cbe7f7e73478001f3cfe5655f4cd23a07c49d42b11", "RogueSession");
        put(D + "PathVisualizerPanel.java", "a93b6a4ba211b7ff9d4154de6f9b8478fced57abbb5d2b4adfcaeab11bad7955",
            "RogueMapModel, RoguePathLayout y ui.RogueMapScreen");
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
        put(D + "CSubmenuRogueAether.java", "310e366b965c6e4336cf4564f912314289608586f3dedb5d9d5d35619f1d3e11", "RogueMeta");
        put(D + "VSubmenuRogueAether.java", "88f6d65766a243a49a06d5d87476ed568d117abbd1adc03c8586845ea93076ac", "RogueMeta");
        put(D + "VSubmenuRogueHistory.java", "c67aedeb02abd60ad17558f5fa6c8c69b762d565403b4f09d01850e83af091f1", "RogueMeta");
        put(D + "CommanderSelectionDialog.java", "d38ef779556616b8c3ecba4e95e2bc02c2940a33a11a81a15ce1c1fc52926ef6", "RogueSession");
        put("forge-gui-desktop/src/main/java/forge/screens/deckeditor/controllers/CEditorRogue.java",
            "02c6ebd1ee92917c69e00479296b3c35c60c2f929968bbc5e25c973f42f7eaff", "RogueDeckRules");
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
