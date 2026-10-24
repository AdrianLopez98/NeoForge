package forge.neo.sound;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import forge.neo.NeoSettings;

/**
 * Los sonidos de Neo Forge sin ventana ({@code run.cmd soundcheck}): que cada
 * fichero sale del jar, que el azar reparte entre todas las variantes (y en
 * "destruir" tambien el de Forge), que con el ajuste apagado vuelven los de
 * Forge y que un evento sin monton no se toca. No guarda el ajuste: lo deja
 * como estaba.
 */
public final class SoundCheck {

    private static int passed;
    private static int failed;

    private SoundCheck() {
    }

    public static void run() {
        final boolean before = NeoSounds.enabled();
        try {
            NeoSettings.setBool(NeoSounds.SETTING, true);

            final Set<String> missing = new HashSet<>();
            int files = 0;
            for (final String ev : NeoSounds.events()) {
                for (final String name : NeoSounds.pool(ev)) {
                    if (name.isEmpty()) {
                        continue;
                    }
                    files++;
                    final File f = NeoSounds.file(name);
                    if (f == null || !f.isFile() || f.length() < 1000) {
                        missing.add(name);
                    }
                }
            }
            check(missing.isEmpty(), files + " sonidos salen del jar a la cache", "no salen: " + missing);

            final Set<String> badSpread = new HashSet<>();
            for (final String ev : NeoSounds.events()) {
                final String[] pool = NeoSounds.pool(ev);
                final Set<String> seen = new HashSet<>();
                for (int i = 0; i < 400; i++) {
                    final File f = NeoSounds.pick(ev);
                    seen.add(f == null ? NeoSounds.FORGE : f.getName());
                }
                if (seen.size() != pool.length) {
                    badSpread.add(ev + " (" + seen.size() + " de " + pool.length + ")");
                }
            }
            check(badSpread.isEmpty(), "el azar reparte entre todas las variantes, y destruir incluye el de Forge",
                    "variantes que no salen: " + badSpread);

            check(NeoSounds.has("instant") && NeoSounds.has("instant.mp3") && !NeoSounds.has("untap"),
                    "instant tiene monton (con extension o sin ella) y untap no", "has() falla");
            check(NeoSounds.pick("untap") == null, "un evento sin monton suena el de Forge", "untap no da el de Forge");

            NeoSettings.setBool(NeoSounds.SETTING, false);
            boolean allForge = true;
            for (final String ev : NeoSounds.events()) {
                allForge &= NeoSounds.pick(ev) == null;
            }
            check(allForge, "con el ajuste apagado, todo suena como en Forge", "apagado aun suena alguno nuestro");

            final float v = NeoSounds.volume();
            check(v >= 0f && v <= 1f, "el volumen de los sonidos nuevos sale de los ajustes (" + v + ")",
                    "volumen fuera de rango: " + v);
        } finally {
            NeoSettings.setBool(NeoSounds.SETTING, before);
        }
        System.out.println();
        System.out.println("  " + passed + " bien, " + failed + " mal");
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) de los sonidos han fallado");
        }
    }

    private static void check(final boolean ok, final String good, final String bad) {
        if (ok) {
            passed++;
            System.out.println("  [OK ] " + good);
        } else {
            failed++;
            System.out.println("  [MAL] " + bad);
        }
    }
}
