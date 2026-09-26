// COPIA SOMBRA de forge-gui-mobile/src/forge/adventure/scene/DeckEditScene.java (NeoForge).
// Va antes en el classpath y sustituye a la de Forge; el original NO se toca.
// Unico cambio: el bloque 'NEOFORGE' de enter(). Si Forge cambia el original,
// AdventureCheck se pone en rojo: hay que volver a copiarlo y reponer el bloque.
package forge.adventure.scene;

import forge.Forge;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import forge.Adventure;
import forge.adventure.data.AdventureEventData;
import forge.screens.FScreen;

/**
 * DeckEditScene
 * Scene class that contains the Deck editor layout
 */
public class DeckEditScene extends ForgeScene {

    AdventureEventData currentEvent;

    private DeckEditScene() {}

    private static DeckEditScene object;
    TextureRegion backDrop;

    public static DeckEditScene getInstance(TextureRegion backdrop) {
        if(object == null)
            object = new DeckEditScene();
        object.backDrop = backdrop;
        return object;
    }

    public void loadEvent(AdventureEventData event){
        currentEvent = event;
    }

    @Override
    public boolean leave() {
        Adventure.getInstance().renderTransitionScreen = true;
        return super.leave();
    }

    @Override
    public void enter() {
        // ---- NEOFORGE: el editor de mazos es el NUESTRO ----
        // Unico cambio frente al DeckEditScene de Forge. Los mazos de evento
        // (draft, jumpstart) siguen con el suyo: tienen reglas propias.
        if (currentEvent == null && forge.neo.adventure.NeoDeckBridge.enabled()) {
            Adventure.getInstance().renderTransitionScreen = false;
            forge.neo.adventure.NeoDeckBridge.open(() -> Forge.switchToLast());
            return;
        }
        if (currentEvent == null)
            ((AdventureDeckEditor) getScreen()).setEvent(null);
        ((AdventureDeckEditor) getScreen()).refresh();
        super.enter();
    }

    @Override
    public FScreen getScreen() {
        return currentEvent == null
            ? new AdventureDeckEditor(false, backDrop)
            :  new AdventureDeckEditor(currentEvent, backDrop);
    }
}
