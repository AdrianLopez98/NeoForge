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
            final List<Deck> decks = NeoFormat.COMMANDER.decks();
            System.out.printf(Locale.ROOT, "  Mazos de Commander disponibles: %d%n", decks.size());
            if (decks.size() < 2) {
                System.out.println("  FALLO: hacen falta al menos 2 mazos de Commander");
                return;
            }
            final Deck hostDeck = decks.get(0);
            final Deck guestDeck = decks.get(1);
            System.out.printf(Locale.ROOT, "  Anfitrion: %s | Invitado: %s%n",
                    hostDeck.getName(), guestDeck.getName());

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

            NeoLobby.setCommander(lobby);
            ok &= check("el lobby esta en Commander",
                    NeoLobby.variants(lobby).contains(GameType.Commander));

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
            guestProc = spawnGuest(guestDeck.getName(), sharedName, guestResult);
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
                return s != null && s.getDeck() != null && s.isReady();
            }, 60_000);
            final LobbySlot remote = lobby.getSlot(guestSeat);
            ok &= check("el mazo del invitado ha llegado al anfitrion",
                    guestReady && remote != null && remote.getDeck() != null);
            if (remote != null && remote.getDeck() != null) {
                ok &= check("y es el SUYO, no el del anfitrion",
                        guestDeck.getName().equals(remote.getDeck().getName()));
            }

            // ---- 4. el anfitrion se prepara ----
            lobby.applyToSlot(hostSeat, UpdateLobbyPlayerEvent.deckUpdate(hostDeck));
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
            lobby.applyToSlot(aiSeat, UpdateLobbyPlayerEvent.deckUpdate(hostDeck));
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
    private static Process spawnGuest(final String deckName, final String guestName,
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

    private static boolean check(final String what, final boolean ok) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK " : "MAL", what);
        return ok;
    }
}
