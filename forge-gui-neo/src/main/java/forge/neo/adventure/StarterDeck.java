package forge.neo.adventure;

import com.badlogic.gdx.Gdx;
import forge.adventure.player.AdventurePlayer;
import forge.adventure.util.AdventureModes;
import forge.adventure.world.WorldSave;
import forge.card.ColorSet;
import forge.deck.CardPool;
import forge.deck.CardRelationMatrixGenerator;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.deck.DeckProxy;
import forge.deck.DeckSection;
import forge.deck.DeckgenUtil;
import forge.item.PaperCard;
import forge.model.FModel;
import forge.neo.NeoSettings;
import forge.util.MyRandom;
import javafx.application.Platform;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * El mazo de salida de una Aventura NUEVA en modo Commander: eliges entre tres
 * comandantes y se monta el mazo alrededor del elegido.
 *
 * <p>El Adventure de Forge da en ese modo <b>un mazo fijo por color</b>
 * ({@code commanderDecks} de {@code res/adventure/common/config.json}): empezar
 * tres partidas en negro era jugar tres veces con Tymaret y las mismas 99
 * (reportado el 17-09-2026). Decidido con Ana:
 * <ul>
 *   <li><b>El color lo sigue eligiendo el Adventure</b>, y sigue siendo uno: lo
 *       fija su propia pantalla y su config, que no se tocan.</li>
 *   <li><b>Tres comandantes al azar de los 100 mono-color mas jugados</b> de ese
 *       color segun EDHREC ({@code commanders-X.txt}, que baja
 *       {@code tools/bajar-comandantes.py}; van dentro del jar para que valga
 *       sin internet).</li>
 *   <li><b>El mazo, con el generador del boton "Generar mazo"</b>
 *       ({@code isCardGen}): tira de la matriz de mazos reales de Commander que
 *       trae Forge, asi que sale coherente con el comandante y no son 99 cartas
 *       sueltas del color. <b>Bracket 2</b>, como los mazos fijos.</li>
 *   <li><b>Ajuste en NeoForge</b> (Ajustes → Aventura), encendido de fabrica;
 *       apagado, el mazo fijo de siempre.</li>
 * </ul>
 *
 * <p>Sin tocar Forge: {@code WorldSave.generateNewWorld} emite la senal publica
 * {@code onLoad} justo despues de {@code player.create}. La misma senal sale al
 * CARGAR una partida, asi que se mira la pila y solo se actua si viene de
 * {@code generateNewWorld}: una partida guardada no se toca nunca. Y si algo
 * falla, se queda el mazo fijo, que ya esta puesto.
 */
final class StarterDeck {

    private StarterDeck() {
    }

    /** El techo de potencia, en la escala oficial de brackets de Commander. */
    private static final int MAX_BRACKET = 2;

    private static final int OPTIONS = 3;

    private static final int TRIES = 4;

    /** Menos tierras que esto es un mazo que no arranca (el fijo lleva 37 + 2). */
    private static final int MIN_LANDS = 32;

    /**
     * Cuantas tierras se dejan. El generador del motor pone 41; los mazos fijos
     * del Adventure llevan 39, y ese es el numero que se queda (decidido
     * jugando el 17-09-2026). Las de mas se cambian por hechizos, o el mazo se
     * quedaria en 97 cartas y no seria legal.
     */
    private static final int WANT_LANDS = 39;

    static void arm() {
        if ("false".equalsIgnoreCase(System.getProperty("neo.adventure.randomStarter"))) {
            return;
        }
        final Thread t = new Thread(() -> {
            final long t0 = System.currentTimeMillis();
            while (System.currentTimeMillis() - t0 < 180_000) {
                try {
                    if (Gdx.app != null && FModel.getMagicDb() != null) {
                        // El ajuste se lee aqui y no antes: NeoSettings busca su
                        // fichero con ForgeConstants, que sin Forge arrancado revienta.
                        if (!NeoSettings.adventureStarter()) {
                            NeoDuelBridge.log("mazo de salida: el fijo de Forge (ajuste apagado)");
                            return;
                        }
                        Gdx.app.postRunnable(() -> WorldSave.getCurrentSave().onLoad(StarterDeck::onLoad));
                        return;
                    }
                } catch (final Throwable ignored) {
                    // todavia cargando
                }
                try {
                    Thread.sleep(500);
                } catch (final InterruptedException e) {
                    return;
                }
            }
            NeoDuelBridge.log("mazo de salida: el Adventure no termina de cargar, se queda el fijo");
        }, "neo-adventure-starter");
        t.setDaemon(true);
        t.start();
    }

