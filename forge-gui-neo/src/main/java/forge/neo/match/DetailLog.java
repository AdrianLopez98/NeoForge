package forge.neo.match;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import forge.game.GameEntityView;
import forge.game.GameLog;
import forge.game.GameLogEntry;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.combat.CombatView;
import forge.game.phase.PhaseType;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;

/**
 * <b>El registro detallado de la partida</b> (Discord, 13-10-2026: <i>"an
 * extended version of the game log where it tracks every aspect of the game
 * (including phase stops, selected targets, full stack info, etc) ... so we
 * can be certain of bugs happening and better report them"</i>).
 *
 * <p>El registro de la mesa cuenta lo que ha pasado; este cuenta ademas <b>lo
 * que se te pregunto y que contestaste</b>, que es lo que falta para entender
 * un fallo: cada fase con quien juega y si tenias parada ahi, quien tiene la
 * prioridad, el stack ENTERO cada vez que cambia (origen, quien lo controla,
 * texto, objetivos, costes opcionales, disparo o no), el combate, cada aviso
 * del motor con sus botones, tus clics y cada dialogo con sus opciones y tu
 * respuesta. Mas las lineas del registro de siempre, en su sitio.
 *
 * <p>Dos salidas, y la misma clase para las dos interfaces:
 * <ul>
 *   <li><b>PC</b>: a fichero, {@code neo/detailed-game.log} junto a
 *       {@code neo.log}, con la partida anterior al lado (Ajustes → Partida,
 *       {@code -Dneo.detail.log=true}). Lo engancha {@code NeoMatchUI}.</li>
 *   <li><b>Android</b> (el jugador que lo pidio jugaba ahi): en memoria, para
 *       que su panel del registro lo ensenye dentro de la partida
 *       ({@link #entries()}), sin fichero. Lo engancha su {@code AndroidMatchUI}
 *       con {@link #open}, y le pasa sus paradas de fase para que las fases
 *       se apunten solas ({@link #sync}). Su decision 342.</li>
 * </ul>
 *
 * <p>Va en <b>ingles</b> y con los nombres de carta en ingles, porque es para
 * mandarlo en un informe. Solo <b>lee</b> vistas: no cambia nada de la partida,
 * y nada de aqui puede tirarla (un fallo al escribir cierra el fichero y
 * sigue en memoria). Se llama desde el hilo del motor y desde el de interfaz,
 * por eso todo va sincronizado. Java que Android tenga en la 26: nada de
 * {@code List.of} ni {@code strip} en lo que corre alli.
 *
 * <p>Lo que <b>no</b> escribe: los eventos crudos del motor, que nombran
 * cartas ocultas (lo que roba el rival). Lo demas es lo que ya se ve en la
 * mesa.
 */
public final class DetailLog {

    /** La casilla de Ajustes del PC, en {@code neo.properties}. */
    public static final String SETTING = "detailLog";

    static final String NAME = "detailed-game.log";
    static final String PREVIOUS = "detailed-game-previous.log";

    /** Lineas que se guardan en memoria: una partida muy larga no se come el movil. */
    static final int MEMORY = 20000;

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Que es cada linea, para pintarla. */
    public enum Kind {
        /** El rotulo de un turno nuevo. */
        TURN,
        /** Lo que ha hecho el jugador: un clic, OK, una respuesta. */
        ACTION,
        /** Una linea suelta: fase, prioridad, aviso, una linea del registro... */
        INFO,
        /** El detalle de la de arriba: las cartas del stack, las opciones... */
        DETAIL
    }

    /**
     * Una linea, tal cual se ensenya.
     *
     * @param phase la fase en la que se apunto ({@code MAIN1}...), vacia en el detalle
     */
    public record Entry(Kind kind, int turn, String phase, String text) {
    }

    private static DetailLog current;

    private Writer out;
    private final ArrayDeque<Entry> memory = new ArrayDeque<>();
    private final BiFunction<PlayerView, PhaseType, Boolean> stops;
    private int logSeen;
    private int turn = -1;
    private PhaseType phase;
    private String priority;
    private String stack = "";
    private String combat = "";
    private String prompt;
    private String buttons;

    private DetailLog(final Writer out, final BiFunction<PlayerView, PhaseType, Boolean> stops) {
        this.out = out;
        this.stops = stops;
    }

