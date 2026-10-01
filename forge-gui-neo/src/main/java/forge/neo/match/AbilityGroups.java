package forge.neo.match;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.game.spellability.SpellAbilityView;

/**
 * Las habilidades IGUALES de una carta, juntas en una sola opcion.
 *
 * <p>Reportado en Discord (01-10-2026) con <i>Marvin, Murderous Mimic</i>, que
 * tiene las habilidades activadas de todas tus criaturas: con una docena que
 * hacen "{T}: esta criatura hace dano igual a su fuerza", el menu salia con
 * doce opciones identicas y las que el jugador queria se quedaban fuera de la
 * pantalla. Dos habilidades activadas con el mismo texto, en la misma carta,
 * hacen lo mismo: se ofrecen una vez, y elegirla activa una de ellas — una que
 * se pueda pagar, si la hay.
 *
 * <p>Los HECHIZOS repetidos no se juntan: ahi el texto igual esconde un coste
 * alternativo distinto (ver {@code AbilityMenu.labelsOf}), y son dos opciones
 * de verdad.
 *
 * <p>Java puro: la usan el menu del escritorio ({@code AbilityMenu}) y
 * {@code AndroidMatchUI.getAbilityToPlay}, que la recibe por el jar. La vigila
 * {@code AbilityGroupCheck}.
 */
public final class AbilityGroups {

    private AbilityGroups() {
    }

    /** Una opcion del menu: las habilidades del motor que representa. */
    public static final class Group {
        /** Indices en la lista del motor. El primero es el que se activa. */
        public final List<Integer> members = new ArrayList<>();
        /** Si alguna de ellas se puede pagar ahora. */
        public boolean playable;

        public int chosen() {
            return members.get(0);
        }

        public int size() {
            return members.size();
        }
    }

    public static List<Group> of(final List<SpellAbilityView> abilities) {
        final List<Group> out = new ArrayList<>();
        if (abilities == null) {
            return out;
        }
        final Map<String, Group> byText = new HashMap<>();
        for (int i = 0; i < abilities.size(); i++) {
            final SpellAbilityView sa = abilities.get(i);
            final boolean can = sa != null && sa.canPlay();
            final String text = sa == null ? null : sa.getDescription();
            final boolean joinable = sa != null && !sa.isSpell() && text != null && !text.isBlank();
            Group g = joinable ? byText.get(text) : null;
            if (g == null) {
                g = new Group();
                g.members.add(i);
                g.playable = can;
                out.add(g);
                if (joinable) {
                    byText.put(text, g);
                }
            } else if (can && !g.playable) {
                // Que la que se activa se pueda pagar si alguna se puede.
                g.members.add(0, i);
                g.playable = true;
            } else {
                g.members.add(i);
            }
        }
        return out;
    }
}
