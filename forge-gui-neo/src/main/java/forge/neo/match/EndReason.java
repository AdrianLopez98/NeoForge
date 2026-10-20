package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.GameLossReason;
import forge.game.player.Player;
import forge.game.player.PlayerOutcome;
import forge.neo.NeoText;
import forge.util.CardTranslation;

/**
 * POR QUE se ha acabado la partida: una frase por jugador que ha perdido (y la
 * del que gana por un efecto), para el cuadro del final.
 *
 * <p>Discord, 20-10-2026: <i>"sometimes I lose or win without knowing why ...
 * won this match even though the opponent had enough life left"</i>. Era el
 * dano de comandante: su Giada llevaba 21 a ese rival. El motor aplica esa
 * derrota (y la de veneno, la de biblioteca vacia, la de un efecto) sin
 * escribir nada en el registro, y el cuadro del final solo decia quien ganaba.
 * El motor SI lo sabe ({@code Player.getOutcome()}); se cuenta.
 *
 * <p>Java puro, sin JavaFX: Android lo usa por el jar. Solo con la partida en
 * este proceso (un invitado en red no tiene {@code Game}): sin ella, nada.
 */
public final class EndReason {

    private EndReason() {
    }

    /** Las frases, ya en el idioma del juego; vacia si no se sabe nada. */
    public static List<String> lines(final Game game) {
        if (game == null) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<>();
        try {
            for (final Player p : game.getRegisteredPlayers()) {
                final String line = line(p);
                if (line != null) {
                    out.add(line);
                }
            }
        } catch (final RuntimeException e) {
            // Una partida a medio cerrar: lo leido, se ensenya.
        }
        return out;
    }

    /** Las frases juntas, una por linea, o null si no hay ninguna. */
    public static String text(final Game game) {
        final List<String> all = lines(game);
        return all.isEmpty() ? null : String.join("\n", all);
    }

    private static String line(final Player p) {
        final PlayerOutcome o = p.getOutcome();
        if (o == null) {
            return null;
        }
        final String who = p.getName();
        if (o.lossState == null) {
            // Ganar porque todos han perdido se entiende solo; ganar por una
            // carta ("you win the game"), no.
            return o.altWinSourceName == null ? null
                    : NeoText.get("over.reason.altWin", who, cardName(o.altWinSourceName));
        }
        final GameLossReason r = o.lossState;
        switch (r) {
            case LifeReachedZero:
                return NeoText.get("over.reason.life", who);
            case CommanderDamage: {
                final String from = commanderThatKilled(p);
                return from == null ? NeoText.get("over.reason.commander", who)
                        : NeoText.get("over.reason.commanderFrom", who, from);
            }
            case Poisoned:
                return NeoText.get("over.reason.poison", who);
            case Milled:
                return NeoText.get("over.reason.milled", who);
            case SpellEffect:
                return NeoText.get("over.reason.effect", who, cardName(o.loseConditionSpell));
            case OpponentWon:
                return NeoText.get("over.reason.opponentWon", who, cardName(o.loseConditionSpell));
            case Conceded:
                return NeoText.get("over.reason.conceded", who);
            default:
                return null;
        }
    }

    /** El comandante que llego a 21 contra este jugador (el que mas, si hay varios). */
    private static String commanderThatKilled(final Player p) {
        Card best = null;
        int most = 0;
        for (final Map.Entry<Card, Integer> e : p.getCommanderDamage()) {
            if (e.getValue() != null && e.getValue() >= 21 && e.getValue() > most) {
                most = e.getValue();
                best = e.getKey();
            }
        }
        return best == null ? null : cardName(best.getName());
    }

    private static String cardName(final String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        try {
            return CardTranslation.getTranslatedName(name);
        } catch (final RuntimeException e) {
            return name;
        }
    }
}
