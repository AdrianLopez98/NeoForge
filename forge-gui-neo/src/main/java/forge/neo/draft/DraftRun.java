package forge.neo.draft;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.model.FModel;
import forge.neo.NeoSettings;

/**
 * El evento de draft: lo que pasa DESPUES de draftear.
 *
 * <p>Draftear un mazo y no hacer nada con el no es jugar a un draft. En Arena
 * el draft es un <b>evento</b>: montas el mazo y lo llevas hasta que se rompe.
 * Aqui es igual, con las reglas que se pidieron:
 *
 * <ul>
 *   <li>Te enfrentas, uno a uno, a los <b>siete rivales que draftearon contigo</b>
 *       — con los mazos que ellos se llevaron, no con preconstruidos.</li>
 *   <li><b>Dos derrotas y se acabo</b>: el draft se borra y hay que empezar
 *       otro.</li>
 *   <li>Ganar a los siete es completarlo.</li>
 * </ul>
 *
 * <p>Eso es lo que le da peso a cada pick: un mazo malo se nota a la segunda
 * partida. Sin evento, el draft es un generador de mazos.
 *
 * <p>El estado vive en {@code neo.properties} (NUESTRO fichero, no el de
 * Forge), una linea por draft. Es deliberadamente poca cosa: tres numeros.
 */
public final class DraftRun {

    /** Derrotas que acaban con el draft. */
    public static final int MAX_LOSSES = 2;

    /**
     * De que evento se trata.
     *
     * <p>Un draft y un sellado son <b>el mismo evento</b>: un pool que has
     * montado tu, siete rivales con el suyo, dos derrotas y se acaba. Lo unico
     * que cambia es de donde salieron las cartas y en que almacen de Forge
     * viven. Por eso esto es un parametro y no una clase aparte: duplicar
     * doscientas lineas para cambiar dos seria duplicar tambien los arreglos.
     */
    public enum Kind {
        DRAFT("draft"), SEALED("sealed");

        private final String prefix;

        Kind(final String prefix) {
            this.prefix = prefix;
        }

        /** El almacen de Forge donde vive este tipo de evento. */
        public forge.util.storage.IStorage<DeckGroup> storage() {
            return this == SEALED ? FModel.getDecks().getSealed() : FModel.getDecks().getDraft();
        }

        String prefix() {
            return prefix;
        }
    }

    private final String name;
    private final Kind kind;
    private int wins;
    private int losses;

    private DraftRun(final String name, final Kind kind) {
        this.name = name;
        this.kind = kind;
        this.wins = NeoSettings.getInt(key("wins"), 0);
        this.losses = NeoSettings.getInt(key("losses"), 0);
    }

    /** El evento de un draft guardado, con lo que llevara jugado. */
    public static DraftRun of(final String draftName) {
        return new DraftRun(draftName, Kind.DRAFT);
    }

    /** Lo mismo, para un sellado. */
    public static DraftRun of(final String eventName, final Kind kind) {
        return new DraftRun(eventName, kind);
    }

    public Kind getKind() {
        return kind;
    }

    /** El draft en curso, o null si no hay ninguno. */
    public static DraftRun current() {
        return current(Kind.DRAFT);
    }

    /** El evento en curso de ese tipo, o null. */
    public static DraftRun current(final Kind kind) {
        final String name = NeoSettings.get(currentKey(kind), null);
        if (name == null || name.isBlank() || group(name, kind) == null) {
            return null;
        }
        return new DraftRun(name, kind);
    }

    /** Marca este draft como el que se esta jugando. */
    public void makeCurrent() {
        NeoSettings.set(currentKey(kind), name);
        NeoSettings.save();
    }

    private static String currentKey(final Kind kind) {
        return kind.prefix() + "Current";
    }

    private String key(final String field) {
        return kind.prefix() + "." + name + "." + field;
    }

    public String getName() {
        return name;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    /** Cuantos rivales quedan por batir. */
    public int getRemaining() {
        return Math.max(0, opponents().size() - wins);
    }

    public boolean isEliminated() {
        return losses >= MAX_LOSSES;
    }

    public boolean isCompleted() {
        return !isEliminated() && wins >= opponents().size();
    }

    public boolean isOver() {
        return isEliminated() || isCompleted();
    }

    /** Tu mazo del draft. */
    public Deck getDeck() {
        final DeckGroup g = group(name, kind);
        return g == null ? null : g.getHumanDeck();
    }

    /** Los mazos que draftearon los rivales. */
    public List<Deck> opponents() {
        final DeckGroup g = group(name, kind);
        return g == null ? new ArrayList<>() : new ArrayList<>(g.getAiDecks());
    }

    /**
     * Contra quien toca ahora.
     *
     * <p>En el orden en que se sentaron a draftear. Es el mismo orden en que
     * les pasaste los sobres, asi que el rival de al lado juega con lo que tu
     * le dejaste — que es la gracia.
     */
    public Deck nextOpponent() {
        final List<Deck> all = opponents();
        return all.isEmpty() ? null : all.get(Math.min(wins + losses, all.size() - 1));
    }

    /**
     * Anota el resultado de una partida.
     *
     * <p>Si con esto se acaba el evento por derrotas, el draft <b>se borra</b>:
     * es lo que se pidio y es lo que hace que arriesgar en los picks tenga
     * consecuencia.
     *
     * @return true si el evento sigue vivo
     */
    public boolean record(final boolean won) {
        if (won) {
            wins++;
        } else {
            losses++;
        }
        NeoSettings.setInt(key("wins"), wins);
        NeoSettings.setInt(key("losses"), losses);
        NeoSettings.save();
        if (isEliminated()) {
            discard();
            return false;
        }
        return !isCompleted();
    }

    /** Borra el draft y su marcador. Sin vuelta atras, como en Arena. */
    public void discard() {
        // Comprobar que existe antes: el almacen de Forge lanza NPE al borrar
        // algo que no esta, y aqui se llega tambien desde una limpieza.
        if (group(name, kind) != null) {
            try {
                kind.storage().delete(name);
            } catch (final RuntimeException e) {
                System.err.println("[neo] no se ha podido borrar el draft: " + e);
            }
        }
        NeoSettings.set(key("wins"), null);
        NeoSettings.set(key("losses"), null);
        if (name.equals(NeoSettings.get(currentKey(kind), null))) {
            NeoSettings.set(currentKey(kind), null);
        }
        NeoSettings.save();
    }

    private static DeckGroup group(final String name, final Kind kind) {
        for (final DeckGroup g : kind.storage()) {
            if (g.getName().equals(name)) {
                return g;
            }
        }
        return null;
    }
}
