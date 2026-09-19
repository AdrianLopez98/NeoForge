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

    /** Clave de NeoSettings: el fullScreen del Adventure lo puso NeoForge, no el jugador. */
    private static final String FULLSCREEN_BY_NEO = "adventure.fullscreenByNeo";

    /**
     * Que el Adventure NAZCA a pantalla completa si NeoForge lo esta.
     *
     * <p>Si nace en ventana, Windows la deja mas baja que el monitor (la barra
     * de titulo: 3840x2097 en un 4K) y Forge fija la escala de TODA su interfaz
     * con ese alto, una sola vez ({@code forge.util.Utils}, constantes). Al
     * estirarla luego a pantalla completa los textos no caben y se cortan por
     * arriba (reportado el 19-09-2026, medido: "escala calculada para 2097").
     * La unica forma de que nazca al tamanyo del monitor es su propio ajuste
     * {@code fullScreen}, que su lanzador lee al crear la ventana; despues
     * {@link WindowPlacement} la pasa a sin bordes al mismo tamanyo.
     *
     * <p>Solo se toca si lo pide NeoForge, y se deshace igual: si NeoForge ya
     * no esta en pantalla completa y el ajuste lo habiamos puesto nosotros, se
     * quita. El que el jugador puso en el Adventure no se toca nunca.
     */
    static void syncFullscreen(final File prefsDir, final boolean neoFullscreen) {
        final File file = new File(prefsDir.getParentFile(), "adventure" + File.separator + "settings.json");
        try {
            final com.badlogic.gdx.utils.JsonValue root = file.isFile()
                    ? new com.badlogic.gdx.utils.JsonReader().parse(
                            new String(java.nio.file.Files.readAllBytes(file.toPath()),
                                    java.nio.charset.StandardCharsets.UTF_8))
                    : new com.badlogic.gdx.utils.JsonValue(com.badlogic.gdx.utils.JsonValue.ValueType.object);
            final boolean theirs = root.getBoolean("fullScreen", false);
            final boolean byNeo = forge.neo.NeoSettings.getBool(FULLSCREEN_BY_NEO, false);
            final boolean want;
            if (neoFullscreen && !theirs) {
                want = true;
                forge.neo.NeoSettings.set(FULLSCREEN_BY_NEO, "true");
            } else if (!neoFullscreen && theirs && byNeo) {
                want = false;
                forge.neo.NeoSettings.set(FULLSCREEN_BY_NEO, null);
            } else {
                return;
            }
            forge.neo.NeoSettings.save();
            if (root.has("fullScreen")) {
                root.get("fullScreen").set(want);
            } else {
                root.addChild("fullScreen", new com.badlogic.gdx.utils.JsonValue(want));
            }
            file.getParentFile().mkdirs();
            java.nio.file.Files.write(file.toPath(),
                    root.prettyPrint(com.badlogic.gdx.utils.JsonWriter.OutputType.json, 0)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("[aventura] pantalla completa del Adventure: " + want);
        } catch (final Throwable e) {
            System.out.println("[aventura] no se pudo ajustar su pantalla completa: " + e);
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
