package forge.neo.deck;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import forge.deck.Deck;
import forge.deck.DeckImportController;
import forge.deck.DeckRecognizer;
import forge.deck.DeckRecognizer.TokenType;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.model.FModel;

/**
 * Importa una decklist pegada (Moxfield, Archidekt, Arena...) y la guarda.
 *
 * <p>Todo el trabajo duro lo hace Forge: {@link DeckRecognizer} reconoce cada
 * linea, resuelve el nombre a una impresion concreta y avisa de lo que no
 * entiende. Nosotros solo le damos el texto y guardamos el resultado.
 *
 * <p>Formato esperado (el de Moxfield): una carta por linea con su cantidad, y
 * el comandante al final separado por una linea en blanco.
 *
 * <p>Esto es la pieza reutilizable de la fase 6: el deck builder llamara a lo
 * mismo desde un cuadro de texto en vez de desde un fichero.
 */
public final class DeckImporter {

    private DeckImporter() {
    }

    /** Resultado de una importacion, con lo que fue mal si fue mal. */
    public static final class Result {
        public final Deck deck;
        public final int accepted;
        public final int unknown;
        public final List<String> problems;

        Result(final Deck deck, final int accepted, final int unknown, final List<String> problems) {
            this.deck = deck;
            this.accepted = accepted;
            this.unknown = unknown;
            this.problems = problems;
        }
    }

    /**
     * Convierte texto en un mazo de Commander.
     *
     * @param text texto pegado de la decklist
     * @param name nombre con el que guardar el mazo
     */
    public static Result importCommander(final String text, final String name) {
        final DeckImportController controller = new DeckImportController(
                new HeadlessWidgets.CheckBox(false),
                new HeadlessWidgets.ComboBox<String>(),
                new HeadlessWidgets.ComboBox<Integer>(),
                false);

        controller.setGameFormat(GameType.Commander);

        final List<DeckRecognizer.Token> tokens = controller.parseInput(withSectionHeaders(text));

        int accepted = 0;
        int unknown = 0;
        final List<String> problems = new java.util.ArrayList<>();
        for (final DeckRecognizer.Token t : tokens) {
            final TokenType type = t.getType();
            if (TokenType.CARD_TOKEN_TYPES.contains(type)) {
                accepted += t.getQuantity();
            } else if (type == TokenType.UNKNOWN_CARD
                    || type == TokenType.UNSUPPORTED_CARD) {
                unknown += Math.max(1, t.getQuantity());
                problems.add(t.getText());
            }
        }

        final Deck deck = controller.accept(name);
        if (deck != null && name != null && !name.isBlank()) {
            // accept() usa el nombre solo para el dialogo de confirmacion; el
            // nombre real sale de un token DECK_NAME en el texto. Como aqui no
            // lo hay, se lo ponemos nosotros.
            deck.setName(name);
        }
        return new Result(deck, accepted, unknown, problems);
    }

    /**
     * Marca las secciones para que el parser sepa quien es el comandante.
     *
     * <p>Moxfield y compania exportan el comandante al final, separado del mazo
     * por una linea en blanco, sin ninguna etiqueta. El parser de Forge no
     * conoce esa convencion: entiende una linea con la palabra "commander" como
     * cambio de seccion. Asi que traducimos el formato antes de parsear.
     *
     * <p><b>Y la otra convencion de Moxfield, que costo un mazo entero.</b> Su
     * exportacion de texto <i>si</i> pone un encabezado — pero pone
     * {@code SIDEBOARD:}, y mete ahi al comandante. Como ese encabezado si lo
     * reconoce Forge, esto se quitaba de en medio y dejaba el texto igual: los
     * comandantes acababan en el banquillo, el mazo se importaba <b>sin
     * comandante</b> y se quedaba el que hubiera puesto antes. Sintoma: se
     * importa un mazo de cuatro colores, el comandante viejo era mono blanco y
     * la mitad de la lista se rechaza "por identidad de color". Nada de eso
     * dice que el problema sea el comandante.
     *
     * <p>Si el texto ya trae encabezados propios <i>y uno de ellos es el del
     * comandante</i>, se deja tal cual.
     */
    static String withSectionHeaders(final String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        final String newline = System.lineSeparator();

        // Si ya hay encabezados explicitos, casi siempre se deja tal cual.
        final String[] lines = text.split("\r?\n");
        int sideAt = -1;
        boolean anySection = false;
        boolean hasCommander = false;
        for (int i = 0; i < lines.length; i++) {
            if (!DeckRecognizer.isDeckSectionName(lines[i].trim())) {
                continue;
            }
            anySection = true;
            final String word = lines[i].toLowerCase(java.util.Locale.ROOT)
                    .replaceAll("[^a-z]", "");
            if (word.contains("commander")) {
                hasCommander = true;
            } else if (word.startsWith("side") || word.equals("sb")) {
                sideAt = i;
            }
        }
        if (anySection) {
            // El banquillo pasa a ser la zona de mando, y solo si es del tamanyo
            // de una: una carta, o dos si son pareja. Un banquillo de verdad
            // (sellado, draft) es mucho mas grande y se queda donde esta.
            if (!hasCommander && sideAt >= 0 && cardLinesAfter(lines, sideAt) <= 2
                    && cardLinesAfter(lines, sideAt) > 0) {
                lines[sideAt] = "Commander";
                return String.join(newline, lines);
            }
            return text;
        }

        // Partir en bloques separados por lineas en blanco.
        final String[] blocks = text.trim().split("(?m)^[ \t]*\r?$");
        final List<String> nonEmpty = new java.util.ArrayList<>();
        for (final String b : blocks) {
            if (!b.isBlank()) {
                nonEmpty.add(b.strip());
            }
        }
        if (nonEmpty.size() < 2) {
            return text;
        }

        // El ultimo bloque es el comandante (uno, o dos si hay pareja).
        final String commander = nonEmpty.remove(nonEmpty.size() - 1);
        if (commander.split("\r?\n").length > 2) {
            return text; // demasiado grande para ser el comandante
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("Main").append(newline);
        for (final String b : nonEmpty) {
            sb.append(b).append(newline);
        }
        sb.append("Commander").append(newline).append(commander).append(newline);
        return sb.toString();
    }

    /** Cuantas lineas con algo escrito hay tras ese encabezado, hasta el siguiente. */
    private static int cardLinesAfter(final String[] lines, final int header) {
        int n = 0;
        for (int i = header + 1; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (DeckRecognizer.isDeckSectionName(line)) {
                break;
            }
            n++;
        }
        return n;
    }

    /** Guarda el mazo en la carpeta de Commander del usuario. */
    public static File save(final Deck deck) {
        FModel.getDecks().getCommander().add(deck);
        return new File(forge.localinstance.properties.ForgeConstants.DECK_COMMANDER_DIR,
                deck.getName() + ".dck");
    }

    public static String readFile(final File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    /** Cuenta las cartas de una seccion, o 0 si no existe. */
    public static int sectionSize(final Deck deck, final DeckSection section) {
        return deck.has(section) ? deck.get(section).countAll() : 0;
    }
}
