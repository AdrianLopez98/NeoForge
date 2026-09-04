package forge.neo.ui;

import java.util.ArrayList;
import java.util.List;

import forge.card.mana.ManaAtom;
import forge.neo.NeoText;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * Barra de un jugador: avatar, nombre, vida, mana y tamano de cada zona.
 *
 * <p>Como en Arena: la vida es lo mas grande de la barra, y el resto son
 * contadores pequenos que se leen de un vistazo sin robar atencion.
 *
 * <p>La zona de mando lleva contador aparte porque en Commander importa:
 * es donde se ve el impuesto y el dano de comandante.
 */
public class PlayerBar extends HBox {

    private final Label name = new Label();
    private final Label life = new Label("40");
    private final Label hand = new Label();
    private final Label library = new Label();
    private final Label graveyard = new Label();
    private final Label exile = new Label();
    private final Label command = new Label();
    private final HBox manaPips = new HBox(4);

    /**
     * Los contadores del JUGADOR: veneno, energia, experiencia, radiacion,
     * entradas y fragmentos de mana.
     *
     * <p>El motor los tiene desde siempre ({@code PlayerView.getCounters}) y
     * la GUI libGDX los pinta; nosotros los enterrabamos en el detalle que sale
     * al pasar el raton. Con el <b>veneno</b> eso es un fallo de verdad: a diez
     * se pierde la partida, y perder por algo que no se ve en pantalla no es
     * una derrota, es una sorpresa.
     */
    private final HBox playerCounters = new HBox(5);

    /**
     * Las reliquias de Ascenso de este jugador. Ver {@link #setRelics}.
     *
     * <p>Vacia en cualquier otro modo, y entonces no ocupa ni un pixel
     * ({@code setManaged(false)}).
     */
    private final HBox relics = new HBox(6);
    private final Label manaCaption = new Label(NeoText.get("bar.mana"));
    private final Circle avatar = new Circle(18);

    /**
     * A quien representa esta barra.
     *
     * <p>Hace falta porque el motor pide elegir JUGADOR en varios sitios
     * (quien empieza la partida, a quien va dirigido un hechizo, a quien
     * atacas). Forge lo resuelve con "click on the portrait": espera que la
     * interfaz llame a {@code IGameController.selectPlayer}. Sin esto, esas
     * peticiones cuelgan la partida para siempre.
     */
    private forge.game.player.PlayerView player;
    private java.util.function.Consumer<forge.game.player.PlayerView> onClick;
    private final Label turnMark = new Label(NeoText.get("bar.turn"));

    /**
     * El dano de comandante que has recibido, y del que mas te ha pegado.
     *
     * <p>Va SIEMPRE a la vista y no escondido en un detalle, porque es un
     * numero que en Commander se mira cada turno: a 21 te mueres, y el motor
     * aplica esa regla desde que se arreglaron los variantes. Sin esto se
     * puede perder una partida sin haberlo visto venir.
     *
     * <p>Solo aparece cuando hay dano: una pastilla a cero seria ruido en
     * todas las partidas en las que nadie conecta con su comandante.
     */
    private final HBox commanderDamage = new HBox(4);

    /**
     * El estado completo del jugador, al pasar el raton.
     *
     * <p>Lo compone el motor entero y traducido ({@code PlayerView.getDetails}
     * mas {@code getPlayerCommanderInfo}): vidas, veneno, cartas en mano y tu
     * maximo, tierras jugadas y tu maximo, robadas este turno, turnos extra,
     * el impuesto de cada comandante...
     *
     * <p>Al pasar el raton y NO al clicar: el click de la barra ya significa
     * "elijo a este jugador" y el motor lo necesita — quien empieza, a quien
     * atacas, a quien apunta un hechizo. Robarselo colgaria la partida.
     */
    private String detailsText = "";

    // Los trozos que se recolocan en modo compacto. Ver setCompact.
    private final HBox nameRow;
    private final HBox zones;
    private final VBox info;
    private final VBox manaBox;
    private final VBox lifeBox;
    private final Region spacer;
    private boolean compact;

