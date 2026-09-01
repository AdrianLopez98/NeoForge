package forge.neo.ui;

import forge.neo.NeoText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import forge.neo.NeoSettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Ajustes: lo grafico y el sonido, en una sola pantalla.
 *
 * <p>Se abre desde el menu de pausa (Escape) y desde la pantalla de inicio, y
 * es el MISMO panel en los dos sitios: dos pantallas de ajustes que se parecen
 * pero no son iguales es la forma mas facil de que se desincronicen.
 *
 * <p>Todo se aplica <b>al momento</b>, sin botón de "guardar": mueves el
 * volumen y lo oyes, mueves la escala y la ves. Guardar aparte solo sirve para
 * que el jugador se pregunte si ha hecho efecto.
 *
 * <p><b>El titulo y el boton de cerrar NO se mueven; lo de en medio rueda.</b>
 * Y eso no es cosmetico: aqui dentro esta la escala de interfaz, o sea que el
 * panel puede crecer <i>por lo que acabas de tocar</i>. Con todo en un bloque,
 * poner la escala al 130% empujaba el boton de cerrar fuera de la ventana y
 * dejaba al jugador encerrado en la pantalla que acababa de romper, sin forma
 * de deshacerlo. Un ajuste que puede dejarte sin salida tiene que tener la
 * salida clavada.
 */
public class SettingsPanel extends VBox {

    /** Lo que el panel necesita de quien lo abre. */
    public interface Host {
        /** Pagar el mana solo al lanzar; puede no haber partida abierta. */
        void setAutoPayMana(boolean on);

        void setPauseMode(int mode);

        /** Ritmo de la IA, en centesimas de multiplicador; puede no haber partida. */
        void setAiSpeed(int hundredths);

        /** Repinta la escala de interfaz en la escena. */
        void applyScale();

        boolean isFullScreen();

        void setFullScreen(boolean on);

        /** Zoom del texto de las cartas; puede no haber mesa abierta. */
        double getTextZoom();

        void setTextZoom(double zoom);

        /** Repinta el rating de las cartas del sobre; puede no haber draft abierto. */
        void setDraftRankingVisible(boolean on);
    }

    /** El titulo, que se queda arriba pase lo que pase. */
    private final Label title = new Label(NeoText.get("settings.title"));

    /** Todo lo que rueda. */
    private final VBox content = new VBox(6);

    private final javafx.scene.control.ScrollPane scroll =
            new javafx.scene.control.ScrollPane(content);

    /** El pie con el boton de cerrar, que se queda abajo pase lo que pase. */
    private final HBox footer = new HBox();

