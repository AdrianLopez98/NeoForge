package forge.neo.adventure;

import forge.adventure.util.Current;
import forge.deck.Deck;
import forge.neo.deck.DeckEditor;
import forge.neo.ui.DeckBuilderScreen;
import javafx.application.Platform;

/**
 * editar un mazo del Adventure abre el deck builder de NeoForge.
 *
 * <p>{@code -Dneo.adventure.editor=false} lo apaga y se usa el suyo.
 */
public final class NeoDeckBridge {

    private NeoDeckBridge() {
    }

    private static final boolean ON =
            !"false".equalsIgnoreCase(System.getProperty("neo.adventure.editor"))
                    && NeoDuelBridge.enabled();

    public static boolean enabled() {
        return ON;
    }

    /** El ultimo contexto abierto, para la autoprueba. */
    static volatile AdventureDeckContext lastContext;

    /** Lo llama DeckEditScene.enter() desde el hilo de libGDX. */
    public static void open(final Runnable backToAdventure) {
        final AdventureDeckContext context = new AdventureDeckContext(Current.player());
        lastContext = context;
        final Deck deck = context.currentDeck();
        NeoDuelBridge.log("abriendo nuestro editor con el mazo " + (deck == null ? "?" : deck.getName())
                + " y " + context.pool().size() + " cartas distintas en la coleccion");
        NeoWindow.takeOver();
        Platform.runLater(() -> {
            try {
                final DeckEditor editor = DeckEditor.copyOf(context, deck);
                final DeckBuilderScreen screen = new DeckBuilderScreen(editor, NeoWindow.cardWidth(),
                        () -> NeoWindow.giveBack(backToAdventure));
                NeoWindow.show(screen, "NeoForge · Adventure · " + deck.getName());
                // Las teclas del duelo no valen aqui.
                NeoWindow.scene().setOnKeyPressed(null);
                NeoWindow.scene().setOnKeyReleased(null);
            } catch (final Throwable e) {
                NeoDuelBridge.log("no se pudo abrir nuestro editor: " + e);
                e.printStackTrace();
                NeoWindow.giveBack(backToAdventure);
            }
        });
    }
}
