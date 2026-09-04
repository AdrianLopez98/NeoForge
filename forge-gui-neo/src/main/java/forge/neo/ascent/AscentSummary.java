package forge.neo.ascent;

import java.util.List;

import forge.deck.Deck;
import forge.item.PaperCard;

/**
 * Como acabo la run, ya en frio.
 *
 * <h2>Por que existe una clase para esto</h2>
 *
 * <p>Por el <b>orden</b>. La run se borra en cuanto termina —y tiene que
 * borrarse ahi: si se dejara para el "volver al menu", cerrar el juego en la
 * pantalla del resumen la dejaria guardada y el modo ofreceria continuar una
 * partida que ya se perdio, que es lo unico que un roguelike no puede
 * permitirse—. Pero {@code discard()} se lleva por delante el {@code .dck} y el
 * bloque {@code ascent.*} entero, o sea <b>todo lo que el resumen tiene que
 * ensenyar</b>. Asi que se hace una foto ANTES, y la pantalla pinta la foto.
 *
 * <p>La otra mitad de por que es una clase aparte y no campos sueltos en la
 * pantalla: sin JavaFX se puede comprobar sin ventana, que es la regla de la
 * que vive este modo.
 *
 * <h2>Y por que el resumen importa</h2>
 *
 * <p>El criterio de exito declarado del modo es <i>«apetece volver a
 * intentarlo despues de perder»</i>, y hasta ahora perder te devolvia al menu
 * <b>sin decir nada</b>: ni cuanto habias subido, ni con que mazo, ni que
 * llevabas. El momento donde se decide si hay otra run estaba en blanco.
 */
public final class AscentSummary {

    private final boolean won;
    private final AscentRun.Mode mode;
    private final int act;
    private final int cleared;
    private final int life;
    private final int maxLife;
    private final int credits;
    private final int ascension;
    private final boolean unlocked;
    private final List<AscentRelic> relics;
    private final List<PaperCard> deck;
    private final String commander;

    /**
     * Los hitos que esta run acaba de conseguir.
     *
     * <p>No entra por el constructor de {@link #of}: se calcula <b>a partir de
     * la foto</b> ({@link AscentUnlocks#record}), asi que en ese momento
     * todavia no existe. Por eso hay {@link #withFeats}, que devuelve una copia
     * — la foto sigue siendo inmutable, que es lo que la hace fiable despues de
     * que la run se haya borrado.
     */
    private final List<AscentFeat> feats;

    private AscentSummary(final boolean won, final AscentRun.Mode mode, final int act,
                          final int cleared, final int life, final int maxLife,
                          final int credits, final int ascension, final boolean unlocked,
                          final List<AscentRelic> relics, final List<PaperCard> deck,
                          final String commander, final List<AscentFeat> feats) {
        this.won = won;
        this.mode = mode;
        this.act = act;
        this.cleared = cleared;
        this.life = life;
        this.maxLife = maxLife;
        this.credits = credits;
        this.ascension = ascension;
        this.unlocked = unlocked;
        this.relics = relics;
        this.deck = deck;
        this.commander = commander;
        this.feats = feats;
    }

    /**
     * La misma foto, con los hitos que la run acaba de conseguir apuntados.
     *
     * <p>Se llama justo despues de {@link AscentUnlocks#record}, que es quien
     * los calcula leyendo esta misma foto.
     */
    public AscentSummary withFeats(final List<AscentFeat> earned) {
        return new AscentSummary(won, mode, act, cleared, life, maxLife, credits,
                ascension, unlocked, relics, deck, commander,
                earned == null ? List.of() : List.copyOf(earned));
    }

    /**
     * La foto de la run, tal y como esta ahora mismo.
     *
     * <p><b>Hay que llamarlo antes de {@code run.discard()}</b>. Despues no hay
     * nada que fotografiar.
     *
     * @param won      si la run se completo (los tres actos)
     * @param unlocked si esa victoria ha subido la Ascension maxima
     */
    public static AscentSummary of(final AscentRun run, final boolean won, final boolean unlocked) {
        final Deck deck = AscentDecks.load(run);
        // El orden canonico, el mismo de todas las vistas del mazo.
        final List<PaperCard> cards = AscentDecks.sortedByCost(deck);
        String commander = null;
        if (deck != null && deck.has(forge.deck.DeckSection.Commander)) {
            // El comandante no esta en el principal: vive en su propia seccion,
            // y es la carta con la que se cuenta la run en modo Commander.
            for (final java.util.Map.Entry<PaperCard, Integer> e
                    : deck.get(forge.deck.DeckSection.Commander)) {
                commander = e.getKey().getName();
                break;
            }
        }
        return new AscentSummary(won, run.getMode(), run.getAct(), run.getCleared(),
                run.getLife(), run.getMaxLife(), run.getCredits(), run.getAscension(),
                unlocked, run.relics(), cards, commander, List.of());
    }

    // ------------------------------------------------------------------

    /** Si se completo la run entera. */
    public boolean isWon() {
        return won;
    }

    public AscentRun.Mode getMode() {
        return mode;
    }

    /** En que acto se acabo (o el ultimo, si se completo). */
    public int getAct() {
        return act;
    }

    /** Cuantos nodos se superaron en toda la run. */
    public int getCleared() {
        return cleared;
    }

    public int getLife() {
        return life;
    }

    public int getMaxLife() {
        return maxLife;
    }

    public int getCredits() {
        return credits;
    }

    public int getAscension() {
        return ascension;
    }

    /** Si esta victoria ha desbloqueado la Ascension siguiente. */
    public boolean isUnlocked() {
        return unlocked;
    }

    public List<AscentRelic> getRelics() {
        return relics;
    }

    /** El mazo con el que se acabo, de mas caro a mas barato. */
    public List<PaperCard> getDeck() {
        return deck;
    }

    /** El comandante, o {@code null} en Estandar. */
    public String getCommander() {
        return commander;
    }

    /** Los hitos que esta run acaba de conseguir. Vacio si ninguno. */
    public List<AscentFeat> getFeats() {
        return feats;
    }

    @Override
    public String toString() {
        return (won ? "completada" : "derrota") + " en el acto " + act
                + " | " + cleared + " nodos | " + life + "/" + maxLife + " vidas | "
                + relics.size() + " reliquias | mazo de " + deck.size();
    }
}
