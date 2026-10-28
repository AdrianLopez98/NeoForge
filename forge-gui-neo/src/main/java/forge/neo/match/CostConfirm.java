package forge.neo.match;

import java.util.List;

import forge.LobbyPlayer;
import forge.game.card.CardView;
import forge.game.cost.CostPart;
import forge.game.player.Player;
import forge.game.player.actions.PayCostAction;
import forge.game.spellability.SpellAbility;
import forge.neo.NeoText;
import forge.player.PlayerControllerHuman;
import forge.util.Localizer;

/**
 * "¿Sacrificar Terrenos expansivos?" en el centro, con la carta delante — y no
 * abajo a la derecha, donde nadie la ve.
 *
 * <p><b>De donde sale esto.</b> Discord, 13-10-2026: <i>"the game stops
 * responding to interactions occasionally... I click on a sacrifice land and
 * the game looks at me wondering what I am doing"</i>. Lo que pasaba: un coste
 * que se paga con la propia carta (sacrificarla, exiliarla, descartarla...) se
 * confirma con {@code PlayerControllerHuman.confirmPayment}, y en escritorio eso
 * es un {@code InputConfirm}: la pregunta en el recuadro de abajo a la derecha y
 * los botones de la barra renombrados a Si/No. Ni dialogo ni carta. El jugador
 * clica la fetch, la ve girarse y se queda mirandola; la pregunta esta en la
 * esquina, y volver a clicar la tierra no hace nada porque el motor espera la
 * respuesta. Con Tesoros, Pistas y Comida, igual, y a media paga.
 *
 * <p>Forge en el movil ya lo pregunta asi: con {@code isLibgdxPort()} ese mismo
 * metodo llama a {@code confirm(carta, pregunta)}, el dialogo con la carta.
 * Aqui se hace lo mismo sobrescribiendo el metodo (es publico), con un tercer
 * boton que pidio Ana: <b>"Si, y no volver a preguntar en esta partida"</b>,
 * que se acuerda por nombre de carta y tipo de coste — con cinco Tesoros, una
 * pregunta y no cinco. "No" sigue cancelando la activacion: es la red para un
 * clic por error, y sacrificar no se deshace (principio 6).
 *
 * <p><b>Fabricate</b> (Discord, 28-10-2026, con Cayth, Famed Mechanist: <i>"a
 * 'No and don't ask again' if I just wanted to make a Servo for each
 * trigger"</i>) tambien pregunta por aqui: es un "salvo que pongas el
 * contador" ({@code UnlessCost$ AddCounter}), y "No" es la ficha. Ahi, y solo
 * ahi, sale un cuarto boton, <b>"No, y no volver a preguntar"</b>: en un coste
 * de sacrificar, "No" cancela la activacion, y fijarlo para toda la partida te
 * dejaria sin poder usar la carta. Y lo de Fabricate se recuerda por la palabra
 * clave, no por la carta: con Cayth la tienen todas tus criaturas, y seria una
 * pregunta por criatura.
 *
 * <p>Se deja como estaba (la pregunta de Forge) mientras se reproduce una macro
 * — la reproduccion contesta ese {@code InputConfirm}, no un dialogo nuestro —
 * y cuando la interfaz no es una {@link NeoMatchUI} (el otro lado de una
 * partida en red).
 *
 * <p>Va en la misma silla que los otros arreglos con asiento (ver por que en
 * {@link ManaColor}): {@link AttackCosts.Confirming} hereda de este y este de
 * {@link TriggerSubject.Telling}.
 */
public final class CostConfirm {

    private CostConfirm() {
    }

    /** Si es el "pon el contador o crea la ficha" de Fabricate (la palabra clave, venga de donde venga). */
    static boolean isFabricate(final SpellAbility sa) {
        for (SpellAbility s = sa; s != null;
             s = s instanceof forge.game.spellability.AbilitySub sub ? sub.getParent() : null) {
            if (s.isKeyword(forge.game.keyword.Keyword.FABRICATE)) {
                return true;
            }
            final forge.game.trigger.Trigger t = s.getTrigger();
            if (t != null && t.isKeyword(forge.game.keyword.Keyword.FABRICATE)) {
                return true;
            }
        }
        return false;
    }

