package forge.neo.match;

import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.TargetRestrictions;
import forge.game.zone.ZoneType;
import forge.neo.NeoText;
import forge.player.PlayerControllerHuman;

/**
 * Por que esa carta no se puede lanzar ahora mismo.
 *
 * <h2>De donde sale esto</h2>
 *
 * <p>Reportado en itch.io el 22-09-2026 con <i>Double Major</i>: <i>"won't
 * target controlled creature, it won't even select the creature card you
 * select... maybe a bug from forge side?"</i>. No era un fallo — la carta dice
 * <i>"copy target creature <b>SPELL</b> you control"</i>, o sea que apunta a un
 * hechizo <b>en la pila</b>, y la pila estaba vacia — pero eso el jugador no
 * podia saberlo: clicaba, no pasaba nada, y lo unico que deciamos era
 * <i>"Ahi no se puede."</i>.
 *
 * <p>Y esa es la parte que si era nuestra. Un mensaje que no explica nada deja
 * al jugador con una sola conclusion razonable — <b>que el juego esta roto</b> —
 * y ademas genera un informe de fallo que no lo es. Es el principio 6 al reves:
 * no basta con impedir la accion, hay que decir <b>por que</b>.
 *
 * <h2>El motor lo sabe, pero no nos lo manda</h2>
 *
 * <p>{@code flashIncorrectAction()} llega sin un solo dato: ni que carta era ni
 * que fallo. Asi que se recupera desde el asiento del controlador, el mismo
 * patron que ya usan {@link TriggerSubject}, {@code ManaColor} y
 * {@code SafeActions}: {@code PlayerControllerHuman} da el jugador y la
 * partida, y con la carta que la interfaz apunto al clicar
 * ({@code NeoMatchUI.lastClicked}) se puede preguntar lo que el motor ya
 * calcula.
 *
 * <p>⚠️ Esto lee {@code Card} y no {@code CardView}, contra la regla general de
 * la seccion 7. Es a proposito y acotado: la restriccion de una habilidad
 * <b>no existe en la vista</b> — ni el prompt de objetivo, ni la velocidad de
 * conjuro, ni si quedan tierras por jugar —, y aqui no se guarda nada ni se
 * duplica estado: se pregunta, se escribe una frase y se tira. El dia que el
 * motor publique esto en {@code CardView}, esta clase sobra.
 *
 * <h2>Lo que NO hace</h2>
 *
 * <p><b>No adivina.</b> Si no reconoce el motivo con certeza, devuelve
 * {@code null} y se queda el mensaje de siempre. Una explicacion equivocada es
 * peor que una generica: manda al jugador a buscar donde no es.
 */
public final class CantPlay {

    private CantPlay() {
    }

    /**
     * Una frase que explique por que no se puede, o {@code null} si no se sabe.
     *
     * @param controller el asiento del humano; {@code null} si no lo es
     * @param clicked    la carta que el jugador acaba de clicar
     */
    public static String reason(final Object controller, final CardView clicked) {
        if (!(controller instanceof PlayerControllerHuman) || clicked == null) {
            return null;
        }
        final PlayerControllerHuman human = (PlayerControllerHuman) controller;
        final Player me = human.getPlayer();
        if (me == null) {
            return null;
        }
        final Card card = inHand(me, clicked);
        if (card == null) {
            // Solo se explica lo que hay en la mano. Una carta de la mesa que
            // no responde puede ser mil cosas (un input a medias, una eleccion
            // en curso), y ahi una frase segura no la hay.
            return null;
        }

        final List<SpellAbility> jugables = card.getAllPossibleAbilities(me, true);
        if (card.isLand()) {
            return jugables.isEmpty() ? landReason(me) : null;
        }
        if (!jugables.isEmpty()) {
            // ⚠️ Que el motor la de por "jugable" NO significa que se pueda
            // lanzar: canPlay() mira zona, tiempo y activador, pero NO si hay
            // objetivo. Ese es exactamente el caso del informe -- Double Major
            // pasa este filtro con la pila vacia -- y por eso la primera
            // version de esta clase devolvia null y no explicaba nada. Lo cazo
            // CantPlayCheck a la primera.
            return noTargetReason(jugables);
        }
        return spellReason(me, card);
    }

