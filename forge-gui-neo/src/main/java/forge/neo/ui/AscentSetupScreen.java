package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.game.card.CardView;
import forge.item.PaperCard;
import forge.neo.NeoText;
import forge.neo.ascent.AscentChallenges;
import forge.neo.ascent.AscentRun;
import forge.neo.ascent.AscentSeed;
import forge.neo.ascent.AscentSeedDeck;
import forge.neo.ascent.AscentUnlocks;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Montar una run: con que reglas, con que comandante y a que Ascension.
 *
 * <h2>Por que esta pantalla es obligatoria</h2>
 *
 * <p>Sin ella, la casilla del menu <b>empezaria una run al pulsarla</b>. Y de
 * una run no se vuelve atras: la que hubiera a medias se pierde, y quien entro
 * solo a mirar que era esto se encuentra con que ya ha empezado. Es el
 * principio 6 en estado puro — lo que no se puede deshacer hay que evitar que
 * pase.
 *
 * <h2>Tres preguntas, y ninguna de relleno</h2>
 *
 * <ol>
 *   <li><b>Estandar o Commander.</b> Cambian el mazo de salida, la vida (20 o
 *       40) y de que se puede premiar. Es la pregunta que mas cambia la run.
 *   <li><b>El comandante</b>, solo en Commander: lo eliges de 10.824 o sale uno
 *       al azar. Las dos opciones son de verdad (el plan de Ascenso 14) — no todo el
 *       mundo quiere leerse ochocientos legendarios antes de una run de una
 *       hora, y a quien si quiera, elegirlo <b>es</b> media partida.
 *   <li><b>La Ascension</b>, que solo se ofrece hasta la que hayas desbloqueado.
 * </ol>
 *
 * <h2>Y los retos, en su pestanya</h2>
 *
 * <p>La primera fila es <b>Estandar · Commander · Retos</b> (Ana, 06-10-2026).
 * En Retos estan las runs que son las mismas para todos: <b>la de hoy</b>
 * ({@link AscentSeed#daily}, una nueva cada dia), la de la semana y la de un
 * codigo pegado. Las dos primeras salen de la fecha: sin internet, sin copiar
 * nada, a un clic de empezar. Antes eran una fila "Semilla" encima de todo, que
 * habia que encontrar.
 *
 * <h2>Y avisa de lo que va a pasar</h2>
 *
 * <p>Si ya hay una run a medias, el boton de empezar <b>lo dice</b> y pide
 * confirmacion. Perder una run por no haber leido un boton seria justo el fallo
 * que este modo no se puede permitir.
 */
public class AscentSetupScreen extends StackPane {

    /** Que se puede hacer aqui. */
    public interface Actions {
        /**
         * Empezar la run.
         *
         * @param commander en Commander, el elegido, o {@code null} para que
         *                  salga uno al azar. En Estandar se ignora
         */
        void start(AscentRun.Mode mode, PaperCard commander, int ascension, byte colours,
                   forge.neo.ascent.AscentPool pool);

        /** Empezar la run de un codigo o del reto de la semana ({@link AscentSeed}). */
        default void startRecipe(final AscentSeed.Recipe recipe) {
        }

        void back();
    }

    /** Cuantos comandantes se pintan de golpe. Son 10.824: hay que cortar. */
    private static final int PAGE = 24;

    private final Actions actions;
    private final double cardWidth;
    private final boolean runInProgress;

    private final VBox body = new VBox(14);
    /**
     * Todo lo de la pantalla menos el pie, con su propio desplazamiento.
     *
     * <p>itch.io, 03-10-2026, con captura: con Ascension 10 la lista de "con lo
     * que vas a jugar" son diez lineas, y el pergamino ya no daba de si. Se salia
     * por abajo — debajo del papel, en blanco — y en una pantalla mas baja el
     * titulo se cortaba por arriba y Volver/Empezar se iban fuera. El pie queda
     * FUERA del desplazamiento: los botones siempre en su sitio (principio 12).
     */
    private final VBox content = new VBox(14);
    private final javafx.scene.control.ScrollPane scroll =
            new javafx.scene.control.ScrollPane(content);
    private AscentRun.Mode mode = AscentRun.Mode.STANDARD;
    private PaperCard commander;
    /**
     * Los colores pedidos para el mazo de Estandar, o 0 = al azar.
     *
     * <p>{@code -Dneo.ascent.setupColours=WB} los deja marcados al abrir, que
     * es la unica forma de capturar la fila con {@code --snapshot}.
     */
    private byte colours = parseColours(System.getProperty("neo.ascent.setupColours", ""));
    /**
     * A que nivel esta puesta la ruleta.
     *
     * <p>{@code -Dneo.ascent.setupLevel=N} la deja puesta en N al abrir: la
     * lista de "con lo que vas a jugar" solo sale con un nivel elegido, y sin
     * esto no hay forma de capturarla con {@code --snapshot} — habria que
     * pulsar un boton a mano, que es justo lo que no se puede hacer desde una
     * prueba. Se recorta a lo desbloqueado, como los botones.
     */
    private int ascension = Math.max(0,
            Math.min(AscentUnlocks.maxAscension(), Integer.getInteger("neo.ascent.setupLevel", 0)));
    /** Lo escrito en el buscador de comandantes ({@code -Dneo.ascent.setupSearch}: para capturas). */
    private String search = System.getProperty("neo.ascent.setupSearch", "");
    /**
     * En que pagina del selector se entra.
     *
     * <p>{@code -Dneo.ascent.setupPage=N} la deja puesta al abrir. Existe para
     * poder capturar la barra de paginas con la flecha de atras ACTIVA: en la
     * pagina 1 sale apagada, que es justo el estado en el que el fallo del
     * 22-09-2026 no se distinguia de lo normal. {@code syncPager} la recorta al
     * rango, asi que un numero grande cae en la ultima.
     */
    private int page = Math.max(0, Integer.getInteger("neo.ascent.setupPage", 0));

    /**
     * De que expansiones salen las cartas (ver {@link forge.neo.ascent.AscentPool}).
     * De fabrica, todas. {@code -Dneo.ascent.setupPool=range:LEA:4ED} o
     * {@code set:ZEN,WWK,ROE} lo deja puesto al abrir, para capturarlo.
     */
    private forge.neo.ascent.AscentPool.Kind poolKind;
    private String poolFrom;
    private String poolTo;
    /** Las expansiones elegidas una a una (un bloque a medida). */
    private final List<String> poolSets = new java.util.ArrayList<>();

    {
        final forge.neo.ascent.AscentPool preset = forge.neo.ascent.AscentPool.parse(
                System.getProperty("neo.ascent.setupPool", ""));
        poolKind = preset.kind;
        if (preset.kind == forge.neo.ascent.AscentPool.Kind.RANGE) {
            poolFrom = preset.from;
            poolTo = preset.to;
        } else if (preset.kind == forge.neo.ascent.AscentPool.Kind.SET) {
            poolSets.addAll(preset.sets);
        }
    }

    /** Solo pruebas: ya se ha pulsado "Al azar" por -Dneo.ascent.rollPool. */
    private boolean rolledForTest;

    /** El pozo que hay puesto ahora. */
    private forge.neo.ascent.AscentPool pool() {
        switch (poolKind) {
            case RANGE:
                return forge.neo.ascent.AscentPool.range(poolFrom, poolTo);
            case SET:
                return forge.neo.ascent.AscentPool.sets(poolSets);
            default:
                return forge.neo.ascent.AscentPool.ALL;
        }
    }

    // ------------------------------------------------------------------
    //  La semilla (Discord, 06-10-2026: "a seeded run ... a weekly challenge")
    // ------------------------------------------------------------------

    /**
     * De donde sale la run: nueva (las pestanyas de Estandar y Commander) o un
     * reto — el de hoy, el de la semana o el de un codigo.
     */
    private enum SeedSource { RANDOM, DAILY, WEEKLY, CODE }

    /**
     * {@code -Dneo.ascent.setupSeed=daily}, {@code =weekly} o {@code =<codigo>}
     * la deja puesta al abrir, para capturarla con {@code --snapshot}.
     */
    private SeedSource seedSource = SeedSource.RANDOM;

    /** El ultimo reto elegido: volver a la pestanya de Retos lo deja donde estaba. */
    private SeedSource challenge = SeedSource.DAILY;

    /**
     * Lo que falta para el reto siguiente, y de que dia es el que se ensenya.
     * El reloj lo refresca, y si ha cambiado el dia rehace la pantalla: quien
     * la deje abierta a medianoche UTC no se queda con el reto de ayer.
     */
    private Label countdown;
    private java.time.LocalDate shownDay;

    /**
     * Cada 15 s. Se para al salir de la escena y vuelve al entrar: un
     * temporizador en marcha sujeta la pantalla para siempre (principio 11).
     */
    private final javafx.animation.Timeline clock = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(javafx.util.Duration.seconds(15), e -> tick()));

    {
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sceneProperty().addListener((o, was, now) -> {
            if (now == null) {
                clock.stop();
            } else {
                clock.play();
            }
        });
    }

    private void tick() {
        if (seedSource != SeedSource.DAILY && seedSource != SeedSource.WEEKLY) {
            return;
        }
        if (!AscentSeed.today().equals(shownDay)) {
            rebuild();
        } else if (countdown != null) {
            countdown.setText(countdownText());
        }
    }

    /** "Nuevo reto en 14 h 3 min, a las 02:00." — la hora, en la del jugador. */
    private String countdownText() {
        final java.time.Instant now = java.time.Instant.now();
        final boolean daily = seedSource == SeedSource.DAILY;
        final java.time.Instant next = daily ? AscentSeed.nextDaily(now) : AscentSeed.nextWeekly(now);
        final int[] left = AscentSeed.timeLeft(now, next);
        final String span = left[0] > 0 ? NeoText.get("ascent.seed.left.dh", left[0], left[1])
                : left[1] > 0 ? NeoText.get("ascent.seed.left.hm", left[1], left[2])
                : NeoText.get("ascent.seed.left.m", left[2]);
        if (!daily) {
            return NeoText.get("ascent.seed.next", span);
        }
        final String at = next.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        return NeoText.get("ascent.seed.next.at", span, at);
    }

    /**
     * El campo del codigo. UNO para toda la vida de la pantalla: rebuild() lo
     * vuelve a colocar en vez de crear otro, que si no se perderia lo escrito y
     * el foco en cada tecla.
     */
    private final TextField codeField = new TextField();

    /**
     * Con un codigo pegado: jugarla con el mazo del codigo (false) o con uno
     * elegido aqui (true). Ver {@link AscentSeed.Recipe#withDeck}. El reto de la
     * semana no lo ofrece. {@code -Dneo.ascent.setupOwnDeck=true} para capturarlo.
     */
    private boolean ownDeck = Boolean.getBoolean("neo.ascent.setupOwnDeck");

    {
        final String preset = System.getProperty("neo.ascent.setupSeed", "").trim();
        if ("daily".equalsIgnoreCase(preset)) {
            seedSource = SeedSource.DAILY;
        } else if ("weekly".equalsIgnoreCase(preset)) {
            seedSource = SeedSource.WEEKLY;
        } else if (!preset.isEmpty()) {
            seedSource = SeedSource.CODE;
            codeField.setText(preset);
        }
        if (seedSource != SeedSource.RANDOM) {
            challenge = seedSource;
        }
        codeField.setId("ascent-seed-field");
        codeField.setPromptText(NeoText.get("ascent.seed.paste"));
        codeField.setMaxWidth(UiScale.px(460));
        codeField.textProperty().addListener((o, a, b) -> {
            rebuild();
            javafx.application.Platform.runLater(() -> {
                codeField.requestFocus();
                codeField.positionCaret(codeField.getText().length());
            });
        });
    }

    /** La receta puesta, o {@code null} con "nueva al azar" o un codigo que no vale. */
    private AscentSeed.Recipe recipe() {
        switch (seedSource) {
            case DAILY:
                return AscentSeed.daily(AscentSeed.today());
            case WEEKLY:
                return AscentSeed.weekly(AscentSeed.today());
            case CODE:
                final AscentSeed.Parsed parsed = AscentSeed.parse(codeField.getText());
                return parsed.ok() ? parsed.recipe : null;
            default:
                return null;
        }
    }

    /** Lo ultimo comprobado: buscar el comandante son diez mil cartas, y esto se llama en cada tecla. */
    private String checkedFor;
    private String checkedProblem;

    /**
     * Por que no se puede empezar con esa semilla, ya escrito; {@code null} si se
     * puede. Con "nueva al azar", siempre {@code null}: ahi manda el resto de la
     * pantalla.
     */
    private String seedProblem() {
        if (seedSource == SeedSource.RANDOM) {
            return null;
        }
        if (seedSource == SeedSource.CODE) {
            final AscentSeed.Parsed parsed = AscentSeed.parse(codeField.getText());
            if (!parsed.ok()) {
                return NeoText.get(parsed.error);
            }
        }
        final AscentSeed.Recipe r = recipe();
        final boolean own = seedSource == SeedSource.CODE && ownDeck;
        final String key = r.code() + (own ? "+own" : "") + "/" + seedSource;
        if (key.equals(checkedFor)) {
            return checkedProblem;
        }
        String problem = null;
        if (!r.sameVersion()) {
            // No se deja empezar (decision del autor, 06-10-2026): con otra version
            // cambian las cartas y no seria la misma run, aunque lo pareciera.
            problem = NeoText.get("ascent.seed.err.version", r.version, forge.neo.NeoVersion.neoVersion());
        } else if (seedSource != SeedSource.WEEKLY && r.ascension > AscentUnlocks.maxAscension()) {
            // El de la semana va a WEEKLY_ASCENSION la tengas o no: los retos no
            // desbloquean nada, asi que tampoco piden nada (Ana, 06-10-2026).
            problem = AscentUnlocks.maxAscension() == 0
                    ? NeoText.get("ascent.seed.err.ascensionNone", r.ascension)
                    : NeoText.get("ascent.seed.err.ascension", r.ascension, AscentUnlocks.maxAscension());
        } else if (r.pool.problem(r.mode) != null) {
            problem = NeoText.get(r.pool.problem(r.mode), forge.neo.ascent.AscentPool.MIN_CARDS);
        } else if (!own && r.hasCommander() && AscentSeedDeck.commanderFor(r) == null) {
            problem = NeoText.get("ascent.seed.err.commander");
        }
        checkedFor = key;
        checkedProblem = problem;
        return problem;
    }

    /** Empezar con lo que haya puesto: la semilla, o lo elegido en la pantalla. */
    private void startNow() {
        if (seedSource == SeedSource.RANDOM) {
            actions.start(mode, commander, ascension, colours, pool());
        } else {
            final AscentSeed.Recipe r = recipe();
            if (r != null && seedProblem() == null) {
                actions.startRecipe(seedSource == SeedSource.CODE && ownDeck
                        ? r.withDeck(r.mode == AscentRun.Mode.STANDARD ? colours : AscentSeedDeck.NO_COLOURS,
                                r.mode == AscentRun.Mode.COMMANDER && commander != null ? commander.getName() : null)
                        : r);
            }
        }
    }

    /** Se puede pulsar Empezar. */
    private boolean canStart() {
        return seedSource == SeedSource.RANDOM
                ? pool().problem(mode) == null
                : recipe() != null && seedProblem() == null;
    }

    /**
     * Los retos: el de hoy, el de la semana o el de un codigo.
     *
     * <p>Con cualquiera de los tres, el modo, las cartas, los colores y la
     * Ascension los pone el reto: el resto de la pantalla se cambia por lo que
     * se va a jugar ({@link #recipeBox}).
     */
    private Region challengeBox() {
        final HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        for (final SeedSource src : new SeedSource[] {SeedSource.DAILY, SeedSource.WEEKLY, SeedSource.CODE}) {
            final Button b = choice(NeoText.get("ascent.seed." + src.name().toLowerCase(java.util.Locale.ROOT)),
                    src == seedSource);
            b.setOnAction(e -> {
                seedSource = src;
                challenge = src;
                rebuild();
                if (src == SeedSource.CODE) {
                    javafx.application.Platform.runLater(codeField::requestFocus);
                }
            });
            row.getChildren().add(b);
        }
        final VBox box = new VBox(8, row);
        box.setAlignment(Pos.CENTER);
        if (seedSource == SeedSource.CODE) {
            box.getChildren().add(codeField);
        }
        final java.time.LocalDate today = AscentSeed.today();
        shownDay = today;
        if (seedSource == SeedSource.WEEKLY) {
            // El tema, en grande: "Esta semana: Kamigawa y Khans of Tarkir".
            final List<String> worlds = AscentSeed.weeklyWorlds(today);
            if (worlds.size() >= 2) {
                final Label theme = line(NeoText.get("ascent.seed.weekly.theme", worlds.get(0), worlds.get(1)));
                theme.setId("ascent-weekly-theme");
                theme.getStyleClass().setAll("ascent-info-title");
                box.getChildren().add(theme);
            }
        }
        box.getChildren().add(line(seedSource == SeedSource.DAILY
                ? NeoText.get("ascent.seed.daily.desc")
                : seedSource == SeedSource.WEEKLY
                ? NeoText.get("ascent.seed.weekly.desc", AscentSeed.weekOf(today), AscentSeed.weekYear(today),
                        AscentSeed.WEEKLY_ASCENSION)
                : NeoText.get("ascent.seed.code.desc")));
        countdown = null;
        if (seedSource != SeedSource.CODE) {
            countdown = line(countdownText());
            countdown.setId("ascent-challenge-countdown");
            box.getChildren().add(countdown);
        }
        // Si ya lo has jugado, y como te fue (AscentChallenges). Solo el de
        // AHORA: el de ayer se borra al mirarlo, y el nuevo no sale hecho.
        final AscentChallenges.Result done = played();
        if (done != null) {
            final Label mark = new Label(playedText(done));
            mark.setId("ascent-challenge-done");
            mark.getStyleClass().addAll("ascent-pill-base", "ascent-pill-relic");
            mark.setWrapText(true);
            mark.setMaxWidth(UiScale.px(620));
            // En su propia fila: la columna estira a todo el ancho, y una
            // pastilla tiene que abrazar su texto.
            final HBox holder = new HBox(mark);
            holder.setAlignment(Pos.CENTER);
            box.getChildren().add(holder);
        }
        // La racha, con su llama (AscentStreakView).
        if (seedSource == SeedSource.DAILY || seedSource == SeedSource.WEEKLY) {
            final Region streak = AscentStreakView.of(seedSource == SeedSource.DAILY
                    ? AscentChallenges.Kind.DAILY : AscentChallenges.Kind.WEEKLY, true);
            if (streak != null) {
                box.getChildren().add(streak);
            }
        }
        return box;
    }

    /** Como te fue en el reto puesto (hoy o la semana), o null. Un codigo pegado no lleva registro. */
    private AscentChallenges.Result played() {
        return seedSource == SeedSource.DAILY ? AscentChallenges.resultFor(AscentChallenges.Kind.DAILY)
                : seedSource == SeedSource.WEEKLY ? AscentChallenges.resultFor(AscentChallenges.Kind.WEEKLY)
                : null;
    }

    private static String playedText(final AscentChallenges.Result r) {
        if (r.won) {
            return r.endless > 0 ? NeoText.get("ascent.challenge.wonEndless", r.endless)
                    : NeoText.get("ascent.challenge.won");
        }
        return NeoText.get("ascent.challenge.reached", r.act, r.cleared);
    }

    /**
     * Con que se va a jugar, segun la semilla: en vez de los controles, porque
     * los pone el codigo — dos sitios que dicen cosas distintas seria un control
     * que no hace lo que parece (principio 1).
     */
    private Region recipeBox() {
        final VBox box = new VBox(6);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(UiScale.px(620));
        final AscentSeed.Recipe r = recipe();
        if (r == null) {
            if (!codeField.getText().isBlank()) {
                box.getChildren().add(warning(seedProblem()));
            }
            return box;
        }
        box.getChildren().add(label("ascent.seed.plays"));
        box.getChildren().add(line(NeoText.get("ascent.seed.sum.mode",
                NeoText.get(r.mode == AscentRun.Mode.COMMANDER
                        ? "ascent.setup.mode.commander" : "ascent.setup.mode.standard"),
                r.ascension == 0 ? NeoText.get("ascent.setup.ascension.none") : String.valueOf(r.ascension))));
        if (seedSource == SeedSource.CODE && ownDeck) {
            // El mazo lo eliges debajo: aqui no se dice el del codigo.
        } else if (r.mode == AscentRun.Mode.COMMANDER) {
            final PaperCard cmd = r.hasCommander() ? AscentSeedDeck.commanderFor(r) : null;
            box.getChildren().add(line(r.hasCommander()
                    ? (cmd == null ? NeoText.get("ascent.seed.err.commander")
                            : NeoText.get("ascent.setup.chosen", forge.neo.card.CardText.nameOf(cmd)))
                    : NeoText.get("ascent.seed.sum.commanderRandom")));
        } else {
            box.getChildren().add(line(r.colours == AscentSeedDeck.NO_COLOURS
                    ? NeoText.get("ascent.seed.sum.coloursRandom") : coloursCaption(r.colours)));
        }
        switch (r.pool.kind) {
            case RANGE:
                box.getChildren().add(line(NeoText.get("ascent.seed.sum.poolRange",
                        editionName(r.pool.from), editionName(r.pool.to))));
                break;
            case SET:
                final List<String> names = new ArrayList<>();
                for (final String c : r.pool.sets) {
                    names.add(editionName(c));
                }
                box.getChildren().add(line(NeoText.get("ascent.seed.sum.poolSets", String.join(", ", names))));
                break;
            default:
                box.getChildren().add(line(NeoText.get("ascent.seed.sum.poolAll")));
                break;
        }
        // El de los retos tambien a la vista: para comprobar entre amigos que
        // es el mismo, y para pegarlo con el resultado en Discord.
        if (seedSource != SeedSource.CODE) {
            final Button code = AscentCodeButton.of(r.code());
            if (code != null) {
                box.getChildren().add(code);
            }
        }
        final String problem = seedProblem();
        if (problem != null) {
            box.getChildren().add(warning(problem));
        } else if (r.ascension > 0) {
            box.getChildren().add(ascensionEffects(r.ascension));
        }
        return box;
    }

    /** "Jugar el reto de hoy": lo mismo que Empezar, con lo que se va a jugar escrito. */
    private Button playButton() {
        final Button play = new Button(NeoText.get(played() != null ? "ascent.seed.play.again"
                : "ascent.seed.play." + seedSource.name().toLowerCase(java.util.Locale.ROOT)));
        play.setId("ascent-challenge-play");
        play.getStyleClass().addAll("ascent-button", "btn-primary");
        play.setDisable(!canStart());
        play.setOnAction(e -> {
            if (runInProgress) {
                confirmOverwrite();
            } else {
                startNow();
            }
        });
        VBox.setMargin(play, new Insets(UiScale.px(6), 0, 0, 0));
        return play;
    }

    /** El mazo del codigo, o elegir el tuyo. */
    private Region deckChoice() {
        final HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        final Button same = choice(NeoText.get("ascent.seed.deck.code"), !ownDeck);
        same.setOnAction(e -> {
            ownDeck = false;
            rebuild();
        });
        final Button own = choice(NeoText.get("ascent.seed.deck.own"), ownDeck);
        own.setOnAction(e -> {
            ownDeck = true;
            page = 0;
            rebuild();
        });
        row.getChildren().addAll(same, own);
        final Label what = line(NeoText.get(ownDeck ? "ascent.seed.deck.own.desc" : "ascent.seed.deck.code.desc"));
        final VBox box = new VBox(6, row, what);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static Label line(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("ascent-info-text");
        l.setWrapText(true);
        l.setMaxWidth(UiScale.px(620));
        l.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        l.setAlignment(Pos.CENTER);
        return l;
    }

    private static Label warning(final String text) {
        final Label l = line(text == null ? "" : text);
        l.getStyleClass().setAll("ascent-info-duel");
        return l;
    }

    /** Los comandantes del pozo puesto: recalcularlos son once mil cartas. */
    private List<PaperCard> commandersCache;
    private forge.neo.ascent.AscentPool commandersFor;

    private List<PaperCard> commanders() {
        final AscentSeed.Recipe r = seedSource == SeedSource.CODE ? recipe() : null;
        final forge.neo.ascent.AscentPool p = r != null ? r.pool : pool();
        if (commandersCache == null || !p.equals(commandersFor)) {
            commandersCache = AscentSeedDeck.commanderPool(p);
            commandersFor = p;
        }
        return commandersCache;
    }

    public AscentSetupScreen(final double cardWidth, final boolean runInProgress,
                             final Actions actions) {
        this.actions = actions;
        this.cardWidth = cardWidth;
        this.runInProgress = runInProgress;
        getStyleClass().add("ascent-map-root");

        final Parchment paper = new Parchment(11L, Color.web("#E8D7B0"));
        StackPane.setMargin(paper, new Insets(10));

        body.setAlignment(Pos.CENTER);
        // El borde del papel esta ROTO: ver Parchment.SAFE_EDGE.
        body.setPadding(new Insets(20, 34, Parchment.SAFE_EDGE, 34));
        content.setAlignment(Pos.CENTER);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        // Para que con poco contenido siga centrado en el papel (los huecos
        // elasticos de rebuild): el contenido llena el alto si cabe, y si no
        // cabe sale la barra.
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);
        // Para capturar el modo Commander, que es el que trae el selector de
        // 10.824 comandantes — o sea la mitad de esta pantalla.
        if ("commander".equalsIgnoreCase(System.getProperty("neo.ascent.setupMode", ""))) {
            mode = AscentRun.Mode.COMMANDER;
            // -Dneo.ascent.setupCommander=N deja marcado el N-esimo de la
            // lista, para poder capturar como se ve el elegido.
            final int preset = Integer.getInteger("neo.ascent.setupCommander", -1);
            final List<PaperCard> pool = commanders();
            if (preset >= 0 && preset < pool.size()) {
                commander = pool.get(preset);
            }
        }
        rebuild();
        // Para capturar el final de la lista (los efectos de Ascension) sin
        // rueda: -Dneo.ascent.setupBottom=true
        if (Boolean.getBoolean("neo.ascent.setupBottom")) {
            javafx.application.Platform.runLater(() -> javafx.application.Platform.runLater(() -> scroll.setVvalue(1)));
        }

        getChildren().addAll(paper, body);
        // -Dneo.ascent.setupStartAt=N pulsa Empezar a los N ms, por el camino
        // de verdad: es la unica forma de jugar sin raton el primer nodo de una
        // run con un pozo de expansiones (con -Dneo.settingsFile aparte, para
        // no tocar la run del jugador).
        final int startAt = Integer.getInteger("neo.ascent.setupStartAt", 0);
        if (startAt > 0 && !runInProgress) {
            final javafx.animation.PauseTransition later =
                    new javafx.animation.PauseTransition(javafx.util.Duration.millis(startAt));
            later.setOnFinished(e -> {
                if (canStart()) {
                    startNow();
                }
            });
            later.play();
        }
        // Click derecho = el comandante grande, que es como se elige entre
        // 10.788 sin conocerselos de memoria.
        CardZoom.install(this);
    }

    // ------------------------------------------------------------------

    private void rebuild() {
        // rebuild() se llama en cada clic (un nivel, una pagina): que la lista
        // no salte arriba del todo cada vez.
        final double kept = scroll.getVvalue();
        content.getChildren().clear();
        // Un hueco elastico arriba y otro abajo: el contenido sigue centrado
        // y los botones quedan al pie del pergamino, donde estan en todas las
        // pantallas (las notas de diseño, principio 12).
        content.getChildren().add(stretch());

        final Label title = new Label(NeoText.get("ascent.setup.title"));
        title.getStyleClass().add("ascent-act");
        final Label sub = new Label(NeoText.get("ascent.setup.subtitle"));
        sub.getStyleClass().add("ascent-hint");
        content.getChildren().addAll(title, sub);

        // Estandar · Commander · Retos: la primera pregunta, siempre arriba.
        content.getChildren().addAll(label("ascent.setup.mode"), modeRow());
        if (seedSource != SeedSource.RANDOM) {
            content.getChildren().add(challengeBox());
            content.getChildren().add(recipeBox());
            final AscentSeed.Recipe r = recipe();
            if (seedSource == SeedSource.CODE && r != null) {
                content.getChildren().addAll(label("ascent.seed.deck"), deckChoice());
                if (ownDeck) {
                    if (r.mode == AscentRun.Mode.COMMANDER) {
                        content.getChildren().addAll(label("ascent.setup.commander"), commanderBox());
                    } else {
                        content.getChildren().addAll(label("ascent.setup.colours"), coloursBox());
                    }
                }
            }
            // Jugarlo, justo debajo de lo que se esta mirando (Ana, 06-10-2026):
            // en una pantalla ancha el pie queda en la esquina, lejos del centro.
            // El del pie se queda (principio 12). Con un codigo y "elegir el mio"
            // en Commander no: debajo van diez mil comandantes.
            if (r != null && !(seedSource == SeedSource.CODE && ownDeck && r.mode == AscentRun.Mode.COMMANDER)) {
                content.getChildren().add(playButton());
            }
            content.getChildren().add(stretch());
            body.getChildren().setAll(scroll, footer());
            return;
        }

        content.getChildren().addAll(label("ascent.pool.title"), poolBox());

        if (mode == AscentRun.Mode.COMMANDER) {
            content.getChildren().addAll(label("ascent.setup.commander"), commanderBox());
        } else {
            content.getChildren().addAll(label("ascent.setup.colours"), coloursBox());
        }

        if (AscentUnlocks.maxAscension() > 0) {
            // Solo se ensenya si hay algo que elegir: una fila con un unico
            // boton pulsado no es una pregunta, es ruido.
            content.getChildren().addAll(label("ascent.setup.ascension"), ascensionRow());
            // Y QUE trae ese nivel. rebuild() se llama al pulsar un numero, asi
            // que la lista se rehace sola con la eleccion nueva.
            if (ascension > 0) {
                content.getChildren().add(ascensionEffects(ascension));
            }
        }

        content.getChildren().add(stretch());
        body.getChildren().setAll(scroll, footer());
        if (kept > 0) {
            javafx.application.Platform.runLater(() -> scroll.setVvalue(kept));
        }
    }

    private static Region stretch() {
        final Region r = new Region();
        VBox.setVgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }

    /**
     * Un boton de eleccion, marcado si es el que esta puesto.
     *
     * <p>La marca no puede faltar: sin ella las dos opciones se ven iguales y
     * no hay forma de saber cual esta elegida — que es lo mismo que no haber
     * preguntado (principio 3). Y va con clase propia porque {@code btn-primary}
     * lo pisa {@code .ascent-button} por orden de hoja de estilos.
     */
    private Button choice(final String text, final boolean selected) {
        final Button b = new Button(text);
        b.getStyleClass().add("ascent-button");
        if (selected) {
            b.getStyleClass().add("ascent-button-on");
        }
        return b;
    }

    private Label label(final String key) {
        final Label l = new Label(NeoText.get(key));
        l.getStyleClass().add("ascent-info-title");
        return l;
    }

    /** Estandar, Commander o Retos. */
    private Region modeRow() {
        final HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        final boolean challenges = seedSource != SeedSource.RANDOM;
        for (final AscentRun.Mode m : AscentRun.Mode.values()) {
            final Button b = choice(NeoText.get(m == AscentRun.Mode.COMMANDER
                    ? "ascent.setup.mode.commander" : "ascent.setup.mode.standard"), !challenges && m == mode);
            b.setOnAction(e -> {
                mode = m;
                commander = null;
                page = 0;
                seedSource = SeedSource.RANDOM;
                rebuild();
            });
            row.getChildren().add(b);
        }
        final Button retos = choice(NeoText.get("ascent.setup.mode.challenges"), challenges);
        retos.setId("ascent-challenges");
        retos.setOnAction(e -> {
            if (seedSource == SeedSource.RANDOM) {
                seedSource = challenge;
                rebuild();
                if (seedSource == SeedSource.CODE) {
                    javafx.application.Platform.runLater(codeField::requestFocus);
                }
            }
        });
        row.getChildren().add(retos);
        final Label what = new Label(NeoText.get(challenges ? "ascent.setup.mode.challenges.desc"
                : mode == AscentRun.Mode.COMMANDER
                ? "ascent.setup.mode.commander.desc" : "ascent.setup.mode.standard.desc"));
        what.getStyleClass().add("ascent-info-text");
        what.setWrapText(true);
        what.setMaxWidth(UiScale.px(620));
        // Centrada como todo lo de debajo: con la fila de los retos debajo,
        // una linea a la izquierda se ve torcida.
        what.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        what.setAlignment(Pos.CENTER);
        final VBox box = new VBox(6, row, what);
        box.setAlignment(Pos.CENTER);
        if (challenges) {
            // Los retos no suben la Ascension (AscentUnlocks.recordWin(AscentRun)),
            // y se dice en su propia linea, no escondido en la descripcion: quien
            // gane uno y no vea subir nada tiene que saber por que (Ana, 06-10-2026).
            final Label notice = line(NeoText.get("ascent.seed.noAscension"));
            notice.setId("ascent-challenge-no-ascension");
            notice.getStyleClass().setAll("ascent-notice");
            box.getChildren().add(notice);
        }
        return box;
    }

    /** Elegir comandante, o dejar que salga uno al azar. */
    private Region commanderBox() {
        final TextField field = new TextField(search);
        field.setPromptText(NeoText.get("ascent.setup.search"));
        // Busca tambien por tipo, habilidad y expansion (Discord, 08-10-2026):
        // la ayuda dice como, con ejemplos. Ver CommanderSearch.
        field.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("ascent.setup.search.tip")));
        field.setPrefWidth(UiScale.px(360));
        field.setMaxWidth(UiScale.px(360));
        field.textProperty().addListener((o, a, b) -> {
            search = b == null ? "" : b;
            page = 0;
            refreshCommanders();
            syncPager();
        });

        final Button random = choice(NeoText.get("ascent.setup.randomCommander"),
                commander == null);
        random.setOnAction(e -> {
            commander = null;
            rebuild();
        });

        // Favoritos (Discord, 06-10-2026): con la estrella de cada carta salen
        // los primeros; esto deja ver SOLO esos. Ver AscentFavorites.
        final int favCount = forge.neo.ascent.AscentFavorites.only(commanders()).size();
        final Button favorites = choice(NeoText.get("ascent.setup.favorites", favCount), onlyFavorites);
        favorites.setDisable(favCount == 0 && !onlyFavorites);
        favorites.setOnAction(e -> {
            onlyFavorites = !onlyFavorites;
            page = 0;
            rebuild();
        });

        final HBox tools = new HBox(12, field, random, favorites);
        tools.setAlignment(Pos.CENTER);

        grid.getChildren().clear();
        grid.setAlignment(Pos.CENTER);
        refreshCommanders();

        // Lo que esta elegido, por escrito: el cerco no se ve si pasas de
        // pagina o buscas otra cosa, y esto si.
        final Label chosen = new Label(commander == null
                ? NeoText.get("ascent.setup.chosenRandom")
                : NeoText.get("ascent.setup.chosen", forge.neo.card.CardText.nameOf(commander)));
        chosen.getStyleClass().add("ascent-info-title");

        final VBox box = new VBox(10, tools, chosen, grid, pager());
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private final FlowPane grid = new FlowPane(10, 10);

    /** El filtro de "solo favoritos" del selector de comandante. */
    private boolean onlyFavorites = Boolean.getBoolean("neo.ascent.onlyFavorites");

    /** Los comandantes que casan con lo escrito, la pagina actual. */
    private void refreshCommanders() {
        grid.getChildren().clear();
        final List<PaperCard> hits = matches();
        final int from = Math.min(page * PAGE, Math.max(0, hits.size() - 1));
        final int to = Math.min(hits.size(), from + PAGE);
        for (int i = from; i < to; i++) {
            final PaperCard c = hits.get(i);
            final CardNode node = new CardNode(cardWidth * 0.8);
            node.setRotationEnabled(false);
            node.setCard(CardView.getCardForUi(c));
            node.setCursor(javafx.scene.Cursor.HAND);
            // El elegido se marca con el mismo cerco que una carta elegida en
            // la mesa; y con uno elegido, los demas se apagan para que salte a
            // la vista. Antes solo cambiaba la opacidad de 0,82 a 1 y clicar
            // parecia no hacer nada (reportado jugando el 24-09-2026).
            final boolean chosen = commander != null && commander.getName().equals(c.getName());
            node.setHighlighted(chosen);
            node.setOpacity(commander == null || chosen ? 1 : 0.55);
            node.setOnMouseClicked(e -> {
                if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                // Un segundo clic sobre el elegido lo suelta y vuelve al azar.
                commander = chosen ? null : c;
                rebuild();
            });
            grid.getChildren().add(withStar(node, c));
        }
    }

    /**
     * La carta con su estrella de favorito en la esquina. La estrella se come su
     * clic: marcar un favorito no es elegirlo para esta run.
     */
    private Region withStar(final CardNode node, final PaperCard c) {
        final boolean fav = forge.neo.ascent.AscentFavorites.isFavorite(c.getName());
        final Label star = new Label(fav ? "★" : "☆");
        star.getStyleClass().add("ascent-fav-star");
        if (fav) {
            star.getStyleClass().add("on");
        }
        star.setCursor(javafx.scene.Cursor.HAND);
        star.setTooltip(new javafx.scene.control.Tooltip(NeoText.get(
                fav ? "ascent.setup.favorite.remove" : "ascent.setup.favorite.add")));
        star.setOnMouseClicked(e -> {
            e.consume();
            if (e.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            forge.neo.ascent.AscentFavorites.toggle(c.getName());
            if (onlyFavorites && forge.neo.ascent.AscentFavorites.only(commanders()).isEmpty()) {
                onlyFavorites = false;
            }
            rebuild();
        });
        final StackPane box = new StackPane(node, star);
        // Al pasar el raton, CardNode baja su viewOrder para ponerse delante
        // (entering -> -1): dentro de esta caja eso la ponia ENCIMA de la
        // estrella, que desaparecia (Ana, 06-10-2026). La estrella va siempre
        // por delante, y la caja sigue a la carta para que la ampliada no
        // quede debajo de la vecina de la derecha.
        star.setViewOrder(-10);
        box.viewOrderProperty().bind(node.viewOrderProperty());
        // Abajo a la izquierda: arriba tapaba el coste, que es justo lo que se
        // mira al elegir comandante; abajo solo tapa la firma del ilustrador.
        StackPane.setAlignment(star, Pos.BOTTOM_LEFT);
        StackPane.setMargin(star, new Insets(UiScale.px(4)));
        return box;
    }

    private List<PaperCard> matches() {
        final List<PaperCard> all = onlyFavorites
                ? forge.neo.ascent.AscentFavorites.only(commanders())
                : forge.neo.ascent.AscentFavorites.favoritesFirst(commanders());
        if (search == null || search.isBlank()) {
            return all;
        }
        // Por nombre, tipo, habilidad o expansion, cada palabra en algun sitio
        // (Discord, 08-10-2026). Ver CommanderSearch, que es de las dos interfaces.
        final List<PaperCard> out = new ArrayList<>();
        for (final PaperCard c : all) {
            if (forge.neo.ascent.CommanderSearch.matches(c, search)) {
                out.add(c);
            }
        }
        return out;
    }

    /**
     * Ir a la pagina siguiente.
     *
     * <p>Principio 5: si una rejilla recorta, tiene que haber forma de llegar a
     * lo recortado. Son 10.824 comandantes y aqui caben 24.
     */
    private Region pager() {
        prevPage.setText("<");
        nextPage.setText(">");
        prevPage.getStyleClass().add("ascent-button");
        nextPage.getStyleClass().add("ascent-button");
        prevPage.setOnAction(e -> {
            page--;
            refreshCommanders();
            syncPager();
        });
        nextPage.setOnAction(e -> {
            page++;
            refreshCommanders();
            syncPager();
        });
        pageLabel.getStyleClass().add("ascent-hint");
        syncPager();
        final HBox row = new HBox(10, prevPage, pageLabel, nextPage);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    private final Label pageLabel = new Label();
    private final Button prevPage = new Button();
    private final Button nextPage = new Button();

    /**
     * Deja la barra de paginas diciendo la verdad: el contador <b>y</b> si cada
     * flecha se puede pulsar.
     *
     * <h2>El fallo que tapa</h2>
     *
     * <p>Reportado jugando el 22-09-2026: <i>"the previous page button isn't
     * working"</i>. Y era exactamente eso, solo el de atras: el
     * {@code setDisable} de las dos flechas se evaluaba <b>una sola vez, al
     * construir la barra</b>, y los manejadores solo refrescaban la rejilla y
     * el texto. Como se entra en la pagina 0, {@code prev} nacia deshabilitado
     * y ya no se volvia a habilitar nunca; {@code next} nacia habilitado, y por
     * eso avanzar si funcionaba y volver no.
     *
     * <p>El mismo agujero se comia la busqueda: escribir en el buscador cambia
     * cuantos hay y pone la pagina a 0, pero nadie tocaba la barra, asi que el
     * contador seguia diciendo "pagina 7 de 461" sobre una busqueda de tres
     * cartas.
     *
     * <p>Por eso ahora hay <b>un solo sitio</b> que pone la barra al dia y lo
     * llaman los tres caminos. Y recorta {@code page} al rango: si te quedas en
     * la pagina 7 y buscas algo con una sola, la pagina que hay que ensenyar es
     * la ultima que existe, no un hueco vacio.
     */
    private void syncPager() {
        final int total = matches().size();
        final int pages = Math.max(1, (total + PAGE - 1) / PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        prevPage.setDisable(page <= 0);
        nextPage.setDisable(page >= pages - 1);
        pageLabel.setText(NeoText.get("ascent.setup.page", page + 1, pages, total));
    }

    /** El nivel de Ascension, hasta el que lleves desbloqueado. */
    private Region ascensionRow() {
        final FlowPane row = new FlowPane(8, 8);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i <= AscentUnlocks.maxAscension(); i++) {
            final int level = i;
            final Button b = choice(level == 0
                    ? NeoText.get("ascent.setup.ascension.none") : String.valueOf(level),
                    level == ascension);
            b.setOnAction(e -> {
                ascension = level;
                rebuild();
            });
            row.getChildren().add(b);
        }
        return row;
    }

    /**
     * <b>Con que reglas vas a jugar</b>, en cristiano y antes de empezar.
     *
     * <p>Se ensenya TODO lo que estara activo y no solo lo que anyade el nivel
     * elegido, porque los niveles se acumulan: eligiendo el 5 se juega con
     * cinco cambios de reglas. Ensenyar solo el ultimo seria contestar una
     * pregunta que nadie ha hecho y dejar los otros cuatro escondidos, que es
     * exactamente como estaba antes del 19-09-2026.
     *
     * <p>Va aqui y no detras de un boton de "ver detalles": es la unica
     * pantalla donde esta decision se toma, y de una run no se vuelve atras.
     */
    private Region ascensionEffects(final int ascension) {
        final VBox box = new VBox(3);
        box.setAlignment(Pos.CENTER_LEFT);
        // Abraza su texto (hasta 560) para que la columna la CENTRE: estirada al
        // ancho de la columna, con el texto a la izquierda, en 4K se iba al
        // borde del pergamino (06-10-2026).
        box.setMaxWidth(Region.USE_PREF_SIZE);
        final Label head = new Label(NeoText.get("ascent.setup.ascension.active"));
        head.getStyleClass().add("ascent-hint");
        box.getChildren().add(head);
        for (final String key : AscentUnlocks.effectKeysUpTo(ascension)) {
            final Label line = new Label("·  " + NeoText.get(key));
            line.getStyleClass().add("ascent-info-text");
            line.setWrapText(true);
            line.setMaxWidth(UiScale.px(560));
            box.getChildren().add(line);
        }
        return box;
    }

    /** Las cinco letras de color: marcas hasta dos, o ninguna. */
    private static final byte[] COLOUR_ORDER = {
        forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE,
        forge.card.MagicColor.BLACK, forge.card.MagicColor.RED,
        forge.card.MagicColor.GREEN};

    private static final String[] COLOUR_LETTERS = {"W", "U", "B", "R", "G"};

    /**
     * Con que colores se genera el mazo de Estandar.
     *
     * <h2>La decision de interfaz, y por que esta</h2>
     *
     * <p>Habia dos formas: dejar que "ninguno marcado" signifique al azar, o
     * poner ademas un boton <i>Al azar</i>. <b>Se eligio lo primero, pero con
     * el resultado escrito debajo siempre.</b>
     *
     * <p>El boton aparte suena mas claro y no lo es: serian <b>dos controles
     * para una sola decision</b>, y en cuanto existen hay un estado imposible
     * que alguien tiene que resolver — ¿que pasa si esta pulsado <i>Al azar</i>
     * y ademas hay dos letras marcadas? Cualquier respuesta a eso es una regla
     * que el jugador tiene que adivinar.
     *
     * <p>El unico argumento a favor del boton era la ambiguedad: sin marcar
     * nada no se sabe si es a proposito o se te olvido. Eso se arregla sin
     * anyadir un control, diciendolo: la linea de debajo dice <b>siempre</b> con
     * que se va a jugar ("Al azar", "Mono-blanco", "Blanco y negro"). Asi no
     * hay modo escondido — que es el principio 1, un control que no hace lo que
     * parece es peor que no tenerlo — y empezar una run <b>no se deshace</b>,
     * asi que lo que va a pasar tiene que estar a la vista antes de pulsar.
     *
     * <p>El tope de <b>dos</b> se aplica al pulsar: marcar una tercera suelta
     * la mas antigua en vez de no hacer nada. Un boton que no responde parece
     * roto; uno que responde ensenya la regla sin un cartel de error.
     */
    private Region coloursBox() {
        final HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        for (int i = 0; i < COLOUR_ORDER.length; i++) {
            final byte c = COLOUR_ORDER[i];
            final String letter = COLOUR_LETTERS[i];
            final Button b = new Button(letter);
            b.getStyleClass().addAll("segment", "colour-filter",
                    "mana-" + letter.toLowerCase(java.util.Locale.ROOT));
            if ((colours & c) != 0) {
                b.getStyleClass().add("colour-filter-on");
            }
            b.setOnAction(e -> {
                colours = toggle(colours, c);
                rebuild();
            });
            row.getChildren().add(b);
        }

        final Label what = new Label(coloursCaption());
        what.getStyleClass().add("ascent-info-text");
        what.setWrapText(true);
        what.setMaxWidth(UiScale.px(620));
        // Centrada, al reves que la descripcion del modo de arriba: aquella es
        // un parrafo que ocupa las dos lineas enteras y alineado a la izquierda
        // se lee mejor; esta es UNA frase corta, y suelta a la izquierda de una
        // caja de 620 parece un texto descolocado en vez de el pie de las cinco
        // letras que tiene encima.
        what.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        // ⚠️ Y setAlignment ADEMAS: con setMaxWidth(620) la etiqueta ocupa los
        // 620 aunque el texto sean cuatro palabras, asi que centrar el NODO en
        // el VBox no mueve nada y setTextAlignment solo reparte las lineas
        // entre si. Lo que coloca el texto dentro de la etiqueta es esto.
        what.setAlignment(Pos.CENTER);

        final VBox box = new VBox(6, row, what);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /**
     * Marcar o desmarcar, respetando el tope.
     *
     * <p>Al marcar la tercera se <b>suelta la primera</b> (la de menor peso en
     * el orden WUBRG). No se ignora la pulsacion: un boton que no reacciona
     * parece roto, y la regla se aprende viendo que siempre quedan dos.
     */
    private static byte toggle(final byte current, final byte colour) {
        if ((current & colour) != 0) {
            return (byte) (current & ~colour);
        }
        byte next = (byte) (current | colour);
        for (final byte c : COLOUR_ORDER) {
            if (Integer.bitCount(next & 0xFF) <= AscentSeedDeck.MAX_COLOURS) {
                break;
            }
            if (c != colour && (next & c) != 0) {
                next = (byte) (next & ~c);
            }
        }
        return next;
    }

    /** Lo que va a pasar al pulsar Empezar, dicho con todas las letras. */
    private String coloursCaption() {
        return coloursCaption(colours);
    }

    private static String coloursCaption(final byte colours) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < COLOUR_ORDER.length; i++) {
            if ((colours & COLOUR_ORDER[i]) != 0) {
                names.add(NeoText.get("ascent.setup.colours."
                        + COLOUR_LETTERS[i].toLowerCase(java.util.Locale.ROOT)));
            }
        }
        if (names.isEmpty()) {
            return NeoText.get("ascent.setup.colours.any");
        }
        if (names.size() == 1) {
            return NeoText.get("ascent.setup.colours.one", names.get(0));
        }
        return NeoText.get("ascent.setup.colours.two", names.get(0), names.get(1));
    }

    /** {@code -Dneo.ascent.setupColours=WB} -> mascara, para poder capturarlo. */
    private static byte parseColours(final String spec) {
        byte mask = 0;
        for (final char c : spec.toCharArray()) {
            mask |= forge.card.MagicColor.fromName(c);
        }
        while (Integer.bitCount(mask & 0xFF) > AscentSeedDeck.MAX_COLOURS) {
            mask = (byte) (mask & (mask - 1));
        }
        return mask;
    }

    // ------------------------------------------------------------------
    //  De que expansiones salen las cartas
    // ------------------------------------------------------------------

    /**
     * Todas / desde-hasta / solo una expansion, y debajo cuantas cartas deja.
     *
     * <p>Pedido en Discord el 02-10-2026 ("restrict ... to a certain edition
     * or card sets, e.g. 4th ed and earlier"). Lo de debajo no es adorno: con
     * Legends sola se puede jugar, con una caja de promos no, y eso hay que
     * saberlo antes de empezar y no en el primer premio vacio.
     */
    private Region poolBox() {
        final HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER);
        for (final forge.neo.ascent.AscentPool.Kind k : forge.neo.ascent.AscentPool.Kind.values()) {
            final Button b = choice(NeoText.get("ascent.pool." + k.name().toLowerCase(java.util.Locale.ROOT)),
                    k == poolKind);
            b.setOnAction(e -> {
                poolKind = k;
                final List<forge.card.CardEdition> eds = forge.neo.ascent.AscentPool.editions();
                if (k == forge.neo.ascent.AscentPool.Kind.RANGE && poolFrom == null && !eds.isEmpty()) {
                    poolFrom = eds.get(0).getCode();
                    poolTo = eds.get(eds.size() - 1).getCode();
                }
                poolChanged();
            });
            row.getChildren().add(b);
        }
        // UNA EXPANSION AL AZAR (Discord, 08-10-2026: "a random set selection
        // button? It could be chaotic, but it would definitely be fun"). No es un
        // tipo mas: deja puesto "Elegir expansiones" con esa sola, que se ve, se
        // puede quitar o ampliar, y otro clic sortea otra. Asi no hay un "al
        // azar" encendido a la vez que unas pastillas (ver el plan de Ascenso 6.3b).
        final Button dice = choice(NeoText.get("ascent.pool.random"), false);
        dice.setId("ascent-pool-random");
        dice.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("ascent.pool.random.tip")));
        dice.setOnAction(e -> {
            final String avoid = poolKind == forge.neo.ascent.AscentPool.Kind.SET && poolSets.size() == 1
                    ? poolSets.get(0) : null;
            final String code = forge.neo.ascent.AscentPool.randomSet(mode, new java.util.Random(), avoid);
            if (code != null) {
                poolKind = forge.neo.ascent.AscentPool.Kind.SET;
                poolSets.clear();
                poolSets.add(code);
                poolChanged();
            }
        });
        row.getChildren().add(dice);
        // Solo pruebas: -Dneo.ascent.rollPool=true lo pulsa una vez al abrir.
        if (Boolean.getBoolean("neo.ascent.rollPool") && !rolledForTest) {
            rolledForTest = true;
            javafx.application.Platform.runLater(dice::fire);
        }
        final VBox box = new VBox(8, row);
        box.setAlignment(Pos.CENTER);
        if (poolKind == forge.neo.ascent.AscentPool.Kind.RANGE) {
            final Label fromLabel = new Label(NeoText.get("ascent.pool.from"));
            final Label toLabel = new Label(NeoText.get("ascent.pool.to"));
            fromLabel.getStyleClass().add("ascent-info-text");
            toLabel.getStyleClass().add("ascent-info-text");
            final HBox pick = new HBox(10, fromLabel, editionBox(poolFrom, c -> {
                poolFrom = c;
                poolChanged();
            }), toLabel, editionBox(poolTo, c -> {
                poolTo = c;
                poolChanged();
            }));
            pick.setAlignment(Pos.CENTER);
            box.getChildren().add(pick);
        } else if (poolKind == forge.neo.ascent.AscentPool.Kind.SET) {
            // Un bloque a medida (Discord, 03-10-2026): se anyaden con el
            // desplegable y cada una sale como una pastilla; clicarla la quita.
            final Region adder = editionBox(null, c -> {
                if (!poolSets.contains(c)) {
                    poolSets.add(c);
                    poolChanged();
                }
            });
            ((javafx.scene.control.ComboBox<?>) adder).setPromptText(NeoText.get("ascent.pool.add"));
            final HBox pick = new HBox(10, adder);
            pick.setAlignment(Pos.CENTER);
            box.getChildren().add(pick);
            if (!poolSets.isEmpty()) {
                final javafx.scene.layout.FlowPane chips = new javafx.scene.layout.FlowPane(6, 6);
                chips.setAlignment(Pos.CENTER);
                chips.setMaxWidth(UiScale.px(720));
                for (final String code : pool().sets) {
                    final Button chip = new Button(editionName(code) + "  \u00d7");
                    chip.getStyleClass().add("ascent-set-chip");
                    chip.setTooltip(new javafx.scene.control.Tooltip(NeoText.get("ascent.pool.remove")));
                    chip.setOnAction(ev -> {
                        poolSets.remove(code);
                        poolChanged();
                    });
                    chips.getChildren().add(chip);
                }
                box.getChildren().add(chips);
            }
        }
        if (poolKind != forge.neo.ascent.AscentPool.Kind.ALL) {
            final forge.neo.ascent.AscentPool p = pool();
            final String problem = p.problem(mode);
            final Label info = new Label(problem != null
                    ? NeoText.get(problem, forge.neo.ascent.AscentPool.MIN_CARDS)
                    : NeoText.get(mode == AscentRun.Mode.COMMANDER
                            ? "ascent.pool.countCommander" : "ascent.pool.count",
                            String.format(java.util.Locale.getDefault(), "%,d", p.spellCount()),
                            p.commanderCount()));
            info.getStyleClass().add(problem != null ? "ascent-info-duel" : "ascent-hint");
            info.setWrapText(true);
            info.setMaxWidth(UiScale.px(620));
            box.getChildren().add(info);
        }
        return box;
    }

    /** "Zendikar (ZEN)", o el codigo si el motor no la conoce. */
    private static String editionName(final String code) {
        try {
            final forge.card.CardEdition ed = forge.model.FModel.getMagicDb().getEditions().get(code);
            return ed == null ? code : ed.getName() + " (" + code + ")";
        } catch (final RuntimeException ex) {
            return code;
        }
    }

    /** Un desplegable de expansiones, de la mas vieja a la mas nueva. */
    private Region editionBox(final String selected, final java.util.function.Consumer<String> onPick) {
        final javafx.scene.control.ComboBox<forge.card.CardEdition> box = new javafx.scene.control.ComboBox<>();
        box.getStyleClass().add("team-combo");
        box.getItems().addAll(forge.neo.ascent.AscentPool.editions());
        box.setVisibleRowCount(16);
        box.setPrefWidth(UiScale.px(300));
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(final forge.card.CardEdition ed) {
                if (ed == null) {
                    return "";
                }
                final String year = ed.getDate() == null ? ""
                        : "  \u00b7  " + new java.text.SimpleDateFormat("yyyy", java.util.Locale.ROOT)
                                .format(ed.getDate());
                return ed.getName() + " (" + ed.getCode() + ")" + year;
            }

            @Override
            public forge.card.CardEdition fromString(final String s) {
                return null;
            }
        });
        for (final forge.card.CardEdition ed : box.getItems()) {
            if (ed.getCode().equals(selected)) {
                box.getSelectionModel().select(ed);
                break;
            }
        }
        box.setOnAction(e -> {
            if (box.getValue() != null) {
                onPick.accept(box.getValue().getCode());
            }
        });
        return box;
    }

    /** Al cambiar el pozo: el comandante elegido puede haberse quedado fuera. */
    private void poolChanged() {
        if (commander != null && !pool().allows(commander)) {
            commander = null;
        }
        page = 0;
        rebuild();
    }

    /** Empezar, y avisar si eso se lleva por delante una run. */
    private Region footer() {
        final Button start = new Button(NeoText.get(runInProgress
                ? "ascent.setup.startOver" : "ascent.setup.start"));
        start.getStyleClass().addAll("ascent-button", "btn-primary");
        // Un pozo con el que no se puede jugar no deja empezar: el motivo sale
        // debajo de las expansiones (poolBox), no aqui.
        start.setDisable(!canStart());
        start.setOnAction(e -> {
            if (runInProgress) {
                confirmOverwrite();
            } else {
                startNow();
            }
        });

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("ascent-button");
        back.setOnAction(e -> actions.back());

        // Volver, abajo a la derecha y junto a la accion principal: el mismo
        // sitio en todas las pantallas (las notas de diseño, principio 12).
        final HBox row = new HBox(12, back, start);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setPadding(new Insets(8, 0, 0, 0));

        if (!runInProgress) {
            return row;
        }
        // Con una run a medias, el boton NO puede limitarse a decir "Empezar":
        // pulsarlo la borra, y eso no se deshace.
        final Label warn = new Label(NeoText.get("ascent.setup.willLose"));
        warn.getStyleClass().add("ascent-info-duel");
        warn.setWrapText(true);
        warn.setMaxWidth(UiScale.px(620));
        final VBox box = new VBox(6, warn, row);
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private void confirmOverwrite() {
        final VBox box = new VBox(12);
        box.getStyleClass().add("ascent-info");
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setAlignment(Pos.CENTER);

        final Label q = new Label(NeoText.get("ascent.setup.confirm.ask"));
        q.getStyleClass().add("ascent-info-title");
        final Label d = new Label(NeoText.get("ascent.setup.confirm.detail"));
        d.getStyleClass().add("ascent-info-text");
        d.setWrapText(true);
        d.setMaxWidth(UiScale.px(420));

        final Button no = new Button(NeoText.get("common.cancel"));
        no.getStyleClass().addAll("ascent-button", "btn-primary");
        final Button yes = new Button(NeoText.get("ascent.setup.confirm.yes"));
        yes.getStyleClass().addAll("ascent-button", "ascent-button-danger");
        yes.setOnAction(e -> startNow());

        final HBox buttons = new HBox(12, no, yes);
        buttons.setAlignment(Pos.CENTER);
        box.getChildren().addAll(q, d, buttons);

        final StackPane layer = new StackPane(box);
        layer.getStyleClass().add("overlay-dim");
        // La respuesta segura es la de la izquierda Y la que cierra al clicar
        // fuera: si se pulsa sin querer, no pasa nada (principio 6b).
        no.setOnAction(e -> getChildren().remove(layer));
        layer.setOnMouseClicked(e -> {
            if (e.getTarget() == layer) {
                getChildren().remove(layer);
            }
        });
        getChildren().add(layer);
    }
}
