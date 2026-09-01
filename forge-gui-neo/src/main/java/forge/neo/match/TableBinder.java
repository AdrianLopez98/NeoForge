package forge.neo.match;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import forge.card.mana.ManaAtom;
import forge.game.GameEntityView;
import forge.game.GameView;
import forge.game.card.CardView;
import forge.game.combat.CombatView;
import forge.game.player.PlayerView;
import forge.game.spellability.StackItemView;
import forge.game.zone.ZoneType;
import forge.neo.ui.CombatOverlay;
import forge.neo.ui.TableScreen;
import forge.util.collect.FCollectionView;
import javafx.application.Platform;

/**
 * Vuelca el estado real de la partida en la mesa.
 *
 * <p>Es TODO el trabajo de la fase 3: la pantalla ya estaba hecha, aqui solo se
 * cambia de donde salen los datos. En vez de repartir cartas del mazo a mano,
 * se leen del {@link GameView} que el motor nos empuja.
 *
 * <p>Dos reglas que respeta:
 * <ul>
 *   <li><b>Solo lee vistas.</b> Nunca toca el estado vivo del motor. Todo sale
 *       de {@code GameView}, {@code PlayerView} y {@code CardView}.</li>
 *   <li><b>Refresco agrupado.</b> El motor avisa de cambios decenas de veces
 *       por turno. Repintar en cada aviso tiraria los fps, asi que se marca
 *       "hay que refrescar" y se hace una sola pasada en el siguiente pulso de
 *       JavaFX.</li>
 * </ul>
 */
public class TableBinder {

    private final TableScreen table;

    /** Evita repintar N veces seguidas: una pasada por pulso basta. */
    private final AtomicBoolean pending = new AtomicBoolean(false);

    private volatile GameView gameView;
    private volatile PlayerView self;

    /**
     * La interfaz que conduce esta partida.
     *
     * <p>Lo necesita el menu de pausa: es quien sabe salir de un match en curso
     * sin dejar al motor bloqueado.
     */
    private volatile NeoMatchUI matchUi;

    public void setMatchUi(final NeoMatchUI ui) {
        this.matchUi = ui;
    }

    public NeoMatchUI getMatchUi() {
        return matchUi;
    }

    /** Rival que se esta viendo arriba. Con mas de uno se elige por pestanyas. */
    private volatile PlayerView viewedOpponent;

    /**
     * A quien le tocaba la ultima vez que se repinto.
     *
     * <p>Sirve para cambiar de pestanya SOLO cuando cambia el turno. Si se
     * siguiera al jugador activo en cada refresco, clicar una pestanya para
     * mirar a otro rival no serviria de nada: volveria sola al momento.
     */
    private volatile PlayerView lastTurnPlayer;

    public TableBinder(final TableScreen table) {
        this.table = table;
        table.setOnOpponentSelected(p -> {
            viewedOpponent = p;
            // La pestanya no solo elige a quien MIRAS: mientras declaras
            // atacantes elige tambien a quien ATACAS. Ver NeoMatchUI.
            final NeoMatchUI ui = matchUi;
            if (ui != null) {
                ui.onOpponentFocused(p);
            }
            requestRefresh();
        });
    }

    /**
     * Quien se sienta abajo. En una partida normal es el jugador local; como
     * espectador no hay ninguno, asi que se coge el primero.
     */
    public TableScreen getTable() {
        return table;
    }

    public void setSelf(final PlayerView player) {
        this.self = player;
        requestRefresh();
    }

    public void setGameView(final GameView view) {
        // Cada partida de un Bo3 (draft/sellado con banquillo) es un Game
        // NUEVO del motor, con sus propios PlayerView — y sus ids de
        // asiento se REPARTEN OTRA VEZ desde 0 (Game.java: "int plId = 0"
        // local al constructor). TrackableObject.equals() compara SOLO por
        // id+clase, no por a que partida pertenece, asi que el PlayerView
        // congelado del rival al final de la partida anterior "es igual"
        // al del rival de la partida nueva. Sin este aviso, viewedOpponent/
        // shownOpponent/lastTurnPlayer se quedaban apuntando al objeto
        // VIEJO — congelado en el ultimo estado de la partida anterior — y
        // el campo del rival enseñaba su mesa de la partida de antes hasta
        // que le tocaba turno: es updateTurn, no apply(), quien primero le
        // asigna el PlayerView nuevo (sin pasar por el equals que atrapaba
        // al viejo), y por eso el fallo se veia "arreglarse solo" justo al
        // acabar tu primer turno. GameView.getId() sale de Game.id =
        // nextId(), un contador ESTATICO de toda la JVM: a diferencia de
        // los ids de jugador, no se reparte de cero en cada partida, asi
        // que sirve para distinguir "sigue siendo la misma partida" de
        // "partida nueva del mismo partido".
        final boolean newGame = view != null && (gameView == null || gameView.getId() != view.getId());
        this.gameView = view;
        if (newGame) {
            viewedOpponent = null;
            shownOpponent = null;
            lastTurnPlayer = null;
        }
        requestRefresh();
    }

