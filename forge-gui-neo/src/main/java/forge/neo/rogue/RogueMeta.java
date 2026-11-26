package forge.neo.rogue;

import forge.gamemodes.rogue.RogueMetaProgress;
import forge.gamemodes.rogue.RogueRunHistoryEntry;
import forge.gamemodes.rogue.effect.AetherEffect;
import forge.gamemodes.rogue.effect.DescensionLevel;
import forge.util.Localizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Las reglas de lo que queda entre runs de Rogue Commander que Forge tiene en
 * sus pantallas Swing ({@code VSubmenuRogueAether}, {@code VSubmenuRogueHistory}),
 * sin pantalla: la usan el escritorio ({@code RogueMetaScreen}) y Android. Lo
 * de juego (comprar, subir de rango, activar) es de {@link RogueMetaProgress}.
 */
public final class RogueMeta {

    public static final String VICTORY = "VICTORY";
    public static final String DEFEAT = "DEFEAT";

    private RogueMeta() {
    }

    /**
     * Construir o mejorar un efecto del Aether. El primero que se construye se
     * enciende solo si queda hueco (como en Forge).
     */
    public static boolean build(final AetherEffect effect) {
        final RogueMetaProgress p = RogueMetaProgress.getInstance();
        final int before = p.getAetherEffectRank(effect);
        if (!p.upgradeAetherEffect(effect)) {
            return false;
        }
        if (before == 0 && p.getAetherEffectRank(effect) == 1
                && p.getActiveAetherEffectCount() < p.getActiveAetherEffectSlots()) {
            p.activateAetherEffect(effect);
        }
        return true;
    }

    /** Si se puede encender o apagar: construido, y con hueco para encenderlo. */
    public static boolean canToggle(final AetherEffect effect) {
        final RogueMetaProgress p = RogueMetaProgress.getInstance();
        final boolean active = p.getActiveAetherEffects().contains(effect);
        return p.getAetherEffectRank(effect) > 0
            && (active || p.getActiveAetherEffectCount() < p.getActiveAetherEffectSlots());
    }

    public static void toggle(final AetherEffect effect) {
        final RogueMetaProgress p = RogueMetaProgress.getInstance();
        if (p.getActiveAetherEffects().contains(effect)) {
            p.deactivateAetherEffect(effect);
        } else {
            p.activateAetherEffect(effect);
        }
    }

    /** Como acabo una run del historial: morir en modo infinito cuenta como victoria (Forge). */
    public static String outcome(final RogueRunHistoryEntry e) {
        final String outcome = e.getOutcome();
        if (DEFEAT.equals(outcome) && e.getEndlessLevel() > 0) {
            return VICTORY;
        }
        return outcome;
    }

    /** La cabecera de una run del historial: el comandante y como acabo. */
    public static String headline(final RogueRunHistoryEntry e) {
        final String o = outcome(e);
        final String key = VICTORY.equals(o) ? "lblRogueHistoryVictory"
            : DEFEAT.equals(o) ? "lblRogueHistoryDefeat" : "lblRogueHistoryAbandoned";
        return e.getCommanderName() + "  ·  " + Localizer.getInstance().getMessage(key);
    }

    /** Las lineas de detalle de una run del historial, en el orden de Forge. */
    public static List<String> details(final RogueRunHistoryEntry e) {
        final Localizer l = Localizer.getInstance();
        final List<String> out = new ArrayList<>();
        // Quien cayo o quien te gano: solo en una victoria o una derrota. Una run
        // abandonada no lleva linea (Forge, RogueRunHistoryEntry.getOutcomeDetail
        // desde #13085; antes salia "Defeated by" con cualquier nombre).
        final String who = e.getBossOrDefeatedBy();
        final String key = VICTORY.equals(e.getOutcome()) ? "lblRogueHistoryBossSlain"
            : DEFEAT.equals(e.getOutcome()) ? "lblRogueHistoryDefeatedBy" : null;
        if (key != null && who != null && !who.isBlank()) {
            out.add(l.getMessage(key, who));
        }
        out.add(RogueText.clean(l.getMessage("lblRogueHistoryTimestamp",
            e.getTimestamp(), e.getFormattedRunTime())));
        if (!e.getPath().isEmpty()) {
            out.add(l.getMessage("lblRogueHistoryPath", String.join(", ", e.getPath())));
        }
        final DescensionLevel dl = DescensionLevel.forLevel(e.getDescensionLevel());
        if (dl != null) {
            out.add(l.getMessage("lblRogueHistoryDescension", dl.level, dl.name));
        }
        if (e.getEndlessLevel() > 0) {
            out.add(l.getMessage("lblRogueEndlessLevel", e.getEndlessLevel()));
        }
        return out;
    }
}
