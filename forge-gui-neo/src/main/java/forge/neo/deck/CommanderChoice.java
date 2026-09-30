package forge.neo.deck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import forge.deck.CommanderOptions;
import forge.deck.CommanderPicks;
import forge.deck.Deck;
import forge.deck.DeckFormat;
import forge.game.GameType;
import forge.item.PaperCard;

/**
 * <b>Elegir con que comandante juega un mazo, solo para la proxima partida.</b>
 *
 * <p>Lo trajo Forge el 29-09-2026 (#12052, "Pick the commander in the
 * Commander lobby") y nos lo aviso un jugador en Discord. Forge hace las
 * cuentas ({@link CommanderOptions}: quien puede llevar el mazo, con que
 * pareja, y la COPIA del mazo con otros comandantes); aqui solo se decide
 * cuando se ofrece y se recuerda lo elegido. Las pantallas (escritorio y
 * Android) pintan encima. <b>Pura</b>: sin JavaFX y sin nada que Android no
 * tenga, porque la usan los dos por el jar.
 *
 * <p>El mazo guardado <b>no se toca nunca</b>: {@link #apply} devuelve una
 * copia, como el lobby de Forge.
 */
public final class CommanderChoice {

    private CommanderChoice() {
    }

    /** Una opcion para ensenyar: quien lleva el mazo y si es la de siempre o una sugerida. */
    public static final class Option {
        public final List<PaperCard> commanders;
        public final CommanderOptions.Kind kind;
        final CommanderOptions.Option raw;

        Option(final CommanderOptions.Option raw) {
            this.raw = raw;
            this.commanders = raw.getCommanders();
            this.kind = raw.getKind();
        }

        public boolean isDefault() {
            return kind == CommanderOptions.Kind.DEFAULT;
        }

        public boolean isSuggested() {
            return kind == CommanderOptions.Kind.SUGGESTED;
        }
    }

    /**
     * Si en este formato se puede elegir comandante: los que llevan zona de
     * mando con un comandante (Commander, Brawl, Tiny Leaders y sus variantes).
     * <b>Oathbreaker no</b>: su zona de mando lleva tambien el hechizo
     * insignia, y cambiar "los comandantes" se lo llevaria por delante. Forge
     * lo limita a Commander; Brawl y Tiny Leaders son el mismo concepto y el
     * motor ya sabe su legalidad.
     */
    public static boolean applies(final GameType type) {
        return type != null && type != GameType.Oathbreaker
                && type.getDeckFormat() != null && type.getDeckFormat().hasCommander();
    }

    /** Quien puede llevar el mazo: el de siempre primero, luego los sugeridos y el resto. */
    public static List<Option> options(final Deck deck, final DeckFormat format) {
        final List<Option> out = new ArrayList<>();
        if (deck == null || format == null || deck.getCommanders().isEmpty()) {
            return out;
        }
        for (final CommanderOptions.Option o : CommanderOptions.getOptions(deck, format)) {
            out.add(new Option(o));
        }
        return out;
    }

    /** Si hay algo que elegir: otra opcion, o una pareja para un comandante que va solo. */
    public static boolean hasChoices(final Deck deck, final List<Option> options, final DeckFormat format) {
        if (deck == null || options == null || options.isEmpty()) {
            return false;
        }
        return CommanderPicks.hasChoices(deck, raw(options), format);
    }

    /** Con quien puede ir en pareja ese comandante (vacio si no admite pareja). */
    public static List<PaperCard> partners(final Deck deck, final PaperCard commander, final DeckFormat format) {
        return CommanderOptions.getPartnerOptions(deck, commander, format);
    }

    /** Si puede ir sin pareja (si no, "Sin pareja" no se ofrece). */
    public static boolean allowsNoPartner(final Deck deck, final Option option, final DeckFormat format) {
        return CommanderPicks.allowsNoPartner(deck, option.raw, format);
    }

    /** El mazo con esos comandantes; {@code null} o los de siempre devuelven el mismo mazo. */
    public static Deck apply(final Deck deck, final List<PaperCard> pick) {
        if (deck == null || pick == null || pick.isEmpty() || same(pick, deck.getCommanders())) {
            return deck;
        }
        return CommanderOptions.withCommanders(deck, pick);
    }

    public static boolean same(final List<PaperCard> a, final List<PaperCard> b) {
        return a != null && b != null && CommanderPicks.isSame(a, b);
    }

    // ------------------------------------------------------------------
    // Lo elegido se recuerda mientras dura la sesion, por mazo, como el lobby
    // de Forge (que lo conserva si se vuelve a elegir el mismo mazo). No se
    // guarda en disco: es "solo para la proxima partida", y un mazo editado
    // entre medias podria no admitirlo ya (por eso se valida al leerlo).

    private static final Map<String, List<PaperCard>> REMEMBERED = new HashMap<>();

    private static String key(final Deck deck, final DeckFormat format) {
        return format + "|" + deck.getName();
    }

    /** Lo elegido para ese mazo, o {@code null} si juega con el de siempre (o ya no vale). */
    public static synchronized List<PaperCard> remembered(final Deck deck, final DeckFormat format) {
        if (deck == null || format == null) {
            return null;
        }
        final List<PaperCard> pick = REMEMBERED.get(key(deck, format));
        if (pick == null) {
            return null;
        }
        if (same(pick, deck.getCommanders()) || !CommanderPicks.isValidPick(deck, pick, format)) {
            REMEMBERED.remove(key(deck, format));
            return null;
        }
        return pick;
    }

    /** Apunta la eleccion; los de siempre (o {@code null}) la borran. */
    public static synchronized void remember(final Deck deck, final DeckFormat format, final List<PaperCard> pick) {
        if (deck == null || format == null) {
            return;
        }
        if (pick == null || pick.isEmpty() || same(pick, deck.getCommanders())) {
            REMEMBERED.remove(key(deck, format));
        } else {
            REMEMBERED.put(key(deck, format), Collections.unmodifiableList(new ArrayList<>(pick)));
        }
    }

    private static List<CommanderOptions.Option> raw(final List<Option> options) {
        final List<CommanderOptions.Option> out = new ArrayList<>();
        for (final Option o : options) {
            out.add(o.raw);
        }
        return out;
    }
}
