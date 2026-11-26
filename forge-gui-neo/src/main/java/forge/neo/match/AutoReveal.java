package forge.neo.match;

import forge.game.Game;
import forge.game.GameView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.interfaces.IGameController;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/**
 * <b>Lo revelado no para lo que has puesto en automatico</b> (Discord,
 * 26-11-2026: <i>"Cloned Coiling Oracle a few times and hit "Auto" on the
 * stack, but each card revealed makes me also hit Got it"</i>).
 *
 * <p>"Resolverlo todo" ({@link ResolveAll}) y "pasar sola esta habilidad"
 * solo tocan la PRIORIDAD: la revelacion de un disparo ({@code getGui().reveal}
 * desde {@code PlayerControllerHuman.reveal}) es otra cosa y salia igual, un
 * dialogo con su "Entendido" por cada copia. Aqui se dice cuando ese dialogo
 * sobra: lo que se esta resolviendo es <b>tuyo</b> y lo has dejado pasar solo.
 * Lo revelado sigue apuntado en el registro ({@link RevealLog}, que llega
 * antes por {@code showRevealedCards}), y la interfaz puede ensenyarlo sin
 * parar.
 *
 * <p>Lo que <b>no</b> se salta, a proposito:
 * <ul>
 *   <li>Lo de un rival, aunque lo hayas dejado pasar solo: eso es informacion
 *       que no tenias.</li>
 *   <li>Si has pedido que te pare cada revelacion (el pase inteligente al
 *       maximo, {@code YIELD_INTERRUPT_ON_REVEAL}): el jugador ha dicho lo
 *       contrario.</li>
 *   <li>Lo que no ocurre al resolver (un coste que revela una carta, el aviso
 *       de las cartas que la IA no sabe jugar): no viene de nada que hayas
 *       puesto en automatico.</li>
 * </ul>
 *
 * <p>La mano de un rival tampoco pasa por aqui: el motor la pregunta con su
 * propio OK ({@code revealInPrompt}), no con {@code reveal}.
 *
 * <p>Se llama desde el hilo del motor (es quien llama a {@code reveal}), asi
 * que leer la pila del {@code Game} es seguro. Compartida con Android por el
 * jar: nada de API de Java que no tenga en la 26.
 */
public final class AutoReveal {

    private AutoReveal() {
    }

    /** Si la revelacion de ahora se puede ensenyar sin pedir "Entendido". */
    public static boolean skipDialog(final IGameController gc, final GameView gv, final PlayerView me) {
        if (gc == null || gv == null || me == null) {
            return false;
        }
        try {
            if (FModel.getPreferences().getPrefBoolean(FPref.YIELD_INTERRUPT_ON_REVEAL)) {
                return false;
            }
            final Game game = gv.getGame();
            if (game != null) {
                // Lo que se resuelve de verdad, y de quien es (en el invitado
                // de red no hay Game: vale lo de arriba de la pila).
                final forge.game.spellability.SpellAbility now = game.getStack().getResolvingAbility();
                if (now == null || now.getActivatingPlayer() == null
                        || now.getActivatingPlayer().getId() != me.getId()) {
                    return false;
                }
            }
            StackItemView top = null;
            if (gv.getStack() != null) {
                for (final StackItemView s : gv.getStack()) {
                    top = s;
                    break;
                }
            }
            if (top == null || top.getActivatingPlayer() == null
                    || top.getActivatingPlayer().getId() != me.getId()) {
                return false;
            }
            if (ResolveAll.isOn(gc)) {
                return true;
            }
            final String key = top.getKey();
            return top.isAbility() && key != null && !key.isEmpty() && gc.shouldAutoYield(key);
        } catch (final RuntimeException e) {
            // Ante la duda, el dialogo de siempre: preguntar de mas no rompe nada.
            return false;
        }
    }
}
