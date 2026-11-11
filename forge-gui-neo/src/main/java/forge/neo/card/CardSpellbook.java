package forge.neo.card;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.StaticData;
import forge.card.ICardFace;
import forge.game.card.CardView;
import forge.item.PaperCard;

/**
 * <b>El spellbook de una carta de Arena</b> (Dragon Typhoon, Tovolar, Garth...:
 * «draft a card from CARDNAME's spellbook»), para verlo al ampliarla igual que
 * las fichas que crea (Discord, 11-11-2026: <i>"an option in the cards from
 * Arena that have the effect of drafting their spellbooks to see the cards in
 * the spellbooks"</i>).
 *
 * <p>No hay reglas nuestras: se LEE el guion, como hace el motor en
 * {@code DraftEffect}: {@code Spellbook$ Carta1,Carta2,...}, y los nombres que
 * llevan coma se escriben con punto y coma ({@code Neriv; Heart of the Storm}).
 * Unas 60 cartas lo usan.
 *
 * <p>En el jar: la usan el escritorio ({@code CardZoom}) y NeoForge Android.
 */
public final class CardSpellbook {

    private CardSpellbook() {
    }

    private static final Pattern SPELLBOOK = Pattern.compile("Spellbook\\$\\s*([^|]+)");

    /** Las cartas de su spellbook, listas para pintar; vacio si no tiene. */
    public static List<CardView> of(final PaperCard card) {
        final List<CardView> out = new ArrayList<>();
        if (card == null || card.getRules() == null) {
            return out;
        }
        final Set<String> names = new LinkedHashSet<>();
        for (final ICardFace face : card.getRules().getAllFaces()) {
            if (face == null) {
                continue;
            }
            collect(face.getAbilities(), names);
            collect(face.getTriggers(), names);
            collect(face.getStaticAbilities(), names);
            collect(face.getReplacements(), names);
            final List<String> vars = new ArrayList<>();
            for (final Map.Entry<String, String> v : face.getVariables()) {
                vars.add(v.getValue());
            }
            collect(vars, names);
        }
        final StaticData data = StaticData.instance();
        for (final String name : names) {
            try {
                final PaperCard pc = data.getCommonCards().getCard(name);
                if (pc != null) {
                    out.add(CardView.getCardForUi(pc));
                }
            } catch (final RuntimeException e) {
                // Un nombre raro no puede romper la carta ampliada.
            }
        }
        return out;
    }

    private static void collect(final Iterable<String> lines, final Set<String> names) {
        if (lines == null) {
            return;
        }
        for (final String line : lines) {
            if (line == null) {
                continue;
            }
            final Matcher m = SPELLBOOK.matcher(line);
            while (m.find()) {
                for (final String s : m.group(1).split(",")) {
                    final String t = s.trim().replace(";", ",");
                    if (!t.isEmpty()) {
                        names.add(t);
                    }
                }
            }
        }
    }
}
