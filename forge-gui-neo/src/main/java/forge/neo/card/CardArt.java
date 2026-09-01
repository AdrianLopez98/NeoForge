package forge.neo.card;

import java.io.File;
import java.io.IOException;

import forge.localinstance.properties.ForgeConstants;
import forge.neo.NeoLanguage;
import forge.neo.NeoSettings;

/**
 * En que idioma se ve el ARTE de las cartas.
 *
 * <p>Traducir el nombre y el texto de una carta no cambia lo que se lee en la
 * mesa: ahi el nombre esta <b>dentro de la imagen</b>, y la imagen viene de
 * Scryfall. Scryfall si sirve las impresiones traducidas — la misma carta, la
 * misma edicion, otro idioma — con solo cambiar un trozo de la URL:
 *
 * <pre>
 * https://api.scryfall.com/cards/tdc/12/en?format=image   ingles
 * https://api.scryfall.com/cards/tdc/12/es?format=image   castellano
 * </pre>
 *
 * <p><b>El Forge normal NO lo hace.</b> Lo hizo: el commit
 * <i>"try get UI language image from scryfall"</i> sacaba el idioma de
 * {@code FPref.UI_LANGUAGE}, y en junio de 2021 lo cambiaron por el idioma de
 * la EDICION ({@code CardEdition.getCardsLangCode()}, que casi siempre es
 * ingles). De aquello queda un {@code langCodeMap} en {@code ImageFetcher} que
 * ya no lee nadie. O sea que esto lo ponemos nosotros.
 *
 * <p><b>Por que las localizadas van en su propia carpeta.</b> Compartimos
 * {@code %LOCALAPPDATA%\\Forge\\Cache\\pics\\cards\\} con la instalacion normal
 * del usuario — 172 MB de cartas ya descargadas. Si guardaramos la version
 * castellana con el mismo nombre de fichero le cambiariamos las cartas a su
 * Forge de siempre, y ademas no habria forma de volver atras. Asi que las
 * nuestras van en {@code pics/cards-es/}, una carpeta hermana: Forge nunca mira
 * ahi ({@code ImageKeys} solo entra en carpetas con nombre de edicion) y borrar
 * esa carpeta deja todo como estaba.
 */
public final class CardArt {

    private CardArt() {
    }

    /** La preferencia: ver las cartas en el idioma del juego. */
    public static final String SETTING = NeoSettings.CARD_ART_LANGUAGE;

    /**
     * Los codigos de Scryfall, por idioma nuestro.
     *
     * <p>Es la misma tabla que {@code ImageFetcher.langCodeMap}, que en Forge
     * quedo sin usar. Solo estan los idiomas en los que Scryfall publica
     * cartas: en {@code hu-HU} o {@code pl-PL} no hay impresiones, asi que no
     * se pide nada y se ve el ingles de siempre.
     */
    private static String scryfallCodeOf(final String language) {
        switch (language) {
            case "es-ES": return "es";
            case "fr-FR": return "fr";
            case "de-DE": return "de";
            case "it-IT": return "it";
            case "pt-BR": return "pt";
            case "ja-JP": return "ja";
            case "ko-KR": return "ko";
            case "ru-RU": return "ru";
            case "zh-CN": return "zhs";
            case "zh-HK": return "zht";
            default: return null;
        }
    }

    /**
     * El codigo de Scryfall que toca ahora, o {@code null} si no hay que pedir
     * nada (se juega en ingles, no hay impresiones en ese idioma, o el jugador
     * lo ha apagado).
     *
     * <p><b>Cacheado, y no por gusto:</b> esto se pregunta una vez por carta
     * que se pinta, y {@code NeoLanguage.current()} <i>lista la carpeta de
     * idiomas</i> cada vez que se le llama. Con una mesa llena serian decenas
     * de accesos a disco por cambio de estado.
     */
    public static String language() {
        String cached = resolved;
        if (cached == null) {
            cached = NeoSettings.getBool(SETTING, true)
                    ? scryfallCodeOf(NeoLanguage.current()) : "";
            resolved = cached;
        }
        return cached == null || cached.isEmpty() ? null : cached;
    }

    /** Cadena vacia = "ya se ha mirado y no hay idioma". */
    private static volatile String resolved;

    /**
     * Vuelve a mirar el idioma y la preferencia.
     *
     * <p>Lo llama el panel de ajustes al tocar la casilla; sin esto, apagar o
     * encender el arte traducido no se notaria hasta reiniciar.
     */
    public static void reload() {
        resolved = null;
    }

    /** Si hay que buscar y pedir arte traducido. */
    public static boolean isLocalized() {
        return language() != null;
    }

    /**
     * La carpeta del arte traducido, hermana de la de Forge.
     *
     * <p>{@code CACHE_CARD_PICS_DIR} es {@code .../pics/cards}; esto devuelve
     * {@code .../pics/cards-es}. Nunca se escribe dentro de la de Forge.
     */
    public static File dir() {
        final String lang = language();
        if (lang == null) {
            return null;
        }
        final File cards = new File(ForgeConstants.CACHE_CARD_PICS_DIR);
        final File parent = cards.getParentFile();
        final String name = cards.getName() + "-" + lang;
        return parent == null ? new File(name) : new File(parent, name);
    }

    /**
     * Donde va la imagen traducida de esa carta.
     *
     * <p>{@code filename} es lo que da {@code PaperCard.getCardImageKey()}:
     * {@code TDC/Access Tunnel.full}. Scryfall sirve la carta con borde
     * completo, y Forge llama a eso {@code .fullborder.jpg} — se respeta el
     * mismo nombre para que las dos carpetas sean intercambiables.
     */
    public static File fileFor(final String filename) {
        final File dir = dir();
        if (dir == null || filename == null || filename.isEmpty()) {
            return null;
        }
        return new File(dir, filename.replace(".full", ".fullborder") + ".jpg");
    }

    /**
     * La marca de "esta carta no existe en este idioma".
     *
     * <p>No todas las cartas se han impreso en castellano — ni de lejos. Sin
     * dejar constancia, cada arranque volveria a preguntarle a Scryfall por las
     * mismas cartas que ya sabemos que no tiene, y Scryfall tiene limite de
     * peticiones. Es un fichero vacio al lado de donde habria ido la imagen:
     * borrar la carpeta lo borra tambien, que es justo lo que se espera.
     */
    private static File missMarker(final String filename) {
        final File image = fileFor(filename);
        return image == null ? null : new File(image.getPath() + ".none");
    }

    /** Si ya sabemos que esta carta no esta en este idioma. */
    public static boolean isMissing(final String filename) {
        final File marker = missMarker(filename);
        return marker != null && marker.exists();
    }

    /** Deja constancia de que Scryfall no la tiene en este idioma. */
    public static void markMissing(final String filename) {
        final File marker = missMarker(filename);
        if (marker == null || marker.exists()) {
            return;
        }
        try {
            final File parent = marker.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            marker.createNewFile();
        } catch (final IOException | SecurityException e) {
            // Que no se pueda marcar solo significa volver a preguntarlo otro
            // dia. No es motivo para romper nada.
            System.err.println("[neo] no se ha podido marcar " + filename + ": " + e);
        }
    }
}
