package forge.neo.net;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import forge.deck.Deck;
import forge.game.player.PlayerView;
import forge.gamemodes.net.DeltaPacket;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoMatchUI;
import forge.trackable.TrackableCollection;

/**
 * Un invitado automatico, para la prueba de {@link LobbyCheck}.
 *
 * <p><b>Por que vive en su propio proceso.</b> Anfitrion e invitado en la misma
 * JVM se reparten <b>un solo hilo de interfaz</b>: {@code FThreads} pregunta
 * siempre por {@code GuiBase.getInterface()}, que es un estatico global. Y hay
 * llamadas del protocolo que devuelven valor — {@code tempShowZones},
 * {@code getAbilityToPlay}, {@code assignCombatDamage}… — en las que el
 * anfitrion bloquea su hilo de interfaz esperando la respuesta del invitado
 * (lo hace el propio Forge: {@code InputSelectEntitiesFromList} envuelve la
 * llamada en {@code FThreads.invokeInEdtNowOrLater}). Con dos procesos eso son
 * milisegundos, porque el invitado contesta en SU hilo. Con uno solo, la
 * respuesta tendria que procesarse en el hilo que esta bloqueado esperandola:
 * se clava para siempre.
 *
 * <p>Es el mismo motivo por el que los tests de red de Forge tienen un
 * {@code MultiProcessGameExecutor}.
 *
 * <p>No se usa jugando: para eso esta la pantalla. Esto solo existe para poder
 * comprobar una partida entera sin dos ordenadores.
 */
public final class LobbyGuest {

    /** La linea que lee el proceso padre. Si cambia, cambiar tambien alli. */
    public static final String RESULT_PREFIX = "[invitado] RESULTADO ";

    private LobbyGuest() {
    }

    /** Cuenta lo que llega por el cable, igual que hace el anfitrion. */
    private static final class CountingUi extends NeoMatchUI {
        private final AtomicInteger deltas = new AtomicInteger();
        private final CountDownLatch opened = new CountDownLatch(1);

        /**
         * El asiento propio TAL Y COMO LLEGA en {@code openView}.
         *
         * <p>Se guarda a proposito para poder comparar despues con el que hay
         * en el {@code GameView}. Son dos objetos distintos: el de aqui es una
         * copia que el cliente deja tal cual, y la sincronizacion por deltas no
         * vuelve a tocarla. Preguntarle a ESE cuantas cartas tienes devuelve
         * siempre 0, que es exactamente lo que se veia jugando.
         */
        private volatile PlayerView seatFromOpenView;

        /** Que ha llegado el aviso de "la partida se acabo". */
        private final CountDownLatch ended = new CountDownLatch(1);

        CountingUi() {
            super(NeoMatchUI.Mode.AUTO_PLAY, false);
        }

        /** Lo mas grande que se ha visto el propio mazo, vivo y en crudo. */
        private final AtomicInteger maxLive = new AtomicInteger();
        private final AtomicInteger maxRaw = new AtomicInteger();

        @Override
        public void applyDelta(final DeltaPacket packet) {
            deltas.incrementAndGet();
            super.applyDelta(packet);
            // Se mide AQUI, con la partida en marcha, y no al final: cuando un
            // jugador pierde se lleva sus cartas fuera de la partida (CR
            // 800.4a), asi que al terminar su biblioteca esta a 0 con razon y
            // la medida no diria nada.
            sample(maxLive, liveSeat());
            sample(maxRaw, seatFromOpenView);
        }

        private static void sample(final AtomicInteger best, final PlayerView p) {
            if (p == null) {
                return;
            }
            final int n = p.getZoneSize(forge.game.zone.ZoneType.Library);
            best.accumulateAndGet(n, Math::max);
        }

        @Override
        public void openView(final TrackableCollection<PlayerView> myPlayers) {
            if (myPlayers != null && !myPlayers.isEmpty()) {
                seatFromOpenView = myPlayers.iterator().next();
            }
            opened.countDown();
            super.openView(myPlayers);
        }

        @Override
        public void afterGameEnd() {
            ended.countDown();
            super.afterGameEnd();
        }

        /** El mismo jugador, buscado en el GameView: el que SI recibe deltas. */
        PlayerView liveSeat() {
            final PlayerView mine = seatFromOpenView;
            if (mine == null || getGameView() == null || getGameView().getPlayers() == null) {
                return mine;
            }
            for (final PlayerView p : getGameView().getPlayers()) {
                if (p != null && p.getId() == mine.getId()) {
                    return p;
                }
            }
            return mine;
        }
    }

    /**
     * Se conecta, se sienta con su mazo y juega hasta que acabe.
     *
     * @param args {@code --port=N --deck=nombre --name=texto --wait=segundos}
     */
    public static void run(final String[] args) {
        final int port = intArg(args, "--port", 36799);
        final String deckName = arg(args, "--deck", null);
        final String name = arg(args, "--name", "Invitado");
        final int waitSecs = intArg(args, "--wait", 300);

        final Deck deck = findDeck(deckName);
        if (deck == null) {
            System.out.println("[invitado] no encuentro el mazo: " + deckName);
            System.out.println(RESULT_PREFIX + "ok=false deltas=0 decisiones=0 motivo=sin-mazo");
            return;
        }
        System.out.printf(Locale.ROOT, "[invitado] %s juega con %s%n", name, deck.getName());

        // Marca deliberada para la prueba del anfitrion: el invitado pone el
        // auto-pase en TRUE y el anfitrion lo pone en FALSE. Luego se le
        // pregunta al anfitrion que valor usa para este jugador; si dice true,
        // es que seedYieldStateOnHost hizo su trabajo. Solo en memoria.
        forge.model.FModel.getPreferences().setPref(
                forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_PASS_NO_ACTIONS,
                true);

        final CountingUi gui = new CountingUi();
        final CountDownLatch lobbyKnown = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);

