package forge.neo.quest;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import forge.deck.Deck;
import forge.gamemodes.quest.QuestController;
import forge.gamemodes.quest.QuestEventDifficulty;
import forge.gamemodes.quest.QuestEventChallenge;
import forge.gamemodes.quest.QuestEventDuel;
import forge.gamemodes.quest.QuestMode;
import forge.gamemodes.quest.QuestWorld;
import forge.gamemodes.quest.StartingPoolPreferences;
import forge.gamemodes.quest.data.DeckConstructionRules;
import forge.gamemodes.quest.data.QuestData;
import forge.gamemodes.quest.data.QuestPreferences;
import forge.gamemodes.quest.io.QuestDataIO;
import forge.item.PaperCard;
import forge.util.ItemPool;
import forge.localinstance.properties.ForgeConstants;
import forge.model.FModel;

/**
 * La aventura: una partida de Quest en curso.
 *
 * <p>Es a {@code QuestController} lo que {@link forge.neo.draft.NeoDraft} es al
 * draft: la tabla que enchufa lo que Forge ya tiene, <b>sin una regla propia</b>.
 * El motor trae la aventura entera — 72 clases en {@code forge.gamemodes.quest}:
 * coleccion, creditos, tienda, sobres, duelos por dificultad, subidas de nivel,
 * desbloqueo de ediciones y el guardado. Lo que no trae es la pantalla.
 *
 * <p>Las dos modalidades que se pidieron NO son cosa nuestra: el motor las tiene
 * como {@link DeckConstructionRules}. En {@code Commander} la aventura entera se
 * juega con mazos de comandante y 40 vidas, y lo aplica el propio
 * {@code QuestUtil} al montar cada duelo.
 *
 * <p>El progreso se guarda en {@code %APPDATA%\\Forge\\quest\\<nombre>.dat}, que
 * es donde lo guarda tambien la instalacion normal de Forge: una aventura
 * empezada aqui se puede seguir alli y al reves.
 *
 * <p>Cero JavaFX a proposito: asi el bucle entero se puede probar sin abrir una
 * ventana ({@code run.cmd questcheck}).
 */
public final class NeoQuest {

    private NeoQuest() {
    }

    /** Con que reglas se juega toda la aventura. */
    public enum Modalidad {
        ESTANDAR("Estandar", DeckConstructionRules.Default,
                "Mazos de 60 cartas y 20 vidas, como una partida normal"),
        COMMANDER("Commander", DeckConstructionRules.Commander,
                "Toda la aventura con mazos de comandante y 40 vidas");

        private final String label;
        private final DeckConstructionRules rules;
        private final String description;

        Modalidad(final String label, final DeckConstructionRules rules, final String description) {
            this.label = label;
            this.rules = rules;
            this.description = description;
        }

        public String getLabel() {
            return forge.neo.NeoText.get("quest.mode." + name() + ".label");
        }

        public String getDescription() {
            return forge.neo.NeoText.get("quest.mode." + name() + ".desc");
        }

        public DeckConstructionRules getRules() {
            return rules;
        }

        public static Modalidad of(final DeckConstructionRules rules) {
            return rules == DeckConstructionRules.Commander ? COMMANDER : ESTANDAR;
        }
    }

    /** Lo dificil que se pone. Son los cuatro niveles del motor. */
    public enum Dificultad {
        FACIL("Facil", 0), NORMAL("Normal", 1), DIFICIL("Dificil", 2), EXPERTA("Experta", 3);

        private final String label;
        private final int index;

        Dificultad(final String label, final int index) {
            this.label = label;
            this.index = index;
        }

        public String getLabel() {
            return forge.neo.NeoText.get("quest.difficulty." + name());
        }

        public int getIndex() {
            return index;
        }
    }

    // ---------------------------------------------------------------
    // Empezar, cargar, guardar, abandonar
    // ---------------------------------------------------------------

    /**
     * Empieza una aventura nueva y la deja cargada.
     *
     * <p>Los parametros que van a null son deliberados: sin formato de premios
     * ni de pool inicial, la aventura usa TODAS las cartas de Forge, que es lo
     * que hace que abrir un sobre pueda darte cualquier cosa. Es el equivalente
     * de "Unrestricted" en la pantalla vieja.
     */
    public static void start(final String name, final Modalidad modalidad,
                             final Dificultad dificultad) {
        start(name, modalidad, dificultad, null);
    }

