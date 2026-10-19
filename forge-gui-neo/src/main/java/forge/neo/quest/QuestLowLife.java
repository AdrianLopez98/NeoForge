package forge.neo.quest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gamemodes.quest.QuestEvent;
import forge.gamemodes.quest.QuestEventChallenge;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;
import forge.sound.MusicPlaylist;

/**
 * <b>Menos vida para empezar</b>: 15 en Estandar y 30 en Commander, marcado al
 * crear la Quest (Discord, 18-10-2026: <i>"a New Campaign setting to adjust our
 * starting life down. Suggest 15 ... independent of whether we select easy,
 * normal, hard"</i>).
 *
 * <p>Es el "Fantasy Mode" del Forge de siempre, pero solo la vida: ese modo
 * ({@code QuestMode.Fantasy}) da 15 y 35, y ademas cambia otras cosas del
 * cuartel. Aqui se resta 5 (o 10 en Commander) a lo que el motor calcula, asi
 * que el elixir del bazar y la libra de carne siguen sumando y restando
 * encima, como en Forge.
 *
 * <p><b>Como se aplica sin tocar el motor.</b> La vida la pone
 * {@code QuestUtil.finishStartingGame} en el asiento del jugador y luego pide
 * el {@code HostedMatch} a la plataforma. Ese match se cambia por uno que, al
 * arrancar, baja la vida de los asientos humanos ({@link #replace}). Los
 * desafios que fijan tu vida ({@code getHumanLife}) se dejan como vienen: en
 * Forge tampoco les afecta el modo.
 *
 * <p>Se guarda al lado de la partida ({@code <nombre>.neo-vida-baja}), como
 * el Coliseo y el sobre del dia: el fichero de Forge no se toca.
 */
public final class QuestLowLife {

    private QuestLowLife() {
    }

    /** La vida que puso el ultimo match bajado, o -1: para questcheck. */
    static volatile int lastApplied = -1;

    private static File fileOf(final String questName) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, questName + ".neo-vida-baja");
    }

    /** La marca, al crear la Quest. */
    public static synchronized void set(final String questName, final boolean on) {
        if (questName == null || questName.isBlank()) {
            return;
        }
        final File f = fileOf(questName);
        try {
            if (on) {
                Files.writeString(f.toPath(), "15/30\n");
            } else {
                Files.deleteIfExists(f.toPath());
            }
        } catch (final IOException | RuntimeException e) {
            System.err.println("[quest] no se ha podido guardar la vida baja: " + e);
        }
    }

    static synchronized void deleteSaved(final String questName) {
        set(questName, false);
    }

    /** Si la Quest cargada empieza con menos vida. */
    public static boolean isOn() {
        try {
            final String name = FModel.getQuest() == null ? null : FModel.getQuest().getName();
            return name != null && !name.isBlank() && fileOf(name).isFile();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** Cuanto se resta: 5 en Estandar, 10 en Commander, 0 si no esta marcada. */
    public static int drop() {
        if (!isOn()) {
            return 0;
        }
        return FModel.getQuest().getDeckConstructionRules()
                == forge.gamemodes.quest.data.DeckConstructionRules.Commander ? 10 : 5;
    }

    /** Si el duelo fija tu vida por su cuenta (algunos desafios). */
    private static boolean fixedLife(final QuestEvent event) {
        return event instanceof QuestEventChallenge c && c.getHumanLife() != null;
    }

    /**
     * El match que hay que devolver al motor para este duelo: el mismo si no
     * hay que bajar nada, o uno que baja la vida al arrancar. Quien lo llame
     * le pone despues sus ganchos (el de la IA blindada incluido: es otro
     * objeto).
     */
    public static HostedMatch replace(final HostedMatch match, final QuestEvent event) {
        final int drop = drop();
        if (drop <= 0 || fixedLife(event)) {
            return match;
        }
        return new Lowered(drop);
    }

    /** Baja la vida de los asientos con interfaz (el jugador) antes de empezar. */
    private static final class Lowered extends HostedMatch {
        private final int drop;

        Lowered(final int drop) {
            this.drop = drop;
        }

        @Override
        public void startMatch(final GameRules gameRules, final Set<GameType> appliedVariants,
                               final List<RegisteredPlayer> players,
                               final Map<RegisteredPlayer, IGuiGame> guis, final MusicPlaylist playlist) {
            if (guis != null) {
                for (final RegisteredPlayer rp : guis.keySet()) {
                    rp.setStartingLife(Math.max(1, rp.getStartingLife() - drop));
                    lastApplied = rp.getStartingLife();
                }
            }
            super.startMatch(gameRules, appliedVariants, players, guis, playlist);
        }
    }
}
