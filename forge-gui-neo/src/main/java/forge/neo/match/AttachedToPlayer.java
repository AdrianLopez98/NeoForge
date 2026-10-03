package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.GameEntityView;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.player.PlayerView;

/**
 * <b>Lo que esta en la mesa de uno pero va CON otro jugador</b>: las
 * maldiciones (auras que encantan a un jugador) y las batallas (a quien
 * protegen).
 *
 * <p>Discord, 03-10-2026: <i>"Jeffery (AI) enchanted Andrea (AI) with a player
 * curse, except the curse just stays on Jeffery's board since they're the
 * owner and there's no way to actually tell who is actually cursed without
 * simply remembering or checking the log"</i> — y lo mismo con las batallas.
 * Por las reglas estan bien donde estan: las controla quien las lanzo. Lo que
 * faltaba es que se VIERA a quien van, y el motor ya lo publica
 * ({@code CardView.getEntityAttachedTo}, {@code getProtectingPlayer}). Es el
 * principio 3: el estado se ve, no se lee.
 *
 * <p>Se ensenya por los dos lados: una pastilla en la barra del jugador
 * maldito o protector ({@code PlayerBar.setAttached}) y un marcador en la
 * propia carta ({@code CardNode.refreshMarkers}). Aqui solo la pregunta, sin
 * JavaFX: Android la usa igual por el jar (Java de la 8).
 */
public final class AttachedToPlayer {

    private AttachedToPlayer() {
    }

    /** El jugador al que encanta esta carta, o null si no encanta a un jugador. */
    public static PlayerView enchantedPlayer(final CardView card) {
        if (card == null) {
            return null;
        }
        try {
            final GameEntityView e = card.getEntityAttachedTo();
            return e instanceof PlayerView ? (PlayerView) e : null;
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    /** A quien protege esta batalla, o null si no es una batalla (o aun no tiene). */
    public static PlayerView protector(final CardView card) {
        if (card == null) {
            return null;
        }
        try {
            final CardStateView st = card.getCurrentState();
            if (st == null || st.getType() == null || !st.getType().isBattle()) {
                return null;
            }
            return card.getProtectingPlayer();
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    /** Las cartas de la mesa (de quien sea) que encantan a este jugador. */
    public static List<CardView> enchanting(final GameView game, final PlayerView player) {
        final List<CardView> out = new ArrayList<>();
        if (game == null || player == null || game.getPlayers() == null) {
            return out;
        }
        for (final PlayerView p : game.getPlayers()) {
            final Iterable<CardView> field = p.getBattlefield();
            if (field == null) {
                continue;
            }
            for (final CardView c : field) {
                final PlayerView target = enchantedPlayer(c);
                if (target != null && target.getId() == player.getId()) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** Las batallas de la mesa (de quien sea) que protege este jugador. */
    public static List<CardView> protecting(final GameView game, final PlayerView player) {
        final List<CardView> out = new ArrayList<>();
        if (game == null || player == null || game.getPlayers() == null) {
            return out;
        }
        for (final PlayerView p : game.getPlayers()) {
            final Iterable<CardView> field = p.getBattlefield();
            if (field == null) {
                continue;
            }
            for (final CardView c : field) {
                final PlayerView who = protector(c);
                if (who != null && who.getId() == player.getId()) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** El nombre de la carta para la pastilla, traducido si toca. */
    public static String nameOf(final CardView card) {
        try {
            final CardStateView st = card.getCurrentState();
            if (st == null) {
                return "";
            }
            final String t = st.getTranslatedName();
            return t == null || t.isEmpty() ? st.getName() : t;
        } catch (final RuntimeException ex) {
            return "";
        }
    }

}
