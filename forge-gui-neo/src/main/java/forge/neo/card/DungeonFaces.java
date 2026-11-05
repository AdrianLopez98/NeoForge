package forge.neo.card;

import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.card.CardRules;
import forge.game.card.CardFaceView;
import forge.game.card.CardView;
import forge.item.PaperToken;

/**
 * "Which dungeon do you want to venture into?": el motor pregunta con NOMBRES
 * ({@code VentureEffect} → {@code chooseSingleCardFace} → {@link CardFaceView}),
 * y elegir entre "Lost Mine of Phandelver" y "Tomb of Annihilation" sin ver
 * sus salas es a ciegas. Discord, 05-11-2026: <i>"include the cards of the
 * dungeons themselves rather than the title so that we can actually see what's
 * in them"</i>.
 *
 * <p>Las mazmorras son fichas ({@code res/tokenscripts}): de un nombre se saca
 * su ficha y de ahi una carta para pintar ({@link CardView#getCardForUi}), la
 * misma que usan las pantallas fuera de partida. Clase pura, la usan las DOS
 * interfaces: el escritorio en {@code NeoMatchUI.getChoices} y Android en
 * {@code AndroidMatchUI.opcionDe}.
 */
public final class DungeonFaces {

    private DungeonFaces() {
    }

    /** Si TODAS las opciones son nombres de mazmorra (y hay alguna). */
    public static boolean allDungeons(final List<?> options) {
        if (options == null || options.isEmpty()) {
            return false;
        }
        for (final Object o : options) {
            if (!(o instanceof CardFaceView face) || scriptOf(face.getName()) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * La carta de una mazmorra para pintarla, o null si no es una mazmorra o
     * algo falla (y entonces se ensenya el nombre, como siempre).
     */
    public static CardView viewOf(final Object option) {
        if (!(option instanceof CardFaceView face)) {
            return null;
        }
        try {
            final String script = scriptOf(face.getName());
            if (script == null) {
                return null;
            }
            final PaperToken token = StaticData.instance().getAllTokens().getToken(script);
            return token == null ? null : CardView.getCardForUi(token);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** El nombre del guion de la ficha de esa mazmorra, o null. */
    static String scriptOf(final String name) {
        if (name == null || StaticData.instance() == null) {
            return null;
        }
        for (final Map.Entry<String, CardRules> e : StaticData.instance().getAllTokens().getRules().entrySet()) {
            final CardRules rules = e.getValue();
            if (rules != null && rules.getType().isDungeon() && name.equals(rules.getMainPart().getName())) {
                return e.getKey();
            }
        }
        return null;
    }
}
