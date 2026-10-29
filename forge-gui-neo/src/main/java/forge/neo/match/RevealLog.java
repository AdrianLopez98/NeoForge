package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import forge.game.GameLog;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.neo.NeoText;

/**
 * LO QUE SE HA REVELADO, en el registro de la partida.
 *
 * <p>Discord, 20-10-2026: <i>"Is it possible to show the reveal card in the
 * log? I know I'm stupid for skipping too fast or forgetting right away"</i>.
 * El motor escribe "Gandalf searches their library for an instant or sorcery
 * card, reveals it..." pero no QUE carta: la ensenya en el momento
 * ({@code IGuiGame.reveal}) y ya.
 *
 * <p>Cada revelacion pasa por {@code IGuiGame.showRevealedCards} (las dos
 * interfaces la reenvian aqui, {@link #note}) y se apunta con el punto del
 * registro en que ocurrio. Al pintar el registro, {@link #merged} mete una
 * entrada de {@code INFORMATION} en ese punto: "Gandalf revela: Lightning
 * Bolt (123)", con la carta, asi que sale con su miniatura y el nombre
 * enlazado como cualquier otra linea. Solo lo que esta pantalla VIO: el motor
 * revela a cada jugador por su cuenta.
 *
 * <p>Java puro, en el jar: lo usan el escritorio y Android. Se escribe desde el
 * hilo del motor y se lee desde el de interfaz: todo sincronizado.
 */
public final class RevealLog {

    private RevealLog() {
    }

    private static final class Mark {
        final int at;
        final GameLogEntry entry;

        Mark(final int at, final GameLogEntry entry) {
            this.at = at;
            this.entry = entry;
        }
    }

    /** Por registro de partida (cada partida tiene el suyo), y se va con el. */
    private static final Map<GameLog, List<Mark>> BY_LOG = new WeakHashMap<>();

    /** Una revelacion: las cartas que acaba de ensenyar el motor. Nunca lanza. */
    public static void note(final GameLog log, final Iterable<CardView> cards) {
        if (log == null || cards == null) {
            return;
        }
        try {
            // Por dueno: "Gandalf revela: X, Y".
            final Map<PlayerView, List<CardView>> byOwner = new LinkedHashMap<>();
            for (final CardView c : cards) {
                if (c == null) {
                    continue;
                }
                List<CardView> l = byOwner.get(c.getOwner());
                if (l == null) {
                    l = new ArrayList<>();
                    byOwner.put(c.getOwner(), l);
                }
                l.add(c);
            }
            if (byOwner.isEmpty()) {
                return;
            }
            final int at = log.getAllEntries().size();
            synchronized (BY_LOG) {
                List<Mark> marks = BY_LOG.get(log);
                if (marks == null) {
                    marks = new ArrayList<>();
                    BY_LOG.put(log, marks);
                }
                for (final Map.Entry<PlayerView, List<CardView>> e : byOwner.entrySet()) {
                    final StringBuilder names = new StringBuilder();
                    for (final CardView c : e.getValue()) {
                        if (names.length() > 0) {
                            names.append(", ");
                        }
                        final String n = c.getCurrentState() == null ? null
                                : forge.neo.card.CardText.nameOf(c.getCurrentState());
                        names.append(n == null || n.isEmpty() ? c.getName() : n)
                                .append(" (").append(c.getId()).append(')');
                    }
                    final String who = e.getKey() == null ? "?" : PlayerName.of(e.getKey());
                    final String text = NeoText.get("log.revealed", who, names.toString());
                    // Dos avisos seguidos de lo mismo (el motor a veces revela y
                    // vuelve a anotar el monton) no son dos lineas.
                    if (!marks.isEmpty() && marks.get(marks.size() - 1).at == at
                            && text.equals(marks.get(marks.size() - 1).entry.message())) {
                        continue;
                    }
                    marks.add(new Mark(at, new GameLogEntry(GameLogEntryType.INFORMATION, text,
                            e.getValue().get(0))));
                }
            }
        } catch (final RuntimeException ignored) {
            // Un registro sin la revelacion es mejor que una partida que se para.
        }
    }

    /**
     * Las entradas del registro en orden de insercion (lo mas viejo primero,
     * como {@code getAllEntries}), con las revelaciones en su sitio.
     */
    public static List<GameLogEntry> merged(final GameLog log) {
        if (log == null) {
            return Collections.emptyList();
        }
        final List<GameLogEntry> all = log.getAllEntries();
        final List<Mark> marks;
        synchronized (BY_LOG) {
            final List<Mark> m = BY_LOG.get(log);
            marks = m == null ? null : new ArrayList<>(m);
        }
        if (marks == null || marks.isEmpty()) {
            return all;
        }
        final List<GameLogEntry> out = new ArrayList<>(all.size() + marks.size());
        int k = 0;
        for (int i = 0; i <= all.size(); i++) {
            while (k < marks.size() && marks.get(k).at <= i) {
                out.add(marks.get(k++).entry);
            }
            if (i < all.size()) {
                out.add(all.get(i));
            }
        }
        while (k < marks.size()) {
            out.add(marks.get(k++).entry);
        }
        return out;
    }
}
