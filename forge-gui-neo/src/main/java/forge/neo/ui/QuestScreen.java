package forge.neo.ui;

import forge.neo.NeoText;
import java.util.List;
import java.util.Map;

import forge.deck.Deck;
import forge.game.card.CardView;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.gamemodes.quest.QuestEventChallenge;
import forge.gamemodes.quest.QuestEventDuel;
import forge.item.PaperCard;
import forge.neo.card.CardNode;
import forge.neo.quest.DuelFace;
import forge.neo.quest.NeoQuest;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * El cuartel general de la aventura.
 *
 * <p>Es la pantalla a la que se vuelve entre duelo y duelo, asi que tiene que
 * contestar de un vistazo a cuatro cosas y en este orden:
 *
 * <ol>
 *   <li><b>Como voy</b> — creditos, victorias, cartas, rango.</li>
 *   <li><b>Contra quien estoy jugando</b> — el nivel de los rivales y cuanto
 *       falta para que suban. Es lo que hace que se NOTE el progreso: sin esto,
 *       ganar veinte duelos no se distingue de ganar dos.</li>
 *   <li><b>Con que juego</b> — el mazo, y si es legal.</li>
 *   <li><b>Que puedo hacer ahora</b> — los cuatro duelos, con su dificultad.</li>
 * </ol>
 *
 * <p>La dificultad va por color y no solo por texto: verde facil, ambar medio,
 * rojo duro, morado experto. Es la misma regla que la mesa — el estado se ve, no
 * se lee.
 */
public class QuestScreen extends BorderPane {

    /** Lo que se puede hacer desde el cuartel general. */
    public interface Actions {
        void duel(QuestEventDuel duel);

        void challenge(QuestEventChallenge challenge);

        void deckPicker();

        void collection();

        /** Editar el mazo con el que juegas ahora. */
        void editDeck();

        /** Montar uno nuevo con lo que tienes. */
        void newDeck();

        void shop();

        void bazaar();

        void newAdventure();

        void back();
    }

    private final double cardWidth;

    public QuestScreen(final double cardWidth, final Actions actions) {
        this.cardWidth = cardWidth;
        getStyleClass().addAll("table-root", "quest");

        setTop(header(actions));
        setCenter(body(actions));

        // Click derecho sobre cualquier carta: a tamanyo de lectura, igual que
        // en la mesa. Aqui las cartas son pequenyas — el comandante del rival
        // ocupa un dedo — y decidir contra quien juegas sin poder leer su carta
        // es decidir a ciegas.
        CardZoom.install(this);
    }

    // ---------------------------------------------------------------
    // Cabecera: quien eres y como vas
    // ---------------------------------------------------------------

