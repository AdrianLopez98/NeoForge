package forge.neo.draft;

import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.gamemodes.limited.LimitedPoolType;
import forge.item.PaperCard;

/**
 * Draftea un draft entero sin abrir una ventana.
 *
 * <p>Mismo papel que {@code DeckRulesCheck} para el deck builder: la pantalla
 * de picks se puede comprobar con una captura, pero <b>el ciclo</b> — 3 sobres
 * x 15 picks, los siete rivales eligiendo a la vez, y el resultado guardado con
 * ocho mazos — no. Aqui se recorre entero cogiendo siempre la primera carta.
 *
 * <p>{@code run.cmd draftcheck}
 */
public final class DraftCheck {

    private DraftCheck() {
    }

    public static void run() {
        final long t0 = System.currentTimeMillis();
        final NeoDraft draft = NeoDraft.start(LimitedPoolType.Full);
        if (draft == null) {
            System.out.println("  FALLO: el motor no ha podido generar los sobres.");
            return;
        }

        int picks = 0;
        int lastRound = -1;
        while (!draft.isDone() && picks < 200) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            if (draft.round() != lastRound) {
                lastRound = draft.round();
                System.out.printf(Locale.ROOT, "  Ronda %d: sobre de %d cartas%n",
                        lastRound, pack.size());
            }
            final PaperCard got = draft.pick(pack.get(0));
            if (got != null) {
                picks++;
            }
        }

        // Lo que el jugador fue poniendo en el mazo durante el draft (la tira
        // de la pantalla de picks) tiene que llegar al mazo guardado.
        final List<PaperCard> planned = new java.util.ArrayList<>(
                draft.picked().subList(0, Math.min(23, draft.picked().size())));
        draft.planMain(planned);
        final DeckGroup group = draft.save("neo-draftcheck");
        final Deck mine = group.getHumanDeck();
        final int pool = poolTotal(mine) + basicsIn(mine);
        // El mazo sale VACIO (23-09-2026): montarlo es cosa del jugador. Y el
        // boton "Montar solo" tiene que seguir dando un mazo jugable.
        final int emptyAtSave = mine.getMain().countAll();
        final int main = autoBuildSaved(DraftRun.of("neo-draftcheck")).getMain().countAll();

        System.out.println();
        System.out.printf(Locale.ROOT,
                "  Picks: %d | pool: %d cartas | mazo al guardar: %d | montado solo: %d"
                + " | rivales: %d mazos | %ds%n",
                picks, pool, emptyAtSave, main, group.getAiDecks().size(),
                (System.currentTimeMillis() - t0) / 1000);

        final boolean ok = picks >= 40 && pool == picks && emptyAtSave == planned.size() && main >= 40
                && group.getAiDecks().size() == 7;
        System.out.println(ok
                ? "  OK - el draft se completa y deja ocho mazos"
                : "  FALLO - revisa el ciclo de picks");

        checkSetDraft("DOM", 7);
        // Y una de las dos expansiones que piden mesa de CUATRO: ahi el evento
        // no son siete rivales, son tres. Ver checkSetDraft.
        checkSetDraft("SPM", 3);
        checkRivals(draft, group);
        checkEditor();
        checkOldFormat();
        checkRun();
        if (!Boolean.getBoolean("neo.draftcheck.noGames")) {
            checkGames();
        }
        checkCubeDraft();
        checkRanking();

