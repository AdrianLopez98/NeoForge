package forge.neo.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import forge.neo.NeoText;
import forge.neo.look.LookItem;
import forge.neo.look.NeoLook;
import forge.neo.look.NeoMusic;
import forge.sound.MusicPlaylist;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/**
 * Personalizacion: tu nombre y tu cara, los rivales, el tapete, las fundas y
 * la musica.
 *
 * <p>Todo lo que se ve aqui se elige de una <b>rejilla que mezcla lo que trae
 * Forge con lo que hayas traido tu</b>, sin distinguirlos: lo importado va
 * primero — si te has molestado en traerlo, no puede quedar sepultado detras de
 * ciento veintiseis — y de ahi para abajo, los de fabrica.
 *
 * <p>El modelo esta en {@link NeoLook} y {@link NeoMusic}; aqui solo se pinta.
 *
 * <p><b>Importar copia el fichero.</b> Se dice en la propia pantalla, porque es
 * lo que evita la pregunta de "¿y si borro el PNG de Descargas?".
 */
public class LookScreen extends BorderPane {

    /** Que pestanya se estaba mirando, para volver a ella. */
    private static int lastTab;

    private final Runnable onBack;
    private final VBox content = new VBox(12);
    private final HBox tabs = new HBox(6);
    private final List<Button> tabButtons = new ArrayList<>();

    /** Se avisa al salir por si hay que repintar la mesa (el tapete). */
    private final Runnable onChanged;

    public LookScreen(final Runnable onBack, final Runnable onChanged) {
        this.onBack = onBack;
        this.onChanged = onChanged;
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("look.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("look.subtitle"));
        sub.getStyleClass().add("home-subtitle");

        final VBox titles = new VBox(2, title, sub);
        titles.setAlignment(Pos.CENTER_LEFT);

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> onBack.run());

        final HBox header = new HBox(14, titles, gap, back);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(22, 30, 6, 30));

        tabs.setPadding(new Insets(0, 30, 8, 30));
        tabs.setAlignment(Pos.CENTER_LEFT);
        addTab(NeoText.get("look.tab.profile"), this::buildProfile);
        addTab(NeoText.get("look.tab.rivals"), this::buildRivals);
        addTab(NeoText.get("look.tab.playmat"), this::buildPlaymats);
        addTab(NeoText.get("look.tab.sleeves"), this::buildSleeves);
        addTab(NeoText.get("look.tab.music"), this::buildMusic);

        setTop(new VBox(header, tabs));

        content.setPadding(new Insets(4, 30, 24, 30));
        setCenter(content);