    private Region header(final Actions actions) {
        final Label title = new Label(NeoText.get("quest.title", safe(NeoQuest.name())));
        title.getStyleClass().add("home-title");

        final Label rank = new Label(NeoQuest.rank()
                + "   ·   " + NeoQuest.modalidad().getLabel());
        rank.getStyleClass().add("home-subtitle");

        final Button nueva = new Button(NeoText.get("quest.new"));
        nueva.getStyleClass().add("btn-secondary");
        nueva.setMinWidth(Region.USE_PREF_SIZE);
        nueva.setOnAction(e -> actions.newAdventure());

        final Button back = new Button(NeoText.get("quest.menu"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(10, new VBox(2, title, rank), gap, nueva, back);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(20, 28, 6, 30));
        return row;
    }

    private Region body(final Actions actions) {
        final VBox content = new VBox(16);
        content.setPadding(new Insets(8, 30, 22, 30));
        content.getChildren().addAll(
                stats(actions),
                deckRow(actions),
                duels(actions),
                challenges(actions));

        final ScrollPane sp = new ScrollPane(content);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    /** Los numeros grandes, y el panel de "contra quien juegas". */
    private Region stats(final Actions actions) {
        final HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(
                tile(String.valueOf(NeoQuest.credits()), NeoText.get("quest.credits"), "stat-credits"),
                tile(NeoQuest.wins() + " - " + NeoQuest.losses(), NeoText.get("quest.record"), null),
                // El numero de cartas ES el boton de la coleccion. Si algo
                // grande dice "146 cartas en tu coleccion", se clica: la misma
                // regla que ya se aplico a las pilas de la mesa.
                clickable(tile(String.valueOf(NeoQuest.collectionSize()),
                        NeoText.get("quest.collectionSize"), null), actions::collection),
                tile(String.valueOf(NeoQuest.life()), NeoText.get("quest.duelLife"), null));

        final Region grow = tierPanel();
        HBox.setHgrow(grow, Priority.ALWAYS);
        row.getChildren().add(grow);
        return row;
    }

    /**
     * Hace que una casilla de numero se pueda clicar.
     *
     * <p>Sale de jugar: <i>"donde pone COLECCION en grande deberia abrirse la
     * coleccion igual que con el boton"</i>. Y tiene razon — un numero grande
     * con su etiqueta parece un boton, asi que serlo cuesta una linea y no
     * serlo despista. El boton de abajo se queda: esto no lo sustituye, lo
     * duplica donde el ojo ya estaba mirando.
     */
    private static Region clickable(final Region tile, final Runnable action) {
        tile.getStyleClass().add("stat-tile-link");
        tile.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                action.run();
            }
        });
        return tile;
    }

    private static Region tile(final String value, final String caption, final String extra) {
        final Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        if (extra != null) {
            v.getStyleClass().add(extra);
        }
        final Label c = new Label(caption);
        c.getStyleClass().add("caption");
        final VBox box = new VBox(2, v, c);
        box.getStyleClass().add("stat-tile");
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(12, 18, 10, 18));
        box.setMinWidth(140);
        return box;
    }

    /**
     * Contra que clase de rival juegas, y cuanto falta para el siguiente escalon.
     *
     * <p><b>Esta es la pieza que hace que la aventura se sienta a aventura.</b>
     * El motor sube el nivel de los rivales con tus victorias; si eso no se ve,
     * el jugador no tiene forma de saber que esta progresando — solo nota, un
     * dia, que le cuesta mas.
     */
    private Region tierPanel() {
        final QuestEventDifficulty tier = NeoQuest.tier();
        final int falta = NeoQuest.winsToNextTier();

        final Label label = new Label(NeoText.get("quest.rivals",
                NeoQuest.tierLabel().toUpperCase()));
        label.getStyleClass().addAll("tier-label", styleFor(tier));

        final Label detail = new Label(falta == 0
                ? NeoText.get("quest.topTier")
                : NeoText.get(falta == 1 ? "quest.toNextTier.one" : "quest.toNextTier", falta));
        detail.getStyleClass().add("home-subtitle");

        final VBox box = new VBox(4, label, detail);
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(12, 18, 10, 18));

        if (falta > 0) {
            // La barra se llena DENTRO del escalon: cuenta desde la ultima
            // subida, no desde cero, que es lo que el jugador quiere saber.
            final int step = Math.max(1, falta + progressInTier());
            final ProgressBar bar = new ProgressBar(progressInTier() / (double) step);
            bar.getStyleClass().addAll("tier-bar", styleFor(tier));
            bar.setMaxWidth(Double.MAX_VALUE);
            box.getChildren().add(bar);
        }
        return box;
    }

    /** Victorias conseguidas dentro del escalon actual. */
    private static int progressInTier() {
        return Math.max(0, NeoQuest.wins() - NeoQuest.tierStartedAt());
    }

    private static String styleFor(final QuestEventDifficulty d) {
        if (d == null) {
            return "tier-easy";
        }
        switch (d) {
            case MEDIUM: return "tier-medium";
            case HARD: return "tier-hard";
            case EXPERT: return "tier-expert";
            default: return "tier-easy";
        }
    }

    // ---------------------------------------------------------------
    // Tu mazo
    // ---------------------------------------------------------------

    private Region deckRow(final Actions actions) {
        final Deck deck = NeoQuest.currentDeck();
        final String problem = NeoQuest.problemWith(deck);

        final Label caption = new Label(NeoText.get("quest.yourDeck"));
        caption.getStyleClass().add("caption");

        final Label name = new Label(deck == null ? NeoText.get("quest.noDeckShort") : deck.getName());
        name.getStyleClass().add("quest-deck-name");

        final Label state = new Label(problem == null
                ? NeoText.get("quest.deckReady", deck.getMain().countAll())
                : problem);
        state.getStyleClass().add(problem == null ? "quest-ok" : "quest-problem");
        state.setWrapText(true);
        state.setMaxWidth(520);

        final Button change = new Button(NeoText.get("quest.changeDeck"));
        change.getStyleClass().add("btn-secondary");
        change.setMinWidth(Region.USE_PREF_SIZE);
        change.setOnAction(e -> actions.deckPicker());

        // Editar y crear, aqui mismo. El mazo de la aventura se toca MUCHO —
        // cada sobre que abres es un motivo para volver — asi que mandar a otra
        // pantalla a buscarlo seria el camino largo de la cosa mas frecuente.
        final Button edit = new Button(NeoText.get("quest.editDeck"));
        edit.getStyleClass().add("btn-secondary");
        edit.setMinWidth(Region.USE_PREF_SIZE);
        edit.setDisable(deck == null);
        edit.setOnAction(e -> actions.editDeck());

        final Button create = new Button(NeoText.get("home.newDeck"));
        create.getStyleClass().add("btn-secondary");
        create.setMinWidth(Region.USE_PREF_SIZE);
        create.setOnAction(e -> actions.newDeck());

        final Button collection = new Button(NeoText.get("quest.collection"));
        collection.getStyleClass().add("btn-secondary");
        collection.setMinWidth(Region.USE_PREF_SIZE);
        collection.setOnAction(e -> actions.collection());

        // Secundario a proposito: en esta pantalla la accion principal es
        // JUGAR un duelo, y el boton destacado tiene que ser uno solo.
        final Button shop = new Button(NeoText.get("quest.shop"));
        shop.getStyleClass().add("btn-secondary");
        shop.setMinWidth(Region.USE_PREF_SIZE);
        shop.setOnAction(e -> actions.shop());

        // El bazar es la OTRA forma de gastar creditos, y la que no da cartas.
        // Va al lado de la tienda porque es la misma decision: en que me gasto
        // lo que he ganado.
        final Button bazaar = new Button(NeoText.get("quest.bazaar"));
        bazaar.getStyleClass().add("btn-secondary");
        bazaar.setMinWidth(Region.USE_PREF_SIZE);
        bazaar.setOnAction(e -> actions.bazaar());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        final VBox texts = new VBox(2, caption, name, state);
        final HBox row = new HBox(10, strip(deck, cardWidth * 0.58), texts, gap,
                edit, create, change, collection, shop, bazaar);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("stat-tile");
        row.setPadding(new Insets(12, 18, 12, 18));
        return row;
    }

    /** Un mazo como tira de cartas solapadas. Sin tierras: no dicen nada. */
    private static Region strip(final Deck deck, final double w) {
        final HBox row = new HBox(-w * 0.5);
        row.setAlignment(Pos.CENTER_LEFT);
        if (deck != null) {
            int shown = 0;
            for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
                if (e.getKey().getRules() != null && e.getKey().getRules().getType().isLand()) {
                    continue;
                }
                final CardNode node = new CardNode(w);
                node.setCard(CardView.getCardForUi(e.getKey()));
                node.setRotationEnabled(false);
                row.getChildren().add(node);
                if (++shown >= 6) {
                    break;
                }
            }
        }
        row.setMinWidth(Region.USE_PREF_SIZE);
        return row;
    }

    // ---------------------------------------------------------------
    // Los duelos
    // ---------------------------------------------------------------

    private Region duels(final Actions actions) {
        final Label caption = new Label(NeoText.get("quest.duels"));
        caption.getStyleClass().add("caption");

        final HBox row = new HBox(14);
        row.setAlignment(Pos.TOP_LEFT);

        final List<QuestEventDuel> duels = NeoQuest.duels();
        final boolean canPlay = NeoQuest.problemWith(NeoQuest.currentDeck()) == null;
        for (final QuestEventDuel duel : duels) {
            final Region tile = duelTile(duel, canPlay, actions);
            HBox.setHgrow(tile, Priority.ALWAYS);
            row.getChildren().add(tile);
        }
        if (duels.isEmpty()) {
            final Label none = new Label(NeoText.get("quest.noDuels"));
            none.getStyleClass().add("home-subtitle");
            row.getChildren().add(none);
        }
        return new VBox(6, caption, row);
    }

    /**
     * Un duelo.
     *
     * <p>Se ensenya <b>la carta del rival</b>: saber que te vas a enfrentar a
     * Vrondiss dice mucho mas que leer su nombre, y es lo que hace que elegir
     * duelo sea una decision y no un sorteo.
     *
     * <p><b>Pero solo es su comandante en una aventura de Commander.</b> En una
     * de Estandar los duelos salen de {@code res/quest/duels}, donde el
     * {@code Title} es el <b>personaje</b> del mazo — "Ajani Goldmane",
     * "Chandra Nalaar" — que casi siempre existe como carta pero no es ningun
     * comandante: ese formato no tiene zona de mando. Poner "SU COMANDANTE"
     * encima era afirmar algo falso, y encima algo que el jugador sabe que es
     * falso porque acaba de elegir Estandar.
     *
     * <p>Y cuando el personaje <b>no</b> es una carta ("King Goldemar", "Bamm
     * Bamm Rubble") se ensenya la mejor carta de su mazo — ver
     * {@link #faceOf}. El unico que se queda con el interrogante es el
     * <b>duelo sorpresa</b>, y ahi si tiene sentido: es lo que se paga por que
     * pueda tocarte cualquiera.
     */
    private Region duelTile(final QuestEventDuel duel, final boolean canPlay,
                            final Actions actions) {
        final VBox box = new VBox(8);
        box.getStyleClass().addAll("duel-tile", styleFor(duel.getDifficulty()));
        box.setPadding(new Insets(14));
        box.setAlignment(Pos.TOP_CENTER);
        box.setMinWidth(190);

        final Label badge = new Label(duel.showDifficulty()
                ? difficultyLabel(duel.getDifficulty()) : NeoText.get("quest.surprise"));
        badge.getStyleClass().addAll("duel-badge", styleFor(duel.getDifficulty()));

        final Label name = new Label(duel.getTitle());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(200);
        name.setMinHeight(Region.USE_PREF_SIZE);

        box.getChildren().add(badge);

        // Al duelo sorpresa no se le pregunta: su gracia es no saber contra
        // quien juegas, y ademas su mazo es el de OTRO rival prestado
        // (getRandomOpponent copia el eventDeck del que le toque barajando),
        // asi que ensenyar una carta suya seria mentir.
        final CardView face = duel.showDifficulty() ? faceOf(duel) : null;

        // La etiqueta va en LAS CUATRO casillas, tengan carta o no. Con la
        // carta a secas se confunde con un premio ("pensaba que era la carta
        // que ganaba si le gano"), y poniendola solo donde hay carta las otras
        // quedaban un peldanyo mas arriba y la fila entera se veia rota.
        final Label against = new Label(NeoText.get(NeoQuest.isActive()
                && NeoQuest.modalidad() == NeoQuest.Modalidad.COMMANDER
                ? "quest.theirCommander" : "quest.theirRival"));
        against.getStyleClass().add("caption");
        box.getChildren().add(against);
        if (face != null) {
            final CardNode node = new CardNode(cardWidth * 0.95);
            node.setRotationEnabled(false);
            node.setCard(face);
            box.getChildren().add(node);
        } else {
            // Sin carta que ensenyar el hueco SI se ocupa igual: una casilla a
            // media altura al lado de otras tres desnivela la fila.
            final Label unknown = new Label("?");
            unknown.getStyleClass().add("duel-unknown");
            unknown.setAlignment(Pos.CENTER);
            unknown.setPrefSize(cardWidth * 0.95, cardWidth * 0.95 * CardNode.ASPECT);
            box.getChildren().add(unknown);
        }
        box.getChildren().add(name);

        final Button play = new Button(NeoText.get("home.play"));
        play.getStyleClass().add("btn-primary");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setDisable(!canPlay);
        play.setOnAction(e -> actions.duel(duel));
        box.getChildren().add(play);
        return box;
    }

    // ---------------------------------------------------------------
    // Los desafios
    // ---------------------------------------------------------------

    /**
     * Los desafios abiertos.
     *
     * <p>Un desafio no es un duelo mas: es una <b>situacion preparada</b> con
     * su propio guion — el rival puede empezar con 25 o 40 vidas y con cartas
     * ya en la mesa, y tu tambien. Forge trae 37 y llevaban desde el primer dia
     * sin abrirse.
     *
     * <p>La seccion se ensenya <b>tambien cuando esta vacia</b>, y ahi esta la
     * gracia: dice cuantas victorias faltan para el siguiente. "No hay
     * desafios" a secas parece que el modo no los tiene; "te faltan 3
     * victorias" es una razon para seguir jugando.
     */
    private Region challenges(final Actions actions) {
        final Label caption = new Label(NeoText.get("quest.challenges"));
        caption.getStyleClass().add("caption");

        final List<QuestEventChallenge> open = NeoQuest.challenges();
        final boolean canPlay = NeoQuest.problemWith(NeoQuest.currentDeck()) == null;

        final javafx.scene.layout.FlowPane row = new javafx.scene.layout.FlowPane(14, 14);
        row.setAlignment(Pos.TOP_LEFT);
        for (final QuestEventChallenge c : open) {
            row.getChildren().add(challengeTile(c, canPlay, actions));
        }
        if (open.isEmpty()) {
            final int missing = NeoQuest.winsToNextChallenge();
            final Label none = new Label(missing > 0
                    ? NeoText.get("quest.nextChallenge", missing)
                    : NeoText.get("quest.noChallenges"));
            none.getStyleClass().add("home-subtitle");
            none.setWrapText(true);
            row.getChildren().add(none);
        }
        return new VBox(6, caption, row);
    }

    /**
     * Un desafio.
     *
     * <p>Lleva lo que hace falta para decidir si entrar: su descripcion (que la
     * escribe el fichero y explica la situacion), la vida del rival cuando no
     * es la normal, la recompensa, y <b>si se puede repetir</b>. Lo ultimo
     * importa mas de lo que parece: la mayoria se juegan UNA vez en toda la
     * aventura, y eso hay que saberlo ANTES de entrar con el mazo a medio
     * montar.
     */
    private Region challengeTile(final QuestEventChallenge c, final boolean canPlay,
                                 final Actions actions) {
        final VBox box = new VBox(6);
        box.getStyleClass().addAll("duel-tile", styleFor(c.getDifficulty()));
        box.setPadding(new Insets(14));
        box.setAlignment(Pos.TOP_LEFT);
        box.setPrefWidth(330);
        box.setMinWidth(300);

        final Label badge = new Label(difficultyLabel(c.getDifficulty()));
        badge.getStyleClass().addAll("duel-badge", styleFor(c.getDifficulty()));

        final Label name = new Label(safe(c.getTitle()));
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(300);
        name.setMinHeight(Region.USE_PREF_SIZE);

        final Label desc = new Label(safe(c.getDescription()));
        desc.getStyleClass().add("home-subtitle");
        desc.setWrapText(true);
        desc.setMaxWidth(300);
        desc.setMinHeight(Region.USE_PREF_SIZE);

        // Lo que hace distinto a ESTE desafio, en una linea. La vida del rival
        // solo se dice si no es la normal: repetir "20 vidas" en los treinta
        // que la tienen es ruido.
        final StringBuilder facts = new StringBuilder();
        final int life = c.getAILife();
        if (life > 0) {
            facts.append(NeoText.get("quest.challenge.life", life));
        }
        if (c.getCreditsReward() > 0) {
            if (facts.length() > 0) {
                facts.append("  ·  ");
            }
            facts.append(NeoText.get("quest.challenge.reward", c.getCreditsReward()));
        }
        if (!c.isRepeatable()) {
            if (facts.length() > 0) {
                facts.append("  ·  ");
            }
            facts.append(NeoText.get("quest.challenge.once"));
        }
        final Label info = new Label(facts.toString());
        info.getStyleClass().add("caption");
        info.setWrapText(true);
        info.setMaxWidth(300);
        info.setMinHeight(Region.USE_PREF_SIZE);

        final Button play = new Button(NeoText.get("home.play"));
        play.getStyleClass().add("btn-primary");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setDisable(!canPlay);
        play.setOnAction(e -> actions.challenge(c));

        box.getChildren().addAll(badge, name, desc, info, play);
        return box;
    }

    /**
     * La carta con la que se presenta a un rival.
     *
     * <p>Cual es y por que la elige asi lo cuenta {@link DuelFace}, que vive en
     * {@code forge.neo.quest} y no en la pantalla a proposito: eso permite
     * recorrer los 1.145 duelos sin ventana y comprobar que a ninguno le falta
     * cara ({@code run.cmd questcheck}).
     */
    private static CardView faceOf(final QuestEventDuel duel) {
        final PaperCard pc = DuelFace.of(duel);
        return pc == null ? null : CardView.getCardForUi(pc);
    }

    private static String difficultyLabel(final QuestEventDifficulty d) {
        if (d == null) {
            return NeoText.get("quest.diff.easy");
        }
        switch (d) {
            case MEDIUM: return NeoText.get("quest.diff.medium");
            case HARD: return NeoText.get("quest.diff.hard");
            case EXPERT: return NeoText.get("quest.diff.expert");
            case WILD: return NeoText.get("quest.diff.wild");
            default: return NeoText.get("quest.diff.easy");
        }
    }

    private static String safe(final String s) {
        return s == null ? "" : s;
    }

    /** Las imagenes llegan de Scryfall en segundo plano: hay que repedirlas. */
    public void refreshArt() {
        CardNode.refreshAllIn(this);
    }
}
