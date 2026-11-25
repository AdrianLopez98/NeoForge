package forge.neo.rogue;

import forge.deck.Deck;
import forge.gamemodes.rogue.RogueEvent;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.effect.BazaarItem;
import forge.gamemodes.rogue.effect.ChestEffect;
import forge.gamemodes.rogue.effect.NPCEffect;
import forge.gamemodes.rogue.effect.SanctumContext;
import forge.gamemodes.rogue.npc.NPCContext;
import forge.item.PaperCard;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Lo que la logica de Rogue Commander le PREGUNTA a la pantalla.
 *
 * <p>En el Forge de escritorio cada una de estas es un dialogo Swing modal
 * ({@code BazaarDialog}, {@code ChestDialog}, {@code NPCDialog}...) llamado
 * desde la propia logica, que se queda esperando la respuesta. Aqui es lo
 * mismo, pero al reves de hilo: la logica corre en el hilo de la run
 * ({@link RogueSession}) y CADA METODO BLOQUEA hasta que el jugador contesta.
 * La pantalla JavaFX los implementa pintando y esperando
 * ({@code forge.neo.ui.RogueDialogs}); Android, con Compose
 * ({@code RoguePreguntas.kt}); el comprobador, contestando solo
 * ({@code RogueCheck}). La mesa del duelo va aparte ({@link RogueTable}).
 *
 * <p>NUNCA se llaman desde el hilo de JavaFX: bloquearian la ventana.
 */
public interface RoguePrompts {

    /** Un trozo de un resultado: un rotulo, un texto y unas cartas (todo opcional). */
    record Section(String label, String text, List<PaperCard> cards) {
        public Section(final String label, final List<PaperCard> cards) {
            this(label, null, cards);
        }

        public Section(final String label, final String text) {
            this(label, text, List.of());
        }
    }

    /** Lo que se contesta a un NPC: volver a tirar, o lo elegido (null si no hay que elegir). */
    record NpcAnswer(boolean reroll, NPCEffect choice) {
    }

    /** Lo que se contesta a un evento. */
    record EventAnswer(boolean reroll, RogueEvent.EventChoice choice) {
    }

    /** Lo que se contesta a un cofre: volver a tirar, o lo elegido (null = saltar). */
    record ChestAnswer(boolean reroll, ChestEffect choice) {
    }

    /** Lo que se contesta al bazar: volver a tirar, lo comprado y la pestaña que quedo. */
    record BazaarAnswer(boolean reroll, Set<BazaarItem> bought, int tab) {
    }

    /** Las opciones del santuario, ya calculadas por la logica (ver RogueSanctum). */
    record SanctumOffer(String restDescription, boolean restEnabled, String restDisabledReason,
                        List<SanctumContext.SanctumChoice> extraChoices) {
    }

    /** Que se eligio en el santuario. {@code custom} solo con {@link SanctumPick#CUSTOM}. */
    record SanctumAnswer(SanctumPick pick, SanctumContext.SanctumChoice custom) {
    }

    /** Las mismas cinco del {@code SanctumDialog} de Forge. */
    enum SanctumPick { HEAL, COOK, REFLECT, CUSTOM, SKIP }

    /** Un aviso con un solo boton. */
    void message(String title, String text);

    /** Si / no. */
    boolean confirm(String title, String text, String yes, String no);

    /** Un resultado: texto y, debajo, secciones con cartas. */
    void result(String title, String text, List<Section> sections);

    /**
     * Los premios de cartas ({@code CardRewardHelper.RewardDialog}): las
     * elegidas, o {@code null} para volver a tirar.
     * {@code rerollCost} es el oro de la siguiente tirada (0 si es gratis;
     * Forge lo pasa aparte desde #13023).
     */
    List<PaperCard> cardReward(String title, List<PaperCard> cards, int maxPicks,
                               String rerollLabel, int rerollCost, boolean rerollEnabled, int gold);

    /**
     * Elegir cartas de una lista ({@code CardSelectionDialog}): entre
     * {@code min} y {@code max}. {@code preview} monta el mazo como quedaria
     * (puede ser null).
     */
    List<PaperCard> chooseCards(String title, String subtitle, List<PaperCard> cards, int min, int max,
                                RogueRun run, Function<List<PaperCard>, Deck> preview);

    /** Un NPC habla y, si trae opciones, se elige una ({@code NPCDialog}). */
    NpcAnswer npc(NPCContext ctx, int rerolls);

    /** Un tutorial: Teferi lo cuenta por trozos. Devuelve true si se pidio saltar TODOS. */
    boolean tutorial(RogueTutorial tutorial);

    /** Un evento con sus opciones ({@code EventDialog}). */
    EventAnswer event(RogueEvent event, RogueRun run, int rerolls);

    /** Un cofre: dos botines, o saltar ({@code ChestDialog}). */
    ChestAnswer chest(List<ChestEffect> choices, RogueRun run, int rerolls);

    /** El bazar ({@code BazaarDialog}). {@code rerollLabel} null = sin volver a tirar. */
    BazaarAnswer bazaar(List<BazaarItem> items, RogueRun run, String title, String rerollLabel,
                        boolean rerollEnabled, int tab);

    /** El santuario ({@code SanctumDialog}). */
    SanctumAnswer sanctum(SanctumOffer offer, RogueRun run);

    /**
     * Elegir comandante (y companero) de entre todos ({@code CommanderSelectionDialog},
     * para la modificacion "Chosen One"). Vacio = cancelar.
     */
    List<PaperCard> chooseCommander(List<PaperCard> eligible, Function<PaperCard, List<PaperCard>> partners);
}
