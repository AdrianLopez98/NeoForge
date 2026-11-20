package forge.neo.card;

import java.util.Locale;

import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.neo.NeoText;

/**
 * <b>Que hace cada contador</b> (Discord, 20-11-2026: <i>"Within the card text
 * view, would it be possible to show these buffs/effects, such as "Stun"
 * already shown, but then show their rules text? ... what does "+1" signify?"</i>).
 * Sin JavaFX: lo usan el PC y Android.
 *
 * <p>El motor no trae explicacion de los contadores, solo su nombre y su color.
 * Pero las reglas solo dan significado PROPIO a unos pocos (las del 122.1 y
 * los que usan las mecanicas de siempre: lealtad, defensa, sagas, niveles,
 * tiempo...); el resto ("carga", "aceite", "botin"...) no hacen nada por si
 * mismos y lo que significan lo dice la carta que los usa. Asi que hay texto
 * propio para esos ({@code counter.help.<NOMBRE>}), uno para los contadores de
 * palabra clave (volar, arrollar...) y uno generico para todos los demas: nunca
 * se queda un contador sin explicacion.
 */
public final class CounterHelp {

    private CounterHelp() {
    }

    /** Los que tienen texto propio. Si se anyade uno, su clave en los 11 idiomas. */
    static final CounterEnumType[] EXPLAINED = {
        CounterEnumType.P1P1, CounterEnumType.M1M1, CounterEnumType.LOYALTY, CounterEnumType.DEFENSE,
        CounterEnumType.STUN, CounterEnumType.SHIELD, CounterEnumType.FINALITY, CounterEnumType.LORE,
        CounterEnumType.LEVEL, CounterEnumType.TIME,
    };

    /** El nombre que se lee: "+1/+1", "Stun"... (no "+1" ni "STUN" de la pastilla). */
    public static String name(final CounterType t) {
        if (t == null) {
            return "";
        }
        if (t.is(CounterEnumType.P1P1)) {
            return "+1/+1";
        }
        if (t.is(CounterEnumType.M1M1)) {
            return "-1/-1";
        }
        final String n = t.getName();
        return n == null || n.isEmpty() ? t.getCounterOnCardDisplayName() : n;
    }

    /** "Contador +1/+1 ×2", "Contador Stun ×1": lo que se ensenya en la ampliacion. */
    public static String label(final CounterType t, final int count) {
        return NeoText.get("counter.label", name(t), count);
    }

    /** Lo que hace, para leerlo al tocar el contador. */
    public static String help(final CounterType t) {
        if (t == null) {
            return "";
        }
        for (final CounterEnumType e : EXPLAINED) {
            if (t.is(e)) {
                return NeoText.get("counter.help." + e.name().toLowerCase(Locale.ROOT));
            }
        }
        if (t.isKeywordCounter()) {
            return NeoText.get("counter.help.keyword", t.getCounterOnCardDisplayName());
        }
        return NeoText.get("counter.help.other");
    }
}
