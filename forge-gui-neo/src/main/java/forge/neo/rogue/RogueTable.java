package forge.neo.rogue;

import forge.game.GameView;

/**
 * Quien JUEGA un duelo de Rogue Commander: la mesa. Es lo unico de la logica
 * del modo que depende de la interfaz, y por eso va aparte: el escritorio lo
 * hace con {@code NeoGame.play} ({@link RogueNeoTable}) y Android con su propio
 * {@code HostedMatch} ({@code RogueMatch.kt}). Todo lo demas
 * ({@link RogueSession}, {@link RogueDuel}, {@link RogueSeats}) es el mismo
 * codigo en los dos.
 */
public interface RogueTable {

    /**
     * Juega el duelo con esos asientos y se queda esperando a que acabe. Lo
     * llama el hilo de la run, nunca el de la interfaz.
     *
     * @return como acabo la ultima partida, o {@code null} si no acabo (se
     *         colgo, revento o no llego a empezar). {@code null} NO es una
     *         derrota: el nodo se queda como estaba
     */
    GameView play(RogueSeats seats);
}
