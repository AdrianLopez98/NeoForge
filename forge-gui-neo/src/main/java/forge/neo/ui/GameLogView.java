package forge.neo.ui;

import forge.neo.NeoText;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import forge.game.GameLog;
import forge.game.GameLogEntry;
import forge.game.GameLogEntryType;
import forge.game.card.CardView;
import forge.game.player.PlayerView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Todo lo que ha pasado en la partida, turno por turno.
 *
 * <p>Nace de un problema real jugando: <i>"de repente me han matado una criatura
 * y no se ni como, porque va todo rapidisimo"</i>. Cuando el rival hace algo a
 * lo que no puedes responder, el motor ni te da prioridad: la mesa cambia sola y
 * no queda rastro.
 *
 * <p>Y resulta que <b>el motor ya lo estaba anotando todo</b>
 * ({@code GameView.getGameLog()}). Lo estabamos tirando. Es el patron que se
 * repite en todo el proyecto: la informacion ya esta, solo hay que ensenyarla.
 *
 * <p>Dos decisiones de presentacion:
 *
 * <ul>
 *   <li><b>Agrupado por turnos</b>, con su cabecera. Un rio de lineas sin cortes
 *       no se puede leer hacia atras, que es justo para lo que sirve esto.</li>
 *   <li><b>Lo tuyo, en negrita.</b> En una partida a cuatro la mayoria de lineas
 *       son de otros; lo que te importa es lo que te toca a ti o a tus cartas, y
 *       tiene que saltar a la vista sin leer todo.</li>
 *   <li><b>De arriba abajo, en orden.</b> El turno 1 arriba y lo ultimo abajo,
 *       y se abre mirando el final. Antes los turnos iban del reves — lo
 *       reciente primero — para no tener que bajar; pero dentro de cada turno
 *       si se leia hacia adelante, asi que el registro cambiaba de sentido
 *       cada pocas lineas y no habia forma de seguir una secuencia. Reportado
 *       probando el juego el 21-09-2026.</li>
 *   <li><b>Se puede copiar entero</b>, como en el Forge de siempre: para
 *       pegarlo en un informe de fallo o repasar la partida fuera.</li>
 * </ul>
 */
public class GameLogView extends VBox {

    /**
     * Que entradas se ensenyan.
     *
     * <p>PHASE y MANA se quedan fuera a proposito: cambian varias veces por
     * turno, no explican nada y ahogarian lo unico que importa, que es "por que
     * se ha muerto mi criatura".
     */
    private static final Set<GameLogEntryType> INTERESTING = EnumSet.of(
            GameLogEntryType.STACK_ADD,
            GameLogEntryType.STACK_RESOLVE,
            GameLogEntryType.ZONE_CHANGE,
            GameLogEntryType.DAMAGE,
            GameLogEntryType.LIFE,
            GameLogEntryType.COMBAT,
            GameLogEntryType.DISCARD,
            GameLogEntryType.LAND,
            GameLogEntryType.EFFECT_REPLACED,
            GameLogEntryType.TURN,
            GameLogEntryType.MULLIGAN,
            GameLogEntryType.INFORMATION);

    private final VBox lines = new VBox(1);
    private final ScrollPane scroll = new ScrollPane(lines);

    /** El registro entero en texto plano: lo que se lleva el boton de copiar. */
    private String plain = "";

    private final Button copy = new Button(NeoText.get("log.copy"));