    /** Texto del prompt que el motor quiere mostrar al jugador. */
    public void setPrompt(final String message) {
        try {
            Platform.runLater(() -> table.setPrompt(message));
        } catch (final IllegalStateException ignored) {
            // sin ventana
        }
    }

    /** Marca que hay que repintar. Se puede llamar desde cualquier hilo. */
    public void requestRefresh() {
        if (pending.compareAndSet(false, true)) {
            try {
                Platform.runLater(this::apply);
            } catch (final IllegalStateException e) {
                // El toolkit no esta arrancado (modo consola): nada que pintar.
                pending.set(false);
            }
        }
    }

    // ---------------------------------------------------------------

    /**
     * El mismo jugador, pero el objeto VIVO del {@code GameView}.
     *
     * <p>Devuelve null si ese jugador no esta en la partida (espectador, o una
     * maqueta sin jugador local).
     */
    private static PlayerView liveOne(final List<PlayerView> players, final PlayerView wanted) {
        if (wanted == null) {
            return null;
        }
        for (final PlayerView p : players) {
            if (p != null && p.getId() == wanted.getId()) {
                return p;
            }
        }
        return null;
    }

    private void apply() {
        pending.set(false);
        final GameView gv = gameView;
        if (gv == null) {
            return;
        }

        final List<PlayerView> players = new ArrayList<>();
        for (final PlayerView p : gv.getPlayers()) {
            players.add(p);
        }
        if (players.isEmpty()) {
            return;
        }

        // Quien va abajo, y OJO CON ESTO en una partida en red.
        //
        // "self" sale de NeoMatchUI.openView, y en el invitado esa lista NO
        // trae los objetos vivos: GameClientHandler deserializa el paquete y
        // se limita a ponerles el tracker, sin cambiarlos por los que el
        // tracker ya tiene. Con la sincronizacion por deltas encendida
        // -- replicatePlayerView se corta en seco si lo esta -- esa copia no
        // vuelve a actualizarse NUNCA: se queda con la foto del momento en que
        // empezo la partida.
        //
        // Y no se nota leyendo el codigo, porque TrackableObject.equals compara
        // por id: la copia congelada "es igual" a la de verdad, asi que
        // contains() dice que si y todo parece correcto. Lo que veia el
        // invitado era su barra con 0 cartas en mano, 0 de mazo y 0 en el
        // cementerio, mientras la del rival iba bien.
        //
        // Se resuelve pidiendo el que esta en el GameView, que es el que
        // reciben los deltas. Buscar por id y no por equals() es a proposito:
        // aqui la diferencia entre los dos objetos es justo lo que importa.
        PlayerView bottom = liveOne(players, self);
        if (bottom == null) {
            bottom = players.get(0);
        }
        // Commander se juega hasta a cuatro. Se muestra UN rival cada vez y se
        // cambia con pestanyas: partir la pantalla en cuatro dejaria las cartas
        // ilegibles.
        final List<PlayerView> opponents = new ArrayList<>();
        for (final PlayerView p : players) {
            if (!p.equals(bottom)) {
                opponents.add(p);
            }
        }

        // Al empezar el turno de un rival, se pasa a su vista. En una partida a
        // cuatro es dificil seguir quien esta jugando si la mesa se queda
        // mirando a otro; asi el turno de IA-3 se ve en el turno de IA-3.
        final PlayerView turnPlayer = gv.getPlayerTurn();
        if (turnPlayer != null && !turnPlayer.equals(lastTurnPlayer)) {
            lastTurnPlayer = turnPlayer;
            if (opponents.contains(turnPlayer)) {
                viewedOpponent = turnPlayer;
            }
        }

        PlayerView top = viewedOpponent;
        if (top == null || !opponents.contains(top)) {
            top = opponents.isEmpty() ? null : opponents.get(0);
            viewedOpponent = top;
        }

        table.setOpponents(opponents, top, gv.getPlayerTurn());

        // Cambio de rival a la vista: la mesa de arriba se sustituye entera y
        // no se puede animar como si se hubiera muerto todo.
        if (top != null && !top.equals(shownOpponent)) {
            table.skipOpponentRemovalAnimation();
        }
        shownOpponent = top;

        applyPlayer(gv, bottom, false);
        if (top != null) {
            applyPlayer(gv, top, true);
        }

        table.setPhase(gv.getPhase());
        table.setStack(gv.getStack(), self);
        table.setCombatLinks(links(gv));

        // El resaltado debil se reparte por NODO, y aqui acaban de aparecer
        // nodos nuevos. Sin reponerlo, lo que el motor dijo que podias usar se
        // apaga en el primer repintado — y durante un pago de mana la mesa se
        // repinta con cada tierra que tapas.
        if (matchUi != null) {
            table.setActionable(matchUi::actionableStrength);
        }
    }

