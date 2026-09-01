package forge.neo.ui;


import forge.neo.NeoText;
import forge.neo.match.NeoFormat;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * El menu inicial: con que quieres jugar.
 *
 * <p>Va ANTES del selector de mazo. La razon es que Forge no es solo Commander:
 * el motor trae todos los formatos programados, y lo unico que los separa es el
 * {@code GameType} y la carpeta de mazos ({@link NeoFormat}). Poner aqui la
 * eleccion cuesta una pantalla y abre el resto del juego.
 *
 * <p>Cada formato dice en una linea que es, porque "Oathbreaker" o "Tiny
 * Leaders" no le dicen nada a quien no los haya jugado, y cuantos mazos tienes
 * de ese formato — asi se ve de un vistazo cual puedes jugar ya.
 */
public class MainMenu extends BorderPane {

    /** Que ha elegido el jugador. */
    public interface Actions {
        void tutorial();

        void play(NeoFormat format);

        void otherFormats();

        void draft();

        void sealed();

        void tournament();

        void online();

        void quest();

        void puzzles();

        void look();

        void achievements();

        void settings();

        void quit();
    }

    public MainMenu(final Actions actions) {
        getStyleClass().addAll("table-root", "home");

        final Label title = new Label(NeoText.get("menu.title"));
        title.getStyleClass().add("home-title");
        final Label subtitle = new Label(NeoText.get("menu.subtitle"));
        subtitle.getStyleClass().add("home-subtitle");
        // El idioma, ARRIBA DEL TODO y en la primera pantalla.
        //
        // Salio de una pregunta muy buena: *"si entra un aleman no va a saber
        // llegar a partida"*. Un ajuste de idioma escondido dentro de Ajustes
        // solo lo encuentra quien ya entiende el idioma en el que esta — o sea,
        // quien no lo necesita. Y por eso cada idioma se llama por su nombre EN
        // SU IDIOMA ("Deutsch", no "Aleman"): asi se reconoce sin leer nada mas.
        final VBox words = new VBox(2, title, subtitle);
        words.setAlignment(Pos.CENTER_LEFT);

        // El logo, AL LADO del titulo y no encima. La primera pantalla ya
        // reparte su alto entre la barra de idiomas, la cabecera y la rejilla
        // de modos; una imagen apilada arriba se lo come y la rejilla empieza a
        // recortar. Al lado no cuesta alto ninguno.
        //
        // Si no hay logo (sin ventana, o el recurso no esta) NeoLogo devuelve
        // null y la cabecera se queda exactamente como estaba: un icono que
        // falta no puede dejar sin menu.
        final javafx.scene.image.ImageView mark = forge.neo.NeoLogo.view(76);
        final HBox header = mark == null ? new HBox(words) : new HBox(16, mark, words);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20, 20, 14, 20));

        final VBox top = new VBox(0, languageBar(), header);

        final FlowPane modes = new FlowPane(16, 16);
        modes.setAlignment(Pos.CENTER);
        modes.setPadding(new Insets(10, 40, 10, 40));
        modes.setPrefWrapLength(900);

        // El tutorial, EL PRIMERO de todos y con la misma pinta que un modo.
        //
        // Va primero porque es lo unico que hay que hacer antes que nada: esta
        // interfaz tiene gestos que no se descubren solos (click derecho para
        // leer una carta, Ctrl+rueda para acercar la mesa, clicar el rail de
        // fases para pararte en el turno del rival) y quien no los conozca
        // juega peor sin saber por que. La letra pequenya lo dice sin rodeos.
        modes.getChildren().add(tile(NeoText.get("menu.tutorial"),
                NeoText.get("menu.tutorial.desc"),
                tutorialNote(), true, actions::tutorial));

        // Commander y Estandar primero: son la base, el resto de casillas se
        // ordena a partir de aqui.
        modes.getChildren().add(formatTile(NeoFormat.COMMANDER, actions));
        modes.getChildren().add(formatTile(NeoFormat.ESTANDAR, actions));

        // Aventura y torneo, PROMOVIDOS por delante de Brawl y Oathbreaker
        // (decision del autor, 31-08-2026): en la practica son los modos que
        // mas se juegan, y el menu se lee de arriba a abajo — lo primero que
        // se ve tiene que ser lo que mas se usa, no el orden en que se
        // construyeron.
        //
        // La aventura: coleccion, creditos, sobres y progreso guardado.
        modes.getChildren().add(tile(NeoText.get("menu.quest"),
                NeoText.get("menu.quest.desc"),
                questNote(), true, actions::quest));

        // El torneo (la auditoría del motor C6): es una pregunta distinta de las
        // demas — "quiero un cuadro de eliminacion directa con MI mazo,
        // contra rivales que el motor se inventa" — y no una opcion de otra
        // casilla.
        modes.getChildren().add(tile(NeoText.get("menu.tournament"),
                NeoText.get("menu.tournament.desc"),
                tournamentNote(), true, actions::tournament));

        modes.getChildren().add(formatTile(NeoFormat.TINY_LEADERS, actions));

        // "Otros formatos": Modern, Pioneer, Pauper... Responden la MISMA
        // pregunta que las casillas de arriba ("elige un mazo y juega"), asi
        // que no se meten una por una — quince casillas mas y el menu deja de
        // ser un menu (la auditoría del motor §2). Una casilla agrupa, y de ahi a la
        // pantalla de mazos de siempre.
        modes.getChildren().add(tile(NeoText.get("menu.otherFormats"),
                NeoText.get("menu.otherFormats.desc"),
                NeoText.get("menu.otherFormats.note"), true, actions::otherFormats));

        // El draft tampoco se entra eligiendo mazo: el mazo lo montas tu
        // abriendo sobres, y despues lo llevas hasta que se rompe.
        modes.getChildren().add(tile(NeoText.get("menu.draft"),
                NeoText.get("menu.draft.desc"),
                draftNote(), true, actions::draft));

        // El sellado: el mismo evento que el draft, pero sin pasar sobres.
        // Va justo al lado porque es la misma decision — "quiero jugar con lo
        // que me toque" — y quien conoce uno reconoce el otro.
        modes.getChildren().add(tile(NeoText.get("menu.sealed"),
                NeoText.get("menu.sealed.desc"),
                sealedNote(), true, actions::sealed));

        // Brawl y Oathbreaker, DESPUES de aventura y torneo: se juegan menos
        // (decision del autor, misma fecha).
        modes.getChildren().add(formatTile(NeoFormat.BRAWL, actions));
        modes.getChildren().add(formatTile(NeoFormat.OATHBREAKER, actions));

        // La partida privada. Va con los modos y no escondida en Ajustes: es
        // una forma de jugar, no una opcion. Y dice de cuantos a cuantos, que
        // es lo primero que se pregunta quien va a llamar a sus amigos.
        modes.getChildren().add(tile(NeoText.get("menu.online"),
                NeoText.get("menu.online.desc"),
                NeoText.get("menu.online.note"), true, actions::online));

        // Los puzzles son un modo propio: no hay mazo que elegir, son
        // situaciones preparadas con un objetivo. Forge trae cientos.
        modes.getChildren().add(tile(NeoText.get("menu.puzzles"), NeoText.get("menu.puzzles.desc"),
                NeoText.get("menu.puzzles.note"), true, actions::puzzles));

        // Personalizar no es un modo de juego, pero va aqui y no escondido en
        // Ajustes: lo que se toca ahi se VE, y lo que se ve se busca en la
        // primera pantalla.
        modes.getChildren().add(tile(NeoText.get("menu.look"),
                NeoText.get("menu.look.desc"),
                lookNote(), true, actions::look));

        // Los logros tampoco son un modo: son el registro de lo que ya has
        // hecho. Van aqui porque es donde se vuelve al terminar de jugar.
        modes.getChildren().add(tile(NeoText.get("menu.achievements"),
                NeoText.get("menu.achievements.desc"),
                achievementNote(), true, actions::achievements));
        // Todo lo de arriba va DENTRO de un visor con desplazamiento.
        //
        // El menu cabia justo, y al anyadir el logo dejo de caber: la fila de
        // idiomas se comia por arriba o el pie por abajo, segun como se
        // maquetara. Recortar sin salida es mentir sobre lo que hay (regla 5 de
        // los principios de interfaz), asi que en vez de apretar los margenes
        // — que volveria a romperse con la siguiente casilla o al subir la
        // escala — se deja bajar con la rueda.
        final VBox centre = new VBox(top, modes);
        centre.setAlignment(Pos.CENTER);

        final ScrollPane scroll = new ScrollPane(centre);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setCenter(scroll);

        final Button settings = new Button(NeoText.get("common.settings"));
        settings.getStyleClass().add("btn-secondary");
        settings.setOnAction(e -> actions.settings());

        final Button quit = new Button(NeoText.get("common.quit"));
        quit.getStyleClass().add("btn-secondary");
        quit.setOnAction(e -> actions.quit());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, gap, settings, quit);
        footer.getStyleClass().add("home-footer");
        footer.setPadding(new Insets(16, 30, 22, 30));
        footer.setAlignment(Pos.CENTER_RIGHT);
        setBottom(footer);
    }

    /**
     * La fila de idiomas de la primera pantalla.
     *
     * <p>Cambiar de idioma pide reiniciar — los nombres de carta se precargan
     * antes de leer las cartas, ver {@link forge.neo.NeoLanguage} — asi que en
     * vez de un aviso que hay que entender, la casilla elegida se marca y lo
     * dice con el icono. Es la unica pantalla donde el idioma se puede tocar
     * ANTES de que importe.
     */
    private static Region languageBar() {
        final java.util.List<forge.neo.NeoLanguage.Option> langs =
                forge.neo.NeoLanguage.available();
        final String current = forge.neo.NeoLanguage.current();

        final Label globe = new Label(NeoText.get("menu.language"));
        globe.getStyleClass().add("caption");

        final javafx.scene.layout.FlowPane row = new javafx.scene.layout.FlowPane(4, 4);
        row.setAlignment(Pos.CENTER_LEFT);

        final Label note = new Label();
        note.getStyleClass().add("home-subtitle");
        note.setVisible(false);
        note.setManaged(false);

        for (final forge.neo.NeoLanguage.Option option : langs) {
            final Button b = new Button(option.getLabel());
            b.getStyleClass().add("segment");
            // La bandera AL LADO del nombre, no en vez de el. Sola no basta: a
            // este tamanyo hay banderas que se parecen mucho, y el nombre
            // escrito en su propio idioma sigue siendo lo que identifica de
            // verdad. La bandera es lo que hace que la fila se recorra con la
            // vista en vez de leyendola. Ver FlagIcon.
            final javafx.scene.Node flag = FlagIcon.of(option.getId(), 12);
            if (flag != null) {
                b.setGraphic(flag);
                b.setGraphicTextGap(6);
            }
            // Sin esto JavaFX los encoge por debajo de su texto en cuanto la
            // fila va justa, y salen botones que ponen "..." y nada mas.
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.pseudoClassStateChanged(SELECTED, option.getId().equals(current));
            b.setOnAction(e -> {
                forge.neo.NeoLanguage.set(option.getId());
                for (final javafx.scene.Node n : row.getChildren()) {
                    n.pseudoClassStateChanged(SELECTED, n == b);
                }
                note.setText(NeoText.get("menu.language.restart", option.getLabel()));
                note.setVisible(true);
                note.setManaged(true);
            });
            row.getChildren().add(b);
        }

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox bar = new HBox(12, globe, row, gap, note);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 30, 6, 30));
        return bar;
    }

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /**
     * La letra pequenya del tutorial.
     *
     * <p>Mientras quede algo por hacer dice para que sirve — no cuantas
     * lecciones hay, que a quien acaba de llegar no le dice nada. Cuando ya
     * estan las tres, pasa a decir eso, porque entonces la pregunta que se hace
     * quien lo mira es otra: "esto ya lo hice?".
     */
    private static String tutorialNote() {
        return forge.neo.tutorial.NeoTutorial.allDone()
                ? NeoText.get("menu.tutorial.done")
                : NeoText.get("menu.tutorial.note");
    }

    /** Lo que hay puesto ahora mismo, para que la casilla diga algo util. */
    private static String lookNote() {
        final String name = forge.neo.look.NeoLook.playerName();
        return name.isBlank() ? forge.neo.NeoText.get("menu.look.note")
                : forge.neo.NeoText.get("menu.look.noteName", name);
    }

    /**
     * Cuantos logros llevas, en la propia casilla.
     *
     * <p>Es el dato que hace que se entre: un "34 de 412" invita a mirar, y
     * "Logros" a secas no dice si hay algo dentro.
     */
    private static String achievementNote() {
        final int[] totals = forge.neo.NeoAchievements.totals(
                forge.neo.NeoAchievements.all());
        return NeoText.get("menu.achievements.note", totals[0], totals[1]);
    }

    /** Si hay una aventura empezada, la casilla dice cuantas tienes. */
    private static String questNote() {
        final java.util.List<String> saves = forge.neo.quest.NeoQuest.saves();
        if (saves.isEmpty()) {
            return NeoText.get("menu.quest.none");
        }
        return saves.size() == 1
                ? NeoText.get("menu.quest.one")
                : NeoText.get("menu.quest.many", saves.size());
    }

    /** Si hay un draft a medias, la casilla lo dice en vez de invitar a otro. */
    private static String sealedNote() {
        final forge.neo.draft.DraftRun run =
                forge.neo.draft.DraftRun.current(forge.neo.draft.DraftRun.Kind.SEALED);
        if (run == null) {
            return NeoText.get("menu.sealed.none");
        }
        return NeoText.get("menu.draft.running", run.getWins(), run.getLosses());
    }

    private static String draftNote() {
        final forge.neo.draft.DraftRun run = forge.neo.draft.DraftRun.current();
        if (run == null) {
            return NeoText.get("menu.draft.none");
        }
        return NeoText.get("menu.draft.running", run.getWins(), run.getLosses());
    }

    /** Si hay un torneo a medias o recien terminado, la casilla lo dice. */
    private static String tournamentNote() {
        final forge.neo.tournament.NeoTournament t = forge.neo.tournament.NeoTournament.current();
        if (t == null) {
            return NeoText.get("menu.tournament.none");
        }
        if (t.isOver()) {
            return NeoText.get("menu.tournament.done");
        }
        return NeoText.get("menu.tournament.running", t.currentRoundIndex() + 1, t.totalRounds());
    }

    /**
     * La tarjeta de un {@link NeoFormat} — cuantos mazos tienes de ese
     * formato, en la propia casilla.
     *
     * <p>Antes esto salia de un bucle sobre {@code NeoFormat.values()} en el
     * orden del enum; ahora el orden lo decide esta pantalla a mano (aventura
     * y torneo promovidos por delante de Brawl/Oathbreaker), asi que cada
     * formato se coloca donde toca con su propia llamada.
     */
    private static Region formatTile(final NeoFormat format, final Actions actions) {
        final int count = format.decks().size();
        return tile(format.getLabel(), format.getDescription(),
                count == 0 ? NeoText.get("menu.noDecks") : NeoText.get("menu.deckCount", count),
                true, () -> actions.play(format));
    }

    /**
     * Una tarjeta de modo.
     *
     * <p>Se entra a un formato aunque no tengas ningun mazo suyo: la pantalla de
     * mazos es tambien donde se monta el primero, asi que cerrarla dejaria sin
     * salida a quien empieza de cero.
     *
     * @param enabled false para un modo que de verdad no se pueda usar
     */
    private static Region tile(final String name, final String description,
                               final String footnote, final boolean enabled,
                               final Runnable action) {
        final Label title = new Label(name);
        title.getStyleClass().add("mode-tile-name");

        final Label desc = new Label(description);
        desc.getStyleClass().add("mode-tile-desc");
        desc.setWrapText(true);

        final Label note = new Label(footnote);
        note.getStyleClass().add("mode-tile-note");

        final VBox box = new VBox(6, title, desc, note);
        box.getStyleClass().add("mode-tile");
        box.setAlignment(Pos.TOP_LEFT);
        box.setPadding(new Insets(18, 20, 16, 20));
        box.setPrefWidth(270);
        box.setMinHeight(140);

        if (enabled) {
            box.setOnMouseClicked(e -> action.run());
        } else {
            box.setDisable(true);
        }
        return box;
    }
}
