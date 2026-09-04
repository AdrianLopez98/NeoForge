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
        otherFormatsFilterThePool();
        generatesADeckForTheCommander();
        generatesARandomOpponentDeck();
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
        check("Commander: y dice que es por la identidad de color",
                String.valueOf(editor.rejectionReason(card("Counterspell")))
                        .contains("identidad de color"));
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
        final String menos = DeckProblem.translate("should have at least 60 cards");
        check("Problema del mazo: 'al menos 60' se traduce",
                !menos.equals("should have at least 60 cards") && menos.contains("60"));

        final String mas = DeckProblem.translate("should have no more than 100 cards");
        check("Problema del mazo: 'no mas de 100' se traduce",
                !mas.equals("should have no more than 100 cards") && mas.contains("100"));

        final String copias =
                DeckProblem.translate("must not contain more than 4 copies of the card Lightning Bolt");
        check("Problema del mazo: las copias de mas se traducen y dicen la carta",
                copias.contains("4") && copias.contains("Lightning Bolt")
                        && !copias.startsWith("must not"));

        final String id = DeckProblem.translate(
                "contains one or more cards that do not match the commanders color identity:"
                + System.lineSeparator() + "Lightning Bolt");
        check("Problema del mazo: la identidad de color se traduce",
                !id.startsWith("contains one or more"));
        check("Problema del mazo: y NO se come la lista de cartas",
                id.contains("Lightning Bolt"));

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
                real != null && !real.startsWith("should have"));
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
     * "Genérame uno" en el selector de rival (la auditoría del motor, apartado B6, segunda
     * mitad): {@code DeckgenUtil.generateCommanderDeck}, la fachada que
     * elige el comandante Y monta el mazo en la misma llamada.
     *
     * <p>Aquí el mazo NO pasa por {@code DeckEditor} — se le da directo al
     * {@code RegisteredPlayer} del rival, como cualquier mazo elegido a mano
     * — así que la comprobación es independiente: que el motor lo declare
     * conforme con {@code DeckFormat.Commander.getDeckConformanceProblem}.
     */
    private static void generatesARandomOpponentDeck() {
        Deck generated = null;
        try {
            generated = forge.deck.DeckgenUtil.generateCommanderDeck(
                    true, forge.game.GameType.Commander);
        } catch (final RuntimeException e) {
            System.out.println("        (Generar rival: excepcion " + e + ")");
        }
        check("Generar rival: el motor monta un mazo de verdad", generated != null);
        if (generated == null) {
            return;
        }
        check("Generar rival: tiene comandante", !generated.getCommanders().isEmpty());
        check("Generar rival: cumple las reglas de construcción de Commander",
                forge.game.GameType.Commander.getDeckFormat()
                        .getDeckConformanceProblem(generated) == null);
        System.out.printf(java.util.Locale.ROOT,
                "        (Generar rival: comandante %s, %d cartas en el principal)%n",
                generated.getCommanders().isEmpty() ? "?" : generated.getCommanders().get(0).getName(),
                generated.getMain().countAll());
    }

    /**
     * Que el catalogo del deck builder NO enseñe las cartas de Ascenso
     * (las 35 reliquias y el segundo aliento del jefe).
     *
     * <p><b>No es paranoia, es un fallo real que se cazo aqui mismo.</b>
     * {@code AscentRelics} las registra con {@code AI:RemoveDeck:All}, que
     * solo evita que un mazo ALEATORIO las incluya — no las saca de
     * {@code getUniqueCards()}, que es de donde tira {@link CardIndex}.
     * Medido: nada mas registrarlas no aparecen ahi (el motor todavia no ha
     * reindexado), pero en cuanto se juega una partida de verdad
     * {@code CardDb} reindexa y <b>si</b> aparecen — asi que un comprobador
     * que mirara justo despues de registrarlas, sin jugar nada, pasaria en
     * verde sin que el fallo estuviera arreglado. Por eso aqui se fuerza
     * {@code AscentRelics.install()} y se construye un {@link CardIndex}
     * de verdad sobre el catalogo completo, que es exactamente el camino que
     * sigue la pantalla del jugador.
     */
    private static void ascentCardsStayOutOfTheCatalogue() {
        AscentRelics.install();
        final Set<String> ours = AscentRelics.allCardNames();
        if (ours.isEmpty()) {
            check("hay reliquias de Ascenso que comprobar", false);
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
