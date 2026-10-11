package forge.neo.ui;

import java.util.function.Function;

import forge.neo.NeoText;
import forge.neo.ascent.AscentRun;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * <b>"Tu mazo", "Reliquias" y "El mapa"</b> a mano en las pantallas de un nodo
 * de Ascenso (premio, tienda, descanso, evento), como en el mapa.
 *
 * <p>Pedido en Discord el 29-09-2026: <i>"allow us a way to view our deck
 * during the reward screen ... Knowing what is currently in our deck and
 * comparing to the rewards on offer is important. Slay the Spire has both its
 * deck and map buttons visible during this screen"</i>. Hasta ahora el mazo
 * solo se veia desde el mapa: el premio se elegia de memoria.
 *
 * <p>Se abren <b>encima</b> — las mismas {@link AscentDeckScreen} y
 * {@link AscentRelicsScreen} del mapa, apiladas en esta misma pantalla — y su
 * "Volver" las quita. No se cambia de pantalla: la de premio ya ha sorteado lo
 * que ofrece, y salir de ella y volver lo perderia o lo volveria a sortear.
 *
 * <p><b>El mapa</b> (Discord, 11-10-2026, en un evento: <i>"A way to see the
 * map before making a choice from this screen"</i>): el mismo
 * {@link AscentMapScreen}, para mirar. No se entra en ningun nodo ni se
 * abandona la run desde ahi; su "Tu mazo" y sus reliquias se abren encima,
 * igual que desde el nodo.
 */
final class AscentQuickLook {

    private AscentQuickLook() {
    }

    /**
     * Pone los dos botones arriba a la derecha de {@code host}.
     *
     * @return que llamar cuando cambien las reliquias (una de premio o de
     *         tienda), para que el contador diga la verdad
     */
    static Runnable install(final StackPane host, final AscentRun run, final double cardWidth) {
        final Button deck = new Button(NeoText.get("ascent.map.deck"));
        deck.setId("ascent-quick-deck");
        final Button relics = new Button();
        relics.setId("ascent-quick-relics");
        final Button map = new Button(NeoText.get("ascent.quick.map"));
        map.setId("ascent-quick-map");
        for (final Button b : new Button[] {map, deck, relics}) {
            b.getStyleClass().add("ascent-button");
            b.setMinWidth(Region.USE_PREF_SIZE);
            b.setFocusTraversable(false);
        }
        deck.setOnAction(e -> open(host, back -> new AscentDeckScreen(run, cardWidth, back, false)));
        relics.setOnAction(e -> open(host, back -> new AscentRelicsScreen(run, cardWidth, back)));
        map.setOnAction(e -> open(host, back -> new AscentMapScreen(run, new AscentMapScreen.Actions() {
            @Override
            public void enter(final forge.neo.ascent.AscentNode node) {
                // Solo mirar.
            }

            @Override
            public void deck() {
                open(host, b -> new AscentDeckScreen(run, cardWidth, b, false));
            }

            @Override
            public void relics() {
                open(host, b -> new AscentRelicsScreen(run, cardWidth, b));
            }

            @Override
            public void abandon() {
                // Solo mirar: el boton ni sale.
            }

            @Override
            public void back() {
                back.run();
            }
        }, true)));
        final Runnable refresh = () -> {
            final int n = run.relics().size();
            relics.setText(NeoText.get("ascent.over.relics", n));
            relics.setVisible(n > 0);
            relics.setManaged(n > 0);
        };
        refresh.run();

        final HBox bar = new HBox(8, map, deck, relics);
        bar.setAlignment(Pos.TOP_RIGHT);
        bar.setPickOnBounds(false);
        bar.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(bar, Pos.TOP_RIGHT);
        // Dentro del papel, lejos de su borde roto (Parchment.SAFE_EDGE).
        StackPane.setMargin(bar, new Insets(Parchment.SAFE_EDGE, Parchment.SAFE_EDGE + 6, 0, 0));
        host.getChildren().add(bar);
        // Solo pruebas: -Dneo.ascent.quickLook=deck|relics lo pulsa solo, para
        // capturar la consulta abierta encima de la pantalla (la guía de pruebas).
        final String test = System.getProperty("neo.ascent.quickLook");
        if ("deck".equals(test)) {
            javafx.application.Platform.runLater(deck::fire);
        } else if ("relics".equals(test)) {
            javafx.application.Platform.runLater(relics::fire);
        } else if ("map".equals(test)) {
            javafx.application.Platform.runLater(map::fire);
        }
        return refresh;
    }

    /** Una pantalla de consulta encima de todo, que su "Volver" quita. */
    private static void open(final StackPane host, final Function<Runnable, StackPane> make) {
        final StackPane[] layer = new StackPane[1];
        layer[0] = make.apply(() -> host.getChildren().remove(layer[0]));
        host.getChildren().add(layer[0]);
    }
}
