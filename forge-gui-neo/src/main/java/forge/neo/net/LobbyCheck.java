package forge.neo.net;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import forge.deck.Deck;
import forge.game.GameType;
import forge.game.player.PlayerView;
import forge.gamemodes.match.GameLobby;
import forge.gamemodes.match.LobbySlot;
import forge.gamemodes.match.LobbySlotType;
import forge.gamemodes.net.DeltaPacket;
import forge.gamemodes.net.event.UpdateLobbyPlayerEvent;
import forge.gamemodes.net.server.ServerGameLobby;
import forge.localinstance.properties.ForgeNetPreferences;
import forge.model.FModel;
import forge.neo.match.NeoFormat;
import forge.neo.match.NeoMatchUI;
import forge.trackable.TrackableCollection;

/**
 * Una partida privada entera, sin abrir una ventana.
 *
 * <p>Levanta el servidor aqui y lanza al invitado <b>en otro proceso</b>, que se
 * conecta por localhost. Los dos usan {@code NeoMatchUI} de verdad, en modo
 * {@code AUTO_PLAY}: lo que se comprueba es la clase que va a jugar, no una
 * maqueta que se le parece.
 *
 * <p><b>Por que dos procesos y no uno.</b> Se intento en uno y se clava, y el
 * motivo merece quedar escrito porque no se deduce leyendo el codigo:
 * {@code FThreads} resuelve el hilo de interfaz por {@code GuiBase.getInterface()},
 * que es un <b>estatico global</b>. En la misma JVM, anfitrion e invitado
 * comparten ese unico hilo. Y varias llamadas del protocolo devuelven valor
 * ({@code tempShowZones}, {@code getAbilityToPlay}, {@code assignCombatDamage}…),
 * en las que el anfitrion <b>bloquea su hilo de interfaz</b> esperando la
 * respuesta — no es cosa nuestra, lo envuelve el propio Forge en
 * {@code InputSelectEntitiesFromList}. Con dos procesos el invitado contesta en
 * su hilo y son milisegundos; con uno, la respuesta tendria que procesarse en el
 * hilo que la esta esperando. Los tests de red de Forge tienen un
 * {@code MultiProcessGameExecutor} por exactamente esto.
 *
 * <p>Lo que se blinda, y nada de ello se ve en una captura:
 * <ol>
 *   <li>que el invitado <b>entre</b> y le toque asiento;</li>
 *   <li>que el <b>Commander</b> llegue a su copia del lobby;</li>
 *   <li>que su <b>mazo</b> viaje al anfitrion — cada uno juega con el suyo;</li>
 *   <li>que lleguen <b>paquetes delta</b> a los dos lados, que es lo unico que
 *       mueve la mesa del invitado, y</li>
 *   <li>que la partida <b>termine</b> con las dos interfaces contestando.</li>
 * </ol>
 *
 * <p>{@code run.cmd lobbycheck}
 */
public final class LobbyCheck {

    /** Un puerto poco probable, para no chocar con una partida de verdad. */
    private static final int PORT = 36799;

    /**
     * Tope de la partida. Bajable con {@code -Dneo.lobby.timeout=N} para iterar
     * rapido cuando se persigue un cuelgue.
     */
    private static final int GAME_TIMEOUT_SECS =
            Integer.getInteger("neo.lobby.timeout", 300);

    private LobbyCheck() {
    }

    /**
     * A que se juega la partida viva.
     *
     * <p>Commander, que es lo que se juega aqui y lo que hay que tener
     * blindado: la zona de mando y el danyo de comandante viajando por el cable
     * son la parte con mas piezas. El resto de modos los cubre
     * {@code checkFormats} en frio, que no monta una partida.
     *
     * <p>{@code -Dneo.lobby.check.format=MOMIR} juega la partida entera en otro
     * modo. No se pone por defecto porque cada corrida son ~60 s y la bateria
     * ya tarda; se usa a mano cuando se toca esta parte. Momir es el que mas
     * merece la pena: es el unico en el que los asientos van <b>sin mazo</b>.
     */
    private static NeoFormat liveFormat() {
        final String wanted = System.getProperty("neo.lobby.check.format");
        if (wanted != null && !wanted.isBlank()) {
            for (final NeoFormat f : NeoLobby.FORMATS) {
                if (f.name().equalsIgnoreCase(wanted.trim())) {
                    return f;
                }
            }
            System.out.println("  (modo desconocido: " + wanted + "; se juega Commander)");
        }
        return NeoFormat.COMMANDER;
    }

    /**
     * {@code NeoMatchUI} que ademas cuenta lo que llega por el cable.
     *
     * <p>Contar {@code applyDelta} no es un adorno: es la prueba de que heredar
     * de {@code NetworkGuiGame} sirve para algo. Con {@code AbstractGuiGame}
     * esos paquetes se reciben y se tiran en silencio — la mesa del invitado no
     * se moveria nunca, pero <b>la partida terminaria igual</b>, asi que sin
     * contarlos la prueba pasaria estando rota.
     */
    private static final class CountingUi extends NeoMatchUI {
        private final AtomicInteger deltas = new AtomicInteger();
        private final CountDownLatch openedLatch = new CountDownLatch(1);

        CountingUi() {
            super(NeoMatchUI.Mode.AUTO_PLAY, false);
        }

        @Override
        public void applyDelta(final DeltaPacket packet) {
            deltas.incrementAndGet();
            super.applyDelta(packet);
        }

        @Override
        public void openView(final TrackableCollection<PlayerView> myPlayers) {
            openedLatch.countDown();
            super.openView(myPlayers);
        }
    }

    public static void run() {
        final long t0 = System.currentTimeMillis();
        boolean ok = true;

        // UPnP fuera: sin ventana el dialogo devuelve la opcion por defecto, que
        // es "abrir el puerto" — cinco segundos y una consulta al router que
        // esta prueba no necesita.
        final String upnpBefore =
                FModel.getNetPreferences().getPref(ForgeNetPreferences.FNetPref.UPnP);
        final String portBefore =
                FModel.getNetPreferences().getPref(ForgeNetPreferences.FNetPref.NET_PORT);
        FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.UPnP, "NEVER");
        // El anfitrion pone el auto-pase en FALSE y el invitado, en TRUE. Luego
        // se le pregunta al anfitrion que valor usa PARA EL INVITADO: si dice
        // true, es que los ajustes del invitado le llegaron. Ver el paso 8.
        final String yieldBefore = FModel.getPreferences().getPref(
                forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_PASS_NO_ACTIONS);
        FModel.getPreferences().setPref(forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_PASS_NO_ACTIONS, false);
        FModel.getNetPreferences().setPref(
                ForgeNetPreferences.FNetPref.NET_PORT, String.valueOf(PORT));

