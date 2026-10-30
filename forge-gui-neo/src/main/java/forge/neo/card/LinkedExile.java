package forge.neo.card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import forge.game.CardTraitBase;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.zone.ZoneType;

/**
 * <b>Lo exiliado que asoma bajo un permanente</b> (decision 572 de Android y
 * {@code PlayerField} en el escritorio): solo lo que esta ENLAZADO a el.
 *
 * <p>Discord, 30-10-2026: Bojuka Bog dejaba un cementerio entero asomando
 * debajo, <i>"it would make sense to only do it in cases where the permanent
 * has a linked ability"</i>. El motor apunta en {@code getExiledCards} todo lo
 * que una carta exilia, aunque luego no vuelva a mirarlo. Asi que:
 *
 * <ul>
 *   <li>Lo que <b>vuelve</b> cuando la carta deja el campo de batalla
 *       ({@code getUntilLeavesBattlefield}: Banishing Light), siempre.</li>
 *   <li>Lo exiliado <b>con</b> ella, solo si su texto lo vuelve a usar: alguna
 *       de sus habilidades habla de lo exiliado con ella ({@code ExiledWith}:
 *       Oblivion Ring al devolverlo) o de lo que imprimio ({@code Imprinted}:
 *       Isochron Scepter). Bojuka Bog no lo menciona nunca, y no asoma.</li>
 * </ul>
 *
 * <p>Lo de si una carta lo menciona se mira en su guion una vez por nombre y
 * se guarda. Pura, sin JavaFX: la usa tambien Android.
 */
public final class LinkedExile {

    private LinkedExile() {
    }

    private static final Map<String, Boolean> LINKED = new ConcurrentHashMap<>();

    /** Lo que asoma bajo {@code cv}: solo lo que sigue en el exilio. */
    public static List<CardView> of(final CardView cv) {
        final Map<Integer, CardView> out = new LinkedHashMap<>();
        if (cv == null) {
            return List.of();
        }
        add(out, cv.getUntilLeavesBattlefield());
        final Iterable<CardView> with = cv.getExiledCards();
        if (with != null && with.iterator().hasNext() && linked(cv)) {
            add(out, with);
        }
        return new ArrayList<>(out.values());
    }

    private static void add(final Map<Integer, CardView> out, final Iterable<CardView> from) {
        if (from == null) {
            return;
        }
        for (final CardView e : from) {
            if (e != null && e.getZone() == ZoneType.Exile) {
                out.putIfAbsent(e.getId(), e);
            }
        }
    }

    /** Si alguna habilidad de esa carta vuelve a usar lo que exilio. */
    private static boolean linked(final CardView cv) {
        final String name = cv.getCurrentState() == null ? null : cv.getCurrentState().getName();
        if (name == null || name.isEmpty()) {
            return true; // boca abajo o sin nombre: mejor ensenyarlo
        }
        final Boolean known = LINKED.get(name);
        if (known != null) {
            return known;
        }
        final Game g = AffectedBy.current();
        if (g == null) {
            return true;
        }
        try {
            final Card c = g.findById(cv.getId());
            if (c == null) {
                return true;
            }
            final boolean yes = mentions(c);
            LINKED.put(name, yes);
            return yes;
        } catch (final RuntimeException e) {
            return true;
        }
    }

    private static boolean mentions(final Card c) {
        for (final String v : c.getSVars().values()) {
            if (talksAboutExiled(v)) {
                return true;
            }
        }
        final List<CardTraitBase> traits = new ArrayList<>();
        traits.addAll(c.getSpellAbilities());
        c.getTriggers().forEach(traits::add);
        c.getStaticAbilities().forEach(traits::add);
        c.getReplacementEffects().forEach(traits::add);
        for (final CardTraitBase t : traits) {
            for (final String v : t.getMapParams().values()) {
                if (talksAboutExiled(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean talksAboutExiled(final String v) {
        return v != null && (v.contains("ExiledWith") || v.contains("Imprinted"));
    }
}
