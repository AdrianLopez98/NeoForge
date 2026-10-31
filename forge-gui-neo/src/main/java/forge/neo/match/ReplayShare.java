package forge.neo.match;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import forge.gamemodes.replay.ReplayStore;

/**
 * <b>Compartir repeticiones</b> (31-10-2026): pasar una a otro jugador y ver
 * la suya.
 *
 * <p>Una repeticion es UN fichero ({@code .frp}) que se basta solo: la
 * cabecera (version de Forge, fecha, "Jugador vs Ajani") y los mensajes que
 * habria recibido un espectador. Asi que compartir es copiarlo e importar es
 * copiarlo de vuelta, comprobando antes que lo sea.
 *
 * <p>Lo unico que no es copiar: las importadas van a su propia carpeta
 * ({@code replays/imported}). Forge borra las mas viejas de {@code replays/}
 * cuando pasan de 20 ({@code ReplayStore.prune}), y la que te ha mandado un
 * amigo no puede caerse por jugar tu veinte partidas mas. Esa carpeta no la
 * mira {@code ReplayStore}: solo lista {@code .frp} sueltos.
 *
 * <p>Leerla es seguro igual que entrar en la sala de un desconocido: pasa por
 * el mismo lector de la partida en red, que solo admite las clases del
 * protocolo y pone tope al tamanyo.
 *
 * <p>Clase pura, sin interfaz: la usan el escritorio ({@code ReplaysScreen})
 * y Android. La vigila {@code replaycheck}. Nada de API que Android 26 no
 * tenga.
 */
public final class ReplayShare {

    /** La cabecera de {@code ReplayRecorder}; en {@code ReplayStore} no son publicas. */
    private static final int MAGIC = 0x46525031; // "FRP1"
    private static final int FORMAT = 2;
    public static final String EXTENSION = ".frp";

    /** Lo mas grande que se importa. Una partida de 16 turnos son ~250 KB. */
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    private ReplayShare() {
    }

    /** Una repeticion de la lista: grabada aqui o importada. */
    public static final class Entry {
        public final File file;
        public final String title;
        public final long startMillis;
        public final String forgeVersion;
        public final boolean imported;

        Entry(final File file, final String title, final long startMillis, final String forgeVersion,
                final boolean imported) {
            this.file = file;
            this.title = title;
            this.startMillis = startMillis;
            this.forgeVersion = forgeVersion;
            this.imported = imported;
        }

        /** Grabada con otro Forge: puede no abrirse (viajan objetos del motor). */
        public boolean otherVersion() {
            return !forgeVersion.equals(forge.util.BuildInfo.getVersionString());
        }

        boolean sameGame(final Entry o) {
            return startMillis == o.startMillis && title.equals(o.title) && forgeVersion.equals(o.forgeVersion);
        }
    }

    /** Como ha ido un import. */
    public enum Outcome {
        /** Anyadida a la lista. */
        ADDED,
        /** Ya estaba (grabada aqui o importada antes): no se duplica. */
        ALREADY,
        /** No es una repeticion de Forge. */
        NOT_REPLAY,
        /** No se ha podido leer o escribir. */
        FAILED
    }

    public static final class Result {
        public final Outcome outcome;
        /** La que queda en la lista (la nueva, o la que ya estaba); null si no hay. */
        public final Entry entry;

        Result(final Outcome outcome, final Entry entry) {
            this.outcome = outcome;
            this.entry = entry;
        }

        /** Lo que se le dice al jugador, en su idioma. Lo usan el escritorio y Android. */
        public String message() {
            switch (outcome) {
                case ADDED:
                    return forge.neo.NeoText.get(entry != null && entry.otherVersion()
                            ? "replays.importedOtherVersion" : "replays.importDone");
                case ALREADY:
                    return forge.neo.NeoText.get("replays.importAlready");
                case NOT_REPLAY:
                    return forge.neo.NeoText.get("replays.importNotReplay");
                default:
                    return forge.neo.NeoText.get("replays.importFailed");
            }
        }
    }