    public SettingsPanel(final Host host, final Runnable onClose) {
        getStyleClass().addAll("dialog", "settings");
        setSpacing(6);
        setPadding(new Insets(22, 26, 18, 26));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        title.getStyleClass().add("dialog-title");

        getChildren().addAll(title, section(NeoText.get("settings.language")));

        // --- idioma ---
        //
        // Forge viene traducido a diez idiomas y con los NOMBRES DE LAS CARTAS
        // en ocho; lo unico que faltaba era dejar elegir. Ver NeoLanguage.
        //
        // Pide reiniciar y no es pereza: los nombres de carta se precargan
        // ANTES de leer las cartas, asi que cambiarlos en caliente dejaria la
        // mitad de la aplicacion en un idioma y la otra mitad en otro.
        final java.util.List<forge.neo.NeoLanguage.Option> langs =
                forge.neo.NeoLanguage.available();
        final String[] labels = new String[langs.size()];
        for (int i = 0; i < langs.size(); i++) {
            labels[i] = langs.get(i).getLabel();
        }
        // La nota va DEBAJO y hablando del idioma elegido, no repetida en cada
        // boton: metida en la etiqueta hacia los botones el doble de anchos y
        // no cabian diez en una fila.
        final Label note = new Label();
        note.getStyleClass().add("home-subtitle");
        note.setWrapText(true);
        note.setMaxWidth(560);
        note.setMinHeight(Region.USE_PREF_SIZE);
        note.setText(noteFor(langs, forge.neo.NeoLanguage.current(), false));

        getChildren().add(choiceRow(NeoText.get("settings.language.row"), labels,
                labelFor(langs, forge.neo.NeoLanguage.current()),
                v -> {
                    for (final forge.neo.NeoLanguage.Option o : langs) {
                        if (v.equals(o.getLabel())) {
                            forge.neo.NeoLanguage.set(o.getId());
                            note.setText(noteFor(langs, o.getId(), true));
                            break;
                        }
                    }
                }));
        getChildren().add(note);

        getChildren().add(section(NeoText.get("settings.graphics")));

        // --- escala de interfaz ---
        final Double savedScale = NeoSettings.getScale();
        getChildren().add(choiceRow(NeoText.get("settings.scale"),
                new String[] {"Auto", "90%", "100%", "115%", "130%", "150%"},
                savedScale == null ? "Auto"
                        : String.format(java.util.Locale.ROOT, "%.0f%%", savedScale * 100),
                v -> {
                    final Double scale = "Auto".equals(v) ? null
                            : Double.valueOf(Integer.parseInt(v.replace("%", "")) / 100.0);
                    UiScale.setOverride(scale);
                    NeoSettings.setScale(scale);
                    NeoSettings.save();
                    host.applyScale();
                }));

        // --- animaciones ---
        getChildren().add(toggleRow(NeoText.get("settings.animations"), NeoSettings.getBool(NeoSettings.ANIMATIONS, true),
                on -> {
                    forge.neo.card.CardNode.setAnimationsEnabled(on);
                    NeoSettings.setBool(NeoSettings.ANIMATIONS, on);
                    NeoSettings.save();
                }));

        // --- brillo de las foil (la auditoría del motor D5) ---
        getChildren().add(toggleRow(NeoText.get("settings.foilEffect"),
                NeoSettings.getBool(NeoSettings.FOIL_EFFECT, true),
                on -> {
                    forge.neo.card.CardNode.setFoilEffectEnabled(on);
                    NeoSettings.setBool(NeoSettings.FOIL_EFFECT, on);
                    NeoSettings.save();
                    if (getScene() != null && getScene().getRoot() != null) {
                        forge.neo.card.CardNode.refreshAllIn(getScene().getRoot());
                    }
                }));

        // --- arte de las cartas en tu idioma ---
        //
        // Scryfall sirve las impresiones traducidas y son otra carta distinta,
        // asi que esto se nota de verdad: el nombre y el texto que se leen EN
        // LA CARTA. Se puede apagar porque hay cartas que no se han impreso
        // fuera del ingles y porque es una segunda descarga entera.
        getChildren().add(toggleRow(NeoText.get("settings.cardArtLanguage"),
                NeoSettings.getBool(NeoSettings.CARD_ART_LANGUAGE, true),
                on -> {
                    NeoSettings.setBool(NeoSettings.CARD_ART_LANGUAGE, on);
                    NeoSettings.save();
                    forge.neo.card.CardArt.reload();
                    // Lo ya decodificado es del idioma anterior: se tira y se
                    // vuelve a pedir, o no se notaria hasta reiniciar.
                    forge.neo.card.CardImages.clear();
                    if (getScene() != null && getScene().getRoot() != null) {
                        forge.neo.card.CardNode.refreshAllIn(getScene().getRoot());
                    }
                }));

        // --- politica de arte (la auditoría del motor B4) ---
        //
        // Solo decide la impresion por defecto cuando NADIE ha elegido una a
        // mano (importar una lista, un mazo que monta el motor, la IA):
        // PrintingDialog sigue mandando carta a carta. Se cambia en
        // caliente, sin reiniciar — StaticData tiene setter de verdad.
        final String[] artLabels = {
                NeoText.get("settings.cardArt.latest"),
                NeoText.get("settings.cardArt.latestCore"),
                NeoText.get("settings.cardArt.original"),
                NeoText.get("settings.cardArt.originalCore")};
        final boolean[][] artValues = {{true, false}, {true, true}, {false, false}, {false, true}};
        final boolean artLatestNow =
                NeoSettings.getBool(NeoSettings.CARD_ART_LATEST, NeoSettings.CARD_ART_LATEST_DEFAULT);
        final boolean artCoreNow = NeoSettings.getBool(NeoSettings.CARD_ART_CORE_ONLY, false);
        String artCurrent = artLabels[0];
        for (int i = 0; i < artValues.length; i++) {
            if (artValues[i][0] == artLatestNow && artValues[i][1] == artCoreNow) {
                artCurrent = artLabels[i];
            }
        }
        getChildren().add(choiceRow(NeoText.get("settings.cardArt"), artLabels, artCurrent,
                v -> {
                    for (int i = 0; i < artLabels.length; i++) {
                        if (artLabels[i].equals(v)) {
                            NeoSettings.setBool(NeoSettings.CARD_ART_LATEST, artValues[i][0]);
                            NeoSettings.setBool(NeoSettings.CARD_ART_CORE_ONLY, artValues[i][1]);
                        }
                    }
                    NeoSettings.save();
                    NeoSettings.applyCardArtToEngine();
                }));

        // --- arte variado al generar un pool (aventura, sellado del motor) ---
        getChildren().add(toggleRow(NeoText.get("settings.randomArtInPools"),
                NeoSettings.getBool(NeoSettings.RANDOM_ART_IN_POOLS, true),
                on -> {
                    NeoSettings.setBool(NeoSettings.RANDOM_ART_IN_POOLS, on);
                    NeoSettings.save();
                    NeoSettings.applyCardArtToEngine();
                }));

        // --- pantalla completa ---
        getChildren().add(toggleRow(NeoText.get("settings.fullscreen"), host.isFullScreen(),
                on -> {
                    host.setFullScreen(on);
                    NeoSettings.setBool(NeoSettings.FULLSCREEN, on);
                    NeoSettings.save();
                }));

        getChildren().add(section(NeoText.get("settings.game")));

        // --- pago de mana ---
        getChildren().add(toggleRow(NeoText.get("settings.autoMana"),
                NeoSettings.autoPayMana(),
                on -> {
                    host.setAutoPayMana(on);
                    NeoSettings.setBool(NeoSettings.AUTO_MANA, on);
                    NeoSettings.save();
                }));

        // --- fuentes que el motor no sabe usar ---
        //
        // Va JUSTO debajo del pago automatico porque es una coletilla suya, no
        // una pregunta aparte (la auditoría del motor 2): solo hace algo cuando el "Auto"
        // del motor se ha dado por vencido.
        getChildren().add(toggleRow(NeoText.get("settings.blindMana"),
                NeoSettings.getBool(NeoSettings.BLIND_MANA, false),
                on -> {
                    forge.neo.match.NeoMatchUI.setBlindSourceFallback(on);
                    NeoSettings.setBool(NeoSettings.BLIND_MANA, on);
                    NeoSettings.save();
                }));

        // --- regla de mulligan (la auditoría del motor B4) ---
        //
        // El motor trae las cinco (Original, Paris, Vancouver, London,
        // Houston — MulliganDefs.MulliganRule) pero el jugador nunca podia
        // elegir: se aplicaba siempre la de fabrica (London) sin ni un
        // ajuste que lo dijera. Se aplica en NeoGame.applyEnginePrefs.
        final String[] mulliganRules = {"London", "Vancouver", "Paris", "Original", "Houston"};
        final String[] mulliganLabels = {
                NeoText.get("settings.mulligan.london"),
                NeoText.get("settings.mulligan.vancouver"),
                NeoText.get("settings.mulligan.paris"),
                NeoText.get("settings.mulligan.original"),
                NeoText.get("settings.mulligan.houston")};
        final String mulliganNow =
                NeoSettings.get(NeoSettings.MULLIGAN_RULE, NeoSettings.MULLIGAN_RULE_DEFAULT);
        String mulliganCurrent = mulliganLabels[0];
        for (int i = 0; i < mulliganRules.length; i++) {
            if (mulliganRules[i].equals(mulliganNow)) {
                mulliganCurrent = mulliganLabels[i];
            }
        }
        getChildren().add(choiceRow(NeoText.get("settings.mulligan"),
                mulliganLabels, mulliganCurrent,
                v -> {
                    String rule = NeoSettings.MULLIGAN_RULE_DEFAULT;
                    for (int i = 0; i < mulliganLabels.length; i++) {
                        if (mulliganLabels[i].equals(v)) {
                            rule = mulliganRules[i];
                        }
                    }
                    NeoSettings.set(NeoSettings.MULLIGAN_RULE, rule);
                    NeoSettings.save();
                }));

        // --- jugar por apuesta (la auditoría del motor B4) ---
        //
        // Apagado de fabrica A PROPOSITO: es el unico ajuste de esta pantalla
        // que PIERDE cartas del mazo de verdad si pierdes la partida, y algo
        // asi no se enciende sin que se pida (principio 6 de las notas de diseño
        // §10b). Solo afecta a los ~30 scripts viejos con habilidad de ante
        // (Arabian Nights, Antiquities, Legends, The Dark): para el resto de
        // las 33.696 cartas esto no cambia nada.
        final boolean anteNow = NeoSettings.getBool(NeoSettings.ANTE, false);
        final Region anteRarityRow = toggleRow(NeoText.get("settings.anteMatchRarity"),
                NeoSettings.getBool(NeoSettings.ANTE_MATCH_RARITY, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.ANTE_MATCH_RARITY, on);
                    NeoSettings.save();
                });
        final Region anteLandsRow = toggleRow(NeoText.get("settings.anteBasicLands"),
                NeoSettings.getBool(NeoSettings.ANTE_INCLUDE_BASIC_LANDS, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.ANTE_INCLUDE_BASIC_LANDS, on);
                    NeoSettings.save();
                });
        anteRarityRow.setVisible(anteNow);
        anteRarityRow.setManaged(anteNow);
        anteLandsRow.setVisible(anteNow);
        anteLandsRow.setManaged(anteNow);

        getChildren().add(toggleRow(NeoText.get("settings.ante"), anteNow,
                on -> {
                    NeoSettings.setBool(NeoSettings.ANTE, on);
                    NeoSettings.save();
                    anteRarityRow.setVisible(on);
                    anteRarityRow.setManaged(on);
                    anteLandsRow.setVisible(on);
                    anteLandsRow.setManaged(on);
                }));
        getChildren().add(anteRarityRow);
        getChildren().add(anteLandsRow);

        // --- preguntar antes de salir de la fase principal ---
        //
        // Sale de jugar: en una fase principal con disparos se da OK una vez
        // por disparo, y cuando se acaban el boton es el MISMO, asi que el OK
        // que ya ibas a dar te planta en el combate. Cambiar de fase no se
        // deshace. Quien vaya atento a cada OK lo pone en "Nunca" y vuelve a
        // como estaba.
        final String[] confirmLabels = {
                NeoText.get("settings.confirmPhase.never"),
                NeoText.get("settings.confirmPhase.combat"),
                NeoText.get("settings.confirmPhase.both")};
        final int confirmNow = NeoSettings.confirmPhaseMode();
        getChildren().add(choiceRow(NeoText.get("settings.confirmPhase"),
                confirmLabels, confirmLabels[confirmNow],
                v -> {
                    int mode = NeoSettings.CONFIRM_PHASE_DEFAULT;
                    for (int i = 0; i < confirmLabels.length; i++) {
                        if (confirmLabels[i].equals(v)) {
                            mode = i;
                        }
                    }
                    NeoSettings.setInt(NeoSettings.CONFIRM_PHASE, mode);
                    NeoSettings.save();
                }));

        // --- pararse cuando pasa algo importante ---
        //
        // Es el YieldController de Forge, que ya estaba escrito entero y solo
        // le faltaba el interruptor. Ver NeoGame.applySmartPass.
        //
        // La sensibilidad solo se ensenya con el pase encendido: una fila que
        // no hace nada porque otra de arriba esta apagada es de las cosas que
        // hacen dudar de si el ajuste funciona.
        final String[] smartLabels = {
                NeoText.get("settings.smartPass.low"),
                NeoText.get("settings.smartPass.normal"),
                NeoText.get("settings.smartPass.all")};
        final Region smartLevelRow = choiceRow(NeoText.get("settings.smartPass.level"),
                smartLabels, smartLabels[NeoSettings.smartPassLevel()],
                v -> {
                    int level = 0;
                    for (int i = 0; i < smartLabels.length; i++) {
                        if (smartLabels[i].equals(v)) {
                            level = i;
                        }
                    }
                    NeoSettings.setInt(NeoSettings.SMART_PASS_LEVEL, level);
                    NeoSettings.save();
                    forge.neo.match.NeoGame.refreshSmartPass();
                });
        final boolean smartNow = NeoSettings.getBool(NeoSettings.SMART_PASS, false);
        smartLevelRow.setVisible(smartNow);
        smartLevelRow.setManaged(smartNow);

        getChildren().add(toggleRow(NeoText.get("settings.smartPass"), smartNow,
                on -> {
                    NeoSettings.setBool(NeoSettings.SMART_PASS, on);
                    NeoSettings.save();
                    forge.neo.match.NeoGame.refreshSmartPass();
                    smartLevelRow.setVisible(on);
                    smartLevelRow.setManaged(on);
                }));
        getChildren().add(smartLevelRow);

        // --- ritmo del turno del rival ---
        //
        // Sale de jugar: la IA no tiene manos y resuelve su turno en
        // milisegundos, asi que te matan una criatura y no te enteras.
        // "Va al stack" es el punto medio y el que casi siempre se quiere: todo
        // lo que puede cambiar la partida pasa por el stack, y las habilidades
        // de mana -- el grueso del ruido de un turno -- no pasan por ahi.
        final String[] pauseLabels = {
                NeoText.get("settings.pause.never"),
                NeoText.get("settings.pause.affects"),
                NeoText.get("settings.pause.stack"),
                NeoText.get("settings.pause.always")};
        final int pauseNow = Math.max(0, Math.min(3,
                NeoSettings.getInt(NeoSettings.PAUSE_MODE, 2)));
        getChildren().add(choiceRow(NeoText.get("settings.pause"),
                pauseLabels, pauseLabels[pauseNow],
                v -> {
                    int mode = 0;
                    for (int i = 0; i < pauseLabels.length; i++) {
                        if (pauseLabels[i].equals(v)) {
                            mode = i;
                        }
                    }
                    host.setPauseMode(mode);
                    NeoSettings.setInt(NeoSettings.PAUSE_MODE, mode);
                    NeoSettings.save();
                }));

        // --- ritmo de la IA ---
        //
        // La IA no tiene manos: resuelve el turno entero en uno o dos segundos
        // y te enteras de lo que ha hecho mirando el marcador. Esto le pone
        // una pausa entre carta y carta. Lo que se elige es el MULTIPLICADOR,
        // no los milisegundos: "x2" se entiende, "1500 ms" no.
        final String[] speedLabels = {
                NeoText.get("settings.aiSpeed.none"),
                "x2", "x1", "x0,5"};
        final int[] speedValues = {0, 200, 100, 50};
        final int speedNow = NeoSettings.getInt(NeoSettings.AI_SPEED, 100);
        String speedCurrent = speedLabels[2];
        for (int i = 0; i < speedValues.length; i++) {
            if (speedValues[i] == speedNow) {
                speedCurrent = speedLabels[i];
            }
        }
        getChildren().add(choiceRow(NeoText.get("settings.aiSpeed"),
                speedLabels, speedCurrent,
                v -> {
                    int value = 100;
                    for (int i = 0; i < speedLabels.length; i++) {
                        if (speedLabels[i].equals(v)) {
                            value = speedValues[i];
                        }
                    }
                    host.setAiSpeed(value);
                    NeoSettings.setInt(NeoSettings.AI_SPEED, value);
                    NeoSettings.save();
                }));

        // --- dificultad de la IA (la auditoría del motor B4) ---
        //
        // Dos ajustes de Forge que hoy no leiamos: cuanto puede "hacer
        // trampa" al barajar su biblioteca (un truco de mana concreto,
        // CHEAT_WITH_MANA_ON_SHUFFLE — no es "mejorar la IA", es encender un
        // interruptor que Forge ya trae hecho) y cuanto tiempo se puede tomar
        // pensando el combate antes de que el motor le corte la busqueda.
        getChildren().add(toggleRow(NeoText.get("settings.aiCheatShuffle"),
                NeoSettings.getBool(NeoSettings.AI_CHEAT_SHUFFLE, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.AI_CHEAT_SHUFFLE, on);
                    NeoSettings.save();
                }));

        final String[] timeoutLabels = {"2 s", "5 s", "10 s", "20 s"};
        final int[] timeoutValues = {2, 5, 10, 20};
        final int timeoutNow = NeoSettings.getInt(NeoSettings.AI_TIMEOUT, NeoSettings.AI_TIMEOUT_DEFAULT);
        String timeoutCurrent = timeoutLabels[1];
        for (int i = 0; i < timeoutValues.length; i++) {
            if (timeoutValues[i] == timeoutNow) {
                timeoutCurrent = timeoutLabels[i];
            }
        }
        getChildren().add(choiceRow(NeoText.get("settings.aiTimeout"),
                timeoutLabels, timeoutCurrent,
                v -> {
                    int value = NeoSettings.AI_TIMEOUT_DEFAULT;
                    for (int i = 0; i < timeoutLabels.length; i++) {
                        if (timeoutLabels[i].equals(v)) {
                            value = timeoutValues[i];
                        }
                    }
                    NeoSettings.setInt(NeoSettings.AI_TIMEOUT, value);
                    NeoSettings.save();
                }));

        // --- el aviso de la IA ---
        //
        // Forge lo suelta antes de CADA partida y hay que cerrarlo a mano. La
        // primera vez informa; a partir de ahi estorba.
        getChildren().add(toggleRow(NeoText.get("settings.hideAiWarning"),
                NeoSettings.getBool(NeoSettings.HIDE_AI_WARNING, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.HIDE_AI_WARNING, on);
                    NeoSettings.save();
                }));

        // --- vender solas las repetidas (aventura) ---
        //
        // Va encendido de fabrica porque el bolsillo de la aventura se llena de
        // Sol Rings que no puedes poner en ningun sitio, pero se puede apagar:
        // vender es de lo poco que no se deshace, y el que quiera guardarselas
        // manda. Lo vendido vuelve al mostrador y el botin dice cuanto.
        getChildren().add(toggleRow(NeoText.get("settings.autoSell"),
                NeoSettings.getBool(forge.neo.quest.NeoQuestRewards.AUTO_SELL, true),
                on -> {
                    NeoSettings.setBool(forge.neo.quest.NeoQuestRewards.AUTO_SELL, on);
                    NeoSettings.save();
                }));

        // --- rating de pick en el draft ---
        //
        // Apagado de fabrica: para quien ya sabe draftear es la respuesta
        // puesta antes de pensarla, y le estropea el draft (NeoSettings).
        getChildren().add(toggleRow(NeoText.get("settings.draftRanking"),
                NeoSettings.showDraftRanking(),
                on -> {
                    NeoSettings.setBool(NeoSettings.DRAFT_RANKING, on);
                    NeoSettings.save();
                    host.setDraftRankingVisible(on);
                }));

        getChildren().add(section(NeoText.get("settings.sound")));

        // --- volumen ---
        getChildren().add(sliderRow(NeoText.get("settings.sfx"),
                0, 100, NeoSettings.getInt(NeoSettings.SOUND_VOLUME, NeoSettings.SOUND_VOLUME_DEFAULT), 5,
                v -> {
                    NeoSettings.setInt(NeoSettings.SOUND_VOLUME, (int) Math.round(v));
                    NeoSettings.save();
                    NeoSettings.applyAudioToEngine();
                },
                v -> String.format(java.util.Locale.ROOT, "%.0f", v)));

        getChildren().add(sliderRow(NeoText.get("settings.music"),
                0, 100, NeoSettings.getInt(NeoSettings.MUSIC_VOLUME, NeoSettings.MUSIC_VOLUME_DEFAULT), 5,
                v -> {
                    NeoSettings.setInt(NeoSettings.MUSIC_VOLUME, (int) Math.round(v));
                    NeoSettings.save();
                    NeoSettings.applyAudioToEngine();
                },
                v -> String.format(java.util.Locale.ROOT, "%.0f", v)));

        final Button close = new Button(NeoText.get("common.close"));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> onClose.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        footer.getChildren().addAll(gap, close);
        footer.setPadding(new Insets(12, 0, 0, 0));

        // Se reparte lo montado: el titulo arriba, el pie abajo y TODO lo demas
        // dentro del visor. Se hace al final y no fila a fila para no tener que
        // acordarse en cada ajuste nuevo de meterlo en el sitio correcto.
        final List<javafx.scene.Node> middle =
                new ArrayList<>(getChildren().subList(1, getChildren().size()));
        content.getChildren().setAll(middle);
        content.setSpacing(getSpacing());

        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Lo que no se encoge: si el VBox tuviera que recortar por arriba o por
        // abajo, se llevaria por delante justo lo que hay que dejar quieto.
        title.setMinHeight(Region.USE_PREF_SIZE);
        footer.setMinHeight(Region.USE_PREF_SIZE);

        getChildren().setAll(title, scroll, footer);
    }

    /**
     * Cuanto puede medir el visor sin salirse de la ventana.
     *
     * <p>Se mide en {@code layoutChildren} y no en el constructor: ahi el alto
     * de la escena todavia vale cero, y dimensionar contra cero da un panel
     * diminuto. La comparacion antes de asignar es lo que corta el bucle —
     * cambiar el alto preferido pide otra pasada de layout.
     *
     * <p>Y se pide {@code prefViewportHeight}, no {@code maxHeight}: un
     * {@code ScrollPane} se dimensiona por su alto <b>preferido</b> y el maximo
     * no le hace ni caso.
     */
    @Override
    protected void layoutChildren() {
        final javafx.scene.Scene sc = getScene();
        if (sc != null && sc.getHeight() > 0) {
            final double chrome = title.prefHeight(-1) + footer.prefHeight(-1)
                    + getPadding().getTop() + getPadding().getBottom()
                    + getSpacing() * 2 + 8;
            final double room = sc.getHeight() * 0.90 - chrome;
            final double wanted = content.prefHeight(content.getWidth() > 0
                    ? content.getWidth() : content.prefWidth(-1));
            final double h = Math.max(160, Math.min(wanted + 2, room));
            if (Math.abs(h - scroll.getPrefViewportHeight()) > 1) {
                scroll.setPrefViewportHeight(h);
            }
        }
        super.layoutChildren();
    }

    // ---------------------------------------------------------------

    /** La etiqueta que le toca al idioma guardado, para preseleccionarla. */
    private static String labelFor(final java.util.List<forge.neo.NeoLanguage.Option> langs,
                                   final String id) {
        for (final forge.neo.NeoLanguage.Option o : langs) {
            if (o.getId().equals(id)) {
                return o.getLabel();
            }
        }
        return langs.isEmpty() ? "" : langs.get(0).getLabel();
    }

    /**
     * Que dice la nota de debajo.
     *
     * <p>Dos cosas, y las dos importan antes de elegir: si ese idioma trae los
     * nombres de las cartas traducidos (no todos), y que hay que reiniciar.
     */
    private static String noteFor(final java.util.List<forge.neo.NeoLanguage.Option> langs,
                                  final String id, final boolean justChanged) {
        String cards = "";
        for (final forge.neo.NeoLanguage.Option o : langs) {
            if (o.getId().equals(id)) {
                cards = o.hasCardNames()
                        ? NeoText.get("settings.language.cards")
                        : NeoText.get("settings.language.noCards");
                break;
            }
        }
        return justChanged ? cards + "  " + NeoText.get("settings.language.restart") : cards;
    }

    private static Label section(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("settings-section");
        return l;
    }

    /** Etiqueta a la izquierda, control a la derecha, ancho fijo. */
    private static HBox row(final String caption, final Region control) {
        final Label label = new Label(caption);
        label.getStyleClass().add("settings-label");
        label.setMinWidth(190);
        final HBox box = new HBox(14, label, control);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 0, 4, 0));
        return box;
    }

    private static HBox choiceRow(final String caption, final String[] values,
                                  final String current, final Consumer<String> onPick) {
        final javafx.scene.layout.FlowPane buttons = new javafx.scene.layout.FlowPane(4, 4);
        final List<Button> all = new ArrayList<>();
        for (final String value : values) {
            final Button b = new Button(value);
            b.getStyleClass().add("segment");
            // Cuando la fila no cabe, JavaFX encoge los botones por debajo de
            // su texto y los corta con puntos suspensivos. Con diez idiomas
            // salian botones que ponian "..." y nada mas.
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, value.equals(current));
            b.setOnAction(e -> {
                for (final Button other : all) {
                    other.pseudoClassStateChanged(SELECTED, other == b);
                }
                onPick.accept(value);
            });
            all.add(b);
            buttons.getChildren().add(b);
        }
        return row(caption, buttons);
    }

    /**
     * Deslizador con su valor escrito al lado.
     *
     * <p>El numero importa: sin el, poner el volumen "igual que ayer" es
     * imposible.
     */
    private static HBox sliderRow(final String caption, final double min, final double max,
                                  final double value, final double step,
                                  final Consumer<Double> onChange,
                                  final java.util.function.Function<Double, String> format) {
        final Slider slider = new Slider(min, max, clamp(value, min, max));
        slider.setBlockIncrement(step);
        slider.setPrefWidth(240);
        slider.getStyleClass().add("settings-slider");

        final Label readout = new Label(format.apply(slider.getValue()));
        readout.getStyleClass().add("settings-value");
        readout.setMinWidth(52);

        slider.valueProperty().addListener((o, was, is) -> {
            final double v = Math.round(is.doubleValue() / step) * step;
            readout.setText(format.apply(v));
            onChange.accept(v);
        });

        final HBox control = new HBox(10, slider, readout);
        control.setAlignment(Pos.CENTER_LEFT);
        return row(caption, control);
    }

    private static HBox toggleRow(final String caption, final boolean on,
                                  final Consumer<Boolean> onChange) {
        final String yes = NeoText.get("common.yes");
        final String no = NeoText.get("common.no");
        return choiceRow(caption, new String[] {yes, no}, on ? yes : no,
                v -> onChange.accept(yes.equals(v)));
    }

    private static double clamp(final double v, final double lo, final double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");
}