    /**
     * Si TODAS sus formas de lanzarla piden un objetivo y no hay ninguno.
     *
     * <p>Tiene que ser <b>todas</b>: si una sola se puede lanzar, la carta se
     * puede lanzar y aqui no hay nada que explicar. Y se exige que el objetivo
     * sea <b>obligatorio</b> ({@code getMinTargets > 0}): un hechizo con
     * objetivo opcional se lanza igual sin nada a lo que apuntar, y decirle al
     * jugador que le falta objetivo seria mandarle a buscar un problema que no
     * tiene.
     */
    private static String noTargetReason(final List<SpellAbility> jugables) {
        String falta = null;
        for (final SpellAbility sa : jugables) {
            if (!sa.isSpell()) {
                continue;
            }
            final TargetRestrictions tgt = sa.getTargetRestrictions();
            if (!sa.usesTargeting() || tgt == null) {
                return null;
            }
            final int minimo = tgt.getMinTargets(sa.getHostCard(), sa);
            // ⚠️ getNumCandidates y NO hasCandidates. hasCandidates devuelve
            // TRUE A CIEGAS cuando el objetivo esta en la pila -- lo dice su
            // propio codigo, "Stack Zone targets are considered later" -- asi
            // que para Double Major, que es justo un hechizo que apunta a la
            // pila, contesta que si hay objetivo con la pila vacia.
            // getNumCandidates si la recorre. Cazado por CantPlayCheck.
            if (minimo <= 0 || tgt.getNumCandidates(sa) >= minimo) {
                // Esta si se puede lanzar: no hay nada que explicar.
                return null;
            }
            if (falta == null) {
                final String what = tgt.getVTSelection();
                falta = what == null || what.isEmpty()
                        ? NeoText.get("why.cant.noTarget")
                        : NeoText.get("why.cant.noTargetIs", what);
            }
        }
        return falta;
    }

    /** La carta del motor que corresponde a esa vista, si esta en tu mano. */
    private static Card inHand(final Player me, final CardView view) {
        for (final Card c : me.getCardsIn(ZoneType.Hand)) {
            if (c.getId() == view.getId()) {
                return c;
            }
        }
        return null;
    }

    /**
     * Una tierra que no se puede jugar: o se acabaron, o no es momento.
     *
     * <p>El orden importa: lo normal con diferencia es haber gastado ya la
     * tierra del turno, y decir "no es tu turno" cuando si lo es manda a
     * cualquiera a mirar donde no es.
     */
    private static String landReason(final Player me) {
        if (!me.getMaxLandPlaysInfinite()
                && me.getLandsPlayedThisTurn() >= me.getMaxLandPlays()) {
            return me.getMaxLandPlays() <= 1
                    ? NeoText.get("why.cant.landUsed")
                    : NeoText.get("why.cant.landUsedN", me.getMaxLandPlays());
        }
        if (!me.canCastSorcery()) {
            return NeoText.get("why.cant.landTiming");
        }
        return null;
    }

    /**
     * Un hechizo que no se puede lanzar.
     *
     * <p>Se miran las habilidades <b>sin</b> filtrar por jugables (el filtro es
     * justo lo que acaba de dejarnos sin ninguna) y se contesta con la primera
     * que sepamos explicar.
     */
    private static String spellReason(final Player me, final Card card) {
        final List<SpellAbility> all = card.getAllPossibleAbilities(me, false);
        for (final SpellAbility sa : all) {
            if (!sa.isSpell()) {
                continue;
            }
            // 1. Sin objetivo legal. Es el caso del reporte, y el unico que el
            //    jugador no puede deducir mirando la mesa: la carta puede pedir
            //    algo que ni siquiera esta en el campo de batalla (un hechizo
            //    en la pila, una carta de un cementerio...).
            if (sa.usesTargeting() && sa.getTargetRestrictions() != null
                    && sa.getTargetRestrictions().getNumCandidates(sa)
                        < sa.getTargetRestrictions().getMinTargets(sa.getHostCard(), sa)) {
                // Con las propias palabras de la carta cuando las trae: dice
                // exactamente que hace falta, y ademas ya viene traducido por
                // el motor.
                final String what = sa.getTargetRestrictions().getVTSelection();
                return what == null || what.isEmpty()
                        ? NeoText.get("why.cant.noTarget")
                        : NeoText.get("why.cant.noTargetIs", what);
            }
            // 2. Velocidad de conjuro: en tu turno y con la pila vacia.
            if (sa.getRestrictions() != null && sa.getRestrictions().isSorcerySpeed()
                    && !me.canCastSorcery()) {
                return NeoText.get("why.cant.sorcerySpeed");
            }
        }
        return null;
    }
}
