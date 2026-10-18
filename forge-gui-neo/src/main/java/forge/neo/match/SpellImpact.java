package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import forge.card.ColorSet;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.event.GameEventSpellResolved;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetChoices;

/**
 * <b>Lo que acaba de golpear un hechizo o una habilidad al resolverse</b>: sus
 * objetivos (cartas y jugadores), el permanente que ha entrado si era un
 * hechizo de permanente, y sus colores. Es lo que pintan los destellos de las
 * dos interfaces ({@code forge.neo.ui.ImpactLayer} en el escritorio,
 * {@code Destellos} en Android). Discord, 17-10-2026.
 *
 * <p>Cero JavaFX y cero API que Android no tenga en la 26: la usa el jar de
 * Android tal cual, como {@code GameStats}.
 *
 * <p><b>Solo vale en el hilo del motor y en el momento del aviso.</b> El motor
 * dispara {@code GameEventSpellResolved} al acabar de resolver pero
 * <b>antes</b> de quitarlo de la pila: arriba del todo sigue el hechizo con sus
 * objetivos, y un momento despues los borra. El aviso solo trae la vista
 * ({@code SpellAbilityView}), que no los tiene. Por eso se comprueba que lo de
 * arriba sea el mismo (por id) y, si no, no se inventa nada.
 *
 * <p>Al invitado de una partida en red no le llega: no tiene la partida, solo
 * vistas ({@code game} es null).
 */
public final class SpellImpact {

    /** {@code CardView} o {@code PlayerView}, sin repetir, en el orden del hechizo. */
    public final List<Object> targets;
    /** El permanente que acaba de entrar lanzado, o null. */
    public final CardView arrived;
    /** Los colores de la carta que lo ha lanzado o activado. */
    public final ColorSet colors;
    /**
     * De donde sale volando hacia sus objetivos: el permanente de la habilidad
     * si esta en la mesa, o si no quien lo ha lanzado ({@code PlayerView}).
     */
    public final Object source;

    private SpellImpact(final List<Object> targets, final CardView arrived, final ColorSet colors,
            final Object source) {
        this.targets = Collections.unmodifiableList(targets);
        this.arrived = arrived;
        this.colors = colors;
        this.source = source;
    }

    /** Si no hay nada que pintar. */
    public boolean isEmpty() {
        return targets.isEmpty() && arrived == null;
    }

    /**
     * Lee lo que acaba de resolverse. Nunca lanza: un fallo aqui no puede
     * romper el oyente del motor, que se traga las excepciones en silencio.
     *
     * @return null si no hay nada que pintar o no se puede saber
     */
    public static SpellImpact read(final Game game, final GameEventSpellResolved e) {
        if (game == null || e == null || e.hasFizzled() || e.spell() == null) {
            return null;
        }
        try {
            if (game.getStack().isEmpty()) {
                return null;
            }
            final SpellAbility sa = game.getStack().peekAbility();
            if (sa == null || sa.getId() != e.spell().getId()) {
                return null;
            }
            final Card host = sa.getHostCard();
            final List<Object> targets = new ArrayList<>();
            for (final TargetChoices tc : sa.getAllTargetChoices()) {
                for (final Card c : tc.getTargetCards()) {
                    final CardView v = c.getView();
                    if (v != null && !targets.contains(v)) {
                        targets.add(v);
                    }
                }
                for (final Player p : tc.getTargetPlayers()) {
                    if (p.getView() != null && !targets.contains(p.getView())) {
                        targets.add(p.getView());
                    }
                }
            }
            CardView arrived = null;
            if (sa.isSpell() && host != null) {
                final Card now = game.getCardState(host, null);
                if (now != null && now.isPermanent() && now.isInPlay()) {
                    arrived = now.getView();
                }
            }
            Object source = null;
            if (!sa.isSpell() && host != null && host.isInPlay()) {
                source = host.getView();
            } else if (sa.getActivatingPlayer() != null) {
                source = sa.getActivatingPlayer().getView();
            }
            final SpellImpact impact = new SpellImpact(targets, arrived,
                    host == null ? ColorSet.C : host.getColor(), source);
            return impact.isEmpty() ? null : impact;
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    @Override
    public String toString() {
        return targets.size() + " objetivo(s)" + (arrived != null ? ", entra " + arrived : "");
    }
}
