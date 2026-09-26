package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.LobbyPlayer;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.neo.NeoText;
import forge.player.PlayerControllerHuman;

/**
 * Ejercer y alistar al atacar: una pregunta de si o no por criatura, con la
 * carta delante.
 *
 * <p><b>De donde sale esto.</b> Reportado el 25-09-2026: <i>"Combat Celebrant
 * ejerce, pero no endereza a las demas ni da el combate adicional"</i>. La
 * carta funciona; lo que fallaba era la pregunta. El motor la hace con
 * {@code PlayerControllerHuman.exertAttackers}, que es un {@code order(...)}
 * de "elige cuales" con minimo 0, y en nuestra mesa eso sale como
 * {@code ChoiceDialog}: la carta <b>sin marcar</b>, el titulo
 * <i>"¿Ejercer atacantes? — Ejecutado"</i> (que parece decir que ya esta
 * hecho), "0 elegidas" en letra pequenya y un Aceptar grande y encendido. El
 * gesto natural es pulsar Aceptar, y Aceptar con nada marcado es <b>no
 * ejercer</b>: ni disparo, ni enderezar, ni combate extra. Reproducido tal
 * cual con la mesa de verdad; marcando la carta antes, todo funciona.
 *
 * <p>Es el principio 1 (un control que no hace lo que parece) y el 6 (ejercer
 * no se deshace: la criatura no se endereza en tu siguiente turno). Asi que se
 * pregunta como lo que es, una decision de si o no sobre UNA criatura, con dos
 * botones que dicen lo que hacen. Alistar ({@code enlistAttackers}) es la misma
 * pregunta con el mismo dialogo, y la misma trampa.
 *
 * <p><b>Sin jugador delante</b> (comprobadores, piloto automatico) el
 * {@code confirm} de {@link NeoMatchUI} devuelve el "si" por defecto: se ejerce
 * y se alista todo, que es lo mismo que devolvia antes el {@code order} en ese
 * modo. Los comprobadores no cambian.
 *
 * <p>Va en la misma silla que los otros arreglos con asiento (ver por que en
 * {@link ManaColor}): {@code ManaColor.Asking} hereda de este y este de
 * {@link TriggerSubject.Telling}.
 */
public final class AttackCosts {

    private AttackCosts() {
    }

    /** El controlador humano de siempre, preguntando criatura a criatura. */
    public static class Confirming extends TriggerSubject.Telling {

        public Confirming(final Player player, final LobbyPlayer lobby,
                          final PlayerControllerHuman owner) {
            super(player, lobby, owner);
        }

        @Override
        public List<Card> exertAttackers(final List<Card> attackers) {
            return askEach(attackers, "attack.exert.ask", "attack.exert.yes", "attack.exert.no");
        }

        @Override
        public List<Card> enlistAttackers(final List<Card> attackers) {
            return askEach(attackers, "attack.enlist.ask", "attack.enlist.yes", "attack.enlist.no");
        }

        private List<Card> askEach(final List<Card> attackers, final String ask,
                                   final String yes, final String no) {
            final List<Card> chosen = new ArrayList<>();
            if (attackers == null) {
                return chosen;
            }
            final List<String> options = List.of(NeoText.get(yes), NeoText.get(no));
            for (final Card c : attackers) {
                // "Si" por defecto: es a lo que viene la carta, y es lo que se
                // hace sin nadie delante.
                if (getGui().confirm(c.getView(), NeoText.get(ask, c.getTranslatedName()),
                        true, options)) {
                    chosen.add(c);
                }
            }
            return chosen;
        }
    }
}