    /** Si esta encendido en el PC: la casilla de Ajustes, o la bandera. */
    public static boolean enabled() {
        final String forced = System.getProperty("neo.detail.log");
        if (forced != null && !forced.isEmpty()) {
            return Boolean.parseBoolean(forced);
        }
        return forge.neo.NeoSettings.getBool(SETTING, false);
    }

    /** El fichero de la partida en curso (o de la ultima). */
    public static File file() {
        return new File(forge.neo.NeoLog.dir(), NAME);
    }

    /**
     * Abre la carpeta del registro en el explorador del sistema, con el fichero
     * marcado si ya existe. En su hilo: un explorador lento no congela Ajustes.
     * Solo PC.
     */
    public static void openFolder() {
        final File f = file();
        final File dir = f.getParentFile();
        final Thread t = new Thread(() -> {
            try {
                if (!dir.isDirectory()) {
                    dir.mkdirs();
                }
                final List<String> cmd;
                if (forge.neo.platform.NeoOs.MAC) {
                    cmd = f.isFile() ? Arrays.asList("open", "-R", f.getPath()) : Arrays.asList("open", dir.getPath());
                } else if (forge.neo.platform.NeoOs.LINUX) {
                    cmd = Arrays.asList("xdg-open", dir.getPath());
                } else {
                    cmd = f.isFile() ? Arrays.asList("explorer", "/select," + f.getPath())
                            : Arrays.asList("explorer", dir.getPath());
                }
                new ProcessBuilder(cmd).start();
            } catch (final IOException | RuntimeException e) {
                System.err.println("[detalle] no se ha podido abrir la carpeta: " + e);
            }
        }, "neo-detail-folder");
        t.setDaemon(true);
        t.start();
    }

    /**
     * PC: empieza el de una partida nueva, si esta encendido, a fichero (la
     * anterior pasa a {@link #PREVIOUS}). Devuelve null si esta apagado.
     */
    static synchronized DetailLog start(final GameView gv) {
        if (!enabled()) {
            closeCurrent();
            return null;
        }
        return open(gv, true, null);
    }

