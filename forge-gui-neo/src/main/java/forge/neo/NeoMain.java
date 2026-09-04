package forge.neo;

import java.util.Locale;

import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.gui.GuiBase;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.match.NeoGame;
import forge.neo.match.NeoMatchUI;
import forge.neo.platform.ConsoleUiDispatcher;
import forge.neo.platform.FxUiDispatcher;
import forge.neo.platform.NeoGuiBase;

/**
 * Punto de entrada de la interfaz Neo.
 *
 * <pre>
 *   (sin argumentos)   lista los mazos de Commander        [fase 0]
 *   play               partida humano-automatico vs IA     [fase 1]
 *   watch              partida IA vs IA, solo observar     [fase 1]
 * </pre>
 *
 * Opciones: {@code --deck=nombre} {@code --opponents=N} {@code --timeout=S} {@code -v}
 */
public final class NeoMain {

    private NeoMain() {
    }

    public static void main(final String[] args) {
        // Lo PRIMERO: las -D de la linea de comandos.
        //
        // Los lanzadores pasan todos los argumentos DESPUES de la clase, asi
        // que una -D llega como argumento del programa y la JVM ni la mira.
        // Sin esto, todas las banderas de traza y de prueba documentadas
        // (-Dneo.images.debug, -Dneo.quest.demoCredits...) se ignoraban en
        // silencio, que es la peor forma de no funcionar.
        applySystemProperties(args);

        // Y justo despues, ANTES del registro: donde van los datos del jugador.
        // Con -Dneo.dataDir se quedan dentro de la carpeta del juego, que es lo
        // que hace que se pueda dar a alguien en un zip. En cuanto alguien toca
        // ForgeConstants ya no hay vuelta atras, y el registro es de los
        // primeros en necesitar una carpeta. Ver NeoPortable.
        NeoPortable.apply();

        final String cmd = args.length > 0 && !args[0].startsWith("-") ? args[0].toLowerCase(Locale.ROOT) : "list";

        // Y aqui mismo, antes que nada mas: la prueba del propio NeoPortable.
        //
        // Va delante del cerrojo, del registro y del motor a proposito. Lo que
        // comprueba es el fichero que le dejamos a Forge ANTES de que arranque,
        // asi que necesita mandar ella sobre forge.assetsDir y neo.dataDir; y
        // no le hacen falta las cartas, que son once segundos. Ver PortableCheck.
        if ("portablecheck".equals(cmd)) {
            banner("Perfil portable: que las rutas con acentos vuelvan igual");
            try {
                PortableCheck.run();
            } catch (final java.io.IOException e) {
                throw new IllegalStateException("no se ha podido montar el banco de pruebas", e);
            }
            return;
        }

        // Un solo NeoForge abierto, y lo PRIMERO de todo: si ya hay uno, este
        // proceso no tiene que hacer absolutamente nada, ni siquiera abrir el
        // registro. Rotar el neo.log que el otro tiene abierto no se puede, y
        // se veia: cada doble clic de mas dejaba una linea de error en el
        // registro DEL OTRO. Ver NeoLock.
        //
        // Se puede preguntar aqui porque el cerrojo solo necesita saber donde
        // van los datos (NeoPortable, ya aplicado): no toca ForgeConstants.
        final boolean windowed = "ui".equals(cmd);
        if (windowed && wantsSingleInstance(args) && !NeoLock.acquire()) {
            System.exit(0);
        }

        // El registro, a fichero, ANTES de que nada escriba nada. Es lo que
        // permite que jugar.cmd arranque sin ventana negra y que aun asi se
        // pueda mirar lo que paso. Ver NeoLog.
        NeoLog.start();

        final boolean verbose = has(args, "-v");
        final String deckName = opt(args, "--deck", null);
        final int opponents = Integer.parseInt(opt(args, "--opponents", "1"));
        final int timeout = Integer.parseInt(opt(args, "--timeout", "300"));

        banner("NeoForge");

        // 1. Adaptador de plataforma. OBLIGATORIO antes de tocar el motor:
        //    ForgeConstants resuelve el directorio de assets en su init estatico.
        //
        //    El hilo de interfaz depende del modo: con ventana es el de JavaFX,
        //    sin ventana uno dedicado. Es lo unico que cambia entre fase 1 y 2.
        //    (El cerrojo de instancia unica ya se pidio arriba, ANTES del
        //    registro: NeoLock.acquire() no se puede llamar dos veces desde
        //    el mismo proceso sin perder el cerrojo original — ver su
        //    javadoc, "el cerrojo vive mientras viva el canal".)

        GuiBase.setInterface(new NeoGuiBase(
                windowed ? new FxUiDispatcher() : new ConsoleUiDispatcher()));
        System.out.println("[1/2] IGuiBase registrado -> NeoGuiBase ("
                + (windowed ? "hilo JavaFX" : "hilo dedicado") + ")");

        // 2. Motor: base de cartas, ediciones, mazos, preferencias.
        //
        // ⚠️ CON VENTANA NO SE CARGA AQUI. Son once segundos largos, y hacerlos
        // antes de Application.launch dejaba la pantalla vacia todo ese rato:
        // sin ventana, sin icono en la barra de tareas y sin forma de saber si
        // el doble clic habia funcionado. Ahora lo hace NeoApp en un hilo de
        // fondo, con la pantalla de carga ya puesta. Ver NeoBoot.
        //
        // Sin ventana (play, watch, los comprobadores) se carga aqui de
        // siempre: no hay a quien enseñarle una barra.
        if (!windowed) {
            NeoBoot.loadEngine(null);
        }

        switch (cmd) {
            case "play":
                runGame(deckName, opponents, NeoMatchUI.Mode.AUTO_PLAY, timeout, verbose);
                break;
            case "watch":
                runGame(deckName, opponents, NeoMatchUI.Mode.OBSERVE, timeout, verbose);
                break;
            case "ui":
                banner("FASE 2 - interfaz JavaFX");
                // Los textos del motor, antes de construir NeoApp: hay campos
                // suyos que tocan GameType, y GameType pide el Localizer nada
                // mas cargarse. Ver NeoBoot.prepareTexts.
                NeoBoot.prepareTexts();
                // A partir de aqui el hilo de interfaz es el de JavaFX.
                // Application.launch() no vuelve hasta que se cierra la ventana.
                javafx.application.Application.launch(NeoApp.class, args);
                break;
            case "import":
                importDeck(opt(args, "--file", null), opt(args, "--name", null));
                break;
            case "diag":
                diagnoseImages(deckName);
                break;
            case "deckcheck":
                // Las reglas de construccion no se ven en una captura: hay que
                // intentar romperlas para saber que se aplican.
                banner("Reglas del deck builder");
                forge.neo.deck.DeckRulesCheck.run();
                break;
            case "draftcheck":
                // El ciclo de un draft entero (3 sobres x 15 picks, los siete
                // rivales eligiendo a la vez) no se ve en una captura.
                banner("Draft completo, sin ventana");
                forge.neo.draft.DraftCheck.run();
                break;
            case "filtercheck":
                banner("Mana: las fuentes que el motor no ve");
                forge.neo.match.FilterCheck.run();
                break;
            case "preparecheck":
                // Las criaturas que "se preparan": el hechizo NO se lanza
                // desde la criatura, se lanza desde una copia que el motor
                // deja en el EXILIO. Casi todas piden una condicion que no se
                // provoca a voluntad, asi que la mesa se fabrica.
                banner("Criaturas preparadas: donde acaba su hechizo");
                forge.neo.match.PrepareCheck.run();
                break;
            case "manacheck":
                // Que una tierra de dos colores pregunte cual da. La mesa que
                // lo provoca (dos montanyas + una dual, y un coste de {R}{G})
                // no sale cuando quieres en una partida, y el fallo es mudo.
                banner("Mana: que la dual pregunte el color");
                forge.neo.match.ManaCheck.run();
                break;
            case "combatcheck":
                // Las pistas de combate: por que el motor no te deja terminar.
                // Un menace bloqueado con una sola criatura no se provoca a
                // voluntad en una partida, asi que el combate se fabrica.
                banner("Combate: por que no se puede");
                forge.neo.match.CombatCheck.run();
                break;
            case "lookcheck":
                // Trocear la hoja de sprites, copiar lo importado y dejar la
                // musica donde el motor la busca. Nada de eso se ve en una
                // captura: o sale un numero, o no sale.
                banner("Personalizacion: avatares, importar y musica");
                forge.neo.look.LookCheck.run();
                break;
            case "netcheck":
                // Los mazos de internet. Es la UNICA prueba que necesita
                // linea: sin ella avisa y se salta la descarga, en vez de
                // fallar — que es exactamente lo que tiene que hacer la
                // pantalla.
                banner("Mazos de internet");
                forge.neo.deck.NetCheck.run();
                break;
            case "questcheck":
                // El progreso de la aventura y, sobre todo, que los rivales
                // suban contigo. Eso no se ve jugando sin ganar veinte duelos.
                banner("Aventura: progreso y nivel de los rivales");
                forge.neo.quest.QuestCheck.run();
                break;
            case "tournamentcheck":
                // El torneo entero sin ventana: generar rivales, jugar ronda
                // a ronda hasta perder o completarlo, y que se guarde, se
                // recargue y se borre en el momento correcto.
                banner("Torneo: rondas y persistencia");
                forge.neo.tournament.TournamentCheck.run();
                break;
            case "sealedcheck":
                // Un sellado entero sin ventana: que los seis sobres sean seis
                // y no seis copias del mismo, y que el mazo salga jugable.
                banner("Sellado: seis sobres y siete rivales");
                forge.neo.draft.SealedCheck.run();
                break;
            case "lobbycheck":
                // Una partida privada entera: servidor y invitado en el mismo
                // proceso, por localhost. Es la unica forma de comprobar la red
                // desde aqui, y coge lo que una captura no puede: que el mazo
                // del invitado viaje, que le lleguen los deltas y que las dos
                // interfaces contesten.
                banner("Partida privada: anfitrion e invitado");
                forge.neo.net.LobbyCheck.run();
                break;
            case "lobbyguest":
                // El invitado de lobbycheck. No se usa jugando: existe para que
                // la prueba pueda tener las dos partes en procesos distintos,
                // que es la unica forma de que no compartan el hilo de interfaz.
                forge.neo.net.LobbyGuest.run(args);
                break;
            case "tutorialcheck":
                // El tutorial se rompe en silencio: un nombre de carta mal
                // escrito deja la mano vacia, un texto que falta ensenya la
                // clave, y un gesto inventado deja un paso que no se puede
                // terminar. Nada de eso se ve en una captura.
                banner("Tutorial: posiciones, textos y gestos");
                forge.neo.tutorial.TutorialCheck.run();
                break;
            case "ascentcheck":
                // La sonda de la fase 0 de Ascenso. Las cinco cosas sobre las
                // que descansa el modo entero — mazo corto, vida arrastrada,
                // jefe con esquemas, una reliquia nuestra y que esa reliquia no
                // se cuele en el catalogo — fallarian en silencio o muy tarde.
                banner("Ascenso: la sonda de la fase 0");
                forge.neo.ascent.AscentProbe.run();
                // Y el bucle del modo: una run entera, sin ventana. Va detras
                // porque no tiene sentido medir el recorrido si el motor no
                // deja hacer lo que el modo supone — si la sonda falla, revienta
                // aqui mismo y esto no llega a correr.
                banner("Ascenso: una run entera, sin ventana");
                forge.neo.ascent.AscentCheck.run();
                break;
            case "reliccheck":
                // Las 37 reliquias, JUGADAS: una partida por cada una, mirando
                // en la mesa que el efecto ocurre de verdad. Va aparte de
                // ascentcheck porque es lo unico del modo que tarda minutos, y
                // porque lo que caza es de otra naturaleza: no que el modo se
                // recorra, sino que un script mal escrito NO hace nada y Forge
                // no lo dice por ningun sitio.
                banner("Ascenso: las reliquias, jugadas una a una");
                forge.neo.ascent.AscentRelicCheck.run(opt(args, "--solo", null));
                break;
            case "questmake":
                // Una aventura de pruebas con dinero de sobra. La tienda y los
                // drops no se pueden mirar sin creditos, y conseguirlos jugando
                // son veinte duelos.
                banner("Aventura de pruebas");
                forge.neo.quest.QuestSandbox.run(args);
                break;
            default:
                listDecks();
                System.out.println();
                System.out.println("  Comandos: play | watch     (--deck=nombre --opponents=N --timeout=S -v)");
                break;
        }

        System.exit(0);
    }

