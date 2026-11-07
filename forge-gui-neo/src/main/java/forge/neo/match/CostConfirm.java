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
            if (card == null || question == null || macros().isReplaying()) {
                return super.confirmPayment(costPart, question, sa);
            }
            final boolean fabricateFirst = isFabricate(sa);
            if (ui == null && fabricateFirst) {
                return super.confirmPayment(costPart, question, sa);
            }
            if (askingGame != getGame()) {
                // Otra partida: lo de "no volver a preguntar" era de la anterior.
                noMoreAsking.clear();
                askingGame = getGame();
            }
            final boolean fabricate = fabricateFirst;
            // EL COSTE ENTERO, ANTES DE PAGAR NADA (Discord, 07-11-2026, Lion's
            // Eye Diamond: "I discarded my entire hand but didn't get LED
            // sac"). Forge pregunta parte a parte y en su orden: primero
            // descartar la mano y DESPUES sacrificar; un "No" en la segunda
            // cancela la activacion, pero la mano ya no vuelve (el descarte no
            // tiene reembolso). Con dos o mas partes que no se deshacen, se
            // pregunta una vez por todo en la primera, y las demas de esa
            // misma activacion ya van contestadas.
            final List<CostPart> heavy = fabricate ? List.of() : heavyParts(sa);
            if (heavy.size() >= 2 && sameActivation(sa, wholeSa) && removeSame(wholePending, costPart)) {
                macros().addRememberedAction(new PayCostAction(card));
                return true;
            }
            wholeSa = null;
            wholePending.clear();
            final boolean whole = heavy.size() >= 2;
            final String key = (fabricate ? "kw:FABRICATE" : sa.getHostCard().getName()) + "|"
                    + (whole ? "whole" : costPart == null ? "" : costPart.getClass().getSimpleName());
            final String asked = whole ? NeoText.get("cost.confirm.whole",
                    forge.neo.card.CardText.nameOf(card.getCurrentState()), wholeCost(sa)) : question;
            if (ui == null) {
                // Sin nuestra mesa (Android, el otro lado de una partida en
                // red): la pregunta de Forge de siempre, pero con el coste
                // entero si toca. Forge apunta el "Si" para las macros.
                final boolean yes = super.confirmPayment(costPart, asked, sa);
                rememberWhole(yes && whole, sa, heavy, costPart);
                return yes;
            }
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
                final int picked = ui.askWithCard(card, asked.replace("\n", " "), options, 0);
                yes = picked != 1 && picked != 3;
                if (picked == 2 || picked == 3) {
                    noMoreAsking.put(key, yes);
                }
            }
            if (yes) {
                // Lo mismo que apunta Forge al contestar "Si": las macros lo graban.
                macros().addRememberedAction(new PayCostAction(card));
            }
            rememberWhole(yes && whole, sa, heavy, costPart);
            return yes;
        }

        /** Contestado el coste entero: lo que queda de esa activacion ya no se pregunta. */
        private void rememberWhole(final boolean on, final SpellAbility sa, final List<CostPart> heavy,
                                   final CostPart answered) {
            if (!on) {
                return;
            }
            wholeSa = sa;
            wholePending.addAll(heavy);
            // La contestada fuera (por tipo y texto: puede ser una copia). Si
            // se quedara, la siguiente activacion empezaria contestada.
            removeSame(wholePending, answered);
        }

        /** La activacion cuyo coste entero ya se ha contestado, y lo que le queda por confirmar. */
        private SpellAbility wholeSa;
        private final List<CostPart> wholePending = new java.util.ArrayList<>();

        /**
         * La misma activacion. ⚠️ No por identidad: Forge paga con una COPIA
         * del coste (y a veces de la habilidad), asi que la parte que llega
         * en la segunda pregunta no es el objeto de {@code getPayCosts()}.
         * Visto con Lion's Eye Diamond: descartaba y volvia a preguntar.
         */
        private static boolean sameActivation(final SpellAbility a, final SpellAbility b) {
            if (a == null || b == null) {
                return false;
            }
            return a == b || a.getHostCard() == b.getHostCard()
                    && String.valueOf(a.getPayCosts()).equals(String.valueOf(b.getPayCosts()));
        }

        private static boolean removeSame(final List<CostPart> parts, final CostPart part) {
            for (int i = 0; i < parts.size(); i++) {
                if (parts.get(i) == part || part != null && parts.get(i).getClass() == part.getClass()
                        && String.valueOf(parts.get(i)).equals(String.valueOf(part))) {
                    parts.remove(i);
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Las partes del coste que no se deshacen: todo menos el mana y girar o
     * enderezar (eso Forge lo devuelve si se cancela).
     */
    static List<CostPart> heavyParts(final SpellAbility sa) {
        final List<CostPart> out = new java.util.ArrayList<>();
        final forge.game.cost.Cost cost = sa == null ? null : sa.getPayCosts();
        if (cost == null) {
            return out;
        }
        for (final CostPart p : cost.getCostParts()) {
            if (!(p instanceof forge.game.cost.CostPartMana) && !(p instanceof forge.game.cost.CostTap)
                    && !(p instanceof forge.game.cost.CostUntap)) {
                out.add(p);
            }
        }
        return out;
    }

    /** El coste entero en una linea («Sacrifice Lion's Eye Diamond, Discard your hand»). */
    static String wholeCost(final SpellAbility sa) {
        final List<String> bits = new java.util.ArrayList<>();
        for (final CostPart p : heavyParts(sa)) {
            String t = String.valueOf(p);
            if (sa.getHostCard() != null) {
                t = t.replace("CARDNAME", sa.getHostCard().getName());
            }
            bits.add(t.trim());
        }
        return String.join(", ", bits);
    }
}