        Process guestProc = null;
        try {
            // ---- mazos ----
            final NeoFormat format = liveFormat();
            final boolean needsDeck = NeoLobby.needsDeck(format);
            Deck hostDeck = null;
            Deck guestDeck = null;
            if (needsDeck) {
                final List<Deck> decks = format.decks();
                System.out.printf(Locale.ROOT, "  Mazos de %s disponibles: %d%n",
                        format, decks.size());
                if (decks.size() < 2) {
                    System.out.printf(Locale.ROOT,
                            "  FALLO: hacen falta al menos 2 mazos de %s%n", format);
                    return;
                }
                hostDeck = decks.get(0);
                guestDeck = decks.get(1);
                System.out.printf(Locale.ROOT, "  Anfitrion: %s | Invitado: %s%n",
                        hostDeck.getName(), guestDeck.getName());
            } else {
                System.out.printf(Locale.ROOT,
                        "  %s no lleva mazo: lo monta el motor%n", format);
            }

            // ---- 1. el servidor ----
            final CountingUi hostUi = new CountingUi();
            final ServerGameLobby lobby = NeoLobby.host(() -> hostUi);
            ok &= check("el servidor esta hospedando", NeoLobby.isHosting());

            // El lobby necesita un oyente. Jugando se lo pone Forge solo (es lo
            // que devuelve NeoOnline.setLobby, y NetConnectUtil lo engancha);
            // aqui no hay pantalla, asi que se pone uno que solo cuenta. Sin
            // el, applyToSlot revienta con NPE en cuanto cambias el TIPO de un
            // asiento -- que es justo lo que hace sentar una IA.
            final AtomicInteger lobbyUpdates = new AtomicInteger();
            lobby.setListener(new forge.interfaces.IUpdateable() {
                @Override
                public void update(final boolean fullUpdate) {
                    lobbyUpdates.incrementAndGet();
                }

                @Override
                public void update(final int slot, final LobbySlotType type) {
                    lobbyUpdates.incrementAndGet();
                }
            });

            NeoLobby.setFormat(lobby, format);
            ok &= check("el lobby esta en " + format, NeoLobby.formatOf(lobby) == format);
            if (format == NeoFormat.COMMANDER) {
                // La variante puesta a mano, no solo nuestra lectura: es lo que
                // hace que se pregunte por la zona de mando y que se pierda a
                // 21 de danyo de comandante.
                ok &= check("y la variante Commander esta aplicada de verdad",
                        NeoLobby.variants(lobby).contains(GameType.Commander));
            }

            // ---- 2. el invitado, en su propio proceso ----
            final AtomicReference<String> guestResult = new AtomicReference<>();
            // El invitado se llama IGUAL que el anfitrion, y es deliberado:
            // asi se reproduce la partida real en la que los dos jugadores no
            // habian cambiado su nombre de perfil y la mesa ponia "Ana" en las
            // dos barras. El motor sabe desempatar (llama "2o Ana" al
            // segundo); lo que hay que comprobar es que se lo pedimos.
            final String sharedName = forge.neo.look.NeoLook.playerName();
            System.out.printf(Locale.ROOT,
                    "  Los dos jugadores se llaman \"%s\" (a proposito)%n", sharedName);
            guestProc = spawnGuest(format,
                    guestDeck == null ? "" : guestDeck.getName(), sharedName, guestResult);
            ok &= check("el proceso del invitado ha arrancado", guestProc != null);
            if (guestProc == null) {
                return;
            }

            final boolean sat = waitUntil(() -> NeoLobby.activeSlots(lobby).size() >= 2, 180_000);
            System.out.printf(Locale.ROOT, "  Lobby: %s%n", NeoLobby.describe(lobby));
            ok &= check("el invitado se ha sentado", sat);
            if (!sat) {
                return;
            }

            final int guestSeat = seatOf(lobby, LobbySlotType.REMOTE);
            final int hostSeat = seatOf(lobby, LobbySlotType.LOCAL);
            ok &= check("hay un asiento REMOTO y uno LOCAL", guestSeat >= 0 && hostSeat >= 0);

            // ---- 3. su mazo viaja ----
            //
            // El anfitrion NO tiene los mazos de sus amigos: el Deck entero va
            // dentro del UpdateLobbyPlayerEvent que manda el invitado.
            final boolean guestReady = waitUntil(() -> {
                final LobbySlot s = lobby.getSlot(guestSeat);
                return s != null && s.isReady() && (!needsDeck || s.getDeck() != null);
            }, 60_000);
            final LobbySlot remote = lobby.getSlot(guestSeat);
            if (needsDeck) {
                ok &= check("el mazo del invitado ha llegado al anfitrion",
                        guestReady && remote != null && remote.getDeck() != null);
                if (remote != null && remote.getDeck() != null) {
                    ok &= check("y es el SUYO, no el del anfitrion",
                            guestDeck.getName().equals(remote.getDeck().getName()));
                }
            } else {
                // Sin mazo y listo: en Momir es lo correcto, y es justo lo que
                // el boton "Listo" de la sala tiene que permitir.
                ok &= check("el invitado se pone listo SIN mazo (lo monta el motor)",
                        guestReady && remote != null && remote.getDeck() == null);
            }

            // ---- 4. el anfitrion se prepara ----
            //
            // Y cambiar de mazo tiene que AVISAR (es lo que hace que el
            // servidor reparta el estado a los demas). El mazo solo no avisa
            // (LobbySlot.apply no lo cuenta como cambio), y en la sala de
            // verdad eso dejaba al invitado sin poder ponerse "Listo".
            final int updatesBefore = lobbyUpdates.get();
            if (hostDeck != null) {
                for (final UpdateLobbyPlayerEvent e : NeoLobby.deckEvents(hostDeck)) {
                    lobby.applyToSlot(hostSeat, e);
                }
                ok &= check("cambiar de mazo avisa (si no, nadie mas se entera)",
                        lobbyUpdates.get() > updatesBefore);
            }
            lobby.applyToSlot(hostSeat, UpdateLobbyPlayerEvent.isReadyUpdate(true));

            // ---- 4-bis. y sienta una IA ----
            //
            // Esto es lo que se llevaba la partida por delante: el asiento
            // nuevo nace SIN NOMBRE y, si no se le pone, el motor construye un
            // Player con nombre null que hace estallar al siguiente. Se usa el
            // mismo evento que compone la pantalla (NeoLobby.aiSeatEvent), no
            // uno hecho a mano aqui: si no, se estaria probando otra cosa.
            lobby.addSlot();
            final int aiSeat = lobby.getNumberOfSlots() - 1;
            lobby.applyToSlot(aiSeat, NeoLobby.aiSeatEvent(lobby));
            if (hostDeck != null) {
                lobby.applyToSlot(aiSeat, UpdateLobbyPlayerEvent.deckUpdate(hostDeck));
            }
            lobby.applyToSlot(aiSeat, UpdateLobbyPlayerEvent.isReadyUpdate(true));
            final LobbySlot ai = lobby.getSlot(aiSeat);
            ok &= check("la IA se sienta CON NOMBRE (sin el, no arranca la partida)",
                    ai != null && ai.getName() != null && !ai.getName().isBlank());
            ok &= check("y es una IA", ai != null && ai.getType() == LobbySlotType.AI);

            System.out.printf(Locale.ROOT, "  Lobby listo: %s%n", NeoLobby.describe(lobby));
            ok &= check("todos listos", lobby.findFirstUnreadySlot() == null);

            // ---- 5. a jugar ----
            final Runnable start = lobby.startGame();
            ok &= check("el lobby deja empezar", start != null);
            if (start == null) {
                System.out.println("  (startGame ha devuelto null: mira el aviso de arriba)");
                return;
            }
            start.run();

            ok &= check("el anfitrion abre partida",
                    hostUi.openedLatch.await(120, TimeUnit.SECONDS));

            // Tres asientos: los dos humanos y la IA. Si la IA no hubiera
            // llegado a la mesa, la partida seria de dos y esto lo cazaria.
            final int seated = hostUi.getGameView() == null ? 0
                    : hostUi.getGameView().getPlayers().size();
            System.out.printf(Locale.ROOT, "  Jugadores en la mesa: %d%n", seated);
            ok &= check("la partida ha arrancado CON la IA dentro (2 humanos + 1 IA)",
                    seated == 3);

            // AQUI y no al final: GameLobby.onMatchOver() vacia el mapa de
            // controladores, asi que preguntando despues de la partida la
            // respuesta depende de quien llegue primero.
            ok &= checkGuestSettingsReachedHost(lobby, guestSeat);
            ok &= checkNetMatch(lobby, hostUi);

            // ---- 6. la partida termina ----
            final boolean finished = waitUntil(
                    () -> hostUi.getGameView() != null && hostUi.getGameView().isGameOver(),
                    GAME_TIMEOUT_SECS * 1000L);
            if (!finished) {
                dumpThreads();
            }

            final int turns = hostUi.getGameView() == null ? 0 : hostUi.getGameView().getTurn();
            final String winner = hostUi.getGameView() == null
                    ? null : hostUi.getGameView().getWinningPlayerName();
            System.out.printf(Locale.ROOT, "  Turnos: %d | gana: %s%n",
                    turns, winner == null || winner.isBlank() ? "(nadie)" : winner);
            ok &= check("la partida ha terminado", finished);
            ok &= check("se han jugado turnos de verdad", turns >= 3);

            // ---- 7. lo que vio cada lado ----
            System.out.printf(Locale.ROOT, "  Anfitrion: %d decisiones%n",
                    hostUi.getDecisionCount());
            ok &= check("el anfitrion ha tomado decisiones", hostUi.getDecisionCount() > 0);

            // El invitado cuenta lo suyo en su proceso y lo dice en una linea.
            waitUntil(() -> guestResult.get() != null, 30_000);
            final String res = guestResult.get();
            System.out.printf(Locale.ROOT, "  Invitado: %s%n", res == null ? "(sin respuesta)" : res);
            ok &= check("el invitado ha contestado con su resultado", res != null);
            if (res != null) {
                ok &= check("el invitado tambien ve la partida terminada", res.contains("ok=true"));
                ok &= check("al invitado le llegan deltas (si no, su mesa estaria congelada)",
                        positiveField(res, "deltas"));
                ok &= check("el invitado ha tomado decisiones",
                        positiveField(res, "decisiones"));

                // --- su propia barra de jugador ---
                //
                // miMazo sale del jugador VIVO (el del GameView) y miMazoCrudo,
                // de la copia que llega en openView. El invitado veia su barra
                // a cero porque la mesa leia la copia, que nadie actualiza.
                // Se comprueban los dos: uno demuestra que ahora funciona y el
                // otro, que el arreglo hace falta.
                ok &= check("el invitado SI ve su propio mazo (era 0: la barra vacia)",
                        positiveField(res, "miMazo"));
                if (field(res, "miMazoCrudo") == 0) {
                    System.out.println("        (y la copia de openView sigue a 0, "
                            + "que es justo el fallo que se esquiva)");
                }

                // --- los nombres ---
                //
                // Los dos jugadores se llamaban igual. Si el desempate del motor
                // no se pidiera, estos dos textos serian identicos.
                final String mine = field(res, "miNombre", "?");
                final String theirs = field(res, "rivalNombre", "?");
                System.out.printf(Locale.ROOT, "  El invitado se ve como \"%s\" y al rival como \"%s\"%n",
                        mine, theirs);
                ok &= check("los dos jugadores NO salen con el mismo nombre",
                        !"?".equals(mine) && !mine.equals(theirs));

                // --- el aviso que cierra la mesa ---
                ok &= check("al invitado le llega afterGameEnd (es lo unico que le saca de la mesa)",
                        res.contains("fin=true"));
            }

            // ---- 8. las frases del motor, en cualquier idioma ----
            ok &= checkPhrasesAreLanguageProof();

            // ---- 9. la sala, sin red ----
            ok &= checkSeatRemoval();
            ok &= checkFormats();
            ok &= checkServerPhrases();
            ok &= checkBuildStamp();
            ok &= checkHostChatHasAName();
            ok &= checkCarrierNatVerdict();

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("  FALLO: interrumpido");
            ok = false;
        } catch (final Exception e) {
            System.out.println("  FALLO: " + e);
            e.printStackTrace();
            ok = false;
        } finally {
            if (guestProc != null && guestProc.isAlive()) {
                guestProc.destroy();
                try {
                    if (!guestProc.waitFor(10, TimeUnit.SECONDS)) {
                        guestProc.destroyForcibly();
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            NeoLobby.stopHosting();
            // Las preferencias se tocaron SOLO en memoria (sin save()), pero
            // esta JVM puede seguir jugando despues.
            FModel.getPreferences().setPref(forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_PASS_NO_ACTIONS, yieldBefore);
            FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.UPnP, upnpBefore);
            FModel.getNetPreferences().setPref(ForgeNetPreferences.FNetPref.NET_PORT, portBefore);
        }

        System.out.printf(Locale.ROOT, "%n  %s  (%ds)%n",
                ok ? "TODO BIEN" : "HAY FALLOS",
                (System.currentTimeMillis() - t0) / 1000);
    }

    // ------------------------------------------------------------------
    // Que los arreglos de siempre valgan tambien en red
    // ------------------------------------------------------------------

    /**
     * Manda el invitado sobre COMO se le juega?
     *
     * <p>El motor corre en el anfitrion, asi que quien decide si a un jugador se
     * le pasa turno solo o se le resaltan las cartas jugables es el
     * {@code YieldController} que el ANFITRION tiene de el. Ese controlador lee
     * sus propias preferencias <b>salvo</b> que el cliente le haya mandado las
     * suyas ({@code seedYieldStateOnHost}, que llaman las dos GUIs oficiales y
     * nosotros no llamabamos).
     *
     * <p>Aqui los dos lados llevan el valor CONTRARIO a proposito — anfitrion
     * false, invitado true — asi que no puede salir bien por casualidad: si el
     * anfitrion dice true, es que la foto del invitado llego.
     */
    /**
     * La partida en red se juega con NUESTRAS reglas, y los arreglos de
     * controlador llegan tambien al asiento del invitado.
     *
     * <p>Sin {@link NetHostedMatch}, el lobby de Forge saca las reglas de sus
     * preferencias, donde {@code UI_MATCHES_PER_GAME} vale 3: en red se jugaba
     * al mejor de tres. Este comprobador no lo veia porque su invitado contesta
     * siempre "salir" al acabar, y eso cierra el partido tras la primera.
     */
    private static boolean checkNetMatch(final GameLobby lobby, final NeoMatchUI hostUi) {
        boolean ok = check("la partida en red la monta NetHostedMatch (nuestras reglas)",
                lobby.getHostedMatch() instanceof NetHostedMatch);
        final forge.game.Game game = hostUi.getGameView() == null ? null
                : hostUi.getGameView().getGame();
        ok &= check("y es a UNA partida, no al mejor de tres",
                game != null && game.getRules().getGamesPerMatch() == 1);
        boolean remoteSeat = false;
        boolean installed = false;
        if (game != null) {
            for (final forge.game.player.Player p : game.getPlayers()) {
                if (p.getController() instanceof forge.player.PlayerControllerHuman pch
                        && pch.getGui() instanceof forge.gamemodes.net.server.RemoteClientGuiGame) {
                    remoteSeat = true;
                    installed = forge.neo.match.ManaColor.isInstalled(p);
                }
            }
        }
        ok &= check("se encuentra el asiento del invitado en el anfitrion", remoteSeat);
        ok &= check("al invitado su dual tambien le pregunta el color (ManaColor en su asiento)",
                installed);
        return ok;
    }

    /** La cruz de la sala no puede descolocar a un invitado. Ver NeoLobby.mayRemoveSeat. */
    private static boolean checkSeatRemoval() {
        final ServerGameLobby l = new ServerGameLobby();
        // [0 LOCAL, 1 OPEN] de fabrica; se completa a [LOCAL, REMOTE, AI, REMOTE].
        l.getSlot(1).setType(LobbySlotType.REMOTE);
        l.addSlot();
        l.getSlot(2).setType(LobbySlotType.AI);
        l.addSlot();
        l.getSlot(3).setType(LobbySlotType.REMOTE);
        boolean ok = check("no se puede quitar el asiento de un amigo conectado",
                !NeoLobby.mayRemoveSeat(l, 3) && !NeoLobby.mayRemoveSeat(l, 1));
        ok &= check("ni uno que tenga un amigo DETRAS (le cambiaria la posicion)",
                !NeoLobby.mayRemoveSeat(l, 2));
        l.getSlot(3).setType(LobbySlotType.OPEN);
        ok &= check("una IA sin nadie detras si se puede quitar", NeoLobby.mayRemoveSeat(l, 2));
        ok &= check("y un hueco libre tambien", NeoLobby.mayRemoveSeat(l, 3));

        // El mazo del que se fue no se queda en su hueco.
        l.getSlot(3).setDeck(new Deck("de otro"));
        NeoLobby.clearOpenSeats(l);
        ok &= check("un hueco libre no se queda con el mazo del que se fue",
                l.getSlot(3).getDeck() == null);
        return ok;
    }

    /**
     * El selector de modo del anfitrion.
     *
     * <p>Son tres cosas y ninguna se ve en una captura:
     *
     * <ol>
     *   <li><b>Que el modo se pueda volver a leer.</b> {@code formatOf} tiene
     *       que devolver lo mismo que se puso, y para los SIETE: el invitado no
     *       recibe {@code currentGameType} — solo el conjunto de variantes — asi
     *       que si esa lectura falla, su pantalla le ofrece los mazos del modo
     *       equivocado. Estandar es el caso delicado: es la <b>ausencia</b> de
     *       variantes, no una variante llamada Constructed.</li>
     *   <li><b>Que cambiar de modo deje a todos sin mazo y sin "listo".</b> Un
     *       mazo de Commander en una sala de Estandar no lo rechaza nadie hasta
     *       que se da a EMPEZAR, y para entonces ya nadie ata el aviso al cambio
     *       de modo.</li>
     *   <li><b>Que se reparta.</b> El cambio tiene que provocar un aviso del
     *       lobby; sin el, el invitado se queda con el modo anterior en
     *       pantalla y eligiendo el mazo que no es. Es justo lo que pasaria con
     *       {@code clearVariants()} a secas, que no llama a
     *       {@code updateView}.</li>
     * </ol>
     */
    private static boolean checkFormats() {
        final ServerGameLobby l = new ServerGameLobby();
        final AtomicInteger updates = new AtomicInteger();
        l.setListener(new forge.interfaces.IUpdateable() {
            @Override
            public void update(final boolean fullUpdate) {
                updates.incrementAndGet();
            }

            @Override
            public void update(final int slot, final LobbySlotType type) {
                updates.incrementAndGet();
            }
        });

        boolean ok = true;
        for (final NeoFormat f : NeoLobby.FORMATS) {
            NeoLobby.setFormat(l, f);
            ok &= check("se puede volver a leer el modo: " + f.getLabel(),
                    NeoLobby.formatOf(l) == f);
        }
        // Y el de fabrica, sin que nadie haya elegido nada.
        ok &= check("una sala recien creada se lee como Estandar",
                NeoLobby.formatOf(new ServerGameLobby()) == NeoFormat.ESTANDAR);

        // Cambiar de modo borra lo que ya no vale, y avisa.
        NeoLobby.setFormat(l, NeoFormat.COMMANDER);
        l.getSlot(0).setDeck(new Deck("mi mazo de Commander"));
        l.getSlot(0).setIsReady(true);
        l.getSlot(1).setType(LobbySlotType.REMOTE);
        l.getSlot(1).setName("Invitado");
        l.getSlot(1).setDeck(new Deck("el mazo del invitado"));
        l.getSlot(1).setIsReady(true);

        final int before = updates.get();
        NeoLobby.setFormat(l, NeoFormat.ESTANDAR);
        ok &= check("cambiar de modo quita el mazo del anfitrion",
                l.getSlot(0).getDeck() == null && !l.getSlot(0).isReady());
        // El asiento remoto es el que no se puede tocar con un evento
        // (ServerGameLobby.mayEdit dice que no), y es justo el que hay que
        // limpiar: el invitado tenia su "Listo" puesto sobre otra cosa.
        ok &= check("y tambien el del invitado, que es el que no se puede editar",
                l.getSlot(1).getDeck() == null && !l.getSlot(1).isReady());
        ok &= check("el cambio se reparte (si no, el invitado ve el modo de antes)",
                updates.get() > before);
        ok &= check("y la sala ya esta en Estandar",
                NeoLobby.formatOf(l) == NeoFormat.ESTANDAR);

        // Los dos modos sin mazo, que son los que rompen el "Listo".
        ok &= check("Momir y MoJhoSto no piden mazo",
                !NeoLobby.needsDeck(NeoFormat.MOMIR) && !NeoLobby.needsDeck(NeoFormat.MOJHOSTO));
        ok &= check("y los demas si", NeoLobby.needsDeck(NeoFormat.COMMANDER)
                && NeoLobby.needsDeck(NeoFormat.ESTANDAR));
        ok &= check("en Momir no se le busca mazo a la IA",
                NeoLobby.randomAiDeck(NeoFormat.MOMIR) == null);

        // La IA tiene que poder sentarse en CUALQUIER modo: Brawl, Oathbreaker
        // y Tiny Leaders no traen preconstruidos, asi que si esto devolviera
        // null el anfitrion se quedaria sin rival y sin saber por que.
        for (final NeoFormat f : NeoLobby.FORMATS) {
            if (!NeoLobby.needsDeck(f)) {
                continue;
            }
            final Deck d = NeoLobby.randomAiDeck(f);
            System.out.printf(Locale.ROOT, "    IA en %s: %s%n",
                    f.getLabel(), d == null ? "(sin mazo)" : d.getName());
            ok &= check("la IA encuentra mazo en " + f.getLabel(), d != null);
        }

        // Los textos de la fila del modo y del enlace a la guia. Sin esto lo
        // que sale en pantalla es la clave, que es lo que pasa siempre que se
        // añade una pantalla y se olvida un idioma.
        for (final String key : new String[] {"lobby.format", "lobby.format.noDeck",
                "lobby.format.changed", "lobby.autoDeck",
                "lobby.wiki", "lobby.wiki.note"}) {
            ok &= check("el texto " + key + " existe",
                    !forge.neo.NeoText.get(key).equals(key));
        }
        // Y cada modo tiene su nombre y su linea: la fila los usa los dos.
        for (final NeoFormat f : NeoLobby.FORMATS) {
            ok &= check("el modo " + f.name() + " tiene nombre y descripcion",
                    !f.getLabel().isBlank() && !f.getDescription().isBlank()
                            && !f.getDescription().startsWith("format."));
        }
        return ok;
    }

    /** Cada aviso en ingles del servidor se sigue reconociendo. Ver NetPhrases. */
    private static boolean checkServerPhrases() {
        final String[][] samples = {
                {"Pepe Luis joined the lobby.", "PLAIN"},
                {"Pepe Luis left the lobby.", "PLAIN"},
                {"Pepe Luis is ready (1/2 players ready)", "PLAIN"},
                {"Pepe Luis is not ready (1/2 players ready)", "PLAIN"},
                {"Pepe Luis disconnected. Waiting 5:00 for reconnect...", "DISCONNECTED"},
                {"Pepe Luis: 4:30 remaining to reconnect.", "PLAIN"},
                {"Pepe Luis has reconnected.", "RECONNECTED"},
                {"Host forced AI takeover for Pepe Luis.", "REPLACED_BY_AI"},
                {"Pepe Luis did not reconnect in time. AI has taken over.", "REPLACED_BY_AI"},
                {"Timeout disabled for Pepe Luis. Waiting indefinitely for reconnect.", "PLAIN"},
                {"(Host can use /skipreconnect to replace disconnected player with AI, or "
                        + "/skiptimeout to wait indefinitely.)", "HOST_HINT"},
                {"Pepe Luis changed their name to Luis", "PLAIN"},
        };
        boolean read = true;
        for (final String[] sample : samples) {
            final NetPhrases.Phrase p = NetPhrases.read(null, sample[0]);
            final boolean one = p.kind().name().equals(sample[1]) && !p.text().equals(sample[0])
                    && (sample[1].equals("HOST_HINT") || p.text().contains("Pepe Luis"));
            if (!one) {
                System.out.printf(Locale.ROOT, "        no se lee: [%s] -> %s [%s]%n",
                        sample[0], p.kind(), p.text());
            }
            read &= one;
        }
        boolean ok = check("los avisos del servidor se reconocen (y no pierden el nombre)", read);
        final NetPhrases.Phrase who = NetPhrases.read(null,
                "Pepe Luis disconnected. Waiting 5:00 for reconnect...");
        ok &= check("y se sabe DE QUIEN hablan", "Pepe Luis".equals(who.who()));
        ok &= check("lo que escribe una persona no se toca",
                NetPhrases.read("Ana", "Pepe joined the lobby.").text()
                        .equals("Ana: Pepe joined the lobby."));
        boolean keys = true;
        for (final String k : NetPhrases.keys()) {
            if (forge.neo.NeoText.get(k).equals(k)) {
                System.out.println("        falta el texto " + k);
                keys = false;
            }
        }
        ok &= check("cada aviso tiene su texto", keys);
        return ok;
    }

    /** La huella de la version viaja y se entiende. Ver NetBuild. */
    private static boolean checkBuildStamp() {
        final String id = NetBuild.id();
        System.out.printf(Locale.ROOT, "  Huella de esta version: %s%n", id);
        boolean ok = check("la huella se lee de vuelta de su linea de chat",
                id.equals(NetBuild.parse(NetBuild.message())));
        ok &= check("una linea normal no es una huella", NetBuild.parse("hola [neo]") == null);
        ok &= check("dos huellas distintas avisan", NetBuild.differs("aaaa", "bbbb"));
        ok &= check("con una en desarrollo no se avisa", !NetBuild.differs(NetBuild.DEV, "bbbb"));
        return ok;
    }

    /**
     * Lo que escribe el anfitrion lleva su nombre.
     *
     * <p>Se mandaba {@code new MessageEvent(texto)}, sin autor: el servidor
     * firma lo de los invitados, pero lo del anfitrion llegaba a todos como un
     * aviso del sistema.
     */
    private static boolean checkHostChatHasAName() {
        final NeoOnline.View view = (NeoOnline.View) java.lang.reflect.Proxy.newProxyInstance(
                NeoOnline.View.class.getClassLoader(), new Class<?>[] {NeoOnline.View.class},
                (proxy, method, args) -> method.isDefault()
                        ? java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args)
                        : null);
        final NeoOnline online = new NeoOnline(view);
        final AtomicReference<forge.gamemodes.net.event.MessageEvent> sent = new AtomicReference<>();
        online.setGameClient(new forge.gamemodes.net.IRemote() {
            @Override
            public void send(final forge.gamemodes.net.event.NetEvent event) {
                if (event instanceof forge.gamemodes.net.event.MessageEvent m) {
                    sent.set(m);
                }
            }

            @Override
            public Object sendAndWait(final forge.gamemodes.net.event.IdentifiableNetEvent event) {
                return null;
            }
        });
        online.say("hola");
        final forge.gamemodes.net.event.MessageEvent m = sent.get();
        return check("lo que escribe el anfitrion en el chat lleva su nombre",
                m != null && m.getSource() != null && !m.getSource().isBlank());
    }

