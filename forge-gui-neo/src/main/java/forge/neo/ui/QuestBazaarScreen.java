package forge.neo.ui;

import java.util.List;

import forge.gamemodes.quest.bazaar.IQuestBazaarItem;
import forge.gamemodes.quest.bazaar.QuestPetController;
import forge.neo.NeoText;
import forge.neo.quest.NeoQuest;
import forge.neo.quest.NeoQuestBazaar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * El bazar: las mejoras permanentes y las mascotas.
 *
 * <p>Es la otra forma de gastar creditos, y la que no da cartas. Lo que compras
 * aqui <b>no se pierde</b> y cambia como juegas: mas vidas de partida, los
 * desafios llegando antes, mejor precio al vender, la primera cartada del
 * mulligan gratis, y una criatura que empieza contigo en la mesa.
 *
 * <p>Se presenta por <b>puestos</b>, que es como lo tiene escrito Forge, y con
 * su parrafo de ambientacion: son seis frases bien escritas que ya estaban en
 * {@code index.xml} y que dan al modo la unica textura que tiene.
 *
 * <p>Como en el resto de la tienda: <b>elegir no compra</b>. Cada objeto lleva
 * su precio y su boton, y el boton dice lo que hace.
 *
 * <p>Un puesto sin nada a la venta <b>se ensenya igual, vacio y con su motivo</b>.
 * Los objetos desaparecen del mostrador segun los compras
 * ({@code isAvailableForPurchase}), asi que un puesto que se queda en blanco es
 * un puesto agotado — y eso es una recompensa, no un error.
 */
public class QuestBazaarScreen extends StackPane {

    private static final javafx.css.PseudoClass SELECTED =
            javafx.css.PseudoClass.getPseudoClass("selected");

    /** Que puede hacer el jugador aqui. */
    public interface Actions {
        void back();

        /** Algo ha cambiado: creditos, vidas, mascotas. Repintar el cuartel. */
        void changed();
    }

    private final Actions actions;
    private final Label credits = new Label();
    private final VBox content = new VBox(16);

    public QuestBazaarScreen(final Actions actions) {
        this.actions = actions;
        getStyleClass().addAll("table-root", "quest");

        final BorderPane frame = new BorderPane();
        frame.setTop(header());
        frame.setCenter(body());
        getChildren().add(frame);

        reload();
    }

