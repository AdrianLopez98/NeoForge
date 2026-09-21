package forge.neo.net;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gamemodes.limited.LimitedPoolType;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.EventFormat;
import forge.gamemodes.net.EventPhase;
import forge.gamemodes.net.NetworkEventView;
import forge.gamemodes.net.server.FServerManager;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.gui.interfaces.IDraftEventHandler;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeNetPreferences;
import forge.model.FModel;

/**
 * Un draft y un sellado <b>en red</b>, enteros y sin ventana.
 *
 * <p><b>Por que se puede hacer en UN proceso</b>, al reves que
 * {@link LobbyCheck}: aqui no se juega ninguna partida, asi que no hay
 * llamadas bloqueantes de {@code IGuiGame} cruzandose por el unico hilo de
 * interfaz. El draft es puro mensaje: llega un sobre, se contesta un pick. Y el
 * anfitrion recibe los suyos por el mismo sitio que un invitado — {@code
 * FServerManager.sendToSlot} cae en {@code dispatchToLocalListener} cuando el
 * asiento no tiene cliente remoto — asi que un pod de un humano y siete IA
 * recorre <b>exactamente</b> el camino que recorreria con amigos.
 *
 * <p>Lo que blinda, y nada de ello se ve en una captura:
 *
 * <ol>
 *   <li>que el evento se monte y se reparta ({@code configureEvent},
 *       {@code startDraftEvent});</li>
 *   <li>que <b>lleguen los sobres</b> — sin {@code getDraftHandler} devolviendo
 *       algo, los paquetes se reciben y se tiran en silencio, y la pantalla no
 *       sale nunca;</li>
 *   <li>que el pick del anfitrion <b>entre</b> por {@code handleDraftPick} con
 *       el {@code -1} que se salta la comprobacion de cliente;</li>
 *   <li>que el draft <b>termine</b> y llegue el pool, que es lo unico que dice
 *       que se ha acabado;</li>
 *   <li>que el pool tenga <b>45 cartas en la banda</b>, que es la forma que
 *       {@code NetPoolContext} da por hecha para montar el mazo;</li>
 *   <li>y que el sellado haga lo propio con sus seis sobres.</li>
 * </ol>
 *
 * <p>{@code run.cmd neteventcheck}
 */
public final class NetEventCheck {

    /** Otro puerto poco probable, distinto del de {@link LobbyCheck}. */
    private static final int PORT = 36801;

    /** Tope para un draft entero. Con IA son segundos, pero el pod es de ocho. */
    private static final int TIMEOUT_SECS = Integer.getInteger("neo.netevent.timeout", 120);