    private static boolean checkGuestSettingsReachedHost(final GameLobby lobby, final int guestSeat) {
        final Object controller = lobby.getController(guestSeat);
        if (!(controller instanceof forge.player.PlayerControllerHuman pch)) {
            return check("se puede preguntar por los ajustes del invitado", false);
        }
        // La foto llega cuando el invitado abre partida, que es un instante
        // despues que el anfitrion. Mirar una sola vez seria una carrera.
        waitUntil(() -> pch.getYieldController().getBoolPref(forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_PASS_NO_ACTIONS), 60_000);
        final boolean autoPass = pch.getYieldController().getBoolPref(forge.localinstance.properties.ForgePreferences.FPref.YIELD_AUTO_PASS_NO_ACTIONS);
        System.out.printf(Locale.ROOT,
                "  Auto-pase del invitado segun el anfitrion: %s (anfitrion=false, invitado=true)%n",
                autoPass);
        return check("los ajustes del INVITADO llegan al anfitrion (auto-pase, resaltados)", autoPass);
    }

    /**
     * Se reconocen las frases del motor venga el idioma que venga?
     *
     * <p>Es el otro medio agujero de jugar en red: varias funciones se disparan
     * al <b>reconocer un texto</b> que compone Forge (adivinar arriba/abajo, el
     * "Auto" del pago de mana, el prompt rutinario). En red ese texto lo escribe
     * el ANFITRION, en SU idioma — y si el invitado juega en otro, esas
     * funciones se apagaban <b>en silencio</b>.
     *
     * <p>Se comprueba con las frases de VERDAD de cada fichero de idioma de
     * Forge, no con una inventada.
     */
    private static boolean checkPhrasesAreLanguageProof() {
        boolean ok = check("se leen los idiomas de Forge",
                forge.neo.EnginePhrase.languageCount() >= 5);

        ok &= phraseInEveryLanguage("lblPutCardOnTopOrBottomLibrary", "adivinar (arriba o abajo)");
        ok &= phraseInEveryLanguage("lblPutCardsOnTheTopLibraryOrGraveyard", "vigilar");
        ok &= phraseInEveryLanguage("lblAICantPlayCards", "aviso de la IA");

        // El "Auto" del pago de mana es una etiqueta de boton: comparacion exacta.
        int autoOk = 0;
        final java.util.List<String> autos = forge.neo.EnginePhrase.known("lblAuto");
        for (final String label : autos) {
            if (forge.neo.EnginePhrase.equalsAny(label, "lblAuto")) {
                autoOk++;
            }
        }
        System.out.printf(Locale.ROOT, "  'Auto' reconocido en %d de %d idiomas%n",
                autoOk, autos.size());
        ok &= check("el 'Auto' del pago de mana se reconoce en cualquier idioma",
                !autos.isEmpty() && autoOk == autos.size());

        // Y que no diga que si a cualquier cosa.
        ok &= check("no reconoce un texto que no es",
                !forge.neo.EnginePhrase.contains(
                        "Esto no es la pregunta de adivinar, es otra cosa cualquiera",
                        "lblPutCardOnTopOrBottomLibrary"));
        return ok;
    }

