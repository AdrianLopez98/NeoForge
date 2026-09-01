package forge.neo.look;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.localinstance.properties.ForgeConstants;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;

/**
 * Que suena, y como se elige.
 *
 * <h2>Esto NO reproduce nada</h2>
 *
 * Forge ya tiene el reproductor, la lista y el encadenado de pistas. Lo unico
 * que hace falta es <b>decirle de que carpeta sacar la musica</b>, y eso es una
 * preferencia: {@code FPref.UI_CURRENT_MUSIC_SET}.
 *
 * <p>El motor busca cada lista por orden y se queda con <b>la primera carpeta
 * que tenga algo</b>:
 *
 * <pre>
 * 1. %APPDATA%\Forge\custom\music\Neo\match\     &lt;- la nuestra
 * 2. %LOCALAPPDATA%\Forge\Cache\music\Neo\match\
 * 3. res\music\match\                            &lt;- las de fabrica
 * </pre>
 *
 * <p>De ahi sale, gratis, el comportamiento que se quiere: <b>si no eliges
 * nada, suenan las de fabrica</b>; en cuanto marcas una, suenan solo las
 * marcadas. Marcar es copiar a esa carpeta; desmarcar es borrar de ahi.
 *
 * <h2>Tres carpetas, y hacen falta las tres</h2>
 *
 * <pre>
 * res\music\&lt;lista&gt;                           las de Forge  (no se tocan)
 * %APPDATA%\Forge\neo\music\&lt;lista&gt;          TU BIBLIOTECA (lo que has traido)
 * %APPDATA%\Forge\custom\music\Neo\&lt;lista&gt;   lo que SUENA ahora
 * </pre>
 *
 * <p>La del medio es la que faltaba, y su ausencia costaba caro: lo que
 * importabas iba <b>solo</b> a la del motor, asi que desmarcar una pista tuya
 * — que es borrarla de ahi — <b>destruia la unica copia que habia</b>. El
 * mismo boton era reversible para una cancion de Forge (la original sigue en
 * {@code res/music}) e irreversible para la tuya, sin decirlo.
 *
 * <p>Con la biblioteca, marcar y desmarcar solo mueven la copia que suena, y
 * lo unico que borra un fichero tuyo es {@link #delete}. Importar deja copia
 * en las dos: en la biblioteca para quedarsela, y en la del motor para que
 * suene desde el primer momento sin tener que marcarla aparte.
 */
public final class NeoMusic {

    private NeoMusic() {
    }

    /** Como se llama nuestro conjunto de musica dentro de Forge. */
    public static final String SET = "Neo";

    /** Una pista: de donde sale, como se llama y si suena. */
    public static final class Track {
        private final String fileName;
        private final File source;
        private final MusicPlaylist list;
        private final boolean imported;

        Track(final String fileName, final File source, final MusicPlaylist list,
              final boolean imported) {
            this.fileName = fileName;
            this.source = source;
            this.list = list;
            this.imported = imported;
        }

        public String getFileName() {
            return fileName;
        }

        /** El nombre sin extension, que es lo que se lee. */
        public String getLabel() {
            final int dot = fileName.lastIndexOf('.');
            return dot > 0 ? fileName.substring(0, dot) : fileName;
        }

        public MusicPlaylist getList() {
            return list;
        }

        /** Si la ha traido el jugador (y por tanto se puede borrar). */
        public boolean isImported() {
            return imported;
        }

        /** Si esta marcada para sonar. */
        public boolean isEnabled() {
            return new File(dir(list), fileName).isFile();
        }

        File getSource() {
            return source;
        }
    }

    // ---------------------------------------------------------------
    // Las carpetas
    // ---------------------------------------------------------------

