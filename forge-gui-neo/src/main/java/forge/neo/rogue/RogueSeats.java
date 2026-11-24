package forge.neo.rogue;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueRun.CarryCard;
import forge.gamemodes.rogue.effect.RogueEffect;
import forge.gamemodes.rogue.effect.RogueEffectComposite;
import forge.gamemodes.rogue.path.NodePlanebound;
import forge.item.PaperCard;
import java.util.EnumSet;
import java.util.List;

/**
 * Los dos asientos de un duelo de Rogue Commander, como los monta
 * {@code NodePlaneboundHelper.handlePlaneboundNode} de Forge. Sin mesa: lo usan
 * igual el escritorio ({@link RogueNeoTable}, por {@code NeoGame.Seating}) y
 * Android ({@code RogueMatch.kt}), que monta su propio {@code HostedMatch}.
 *
 * <p>Dos trampas que viven aqui para que nadie las repita:
 * <ul>
 *   <li>{@code forVariants} PISA la vida, asi que {@code setStartingLife} va
 *       despues (la de la run para ti, la del nodo para el Planebound).</li>
 *   <li>{@code onMatchStart} necesita los DOS asientos: se llama al montar el
 *       del rival, y por eso el tuyo va primero.</li>
 * </ul>
 *
 * <p>El {@code GameType} es {@code RogueCommander}: de el cuelga el plano fijo
 * de {@code PlaneswalkEffect}. Las variantes van aparte ({@link #variants()}).
 */
public final class RogueSeats {

    private final RogueRun run;
    private final NodePlanebound node;
    private final EnumSet<GameType> variants;
    private final List<PaperCard> planes;
    private final int planeboundRowCount;
    private final LobbyPlayer aiPlayer;
    private final Deck planeboundDeck;
    private final int testHumanLife;
    private final int testAiLife;
    private RegisteredPlayer human;

    RogueSeats(RogueRun run, NodePlanebound node, EnumSet<GameType> variants, List<PaperCard> planes,
               int planeboundRowCount, LobbyPlayer aiPlayer, Deck planeboundDeck,
               int testHumanLife, int testAiLife) {
        this.run = run;
        this.node = node;
        this.variants = variants;
        this.planes = planes;
        this.planeboundRowCount = planeboundRowCount;
        this.aiPlayer = aiPlayer;
        this.planeboundDeck = planeboundDeck;
        this.testHumanLife = testHumanLife;
        this.testAiLife = testAiLife;
    }

    /** Las variantes de la partida: Commander, y Planechase si hay plano. */
    public EnumSet<GameType> variants() {
        return variants;
    }

    /** {@code GameType.RogueCommander}. */
    public GameType gameType() {
        return GameType.RogueCommander;
    }

    /** Tu mazo: el de la run. */
    public Deck humanDeck() {
        return run.getCurrentDeck();
    }

    /** El mazo del Planebound. */
    public Deck planeboundDeck() {
        return planeboundDeck;
    }

    /** El jugador de la IA: con el nombre del Planebound y su cara. */
    public LobbyPlayer aiPlayer() {
        return aiPlayer;
    }

    /** Tu asiento. Va PRIMERO (ver {@link #opponent}). Sin jugador: lo pone quien monta la partida. */
    public RegisteredPlayer human() {
        human = RegisteredPlayer.forVariants(
            2, variants, run.getCurrentDeck(),
            null, false, planes, null
        );
        human.setStartingLife(testHumanLife >= 0 ? testHumanLife : run.getCurrentLife());
        // NEOFORGE: sin lobbyPlayer.setName/Avatar/Sleeve (el jugador es global)

        if (!run.getCarryCards().isEmpty()) {
            RogueEffect.addCardToCommandZone("Rogue - Carry Card Enabler", human);
        }
        for (CarryCard card : run.getCarryCards()) {
            RogueEffect.addCardToCommandZone(card.toPaperCard(), human);
        }
        return human;
    }

    /** El asiento del Planebound, con su vida; y aqui, con los dos ya puestos, {@code onMatchStart}. */
    public RegisteredPlayer opponent() {
        RegisteredPlayer ai = RegisteredPlayer.forVariants(
            2, variants, planeboundDeck,
            null, false, planes, null
        );
        ai.setStartingLife(testAiLife >= 0 ? testAiLife
            : node.getPlaneboundLife(planeboundRowCount));
        if (human != null) {
            RogueEffectComposite.INSTANCE.onMatchStart(human, ai, run);
        }
        return ai;
    }
}
