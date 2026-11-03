package forge.neo.deck;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.card.CardEdition;
import forge.item.PaperCard;
import forge.model.FModel;

/**
 * Si una impresion es "full art": el boton "Solo full art" del selector de arte,
 * en PC y en Android (Discord, 03-11-2026: <i>"an option to filter full art
 * lands from normal ones ... searching for one in specific is hard"</i>).
 *
 * <p><b>Forge no lo sabe.</b> De 3.953 impresiones de basicas solo 50 estan en
 * la seccion {@code [full art]} de su edicion; las de Zendikar van en
 * {@code [cards]} como cualquier otra. La lista sale de Scryfall
 * ({@code is:fullart}) con {@code tools/full-art.py}, que la rehace
 * {@code actualizar.bat}, y viaja dentro del jar ({@code full-art.txt}): el
 * juego no pregunta a internet. Lo que Forge si marca como full art cuenta
 * tambien, por si la lista se queda atras.
 *
 * <p>Se casa por la edicion en Scryfall ({@code ScryfallCode} de la edicion de
 * Forge) y el numero de coleccionista: 97% de la lista el dia que se hizo.
 *
 * <p>Java puro y nada por encima de la API 26: lo usa Android por el jar.
 */
public final class FullArt {

    private static final String RESOURCE = "full-art.txt";
    private static volatile Set<String> keys;

    private FullArt() {
    }

    /** Si esta impresion es full art. */
    public static boolean is(final PaperCard card) {
        if (card == null) {
            return false;
        }
        final String number = card.getCollectorNumber();
        if (number == null || number.isEmpty()) {
            return false;
        }
        final CardEdition edition;
        try {
            edition = FModel.getMagicDb().getEditions().get(card.getEdition());
        } catch (final RuntimeException e) {
            return false;
        }
        if (edition == null) {
            return false;
        }
        final String n = number.toLowerCase(Locale.ROOT);
        String code;
        try {
            code = edition.getScryfallCode();
        } catch (final RuntimeException e) {
            code = edition.getCode();
        }
        if (code != null && keys().contains(code.toLowerCase(Locale.ROOT) + "\t" + n)) {
            return true;
        }
        return CardEdition.EditionSectionWithCollectorNumbers.FULL_ART.getName()
                .equals(edition.getSectionForCollectorNumber(number));
    }

    /** Solo las full art de la lista, en el mismo orden. */
    public static List<PaperCard> only(final List<PaperCard> printings) {
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard p : printings) {
            if (is(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Si en la lista hay alguna full art Y alguna que no: solo entonces tiene sentido el filtro. */
    public static boolean worthFiltering(final List<PaperCard> printings) {
        boolean some = false;
        boolean other = false;
        for (final PaperCard p : printings) {
            if (is(p)) {
                some = true;
            } else {
                other = true;
            }
            if (some && other) {
                return true;
            }
        }
        return false;
    }

    /** Cuantas lineas trae la lista (para el comprobador). */
    static int listSize() {
        return keys().size();
    }

    private static Set<String> keys() {
        Set<String> k = keys;
        if (k == null) {
            synchronized (FullArt.class) {
                k = keys;
                if (k == null) {
                    k = load();
                    keys = k;
                }
            }
        }
        return k;
    }

    private static Set<String> load() {
        final Set<String> out = new HashSet<>();
        try (InputStream in = FullArt.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return Collections.emptySet();
            }
            final BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.indexOf('\t') > 0) {
                    out.add(line.toLowerCase(Locale.ROOT));
                }
            }
        } catch (final java.io.IOException | RuntimeException e) {
            return Collections.emptySet();
        }
        return out;
    }
}