    // ---------------------------------------------------------------

    /**
     * {@code -Dclave=valor} de la linea de comandos a propiedades de sistema.
     *
     * <p>Sin valor vale "true", que es como se usan las banderas de traza.
     */
    private static void applySystemProperties(final String[] args) {
        for (final String arg : args) {
            if (arg == null || !arg.startsWith("-D") || arg.length() <= 2) {
                continue;
            }
            final String body = arg.substring(2);
            final int eq = body.indexOf('=');
            final String key = eq < 0 ? body : body.substring(0, eq);
            final String value = eq < 0 ? "true" : body.substring(eq + 1);
            if (!key.isEmpty()) {
                System.setProperty(key, value);
            }
        }
    }

    private static void runGame(final String deckName, final int opponents,
                                final NeoMatchUI.Mode mode, final int timeout, final boolean verbose) {

        final Deck deck = deckName == null ? NeoGame.firstCommanderDeck() : NeoGame.commanderDeck(deckName);
        if (deck == null) {
            System.out.println();
            System.out.println("  No se ha encontrado ningun mazo de Commander"
                    + (deckName == null ? "" : " llamado '" + deckName + "'") + ".");
            System.out.println("  Buscados en %APPDATA%\\Forge\\decks\\commander\\");
            return;
        }

        banner("FASE 1 - partida de Commander");
        final long t0 = System.currentTimeMillis();
        final NeoGame.Result r = NeoGame.play(deck, opponents, mode, timeout, verbose);
        final long secs = (System.currentTimeMillis() - t0) / 1000;

        System.out.println();
        if (r.completed) {
            banner("OK - partida completada");
            System.out.printf(Locale.ROOT,
                    "  Turnos: %d | decisiones contestadas: %d | duracion: %ds%n",
                    r.turns, r.decisions, secs);
            if (r.winner != null && !r.winner.isEmpty()) {
                System.out.println("  Ganador: " + r.winner);
            }
            System.out.println();
            System.out.println("  El motor ha jugado una partida entera conducida por NeoMatchUI,");
            System.out.println("  sin Swing. Camino de lectura y de escritura validados.");
            System.out.println("  Siguiente: FASE 2 - ventana JavaFX y render de carta.");
        } else {
            banner("FALLO - la partida no termino");
            System.out.printf(Locale.ROOT, "  Turnos alcanzados: %d | decisiones: %d%n",
                    r.turns, r.decisions);
        }
    }

