package forge.neo.match;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.game.card.CardView;
import forge.gamemodes.match.input.InputSelectCardsForConvokeOrImprovise;
import forge.interfaces.IGameController;
import forge.player.PlayerControllerHuman;

/**
 * El primer paso de pagar <b>waterbend</b>, <b>convocar</b> o <b>improvisar</b>:
 * "elige que artefactos o criaturas giras para ayudar".
 *
 * <p><b>De donde sale.</b> itch.io, 06-10-2026, con mazos del set de Avatar:
 * <i>"I seem to be unable to pay that cost by tapping lands or using floating
 * mana. It is only allowing me to pay with by tapping creatures and/or
 * artifacts"</i>. Se paga en dos pasos ({@code CostAdjustment}): primero el
 * motor pregunta que giras ({@link InputSelectCardsForConvokeOrImprovise}, y
 * ahi una tierra no vale) y al dar OK llega el pago de siempre, con tierras y
 * reserva. Nada lo decia: el texto del motor, en ingles, era <i>"Choose
 * artifact or creature to tap for Waterbend. Remaining mana cost is {3}. You
 * may select up to -3 more"</i> (el -3 es suyo: resta al reves), con un OK a
 * secas, y tocar una tierra no hacia nada.
 *
 * <p>Esta clase es lo que las dos interfaces comparten (escritorio y Android,
 * por el jar): <b>reconocer</b> el paso por el texto del motor, que es fijo y
 * sin traducir, saber si es el input activo, y decidir si una carta tocada es
 * una tierra que "paga el resto". Cada interfaz pone su texto, su boton
 * "Pagar con mana" y el toque de la tierra: hace OK y la gira en cuanto llega
 * el pago. Ojo ahi: {@code InputPayMana} pide los botones ANTES de mandar su
 * texto, asi que la tierra se suelta al ver el pago, no al cambiar el prompt.
 *
 * <p>Nada de API que Android no tenga en la 26: ni records ni
 * {@code String.isBlank}.
 */
public final class TapToPayStep {

    private TapToPayStep() {
    }

    private static final Pattern PROMPT = Pattern.compile(
            "Choose [^\\n]*? to tap for (Waterbend|Convoke|Improvise)\\.\\s*"
                    + "Remaining mana cost is ([^\\s.]*)");

    /** Lo que dice el prompt de ese paso. */
    public static final class Step {
        /** {@code "waterbend"}, {@code "convoke"} o {@code "improvise"}. */
        public final String kind;
        /** Lo que falta por pagar, como lo escribe el motor: {@code {5}{U}{U}}. */
        public final String left;
        /** Lo que venia delante (la carta y su habilidad), o "". */
        public final String head;

        Step(final String kind, final String left, final String head) {
            this.kind = kind;
            this.left = left;
            this.head = head;
        }

        /** Queda algo por pagar con mana. Con todo cubierto, OK es solo "hecho". */
        public boolean somethingLeft() {
            return !left.isEmpty() && !"0".equals(left) && !"{0}".equals(left);
        }
    }

    /** El paso, si el mensaje del motor es el suyo; si no, null. */
    public static Step parse(final String message) {
        if (message == null) {
            return null;
        }
        final Matcher m = PROMPT.matcher(message);
        if (!m.find()) {
            return null;
        }
        final String kind = m.group(1).toLowerCase(java.util.Locale.ROOT);
        return new Step(kind, m.group(2), message.substring(0, m.start()).trim());
    }

    /** Si el input activo del motor es ese paso. */
    public static boolean isActive(final IGameController gc) {
        if (!(gc instanceof PlayerControllerHuman)) {
            return false;
        }
        try {
            return ((PlayerControllerHuman) gc).getInputQueue().getInput()
                    instanceof InputSelectCardsForConvokeOrImprovise;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /**
     * Si tocar esta carta en ese paso significa "lo pago con mana, empezando
     * por esta": una tierra propia, enderezada, que no sea artefacto ni
     * criatura (esas si las puede elegir el motor ahi, y se le dejan a el).
     *
     * @param mine si la controla el jugador que esta pagando
     */
    public static boolean isLandThatPays(final CardView card, final boolean mine) {
        if (card == null || !mine || card.isTapped()) {
            return false;
        }
        final CardView.CardStateView st = card.getCurrentState();
        return st != null && st.isLand() && !st.isArtifact() && !st.isCreature();
    }
}