    private NetEventCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        final long t0 = System.currentTimeMillis();
        final String portBefore =
                FModel.getNetPreferences().getPref(ForgeNetPreferences.FNetPref.NET_PORT);
        final String upnpBefore =
                FModel.getNetPreferences().getPref(ForgeNetPreferences.FNetPref.UPnP);
        FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.UPnP, "NEVER");
        FModel.getNetPreferences().setPref(
                ForgeNetPreferences.FNetPref.NET_PORT, String.valueOf(PORT));
        try {
            draft();
            sealed();
            poolContext();
        } catch (final Exception | LinkageError e) {
            System.out.println("  FALLO: " + e);
            e.printStackTrace();
            failed++;
        } finally {
            NeoLobby.stopHosting();
            FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.UPnP, upnpBefore);
            FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.NET_PORT, portBefore);
        }
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal  (%ds)%n",
                passed, failed, (System.currentTimeMillis() - t0) / 1000);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del evento en red han fallado");
        }
    }

    // ------------------------------------------------------------------
    // El draft
    // ------------------------------------------------------------------

    private static void draft() throws Exception {
        System.out.println("\n  -- draft en red: un humano y siete IA --");
        final ServerGameLobby lobby = startLobby();
        try {
            // LimitedPoolType.Full a proposito: es el unico que NO abre
            // dialogos de bloque o expansion. Sin ventana, getChoices contesta
            // lo primero de la lista y la prueba dependeria de que expansion
            // salga primera hoy.
            final forge.gamemodes.limited.BoosterDraft draft =
                    forge.gamemodes.limited.BoosterDraft.createDraftForNetwork(LimitedPoolType.Full);
            check("se monta el draft de red", draft != null);
            if (draft == null) {
                return;
            }
            // Sin reloj: el servidor elegiria por nosotros a mitad de prueba y
            // lo que se quiere comprobar es que NUESTRO pick entra.
            final boolean ok = NeoNetEvent.configure(lobby, EventFormat.BOOSTER_DRAFT,
                    LimitedPoolType.Full, draft, 0, 0);
            check("el evento queda configurado", ok);
            check("y la sala lo publica para el invitado",
                    NeoNetEvent.viewOf(lobby) != null);

            final NetworkEventView view = NeoNetEvent.viewOf(lobby);
            check("el evento viaja como DRAFT",
                    view != null && view.getFormat() == EventFormat.BOOSTER_DRAFT);

            final Robot robot = new Robot(lobby);
            FServerManager.getInstance().setDraftHandler(robot);

            lobby.applyToSlot(0, forge.gamemodes.net.event.UpdateLobbyPlayerEvent
                    .isReadyUpdate(true));
            final String why = NeoNetEvent.start(lobby);
            check("el draft arranca", why == null);
            if (why != null) {
                System.out.println("        motivo: " + why);
                return;
            }
            check("el pod se rellena con IA (8 asientos)",
                    lobby.getCurrentEvent() != null
                            && lobby.getCurrentEvent().getParticipants().size() == 8);
            check("y la fase pasa a DRAFTING",
                    lobby.getCurrentEvent() != null
                            && lobby.getCurrentEvent().getPhase() == EventPhase.DRAFTING);

            check("llega el primer sobre (sin esto la pantalla no sale nunca)",
                    robot.firstPack.await(60, TimeUnit.SECONDS));
            System.out.printf(Locale.ROOT, "  Mi asiento: %d%n", robot.seat);

            final boolean done = robot.pool.await(TIMEOUT_SECS, TimeUnit.SECONDS);
            System.out.printf(Locale.ROOT, "  Sobres recibidos: %d | picks enviados: %d%n",
                    robot.packs.get(), robot.picks.get());
            check("el draft termina y llega el pool", done);
            check("se han recibido los 45 sobres del pod", robot.packs.get() == 45);
            check("y se ha contestado a todos", robot.picks.get() == 45);

            final Deck pool = robot.received.get();
            final int inPool = pool == null || pool.get(DeckSection.Sideboard) == null
                    ? 0 : pool.get(DeckSection.Sideboard).countAll();
            System.out.printf(Locale.ROOT, "  Pool: %s (%d cartas en la banda)%n",
                    pool == null ? "(ninguno)" : pool.getName(), inPool);
            // 45 EN LA BANDA, no en el mazo: es la forma que NetPoolContext da
            // por hecha. Si el motor cambiara de sitio los picks, el
            // constructor abriria un pool vacio y no diria por que.
            check("el pool trae las 45 cartas en la BANDA", inPool == 45);
            check("y viene etiquetado con su evento", NeoNetEvent.eventIdOf(pool) != null);
            check("el pool queda guardado y se encuentra por su evento",
                    !NeoNetEvent.poolsFor(NeoNetEvent.eventIdOf(pool), true).isEmpty());

            // Lo que la sala ensenya despues: limitado, y el mazo sale del pool.
            check("la sala queda en LIMITADO", NeoNetEvent.isLimited(lobby));
            check("y apunta a este evento para elegir mazo",
                    NeoNetEvent.eventIdOf(pool).equals(NeoNetEvent.activeEventId(lobby)));
            lastPool = pool;
        } finally {
            FServerManager.getInstance().setDraftHandler(null);
            NeoLobby.stopHosting();
        }
    }

    /** El pool del draft, para reutilizarlo en la prueba del constructor. */
    private static Deck lastPool;

    // ------------------------------------------------------------------
    // El sellado
    // ------------------------------------------------------------------

    private static void sealed() throws Exception {
        System.out.println("\n  -- sellado en red --");
        final ServerGameLobby lobby = startLobby();
        // ⚠️ Un sellado PREGUNTA cuantos sobres quieres
        // (SealedCardPoolGenerator.chooseNumberOfBoosters, un SGuiChoose de 3 a
        // 12) y sin ventana no hay quien conteste: NeoGuiBase.getChoices con
        // min=0 devuelve la lista vacia — que es lo correcto, ahi "vacio"
        // significa "he cancelado" — y el generador se queda sin producto. Se
        // pone un elegidor de usar y tirar que hace de jugador, igual que hace
        // NeoDraft.startCube, y se devuelve el de antes al salir: si no,
        // cualquier pregunta posterior se contestaria sola para siempre.
        final forge.neo.platform.NeoGuiBase.Chooser before =
                forge.neo.platform.NeoGuiBase.getChooser();
        forge.neo.platform.NeoGuiBase.setChooser(new forge.neo.platform.NeoGuiBase.Chooser() {
            @Override
            public <T> List<T> choose(final String message, final int min, final int max,
                                      final List<T> options, final List<T> preselected,
                                      final java.util.function.Function<T, String> display) {
                System.out.printf(Locale.ROOT, "    (contesto \"%s\" -> %s)%n",
                        message, options.isEmpty() ? "nada" : options.get(0));
                return options.isEmpty() ? List.of() : List.of(options.get(0));
            }
        });
        try {
            final boolean ok = NeoNetEvent.configure(lobby, EventFormat.SEALED,
                    LimitedPoolType.Full, null, 0, 0);
            check("el sellado queda configurado", ok);

            final Robot robot = new Robot(lobby);
            FServerManager.getInstance().setDraftHandler(robot);
            lobby.applyToSlot(0, forge.gamemodes.net.event.UpdateLobbyPlayerEvent
                    .isReadyUpdate(true));
            final String why = NeoNetEvent.start(lobby);
            check("el sellado arranca", why == null);
            if (why != null) {
                return;
            }
            check("llega el pool", robot.pool.await(60, TimeUnit.SECONDS));
            final Deck pool = robot.received.get();
            final int inPool = pool == null || pool.get(DeckSection.Sideboard) == null
                    ? 0 : pool.get(DeckSection.Sideboard).countAll();
            System.out.printf(Locale.ROOT, "  Pool de sellado: %d cartas%n", inPool);
            // TRES sobres, no seis: cuantos son se PREGUNTA (de 3 a 12), y el
            // suplente de arriba contesta siempre lo primero de la lista.
            // Jugando lo elige el anfitrion. No se fija el numero exacto porque
            // un sobre generico no siempre trae quince cartas, pero por debajo
            // de 40 es que se ha abierto de menos.
            check("el pool de sellado son los sobres pedidos (>= 40 cartas)", inPool >= 40);
            check("y tambien viene en la BANDA",
                    pool != null && pool.getMain().countAll() == 0);
        } finally {
            forge.neo.platform.NeoGuiBase.setChooser(before);
            FServerManager.getInstance().setDraftHandler(null);
            NeoLobby.stopHosting();
        }
        cancelledEvent();
    }

    /**
     * Cancelar el asistente no puede dejar un evento a medias.
     *
     * <p>Es el fallo que esta prueba encontro el 21-09-2026:
     * {@code createEvent} deja el evento puesto y {@code configureEvent}
     * devuelve false sin quitarlo, asi que la sala ensenyaba "listo para
     * empezar" sobre un evento sin producto — y al pulsar EMPEZAR no pasaba
     * nada, porque {@code startSealedEvent} se planta al no encontrar
     * generador y vuelve <b>sin decir nada</b>. Un boton que no hace lo que
     * dice (principio 1).
     */
    private static void cancelledEvent() {
        System.out.println();
        System.out.println("  -- cancelar el asistente --");
        final ServerGameLobby lobby = startLobby();
        try {
            // Sin elegidor puesto, la pregunta de los sobres se cancela sola:
            // es exactamente lo que pasa si el jugador cierra el dialogo.
            final boolean ok = NeoNetEvent.configure(lobby, EventFormat.SEALED,
                    LimitedPoolType.Full, null, 0, 0);
            check("cancelar deja el evento sin configurar", !ok);
            check("y NO deja uno a medias en la sala", lobby.getCurrentEvent() == null);
            check("asi que no se puede empezar nada",
                    "noEvent".equals(NeoNetEvent.start(lobby)));
        } finally {
            NeoLobby.stopHosting();
        }
    }

    // ------------------------------------------------------------------
    // El constructor de mazos sobre el pool
    // ------------------------------------------------------------------

    /**
     * Que el pool se pueda <b>montar</b>.
     *
     * <p>Llegar el pool y poder jugarlo no es lo mismo: si
     * {@code NetPoolContext} leyera la mitad equivocada del mazo, el
     * constructor abriria un catalogo vacio — y eso no revienta, solo deja al
     * jugador sin nada que hacer.
     */
    private static void poolContext() {
        System.out.println("\n  -- montar el mazo con el pool --");
        final Deck pool = lastPool;
        if (pool == null) {
            check("hay un pool del draft que montar", false);
            return;
        }
        final forge.neo.draft.NetPoolContext context =
                new forge.neo.draft.NetPoolContext(pool, false);
        final List<PaperCard> catalogue = context.pool();
        System.out.printf(Locale.ROOT, "  Catalogo del constructor: %d entradas%n",
                catalogue == null ? 0 : catalogue.size());
        check("el catalogo NO esta vacio", catalogue != null && !catalogue.isEmpty());
        // Las cinco basicas van aparte y sin techo: en limitado se ponen las
        // que hagan falta.
        check("y trae las cinco basicas", catalogue != null && catalogue.size() >= 5);
        check("el pool vive en la banda (si no, sacar una carta la destruye)",
                context.poolInSideboard());
        check("las reglas son las de limitado",
                context.deckFormat() == forge.game.GameType.Draft.getDeckFormat());

        // El techo de copias es el POOL, no las reglas: de una carta que
        // draftaste una vez, llevas una.
        PaperCard sample = null;
        for (final java.util.Map.Entry<PaperCard, Integer> e : pool.get(DeckSection.Sideboard)) {
            if (e.getKey() != null && e.getKey().getRules() != null
                    && !e.getKey().getRules().getType().isBasicLand()) {
                sample = e.getKey();
                break;
            }
        }
        check("una carta del pool se reconoce como tuya",
                sample != null && context.owned(sample) > 0);
        // Y una que NO salio en tus sobres no se puede meter.
        final PaperCard outsider = FModel.getMagicDb().getCommonCards().getCard("Black Lotus");
        if (outsider != null) {
            check("una carta de fuera del pool no cuenta como tuya",
                    context.owned(outsider) == 0
                            || pool.get(DeckSection.Sideboard).contains(outsider));
        }
    }

    // ------------------------------------------------------------------

    /**
     * El que juega el draft por nosotros.
     *
     * <p>Coge <b>la primera carta</b> de cada sobre, que es lo mismo que hace
     * {@code DraftScreen.autoPick} — o sea que se recorre el mismo camino que
     * un click: {@link NetDraftSource#pick} compone el
     * {@code DraftPickEvent} y lo entrega por donde toca.
     */
    private static final class Robot implements IDraftEventHandler {
        private final ServerGameLobby lobby;
        private final CountDownLatch firstPack = new CountDownLatch(1);
        private final CountDownLatch pool = new CountDownLatch(1);
        private final AtomicInteger packs = new AtomicInteger();
        private final AtomicInteger picks = new AtomicInteger();
        private final AtomicReference<Deck> received = new AtomicReference<>();
        private volatile NetDraftSource source;
        private volatile int seat = -1;

        Robot(final ServerGameLobby lobby) {
            this.lobby = lobby;
        }

        @Override
        public void draftPackArrived(final int seatIndex, final List<PaperCard> pack,
                                     final int packNumber, final int pickNumber,
                                     final int timerDurationSeconds) {
            packs.incrementAndGet();
            if (source == null) {
                seat = seatIndex;
                final NetworkEventView view = NeoNetEvent.viewOf(lobby);
                source = new NetDraftSource(seatIndex,
                        view == null ? List.of() : view.getParticipants(),
                        view == null ? null : view.getProductDescription(),
                        pick -> lobby.handleDraftPick(pick, -1));
                firstPack.countDown();
            }
            source.packArrived(pack, packNumber, pickNumber, timerDurationSeconds);
            // El pick se manda desde OTRO hilo: el que trae el sobre es el del
            // servidor, y contestar ahi mismo reentraria en el monitor del
            // BoosterDraftHost mientras todavia esta repartiendo.
            final NetDraftSource s = source;
            final List<PaperCard> cards = s.currentCards();
            if (cards.isEmpty()) {
                return;
            }
            final Thread t = new Thread(() -> {
                if (s.pick(cards.get(0)) != null) {
                    picks.incrementAndGet();
                }
            }, "neo-netevent-pick");
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void draftSeatPicked(final int seatIndex, final int[] seatQueueDepths) {
            final NetDraftSource s = source;
            if (s != null) {
                s.seatPicked(seatQueueDepths);
            }
        }

        @Override
        public void draftAutoPicked(final int seatIndex, final PaperCard card,
                                    final int packNumber, final int pickInPack) {
            final NetDraftSource s = source;
            if (s != null) {
                s.autoPicked(seatIndex, card);
            }
        }

        @Override
        public void receiveEventPool(final String eventId, final Deck deck) {
            NeoNetEvent.savePool(deck, lobby.getCurrentEvent());
            lobby.selectEventForMatch(eventId, true);
            received.set(deck);
            final NetDraftSource s = source;
            if (s != null) {
                s.finish();
            }
            pool.countDown();
        }
    }

    /** Una sala hospedando, con su oyente: sin el, applyToSlot revienta con NPE. */
    private static ServerGameLobby startLobby() {
        final ServerGameLobby lobby = NeoLobby.host(() -> {
            throw new IllegalStateException("aqui no se juega ninguna partida");
        });
        lobby.setListener(new forge.interfaces.IUpdateable() {
            @Override
            public void update(final boolean fullUpdate) {
            }

            @Override
            public void update(final int slot, final LobbySlotType type) {
            }
        });
        // Limitado, como lo deja la sala al montar un evento.
        NeoLobby.setFormat(lobby, forge.neo.match.NeoFormat.ESTANDAR);
        lobby.setLimitedMode(true);
        return lobby;
    }

    private static boolean check(final String what, final boolean ok) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK " : "NO ", what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        return ok;
    }
}