    /** Donde viven las importadas. Se crea si no esta. */
    public static File importedDir() {
        final File dir = new File(ReplayStore.directory(), "imported");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Todas, las grabadas y las importadas, la partida mas reciente primero. */
    public static List<Entry> list() {
        final List<Entry> out = new ArrayList<>();
        for (final ReplayStore.Info i : ReplayStore.list()) {
            out.add(new Entry(i.file, i.title, i.startMillis, i.forgeVersion, false));
        }
        final File[] mine = importedDir().listFiles((d, n) -> n.endsWith(EXTENSION));
        if (mine != null) {
            for (final File f : mine) {
                final Entry e = read(f, true);
                if (e != null) {
                    out.add(e);
                }
            }
        }
        out.sort(Comparator.comparingLong((Entry e) -> e.startMillis).reversed());
        return out;
    }

    /** La cabecera de un fichero, o null si no es una repeticion. */
    public static Entry read(final File f, final boolean imported) {
        try (InputStream in = new FileInputStream(f)) {
            return read(in, f, imported);
        } catch (final IOException e) {
            return null;
        }
    }

    private static Entry read(final InputStream raw, final File f, final boolean imported) {
        try {
            final DataInputStream in = new DataInputStream(new BufferedInputStream(raw, 1024));
            if (in.readInt() != MAGIC || in.readInt() != FORMAT) {
                return null;
            }
            final String version = in.readUTF();
            final long start = in.readLong();
            final String title = in.readUTF();
            return new Entry(f, title, start, version, imported);
        } catch (final IOException | RuntimeException e) {
            return null;
        }
    }

    /** Importar un fichero del disco (el escritorio). */
    public static Result importFile(final File source) {
        try (InputStream in = new FileInputStream(source)) {
            return importFrom(in);
        } catch (final IOException e) {
            return new Result(Outcome.FAILED, null);
        }
    }

    /**
     * Importar lo que llegue por un flujo (Android lo abre con su selector de
     * ficheros). Se copia a un temporal, se comprueba la cabecera y solo
     * entonces entra en la lista: nunca queda a medias un fichero roto.
     */
    public static Result importFrom(final InputStream source) {
        final File dir = importedDir();
        File tmp = null;
        try {
            tmp = File.createTempFile("import-", ".tmp", dir);
            try (OutputStream out = new FileOutputStream(tmp)) {
                final byte[] buf = new byte[64 * 1024];
                long total = 0;
                int n;
                while ((n = source.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_BYTES) {
                        return new Result(Outcome.NOT_REPLAY, null);
                    }
                    out.write(buf, 0, n);
                }
            }
            final Entry header = read(tmp, true);
            if (header == null) {
                return new Result(Outcome.NOT_REPLAY, null);
            }
            for (final Entry e : list()) {
                if (e.sameGame(header)) {
                    return new Result(Outcome.ALREADY, e);
                }
            }
            final File dest = freeName(dir, header);
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tmp = null;
            return new Result(Outcome.ADDED, read(dest, true));
        } catch (final IOException | RuntimeException e) {
            return new Result(Outcome.FAILED, null);
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    /** Quitar una importada. Las grabadas aqui las gestiona Forge: esas no. */
    public static boolean remove(final Entry e) {
        return e != null && e.imported && e.file.delete();
    }

    /**
     * El nombre con el que sale al compartirla: quien la recibe ve de que va
     * ("Ana vs Ajani 2026-10-31 1643.frp") en vez de "replay-20261031-164301".
     */
    public static String shareName(final Entry e) {
        final String when = new SimpleDateFormat("yyyy-MM-dd HHmm", Locale.ROOT).format(new Date(e.startMillis));
        String name = (e.title == null ? "" : e.title).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "").trim();
        if (name.length() > 80) {
            name = name.substring(0, 80).trim();
        }
        return (name.isEmpty() ? "replay" : name) + " " + when + EXTENSION;
    }

    private static File freeName(final File dir, final Entry e) {
        final String base = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date(e.startMillis));
        File f = new File(dir, "imported-" + base + EXTENSION);
        for (int i = 2; f.exists(); i++) {
            f = new File(dir, "imported-" + base + "-" + i + EXTENSION);
        }
        return f;
    }
}
