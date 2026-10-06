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

        /**
         * Si hay una partida en marcha ahora mismo.
         *
         * <p>Es lo que decide si se avisa de que un ajuste no va a cambiar
         * nada de lo que estas viendo. Fuera de partida no hace falta decir
         * nada: lo que elijas vale para la que empieces despues, que es lo que
         * cualquiera espera.
         */
        default boolean isInMatch() {
            return false;
        }

        /**
         * Repintar la mesa ya.
         *
         * <p>Para los ajustes que la mesa relee en cada repintado: sin esto se
         * aplican igual, pero <b>cuando el motor mande el siguiente aviso</b>,
         * que si es tu turno y no ha pasado nada puede tardar. Y ese retraso
         * se lee como que el ajuste no funciona.
         */
        default void refreshTable() {
        }

        /**
         * Reabrir el juego para aplicar una importacion de datos ya preparada
         * (NeoBackup.stage). {@code false} si no se puede reabrir solo
         * (arbol de desarrollo): entonces se le pide al jugador que lo haga.
         */
        default boolean restartToImport() {
            return false;
        }
    }

    /** El titulo, que se queda arriba pase lo que pase. */
    private final Label title = new Label(NeoText.get("settings.title"));

    /** Todo lo que rueda. */
    private final VBox content = new VBox(6);

    private final javafx.scene.control.ScrollPane scroll =
            new javafx.scene.control.ScrollPane(content);

    /** El pie con el boton de cerrar, que se queda abajo pase lo que pase. */
    private final HBox footer = new HBox();

    /**
     * Si esto se abrio con una partida en marcha.
     *
     * <p>Se pregunta UNA vez, al construir: el panel se monta entero de golpe
     * y no sobrevive a la partida.
     */
    private final boolean inMatch;

    public SettingsPanel(final Host host, final Runnable onClose) {
        this.inMatch = host.isInMatch();
        getStyleClass().addAll("dialog", "settings");
        setSpacing(6);
        setPadding(new Insets(22, 26, 18, 26));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        title.getStyleClass().add("dialog-title");

        getChildren().add(title);
        tab(Tab.GENERAL);
        getChildren().add(section(NeoText.get("settings.language")));

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
        note.setMaxWidth(UiScale.px(560));
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

        tab(Tab.DISPLAY);

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

        // --- cuanto crece la carta al pasar el raton ---
        //
        // Pedido por un jugador el 22-09-2026: "an option to enlarge the card a
        // bit on mouse over, not as much as with right click". El 8 % de
        // siempre se queda corto en pantallas grandes y sobra en las pequenyas,
        // asi que se pregunta en vez de subirlo para todos. El tope (150 %) es
        // a proposito: mas alla el hover se come al clic derecho, y entonces
        // son dos gestos para lo mismo.
        final int[] hoverPcts = {100, 108, 120, 135, 150};
        final String[] hoverLabels = new String[hoverPcts.length];
        for (int i = 0; i < hoverPcts.length; i++) {
            hoverLabels[i] = hoverPcts[i] == 100
                    ? NeoText.get("settings.hoverZoom.off") : hoverPcts[i] + "%";
        }
        final int hoverNow = (int) Math.round(NeoSettings.hoverZoom() * 100);
        getChildren().add(choiceRow(NeoText.get("settings.hoverZoom"), hoverLabels,
                hoverNow == 100 ? NeoText.get("settings.hoverZoom.off") : hoverNow + "%",
                v -> {
                    final int pct = v.endsWith("%")
                            ? Integer.parseInt(v.replace("%", "")) : 100;
                    NeoSettings.setInt(NeoSettings.HOVER_ZOOM, pct);
                    NeoSettings.save();
                }));

        // --- la mano en abanico ---
        //
        // Encendido de fabrica. Apagarlo pone las cartas rectas: pierdes la
        // lectura de abanico y ganas nitidez, porque una carta girada la pinta
        // JavaFX con los bordes suavizados y en 1080p eso se ve sucio.
        getChildren().add(toggleRow(NeoText.get("settings.handFan"),
                NeoSettings.handFan(),
                on -> {
                    HandFan.setFanned(on);
                    NeoSettings.setBool(NeoSettings.HAND_FAN, on);
                    NeoSettings.save();
                    // Los ajustes se abren con la partida detras: sin esto el
                    // cambio no se veria hasta el siguiente aviso del motor.
                    if (getScene() != null && getScene().getRoot() != null) {
                        HandFan.relayoutAllIn(getScene().getRoot());
                    }
                }));

        // --- la mano ordenada ---
        //
        // "Order hand by CMC and color" de Forge (itch.io, 28-09-2026).
        getChildren().add(toggleRow(NeoText.get("settings.orderHand"),
                NeoSettings.orderHand(),
                on -> {
                    NeoSettings.setBool(NeoSettings.ORDER_HAND, on);
                    NeoSettings.save();
                    if (getScene() != null && getScene().getRoot() != null) {
                        TableScreen.reorderHandsIn(getScene().getRoot());
                    }
                }));

        // --- la mano tactil de Android ---
        //
        // Pedido en itch.io el 27-09-2026 por quien juega en una Surface: en la
        // pantalla tactil no hay "pasar el raton", asi que tocar una carta de la
        // mano la jugaba sin poder leerla. Apagado de fabrica: cambia lo que
        // hace tocar la mano. Se lee en cada gesto, sin reiniciar nada.
        final String[] touchValues = {NeoSettings.TOUCH_HAND_OFF,
                NeoSettings.TOUCH_HAND_FINGER, NeoSettings.TOUCH_HAND_ALWAYS};
        final String[] touchLabels = {NeoText.get("settings.touchHand.off"),
                NeoText.get("settings.touchHand.finger"),
                NeoText.get("settings.touchHand.always")};
        final String touchNow = NeoSettings.touchHand();
        String touchCurrent = touchLabels[0];
        for (int i = 0; i < touchValues.length; i++) {
            if (touchValues[i].equals(touchNow)) {
                touchCurrent = touchLabels[i];
            }
        }
        getChildren().add(choiceRow(NeoText.get("settings.touchHand"), touchLabels, touchCurrent,
                v -> {
                    for (int i = 0; i < touchLabels.length; i++) {
                        if (touchLabels[i].equals(v)) {
                            NeoSettings.set(NeoSettings.TOUCH_HAND, touchValues[i]);
                        }
                    }
                    NeoSettings.save();
                }));
        final Label touchNote = new Label(NeoText.get("settings.touchHand.note"));
        touchNote.getStyleClass().add("settings-note");
        touchNote.setWrapText(true);
        touchNote.setMaxWidth(UiScale.px(560));
        touchNote.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().add(touchNote);

        // --- palabras clave en las cartas de la mesa ---
        //
        // Pedido desde Reddit el 26-09-2026: volar, toque mortal, arrollar...
        // en chapitas sobre la carta, sin tener que ampliarla. Encendido de
        // fabrica; apagable para quien prefiera el arte limpio.
        getChildren().add(toggleRow(NeoText.get("settings.keywordBadges"),
                forge.neo.card.CardNode.areKeywordBadgesEnabled(),
                on -> {
                    forge.neo.card.CardNode.setKeywordBadgesEnabled(on);
                    NeoSettings.setBool(NeoSettings.KEYWORD_BADGES, on);
                    NeoSettings.save();
                    if (getScene() != null && getScene().getRoot() != null) {
                        forge.neo.card.CardNode.refreshAllIn(getScene().getRoot());
                    }
                }));

        // --- brillo de las foil (la auditoría del motor, apartado D5) ---
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

        // --- cartas nitidas ---
        //
        // Reducir la imagen UNA vez y con un buen filtro, en vez de dejar que
        // JavaFX la encoja al pintar. Encendido de fabrica; apagable por si en
        // algun equipo se nota. Ver forge.neo.card.Resample.
        getChildren().add(toggleRow(NeoText.get("settings.sharpArt"),
                forge.neo.card.CardImages.isSharp(),
                on -> {
                    NeoSettings.setBool(NeoSettings.SHARP_ART, on);
                    NeoSettings.save();
                    forge.neo.card.CardImages.setSharp(on);
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

        // --- politica de arte (la auditoría del motor, apartado B4) ---
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
        final boolean artCoreNow = NeoSettings.getBool(NeoSettings.CARD_ART_CORE_ONLY,
                NeoSettings.CARD_ART_CORE_ONLY_DEFAULT);
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

        tab(Tab.GAME);

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

        // --- regla de mulligan (la auditoría del motor, apartado B4) ---
        //
        // El motor trae las cinco (Original, Paris, Vancouver, London,
        // Houston — MulliganDefs.MulliganRule) pero el jugador nunca podia
        // elegir: se aplicaba siempre la de fabrica (London) sin ni un
        // ajuste que lo dijera. Se aplica en NeoGame.applyEnginePrefs.
        final String[] mulliganRules = {"London", "Vancouver", "Paris", "Original", "Houston",
                forge.neo.match.FriendlyMulligan.RULE};
        final String[] mulliganLabels = {
                NeoText.get("settings.mulligan.london"),
                NeoText.get("settings.mulligan.vancouver"),
                NeoText.get("settings.mulligan.paris"),
                NeoText.get("settings.mulligan.original"),
                NeoText.get("settings.mulligan.houston"),
                NeoText.get("settings.mulligan.friendly")};
        final String mulliganNow =
                NeoSettings.get(NeoSettings.MULLIGAN_RULE, NeoSettings.MULLIGAN_RULE_DEFAULT);
        String mulliganCurrent = mulliganLabels[0];
        for (int i = 0; i < mulliganRules.length; i++) {
            if (mulliganRules[i].equals(mulliganNow)) {
                mulliganCurrent = mulliganLabels[i];
            }
        }
        // La aplica NeoGame.applyEnginePrefs al MONTAR la partida, asi que
        // cambiarla a mitad no toca la que estas jugando: se avisa.
        final Label mulliganNote = nextGameNote();
        getChildren().add(choiceRow(NeoText.get("settings.mulligan"),
                mulliganLabels, mulliganCurrent,
                nextGame(v -> {
                    String rule = NeoSettings.MULLIGAN_RULE_DEFAULT;
                    for (int i = 0; i < mulliganLabels.length; i++) {
                        if (mulliganLabels[i].equals(v)) {
                            rule = mulliganRules[i];
                        }
                    }
                    NeoSettings.set(NeoSettings.MULLIGAN_RULE, rule);
                    NeoSettings.save();
                }, mulliganNote)));
        getChildren().add(mulliganNote);

        // --- jugar por apuesta (la auditoría del motor, apartado B4) ---
        //
        // Apagado de fabrica A PROPOSITO: es el unico ajuste de esta pantalla
        // que PIERDE cartas del mazo de verdad si pierdes la partida, y algo
        // asi no se enciende sin que se pida (principio 6 de las notas de diseño
        // §10b). Solo afecta a los ~30 scripts viejos con habilidad de ante
        // (Arabian Nights, Antiquities, Legends, The Dark): para el resto de
        // las 33.696 cartas esto no cambia nada.
        // Las tres van a las GameRules, que se montan al empezar la partida.
        final Label anteNote = nextGameNote();
        final boolean anteNow = NeoSettings.getBool(NeoSettings.ANTE, false);
        final Region anteRarityRow = toggleRow(NeoText.get("settings.anteMatchRarity"),
                NeoSettings.getBool(NeoSettings.ANTE_MATCH_RARITY, false),
                nextGame(on -> {
                    NeoSettings.setBool(NeoSettings.ANTE_MATCH_RARITY, on);
                    NeoSettings.save();
                }, anteNote));
        final Region anteLandsRow = toggleRow(NeoText.get("settings.anteBasicLands"),
                NeoSettings.getBool(NeoSettings.ANTE_INCLUDE_BASIC_LANDS, false),
                nextGame(on -> {
                    NeoSettings.setBool(NeoSettings.ANTE_INCLUDE_BASIC_LANDS, on);
                    NeoSettings.save();
                }, anteNote));
        anteRarityRow.setVisible(anteNow);
        anteRarityRow.setManaged(anteNow);
        anteLandsRow.setVisible(anteNow);
        anteLandsRow.setManaged(anteNow);

        getChildren().add(toggleRow(NeoText.get("settings.ante"), anteNow,
                nextGame(on -> {
                    NeoSettings.setBool(NeoSettings.ANTE, on);
                    NeoSettings.save();
                    anteRarityRow.setVisible(on);
                    anteRarityRow.setManaged(on);
                    anteLandsRow.setVisible(on);
                    anteLandsRow.setManaged(on);
                }, anteNote)));
        getChildren().add(anteRarityRow);
        getChildren().add(anteLandsRow);
        // La nota, debajo de las tres: hablan de lo mismo, y una por fila
        // seria decir lo mismo tres veces seguidas.
        getChildren().add(anteNote);

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

        // --- preguntar antes de no bloquear con nada (BlockGuard) ---
        //
        // Lo mismo que lo de arriba, en el paso de bloqueos: tras una rafaga de
        // OK a disparos, el siguiente deja pasar el combate sin bloquear.
        // Apagado de fabrica: lo pidio asi quien lo propuso.
        getChildren().add(toggleRow(NeoText.get("settings.confirmBlock"),
                NeoSettings.confirmNoBlock(), on -> {
                    NeoSettings.setBool(NeoSettings.CONFIRM_NO_BLOCK, on);
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
        final boolean autoPassNow = NeoSettings.getBool(NeoSettings.AUTO_PASS, true);
        final boolean[] smartState = {NeoSettings.getBool(NeoSettings.SMART_PASS, false)};
        smartLevelRow.setVisible(autoPassNow && smartState[0]);
        smartLevelRow.setManaged(autoPassNow && smartState[0]);

        final Region smartRow = toggleRow(NeoText.get("settings.smartPass"), smartState[0],
                on -> {
                    smartState[0] = on;
                    NeoSettings.setBool(NeoSettings.SMART_PASS, on);
                    NeoSettings.save();
                    forge.neo.match.NeoGame.refreshSmartPass();
                    smartLevelRow.setVisible(on);
                    smartLevelRow.setManaged(on);
                });
        smartRow.setVisible(autoPassNow);
        smartRow.setManaged(autoPassNow);

        // --- pasar la prioridad sola ---
        //
        // Pedido en r/forgeMTG: "let me do it myself". Encendido es lo de
        // siempre. Las dos filas de "pararse" solo tienen sentido con el pase
        // encendido (son interrupciones DEL pase), asi que se esconden con el.
        getChildren().add(toggleRow(NeoText.get("settings.autoPass"), autoPassNow,
                on -> {
                    NeoSettings.setBool(NeoSettings.AUTO_PASS, on);
                    NeoSettings.save();
                    forge.neo.match.NeoGame.refreshAutoPass();
                    smartRow.setVisible(on);
                    smartRow.setManaged(on);
                    smartLevelRow.setVisible(on && smartState[0]);
                    smartLevelRow.setManaged(on && smartState[0]);
                }));
        getChildren().add(smartRow);
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
        // Donde sale: en la esquina sin parar (de fabrica) o en el centro
        // esperando a que pulses. Solo tiene sentido si se para en algo.
        final String[] newsLabels = {
                NeoText.get("settings.news.corner"),
                NeoText.get("settings.news.center")};
        final Region newsRow = choiceRow(NeoText.get("settings.news"),
                newsLabels, newsLabels[Math.max(0, Math.min(1,
                        NeoSettings.getInt(NeoSettings.NEWS_STYLE, 0)))],
                v -> {
                    NeoSettings.setInt(NeoSettings.NEWS_STYLE, newsLabels[1].equals(v) ? 1 : 0);
                    NeoSettings.save();
                });
        newsRow.setVisible(pauseNow > 0);
        newsRow.setManaged(pauseNow > 0);
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
                    newsRow.setVisible(mode > 0);
                    newsRow.setManaged(mode > 0);
                }));
        getChildren().add(newsRow);

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

        // --- parar la partida mientras lees ---
        //
        // Lo otro que faltaba del mismo problema: el ritmo de arriba te da
        // tres segundos por carta, pero leerse una carta que no conoces son
        // mas. Ampliar una carta (o abrir este menu) para el reloj hasta que
        // la cierras. Encendido de fabrica: solo pasa cuando eres tu quien
        // decide mirar, y quien lo necesita es justo quien no va a bajar hasta
        // aqui. En red no hace nada (parar la mesa congelaria a los demas).
        getChildren().add(toggleRow(NeoText.get("settings.pauseWhileReading"),
                NeoSettings.pauseWhileReading(),
                on -> {
                    NeoSettings.setBool(NeoSettings.PAUSE_WHILE_READING, on);
                    NeoSettings.save();
                }));

        // --- dificultad de la IA (la auditoría del motor, apartado B4) ---
        //
        // Dos ajustes de Forge que hoy no leiamos: cuanto puede "hacer
        // trampa" al barajar su biblioteca (un truco de mana concreto,
        // CHEAT_WITH_MANA_ON_SHUFFLE — no es "mejorar la IA", es encender un
        // interruptor que Forge ya trae hecho) y cuanto tiempo se puede tomar
        // pensando el combate antes de que el motor le corte la busqueda.
        // Va a GameRules.setAllowCheatShuffle al montar la partida.
        final Label aiNote = nextGameNote();
        getChildren().add(toggleRow(NeoText.get("settings.aiCheatShuffle"),
                NeoSettings.getBool(NeoSettings.AI_CHEAT_SHUFFLE, false),
                nextGame(on -> {
                    NeoSettings.setBool(NeoSettings.AI_CHEAT_SHUFFLE, on);
                    NeoSettings.save();
                }, aiNote)));

        final String[] timeoutLabels = {"2 s", "5 s", "10 s", "20 s"};
        final int[] timeoutValues = {2, 5, 10, 20};
        final int timeoutNow = NeoSettings.getInt(NeoSettings.AI_TIMEOUT, NeoSettings.AI_TIMEOUT_DEFAULT);
        String timeoutCurrent = timeoutLabels[1];
        for (int i = 0; i < timeoutValues.length; i++) {
            if (timeoutValues[i] == timeoutNow) {
                timeoutCurrent = timeoutLabels[i];
            }
        }
        // HostedMatch.startGame lee MATCH_AI_TIMEOUT al empezar cada partida.
        getChildren().add(choiceRow(NeoText.get("settings.aiTimeout"),
                timeoutLabels, timeoutCurrent,
                nextGame(v -> {
                    int value = NeoSettings.AI_TIMEOUT_DEFAULT;
                    for (int i = 0; i < timeoutLabels.length; i++) {
                        if (timeoutLabels[i].equals(v)) {
                            value = timeoutValues[i];
                        }
                    }
                    NeoSettings.setInt(NeoSettings.AI_TIMEOUT, value);
                    NeoSettings.save();
                }, aiNote)));
        getChildren().add(aiNote);

        tab(Tab.TABLE);

        // --- todas las mesas a la vez ---
        //
        // Solo cambia algo a mas de dos jugadores: con un rival ya se ve su
        // mesa entera. Apagado de fabrica; ver NeoSettings.ALL_BOARDS, que
        // trae los tamanyos medidos y por que se elige en vez de imponerse.
        getChildren().add(toggleRow(NeoText.get("settings.allBoards"),
                NeoSettings.getBool(NeoSettings.ALL_BOARDS, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.ALL_BOARDS, on);
                    NeoSettings.save();
                    // Este NO necesita partida nueva: TableBinder lo relee en
                    // cada repintado. Lo que le faltaba era el empujon -- sin
                    // el, el cambio espera al siguiente aviso del motor, que
                    // en tu turno y sin nada pasando puede tardar. De ahi
                    // salio el reporte de "no se aplica hasta que sales".
                    host.refreshTable();
                }));

        // --- las cartas en el stack ---
        //
        // Encendido de fabrica, al reves que el resto de lo nuevo: no es una
        // idea nuestra, es el arreglo de algo que un jugador dijo que le
        // costaba usar ("el stack es donde se gana y se pierde una partida").
        // El ajuste esta para volver a la lista compacta, que ocupa un tercio
        // y sigue siendo la buena con quince disparos encadenados.
        //
        // No lleva nota de "partida nueva": TableScreen lo relee en cada
        // repintado del stack, asi que se nota en cuanto haya algo dentro. Y
        // se empuja la mesa por lo mismo que "ver todas las mesas": si es tu
        // turno y no pasa nada, el siguiente aviso del motor puede tardar y
        // ese retraso se lee como que el ajuste no funciona.
        getChildren().add(toggleRow(NeoText.get("settings.stackCards"),
                NeoSettings.stackCards(),
                on -> {
                    NeoSettings.setBool(NeoSettings.STACK_CARDS, on);
                    NeoSettings.save();
                    host.refreshTable();
                }));

        // --- sitio para la mesa: columna plegada y barras compactas ---
        //
        // Pedido en itch.io el 04-10-2026 jugando a cuatro. Apagados de
        // fabrica. La mesa los lee en cada reparto, asi que se notan en el acto.
        getChildren().add(toggleRow(NeoText.get("settings.sideFolded"),
                NeoSettings.getBool(NeoSettings.SIDE_FOLDED, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.SIDE_FOLDED, on);
                    NeoSettings.save();
                    host.refreshTable();
                }));
        getChildren().add(toggleRow(NeoText.get("settings.slimBars"),
                NeoSettings.getBool(NeoSettings.SLIM_BARS, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.SLIM_BARS, on);
                    NeoSettings.save();
                    host.refreshTable();
                }));

        // --- la carta grande del stack en mitad de la mesa ---
        //
        // Pedido en itch.io el 04-10-2026: moverla, agrandarla o quitarla. Lo
        // primero y lo segundo se hacen en la propia carta (arrastrar, rueda,
        // esquina); aqui se quita y se devuelve a su sitio. Ver PromptBanner.
        getChildren().add(toggleRow(NeoText.get("settings.stackBanner"),
                NeoSettings.stackBanner(),
                on -> {
                    NeoSettings.setBool(NeoSettings.STACK_BANNER, on);
                    NeoSettings.save();
                    PromptBanner.refreshLive();
                    host.refreshTable();
                }));
        getChildren().add(toggleRow(NeoText.get("settings.stackBanner.edit"),
                NeoSettings.stackBannerEditable(),
                on -> {
                    NeoSettings.setBool(NeoSettings.STACK_BANNER_EDIT, on);
                    NeoSettings.save();
                    PromptBanner.refreshLive();
                }));
        // Como se mueve y se agranda: se hace en la propia carta, y un gesto que
        // nadie sabe que existe no le sirve a nadie. Va pegada a SU interruptor:
        // debajo del Reset parecia hablar del boton (itch.io, 06-10-2026).
        final Label bannerNote = new Label(NeoText.get("settings.stackBanner.note"));
        bannerNote.getStyleClass().add("settings-note");
        bannerNote.setWrapText(true);
        bannerNote.setMaxWidth(UiScale.px(560));
        bannerNote.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().add(bannerNote);
        final Button bannerReset = new Button(NeoText.get("settings.stackBanner.reset"));
        bannerReset.getStyleClass().add("segment");
        bannerReset.setMinWidth(Region.USE_PREF_SIZE);
        bannerReset.setOnAction(e -> {
            PromptBanner.resetPlacement();
            host.refreshTable();
        });
        getChildren().add(row(NeoText.get("settings.stackBanner.place"), bannerReset));

        // --- apilar las cartas iguales, no solo las fichas ---
        //
        // Como Forge. Pedido en itch.io el 29-09-2026 con treinta Rat Colony
        // en una fila con flecha. Encendido de fabrica. Ver BattlefieldPane.
        getChildren().add(toggleRow(NeoText.get("settings.stackSame"),
                NeoSettings.stackSameCards(),
                on -> {
                    NeoSettings.setBool(NeoSettings.STACK_SAME, on);
                    NeoSettings.save();
                    host.refreshTable();
                }));

        // --- equipos y auras: abanico o apilados ---
        //
        // Pedido en itch.io el 20-09-2026: con la mesa llena, lo enganchado
        // queda en rendijas de pocos pixeles y cuesta clicarlo. Apilado no
        // ocupa nada fuera de la carta y se abre por su contador, en grande.
        // Apagado de fabrica: apilado se pierde de un vistazo QUE criatura
        // esta encantada, y eso importa. Se relee al montar la mesa.
        getChildren().add(toggleRow(NeoText.get("settings.attachStacked"),
                NeoSettings.attachmentsStacked(),
                on -> {
                    NeoSettings.setBool(NeoSettings.ATTACHMENTS_STACKED, on);
                    NeoSettings.save();
                    host.refreshTable();
                }));

        // --- el panel de detalle al pasar el raton ---
        //
        // Apagado de fabrica. Encendido ocupa SOLO lo que sobra debajo del
        // stack, asi que no le quita sitio a nada; ver NeoSettings.HOVER_DETAIL.
        getChildren().add(toggleRow(NeoText.get("settings.hoverDetail"),
                NeoSettings.getBool(NeoSettings.HOVER_DETAIL, false),
                on -> {
                    NeoSettings.setBool(NeoSettings.HOVER_DETAIL, on);
                    NeoSettings.save();
                }));

        tab(Tab.GAME);
        getChildren().add(section(NeoText.get("settings.rivalsSection")));

        // --- el aviso de la IA ---
        //
        // Forge lo suelta antes de CADA partida y hay que cerrarlo a mano. La
        // primera vez informa; a partir de ahi estorba.
        // Forge lo suelta ANTES de cada partida, o sea que encenderlo a mitad
        // no quita ninguno de esta.
        final Label warnNote = nextGameNote();
        getChildren().add(toggleRow(NeoText.get("settings.hideAiWarning"),
                NeoSettings.getBool(NeoSettings.HIDE_AI_WARNING, false),
                nextGame(on -> {
                    NeoSettings.setBool(NeoSettings.HIDE_AI_WARNING, on);
                    NeoSettings.save();
                }, warnNote)));
        getChildren().add(warnNote);

        // --- rivales mas modernos ---
        //
        // No quita ni un mazo: solo cambia el orden en que salen de la bolsa
        // al sortear rival. Encendido de fabrica porque el reparto de fabrica
        // esta muy escorado (en Estandar, 392 de 505 preconstruidos son de
        // antes de 2018), pero se puede apagar: quien quiera azar plano manda.
        // El rival ya esta sorteado cuando empieza la partida: esto decide con
        // que mazo sale el SIGUIENTE.
        final Label rivalsNote = nextGameNote();
        getChildren().add(toggleRow(NeoText.get("settings.modernRivals"),
                NeoSettings.getBool(NeoSettings.MODERN_RIVALS, true),
                nextGame(on -> {
                    NeoSettings.setBool(NeoSettings.MODERN_RIVALS, on);
                    NeoSettings.save();
                }, rivalsNote)));
        getChildren().add(rivalsNote);

        tab(Tab.MODES);
        getChildren().add(section(NeoText.get("settings.modesSection")));

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

        // --- sobres solo del mundo en que estas (Quest) ---
        //
        // Apagado de fabrica: cambia la tienda de quien ya juegue en un mundo.
        getChildren().add(toggleRow(NeoText.get("settings.questWorldShop"),
                NeoSettings.getBool(forge.neo.quest.NeoQuest.WORLD_SHOP, false),
                on -> {
                    NeoSettings.setBool(forge.neo.quest.NeoQuest.WORLD_SHOP, on);
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

        // --- la Aventura (el Adventure de Forge) ---
        //
        // Solo lo que es NUESTRO de ese modo. En Mac no hay Aventura.
        if (!forge.neo.platform.NeoOs.MAC) {
            getChildren().add(section(NeoText.get("settings.adventure")));
            getChildren().add(toggleRow(NeoText.get("settings.adventureStarter"),
                    NeoSettings.adventureStarter(),
                    on -> {
                        NeoSettings.setBool(NeoSettings.ADVENTURE_STARTER, on);
                        NeoSettings.save();
                    }));
        }

        // --- Discord ---
        //
        // Pedido en itch.io el 22-09-2026. El interruptor es obligatorio y no
        // un detalle: esto lo ven TODOS los amigos de quien juega, y quien no
        // lo quiera tiene que poder apagarlo sin buscar. Se aplica en el acto
        // (no "en la proxima partida"): apagarlo sin que desaparezca de Discord
        // hasta reiniciar seria justo lo contrario de lo que pide quien lo
        // apaga. Ver forge.neo.discord.DiscordRich.
        // --- partida en red: el tiempo de AFK ---
        //
        // Pedido en itch.io el 29-09-2026: "Customizable AFK timeout for
        // multiplayer ... fixed at 5 minutes". No es fijo: es la preferencia de
        // Forge NET_AFK_TIMEOUT (minutos, 0 = nunca), que el anfitrion lee al
        // esperar la prioridad de cada jugador (FServerManager.armAfkTimeout).
        // Solo faltaba donde cambiarla. Cuenta la del ANFITRION; se guarda en
        // las preferencias de red de Forge, que es donde la lee el motor.
        tab(Tab.GENERAL);
        getChildren().add(section(NeoText.get("settings.net")));
        final forge.localinstance.properties.ForgeNetPreferences netPrefs =
                forge.model.FModel.getNetPreferences();
        final String[] afkValues = {"0", "1", "2", "3", "5", "10", "15", "30", "60"};
        final String[] afkLabels = new String[afkValues.length];
        for (int i = 0; i < afkValues.length; i++) {
            afkLabels[i] = "0".equals(afkValues[i]) ? NeoText.get("common.no") : afkValues[i];
        }
        final int afkNow = netPrefs.getPrefInt(
                forge.localinstance.properties.ForgeNetPreferences.FNetPref.NET_AFK_TIMEOUT);
        String afkCurrent = afkNow <= 0 ? afkLabels[0] : String.valueOf(afkNow);
        getChildren().add(choiceRow(
                forge.util.Localizer.getInstance().getMessage("lblAfkTimeout"), afkLabels, afkCurrent,
                label -> {
                    final String minutes = label.equals(afkLabels[0]) ? "0" : label;
                    netPrefs.setPref(forge.localinstance.properties.ForgeNetPreferences.FNetPref
                            .NET_AFK_TIMEOUT, minutes);
                    netPrefs.save();
                }));
        final Label afkNote = new Label(NeoText.get("settings.net.afk.note"));
        afkNote.getStyleClass().add("home-subtitle");
        afkNote.setWrapText(true);
        afkNote.setMaxWidth(UiScale.px(560));
        afkNote.setMinHeight(Region.USE_PREF_SIZE);
        getChildren().add(afkNote);

        getChildren().add(section(NeoText.get("settings.discord")));
        getChildren().add(toggleRow(NeoText.get("settings.discord.on"),
                NeoSettings.discord(),
                on -> {
                    NeoSettings.setBool(NeoSettings.DISCORD, on);
                    NeoSettings.save();
                    forge.neo.discord.DiscordRich.setEnabled(on);
                }));

        // --- versiones nuevas ---
        //
        // Pedido en itch.io el 28-09-2026. Encendido de fabrica; se apaga aqui
        // porque preguntar a itch.io le ensenya tu IP. Encenderlo pregunta en
        // el acto si aun no se habia hecho. Ver forge.neo.update.NeoUpdate.
        getChildren().add(section(NeoText.get("settings.update")));
        getChildren().add(toggleRow(NeoText.get("settings.update.on"),
                NeoSettings.updateCheck(),
                on -> {
                    NeoSettings.setBool(NeoSettings.UPDATE_CHECK, on);
                    NeoSettings.save();
                    forge.neo.update.NeoUpdate.enabledChanged(on);
                }));

        tab(Tab.SOUND);

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

        // --- atajos de teclado ---
        //
        // Una fila con un boton y no trece filas aqui: los atajos son una
        // pantalla aparte (ShortcutsPanel), que se viene a consultar tanto como a
        // cambiar, y metidos en este scroll lo alargarian el doble.
        // --- el arte de las cartas, bajado de antemano ---
        //
        // Pedido por dos jugadores el 20-09-2026: que las cartas salgan
        // SIEMPRE con foto, tambien sin internet. En el zip no cabe (2 GB, y
        // casi todo son cartas que ese jugador no vera nunca), asi que se baja
        // desde aqui. Dos botones porque son dos necesidades: irse sin linea, o
        // simplemente que TUS mazos salgan con foto — que es lo que quiere casi
        // todo el mundo y son unos cientos de MB.
        //
        // Aqui, y no en el menu: una casilla del menu es una PREGUNTA distinta
        // (la auditoría del motor 2), y esto es mantenimiento.
        tab(Tab.ART);
        final Button artAll = new Button(NeoText.get("settings.art.all"));
        artAll.getStyleClass().add("segment");
        artAll.setMinWidth(Region.USE_PREF_SIZE);
        artAll.setOnAction(e -> showArtDownload(forge.neo.card.ArtDownload.Scope.ALL));
        getChildren().add(row(NeoText.get("settings.art.allRow"), artAll));

        final Button artDecks = new Button(NeoText.get("settings.art.decks"));
        artDecks.getStyleClass().add("segment");
        artDecks.setMinWidth(Region.USE_PREF_SIZE);
        artDecks.setOnAction(e -> showArtDownload(forge.neo.card.ArtDownload.Scope.MY_DECKS));
        getChildren().add(row(NeoText.get("settings.art.decksRow"), artDecks));

        // Todas las impresiones y artes, como el descargador de Forge (~7 GB).
        // Para quien lo quiere TODO; al que solo quiere jugar sin linea le
        // basta el primero. Pedido en Discord el 23-09-2026.
        final Button artEvery = new Button(NeoText.get("settings.art.every"));
        artEvery.getStyleClass().add("segment");
        artEvery.setMinWidth(Region.USE_PREF_SIZE);
        artEvery.setOnAction(e -> showArtDownload(forge.neo.card.ArtDownload.Scope.EVERY_PRINTING));
        getChildren().add(row(NeoText.get("settings.art.everyRow"), artEvery));

        // Por expansion o por formato, como el descargador de Forge. Pedido en
        // Discord el 02-10-2026: "Forge has options for entire formats or by
        // set (which I'm more interested in)".
        getChildren().add(row(NeoText.get("settings.art.setRow"), artSetPicker()));
        getChildren().add(row(NeoText.get("settings.art.formatRow"), artFormatPicker()));

        // Buscar imagenes mejores de todo lo bajado: las provisionales de
        // Scryfall de las expansiones recientes. Pedido en itch.io el
        // 29-09-2026 despues del boton de una carta. Ver ArtHdScan.
        final Button artHd = new Button(NeoText.get("settings.art.hd"));
        artHd.setId("settings-art-hd");
        artHd.getStyleClass().add("segment");
        artHd.setMinWidth(Region.USE_PREF_SIZE);
        final Label artHdStatus = new Label();
        artHdStatus.getStyleClass().add("home-subtitle");
        artHdStatus.setWrapText(true);
        artHdStatus.setMaxWidth(UiScale.px(460));
        artHd.setOnAction(e -> {
            artHd.setDisable(true);
            artHdStatus.setText(NeoText.get("settings.art.hd.start"));
            final Thread t = new Thread(() -> {
                final forge.neo.card.ArtHdScan.Result r = forge.neo.card.ArtHdScan.run(
                        forge.neo.card.CardImages.HD_STORE,
                        new forge.neo.card.ArtHdScan.Progress() {
                            @Override
                            public void update(final int done, final int sets, final String name, final int updated) {
                                javafx.application.Platform.runLater(() ->
                                        artHdStatus.setText(NeoText.get("settings.art.hd.progress",
                                                Math.min(done + 1, Math.max(sets, 1)), sets, name, updated)));
                            }

                            // Scryfall ha pedido esperar: se dice, o parece colgado.
                            @Override
                            public void waiting(final long seconds) {
                                javafx.application.Platform.runLater(() ->
                                        artHdStatus.setText(NeoText.get("settings.art.hd.waiting", seconds)));
                            }
                        },
                        null);
                javafx.application.Platform.runLater(() -> {
                    artHd.setDisable(false);
                    artHdStatus.setText(r.sets() == 0 ? NeoText.get("settings.art.hd.none")
                            : r.stoppedByNetwork() ? NeoText.get("settings.art.hd.offline", r.updated())
                            : NeoText.get("settings.art.hd.done", r.updated(), r.sets()));
                });
            }, "neo-art-hd");
            t.setDaemon(true);
            t.start();
        });
        final HBox artHdBox = new HBox(12, artHd, artHdStatus);
        artHdBox.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(row(NeoText.get("settings.art.hdRow"), artHdBox));

        addDataSection(host);

        tab(Tab.GENERAL);
        getChildren().add(section(NeoText.get("settings.keyboard")));
        final Button shortcuts = new Button(NeoText.get("settings.shortcuts.open"));
        shortcuts.getStyleClass().add("segment");
        shortcuts.setMinWidth(Region.USE_PREF_SIZE);
        shortcuts.setOnAction(e -> showShortcuts());
        getChildren().add(row(NeoText.get("settings.shortcuts"), shortcuts));

        final Button close = new Button(NeoText.get("common.close"));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> onClose.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        footer.getChildren().addAll(gap, close);
        footer.setPadding(new Insets(12, 0, 0, 0));

        // LAS PESTANYAS (itch.io, 05-10-2026: "separate into different tabs
        // each config part, it would be easier to find stuff other than having
        // to scroll through everything"). Cada bloque de arriba va detras de su
        // marca (tab) y aqui se reparten los MISMOS nodos en paginas: ningun
        // ajuste cambia, solo donde vive. Se hace al final, como antes el
        // visor, para que una fila nueva no tenga que saber nada de pestanyas:
        // basta con que vaya detras de la marca que le toca.
        final List<javafx.scene.Node> middle =
                new ArrayList<>(getChildren().subList(1, getChildren().size()));
        Tab current = Tab.GENERAL;
        for (final javafx.scene.Node n : middle) {
            if (n.getProperties().get(TAB_MARK) instanceof Tab t) {
                current = t;
                continue;
            }
            pages.computeIfAbsent(current, k -> {
                final VBox page = new VBox(getSpacing());
                page.getStyleClass().add("settings-page");
                return page;
            }).getChildren().add(n);
        }

        final javafx.scene.control.ToggleGroup group = new javafx.scene.control.ToggleGroup();
        rail.getStyleClass().add("settings-tabs");
        rail.setMinWidth(Region.USE_PREF_SIZE);
        for (final Tab t : Tab.values()) {
            if (!pages.containsKey(t)) {
                continue;
            }
            final javafx.scene.control.ToggleButton b =
                    new javafx.scene.control.ToggleButton(NeoText.get("settings.tab." + t.id));
            b.getStyleClass().add("settings-tab");
            b.setToggleGroup(group);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(e -> showTab(t));
            tabButtons.put(t, b);
            rail.getChildren().add(b);
        }
        // Siempre hay una puesta: clicar otra vez la que ya esta no la apaga.
        group.selectedToggleProperty().addListener((o, was, now) -> {
            if (now == null && was != null) {
                was.setSelected(true);
            }
        });
        showTab(Tab.byId(NeoSettings.get(LAST_TAB, Tab.GAME.id)), false);

        content.setSpacing(getSpacing());
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);

        final HBox body = new HBox(UiScale.px(18), rail, scroll);
        HBox.setHgrow(scroll, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);

        // Lo que no se encoge: si el VBox tuviera que recortar por arriba o por
        // abajo, se llevaria por delante justo lo que hay que dejar quieto.
        title.setMinHeight(Region.USE_PREF_SIZE);
        footer.setMinHeight(Region.USE_PREF_SIZE);

        getChildren().setAll(title, body, footer);
    }

    /** Las pestanyas, en el orden del carril. El id es el de su texto y el que se guarda. */
    public enum Tab {
        GENERAL("general"), GAME("game"), TABLE("table"), DISPLAY("display"),
        SOUND("sound"), ART("art"), MODES("modes"), DATA("data");

        public final String id;

        Tab(final String id) {
            this.id = id;
        }

        static Tab byId(final String id) {
            for (final Tab t : values()) {
                if (t.id.equalsIgnoreCase(id)) {
                    return t;
                }
            }
            return GAME;
        }
    }

    /** La ultima pestanya abierta: se vuelve a ella. Es comodidad, no un ajuste. */
    private static final String LAST_TAB = "settingsTab";

    private static final String TAB_MARK = "neo.settings.tab";

    private final java.util.Map<Tab, VBox> pages = new java.util.EnumMap<>(Tab.class);
    private final java.util.Map<Tab, javafx.scene.control.ToggleButton> tabButtons =
            new java.util.EnumMap<>(Tab.class);
    private final VBox rail = new VBox(4);

    /** Lo que se anyada a partir de aqui va a esa pestanya. No se pinta. */
    private void tab(final Tab t) {
        final Region mark = new Region();
        mark.getProperties().put(TAB_MARK, t);
        getChildren().add(mark);
    }

    /**
     * Que filas hay en cada pestanya, por su etiqueta. Para la auditoria de
     * las maquetas ({@code -Dneo.settings.audit}): comprobar que al repartir en
     * pestanyas no se ha perdido ni duplicado ningun ajuste.
     */
    public java.util.Map<String, List<String>> rowsByTab() {
        final java.util.Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        for (final Tab t : Tab.values()) {
            final VBox page = pages.get(t);
            if (page == null) {
                continue;
            }
            final List<String> labels = new ArrayList<>();
            for (final javafx.scene.Node n : page.lookupAll(".settings-label")) {
                if (n instanceof Label l) {
                    labels.add(l.getText());
                }
            }
            out.put(t.id, labels);
        }
        return out;
    }

    /**
     * Pulsa el boton {@code button} de la fila {@code caption}, este en la
     * pestanya que este, por el mismo {@code fire()} que un clic. Para las
     * maquetas: demuestra que cada fila sigue haciendo lo que hacia.
     */
    public boolean pressForTest(final String caption, final String button) {
        for (final VBox page : pages.values()) {
            for (final javafx.scene.Node n : page.lookupAll(".settings-label")) {
                if (!(n instanceof Label l) || !caption.equals(l.getText()) || l.getParent() == null) {
                    continue;
                }
                for (final javafx.scene.Node b : l.getParent().lookupAll(".button")) {
                    if (b instanceof javafx.scene.control.ButtonBase bb && button.equals(bb.getText())) {
                        bb.fire();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Abre esa pestanya (por su id: "game", "table"...). Para las maquetas. */
    public void showTab(final String id) {
        showTab(Tab.byId(id));
    }

    public void showTab(final Tab t) {
        showTab(t, true);
    }

    private void showTab(final Tab wanted, final boolean remember) {
        final Tab t = pages.containsKey(wanted) ? wanted : Tab.GAME;
        final VBox page = pages.get(t);
        if (page == null) {
            return;
        }
        content.getChildren().setAll(page);
        scroll.setVvalue(0);
        final javafx.scene.control.ToggleButton b = tabButtons.get(t);
        if (b != null) {
            b.setSelected(true);
        }
        if (remember) {
            NeoSettings.set(LAST_TAB, t.id);
            NeoSettings.save();
        }
    }

    /** Lo mas alto y lo mas ancho que ha pedido una pestanya: ver layoutChildren. */
    private double tallest;
    private double widest;

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
            final double wanted = Math.max(rail.prefHeight(-1), content.prefHeight(
                    content.getWidth() > 0 ? content.getWidth() : content.prefWidth(-1)));
            // Con pestanyas, lo MAS alto que se haya visto: si no, cada cambio
            // de pestanya encogia o estiraba el panel y el carril saltaba de
            // sitio bajo el raton. Y lo mismo a lo ancho.
            tallest = Math.max(tallest, wanted);
            final double h = Math.max(160, Math.min(tallest + 2, room));
            final double w = content.prefWidth(-1);
            if (w > widest + 1) {
                widest = w;
                scroll.setMinViewportWidth(w);
            }
            if (Math.abs(h - scroll.getPrefViewportHeight()) > 1) {
                scroll.setPrefViewportHeight(h);
            }
        }
        super.layoutChildren();
    }

    /**
     * Cambia el contenido por la pantalla de atajos, con "Volver" para regresar.
     *
     * <p>Se hace aqui dentro y no abriendo otra capa: este panel vive en dos
     * sitios distintos (el menu principal y la pausa) y cada uno lo cierra a su
     * manera. Sustituyendo lo de dentro, "Volver" funciona igual en los dos sin
     * que ninguno tenga que saber que existe esta pantalla.
     */
    public void showShortcuts() {
        if (shortcutsOpen) {
            return;
        }
        shortcutsOpen = true;
        final List<javafx.scene.Node> main = new ArrayList<>(getChildren());
        getChildren().setAll(new ShortcutsPanel(false, NeoText.get("common.back"), () -> {
            shortcutsOpen = false;
            getChildren().setAll(main);
        }));
    }

    private boolean shortcutsOpen;

    /**
     * La descarga del arte, dentro de estos mismos Ajustes.
     *
     * <p>Mismo apanyo que {@link #showShortcuts()}: se cambia el contenido y se
     * vuelve. Asi vale igual desde el menu que desde la pausa a mitad de
     * partida, sin una capa nueva ni un camino nuevo.
     */
    public void showArtDownload(final forge.neo.card.ArtDownload.Scope scope) {
        showArtDownload(scope, null);
    }

    /** Con una expansion (su codigo) o un formato (su nombre). */
    public void showArtDownload(final forge.neo.card.ArtDownload.Scope scope, final String target) {
        if (shortcutsOpen) {
            return;
        }
        shortcutsOpen = true;
        final List<javafx.scene.Node> main = new ArrayList<>(getChildren());
        getChildren().setAll(new ArtDownloadPanel(scope, target, () -> {
            shortcutsOpen = false;
            getChildren().setAll(main);
        }));
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

    /**
     * La nota de "esto no cambia la partida que estas jugando".
     *
     * <p>Reportado jugando el 07-09-2026: <i>enciendo algo en Ajustes, vuelvo
     * a la mesa y no ha cambiado nada, asi que parece roto</i>. Y no lo esta:
     * hay ajustes que el motor solo lee al <b>montar</b> la partida (la regla
     * de mulligan, el tope de tiempo de la IA, con que mazo sale el rival...),
     * asi que cambiarlos a mitad no puede hacer nada. Un ajuste que se traga
     * su propia respuesta es el principio 1 de {@code las notas de diseño} §10b.
     *
     * <p>Tres decisiones:
     *
     * <ul>
     *   <li><b>Solo dentro de una partida.</b> Desde el menu, "la siguiente
     *       partida" es la que vas a empezar ahora: avisar ahi seria ruido.</li>
     *   <li><b>Al tocarlo, no siempre.</b> Una nota permanente debajo de siete
     *       filas es una pantalla llena de avisos que nadie lee; puesta justo
     *       donde acabas de clicar, se lee.</li>
     *   <li><b>Nace oculta y sin ocupar</b> ({@code setManaged(false)}): si no,
     *       deja un hueco entre filas que no se explica.</li>
     * </ul>
     */
    private static Label nextGameNote() {
        final Label note = new Label(NeoText.get("settings.nextGame"));
        note.getStyleClass().add("settings-note");
        note.setWrapText(true);
        note.setMaxWidth(UiScale.px(560));
        note.setMinHeight(Region.USE_PREF_SIZE);
        // -Dneo.settings.notes=true las nace visibles TODAS: es la unica
        // forma de capturar esta pantalla con sus notas puestas, porque de
        // verdad solo salen al tocar el ajuste y con una partida detras.
        final boolean forced = Boolean.getBoolean("neo.settings.notes");
        note.setVisible(forced);
        note.setManaged(forced);
        return note;
    }

    /**
     * Envuelve el manejador de un ajuste que solo se aplica al empezar partida.
     *
     * <p>Lo que hace el ajuste no cambia: se hace lo de siempre y ademas se
     * ensenya la nota. Asi el aviso no puede desincronizarse de lo que la fila
     * hace de verdad, que es lo que pasaria con una lista aparte de "estos
     * avisan".
     */
    private <T> Consumer<T> nextGame(final Consumer<T> action, final Label note) {
        return v -> {
            action.accept(v);
            if (inMatch) {
                note.setVisible(true);
                note.setManaged(true);
            }
        };
    }

    // ------------------------------------------------------------------
    // Tus datos: exportar e importar (forge.neo.data.NeoBackup)

    /**
     * "Tus datos": exportar e importar en un zip. Pedido en Discord el
     * 30-09-2026 para pasar los datos entre versiones y entre PC y Android.
     *
     * <p>Con una partida en marcha va apagado: importar reinicia el juego.
     * Importar no escribe nada ahora: prepara el zip y reinicia, y se aplica
     * al arrancar (ver NeoBackup).
     */
    private void addDataSection(final Host host) {
        tab(Tab.DATA);
        final Label help = new Label(NeoText.get("settings.data.help"));
        help.getStyleClass().add("home-subtitle");
        help.setWrapText(true);
        help.setMaxWidth(560);
        getChildren().add(help);

        final forge.neo.data.NeoBackup.Places places = forge.neo.data.DataPlaces.desktop();
        final long musicMb = (sizeOf(new java.io.File(places.root, "neo/music"))
                + sizeOf(new java.io.File(places.root, "custom"))) >> 20;
        final javafx.scene.control.CheckBox music = new javafx.scene.control.CheckBox(
                NeoText.get("settings.data.music", musicMb));
        music.setSelected(false);

        final Button export = new Button(NeoText.get("settings.data.export"));
        final Button importB = new Button(NeoText.get("settings.data.import"));
        for (final Button b : new Button[] {export, importB}) {
            b.getStyleClass().add("segment");
            b.setMinWidth(Region.USE_PREF_SIZE);
        }
        export.setId("settings-data-export");
        importB.setId("settings-data-import");
        final Label status = new Label();
        status.getStyleClass().add("home-subtitle");
        status.setWrapText(true);
        status.setMaxWidth(560);
        final VBox choice = new VBox(6);

        if (inMatch) {
            export.setDisable(true);
            importB.setDisable(true);
            status.setText(NeoText.get("settings.data.inMatch"));
        }

        export.setOnAction(e -> {
            final javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setInitialFileName("NeoForge-datos-"
                    + new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date())
                    + ".zip");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("zip", "*.zip"));
            final java.io.File to = fc.showSaveDialog(getScene() == null ? null : getScene().getWindow());
            if (to == null) {
                return;
            }
            export.setDisable(true);
            status.setText(NeoText.get("settings.data.exporting"));
            final boolean withMusic = music.isSelected();
            final Thread t = new Thread(() -> {
                String msg;
                try {
                    final forge.neo.data.NeoBackup.Summary s = forge.neo.data.NeoBackup.export(
                            places, to, withMusic, forge.neo.NeoVersion.neoVersion(), forge.neo.data.DataPlaces.platform());
                    msg = NeoText.get("settings.data.exported", to.getName(), s.decks, s.total());
                } catch (final java.io.IOException | RuntimeException ex) {
                    msg = NeoText.get("settings.data.failed", ex.getMessage());
                }
                final String m = msg;
                javafx.application.Platform.runLater(() -> {
                    status.setText(m);
                    export.setDisable(false);
                });
            }, "neo-export");
            t.setDaemon(true);
            t.start();
        });

        importB.setOnAction(e -> {
            final javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("zip", "*.zip"));
            final java.io.File from = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (from == null) {
                return;
            }
            final forge.neo.data.NeoBackup.Summary s = forge.neo.data.NeoBackup.inspect(from);
            choice.getChildren().clear();
            if (!s.valid) {
                status.setText(NeoText.get("settings.data.notBackup"));
                return;
            }
            status.setText(NeoText.get("settings.data.found", platformName(s.platform),
                    s.version.isEmpty() ? "?" : s.version, s.date, s.decks, s.total()));
            final Button add = new Button(NeoText.get("settings.data.add"));
            final Button replace = new Button(NeoText.get("settings.data.replace"));
            final Button cancel = new Button(NeoText.get("common.cancel"));
            add.getStyleClass().add("btn-primary");
            replace.getStyleClass().add("btn-secondary");
            cancel.getStyleClass().add("btn-secondary");
            final Label addHelp = new Label(NeoText.get("settings.data.addHelp"));
            final Label replaceHelp = new Label(NeoText.get("settings.data.replaceHelp"));
            addHelp.getStyleClass().add("home-subtitle");
            replaceHelp.getStyleClass().add("home-subtitle");
            cancel.setOnAction(x -> {
                choice.getChildren().clear();
                status.setText("");
            });
            final java.util.function.Consumer<forge.neo.data.NeoBackup.Mode> go = mode -> {
                try {
                    forge.neo.data.NeoBackup.stage(places.root, from, mode);
                } catch (final java.io.IOException ex) {
                    status.setText(NeoText.get("settings.data.failed", ex.getMessage()));
                    return;
                }
                choice.getChildren().clear();
                if (!host.restartToImport()) {
                    status.setText(NeoText.get("settings.data.restartManual"));
                }
            };
            add.setOnAction(x -> go.accept(forge.neo.data.NeoBackup.Mode.ADD));
            replace.setOnAction(x -> go.accept(forge.neo.data.NeoBackup.Mode.REPLACE));
            final HBox addRow = new HBox(10, add, addHelp);
            final HBox replaceRow = new HBox(10, replace, replaceHelp);
            addRow.setAlignment(Pos.CENTER_LEFT);
            replaceRow.setAlignment(Pos.CENTER_LEFT);
            choice.getChildren().addAll(addRow, replaceRow, cancel);
        });

        final HBox buttons = new HBox(12, export, importB, music);
        buttons.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(buttons, status, choice);
    }

    /** "Windows", "Android"... para decir de donde viene un zip. */
    private static String platformName(final String p) {
        if (p == null || p.isEmpty()) {
            return "?";
        }
        return Character.toUpperCase(p.charAt(0)) + p.substring(1);
    }

    private static long sizeOf(final java.io.File f) {
        if (f.isFile()) {
            return f.length();
        }
        long n = 0;
        final java.io.File[] kids = f.listFiles();
        if (kids != null) {
            for (final java.io.File k : kids) {
                n += sizeOf(k);
            }
        }
        return n;
    }

    private static Label section(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("settings-section");
        return l;
    }

    /** Etiqueta a la izquierda, control a la derecha, ancho fijo. */
    /** Elegir una expansion (la mas nueva arriba) y bajar sus artes. */
    private Region artSetPicker() {
        final javafx.scene.control.ComboBox<forge.card.CardEdition> sets = new javafx.scene.control.ComboBox<>();
        sets.getItems().addAll(forge.neo.card.ArtDownload.editions());
        // El desplegable oscuro de los equipos: sin clase sale el blanco de JavaFX.
        sets.getStyleClass().add("team-combo");
        sets.setVisibleRowCount(16);
        sets.setPrefWidth(UiScale.px(300));
        sets.setPromptText(NeoText.get("settings.art.setPick"));
        sets.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final forge.card.CardEdition ed) {
                return ed == null ? "" : ed.getName() + " (" + ed.getCode() + ")";
            }

            @Override
            public forge.card.CardEdition fromString(final String s) {
                return null;
            }
        });
        final Button go = new Button(NeoText.get("settings.art.set"));
        go.getStyleClass().add("segment");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.disableProperty().bind(sets.valueProperty().isNull());
        go.setOnAction(e -> showArtDownload(forge.neo.card.ArtDownload.Scope.SET, sets.getValue().getCode()));
        final HBox box = new HBox(8, sets, go);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Elegir un formato y bajar una foto de cada carta legal en el. */
    private Region artFormatPicker() {
        final javafx.scene.control.ComboBox<forge.game.GameFormat> formats = new javafx.scene.control.ComboBox<>();
        formats.getItems().addAll(forge.neo.card.ArtDownload.formats());
        // El desplegable oscuro de los equipos: sin clase sale el blanco de JavaFX.
        formats.getStyleClass().add("team-combo");
        formats.setVisibleRowCount(16);
        formats.setPrefWidth(UiScale.px(300));
        formats.setPromptText(NeoText.get("settings.art.formatPick"));
        formats.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final forge.game.GameFormat f) {
                return f == null ? "" : f.getName();
            }

            @Override
            public forge.game.GameFormat fromString(final String s) {
                return null;
            }
        });
        final Button go = new Button(NeoText.get("settings.art.format"));
        go.getStyleClass().add("segment");
        go.setMinWidth(Region.USE_PREF_SIZE);
        go.disableProperty().bind(formats.valueProperty().isNull());
        go.setOnAction(e -> showArtDownload(forge.neo.card.ArtDownload.Scope.FORMAT, formats.getValue().getName()));
        final HBox box = new HBox(8, formats, go);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static HBox row(final String caption, final Region control) {
        final Label label = new Label(caption);
        label.getStyleClass().add("settings-label");
        label.setMinWidth(UiScale.px(190));
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
        slider.setPrefWidth(UiScale.px(240));
        slider.getStyleClass().add("settings-slider");

        final Label readout = new Label(format.apply(slider.getValue()));
        readout.getStyleClass().add("settings-value");
        readout.setMinWidth(UiScale.px(52));

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