        // No dejar basura en los mazos del usuario: es SU carpeta.
        borrar("neo-draftcheck");
    }

    /**
     * Draft de cubo (la auditoría del motor, apartado C2), de principio a fin.
     *
     * <p>No hace falta un nombre fijo: cualquier cubo de {@code res/cube}
     * sirve, así que se coge el primero que {@link NeoDraft#cubes()} liste —
     * eso prueba de paso que la propia lista no está vacía, que ya es la
     * mitad de lo que puede romperse aquí (leer 96 ficheros {@code .draft}
     * sin que ninguno tumbe la lista entera).
     *
     * <p>Lo que de verdad hay que confirmar es que {@link NeoDraft#startCube}
     * — que contesta la pregunta del motor ANTES de que se haga, con un
     * elegidor de usar-y-tirar — deja el elegidor de siempre <b>tal y como
     * estaba</b>: se comprueba haciendo un segundo draft de una expansión
     * normal justo después, que tiene que funcionar exactamente igual que si
     * el cubo no se hubiera tocado.
     */
    private static void checkCubeDraft() {
        final List<forge.gamemodes.limited.CustomLimited> cubes = NeoDraft.cubes();
        System.out.printf(Locale.ROOT, "%n  Cubos encontrados: %d%n", cubes.size());
        if (cubes.isEmpty()) {
            System.out.println("  FALLO: no se ha leido ni un cubo de res/cube");
            return;
        }
        final String name = cubes.get(0).getName();
        final NeoDraft cubeDraft = NeoDraft.startCube(name);
        if (cubeDraft == null) {
            System.out.println("  FALLO: no se ha podido empezar el draft de \"" + name + "\"");
            return;
        }

        int picks = 0;
        while (!cubeDraft.isDone() && picks < 60) {
            final List<PaperCard> pack = cubeDraft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            if (cubeDraft.pick(pack.get(0)) != null) {
                picks++;
            }
        }
        final DeckGroup cubeGroup = cubeDraft.save("neo-draftcheck-cubo");
        final int cubePool = cubeGroup.getHumanDeck().get(DeckSection.Sideboard).countAll();
        System.out.printf(Locale.ROOT,
                "  Cubo \"%s\": %d picks | pool %d cartas | rivales %d mazos%n",
                name, picks, cubePool, cubeGroup.getAiDecks().size());
        final boolean cubeOk = picks >= 40 && cubePool >= 40 && cubeGroup.getAiDecks().size() == 7;
        System.out.println(cubeOk
                ? "  OK - el draft de cubo se completa y deja ocho mazos"
                : "  FALLO - revisa el ciclo de picks del cubo");
        borrar("neo-draftcheck-cubo");

        // El elegidor de usar-y-tirar de startCube tiene que haberse quitado
        // solo: un draft normal, justo despues, tiene que preguntar por
        // dialogo como siempre (o resolverse por su cuenta sin ventana, que
        // es lo que pasa aqui) y no devolver el cubo de antes por error.
        final forge.card.CardEdition dom = forge.model.FModel.getMagicDb().getEditions().get("DOM");
        if (dom != null) {
            final NeoDraft afterCube = NeoDraft.start(dom);
            System.out.println(afterCube != null
                    ? "  OK - un draft normal despues del cubo sigue funcionando"
                    : "  FALLO - el elegidor de usar-y-tirar se quedo puesto");
        }
    }

    /**
     * El rating de pick del sobre (la auditoría del motor, apartado C3): que el numero que se
     * pinta en la esquina de la carta ({@code CardRanker.getRawScore}, la
     * misma llamada que hace {@code DraftScreen}) sea siempre un numero de
     * verdad y quede dentro del 0-99 que la pastilla puede pintar.
     *
     * <p>Una tierra basica es <b>unpickable</b> a proposito
     * ({@code CardRanker.SCORE_UNPICKABLE}), asi que tiene que salir pinchada
     * al suelo (0), y una carta cualquiera del sobre tiene que dar el MISMO
     * numero las dos veces que se pregunta — si no fuera determinista, la
     * pastilla parpadearia de pick en pick sin que la carta cambiara.
     */
    private static void checkRanking() {
        final PaperCard plains = forge.model.FModel.getMagicDb().getCommonCards()
                .getCard("Plains");
        boolean ok = plains != null;
        int landClamped = -1;
        if (plains != null) {
            landClamped = (int) Math.round(Math.max(0, Math.min(99,
                    forge.gamemodes.limited.CardRanker.getRawScore(plains))));
            ok &= landClamped == 0;
        }

        final NeoDraft draft = NeoDraft.start(LimitedPoolType.Full);
        int sample = -1;
        int sampleAgain = -1;
        String sampleName = null;
        if (draft != null && !draft.currentCards().isEmpty()) {
            final PaperCard card = draft.currentCards().get(0);
            sampleName = card.getName();
            sample = (int) Math.round(Math.max(0, Math.min(99,
                    forge.gamemodes.limited.CardRanker.getRawScore(card))));
            sampleAgain = (int) Math.round(Math.max(0, Math.min(99,
                    forge.gamemodes.limited.CardRanker.getRawScore(card))));
            ok &= sample >= 0 && sample <= 99 && sample == sampleAgain;
        } else {
            ok = false;
        }

        System.out.println();
        System.out.printf(Locale.ROOT,
                "  Rating: Plains -> %d (tierra basica) | %s -> %d (repetido: %d)%n",
                landClamped, sampleName, sample, sampleAgain);
        System.out.println(ok
                ? "  OK - el rating de CardRanker es determinista y cabe en la pastilla"
                : "  FALLO - revisa DraftScreen.refresh/refreshRanking");
    }

    /**
     * Que un draft de UNA expansion traiga sobres de esa expansion.
     *
     * <p>Reportado jugando: <i>"los sobres que se abren llevan cartas de
     * expansiones diferentes"</i>. Era verdad y era lo esperable:
     * {@code LimitedPoolType.Full} arma sobres genericos con todas las cartas
     * de Forge, asi que en el mismo sobre salia una carta de 1994 y una de este
     * anyo. Un draft de verdad es de un set.
     *
     * <p>Se comprueba carta por carta y en los tres sobres, que es lo unico que
     * lo demuestra: mirar una captura del primero no dice nada — el fallo se
     * ve a la tercera carta rara, no a la primera.
     */
    private static void checkSetDraft(final String code, final int expectedRivals) {
        final forge.card.CardEdition set = forge.model.FModel.getMagicDb()
                .getEditions().get(code);
        if (set == null || !set.hasBoosterTemplate()) {
            System.out.println("  (sin " + code + ": me salto el draft de una expansion)");
            return;
        }

        final NeoDraft draft = NeoDraft.start(set);
        if (draft == null) {
            System.out.println("  FALLO: no se ha podido montar un draft de " + code);
            return;
        }

        // Todo lo que ESTA EDICION declara, hojas extra incluidas.
        //
        // Hace falta porque "de fuera" NO es "lleva otro codigo de edicion":
        // los sets modernos meten HOJAS EXTRA en el sobre y sus cartas se
        // imprimen con el codigo de OTRO set a proposito. El ejemplo con el
        // que se pillo: un sobre de Marvel's Spider-Man (SPM) saca
        // "Rest in Peace [MAR]" o "Feed the Swarm [MAR]" el 4,16% de las
        // veces, porque la edicion las declara en su seccion
        // [source material] y el hueco Common-Borderless tira de esa hoja.
        //
        // ⚠️ Y hacen falta las DOS listas, que no es evidente:
        //   · getAllCardsInSet() son las secciones que el motor conoce por su
        //     nombre ([cards], [borderless], [promo]...).
        //   · getPrintSheetsBySection() trae ademas las hojas HECHAS A MANO
        //     por el set ([source material], [scene cards], [webslinger]...),
        //     que el motor guarda aparte como "custom print sheets" y que
        //     getCardInSet NO ve. Con solo la primera, la prueba seguia
        //     dando FALLO — solo que ya no siempre, que es peor.
        //
        // La prueba sigue sirviendo para lo que se escribio (que los sobres no
        // sean genericos): una carta de 1994 en un sobre de hoy no esta
        // declarada en ninguna seccion ni hoja de este set.
        final java.util.Set<String> declared = new java.util.HashSet<>();
        for (final forge.card.CardEdition.EditionEntry e : set.getAllCardsInSet()) {
            declared.add(e.name());
        }
        for (final forge.card.PrintSheet sheet : set.getPrintSheetsBySection()) {
            declared.addAll(sheet.toNameLookup().keySet());
        }

        int seen = 0;
        int foreign = 0;
        String firstForeign = null;
        int rounds = 0;
        int lastRound = -1;
        while (!draft.isDone()) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            if (draft.round() != lastRound) {
                lastRound = draft.round();
                rounds++;
            }
            for (final PaperCard c : pack) {
                seen++;
                if (!code.equals(c.getEdition()) && !declared.contains(c.getName())) {
                    foreign++;
                    if (firstForeign == null) {
                        firstForeign = c.getName() + " [" + c.getEdition() + "]";
                    }
                }
            }
            draft.pick(pack.get(0));
        }

        // Y el evento que sale de ahi tiene que cuadrar con la mesa que pide
        // el set. Dos expansiones (Spider-Man y las Tortugas) declaran
        // RecommendedPodSize=4, o sea TRES rivales y no siete: si algo diera
        // por hecho el siete, se veria justo aqui.
        final String name = "neo-setcheck-" + code;
        borrar(name);
        final DeckGroup group = draft.save(name);
        final DraftRun run = DraftRun.of(name);
        final int rivals = run.opponents().size();

        final boolean ok = foreign == 0 && rounds == 3 && seen > 100
                && draft.productName().contains(code)
                && rivals == expectedRivals
                && group.getHumanDeck().getMain().countAll() == 0
                && group.getHumanDeck().get(DeckSection.Sideboard).countAll() >= 40
                && !run.isOver() && run.nextOpponent() != null;
        System.out.println();
        System.out.printf(Locale.ROOT,
                "  Draft de %s: %d rondas, %d cartas vistas, %d de fuera | %d rivales"
                + " (se esperaban %d) | mazo de %d%s%n",
                draft.productName(), rounds, seen, foreign, rivals, expectedRivals,
                group.getHumanDeck().getMain().countAll(),
                firstForeign == null ? "" : " | p.ej. " + firstForeign);
        System.out.println(ok
                ? "  OK - los tres sobres son de la expansion elegida y el evento cuadra"
                : "  FALLO - revisa el draft de una expansion");
        borrar(name);
    }

    /**
     * Que los siete rivales jueguen con lo que DRAFTEARON.
     *
     * <p>Es la pregunta que se hace cualquiera al terminar un draft: si el de
     * al lado lleva de verdad lo que le fui pasando o un mazo inventado. La
     * respuesta esta en el motor — {@code LimitedPlayerAI.buildDeck} monta cada
     * mazo con {@code BoosterDeckBuilder} sobre el pool de ESE jugador y los
     * colores que fue acumulando — pero eso no se ve jugando: solo se ve
     * comparando carta por carta.
     *
     * <p>Las tierras basicas no cuentan: no salen de ningun sobre, las anyade
     * el constructor de mazos hasta llegar a 40.
     */
    private static void checkRivals(final NeoDraft draft, final DeckGroup group) {
        final List<forge.deck.CardPool> pools = draft.rivalPools();
        final List<Deck> decks = group.getAiDecks();
        boolean ok = pools.size() == decks.size() && !decks.isEmpty();

        int checked = 0;
        int cards = 0;
        String culprit = null;
        for (int i = 0; i < decks.size() && i < pools.size(); i++) {
            final forge.deck.CardPool pool = pools.get(i);
            for (final java.util.Map.Entry<PaperCard, Integer> e : decks.get(i).getMain()) {
                if (e.getKey().getRules().getType().isBasicLand()) {
                    continue;
                }
                cards += e.getValue();
                if (pool.count(e.getKey()) < e.getValue()) {
                    ok = false;
                    if (culprit == null) {
                        culprit = "rival " + (i + 1) + ": " + e.getKey().getName();
                    }
                }
            }
            checked++;
        }

        System.out.println();
        System.out.printf(Locale.ROOT,
                "  Rivales: %d mazos, %d cartas (sin basicas) contrastadas con su pool%s%n",
                checked, cards, culprit == null ? "" : " | sobra " + culprit);
        System.out.println(ok
                ? "  OK - cada rival juega con las cartas que drafteo, montadas por el motor"
                : "  FALLO - hay cartas en los mazos rivales que no salieron del draft");
    }

    /**
     * Que el mazo del draft se pueda EDITAR y que lo editado se guarde.
     *
     * <p>Tres cosas que no se ven mirando la pantalla y que rompen el modo
     * entero si fallan:
     *
     * <ol>
     *   <li>El catalogo es tu pool: ni una carta de fuera, y las cinco basicas
     *       sin techo.</li>
     *   <li>El techo de copias es lo que draftaste. Meter una carta que no
     *       tienes tiene que dar motivo, no colarse.</li>
     *   <li>Guardar escribe dentro del EVENTO — el mazo humano cambia y los
     *       siete rivales siguen ahi. Si esto falla, editar el mazo se lleva
     *       por delante el draft.</li>
     * </ol>
     */
    private static void checkEditor() {
        final String name = "neo-editcheck";
        borrar(name);

        final NeoDraft draft = NeoDraft.start(LimitedPoolType.Full);
        if (draft == null) {
            System.out.println("  FALLO: no se ha podido montar el draft de prueba del editor.");
            return;
        }
        while (!draft.isDone()) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            draft.pick(pack.get(0));
        }
        draft.save(name);

        final DraftRun run = DraftRun.of(name);
        autoBuildSaved(run);
        final int poolAtStart = poolTotal(run.getDeck());
        final DraftDeckContext context = new DraftDeckContext(run);
        final forge.neo.deck.DeckEditor editor =
                forge.neo.deck.DeckEditor.copyOf(context, run.getDeck());

        boolean ok = context.isLimited() && !context.canRename();

        // 0. lo primero de todo: abrir el editor con el mazo recien montado NO
        //    puede dar un mazo ilegal. El motor lo monta con SealedDeckBuilder,
        //    y si ese devolviera OTRA impresion de la misma carta, el pool no
        //    la reconoceria: las 23 cartas del mazo saldrian marcadas como "no
        //    la tienes" y no se podria ni guardar.
        final List<PaperCard> bad = editor.illegalCards();
        if (!bad.isEmpty()) {
            System.out.printf(Locale.ROOT,
                    "  FALLO: %d cartas del mazo no se reconocen en el pool (p.ej. %s)%n",
                    bad.size(), bad.get(0).getName() + " [" + bad.get(0).getEdition() + "]");
        }
        ok &= bad.isEmpty() && editor.blockingProblem() == null && editor.isPlayable();

        // 1. el catalogo son tus picks y las basicas, NADA mas
        //
        // Y se comprueba sobre el BUSCADOR, no sobre la lista: el catalogo que
        // se ve sale de DeckEditor.find, y que el pool este bien no demuestra
        // que la busqueda no se vaya a las 33.000 cartas de Magic.
        int basics = 0;
        for (final PaperCard c : context.pool()) {
            if (c.getRules().getType().isBasicLand()) {
                basics++;
            }
        }
        ok &= basics == 5;
        ok &= context.pool().size() > basics;

        final java.util.List<PaperCard> catalogue =
                editor.find("", false, null, 5000, false).cards;
        int intruders = 0;
        String firstIntruder = null;
        for (final PaperCard c : catalogue) {
            if (c.getRules().getType().isBasicLand()) {
                continue;
            }
            // Mazo y banda son disjuntos: lo tuyo es la suma de los dos.
            if (run.getDeck().get(DeckSection.Sideboard).count(c) == 0
                    && run.getDeck().getMain().count(c) == 0) {
                intruders++;
                if (firstIntruder == null) {
                    firstIntruder = c.getName() + " [" + c.getEdition() + "]";
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "  Catalogo: %d cartas | de fuera del draft: %d%s%n",
                catalogue.size(), intruders,
                firstIntruder == null ? "" : " (p.ej. " + firstIntruder + ")");
        ok &= intruders == 0;

        // 2. el techo es lo que tienes
        final PaperCard drafted = run.getDeck().get(DeckSection.Sideboard).toFlatList().get(0);
        ok &= context.owned(drafted) >= 1;
        final PaperCard outsider = outsider(context);
        if (outsider != null) {
            ok &= context.owned(outsider) == 0
                    && editor.rejectionReason(outsider) != null
                    && editor.add(outsider, 1) == 0;
        }

        // 3. lo que se edita se guarda, y los rivales siguen ahi
        //
        // Se quita una carta que este DE VERDAD en el mazo: el pool son 45
        // cartas y solo 23 entraron, asi que la primera del pool casi nunca es
        // una de ellas — quitarla no quitaria nada y la comprobacion mentiria.
        final PaperCard swamp = basic(context, "Swamp");
        final PaperCard inDeck = firstSpellInDeck(editor.getDeck());
        final int before = editor.mainCount();
        final int added = swamp == null ? 0 : editor.add(swamp, 3);
        final int removed = inDeck == null ? 0 : 1;
        if (inDeck != null) {
            editor.remove(inDeck, 1);
        }
        editor.save();

        final DeckGroup saved = group(name);
        ok &= saved != null && saved.getAiDecks().size() == 7;
        if (saved != null) {
            final Deck human = saved.getHumanDeck();
            ok &= human.getName().equals(name);
            ok &= human.getMain().countAll() == before + added - removed;
            ok &= inDeck == null
                    || human.getMain().count(inDeck) == editor.getDeck().getMain().count(inDeck);
            ok &= swamp == null || human.getMain().count(swamp) >= added;
            // Y el pool sigue entero: editar el mazo no toca lo que draftaste.
            // Antes del 23-09-2026 quitar una carta del mazo de un draft la
            // DUPLICABA en la banda, y esta comprobacion comparaba el mazo
            // guardado consigo mismo, asi que no lo veia.
            ok &= poolTotal(human) == poolAtStart;
        }

        // 4. vaciar y volver a montar no crea ni destruye cartas
        final int cleared = editor.clearToPool();
        final boolean clearOk = editor.mainCount() == 0 && poolTotal(editor.getDeck()) == poolAtStart;
        final int rebuilt = editor.autoBuildFromPool();
        final boolean rebuildOk = rebuilt >= 40 && editor.isPlayable()
                && poolTotal(editor.getDeck()) == poolAtStart;
        System.out.printf(Locale.ROOT,
                "  Vaciar: %d cartas al pool (%s) | montar solo: %d cartas (%s) | pool %d%n",
                cleared, clearOk ? "bien" : "MAL", rebuilt, rebuildOk ? "bien" : "MAL",
                poolTotal(editor.getDeck()));
        ok &= clearOk && rebuildOk;

        System.out.println();
        System.out.printf(Locale.ROOT,
                "  Editor: catalogo de %d (5 basicas) | +%d pantanos | -%d | mazo %d -> %d%n",
                context.pool().size(), added, removed, before,
                saved == null ? -1 : saved.getHumanDeck().getMain().countAll());
        System.out.println(ok
                ? "  OK - el mazo del draft se edita con tu pool y se guarda en el evento"
                : "  FALLO - revisa DraftDeckContext");

        borrar(name);
    }

    /**
     * Un draft guardado con el formato de antes del 23-09-2026 — banda con el
     * pool ENTERO y el mazo como copia — se pasa al de ahora al abrirlo, una
     * sola vez, sin perder ni ganar cartas.
     */
    private static void checkOldFormat() {
        final String name = "neo-oldformatcheck";
        borrar(name);
        final NeoDraft draft = NeoDraft.start(LimitedPoolType.Full);
        if (draft == null) {
            System.out.println("  FALLO: no se ha podido montar el draft de prueba del formato viejo.");
            return;
        }
        while (!draft.isDone()) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            draft.pick(pack.get(0));
        }
        final DeckGroup group = draft.save(name);
        final Deck human = group.getHumanDeck();
        final int poolSize = poolTotal(human);

        // Como lo dejaba el codigo viejo: el mazo es una COPIA, la banda intacta.
        final forge.deck.CardPool built = LimitedAutoBuild.build(
                LimitedAutoBuild.poolOf(human), null);
        human.getMain().clear();
        if (built != null) {
            human.getMain().addAll(built);
        }
        forge.model.FModel.getDecks().getDraft().add(group);
        forge.neo.NeoSettings.set("draft." + name + ".split", null);
        forge.neo.NeoSettings.save();

        final DraftRun run = DraftRun.of(name);           // aqui se pasa
        final int afterFirst = poolTotal(run.getDeck());
        final int mainAfter = run.getDeck().getMain().countAll();
        final int afterSecond = poolTotal(DraftRun.of(name).getDeck());   // y no otra vez

        final boolean ok = built != null && afterFirst == poolSize && afterSecond == poolSize
                && mainAfter == built.countAll();
        System.out.printf(Locale.ROOT,
                "  Formato viejo: pool %d -> %d al abrirlo -> %d la segunda vez | mazo %d%n",
                poolSize, afterFirst, afterSecond, mainAfter);
        System.out.println(ok
                ? "  OK - un draft de antes se pasa al formato nuevo sin perder cartas"
                : "  FALLO - revisa DraftRun.splitIfNeeded");
        borrar(name);
    }

    /**
     * Las partidas de un evento, JUGADAS (el motor contesta por el humano), con
     * los mismos parametros que manda el marcador ({@code NeoAppDraft
     * .playEventMatch}): la tanda o una libre contra un rival, una libre
     * contra tres al azar a la vez, y al mejor de tres con banquillo.
     *
     * <p>Es lo que no se ve comprobando reglas: que el motor arranque una
     * partida de Draft a cuatro con mazos de 40, y que el Bo3 entre partidas
     * se lleve bien con el pool en la banda.
     */
    private static void checkGames() {
        final String name = "neo-gamescheck";
        borrar(name);
        final NeoDraft draft = NeoDraft.start(LimitedPoolType.Full);
        if (draft == null) {
            System.out.println("  FALLO: no se ha podido montar el draft de las partidas.");
            return;
        }
        while (!draft.isDone()) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            draft.pick(pack.get(0));
        }
        draft.save(name);
        final DraftRun run = DraftRun.of(name);
        final Deck mine = autoBuildSaved(run);
        final List<Deck> rivals = run.opponents();

        final forge.neo.match.NeoGame.Result one = play(mine, List.of(rivals.get(0)), 1);
        final List<Deck> shuffled = new java.util.ArrayList<>(rivals);
        java.util.Collections.shuffle(shuffled);
        final forge.neo.match.NeoGame.Result three = play(mine, shuffled.subList(0, 3), 1);
        final forge.neo.match.NeoGame.Result bo3 = play(mine, List.of(rivals.get(1)), 3);

        final boolean ok = done(one) && done(three) && done(bo3)
                && existe(name) && poolTotal(run.getDeck()) == poolTotal(mine);
        System.out.printf(Locale.ROOT,
                "  Partidas: 1v1 %s | 1 contra 3 %s | Bo3 %s%n",
                describe(one), describe(three), describe(bo3));
        System.out.println(ok
                ? "  OK - el mazo del evento se juega: uno contra uno, contra varios y al mejor de tres"
                : "  FALLO - revisa NeoAppDraft.playEventMatch / NeoGame con mazos de limitado");
        borrar(name);
    }

    private static boolean done(final forge.neo.match.NeoGame.Result r) {
        return r != null && r.completed && r.turns > 0;
    }

    private static String describe(final forge.neo.match.NeoGame.Result r) {
        return r == null ? "REVENTADA" : (r.completed ? "acabada" : "SIN ACABAR")
                + " en " + r.turns + " turnos, gana " + r.winner;
    }

    private static forge.neo.match.NeoGame.Result play(final Deck mine, final List<Deck> rivals,
                                                       final int games) {
        try {
            return forge.neo.match.NeoGame.play(mine, rivals.size(),
                    forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 240, false, null, null, true,
                    forge.neo.match.NeoFormat.DRAFT, rivals, games);
        } catch (final Exception e) {
            System.out.println("  FALLO: la partida ha reventado: " + e);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Pulsa "Montar solo" sobre el evento guardado y lo guarda, como haria el
     * jugador. Devuelve el mazo ya releido del almacen.
     */
    static Deck autoBuildSaved(final DraftRun run) {
        final forge.neo.deck.DeckEditor editor = forge.neo.deck.DeckEditor.copyOf(
                new DraftDeckContext(run), run.getDeck());
        editor.autoBuildFromPool();
        editor.save();
        return run.getDeck();
    }

    private static int basicsIn(final Deck deck) {
        int n = 0;
        for (final forge.deck.CardPool section : new forge.deck.CardPool[] {
                deck.get(DeckSection.Sideboard), deck.getMain()}) {
            for (final java.util.Map.Entry<PaperCard, Integer> e : section) {
                if (DraftDeckContext.isBasic(e.getKey())) {
                    n += e.getValue();
                }
            }
        }
        return n;
    }

    /** Las cartas del pool que no son basicas, mazo y banda juntos. */
    static int poolTotal(final Deck deck) {
        return LimitedAutoBuild.poolOf(deck).size();
    }

    /** La primera carta del mazo que no sea una tierra basica. */
    private static PaperCard firstSpellInDeck(final Deck deck) {
        for (final java.util.Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (!e.getKey().getRules().getType().isBasicLand()) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Una carta que NO esta en el pool, para probar que no se cuela. */
    private static PaperCard outsider(final DraftDeckContext context) {
        for (final PaperCard c : forge.model.FModel.getMagicDb().getCommonCards().getUniqueCards()) {
            if (!c.getRules().getType().isBasicLand() && context.owned(c) == 0) {
                return c;
            }
        }
        return null;
    }

    private static PaperCard basic(final DraftDeckContext context, final String name) {
        for (final PaperCard c : context.pool()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static DeckGroup group(final String name) {
        for (final DeckGroup g : forge.model.FModel.getDecks().getDraft()) {
            if (g.getName().equals(name)) {
                return g;
            }
        }
        return null;
    }

    /**
     * Las reglas del evento: dos derrotas y el draft se borra.
     *
     * <p>Es la parte que NO se puede comprobar jugando de verdad sin echar dos
     * partidas enteras, y la que peor se lleva un fallo: si el borrado no
     * funciona te quedas con un draft eliminado ocupando sitio; si funciona de
     * mas, te borra uno vivo.
     */
    private static void checkRun() {
        final String name = "neo-runcheck";
        borrar(name);

        final NeoDraft draft = NeoDraft.start(LimitedPoolType.Full);
        if (draft == null) {
            System.out.println("  FALLO: no se ha podido montar el draft de prueba del evento.");
            return;
        }
        while (!draft.isDone()) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            draft.pick(pack.get(0));
        }
        draft.save(name);

        final DraftRun run = DraftRun.of(name);
        // De fabrica NO es Arena: la tanda de Forge, que no borra nada.
        boolean ok = !run.isArena() && run.canChangeArena();

        // --- la tanda: se juega contra todos, gane o pierda, y se repite ---
        for (int i = 0; i < run.opponents().size(); i++) {
            run.record(i % 2 == 0);
        }
        final boolean gauntletOk = run.isCompleted() && !run.isEliminated() && existe(name)
                && run.getWins() + run.getLosses() == run.opponents().size()
                && !run.canChangeArena();
        run.restart();
        final boolean restartOk = run.getWins() == 0 && run.getLosses() == 0 && !run.isOver()
                && run.canChangeArena();
        System.out.printf(Locale.ROOT,
                "  Tanda: acaba tras %d partidas sin borrar nada: %s | se repite: %s%n",
                run.opponents().size(), gauntletOk ? "si" : "NO", restartOk ? "si" : "NO");
        ok &= gauntletOk && restartOk;

        // --- modo Arena: dos derrotas y se borra ---
        run.setArena(true);
        ok &= run.isArena() && run.opponents().size() == 7
                && !run.isOver()
                && run.nextOpponent() != null;

        run.record(true);                       // una victoria
        ok &= run.getWins() == 1 && !run.isOver();

        run.record(false);                      // primera derrota: sigue vivo
        ok &= run.getLosses() == 1 && !run.isOver() && existe(name);

        run.record(false);                      // segunda: fuera y borrado
        ok &= run.isEliminated() && run.isOver() && !existe(name);

        System.out.println();
        System.out.println(ok
                ? "  OK - el evento cuenta bien y a la segunda derrota borra el draft"
                : "  FALLO - las reglas del evento no se aplican");

        // Por si el borrado fallo, que no quede rastro en la carpeta del usuario.
        borrar(name);
    }

    /** Borra solo si existe: el almacen de Forge lanza NPE si no esta. */
    private static void borrar(final String name) {
        if (existe(name)) {
            forge.model.FModel.getDecks().getDraft().delete(name);
        }
        // Y su marca de formato (DraftRun.markSplit): no dejar rastro en el
        // neo.properties del jugador.
        for (final String field : new String[] {"split", "arena", "wins", "losses"}) {
            forge.neo.NeoSettings.set("draft." + name + "." + field, null);
        }
        forge.neo.NeoSettings.save();
    }

    private static boolean existe(final String name) {
        for (final DeckGroup g : forge.model.FModel.getDecks().getDraft()) {
            if (g.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
