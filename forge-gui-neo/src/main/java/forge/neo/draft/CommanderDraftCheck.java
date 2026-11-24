package forge.neo.draft;

import java.util.List;
import java.util.Locale;

import forge.deck.Deck;
import forge.deck.DeckGroup;
import forge.deck.DeckSection;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.deck.DeckEditor;

/**
 * {@code run.cmd cmddraftcheck}: un Commander Draft de punta a punta, sin
 * ventana. Ver {@link CommanderDraft}.
 *
 * <ol>
 * <li>Draftea Commander Legends (CMR) eligiendo siempre la primera.</li>
 * <li>Tu mazo lleva la marca y los siete rivales salen con comandante.</li>
 * <li>El editor: formato CommanderDraft, candidatos del pool mas los gratis,
 *     el comandante sale de la banda al ponerlo y vuelve al quitarlo.</li>
 * <li>Se monta un mazo de 60 dentro de su identidad y es legal.</li>
 * <li>Se juega contra un rival con las reglas de Commander (40 vidas).</li>
 * </ol>
 */
public final class CommanderDraftCheck {

    private CommanderDraftCheck() {
    }

    private static int passed;
    private static int failed;
    private static final String NAME = "neo-cmddraftcheck";

    public static void run() {
        passed = 0;
        failed = 0;
        final forge.card.CardEdition cmr = FModel.getMagicDb().getEditions().get("CMR");
        if (cmr == null) {
            throw new IllegalStateException("no esta Commander Legends (CMR)");
        }
        final NeoDraft draft = NeoDraft.start(cmr);
        check(draft != null, "el draft de CMR arranca");
        int picks = 0;
        while (draft != null && !draft.isDone() && picks < 400) {
            final List<PaperCard> pack = draft.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            draft.pick(pack.get(0));
            picks++;
        }
        check(draft != null && draft.isDone(), "el draft se acaba (" + picks + " picks)");
        if (FModel.getDecks().getDraft().contains(NAME)) {
            FModel.getDecks().getDraft().delete(NAME);
        }
        final DeckGroup group = draft.save(NAME);
        final Deck mine = group.getHumanDeck();

        // 2. La marca y los rivales.
        check(CommanderDraft.is(mine), "tu mazo lleva la marca CommanderDraft " + mine.getTags());
        int rivalsWithCommander = 0;
        for (final Deck ai : group.getAiDecks()) {
            if (ai.has(DeckSection.Commander) && !ai.get(DeckSection.Commander).isEmpty()) {
                rivalsWithCommander++;
            }
        }
        check(rivalsWithCommander == group.getAiDecks().size() && rivalsWithCommander > 0,
                "los rivales salen con comandante (" + rivalsWithCommander + " de "
                        + group.getAiDecks().size() + ")");

        // 3. El editor.
        final DraftDeckContext ctx = new DraftDeckContext(DraftRun.of(NAME));
        check(ctx.isCommanderDraft(), "el editor sabe que es un Commander Draft");
        check(ctx.deckFormat() == forge.deck.DeckFormat.CommanderDraft, "formato " + ctx.deckFormat());
        final DeckEditor editor = new DeckEditor(ctx, new Deck(mine, NAME));
        check(editor.usesCommander(), "el editor tiene zona de mando");
        final List<PaperCard> candidates = editor.commanderCandidates("", 500);
        check(candidates.stream().anyMatch(c -> c.getName().equals("The Prismatic Piper")),
                "The Prismatic Piper se ofrece gratis (" + candidates.size() + " candidatos)");
        PaperCard chosen = null;
        for (final PaperCard c : candidates) {
            if (!ctx.isFreeCommander(c) && mine.get(DeckSection.Sideboard).count(c) > 0
                    && c.getRules().getColorIdentity().countColors() >= 1) {
                chosen = c;
                break;
            }
        }
        if (chosen == null) {
            chosen = candidates.stream().filter(c -> !ctx.isFreeCommander(c)).findFirst().orElse(null);
        }
        check(chosen != null, "hay un comandante draftado (" + chosen + ")");
        if (chosen == null) {
            finish();
            return;
        }
        final int before = editor.getDeck().get(DeckSection.Sideboard).count(chosen);
        check(editor.setCommander(chosen), "se pone de comandante");
        check(editor.getDeck().get(DeckSection.Sideboard).count(chosen) == before - 1,
                "sale de la banda al ponerlo");
        editor.removeCommander(chosen);
        check(editor.getDeck().get(DeckSection.Sideboard).count(chosen) == before,
                "vuelve a la banda al quitarlo");
        editor.setCommander(chosen);

        // 4. Sesenta cartas en su identidad.
        final forge.card.ColorSet identity = chosen.getRules().getColorIdentity();
        for (final PaperCard c : ctx.pool()) {
            if (editor.getDeck().getMain().countAll() >= 40) {
                break;
            }
            if (c.getRules().getType().isBasicLand() || !c.getRules().getColorIdentity().hasNoColorsExcept(identity)) {
                continue;
            }
            editor.add(c, editor.roomFor(c));
        }
        final List<PaperCard> basics = ctx.pool().stream()
                .filter(c -> c.getRules().getType().isBasicLand())
                .filter(c -> c.getRules().getColorIdentity().isColorless()
                        || c.getRules().getColorIdentity().hasNoColorsExcept(identity))
                .filter(c -> !c.getRules().getColorIdentity().isColorless()
                        || identity.isColorless())
                .toList();
        int guard = 0;
        while (editor.getDeck().getMain().countAll() < 59 && !basics.isEmpty() && guard++ < 100) {
            editor.add(basics.get(guard % basics.size()), 1);
        }
        final String problem = ctx.conformanceProblem(editor.getDeck());
        check(problem == null, "el mazo de " + editor.getDeck().getMain().countAll()
                + " + comandante es legal (" + problem + ")");

        // 4b. "Montar solo": con el comandante puesto lo respeta, y sin
        //     comandante elige uno; los dos mazos tienen que ser legales.
        final DeckEditor auto = new DeckEditor(ctx, new Deck(mine, NAME));
        check(auto.canAutoBuild(), "«Montar solo» se ofrece en Commander Draft");
        final int sinCmd = auto.autoBuildFromPool();
        check(sinCmd > 0 && !auto.commanders().isEmpty(),
                "sin comandante elige uno y monta " + sinCmd + " (" + auto.commanders() + ")");
        final String autoProblem = ctx.conformanceProblem(auto.getDeck());
        check(autoProblem == null, "y el mazo montado es legal (" + autoProblem + ", "
                + auto.getDeck().getMain().countAll() + " + " + auto.commanders().size() + ")");
        final int poolBefore = mine.get(DeckSection.Sideboard).countAll() + mine.getMain().countAll();
        int poolAfter = auto.getDeck().get(DeckSection.Sideboard).countAll();
        for (final java.util.Map.Entry<PaperCard, Integer> e : auto.getDeck().getMain()) {
            if (!e.getKey().getRules().getType().isBasicLand()) {
                poolAfter += e.getValue();
            }
        }
        for (final PaperCard c : auto.commanders()) {
            if (!ctx.isFreeCommander(c)) {
                poolAfter++;
            }
        }
        check(poolAfter == poolBefore, "el pool no cambia de tamanyo (" + poolBefore + " -> " + poolAfter + ")");
        final DeckEditor withCmd = new DeckEditor(ctx, new Deck(mine, NAME));
        withCmd.setCommander(chosen);
        withCmd.autoBuildFromPool();
        check(withCmd.commanders().contains(chosen), "con comandante puesto, lo respeta (" + withCmd.commanders() + ")");
        final String withCmdProblem = ctx.conformanceProblem(withCmd.getDeck());
        check(withCmdProblem == null, "y tambien es legal (" + withCmdProblem + ")");

        // 5. La partida, con reglas de Commander.
        final boolean[] commanderRules = {false};
        final int[] startLife = {0};
        forge.neo.match.NeoGame.setOnUiForTest(ui -> {
            final Thread spy = new Thread(() -> {
                for (int i = 0; i < 200; i++) {
                    final forge.game.GameView gv = ui.getGameView();
                    if (gv != null && gv.getPlayers() != null && !gv.getPlayers().isEmpty()) {
                        commanderRules[0] = gv.isCommander();
                        startLife[0] = gv.getPlayers().iterator().next().getLife();
                        return;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (final InterruptedException e) {
                        return;
                    }
                }
            }, "neo-cmddraftcheck-spy");
            spy.setDaemon(true);
            spy.start();
        });
        final forge.neo.match.NeoGame.Result r = forge.neo.match.NeoGame.play(editor.getDeck(), 1,
                forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 60, false, null, null, true,
                forge.neo.match.NeoFormat.DRAFT, List.of(group.getAiDecks().get(0)), 1,
                CommanderDraft.seating());
        forge.neo.match.NeoGame.setOnUiForTest(null);
        check(r != null && r.turns > 0, "la partida se juega (" + (r == null ? "-" : r.turns + " turnos") + ")");
        check(commanderRules[0], "con las reglas de Commander");
        check(startLife[0] == 40, "y 40 vidas (" + startLife[0] + ")");

        // 6. A partidas de cuatro (Discord, 17-11-2026: "4-player combats
        //    instead of 7 games of 1v1").
        final DraftRun run = DraftRun.of(NAME);
        run.restart();
        check(run.canPlayPods() && !run.isPods(), "se puede jugar a cuatro, y de fabrica no ("
                + run.opponents().size() + " rivales)");
        check(run.games() == run.opponents().size(), "a uno contra uno, una partida por rival (" + run.games() + ")");
        run.setPods(true);
        check(run.isPods() && run.games() == 3, "a cuatro con siete rivales son tres partidas (" + run.games() + ")");
        check(run.nextOpponentIndexes().equals(List.of(0, 1, 2)) && run.nextOpponents().size() == 3,
                "la primera, contra los rivales 1-2-3 " + run.nextOpponentIndexes());
        run.record(true);
        check(run.nextOpponentIndexes().equals(List.of(3, 4, 5)), "la segunda, contra 4-5-6 " + run.nextOpponentIndexes());
        run.record(false);
        check(run.nextOpponentIndexes().equals(List.of(6, 0, 1)) && run.getRemaining() == 1,
                "la tercera, el 7 con dos de los primeros " + run.nextOpponentIndexes()
                        + ", y queda una (" + run.getRemaining() + ")");
        run.record(true);
        check(run.isCompleted() && run.getRemaining() == 0, "tras tres partidas la tanda se acaba");
        run.restart();
        final List<Deck> pod = run.nextOpponents();
        final int[] seats = {0};
        forge.neo.match.NeoGame.setOnUiForTest(ui -> {
            final Thread spy = new Thread(() -> {
                for (int i = 0; i < 200; i++) {
                    final forge.game.GameView gv = ui.getGameView();
                    if (gv != null && gv.getPlayers() != null && !gv.getPlayers().isEmpty()) {
                        seats[0] = gv.getPlayers().size();
                        return;
                    }
                    try {
                        Thread.sleep(50);
                    } catch (final InterruptedException e) {
                        return;
                    }
                }
            }, "neo-cmddraftcheck-pods");
            spy.setDaemon(true);
            spy.start();
        });
        final forge.neo.match.NeoGame.Result r4 = forge.neo.match.NeoGame.play(editor.getDeck(), pod.size(),
                forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 90, false, null, null, true,
                forge.neo.match.NeoFormat.DRAFT, pod, 1, CommanderDraft.seating());
        forge.neo.match.NeoGame.setOnUiForTest(null);
        check(r4 != null && r4.turns > 0 && seats[0] == 4, "y la partida de cuatro se juega ("
                + seats[0] + " jugadores, " + (r4 == null ? "-" : r4.turns + " turnos") + ")");
        run.setPods(false);

        // 7. Un CUBO como Commander Draft (Discord, 17-11-2026: "is there any
        //    way to play a commander cube?"). Ver CommanderCubeDraft.
        cubeAsCommander();

        // 8. Un rival con comandante INCOLORO: el motor no sabe montarle el
        //    mazo y antes se perdia el draft entero al guardarlo.
        colorlessCommander();

        if (Boolean.getBoolean("neo.cmddraft.keep")) {
            // Para capturar el editor: el draft se queda, con el comandante y el
            // mazo puestos, como evento en curso (run.cmd ui --draft-deck).
            final DeckGroup kept = FModel.getDecks().getDraft().get(NAME);
            kept.setHumanDeck(new Deck(editor.getDeck(), NAME));
            FModel.getDecks().getDraft().add(kept);
            DraftRun.of(NAME).makeCurrent();
            System.out.println("  (el draft se queda como evento en curso: " + NAME + ")");
        } else {
            FModel.getDecks().getDraft().delete(NAME);
        }
        finish();
    }

    private static final String CUBE_NAME = "neo-cmdcubecheck";

    private static void cubeAsCommander() {
        final NeoDraft plain = NeoDraft.startCube("Legendary Cube");
        check(plain != null, "el Legendary Cube arranca como draft normal");
        if (plain != null) {
            for (int i = 0; i < 400 && !plain.isDone(); i++) {
                final List<PaperCard> pack = plain.currentCards();
                if (pack.isEmpty()) {
                    break;
                }
                plain.pick(pack.get(0));
            }
            if (FModel.getDecks().getDraft().contains(CUBE_NAME)) {
                FModel.getDecks().getDraft().delete(CUBE_NAME);
            }
            final DeckGroup g = plain.save(CUBE_NAME);
            check(!CommanderDraft.is(g.getHumanDeck()), "y sin la casilla NO es Commander Draft (como siempre)");
            FModel.getDecks().getDraft().delete(CUBE_NAME);
        }

        final NeoDraft cube = NeoDraft.startCube("Legendary Cube", true);
        check(cube != null, "con la casilla, el cubo arranca como Commander Draft");
        if (cube == null) {
            return;
        }
        int picks = 0;
        while (!cube.isDone() && picks < 400) {
            final List<PaperCard> pack = cube.currentCards();
            if (pack.isEmpty()) {
                break;
            }
            cube.pick(pack.get(0));
            picks++;
        }
        check(cube.isDone(), "el draft del cubo se acaba (" + picks + " picks)");
        if (FModel.getDecks().getDraft().contains(CUBE_NAME)) {
            FModel.getDecks().getDraft().delete(CUBE_NAME);
        }
        final DeckGroup group = cube.save(CUBE_NAME);
        final Deck mine = group.getHumanDeck();
        check(CommanderDraft.is(mine), "tu mazo lleva la marca CommanderDraft " + mine.getTags());
        int withCommander = 0;
        for (final Deck ai : group.getAiDecks()) {
            if (ai.has(DeckSection.Commander) && !ai.get(DeckSection.Commander).isEmpty()) {
                withCommander++;
            }
        }
        check(withCommander == group.getAiDecks().size() && withCommander > 0,
                "los rivales del cubo salen con comandante (" + withCommander + " de " + group.getAiDecks().size() + ")");
        final DraftDeckContext ctx = new DraftDeckContext(DraftRun.of(CUBE_NAME));
        check(ctx.isCommanderDraft() && ctx.deckFormat() == forge.deck.DeckFormat.CommanderDraft,
                "el editor lo trata como Commander Draft");
        final DeckEditor built = new DeckEditor(ctx, new Deck(mine, CUBE_NAME));
        built.autoBuildFromPool();
        final String problem = ctx.conformanceProblem(built.getDeck());
        check(!built.commanders().isEmpty() && problem == null, "«Montar solo» le pone comandante y sale legal ("
                + built.commanders() + (problem == null ? "" : ", " + problem) + ")");
        final forge.neo.match.NeoGame.Result r = forge.neo.match.NeoGame.play(built.getDeck(), 1,
                forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 60, false, null, null, true,
                forge.neo.match.NeoFormat.DRAFT, List.of(group.getAiDecks().get(0)), 1, CommanderDraft.seating());
        check(r != null && r.turns > 0, "y se juega una partida con el (" + (r == null ? "-" : r.turns + " turnos") + ")");
        FModel.getDecks().getDraft().delete(CUBE_NAME);
    }

    /**
     * Un rival con comandante INCOLORO (24-11-2026, cerrando la 1.0.65: la
     * bateria fallo una vez con "Add Lands to empty deck list!" al guardar el
     * draft del Legendary Cube). La IA fija sus colores a la identidad del
     * comandante, y con un artefacto legendario el constructor de Forge cuenta
     * cero simbolos de color y revienta. En el cubo depende de que sobre le
     * toque a cada IA; aqui se provoca siempre:
     *
     * <ol>
     * <li>un asiento suelto con Memnarch de comandante y un pool con algo de
     *     color: el motor revienta y {@link NeoDraft#rivalDeck} le da otro
     *     comandante de su pool;</li>
     * <li>uno con todo incoloro: mazo a mano con Yermos;</li>
     * <li>y entero, por el camino del jugador: un cubo de solo artefactos
     *     incoloros, drafteado como Commander Draft y guardado.</li>
     * </ol>
     */
    private static void colorlessCommander() {
        final forge.card.CardDb db = FModel.getMagicDb().getCommonCards();
        final PaperCard memnarch = db.getCard("Memnarch");
        final List<String> colourless = List.of("Sol Ring", "Mind Stone", "Arcane Signet", "Wayfarer's Bauble",
                "Myr Retriever", "Ornithopter", "Steel Overseer", "Solemn Simulacrum", "Burnished Hart",
                "Palladium Myr", "Pilgrim's Eye", "Wurmcoil Engine");
        final List<String> coloured = List.of("Krenko, Mob Boss", "Goblin Guide", "Lightning Bolt",
                "Mogg Fanatic", "Raging Goblin", "Shock", "Lava Spike", "Goblin Bushwhacker");

        // 1 y 2: asientos sueltos, sin draft detras
        for (final boolean withColour : new boolean[] {true, false}) {
            final forge.gamemodes.limited.CommanderLimitedPlayerAI seat =
                    new forge.gamemodes.limited.CommanderLimitedPlayerAI(1, null);
            seat.receiveOpenedPack(new forge.gamemodes.limited.DraftPack(new java.util.ArrayList<>(List.of(memnarch)), 1));
            final PaperCard pickedCommander = seat.chooseCard();
            final forge.deck.CardPool pool = seat.getDeck().getOrCreate(DeckSection.Sideboard);
            pool.add(memnarch);
            for (final String n : colourless) {
                pool.add(db.getCard(n));
            }
            if (withColour) {
                for (final String n : coloured) {
                    pool.add(db.getCard(n));
                }
            }
            final String what = withColour ? "con algo de color en el pool" : "con todo el pool incoloro";
            boolean engineFails;
            try {
                seat.buildDeck(null);
                engineFails = false;
            } catch (final RuntimeException e) {
                engineFails = "Add Lands to empty deck list!".equals(e.getMessage());
            }
            // Que el motor reviente depende del pool (con estas cartas solo
            // revienta el todo incoloro); el que tiene que salir siempre en rojo
            // si se rompe es el cubo de abajo, que lo provoca en los siete.
            System.out.println(engineFails
                    ? "  (el motor sigue reventando con Memnarch de comandante, " + what + ")"
                    : "  (el motor monta solo el mazo de Memnarch, " + what + ": no hace falta el de repuesto)");
            final int before = NeoDraft.FALLBACKS.get();
            final Deck d = NeoDraft.rivalDeck(seat, null, 1);
            final forge.deck.CardPool cmd = d.get(DeckSection.Commander);
            int lands = 0;
            for (final java.util.Map.Entry<PaperCard, Integer> e : d.getMain()) {
                if (e.getKey().getRules().getType().isLand()) {
                    lands += e.getValue();
                }
            }
            final int total = d.getMain().countAll() + (cmd == null ? 0 : cmd.countAll());
            final boolean ok = memnarch.equals(pickedCommander) && cmd != null && !cmd.isEmpty()
                    && lands > 0 && total >= 40
                    && (!engineFails || NeoDraft.FALLBACKS.get() == before + 1)
                    && (withColour || !engineFails || cmd.contains(memnarch));
            check(ok, "comandante incoloro, " + what + ": el rival sale con mazo (" + (cmd == null ? "-" : cmd.toFlatList())
                    + ", " + total + " cartas, " + lands + " tierras)");
        }

        // 3: el camino del jugador, con un cubo de solo incoloros
        final String before = System.getProperty("neo.cubes.dir");
        final java.io.File dir;
        try {
            dir = java.nio.file.Files.createTempDirectory("neo-cubo-incoloro").toFile();
        } catch (final java.io.IOException e) {
            check(false, "no se ha podido crear la carpeta del cubo incoloro: " + e);
            return;
        }
        System.setProperty("neo.cubes.dir", dir.getPath());
        final String saved = "neo-cmdcubecheck-incoloro";
        try {
            final List<PaperCard> legends = new java.util.ArrayList<>();
            final List<PaperCard> rest = new java.util.ArrayList<>();
            for (final PaperCard c : db.getUniqueCards()) {
                final forge.card.CardRules r = c.getRules();
                if (!r.getColorIdentity().isColorless() || r.getType().isLand() || !r.getType().isArtifact()
                        || c.getName().contains("//") || c.getName().startsWith("A-")) {
                    continue;
                }
                (r.canBeCommander() ? legends : rest).add(c);
            }
            final java.util.Comparator<PaperCard> byName = java.util.Comparator.comparing(PaperCard::getName);
            legends.sort(byName);
            rest.sort(byName);
            final StringBuilder dck = new StringBuilder("[metadata]\nName=Cubo incoloro NeoForge\n[Main]\n");
            int n = 0;
            for (final PaperCard c : legends) {
                if (n >= 150) {
                    break;
                }
                dck.append("1 ").append(c.getName()).append('\n');
                n++;
            }
            for (final PaperCard c : rest) {
                if (n >= 360) {
                    break;
                }
                dck.append("1 ").append(c.getName()).append('\n');
                n++;
            }
            final String cubeName;
            try {
                cubeName = CubeCobra.save("zz-incoloro", dck.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } catch (final CubeCobra.ImportException e) {
                check(false, "el cubo incoloro no se ha guardado: " + e);
                return;
            }
            final NeoDraft draft = NeoDraft.startCube(cubeName, true);
            check(draft != null, "un cubo de solo artefactos incoloros arranca como Commander Draft (" + n + " cartas)");
            if (draft == null) {
                return;
            }
            int picks = 0;
            while (!draft.isDone() && picks < 400) {
                final List<PaperCard> pack = draft.currentCards();
                if (pack.isEmpty()) {
                    break;
                }
                draft.pick(pack.get(0));
                picks++;
            }
            if (FModel.getDecks().getDraft().contains(saved)) {
                FModel.getDecks().getDraft().delete(saved);
            }
            final int fallbacksBefore = NeoDraft.FALLBACKS.get();
            DeckGroup group = null;
            try {
                group = draft.save(saved);
            } catch (final RuntimeException e) {
                check(false, "guardar el draft del cubo incoloro revienta: " + e);
                return;
            }
            final int fell = NeoDraft.FALLBACKS.get() - fallbacksBefore;
            int good = 0;
            for (final Deck ai : group.getAiDecks()) {
                final forge.deck.CardPool cmd = ai.get(DeckSection.Commander);
                if (cmd != null && !cmd.isEmpty() && ai.getMain().countAll() >= 40) {
                    good++;
                }
            }
            check(FModel.getDecks().getDraft().contains(saved) && good == group.getAiDecks().size() && good > 0,
                    "y se guarda, con los " + good + " de " + group.getAiDecks().size()
                            + " rivales con comandante y mazo (" + fell + " montados de repuesto)");
            final forge.neo.match.NeoGame.Result r = forge.neo.match.NeoGame.play(group.getAiDecks().get(0), 1,
                    forge.neo.match.NeoMatchUI.Mode.AUTO_PLAY, 30, false, null, null, true,
                    forge.neo.match.NeoFormat.DRAFT, List.of(group.getAiDecks().get(1)), 1, CommanderDraft.seating());
            check(r != null && r.turns > 0, "y dos de esos mazos juegan una partida ("
                    + (r == null ? "-" : r.turns + " turnos") + ")");
        } finally {
            if (FModel.getDecks().getDraft().contains(saved)) {
                FModel.getDecks().getDraft().delete(saved);
            }
            if (before == null) {
                System.clearProperty("neo.cubes.dir");
            } else {
                System.setProperty("neo.cubes.dir", before);
            }
            final java.io.File[] left = dir.listFiles();
            for (final java.io.File f : left == null ? new java.io.File[0] : left) {
                f.delete();
            }
            dir.delete();
        }
    }

    private static void finish() {
        System.out.printf(Locale.ROOT, "%n  %d bien, %d mal%n", passed, failed);
        if (failed > 0) {
            throw new IllegalStateException(failed + " comprobacion(es) del Commander Draft han fallado");
        }
    }

    private static void check(final boolean ok, final String what) {
        if (ok) {
            passed++;
            System.out.println("  OK   " + what);
        } else {
            failed++;
            System.out.println("  MAL  " + what);
        }
    }
}
