package forge.deck;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import forge.deck.DeckRecognizer.Token;
import forge.deck.DeckRecognizer.TokenType;

/**
 * <b>Importar un mazo por enlace sin que una impresion rara lo tumbe.</b>
 *
 * <p>Fichero NUESTRO (forge-gui-neo), en el paquete {@code forge.deck} solo
 * para poder usar los proveedores de Forge, que son de paquete. No es copia de
 * nada: no se toca ningun fichero de Forge (regla de oro).
 *
 * <p>Discord/itch.io, 08-11-2026: un mazo de Moxfield con impresiones de los
 * World Championship (WC00-WC04, borde dorado, que Forge no trae) no entraba:
 * <i>"Forge could not find this Moxfield card in the database: Skirk Prospector
 * [WC03]"</i>, y la carta si esta en el juego. Forge ya intenta quitar la
 * edicion cuando no la conoce ({@code DeckUrlLoader.PRINTING_HINT}), pero su
 * patron solo acepta numeros de coleccionista en MAYUSCULAS, y los de esas
 * ediciones van en minusculas ({@code we230}, {@code bk327sb}). Asi que la
 * linea {@code 1 Skirk Prospector [WC03] we230} se quedaba sin reconocer.
 *
 * <p>Primero se deja hacer a Forge, tal cual: si funciona, no cambia nada. Si
 * falla, se vuelve a pedir el mazo y se importa con el mismo recognizer, pero
 * quitando la impresion que no conoce (cualquier numero). Si arreglan el patron
 * rio arriba, esto simplemente deja de hacer falta.
 *
 * <p>Java puro: lo usa Android por el jar.
 */
public final class NeoDeckUrlLoader {

    /** Como {@code DeckUrlLoader.PRINTING_HINT}, con el numero en cualquier caja. */
    private static final Pattern ANY_PRINTING = Pattern.compile(
            "^(\\s*\\d+\\s+.+?)\\s+\\[[A-Za-z0-9_]{2,7}\\](?:\\s+\\S+)?\\s*$");

    private NeoDeckUrlLoader() {
    }

    /** El mazo de ese enlace (Moxfield, Archidekt, TappedOut, MTGGoldfish). */
    public static Deck load(final String deckUrl) throws IOException {
        try {
            return DeckUrlLoader.load(deckUrl).getDeck();
        } catch (final IOException first) {
            final String url = normalize(deckUrl);
            final DeckUrlProvider provider = provider(url);
            if (provider == null) {
                throw first;
            }
            final DeckUrlProvider.RemoteDeck remote;
            try {
                remote = provider.load(url, List.of());
            } catch (final IOException | RuntimeException again) {
                throw first;
            }
            return importLenient(remote, first);
        }
    }

    /** Lo de {@code DeckUrlLoader.importDeck}, quitando las impresiones que no conoce. */
    static Deck importLenient(final DeckUrlProvider.RemoteDeck remote, final IOException first)
            throws IOException {
        final DeckRecognizer recognizer = new DeckRecognizer();
        recognizer.forceImportBannedAndRestrictedCards();
        final String[] lines = lenientLines(recognizer, remote.importText());
        final List<Token> tokens = recognizer.parseCardList(lines);
        final Deck deck = new Deck(remote.name());
        for (final Token token : tokens) {
            final TokenType type = token.getType();
            if (type == TokenType.UNKNOWN_CARD || type == TokenType.UNSUPPORTED_CARD) {
                // Esta si que no esta: el mensaje de Forge, con ESTA carta.
                throw new IOException(forge.util.Localizer.getInstance().getMessage(
                        "lblDeckUrlCardNotFound", remote.providerName(), token.getText()));
            }
            if (!token.isTokenForDeck() || type == TokenType.DECK_NAME) {
                continue;
            }
            deck.getOrCreate(token.getTokenSection()).add(token.getCard(), token.getQuantity());
        }
        if (deck.getMain().isEmpty() && !deck.has(DeckSection.Commander)) {
            throw first;
        }
        deck.setDeckFormat(remote.format());
        deck.setSourceUrl(remote.sourceUrl());
        return deck;
    }

    /** Las lineas, con "[SET] numero" quitado donde sin eso SI se reconoce la carta. */
    static String[] lenientLines(final DeckRecognizer recognizer, final String importText) {
        final String[] lines = importText.split("\n");
        DeckSection section = null;
        for (int i = 0; i < lines.length; i++) {
            final Token token = recognizer.recognizeLine(lines[i], section);
            if (token == null) {
                continue;
            }
            if (token.getType() == TokenType.DECK_SECTION_NAME) {
                section = DeckSection.valueOf(token.getText());
                continue;
            }
            if (token.getType() == TokenType.UNKNOWN_CARD || token.getType() == TokenType.UNSUPPORTED_CARD) {
                final String plain = ANY_PRINTING.matcher(lines[i]).replaceFirst("$1");
                if (!plain.equals(lines[i])) {
                    final Token retry = recognizer.recognizeLine(plain, section);
                    if (retry != null && retry.isCardToken()) {
                        lines[i] = plain;
                    }
                }
            }
        }
        return lines;
    }

    private static String normalize(final String deckUrl) {
        final String trimmed = deckUrl == null ? "" : deckUrl.trim();
        return trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*") ? trimmed : "https://" + trimmed;
    }

    private static DeckUrlProvider provider(final String url) {
        final String host;
        try {
            final String h = URI.create(url).getHost();
            host = h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (final RuntimeException e) {
            return null;
        }
        if (host.endsWith("moxfield.com")) {
            return new MoxfieldDeckUrlProvider();
        }
        if (host.endsWith("archidekt.com")) {
            return new ArchidektDeckUrlProvider();
        }
        if (host.endsWith("tappedout.net")) {
            return new TappedOutDeckUrlProvider();
        }
        if (host.endsWith("mtggoldfish.com")) {
            return new MtgGoldfishDeckUrlProvider();
        }
        return null;
    }
}
