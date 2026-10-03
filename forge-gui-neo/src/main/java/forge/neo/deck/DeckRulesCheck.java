package forge.neo.deck;

import java.util.List;
import java.util.Set;

import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.ascent.AscentRelics;
import forge.neo.match.NeoFormat;

/**
 * Comprueba que el editor aplica de verdad las reglas de construccion.
 *
 * <p>Existe porque estas reglas <b>no se ven en una captura</b>: que no te deje
 * meter dos copias en Commander, o una carta fuera de la identidad de color de
 * tu comandante, solo se comprueba intentandolo. Es la unica forma de validar
 * el deck builder sin sentarse a montar un mazo a mano.
 *
 * <p>Se ejecuta con {@code run.cmd deckcheck}.
 */
public final class DeckRulesCheck {

    private DeckRulesCheck() {
    }

    private static int passed;
    private static int failed;

    public static void run() {
        passed = 0;
        failed = 0;

        commanderIsSingleton();
        commanderColourIdentity();
        wildColourCompanion();
        basicLandsAreUnlimited();
        constructedAllowsFour();
        sideboardCountsTowardsTheLimit();
        constructedIsSixtyAndHasNoCommander();
        deckProblemsAreTranslated();
        changingCommanderFindsIllegalCards();
        searchIsFastEnough();
        fineFiltersNarrow();
        rulesTextSearchFinds();
        deckStatsAddUp();
        cardTextFollowsTheLanguage();
        searchFindsTranslatedNames();
        interfaceTextIsTranslated();
        importFindsTheCommander();
        importKeepsWhatDoesNotFit();
        decksCanBeDeleted();
        renamingDoesNotLeaveACopy();
        collectionsKeepDecksApart();
        otherFormatsFilterThePool();
        generatesADeckForTheCommander();
        generatedDeckSkipsWhatIsAlreadyInTheDeck();
        generatedDeckFitsTheCommandZone();
        generatesARandomOpponentDeck();
        adventureIgnoresTheBanList();
        newestFirstOrdersByAcquisition();
        catalogueSorts();
        collectionCountAndUsedUp();
        oathbreakerHasTwoSlots();
        oathbreakerDoesNotChangeCommander();
        companionGoesToTheSideboard();
        foilAllAtOnce();
        // La ultima: mete las reliquias de Ascenso en el catalogo del motor y
        // ya no las saca. Ver su javadoc.
        ascentCardsStayOutOfTheCatalogue();

        System.out.println();
        System.out.printf("  %d comprobaciones OK, %d fallos%n", passed, failed);
        if (failed > 0) {
            System.out.println("  *** HAY FALLOS ***");
        }
    }

    // ---------------------------------------------------------------

    /**
     * Las cartas se leen en el idioma elegido.
     *
     * <p>Forge trae los nombres traducidos y los precarga, pero <b>no los
     * aplica solo</b>: hay que pedirlos. Esto comprueba las dos mitades — que
     * el idioma ha llegado al motor, y que nuestra capa
     * ({@code CardText} / {@code CardTranslation}) devuelve la traduccion.
     *
     * <p>Vale para cualquier idioma, incluido el ingles: ahi lo correcto es que
     * NO cambie nada.
     */
    private static void cardTextFollowsTheLanguage() {
        final String language = forge.neo.NeoLanguage.current();
        // needsTranslation() es privado, asi que se pregunta por el idioma que
        // CardTranslation dice tener cargado: es la misma respuesta.
        final boolean translated =
                !"en-US".equals(forge.util.CardTranslation.getLanguageSelected());
        final String name = forge.util.CardTranslation.getTranslatedName("Sol Ring");
        System.out.printf(java.util.Locale.ROOT,
                "        (idioma %s | Sol Ring -> \"%s\")%n", language, name);

        if (translated) {
            check("Idioma: los nombres de carta llegan traducidos",
                    !"Sol Ring".equals(name) && !name.isBlank());
        } else {
            // En ingles no hay traduccion cargada y todo tiene que pasar tal
            // cual: la capa no puede costar nada ni romper nada.
            check("Idioma en ingles: los nombres pasan tal cual",
                    "Sol Ring".equals(name));
        }
    }

