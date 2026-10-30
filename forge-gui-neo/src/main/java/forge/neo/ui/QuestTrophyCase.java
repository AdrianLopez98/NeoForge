package forge.neo.ui;

import java.util.List;

import forge.neo.NeoText;
import forge.neo.quest.NeoQuestBazaar;
import forge.neo.quest.NeoQuestTrophies;
import forge.neo.quest.NeoQuestTrophies.Cup;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * <b>La vitrina de la Quest</b> (Discord, 25-10-2026: <i>"a list of challenges
 * won and other trophies (championship, tournaments)"</i>). Un dialogo que se
 * abre desde la cabecera del cuartel: los desafios superados, los podios de
 * los Campeonatos, las Copas y el Coliseo, la mejor racha y las mejoras del
 * bazar. Los datos, en {@link NeoQuestTrophies}; los dibujos son los trofeos
 * de la vitrina de logros de Forge.
 *
 * <p>Los podios que aun no tienes salen apagados en vez de no salir: una
 * vitrina con huecos dice que hay algo que ganar.
 */
final class QuestTrophyCase extends VBox {

    QuestTrophyCase(final Runnable close, final double maxWidth, final double maxHeight) {
        super(12);
        getStyleClass().add("dialog");
        setPadding(new Insets(18));
        setId("quest-trophy-case");

        final Label title = new Label(NeoText.get("trophies.title"));
        title.getStyleClass().add("dialog-title");
        final Label sub = new Label(NeoText.get("trophies.sub", NeoQuestTrophies.bestStreak(),
                NeoQuestTrophies.wins(), NeoQuestTrophies.losses()));
        sub.getStyleClass().add("home-subtitle");
        sub.setWrapText(true);

        final VBox shelves = new VBox(14);
        shelves.getChildren().add(challenges());
        shelves.getChildren().add(shelf(NeoText.get("trophies.championships"), null,
                podium(NeoQuestTrophies.championshipPlaces())));
        // El torneo y el Coliseo, con su podio como los campeonatos (Discord,
        // 30-10-2026: el 3.º del Coliseo no contaba). Del 5.º para abajo, nada.
        shelves.getChildren().add(shelf(NeoText.get("trophies.cupShelf"), NeoText.get("trophies.sincePlaces"),
                podium(NeoQuestTrophies.cupPlaces())));
        shelves.getChildren().add(shelf(NeoText.get("trophies.colosseumShelf"), NeoText.get("trophies.sincePlaces"),
                podium(NeoQuestTrophies.colosseumPlaces())));
        final List<NeoQuestBazaar.OwnedUpgrade> owned = NeoQuestBazaar.ownedUpgrades();
        if (!owned.isEmpty()) {
            final List<Region> badges = new java.util.ArrayList<>();
            for (final NeoQuestBazaar.OwnedUpgrade u : owned) {
                badges.add(QuestBazaarScreen.upgradeBadge(u));
            }
            shelves.getChildren().add(shelf(NeoText.get("bazaar.upgrades"), null, badges));
        }

        final ScrollPane sp = new ScrollPane(shelves);
        sp.getStyleClass().add("dialog-scroll");
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setPrefViewportHeight(Math.max(260, maxHeight * 0.7));

        final Button back = new Button(NeoText.get("common.back"));
        back.getStyleClass().add("btn-secondary");
        back.setOnAction(e -> close.run());
        final HBox bottom = new HBox(back);
        bottom.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(title, sub, sp, bottom);
        setMaxWidth(maxWidth);
        setMaxHeight(Region.USE_PREF_SIZE);
    }

    /** Un desafio, un trofeo con su nombre debajo y lo que pedia al pasar el raton. */
    private static Region challenges() {
        final List<NeoQuestTrophies.Challenge> beaten = NeoQuestTrophies.challenges();
        if (beaten.isEmpty()) {
            final Label none = new Label(NeoText.get("trophies.noChallenges"));
            none.getStyleClass().add("home-subtitle");
            none.setWrapText(true);
            return shelf(NeoText.get("trophies.challenges", 0), null, List.of(none));
        }
        final List<Region> items = new java.util.ArrayList<>();
        for (final NeoQuestTrophies.Challenge c : beaten) {
            final Region t = trophy(Cup.CHALLENGE, c.title(), 1, false);
            if (!c.description().isBlank()) {
                final Tooltip tip = new Tooltip(c.description());
                tip.setWrapText(true);
                tip.setMaxWidth(UiScale.px(420));
                Tooltip.install(t, tip);
            }
            items.add(t);
        }
        return shelf(NeoText.get("trophies.challenges", beaten.size()), null, items);
    }

    private static List<Region> podium(final int[] places) {
        final Cup[] cups = {Cup.GOLD, Cup.SILVER, Cup.BRONZE, Cup.FOURTH};
        final List<Region> out = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            out.add(trophy(cups[i], NeoText.get("trophies.place" + (i + 1)), places[i], true));
        }
        return out;
    }

    /** Una balda: su titulo, una nota opcional y lo que hay encima. */
    private static Region shelf(final String caption, final String note, final List<? extends Region> items) {
        final Label cap = new Label(caption);
        cap.getStyleClass().add("caption");
        final FlowPane row = new FlowPane(12, 12);
        row.getChildren().addAll(items);
        final VBox box = new VBox(8, cap, row);
        if (note != null) {
            final Label n = new Label(note);
            n.getStyleClass().add("home-subtitle");
            n.setWrapText(true);
            box.getChildren().add(1, n);
        }
        box.getStyleClass().add("stat-tile");
        box.setPadding(new Insets(14, 18, 14, 18));
        return box;
    }

    /**
     * Un trofeo de la lamina de Forge con su placa. {@code counted}: lleva
     * "×N" y, a cero, sale apagado.
     */
    private static Region trophy(final Cup cup, final String name, final int count, final boolean counted) {
        final VBox item = new VBox(4);
        item.setAlignment(Pos.TOP_CENTER);
        item.setPrefWidth(UiScale.px(118));
        final Image sprite = sprite();
        final int[] r = cup.rect();
        if (sprite != null && r != null) {
            final ImageView v = new ImageView(sprite);
            v.setViewport(new Rectangle2D(r[0], r[1], r[2], r[3]));
            v.setFitHeight(UiScale.px(96));
            v.setPreserveRatio(true);
            v.setSmooth(true);
            item.getChildren().add(v);
        }
        final Label plate = new Label(name);
        plate.getStyleClass().add("trophy-plate");
        plate.setWrapText(true);
        plate.setMaxWidth(UiScale.px(116));
        plate.setTextAlignment(TextAlignment.CENTER);
        plate.setAlignment(Pos.CENTER);
        plate.setMinHeight(Region.USE_PREF_SIZE);
        item.getChildren().add(plate);
        if (counted) {
            final Label n = new Label("×" + count);
            n.getStyleClass().add("stat-value");
            item.getChildren().add(n);
            if (count == 0) {
                item.setOpacity(0.35);
            }
        }
        return item;
    }

    /** La lamina de trofeos de Forge, leida una vez. */
    private static Image spriteCache;

    private static Image sprite() {
        if (spriteCache == null) {
            final java.io.File f = new java.io.File(NeoQuestTrophies.spriteFile());
            if (f.isFile()) {
                spriteCache = new Image(f.toURI().toString());
            }
        }
        return spriteCache;
    }
}
