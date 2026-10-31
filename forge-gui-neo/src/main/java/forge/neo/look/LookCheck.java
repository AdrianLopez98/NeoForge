package forge.neo.look;

import java.io.File;
import java.util.List;
import java.util.Locale;

import forge.sound.MusicPlaylist;

/**
 * Comprueba la personalizacion sin ventana.
 *
 * <p>Lo que hay aqui <b>no se ve en una captura</b>: cuantos avatares salen de
 * trocear la hoja de Forge, que importar copia el fichero de verdad y no se
 * queda con la ruta, que marcar una cancion la pone donde el motor la busca, y
 * que nada de esto escribe fuera de nuestra carpeta.
 *
 * <p>Se ejecuta con {@code run.cmd lookcheck}.
 */
public final class LookCheck {

    private LookCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        spritesAreSliced();
        listsMixBothOrigins();
        importCopiesTheFile();
        musicGoesWhereTheEngineLooks();
        importedMusicIsKept();
        aiNamesAreRealAndStable();
        rivalsPlayTheirOwnWay();
        aiProfilesComeAndGo();
        deckCarriesItsOwnSleeve();
        hoverZoomBehaves();

        System.out.println();
        System.out.printf(Locale.ROOT, "  %d comprobaciones OK, %d fallos%n", passed, failed);
        if (failed > 0) {
            System.out.println("  *** HAY FALLOS ***");
        }
    }

    // ---------------------------------------------------------------

    /**
     * El aumento al pasar el raton: que respete el tope y que <b>por defecto no
     * cambie nada</b>.
     *
     * <h2>Que se comprueba y por que</h2>
     *
     * <p>Pedido por un jugador el 22-09-2026 (<i>"an option to enlarge the card
     * a bit on mouse over"</i>). Lo que hay que garantizar no es que ampliar
     * funcione —eso es un {@code setScale}— sino las dos cosas que se rompen en
     * silencio:
     *
     * <ol>
     *   <li><b>Que a quien no toque el ajuste no le cambie la mesa.</b> Con el
     *       valor de siempre (108 %) la carta tiene que subir <b>exactamente</b>
     *       el {@code 0,12 x ancho} que estaba escrito a mano. Un decimal de
     *       mas aqui y la mano se mueve un poco para todo el mundo, sin que
     *       nadie sepa por que.</li>
     *   <li><b>Que el tope se aplique.</b> Sin el, un numero grande en las
     *       preferencias convierte el hover en el zoom del clic derecho, y
     *       entonces son dos gestos para lo mismo.</li>
     * </ol>
     *
     * <p>Sin ventana: es aritmetica, y comprobarla jugando costaria una partida
     * y no daria mas certeza.
     *
     * <h2>Lo de fabrica se le pregunta a la CONSTANTE, no a {@code hoverZoom()}</h2>
     *
     * <p>Parece lo mismo y no lo es. Sin la bandera {@code -Dneo.hoverZoom},
     * {@code NeoSettings.hoverZoom()} hace {@code getInt(HOVER_ZOOM, ...)}: lee
     * <b>el ajuste guardado de quien esta ejecutando la prueba</b>. O sea que
     * la comprobacion titulada "de fabrica" no miraba lo de fabrica — miraba el
     * hover de esta maquina, y se ponia en rojo en cuanto alguien lo cambiaba
     * en Ajustes, que es justo para lo que existe el ajuste.
     *
     * <p>Paso el 22-09-2026 con el hover al maximo: <i>"hover: de fabrica
     * aumenta un 50 %"</i>, toda la bateria en rojo por una preferencia. Y un
     * rojo que depende de como tengas configurado el juego es peor que no tener
     * la prueba: se deja de mirar, y con el se dejan de mirar los otros 27.
     *
     * <p>Por el camino de las preferencias si se comprueba lo unico que vale
     * para cualquier ajuste: que lo que salga de ahi este DENTRO del tope y el
     * suelo. Eso caza un recorte roto sin depender de lo que tenga guardado
     * nadie.
     */
    private static void hoverZoomBehaves() {
        final String antes = System.getProperty("neo.hoverZoom");
        try {
            System.clearProperty("neo.hoverZoom");
            final int fabrica = forge.neo.NeoSettings.HOVER_ZOOM_DEFAULT;
            check("hover: de fabrica aumenta un " + (fabrica - 100) + " % (el de siempre)",
                    fabrica == 108);

            // Y por el camino de las preferencias, lo que vale sea cual sea el
            // ajuste de quien pasa la prueba: que el recorte se aplique.
            final double guardado = forge.neo.NeoSettings.hoverZoom();
            check("hover: lo que hay guardado sale recortado entre 100 % y 150 % (aqui, "
                    + Math.round(guardado * 100) + " %)",
                    guardado >= 1.0 - 1e-9 && guardado <= 1.5 + 1e-9);

            // La cuenta del levantamiento, con el valor de siempre, tiene que
            // dar el 0,12 x ancho de antes. Ese es el "no he cambiado nada".
            final double w = 100.0;
            check("hover: con el aumento de siempre la carta sube lo mismo que antes",
                    Math.abs(forge.neo.card.CardNode.hoverLiftFor(1.08, w) - w * 0.12) < 1e-9);
            check("hover: al ampliar mas, sube mas (si no, la mano se saldria por abajo)",
                    forge.neo.card.CardNode.hoverLiftFor(1.35, w)
                            > forge.neo.card.CardNode.hoverLiftFor(1.08, w));

            System.setProperty("neo.hoverZoom", "400");
            check("hover: el tope se aplica (400 pedido -> 150)",
                    Math.abs(forge.neo.NeoSettings.hoverZoom() - 1.50) < 1e-9);
            System.setProperty("neo.hoverZoom", "10");
            check("hover: el suelo se aplica (10 pedido -> 100, la carta nunca encoge)",
                    Math.abs(forge.neo.NeoSettings.hoverZoom() - 1.00) < 1e-9);
            System.setProperty("neo.hoverZoom", "no-es-un-numero");
            // Contra la constante, para que este y el de arriba no puedan
            // separarse el dia que se cambie el valor de fabrica.
            check("hover: un valor con basura cae en el de siempre, no revienta",
                    Math.abs(forge.neo.NeoSettings.hoverZoom() - fabrica / 100.0) < 1e-9);
        } finally {
            if (antes == null) {
                System.clearProperty("neo.hoverZoom");
            } else {
                System.setProperty("neo.hoverZoom", antes);
            }
        }
    }

    /**
     * La hoja de sprites se trocea y salen muchos.
     *
     * <p>Es lo primero que puede fallar sin decir nada: si el recorte diera
     * cero, la pantalla saldria vacia y pareceria que "no hay avatares".
     */
    private static void spritesAreSliced() {
        final int avatars = NeoLook.builtInAvatarCount();
        final int sleeves = NeoLook.builtInSleeveCount();
        System.out.printf(Locale.ROOT, "        (%d avatares, %d fundas de fabrica)%n",
                avatars, sleeves);
        check("Avatares: la hoja se trocea y salen mas de cien", avatars > 100);
        check("Fundas: la hoja se trocea", sleeves > 10);

        // Cada uno tiene que poder identificarse: es lo que se guarda en los
        // ajustes y lo que se busca al arrancar.
        check("Avatares: el primero tiene identificador",
                avatars > 0 && NeoLook.builtInAvatar(0).getId().startsWith("sprite:avatar:"));
        check("Avatares: el indice del motor da la vuelta sin romperse",
                avatars > 0 && NeoLook.builtInAvatar(9999) != null);

        // Que se PINTEN no se puede comprobar aqui: construir una imagen de
        // JavaFX sin ventana lanza "Internal graphics not initialized yet".
        // Eso se ve en la captura de la pantalla, no en este comprobador.
    }

    /**
     * La funda del mazo: se guarda en el, se relee y se puede quitar.
     *
     * <p>Se comprueba <b>por el .dck</b>, no solo en memoria: vive en una
     * etiqueta ({@code Tags=} del fichero), y lo que hay que asegurar es que
     * sobrevive al viaje de ida y vuelta. Si no, la funda se pondria, se veria
     * puesta, y estaria perdida la proxima vez que se abriera el mazo — sin
     * ningun aviso.
     */
    private static void deckCarriesItsOwnSleeve() {
        final forge.deck.Deck deck = new forge.deck.Deck("comprobacion de fundas");
        check("Funda del mazo: de fabrica no lleva ninguna",
                NeoLook.deckSleeveId(deck) == null);

        NeoLook.setDeckSleeve(deck, "sprite:sleeve:7");
        check("Funda del mazo: se le pone", "sprite:sleeve:7".equals(NeoLook.deckSleeveId(deck)));

        // Dos fundas en el mismo mazo no significan nada: la segunda releva.
        NeoLook.setDeckSleeve(deck, "sprite:sleeve:3");
        check("Funda del mazo: la nueva releva a la anterior",
                "sprite:sleeve:3".equals(NeoLook.deckSleeveId(deck)));

        // El viaje de ida y vuelta por el .dck de verdad.
        try {
            final java.io.File tmp = java.io.File.createTempFile("neo-fundas", ".dck");
            tmp.deleteOnExit();
            forge.deck.io.DeckSerializer.writeDeck(deck, tmp);
            final forge.deck.Deck reloaded = forge.deck.io.DeckSerializer.fromFile(tmp);
            check("Funda del mazo: sobrevive a guardar y volver a leer el .dck",
                    reloaded != null && "sprite:sleeve:3".equals(NeoLook.deckSleeveId(reloaded)));
            tmp.delete();
        } catch (final java.io.IOException e) {
            check("Funda del mazo: sobrevive a guardar y volver a leer el .dck (" + e + ")", false);
        }

        // Y lo que de verdad se ve en la mesa: mientras se juega con ese mazo
        // manda la suya, y en cuanto se acaba vuelve a mandar la de
        // Personalizar. Es lo que hace TableBinder.sleeveOf.
        NeoLook.setDeckInPlay(deck);
        check("Funda en juego: manda la del mazo",
                "sprite:sleeve:3".equals(NeoLook.sleeveInPlay().getId()));
        NeoLook.setDeckInPlay(null);
        check("Funda en juego: sin mazo puesto, la tuya",
                NeoLook.currentSleeve().getId().equals(NeoLook.sleeveInPlay().getId()));

        NeoLook.setDeckSleeve(deck, null);
        check("Funda del mazo: se le puede quitar", NeoLook.deckSleeveId(deck) == null);
        NeoLook.setDeckInPlay(deck);
        check("Funda en juego: un mazo sin funda propia usa la tuya",
                NeoLook.currentSleeve().getId().equals(NeoLook.sleeveInPlay().getId()));
        NeoLook.setDeckInPlay(null);

        // Y una importada, que es la que puede llevar caracteres raros: el
        // separador de etiquetas es la COMA, y por eso freeName() se las quita
        // al nombre del fichero.
        check("Funda importada: el nombre de fichero no puede traer comas",
                !NeoLook.freeName(NeoLook.sleevesDir(), "una,funda.png").getName().contains(","));
    }

    /** Las listas mezclan lo de Forge con lo tuyo, y el tapete trae su "ninguno". */
    private static void listsMixBothOrigins() {
        final List<LookItem> playmats = NeoLook.playmats();
        check("Tapetes: el primero es \"ninguno\"",
                !playmats.isEmpty() && playmats.get(0).isNone());
        check("Tapetes: ademas hay fondos del skin", playmats.size() > 1);

        boolean anyFile = false;
        for (final LookItem p : playmats) {
            if (p.getFile() != null && p.getFile().isFile()) {
                anyFile = true;
                break;
            }
        }
        check("Tapetes: los del skin existen en disco", anyFile);
    }

    /**
     * Importar COPIA. Si guardara la ruta, mover el fichero romperia el avatar.
     */
    private static void importCopiesTheFile() {
        File temp = null;
        LookItem brought = null;
        try {
            // Se usa una imagen que ya trae Forge como si fuera "un PNG del
            // jugador": lo que se comprueba es la copia, no el contenido.
            final File source = new File(
                    forge.localinstance.properties.ForgeConstants.DEFAULT_SKINS_DIR, "bg_texture.jpg");
            if (!source.isFile()) {
                System.out.println("        (sin fichero de origen, me salto la prueba)");
                return;
            }
            temp = File.createTempFile("neo-look-", ".jpg");
            java.nio.file.Files.copy(source.toPath(), temp.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            brought = NeoLook.importInto(NeoLook.avatarsDir(), temp);
            check("Importar: devuelve algo", brought != null);
            if (brought == null) {
                return;
            }
            check("Importar: el fichero esta en NUESTRA carpeta",
                    brought.getFile() != null && brought.getFile().isFile()
                            && brought.getFile().getParentFile().equals(NeoLook.avatarsDir()));
            check("Importar: y no es el original",
                    !brought.getFile().getAbsolutePath().equals(temp.getAbsolutePath()));

            // Y borrar el original no puede dejar el avatar roto.
            check("Importar: sobrevive a que borres el original", temp.delete() && brought.getFile().isFile());

            boolean listed = false;
            for (final LookItem a : NeoLook.avatars()) {
                if (a.getId().equals(brought.getId())) {
                    listed = true;
                    break;
                }
            }
            check("Importar: sale en la lista", listed);
            check("Importar: y lo importado se puede borrar", NeoLook.delete(brought));
        } catch (final Exception e) {
            System.out.println("        (fallo inesperado: " + e + ")");
            check("Importar: sin excepciones", false);
        } finally {
            if (temp != null && temp.exists()) {
                temp.delete();
            }
            if (brought != null && brought.getFile() != null && brought.getFile().exists()) {
                brought.getFile().delete();
            }
        }
    }

    /**
     * Marcar una cancion la deja donde el motor la busca.
     *
     * <p>Y desmarcarla la quita. Es lo que hace que "si no eliges nada suenan
     * las de fabrica" salga gratis, sin una sola linea de reproductor.
     */
    private static void musicGoesWhereTheEngineLooks() {
        final List<NeoMusic.Track> match = NeoMusic.tracks(MusicPlaylist.MATCH);
        System.out.printf(Locale.ROOT, "        (%d pistas de partida)%n", match.size());
        check("Musica: hay pistas de partida", !match.isEmpty());
        if (match.isEmpty()) {
            return;
        }

        final NeoMusic.Track first = match.get(0);
        final boolean was = first.isEnabled();
        try {
            check("Musica: se puede marcar", NeoMusic.setEnabled(first, true));
            final File mine = new File(NeoMusic.dir(MusicPlaylist.MATCH), first.getFileName());
            check("Musica: y aparece donde el motor la busca", mine.isFile());

            // Marcar una de FORGE no la convierte en tuya.
            //
            // Se daba por tuya cualquier cosa que estuviera en nuestra
            // carpeta, y ahi acaban tambien las de Forge en cuanto las marcas.
            // Resultado: una pista de fabrica marcada salia con una "x" de
            // borrar al lado que hacia lo mismo que desmarcarla.
            if (!first.isImported()) {
                final NeoMusic.Track marked = byName(MusicPlaylist.MATCH, first.getFileName());
                check("Musica: marcar una de Forge no la hace tuya",
                        marked != null && !marked.isImported());
            }

            check("Musica: se puede desmarcar", NeoMusic.setEnabled(first, false));
            check("Musica: y desaparece de ahi", !mine.isFile());
            check("Musica: la de Forge sigue estando",
                    first.isImported() || NeoMusic.tracks(MusicPlaylist.MATCH).size() == match.size());

            musicDoesNotLieWhenItCannotDelete(first, mine);
        } finally {
            NeoMusic.setEnabled(first, was);
        }
    }

    /** La pista de esa lista que se llame asi. */
    private static NeoMusic.Track byName(final MusicPlaylist list, final String fileName) {
        for (final NeoMusic.Track t : NeoMusic.tracks(list)) {
            if (t.getFileName().equals(fileName)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Si no se puede desmarcar, se dice. No se hace como que si.
     *
     * <p>El fallo que reporto el jugador: desmarcar es borrar nuestra copia, y
     * en Windows <b>un fichero abierto no se puede borrar</b>. La pista que
     * suena la tiene abierta el reproductor, asi que el borrado fallaba, la
     * pantalla se repintaba igual y el boton seguia diciendo "Suena" sin
     * explicar nada. Con una sola pista marcada, la unica que querias apagar
     * era justo la unica que no se podia.
     *
     * <p>Ahora {@code NeoMusic} suelta la musica y reintenta; y si aun asi no
     * puede — otro programa tiene el fichero — devuelve {@code false} y la
     * pantalla lo dice. Esto comprueba lo segundo, que es lo que no se puede
     * dejar al azar: <b>mentir es peor que fallar</b>.
     */
    private static void musicDoesNotLieWhenItCannotDelete(final NeoMusic.Track track,
                                                          final File mine) {
        NeoMusic.setEnabled(track, true);
        if (!mine.isFile()) {
            return;
        }
        boolean reported = false;
        boolean stillThere = false;
        try (java.io.FileOutputStream held = new java.io.FileOutputStream(mine, true)) {
            // Con el fichero abierto, Windows no deja borrarlo.
            reported = !NeoMusic.setEnabled(track, false);
            stillThere = mine.isFile();
        } catch (final java.io.IOException e) {
            System.out.println("        (no se ha podido simular el bloqueo: " + e + ")");
            return;
        }
        if (!stillThere) {
            // En un sistema que si deja borrar un fichero abierto (Linux) no
            // hay nada que comprobar: el borrado funciona y ya esta.
            System.out.println("        (aqui un fichero abierto SI se puede borrar)");
            return;
        }
        check("Musica: si no se puede borrar, se dice", reported);
    }

    /**
     * Tu musica se queda: importar copia, y desmarcar NO la destruye.
     *
     * <p>Antes lo que importabas se copiaba solo a la carpeta del motor — la
     * que dice "esto suena ahora" — y desmarcar es borrar de ahi. O sea que el
     * mismo boton que apaga una cancion de Forge (reversible: la original sigue
     * en {@code res/music}) <b>borraba para siempre la tuya</b>, sin avisar y
     * sin vuelta atras.
     *
     * <p>Ahora importar deja dos copias: una en tu biblioteca, permanente, y
     * otra en la del motor para que suene ya. Esto comprueba las tres cosas que
     * lo sostienen: que suena de entrada, que apagarla la conserva, y que lo
     * unico que la borra de verdad es borrarla.
     */
    private static void importedMusicIsKept() {
        final MusicPlaylist list = MusicPlaylist.MATCH;
        final List<NeoMusic.Track> stock = NeoMusic.tracks(list);
        if (stock.isEmpty()) {
            System.out.println("        (sin musica de fabrica, me salto la prueba)");
            return;
        }

        File temp = null;
        NeoMusic.Track brought = null;
        try {
            // Se usa una pista de Forge como si fuera "un mp3 del jugador":
            // lo que se comprueba es el trasiego de ficheros, no el sonido.
            temp = File.createTempFile("neo-music-", ".mp3");
            java.nio.file.Files.copy(stock.get(0).getSource().toPath(), temp.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            brought = NeoMusic.importTrack(list, temp);
            check("Musica importada: devuelve algo", brought != null);
            if (brought == null) {
                return;
            }
            final File mine = new File(NeoMusic.libraryDir(list), brought.getFileName());
            final File playing = new File(NeoMusic.dir(list), brought.getFileName());

            check("Musica importada: se guarda en tu biblioteca", mine.isFile());
            check("Musica importada: y suena desde ya", playing.isFile());
            check("Musica importada: sobrevive a que borres el original",
                    temp.delete() && mine.isFile());
            check("Musica importada: sale como tuya", byName(list, brought.getFileName()) != null
                    && byName(list, brought.getFileName()).isImported());

            // LO IMPORTANTE: apagarla no puede destruirla.
            check("Musica importada: se puede apagar", NeoMusic.setEnabled(brought, false));
            check("Musica importada: y apagarla NO la borra", mine.isFile());
            final NeoMusic.Track off = byName(list, brought.getFileName());
            check("Musica importada: sigue en la lista tras apagarla",
                    off != null && off.isImported() && !off.isEnabled());

            check("Musica importada: se vuelve a encender",
                    NeoMusic.setEnabled(brought, true) && playing.isFile());

            check("Musica importada: borrarla la quita de verdad", NeoMusic.delete(brought));
            check("Musica importada: de los dos sitios", !mine.isFile() && !playing.isFile());
        } catch (final Exception e) {
            System.out.println("        (fallo inesperado: " + e + ")");
            check("Musica importada: sin excepciones", false);
        } finally {
            if (temp != null) {
                temp.delete();
            }
            if (brought != null) {
                NeoMusic.delete(brought);
            }
        }
    }

    /**
     * Los rivales tienen nombre de verdad, y el mismo la proxima vez.
     *
     * <p>Sortearlo en cada partida haria que "el rival de siempre" fuera otro
     * cada vez, que es justo lo contrario de lo que se busca.
     */
    private static void aiNamesAreRealAndStable() {
        final List<String> before = NeoLook.aiNames();
        try {
            NeoLook.setAiNames(List.of());
            final String first = NeoPlayers.aiName(0);
            System.out.printf(Locale.ROOT, "        (el rival 0 se llama \"%s\")%n", first);
            check("Rivales: tienen nombre", first != null && !first.isBlank());
            check("Rivales: y no es \"IA-1\"", !"IA-1".equals(first));
            check("Rivales: el mismo la proxima vez", first.equals(NeoPlayers.aiName(0)));
            check("Rivales: y cada uno el suyo", !first.equals(NeoPlayers.aiName(1)));
        } finally {
            NeoLook.setAiNames(before);
        }
    }

    /**
     * Cada rival con su cara y su forma de jugar ({@link RivalSetup}).
     *
     * <p>Pedido en Discord el 02-10-2026. Lo que se rompe en silencio no es la
     * pantalla, es lo que llega al motor: que el rival 2 juegue de verdad con
     * el perfil que le pusiste, que "Al azar" no se salga del pozo, que un modo
     * que pasa su perfil, como Ascenso, <b>siga mandando</b> (su dificultad
     * depende de el; Quest y la Aventura ni pasan por aqui) y que una cara
     * importada no le llegue al motor como un
     * numero de la hoja que no es. Por eso se mira el {@code LobbyPlayerAi}
     * que sale de {@code NeoPlayers.ai}, que es lo que recibe la partida.
     *
     * <p>Toca los ajustes del jugador: se guardan antes y se reponen siempre.
     */
    private static void rivalsPlayTheirOwnWay() {
        final String[] keys = {RivalSetup.NAMES, RivalSetup.MODES, RivalSetup.AVATARS, RivalSetup.POOL};
        final String[] before = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            before[i] = forge.neo.NeoSettings.get(keys[i], null);
        }
        try {
            for (final String k : keys) {
                forge.neo.NeoSettings.set(k, null);
            }
            final java.util.Random rnd = new java.util.Random(7);
            final String normal = RivalSetup.perRival("Default");

            check("Rivales/IA: sin elegir nada, la general",
                    "Default".equals(RivalSetup.resolve(normal, 2, rnd)));
            check("Rivales/IA: y la general vacia sigue siendo la de Forge",
                    "".equals(RivalSetup.resolve(RivalSetup.perRival(""), 0, rnd)));

            RivalSetup.setMode(1, "Cautious");
            RivalSetup.setMode(3, "Reckless");
            check("Rivales/IA: el rival 2 juega con la suya",
                    "Cautious".equals(RivalSetup.resolve(normal, 1, rnd)));
            check("Rivales/IA: el 4 con la suya", "Reckless".equals(RivalSetup.resolve(normal, 3, rnd)));
            check("Rivales/IA: y el 1, sin elegir, con la general",
                    "Default".equals(RivalSetup.resolve(normal, 0, rnd)));
            check("Rivales/IA: fuera de una partida normal manda el perfil del modo (Ascenso)",
                    "Experimental".equals(RivalSetup.resolve("Experimental", 1, rnd)));

            RivalSetup.setPool(java.util.Arrays.asList("Cautious", "Experimental", "Basura"));
            check("Rivales/IA: el pozo se queda con lo que existe",
                    RivalSetup.pool().equals(java.util.Arrays.asList("Cautious", "Experimental")));
            RivalSetup.setMode(2, RivalSetup.RANDOM);
            final java.util.Set<String> own = new java.util.TreeSet<>();
            final java.util.Set<String> general = new java.util.TreeSet<>();
            for (int i = 0; i < 300; i++) {
                own.add(RivalSetup.resolve(normal, 2, rnd));
                general.add(RivalSetup.resolve(RivalSetup.perRival(RivalSetup.RANDOM), 0, rnd));
            }
            final java.util.Set<String> expected = new java.util.TreeSet<>(RivalSetup.pool());
            System.out.printf(Locale.ROOT, "        (al azar salen %s y %s)%n", own, general);
            check("Rivales/IA: al azar, todos los del pozo y solo esos", own.equals(expected));
            check("Rivales/IA: y la general al azar, igual", general.equals(expected));

            RivalSetup.setPool(List.of());
            check("Rivales/IA: un pozo vacio son los cuatro", RivalSetup.pool().size() == 4);

            forge.neo.NeoSettings.set(RivalSetup.MODES, "Foo|Cautious");
            check("Rivales/IA: un modo que no existe no se cuela",
                    "".equals(RivalSetup.mode(0)) && "Cautious".equals(RivalSetup.mode(1)));

            check("Rivales/cara: el numero de la hoja",
                    RivalSetup.spriteIndexOf("sprite:avatar:12") == 12
                            && RivalSetup.spriteIndexOf("file:mia.png") == -1
                            && RivalSetup.spriteIndexOf("") == -1
                            && RivalSetup.spriteIndexOf(null) == -1
                            && RivalSetup.spriteIndexOf("sprite:avatar:x") == -1);

            // Lo que recibe la partida.
            NeoLook.setAiNames(java.util.Arrays.asList("Uno", "Dos", "Tres", "Cuatro"));
            forge.neo.NeoSettings.set(RivalSetup.MODES, null);
            RivalSetup.setMode(1, "Cautious");
            RivalSetup.setAvatar(1, "sprite:avatar:7");
            final forge.ai.LobbyPlayerAi dos = (forge.ai.LobbyPlayerAi) NeoPlayers.ai(1, normal);
            final forge.ai.LobbyPlayerAi uno = (forge.ai.LobbyPlayerAi) NeoPlayers.ai(0, normal);
            check("Rivales/partida: el rival 2 se sienta con su nombre, su IA y su cara",
                    "Dos".equals(dos.getName()) && "Cautious".equals(dos.getAiProfile())
                            && dos.getAvatarIndex() == 7 && NeoPlayers.customFaceOf("Dos") == null);
            check("Rivales/partida: el 1, sin elegir nada, como siempre",
                    "Default".equals(uno.getAiProfile()) && uno.getAvatarIndex() == 0);
            final forge.ai.LobbyPlayerAi quest = (forge.ai.LobbyPlayerAi) NeoPlayers.ai(1, "Reckless");
            check("Rivales/partida: un modo que pasa su perfil (Ascenso) manda, y la cara sigue",
                    "Reckless".equals(quest.getAiProfile()) && quest.getAvatarIndex() == 7);

            RivalSetup.setAvatar(2, "file:mia.png");
            final forge.ai.LobbyPlayerAi tres = (forge.ai.LobbyPlayerAi) NeoPlayers.ai(2, normal);
            check("Rivales/partida: una cara importada la pinta la mesa, no el motor",
                    "file:mia.png".equals(NeoPlayers.customFaceOf("Tres")) && tres.getAvatarIndex() == 2);
            // Dos rivales con el mismo nombre, uno con cara y otro sin ella:
            // sentar al segundo no puede borrarle la cara al primero.
            NeoLook.setAiNames(java.util.Arrays.asList("Uno", "Dos", "Tres", "Tres"));
            NeoPlayers.ai(3, normal);
            check("Rivales/partida: un tocayo sin cara no le quita la suya",
                    "file:mia.png".equals(NeoPlayers.customFaceOf("Tres")));
            // Lo que la vacia es montar otra partida (NeoGame.play).
            RivalSetup.setAvatar(2, "");
            NeoPlayers.newMatch();
            NeoPlayers.ai(2, normal);
            check("Rivales/partida: y en la partida siguiente, sin ella, se olvida",
                    NeoPlayers.customFaceOf("Tres") == null);

            // Como se llaman en la partida (Discord, 30-10-2026): el de su
            // comandante o uno al azar. Por la bandera, sin tocar preferencias.
            final forge.deck.Deck krenko = new forge.deck.Deck("prueba");
            krenko.getOrCreate(forge.deck.DeckSection.Commander).add(
                    forge.model.FModel.getMagicDb().getCommonCards().getCard("Krenko, Mob Boss"));
            try {
                System.setProperty("neo.aiNames", "commander");
                NeoPlayers.newMatch();
                final String k1 = NeoPlayers.ai(0, normal, krenko).getName();
                final String k2 = NeoPlayers.ai(1, normal, krenko).getName();
                final String none = NeoPlayers.ai(2, normal, new forge.deck.Deck("sin")).getName();
                System.out.printf(java.util.Locale.ROOT, "        (comandante: %s / %s / %s)%n", k1, k2, none);
                check("Rivales/nombre: el de su comandante, corto", !k1.contains(",") && k1.length() > 0
                        && forge.util.CardTranslation.getTranslatedName("Krenko, Mob Boss").startsWith(k1));
                check("Rivales/nombre: dos con el mismo comandante no se llaman igual", !k1.equals(k2));
                check("Rivales/nombre: sin comandante, el de Personalizar", "Tres".equals(none));
                System.setProperty("neo.aiNames", "random");
                NeoPlayers.newMatch();
                final String r = NeoPlayers.ai(0, normal, krenko).getName();
                check("Rivales/nombre: al azar no toca el guardado",
                        !r.isBlank() && "Uno".equals(NeoPlayers.aiName(0)));
                System.setProperty("neo.aiNames", "saved");
                NeoPlayers.newMatch();
                check("Rivales/nombre: de fabrica, el de siempre",
                        "Uno".equals(NeoPlayers.ai(0, normal, krenko).getName()));
            } finally {
                System.clearProperty("neo.aiNames");
            }
        } finally {
            for (int i = 0; i < keys.length; i++) {
                forge.neo.NeoSettings.set(keys[i], before[i]);
            }
            forge.neo.NeoSettings.save();
        }
    }

    /**
     * Los perfiles de IA que trae el jugador ({@link AiProfiles}). Pedido en
     * Discord el 14-10-2026: un {@code .ai} propio "no aparece en la lista".
     *
     * <p>Lo que se mira es lo que no se ve en el menu: que el MOTOR lo tenga
     * cargado con sus valores (un nombre en un boton no prueba nada), que los
     * de fabrica sigan intactos, y que un fichero con basura no tumbe la carga
     * — Forge revienta con un ajuste que no existe, y si eso pasa al arrancar
     * el juego no abre.
     *
     * <p>Corre sobre los datos de quien la lance (la bateria le da los suyos
     * propios): solo crea perfiles que empiezan por "NeoCheck" y los quita al
     * acabar, y devuelve los ajustes que toca.
     */
    private static void aiProfilesComeAndGo() {
        final String profileBefore = forge.neo.NeoSettings.get(forge.neo.NeoSettings.AI_PROFILE, null);
        final String poolBefore = forge.neo.NeoSettings.get(RivalSetup.POOL, null);
        final List<File> temps = new java.util.ArrayList<>();
        final File broken = new File(AiProfiles.userDir(), "NeoCheck roto.ai");
        // Los nombres que la prueba crea sin el prefijo "NeoCheck" (los de Forge
        // numerados). Solo se quitan al acabar los que NO tuviera ya el jugador:
        // "Default 2" es justo como se llama su Default.ai retocado.
        final List<String> ours = new java.util.ArrayList<>();
        try {
            // 1. Lo de fabrica pasa el filtro entero: si no, el filtro es mas
            //    estricto que el motor y le quitaria ajustes buenos a la gente.
            boolean factoryClean = true;
            final File[] factory = new File(forge.localinstance.properties.ForgeConstants.AI_PROFILE_DIR)
                    .listFiles((d, n) -> n.endsWith(".ai"));
            for (final File f : factory == null ? new File[0] : factory) {
                final AiProfiles.Parsed p = AiProfiles.parse(java.nio.file.Files.readAllLines(f.toPath()));
                if (!p.skipped.isEmpty() || p.lines.size() < 50) {
                    System.out.println("        " + f.getName() + ": " + p.lines.size() + " validas, saltadas "
                            + p.skipped);
                    factoryClean = false;
                }
            }
            check("IA propia: los perfiles de fabrica pasan el filtro sin perder una linea",
                    factory != null && factory.length >= 4 && factoryClean);

            // 2. Limpiar: espacios, BOM, repetidos; saltar lo que reventaria.
            final List<String> raw = java.util.Arrays.asList("﻿# mi IA", "MULLIGAN_THRESHOLD = 2 ",
                    "PLAY_AGGRO=true", "AJUSTE_QUE_NO_EXISTE=3", "CHANCE_TO_ATTACK_INTO_TRADE=mucho",
                    "PLAY_AGGRO=false", "una linea sin igual", "MOVE_EQUIPMENT_TO_BETTER_CREATURES=never");
            final AiProfiles.Parsed parsed = AiProfiles.parse(raw);
            check("IA propia: se queda con lo que vale, limpio y con el ultimo de los repetidos",
                    parsed.lines.equals(java.util.Arrays.asList("MULLIGAN_THRESHOLD=2", "PLAY_AGGRO=false",
                            "MOVE_EQUIPMENT_TO_BETTER_CREATURES=never")));
            check("IA propia: y salta lo que haria reventar al motor (clave rara, numero que no es)",
                    parsed.skipped.size() == 3);

            // 3. Importar: el motor lo tiene, con SUS valores.
            final File src = tempProfile(temps, "NeoCheck Pabi.txt", raw);
            final AiProfiles.Imported imp = AiProfiles.importFile(src);
            final String name = "NeoCheck Pabi";
            check("IA propia: importar le da el nombre del fichero y cuenta lo que ha saltado",
                    name.equals(imp.name) && imp.settings == 3 && imp.skipped.size() == 3);
            check("IA propia: el motor la tiene, junto a las de fabrica",
                    forge.ai.AiProfileUtil.getAvailableProfiles().contains(name)
                            && forge.ai.AiProfileUtil.getAvailableProfiles().containsAll(
                                    java.util.Arrays.asList(RivalSetup.PROFILES)));
            check("IA propia: sale donde se elige (menu, cada rival, el pozo)",
                    AiProfiles.all().contains(name) && RivalSetup.isProfile(name));
            final forge.LobbyPlayer mine = aiWith(name);
            check("IA propia: el rival que la lleva juega con SUS valores",
                    "2".equals(forge.ai.AiProfileUtil.getAIProp(mine, forge.ai.AiProps.MULLIGAN_THRESHOLD))
                            && "false".equals(forge.ai.AiProfileUtil.getAIProp(mine, forge.ai.AiProps.PLAY_AGGRO)));
            final forge.LobbyPlayer reckless = aiWith("Reckless");
            check("IA propia: y las de fabrica siguen con los suyos (Reckless es agresiva)",
                    "true".equals(forge.ai.AiProfileUtil.getAIProp(reckless, forge.ai.AiProps.PLAY_AGGRO)));
            check("IA propia: se ha guardado limpia en los datos del jugador",
                    new File(AiProfiles.userDir(), name + ".ai").isFile());

            // 4. Volver a importar con el mismo nombre la sustituye (asi se retoca).
            AiProfiles.importFile(tempProfile(temps, "NeoCheck Pabi.ai",
                    java.util.Arrays.asList("MULLIGAN_THRESHOLD=4")));
            check("IA propia: reimportar con el mismo nombre la sustituye",
                    "4".equals(forge.ai.AiProfileUtil.getAIProp(aiWith(name),
                            forge.ai.AiProps.MULLIGAN_THRESHOLD))
                            && java.util.Collections.frequency(AiProfiles.custom(), name) == 1);

            // 5. Con el nombre de una de Forge no la pisa: se numera. Si el
            //    jugador ya tiene un "Default 2" (su Default.ai retocado), esto
            //    se lo sustituiria: entonces se salta.
            if (AiProfiles.isCustom("Default 2")) {
                System.out.println("        (saltada la de Default.ai: ya tienes un \"Default 2\")");
            } else {
                ours.add("Default 2");
                final AiProfiles.Imported clash = AiProfiles.importFile(tempProfile(temps, "Default.ai",
                        java.util.Arrays.asList("PLAY_AGGRO=true")));
                check("IA propia: un Default.ai propio entra como \"Default 2\" y Default sigue siendo el de Forge",
                        "Default 2".equals(clash.name) && "false".equals(forge.ai.AiProfileUtil.getAIProp(
                                aiWith("Default"), forge.ai.AiProps.PLAY_AGGRO)));
                AiProfiles.delete("Default 2");
            }

            // 5b. Los nombres "Random (...)": el motor trata aparte los suyos, y
            //     una numeracion por prefijo no acababa nunca (la ventana colgada,
            //     revisor 14-10-2026). Si esto se cuelga, la bateria lo corta.
            check("IA propia: solo se reservan los nombres exactos",
                    AiProfiles.isReserved("Random (Every Match)") && AiProfiles.isReserved("random")
                            && !AiProfiles.isReserved("Random (aggro)")
                            && !AiProfiles.isReserved("Random (Every Match) 2"));
            if (!AiProfiles.isCustom("Random (Every Match) 2")) {
                ours.add("Random (Every Match) 2");
                final AiProfiles.Imported rnd = AiProfiles.importFile(tempProfile(temps,
                        "Random (Every Match).ai", java.util.Arrays.asList("PLAY_AGGRO=true")));
                check("IA propia: \"Random (Every Match).ai\" entra numerado y no cuelga",
                        "Random (Every Match) 2".equals(rnd.name));
                AiProfiles.delete("Random (Every Match) 2");
            }

            // 5c. Una tilde de un comentario guardado en ANSI (Bloc de notas,
            //     Notepad++) no puede impedir importar: Forge lo cargaria.
            final File ansi = tempProfile(temps, "NeoCheck ansi.ai", java.util.Collections.emptyList());
            java.nio.file.Files.write(ansi.toPath(),
                    "# mi versi\u00f3n\r\nMULLIGAN_THRESHOLD=3\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            final AiProfiles.Imported ansiImp = AiProfiles.importFile(ansi);
            check("IA propia: un fichero en ANSI (una tilde en un comentario) se importa",
                    "NeoCheck ansi".equals(ansiImp.name) && ansiImp.settings == 1);

            // 6. Nada valido: no se importa nada.
            final AiProfiles.Imported junk = AiProfiles.importFile(tempProfile(temps, "NeoCheck basura.ai",
                    java.util.Arrays.asList("hola", "ESTO=no")));
            check("IA propia: un fichero sin un ajuste valido no se importa",
                    junk.name == null && !AiProfiles.isCustom("NeoCheck basura"));

            // 7. Uno roto dejado a mano en la carpeta no tumba la carga.
            java.nio.file.Files.write(broken.toPath(), java.util.Arrays.asList("NO_EXISTE=1"));
            AiProfiles.install();
            check("IA propia: uno roto dejado a mano se ignora y los demas siguen",
                    !AiProfiles.isCustom("NeoCheck roto") && AiProfiles.isCustom(name));

            // 7b. Cargar NO cambia la IA guardada aunque nombre a una que no
            //     esta: un fichero que un dia no se pudo leer la habria puesto
            //     en Default para siempre. El menu la ensenya como Default.
            forge.neo.NeoSettings.set(forge.neo.NeoSettings.AI_PROFILE, "NeoCheck fantasma");
            AiProfiles.install();
            check("IA propia: cargar no toca la IA guardada; el menu la ve como Default",
                    "NeoCheck fantasma".equals(forge.neo.NeoSettings.get(forge.neo.NeoSettings.AI_PROFILE, null))
                            && "Default".equals(AiProfiles.orDefault("NeoCheck fantasma"))
                            && name.equals(AiProfiles.orDefault(name)) && "".equals(AiProfiles.orDefault("")));

            // 8. Quitarla: el ajuste que la nombraba vuelve solo a Default.
            forge.neo.NeoSettings.set(forge.neo.NeoSettings.AI_PROFILE, name);
            RivalSetup.setPool(java.util.Arrays.asList("Cautious", name));
            final boolean gone = AiProfiles.delete(name);
            check("IA propia: quitarla la quita del motor y de la lista",
                    gone && !forge.ai.AiProfileUtil.getAvailableProfiles().contains(name)
                            && !RivalSetup.isProfile(name));
            check("IA propia: y lo que la tenia elegida vuelve solo a lo de siempre",
                    "Default".equals(forge.neo.NeoSettings.get(forge.neo.NeoSettings.AI_PROFILE, null))
                            && RivalSetup.pool().equals(java.util.Arrays.asList("Cautious")));
        } catch (final java.io.IOException | RuntimeException e) {
            e.printStackTrace(System.out);
            check("IA propia: sin excepciones", false);
        } finally {
            broken.delete();
            for (final String n : new java.util.ArrayList<>(AiProfiles.custom())) {
                if (n.startsWith("NeoCheck") || ours.contains(n)) {
                    AiProfiles.delete(n);
                }
            }
            for (final File f : temps) {
                f.delete();
            }
            AiProfiles.install();
            forge.neo.NeoSettings.set(forge.neo.NeoSettings.AI_PROFILE, profileBefore);
            forge.neo.NeoSettings.set(RivalSetup.POOL, poolBefore);
            forge.neo.NeoSettings.save();
        }
    }

    /** Un rival de IA con ese perfil, sin sortear ni guardar nombres (NeoPlayers.ai los guarda). */
    private static forge.LobbyPlayer aiWith(final String profile) {
        final forge.ai.LobbyPlayerAi p = new forge.ai.LobbyPlayerAi("NeoCheck", null);
        p.setAiProfile(profile);
        return p;
    }

    private static File tempProfile(final List<File> temps, final String name, final List<String> lines)
            throws java.io.IOException {
        final File dir = java.nio.file.Files.createTempDirectory("neo-ai-").toFile();
        final File f = new File(dir, name);
        java.nio.file.Files.write(f.toPath(), lines, java.nio.charset.StandardCharsets.UTF_8);
        temps.add(f);
        temps.add(dir);
        return f;
    }

    // ---------------------------------------------------------------

    private static void check(final String what, final boolean ok) {
        System.out.printf(Locale.ROOT, "  [%s] %s%n", ok ? "OK " : "MAL", what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
