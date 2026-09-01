package forge.neo.ui;

import forge.neo.NeoText;
import forge.neo.card.CardText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import forge.card.mana.ManaCost;
import forge.game.card.CardView;
import forge.game.spellability.SpellAbilityView;
import forge.neo.card.CardNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Que hacer con esta carta: el menu de habilidades, como el de Arena.
 *
 * <p>Sale cuando una carta tiene mas de una cosa jugable — un equipamiento con
 * una habilidad activada y su "Equipar", una tierra que produce mana y ademas
 * hace otra cosa, un planeswalker con tres lealtades. La lista la da el motor
 * ({@code getAbilityToPlay}); aqui solo se presenta.
 *
 * <p>La carta se ensenya <b>grande</b> y las habilidades como recuadros a su
 * lado. No es capricho: cuando el juego te para para preguntarte algo, lo
 * primero que necesitas es volver a leer la carta, y a tamano de mesa no se
 * puede.
 *
 * <ul>
 *   <li><b>Lo que no se puede jugar se ve apagado</b> y lo dice. El motor ya
 *       sabe si puedes pagarlo ({@code SpellAbilityView.canPlay()}); sin
 *       ensenyarlo, clicas Equipar, no pasa nada y no sabes por que.</li>
 *   <li><b>Se puede cancelar.</b> Clicar una carta por error no puede obligarte
 *       a activar algo.</li>
 * </ul>
 */
public class AbilityMenu extends VBox {

    /**
     * @param onPick recibe el indice elegido, o -1 si se cancela
     */
    public AbilityMenu(final CardView host, final List<SpellAbilityView> abilities,
                       final double cardWidth, final Consumer<Integer> onPick) {
        this(host, labelsOf(host, abilities), playableOf(abilities), cardWidth, onPick);
    }

    /**
     * Version con los textos ya resueltos.
     *
     * <p>Existe para poder ver el menu sin una partida detras: un
     * {@code SpellAbilityView} no se puede construir fuera del motor, asi que
     * sin esta puerta el aspecto de esta pantalla no se podria comprobar con
     * una captura. La usa {@code --mock-abilities}.
     */
    public AbilityMenu(final CardView host, final List<String> labels, final boolean[] playable,
                       final double cardWidth, final Consumer<Integer> onPick) {
        getStyleClass().addAll("dialog", "ability-menu");
        setSpacing(12);
        setPadding(new Insets(22, 26, 20, 26));
        setMaxWidth(Region.USE_PREF_SIZE);
        setMaxHeight(Region.USE_PREF_SIZE);

        final String name = host == null || host.getCurrentState() == null
                ? NeoText.get("ability.thisCard") : CardText.nameOf(host.getCurrentState());
        final Label title = new Label(name);
        title.getStyleClass().add("dialog-title");

        final Label help = new Label(NeoText.get("ability.help"));
        help.getStyleClass().add("home-subtitle");

        final VBox list = new VBox(8);
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            final boolean can = playable == null || i >= playable.length || playable[i];

            final Button b = new Button(can ? labels.get(i)
                    : labels.get(i) + "\n" + NeoText.get("ability.cannot"));
            b.getStyleClass().add("ability-item");
            b.setWrapText(true);
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinHeight(Region.USE_PREF_SIZE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setDisable(!can);
            b.setOnAction(e -> onPick.accept(index));
            list.getChildren().add(b);
        }
        // Los recuadros acompanyan a la carta: ni tan estrechos que el texto se
        // parta en cada palabra, ni tan anchos que se despeguen de ella.
        list.setPrefWidth(Math.max(360, cardWidth * 1.6));

        final Button cancel = new Button(NeoText.get("common.cancel"));
        cancel.getStyleClass().add("btn-secondary");
        cancel.setOnAction(e -> onPick.accept(-1));

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox footer = new HBox(gap, cancel);

        final VBox right = new VBox(10, title, help, list, footer);
        right.setAlignment(Pos.TOP_LEFT);

        if (host != null) {
            final CardNode node = new CardNode(cardWidth);
            node.setRotationEnabled(false);
            node.setCard(host);
            node.setHoverEnabled(false);
            // A este tamano la carta ya lleva su P/T impresa.
            node.setBadgesVisible(false);
            final HBox row = new HBox(26, node, right);
            row.setAlignment(Pos.TOP_LEFT);
            getChildren().add(row);
        } else {
            getChildren().add(right);
        }
    }

    /**
     * Los textos de la lista, distinguiendo las opciones que salen IGUALES.
     *
     * <p>Le pasa esto a un coste alternativo (<i>Deadly Rollick</i>: pagar
     * {3}{B}, o gratis si controlas un comandante): el motor compone el texto
     * de esa segunda opcion anyadiendole entre parentesis por que es distinta
     * — pero <b>solo en ingles</b>. {@code CardTranslation} traduce frase a
     * frase comparando contra el oraculo de la carta, y para eso primero
     * <b>borra todo lo que vaya entre parentesis</b>
     * (`descText.replaceAll("\\(.*\\)", "")` en
     * {@code CardTranslation.translateSingleIngameText}) — asi que jugando en
     * castellano las dos opciones acaban con el MISMO texto traducido y no
     * hay forma de saber cual es cual. Es un fallo del motor (`forge-core`,
     * no se toca).
     *
     * <p>No hay forma de recuperar el coste exacto de la alternativa — la
     * vista no lo publica — pero SI se sabe una cosa segura:
     * {@code Card.getAllPossibleAbilities} anyade siempre la habilidad base
     * PRIMERO y sus costes alternativos justo despues (Card.java). Asi que
     * cuando dos textos salen iguales, el primero es "pagar lo impreso" — y
     * eso SI se puede ensenyar, porque el coste impreso no es texto y no lo
     * toca la traduccion rota.
     */
    private static List<String> labelsOf(final CardView host, final List<SpellAbilityView> abilities) {
        final List<String> raw = new ArrayList<>();
        for (final SpellAbilityView sa : abilities) {
            final String desc = sa.getDescription();
            raw.add(desc == null || desc.isBlank() ? String.valueOf(sa) : desc);
        }

        final List<String> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            String label = raw.get(i);
            final boolean duplicated = countOf(raw, label) > 1;
            if (duplicated) {
                final boolean firstOfItsKind = raw.indexOf(label) == i;
                label += "\n" + (firstOfItsKind
                        ? printedCostNote(host)
                        : NeoText.get("ability.altCost"));
            }
            out.add(label);
        }
        return out;
    }

    private static int countOf(final List<String> list, final String value) {
        int n = 0;
        for (final String s : list) {
            if (s.equals(value)) {
                n++;
            }
        }
        return n;
    }

    /** "(pagando su coste: {3}{B})", o el aviso generico si no hay coste que leer. */
    private static String printedCostNote(final CardView host) {
        if (host != null && host.getCurrentState() != null) {
            final ManaCost cost = host.getCurrentState().getManaCost();
            if (cost != null) {
                return NeoText.get("ability.printedCost", cost.toString());
            }
        }
        return NeoText.get("ability.normalCost");
    }

    private static boolean[] playableOf(final List<SpellAbilityView> abilities) {
        final boolean[] out = new boolean[abilities.size()];
        for (int i = 0; i < abilities.size(); i++) {
            out[i] = abilities.get(i).canPlay();
        }
        return out;
    }
}
