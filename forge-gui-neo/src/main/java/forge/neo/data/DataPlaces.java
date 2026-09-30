package forge.neo.data;

import java.io.File;

import forge.neo.NeoLog;
import forge.neo.NeoPortable;

/**
 * Donde viven tus datos EN EL ESCRITORIO, para {@link NeoBackup}. Sin tocar
 * ForgeConstants: se usa tambien en el arranque, antes del motor.
 *
 * <p>La Aventura es la unica que cambia: el {@code .exe} portable lleva perfil
 * y la guarda donde Forge ({@code datos/adventure}); sin perfil (desarrollo,
 * Mac) se lanza con su propio APPDATA y queda en
 * {@code <datos>/neo/adventure/Forge/adventure}. Es la misma regla que
 * {@code AdventureLauncher}.
 */
public final class DataPlaces {

    private DataPlaces() {
    }

    public static NeoBackup.Places desktop() {
        final File root = NeoLog.dataRoot();
        final File adventure = NeoPortable.dataDir() != null
                ? new File(root, "adventure")
                : new File(root, "neo" + File.separator + "adventure" + File.separator + "Forge"
                        + File.separator + "adventure");
        return new NeoBackup.Places(root, adventure);
    }

    /** "windows", "mac" o "linux": para el manifest y para decir de donde viene un zip. */
    public static String platform() {
        final String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        return os.contains("win") ? "windows" : os.contains("mac") ? "mac" : "linux";
    }
}
