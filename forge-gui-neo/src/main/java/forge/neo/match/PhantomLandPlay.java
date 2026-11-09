package forge.neo.match;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.Card;
import forge.game.card.CardPlayOption;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * <b>Las tierras que el motor deja jugar sin permiso</b> (Discord, 08-11-2026:
 * con Grenzo, Crooked Jailer, <i>"I suddenly got the indication that I could
 * play multiple lands"</i>, todas del cementerio del rival).
 *
 * <p>Grenzo da su {0} con {@code MayPlay$ True | MayPlayDontGrantZonePermissions$ True}
 * a toda carta que no es tuya de valor 3 o menos, este donde este. Esa bandera
 * quiere decir "solo cambia el coste: no te deja jugarla desde esa zona". Para
 * los hechizos el motor lo respeta ({@code SpellAbilityRestriction}, mira
 * {@code grantsZonePermissions}), pero {@code Player.canPlayLand} solo pregunta
 * si hay ALGUN permiso ({@code landSa.getMayPlay() != null}) — y una tierra
 * tiene valor 0. Resultado: toda tierra de los cementerios rivales salia
 * jugable, y clicarla la jugaba de verdad (y la IA igual). Ademas una tierra no
 * se lanza, asi que Grenzo no deberia tocarla nunca.
 *
 * <p>Regla de oro: no se toca el motor. Se repite aqui la misma condicion que
 * usa {@code SpellAbilityRestriction} para los hechizos, y quien la aplica es
 * {@link SafeActions.Guarded} (al clicar y al resaltar) y {@link SafeAi} (lo
 * que elige la IA). Si Card-Forge lo arregla, esto deja de saltar solo.
 */
public final class PhantomLandPlay {

    /** {@code -Dneo.phantomLands=false} lo apaga. */
    static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("neo.phantomLands"));

    private PhantomLandPlay() {
    }

    /** Jugar esta tierra con este permiso, que no deja jugarla desde donde esta. */
    public static boolean illegal(final SpellAbility sa, final Player who) {
        if (!ON || sa == null || who == null || !sa.isLandAbility()) {
            return false;
        }
        try {
            final CardPlayOption option = sa.getMayPlayOption();
            if (option == null || option.grantsZonePermissions()) {
                return false;
            }
            return withoutZonePermission(sa.getHostCard(), who);
        } catch (final RuntimeException e) {
            return false; // ante la duda, decide el motor
        }
    }

    /** La lista sin las jugadas de tierra sin permiso. */
    public static List<SpellAbility> strip(final List<SpellAbility> abilities, final Player who) {
        if (!ON || abilities == null || abilities.isEmpty()) {
            return abilities;
        }
        List<SpellAbility> out = null;
        for (int i = 0; i < abilities.size(); i++) {
            final SpellAbility sa = abilities.get(i);
            if (illegal(sa, who)) {
                if (out == null) {
                    out = new ArrayList<>(abilities.subList(0, i));
                }
            } else if (out != null) {
                out.add(sa);
            }
        }
        return out == null ? abilities : out;
    }

    /**
     * Una tierra de fuera que solo sale jugable por un permiso de ese tipo:
     * para no iluminarla. Las tierras con permiso de verdad (Crucible of
     * Worlds, lo robado con un atraco) no entran.
     */
    public static boolean phantom(final Card card, final Player who) {
        if (!ON || card == null || who == null) {
            return false;
        }
        try {
            if (!card.isLand() || card.mayPlay(who).isEmpty()) {
                return false;
            }
            return withoutZonePermission(card, who);
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** La misma condicion que {@code SpellAbilityRestriction} para un hechizo. */
    private static boolean withoutZonePermission(final Card card, final Player who) {
        if (card == null || card.isInZone(ZoneType.Battlefield)) {
            return false;
        }
        final boolean ownHand = card.isInZone(ZoneType.Hand) && who.equals(card.getOwner());
        if (ownHand) {
            return false;
        }
        for (final CardPlayOption o : card.mayPlay(who)) {
            if (o.grantsZonePermissions()) {
                return false;
            }
        }
        return true;
    }
}