    /**
     * La frase de esa clave, en cada idioma, tiene que reconocerse.
     *
     * <p>Se construye como la construiria el motor: sustituyendo el hueco por un
     * nombre de carta. Y se usa un nombre <b>cualquiera</b>, porque ese es el
     * caso real: cada lado traduce el nombre por su cuenta y no tiene por que
     * coincidir. Comparando por trozos literales, el nombre no participa.
     */
    private static boolean phraseInEveryLanguage(final String key, final String what) {
        final java.util.List<String> templates = forge.neo.EnginePhrase.known(key);
        if (templates.isEmpty()) {
            return check("hay frases para " + what, false);
        }
        int hits = 0;
        for (final String t : templates) {
            final String asEngineWouldWrite = t.replace("{0}", "Sol Ring")
                    .replace("{1}", "2").replace("''", "'");
            if (forge.neo.EnginePhrase.contains(asEngineWouldWrite, key)) {
                hits++;
            }
        }
        System.out.printf(Locale.ROOT, "  %s: reconocido en %d de %d idiomas%n",
                what, hits, templates.size());
        return check(what + ": se reconoce en CUALQUIER idioma", hits == templates.size());
    }

    // ------------------------------------------------------------------
    // El proceso del invitado
    // ------------------------------------------------------------------