    /** Quita la extension de un nombre de fichero. */
    private static String stripExtension(final String fileName) {
        final int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Importa una decklist pegada y la guarda como mazo de Commander. */
    private static void importDeck(final String file, final String name) {
        if (file == null) {
            System.out.println("  Uso: import --file=lista.txt [--name=nombre]");
            return;
        }
        final java.io.File f = new java.io.File(file);
        if (!f.exists()) {
            System.out.println("  No existe el fichero: " + f.getAbsolutePath());
            return;
        }
        banner("IMPORTAR MAZO");
        try {
            final String text = forge.neo.deck.DeckImporter.readFile(f);
            final String deckName = name != null ? name : stripExtension(f.getName());

            final var result = forge.neo.deck.DeckImporter.importCommander(text, deckName);
            final Deck deck = result.deck;
            if (deck == null) {
                System.out.println("  El importador no ha devuelto ningun mazo.");
                return;
            }

            System.out.printf(Locale.ROOT, "  Reconocidas: %d cartas%n", result.accepted);
            if (result.unknown > 0) {
                System.out.printf(Locale.ROOT, "  NO reconocidas: %d%n", result.unknown);
                for (final String p : result.problems) {
                    System.out.println("     - " + p);
                }
            }

            System.out.printf(Locale.ROOT, "  Mazo principal: %d%n", deck.getMain().countAll());
            if (deck.has(DeckSection.Commander)) {
                for (final java.util.Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                    System.out.println("  Comandante: " + e.getKey().getName()
                            + " [" + e.getKey().getEdition() + "]");
                }
            } else {
                System.out.println("  AVISO: el mazo no tiene comandante.");
            }

            forge.neo.deck.DeckImporter.save(deck);
            System.out.println();
            System.out.println("  Guardado como '" + deck.getName() + "'.");
        } catch (final Exception e) {
            System.out.println("  Fallo al importar: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Diagnostico de imagenes: para una carta real del mazo, muestra la clave
     * de imagen, si el fichero esta en disco y que pasa al pedir la descarga.
     */
    private static void diagnoseImages(final String deckName) {
        final Deck deck = deckName == null ? NeoGame.firstCommanderDeck() : NeoGame.commanderDeck(deckName);
        if (deck == null) {
            System.out.println("  No hay mazo.");
            return;
        }
        banner("DIAGNOSTICO DE IMAGENES");
        System.out.println("  CACHE_CARD_PICS_DIR = "
                + forge.localinstance.properties.ForgeConstants.CACHE_CARD_PICS_DIR);
        System.out.println();

        int n = 0;
        for (final java.util.Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            final PaperCard pc = e.getKey();
            final forge.game.card.CardView cv = forge.game.card.CardView.getCardForUi(pc);
            final String key = cv.getCurrentState().getImageKey();
            final java.io.File f = forge.ImageKeys.getImageFile(key);

            System.out.println("  " + pc.getName() + " [" + pc.getEdition() + "]");
            System.out.println("     clave  : " + key);
            System.out.println("     fichero: " + (f == null ? "(no encontrado en disco)" : f.getAbsolutePath()));
            System.out.println("     hasImage: " + forge.ImageKeys.hasImage(pc));

            if (f == null) {
                final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
                // fetchImage exige el hilo de interfaz (assertExecutedByEdt).
                GuiBase.getInterface().invokeInEdtLater(
                        () -> GuiBase.getInterface().getImageFetcher().fetchImage(key, done::countDown));
                try {
                    final boolean ok = done.await(25, java.util.concurrent.TimeUnit.SECONDS);
                    forge.ImageKeys.clearMissingCards();
                    final java.io.File after = forge.ImageKeys.getImageFile(key);
                    System.out.println("     descarga: " + (ok ? "callback OK" : "TIMEOUT")
                            + " | ahora en disco: " + (after != null));
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println();
            if (++n >= 3) {
                break;
            }
        }
    }

    private static void listDecks() {
        System.out.println();
        System.out.println("  Mazos de Commander:");
        int count = 0;
        for (final Deck deck : FModel.getDecks().getCommander()) {
            count++;
            System.out.println();
            System.out.println("  -- " + deck.getName() + "  (" + deck.getMain().countAll() + " cartas)");
            if (deck.has(DeckSection.Commander)) {
                for (final java.util.Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
                    final PaperCard c = e.getKey();
                    System.out.println("     Comandante: " + c.getName() + " [" + c.getEdition() + "]"
                            + "  " + c.getRules().getManaCost());
                }
            }
        }
        if (count == 0) {
            System.out.println("     (ninguno)");
        }
    }

    /**
     * Si esta ejecucion tiene que ser la unica.
     *
     * <p>Lo normal es que si. Las dos excepciones son de verdad excepciones:
     *
     * <ul>
     *   <li><b>Las pruebas de partida privada</b> ({@code --lobby} y
     *       {@code --lobby-join}) necesitan dos ventanas en el mismo
     *       ordenador — anfitrion e invitado — y sin esto la segunda se
     *       cerraria sola. El fallo seria ademas de los que no se entienden:
     *       una prueba de red que no arranca sin decir nada.</li>
     *   <li>{@code -Dneo.singleInstance=false}, la valvula de escape de
     *       siempre para cuando haga falta abrir dos a proposito.</li>
     * </ul>
     */
    private static boolean wantsSingleInstance(final String[] args) {
        if ("false".equalsIgnoreCase(System.getProperty("neo.singleInstance"))) {
            return false;
        }
        for (final String a : args) {
            if (a.equals("--lobby") || a.startsWith("--lobby-join")) {
                return false;
            }
        }
        return true;
    }

    private static boolean has(final String[] args, final String flag) {
        for (final String a : args) {
            if (a.equals(flag)) {
                return true;
            }
        }
        return false;
    }

    private static String opt(final String[] args, final String key, final String def) {
        for (final String a : args) {
            if (a.startsWith(key + "=")) {
                return a.substring(key.length() + 1);
            }
        }
        return def;
    }

    private static void banner(final String text) {
        final String line = "=".repeat(Math.max(60, text.length() + 4));
        System.out.println();
        System.out.println(line);
        System.out.println("  " + text);
        System.out.println(line);
    }
}