    public PlayerBar(final boolean opponent) {
        getStyleClass().addAll("player-bar", opponent ? "player-bar-opponent" : "player-bar-self");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(18);
        setPadding(new Insets(8, 16, 8, 16));

        avatar.getStyleClass().add("avatar");

        name.getStyleClass().add("player-name");
        turnMark.getStyleClass().add("turn-mark");
        turnMark.setVisible(false);
        // El contenedor NO lleva la clase: la llevan las pastillas de dentro.
        // Con las dos, el fondo de la pastilla se pintaba tambien detras de la
        // fila entera y se veia un recuadro suelto cuando no habia nada.
        commanderDamage.setAlignment(Pos.CENTER_LEFT);
        commanderDamage.setVisible(false);
        commanderDamage.setManaged(false);

        final HBox nameRow = new HBox(8, name, turnMark, commanderDamage);
        nameRow.setAlignment(Pos.CENTER_LEFT);


        life.getStyleClass().add("life");
        final Label lifeCaption = new Label(NeoText.get("bar.life"));
        lifeCaption.getStyleClass().add("caption");
        final VBox lifeBox = new VBox(-4, life, lifeCaption);
        lifeBox.setAlignment(Pos.CENTER);

        final HBox zones = new HBox(14,
                zone(NeoText.get("zone.hand"), hand, forge.game.zone.ZoneType.Hand),
                zone(NeoText.get("zone.library"), library, forge.game.zone.ZoneType.Library),
                zone(NeoText.get("zone.graveyard"), graveyard, forge.game.zone.ZoneType.Graveyard),
                zone(NeoText.get("zone.exile"), exile, forge.game.zone.ZoneType.Exile),
                zone(NeoText.get("zone.command"), command, forge.game.zone.ZoneType.Command));
        zones.setAlignment(Pos.CENTER_LEFT);

        playerCounters.setAlignment(Pos.CENTER_LEFT);
        playerCounters.setVisible(false);
        playerCounters.setManaged(false);

        final VBox info = new VBox(4, nameRow, zones, playerCounters);
        info.setAlignment(Pos.CENTER_LEFT);
        this.nameRow = nameRow;
        this.zones = zones;
        this.info = info;

        manaPips.setAlignment(Pos.CENTER_LEFT);
        manaPips.getStyleClass().add("mana-pool");
        manaPips.setPadding(new Insets(3, 8, 3, 8));

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);

        // La reserva de mana va junto a los contadores de zona y con su titulo,
        // como el resto: es un dato que se consulta constantemente mientras
        // decides que lanzar, no un adorno al margen.
        manaCaption.getStyleClass().add("caption");
        manaCaption.setVisible(false);
        final VBox manaBox = new VBox(-2, manaPips, manaCaption);
        manaBox.setAlignment(Pos.CENTER_LEFT);
        this.manaBox = manaBox;
        this.lifeBox = lifeBox;
        this.spacer = gap;

        relics.setAlignment(Pos.CENTER_RIGHT);
        relics.setVisible(false);
        relics.setManaged(false);

        getChildren().addAll(avatar, info, gap, relics, manaBox, lifeBox);

