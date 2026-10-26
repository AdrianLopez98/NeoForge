package forge.neo.card;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.player.Player;
import forge.game.staticability.StaticAbility;
import forge.game.staticability.StaticAbilityMode;
import forge.game.zone.ZoneType;
import forge.neo.NeoText;
import forge.util.CardTranslation;

/**
 * Lo que OTRAS cartas le hacen a esta: "no se pueden activar (Collector
 * Ouphe)", "no puede bloquear (Arrest)", "provocada por Ana". Pedido en Discord
 * el 23-10-2026: la carta no lo dice, y averiguar por que un artefacto no se
 * deja activar era recorrer la mesa entera.
 *
 * <p>El motor no lo publica en el {@code CardView}: se lee de la partida. Las
 * restricciones son habilidades estaticas de otras cartas (modos {@code Cant*}
 * y {@code Must*}, y poco mas) cuyo {@code ValidCard} incluye a esta; la
 * provocacion va en la propia carta ({@code Card.getGoaded}).
 *
 * <p>La partida se guarda con una referencia DEBIL (principio 11): esto es un
 * {@code static} y no puede sujetar una partida acabada. Sin partida aqui (el
 * invitado en red) no hay nada que decir y la lista sale vacia. Se lee desde el
 * hilo de interfaz mientras el motor juega: cualquier fallo de esa lectura es
 * "nada que decir", nunca una excepcion.
 *
 * <p>Compartida con Android por el jar: nada de JavaFX.
 */
public final class AffectedBy {

    /** Una linea: la carta (o jugador) responsable y lo que hace. */
    public record Line(String source, String text) {
    }

    private static volatile WeakReference<Game> game = new WeakReference<>(null);

    private AffectedBy() {
    }

    /** La partida que se esta pintando ({@code openView}); null la olvida. */
    public static void setGame(final Game g) {
        game = new WeakReference<>(g);
    }

    /**
     * Esa misma partida, para quien la necesite fuera de la mesa: el sonido
     * de destruir segun el tipo ({@code forge.neo.sound.DestroySound}). La
     * registran {@code NeoMatchUI} y la de Android al abrir la mesa.
     */
    public static Game current() {
        return game.get();
    }

    /** Lo que le afecta a esa carta ahora mismo; vacio si nada o si no se sabe. */
    public static List<Line> of(final CardView view) {
        final Game g = game.get();
        if (g == null || view == null) {
            return List.of();
        }
        try {
            final Card card = g.findById(view.getId());
            return card == null ? List.of() : of(g, card);
        } catch (final RuntimeException e) {
            return List.of();
        }
    }

    /** Lo mismo por id de carta (Android: su modelo de la mesa lleva el id, no el CardView). */
    public static List<Line> ofId(final int cardId) {
        final Game g = game.get();
        if (g == null) {
            return List.of();
        }
        try {
            final Card card = g.findById(cardId);
            return card == null ? List.of() : of(g, card);
        } catch (final RuntimeException e) {
            return List.of();
        }
    }

    /** Una linea lista para leer: "Collector Ouphe: ..." (la de provocar ya nombra a quien). */
    public static String text(final Line l) {
        return l.text().contains(l.source()) ? l.text() : l.source() + ": " + l.text();
    }

    static List<Line> of(final Game g, final Card card) {
        final List<Line> out = new ArrayList<>();
        if (card.isGoaded()) {
            final List<String> who = new ArrayList<>();
            for (final Player p : card.getGoaded()) {
                who.add(p.getName());
            }
            final String names = String.join(", ", who);
            out.add(new Line(names, NeoText.get("affected.goad", names)));
        }
        // Una linea por habilidad, aunque haya dos copias de la misma carta:
        // "Collector Ouphe" dos veces no dice nada mas que una.
        final Set<String> seen = new LinkedHashSet<>();
        for (final Card host : g.getCardsIn(ZoneType.STATIC_ABILITIES_SOURCE_ZONES)) {
            if (host == card || host.getId() == card.getId()) {
                continue; // lo suyo ya lo dice su texto
            }
            for (final StaticAbility st : host.getStaticAbilities()) {
                if (!restricts(st) || !st.hasParam("ValidCard")
                        || !st.matchesValidParam("ValidCard", card)) {
                    continue;
                }
                if (st.hasParam("AffectedZone")
                        && !card.isInZone(ZoneType.smartValueOf(st.getParam("AffectedZone")))) {
                    continue;
                }
                if (!st.checkConditions()) {
                    continue;
                }
                final String desc = describe(st, host);
                if (desc.isEmpty()) {
                    continue;
                }
                final String source = CardTranslation.getTranslatedName(host.getName());
                if (seen.add(source + "|" + desc)) {
                    out.add(new Line(source, desc));
                }
            }
        }
        return out;
    }

    /**
     * Las que le PROHIBEN u OBLIGAN algo a la carta. Fuera las de coste (que
     * valen para todo lo que lanzas) y las continuas (P/T, palabras clave: eso
     * ya se ve en la carta y en la ficha de la ampliacion).
     */
    private static boolean restricts(final StaticAbility st) {
        for (final StaticAbilityMode m : st.getMode()) {
            final String n = m.name();
            if (n.startsWith("Cant") || n.startsWith("Must") || m == StaticAbilityMode.DisableTriggers
                    || m == StaticAbilityMode.AssignNoCombatDamage) {
                return true;
            }
        }
        return false;
    }

    private static String describe(final StaticAbility st, final Card host) {
        String d = st.toString();
        if (d == null || d.isBlank()) {
            return "";
        }
        d = CardTranslation.translateSingleDescriptionText(d, host);
        return d.replace("CARDNAME", CardTranslation.getTranslatedName(host.getName())).trim();
    }
}