        // -Dneo.look.tab=N abre una pestanya concreta: es la unica forma de
        // capturar las de dentro sin poder clicar.
        final int forced = Integer.getInteger("neo.look.tab", -1);
        select(forced >= 0 ? Math.min(forced, tabButtons.size() - 1)
                : Math.min(lastTab, tabButtons.size() - 1));
    }

    // ---------------------------------------------------------------
    // Pestanyas
    // ---------------------------------------------------------------

    private final List<Runnable> builders = new ArrayList<>();

    private void addTab(final String label, final Runnable builder) {
        final int index = tabButtons.size();
        final Button b = new Button(label);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> select(index));
        tabButtons.add(b);
        builders.add(builder);
        tabs.getChildren().add(b);
    }

    private void select(final int index) {
        lastTab = Math.max(0, index);
        for (int i = 0; i < tabButtons.size(); i++) {
            tabButtons.get(i).pseudoClassStateChanged(SELECTED, i == lastTab);
        }
        content.getChildren().clear();
        builders.get(lastTab).run();
    }

    /** Lo ultimo que no se pudo hacer con la musica, para poder decirlo. */
    private String musicProblem;

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    private static final javafx.css.PseudoClass PICKED =
            javafx.css.PseudoClass.getPseudoClass("picked");

    // ---------------------------------------------------------------
    // 1. Perfil: tu nombre y tu cara
    // ---------------------------------------------------------------

    private void buildProfile() {
        final TextField name = new TextField(NeoLook.playerName());
        name.setPromptText(NeoText.get("look.name.hint"));
        name.getStyleClass().add("text-input");
        name.setPrefColumnCount(18);
        name.textProperty().addListener((o, was, is) -> NeoLook.setPlayerName(is));

        final HBox nameRow = new HBox(12, caption(NeoText.get("look.name")), name);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(nameRow,
                caption(NeoText.get("look.avatar")),
                picker(NeoLook.avatars(), NeoLook.AVATAR, 74, true,
                        NeoLook.avatarsDir(), NeoText.get("look.avatar.size")));
    }

    // ---------------------------------------------------------------
    // 2. Rivales
    // ---------------------------------------------------------------

    /**
     * Los nombres de las IAs.
     *
     * <p>Se ensenyan cuatro huecos porque Commander se juega hasta a cuatro y
     * el numero de rivales se elige en otra pantalla: rellenar solo los que hay
     * ahora obligaria a volver aqui cada vez que cambias de partida.
     */
    private void buildRivals() {
        final Label hint = new Label(NeoText.get("look.rivals.hint"));
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(620);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        content.getChildren().add(hint);

        final List<TextField> fields = new ArrayList<>();
        final List<String> saved = NeoLook.aiNames();
        for (int i = 0; i < 4; i++) {
            final TextField f = new TextField(i < saved.size() ? saved.get(i) : "");
            f.getStyleClass().add("text-input");
            f.setPrefColumnCount(16);
            f.setPromptText(NeoText.get("look.rivals.empty"));
            fields.add(f);
            final HBox row = new HBox(12, caption(NeoText.get("look.rival", i + 1)), f);
            row.setAlignment(Pos.CENTER_LEFT);
            content.getChildren().add(row);
        }

        final Runnable save = () -> {
            final List<String> names = new ArrayList<>();
            for (final TextField f : fields) {
                names.add(f.getText());
            }
            NeoLook.setAiNames(names);
        };
        for (final TextField f : fields) {
            f.textProperty().addListener((o, was, is) -> save.run());
        }

        // Vaciarlos todos: la proxima partida vuelve a sortear nombres.
        final Button clear = new Button(NeoText.get("look.rivals.reroll"));
        clear.getStyleClass().add("btn-secondary");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setOnAction(e -> {
            for (final TextField f : fields) {
                f.setText("");
            }
            NeoLook.setAiNames(List.of());
            select(1);
        });
        content.getChildren().add(clear);
    }

    // ---------------------------------------------------------------
    // 3. Tapete
    // ---------------------------------------------------------------

    private void buildPlaymats() {
        content.getChildren().add(picker(NeoLook.playmats(), NeoLook.PLAYMAT, 150, false,
                NeoLook.playmatsDir(), NeoText.get("look.playmat.size")));
    }

    // ---------------------------------------------------------------
    // 4. Fundas
    // ---------------------------------------------------------------
    //
    // Durante un tiempo esta pestanya NO estuvo, y por un motivo bueno: una
    // funda solo se ve en el reverso de una carta boca abajo, y la mesa no
    // pintaba ninguna. Un ajuste que no cambia nada de lo que ves es peor que
    // no tenerlo (principio 1).
    //
    // Ya no es asi: el cementerio y el exilio de la mesa se pintan con la
    // funda de su duenyo (TableBinder.sleeveOf -> PlayerField.setSleeveImage),
    // y el motor le reparte una a cada IA (PlayerView.getSleeveIndex), asi que
    // la tuya era la unica que no se podia elegir.

    private void buildSleeves() {
        final Label hint = new Label(NeoText.get("look.sleeves.hint"));
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(680);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        content.getChildren().addAll(hint,
                picker(NeoLook.sleeves(), NeoLook.SLEEVE, 100, false,
                        NeoLook.sleevesDir(), NeoText.get("look.sleeve.size")));
    }

    /**
     * Una rejilla de cosas para elegir, con su boton de importar.
     *
     * @param key    la clave de ajustes donde se guarda lo elegido
     * @param round  el retrato va redondo; el tapete y la funda, rectangulares
     * @param dir    donde se copia lo que se importe
     * @param sizeHint que medidas conviene que tenga lo que traigas
     */
    private Region picker(final List<LookItem> items, final String key, final double tile,
                          final boolean round, final File dir, final String sizeHint) {
        final FlowPane grid = new FlowPane(10, 10);
        grid.setAlignment(Pos.TOP_LEFT);

        // Lo marcado tiene que ser lo que de verdad se esta usando, no lo que
        // ponga el ajuste: sin ajuste puesto NeoLook se cae al primero de la
        // lista, y si aqui no se marcaba nadie la pantalla decia que no habias
        // elegido cuando si tenias uno puesto.
        final String current = (NeoLook.PLAYMAT.equals(key) ? NeoLook.currentPlaymat()
                : NeoLook.SLEEVE.equals(key) ? NeoLook.currentSleeve()
                : NeoLook.currentAvatar()).getId();

        for (final LookItem item : items) {
            grid.getChildren().add(tile(item, key, current, tile, round, dir));
        }

        final ScrollPane scroll = new ScrollPane(grid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // El pie: importar, y las medidas. Decir el tamanyo ANTES de elegir el
        // fichero ahorra la unica forma de equivocarse aqui.
        final Button bring = new Button(NeoText.get("look.import"));
        bring.getStyleClass().add("btn-primary");
        bring.setMinWidth(Region.USE_PREF_SIZE);
        bring.setOnAction(e -> {
            final File chosen = chooseImage();
            if (chosen == null) {
                return;
            }
            final LookItem brought = NeoLook.importInto(dir, chosen);
            if (brought != null) {
                forge.neo.NeoSettings.set(key, brought.getId());
                forge.neo.NeoSettings.save();
                changed();
                select(lastTab);
            }
        });

        final Label hint = new Label(sizeHint);
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(620);
        hint.setMinHeight(Region.USE_PREF_SIZE);

        final HBox footer = new HBox(14, bring, hint);
        footer.setAlignment(Pos.CENTER_LEFT);

        final VBox box = new VBox(10, scroll, footer);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Una casilla de la rejilla. */
    private Region tile(final LookItem item, final String key, final String current,
                        final double size, final boolean round, final File dir) {
        final StackPane face = new StackPane();
        face.getStyleClass().add("look-tile");
        face.setPrefSize(size, round ? size : size * 1.2);
        face.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        face.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        face.pseudoClassStateChanged(PICKED, item.getId().equals(current));

        if (item.isNone()) {
            final Label none = new Label(NeoText.get("look.none"));
            none.getStyleClass().add("home-subtitle");
            face.getChildren().add(none);
        } else {
            final Image image = item.image();
            if (image != null) {
                final ImageView view = new ImageView(image);
                view.setFitWidth(size - 6);
                view.setFitHeight((round ? size : size * 1.2) - 6);
                view.setPreserveRatio(false);
                view.setSmooth(true);
                if (round) {
                    view.setClip(new javafx.scene.shape.Circle(
                            (size - 6) / 2, (size - 6) / 2, (size - 6) / 2));
                }
                face.getChildren().add(view);
            } else {
                // Una imagen ilegible no puede dejar un hueco mudo: se dice.
                final Label bad = new Label("?");
                bad.getStyleClass().add("home-subtitle");
                face.getChildren().add(bad);
            }
        }

        face.setCursor(javafx.scene.Cursor.HAND);
        face.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                // Click derecho sobre algo tuyo: borrarlo. Sobre algo de Forge
                // no hace nada, que no es tuyo para borrarlo.
                if (item.isImported() && NeoLook.delete(item)) {
                    select(lastTab);
                }
                return;
            }
            forge.neo.NeoSettings.set(key, item.getId());
            forge.neo.NeoSettings.save();
            changed();
            select(lastTab);
        });
        return face;
    }

    // ---------------------------------------------------------------
    // 5. Musica
    // ---------------------------------------------------------------

    /**
     * Que suena en los menus y que suena en la partida.
     *
     * <p>Cada pista se marca o se desmarca. <b>Sin marcar ninguna suenan las de
     * Forge</b>, que es lo que pasa hoy y lo que tiene que seguir pasando si no
     * tocas nada; en cuanto marcas una, suenan solo las marcadas.
     */
    private void buildMusic() {
        final Label hint = new Label(NeoText.get("look.music.hint"));
        hint.getStyleClass().add("home-subtitle");
        hint.setWrapText(true);
        hint.setMaxWidth(680);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        content.getChildren().add(hint);

        if (musicProblem != null) {
            final Label bad = new Label(musicProblem);
            bad.getStyleClass().add("action-warning");
            bad.setWrapText(true);
            bad.setMaxWidth(680);
            bad.setMinHeight(Region.USE_PREF_SIZE);
            content.getChildren().add(bad);
        }

        final HBox lists = new HBox(20,
                musicList(MusicPlaylist.MENUS, NeoText.get("look.music.menus")),
                musicList(MusicPlaylist.MATCH, NeoText.get("look.music.match")));
        lists.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(lists, Priority.ALWAYS);
        content.getChildren().add(lists);
    }

    private Region musicList(final MusicPlaylist list, final String label) {
        final VBox rows = new VBox(4);

        for (final NeoMusic.Track track : NeoMusic.tracks(list)) {
            final Label name = new Label(track.getLabel());
            name.getStyleClass().add("stack-spell");
            name.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(name, Priority.ALWAYS);

            final Button toggle = new Button(NeoText.get(
                    track.isEnabled() ? "look.music.on" : "look.music.off"));
            toggle.getStyleClass().add("segment");
            toggle.setMinWidth(Region.USE_PREF_SIZE);
            toggle.pseudoClassStateChanged(SELECTED, track.isEnabled());
            toggle.setOnAction(e -> {
                // Un fallo aqui se VE. Antes se tiraba el resultado y la
                // pantalla se repintaba igual: el boton seguia diciendo
                // "Suena" y no habia forma de saber por que.
                musicProblem = !NeoMusic.setEnabled(track, !track.isEnabled())
                        ? NeoText.get("look.music.stuck", track.getLabel()) : null;
                select(lastTab);
            });

            final HBox row = new HBox(8, name, toggle);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("stack-strip");

            if (track.isImported()) {
                final Button del = new Button("x");
                del.getStyleClass().add("segment");
                del.setMinWidth(Region.USE_PREF_SIZE);
                del.setOnAction(e -> {
                    musicProblem = !NeoMusic.delete(track)
                            ? NeoText.get("look.music.stuck", track.getLabel()) : null;
                    select(lastTab);
                });
                row.getChildren().add(del);
            }
            rows.getChildren().add(row);
        }

        final ScrollPane scroll = new ScrollPane(rows);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        final Button bring = new Button(NeoText.get("look.import"));
        bring.getStyleClass().add("btn-secondary");
        bring.setMinWidth(Region.USE_PREF_SIZE);
        bring.setOnAction(e -> {
            final File chosen = chooseAudio();
            if (chosen != null && NeoMusic.importTrack(list, chosen) != null) {
                select(lastTab);
            }
        });

        final VBox box = new VBox(8, caption(label), scroll, bring);
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setPrefWidth(340);
        return box;
    }

    // ---------------------------------------------------------------
    // El explorador de ficheros
    // ---------------------------------------------------------------

    private File chooseImage() {
        return choose(NeoText.get("look.choose.image"),
                new FileChooser.ExtensionFilter(NeoText.get("look.filter.image"),
                        "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
    }

    private File chooseAudio() {
        return choose(NeoText.get("look.choose.audio"),
                new FileChooser.ExtensionFilter(NeoText.get("look.filter.audio"),
                        NeoMusic.audioExtensions().toArray(new String[0])));
    }

    /**
     * Abre el explorador del sistema.
     *
     * <p>Si algo fallara — sin ventana, un gestor de ficheros que no arranca —
     * se devuelve null y no pasa nada: no importas y la pantalla sigue viva.
     */
    private File choose(final String title, final FileChooser.ExtensionFilter filter) {
        try {
            final FileChooser chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().add(filter);
            return chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido abrir el explorador: " + e);
            return null;
        }
    }

    private void changed() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private static Label caption(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("caption");
        return l;
    }

    /** Las imagenes de las cartas llegan tarde; aqui no hay cartas, pero por si acaso. */
    public void refreshArt() {
        forge.neo.card.CardNode.refreshAllIn(this);
    }

    /** Para poder volver desde fuera. */
    public void goBack() {
        onBack.run();
    }
}
