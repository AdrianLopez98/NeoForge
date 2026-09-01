package forge.neo.tutorial;

import java.util.List;

import forge.game.Game;
import forge.game.GameState;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * La mesa preparada de una leccion, aplicada a la partida.
 *
 * <p>Es exactamente lo que hace {@code forge.gamemodes.puzzle.Puzzle} — de ahi
 * sale la idea — <b>menos la carta de objetivo</b>. Un puzzle mete en la zona de
 * mando una carta llamada "Puzzle Goal" con el objetivo escrito y un disparo que
 * te hace perder al llegar a un turno. En un tutorial eso sobra por partida
 * doble: no hay objetivo que cumplir, y una carta en blanco al lado del
 * comandante es justo el tipo de cosa que hay que explicar y no que aparezca.
 *
 * <p>Lo unico que si hay que copiar de {@code Puzzle} es el <b>tamanyo maximo de
 * mano</b>. Los dos asientos se registran con {@code setStartingHand(0)} — la
 * mano la reparte esta posicion, no un mazo — y {@code Game} usa ese numero
 * <i>tambien</i> como maximo ({@code Game.java}: {@code setMaxHandSize} y
 * {@code setStartingHandSize} con el mismo valor). Sin corregirlo, el maximo es
 * cero y en el primer paso de limpieza el motor te obliga a descartar la mano
 * entera. Se ve venir leyendo el codigo de {@code Puzzle}, no jugando.
 */
public final class TutorialState extends GameState {

    /** Mano de siete, como en una partida normal. */
    private static final int HAND_SIZE = 7;

    public TutorialState(final List<String> lines) {
        parse(lines);
    }

    @Override
    protected void applyGameOnThread(final Game game) {
        for (final Player p : game.getPlayers()) {
            p.setStartingHandSize(HAND_SIZE);
            p.setMaxHandSize(HAND_SIZE);
        }
        super.applyGameOnThread(game);
        summary = describe(game);
    }

    private volatile String summary;

    /**
     * Como quedo la mesa de verdad, para poder comprobarla sin ventana.
     *
     * <p>Sin esto, un nombre de carta mal escrito en una leccion se traga el
     * motor con un {@code System.err} y la leccion arranca con una zona vacia:
     * el paso dice "lanza la criatura" y no hay criatura. Se apunta aqui porque
     * es el unico sitio donde se sabe que se ha creado de verdad.
     *
     * @return null si la posicion todavia no se ha aplicado
     */
    public String getSummary() {
        return summary;
    }

    private static String describe(final Game game) {
        final StringBuilder sb = new StringBuilder();
        for (final Player p : game.getPlayers()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(p.isAI() ? "rival" : "tu").append(": ")
                    .append("mano ").append(p.getCardsIn(ZoneType.Hand).size())
                    .append(", mesa ").append(p.getCardsIn(ZoneType.Battlefield).size())
                    .append(", mazo ").append(p.getCardsIn(ZoneType.Library).size())
                    .append(", mando ").append(p.getCardsIn(ZoneType.Command).size())
                    .append(", vida ").append(p.getLife());
        }
        return sb.toString();
    }
}