        NeoLobby.Guest guest = null;
        boolean ok = false;
        String why = "";
        try {
            guest = NeoLobby.join("localhost", port, name, gui,
                    l -> lobbyKnown.countDown(), null, closed::countDown);

            if (!lobbyKnown.await(30, TimeUnit.SECONDS)) {
                why = "no-entro-al-lobby";
                return;
            }
            System.out.println("[invitado] dentro del lobby");

            // Su mazo y su "listo" viajan al anfitrion. Es exactamente lo que
            // hara la pantalla cuando la haya.
            guest.send(UpdateLobbyPlayerEvent.deckUpdate(deck));
            guest.send(UpdateLobbyPlayerEvent.isReadyUpdate(true));
            System.out.println("[invitado] mazo enviado y listo");

            if (!gui.opened.await(waitSecs, TimeUnit.SECONDS)) {
                why = "la-partida-no-empezo";
                return;
            }
            System.out.println("[invitado] partida abierta");

            // A partir de aqui juega NeoMatchUI en automatico. Se acaba cuando
            // el anfitrion cierra la conexion o la partida termina.
            final long deadline = System.currentTimeMillis() + waitSecs * 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (gui.getGameView() != null && gui.getGameView().isGameOver()) {
                    ok = true;
                    break;
                }
                if (closed.await(200, TimeUnit.MILLISECONDS)) {
                    // El anfitrion ha cerrado. Si la partida habia terminado es
                    // lo normal; si no, es que algo se corto.
                    ok = gui.getGameView() != null && gui.getGameView().isGameOver();
                    why = ok ? "" : "conexion-cerrada-antes-de-acabar";
                    break;
                }
            }
            if (!ok && why.isEmpty()) {
                why = "plazo-agotado";
            }
            // El aviso que cierra la mesa llega DESPUES de que la partida se
            // marque terminada, y por el cable. Sin esperarlo aqui, el
            // resultado se manda antes de tiempo y la comprobacion sale unas
            // veces bien y otras mal segun quien corra mas.
            if (ok && !gui.ended.await(30, TimeUnit.SECONDS)) {
                System.out.println("[invitado] la partida acabo pero afterGameEnd no llego");
            }
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            why = "interrumpido";
        } catch (final Exception e) {
            why = "excepcion:" + e;
            e.printStackTrace();
        } finally {
            final int turns = gui.getGameView() == null ? 0 : gui.getGameView().getTurn();

            // Lo que veria el invitado en SU barra de jugador.
            //
            // Se apuntan los dos numeros, el vivo y el crudo, y eso es el
            // meollo de la comprobacion: si el vivo es > 0 y el crudo es 0, se
            // ha reproducido el fallo Y se ha demostrado que el arreglo hace
            // algo. Con un solo numero la prueba pasaria igual estando rota.
            final PlayerView live = gui.liveSeat();
            System.out.printf(Locale.ROOT,
                    "%sok=%s deltas=%d decisiones=%d turnos=%d miMazo=%d miMazoCrudo=%d"
                            + " miNombre=%s rivalNombre=%s fin=%s motivo=%s%n",
                    RESULT_PREFIX, ok, gui.deltas.get(), gui.getDecisionCount(), turns,
                    gui.maxLive.get(), gui.maxRaw.get(),
                    forge.neo.match.PlayerName.of(live),
                    forge.neo.match.PlayerName.of(otherThan(gui, live)),
                    gui.ended.getCount() == 0,
                    why.isEmpty() ? "-" : why);
            System.out.flush();
            if (guest != null) {
                guest.close();
            }
        }
    }

    /** Cualquier otro jugador de la mesa, para comparar los nombres. */
    private static PlayerView otherThan(final CountingUi gui, final PlayerView me) {
        if (gui.getGameView() == null || gui.getGameView().getPlayers() == null) {
            return null;
        }
        for (final PlayerView p : gui.getGameView().getPlayers()) {
            if (p != null && (me == null || p.getId() != me.getId())) {
                return p;
            }
        }
        return null;
    }

    /** Por nombre exacto; si no, el primero que haya. */
    private static Deck findDeck(final String name) {
        final List<Deck> decks = NeoFormat.COMMANDER.decks();
        if (decks.isEmpty()) {
            return null;
        }
        if (name == null) {
            return decks.get(0);
        }
        for (final Deck d : decks) {
            if (name.equals(d.getName())) {
                return d;
            }
        }
        return null;
    }

    private static String arg(final String[] args, final String key, final String fallback) {
        for (final String a : args) {
            if (a.startsWith(key + "=")) {
                return a.substring(key.length() + 1);
            }
        }
        return fallback;
    }

    private static int intArg(final String[] args, final String key, final int fallback) {
        final String v = arg(args, key, null);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (final NumberFormatException e) {
            return fallback;
        }
    }
}
