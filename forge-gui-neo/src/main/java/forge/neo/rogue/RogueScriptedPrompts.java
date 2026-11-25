package forge.neo.rogue;

import forge.deck.Deck;
import forge.gamemodes.rogue.RogueEvent;
import forge.gamemodes.rogue.RogueRun;
import forge.gamemodes.rogue.RogueTutorial;
import forge.gamemodes.rogue.effect.BazaarItem;
import forge.gamemodes.rogue.effect.ChestEffect;
import forge.gamemodes.rogue.npc.NPCContext;
import forge.item.PaperCard;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * El jugador que contesta solo a todo lo que pregunta Rogue Commander: siempre
 * lo primero que se pueda. Lo usan {@code roguecheck} en el escritorio y la
 * prueba sin pantallas de Android ({@code RogueProbe.kt}), que asi recorren la
 * misma run con las mismas respuestas.
 */
public final class RogueScriptedPrompts implements RoguePrompts {
    public int rewardPicks;
    public int lastBazaarItems;
    public int lastChestChoices;
    public boolean lastRestEnabled;

    @Override
    public void message(final String title, final String text) {
        System.out.println("    [aviso] " + title + ": " + text);
    }

    @Override
    public boolean confirm(final String title, final String text, final String yes, final String no) {
        return false;
    }

    @Override
    public void result(final String title, final String text, final List<Section> sections) {
        System.out.println("    [resultado] " + title + (text == null ? "" : ": " + text));
    }

    @Override
    public List<PaperCard> cardReward(final String title, final List<PaperCard> cards, final int maxPicks,
                                      final String rerollLabel, final int rerollCost,
                                      final boolean rerollEnabled, final int gold) {
        final List<PaperCard> out = new ArrayList<>(cards.subList(0, Math.min(maxPicks, cards.size())));
        rewardPicks += out.size();
        return out;
    }

    @Override
    public List<PaperCard> chooseCards(final String title, final String subtitle, final List<PaperCard> cards,
                                       final int min, final int max, final RogueRun run,
                                       final Function<List<PaperCard>, Deck> preview) {
        return new ArrayList<>(cards.subList(0, Math.min(Math.max(min, Math.min(1, max)), cards.size())));
    }

    @Override
    public NpcAnswer npc(final NPCContext ctx, final int rerolls) {
        return new NpcAnswer(false, ctx.choices().isEmpty() ? null : ctx.choices().get(0).npcEffect());
    }

    @Override
    public boolean tutorial(final RogueTutorial tutorial) {
        return false;
    }

    @Override
    public EventAnswer event(final RogueEvent event, final RogueRun run, final int rerolls) {
        for (final RogueEvent.EventChoice c : event.getChoices()) {
            if (c.effect().isChoiceAvailable(run)) {
                return new EventAnswer(false, c);
            }
        }
        return new EventAnswer(false, null);
    }

    @Override
    public ChestAnswer chest(final List<ChestEffect> choices, final RogueRun run, final int rerolls) {
        lastChestChoices = choices.size();
        return new ChestAnswer(false, choices.isEmpty() ? null : choices.get(0));
    }

    @Override
    public BazaarAnswer bazaar(final List<BazaarItem> items, final RogueRun run, final String title,
                               final String rerollLabel, final boolean rerollEnabled, final int tab) {
        lastBazaarItems = items.size();
        final Set<BazaarItem> bought = new HashSet<>();
        for (final BazaarItem item : items) {
            if (item.getPrice() <= run.getCurrentGold()) {
                bought.add(item);
                break;
            }
        }
        return new BazaarAnswer(false, bought, tab);
    }

    @Override
    public SanctumAnswer sanctum(final SanctumOffer offer, final RogueRun run) {
        lastRestEnabled = offer.restEnabled();
        return new SanctumAnswer(offer.restEnabled() ? SanctumPick.HEAL : SanctumPick.REFLECT, null);
    }

    @Override
    public List<PaperCard> chooseCommander(final List<PaperCard> eligible,
                                           final Function<PaperCard, List<PaperCard>> partners) {
        return eligible.isEmpty() ? List.of() : List.of(eligible.get(0));
    }
}