    /**
     * Empieza una aventura con un mazo concreto de partida.
     *
     * <p>Esto es lo que hace que la aventura tenga cuesta arriba: empiezas con
     * un <b>preconstruido</b> — un mazo de verdad, jugable y bastante malo — en
     * vez de con trescientas cartas sueltas, y lo vas mejorando con lo que
     * salga de los sobres. Es la diferencia entre "monta un mazo con este pool"
     * y "toma este mazo, hazlo mejor".
     *
     * <p>El motor lo soporta de serie: {@code newGame} con {@code startingCards}
     * mete el mazo entero en tu coleccion Y te lo deja montado, asi que puedes
     * jugar el primer duelo sin pasar por el constructor.
     *
     * @param startingDeck el mazo con el que empiezas, o null para un pool suelto
     */
    public static void start(final String name, final Modalidad modalidad,
                             final Dificultad dificultad, final Deck startingDeck) {
        start(name, modalidad, dificultad, startingDeck, QuestWorld.MAINWORLDNAME);
    }

    /**
     * Y en el mundo que se elija.
     *
     * <p>El mundo <b>no es un decorado</b>: cada uno trae su lista de
     * expansiones y de prohibidas, y ademas su propia carpeta de rivales y de
     * desafios. Empezar en Zendikar significa que la tienda solo vende sobres
     * de Zendikar y que los rivales son los de esa carpeta. Ver
     * {@link NeoQuestWorlds}.
     *
     * @param worldName el mundo, o null para el principal
     */
    public static void start(final String name, final Modalidad modalidad,
                             final Dificultad dificultad, final Deck startingDeck,
                             final String worldName) {
        final StartingPoolPreferences prefs = new StartingPoolPreferences(
                StartingPoolPreferences.PoolType.BALANCED,
                Collections.emptyList(),   // sin colores preferidos: que salga de todo
                true,                      // con artefactos
                false,                     // sin edicion completa de regalo
                false,                     // sin duplicados
                0);

        // El mundo hay que decirlo. Con null, QuestController.resetDuelsManager
        // se queda con su variable inicializada a DEFAULT_CHALLENGES_DIR y acaba
        // sacando los "duelos" de la carpeta de DESAFIOS: rivales que no son los
        // que tocan y con la dificultad mal. Con "Main world" usa
        // res/quest/duels, que trae 238 rivales faciles.
        final String world = worldName == null || worldName.isBlank()
                ? QuestWorld.MAINWORLDNAME : worldName;
        FModel.getQuest().newGame(name, dificultad.getIndex(), QuestMode.Classic,
                null, true, startingDeck, null, world, prefs,
                modalidad.getRules());
        if (startingDeck != null) {
            // newGame lo mete en la coleccion y en tus mazos, pero no lo deja
            // ELEGIDO: sin esto el primer duelo te dice que no tienes mazo.
            setCurrentDeck(startingDeck.getName());
        }
        oneGamePerDuel();
        dropSideboards();
        forgetDuels();
        forgetChallenges();
        save();
        remember(name);
    }

    /** Deja cargada una aventura ya guardada. */
    public static boolean load(final String name) {
        final File file = fileOf(name);
        if (!file.isFile()) {
            return false;
        }
        try {
            FModel.getQuest().load(QuestDataIO.loadData(file));
            oneGamePerDuel();
            if (dropSideboards()) {
                save();
            }
            forgetDuels();
            remember(name);
            return true;
        } catch (final IOException e) {
            System.err.println("[neo] no se ha podido cargar la aventura: " + e);
            return false;
        }
    }

    /** Vuelve a dejar cargada la ultima aventura que se estuviera jugando. */
    public static boolean resume() {
        final String saved = FModel.getQuestPreferences()
                .getPref(QuestPreferences.QPref.CURRENT_QUEST);
        if (saved != null && !saved.isBlank()) {
            final String name = saved.endsWith(".dat")
                    ? saved.substring(0, saved.length() - 4) : saved;
            if (load(name)) {
                return true;
            }
        }
        // La preferencia puede apuntar a una aventura borrada, o venir de la
        // instalacion normal de Forge sin haberse tocado nunca. Si hay alguna
        // guardada, se abre esa antes que mandar al jugador a crear otra: sus
        // aventuras viejas viven en la MISMA carpeta.
        final List<String> all = saves();
        return !all.isEmpty() && load(all.get(0));
    }

