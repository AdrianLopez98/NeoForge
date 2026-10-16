package forge.neo.quest;

import forge.game.GameType;
import forge.game.GameView;
import forge.gamemodes.quest.bazaar.QuestItemType;
import forge.model.FModel;

/**
 * <b>Sleight of Hand</b>, la mejora del bazar que hace gratis el mulligan
 * (Discord, 16-10-2026: <i>"it didnt put to the bottom. it kept the 7 cards,
 * but i still had to select a card"</i>).
 *
 * <p>Funciona, pero en un orden que no se entiende: Forge
 * ({@code QuestController.receiveGameEvent}) roba una carta de mas cuando
 * dispara el aviso del mulligan, y con la regla de Londres ese aviso llega
 * <b>despues</b> de que hayas puesto las cartas al fondo
 * ({@code AbstractMulligan.mulligan}: robar 7, devolver, y luego
 * {@code onMulliganned}). O sea que te pide devolver una de siete como si la
 * mejora no existiera, y la carta llega luego, sin decir nada. No se toca el
 * motor: se dice en la pregunta.
 *
 * <p>Java puro: lo usa tambien Android por el jar.
 */
public final class QuestSleight {

    private QuestSleight() {
    }

    /**
     * La linea que se anyade debajo de "Devuelve N cartas al fondo de tu
     * biblioteca", o null si no toca: no es esa pregunta, no es una partida de
     * la Quest o no tienes la mejora.
     */
    public static String note(final String message, final GameView game) {
        try {
            if (game == null || game.getGameType() != GameType.Quest
                    || !forge.neo.EnginePhrase.contains(message, "lblReturnForLondon")
                    || !NeoQuest.isActive()
                    || !FModel.getQuest().getAssets().hasItem(QuestItemType.SLEIGHT)) {
                return null;
            }
            return forge.neo.NeoText.get("quest.sleight.note");
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
