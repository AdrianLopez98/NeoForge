package forge.neo.match;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import forge.gamemodes.replay.ReplayStore;
import forge.neo.NeoSettings;
import forge.neo.tutorial.TutorialLesson;
import forge.neo.tutorial.TutorialState;

/**
 * <b>Las repeticiones</b> ({@code run.cmd replaycheck}, Forge #12255).
 *
 * <p>Que nuestras partidas se graban con el ajuste encendido, que NO se graban
 * con el apagado (el ajuste vive en {@code neo.properties} y se pasa al motor
 * en {@code NeoGame.applyEnginePrefs}: si alguien lo mueve detras de
 * {@code openView}, la grabacion ya habria empezado), que la lista las lee, y
 * que los textos en ingles de {@code ReplayPlayer} se traducen. Si Forge
 * cambia esos textos, los botones saldrian en ingles: esto se pone en rojo.
 *
 * <p>Verla no se prueba aqui (hace falta la mesa): es
 * {@code run.cmd ui --replays -Dneo.replay.play=true}.
 */
public final class ReplayCheck {

    private ReplayCheck() {
    }

    public static void run() {
        int bad = 0;
        final boolean saved = NeoSettings.recordReplays();
        ReplayStore.Info rec = null;
        try {
            NeoSettings.setBool(NeoSettings.RECORD_REPLAYS, true);
            final long t0 = System.currentTimeMillis();
            play();
            rec = newest();
            bad += check(rec != null && rec.file.lastModified() >= t0 - 1000,
                    "con el ajuste encendido, la partida se graba: " + (rec == null ? "(nada)" : rec.file.getName()));
            bad += check(rec != null && rec.title.contains(" vs "),
                    "y la lista la lee, con quienes jugaban: " + (rec == null ? "" : rec.title));

            NeoSettings.setBool(NeoSettings.RECORD_REPLAYS, false);
            play();
            final ReplayStore.Info off = newest();
            // Por NOMBRE y no por fecha: la partida de antes puede seguir
            // escribiendo en su fichero si se paso de su tiempo.
            bad += check(off == null || rec != null && off.file.getName().equals(rec.file.getName()),
                    "con el ajuste apagado, no se graba: " + (off == null ? "(nada)" : off.file.getName()));
        } finally {
            // Solo en memoria: nunca se guarda desde aqui.
            NeoSettings.setBool(NeoSettings.RECORD_REPLAYS, saved);
        }

        bad += share(rec);

        // Los textos de ReplayPlayer, traducidos (y con su numero).
        bad += check(!ReplayText.label("Pause").equals("Pause") || isEnglish(), "Pause → " + ReplayText.label("Pause"));
        bad += check(ReplayText.label("Speed 4x").contains("4") && !ReplayText.label("Speed 4x").startsWith("Speed 4x"),
                "Speed 4x → " + ReplayText.label("Speed 4x"));
        bad += check(ReplayText.prompt("Replaying Speed 2x.").contains("2")
                        && !ReplayText.prompt("Replaying Speed 2x.").startsWith("Replaying"),
                "Replaying Speed 2x. → " + ReplayText.prompt("Replaying Speed 2x."));
        bad += check(ReplayText.prompt("Replay paused. Speed 8x.").contains("8")
                        && !ReplayText.prompt("Replay paused. Speed 8x.").equals("Replay paused. Speed 8x."),
                "Replay paused. Speed 8x. → " + ReplayText.prompt("Replay paused. Speed 8x."));
        bad += check(!ReplayText.prompt("Replay finished.").equals("Replay finished.") || isEnglish(),
                "Replay finished. → " + ReplayText.prompt("Replay finished."));
        bad += check(ReplayText.label("Algo nuevo").equals("Algo nuevo"),
                "lo que no se conoce, tal cual");

        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", 15 - bad, bad);
        if (bad > 0) {
            throw new IllegalStateException(bad + " comprobacion(es) de las repeticiones han fallado");
        }
    }

