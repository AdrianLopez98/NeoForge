package forge.neo.card;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import forge.StaticData;
import forge.card.ICardFace;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.item.PaperToken;

/**
 * <b>Las fichas que crea una carta</b>, para ensenyarlas al ampliarla fuera de
 * una partida — la enciclopedia, el constructor (Discord, 23-10-2026: <i>"it
 * would be nice if this extended detail view (including created tokens) be
 * available in the Card Encyclopedia"</i>). Asi se ve si su imagen esta sin
 * tener que jugar la carta.
 *
 * <p>No hay reglas nuestras: se LEE el guion de la carta. Dos formas de crear
 * una ficha cubren casi todas: un guion de ficha ({@code TokenScript$
 * w_1_1_soldier,...}, que da la ficha de su expansion en {@code TokenDb}) y
 * copiar una carta por su nombre ({@code DefinedName$ Gingerbrute}, con la
 * imagen que buscaria la partida: {@code StaticData.getOtherImageKey} +
 * {@link TokenKeys}). Lo que se crea de otra manera (investigar, amasar...)
 * no sale: mejor no ensenyar una ficha que ensenyar una equivocada.
 *
 * <p>En el jar: la usan el escritorio ({@code CardZoom}) y NeoForge Android.
 */
public final class CardTokens {

    private CardTokens() {
    }

    private static final Pattern SCRIPT = Pattern.compile("TokenScript\\$\\s*([^|]+)");
    private static final Pattern NAMED = Pattern.compile("CopyPermanent.*?DefinedName\\$\\s*([^|]+)");

    /** Las fichas de esta carta, ya listas para pintar; vacio si no crea ninguna que se sepa. */
    public static List<CardView> of(final PaperCard card) {
        final List<CardView> out = new ArrayList<>();
        if (card == null || card.getRules() == null) {
            return out;
        }
        final Set<String> scripts = new LinkedHashSet<>();
        final Set<String> named = new LinkedHashSet<>();
        for (final ICardFace face : card.getRules().getAllFaces()) {
            if (face == null) {
                continue;
            }
            collect(face.getAbilities(), scripts, named);
            collect(face.getTriggers(), scripts, named);
            collect(face.getStaticAbilities(), scripts, named);
            collect(face.getReplacements(), scripts, named);
            final List<String> vars = new ArrayList<>();
            for (final Map.Entry<String, String> v : face.getVariables()) {
                vars.add(v.getValue());
            }
            collect(vars, scripts, named);
        }
        final StaticData data = StaticData.instance();
        for (final String script : scripts) {
            try {
                PaperToken t = data.getAllTokens().getToken(script, card.getEdition());
                if (t == null) {
                    t = data.getAllTokens().getToken(script);
                }
                if (t != null) {
                    out.add(CardView.getCardForUi(t));
                }
            } catch (final RuntimeException e) {
                // Un guion raro no puede romper la carta ampliada.
            }
        }
        for (final String name : named) {
            try {
                final PaperCard copied = data.getCommonCards().getCard(name);
                if (copied == null) {
                    continue;
                }
                final Card c = Card.fromPaperCard(copied, null);
                final String tokenName = name.replace(",", "").replace(" ", "_").toLowerCase(Locale.ROOT);
                c.getCurrentState().setImageKey(TokenKeys.complete(
                        data.getOtherImageKey(tokenName, card.getEdition())));
                out.add(c.getView());
            } catch (final RuntimeException e) {
                // Igual.
            }
        }
        return out;
    }

    private static void collect(final Iterable<String> lines, final Set<String> scripts, final Set<String> named) {
        if (lines == null) {
            return;
        }
        for (final String line : lines) {
            if (line == null) {
                continue;
            }
            final Matcher m = SCRIPT.matcher(line);
            while (m.find()) {
                for (final String s : m.group(1).split(",")) {
                    final String t = s.trim();
                    // Lo que no parece el nombre de un guion (una variable, "Remembered"...) no se busca.
                    if (!t.isEmpty() && t.equals(t.toLowerCase(Locale.ROOT)) && t.indexOf(' ') < 0) {
                        scripts.add(t);
                    }
                }
            }
            final Matcher n = NAMED.matcher(line);
            while (n.find()) {
                final String t = n.group(1).trim();
                if (!t.isEmpty()) {
                    named.add(t);
                }
            }
        }
    }
}
