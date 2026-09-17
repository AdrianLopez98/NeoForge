package forge.neo.adventure;

import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoLanguage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Que la Aventura salga en el idioma de NeoForge y con tus ajustes.
 *
 * <p>El Adventure puede guardar sus datos aparte (ver {@link AdventureLauncher}),
 * y entonces no lee ni el idioma ni los ajustes de NeoForge. Justo antes de
 * abrirlo, desde el menu:
 * <ul>
 *   <li><b>El idioma del motor</b> ({@code UI_LANGUAGE} de su
 *       {@code forge.preferences}): el que tenga NeoForge, si Forge trae ese
 *       idioma; si no, ingles.</li>
 *   <li><b>Directo al Adventure y sin su musica</b> ({@code UI_SELECTOR_MODE},
 *       {@code UI_ENABLE_MUSIC} y {@code UI_VOL_MUSIC}).</li>
 *   <li><b>Los ajustes de NeoForge</b> ({@code neo.properties}): se copian, para
 *       que la mesa y el editor se comporten como los tienes (escala, pago
 *       automatico, idioma de nuestras pantallas...).</li>
 * </ul>
 * Los ajustes de NeoForge se leen, no se tocan.
 */
final class AdventureSettings {

    private AdventureSettings() {
    }

    static void prepare(final File prefsDir, final boolean ownFolder) {
        prefsDir.mkdirs();
        final String lang = engineLanguage();
        try {
            // Con carpeta propia se le copian los ajustes de NeoForge; sin ella
            // (el .exe, con perfil) ya lee los mismos: no hay nada que copiar.
            if (ownFolder) {
                copyNeoSettings(prefsDir, lang);
            }
            final java.util.Map<String, String> prefs = new java.util.LinkedHashMap<>();
            prefs.put("UI_LANGUAGE", lang);
            // Directo al Adventure: sin la pantalla de "Modo clasico / Modo de aventura".
            prefs.put("UI_SELECTOR_MODE", "Adventure");
            // Sin su musica: suena la de NeoForge, que sigue detras.
            //
            // Hacen falta las DOS. SoundSystem.isMuted() mira UI_ENABLE_MUSIC
            // solo fuera de libGDX (nuestros duelos, con NeoGuiBase); en el
            // mapa, que es libGDX, mira UI_VOL_MUSIC < 1 y el interruptor le da
            // igual. Con solo el interruptor su musica sonaba igual, y a volumen
            // 100, encima de la nuestra (reportado jugando el 17-09-2026).
            prefs.put("UI_ENABLE_MUSIC", "false");
            prefs.put("UI_VOL_MUSIC", "0");
            // Y los efectos, al volumen de NeoForge. Su proceso no pasa por
            // NeoSettings.applyAudioToEngine (eso lo hace NeoApp al arrancar),
            // asi que sin esto la Aventura sonaba SIEMPRE al 100: "el combate
            // suena altisimo y no puedo bajarlo" (17-09-2026). Se escribe aqui
            // y no al empezar un duelo para que valga tambien en su mapa.
            final int sfx = forge.neo.NeoSettings.getInt(
                    forge.neo.NeoSettings.SOUND_VOLUME, forge.neo.NeoSettings.SOUND_VOLUME_DEFAULT);
            prefs.put("UI_VOL_SOUNDS", String.valueOf(sfx));
            prefs.put("UI_ENABLE_SOUNDS", String.valueOf(sfx > 0));
            setForgePrefs(new File(prefsDir, "forge.preferences"), prefs);
            System.out.println("[aventura] idioma: " + lang);
        } catch (final IOException e) {
            System.out.println("[aventura] no se pudo pasar idioma/ajustes: " + e);
        }
    }

    /** El idioma de NeoForge si Forge lo trae; si no, ingles. */
    static String engineLanguage() {
        final String wanted = NeoLanguage.current();
        final File file = new File(ForgeConstants.LANG_DIR, wanted + ".properties");
        return file.isFile() ? wanted : "en-US";
    }

    private static void copyNeoSettings(final File prefsDir, final String lang) throws IOException {
        final File mine = new File(ForgeConstants.USER_PREFS_DIR, "neo.properties");
        final File theirs = new File(prefsDir, "neo.properties");
        final java.util.Properties p = new java.util.Properties();
        if (mine.isFile()) {
            Files.copy(mine.toPath(), theirs.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try (var in = Files.newInputStream(theirs.toPath())) {
                p.load(in);
            }
        }
        p.setProperty(NeoLanguage.SETTING, lang);
        try (var out = Files.newOutputStream(theirs.toPath())) {
            p.store(out, "NeoForge (copia para la Aventura)");
        }
    }

    private static void setForgePrefs(final File file, final java.util.Map<String, String> values)
            throws IOException {
        final List<String> lines = file.isFile()
                ? new ArrayList<>(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8))
                : new ArrayList<>();
        for (final java.util.Map.Entry<String, String> e : values.entrySet()) {
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(e.getKey() + "=")) {
                    lines.set(i, e.getKey() + "=" + e.getValue());
                    found = true;
                }
            }
            if (!found) {
                lines.add(e.getKey() + "=" + e.getValue());
            }
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }
}