    /**
     * Empieza el de una partida nueva.
     *
     * @param toFile si ademas se escribe el fichero (PC); si no se puede, se
     *               sigue solo en memoria
     * @param stops  si el jugador se para en esa fase con ese jugador en el
     *               turno (null = no se sabe). Con esto, {@link #sync} apunta
     *               las fases solo; sin esto, las apunta quien llame a
     *               {@link #phase}
     */
    public static synchronized DetailLog open(final GameView gv, final boolean toFile,
                                              final BiFunction<PlayerView, PhaseType, Boolean> stops) {
        closeCurrent();
        if (gv == null) {
            return null;
        }
        Writer w = null;
        if (toFile) {
            try {
                final File dir = forge.neo.NeoLog.dir();
                if (dir.isDirectory() || dir.mkdirs()) {
                    final File target = new File(dir, NAME);
                    if (target.isFile()) {
                        final File previous = new File(dir, PREVIOUS);
                        if (previous.isFile()) {
                            previous.delete();
                        }
                        target.renameTo(previous);
                    }
                    w = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(target), StandardCharsets.UTF_8));
                    System.out.println("  [detalle] registro detallado en " + target);
                }
            } catch (final IOException | RuntimeException e) {
                System.err.println("[detalle] no se ha podido abrir el fichero: " + e);
                w = null;
            }
        }
        final DetailLog log = new DetailLog(w, stops);
        try {
            log.header(gv);
        } catch (final RuntimeException e) {
            System.err.println("[detalle] cabecera: " + e);
        }
        current = log;
        return log;
    }

    private static void closeCurrent() {
        if (current != null) {
            current.close();
            current = null;
        }
    }

    private void header(final GameView gv) {
        raw("NeoForge detailed game log", Kind.INFO);
        raw("Version " + forge.neo.NeoVersion.neoVersion()
                + " (Forge " + forge.neo.NeoVersion.forgeVersion() + ") · "
                + System.getProperty("os.name", "?") + " · started "
                + LocalDateTime.now().withNano(0).toString().replace('T', ' '), Kind.DETAIL);
        final forge.game.Game game = gv.getGame();
        final StringBuilder fmt = new StringBuilder("Format: ").append(gv.getGameType());
        if (game != null && game.getRules() != null) {
            final List<String> variants = new ArrayList<>();
            for (final forge.game.GameType t : forge.game.GameType.values()) {
                if (game.getRules().hasAppliedVariant(t)) {
                    variants.add(t.name());
                }
            }
            fmt.append(" · variants: ").append(String.join(", ", variants));
        }
        raw(fmt.toString(), Kind.DETAIL);
        for (final PlayerView p : gv.getPlayers()) {
            raw("Player: " + p.getName() + (p.isAI() ? " (AI)" : " (human)")
                    + " · life " + p.getLife() + commanders(p), Kind.DETAIL);
        }
        raw("Lines: time · turn/phase · what. '>' marks what you did.", Kind.DETAIL);
        raw("", Kind.DETAIL);
    }

    private static String commanders(final PlayerView p) {
        final Iterable<CardView> cmd = p.getCommanders();
        if (cmd == null) {
            return "";
        }
        final List<String> names = new ArrayList<>();
        for (final CardView c : cmd) {
            names.add(card(c));
        }
        return names.isEmpty() ? "" : " · commander " + String.join(" + ", names);
    }

    // ---------------------------------------------------------------
    // Lo que llega de la interfaz
    // ---------------------------------------------------------------

    /**
     * Fase nueva: quien juega, y si el jugador tenia parada ahi.
     *
     * @param stop si la interfaz se para en esta fase (null si no aplica)
     */
    public synchronized void phase(final GameView gv, final Boolean stop) {
        if (gv == null) {
            return;
        }
        syncGameLog(gv.getGameLog());
        writePhase(gv, stop);
        syncRest(gv);
    }

    private void writePhase(final GameView gv, final Boolean stop) {
        final PhaseType p = gv.getPhase();
        if (p == phase && gv.getTurn() == turn) {
            return;
        }
        if (gv.getTurn() != turn) {
            turn = gv.getTurn();
            phase = null;
            line("");
            line("======== TURN " + gv.getTurn() + " · " + name(gv.getPlayerTurn()) + " ========");
            life(gv);
        }
        phase = p;
        line("--- " + phaseName(p) + (stop == null ? "" : stop ? " · your stop: YES" : " · your stop: no (auto-pass)"));
    }

    /** Lo que haya cambiado desde la ultima vez: registro, fase, prioridad, stack, combate. */
    public synchronized void sync(final GameView gv) {
        if (gv == null) {
            return;
        }
        try {
            syncGameLog(gv.getGameLog());
            if (stops != null && gv.getPhase() != null
                    && (gv.getPhase() != phase || gv.getTurn() != turn)) {
                Boolean stop = null;
                try {
                    stop = stops.apply(gv.getPlayerTurn(), gv.getPhase());
                } catch (final RuntimeException ignored) {
                    // sin saberlo, se apunta la fase sin la parada
                }
                writePhase(gv, stop);
            }
            syncRest(gv);
        } catch (final RuntimeException e) {
            // Una vista a medio actualizar no puede tirar la partida.
            line("(could not read the table: " + e + ")");
        }
    }

    private void syncRest(final GameView gv) {
        try {
            syncPriority(gv);
            syncStack(gv);
            syncCombat(gv);
        } catch (final RuntimeException e) {
            line("(could not read the table: " + e + ")");
        }
    }

    private void syncGameLog(final GameLog log) {
        if (log == null) {
            return;
        }
        final int size = log.size();
        if (size < logSeen) {
            // Deshacer recorta el registro del motor.
            line("LOG truncated (undo): " + logSeen + " -> " + size + " entries");
            logSeen = size;
            return;
        }
        if (size == logSeen) {
            return;
        }
        final List<GameLogEntry> all = log.getAllEntries();
        for (int i = logSeen; i < all.size(); i++) {
            final GameLogEntry e = all.get(i);
            line("LOG " + e.type() + ": " + e.message());
        }
        logSeen = all.size();
    }

    private void syncPriority(final GameView gv) {
        String who = null;
        for (final PlayerView p : gv.getPlayers()) {
            if (p.getHasPriority()) {
                who = name(p);
                break;
            }
        }
        if (who != null && !who.equals(priority)) {
            priority = who;
            line("priority: " + who);
        }
    }

    private void syncStack(final GameView gv) {
        final Iterable<StackItemView> items = gv.getStack();
        final List<String> rows = new ArrayList<>();
        int n = 0;
        if (items != null) {
            for (final StackItemView si : items) {
                n++;
                item(rows, si, n == 1 ? "  top " : "  " + n + ".  ", 0);
            }
        }
        final String now = String.join("\n", rows);
        if (now.equals(stack)) {
            return;
        }
        stack = now;
        line(n == 0 ? "STACK: empty" : "STACK (" + n + "):");
        for (final String r : rows) {
            raw(r, Kind.DETAIL);
        }
    }

    private static void item(final List<String> rows, final StackItemView si, final String lead,
                             final int depth) {
        if (si == null || depth > 8) {
            return;
        }
        rows.add(lead + (si.isTrigger() ? "[trigger] " : si.isAbility() ? "[ability] " : "[spell] ")
                + card(si.getSourceCard())
                + " · by " + name(si.getActivatingPlayer())
                + (si.isOptionalTrigger() ? " · optional" : ""));
        rows.add("        text: " + oneLine(si.getText()));
        final List<String> targets = new ArrayList<>();
        if (si.getTargetCards() != null) {
            for (final CardView c : si.getTargetCards()) {
                targets.add(card(c));
            }
        }
        if (si.getTargetPlayers() != null) {
            for (final PlayerView p : si.getTargetPlayers()) {
                targets.add(name(p));
            }
        }
        if (!targets.isEmpty()) {
            rows.add("        targets: " + String.join(", ", targets));
        }
        final String optional = si.getOptionalCostString();
        if (optional != null && !optional.trim().isEmpty()) {
            rows.add("        optional costs: " + oneLine(optional));
        }
        final StackItemView sub = si.getSubInstance();
        if (sub != null) {
            item(rows, sub, "        then ", depth + 1);
        }
    }

    private void syncCombat(final GameView gv) {
        final CombatView c = gv.getCombat();
        final List<String> rows = new ArrayList<>();
        if (c != null && c.getNumAttackers() > 0) {
            for (final CardView a : c.getAttackers()) {
                final StringBuilder sb = new StringBuilder("  ").append(card(a))
                        .append(" -> ").append(entity(c.getDefender(a)));
                final List<String> blockers = new ArrayList<>();
                if (c.getBlockers(a) != null) {
                    for (final CardView b : c.getBlockers(a)) {
                        blockers.add(card(b));
                    }
                }
                if (!blockers.isEmpty()) {
                    sb.append(" · blocked by ").append(String.join(", ", blockers));
                }
                rows.add(sb.toString());
            }
        }
        final String now = String.join("\n", rows);
        if (now.equals(combat)) {
            return;
        }
        combat = now;
        if (rows.isEmpty()) {
            line("COMBAT: over");
        } else {
            line("COMBAT:");
            for (final String r : rows) {
                raw(r, Kind.DETAIL);
            }
        }
    }

    private void life(final GameView gv) {
        final List<String> parts = new ArrayList<>();
        for (final PlayerView p : gv.getPlayers()) {
            parts.add(name(p) + " " + p.getLife() + " life, hand " + p.getZoneSize(forge.game.zone.ZoneType.Hand)
                    + ", library " + p.getZoneSize(forge.game.zone.ZoneType.Library));
        }
        raw("  " + String.join(" | ", parts), Kind.DETAIL);
    }

    /** El aviso del motor (lo que dice la barra de los botones). */
    public synchronized void prompt(final PlayerView who, final String message, final CardView about) {
        final String text = oneLine(message) + (about == null ? "" : " [card: " + card(about) + "]");
        if (Objects.equals(text, prompt)) {
            return;
        }
        prompt = text;
        line("PROMPT" + (who == null ? "" : " (" + name(who) + ")") + ": " + text);
    }

    /** Los dos botones, solo cuando cambian. */
    public synchronized void buttons(final String ok, final String cancel, final boolean okOn,
                                     final boolean cancelOn) {
        final String text = "[" + label(ok, okOn) + "] [" + label(cancel, cancelOn) + "]";
        if (text.equals(buttons)) {
            return;
        }
        buttons = text;
        line("buttons: " + text);
    }

    private static String label(final String s, final boolean on) {
        final String t = s == null || s.trim().isEmpty() ? "-" : oneLine(s);
        return on ? t : t + " (off)";
    }

    /** Lo que ha hecho el jugador: un clic, OK, Cancelar... */
    public synchronized void did(final String what) {
        line("> " + what);
    }

    /** Un dialogo: la pregunta, las opciones y lo que se contesto (null = nada / cancelado). */
    public synchronized void asked(final String question, final List<String> options, final String answer) {
        line("DIALOG: " + oneLine(question));
        if (options != null && !options.isEmpty()) {
            final int max = 40;
            final List<String> shown = options.size() > max ? options.subList(0, max) : options;
            raw("        options: " + String.join(" | ", shown)
                    + (options.size() > max ? " | ... (" + options.size() + " in all)" : ""), Kind.DETAIL);
        }
        line("> answered: " + (answer == null ? "(nothing / cancelled)" : answer));
    }

    /** Se acabo la partida: lo apunta y cierra el fichero. Lo de memoria se queda. */
    public synchronized void end(final GameView gv) {
        sync(gv);
        line("");
        line("======== GAME OVER ========");
        if (gv != null) {
            life(gv);
            if (gv.getGame() != null && gv.getGame().getOutcome() != null) {
                raw("  " + gv.getGame().getOutcome().getWinCondition() + " · winner: "
                        + (gv.getGame().getOutcome().getWinningLobbyPlayer() == null ? "none (draw)"
                        : gv.getGame().getOutcome().getWinningLobbyPlayer().getName()), Kind.DETAIL);
            }
        }
        close();
    }

    /** Lo apuntado hasta ahora, en orden (lo mas viejo primero). Una copia. */
    public synchronized List<Entry> entries() {
        return new ArrayList<>(memory);
    }

    /** Todo como texto, como iria en el fichero pero sin la hora: para copiarlo. */
    public synchronized String text() {
        final StringBuilder sb = new StringBuilder();
        for (final Entry e : memory) {
            if (!e.phase().isEmpty()) {
                sb.append('T').append(e.turn()).append(' ').append(e.phase()).append(" · ");
            }
            sb.append(e.text()).append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------

    public static String card(final CardView c) {
        return c == null ? "?" : c.toString();
    }

    public static String name(final PlayerView p) {
        return p == null ? "?" : p.getName();
    }

    private static String entity(final GameEntityView e) {
        if (e instanceof CardView c) {
            return card(c);
        }
        if (e instanceof PlayerView p) {
            return name(p);
        }
        return String.valueOf(e);
    }

    private static String phaseName(final PhaseType p) {
        return p == null ? "?" : p.name();
    }

    private static String oneLine(final String s) {
        return s == null ? "" : s.replace("\r", "").replace('\n', ' ').trim();
    }

    private void line(final String text) {
        // El rotulo del turno va sin prefijo: ya dice el turno.
        final boolean placed = turn >= 0 && !text.isEmpty() && !text.startsWith("========");
        final String where = placed ? "T" + turn + " " + (phase == null ? "" : phaseName(phase)) + " · " : "";
        write(LocalDateTime.now().format(CLOCK) + "  " + where + text);
        if (!text.isEmpty()) {
            final Kind kind = text.startsWith("======== TURN") ? Kind.TURN
                    : text.startsWith("> ") ? Kind.ACTION : Kind.INFO;
            remember(new Entry(kind, Math.max(turn, 0),
                    placed && phase != null && kind != Kind.TURN ? phaseName(phase) : "", text));
        }
    }

    private void raw(final String text, final Kind kind) {
        write(text);
        if (!text.isEmpty()) {
            remember(new Entry(kind, Math.max(turn, 0), "", text));
        }
    }

    private void remember(final Entry e) {
        memory.addLast(e);
        while (memory.size() > MEMORY) {
            memory.removeFirst();
        }
    }

    private void write(final String text) {
        if (out == null) {
            return;
        }
        try {
            out.write(text);
            out.write(System.lineSeparator());
            // A cada linea: si la partida se cuelga o revienta, lo ultimo es
            // justo lo que hay que leer.
            out.flush();
        } catch (final IOException e) {
            System.err.println("[detalle] no se puede escribir, se cierra el fichero: " + e);
            close();
        }
    }

    private void close() {
        final Writer w = out;
        out = null;
        if (w != null) {
            try {
                w.close();
            } catch (final IOException ignored) {
                // nada: ya no se iba a escribir mas
            }
        }
    }
}