        // Toda la barra es zona de click, no solo el circulo: es un objetivo
        // mucho mas facil de acertar.
        setOnMouseClicked(e -> {
            if (onClick != null && player != null) {
                onClick.accept(player);
            }
        });
    }

    /**
     * La barra en DOS filas, para cuando no hay ancho.
     *
     * <p>Con tres rivales en fila cada barra se queda con un tercio de la
     * pantalla, y esta barra <b>no encoge</b>: lleva avatar, nombre, los cinco
     * contadores de zona, la reserva de mana y la vida, y por debajo de su
     * ancho natural (~520 px) se <b>recorta</b>. Medido a 1024 de ancho con
     * tres rivales: desaparecia la VIDA de los tres, que es justo el numero por
     * el que decides a quien atacas.
     *
     * <p>Y esto NO lo arregla el zoom de la mesa, aunque arregle las cartas: la
     * barra vive fuera del visor, asi que Ctrl+rueda no la toca. Habia que
     * arreglarlo aqui.
     *
     * <p>La solucion es crecer hacia abajo, que es lo que sobra: en la fila de
     * rivales las mesas quedan limitadas por el ANCHO y les sobra altura
     * (medido — darles mas alto no agranda ni un pixel las cartas). Asi que la
     * vida sube a la fila del nombre y el resto se apila debajo. <b>No se
     * esconde ni un dato</b>: con las mesas de rival sin su tira de cementerio
     * y exilio, estos contadores son el unico sitio donde se ven.
     */
    public void setCompact(final boolean on) {
        if (compact == on) {
            return;
        }
        compact = on;
        if (on) {
            nameRow.getChildren().addAll(spacer, lifeBox);
            info.getChildren().setAll(nameRow, zones, manaBox, playerCounters, relics);
            zones.setSpacing(6);
            setSpacing(10);
            setPadding(new Insets(6, 10, 6, 10));
            getChildren().setAll(avatar, info);
            HBox.setHgrow(info, Priority.ALWAYS);
        } else {
            nameRow.getChildren().removeAll(spacer, lifeBox);
            info.getChildren().setAll(nameRow, zones, playerCounters);
            zones.setSpacing(14);
            setSpacing(18);
            setPadding(new Insets(8, 16, 8, 16));
            getChildren().setAll(avatar, info, spacer, relics, manaBox, lifeBox);
        }
        requestLayout();
    }

    /** true si la barra esta en su version de dos filas. */
    public boolean isCompact() {
        return compact;
    }

    public void setPlayer(final forge.game.player.PlayerView p) {
        this.player = p;
    }

    /**
     * Cuanto dano de comandante llevas y de quien.
     *
     * <p>Se pinta el PEOR, que es el que te va a matar. El desglose completo va
     * en el detalle, con el nombre de cada comandante.
     *
     * @param worst el dano mas alto recibido de un solo comandante
     */
    public void setCommanderDamage(final int worst) {
        setCommanderDamage(worst > 0
                ? List.of(new CommanderHit(null, worst)) : List.of());
    }

    /**
     * Cuanto dano de comandante llevas, <b>y de quien</b>.
     *
     * <p>Una pastilla por comandante que te haya pegado, porque el limite de
     * 21 es <b>por comandante</b> y no en total: el motor lo lleva en un
     * {@code Map<Card, Integer>} y la derrota se comprueba entrada a entrada
     * ({@code Player.java}, {@code if (entry.getValue() >= 21)}). O sea que
     * tres rivales pegandote 10 cada uno <b>no</b> te matan, y una sola
     * pastilla con el peor numero no deja verlo — que era justo la duda al
     * jugar: no habia forma de saber si ese 10 era de uno o la suma de tres.
     *
     * <p>Se ordenan de mas a menos: la que te va a matar va primero.
     */
    public void setCommanderDamage(final List<CommanderHit> hits) {
        final List<CommanderHit> list = hits == null ? List.<CommanderHit>of() : hits;
        final boolean show = !list.isEmpty();
        commanderDamage.setVisible(show);
        commanderDamage.setManaged(show);
        commanderDamage.getChildren().clear();
        if (!show) {
            return;
        }
        final List<CommanderHit> sorted = new ArrayList<>(list);
        sorted.sort((a, b) -> Integer.compare(b.damage(), a.damage()));
        for (final CommanderHit hit : sorted) {
            final Label pill = new Label(hit.who() == null
                    ? NeoText.get("bar.commanderDamage", hit.damage(), LETHAL_COMMANDER)
                    : NeoText.get("bar.commanderDamage.from", hit.who(), hit.damage(),
                            LETHAL_COMMANDER));
            pill.getStyleClass().add("commander-damage");
            // Ambar cuando queda poco, rojo cuando un golpe mas puede matarte.
            // Es el codigo de color de la seccion 6: aviso y peligro. Y por
            // pastilla, no por el peor: lo que importa es cual de ellas esta
            // cerca de los 21.
            pill.pseudoClassStateChanged(CMD_WARN,
                    hit.damage() >= LETHAL_COMMANDER / 2
                            && hit.damage() < LETHAL_COMMANDER - 6);
            pill.pseudoClassStateChanged(CMD_DANGER, hit.damage() >= LETHAL_COMMANDER - 6);
            commanderDamage.getChildren().add(pill);
        }
    }

    /**
     * Un comandante que te ha pegado, y cuanto llevas de el.
     *
     * @param who    como se le llama en la pastilla, o null para no decirlo
     *               (partidas a dos: solo puede ser uno)
     * @param damage cuanto dano de ESE comandante llevas encima
     */
    public record CommanderHit(String who, int damage) {
    }

    /** A los 21 de un mismo comandante se pierde la partida (CR 903.10a). */
    public static final int LETHAL_COMMANDER = 21;

    private static final javafx.css.PseudoClass CMD_WARN =
            javafx.css.PseudoClass.getPseudoClass("warn");
    private static final javafx.css.PseudoClass CMD_DANGER =
            javafx.css.PseudoClass.getPseudoClass("danger");

    /**
     * El estado completo del jugador, para ensenyarlo al pasar el raton.
     *
     * <p>Se guarda aqui pero lo PINTA la mesa, en un panel dentro de la
     * escena. Un {@code Tooltip} de JavaFX seria mas corto de escribir, pero
     * es una <b>ventana aparte</b>: no le afecta la escala de interfaz del
     * proyecto — que se aplica como tamanyo de fuente en la raiz de la escena —
     * asi que a 130% se quedaria pequenyo mientras todo lo demas crece.
     */
    public void setDetails(final String text) {
        this.detailsText = text == null ? "" : text;
    }

    public String getDetailsText() {
        return detailsText;
    }

    /**
     * La cara del retrato.
     *
     * <p>Se pinta como relleno del circulo y no como una imagen encima, para
     * que el recorte redondo salga solo. {@code ImagePattern} con las cuatro
     * coordenadas a 0,0,1,1 y {@code proportional} estira la imagen al circulo:
     * los avatares de Forge son cuadrados y los importados casi nunca, asi que
     * sin eso saldrian descentrados.
     *
     * <p>Un null deja el circulo gris de siempre, que es lo que hay que ver
     * cuando no se ha podido leer la imagen.
     */
    public void setAvatarImage(final javafx.scene.image.Image image) {
        if (image == null) {
            avatar.setFill(null);
            avatar.getStyleClass().remove("avatar-image");
            return;
        }
        avatar.setFill(new javafx.scene.paint.ImagePattern(image, 0, 0, 1, 1, true));
        if (!avatar.getStyleClass().contains("avatar-image")) {
            avatar.getStyleClass().add("avatar-image");
        }
    }

    public forge.game.player.PlayerView getPlayer() {
        return player;
    }

    /**
     * El retrato, no la barra entera.
     *
     * <p>Es donde tienen que morir las flechas de ataque: en Arena la flecha
     * apunta a la cara del rival, y con seis atacantes eso se lee mucho mejor
     * que seis puntas repartidas por una barra de mil pixeles de ancho.
     */
    public javafx.scene.Node getPortrait() {
        return avatar;
    }

    public void setOnPlayerClicked(final java.util.function.Consumer<forge.game.player.PlayerView> h) {
        this.onClick = h;
    }

    /** Resalta la barra cuando el motor esta pidiendo elegir un jugador. */
    public void setSelectable(final boolean on) {
        pseudoClassStateChanged(SELECTABLE, on);
        setCursor(on ? javafx.scene.Cursor.HAND : javafx.scene.Cursor.DEFAULT);
    }

    private static final javafx.css.PseudoClass SELECTABLE =
            javafx.css.PseudoClass.getPseudoClass("selectable");

    private static final javafx.css.PseudoClass HIGHLIGHTED =
            javafx.css.PseudoClass.getPseudoClass("highlighted");

    /**
     * Resaltado que pide el motor con {@code setHighlighted}.
     *
     * <p>Mientras declaras atacantes, este es el jugador al que vas a atacar.
     * Es la informacion mas importante de la fase y en Forge Swing no se ve por
     * ningun lado.
     */
    public void setHighlighted(final boolean on) {
        pseudoClassStateChanged(HIGHLIGHTED, on);
    }

    /**
     * Un contador de zona, clicable para ver lo que hay dentro.
     *
     * <p>Saber que hay CINCO cartas en tu cementerio no sirve de nada: lo que
     * necesitas saber es CUALES. Muchisimas cartas se juegan desde el
     * cementerio o el exilio, y en Commander la zona de mando se consulta cada
     * dos por tres.
     */
    private Region zone(final String caption, final Label value,
                        final forge.game.zone.ZoneType type) {
        value.getStyleClass().add("zone-count");
        final Label c = new Label(caption);
        c.getStyleClass().add("caption");
        final VBox box = new VBox(-2, value, c);
        box.getStyleClass().add("zone-button");
        box.setAlignment(Pos.CENTER);
        box.setMinWidth(42);
        box.setCursor(javafx.scene.Cursor.HAND);
        box.setOnMouseClicked(e -> {
            // Que no llegue a la barra: mirar una zona no es "elegir a este
            // jugador" como objetivo.
            e.consume();
            if (onZone != null && player != null) {
                onZone.accept(player, type);
            }
        });
        return box;
    }

    /**
     * Las reliquias de Ascenso que lleva este jugador, en la barra.
     *
     * <h2>Por que no basta con el contador de la zona de mando</h2>
     *
     * <p>Del rival <b>no se ve la zona de mando</b>: es un contador ("2
     * MANDO") que hay que clicar. Para un comandante da igual — se ve en la
     * mesa en cuanto lo lanza —, pero una reliquia de Ascenso <b>nunca sale de
     * ahi</b>: es una pasiva permanente que esta actuando todo el rato y que
     * cambia como se juega el combate entero. Reportado jugando el 03-09-2026
     * contra el jefe del acto 1: <i>"necesito poder ver sus reliquias, esa
     * data es muy vital"</i>. Es el principio 3 — el estado se ve, no se lee —
     * y ademas el mismo caso exacto que ya obligo a sacar la velocidad aqui
     * ({@code PlayerSpeed}): un dato que el motor solo publica como una carta
     * dentro del mando, o sea invisible.
     *
     * <h2>Por que esto no toca los demas modos</h2>
     *
     * <p>No hay ni una bandera de modo. Quien llama a esto
     * ({@code TableBinder}) solo mete las cartas que {@code AscentRelics}
     * reconoce por nombre, y esas cartas <b>solo existen si se ha jugado a
     * Ascenso</b>: en Commander, en Estandar, en la aventura o en un draft la
     * lista sale vacia sola y la fila ni se pinta. Un interruptor "estoy en
     * Ascenso" habria que pasarlo por cuatro sitios y podria quedarse mal
     * puesto; esto no puede.
     *
     * <p>Cada pastilla lleva el emblema y el nombre, y se clica para ver la
     * carta entera con lo que hace. El emblema va del color de su rareza, el
     * mismo que la carta ({@code RelicArt.frameOf}): son la misma reliquia
     * vista de dos maneras.
     */
    public void setRelics(final java.util.List<forge.game.card.CardView> cards) {
        relics.getChildren().clear();
        if (cards == null || cards.isEmpty()) {
            relics.setVisible(false);
            relics.setManaged(false);
            return;
        }
        for (final forge.game.card.CardView card : cards) {
            final forge.neo.ascent.AscentRelic relic =
                    forge.neo.ascent.AscentRelics.byCardName(nameOf(card));
            if (relic == null) {
                continue;
            }
            relics.getChildren().add(relicChip(relic, card));
        }
        final boolean any = !relics.getChildren().isEmpty();
        relics.setVisible(any);
        relics.setManaged(any);
    }

    private static String nameOf(final forge.game.card.CardView card) {
        try {
            return card == null || card.getCurrentState() == null
                    ? null : card.getCurrentState().getName();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Una reliquia: su emblema y su nombre, y la carta detras del click. */
    private Region relicChip(final forge.neo.ascent.AscentRelic relic,
                             final forge.game.card.CardView card) {
        final javafx.scene.paint.Color frame =
                forge.neo.card.RelicArt.frameOf(relic.getRarity());
        final javafx.scene.paint.Color ink =
                forge.neo.card.RelicArt.inkOf(relic.getRarity());
        final javafx.scene.paint.Color hole =
                forge.neo.card.RelicArt.holeOf(relic.getRarity());

        final double size = 20;
        final javafx.scene.Group art = forge.neo.card.RelicEmblem.of(
                forge.neo.card.RelicEmblem.motifOf(relic.getId()), size, ink, hole);
        final StackPane holder = new StackPane(art);
        holder.setMinSize(size, size);
        holder.setPrefSize(size, size);
        holder.setMaxSize(size, size);

        final Label label = new Label(relic.getCardName());
        label.getStyleClass().add("relic-chip-name");
        label.setStyle("-fx-text-fill: " + hex(ink) + ";");

        final HBox chip = new HBox(6, holder, label);
        chip.getStyleClass().add("relic-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setStyle("-fx-border-color: " + hex(frame) + ";");
        chip.setCursor(javafx.scene.Cursor.HAND);
        // Como los contadores de zona: se consume para que mirar una reliquia
        // no signifique ademas elegir a este jugador como objetivo.
        chip.setOnMouseClicked(e -> {
            e.consume();
            forge.neo.ui.CardZoom.show(chip, card);
        });
        return chip;
    }

    private static String hex(final javafx.scene.paint.Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    /** Que hacer al clicar un contador de zona. */
    public void setOnZoneClicked(
            final java.util.function.BiConsumer<forge.game.player.PlayerView,
                    forge.game.zone.ZoneType> handler) {
        this.onZone = handler;
    }

    private java.util.function.BiConsumer<forge.game.player.PlayerView,
            forge.game.zone.ZoneType> onZone;

    // ---------------------------------------------------------------

    public void setPlayerName(final String n) {
        name.setText(n);
    }

    /**
     * La vida, con animacion y color.
     *
     * <p>Es el numero que decide la partida y hasta ahora cambiaba en silencio:
     * la IA resuelve su turno en milisegundos y te encuentras con diez vidas
     * menos sin haber visto nada. Verde si sube, rojo si baja, y un golpe de
     * escala para que el ojo vaya solo ahi.
     *
     * <p>El color se queda puesto casi un segundo. Menos no da tiempo a verlo
     * si estabas mirando otra parte de la mesa, que es justo el caso.
     */
    public void setLife(final int v) {
        final boolean changed = lastLife != Integer.MIN_VALUE && v != lastLife;
        final boolean up = v > lastLife;
        lastLife = v;
        life.setText(String.valueOf(v));
        if (!changed) {
            return;
        }
        life.getStyleClass().removeAll("life-up", "life-down");
        life.getStyleClass().add(up ? "life-up" : "life-down");
        Anim.bump(life);
        if (Boolean.getBoolean("neo.anim.debug")) {
            System.out.printf("[anim] vida %s: %d (%s)%n",
                    name.getText(), v, up ? "sube" : "baja");
        }

        // Un solo temporizador: con varios golpes seguidos (un rayo y luego el
        // combate) el color tiene que durar desde el ULTIMO, no apagarse a
        // media cuenta del anterior.
        if (lifeReset == null) {
            lifeReset = new javafx.animation.PauseTransition(
                    javafx.util.Duration.millis(900));
            lifeReset.setOnFinished(e ->
                    life.getStyleClass().removeAll("life-up", "life-down"));
        }
        lifeReset.playFromStart();
    }

    private int lastLife = Integer.MIN_VALUE;
    private javafx.animation.PauseTransition lifeReset;

    public void setActiveTurn(final boolean active) {
        turnMark.setVisible(active);
        pseudoClassStateChanged(ACTIVE, active);
    }

    private static final javafx.css.PseudoClass ACTIVE =
            javafx.css.PseudoClass.getPseudoClass("active");

    public void setZones(final int handSize, final int librarySize, final int graveSize,
                         final int exileSize, final int commandSize) {
        hand.setText(String.valueOf(handSize));
        library.setText(String.valueOf(librarySize));
        graveyard.setText(String.valueOf(graveSize));
        exile.setText(String.valueOf(exileSize));
        command.setText(String.valueOf(commandSize));
    }

    /**
     * Pips del mana FLOTANTE, en orden WUBRG + incoloro.
     *
     * <p>Ojo con la lectura: {@code PlayerView.getMana(color)} es la reserva de
     * mana, no lo que podrias producir. Fuera de un pago casi siempre esta
     * vacia, y eso es correcto.
     */
    public void setMana(final int w, final int u, final int b, final int r, final int g, final int c) {
        manaPips.getChildren().clear();
        // La codificacion es la de ManaAtom, no la de MagicColor: es la que usa
        // la reserva del motor y la que espera IGameController.useMana. Solo se
        // diferencian en el incoloro, que en MagicColor vale 0.
        addPip(ManaAtom.WHITE, "W", w);
        addPip(ManaAtom.BLUE, "U", u);
        addPip(ManaAtom.BLACK, "B", b);
        addPip(ManaAtom.RED, "R", r);
        addPip(ManaAtom.GREEN, "G", g);
        addPip(ManaAtom.COLORLESS, "C", c);
        manaCaption.setVisible(!manaPips.getChildren().isEmpty());
    }

    /**
     * Los contadores del jugador, en pastillas junto a las zonas.
     *
     * <p>Se pintan <b>los que tiene</b>, ni uno mas: una fila de ceros en cada
     * partida seria ruido. El color y el nombre corto los da el propio
     * {@code CounterType}, asi que no hay ninguna lista escrita a mano que se
     * quede corta cuando salga un contador nuevo.
     *
     * <p>El <b>veneno</b> se marca en rojo cuando ya es letal: es el unico de
     * estos que te hace perder la partida, y cuantos hacen falta lo dice el
     * motor ({@code GameView.getPoisonCountersToLose}), que sabe de variantes.
     */
    public void setCounters(final com.google.common.collect.Multiset<forge.game.card.CounterType> counters,
                            final int shards, final int poisonToLose,
                            final String controlledBy,
                            final int speed, final String speedText) {
        playerCounters.getChildren().clear();
        addSpeedPill(speed, speedText);
        if (controlledBy != null && !controlledBy.isBlank()) {
            // Alguien te esta jugando el turno (Mindslaver y compania). Pasa
            // poquisimo, y justo por eso hay que decirlo: sin un cartel, lo que
            // ve el jugador es que la partida hace cosas solas.
            final Label pill = new Label(NeoText.get("bar.controlledBy", controlledBy));
            pill.getStyleClass().addAll("player-counter", "player-counter-control");
            playerCounters.getChildren().add(pill);
        }
        if (counters != null) {
            final java.util.List<forge.game.card.CounterType> types =
                    new java.util.ArrayList<>(counters.elementSet());
            types.sort(java.util.Comparator.comparing(forge.game.card.CounterType::getName));
            for (final forge.game.card.CounterType type : types) {
                final int n = counters.count(type);
                if (n <= 0) {
                    continue;
                }
                final Label pill = new Label(n + " " + counterName(type));
                pill.getStyleClass().add("player-counter");
                if (type.is(forge.game.card.CounterEnumType.POISON)) {
                    pill.getStyleClass().add("player-counter-poison");
                    pill.pseudoClassStateChanged(COUNTER_LETHAL,
                            poisonToLose > 0 && n >= poisonToLose);
                } else {
                    // El color lo pone el motor: no hay lista fija que mantener.
                    pill.setStyle("-fx-border-color: rgb(" + type.getRed() + ","
                            + type.getGreen() + "," + type.getBlue() + ");");
                }
                playerCounters.getChildren().add(pill);
            }
        }
        if (shards > 0) {
            // Los fragmentos de mana de Alchemy no son un contador: van aparte.
            final Label pill = new Label(shards + " " + NeoText.get("bar.shards"));
            pill.getStyleClass().add("player-counter");
            playerCounters.getChildren().add(pill);
        }
        final boolean any = !playerCounters.getChildren().isEmpty();
        playerCounters.setVisible(any);
        playerCounters.setManaged(any);
    }

    private static final javafx.css.PseudoClass COUNTER_LETHAL =
            javafx.css.PseudoClass.getPseudoClass("lethal");

    private static final javafx.css.PseudoClass SPEED_MAX =
            javafx.css.PseudoClass.getPseudoClass("max");

    /**
     * La velocidad, la primera de las pastillas.
     *
     * <p>Va aqui y no en la zona de mando porque del rival <b>no se ve la zona
     * de mando</b>, solo su contador: la velocidad del rival era invisible a
     * menos que se te ocurriera abrirsela, y es justo el dato por el que
     * decides si te puede matar este turno.
     *
     * <p>La maxima se marca aparte. No es "uno mas que tres": es el escalon en
     * el que se encienden las habilidades que dicen <i>si vas a velocidad
     * maxima</i>, o sea que cambia lo que las cartas hacen. Ver
     * {@link forge.neo.match.PlayerSpeed}, que explica por que hay que
     * deducirla en vez de pedirla.
     */
    private void addSpeedPill(final int speed, final String speedText) {
        if (speed == forge.neo.match.PlayerSpeed.NONE) {
            return;
        }
        final String text;
        if (speed == forge.neo.match.PlayerSpeed.MAX) {
            text = NeoText.get("bar.speedMax");
        } else if (speed == forge.neo.match.PlayerSpeed.UNREADABLE) {
            // No se pudo leer el numero. Se ensenya lo que dice el motor, que
            // ya viene traducido, en vez de inventarse una cifra.
            if (speedText == null || speedText.isBlank()) {
                return;
            }
            text = speedText;
        } else {
            text = NeoText.get("bar.speed", String.valueOf(speed));
        }
        final Label pill = new Label(text);
        pill.getStyleClass().addAll("player-counter", "player-counter-speed");
        pill.pseudoClassStateChanged(SPEED_MAX, speed == forge.neo.match.PlayerSpeed.MAX);
        playerCounters.getChildren().add(pill);
    }

    /**
     * Como se llama un contador de jugador en cristiano.
     *
     * <p>El motor los abrevia para que quepan sobre una carta ({@code POISN},
     * {@code NRG}) y <b>no los traduce</b> ni siquiera en su propia interfaz.
     * Aqui hay sitio de sobra, asi que los cinco que existen de verdad llevan
     * su nombre entero; cualquier otro sale como lo diga el motor, que es
     * mejor que no salir.
     */
    private static String counterName(final forge.game.card.CounterType type) {
        if (type.is(forge.game.card.CounterEnumType.POISON)) {
            return NeoText.get("counter.poison");
        }
        if (type.is(forge.game.card.CounterEnumType.ENERGY)) {
            return NeoText.get("counter.energy");
        }
        if (type.is(forge.game.card.CounterEnumType.EXPERIENCE)) {
            return NeoText.get("counter.experience");
        }
        if (type.is(forge.game.card.CounterEnumType.RAD)) {
            return NeoText.get("counter.rad");
        }
        if (type.is(forge.game.card.CounterEnumType.TICKET)) {
            return NeoText.get("counter.ticket");
        }
        return type.getCounterOnCardDisplayName();
    }

    /**
     * Que hacer al clicar un pip de mana.
     *
     * <p>Es como se gasta el mana flotante durante un pago: el motor lo recibe
     * por {@code IGameController.useMana(byte)}. Sin esto, el mana que sobra de
     * una tierra que produce dos se queda ahi y no hay forma de usarlo.
     */
    public void setOnManaClicked(final java.util.function.Consumer<Byte> handler) {
        this.onMana = handler;
    }

    /** Destaca la reserva de mana mientras hay un pago en curso. */
    public void setManaPoolActive(final boolean on) {
        manaPips.pseudoClassStateChanged(POOL_ACTIVE, on);
    }

    private static final javafx.css.PseudoClass POOL_ACTIVE =
            javafx.css.PseudoClass.getPseudoClass("pool-active");

    private java.util.function.Consumer<Byte> onMana;

    private void addPip(final int colour, final String letter, final int amount) {
        if (amount <= 0) {
            return;
        }
        final Label pip = new Label(amount > 1 ? String.valueOf(amount) : "");
        pip.getStyleClass().addAll("mana-pip", "mana-" + letter.toLowerCase());
        pip.setAlignment(Pos.CENTER);
        final StackPane sp = new StackPane(pip);
        sp.setCursor(javafx.scene.Cursor.HAND);
        sp.setOnMouseClicked(e -> {
            // No dejar que el click llegue a la barra: clicar un pip no debe
            // valer ademas como "elegir a este jugador".
            e.consume();
            if (onMana != null) {
                onMana.accept((byte) colour);
            }
        });
        manaPips.getChildren().add(sp);
    }
}
