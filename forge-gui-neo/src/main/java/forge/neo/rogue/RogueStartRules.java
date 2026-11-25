package forge.neo.rogue;

import forge.gamemodes.rogue.RogueConfig;
import forge.gamemodes.rogue.RogueDeck;
import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.effect.CustomRunModification;
import forge.gamemodes.rogue.effect.DescensionLevel;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Lo que se puede elegir al EMPEZAR una run de Rogue Commander, sin pantalla:
 * las reglas de {@code VSubmenuRogueStart} / {@code CSubmenuRogueStart} de Forge
 * ({@code loadAvailableCommanders}, {@code onCustomModificationToggled},
 * {@code updateDescensionVisibility}, {@code updateDescensionDisplay} y
 * {@code updateStartButtonState}): desbloqueos, conflictos entre
 * modificaciones, el comandante generado que fuerza las aleatorias, y hasta que
 * nivel de Descension se puede bajar.
 *
 * <p>La usan las dos interfaces: el escritorio ({@code RogueStartScreen}) y
 * Android. Cada una pinta lo que esta clase dice y le pasa los clics; ninguna
 * decide nada por su cuenta.
 */
public final class RogueStartRules {

    /** Lo que se pinta de la Descension. */
    public record Descension(boolean checkEnabled, boolean checked, boolean boxVisible,
                             boolean downVisible, boolean upVisible, String levelText, String description) {
    }

    private final RogueMetaProgress progress = RogueMetaProgress.getInstance();
    private final Set<CustomRunModification> checkedMods = EnumSet.noneOf(CustomRunModification.class);
    private final Set<CustomRunModification> disabledMods = EnumSet.noneOf(CustomRunModification.class);
    private boolean custom;
    private RogueDeck selected;
    private int level;
    private List<RogueDeck> commanders = List.of();

    public RogueStartRules() {
        reloadCommanders();
    }

    // ------------------------------------------------------------------
    // Lo que se lee

    /** Los comandantes del carrusel: desbloqueados primero, luego por nombre. */
    public List<RogueDeck> commanders() {
        return commanders;
    }

    public RogueDeck selected() {
        return selected;
    }

    public boolean isCustom() {
        return custom;
    }

    public boolean customUnlocked() {
        return progress.isCustomRunsUnlocked();
    }

    public boolean descensionUnlocked() {
        return progress.isDescensionModeUnlocked();
    }

    public boolean isChecked(final CustomRunModification m) {
        return checkedMods.contains(m);
    }

    public boolean isDisabled(final CustomRunModification m) {
        return disabledMods.contains(m);
    }

    /** Si la run lleva un comandante generado: el carrusel se apaga (no cuenta el elegido). */
    public boolean generatedCommander() {
        for (final CustomRunModification m : CustomRunModification.values()) {
            if (m.isGeneratedCommander() && checkedMods.contains(m)) {
                return true;
            }
        }
        return false;
    }

    /** Si ya hay al menos una run acabada (Aether e historial). */
    public boolean anyRunCompleted() {
        return progress.getTotalRunsCompleted() > 0;
    }

    // ------------------------------------------------------------------
    // Lo que hace el jugador

    /** Clic en un comandante. Uno bloqueado no se elige: se deselecciona el que hubiera. */
    public void select(final RogueDeck deck) {
        if (deck != null && !deck.isUnlocked()) {
            selected = null;
            return;
        }
        final boolean changed = deck != selected;
        selected = deck;
        // Cambiar de comandante en una run normal pone la Descension a 0 (Forge).
        if (changed && !custom) {
            level = 0;
        }
    }

    /** La casilla de run personalizada. Devuelve true si el carrusel ha cambiado. */
    public boolean setCustom(final boolean on) {
        final boolean wasExperiment = checkedMods.contains(CustomRunModification.EXPERIMENT);
        custom = on && customUnlocked();
        if (!custom) {
            checkedMods.clear();
            disabledMods.clear();
        }
        if (wasExperiment && !custom) {
            reloadCommanders();
            return true;
        }
        return false;
    }

    /** {@code onCustomModificationToggled}. Devuelve true si el carrusel ha cambiado. */
    public boolean setModification(final CustomRunModification m, final boolean on) {
        if (disabledMods.contains(m)) {
            return false;
        }
        final boolean wasGenerated = generatedCommander();
        if (on) {
            checkedMods.add(m);
            for (final CustomRunModification other : CustomRunModification.values()) {
                if (other != m && m.conflictsWith(other)) {
                    checkedMods.remove(other);
                }
            }
        } else {
            checkedMods.remove(m);
        }
        final boolean generated = generatedCommander();
        for (final CustomRunModification other : CustomRunModification.values()) {
            if (other.isIncludedWithGeneratedCommander()) {
                if (generated) {
                    checkedMods.add(other);
                    disabledMods.add(other);
                } else {
                    disabledMods.remove(other);
                    // Forge (RogueStart.toggle, igual que el Swing de antes): si el
                    // comandante generado se va, se van tambien las que traia; si
                    // no, la run empezaria con Random Start y Random Rewards que
                    // nadie ha marcado.
                    if (wasGenerated || m.isGeneratedCommander()) {
                        checkedMods.remove(other);
                    }
                }
            }
        }
        if (m == CustomRunModification.EXPERIMENT) {
            reloadCommanders();
            return true;
        }
        return false;
    }