    /** El controlador humano de siempre, con los costes de la propia carta en un dialogo. */
    public static class Asking extends TriggerSubject.Telling {

        /** Lo que no hay que volver a preguntar (y que se contesto), y de que partida es. */
        private final java.util.Map<String, Boolean> noMoreAsking = new java.util.HashMap<>();
        private Object askingGame;

        public Asking(final Player player, final LobbyPlayer lobby,
                      final PlayerControllerHuman owner) {
            super(player, lobby, owner);
        }

        /** El companero que se dio al motor en lugar de "ninguno" ({@link CompanionDecline}). */
        private forge.game.card.Card declinedCompanion;

        @Override
        public <T extends forge.game.GameEntity> T chooseSingleEntityForEffect(
                final forge.util.collect.FCollectionView<T> optionList,
                final forge.game.player.DelayedReveal delayedReveal, final SpellAbility sa,
                final String title, final boolean isOptional, final Player targetedPlayer,
                final java.util.Map<String, Object> params) {
            final T picked = super.chooseSingleEntityForEffect(optionList, delayedReveal, sa, title,
                    isOptional, targetedPlayer, params);
            // "Ninguno" al elegir companero: el motor no lo sabe tomar.
            if (picked == null && sa != null && sa.getApi() == forge.game.ability.ApiType.CompanionChoose
                    && optionList != null && !optionList.isEmpty()
                    && optionList.get(0) instanceof forge.game.card.Card first) {
                declinedCompanion = CompanionDecline.stand(first);
                return optionList.get(0);
            }
            return picked;
        }

        @Override
        public java.util.Map<forge.deck.DeckSection, List<? extends forge.item.PaperCard>> complainCardsCantPlayWell(
                final forge.deck.Deck myDeck) {
            undoDeclinedCompanion();
            return super.complainCardsCantPlayWell(myDeck);
        }

        @Override
        public boolean mulliganKeepHand(final Player firstPlayer, final int cardsToReturn) {
            undoDeclinedCompanion();
            return super.mulliganKeepHand(firstPlayer, cardsToReturn);
        }

        private void undoDeclinedCompanion() {
            final forge.game.card.Card c = declinedCompanion;
            if (c != null) {
                declinedCompanion = null;
                CompanionDecline.undo(getPlayer(), c);
            }
        }

        @Override
        public boolean confirmPayment(final CostPart costPart, final String question, final SpellAbility sa) {
            final NeoMatchUI ui = NeoMatchUI.of(getGui());
            final CardView card = sa == null ? null : sa.getCardView();
            if (ui == null || card == null || question == null || macros().isReplaying()) {
                return super.confirmPayment(costPart, question, sa);
            }
            if (askingGame != getGame()) {
                // Otra partida: lo de "no volver a preguntar" era de la anterior.
                noMoreAsking.clear();
                askingGame = getGame();
            }
            final boolean fabricate = isFabricate(sa);
            final String key = (fabricate ? "kw:FABRICATE" : sa.getHostCard().getName()) + "|"
                    + (costPart == null ? "" : costPart.getClass().getSimpleName());
            final Boolean remembered = noMoreAsking.get(key);
            boolean yes;
            if (remembered != null) {
                yes = remembered;
            } else {
                final Localizer loc = Localizer.getInstance();
                final List<String> options = fabricate
                        ? List.of(loc.getMessage("lblYes"), loc.getMessage("lblNo"),
                                NeoText.get("cost.confirm.always"), NeoText.get("cost.confirm.neverAgain"))
                        : List.of(loc.getMessage("lblYes"), loc.getMessage("lblNo"),
                                NeoText.get("cost.confirm.always"));
                final int picked = ui.askWithCard(card, question.replace("\n", " "), options, 0);
                yes = picked != 1 && picked != 3;
                if (picked == 2 || picked == 3) {
                    noMoreAsking.put(key, yes);
                }
            }
            if (yes) {
                // Lo mismo que apunta Forge al contestar "Si": las macros lo graban.
                macros().addRememberedAction(new PayCostAction(card));
            }
            return yes;
        }
    }
}
