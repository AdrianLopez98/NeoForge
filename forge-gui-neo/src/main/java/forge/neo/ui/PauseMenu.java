package forge.neo.ui;

import java.util.function.Consumer;

import forge.neo.NeoText;
import forge.neo.tutorial.Gesture;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * El menu de Escape: continuar, ajustes, reiniciar, salir.
 *
 * <p>Vive en una capa PROPIA, por encima de la de los dialogos del motor. Es
 * importante: el motor puede estar bloqueado esperando una respuesta ("elige
 * una carta") justo cuando pulsas Escape, y su dialogo no se puede perder. Con
 * dos capas, el menu se pone encima y al cerrarlo el dialogo sigue ahi.
 *
 * <p>Reiniciar y salir piden confirmacion porque las dos tiran la partida en
 * curso, y estas a un tecleo de distancia de ellas.
 */
public class PauseMenu extends StackPane {

    /** Que ha pedido el jugador. */
    public interface Actions {
        void resume();

        void restart();

        void quitToMenu();

        /**
         * Si hay ordenes de disparos guardados con "Usar siempre este orden".
         * Solo entonces sale el boton de olvidarlos.
         */
        default boolean hasSavedOrders() {
            return false;
        }

        /** Olvida esos ordenes: es el "Reset Saved Orders" del menu de Forge. */
        default void forgetSavedOrders() {
        }

        /**
         * Si se puede rendir solo esta partida de un partido al mejor de N
         * ({@code NeoMatchUI.canConcedeGame}). Solo entonces sale el boton.
         */
        default boolean canConcedeGame() {
            return false;
        }

        /** Rinde esta partida y sigue el partido ({@code NeoMatchUI.concedeGame}). */
        default void concedeGame() {
        }

        /**
         * Las trampas de Forge si esta partida admite el modo desarrollador
         * (ajuste encendido y partida suelta contra la IA), o null: entonces
         * "Dev" ni sale. Ver {@code NeoSettings.DEV_MODE}.
         */
        default forge.interfaces.IDevModeCheats devCheats() {
            return null;
        }
    }

    private final VBox root = new VBox(10);
    private final Actions actions;
    private final SettingsPanel.Host settingsHost;

    /**
     * Quien se entera de que se ha entrado en Ajustes, si hay alguien.
     *
     * <p>Lo pone el tutorial, que tiene un paso dedicado a que el jugador vea
     * lo que hay ahi dentro. Entrar en Ajustes no llega al motor de ninguna
     * forma, asi que hay que contarlo desde aqui. Ver {@code Gesture}.
     */
    private Consumer<String> gestureSpy;

    /**
     * Si al salir de esta partida se pierde algo que no se recupera.
     *
     * <p>Lo enciende Ascenso, y cambia dos cosas del menu. Ver
     * {@link #PauseMenu(Actions, SettingsPanel.Host, boolean)}.
     */
    private final boolean runAtStake;

    /**
     * Si es una partida en red. Ahi "Reiniciar" no existe — la partida no es
     * nuestra, es de la sala, y lo unico que hacia era volver a ella igual que
     * "Salir" — y salir no lleva al menu sino a la sala, rindiendote.
     */
    private final boolean netGame;

    /**
     * Si es un duelo de la <b>Aventura</b>.
     *
     * <p>Ahi no hay "Reiniciar" (repetir el duelo seria volver a barajar contra
     * el mismo enemigo) y salir no lleva al menu: se rinde uno y vuelve al
     * mapa. Lo unico que cambia son las palabras, pero <b>tienen que cambiar
     * las dos</b>: hasta el 22-09-2026 solo se reetiquetaba el boton — a mano,
     * buscando el texto entre los nodos desde {@code DuelControls} — y la
     * pregunta que salia detras seguia diciendo "vuelves a la pantalla de
     * inicio", que aqui es mentira.
     */
    private final boolean adventure;

    public PauseMenu(final Actions actions, final SettingsPanel.Host settingsHost) {
        this(actions, settingsHost, false);
    }

    public PauseMenu(final Actions actions, final SettingsPanel.Host settingsHost,
                     final boolean runAtStake) {
        this(actions, settingsHost, runAtStake, false);
    }

    /** El menu del duelo de la Aventura: sin reiniciar, y salir es rendirse. */
    public static PauseMenu forAdventure(final Actions actions,
                                         final SettingsPanel.Host settingsHost) {
        return new PauseMenu(actions, settingsHost, false, false, true, false);
    }

    /**
     * @param runAtStake si esta partida es un nodo de una <b>run</b> de Ascenso
     *
     * <p><b>Que cambia, y por que.</b> En Ascenso un nodo <b>solo se marca
     * resuelto al ganarlo</b>. O sea que dejar la partida a medias y volver
     * significaria entrar otra vez en el mismo nodo con otra mano: es la unica
     * forma de hacer trampa que tendria el modo, y ademas la mas tentadora
     * (basta con que empiece a ir mal). Asi que salir a medias <b>cuesta la
     * run</b>, igual que perder el duelo.
     *
     * <ul>
     *   <li><b>"Reiniciar" no aparece.</b> Reiniciar el duelo <i>es</i> volver a
     *       barajar tu mano: la trampa en estado puro. Y dejarlo puesto pero
     *       haciendo que acabe la run seria un boton que dice una cosa y hace la
     *       contraria, que es peor que no tenerlo (principio 1 del las notas de diseño).
     *   <li><b>"Salir al menu" avisa de lo que cuesta</b>, con todas las letras.
     *       Es el principio 6b: lo que no se deshace, pregunta.
     * </ul>
     *
     * <p>La salida <b>sin coste</b> existe y esta donde tiene que estar: el
     * boton "Volver" del mapa. Ahi no hay ninguna partida a medias que
     * rebobinar, asi que la run se guarda y se continua cuando se quiera.
     */
    public PauseMenu(final Actions actions, final SettingsPanel.Host settingsHost,
                     final boolean runAtStake, final boolean netGame) {
        this(actions, settingsHost, runAtStake, netGame, false, false);
    }

    /**
     * El de una partida, diciendo ademas si se puede reiniciar. El torneo de
     * draft de la Quest no deja: reiniciar seria un reintento gratis con
     * premios en juego (revision del 13-10-2026). Salir sigue con su texto de
     * siempre, no el de Ascenso.
     */
    public static PauseMenu forMatch(final Actions actions, final SettingsPanel.Host settingsHost,
                                     final boolean runAtStake, final boolean netGame,
                                     final boolean restartAllowed) {
        return new PauseMenu(actions, settingsHost, runAtStake, netGame, false, !restartAllowed);
    }

    private PauseMenu(final Actions actions, final SettingsPanel.Host settingsHost,
                      final boolean runAtStake, final boolean netGame, final boolean adventure,
                      final boolean noRestart) {
        this.actions = actions;
        this.settingsHost = settingsHost;
        this.runAtStake = runAtStake;
        this.netGame = netGame;
        this.adventure = adventure;
        this.noRestart = noRestart;
        setAlignment(Pos.CENTER);
        showMain();
    }

    private final boolean noRestart;

    /**
     * Una repeticion (Forge #12255): ni se reinicia ni se pierde nada, asi que
     * en vez de "Reiniciar" y "Salir al menu" (con sus avisos de partida
     * perdida, que aqui mentian) hay un solo "Cerrar la repeticion", sin
     * pregunta. Vuelve a la lista.
     */
    private boolean replay;

    public void asReplay() {
        replay = true;
        showMain();
    }

    private void showMain() {
        root.getStyleClass().addAll("dialog", "pause-menu");
        root.setSpacing(10);
        root.setPadding(new Insets(26, 30, 22, 30));
        root.setAlignment(Pos.CENTER);
        root.setMaxWidth(Region.USE_PREF_SIZE);
        root.setMaxHeight(Region.USE_PREF_SIZE);
        root.getChildren().clear();

        final Label title = new Label(NeoText.get("pause.title"));
        title.getStyleClass().add("dialog-title");

        root.getChildren().addAll(title,
                item(NeoText.get("pause.resume"), "btn-primary", actions::resume),
                item(NeoText.get("common.settings"), "btn-secondary", this::showSettings));

        // El pago automatico a mano, si se ha pedido (Ajustes, apagado de
        // fabrica): dice como esta y lo cambia sin entrar en Ajustes.
        if (forge.neo.NeoSettings.autoManaQuick()) {
            final boolean on = forge.neo.NeoSettings.autoPayMana();
            root.getChildren().add(item(NeoText.get(on ? "pause.autoMana.on" : "pause.autoMana.off"),
                    "btn-secondary", () -> {
                        settingsHost.setAutoPayMana(!on);
                        forge.neo.NeoSettings.setBool(forge.neo.NeoSettings.AUTO_MANA, !on);
                        forge.neo.NeoSettings.save();
                        showMain();
                    }));
        }

        // EL MODO DESARROLLADOR: solo si la partida lo admite (Actions.devCheats).
        if (!replay && actions.devCheats() != null) {
            root.getChildren().add(item(NeoText.get("pause.dev"), "btn-secondary", this::showDev));
        }

        if (actions.hasSavedOrders()) {
            root.getChildren().add(item(NeoText.get("pause.forgetOrders"), "btn-secondary", () -> {
                actions.forgetSavedOrders();
                showMain();
            }));
        }

        // Reiniciar NO se ofrece en una run: reiniciar el duelo es volver a
        // barajar tu mano, o sea la trampa que el modo entero tiene que impedir.
        if (replay) {
            root.getChildren().add(item(NeoText.get("over.replay"), "btn-secondary", actions::quitToMenu));
            getChildren().setAll(root);
            return;
        }
        if (!runAtStake && !netGame && !adventure && !noRestart) {
            root.getChildren().add(item(NeoText.get("pause.restart"), "btn-secondary",
                    () -> confirm(NeoText.get("pause.restart.ask"),
                            NeoText.get("pause.restart.detail"),
                            NeoText.get("pause.restart.yes"), actions::restart)));
        }

        // Rendir SOLO esta partida, en un partido al mejor de N (Discord,
        // 03-11-2026): la pantalla de derrota de siempre, con "Siguiente
        // partida". Va justo encima de salir, que en un partido rinde TODO.
        if (actions.canConcedeGame()) {
            root.getChildren().add(item(NeoText.get("pause.concedeGame"), "btn-secondary",
                    () -> confirm(NeoText.get("pause.concedeGame.ask"),
                            NeoText.get("pause.concedeGame.detail"),
                            NeoText.get("pause.concedeGame.yes"), actions::concedeGame)));
        }

        root.getChildren().add(item(NeoText.get(adventure ? "adventure.concede"
                        : netGame ? "pause.quit.net" : "pause.quit"),
                "btn-secondary", this::askQuit));

        getChildren().setAll(root);
    }

    /**
     * La pregunta de salir, directamente.
     *
     * <p>Es tambien lo que abre el atajo de rendirse (Ctrl+Q en Forge): rendirse
     * no se deshace, asi que por teclado pregunta igual que por el menu — y con
     * las mismas palabras, incluido el aviso de que en Ascenso cuesta la run.
     */
    public void askQuit() {
        if (replay) {
            actions.quitToMenu();
            return;
        }
        if (adventure) {
            confirm(NeoText.get("adventure.concede.ask"),
                    NeoText.get("adventure.concede.detail"),
                    NeoText.get("adventure.concede.yes"), actions::quitToMenu);
            return;
        }
        if (netGame) {
            confirm(NeoText.get("pause.quit.net.ask"), NeoText.get("pause.quit.net.detail"),
                    NeoText.get("pause.quit.net.yes"), actions::quitToMenu);
            return;
        }
        confirm(NeoText.get(runAtStake ? "pause.quit.run.ask" : "pause.quit.ask"),
                NeoText.get(runAtStake ? "pause.quit.run.detail" : "pause.quit.detail"),
                NeoText.get(runAtStake ? "pause.quit.run.yes" : "pause.quit.yes"),
                actions::quitToMenu);
    }

    /**
     * Lo que esta encendido de las dos trampas que son interruptores (tierras sin
     * limite y ver todas las cartas), por partida: Forge no deja leerlo, asi que
     * se apunta aqui con el objeto de trampas de esa partida como clave.
     */
    private static final java.util.Map<forge.interfaces.IDevModeCheats, boolean[]> DEV_TOGGLES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * EL MENU "DEV": las trampas de Forge ({@code IDevModeCheats}), como el menu
     * Dev de la GUI Swing. Cada una cierra el menu y corre en SU PROPIO HILO, ni
     * el de JavaFX ni el del motor: casi todas preguntan algo (que carta, que
     * jugador) con los dialogos de siempre, que bloquean a quien llama hasta la
     * respuesta (las notas de diseño). Lo que cambia la partida lo pasa Forge a su hilo
     * ({@code GameAction.invoke}).
     */
    private void showDev() {
        final forge.interfaces.IDevModeCheats cheats = actions.devCheats();
        if (cheats == null) {
            showMain();
            return;
        }
        final boolean[] toggles = DEV_TOGGLES.computeIfAbsent(cheats, k -> new boolean[2]);
        final VBox box = new VBox(10);
        box.getStyleClass().addAll("dialog", "pause-menu");
        box.setPadding(new Insets(22, 26, 18, 26));
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);

        final Label title = new Label(NeoText.get("pause.dev"));
        title.getStyleClass().add("dialog-title");
        final Label sub = new Label(NeoText.get("dev.note"));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        sub.setMaxWidth(UiScale.px(460));

        // Dos columnas: un GridPane (un TilePane dentro del scroll se quedaba en una).
        final javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        final java.util.List<Button> buttons = new java.util.ArrayList<>();
        final Object[][] all = {
            {"dev.mana", (Runnable) cheats::generateMana},
            {"dev.untap", (Runnable) cheats::untapPermanents},
            {"dev.tap", (Runnable) cheats::tapPermanents},
            {"dev.life", (Runnable) cheats::setPlayerLife},
            {"dev.addCounters", (Runnable) cheats::addCountersToPermanent},
            {"dev.removeCounters", (Runnable) cheats::removeCountersFromPermanent},
            {"dev.tutor", (Runnable) cheats::tutorForCard},
            {"dev.toHand", (Runnable) cheats::addCardToHand},
            {"dev.toBattlefield", (Runnable) cheats::addCardToBattlefield},
            {"dev.token", (Runnable) cheats::addTokenToBattlefield},
            {"dev.toLibrary", (Runnable) cheats::addCardToLibrary},
            {"dev.toGraveyard", (Runnable) cheats::addCardToGraveyard},
            {"dev.toExile", (Runnable) cheats::addCardToExile},
            {"dev.cast", (Runnable) cheats::castASpell},
            {"dev.repeat", (Runnable) cheats::repeatLastAddition},
            {"dev.exileHand", (Runnable) cheats::exileCardsFromHand},
            {"dev.exileBattlefield", (Runnable) cheats::exileCardsFromBattlefield},
            {"dev.removeFromGame", (Runnable) cheats::removeCardsFromGame},
            {"dev.win", (Runnable) cheats::winGame},
        };
        for (final Object[] a : all) {
            buttons.add(devButton(NeoText.get((String) a[0]), (Runnable) a[1]));
        }
        // Los dos interruptores: dicen como estan.
        buttons.add(devButton(NeoText.get(toggles[0] ? "dev.lands.on" : "dev.lands.off"), () -> {
            toggles[0] = !toggles[0];
            cheats.setCanPlayUnlimitedLands(toggles[0]);
        }));
        buttons.add(devButton(NeoText.get(toggles[1] ? "dev.viewAll.on" : "dev.viewAll.off"), () -> {
            toggles[1] = !toggles[1];
            cheats.setViewAllCards(toggles[1]);
        }));
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setPrefWidth(UiScale.px(230));
            grid.add(buttons.get(i), i % 2, i / 2);
        }
        final javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(UiScale.px(520));

        box.getChildren().addAll(title, sub, scroll,
                item(NeoText.get("common.back"), "btn-primary", this::showMain));
        getChildren().setAll(box);
    }

    /** Para la captura: abre el menu Dev (NeoApp, --mock-pause -Dneo.dev.open=true). */
    public void showDevForTest() {
        showDev();
    }

    /** Un boton de trampa: cierra el menu y la lanza en su hilo. */
    private Button devButton(final String text, final Runnable cheat) {
        final Button b = new Button(text);
        b.getStyleClass().addAll("btn-secondary", "pause-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> {
            actions.resume();
            final Thread t = new Thread(() -> {
                try {
                    cheat.run();
                } catch (final RuntimeException ex) {
                    System.err.println("[neo] modo desarrollador: " + ex);
                    ex.printStackTrace();
                }
            }, "neo-dev-cheat");
            t.setDaemon(true);
            t.start();
        });
        return b;
    }

    /** Quien se entera de los gestos de este menu. Lo usa el tutorial. */
    public void setGestureSpy(final Consumer<String> spy) {
        this.gestureSpy = spy;
    }

    private void showSettings() {
        getChildren().setAll(new SettingsPanel(settingsHost, this::showMain));
        if (gestureSpy != null) {
            gestureSpy.accept(Gesture.SETTINGS_OPEN);
        }
    }

    /** Confirmacion para lo que no tiene vuelta atras. */
    private void confirm(final String question, final String detail,
                         final String yesLabel, final Runnable onYes) {
        final VBox box = new VBox(10);
        box.getStyleClass().addAll("dialog", "pause-menu");
        box.setPadding(new Insets(26, 30, 22, 30));
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMaxHeight(Region.USE_PREF_SIZE);

        final Label title = new Label(question);
        title.getStyleClass().add("dialog-title");
        final Label sub = new Label(detail);
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);
        sub.setMaxWidth(UiScale.px(340));

        box.getChildren().addAll(title, sub,
                item(yesLabel, "btn-primary", onYes),
                item(NeoText.get("common.cancel"), "btn-secondary", this::showMain));
        getChildren().setAll(box);
    }

    private static Button item(final String text, final String style, final Runnable action) {
        final Button b = new Button(text);
        b.getStyleClass().addAll(style, "pause-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return b;
    }
}