    /** Nuestra carpeta para esa lista, creada si hace falta. */
    public static File dir(final MusicPlaylist list) {
        final File dir = new File(ForgeConstants.USER_CUSTOM_DIR
                + ForgeConstants.MUSIC_DIR + SET + File.separator + list.getSubDir());
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** La carpeta de fabrica de esa lista. */
    private static File stockDir(final MusicPlaylist list) {
        return new File(ForgeConstants.RES_DIR + ForgeConstants.MUSIC_DIR + list.getSubDir());
    }

    /**
     * Tu MUSICA, la que has traido tu. Permanente.
     *
     * <p>Esta carpeta existe por un fallo real de la version anterior: lo que
     * importabas se copiaba <b>solo</b> a la carpeta del motor, que es la que
     * dice "esto suena ahora". Y como desmarcar una pista es borrarla de ahi,
     * <b>desmarcar una pista tuya la destruia</b> — la unica copia que habia.
     * Quedaba una pantalla en la que el mismo boton que apaga una cancion de
     * Forge (reversible, la original sigue en {@code res/music}) borraba para
     * siempre la tuya.
     *
     * <p>Con esto, importar deja <b>dos</b> copias: una aqui, que es tu
     * biblioteca y no la toca nadie, y otra en la del motor, para que suene
     * desde el primer momento. Marcar y desmarcar mueven solo la segunda, asi
     * que ya no hay forma de perder un fichero sin pedirlo: lo unico que borra
     * de verdad es la "x".
     *
     * <p>Va en {@code %APPDATA%\Forge\neo\}, que es NUESTRA carpeta, y no en
     * {@code custom/music/}, que la compartimos con el Forge del usuario.
     */
    public static File libraryDir(final MusicPlaylist list) {
        final File dir = new File(new File(NeoLook.root(), "music"), list.getSubDir());
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Recoge lo que la version anterior dejo suelto.
     *
     * <p>Quien ya hubiera importado algo lo tiene <b>solo</b> en la carpeta del
     * motor. Sin esto, esa pista dejaria de contar como tuya en cuanto la
     * desmarcaras y desapareceria de la lista: pasar a un modelo nuevo no puede
     * costarle al jugador la musica que ya habia traido.
     *
     * <p>Se copia, no se mueve: la de la carpeta del motor tiene que seguir ahi
     * porque es la que dice que esa pista esta marcada.
     */
    private static void adoptStrays(final MusicPlaylist list) {
        final List<String> known = new ArrayList<>();
        for (final File f : sortedAudio(stockDir(list))) {
            known.add(f.getName().toLowerCase(Locale.ROOT));
        }
        for (final File f : sortedAudio(libraryDir(list))) {
            known.add(f.getName().toLowerCase(Locale.ROOT));
        }
        for (final File f : sortedAudio(dir(list))) {
            if (known.contains(f.getName().toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                Files.copy(f.toPath(), new File(libraryDir(list), f.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (final IOException | SecurityException e) {
                System.err.println("[neo] no se ha podido guardar tu pista " + f.getName()
                        + " en la biblioteca: " + e);
            }
        }
    }

    // ---------------------------------------------------------------
    // Que hay
    // ---------------------------------------------------------------

    /**
     * Todas las pistas de una lista: las de Forge y las tuyas, sin repetir.
     *
     * <p>Una pista tuya que se llame igual que una de fabrica tapa a la de
     * fabrica — es lo mismo que hace el motor al buscar la carpeta.
     */
    public static List<Track> tracks(final MusicPlaylist list) {
        adoptStrays(list);

        final List<Track> out = new ArrayList<>();
        final List<String> seen = new ArrayList<>();

        // Primero LAS TUYAS, y "tuya" es la que esta en tu biblioteca — no la
        // que este en la carpeta del motor, que es otra cosa (ahi acaban
        // tambien las de Forge en cuanto las marcas, porque marcar es copiar).
        // Confundir las dos hacia que una pista de fabrica marcada saliera con
        // una "x" de borrar al lado que en realidad solo la desmarcaba.
        for (final File f : sortedAudio(libraryDir(list))) {
            out.add(new Track(f.getName(), f, list, true));
            seen.add(f.getName().toLowerCase(Locale.ROOT));
        }
        // Y una tuya que se llame igual que una de fabrica tapa a la de
        // fabrica: es lo mismo que hace el motor al elegir carpeta.
        for (final File f : sortedAudio(stockDir(list))) {
            if (!seen.contains(f.getName().toLowerCase(Locale.ROOT))) {
                out.add(new Track(f.getName(), f, list, false));
            }
        }
        out.sort((a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        return out;
    }

    private static List<File> sortedAudio(final File dir) {
        final File[] files = dir.listFiles();
        if (files == null) {
            return List.of();
        }
        final List<File> out = new ArrayList<>();
        for (final File f : files) {
            if (f.isFile() && isAudio(f.getName())) {
                out.add(f);
            }
        }
        out.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return out;
    }

    private static boolean isAudio(final String name) {
        final String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a")
                || n.endsWith(".aif") || n.endsWith(".aiff");
    }

    /** Las extensiones que acepta el explorador al importar. */
    public static List<String> audioExtensions() {
        return Arrays.asList("*.mp3", "*.wav", "*.m4a", "*.aif", "*.aiff");
    }

    // ---------------------------------------------------------------
    // Marcar, importar, borrar
    // ---------------------------------------------------------------

    /**
     * Marca o desmarca una pista.
     *
     * <p>Marcar una de fabrica la copia a nuestra carpeta; desmarcarla la borra
     * de ahi. La de Forge no se toca: por eso siempre se puede volver atras
     * desmarcandolo todo.
     */
    public static boolean setEnabled(final Track track, final boolean on) {
        if (track == null) {
            return false;
        }
        final File mine = new File(dir(track.getList()), track.getFileName());
        try {
            if (on) {
                if (!mine.isFile()) {
                    Files.copy(track.getSource().toPath(), mine.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            } else if (mine.isFile() && !remove(mine)) {
                return false;
            }
        } catch (final IOException | SecurityException e) {
            System.err.println("[neo] no se ha podido cambiar la pista " + track.getFileName()
                    + ": " + e);
            return false;
        }
        refresh();
        return true;
    }

    /**
     * Borra un fichero de musica AUNQUE SEA EL QUE ESTA SONANDO.
     *
     * <p>Aqui esta el fallo que se reporto: <i>"no me deja apagar la que
     * esta"</i>. Desmarcar una pista es borrar nuestra copia, y en Windows
     * <b>un fichero abierto no se puede borrar</b>. La pista que suena la tiene
     * abierta un {@code MediaPlayer} de JavaFX, asi que {@code File.delete()}
     * devolvia {@code false}, {@code setEnabled} devolvia {@code false}, la
     * pantalla se repintaba igual y el boton seguia diciendo "Suena". Y como
     * con una sola pista marcada esa suena en bucle para siempre, la unica que
     * de verdad querias apagar era justo la unica que no se podia.
     *
     * <p>Asi que primero se calla la musica — {@code setBackgroundMusic(null)}
     * hace que {@code SoundSystem} llame a {@code dispose()} y suelte el
     * fichero — se borra, y se vuelve a arrancar la lista. Como el
     * {@code dispose()} nativo no es instantaneo, se reintenta un rato corto.
     *
     * <p>Y arrancar de nuevo la lista no es un extra: es lo que hace que
     * apagar la pista que estas oyendo <b>se oiga</b> en el momento, en vez de
     * seguir sonando hasta el final.
     */
    private static boolean remove(final File file) {
        if (file.delete()) {
            return true;
        }
        final MusicPlaylist playing = SoundSystem.instance.getCurrentPlaylist();
        stop();
        for (int i = 0; i < RELEASE_TRIES && file.exists(); i++) {
            if (file.delete()) {
                break;
            }
            try {
                Thread.sleep(RELEASE_WAIT_MS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        final boolean gone = !file.exists();
        if (!gone) {
            System.err.println("[neo] no se ha podido borrar " + file
                    + " (sigue abierto por otro programa?)");
        }
        // La lista ha cambiado: hay que releerla ANTES de volver a sonar.
        refresh();
        if (playing != null) {
            SoundSystem.instance.setBackgroundMusic(playing);
        }
        return gone;
    }

    /** Cuanto se espera a que el reproductor suelte el fichero (300 ms). */
    private static final int RELEASE_TRIES = 10;
    private static final long RELEASE_WAIT_MS = 30;

    /**
     * Trae un fichero de musica, se lo queda y lo deja sonando.
     *
     * <p>Se copia <b>dos veces, a proposito</b>: a tu biblioteca
     * ({@link #libraryDir}), que es donde vive para siempre y de donde no lo
     * quita nada, y a la carpeta del motor, que es lo que hace que suene desde
     * ya sin tener que marcarlo a mano.
     *
     * <p>Nunca se enlaza al fichero original: lo puedes borrar, o traerlo de un
     * USB que luego quitas, y la musica sigue estando.
     */
    public static Track importTrack(final MusicPlaylist list, final File source) {
        if (source == null || !source.isFile()) {
            return null;
        }
        try {
            final File mine = NeoLook.freeName(libraryDir(list), source.getName());
            Files.copy(source.toPath(), mine.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // Y marcada de entrada: traer una cancion y que no suene hasta que
            // ademas la marques seria pedir dos pasos para una sola intencion.
            Files.copy(mine.toPath(), new File(dir(list), mine.getName()).toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            refresh();
            return new Track(mine.getName(), mine, list, true);
        } catch (final IOException | SecurityException e) {
            System.err.println("[neo] no se ha podido importar " + source + ": " + e);
            return null;
        }
    }

    /**
     * Borra del disco una pista tuya. Las de Forge no se pueden borrar.
     *
     * <p>Es <b>lo unico</b> que destruye un fichero tuyo, y por eso esta
     * separado de desmarcar: desmarcar solo la calla, borrar la quita de tu
     * biblioteca. Hay que quitarla de los dos sitios — si se quedara en la
     * carpeta del motor, seguiria sonando una cancion que acabas de borrar.
     */
    public static boolean delete(final Track track) {
        if (track == null || !track.isImported()) {
            return false;
        }
        // Primero la copia que suena: si es esa, el fichero esta abierto y hay
        // que soltarlo antes de poder borrarlo. Ver remove().
        final File playing = new File(dir(track.getList()), track.getFileName());
        boolean gone = !playing.isFile() || remove(playing);
        final File mine = new File(libraryDir(track.getList()), track.getFileName());
        if (mine.isFile() && !mine.delete()) {
            gone = false;
        }
        refresh();
        return gone;
    }

    // ---------------------------------------------------------------
    // El enganche con el motor
    // ---------------------------------------------------------------

    /**
     * Le dice al motor que use nuestro conjunto.
     *
     * <p>Se escribe <b>solo en memoria</b>, como el volumen y el idioma:
     * {@code %APPDATA%\Forge\} lo compartimos con la instalacion normal del
     * usuario y no vamos a cambiarle los ajustes a su Forge de siempre.
     *
     * <p>Y solo si la carpeta existe: el motor descarta un conjunto que no
     * encuentre y se repone a "Default" el solo.
     */
    public static void applyToEngine() {
        try {
            // Con solo crearlas, el conjunto ya existe para el motor.
            dir(MusicPlaylist.MENUS);
            dir(MusicPlaylist.MATCH);
            FModel.getPreferences().setPref(FPref.UI_CURRENT_MUSIC_SET, SET);
            refresh();
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido aplicar el conjunto de musica: " + e);
        }
    }

    /** El motor cachea la lista de ficheros: hay que decirle que ha cambiado. */
    private static void refresh() {
        MusicPlaylist.invalidateMusicPlaylist();
    }

    /**
     * Arranca la musica de los menus.
     *
     * <p>No sonaba nada fuera de la partida, y no porque faltara musica —
     * {@code res/music/menus} trae cuatro pistas — sino porque nadie pedia esa
     * lista. La de partida si sonaba porque la arranca {@code HostedMatch}.
     */
    public static void playMenus() {
        try {
            SoundSystem.instance.setBackgroundMusic(MusicPlaylist.MENUS);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido arrancar la musica del menu: " + e);
        }
    }

    /** Para la musica (al entrar en partida la cambia {@code HostedMatch}). */
    public static void stop() {
        try {
            SoundSystem.instance.setBackgroundMusic(null);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido parar la musica: " + e);
        }
    }
}
