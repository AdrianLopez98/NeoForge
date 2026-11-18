package forge.neo.draft;

import forge.gamemodes.limited.BoosterDraft;
import forge.gamemodes.limited.CommanderLimitedPlayerAI;
import forge.gamemodes.limited.LimitedPlayer;
import forge.gamemodes.limited.LimitedPoolType;

/**
 * <b>Un cubo drafteado como Commander Draft</b> (Discord, 17-11-2026: <i>"is
 * there any way to play a commander cube? I was able to paste a cube list but
 * no option to choose a commander"</i>).
 *
 * <p>Forge solo enciende el modo Commander para las expansiones que lo llevan
 * en su fichero de edicion ({@code DraftType.Commander}: Commander Legends,
 * Baldur's Gate...); un cubo pegado se draftea siempre como uno de 40 cartas.
 * Con una subclase nuestra se monta el draft con los mismos pasos que
 * {@code BoosterDraft.createDraft(Custom)} y el modo encendido desde el
 * principio.  Sin tocar el motor.
 *
 * <p>⚠️ DESDE FORGE #12919 (18-11-2026) {@code activateCommanderDraft} es
 * privado y el motor lo enciende solo si TODAS las cartas vienen de
 * expansiones de Commander Draft ({@code DeckFormat.isCommanderDraft}): un
 * cubo no lo cumple nunca.  Asi que el asiento es otro: {@code createAIPlayer}
 * ({@code protected}), al que llama el propio constructor para cada rival.
 * Devolviendo siempre un {@code CommanderLimitedPlayerAI}, los rivales nacen ya
 * de Commander, y {@code isCommanderDraft} (campo {@code protected}) se pone a
 * true en el constructor, que es lo que lee {@code NeoDraft.toGroup}.
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
        isCommanderDraft = true;
    }

    /**
     * Cada rival, de Commander.  Lo llama el constructor de {@code BoosterDraft}
     * (antes de que corra el nuestro: no puede depender de nada de esta clase)
     * y {@code setPodSize} si el cubo cambia cuantos se sientan.
     */
    @Override
    protected LimitedPlayer createAIPlayer(final int seatingOrder) {
        return new CommanderLimitedPlayerAI(seatingOrder, this);
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
        d.initializeBoosters();
        return d;
    }
}
