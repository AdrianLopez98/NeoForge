package forge.neo.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import forge.game.GameEntity;
import forge.game.player.Player;
import forge.game.player.PlayerView;
import forge.gamemodes.match.input.Input;
import forge.gamemodes.match.input.InputSelectEntitiesFromList;
import forge.player.PlayerControllerHuman;
import forge.util.Localizer;

/**
 * <b>El motor espera que elijas un JUGADOR, y no lo dice</b> (Discord,
 * 08-10-2026, con <i>Curator of Destinies</i> en Commander: <i>"the choice
 * interface disappeared ... the game then was waiting"</i>).
 *
 * <p>Cuando una carta deja la decision a "un rival" y hay varios, el motor te
 * pregunta primero CUAL — {@code TwoPilesEffect} (quien separa y quien elige
 * pila: <i>Curator of Destinies</i>, <i>Fact or Fiction</i>...),
 * {@code DigEffect} y {@code ChangeZoneEffect} —. Lo pregunta con
 * {@code chooseSingleEntityForEffect} sobre una lista de jugadores, y como son
 * jugadores {@code PlayerControllerHuman.useSelectCardsInput} dice que si: sale
 * un {@code InputSelectEntitiesFromList} que solo se contesta <b>clicando un
 * retrato</b>. Sin dialogo, sin ninguna carta marcada, con OK y Cancelar
 * apagados y con un prompt que dice, literalmente, <b>"Chooser:"</b>. El
 * jugador cerraba el dialogo anterior (el de mirar las cinco cartas) y la
 * partida se quedaba "esperando" sin forma aparente de seguir. A uno contra uno
 * no pasa: con un solo rival el motor no pregunta.
 *
 * <p>Aqui solo se LEE lo que el motor ya tiene: el input vivo y sus
 * {@code getValidChoices()}. Quien decide si la eleccion vale es el motor
 * ({@code selectPlayer}, el mismo que un clic en el retrato).
 *
 * <p>Pura y compartida con Android por el jar (nada de {@code List.of}).
 */
public final class PlayerPick {

    private PlayerPick() {
    }

    /** Un jugador que se puede elegir ahora mismo, y si ya esta elegido. */
    public static final class Choice {
        private final PlayerView player;
        private final boolean picked;

        Choice(final PlayerView player, final boolean picked) {
            this.player = player;
            this.picked = picked;
        }

        public PlayerView player() {
            return player;
        }

        /** Ya elegido (en una eleccion de varios, volver a clicarlo lo quita). */
        public boolean picked() {
            return picked;
        }
    }

    /**
     * Los jugadores entre los que el motor espera que elijas, si lo que espera
     * es un jugador.
     *
     * <p>Solo cuenta un {@code InputSelectEntitiesFromList} en el que <b>todo</b>
     * lo elegible son jugadores: si hay cartas mezcladas, esas se clican en la
     * mesa como siempre, y los objetivos ({@code InputSelectTargets}) no pasan
     * por aqui — ese prompt ya dice "elige un objetivo".
     *
     * @param controller el {@code IGameController} del jugador local; si no es
     *                   el del motor de esta maquina (el invitado de una partida
     *                   en red), no se sabe y sale vacio
     * @return vacio si no se espera un jugador
     */
    public static List<Choice> pending(final Object controller) {
        if (!(controller instanceof PlayerControllerHuman)) {
            return Collections.emptyList();
        }
        try {
            final Input input = ((PlayerControllerHuman) controller).getInputQueue().getInput();
            if (!(input instanceof InputSelectEntitiesFromList)) {
                return Collections.emptyList();
            }
            final InputSelectEntitiesFromList<?> select = (InputSelectEntitiesFromList<?>) input;
            final List<Choice> out = new ArrayList<>();
            for (final GameEntity e : select.getValidChoices()) {
                if (!(e instanceof Player)) {
                    return Collections.emptyList();
                }
                out.add(new Choice(((Player) e).getView(), select.getSelected().contains(e)));
            }
            return out;
        } catch (final RuntimeException e) {
            // El input cambia en el hilo del motor mientras se lee: mejor no
            // ensenyar nada que ensenyar lo de la pregunta anterior.
            return Collections.emptyList();
        }
    }

    /**
     * El "Chooser:" de esas preguntas, que no dice nada.
     *
     * <p>El motor lo compone con {@code lblChooser} + ":" en el idioma de la
     * partida ("Chooser:", "Elector:"...) y lo pone como ultima linea, detras de
     * la carta de la que viene. Se reconoce por la clave, no por el ingles.
     */
    public static boolean isChooserPrompt(final String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        final String chooser = Localizer.getInstance().getMessage("lblChooser") + ":";
        return lastLine(message).equals(chooser);
    }

    /**
     * Cambia el "Chooser:" por una frase que diga que hacer.
     *
     * @return el mensaje con la ultima linea cambiada, o tal cual si no era eso
     */
    public static String explainChooser(final String message, final String explanation) {
        if (!isChooserPrompt(message)) {
            return message;
        }
        final String trimmed = stripEnd(message);
        final int nl = trimmed.lastIndexOf('\n');
        return nl < 0 ? explanation : trimmed.substring(0, nl + 1) + explanation;
    }

    private static String lastLine(final String message) {
        final String trimmed = stripEnd(message);
        final int nl = trimmed.lastIndexOf('\n');
        return (nl < 0 ? trimmed : trimmed.substring(nl + 1)).trim();
    }

    private static String stripEnd(final String s) {
        int end = s.length();
        while (end > 0 && Character.isWhitespace(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(0, end);
    }
}
