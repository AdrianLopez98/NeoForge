package forge.neo.draft;

import forge.gamemodes.limited.BoosterDraft;
import forge.gamemodes.limited.LimitedPoolType;

/**
 * <b>Un cubo drafteado como Commander Draft</b> (Discord, 17-11-2026: <i>"is
 * there any way to play a commander cube? I was able to paste a cube list but
 * no option to choose a commander"</i>).
 *
 * <p>Forge solo enciende el modo Commander para las expansiones que lo llevan
 * en su fichero de edicion ({@code DraftType.Commander}: Commander Legends,
 * Baldur's Gate...); un cubo pegado se draftea siempre como uno de 40 cartas.
 * El interruptor es {@code activateCommanderDraft}, {@code protected}, igual
 * que {@code generateProduct}: con una subclase nuestra se monta el draft con
 * los mismos pasos que {@code BoosterDraft.createDraft(Custom)} y el modo
 * encendido antes de repartir los sobres, que es cuando lo pide su javadoc.
 * Sin tocar el motor.
 *
 * <p>Lo de despues ya existe: los rivales draftean buscando comandante
 * ({@code CommanderLimitedPlayerAI}, que crea el propio motor al encenderlo),
 * {@code NeoDraft.toGroup} pone la marca {@link CommanderDraft#TAG} y el
 * evento es un Commander Draft como cualquier otro: elegir comandante del
 * pool, 60 cartas, reglas de Commander y partidas de cuatro. A diferencia de
 * Commander Legends no hay doble pick ni comandante gratis de la expansion:
 * un cubo no los trae.
 */
final class CommanderCubeDraft extends BoosterDraft {

    private CommanderCubeDraft() {
        super(LimitedPoolType.Custom);
    }

    /**
     * Como {@code createDraft(Custom)}, con el modo Commander. Pregunta que
     * cubo por el elegidor del motor, como aquel: quien llama lo contesta
     * ({@code NeoDraft.startCube}).
     *
     * @return null si el motor no ha podido montar el producto
     */
    static CommanderCubeDraft create() {
        final CommanderCubeDraft d = new CommanderCubeDraft();
        if (!d.generateProduct()) {
            return null;
        }
        // Despues de generateProduct (que fija cuantos se sientan) y antes de
        // repartir: cambia las IA por las de Commander.
        d.activateCommanderDraft(null, null);
        d.initializeBoosters();
        return d;
    }
}