    /** La casilla de la Descension: nivel 1 o nada. */
    public void setDescension(final boolean on) {
        level = on ? 1 : 0;
    }

    public void descensionDown() {
        level = Math.max(1, level - 1);
    }

    public void descensionUp() {
        level = Math.min(DescensionLevel.getMaxLevel(), level + 1);
    }

    // ------------------------------------------------------------------
    // Lo que se pinta despues de cada clic

    /** {@code updateDescensionVisibility} + {@code updateDescensionDisplay}. */
    public Descension descension() {
        final Localizer l = Localizer.getInstance();
        final boolean unlocked = descensionUnlocked();
        final int maxUnlocked = maxDescension();
        final boolean show = unlocked && (custom || (selected != null && maxUnlocked > 0));
        String description = "";
        if (!show) {
            level = 0;
            if (unlocked && selected != null && !custom) {
                description = RogueText.clean(l.getMessage(
                    "lblRogueUnlockCommanderDescensionHint", selected.getCommanderCardName()));
            }
        }
        final boolean boxVisible = level > 0 || (unlocked && selected != null && !custom && maxUnlocked == 0);
        final boolean locked = level > maxUnlocked;
        String levelText = "";
        if (level > 0) {
            final DescensionLevel dl = DescensionLevel.forLevel(level);
            levelText = (locked ? "🔒 " : "") + (dl != null
                ? l.getMessage("lblRogueNamedLevel", level, dl.name)
                : l.getMessage("lblRogueLevel", level));
            description = locked
                ? RogueText.clean(l.getMessage("lblRogueUnlockNextDescensionHint",
                    selected == null ? "" : selected.getCommanderCardName(), level - 1, level))
                : dl == null ? "" : RogueText.clean(dl.description);
        }
        return new Descension(show, level > 0, boxVisible, level > 1,
            level > 0 && level <= maxUnlocked && level < DescensionLevel.getMaxLevel(), levelText, description);
    }

    /** {@code updateStartButtonState}. */
    public boolean canStart() {
        // Forge lo mira DESPUES de ajustar la Descension (que puede bajarla a 0).
        descension();
        final int maxUnlocked = maxDescension();
        final boolean unlocked = descensionUnlocked();
        final boolean generated = custom && generatedCommander();
        final boolean customAllowed = !custom || customUnlocked();
        final boolean anyMod = !checkedModifications().isEmpty();
        final boolean hasDeck = custom ? anyMod && (generated || selected != null) : selected != null;
        final boolean descensionAllowed = level == 0
            || (unlocked && (custom || (selected != null && level <= maxUnlocked)));
        return customAllowed && hasDeck && descensionAllowed;
    }

    /** Lo que se le pasa a {@link RogueSession#beginNewRun}. */
    public RogueSession.StartOptions options() {
        return new RogueSession.StartOptions(selected, custom, checkedModifications(), level);
    }

    // ------------------------------------------------------------------

    private int maxDescension() {
        return custom ? DescensionLevel.getMaxLevel()
            : selected == null ? 0 : progress.getMaxDescensionUnlocked(selected.getCommanderCardName());
    }

    private List<CustomRunModification> checkedModifications() {
        final List<CustomRunModification> out = new ArrayList<>();
        if (!custom) {
            return out;
        }
        for (final CustomRunModification m : CustomRunModification.values()) {
            if (checkedMods.contains(m)) {
                out.add(m);
            }
        }
        return out;
    }

    /** {@code loadAvailableCommanders}: y vuelve a elegir el de antes si sigue, o el primero desbloqueado. */
    private void reloadCommanders() {
        final boolean experimental = custom && checkedMods.contains(CustomRunModification.EXPERIMENT);
        final List<RogueDeck> decks = new ArrayList<>(RogueConfig.loadRogueDecks());
        decks.removeIf(d -> d.isExperimental() != experimental);
        decks.sort(Comparator.comparing((RogueDeck d) -> !d.isUnlocked()).thenComparing(RogueDeck::getName));
        commanders = List.copyOf(decks);
        final String previous = selected == null ? null : selected.getCommanderCardName();
        RogueDeck first = null;
        RogueDeck again = null;
        for (final RogueDeck deck : decks) {
            if (deck.isUnlocked() && first == null) {
                first = deck;
            }
            if (deck.isUnlocked() && deck.getCommanderCardName().equals(previous)) {
                again = deck;
            }
        }
        select(again != null ? again : first);
    }
}
