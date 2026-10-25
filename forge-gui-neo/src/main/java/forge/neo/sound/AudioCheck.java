package forge.neo.sound;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import forge.neo.platform.JavaSound;

/**
 * <b>El sonido de Linux, sin altavoz</b> ({@code run.cmd audiocheck}): que el
 * camino de {@link JavaSound} — el de SteamOS, donde JavaFX no puede con los
 * MP3 — decodifica TODOS los sonidos del juego: los de Forge
 * ({@code res/sound}), su musica ({@code res/music}) y los nuestros
 * ({@link NeoSounds}, dentro del jar). Y, si el equipo tiene tarjeta de sonido,
 * que se puede abrir una linea; en las maquinas de GitHub no la hay, y eso se
 * dice sin dar rojo.
 *
 * <p>Nacio el 25-10-2026 con el log de una Steam Deck: <i>"MediaException:
 * Could not create player!"</i> en cada MP3.
 */
public final class AudioCheck {

    private AudioCheck() {
    }

    public static void run() {
        int bad = 0;
        int ok = 0;
        final String assets = System.getProperty("forge.assetsDir", "../forge-gui/");
        for (final String dir : new String[] {"res/sound", "res/music"}) {
            final List<File> files = new ArrayList<>();
            collect(new File(assets, dir), files);
            if (files.isEmpty()) {
                System.out.println("  [MAL] no hay MP3 en " + new File(assets, dir).getAbsolutePath());
                bad++;
            }
            for (final File f : files) {
                try (InputStream in = new FileInputStream(f)) {
                    JavaSound.probe(in);
                    ok++;
                } catch (final Exception e) {
                    System.out.println("  [MAL] " + dir + "/" + f.getName() + ": " + e);
                    bad++;
                }
            }
            System.out.println("  " + dir + ": " + files.size() + " MP3");
        }
        int ours = 0;
        for (final String event : NeoSounds.events()) {
            for (final String name : NeoSounds.pool(event)) {
                if (name.isEmpty()) {
                    continue;
                }
                try (InputStream in = AudioCheck.class.getResourceAsStream("/forge/neo/sound/" + name)) {
                    if (in == null) {
                        throw new java.io.FileNotFoundException("no esta en el jar");
                    }
                    JavaSound.probe(in);
                    ok++;
                    ours++;
                } catch (final Exception e) {
                    System.out.println("  [MAL] " + name + ": " + e);
                    bad++;
                }
            }
        }
        System.out.println("  de Neo Forge: " + ours + " MP3");
        System.out.println("  [" + (bad == 0 ? "OK " : "MAL") + "] " + ok + " MP3 decodificados sin JavaFX");

        final String line = JavaSound.probeLine();
        System.out.println(line == null
                ? "  [OK ] se abre una linea de sonido (16 bits, 44,1 kHz)"
                : "  [-- ] sin tarjeta de sonido en este equipo, no se prueba la linea: " + line);
        System.out.println("  por aqui en este sistema: " + (JavaSound.enabled() ? "si" : "no (JavaFX)"));

        System.out.printf(Locale.ROOT, "%n  %d mal%n", bad);
        if (bad > 0) {
            throw new IllegalStateException(bad + " sonido(s) no se pueden decodificar");
        }
    }

    private static void collect(final File dir, final List<File> out) {
        final File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (final File f : kids) {
            if (f.isDirectory()) {
                collect(f, out);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".mp3")) {
                out.add(f);
            }
        }
    }
}