    /**
     * El buscador encuentra por el nombre que se VE.
     *
     * <p>Jugando en castellano la carta pone "Anillo solar" y el catalogo lo
     * ensenya asi, pero el indice guardaba solo el nombre ingles: escribias lo
     * que estabas leyendo y no salia nada. Se comprueban los dos sentidos —
     * el nombre ingles tiene que seguir valiendo siempre, porque es el que se
     * pega desde Moxfield.
     */
    private static void searchFindsTranslatedNames() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.ESTANDAR, "idioma");
        check("Buscador: el nombre ingles siempre encuentra",
                found(editor, "Sol Ring"));

        final String translated = forge.util.CardTranslation.getTranslatedName("Sol Ring");
        if ("Sol Ring".equals(translated)) {
            // En ingles no hay nada mas que comprobar: es el mismo nombre.
            return;
        }
        System.out.printf(java.util.Locale.ROOT,
                "        (buscando \"%s\")%n", translated);
        check("Buscador: el nombre traducido tambien encuentra",
                found(editor, translated));
    }

    /** Si una busqueda devuelve el Sol Ring. */
    private static boolean found(final DeckEditor editor, final String query) {
        for (final PaperCard c : editor.search(query, false, null, 50)) {
            if ("Sol Ring".equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nuestro propio cromo esta traducido, y los dos ficheros van a la par.
     *
     * <p>El riesgo real de una tabla de textos no es que falle al leerla: es
     * que alguien anyada una clave a UN fichero y no al otro. Eso no se ve
     * jugando en castellano y deja la pantalla a medias en ingles, asi que se
     * comparan las dos listas de claves enteras.
     *
     * <p>Y se comprueba el ultimo escalon: una clave que no existe tiene que
     * salir tal cual, para que un hueco se VEA en vez de quedarse en blanco.
     */
    private static void interfaceTextIsTranslated() {
        final java.util.Properties mine = readLang(forge.neo.NeoLanguage.current());
        final java.util.Properties english = readLang("en-US");

        check("Textos: hay fichero del idioma en uso", !mine.isEmpty() || !english.isEmpty());
        check("Textos: hay fichero de respaldo en ingles", !english.isEmpty());

        if (!mine.isEmpty() && mine != english) {
            final java.util.Set<Object> onlyMine = new java.util.TreeSet<>(mine.keySet());
            onlyMine.removeAll(english.keySet());
            // Las tgt.* son el vocabulario de EngineText y en ingles no existen
            // a proposito: su lado ingles es el texto del propio motor. Ver
            // tambien tools/comprobar-idiomas.py.
            onlyMine.removeIf(k -> String.valueOf(k).startsWith("tgt."));
            final java.util.Set<Object> onlyEnglish = new java.util.TreeSet<>(english.keySet());
            onlyEnglish.removeAll(mine.keySet());
            if (!onlyMine.isEmpty() || !onlyEnglish.isEmpty()) {
                System.out.printf(java.util.Locale.ROOT,
                        "        (solo en el idioma: %s | solo en ingles: %s)%n",
                        onlyMine, onlyEnglish);
            }
            check("Textos: las mismas claves en los dos ficheros",
                    onlyMine.isEmpty() && onlyEnglish.isEmpty());
        }

        check("Textos: una clave conocida se traduce",
                !"menu.title".equals(forge.neo.NeoText.get("menu.title")));
        check("Textos: los huecos se rellenan",
                forge.neo.NeoText.get("turn.number", 7).contains("7"));
        check("Textos: una clave que no existe se ve",
                "no.existe.esta.clave".equals(
                        forge.neo.NeoText.get("no.existe.esta.clave")));
    }

    /** Lee un fichero de textos nuestro, o vacio si no hay. */
    private static java.util.Properties readLang(final String id) {
        final java.util.Properties p = new java.util.Properties();
        try (java.io.InputStream in = DeckRulesCheck.class
                .getResourceAsStream("/forge/neo/lang/neo-" + id + ".properties")) {
            if (in != null) {
                p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            }
        } catch (final java.io.IOException e) {
            System.err.println("[neo] no se ha podido leer neo-" + id + ".properties: " + e);
        }
        return p;
    }

    /**
     * Los filtros finos tienen que RECORTAR, y recortar lo que dicen.
     *
     * <p>Un filtro que no filtra no se distingue de uno que si mientras no te
     * fijas en el numero, y un filtro que filtra de mas te deja mirando un
     * catalogo vacio sin saber por que.
     */
    private static void fineFiltersNarrow() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.ESTANDAR, "filtros");

        final int all = editor.find("", false, null, 5000).total;

        // Rareza: solo miticas.
        final DeckEditor.SearchResult mythic = editor.find("", false,
                c -> c.getRarity() == forge.card.CardRarity.MythicRare, 5000);
        boolean onlyMythic = true;
        for (final PaperCard c : mythic.cards) {
            onlyMythic &= c.getRarity() == forge.card.CardRarity.MythicRare;
        }
        check("Filtro de rareza: recorta", mythic.total > 0 && mythic.total < all);
        check("Filtro de rareza: y todas son miticas", onlyMythic);

        // Coste: solo las de coste 1.
        final DeckEditor.SearchResult cheap = editor.find("", false,
                c -> c.getRules().getManaCost().getCMC() == 1, 5000);
        boolean allOne = true;
        for (final PaperCard c : cheap.cards) {
            allOne &= c.getRules().getManaCost().getCMC() == 1;
        }
        check("Filtro de coste: recorta", cheap.total > 0 && cheap.total < all);
        check("Filtro de coste: y todas cuestan 1", allOne);

        // Los dos a la vez recortan mas que cada uno por su cuenta.
        final int both = editor.find("", false,
                c -> c.getRarity() == forge.card.CardRarity.MythicRare
                        && c.getRules().getManaCost().getCMC() == 1, 5000).total;
        check("Filtros combinados: recortan mas que cada uno",
                both > 0 && both < mythic.total && both < cheap.total);
        System.out.printf(java.util.Locale.ROOT,
                "        (catalogo %d | miticas %d | coste 1 %d | ambas %d)%n",
                all, mythic.total, cheap.total, both);
    }

    /**
     * Buscar en el texto de reglas encuentra lo que el nombre no.
     *
     * <p>Es la diferencia entre buscar una carta que ya conoces y buscar una
     * carta que HAGA algo, que es lo que se pregunta montando un mazo.
     */
    private static void rulesTextSearchFinds() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.ESTANDAR, "texto");

        final long t0 = System.currentTimeMillis();
        final int byName = editor.find("flying", false, null, 5000, false).total;
        final int byText = editor.find("flying", false, null, 5000, true).total;
        final long ms = System.currentTimeMillis() - t0;

        System.out.printf(java.util.Locale.ROOT,
                "        (\"flying\": %d por nombre y tipo, %d mirando el texto, %d ms)%n",
                byName, byText, ms);
        check("Texto de reglas: encuentra MUCHAS mas que el nombre", byText > byName * 10);
        // Nunca menos: la busqueda por texto incluye la del nombre.
        check("Texto de reglas: no pierde ninguna de las de antes", byText >= byName);
        check("Texto de reglas: sigue siendo instantanea", ms < 3000);
    }

    /**
     * Las estadisticas del mazo tienen que cuadrar con el mazo.
     *
     * <p>Un numero mal en un panel de estadisticas no se nota: parece un dato.
     * Aqui se monta un mazo del que se sabe la respuesta de antemano.
     */
    private static void deckStatsAddUp() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.ESTANDAR, "numeros");

        // 4 Lightning Bolt ({R}) y 10 Mountain: 4 simbolos rojos, 10 tierras,
        // y coste medio 1 (las tierras no cuentan).
        editor.add(card("Lightning Bolt"), 4);
        editor.add(card("Mountain"), 10);

        final int[] pips = editor.colourPips();
        check("Estadisticas: 4 simbolos rojos", pips[3] == 4);
        check("Estadisticas: y ninguno de los otros colores",
                pips[0] == 0 && pips[1] == 0 && pips[2] == 0 && pips[4] == 0);
        check("Estadisticas: 10 tierras", editor.landCount() == 10);
        check("Estadisticas: coste medio 1 (sin contar tierras)",
                Math.abs(editor.averageCmc() - 1.0) < 0.001);

        // Una carta con DOS simbolos del mismo color cuenta dos veces: es todo
        // el sentido de contar simbolos en vez de cartas.
        final PaperCard twoRed = card("Goblin Chainwhirler");
        if (twoRed != null) {
            final int before = editor.colourPips()[3];
            editor.add(twoRed, 1);
            final int after = editor.colourPips()[3];
            System.out.printf(java.util.Locale.ROOT,
                    "        (Goblin Chainwhirler suma %d simbolos rojos)%n", after - before);
            check("Estadisticas: una carta de {R}{R}{R} suma 3, no 1", after - before == 3);
        }
    }

    /**
     * "Todas foil" (Discord, 03-10-2026): todo el mazo de golpe, comandante
     * incluido, y vuelta atras; y sin cambiar ni la edicion ni el numero de
     * copias de nada.
     */
    private static void foilAllAtOnce() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "foil");
        editor.setCommander(card("Krenko, Mob Boss"));
        editor.add(card("Lightning Bolt"), 1);
        editor.add(card("Mountain"), 5);
        final int before = editor.getDeck().getMain().countAll();
        check("Todas foil: un mazo nuevo no es todo foil", !editor.isAllFoil());
        final int changed = editor.setAllFoil(true);
        check("Todas foil: cambian las 7 copias (" + changed + ")", changed == 7);
        check("Todas foil: ahora lo es entero, comandante incluido", editor.isAllFoil());
        boolean commanderFoil = true;
        for (final java.util.Map.Entry<PaperCard, Integer> e : editor.getDeck().get(forge.deck.DeckSection.Commander)) {
            commanderFoil &= e.getKey().isFoil();
        }
        check("Todas foil: el comandante tambien", commanderFoil);
        check("Todas foil: las mismas cartas en el principal", editor.getDeck().getMain().countAll() == before);
        editor.setAllFoil(false);
        boolean anyFoil = false;
        for (final java.util.Map.Entry<PaperCard, Integer> e : editor.getDeck().getMain()) {
            anyFoil |= e.getKey().isFoil();
        }
        check("Todas foil: quitarlo las deja normales otra vez", !anyFoil && !editor.isAllFoil());

        // Quest y aventura (Ana, 03-10-2026): "solo se ponga foil si tienes
        // foil, sino no". Tienes el Rayo normal y el Shock foil; de la Montana,
        // ninguna foil. Las basicas son la trampa: el contexto de la aventura
        // contesta null ("cualquier arte") y eso no puede valer para el brillo.
        final PaperCard bolt = card("Lightning Bolt");
        final PaperCard shock = card("Shock");
        final PaperCard mountain = card("Mountain");
        final DeckEditor quest = new DeckEditor(new OwnedPrintings(java.util.Map.of(
                bolt.getName(), List.of(bolt),
                shock.getName(), List.of(shock, shock.getFoiled()))),
                new Deck("coleccion"));
        quest.add(bolt, 1);
        quest.add(shock, 1);
        quest.add(mountain, 3);
        check("Todas foil en tu coleccion: es limitado", quest.isLimited());
        check("Todas foil en tu coleccion: el mazo tiene las 5 cartas de la prueba",
                quest.getDeck().getMain().countAll() == 5);
        final int owned = quest.setAllFoil(true);
        int foils = 0;
        boolean boltFoil = false;
        boolean mountainFoil = false;
        for (final java.util.Map.Entry<PaperCard, Integer> c : quest.getDeck().getMain()) {
            if (c.getKey().isFoil()) {
                foils += c.getValue();
                boltFoil |= c.getKey().getName().equals(bolt.getName());
                mountainFoil |= c.getKey().getName().equals(mountain.getName());
            }
        }
        check("Todas foil en tu coleccion: solo el Shock, que lo tienes foil (" + owned + ")",
                owned == 1 && foils == 1);
        check("Todas foil en tu coleccion: el Rayo normal se queda normal", !boltFoil);
        check("Todas foil en tu coleccion: la basica tampoco, aunque su arte sea libre", !mountainFoil);
        check("Foil de una carta: la misma regla (Shock si, Rayo y Montana no)",
                !quest.canFoil(bolt) && !quest.canFoil(mountain)
                        && editor.canFoil(mountain));
    }

    /** Una coleccion con los artes que has abierto, y las basicas libres (como la aventura). */
    private static final class OwnedPrintings implements DeckContext {

        private final java.util.Map<String, List<PaperCard>> printings;

        OwnedPrintings(final java.util.Map<String, List<PaperCard>> printings) {
            this.printings = printings;
        }

        @Override
        public String getLabel() {
            return "Quest";
        }

        @Override
        public forge.deck.DeckFormat deckFormat() {
            return forge.game.GameType.Constructed.getDeckFormat();
        }

        @Override
        public forge.util.storage.IStorage<Deck> storage() {
            return new forge.util.storage.StorageBase<>("quest", "quest", new java.util.HashMap<>());
        }

        @Override
        public List<PaperCard> pool() {
            final List<PaperCard> out = new java.util.ArrayList<>();
            printings.values().forEach(out::addAll);
            return out;
        }

        @Override
        public int owned(final PaperCard card) {
            return Integer.MAX_VALUE;
        }

        @Override
        public List<PaperCard> printingsOf(final PaperCard card) {
            if (card.getRules().getType().isBasicLand()) {
                return null;
            }
            return printings.getOrDefault(card.getName(), List.of());
        }
    }

    /** En Commander no puede haber dos copias de nada que no sea tierra basica. */
    private static void commanderIsSingleton() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        final PaperCard solRing = card("Sol Ring");

        check("Commander: la primera copia entra", editor.add(solRing, 1) == 1);
        check("Commander: la segunda NO entra", editor.add(solRing, 1) == 0);
        check("Commander: sigue habiendo una sola", editor.countOf(solRing) == 1);
        check("Commander: y lo explica",
                editor.rejectionReason(solRing) != null);
    }

    /** Con comandante puesto, lo que no encaje en su identidad se rechaza. */
    private static void commanderColourIdentity() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        // Un comandante mono-rojo: nada azul deberia poder entrar.
        check("Commander: se acepta un comandante legal",
                editor.setCommander(card("Krenko, Mob Boss")));

        check("Commander: una carta roja entra",
                editor.add(card("Lightning Bolt"), 1) == 1);
        check("Commander: una carta azul NO entra",
                editor.add(card("Counterspell"), 1) == 0);
        // Contra el texto del idioma en uso, no contra el castellano: la
        // bateria se pasa en el idioma que tenga puesto el jugador.
        check("Commander: y dice que es por la identidad de color",
                String.valueOf(editor.rejectionReason(card("Counterspell"))).equals(
                        forge.neo.NeoText.get("reject.identity",
                                forge.neo.card.CardText.nameOf(card("Counterspell")))));
    }

    /**
     * Clara Oswald con un Doctor: "choose a color" le da al mazo UN color mas,
     * y el motor lo acepta al guardar. El editor no lo sabia y no dejaba
     * guardar (itch.io, 30-09-2026).
     */
    private static void wildColourCompanion() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        check("Clara: entra el Doctor (UR)", editor.setCommander(card("The Twelfth Doctor")));
        check("Clara: entra Clara como companera", editor.setCommander(card("Clara Oswald")));
        check("Clara: son dos comandantes", editor.commanders().size() == 2);
        check("Clara: una verde entra (el color comodin)",
                editor.add(card("Llanowar Elves"), 1) == 1);
        check("Clara: otra verde tambien", editor.add(card("Growth Spiral"), 1) == 1);
        check("Clara: pero una negra ya NO (el comodin es uno)",
                editor.add(card("Doom Blade"), 1) == 0);
        check("Clara: el editor no marca nada", editor.illegalCards().isEmpty());
        final String engine = forge.deck.DeckFormat.Commander.getDeckConformanceProblem(editor.getDeck());
        check("Clara: y el motor tampoco se queja de la identidad",
                engine == null || !engine.contains("color identity"));
    }

    /** Las tierras basicas no tienen limite en ningun formato. */
    private static void basicLandsAreUnlimited() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        check("Commander: 30 Montanyas entran aunque sea singleton",
                editor.add(card("Mountain"), 30) == 30);
    }

    /** En Estandar caben cuatro copias, la quinta no. */
    private static void constructedAllowsFour() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.ESTANDAR, "prueba");
        final PaperCard c = card("Llanowar Elves");
        check("Estandar: entran 4 copias", editor.add(c, 4) == 4);
        check("Estandar: la quinta no", editor.add(c, 1) == 0);
        check("Estandar: pedir 10 mete solo las que caben",
                DeckEditor.createNew(NeoFormat.ESTANDAR, "x").add(c, 10) == 4);
    }

    /**
     * El banquillo cuenta para el limite de copias. Y otra impresion tambien.
     *
     * <p>Las dos cosas salieron del mismo fallo, jugando: un preconstruido de
     * la aventura trae tres Experimental Frenzy en un banquillo que ninguna
     * pantalla ensenya, el editor dejaba meter tres mas en el principal, y el
     * mazo se quedaba con seis y <b>sin poder jugarse</b> — con solo tres a la
     * vista.
     *
     * <p>La causa es que se contaba por impresion y solo en el principal,
     * mientras {@code DeckFormat.getDeckConformanceProblem} agrupa por
     * <b>nombre</b> sobre {@code getAllCardsInASinglePool()}, que suma el
     * banquillo. Aqui se comprueba la parte que no depende de la aventura: en
     * un mazo cualquiera de Estandar, cuatro copias en el banquillo dejan sitio
     * para cero en el principal.
     *
     * <p>Y que quitarlas empiece por el banquillo, que es lo que hace que el
     * jugador pueda salir del atolladero: si se quitara del principal se le
     * vaciaria justo lo que esta mirando para dejar intacto lo que no puede
     * abrir.
     */
    private static void sideboardCountsTowardsTheLimit() {
        final PaperCard c = card("Llanowar Elves");

        // 4 en el banquillo: en el principal ya no cabe ninguna.
        final Deck withSide = new Deck("banquillo");
        withSide.getOrCreate(forge.deck.DeckSection.Sideboard).add(c, 4);
        final DeckEditor editor = new DeckEditor(NeoFormat.ESTANDAR, withSide);
        check("Estandar: el banquillo cuenta (4 fuera -> el editor cuenta 4)",
                editor.countOf(c) == 4);
        check("Estandar: y con 4 en el banquillo no entra ninguna mas",
                editor.add(c, 1) == 0);

        // Y con 2 + 3 = 5, se marca y se arregla quitando del BANQUILLO.
        final Deck over = new Deck("pasado");
        over.getMain().add(c, 2);
        over.getOrCreate(forge.deck.DeckSection.Sideboard).add(c, 3);
        final DeckEditor fix = new DeckEditor(NeoFormat.ESTANDAR, over);
        check("Estandar: 2 + 3 son 5 copias y se marcan",
                fix.illegalCards().stream().anyMatch(x -> x.getName().equals(c.getName())));
        fix.removeIllegal();
        check("Estandar: al arreglarlo quedan 4", fix.countOf(c) == 4);
        check("Estandar: y las que se quitan salen del banquillo, no del principal",
                over.getMain().count(c) == 2
                        && over.get(forge.deck.DeckSection.Sideboard).count(c) == 2);

        // Otra IMPRESION de la misma carta es la misma carta.
        final List<PaperCard> arts = FModel.getMagicDb().getCommonCards().getAllCards(c);
        if (arts.size() >= 2) {
            final Deck twoArts = new Deck("artes");
            twoArts.getMain().add(arts.get(0), 4);
            final DeckEditor mixed = new DeckEditor(NeoFormat.ESTANDAR, twoArts);
            check("Estandar: 4 de una edicion y el editor cuenta 4 mirando la otra",
                    mixed.countOf(arts.get(1)) == 4);
            check("Estandar: y no deja meter una quinta con otro arte",
                    mixed.add(arts.get(1), 1) == 0);
        }
    }

    /**
     * Estandar es un mazo de 60 cartas y SIN comandante.
     *
     * <p>Las dos mitades hacen falta. Que el minimo sea 60 lo contesta el motor
     * ({@code DeckFormat.Constructed}) y no lo escribimos nosotros, pero si algo
     * de la interfaz lo diera por 40 o por 100 no habria forma de enterarse
     * hasta intentar jugar. Y lo del comandante no es cosmetico: es lo que
     * decide si la pantalla ensenya el boton de elegirlo, la fila de la zona de
     * mando y el marco dorado de la portada.
     */
    private static void constructedIsSixtyAndHasNoCommander() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.ESTANDAR, "sesenta");
        check("Estandar: el formato NO lleva comandante", !editor.usesCommander());
        check("Estandar: y el motor tampoco lo pide",
                !NeoFormat.ESTANDAR.deckFormat().hasCommander());

        // Ni aunque se lo pidas: un legendario no puede ser comandante aqui.
        check("Estandar: no se puede nombrar comandante",
                !editor.setCommander(card("Krenko, Mob Boss"))
                        && editor.commanders().isEmpty());

        final PaperCard land = card("Mountain");
        editor.add(land, 59);
        check("Estandar: con 59 cartas el mazo aun no vale",
                editor.mainCount() == 59 && !editor.isPlayable());
        final String falta = editor.problem();
        check("Estandar: y el motor dice que faltan para 60",
                falta != null && falta.contains("60"));

        editor.add(land, 1);
        check("Estandar: con 60 ya se puede jugar",
                editor.mainCount() == 60 && editor.isPlayable());

        // Y no hay techo por arriba: 60 es un minimo, no una talla.
        editor.add(land, 5);
        check("Estandar: 65 tambien vale (60 es minimo, no maximo)",
                editor.mainCount() == 65 && editor.isPlayable());
    }

    /**
     * Que "que le falta al mazo" salga en el idioma en que se juega.
     *
     * <p>El motor compone esas frases a mano y sin {@code Localizer}, asi que
     * llegan en ingles siempre. Se reconocen desde fuera ({@link DeckProblem}),
     * y lo que importa comprobar son las dos mitades: que lo conocido se
     * traduzca <b>quedandose con el numero</b>, y que lo desconocido pase tal
     * cual — traducir a ciegas una frase que no se ha visto nunca es la forma
     * de acabar diciendo otra cosa.
     */
    private static void deckProblemsAreTranslated() {
        // Se compara con NUESTRO texto del idioma en uso, no con "distinto del
        // ingles": jugando en ingles la frase traducida puede ser identica a
        // la del motor, y la comprobacion salia en rojo sin que nada fallara
        // (28-09-2026, con el juego puesto en ingles).
        final String menos = DeckProblem.translate("should have at least 60 cards");
        check("Problema del mazo: 'al menos 60' se traduce",
                menos.equals(forge.neo.NeoText.get("deck.problem.atLeast", "60")));

        final String mas = DeckProblem.translate("should have no more than 100 cards");
        check("Problema del mazo: 'no mas de 100' se traduce",
                mas.equals(forge.neo.NeoText.get("deck.problem.atMost", "100")));

        final String copias =
                DeckProblem.translate("must not contain more than 4 copies of the card Lightning Bolt");
        check("Problema del mazo: las copias de mas se traducen y dicen la carta",
                copias.equals(forge.neo.NeoText.get("deck.problem.copies", "4", "Lightning Bolt"))
                        && copias.contains("Lightning Bolt"));

        final String id = DeckProblem.translate(
                "contains one or more cards that do not match the commanders color identity:"
                + System.lineSeparator() + "Lightning Bolt");
        check("Problema del mazo: la identidad de color se traduce",
                id.startsWith(forge.neo.NeoText.get("deck.problem.identity")));
        check("Problema del mazo: y NO se come la lista de cartas",
                id.contains("Lightning Bolt"));

        // Las dos de Oathbreaker, que son las que mas se leen ahi: salen en
        // cuanto abres un mazo nuevo y no se van hasta llenar los dos huecos.
        final String sinOath = DeckProblem.translate("is missing an oathbreaker");
        check("Problema del mazo: 'sin oathbreaker' se traduce",
                sinOath.equals(forge.neo.NeoText.get("deck.problem.noOathbreaker")));
        final String sinSpell = DeckProblem.translate("is missing a signature spell");
        check("Problema del mazo: 'sin hechizo insignia' se traduce",
                sinSpell.equals(forge.neo.NeoText.get("deck.problem.noSignature")));

        // Lo que no conocemos, intacto. Ni traducido a medias ni tragado.
        final String raro = "contains the nonexisting card Blorble";
        check("Problema del mazo: lo desconocido pasa tal cual",
                raro.equals(DeckProblem.translate(raro)));
        check("Problema del mazo: null sigue siendo null",
                DeckProblem.translate(null) == null);

        // Y por el camino de verdad, no solo sobre la funcion suelta.
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.ESTANDAR, "idioma-problema");
        final String real = editor.problem();
        check("Problema del mazo: el editor lo devuelve ya traducido",
                real != null && real.equals(forge.neo.NeoText.get("deck.problem.atLeast", "60")));
    }

    /** Cambiar de comandante detecta lo que se ha quedado fuera. */
    private static void changingCommanderFindsIllegalCards() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        editor.setCommander(card("Krenko, Mob Boss"));
        editor.add(card("Lightning Bolt"), 1);
        check("Con el comandante rojo el mazo esta limpio",
                editor.illegalCards().isEmpty());

        // Al cambiar a un comandante verde, el Rayo se queda fuera.
        editor.setCommander(card("Ezuri, Renegade Leader"));
        final List<PaperCard> bad = editor.illegalCards();
        check("Al cambiar de comandante se detecta la carta que ya no cabe",
                bad.size() == 1 && bad.get(0).getName().equals("Lightning Bolt"));
        check("Y se puede quitar", editor.removeIllegal() == 1);
        check("Despues el mazo esta limpio", editor.illegalCards().isEmpty());
    }

    /**
     * El buscador tiene que responder mientras se teclea.
     *
     * <p>El editor viejo de Forge se arrastraba, y es el motivo por el que este
     * trabaja sobre {@code getUniqueCards()} (una impresion por carta) en vez de
     * sobre las 95.000 impresiones. Aqui se mide, para que no se degrade sin que
     * nadie se entere.
     */
    private static void searchIsFastEnough() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        editor.setCommander(card("Krenko, Mob Boss"));

        final String[] queries = {"s", "so", "sol", "sol ", "sol r", "goblin", "lightning"};
        long worst = 0;
        for (final String q : queries) {
            final long t0 = System.nanoTime();
            editor.search(q, true, null, 60);
            final long ms = (System.nanoTime() - t0) / 1_000_000;
            worst = Math.max(worst, ms);
            System.out.printf("       busqueda \"%s\": %d ms%n", q, ms);
        }
        check("El buscador responde en menos de 150 ms por tecla", worst < 150);

        final long t0 = System.nanoTime();
        final int printings = editor.printingsOf(card("Sol Ring")).size();
        final long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("       %d ediciones de Sol Ring en %d ms%n", printings, ms);
        check("Las ediciones de UNA carta se traen al instante", ms < 50 && printings > 1);
    }

    /**
     * De donde saca el importador al comandante.
     *
     * <p>Las dos convenciones que se usan por ahi, y las dos han fallado:
     *
     * <ul>
     *   <li><b>Un bloque suelto al final</b>, sin etiqueta (Archidekt, Arena).
     *       Eso ya lo traducia {@code withSectionHeaders}.</li>
     *   <li><b>{@code SIDEBOARD:}</b>, que es lo que exporta Moxfield — y ahi
     *       es donde mete a los comandantes. Ese encabezado <b>si</b> lo
     *       reconoce Forge, asi que el traductor se apartaba y el mazo entraba
     *       SIN comandante: se quedaba el de antes. Con un comandante viejo
     *       mono blanco y una lista de cuatro colores, se rechazaba media
     *       lista "por identidad de color" y nada apuntaba al culpable.</li>
     * </ul>
     */
    private static void importFindsTheCommander() {
        final String moxfield = String.join(System.lineSeparator(),
                "1 Sol Ring",
                "1 Counterspell",
                "1 Island",
                "",
                "SIDEBOARD:",
                "1 Yarok, the Desecrated");
        final DeckImporter.Result fromSide = DeckImporter.importCommander(moxfield, "prueba");
        System.out.printf(java.util.Locale.ROOT,
                "        (SIDEBOARD: mazo=%s comandantes=%s principal=%d banquillo=%d aceptadas=%d desconocidas=%d %s)%n",
                fromSide.deck != null,
                fromSide.deck == null ? "-" : fromSide.deck.getCommanders(),
                fromSide.deck == null ? -1 : DeckImporter.sectionSize(fromSide.deck, forge.deck.DeckSection.Main),
                fromSide.deck == null ? -1 : DeckImporter.sectionSize(fromSide.deck, forge.deck.DeckSection.Sideboard),
                fromSide.accepted, fromSide.unknown, fromSide.problems);
        check("Importar: el comandante del SIDEBOARD se reconoce",
                fromSide.deck != null && fromSide.deck.getCommanders().size() == 1
                        && fromSide.deck.getCommanders().get(0).getName()
                                .equals("Yarok, the Desecrated"));
        check("Importar: y el resto se queda en el mazo",
                fromSide.deck != null && fromSide.deck.getMain().countAll() == 3);

        final String loose = String.join(System.lineSeparator(),
                "1 Sol Ring",
                "1 Counterspell",
                "",
                "1 Yarok, the Desecrated");
        final DeckImporter.Result fromBlock = DeckImporter.importCommander(loose, "prueba");
        check("Importar: el comandante suelto al final sigue funcionando",
                fromBlock.deck != null && fromBlock.deck.getCommanders().size() == 1);

        // Y un banquillo DE VERDAD (sellado, draft) no se confunde con la zona
        // de mando: si tiene mas de dos cartas, se queda donde esta.
        final String realSide = String.join(System.lineSeparator(),
                "1 Sol Ring",
                "",
                "SIDEBOARD:",
                "1 Island", "1 Forest", "1 Swamp");
        final DeckImporter.Result big = DeckImporter.importCommander(realSide, "prueba");
        check("Importar: un banquillo grande NO se toma por comandante",
                big.deck != null && big.deck.getCommanders().isEmpty());
    }

    /**
     * Una lista de fuera entra ENTERA, quepa o no.
     *
     * <p>Filtrar al anyadir esta bien montando el mazo carta a carta; al pegar
     * una lista es lo contrario de lo que hace falta, porque lo que se queda
     * fuera no se puede ni mirar. Ahora entra todo, se marca, y lo que impide
     * que se cuele es que el mazo no se pueda guardar mientras siga ahi.
     */
    private static void importKeepsWhatDoesNotFit() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        editor.setCommander(card("Krenko, Mob Boss"));

        check("Importar: lo que no cabe entra igual",
                editor.addAnyway(card("Counterspell"), 1) == 1);
        check("Importar: y queda marcado como que no cabe",
                editor.illegalCards().size() == 1);
        check("Importar: mientras siga ahi, el mazo NO se puede guardar",
                editor.blockingProblem() != null);
        check("Importar: ni jugar", !editor.isPlayable());
        check("Importar: y se puede quitar de golpe", editor.removeIllegal() == 1);
        check("Importar: despues ya se puede guardar", editor.blockingProblem() == null);
    }

    /**
     * La papelera del selector de mazos.
     *
     * <p>Borrar es lo unico del selector que <b>no se puede deshacer</b>, y
     * escribe en la MISMA carpeta que la instalacion normal de Forge. Asi que
     * hay que comprobar las dos mitades: que borra de verdad (si no, la
     * papelera es un boton que no hace nada y el mazo reaparece al volver a
     * entrar) y que <b>no</b> se ofrece donde no debe — un preconstruido de
     * Forge se lee de {@code res/} y volveria a salir al arrancar.
     *
     * <p>El mazo de prueba se llama raro a proposito: si algo peta a medias, se
     * ve de un vistazo cual es y que no es del usuario.
     */
    private static void decksCanBeDeleted() {
        final String name = "__neocheck-borrar__";
        final NeoFormat format = NeoFormat.COMMANDER;

        final DeckEditor editor = DeckEditor.createNew(format, name);
        editor.setCommander(card("Krenko, Mob Boss"));
        editor.add(card("Mountain"), 10);
        editor.save();

        Deck saved = null;
        for (final Deck d : format.decks()) {
            if (d.getName().equals(name)) {
                saved = d;
                break;
            }
        }
        check("Papelera: el mazo de prueba se ha guardado", saved != null);
        if (saved == null) {
            return;
        }

        // Un preconstruido que se llama IGUAL que uno tuyo: retocas "Abzan
        // Armor [TDC] [2025]" y lo guardas tal cual, y decks() trae los dos.
        // Hace de preconstruido una copia suelta del mismo mazo — mismo
        // nombre, otro objeto y fuera de tu carpeta —, que es justo lo que
        // llega en ese caso, sin escribir en el disco un fichero con el
        // nombre de un precon de verdad. Preguntando por el nombre salia
        // tuyo, con papelera, y esa papelera borraba TU fichero.
        final Deck twin = new Deck(saved, name);
        check("Papelera: un preconstruido con el nombre de uno tuyo no es tuyo",
                !format.isMine(twin));
        check("Papelera: ni lleva papelera", !format.canDelete(twin));
        check("Papelera: y borrarlo no se lleva el tuyo",
                !format.delete(twin) && format.storage().get(name) == saved);

        check("Papelera: un mazo tuyo se puede borrar", format.canDelete(saved));
        check("Papelera: y se borra de verdad", format.delete(saved));

        boolean stillThere = false;
        for (final Deck d : format.decks()) {
            if (d.getName().equals(name)) {
                stillThere = true;
                break;
            }
        }
        check("Papelera: ya no esta en la lista", !stillThere);

        // Y lo que NO se puede borrar: un preconstruido de Forge.
        Deck precon = null;
        for (final Deck d : format.decks()) {
            if (!format.isMine(d)) {
                precon = d;
                break;
            }
        }
        check("Papelera: un preconstruido de Forge no la lleva",
                precon != null && !format.canDelete(precon));
        check("Papelera: y no se borra ni pidiendoselo",
                precon != null && !format.delete(precon));
    }

    /**
     * Las colecciones de mazos ({@link DeckCollections}, pedidas en itch.io el
     * 27-09-2026) no pierden ni duplican mazos.
     *
     * <p>Lo que se vigila es lo que no se ve en una captura: que editar y
     * renombrar un mazo DENTRO de una coleccion no lo copie a "Mis mazos"
     * (el constructor guardaba siempre en la carpeta del formato), que mover
     * no deje el original detras, y que borrar una coleccion devuelva sus
     * mazos en vez de llevarselos.
     */
    private static void collectionsKeepDecksApart() {
        final NeoFormat format = NeoFormat.ESTANDAR;
        final String col = "__neocheck-coleccion__";
        final String col2 = "__neocheck-coleccion-b__";
        final String a = "__neocheck-col-a__";
        final String b = "__neocheck-col-b__";
        for (final String c : new String[] {col, col2}) {
            if (DeckCollections.storage(format, c) != null) {
                DeckCollections.delete(format, c);
            }
        }
        for (final String n : new String[] {a, b, a + " (2)"}) {
            if (format.storage().contains(n)) {
                format.storage().delete(n);
            }
        }

        check("Colecciones: Estandar las admite", DeckCollections.isSupported(format));
        check("Colecciones: draft no", !DeckCollections.isSupported(NeoFormat.DRAFT));
        check("Colecciones: un nombre con barra no vale",
                DeckCollections.problem(format, "a/b", null) != null);
        check("Colecciones: se crea", DeckCollections.create(format, col)
                && DeckCollections.list(format).contains(col));
        check("Colecciones: el mismo nombre en mayusculas es el mismo",
                DeckCollections.problem(format, col.toUpperCase(java.util.Locale.ROOT), null) != null);

        // Un mazo nuevo montado con la pestanya de la coleccion abierta.
        final DeckEditor inside = DeckEditor.createNew(new CollectionContext(format, col), a);
        inside.add(card("Mountain"), 20);
        inside.save();
        check("Colecciones: el mazo nuevo se guarda en la coleccion",
                DeckCollections.storage(format, col).contains(a));
        check("Colecciones: y NO en Mis mazos", !format.storage().contains(a));

        // Abrirlo y renombrarlo como lo hace el constructor.
        final DeckEditor reopened = DeckEditor.copyOf(new CollectionContext(format, col),
                DeckCollections.storage(format, col).get(a));
        check("Colecciones: al abrirlo se sabe que esta guardado",
                a.equals(reopened.getSavedAs()));
        reopened.setName(b);
        reopened.save();
        check("Colecciones: renombrar dentro lo renombra",
                DeckCollections.storage(format, col).contains(b)
                        && !DeckCollections.storage(format, col).contains(a));
        check("Colecciones: y no aparece ninguna copia en Mis mazos",
                !format.storage().contains(a) && !format.storage().contains(b));

        // Mover a otra coleccion y de vuelta a Mis mazos.
        DeckCollections.create(format, col2);
        final Deck deck = DeckCollections.storage(format, col).get(b);
        check("Colecciones: mover a otra coleccion",
                DeckCollections.move(format, deck, col, true, col2) == null);
        check("Colecciones: y no se queda en la de antes",
                !DeckCollections.storage(format, col).contains(b)
                        && DeckCollections.storage(format, col2).contains(b));
        check("Colecciones: el .dck esta en su carpeta",
                new java.io.File(forge.localinstance.properties.ForgeConstants.DECK_CONSTRUCTED_DIR,
                        col2 + java.io.File.separator + b + ".dck").isFile());

        // Un mazo que no es tuyo se copia; el de origen ni se toca.
        final Deck precon = NeoFormat.precons().isEmpty() ? null : NeoFormat.precons().get(0);
        if (precon != null) {
            check("Colecciones: un preconstruido se copia a la coleccion",
                    DeckCollections.move(format, precon, null, false, col) == null
                            && DeckCollections.storage(format, col).contains(precon.getName()));
            DeckCollections.storage(format, col).delete(precon.getName());
        }

        // Dos con el mismo nombre en el mismo sitio: no se pisa.
        final DeckEditor clash = DeckEditor.createNew(format, b);
        clash.add(card("Mountain"), 20);
        clash.save();
        check("Colecciones: mover encima de otro con el mismo nombre se niega",
                DeckCollections.move(format, DeckCollections.storage(format, col2).get(b),
                        col2, true, null) != null
                        && DeckCollections.storage(format, col2).contains(b));

        // Borrar la coleccion devuelve sus mazos (con " (2)" si choca).
        final int moved = DeckCollections.delete(format, col2);
        check("Colecciones: borrarla devuelve sus mazos a Mis mazos",
                moved == 1 && format.storage().contains(b + " (2)")
                        && format.storage().contains(b));
        check("Colecciones: y la carpeta ya no esta",
                !DeckCollections.list(format).contains(col2));

        // Renombrar la coleccion se lleva sus mazos.
        DeckCollections.storage(format, col).add(new Deck(a));
        check("Colecciones: renombrarla",
                DeckCollections.rename(format, col, col2)
                        && DeckCollections.storage(format, col2).contains(a)
                        && DeckCollections.storage(format, col) == null);

        DeckCollections.delete(format, col2);
        for (final String n : new String[] {a, b, b + " (2)"}) {
            if (format.storage().contains(n)) {
                format.storage().delete(n);
            }
        }
        check("Colecciones: la prueba no deja nada detras",
                !DeckCollections.list(format).contains(col) && !DeckCollections.list(format).contains(col2)
                        && !format.storage().contains(a) && !format.storage().contains(b)
                        && !format.storage().contains(b + " (2)"));
    }

    /**
     * Renombrar renombra; no deja una copia con el nombre viejo.
     *
     * <p>Guardar es {@code storage().add(deck)}, asi que con el nombre nuevo se
     * escribia un fichero nuevo y el viejo se quedaba donde estaba: abrias un
     * mazo, lo renombrabas, lo guardabas y te encontrabas <b>dos</b>. Y no se
     * ve en una captura — el mazo nuevo sale perfecto, el que sobra esta en la
     * otra pantalla.
     *
     * <p>Se comprueba tambien lo que abre este arreglo: que renombrar hacia el
     * nombre de OTRO mazo tuyo avise antes de pisarlo, y que guardar encima de
     * ti mismo NO avise, que es lo normal.
     */
    private static void renamingDoesNotLeaveACopy() {
        final NeoFormat format = NeoFormat.COMMANDER;
        final String first = "__neocheck-nombre-a__";
        final String second = "__neocheck-nombre-b__";
        final String other = "__neocheck-otro__";
        for (final String n : new String[] {first, second, other}) {
            if (format.storage().contains(n)) {
                format.storage().delete(n);
            }
        }

        final DeckEditor editor = DeckEditor.createNew(format, first);
        editor.setCommander(card("Krenko, Mob Boss"));
        editor.add(card("Mountain"), 10);
        editor.save();
        check("Renombrar: el mazo se guarda con su nombre", format.storage().contains(first));

        // Y ahora lo de siempre: abrirlo como lo abre el deck builder.
        final DeckEditor reopened = DeckEditor.copyOf(format, format.storage().get(first));
        check("Renombrar: al abrirlo se sabe con que nombre esta guardado",
                first.equals(reopened.getSavedAs()));
        reopened.setName(second);
        reopened.save();
        check("Renombrar: aparece con el nombre nuevo", format.storage().contains(second));
        check("Renombrar: y NO se queda una copia con el viejo",
                !format.storage().contains(first));

        // Guardar otra vez sin tocar el nombre no puede borrar nada.
        reopened.save();
        check("Renombrar: guardar dos veces seguidas lo deja donde esta",
                format.storage().contains(second));

        // Pisar OTRO mazo si tiene que avisar; pisarte a ti mismo, no.
        final DeckEditor third = DeckEditor.createNew(format, other);
        third.add(card("Mountain"), 10);
        third.save();
        check("Renombrar: avisa si el nombre es el de otro mazo tuyo",
                reopened.wouldOverwriteAnother(other));
        check("Renombrar: y NO avisa al guardarte encima de ti mismo",
                !reopened.wouldOverwriteAnother(second));

        // Y el caso feo: un mazo NUEVO que por casualidad se llama igual que
        // uno guardado. No esta guardado, asi que renombrarlo no puede borrar
        // el otro — que el jugador ni ha abierto.
        final DeckEditor sameName = DeckEditor.createNew(format, other);
        check("Renombrar: un mazo nuevo no se adueña de un nombre que ya existe",
                sameName.getSavedAs() == null);
        sameName.add(card("Mountain"), 5);
        sameName.setName("__neocheck-nombre-c__");
        sameName.save();
        check("Renombrar: y al renombrarlo el otro sigue ahi",
                format.storage().contains(other));
        format.storage().delete("__neocheck-nombre-c__");

        format.storage().delete(second);
        format.storage().delete(other);
        check("Renombrar: la prueba no deja nada detras",
                !format.storage().contains(first) && !format.storage().contains(second)
                        && !format.storage().contains(other));
    }

    /**
     * El pozo de "Otros formatos" (la auditoría del motor, apartado C1) se aplica de verdad al
     * catalogo, no solo al mazo guardado.
     *
     * <p><b>Pauper es el caso de prueba de §1.6</b>: es el mas estricto y el
     * que antes canta. La comprobacion no puede limitarse a "el catalogo
     * cumple {@code getFilterRules()}" — eso seria circular, porque es
     * literalmente como se construyo el catalogo. Hace falta una lista
     * independiente: cartas que NUNCA fueron comunes (no pueden aparecer) y
     * cartas comunes de sobra conocidas (tienen que aparecer).
     */
    private static void otherFormatsFilterThePool() {
        final DeckEditor pauper = new DeckEditor(NeoFormat.PAUPER, new Deck("__neocheck-pauper__"));
        final List<PaperCard> catalogue = pauper.find("", false, null, 200_000, false).cards;

        check("Pauper: el catalogo no esta vacio", !catalogue.isEmpty());

        final java.util.Set<String> names = new java.util.HashSet<>();
        for (final PaperCard c : catalogue) {
            names.add(c.getName());
        }
        // Nunca se imprimieron como comunes: si aparecen, el filtro esta mal.
        // (Sol Ring SI es Pauper-legal de verdad — se imprimio comun en varios
        // precons de Commander — asi que no vale como ejemplo de lo contrario.)
        for (final String rare : new String[] {"Black Lotus", "Time Walk",
                "Ragavan, Nimble Pilferer", "Tarmogoyf"}) {
            check("Pauper: " + rare + " NO esta en el catalogo (nunca fue comun)",
                    !names.contains(rare));
        }
        // Comunes de sobra conocidas: si faltan, el filtro recorto de mas.
        for (final String common : new String[] {"Lightning Bolt", "Counterspell",
                "Llanowar Elves", "Plains"}) {
            check("Pauper: " + common + " SI esta en el catalogo", names.contains(common));
        }

        // Y una pasada completa: NINGUNA carta del catalogo deja de cumplir
        // lo que dice Pauper.txt ("Rarities:L, C"). Confirma que el pozo
        // llega de verdad hasta el indice y no solo hasta
        // rejectionReason/conformanceProblem.
        //
        // OJO: "L" es CardRarity.BasicLand, y no es lo mismo que
        // CardType.isBasicLand(). Forge marca con esa rareza las tierras NO
        // basicas que ocupan el hueco de tierra basica en un sobre — las
        // "surveil lands" de Duskmourn, las de las colaboraciones (Marvel,
        // Avatar, Tortugas Ninja, Final Fantasy)... — que estan en el pozo de
        // Pauper por eso, no porque sean comunes. Medir por TIPO de carta en
        // vez de por RAREZA de impresion habria marcado 55 aciertos como
        // fallos.
        final forge.card.CardDb db = forge.StaticData.instance().getCommonCards();
        final java.util.function.Predicate<? super PaperCard> everCommon =
                db.wasPrintedAtRarity(forge.card.CardRarity.Common);
        final java.util.function.Predicate<? super PaperCard> everBasicLandSlot =
                db.wasPrintedAtRarity(forge.card.CardRarity.BasicLand);
        int notCommon = 0;
        for (final PaperCard c : catalogue) {
            if (!everCommon.test(c) && !everBasicLandSlot.test(c)) {
                notCommon++;
                if (notCommon <= 20) {
                    System.out.println("        NO COMUN: " + c.getName() + " [" + c.getEdition() + "]");
                }
            }
        }
        System.out.printf(java.util.Locale.ROOT,
                "        (Pauper: %d cartas en el catalogo, %d que nunca fueron comunes)%n",
                catalogue.size(), notCommon);
        check("Pauper: cero cartas en el catalogo que no sean comunes", notCommon == 0);

        // Y otro formato con pozo (Modern) rechaza una carta que Pauper SI
        // deja: confirma que cada NeoFormat lee SU PROPIO GameFormat y no
        // comparten uno cacheado por error.
        final DeckEditor modern = new DeckEditor(NeoFormat.MODERN, new Deck("__neocheck-modern__"));
        check("Modern: acepta Ragavan (formato mas permisivo que Pauper)",
                modern.rejectionReason(card("Ragavan, Nimble Pilferer")) == null);
        check("Pauper: rechaza Ragavan (no es comun)",
                pauper.rejectionReason(card("Ragavan, Nimble Pilferer")) != null);
    }

    /**
     * "Generar mazo" (la auditoría del motor, apartado B6): {@code DeckEditor.generateForCommander}.
     *
     * <p>No comprueba que el mazo generado sea BUENO — eso es cosa del motor,
     * que ya trae sus propios mazos genéticos de IA — sino que lo que
     * {@code DeckgenUtil} devuelve entra por el camino normal del editor
     * ({@code addAnyway} + {@code illegalCards}) y no se cuela nada fuera de
     * la identidad de color ni por encima del límite de copias.
     */
    private static void generatesADeckForTheCommander() {
        final DeckEditor sinComandante = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba-gen-0");
        check("Generar: sin comandante no hace nada",
                sinComandante.generateForCommander() == -1);

        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba-gen-1");
        check("Generar: se acepta el comandante",
                editor.setCommander(card("Krenko, Mob Boss")));

        final int added = editor.generateForCommander();
        System.out.printf(java.util.Locale.ROOT,
                "        (Generar: %d cartas para Krenko, Mob Boss)%n", added);
        check("Generar: mete un montón de cartas", added > 50);
        check("Generar: el principal no se pasa del hueco de Commander",
                editor.mainCount() <= 99);
        check("Generar: nada se cuela fuera de la identidad de color ni de copias",
                editor.illegalCards().isEmpty());

        // Generar dos veces SUSTITUYE, no amontona: si sumara, el segundo
        // mazo tendria hasta 198 cartas en vez de <=99.
        final int addedAgain = editor.generateForCommander();
        check("Generar otra vez: sustituye el mazo, no lo amontona",
                editor.mainCount() <= 99 && addedAgain > 0);
    }

    /**
     * "Generar mazo" con el hechizo insignia o el companero ya puestos.
     *
     * <p>El generador de Forge solo recibe el comandante: el hechizo insignia
     * del jugador (Oathbreaker) y el companero del banquillo no los conoce, y
     * los metia tambien en el principal. Medido el 02-10-2026: 18 de 30 mazos
     * de Arlinn Kord traian su Moonmist y 18 de 30 de Runo Stromkirk su
     * Gyruda, y salian ya sin poderse guardar (<i>"must not contain more than
     * 1 copies"</i>). Lo limpia {@code GeneratedDecks.fixCopyLimits} con lo
     * que el mazo lleva fuera del principal.
     *
     * <p>Primero a mano, que no depende del azar: con el hechizo metido en el
     * principal el motor rechaza el mazo, y el arreglo lo cambia por una
     * basica. Despues por el editor de verdad, seis veces cada uno: con 3 de
     * cada 5 mazos trayendolo, seis seguidos limpios sin el arreglo saldrian
     * un 0,4% de las veces.
     */
    private static void generatedDeckSkipsWhatIsAlreadyInTheDeck() {
        final PaperCard arlinn = card("Arlinn Kord");
        // El hechizo que mas se juega con ella: el que mas le sale al generador.
        final PaperCard spell = mostPlayedWith("Oathbreaker", arlinn,
                c -> c.getRules().canBeSignatureSpell());
        check("Generar con hechizo insignia: la matriz de Oathbreaker le da hechizos a "
                + arlinn.getName() + (spell == null ? "" : " (" + spell.getName() + ")"), spell != null);
        if (spell == null) {
            return;
        }

        // 1. A mano.
        final DeckEditor byHand = DeckEditor.createNew(NeoFormat.OATHBREAKER, "prueba-gen-insignia");
        byHand.setCommander(arlinn);
        byHand.setCommander(spell);
        byHand.add(card("Forest"), 57);
        byHand.addAnyway(spell, 1);
        final Deck deck = byHand.getDeck();
        final String before = byHand.problem();
        check("Generar con hechizo insignia: el motor rechaza el hechizo repetido en el principal ("
                + before + ")", before != null);
        final int swapped = GeneratedDecks.fixCopyLimits(deck, byHand.deckFormat(),
                deck.get(forge.deck.DeckSection.Commander));
        final String after = byHand.problem();
        check("Generar con hechizo insignia: sale del principal por una basica y el mazo es legal"
                        + (after == null ? "" : " -> " + after),
                swapped == 1 && deck.getMain().countByName(spell.getName()) == 0
                        && deck.getMain().countAll() == 58 && after == null);
        check("Generar con hechizo insignia: y el de la zona de mando sigue puesto",
                spell.equals(byHand.signatureSpell()));

        // 2. Por el editor, como el boton.
        generatesWithout(NeoFormat.OATHBREAKER, arlinn, spell, null, spell);

        final PaperCard runo = card("Runo Stromkirk");
        final PaperCard gyruda = card("Gyruda, Doom of Depths");
        check("Generar con companero: la matriz de Commander trae a Gyruda para Runo Stromkirk",
                mostPlayedWith("Commander", runo, c -> c.getName().equals(gyruda.getName())) != null);
        generatesWithout(NeoFormat.COMMANDER, runo, null, gyruda, gyruda);
    }

    /** Seis mazos generados con esa zona de mando y ese companero: ninguno repite {@code watch}. */
    private static void generatesWithout(final NeoFormat format, final PaperCard head,
            final PaperCard signature, final PaperCard companion, final PaperCard watch) {
        int repeated = 0;
        int bad = 0;
        String last = null;
        for (int i = 0; i < 6; i++) {
            final DeckEditor editor = DeckEditor.createNew(format, "prueba-gen-fuera-" + i);
            editor.setCommander(head);
            if (signature != null) {
                editor.setCommander(signature);
            }
            if (companion != null) {
                editor.setCompanion(companion);
            }
            if (editor.generateForCommander() < 0) {
                bad++;
                last = "el generador no ha devuelto nada";
                continue;
            }
            if (editor.getDeck().getMain().countByName(watch.getName()) > 0) {
                repeated++;
            }
            final String problem = editor.problem();
            if (problem != null) {
                bad++;
                last = problem;
            }
        }
        check("Generar " + format + " (" + head.getName() + " + " + watch.getName()
                        + "): seis mazos sin repetirlo y legales"
                        + (last == null ? "" : " -> " + repeated + " lo repiten; " + last),
                repeated == 0 && bad == 0);
    }

    /** La carta de la matriz del generador que mas se juega con ese comandante y cumple {@code which}. */
    private static PaperCard mostPlayedWith(final String matrix, final PaperCard commander,
            final java.util.function.Predicate<PaperCard> which) {
        final java.util.Map<String, List<java.util.Map.Entry<PaperCard, Integer>>> pools =
                forge.deck.CardRelationMatrixGenerator.cardPools.get(matrix);
        final List<java.util.Map.Entry<PaperCard, Integer>> pool =
                pools == null ? null : pools.get(commander.getName());
        if (pool == null) {
            return null;
        }
        PaperCard best = null;
        int most = 0;
        for (final java.util.Map.Entry<PaperCard, Integer> e : pool) {
            if (which.test(e.getKey()) && e.getValue() > most) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        return best;
    }

    /**
     * "Generar mazo" con un comandante que admite companero de mando (Partner,
     * Background, Doctor...).
     *
     * <p>El generador de Forge le busca <b>siempre</b> un companero suyo, al
     * azar, y monta 98 cartas para la identidad de los dos; el editor tiraba
     * ese companero y se quedaba el principal. Medido el 02-10-2026: Thrasios
     * solo, <b>20 de 20</b> mazos con 7-30 cartas fuera de su identidad y una
     * carta de menos (98 + 1); Thrasios con Tymna, <b>14 de 30</b> fuera de
     * identidad (cuando el generador le elegia a Rograkh o a Kraum). Ahora se
     * monta para la zona de mando del jugador:
     * {@link GeneratedDecks#forCommandZone}.
     *
     * <p>Seis mazos de cada. Sin el arreglo, Thrasios solo sale en rojo
     * siempre, y la pareja sale seis veces limpia un 2% de las veces. Y lo
     * mismo en Tiny Leaders, que el generador monta con la misma matriz y el
     * mismo companero al azar.
     */
    private static void generatedDeckFitsTheCommandZone() {
        final PaperCard thrasios = card("Thrasios, Triton Hero");
        final PaperCard tymna = card("Tymna the Weaver");
        generatesForCommandZone(NeoFormat.COMMANDER, thrasios, null);
        generatesForCommandZone(NeoFormat.COMMANDER, thrasios, tymna);
        generatesForCommandZone(NeoFormat.TINY_LEADERS, thrasios, tymna);
    }

    /**
     * Seis mazos para esa zona de mando: dentro de su identidad, legales, con
     * la zona de mando tal cual y, si hay companero, con cartas de sus colores.
     * La identidad se calcula aqui aparte, sin preguntarle al editor: es
     * justo lo que se esta comprobando.
     */
    private static void generatesForCommandZone(final NeoFormat format, final PaperCard head,
                                                final PaperCard partner) {
        final forge.card.ColorSet own = head.getRules().getColorIdentity();
        final forge.card.ColorSet identity = partner == null ? own
                : forge.card.ColorSet.combine(own, partner.getRules().getColorIdentity());
        final String who = format.deckFormat() + " " + head.getName()
                + (partner == null ? " solo" : " + " + partner.getName());
        int outside = 0;
        int withPartnerColours = 0;
        int bad = 0;
        String last = null;
        for (int i = 0; i < 6; i++) {
            final DeckEditor editor = DeckEditor.createNew(format, "prueba-gen-partner-" + i);
            editor.setCommander(head);
            if (partner != null && !editor.setCommander(partner)) {
                bad++;
                last = "el editor no acepta a " + partner.getName() + " de companero";
                continue;
            }
            if (editor.generateForCommander() < 0) {
                bad++;
                last = "el generador no ha devuelto nada";
                continue;
            }
            boolean partnerColours = false;
            for (final java.util.Map.Entry<PaperCard, Integer> e : editor.getDeck().getMain()) {
                final forge.card.ColorSet ci = e.getKey().getRules().getColorIdentity();
                if (!ci.hasNoColorsExcept(identity)) {
                    outside += e.getValue();
                } else if (!ci.hasNoColorsExcept(own)) {
                    partnerColours = true;
                }
            }
            if (partnerColours) {
                withPartnerColours++;
            }
            if (editor.commanders().size() != (partner == null ? 1 : 2)) {
                bad++;
                last = "la zona de mando ha cambiado: " + editor.commanders();
                continue;
            }
            final String problem = editor.problem();
            if (problem != null) {
                bad++;
                last = problem;
            }
        }
        check("Generar " + who + ": seis mazos dentro de su identidad y legales"
                        + (outside == 0 && last == null ? ""
                                : " -> " + outside + " cartas fuera de la identidad"
                                        + (last == null ? "" : "; " + last)),
                outside == 0 && bad == 0);
        if (partner != null) {
            check("Generar " + who + ": con cartas de los colores del companero ("
                    + withPartnerColours + " de 6)", withPartnerColours == 6);
        }
    }

    /**
     * "Genérame uno" en el selector de rival (la auditoría del motor, apartado B6, segunda
     * mitad): {@code DeckgenUtil.generateCommanderDeck}, la fachada que
     * elige el comandante Y monta el mazo en la misma llamada.
     *
     * <p>Aquí el mazo NO pasa por {@code DeckEditor} — se le da directo al
     * {@code RegisteredPlayer} del rival, como cualquier mazo elegido a mano
     * — así que la comprobación es independiente: que el motor lo declare
     * conforme con {@code DeckFormat.Commander.getDeckConformanceProblem}.
     *
     * <p>Se prueba {@link GeneratedDecks#commanderDeck}, que es lo que recibe
     * el jugador, y no el generador pelado: el 02-10-2026 esto falló UNA vez
     * con Brad Boimler y tres pasadas seguidas salieron verdes. Era Gleemox
     * ({@code DeckLimit:0}), que el generador mete en ~1 de cada 100 mazos.
     * Si vuelve a fallar, el motivo del motor sale en la propia linea.
     */
    private static void generatesARandomOpponentDeck() {
        Deck generated = null;
        try {
            generated = GeneratedDecks.commanderDeck(true, forge.game.GameType.Commander);
        } catch (final RuntimeException e) {
            System.out.println("        (Generar rival: excepcion " + e + ")");
        }
        check("Generar rival: el motor monta un mazo de verdad", generated != null);
        if (generated == null) {
            return;
        }
        check("Generar rival: tiene comandante", !generated.getCommanders().isEmpty());
        final String problem = commanderProblem(generated);
        check("Generar rival: cumple las reglas de construcción de Commander"
                + (problem == null ? "" : " -> " + problem), problem == null);
        System.out.printf(java.util.Locale.ROOT,
                "        (Generar rival: comandante %s, %d cartas en el principal)%n",
                generated.getCommanders().isEmpty() ? "?" : generated.getCommanders().get(0).getName(),
                generated.getMain().countAll());

        // Con otro problema ya puesto, la prueba de Gleemox saldria en rojo
        // por arrastre: un fallo de verdad contado tres veces.
        if (problem == null) {
            generatedDecksDropGleemox(generated);
        }
    }

    /**
     * Lo que arregla {@link GeneratedDecks#fixCopyLimits}, sin esperar al 1%
     * en que el generador saca Gleemox: se le mete a mano en el sitio de un
     * hechizo y se mira que salga por una basica, con el mazo en su tamaño.
     *
     * <p>Antes, que el motor la rechace de verdad: si algun dia Forge le quita
     * el {@code DeckLimit:0}, el arreglo sobra y esta prueba lo dice en vez de
     * dar un verde que no demuestra nada.
     */
    private static void generatedDecksDropGleemox(final Deck deck) {
        final PaperCard gleemox = FModel.getMagicDb().getCommonCards().getCard("Gleemox");
        if (gleemox == null) {
            System.out.println("        (Gleemox ya no esta en Forge: nada que probar)");
            return;
        }
        PaperCard spell = null;
        for (final java.util.Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (!e.getKey().getRules().getType().isLand()) {
                spell = e.getKey();
                break;
            }
        }
        if (spell == null) {
            check("Gleemox: el mazo generado trae algun hechizo que cambiar", false);
            return;
        }
        final int size = deck.getMain().countAll();
        deck.getMain().remove(spell, 1);
        deck.getMain().add(gleemox, 1);
        final String before = commanderProblem(deck);
        check("Gleemox: el motor la da por ilegal (" + before + ")",
                before != null && before.contains("Gleemox"));

        final int swapped = GeneratedDecks.fixCopyLimits(deck, forge.deck.DeckFormat.Commander);
        final String after = commanderProblem(deck);
        check("Gleemox: se cambia por una basica y el mazo vuelve a ser legal"
                        + (after == null ? "" : " -> " + after),
                swapped == 1 && deck.getMain().countByName("Gleemox") == 0
                        && deck.getMain().countAll() == size && after == null);
        check("Gleemox: un mazo sin nada de mas no se toca",
                GeneratedDecks.fixCopyLimits(deck, forge.deck.DeckFormat.Commander) == 0
                        && deck.getMain().countAll() == size);
    }

    private static String commanderProblem(final Deck deck) {
        return forge.game.GameType.Commander.getDeckFormat().getDeckConformanceProblem(deck);
    }

    /**
     * Que el catalogo del deck builder NO enseñe las cartas de Ascenso
     * (las 35 reliquias y el segundo aliento del jefe).
     *
     * <p><b>No es paranoia, es un fallo real que se cazo aqui mismo.</b>
     * {@code AscentRelics} las registra con {@code AI:RemoveDeck:All}, que
     * solo evita que un mazo ALEATORIO las incluya — no las saca de
     * {@code getUniqueCards()}, que es de donde tira {@link CardIndex}.
     * Nada mas registrarlas no aparecen ahi ({@code CardDb.addCard} no
     * reindexa), pero <b>si</b> en cuanto el motor rehace la base entera.
     *
     * <p>⚠️ Y eso hay que <b>forzarlo</b>. Hasta el 02-10-2026 esto miraba
     * justo despues de {@code install()}, contando con que la reindexacion
     * "llega jugando" — y desde Forge #11763 (rebase del 09-09-2026) ya no
     * llega: el catalogo no traia ni una reliquia y el verde no demostraba
     * nada, con o sin filtro. Ahora se meten a la fuerza
     * ({@code AscentRelics.exposeInUniqueCardsForTest}), se exige que esten
     * todas, y solo entonces se construye el {@link CardIndex} de verdad,
     * que es el camino que sigue la pantalla del jugador. Va la ultima del
     * comprobador porque ya no se pueden sacar.
     */
    private static void ascentCardsStayOutOfTheCatalogue() {
        AscentRelics.install();
        final Set<String> ours = AscentRelics.allCardNames();
        if (ours.isEmpty()) {
            check("hay reliquias de Ascenso que comprobar", false);
            return;
        }
        final int solas = AscentRelics.countInUniqueCards();
        final int dentro = AscentRelics.exposeInUniqueCardsForTest();
        check("las " + ours.size() + " cartas de Ascenso estan en el catalogo del motor, que es"
                + " el caso que el filtro tiene que aguantar (forzado: " + dentro
                + "; solas habia " + solas + ")", dentro == ours.size());
        if (dentro != ours.size()) {
            return;
        }
        final CardIndex index = CardIndex.of(FModel.getMagicDb().getCommonCards().getUniqueCards());
        int intrusas = 0;
        for (int i = 0; i < index.size(); i++) {
            if (ours.contains(index.cardAt(i).getName())) {
                intrusas++;
            }
        }
        check("el catalogo del deck builder no ensenya ninguna de las " + ours.size()
                + " cartas de Ascenso" + (intrusas > 0 ? " (" + intrusas + " coladas)" : ""),
                intrusas == 0);
    }

    // ---------------------------------------------------------------

    /**
     * Oathbreaker tiene DOS huecos en la zona de mando, y los dos se rellenan.
     *
     * <p>Reportado por un jugador el 21-09-2026: no habia forma de poner el
     * hechizo insignia. El editor solo conocia "comandante", y para este
     * formato eso es {@code canBeOathbreaker()} — planeswalkers — asi que un
     * instantaneo no salia en el selector y {@code setCommander} lo rechazaba.
     * El mazo se quedaba para siempre en "is missing a signature spell", y
     * pegar una lista de Moxfield <b>perdia</b> el hechizo por el mismo sitio.
     */
    private static void oathbreakerHasTwoSlots() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.OATHBREAKER, "prueba");
        final PaperCard oath = card("Chandra, Fire Artisan");
        final PaperCard spell = card("Lightning Bolt");

        check("Oathbreaker: el formato declara los dos huecos",
                editor.usesCommander() && editor.usesSignatureSpell());
        check("Oathbreaker: entra el planeswalker", editor.setCommander(oath));
        check("Oathbreaker: entra el hechizo insignia", editor.setCommander(spell));

        // Lo que fallaba: el segundo vaciaba la zona y se llevaba al primero.
        check("Oathbreaker: el planeswalker sigue puesto",
                oath.equals(editor.mainCommander()));
        check("Oathbreaker: y el hechizo tambien",
                spell.equals(editor.signatureSpell()));
        check("Oathbreaker: la zona de mando tiene exactamente 2",
                editor.commanders().size() == 2);

        // Cambiar de planeswalker sustituye SOLO al planeswalker.
        final PaperCard other = card("Jaya, Venerated Firemage");
        check("Oathbreaker: se cambia de oathbreaker", editor.setCommander(other));
        check("Oathbreaker: el nuevo esta puesto", other.equals(editor.mainCommander()));
        check("Oathbreaker: y el hechizo NO se ha ido",
                spell.equals(editor.signatureSpell()));
        check("Oathbreaker: siguen siendo 2", editor.commanders().size() == 2);

        // La identidad de color la fija el oathbreaker, no el hechizo: si el
        // hechizo contara, la interfaz daria por buenas cartas que el motor
        // rechaza al guardar.
        check("Oathbreaker: una carta roja entra",
                editor.add(card("Goblin Guide"), 1) == 1);
        check("Oathbreaker: una azul NO entra",
                editor.add(card("Counterspell"), 1) == 0);

        // Y el selector del hueco del hechizo ofrece instantaneos y conjuros.
        final List<PaperCard> spells = editor.signatureCandidates("Lightning Bolt", 20);
        check("Oathbreaker: el selector del hechizo encuentra el instantaneo",
                spells.stream().anyMatch(c -> "Lightning Bolt".equals(c.getName())));
        check("Oathbreaker: y el del comandante NO lo ofrece",
                editor.commanderCandidates("Lightning Bolt", 20).isEmpty());

        // El mazo entero: 58 + oathbreaker + hechizo = 60, y el motor lo da por
        // bueno. Es la comprobacion de verdad — las demas miran piezas.
        final DeckEditor full = DeckEditor.createNew(NeoFormat.OATHBREAKER, "completo");
        full.setCommander(oath);
        full.setCommander(spell);
        full.add(card("Mountain"), 58);
        check("Oathbreaker: un mazo con los dos huecos es legal",
                full.problem() == null);

        // Y sin hechizo no lo es, que es justo el estado en el que se quedaban
        // todos los mazos antes de esto.
        final DeckEditor half = DeckEditor.createNew(NeoFormat.OATHBREAKER, "sin hechizo");
        half.setCommander(oath);
        half.add(card("Mountain"), 58);
        check("Oathbreaker: sin hechizo insignia NO es legal",
                half.problem() != null);
    }

    /**
     * Y Commander no se entera de nada de lo anterior.
     *
     * <p>Todo el hueco del hechizo cuelga de {@code hasSignatureSpell()}, que
     * solo es cierto en Oathbreaker. Esta prueba existe para que se vea en rojo
     * el dia que alguien lo saque de ahi.
     */
    private static void oathbreakerDoesNotChangeCommander() {
        final DeckEditor editor = DeckEditor.createNew(NeoFormat.COMMANDER, "prueba");
        check("Commander: no tiene hechizo insignia", !editor.usesSignatureSpell());
        check("Commander: un instantaneo no es hechizo insignia",
                !editor.isSignatureSpell(card("Lightning Bolt")));
        check("Commander: un instantaneo NO puede ser comandante",
                !editor.setCommander(card("Lightning Bolt")));

        // El reemplazo de siempre: un comandante que no es companyero sustituye
        // al anterior y la zona se queda con uno.
        check("Commander: entra el primero", editor.setCommander(card("Krenko, Mob Boss")));
        check("Commander: entra el segundo", editor.setCommander(card("Talrand, Sky Summoner")));
        check("Commander: y sustituye al primero", editor.commanders().size() == 1);
        check("Commander: mainCommander devuelve ese",
                "Talrand, Sky Summoner".equals(
                        editor.mainCommander() == null ? null : editor.mainCommander().getName()));
    }

    /**
     * En la Aventura, la lista de prohibidas del formato no pinta nada — pero
     * las reglas de construccion si, y fuera de la Aventura no cambia nada.
     *
     * <p>Las tres mitades hacen falta. Quitar la comprobacion del pozo de
     * cartas es aflojar una regla, y aflojarla de mas seria peor que el fallo
     * que arregla: un mazo de Commander normal tiene que seguir rechazando lo
     * mismo que rechazaba ayer.
     *
     * <p>Sale de un informe de Reddit del 22-09-2026 sobre una partida de
     * <i>Realm of Legends</i> traida de Forge: <i>"it tells me that a lot of
     * the cards are not legal in adventure, when in fact I am using them in
     * Forge's Realm of Legends deck"</i>. Eran las 58 prohibidas en Commander y
     * las 216 rebalanceadas de Alchemy que la Aventura reparte como premio; su
     * editor no las mira nunca. Ver {@link DeckContext#enforcesCardPool()}.
     */
    private static void adventureIgnoresTheBanList() {
        final PaperCard banned = card("Mana Crypt");
        final PaperCard alchemy = card("A-Blood Artist");
        final PaperCard normal = card("Sol Ring");
        final PaperCard commander = card("Yahenni, Undying Partisan");
        final PaperCard offColour = card("Lightning Bolt");

        // 1. Fuera de la Aventura manda el formato, hoy igual que ayer.
        final DeckEditor cmd = new DeckEditor(NeoFormat.COMMANDER, new Deck("__neocheck-ban__"));
        check("Commander: Mana Crypt sigue prohibida",
                cmd.rejectionReason(banned) != null);
        check("Commander: las rebalanceadas de Arena siguen fuera",
                cmd.rejectionReason(alchemy) != null);

        // 2. Dentro, no.
        final DeckEditor adv = new DeckEditor(
                new AdventureLike(List.of(banned, alchemy, normal, commander, offColour)),
                new Deck("__neocheck-adv__"));
        check("Aventura: una prohibida en Commander entra (la dio el propio modo)",
                adv.rejectionReason(banned) == null);
        check("Aventura: una rebalanceada de Arena entra",
                adv.rejectionReason(alchemy) == null);
        check("Aventura: y lo de siempre sigue entrando",
                adv.rejectionReason(normal) == null);
        adv.add(banned, 1);
        adv.add(alchemy, 1);
        check("Aventura: puestas en el mazo, no salen marcadas como ilegales",
                adv.illegalCards().isEmpty());

        // 3. Pero las reglas de construccion, las mismas de siempre.
        check("Aventura: el singleton de Commander se sigue aplicando",
                adv.rejectionReason(banned) != null);
        check("Aventura: el comandante se acepta igual",
                adv.setCommander(commander));
        check("Aventura: la identidad de color se sigue aplicando",
                adv.rejectionReason(offColour) != null);
    }

    /**
     * <b>Lo ultimo primero</b> (pedido en itch.io el 27-09-2026): el catalogo
     * de la Aventura ordenado por cuando entro cada carta.
     *
     * <p>Lo que se rompe sin verse es el ORDEN respecto al corte: la busqueda
     * se queda con las N primeras, y ordenar despues del corte dejaria fuera
     * justo lo ultimo que ha entrado en una coleccion grande. Por eso se pide
     * con un tope menor que el pool.
     */
    private static void newestFirstOrdersByAcquisition() {
        final List<PaperCard> pool = List.of(card("Counterspell"), card("Lightning Bolt"),
                card("Llanowar Elves"), card("Serra Angel"), card("Sol Ring"));
        final DeckEditor adv = new DeckEditor(new AdventureLike(pool, java.util.Map.of(
                "Sol Ring", 300L, "Lightning Bolt", 100L, "Llanowar Elves", 200L)),
                new Deck("__neocheck-newest__"));
        check("Lo ultimo primero: el contexto de la Aventura lo ofrece", adv.tracksAcquisition());
        // Por el nombre que SE VE, que en otro idioma es otro orden, y sin
        // tildes (DeckEditor.sortName): en castellano "Angel de Serra" con
        // tilde va el primero, no detras de la Z.
        final List<PaperCard> sorted = new java.util.ArrayList<>(pool);
        sorted.sort(java.util.Comparator.comparing((PaperCard c) ->
                org.apache.commons.lang3.StringUtils.stripAccents(
                        forge.neo.card.CardText.nameOf(c).toLowerCase(java.util.Locale.ROOT))));
        final List<String> byName = names(adv.find("", false, null, 3, false).cards);
        check("Lo ultimo primero: apagado, por nombre -> " + byName,
                byName.equals(names(sorted.subList(0, 3))));
        adv.setNewestFirst(true);
        final List<String> newest = names(adv.find("", false, null, 3, false).cards);
        check("Lo ultimo primero: encendido, por hora y ANTES del corte -> " + newest,
                newest.equals(List.of("Sol Ring", "Llanowar Elves", "Lightning Bolt")));
        final List<String> all = names(adv.find("", false, null, 10, false).cards);
        final List<String> undated = new java.util.ArrayList<>(names(sorted));
        undated.retainAll(List.of("Counterspell", "Serra Angel"));
        check("Lo ultimo primero: lo que no tiene hora va detras, por nombre -> " + all,
                all.subList(3, 5).equals(undated));
        final DeckEditor cmd = new DeckEditor(NeoFormat.COMMANDER, new Deck("__neocheck-newest2__"));
        check("Lo ultimo primero: fuera de una coleccion no se ofrece", !cmd.tracksAcquisition());
    }

    /**
     * Los ordenes del catalogo que tiene el editor de Forge (itch.io,
     * 29-09-2026): cada uno pone lo esperado arriba, y se ordena ANTES del
     * corte (se pide con un tope menor que el pool).
     */
    private static void catalogueSorts() {
        final List<PaperCard> pool = List.of(card("Counterspell"), card("Lightning Bolt"),
                card("Llanowar Elves"), card("Serra Angel"), card("Sol Ring"), card("Forest"));
        final DeckEditor adv = new DeckEditor(new AdventureLike(pool, java.util.Map.of()),
                new Deck("__neocheck-sort__"));
        adv.setSort(DeckEditor.Sort.COST);
        final List<String> cost = names(adv.find("", false, null, 2, false).cards);
        check("Orden por coste: primero lo de coste 0 -> " + cost,
                new java.util.HashSet<>(cost).equals(java.util.Set.of("Forest", "Lightning Bolt"))
                        || cost.contains("Forest"));
        adv.setSort(DeckEditor.Sort.POWER);
        final List<String> power = names(adv.find("", false, null, 1, false).cards);
        check("Orden por fuerza: la criatura mas fuerte arriba -> " + power,
                power.equals(List.of("Serra Angel")));
        adv.setSort(DeckEditor.Sort.TYPE);
        final List<String> type = names(adv.find("", false, null, 10, false).cards);
        check("Orden por tipo: criaturas primero y tierras al final -> " + type,
                type.get(0).equals("Llanowar Elves") || type.get(0).equals("Serra Angel"));
        check("Orden por tipo: la tierra la ultima", type.get(type.size() - 1).equals("Forest"));
        adv.setSort(DeckEditor.Sort.COLOR);
        final List<String> colour = names(adv.find("", false, null, 10, false).cards);
        check("Orden por color: el incoloro al final -> " + colour,
                colour.indexOf("Sol Ring") > colour.indexOf("Counterspell"));

        // AL REVES (itch.io, 03-10-2026: "You can't change sort ascending or
        // decending"). Se invierte el criterio, no la lista: lo que no aplica
        // sigue al final, y se ordena ANTES del corte.
        adv.setSort(DeckEditor.Sort.COST);
        adv.setSortReversed(true);
        final List<String> costDown = names(adv.find("", false, null, 1, false).cards);
        check("Coste al reves: lo mas caro arriba, antes del corte -> " + costDown,
                costDown.equals(List.of("Serra Angel")));
        adv.setSort(DeckEditor.Sort.POWER);
        final List<String> powerUp = names(adv.find("", false, null, 10, false).cards);
        check("Fuerza al reves: la mas debil arriba y lo que no es criatura sigue al final -> " + powerUp,
                powerUp.get(0).equals("Llanowar Elves")
                        && powerUp.indexOf("Serra Angel") < powerUp.indexOf("Counterspell")
                        && powerUp.indexOf("Serra Angel") < powerUp.indexOf("Forest"));
        // Por nombre, justo la lista de al derecho dada la vuelta. Sin nombres
        // escritos: se ordena por el TRADUCIDO, y eso depende del idioma.
        adv.setSort(DeckEditor.Sort.NAME);
        final List<String> nameDown = names(adv.find("", false, null, 10, false).cards);
        adv.setSortReversed(false);
        final List<String> nameUp = new java.util.ArrayList<>(names(adv.find("", false, null, 10, false).cards));
        java.util.Collections.reverse(nameUp);
        check("Nombre al reves: la lista de la A a la Z dada la vuelta -> " + nameDown,
                nameDown.equals(nameUp));

        // EL ORDEN DEL MAZO, dentro de cada tipo (itch.io: "No sort in deck, Why ?").
        adv.add(card("Serra Angel"), 1);
        adv.add(card("Llanowar Elves"), 1);
        final String creatures = groupWith(adv, "Serra Angel");
        check("De fabrica el mazo va por coste, como antes -> " + groupNames(adv, creatures),
                groupNames(adv, creatures).equals(List.of("Llanowar Elves", "Serra Angel")));
        adv.setDeckSort(DeckEditor.Sort.COST, true);
        check("El mazo por coste al reves -> " + groupNames(adv, creatures),
                groupNames(adv, creatures).equals(List.of("Serra Angel", "Llanowar Elves")));
        adv.setDeckSort(DeckEditor.Sort.TYPE, false);
        check("TIPO no es un orden del mazo (los grupos ya son los tipos): vuelve a coste",
                adv.getDeckSort() == DeckEditor.Sort.COST);
        adv.setDeckSort(DeckEditor.Sort.COST, false);

        // EL FILTRO DEL MAZO (itch.io: "the best a filter can do ... only split
        // the deck into 2"): tipo, coste y rareza, ademas de color y texto.
        final java.util.Set<forge.card.CardType.CoreType> none = java.util.Collections.emptySet();
        final java.util.Set<Integer> noCost = java.util.Collections.emptySet();
        final java.util.Set<forge.card.CardRarity> noRarity = java.util.Collections.emptySet();
        check("Filtro del mazo: un instantaneo pasa por tipo Instantaneo",
                DeckFilter.accepts(card("Counterspell"), 0, false, "",
                        java.util.EnumSet.of(forge.card.CardType.CoreType.Instant), noCost, noRarity));
        check("Filtro del mazo: una criatura no pasa por tipo Instantaneo",
                !DeckFilter.accepts(card("Serra Angel"), 0, false, "",
                        java.util.EnumSet.of(forge.card.CardType.CoreType.Instant), noCost, noRarity));
        check("Filtro del mazo: por coste, 7 es 7 o mas y el 5 no entra en el 2",
                !DeckFilter.accepts(card("Serra Angel"), 0, false, "", none, java.util.Set.of(2), noRarity)
                        && DeckFilter.accepts(card("Serra Angel"), 0, false, "", none, java.util.Set.of(5), noRarity));
        check("Filtro del mazo: las incoloras solo con su boton",
                DeckFilter.accepts(card("Sol Ring"), 0, true, "", none, noCost, noRarity)
                        && !DeckFilter.accepts(card("Sol Ring"),
                        forge.card.MagicColor.GREEN, false, "", none, noCost, noRarity));
        adv.setSort(DeckEditor.Sort.NAME);
    }

    /** El grupo del mazo en el que esta esa carta. */
    private static String groupWith(final DeckEditor ed, final String name) {
        for (final String g : DeckEditor.GROUPS) {
            for (final java.util.Map.Entry<PaperCard, Integer> e : ed.cardsInGroup(g)) {
                if (e.getKey().getName().equals(name)) {
                    return g;
                }
            }
        }
        return "";
    }

    private static List<String> groupNames(final DeckEditor ed, final String group) {
        final List<String> out = new java.util.ArrayList<>();
        for (final java.util.Map.Entry<PaperCard, Integer> e : ed.cardsInGroup(group)) {
            out.add(e.getKey().getName());
        }
        return out;
    }

    /**
     * Ordenar por cuantas tienes y "Ocultar las ya puestas" (Discord,
     * 03-10-2026). La cantidad pone arriba la que mas tienes y las infinitas
     * (las basicas de la Aventura) al final; y lo que ya esta puesto del todo
     * sale en {@code usedUpNames}, pero no la basica ni lo que no esta en el
     * mazo. Sin empates de cantidad a proposito: a igual numero manda el
     * nombre TRADUCIDO, y eso depende del idioma con que se pase.
     */
    private static void collectionCountAndUsedUp() {
        final List<PaperCard> pool = List.of(card("Counterspell"), card("Lightning Bolt"),
                card("Llanowar Elves"), card("Serra Angel"), card("Forest"));
        final DeckEditor adv = new DeckEditor(new AdventureLike(pool, java.util.Map.of(),
                java.util.Map.of("Counterspell", 1, "Lightning Bolt", 4,
                        "Llanowar Elves", 2, "Serra Angel", 3)),
                new Deck("__neocheck-count__"));
        adv.setSort(DeckEditor.Sort.COUNT);
        final List<String> byCount = names(adv.find("", false, null, 10, false).cards);
        check("Orden por cantidad: de la que mas tienes a la que menos, las infinitas al final -> "
                        + byCount,
                byCount.equals(List.of("Lightning Bolt", "Serra Angel", "Llanowar Elves",
                        "Counterspell", "Forest")));
        final List<String> firstTwo = names(adv.find("", false, null, 2, false).cards);
        check("Orden por cantidad: ANTES del corte -> " + firstTwo,
                firstTwo.equals(List.of("Lightning Bolt", "Serra Angel")));
        adv.setSort(DeckEditor.Sort.NAME);

        // Este contexto juega con las reglas de Commander: una copia de cada.
        adv.add(card("Lightning Bolt"), 1);
        adv.add(card("Forest"), 3);
        final java.util.Set<String> used = adv.usedUpNames();
        check("Ya puestas: el Rayo (una en Commander) si; la basica y lo que no esta, no -> " + used,
                used.equals(java.util.Set.of("Lightning Bolt")));
    }

    private static List<String> names(final List<PaperCard> cards) {
        final List<String> out = new java.util.ArrayList<>();
        for (final PaperCard c : cards) {
            out.add(c.getName());
        }
        return out;
    }

    /**
     * El companero (Lurrus, Kaheera...) va al BANQUILLO, que es donde lo busca
     * el motor al empezar ({@code Player.assignCompanion}).
     *
     * <p>Informe de itch.io del 27-09-2026: no habia forma de ponerlo. El
     * editor no ensenyaba banquillo y el importador, con Lurrus solo en el
     * banquillo de Moxfield, lo tomaba por comandante: en un mazo sin zona de
     * mando salia "no se pudo hacer comandante" y ya.
     */
    private static void companionGoesToTheSideboard() {
        final PaperCard kaheera = card("Kaheera, the Orphanguard");
        final PaperCard lions = card("Savannah Lions");
        check("Companero: se reconoce la palabra clave",
                DeckEditor.isCompanionCard(kaheera) && !DeckEditor.isCompanionCard(lions));
        check("Companero: 'Doctor's companion' no es esto",
                !DeckEditor.isCompanionCard(card("Rose Tyler")));

        // 1. A mano, en un construido.
        final DeckEditor vintage = DeckEditor.createNew(NeoFormat.VINTAGE, "companero");
        check("Companero: un construido lo admite", vintage.usesCompanion());
        vintage.add(kaheera, 1);
        check("Companero: se pone", vintage.setCompanion(kaheera) == null);
        check("Companero: va al banquillo y sale del principal",
                vintage.companion() == kaheera
                        && vintage.getDeck().getMain().countByName(kaheera.getName()) == 0
                        && vintage.getDeck().get(forge.deck.DeckSection.Sideboard).count(kaheera) == 1);
        check("Companero: una carta sin la palabra clave no",
                vintage.setCompanion(lions) != null && vintage.companion() == kaheera);
        vintage.removeCompanion();
        check("Companero: y se quita", vintage.companion() == null);

        // 2. Importando: Moxfield lo deja solo en el banquillo -> el
        // importador lo toma por comandante; sin zona de mando es companero.
        final String moxfield = String.join(System.lineSeparator(),
                "4 Savannah Lions", "", "SIDEBOARD:", "1 Kaheera, the Orphanguard");
        final DeckImporter.Result mox = DeckImporter.importCommander(moxfield, "prueba");
        check("Companero: importado sin zona de mando, sale como companero",
                DeckImporter.companionsOf(mox.deck, false).contains(kaheera));
        check("Companero: y con zona de mando, un Lurrus solo sigue siendo comandante",
                DeckImporter.companionsOf(mox.deck, true).isEmpty());

        // 3. Arena: encabezado "Companion", que Forge no conoce.
        final String arena = String.join(System.lineSeparator(),
                "Companion", "1 Kaheera, the Orphanguard (IKO) 16", "",
                "Deck", "4 Savannah Lions");
        final DeckImporter.Result ar = DeckImporter.importCommander(arena, "prueba");
        System.out.printf(java.util.Locale.ROOT,
                "        (Arena: comandantes=%s principal=%d banquillo=%s desconocidas=%s)%n",
                ar.deck == null ? "-" : ar.deck.getCommanders(),
                ar.deck == null ? -1 : DeckImporter.sectionSize(ar.deck, forge.deck.DeckSection.Main),
                ar.deck == null || !ar.deck.has(forge.deck.DeckSection.Sideboard) ? "-"
                        : ar.deck.get(forge.deck.DeckSection.Sideboard).toFlatList(),
                ar.problems);
        check("Companero: el encabezado 'Companion' de Arena no es una carta desconocida",
                ar.unknown == 0);
        check("Companero: y lo deja en el banquillo, no de comandante",
                ar.deck != null && ar.deck.getCommanders().isEmpty()
                        && DeckImporter.companionsOf(ar.deck, true).contains(
                                ar.deck.get(forge.deck.DeckSection.Sideboard).toFlatList().get(0)));

        // 4. Moxfield con comandante Y companero en el banquillo.
        final String both = String.join(System.lineSeparator(),
                "1 Sol Ring", "", "SIDEBOARD:",
                "1 Kaheera, the Orphanguard", "1 Yarok, the Desecrated");
        final DeckImporter.Result mixed = DeckImporter.importCommander(both, "prueba");
        check("Companero: comandante y companero juntos se separan",
                mixed.deck != null && mixed.deck.getCommanders().size() == 1
                        && mixed.deck.getCommanders().get(0).getName().equals("Yarok, the Desecrated")
                        && DeckImporter.companionsOf(mixed.deck, true).size() == 1);

        // 5. En Commander, la identidad cuenta tambien para el companero (el
        // motor juzga el banquillo con ella).
        final DeckEditor cmd = DeckEditor.createNew(NeoFormat.COMMANDER, "companero");
        cmd.setCommander(card("Krenko, Mob Boss"));
        check("Companero: en Commander, uno fuera de la identidad no entra",
                cmd.setCompanion(kaheera) != null && cmd.companion() == null);
        check("Companero: en limitado no hay hueco (el banquillo es el pool)",
                !DeckEditor.createNew(NeoFormat.DRAFT, "companero").usesCompanion());
        // 6. Companero puesto antes que el comandante, y luego un comandante de
        //    otro color: se marca y "quitar lo que no cabe" lo quita.
        final DeckEditor late = DeckEditor.createNew(NeoFormat.COMMANDER, "companero");
        check("Companero: sin comandante todavia, se pone", late.setCompanion(kaheera) == null);
        late.setCommander(card("Krenko, Mob Boss"));
        check("Companero: con un comandante de otro color sale como lo que no cabe",
                late.illegalCards().contains(kaheera));
        late.removeIllegal();
        check("Companero: y se quita con lo que no cabe", late.companion() == null);

        // 7. En un banquillo de verdad, cambiar de companero no borra el otro.
        final DeckEditor real = DeckEditor.createNew(NeoFormat.VINTAGE, "companero");
        real.getDeck().getOrCreate(forge.deck.DeckSection.Sideboard).add(card("Yorion, Sky Nomad"), 1);
        real.getDeck().getOrCreate(forge.deck.DeckSection.Sideboard).add(card("Swords to Plowshares"), 1);
        check("Companero: en un banquillo de verdad se pone el nuevo",
                real.setCompanion(kaheera) == null && real.companion() == kaheera);
        check("Companero: y el Yorion del jugador sigue ahi",
                real.getDeck().get(forge.deck.DeckSection.Sideboard).countByName("Yorion, Sky Nomad") == 1);
    }

    /**
     * Un contexto como el de la Aventura: coleccion cerrada, reglas de
     * Commander y <b>sin</b> pozo de cartas del formato.
     *
     * <p>Copia de lo que contesta {@code AdventureDeckContext} en las tres
     * preguntas que importan aqui. No se usa el de verdad porque cuelga de un
     * {@code AdventurePlayer} y del hilo de libGDX, que sin ventana no existen.
     */
    private static final class AdventureLike implements DeckContext {

        private final List<PaperCard> pool;
        private final java.util.Map<String, Long> acquired;
        /** Cuantas tienes de cada una; la que no esta, sin techo. */
        private final java.util.Map<String, Integer> ownedBy;

        AdventureLike(final List<PaperCard> pool) {
            this(pool, java.util.Map.of());
        }

        AdventureLike(final List<PaperCard> pool, final java.util.Map<String, Long> acquired) {
            this(pool, acquired, java.util.Map.of());
        }

        AdventureLike(final List<PaperCard> pool, final java.util.Map<String, Long> acquired,
                      final java.util.Map<String, Integer> ownedBy) {
            this.pool = pool;
            this.acquired = acquired;
            this.ownedBy = ownedBy;
        }

        @Override
        public boolean tracksAcquisition() {
            return true;
        }

        @Override
        public long acquiredAt(final PaperCard card) {
            return acquired.getOrDefault(card.getName(), 0L);
        }

        @Override
        public String getLabel() {
            return "Adventure";
        }

        @Override
        public forge.deck.DeckFormat deckFormat() {
            return forge.game.GameType.Commander.getDeckFormat();
        }

        @Override
        public forge.util.storage.IStorage<Deck> storage() {
            return new forge.util.storage.StorageBase<>("adv", "adv", new java.util.HashMap<>());
        }

        @Override
        public List<PaperCard> pool() {
            return pool;
        }

        @Override
        public int owned(final PaperCard card) {
            return ownedBy.getOrDefault(card.getName(), Integer.MAX_VALUE);
        }

        @Override
        public boolean enforcesCardPool() {
            return false;
        }
    }

    private static PaperCard card(final String name) {
        final PaperCard c = FModel.getMagicDb().getCommonCards().getCard(name);
        if (c == null) {
            throw new IllegalStateException("No existe la carta de prueba: " + name);
        }
        return c;
    }

    private static void check(final String what, final boolean ok) {
        System.out.printf("  [%s] %s%n", ok ? "OK " : "MAL", what);
        if (ok) {
            passed++;
        } else {
            failed++;
        }
    }
}
