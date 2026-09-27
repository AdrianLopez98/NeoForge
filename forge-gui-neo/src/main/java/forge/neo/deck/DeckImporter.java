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
        // Las secciones, SIEMPRE explicitas. Sin ellas la lista queda vacia, que
        // para Forge es "todas permitidas"... hasta Card-Forge 3ca40a1955
        // (#10517, 12-09-2026): parseInput ahora ANYADE Commander a esa lista
        // cuando el texto trae una linea "Commander" — y withSectionHeaders la
        // escribe siempre. La lista pasaba a ser SOLO Commander, el resto de
        // cartas salia como "no soportada" y la importacion se rompia entera
        // (medido: Sol Ring e Island fuera, Counterspell de comandante). Con
        // Commander ya dentro, parseInput no anyade nada.
        controller.setAllowedSections(java.util.List.of(
                forge.deck.DeckSection.Main,
                forge.deck.DeckSection.Sideboard,
                forge.deck.DeckSection.Commander));

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
        if (deck != null) {
            companionsBackToSideboard(deck, text);
        }
        return new Result(deck, accepted, unknown, problems);
    }

    /**
     * Lo que venia bajo un encabezado {@code Companion} vuelve al banquillo.
     *
     * <p>El parser de Forge, en Commander y sin zona de mando en la lista, sube
     * al comandante una legendaria del banquillo — y Lurrus o Kaheera lo son.
     * Con el encabezado de Arena no hay duda de lo que querias.
     */
    private static void companionsBackToSideboard(final Deck deck, final String text) {
        if (!deck.has(DeckSection.Commander) || text == null) {
            return;
        }
        final java.util.Set<String> named = new java.util.HashSet<>();
        boolean inside = false;
        for (final String raw : text.split("\r?\n")) {
            final String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.toLowerCase(java.util.Locale.ROOT).matches("companion:?")) {
                inside = true;
                continue;
            }
            if (DeckRecognizer.isDeckSectionName(line)) {
                inside = false;
                continue;
            }
            if (inside) {
                named.add(cardName(line).toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (named.isEmpty()) {
            return;
        }
        final forge.deck.CardPool zone = deck.get(DeckSection.Commander);
        for (final forge.item.PaperCard c : new java.util.ArrayList<>(zone.toFlatList())) {
            if (DeckEditor.isCompanionCard(c)
                    && named.contains(c.getName().toLowerCase(java.util.Locale.ROOT))) {
                zone.remove(c, 1);
                deck.getOrCreate(DeckSection.Sideboard).add(c, 1);
            }
        }
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
     *
     * <p><b>Y el companero</b> (27-09-2026, itch.io). Arena lo exporta bajo su
     * propio encabezado, {@code Companion}, que Forge no conoce: lo leia como
     * una carta que no existe. Se traduce a {@code Sideboard}, que es donde lo
     * busca el motor, y ese banquillo nunca se convierte en comandante. Y si el
     * banquillo de Moxfield trae comandante <i>y</i> companero, el companero se
     * queda en el banquillo: los dos a la zona de mando hacia que el segundo
     * sustituyera al primero.
     */
    static String withSectionHeaders(final String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        final String newline = System.lineSeparator();

        // Si ya hay encabezados explicitos, casi siempre se deja tal cual.
        final String[] lines = text.split("\r?\n");
        final java.util.Set<Integer> companionHeaders = new java.util.HashSet<>();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().toLowerCase(java.util.Locale.ROOT).matches("companion:?")) {
                lines[i] = "Sideboard";
                companionHeaders.add(i);
            }
        }
        int sideAt = -1;
        boolean anySection = false;
        boolean hasCommander = false;
        for (int i = 0; i < lines.length; i++) {
            if (!DeckRecognizer.isDeckSectionName(lines[i].trim())) {
                continue;
            }
            anySection = true;
            if (companionHeaders.contains(i)) {
                continue; // el banquillo del companero no es la zona de mando
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
                keepCompanionInSideboard(lines, sideAt, newline);
                return String.join(newline, lines);
            }
            return companionHeaders.isEmpty() ? text : String.join(newline, lines);
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

    /**
     * Si el banquillo de dos cartas que va a ser zona de mando trae un
     * comandante y un companero, el companero vuelve al banquillo.
     *
     * <p>Con una sola carta no se toca: un Lurrus solo en el banquillo de
     * Moxfield es, en un mazo de Commander, casi siempre el comandante. Si el
     * mazo no tiene zona de mando, el editor lo pone de companero al importar.
     */
    private static void keepCompanionInSideboard(final String[] lines, final int header,
                                                 final String newline) {
        final List<Integer> cards = new java.util.ArrayList<>();
        for (int i = header + 1; i < lines.length; i++) {
            final String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            if (DeckRecognizer.isDeckSectionName(line)) {
                break;
            }
            cards.add(i);
        }
        if (cards.size() != 2) {
            return;
        }
        final boolean first = isCompanionLine(lines[cards.get(0)]);
        final boolean second = isCompanionLine(lines[cards.get(1)]);
        if (first == second) {
            return;
        }
        final int companion = first ? cards.get(0) : cards.get(1);
        final int commander = first ? cards.get(1) : cards.get(0);
        final String companionLine = lines[companion];
        // Comandante primero, luego el banquillo con el companero.
        lines[cards.get(0)] = lines[commander];
        lines[cards.get(1)] = "Sideboard" + newline + companionLine;
    }

    /**
     * Las cartas de una lista importada que hay que poner de companero.
     *
     * <p>Las del banquillo con la palabra clave, y ademas — si el mazo de
     * destino no tiene zona de mando — las que el importador tomo por
     * comandante: un banquillo de una carta se lee como comandante (Moxfield),
     * y en Estandar o en la Aventura eso solo puede ser un companero. Salia
     * "no se pudo hacer comandante" (itch.io, 27-09-2026).
     */
    public static List<forge.item.PaperCard> companionsOf(final Deck deck,
                                                          final boolean hasCommandZone) {
        final List<forge.item.PaperCard> out = new java.util.ArrayList<>();
        if (deck == null) {
            return out;
        }
        if (deck.has(DeckSection.Sideboard)) {
            for (final java.util.Map.Entry<forge.item.PaperCard, Integer> e
                    : deck.get(DeckSection.Sideboard)) {
                if (DeckEditor.isCompanionCard(e.getKey())) {
                    out.add(e.getKey());
                }
            }
        }
        if (!hasCommandZone) {
            for (final forge.item.PaperCard c : deck.getCommanders()) {
                if (DeckEditor.isCompanionCard(c)) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** Si la linea ("1 Lurrus of the Dream-Den (IKO) 226") es una carta con Companion. */
    static boolean isCompanionLine(final String line) {
        final String name = cardName(line);
        if (name.isEmpty()) {
            return false;
        }
        final forge.item.PaperCard card =
                forge.StaticData.instance().getCommonCards().getCard(name);
        return DeckEditor.isCompanionCard(card);
    }

    /** El nombre de una linea de lista: sin cantidad, sin "(SET) 123" y sin "*F*". */
    private static String cardName(final String line) {
        return line.trim()
                .replaceFirst("^\\d+\\s*[xX]?\\s+", "")
                .replaceFirst("\\s+\\(.*$", "")
                .replaceFirst("\\s+\\*.*$", "")
                .trim();
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
