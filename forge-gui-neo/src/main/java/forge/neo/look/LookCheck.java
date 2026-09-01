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
        deckCarriesItsOwnSleeve();

        System.out.println();
        System.out.printf(Locale.ROOT, "  %d comprobaciones OK, %d fallos%n", passed, failed);
        if (failed > 0) {
            System.out.println("  *** HAY FALLOS ***");
        }
    }

    // ---------------------------------------------------------------

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