    /**
     * En la aventura los mazos no tienen banquillo.
     *
     * <p>Tres cosas, y las tres apuntan al mismo sitio. El editor de NeoForge
     * <b>no ensenya el banquillo</b> — no existe el concepto en ninguna
     * pantalla. Cada duelo es <b>una sola partida</b> ({@link
     * #oneGamePerDuel()}), asi que no se cambia nada entre juegos y el
     * banquillo no llega a usarse jamas. Y sin embargo el motor <b>si lo
     * cuenta</b> para el limite de copias: {@code getAllCardsInASinglePool}
     * suma principal y banquillo antes de mirar si te pasas de cuatro.
     *
     * <p>Juntando las tres sale el fallo que se reporto: adoptas el
     * preconstruido <i>Cavalcade Charge</i>, que trae 3 Experimental Frenzy en
     * un banquillo que no puedes ver, metes 3 en el principal porque el
     * catalogo te deja, y el cuartel general te dice que no puedes jugar con un
     * mazo en el que solo ves tres copias.
     *
     * <p><b>No se pierde nada al quitarlo.</b> Las cartas del banquillo entran
     * en tu coleccion al adoptar el mazo ({@code QuestUtilCards.addDeck} suma
     * {@code getAllCardsInASinglePool}), o sea que siguen ahi para meterlas en
     * el principal cuando quieras. Lo unico que desaparece es una zona
     * invisible que solo servia para hacer ilegal el mazo.
     *
     * @return true si habia algo que quitar (o sea, si hay que guardar)
     */
    public static boolean dropSideboards() {
        final QuestController q = FModel.getQuest();
        if (q == null || q.getMyDecks() == null) {
            return false;
        }
        // Se recogen primero y se sustituyen despues: anyadir al almacen
        // mientras se recorre lo revienta.
        final List<Deck> fixed = new ArrayList<>();
        for (final Deck deck : q.getMyDecks()) {
            final forge.deck.CardPool side = deck.get(forge.deck.DeckSection.Sideboard);
            if (side != null && !side.isEmpty()) {
                // COPIA, no vaciar el original: el mazo que el motor mete en
                // tus mazos al comprar un preconstruido es el MISMO objeto que
                // tiene la tienda en su catalogo. Vaciarlo ahi le quitaria el
                // banquillo al preconstruido para el resto de la sesion.
                final Deck copy = new Deck(deck, deck.getName());
                copy.get(forge.deck.DeckSection.Sideboard).clear();
                fixed.add(copy);
            }
        }
        for (final Deck deck : fixed) {
            q.getMyDecks().add(deck);
        }
        return !fixed.isEmpty();
    }

    /**
     * Cada duelo es UNA partida, no un match al mejor de tres.
     *
     * <p>No es solo ritmo: {@code QuestWinLoseController} solo reparte
     * recompensas y anota la victoria <b>cuando el MATCH se acaba</b>
     * ({@code isMatchOver}). Con el valor de fabrica (3 partidas) ganabas el
     * duelo, volvias al cuartel general y no habia cobrado nada ni subido el
     * marcador — sintoma exacto: "15-0 -> 15-0, creditos 200 -> 200".
     */
    private static void oneGamePerDuel() {
        FModel.getQuest().setMatchLength("1");
    }

    public static void save() {
        FModel.getQuest().save();
    }

    /** true si hay una aventura cargada ahora mismo. */
    public static boolean isActive() {
        final QuestController q = FModel.getQuest();
        return q != null && q.getAssets() != null && q.getName() != null && !q.getName().isBlank();
    }

    public static QuestController engine() {
        return FModel.getQuest();
    }

    /**
     * Borra una aventura del disco.
     *
     * <p>Se pidio poder "volver a empezar cuando quieras". Aqui no hay vuelta
     * atras, asi que quien lo llame tiene que preguntar antes.
     */
    public static void delete(final String name) {
        final File dat = fileOf(name);
        final File bak = new File(ForgeConstants.QUEST_SAVE_DIR, name + ".dat.bak");
        if (dat.isFile()) {
            dat.delete();
        }
        if (bak.isFile()) {
            bak.delete();
        }
        final String current = FModel.getQuestPreferences()
                .getPref(QuestPreferences.QPref.CURRENT_QUEST);
        if (current != null && current.equals(name + ".dat")) {
            remember("");
        }
    }

    /** Las aventuras guardadas, por nombre. */
    /**
     * Lo que se sabe de una aventura guardada SIN abrirla.
     *
     * <p>Hace falta para poder elegir: con tres partidas guardadas, una lista
     * de nombres no dice cual es la de Commander y cual la de Estandar, que es
     * justo lo que hay que saber para escoger.
     */
    public static final class SaveInfo {
        private final String name;
        private final boolean commander;
        private final int wins;
        private final int losses;
        private final long credits;
        private final int level;
        private final boolean readable;

        SaveInfo(final String name, final boolean commander, final int wins, final int losses,
                 final long credits, final int level, final boolean readable) {
            this.name = name;
            this.commander = commander;
            this.wins = wins;
            this.losses = losses;
            this.credits = credits;
            this.level = level;
            this.readable = readable;
        }

        public String getName() {
            return name;
        }

        /** Si se juega con mazos de comandante. */
        public boolean isCommander() {
            return commander;
        }

        public int getWins() {
            return wins;
        }

        public int getLosses() {
            return losses;
        }

        public long getCredits() {
            return credits;
        }

        public int getLevel() {
            return level;
        }