    private static void onLoad() {
        try {
            if (!fromNewWorld()) {
                return;
            }
            final AdventurePlayer player = WorldSave.getCurrentSave().getPlayer();
            if (player.getAdventureMode() != AdventureModes.Commander) {
                return;
            }
            final ColorSet color = commanderIdentity(player.getSelectedDeck());
            if (color == null) {
                return;
            }
            final List<PaperCard> options = options(color);
            if (options.isEmpty()) {
                NeoDuelBridge.log("mazo de salida: ningun comandante de " + color + ", se queda el fijo");
                return;
            }
            // Al frame siguiente, desde el hilo de libGDX: es de donde hay que
            // tomar la ventana (NeoWindow.takeOver guarda ese hilo), y para
            // entonces la pantalla de nueva partida ya ha terminado lo suyo.
            Gdx.app.postRunnable(() -> openPicker(player, color, options));
        } catch (final Throwable e) {
            // Nunca tumbar la creacion de la partida por esto: se queda el fijo.
            NeoDuelBridge.log("mazo de salida: no se ha podido ofrecer, se queda el fijo: " + e);
        }
    }

    /** Si esta senal la ha lanzado una partida nueva, y no una carga. */
    static boolean fromNewWorld() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(f ->
                "generateNewWorld".equals(f.getMethodName())
                        && f.getClassName().endsWith("WorldSave")));
    }

    private static void openPicker(final AdventurePlayer player, final ColorSet color,
                                   final List<PaperCard> options) {
        NeoDuelBridge.log("mazo de salida: ofreciendo " + options);
        NeoWindow.takeOver();
        final Runnable back = () -> NeoWindow.giveBack(() -> { });
        Platform.runLater(() -> {
            try {
                final CommanderPickScreen[] screen = new CommanderPickScreen[1];
                screen[0] = new CommanderPickScreen(options, NeoWindow.cardWidth() * 2.8,
                        pc -> build(player, color, pc, screen[0], back),
                        () -> {
                            NeoDuelBridge.log("mazo de salida: el jugador se queda el fijo");
                            back.run();
                        });
                NeoWindow.show(screen[0], "NeoForge · Adventure");
                NeoWindow.scene().setOnKeyPressed(null);
                NeoWindow.scene().setOnKeyReleased(null);
            } catch (final Throwable e) {
                NeoDuelBridge.log("mazo de salida: no se pudo abrir la eleccion: " + e);
                e.printStackTrace();
                back.run();
            }
        });
    }

    /** Monta el mazo fuera del hilo de interfaz y lo pone en el hilo de libGDX. */
    private static void build(final AdventurePlayer player, final ColorSet color, final PaperCard cmd,
                              final CommanderPickScreen screen, final Runnable back) {
        final Thread t = new Thread(() -> {
            final Deck gen = generate(cmd, color);
            if (gen == null) {
                Platform.runLater(screen::failed);
                sleep(2500);
                back.run();
                return;
            }
            Gdx.app.postRunnable(() -> {
                try {
                    apply(player, gen);
                } catch (final Throwable e) {
                    NeoDuelBridge.log("mazo de salida: no se pudo poner, se queda el fijo: " + e);
                }
                back.run();
            });
        }, "neo-adventure-starter-build");
        t.setDaemon(true);
        t.start();
    }

    /** El mazo para este comandante, o {@code null} si ningun intento vale. */
    static Deck generate(final PaperCard cmd, final ColorSet color) {
        for (int i = 0; i < TRIES; i++) {
            // Los ultimos intentos, sin la matriz: menos tematico, pero es
            // mejor que quedarse sin mazo por un comandante raro.
            final boolean cardGen = i < TRIES - 1 && inMatrix(cmd);
            final Deck gen;
            try {
                gen = DeckgenUtil.generateRandomCommanderDeck(
                        cmd, DeckFormat.Commander, false, cardGen, MAX_BRACKET);
            } catch (final RuntimeException e) {
                NeoDuelBridge.log("mazo de salida: el generador ha fallado con " + cmd.getName() + ": " + e);
                continue;
            }
            trimLands(gen, cmd);
            final String why = problem(gen, color);
            if (why == null) {
                NeoDuelBridge.log("mazo de salida generado: " + cmd.getName() + " ("
                        + (cardGen ? "coherente" : "por color") + ", intento " + (i + 1) + ")");
                return gen;
            }
            NeoDuelBridge.log("mazo de salida: descartado para " + cmd.getName() + " (" + why + ")");
        }
        return null;
    }

    /**
     * Deja el mazo en {@link #WANT_LANDS} tierras, cambiando las que sobran por
     * hechizos que el generador propone para ESE comandante.
     *
     * <p>Se quitan tierras <b>basicas</b> y solo se quitan: una no basica puede
     * ser media razon por la que el mazo funciona. Y por cada una que sale,
     * entra un hechizo, porque 99 cartas exactas es una regla del formato.
     */
    private static void trimLands(final Deck deck, final PaperCard cmd) {
        final List<PaperCard> basics = new ArrayList<>();
        int lands = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                lands += e.getValue();
                if (e.getKey().isVeryBasicLand()) {
                    for (int i = 0; i < e.getValue(); i++) {
                        basics.add(e.getKey());
                    }
                }
            }
        }
        int extra = Math.min(lands - WANT_LANDS, basics.size());
        if (extra <= 0) {
            return;
        }
        // Los hechizos de repuesto salen de OTRO mazo del mismo comandante: es
        // el mismo pozo tematico, sin tener que replicar aqui como se elige.
        final List<PaperCard> spare = new ArrayList<>();
        try {
            final Deck other = DeckgenUtil.generateRandomCommanderDeck(
                    cmd, DeckFormat.Commander, false, true, MAX_BRACKET);
            for (final Map.Entry<PaperCard, Integer> e : other.getMain()) {
                final PaperCard c = e.getKey();
                if (!c.getRules().getType().isLand() && deck.getMain().count(c) == 0
                        && !c.getName().equals(cmd.getName())) {
                    spare.add(c);
                }
            }
        } catch (final RuntimeException e) {
            NeoDuelBridge.log("mazo de salida: sin hechizos de repuesto: " + e);
        }
        Collections.shuffle(spare, MyRandom.getRandom());
        extra = Math.min(extra, spare.size());
        for (int i = 0; i < extra; i++) {
            deck.getMain().remove(basics.get(i), 1);
            deck.getMain().add(spare.get(i), 1);
        }
        NeoDuelBridge.log("mazo de salida: " + extra + " tierras cambiadas por hechizos ("
                + lands + " -> " + (lands - extra) + ")");
    }

    /**
     * Pone el mazo generado en el lugar del fijo. Hilo de libGDX.
     *
     * <p>El mismo {@code Deck} por dentro: es el objeto que el jugador tiene en
     * su ranura 0 y como mazo elegido, y conserva su nombre. Y la coleccion:
     * {@code create()} metio las cartas del fijo; se cambian por las del nuevo,
     * o te quedarias con 99 cartas regaladas de mas.
     */
    static void apply(final AdventurePlayer player, final Deck gen) {
        final Deck fixed = player.getSelectedDeck();
        final CardPool before = fixed.getAllCardsInASinglePool(true, true);
        fixed.getMain().clear();
        fixed.getMain().addAll(gen.getMain());
        fixed.getOrCreate(DeckSection.Commander).clear();
        fixed.getOrCreate(DeckSection.Commander).addAll(gen.get(DeckSection.Commander));
        player.getCards().removeAll(before);
        player.getCards().addAllFlat(fixed.getAllCardsInASinglePool(true, true).toFlatList());
        player.setColorIdentity(DeckProxy.getColorIdentity(fixed));
    }

    /** La identidad del comandante del mazo fijo, que es el color que se eligio. */
    private static ColorSet commanderIdentity(final Deck deck) {
        if (deck == null || deck.get(DeckSection.Commander) == null
                || deck.get(DeckSection.Commander).isEmpty()) {
            return null;
        }
        return deck.get(DeckSection.Commander).get(0).getRules().getColorIdentity();
    }

    /** Tres al azar de la lista de ese color. */
    static List<PaperCard> options(final ColorSet color) {
        final List<PaperCard> pool = pool(color);
        Collections.shuffle(pool, MyRandom.getRandom());
        return new ArrayList<>(pool.subList(0, Math.min(OPTIONS, pool.size())));
    }

    /**
     * La lista de EDHREC de ese color, quitando lo que Forge no puede ofrecer:
     * lo que no tiene, lo que no es de exactamente ese color y lo que puede
     * llevar companero (el companero lo sortea el generador, y podria traer
     * otro color).
     */
    static List<PaperCard> pool(final ColorSet color) {
        final List<PaperCard> out = new ArrayList<>();
        final String letter = letter(color);
        if (letter == null) {
            return out;
        }
        try (InputStream in = StarterDeck.class.getResourceAsStream(
                "/forge/neo/adventure/commanders-" + letter + ".txt")) {
            if (in == null) {
                return out;
            }
            final BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                // EDHREC escribe las de dos caras enteras; Forge las conoce por la de arriba.
                final int dfc = line.indexOf(" // ");
                final String name = dfc > 0 ? line.substring(0, dfc) : line;
                final PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(name);
                if (pc == null || !pc.getRules().canBeCommander() || pc.getRules().canBePartnerCommander()) {
                    continue;
                }
                if (pc.getRules().getColorIdentity().getColor() != color.getColor()) {
                    continue;
                }
                out.add(pc);
            }
        } catch (final Exception e) {
            NeoDuelBridge.log("mazo de salida: no se pudo leer la lista de " + letter + ": " + e);
        }
        return out;
    }

    private static String letter(final ColorSet color) {
        for (final String l : new String[] {"W", "U", "B", "R", "G"}) {
            if (ColorSet.fromNames(l.toCharArray()).getColor() == color.getColor()) {
                return l;
            }
        }
        return null;
    }

    /** Si el generador coherente conoce a este comandante (y la matriz esta cargada). */
    private static boolean inMatrix(final PaperCard cmd) {
        synchronized (StarterDeck.class) {
            if (CardRelationMatrixGenerator.cardPools.get(DeckFormat.Commander.toString()) == null) {
                try {
                    CardRelationMatrixGenerator.initializeFormat(DeckFormat.Commander);
                } catch (final RuntimeException e) {
                    NeoDuelBridge.log("mazo de salida: sin matriz de Commander: " + e);
                }
            }
        }
        final Map<String, ?> m = CardRelationMatrixGenerator.cardPools.get(DeckFormat.Commander.toString());
        return m != null && m.get(cmd.getName()) != null;
    }

    /** Por que este mazo generado no vale, o {@code null} si vale. */
    private static String problem(final Deck deck, final ColorSet color) {
        if (deck == null || deck.get(DeckSection.Commander) == null
                || deck.get(DeckSection.Commander).isEmpty()) {
            return "sin comandante";
        }
        final int size = deck.getMain().countAll();
        if (size != 99) {
            return size + " cartas";
        }
        int lands = 0;
        for (final Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (e.getKey().getRules().getType().isLand()) {
                lands += e.getValue();
            }
        }
        if (lands < MIN_LANDS) {
            return lands + " tierras";
        }
        for (final Map.Entry<PaperCard, Integer> e : deck.get(DeckSection.Commander)) {
            if (e.getKey().getRules().getColorIdentity().getColor() != color.getColor()) {
                return "otro color";
            }
        }
        return null;
    }

    private static void sleep(final long ms) {
        try {
            Thread.sleep(ms);
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