    /**
     * Lanza otra JVM con el mismo classpath y la deja jugando de invitada.
     *
     * <p>Su salida se lee en un hilo aparte y se va imprimiendo con sangria: si
     * el invitado falla, el motivo tiene que verse aqui, no perderse en una
     * consola que nadie mira.
     */
    private static Process spawnGuest(final NeoFormat format, final String deckName,
                                      final String guestName,
                                      final AtomicReference<String> result) {
        try {
            final String java = System.getProperty("java.home")
                    + File.separator + "bin" + File.separator + "java";
            final List<String> cmd = new ArrayList<>();
            cmd.add(java);
            // Los mismos que pasan los lanzadores: XStream los necesita en 17.
            cmd.add("--add-opens");
            cmd.add("java.base/java.util=ALL-UNNAMED");
            cmd.add("--add-opens");
            cmd.add("java.base/java.lang=ALL-UNNAMED");
            cmd.add("-Dfile.encoding=UTF-8");
            cmd.add("-Xmx2g");
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("forge.neo.NeoMain");
            cmd.add("lobbyguest");
            cmd.add("--port=" + PORT);
            cmd.add("--deck=" + deckName);
            cmd.add("--format=" + format.name());
            cmd.add("--name=" + guestName);
            cmd.add("--wait=" + GAME_TIMEOUT_SECS);

            final ProcessBuilder pb = new ProcessBuilder(cmd);
            // El directorio de trabajo importa: sin el, "../forge-gui/" no
            // resuelve y el invitado no encuentra ni una carta.
            pb.directory(new File(System.getProperty("user.dir")));
            pb.redirectErrorStream(true);
            final Process p = pb.start();

            final Thread pump = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(
                        p.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        if (line.startsWith(LobbyGuest.RESULT_PREFIX)) {
                            result.set(line.substring(LobbyGuest.RESULT_PREFIX.length()).trim());
                        }
                        // Su carga de cartas son cuarenta lineas que no aportan
                        // nada aqui: solo se enseña lo del invitado.
                        if (line.startsWith("[invitado]") || line.startsWith("[lobby]")
                                || line.contains("Exception") || line.contains("FALLO")) {
                            System.out.println("      | " + line);
                        }
                    }
                } catch (final Exception e) {
                    System.out.println("      | (se corto la salida del invitado: " + e + ")");
                }
            }, "lobbycheck-guest-out");
            pump.setDaemon(true);
            pump.start();
            System.out.println("  Invitado lanzado en otro proceso (carga sus cartas, tarda un poco)");
            return p;
        } catch (final Exception e) {
            System.out.println("  FALLO al lanzar el invitado: " + e);
            return null;
        }
    }

    /** {@code campo=N} con N mayor que cero, dentro de la linea de resultado. */
    private static boolean positiveField(final String line, final String field) {
        return field(line, field) > 0;
    }

    /** El valor numerico de {@code campo=N} en la linea del invitado, o -1. */
    private static int field(final String line, final String field) {
        final String key = field + "=";
        final int at = line.indexOf(key);
        if (at < 0) {
            return -1;
        }
        int end = at + key.length();
        while (end < line.length() && Character.isDigit(line.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(line.substring(at + key.length(), end));
        } catch (final NumberFormatException e) {
            return -1;
        }
    }

    /**
     * El valor de texto de {@code campo=algo} en la linea del invitado.
     *
     * <p>Corta en el siguiente {@code " x="}, no en el siguiente espacio: un
     * nombre de jugador puede llevarlos ("2o Ana" es el caso que se comprueba).
     */
    private static String field(final String line, final String name, final String fallback) {
        final String key = name + "=";
        final int at = line.indexOf(key);
        if (at < 0) {
            return fallback;
        }
        final int from = at + key.length();
        final java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\\s\\w+=").matcher(line);
        final int end = m.find(from) ? m.start() : line.length();
        final String value = line.substring(from, end).trim();
        return value.isEmpty() ? fallback : value;
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** El primer asiento de ese tipo, o -1. */
    private static int seatOf(final GameLobby lobby, final LobbySlotType type) {
        for (int i = 0; i < lobby.getNumberOfSlots(); i++) {
            final LobbySlot s = lobby.getSlot(i);
            if (s != null && s.getType() == type) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Espera a que algo se cumpla.
     *
     * <p>Casi todo aqui pasa por el cable y llega cuando llega. Fijar esperas a
     * ojo hace que la prueba falle en un ordenador lento y pase en uno rapido,
     * que es la peor clase de prueba.
     */
    private static boolean waitUntil(final java.util.function.BooleanSupplier cond,
                                     final long timeoutMs) {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (cond.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(100);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return cond.getAsBoolean();
    }

    /**
     * Donde esta parado cada hilo.
     *
     * <p>Es lo primero que hay que mirar cuando esto se cuelga, y por eso lo
     * hace la propia prueba: un volcado pedido desde fuera llega tarde y hay que
     * acertar con el momento.
     */
    private static void dumpThreads() {
        System.out.println();
        System.out.println("  ---- HILOS (la partida no ha terminado) ----");
        final java.util.Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        final List<Thread> sorted = new ArrayList<>(all.keySet());
        sorted.sort(java.util.Comparator.comparing(Thread::getName));
        for (final Thread t : sorted) {
            final String n = t.getName();
            if (n.startsWith("Reference Handler") || n.startsWith("Finalizer")
                    || n.startsWith("Signal Dispatcher") || n.startsWith("Common-Cleaner")
                    || n.startsWith("Notification Thread") || n.startsWith("process reaper")) {
                continue;
            }
            System.out.printf(Locale.ROOT, "  [%s] %s%n", t.getState(), n);
            final StackTraceElement[] st = all.get(t);
            for (int i = 0; i < Math.min(st.length, 12); i++) {
                System.out.printf(Locale.ROOT, "      %s%n", st[i]);
            }
        }
        System.out.println("  --------------------------------------------");
        System.out.println();
    }

    /**
     * El veredicto de {@link NetReach}, sin tocar la red.
     *
     * <p>Lo que se prueba es la <b>regla</b>, que es lo unico que se puede
     * comprobar aqui: preguntarle de verdad al router necesita un router, y el
     * resultado dependeria de en que casa se ejecute la bateria. La consulta en
     * si (jupnp, descubrimiento, {@code GetExternalIPAddress}) se traga todo lo
     * que salga mal y devuelve "no lo se", asi que lo que puede estropear una
     * sala no es la consulta: es clasificar mal lo que conteste.
     *
     * <p><b>La mitad de las comprobaciones son de NO avisar</b>, y esas son las
     * que importan. Un falso positivo aqui le esconde a alguien la direccion
     * con la que podia hospedar perfectamente, y encima le dice que la culpa es
     * de su operador: se equivoca y ademas manda a buscar en el sitio
     * equivocado. Por eso todo lo dudoso sale {@code UNKNOWN}.
     */
    private static boolean checkCarrierNatVerdict() {
        System.out.println();
        System.out.println("  -- el aviso de CGNAT (la regla, sin red) --");

        // Lo que se ve desde fuera es una del operador: CGNAT seguro.
        boolean ok = check("100.73.0.1 por fuera es CGNAT (el caso real del 18-09-2026)",
                NetReach.verdictFor("100.73.0.1", "81.0.42.39") == NetReach.Verdict.CGNAT);
        ok &= check("y los dos bordes del rango tambien (100.64 y 100.127)",
                NetReach.verdictFor("100.64.0.1", null) == NetReach.Verdict.CGNAT
                        && NetReach.verdictFor("100.127.255.254", null) == NetReach.Verdict.CGNAT);
        ok &= check("un router con la WAN en 10.x tambien (doble router en casa)",
                NetReach.verdictFor("10.0.0.1", "81.0.42.39") == NetReach.Verdict.CGNAT);
        ok &= check("y en 192.168.x y 172.16.x",
                NetReach.verdictFor("192.168.2.1", null) == NetReach.Verdict.CGNAT
                        && NetReach.verdictFor("172.20.0.1", null) == NetReach.Verdict.CGNAT);

        // Y ahora lo contrario, que es lo que no puede fallar.
        ok &= check("la misma IP a los dos lados es alcanzable (se puede abrir el puerto)",
                NetReach.verdictFor("79.146.248.201", "79.146.248.201")
                        == NetReach.Verdict.REACHABLE);
        ok &= check("100.63 y 100.128 quedan FUERA del rango del operador",
                NetReach.verdictFor("100.63.0.1", "100.63.0.1") == NetReach.Verdict.REACHABLE
                        && NetReach.verdictFor("100.128.0.1", "100.128.0.1")
                                == NetReach.Verdict.REACHABLE);
        ok &= check("172.32 tampoco es privada (el rango acaba en 172.31)",
                NetReach.verdictFor("172.32.0.1", "172.32.0.1") == NetReach.Verdict.REACHABLE);
        ok &= check("router callado: no se sabe, y NO se avisa",
                NetReach.verdictFor(null, "81.0.42.39") == NetReach.Verdict.UNKNOWN);
        ok &= check("dos publicas distintas: no se sabe (suele ser una VPN puesta aqui)",
                NetReach.verdictFor("79.146.248.201", "81.0.42.39") == NetReach.Verdict.UNKNOWN);
        ok &= check("una respuesta que no es una IP no se toma por CGNAT",
                NetReach.verdictFor("0.0.0.0.0", null) == NetReach.Verdict.UNKNOWN
                        && NetReach.verdictFor("300.1.1.1", null) == NetReach.Verdict.UNKNOWN
                        && NetReach.verdictFor("", null) == NetReach.Verdict.UNKNOWN);

        // El 100.64/10 significa dos cosas segun donde este, y confundirlas
        // convertiria a todo el que use Tailscale en un falso CGNAT.
        ok &= check("100.64/10 en una interfaz LOCAL es Tailscale, no CGNAT",
                NetReach.isCarrierNat("100.73.0.1") && !NetReach.isPrivate("100.73.0.1"));

        // Y la otra mitad del aviso: cual es la IP que SI sirve. Los nombres
        // son los que pone FServerManager.getFriendlyInterfaceName, no unos
        // nuestros: si aqui se escribe otra cosa, la red virtual sale como una
        // tarjeta de red mas y el jugador vuelve a tener que adivinar.
        ok &= check("se reconocen las redes virtuales con el nombre del motor",
                NetReach.isVirtualLan("Radmin VPN") && NetReach.isVirtualLan("ZeroTier")
                        && NetReach.isVirtualLan("Tailscale") && NetReach.isVirtualLan("Hamachi")
                        && NetReach.isVirtualLan("WireGuard")
                        && NetReach.isVirtualLan("VPN (tun0)")
                        && NetReach.isVirtualLan("VPN Tunnel")
                        && NetReach.isVirtualLan("Virtual Network"));
        ok &= check("y la tarjeta de casa NO se toma por una red virtual",
                !NetReach.isVirtualLan("Ethernet") && !NetReach.isVirtualLan("Wi-Fi")
                        && !NetReach.isVirtualLan("LAN (en0)") && !NetReach.isVirtualLan(null));

        // Los adaptadores fantasma de Windows. Medidos en una maquina normal:
        // 27, casi todos caidos. Si alguno colara como "red virtual dormida",
        // la sala mandaria a abrir la VPN del trabajo para jugar a Magic.
        // Que no se pierda ninguna direccion. El motor si las pierde: mete las
        // suyas en un mapa con el nombre bonito de clave, y en Windows la
        // tarjeta de verdad y el conmutador de Hyper-V/WSL se llaman los dos
        // ethN, o sea "Ethernet" los dos, o sea uno pisa al otro. Caso real:
        // la sala ensenyaba 172.24.192.1 (WSL) y la 192.168.x.x no salia.
        final List<String[]> mine = NeoOnline.localAddresses();
        int usableV4 = 0;
        try {
            for (final java.net.NetworkInterface i : java.util.Collections
                    .list(java.net.NetworkInterface.getNetworkInterfaces())) {
                if (!i.isUp() || i.isLoopback()) {
                    continue;
                }
                for (final java.net.InetAddress a : java.util.Collections.list(i.getInetAddresses())) {
                    if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress()
                            && !a.getHostAddress().startsWith("169.254.")) {
                        usableV4++;
                    }
                }
            }
        } catch (final java.net.SocketException e) {
            usableV4 = -1;
        }
        ok &= check("se enseñan TODAS las direcciones de esta maquina (" + usableV4 + ")",
                usableV4 < 0 || mine.size() == usableV4);
        final java.util.Set<String> labels = new java.util.HashSet<>();
        boolean dup = false;
        for (final String[] row : mine) {
            dup |= !labels.add(row[0]);
        }
        ok &= check("y ningun nombre se repite (dos 'Ethernet' serian una perdida)", !dup);
        ok &= check("los conmutadores virtuales de Windows van al final",
                virtualSwitchesLast(mine));

        ok &= check("los adaptadores fantasma de Windows NO cuelan como red de jugar",
                NeoOnline.dormantVirtualLan() == null
                        || !NeoOnline.dormantVirtualLan().toLowerCase(Locale.ROOT)
                                .contains("miniport"));

        // IPv6: la salida gratis del CGNAT. Lo que se comprueba es lo unico
        // que se rompe en silencio — la FORMA de la direccion. Medido sobre
        // URLValidator: con corchetes pasa, sin ellos devuelve null, o sea que
        // la sala contesta "esa direccion no vale" a una direccion correcta.
        ok &= check("una IPv6 pegada sin corchetes se arregla sola",
                "[2001:db8::1]".equals(NetReach.normaliseAddress("2001:db8::1")));
        ok &= check("y la que ya viene con corchetes no se toca",
                "[2001:db8::1]:36743"
                        .equals(NetReach.normaliseAddress("[2001:db8::1]:36743")));
        ok &= check("una IPv4 con puerto NO se toca (un solo ':')",
                "79.146.248.201:36743"
                        .equals(NetReach.normaliseAddress("79.146.248.201:36743")));
        ok &= check("ni un nombre de maquina, ni el hueco vacio",
                "midominio.com:36743"
                        .equals(NetReach.normaliseAddress(" midominio.com:36743 "))
                        && "".equals(NetReach.normaliseAddress("  "))
                        && NetReach.normaliseAddress(null) == null);
        ok &= check("lo arreglado lo entiende el motor de verdad",
                forge.util.URLValidator.parseURL(
                        NetReach.normaliseAddress("2001:db8::1")) != null
                        && forge.util.URLValidator.parseURL("2001:db8::1") == null);

        for (final String key : new String[] {"lobby.address.cgnat", "lobby.address.cgnat.what",
                "lobby.address.cgnat.fix", "lobby.address.cgnat.useVirtual",
                "lobby.address.cgnat.wakeUp", "lobby.address.cgnat.getIt",
                "lobby.address.ipv6", "lobby.address.ipv6.note",
                "lobby.address.cgnat.v4only", "lobby.address.cgnat.tryIpv6",
                "lobby.address.virtual", "lobby.address.virtual.note",
                "lobby.address.noRouter"}) {
            ok &= check("el texto " + key + " existe",
                    !forge.neo.NeoText.get(key).startsWith("lobby."));
        }
        ok &= check("y el de la direccion buena lleva sus dos huecos",
                forge.neo.NeoText.get("lobby.address.virtual", "Radmin VPN", "26.1.2.3:36743")
                        .contains("Radmin VPN")
                        && forge.neo.NeoText
                                .get("lobby.address.virtual", "Radmin VPN", "26.1.2.3:36743")
                                .contains("26.1.2.3:36743"));
        return ok;
    }

    /**
     * Que ninguna direccion de verdad quede detras de un conmutador virtual.
     *
     * <p>Se mira por el <b>rango</b> y no por el nombre: aqui ya solo hay la
     * cadena que se le ensenya al jugador. Es una aproximacion suficiente —
     * 172.16/12 es donde Windows monta los suyos (Hyper-V, WSL).
     */
    private static boolean virtualSwitchesLast(final List<String[]> rows) {
        boolean seenSwitch = false;
        for (final String[] row : rows) {
            final boolean isSwitch = row[1].startsWith("172.")
                    && NetReach.isPrivate(row[1].split(":")[0]);
            if (isSwitch) {
                seenSwitch = true;
            } else if (seenSwitch) {
                return false;   // una de verdad DESPUES de una virtual
            }
        }
        return true;
    }

    private static boolean check(final String what, final boolean ok) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK " : "MAL", what);
        return ok;
    }
}