    private Region header() {
        final Label title = new Label(NeoText.get("bazaar.title"));
        title.getStyleClass().add("home-title");
        final Label sub = new Label(NeoText.get("bazaar.subtitle"));
        sub.getStyleClass().add("home-subtitle");

        credits.getStyleClass().addAll("stat-value", "stat-credits");
        final Label cap = new Label(NeoText.get("quest.credits"));
        cap.getStyleClass().add("caption");
        final VBox money = new VBox(-2, credits, cap);
        money.setAlignment(Pos.CENTER);
        money.getStyleClass().add("stat-tile");
        money.setPadding(new Insets(8, 18, 8, 18));

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setMinWidth(Region.USE_PREF_SIZE);
        back.setOnAction(e -> actions.back());

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(14, new VBox(2, title, sub), gap, money, back);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(18, 28, 8, 30));
        return row;
    }

    private Region body() {
        content.setPadding(new Insets(8, 30, 22, 30));
        final ScrollPane sp = new ScrollPane(content);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    /**
     * Se repinta ENTERO despues de cada compra.
     *
     * <p>No es pereza: comprar cambia el mostrador de verdad — la mejora que
     * acabas de comprar desaparece, la mascota pasa a ofrecer el nivel
     * siguiente y aparece en los huecos — asi que refrescar solo una fila
     * dejaria la pantalla mintiendo.
     */
    private void reload() {
        credits.setText(String.valueOf(NeoQuest.credits()));
        content.getChildren().clear();

        content.getChildren().add(pets());

        for (final NeoQuestBazaar.Stall stall : NeoQuestBazaar.stalls()) {
            content.getChildren().add(stallBox(stall));
        }

        // El viaje va EL ULTIMO, y plegado.
        //
        // Estuvo arriba del todo y estaba mal: limitar la aventura a un bloque
        // es una decision de veterano, y lo primero que se ve al entrar al
        // bazar tiene que ser lo que se viene a hacer aqui, que es comprar. De
        // fabrica se juega con todo.
        content.getChildren().add(worlds());
    }

    private Region stallBox(final NeoQuestBazaar.Stall stall) {
        final Label name = new Label(stall.getTitle());
        name.getStyleClass().add("quest-deck-name");

        final Label fluff = new Label(stall.getFluff());
        fluff.getStyleClass().add("home-subtitle");
        fluff.setWrapText(true);
        fluff.setMaxWidth(880);
        fluff.setMinHeight(Region.USE_PREF_SIZE);

        final VBox box = new VBox(8, name, fluff);
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(14, 18, 14, 18));

        final List<IQuestBazaarItem> items = stall.getItems();
        if (items.isEmpty()) {
            final Label sold = new Label(NeoText.get("bazaar.soldOut"));
            sold.getStyleClass().add("caption");
            box.getChildren().add(sold);
            return box;
        }
        for (final IQuestBazaarItem item : items) {
            box.getChildren().add(itemRow(item));
        }
        return box;
    }

    private Region itemRow(final IQuestBazaarItem item) {
        final int price = NeoQuestBazaar.priceOf(item);
        final boolean afford = NeoQuestBazaar.canAfford(item);

        final Label name = new Label(item.getPurchaseName());
        name.getStyleClass().add("duel-name");

        // La descripcion la escribe el propio objeto y ya dice lo que hace y a
        // que nivel lo tienes ("Level 2 -> 3"). No hay que inventarse nada.
        final Label what = new Label(NeoQuestBazaar.descriptionOf(item));
        what.getStyleClass().add("home-subtitle");
        what.setWrapText(true);
        what.setMaxWidth(620);
        what.setMinHeight(Region.USE_PREF_SIZE);

        final Label cost = new Label(NeoText.get("shop.credits.short", price));
        cost.getStyleClass().add(afford ? "set-price-ok" : "set-price-no");

        final Button buy = new Button(NeoText.get("bazaar.buy"));
        buy.getStyleClass().add("btn-primary");
        buy.setMinWidth(Region.USE_PREF_SIZE);
        buy.setDisable(!afford);
        buy.setOnAction(e -> {
            if (NeoQuestBazaar.buy(item)) {
                reload();
                actions.changed();
            }
        });

        final Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        final HBox row = new HBox(14, new VBox(2, name, what), gap, cost, buy);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 4, 8, 4));
        return row;
    }

    /**
     * A donde puedes viajar.
     *
     * <p>Un mundo no es un decorado: decide <b>con que cartas existe el
     * juego</b>. En Zendikar la tienda solo vende sobres de Zendikar, los
     * premios salen de ahi y los rivales son los de esa carpeta. Viajar es la
     * forma de cambiar de aventura sin empezar otra.
     *
     * <p>Va aqui, con el bazar, porque es lo mismo: cosas que cambian la
     * aventura entera y que no son cartas.
     */
    private Region worlds() {
        final Label cap = new Label(NeoText.get("bazaar.worlds"));
        cap.getStyleClass().add("caption");

        final forge.gamemodes.quest.QuestWorld now = forge.neo.quest.NeoQuestWorlds.current();
        final boolean everything = now == null
                || forge.gamemodes.quest.QuestWorld.MAINWORLDNAME.equals(now.getName());
        final Label where = new Label(everything
                ? NeoText.get("bazaar.worldAll") : NeoText.get("bazaar.worldNow", now.getName()));
        where.getStyleClass().add("quest-deck-name");

        final Label warn = new Label(NeoText.get("bazaar.worldWarn"));
        warn.getStyleClass().add("home-subtitle");
        warn.setWrapText(true);
        warn.setMaxWidth(880);
        warn.setMinHeight(Region.USE_PREF_SIZE);

        final Button toggle = new Button(NeoText.get("questNew.worldOpen"));
        toggle.getStyleClass().add("btn-secondary");
        toggle.setMinWidth(Region.USE_PREF_SIZE);

        worldSearch.setPromptText(NeoText.get("questNew.worldSearch"));
        worldSearch.getStyleClass().add("text-input");
        worldSearch.setPrefColumnCount(18);
        worldSearch.textProperty().addListener((o, was, is) -> paintWorlds());

        worldGrid.setAlignment(Pos.TOP_LEFT);
        final ScrollPane scroll = new ScrollPane(worldGrid);
        scroll.getStyleClass().add("dialog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(170);

        paintWorlds();

        final VBox picker = new VBox(8, warn, worldSearch, scroll);
        picker.setVisible(false);
        picker.setManaged(false);
        toggle.setOnAction(e -> {
            final boolean open = !picker.isVisible();
            picker.setVisible(open);
            picker.setManaged(open);
            toggle.setText(NeoText.get(open ? "questNew.worldClose" : "questNew.worldOpen"));
        });

        final HBox head = new HBox(12, where, toggle);
        head.setAlignment(Pos.CENTER_LEFT);

        final VBox box = new VBox(8, cap, head, picker);
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(14, 18, 14, 18));
        return box;
    }

    private final javafx.scene.control.TextField worldSearch = new javafx.scene.control.TextField();
    private final javafx.scene.layout.FlowPane worldGrid = new javafx.scene.layout.FlowPane(8, 8);

    private void paintWorlds() {
        worldGrid.getChildren().clear();
        final forge.gamemodes.quest.QuestWorld now = forge.neo.quest.NeoQuestWorlds.current();
        final String q = worldSearch.getText() == null ? ""
                : worldSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        for (final forge.gamemodes.quest.QuestWorld w
                : forge.neo.quest.NeoQuestWorlds.all()) {
            final String sets = forge.neo.quest.NeoQuestWorlds.describe(w);
            final boolean here = now != null && now.getName().equals(w.getName());
            // El mundo en el que estas nunca se filtra: si el buscador lo
            // escondiera, dejaria de verse donde estas.
            if (!q.isEmpty() && !here
                    && !w.getName().toLowerCase(java.util.Locale.ROOT).contains(q)
                    && !sets.toLowerCase(java.util.Locale.ROOT).contains(q)) {
                continue;
            }
            worldGrid.getChildren().add(worldTile(w, sets, here));
        }
    }

    private Region worldTile(final forge.gamemodes.quest.QuestWorld w,
                             final String sets, final boolean here) {
        final Label name = new Label(w.getName());
        name.getStyleClass().add("duel-name");
        name.setWrapText(true);
        name.setMaxWidth(210);
        name.setMinHeight(Region.USE_PREF_SIZE);

        final Label what = new Label(here ? NeoText.get("bazaar.worldHere") : sets);
        what.getStyleClass().add("caption");
        what.setWrapText(true);
        what.setMaxWidth(210);
        what.setMinHeight(Region.USE_PREF_SIZE);

        final VBox tile = new VBox(2, name, what);
        tile.getStyleClass().add("set-tile");
        tile.setPadding(new Insets(8, 10, 8, 10));
        tile.setPrefWidth(230);
        tile.pseudoClassStateChanged(SELECTED, here);
        tile.setOnMouseClicked(e -> {
            if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                    && forge.neo.quest.NeoQuestWorlds.travelTo(w)) {
                reload();
                actions.changed();
            }
        });
        return tile;
    }

    /**
     * Los huecos de mascota.
     *
     * <p>Comprarla no basta: hay que <b>ponersela</b>. Es la mitad que se
     * olvida — {@code selectPet} es una llamada aparte, y sin ella la mascota
     * esta comprada, pagada y no sale a la mesa. Por eso los huecos van arriba
     * del todo, antes que los puestos.
     */
    private Region pets() {
        final Label cap = new Label(NeoText.get("bazaar.pets"));
        cap.getStyleClass().add("caption");

        final VBox box = new VBox(8, cap);

        boolean any = false;
        for (int slot = 0; slot < NeoQuestBazaar.petSlots(); slot++) {
            final List<QuestPetController> owned = NeoQuestBazaar.ownedPets(slot);
            if (owned.isEmpty()) {
                continue;
            }
            any = true;
            box.getChildren().add(petRow(slot, owned));
        }
        if (!any) {
            final Label none = new Label(NeoText.get("bazaar.noPets"));
            none.getStyleClass().add("home-subtitle");
            none.setWrapText(true);
            box.getChildren().add(none);
        }
        final VBox tile = new VBox(box);
        tile.getStyleClass().add("stat-tile");
        tile.setPadding(new Insets(14, 18, 14, 18));
        return tile;
    }

    private Region petRow(final int slot, final List<QuestPetController> owned) {
        final Label label = new Label(NeoText.get("bazaar.petSlot", slot + 1));
        label.getStyleClass().add("caption");

        final HBox choices = new HBox(6);
        choices.setAlignment(Pos.CENTER_LEFT);

        final String chosen = NeoQuestBazaar.selectedPet(slot);

        // "Ninguna" es una opcion de verdad y tiene que estar: una mascota en
        // la mesa es informacion que le das al rival, y hay quien prefiere no
        // llevarla.
        choices.getChildren().add(petPick(slot, null, NeoText.get("bazaar.noPet"), chosen == null));
        for (final QuestPetController pet : owned) {
            choices.getChildren().add(petPick(slot, pet.getName(), pet.getName(),
                    pet.getName().equals(chosen)));
        }

        final VBox row = new VBox(4, label, choices);
        row.setPadding(new Insets(4, 0, 4, 0));
        return row;
    }

    private Button petPick(final int slot, final String value, final String label,
                           final boolean selected) {
        final Button b = new Button(label);
        b.getStyleClass().add("segment");
        b.setMinWidth(Region.USE_PREF_SIZE);
        b.pseudoClassStateChanged(SELECTED, selected);
        b.setOnAction(e -> {
            NeoQuestBazaar.selectPet(slot, value);
            reload();
            actions.changed();
        });
        return b;
    }
}