    /**
     * Las flechas que hay que pintar ahora mismo.
     *
     * <p>Todo sale del {@code GameView}: quien ataca a quien, quien bloquea a
     * quien y a que apunta cada hechizo del stack. La interfaz no deduce nada,
     * solo dibuja lo que el motor ya sabe.
     */
    /** El rival que se esta pintando arriba ahora mismo. */
    private PlayerView shownOpponent;

    private static List<Object[]> links(final GameView gv) {
        final List<Object[]> out = new ArrayList<>();

        final CombatView combat = gv.getCombat();
        if (combat != null && combat.getAttackers() != null) {
            for (final CardView attacker : combat.getAttackers()) {
                final GameEntityView defender = combat.getDefender(attacker);
                if (defender != null) {
                    out.add(new Object[] {attacker, defender, CombatOverlay.Kind.ATTACK});
                }
                // Los bloqueos ya declarados y los que se estan declarando
                // ahora mismo se pintan igual: al jugador le da lo mismo, lo
                // que quiere ver es quien para a quien.
                final Set<CardView> seen = new HashSet<>();
                addBlockers(out, attacker, combat.getBlockers(attacker), seen);
                addBlockers(out, attacker, combat.getPlannedBlockers(attacker), seen);
            }
        }

        if (gv.getStack() != null) {
            for (final StackItemView item : gv.getStack()) {
                final CardView source = item.getSourceCard();
                if (source == null) {
                    continue;
                }
                if (item.getTargetCards() != null) {
                    for (final CardView target : item.getTargetCards()) {
                        out.add(new Object[] {source, target, CombatOverlay.Kind.TARGET});
                    }
                }
                if (item.getTargetPlayers() != null) {
                    for (final PlayerView target : item.getTargetPlayers()) {
                        out.add(new Object[] {source, target, CombatOverlay.Kind.TARGET});
                    }
                }
            }
        }
        return out;
    }

    private static void addBlockers(final List<Object[]> out, final CardView attacker,
                                    final Iterable<CardView> blockers, final Set<CardView> seen) {
        if (blockers == null) {
            return;
        }
        for (final CardView blocker : blockers) {
            if (blocker != null && seen.add(blocker)) {
                out.add(new Object[] {blocker, attacker, CombatOverlay.Kind.BLOCK});
            }
        }
    }

    /**
     * Que cara le toca a cada uno.
     *
     * <p>La tuya la eliges tu y puede ser un PNG cualquiera. La de un rival
     * sale del <b>indice que reparte el motor</b> sobre la hoja de sprites de
     * Forge, asi que cada IA tiene la suya y siempre la misma en esa partida.
     */
    private javafx.scene.image.Image faceOf(final PlayerView p, final boolean opponent) {
        try {
            if (!opponent) {
                return forge.neo.look.NeoLook.currentAvatar().image();
            }
            return forge.neo.look.NeoLook.builtInAvatar(p.getAvatarIndex()).image();
        } catch (final RuntimeException e) {
            // Sin cara se juega igual; con la mesa caida, no.
            return null;
        }
    }