        /** Si el fichero se ha podido leer. Uno roto sale, pero solo con su nombre. */
        public boolean isReadable() {
            return readable;
        }
    }

    /**
     * Las aventuras guardadas, con sus datos.
     *
     * <p>Cada una se lee del disco con {@code QuestDataIO.loadData}, que NO la
     * instala en el motor: solo devuelve los datos. Son ficheros pequenyos y
     * son tres o cuatro, asi que leerlos para pintar la lista no se nota.
     *
     * <p>Una partida que no se pueda leer <b>sigue saliendo en la lista</b>,
     * con su nombre y marcada como ilegible. Esconderla seria lo peor que se
     * puede hacer: es justamente la que el jugador va a querer borrar.
     */
    public static List<SaveInfo> saveInfos() {
        final List<SaveInfo> out = new ArrayList<>();
        for (final String name : saves()) {
            out.add(infoOf(name));
        }
        return out;
    }

    private static SaveInfo infoOf(final String name) {
        try {
            final forge.gamemodes.quest.data.QuestData data =
                    forge.gamemodes.quest.io.QuestDataIO.loadData(fileOf(name));
            if (data == null) {
                return new SaveInfo(name, false, 0, 0, 0, 0, false);
            }
            final boolean commander = data.deckConstructionRules
                    == forge.gamemodes.quest.data.DeckConstructionRules.Commander;
            int wins = 0;
            int losses = 0;
            int level = 0;
            if (data.getAchievements() != null) {
                wins = data.getAchievements().getWin();
                losses = data.getAchievements().getLost();
                level = data.getAchievements().getLevel();
            }
            final long credits = data.getAssets() == null ? 0 : data.getAssets().getCredits();
            return new SaveInfo(name, commander, wins, losses, credits, level, true);
        } catch (final Exception e) {
            System.err.println("[neo] no se ha podido leer la aventura " + name + ": " + e);
            return new SaveInfo(name, false, 0, 0, 0, 0, false);
        }
    }

