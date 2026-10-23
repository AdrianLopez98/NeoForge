package forge.neo.card;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import forge.ImageKeys;
import forge.StaticData;
import forge.card.CardEdition;

/**
 * <b>Las fichas que llegan sin expansion en su clave de imagen.</b>
 *
 * <p>Discord, 23-10-2026, con Ginger, Queen of Sweets (Reality Fracture
 * Commander): su Gingerbrute salia sin imagen y con "esta carta no se baja de
 * Scryfall", y Scryfall SI la tiene (TFRC n.º 12). Ginger no crea la ficha
 * con un guion de ficha sino <b>copiando la carta por su nombre</b>
 * ({@code CopyPermanent | DefinedName$ Gingerbrute}), y para eso Forge busca
 * la imagen en la seccion {@code [other]} de cada expansion
 * ({@code StaticData.getOtherImageKey}). En la de FRC el Gingerbrute esta en
 * {@code [tokens]}, asi que no lo encuentra y se queda con
 * {@code t:gingerbrute} a secas — y {@code ImageFetcher} descarta una clave de
 * ficha sin expansion ("malformed").
 *
 * <p>Arreglarlo en el fichero de la expansion es tocar Forge (regla de oro).
 * Aqui se <b>completa la clave</b> antes de buscar o bajar nada: la expansion
 * mas reciente cuya lista {@code [tokens]} tenga ese nombre. Con
 * {@code t:gingerbrute|FRC} el descargador de Forge ya sabe ir a Scryfall.
 * Si ninguna la tiene, la clave se queda como estaba.
 *
 * <p>Pura y en el jar: la usan {@code CardImages} y la de NeoForge Android.
 */
public final class TokenKeys {

    private TokenKeys() {
    }

    /** nombre de ficha -> expansion ("" = ninguna la tiene). */
    private static final Map<String, String> SETS = new ConcurrentHashMap<>();

    /** La clave con su expansion si era una ficha sin ella; si no, la misma. */
    public static String complete(final String imageKey) {
        if (imageKey == null || !imageKey.startsWith(ImageKeys.TOKEN_PREFIX) || imageKey.indexOf('|') >= 0) {
            return imageKey;
        }
        String name = imageKey.substring(ImageKeys.TOKEN_PREFIX.length());
        String back = "";
        if (name.endsWith(ImageKeys.BACKFACE_POSTFIX)) {
            back = ImageKeys.BACKFACE_POSTFIX;
            name = name.substring(0, name.length() - back.length());
        }
        if (name.isEmpty()) {
            return imageKey;
        }
        final String set = SETS.computeIfAbsent(name, TokenKeys::newestSetListing);
        return set.isEmpty() ? imageKey : ImageKeys.TOKEN_PREFIX + name + "|" + set + back;
    }

    private static String newestSetListing(final String tokenName) {
        try {
            final StaticData data = StaticData.instance();
            if (data == null) {
                return "";
            }
            CardEdition best = null;
            for (final CardEdition e : data.getEditions()) {
                if (e.getType() == CardEdition.Type.CUSTOM_SET || e.getTokens().get(tokenName).isEmpty()) {
                    continue;
                }
                if (best == null || e.getDate().after(best.getDate())) {
                    best = e;
                }
            }
            return best == null ? "" : best.getCode();
        } catch (final RuntimeException e) {
            return "";
        }
    }
}
