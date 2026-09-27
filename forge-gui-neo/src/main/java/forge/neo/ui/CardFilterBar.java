package forge.neo.ui;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import forge.card.CardRarity;
import forge.card.CardType;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.item.PaperCard;
import forge.neo.NeoText;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Los filtros del constructor de mazos —color, tipo, rareza y coste— para
 * cualquier rejilla de cartas que no sea el constructor.
 *
 * <p>Nace de la pestanya de vender de la tienda (informe de itch.io,
 * 27-09-2026: <i>"filter options like deck building"</i>). Son los mismos
 * botones, con las mismas clases de estilo y las mismas claves de texto, para
 * que quien ya sabe filtrar en el constructor sepa filtrar aqui sin aprender
 * nada. Las reglas tambien son las mismas: el color es <b>identidad</b>, el 7
 * es "7 o mas", y dentro de un grupo se suma (rojo O verde) mientras que entre
 * grupos se exige (rojo Y criatura).
 *
 * <p>El constructor sigue con los suyos: estan entrelazados con el modo
 * comandante y el "solo lo que cabe", y moverlo de sitio no aporta nada a quien
 * juega.
 *
 * <p>Dos piezas: {@link #bar()} (los cinco colores y el boton que despliega el
 * resto) va en la fila del buscador, y {@link #advanced()} debajo, plegada.
 */
public final class CardFilterBar {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    private final Runnable onChange;
    private final Set<Byte> colours = new HashSet<>();
    private final Set<CardRarity> rarities = EnumSet.noneOf(CardRarity.class);
    private final Set<Integer> cmcs = new HashSet<>();
    private final Set<CardType.CoreType> types = EnumSet.noneOf(CardType.CoreType.class);
    private final List<Button> buttons = new ArrayList<>();
    private final Button toggle = new Button();
    private final HBox bar;
    private final VBox advanced;
    /**
     * El color es la IDENTIDAD (lo de siempre) o el color impreso de la carta.
     * En la tienda y el constructor manda la identidad, que es lo que decide
     * si cabe en un mazo de Commander; la enciclopedia deja elegir, porque ahi
     * se pregunta tambien "que cartas SON rojas".
     */
    private boolean byIdentity = true;

    public CardFilterBar(final Runnable onChange) {
        this.onChange = onChange;

        bar = new HBox(6,
                colour("W", MagicColor.WHITE), colour("U", MagicColor.BLUE),
                colour("B", MagicColor.BLACK), colour("R", MagicColor.RED),
                colour("G", MagicColor.GREEN), toggle);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("card-filter");

        toggle.getStyleClass().add("segment");
        toggle.setMinWidth(Region.USE_PREF_SIZE);
        toggle.setOnAction(e -> toggleAdvanced());

        final FlowPane typeRow = new FlowPane(5, 5);
        typeRow.setAlignment(Pos.CENTER_LEFT);
        for (final CardType.CoreType t : new CardType.CoreType[] {
                CardType.CoreType.Creature, CardType.CoreType.Instant,
                CardType.CoreType.Sorcery, CardType.CoreType.Artifact,
                CardType.CoreType.Enchantment, CardType.CoreType.Planeswalker,
                CardType.CoreType.Battle, CardType.CoreType.Land}) {
            typeRow.getChildren().add(type(t));
        }

        final HBox rarityRow = new HBox(4,
                rarity("C", CardRarity.Common), rarity("I", CardRarity.Uncommon),
                rarity("R", CardRarity.Rare), rarity("M", CardRarity.MythicRare));
        rarityRow.setAlignment(Pos.CENTER_LEFT);

        final HBox cmcRow = new HBox(4);
        cmcRow.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i <= 7; i++) {
            cmcRow.getChildren().add(cmc(i));
        }

        final Button clear = new Button(NeoText.get("deck.clearFilters"));
        clear.getStyleClass().add("segment");
        clear.setMinWidth(Region.USE_PREF_SIZE);
        clear.setOnAction(e -> clear());

        final FlowPane fine = new FlowPane(10, 8,
                new HBox(6, caption(NeoText.get("deck.rarity")), rarityRow),
                new HBox(6, caption(NeoText.get("deck.cmc")), cmcRow), clear);
        fine.setAlignment(Pos.CENTER_LEFT);

        advanced = new VBox(10, new VBox(6, caption(NeoText.get("deck.type")), typeRow), fine);
        advanced.getStyleClass().addAll("card-filter", "builder-advanced");
        advanced.setVisible(false);
        advanced.setManaged(false);
        updateToggle();
    }

    /** Los colores y el boton de "Filtros": va en la fila del buscador. */
    public Region bar() {
        return bar;
    }

    /** Tipo, rareza y coste, plegados hasta que se piden. */
    public Region advanced() {
        return advanced;
    }

    /** Si hay algun filtro encendido. */
    public boolean isActive() {
        return !colours.isEmpty() || !rarities.isEmpty() || !cmcs.isEmpty() || !types.isEmpty();
    }

    /** La carta pasa los filtros. Sin filtros encendidos pasan todas. */
    public boolean test(final PaperCard card) {
        if (card == null) {
            return false;
        }
        if (!rarities.isEmpty() && !rarities.contains(card.getRarity())) {
            return false;
        }
        if (!types.isEmpty() && java.util.Collections.disjoint(
                types, card.getRules().getType().getCoreTypes())) {
            return false;
        }
        if (!cmcs.isEmpty()
                && !cmcs.contains(Math.min(card.getRules().getManaCost().getCMC(), 7))) {
            return false;
        }
        if (!colours.isEmpty()) {
            final ColorSet id = byIdentity ? card.getRules().getColorIdentity()
                    : card.getRules().getColor();
            for (final byte c : colours) {
                if (id.hasAnyColor(c)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    /** Filtrar por identidad de color (true, lo de fabrica) o por el color impreso. */
    public void setByIdentity(final boolean identity) {
        if (identity != byIdentity) {
            byIdentity = identity;
            if (!colours.isEmpty()) {
                onChange.run();
            }
        }
    }

    public boolean isByIdentity() {
        return byIdentity;
    }

    /** Lo mismo, como predicado. */
    public Predicate<PaperCard> predicate() {
        return this::test;
    }

    /** Apaga todos los filtros. */
    public void clear() {
        colours.clear();
        rarities.clear();
        cmcs.clear();
        types.clear();
        for (final Button b : buttons) {
            b.pseudoClassStateChanged(SELECTED, false);
        }
        changed();
    }

    // ---------------------------------------------------------------

    private void toggleAdvanced() {
        final boolean show = !advanced.isVisible();
        advanced.setVisible(show);
        advanced.setManaged(show);
        updateToggle();
    }

    private void changed() {
        updateToggle();
        onChange.run();
    }

    private void updateToggle() {
        final int active = colours.size() + rarities.size() + cmcs.size() + types.size();
        toggle.setText(NeoText.get("deck.filters") + (active == 0 ? "" : " · " + active)
                + (advanced != null && advanced.isVisible() ? "  −" : "  +"));
        toggle.pseudoClassStateChanged(SELECTED,
                active > 0 || (advanced != null && advanced.isVisible()));
    }

    private <T> Button flip(final String text, final Set<T> set, final T value,
                            final String... styles) {
        final Button b = new Button(text);
        b.getStyleClass().add("segment");
        b.getStyleClass().addAll(styles);
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.setOnAction(e -> {
            if (!set.remove(value)) {
                set.add(value);
            }
            b.pseudoClassStateChanged(SELECTED, set.contains(value));
            changed();
        });
        buttons.add(b);
        return b;
    }

    private Button colour(final String letter, final byte colour) {
        return flip(letter, colours, colour, "colour-filter",
                "mana-" + letter.toLowerCase(java.util.Locale.ROOT));
    }

    private Button rarity(final String letter, final CardRarity rarity) {
        return flip(letter, rarities, rarity, "rarity-filter",
                "rarity-" + letter.toLowerCase(java.util.Locale.ROOT));
    }

    private Button cmc(final int cmc) {
        return flip(cmc == 7 ? "7+" : String.valueOf(cmc), cmcs, cmc, "cmc-filter");
    }

    private Button type(final CardType.CoreType type) {
        return flip(type.getTranslatedName(), types, type, "type-filter");
    }

    private static Label caption(final String text) {
        final Label l = new Label(text);
        l.getStyleClass().add("caption");
        return l;
    }
}
