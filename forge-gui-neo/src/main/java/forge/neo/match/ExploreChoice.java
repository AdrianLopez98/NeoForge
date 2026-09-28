package forge.neo.match;

import java.util.List;
import java.util.Map;

import forge.LobbyPlayer;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollectionView;
import forge.game.player.Player;
import forge.game.player.PlayerActionConfirmMode;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.neo.NeoText;
import forge.player.PlayerControllerHuman;
import forge.util.Localizer;

/**
 * Explorar: una sola pregunta, con la carta delante y dos botones que dicen lo
 * que hacen.
 *
 * <p><b>De donde sale esto.</b> Pedido en itch.io y en Discord el 28-09-2026:
 * cuando una criatura explora y sale una carta que no es tierra, hay que dar
 * OK al dialogo que ensenya la carta y luego, abajo a la derecha, contestar
 * si o no a "¿poner X en tu cementerio?". Dos gestos en dos sitios para una
 * sola decision. El motor lo hace asi ({@code ExploreEffect.resolve}): primero
 * {@code GameAction.reveal} y luego {@code confirmAction}, que en
 * {@code PlayerControllerHuman} es un {@code InputConfirm} — los botones de la
 * barra, no un dialogo.
 *
 * <p>Asi que aqui se hacen dos cosas, y las dos solo cuando explora
 * <b>este</b> jugador y la carta no es tierra:
 * <ol>
 *   <li>Se <b>calla el revelado</b> a su propio duenyo. La pregunta que viene
 *       justo detras ya ensenya la carta; a los rivales se les sigue
 *       ensenyando, que es para lo que se revela.</li>
 *   <li>La pregunta sale como el resto de preguntas con carta
 *       ({@code CardPromptDialog}): "Al cementerio" / "Dejarla encima".</li>
 * </ol>
 *
 * <p><b>Y un tercer boton</b>, que tambien pedian: con varias exploraciones
 * seguidas, si dejas la carta encima la siguiente revela <b>la misma</b>, y la
 * pregunta se repite identica. "Dejarla encima y no volver a preguntar" la
 * recuerda para esa carta durante el turno. Solo se recuerda lo que el jugador
 * ha elegido a proposito; mandarla al cementerio no se recuerda nunca (ya no
 * esta encima).
 *
 * <p>La pregunta del motor se reconoce por su habilidad ({@code ApiType.Explore})
 * y por el parametro {@code RevealedCard}, que solo pone {@code ExploreEffect};
 * el revelado, por el prefijo {@code lblRevealedForExplore}. Si algun dia
 * cambian, esto deja de saltar y se vuelve a los dos pasos de siempre — no se
 * rompe nada.
 *
 * <p>Va en la misma silla que los otros arreglos con asiento (ver por que en
 * {@link ManaColor}): {@code ManaColor.Asking} hereda de este y este de
 * {@link AttackCosts.Confirming}.
 */
public final class ExploreChoice {

    private ExploreChoice() {
    }

    /** El controlador humano de siempre, con la exploracion en un dialogo. */
    public static class Asking extends AttackCosts.Confirming {

        /** La carta que el jugador pidio dejar encima sin volver a preguntar. */
        private int keptCardId = -1;
        private int keptTurn = -1;

        public Asking(final Player player, final LobbyPlayer lobby,
                      final PlayerControllerHuman owner) {
            super(player, lobby, owner);
        }

        @Override
        public void reveal(final CardCollectionView cards, final ZoneType zone, final Player owner,
                           final String message, final boolean addSuffix) {
            if (isOwnExploreReveal(cards, zone, owner, message)) {
                // Lo ensenya la pregunta que viene justo detras.
                return;
            }
            super.reveal(cards, zone, owner, message, addSuffix);
        }

        @Override
        public boolean confirmAction(final SpellAbility sa, final PlayerActionConfirmMode mode,
                                     final String message, final List<String> options,
                                     final Card cardToShow, final Map<String, Object> params) {
            if (!isExploreQuestion(sa, cardToShow, params)
                    || !(getGui() instanceof NeoMatchUI ui)) {
                return super.confirmAction(sa, mode, message, options, cardToShow, params);
            }
            final int turn = getGame().getPhaseHandler().getTurn();
            if (cardToShow.getId() == keptCardId && turn == keptTurn) {
                return false;
            }
            // "Dejarla encima" por defecto: es lo que no pierde nada.
            final int picked = ui.askWithCard(cardToShow.getView(),
                    NeoText.get("explore.ask", cardToShow.getTranslatedName()),
                    List.of(NeoText.get("explore.graveyard"), NeoText.get("explore.top"),
                            NeoText.get("explore.topAlways")),
                    1);
            if (picked == 2) {
                keptCardId = cardToShow.getId();
                keptTurn = turn;
            }
            return picked == 0;
        }

        private boolean isOwnExploreReveal(final CardCollectionView cards, final ZoneType zone,
                                           final Player owner, final String message) {
            if (owner != getPlayer() || zone != ZoneType.Library || message == null
                    || cards == null || cards.size() != 1 || cards.getFirst().isLand()
                    || !(getGui() instanceof NeoMatchUI)) {
                return false;
            }
            return message.startsWith(Localizer.getInstance().getMessage("lblRevealedForExplore"));
        }

        private static boolean isExploreQuestion(final SpellAbility sa, final Card card,
                                                 final Map<String, Object> params) {
            return sa != null && card != null && params != null
                    && sa.getApi() == ApiType.Explore
                    && params.get("RevealedCard") == card;
        }
    }
}
