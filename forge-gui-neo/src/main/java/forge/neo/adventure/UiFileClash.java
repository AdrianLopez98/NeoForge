package forge.neo.adventure;

import com.badlogic.gdx.files.FileHandle;
import forge.adventure.util.Config;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * que el libro de misiones (tecla Q) no cierre el Adventure en espanyol.
 *
 * <p><b>Es un fallo de Forge, no nuestro.</b> {@code Config.getFile} busca la
 * traduccion de un fichero SOLO por su nombre: para {@code ui/quests.json} (el
 * diseno de la pantalla) mira {@code languages/quests-es-ES.json}... que existe,
 * pero son las MISIONES traducidas de Shandalar, no un diseno. Lo carga como
 * {@code UIData}, revienta la lectura del JSON, el hilo de libGDX muere y la
 * ventana desaparece (reportado jugando el 19-09-2026). Le pasa a cualquiera que
 * juegue el Adventure de Forge en espanyol; aqui sale siempre porque la Aventura
 * arranca en el idioma de NeoForge.
 *
 * <p>No se toca {@code Config} (regla de oro): se le rellena la cache ANTES de
 * que nadie pida esos ficheros, con el diseno de verdad. {@code getFile} mira la
 * cache lo primero. El campo es privado y no hay otra forma de llegar —es la
 * unica reflexion del modulo—; si Forge lo arregla, esto sigue siendo inocuo
 * (pone lo mismo que ellos pondrian), y si cambian el campo, no hace nada.
 *
 * <p>Se comparan TODOS los disenyos de {@code ui/} contra CUALQUIER idioma de
 * {@code languages/}: hoy solo choca {@code quests}, pero el dia que alguien
 * traduzca {@code shops} al aleman o un diseno se llame como otro fichero de
 * datos, choca igual y ya esta cubierto.
 *
 * <p><b>Publica porque Android tambien la necesita.</b> Alli el Adventure corre
 * dentro de la aplicacion de NeoForge y llega al mismo fallo por el mismo
 * camino —el idioma del Adventure es el de NeoForge—, pero no pasa por
 * {@link WindowPlacement}, que es de LWJGL. La logica no se duplica: NeoForge
 * Android llama a esto mismo desde su propio primer fotograma.
 */
public final class UiFileClash {

    private UiFileClash() {
    }

    /** En el hilo de libGDX, con {@link Config} ya creada (primer fotograma). */
    public static void apply() {
        try {
            final Config cfg = Config.instance();
            final Field f = Config.class.getDeclaredField("Cache");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            final Map<String, FileHandle> cache = (Map<String, FileHandle>) f.get(cfg);
            final String plane = cfg.getFilePath("");
            final String common = cfg.getCommonFilePath("");
            for (final String root : new String[] {plane, common}) {
                final File[] files = new File(root + "ui").listFiles();
                if (files == null) {
                    continue;
                }
                for (final File file : files) {
                    final String name = file.getName();
                    final int dot = name.lastIndexOf('.');
                    final String path = "ui/" + name;
                    if (dot <= 0 || cache.containsKey(path)
                            || !(clashes(plane, name, dot) || clashes(common, name, dot))) {
                        continue;
                    }
                    // El mismo orden que getFile, sin la traduccion: el de su
                    // plano si lo tiene, si no el comun.
                    final File good = new File(plane + path).isFile() ? new File(plane + path) : file;
                    cache.put(path, new FileHandle(good));
                    NeoDuelBridge.log("diseno que chocaba con una traduccion, fijado: " + path);
                }
            }
        } catch (final Throwable e) {
            NeoDuelBridge.log("no se pudo revisar los disenos traducidos: " + e);
        }
    }

    /** Si en {@code root/languages} hay algun "nombre-IDIOMA.ext" para ese fichero. */
    private static boolean clashes(final String root, final String name, final int dot) {
        final String base = name.substring(0, dot) + "-";
        final String ext = name.substring(dot);
        final File[] langs = new File(root + "languages").listFiles();
        if (langs == null) {
            return false;
        }
        for (final File l : langs) {
            final String n = l.getName();
            if (n.startsWith(base) && n.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }
}
