package forge.neo.card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import forge.neo.NeoText;
import forge.neo.ascent.AscentRelic;
import forge.neo.ascent.AscentRelics;

import com.google.common.collect.Multiset;

import forge.game.card.CardView;
import forge.game.card.CardView.CardStateView;
import forge.game.card.CounterEnumType;
import forge.game.card.CounterType;
import forge.game.zone.ZoneType;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.CacheHint;
import javafx.scene.control.Label;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Una carta en pantalla.
 *
 * <p>Es el ladrillo del que se compone toda la interfaz: mano, mesa, stack,
 * cementerio, previsualizacion. Todo lo que pinta sale de un {@link CardView}
 * — nunca del estado vivo del motor.
 *
 * <p>Si la imagen aun no esta descargada, dibuja un respaldo procedural legible
 * (nombre, coste, tipo, P/T) en vez de un hueco vacio. Cuando la imagen llega,
 * {@link #refresh()} la sustituye.
 *
 * <p>Rendimiento: NO recrear estos nodos en cada actualizacion. Reutilizar el
 * mismo CardNode por id de carta y llamar a {@link #refresh()}.
 */
public class CardNode extends StackPane {

    /** Proporcion real de una carta de Magic: 63 x 88 mm. */
    public static final double ASPECT = 88.0 / 63.0;

    private static final Duration TAP_TIME = Duration.millis(220);
    private static final Duration HOVER_TIME = Duration.millis(140);

    /**
     * Si las cartas se mueven o cambian de golpe.
     *
     * <p>Es un ajuste, no un adorno: en un portatil flojo con la mesa llena, la
     * decena de transiciones simultaneas de un turno de combate se nota. Apagado
     * se ve exactamente lo mismo, solo que sin recorrido.
     */
    private static volatile boolean animations = true;

    public static void setAnimationsEnabled(final boolean on) {
        animations = on;
    }

    public static boolean areAnimationsEnabled() {
        return animations;
    }

    /**
     * Si las cartas foil brillan (la auditoría del motor, apartado D5).
     *
     * <p>Encendido de fabrica: es puro adorno, no cambia nada de como se
     * juega — al reves que el pago automatico o el pase inteligente, que
     * cambian el RITMO de la partida y por eso vienen apagados. Aqui la
     * regla es la de las animaciones (las notas de diseño): apagado se ve
     * exactamente la carta de siempre, sin ningun brillo.
     */
    private static volatile boolean foilEffect = true;

    public static void setFoilEffectEnabled(final boolean on) {
        foilEffect = on;
    }

    public static boolean isFoilEffectEnabled() {
        return foilEffect;
    }

    /**
     * Si el reflejo de la foil se MUEVE, o se queda quieto como antes.
     *
     * <p>No es un ajuste del menu — el interruptor que ve el jugador sigue
     * siendo uno solo, "brillo de las foil" (§2 de la auditoría del motor: una casilla
     * es una pregunta distinta, y "¿quieto o en movimiento?" no lo es). Es la
     * palanca con la que se compara una cosa con la otra al medir, desde
     * {@code -Dneo.foil.static=true}, y la red por si algun dia hace falta
     * dejar quietas las foil en una maquina floja sin apagarlas del todo.
     */
    private static volatile boolean foilMotion = !Boolean.getBoolean("neo.foil.static");

    public static void setFoilMotionEnabled(final boolean on) {
        foilMotion = on;
    }

    public static boolean isFoilMotionEnabled() {
        return foilMotion;
    }

    /**
     * Trata TODA carta como foil. Solo para medir el peor caso posible — un
     * Commander entero en foil, que es justo lo que hay que poder descartar
     * antes de dar esto por bueno. En una partida real son ~1 de cada 20
     * ({@code Match.preparePlayerZone}).
     */
    private static final boolean FOIL_EVERYTHING = Boolean.getBoolean("neo.foil.all");

    /**
     * La <b>difraccion</b> de una foil: rayas diagonales finas de color que se
     * repiten por toda la carta, en modo {@code SCREEN} para que ACLAREN el
     * arte de debajo en vez de teñirlo — es lo que hace que una lamina
     * metalica brille en vez de quedar sucia.
     *
     * <p>Antes esto era UN solo degradado estirado de esquina a esquina, con
     * bandas anchisimas: eso no es un reflejo, es un tinte, y el ojo lo lee
     * como una calcomania pegada encima. Lo que identifica a una foil de
     * verdad es la <b>frecuencia</b> — rayas finas — y que el color
     * <b>cambie al mover la carta</b>. De ahi {@link CycleMethod#REPEAT} sobre
     * un tramo corto (0,16 de la diagonal) y un alfa bajo: mucho mas suave que
     * antes, porque ahora quien llama la atencion es el movimiento y no el
     * color.
     *
     * <p>Un {@code LinearGradient} y nada mas (D5 lo pedia asi): nada de
     * texturas ni imagenes, que no las tenemos — el skin de Swing las trae
     * de una descarga aparte que no hacemos. Coordenadas proporcionales
     * (0..1), asi que UN solo gradiente vale para cualquier tamano de carta
     * sin recalcular nada.
     */
    private static final LinearGradient FOIL_TINT = new LinearGradient(
            0, 0, 0.11, 0.11, true, CycleMethod.REPEAT,
            new Stop(0.00, Color.rgb(255, 170, 90, 0.09)),
            new Stop(0.25, Color.rgb(120, 255, 205, 0.09)),
            new Stop(0.50, Color.rgb(110, 190, 255, 0.09)),
            new Stop(0.75, Color.rgb(215, 140, 255, 0.09)),
            new Stop(1.00, Color.rgb(255, 170, 90, 0.09)));

    /**
     * El <b>reflejo</b>: una banda estrecha y clara que cruza la carta en
     * diagonal cada siete segundos. Es la pieza que hace que parezca metal en
     * vez de papel pintado, y la unica que se mueve.
     *
     * <p>Va en composicion normal, <b>no</b> en {@code SCREEN}, y eso es una
     * decision de rendimiento: un modo de mezcla obliga a JavaFX a componer el
     * nodo en una pasada aparte, y esta banda se repinta constantemente. Con
     * un blanco sobre el arte se ve practicamente igual y no cuesta esa
     * pasada. Las dos franjas de color de los bordes son la orla que deja el
     * reflejo de una foil de verdad al separar la luz.
     *
     * <p>Lo brillante que es (0,40 en el nucleo) esta topado por la
     * legibilidad, que en este proyecto va antes que el adorno (§6): a 0,52
     * la banda lavaba el arte y el texto de las cartas pequenas de la mesa, y
     * eso con un mazo entero en foil es todo el rato.
     */
    private static final LinearGradient FOIL_SPECULAR = new LinearGradient(
            0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0.00, Color.rgb(255, 255, 255, 0.00)),
            new Stop(0.26, Color.rgb(140, 255, 220, 0.13)),
            new Stop(0.42, Color.rgb(255, 255, 255, 0.17)),
            new Stop(0.50, Color.rgb(255, 255, 255, 0.40)),
            new Stop(0.58, Color.rgb(255, 255, 255, 0.17)),
            new Stop(0.74, Color.rgb(205, 155, 255, 0.13)),
            new Stop(1.00, Color.rgb(255, 255, 255, 0.00)));

    /** Inclinacion del reflejo, en grados. */
    private static final double FOIL_TILT = 22;

    private final ImageView art = new ImageView();
    private final Rectangle clip = new Rectangle();
    private final Rectangle foilTint = new Rectangle();
    private final Rectangle foilShine = new Rectangle();

    /**
     * El desfase propio de esta carta dentro del ciclo de siete segundos.
     *
     * <p>Sin esto, cuarenta foil de la misma mesa destellan <b>a la vez</b>, y
     * eso no parece un reflejo: parece un estrobo. Se reparte con el numero
     * aureo en vez de al azar para que dos cartas seguidas nunca caigan cerca
     * — y para que una captura de pantalla salga siempre igual.
     */
    private final double foilPhase = nextFoilPhase();

    private static double foilPhaseSeed;

    private static synchronized double nextFoilPhase() {
        foilPhaseSeed += 0.6180339887;
        foilPhaseSeed -= Math.floor(foilPhaseSeed);
        return foilPhaseSeed;
    }

    /** Si el reflejo de la foil esta dado de alta en {@link FoilClock}. */
    private boolean foilTicking;
    private final Rectangle border = new Rectangle();

    /**
     * La cara de una reliquia de Ascenso, para las cartas que nos inventamos
     * nosotros.
     *
     * <p>Va aqui y no en las pantallas de Ascenso por el <b>principio 8</b>:
     * una reliquia se ve en siete sitios — el premio, la tienda, la barra del
     * mapa, el visor del mazo, el resumen del final, la carta ampliada y la
     * zona de mando de la partida — y todos la pintan con un {@code CardNode}.
     * Puesta aqui sale bien en los siete; puesta en una pantalla, en una.
     *
     * <p>Se construye siempre aunque casi nunca se use: son cuatro nodos
     * vacios por carta, y la alternativa —crearla la primera vez que hace
     * falta— obliga a tocar el grafo de escena a mitad de un refresco.
     */
    private final RelicArt relicFace = new RelicArt();

    private final VBox fallback = new VBox(2);
    private final Label fbName = new Label();
    private final Label fbCost = new Label();
    private final Label fbType = new Label();
    private final Label fbPt = new Label();
    private final Label ptBadge = new Label();
    private final Label damageBadge = new Label();
    private final Label rankBadge = new Label();

    /**
     * Los contadores de la carta, en fila por la esquina de arriba.
     *
     * <p>Incluye la <b>lealtad</b> de un planeswalker: en el motor la lealtad
     * ES un contador ({@code CounterEnumType.LOYALTY}), asi que sale sola por
     * el mismo camino y no hay que tratarla aparte. Sin esto no habia forma de
     * ver cuanta lealtad le queda a un planeswalker de la mesa.
     */
    private final HBox counters = new HBox(3);
    private final VBox markers = new VBox(2);

    /** Danyo marcado y P/T, en la misma esquina y en este orden. */
    private final HBox ptRow = new HBox(3, damageBadge, ptBadge);

    private final DropShadow lift = new DropShadow(18, Color.rgb(0, 0, 0, 0.65));

    private CardView card;
    private double cardWidth;
    private boolean tapped;
    private boolean hoverEnabled = true;
    private String shownImageKey;

    /**
     * Hacia donde avanza esta carta al atacar: -1 arriba (tu campo), +1 abajo
     * (el del rival), 0 no avanza. Lo pone quien la coloca, porque la carta no
     * sabe en que lado de la mesa esta.
     */
    private int combatDirection;

    /**
     * Desplazamiento vertical "de reposo": el avance del atacante.
     *
     * <p>Existe porque el hover TAMBIEN mueve la carta en vertical. Si las dos
     * animaciones escribieran translateY directamente, pasar el raton por
     * encima de un atacante lo devolveria a la fila.
     */
    private double baseTranslateY;
    private boolean advanced;

    /**
     * Orden de pintado "de reposo".
     *
     * <p>Existe por el mismo motivo que {@link #baseTranslateY}: el hover
     * TAMBIEN toca el orden de pintado para levantar la carta por encima de sus
     * vecinas. Si al salir el raton se devolviera a cero a secas, una carta que
     * vive delante a proposito — lo enganchado a una criatura — se caeria
     * detras de su anfitriona en cuanto el raton pasara por encima, que es
     * justo el fallo que hacia que un aura se viera "solo un poquito".
     */
    private double baseViewOrder;

    public CardNode(final double width) {
        getStyleClass().add("card");
        setAlignment(Pos.CENTER);
        setPickOnBounds(true);

        art.setPreserveRatio(false);
        art.setSmooth(true);

        border.setFill(Color.TRANSPARENT);
        border.getStyleClass().add("card-border");

        // Las dos capas van SOBRE el arte y DEBAJO del borde: asi tiñen la
        // carta pero el canto se sigue leyendo nitido encima. Ni un click ni
        // un hover pueden ir a parar aqui.
        //
        // La difraccion si va en SCREEN (aclara el arte en vez de ensuciarlo)
        // y se queda quieta, asi que la caja del nodo la absorbe. El reflejo
        // va en composicion normal porque es el que se repinta: ver
        // FOIL_SPECULAR.
        foilTint.setFill(FOIL_TINT);
        foilTint.setBlendMode(BlendMode.SCREEN);
        foilTint.setMouseTransparent(true);
        foilTint.setVisible(false);

        foilShine.setFill(FOIL_SPECULAR);
        foilShine.setMouseTransparent(true);
        foilShine.setVisible(false);
        foilShine.setRotate(FOIL_TILT);

        // --- respaldo procedural, por si no hay imagen todavia ---
        fallback.getStyleClass().add("card-fallback");
        fallback.setAlignment(Pos.TOP_LEFT);
        fbName.getStyleClass().add("card-fb-name");
        fbName.setWrapText(true);
        fbCost.getStyleClass().add("card-fb-cost");
        fbType.getStyleClass().add("card-fb-type");
        fbType.setWrapText(true);
        fbPt.getStyleClass().add("card-fb-pt");
        final Region spacer = new Region();
        VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        fallback.getChildren().addAll(fbName, fbCost, fbType, spacer, fbPt);

        ptBadge.getStyleClass().add("card-pt");
        ptBadge.setVisible(false);

        damageBadge.getStyleClass().add("card-damage");
        damageBadge.setVisible(false);

        // El rating del draft: la unica esquina que quedaba libre (contadores
        // arriba-izquierda, marcadores arriba-derecha, P/T abajo-derecha).
        rankBadge.getStyleClass().add("card-draft-rank");
        rankBadge.setVisible(false);
        rankBadge.setMouseTransparent(true);
        StackPane.setAlignment(rankBadge, Pos.BOTTOM_LEFT);

        // Danyo y fuerza/resistencia JUNTOS, y en la esquina donde van
        // impresos. Separados (el danyo en la otra punta) hay que mirar dos
        // sitios y hacer la resta de cabeza para saber si una criatura muere.
        ptRow.getStyleClass().add("card-pt-row");
        ptRow.setAlignment(Pos.BOTTOM_RIGHT);
        ptRow.setMouseTransparent(true);
        StackPane.setAlignment(ptRow, Pos.BOTTOM_RIGHT);

        counters.getStyleClass().add("card-counters");
        counters.setAlignment(Pos.TOP_LEFT);
        StackPane.setAlignment(counters, Pos.TOP_LEFT);
        // Es informacion, no un control: el click tiene que llegar a la carta.
        counters.setMouseTransparent(true);
        counters.setVisible(false);

        // Los marcadores del motor: velocidad, nivel de Clase, nivel del
        // Anillo, en que habitacion estas. Abajo a la izquierda, que es la
        // unica esquina libre (contadores arriba, P/T abajo a la derecha).
        markers.getStyleClass().add("card-markers");
        markers.setAlignment(Pos.TOP_RIGHT);
        // Arriba a la DERECHA porque es la unica esquina que queda libre: los
        // contadores van arriba a la izquierda y la P/T abajo a la derecha.
        // Abajo a la izquierda chocaban con la P/T y se comian el numero.
        StackPane.setAlignment(markers, Pos.TOP_RIGHT);
        markers.setMouseTransparent(true);
        markers.setVisible(false);

        relicFace.setVisible(false);

        getChildren().addAll(fallback, relicFace, art, foilTint, foilShine, border,
                counters, markers, ptRow, rankBadge);
        setClip(clip);

        setCardWidth(width);
        setCache(true);
        setCacheHint(CacheHint.QUALITY);

        // Una foil solo se anima si esta PUESTA en la pantalla. Cuando se
        // descarta una pantalla entera, sus cartas salen de la escena y aqui
        // se dan de baja solas: sin esto el reloj seguiria repartiendo fase a
        // cartas que ya no ve nadie, y ademas no se soltarian nunca.
        sceneProperty().addListener((o, was, now) -> updateFoilMotion());
        visibleProperty().addListener((o, was, now) -> updateFoilMotion());

        setOnMouseEntered(e -> { if (hoverEnabled) { hoverIn(); } });
        // Ojo: la salida NO se atiende a secas. Ver leaving().
        setOnMouseExited(e -> { if (hoverEnabled) { leaving(e); } });
    }

    // ---------------------------------------------------------------
    // Foil
    // ---------------------------------------------------------------

    /**
     * Cuanto recorre el reflejo, en anchos de carta.
     *
     * <p>Este numero se midio, no se eligio. Con 4,6 la banda tardaba tanto en
     * ir y volver que solo entraba en la carta el 22 % del ciclo: el reflejo
     * <b>casi nunca se veia</b>. No se noto mirando capturas — se noto
     * restando dos capturas y viendo que dos fases distintas daban la misma
     * imagen. Con 2,2 el nucleo brillante cruza durante algo mas de la mitad
     * del ciclo (unos 3,8 s de los 7) y descansa el resto, que es lo que se
     * pedia: que se mueva, pero no mucho.
     */
    private static final double FOIL_TRAVEL = 2.2;

    /**
     * A partir de que distancia del centro la banda ya no toca la carta, en
     * anchos de carta. Sale de su tamano: media anchura del rectangulo mas lo
     * que se ensancha al inclinarlo 22 grados, que suma 0,58; mas la media
     * carta, 1,08.
     */
    private static final double FOIL_ON_CARD = 1.08;

    /**
     * Congela el reflejo en un punto del ciclo, entre 0 y 1, y le quita a
     * cada carta su desfase propio: {@code -Dneo.foil.phase=0.5} lo deja
     * justo en el centro. Es lo unico que hace verificable con
     * {@code --snapshot} algo que se mueve — sin esto, una captura pilla la
     * banda donde le toque, y la mitad de las veces fuera de la carta.
     */
    private static final double FOIL_FROZEN =
            Double.parseDouble(System.getProperty("neo.foil.phase", "-1"));

    /** Si esta carta es foil. Ver {@link #setFoil}. */
    private boolean foil;

    /** Pone o quita las dos capas de foil, y da de alta o de baja el reflejo. */
    private void setFoil(final boolean on) {
        if (foilTint.isVisible() != on) {
            foilTint.setVisible(on);
        }
        this.foil = on;
        updateFoilMotion();
    }

    /**
     * Decide si esta carta se anima ahora mismo, y lo apunta en
     * {@link FoilClock}.
     *
     * <p>Tienen que darse las tres: que sea foil, que el jugador no lo haya
     * apagado, y que este puesta en una pantalla y visible.
     *
     * <p>Hubo una cuarta — un ancho minimo, para no animar cartas tan
     * pequenas que el reflejo no se percibe — y se quito despues de medir:
     * <b>no estaba optimizando nada</b> (84 cartas barriendo a la vez dan los
     * mismos 75 fps que la mesa sin una sola foil) y en cambio abria un
     * agujero, porque acercar la mesa con Ctrl+rueda no cambia el ancho de la
     * carta sino la escala de sus padres: una carta que se ve al doble se
     * habria quedado sin brillar. Una optimizacion que no optimiza y encima
     * tiene un caso raro no se queda.
     */
    private void updateFoilMotion() {
        final boolean want = foil && foilEffect && foilMotion
                && getScene() != null && isVisible();
        if (want == foilTicking) {
            return;
        }
        foilTicking = want;
        if (want) {
            // Mientras se anima, la caja del nodo no sirve para nada: se
            // invalidaria en cada frame y encima habria que reescribirla. Se
            // apaga, y se vuelve a poner en cuanto el reflejo para.
            setCache(false);
            FoilClock.register(this);
        } else {
            FoilClock.unregister(this);
            foilShine.setVisible(false);
            setCache(true);
        }
    }

    /**
     * Un latido del reloj compartido: coloca el reflejo dentro de su ciclo.
     *
     * <p>Cuando la banda esta del todo fuera de la carta no se toca <b>ni una
     * propiedad</b>, y eso no es tacaneria: cualquier escritura ensuciaria el
     * nodo y obligaria a JavaFX a repintar la carta entera para no cambiar
     * nada. Con el recorrido corto de ahora eso pasa poco — la banda esta casi
     * siempre rozando la carta, aunque su nucleo brillante solo la cruza
     * durante el 45 % del ciclo — asi que esto ya no es la optimizacion que
     * era: es una comparacion que no cuesta nada y evita repintar de balde.
     */
    void tickFoil(final double phase) {
        double p = FOIL_FROZEN >= 0 ? FOIL_FROZEN : phase + foilPhase;
        p -= Math.floor(p);
        final double tx = p * FOIL_TRAVEL - FOIL_TRAVEL / 2;
        if (Math.abs(tx) >= FOIL_ON_CARD) {
            if (foilShine.isVisible()) {
                foilShine.setVisible(false);
            }
            return;
        }
        foilShine.setTranslateX(tx * cardWidth);
        if (!foilShine.isVisible()) {
            foilShine.setVisible(true);
        }
    }

    // ---------------------------------------------------------------
    // Geometria
    // ---------------------------------------------------------------

    public final void setCardWidth(final double w) {
        this.cardWidth = w;
        final double h = w * ASPECT;
        final double radius = w * 0.05;

        setPrefSize(w, h);
        setMinSize(w, h);
        setMaxSize(w, h);

        art.setFitWidth(w);
        art.setFitHeight(h);

        clip.setWidth(w);
        clip.setHeight(h);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);

        border.setWidth(w - 1);
        border.setHeight(h - 1);
        border.setArcWidth(radius * 2);
        border.setArcHeight(radius * 2);

        foilTint.setWidth(w);
        foilTint.setHeight(h);
        foilTint.setArcWidth(radius * 2);
        foilTint.setArcHeight(radius * 2);

        // El reflejo es una banda estrecha y MAS ALTA que la carta: al
        // inclinarla 22 grados tiene que seguir tapando las dos esquinas. No
        // lleva esquinas redondeadas porque no hace falta — el clip del nodo
        // ya recorta la carta entera.
        foilShine.setWidth(w * 0.34);
        foilShine.setHeight(h * 1.60);
        updateFoilMotion();

        relicFace.setSize(w, h);

        // El respaldo tiene que ocupar la carta entera. Sin fijar el tamano, el
        // StackPane lo encoge a su contenido y el texto se parte letra a letra.
        fallback.setPrefSize(w, h);
        fallback.setMinSize(w, h);
        fallback.setMaxSize(w, h);

        final double pad = Math.max(4, w * 0.06);
        fallback.setPadding(new javafx.geometry.Insets(pad));
        final double textWidth = w - pad * 2;
        fbName.setMaxWidth(textWidth);
        fbName.setPrefWidth(textWidth);
        fbType.setMaxWidth(textWidth);
        fbType.setPrefWidth(textWidth);
        fbCost.setMaxWidth(textWidth);
        fbPt.setMaxWidth(textWidth);
        final double fs = Math.max(7, w * 0.085);
        fbName.setStyle("-fx-font-size:" + fs + "px;");
        fbCost.setStyle("-fx-font-size:" + (fs * 0.85) + "px;");
        fbType.setStyle("-fx-font-size:" + (fs * 0.78) + "px;");
        fbPt.setStyle("-fx-font-size:" + (fs * 1.05) + "px;");
        // La P/T es el numero que mas se mira de la mesa: va mas grande que
        // cualquier otra pastilla.
        ptBadge.setStyle("-fx-font-size:" + (fs * 1.2) + "px;");
        damageBadge.setStyle("-fx-font-size:" + (fs * 1.05) + "px;");
        rankBadge.setStyle("-fx-font-size:" + (fs * 1.05) + "px;");
        counterFontPx = fs * 0.95;
        for (final javafx.scene.Node n : counters.getChildren()) {
            applyCounterFont((Label) n);
        }
        for (final javafx.scene.Node n : markers.getChildren()) {
            ((Label) n).setStyle("-fx-font-size:" + (counterFontPx * 0.92) + "px;");
            ((Label) n).setMaxWidth(Math.max(36, w * 0.68));
        }

        // El avance esta en proporcion al tamano de carta, asi que cambia con el.
        applyAdvance(false);
    }

    /** true si se esta mostrando la imagen real de la carta. */
    public boolean hasArt() {
        return art.getImage() != null;
    }

    public double getCardWidth() {
        return cardWidth;
    }

    public double getCardHeight() {
        return cardWidth * ASPECT;
    }

    // ---------------------------------------------------------------
    // Contenido
    // ---------------------------------------------------------------

    public CardView getCard() {
        return card;
    }

    public void setCard(final CardView card) {
        this.card = card;
        this.shownImageKey = null;
        refresh();
    }

    /** Vuelve a leer el CardView y actualiza lo que haya cambiado. */
    /**
     * Refresca TODAS las cartas que cuelguen de un nodo.
     *
     * <p>Las imagenes llegan de Scryfall en segundo plano, asi que una carta
     * recien pintada ensenya el marcador hasta que su imagen aparece y alguien
     * la vuelve a pedir. Antes cada pantalla enumeraba a mano sus listas de
     * cartas, y lo que no estuviera en esa lista se quedaba con el marcador para
     * siempre — sintoma real: el selector de ediciones salia vacio la primera
     * vez y bien la segunda, porque para entonces ya estaban descargadas.
     *
     * <p>Recorrer el arbol no enumera nada: coge tambien las cartas de los
     * dialogos que se abran en el futuro, sin que haya que acordarse.
     */
    public static void refreshAllIn(final javafx.scene.Node root) {
        if (root instanceof CardNode) {
            ((CardNode) root).refresh();
            return;
        }
        if (root instanceof javafx.scene.Parent) {
            for (final javafx.scene.Node child
                    : ((javafx.scene.Parent) root).getChildrenUnmodifiable()) {
                refreshAllIn(child);
            }
        }
    }

    /**
     * Pintar una cara CONCRETA de la carta, no la que este boca arriba.
     *
     * <p>Existe para poder ensenyar <b>la otra cara</b>: una carta que se
     * transforma, una de dos caras modal, una partida. El motor las publica
     * ({@code CardView.getAlternateState}, {@code getLeftSplitState} y
     * {@code getRightSplitState}) y hasta ahora no habia forma de mirarlas —
     * veias la cara de arriba y la de abajo no existia para el juego.
     *
     * <p>Con {@code null} vuelve a la cara que este boca arriba.
     */
    public void setFace(final CardView card, final CardStateView face) {
        this.forcedFace = face;
        setCard(card);
    }

    private CardStateView forcedFace;

    /**
     * Si al cambiar de carta se borra el arte mientras llega el nuevo.
     *
     * <p><b>Apagado por defecto, y a proposito.</b> En la mesa, en la mano y en
     * el catalogo un nodo ensenya siempre la misma carta: si su imagen esta
     * puesta, es la suya, y borrarla porque una consulta a la cache llegue en
     * mal momento seria hacer parpadear cartas que se ven perfectamente. Ahi la
     * regla es la de siempre: <i>si no esta cargada se carga, tarde lo que
     * tarde</i>.
     *
     * <p>Se enciende en los nodos que van <b>cambiando de carta</b> — el cartel
     * central es el caso — donde lo que se queda puesto no es el arte de esta
     * carta sino el de la anterior. Reportado jugando: el rival juega algo, el
     * texto sale bien al instante (se relee en cada refresco) y el arte sigue
     * siendo el del hechizo de antes. Ahi si: mejor sin arte, con el respaldo
     * diciendo el nombre, el coste y el tipo de la carta buena.
     */
    private boolean blankWhileLoading;

    /** Ver {@link #blankWhileLoading}. */
    public void setBlankWhileLoading(final boolean on) {
        this.blankWhileLoading = on;
    }

    public void refresh() {
        if (card == null) {
            art.setImage(null);
            fallback.setVisible(true);
            relicFace.setVisible(false);
            setFoil(false);
            return;
        }
        final CardStateView st = forcedFace != null ? forcedFace : card.getCurrentState();

        // --- imagen ---
        //
        // La rama del "todavia no esta" depende de para que sea este nodo, y la
        // diferencia importa (ver setBlankWhileLoading): en un nodo que SIEMPRE
        // ensenya la misma carta, quedarse con lo que ya hay es lo correcto —
        // es su propio arte, y borrarlo solo hace parpadear la mesa. En uno que
        // va cambiando de carta, lo que se queda es el arte de OTRA.
        final String key = st == null ? null : st.getImageKey();
        if (key == null) {
            if (blankWhileLoading) {
                art.setImage(null);
                shownImageKey = null;
            }
        } else if (!key.equals(shownImageKey)) {
            final Image img = CardImages.get(key);
            if (img != null) {
                art.setImage(img);
                shownImageKey = key;
            } else if (blankWhileLoading) {
                art.setImage(null);
            }
        }
        final boolean hasArt = art.getImage() != null;
        art.setVisible(hasArt);

        // --- si es una carta NUESTRA, su cara dibujada ---
        //
        // Las reliquias de Ascenso no existen en Magic: Scryfall no tiene arte
        // que bajar y nunca lo va a tener, asi que caian en el respaldo
        // generico de mas abajo y —sin coste y sin P/T— se veian como un
        // rectangulo oscuro con el nombre arriba. RelicArt les pinta una carta
        // de verdad, con lo que hacen escrito en su caja de texto.
        //
        // La pregunta es barata (un mapa por nombre) y devuelve null mientras
        // no se haya jugado a Ascenso, que es cuando se registran: fuera del
        // modo esto no cuesta nada y no cambia nada.
        final AscentRelic relic = hasArt || st == null
                ? null : AscentRelics.byCardName(st.getName());
        if (relic != null) {
            relicFace.setRelic(relic);
        }
        relicFace.setVisible(relic != null);
        fallback.setVisible(!hasArt && relic == null);

        // --- brillo de foil (la auditoría del motor, apartado D5) ---
        //
        // getFoilIndex() SOLO se rellena dentro de una partida de verdad
        // (Match.preparePlayerZone llama a Card.setRandomFoil() al montar la
        // biblioteca) y CUBRE hasPaperFoil(): toda carta foil de mazo recibe
        // ademas un acabado al azar al entrar en juego, asi que mirar el
        // indice basta ahi. Fuera de una partida (catalogo, mazo, coleccion,
        // sobres) el indice esta siempre a 0 — CardView.getCardForUi ya deja
        // hasPaperFoil() puesto desde Card.fromPaperCard, que es lo que se
        // usa entonces. Solo con arte de verdad: sobre el respaldo procedural
        // el brillo no tiene nada que iluminar y solo ensuciaria el texto.
        final boolean foil = hasArt && foilEffect
                && (FOIL_EVERYTHING
                    || (st != null && st.getFoilIndex() > 0) || card.hasPaperFoil());
        setFoil(foil);

        // El rating del draft comparte esquina con la P/T del respaldo
        // procedural (fbPt): sin arte, esa esquina ya esta ocupada.
        if (draftRank != null && hasArt) {
            rankBadge.setText(String.valueOf(draftRank));
            rankBadge.setVisible(true);
            rankBadge.pseudoClassStateChanged(RANK_GOOD, draftRank >= 66);
            rankBadge.pseudoClassStateChanged(RANK_POOR, draftRank < 33);
        } else {
            rankBadge.setVisible(false);
            rankBadge.pseudoClassStateChanged(RANK_GOOD, false);
            rankBadge.pseudoClassStateChanged(RANK_POOR, false);
        }

        // --- respaldo procedural ---
        if (!hasArt && st != null) {
            fbName.setText(CardText.nameOf(st));
            fbCost.setText(st.getManaCost() == null ? "" : st.getManaCost().toString());
            fbType.setText(CardText.typeOf(st));
            fbPt.setText(st.isCreature() ? st.getPower() + " / " + st.getToughness() : "");
        }

        // --- fuerza / resistencia, que es EL numero de la mesa ---
        //
        // st.getPower() ya es la fuerza ACTUAL: el motor devuelve getNetPower(),
        // o sea con contadores, auras, equipo y efectos hasta el final del turno
        // ya sumados. No hay que calcular nada; lo que hacia falta era que se
        // leyera de un vistazo y que se notara cuando NO es la impresa.
        final int dmg = card.getDamage();
        if (st != null && st.isCreature() && badgesVisible) {
            final int pow = st.getPower();
            final int tou = st.getToughness();
            ptBadge.setText(pow + "/" + tou);
            ptBadge.setVisible(hasArt);

            // Verde si esta mejorada, rojo si esta empeorada: es lo que dice de
            // un golpe "esto ya no es la carta que pone ahi impresa".
            final int[] printed = printedPt(st);
            final boolean up = printed != null && (pow > printed[0] || tou > printed[1]);
            final boolean down = printed != null && (pow < printed[0] || tou < printed[1]);
            ptBadge.pseudoClassStateChanged(BUFFED, up && !down);
            ptBadge.pseudoClassStateChanged(WEAKENED, down);

            // Y en rojo entero cuando el danyo marcado ya la mata: ahi no hay
            // que hacer ninguna resta de cabeza.
            final int lethal = card.getLethalDamage();
            ptBadge.pseudoClassStateChanged(LETHAL, dmg > 0 && lethal <= 0);
        } else {
            ptBadge.setVisible(false);
            ptBadge.pseudoClassStateChanged(BUFFED, false);
            ptBadge.pseudoClassStateChanged(WEAKENED, false);
            ptBadge.pseudoClassStateChanged(LETHAL, false);
        }

        // --- daño marcado, pegado a la P/T ---
        if (dmg > 0 && badgesVisible) {
            damageBadge.setText("-" + dmg);
            damageBadge.setVisible(true);
        } else {
            damageBadge.setVisible(false);
        }
        ptRow.setVisible(ptBadge.isVisible() || damageBadge.isVisible());

        // --- contadores (y la lealtad, que es un contador mas) ---
        refreshCounters();

        // --- marcadores del motor ---
        refreshMarkers(st);

        // --- estado ---
        // Girar y enderezar CON animacion. setTapped ya se corta solo cuando el
        // valor no cambia, asi que esto no relanza la transicion en cada aviso
        // del motor (que llegan decenas por turno): solo cuando de verdad se
        // tapa o se endereza.
        setTapped(card.isTapped(), true);
        setAdvanced(card.isAttacking() || card.isBlocking());
        pseudoClassStateChanged(SICK, card.isSick());
        pseudoClassStateChanged(ATTACKING, card.isAttacking());
        pseudoClassStateChanged(BLOCKING, card.isBlocking());
        pseudoClassStateChanged(COMMANDER, card.isCommander());
        // Desplazada (phasing): sigue en la mesa pero es como si no existiera.
        // Sin marcarlo ves una criatura que no ataca, no bloquea y no se puede
        // señalar, y no hay nada en pantalla que explique por que.
        pseudoClassStateChanged(PHASED, card.isPhasedOut());
    }

    private static final javafx.css.PseudoClass SICK = javafx.css.PseudoClass.getPseudoClass("sick");
    private static final javafx.css.PseudoClass ATTACKING = javafx.css.PseudoClass.getPseudoClass("attacking");
    private static final javafx.css.PseudoClass BLOCKING = javafx.css.PseudoClass.getPseudoClass("blocking");
    private static final javafx.css.PseudoClass COMMANDER = javafx.css.PseudoClass.getPseudoClass("commander");
    private static final javafx.css.PseudoClass SELECTABLE = javafx.css.PseudoClass.getPseudoClass("selectable");
    private static final javafx.css.PseudoClass HIGHLIGHTED = javafx.css.PseudoClass.getPseudoClass("highlighted");
    private static final javafx.css.PseudoClass DRAGGING = javafx.css.PseudoClass.getPseudoClass("dragging");
    private static final javafx.css.PseudoClass DROP_TARGET = javafx.css.PseudoClass.getPseudoClass("drop-target");
    private static final javafx.css.PseudoClass ACTIONABLE =
            javafx.css.PseudoClass.getPseudoClass("actionable");
    private static final javafx.css.PseudoClass AUTO_TAP =
            javafx.css.PseudoClass.getPseudoClass("auto-tap");
    private static final javafx.css.PseudoClass PHASED =
            javafx.css.PseudoClass.getPseudoClass("phased");

    public void setSelectable(final boolean on) {
        selectable = on;
        pseudoClassStateChanged(SELECTABLE, on);
    }

    /**
     * El resaltado <b>debil</b>: "esto lo puedes usar ahora mismo".
     *
     * <p>No lo calculamos nosotros, lo dice el motor
     * ({@code PlayerControllerHuman.pushActionableCards} →
     * {@code IGuiGame.setWeaklySelectable}) y viene con <b>fuerza</b>:
     *
     * <ul>
     *   <li><b>1</b> — puedes jugarla o activarla ahora. Durante un pago de
     *       mana, son las fuentes de mana que te quedan.</li>
     *   <li><b>2</b> — ademas es una de las que <b>taparia el boton "Auto"</b>.
     *       O sea: el plan del motor, antes de pulsarlo.</li>
     * </ul>
     *
     * <p>Es distinto de {@link #setSelectable}, que es "el motor esta
     * esperando que elijas una de estas": aquello es una obligacion y esto una
     * sugerencia, asi que se pintan distinto y el fuerte manda sobre el debil.
     */
    public void setActionable(final int strength) {
        this.actionable = strength >= 1;
        pseudoClassStateChanged(ACTIONABLE, actionable);
        pseudoClassStateChanged(AUTO_TAP, strength >= 2);
    }

    /** Si lleva el resaltado de "esto lo puedes usar". Para las pruebas. */
    public boolean isActionable() {
        return actionable;
    }

    private boolean actionable;

    /** Si el motor esta esperando que se cliquen cartas y esta es una de ellas. */
    public boolean isSelectable() {
        return selectable;
    }

    private boolean selectable;

    /**
     * Resaltado que pide el motor con {@code setHighlighted}.
     *
     * <p>Lo usa sobre todo el combate: el defensor actual mientras declaras
     * atacantes, y el atacante actual mientras declaras bloqueadores. Sin esto
     * no hay forma de saber sobre que estas trabajando.
     */
    public void setHighlighted(final boolean on) {
        pseudoClassStateChanged(HIGHLIGHTED, on);
    }

    /** La carta que se esta arrastrando ahora mismo. */
    public void setDragging(final boolean on) {
        pseudoClassStateChanged(DRAGGING, on);
    }

    /** Destino valido debajo del raton durante un arrastre. */
    public void setDropTarget(final boolean on) {
        pseudoClassStateChanged(DROP_TARGET, on);
    }

    /**
     * Hacia donde avanza al entrar en combate. -1 hacia arriba, +1 hacia abajo.
     * Lo fija quien coloca la carta: el campo propio empuja hacia la linea de
     * combate y el del rival, hacia abajo.
     */
    public void setCombatDirection(final int dir) {
        if (this.combatDirection != dir) {
            this.combatDirection = dir;
            applyAdvance(false);
        }
    }

    /**
     * Avanzar hacia la linea de combate, como en Arena.
     *
     * <p>Que la criatura DE UN PASO al atacar cuenta lo que ha pasado mucho
     * mejor que un icono: se ve el cambio con el rabillo del ojo.
     */
    public void setAdvanced(final boolean on) {
        if (this.advanced == on) {
            return;
        }
        this.advanced = on;
        applyAdvance(true);
    }

    private static final javafx.css.PseudoClass BUFFED =
            javafx.css.PseudoClass.getPseudoClass("buffed");
    private static final javafx.css.PseudoClass WEAKENED =
            javafx.css.PseudoClass.getPseudoClass("weakened");
    private static final javafx.css.PseudoClass LETHAL =
            javafx.css.PseudoClass.getPseudoClass("lethal");

    /**
     * La fuerza y resistencia IMPRESAS en la carta, o null si no se sabe.
     *
     * <p>El motor publica la actual, no la impresa, asi que la impresa hay que
     * sacarla de la carta en papel — el mismo camino que ya se usa para las
     * imagenes ({@code ImageUtil.getPaperCardFromImageKey}). Solo sirve para
     * decidir el color de la pastilla: si no hay carta en papel (una ficha, por
     * ejemplo) simplemente no se tinta.
     *
     * <p>Se cachea por clave de imagen porque {@code refresh()} se llama muchas
     * veces por segundo y esta busqueda no es gratis.
     */
    private static int[] printedPt(final CardStateView st) {
        final String key = st == null ? null : st.getImageKey();
        if (key == null || key.isEmpty()) {
            return null;
        }
        final int[] cached = PRINTED_PT.get(key);
        if (cached != null) {
            return cached.length == 0 ? null : cached;
        }
        int[] value = NO_PT;
        try {
            final forge.item.PaperCard pc = forge.util.ImageUtil.getPaperCardFromImageKey(key);
            if (pc != null && pc.getRules() != null && pc.getRules().getPower() != null) {
                value = new int[] {pc.getRules().getIntPower(), pc.getRules().getIntToughness()};
            }
        } catch (final RuntimeException ignored) {
            // Ficha, emblema o clave que no corresponde a una carta en papel.
        }
        PRINTED_PT.put(key, value);
        return value.length == 0 ? null : value;
    }

    /**
     * La fuerza y resistencia impresas, para quien las necesite fuera de aqui.
     *
     * <p>La usa la carta ampliada: ahi la imagen ensenya la P/T <b>impresa</b>,
     * asi que sin decir la actual al lado se lee justo lo contrario de lo que
     * pasa en la mesa — una criatura con contadores y un aura encima pone 0/7
     * en grande cuando de verdad es un 44/46.
     *
     * @return {fuerza, resistencia} o null si no se sabe (una ficha, un emblema)
     */
    public static int[] printedPowerToughness(final CardStateView st) {
        return printedPt(st);
    }

    private static final int[] NO_PT = new int[0];
    private static final java.util.Map<String, int[]> PRINTED_PT =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Tamano de letra de las pastillas de contador, en proporcion a la carta. */
    private double counterFontPx = 10;

    /**
     * Cuantas pastillas caben antes de que la carta parezca un semaforo.
     *
     * <p>Lo que se recorte sigue estando en el panel de detalle, en texto: si
     * algo se corta, tiene que haber forma de llegar a ello.
     */
    private static final int MAX_COUNTER_PILLS = 3;

    /**
     * Pinta los contadores de la carta.
     *
     * <p>Todo sale del motor: {@code CardView.getCounters()} da el recuento por
     * tipo y el propio {@link CounterType} trae hasta el <b>color</b> con el que
     * pintarlo ({@code getRed/getGreen/getBlue}). No hay ni una tabla nuestra.
     *
     * <p>Solo en el campo de batalla. Fuera de el, la lealtad que devuelve el
     * motor es la impresa en la carta y repetirla encima del arte solo estorba
     * (se veria en cada planeswalker del deck builder).
     */
    private void refreshCounters() {
        final Multiset<CounterType> all = card == null ? null : card.getCounters();
        final boolean inPlay = card != null && ZoneType.Battlefield == card.getZone();
        if (!inPlay || !countersVisible || all == null || all.isEmpty()) {
            counters.getChildren().clear();
            counters.setVisible(false);
            return;
        }

        // La lealtad primero: es el numero que se mira. Luego +1/+1 y -1/-1,
        // que son los que cambian el combate, y despues el resto por nombre.
        final List<CounterType> types = new ArrayList<>(all.elementSet());
        types.sort(Comparator.comparingInt(CardNode::counterRank)
                .thenComparing(CounterType::getName));

        counters.getChildren().clear();
        final boolean wide = cardWidth >= 108;
        int shown = 0;
        for (final CounterType t : types) {
            if (shown == MAX_COUNTER_PILLS && types.size() > MAX_COUNTER_PILLS) {
                final Label more = pill("+" + (types.size() - shown), Color.rgb(28, 34, 44));
                counters.getChildren().add(more);
                break;
            }
            final Label pill = pill(counterText(t, all.count(t), wide),
                    Color.rgb(t.getRed(), t.getGreen(), t.getBlue()));
            if (t.is(CounterEnumType.LOYALTY) || t.is(CounterEnumType.DEFENSE)) {
                // La lealtad es LA cifra de un planeswalker, igual que la P/T
                // lo es de una criatura: va del mismo tamano. Y la DEFENSA es
                // lo mismo para una Batalla — se mira igual y baja igual.
                pill.getStyleClass().add("card-counter-loyalty");
                applyCounterFont(pill);
            }
            counters.getChildren().add(pill);
            shown++;
        }
        counters.setVisible(true);
    }

    /**
     * Los marcadores que el motor pinta sobre la carta.
     *
     * <p>Es lo que hace visibles varias mecanicas modernas que <b>no son
     * contadores</b> y que sin esto no se ven en ninguna parte:
     *
     * <ul>
     *   <li><b>Velocidad</b> (Aetherdrift). El efecto "Start Your Engines!"
     *       vive en la zona de mando y el numero va aqui: sin el ves la carta
     *       pero no si vas a 1 o a 4, que es lo unico que importa.</li>
     *   <li><b>Nivel de una Clase</b> — el motor lo manda como {@code CL:2}.</li>
     *   <li><b>Cuanto te ha tentado el Anillo</b> — {@code RL:3}.</li>
     *   <li><b>En que habitacion estas</b> (las cartas Room de Duskmourn).</li>
     * </ul>
     *
     * <p>Las dos GUIs oficiales lo pintan (`CardPanel.drawMarkersTabs`,
     * `CardRenderer`); nosotros no, y por eso no se veia. Los prefijos crudos
     * del motor se traducen aqui: {@code CL:} y {@code RL:} son cripticos hasta
     * en ingles. El texto de superposicion (la velocidad) ya viene traducido
     * por el {@code Localizer}, asi que ese se deja tal cual.
     *
     * <p><b>La rueda de un Artilugio y las luces de una Atraccion</b> (D1 de
     * la auditoría del motor) no vienen en {@code getMarkerText()} — son propiedades
     * sueltas de {@code Card}/{@code CardStateView}, {@code getSprocket()} y
     * {@code getAttractionLights()} — pero son EXACTAMENTE el mismo problema:
     * estado que el motor calcula y que sin esto no se ve en ningun sitio (a
     * que rueda esta enganchado un Artilugio no esta impreso en la carta; las
     * luces de una Atraccion se sortean al entrar a la feria, tampoco). Un
     * Vehiculo o una Nave NO llevan marcador: su P/T impreso ya se ve en el
     * arte, y si estan tripulados ya lo dice la propia pastilla de P/T
     * (aparece o no aparece), que es la misma senyal que ya usa cualquier
     * criatura.
     */
    private void refreshMarkers(final CardStateView st) {
        final List<String> raw = card == null ? null : card.getMarkerText();
        final List<String> lines = raw == null ? new ArrayList<>() : markerLines(raw);
        // "Preparada" va la PRIMERA, y no viene de getMarkerText(): el motor no
        // la publica ahi. Ver isPrepared().
        if (isPrepared()) {
            lines.add(0, NeoText.get("marker.prepared"));
        }
        if (st != null) {
            if (st.isContraption() && card.getSprocket() > 0) {
                lines.add(NeoText.get("marker.sprocket", card.getSprocket()));
            }
            if (st.isAttraction() && st.getAttractionLights() != null
                    && !st.getAttractionLights().isEmpty()) {
                final List<Integer> lit = new ArrayList<>(st.getAttractionLights());
                Collections.sort(lit);
                final StringBuilder sb = new StringBuilder();
                for (int i = 0; i < lit.size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(lit.get(i));
                }
                lines.add(NeoText.get("marker.attractionLights", sb.toString()));
            }
        }
        // Se apagan con las pastillas, no con los contadores: en la carta
        // AMPLIADA no aportan nada (el texto se lee entero) y en cambio
        // tapan el titulo, que es justo donde caen.
        if (lines.isEmpty() || !countersVisible || !badgesVisible) {
            markers.getChildren().clear();
            markers.setVisible(false);
            return;
        }
        markers.getChildren().clear();
        for (final String line : lines) {
            final Label l = new Label(line);
            l.getStyleClass().add("card-marker");
            // La de "Preparada" no es un dato mas: es "aqui hay algo que puedes
            // hacer AHORA". Va en acento, como todo lo accionable, y no en el
            // ambar de los marcadores informativos.
            if (line.equals(NeoText.get("marker.prepared"))) {
                l.getStyleClass().add("card-marker-ready");
            }
            l.setStyle("-fx-font-size:" + (counterFontPx * 0.92) + "px;");
            // Lo que no cabe de ancho baja de linea. Un nombre de habitacion no
            // cabe en una carta de mesa por corto que sea el texto, y truncarlo
            // con puntos suspensivos dejaria un marcador que no dice nada.
            l.setWrapText(true);
            l.setMaxWidth(Math.max(36, cardWidth * 0.68));
            markers.getChildren().add(l);
        }
        markers.setVisible(!markers.getChildren().isEmpty());
    }

    /**
     * Si esta criatura esta <b>preparada</b> ahora mismo.
     *
     * <p>Cuidado con cual de las dos preguntas se hace, porque se parecen y
     * significan cosas opuestas:
     *
     * <ul>
     *   <li>{@code hasPreparedSpell()} es <b>estructural</b>: la carta TIENE
     *       una cara de hechizo. Es cierto para las 54 cartas de la mecanica
     *       siempre, esten preparadas o no — incluso en la mano.</li>
     *   <li>{@code getPreparedSpell()} no es null solo cuando <b>lo esta</b>:
     *       es la copia que el motor ha dejado en el exilio, y desaparece en
     *       cuanto lanzas el hechizo.</li>
     * </ul>
     *
     * <p>Preguntar la primera pondria la pastilla a una criatura que no puede
     * hacer nada, que es exactamente el aviso que no hay que dar.
     */
    private boolean isPrepared() {
        return card != null && card.getPreparedSpell() != null
                && card.getZone() == forge.game.zone.ZoneType.Battlefield;
    }

    /** Traduce los prefijos del motor y junta "In Room:" con su habitacion. */
    private static List<String> markerLines(final List<String> raw) {
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            final String s = raw.get(i);
            if (s == null || s.isBlank()) {
                continue;
            }
            if ("In Room:".equals(s)) {
                // El nombre de la habitacion viene en el elemento siguiente.
                final String room = i + 1 < raw.size() ? raw.get(i + 1) : "";
                out.add(NeoText.get("marker.room", room));
                i++;
            } else if (s.startsWith("CL:")) {
                out.add(NeoText.get("marker.classLevel", s.substring(3)));
            } else if (s.startsWith("RL:")) {
                out.add(NeoText.get("marker.ringLevel", s.substring(3)));
            } else {
                out.add(s);
            }
        }
        return out;
    }

    private static int counterRank(final CounterType t) {
        if (t.is(CounterEnumType.LOYALTY) || t.is(CounterEnumType.DEFENSE)) {
            return 0;
        }
        if (t.is(CounterEnumType.P1P1) || t.is(CounterEnumType.M1M1)) {
            return 1;
        }
        return 2;
    }

    /**
     * El texto de una pastilla.
     *
     * <p>El numero siempre; el nombre solo si la carta es lo bastante ancha
     * para que quepa. El color ya distingue el tipo, y el detalle completo esta
     * en el panel lateral al pasar el raton.
     */
    private static String counterText(final CounterType t, final int n, final boolean wide) {
        if (t.is(CounterEnumType.P1P1)) {
            return "+" + n;
        }
        if (t.is(CounterEnumType.M1M1)) {
            return "-" + n;
        }
        if (t.is(CounterEnumType.LOYALTY) || t.is(CounterEnumType.DEFENSE)) {
            return String.valueOf(n);
        }
        return wide ? n + " " + t.getCounterOnCardDisplayName() : String.valueOf(n);
    }

    private Label pill(final String text, final Color bg) {
        final Label l = new Label(text);
        l.getStyleClass().add("card-counter");
        // El color lo pone el motor, asi que va en linea y no en la hoja de
        // estilos: no hay una lista fija de contadores que podamos escribir.
        l.setStyle("-fx-background-color:" + toRgba(bg, 0.92) + ";"
                + "-fx-text-fill:" + (isLight(bg) ? "#0E1218" : "#FFFFFF") + ";");
        applyCounterFont(l);
        return l;
    }

    private void applyCounterFont(final Label l) {
        final double px = l.getStyleClass().contains("card-counter-loyalty")
                ? counterFontPx * 1.28 : counterFontPx;
        final String colours = l.getStyle();
        l.setStyle(colours.replaceAll("-fx-font-size:[^;]*;", "")
                + "-fx-font-size:" + px + "px;");
    }

    private static boolean isLight(final Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue() > 0.62;
    }

    private static String toRgba(final Color c, final double alpha) {
        return String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,%.2f)",
                (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255), alpha);
    }

    private void applyAdvance(final boolean animate) {
        baseTranslateY = advanced ? combatDirection * cardWidth * 0.20 : 0;
        if (lifted) {
            return; // el hover manda mientras la carta este levantada
        }
        if (!animate || !animations) {
            setTranslateY(baseTranslateY);
            return;
        }
        final TranslateTransition t = new TranslateTransition(TAP_TIME, this);
        t.setToY(baseTranslateY);
        t.setInterpolator(Interpolator.EASE_OUT);
        t.play();
    }

    /**
     * Marca visualmente la carta como comandante.
     *
     * <p>Existe aparte de {@link #refresh()} porque {@code CardView.isCommander()}
     * solo es cierto dentro de una partida: fuera de ella (banco de pruebas,
     * editor de mazos) hay que decirlo a mano.
     */
    public void setCommanderStyle(final boolean on) {
        pseudoClassStateChanged(COMMANDER, on);
    }

    // ---------------------------------------------------------------
    // Animaciones
    // ---------------------------------------------------------------

    /**
     * Girar la carta al taparse. 90 grados como en la mesa de verdad; lo que la
     * hace sentir moderna es que la rotacion esta ANIMADA, no que el angulo sea
     * distinto.
     */
    public void setTapped(final boolean tapped, final boolean animate) {
        if (this.tapped == tapped) {
            return;
        }
        this.tapped = tapped;
        if (!rotationEnabled) {
            // Vista de lectura: la carta se queda derecha aunque este girada.
            setRotate(0);
            return;
        }
        final double target = tapped ? 90 : 0;
        if (!animate || !animations) {
            setRotate(target);
            return;
        }
        final RotateTransition rt = new RotateTransition(TAP_TIME, this);
        rt.setToAngle(target);
        rt.setInterpolator(Interpolator.EASE_OUT);
        rt.play();
    }

    public boolean isTapped() {
        return tapped;
    }

    public void toggleTap() {
        setTapped(!tapped, true);
    }

    public void setHoverEnabled(final boolean on) {
        this.hoverEnabled = on;
        if (!on && lifted) {
            // Apagar el hover con la carta levantada la dejaria arriba para
            // siempre, y con el vigilante de la escena colgado.
            hoverOut();
        }
    }

    /**
     * Si la carta gira al taparse.
     *
     * <p>Se apaga en las vistas que existen para LEER la carta: la ampliada del
     * click derecho y el menu de habilidades. Ahi que salga tumbada solo estorba
     * — el jugador ya sabe que esta girada, la ha visto en la mesa; lo que
     * quiere es leerla.
     */
    public void setRotationEnabled(final boolean on) {
        this.rotationEnabled = on;
        if (!on) {
            setRotate(0);
        }
    }

    private boolean rotationEnabled = true;

    /**
     * Donde se pinta esta carta respecto a sus vecinas cuando el raton no esta
     * encima. Lo pone quien la coloca; ver {@link #baseViewOrder}.
     */
    public void setBaseViewOrder(final double order) {
        this.baseViewOrder = order;
        if (!lifted) {
            setViewOrder(order);
        }
    }

    /**
     * Cuanto hay que subir la pastilla de P/T porque debajo hay algo pintado.
     *
     * <p>Lo pide {@code CardStackNode} cuando la criatura lleva equipo o auras:
     * asoman por el borde de abajo, que es justo donde vive el numero que mas
     * se mira de la mesa. Subirlo un poco es mas barato que buscarle otra
     * esquina — sigue estando donde va impreso, solo que despejado.
     */
    public void setBottomInset(final double px) {
        if (Math.abs(px - bottomInset) < 0.5) {
            return;
        }
        this.bottomInset = px;
        StackPane.setMargin(ptRow, new javafx.geometry.Insets(0, 0, px, 0));
    }

    private double bottomInset;

    /**
     * Ensenya u oculta las pastillas de contador.
     *
     * <p>Aparte de {@code setBadgesVisible} a proposito: en la carta ampliada
     * los contadores se enumeran al lado, con su nombre entero y su cuenta, asi
     * que la pastilla encima del titulo solo tapa. En el reparto de danyo, en
     * cambio, hacen falta.
     */
    public void setCountersVisible(final boolean on) {
        this.countersVisible = on;
        refreshCounters();
    }

    private boolean countersVisible = true;

    /**
     * Esta carta esta enganchada a otra (aura, equipo, fortificacion).
     *
     * <p>Solo cambia como se ve: un borde propio para que se lea como una carta
     * aparte y no como un trozo de la de debajo.
     */
    public void setAttached(final boolean on) {
        pseudoClassStateChanged(ATTACHED, on);
    }

    private static final javafx.css.PseudoClass ATTACHED =
            javafx.css.PseudoClass.getPseudoClass("attached");

    /**
     * Ensenya u oculta las pastillas de P/T y dano.
     *
     * <p>Se apagan en la carta ampliada: a ese tamano la carta ya lleva impresa
     * su propia fuerza y resistencia, y la pastilla se le pone encima.
     */
    public void setBadgesVisible(final boolean on) {
        badgesVisible = on;
        refresh();
    }

    private boolean badgesVisible = true;

    /**
     * El rating del draft, 0-99 o {@code null} para apagarlo.
     *
     * <p>El numero lo calcula quien nos llama ({@code CardRanker.getRawScore},
     * en {@code forge-gui}) porque es logica de draft, no de presentacion: aqui
     * solo se pinta. Tres tonos y no una escala continua, para que se lea de un
     * vistazo y no haya que comparar dos numeros parecidos (principio 3 de
     * las notas de diseño, "el estado se ve, no se lee").
     */
    public void setDraftRank(final Integer rank) {
        this.draftRank = rank == null ? null : Math.max(0, Math.min(99, rank));
        refresh();
    }

    private Integer draftRank;

    private static final javafx.css.PseudoClass RANK_GOOD =
            javafx.css.PseudoClass.getPseudoClass("rank-good");
    private static final javafx.css.PseudoClass RANK_POOR =
            javafx.css.PseudoClass.getPseudoClass("rank-poor");

    /**
     * El raton ha salido... o eso parece.
     *
     * <p>Aqui vivia un parpadeo reportado jugando: <i>"pongo el raton encima de
     * una carta y parpadea muchas veces"</i>. La causa es geometrica y no se ve
     * leyendo el codigo del hover, porque el hover esta bien: el problema es
     * que <b>la carta se quita de debajo del raton ella sola</b>.
     *
     * <p>{@link #hoverIn()} la sube {@code 0,12 x ancho} y la agranda un 8 %.
     * Crecer baja el borde de abajo {@code 0,04 x alto = 0,056 x ancho}, o sea
     * menos de lo que sube: el borde inferior acaba <b>0,064 x ancho mas
     * arriba</b> que en reposo. Si el raton estaba en esa franja de abajo, al
     * levantarse la carta el raton queda fuera → sale el {@code MOUSE_EXITED} →
     * la carta vuelve → el raton esta dentro otra vez → {@code MOUSE_ENTERED}...
     * a la velocidad de los fotogramas. En la mano no se nota porque debajo no
     * hay nada, pero en una rejilla — el sobre del draft, el catalogo del deck
     * builder — se cae justo en el hueco entre filas.
     *
     * <p>Asi que una salida solo cuenta si el raton esta de verdad fuera del
     * sitio que la carta ocupa <b>en reposo</b>. Si esta dentro, la salida es
     * mentira y se ignora; y como entonces ya no llegan mas eventos a esta
     * carta, se vigila desde la escena hasta que salga de verdad
     * ({@link #watchRealExit()}).
     */
    private void leaving(final javafx.scene.input.MouseEvent e) {
        final javafx.geometry.Bounds resting = restingBoundsInParent();
        final javafx.scene.Parent parent = getParent();
        if (resting == null || parent == null) {
            hoverOut();
            return;
        }
        // Se mira en coordenadas del PADRE: las locales de la carta ya llevan
        // puesta la transformacion del hover, que es justo lo que hay que
        // deshacer.
        final javafx.geometry.Point2D p = parent.sceneToLocal(e.getSceneX(), e.getSceneY());
        if (resting.contains(p)) {
            watchRealExit();
        } else {
            hoverOut();
        }
    }

    /**
     * El rectangulo que ocupa la carta SIN el realce del hover.
     *
     * <p>Se calcula, no se mide: medir {@code getBoundsInParent()} devolveria
     * las de ahora, que son precisamente las levantadas. Se tiene en cuenta el
     * giro de tapar, que ensancha la caja.
     */
    private javafx.geometry.Bounds restingBoundsInParent() {
        final double w = getWidth();
        final double h = getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        double bw = w;
        double bh = h;
        final double rot = getRotate();
        if (rot != 0) {
            final double a = Math.toRadians(rot);
            final double cos = Math.abs(Math.cos(a));
            final double sin = Math.abs(Math.sin(a));
            bw = w * cos + h * sin;
            bh = w * sin + h * cos;
        }
        final double cx = getLayoutX() + w / 2 + getTranslateX();
        final double cy = getLayoutY() + h / 2 + baseTranslateY;
        return new javafx.geometry.BoundingBox(cx - bw / 2, cy - bh / 2, bw, bh);
    }

    /**
     * Vigila desde la escena hasta que el raton salga DE VERDAD.
     *
     * <p>Hace falta porque una vez ignorada la salida falsa esta carta ya no
     * recibe ningun evento mas: el raton esta encima del hueco que ha dejado,
     * que pertenece al padre. Un filtro en la escena es el unico sitio desde el
     * que se puede ver pasar.
     *
     * <p>Solo hay uno vivo a la vez y solo mientras una carta esta levantada,
     * asi que no es un coste que se acumule.
     */
    private void watchRealExit() {
        if (exitWatch != null) {
            return;
        }
        final javafx.scene.Scene sc = getScene();
        if (sc == null) {
            hoverOut();
            return;
        }
        watchedScene = sc;
        exitWatch = ev -> {
            final javafx.geometry.Bounds resting = restingBoundsInParent();
            final javafx.scene.Parent parent = getParent();
            // Si la carta se ha ido de la pantalla (una rejilla que se repinta),
            // el vigilante se apaga solo: si no, se quedaria colgado de la
            // escena para siempre.
            if (parent == null || getScene() == null || resting == null || !isVisible()) {
                hoverOut();
                return;
            }
            final javafx.geometry.Point2D p =
                    parent.sceneToLocal(ev.getSceneX(), ev.getSceneY());
            if (!resting.contains(p)) {
                hoverOut();
            }
        };
        sc.addEventFilter(javafx.scene.input.MouseEvent.ANY, exitWatch);
    }

    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> exitWatch;
    private javafx.scene.Scene watchedScene;

    /**
     * Si la carta esta levantada por el hover.
     *
     * <p>No vale {@code isHover()}: mientras se ignora una salida falsa
     * ({@link #leaving}) la carta sigue levantada pero JavaFX ya dice que el
     * raton no esta encima, y el avance del atacante le devolveria el sitio de
     * reposo a mitad del gesto.
     */
    private boolean lifted;

    private void stopWatching() {
        if (exitWatch != null && watchedScene != null) {
            watchedScene.removeEventFilter(javafx.scene.input.MouseEvent.ANY, exitWatch);
        }
        exitWatch = null;
        watchedScene = null;
    }

    private void hoverIn() {
        stopWatching();
        lifted = true;
        // Mientras el raton esta encima, delante de todo: da igual que esta
        // carta viva por DETRAS de otra (lo enganchado a una criatura), porque
        // pasar el raton por encima es justo el gesto de "ensenyamela".
        setViewOrder(Math.min(baseViewOrder, 0) - 1);
        setEffect(lift);
        if (!animations) {
            setScaleX(1.08);
            setScaleY(1.08);
            setTranslateY(baseTranslateY - cardWidth * 0.12);
            return;
        }
        final ScaleTransition s = new ScaleTransition(HOVER_TIME, this);
        s.setToX(1.08);
        s.setToY(1.08);
        s.setInterpolator(Interpolator.EASE_OUT);
        s.play();
        final TranslateTransition t = new TranslateTransition(HOVER_TIME, this);
        t.setToY(baseTranslateY - cardWidth * 0.12);
        t.setInterpolator(Interpolator.EASE_OUT);
        t.play();
    }

    private void hoverOut() {
        stopWatching();
        lifted = false;
        setViewOrder(baseViewOrder);
        setEffect(null);
        if (!animations) {
            setScaleX(1);
            setScaleY(1);
            setTranslateY(baseTranslateY);
            return;
        }
        final ScaleTransition s = new ScaleTransition(HOVER_TIME, this);
        s.setToX(1);
        s.setToY(1);
        s.setInterpolator(Interpolator.EASE_OUT);
        s.play();
        final TranslateTransition t = new TranslateTransition(HOVER_TIME, this);
        t.setToY(baseTranslateY);
        t.setInterpolator(Interpolator.EASE_OUT);
        t.play();
    }
}
