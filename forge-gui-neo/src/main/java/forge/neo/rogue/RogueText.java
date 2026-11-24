package forge.neo.rogue;

import forge.gamemodes.rogue.TextHelper;
import forge.gamemodes.rogue.effect.CustomRunModification;
import forge.util.Localizer;

/**
 * Textos de Rogue Commander listos para pintar, los mismos en el escritorio y
 * en Android (por eso viven en la logica y no en una pantalla).
 */
public final class RogueText {

    private RogueText() {
    }

    /** Donde se reportan los fallos del modo: es de Forge y su logica no la mantenemos. */
    public static final String REPORT_URL = "https://github.com/Card-Forge/forge/issues";

    /** Un apartado de la "i" del modo. */
    public record InfoSection(String title, String text) {
    }

    /**
     * La "i" del inicio de Rogue Commander (Ana, 24-11-2026): que es el modo,
     * como se juega, que pasa entre runs y a quien se reportan los fallos (lo
     * que no se ve mal en pantalla, a Forge). Los mismos apartados en el PC
     * ({@code RogueStartScreen}) y en Android ({@code RogueFlow.kt}).
     */
    public static java.util.List<InfoSection> info() {
        return java.util.List.of(
            new InfoSection(forge.neo.NeoText.get("rogue.info.what.title"), forge.neo.NeoText.get("rogue.info.what")),
            new InfoSection(forge.neo.NeoText.get("rogue.info.how.title"), forge.neo.NeoText.get("rogue.info.how")),
            new InfoSection(forge.neo.NeoText.get("rogue.info.between.title"), forge.neo.NeoText.get("rogue.info.between")),
            new InfoSection(forge.neo.NeoText.get("rogue.info.bugs.title"), forge.neo.NeoText.get("rogue.info.bugs")));
    }

    /**
     * Un texto de Forge sin lo que es de su interfaz Swing: las marcas de
     * vista previa ({@code TextHelper.stripPreviewMarkers}) y el HTML de sus
     * etiquetas ({@code <br>} pasa a salto de linea, lo demas se quita).
     */
    public static String clean(final String s) {
        if (s == null) {
            return "";
        }
        String out = TextHelper.stripPreviewMarkers(s);
        out = out.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
        // **Nombre**: la negrita de Forge (RogueButtonHelper.applyAutomaticHighlights).
        // Aqui el texto es llano: sin esto salian los asteriscos ("Gain the Trait
        // **Shield Of Faith**", visto en el cofre de Android el 24-11-2026).
        out = out.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        return out;
    }

    /**
     * El nombre de una mejora del Aether. Es su campo {@code name}: desde
     * Kotlin (Android) {@code name} es el del enum ("LEVEL_2"), no este.
     */
    public static String name(final forge.gamemodes.rogue.AetherUpgrade upgrade) {
        return upgrade == null ? "" : upgrade.name;
    }

    /** El nombre de un nivel de Descension (su campo {@code name}; ver el de arriba). */
    public static String name(final forge.gamemodes.rogue.effect.DescensionLevel level) {
        return level == null ? "" : level.name;
    }

    /** El nombre de una modificacion de run personalizada, como lo pone {@code VSubmenuRogueStart}. */
    public static String modification(final CustomRunModification m) {
        return m == CustomRunModification.MANIA ? Localizer.getInstance().getMessage("lblRogueMania")
            : m == CustomRunModification.CHOSEN_ONE ? Localizer.getInstance().getMessage("lblRogueChosenOne")
            : m.getDisplayName();
    }
}
