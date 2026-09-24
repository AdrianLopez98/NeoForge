package forge.neo.adventure;

import com.badlogic.gdx.Gdx;
import forge.deck.Deck;
import forge.deck.DeckgenUtil;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.model.FModel;
import forge.player.GamePlayerUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * PRUEBA sin manos: con -Dneo.adventure.selftest=true, en cuanto el Adventure
 * termina de cargar se lanza un duelo DESDE SU HILO, igual que haria su
 * DuelScene, y se juega solo en nuestra mesa. Comprueba lo arriesgado: las
 * dos librerias graficas a la vez, el cambio de GuiBase y la vuelta.
 */
final class SelfTest {

    private SelfTest() {
    }

    static void arm() {
        final String mode = System.getProperty("neo.adventure.selftest", "false");
        if ("false".equals(mode)) {
            return;
        }
        final Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            while (true) {
                try {
                    if (Gdx.app != null && FModel.getMagicDb() != null && FModel.getPreferences() != null
) {
                        break;
                    }
                } catch (final Throwable e) {
                    if (System.currentTimeMillis() - t0 > 60000) {
                        NeoDuelBridge.log("autoprueba: el Adventure no termina de cargar: " + e);
                        return;
                    }
                }
                try {
                    Thread.sleep(500);
                } catch (final InterruptedException e) {
                    return;
                }
            }
            NeoDuelBridge.log("autoprueba: motor del Adventure cargado");
            try {
                Thread.sleep(5000);
            } catch (final InterruptedException e) {
                return;
            }
            NeoDuelBridge.log("autoprueba (" + mode + "): lanzando desde el hilo de libGDX");
            Gdx.app.postRunnable("editor".equals(mode) ? SelfTest::editor
                    : "starter".equals(mode) ? SelfTest::starter
                    : "questlog".equals(mode) ? SelfTest::questLog
                    : "duels".equals(mode) ? () -> duels(1) : SelfTest::duel);
        }, "neo-adventure-selftest");
        t.setDaemon(true);
        t.start();
    }

    /**
     * El editor: se abre con el mazo elegido del jugador, se captura, se guarda
     * un mazo de 20 Bosques (sin tener ninguno: tienen que salir copias "no
     * vendibles", como en su editor) y se vuelve.
     */
    /**
     * {@code -Dneo.adventure.selftest=questlog}: construye el libro de misiones
     * (la tecla Q) y dice si ha podido. En espanyol reventaba al leer su diseno
     * (ver UiFileClash); lo que se comprueba es que carga el diseno y no las
     * misiones traducidas.
     */
    private static void questLog() {
        try {
            forge.adventure.scene.QuestLogScene.instance(null);
            NeoDuelBridge.log("autoprueba questlog: libro de misiones OK (idioma "
                    + forge.adventure.util.Config.instance().getLang() + ")");
        } catch (final Throwable e) {
            NeoDuelBridge.log("autoprueba questlog: FALLA: " + e);
        }
    }

    private static void editor() {
        final forge.deck.CardPool some = new forge.deck.CardPool();
        for (final String n : new String[] {"Lightning Bolt", "Llanowar Elves", "Counterspell",
                "Arcane Signet", "Swamp", "Serra Angel"}) {
            final forge.item.PaperCard c = FModel.getMagicDb().getCommonCards().getCard(n);
            if (c != null) {
                some.add(c, 2);
            }
        }
        forge.adventure.util.Current.player().addCards(some);
        NeoDeckBridge.open(() -> NeoDuelBridge.log("autoprueba editor: VUELTA AL ADVENTURE, mazo = "
                + forge.adventure.util.Current.player().getSelectedDeck().getName() + " con "
                + forge.adventure.util.Current.player().getSelectedDeck().getMain().countAll()
                + " cartas; Bosques en la coleccion = "
                + forge.adventure.util.Current.player().getCards().countAll()
                + "; GuiBase = " + forge.gui.GuiBase.getInterface().getClass().getSimpleName()));
        final Thread t = new Thread(() -> {
            try {
                Thread.sleep(Long.getLong("neo.adventure.snapshotMs", 8000));
                final String file = System.getProperty("neo.adventure.snapshot");
                // -Dneo.adventure.snapshotSize=1600x900: la ventana de la prueba
                // sale del tamano de la del Adventure, que sin partida es diminuta.
                final String size = System.getProperty("neo.adventure.snapshotSize");
                if (file != null && size != null) {
                    final String[] wh = size.split("x");
                    javafx.application.Platform.runLater(() -> NeoWindow.resizeForTest(
                            Double.parseDouble(wh[0]), Double.parseDouble(wh[1])));
                    Thread.sleep(3000);
                }
                if (file != null) {
                    javafx.application.Platform.runLater(() -> {
                        try {
                            forge.neo.Snapshots.writePng(NeoWindow.scene().snapshot(null), new java.io.File(file));
                            NeoDuelBridge.log("autoprueba editor: captura en " + file);
                        } catch (final Exception e) {
                            NeoDuelBridge.log("no se pudo capturar: " + e);
                        }
                    });
                    Thread.sleep(2000);
                }
                final Deck deck = new Deck("Mazo de prueba NeoForge");
                deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Forest"), 20);
                NeoDeckBridge.lastContext.storage().add(deck);
                NeoWindow.giveBack(() -> NeoDuelBridge.log("autoprueba editor: "
                        + "mazo = " + forge.adventure.util.Current.player().getSelectedDeck().getName()
                        + " (" + forge.adventure.util.Current.player().getSelectedDeck().getMain().countAll()
                        + " cartas), cartas en la coleccion = "
                        + forge.adventure.util.Current.player().getCards().countAll()
                        + ", GuiBase = " + forge.gui.GuiBase.getInterface().getClass().getSimpleName()));
            } catch (final Throwable e) {
                NeoDuelBridge.log("autoprueba editor ha fallado: " + e);
                e.printStackTrace();
            }
        }, "neo-adventure-selftest-editor");
        t.setDaemon(true);
        t.start();
    }

    /**
     * {@code -Dneo.adventure.selftest=duels} (+ {@code neo.adventure.duels=N}, 5
     * de fabrica): N duelos seguidos, con la VRAM de JavaFX apuntada al empezar
     * cada uno (la pone NeoDuelBridge) y al volver. Es la prueba de la pantalla
     * en blanco del 23-09-2026, que salia "cada 3-5 duelos".
     */
    private static void duels(final int n) {
        final int total = Integer.getInteger("neo.adventure.duels", 5);
        duel(() -> {
            NeoDuelBridge.log("autoprueba duels: " + n + " de " + total + " terminado | "
                    + forge.neo.platform.PrismGuard.vram() + " | reparaciones "
                    + forge.neo.platform.PrismGuard.repairs());
            if (n < total) {
                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (final InterruptedException e) {
                        return;
                    }
                    Gdx.app.postRunnable(() -> duels(n + 1));
                }, "neo-adventure-selftest-duels").start();
            } else {
                NeoDuelBridge.log("autoprueba duels: FIN");
            }
        });
    }

    private static void duel() {
        duel(null);
    }

    private static void duel(final Runnable after) {
        final EnumSet<GameType> variants = EnumSet.of(GameType.Adventure);
        final Deck mine = DeckgenUtil.getRandomColorDeck(false);
        final Deck theirs = DeckgenUtil.getRandomColorDeck(true);
        final RegisteredPlayer human = RegisteredPlayer.forVariants(2, variants, mine, null, false, null, null);
        human.setPlayer(GamePlayerUtil.getGuiPlayer());
        human.setStartingLife(20);
        final RegisteredPlayer ai = RegisteredPlayer.forVariants(2, variants, theirs, null, false, null, null);
        ai.setPlayer(GamePlayerUtil.createAiPlayer("Goblin de prueba"));
        ai.setStartingLife(20);
        final List<RegisteredPlayer> players = new ArrayList<>();
        players.add(ai);
        players.add(human);
        final GameRules rules = new GameRules(GameType.Adventure);
        rules.setGamesPerMatch(1);
        rules.setManaBurn(false);
        rules.setWarnAboutAICards(false);
        final HostedMatch hosted = new HostedMatch();
        NeoDuelBridge.play(hosted, rules, variants, players, human, "Goblin de prueba",
                () -> {
                    // Lo que sostiene el bloque NEOFORGE-2 de DuelScene: al volver,
                    // el juego ya es null (endCurrentGame) pero el match sigue y
                    // sabe quien gano. Si esto dejara de ser cierto, el duelo de la
                    // Aventura volveria a contar como derrota siempre.
                    NeoDuelBridge.log("autoprueba: getGame() = " + hosted.getGame()
                            + " / getMatch() = " + hosted.getMatch()
                            + " / ganador = " + (hosted.getMatch() == null ? "?"
                            : hosted.getMatch().getWinner())
                            + " / gana el humano = " + (hosted.getMatch() != null
                            && human == hosted.getMatch().getWinner()));
                    NeoDuelBridge.log("autoprueba: VUELTA AL ADVENTURE en el hilo "
                            + Thread.currentThread().getName() + ", GuiBase = "
                            + forge.gui.GuiBase.getInterface().getClass().getSimpleName());
                    if (after != null) {
                        after.run();
                    }
                });
    }


    /**
     * El mazo de salida (StarterDeck), sin manos. Primero, por cada color:
     * cuantos comandantes de la lista ofrece y un mazo generado para uno de
     * ellos. Despues crea un mundo nuevo en Commander negro como el boton de
     * "nueva partida", captura la eleccion ({@code snapshot}), elige el primero
     * y dice con que mazo se queda. No guarda partida.
     */
    private static void starter() {
        new Thread(() -> {
            for (final String l : new String[] {"W", "U", "B", "R", "G"}) {
                final forge.card.ColorSet color = forge.card.ColorSet.fromNames(l.toCharArray());
                final List<forge.item.PaperCard> pool = StarterDeck.pool(color);
                final forge.item.PaperCard cmd = pool.get(0);
                final Deck d = StarterDeck.generate(cmd, color);
                NeoDuelBridge.log("autoprueba starter " + l + ": " + pool.size() + " comandantes; "
                        + cmd.getName() + " -> " + (d == null ? "SIN MAZO" : d.getMain().countAll() + " cartas, "
                        + d.getMain().toFlatList().stream().filter(c -> c.getRules().getType().isLand()).count()
                        + " tierras"));
            }
            Gdx.app.postRunnable(() -> {
                final forge.adventure.data.DifficultyData diff =
                        forge.adventure.util.Config.instance().getConfigData().difficulties[1];
                forge.adventure.world.WorldSave.generateNewWorld("Prueba", true, 0, 0,
                        forge.card.ColorSet.fromNames("B".toCharArray()), diff,
                        forge.adventure.util.AdventureModes.Commander, 0, null, 1234);
                NeoDuelBridge.log("autoprueba starter: mundo nuevo con "
                        + forge.adventure.util.Current.player().getSelectedDeck()
                                .get(forge.deck.DeckSection.Commander).get(0).getName());
            });
            try {
                Thread.sleep(Long.getLong("neo.adventure.snapshotMs", 8000));
                final String size = System.getProperty("neo.adventure.snapshotSize");
                if (size != null) {
                    final String[] wh = size.split("x");
                    javafx.application.Platform.runLater(() -> NeoWindow.resizeForTest(
                            Double.parseDouble(wh[0]), Double.parseDouble(wh[1])));
                    Thread.sleep(3000);
                }
                final String file = System.getProperty("neo.adventure.snapshot");
                javafx.application.Platform.runLater(() -> {
                    try {
                        if (file != null) {
                            forge.neo.Snapshots.writePng(NeoWindow.scene().snapshot(null), new java.io.File(file));
                        }
                        final javafx.scene.Parent root = NeoWindow.scene().getRoot();
                        NeoDuelBridge.log("autoprueba starter: en pantalla " + root.getClass().getSimpleName());
                        if (root instanceof CommanderPickScreen) {
                            ((CommanderPickScreen) root).pickForTest(0);
                        }
                    } catch (final Exception e) {
                        NeoDuelBridge.log("autoprueba starter: " + e);
                    }
                });
                Thread.sleep(20000);
                final forge.adventure.player.AdventurePlayer p = forge.adventure.util.Current.player();
                final Deck d = p.getSelectedDeck();
                NeoDuelBridge.log("autoprueba starter FINAL: " + d.getName() + " / comandante "
                        + d.get(forge.deck.DeckSection.Commander).get(0).getName()
                        + " / " + d.getMain().countAll() + " cartas / coleccion "
                        + p.getCards().countAll() + " / identidad " + p.getColorIdentity());
            } catch (final Throwable e) {
                NeoDuelBridge.log("autoprueba starter ha fallado: " + e);
                e.printStackTrace();
            }
        }, "neo-adventure-selftest-starter").start();
    }
}
