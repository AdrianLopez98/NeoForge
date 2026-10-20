package forge.neo.match;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import forge.game.card.CardView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.neo.NeoText;

/**
 * A QUIEN APUNTA algo del stack, agrupado por quien lo controla:
 * "Grizzly Bears, Llanowar Elves (Parker) · Sol Ring (Tu) · Parker".
 *
 * <p>Discord, 20-10-2026: <i>"it would already help a lot if the targets were
 * clearly listed somewhere in that window, ideally grouped by their
 * controller/owner"</i>. Lo usan el cartel del escritorio
 * ({@code TableScreen.bannerText}) y la pila de Android. Java puro: los
 * nombres los pone cada interfaz ({@code nameOf}, {@code playerName}), que
 * sabe traducirlos.
 */
public final class StackTargets {

    private StackTargets() {
    }

    public static String byController(final StackItemView item, final PlayerView me,
                                      final Function<CardView, String> nameOf,
                                      final Function<PlayerView, String> playerName) {
        if (item == null) {
            return "";
        }
        final Map<String, List<String>> groups = new LinkedHashMap<>();
        final List<String> players = new ArrayList<>();
        try {
            if (item.getTargetCards() != null) {
                for (final CardView c : item.getTargetCards()) {
                    if (c == null) {
                        continue;
                    }
                    final String name = nameOf.apply(c);
                    final PlayerView ctrl = c.getController();
                    final String who = ctrl == null ? "" : who(ctrl, me, playerName);
                    List<String> names = groups.get(who);
                    if (names == null) {
                        names = new ArrayList<>();
                        groups.put(who, names);
                    }
                    if (name != null && !name.trim().isEmpty() && !names.contains(name)) {
                        names.add(name);
                    }
                }
            }
            if (item.getTargetPlayers() != null) {
                for (final PlayerView p : item.getTargetPlayers()) {
                    if (p != null) {
                        final String n = who(p, me, playerName);
                        if (!players.contains(n)) {
                            players.add(n);
                        }
                    }
                }
            }
        } catch (final RuntimeException e) {
            // Un objetivo que ya no existe: lo que se haya leido.
        }
        final List<String> parts = new ArrayList<>();
        for (final Map.Entry<String, List<String>> g : groups.entrySet()) {
            if (g.getValue().isEmpty()) {
                continue;
            }
            final String cards = String.join(", ", g.getValue());
            parts.add(g.getKey().isEmpty() ? cards : cards + " (" + g.getKey() + ")");
        }
        parts.addAll(players);
        return String.join(" · ", parts);
    }

    private static String who(final PlayerView p, final PlayerView me,
                              final Function<PlayerView, String> playerName) {
        return p.equals(me) ? NeoText.get("stats.you") : playerName.apply(p);
    }
}