    public GameLogView(final Runnable onClose) {
        getStyleClass().addAll("dialog", "game-log");
        setSpacing(12);
        setPadding(new Insets(18, 20, 16, 20));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final Label heading = new Label(NeoText.get("log.title"));
        heading.getStyleClass().add("dialog-title");

        final Label hint = new Label(NeoText.get("log.hint"));
        hint.getStyleClass().add("dialog-text");

        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportWidth(620);
        scroll.setPrefViewportHeight(460);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Abrir mirando el final, que es lo que se viene a leer: "que acaba de
        // pasar". Se engancha al alto de las lineas y no a un runLater porque
        // el ScrollPane no sabe cuanto puede bajar hasta que ha repartido su
        // contenido, y eso ocurre DESPUES de update(). Es el mismo apanyo que
        // usa el chat de red (NetChatView).
        lines.heightProperty().addListener((o, was, is) -> scroll.setVvalue(1.0));

        copy.getStyleClass().add("btn-secondary");
        copy.setOnAction(e -> {
            final javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString(plain);
            javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
            // El portapapeles no se ve: sin acuse no hay forma de saber si el
            // boton ha hecho algo. Vuelve a su texto solo, a los dos segundos.
            copy.setText(NeoText.get("log.copied"));
            final javafx.animation.PauseTransition back =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            back.setOnFinished(ev -> copy.setText(NeoText.get("log.copy")));
            back.play();
        });

        final Button close = new Button(NeoText.get("common.close"));
        close.getStyleClass().add("btn-primary");
        close.setOnAction(e -> onClose.run());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(10, copy, gap, close);
        footer.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading, hint, scroll, footer);
    }

    /**
     * Vuelca el registro entero.
     *
     * <p>Se reconstruye de golpe en vez de ir anyadiendo porque esto se abre a
     * peticion y se cierra: no esta en pantalla mientras se juega, asi que el
     * coste da igual y el codigo es mucho mas simple.
     *
     * @param me el jugador de abajo, para saber que resaltar
     */
    public void update(final GameLog log, final PlayerView me) {
        lines.getChildren().clear();
        if (log == null) {
            return;
        }

        // OJO: getAllEntries() ya viene en ORDEN DE INSERCION, o sea lo mas
        // viejo primero — el que devuelve lo reciente primero es getLogEntries.
        // Aqui habia un reverse() por esa confusion, y luego otro sobre los
        // grupos que lo tapaba a medias: los turnos salian en orden pero las
        // lineas de dentro al reves. Se lee tal cual.
        final List<GameLogEntry> all = log.getAllEntries();

        // El nombre DE PARTIDA, no el del perfil: las lineas del registro las
        // escribe el motor con Player.toString(), que es el desempatado. Con
        // dos jugadores llamados igual, buscar el del perfil ponia en negrita
        // tambien lo que hacia el otro.
        final String myName = me == null ? null
                : forge.neo.match.PlayerName.of(me).toLowerCase(Locale.ROOT);

        // Se agrupa por turno (cabecera + sus lineas) y los turnos van en
        // ORDEN, el primero arriba y el ultimo abajo. Antes se daba la vuelta
        // a los grupos para no tener que bajar hasta el turno 80 — pero dentro
        // de cada turno se seguia leyendo hacia adelante, asi que el registro
        // cambiaba de sentido cada pocas lineas. El atajo de verdad es abrirlo
        // mirando el final, que es lo que hace el constructor.
        final StringBuilder text = new StringBuilder();
        boolean any = false;
        for (final GameLogEntry entry : all) {
            if (!INTERESTING.contains(entry.type())) {
                continue;
            }
            any = true;
            if (entry.type() == GameLogEntryType.TURN) {
                if (text.length() > 0) {
                    text.append(System.lineSeparator());
                }
                lines.getChildren().add(turnHeader(entry.message()));
            } else {
                lines.getChildren().add(line(entry, mine(entry, me, myName)));
            }
            text.append(entry.message()).append(System.lineSeparator());
        }

        plain = text.toString();
        copy.setDisable(!any);

        if (!any) {
            final Label empty = new Label(NeoText.get("log.empty"));
            empty.getStyleClass().add("home-subtitle");
            lines.getChildren().add(empty);
        }
    }

    /**
     * Si esta linea te toca a ti.
     *
     * <p>Se mira primero la carta que el motor adjunta a la entrada — que es lo
     * fiable — y solo si no hay carta se recurre a buscar tu nombre en el texto.
     */
    static boolean mine(final GameLogEntry entry, final PlayerView me, final String myName) {
        if (me == null) {
            return false;
        }
        final CardView card = entry.sourceCard();
        if (card != null) {
            if (me.equals(card.getController()) || me.equals(card.getOwner())) {
                return true;
            }
        }
        return myName != null && entry.message() != null
                && entry.message().toLowerCase(Locale.ROOT).contains(myName);
    }

    private static Region turnHeader(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("log-turn");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    private static Region line(final GameLogEntry entry, final boolean mine) {
        final Label l = new Label(entry.message());
        l.getStyleClass().add("log-line");
        l.getStyleClass().add(styleFor(entry.type()));
        if (mine) {
            l.getStyleClass().add("log-mine");
        }
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    /** Un color por clase de suceso: se distingue sin leer. */
    private static String styleFor(final GameLogEntryType type) {
        switch (type) {
            case DAMAGE:
            case LIFE:
                return "log-damage";
            case STACK_ADD:
            case STACK_RESOLVE:
                return "log-spell";
            case ZONE_CHANGE:
            case DISCARD:
                return "log-zone";
            default:
                return "log-other";
        }
    }
}