    /**
     * La funda de este jugador, para su cementerio y su exilio.
     *
     * <p>Misma idea que {@link #faceOf}, y el mismo motivo por el que se
     * pregunta cada vez: en 3-4 jugadores {@code opponentField} se REUSA para
     * el rival que se este mirando, así que la funda tiene que refrescarse en
     * cada cambio de pestaña — no sólo la primera vez que se pinta la mesa.
     */
    private javafx.scene.image.Image sleeveOf(final PlayerView p, final boolean opponent) {
        try {
            if (!opponent) {
                // La del MAZO con el que juegas, si lleva una; si no, la tuya
                // de Personalizar. Ver NeoLook.sleeveInPlay().
                return forge.neo.look.NeoLook.sleeveInPlay().image();
            }
            return forge.neo.look.NeoLook.builtInSleeve(p.getSleeveIndex()).image();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * El dano de comandante mas alto que ha recibido este jugador.
     *
     * <p>Se pregunta por CADA comandante de la partida — el suyo tambien, que
     * un comandante robado o un efecto puede pegarte con el tuyo. El motor
     * lleva la cuenta por comandante: {@code PlayerView.getCommanderDamage}.
     *
     * <p>A 21 de un mismo comandante se pierde, y el motor lo aplica
     * (Player.java: {@code if (entry.getValue() >= 21) loseConditionMet}). Sin
     * ensenyarlo se puede perder una partida sin verlo venir.
     */
    private static int worstCommanderDamage(final GameView gv, final PlayerView p) {
        if (gv == null || p == null || gv.getPlayers() == null) {
            return 0;
        }
        int worst = 0;
        try {
            for (final PlayerView other : gv.getPlayers()) {
                final List<CardView> commanders = other.getCommanders();
                if (commanders == null) {
                    continue;
                }
                for (final CardView commander : commanders) {
                    if (commander != null) {
                        worst = Math.max(worst, p.getCommanderDamage(commander));
                    }
                }
            }
        } catch (final RuntimeException e) {
            return 0;
        }
        return worst;
    }

    /**
     * El estado del jugador, tal y como lo escribe el motor.
     *
     * <p>No se compone NADA aqui, y es a proposito: {@code getDetails()} ya
     * trae vidas, contadores, mano y su maximo, tierras jugadas, robadas este
     * turno, turnos extra y palabras clave — todo traducido — y termina
     * llamando el solo a {@code getPlayerCommanderInfo()}, que anyade el
     * impuesto de cada comandante y el desglose del dano de comandante.
     *
     * <p>La primera version anyadia ese ultimo bloque otra vez por su cuenta y
     * salia <b>duplicado</b> en pantalla. Antes de "completar" lo que da el
     * motor, conviene leer hasta el final lo que ya trae.
     */
    private static String detailsOf(final PlayerView p) {
        if (p == null) {
            return "";
        }
        try {
            final String base = p.getDetails();
            return base == null ? "" : base.trim();
        } catch (final RuntimeException e) {
            return "";
        }
    }

    /**
     * Los contadores del jugador, o nada si el motor aun no los ha puesto.
     *
     * <p>Se lee del {@code PlayerView} y no se compone: cuando salga un
     * contador nuevo aparecera solo, con su color y su nombre corto.
     */
    private static com.google.common.collect.Multiset<forge.game.card.CounterType>
            countersOf(final PlayerView p) {
        try {
            return p == null ? null : p.getCounters();
        } catch (final RuntimeException e) {
            return null;
        }
    }

    private static int shardsOf(final PlayerView p) {
        try {
            return p == null ? 0 : p.getNumManaShards();
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /**
     * Quien te esta jugando el turno, si no eres tu.
     *
     * <p>{@code PlayerView.getMindSlaveMaster} lo publica desde siempre y no lo
     * ensenyabamos en ninguna parte — ni siquiera sale en {@code getDetails}.
     * Es raro (Mindslaver, Sen Triplets), pero cuando pasa, sin decirlo, lo que
     * se ve es que la partida se mueve sola.
     */
    private static String controllerOf(final PlayerView p) {
        try {
            final PlayerView master = p == null ? null : p.getMindSlaveMaster();
            return master == null || master.equals(p) ? null : PlayerName.of(master);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /** Cuanto veneno te mata. Lo dice el motor: en dos cabezas no son diez. */
    private static int poisonToLose(final GameView gv) {
        try {
            return gv == null ? 10 : gv.getPoisonCountersToLose();
        } catch (final RuntimeException e) {
            return 10;
        }
    }

    private void applyPlayer(final GameView gv, final PlayerView p, final boolean opponent) {
        final var bar = opponent ? table.getOpponentBar() : table.getSelfBar();

        bar.setPlayer(p);
        bar.setAvatarImage(faceOf(p, opponent));
        (opponent ? table.getOpponentField() : table.getSelfField())
                .setSleeveImage(sleeveOf(p, opponent));
        // La pastilla se calcula sobre TODOS los jugadores del GameView.
        // El panel de detalle trae su propio desglose, pero por otra ruta del
        // motor (getPlayerCommanderInfo, que recorre getOpponents()), asi que
        // si alguna vez discrepan, el numero de la pastilla es el de fiar.
        bar.setCommanderDamage(worstCommanderDamage(gv, p));
        bar.setDetails(detailsOf(p));
        bar.setPlayerName(PlayerName.of(p));
        bar.setLife(p.getLife());
        bar.setActiveTurn(p.equals(gv.getPlayerTurn()));
        bar.setZones(
                p.getZoneSize(ZoneType.Hand),
                p.getZoneSize(ZoneType.Library),
                p.getZoneSize(ZoneType.Graveyard),
                p.getZoneSize(ZoneType.Exile),
                p.getZoneSize(ZoneType.Command));

        // Contadores del jugador: veneno, energia, experiencia, radiacion,
        // entradas... y los fragmentos de mana, que no son un contador.
        //
        // Estaban SOLO en el detalle que sale al pasar el raton, y con el
        // veneno eso es un fallo: a diez se pierde la partida.
        // La velocidad va con ellos aunque no sea un contador: es lo mismo
        // — un estado del jugador que cambia la partida y que hay que poder
        // ver de un vistazo. Del rival es que no habia OTRA forma de verlo:
        // el motor solo la publica como una carta dentro de su zona de mando.
        bar.setCounters(countersOf(p), shardsOf(p), poisonToLose(gv), controllerOf(p),
                forge.neo.match.PlayerSpeed.of(p), forge.neo.match.PlayerSpeed.rawText(p));

        // Reserva de mana, por tipo.
        //
        // OJO CON LA CODIFICACION: hay DOS y no coinciden. El motor guarda la
        // reserva con las de ManaAtom (`PlayerView.updateMana` recorre
        // `ManaAtom.MANATYPES`), y ahi el incoloro es 1<<5. En MagicColor el
        // incoloro es 0, "la ausencia de color". Los cinco colores valen lo
        // mismo en las dos, asi que usar la de MagicColor parecia funcionar...
        // salvo para el incoloro, que devolvia siempre 0.
        // Las constantes de ManaAtom son int (hay banderas por encima del byte,
        // como IS_X), pero los tipos de mana caben de sobra en un byte.
        bar.setMana(
                p.getMana((byte) ManaAtom.WHITE),
                p.getMana((byte) ManaAtom.BLUE),
                p.getMana((byte) ManaAtom.BLACK),
                p.getMana((byte) ManaAtom.RED),
                p.getMana((byte) ManaAtom.GREEN),
                p.getMana((byte) ManaAtom.COLORLESS));

        final List<CardView> battlefield = toList(p.getBattlefield());
        if (opponent) {
            table.setOpponentBattlefield(battlefield);
            table.setOpponentZonePiles(p.getZoneSize(ZoneType.Graveyard),
                    p.getZoneSize(ZoneType.Exile),
                    hasPlayable(p, ZoneType.Graveyard), hasPlayable(p, ZoneType.Exile));
        } else {
            table.setSelfBattlefield(battlefield);
            table.setSelfZonePiles(p.getZoneSize(ZoneType.Graveyard),
                    p.getZoneSize(ZoneType.Exile),
                    hasPlayable(p, ZoneType.Graveyard), hasPlayable(p, ZoneType.Exile));
            // La mano solo se ve entera si es la nuestra; la del rival se
            // muestra como reverso en la barra (contador de MANO).
            table.setHand(toList(p.getHand()));
            table.setCommandZone(toList(p.getCommand()), p);
        }
    }

    /**
     * Hay en esta zona de este jugador algo que TU puedas lanzar ahora mismo.
     *
     * <p>La pila del cementerio y la del exilio dicen cuantas cartas hay, no si
     * alguna sirve. Y una aventura exiliada o un flashback no se descubren
     * solos: hay que abrir la zona para verlos. Esto es lo que hace que se
     * abra — el borde de acento de la pila.
     *
     * <p>La zona se pregunta por la carta ({@code CardView.getZone()}) y no por
     * la lista de la zona: {@code getFlashback()} las trae TODAS juntas, de las
     * cinco zonas y de todos los jugadores.
     */
    private boolean hasPlayable(final PlayerView owner, final ZoneType zone) {
        for (final CardView cv : playableOutside()) {
            if (cv != null && cv.getZone() == zone && owner.equals(cv.getOwner())) {
                return true;
            }
        }
        return false;
    }

    /** Lo que el motor dice que puedes lanzar desde fuera de mano y campo. */
    private FCollectionView<CardView> playableOutside() {
        final PlayerView me = matchUi == null ? null : matchUi.localPlayerView();
        final FCollectionView<CardView> flash = me == null ? null : me.getFlashback();
        return flash == null ? forge.util.collect.FCollection.getEmpty() : flash;
    }

    private static List<CardView> toList(final FCollectionView<CardView> view) {
        final List<CardView> out = new ArrayList<>();
        if (view != null) {
            for (final CardView cv : view) {
                out.add(cv);
            }
        }
        return out;
    }
}
