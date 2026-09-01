package forge.neo.quest;

import java.util.concurrent.CountDownLatch;

import forge.gamemodes.quest.QuestEvent;
import forge.gamemodes.quest.QuestUtil;
import forge.neo.match.NeoMatchUI;
import forge.neo.match.TableBinder;
import forge.neo.platform.NeoGuiBase;

/**
 * Un duelo de la aventura.
 *
 * <p>A diferencia de {@link forge.neo.match.NeoGame}, aqui <b>no montamos el
 * match</b>: lo monta {@code QuestUtil.finishStartingGame()}, que es la misma
 * llamada que usa la GUI vieja. Y hace bastante mas de lo que parece:
 *
 * <ul>
 *   <li>Vidas segun la modalidad — 20 en Estandar, 40 en Commander — mas las
 *       mejoras del bazar que hayas comprado.</li>
 *   <li>La variante {@code GameType.Commander} si la aventura es de Commander,
 *       con su asiento registrado como toca.</li>
 *   <li>Las cartas que empiezan en la mesa: las tuyas del bazar y las del
 *       rival que traiga el duelo.</li>
 *   <li>El apuesta (ante) y la longitud del match, segun tus preferencias.</li>
 * </ul>
 *
 * <p>Reimplementarlo aqui seria copiar cuarenta lineas de reglas que ya
 * existen, y que ademas cambian con cada actualizacion de Forge.
 *
 * <p><b>Y vale igual para un DESAFIO.</b> {@code QuestUtil.setEvent} recibe
 * {@code QuestEvent}, la clase padre, asi que un {@code QuestEventChallenge}
 * entra por el mismo sitio: las cartas que empiezan en la mesa, la vida rara
 * del rival y su mazo fijo los aplica {@code finishStartingGame} sin que haya
 * que decirle nada. Los 37 desafios de Forge no costaron ni una linea de
 * partida.
 *
 * <p>Lo unico que hace falta es decirle QUE interfaz usar — {@code IGuiBase}
 * tiene la fabrica — y quedarse con el {@code HostedMatch} que crea, para poder
 * esperar a que termine.
 */
public final class NeoQuestMatch {

    private NeoQuestMatch() {
    }

    /**
     * Juega un duelo y aplica las recompensas.
     *
     * <p>Llamar desde un hilo que NO sea el de interfaz: se queda esperando a
     * que el match termine.
     *
     * @return lo que el motor te ha dado por ganarlo (o nada, si perdiste)
     */
    public static NeoQuestRewards play(final QuestEvent duel, final TableBinder binder,
                                       final boolean autoPayMana) {
        return play(duel, binder, autoPayMana, NeoMatchUI.Mode.HUMAN);
    }

    /**
     * Igual, en el modo que se pida.
     *
     * <p>{@code AUTO_PLAY} contesta sola: es lo que permite comprobar el bucle
     * entero — duelo, victoria o derrota y recompensas — sin abrir una ventana
     * ni jugar cuarenta turnos a mano.
     */
    public static NeoQuestRewards play(final QuestEvent duel, final TableBinder binder,
                                       final boolean autoPayMana, final NeoMatchUI.Mode mode) {
        return playFully(duel, binder, autoPayMana, mode).rewards;
    }

    /**
     * Lo que ha pasado en el duelo: lo que te llevas y COMO has salido.
     *
     * <p>Lo segundo hace falta porque el menu de pausa ofrece "Reiniciar" en
     * cualquier partida, tambien en un duelo de la aventura. Sin mirarlo, ese
     * boton te devolvia al cuartel general igual que "Salir": un boton que no
     * hace lo que dice es peor que no tenerlo (principio 1 de las notas de diseño).
     */
    public static final class Outcome {
        public final NeoQuestRewards rewards;
        public final NeoMatchUI.Exit exit;

        Outcome(final NeoQuestRewards rewards, final NeoMatchUI.Exit exit) {
            this.rewards = rewards;
            this.exit = exit;
        }
    }

    /** Igual, pero contando ademas como se ha salido de la partida. */
    public static Outcome playFully(final QuestEvent duel, final TableBinder binder,
                                    final boolean autoPayMana, final NeoMatchUI.Mode mode) {
        final NeoMatchUI gui = new NeoMatchUI(mode, false);
        gui.setQuestDuel(true);
        gui.setAutoPayMana(autoPayMana);
        if (binder != null) {
            gui.setBinder(binder);
            gui.setTable(binder.getTable());
            binder.setMatchUi(gui);
        }

        final CountDownLatch over = new CountDownLatch(1);
        NeoGuiBase.setGuiGameFactory(() -> gui);
        NeoGuiBase.setOnMatchCreated(match -> {
            match.setOnMatchOver(over::countDown);
            return match;
        });

        try {
            QuestUtil.setEvent(duel);
            // canStartGame() levantaria dialogos del motor si algo falla; la
            // legalidad ya se comprueba en el cuartel general, asi que se entra
            // directo a la parte que monta la partida.
            QuestUtil.finishStartingGame();

            // Sin tope: un duelo puede durar lo que quiera. El hilo es daemon.
            over.await();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            NeoGuiBase.setOnMatchCreated(null);
        }

        final NeoQuestRewards rewards = NeoQuestRewards.apply(gui.getGameView());
        final NeoMatchUI.Exit exit = gui.getExitAction();
        gui.shutdown();
        return new Outcome(rewards, exit);
    }
}
