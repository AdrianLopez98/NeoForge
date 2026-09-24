package forge.neo.draft;

import java.util.ArrayList;
import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.model.FModel;
import forge.neo.NeoSettings;

/**
 * El evento de draft (o de sellado): lo que pasa DESPUES de draftear.
 *
 * <p>Te enfrentas, uno a uno y en orden, a los <b>rivales que draftearon
 * contigo</b> — con los mazos que ellos se llevaron, no con preconstruidos.
 * Es la "tanda" (el gauntlet de Forge): cuenta victorias y derrotas, se acaba
 * al jugar contra todos, y se puede <b>repetir</b> ({@link #restart}).
 *
 * <p>Hasta el 23-09-2026 las reglas eran las de Arena y nada mas: dos derrotas
 * y el draft se borraba, sin poder elegir rival ni volver a un pool viejo.
 * Reportado en itch.io por quien juega limitado a diario: Forge deja todo eso,
 * y quitarlo era quitar lo que venia a buscar. Ahora el <b>modo Arena</b> es
 * una opcion de cada evento ({@link #isArena}), apagada de fabrica, que solo
 * se puede cambiar antes de la primera partida: cambiarla a mitad seria
 * reescribir las reglas de algo ya jugado. Las partidas libres (contra un
 * rival elegido, o contra varios al azar) no cuentan para la tanda, igual que
 * en Forge.
 *
 * <p>El estado vive en {@code neo.properties} (NUESTRO fichero, no el de
 * Forge), una linea por draft. Es deliberadamente poca cosa: tres numeros.
 */
public final class DraftRun {

    /** Derrotas que acaban con el draft, en modo Arena. */
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

    /**
     * Leido una vez y guardado aqui: {@link #discard} borra la clave, y el
     * marcador que se pinta DESPUES de la derrota que borra el evento tiene que
     * seguir sabiendo que era Arena (si no, dejaria de verse eliminado).
     */
    private boolean arena;

    private DraftRun(final String name, final Kind kind) {
        this.name = name;
        this.kind = kind;
        this.wins = NeoSettings.getInt(key("wins"), 0);
        this.losses = NeoSettings.getInt(key("losses"), 0);
        this.arena = NeoSettings.getBool(key("arena"), false);
        splitIfNeeded();
    }

    /**
     * Apunta que el mazo y la banda de este evento ya son <b>disjuntos</b>.
     *
     * <p>Lo llama quien escribe el evento ({@code NeoDraft.save}) ANTES de
     * abrir el {@code DraftRun}, para que {@link #splitIfNeeded} no lo toque.
     */
    public static void markSplit(final String eventName, final Kind kind) {
        NeoSettings.setBool(kind.prefix() + "." + eventName + ".split", true);
        NeoSettings.save();
    }

    /**
     * Pasa un draft guardado con el formato viejo al de ahora, una sola vez.
     *
     * <p>Hasta el 23-09-2026 un draft guardaba el pool ENTERO en la banda y en
     * el principal una <b>copia</b> de lo que se jugaba, mientras que el
     * sellado los tenia disjuntos. El editor los trata como disjuntos a los
     * dos, asi que en un draft quitar una carta del mazo la devolvia a una
     * banda donde ya estaba: <b>el pool crecia una carta</b> cada vez. Ahora
     * los dos son iguales, y los drafts de antes se arreglan al abrirlos:
     * lo que esta en el principal se descuenta de la banda.
     *
     * <p>No se deduce del contenido — con copias repetidas no hay forma de
     * saberlo —, se apunta: los drafts nuevos nacen marcados
     * ({@link #markSplit}) y los que no lo estan son de antes.
     */
    private void splitIfNeeded() {
        if (kind != Kind.DRAFT || NeoSettings.getBool(key("split"), false)) {
            return;
        }
        final DeckGroup g = group(name, kind);
        if (g == null || g.getHumanDeck() == null) {
            return;
        }
        final Deck mine = g.getHumanDeck();
        final forge.deck.CardPool side = mine.get(forge.deck.DeckSection.Sideboard);
        if (side != null) {
            for (final java.util.Map.Entry<forge.item.PaperCard, Integer> e : mine.getMain()) {
                if (DraftDeckContext.isBasic(e.getKey())) {
                    continue;
                }
                int left = e.getValue();
                final int exact = Math.min(left, side.count(e.getKey()));
                side.remove(e.getKey(), exact);
                left -= exact;
                // El motor montaba a veces con OTRA impresion de la misma carta.
                final String wanted = DraftDeckContext.normalizedName(e.getKey().getName());
                for (final forge.item.PaperCard other : new java.util.ArrayList<>(side.toFlatList())) {
                    if (left <= 0) {
                        break;
                    }
                    if (DraftDeckContext.normalizedName(other.getName()).equals(wanted)) {
                        side.remove(other, 1);
                        left--;
                    }
                }
            }
            try {
                kind.storage().add(g);
            } catch (final RuntimeException e) {
                System.err.println("[neo] no se ha podido pasar el draft al formato nuevo: " + e);
                return;
            }
        }
        markSplit(name, kind);
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

    /**
     * Si este evento se juega con las reglas de Arena: dos derrotas y se borra.
     * Apagado de fabrica.
     */
    public boolean isArena() {
        return arena;
    }

    /** El modo solo se elige antes de jugar la primera partida de la tanda. */
    public boolean canChangeArena() {
        return wins + losses == 0;
    }

    public void setArena(final boolean on) {
        if (!canChangeArena()) {
            return;
        }
        arena = on;
        NeoSettings.setBool(key("arena"), on);
        NeoSettings.save();
    }

    public boolean isEliminated() {
        return isArena() && losses >= MAX_LOSSES;
    }

    /**
     * La tanda se ha acabado sin eliminacion: en Arena, al ganar a todos; si
     * no, al haber jugado contra todos, gane o pierda.
     */
    public boolean isCompleted() {
        if (isEliminated()) {
            return false;
        }
        final int size = opponents().size();
        return isArena() ? wins >= size : wins + losses >= size;
    }

    /** Vuelve a empezar la tanda con el mismo mazo y los mismos rivales. */
    public void restart() {
        wins = 0;
        losses = 0;
        NeoSettings.setInt(key("wins"), 0);
        NeoSettings.setInt(key("losses"), 0);
        NeoSettings.save();
    }

    /** Los eventos guardados de ese tipo, con su marcador. */
    public static List<DraftRun> saved(final Kind kind) {
        final List<DraftRun> out = new ArrayList<>();
        for (final DeckGroup g : kind.storage()) {
            out.add(new DraftRun(g.getName(), kind));
        }
        return out;
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
     * Anota el resultado de una partida de la tanda.
     *
     * <p>Solo en modo Arena, si con esto se acaba el evento por derrotas, el
     * draft <b>se borra</b>. Fuera de Arena no se borra nunca solo.
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

    /** Borra el draft y su marcador. Sin vuelta atras. */
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
        NeoSettings.set(key("split"), null);
        NeoSettings.set(key("arena"), null);
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