    /**
     * Compartir e importar ({@link ReplayShare}): la grabada no se duplica, una
     * de otra partida entra en {@code imported/} y sale en la lista, dos veces
     * no entra dos, lo que no es una repeticion no entra, y quitarla la quita
     * (solo a las importadas). Seis comprobaciones.
     */
    private static int share(final ReplayStore.Info rec) {
        if (rec == null) {
            return check(false, "compartir: no hay partida grabada con que probar") * 6;
        }
        int bad = 0;
        java.io.File other = null;
        java.io.File junk = null;
        ReplayShare.Result added = null;
        try {
            final ReplayShare.Result again = ReplayShare.importFile(rec.file);
            bad += check(again.outcome == ReplayShare.Outcome.ALREADY,
                    "importar la que ya esta grabada: no se duplica (" + again.outcome + ")");

            // "La de un amigo": la misma partida con otra fecha en la cabecera.
            other = java.io.File.createTempFile("replaycheck-", ".frp");
            otherGame(rec.file, other);
            added = ReplayShare.importFile(other);
            final ReplayShare.Result fadded = added;
            final boolean listed = added.entry != null && ReplayShare.list().stream()
                    .anyMatch(e -> e.imported && e.file.equals(fadded.entry.file));
            bad += check(added.outcome == ReplayShare.Outcome.ADDED && listed
                            && added.entry.file.getParentFile().equals(ReplayShare.importedDir()),
                    "importar otra: entra en imported/ y sale en la lista (" + added.outcome + ")");
            bad += check(ReplayShare.importFile(other).outcome == ReplayShare.Outcome.ALREADY,
                    "importarla dos veces: una sola");

            junk = java.io.File.createTempFile("replaycheck-", ".frp");
            java.nio.file.Files.write(junk.toPath(), "no soy una repeticion".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            bad += check(ReplayShare.importFile(junk).outcome == ReplayShare.Outcome.NOT_REPLAY,
                    "un fichero que no lo es: no entra");

            final String name = ReplayShare.shareName(ReplayShare.read(rec.file, false));
            bad += check(name.endsWith(ReplayShare.EXTENSION) && name.contains(" vs "),
                    "el nombre al compartir se entiende: " + name);

            final boolean gone = ReplayShare.remove(added.entry) && !added.entry.file.exists();
            final boolean recordedKept = !ReplayShare.remove(ReplayShare.read(rec.file, false)) && rec.file.exists();
            bad += check(gone && recordedKept, "quitar: la importada se va, la grabada no se toca");
        } catch (final java.io.IOException e) {
            bad += check(false, "compartir: " + e);
        } finally {
            if (other != null) {
                other.delete();
            }
            if (junk != null) {
                junk.delete();
            }
            if (added != null && added.entry != null) {
                added.entry.file.delete();
            }
        }
        return bad;
    }

    /** Copia {@code from} cambiando la fecha de la cabecera: para el formato, otra partida. */
    private static void otherGame(final java.io.File from, final java.io.File to) throws java.io.IOException {
        try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.FileInputStream(from));
                java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.FileOutputStream(to))) {
            out.writeInt(in.readInt());
            out.writeInt(in.readInt());
            out.writeUTF(in.readUTF());
            out.writeLong(in.readLong() - 86_400_000L);
            out.writeUTF(in.readUTF());
            in.transferTo(out);
        }
    }

    private static boolean isEnglish() {
        return forge.neo.NeoLanguage.current().startsWith("en");
    }

    private static ReplayStore.Info newest() {
        final List<ReplayStore.Info> all = ReplayStore.list();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Una partida corta con la interfaz contestando sola: la IA tiene 20 vidas y le pegan 7 al turno. */
    private static void play() {
        final TutorialLesson lesson = position();
        NeoGame.playTutorial(lesson, new TutorialState(lesson.getState()), NeoMatchUI.Mode.AUTO_PLAY, 30,
                null, false, ui -> { });
    }

    private static int check(final boolean ok, final String what) {
        System.out.println("  [" + (ok ? "OK " : "MAL") + "] " + what);
        return ok ? 0 : 1;
    }

    private static TutorialLesson position() {
        final List<String> state = Arrays.asList(
                "turn=3",
                "activeplayer=human",
                "activephase=MAIN1",
                "humanlife=20",
                "ailife=3",
                "humanbattlefield=Plains;Savannah Lions;Savannah Lions",
                "humanhand=",
                "humanlibrary=Plains;Plains;Plains",
                "humangraveyard=", "humanexile=", "humancommand=",
                "aibattlefield=",
                "aihand=",
                "ailibrary=Plains;Plains;Plains",
                "aigraveyard=", "aiexile=", "aicommand=");
        return new TutorialLesson("replay", state, List.of());
    }
}