    public static List<String> saves() {
        final List<String> out = new ArrayList<>();
        final File dir = new File(ForgeConstants.QUEST_SAVE_DIR);
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File f : files) {
                if (f.isFile() && f.getName().endsWith(".dat")) {
                    out.add(f.getName().substring(0, f.getName().length() - 4));
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    /** Lee una aventura guardada sin cargarla, para poder listarla con datos. */
    public static QuestData peek(final String name) {
        try {
            return QuestDataIO.loadData(fileOf(name));
        } catch (final IOException | RuntimeException e) {
            return null;
        }
    }

    private static File fileOf(final String name) {
        return new File(ForgeConstants.QUEST_SAVE_DIR, name + ".dat");
    }

    /** Deja anotado cual es la aventura en curso, para reanudarla al abrir. */
    private static void remember(final String name) {
        FModel.getQuestPreferences().setPref(QuestPreferences.QPref.CURRENT_QUEST,
                name.isEmpty() ? "" : name + ".dat");
        FModel.getQuestPreferences().save();
    }

    // ---------------------------------------------------------------
    // Como voy
    // ---------------------------------------------------------------

    public static String name() {
        return isActive() ? FModel.getQuest().getName() : null;
    }

    public static Modalidad modalidad() {
        return Modalidad.of(FModel.getQuest().getDeckConstructionRules());
    }

    public static long credits() {
        return FModel.getQuest().getAssets().getCredits();
    }

    public static int level() {
        return FModel.getQuest().getLevel();
    }

    /** El titulo del nivel ("Level 3 - Mana Adept"...). Lo escribe el motor. */
    public static String rank() {
        return FModel.getQuest().getRank();
    }

    public static int wins() {
        return FModel.getQuest().getAchievements().getWin();
    }

    public static int losses() {
        return FModel.getQuest().getAchievements().getLost();
    }

    /** Con cuantas vidas empiezas los duelos, con las mejoras del bazar ya sumadas. */
    public static int life() {
        return FModel.getQuest().getAssets().getLife(FModel.getQuest().getMode());
    }

    /** Tu coleccion entera. */
    public static ItemPool<PaperCard> collection() {
        return FModel.getQuest().getAssets().getCardPool();
    }

    /** Cuantas cartas distintas llevas. */
    public static int collectionSize() {
        return collection() == null ? 0 : collection().countAll();
    }

    /**
     * El mismo mazo pero con 12 cartas. <b>Herramienta de prueba.</b>
     *
     * <p>Sirve para que un duelo termine en un minuto en vez de en veinte: el
     * rival se queda sin biblioteca y pierde. Es la unica forma de recorrer el
     * camino de VICTORIA sin jugar la partida a mano, y ese camino — cobrar,
     * subir de nivel, el sobre de premio, la pantalla del botin — es justo el
     * que importa.
     *
     * <p>Se RECORTA el suyo en vez de fabricar uno de montanyas porque en la
     * aventura de Commander el asiento se registra con
     * {@code RegisteredPlayer.forCommander}, y un mazo sin comandante deja la
     * partida sin arrancar: se queda colgada sin decir nada.
     */
    public static Deck tinyCopyOf(final Deck original) {
        final Deck small = (Deck) original.copyTo("neo-tiny");
        final List<forge.item.PaperCard> keep = small.getMain().toFlatList();
        small.getMain().clear();

        // ⚠️ Las TIERRAS primero, y no las doce primeras cartas del mazo.
        //
        // Recortar por orden dejaba el rig a suerte: si esas doce traian
        // amenazas, el rival montaba mesa y MATABA al humano — que en AUTO_PLAY
        // se queda mirando, con la mesa a cero — antes de quedarse sin
        // biblioteca. Y entonces el camino de victoria, que es justo el que
        // esta prueba existe para recorrer, no se recorria.
        //
        // Medido el 30-08-2026: tres corridas de questcheck sobre el mismo
        // binario dieron verde, ROJO y verde. En la roja el rival llego a ocho
        // permanentes y bajo al humano de 20 a 0 en once turnos; en las verdes
        // le toco un rival que no hizo nada. O sea que dependia de a quien
        // emparejara el azar, no del codigo.
        //
        // Con las tierras delante el rival no puede hacer nada y se queda sin
        // cartas, que es lo que se buscaba. La lista se ordena en vez de
        // filtrarse para que un mazo con menos de doce tierras siga saliendo
        // con doce cartas: encogerlo es lo que hace que el duelo dure un minuto.
        final List<forge.item.PaperCard> orden = new ArrayList<>(keep.size());
        for (final forge.item.PaperCard c : keep) {
            if (c.getRules().getType().isLand()) {
                orden.add(c);
            }
        }
        for (final forge.item.PaperCard c : keep) {
            if (!c.getRules().getType().isLand()) {
                orden.add(c);
            }
        }
        for (int i = 0; i < Math.min(12, orden.size()); i++) {
            small.getMain().add(orden.get(i));
        }
        return small;
    }

    /** Los mazos que has montado en esta aventura. */
    public static List<Deck> decks() {
        final List<Deck> out = new ArrayList<>();
        FModel.getQuest().getMyDecks().forEach(out::add);
        return out;
    }

    /** El mazo con el que juegas los duelos, o null si no has elegido. */
    public static Deck currentDeck() {
        final String name = FModel.getQuest().getCurrentDeck();
        if (name == null) {
            return null;
        }
        return FModel.getQuest().getMyDecks().get(name);
    }

    public static void setCurrentDeck(final String deckName) {
        FModel.getQuest().setCurrentDeck(deckName);
        save();
    }

    /**
     * Borra un mazo de la aventura. Devuelve true si de verdad se ha ido.
     *
     * <p>Las cartas <b>no</b> se pierden: en la aventura el mazo es una lista
     * sobre tu coleccion, y la coleccion es lo que se gana y se compra. Borrar
     * un mazo solo deshace el montaje.
     *
     * <p>Si era el mazo con el que juegas, se pasa a otro que quede. Quedarse
     * apuntando a uno que ya no existe deja el cuartel diciendo "sin mazo" y
     * los duelos sin arrancar, que es un callejon sin salida a un click de
     * distancia.
     */
    public static boolean deleteDeck(final String deckName) {
        if (deckName == null || FModel.getQuest().getMyDecks().get(deckName) == null) {
            return false;
        }
        FModel.getQuest().getMyDecks().delete(deckName);
        if (deckName.equals(FModel.getQuest().getCurrentDeck())) {
            final List<Deck> left = decks();
            FModel.getQuest().setCurrentDeck(left.isEmpty() ? null : left.get(0).getName());
        }
        save();
        return FModel.getQuest().getMyDecks().get(deckName) == null;
    }

    /**
     * Los mazos con los que se puede empezar la aventura.
     *
     * <p>Son los preconstruidos que trae Forge: 173 de Commander y 505 del
     * resto. Estan pensados justo para esto — son mazos completos, tematicos y
     * de potencia baja, o sea con sitio para mejorar.
     */
    public static List<Deck> starterDecks(final Modalidad modalidad) {
        final List<Deck> out = new ArrayList<>();
        if (modalidad == Modalidad.COMMANDER) {
            FModel.getDecks().getCommanderPrecons().forEach(out::add);
        } else {
            try {
                for (final forge.item.PreconDeck precon : QuestController.getPrecons()) {
                    if (precon.getDeck() != null) {
                        out.add(precon.getDeck());
                    }
                }
            } catch (final RuntimeException e) {
                System.err.println("[neo] no se han podido leer los preconstruidos: " + e);
            }
        }
        return out;
    }

    /**
     * Un mazo de Commander deliberadamente flojo: <b>bracket 1</b>.
     *
     * <p>Se pidio poder empezar con algo "muy malo" para ir mejorandolo. El
     * bracket es la escala de potencia oficial de Commander y el motor la
     * calcula ({@code CommanderBracketCalculator}), asi que se le pide un mazo
     * de bracket 1 y punto: nosotros no decidimos que carta es fuerte.
     *
     * @return el mazo, o null si el motor no ha podido generarlo
     */
    public static Deck bracketOneDeck() {
        final List<PaperCard> commanders = new ArrayList<>();
        for (final PaperCard pc : FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (pc.getRules() != null
                    && forge.deck.DeckFormat.Commander.isLegalCommander(pc.getRules())) {
                commanders.add(pc);
            }
        }
        if (commanders.isEmpty()) {
            return null;
        }
        Collections.shuffle(commanders);
        try {
            return forge.deck.DeckgenUtil.generateRandomCommanderDeck(
                    commanders.get(0), forge.deck.DeckFormat.Commander, false, false, 1);
        } catch (final RuntimeException e) {
            System.err.println("[neo] no se ha podido generar un mazo de bracket 1: " + e);
            return null;
        }
    }

    /**
     * Monta un mazo jugable con las cartas que tienes, y lo deja elegido.
     *
     * <p>Sin esto la aventura no arranca: empiezas con 300 cartas sueltas y
     * ningun mazo, y sin mazo no hay duelo. Es ademas lo que quiere el 90% de
     * las veces quien acaba de abrir tres sobres — montarlo a mano es una
     * opcion, no una obligacion.
     *
     * <p>Lo monta el motor con TU coleccion, no con todas las cartas de Forge:
     * {@code SealedDeckBuilder} para Estandar (elige dos colores del mejor
     * tercio del pool) y {@code CardThemedCommanderDeckBuilder} para Commander.
     * Nosotros solo elegimos el comandante, y eso tambien lo decide el motor:
     * {@code DeckFormat.Commander.isLegalCommander}.
     *
     * @return el mazo, o null si el pool no da para uno legal
     */
    public static Deck buildStarterDeck(final String deckName) {
        final List<PaperCard> pool = collection().toFlatList();
        if (pool.isEmpty()) {
            return null;
        }
        Deck deck;
        if (modalidad() == Modalidad.COMMANDER) {
            final PaperCard commander = pickCommander(pool);
            if (commander == null) {
                return null;
            }
            final List<PaperCard> rest = new ArrayList<>(pool);
            rest.removeAll(FModel.getMagicDb().getCommonCards().getAllCards(commander));
            final forge.deck.generation.DeckGeneratorBase gen =
                    new forge.gamemodes.limited.CardThemedCommanderDeckBuilder(
                            commander, null, rest, false, forge.deck.DeckFormat.Commander);
            gen.setSingleton(true);
            final forge.deck.CardPool cards = gen.getDeck(
                    forge.deck.DeckFormat.Commander.getMainRange().getMaximum(), false);
            deck = new Deck(deckName);
            deck.getMain().addAll(cards);
            deck.getOrCreate(forge.deck.DeckSection.Commander).add(commander);
        } else {
            deck = (Deck) new forge.gamemodes.limited.SealedDeckBuilder(pool)
                    .buildDeck().copyTo(deckName);
        }
        // El montador de sellado deja las sobras en el banquillo, y en la
        // aventura eso solo sirve para pasarse del limite de copias sin que se
        // vea. Ver dropSideboards(): las cartas siguen en tu coleccion.
        final forge.deck.CardPool side = deck.get(forge.deck.DeckSection.Sideboard);
        if (side != null) {
            side.clear();
        }
        FModel.getQuest().getMyDecks().add(deck);
        setCurrentDeck(deckName);
        return deck;
    }

    /** El mejor candidato a comandante que tengas. Legal lo decide el motor. */
    private static PaperCard pickCommander(final List<PaperCard> pool) {
        for (final PaperCard pc : pool) {
            if (pc.getRules() != null
                    && forge.deck.DeckFormat.Commander.isLegalCommander(pc.getRules())) {
                return pc;
            }
        }
        return null;
    }

    /**
     * Que le pasa a este mazo para no poder jugarlo, o null si esta bien.
     *
     * <p>Lo contesta el motor con las reglas de ESTA aventura (las de Quest, y
     * ademas las de Commander si la modalidad lo es). Aqui no se comprueba
     * nada a mano.
     */
    public static String problemWith(final Deck deck) {
        if (deck == null) {
            return forge.neo.NeoText.get("quest.noDeck");
        }
        // Traducido, como en el editor: el motor compone estas frases a mano y
        // sin Localizer, asi que llegan en ingles en los once idiomas. Aqui se
        // leen en el cuartel general, que es donde te enteras de que no puedes
        // jugar. Lo que DeckProblem no reconoce pasa tal cual.
        return forge.neo.deck.DeckProblem.translate(
                forge.gamemodes.quest.QuestUtil.getDeckConformanceProblemsBeforeGame(deck));
    }

    // ---------------------------------------------------------------
    // Los duelos
    // ---------------------------------------------------------------

    /**
     * Contra que clase de rival estas jugando ahora.
     *
     * <p><b>Esto es lo que hace que la aventura sea una aventura.</b> El motor
     * escala los rivales con tus VICTORIAS, no con el reloj: al empezar un
     * Commander te toca la version floja del mazo generado, y segun ganas te va
     * sustituyendo cartas por las de la version experta (30% en medio, 60% en
     * dificil, el mazo entero en experto). En Estandar hace lo mismo eligiendo
     * los duelos de la carpeta por dificultad.
     *
     * <p>Los umbrales son del motor ({@code DifficultyPrefs.WINS_*AI}) y
     * dependen de la dificultad que elegiste. Aqui solo se LEEN, para poder
     * ensenyar por donde vas.
     */
    public static QuestEventDifficulty tier() {
        final int wins = wins();
        final int idx = FModel.getQuest().getAchievements().getDifficulty();
        final QuestPreferences prefs = FModel.getQuestPreferences();
        if (wins >= prefs.getPrefInt(QuestPreferences.DifficultyPrefs.WINS_EXPERTAI, idx)) {
            return QuestEventDifficulty.EXPERT;
        }
        if (wins >= prefs.getPrefInt(QuestPreferences.DifficultyPrefs.WINS_HARDAI, idx)) {
            return QuestEventDifficulty.HARD;
        }
        if (wins >= prefs.getPrefInt(QuestPreferences.DifficultyPrefs.WINS_MEDIUMAI, idx)) {
            return QuestEventDifficulty.MEDIUM;
        }
        return QuestEventDifficulty.EASY;
    }

    /** Cuantas victorias faltan para que los rivales suban de nivel. 0 si ya estas arriba. */
    public static int winsToNextTier() {
        final int wins = wins();
        final int idx = FModel.getQuest().getAchievements().getDifficulty();
        final QuestPreferences prefs = FModel.getQuestPreferences();
        for (final QuestPreferences.DifficultyPrefs step : new QuestPreferences.DifficultyPrefs[] {
                QuestPreferences.DifficultyPrefs.WINS_MEDIUMAI,
                QuestPreferences.DifficultyPrefs.WINS_HARDAI,
                QuestPreferences.DifficultyPrefs.WINS_EXPERTAI}) {
            final int need = prefs.getPrefInt(step, idx);
            if (wins < need) {
                return need - wins;
            }
        }
        return 0;
    }

    /**
     * Con cuantas victorias empezo el escalon actual.
     *
     * <p>Solo sirve para pintar la barra de progreso: sin esto, la barra
     * contaria desde cero y en el escalon dos ya saldria casi llena sin haber
     * ganado nada nuevo.
     */
    public static int tierStartedAt() {
        final int idx = FModel.getQuest().getAchievements().getDifficulty();
        final QuestPreferences prefs = FModel.getQuestPreferences();
        switch (tier()) {
            case EXPERT: return prefs.getPrefInt(QuestPreferences.DifficultyPrefs.WINS_EXPERTAI, idx);
            case HARD: return prefs.getPrefInt(QuestPreferences.DifficultyPrefs.WINS_HARDAI, idx);
            case MEDIUM: return prefs.getPrefInt(QuestPreferences.DifficultyPrefs.WINS_MEDIUMAI, idx);
            default: return 0;
        }
    }

    /** El nivel de rival en castellano, para la pantalla. */
    public static String tierLabel() {
        switch (tier()) {
            case MEDIUM: return forge.neo.NeoText.get("quest.tier.medium");
            case HARD: return forge.neo.NeoText.get("quest.tier.hard");
            case EXPERT: return forge.neo.NeoText.get("quest.tier.expert");
            default: return forge.neo.NeoText.get("quest.tier.easy");
        }
    }

    /**
     * Los duelos que tienes disponibles ahora mismo.
     *
     * <p>Los genera el motor segun tu nivel y tu racha, y cada uno trae su
     * dificultad, su mazo y su recompensa. Se renuevan al ganar.
     */
    public static List<QuestEventDuel> duels() {
        final QuestController q = FModel.getQuest();
        if (q == null || q.getDuelsManager() == null) {
            return new ArrayList<>();
        }
        // OJO: getAllDuels() son TODOS los duelos posibles — cientos. Los que
        // te tocan hoy los elige generateDuels() a partir de tus victorias, y
        // son cuatro.
        final int stamp = wins() * 1000 + losses();
        if (currentDuels == null || duelsStamp != stamp) {
            currentDuels = q.getDuelsManager().generateDuels();
            duelsStamp = stamp;
        }
        return currentDuels == null ? new ArrayList<>() : new ArrayList<>(currentDuels);
    }

    /**
     * Los duelos de hoy, y con que marcador se generaron.
     *
     * <p>Se guardan porque la pantalla se repinta muchas veces y volver a
     * generarlos en cada repintado te cambiaria las opciones delante de las
     * narices. Se renuevan cuando cambia el marcador, o sea despues de cada
     * duelo, que es exactamente cuando toca.
     */
    private static List<QuestEventDuel> currentDuels;
    private static int duelsStamp = -1;

    /** Olvida los duelos de hoy. Al cambiar de aventura son otros. */
    public static void forgetDuels() {
        currentDuels = null;
        duelsStamp = -1;
    }

    // ===============================================================
    // Los desafios
    // ===============================================================

    /**
     * Los desafios que tienes abiertos ahora mismo.
     *
     * <p>Un desafio no es un duelo mas: es una <b>situacion preparada</b>. El
     * fichero trae el mazo del rival, cuanta vida tiene (a menudo mucha mas),
     * las cartas con las que uno u otro <b>empieza en la mesa</b>, y una
     * recompensa fija. Forge trae 37 en {@code res/quest/challenges} y no se
     * habian abierto nunca.
     *
     * <p>Se desbloquean con victorias, y de eso se encarga el motor entero:
     * {@code regenerateChallenges()} mira cuantas llevas, cuantos desafios has
     * jugado ya y cuales piden mas victorias de las que tienes, y deja como
     * mucho <b>cinco</b> abiertos. Cada cuantas victorias llega uno nuevo lo
     * dice {@code getTurnsToUnlockChallenge()}, que ademas <b>baja con el
     * bazar</b> (el mapa lo acorta un turno, el zepelin dos).
     *
     * <p>Y un detalle que cambia como se presentan: los que <b>no</b> son
     * repetibles se juegan UNA vez en toda la aventura. Eso hay que decirlo
     * antes, no despues.
     */
    public static List<QuestEventChallenge> challenges() {
        final QuestController q = FModel.getQuest();
        if (q == null) {
            return new ArrayList<>();
        }
        final int stamp = wins() * 1000 + losses();
        if (currentChallenges == null || challengeStamp != stamp) {
            try {
                // Las dos llamadas hacen falta, y en este orden: la primera
                // decide CUALES estan abiertos hoy y lo apunta en la partida
                // guardada; la segunda los saca de ahi. Pedir la lista sin
                // regenerar devuelve la de la sesion anterior.
                q.regenerateChallenges();
                final List<QuestEventChallenge> out = new ArrayList<>();
                for (final String id : q.getAchievements().getCurrentChallenges()) {
                    final QuestEventChallenge c = q.getChallenges().get(id);
                    if (c != null) {
                        out.add(c);
                    }
                }
                currentChallenges = out;
            } catch (final RuntimeException e) {
                System.err.println("[aventura] no se han podido leer los desafios: " + e);
                currentChallenges = new ArrayList<>();
            }
            challengeStamp = stamp;
        }
        return new ArrayList<>(currentChallenges);
    }

    private static List<QuestEventChallenge> currentChallenges;
    private static int challengeStamp = -1;

    /** Cada cuantas victorias se abre un desafio nuevo. */
    public static int winsPerChallenge() {
        final QuestController q = FModel.getQuest();
        return q == null ? 0 : q.getTurnsToUnlockChallenge();
    }

    /** Cuantos desafios llevas jugados. */
    public static int challengesPlayed() {
        final QuestController q = FModel.getQuest();
        return q == null ? 0 : q.getAchievements().getChallengesPlayed();
    }

    /**
     * Cuantas victorias faltan para el siguiente desafio.
     *
     * <p>Es la misma cuenta que hace {@code regenerateChallenges}, al reves:
     * uno cada {@code winsPerChallenge()} victorias, menos los que ya has
     * jugado. Sin esto la pantalla solo puede decir "no hay ninguno", que no
     * explica nada.
     */
    public static int winsToNextChallenge() {
        final int every = winsPerChallenge();
        if (every <= 0) {
            return 0;
        }
        final int earned = wins() / every - challengesPlayed();
        if (earned > 0) {
            return 0;
        }
        final int needed = (challengesPlayed() + 1) * every;
        return Math.max(0, needed - wins());
    }

    /** Olvida los desafios de hoy. */
    public static void forgetChallenges() {
        currentChallenges = null;
        challengeStamp = -1;
    }
}
